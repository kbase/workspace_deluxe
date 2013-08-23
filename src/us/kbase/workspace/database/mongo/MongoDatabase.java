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

	private final DB workspace;
	private final Jongo wsjongo;
	private final BlobStore blob;
	private final FindAndModify updateWScounter;
	
	private static final Map<String, Map<List<String>, List<String>>> indexes;
	static {
		indexes = new HashMap<String, Map<List<String>, List<String>>>();
		Map<List<String>, List<String>> ws = new HashMap<List<String>, List<String>>();
		ws.put(Arrays.asList("owner"), Arrays.asList(""));
		ws.put(Arrays.asList("id"), Arrays.asList("unique"));
		ws.put(Arrays.asList("globalread"), Arrays.asList("sparse"));
		ws.put(Arrays.asList("users"), Arrays.asList("")); //TODO this might need work
		ws.put(Arrays.asList("name"), Arrays.asList("unique", "sparse"));
		indexes.put(WORKSPACES, ws);
	}

	public MongoDatabase(String host, String database, String backendSecret)
			throws UnknownHostException, IOException, InvalidHostException,
			WorkspaceDBException {
		workspace = getDB(host, database);
		try {
			workspace.getCollectionNames();
		} catch (MongoException.Network men) {
			throw (IOException) men.getCause();
		}
		wsjongo = new Jongo(workspace);
		blob = setupDB(backendSecret);
		updateWScounter = buildCounterQuery();
		ensureIndexes();
	}

	public MongoDatabase(String host, String database, String backendSecret,
			String user, String password) throws UnknownHostException,
			IOException, DBAuthorizationException, InvalidHostException,
			WorkspaceDBException {
		workspace = getDB(host, database);
		try {
			workspace.authenticate(user, password.toCharArray());
		} catch (MongoException.Network men) {
			throw (IOException) men.getCause();
		}
		try {
			workspace.getCollectionNames();
		} catch (MongoException me) {
			throw new DBAuthorizationException("Not authorized for database "
					+ database, me);
		}
		wsjongo = new Jongo(workspace);
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
				workspace.getCollection(col).ensureIndex(index, opts);
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
		if (!workspace.collectionExists(SETTINGS)) {
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
			return new GridFSBackend(workspace);
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
			workspace.getCollection(WORKSPACES).insert(ws);
		} catch (MongoException.DuplicateKey mdk) {
			throw new IllegalArgumentException(String.format(
					"Workspace %s already exists", wsname));
		}
		return new MongoWSMeta(count, wsname, user, moddate, null,
				Permission.ADMIN, globalRead);
	}

	@Override
	public String getWorkspaceDescription(WorkspaceIdentifier workspace) throws
			NoSuchWorkspaceException {
		String err = null;
		String query = null;
		if (workspace.getId() != null) {
			query = String.format("{id: %d}", workspace.getId());
			err = "id " + workspace.getId();
		} else {
			query = String.format("{name: \"%s\"}", workspace.getName());
			err = "name " + workspace.getName();
		}
		@SuppressWarnings("unchecked")
		Map<String, String> result = (Map<String, String>)
				wsjongo.getCollection(WORKSPACES).findOne(query)
				.projection("{description: 1}").as(Map.class);
		if (result == null) {
			throw new NoSuchWorkspaceException(String.format(
					"No workspace with %s exists", err));
		}
		return result.get("description");
	}
	
}
