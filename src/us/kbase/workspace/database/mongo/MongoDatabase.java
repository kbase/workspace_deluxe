package us.kbase.workspace.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jongo.FindAndModify;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.marshall.MarshallingException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.experimental.runners.Enclosed;

import us.kbase.typedobj.core.AbsoluteTypeId;
import us.kbase.typedobj.core.ModuleType;
import us.kbase.typedobj.core.TypeId;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.SimpleTypeDefinitionDB;
import us.kbase.typedobj.db.UserInfoProviderForTests;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIDResolvedWSNoVer;
import us.kbase.workspace.database.ObjectMetaData;
import us.kbase.workspace.database.ObjectUserMetaData;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceMetaData;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceObjectID;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.DBAuthorizationException;
import us.kbase.workspace.database.exceptions.InvalidHostException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.UninitializedWorkspaceDBException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.exceptions.WorkspaceDBException;
import us.kbase.workspace.database.exceptions.WorkspaceDBInitializationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspace.workspaces.Provenance;
import us.kbase.workspace.workspaces.TypeSchema;
import us.kbase.workspace.workspaces.WorkspaceSaveObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoException;

@RunWith(Enclosed.class)
public class MongoDatabase implements Database {

	//TODO handle deleting workspaces - changes most methods
	//TODO handle hidden and deleted objects - changes most methods
	//TODO query user metadata
	
	private static final String SETTINGS = "settings";
	private static final String WORKSPACES = "workspaces";
	private static final String WS_ACLS = "workspaceACLs";
	private static final String WORKSPACE_PTRS = "workspacePointers";
	private static final String SHOCK_COLLECTION = "shockData";
//	private static final int MAX_USER_META_SIZE = 16000;
	private static final User allUsers = new AllUsers('*');
	
	private static MongoClient mongoClient = null;
	private final DB wsmongo;
	private final Jongo wsjongo;
	private final BlobStore blob;
	private final QueryMethods query;
	private final FindAndModify updateWScounter;
	private final TypedObjectValidator typeValidator;
	
	private final Map<AbsoluteTypeId, Boolean> typeIndexEnsured = 
			new HashMap<AbsoluteTypeId, Boolean>();
	
	//TODO constants class with field names for all objects

	private static final Map<String, Map<List<String>, List<String>>> indexes;
	static {
		//hardcoded indexes
		indexes = new HashMap<String, Map<List<String>, List<String>>>();
		Map<List<String>, List<String>> ws = new HashMap<List<String>, List<String>>();
		//find workspaces you own
		ws.put(Arrays.asList("owner"), Arrays.asList(""));
		//find workspaces by permanent id
		ws.put(Arrays.asList("id"), Arrays.asList("unique"));
		//find workspaces by mutable name
		ws.put(Arrays.asList("name"), Arrays.asList("unique", "sparse"));
		indexes.put(WORKSPACES, ws);
		
		Map<List<String>, List<String>> wsACL = new HashMap<List<String>, List<String>>();
		//get a user's permission for a workspace, index covers queries
		wsACL.put(Arrays.asList("id", "user", "perm"), Arrays.asList("unique"));
		//find workspaces to which a user has some level of permission, index coves queries
		wsACL.put(Arrays.asList("user", "perm", "id"), Arrays.asList(""));
		indexes.put(WS_ACLS, wsACL);
		
		Map<List<String>, List<String>> wsPtr = new HashMap<List<String>, List<String>>();
		//find objects by workspace id & name
		wsPtr.put(Arrays.asList("workspace", "name"), Arrays.asList("unique", "sparse"));
		//find object by workspace id & object id
		wsPtr.put(Arrays.asList("workspace", "id"), Arrays.asList("unique"));
		//find objects by legacy UUID
		wsPtr.put(Arrays.asList("versions.legacyUUID"), Arrays.asList("unique", "sparse"));
		//determine whether a particular object references this object
		wsPtr.put(Arrays.asList("versions.reffedBy"), Arrays.asList("")); //TODO this might be a bad idea
		//TODO deletion and creation dates for search?
		indexes.put(WORKSPACE_PTRS, wsPtr);
	}

	public MongoDatabase(String host, String database, String backendSecret)
			throws UnknownHostException, IOException, InvalidHostException,
			WorkspaceDBException, TypeStorageException {
		wsmongo = getDB(host, database);
		try {
			wsmongo.getCollectionNames();
		} catch (MongoException.Network men) {
			throw (IOException) men.getCause();
		}
		wsjongo = new Jongo(wsmongo);
		query = new QueryMethods(wsmongo, (AllUsers) allUsers, WORKSPACES,
				WORKSPACE_PTRS, WS_ACLS);
		blob = setupDB(backendSecret);
		updateWScounter = buildCounterQuery();
		//TODO replace with real validator storage system
		this.typeValidator = new TypedObjectValidator(
				new SimpleTypeDefinitionDB(
						new FileTypeStorage("/home/crusherofheads/workspacetypes"), 
						new UserInfoProviderForTests()));
		ensureIndexes();
	}

	public MongoDatabase(String host, String database, String backendSecret,
			String user, String password)
			throws UnknownHostException, IOException, DBAuthorizationException,
			WorkspaceDBException, InvalidHostException, TypeStorageException{
		wsmongo = getDB(host, database);
		try {
			wsmongo.authenticate(user, password.toCharArray());
		} catch (MongoException.Network men) {
			throw (IOException) men.getCause();
		}
		try {
			wsmongo.getCollectionNames();
		} catch (MongoException me) {
			throw new DBAuthorizationException("Not authorized for database "
					+ database, me);
		}
		wsjongo = new Jongo(wsmongo);
		query = new QueryMethods(wsmongo, (AllUsers) allUsers, WORKSPACES,
				WORKSPACE_PTRS, WS_ACLS);
		blob = setupDB(backendSecret);
		updateWScounter = buildCounterQuery();
		//TODO replace with real validator storage system
		this.typeValidator = new TypedObjectValidator(
				new SimpleTypeDefinitionDB(
						new FileTypeStorage("/home/crusherofheads/workspacetypes"), 
						new UserInfoProviderForTests()));
		ensureIndexes();
	}
	
	private void ensureIndexes() {
		for (String col: indexes.keySet()) {
			for (List<String> idx: indexes.get(col).keySet()) {
				final DBObject index = new BasicDBObject();
				final DBObject opts = new BasicDBObject();
				for (String field: idx) {
					index.put(field, 1);
				}
				for (String option: indexes.get(col).get(idx)) {
					if (!option.equals("")) {
						opts.put(option, 1);
					}
				}
				wsmongo.getCollection(col).ensureIndex(index, opts);
			}
		}
	}
	
	private void ensureTypeIndexes(AbsoluteTypeId type) {
		if (typeIndexEnsured.containsKey(type)) {
			return;
		}
		String col = getTypeCollection(type);
		final DBObject chksum = new BasicDBObject();
		chksum.put("chksum", 1);
		final DBObject unique = new BasicDBObject();
		unique.put("unique", 1);
		wsmongo.getCollection(col).ensureIndex(chksum, unique);
		final DBObject workspaces = new BasicDBObject();
		workspaces.put("workspaces", 1);
		wsmongo.getCollection(col).ensureIndex(workspaces);
		typeIndexEnsured.put(type, true);
	}
	
	private FindAndModify buildCounterQuery() {
		return wsjongo.getCollection("workspaceCounter")
				.findAndModify("{id: 'wscounter'}")
				.upsert().returnNew().with("{$inc: {num: 1}}")
				.projection("{num: 1, _id: 0}");
	}

	private DB getDB(String host, String database) throws UnknownHostException,
			InvalidHostException {
		//Only make one instance of MongoClient per JVM per mongo docs
		if (mongoClient == null) {
			// Don't print to stderr
			Logger.getLogger("com.mongodb").setLevel(Level.OFF);
			final MongoClientOptions opts = MongoClientOptions.builder()
					.autoConnectRetry(true).build();
			try {
				mongoClient = new MongoClient(host, opts);
			} catch (NumberFormatException nfe) {
				throw new InvalidHostException(host
						+ " is not a valid mongodb host");
			}
		}
		return mongoClient.getDB(database);
	}

	private BlobStore setupDB(String backendSecret) throws WorkspaceDBException {
		if (!wsmongo.collectionExists(SETTINGS)) {
			throw new UninitializedWorkspaceDBException(
					"No settings collection exists");
		}
		MongoCollection settings = wsjongo.getCollection(SETTINGS);
		if (settings.count() != 1) {
			throw new CorruptWorkspaceDBException(
					"More than one settings document exists");
		}
		Settings wsSettings = null;
		try {
			wsSettings = settings.findOne().as(Settings.class);
		} catch (MarshallingException me) {
			Throwable ex = me.getCause();
			if (ex == null) {
				throw new CorruptWorkspaceDBException(
						"Unable to unmarshal settings document", me);
			}
			ex = ex.getCause();
			if (ex == null || !(ex instanceof CorruptWorkspaceDBException)) {
				throw new CorruptWorkspaceDBException(
						"Unable to unmarshal settings document", me);
			}
			throw (CorruptWorkspaceDBException) ex;
		}
		if (wsSettings.isGridFSBackend()) {
			return new GridFSBackend(wsmongo);
		}
		if (wsSettings.isShockBackend()) {
			URL shockurl = null;
			try {
				shockurl = new URL(wsSettings.getShockUrl());
			} catch (MalformedURLException mue) {
				throw new CorruptWorkspaceDBException(
						"Settings has bad shock url: "
								+ wsSettings.getShockUrl(), mue);
			}
			BlobStore bs;
			try {
				bs = new ShockBackend(wsmongo.getCollection(SHOCK_COLLECTION),
						shockurl, wsSettings.getShockUser(), backendSecret);
			} catch (BlobStoreAuthorizationException e) {
				throw new DBAuthorizationException(
						"Not authorized to access the blob store database", e);
			} catch (BlobStoreException e) {
				throw new WorkspaceDBInitializationException(
						"The database could not be initialized: " +
						e.getLocalizedMessage(), e);
			}
			// TODO if shock, check a few random nodes to make sure they match
			// the internal representation, die otherwise
			return bs;
		}
		throw new RuntimeException("Something's real broke y'all");
	}
	
	@Override
	public TypedObjectValidator getTypeValidator() {
		return typeValidator;
	}

	@Override
	public String getBackendType() {
		return blob.getStoreType();
	}

	@Override
	public WorkspaceMetaData createWorkspace(final WorkspaceUser user,
			final String wsname, final boolean globalRead,
			final String description) throws PreExistingWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		//avoid incrementing the counter if we don't have to
		try {
			if (wsjongo.getCollection(WORKSPACES).count("{name: #}", wsname) > 0) {
				throw new PreExistingWorkspaceException(String.format(
						"Workspace %s already exists", wsname));
			}
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		Integer count; 
		try {
			count = (Integer) updateWScounter.as(DBObject.class).get("num");
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		final DBObject ws = new BasicDBObject();
		ws.put("owner", user.getUser());
		ws.put("id", count);
		Date moddate = new Date();
		ws.put("moddate", moddate);
		ws.put("name", wsname);
		ws.put("deleted", null);
		ws.put("numpointers", 0);
		ws.put("description", description);
		try {
			wsmongo.getCollection(WORKSPACES).insert(ws);
		} catch (MongoException.DuplicateKey mdk) {
			//this is almost impossible to test and will probably almost never happen
			throw new PreExistingWorkspaceException(String.format(
					"Workspace %s already exists", wsname));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		setPermissionsForWorkspaceUsers(count, Arrays.asList(user),
				Permission.OWNER, false);
		if (globalRead) {
			setPermissions(count, Arrays.asList(allUsers), Permission.READ,
					false);
		}
		return new MongoWSMeta(count, wsname, user, moddate, Permission.OWNER,
				globalRead);
	}
	
	//projection lists
	private static final Set<String> PROJ_DESC = newHashSet("description");
	private static final Set<String> PROJ_ID = newHashSet("id");
	private static final Set<String> PROJ_OWNER = newHashSet("owner");
	
	//http://stackoverflow.com/questions/2041778/initialize-java-hashset-values-by-construction
	@SafeVarargs
	private static <T> Set<T> newHashSet(T... objs) {
		Set<T> set = new HashSet<T>();
		for (T o : objs) {
			set.add(o);
		}
		return set;
	}
	
	@Override
	public String getWorkspaceDescription(final ResolvedWorkspaceID rwsi) throws
			CorruptWorkspaceDBException, WorkspaceCommunicationException {
		return (String) query.queryWorkspace(query.convertResolvedID(rwsi),
				PROJ_DESC).get("description");
	}
	
	public ResolvedWorkspaceID resolveWorkspace(final WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException {
		Set<WorkspaceIdentifier> wsiset = new HashSet<WorkspaceIdentifier>();
		wsiset.add(wsi);
		return resolveWorkspaces(wsiset).get(wsi);
				
	}
	
	public Map<WorkspaceIdentifier, ResolvedWorkspaceID> resolveWorkspaces(
			final Set<WorkspaceIdentifier> wsis) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> ret =
				new HashMap<WorkspaceIdentifier, ResolvedWorkspaceID>();
		if (wsis.isEmpty()) {
			return ret;
		}
		final Map<WorkspaceIdentifier, Map<String, Object>> res =
				query.queryWorkspacesByIdentifier(wsis, PROJ_ID);
		final Map<ResolvedMongoWSID, ResolvedMongoWSID> seen = 
				new HashMap<ResolvedMongoWSID, ResolvedMongoWSID>();
		for (final WorkspaceIdentifier wsi: res.keySet()) {
			ResolvedMongoWSID r = new ResolvedMongoWSID(
					(Integer) res.get(wsi).get("id"));
			if (seen.containsKey(r)) {
				r = seen.get(r);
			} else {
				seen.put(r, r);
			}
			ret.put(wsi, r);
		}
		return ret;
	}
	
	@Override
	public void setPermissions(final ResolvedWorkspaceID rwsi,
			final List<WorkspaceUser> users, final Permission perm) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		setPermissionsForWorkspaceUsers(query.convertResolvedID(rwsi).getID(),
				users, perm, true);
	}
	
	//wsid must exist as a workspace
	private void setPermissionsForWorkspaceUsers(final int wsid,
			final List<WorkspaceUser> users, final Permission perm, 
			final boolean checkowner) throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		List<User> u = new ArrayList<User>();
		for (User user: users) {
			u.add(user);
		}
		setPermissions(wsid, u, perm, checkowner);
		
	}
	
	//wsid must exist as a workspace
	private void setPermissions(final int wsid, final List<User> users,
			final Permission perm, final boolean checkowner) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final WorkspaceUser owner;
		if (checkowner) {
			try {
				owner = new WorkspaceUser((String) 
						query.queryWorkspace(wsid, PROJ_OWNER).get("owner"));
			} catch (NoSuchWorkspaceException nswe) {
				throw new CorruptWorkspaceDBException(String.format(
						"Workspace %s was deleted from the database", wsid));
			}
		} else {
			owner = null;
		}
		for (User user: users) {
			if (owner != null && owner.getUser().equals(user.getUser())) {
				continue; // can't change owner permissions
			}
			try {
				if (perm.equals(Permission.NONE)) {
					wsjongo.getCollection(WS_ACLS).remove("{id: #, user: #}",
							wsid, user.getUser());
				} else {
					wsjongo.getCollection(WS_ACLS).update("{id: #, user: #}",
							wsid, user.getUser()).upsert()
							.with("{$set: {perm: #}}", perm.getPermission());
				}
			} catch (MongoException me) {
				throw new WorkspaceCommunicationException(
						"There was a problem communicating with the database", me);
			}
		}
	}
	
	@Override
	public Permission getPermission(final WorkspaceUser user,
			final ResolvedWorkspaceID wsi) throws 
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final Set<ResolvedWorkspaceID> wsis =
				new HashSet<ResolvedWorkspaceID>();
		wsis.add(wsi);
		return getPermissions(user, wsis).get(wsi);
	}
	
	@Override
	public Map<ResolvedWorkspaceID, Permission> getPermissions(
			final WorkspaceUser user, final Set<ResolvedWorkspaceID> rwsis)
			throws WorkspaceCommunicationException, 
			CorruptWorkspaceDBException {
		final Set<User> users = new HashSet<User>();
		if (user != null) {
			users.add(user);
		}
		users.add(allUsers);
		final Set<ResolvedMongoWSID> rm = new HashSet<ResolvedMongoWSID>();
		for (final ResolvedWorkspaceID r: rwsis) {
			rm.add(query.convertResolvedID(r));
		}
		final Map<ResolvedMongoWSID, Map<User, Permission>> perms = 
				query.queryPermissions(rm, users);
		final Map<ResolvedWorkspaceID, Permission> ret = 
				new HashMap<ResolvedWorkspaceID, Permission>();
		for (ResolvedMongoWSID r: perms.keySet()) {
			Permission p = Permission.NONE;
			if (perms.get(r).containsKey(allUsers)) {
				p = perms.get(r).get(allUsers); //if allUsers is in the DB it's always read
			}
			if (perms.get(r).containsKey(user) &&
					!perms.get(r).get(user).equals(Permission.NONE)) {
				p = perms.get(r).get(user);
			}
			ret.put(r, p);
		}
		return ret;
	}
	
	@Override
	public Map<User, Permission> getUserAndGlobalPermission(
			final WorkspaceUser user, final ResolvedWorkspaceID rwsi) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final Set<User> users = new HashSet<User>();
		users.add(allUsers);
		if (user != null) {
			users.add(user);
		}
		final Map<User, Permission> ret = query.queryPermissions(
				query.convertResolvedID(rwsi), users);
		if (!ret.containsKey(user)) {
			ret.put(user, Permission.NONE);
		}
		return ret;
	}

	@Override
	public Map<User, Permission> getAllPermissions(
			final ResolvedWorkspaceID rwsi) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		return query.queryPermissions(query.convertResolvedID(rwsi));
	}

	private static final Set<String> PROJ_ID_NAME_OWNER_MODDATE = 
			newHashSet("id", "name", "owner", "moddate");
	
	@Override
	public WorkspaceMetaData getWorkspaceMetadata(final WorkspaceUser user,
			final ResolvedWorkspaceID rwsi) throws 
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final ResolvedMongoWSID m = query.convertResolvedID(rwsi);
		final Map<String, Object> ws = query.queryWorkspace(m,
				PROJ_ID_NAME_OWNER_MODDATE);
		final Map<User, Permission> res = getUserAndGlobalPermission(user,
				m);
		return new MongoWSMeta((int) ws.get("id"), (String) ws.get("name"),
				new WorkspaceUser((String) ws.get("owner")),
				(Date) ws.get("moddate"), res.get(user),
				res.containsKey(allUsers));
	}
	
	private static class ObjID {
		public String name;
		public int id;
		
		public ObjID(String name, int id) {
			this.name = name;
			this.id = id;
		}

		@Override
		public String toString() {
			return "ObjID [name=" + name + ", id=" + id + "]";
		}
	}
	
	private final Set<String> PROJ_ID_NAME = newHashSet("id", "name");
	
	private Map<WorkspaceObjectID, ObjID> getObjectIDs(
			final ResolvedMongoWSID workspaceID,
			final Set<WorkspaceObjectID> objects) throws
			WorkspaceCommunicationException {
		
		final Map<WorkspaceObjectID, ObjectIDResolvedWSNoVer> queryobjs = 
				new HashMap<WorkspaceObjectID, ObjectIDResolvedWSNoVer>();
		for (final WorkspaceObjectID o: objects) {
			queryobjs.put(o, new ObjectIDResolvedWSNoVer(workspaceID, o));
		}
		final Map<ObjectIDResolvedWSNoVer, Map<String, Object>> retobjs;
		try { 
			retobjs = query.queryObjects(
					new HashSet<ObjectIDResolvedWSNoVer>(queryobjs.values()),
					PROJ_ID_NAME, new HashSet<String>(), false);
		} catch (NoSuchObjectException nsoe) {
			throw new RuntimeException(
					"Threw a NoSuchObjectException when explicitly told not to");
		}
		
		final Map<WorkspaceObjectID, ObjID> goodIds =
				new HashMap<WorkspaceObjectID, ObjID>();
		for (final WorkspaceObjectID o: objects) {
			if (retobjs.containsKey(queryobjs.get(o))) {
				final Map<String, Object> pointer =
						retobjs.get(queryobjs.get(o));
				goodIds.put(o, new ObjID((String) pointer.get("name"),
						(int) pointer.get("id")));
			}
		}
		return goodIds;
	}
	
	// save object in preexisting object container
	private ObjectMetaData saveObjectInstance(final WorkspaceUser user,
			final ResolvedMongoWSID wsid, final int objectid,
			final ObjectSavePackage pkg)
			throws WorkspaceCommunicationException {
		//TODO save datainstance/provenance
		final int ver;
		try {
			ver = (int) wsjongo.getCollection(WORKSPACE_PTRS)
					.findAndModify("{workspace: #, id: #}", wsid.getID(), objectid)
					.returnNew().with("{$inc: {version: 1}}")
					.projection("{version: 1, _id: 0}").as(DBObject.class)
					.get("version");
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		final DBObject query = new BasicDBObject();
		query.put("workspace", wsid.getID());
		query.put("id", objectid);
		
		final DBObject pointer = new BasicDBObject();
		pointer.put("version", ver);
		pointer.put("createdby", user.getUser());
		pointer.put("chksum", pkg.td.getChksum());
		final List<Map<String, String>> meta = 
				new ArrayList<Map<String, String>>();
		if (pkg.wo.getUserMeta() != null) {
			for (String key: pkg.wo.getUserMeta().keySet()) {
				Map<String, String> m = new HashMap<String, String>();
				m.put("k", key);
				m.put("v", pkg.wo.getUserMeta().get(key));
				meta.add(m);
			}
		}
		pointer.put("meta", meta);
		final Date created = new Date();
		pointer.put("createDate", created);
		pointer.put("reffedBy", new ArrayList<Object>()); //TODO this might be a really bad idea
		pointer.put("provenance", null); //TODO add objectID
		pointer.put("type", pkg.td.getType().getTypeString());
		pointer.put("size", pkg.td.getSize());
		pointer.put("revert", null);
		final DBObject versions = new BasicDBObject();
		versions.put("versions", pointer);
		final DBObject update = new BasicDBObject();
		update.put("$push", versions);
		final DBObject deleted = new BasicDBObject();
		deleted.put("deleted", null);
		update.put("$set", deleted);
		
		try {
			wsmongo.getCollection(WORKSPACE_PTRS).update(query, update);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		return new MongoObjectMeta(objectid, pkg.name,
				pkg.td.getType().getTypeString(), created, ver, user, wsid,
				pkg.td.getChksum(), pkg.td.getSize());
	}
	
	//TODO make all projections not include _id unless specified
	
	private String generateUniqueNameForObject(final ResolvedWorkspaceID wsid,
			final int objectid) throws WorkspaceCommunicationException {
		@SuppressWarnings("rawtypes")
		Iterable<Map> ids;
		try {
			ids = wsjongo.getCollection(WORKSPACE_PTRS)
					.find("{workspace: #, name: {$regex: '^#'}}", wsid.getID(),
							objectid)
					.projection("{name: 1, _id: 0}").as(Map.class);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		boolean exact = false;
		final Set<Integer> suffixes = new HashSet<Integer>();
		for (@SuppressWarnings("rawtypes") Map m: ids) {
			
			final String[] id = ((String) m.get("name")).split("-");
			if (id.length == 2) {
				try {
					suffixes.add(Integer.parseInt(id[1]));
				} catch (NumberFormatException e) {
					// do nothing
				}
			} else if (id.length == 1) {
				try {
					exact = exact || objectid == Integer.parseInt(id[0]);
				} catch (NumberFormatException e) {
					// do nothing
				}
			}
		}
		if (!exact) {
			return "" + objectid;
		}
		int counter = 1;
		while (suffixes.contains(counter)) {
			counter++;
		}
		return objectid + "-" + counter;
	}
	
	//save brand new object - create container
	//objectid *must not exist* in the workspace otherwise this method will recurse indefinitely
	//the workspace must exist
	private ObjectMetaData saveObjectWithNewPointer(final WorkspaceUser user,
			final ResolvedMongoWSID wsid, final int objectid, final String name,
			final ObjectSavePackage pkg) throws WorkspaceCommunicationException {
		String newName = name;
		if (name == null) {
			newName = generateUniqueNameForObject(wsid, objectid);
			pkg.name = newName;
		}
		final DBObject dbo = new BasicDBObject();
		dbo.put("workspace", wsid.getID());
		dbo.put("id", objectid);
		dbo.put("version", 0);
		dbo.put("name", newName);
		dbo.put("deleted", null);
		dbo.put("hidden", false); //TODO hidden, also set hidden when not creating pointer from scratch
		dbo.put("versions", new ArrayList<Object>());
		try {
			//maybe could speed things up with batch inserts but dealing with
			//errors would really suck
			//do this later if it becomes a bottleneck
			wsmongo.getCollection(WORKSPACE_PTRS).insert(dbo);
		} catch (MongoException.DuplicateKey dk) {
			//ok, someone must've just this second added this name to an object
			//asshole
			//this should be a rare event
			//TODO is this a name or id clash? if the latter, something is broken
			if (name == null) {
				//not much chance of this happening again, let's just recurse
				return saveObjectWithNewPointer(user, wsid, objectid, name, pkg);
			}
			final WorkspaceObjectID o = pkg.wo.getObjectIdentifier();
			final Map<WorkspaceObjectID, ObjID> objID = getObjectIDs(wsid,
					new HashSet<WorkspaceObjectID>(Arrays.asList(o)));
			if (objID.isEmpty()) {
				//oh ffs, name deleted again, recurse
				return saveObjectWithNewPointer(user, wsid, objectid, name, pkg);
			}
			return saveObjectInstance(user, wsid, objID.get(o).id, pkg);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		return saveObjectInstance(user, wsid, objectid, pkg);
	}
	
	private static class ObjectSavePackage {
		
		public WorkspaceSaveObject wo;
		public String name;
		public TypeData td;
		
		@Override
		public String toString() {
			return "ObjectSavePackage [wo=" + wo + ", name=" + name + ", td="
					+ td + "]";
		}
	}
	
	private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();
	private static final ObjectMapper SORTED_MAPPER = new ObjectMapper();
	static {
		SORTED_MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
	}
	
	//at this point the objects are expected to be validated and references rewritten
	private List<ObjectSavePackage> saveObjectsBuildPackages(
			final ResolvedMongoWSID rwsi,
			final List<WorkspaceSaveObject> objects) {
		//TODO sorted nodes how
		//this method must maintain the order of the objects
		final List<ObjectSavePackage> ret = new LinkedList<ObjectSavePackage>();
		for (WorkspaceSaveObject o: objects) {
			final ObjectSavePackage pkg = new ObjectSavePackage();
			pkg.wo = o;
			final TypeId t = o.getType();
			final AbsoluteTypeId type = new AbsoluteTypeId(t.getType(), t.getMajorVersion() == null ? 0 : t.getMajorVersion(),
					t.getMinorVersion() == null ? 0 : t.getMinorVersion()); //TODO could make this a bit cleaner
			//TODO get subdata (later)?
			//TODO check subdata size
			//TODO change subdata disallowed chars - html encode (%)
			//TODO when safe, add references to references collection
			//could save time by making type->data->TypeData map and reusing
			//already calced TDs, but hardly seems worth it - unlikely event
			pkg.td = new TypeData(o.getData().toString(), type, rwsi, null); //TODO add subdata
			ret.add(pkg);
		}
		return ret;
	}
	
	//at this point the objects are expected to be validated and references rewritten
	@Override
	public List<ObjectMetaData> saveObjects(final WorkspaceUser user, 
			final ResolvedWorkspaceID rwsi,
			final List<WorkspaceSaveObject> objects) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException,
			NoSuchObjectException {
		//TODO break this up
		//this method must maintain the order of the objects
		final List<ObjectMetaData> ret = new ArrayList<ObjectMetaData>();
		
		final ResolvedMongoWSID wsidmongo = query.convertResolvedID(rwsi);
		final List<ObjectSavePackage> packages = saveObjectsBuildPackages(
				wsidmongo, objects);
		//TODO move up to here into workspaces, but build typedata here 
		final Map<WorkspaceObjectID, List<ObjectSavePackage>> idToPkg =
				new HashMap<WorkspaceObjectID, List<ObjectSavePackage>>();
		int newobjects = 0;
		for (final ObjectSavePackage p: packages) {
			final WorkspaceObjectID o = p.wo.getObjectIdentifier();
			if (o != null) {
//				names.add(p.wo.getObjectIdentifier());
				if (idToPkg.get(o) == null) {
					idToPkg.put(o, new ArrayList<ObjectSavePackage>());
				}
				idToPkg.get(o).add(p);
			} else {
				newobjects++;
			}
		}
		//TODO unique index on ws/objid/ver
		final Map<WorkspaceObjectID, ObjID> objIDs = getObjectIDs(wsidmongo,
				idToPkg.keySet());
		for (WorkspaceObjectID o: idToPkg.keySet()) {
			if (!objIDs.containsKey(o)) {
				if (o.getId() != null) {
					throw new NoSuchObjectException(
							"There is no object with id " + o.getId());
				} else {
					for (ObjectSavePackage pkg: idToPkg.get(o)) {
						pkg.name = o.getName();
					}
					newobjects++;
				}
			} else {
				for (ObjectSavePackage pkg: idToPkg.get(o)) {
					pkg.name = objIDs.get(o).name;
				}
			}
		}
		//at this point everything should be ready to save, only comm errors
		//can stop us now, the world is doomed
		saveData(wsidmongo, packages);
		int lastid;
			try {
				lastid = (int) wsjongo.getCollection(WORKSPACES)
						.findAndModify("{id: #}", wsidmongo.getID())
						.returnNew().with("{$inc: {numpointers: #}}", newobjects)
						.projection("{numpointers: 1, _id: 0}").as(DBObject.class)
						.get("numpointers");
			} catch (MongoException me) {
				throw new WorkspaceCommunicationException(
						"There was a problem communicating with the database", me);
			}
		//TODO batch updates when everything known to be ok
		int newid = lastid - newobjects + 1;
		//todo get counts and numbers
		Map<String, Integer> seenNames = new HashMap<String, Integer>();
		for (final ObjectSavePackage p: packages) {
			WorkspaceObjectID oi = p.wo.getObjectIdentifier();
			if (oi == null) { //no name given, need to generate one
				ret.add(saveObjectWithNewPointer(user, wsidmongo, newid++, null, p));
			} else if (oi.getId() != null) { //confirmed ok id
				ret.add(saveObjectInstance(user, wsidmongo, oi.getId(), p));
			} else if (objIDs.get(oi) != null) {//given name translated to id
				ret.add(saveObjectInstance(user, wsidmongo, objIDs.get(oi).id, p));
			} else if (seenNames.containsKey(oi.getName())) {
				//we've already generated an id for this name
				ret.add(saveObjectInstance(user, wsidmongo, seenNames.get(oi.getName()), p));
			} else {//new name, need to generate new id
				ObjectMetaData m = saveObjectWithNewPointer(user, wsidmongo,
						newid++, oi.getName(), p);
				ret.add(m);
				seenNames.put(oi.getName(), m.getObjectId());
			}
		}
		return ret;
	}
	
	//TODO break this up
	private void saveData(final ResolvedMongoWSID workspaceid,
			final List<ObjectSavePackage> data) throws
			WorkspaceCommunicationException {
		final Map<AbsoluteTypeId, List<ObjectSavePackage>> pkgByType =
				new HashMap<AbsoluteTypeId, List<ObjectSavePackage>>();
		for (final ObjectSavePackage p: data) {
			if (pkgByType.get(p.td.getType()) == null) {
				pkgByType.put(p.td.getType(), new ArrayList<ObjectSavePackage>());
			}
			pkgByType.get(p.td.getType()).add(p);
		}
		for (final AbsoluteTypeId type: pkgByType.keySet()) {
			ensureTypeIndexes(type); //TODO do this on adding type and on startup
			final String col = getTypeCollection(type);
			final Map<String, TypeData> chksum = new HashMap<String, TypeData>();
			for (ObjectSavePackage p: pkgByType.get(type)) {
				chksum.put(p.td.getChksum(), p.td);
			}
			final DBObject query = new BasicDBObject();
			final DBObject inchk = new BasicDBObject();
			inchk.put("$in", new ArrayList<String>(chksum.keySet()));
			query.put("chksum", inchk);
			final DBObject proj = new BasicDBObject();
			proj.put("chksum", 1);
			proj.put("_id", 0);
			DBCursor res;
			try {
				res = wsmongo.getCollection(col).find(query, proj);
			} catch (MongoException me) {
				throw new WorkspaceCommunicationException(
						"There was a problem communicating with the database", me);
			}
			final Set<String> existChksum = new HashSet<String>();
			for (DBObject dbo: res) {
				existChksum.add((String)dbo.get("chksum"));
			}
			
			//TODO what happens if a piece of data is deleted after pulling the existing chksums? pull workspaces field, if empty do an upsert just in case
			final List<TypeData> newdata = new ArrayList<TypeData>();
			for (String md5: chksum.keySet()) {
				if (existChksum.contains(md5)) {
					try { //TODO need a test for this once admin stuff is ready
						wsjongo.getCollection(col)
								.update("{chksum: #}", md5)
								.with("{$addToSet: {workspaces: #}}",
										workspaceid.getID());
					} catch (MongoException me) {
						throw new WorkspaceCommunicationException(
								"There was a problem communicating with the database",
								me);
					}
					continue;
				}
				newdata.add(chksum.get(md5));
				try {
					//this is kind of stupid, but no matter how you slice it you have
					//to calc md5s before you save the data
					blob.saveBlob(new MD5(md5), chksum.get(md5).getData());
				} catch (BlobStoreCommunicationException e) {
					throw new WorkspaceCommunicationException(
							e.getLocalizedMessage(), e);
				} catch (BlobStoreAuthorizationException e) {
					throw new WorkspaceCommunicationException(
							"Authorization error communicating with the backend storage system",
							e);
				}
			}
			try {
				wsjongo.getCollection(col).insert((Object[]) newdata.toArray(
						new TypeData[newdata.size()]));
			} catch (MongoException.DuplicateKey dk) {
				//dammit, someone just inserted this data
				//we'll have to go one by one doing upserts
				for (TypeData td: newdata) {
					final DBObject ckquery = new BasicDBObject();
					query.put("chksum", td.getChksum());
					wsmongo.getCollection(col).update(ckquery, td.getSafeUpdate(), true, false);
				}
			} catch (MongoException me) {
				throw new WorkspaceCommunicationException(
						"There was a problem communicating with the database", me);
			}
		}
		//TODO save provenance as batch and add prov id to pkgs
	}
	
	private String getTypeCollection(final AbsoluteTypeId type) {
		return "type-" + type.getType().getTypeString() + "-" +
				type.getMajorVersion();
	}
	
	public Map<ObjectIDResolvedWS, WorkspaceObjectData> getObjects(
			final Set<ObjectIDResolvedWS> objectIDs) throws
			NoSuchObjectException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		final Map<ObjectIDResolvedWSNoVer, Map<String, Object>> pointerData =
				getPointerData(objectIDs);
		final Map<ObjectIDResolvedWS, WorkspaceObjectData> ret =
				new HashMap<ObjectIDResolvedWS, WorkspaceObjectData>();
		final Map<String, Object> chksumToData = new HashMap<String, Object>();
		for (ObjectIDResolvedWS o: objectIDs) {
			final MongoObjectUserMeta meta = generateUserMeta(
					pointerData.get(o.withoutVersion()),
					o.getVersion(), 
					Integer.toString(o.getWorkspaceIdentifier().getID()),
					o.getIdentifierString());
			if (chksumToData.containsKey(meta.getCheckSum())) {
				ret.put(o, new WorkspaceObjectData(
						chksumToData.get(meta.getCheckSum()), meta));
			} else {
				final String data;
				try {
					data = blob.getBlob(new MD5(meta.getCheckSum()));
				} catch (BlobStoreCommunicationException e) {
					throw new WorkspaceCommunicationException(
							e.getLocalizedMessage(), e);
				} catch (BlobStoreAuthorizationException e) {
					throw new WorkspaceCommunicationException(
							"Authorization error communicating with the backend storage system",
							e);
				} catch (NoSuchBlobException e) {
					throw new CorruptWorkspaceDBException(String.format(
							"No data present for valid pointer %s.%s.%s",
							meta.getWorkspaceId(), meta.getObjectId(),
							meta.getVersion()), e);
				}
				final Object object;
				try {
					object = DEFAULT_MAPPER.readValue(data, Object.class);
				} catch (IOException e) {
					throw new RuntimeException(String.format(
							"Unable to deserialize object %s",
							meta.getCheckSum()), e); 
				}
				chksumToData.put(meta.getCheckSum(), object);
				ret.put(o, new WorkspaceObjectData(object, meta));
			}
		}
		return ret;
	}
	
	private static final Set<String> PROJ_META =
			newHashSet("id", "name", "workspace", "version");
	private static final Set<String> PROJ_META_VER = newHashSet("version",
			"meta", "type", "createDate", "createdby", "chksum", "size");
	
	private Map<ObjectIDResolvedWSNoVer, Map<String, Object>> getPointerData(
			final Set<ObjectIDResolvedWS> objectIDs) throws
			NoSuchObjectException, WorkspaceCommunicationException {
		final Set<ObjectIDResolvedWSNoVer> noVer =
				new HashSet<ObjectIDResolvedWSNoVer>();
		for (final ObjectIDResolvedWS o: objectIDs) {
			noVer.add(o.withoutVersion());
		}
		
		final Map<ObjectIDResolvedWSNoVer, Map<String, Object>> qres =
				query.queryObjects(noVer, PROJ_META, PROJ_META_VER);

		//preprocess data structure for faster access if multiple versions
		//of same object required
		for (ObjectIDResolvedWSNoVer o: qres.keySet()) {
			final Map<String, Object> pointer = qres.get(o);
			@SuppressWarnings("unchecked")
			final List<Map<String, Object>> listver =
					(List<Map<String, Object>>) pointer.get("versions");
			final Map<Integer, Map<String, Object>> versions =
					new HashMap<Integer, Map<String,Object>>();
			for (final Map<String, Object> m: listver) {
					versions.put((Integer) m.get("version"), m);
			}
			pointer.put("versions", versions);
		}
		return qres;
	}
	
	private MongoObjectUserMeta generateUserMeta(
			final Map<String, Object> pointer, final Integer version,
			final String workspaceIdentifier, final String objectIdentifier)
			throws NoSuchObjectException {
		final int maxver = (int) pointer.get("version");
		final int ver;
		if (version == null) {
			ver = maxver;
		} else {
			ver = version;
			if (ver > maxver) {
				throw new NoSuchObjectException(String.format(
						"No object with identifier '%s' and version %s exists in workspace %s",
						objectIdentifier, ver, workspaceIdentifier));
			}
		}
		@SuppressWarnings("unchecked")
		final Map<String, Object> verpoint = 
				((Map<Integer, Map<String, Object>>)
						pointer.get("versions")).get(ver);
		if (verpoint == null) { // it's been deleted //TODO test when garbage collection is implemented
			throw new NoSuchObjectException(String.format(
					"No object with identifier '%s' and version %s exists in workspace %s",
					objectIdentifier, ver, workspaceIdentifier));
		}
		@SuppressWarnings("unchecked")
		final List<Map<String, String>> meta =
				(List<Map<String, String>>) verpoint.get("meta");
		return new MongoObjectUserMeta(
				(int) pointer.get("id"),
				(String) pointer.get("name"),
				(String) verpoint.get("type"),
				(Date) verpoint.get("createDate"), ver,
				new WorkspaceUser((String) verpoint.get("createdby")),
				new ResolvedMongoWSID((int) pointer.get("workspace")),
				(String) verpoint.get("chksum"),
				(int) verpoint.get("size"),
				metaMongoArrayToHash(meta));
	}
	
	//TODO provide the workspace name for error purposes
	@Override
	public Map<ObjectIDResolvedWS, ObjectUserMetaData> getObjectMeta(
			final Set<ObjectIDResolvedWS> objectIDs) throws
			NoSuchObjectException, WorkspaceCommunicationException {
		final Map<ObjectIDResolvedWSNoVer, Map<String, Object>> pointerData =
				getPointerData(objectIDs);
		final Map<ObjectIDResolvedWS, ObjectUserMetaData> ret =
				new HashMap<ObjectIDResolvedWS, ObjectUserMetaData>();
		for (ObjectIDResolvedWS o: objectIDs) {
			ret.put(o, generateUserMeta(pointerData.get(o.withoutVersion()),
					o.getVersion(), 
					Integer.toString(o.getWorkspaceIdentifier().getID()),
					o.getIdentifierString()));
		}
		return ret;
	}
	
	private Map<String, String> metaMongoArrayToHash(
			final List<Map<String, String>> meta) {
		final Map<String, String> ret = new HashMap<String, String>();
		for (final Map<String, String> m: meta) {
			ret.put(m.get("k"), m.get("v"));
		}
		return ret;
	}
	
	public static class TestMongoInternals {
		
		//screwy tests for methods that can't be tested in a black box manner
	
		private static MongoDatabase testdb;
		
		@BeforeClass
		public static void setUpClass() throws Exception {
			WorkspaceTestCommon.destroyAndSetupDB(1, "gridFS", "foo");
			String host = WorkspaceTestCommon.getHost();
			String db1 = WorkspaceTestCommon.getDB1();
			String mUser = WorkspaceTestCommon.getMongoUser();
			String mPwd = WorkspaceTestCommon.getMongoPwd();
			if (mUser == null || mUser == "") {
				testdb = new MongoDatabase(host, db1, "foo");
			} else {
				testdb = new MongoDatabase(host, db1, "foo", mUser, mPwd);
			}
		}
		
		@Test
		public void createPointer() throws Exception {
			testdb.createWorkspace(new WorkspaceUser("u"), "ws", false, null);
			Map<String, Object> data = new HashMap<String, Object>();
			Map<String, String> meta = new HashMap<String, String>();
			Map<String, Object> moredata = new HashMap<String, Object>();
			moredata.put("foo", "bar");
			data.put("fubar", moredata);
			meta.put("metastuff", "meta");
			Provenance p = new Provenance("kbasetest2");
			TypeId t = new TypeId(new ModuleType("SomeModule", "AType"), 0, 1);
			AbsoluteTypeId at = new AbsoluteTypeId(new ModuleType("SomeModule", "AType"), 0, 1);
			WorkspaceSaveObject wo = new WorkspaceSaveObject(
					new WorkspaceObjectID("testobj"),
					DEFAULT_MAPPER.valueToTree(data), t, meta, p, false);
			List<WorkspaceSaveObject> wco = new ArrayList<WorkspaceSaveObject>();
			wco.add(wo);
			ObjectSavePackage pkg = new ObjectSavePackage();
			pkg.wo = wo;
			ResolvedMongoWSID rwsi = new ResolvedMongoWSID(1);
			pkg.td = new TypeData(DEFAULT_MAPPER.writeValueAsString(data), at, rwsi , data);
			testdb.saveObjects(new WorkspaceUser("u"), rwsi, wco);
			ObjectMetaData md = testdb.saveObjectWithNewPointer(new WorkspaceUser("u"), rwsi, 3, "testobj", pkg);
			assertThat("objectid is revised to existing object", md.getObjectId(), is(1));
		}
	}
}
