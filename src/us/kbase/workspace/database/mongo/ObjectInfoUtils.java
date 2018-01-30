package us.kbase.workspace.database.mongo;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import us.kbase.workspace.database.GetObjectInformationParameters;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.ResolvedObjectID;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.UncheckedUserMetadata;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

/** A collection of utility methods mainly for generating ObjectInfomation
 * objects from mongo records.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class ObjectInfoUtils {
	
	//TODO TEST unit tests
	//TODO JAVADOC
	
	private final QueryMethods query;
	
	ObjectInfoUtils(final QueryMethods query) {
		if (query == null) {
			throw new NullPointerException("query argument may not be null");
		}
		this.query = query;
	}
	
	private static final Set<String> FLDS_LIST_OBJ_VER = newHashSet(
			Fields.VER_VER, Fields.VER_TYPE, Fields.VER_SAVEDATE,
			Fields.VER_SAVEDBY, Fields.VER_VER, Fields.VER_CHKSUM,
			Fields.VER_SIZE, Fields.VER_ID, Fields.VER_WS_ID);
	
	private static final Set<String> FLDS_LIST_OBJ = newHashSet(
			Fields.OBJ_ID, Fields.OBJ_NAME, Fields.OBJ_DEL, Fields.OBJ_HIDE,
			Fields.OBJ_VCNT, Fields.OBJ_WS_ID);
	
	List<ObjectInformation> filter(
			final GetObjectInformationParameters params)
			throws WorkspaceCommunicationException {
		/* Could make this method more efficient by doing different queries
		 * based on the filters. If there's no filters except the workspace,
		 * for example, just grab all the objects for the workspaces,
		 * filtering out hidden and deleted in the query and pull the most
		 * recent versions for the remaining objects. For now, just go
		 * with a dumb general method and add smarter heuristics as needed.
		 */
		
		// if the limit = 1 don't want to keep querying for 1 object
		// until one is found that's not deleted/hidden/early version
		final int querysize = params.getLimit() < 100 ? 100 :
				params.getLimit();
		final PermissionSet pset = params.getPermissionSet();
		if (pset.isEmpty()) {
			return new LinkedList<ObjectInformation>();
		}
		final DBObject verq = buildQuery(params);
		final DBObject projection = buildProjection(params);
		final DBObject sort = buildSortSpec(params);
		final DBCursor cur = buildCursor(verq, projection, sort);
		
		//querying on versions directly so no need to worry about race 
		//condition where the workspace object was saved but no versions
		//were saved yet
		
		final List<ObjectInformation> ret = new LinkedList<>();
		while (cur.hasNext() && ret.size() < params.getLimit()) {
			final List<Map<String, Object>> verobjs = new ArrayList<>();
			while (cur.hasNext() && verobjs.size() < querysize) {
				try {
					verobjs.add(QueryMethods.dbObjectToMap(cur.next()));
				} catch (MongoException me) {
					throw new WorkspaceCommunicationException(
							"There was a problem communicating with the database", me);
				}
			}
			final Map<Map<String, Object>, ObjectInformation> objs =
					generateObjectInfo(pset, verobjs, params.isShowHidden(),
							params.isShowDeleted(), params.isShowOnlyDeleted(),
							params.isShowAllVersions(), params.asAdmin()
							);
			//maintain the ordering 
			final Iterator<Map<String, Object>> veriter = verobjs.iterator();
			while (veriter.hasNext() && ret.size() < params.getLimit()) {
				final Map<String, Object> v = veriter.next();
				if (objs.containsKey(v)) {
					ret.add(objs.get(v));
				}
			}
		}
		return ret;
	}

	private DBCursor buildCursor(
			final DBObject verq,
			final DBObject projection,
			final  DBObject sort)
			throws WorkspaceCommunicationException {
		final DBCursor cur;
		try {
			cur = query.getDatabase().getCollection(query.getVersionCollection())
					.find(verq, projection).sort(sort);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		return cur;
	}

	private DBObject buildProjection(final GetObjectInformationParameters params) {
		final DBObject projection = new BasicDBObject();
		for (final String field: FLDS_LIST_OBJ_VER) {
			projection.put(field, 1);
		}
		if (params.isIncludeMetaData()) {
			projection.put(Fields.VER_META, 1);
		}
		return projection;
	}
	
	/* Be very careful when changing how sorting works or adding new sorts. You should be well
	 * versed in mongodb indexing and how indexes affect how sorts work. Never allow a sort
	 * that could occur in mongodb memory - this means there's a limit to the amount of data
	 * that can be sorted without an error and therefore errors *will* occur when a sort on more
	 * data than the limit is attempted.
	 */
	private DBObject buildSortSpec(final GetObjectInformationParameters params) {
		final DBObject sort = new BasicDBObject();
		if (params.isObjectIDFiltersOnly()) {
			sort.put(Fields.VER_WS_ID, 1);
			sort.put(Fields.VER_ID, 1);
			sort.put(Fields.VER_VER, -1);
		}
		return sort;
	}

	private DBObject buildQuery(final GetObjectInformationParameters params) {
		final Set<Long> ids = new HashSet<Long>();
		for (final ResolvedWorkspaceID rwsi:
				params.getPermissionSet().getWorkspaces()) {
			ids.add(rwsi.getID());
		}
		final DBObject verq = new BasicDBObject();
		verq.put(Fields.VER_WS_ID, new BasicDBObject("$in", ids));
		if (params.getType() != null) {
			verq.put(Fields.VER_TYPE, new BasicDBObject(
					"$regex", "^" + params.getType().getTypePrefix()));
		}
		if (!params.getSavers().isEmpty()) {
			verq.put(Fields.VER_SAVEDBY, new BasicDBObject(
					"$in", convertWorkspaceUsers(params.getSavers())));
		}
		if (!params.getMetadata().isEmpty()) {
			final List<DBObject> andmetaq = new LinkedList<DBObject>();
			for (final Entry<String, String> e:
					params.getMetadata().getMetadata().entrySet()) {
				final DBObject mentry = new BasicDBObject();
				mentry.put(Fields.META_KEY, e.getKey());
				mentry.put(Fields.META_VALUE, e.getValue());
				andmetaq.add(new BasicDBObject(Fields.VER_META, mentry));
			}
			verq.put("$and", andmetaq); //note more than one entry is untested
		}
		if (params.getBefore() != null || params.getAfter() != null) {
			final DBObject d = new BasicDBObject();
			if (params.getBefore() != null) {
				d.put("$lt", params.getBefore());
			}
			if (params.getAfter() != null) {
				d.put("$gt", params.getAfter());
			}
			verq.put(Fields.VER_SAVEDATE, d);
		}
		if (params.getMinObjectID() > 1 || params.getMaxObjectID() > 0) {
			final DBObject id = new BasicDBObject();
			if (params.getMinObjectID() > 1) {
				id.put("$gte", params.getMinObjectID());
			}
			if (params.getMaxObjectID() > 0) {
				id.put("$lte", params.getMaxObjectID());
			}
			verq.put(Fields.VER_ID, id);
		}
		
		return verq;
	}
	
	Map<Map<String, Object>, ObjectInformation> generateObjectInfo(
			final PermissionSet pset,
			final List<Map<String, Object>> verobjs,
			final boolean includeHidden,
			final boolean includeDeleted,
			final boolean onlyIncludeDeleted,
			final boolean includeAllVers,
			final boolean asAdmin)
			throws WorkspaceCommunicationException {
		final Map<Map<String, Object>, ObjectInformation> ret =
				new HashMap<Map<String, Object>, ObjectInformation>();
		if (verobjs.isEmpty()) {
			return ret;
		}
		final Map<Long, ResolvedWorkspaceID> ids = new HashMap<>();
		for (final ResolvedWorkspaceID rwsi: pset.getWorkspaces()) {
			ids.put(rwsi.getID(), rwsi);
		}
		final Map<Long, Set<Long>> verdata = getObjectIDsFromVersions(verobjs);
		//TODO PERFORMANCE This $or query might be better as multiple individual queries, test
		final List<DBObject> orquery = new LinkedList<DBObject>();
		for (final Long wsid: verdata.keySet()) {
			final DBObject query = new BasicDBObject(Fields.VER_WS_ID, wsid);
			query.put(Fields.VER_ID, new BasicDBObject("$in", verdata.get(wsid)));
			orquery.add(query);
		}
		final DBObject objq = new BasicDBObject("$or", orquery);
		//could include / exclude hidden and deleted objects here? Prob
		// not worth the effort
		//we're querying with known versions, so there's no need to exclude
		//workspace objects with 0 versions
		final Map<Long, Map<Long, Map<String, Object>>> objdata =
				organizeObjData(query.queryCollection(
						query.getObjectCollection(), objq, FLDS_LIST_OBJ));
		for (final Map<String, Object> vo: verobjs) {
			final long wsid = (Long) vo.get(Fields.VER_WS_ID);
			final long id = (Long) vo.get(Fields.VER_ID);
			final int ver = (Integer) vo.get(Fields.VER_VER);
			final Map<String, Object> obj = objdata.get(wsid).get(id);
			final int lastver = (Integer) obj.get(Fields.OBJ_VCNT);
			final ResolvedWorkspaceID rwsi = ids.get(wsid);
			boolean isDeleted = (Boolean) obj.get(Fields.OBJ_DEL);
			if (!includeAllVers && lastver != ver) {
				/* this is tricky. As is, if there's a failure between incrementing
				 * an object ver count and saving the object version no latest
				 * ver will be listed. On the other hand, if we just take
				 * the max ver we'd be adding incorrect latest vers when filters
				 * exclude the real max ver. To do this correctly we've have to
				 * get the max ver for all objects which is really expensive.
				 * Since the failure mode should be very rare and it fixable
				 * by simply reverting the object do nothing for now.
				 */
				continue;
			}
			if ((Boolean) obj.get(Fields.OBJ_HIDE) && !includeHidden) {
				continue;
			}
			if (onlyIncludeDeleted) {
				if (isDeleted && (asAdmin || pset.hasPermission(rwsi, Permission.WRITE))) {
					ret.put(vo, generateObjectInfo(rwsi, id,
							(String) obj.get(Fields.OBJ_NAME), vo));
				}
				continue;
			}
			if (isDeleted && (!includeDeleted ||
					(!asAdmin && !pset.hasPermission(rwsi, Permission.WRITE)))) {
				continue;
			}
			ret.put(vo, generateObjectInfo(rwsi, id, (String) obj.get(Fields.OBJ_NAME), vo));
		}
		return ret;
	}
	
	static ObjectInformation generateObjectInfo(
			final ResolvedObjectID roi, final Map<String, Object> ver) {
		return generateObjectInfo(roi.getWorkspaceIdentifier(), roi.getId(),
				roi.getName(), ver);
	}
	
	static ObjectInformation generateObjectInfo(
			final ResolvedWorkspaceID rwsi, final long objid, final String name,
			final Map<String, Object> ver) {
		@SuppressWarnings("unchecked")
		final List<Map<String, String>> meta =
				(List<Map<String, String>>) ver.get(Fields.VER_META);
		return new ObjectInformation(
				objid,
				name,
				(String) ver.get(Fields.VER_TYPE),
				(Date) ver.get(Fields.VER_SAVEDATE),
				(Integer) ver.get(Fields.VER_VER),
				new WorkspaceUser((String) ver.get(Fields.VER_SAVEDBY)),
				rwsi,
				(String) ver.get(Fields.VER_CHKSUM),
				(Long) ver.get(Fields.VER_SIZE),
				meta == null ? null : new UncheckedUserMetadata(
						metaMongoArrayToHash(meta)));
	}
	
	static Map<String, String> metaMongoArrayToHash(
			final List<? extends Object> meta) {
		final Map<String, String> ret = new HashMap<String, String>();
		if (meta != null) {
			for (final Object o: meta) {
				//frigging mongo
				if (o instanceof DBObject) {
					final DBObject dbo = (DBObject) o;
					ret.put((String) dbo.get(Fields.META_KEY),
							(String) dbo.get(Fields.META_VALUE));
				} else {
					@SuppressWarnings("unchecked")
					final Map<String, String> m = (Map<String, String>) o;
					ret.put(m.get(Fields.META_KEY),
							m.get(Fields.META_VALUE));
				}
			}
		}
		return ret;
	}
	
	static List<Map<String, String>> metaHashToMongoArray(
			final Map<String, String> usermeta) {
		final List<Map<String, String>> meta = 
				new ArrayList<Map<String, String>>();
		if (usermeta != null) {
			for (String key: usermeta.keySet()) {
				Map<String, String> m = new LinkedHashMap<String, String>(2);
				m.put(Fields.META_KEY, key);
				m.put(Fields.META_VALUE, usermeta.get(key));
				meta.add(m);
			}
		}
		return meta;
	}
	
	private Map<Long, Set<Long>> getObjectIDsFromVersions(
			final List<Map<String, Object>> objs) {
		final Map<Long, Set<Long>> ret = new HashMap<Long, Set<Long>>();
		for (final Map<String, Object> o: objs) {
			final long wsid = (Long) o.get(Fields.VER_WS_ID);
			final long objid = (Long) o.get(Fields.VER_ID);
			if (!ret.containsKey(wsid)) {
				ret.put(wsid, new HashSet<Long>());
			}
			ret.get(wsid).add(objid);
		}
		return ret;
	}
	
	private Map<Long, Map<Long, Map<String, Object>>> organizeObjData(
			final List<Map<String, Object>> objs) {
		final Map<Long, Map<Long, Map<String, Object>>> ret =
				new HashMap<Long, Map<Long,Map<String,Object>>>();
		for (final Map<String, Object> o: objs) {
			final long wsid = (Long) o.get(Fields.OBJ_WS_ID);
			final long objid = (Long) o.get(Fields.OBJ_ID);
			if (!ret.containsKey(wsid)) {
				ret.put(wsid, new HashMap<Long, Map<String, Object>>());
			}
			ret.get(wsid).put(objid, o);
		}
		return ret;
	}
	
	/* the following methods are duplicated in MongoWorkspaceDB class, but so
	 * simple not worth worrying about it
	 */
	private List<String> convertWorkspaceUsers(
			final List<WorkspaceUser> owners) {
		final List<String> own = new ArrayList<String>();
		for (final WorkspaceUser wu: owners) {
			own.add(wu.getUser());
		}
		return own;
	}
	
	//http://stackoverflow.com/questions/2041778/initialize-java-hashset-values-by-construction
	@SafeVarargs
	private static <T> Set<T> newHashSet(T... objs) {
		Set<T> set = new HashSet<T>();
		for (T o : objs) {
			set.add(o);
		}
		return set;
	}
}
