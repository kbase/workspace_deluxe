package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.inst;
import static us.kbase.common.test.TestCommon.set;

import java.lang.reflect.Constructor;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

import us.kbase.common.service.UObject;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.ExtractedMetadata;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.ValidatedTypedObject;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Provenance.ProvenanceAction;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedObjectIDNoVer;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.mongo.BlobStore;
import us.kbase.workspace.database.mongo.Fields;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;

//TODO TEST start moving a bunch of the tests from Workspace test to here, and use mocks in workspace test.

public class MongoWorkspaceDBTest {

	private static MongoController MONGO;
	private static DB MONGO_DB;
	
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
		MONGO_DB = mc.getDB("test_" + MongoWorkspaceDBTest.class.getSimpleName());
		
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
	
	private class TestMocks {
		public final MongoWorkspaceDB mdb;
		@SuppressWarnings("unused")
		public final BlobStore bsmock;
		@SuppressWarnings("unused")
		public final TempFilesManager tfmmock;
		public final Clock clockmock;
		
		public TestMocks(
				final MongoWorkspaceDB mdb,
				final BlobStore bsmock,
				final TempFilesManager tfmmock,
				final Clock clockmock) {
			this.mdb = mdb;
			this.bsmock = bsmock;
			this.tfmmock = tfmmock;
			this.clockmock = clockmock;
		}
	}
	
	private TestMocks getDBInstance() throws Exception {
		final BlobStore bs = mock(BlobStore.class);
		final TempFilesManager tfm = mock(TempFilesManager.class);
		final Clock clock = mock(Clock.class);
		final Constructor<MongoWorkspaceDB> con =
				MongoWorkspaceDB.class.getDeclaredConstructor(
						DB.class, BlobStore.class, TempFilesManager.class, Clock.class);
		con.setAccessible(true);
		final MongoWorkspaceDB mdb = con.newInstance(MONGO_DB, bs, tfm, clock);
		return new TestMocks(mdb, bs, tfm, clock);
	}
	
	@Test
	public void getProvenanceWithNullFields() throws Exception {
		// check that older provenance records with missing fields don't throw NPEs.
		
		final TestMocks mocks = getDBInstance();
		when(mocks.clockmock.instant()).thenReturn(Instant.now());
		final MongoWorkspaceDB db = mocks.mdb;
		
		final WorkspaceUser u = new WorkspaceUser("u");
		db.createWorkspace(u, "ws", false, null, new WorkspaceUserMetadata());
		
		final Provenance p = new Provenance(u, new Date(10000));
		p.setWorkspaceID(1L);
		p.addAction(new ProvenanceAction().withCaller("call"));
		
		final ResolvedWorkspaceID wsid = new ResolvedWorkspaceID(1, "ws", false, false);
		saveTestObject(db, wsid, u, p, "newobj", "Mod.Type-5.1",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L);
		
		final DBObject v = MONGO_DB.getCollection("workspaceObjVersions").findOne();
		final ObjectId i = (ObjectId) v.get("provenance");
		MONGO_DB.getCollection("provenance").update(
				new BasicDBObject("_id", i),
				new BasicDBObject("$set", new BasicDBObject("actions.0.externalData", null)
						.append("wsid", null)
						.append("actions.0.subActions", null)
						.append("actions.0.custom", null)
						.append("actions.0.wsobjs", null)));
		
		
		final Map<ObjectIDResolvedWS, Map<SubsetSelection, WorkspaceObjectData>> res =
				db.getObjects(
						ImmutableMap.of(new ObjectIDResolvedWS(wsid, 1), set()),
						null,
						0,
						true,
						false,
						true);
		
		final Provenance pgot = res.get(new ObjectIDResolvedWS(wsid, 1))
				.get(SubsetSelection.EMPTY).getProvenance();
		
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
		final TestMocks mocks = getDBInstance();
		when(mocks.clockmock.instant()).thenReturn(Instant.now());
		final MongoWorkspaceDB db = mocks.mdb;
		
		final WorkspaceUser u = new WorkspaceUser("u");
		db.createWorkspace(u, "ws", false, null, new WorkspaceUserMetadata());
		
		final Provenance p = new Provenance(u, new Date(10000));
		p.setWorkspaceID(1L);
		
		final ResolvedWorkspaceID wsid = new ResolvedWorkspaceID(1, "ws", false, false);
		saveTestObject(db, wsid, u, p, "newobj", "Mod.Type-5.1",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L);
		
		MONGO_DB.getCollection("workspaceObjVersions").update(
				new BasicDBObject(),
				new BasicDBObject("$unset", new BasicDBObject(Fields.VER_EXT_IDS, "")));
		
		final Map<ObjectIDResolvedWS, Map<SubsetSelection, WorkspaceObjectData>> res =
				db.getObjects(
						ImmutableMap.of(new ObjectIDResolvedWS(wsid, 1), set()),
						null,
						0,
						true,
						false,
						true);
		
		final WorkspaceObjectData wod = res.get(new ObjectIDResolvedWS(wsid, 1))
				.get(SubsetSelection.EMPTY);
		assertThat("incorrect data", wod.getSerializedData(), nullValue());
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
		final TestMocks mocks = getDBInstance();
		final MongoWorkspaceDB db = mocks.mdb;
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
		
		// create workspaces
		final WorkspaceUser u = new WorkspaceUser("u");
		db.createWorkspace(u, "ws1", false, null, new WorkspaceUserMetadata());
		db.createWorkspace(u, "ws2", false, null, new WorkspaceUserMetadata());
		
		// save objects
		final Provenance p = new Provenance(u);
		final String type = "Mod.Type-5.1";
		
		final ResolvedWorkspaceID wsid = new ResolvedWorkspaceID(1, "ws", false, false);
		saveTestObject(db, wsid, u, p, "newobj", type, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 22L);
		saveTestObject(db, wsid, u, p, "newobj2", type, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab", 22L);
		
		final ResolvedWorkspaceID wsid2 = new ResolvedWorkspaceID(2, "ws2", false, false);
		saveTestObject(db, wsid2, u, p, "newobj3", type, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaac", 22L);
		
		final ObjectIDResolvedWS oid1 = new ObjectIDResolvedWS(wsid, "newobj");
		final ObjectIDResolvedWS oid2 = new ObjectIDResolvedWS(wsid, "newobj2");
		final ObjectIDResolvedWS oid3 = new ObjectIDResolvedWS(wsid2, "newobj3");
		final Set<ObjectIDResolvedWS> objectIDs = set(oid1, oid2, oid3);

		// don't care about the object times, they're set by the save code
		checkObjectDeletionState(false, set(), 3);
		
		deleteOrUndeleteAndCheck(db, objectIDs, true, wsid, wsid2,
				set(inst(20000), inst(40000)),
				set(inst(30000), inst(50000)));
		deleteOrUndeleteAndCheck(db, objectIDs, false, wsid, wsid2,
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
		final Map<Long, Instant> wsid2time = new HashMap<>();
		for (final Entry<ResolvedObjectIDNoVer, Instant> e: ret.entrySet()) {
			final long wsid = e.getKey().getWorkspaceIdentifier().getID();
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
		for (final DBObject obj: MONGO_DB.getCollection("workspaceObjects")
				.find(new BasicDBObject())) {
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
		for (final DBObject obj: MONGO_DB.getCollection("workspaces")
				.find(new BasicDBObject())) {
			final Instant date = ((Date)obj.get("moddate")).toInstant();
			gotTimes.add(date);
		}
		assertThat("incorrect workspace update times", new HashSet<>(gotTimes), is(wsTimes));
		// check that the number of workspaces is as expected without passing in a count
		// won't work if the workspaces have the same time
		assertThat("incorrect workspace count", gotTimes.size(), is(wsTimes.size()));
	}
	
	private Reference saveTestObject(
			final MongoWorkspaceDB db,
			final ResolvedWorkspaceID wsid,
			final WorkspaceUser u,
			final Provenance prov,
			final String name,
			final String absoluteTypeDef,
			final String md5,
			final long size)
			throws Exception {
		final ValidatedTypedObject vto = mock(ValidatedTypedObject.class);
		when(vto.getValidationTypeDefId()).thenReturn(
				AbsoluteTypeDefId.fromAbsoluteTypeString(absoluteTypeDef));
		when(vto.extractMetadata(16000)).thenReturn(new ExtractedMetadata(Collections.emptyMap()));
		when(vto.getMD5()).thenReturn(new MD5(md5));
		when(vto.getRelabeledSize()).thenReturn(size);

		final List<ObjectInformation> res = db.saveObjects(u, wsid,
				Arrays.asList(new WorkspaceSaveObject(
						new ObjectIDNoWSNoVer(name),
						new UObject(ImmutableMap.of("foo", "bar")),
						new TypeDefId("Mod.Type", "5.1"),
						null,
						prov,
						false)
						.resolve(
								vto,
								set(),
								Collections.emptyList(),
								Collections.emptyMap())
						));
		return res.get(0).getReferencePath().get(0);
	}
}
