package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;
import static us.kbase.workspace.test.WorkspaceTestCommon.basicProv;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.SchemaUpdater;
import us.kbase.workspace.database.mongo.SchemaUpdater.SchemaUpdateException;
import us.kbase.workspace.database.provenance.Provenance;

public class SchemaUpdaterTest {

	// these tests take a while because each one creates indexes
	// maybe think about a way to not require that...

	private static MongoController MONGO;
	private static MongoClient MC;

	private final static String SOURCE_DB = SchemaUpdaterTest.class.getSimpleName() +
			"_source_objects";

	private final static String USER = "user";
	private final static String COL_WS_VER = "workspaceObjVersions";
	private static final String MD5PRE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
	private static final List<String> FULL_LOGS = Arrays.asList(
			"Finding types to be updated",
			"5 types found",
			"Processed type #1/5: Mod1.Type1-7.2, object count: 3, cumulative: 3",
			"Processed type #2/5: Mod2.Type1-7.2, object count: 2, cumulative: 5",
			"Processed type #3/5: Mod2.Type2-7.2, object count: 2, cumulative: 7",
			"Processed type #4/5: Mod2.Type2-8.2, object count: 1, cumulative: 8",
			"Processed type #5/5: Mod2.Type2-8.3, object count: 2, cumulative: 10"
			);

	@BeforeClass
	public static void setup() throws Exception {
		TestCommon.stfuLoggers();
		MONGO = new MongoController(
				TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using Mongo temp dir " + MONGO.getTempDir());
		System.out.println("Started test mongo instance at localhost:" +
				MONGO.getServerPort());

		MC = new MongoClient("localhost:" + MONGO.getServerPort());
		setUpTestDB();
	}

	private static void setUpTestDB() throws Exception {
		final MongoDatabase testDB = MC.getDatabase(SOURCE_DB);
		final PartialMock pm = new PartialMock(testDB);
		when(pm.clockmock.instant()).thenReturn(Instant.now());
		final WorkspaceUser u = new WorkspaceUser(USER);
		final WorkspaceUserMetadata m = new WorkspaceUserMetadata();
		pm.mdb.createWorkspace(u, "one", false, null, m);
		pm.mdb.createWorkspace(u, "two", false, null, m);
		pm.mdb.createWorkspace(u, "three", false, null, m);
		final ResolvedWorkspaceID ws1 = new ResolvedWorkspaceID(1, "one", false, false);
		final ResolvedWorkspaceID ws2 = new ResolvedWorkspaceID(2, "two", false, false);
		final ResolvedWorkspaceID ws3 = new ResolvedWorkspaceID(3, "three", false, false);

		final Provenance p = basicProv(u);
		pm.saveTestObject(ws1, u, p, "o1", "Mod1.Type1-7.2", MD5PRE + "01", 1);
		pm.saveTestObject(ws1, u, p, "o2", "Mod1.Type1-7.2", MD5PRE + "02", 1);
		pm.saveTestObject(ws1, u, p, "o2", "Mod1.Type1-7.2", MD5PRE + "03", 1);
		pm.saveTestObject(ws1, u, p, "o3", "Mod2.Type1-7.2", MD5PRE + "04", 1);
		pm.saveTestObject(ws1, u, p, "o4", "Mod2.Type1-7.2", MD5PRE + "05", 1);
		pm.saveTestObject(ws2, u, p, "o1", "Mod2.Type2-7.2", MD5PRE + "06", 1);
		pm.saveTestObject(ws2, u, p, "o1", "Mod2.Type2-7.2", MD5PRE + "07", 1);
		pm.saveTestObject(ws2, u, p, "o2", "Mod2.Type2-8.2", MD5PRE + "08", 1);
		pm.saveTestObject(ws3, u, p, "o1", "Mod2.Type2-8.3", MD5PRE + "09", 1);
		pm.saveTestObject(ws3, u, p, "o1", "Mod2.Type2-8.3", MD5PRE + "10", 1);
	}

	private final static Document BASE = new Document()
			.append("provrefs", Collections.emptyList())
			.append("copied", null)
			.append("revert", null)
			.append("size", 1L)
			.append("refs", Collections.emptyList())
			.append("meta", Collections.emptyList())
			.append("extids", new Document())
			.append("savedby", USER)
			;

	private final static List<Document> EXPECTED = Arrays.asList(
			new Document(BASE)
					.append("type", "Mod1.Type1-7.2")
					.append("tyname", "Mod1.Type1")
					.append("tymaj", 7)
					.append("tymin", 2)
					.append("ws", 1L)
					.append("id", 1L)
					.append("ver", 1)
					.append("savedate", Date.from(Instant.ofEpochMilli(10000)))
					.append("chksum", MD5PRE + "01"),
			new Document(BASE)
					.append("type", "Mod1.Type1-7.2")
					.append("tyname", "Mod1.Type1")
					.append("tymaj", 7)
					.append("tymin", 2)
					.append("ws", 1L)
					.append("id", 2L)
					.append("ver", 1)
					.append("savedate", Date.from(Instant.ofEpochMilli(20000)))
					.append("chksum", MD5PRE + "02"),
			new Document(BASE)
					.append("type", "Mod1.Type1-7.2")
					.append("tyname", "Mod1.Type1")
					.append("tymaj", 7)
					.append("tymin", 2)
					.append("ws", 1L)
					.append("id", 2L)
					.append("ver", 2)
					.append("savedate", Date.from(Instant.ofEpochMilli(30000)))
					.append("chksum", MD5PRE + "03"),
			new Document(BASE)
					.append("type", "Mod2.Type1-7.2")
					.append("tyname", "Mod2.Type1")
					.append("tymaj", 7)
					.append("tymin", 2)
					.append("ws", 1L)
					.append("id", 3L)
					.append("ver", 1)
					.append("savedate", Date.from(Instant.ofEpochMilli(40000)))
					.append("chksum", MD5PRE + "04"),
			new Document(BASE)
					.append("type", "Mod2.Type1-7.2")
					.append("tyname", "Mod2.Type1")
					.append("tymaj", 7)
					.append("tymin", 2)
					.append("ws", 1L)
					.append("id", 4L)
					.append("ver", 1)
					.append("savedate", Date.from(Instant.ofEpochMilli(50000)))
					.append("chksum", MD5PRE + "05"),
			new Document(BASE)
					.append("type", "Mod2.Type2-7.2")
					.append("tyname", "Mod2.Type2")
					.append("tymaj", 7)
					.append("tymin", 2)
					.append("ws", 2L)
					.append("id", 1L)
					.append("ver", 1)
					.append("savedate", Date.from(Instant.ofEpochMilli(60000)))
					.append("chksum", MD5PRE + "06"),
			new Document(BASE)
					.append("type", "Mod2.Type2-7.2")
					.append("tyname", "Mod2.Type2")
					.append("tymaj", 7)
					.append("tymin", 2)
					.append("ws", 2L)
					.append("id", 1L)
					.append("ver", 2)
					.append("savedate", Date.from(Instant.ofEpochMilli(70000)))
					.append("chksum", MD5PRE + "07"),
			new Document(BASE)
					.append("type", "Mod2.Type2-8.2")
					.append("tyname", "Mod2.Type2")
					.append("tymaj", 8)
					.append("tymin", 2)
					.append("ws", 2L)
					.append("id", 2L)
					.append("ver", 1)
					.append("savedate", Date.from(Instant.ofEpochMilli(80000)))
					.append("chksum", MD5PRE + "08"),
			new Document(BASE)
					.append("type", "Mod2.Type2-8.3")
					.append("tyname", "Mod2.Type2")
					.append("tymaj", 8)
					.append("tymin", 3)
					.append("ws", 3L)
					.append("id", 1L)
					.append("ver", 1)
					.append("savedate", Date.from(Instant.ofEpochMilli(90000)))
					.append("chksum", MD5PRE + "09"),
			new Document(BASE)
					.append("type", "Mod2.Type2-8.3")
					.append("tyname", "Mod2.Type2")
					.append("tymaj", 8)
					.append("tymin", 3)
					.append("ws", 3L)
					.append("id", 1L)
					.append("ver", 2)
					.append("savedate", Date.from(Instant.ofEpochMilli(100000)))
					.append("chksum", MD5PRE + "10")
			);

	private static List<ObjectId> copySourceTo(final MongoDatabase db, final int schemaver) {
		// only copy versions collection, since only affected collection
		final MongoDatabase sourceDB = MC.getDatabase(SOURCE_DB);
		final List<ObjectId> prov = new LinkedList<>();
		int count = 0;
		for (final Document d: sourceDB.getCollection(COL_WS_VER).find()) {
			d.put("savedate", Date.from(Instant.ofEpochMilli(++count * 10000)));
			db.getCollection(COL_WS_VER).insertOne(d);
			prov.add(d.getObjectId("provenance"));
		}
		db.getCollection("config").insertOne(new Document("config", "config")
				.append("schemaver", schemaver).append("inupdate", false));
		return prov;
	}

	private static void checkExpected(final MongoDatabase db, final List<ObjectId> prov) {
		final List<Document> got = new LinkedList<>();
		db.getCollection(COL_WS_VER).find().forEach((Consumer<Document>) d -> got.add(d));
		assertThat("got size != expected", got.size(), is(EXPECTED.size()));
		assertThat("got size != prov", got.size(), is(prov.size()));
		for (int i = 0; i < got.size(); i++) {
			assertThat("incorrect provenance", got.get(i).remove("provenance"), is(prov.get(i)));
			got.get(i).remove("_id");
			assertThat("incorrect ws obj ver", got.get(i), is(EXPECTED.get(i)));
		}
	}

	private static void checkConfig(final MongoDatabase db, final int schemaver) {
		final Document got = db.getCollection("config").find().first();
		got.remove("_id");
		assertThat("incorrect config", got, is(new Document("config", "config")
				.append("inupdate", false).append("schemaver", schemaver)));
	}

	private void assertIndexesCreated(final MongoDatabase db, final boolean oldTypeIndex) {
		/* checks that at least some of the mongo indexes are created. Since the underlying
		 * MongoWorkspaceDB method is already thoroughly tested elsewhere we just check a single
		 * index.
		 */
		final String ns = db.getName() + "." + COL_WS_VER;
		final Document currentIndex = new Document("v", 2)
				.append("key", new Document("tyname", 1)
						.append("tymaj", 1).append("tymin", 1)
						.append("ws", 1).append("id", 1).append("ver", -1))
				.append("name", "tyname_1_tymaj_1_tymin_1_ws_1_id_1_ver_-1")
				.append("ns", ns);
		final Set<Document> indexes = new HashSet<>();
		db.getCollection("workspaceObjVersions").listIndexes()
				.forEach((Consumer<Document>) indexes::add);
		assertThat("incorrect current index", indexes.contains(currentIndex), is(true));

		if (oldTypeIndex) {
			final Document typeIndex = new Document("v", 2)
					.append("key", new Document("type", 1).append("chksum", 1))
					.append("name", "type_1_chksum_1")
					.append("ns", ns);
			assertThat("incorrect old index", indexes.contains(typeIndex), is(true));
		}
	}

	private void runUpdateAndCheck(
			final MongoDatabase db,
			final boolean complete,
			final boolean overrideVersionCheck,
			final int priorSchemaVer,
			final int finalSchemaVer,
			final Document modquery,
			final long expectedObjCount,
			final List<String> expectedLogs)
			throws Exception {
		final List<ObjectId> prov = copySourceTo(db, priorSchemaVer);
		final MongoCollection<Document> col = db.getCollection(COL_WS_VER);
		col.updateMany(modquery, new Document("$unset",
				new Document("tyname", "").append("tymaj", "" ).append("tymin", "")));
		// check update worked
		for (final Document d: col.find(modquery)) {
			assertThat("tyname not removed\n" + d, d.containsKey("tyname"), is(false));
			assertThat("tymaj not removed\n" + d, d.containsKey("tymaj"), is(false));
			assertThat("tymin not removed\n" + d, d.containsKey("tymin"), is(false));
		}
		final List<String> logs = new LinkedList<>();
		assertThat("incorrect obj counts",
				new SchemaUpdater().update(db, s -> logs.add(s), complete, overrideVersionCheck),
				is(expectedObjCount));
		checkExpected(db, prov);
		assertThat("incorrect logs", logs, is(expectedLogs));
		assertIndexesCreated(db, true);
		checkConfig(db, finalSchemaVer);
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		if (MONGO != null) {
			System.out.println("destroying mongo temp files");
			MONGO.destroy(TestCommon.getDeleteTempFiles());
		}
	}

	private MongoDatabase getDB() {
		return MC.getDatabase("test_" + TestCommon.getMethodName(2));
	}

	@Test
	public void updateFailNullParams() throws Exception {
		failUpdate(null, s -> {}, new NullPointerException("db"));
		failUpdate(getDB(), null, new NullPointerException("logger"));
	}

	private void failUpdate(
			final MongoDatabase db,
			final Consumer<String> logger,
			final Exception expected) {
		try {
			new SchemaUpdater().update(db, logger, false, false);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}

	@Test
	public void updateFailTwoConfigDocs() throws Exception {
		final MongoDatabase db = getDB();
		final Document cfg = new Document("config", "config")
				.append("schemaver", 1).append("inupdate", false);
		db.getCollection("config").insertMany(Arrays.asList(new Document(cfg), new Document(cfg)));
		failUpdate(db, s -> {}, new SchemaUpdateException("Couldn't initialize database: " +
				"Found duplicate index keys in the database, aborting startup"));
	}

	@Test
	public void updateFailInUpdate() throws Exception {
		final MongoDatabase db = getDB();
		final Document cfg = new Document("config", "config")
				.append("schemaver", 1).append("inupdate", true);
		db.getCollection("config").insertOne(cfg);
		failUpdate(db, s -> {}, new SchemaUpdateException("Couldn't initialize database: " +
				"The database is in the middle of an update from v1 of the schema. " +
				"Aborting startup."));
	}

	@Test
	public void updateFailUpdateDoneEquals() throws Exception {
		updateFailUpdateDone(getDB(), MongoWorkspaceDB.SCHEMA_VERSION);
	}

	@Test
	public void updateFailUpdateDoneGreater() throws Exception {
		updateFailUpdateDone(getDB(), 50);
	}

	private void updateFailUpdateDone(final MongoDatabase db, final int configver) {
		final Document cfg = new Document("config", "config")
				.append("schemaver", configver).append("inupdate", false);
		db.getCollection("config").insertOne(cfg);
		failUpdate(db, s -> {}, new SchemaUpdateException(String.format(
				"Current DB schema version is %s, while the version to be updated to is 2. " +
				"Update is already complete.", configver)));
	}

	@Test
	public void updateNewDatabaseAndCheckIndexes() throws Exception {
		// run an update on a database that has never been used and therefore has no
		// config document
		// check v2 schema indexes are created as expected
		final List<String> logs = new LinkedList<>();
		final MongoDatabase db = getDB();
		assertThat("incorrect obj counts",
				new SchemaUpdater().update(db, s -> logs.add(s), false, false),
				is(0L));
		assertThat("incorrect logs", logs, is(Collections.emptyList()));
		assertIndexesCreated(db, false);
	}

	@Test
	public void updateEmptyDatabaseAndCheckIndexes() throws Exception {
		// run an update on a database that has never had any objects saved
		// check v2 schema indexes are created as expected and v1 type index is created
		final List<String> logs = new LinkedList<>();
		final MongoDatabase db = getDB();
		db.getCollection("config").insertOne(new Document("config", "config")
				.append("schemaver", 1).append("inupdate", false));
		assertThat("incorrect obj counts",
				new SchemaUpdater().update(db, s -> logs.add(s), false, false),
				is(0L));
		assertThat("incorrect logs", logs, is(Arrays.asList(
				"Finding types to be updated", "0 types found")));
		assertIndexesCreated(db, true);
	}

	@Test
	public void updateFull() throws Exception {
		runUpdateAndCheck(getDB(), false, false, 1, 1, new Document(), 10L, FULL_LOGS);
	}

	@Test
	public void updateFullWithComplete() throws Exception {
		runUpdateAndCheck(getDB(), true, false, 1, 2, new Document(), 10L, FULL_LOGS);
	}

	@Test
	public void updateFullWithOverrideVerCheck() throws Exception {
		runUpdateAndCheck(getDB(), false, true, 4, 4, new Document(), 10L, FULL_LOGS);
	}

	@Test
	public void updateFullWithCompleteAndOverrideVerCheck() throws Exception {
		runUpdateAndCheck(getDB(), true, true, 4, 4, new Document(), 10L, FULL_LOGS);
	}

	@Test
	public void updatePartialMissingWorkspace1() throws Exception {
		final Document query = new Document("ws", new Document("$ne", 1));
		final List<String> expectedLogs = Arrays.asList(
				"Finding types to be updated",
				"3 types found",
				"Processed type #1/3: Mod2.Type2-7.2, object count: 2, cumulative: 2",
				"Processed type #2/3: Mod2.Type2-8.2, object count: 1, cumulative: 3",
				"Processed type #3/3: Mod2.Type2-8.3, object count: 2, cumulative: 5"
				);
		runUpdateAndCheck(getDB(), false, false, 1, 1, query, 5L, expectedLogs);
	}

	@Test
	public void updatePartialMissingWorkspace2() throws Exception {
		final Document query = new Document("ws", new Document("$ne", 2));
		final List<String> expectedLogs = Arrays.asList(
				"Finding types to be updated",
				"3 types found",
				"Processed type #1/3: Mod1.Type1-7.2, object count: 3, cumulative: 3",
				"Processed type #2/3: Mod2.Type1-7.2, object count: 2, cumulative: 5",
				"Processed type #3/3: Mod2.Type2-8.3, object count: 2, cumulative: 7"
				);
		runUpdateAndCheck(getDB(), true, false, 1, 2, query, 7L, expectedLogs);
	}

	@Test
	public void updatePartialMissingRandomObjects() throws Exception {
		final Document query = new Document("chksum", new Document("$nin", Arrays.asList(
				MD5PRE + "03", MD5PRE + "04", MD5PRE + "08", MD5PRE + "10")));
		final List<String> expectedLogs = Arrays.asList(
				"Finding types to be updated",
				"4 types found",
				"Processed type #1/4: Mod1.Type1-7.2, object count: 2, cumulative: 2",
				"Processed type #2/4: Mod2.Type1-7.2, object count: 1, cumulative: 3",
				"Processed type #3/4: Mod2.Type2-7.2, object count: 2, cumulative: 5",
				"Processed type #4/4: Mod2.Type2-8.3, object count: 1, cumulative: 6"
				);
		runUpdateAndCheck(getDB(), false, false, 1, 1, query, 6L, expectedLogs);
	}

	@Test
	public void updateOnCompleteDatabase() throws Exception {
		final Document query = new Document("ws", new Document("$gt", 100));
		final List<String> expectedLogs = Arrays.asList(
				"Finding types to be updated",
				"0 types found"
				);
		runUpdateAndCheck(getDB(), false, false, 1, 1, query, 0L, expectedLogs);
	}

	@Test
	public void updateOnCompleteDatabaseWithOverrideVerCheck() throws Exception {
		final Document query = new Document("ws", new Document("$gt", 100));
		final List<String> expectedLogs = Arrays.asList(
				"Finding types to be updated",
				"0 types found"
				);
		runUpdateAndCheck(getDB(), false, true, 2, 2, query, 0L, expectedLogs);
	}
}
