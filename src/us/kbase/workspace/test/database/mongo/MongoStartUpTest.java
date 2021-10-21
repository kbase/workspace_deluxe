package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.assertExceptionCorrect;
import static us.kbase.common.test.TestCommon.set;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
	
	// TODO TEST each one of the startup tests takes 2 seconds. What's taking so long? Indexes?
	
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
		
		failMongoWSStart(db, new CorruptWorkspaceDBException(
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
	
	@Test
	public void indexesConfig() {
		final Set<Document> indexes = new HashSet<>();
		db.getCollection("config").listIndexes().forEach((Consumer<Document>) indexes::add);
		assertThat("incorrect indexes", indexes, is(set(
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("config", 1))
						.append("name", "config_1")
						.append("ns", "MongoStartUpTest.config"),
				new Document("v", INDEX_VER)
						.append("key", new Document("_id", 1))
						.append("name", "_id_")
						.append("ns", "MongoStartUpTest.config")
				)));
	}
	
	@Test
	public void indexesWorkspaces() {
		final Set<Document> indexes = new HashSet<>();
		db.getCollection("workspaces").listIndexes().forEach((Consumer<Document>) indexes::add);
		assertThat("incorrect indexes", indexes, is(set(
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
				)));
	}
	
	@Test
	public void indexesWorkspaceACLs() {
		final Set<Document> indexes = new HashSet<>();
		db.getCollection("workspaceACLs").listIndexes().forEach((Consumer<Document>) indexes::add);
		assertThat("incorrect indexes", indexes, is(set(
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
				)));
	}
	
	@Test
	public void indexesWorkspaceObjects() {
		final Set<Document> indexes = new HashSet<>();
		db.getCollection("workspaceObjects").listIndexes()
				.forEach((Consumer<Document>) indexes::add);
		assertThat("incorrect indexes", indexes, is(set(
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
				)));
	}
	
	@Test
	public void indexesWorkspaceObjectVersions() {
		final Set<Document> indexes = new HashSet<>();
		db.getCollection("workspaceObjVersions").listIndexes()
				.forEach((Consumer<Document>) indexes::add);
		assertThat("incorrect indexes", indexes, is(set(
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
				)));
	}
	
	@Test
	public void indexesAdmins() {
		final Set<Document> indexes = new HashSet<>();
		db.getCollection("admins").listIndexes().forEach((Consumer<Document>) indexes::add);
		assertThat("incorrect indexes", indexes, is(set(
				new Document("v", INDEX_VER)
						.append("unique", true)
						.append("key", new Document("user", 1))
						.append("name", "user_1")
						.append("ns", "MongoStartUpTest.admins"),
				new Document("v", INDEX_VER)
						.append("key", new Document("_id", 1))
						.append("name", "_id_")
						.append("ns", "MongoStartUpTest.admins")
				)));
	}
	
}
