package us.kbase.workspace.database.mongo;

import static us.kbase.workspace.database.mongo.ObjectInfoUtils.metaMongoArrayToHash;
import static us.kbase.workspace.database.mongo.ObjectInfoUtils.metaHashToMongoArray;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.jongo.FindAndModify;
import org.jongo.Jongo;
import org.slf4j.LoggerFactory;

import us.kbase.common.utils.Counter;
import us.kbase.common.utils.CountingOutputStream;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.ExtractedMetadata;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.exceptions.ExceededMaxMetadataSizeException;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.RemappedId;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.ObjectReferenceSet;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder.ResourceUsageConfiguration;
import us.kbase.workspace.database.WorkspaceUserMetadata.MetadataException;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.CopyResult;
import us.kbase.workspace.database.GetObjectInformationParameters;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectInfoWithModDate;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.PermissionSet.Builder;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedObjectID;
import us.kbase.workspace.database.ResolvedObjectIDNoVer;
import us.kbase.workspace.database.ResolvedSaveObject;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.TypeAndReference;
import us.kbase.workspace.database.UncheckedUserMetadata;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.WorkspaceUserMetadata.MetadataSizeException;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.DeletedObjectException;
import us.kbase.workspace.database.exceptions.FileCacheIOException;
import us.kbase.workspace.database.exceptions.FileCacheLimitExceededException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.exceptions.WorkspaceDBInitializationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.DuplicateKeyException;
import com.mongodb.MongoException;
import com.mongodb.WriteResult;

public class MongoWorkspaceDB implements WorkspaceDatabase {
	
	// TODO TEST need some lower level tests for this module rather than just integration tests

	public static final String COL_ADMINS = CollectionNames.COL_ADMINS;
	public static final String COL_WS_CNT = CollectionNames.COL_WS_CNT;
	public static final String COL_WORKSPACES = CollectionNames.COL_WORKSPACES;
	public static final String COL_WS_ACLS = CollectionNames.COL_WS_ACLS;
	public static final String COL_WORKSPACE_OBJS = CollectionNames.COL_WORKSPACE_OBJS;
	public static final String COL_WORKSPACE_VERS = CollectionNames.COL_WORKSPACE_VERS;
	public static final String COL_PROVENANCE = CollectionNames.COL_PROVENANCE;
	public static final String COL_CONFIG = CollectionNames.COL_CONFIG;
	public static final AllUsers ALL_USERS = Workspace.ALL_USERS;
	

	//TODO CONFIG this should really be configurable
	private static final long MAX_PROV_SIZE = 1000000;
	private static final int SCHEMA_VERSION = 1;
	
	private ResourceUsageConfiguration rescfg;
	private final DB wsmongo;
	private final Jongo wsjongo;
	private final BlobStore blob;
	private final QueryMethods query;
	private final ObjectInfoUtils objutils;
	private final FindAndModify updateWScounter;
	
	private final TempFilesManager tfm;
	
	private static final String IDX_UNIQ = "unique";
	private static final String IDX_SPARSE = "sparse";
	
	private HashMap<String, List<IndexSpecification>> getIndexSpecs() {
		// should probably rework this and the index spec class
		//hardcoded indexes
		final HashMap<String, List<IndexSpecification>> indexes = new HashMap<>();
		
		//workspaces indexes
		final LinkedList<IndexSpecification> ws = new LinkedList<>();
		//find workspaces you own
		ws.add(idxSpec(Fields.WS_OWNER, 1));
		//find workspaces by permanent id
		ws.add(idxSpec(Fields.WS_ID, 1, IDX_UNIQ));
		// find workspaces by mutable name. Sparse to avoid indexing workspaces without names,
		// which means the workspace is mid-clone. Since null can only be in the unique index
		// once, only workspace could be clones at a time w/o sparse.
		ws.add(idxSpec(Fields.WS_NAME, 1, IDX_UNIQ, IDX_SPARSE));
		//find workspaces by metadata
		ws.add(idxSpec(Fields.WS_META, 1, IDX_SPARSE));
		indexes.put(COL_WORKSPACES, ws);
		
		//workspace acl indexes
		final LinkedList<IndexSpecification> wsACL = new LinkedList<>();
		//get a user's permission for a workspace, index covers queries
		wsACL.add(idxSpec(Fields.ACL_WSID, 1, Fields.ACL_USER, 1, Fields.ACL_PERM, 1, IDX_UNIQ));
		//find workspaces to which a user has some level of permission, index coves queries
		wsACL.add(idxSpec(Fields.ACL_USER, 1, Fields.ACL_PERM, 1, Fields.ACL_WSID, 1));
		indexes.put(COL_WS_ACLS, wsACL);
		
		//workspace object indexes
		final LinkedList<IndexSpecification> wsObj = new LinkedList<>();
		//find objects by workspace id & name
		wsObj.add(idxSpec(Fields.OBJ_WS_ID, 1, Fields.OBJ_NAME, 1, IDX_UNIQ));
		//find object by workspace id & object id
		wsObj.add(idxSpec(Fields.OBJ_WS_ID, 1, Fields.OBJ_ID, 1, IDX_UNIQ));
		//find recently modified objects
		wsObj.add(idxSpec(Fields.OBJ_MODDATE, 1));
		//find object to garbage collect
		wsObj.add(idxSpec(Fields.OBJ_DEL, 1, Fields.OBJ_REFCOUNTS, 1));
		indexes.put(COL_WORKSPACE_OBJS, wsObj);

		//workspace object version indexes
		final LinkedList<IndexSpecification> wsVer = new LinkedList<>();
		//find versions (might not be needed any more given next index, but keep around for now)
		wsVer.add(idxSpec(Fields.VER_WS_ID, 1, Fields.VER_ID, 1, Fields.VER_VER, 1, IDX_UNIQ));
		//find versions and sort descending on version
		wsVer.add(idxSpec(Fields.VER_WS_ID, 1, Fields.VER_ID, 1, Fields.VER_VER, -1, IDX_UNIQ));
		//find versions by data object
		wsVer.add(idxSpec(Fields.VER_TYPE, 1, Fields.VER_CHKSUM, 1));
		//find versions by user
		wsVer.add(idxSpec(Fields.VER_SAVEDBY, 1));
		//determine whether a particular object is referenced by this object
		wsVer.add(idxSpec(Fields.VER_REF, 1, IDX_SPARSE));
		//determine whether a particular object is included in this object's provenance
		wsVer.add(idxSpec(Fields.VER_PROVREF, 1, IDX_SPARSE));
		//find objects that have the same provenance
		wsVer.add(idxSpec(Fields.VER_PROV, 1));
		//find objects by saved date
		wsVer.add(idxSpec(Fields.VER_SAVEDATE, 1));
		//find objects by metadata
		wsVer.add(idxSpec(Fields.VER_META, 1, IDX_SPARSE));
		indexes.put(COL_WORKSPACE_VERS, wsVer);
		
		//no indexes needed for provenance since all lookups are by _id
		
		//admin indexes
		final LinkedList<IndexSpecification> admin = new LinkedList<>();
		//find admins by name
		admin.add(idxSpec(Fields.ADMIN_NAME, 1, IDX_UNIQ));
		indexes.put(COL_ADMINS, admin);
		
		//config indexes
		final LinkedList<IndexSpecification> cfg = new LinkedList<>();
		//ensure only one config object
		cfg.add(idxSpec(Fields.CONFIG_KEY, 1, IDX_UNIQ));
		indexes.put(COL_CONFIG, cfg);
		
		return indexes;
	}
	
	public MongoWorkspaceDB(final DB workspaceDB, final BlobStore blobStore,
			final TempFilesManager tfm)
			throws WorkspaceCommunicationException,
			WorkspaceDBInitializationException, CorruptWorkspaceDBException {
		if (workspaceDB == null || blobStore == null || tfm == null) {
			throw new NullPointerException("No arguments can be null");
		}
		rescfg = new ResourceUsageConfigurationBuilder().build();
		this.tfm = tfm;
		wsmongo = workspaceDB;
		wsjongo = new Jongo(wsmongo);
		query = new QueryMethods(wsmongo, (AllUsers) ALL_USERS, COL_WORKSPACES,
				COL_WORKSPACE_OBJS, COL_WORKSPACE_VERS, COL_WS_ACLS);
		objutils = new ObjectInfoUtils(query);
		blob = blobStore;
		updateWScounter = buildCounterQuery(wsjongo);
		//TODO DBCONSIST check a few random types and make sure they exist
		ensureIndexes();
		checkConfig();
	}
	
	private static class IndexSpecification {
		public DBObject index;
		public DBObject options;
		
		private IndexSpecification(final DBObject index, final DBObject options) {
			this.index = index;
			this.options = options;
		}
	}
	
	// 1 for ascending sort, -1 for descending
	private static IndexSpecification idxSpec(
			final String field, final int ascendingSort,
			final String... options) {
		
		return new IndexSpecification(new BasicDBObject(field, ascendingSort),
				getIndexOptions(options));
	}

	private static IndexSpecification idxSpec(
			final String field1, final int ascendingSort1,
			final String field2, final int ascendingSort2,
			final String... options) {
		return new IndexSpecification(
				new BasicDBObject(field1, ascendingSort1)
						.append(field2, ascendingSort2),
				getIndexOptions(options));
	}

	private static IndexSpecification idxSpec(
			final String field1, final int ascendingSort1,
			final String field2, final int ascendingSort2,
			final String field3, final int ascendingSort3,
			final String... options) {
		return new IndexSpecification(
				new BasicDBObject(field1, ascendingSort1)
					.append(field2, ascendingSort2)
					.append(field3, ascendingSort3),
				getIndexOptions(options));
	}
	
	private static DBObject getIndexOptions(final String[] options) {
		final DBObject opts = new BasicDBObject();
		for (final String s: options) {
			opts.put(s, 1);
		}
		return opts;
	}

	public List<DependencyStatus> status() {
		//note failures are tested manually for now, if you make changes test
		//things still work
		//TODO TEST add tests exercising failures
		final List<DependencyStatus> deps = new LinkedList<>(blob.status());
		final String version;
		try {
			final CommandResult bi = wsmongo.command("buildInfo");
			version = bi.getString("version");
		} catch (MongoException e) {
			LoggerFactory.getLogger(getClass())
				.error("Failed to connect to MongoDB", e);
			deps.add(0, new DependencyStatus(false,
					"Couldn't connect to MongoDB: " + e.getMessage(),
					"MongoDB", "Unknown"));
			return deps;
		}
		deps.add(0, new DependencyStatus(true, "OK", "MongoDB", version));
		return deps;
	}
	
	@Override
	public void setResourceUsageConfiguration(
			final ResourceUsageConfiguration rescfg) {
		this.rescfg = rescfg;
	}
	
	@Override
	public TempFilesManager getTempFilesManager() {
		return tfm;
	}
	
	private void checkConfig() throws WorkspaceCommunicationException,
			WorkspaceDBInitializationException, CorruptWorkspaceDBException {
		final DBObject cfg = new BasicDBObject(
				Fields.CONFIG_KEY, Fields.CONFIG_VALUE);
		cfg.put(Fields.CONFIG_UPDATE, false);
		cfg.put(Fields.CONFIG_SCHEMA_VERSION, SCHEMA_VERSION);
		try {
			wsmongo.getCollection(COL_CONFIG).insert(cfg);
		} catch (DuplicateKeyException dk) {
			//ok, the version doc is already there, this isn't the first
			//startup
			final DBCursor cur = wsmongo.getCollection(COL_CONFIG)
					.find(new BasicDBObject(
							Fields.CONFIG_KEY, Fields.CONFIG_VALUE));
			if (cur.size() != 1) {
				throw new CorruptWorkspaceDBException(
						"Multiple config objects found in the database. " +
						"This should not happen, something is very wrong.");
			}
			final DBObject storedCfg = cur.next();
			if ((Integer)storedCfg.get(Fields.CONFIG_SCHEMA_VERSION) !=
					SCHEMA_VERSION) {
				throw new WorkspaceDBInitializationException(String.format(
						"Incompatible database schema. Server is v%s, DB is v%s",
						SCHEMA_VERSION,
						storedCfg.get(Fields.CONFIG_SCHEMA_VERSION)));
			}
			if ((Boolean)storedCfg.get(Fields.CONFIG_UPDATE)) {
				throw new CorruptWorkspaceDBException(String.format(
						"The database is in the middle of an update from " +
						"v%s of the schema. Aborting startup.", 
						storedCfg.get(Fields.CONFIG_SCHEMA_VERSION)));
			}
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
	}
	
	private void ensureIndexes() throws CorruptWorkspaceDBException {
		final HashMap<String, List<IndexSpecification>> indexes = getIndexSpecs();
		for (final String col: indexes.keySet()) {
//			wsmongo.getCollection(col).resetIndexCache();
			for (final IndexSpecification index: indexes.get(col)) {
				try {
					wsmongo.getCollection(col).createIndex(index.index, index.options);
				} catch (DuplicateKeyException dk) {
					throw new CorruptWorkspaceDBException(
							"Found duplicate index keys in the database, " +
							"aborting startup", dk);
				}
			}
		}
	}
	
	private static FindAndModify buildCounterQuery(final Jongo j) {
		return j.getCollection(COL_WS_CNT)
				.findAndModify(String.format("{%s: #}",
						Fields.CNT_ID), Fields.CNT_ID_VAL)
				.upsert().returnNew()
				.with("{$inc: {" + Fields.CNT_NUM + ": #}}", 1L)
				.projection(String.format("{%s: 1, %s: 0}",
						Fields.CNT_NUM, Fields.MONGO_ID));
	}

	private final static String M_WS_DATE_WTH = String.format(
			"{$set: {%s: #}}", Fields.WS_MODDATE);
	
	private void updateWorkspaceModifiedDate(final ResolvedWorkspaceID rwsi)
			throws WorkspaceCommunicationException {
		try {
			wsjongo.getCollection(COL_WORKSPACES)
				.update(M_WS_ID_QRY, rwsi.getID())
				.with(M_WS_DATE_WTH, new Date());
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
	}

	private static final Set<String> FLDS_CREATE_WS =
			newHashSet(Fields.WS_DEL, Fields.WS_OWNER);
	
	@Override
	public WorkspaceInformation createWorkspace(
			final WorkspaceUser user,
			final String wsname,
			final boolean globalRead,
			final String description,
			final WorkspaceUserMetadata meta)
			throws PreExistingWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		return createWorkspace(user, wsname, globalRead, description, meta,
				false);
	}
	
	private WorkspaceInformation createWorkspace(
			final WorkspaceUser user,
			final String wsname,
			final boolean globalRead,
			final String description,
			final WorkspaceUserMetadata meta,
			final boolean cloning)
			throws PreExistingWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		
		if (meta == null) {
			throw new NullPointerException("meta cannot be null");
		}
		//avoid incrementing the counter if we don't have to
		try {
			final List<Map<String, Object>> ws = query.queryCollection(
					COL_WORKSPACES, new BasicDBObject(Fields.WS_NAME, wsname),
					FLDS_CREATE_WS);
			if (ws.size() == 1) {
				final boolean del = (Boolean) ws.get(0).get(Fields.WS_DEL);
				final String owner = (String) ws.get(0).get(Fields.WS_OWNER);
				String err = String.format(
						"Workspace name %s is already in use", wsname);
				if (del && owner.equals(user.getUser())) {
					err += " by a deleted workspace";
				}
				throw new PreExistingWorkspaceException(err);
			} else if (ws.size() > 1) { //should be impossible
				throw new CorruptWorkspaceDBException(String.format(
						"There is more than one workspace with the name %s",
						wsname));
			}
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		final long count; 
		try {
			count = ((Number) updateWScounter.as(DBObject.class)
					.get(Fields.CNT_NUM)).longValue();
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		final DBObject ws = new BasicDBObject();
		ws.put(Fields.WS_OWNER, user.getUser());
		ws.put(Fields.WS_ID, count);
		final Date moddate = new Date();
		if (cloning) {
			ws.put(Fields.WS_CLONING, true);
		} else {
			//it'd be extremely weird to be told a workspace exists when no one
			//can access it, so don't reserve a name until the clone is done
			ws.put(Fields.WS_NAME, wsname);
			ws.put(Fields.WS_MODDATE, moddate);
		}
		ws.put(Fields.WS_DEL, false);
		ws.put(Fields.WS_NUMOBJ, 0L);
		ws.put(Fields.WS_DESC, description);
		ws.put(Fields.WS_LOCKED, false);
		ws.put(Fields.WS_META, metaHashToMongoArray(meta.getMetadata()));
		try {
			wsmongo.getCollection(COL_WORKSPACES).insert(ws);
		} catch (DuplicateKeyException mdk) {
			// this is almost impossible to test and will probably almost never
			// happen
			throw new PreExistingWorkspaceException(String.format(
					"Workspace name %s is already in use", wsname), mdk);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		if (!cloning) {
			setCreatedWorkspacePermissions(user, globalRead,
					new ResolvedWorkspaceID(count, wsname, false, false));
		}
		return WorkspaceInformation.getBuilder()
				.withID(count)
				.withName(wsname)
				.withOwner(user)
				.withModificationDate(moddate.toInstant())
				.withMaximumObjectID(0L)
				.withUserPermission(Permission.OWNER)
				.withGlobalRead(globalRead)
				.withLocked(false)
				.withUserMetadata(new UncheckedUserMetadata(meta))
				.build();
	}

	private void setCreatedWorkspacePermissions(
			final WorkspaceUser user,
			final boolean globalRead,
			final ResolvedWorkspaceID newWSid)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		setPermissionsForWorkspaceUsers(newWSid, Arrays.asList(user),
				Permission.OWNER, false);
		if (globalRead) {
			setPermissions(newWSid, Arrays.asList((User) ALL_USERS), Permission.READ,
					false);
		}
	}
	
	private static final Set<String> FLDS_WS_META = newHashSet(Fields.WS_META);
	
	private final static String M_WS_META_QRY = String.format(
			"{%s: #, \"%s.%s\": #}", Fields.WS_ID, Fields.WS_META,
			Fields.META_KEY);
	private final static String M_SET_WS_META_WTH = String.format(
			"{$set: {\"%s.$.%s\": #, %s: #}}",
			Fields.WS_META, Fields.META_VALUE, Fields.WS_MODDATE); 
	
	private final static String M_SET_WS_META_NOT_QRY = String.format(
			"{%s: #, \"%s.%s\": {$nin: [#]}}", Fields.WS_ID, Fields.WS_META,
			Fields.META_KEY);
	private final static String M_SET_WS_META_NOT_WTH = String.format(
			"{$push: {%s: {%s: #, %s: #}}, $set: {%s: #}}",
			Fields.WS_META, Fields.META_KEY, Fields.META_VALUE,
			Fields.WS_MODDATE); 
	
	@Override
	public Instant setWorkspaceMeta(final ResolvedWorkspaceID rwsi,
			final WorkspaceUserMetadata newMeta)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		
		if (newMeta == null || newMeta.isEmpty()) {
			throw new IllegalArgumentException("Metadata cannot be null or empty");
		}
		final Map<String, Object> ws = query.queryWorkspace(rwsi, FLDS_WS_META);
		@SuppressWarnings("unchecked")
		final Map<String, String> currMeta = metaMongoArrayToHash(
				(List<Object>) ws.get(Fields.WS_META));
		currMeta.putAll(newMeta.getMetadata());
		try {
			WorkspaceUserMetadata.checkMetadataSize(currMeta);
		} catch (MetadataException me) {
			throw new IllegalArgumentException(String.format(
					"Updated metadata exceeds allowed size of %sB",
					WorkspaceUserMetadata.MAX_METADATA_SIZE));
		}
		
		/* it's possible if this is running at the same time on the same object
		 * that the metadata size could exceed 16k since the size check
		 * happens once at the beginning of the method. That has virtually no 
		 * repercussions whatsoever, so meh.
		 */
		Instant time = null;
		for (final Entry<String, String> e: newMeta.getMetadata().entrySet()) {
			final String key = e.getKey();
			final String value = e.getValue();
			boolean success = false;
			while (!success) { //Danger, Will Robinson! Danger!
				//replace the value if it exists already
				WriteResult wr;
				try {
					time = Instant.now();
					wr = wsjongo.getCollection(COL_WORKSPACES)
							.update(M_WS_META_QRY, rwsi.getID(), key)
							.with(M_SET_WS_META_WTH, value, Date.from(time));
				} catch (MongoException me) {
					throw new WorkspaceCommunicationException(
							"There was a problem communicating with the database",
							me);
				}
				if (wr.getN() == 1) { //ok, it worked
					success = true;
					continue;
				}
				//add the key/value pair to the array
				time = Instant.now();
				try {
					wr = wsjongo.getCollection(COL_WORKSPACES)
							.update(M_SET_WS_META_NOT_QRY, rwsi.getID(), key)
							.with(M_SET_WS_META_NOT_WTH, key, value, Date.from(time));
				} catch (MongoException me) {
					throw new WorkspaceCommunicationException(
							"There was a problem communicating with the database",
							me);
				}
				if (wr.getN() == 1) { //ok, it worked
					success = true;
				}
				/* amazingly, someone added that key to the metadata between the
				   two calls above, so here we go again on our own
				   Should be impossible to get stuck in a loop, but if so add
				   counter and throw error if > 3 or something
				 */
			}
		}
		return time;
	}
	
	
	private static final String M_REM_META_WTH = String.format(
			"{$pull: {%s: {%s: #}}, $set: {%s: #}}",
			Fields.WS_META, Fields.META_KEY, Fields.WS_MODDATE);
	
	@Override
	public Instant removeWorkspaceMetaKey(final ResolvedWorkspaceID rwsi,
			final String key) throws WorkspaceCommunicationException {
		final Instant time = Instant.now();
		try {
			wsjongo.getCollection(COL_WORKSPACES)
					.update(M_WS_META_QRY, rwsi.getID(), key)
					.with(M_REM_META_WTH, key, Date.from(time));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		return time;
	}
	
	private static final Set<String> FLDS_CLONE_WS =
			newHashSet(Fields.OBJ_ID, Fields.OBJ_NAME, Fields.OBJ_DEL, Fields.OBJ_HIDE);
	
	@Override
	public WorkspaceInformation cloneWorkspace(
			final WorkspaceUser user,
			final ResolvedWorkspaceID wsid,
			final String newname,
			final boolean globalRead,
			final String description,
			final WorkspaceUserMetadata meta,
			final Set<ObjectIDNoWSNoVer> exclude)
			throws PreExistingWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException,
			NoSuchObjectException {
		
		// looked at using copyObject to do this but was too messy
		final ResolvedWorkspaceID fromWS = wsid;
		// resolve the object before making a new workspace in case something
		// fails
		final Collection<ResolvedObjectID> resexclude =
				resolveObjectIDs(fromWS, exclude).values();
		final WorkspaceInformation wsinfo = createWorkspace(
				user, newname, globalRead, description, meta, true);
		final ResolvedWorkspaceID toWS = new ResolvedWorkspaceID(wsinfo.getId(),
				wsinfo.getName(), wsinfo.isLocked(), false); //assume it's not deleted already
		final DBObject q = new BasicDBObject(Fields.OBJ_WS_ID, fromWS.getID());
		//skip any objects with no versions, likely a race condition
		//or worse the db went down post version increment pre version save
		//need to move to transactional backend or relationless schema
		q.put(Fields.OBJ_VCNT, new BasicDBObject("$gt", 0));
		q.put(Fields.OBJ_DEL, false);
		addExcludedToCloneQuery(fromWS, resexclude, q);
		final DBObject hint = new BasicDBObject(Fields.OBJ_WS_ID, 1);
		hint.put(Fields.OBJ_ID, 1);
		long maxid = 0;
		try {
			final DBCursor wsobjects = query.queryCollectionCursor(
					COL_WORKSPACE_OBJS, q, FLDS_CLONE_WS, hint, -1);
			for (final DBObject o: wsobjects) {
				final long objid = (Long) o.get(Fields.OBJ_ID);
				final String name = (String) o.get(Fields.OBJ_NAME);
				final boolean hidden = (Boolean) o.get(Fields.OBJ_HIDE);
				final boolean deleted = (Boolean) o.get(Fields.OBJ_DEL);
				maxid = Math.max(maxid, objid);
				final ResolvedObjectIDNoVer roi =
						new ResolvedObjectIDNoVer(fromWS, objid, name, deleted);
				final List<Map<String, Object>> versions;
				try {
					versions = queryAllVersions(
							new HashSet<ResolvedObjectIDNoVer>(Arrays.asList(roi)),
							FLDS_VER_COPYOBJ).get(roi);
				} catch (NoSuchObjectException nsoe) {
					/* The object was saved to the objects collections and the
					 * version was incremented at least once. However, no
					 * versions exist in the version collection. So either a
					 * race condition or the system died before versions could
					 * be saved, so skip it. Really need to move to a backend
					 * with transactions or simplify the schema so it's
					 * relationless.
					 */
					continue;
				}
				for (final Map<String, Object> v: versions) {
					final int ver = (Integer) v.get(Fields.VER_VER);
					v.remove(Fields.MONGO_ID);
					v.put(Fields.VER_SAVEDBY, user.getUser());
					v.put(Fields.VER_RVRT, null);
					v.put(Fields.VER_COPIED, new Reference(fromWS.getID(), objid, ver).toString());
				}
				updateReferenceCountsForVersions(versions);
				saveWorkspaceObject(toWS, objid, name);
				saveObjectVersions(user, toWS, objid, versions, hidden);
			}
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		if (maxid > 0) {
			incrementWorkspaceCounter(toWS, maxid);
		}
		final Instant moddate = updateClonedWorkspaceInformation(
				user, globalRead, toWS.getID(), newname);
		return WorkspaceInformation.getBuilder()
				.withID(wsinfo.getId())
				.withName(newname)
				.withOwner(user)
				.withModificationDate(moddate)
				.withMaximumObjectID(maxid)
				.withUserPermission(Permission.OWNER)
				.withGlobalRead(globalRead)
				.withLocked(wsinfo.isLocked())
				.withUserMetadata(new UncheckedUserMetadata(meta))
				.build();
	}

	// this method expects that the id exists. If it does not it'll throw an
	// IllegalState exception.
	private Instant updateClonedWorkspaceInformation(
			final WorkspaceUser user,
			final boolean globalRead,
			final long id,
			final String newname)
			throws PreExistingWorkspaceException, WorkspaceCommunicationException,
				CorruptWorkspaceDBException {
		
		final DBObject q = new BasicDBObject(Fields.WS_ID, id);

		final Date moddate = new Date();
		final DBObject ws = new BasicDBObject();
		ws.put(Fields.WS_MODDATE, moddate);
		ws.put(Fields.WS_NAME, newname);
		
		final DBObject update = new BasicDBObject(
				"$unset", new BasicDBObject(Fields.WS_CLONING, ""));
		update.put("$set", ws);
		final WriteResult wr;
		try {
			wr = wsmongo.getCollection(COL_WORKSPACES).update(q, update);
		} catch (DuplicateKeyException mdk) {
			throw new PreExistingWorkspaceException(String.format(
					"Workspace name %s is already in use", newname));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		if (wr.getN() != 1) {
			throw new IllegalStateException("A programming error occurred: " +
					"there is no workspace with ID " + id);
		}
		setCreatedWorkspacePermissions(user, globalRead,
				new ResolvedWorkspaceID(id, newname, false, false));
		return moddate.toInstant();
	}

	private void addExcludedToCloneQuery(
			final ResolvedWorkspaceID fromWS,
			final Collection<ResolvedObjectID> resexclude,
			final DBObject q)
			throws WorkspaceCommunicationException {
		if (resexclude == null || resexclude.isEmpty()) {
			return;
		}
		final Set<Long> excludeids = new HashSet<Long>();
		for (final ResolvedObjectID o: resexclude) {
			excludeids.add(o.getId());
		}
		q.put(Fields.OBJ_ID, new BasicDBObject("$nin", excludeids));
	}
	
	private final static String M_LOCK_WS_WTH = String.format("{$set: {%s: #}}",
			Fields.WS_LOCKED);
	
	@Override
	public Instant lockWorkspace(final ResolvedWorkspaceID rwsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		try {
			wsjongo.getCollection(COL_WORKSPACES)
				.update(M_WS_ID_QRY, rwsi.getID())
				.with(M_LOCK_WS_WTH, true);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		// ws mod date isn't changed, maybe it should be?
		return Instant.now();
	}
	
	private static final Set<String> FLDS_VER_COPYOBJ = newHashSet(
			Fields.VER_WS_ID, Fields.VER_ID, Fields.VER_VER,
			Fields.VER_TYPE, Fields.VER_CHKSUM, Fields.VER_SIZE,
			Fields.VER_PROV, Fields.VER_REF, Fields.VER_PROVREF,
			Fields.VER_COPIED, Fields.VER_META, Fields.VER_EXT_IDS);
	
	@Override
	public CopyResult copyObject(
			final WorkspaceUser user,
			final ObjectIDResolvedWS from,
			final ObjectIDResolvedWS to)
			throws NoSuchObjectException, WorkspaceCommunicationException {
		return copyOrRevert(user, from, to, false);
	}
	
	@Override
	public ObjectInformation revertObject(final WorkspaceUser user, final ObjectIDResolvedWS oi)
			throws NoSuchObjectException, WorkspaceCommunicationException {
		return copyOrRevert(user, oi, null, true).getObjectInformation();
	}
		
	private CopyResult copyOrRevert(
			final WorkspaceUser user,
			final ObjectIDResolvedWS from,
			ObjectIDResolvedWS to,
			final boolean revert)
			throws NoSuchObjectException, WorkspaceCommunicationException {
		final ResolvedObjectID rfrom = resolveObjectIDs(
				new HashSet<ObjectIDResolvedWS>(Arrays.asList(from))).get(from);
		final ResolvedObjectID rto;
		if (revert) {
			to = from;
			rto = rfrom;
		} else {
			final ObjectIDResolvedWS toNoVer = to.getId() == null ?
					new ObjectIDResolvedWS(to.getWorkspaceIdentifier(), to.getName()) :
						new ObjectIDResolvedWS(to.getWorkspaceIdentifier(), to.getId());
			rto = resolveObjectIDs(new HashSet<ObjectIDResolvedWS>(Arrays.asList(toNoVer)),
					false, true, false).get(toNoVer); //don't except if there's no object
		}
		if (rto == null && to.getId() != null) {
			throw new NoSuchObjectException(String.format(
					"Copy destination is specified as object id %s in workspace %s which " +
					"does not exist.", to.getId(), to.getWorkspaceIdentifier().getID()), to);
		}
		final boolean copyAll;
		final List<Map<String, Object>> versions;
		if (rto == null && from.getVersion() == null) {
			final ResolvedObjectIDNoVer o = new ResolvedObjectIDNoVer(rfrom);
			versions = queryAllVersions(
					new HashSet<ResolvedObjectIDNoVer>(Arrays.asList(o)),
					FLDS_VER_COPYOBJ).get(o);
			copyAll = true;
		} else {
			versions = Arrays.asList(queryVersions(
					new HashSet<ResolvedObjectID>(Arrays.asList(rfrom)),
					FLDS_VER_COPYOBJ, false).get(rfrom));
			copyAll = false;
		}
		for (final Map<String, Object> v: versions) {
			int ver = (Integer) v.get(Fields.VER_VER);
			v.remove(Fields.MONGO_ID);
			v.put(Fields.VER_SAVEDBY, user.getUser());
			if (revert) {
				v.put(Fields.VER_RVRT, ver);
			} else {
				v.put(Fields.VER_RVRT, null);
				v.put(Fields.VER_COPIED, new Reference(
						rfrom.getWorkspaceIdentifier().getID(), rfrom.getId(), ver).toString());
			}
		}
		updateReferenceCountsForVersions(versions);
		final ResolvedWorkspaceID toWS = to.getWorkspaceIdentifier();
		final long objid;
		if (rto == null) { //need to make a new object
			final long id = incrementWorkspaceCounter(toWS, 1);
			objid = saveWorkspaceObject(toWS, id, to.getName()).id;
		} else {
			objid = rto.getId();
		}
		saveObjectVersions(user, toWS, objid, versions, null);
		final Map<String, Object> info = versions.get(versions.size() - 1);
		updateWorkspaceModifiedDate(toWS);
		final ObjectInformation oi = ObjectInfoUtils.generateObjectInfo(toWS, objid,
				rto == null ? to.getName() : rto.getName(), info);
		return new CopyResult(oi, copyAll);
	}
	
	final private static String M_RENAME_WS_WTH = String.format(
			"{$set: {%s: #, %s: #}}", Fields.WS_NAME, Fields.WS_MODDATE);
	
	@Override
	public Instant renameWorkspace(final ResolvedWorkspaceID rwsi, final String newname)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		if (newname.equals(rwsi.getName())) {
			throw new IllegalArgumentException("Workspace is already named " +
					newname);
		}
		final Instant now = Instant.now();
		try {
			wsjongo.getCollection(COL_WORKSPACES)
					.update(M_WS_ID_QRY, rwsi.getID())
					.with(M_RENAME_WS_WTH, newname, Date.from(now));
		} catch (DuplicateKeyException medk) {
			throw new IllegalArgumentException(
					"There is already a workspace named " + newname);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		return now;
	}
	
	final private static String M_RENAME_OBJ_QRY = String.format(
			"{%s: #, %s: #}", Fields.OBJ_WS_ID, Fields.OBJ_ID);
	final private static String M_RENAME_OBJ_WTH = String.format(
			"{$set: {%s: #, %s: #}}", Fields.OBJ_NAME, Fields.OBJ_MODDATE);
	
	@Override
	public ObjectInfoWithModDate renameObject(
			final ObjectIDResolvedWS oi,
			final String newname)
			throws NoSuchObjectException, WorkspaceCommunicationException {
		Set<ObjectIDResolvedWS> input = new HashSet<ObjectIDResolvedWS>(
				Arrays.asList(oi));
		final ResolvedObjectID roi = resolveObjectIDs(input).get(oi);
		if (newname.equals(roi.getName())) {
			throw new IllegalArgumentException("Object is already named " +
					newname);
		}
		final Instant time = Instant.now();
		try {
			wsjongo.getCollection(COL_WORKSPACE_OBJS)
					.update(M_RENAME_OBJ_QRY,
							roi.getWorkspaceIdentifier().getID(), roi.getId())
					.with(M_RENAME_OBJ_WTH, newname, Date.from(time));
		} catch (DuplicateKeyException medk) {
			throw new IllegalArgumentException(
					"There is already an object in the workspace named " +
							newname);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		final ObjectIDResolvedWS oid = new ObjectIDResolvedWS(
				roi.getWorkspaceIdentifier(), roi.getId(), roi.getVersion());
		input = new HashSet<ObjectIDResolvedWS>(Arrays.asList(oid));
		
		final ObjectInformation oinf =
				getObjectInformation(input, false, true, false, true).get(oid);
		updateWorkspaceModifiedDate(roi.getWorkspaceIdentifier());
		return new ObjectInfoWithModDate(oinf, time);
	}
	
	//projection lists
	private static final Set<String> FLDS_WS_DESC = newHashSet(Fields.WS_DESC);
	private static final Set<String> FLDS_WS_OWNER = newHashSet(Fields.WS_OWNER);
	
	//http://stackoverflow.com/questions/2041778/initialize-java-hashset-values-by-construction
	@SafeVarargs
	private static <T> Set<T> newHashSet(T... objs) {
		Set<T> set = new HashSet<T>();
		for (T o : objs) {
			set.add(o);
		}
		return set;
	}
	
	@Override
	public String getWorkspaceDescription(final ResolvedWorkspaceID rwsi) throws
			CorruptWorkspaceDBException, WorkspaceCommunicationException {
		return (String) query.queryWorkspace(rwsi, FLDS_WS_DESC).get(Fields.WS_DESC);
	}
	
	private final static String M_WS_ID_QRY = String.format("{%s: #}",
			Fields.WS_ID);
	private final static String M_DESC_WTH = String.format(
			"{$set: {%s: #, %s: #}}", Fields.WS_DESC, Fields.WS_MODDATE);
	
	@Override
	public Instant setWorkspaceDescription(final ResolvedWorkspaceID rwsi,
			final String description) throws WorkspaceCommunicationException {
		//TODO CODE generalized method for setting fields?
		final Instant now = Instant.now();
		try {
			wsjongo.getCollection(COL_WORKSPACES)
				.update(M_WS_ID_QRY, rwsi.getID())
				.with(M_DESC_WTH, description, Date.from(now));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		return now;
	}
	
	@Override
	public ResolvedWorkspaceID resolveWorkspace(final WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException {
		return resolveWorkspace(wsi, false);
	}
	
	@Override
	public ResolvedWorkspaceID resolveWorkspace(
			final WorkspaceIdentifier wsi,
			final boolean allowDeleted)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException {
		Set<WorkspaceIdentifier> wsiset = new HashSet<WorkspaceIdentifier>();
		wsiset.add(wsi);
		return resolveWorkspaces(wsiset, allowDeleted, false).get(wsi);
				
	}
	
	@Override
	public Map<WorkspaceIdentifier, ResolvedWorkspaceID> resolveWorkspaces(
			final Set<WorkspaceIdentifier> wsis) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException {
		return resolveWorkspaces(wsis, false);
	}
	
	private static final Set<String> FLDS_WS_ID_NAME_DEL =
			newHashSet(Fields.WS_ID, Fields.WS_NAME, Fields.WS_DEL,
					Fields.WS_LOCKED);
	
	@Override
	public Map<WorkspaceIdentifier, ResolvedWorkspaceID> resolveWorkspaces(
			final Set<WorkspaceIdentifier> wsis,
			final boolean suppressErrors)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException {
		return resolveWorkspaces(wsis, suppressErrors, suppressErrors);
	}
		
	private Map<WorkspaceIdentifier, ResolvedWorkspaceID> resolveWorkspaces(
			final Set<WorkspaceIdentifier> wsis,
			final boolean allowDeleted,
			final boolean allowMissing)
			throws WorkspaceCommunicationException, NoSuchWorkspaceException {
		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> ret =
				new HashMap<WorkspaceIdentifier, ResolvedWorkspaceID>();
		if (wsis.isEmpty()) {
			return ret;
		}
		final Map<WorkspaceIdentifier, Map<String, Object>> res =
				query.queryWorkspacesByIdentifier(wsis, FLDS_WS_ID_NAME_DEL);
		for (final WorkspaceIdentifier wsi: wsis) {
			if (!res.containsKey(wsi)) {
				if (!allowMissing) {
					throw new NoSuchWorkspaceException(String.format(
							"No workspace with %s exists", getWSErrorId(wsi)),
							wsi);
				}
			} else {
				if (!allowDeleted && (Boolean) res.get(wsi).get(Fields.WS_DEL)) {
					throw new NoSuchWorkspaceException("Workspace " +
							wsi.getIdentifierString() + " is deleted", wsi);
				}
				final ResolvedWorkspaceID r = new ResolvedWorkspaceID(
						(Long) res.get(wsi).get(Fields.WS_ID),
						(String) res.get(wsi).get(Fields.WS_NAME),
						(Boolean) res.get(wsi).get(Fields.WS_LOCKED), 
						(Boolean) res.get(wsi).get(Fields.WS_DEL));
				ret.put(wsi, r);
			}
		}
		return ret;
	}
	
	@Override
	public Map<ResolvedWorkspaceID, Map<User, Permission>> getAllPermissions(
			final Set<ResolvedWorkspaceID> rwsis)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		final Map<ResolvedWorkspaceID, Map<User, Permission>> res =
				query.queryPermissions(rwsis, null);
		final Map<ResolvedWorkspaceID, Map<User, Permission>> ret = 
				new HashMap<ResolvedWorkspaceID, Map<User, Permission>>();
		//probably a better way to do this
		for (final ResolvedWorkspaceID r: res.keySet()) {
			ret.put((ResolvedWorkspaceID)r, res.get(r)); 
		}
		return ret;
	}
	
	@Override
	public Permission getPermission(final WorkspaceUser user, final ResolvedWorkspaceID wsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		return getPermissions(user, wsi).getPermission(wsi);
	}
	public PermissionSet getPermissions(final WorkspaceUser user, final ResolvedWorkspaceID rwsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final Set<ResolvedWorkspaceID> wsis =
				new HashSet<ResolvedWorkspaceID>();
		wsis.add(rwsi);
		return getPermissions(user, wsis);
	}
	
	@Override
	public PermissionSet getPermissions(
			final WorkspaceUser user, final Set<ResolvedWorkspaceID> rwsis)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		return getPermissions(user, rwsis, Permission.READ, false, false, false);
	}

	@Override
	public PermissionSet getPermissions(
			final WorkspaceUser user,
			final Permission perm,
			final boolean excludeGlobalRead)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		return getPermissions(user, new HashSet<ResolvedWorkspaceID>(), perm, excludeGlobalRead,
				false, false);
	}
	
	@Override
	public PermissionSet getPermissions(
			final WorkspaceUser user,
			final Set<ResolvedWorkspaceID> rwsis,
			final Permission perm,
			final boolean excludeGlobalRead,
			final boolean excludeDeletedWorkspaces,
			final boolean includeProvidedWorkspaces)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		if (perm == null || Permission.NONE.equals(perm)) {
			throw new IllegalArgumentException(
					"Permission cannot be null or NONE");
		}
		final Map<ResolvedWorkspaceID, Map<User, Permission>> userperms;
		if (user != null) {
			userperms = query.queryPermissions(rwsis, new HashSet<User>(Arrays.asList(user)),
					perm, excludeDeletedWorkspaces);
		} else {
			userperms = new HashMap<ResolvedWorkspaceID, Map<User,Permission>>();
		}
		final Set<User> allusers = new HashSet<User>(Arrays.asList(ALL_USERS));
		final Map<ResolvedWorkspaceID, Map<User, Permission>> globalperms;
		if (excludeGlobalRead || perm.compareTo(Permission.WRITE) >= 0) {
			if (userperms.isEmpty()) {
				globalperms = new HashMap<ResolvedWorkspaceID, Map<User,Permission>>();
			} else {
				globalperms = query.queryPermissions(userperms.keySet(), allusers);
			}
		} else {
			globalperms = query.queryPermissions(rwsis, allusers,
					Permission.READ, excludeDeletedWorkspaces);
		}
		return buildPermissionSet(user, rwsis, userperms, globalperms, includeProvidedWorkspaces);
	}

	private PermissionSet buildPermissionSet(
			final WorkspaceUser user,
			final Set<ResolvedWorkspaceID> rmwsis,
			final Map<ResolvedWorkspaceID, Map<User, Permission>> userperms,
			final Map<ResolvedWorkspaceID, Map<User, Permission>> globalperms,
			final boolean includeProvidedWorkspaces) {
		final Builder pset = PermissionSet.getBuilder(user, ALL_USERS);
		final Set<ResolvedWorkspaceID> local = rmwsis == null ?
				new HashSet<>() : new HashSet<>(rmwsis);
		for (final ResolvedWorkspaceID rwsi: userperms.keySet()) {
			Permission gl = globalperms.get(rwsi) == null ? Permission.NONE :
				globalperms.get(rwsi).get(ALL_USERS);
			gl = gl == null ? Permission.NONE : gl;
			Permission p = userperms.get(rwsi).get(user);
			p = p == null ? Permission.NONE : p;
			if (!p.equals(Permission.NONE) || !gl.equals(Permission.NONE)) {
				pset.withWorkspace(rwsi, p, gl);
				local.remove(rwsi);
			}
			globalperms.remove(rwsi);
		}
		for (final ResolvedWorkspaceID rwsi: globalperms.keySet()) {
			final Permission gl = globalperms.get(rwsi).get(ALL_USERS);
			if (gl != null && !gl.equals(Permission.NONE)) {
				pset.withWorkspace(rwsi, Permission.NONE, gl);
				local.remove(rwsi);
			}
		}
		if (includeProvidedWorkspaces) {
			for (final ResolvedWorkspaceID rwsid: local) {
				if (!pset.hasWorkspace(rwsid)) {
					pset.withUnreadableWorkspace(rwsid);
				}
			}
		}
		return pset.build();
	}
	
	private static String getWSErrorId(final WorkspaceIdentifier wsi) {
		if (wsi.getId() == null) {
			return "name " + wsi.getName();
		}
		return "id " + wsi.getId();
	}
	
	final private static String M_CHOWN_WS_NEWNAME_WTH = String.format(
			"{$set: {%s: #, %s: #, %s: #}}",
			Fields.WS_OWNER, Fields.WS_NAME, Fields.WS_MODDATE);
	final private static String M_CHOWN_WS_WTH = String.format(
			"{$set: {%s: #, %s: #}}",
			Fields.WS_OWNER, Fields.WS_MODDATE);

	@Override
	public Instant setWorkspaceOwner(
			final ResolvedWorkspaceID rwsi,
			final WorkspaceUser owner,
			final WorkspaceUser newUser,
			final Optional<String> newname)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final Instant now = Instant.now();
		try {
			if (!newname.isPresent()) {
				wsjongo.getCollection(COL_WORKSPACES)
						.update(M_WS_ID_QRY, rwsi.getID())
						.with(M_CHOWN_WS_WTH, newUser.getUser(), Date.from(now));
			} else {
				wsjongo.getCollection(COL_WORKSPACES)
					.update(M_WS_ID_QRY, rwsi.getID())
					.with(M_CHOWN_WS_NEWNAME_WTH,
							newUser.getUser(), newname.get(), Date.from(now));
			}
		} catch (DuplicateKeyException medk) {
			throw new IllegalArgumentException(
					"There is already a workspace named " + newname.get());
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		final ResolvedWorkspaceID newRwsi = new ResolvedWorkspaceID(
				rwsi.getID(),
				newname.isPresent() ? newname.get() : rwsi.getName(), false, false);
		setPermissionsForWorkspaceUsers(newRwsi, Arrays.asList(owner), Permission.ADMIN, false);
		setPermissionsForWorkspaceUsers(newRwsi, Arrays.asList(newUser), Permission.OWNER, false);
		return now;
	}
	
	@Override
	public Instant setPermissions(final ResolvedWorkspaceID rwsi,
			final List<WorkspaceUser> users, final Permission perm) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		return setPermissionsForWorkspaceUsers(rwsi, users, perm, true);
	}
	
	@Override
	public Instant setGlobalPermission(final ResolvedWorkspaceID rwsi,
			final Permission perm)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		return setPermissions(rwsi, Arrays.asList((User) ALL_USERS), perm, false);
	}
	
	//wsid must exist as a workspace
	private Instant setPermissionsForWorkspaceUsers(final ResolvedWorkspaceID wsid,
			final List<WorkspaceUser> users, final Permission perm, 
			final boolean checkowner) throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		List<User> u = new ArrayList<User>();
		for (User user: users) {
			u.add(user);
		}
		return setPermissions(wsid, u, perm, checkowner);
		
	}
	
	private static final String M_PERMS_QRY = String.format("{%s: #, %s: #}",
			Fields.ACL_WSID, Fields.ACL_USER);
	private static final String M_PERMS_UPD = String.format("{$set: {%s: #}}",
			Fields.ACL_PERM);
	
	private Instant setPermissions(
			final ResolvedWorkspaceID wsid,
			final List<User> users,
			final Permission perm,
			final boolean checkowner)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		final WorkspaceUser owner;
		if (checkowner) {
			final Map<String, Object> ws =
					query.queryWorkspace(wsid, FLDS_WS_OWNER);
			if (ws == null) {
				throw new CorruptWorkspaceDBException(String.format(
						"Workspace %s was unexpectedly deleted from the database",
						wsid.getID()));
			}
			owner = new WorkspaceUser((String) ws.get(Fields.WS_OWNER));
		} else {
			owner = null;
		}
		for (User user: users) {
			if (owner != null && owner.getUser().equals(user.getUser())) {
				continue; // can't change owner permissions
			}
			try {
				if (perm.equals(Permission.NONE)) {
					wsjongo.getCollection(COL_WS_ACLS).remove(
							M_PERMS_QRY, wsid.getID(), user.getUser());
				} else {
					wsjongo.getCollection(COL_WS_ACLS).update(
							M_PERMS_QRY, wsid.getID(), user.getUser())
							.upsert().with(M_PERMS_UPD, perm.getPermission());
				}
			} catch (MongoException me) {
				throw new WorkspaceCommunicationException(
						"There was a problem communicating with the database", me);
			}
		}
		// hmm. should the workspace mod date be changed when setting perms? Currently not
		return Instant.now();
	}
	
	private static final Set<String> FLDS_WS_NO_DESC = 
			newHashSet(Fields.WS_ID, Fields.WS_NAME, Fields.WS_OWNER,
					Fields.WS_MODDATE, Fields.WS_NUMOBJ, Fields.WS_DEL,
					Fields.WS_LOCKED, Fields.WS_META);
	
	@Override
	public List<WorkspaceInformation> getWorkspaceInformation(
			final PermissionSet pset,
			final List<WorkspaceUser> owners,
			final WorkspaceUserMetadata meta,
			final Date after,
			final Date before,
			final boolean showDeleted, 
			final boolean showOnlyDeleted)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final Map<Long, ResolvedWorkspaceID> rwsis = new HashMap<Long, ResolvedWorkspaceID>();
		for (final ResolvedWorkspaceID rwsi: pset.getWorkspaces()) {
			rwsis.put(rwsi.getID(), rwsi);
		}
		final DBObject q = new BasicDBObject(Fields.WS_ID,
				new BasicDBObject("$in", rwsis.keySet()));
		if (owners != null && !owners.isEmpty()) {
			q.put(Fields.WS_OWNER, new BasicDBObject("$in", convertWorkspaceUsers(owners)));
		}
		if (meta != null && !meta.isEmpty()) {
			final List<DBObject> andmetaq = new LinkedList<DBObject>();
			for (final Entry<String, String> e:
					meta.getMetadata().entrySet()) {
				final DBObject mentry = new BasicDBObject();
				mentry.put(Fields.META_KEY, e.getKey());
				mentry.put(Fields.META_VALUE, e.getValue());
				andmetaq.add(new BasicDBObject(Fields.WS_META, mentry));
			}
			q.put("$and", andmetaq); //note more than one entry is untested
		}
		if (before != null || after != null) {
			final DBObject d = new BasicDBObject();
			if (before != null) {
				d.put("$lt", before);
			}
			if (after != null) {
				d.put("$gt", after);
			}
			q.put(Fields.WS_MODDATE, d);
		}
		final List<Map<String, Object>> ws = query.queryCollection(
				COL_WORKSPACES, q, FLDS_WS_NO_DESC);
		
		final List<WorkspaceInformation> ret = new LinkedList<WorkspaceInformation>();
		for (final Map<String, Object> w: ws) {
			final ResolvedWorkspaceID rwsi = rwsis.get((Long) w.get(Fields.WS_ID));
			final boolean isDeleted = (Boolean) w.get(Fields.WS_DEL);
			if (showOnlyDeleted) {
				if (isDeleted && pset.hasUserPermission(rwsi, Permission.OWNER)) {
					ret.add(generateWSInfo(rwsi, pset, w));
				}
			} else if (!isDeleted ||
					(showDeleted && pset.hasUserPermission(rwsi, Permission.OWNER))) {
				ret.add(generateWSInfo(rwsi, pset, w));
			}
		}
		return ret;
	}

	private List<String> convertWorkspaceUsers(final List<WorkspaceUser> owners) {
		final List<String> own = new ArrayList<String>();
		for (final WorkspaceUser wu: owners) {
			own.add(wu.getUser());
		}
		return own;
	}
	
	@Override
	public WorkspaceUser getWorkspaceOwner(final ResolvedWorkspaceID rwsi)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		final Map<String, Object> ws = query.queryWorkspace(rwsi,
				new HashSet<String>(Arrays.asList(Fields.WS_OWNER)));
		return new WorkspaceUser((String) ws.get(Fields.WS_OWNER));
	}
	
	@Override
	public WorkspaceInformation getWorkspaceInformation(
			final WorkspaceUser user, final ResolvedWorkspaceID rwsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final Map<String, Object> ws = query.queryWorkspace(rwsi, FLDS_WS_NO_DESC);
		final PermissionSet perms = getPermissions(user, rwsi);
		return generateWSInfo(rwsi, perms, ws);
	}

	/* Note if rwsi is not in perm set will return ws info with NONE for both permissions. */
	private WorkspaceInformation generateWSInfo(
			final ResolvedWorkspaceID rwsi,
			final PermissionSet perms,
			final Map<String, Object> wsdata) {
		
		@SuppressWarnings("unchecked")
		final List<Map<String, String>> meta =
				(List<Map<String, String>>) wsdata.get(Fields.WS_META);
		return WorkspaceInformation.getBuilder()
				.withID((Long) wsdata.get(Fields.WS_ID))
				.withName((String) wsdata.get(Fields.WS_NAME))
				.withOwner(new WorkspaceUser((String) wsdata.get(Fields.WS_OWNER)))
				.withModificationDate(((Date) wsdata.get(Fields.WS_MODDATE)).toInstant())
				.withMaximumObjectID((Long) wsdata.get(Fields.WS_NUMOBJ))
				.withUserPermission(perms.getUserPermission(rwsi))
				.withGlobalRead(perms.isWorldReadable(rwsi))
				.withLocked((Boolean) wsdata.get(Fields.WS_LOCKED))
				.withUserMetadata(new UncheckedUserMetadata(metaMongoArrayToHash(meta)))
				.build();
	}
	
	private Map<ObjectIDNoWSNoVer, ResolvedObjectID> resolveObjectIDs(
			final ResolvedWorkspaceID workspaceID,
			final Set<ObjectIDNoWSNoVer> objects) throws
			WorkspaceCommunicationException, NoSuchObjectException {
		return resolveObjectIDs(workspaceID, objects, true, true);
	}
	
	private Map<ObjectIDNoWSNoVer, ResolvedObjectID>
				resolveObjectIDsIgnoreExceptions(
			final ResolvedWorkspaceID workspaceID,
			final Set<ObjectIDNoWSNoVer> objects) throws
			WorkspaceCommunicationException {
		try {
			return resolveObjectIDs(workspaceID, objects, false, false);
		} catch (NoSuchObjectException nsoe) {
			throw new RuntimeException(
					"Threw a NoSuchObjectException when explicitly told not to");
		}
	}
	
	private Map<ObjectIDNoWSNoVer, ResolvedObjectID> resolveObjectIDs(
			final ResolvedWorkspaceID workspaceID,
			final Set<ObjectIDNoWSNoVer> objects,
			final boolean exceptIfDeleted,
			final boolean exceptIfMissing)
			throws WorkspaceCommunicationException, NoSuchObjectException {
		if (objects == null || objects.isEmpty()) {
			return new HashMap<ObjectIDNoWSNoVer, ResolvedObjectID>();
		}
		
		final Map<ObjectIDNoWSNoVer, ObjectIDResolvedWS> queryobjs = 
				new HashMap<ObjectIDNoWSNoVer, ObjectIDResolvedWS>();
		for (final ObjectIDNoWSNoVer o: objects) {
			queryobjs.put(o, new ObjectIDResolvedWS(workspaceID, o));
		}
		final Map<ObjectIDResolvedWS, ResolvedObjectID> res =
				resolveObjectIDs(
					new HashSet<ObjectIDResolvedWS>(queryobjs.values()),
					exceptIfDeleted, exceptIfMissing);
		final Map<ObjectIDNoWSNoVer, ResolvedObjectID> ret = 
				new HashMap<ObjectIDNoWSNoVer, ResolvedObjectID>();
		for (final ObjectIDNoWSNoVer o: objects) {
			if (res.containsKey(queryobjs.get(o))) {
				ret.put(o, res.get(queryobjs.get(o)));
			}
		}
		return ret;
	}
	
	// save object in preexisting object container
	private ObjectInformation saveObjectVersion(final WorkspaceUser user,
			final ResolvedWorkspaceID wsid, final long objectid,
			final ObjectSavePackage pkg)
			throws WorkspaceCommunicationException {
		final Map<String, Object> version = new HashMap<String, Object>();
		version.put(Fields.VER_SAVEDBY, user.getUser());
		version.put(Fields.VER_CHKSUM, pkg.wo.getRep().getMD5().getMD5());
		version.put(Fields.VER_META, metaHashToMongoArray(
				pkg.wo.getUserMeta().getMetadata()));
		version.put(Fields.VER_REF, pkg.refs);
		version.put(Fields.VER_PROVREF, pkg.provrefs);
		version.put(Fields.VER_PROV, pkg.mprov.getMongoId());
		version.put(Fields.VER_TYPE, pkg.wo.getRep().getValidationTypeDefId()
				.getTypeString());
		version.put(Fields.VER_SIZE, pkg.wo.getRep().getRelabeledSize());
		version.put(Fields.VER_RVRT, null);
		version.put(Fields.VER_COPIED, null);
		version.put(Fields.VER_EXT_IDS, extractedIDsToStrings(
				pkg.wo.getExtractedIDs()));
		
		saveObjectVersions(user, wsid, objectid, Arrays.asList(version),
				pkg.wo.isHidden());
		
		return new ObjectInformation(
				objectid,
				pkg.name,
				pkg.wo.getRep().getValidationTypeDefId().getTypeString(),
				(Date) version.get(Fields.VER_SAVEDATE),
				(Integer) version.get(Fields.VER_VER),
				user,
				wsid,
				pkg.wo.getRep().getMD5().getMD5(),
				pkg.wo.getRep().getRelabeledSize(),
				new UncheckedUserMetadata(pkg.wo.getUserMeta()));
	}

	private Map<String, Set<String>> extractedIDsToStrings(
			Map<IdReferenceType, Set<RemappedId>> extractedIDs) {
		Map<String, Set<String>> ret = new HashMap<String, Set<String>>();
		for (final IdReferenceType t: extractedIDs.keySet()) {
			ret.put(t.getType(), new HashSet<String>());
			for (final RemappedId i: extractedIDs.get(t)) {
				ret.get(t.getType()).add(i.getId());
			}
		}
		return ret;
	}


	

	private static final String M_SAVEINS_QRY = String.format("{%s: #, %s: #}",
			Fields.OBJ_WS_ID, Fields.OBJ_ID);
	private static final String M_SAVEINS_PROJ = String.format("{%s: 1, %s: 0}",
			Fields.OBJ_VCNT, Fields.MONGO_ID);
	private static final String M_SAVEINS_WTH = String.format(
			"{$inc: {%s: #}, $set: {%s: false, %s: #, %s: null, %s: #}, $push: {%s: {$each: #}}}",
			Fields.OBJ_VCNT, Fields.OBJ_DEL, Fields.OBJ_MODDATE,
			Fields.OBJ_LATEST, Fields.OBJ_HIDE, Fields.OBJ_REFCOUNTS);
	private static final String M_SAVEINS_NO_HIDE_WTH = String.format(
			"{$inc: {%s: #}, $set: {%s: false, %s: #, %s: null}, $push: {%s: {$each: #}}}",
			Fields.OBJ_VCNT, Fields.OBJ_DEL, Fields.OBJ_MODDATE,
			Fields.OBJ_LATEST, Fields.OBJ_REFCOUNTS);
	
	private void saveObjectVersions(final WorkspaceUser user,
			final ResolvedWorkspaceID wsid, final long objectid,
			final List<Map<String, Object>> versions, final Boolean hidden)
			throws WorkspaceCommunicationException {
		// collection objects might be batchable if saves are slow
		/* TODO BUG deal with rare failure modes below as much as possible at some point. Not high prio since rare
		 * 1) save an object, crash w/ 0 versions. 2) increment versions, crash w/o saving
		 * check all places counter incremented (ws, obj, ver) to see if any other problems
		 * known issues in resolveObjects and listObjects
		 * can't necc count on the fact that vercount or latestVersion is accurate
		 * ignore listObjs for now, in resolveObjs mark vers with class and
		 * have queryVersions pull the right version if it's missing. Make a test for this.
		 * Have queryVersions revert to the newest version if the latest is missing, autorevert
		 * 
		 * None of the above addresses the object w/ 0 versions failure. Not sure what to do about that.
		 * 
		*/
		int ver;
		final List<Integer> zeros = new LinkedList<Integer>();
		for (int i = 0; i < versions.size(); i++) {
			zeros.add(0);
		}
		final Date saved = new Date();
		try {
			final FindAndModify q = wsjongo.getCollection(COL_WORKSPACE_OBJS)
					.findAndModify(M_SAVEINS_QRY, wsid.getID(), objectid)
					.returnNew();
			if (hidden == null) {
				q.with(M_SAVEINS_NO_HIDE_WTH, versions.size(),
						saved, zeros);
			} else {
				q.with(M_SAVEINS_WTH, versions.size(), saved,
						hidden, zeros);
			}
			ver = (Integer) q
					.projection(M_SAVEINS_PROJ).as(DBObject.class)
					.get(Fields.OBJ_VCNT)
					- versions.size() + 1;
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		//TODO look into why saving array of maps via List.ToArray() /w Jongo makes Lazy?Objects return, which screw up everything
		final List<DBObject> dbo = new LinkedList<DBObject>();
		for (final Map<String, Object> v: versions) {
			v.put(Fields.VER_SAVEDATE, saved);
			v.put(Fields.VER_WS_ID, wsid.getID());
			v.put(Fields.VER_ID, objectid);
			v.put(Fields.VER_VER, ver++);
			final DBObject d = new BasicDBObject();
			for (final Entry<String, Object> e: v.entrySet()) {
				d.put(e.getKey(), e.getValue());
			}
			dbo.add(d);
		}

		try {
			wsmongo.getCollection(COL_WORKSPACE_VERS).insert(dbo);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
	}
	
	//save brand new object - create container
	//objectid *must not exist* in the workspace otherwise this method will recurse indefinitely
	//the workspace must exist
	private IDName saveWorkspaceObject(
			final ResolvedWorkspaceID wsid,
			final long objectid,
			final String name)
			throws WorkspaceCommunicationException {
		if (name == null) {
			throw new NullPointerException("name");
		}
		final DBObject dbo = new BasicDBObject();
		dbo.put(Fields.OBJ_WS_ID, wsid.getID());
		dbo.put(Fields.OBJ_ID, objectid);
		dbo.put(Fields.OBJ_VCNT, 0); //Integer
		dbo.put(Fields.OBJ_REFCOUNTS, new LinkedList<Integer>());
		dbo.put(Fields.OBJ_NAME, name);
		dbo.put(Fields.OBJ_LATEST, null); //TODO DBUPDATE remove this field. Deleting versions is out, just delete the entire object.
		dbo.put(Fields.OBJ_DEL, false);
		dbo.put(Fields.OBJ_HIDE, false);
		try {
			//maybe could speed things up with batch inserts but dealing with
			//errors would really suck
			//do this later if it becomes a bottleneck
			wsmongo.getCollection(COL_WORKSPACE_OBJS).insert(dbo);
		} catch (DuplicateKeyException dk) {
			//ok, someone must've just this second added this name to an object
			//asshole
			//this should be a rare event
			//TODO BUG if id dupe throw exception, stack overflow otherwise. Can't actually happen unless bug in code though.
			final ObjectIDNoWSNoVer o = new ObjectIDNoWSNoVer(name);
			final Map<ObjectIDNoWSNoVer, ResolvedObjectID> objID =
					resolveObjectIDsIgnoreExceptions(wsid,
							new HashSet<ObjectIDNoWSNoVer>(Arrays.asList(o)));
			if (objID.isEmpty()) {
				//oh ffs, name deleted again, try again
				return saveWorkspaceObject(wsid, objectid, name);
			}
			//save version via the id associated with our name which already exists
			return new IDName(objID.get(o).getId(), objID.get(o).getName());
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		return new IDName(objectid, name);
	}
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	private static String getObjectErrorId(final ObjectIDNoWSNoVer oi, final int objcount) {
		return "#" + objcount + ", " + oi.getIdentifierString();
	}
	
	//at this point the objects are expected to be validated and references rewritten
	private List<ObjectSavePackage> saveObjectsBuildPackages(
			final List<ResolvedSaveObject> objects) {
		//this method must maintain the order of the objects
		int objnum = 1;
		final List<ObjectSavePackage> ret = new LinkedList<ObjectSavePackage>();
		for (ResolvedSaveObject o: objects) {
			if (o.getRep().getValidationTypeDefId().getMd5() != null) {
				throw new RuntimeException("MD5 types are not accepted");
			}
			final ObjectSavePackage pkg = new ObjectSavePackage();
			pkg.refs = refsToString(o.getRefs());
			//cannot do by combining in one set since a non-MongoReference
			//could be overwritten by a MongoReference if they have the same
			//hash
			pkg.provrefs = refsToString(o.getProvRefs());
			pkg.wo = o;
			checkObjectLength(o.getProvenance(), MAX_PROV_SIZE,
					o.getObjectIdentifier(), objnum, "provenance");
			
			try {
				// TODO improved handling of ExceededMaxMetadataException - what's actually needed here? Seems ok as is
				ExtractedMetadata extract = o.getRep()
						.extractMetadata(
								WorkspaceUserMetadata.MAX_METADATA_SIZE);
				pkg.wo.addUserMeta(extract.getMetadata());
			} catch (ExceededMaxMetadataSizeException e) {
				throw new IllegalArgumentException(String.format(
						"Object %s: %s",
						getObjectErrorId(o.getObjectIdentifier(), objnum),
						e.getMessage()), e);
			} catch (MetadataSizeException mse) {
				throw new IllegalArgumentException(String.format(
						"Object %s: The user-provided metadata, when " +
						"updated with object-extracted metadata, exceeds " +
						"the allowed maximum of %sB",
						getObjectErrorId(o.getObjectIdentifier(), objnum),
						WorkspaceUserMetadata.MAX_METADATA_SIZE)); 
			} catch (MetadataException me) {
				throw new IllegalArgumentException(String.format(
						"Object %s: %s",
						getObjectErrorId(o.getObjectIdentifier(), objnum),
						me.getMessage()), me);
			}
			ret.add(pkg);
			objnum++;
		}
		return ret;
	}
	
	//is there some way to combine these with generics?
	private Set<String> refsToString(final Set<Reference> refs) {
		final Set<String> newrefs = new HashSet<String>();
		refsToString(refs, newrefs);
		return newrefs;
	}
	
	//order must be maintained
	private List<String> refsToString(final List<Reference> refs) {
		final List<String> newrefs = new LinkedList<String>();
		refsToString(refs, newrefs);
		return newrefs;
	}

	private void refsToString(final Collection<Reference> refs,
			final Collection<String> newrefs) {
		for (final Reference r: refs) {
			newrefs.add(r.toString());
		}
	}

	private void checkObjectLength(final Object o, final long max,
			final ObjectIDNoWSNoVer oi, final int objnum,
			final String objtype) {
		final CountingOutputStream cos = new CountingOutputStream();
		try {
			//writes in UTF8
			MAPPER.writeValue(cos, o);
		} catch (IOException ioe) {
			throw new RuntimeException("something's broken", ioe);
		} finally {
			try {
				cos.close();
			} catch (IOException ioe) {
				throw new RuntimeException("something's broken", ioe);
			}
		}
		if (cos.getSize() > max) {
			throw new IllegalArgumentException(String.format(
					"Object %s %s size %s exceeds limit of %s",
					getObjectErrorId(oi, objnum), objtype, cos.getSize(), max));
		}
	}
	
	private static final String M_SAVE_WTH = String.format("{$inc: {%s: #}}",
					Fields.WS_NUMOBJ);
	private static final String M_SAVE_PROJ = String.format("{%s: 1, %s: 0}",
			Fields.WS_NUMOBJ, Fields.MONGO_ID);
			
	//at this point the objects are expected to be validated and references rewritten
	@Override
	public List<ObjectInformation> saveObjects(final WorkspaceUser user, 
			final ResolvedWorkspaceID rwsi,
			final List<ResolvedSaveObject> objects)
			throws WorkspaceCommunicationException,
			NoSuchObjectException {
		//TODO CODE break this up
		//this method must maintain the order of the objects
		
		final List<ObjectSavePackage> packages = saveObjectsBuildPackages(
				objects);
		final Map<ObjectIDNoWSNoVer, List<ObjectSavePackage>> idToPkg =
				new HashMap<ObjectIDNoWSNoVer, List<ObjectSavePackage>>();
		
		//list all the save packages by object id/name
		for (final ObjectSavePackage p: packages) {
			final ObjectIDNoWSNoVer o = p.wo.getObjectIdentifier();
			if (idToPkg.get(o) == null) {
				idToPkg.put(o, new ArrayList<ObjectSavePackage>());
			}
			idToPkg.get(o).add(p);
		}
		
		//confirm object IDs exist or get the id for a name, if any
		final Map<ObjectIDNoWSNoVer, ResolvedObjectID> objIDs =
				resolveObjectIDsIgnoreExceptions(rwsi, idToPkg.keySet());
		
		//check each id or name provided by the user
		int newobjects = 0;
		for (ObjectIDNoWSNoVer o: idToPkg.keySet()) {
			if (!objIDs.containsKey(o)) {
				//the id or name wasn't found
				if (o.getId().isPresent()) {
					//no such id, punt
					throw new NoSuchObjectException("There is no object with id " +
							o.getId().get(), new ObjectIDResolvedWS(rwsi, o.getId().get()));
				} else {
					//no such name, add the unconfirmed name to all the packages
					// and increment the counter for the object ids we need
					for (ObjectSavePackage pkg: idToPkg.get(o)) {
						pkg.name = o.getName().get();
					}
					newobjects++;
				}
			} else {
				//confirmed either the ID or the name, add the confirmed name to the package
				for (ObjectSavePackage pkg: idToPkg.get(o)) {
					pkg.name = objIDs.get(o).getName();
				}
			}
		}
		//at this point everything should be ready to save, only comm errors
		//can stop us now, the world is doomed
		saveData(rwsi, packages);
		saveProvenance(packages);
		updateReferenceCounts(packages);
		long newid = incrementWorkspaceCounter(rwsi, newobjects);
		/*  alternate impl: 1) make all save objects 2) increment all version
		 *  counters 3) batch save versions
		 *  This probably won't help much. Firstly, saving the same object
		 *  multiple times (e.g. save over the same object in the same
		 *  saveObjects call) is going to be a rare op - who wants to do that?
		 *  Hence batching up the version increments is probably not going to
		 *  help much.
		 *  Secondly, the write lock is on a per document basis, so batching
		 *  writes has no effect on write locking.
		 *  That means that the gain from batching writes is removal of the 
		 *  flight time to/from the server between each object. This may
		 *  be significant for many small objects, but is probably
		 *  insignificant for a few objects, or many large objects.
		 *  Summary: probably not worth the trouble and increase in code
		 *  complexity.
		 */
		final List<ObjectInformation> ret = new ArrayList<ObjectInformation>();
		final Map<String, Long> seenNames = new HashMap<String, Long>();
		for (final ObjectSavePackage p: packages) {
			final ObjectIDNoWSNoVer oi = p.wo.getObjectIdentifier();
			if (oi.getId().isPresent()) { //confirmed ok id
				ret.add(saveObjectVersion(user, rwsi, oi.getId().get(), p));
			} else if (objIDs.get(oi) != null) {//given name translated to id
				ret.add(saveObjectVersion(user, rwsi, objIDs.get(oi).getId(), p));
			} else if (seenNames.containsKey(oi.getName().get())) {
				//we've already generated an id for this name
				ret.add(saveObjectVersion(user, rwsi, seenNames.get(oi.getName().get()), p));
			} else {//new name, need to generate new id
				final IDName obj = saveWorkspaceObject(rwsi, newid++, oi.getName().get());
				p.name = obj.name;
				seenNames.put(obj.name, obj.id);
				ret.add(saveObjectVersion(user, rwsi, obj.id, p));
			}
		}
		updateWorkspaceModifiedDate(rwsi);
		return ret;
	}

	//returns starting object number
	private long incrementWorkspaceCounter(final ResolvedWorkspaceID wsidmongo,
			final long newobjects) throws WorkspaceCommunicationException {
		final long lastid;
		try {
			lastid = ((Number) wsjongo.getCollection(COL_WORKSPACES)
					.findAndModify(M_WS_ID_QRY, wsidmongo.getID())
					.returnNew().with(M_SAVE_WTH, newobjects)
					.projection(M_SAVE_PROJ)
					.as(DBObject.class).get(Fields.WS_NUMOBJ)).longValue();
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		long newid = lastid - newobjects + 1;
		return newid;
	}
	
	private void saveProvenance(final List<ObjectSavePackage> packages)
			throws WorkspaceCommunicationException {
		final List<MongoProvenance> prov = new LinkedList<MongoProvenance>();
		for (final ObjectSavePackage p: packages) {
			final MongoProvenance mp = new MongoProvenance(
					p.wo.getProvenance());
			prov.add(mp);
			p.mprov = mp;
		}
		try {
			wsjongo.getCollection(COL_PROVENANCE).insert((Object[])
					prov.toArray(new MongoProvenance[prov.size()]));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
	}

	private static class VerCount {
		final public int ver;
		final public int count;

		public VerCount (final int ver, final int count) {
			this.ver = ver;
			this.count = count;
		}
		
		@Override
		public String toString() {
			return "VerCount [ver=" + ver + ", count=" + count + "]";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + count;
			result = prime * result + ver;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			VerCount other = (VerCount) obj;
			if (count != other.count)
				return false;
			if (ver != other.ver)
				return false;
			return true;
		}
	}
	
	private void updateReferenceCounts(final List<ObjectSavePackage> packages)
			throws WorkspaceCommunicationException {
		//TODO GC when garbage collection working much more testing of these methods
		final Map<Long, Map<Long, Map<Integer, Counter>>> refcounts = 
				countReferences(packages);
		/* since the version numbers are probably highly skewed towards 1 and
		 * the reference counts are also highly skewed towards 1 we can 
		 * probably minimize the number of updates by running one update
		 * per version/count combination
		 */
		updateReferenceCounts(refcounts);
	}
	
	private void updateReferenceCountsForVersions(
			final List<Map<String, Object>> versions)
			throws WorkspaceCommunicationException {
		//TODO GC when garbage collection working much more testing of these methods
		final Map<Long, Map<Long, Map<Integer, Counter>>> refcounts = 
				countReferencesForVersions(versions);
		/* since the version numbers are probably highly skewed towards 1 and
		 * the reference counts are also highly skewed towards 1 we can 
		 * probably minimize the number of updates by running one update
		 * per version/count combination
		 */
		updateReferenceCounts(refcounts);
	}

	private void updateReferenceCounts(
			final Map<Long, Map<Long, Map<Integer, Counter>>> refcounts)
			throws WorkspaceCommunicationException {
		final Map<VerCount, Map<Long, List<Long>>> queries = 
				new HashMap<VerCount, Map<Long,List<Long>>>();
		for (final Long ws: refcounts.keySet()) {
			for (final Long obj: refcounts.get(ws).keySet()) {
				for (final Integer ver: refcounts.get(ws).get(obj).keySet()) {
					final VerCount vc = new VerCount(ver,
							refcounts.get(ws).get(obj).get(ver).getValue());
					if (!queries.containsKey(vc)) {
						queries.put(vc, new HashMap<Long, List<Long>>());
					}
					if (!queries.get(vc).containsKey(ws)) {
						queries.get(vc).put(ws, new LinkedList<Long>());
					}
					queries.get(vc).get(ws).add(obj);
				}
			}
		}
		for (final VerCount vc: queries.keySet()) {
			updateReferenceCounts(vc, queries.get(vc));
		}
	}

	private void updateReferenceCounts(final VerCount vc,
			final Map<Long, List<Long>> wsToObjs)
			throws WorkspaceCommunicationException {
		final DBObject update = new BasicDBObject("$inc",
				new BasicDBObject(Fields.OBJ_REFCOUNTS + "." + (vc.ver - 1),
						vc.count));
		final List<DBObject> orquery = new LinkedList<DBObject>();
		for (final Long ws: wsToObjs.keySet()) {
			final DBObject query = new BasicDBObject(Fields.OBJ_WS_ID, ws);
			query.put(Fields.OBJ_ID, new BasicDBObject("$in",
					wsToObjs.get(ws)));
			orquery.add(query);
		}
		try {
			wsmongo.getCollection(COL_WORKSPACE_OBJS).update(
					new BasicDBObject("$or", orquery), update, false, true);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
	}

	private Map<Long, Map<Long, Map<Integer, Counter>>> countReferences(
			final List<ObjectSavePackage> packages) {
		final Map<Long, Map<Long, Map<Integer, Counter>>> refcounts =
				new HashMap<Long, Map<Long,Map<Integer,Counter>>>();
		for (final ObjectSavePackage p: packages) {
			//these were checked to be MongoReferences in saveObjectBuildPackages
			final Set<Reference> refs = new HashSet<Reference>();
			refs.addAll(p.wo.getRefs());
			refs.addAll(p.wo.getProvRefs());
			countReferences(refcounts, refs);
		}
		return refcounts;
	}
	
	private Map<Long, Map<Long, Map<Integer, Counter>>> countReferencesForVersions(
			final List<Map<String, Object>> versions) {
		final Map<Long, Map<Long, Map<Integer, Counter>>> refcounts =
				new HashMap<Long, Map<Long,Map<Integer,Counter>>>();
		for (final Map<String, Object> p: versions) {
			//these were checked to be MongoReferences in saveObjectBuildPackages
			final Set<Reference> refs = new HashSet<Reference>();
			@SuppressWarnings("unchecked")
			final List<String> objrefs = (List<String>) p.get(Fields.VER_REF);
			@SuppressWarnings("unchecked")
			final List<String> provrefs = (List<String>) p.get(Fields.VER_PROVREF);
//			objrefs.addAll(provrefs); //DON'T DO THIS YOU MORON
			for (final String s: objrefs) {
				refs.add(new Reference(s));
			}
			for (final String s: provrefs) {
				refs.add(new Reference(s));
			}
			countReferences(refcounts, refs);
		}
		return refcounts;
	}

	private void countReferences(
			final Map<Long, Map<Long, Map<Integer, Counter>>> refcounts,
			final Set<Reference> refs) {
		for (final Reference r: refs) {
			if (!refcounts.containsKey(r.getWorkspaceID())) {
				refcounts.put(r.getWorkspaceID(),
						new HashMap<Long, Map<Integer, Counter>>());
			}
			if (!refcounts.get(r.getWorkspaceID())
					.containsKey(r.getObjectID())) {
				refcounts.get(r.getWorkspaceID()).put(r.getObjectID(),
						new HashMap<Integer, Counter>());
			}
			if (!refcounts.get(r.getWorkspaceID()).get(r.getObjectID())
					.containsKey(r.getVersion())) {
				refcounts.get(r.getWorkspaceID()).get(r.getObjectID())
					.put(r.getVersion(), new Counter());
			}
			refcounts.get(r.getWorkspaceID()).get(r.getObjectID())
				.get(r.getVersion()).increment();
		}
	}

	private void saveData(
			final ResolvedWorkspaceID workspaceid,
			final List<ObjectSavePackage> data)
			throws WorkspaceCommunicationException {
		try {
			for (ObjectSavePackage p: data) {
				final String md5 = p.wo.getRep().getMD5().getMD5();
				try (final InputStream is = p.wo.getRep().getInputStream()) {
					blob.saveBlob(new MD5(md5), is, true); //always sorted in 0.2.0+
				} catch (BlobStoreCommunicationException e) {
					throw new WorkspaceCommunicationException(
							e.getLocalizedMessage(), e);
				} catch (BlobStoreAuthorizationException e) {
					throw new WorkspaceCommunicationException(
							"Authorization error communicating with the backend storage system",
							e);
				} catch (IOException ioe) {
					// closing the input stream failed - nothing can be done.
					// CAUTION - if you change this method, make sure you
					// don't add any actions that throw IOEs or they'll be
					// ignored here.
				}
			}
		} finally {
			for (final ObjectSavePackage o: data) {
				try {
					o.wo.getRep().destroyCachedResources();
				} catch (RuntimeException | Error e) {
					//ok, we just possibly left a temp file on disk,
					//but it's not worth interrupting the entire call for
				}
			}
		}
	}

	private static final Set<String> FLDS_VER_GET_OBJECT = newHashSet(
			Fields.VER_VER, Fields.VER_META, Fields.VER_TYPE,
			Fields.VER_SAVEDATE, Fields.VER_SAVEDBY,
			Fields.VER_CHKSUM, Fields.VER_SIZE, Fields.VER_PROV,
			Fields.VER_PROVREF, Fields.VER_REF, Fields.VER_EXT_IDS,
			Fields.VER_COPIED);
	
	@Override
	public Map<ObjectIDResolvedWS, Map<SubsetSelection, WorkspaceObjectData>>
			getObjects(
				final Map<ObjectIDResolvedWS, Set<SubsetSelection>> objs,
				final ByteArrayFileCacheManager dataMan,
				final long usedDataAllocation,
				final boolean exceptIfDeleted,
				final boolean includeDeleted,
				final boolean exceptIfMissing)
			throws WorkspaceCommunicationException, NoSuchObjectException,
			TypedObjectExtractionException, CorruptWorkspaceDBException {
		
		final Map<ObjectIDResolvedWS, ResolvedObjectID> resobjs =
				resolveObjectIDs(objs.keySet(), exceptIfDeleted, includeDeleted, exceptIfMissing);
		final Map<ResolvedObjectID, Map<String, Object>> vers = 
				queryVersions(
						new HashSet<ResolvedObjectID>(resobjs.values()),
						FLDS_VER_GET_OBJECT, !exceptIfMissing);
		if (dataMan != null) {
			checkTotalFileSize(usedDataAllocation, objs, resobjs, vers);
		}
		final Map<ObjectId, MongoProvenance> provs = getProvenance(vers);
		final Map<String, ByteArrayFileCache> chksumToData =
				new HashMap<String, ByteArrayFileCache>();
		final Map<ObjectIDResolvedWS, Map<SubsetSelection, WorkspaceObjectData>> ret =
				new HashMap<ObjectIDResolvedWS, Map<SubsetSelection, WorkspaceObjectData>>();
		for (final ObjectIDResolvedWS o: objs.keySet()) {
			final ResolvedObjectID roi = resobjs.get(o);
			if (!vers.containsKey(roi)) {
				continue; // works if roi is null or vers doesn't have the key
			}
			final MongoProvenance prov = provs.get((ObjectId) vers.get(roi)
					.get(Fields.VER_PROV));
			final String copyref =
					(String) vers.get(roi).get(Fields.VER_COPIED);
			final Reference copied = copyref == null ? null : new Reference(copyref);
			@SuppressWarnings("unchecked")
			final Map<String, List<String>> extIDs =
					(Map<String, List<String>>) vers.get(roi).get(Fields.VER_EXT_IDS);
			@SuppressWarnings("unchecked")
			final List<String> refs =
					(List<String>) vers.get(roi).get(Fields.VER_REF);
			final ObjectInformation info = ObjectInfoUtils.generateObjectInfo(
					roi, vers.get(roi));
			if (dataMan == null) {
				ret.put(o, new HashMap<SubsetSelection, WorkspaceObjectData>());
				ret.get(o).put(SubsetSelection.EMPTY, new WorkspaceObjectData(
						info, prov, refs, copied, extIDs));
			} else {
				try {
					if (objs.get(o).isEmpty()) {
						// this can never happen based on the Workspace code
						throw new IllegalStateException(
								"At least one SubsetSelection must be provided");
					} else {
						for (final SubsetSelection op: objs.get(o)) {
							buildReturnedObjectData(
									o, op, prov, refs, copied, extIDs, info,
									chksumToData, dataMan, ret);
						}
					}
				} catch (TypedObjectExtractionException |
						WorkspaceCommunicationException |
						CorruptWorkspaceDBException |
						RuntimeException |
						Error e) {
					cleanUpTempObjectFiles(chksumToData, ret);
					throw e;
				}
			}
		}
		return ret;
	}

	private void checkTotalFileSize(
			final long usedDataAllocation,
			final Map<ObjectIDResolvedWS, Set<SubsetSelection>> paths,
			final Map<ObjectIDResolvedWS, ResolvedObjectID> resobjs,
			final Map<ResolvedObjectID, Map<String, Object>> vers) {
		//could take into account that identical md5s won't incur a real
		//size penalty, but meh
		long size = 0;
		for (final ObjectIDResolvedWS o: paths.keySet()) {
			// works if resobjs.get(o) is null or vers doesn't contain
			if (vers.containsKey(resobjs.get(o))) {
				final Set<SubsetSelection> ops = paths.get(o);
				final long mult = ops.size() < 1 ? 1 : ops.size();
				size += mult * (Long) vers.get(resobjs.get(o))
						.get(Fields.VER_SIZE);
			}
		}
		if (size + usedDataAllocation > rescfg.getMaxReturnedDataSize()) {
			throw new IllegalArgumentException(String.format(
					"Too much data requested from the workspace at once; " +
					"data requested including potential subsets is %sB " + 
					"which exceeds maximum of %s.", size + usedDataAllocation,
					rescfg.getMaxReturnedDataSize()));
		}
	}

	private void cleanUpTempObjectFiles(
			final Map<String, ByteArrayFileCache> chksumToData,
			final Map<ObjectIDResolvedWS, Map<SubsetSelection,
				WorkspaceObjectData>> ret) {
		for (final ByteArrayFileCache f: chksumToData.values()) {
			try {
				f.destroy();
			} catch (RuntimeException | Error e) {
				//continue
			}
		}
		for (final Map<SubsetSelection, WorkspaceObjectData> m:
			ret.values()) {
			for (final WorkspaceObjectData wod: m.values()) {
				try {
					wod.destroy();
				} catch (RuntimeException | Error e) {
					//continue
				}
			}
		}
	}
	

	//yuck. Think more about the interface here
	private void buildReturnedObjectData(
			final ObjectIDResolvedWS o,
			final SubsetSelection op,
			final MongoProvenance prov,
			final List<String> refs,
			final Reference copied,
			final Map<String, List<String>> extIDs,
			final ObjectInformation info,
			final Map<String, ByteArrayFileCache> chksumToData,
			final ByteArrayFileCacheManager bafcMan,
			final Map<ObjectIDResolvedWS,
					Map<SubsetSelection, WorkspaceObjectData>> ret)
			throws TypedObjectExtractionException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		if (!ret.containsKey(o)) {
			ret.put(o, new HashMap<SubsetSelection, WorkspaceObjectData>());
		}
		if (chksumToData.containsKey(info.getCheckSum())) {
			/* might be subsetting the same object the same way multiple
			 * times, but probably unlikely. If it becomes a problem
			 * memoize the subset
			 */
			ret.get(o).put(op, new WorkspaceObjectData(getDataSubSet(
					chksumToData.get(info.getCheckSum()), op, bafcMan),
					info, prov, refs, copied, extIDs));
		} else {
			final ByteArrayFileCache data;
			try {
				data = blob.getBlob(new MD5(info.getCheckSum()), bafcMan);
			} catch (FileCacheIOException e) {
				throw new WorkspaceCommunicationException(
						e.getLocalizedMessage(), e);
			} catch (FileCacheLimitExceededException e) {
				throw new IllegalArgumentException( //shouldn't happen if size was checked correctly beforehand
						"Too much data requested from the workspace at once; " +
						"data requested including subsets exceeds maximum of "
						+ bafcMan.getMaxSizeOnDisk());
			} catch (BlobStoreCommunicationException e) {
				throw new WorkspaceCommunicationException(
						e.getLocalizedMessage(), e);
			} catch (BlobStoreAuthorizationException e) {
				throw new WorkspaceCommunicationException(
						"Authorization error communicating with the backend storage system",
						e);
			} catch (NoSuchBlobException e) {
				throw new CorruptWorkspaceDBException(String.format(
						"No data present for valid object %s.%s.%s",
						info.getWorkspaceId(), info.getObjectId(),
						info.getVersion()), e);
			}
			chksumToData.put(info.getCheckSum(), data);
			ret.get(o).put(op, new WorkspaceObjectData(
					getDataSubSet(data, op, bafcMan),
					info, prov, refs, copied, extIDs));
		}
	}
	
	private ByteArrayFileCache getDataSubSet(final ByteArrayFileCache data,
			final SubsetSelection paths, final ByteArrayFileCacheManager bafcMan)
			throws TypedObjectExtractionException,
			WorkspaceCommunicationException {
		if (paths.isEmpty()) {
			return data;
		}
		try {
			return bafcMan.getSubdataExtraction(data, paths);
		} catch (FileCacheIOException e) {
			throw new WorkspaceCommunicationException(
					e.getLocalizedMessage(), e);
		} catch (FileCacheLimitExceededException e) {
			throw new IllegalArgumentException( //shouldn't happen if size was checked correctly beforehand
					"Too much data requested from the workspace at once; " +
					"data requested including subsets exceeds maximum of "
					+ bafcMan.getMaxSizeOnDisk());
		}
	}
	
	private static final Set<String> FLDS_GET_REF_FROM_OBJ = newHashSet(
			Fields.VER_PROVREF, Fields.VER_REF, Fields.VER_VER);
	
	@Override
	public Map<ObjectIDResolvedWS, ObjectReferenceSet> getObjectOutgoingReferences(
			final Set<ObjectIDResolvedWS> objs,
			final boolean exceptIfDeleted,
			final boolean includeDeleted,
			final boolean exceptIfMissing)
			throws NoSuchObjectException, WorkspaceCommunicationException {
		final Map<ObjectIDResolvedWS, ObjectReferenceSet> ret =
				new HashMap<ObjectIDResolvedWS, ObjectReferenceSet>();
		
		final Map<ObjectIDResolvedWS, ResolvedObjectID> resobjs = 
				resolveObjectIDs(objs, exceptIfDeleted, includeDeleted, exceptIfMissing);
		final Map<ResolvedObjectID, Map<String, Object>> refs =
				queryVersions(
						new HashSet<ResolvedObjectID>(resobjs.values()),
						FLDS_GET_REF_FROM_OBJ, !exceptIfMissing);
		
		for (final ObjectIDResolvedWS oi: objs) {
			if (!resobjs.containsKey(oi)) {
				continue;
			}
			final ResolvedObjectID res = resobjs.get(oi);
			final Map<String, Object> m = refs.get(res);
			final int ver = (Integer) m.get(Fields.VER_VER);
			final Reference ref = new Reference(
					res.getWorkspaceIdentifier().getID(), res.getId(), ver);
			@SuppressWarnings("unchecked")
			final List<String> rf = (List<String>) m.get(Fields.VER_REF);
			@SuppressWarnings("unchecked")
			final List<String> prf = (List<String>) m.get(Fields.VER_PROVREF);
			final Set<Reference> r = new HashSet<Reference>();
			for (String s: rf) {
				r.add(new Reference(s));
			}
			for (String s: prf) {
				r.add(new Reference(s));
			}
			ret.put(oi, new ObjectReferenceSet(ref, r, false));
		}
		return ret;
	}
	
	private static final Set<String> FLDS_GET_REF_TO_OBJ = newHashSet(
			Fields.VER_WS_ID, Fields.VER_ID, Fields.VER_VER, Fields.VER_PROVREF, Fields.VER_REF);
	
	@Override
	public Map<ObjectIDResolvedWS, Reference> getObjectReference(
			final Set<ObjectIDResolvedWS> objs)
			throws WorkspaceCommunicationException {
		final Map<ObjectIDResolvedWS, Reference> ret = new HashMap<>();
		if (objs.isEmpty()) {
			return ret;
		}
		final Map<ObjectIDResolvedWS, ResolvedObjectID> resobjs;
		final Map<ResolvedObjectID, Boolean> exists;
		try {
			resobjs = resolveObjectIDs(objs, false, true, false);
			// the only way exists can be false if if there's DB inconsistency
			//TODO GC when schema is changed to ensure versions always exist, remove
			exists = verifyVersions(new HashSet<>(resobjs.values()), false);
		} catch (NoSuchObjectException e) {
			throw new RuntimeException("Threw exception when explicitly told not to", e);
		}
		for (final Entry<ObjectIDResolvedWS, ResolvedObjectID> o: resobjs.entrySet()) {
			if (exists.get(o.getValue())) {
				ret.put(o.getKey(), o.getValue().getReference());
			}
		}
		return ret;
	}

	@Override
	public Map<Reference, ObjectReferenceSet> getObjectIncomingReferences(
			final Set<Reference> refs)
			throws WorkspaceCommunicationException {
		if (refs.isEmpty()) {
			return new HashMap<>();
		}
		//TODO MEM add limit for number of refs returned (probably 50K, but make a method param) & throw exception if more than that returned
		final List<String> refStrings = new LinkedList<>();
		for (final Reference r: refs) {
			refStrings.add(r.getId());
		}
		
		final DBObject q = new BasicDBObject("$or", Arrays.asList(
				new BasicDBObject(Fields.VER_REF, new BasicDBObject("$in", refStrings)),
				new BasicDBObject(Fields.VER_PROVREF, new BasicDBObject("$in", refStrings))));
		final List<Map<String, Object>> vers = query.queryCollection(
				COL_WORKSPACE_VERS, q, FLDS_GET_REF_TO_OBJ);
		return buildReferenceToReferencesMap(refs, vers);
	}
	
	private Map<Reference, ObjectReferenceSet> buildReferenceToReferencesMap(
			final Set<Reference> refs,
			final List<Map<String, Object>> vers) {
		final Map<Reference, Set<Reference>> refToRefs = new HashMap<Reference, Set<Reference>>();
		for (final Reference r: refs) {
			refToRefs.put(r, new HashSet<Reference>());
		}
		for (final Map<String, Object> v: vers) {
			final long ws = (Long) v.get(Fields.VER_WS_ID);
			final long obj = (Long) v.get(Fields.VER_ID);
			final int ver = (Integer) v.get(Fields.VER_VER);
			final Reference thisref = new Reference(ws, obj, ver);
			
			final Set<String> allrefs = new HashSet<String>();
			@SuppressWarnings("unchecked")
			final List<String> increfs = (List<String>) v.get(Fields.VER_REF);
			allrefs.addAll(increfs);
			increfs.clear();
			@SuppressWarnings("unchecked")
			final List<String> provrefs = (List<String>) v.get(Fields.VER_PROVREF);
			allrefs.addAll(provrefs);
			provrefs.clear();
			for (final String ref: allrefs) {
				final Reference r = new Reference(ref);
				if (refToRefs.containsKey(r)) {
					refToRefs.get(r).add(thisref);
				}
			}
			
		}
		final Map<Reference, ObjectReferenceSet> ret = new HashMap<>();
		for (final Reference r: refToRefs.keySet()) {
			ret.put(r, new ObjectReferenceSet(r, refToRefs.get(r), true));
		}
		return ret;
	}

	private static final Set<String> FLDS_GETREFOBJ = newHashSet(
			Fields.VER_WS_ID, Fields.VER_ID, Fields.VER_VER,
			Fields.VER_VER, Fields.VER_TYPE, Fields.VER_META,
			Fields.VER_SAVEDATE, Fields.VER_SAVEDBY,
			Fields.VER_CHKSUM, Fields.VER_SIZE,
			Fields.VER_PROVREF, Fields.VER_REF);
	
	@Override
	public Map<ObjectIDResolvedWS, Set<ObjectInformation>>
			getReferencingObjects(final PermissionSet perms,
					final Set<ObjectIDResolvedWS> objs)
		throws NoSuchObjectException, WorkspaceCommunicationException {
		final List<Long> wsids = new LinkedList<Long>();
		for (final ResolvedWorkspaceID ws: perms.getWorkspaces()) {
			wsids.add(ws.getID());
		}
		final Map<ObjectIDResolvedWS, ResolvedObjectID> resobjs =
				resolveObjectIDs(objs);
		verifyVersions(new HashSet<ResolvedObjectID>(resobjs.values()), true);
		final Map<String, Set<ObjectIDResolvedWS>> ref2id =
				new HashMap<String, Set<ObjectIDResolvedWS>>();
		for (final ObjectIDResolvedWS oi: objs) {
			final ResolvedObjectID r = resobjs.get(oi);
			final String ref = r.getReference().toString();
			if (!ref2id.containsKey(ref)) {
				ref2id.put(ref, new HashSet<ObjectIDResolvedWS>());
			}
			ref2id.get(ref).add(oi);
		}
		final DBObject q = new BasicDBObject(Fields.VER_WS_ID, new BasicDBObject("$in", wsids));
		q.put("$or", Arrays.asList(new BasicDBObject(Fields.VER_REF,
						new BasicDBObject("$in", ref2id.keySet())),
				new BasicDBObject(Fields.VER_PROVREF, new BasicDBObject("$in", ref2id.keySet()))));
		final List<Map<String, Object>> vers = query.queryCollection(
				COL_WORKSPACE_VERS, q, FLDS_GETREFOBJ);
		final Map<Map<String, Object>, ObjectInformation> voi = objutils.generateObjectInfo(
				perms, vers, true, false, false, true, false);
		final Map<ObjectIDResolvedWS, Set<ObjectInformation>> ret = new HashMap<>();
		for (final ObjectIDResolvedWS o: objs) {
			ret.put(o, new HashSet<ObjectInformation>());
		}
		for (final Map<String, Object> ver: voi.keySet()) {
			@SuppressWarnings("unchecked")
			final List<String> refs = (List<String>) ver.get(Fields.VER_REF);
			@SuppressWarnings("unchecked")
			final List<String> provrefs = (List<String>) ver.get(Fields.VER_PROVREF);
			final Set<String> allrefs = new HashSet<String>();
			allrefs.addAll(refs);
			allrefs.addAll(provrefs);
			for (final String ref: allrefs) {
				if (ref2id.containsKey(ref)) {
					for (final ObjectIDResolvedWS oi: ref2id.get(ref)) {
						ret.get(oi).add(voi.get(ver));
					}
				}
			}
		}
		return ret;
	}
	
	private static final Set<String> FLDS_REF_CNT = newHashSet(
			Fields.OBJ_ID, Fields.OBJ_NAME, Fields.OBJ_DEL, Fields.OBJ_VCNT, Fields.OBJ_REFCOUNTS);

	/** @deprecated */
	@Override
	public Map<ObjectIDResolvedWS, Integer> getReferencingObjectCounts(
			final Set<ObjectIDResolvedWS> objects)
			throws WorkspaceCommunicationException, NoSuchObjectException {
		//TODO GC test w/ garbage collection
		final Map<ObjectIDResolvedWS, Map<String, Object>> objdata =
				queryObjects(objects, FLDS_REF_CNT, true, false, true);
		final Map<ObjectIDResolvedWS, Integer> ret =
				new HashMap<ObjectIDResolvedWS, Integer>();
		for (final ObjectIDResolvedWS o: objects) {
			//this is another place where extremely rare failures could cause
			//problems
			final int ver;
			final int latestver = (Integer) objdata.get(o).get(Fields.OBJ_VCNT);
			if (o.getVersion() == null) {
				ver = latestver;
			} else {
				ver = o.getVersion();
				if (ver > latestver) {
					final Map<String, Object> obj = objdata.get(o);
					final String name = (String) obj.get(Fields.OBJ_NAME);
					final Long id = (Long) obj.get(Fields.OBJ_ID);
					
					throw new NoSuchObjectException(String.format(
							"No object with id %s (name %s) and version %s exists "
							+ "in workspace %s", id, name, ver,
							o.getWorkspaceIdentifier().getID()), o);
				}
			}
			@SuppressWarnings("unchecked")
			final List<Integer> refs = (List<Integer>) objdata.get(o).get(
					Fields.OBJ_REFCOUNTS);
			//TODO GC when GC enabled handle the case where the version is deleted
			ret.put(o, refs.get(ver - 1));
		}
		return ret;
	}
	
	private Map<ObjectId, MongoProvenance> getProvenance(
			final Map<ResolvedObjectID, Map<String, Object>> vers)
			throws WorkspaceCommunicationException {
		final Map<ObjectId, Map<String, Object>> provIDs =
				new HashMap<ObjectId, Map<String,Object>>();
		for (final ResolvedObjectID id: vers.keySet()) {
			provIDs.put((ObjectId) vers.get(id).get(Fields.VER_PROV),
					vers.get(id));
		}
		final Map<ObjectId, MongoProvenance> ret =
				new HashMap<ObjectId, MongoProvenance>();
		try {
			final Iterable<MongoProvenance> provs =
					wsjongo.getCollection(COL_PROVENANCE)
					.find("{_id: {$in: #}}", provIDs.keySet())
					.as(MongoProvenance.class);
			for (MongoProvenance p: provs) {
				@SuppressWarnings("unchecked")
				final List<String> resolvedRefs = (List<String>) provIDs
						.get(p.getMongoId()).get(Fields.VER_PROVREF);
				ret.put(p.getMongoId(), p);
				p.resolveReferences(resolvedRefs); //this is a gross hack. I'm rather proud of it actually
			}
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		return ret;
	}
	
	private static final Set<String> FLDS_VER_TYPE = newHashSet(
			Fields.VER_TYPE, Fields.VER_VER);
	
	public Map<ObjectIDResolvedWS, TypeAndReference> getObjectType(
			final Set<ObjectIDResolvedWS> objectIDs,
			final boolean ignoreErrors) throws
			NoSuchObjectException, WorkspaceCommunicationException {
		//this method is a pattern - generalize somehow?
		final Map<ObjectIDResolvedWS, ResolvedObjectID> oids =
				resolveObjectIDs(objectIDs, !ignoreErrors, ignoreErrors, !ignoreErrors);
		//instead of calling verifyVersions() just query the version here
		final Map<ResolvedObjectID, Map<String, Object>> vers = 
				queryVersions(new HashSet<ResolvedObjectID>(oids.values()),
						FLDS_VER_TYPE, ignoreErrors);
		final Map<ObjectIDResolvedWS, TypeAndReference> ret = new HashMap<>();
		for (final ObjectIDResolvedWS o: objectIDs) {
			final ResolvedObjectID roi = oids.get(o);
			final Map<String, Object> v = vers.get(roi);
			if (v != null) {
				ret.put(o, new TypeAndReference(AbsoluteTypeDefId.fromAbsoluteTypeString(
								(String) v.get(Fields.VER_TYPE)),
						new Reference(roi.getWorkspaceIdentifier().getID(), roi.getId(),
								(Integer) v.get(Fields.VER_VER))));
			}
		}
		return ret;
	}
	
	private static final Set<String> FLDS_NAME_PREFIX = newHashSet(
			Fields.OBJ_NAME, Fields.OBJ_WS_ID);

	@Override
	public Map<ResolvedWorkspaceID, List<String>> getNamesByPrefix(
			final Set<ResolvedWorkspaceID> rwsis,
			final String prefix,
			final boolean includeHidden,
			final int limit)
			throws WorkspaceCommunicationException {
		final Map<ResolvedWorkspaceID, List<String>> ret =
				new HashMap<ResolvedWorkspaceID, List<String>>();
		if (rwsis.isEmpty()) {
			return ret;
		}
		final Map<Long, ResolvedWorkspaceID> wsIDtoWS =
				new HashMap<Long, ResolvedWorkspaceID>();
		for (final ResolvedWorkspaceID rwsid: rwsis) {
			wsIDtoWS.put(rwsid.getID(), rwsid);
		}
		final DBObject q = new BasicDBObject(Fields.OBJ_DEL, false);
		q.put(Fields.OBJ_WS_ID, new BasicDBObject("$in", wsIDtoWS.keySet()));
		if (!prefix.isEmpty()) {
			// escape regex chars
			q.put(Fields.OBJ_NAME,
					new BasicDBObject("$regex", "^" + Pattern.quote(prefix)));
		}
		if (!includeHidden) {
			q.put(Fields.OBJ_HIDE, false);
		}
		
		final List<Map<String, Object>> names = query.queryCollection(
				COL_WORKSPACE_OBJS, q, FLDS_NAME_PREFIX, limit);
		for (final Map<String, Object> o: names) {
			final Long wsid = (Long) o.get(Fields.OBJ_WS_ID);
			final String name = (String) o.get(Fields.OBJ_NAME);
			final ResolvedWorkspaceID rwsid = wsIDtoWS.get(wsid);
			if (!ret.containsKey(rwsid)) {
				ret.put(rwsid, new LinkedList<String>());
			}
			ret.get(rwsid).add(name);
		}
		return ret;
	}
	
	@Override
	public List<ObjectInformation> getObjectInformation(
			final GetObjectInformationParameters params)
			throws WorkspaceCommunicationException {
		return objutils.filter(params);
	}

	private static final Set<String> FLDS_VER_OBJ_HIST = newHashSet(
			Fields.VER_WS_ID, Fields.VER_ID, Fields.VER_VER,
			Fields.VER_TYPE, Fields.VER_CHKSUM, Fields.VER_SIZE,
			Fields.VER_META, Fields.VER_SAVEDATE, Fields.VER_SAVEDBY);
	
	@Override
	public List<ObjectInformation> getObjectHistory(
			final ObjectIDResolvedWS oi)
		throws NoSuchObjectException, WorkspaceCommunicationException {
		final ResolvedObjectID roi = resolveObjectIDs(
				new HashSet<ObjectIDResolvedWS>(Arrays.asList(oi))).get(oi);
		final ResolvedObjectIDNoVer o =
				new ResolvedObjectIDNoVer(roi);
		final List<Map<String, Object>> versions = queryAllVersions(
				new HashSet<ResolvedObjectIDNoVer>(Arrays.asList(o)),
				FLDS_VER_OBJ_HIST).get(o);
		final LinkedList<ObjectInformation> ret =
				new LinkedList<ObjectInformation>();
		for (final Map<String, Object> v: versions) {
			ret.add(ObjectInfoUtils.generateObjectInfo(roi, v));
		}
		return ret;
	}
	
	private static final Set<String> FLDS_VER_META = newHashSet(
			Fields.VER_VER, Fields.VER_TYPE,
			Fields.VER_SAVEDATE, Fields.VER_SAVEDBY,
			Fields.VER_CHKSUM, Fields.VER_SIZE);
	
	@Override
	public Map<ObjectIDResolvedWS, ObjectInformation> getObjectInformation(
			final Set<ObjectIDResolvedWS> objectIDs,
			final boolean includeMetadata,
			final boolean exceptIfDeleted,
			final boolean includeDeleted,
			final boolean exceptIfMissing)
			throws NoSuchObjectException, WorkspaceCommunicationException {
		final Map<ObjectIDResolvedWS, ResolvedObjectID> oids =
				resolveObjectIDs(objectIDs, exceptIfDeleted, includeDeleted, exceptIfMissing);
		final Set<String> fields;
		if (includeMetadata) {
			fields = new HashSet<String>(FLDS_VER_META);
			fields.add(Fields.VER_META);
		} else {
			fields = FLDS_VER_META;
		}
		final Map<ResolvedObjectID, Map<String, Object>> vers = 
				queryVersions(
						new HashSet<ResolvedObjectID>(oids.values()),
						fields, !exceptIfMissing);
		final Map<ObjectIDResolvedWS, ObjectInformation> ret =
				new HashMap<ObjectIDResolvedWS, ObjectInformation>();
		for (ObjectIDResolvedWS o: objectIDs) {
			final ResolvedObjectID roi = oids.get(o);
			if (vers.containsKey(roi)) {
				ret.put(o, ObjectInfoUtils.generateObjectInfo(
						roi, vers.get(roi)));
			}
		}
		return ret;
	}
	
	private static final Set<String> FLDS_RESOLVE_OBJS =
			newHashSet(Fields.OBJ_ID, Fields.OBJ_NAME, Fields.OBJ_DEL, Fields.OBJ_VCNT);
	
	private Map<ObjectIDResolvedWS, ResolvedObjectID> resolveObjectIDs(
			final Set<ObjectIDResolvedWS> objectIDs)
			throws NoSuchObjectException, WorkspaceCommunicationException {
		return resolveObjectIDs(objectIDs, true, true);
	}
	
	private Map<ObjectIDResolvedWS, ResolvedObjectID> resolveObjectIDs(
			final Set<ObjectIDResolvedWS> objectIDs,
			final boolean exceptIfDeleted, final boolean exceptIfMissing)
			throws NoSuchObjectException, WorkspaceCommunicationException {
		return resolveObjectIDs(objectIDs, exceptIfDeleted, true, exceptIfMissing);
	}
	
	private Map<ObjectIDResolvedWS, ResolvedObjectID> resolveObjectIDs(
			final Set<ObjectIDResolvedWS> objectIDs,
			final boolean exceptIfDeleted,
			final boolean includeDeleted,
			final boolean exceptIfMissing)
			throws NoSuchObjectException, WorkspaceCommunicationException {
		final Map<ObjectIDResolvedWS, Map<String, Object>> ids = 
				queryObjects(objectIDs, FLDS_RESOLVE_OBJS, exceptIfDeleted,
						includeDeleted, exceptIfMissing);
		final Map<ObjectIDResolvedWS, ResolvedObjectID> ret =
				new HashMap<ObjectIDResolvedWS, ResolvedObjectID>();
		for (final ObjectIDResolvedWS o: ids.keySet()) {
			final String name = (String) ids.get(o).get(Fields.OBJ_NAME);
			final long id = (Long) ids.get(o).get(Fields.OBJ_ID);
			final boolean deleted = (Boolean) ids.get(o).get(Fields.OBJ_DEL);
			//TODO BUG this could be wrong if the vercount was incremented without a ver save, should verify and then sort if needed, or better yet, fix the schema so it can't happen
			final int latestVersion = (Integer) ids.get(o).get(Fields.OBJ_VCNT);
			final int version = o.getVersion() == null ? latestVersion: o.getVersion();
			if (version > latestVersion) {
				if (exceptIfMissing) {
					throw new NoSuchObjectException(String.format(
							"No object with id %s (name %s) and version %s exists in " +
							"workspace %s (name %s)",
							id, name, version, 
							o.getWorkspaceIdentifier().getID(),
							o.getWorkspaceIdentifier().getName()), o);
				}
			} else {
				ret.put(o, new ResolvedObjectID(o.getWorkspaceIdentifier(),
						id, version, name, deleted));
			}
		}
		return ret;
	}
	
	
	private Map<ObjectIDResolvedWS, Map<String, Object>> queryObjects(
			final Set<ObjectIDResolvedWS> objectIDs,
			final Set<String> fields,
			final boolean exceptIfDeleted,
			final boolean includeDeleted,
			final boolean exceptIfMissing)
			throws WorkspaceCommunicationException, NoSuchObjectException,
				DeletedObjectException {
		final Map<ObjectIDResolvedWS, ObjectIDResolvedWSNoVer> nover =
				new HashMap<ObjectIDResolvedWS, ObjectIDResolvedWSNoVer>();
		for (final ObjectIDResolvedWS o: objectIDs) {
			nover.put(o, new ObjectIDResolvedWSNoVer(o));
		}
		final Map<ObjectIDResolvedWSNoVer, Map<String, Object>> ids = 
				query.queryObjects(
						new HashSet<ObjectIDResolvedWSNoVer>(nover.values()),
						fields);
		final Map<ObjectIDResolvedWS, Map<String, Object>> ret =
				new HashMap<ObjectIDResolvedWS, Map<String,Object>>();
		for (final ObjectIDResolvedWS oid: nover.keySet()) {
			//this could happen multiple times per object *shrug*
			//if becomes a problem, hash nover -> ver and just loop through
			//the novers
			final ObjectIDResolvedWSNoVer o = nover.get(oid);
			if (!ids.containsKey(o)) {
				if (exceptIfMissing) {
					final String err = oid.getId() == null ? "name" : "id";
					throw new NoSuchObjectException(String.format(
							"No object with %s %s exists in workspace %s (name %s)",
							err, oid.getIdentifierString(), oid.getWorkspaceIdentifier().getID(),
							oid.getWorkspaceIdentifier().getName()), oid);
				} else {
					continue;
				}
			}
			final String name = (String) ids.get(o).get(Fields.OBJ_NAME);
			final long id = (Long) ids.get(o).get(Fields.OBJ_ID);
			final boolean deleted = (Boolean) ids.get(o).get(Fields.OBJ_DEL);
			if (exceptIfDeleted && deleted) {
				throw new DeletedObjectException(String.format(
						"Object %s (name %s) in workspace %s (name %s) has been deleted",
						id, name, oid.getWorkspaceIdentifier().getID(),
						oid.getWorkspaceIdentifier().getName()), oid);
			}
			if (!deleted || includeDeleted) {
				ret.put(oid, ids.get(o));
			}
		}
		return ret;
	}
	
	//In rare race conditions an object may exist with a ver count of 1 but
	//no versions. Really need to move this code to a backend DB with
	//transactions if we want autoincrementing counters.
	private Map<ResolvedObjectID, Map<String, Object>> queryVersions(
			final Set<ResolvedObjectID> objectIds,
			final Set<String> fields,
			boolean ignoreMissing)
			throws WorkspaceCommunicationException, NoSuchObjectException {
		final Map<ResolvedObjectID, Map<String, Object>> vers = 
				query.queryVersions(objectIds, fields);
		if (ignoreMissing) {
			return vers;
		}
		// the ID was resolved, but could have been deleted since then,
		// or if the database failed between an autoincrement and a version
		// save the version might not exist
		for (ResolvedObjectID roi: objectIds) {
			if (!vers.containsKey(roi)) {
				ObjectIDResolvedWS oid = new ObjectIDResolvedWS(
						roi.getWorkspaceIdentifier(), roi.getId());
				throw new NoSuchObjectException(String.format(
						"No object with id %s (name %s) and version %s " +
						"exists in workspace %s (name %s)",
						roi.getId(), roi.getName(), roi.getVersion(), 
						roi.getWorkspaceIdentifier().getID(),
						roi.getWorkspaceIdentifier().getName()), oid);
			}
		}
		return vers;
	}
	
	//In rare race conditions an object may exist with a ver count of 1 but
	//no versions. Really need to move this code to a backend DB with
	//transactions if we want autoincrementing counters.
	private Map<ResolvedObjectIDNoVer, List<Map<String, Object>>>
		queryAllVersions(
			final HashSet<ResolvedObjectIDNoVer> objectIDs,
			final Set<String> fields)
			throws WorkspaceCommunicationException, NoSuchObjectException {
		
		final Map<ResolvedObjectIDNoVer, List<Map<String, Object>>> ret =
				query.queryAllVersions(objectIDs, fields);
		for (final Entry<ResolvedObjectIDNoVer,
				List<Map<String, Object>>> s: ret.entrySet()) {
			if (s.getValue().isEmpty()) {
				final ResolvedObjectIDNoVer oid = s.getKey();
				final ObjectIDResolvedWS oidrws = new ObjectIDResolvedWS(
						oid.getWorkspaceIdentifier(), oid.getName());
				throw new NoSuchObjectException(String.format(
						"No object with name %s exists in workspace %s",
						oid.getName(),
						oid.getWorkspaceIdentifier().getID()), oidrws);
			}
		}
		return ret;
	}

	/* it's possible that a version was incremented on the object but the version data
	 * was not yet saved. It's also possible that the db or server went down after the version
	 * was incremented, leaving the db in an inconsistent state. This function verifies that
	 * the versions of the provided objects exist.
	 */
	private Map<ResolvedObjectID, Boolean> verifyVersions(
			final Set<ResolvedObjectID> objs,
			final boolean exceptIfMissing)
			throws WorkspaceCommunicationException, NoSuchObjectException {
		final Map<ResolvedObjectID, Map<String, Object>> vers =
				queryVersions(objs, new HashSet<String>(), !exceptIfMissing); //don't actually need the data
		final Map<ResolvedObjectID, Boolean> ret =
				new HashMap<ResolvedObjectID, Boolean>();
		for (final ResolvedObjectID o: objs) {
			ret.put(o, vers.containsKey(o));
		}
		return ret;
	}
	
	@Override
	public Map<Reference, Boolean> getObjectExistsRef(final Set<Reference> refs)
			throws WorkspaceCommunicationException {
		final Map<Reference, Boolean> ret = new HashMap<>();
		if (refs.isEmpty()) {
			return ret;
		}
		final Set<ObjectIDResolvedWSNoVer> objs = new HashSet<>();
		for (final Reference r: refs) {
			// this is a bit of a hack
			objs.add(new ObjectIDResolvedWSNoVer(new ResolvedWorkspaceID(
					r.getWorkspaceID(), "a", false, false), r.getObjectID()));
		}
		final Map<ObjectIDResolvedWSNoVer, Map<String, Object>> res =
				query.queryObjects(objs, newHashSet(Fields.OBJ_DEL));
		for (final Reference r: refs) {
			ret.put(r, false);
			final ObjectIDResolvedWSNoVer o = new ObjectIDResolvedWSNoVer(new ResolvedWorkspaceID(
					r.getWorkspaceID(), "a", false, false), r.getObjectID());
			if (res.containsKey(o)) { // only false if the ref ws or obj id is bad
				final boolean deleted = (boolean) res.get(o).get(Fields.OBJ_DEL);
				if (!deleted) {
					ret.put(r, true);
				}
			}
		}
		return ret;
	}

	@Override
	public Map<ObjectIDResolvedWS, Boolean> getObjectExists(
			final Set<ObjectIDResolvedWS> objectIDs)
			throws WorkspaceCommunicationException {
		final Map<ResolvedObjectID, Boolean> exists;
		final Map<ObjectIDResolvedWS, ResolvedObjectID> objs;
		try {
			objs = resolveObjectIDs(objectIDs, false, false);
			final Iterator<Entry<ObjectIDResolvedWS, ResolvedObjectID>> i =
					objs.entrySet().iterator();
			while (i.hasNext()) {
				if (i.next().getValue().isDeleted()) {
					i.remove();
				}
			}
			exists = verifyVersions(new HashSet<ResolvedObjectID>(
					objs.values()), false);
		} catch (NoSuchObjectException e) {
			throw new RuntimeException(
					"Explicitly told not to throw exception but did anyway",
					e);
		}
		final Map<ObjectIDResolvedWS, Boolean> ret =
				new HashMap<ObjectIDResolvedWS, Boolean>();
		for (final ObjectIDResolvedWS o: objectIDs) {
			if (!objs.containsKey(o)) {
				ret.put(o, false);
			} else {
				ret.put(o, exists.get(objs.get(o)));
			}
			
		}
		return ret;
	}

	@Override
	public void setObjectsHidden(final Set<ObjectIDResolvedWS> objectIDs,
			final boolean hide)
			throws NoSuchObjectException, WorkspaceCommunicationException {
		//TODO CODE generalize, nearly identical to delete objects
		final Map<ObjectIDResolvedWS, ResolvedObjectID> ids =
				resolveObjectIDs(objectIDs);
		final Map<ResolvedWorkspaceID, List<Long>> toModify =
				new HashMap<ResolvedWorkspaceID, List<Long>>();
		for (final ObjectIDResolvedWS o: objectIDs) {
			final ResolvedWorkspaceID ws = o.getWorkspaceIdentifier();
			if (!toModify.containsKey(ws)) {
				toModify.put(ws, new ArrayList<Long>());
			}
			toModify.get(ws).add(ids.get(o).getId());
		}
		//Do this by workspace since per mongo docs nested $ors are crappy
		for (final ResolvedWorkspaceID ws: toModify.keySet()) {
			setObjectsHidden(ws, toModify.get(ws), hide);
		}
	}
	
	private static final String M_HIDOBJ_WTH = String.format(
			"{$set: {%s: #}}", Fields.OBJ_HIDE);
	private static final String M_HIDOBJ_QRY = String.format(
			"{%s: #, %s: {$in: #}}", Fields.OBJ_WS_ID, Fields.OBJ_ID);
	
	private void setObjectsHidden(final ResolvedWorkspaceID ws,
			final List<Long> objectIDs, final boolean hide)
			throws WorkspaceCommunicationException {
		//TODO CODE general set field method?
		if (objectIDs.isEmpty()) {
			throw new IllegalArgumentException("Object IDs cannot be empty");
		}
		try {
			wsjongo.getCollection(COL_WORKSPACE_OBJS)
					.update(M_HIDOBJ_QRY, ws.getID(), objectIDs).multi()
					.with(M_HIDOBJ_WTH, hide);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
	}
	
	@Override
	public Map<ResolvedObjectIDNoVer, Instant> setObjectsDeleted(
			final Set<ObjectIDResolvedWS> objectIDs,
			final boolean delete)
			throws NoSuchObjectException, WorkspaceCommunicationException {
		final Map<ObjectIDResolvedWS, ResolvedObjectID> ids =
				resolveObjectIDs(objectIDs, delete, true);
		final Map<ResolvedWorkspaceID, List<Long>> toModify =
				new HashMap<ResolvedWorkspaceID, List<Long>>();
		for (final ObjectIDResolvedWS o: objectIDs) {
			final ResolvedWorkspaceID ws = o.getWorkspaceIdentifier();
			final ResolvedObjectID obj = ids.get(o);
			if (!toModify.containsKey(ws)) {
				toModify.put(ws, new ArrayList<Long>());
			}
			toModify.get(ws).add(obj.getId());
		}
		//Do this by workspace since per mongo docs nested $ors are crappy
		final Map<ResolvedWorkspaceID, Instant> modtimes = new HashMap<>();
		for (final ResolvedWorkspaceID ws: toModify.keySet()) {
			modtimes.put(ws, setObjectsDeleted(ws, toModify.get(ws), delete));
			updateWorkspaceModifiedDate(ws);
		}
		final Map<ResolvedObjectIDNoVer, Instant> ret = new HashMap<>();
		for (final ObjectIDResolvedWS o: objectIDs) {
			final ResolvedWorkspaceID ws = o.getWorkspaceIdentifier();
			final ResolvedObjectID obj = ids.get(o);
			ret.put(new ResolvedObjectIDNoVer(ws, obj.getId(), obj.getName(), delete),
					modtimes.get(ws));
		}
		return ret;
	}
	
	private static final String M_DELOBJ_WTH = String.format(
			"{$set: {%s: #, %s: #}}", Fields.OBJ_DEL, Fields.OBJ_MODDATE);
	
	private Instant setObjectsDeleted(
			final ResolvedWorkspaceID ws,
			final List<Long> objectIDs,
			final boolean delete)
			throws WorkspaceCommunicationException {
		final String query;
		if (objectIDs.isEmpty()) {
			query = String.format(
					"{%s: %s, %s: %s}", Fields.OBJ_WS_ID, ws.getID(), Fields.OBJ_DEL, !delete);
		} else {
			query = String.format(
					"{%s: %s, %s: {$in: [%s]}, %s: %s}",
					Fields.OBJ_WS_ID, ws.getID(), Fields.OBJ_ID,
					StringUtils.join(objectIDs, ", "), Fields.OBJ_DEL, !delete);
		}
		final Instant time;
		try {
			time = Instant.now();
			wsjongo.getCollection(COL_WORKSPACE_OBJS).update(query).multi()
					.with(M_DELOBJ_WTH, delete, Date.from(time));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		return time;
	}
	
	private static final String M_DELWS_UPD = String.format("{%s: #}",
						Fields.WS_ID);
	private static final String M_DELWS_WTH = String.format(
			"{$set: {%s: #, %s: #}}", Fields.WS_DEL, Fields.WS_MODDATE);
	
	public Instant setWorkspaceDeleted(final ResolvedWorkspaceID rwsi,
			final boolean delete) throws WorkspaceCommunicationException {
		//there's a possibility of a race condition here if a workspace is
		//deleted and undeleted or vice versa in a very short amount of time,
		//but that seems so unlikely it's not worth the code
		
		if (delete) {
			// delete objects first so that we can't have undeleted object in a deleted workspace 
			setObjectsDeleted(rwsi, new ArrayList<Long>(), delete);
		}
		final Instant now = Instant.now();
		try {
			wsjongo.getCollection(COL_WORKSPACES).update(M_DELWS_UPD, rwsi.getID())
					.with(M_DELWS_WTH, delete, Date.from(now));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		if (!delete) {
			//undelete object last so we yadda yadda
			setObjectsDeleted(rwsi, new ArrayList<Long>(), delete);
		}
		return now;
	}
	
	@Override
	public Set<WorkspaceUser> getAllWorkspaceOwners()
			throws WorkspaceCommunicationException {
		final Set<WorkspaceUser> ret = new HashSet<WorkspaceUser>();
		final DBObject q = new BasicDBObject(Fields.WS_CLONING,
				new BasicDBObject("$exists", false));
		try {
			@SuppressWarnings("unchecked")
			final List<String> users = wsmongo.getCollection(COL_WORKSPACES)
				.distinct(Fields.WS_OWNER, q);
			for (final String u: users) {
				ret.add(new WorkspaceUser(u));
			}
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		return ret;
	}

	private static final String M_ADMIN_QRY = String.format(
			"{%s: #}", Fields.ADMIN_NAME);
	
	@Override
	public boolean isAdmin(WorkspaceUser putativeAdmin)
			throws WorkspaceCommunicationException {
		try {
			return wsjongo.getCollection(COL_ADMINS).count(M_ADMIN_QRY,
					putativeAdmin.getUser()) > 0;
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
	}
	

	@Override
	public Set<WorkspaceUser> getAdmins()
			throws WorkspaceCommunicationException {
		final Set<WorkspaceUser> ret = new HashSet<WorkspaceUser>();
		final DBCursor cur;
		try {
			cur = wsmongo.getCollection(COL_ADMINS).find();
			for (final DBObject dbo: cur) {
				ret.add(new WorkspaceUser((String) dbo.get(Fields.ADMIN_NAME)));
			}
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
		return ret;
	}

	@Override
	public void removeAdmin(WorkspaceUser user)
			throws WorkspaceCommunicationException {
		try {
			wsjongo.getCollection(COL_ADMINS).remove(M_ADMIN_QRY,
					user.getUser());
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
	}

	@Override
	public void addAdmin(WorkspaceUser user)
			throws WorkspaceCommunicationException {
		try {
			wsjongo.getCollection(COL_ADMINS).update(M_ADMIN_QRY,
					user.getUser()).upsert().with(M_ADMIN_QRY, user.getUser());
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(
					"There was a problem communicating with the database", me);
		}
	}
}
