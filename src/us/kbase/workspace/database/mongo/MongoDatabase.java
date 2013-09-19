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
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspace.workspaces.AbsoluteTypeId;
import us.kbase.workspace.workspaces.Provenance;
import us.kbase.workspace.workspaces.TypeId;
import us.kbase.workspace.workspaces.TypeSchema;
import us.kbase.workspace.workspaces.WorkspaceSaveObject;
import us.kbase.workspace.workspaces.WorkspaceType;

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
	
	private Map<String, Object> queryWorkspace(final ResolvedMongoWSID rwsi,
			final Set<String> fields) throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		Set<ResolvedMongoWSID> rwsiset = new HashSet<ResolvedMongoWSID>();
		rwsiset.add(rwsi);
		return queryWorkspacesByResolvedID(rwsiset, fields).get(rwsi);
	}
	
	private Map<ResolvedMongoWSID, Map<String, Object>>
			queryWorkspacesByResolvedID(final Set<ResolvedMongoWSID> rwsiset,
			final Set<String> fields) throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		final Map<Integer, ResolvedMongoWSID> ids =
				new HashMap<Integer, ResolvedMongoWSID>();
		for (ResolvedMongoWSID r: rwsiset) {
			ids.put(r.getID(), r);
		}
		final Map<Integer, Map<String, Object>> idres;
		try {
			idres = queryWorkspacesByID(ids.keySet(), fields);
		} catch (NoSuchWorkspaceException nswe) {
			throw new CorruptWorkspaceDBException(
					"Workspace deleted from database: " + 
					nswe.getLocalizedMessage());
		}
		final Map<ResolvedMongoWSID, Map<String, Object>> ret =
				new HashMap<ResolvedMongoWSID, Map<String,Object>>();
		for (Integer id: idres.keySet()) {
			ret.put(ids.get(id), idres.get(id));
		}
		return ret;
	}

//	private Map<String, Object> queryWorkspace(final WorkspaceIdentifier wsi,
//			final Set<String> fields) throws NoSuchWorkspaceException,
//			WorkspaceCommunicationException {
//		Set<WorkspaceIdentifier> wsiset = new HashSet<WorkspaceIdentifier>();
//		wsiset.add(wsi);
//		return queryWorkspacesByIdentifier(wsiset, fields).get(wsi);
//	}
	
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
				queryCollection(WORKSPACES,
				String.format("{name: {$in: [\"%s\"]}}", 
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
	
	//TODO constants class with field names for all objects
	
	//TODO move all query methods into separate class.
	private Map<Integer, Map<String, Object>> queryWorkspacesByID(
			final Set<Integer> wsids, final Set<String> fields) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		if (wsids.isEmpty()) {
			return new HashMap<Integer, Map<String, Object>>();
		}
		fields.add("id");
		final List<Map<String, Object>> queryres =
				queryCollection(WORKSPACES, String.format("{id: {$in: [%s]}}",
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

	private Map<ObjectIDResolvedWSNoVer, Map<String, Object>> queryObjects(
			final Set<ObjectIDResolvedWSNoVer> objectIDs,
			final Set<String> fields, final Set<String> versionfields) throws
			NoSuchObjectException, WorkspaceCommunicationException {
		
		final Map<ResolvedMongoWSID,
				Map<Integer, ObjectIDResolvedWSNoVer>> ids = 
						new HashMap<ResolvedMongoWSID,
								Map<Integer, ObjectIDResolvedWSNoVer>>();
		final Map<ResolvedMongoWSID,
				Map<String, ObjectIDResolvedWSNoVer>> names = 
						new HashMap<ResolvedMongoWSID,
								Map<String, ObjectIDResolvedWSNoVer>>();
		for (final ObjectIDResolvedWSNoVer o: objectIDs) {
			final ResolvedMongoWSID rwsi =
					convertResolvedID(o.getWorkspaceIdentifier());
			if (o.getId() == null) {
				if (names.get(rwsi) == null) {
					names.put(rwsi,
							new HashMap<String, ObjectIDResolvedWSNoVer>());
				}
				names.get(rwsi).put(o.getName(), o);
			} else {
				if (ids.get(rwsi) == null) {
					ids.put(rwsi,
							new HashMap<Integer, ObjectIDResolvedWSNoVer>());
				}
				ids.get(rwsi).put(o.getId(), o);
			}
		}
			
		final Map<ObjectIDResolvedWSNoVer, Map<String, Object>> ret =
				new HashMap<ObjectIDResolvedWSNoVer, Map<String, Object>>();
		//nested or queries are slow per the mongo docs so just query one
		//workspace at a time. If profiling shows this is slow investigate
		//further
		for (final ResolvedMongoWSID rwsi: ids.keySet()) {
			final Map<Integer, Map<String, Object>> idres = 
					queryObjectsByID(rwsi, ids.get(rwsi).keySet(), fields,
							versionfields);
			for (final Integer id: idres.keySet()) {
				ret.put(ids.get(rwsi).get(id), idres.get(id));
			}
		}
		for (final ResolvedMongoWSID rwsi: names.keySet()) {
			final Map<String, Map<String, Object>> nameres = 
					queryObjectsByName(rwsi, names.get(rwsi).keySet(), fields,
							versionfields);
			for (final String name: nameres.keySet()) {
				ret.put(names.get(rwsi).get(name), nameres.get(name));
			}
		}
		return ret;
	}
	
	private Map<String, Map<String, Object>> queryObjectsByName(
			final ResolvedMongoWSID rwsi, final Set<String> names,
			final Set<String> fields, final Set<String> versionfields) throws
			NoSuchObjectException, WorkspaceCommunicationException {
		if (names.isEmpty()) {
			return new HashMap<String, Map<String, Object>>();
		}
		fields.add("name");
		final List<Map<String, Object>> queryres = queryObjects(
				String.format("{workspace: %s, name: {$in: [\"%s\"]}}",
				rwsi.getID(), StringUtils.join(names, "\", \"")), fields,
				versionfields);
		final Map<String, Map<String, Object>> result =
				new HashMap<String, Map<String, Object>>();
		for (Map<String, Object> m: queryres) {
			result.put((String) m.get("name"), m);
		}
		for (String name: names) {
			if (!result.containsKey(name)) {
				throw new NoSuchObjectException(String.format(
						"No object with name %s exists in workspace %s", name,
						rwsi.getID()));
			}
		}
		return result;
	}
	
	private Map<Integer, Map<String, Object>> queryObjectsByID(
			final ResolvedMongoWSID rwsi, final Set<Integer> ids,
			final Set<String> fields, final Set<String> versionfields) throws
			NoSuchObjectException, WorkspaceCommunicationException {
		if (ids.isEmpty()) {
			return new HashMap<Integer, Map<String, Object>>();
		}
		fields.add("id");
		final List<Map<String, Object>> queryres = queryObjects(
				String.format("{workspace: %s, id: {$in: [%s]}}",
				rwsi.getID(), StringUtils.join(ids, ", ")), fields,
				versionfields);
		final Map<Integer, Map<String, Object>> result =
				new HashMap<Integer, Map<String, Object>>();
		for (Map<String, Object> m: queryres) {
			result.put((Integer) m.get("id"), m);
		}
		for (Integer id: ids) {
			if (!result.containsKey(id)) {
				throw new NoSuchObjectException(String.format(
						"No object with name %s exists in workspace %s", id,
						rwsi.getID()));
			}
		}
		return result;
	}
	
	private List<Map<String, Object>> queryObjects(final String query,
			final Set<String> fields, final Set<String> versionfields) throws
			WorkspaceCommunicationException {
		for (final String field: versionfields) {
			fields.add("versions." + field);
		}
		return queryCollection(WORKSPACE_PTRS, query, fields);
	}
	
	private List<Map<String, Object>> queryCollection(final String collection,
			final String query, final Set<String> fields) throws
			WorkspaceCommunicationException {
		final DBObject projection = new BasicDBObject();
		for (final String field: fields) {
			projection.put(field, 1);
		}
		@SuppressWarnings("rawtypes")
		final Iterable<Map> im;
		try {
			@SuppressWarnings({ "rawtypes" })
			final Iterable<Map> res = wsjongo.getCollection(collection)
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
	public String getWorkspaceDescription(final ResolvedWorkspaceID rwsi) throws
			CorruptWorkspaceDBException, WorkspaceCommunicationException {
		return (String) queryWorkspace(convertResolvedID(rwsi), PROJ_DESC)
				.get("description");
	}
	
	private ResolvedMongoWSID convertResolvedID(ResolvedWorkspaceID rwsi) {
		if (!(rwsi instanceof ResolvedMongoWSID)) {
			throw new RuntimeException(
					"Passed incorrect implementation of ResolvedWorkspaceID");
		}
		return (ResolvedMongoWSID) rwsi;
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
				queryWorkspacesByIdentifier(wsis, PROJ_ID);
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
		setPermissionsForWorkspaceUsers(convertResolvedID(rwsi).getID(), users, perm,
				true);
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
				owner = new WorkspaceUser((String) queryWorkspace(wsid, PROJ_OWNER)
						.get("owner"));
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
	
	private Map<User, Permission> queryPermissions(
			final ResolvedMongoWSID rwsi) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		return queryPermissions(rwsi, null);
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
			final ResolvedMongoWSID rwsi, final Set<User> users) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final Set<ResolvedMongoWSID> wsis = new HashSet<ResolvedMongoWSID>();
		wsis.add(rwsi);
		return queryPermissions(wsis, users).get(rwsi);
	}
	
	private Map<ResolvedMongoWSID, Map<User, Permission>> queryPermissions(
			final Set<ResolvedMongoWSID> rwsis, final Set<User> users) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final DBObject query = new BasicDBObject();
		final DBObject iddb = new BasicDBObject();
		final Set<Integer> wsids = new HashSet<Integer>();
		for (final ResolvedMongoWSID r: rwsis) {
			wsids.add(r.getID());
		}
		iddb.put("$in", wsids);
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
		for (final DBObject m: res) {
			final int wsid = (int) m.get("id");
			if (!wsidToPerms.containsKey(wsid)) {
				wsidToPerms.put(wsid, new HashMap<User, Permission>());
			}
			wsidToPerms.get(wsid).put(getUser((String) m.get("user")),
					Permission.fromInt((int) m.get("perm")));
		}
		final Map<ResolvedMongoWSID, Map<User, Permission>> ret =
				new HashMap<ResolvedMongoWSID, Map<User, Permission>>();
		for (ResolvedMongoWSID rwsi: rwsis) {
			final Map<User, Permission> p = wsidToPerms.get(rwsi.getID());
			ret.put(rwsi, p == null ? new HashMap<User, Permission>() : p);
		}
		return ret;
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
			rm.add(convertResolvedID(r));
		}
		final Map<ResolvedMongoWSID, Map<User, Permission>> perms = 
				queryPermissions(rm, users);
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
		final Map<User, Permission> ret = queryPermissions(
				convertResolvedID(rwsi), users);
		if (!ret.containsKey(user)) {
			ret.put(user, Permission.NONE);
		}
		return ret;
	}

	@Override
	public Map<User, Permission> getAllPermissions(
			final ResolvedWorkspaceID rwsi) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		return queryPermissions(convertResolvedID(rwsi));
	}

	private static final Set<String> PROJ_ID_NAME_OWNER_MODDATE = 
			newHashSet("id", "name", "owner", "moddate");
	
	@Override
	public WorkspaceMetaData getWorkspaceMetadata(final WorkspaceUser user,
			final ResolvedWorkspaceID rwsi) throws 
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final ResolvedMongoWSID m = convertResolvedID(rwsi);
		final Map<String, Object> ws = queryWorkspace(convertResolvedID(m),
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
	
	// T must be String or Integer
	private <T> void validateOrTranslateObjectIDs(
			final ResolvedMongoWSID workspaceID,
			final Map<T, WorkspaceObjectID> objects,
			final Map<WorkspaceObjectID, ObjID> validatedIDs) throws 
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
		query.put("workspace", workspaceID.getID());
		final DBObject identifiers = new BasicDBObject();
		identifiers.put("$in", objects.keySet());
		query.put(string ? "name" : "id", identifiers);
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
			final String name = (String) m.get("name");
			final Integer id = (Integer) m.get("id");
			validatedIDs.put(objects.get(string ? name : id), new ObjID(name, id));
		}
	}
	
	//TODO use queryWorkspaces
	private Map<WorkspaceObjectID, ObjID> getObjectIDs(
			final ResolvedMongoWSID workspaceID,
			final Set<WorkspaceObjectID> objects) throws
			WorkspaceCommunicationException {
		final Map<String, WorkspaceObjectID> names
				= new HashMap<String, WorkspaceObjectID>();
		final Map<Integer, WorkspaceObjectID> ids
				= new HashMap<Integer, WorkspaceObjectID>();
		final Map<WorkspaceObjectID, ObjID> goodIds =
				new HashMap<WorkspaceObjectID, ObjID>();
		for (final WorkspaceObjectID o: objects) {
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
	private ObjectMetaData createPointerAndSaveObject(final WorkspaceUser user,
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
				return createPointerAndSaveObject(user, wsid, objectid, name, pkg);
			}
			final WorkspaceObjectID o = pkg.wo.getObjectIdentifier();
			final Map<WorkspaceObjectID, ObjID> objID = getObjectIDs(wsid,
					new HashSet<WorkspaceObjectID>(Arrays.asList(o)));
			if (objID.isEmpty()) {
				//oh ffs, name deleted again, recurse
				return createPointerAndSaveObject(user, wsid, objectid, name, pkg);
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
	
	private static String getObjectErrorId(final WorkspaceObjectID oi,
			final int objcount) {
		String objErrId = "#" + objcount;
		objErrId += oi == null ? "" : ", " + oi.getIdentifierString();
		return objErrId;
	}
	
	private List<ObjectSavePackage> createObjectSavePackages(
			final ResolvedMongoWSID rwsi,
			final List<WorkspaceSaveObject> objects) {
		//this method must maintain the order of the objects
		//TODO split this up
		final List<ObjectSavePackage> ret = new LinkedList<ObjectSavePackage>();
		final Set<TypeId> types = new HashSet<TypeId>();
		int objcount = 1;
		for (WorkspaceSaveObject wo: objects) {
			types.add(wo.getType());
			final WorkspaceObjectID oi = wo.getObjectIdentifier();
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
			final WorkspaceObjectID oi = pkg.wo.getObjectIdentifier();
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
			pkg.td = new TypeData(tds.data, tds.type, rwsi, null); //TODO add subdata
		}
		return ret;
	}
	
	@Override
	public List<ObjectMetaData> saveObjects(final WorkspaceUser user, 
			final ResolvedWorkspaceID rwsi,
			final List<WorkspaceSaveObject> objects) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException,
			NoSuchObjectException {
		//this method must maintain the order of the objects
//		final Set<ObjectIdentifier> names = new HashSet<ObjectIdentifier>();
		final List<ObjectMetaData> ret = new ArrayList<ObjectMetaData>();
		
		final ResolvedMongoWSID wsidmongo = convertResolvedID(rwsi);
		final List<ObjectSavePackage> packages = createObjectSavePackages(
				wsidmongo, objects);
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
				ret.add(createPointerAndSaveObject(user, wsidmongo, newid++, null, p));
			} else if (oi.getId() != null) { //confirmed ok id
				ret.add(saveObjectInstance(user, wsidmongo, oi.getId(), p));
			} else if (objIDs.get(oi) != null) {//given name translated to id
				ret.add(saveObjectInstance(user, wsidmongo, objIDs.get(oi).id, p));
			} else if (seenNames.containsKey(oi.getName())) {
				//we've already generated an id for this name
				ret.add(saveObjectInstance(user, wsidmongo, seenNames.get(oi.getName()), p));
			} else {//new name, need to generate new id
				ObjectMetaData m = createPointerAndSaveObject(user, wsidmongo,
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
		return "type-" + type.getTypeString();
	}
	
	public Map<ObjectIDResolvedWS, WorkspaceObjectData> getObjects(
			final Set<ObjectIDResolvedWS> objectIDs) {
		//TODO getObjects
		return null;
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
				queryObjects(noVer, PROJ_META, PROJ_META_VER);

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
						workspaceIdentifier, ver, objectIdentifier));
			}
		}
		@SuppressWarnings("unchecked")
		final Map<String, Object> verpoint = 
				((Map<Integer, Map<String, Object>>)
						pointer.get("versions")).get(ver);
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
			TypeId t = new TypeId(new WorkspaceType("SomeModule", "AType"), 0, 1);
			AbsoluteTypeId at = new AbsoluteTypeId(new WorkspaceType("SomeModule", "AType"), 0, 1);
			WorkspaceSaveObject wo = new WorkspaceSaveObject(new WorkspaceObjectID("testobj"), data, t, meta, p, false);
			List<WorkspaceSaveObject> wco = new ArrayList<WorkspaceSaveObject>();
			wco.add(wo);
			ObjectSavePackage pkg = new ObjectSavePackage();
			pkg.wo = wo;
			ResolvedMongoWSID rwsi = new ResolvedMongoWSID(1);
			pkg.td = new TypeData(sortedMapper.writeValueAsString(data), at, rwsi , data);
			testdb.saveObjects(new WorkspaceUser("u"), rwsi, wco);
			ObjectMetaData md = testdb.createPointerAndSaveObject(new WorkspaceUser("u"), rwsi, 3, "testobj", pkg);
			assertThat("objectid is revised to existing object", md.getObjectId(), is(1));
		}
	}
}
