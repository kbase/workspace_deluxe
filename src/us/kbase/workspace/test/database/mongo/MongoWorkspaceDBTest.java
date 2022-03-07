package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.inst;
import static us.kbase.common.test.TestCommon.list;
import static us.kbase.common.test.TestCommon.set;

import java.io.ByteArrayInputStream;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
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

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;

import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Provenance.ProvenanceAction;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.exceptions.FileCacheException;
import us.kbase.workspace.database.exceptions.FileCacheIOException;
import us.kbase.workspace.database.exceptions.NoObjectDataException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.ResolvedObjectIDNoVer;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.mongo.Fields;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

//TODO TEST start moving a bunch of the tests from Workspace test to here, and use mocks in workspace test.

public class MongoWorkspaceDBTest {
	
	private static final Optional<ByteArrayFileCache> OC = Optional.empty();

	private static MongoController MONGO;
	private static MongoDatabase MONGO_DB;
	
	private static ByteArrayFileCacheManager bafcm;
	static {
		 // won't actually make temp files in this test
		final TempFilesManager tfm = new TempFilesManager(
				Paths.get(TestCommon.getTempDir()).toFile());
		bafcm = new ByteArrayFileCacheManager(100000, 100000, tfm);
	}
	
	// has no hashCode(), so identity equality
	// shouldn't have hashCode() anyway, data could be huge
	private static ByteArrayFileCache getBAFC(final String json) {
		try {
			return bafcm.createBAFC(new ByteArrayInputStream(json.getBytes()), true, true);
		} catch (FileCacheException e) {
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
		
		@SuppressWarnings("resource")
		final MongoClient mc = new MongoClient("localhost:" + MONGO.getServerPort());
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
		
		final Provenance p = new Provenance(u, new Date(10000));
		p.setWorkspaceID(1L);
		p.addAction(new ProvenanceAction().withCaller("call"));
		
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
		
		
		final Map<ObjectIDResolvedWS, Map<SubsetSelection, WorkspaceObjectData.Builder>> res =
				mocks.mdb.getObjects(
						ImmutableMap.of(new ObjectIDResolvedWS(wsid, 1), set()),
						null,
						0,
						true,
						false,
						true);
		
		final Provenance pgot = res.get(new ObjectIDResolvedWS(wsid, 1))
				.get(SubsetSelection.EMPTY).build().getProvenance();
		
		//TODO TEST add equals methods to provenance classes & test & use here
		assertThat("incorrect user", pgot.getUser(), is(new WorkspaceUser("u")));
		assertThat("incorrect date", pgot.getDate(), is(new Date(10000)));
		assertThat("incorrect wsid", pgot.getWorkspaceID(), nullValue());
		assertThat("incorrect action count", pgot.getActions().size(), is(1));
		final ProvenanceAction pagot = pgot.getActions().get(0);
		
		assertThat("incorrect caller", pagot.getCaller(), is("call"));
	}
	
	@Test
	public void getObjectWithoutExternalIDsField() throws Exception {
		// check that older objects without external ID fields in the document don't cause NPEs
		final PartialMock mocks = new PartialMock(MONGO_DB);
		when(mocks.clockmock.instant()).thenReturn(Instant.now());
		
		final WorkspaceUser u = new WorkspaceUser("u");
		mocks.mdb.createWorkspace(u, "ws", false, null, new WorkspaceUserMetadata());
		
		final Provenance p = new Provenance(u, new Date(10000));
		p.setWorkspaceID(1L);
		
		final ResolvedWorkspaceID wsid = new ResolvedWorkspaceID(1, "ws", false, false);
		mocks.saveTestObject(wsid, u, p, "newobj", "Mod.Type-5.1",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L);
		
		MONGO_DB.getCollection("workspaceObjVersions").updateOne(
				new Document(),
				new Document("$unset", new Document(Fields.VER_EXT_IDS, "")));
		
		final Map<ObjectIDResolvedWS, Map<SubsetSelection, WorkspaceObjectData.Builder>> res =
				mocks.mdb.getObjects(
						ImmutableMap.of(new ObjectIDResolvedWS(wsid, 1), set()),
						null,
						0,
						true,
						false,
						true);
		
		final WorkspaceObjectData wod = res.get(new ObjectIDResolvedWS(wsid, 1))
				.get(SubsetSelection.EMPTY).build();
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
		final Provenance p = new Provenance(u);
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
		mocks.mdb.addDataToObjects(Collections.emptyList(), bafcMock);
	}

	private List<WorkspaceObjectData.Builder> addDataToObjectsSetup() {
		final Provenance p = new Provenance(new WorkspaceUser("user1"));
		final List<WorkspaceObjectData.Builder> objects = list(
				WorkspaceObjectData.getBuilder(
						new ObjectInformation(
								1, "one", "type", new Date(), 1, new WorkspaceUser("u"),
								new ResolvedWorkspaceID(1, "ws1", false, false),
								"c6b87e665ecd549c082db04f0979f551", 5, null),
						p),
				// same object accessed in a different way, which can happen
				WorkspaceObjectData.getBuilder(
						new ObjectInformation(
								1, "one", "type", new Date(), 1, new WorkspaceUser("u"),
								new ResolvedWorkspaceID(1, "ws1", false, false),
								"c6b87e665ecd549c082db04f0979f551", 5, null)
								.updateReferencePath(list(
										new Reference(2, 2, 2), new Reference(1, 1, 1))),
						p)
						.withSubsetSelection(new SubsetSelection(list("/bar"))),
				WorkspaceObjectData.getBuilder(
						new ObjectInformation(
								2, "two", "type", new Date(), 1, new WorkspaceUser("u"),
								new ResolvedWorkspaceID(1, "ws1", false, false),
								"a06ab5aadd3e058c7236bd6b681eefc7", 5, null),
						p)
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
		
		mocks.mdb.addDataToObjects(objects, bafcMock);
		
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
	public void addDataToObjectsFailGetBlobFileIOException() throws Exception {
		failAddDataToObjectsGetBlobException(
				new FileCacheIOException("foo"), new WorkspaceCommunicationException("foo"));
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
				new NoSuchBlobException("no MD5 or whatever"),
				new NoObjectDataException("No data present for object 1/2/1"));
		
		TestCommon.assertExceptionCorrect(
				got.getCause(), new NoSuchBlobException("no MD5 or whatever"));
	}
	
	@Test
	public void addDataToObjectsFailGetBlobRuntimeException() throws Exception {
		// test that data is cleaned up correctly with a runtime exception
		failAddDataToObjectsGetBlobException(
				new RuntimeException("aw dang"), new RuntimeException("aw dang"));
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
				new FileCacheIOException("foo"), new WorkspaceCommunicationException("foo"));
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
		 * cleaned up via the list of ByteArrayFileCaches.
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

	public Exception failAddDataToObjects(
			final PartialMock mocks,
			final List<WorkspaceObjectData.Builder> objects,
			final ByteArrayFileCacheManager bafcMock,
			final Exception expected) {
		try {
			mocks.mdb.addDataToObjects(objects, bafcMock);
			fail("expected exception");
			return null; // can't actually get here
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
			return got;
		}
	}

}
