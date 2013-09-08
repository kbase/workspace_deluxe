package us.kbase.workspace.database.mongo;


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

import org.apache.commons.codec.digest.DigestUtils;
import org.jongo.FindAndModify;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.marshall.MarshallingException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.experimental.runners.Enclosed;

import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.exceptions.DBAuthorizationException;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.InvalidHostException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.UninitializedWorkspaceDBException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.exceptions.WorkspaceDBException;
import us.kbase.workspace.test.Common;
import us.kbase.workspace.test.TestException;
import us.kbase.workspace.workspaces.ObjectIdentifier;
import us.kbase.workspace.workspaces.ObjectMetaData;
import us.kbase.workspace.workspaces.Permission;
import us.kbase.workspace.workspaces.Provenance;
import us.kbase.workspace.workspaces.TypeId;
import us.kbase.workspace.workspaces.TypeSchema;
import us.kbase.workspace.workspaces.WorkspaceIdentifier;
import us.kbase.workspace.workspaces.WorkspaceMetaData;
import us.kbase.workspace.workspaces.WorkspaceObject;
import us.kbase.workspace.workspaces.WorkspaceObjectCollection;
import us.kbase.workspace.workspaces.WorkspaceType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
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
	private static final int MAX_USER_META_SIZE = 16000;
	private String allUsers = "*";
	
	private static MongoClient mongoClient = null;
	private final DB wsmongo;
	private final Jongo wsjongo;
	private final BlobStore blob;
	private final FindAndModify updateWScounter;
	
	private static final Map<String, Map<List<String>, List<String>>> indexes;
	static {
		//hardcoded indexes
		indexes = new HashMap<String, Map<List<String>, List<String>>>();
		Map<List<String>, List<String>> ws = new HashMap<List<String>, List<String>>();
		//find workspaces you own
		ws.put(Arrays.asList("owner"), Arrays.asList(""));
		//find workspaces by permanent id
		ws.put(Arrays.asList("id"), Arrays.asList("unique"));
		//find world readable workspaces
		ws.put(Arrays.asList("globalread"), Arrays.asList("sparse"));
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
		wsPtr.put(Arrays.asList("versions.reffedBy"), Arrays.asList(""));
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
				DBObject index = new BasicDBObject();
				DBObject opts = new BasicDBObject();
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
			BlobStore bs = new ShockBackend(shockurl,
					wsSettings.getShockUser(), backendSecret);
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
	
	private void checkUser(String user) {
		if (allUsers.equals(user)) {
			throw new IllegalArgumentException("Illegal user name: " + user);
		}
	}

	@Override
	public WorkspaceMetaData createWorkspace(String user, String wsname,
			boolean globalRead, String description) throws
			PreExistingWorkspaceException, WorkspaceCommunicationException {
		checkUser(user);
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
		ws.put("owner", user);
		ws.put("id", count);
		ws.put("globalread", globalRead);
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
			setPermissions(count, Arrays.asList(user), Permission.OWNER, false);
			if (globalRead) {
				setPermissions(count, Arrays.asList(allUsers), Permission.READ, false);
			}
		} catch (NoSuchWorkspaceException nswe) { //should never happen
			throw new RuntimeException("just created a workspace that doesn't exist", nswe);
		}
		return new MongoWSMeta(count, wsname, user, moddate, Permission.OWNER,
				globalRead);
	}

	private Map<String, Object> queryWorkspace(WorkspaceIdentifier wsi,
			List<String> fields) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		if (wsi.getId() != null) {
			return queryWorkspace(wsi.getId(), fields);
		}
		return queryWorkspace(wsi.getName(), fields);
	}
	
	private Map<String, Object> queryWorkspace(String wsname, List<String> fields)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException {
		return queryWorkspace(String.format("{name: \"%s\"}", wsname),
				"name " + wsname, fields);
	}
	
	private Map<String, Object> queryWorkspace(int wsid, List<String> fields)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException {
		return queryWorkspace(String.format("{id: %d}", wsid), "id " + wsid,
				fields);
	}
		
	private Map<String, Object> queryWorkspace(String query, String error,
			List<String> fields) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		final DBObject projection = new BasicDBObject();
		for (String field: fields) {
			projection.put(field, 1);
		}
		Map<String, Object> result;
		try {
			@SuppressWarnings("unchecked")
			final Map<String, Object> res = wsjongo.getCollection(WORKSPACES)
					.findOne(query).projection(projection.toString())
					.as(Map.class);
			result = res;
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		if (result == null) {
			throw new NoSuchWorkspaceException(String.format(
					"No workspace with %s exists", error));
		}
		return result;
	}

	@Override
	public String getWorkspaceDescription(WorkspaceIdentifier wsi) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		return (String) queryWorkspace(wsi, Arrays.asList("description"))
				.get("description");
	}
	
	private int getWorkspaceID(WorkspaceIdentifier wsi, boolean verify) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		if (!verify && wsi.getId() != null) {
			return wsi.getId();
		}
		return (int) queryWorkspace(wsi, Arrays.asList("id")).get("id");
	}

	private String getOwner(int wsid) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		return (String) queryWorkspace(wsid, Arrays.asList("owner")).get("owner");
	}
	
	@Override
	public void setPermissions(WorkspaceIdentifier wsi, List<String> users,
			Permission perm) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		for (String user: users) {
			checkUser(user);
		}
		setPermissions(getWorkspaceID(wsi, true), users, perm, true);
	}
	
	private void setPermissions(int wsid, List<String> users, Permission perm,
			boolean checkowner) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		final String owner = checkowner ? getOwner(wsid) : "";
		for (String user: users) {
			if (owner.equals(user)) {
				continue; // can't change owner permissions
			}
			try {
				if (perm.equals(Permission.NONE)) {
					wsjongo.getCollection(WS_ACLS).remove("{id: #, user: #}", wsid, user);
				} else {
					wsjongo.getCollection(WS_ACLS).update("{id: #, user: #}", wsid, user)
						.upsert().with("{$set: {perm: #}}", perm.getPermission());
				}
			} catch (MongoException me) {
				throw new WorkspaceCommunicationException(
						"There was a problem communicating with the database", me);
			}
		}
	}
	
	private Permission translatePermission(int perm) {
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
	
	private Map<String, Permission> queryPermissions(WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException {
		return queryPermissions(wsi, null);
	}
	
	private Map<String, Permission> queryPermissions(WorkspaceIdentifier wsi,
			List<String> users) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		final DBObject query = new BasicDBObject();
		query.put("id", getWorkspaceID(wsi, true));
		if (users != null && users.size() > 0) {
			final DBObject usersdb = new BasicDBObject();
			usersdb.put("$in", users);
			query.put("user", usersdb);
		}
		@SuppressWarnings("rawtypes")
		final Iterable<Map> res;
		try {
			@SuppressWarnings("rawtypes")
			final Iterable<Map> result = wsjongo.getCollection(WS_ACLS)
					.find(query.toString()).projection("{user: 1, perm: 1}")
					.as(Map.class);
			res = result;
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		final Map<String, Permission> ret = new HashMap<String, Permission>();
		for (@SuppressWarnings("rawtypes") Map m: res) {
			ret.put((String) m.get("user"), translatePermission((int) m.get("perm")));
		}
		return ret;
	}

	@Override
	public Permission getPermission(String user, WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException {
		checkUser(user);
		final Map<String, Permission> res = getUserAndGlobalPermission(user, wsi);
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
	public Map<String, Permission> getUserAndGlobalPermission(
			String user, WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		final List<String> users = new ArrayList<String>();
		users.add(allUsers);
		if (user != null) {
			checkUser(user);
			users.add(user);
		}
		final Map<String, Permission> ret = queryPermissions(wsi, users);
		if (!ret.containsKey(user)) {
			ret.put(user, Permission.NONE);
		}
		return ret;
	}

	@Override
	public Map<String, Permission> getAllPermissions(
			WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		return queryPermissions(wsi);
	}

	@Override
	public WorkspaceMetaData getWorkspaceMetadata(String user,
			WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		final Map<String, Object> ws = queryWorkspace(wsi, Arrays.asList(
				"id", "name", "owner", "moddate"));
		final Map<String, Permission> res = getUserAndGlobalPermission(user, wsi);
		return new MongoWSMeta((int) ws.get("id"), (String) ws.get("name"),
				(String) ws.get("owner"), (Date) ws.get("moddate"),
				res.get(user), res.containsKey(allUsers));
	}

	@Override
	public void setAllUsersSymbol(String allUsers) {
		this.allUsers = allUsers;
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
	
	private Map<ObjectIdentifier, ObjID> getObjectIDs(int workspaceId,
			Set<ObjectIdentifier> objects) throws
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
		// could also try and unify all this mostly duplicate code
		if (!names.isEmpty()) {
			final DBObject query = new BasicDBObject();
			query.put("workspace", workspaceId);
			final DBObject namesdb = new BasicDBObject();
			namesdb.put("$in", names.keySet());
			query.put("name", namesdb);
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
				goodIds.put(names.get(name), new ObjID(name, id));
			}
		}
		if (!ids.isEmpty()) {
			final DBObject query = new BasicDBObject();
			query.put("workspace", workspaceId);
			final DBObject idsdb = new BasicDBObject();
			idsdb.put("$in", ids.keySet());
			query.put("id", idsdb);
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
				final String name = (String) m.get("name");
				final Integer id = (Integer) m.get("id");
				goodIds.put(ids.get(id), new ObjID(name, id));
			}
		}
		return goodIds;
	}
	
	// save object in preexisting object container
	private ObjectMetaData saveObject(final String user, final int wsid,
			final int objectid, final ObjectSavePackage pkg)
			throws WorkspaceCommunicationException {
		System.out.println("****save prexisting obj called****");
		System.out.println("wsid " + wsid);
		System.out.println("objectid " + objectid);
		System.out.println(pkg);
		//TODO save data
		//TODO save datainstance
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
		pointer.put("createdby", user);
		pointer.put("meta", pkg.wo.getUserMeta());
		final Date created = new Date();
		pointer.put("createDate", created);
		pointer.put("reffedBy", new ArrayList<Object>()); //TODO this might be a really bad idea
		pointer.put("objectId", null); //TODO add objectID
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
		
		//TODO return metadata
		return new MongoObjectMeta(objectid, pkg.name, pkg.wo.getType(), created,
				ver, user, wsid, pkg.chksum, pkg.wo.getUserMeta());
	}
	
	//TODO make all projections not include _id unless specified
	
	private String getUniqueNameForObject(final int wsid, final int objectid)
			throws WorkspaceCommunicationException {
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
	private ObjectMetaData createPointerAndSaveObject(final String user, final int wsid,
			final int objectid, final String name, final ObjectSavePackage pkg)
			throws WorkspaceCommunicationException {
		//TODO the saveObject methods need some serious cleaning up - 1 is for creating container, 1 is for objects
		System.out.println("****save new obj called****");
		System.out.println("wsid " + wsid);
		System.out.println("objectid " + objectid);
		System.out.println("name " + name);
		System.out.println(pkg);
		String newName = name;
		if (name == null) {
			System.out.println("Getting name from null");
			newName = getUniqueNameForObject(wsid, objectid);
			pkg.name = newName;
		}
		System.out.println("newname " + newName);
		final DBObject dbo = new BasicDBObject();
		dbo.put("workspace", wsid);
		dbo.put("id", objectid);
		dbo.put("version", 0);
		dbo.put("name", newName);
		dbo.put("deleted", null);
		dbo.put("hidden", false);
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
		public WorkspaceObject wo;
		public String name;
		public String json;
		public String chksum;
		public Map<String, Object> subdata = null;
		public int size;
		
		@Override
		public String toString() {
			return "ObjectSavePackage [wo=" + wo + ", json=" + json
					+ ", chksum=" + chksum + ", subdata=" + subdata + ", size="
					+ size + "]";
		}
		
	}
	
	private Map<TypeId, TypeSchema> getTypes(Set<TypeId> types) {
		Map<TypeId, TypeSchema> ret = new HashMap<TypeId, TypeSchema>();
		//TODO getTypes
		return ret;
	}
	
	private static final ObjectMapper mapper = new ObjectMapper();
	
	private List<ObjectSavePackage> createObjectSavePackages(
			WorkspaceObjectCollection objects) {
		//this method must maintain the order of the objects
		List<ObjectSavePackage> ret = new LinkedList<ObjectSavePackage>();
		final Set<TypeId> types = new HashSet<TypeId>();
		int objcount = 1;
		for (WorkspaceObject wo: objects) {
			types.add(wo.getType());
			final ObjectIdentifier oi = wo.getObjectIdentifier();
			final ObjectSavePackage p = new ObjectSavePackage();
			String objerrid = "#" + objcount;
			objerrid += oi == null ? "" : ", " + oi.getIdentifierString();
			final String objerrpunc = oi == null ? "" : ",";
			ret.add(p);
			p.wo = wo;
			if (wo.getUserMeta() != null) {
				String meta;
				try {
					meta = mapper.writeValueAsString(wo.getUserMeta());
				} catch (JsonProcessingException jpe) {
					throw new IllegalArgumentException(String.format(
							"Unable to serialize metadata for object %s",
							objerrid), jpe);
				}
				if (meta.length() > MAX_USER_META_SIZE) {
					throw new IllegalArgumentException(String.format(
							"Metadata for object %s is > %s bytes",
							objerrid + objerrpunc, MAX_USER_META_SIZE));
				}
			}
			try {
				p.json = mapper.writeValueAsString(wo.getData());
			} catch (JsonProcessingException jpe) {
				throw new IllegalArgumentException(String.format(
						"Unable to serialize data for object %s",
						objerrid), jpe);
			}
			//TODO do this *after* rewrites
			p.size = p.json.length();
			p.chksum = DigestUtils.md5Hex(p.json);
			objcount++;
		}
		Map<TypeId, TypeSchema> schemas = getTypes(types);
		//TODO check types 
		//TODO get subdata
		//TODO change subdata disallowed chars - html encode (%)
		//TODO make sure all object and provenance references exist aren't deleted, convert to perm refs - batch
		return ret;
	}
	
	@Override
	public List<ObjectMetaData> saveObjects(final String user, 
			final WorkspaceObjectCollection objects) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException,
			NoSuchObjectException {
		//this method must maintain the order of the objects
		final int wsid = getWorkspaceID(objects.getWorkspaceIdentifier(), true);
//		final Set<ObjectIdentifier> names = new HashSet<ObjectIdentifier>();
		final List<ObjectMetaData> ret = new ArrayList<ObjectMetaData>();
		
		
		final List<ObjectSavePackage> packages = createObjectSavePackages(objects);
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
		saveData(packages);
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
	
	private void saveData(List<ObjectSavePackage> data) {
		Map<TypeId, List<ObjectSavePackage>> pkgByType =
				new HashMap<TypeId, List<ObjectSavePackage>>();
		for (ObjectSavePackage pkg: data) {
			if (pkgByType.get(pkg.wo.getType()) == null) {
				pkgByType.put(pkg.wo.getType(), new ArrayList<ObjectSavePackage>());
			}
			
		}
		
	}
	
	public static class TestMongoInternals {
		
		//screwy tests for methods that can't be tested in a black box manner
	
		private static MongoDatabase testdb;
		
		@BeforeClass
		public static void setUpClass() throws Exception {
			Common.destroyAndSetupDB(1, "gridFS", "foo");
			String host = Common.getHost();
			String db1 = Common.getDB1();
			String mUser = Common.getMongoUser();
			String mPwd = Common.getMongoPwd();
			if (mUser == null || mUser == "") {
				testdb = new MongoDatabase(host, db1, "foo");
			} else {
				testdb = new MongoDatabase(host, db1, "foo", mUser, mPwd);
			}
		}
		
		@Test
		public void createPointer() throws Exception {
			testdb.createWorkspace("u", "ws", false, null);
			Map<String, Object> data = new HashMap<String, Object>();
			Map<String, Object> meta = new HashMap<String, Object>();
			Map<String, Object> moredata = new HashMap<String, Object>();
			moredata.put("foo", "bar");
			data.put("fubar", moredata);
			meta.put("metastuff", moredata);
			Provenance p = new Provenance("kbasetest2");
			TypeId t = new TypeId(new WorkspaceType("SomeModule", "AType"), 0, 1);
			WorkspaceIdentifier wsi = new WorkspaceIdentifier(1);
			WorkspaceObject wo = new WorkspaceObject(new ObjectIdentifier(wsi, "testobj"), data, t, meta, p, false);
			WorkspaceObjectCollection wco = new WorkspaceObjectCollection(wsi);
			wco.addObject(wo);
			ObjectSavePackage pkg = new ObjectSavePackage();
			pkg.wo = wo;
			testdb.saveObjects("u", wco);
			testdb.createPointerAndSaveObject("u", 1, 3, "testobj", pkg); //TODO check meta is as expected, objid should == 1
		}
	}
	
}
