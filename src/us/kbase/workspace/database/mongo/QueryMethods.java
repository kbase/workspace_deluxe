package us.kbase.workspace.database.mongo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jongo.Jongo;

import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.ObjectIDResolvedWSNoVer;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

public class QueryMethods {
	
	private final DB wsmongo;
	private final Jongo wsjongo;
	private final AllUsers allUsers;
	private final String workspaceCollection;
	private final String pointerCollection;
	private final String workspaceACLCollection;
	
	QueryMethods(DB wsmongo, AllUsers allUsers, String workspaceCollection,
			String pointerCollection, String workspaceACLCollection) {
		this.wsmongo = wsmongo;
		wsjongo = new Jongo(wsmongo);
		this.allUsers = allUsers;
		this.workspaceCollection = workspaceCollection;
		this.pointerCollection = pointerCollection;
		this.workspaceACLCollection = workspaceACLCollection;
	}
	
	
	Map<String, Object> queryWorkspace(final ResolvedMongoWSID rwsi,
			final Set<String> fields) throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		Set<ResolvedMongoWSID> rwsiset = new HashSet<ResolvedMongoWSID>();
		rwsiset.add(rwsi);
		return queryWorkspacesByResolvedID(rwsiset, fields).get(rwsi);
	}
	
	Map<ResolvedMongoWSID, Map<String, Object>>
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

	Map<String, Object> queryWorkspace(final WorkspaceIdentifier wsi,
			final Set<String> fields) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		Set<WorkspaceIdentifier> wsiset = new HashSet<WorkspaceIdentifier>();
		wsiset.add(wsi);
		return queryWorkspacesByIdentifier(wsiset, fields).get(wsi);
	}
	
	Map<WorkspaceIdentifier, Map<String, Object>>
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
	
	Map<String, Object> queryWorkspace(final String name,
			final Set<String> fields) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		Set<String> nameset = new HashSet<String>();
		nameset.add(name);
		return queryWorkspacesByName(nameset, fields).get(name);
	}
	
	Map<String, Map<String, Object>> queryWorkspacesByName(
			final Set<String> wsnames, final Set<String> fields) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		if (wsnames.isEmpty()) {
			return new HashMap<String, Map<String, Object>>();
		}
		fields.add("name");
		final List<Map<String, Object>> queryres =
				queryCollection(workspaceCollection,
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
	
	Map<String, Object> queryWorkspace(final Integer id,
			final Set<String> fields) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		Set<Integer> idset = new HashSet<Integer>();
		idset.add(id);
		return queryWorkspacesByID(idset, fields).get(id);
	}	
	
	Map<Integer, Map<String, Object>> queryWorkspacesByID(
			final Set<Integer> wsids, final Set<String> fields) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		if (wsids.isEmpty()) {
			return new HashMap<Integer, Map<String, Object>>();
		}
		fields.add("id");
		final List<Map<String, Object>> queryres =
				queryCollection(workspaceCollection, String.format(
				"{id: {$in: [%s]}}", StringUtils.join(wsids, ", ")), fields);
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
	
	Map<ObjectIDResolvedWSNoVer, Map<String, Object>> queryObjects(
			final Set<ObjectIDResolvedWSNoVer> objectIDs,
			final Set<String> fields, final Set<String> versionfields) throws
			NoSuchObjectException, WorkspaceCommunicationException {
		return queryObjects(objectIDs, fields, versionfields, true);
	}

	Map<ObjectIDResolvedWSNoVer, Map<String, Object>> queryObjects(
			final Set<ObjectIDResolvedWSNoVer> objectIDs,
			final Set<String> fields, final Set<String> versionfields,
			final boolean exceptOnMissing) throws
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
							versionfields, exceptOnMissing);
			for (final Integer id: idres.keySet()) {
				ret.put(ids.get(rwsi).get(id), idres.get(id));
			}
		}
		for (final ResolvedMongoWSID rwsi: names.keySet()) {
			final Map<String, Map<String, Object>> nameres = 
					queryObjectsByName(rwsi, names.get(rwsi).keySet(), fields,
							versionfields, exceptOnMissing);
			for (final String name: nameres.keySet()) {
				ret.put(names.get(rwsi).get(name), nameres.get(name));
			}
		}
		return ret;
	}
	
	Map<String, Map<String, Object>> queryObjectsByName(
			final ResolvedMongoWSID rwsi, final Set<String> names,
			final Set<String> fields, final Set<String> versionfields,
			final boolean exceptOnMissing) throws
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
			if (exceptOnMissing && !result.containsKey(name)) {
				throw new NoSuchObjectException(String.format(
						"No object with name %s exists in workspace %s", name,
						rwsi.getID()));
			}
		}
		return result;
	}
	
	Map<Integer, Map<String, Object>> queryObjectsByID(
			final ResolvedMongoWSID rwsi, final Set<Integer> ids,
			final Set<String> fields, final Set<String> versionfields,
			final boolean exceptOnMissing) throws
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
			if (exceptOnMissing && !result.containsKey(id)) {
				throw new NoSuchObjectException(String.format(
						"No object with id %s exists in workspace %s", id,
						rwsi.getID()));
			}
		}
		return result;
	}
	
	List<Map<String, Object>> queryObjects(final String query,
			final Set<String> fields, final Set<String> versionfields) throws
			WorkspaceCommunicationException {
		if (versionfields != null) {
			for (final String field: versionfields) {
				fields.add("versions." + field);
			}
		}
		return queryCollection(pointerCollection, query, fields);
	}
	
	List<Map<String, Object>> queryCollection(final String collection,
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
	
	Map<User, Permission> queryPermissions(
			final ResolvedMongoWSID rwsi) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		return queryPermissions(rwsi, null);
	}
	
	Map<User, Permission> queryPermissions(
			final ResolvedMongoWSID rwsi, final Set<User> users) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final Set<ResolvedMongoWSID> wsis = new HashSet<ResolvedMongoWSID>();
		wsis.add(rwsi);
		return queryPermissions(wsis, users).get(rwsi);
	}
	
	Map<ResolvedMongoWSID, Map<User, Permission>> queryPermissions(
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
			res = wsmongo.getCollection(workspaceACLCollection)
					.find(query, proj);
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
	
	private User getUser(final String user) throws
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
	
	ResolvedMongoWSID convertResolvedID(ResolvedWorkspaceID rwsi) {
		if (!(rwsi instanceof ResolvedMongoWSID)) {
			throw new RuntimeException(
					"Passed incorrect implementation of ResolvedWorkspaceID");
		}
		return (ResolvedMongoWSID) rwsi;
	}
	

}
