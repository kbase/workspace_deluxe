package us.kbase.workspace.database.mongo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jongo.FindAndModify;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.marshall.MarshallingException;

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.exceptions.DBAuthorizationException;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.InvalidHostException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.UninitializedWorkspaceDBException;
import us.kbase.workspace.database.exceptions.WorkspaceDBException;
import us.kbase.workspace.workspaces.Permission;
import us.kbase.workspace.workspaces.WorkspaceIdentifier;
import us.kbase.workspace.workspaces.WorkspaceMetaData;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;

public class MongoDatabase implements Database {

	private static final String SETTINGS = "settings";
	private static final String WORKSPACES = "workspaces";
	private static final String WS_ACLS = "workspaceACLs";
	private String allUsers = "*";
	
	//TODO make getMongo static method that returns the same instance of mongoClient
	//TODO check autoreconnect
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
		// Don't print to stderr
		Logger.getLogger("com.mongodb").setLevel(Level.OFF);
		MongoClient m = null;
		try {
			m = new MongoClient(host);
		} catch (NumberFormatException nfe) {
			throw new InvalidHostException(host
					+ " is not a valid mongodb host");
		}
		return m.getDB(database);
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

	@Override
	public WorkspaceMetaData createWorkspace(String user, String wsname,
			boolean globalRead, String description) throws
			PreExistingWorkspaceException {
		//TODO checkuser fn
		if (allUsers.equals(user)) {
			throw new IllegalArgumentException("Illegal user name: " + user);
		}
		//avoid incrementing the counter if we don't have to
		if (wsjongo.getCollection(WORKSPACES).count("{name: #}", wsname) > 0) {
			throw new PreExistingWorkspaceException(String.format(
					"Workspace %s already exists", wsname));
		}
		final Integer count = (Integer) updateWScounter.as(DBObject.class).get("num");
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

	private class QueryErr {
		public String query;
		public String err;
	}
	
	private QueryErr setUpQuery(WorkspaceIdentifier wsi) {
		final QueryErr qe = new QueryErr();
		if (wsi.getId() != null) {
			qe.query = String.format("{id: %d}", wsi.getId());
			qe.err = "id " + wsi.getId();
		} else {
			qe.query = String.format("{name: \"%s\"}", wsi.getName());
			qe.err = "name " + wsi.getName();
		}
		return qe;
	}

	@Override
	public String getWorkspaceDescription(WorkspaceIdentifier workspace) throws
			NoSuchWorkspaceException {
		final QueryErr qe = setUpQuery(workspace);
		@SuppressWarnings("unchecked")
		final Map<String, String> result = wsjongo.getCollection(WORKSPACES)
				.findOne(qe.query).projection("{description: 1}").as(Map.class);
		if (result == null) {
			throw new NoSuchWorkspaceException(String.format(
					"No workspace with %s exists", qe.err));
		}
		return result.get("description");
	}
	
	private int getWorkspaceID(WorkspaceIdentifier wsi, boolean verify) throws
			NoSuchWorkspaceException {
		if (wsi.getId() != null) {
			if (verify) {
				final QueryErr qe = setUpQuery(wsi);
				if (wsjongo.getCollection(WORKSPACES).count(qe.query) == 0) {
					throw new NoSuchWorkspaceException(String.format(
							"No workspace with %s exists", qe.err));
				}
			}
			return wsi.getId();
		}
		//TODO make simple query/projection method
		final QueryErr qe = setUpQuery(wsi);
		@SuppressWarnings("unchecked")
		final Map<String, Object> result = wsjongo.getCollection(WORKSPACES)
				.findOne(qe.query).projection("{id: 1}").as(Map.class);
		if (result == null) {
			throw new NoSuchWorkspaceException(String.format(
					"No workspace with %s exists", qe.err));
		}
		return (Integer) result.get("id");
	}

	private String getOwner(int wsid) throws NoSuchWorkspaceException {
		//TODO make generalized query method
		@SuppressWarnings("unchecked")
		Map<String, Object> ws = wsjongo.getCollection(WORKSPACES)
				.findOne("{id: #}", wsid).projection("{owner: 1}").as(Map.class);
		if (ws == null) {
			throw new NoSuchWorkspaceException(String.format(
					"No workspace with id %s exists", wsid));
		}
		return (String) ws.get("owner");
	}
	
	@Override
	public void setPermissions(WorkspaceIdentifier workspace,
			List<String> users, Permission perm) throws
			NoSuchWorkspaceException {
		for (String user: users) {
			if (allUsers.equals(user)) {
				throw new IllegalArgumentException("Illegal user name: " + user);
			}
		}
		setPermissions(getWorkspaceID(workspace, true), users, perm, true);
	}
	
	private void setPermissions(int wsid, List<String> users, Permission perm,
			boolean checkowner) throws NoSuchWorkspaceException {
		String owner = checkowner ? getOwner(wsid) : "";
		for (String user: users) {
			if (owner.equals(user)) {
				continue; // can't change owner permissions
			}
			if (perm.equals(Permission.NONE)) {
				wsjongo.getCollection(WS_ACLS).remove("{id: #, user: #}", wsid, user);
			} else {
				wsjongo.getCollection(WS_ACLS).update("{id: #, user: #}", wsid, user)
					.upsert().with("{$set: {perm: #}}", perm.getPermission());
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

	@Override
	public Permission getPermission(WorkspaceIdentifier workspace, String user)
			throws NoSuchWorkspaceException {
		if (allUsers.equals(user)) {
			throw new IllegalArgumentException("Illegal user name: " + user);
		}
		@SuppressWarnings("rawtypes")
		//TODO use $in instead of $or
		final Iterable<Map> res = wsjongo.getCollection(WS_ACLS)
				.find("{id: #, $or: [{user: #}, {user: #}]}",
						getWorkspaceID(workspace, true), user, allUsers)
				.projection("{perm: 1}").as(Map.class);
		int perm = 0;
		for (@SuppressWarnings("rawtypes") Map m: res) {
			final int newperm = (int) m.get("perm");
			if (perm < newperm){
				perm = newperm;
			}
		}
		return translatePermission(perm);
	}
	
	//TODO use consistent variable names and order in interface
	
	//TODO merge common code with above
	@Override
	public Map<String, Permission> getUserAndGlobalPermission(
			WorkspaceIdentifier workspace, String user) throws NoSuchWorkspaceException {
		if (allUsers.equals(user)) {
			throw new IllegalArgumentException("Illegal user name: " + user);
		}
		@SuppressWarnings("rawtypes")
		//TODO use $in instead of $or
		final Iterable<Map> res = wsjongo.getCollection(WS_ACLS)
				.find("{id: #, $or: [{user: #}, {user: #}]}",
						getWorkspaceID(workspace, true), user, allUsers)
				.projection("{user: 1, perm: 1}").as(Map.class);
		Map<String, Permission> ret = new HashMap<String, Permission>();
		for (@SuppressWarnings("rawtypes") Map m: res) {
			ret.put((String) m.get("user"), translatePermission((int) m.get("perm")));
		}
		if (!ret.containsKey(user)) {
			ret.put(user, Permission.NONE);
		}
		return ret;
	}

	//TODO make common methods for queries, general clean up
	
	@Override
	public Map<String, Permission> getAllPermissions(WorkspaceIdentifier wsi,
			String user) throws NoSuchWorkspaceException {
		if (allUsers.equals(user)) {
			throw new IllegalArgumentException("Illegal user name: " + user);
		}
//		int wsid = getWorkspaceID(wsi, true);
//		@SuppressWarnings("unchecked")
//		final Map<String, Object> acl = (Map<String, Object>) wsjongo.getCollection(WS_ACLS)
//				.findOne("{id: #, user: #}", wsid, user)
//				.projection("{perm: 1}").as(Map.class);
		final Map<String, Permission> ret = new HashMap<String, Permission>();
//		if (acl == null) {
//			return ret;
//		}
//		if (Permission.ADMIN.getPermission() > (int) acl.get("perm")) {
//			ret.put(user, translatePermission((int) acl.get("perm")));
//		} else {
		@SuppressWarnings("rawtypes")
		final Iterable<Map> acls = wsjongo.getCollection(WS_ACLS)
				.find("{id: #}", getWorkspaceID(wsi, true))
				.projection("{perm: 1, user: 1}").as(Map.class);
		for (@SuppressWarnings("rawtypes") Map m: acls) {
			ret.put((String) m.get("user"), translatePermission((int) m.get("perm")));
		}
//		}
		return ret;
		
	}

	@Override
	public WorkspaceMetaData getWorkspaceMetadata(WorkspaceIdentifier wksp,
			String user) throws NoSuchWorkspaceException {
		QueryErr qe = setUpQuery(wksp);
		@SuppressWarnings("unchecked")
		//TODO use common method for getting workspace fields
		Map<String, Object> ws = wsjongo.getCollection(WORKSPACES)
				.findOne(qe.query).projection("{id: 1, name: 1, owner: 1, moddate: 1}")
				.as(Map.class);
		if (ws == null) {
			throw new NoSuchWorkspaceException(String.format(
					"No workspace with %s exists", qe.err));
		}
		@SuppressWarnings("rawtypes")
		//TODO use getUserAndGlobalPermissions
		//TODO use $in instead of $or
		final Iterable<Map> res = wsjongo.getCollection(WS_ACLS)
				.find("{id: #, $or: [{user: #}, {user: #}]}",
						ws.get("id"), user, allUsers)
				.projection("{user: 1, perm: 1}").as(Map.class);
		boolean globalread = false;
		Permission p = Permission.NONE;
		for (@SuppressWarnings("rawtypes") Map m: res) {
			if (m.get("user").equals(allUsers)) {
				globalread = true;
			} else if (m.get("user").equals(user)) {
				p = translatePermission((int) m.get("perm"));
			}
		}
		return new MongoWSMeta((int) ws.get("id"), (String) ws.get("name"),
				(String) ws.get("owner"), (Date) ws.get("moddate"),
				p, globalread);
	}

	@Override
	public void setAllUsersSymbol(String allUsers) {
		this.allUsers = allUsers;
	}
}
