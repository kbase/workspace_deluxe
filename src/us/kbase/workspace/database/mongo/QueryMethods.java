package us.kbase.workspace.database.mongo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jongo.Jongo;

import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
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
	private final String versionCollection;
	private final String workspaceACLCollection;
	
	
	QueryMethods(final DB wsmongo, final AllUsers allUsers,
			final String workspaceCollection, final String pointerCollection,
			final String versionCollection,
			final String workspaceACLCollection) {
		this.wsmongo = wsmongo;
		wsjongo = new Jongo(wsmongo);
		this.allUsers = allUsers;
		this.workspaceCollection = workspaceCollection;
		this.pointerCollection = pointerCollection;
		this.versionCollection = versionCollection;
		this.workspaceACLCollection = workspaceACLCollection;
	}
	
	
	Map<String, Object> queryWorkspace(final ResolvedMongoWSID rwsi,
			final Set<String> fields) throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		final Set<ResolvedMongoWSID> rwsiset = new HashSet<ResolvedMongoWSID>();
		rwsiset.add(rwsi);
		return queryWorkspacesByResolvedID(rwsiset, fields).get(rwsi);
	}
	
	Map<ResolvedMongoWSID, Map<String, Object>>
			queryWorkspacesByResolvedID(final Set<ResolvedMongoWSID> rwsiset,
			final Set<String> fields) throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		final Map<Long, ResolvedMongoWSID> ids =
				new HashMap<Long, ResolvedMongoWSID>();
		for (ResolvedMongoWSID r: rwsiset) {
			ids.put(r.getID(), r);
		}
		final Map<Long, Map<String, Object>> idres =
				queryWorkspacesByID(ids.keySet(), fields);
		final Map<ResolvedMongoWSID, Map<String, Object>> ret =
				new HashMap<ResolvedMongoWSID, Map<String,Object>>();
		for (final Long id: ids.keySet()) {
			if (!idres.containsKey(id)) {
				throw new CorruptWorkspaceDBException(
						"Resolved workspace unexpectedly deleted from database: "
						+ id);
			}
			ret.put(ids.get(id), idres.get(id));
		}
		return ret;
	}

//	Map<String, Object> queryWorkspace(final WorkspaceIdentifier wsi,
//			final Set<String> fields) throws WorkspaceCommunicationException {
//		Set<WorkspaceIdentifier> wsiset = new HashSet<WorkspaceIdentifier>();
//		wsiset.add(wsi);
//		final Map<WorkspaceIdentifier, Map<String, Object>> ws =
//				queryWorkspacesByIdentifier(wsiset, fields);
//		if (!ws.containsKey(wsi)) {
//			return null;
//		}
//		return ws.get(wsi);
//	}
	
	Map<WorkspaceIdentifier, Map<String, Object>>
			queryWorkspacesByIdentifier(final Set<WorkspaceIdentifier> wsiset,
			final Set<String> fields) throws WorkspaceCommunicationException {
		if (wsiset.isEmpty()) {
			return new HashMap<WorkspaceIdentifier, Map<String,Object>>();
		}
		final Map<Long, WorkspaceIdentifier> ids =
				new HashMap<Long, WorkspaceIdentifier>();
		final Map<String, WorkspaceIdentifier> names =
				new HashMap<String, WorkspaceIdentifier>();
		for (WorkspaceIdentifier wsi: wsiset) {
			if (wsi.getId() != null) {
				ids.put(wsi.getId(), wsi);
			} else {
				names.put(wsi.getName(), wsi);
			}
		}
		final List<DBObject> orquery = new LinkedList<DBObject>();
		if (!ids.isEmpty()) {
			orquery.add(new BasicDBObject(Fields.WS_ID,
					new BasicDBObject("$in", ids.keySet())));
		}
		if (!names.isEmpty()) {
			orquery.add(new BasicDBObject(Fields.WS_NAME,
					new BasicDBObject("$in", names.keySet())));
		}
		fields.add(Fields.WS_NAME);
		fields.add(Fields.WS_ID);
		final List<Map<String, Object>> res = queryCollection(
				workspaceCollection, new BasicDBObject("$or", orquery), fields);
		
		final Map<WorkspaceIdentifier, Map<String, Object>> ret =
				new HashMap<WorkspaceIdentifier, Map<String,Object>>();
		for (final Map<String, Object> m: res) {
			final String name = (String) m.get(Fields.WS_NAME);
			final Long id = (Long) m.get(Fields.WS_ID);
			if (names.containsKey(name)) {
				ret.put(names.get(name), m);
			}
			if (ids.containsKey(id)) {
				ret.put(ids.get(id), m);
			}
		}
		return ret;
	}
	
	private Map<Long, Map<String, Object>> queryWorkspacesByID(
			final Set<Long> wsids, final Set<String> fields)
			throws WorkspaceCommunicationException {
		if (wsids.isEmpty()) {
			return new HashMap<Long, Map<String, Object>>();
		}
		fields.add(Fields.WS_ID);
		final List<Map<String, Object>> queryres =
				queryCollection(workspaceCollection, String.format(
				"{%s: {$in: [%s]}}", Fields.WS_ID,
				StringUtils.join(wsids, ", ")), fields);
		final Map<Long, Map<String, Object>> result =
				new HashMap<Long, Map<String, Object>>();
		for (Map<String, Object> m: queryres) {
			result.put((Long) m.get(Fields.WS_ID), m);
		}
		return result;
	}

	Map<ObjectIDResolvedWSNoVer, Map<String, Object>> queryObjects(
			final Set<ObjectIDResolvedWSNoVer> objectIDs,
			final Set<String> fields)
			throws WorkspaceCommunicationException {
		if (objectIDs.isEmpty()) {
			return new HashMap<ObjectIDResolvedWSNoVer, Map<String,Object>>();
		}
		final Map<ResolvedMongoWSID,
				Map<Long, ObjectIDResolvedWSNoVer>> ids = 
						new HashMap<ResolvedMongoWSID,
								Map<Long, ObjectIDResolvedWSNoVer>>();
		final Map<ResolvedMongoWSID,
				Map<String, ObjectIDResolvedWSNoVer>> names = 
						new HashMap<ResolvedMongoWSID,
								Map<String, ObjectIDResolvedWSNoVer>>();
		for (final ObjectIDResolvedWSNoVer o: objectIDs) {
			final ResolvedMongoWSID rwsi =
					convertResolvedWSID(o.getWorkspaceIdentifier());
			if (o.getId() == null) {
				if (names.get(rwsi) == null) {
					names.put(rwsi,
							new HashMap<String, ObjectIDResolvedWSNoVer>());
				}
				names.get(rwsi).put(o.getName(), o);
			} else {
				if (ids.get(rwsi) == null) {
					ids.put(rwsi,
							new HashMap<Long, ObjectIDResolvedWSNoVer>());
				}
				ids.get(rwsi).put(o.getId(), o);
			}
		}
		
		final List<DBObject> orquery = new LinkedList<DBObject>();
		for (final ResolvedMongoWSID rwsi: names.keySet()) {
			final DBObject query = new BasicDBObject(Fields.PTR_WS_ID,
					rwsi.getID());
			query.put(Fields.PTR_NAME, new BasicDBObject(
					"$in", names.get(rwsi).keySet()));
			orquery.add(query);
		}
		for (final ResolvedMongoWSID rwsi: ids.keySet()) {
			final DBObject query = new BasicDBObject(Fields.PTR_WS_ID,
					rwsi.getID());
			query.put(Fields.PTR_ID, new BasicDBObject(
					"$in", ids.get(rwsi).keySet()));
			orquery.add(query);
		}
		fields.add(Fields.PTR_ID);
		fields.add(Fields.PTR_NAME);
		fields.add(Fields.PTR_WS_ID);
		final List<Map<String, Object>> queryres = queryCollection(
				pointerCollection, new BasicDBObject("$or", orquery), fields);

		final Map<ObjectIDResolvedWSNoVer, Map<String, Object>> ret =
				new HashMap<ObjectIDResolvedWSNoVer, Map<String, Object>>();
		for (Map<String, Object> m: queryres) {
			final ResolvedMongoWSID rwsi =
					new ResolvedMongoWSID((Long) m.get(Fields.PTR_WS_ID));
			final String name = (String) m.get(Fields.PTR_NAME);
			final Long id = (Long) m.get(Fields.PTR_ID);
			if (names.containsKey(rwsi) && names.get(rwsi).containsKey(name)) {
				ret.put(names.get(rwsi).get(name), m);
			}
			if (ids.containsKey(rwsi) && ids.get(rwsi).containsKey(id)) {
				ret.put(ids.get(rwsi).get(id), m);
			}
		}
		return ret;
	}
	
	//all incoming object IDs must have versions
	Map<ResolvedMongoObjectID, Map<String, Object>> queryVersions(
			final Set<ResolvedMongoObjectID> objectIDs, final Set<String> fields)
			throws WorkspaceCommunicationException {

		final Map<ResolvedMongoWSID, Map<Long, List<Integer>>> ids = 
			new HashMap<ResolvedMongoWSID, Map<Long, List<Integer>>>();
		
		for (final ResolvedMongoObjectID roi: objectIDs) {
			final ResolvedMongoWSID rwsi =
					convertResolvedWSID(roi.getWorkspaceIdentifier());
			if (ids.get(rwsi) == null) {
				ids.put(rwsi, new HashMap<Long, List<Integer>>());
			}
			if (ids.get(rwsi).get(roi.getId()) == null) {
				ids.get(rwsi).put(roi.getId(), new LinkedList<Integer>());
			}
			ids.get(rwsi).get(roi.getId()).add(roi.getVersion());
		}
		
		// ws id, obj id, obj version, version data map
		final Map<ResolvedMongoWSID, Map<Long, Map<Integer, Map<String, Object>>>> data = //this is getting ridiculous
				queryVersions(ids, fields);
		
		final Map<ResolvedMongoObjectID, Map<String, Object>> ret =
				new HashMap<ResolvedMongoObjectID, Map<String,Object>>();
		
		for (final ResolvedMongoObjectID roi: objectIDs) {
			final Map<String, Object> d = data.get(
					roi.getWorkspaceIdentifier()).get(roi.getId())
					.get(roi.getVersion());
			if (d != null) {
				ret.put(roi, d);
			}
		}
		return ret;
	}
	
	//TODO get rid of $or queries - stupid slow
	
	private Map<ResolvedMongoWSID, Map<Long, Map<Integer, Map<String, Object>>>>
			queryVersions(final Map<ResolvedMongoWSID, Map<Long, List<Integer>>> ids,
			final Set<String> fields) throws WorkspaceCommunicationException {
		fields.add(Fields.VER_ID);
		fields.add(Fields.VER_VER);
		//disgusting. need to do better.
		//nested or queries are slow per the mongo docs so just query one
		//workspace at a time. If profiling shows this is slow investigate
		//further
		//actually, $or queries just suck it seems. Way faster to do single queries
		final Map<ResolvedMongoWSID, Map<Long, Map<Integer, Map<String, Object>>>>
			ret = new HashMap<ResolvedMongoWSID, Map<Long,Map<Integer,Map<String,Object>>>>();
		for (final ResolvedMongoWSID rwsi: ids.keySet()) {
			ret.put(rwsi, new HashMap<Long, Map<Integer, Map<String,Object>>>());
//			final List<DBObject> orquery = new LinkedList<DBObject>();
			for (final Long objectID: ids.get(rwsi).keySet()) {
				ret.get(rwsi).put(objectID,
						new HashMap<Integer, Map<String, Object>>());
				final DBObject q;
				if (ids.get(rwsi).get(objectID).size() == 1) {
					q = new BasicDBObject(Fields.VER_VER,
							ids.get(rwsi).get(objectID).get(0));
				} else {
					q = new BasicDBObject(Fields.VER_VER,
						new BasicDBObject("$in", ids.get(rwsi).get(objectID)));
				}
				q.put(Fields.VER_ID, objectID);
				q.put(Fields.VER_WS_ID, rwsi.getID());
				final List<Map<String, Object>> res = queryCollection(
						versionCollection, q, fields);
				for (final Map<String, Object> r: res) {
					final Long id = (Long) r.get(Fields.VER_ID);
					final Integer ver = (Integer) r.get(Fields.VER_VER);
					ret.get(rwsi).get(id).put(ver, r);
				}
//				orquery.add(q);
			}
//			final DBObject query = new BasicDBObject("$or", orquery);
//			query.put(Fields.VER_WS_ID, rwsi.getID());
//			final List<Map<String, Object>> res = queryCollection(
//					versionCollection, query, fields);
//			for (final Map<String, Object> r: res) {
//				final Long id = (Long) r.get(Fields.VER_ID);
//				final Integer ver = (Integer) r.get(Fields.VER_VER);
//				ret.get(rwsi).get(id).put(ver, r);
//			}
		}
		return ret;
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
	
	List<Map<String, Object>> queryCollection(final String collection,
			final DBObject query, final Set<String> fields) throws
			WorkspaceCommunicationException {
		final DBObject projection = new BasicDBObject();
		for (final String field: fields) {
			projection.put(field, 1);
		}
		final DBCursor im;
		try {
			im = wsmongo.getCollection(collection).find(query, projection);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		final List<Map<String, Object>> result =
				new ArrayList<Map<String,Object>>();
		for (final DBObject o: im) {
			result.add(dbObjectToMap(o));
		}
		return result;
	}
	
	//since LazyBsonObject.toMap() is not supported
	private Map<String, Object> dbObjectToMap(final DBObject o) {
		final Map<String, Object> m = new HashMap<String, Object>();
		for (final String name: o.keySet()) {
			m.put(name, o.get(name));
		}
		return m;
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
		return queryPermissions(rwsis, users, Permission.NONE);
	}
	
	Map<ResolvedMongoWSID, Map<User, Permission>> queryPermissions(
			final Set<User> users, final Permission minPerm) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		return queryPermissions(null, users, minPerm);
	}
	
	Map<ResolvedMongoWSID, Map<User, Permission>> queryPermissions(
			final Set<ResolvedMongoWSID> rwsis, final Set<User> users,
			final Permission minPerm) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final DBObject query = new BasicDBObject();
		if (rwsis != null && rwsis.size() > 0) {
			final Set<Long> wsids = new HashSet<Long>();
			for (final ResolvedMongoWSID r: rwsis) {
				wsids.add(r.getID());
			}
			query.put(Fields.ACL_WSID, new BasicDBObject("$in", wsids));
		}
		if (users != null && users.size() > 0) {
			final List<String> u = new ArrayList<String>();
			for (User user: users) {
				u.add(user.getUser());
			}
			query.put(Fields.ACL_USER, new BasicDBObject("$in", u));
		}
		if (minPerm != null & !Permission.NONE.equals(minPerm)) {
			query.put(Fields.ACL_PERM, new BasicDBObject("$gte",
					minPerm.getPermission()));
		}
		final DBObject proj = new BasicDBObject();
		proj.put(Fields.MONGO_ID, 0);
		proj.put(Fields.ACL_USER, 1);
		proj.put(Fields.ACL_PERM, 1);
		proj.put(Fields.ACL_WSID, 1);
		
		final DBCursor res;
		try {
			res = wsmongo.getCollection(workspaceACLCollection)
					.find(query, proj);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		
		final Map<ResolvedMongoWSID, Map<User, Permission>> wsidToPerms =
				new HashMap<ResolvedMongoWSID, Map<User, Permission>>();
		for (final DBObject m: res) {
			final ResolvedMongoWSID wsid = new ResolvedMongoWSID(
					(Long) m.get(Fields.ACL_WSID));
			if (!wsidToPerms.containsKey(wsid)) {
				wsidToPerms.put(wsid, new HashMap<User, Permission>());
			}
			wsidToPerms.get(wsid).put(getUser((String) m.get(Fields.ACL_USER)),
					Permission.fromInt((Integer) m.get(Fields.ACL_PERM)));
		}
		if (rwsis != null) {
			for (ResolvedMongoWSID rwsi: rwsis) {
				if (!wsidToPerms.containsKey(rwsi)) {
					wsidToPerms.put(rwsi, new HashMap<User, Permission>());
				}
			}
		}
		return wsidToPerms;
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
	
	ResolvedMongoWSID convertResolvedWSID(ResolvedWorkspaceID rwsi) {
		if (!(rwsi instanceof ResolvedMongoWSID)) {
			throw new RuntimeException(
					"Passed incorrect implementation of ResolvedWorkspaceID:" +
					(rwsi == null ? null : rwsi.getClass()));
		}
		return (ResolvedMongoWSID) rwsi;
	}
}
