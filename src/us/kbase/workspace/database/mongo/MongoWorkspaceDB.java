package us.kbase.workspace.database.mongo;

import static java.util.Objects.requireNonNull;
import static us.kbase.workspace.database.Util.noNulls;
import static us.kbase.workspace.database.mongo.CollectionNames.COL_ADMINS;
import static us.kbase.workspace.database.mongo.CollectionNames.COL_WS_CNT;
import static us.kbase.workspace.database.mongo.CollectionNames.COL_WORKSPACES;
import static us.kbase.workspace.database.mongo.CollectionNames.COL_WS_ACLS;
import static us.kbase.workspace.database.mongo.CollectionNames.COL_WORKSPACE_OBJS;
import static us.kbase.workspace.database.mongo.CollectionNames.COL_WORKSPACE_VERS;
import static us.kbase.workspace.database.mongo.CollectionNames.COL_PROVENANCE;
import static us.kbase.workspace.database.mongo.CollectionNames.COL_SCHEMA_CONFIG;
import static us.kbase.workspace.database.mongo.CollectionNames.COL_DYNAMIC_CONFIG;
import static us.kbase.workspace.database.mongo.ObjectInfoUtils.metaMongoArrayToHash;
import static us.kbase.workspace.database.mongo.ObjectInfoUtils.metaHashToMongoArray;

import java.io.IOException;
import java.time.Clock;
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
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.LoggerFactory;

import us.kbase.common.utils.Counter;
import us.kbase.common.utils.CountingOutputStream;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.ExtractedMetadata;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.exceptions.ExceededMaxMetadataSizeException;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.RemappedId;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.DynamicConfig;
import us.kbase.workspace.database.DynamicConfig.DynamicConfigUpdate;
import us.kbase.workspace.database.ObjectReferenceSet;
import us.kbase.workspace.database.WorkspaceUserMetadata.MetadataException;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.CopyResult;
import us.kbase.workspace.database.ListObjectsParameters.ResolvedListObjectParameters;
import us.kbase.workspace.database.MetadataUpdate;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectInfoWithModDate;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.PermissionSet.Builder;
import us.kbase.workspace.database.provenance.Provenance;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedObjectID;
import us.kbase.workspace.database.ResolvedObjectIDNoVer;
import us.kbase.workspace.database.ResolvedSaveObject;
import us.kbase.workspace.database.ResolvedWorkspaceID;
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
import us.kbase.workspace.database.exceptions.NoObjectDataException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.exceptions.WorkspaceDBInitializationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;
import us.kbase.workspace.database.provenance.ExternalData;
import us.kbase.workspace.database.provenance.ProvenanceAction;
import us.kbase.workspace.database.provenance.SubAction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DuplicateKeyException;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;

public class MongoWorkspaceDB implements WorkspaceDatabase {

	/* NOTES ON SHARDING:
	 * - workspace cannot be sharded as it requires unique indexes on id and name. It's
	 *   extremely unlikely to get big enough to require sharding though. The WSobject & ver
	 *   collections are 2-3 orders of magnitude larger in prod.
	 * - WS ACLs could be sharded on a hashed WSID, but probably not necessary.
	 * - WS objects could be sharded on a hashed WSID. This means that large workspaces would
	 *   exist entirely on one shard, unfortunately, but that index must be unique.
	 * - WS object versions could be sharded on a hashed WSID or combined WSID/Object ID. The
	 *   latter would split objects between shards, but all the versions for a particular object
	 *   would remain on one shard. Again, the WSID/Object ID index must be unique.
	 * - Provenance could be sharded on the (hashed?) Mongo Object ID.
	 * - Admins and configs are tiny.
	 */

	// TODO TEST need some lower level tests for this module rather than just integration tests
	// TODO MONGO in 4.2 index creation is no longer blocking (I think) = big mess,
	// could cause index build fail and corrupt data in the db
	// Need to look into this before upgrading to 4.2

	private static final long OBJECT_DATA_FETCH_TIMEOUT_SEC = 900;

	public static final AllUsers ALL_USERS = Workspace.ALL_USERS;

	//TODO CONFIG this should really be configurable
	private static final long MAX_PROV_SIZE = 1000000;

	/** The expected version of the database schema. */
	public static final int SCHEMA_VERSION = 2;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String ERR_DB_COMM =
			"There was a problem communicating with the database";

	private final MongoDatabase wsmongo;
	private final BlobStore blob;
	private final QueryMethods query;
	private final ObjectInfoUtils objutils;

	// TODO TEST add more unit tests using the mocked clock
	private final Clock clock;

	private static final IndexOptions IDX_UNIQ = new IndexOptions().unique(true);
	private static final IndexOptions IDX_SPARSE = new IndexOptions().sparse(true);
	private static final IndexOptions IDX_UNIQ_SPARSE = new IndexOptions()
			.unique(true).sparse(true);

	private static HashMap<String, List<IndexSpecification>> getIndexSpecs() {
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
		// once, only 1 workspace could be cloned at a time w/o sparse.
		ws.add(idxSpec(Fields.WS_NAME, 1, IDX_UNIQ_SPARSE));
		//find workspaces by metadata
		ws.add(idxSpec(Fields.WS_META, 1, IDX_SPARSE));
		indexes.put(COL_WORKSPACES, ws);

		//workspace acl indexes
		final LinkedList<IndexSpecification> wsACL = new LinkedList<>();
		// TODO CODE add a unique index on WSID and user since a user should only have 1 perm
		// per workspace
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
		//find versions per type and sort descending on version
		wsVer.add(idxSpec(
				Fields.VER_TYPE_NAME, 1, Fields.VER_TYPE_MAJOR_VERSION, 1,
				Fields.VER_TYPE_MINOR_VERSION, 1,
				Fields.VER_WS_ID, 1, Fields.VER_ID, 1, Fields.VER_VER, -1));
		//find versions per type with major version and sort descending on version
		wsVer.add(idxSpec(
				Fields.VER_TYPE_NAME, 1, Fields.VER_TYPE_MAJOR_VERSION, 1,
				Fields.VER_WS_ID, 1, Fields.VER_ID, 1, Fields.VER_VER, -1));
		//find versions per type name only and sort descending on version
		wsVer.add(idxSpec(
				Fields.VER_TYPE_NAME, 1,
				Fields.VER_WS_ID, 1, Fields.VER_ID, 1, Fields.VER_VER, -1));
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

		//schema indexes
		final LinkedList<IndexSpecification> schema = new LinkedList<>();
		//ensure only one config object
		schema.add(idxSpec(Fields.SCHEMA_CONFIG_KEY, 1, IDX_UNIQ));
		indexes.put(COL_SCHEMA_CONFIG, schema);

		// dynamic config indexes
		final LinkedList<IndexSpecification> dyncfg = new LinkedList<>();
		// ensure no duplicate config items
		dyncfg.add(idxSpec(Fields.DYNAMIC_CONFIG_KEY, 1, IDX_UNIQ));
		indexes.put(COL_DYNAMIC_CONFIG, dyncfg);

		return indexes;
	}

	/** Create a workspace database using MongoDB as a backend.
	 * @param workspaceDB the MongoDB in which to store data
	 * @param blobStore the blob store in which to store object data
	 * @throws WorkspaceCommunicationException if the backend cannot be reached
	 * @throws WorkspaceDBInitializationException if the database cannot be initialized
	 * @throws CorruptWorkspaceDBException if the database is corrupt.
	 */
	public MongoWorkspaceDB(final MongoDatabase workspaceDB, final BlobStore blobStore)
			throws WorkspaceCommunicationException,
				WorkspaceDBInitializationException, CorruptWorkspaceDBException {
		this(workspaceDB, blobStore, Clock.systemDefaultZone());
	}

	// for tests
	private MongoWorkspaceDB(
			final MongoDatabase workspaceDB,
			final BlobStore blobStore,
			final Clock clock)
			throws WorkspaceCommunicationException,
				WorkspaceDBInitializationException, CorruptWorkspaceDBException {
		if (workspaceDB == null || blobStore == null) {
			throw new NullPointerException("No arguments can be null");
		}
		this.clock = clock;
		wsmongo = workspaceDB;
		query = new QueryMethods(wsmongo, (AllUsers) ALL_USERS, COL_WORKSPACES,
				COL_WORKSPACE_OBJS, COL_WORKSPACE_VERS, COL_WS_ACLS);
		objutils = new ObjectInfoUtils(query);
		blob = blobStore;
		//TODO DBCONSIST check a few random types and make sure they exist
		ensureIndexes(wsmongo);
		checkSchema(wsmongo);
	}

	private static class IndexSpecification {
		public Document index;
		public IndexOptions options;

		private IndexSpecification(final Document index, final IndexOptions options) {
			this.index = index;
			this.options = options;
		}
	}

	// 1 for ascending sort, -1 for descending
	private static IndexSpecification idxSpec(
			final String field, final int ascendingSort,
			final IndexOptions... options) {
		return new IndexSpecification(
				new Document(field, ascendingSort),
				getIndexOptions(options));
	}

	private static IndexSpecification idxSpec(
			final String field1, final int ascendingSort1,
			final String field2, final int ascendingSort2,
			final IndexOptions... options) {
		return new IndexSpecification(
				new Document(field1, ascendingSort1)
						.append(field2, ascendingSort2),
				getIndexOptions(options));
	}

	private static IndexSpecification idxSpec(
			final String field1, final int ascendingSort1,
			final String field2, final int ascendingSort2,
			final String field3, final int ascendingSort3,
			final IndexOptions... options) {
		return new IndexSpecification(
				new Document(field1, ascendingSort1)
					.append(field2, ascendingSort2)
					.append(field3, ascendingSort3),
				getIndexOptions(options));
	}

	// ew. Not sure how to make this less gross though.
	private static IndexSpecification idxSpec(
			final String field1, final int ascendingSort1,
			final String field2, final int ascendingSort2,
			final String field3, final int ascendingSort3,
			final String field4, final int ascendingSort4,
			final IndexOptions... options) {
		return new IndexSpecification(
				new Document(field1, ascendingSort1)
					.append(field2, ascendingSort2)
					.append(field3, ascendingSort3)
					.append(field4, ascendingSort4),
				getIndexOptions(options));
	}

	// ew. Not sure how to make this less gross though.
	private static IndexSpecification idxSpec(
			final String field1, final int ascendingSort1,
			final String field2, final int ascendingSort2,
			final String field3, final int ascendingSort3,
			final String field4, final int ascendingSort4,
			final String field5, final int ascendingSort5,
			final IndexOptions... options) {
		return new IndexSpecification(
				new Document(field1, ascendingSort1)
					.append(field2, ascendingSort2)
					.append(field3, ascendingSort3)
					.append(field4, ascendingSort4)
					.append(field5, ascendingSort5),
				getIndexOptions(options));
	}

	// ew. Not sure how to make this less gross though.
	private static IndexSpecification idxSpec(
			final String field1, final int ascendingSort1,
			final String field2, final int ascendingSort2,
			final String field3, final int ascendingSort3,
			final String field4, final int ascendingSort4,
			final String field5, final int ascendingSort5,
			final String field6, final int ascendingSort6,
			final IndexOptions... options) {
		return new IndexSpecification(
				new Document(field1, ascendingSort1)
					.append(field2, ascendingSort2)
					.append(field3, ascendingSort3)
					.append(field4, ascendingSort4)
					.append(field5, ascendingSort5)
					.append(field6, ascendingSort6),
				getIndexOptions(options));
	}

	private static IndexOptions getIndexOptions(final IndexOptions[] options) {
		if (options.length == 1) {
			return options[0];
		}
		return new IndexOptions();
	}

	static boolean isDuplicateKeyException(final MongoWriteException mwe) {
		return mwe.getError().getCategory().equals(ErrorCategory.DUPLICATE_KEY);
	}

	public List<DependencyStatus> status() {
		//note failures are tested manually for now, if you make changes test
		//things still work
		//TODO TEST add tests exercising failures
		final List<DependencyStatus> deps = new LinkedList<>(blob.status());
		final String version;
		try {
			final Document bi = wsmongo.runCommand(new Document("buildInfo", 1));
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

	private static void checkSchema(final MongoDatabase wsmongo)
			throws WorkspaceCommunicationException, WorkspaceDBInitializationException,
				CorruptWorkspaceDBException {
		final Document cfg = new Document(
				Fields.SCHEMA_CONFIG_KEY, Fields.SCHEMA_CONFIG_VALUE);
		cfg.put(Fields.SCHEMA_CONFIG_UPDATE, false);
		cfg.put(Fields.SCHEMA_CONFIG_VERSION, SCHEMA_VERSION);
		try {
			wsmongo.getCollection(COL_SCHEMA_CONFIG).insertOne(cfg);
		} catch (MongoWriteException mwe) {
			if (isDuplicateKeyException(mwe)) {
				//ok, the version doc is already there, this isn't the first
				//startup
				checkExtantSchemaAndGetVersion(wsmongo, false, false);
			} else {
				// pretty hard to test this
				throw new WorkspaceCommunicationException(ERR_DB_COMM, mwe);
			}
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
	}

	static Optional<Integer> checkExtantSchemaAndGetVersion(
			final MongoDatabase wsmongo,
			final boolean allowNoConfig,
			final boolean skipVersionCheck)
			throws CorruptWorkspaceDBException, WorkspaceDBInitializationException,
				WorkspaceCommunicationException {
		final Document query = new Document(Fields.SCHEMA_CONFIG_KEY, Fields.SCHEMA_CONFIG_VALUE);
		try {
			final MongoCursor<Document> cur = wsmongo.getCollection(COL_SCHEMA_CONFIG).find(query)
					.iterator();
			if (!cur.hasNext()) {
				if (allowNoConfig) {
					return Optional.empty();
				}
				throw new CorruptWorkspaceDBException(
						"No config object found in the database. " +
						"This should not happen, something is very wrong.");
			}
			final Document storedCfg = cur.next();
			if (cur.hasNext()) {
				throw new CorruptWorkspaceDBException(
						"> 1 config objects found in the database. " +
						"This should not happen, something is very wrong.");
			}
			final Integer schemaVer = (Integer)storedCfg.get(Fields.SCHEMA_CONFIG_VERSION);
			if (!skipVersionCheck && schemaVer != SCHEMA_VERSION) {
				throw new WorkspaceDBInitializationException(String.format(
						"Incompatible database schema. Server is v%s, DB is v%s",
						SCHEMA_VERSION,
						storedCfg.get(Fields.SCHEMA_CONFIG_VERSION)));
			}
			if ((Boolean)storedCfg.get(Fields.SCHEMA_CONFIG_UPDATE)) {
				throw new WorkspaceDBInitializationException(String.format(
						"The database is in the middle of an update from " +
						"v%s of the schema. Aborting startup.",
						storedCfg.get(Fields.SCHEMA_CONFIG_VERSION)));
			}
			return Optional.of(schemaVer);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
	}

	static void ensureIndexes(final MongoDatabase wsdb)
			throws CorruptWorkspaceDBException, WorkspaceCommunicationException {
		final HashMap<String, List<IndexSpecification>> indexes = getIndexSpecs();
		for (final String col: indexes.keySet()) {
			for (final IndexSpecification index: indexes.get(col)) {
				try {
					wsdb.getCollection(col).createIndex(index.index, index.options);
					// this seems to be the 1 f*&)&*)*ing place where DKE is still used
				} catch (DuplicateKeyException dk) {
					throw new CorruptWorkspaceDBException(
							"Found duplicate index keys in the database, " +
							"aborting startup", dk);
				} catch (MongoException me) {
					throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
				}
			}
		}
	}

	private void updateWorkspaceModifiedDate(final ResolvedWorkspaceID rwsi)
			throws WorkspaceCommunicationException {
		try {
			wsmongo.getCollection(COL_WORKSPACES).updateOne(
					new Document(Fields.WS_ID, rwsi.getID()),
					new Document("$set",
							new Document(Fields.WS_MODDATE, Date.from(clock.instant()))));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
	}

	private static final Set<String> FLDS_CREATE_WS = newHashSet(Fields.WS_DEL, Fields.WS_OWNER);

	@Override
	public void setConfig(final DynamicConfigUpdate config, final boolean overwrite)
			throws WorkspaceCommunicationException {
		final Map<String, Object> cfg = requireNonNull(config, "config").toSet();
		final String op = overwrite ? "$set" : "$setOnInsert";
		try {
			// could do an insertMany here but meh, performance is not going to be an issue
			for (final String key: cfg.keySet()) {
				wsmongo.getCollection(COL_DYNAMIC_CONFIG).updateOne(
						new Document(Fields.DYNAMIC_CONFIG_KEY, key),
						new Document(
								op, new Document(Fields.DYNAMIC_CONFIG_VALUE, cfg.get(key))),
						new UpdateOptions().upsert(true));
			}
			for (final String key: config.toRemove()) {
				wsmongo.getCollection(COL_DYNAMIC_CONFIG).deleteOne(
						new Document(Fields.DYNAMIC_CONFIG_KEY, key));
			}
		} catch (MongoException me) {
			// not sure how to test this
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
	}

	@Override
	public DynamicConfig getConfig()
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final Map<String, Object> values = new HashMap<>();
		try {
			// in the future might want to specify fields to pull
			for (final Document d: wsmongo.getCollection(COL_DYNAMIC_CONFIG).find()) {
				values.put(
						d.getString(Fields.DYNAMIC_CONFIG_KEY),
						d.get(Fields.DYNAMIC_CONFIG_VALUE));
			}
		} catch (MongoException me) {
			// not sure how to test this
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
		try {
			return DynamicConfig.getBuilder().withMap(values).build();
		} catch (IllegalArgumentException e) {
			throw new CorruptWorkspaceDBException(
					"Illegal configuration values found in database", e);
		}
	}

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
					COL_WORKSPACES, new Document(Fields.WS_NAME, wsname),
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
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
		final long count = updateWorkspaceCounter();
		final Document ws = new Document();
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
			wsmongo.getCollection(COL_WORKSPACES).insertOne(ws);
		} catch (MongoWriteException mwe) {
			// this is almost impossible to test and will probably almost never
			// happen
			if (isDuplicateKeyException(mwe)) {
				throw new PreExistingWorkspaceException(String.format(
						"Workspace name %s is already in use", wsname), mwe);
			}
			throw new WorkspaceCommunicationException(ERR_DB_COMM, mwe);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
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

	private long updateWorkspaceCounter() throws WorkspaceCommunicationException {
		try {
			final Document updated = wsmongo.getCollection(COL_WS_CNT).findOneAndUpdate(
					new Document(Fields.CNT_ID, Fields.CNT_ID_VAL),
					new Document("$inc", new Document(Fields.CNT_NUM, 1L)),
					new FindOneAndUpdateOptions()
							.projection(new Document(Fields.CNT_NUM, 1).append(Fields.MONGO_ID, 0))
							.upsert(true)
							.returnDocument(ReturnDocument.AFTER)
			);
			return ((Number) updated.get(Fields.CNT_NUM)).longValue();
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
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

	@FunctionalInterface
	interface DocumentProvider {
		Map<String, Object> getDocument()
				throws WorkspaceCommunicationException, CorruptWorkspaceDBException,
					NoSuchObjectException;
	}
	
	@Override
	public Optional<Instant> setWorkspaceMeta(
			final ResolvedWorkspaceID rwsi,
			final MetadataUpdate meta)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		requireNonNull(rwsi, "rwsi");
		try {
			return setMetadataOnDocument(
					meta,
					COL_WORKSPACES,
					() -> query.queryWorkspace(rwsi, newHashSet(Fields.WS_META)),
					new Document(Fields.WS_ID, rwsi.getID()),
					Fields.WS_META,
					Fields.WS_MODDATE);
		} catch (NoSuchObjectException e) {
			throw new RuntimeException("You divided by zero, didn't you, you absolute muppet", e);
		}
	}
	
	@Override
	public Map<ObjectIDResolvedWS, ResolvedObjectID> setAdminObjectMeta(
			final Map<ObjectIDResolvedWS, MetadataUpdate> update)
			throws NoSuchObjectException, WorkspaceCommunicationException,
				CorruptWorkspaceDBException {
		noNulls(requireNonNull(update, "update").keySet(), "null object ID in update");
		final Map<ObjectIDResolvedWS, ResolvedObjectID> oids = resolveObjectIDs(update.keySet());
		final Set<String> fields = new HashSet<>(Arrays.asList(Fields.VER_ADMINMETA));
		for (final ObjectIDResolvedWS oirw: update.keySet()) {
			final ResolvedObjectID roi = oids.get(oirw);
			try {
				setMetadataOnDocument(
						update.get(oirw),
						COL_WORKSPACE_VERS,
						() -> queryVersions(new HashSet<>(Arrays.asList(roi)), fields, false)
								.get(roi),
						new Document(Fields.VER_WS_ID, roi.getWorkspaceIdentifier().getID())
								.append(Fields.VER_ID, roi.getId())
								.append(Fields.VER_VER, roi.getVersion()),
						Fields.VER_ADMINMETA,
						null);
			} catch (IllegalArgumentException e) {
				final String err;
				if (oirw.getVersion() == null) {
					err = String.format(
							"Error setting metadata on workspace %s id %s, object %s, "
							+ "latest version: %s",
							oirw.getWorkspaceIdentifier().getName(),
							oirw.getWorkspaceIdentifier().getID(),
							oirw.getIdentifierString(),
							e.getMessage()
					);
				} else {
					err = String.format(
							"Error setting metadata on workspace %s id %s, object %s, "
							+ "version %s: %s",
							oirw.getWorkspaceIdentifier().getName(),
							oirw.getWorkspaceIdentifier().getID(),
							oirw.getIdentifierString(),
							oirw.getVersion(),
							e.getMessage()
					);
				}
				throw new IllegalArgumentException(err, e);
			}
		}
		return oids;
	}
	
	private Optional<Instant> setMetadataOnDocument(
			final MetadataUpdate newMeta,
			final String collection,
			final DocumentProvider dp,
			final Document identifier,
			final String metaField,
			final String moddateField)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException,
				NoSuchObjectException {
		if (newMeta == null || !newMeta.hasUpdate()) {
			throw new IllegalArgumentException("No metadata changes provided");
		}
		int attempts = 1;
		Instant time = null;
		while (time == null) {
			final Map<String, Object> doc = dp.getDocument();
			@SuppressWarnings("unchecked")
			final List<Map<String, String>> mlist = (List<Map<String, String>>) doc.get(metaField);
			final Map<String, String> oldMeta = metaMongoArrayToHash(mlist);
			final Map<String, String> updatedMeta = new HashMap<>(oldMeta);
			if (newMeta.getMeta().isPresent()) {
				updatedMeta.putAll(newMeta.getMeta().get().getMetadata());
			}
			if (newMeta.getToRemove().isPresent()) {
				for (final String k: newMeta.getToRemove().get()) {
					updatedMeta.remove(k);
				}
			}
			if (oldMeta.equals(updatedMeta)) {
				return Optional.empty();
			}
			try {
				WorkspaceUserMetadata.checkMetadataSize(updatedMeta);
			} catch (MetadataException me) {
				throw new IllegalArgumentException(String.format(
						"Updated metadata exceeds allowed size of %sB",
						WorkspaceUserMetadata.MAX_METADATA_SIZE));
			}
			final Document query = identifier.append(metaField, mlist);
			final Document metaUpdate = new Document(metaField, metaHashToMongoArray(updatedMeta));
			time = _internal_setMeta(attempts, 5, collection, query, metaUpdate, moddateField);
			attempts++;
		}
		return Optional.of(time);
	}
	
	// split the method for testing purposes
	private Instant _internal_setMeta(
			final int attempts,
			final int maxattempts,
			final String collection,
			final Document query,
			final Document metaUpdate,
			final String modDateField)
			throws WorkspaceCommunicationException {
		try {
			final Instant time = clock.instant();
			if (modDateField != null) {
				metaUpdate.append(modDateField, Date.from(time));
			}
			// only match if the metadata we pulled from the db is still the same, so we don't
			// clobber any interleaving changes
			final UpdateResult ur = wsmongo.getCollection(collection).updateOne(
					query, new Document("$set", metaUpdate));
			if (ur.getModifiedCount() == 1) { //ok, it worked
				return time;
			} else if (attempts >= maxattempts) {
				throw new WorkspaceCommunicationException(
						String.format("Failed to update metadata %s times", attempts));
			} else {
				return null;
			}
		} catch (MongoException me) { /// very difficult to test
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
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
		final Document q = new Document(Fields.OBJ_WS_ID, fromWS.getID());
		//skip any objects with no versions, likely a race condition
		//or worse the db went down post version increment pre version save
		//need to move to transactional backend or relationless schema
		q.put(Fields.OBJ_VCNT, new Document("$gt", 0));
		q.put(Fields.OBJ_DEL, false);
		addExcludedToCloneQuery(fromWS, resexclude, q);
		final Document hint = new Document(Fields.OBJ_WS_ID, 1);
		hint.put(Fields.OBJ_ID, 1);
		long maxid = 0;
		try {
			final FindIterable<Document> wsobjects = query.queryCollectionCursor(
					COL_WORKSPACE_OBJS, q, FLDS_CLONE_WS, hint, -1);
			for (final Document o: wsobjects) {
				final long objid = o.getLong(Fields.OBJ_ID);
				final String name = o.getString(Fields.OBJ_NAME);
				final boolean hidden = o.getBoolean(Fields.OBJ_HIDE);
				final boolean deleted = o.getBoolean(Fields.OBJ_DEL);
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
				// so if a save fails here the reference counts in other objects are wrong.
				// need to detect failed saves on start up and fix or something
				// mark a save started, then update ref counts etc.
				// Or don't use refcounts - search for refs at time of deletion?
				saveWorkspaceObject(toWS, objid, name);
				saveObjectVersions(user, toWS, objid, versions, hidden);
			}
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
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

		final Document q = new Document(Fields.WS_ID, id);

		final Date moddate = new Date();
		final Document ws = new Document();
		ws.put(Fields.WS_MODDATE, moddate);
		ws.put(Fields.WS_NAME, newname);

		final Document update = new Document("$unset", new Document(Fields.WS_CLONING, ""));
		update.put("$set", ws);
		final UpdateResult ur;
		try {
			ur = wsmongo.getCollection(COL_WORKSPACES).updateOne(q, update);
		} catch (MongoWriteException mwe) {
			if (isDuplicateKeyException(mwe)) {
				throw new PreExistingWorkspaceException(String.format(
						"Workspace name %s is already in use", newname));
			}
			throw new WorkspaceCommunicationException(ERR_DB_COMM, mwe);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
		if (ur.getModifiedCount() != 1) {
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
			final Document q)
			throws WorkspaceCommunicationException {
		if (resexclude == null || resexclude.isEmpty()) {
			return;
		}
		final Set<Long> excludeids = new HashSet<Long>();
		for (final ResolvedObjectID o: resexclude) {
			excludeids.add(o.getId());
		}
		q.put(Fields.OBJ_ID, new Document("$nin", excludeids));
	}

	@Override
	public Instant lockWorkspace(final ResolvedWorkspaceID rwsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		try {
			wsmongo.getCollection(COL_WORKSPACES).updateOne(
					new Document(Fields.WS_ID, rwsi.getID()),
					new Document("$set", new Document(Fields.WS_LOCKED, true)));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
		// ws mod date isn't changed, maybe it should be?
		return Instant.now();
	}

	// hmm, is there a reason we list the fields rather than just pulling everything?
	// seems bug prone
	// TODO CODE maybe a field generator that deals with standard field sets? Then only have
	//           set and test in one place. A few places adding meta fields for instance
	private static final Set<String> FLDS_VER_COPYOBJ = newHashSet(
			// include full type name for rollback purposes, remove later
			Fields.VER_WS_ID, Fields.VER_ID, Fields.VER_VER, Fields.VER_TYPE_FULL,
			Fields.VER_TYPE_NAME, Fields.VER_TYPE_MAJOR_VERSION, Fields.VER_TYPE_MINOR_VERSION,
			Fields.VER_CHKSUM, Fields.VER_SIZE,
			Fields.VER_PROV, Fields.VER_REF, Fields.VER_PROVREF,
			Fields.VER_COPIED, Fields.VER_META, Fields.VER_ADMINMETA, Fields.VER_EXT_IDS);

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
		// so if a save fails here the reference counts in other objects are wrong.
		// need to detect failed saves on start up and fix or something
		// mark a save started, then update ref counts etc.
		// Or don't use refcounts - search for refs at time of deletion?
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

	@Override
	public Instant renameWorkspace(final ResolvedWorkspaceID rwsi, final String newname)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		if (newname.equals(rwsi.getName())) {
			throw new IllegalArgumentException("Workspace is already named " +
					newname);
		}
		final Instant now = Instant.now();
		try {
			wsmongo.getCollection(COL_WORKSPACES).updateOne(
					new Document(Fields.WS_ID, rwsi.getID()),
					new Document("$set", new Document(Fields.WS_NAME, newname)
							.append(Fields.WS_MODDATE, Date.from(now))));
		} catch (MongoWriteException mwe) {
			if (isDuplicateKeyException(mwe)) {
				throw new IllegalArgumentException(
						"There is already a workspace named " + newname);
			}
			throw new WorkspaceCommunicationException(ERR_DB_COMM, mwe);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
		return now;
	}

	@Override
	public ObjectInfoWithModDate renameObject(
			final ObjectIDResolvedWS oi,
			final String newname)
			throws NoSuchObjectException, WorkspaceCommunicationException {
		Set<ObjectIDResolvedWS> input = new HashSet<>(Arrays.asList(oi));
		final ResolvedObjectID roi = resolveObjectIDs(input).get(oi);
		if (newname.equals(roi.getName())) {
			throw new IllegalArgumentException("Object is already named " +
					newname);
		}
		final Instant time = Instant.now();
		try {
			wsmongo.getCollection(COL_WORKSPACE_OBJS).updateOne(
					new Document(Fields.OBJ_WS_ID, roi.getWorkspaceIdentifier().getID())
							.append(Fields.OBJ_ID, roi.getId()),
					new Document("$set", new Document(Fields.OBJ_NAME, newname)
							.append(Fields.OBJ_MODDATE, Date.from(time))));
		} catch (MongoWriteException mwe) {
			if (isDuplicateKeyException(mwe)) {
				throw new IllegalArgumentException(
						"There is already an object in the workspace named " + newname);
			}
			throw new WorkspaceCommunicationException(ERR_DB_COMM, mwe);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
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
		return Stream.of(objs).collect(Collectors.toSet());
	}

	@Override
	public String getWorkspaceDescription(final ResolvedWorkspaceID rwsi) throws
			CorruptWorkspaceDBException, WorkspaceCommunicationException {
		return (String) query.queryWorkspace(rwsi, FLDS_WS_DESC).get(Fields.WS_DESC);
	}

	@Override
	public Instant setWorkspaceDescription(final ResolvedWorkspaceID rwsi,
			final String description) throws WorkspaceCommunicationException {
		//TODO CODE generalized method for setting fields?
		final Instant now = Instant.now();
		try {
			wsmongo.getCollection(COL_WORKSPACES).updateOne(
					new Document(Fields.WS_ID, rwsi.getID()),
					new Document("$set", new Document(Fields.WS_DESC, description)
							.append(Fields.WS_MODDATE, Date.from(now))));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
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
			// TODO CODE don't think comm exception needs to be checked. Usually will just fail.
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
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		// TODO CODE error on null or empty input
		return query.queryPermissions(rwsis, null);
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
			throw new IllegalArgumentException("Permission cannot be null or NONE");
		}
		final Map<ResolvedWorkspaceID, Map<User, Permission>> userperms;
		if (user != null) {
			userperms = query.queryPermissions(rwsis, user, perm, excludeDeletedWorkspaces);
		} else {
			userperms = new HashMap<>();
		}
		final Map<ResolvedWorkspaceID, Map<User, Permission>> globalperms;
		if (excludeGlobalRead || perm.compareTo(Permission.WRITE) >= 0) {
			if (userperms.isEmpty()) {
				globalperms = new HashMap<>();
			} else {
				globalperms = query.queryPermissions(userperms.keySet(), ALL_USERS);
			}
		} else {
			globalperms = query.queryPermissions(rwsis, ALL_USERS,
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

	@Override
	public Instant setWorkspaceOwner(
			final ResolvedWorkspaceID rwsi,
			final WorkspaceUser owner,
			final WorkspaceUser newUser,
			final Optional<String> newname)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		final Instant now = Instant.now();
		final Document query = new Document(Fields.WS_ID, rwsi.getID());
		final Document set = new Document(Fields.WS_OWNER, newUser.getUser())
				.append(Fields.WS_MODDATE, Date.from(now));
		if (newname.isPresent()) {
			set.append(Fields.WS_NAME, newname.get());
		}
		try {
			wsmongo.getCollection(COL_WORKSPACES).updateOne(query, new Document("$set", set));
		} catch (MongoWriteException mwe) {
			if (isDuplicateKeyException(mwe)) {
				throw new IllegalArgumentException(
						"There is already a workspace named " + newname.get());
			}
			throw new WorkspaceCommunicationException(ERR_DB_COMM, mwe);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
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

	private Instant setPermissions(
			final ResolvedWorkspaceID wsid,
			final List<User> users,
			final Permission perm,
			final boolean checkowner)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		final WorkspaceUser owner;
		if (checkowner) {
			final Map<String, Object> ws = query.queryWorkspace(wsid, FLDS_WS_OWNER);
			if (ws == null) {
				throw new CorruptWorkspaceDBException(String.format(
						"Workspace %s was unexpectedly deleted from the database",
						wsid.getID()));
			}
			owner = new WorkspaceUser((String) ws.get(Fields.WS_OWNER));
		} else {
			owner = null;
		}
		for (final User user: users) {
			if (owner != null && owner.getUser().equals(user.getUser())) {
				continue; // can't change owner permissions
			}
			final Document query = new Document(Fields.ACL_WSID, wsid.getID())
					.append(Fields.ACL_USER, user.getUser());
			try {
				if (perm.equals(Permission.NONE)) {
					wsmongo.getCollection(COL_WS_ACLS).deleteOne(query);
				} else {
					wsmongo.getCollection(COL_WS_ACLS).updateOne(
							query,
							new Document(
									"$set", new Document(Fields.ACL_PERM, perm.getPermission())),
							new UpdateOptions().upsert(true)
					);
				}
			} catch (MongoException me) {
				throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
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
		final Map<Long, ResolvedWorkspaceID> rwsis = new HashMap<>();
		for (final ResolvedWorkspaceID rwsi: pset.getWorkspaces()) {
			rwsis.put(rwsi.getID(), rwsi);
		}
		final Document q = new Document(Fields.WS_ID, new Document("$in", rwsis.keySet()));
		if (owners != null && !owners.isEmpty()) {
			q.put(Fields.WS_OWNER, new Document(
					"$in", owners.stream().map(o -> o.getUser()).collect(Collectors.toList())));
		}
		if (meta != null && !meta.isEmpty()) {
			final List<Document> andmetaq = new LinkedList<>();
			for (final Entry<String, String> e: meta.getMetadata().entrySet()) {
				final Document mentry = new Document();
				mentry.put(Fields.META_KEY, e.getKey());
				mentry.put(Fields.META_VALUE, e.getValue());
				andmetaq.add(new Document(Fields.WS_META, mentry));
			}
			q.put("$and", andmetaq); //note more than one entry is untested
		}
		if (before != null || after != null) {
			final Document d = new Document();
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
	private ObjectInformation saveObjectVersion(
			final WorkspaceUser user,
			final ResolvedWorkspaceID wsid,
			final long objectid,
			final ObjectSavePackage pkg)
			throws WorkspaceCommunicationException {
		final Map<String, Object> version = new HashMap<String, Object>();
		final AbsoluteTypeDefId t = pkg.wo.getRep().getValidationTypeDefId();
		version.put(Fields.VER_SAVEDBY, user.getUser());
		version.put(Fields.VER_CHKSUM, pkg.wo.getRep().getMD5().getMD5());
		version.put(Fields.VER_META, metaHashToMongoArray(pkg.wo.getUserMeta().getMetadata()));
		version.put(Fields.VER_REF, pkg.refs);
		version.put(Fields.VER_PROVREF, pkg.provrefs);
		version.put(Fields.VER_PROV, pkg.provid);
		version.put(Fields.VER_TYPE_NAME, t.getType().getTypeString());
		version.put(Fields.VER_TYPE_MAJOR_VERSION, t.getMajorVersion());
		version.put(Fields.VER_TYPE_MINOR_VERSION, t.getMinorVersion());
		// TODO CODE this field is here to allow for rollbacks from v0.12.0. remove later.
		version.put(Fields.VER_TYPE_FULL, t.getTypeString());
		version.put(Fields.VER_SIZE, pkg.wo.getRep().getRelabeledSize());
		version.put(Fields.VER_RVRT, null);
		version.put(Fields.VER_COPIED, null);
		version.put(Fields.VER_EXT_IDS, extractedIDsToStrings(pkg.wo.getExtractedIDs()));

		saveObjectVersions(user, wsid, objectid, Arrays.asList(version), pkg.wo.isHidden());
		
		return ObjectInformation.getBuilder()
				.withObjectID(objectid)
				.withObjectName(pkg.name)
				.withType(pkg.wo.getRep().getValidationTypeDefId())
				.withSavedDate((Date) version.get(Fields.VER_SAVEDATE))
				.withVersion((int) version.get(Fields.VER_VER))
				.withSavedBy(user)
				.withWorkspace(wsid)
				.withChecksum(pkg.wo.getRep().getMD5())
				.withSize(pkg.wo.getRep().getRelabeledSize())
				.withUserMetadata(new UncheckedUserMetadata(pkg.wo.getUserMeta()))
				.build();
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
		final Document set = new Document(Fields.OBJ_DEL, false)
				.append(Fields.OBJ_MODDATE, saved)
				.append(Fields.OBJ_LATEST, null);
		final Document update = new Document(
				"$inc", new Document(Fields.OBJ_VCNT, versions.size()))
				.append("$set", set)
				.append("$push", new Document(Fields.OBJ_REFCOUNTS, new Document("$each", zeros)));
		if (hidden != null) {
			set.append(Fields.OBJ_HIDE, hidden);
		}
		try {
			final Document res = wsmongo.getCollection(COL_WORKSPACE_OBJS).findOneAndUpdate(
					new Document(Fields.OBJ_WS_ID, wsid.getID()).append(Fields.OBJ_ID, objectid),
					update,
					new FindOneAndUpdateOptions()
							.projection(new Document(Fields.OBJ_VCNT, 1)
									.append(Fields.MONGO_ID, 0))
							.returnDocument(ReturnDocument.AFTER)
			);
			ver = res.getInteger(Fields.OBJ_VCNT) - versions.size() + 1;
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
		//TODO look into why saving array of maps via List.ToArray() makes Lazy?Objects return, which screw up everything
		// note ^ this may or may not be moot with the conversion to Document
		final List<Document> dbo = new LinkedList<>();
		for (final Map<String, Object> v: versions) {
			v.put(Fields.VER_SAVEDATE, saved);
			v.put(Fields.VER_WS_ID, wsid.getID());
			v.put(Fields.VER_ID, objectid);
			v.put(Fields.VER_VER, ver++);
			final Document d = new Document();
			for (final Entry<String, Object> e: v.entrySet()) {
				d.put(e.getKey(), e.getValue());
			}
			dbo.add(d);
		}

		try {
			wsmongo.getCollection(COL_WORKSPACE_VERS).insertMany(dbo);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
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
		final Document dbo = new Document();
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
			wsmongo.getCollection(COL_WORKSPACE_OBJS).insertOne(dbo);
		} catch (MongoWriteException mwe) {
			if (isDuplicateKeyException(mwe)) {
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
			}
			throw new WorkspaceCommunicationException(ERR_DB_COMM, mwe);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
		return new IDName(objectid, name);
	}

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
			// 19/2/4: I don't think MongoReferences even exist any more, so I dunno what this is
			// talking about.
			// That being said, these need to stay separate so the prov refs can be split
			// up into the provenance actions correctly (which is a really bad idea, but too
			// late to fix easily now. Would need retroactive DB schema changes
			pkg.provrefs = refsToString(o.getProvRefs());
			pkg.wo = o;
			checkObjectLength(toDocument(o.getProvenance()), MAX_PROV_SIZE,
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

	//at this point the objects are expected to be validated and references rewritten
	@Override
	public List<ObjectInformation> saveObjects(final WorkspaceUser user,
			final ResolvedWorkspaceID rwsi,
			final List<ResolvedSaveObject> objects)
			throws WorkspaceCommunicationException,
			NoSuchObjectException {
		//TODO CODE break this up
		//this method must maintain the order of the objects

		final List<ObjectSavePackage> packages = saveObjectsBuildPackages(objects);
		final Map<ObjectIDNoWSNoVer, List<ObjectSavePackage>> idToPkg = new HashMap<>();

		//list all the save packages by object id/name
		for (final ObjectSavePackage p: packages) {
			final ObjectIDNoWSNoVer o = p.wo.getObjectIdentifier();
			if (idToPkg.get(o) == null) {
				idToPkg.put(o, new ArrayList<>());
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
			lastid = ((Number) wsmongo.getCollection(COL_WORKSPACES).findOneAndUpdate(
					new Document(Fields.WS_ID, wsidmongo.getID()),
					new Document("$inc", new Document(Fields.WS_NUMOBJ, newobjects)),
					new FindOneAndUpdateOptions()
							.projection(new Document(Fields.WS_NUMOBJ, 1)
									.append(Fields.MONGO_ID, 0))
							.returnDocument(ReturnDocument.AFTER)
			).get(Fields.WS_NUMOBJ)).longValue();
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
		long newid = lastid - newobjects + 1;
		return newid;
	}

	// has a side effect of setting the provid field on each package
	private void saveProvenance(final List<ObjectSavePackage> packages)
			throws WorkspaceCommunicationException {
		final Map<ObjectSavePackage, Document> provmap = new HashMap<>();
		for (final ObjectSavePackage p: packages) {
			provmap.put(p, new Document(toDocument(p.wo.getProvenance())));
		}
		try {
			wsmongo.getCollection(COL_PROVENANCE).insertMany(new LinkedList<>(provmap.values()));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
		for (final ObjectSavePackage p: packages) {
			p.provid = (ObjectId) provmap.get(p).get(Fields.MONGO_ID); // ew, side effect
		}
	}

	private <T> List<T> emptyToNull(final List<T> list) {
		return list.isEmpty() ? null : list;
	}

	private Map<String, String> emptyToNull(final Map<String, String> map) {
		return map.isEmpty() ? null : map;
	}

	private Map<String, Object> toDocument(final Provenance p) {
		final Map<String, Object> ret = new HashMap<>();
		ret.put(Fields.PROV_USER, p.getUser().getUser());
		// this is a bit of a hack, but the JSON representation of an Instant is ~30 chars bigger
		// than a Date and not always the same length, which screws with the size calculations in
		// tests when writing this map to JSON (which is also a bit of a hack, but doesn't need to
		// be byte level accurate). Both instants and dates are accepted by Mongo so just use a
		// Date here.
		// Might make more sense to write BSON vs. JSON for the size calcs.
		ret.put(Fields.PROV_DATE, Date.from(p.getDate()));
		ret.put(Fields.PROV_WS_ID, p.getWorkspaceID().orElse(null));
		final List<Map<String, Object>> actions = new LinkedList<>();
		ret.put(Fields.PROV_ACTIONS, actions);
		for (final ProvenanceAction pa: p.getActions()) {
			final Map<String, Object> paret = new HashMap<>();
			actions.add(paret);
			paret.put(Fields.PROV_ACTION_CALLER, pa.getCaller().orElse(null));
			paret.put(Fields.PROV_ACTION_COMMAND_LINE, pa.getCommandLine().orElse(null));
			paret.put(Fields.PROV_ACTION_CUSTOM, emptyToNull(pa.getCustom()));
			paret.put(Fields.PROV_ACTION_DESCRIPTION, pa.getDescription().orElse(null));
			paret.put(Fields.PROV_ACTION_INCOMING_ARGS, emptyToNull(pa.getIncomingArgs()));
			paret.put(Fields.PROV_ACTION_METHOD, pa.getMethod().orElse(null));
			paret.put(Fields.PROV_ACTION_METHOD_PARAMS, emptyToNull(pa.getMethodParameters()));
			paret.put(Fields.PROV_ACTION_OUTGOING_ARGS, emptyToNull(pa.getOutgoingArgs()));
			paret.put(Fields.PROV_ACTION_SCRIPT, pa.getScript().orElse(null));
			paret.put(Fields.PROV_ACTION_SCRIPT_VER, pa.getScriptVersion().orElse(null));
			paret.put(Fields.PROV_ACTION_SERVICE, pa.getServiceName().orElse(null));
			paret.put(Fields.PROV_ACTION_SERVICE_VER, pa.getServiceVersion().orElse(null));
			paret.put(Fields.PROV_ACTION_TIME, pa.getTime().map(t -> Date.from(t)).orElse(null));
			paret.put(Fields.PROV_ACTION_WS_OBJS, emptyToNull(pa.getWorkspaceObjects()));

			final List<Map<String, Object>> extdata = new LinkedList<>();
			paret.put(Fields.PROV_ACTION_EXTERNAL_DATA, extdata);
			for (final ExternalData d: pa.getExternalData()) {
				final Map<String, Object> dret = new HashMap<>();
				extdata.add(dret);
				dret.put(Fields.PROV_EXTDATA_DATA_ID, d.getDataID().orElse(null));
				dret.put(Fields.PROV_EXTDATA_DATA_URL,
						d.getDataURL().map(u -> u.toString()).orElse(null));
				dret.put(Fields.PROV_EXTDATA_DESCRIPTION, d.getDescription().orElse(null));
				dret.put(Fields.PROV_EXTDATA_RESOURCE_DATE,
						d.getResourceReleaseDate().orElse(null));
				dret.put(Fields.PROV_EXTDATA_RESOURCE_NAME, d.getResourceName().orElse(null));
				dret.put(Fields.PROV_EXTDATA_RESOURCE_URL,
						d.getResourceURL().map(u -> u.toString()).orElse(null));
				dret.put(Fields.PROV_EXTDATA_RESOURCE_VER, d.getResourceVersion().orElse(null));
			}

			final List<Map<String, Object>> subactions = new LinkedList<>();
			paret.put(Fields.PROV_ACTION_SUB_ACTIONS, subactions);
			for (final SubAction a: pa.getSubActions()) {
				final Map<String, Object> aret = new HashMap<>();
				subactions.add(aret);
				aret.put(Fields.PROV_SUBACTION_CODE_URL,
						a.getCodeURL().map(u -> u.toString()).orElse(null));
				aret.put(Fields.PROV_SUBACTION_COMMIT, a.getCommit().orElse(null));
				aret.put(Fields.PROV_SUBACTION_ENDPOINT_URL,
						a.getEndpointURL().map(u -> u.toString()).orElse(null));
				aret.put(Fields.PROV_SUBACTION_NAME, a.getName().orElse(null));
				aret.put(Fields.PROV_SUBACTION_VER, a.getVersion().orElse(null));
			}
		}
		return ret;
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
		final Document update = new Document("$inc",
				new Document(Fields.OBJ_REFCOUNTS + "." + (vc.ver - 1),
						vc.count));
		final List<Document> orquery = new LinkedList<>();
		for (final Long ws: wsToObjs.keySet()) {
			final Document query = new Document(Fields.OBJ_WS_ID, ws);
			query.put(Fields.OBJ_ID, new Document("$in", wsToObjs.get(ws)));
			orquery.add(query);
		}
		try {
			wsmongo.getCollection(COL_WORKSPACE_OBJS).updateMany(
					new Document("$or", orquery), update);
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
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
				try {
					blob.saveBlob(new MD5(md5), p.wo.getRep(), true); //always sorted in 0.2.0+
				} catch (BlobStoreCommunicationException e) {
					throw new WorkspaceCommunicationException(
							e.getLocalizedMessage(), e);
				} catch (BlobStoreAuthorizationException e) {
					throw new WorkspaceCommunicationException(
							"Authorization error communicating with the backend storage system",
							e);
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
			Fields.VER_VER, Fields.VER_META, Fields.VER_ADMINMETA,
			Fields.VER_TYPE_NAME, Fields.VER_TYPE_MAJOR_VERSION, Fields.VER_TYPE_MINOR_VERSION,
			Fields.VER_SAVEDATE, Fields.VER_SAVEDBY,
			Fields.VER_CHKSUM, Fields.VER_SIZE, Fields.VER_PROV,
			Fields.VER_PROVREF, Fields.VER_REF, Fields.VER_EXT_IDS,
			Fields.VER_COPIED);

	@Override
	public Map<ObjectIDResolvedWS, WorkspaceObjectData.Builder> getObjects(
				final Set<ObjectIDResolvedWS> objs,
				final boolean exceptIfDeleted,
				final boolean includeDeleted,
				final boolean exceptIfMissing)
			throws WorkspaceCommunicationException, NoSuchObjectException {

		// TODO CODE update these to return Documents instead of Maps
		// document are easier to deal with w/ mongo stuff
		final Map<ObjectIDResolvedWS, ResolvedObjectID> resobjs =
				resolveObjectIDs(objs, exceptIfDeleted, includeDeleted, exceptIfMissing);
		final Map<ResolvedObjectID, Map<String, Object>> vers =
				queryVersions(
						new HashSet<>(resobjs.values()), FLDS_VER_GET_OBJECT, !exceptIfMissing);
		final Map<ObjectId, Provenance> provs = getProvenance(vers);
		final Map<ObjectIDResolvedWS, WorkspaceObjectData.Builder> ret = new HashMap<>();
		for (final ObjectIDResolvedWS o: objs) {
			final ResolvedObjectID roi = resobjs.get(o);
			if (!vers.containsKey(roi)) {
				continue; // works if roi is null or vers doesn't have the key
			}
			final Provenance prov = provs.get((ObjectId) vers.get(roi).get(Fields.VER_PROV));
			final String copyref = (String) vers.get(roi).get(Fields.VER_COPIED);
			final Reference copied = copyref == null ? null : new Reference(copyref);
			@SuppressWarnings("unchecked")
			final Map<String, List<String>> extIDs =
					(Map<String, List<String>>) vers.get(roi).get(Fields.VER_EXT_IDS);
			@SuppressWarnings("unchecked")
			final List<String> refs = (List<String>) vers.get(roi).get(Fields.VER_REF);
			final ObjectInformation info = ObjectInfoUtils.generateObjectInfo(roi, vers.get(roi));
			ret.put(o, addExternalIDs(WorkspaceObjectData.getBuilder(info, prov)
					.withReferences(refs)
					.withCopyReference(copied),
					extIDs));
		}
		return ret;
	}

	private WorkspaceObjectData.Builder addExternalIDs(
			final WorkspaceObjectData.Builder wod,
			final Map<String, List<String>> extIDs) {
		if (extIDs != null) {
			extIDs.keySet().stream().forEach(
					k -> wod.withExternalIDs(new IdReferenceType(k), extIDs.get(k)));
		}
		return wod;
	}

	@Override
	public void addDataToObjects(
			final Collection<WorkspaceObjectData.Builder> objects,
			final ByteArrayFileCacheManager dataManager,
			final int backendScaling)
			throws WorkspaceCommunicationException, TypedObjectExtractionException,
				NoObjectDataException, InterruptedException {
		noNulls(requireNonNull(objects, "objects"), "null found in objects");
		requireNonNull(dataManager, "dataManager");
		if (backendScaling < 1) {
			throw new IllegalArgumentException("backendScaling must be > 0");
		}
		if (objects.isEmpty()) {
			return;
		}
		final Map<String, List<WorkspaceObjectData.Builder>> chksum2obj = new HashMap<>();
		for (final WorkspaceObjectData.Builder b: objects) {
			if (!chksum2obj.containsKey(b.getObjectInfo().getCheckSum())) {
				chksum2obj.put(b.getObjectInfo().getCheckSum(), new LinkedList<>());
			}
			chksum2obj.get(b.getObjectInfo().getCheckSum()).add(b);
		}
		final List<BackendFileCallable> callables = chksum2obj.entrySet().stream()
				.map(e -> new BackendFileCallable(blob, e.getKey(), dataManager, e.getValue()))
				.collect(Collectors.toList());
		final ExecutorService eserv = Executors.newFixedThreadPool(
				Math.min(backendScaling, chksum2obj.size()));
		try {
			final List<Future<Void>> futures = eserv.invokeAll(
					callables, OBJECT_DATA_FETCH_TIMEOUT_SEC, TimeUnit.SECONDS);
			for (final Future<Void> f: futures) {
				f.get(); // trigger exceptions
			}
		} catch (ExecutionException e) { // just throw the 1st exception encountered
			cleanUpTempObjectFiles(objects, callables);
			// Wrap EE exceptions to get the full stack
			final Throwable cause = e.getCause();
			if (cause instanceof IOException) {
				throw new WorkspaceCommunicationException(cause.getLocalizedMessage(), e);
			} else if (cause instanceof BlobStoreCommunicationException) {
				throw new WorkspaceCommunicationException(cause.getLocalizedMessage(), e);
			} else if (cause instanceof BlobStoreAuthorizationException) {
				throw new WorkspaceCommunicationException(
						"Authorization error communicating with the backend storage system", e);
			} else if (cause instanceof NoSuchBlobException) {
				final ObjectInformation info = chksum2obj.get(((NoSuchBlobException) cause)
						.getMD5().getMD5()).get(0).getObjectInfo();
				throw new NoObjectDataException(String.format(
						"No data present for object %s/%s/%s",
						info.getWorkspaceId(), info.getObjectId(), info.getVersion()), e);
			} else if (cause instanceof TypedObjectExtractionException) {
				throw new TypedObjectExtractionException(cause.getLocalizedMessage(), e);
			} else if (cause instanceof WorkspaceCommunicationException) {
				throw new WorkspaceCommunicationException(cause.getLocalizedMessage(), e);
			} else {
				throw new RuntimeException("Unexpected error", e);
			}
		} catch (InterruptedException | RuntimeException | Error e) {
			// no easy way to test this AFAICT
			cleanUpTempObjectFiles(objects, callables);
			throw e;
		} finally {
			eserv.shutdownNow();
		}
	}

	private static class BackendFileCallable implements Callable<Void> {

		private final BlobStore blob;
		private final String chksum;
		private final ByteArrayFileCacheManager dataManager;
		private ByteArrayFileCache cacheResult = null;
		private final List<WorkspaceObjectData.Builder> wods;

		private BackendFileCallable(
				final BlobStore blob,
				final String chksum,
				final ByteArrayFileCacheManager dataManager,
				final List<WorkspaceObjectData.Builder> wods) {
			this.blob = blob;
			this.chksum = chksum;
			this.dataManager = dataManager;
			this.wods = wods;
		}

		@Override
		public Void call() throws BlobStoreAuthorizationException, BlobStoreCommunicationException,
				NoSuchBlobException, IOException, WorkspaceCommunicationException,
				TypedObjectExtractionException {
			cacheResult = blob.getBlob(new MD5(chksum), dataManager);
			for (final WorkspaceObjectData.Builder b: wods) {
				/* might be subsetting the same object the same way multiple times, but
				 * probably unlikely. If it becomes a problem memoize the subset
				 */
				b.withData(getDataSubSet(cacheResult, b.getSubsetSelection(), dataManager));
			}
			return null;
		}
	}

	private void cleanUpTempObjectFiles(
			final Collection<WorkspaceObjectData.Builder> objects,
			final List<BackendFileCallable> callables) {
		/* I assume (although this is a guess) that if a thread is interrupted while
		 * ByteArrayFileCacheManager is writing to a file, an InterruptedIOException will be
		 * thrown:
		 * https://docs.oracle.com/javase/8/docs/api/java/io/InterruptedIOException.html
		 * In that case the temp file will be cleaned up by BAFCM.
		 * Not sure how to test that though. If the workspace starts leaving temp files all over
		 * the place worth spending some time here, but for now hold off.
		 */
		for (final WorkspaceObjectData.Builder builder: objects) {
			try {
				final WorkspaceObjectData built = builder.build();
				if (built.hasData()) {
					builder.withData(null);
					built.destroy();
				}
			} catch (RuntimeException | Error e) {
				// continue
				// not really any way to test this
			}
		}
		for (final BackendFileCallable bfc: callables) {
			if (bfc.cacheResult != null) {
				try {
					bfc.cacheResult.destroy();
				} catch (RuntimeException | Error e) {
					// continue
					// not really any way to test this
				}
			}
		}
	}

	private static ByteArrayFileCache getDataSubSet(
			final ByteArrayFileCache data,
			final SubsetSelection paths,
			final ByteArrayFileCacheManager bafcMan)
			throws TypedObjectExtractionException, WorkspaceCommunicationException {
		if (paths.isEmpty()) {
			return data;
		}
		try {
			return bafcMan.getSubdataExtraction(data, paths);
		} catch (IOException e) {
			throw new WorkspaceCommunicationException(e.getLocalizedMessage(), e);
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

		final Document q = new Document("$or", Arrays.asList(
				new Document(Fields.VER_REF, new Document("$in", refStrings)),
				new Document(Fields.VER_PROVREF, new Document("$in", refStrings))));
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
			Fields.VER_TYPE_NAME, Fields.VER_TYPE_MAJOR_VERSION, Fields.VER_TYPE_MINOR_VERSION,
			Fields.VER_META, Fields.VER_ADMINMETA, Fields.VER_SAVEDATE, Fields.VER_SAVEDBY,
			Fields.VER_CHKSUM, Fields.VER_SIZE,
			Fields.VER_PROVREF, Fields.VER_REF);

	@Override
	public Map<ObjectIDResolvedWS, Set<ObjectInformation>> getReferencingObjects(
			final PermissionSet perms,
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
		final Document q = new Document(Fields.VER_WS_ID, new Document("$in", wsids));
		q.put("$or", Arrays.asList(new Document(Fields.VER_REF,
						new Document("$in", ref2id.keySet())),
				new Document(Fields.VER_PROVREF, new Document("$in", ref2id.keySet()))));
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

	private Map<ObjectId, Provenance> getProvenance(
			final Map<ResolvedObjectID, Map<String, Object>> vers)
			throws WorkspaceCommunicationException {
		final Map<ObjectId, Map<String, Object>> provIDs = new HashMap<>();
		for (final ResolvedObjectID id: vers.keySet()) {
			provIDs.put((ObjectId) vers.get(id).get(Fields.VER_PROV), vers.get(id));
		}
		final Map<ObjectId, Provenance> ret = new HashMap<>();
		final Document query = new Document(Fields.MONGO_ID,
				new Document("$in", provIDs.keySet()));
		try {
			for (final Document dbo: wsmongo.getCollection(COL_PROVENANCE).find(query)) {
				final ObjectId oid = dbo.getObjectId(Fields.MONGO_ID);
				// this list is expected to be ordered in the same order as in the incoming
				// provenance actions
				@SuppressWarnings("unchecked")
				final List<String> resolvedRefs = (List<String>) provIDs.get(oid)
						.get(Fields.VER_PROVREF);
				ret.put(oid, toProvenance(dbo, resolvedRefs));
			}
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
		return ret;
	}

	private Provenance toProvenance(
			final Document p,
			// this list is expected to be ordered in the same order as in the incoming
			// provenance actions
			final List<String> resolvedRefs) {
		// make a copy we can mutate
		final List<String> rrcopy = new LinkedList<>(resolvedRefs);
		final Provenance.Builder ret = Provenance.getBuilder(
				new WorkspaceUser(p.getString(Fields.PROV_USER)),
				p.getDate(Fields.PROV_DATE).toInstant())
				// objects saved before version 0.4.1 will have null workspace IDs
				.withWorkspaceID(p.getLong(Fields.PROV_WS_ID));
		for (final Document pa: p.getList(Fields.PROV_ACTIONS, Document.class)) {
			@SuppressWarnings("unchecked")
			final Map<String, String> precustom =
					(Map<String, String>) pa.get(Fields.PROV_ACTION_CUSTOM);
			// mongo maps are not equal to HashMaps, they implement Map but inherit from Object
			final Map<String, String> custom = precustom == null ? null : new HashMap<>(precustom);

			// adds the correct references to each prov action based on the ordering of the
			// incoming reference list
			final List<String> wsobjs = pa.getList(Fields.PROV_ACTION_WS_OBJS, String.class);
			final int refcnt = wsobjs == null ? 0 : wsobjs.size();
			final List<String> actionRefs = new LinkedList<>(rrcopy.subList(0, refcnt));
			rrcopy.subList(0, refcnt).clear();

			ret.withAction(ProvenanceAction.getBuilder()
					.withExternalData(toExternalData(pa.getList(
							Fields.PROV_ACTION_EXTERNAL_DATA, Document.class)))
					.withSubActions(toSubAction(pa.getList(
							Fields.PROV_ACTION_SUB_ACTIONS, Document.class)))
					.withCaller(pa.getString(Fields.PROV_ACTION_CALLER))
					.withCommandLine(pa.getString(Fields.PROV_ACTION_COMMAND_LINE))
					.withCustom(custom)
					.withDescription(pa.getString(Fields.PROV_ACTION_DESCRIPTION))
					.withIncomingArgs(pa.getList(Fields.PROV_ACTION_INCOMING_ARGS, String.class))
					.withMethod(pa.getString(Fields.PROV_ACTION_METHOD))
					.withMethodParameters(getParamList(pa))
					.withOutgoingArgs(pa.getList(Fields.PROV_ACTION_OUTGOING_ARGS, String.class))
					.withScript(pa.getString(Fields.PROV_ACTION_SCRIPT))
					.withScriptVersion(pa.getString(Fields.PROV_ACTION_SCRIPT_VER))
					.withServiceName(pa.getString(Fields.PROV_ACTION_SERVICE))
					.withServiceVersion(pa.getString(Fields.PROV_ACTION_SERVICE_VER))
					.withTime(getInstant(pa, Fields.PROV_ACTION_TIME))
					.withWorkspaceObjects(wsobjs)
					.withResolvedObjects(actionRefs)
					.build()
					);
		}
		return ret.build();
	}

	private List<Object> getParamList(final Document provaction) {
		// if the param list has maps in it, they're Mongo Document objects under the hood.
		// Need to run them through a converter to update them to standard maps, which aren't
		// equal() to Mongo Documents
		return MAPPER.convertValue(
				provaction.getList(Fields.PROV_ACTION_METHOD_PARAMS, Object.class),
				new TypeReference<List<Object>>() {});
	}

	private List<SubAction> toSubAction(final List<Document> subdata) {
		if (subdata == null) {
			return null;
		}
		return subdata.stream().map(s -> SubAction.getBuilder()
				.withCodeURL(s.getString(Fields.PROV_SUBACTION_CODE_URL))
				.withCommit(s.getString(Fields.PROV_SUBACTION_COMMIT))
				.withEndpointURL(s.getString(Fields.PROV_SUBACTION_ENDPOINT_URL))
				.withName(s.getString(Fields.PROV_SUBACTION_NAME))
				.withVersion(s.getString(Fields.PROV_SUBACTION_VER))
				.build())
				.collect(Collectors.toList());
	}

	private List<ExternalData> toExternalData(final List<Document> extdata) {
		if (extdata == null) {
			return null;
		}
		return extdata.stream().map(e -> ExternalData.getBuilder()
				.withDataID(e.getString(Fields.PROV_EXTDATA_DATA_ID))
				.withDataURL(e.getString(Fields.PROV_EXTDATA_DATA_URL))
				.withDescription(e.getString(Fields.PROV_EXTDATA_DESCRIPTION))
				.withResourceName(e.getString(Fields.PROV_EXTDATA_RESOURCE_NAME))
				.withResourceReleaseDate(getInstant(e, Fields.PROV_EXTDATA_RESOURCE_DATE))
				.withResourceURL(e.getString(Fields.PROV_EXTDATA_RESOURCE_URL))
				.withResourceVersion(e.getString(Fields.PROV_EXTDATA_RESOURCE_VER))
				.build())
				.collect(Collectors.toList());
	}

	private Instant getInstant(Document doc, final String field) {
		return Optional.ofNullable(doc.getDate(field)).map(d -> d.toInstant()).orElse(null);
	}

	private static final Set<String> FLDS_VER_TYPE = newHashSet(
			Fields.VER_TYPE_NAME, Fields.VER_TYPE_MAJOR_VERSION, Fields.VER_TYPE_MINOR_VERSION,
			Fields.VER_VER);

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
				ret.put(o, new TypeAndReference(
						new AbsoluteTypeDefId(
								new TypeDefName((String) v.get(Fields.VER_TYPE_NAME)),
								(int) v.get(Fields.VER_TYPE_MAJOR_VERSION),
								(int) v.get(Fields.VER_TYPE_MINOR_VERSION)),
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
		final Document q = new Document(Fields.OBJ_DEL, false);
		q.put(Fields.OBJ_WS_ID, new Document("$in", wsIDtoWS.keySet()));
		if (!prefix.isEmpty()) {
			// escape regex chars
			q.put(Fields.OBJ_NAME, new Document("$regex", "^" + Pattern.quote(prefix)));
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
			final ResolvedListObjectParameters params)
			throws WorkspaceCommunicationException {
		return new ObjectLister(wsmongo.getCollection(COL_WORKSPACE_VERS), objutils)
				.filter(params);
	}

	private static final Set<String> FLDS_VER_OBJ_HIST = newHashSet(
			Fields.VER_WS_ID, Fields.VER_ID, Fields.VER_VER,
			Fields.VER_TYPE_NAME, Fields.VER_TYPE_MAJOR_VERSION, Fields.VER_TYPE_MINOR_VERSION,
			Fields.VER_CHKSUM, Fields.VER_SIZE,
			Fields.VER_META, Fields.VER_ADMINMETA, Fields.VER_SAVEDATE, Fields.VER_SAVEDBY);

	@Override
	public List<ObjectInformation> getObjectHistory(
			final ObjectIDResolvedWS oi)
		throws NoSuchObjectException, WorkspaceCommunicationException {
		final ResolvedObjectID roi = resolveObjectIDs(new HashSet<>(Arrays.asList(oi))).get(oi);
		final ResolvedObjectIDNoVer o = new ResolvedObjectIDNoVer(roi);
		final List<Map<String, Object>> versions = queryAllVersions(
				new HashSet<>(Arrays.asList(o)), FLDS_VER_OBJ_HIST).get(o);
		final LinkedList<ObjectInformation> ret =
				new LinkedList<ObjectInformation>();
		for (final Map<String, Object> v: versions) {
			ret.add(ObjectInfoUtils.generateObjectInfo(roi, v));
		}
		return ret;
	}

	private static final Set<String> FLDS_VER_META = newHashSet(
			Fields.VER_VER,
			Fields.VER_TYPE_NAME, Fields.VER_TYPE_MAJOR_VERSION, Fields.VER_TYPE_MINOR_VERSION,
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
			fields = new HashSet<>(FLDS_VER_META);
			fields.add(Fields.VER_META);
			fields.add(Fields.VER_ADMINMETA);
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
		final Map<ObjectIDResolvedWS, ResolvedObjectID> ret = new HashMap<>();
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
	public Map<ResolvedObjectIDNoVer, Instant> setObjectsHidden(
			final Set<ObjectIDResolvedWS> objectIDs,
			final boolean hide)
			throws NoSuchObjectException, WorkspaceCommunicationException {
		//TODO CODE generalize, nearly identical to delete objects
		final Map<ObjectIDResolvedWS, ResolvedObjectID> ids =
				resolveObjectIDs(objectIDs);
		final Map<ResolvedWorkspaceID, List<Long>> toModify = new HashMap<>();
		for (final ObjectIDResolvedWS o: objectIDs) {
			final ResolvedWorkspaceID ws = o.getWorkspaceIdentifier();
			if (!toModify.containsKey(ws)) {
				toModify.put(ws, new ArrayList<Long>());
			}
			toModify.get(ws).add(ids.get(o).getId());
		}
		final Map<ResolvedWorkspaceID, Instant> modtimes = new HashMap<>();
		//Do this by workspace since per mongo docs nested $ors are crappy
		for (final ResolvedWorkspaceID ws: toModify.keySet()) {
			modtimes.put(ws, setObjectsHidden(ws, toModify.get(ws), hide));
		}
		final Map<ResolvedObjectIDNoVer, Instant> ret = new HashMap<>();
		for (final ObjectIDResolvedWS o: objectIDs) {
			final ResolvedWorkspaceID ws = o.getWorkspaceIdentifier();
			final ResolvedObjectID obj = ids.get(o);
			ret.put(new ResolvedObjectIDNoVer(obj), modtimes.get(ws));
		}
		return ret;
	}

	private Instant setObjectsHidden(final ResolvedWorkspaceID ws,
			final List<Long> objectIDs, final boolean hide)
			throws WorkspaceCommunicationException {
		//TODO CODE general set field method?
		if (objectIDs.isEmpty()) {
			throw new IllegalArgumentException("Object IDs cannot be empty");
		}
		final Instant now = clock.instant();
		try {
			wsmongo.getCollection(COL_WORKSPACE_OBJS).updateMany(
					new Document(Fields.OBJ_WS_ID, ws.getID())
							.append(Fields.OBJ_ID, new Document("$in", objectIDs)),
					new Document("$set", new Document(Fields.OBJ_HIDE, hide)));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
		return now;
	}

	@Override
	public Map<ResolvedObjectIDNoVer, Instant> setObjectsDeleted(
			final Set<ObjectIDResolvedWS> objectIDs,
			final boolean delete)
			throws NoSuchObjectException, WorkspaceCommunicationException {
		final Map<ObjectIDResolvedWS, ResolvedObjectID> ids =
				resolveObjectIDs(objectIDs, delete, true);
		final Map<ResolvedWorkspaceID, List<Long>> toModify = new HashMap<>();
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

	private Instant setObjectsDeleted(
			final ResolvedWorkspaceID ws,
			final List<Long> objectIDs,
			final boolean delete)
			throws WorkspaceCommunicationException {
		final Document query = new Document(Fields.OBJ_WS_ID, ws.getID())
				.append(Fields.OBJ_DEL, !delete);
		if (!objectIDs.isEmpty()) {
			query.append(Fields.OBJ_ID, new Document("$in", objectIDs));
		}
		final Instant time;
		try {
			time = clock.instant();
			wsmongo.getCollection(COL_WORKSPACE_OBJS).updateMany(
					query,
					new Document("$set", new Document(Fields.OBJ_DEL, delete)
							.append(Fields.OBJ_MODDATE, Date.from(time))));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
		return time;
	}

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
			wsmongo.getCollection(COL_WORKSPACES).updateOne(
					new Document(Fields.WS_ID, rwsi.getID()),
					new Document("$set", new Document(Fields.WS_DEL, delete)
							.append(Fields.WS_MODDATE, Date.from(now))));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
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
		final Document q = new Document(Fields.WS_CLONING, new Document("$exists", false));
		try {
			final DistinctIterable<String> users = wsmongo.getCollection(COL_WORKSPACES)
				.distinct(Fields.WS_OWNER, q, String.class);
			for (final String u: users) {
				ret.add(new WorkspaceUser(u));
			}
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
		return ret;
	}

	@Override
	public boolean isAdmin(WorkspaceUser putativeAdmin)
			throws WorkspaceCommunicationException {
		try {
			return wsmongo.getCollection(COL_ADMINS).countDocuments(
					new Document(Fields.ADMIN_NAME, putativeAdmin.getUser())) > 0;
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
	}


	@Override
	public Set<WorkspaceUser> getAdmins()
			throws WorkspaceCommunicationException {
		final Set<WorkspaceUser> ret = new HashSet<WorkspaceUser>();
		try {
			for (final Document dbo: wsmongo.getCollection(COL_ADMINS).find()) {
				ret.add(new WorkspaceUser((String) dbo.get(Fields.ADMIN_NAME)));
			}
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
		return ret;
	}

	@Override
	public void removeAdmin(WorkspaceUser user)
			throws WorkspaceCommunicationException {
		try {
			wsmongo.getCollection(COL_ADMINS).deleteOne(
					new Document(Fields.ADMIN_NAME, user.getUser()));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
	}

	@Override
	public void addAdmin(WorkspaceUser user)
			throws WorkspaceCommunicationException {
		try {
			wsmongo.getCollection(COL_ADMINS).updateOne(
					new Document(Fields.ADMIN_NAME, user.getUser()),
					new Document("$set", new Document(Fields.ADMIN_NAME, user.getUser())),
					new UpdateOptions().upsert(true));
		} catch (MongoException me) {
			throw new WorkspaceCommunicationException(ERR_DB_COMM, me);
		}
	}
}
