package us.kbase.test.workspace.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static us.kbase.test.common.TestCommon.inst;
import static us.kbase.test.common.TestCommon.list;
import static us.kbase.test.common.TestCommon.opt;
import static us.kbase.test.common.TestCommon.set;
import static us.kbase.test.workspace.WorkspaceTestCommon.basicProv;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.IntStream;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import us.kbase.common.service.UObject;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.common.utils.sortjson.UTF8JsonSorterFactory;
import us.kbase.test.common.TestCommon;
import us.kbase.test.typedobj.DummyValidatedTypedObject;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedObjectID;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.NoObjectDataException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.ResolvedObjectIDNoVer;
import us.kbase.workspace.database.ResolvedSaveObject;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.UncheckedUserMetadata;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.DynamicConfig;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.DynamicConfig.DynamicConfigUpdate;
import us.kbase.workspace.database.MetadataUpdate;
import us.kbase.workspace.database.mongo.Fields;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;
import us.kbase.workspace.database.provenance.Provenance;
import us.kbase.workspace.database.provenance.ProvenanceAction;

//TODO TEST start moving a bunch of the tests from Workspace test to here, and use mocks in workspace test.

public class MongoWorkspaceDBTest {

	private static final Optional<ByteArrayFileCache> OC = Optional.empty();

	private static MongoController MONGO;
	private static MongoDatabase MONGO_DB;

	// has no hashCode(), so identity equality
	// shouldn't have hashCode() anyway, data could be huge
	private static ByteArrayFileCache getBAFC(final String json) {
		try {
			return new ByteArrayFileCacheManager()
					.createBAFC(new ByteArrayInputStream(json.getBytes()), true, true);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@BeforeClass
	public static void setup() throws Exception {
		TestCommon.stfuLoggers();
		MONGO = new MongoController(TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using Mongo temp dir " + MONGO.getTempDir());
		System.out.println("Started test mongo instance at localhost:" +
				MONGO.getServerPort());

		final MongoClient mc = MongoClients.create("mongodb://localhost:" + MONGO.getServerPort());
		MONGO_DB = mc.getDatabase("test_" + MongoWorkspaceDBTest.class.getSimpleName());

	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		if (MONGO != null) {
			System.out.println("destroying mongo temp files");
			MONGO.destroy(TestCommon.getDeleteTempFiles());
		}
	}

	@Before
	public void clearDB() throws Exception {
		TestCommon.destroyDB(MONGO_DB);
	}

	@Test
	public void getProvenanceWithNullFields() throws Exception {
		// check that older provenance records with missing fields don't throw NPEs.

		final PartialMock mocks = new PartialMock(MONGO_DB);
		when(mocks.clockmock.instant()).thenReturn(Instant.now());

		final WorkspaceUser u = new WorkspaceUser("u");
		mocks.mdb.createWorkspace(u, "ws", false, null, new WorkspaceUserMetadata());

		final Provenance p = Provenance.getBuilder(u, inst(10000))
				.withWorkspaceID(1L)
				.withAction(ProvenanceAction.getBuilder().withCaller("call").build())
				.build();

		final ResolvedWorkspaceID wsid = new ResolvedWorkspaceID(1, "ws", false, false);
		mocks.saveTestObject(wsid, u, p, "newobj", "Mod.Type-5.1",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L);

		final Document v = MONGO_DB.getCollection("workspaceObjVersions").find().first();
		final ObjectId i = (ObjectId) v.get("provenance");
		MONGO_DB.getCollection("provenance").updateOne(
				new Document("_id", i),
				new Document("$set", new Document("actions.0.externalData", null)
						.append("wsid", null)
						.append("actions.0.subActions", null)
						.append("actions.0.custom", null)
						.append("actions.0.wsobjs", null)));


		final Map<ObjectIDResolvedWS, WorkspaceObjectData.Builder> res = mocks.mdb.getObjects(
				set(new ObjectIDResolvedWS(wsid, 1)), true, false, true);

		final Provenance pgot = res.get(new ObjectIDResolvedWS(wsid, 1))
				.build().getProvenance();

		//TODO TEST add equals methods to provenance classes & test & use here
		assertThat("incorrect user", pgot.getUser(), is(new WorkspaceUser("u")));
		assertThat("incorrect date", pgot.getDate(), is(inst(10000)));
		assertThat("incorrect wsid", pgot.getWorkspaceID(), is(Optional.empty()));
		assertThat("incorrect action count", pgot.getActions().size(), is(1));
		final ProvenanceAction pagot = pgot.getActions().get(0);

		assertThat("incorrect caller", pagot.getCaller(), is(opt("call")));
	}

	@Test
	public void getObjectWithoutExternalIDsField() throws Exception {
		// check that older objects without external ID fields in the document don't cause NPEs
		final PartialMock mocks = new PartialMock(MONGO_DB);
		when(mocks.clockmock.instant()).thenReturn(Instant.now());

		final WorkspaceUser u = new WorkspaceUser("u");
		mocks.mdb.createWorkspace(u, "ws", false, null, new WorkspaceUserMetadata());

		final Provenance p = Provenance.getBuilder(u, inst(10000)).withWorkspaceID(1L).build();

		final ResolvedWorkspaceID wsid = new ResolvedWorkspaceID(1, "ws", false, false);
		mocks.saveTestObject(wsid, u, p, "newobj", "Mod.Type-5.1",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L);

		MONGO_DB.getCollection("workspaceObjVersions").updateOne(
				new Document(),
				new Document("$unset", new Document(Fields.VER_EXT_IDS, "")));

		final Map<ObjectIDResolvedWS, WorkspaceObjectData.Builder> res = mocks.mdb.getObjects(
				set(new ObjectIDResolvedWS(wsid, 1)), true, false, true);

		final WorkspaceObjectData wod = res.get(new ObjectIDResolvedWS(wsid, 1)).build();
		assertThat("incorrect data", wod.getSerializedData(), is(Optional.empty()));
		assertThat("incorrect ext ids", wod.getExtractedIds(), is(Collections.emptyMap()));
	}

	@Test
	public void deleteAndUndeleteObjects() throws Exception {
		// a basic happy path test for un/deleting objects
		// doesn't make much sense to split into a test for delete and a test for undelete since
		// the setup for undelete is basically the delete test
		// TODO TEST add deeper un/delete unit tests, including unhappy path. Scan code paths
		// Most tests are in WorkspaceTest.java which is an integration test

		// setup mocks
		final PartialMock mocks = new PartialMock(MONGO_DB);
		when(mocks.clockmock.instant()).thenReturn(
				inst(10000), // save #1 ws update
				inst(10000), // save #2 ws update
				inst(10000), // save #3 ws update
				inst(20000), // delete objects in one ws
				inst(30000), // update one ws date
				inst(40000), // delete objects in other ws
				inst(50000), // update other ws date
				inst(60000), // undelete objects in one ws
				inst(70000), // update one ws date
				inst(80000), // undelete objects in other ws
				inst(90000)  // update other ws date
				);

		final ResolvedWorkspaceID wsid = new ResolvedWorkspaceID(1, "ws", false, false);
		final ResolvedWorkspaceID wsid2 = new ResolvedWorkspaceID(2, "ws2", false, false);

		final Set<ObjectIDResolvedWS> objectIDs = setupTestDataForHideDelete(mocks, wsid, wsid2);

		// don't care about the object times, they're set by the save code
		checkObjectDeletionState(false, set(), 3);

		deleteOrUndeleteAndCheck(mocks.mdb, objectIDs, true, wsid, wsid2,
				set(inst(20000), inst(40000)),
				set(inst(30000), inst(50000)));
		deleteOrUndeleteAndCheck(mocks.mdb, objectIDs, false, wsid, wsid2,
				set(inst(60000), inst(80000)),
				set(inst(70000), inst(90000)));
	}

	private void deleteOrUndeleteAndCheck(
			final MongoWorkspaceDB db,
			final Set<ObjectIDResolvedWS> objs,
			final boolean delete,
			final ResolvedWorkspaceID wsid1,
			final ResolvedWorkspaceID wsid2,
			final Set<Instant> objectTimes,
			final Set<Instant> wsTimes)
			throws Exception {
		final Map<ResolvedObjectIDNoVer, Instant> ret = db.setObjectsDeleted(objs, delete);
		// because we're using sets and maps, there's no guarantee in what order the objects and
		// workspaces will be processed
		final Map<ResolvedWorkspaceID, Instant> wsid2time = new HashMap<>();
		for (final Entry<ResolvedObjectIDNoVer, Instant> e: ret.entrySet()) {
			final ResolvedWorkspaceID wsid = e.getKey().getWorkspaceIdentifier();
			if (wsid2time.containsKey(wsid) && !wsid2time.get(wsid).equals(e.getValue())) {
				fail("Got two different object delete times for wsid " + wsid);
			}
			wsid2time.put(wsid, e.getValue());
		}
		assertThat("incorrect times for object update", new HashSet<>(wsid2time.values()),
				is(objectTimes));
		assertThat("incorrect objects", ret.keySet(), is(set(
				new ResolvedObjectIDNoVer(wsid1, 1, "newobj", delete),
				new ResolvedObjectIDNoVer(wsid1, 2, "newobj2", delete),
				new ResolvedObjectIDNoVer(wsid2, 1, "newobj3", delete)
				)));
		checkObjectDeletionState(delete, objectTimes, objs.size());
		checkWorkspaceUpdateTimes(wsTimes);
	}

	private void checkObjectDeletionState(
			final boolean deleted,
			final Set<Instant> objectTimes,
			final int expectedCount) {
		// welp, it seems there's no way to get the deletion state via the MongoWorkspaceDB api
		int count = 0;
		final Map<Long, Instant> wsid2time = new HashMap<>();
		for (final Document obj: MONGO_DB.getCollection("workspaceObjects").find(new Document())) {
			assertThat(String.format("incorrect delete for object %s", obj),
					obj.get("del"), is(deleted));
			if (!objectTimes.isEmpty()) {
				final long wsid = (long) obj.get("ws");
				final Instant date = ((Date)obj.get("moddate")).toInstant();
				if (wsid2time.containsKey(wsid) && !wsid2time.get(wsid).equals(date)) {
					fail("Got two different mongo object delete times for wsid " + wsid);
				}
				wsid2time.put(wsid, date);
			}
			count++;
		}
		assertThat("incorrect times for mongo object update", new HashSet<>(wsid2time.values()),
				is(objectTimes));
		assertThat("incorrect object count", count, is(expectedCount));
	}

	private void checkWorkspaceUpdateTimes(final Set<Instant> wsTimes) {
		final List<Instant> gotTimes = new ArrayList<>();
		for (final Document obj: MONGO_DB.getCollection("workspaces").find(new Document())) {
			final Instant date = ((Date)obj.get("moddate")).toInstant();
			gotTimes.add(date);
		}
		assertThat("incorrect workspace update times", new HashSet<>(gotTimes), is(wsTimes));
		// check that the number of workspaces is as expected without passing in a count
		// won't work if the workspaces have the same time
		assertThat("incorrect workspace count", gotTimes.size(), is(wsTimes.size()));
	}

	private Set<ObjectIDResolvedWS> setupTestDataForHideDelete(
			final PartialMock mocks,
			final ResolvedWorkspaceID wsid,
			final ResolvedWorkspaceID wsid2)
			throws Exception {
		// create workspaces
		final WorkspaceUser u = new WorkspaceUser("u");
		mocks.mdb.createWorkspace(u, "ws1", false, null, new WorkspaceUserMetadata());
		mocks.mdb.createWorkspace(u, "ws2", false, null, new WorkspaceUserMetadata());

		// save objects
		final Provenance p = basicProv(u);
		final String type = "Mod.Type-5.1";

		mocks.saveTestObject(wsid, u, p, "newobj", type, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L);
		mocks.saveTestObject(wsid, u, p, "newobj2", type, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab", 22L);
		mocks.saveTestObject(
				wsid2, u, p, "newobj3", type, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaac", 22L);

		final ObjectIDResolvedWS oid1 = new ObjectIDResolvedWS(wsid, "newobj");
		final ObjectIDResolvedWS oid2 = new ObjectIDResolvedWS(wsid, "newobj2");
		final ObjectIDResolvedWS oid3 = new ObjectIDResolvedWS(wsid2, "newobj3");
		final Set<ObjectIDResolvedWS> objectIDs = set(oid1, oid2, oid3);
		return objectIDs;
	}

	@Test
	public void hideAndUnhideObjects() throws Exception {
		// a basic happy path test for un/hiding objects
		// doesn't make much sense to split into a test for hide and a test for unhide since
		// the setup for unhide is basically the hide test
		// TODO TEST add deeper un/hide unit tests, including unhappy path. Scan code paths
		// Most tests are in WorkspaceTest.java which is an integration test

		// setup mocks
		final PartialMock mocks = new PartialMock(MONGO_DB);
		when(mocks.clockmock.instant()).thenReturn(
				inst(10000), // save #1 ws update
				inst(10000), // save #2 ws update
				inst(10000), // save #3 ws update
				inst(20000), // hide objects in one ws
				inst(40000), // hide objects in other ws
				inst(60000), // unhide objects in one ws
				inst(80000)  // unhide objects in other ws
				);

		final ResolvedWorkspaceID wsid = new ResolvedWorkspaceID(1, "ws", false, false);
		final ResolvedWorkspaceID wsid2 = new ResolvedWorkspaceID(2, "ws2", false, false);

		final Set<ObjectIDResolvedWS> objectIDs = setupTestDataForHideDelete(mocks, wsid, wsid2);

		hideOrUnhideAndCheck(
				mocks.mdb, objectIDs, true, wsid, wsid2, set(inst(20000), inst(40000)));
		hideOrUnhideAndCheck(
				mocks.mdb, objectIDs, false, wsid, wsid2, set(inst(60000), inst(80000)));
	}

	private void hideOrUnhideAndCheck(
			final MongoWorkspaceDB db,
			final Set<ObjectIDResolvedWS> objs,
			final boolean hide,
			final ResolvedWorkspaceID wsid1,
			final ResolvedWorkspaceID wsid2,
			final Set<Instant> objectTimes)
			throws Exception {
		final Map<ResolvedObjectIDNoVer, Instant> ret = db.setObjectsHidden(objs, hide);
		// because we're using sets and maps, there's no guarantee in what order the objects and
		// workspaces will be processed
		final Map<ResolvedWorkspaceID, Instant> wsid2time = new HashMap<>();
		for (final Entry<ResolvedObjectIDNoVer, Instant> e: ret.entrySet()) {
			final ResolvedWorkspaceID wsid = e.getKey().getWorkspaceIdentifier();
			if (wsid2time.containsKey(wsid) && !wsid2time.get(wsid).equals(e.getValue())) {
				fail("Got two different object hide times for wsid " + wsid);
			}
			wsid2time.put(wsid, e.getValue());
		}
		assertThat("incorrect times for object update", new HashSet<>(wsid2time.values()),
				is(objectTimes));
		assertThat("incorrect objects", ret.keySet(), is(set(
				new ResolvedObjectIDNoVer(wsid1, 1, "newobj", false),
				new ResolvedObjectIDNoVer(wsid1, 2, "newobj2", false),
				new ResolvedObjectIDNoVer(wsid2, 1, "newobj3", false)
				)));
		checkObjectHideState(hide, objs.size());
	}

	private void checkObjectHideState(
			final boolean hidden,
			final int expectedCount) {
		// welp, it seems there's no way to get the deletion state via the MongoWorkspaceDB api
		int count = 0;
		for (final Document obj: MONGO_DB.getCollection("workspaceObjects").find(new Document())) {
			assertThat(String.format("incorrect hide for object %s", obj),
					obj.get("hide"), is(hidden));
			count++;
		}
		assertThat("incorrect object count", count, is(expectedCount));
	}

	@Test
	public void addDataToObjectsNoop() throws Exception {
		final PartialMock mocks = new PartialMock(MONGO_DB);
		final ByteArrayFileCacheManager bafcMock = mock(ByteArrayFileCacheManager.class);

		// nothing should happen
		mocks.mdb.addDataToObjects(Collections.emptyList(), bafcMock, 1);
	}

	private List<WorkspaceObjectData.Builder> addDataToObjectsSetup() {
		final Provenance p = basicProv(new WorkspaceUser("user1"));
		final ObjectInformation oi1 = ObjectInformation.getBuilder()
				.withObjectID(1)
				.withObjectName("one")
				.withType(new AbsoluteTypeDefId(new TypeDefName("a.b"), 1, 1))
				.withSavedDate(Instant.now())
				.withVersion(1)
				.withSavedBy(new WorkspaceUser("u"))
				.withWorkspace(new ResolvedWorkspaceID(1, "ws1", false, false))
				.withChecksum("c6b87e665ecd549c082db04f0979f551")
				.withSize(5)
				.build();
		final ObjectInformation oi2 = ObjectInformation.getBuilder()
				.withObjectID(2)
				.withObjectName("two")
				.withType(new AbsoluteTypeDefId(new TypeDefName("a.b"), 1, 1))
				.withSavedDate(Instant.now())
				.withVersion(1)
				.withSavedBy(new WorkspaceUser("u"))
				.withWorkspace(new ResolvedWorkspaceID(1, "ws1", false, false))
				.withChecksum("a06ab5aadd3e058c7236bd6b681eefc7")
				.withSize(5)
				.build();
		final List<WorkspaceObjectData.Builder> objects = list(
				WorkspaceObjectData.getBuilder(oi1, p),
				// same object accessed in a different way, which can happen
				WorkspaceObjectData.getBuilder(
						oi1.updateReferencePath(list(
								new Reference(2, 2, 2), new Reference(1, 1, 1))),
						p)
						.withSubsetSelection(new SubsetSelection(list("/bar"))),
				WorkspaceObjectData.getBuilder(oi2, p)
						.withSubsetSelection(new SubsetSelection(list("/foo")))
				);
		return objects;
	}

	@Test
	public void addDataToObjects() throws Exception {
		final PartialMock mocks = new PartialMock(MONGO_DB);
		final ByteArrayFileCacheManager bafcMock = mock(ByteArrayFileCacheManager.class);
		final List<WorkspaceObjectData.Builder> objects = addDataToObjectsSetup();

		// BAFC does not override equals (which makes sense) so we do all tests based on equality
		// that means the contents don't matter
		final ByteArrayFileCache bafc1 = getBAFC("{}");
		final ByteArrayFileCache bafc2 = getBAFC("{}");
		final ByteArrayFileCache bafc1sub = getBAFC("{}");
		final ByteArrayFileCache bafc2sub = getBAFC("{}");

		when(mocks.bsmock.getBlob(new MD5("c6b87e665ecd549c082db04f0979f551"), bafcMock))
				.thenReturn(bafc1, (ByteArrayFileCache) null); // cause a failure if called 2x
		when(mocks.bsmock.getBlob(new MD5("a06ab5aadd3e058c7236bd6b681eefc7"), bafcMock))
			.thenReturn(bafc2, (ByteArrayFileCache) null); // cause a failure if called 2x

		when(bafcMock.getSubdataExtraction(bafc1, new SubsetSelection(list("/bar"))))
				.thenReturn(bafc1sub, (ByteArrayFileCache) null); // cause a failure if called 2x
		when(bafcMock.getSubdataExtraction(bafc2, new SubsetSelection(list("/foo"))))
				.thenReturn(bafc2sub, (ByteArrayFileCache) null); // cause a failure if called 2x

		mocks.mdb.addDataToObjects(objects, bafcMock, 1);

		final ByteArrayFileCache got1 = objects.get(0).build().getSerializedData().get();
		final ByteArrayFileCache got2 = objects.get(1).build().getSerializedData().get();
		final ByteArrayFileCache got3 = objects.get(2).build().getSerializedData().get();

		assertThat("incorrect data", got1, is(bafc1));
		assertThat("data is destroyed", got1.isDestroyed(), is(false));
		assertThat("incorrect data", got2, is(bafc1sub));
		assertThat("data is destroyed", got2.isDestroyed(), is(false));
		assertThat("incorrect data", got3, is(bafc2sub));
		assertThat("data is destroyed", got3.isDestroyed(), is(false));
	}

	@Test
	public void addDataToObjectsFailBadArgs() throws Exception {
		final PartialMock mocks = new PartialMock(MONGO_DB);

		final ByteArrayFileCacheManager m = new ByteArrayFileCacheManager();
		final List<WorkspaceObjectData.Builder> objs = addDataToObjectsSetup();
		final List<WorkspaceObjectData.Builder> badobjs = new LinkedList<>(objs);
		badobjs.set(1, null);

		failAddDataToObjects(mocks, null, m, new NullPointerException("objects"));
		failAddDataToObjects(mocks, badobjs, m, new NullPointerException("null found in objects"));
		failAddDataToObjects(mocks, objs, null, new NullPointerException("dataManager"));
		failAddDataToObjects(mocks, objs, m, 0, new IllegalArgumentException(
				"backendScaling must be > 0"));
		failAddDataToObjects(mocks, objs, m, -100, new IllegalArgumentException(
				"backendScaling must be > 0"));
	}

	@Test
	public void addDataToObjectsFailGetBlobFileIOException() throws Exception {
		failAddDataToObjectsGetBlobException(
				new IOException("foo"), new WorkspaceCommunicationException("foo"));
	}

	@Test
	public void addDataToObjectsFailGetBlobCommException() throws Exception {
		failAddDataToObjectsGetBlobException(
				new BlobStoreCommunicationException("bar"),
				new WorkspaceCommunicationException("bar"));
	}

	@Test
	public void addDataToObjectsFailGetBlobAuthException() throws Exception {
		failAddDataToObjectsGetBlobException(
				new BlobStoreAuthorizationException("poopy ducks"),
				new WorkspaceCommunicationException(
						"Authorization error communicating with the backend storage system"));
	}

	@Test
	public void addDataToObjectsFailGetBlobNoBlobException() throws Exception {
		final Exception got = failAddDataToObjectsGetBlobException(
				new NoSuchBlobException(
						"no MD5 or whatever", new MD5("a06ab5aadd3e058c7236bd6b681eefc7")),
				new NoObjectDataException("No data present for object 1/2/1"));

		TestCommon.assertExceptionCorrect(got.getCause().getCause(), new NoSuchBlobException(
				"no MD5 or whatever", new MD5("a06ab5aadd3e058c7236bd6b681eefc7")));
	}

	@Test
	public void addDataToObjectsFailGetBlobRuntimeException() throws Exception {
		// test that data is cleaned up correctly with a runtime exception
		failAddDataToObjectsGetBlobException(
				new RuntimeException("aw dang"), new RuntimeException("Unexpected error"));
	}


	public Exception failAddDataToObjectsGetBlobException(
			final Exception blobErr,
			final Exception expected)
			throws Exception {
		final PartialMock mocks = new PartialMock(MONGO_DB);
		final ByteArrayFileCacheManager bafcMock = mock(ByteArrayFileCacheManager.class);
		final List<WorkspaceObjectData.Builder> objects = addDataToObjectsSetup();

		final ByteArrayFileCache bafc1 = getBAFC("{}");
		final ByteArrayFileCache bafc1sub = getBAFC("{}");

		when(mocks.bsmock.getBlob(new MD5("c6b87e665ecd549c082db04f0979f551"), bafcMock))
				.thenReturn(bafc1, (ByteArrayFileCache) null); // cause a failure if called 2x
		when(mocks.bsmock.getBlob(new MD5("a06ab5aadd3e058c7236bd6b681eefc7"), bafcMock))
				.thenThrow(blobErr);

		when(bafcMock.getSubdataExtraction(bafc1, new SubsetSelection(list("/bar"))))
				.thenReturn(bafc1sub, (ByteArrayFileCache) null); // cause a failure if called 2x

		final Exception got = failAddDataToObjects(mocks, objects, bafcMock, expected);

		for (final WorkspaceObjectData.Builder b: objects) {
			assertThat("expected no data", b.build().getSerializedData(), is(OC));
		}
		assertThat("expected destroyed", bafc1.isDestroyed(), is(true));
		assertThat("expected destroyed", bafc1sub.isDestroyed(), is(true));
		return got;
	}

	@Test
	public void addDataToObjectsFailSubsetFileIOException() throws Exception {
		addDataToObjectsFailSubsetFileIOException(
				new IOException("foo"), new WorkspaceCommunicationException("foo"));
	}

	@Test
	public void addDataToObjectsFailSubsetExtractionException() throws Exception {
		// test that data is removed
		addDataToObjectsFailSubsetFileIOException(
				new TypedObjectExtractionException("foo"),
				new TypedObjectExtractionException("foo"));
	}

	private Exception addDataToObjectsFailSubsetFileIOException(
			final Exception subsetErr,
			final Exception expected)
			throws Exception {
		final PartialMock mocks = new PartialMock(MONGO_DB);
		final ByteArrayFileCacheManager bafcMock = mock(ByteArrayFileCacheManager.class);
		final List<WorkspaceObjectData.Builder> objects = addDataToObjectsSetup();

		final ByteArrayFileCache bafc1 = getBAFC("{}");

		when(mocks.bsmock.getBlob(new MD5("c6b87e665ecd549c082db04f0979f551"), bafcMock))
				.thenReturn(bafc1, (ByteArrayFileCache) null); // cause a failure if called 2x

		when(bafcMock.getSubdataExtraction(bafc1, new SubsetSelection(list("/bar"))))
				.thenThrow(subsetErr);

		final Exception got = failAddDataToObjects(mocks, objects, bafcMock, expected);

		for (final WorkspaceObjectData.Builder b: objects) {
			assertThat("expected no data", b.build().getSerializedData(), is(OC));
		}
		assertThat("expected destroyed", bafc1.isDestroyed(), is(true));
		return got;
	}

	@Test
	public void addDataToObjectsFailSubsetExtractionExceptionWithInvisibleParent()
			throws Exception {
		/* the test above for failing subset extraction tests the case where object data
		 * is requested for the same object with and without a subset. However, if subsetting
		 * fails on an object with a subset request, and only a subset request, then the
		 * parent file is not visible in the list of WorkspaceObjectData and needs to be
		 * cleaned up via the list of Callables.
		 */
		final PartialMock mocks = new PartialMock(MONGO_DB);
		final ByteArrayFileCacheManager bafcMock = mock(ByteArrayFileCacheManager.class);
		final List<WorkspaceObjectData.Builder> objects = new LinkedList<>(
				addDataToObjectsSetup());
		objects.remove(0); // remove the object without a subset selection

		final ByteArrayFileCache bafc1 = getBAFC("{}");

		when(mocks.bsmock.getBlob(new MD5("c6b87e665ecd549c082db04f0979f551"), bafcMock))
				.thenReturn(bafc1, (ByteArrayFileCache) null); // cause a failure if called 2x

		when(bafcMock.getSubdataExtraction(bafc1, new SubsetSelection(list("/bar"))))
				.thenThrow(new TypedObjectExtractionException("dang"));

		failAddDataToObjects(mocks, objects, bafcMock, new TypedObjectExtractionException("dang"));

		for (final WorkspaceObjectData.Builder b: objects) {
			assertThat("expected no data", b.build().getSerializedData(), is(OC));
		}
		assertThat("expected destroyed", bafc1.isDestroyed(), is(true));
	}

	private Exception failAddDataToObjects(
			final PartialMock mocks,
			final List<WorkspaceObjectData.Builder> objects,
			final ByteArrayFileCacheManager bafcMock,
			final Exception expected) {
		return failAddDataToObjects(mocks, objects, bafcMock, 1, expected);
	}

	private Exception failAddDataToObjects(
			final PartialMock mocks,
			final List<WorkspaceObjectData.Builder> objects,
			final ByteArrayFileCacheManager bafcMock,
			final int backendScaling,
			final Exception expected) {
		try {
			mocks.mdb.addDataToObjects(objects, bafcMock, backendScaling);
			fail("expected exception");
			return null; // can't actually get here
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
			return got;
		}
	}

	@Test
	public void addDataToObjectsBackendScaling() throws Exception {
		/* The scaling factor is really just an implementation detail and doesn't affect the
		 * data returned from the method, so we really shouldn't care about testing it
		 * other than it doesn't cause failures. The only noticeable effect should be that
		 * a higher scaling factor should result in a faster return, which is not something
		 * that should be in automated tests as it's heavily dependent on the environment.
		 * Furthermore, in automated tests like this where network speed is not an issue
		 * little to no benefit will be seen from parallelizing the requests as IO should be
		 * local disk bound.
		 *
		 * Hence, this test just tries different scaling factors and prints out the speed
		 * for informational purposes. Any rigorous speed testing should take place outside the
		 * context of unit or even integration tests.
		 */

		// make a real MongoWSDB environment vs mocks
		final MongoWorkspaceDB db = new MongoWorkspaceDB(MONGO_DB, new GridFSBlobStore(MONGO_DB));
		final WorkspaceUser u = new WorkspaceUser("u1");
		final ResolvedWorkspaceID ws = new ResolvedWorkspaceID(1, "one", false, false);
		db.createWorkspace(u, "one", false, null, new WorkspaceUserMetadata());
		final List<ResolvedSaveObject> objs = saveObjects(db, ws, 100);

		final List<WorkspaceObjectData.Builder> wods = new LinkedList<>();
		int counter = 1;
		for (final ResolvedSaveObject o: objs) {
			wods.add(WorkspaceObjectData.getBuilder(
					ObjectInformation.getBuilder()
							.withObjectID(counter)
							.withObjectName("o" + counter)
							.withType(new AbsoluteTypeDefId(new TypeDefName("Mod.Meth"), 1, 0))
							.withSavedDate(Instant.now())
							.withVersion(1)
							.withSavedBy(u)
							.withWorkspace(ws)
							.withChecksum(o.getRep().getMD5())
							.withSize(o.getRep().getRelabeledSize())
							.build(),
					basicProv(u))
					.withSubsetSelection(new SubsetSelection(list("baz"))));
			counter++;
		}
		final ByteArrayFileCacheManager man = new ByteArrayFileCacheManager();
		final Map<Integer, Long> scaling2elapsed = new TreeMap<>();
		for (final int i: list(1, 2, 5, 10, 50, 100, 500)) {
			scaling2elapsed.put(i, timeAddDataToObjects(db, wods, man, i));
		}
		System.out.println(scaling2elapsed);
	}

	private long timeAddDataToObjects(
			final MongoWorkspaceDB db,
			final List<WorkspaceObjectData.Builder> wods,
			final ByteArrayFileCacheManager man,
			final int backendScaling)
			throws Exception {
		final long start = System.currentTimeMillis();
		db.addDataToObjects(wods, man, backendScaling);
		return System.currentTimeMillis() - start;
	}

	private List<ResolvedSaveObject> saveObjects(
			final MongoWorkspaceDB db,
			final ResolvedWorkspaceID ws,
			final int count)
			throws Exception {
		final List<ResolvedSaveObject> objects = new LinkedList<>();
		for (int i = 0; i < count; i++) {
			final WorkspaceSaveObject wso = new WorkspaceSaveObject(
					new ObjectIDNoWSNoVer("o" + i),
					new UObject(ImmutableMap.of("foo", "bar", "baz", "bat" + i)),
					new TypeDefId("Mod.Meth"),
					null,
					basicProv(new WorkspaceUser("u1")),
					false);
			final DummyValidatedTypedObject dummy = new DummyValidatedTypedObject(
					AbsoluteTypeDefId.fromAbsoluteTypeString("Mod.Meth-1.0"),
					wso.getData());
			dummy.calculateRelabeledSize();
			dummy.sort(new UTF8JsonSorterFactory(100000));
			objects.add(wso.resolve(ws, dummy, set(), list(), Collections.emptyMap()));

		}
		db.saveObjects(new WorkspaceUser("u1"), ws, objects);
		return objects;
	}

	@Test
	public void dynamicConfigSetAndGetNoop() throws Exception {
		final PartialMock mocks = new PartialMock(MONGO_DB);

		final DynamicConfigUpdate dcu = DynamicConfigUpdate.getBuilder().build();
		mocks.mdb.setConfig(dcu, false);

		final DynamicConfig expected = DynamicConfig.getBuilder().build();
		assertThat("incorrect config", mocks.mdb.getConfig(), is(expected));
	}

	@Test
	public void dynamicConfigSetAndGet() throws Exception {
		final PartialMock mocks = new PartialMock(MONGO_DB);

		final DynamicConfigUpdate dcu = DynamicConfigUpdate.getBuilder()
				.withBackendScaling(42).build();
		mocks.mdb.setConfig(dcu, false);

		final DynamicConfig expected = DynamicConfig.getBuilder().withBackendScaling(42).build();
		assertThat("incorrect config", mocks.mdb.getConfig(), is(expected));

		final DynamicConfigUpdate dcu2 = DynamicConfigUpdate.getBuilder()
				.withBackendScaling(8).build();
		mocks.mdb.setConfig(dcu2, false);
		assertThat("incorrect config", mocks.mdb.getConfig(), is(expected));

		mocks.mdb.setConfig(dcu2, true); // overwrite
		final DynamicConfig expected2 = DynamicConfig.getBuilder().withBackendScaling(8).build();
		assertThat("incorrect config", mocks.mdb.getConfig(), is(expected2));
	}

	@Test
	public void dynamicConfigSetAndGetWithIniticalOverwrite() throws Exception {
		final PartialMock mocks = new PartialMock(MONGO_DB);

		final DynamicConfigUpdate dcu = DynamicConfigUpdate.getBuilder()
				.withBackendScaling(42).build();
		mocks.mdb.setConfig(dcu, true);

		final DynamicConfig expected = DynamicConfig.getBuilder().withBackendScaling(42).build();
		assertThat("incorrect config", mocks.mdb.getConfig(), is(expected));
	}

	@Test
	public void dynamicConfigIllegalKeysAndRemove() throws Exception {
		final PartialMock mocks = new PartialMock(MONGO_DB);
		MONGO_DB.getCollection("dyncfg").insertMany(list(
				new Document("key", "illegal").append("value", "whatever"),
				new Document("key", "backend-file-retrieval-scaling").append("value", 89)
				));
		try {
			mocks.mdb.getConfig();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new CorruptWorkspaceDBException(
					"Illegal configuration values found in database"));
			TestCommon.assertExceptionCorrect(got.getCause(), new IllegalArgumentException(
					"Unexpected key in configuration map: illegal"));
		}

		// since currently the remove method does nothing
		final DynamicConfigUpdate update = mock(DynamicConfigUpdate.class);
		when(update.toSet()).thenReturn(Collections.emptyMap());
		// foo should be a noop
		when(update.toRemove()).thenReturn(set("foo", "illegal"));

		mocks.mdb.setConfig(update, false);

		final DynamicConfig expected = DynamicConfig.getBuilder().withBackendScaling(89).build();
		assertThat("incorrect config", mocks.mdb.getConfig(), is(expected));
	}

	@Test
	public void failDynamicConfigSet() throws Exception {
		final PartialMock mocks = new PartialMock(MONGO_DB);
		try {
			mocks.mdb.setConfig(null, false);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("config"));
		}
	}
	
	@Test
	public void setWorkspaceMeta() throws Exception {
		// Tests the immediately successful case only. See the mongo internal tests class
		// for tests for cases where there's at least one failure trying to update the data.
		final PartialMock mocks = new PartialMock(MONGO_DB);
		final WorkspaceUser user = new WorkspaceUser("a");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(1, "wsn", false, false);
		
		mocks.mdb.createWorkspace(user, "wsn", false, null, new WorkspaceUserMetadata());
		
		when(mocks.clockmock.instant()).thenReturn(inst(10000), inst(15000), null);
	
		// test against empty metadata
		final Optional<Instant> result = mocks.mdb.setWorkspaceMeta(
				rwsi,
				new MetadataUpdate(
					new WorkspaceUserMetadata(ImmutableMap.of(
							"foo", "bar", "baz", "bat", "to_remove", "a")),
					Arrays.asList("no_key_present")
				)
		);
		
		assertThat("incorrect time", result, is(opt(inst(10000))));
		
		final Map<String, String> m = mocks.mdb.getWorkspaceInformation(user, rwsi)
				.getUserMeta().getMetadata();
		assertThat("incorrect meta", m, is(ImmutableMap.of(
				"foo", "bar", "baz", "bat", "to_remove", "a")));
		
		// test against non-empty metadata
		final Optional<Instant> result2 = mocks.mdb.setWorkspaceMeta(
				rwsi,
				new MetadataUpdate(
					new WorkspaceUserMetadata(ImmutableMap.of("baz", "bing", "thingy", "thinger")),
					Arrays.asList("to_remove", "somecrap")
				)
		);
		
		assertThat("incorrect time", result2, is(opt(inst(15000))));
		
		// check that the whole info is ok at least once
		final WorkspaceInformation m2 = mocks.mdb.getWorkspaceInformation(user, rwsi);
		assertThat("incorrect workspace info", m2, is(WorkspaceInformation.getBuilder()
				.withID(1)
				.withMaximumObjectID(0)
				.withUserPermission(Permission.OWNER)
				.withModificationDate(inst(15000))
				.withName("wsn")
				.withOwner(new WorkspaceUser("a"))
				.withUserMetadata(new UncheckedUserMetadata(ImmutableMap.of(
						"foo", "bar", "baz", "bing", "thingy", "thinger")))
				.build()
		));
	}
	
	@Test
	public void setWorkspaceMetaRemoveOnly() throws Exception {
		final PartialMock mocks = new PartialMock(MONGO_DB);
		final WorkspaceUser user = new WorkspaceUser("a");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(1, "wsn", false, false);
		
		mocks.mdb.createWorkspace(user, "wsn", false, null, new WorkspaceUserMetadata(
				ImmutableMap.of("x", "y", "a", "b", "leave", "me alone")));
		
		when(mocks.clockmock.instant()).thenReturn(inst(10000), (Instant) null);
	
		final Optional<Instant> result = mocks.mdb.setWorkspaceMeta(
				rwsi, new MetadataUpdate(null, Arrays.asList("x", "a")));
		
		assertThat("incorrect time", result, is(opt(inst(10000))));
		
		final Map<String, String> m = mocks.mdb.getWorkspaceInformation(user, rwsi)
				.getUserMeta().getMetadata();
		assertThat("incorrect meta", m, is(ImmutableMap.of("leave", "me alone")));
	}
	
	@Test
	public void setWorkspaceMetaNoop() throws Exception {
		// Tests the case where if the DB was updated with the provided changes the actual
		// metadata wouldn't change
		final PartialMock mocks = new PartialMock(MONGO_DB);
		final WorkspaceUser user = new WorkspaceUser("a");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(1, "wsn", false, false);
		
		mocks.mdb.createWorkspace(user, "wsn", false, null, new WorkspaceUserMetadata(
				ImmutableMap.of("a", "b", "c", "d")));
		
		when(mocks.clockmock.instant()).thenReturn(inst(10000), (Instant) null);
	
		final Optional<Instant> result = mocks.mdb.setWorkspaceMeta(
				rwsi,
				new MetadataUpdate(
					new WorkspaceUserMetadata(ImmutableMap.of("a", "b", "x", "y")),
					Arrays.asList("x", "z")
				)
		);
		
		assertThat("incorrect time", result, is(Optional.empty()));
		
		final Map<String, String> m = mocks.mdb.getWorkspaceInformation(user, rwsi)
				.getUserMeta().getMetadata();
		assertThat("incorrect meta", m, is(ImmutableMap.of("a", "b", "c", "d")));
	}
	
	@Test
	public void setWorkspaceMetaFailBadInput() throws Exception {
		final PartialMock mocks = new PartialMock(MONGO_DB);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(1, "wsn", false, false);
		final MetadataUpdate mu = new MetadataUpdate(null, list("foo"));
		failSetWorkspaceMeta(mocks.mdb, null, mu, new NullPointerException("rwsi"));
		failSetWorkspaceMeta(mocks.mdb, rwsi, null,
				new IllegalArgumentException("No metadata changes provided"));
		failSetWorkspaceMeta(mocks.mdb, rwsi, new MetadataUpdate(null, null),
				new IllegalArgumentException("No metadata changes provided"));
	}
	
	private List<Object> setWorkspaceMetaBigMetaSetup() throws Exception {
		final PartialMock mocks = new PartialMock(MONGO_DB);
		final WorkspaceUser user = new WorkspaceUser("a");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(1, "wsn", false, false);
		final String l890 = TestCommon.LONG1001.substring(0, 890);
		
		final Map<String, String> meta = new HashMap<>();
		IntStream.range(0, 10).forEach(i -> meta.put("a" + i, l890));
		
		mocks.mdb.createWorkspace(user, "wsn", false, null, new WorkspaceUserMetadata(meta));
		
		final Map<String, String> meta2 = new HashMap<>();
		IntStream.range(0, 7).forEach(i -> meta2.put("b" + i, l890));
		
		return Arrays.asList(mocks, rwsi, l890, meta2);
	}
	
	@Test
	public void setWorkspaceMetaPassBigMeta() throws Exception {
		// also tests setting metadata without the remove parameter
		final List<Object> setup = setWorkspaceMetaBigMetaSetup();
		final PartialMock mocks = (PartialMock) setup.get(0);
		final ResolvedWorkspaceID rwsi = (ResolvedWorkspaceID) setup.get(1);
		final String l890 = (String) setup.get(2);
		
		
		@SuppressWarnings("unchecked")
		final Map<String, String> meta2 = (Map<String, String>) setup.get(3);
		meta2.put("b10", TestCommon.LONG1001.substring(0, 724));
		
		when(mocks.clockmock.instant()).thenReturn(inst(10000), (Instant) null);
		
		// test against empty metadata
		final Optional<Instant> result = mocks.mdb.setWorkspaceMeta(
				rwsi, new MetadataUpdate(new WorkspaceUserMetadata(meta2), null));
		
		assertThat("incorrect time", result, is(opt(inst(10000))));
		
		final Map<String, String> m2 = mocks.mdb.getWorkspaceInformation(
				new WorkspaceUser("a"), rwsi).getUserMeta().getMetadata();
		
		final Map<String, String> expected = new HashMap<>();
		for (final String key: list(
				"a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9",
				"b0", "b1", "b2", "b3", "b4", "b5", "b6")) {
			expected.put(key, l890);
		}
		expected.put("b10", l890.substring(0, 724));
		assertThat("incorrect meta", m2, is(expected));
	}
	
	@Test
	public void setWorkspaceMetaFailBigMeta() throws Exception {
		final List<Object> setup = setWorkspaceMetaBigMetaSetup();
		final PartialMock mocks = (PartialMock) setup.get(0);
		final ResolvedWorkspaceID rwsi = (ResolvedWorkspaceID) setup.get(1);
		
		@SuppressWarnings("unchecked")
		final Map<String, String> meta2 = (Map<String, String>) setup.get(3);
		meta2.put("b10", TestCommon.LONG1001.substring(0, 725));
		
		failSetWorkspaceMeta(
				mocks.mdb, rwsi, new MetadataUpdate(new WorkspaceUserMetadata(meta2), null),
				new IllegalArgumentException("Updated metadata exceeds allowed size of 16000B"));
	}
	
	private void failSetWorkspaceMeta(
			final MongoWorkspaceDB mdb,
			final ResolvedWorkspaceID rwsi,
			final MetadataUpdate meta,
			final Exception expected)
			throws Exception {
		try {
			mdb.setWorkspaceMeta(rwsi, meta);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void setAdminObjectMetaAndGetObjectInfo() throws Exception {
		// Tests the immediately successful case only. See the mongo internal tests class
		// for tests for cases where there's at least one failure trying to update the data.
		final PartialMock mocks = new PartialMock(MONGO_DB);
		final WorkspaceUser user = new WorkspaceUser("a");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(1, "wsn", false, false);
		when(mocks.clockmock.instant()).thenReturn(inst(10000));
		
		mocks.mdb.createWorkspace(user, "wsn", false, null, new WorkspaceUserMetadata());
		
		final Provenance p = Provenance.getBuilder(user, inst(10000)).withWorkspaceID(1L).build();

		mocks.saveTestObject(rwsi, user, p, "newobj", "Mod.Type-5.1",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L,
				new WorkspaceUserMetadata(ImmutableMap.of("thing", "stuff")));
		final ObjectIDResolvedWS oi1_1 = new ObjectIDResolvedWS(rwsi, 1, 1);
		mocks.saveTestObject(rwsi, user, p, "newobj", "Mod.Type-5.1",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L);
		final ObjectIDResolvedWS oi1_2 = new ObjectIDResolvedWS(rwsi, "newobj");
		mocks.saveTestObject(rwsi, user, p, "newobj2", "Mod.Type-5.1",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L);
		final ObjectIDResolvedWS oi2_1 = new ObjectIDResolvedWS(rwsi, 2);
		mocks.saveTestObject(rwsi, user, p, "newobj3", "Mod.Type-5.1",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L,
				new WorkspaceUserMetadata(ImmutableMap.of("whee", "whoo")));
		final ObjectIDResolvedWS oi3_1 = new ObjectIDResolvedWS(rwsi, "newobj3", 1);
		
		// 1st round - add metadata
		final Map<ObjectIDResolvedWS, ResolvedObjectID> res = mocks.mdb.setAdminObjectMeta(
				ImmutableMap.of(
						oi1_1, new MetadataUpdate(
								new WorkspaceUserMetadata(ImmutableMap.of("foo", "bar")), null),
						oi1_2, new MetadataUpdate(
								new WorkspaceUserMetadata(
										ImmutableMap.of("a", "b", "c", "d")), null),
						oi2_1, new MetadataUpdate(
								new WorkspaceUserMetadata(ImmutableMap.of("x", "y")), null)
		));
		
		assertThat("incorrect result", res, is(ImmutableMap.of(
				oi1_1, new ResolvedObjectID(rwsi, 1, 1, "newobj", false),
				oi1_2, new ResolvedObjectID(rwsi, 1, 2, "newobj", false),
				oi2_1, new ResolvedObjectID(rwsi, 2, 1, "newobj2", false)
		)));
		
		final Map<ObjectIDResolvedWS, ObjectInformation> infos = mocks.mdb.getObjectInformation(
				set(oi1_1, oi1_2, oi2_1, oi3_1), true, true, false, true);
		
		// test at least one object info
		assertThat("incorrect objectInfo", infos.get(oi1_1), is(ObjectInformation.getBuilder()
				.withUserMetadata(new UncheckedUserMetadata(ImmutableMap.of("thing", "stuff")))
				.withAdminUserMetadata(new UncheckedUserMetadata(ImmutableMap.of("foo", "bar")))
				.withChecksum("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
				.withObjectID(1)
				.withObjectName("newobj")
				.withSavedBy(new WorkspaceUser("a"))
				  // no check for this field, clock for save objects isn't mocked yet
				.withSavedDate(infos.get(oi1_1).getSavedDate())
				.withSize(22)
				.withType(new AbsoluteTypeDefId(new TypeDefName("Mod.Type"), 5, 1))
				.withVersion(1)
				.withWorkspace(rwsi)
				.build()
		));
		assertThat("incorrect admin meta", infos.get(oi1_2).getAdminUserMetaData(),
				is(opt(new UncheckedUserMetadata(ImmutableMap.of("a", "b", "c", "d")))));
		assertThat("incorrect meta", infos.get(oi1_2).getUserMetaData(),
				is(Optional.empty()));
		assertThat("incorrect admin meta", infos.get(oi2_1).getAdminUserMetaData(),
				is(opt(new UncheckedUserMetadata(ImmutableMap.of("x", "y")))));
		assertThat("incorrect meta", infos.get(oi2_1).getUserMetaData(),
				is(Optional.empty()));
		assertThat("incorrect admin meta", infos.get(oi3_1).getAdminUserMetaData(),
				is(Optional.empty()));
		assertThat("incorrect meta", infos.get(oi3_1).getUserMetaData(),
				is(opt(new UncheckedUserMetadata(ImmutableMap.of("whee", "whoo")))));
		
		// 2nd round - remove keys and test noops
		mocks.mdb.setAdminObjectMeta(ImmutableMap.of(
				oi1_1, new MetadataUpdate(  // noop
						new WorkspaceUserMetadata(ImmutableMap.of("foo", "bar")),
						Arrays.asList("no_key_here")),
				oi1_2, new MetadataUpdate(  // remove
						null,
						Arrays.asList("c", "fakekey")),
				oi2_1, new MetadataUpdate(  // add
						new WorkspaceUserMetadata(ImmutableMap.of("z", "a")), null)
		));
		
		final Map<ObjectIDResolvedWS, ObjectInformation> infos2 = mocks.mdb.getObjectInformation(
				set(oi1_1, oi1_2, oi2_1, oi3_1), true, true, false, true);
		
		assertThat("incorrect admin meta", infos2.get(oi1_1).getAdminUserMetaData(),
				is(opt(new UncheckedUserMetadata(ImmutableMap.of("foo", "bar")))));
		assertThat("incorrect meta", infos2.get(oi1_1).getUserMetaData(),
				is(opt(new UncheckedUserMetadata(ImmutableMap.of("thing", "stuff")))));
		assertThat("incorrect admin meta", infos2.get(oi1_2).getAdminUserMetaData(),
				is(opt(new UncheckedUserMetadata(ImmutableMap.of("a", "b")))));
		assertThat("incorrect meta", infos2.get(oi1_2).getUserMetaData(),
				is(Optional.empty()));
		assertThat("incorrect admin meta", infos2.get(oi2_1).getAdminUserMetaData(),
				is(opt(new UncheckedUserMetadata(ImmutableMap.of("x", "y", "z", "a")))));
		assertThat("incorrect meta", infos2.get(oi2_1).getUserMetaData(),
				is(Optional.empty()));
		assertThat("incorrect admin meta", infos2.get(oi3_1).getAdminUserMetaData(),
				is(Optional.empty()));
		assertThat("incorrect meta", infos2.get(oi3_1).getUserMetaData(),
				is(opt(new UncheckedUserMetadata(ImmutableMap.of("whee", "whoo")))));
	}
	
	@Test
	public void setAdminObjectMetaFailBadInput() throws Exception {
		final PartialMock mocks = new PartialMock(MONGO_DB);
		final WorkspaceUser user = new WorkspaceUser("a");
		when(mocks.clockmock.instant()).thenReturn(inst(10000));
		
		mocks.mdb.createWorkspace(user, "wsn", false, null, new WorkspaceUserMetadata());
		mocks.mdb.createWorkspace(user, "wsn2", false, null, new WorkspaceUserMetadata());
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(2, "wsn2", false, false);
		
		final Provenance p = Provenance.getBuilder(user, inst(10000)).withWorkspaceID(1L).build();
		final MetadataUpdate m = new MetadataUpdate(null, Arrays.asList("a"));
		
		mocks.saveTestObject(rwsi, user, p, "newobj", "Mod.Type-5.1",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L);
		mocks.saveTestObject(rwsi, user, p, "newobj2", "Mod.Type-5.1",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L);
		mocks.saveTestObject(rwsi, user, p, "newobj2", "Mod.Type-5.1",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L);
		mocks.saveTestObject(rwsi, user, p, "delobj", "Mod.Type-5.1",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L);
		mocks.mdb.setObjectsDeleted(set(new ObjectIDResolvedWS(rwsi, 3)), true);
		
		setAdminObjectMetaFail(mocks, null, new NullPointerException("update"));
		final HashMap<ObjectIDResolvedWS, MetadataUpdate> nullMap = new HashMap<>();
		nullMap.put(null, m);
		setAdminObjectMetaFail(mocks, nullMap, new NullPointerException(
				"null object ID in update"));
		nullMap.clear();
		nullMap.put(new ObjectIDResolvedWS(rwsi, 2), null);
		setAdminObjectMetaFail(mocks, nullMap, new IllegalArgumentException(
				"Error setting metadata on workspace wsn2 id 2, object 2, latest version: "
				+ "No metadata changes provided"));
		nullMap.clear();
		nullMap.put(new ObjectIDResolvedWS(rwsi, 2, 1), null);
		setAdminObjectMetaFail(mocks, nullMap, new IllegalArgumentException(
				"Error setting metadata on workspace wsn2 id 2, object 2, version 1: "
				+ "No metadata changes provided"));
		final ObjectIDResolvedWS roi1 = new ObjectIDResolvedWS(rwsi, 4);
		setAdminObjectMetaFail(mocks, ImmutableMap.of(roi1, m), new NoSuchObjectException(
				"No object with id 4 exists in workspace 2 (name wsn2)", roi1));
		final ObjectIDResolvedWS roi2 = new ObjectIDResolvedWS(rwsi, 2, 3);
		setAdminObjectMetaFail(mocks, ImmutableMap.of(roi2, m), new NoSuchObjectException(
				"No object with id 2 (name newobj2) and version 3 exists in workspace 2 "
				+ "(name wsn2)",
				roi2));
		final ObjectIDResolvedWS roi3 = new ObjectIDResolvedWS(rwsi, 3);
		setAdminObjectMetaFail(mocks, ImmutableMap.of(roi3, m), new NoSuchObjectException(
				"Object 3 (name delobj) in workspace 2 (name wsn2) has been deleted", roi3));
	}
	
	@Test
	public void setAdminObjectMetaFailBigMeta() throws Exception {
		final PartialMock mocks = new PartialMock(MONGO_DB);
		when(mocks.clockmock.instant()).thenReturn(inst(10000));
		final WorkspaceUser user = new WorkspaceUser("a");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(1, "wsn", false, false);
		final String l890 = TestCommon.LONG1001.substring(0, 890);
		
		final Map<String, String> meta = new HashMap<>();
		IntStream.range(0, 10).forEach(i -> meta.put("a" + i, l890));
		IntStream.range(0, 7).forEach(i -> meta.put("b" + i, l890));
		meta.put("b10", TestCommon.LONG1001.substring(0, 724));
		
		mocks.mdb.createWorkspace(user, "wsn", false, null, new WorkspaceUserMetadata(null));
		
		final Provenance p = Provenance.getBuilder(user, inst(10000)).withWorkspaceID(1L).build();
		
		mocks.saveTestObject(rwsi, user, p, "newobj", "Mod.Type-5.1",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L);
		final ObjectIDResolvedWS oid = new ObjectIDResolvedWS(rwsi, 1);
		
		mocks.mdb.setAdminObjectMeta(ImmutableMap.of(
				oid, new MetadataUpdate(new WorkspaceUserMetadata(meta), null)));
		
		setAdminObjectMetaFail(
				mocks,
				ImmutableMap.of(oid, new MetadataUpdate(new WorkspaceUserMetadata(
						ImmutableMap.of("b10", TestCommon.LONG1001.substring(0, 725))),
						null)),
				new IllegalArgumentException(
						"Error setting metadata on workspace wsn id 1, object 1, latest version: "
						+ "Updated metadata exceeds allowed size of 16000B")
		);
	}
	
	private void setAdminObjectMetaFail(
			final PartialMock mocks,
			final Map<ObjectIDResolvedWS, MetadataUpdate> input,
			final Exception expected)
			throws Exception {
		try {
			mocks.mdb.setAdminObjectMeta(input);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
}
