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

import org.apache.commons.lang3.StringUtils;
import org.jongo.FindAndModify;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.marshall.MarshallingException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.experimental.runners.Enclosed;

import us.kbase.workspace.database.Database;
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
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspace.workspaces.AbsoluteTypeId;
import us.kbase.workspace.workspaces.AllUsers;
import us.kbase.workspace.workspaces.ObjectIdentifier;
import us.kbase.workspace.workspaces.ObjectMetaData;
import us.kbase.workspace.workspaces.Permission;
import us.kbase.workspace.workspaces.Provenance;
import us.kbase.workspace.workspaces.TypeId;
import us.kbase.workspace.workspaces.TypeSchema;
import us.kbase.workspace.workspaces.User;
import us.kbase.workspace.workspaces.WorkspaceIdentifier;
import us.kbase.workspace.workspaces.WorkspaceMetaData;
import us.kbase.workspace.workspaces.WorkspaceSaveObject;
import us.kbase.workspace.workspaces.WorkspaceObjectCollection;
import us.kbase.workspace.workspaces.WorkspaceType;
import us.kbase.workspace.workspaces.WorkspaceUser;

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
	
	private static final String SETTINGS = "settings";
	private static final String WORKSPACES = "workspaces";
	private static final String WS_ACLS = "workspaceACLs";
	private static final String WORKSPACE_PTRS = "workspacePointers";
	private static final String SHOCK_COLLECTION = "shockData";
	private static final int MAX_USER_META_SIZE = 16000;
	private static final User allUsers = new AllUsers('*');
	
	private static MongoClient mongoClient = null;
	private final DB wsmongo;
	private final Jongo wsjongo;
	private final BlobStore blob;
	private final FindAndModify updateWScounter;
	
	private final Map<AbsoluteTypeId, Boolean> typeIndexEnsured = 
			new HashMap<AbsoluteTypeId, Boolean>();
	
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
			WorkspaceDBException {
		wsmongo = getDB(host, database);
		try {
			wsmongo.getCollectionNames();
		} catch (MongoException.Network men) {
			throw (IOException) men.getCause();
		}
		wsjongo = new Jongo(wsmongo);
		blob = setupDB(backendSecret);
		updateWScounter = buildCounterQuery();
		ensureIndexes();
	}

	public MongoDatabase(String host, String database, String backendSecret,
			String user, String password) throws UnknownHostException,
			IOException, DBAuthorizationException, InvalidHostException,
			WorkspaceDBException {
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
		blob = setupDB(backendSecret);
		updateWScounter = buildCounterQuery();
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
	public String getBackendType() {
		return blob.getStoreType();
	}

	@Override
	public WorkspaceMetaData createWorkspace(final WorkspaceUser user,
			final String wsname, final boolean globalRead,
			final String description) throws PreExistingWorkspaceException,
			WorkspaceCommunicationException {
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
		try { //this is almost impossible to test and will probably almost never happen
			wsmongo.getCollection(WORKSPACES).insert(ws);
		} catch (MongoException.DuplicateKey mdk) {
			throw new PreExistingWorkspaceException(String.format(
					"Workspace %s already exists", wsname));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		try {
			setPermissionsForWorkspaceUsers(count, Arrays.asList(user),
					Permission.OWNER, false);
			if (globalRead) {
				setPermissions(count, Arrays.asList(allUsers), Permission.READ,
						false);
			}
		} catch (NoSuchWorkspaceException nswe) { //should never happen
			throw new RuntimeException(
					"just created a workspace that doesn't exist", nswe);
		}
		return new MongoWSMeta(count, wsname, user, moddate, Permission.OWNER,
				globalRead);
	}
	
	private Map<String, Object> queryWorkspace(final WorkspaceIdentifier wsi,
			final Set<String> fields) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		Set<WorkspaceIdentifier> wsiset = new HashSet<WorkspaceIdentifier>();
		wsiset.add(wsi);
		return queryWorkspacesByIdentifier(wsiset, fields).get(wsi);
	}

	private Map<WorkspaceIdentifier, Map<String, Object>>
			queryWorkspacesByIdentifier(final Set<WorkspaceIdentifier> wsiset,
			final Set<String> fields) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		final Map<Integer, WorkspaceIdentifier> ids =
				new HashMap<Integer, WorkspaceIdentifier>();
		final Map<String, WorkspaceIdentifier> names =
				new HashMap<String, WorkspaceIdentifier>();
		for (WorkspaceIdentifier wsi: wsiset) {
			if (wsi.getId() != null) {
				ids.put(wsi.getId(), wsi);
			} else {
				names.put(wsi.getName(), wsi);
			}
		}
		//could do an or here but hardly seems worth it
		final Map<WorkspaceIdentifier, Map<String, Object>> ret =
				new HashMap<WorkspaceIdentifier, Map<String,Object>>();
		final Map<Integer, Map<String, Object>> idres = queryWorkspacesByID(
				ids.keySet(), fields);
		for (Integer id: idres.keySet()) {
			ret.put(ids.get(id), idres.get(id));
		}
		final Map<String, Map<String, Object>> nameres = queryWorkspacesByName(
				names.keySet(), fields);
		for (String name: nameres.keySet()) {
			ret.put(names.get(name), nameres.get(name));
		}
		return ret;
	}
	
//	private Map<String, Object> queryWorkspace(final String name,
//			final Set<String> fields) throws NoSuchWorkspaceException,
//			WorkspaceCommunicationException {
//		Set<String> nameset = new HashSet<String>();
//		nameset.add(name);
//		return queryWorkspacesByName(nameset, fields).get(name);
//	}
	
	private Map<String, Map<String, Object>> queryWorkspacesByName(
			final Set<String> wsnames, final Set<String> fields) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		if (wsnames.isEmpty()) {
			return new HashMap<String, Map<String, Object>>();
		}
		fields.add("name");
		final List<Map<String, Object>> queryres =
				queryWorkspaces(String.format("{name: {$in: [\"%s\"]}}", 
				StringUtils.join(wsnames, "\", \"")), fields);
		final Map<String, Map<String, Object>> result =
				new HashMap<String, Map<String, Object>>();
		for (Map<String, Object> m: queryres) {
			result.put((String) m.get("name"), m);
		}
		for (String name: wsnames) {
			if (!result.containsKey(name)) {
				throw new NoSuchWorkspaceException(String.format(
						"No workspace with name %s exists", name));
			}
		}
		return result;
	}
	
	private Map<String, Object> queryWorkspace(final Integer id,
			final Set<String> fields) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		Set<Integer> idset = new HashSet<Integer>();
		idset.add(id);
		return queryWorkspacesByID(idset, fields).get(id);
	}	
	
	private Map<Integer, Map<String, Object>> queryWorkspacesByID(
			final Set<Integer> wsids, final Set<String> fields) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		if (wsids.isEmpty()) {
			return new HashMap<Integer, Map<String, Object>>();
		}
		fields.add("id");
		final List<Map<String, Object>> queryres =
				queryWorkspaces(String.format("{id: {$in: [%s]}}",
				StringUtils.join(wsids, ", ")), fields);
		final Map<Integer, Map<String, Object>> result =
				new HashMap<Integer, Map<String, Object>>();
		for (Map<String, Object> m: queryres) {
			result.put((Integer) m.get("id"), m);
		}
		for (Integer id: wsids) {
			if (!result.containsKey(id)) {
				throw new NoSuchWorkspaceException(String.format(
						"No workspace with id %s exists", id));
			}
		}
		return result;
	}
		
	private List<Map<String, Object>> queryWorkspaces(final String query,
			final Set<String> fields) throws WorkspaceCommunicationException {
		final DBObject projection = new BasicDBObject();
		for (String field: fields) {
			projection.put(field, 1);
		}
		@SuppressWarnings("rawtypes")
		final Iterable<Map> im;
		try {
			@SuppressWarnings({ "rawtypes" })
			final Iterable<Map> res = wsjongo.getCollection(WORKSPACES)
					.find(query).projection(projection.toString())
					.as(Map.class);
			im = res;
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		final List<Map<String, Object>> result =
				new ArrayList<Map<String,Object>>();
		for (@SuppressWarnings("rawtypes") Map m: im) {
			@SuppressWarnings("unchecked")
			final Map<String, Object> castmap = (Map<String, Object>) m; 
			result.add(castmap);
		}
		return result;
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
	public String getWorkspaceDescription(final WorkspaceIdentifier wsi) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		return (String) queryWorkspace(wsi, PROJ_DESC)
				.get("description");
	}
	
	private int getWorkspaceID(final WorkspaceIdentifier wsi,
			final boolean verify) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		if (!verify && wsi.getId() != null) {
			return wsi.getId();
		}
		Set<WorkspaceIdentifier> wsiset = new HashSet<WorkspaceIdentifier>();
		wsiset.add(wsi);
		return getWorkspaceIDs(wsiset, verify).get(wsi);
	}
	
	private Map<WorkspaceIdentifier, Integer> getWorkspaceIDs(
			final Set<WorkspaceIdentifier> wsis, final boolean verify)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException {
		Set<WorkspaceIdentifier> query = new HashSet<WorkspaceIdentifier>();
		Map<WorkspaceIdentifier, Integer> ret =
				new HashMap<WorkspaceIdentifier, Integer>();
		for (WorkspaceIdentifier wsi: wsis) {
			if (!verify && wsi.getId() != null) {
				ret.put(wsi, wsi.getId());
			} else {
				query.add(wsi);
			}
			
		}
		if (query.isEmpty()) {
			return ret;
		}
		Map<WorkspaceIdentifier, Map<String, Object>> res =
				queryWorkspacesByIdentifier(query, PROJ_ID);
		for (WorkspaceIdentifier wsi: res.keySet()) {
			ret.put(wsi, (Integer) res.get(wsi).get("id"));
		}
		return ret;
	}

	private WorkspaceUser getOwner(final int wsid) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		return new WorkspaceUser((String) queryWorkspace(wsid, PROJ_OWNER)
				.get("owner"));
	}
	
	@Override
	public void setPermissions(final WorkspaceIdentifier wsi,
			final List<WorkspaceUser> users, final Permission perm) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		setPermissionsForWorkspaceUsers(getWorkspaceID(wsi, true), users, perm,
				true);
	}
	
	private void setPermissionsForWorkspaceUsers(final int wsid,
			final List<WorkspaceUser> users, final Permission perm, 
			final boolean checkowner) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		List<User> u = new ArrayList<User>();
		for (User user: users) {
			u.add(user);
		}
		setPermissions(wsid, u, perm, checkowner);
		
	}
	
	private void setPermissions(final int wsid, final List<User> users,
			final Permission perm, final boolean checkowner) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		final WorkspaceUser owner = checkowner ? getOwner(wsid) : null;
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
	
	private Permission translatePermission(final int perm) {
		switch (perm) {
			case 0: return Permission.NONE;
			case 1: return Permission.READ;
			case 2: return Permission.WRITE;
			case 3: return Permission.ADMIN;
			case 4: return Permission.OWNER;
			default: throw new IllegalArgumentException(
					"No such permission level " + perm);
		}
	}
	
	private Map<User, Permission> queryPermissions(
			final WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		return queryPermissions(wsi, null);
	}
	
	private static User getUser(final String user) throws
			CorruptWorkspaceDBException {
		try {
			return new WorkspaceUser(user);
		} catch (IllegalArgumentException iae) {
			if (user.length() != 1) {
				throw new CorruptWorkspaceDBException(String.format(
						"Illegal user %s found in database", user));
			}
			try {
				final AllUsers u = new AllUsers(user.charAt(0));
				if (!allUsers.equals(u)) {
					throw new IllegalArgumentException();
				}
				return u;
			} catch (IllegalArgumentException i) {
				throw new CorruptWorkspaceDBException(String.format(
						"Illegal user %s found in database", user));
			}
		}
	}
	
	private Map<User, Permission> queryPermissions(
			final WorkspaceIdentifier wsi, final Set<User> users) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		final Set<WorkspaceIdentifier> wsis = new HashSet<WorkspaceIdentifier>();
		wsis.add(wsi);
		return queryPermissions(wsis, users).get(wsi);
	}
	
	//TODO think about how to pare down the list of workspace identifiers to unique workspaces and still make it easy for users to use the pared list
	private Map<WorkspaceIdentifier, Map<User, Permission>> queryPermissions(
			final Set<WorkspaceIdentifier> wsis, final Set<User> users) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		final DBObject query = new BasicDBObject();
		final DBObject iddb = new BasicDBObject();
		final Map<WorkspaceIdentifier, Integer> wsids = 
				getWorkspaceIDs(wsis, true);
		iddb.put("$in", wsids.values());
		query.put("id", iddb);
		if (users != null && users.size() > 0) {
			final List<String> u = new ArrayList<String>();
			for (User user: users) {
				u.add(user.getUser());
			}
			final DBObject usersdb = new BasicDBObject();
			usersdb.put("$in", u);
			query.put("user", usersdb);
		}
		final DBObject proj = new BasicDBObject();
		proj.put("_id", 0);
		proj.put("user", 1);
		proj.put("perm", 1);
		proj.put("id", 1);
		
		final DBCursor res;
		try {
			res = wsmongo.getCollection(WS_ACLS).find(query, proj);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		
		final Map<Integer, Map<User, Permission>> wsidToPerms =
				new HashMap<Integer, Map<User, Permission>>();
		for (DBObject m: res) {
			final int wsid = (int) m.get("id");
			if (!wsidToPerms.containsKey(wsid)) {
				wsidToPerms.put(wsid, new HashMap<User, Permission>());
			}
			wsidToPerms.get(wsid).put(getUser((String) m.get("user")),
					translatePermission((int) m.get("perm")));
		}
		final Map<WorkspaceIdentifier, Map<User, Permission>> ret =
				new HashMap<WorkspaceIdentifier, Map<User, Permission>>();
		for (WorkspaceIdentifier wsi: wsids.keySet()) {
			final Map<User, Permission> p = wsidToPerms.get(wsids.get(wsi));
			ret.put(wsi, p == null ? new HashMap<User, Permission>() : p);
		}
		return ret;
	}

	@Override
	public Permission getPermission(final WorkspaceUser user,
			final WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final Map<User, Permission> res = getUserAndGlobalPermission(user, wsi);
		Permission perm = Permission.NONE;
		if (res.containsKey(allUsers)) {
			perm = res.get(allUsers); //if allUsers is in the DB it's always read
		}
		if (res.containsKey(user) && !res.get(user).equals(Permission.NONE)) {
			perm = res.get(user);
		}
		return perm;
	}
	
	@Override
	public Map<WorkspaceIdentifier, Permission> getPermissions(
			final WorkspaceUser user, final List<WorkspaceIdentifier> wsis)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public Map<User, Permission> getUserAndGlobalPermission(
			final WorkspaceUser user, final WorkspaceIdentifier wsi) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		final Set<User> users = new HashSet<User>();
		users.add(allUsers);
		if (user != null) {
			users.add(user);
		}
		final Map<User, Permission> ret = queryPermissions(wsi, users);
		if (!ret.containsKey(user)) {
			ret.put(user, Permission.NONE);
		}
		return ret;
	}

	@Override
	public Map<User, Permission> getAllPermissions(
			final WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		return queryPermissions(wsi);
	}

	private static final Set<String> PROJ_ID_NAME_OWNER_MODDATE = 
			newHashSet("id", "name", "owner", "moddate");
	
	@Override
	public WorkspaceMetaData getWorkspaceMetadata(final WorkspaceUser user,
			final WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final Map<String, Object> ws = queryWorkspace(wsi,
				PROJ_ID_NAME_OWNER_MODDATE);
		final Map<User, Permission> res = getUserAndGlobalPermission(user,
				wsi);
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
	
	// T must be String or Integer
	private <T> void validateOrTranslateObjectIDs(final int workspaceID,
			final Map<T, ObjectIdentifier> objects,
			final Map<ObjectIdentifier, ObjID> validatedIDs) throws 
			WorkspaceCommunicationException {
		if (objects.isEmpty()) {
			return;
		}
		
		//this is kind of lame... but C&Ping the code with minor changes is pretty lame too
		boolean string = objects.keySet().iterator().next() instanceof String;
		boolean integer = objects.keySet().iterator().next() instanceof Integer;
		if (!string && !integer) {
			throw new IllegalArgumentException("Only takes strings or integers");
		}
		
		final DBObject query = new BasicDBObject();
		query.put("workspace", workspaceID);
		final DBObject identifiers = new BasicDBObject();
		identifiers.put("$in", objects.keySet());
		query.put(string ? "name" : "id", identifiers);
		System.out.println(query);
		@SuppressWarnings("rawtypes")
		Iterable<Map> res; 
		try {
			res = wsjongo.getCollection(WORKSPACE_PTRS)
					.find(query.toString())
					.projection("{id: 1, name: 1, _id: 0}")
					.as(Map.class);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		for (@SuppressWarnings("rawtypes") Map m: res) {
			System.out.println(m);
			final String name = (String) m.get("name");
			final Integer id = (Integer) m.get("id");
			validatedIDs.put(objects.get(string ? name : id), new ObjID(name, id));
		}
	}
	
	private Map<ObjectIdentifier, ObjID> getObjectIDs(final int workspaceID,
			final Set<ObjectIdentifier> objects) throws
			WorkspaceCommunicationException {
		final Map<String, ObjectIdentifier> names
				= new HashMap<String, ObjectIdentifier>();
		final Map<Integer, ObjectIdentifier> ids
				= new HashMap<Integer, ObjectIdentifier>();
		final Map<ObjectIdentifier, ObjID> goodIds =
				new HashMap<ObjectIdentifier, ObjID>();
		for (final ObjectIdentifier o: objects) {
			if (o.getId() == null) {
				names.put(o.getName(), o);
			} else {
				ids.put(o.getId(), o);
			}
		}
		// could try doing an or later, probably doesn't matter
		validateOrTranslateObjectIDs(workspaceID, names, goodIds);
		validateOrTranslateObjectIDs(workspaceID, ids, goodIds);
		return goodIds;
	}
	
	// save object in preexisting object container
	private ObjectMetaData saveObject(final WorkspaceUser user, final int wsid,
			final int objectid, final ObjectSavePackage pkg)
			throws WorkspaceCommunicationException {
		System.out.println("****save prexisting obj called****");
		System.out.println("wsid " + wsid);
		System.out.println("objectid " + objectid);
		System.out.println(pkg);
		//TODO save datainstance/provenance
		final int ver;
		try {
			ver = (int) wsjongo.getCollection(WORKSPACE_PTRS)
					.findAndModify("{workspace: #, id: #}", wsid, objectid)
					.returnNew().with("{$inc: {version: 1}}")
					.projection("{version: 1, _id: 0}").as(DBObject.class)
					.get("version");
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		final DBObject query = new BasicDBObject();
		query.put("workspace", wsid);
		query.put("id", objectid);
		
		final DBObject pointer = new BasicDBObject();
		pointer.put("version", ver);
		pointer.put("createdby", user.getUser());
		pointer.put("chksum", pkg.td.getChksum());
		pointer.put("meta", pkg.wo.getUserMeta());
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
	
	private String generateUniqueNameForObject(final int wsid,
			final int objectid) throws WorkspaceCommunicationException {
		System.out.println("***get unique name called ***");
		System.out.println("wsid " + wsid);
		System.out.println("objectid " + objectid);
		@SuppressWarnings("rawtypes")
		Iterable<Map> ids;
		try {
			ids = wsjongo.getCollection(WORKSPACE_PTRS)
					.find("{workspace: #, name: {$regex: '^#'}}", wsid, objectid)
					.projection("{name: 1, _id: 0}").as(Map.class);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
//		System.out.println(ids);
		boolean exact = false;
		final Set<Integer> suffixes = new HashSet<Integer>();
		for (@SuppressWarnings("rawtypes") Map m: ids) {
			
			final String[] id = ((String) m.get("name")).split("-");
			System.out.println("*** checking matching id***");
			System.out.println(m);
			System.out.println(Arrays.toString(id));
			System.out.println(id.length);
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
		System.out.println("exact " + exact);
		System.out.println(suffixes);
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
	private ObjectMetaData createPointerAndSaveObject(final WorkspaceUser user,
			final int wsid, final int objectid, final String name,
			final ObjectSavePackage pkg) throws WorkspaceCommunicationException {
		System.out.println("****save new obj called****");
		System.out.println("wsid " + wsid);
		System.out.println("objectid " + objectid);
		System.out.println("name " + name);
		System.out.println(pkg);
		String newName = name;
		if (name == null) {
			System.out.println("Getting name from null");
			newName = generateUniqueNameForObject(wsid, objectid);
			pkg.name = newName;
		}
		System.out.println("newname " + newName);
		final DBObject dbo = new BasicDBObject();
		dbo.put("workspace", wsid);
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
				return createPointerAndSaveObject(user, wsid, objectid, name, pkg);
			}
			final ObjectIdentifier o = pkg.wo.getObjectIdentifier();
			final Map<ObjectIdentifier, ObjID> objID = getObjectIDs(wsid,
					new HashSet<ObjectIdentifier>(Arrays.asList(o)));
			System.out.println(objID);
			if (objID.isEmpty()) {
				//oh ffs, name deleted again, recurse
				return createPointerAndSaveObject(user, wsid, objectid, name, pkg);
			}
			return saveObject(user, wsid, objID.get(o).id, pkg);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		return saveObject(user, wsid, objectid, pkg);
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
	
	private Map<TypeId, TypeSchema> getTypes(final Set<TypeId> types) {
		Map<TypeId, TypeSchema> ret = new HashMap<TypeId, TypeSchema>();
		//TODO getTypes
		return ret;
	}
	
	private static final ObjectMapper defaultMapper = new ObjectMapper();
	private static final ObjectMapper sortedMapper = new ObjectMapper();
	static {
		sortedMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
	}
	
	private static String getObjectErrorId(final ObjectIdentifier oi,
			final int objcount) {
		String objErrId = "#" + objcount;
		objErrId += oi == null ? "" : ", " + oi.getIdentifierString();
		return objErrId;
	}
	
	private List<ObjectSavePackage> createObjectSavePackages(
			final int workspaceid, final WorkspaceObjectCollection objects) {
		//this method must maintain the order of the objects
		//TODO split this up
		final List<ObjectSavePackage> ret = new LinkedList<ObjectSavePackage>();
		final Set<TypeId> types = new HashSet<TypeId>();
		int objcount = 1;
		for (WorkspaceSaveObject wo: objects) {
			types.add(wo.getType());
			final ObjectIdentifier oi = wo.getObjectIdentifier();
			final ObjectSavePackage p = new ObjectSavePackage();
			final String objErrId = getObjectErrorId(oi, objcount);
			final String objerrpunc = oi == null ? "" : ",";
			ret.add(p);
			p.wo = wo;
			if (wo.getUserMeta() != null) {
				String meta;
				try {
					meta = defaultMapper.writeValueAsString(wo.getUserMeta());
				} catch (JsonProcessingException jpe) {
					throw new IllegalArgumentException(String.format(
							"Unable to serialize metadata for object %s",
							objErrId), jpe);
				}
				if (meta.length() > MAX_USER_META_SIZE) {
					throw new IllegalArgumentException(String.format(
							"Metadata for object %s is > %s bytes",
							objErrId + objerrpunc, MAX_USER_META_SIZE));
				}
			}
			objcount++;
		}
		final Map<TypeId, TypeSchema> schemas = getTypes(types);
		class TypeDataStore {
			public String data;
			public AbsoluteTypeId type;
		}
		objcount = 1;
		final Map<ObjectSavePackage, TypeDataStore> pkgData = 
				new HashMap<ObjectSavePackage, TypeDataStore>();
		for (ObjectSavePackage pkg: ret) {
			final TypeDataStore tds = new TypeDataStore();
			final ObjectIdentifier oi = pkg.wo.getObjectIdentifier();
			final String objErrId = getObjectErrorId(oi, objcount);
//			final String objerrpunc = oi == null ? "" : ",";
			String json;
			try {
				json = sortedMapper.writeValueAsString(pkg.wo.getData());
			} catch (JsonProcessingException jpe) {
				throw new IllegalArgumentException(String.format(
						"Unable to serialize data for object %s",
						objErrId), jpe);
			}
			//TODO check type for json vs schema, transform to absolute type, below is temp
			final TypeId t = pkg.wo.getType();
			tds.type = new AbsoluteTypeId(t.getType(), t.getMajorVersion() == null ? 0 : t.getMajorVersion(),
					t.getMinorVersion() == null ? 0 : t.getMinorVersion()); //TODO could make this a bit cleaner
			//TODO get references 
			//TODO get subdata (later)?
			//TODO check subdata size
			//TODO temporary saving of json - remove when rewritten with new refs below
			tds.data = json;
			pkgData.put(pkg, tds);
			objcount++;
		}
		//TODO map all references to real references, error if not found
		//TODO make sure all object and provenance references exist aren't deleted, convert to perm refs - batch
		
		for (ObjectSavePackage pkg: ret) {
			final TypeDataStore tds = pkgData.get(pkg);
			//TODO rewrite data with new references
			//TODO -or- get subdata after rewrite?
			//TODO check subdata size
			//TODO change subdata disallowed chars - html encode (%)
			//TODO when safe, add references to references collection
			//could save time by making type->data->TypeData map and reusing
			//already calced TDs, but hardly seems worth it - unlikely event
			pkg.td = new TypeData(tds.data, tds.type, workspaceid, null); //TODO add subdata
		}
		return ret;
	}
	
	@Override
	public List<ObjectMetaData> saveObjects(final WorkspaceUser user, 
			final WorkspaceObjectCollection objects) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException,
			NoSuchObjectException {
		//this method must maintain the order of the objects
		final int wsid = getWorkspaceID(objects.getWorkspaceIdentifier(), true);
//		final Set<ObjectIdentifier> names = new HashSet<ObjectIdentifier>();
		final List<ObjectMetaData> ret = new ArrayList<ObjectMetaData>();
		
		
		final List<ObjectSavePackage> packages = createObjectSavePackages(wsid,
				objects);
		final Map<ObjectIdentifier, List<ObjectSavePackage>> idToPkg =
				new HashMap<ObjectIdentifier, List<ObjectSavePackage>>();
		int newobjects = 0;
		for (final ObjectSavePackage p: packages) {
			final ObjectIdentifier o = p.wo.getObjectIdentifier();
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
		final Map<ObjectIdentifier, ObjID> objIDs = getObjectIDs(wsid, idToPkg.keySet());
		for (ObjectIdentifier o: idToPkg.keySet()) {
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
//			System.out.println(o);
//			System.out.println(idToPkg.get(o));
		}
		//at this point everything should be ready to save, only comm errors
		//can stop us now, the world is doomed
		saveData(wsid, packages);
		int lastid;
			try {
				lastid = (int) wsjongo.getCollection(WORKSPACES)
						.findAndModify("{id: #}", wsid)
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
			ObjectIdentifier oi = p.wo.getObjectIdentifier();
			if (oi == null) { //no name given, need to generate one
				ret.add(createPointerAndSaveObject(user, wsid, newid++, null, p));
			} else if (oi.getId() != null) { //confirmed ok id
				ret.add(saveObject(user, wsid, oi.getId(), p));
			} else if (objIDs.get(oi) != null) {//given name translated to id
				ret.add(saveObject(user, wsid, objIDs.get(oi).id, p));
			} else if (seenNames.containsKey(oi.getName())) {
				//we've already generated an id for this name
				ret.add(saveObject(user, wsid, seenNames.get(oi.getName()), p));
			} else {//new name, need to generate new id
				ObjectMetaData m = createPointerAndSaveObject(user, wsid,
						newid++, oi.getName(), p);
				ret.add(m);
				seenNames.put(oi.getName(), m.getObjectId());
			}
		}
		return ret;
	}
	
	//TODO test the paths
	//TODO break this up
	private void saveData(final int workspaceid,
			final List<ObjectSavePackage> data) throws
			WorkspaceCommunicationException {
		final Map<AbsoluteTypeId, List<ObjectSavePackage>> pkgByType =
				new HashMap<AbsoluteTypeId, List<ObjectSavePackage>>();
		for (ObjectSavePackage p: data) {
			if (pkgByType.get(p.td.getType()) == null) {
				pkgByType.put(p.td.getType(), new ArrayList<ObjectSavePackage>());
			}
			pkgByType.get(p.td.getType()).add(p);
		}
		for (AbsoluteTypeId type: pkgByType.keySet()) {
			ensureTypeIndexes(type); //TODO do this on adding type and on startup
			String col = getTypeCollection(type);
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
					try {
						wsjongo.getCollection(col)
								.update("{chksum: #}", md5)
								.with("{$addToSet: {workspaces: #}}", workspaceid);
					} catch (MongoException me) {
						throw new WorkspaceCommunicationException(
								"There was a problem communicating with the database", me);
					}
					return;
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
				System.out.println("***bulk insertion***");
				System.out.println(newdata);
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
		return "type-" + type.getTypeString();
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
			Map<String, Object> meta = new HashMap<String, Object>();
			Map<String, Object> moredata = new HashMap<String, Object>();
			moredata.put("foo", "bar");
			data.put("fubar", moredata);
			meta.put("metastuff", moredata);
			Provenance p = new Provenance("kbasetest2");
			TypeId t = new TypeId(new WorkspaceType("SomeModule", "AType"), 0, 1);
			AbsoluteTypeId at = new AbsoluteTypeId(new WorkspaceType("SomeModule", "AType"), 0, 1);
			WorkspaceIdentifier wsi = new WorkspaceIdentifier(1);
			WorkspaceSaveObject wo = new WorkspaceSaveObject(new ObjectIdentifier(wsi, "testobj"), data, t, meta, p, false);
			WorkspaceObjectCollection wco = new WorkspaceObjectCollection(wsi);
			wco.addObject(wo);
			ObjectSavePackage pkg = new ObjectSavePackage();
			pkg.wo = wo;
			pkg.td = new TypeData(sortedMapper.writeValueAsString(data), at, 1, meta);
			testdb.saveObjects(new WorkspaceUser("u"), wco);
			ObjectMetaData md = testdb.createPointerAndSaveObject(new WorkspaceUser("u"), 1, 3, "testobj", pkg);
			assertThat("objectid is revised to existing object", md.getObjectId(), is(1));
		}
	}
}
