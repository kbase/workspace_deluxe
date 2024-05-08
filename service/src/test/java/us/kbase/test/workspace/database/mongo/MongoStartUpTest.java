package us.kbase.test.workspace.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.test.common.TestCommon.assertExceptionCorrect;
import static us.kbase.test.common.TestCommon.set;
import static us.kbase.test.workspace.WorkspaceMongoIndex.getAndNormalizeIndexes;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.bson.Document;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

import us.kbase.test.common.TestCommon;
import us.kbase.testutils.controllers.mongo.MongoController;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.WorkspaceDBInitializationException;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;

/** Test that Mongo starts up correctly with the correct indexes and configuration document
 * Test that Mongo correctly fails to start up when the configuration document is incorrect.
 *
 * Note that creating all the indexes currently takes ~2s, so the tests that create indexes
 * are slow.
 *
 */
public class MongoStartUpTest {

	private static int INDEX_VER = 2;

	private static MongoDatabase db;
	private static MongoController mongo;
	private static MongoClient mongoClient;

	public static final TypeDefId SAFE_TYPE =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);

	@BeforeClass
	public static void setUpClass() throws Exception {
		mongo = new MongoController(
				TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using mongo temp dir " + mongo.getTempDir());
		TestCommon.stfuLoggers();
		mongoClient = MongoClients.create("mongodb://localhost:" + mongo.getServerPort());
		final MongoDatabase mdb = mongoClient.getDatabase("MongoStartUpTest");
		db = mongoClient.getDatabase("MongoStartUpTest");

		TestCommon.destroyDB(mdb);

		new MongoWorkspaceDB(mdb, new GridFSBlobStore(mdb));
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		if (mongo != null) {
			mongo.destroy(TestCommon.getDeleteTempFiles());
		}
	}

	@Before
	public void clearDB() throws Exception {
		TestCommon.destroyDB(db);
	}

	@Test
	public void startUpAndCheckConfigDoc() throws Exception {
		final MongoDatabase db = mongoClient.getDatabase("startUpAndCheckConfigDoc");
		new MongoWorkspaceDB(db, new GridFSBlobStore(db));

		final MongoCursor<Document> c = db.getCollection("config").find().iterator();
		final Document cd = c.next();
		assertThat("correct config key & value", (String)cd.get("config"), is("config"));
		assertThat("not in update", (Boolean)cd.get("inupdate"), is(false));
		assertThat("schema ver", (Integer)cd.get("schemaver"), is(2));
		assertThat("Only one config doc", c.hasNext(), is(false));

		//check startup works with the config object in place
		MongoWorkspaceDB m = new MongoWorkspaceDB(db,  new GridFSBlobStore(db));
		WorkspaceInformation ws = m.createWorkspace(
				new WorkspaceUser("foo"), "bar", false, null, new WorkspaceUserMetadata());
		assertThat("check a ws field", ws.getName(), is("bar"));
	}

	@Test
	public void startUpWith2ConfigDocs() throws Exception {
		final MongoDatabase db = mongoClient.getDatabase("startUpWith2ConfigDocs");

		final Map<String, Object> m = new HashMap<String, Object>();
		m.put("config", "config");
		m.put("inupdate", false);
		m.put("schemaver", 2);

		db.getCollection("config").insertMany(Arrays.asList(new Document(m), new Document(m)));

		failMongoWSStart(db, new CorruptWorkspaceDBException(
				"Found duplicate index keys in the database, aborting startup"));
	}

	@Test
	public void startUpWithBadSchemaVersion() throws Exception {
		final MongoDatabase db = mongoClient.getDatabase("startUpWithBadSchemaVersion");

		final Document cfg = new Document("config", "config");
		cfg.put("inupdate", false);
		cfg.put("schemaver", 50);

		db.getCollection("config").insertOne(cfg);

		failMongoWSStart(db, new WorkspaceDBInitializationException(
				"Incompatible database schema. Server is v2, DB is v50"));
	}

	@Test
	public void startUpWithUpdateInProgress() throws Exception {
		final MongoDatabase db = mongoClient.getDatabase("startUpWithUpdateInProgress");

		final Document cfg = new Document("config", "config");
		cfg.put("inupdate", true);
		cfg.put("schemaver", 2);

		db.getCollection("config").insertOne(cfg);

		failMongoWSStart(db, new WorkspaceDBInitializationException(
				"The database is in the middle of an update from v2 of the " +
				"schema. Aborting startup."));
	}

	private void failMongoWSStart(final MongoDatabase db, final Exception exp)
			throws Exception {
		try {
			new MongoWorkspaceDB(db, new GridFSBlobStore(db));
			fail("started mongo with bad config");
		} catch (Exception e) {
			assertExceptionCorrect(e, exp);
		}
	}


	private Optional<Integer> invokeCheckExtantConfigAndGetVersion(
			final MongoDatabase db,
			final boolean allowNoConfig,
			final boolean skipVersionCheck)
			throws Exception {
		final Method m = MongoWorkspaceDB.class.getDeclaredMethod(
				"checkExtantSchemaAndGetVersion",
				MongoDatabase.class,
				boolean.class,
				boolean.class);
		m.setAccessible(true);
		@SuppressWarnings("unchecked")
		final Optional<Integer> ver = (Optional<Integer>) m.invoke(
				null, db, allowNoConfig, skipVersionCheck);
		return ver;
	}

	private void failCheckExtantConfigAndGetVersion(
			final MongoDatabase db,
			final boolean allowNoConfig,
			final boolean skipVersionCheck,
			final Exception expected)
			throws Exception{
		try {
			invokeCheckExtantConfigAndGetVersion(db, allowNoConfig, skipVersionCheck);
			fail("expected exception");
		} catch (InvocationTargetException got) {
			TestCommon.assertExceptionCorrect(got.getCause(), expected);
		}
	}

	@Test
	public void checkExtantConfig() throws Exception {
		final MongoDatabase db = mongoClient.getDatabase("checkExtantConfig");

		db.getCollection("config").insertOne(new Document("config", "config")
				.append("inupdate", false).append("schemaver", 2));

		Optional<Integer> ver = invokeCheckExtantConfigAndGetVersion(db, false, false);
		assertThat("incorrect version", ver, is(Optional.of(2)));
		invokeCheckExtantConfigAndGetVersion(db, true, false); // coverage
		assertThat("incorrect version", ver, is(Optional.of(2)));
	}

	@Test
	public void checkExtantConfigWithNoDocument() throws Exception {
		final MongoDatabase db = mongoClient.getDatabase("checkExtantConfigWithNoDocument");

		// pass case
		final Optional<Integer> ver = invokeCheckExtantConfigAndGetVersion(db, true, false);
		assertThat("incorrect version", ver, is(Optional.empty()));

		// fail case
		final String err = "No config object found in the database. " +
				"This should not happen, something is very wrong.";
		failCheckExtantConfigAndGetVersion(
				db, false, false, new CorruptWorkspaceDBException(err));
	}

	@Test
	public void checkExtantConfigWithTwoDocumentsFail() throws Exception {
		final String err = "> 1 config objects found in the database. " +
				"This should not happen, something is very wrong.";
		final MongoDatabase db = mongoClient.getDatabase("checkExtantConfigWithTwoDocumentsFail");

		final Map<String, Object> m = new HashMap<String, Object>();
		m.put("config", "config");
		m.put("inupdate", false);
		m.put("schemaver", 2);
		db.getCollection("config").insertMany(Arrays.asList(new Document(m), new Document(m)));

		failCheckExtantConfigAndGetVersion(
				db, false, false, new CorruptWorkspaceDBException(err));
	}

	@Test
	public void checkExtantConfigWithBadSchemaVersion() throws Exception {
		final MongoDatabase db = mongoClient.getDatabase("checkExtantConfigWithBadSchemaVersion");

		db.getCollection("config").insertOne(new Document("config", "config")
				.append("inupdate", false).append("schemaver", 100));

		// pass case
		Optional<Integer> ver = invokeCheckExtantConfigAndGetVersion(db, false, true);
		assertThat("incorrect version", ver, is(Optional.of(100)));

		// fail case
		final String err = "Incompatible database schema. Server is v2, DB is v100";
		failCheckExtantConfigAndGetVersion(
				db, false, false, new WorkspaceDBInitializationException(err));
	}

	@Test
	public void checkExtantConfigInUpdate() throws Exception {
		final MongoDatabase db = mongoClient.getDatabase("checkExtantConfigInUpdate");

		db.getCollection("config").insertOne(new Document("config", "config")
				.append("inupdate", true).append("schemaver", 2));

		final String err = "The database is in the middle of an update from v2 of the schema. " +
				"Aborting startup.";
		failCheckExtantConfigAndGetVersion(
				db, false, false, new WorkspaceDBInitializationException(err));
	}

	@Test
	public void checkCollectionNames() throws Exception {
		// primary purpose of this test is to fail if a new collection is added so the dev
		// knows to add tests for checking indexes
		final Set<String> names = new HashSet<>();
		final Set<String> expected = set(
				"config",
				"dyncfg",
				"workspaces",
				"workspaceACLs",
				"workspaceObjects",
				"workspaceObjVersions",
				// "provenance", no provenance collection because no created indexes
				"admins"
				);
		// this is annoying. MongoIterator has two forEach methods with different signatures
		// and so which one to call is ambiguous for lambda expressions.
		db.listCollectionNames().forEach((Consumer<String>) names::add);
		assertThat("incorrect collection names", names, is(expected));
	}

	// test index creation calling the static method rather than constructing the mongo ws db
	// object
	private void createIndexes(final String dbname) throws Exception {
		final MongoDatabase wsdb = mongoClient.getDatabase(dbname);
		final Method method = MongoWorkspaceDB.class.getDeclaredMethod(
				"ensureIndexes", MongoDatabase.class);
		method.setAccessible(true);
		method.invoke(null, wsdb);
	}

	@Test
	public void indexesConfig() throws Exception {
		final Set<Document> expectedIndexes = set(
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("config", 1))
						.append("name", "config_1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("_id", 1))
						.append("name", "_id_")
				);
		assertThat("incorrect indexes", getAndNormalizeIndexes(db, "config"), is(expectedIndexes));

		final MongoDatabase wsdb = mongoClient.getDatabase("indexesConfig");
		createIndexes("indexesConfig");
		assertThat("incorrect indexes", getAndNormalizeIndexes(wsdb, "config"), is(expectedIndexes));
	}

	@Test
	public void indexesDynamicConfig() throws Exception {
		final Set<Document> expectedIndexes = set(
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("key", 1))
						.append("name", "key_1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("_id", 1))
						.append("name", "_id_")
				);
		assertThat("incorrect indexes", getAndNormalizeIndexes(db, "dyncfg"), is(expectedIndexes));

		final MongoDatabase wsdb = mongoClient.getDatabase("indexesDynConfig");
		createIndexes("indexesDynConfig");
		assertThat("incorrect indexes", getAndNormalizeIndexes(wsdb, "dyncfg"), is(expectedIndexes));
	}

	@Test
	public void indexesWorkspaces() throws Exception {
		final Set<Document> expectedIndexes = set(
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("ws", 1))
						.append("name", "ws_1"),
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("sparse", true)
						.append("key", new Document("name", 1))
						.append("name", "name_1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("owner", 1))
						.append("name", "owner_1"),
				new Document("v", INDEX_VER)
						.append("sparse", true)
						.append("key", new Document("meta", 1))
						.append("name", "meta_1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("_id", 1))
						.append("name", "_id_")
				);
		assertThat("incorrect indexes", getAndNormalizeIndexes(db, "workspaces"), is(expectedIndexes));

		final MongoDatabase wsdb = mongoClient.getDatabase("indexesWorkspaces");
		createIndexes("indexesWorkspaces");
		assertThat("incorrect indexes", getAndNormalizeIndexes(wsdb, "workspaces"), is(expectedIndexes));
	}

	@Test
	public void indexesWorkspaceACLs() throws Exception {
		final Set<Document> expectedIndexes = set(
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("id", 1).append("user", 1).append("perm", 1))
						.append("name", "id_1_user_1_perm_1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("user", 1).append("perm", 1).append("id", 1))
						.append("name", "user_1_perm_1_id_1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("_id", 1))
						.append("name", "_id_")
				);
		assertThat("incorrect indexes", getAndNormalizeIndexes(db, "workspaceACLs"), is(expectedIndexes));

		final MongoDatabase wsdb = mongoClient.getDatabase("indexesWorkspaceACLs");
		createIndexes("indexesWorkspaceACLs");
		assertThat("incorrect indexes", getAndNormalizeIndexes(wsdb, "workspaceACLs"), is(expectedIndexes));
	}

	@Test
	public void indexesWorkspaceObjects() throws Exception {
		final Set<Document> expectedIndexes = set(
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("ws", 1).append("name", 1))
						.append("name", "ws_1_name_1"),
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("ws", 1).append("id", 1))
						.append("name", "ws_1_id_1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("moddate", 1))
						.append("name", "moddate_1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("del", 1).append("refcnt", 1))
						.append("name", "del_1_refcnt_1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("_id", 1))
						.append("name", "_id_")
				);
		assertThat("incorrect indexes", getAndNormalizeIndexes(db, "workspaceObjects"), is(expectedIndexes));

		final MongoDatabase wsdb = mongoClient.getDatabase("indexesWorkspaceObjects");
		createIndexes("indexesWorkspaceObjects");
		assertThat("incorrect indexes", getAndNormalizeIndexes(wsdb, "workspaceObjects"), is(expectedIndexes));
	}

	@Test
	public void indexesWorkspaceObjectVersions() throws Exception {
		final Set<Document> expectedIndexes = set(
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("ws", 1).append("id", 1).append("ver", 1))
						.append("name", "ws_1_id_1_ver_1"),
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("ws", 1).append("id", 1).append("ver", -1))
						.append("name", "ws_1_id_1_ver_-1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("tyname", 1)
								.append("tymaj", 1).append("tymin", 1)
								.append("ws", 1).append("id", 1).append("ver", -1))
						.append("name", "tyname_1_tymaj_1_tymin_1_ws_1_id_1_ver_-1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("tyname", 1).append("tymaj", 1)
								.append("ws", 1).append("id", 1).append("ver", -1))
						.append("name", "tyname_1_tymaj_1_ws_1_id_1_ver_-1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("tyname", 1)
								.append("ws", 1).append("id", 1).append("ver", -1))
						.append("name", "tyname_1_ws_1_id_1_ver_-1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("provenance", 1))
						.append("name", "provenance_1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("savedby", 1))
						.append("name", "savedby_1"),
				new Document("v", INDEX_VER)
						.append("sparse", true)
						.append("key", new Document("provrefs", 1))
						.append("name", "provrefs_1"),
				new Document("v", INDEX_VER)
						.append("sparse", true)
						.append("key", new Document("refs", 1))
						.append("name", "refs_1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("savedate", 1))
						.append("name", "savedate_1"),
				new Document("v", INDEX_VER)
						.append("sparse", true)
						.append("key", new Document("meta", 1))
						.append("name", "meta_1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("_id", 1))
						.append("name", "_id_")
				);
		assertThat("incorrect indexes", getAndNormalizeIndexes(db, "workspaceObjVersions"),
				is(expectedIndexes));

		final MongoDatabase wsdb = mongoClient.getDatabase("indexesWorkspaceObjectVersions");
		createIndexes("indexesWorkspaceObjectVersions");
		assertThat("incorrect indexes", getAndNormalizeIndexes(wsdb, "workspaceObjVersions"),
				is(expectedIndexes));
	}

	@Test
	public void indexesAdmins() throws Exception {
		final Set<Document> expectedIndexes = set(
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("user", 1))
						.append("name", "user_1"),
				new Document("v", INDEX_VER)
						.append("key", new Document("_id", 1))
						.append("name", "_id_")
				);
		assertThat("incorrect indexes", getAndNormalizeIndexes(db, "admins"), is(expectedIndexes));

		final MongoDatabase wsdb = mongoClient.getDatabase("indexesAdmins");
		createIndexes("indexesAdmins");
		assertThat("incorrect indexes", getAndNormalizeIndexes(wsdb, "admins"), is(expectedIndexes));
	}

}