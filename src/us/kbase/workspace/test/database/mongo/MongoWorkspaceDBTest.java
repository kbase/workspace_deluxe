package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.set;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
	
	@Test
	public void getProvenanceWithNullFields() throws Exception {
		// check that older provenance records with missing fields don't throw NPEs.
		
		final BlobStore bs = mock(BlobStore.class);
		final TempFilesManager tfm = mock(TempFilesManager.class);
		
		final MongoWorkspaceDB db = new MongoWorkspaceDB(MONGO_DB, bs, tfm);
		
		db.createWorkspace(new WorkspaceUser("u"), "ws", false, null, new WorkspaceUserMetadata());
		
		final Provenance p = new Provenance(new WorkspaceUser("u"), new Date(10000));
		p.setWorkspaceID(1L);
		p.addAction(new ProvenanceAction().withCaller("call"));
		
		final ResolvedWorkspaceID wsid = new ResolvedWorkspaceID(1, "ws", false, false);
		saveTestObject(db, wsid, "u", p, "newobj", "Mod.Type-5.1",
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
		final BlobStore bs = mock(BlobStore.class);
		final TempFilesManager tfm = mock(TempFilesManager.class);
		
		final MongoWorkspaceDB db = new MongoWorkspaceDB(MONGO_DB, bs, tfm);
		
		db.createWorkspace(new WorkspaceUser("u"), "ws", false, null, new WorkspaceUserMetadata());
		
		final Provenance p = new Provenance(new WorkspaceUser("u"), new Date(10000));
		p.setWorkspaceID(1L);
		
		final ResolvedWorkspaceID wsid = new ResolvedWorkspaceID(1, "ws", false, false);
		saveTestObject(db, wsid, "u", p, "newobj", "Mod.Type-5.1",
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
	
	private Reference saveTestObject(
			final MongoWorkspaceDB db,
			final ResolvedWorkspaceID wsid,
			final String user,
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

		final List<ObjectInformation> res = db.saveObjects(new WorkspaceUser(user), wsid,
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
