package us.kbase.workspace.database.mongo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
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
			boolean globalRead, String description) {
		//avoid incrementing the counter if we don't have to
		if (wsjongo.getCollection(WORKSPACES).count("{name: #}", wsname) > 0) {
			throw new IllegalArgumentException(String.format(
					"Workspace %s already exists", wsname));
		}
		final Integer count = (Integer) updateWScounter.as(DBObject.class).get("num");
		final DBObject ws = new BasicDBObject();
		ws.put("owner", user);
		ws.put("id", count);
		ws.put("globalread", globalRead);
		Date moddate = new Date();
		ws.put("moddate", moddate);
		@SuppressWarnings("rawtypes")
		final List javashutup = new ArrayList();
		ws.put("users", javashutup);
		ws.put("name", wsname);
		ws.put("deleted", null);
		ws.put("numpointers", 0);
		ws.put("description", description);
		try {
			wsmongo.getCollection(WORKSPACES).insert(ws);
		} catch (MongoException.DuplicateKey mdk) {
			throw new IllegalArgumentException(String.format(
					"Workspace %s already exists", wsname));
		}
		return new MongoWSMeta(count, wsname, user, moddate, null,
				Permission.ADMIN, globalRead);
	}

	private class QueryErr {
		public String query;
		public String err;
	}
	
	private QueryErr setUpQuery(WorkspaceIdentifier wsi) {
//		String err = null;
//		String query = null;
		QueryErr qe = new QueryErr();
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
//		String err = null;
//		String query = null;
//		if (workspace.getId() != null) {
//			query = String.format("{id: %d}", workspace.getId());
//			err = "id " + workspace.getId();
//		} else {
//			query = String.format("{name: \"%s\"}", workspace.getName());
//			err = "name " + workspace.getName();
//		}
		QueryErr qe = setUpQuery(workspace);
		@SuppressWarnings("unchecked")
		Map<String, String> result = (Map<String, String>)
				wsjongo.getCollection(WORKSPACES).findOne(qe.query)
				.projection("{description: 1}").as(Map.class);
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
				QueryErr qe = setUpQuery(wsi);
				if (wsjongo.getCollection(WORKSPACES).count(qe.query) == 0) {
					throw new NoSuchWorkspaceException(String.format(
							"No workspace with %s exists", qe.err));
				}
			}
			return wsi.getId();
		}
		//TODO make simple query/projection method
		QueryErr qe = setUpQuery(wsi);
		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>)
				wsjongo.getCollection(WORKSPACES).findOne(qe.query)
				.projection("{id: 1}").as(Map.class);
		if (result == null) {
			throw new NoSuchWorkspaceException(String.format(
					"No workspace with %s exists", qe.err));
		}
		return (Integer) result.get("id");
	}

	@Override
	public void setPermissions(WorkspaceIdentifier workspace,
			List<String> users, Permission perm) throws
			NoSuchWorkspaceException {
		int wsid = getWorkspaceID(workspace, true);
		for (String user: users)
			if (perm.equals(Permission.NONE)) {
				wsjongo.getCollection(WS_ACLS).remove("{id: #, user: #}", wsid, user);
			} else {
				wsjongo.getCollection(WS_ACLS).update("{id: #, user: #}", wsid, user)
					.upsert().with("{$set: {perm: #}}", perm.getPermission());
			}
	}
	
}
