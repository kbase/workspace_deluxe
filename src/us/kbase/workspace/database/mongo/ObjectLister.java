package us.kbase.workspace.database.mongo;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

import us.kbase.typedobj.core.TypeDefId;
import us.kbase.workspace.database.ListObjectsParameters.ResolvedListObjectParameters;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;

/** A helper class for listing workspace objects based on a set of filters. Depends
 * on {@link MongoWorkspaceDB} setting up indexes correctly.
 */
public class ObjectLister {
	
	private final DBCollection verCol;
	private final ObjectInfoUtils infoUtils;
	
	/** Create the lister.
	 * @param verCol the MongoDB collection storing workspace object version information.
	 * @param infoUtils an instance of the objects informational utilities class.
	 */
	public ObjectLister(final DBCollection verCol, final ObjectInfoUtils infoUtils) {
		this.verCol = requireNonNull(verCol, "verCol cannot be null");
		this.infoUtils = requireNonNull(infoUtils, "infoUtils cannot be null");
	}
	
	private static final Set<String> FLDS_LIST_OBJ_VER = Stream.of(
			Fields.VER_VER, Fields.VER_TYPE_FULL, Fields.VER_SAVEDATE,
			Fields.VER_SAVEDBY, Fields.VER_CHKSUM, Fields.VER_SIZE,
			Fields.VER_ID, Fields.VER_WS_ID).collect(Collectors.toSet());
	
	/** List objects as per the given parameters.
	 * Objects will be sorted by the workspace, object and version numbers, with descending
	 * versions, if the metadata, savers, and timestamp filters are not supplied. If those
	 * filters are supplied sort behavior is undefined.
	 * @param params the parameters for listing objects.
	 * @return the list of objects.
	 * @throws WorkspaceCommunicationException if the database could not be contacted.
	 */
	public List<ObjectInformation> filter(final ResolvedListObjectParameters params)
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
		final int querysize = requireNonNull(params, "params cannot be null")
				.getLimit() < 100 ? 100 : params.getLimit();
		// TODO CODE experiment with max size for querysize, 10K might slow things down. 1K?
		final PermissionSet pset = params.getPermissionSet();
		final List<ObjectInformation> ret = new LinkedList<>();
		if (pset.isEmpty()) {
			return ret;
		}
		final DBObject verq = buildQuery(params);
		final DBObject projection = buildProjection(params);
		final DBObject sort = buildSortSpec(params);
		
		//querying on versions directly so no need to worry about race 
		//condition where the workspace object was saved but no versions
		//were saved yet
		try (final DBCursor cur = verCol.find(verq, projection)) {
			cur.sort(sort);
			final List<Map<String, Object>> verobjs = new ArrayList<>(querysize);
			while (cur.hasNext() && ret.size() < params.getLimit()) {
				verobjs.clear();
				while (cur.hasNext() && verobjs.size() < querysize) {
					verobjs.add(QueryMethods.dbObjectToMap(cur.next()));
				}
				// this method accesses the DB, so we batch calls to it to reduce transport time
				final Map<Map<String, Object>, ObjectInformation> objs =
						infoUtils.generateObjectInfo(pset, verobjs, params.isShowHidden(),
								params.isShowDeleted(), params.isShowOnlyDeleted(),
								params.isShowAllVersions(), params.asAdmin()
								);
				//maintain the ordering from Mongo
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
	
	private DBObject buildProjection(final ResolvedListObjectParameters params) {
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
	private DBObject buildSortSpec(final ResolvedListObjectParameters params) {
		final DBObject sort = new BasicDBObject();
		if (isSafeForUPASort(params)) {
			addTypeField(sort, params.getType(), () -> 1);
			sort.put(Fields.VER_WS_ID, 1);
			sort.put(Fields.VER_ID, 1);
			sort.put(Fields.VER_VER, -1);
		}
		return sort;
	}
	
	/* Check if there are no filters set other than the object ID and type filters. The object ID
	 * and type filters may or may not be set.
	 * The other filters are the two date filters, the metadata filter, and the savers filter.
	 * @return true if no filters other than the object ID filters are set.
	 */
	private boolean isSafeForUPASort(final ResolvedListObjectParameters params) {
		// be really careful about modifying this function. See notes in this file.
		return !params.getAfter().isPresent()
				&& !params.getBefore().isPresent()
				&& params.getMetadata().isEmpty()
				&& params.getSavers().isEmpty();
	}
	
	private void addTypeField(
			final DBObject toBeModified,
			final Optional<TypeDefId> type,
			final Supplier<Object> value) {
		if (type.isPresent()) {
			// TODO CODE use optionals for typedefid versions
			if (type.get().getMinorVersion() != null) {
				toBeModified.put(Fields.VER_TYPE_FULL, value.get());
			} else if (type.get().getMajorVersion() != null) {
				toBeModified.put(Fields.VER_TYPE_WITH_MAJOR_VERSION, value.get());
			} else {
				toBeModified.put(Fields.VER_TYPE_NAME, value.get());
			}
		}
	}

	private DBObject buildQuery(final ResolvedListObjectParameters params) {
		final List<Long> ids = params.getPermissionSet().getWorkspaces().stream()
				.map(ws -> ws.getID()).distinct().sorted().collect(Collectors.toList());
		final DBObject verq = new BasicDBObject();
		verq.put(Fields.VER_WS_ID, new BasicDBObject("$in", ids));
		addTypeField(verq, params.getType(), () -> params.getType().get().getTypeString());
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
		if (params.getBefore().isPresent() || params.getAfter().isPresent()) {
			final DBObject d = new BasicDBObject();
			// TODO CODE remove date conversion at some point
			// not quite sure what's going on here, but Instants work fine for the integration
			// tests, but fail for unit tests with Mockito. Maybe needs a client bump. Later.
			if (params.getBefore().isPresent()) {
				d.put("$lt", Date.from(params.getBefore().get()));
			}
			if (params.getAfter().isPresent()) {
				d.put("$gt", Date.from(params.getAfter().get()));
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
