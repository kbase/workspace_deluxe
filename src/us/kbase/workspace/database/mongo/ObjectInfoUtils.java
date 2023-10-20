package us.kbase.workspace.database.mongo;

import static java.util.Objects.requireNonNull;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bson.Document;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.ResolvedObjectID;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.UncheckedUserMetadata;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;

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
	
	public ObjectInfoUtils(final QueryMethods query) {
		this.query = requireNonNull(query, "query argument may not be null");
	}
	
	private static final Set<String> FLDS_LIST_OBJ = Stream.of(
			Fields.OBJ_ID, Fields.OBJ_NAME, Fields.OBJ_DEL, Fields.OBJ_HIDE,
			Fields.OBJ_VCNT, Fields.OBJ_WS_ID).collect(Collectors.toSet());
	
	public Map<Map<String, Object>, ObjectInformation> generateObjectInfo(
			final PermissionSet pset,
			final List<Map<String, Object>> verobjs,
			final boolean includeHidden,
			final boolean includeDeleted,
			final boolean onlyIncludeDeleted,
			final boolean includeAllVers,
			final boolean asAdmin)
			throws WorkspaceCommunicationException {
		final Map<Map<String, Object>, ObjectInformation> ret = new HashMap<>();
		if (verobjs.isEmpty()) {
			return ret;
		}
		final Map<Long, ResolvedWorkspaceID> ids = new HashMap<>();
		for (final ResolvedWorkspaceID rwsi: pset.getWorkspaces()) {
			ids.put(rwsi.getID(), rwsi);
		}
		final Map<Long, Set<Long>> verdata = getObjectIDsFromVersions(verobjs);
		//TODO PERFORMANCE This $or query might be better as multiple individual queries, test
		// e.g. one workspace per query
		final List<Document> orquery = new LinkedList<>();
		for (final Long wsid: verdata.keySet()) {
			final Document query = new Document(Fields.VER_WS_ID, wsid);
			query.put(Fields.VER_ID, new Document("$in", verdata.get(wsid)));
			orquery.add(query);
		}
		final Document objq = new Document("$or", orquery);
		//could include / exclude hidden and deleted objects here? Given reports are hidden
		// and a large chunk of the objects might make sense
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
			final ResolvedObjectID roi,
			final Map<String, Object> ver) {
		return generateObjectInfo(
				roi.getWorkspaceIdentifier(),
				roi.getId(),
				roi.getName(),
				ver);
	}
	
	static ObjectInformation generateObjectInfo(
			final ResolvedWorkspaceID rwsi,
			final long objid,
			final String name,
			// TODO CODE converting from Document to Map is stupid. Stop that
			final Map<String, Object> ver) {
		@SuppressWarnings("unchecked")
		final List<Map<String, String>> meta =
				(List<Map<String, String>>) ver.get(Fields.VER_META);
		@SuppressWarnings("unchecked")
		final List<Map<String, String>> adminmeta =
				(List<Map<String, String>>) ver.get(Fields.VER_ADMINMETA);
		final AbsoluteTypeDefId type = new AbsoluteTypeDefId(
				new TypeDefName((String) ver.get(Fields.VER_TYPE_NAME)),
				(int) ver.get(Fields.VER_TYPE_MAJOR_VERSION),
				(int) ver.get(Fields.VER_TYPE_MINOR_VERSION));
		return ObjectInformation.getBuilder()
				.withObjectID(objid)
				.withObjectName(name)
				.withType(type)
				.withSavedDate((Date) ver.get(Fields.VER_SAVEDATE))
				.withVersion((int) ver.get(Fields.VER_VER))
				.withSavedBy(new WorkspaceUser((String) ver.get(Fields.VER_SAVEDBY)))
				.withWorkspace(rwsi)
				.withChecksum((String) ver.get(Fields.VER_CHKSUM))
				.withSize((long) ver.get(Fields.VER_SIZE))
				.withUserMetadata(
					new UncheckedUserMetadata(metaMongoArrayToHash(meta)))
				.withAdminUserMetadata(
					new UncheckedUserMetadata(metaMongoArrayToHash(adminmeta)))
				.build();
	}
	
	// TODO CODE not clear if this is still necessary with Document vs DBObject
	static Map<String, String> metaMongoArrayToHash(
			final List<? extends Object> meta) {
		final Map<String, String> ret = new HashMap<>();
		if (meta != null) {
			for (final Object o: meta) {
				//frigging mongo
				if (o instanceof Document) {
					final Document dbo = (Document) o;
					ret.put((String) dbo.get(Fields.META_KEY),
							(String) dbo.get(Fields.META_VALUE));
				} else {
					@SuppressWarnings("unchecked")
					final Map<String, String> m = (Map<String, String>) o;
					ret.put(m.get(Fields.META_KEY), m.get(Fields.META_VALUE));
				}
			}
		}
		return ret;
	}
	
	static List<Map<String, String>> metaHashToMongoArray(final Map<String, String> usermeta) {
		final List<Map<String, String>> meta = new ArrayList<>();
		if (usermeta != null) {
			for (String key: usermeta.keySet()) {
				Map<String, String> m = new LinkedHashMap<>(2);
				m.put(Fields.META_KEY, key);
				m.put(Fields.META_VALUE, usermeta.get(key));
				meta.add(m);
			}
		}
		return meta;
	}
	
	private Map<Long, Set<Long>> getObjectIDsFromVersions(
			final List<Map<String, Object>> objs) {
		final Map<Long, Set<Long>> ret = new HashMap<>();
		for (final Map<String, Object> o: objs) {
			final long wsid = (Long) o.get(Fields.VER_WS_ID);
			final long objid = (Long) o.get(Fields.VER_ID);
			if (!ret.containsKey(wsid)) {
				ret.put(wsid, new HashSet<>());
			}
			ret.get(wsid).add(objid);
		}
		return ret;
	}
	
	private Map<Long, Map<Long, Map<String, Object>>> organizeObjData(
			final List<Map<String, Object>> objs) {
		final Map<Long, Map<Long, Map<String, Object>>> ret = new HashMap<>();
		for (final Map<String, Object> o: objs) {
			final long wsid = (Long) o.get(Fields.OBJ_WS_ID);
			final long objid = (Long) o.get(Fields.OBJ_ID);
			if (!ret.containsKey(wsid)) {
				ret.put(wsid, new HashMap<>());
			}
			ret.get(wsid).put(objid, o);
		}
		return ret;
	}
}
