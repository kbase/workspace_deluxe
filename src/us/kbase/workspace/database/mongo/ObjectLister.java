package us.kbase.workspace.database.mongo;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

import us.kbase.workspace.database.GetObjectInformationParameters;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;

public class ObjectLister {
	
	//TODO TEST unit tests
	//TODO JAVADOC
	
	private final DBCollection verCol;
	private final ObjectInfoUtils infoUtils;
	
	public ObjectLister(final DBCollection verCol, final ObjectInfoUtils infoUtils) {
		this.verCol = requireNonNull(verCol, "verCol cannot be null");
		this.infoUtils = requireNonNull(infoUtils, "infoUtils cannot be null");
	}
	
	private static final Set<String> FLDS_LIST_OBJ_VER = Stream.of(
			Fields.VER_VER, Fields.VER_TYPE, Fields.VER_SAVEDATE,
			Fields.VER_SAVEDBY, Fields.VER_VER, Fields.VER_CHKSUM,
			Fields.VER_SIZE, Fields.VER_ID, Fields.VER_WS_ID).collect(Collectors.toSet());
	
	List<ObjectInformation> filter(final GetObjectInformationParameters params)
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
		final int querysize = params.getLimit() < 100 ? 100 : params.getLimit();
		final PermissionSet pset = params.getPermissionSet();
		if (pset.isEmpty()) {
			return new LinkedList<>();
		}
		final DBObject verq = buildQuery(params);
		final DBObject projection = buildProjection(params);
		final DBObject sort = buildSortSpec(params);
		
		//querying on versions directly so no need to worry about race 
		//condition where the workspace object was saved but no versions
		//were saved yet
		
		final List<ObjectInformation> ret = new LinkedList<>();
		try (final DBCursor cur = verCol.find(verq, projection)) {
			cur.sort(sort);
			while (cur.hasNext() && ret.size() < params.getLimit()) {
				final List<Map<String, Object>> verobjs = new ArrayList<>();
				while (cur.hasNext() && verobjs.size() < querysize) {
					verobjs.add(QueryMethods.dbObjectToMap(cur.next()));
				}
				// this method accesses the DB, so we group calls to it to reduce transport time
				final Map<Map<String, Object>, ObjectInformation> objs =
						infoUtils.generateObjectInfo(pset, verobjs, params.isShowHidden(),
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
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		return ret;
	}
	
	private DBObject buildProjection(final GetObjectInformationParameters params) {
		final DBObject projection = new BasicDBObject();
		FLDS_LIST_OBJ_VER.forEach(field -> projection.put(field, 1));
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
	 * 
	 * 19/2/13: This is really difficult to test on mongo 3+. There will always be a list
	 * of workspaces in an $in clause, and so the query optimizer is now good enough to always
	 * use the ws/obj/ver index to sort, at least for smaller data sets. It's still potentially
	 * dangerous to add the sort, since that forces the optimizer to use the ws/obj/ver index
	 * (which could return a huge number of results and really slow down the query).
	 */
	private DBObject buildSortSpec(final GetObjectInformationParameters params) {
		final DBObject sort = new BasicDBObject();
		if (isObjectIDFiltersOnly(params)) {
			sort.put(Fields.VER_WS_ID, 1);
			sort.put(Fields.VER_ID, 1);
			sort.put(Fields.VER_VER, -1);
		}
		return sort;
	}
	
	/* Check if there are no filters set other than the object ID filters. The object ID filters
	 * may or may not be set.
	 * The other filters are the two date filters, the metadata filter, the savers filter, and
	 * the type filter.
	 * @return true if no filters other than the object ID filters are set.
	 */
	private boolean isObjectIDFiltersOnly(final GetObjectInformationParameters params) {
		// be really careful about modifying this function. See notes in this file.
		boolean oidFiltersOnly = params.getAfter() == null;
		oidFiltersOnly = oidFiltersOnly && params.getBefore() == null;
		oidFiltersOnly = oidFiltersOnly && params.getMetadata().isEmpty();
		oidFiltersOnly = oidFiltersOnly && params.getSavers().isEmpty();
		// must have workspaces specified
		oidFiltersOnly = oidFiltersOnly && params.getType() == null;
		return oidFiltersOnly;
	}

	private DBObject buildQuery(final GetObjectInformationParameters params) {
		final Set<Long> ids = params.getPermissionSet().getWorkspaces().stream()
				.map(ws -> ws.getID()).collect(Collectors.toSet());
		final DBObject verq = new BasicDBObject();
		verq.put(Fields.VER_WS_ID, new BasicDBObject("$in", ids));
		if (params.getType() != null) {
			verq.put(Fields.VER_TYPE, new BasicDBObject(
					"$regex", "^" + params.getType().getTypePrefix()));
		}
		if (!params.getSavers().isEmpty()) {
			verq.put(Fields.VER_SAVEDBY, new BasicDBObject("$in", params.getSavers().stream()
					.map(s -> s.getUser()).collect(Collectors.toList())));
		}
		if (!params.getMetadata().isEmpty()) {
			final List<DBObject> andmetaq = new LinkedList<DBObject>();
			for (final Entry<String, String> e: params.getMetadata().getMetadata().entrySet()) {
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
}
