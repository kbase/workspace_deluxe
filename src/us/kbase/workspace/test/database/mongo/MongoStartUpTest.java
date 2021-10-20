package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.assertExceptionCorrect;
import static us.kbase.common.test.TestCommon.set;

import java.io.File;
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

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;

import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.typedobj.core.TempFilesManager;
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
		String mongohost = "localhost:" + mongo.getServerPort();
		mongoClient = new MongoClient(mongohost);
		final DB mdb = mongoClient.getDB("MongoStartUpTest");
		db = mongoClient.getDatabase("MongoStartUpTest");
		
		TestCommon.destroyDB(mdb);
		
		TempFilesManager tfm = new TempFilesManager(new File(TestCommon.getTempDir()));
		new MongoWorkspaceDB(mdb, new GridFSBlobStore(mdb), tfm);
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
		final DB db = mongoClient.getDB("startUpAndCheckConfigDoc");
		TempFilesManager tfm = new TempFilesManager(new File(TestCommon.getTempDir()));
		new MongoWorkspaceDB(db, new GridFSBlobStore(db), tfm);
		
		DBCursor c = db.getCollection("config").find();
		assertThat("Only one config doc", c.size(), is(1));
		DBObject cd = c.next();
		assertThat("correct config key & value", (String)cd.get("config"), is("config"));
		assertThat("not in update", (Boolean)cd.get("inupdate"), is(false));
		assertThat("schema v1", (Integer)cd.get("schemaver"), is(1));
		
		//check startup works with the config object in place
		MongoWorkspaceDB m = new MongoWorkspaceDB(db,  new GridFSBlobStore(db), tfm);
		WorkspaceInformation ws = m.createWorkspace(
				new WorkspaceUser("foo"), "bar", false, null, new WorkspaceUserMetadata());
		assertThat("check a ws field", ws.getName(), is("bar"));
	}
	
	@Test
	public void startUpWith2ConfigDocs() throws Exception {
		final DB db = mongoClient.getDB("startUpWith2ConfigDocs");
		
		final Map<String, Object> m = new HashMap<String, Object>();
		m.put("config", "config");
		m.put("inupdate", false);
		m.put("schemaver", 1);
		
		db.getCollection("config").insert(Arrays.asList(
				new BasicDBObject(m), new BasicDBObject(m)));
		
		failMongoWSStart(db, new CorruptWorkspaceDBException(
				"Found duplicate index keys in the database, aborting startup"));
	}
	
	@Test
	public void startUpWithBadSchemaVersion() throws Exception {
		final DB db = mongoClient.getDB("startUpWithBadSchemaVersion");
		
		final DBObject cfg = new BasicDBObject("config", "config");
		cfg.put("inupdate", false);
		cfg.put("schemaver", 4);
		
		db.getCollection("config").insert(cfg);
		
		failMongoWSStart(db, new WorkspaceDBInitializationException(
				"Incompatible database schema. Server is v1, DB is v4"));
	}
	
	@Test
	public void startUpWithUpdateInProgress() throws Exception {
		final DB db = mongoClient.getDB("startUpWithUpdateInProgress");
		
		final DBObject cfg = new BasicDBObject("config", "config");
		cfg.put("inupdate", true);
		cfg.put("schemaver", 1);
		
		db.getCollection("config").insert(cfg);
		
		failMongoWSStart(db, new WorkspaceDBInitializationException(
				"The database is in the middle of an update from v1 of the " +
				"schema. Aborting startup."));
	}

	private void failMongoWSStart(final DB db, final Exception exp)
			throws Exception {
		TempFilesManager tfm = new TempFilesManager(new File(TestCommon.getTempDir()));
		try {
			new MongoWorkspaceDB(db, new GridFSBlobStore(db), tfm);
			fail("started mongo with bad config");
		} catch (Exception e) {
			assertExceptionCorrect(e, exp);
		}
	}
	
	
	private Optional<Integer> invokeCheckExtantConfigAndGetVersion(
			final DB db,
			final boolean allowNoConfig,
			final boolean skipVersionCheck)
			throws Exception {
		final Method m = MongoWorkspaceDB.class.getDeclaredMethod(
				"checkExtantConfigAndGetVersion", DB.class, boolean.class, boolean.class);
		m.setAccessible(true);
		@SuppressWarnings("unchecked")
		final Optional<Integer> ver = (Optional<Integer>) m.invoke(
				null, db, allowNoConfig, skipVersionCheck);
		return ver;
	}
	
	private void failCheckExtantConfigAndGetVersion(
			final DB db,
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
		final DB db = mongoClient.getDB("checkExtantConfig");
		
		db.getCollection("config").insert(new BasicDBObject("config", "config")
				.append("inupdate", false).append("schemaver", 1));
		
		Optional<Integer> ver = invokeCheckExtantConfigAndGetVersion(db, false, false);
		assertThat("incorrect version", ver, is(Optional.of(1)));
		invokeCheckExtantConfigAndGetVersion(db, true, false); // coverage
		assertThat("incorrect version", ver, is(Optional.of(1)));
	}
	
	@Test
	public void checkExtantConfigWithNoDocument() throws Exception {
		final DB db = mongoClient.getDB("checkExtantConfigWithNoDocument");
		
		// pass case
		final Optional<Integer> ver = invokeCheckExtantConfigAndGetVersion(db, true, false);
		assertThat("incorrect version", ver, is(Optional.empty()));
		
		// fail case
		final String err = "0 config object(s) found in the database. " +
				"This should not happen, something is very wrong.";
		failCheckExtantConfigAndGetVersion(
				db, false, false, new CorruptWorkspaceDBException(err));
	}
	
	@Test
	public void checkExtantConfigWithTwoDocumentsFail() throws Exception {
		final String err = "2 config object(s) found in the database. " +
				"This should not happen, something is very wrong.";
		final DB db = mongoClient.getDB("checkExtantConfigWithTwoDocumentsFail");
		
		final Map<String, Object> m = new HashMap<String, Object>();
		m.put("config", "config");
		m.put("inupdate", false);
		m.put("schemaver", 1);
		db.getCollection("config").insert(Arrays.asList(
				new BasicDBObject(m), new BasicDBObject(m)));
		
		failCheckExtantConfigAndGetVersion(
				db, false, false, new CorruptWorkspaceDBException(err));
	}
	
	@Test
	public void checkExtantConfigWithBadSchemaVersion() throws Exception {
		final DB db = mongoClient.getDB("checkExtantConfigWithBadSchemaVersion");
		
		db.getCollection("config").insert(new BasicDBObject("config", "config")
				.append("inupdate", false).append("schemaver", 100));
		
		// pass case
		Optional<Integer> ver = invokeCheckExtantConfigAndGetVersion(db, false, true);
		assertThat("incorrect version", ver, is(Optional.of(100)));
		
		// fail case
		final String err = "Incompatible database schema. Server is v1, DB is v100";
		failCheckExtantConfigAndGetVersion(
				db, false, false, new WorkspaceDBInitializationException(err));
	}
	
	@Test
	public void checkExtantConfigInUpdate() throws Exception {
		final DB db = mongoClient.getDB("checkExtantConfigInUpdate");
		
		db.getCollection("config").insert(new BasicDBObject("config", "config")
				.append("inupdate", true).append("schemaver", 1));
		
		final String err = "The database is in the middle of an update from v1 of the schema. " +
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
				"workspaces",
				"workspaceACLs",
				"workspaceObjects",
				"workspaceObjVersions",
				// "provenance", no provenance collection because no created indexes
				"admins",
				// fs.* because we're using GridFS backend.
				// Other backends should check their own indexes
				"fs.files",
				"fs.chunks"
				);
		// this is annoying. MongoIterator has two forEach methods with different signatures
		// and so which one to call is ambiguous for lambda expressions.
		db.listCollectionNames().forEach((Consumer<String>) names::add);
		assertThat("incorrect collection names", names, is(expected));
	}
	
	// test index creation calling the static method rather than constructing the mongo ws db
	// object
	private void createIndexes(final String dbname) throws Exception {
		final DB wsdb = mongoClient.getDB(dbname);
		final Method method = MongoWorkspaceDB.class.getDeclaredMethod("ensureIndexes", DB.class);
		method.setAccessible(true);
		method.invoke(null, wsdb);
	}
	
	private Set<Document> getIndexes(final MongoDatabase db, final String collection) {
		final Set<Document> indexes = new HashSet<>();
		db.getCollection(collection).listIndexes().forEach((Consumer<Document>) indexes::add);
		return indexes;
	}
	
	private void setNamespace(final Set<Document> toBeModified, final String namespace) {
		toBeModified.forEach(d -> d.append("ns", namespace));
	}
	
	@Test
	public void indexesConfig() throws Exception {
		final Set<Document> expectedIndexes = set(
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("config", 1))
						.append("name", "config_1")
						.append("ns", "MongoStartUpTest.config"),
				new Document("v", INDEX_VER)
						.append("key", new Document("_id", 1))
						.append("name", "_id_")
						.append("ns", "MongoStartUpTest.config")
				);
		assertThat("incorrect indexes", getIndexes(db, "config"), is(expectedIndexes));
		
		final MongoDatabase wsdb = mongoClient.getDatabase("indexesConfig");
		createIndexes("indexesConfig");
		setNamespace(expectedIndexes, "indexesConfig.config");
		assertThat("incorrect indexes", getIndexes(wsdb, "config"), is(expectedIndexes));
	}
	
	@Test
	public void indexesWorkspaces() throws Exception {
		final Set<Document> expectedIndexes = set(
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("ws", 1))
						.append("name", "ws_1")
						.append("ns", "MongoStartUpTest.workspaces"),
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("sparse", true)
						.append("key", new Document("name", 1))
						.append("name", "name_1")
						.append("ns", "MongoStartUpTest.workspaces"),
				new Document("v", INDEX_VER)
						.append("key", new Document("owner", 1))
						.append("name", "owner_1")
						.append("ns", "MongoStartUpTest.workspaces"),
				new Document("v", INDEX_VER)
						.append("sparse", true)
						.append("key", new Document("meta", 1))
						.append("name", "meta_1")
						.append("ns", "MongoStartUpTest.workspaces"),
				new Document("v", INDEX_VER)
						.append("key", new Document("_id", 1))
						.append("name", "_id_")
						.append("ns", "MongoStartUpTest.workspaces")
				);
		assertThat("incorrect indexes", getIndexes(db, "workspaces"), is(expectedIndexes));
		
		final MongoDatabase wsdb = mongoClient.getDatabase("indexesWorkspaces");
		createIndexes("indexesWorkspaces");
		setNamespace(expectedIndexes, "indexesWorkspaces.workspaces");
		assertThat("incorrect indexes", getIndexes(wsdb, "workspaces"), is(expectedIndexes));
	}
	
	@Test
	public void indexesWorkspaceACLs() throws Exception {
		final Set<Document> expectedIndexes = set(
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("id", 1).append("user", 1).append("perm", 1))
						.append("name", "id_1_user_1_perm_1")
						.append("ns", "MongoStartUpTest.workspaceACLs"),
				new Document("v", INDEX_VER)
						.append("key", new Document("user", 1).append("perm", 1).append("id", 1))
						.append("name", "user_1_perm_1_id_1")
						.append("ns", "MongoStartUpTest.workspaceACLs"),
				new Document("v", INDEX_VER)
						.append("key", new Document("_id", 1))
						.append("name", "_id_")
						.append("ns", "MongoStartUpTest.workspaceACLs")
				);
		assertThat("incorrect indexes", getIndexes(db, "workspaceACLs"), is(expectedIndexes));
		
		final MongoDatabase wsdb = mongoClient.getDatabase("indexesWorkspaceACLs");
		createIndexes("indexesWorkspaceACLs");
		setNamespace(expectedIndexes, "indexesWorkspaceACLs.workspaceACLs");
		assertThat("incorrect indexes", getIndexes(wsdb, "workspaceACLs"), is(expectedIndexes));
	}
	
	@Test
	public void indexesWorkspaceObjects() throws Exception {
		final Set<Document> expectedIndexes = set(
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("ws", 1).append("name", 1))
						.append("name", "ws_1_name_1")
						.append("ns", "MongoStartUpTest.workspaceObjects"),
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("ws", 1).append("id", 1))
						.append("name", "ws_1_id_1")
						.append("ns", "MongoStartUpTest.workspaceObjects"),
				new Document("v", INDEX_VER)
						.append("key", new Document("moddate", 1))
						.append("name", "moddate_1")
						.append("ns", "MongoStartUpTest.workspaceObjects"),
				new Document("v", INDEX_VER)
						.append("key", new Document("del", 1).append("refcnt", 1))
						.append("name", "del_1_refcnt_1")
						.append("ns", "MongoStartUpTest.workspaceObjects"),
				new Document("v", INDEX_VER)
						.append("key", new Document("_id", 1))
						.append("name", "_id_")
						.append("ns", "MongoStartUpTest.workspaceObjects")
				);
		assertThat("incorrect indexes", getIndexes(db, "workspaceObjects"), is(expectedIndexes));
		
		final MongoDatabase wsdb = mongoClient.getDatabase("indexesWorkspaceObjects");
		createIndexes("indexesWorkspaceObjects");
		setNamespace(expectedIndexes, "indexesWorkspaceObjects.workspaceObjects");
		assertThat("incorrect indexes", getIndexes(wsdb, "workspaceObjects"), is(expectedIndexes));
	}
	
	@Test
	public void indexesWorkspaceObjectVersions() throws Exception {
		final Set<Document> expectedIndexes = set(
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("ws", 1).append("id", 1).append("ver", 1))
						.append("name", "ws_1_id_1_ver_1")
						.append("ns", "MongoStartUpTest.workspaceObjVersions"),
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("ws", 1).append("id", 1).append("ver", -1))
						.append("name", "ws_1_id_1_ver_-1")
						.append("ns", "MongoStartUpTest.workspaceObjVersions"),
				new Document("v", INDEX_VER)
						.append("key", new Document("tyname", 1)
								.append("tymaj", 1).append("tymin", 1)
								.append("ws", 1).append("id", 1).append("ver", -1))
						.append("name", "tyname_1_tymaj_1_tymin_1_ws_1_id_1_ver_-1")
						.append("ns", "MongoStartUpTest.workspaceObjVersions"),
				new Document("v", INDEX_VER)
						.append("key", new Document("tyname", 1).append("tymaj", 1)
								.append("ws", 1).append("id", 1).append("ver", -1))
						.append("name", "tyname_1_tymaj_1_ws_1_id_1_ver_-1")
						.append("ns", "MongoStartUpTest.workspaceObjVersions"),
				new Document("v", INDEX_VER)
						.append("key", new Document("tyname", 1)
								.append("ws", 1).append("id", 1).append("ver", -1))
						.append("name", "tyname_1_ws_1_id_1_ver_-1")
						.append("ns", "MongoStartUpTest.workspaceObjVersions"),
				new Document("v", INDEX_VER)
						.append("key", new Document("provenance", 1))
						.append("name", "provenance_1")
						.append("ns", "MongoStartUpTest.workspaceObjVersions"),
				new Document("v", INDEX_VER)
						.append("key", new Document("savedby", 1))
						.append("name", "savedby_1")
						.append("ns", "MongoStartUpTest.workspaceObjVersions"),
				new Document("v", INDEX_VER)
						.append("sparse", true)
						.append("key", new Document("provrefs", 1))
						.append("name", "provrefs_1")
						.append("ns", "MongoStartUpTest.workspaceObjVersions"),
				new Document("v", INDEX_VER)
						.append("sparse", true)
						.append("key", new Document("refs", 1))
						.append("name", "refs_1")
						.append("ns", "MongoStartUpTest.workspaceObjVersions"),
				new Document("v", INDEX_VER)
						.append("key", new Document("savedate", 1))
						.append("name", "savedate_1")
						.append("ns", "MongoStartUpTest.workspaceObjVersions"),
				new Document("v", INDEX_VER)
						.append("sparse", true)
						.append("key", new Document("meta", 1))
						.append("name", "meta_1")
						.append("ns", "MongoStartUpTest.workspaceObjVersions"),
				new Document("v", INDEX_VER)
						.append("key", new Document("_id", 1))
						.append("name", "_id_")
						.append("ns", "MongoStartUpTest.workspaceObjVersions")
				);
		assertThat("incorrect indexes", getIndexes(db, "workspaceObjVersions"),
				is(expectedIndexes));
		
		final MongoDatabase wsdb = mongoClient.getDatabase("indexesWorkspaceObjectVersions");
		createIndexes("indexesWorkspaceObjectVersions");
		setNamespace(expectedIndexes, "indexesWorkspaceObjectVersions.workspaceObjVersions");
		assertThat("incorrect indexes", getIndexes(wsdb, "workspaceObjVersions"),
				is(expectedIndexes));
	}
	
	@Test
	public void indexesAdmins() throws Exception {
		final Set<Document> expectedIndexes = set(
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("user", 1))
						.append("name", "user_1")
						.append("ns", "MongoStartUpTest.admins"),
				new Document("v", INDEX_VER)
						.append("key", new Document("_id", 1))
						.append("name", "_id_")
						.append("ns", "MongoStartUpTest.admins")
				);
		assertThat("incorrect indexes", getIndexes(db, "admins"), is(expectedIndexes));
		
		final MongoDatabase wsdb = mongoClient.getDatabase("indexesAdmins");
		createIndexes("indexesAdmins");
		setNamespace(expectedIndexes, "indexesAdmins.admins");
		assertThat("incorrect indexes", getIndexes(wsdb, "admins"), is(expectedIndexes));
	}
	
}
