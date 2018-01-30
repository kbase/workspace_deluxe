package us.kbase.workspace.database.mongo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.ResolvedObjectID;
import us.kbase.workspace.database.ResolvedObjectIDNoVer;
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
	
	//TODO TEST unit tests
	//TODO JAVADOC
	
	private final DB wsmongo;
	private final AllUsers allUsers;
	private final String workspaceCollection;
	private final String objectCollection;
	private final String versionCollection;
	private final String workspaceACLCollection;
	
	QueryMethods(
			final DB wsmongo,
			final AllUsers allUsers,
			final String workspaceCollection,
			final String objectCollection,
			final String versionCollection,
			final String workspaceACLCollection) {
		if (wsmongo == null ||
				allUsers == null ||
				workspaceCollection == null ||
				objectCollection == null ||
				versionCollection == null ||
				workspaceACLCollection == null) {
			throw new NullPointerException("No arguments may be null");
		}
		if (workspaceCollection.isEmpty() ||
				objectCollection.isEmpty() ||
				versionCollection.isEmpty() ||
				workspaceACLCollection.isEmpty()) {
			throw new IllegalArgumentException(
					"No collection names may be empty strings");
		}
				
		this.wsmongo = wsmongo;
		this.allUsers = allUsers;
		this.workspaceCollection = workspaceCollection;
		this.objectCollection = objectCollection;
		this.versionCollection = versionCollection;
		this.workspaceACLCollection = workspaceACLCollection;
	}
	
	
	DB getDatabase() {
		return wsmongo;
	}

	String getWorkspaceCollection() {
		return workspaceCollection;
	}


	String getObjectCollection() {
		return objectCollection;
	}


	String getVersionCollection() {
		return versionCollection;
	}


	String getWorkspaceACLCollection() {
		return workspaceACLCollection;
	}


	Map<String, Object> queryWorkspace(final ResolvedWorkspaceID rwsi,
			final Set<String> fields) throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		final Set<ResolvedWorkspaceID> rwsiset = new HashSet<ResolvedWorkspaceID>();
		rwsiset.add(rwsi);
		return queryWorkspacesByResolvedID(rwsiset, fields).get(rwsi);
	}
	
	Map<ResolvedWorkspaceID, Map<String, Object>>
			queryWorkspacesByResolvedID(final Set<ResolvedWorkspaceID> rwsiset,
			final Set<String> fields) throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		final Map<Long, ResolvedWorkspaceID> ids =
				new HashMap<Long, ResolvedWorkspaceID>();
		for (ResolvedWorkspaceID r: rwsiset) {
			ids.put(r.getID(), r);
		}
		final Map<Long, Map<String, Object>> idres =
				queryWorkspacesByID(ids.keySet(), fields, false);
		final Map<ResolvedWorkspaceID, Map<String, Object>> ret =
				new HashMap<ResolvedWorkspaceID, Map<String,Object>>();
		for (final Long id: ids.keySet()) {
			if (!idres.containsKey(id)) {
				/* This will cause havoc when GC is active. Could resolve a deleted workspace,
				 * which is allowed, and then GC before getting here
				 * 
				 * UPDATE - In general deleted workspaces shouldn't get resolved workspace ids.
				 * there are currently (5/30/17) two places where they can:
				 * 1) to undelete a workspace, and that should go directly to an undelete method.
				 * 2) When traversing the object graph into deleted workspaces. The user would
				 * have to extract the workspace ID and send it here, but that should be safe since
				 * if its in the object graph, it contains objects with non-zero reference counts
				 * and therefore can't be deleted permanently.
				 * 
				 * TODO CODE consider making the undelete method take a workspaceIdentifier
				 * rather than a resolved ID to remove any ability to get a resolved ID for a
				 * deleted workspace.
				 * 
				 * Also consider just returning all the WS info in one shot rather than futzing
				 * with resolved IDs and WorkspaceInfos.
				 */
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
		final BasicDBObject q = new BasicDBObject("$or", orquery);
		q.put(Fields.WS_CLONING, new BasicDBObject("$exists", false));
		final List<Map<String, Object>> res = queryCollection(
				workspaceCollection, q, fields);
		
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
			final Set<Long> wsids,
			final Set<String> fields,
			final boolean excludeDeletedWorkspaces)
			throws WorkspaceCommunicationException {
		if (wsids.isEmpty()) {
			return new HashMap<Long, Map<String, Object>>();
		}
		fields.add(Fields.WS_ID);
		final DBObject q = new BasicDBObject(Fields.WS_ID,
				new BasicDBObject("$in", wsids));
		if (excludeDeletedWorkspaces) {
			q.put(Fields.WS_DEL, false);
		}
		q.put(Fields.WS_CLONING, new BasicDBObject("$exists", false));
		final List<Map<String, Object>> queryres =
				queryCollection(workspaceCollection, q, fields);
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
		final Map<Long, ResolvedWorkspaceID> idToWS =
				new HashMap<Long, ResolvedWorkspaceID>();
		final Map<ResolvedWorkspaceID,
				Map<Long, ObjectIDResolvedWSNoVer>> ids = 
						new HashMap<ResolvedWorkspaceID,
								Map<Long, ObjectIDResolvedWSNoVer>>();
		final Map<ResolvedWorkspaceID,
				Map<String, ObjectIDResolvedWSNoVer>> names = 
						new HashMap<ResolvedWorkspaceID,
								Map<String, ObjectIDResolvedWSNoVer>>();
		for (final ObjectIDResolvedWSNoVer o: objectIDs) {
			final ResolvedWorkspaceID rwsi = o.getWorkspaceIdentifier();
			idToWS.put(rwsi.getID(), rwsi);
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
		
		//TODO PERFORMANCE This $or query might be better as multiple individual queries, test
		final List<DBObject> orquery = new LinkedList<DBObject>();
		for (final ResolvedWorkspaceID rwsi: names.keySet()) {
			final DBObject query = new BasicDBObject(Fields.OBJ_WS_ID,
					rwsi.getID());
			query.put(Fields.OBJ_NAME, new BasicDBObject(
					"$in", names.get(rwsi).keySet()));
			//if ver count < 1, we're in a race condition or the database went
			//down after saving the object but before saving the version
			//so don't look at objects with no versions
			query.put(Fields.OBJ_VCNT, new BasicDBObject("$gt", 0));
			orquery.add(query);
		}
		for (final ResolvedWorkspaceID rwsi: ids.keySet()) {
			final DBObject query = new BasicDBObject(Fields.OBJ_WS_ID,
					rwsi.getID());
			query.put(Fields.OBJ_ID, new BasicDBObject(
					"$in", ids.get(rwsi).keySet()));
			//see notes in loop above
			query.put(Fields.OBJ_VCNT, new BasicDBObject("$gt", 0));
			orquery.add(query);
		}
		fields.add(Fields.OBJ_ID);
		fields.add(Fields.OBJ_NAME);
		fields.add(Fields.OBJ_WS_ID);
		final List<Map<String, Object>> queryres = queryCollection(
				objectCollection, new BasicDBObject("$or", orquery), fields);

		final Map<ObjectIDResolvedWSNoVer, Map<String, Object>> ret =
				new HashMap<ObjectIDResolvedWSNoVer, Map<String, Object>>();
		for (Map<String, Object> m: queryres) {
			final ResolvedWorkspaceID rwsi =
					idToWS.get((Long) m.get(Fields.OBJ_WS_ID));
			final String name = (String) m.get(Fields.OBJ_NAME);
			final Long id = (Long) m.get(Fields.OBJ_ID);
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
	Map<ResolvedObjectID, Map<String, Object>> queryVersions(
			final Set<ResolvedObjectID> objectIDs, final Set<String> fields)
			throws WorkspaceCommunicationException {

		final Map<ResolvedWorkspaceID, Map<Long, List<Integer>>> ids = 
			new HashMap<ResolvedWorkspaceID, Map<Long, List<Integer>>>();
		
		for (final ResolvedObjectID roi: objectIDs) {
			final ResolvedWorkspaceID rwsi = roi.getWorkspaceIdentifier();
			if (ids.get(rwsi) == null) {
				ids.put(rwsi, new HashMap<Long, List<Integer>>());
			}
			if (ids.get(rwsi).get(roi.getId()) == null) {
				ids.get(rwsi).put(roi.getId(), new LinkedList<Integer>());
			}
			ids.get(rwsi).get(roi.getId()).add(roi.getVersion());
		}
		
		// ws id, obj id, obj version, version data map
		final Map<ResolvedWorkspaceID, Map<Long, Map<Integer, Map<String, Object>>>> data = //this is getting ridiculous
				queryVersions(ids, fields);
		
		final Map<ResolvedObjectID, Map<String, Object>> ret =
				new HashMap<ResolvedObjectID, Map<String,Object>>();
		
		for (final ResolvedObjectID roi: objectIDs) {
			final Map<String, Object> d = data.get(
					roi.getWorkspaceIdentifier()).get(roi.getId())
					.get(roi.getVersion());
			if (d != null) {
				ret.put(roi, d);
			}
		}
		return ret;
	}
	
	//method assumes at least one version exists
	Map<ResolvedObjectIDNoVer, List<Map<String, Object>>> queryAllVersions(
			final HashSet<ResolvedObjectIDNoVer> objIDs,
			final Set<String> fields)
			throws WorkspaceCommunicationException {
		final Map<ResolvedWorkspaceID, Map<Long, List<Integer>>> ids =
				new HashMap<ResolvedWorkspaceID, Map<Long,List<Integer>>>();
		
		for (final ResolvedObjectIDNoVer roi: objIDs) {
			final ResolvedWorkspaceID rwsi = roi.getWorkspaceIdentifier();
			if (ids.get(rwsi) == null) {
				ids.put(rwsi, new HashMap<Long, List<Integer>>());
			}
			ids.get(rwsi).put(roi.getId(), new LinkedList<Integer>());
		}
		// ws id, obj id, obj version, version data map
		final Map<ResolvedWorkspaceID, Map<Long, Map<Integer, Map<String, Object>>>> data = //this is getting ridiculous
				queryVersions(ids, fields);
		
		final Map<ResolvedObjectIDNoVer, List<Map<String, Object>>> ret =
				new HashMap<ResolvedObjectIDNoVer, List<Map<String,Object>>>();
		
		for (final ResolvedObjectIDNoVer roi: objIDs) {
			final Map<Integer, Map<String, Object>> d = data.get(
					roi.getWorkspaceIdentifier()).get(roi.getId());
			final List<Integer> sorted = new ArrayList<Integer>(d.keySet());
			Collections.sort(sorted);
			final List<Map<String, Object>> r =
					new LinkedList<Map<String,Object>>();
			for (final Integer i: sorted) {
				r.add(d.get(i));
			}
			ret.put(roi, r);
		}
		return ret;
	}
	
	private Map<ResolvedWorkspaceID, Map<Long, Map<Integer, Map<String, Object>>>>
			queryVersions(final Map<ResolvedWorkspaceID, Map<Long, List<Integer>>> ids,
			final Set<String> fields) throws WorkspaceCommunicationException {
		fields.add(Fields.VER_ID);
		fields.add(Fields.VER_VER);
		//disgusting. need to do better.
		//nested or queries are slow per the mongo docs so just query one
		//workspace at a time. If profiling shows this is slow investigate
		//further
		//actually, $or queries just suck it seems. Way faster to do single queries
		final Map<ResolvedWorkspaceID, Map<Long, Map<Integer, Map<String, Object>>>>
			ret = new HashMap<ResolvedWorkspaceID, Map<Long,Map<Integer,Map<String,Object>>>>();
		for (final ResolvedWorkspaceID rwsi: ids.keySet()) {
			ret.put(rwsi, new HashMap<Long, Map<Integer, Map<String,Object>>>());
			for (final Long objectID: ids.get(rwsi).keySet()) {
				ret.get(rwsi).put(objectID,
						new HashMap<Integer, Map<String, Object>>());
				final DBObject q;
				if (ids.get(rwsi).get(objectID).size() == 0) {
					q = new BasicDBObject();
				} else if (ids.get(rwsi).get(objectID).size() == 1) {
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
			}
		}
		return ret;
	}
	
	List<Map<String, Object>> queryCollection(final String collection,
			final DBObject query, final Set<String> fields)
			throws WorkspaceCommunicationException {
		return queryCollection(collection, query, fields, null, -1);
	}
	
	List<Map<String, Object>> queryCollection(final String collection,
	final DBObject query, final Set<String> fields, final int limit)
	throws WorkspaceCommunicationException {
		return queryCollection(collection, query, fields, null, limit);
	}

	List<Map<String, Object>> queryCollection(
			final String collection,
			final DBObject query,
			final Set<String> fields,
			// really shouldn't be necessary, but 2.4 sometimes isn't smart
			final DBObject queryHint,
			final int limit)
			throws WorkspaceCommunicationException {
		final List<Map<String, Object>> result =
				new ArrayList<Map<String,Object>>();
		try {
			final DBCursor im = queryCollectionCursor(
					collection, query, fields, queryHint, limit);
			for (final DBObject o: im) {
				result.add(dbObjectToMap(o));
			}
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		return result;
	}
	
	DBCursor queryCollectionCursor(
			final String collection,
			final DBObject query,
			final Set<String> fields,
			// really shouldn't be necessary, but 2.4 sometimes isn't smart
			final DBObject queryHint,
			final int limit)
			throws WorkspaceCommunicationException {
		final DBObject projection = new BasicDBObject();
		projection.put(Fields.MONGO_ID, 0);
		for (final String field: fields) {
			projection.put(field, 1);
		}
		try {
			final DBCursor im = wsmongo.getCollection(collection)
					.find(query, projection);
			if (limit > 0) {
				im.limit(limit);
			}
			if (queryHint != null) {
				im.hint(queryHint); //currently mdb only supports 1 index
			}
			return im;
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
	}
	
	//since LazyBsonObject.toMap() is not supported
	static Map<String, Object> dbObjectToMap(final DBObject o) {
		final Map<String, Object> m = new HashMap<String, Object>();
		for (final String name: o.keySet()) {
			m.put(name, o.get(name));
		}
		return m;
	}
	
	Map<User, Permission> queryPermissions(
			final ResolvedWorkspaceID rwsi) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		return queryPermissions(rwsi, null);
	}
	
	Map<User, Permission> queryPermissions(
			final ResolvedWorkspaceID rwsi, final Set<User> users) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final Set<ResolvedWorkspaceID> wsis = new HashSet<ResolvedWorkspaceID>();
		wsis.add(rwsi);
		return queryPermissions(wsis, users).get(rwsi);
	}
	
	Map<ResolvedWorkspaceID, Map<User, Permission>> queryPermissions(
			final Set<ResolvedWorkspaceID> rwsis, final Set<User> users) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		return queryPermissions(rwsis, users, Permission.NONE, false);
	}
	
	Map<ResolvedWorkspaceID, Map<User, Permission>> queryPermissions(
			final Set<User> users, final Permission minPerm) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		return queryPermissions(null, users, minPerm, false);
	}
	
	private final static HashSet<String> PROJ_WS_ID_NAME_LOCK_DEL = 
			new HashSet<String>(Arrays.asList(Fields.WS_ID, Fields.WS_NAME,
					Fields.WS_LOCKED, Fields.WS_DEL));
	
	Map<ResolvedWorkspaceID, Map<User, Permission>> queryPermissions(
			final Set<ResolvedWorkspaceID> rwsis,
			final Set<User> users,
			final Permission minPerm,
			final boolean excludeDeletedWorkspaces)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final DBObject query = new BasicDBObject();
		final Map<Long, ResolvedWorkspaceID> idToWS = new HashMap<Long, ResolvedWorkspaceID>();
		if (rwsis != null && rwsis.size() > 0) {
			final Set<Long> wsids = new HashSet<Long>();
			for (final ResolvedWorkspaceID r: rwsis) {
				idToWS.put(r.getID(), r);
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
			query.put(Fields.ACL_PERM, new BasicDBObject("$gte", minPerm.getPermission()));
		}
		final DBObject proj = new BasicDBObject();
		proj.put(Fields.MONGO_ID, 0);
		proj.put(Fields.ACL_USER, 1);
		proj.put(Fields.ACL_PERM, 1);
		proj.put(Fields.ACL_WSID, 1);
		
		final Map<ResolvedWorkspaceID, Map<User, Permission>> wsidToPerms =
				new HashMap<ResolvedWorkspaceID, Map<User, Permission>>();
		final Map<Long, List<DBObject>> noWS = new HashMap<Long, List<DBObject>>();
		try {
			final DBCursor res = wsmongo.getCollection(workspaceACLCollection).find(query, proj);
			for (final DBObject m: res) {
				final Long id = (Long) m.get(Fields.ACL_WSID);
				if (!idToWS.containsKey(id)) {
					if (!noWS.containsKey(id)) {
						noWS.put(id, new LinkedList<DBObject>());
					}
					noWS.get(id).add(m);
				} else {
					final ResolvedWorkspaceID wsid = idToWS.get(id);
					addPerm(wsidToPerms, m, wsid);
				}
			}
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		if (rwsis != null) {
			for (ResolvedWorkspaceID rwsi: rwsis) {
				if (!wsidToPerms.containsKey(rwsi)) {
					wsidToPerms.put(rwsi, new HashMap<User, Permission>());
				}
			}
		}
		if (!noWS.isEmpty()) {
			final Map<Long, Map<String, Object>> ws = queryWorkspacesByID(
					noWS.keySet(), PROJ_WS_ID_NAME_LOCK_DEL, excludeDeletedWorkspaces);
			for (final Long id: ws.keySet()) {
				final ResolvedWorkspaceID wsid = new ResolvedWorkspaceID(
						(Long) ws.get(id).get(Fields.WS_ID),
						(String) ws.get(id).get(Fields.WS_NAME),
						(Boolean) ws.get(id).get(Fields.WS_LOCKED),
						(Boolean) ws.get(id).get(Fields.WS_DEL));
				for (final DBObject m: noWS.get(id)) {
					addPerm(wsidToPerms, m, wsid);
				}
			}
		}
		return wsidToPerms;
	}

	private void addPerm(
			final Map<ResolvedWorkspaceID, Map<User, Permission>> wsidToPerms,
			final DBObject m, final ResolvedWorkspaceID wsid)
			throws CorruptWorkspaceDBException {
		if (!wsidToPerms.containsKey(wsid)) {
			wsidToPerms.put(wsid, new HashMap<User, Permission>());
		}
		wsidToPerms.get(wsid).put(getUser(
				(String) m.get(Fields.ACL_USER)),
				Permission.fromInt((Integer) m.get(Fields.ACL_PERM)));
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
}
