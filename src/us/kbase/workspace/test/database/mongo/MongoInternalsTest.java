package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jongo.Jongo;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.service.UObject;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.LocalTypeProvider;
import us.kbase.typedobj.core.ObjectPaths;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.RemappedId;
import us.kbase.typedobj.test.DummyTypedObjectValidationReport;
import us.kbase.workspace.database.DefaultReferenceParser;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedSaveObject;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.exceptions.WorkspaceDBInitializationException;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.IDName;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.ObjectSavePackage;
import us.kbase.workspace.database.mongo.ResolvedMongoWSID;
import us.kbase.workspace.database.mongo.TypeData;
import us.kbase.workspace.test.WorkspaceTestCommon;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

public class MongoInternalsTest {
	
	private static Jongo jdb;
	private static MongoWorkspaceDB mwdb;
	private static Workspace ws;
	private static Types types;
	private static MongoController mongo;
	private static MongoClient mongoClient;
	
	private static final IdReferenceHandlerSetFactory fac =
			new IdReferenceHandlerSetFactory(100);
	
	public static final TypeDefId SAFE_TYPE =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);

	@BeforeClass
	public static void setUpClass() throws Exception {
		mongo = new MongoController(WorkspaceTestCommon.getMongoExe(),
				Paths.get(WorkspaceTestCommon.getTempDir()),
				WorkspaceTestCommon.useWiredTigerEngine());
		System.out.println("Using mongo temp dir " +
				mongo.getTempDir());
		WorkspaceTestCommon.stfuLoggers();
		String mongohost = "localhost:" + mongo.getServerPort();
		mongoClient = new MongoClient(mongohost);
		final DB db = mongoClient.getDB("MongoInternalsTest");
		String typedb = "MongoInternalsTest_types";
		WorkspaceTestCommon.destroyWSandTypeDBs(db, typedb);
		jdb = new Jongo(db);
		
		TempFilesManager tfm = new TempFilesManager(
				new File(WorkspaceTestCommon.getTempDir()));
		final TypeDefinitionDB typeDefDB = new TypeDefinitionDB(
				new MongoTypeStorage(GetMongoDB.getDB(mongohost, typedb)));
		TypedObjectValidator val = new TypedObjectValidator(
				new LocalTypeProvider(typeDefDB));
		mwdb = new MongoWorkspaceDB(db, new GridFSBlobStore(db), tfm);
		ws = new Workspace(mwdb,
				new ResourceUsageConfigurationBuilder().build(),
				new DefaultReferenceParser(), val);
		assertTrue("GridFS backend setup failed",
				ws.getBackendType().equals("GridFS"));

		//make a general spec that tests that don't worry about typechecking can use
		WorkspaceUser foo = new WorkspaceUser("foo");
		//simple spec
		types = new Types(typeDefDB);
		types.requestModuleRegistration(foo, "SomeModule");
		types.resolveModuleRegistration("SomeModule", true);
		types.compileNewTypeSpec(foo, 
				"module SomeModule {/* @optional thing */ typedef structure {string thing;} AType;};",
				Arrays.asList("AType"), null, null, false, null);
		types.releaseTypes(foo, "SomeModule");
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (mongo != null) {
			mongo.destroy(WorkspaceTestCommon.deleteTempFiles());
		}
	}
	
	@Test
	public void startUpAndCheckConfigDoc() throws Exception {
		final DB db = mongoClient.getDB("startUpAndCheckConfigDoc");
		TempFilesManager tfm = new TempFilesManager(
				new File(WorkspaceTestCommon.getTempDir()));
		new MongoWorkspaceDB(db, new GridFSBlobStore(db), tfm);
		
		DBCursor c = db.getCollection("config").find();
		assertThat("Only one config doc", c.size(), is(1));
		DBObject cd = c.next();
		assertThat("correct config key & value", (String)cd.get("config"),
				is("config"));
		assertThat("not in update", (Boolean)cd.get("inupdate"), is(false));
		assertThat("schema v1", (Integer)cd.get("schemaver"), is(1));
		
		//check startup works with the config object in place
		MongoWorkspaceDB m = new MongoWorkspaceDB(
				db,  new GridFSBlobStore(db), tfm);
		WorkspaceInformation ws = m.createWorkspace(
				new WorkspaceUser("foo"), "bar", false, null,
				new WorkspaceUserMetadata());
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
				(DBObject) new BasicDBObject(m), new BasicDBObject(m)));
		
		failMongoWSStart(db, new CorruptWorkspaceDBException(
				"Found duplicate index keys in the database, " +
						"aborting startup"));
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
		TempFilesManager tfm = new TempFilesManager(
				new File(WorkspaceTestCommon.getTempDir()));
		try {
			new MongoWorkspaceDB(db, new GridFSBlobStore(db), tfm);
			fail("started mongo with bad config");
		} catch (Exception e) {
			assertExceptionCorrect(e, exp);
		}
	}
	
	private void assertExceptionCorrect(Exception got, Exception expected) {
		assertThat("correct exception", got.getLocalizedMessage(),
				is(expected.getLocalizedMessage()));
		assertThat("correct exception type", got, is(expected.getClass()));
	}
	
	@Test
	public void raceConditionRevertObjectId() throws Exception {
		//TODO more tests like this to test internals that can't be tested otherwise
		
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("ws");
		WorkspaceUser user = new WorkspaceUser("u");
		long wsid = ws.createWorkspace(user, wsi.getName(), false, null, null)
				.getId();
		
		final Map<String, Object> data = new HashMap<String, Object>();
		Map<String, String> meta = new HashMap<String, String>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		meta.put("metastuff", "meta");
		Provenance p = new Provenance(new WorkspaceUser("kbasetest2"));
		setWsidOnProvenance(wsid, p);
		TypeDefId t = new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);
		AbsoluteTypeDefId at = new AbsoluteTypeDefId(
				new TypeDefName("SomeModule", "AType"), 0, 1);
		
		WorkspaceSaveObject wso = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("testobj"), new UObject(data), t,
				new WorkspaceUserMetadata(meta), p, false);
		ResolvedSaveObject rso = wso.resolve(
				new DummyTypedObjectValidationReport(at, wso.getData()),
				new HashSet<Reference>(), new LinkedList<Reference>(),
				new HashMap<IdReferenceType, Set<RemappedId>>());
		ResolvedMongoWSID rwsi = (ResolvedMongoWSID) mwdb.resolveWorkspace(
				wsi);
		mwdb.saveObjects(user, rwsi, Arrays.asList(rso));
		
		IDnPackage inp = startSaveObject(rwsi, rso, 3, at);
		ObjectSavePackage pkg = inp.pkg;
		IDName r = inp.idname;
		
		Field name = pkg.getClass().getDeclaredField("name");
		name.setAccessible(true);
		Field idname = r.getClass().getDeclaredField("name");
		idname.setAccessible(true);
		name.set(pkg, idname.get(r));
		
		Method saveProvenance = mwdb.getClass()
				.getDeclaredMethod("saveProvenance", List.class);
		saveProvenance.setAccessible(true);
		saveProvenance.invoke(mwdb, Arrays.asList(pkg));
		
		Method saveObjectVersion = mwdb.getClass()
				.getDeclaredMethod("saveObjectVersion", WorkspaceUser.class,
						ResolvedMongoWSID.class, long.class, ObjectSavePackage.class);
		saveObjectVersion.setAccessible(true);
		Field idid = r.getClass().getDeclaredField("id");
		idid.setAccessible(true);
		ObjectInformation md = (ObjectInformation) saveObjectVersion.invoke(
				mwdb, new WorkspaceUser("u"), rwsi, idid.get(r), pkg);
		assertThat("objectid is revised to existing object", md.getObjectId(), is(1L));
	}

	private void setWsidOnProvenance(long wsid, Provenance p)
			throws NoSuchMethodException, IllegalAccessException,
			InvocationTargetException {
		Method setWsid = p.getClass().getDeclaredMethod("setWorkspaceID",
				Long.class);
		setWsid.setAccessible(true);
		setWsid.invoke(p, new Long(wsid));
	}
	
	@Test
	public void setGetRaceCondition() throws Exception {
		String objname = "testobj";
		String objname2 = "testobj2";
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("setGetRace");
		WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("setGetRace2");
		WorkspaceIdentifier wsi3 = new WorkspaceIdentifier("setGetRace3");
		
		WorkspaceUser user = new WorkspaceUser("u");
		long wsid = ws.createWorkspace(user, wsi.getName(), false, null, null)
				.getId();
		
		final Map<String, Object> data = new HashMap<String, Object>();
		Provenance p = new Provenance(new WorkspaceUser("kbasetest2"));
		setWsidOnProvenance(wsid, p);
		TypeDefId t = new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);
		AbsoluteTypeDefId at = new AbsoluteTypeDefId(
				new TypeDefName("SomeModule", "AType"), 0, 1);
		
		ResolvedSaveObject rso = createResolvedWSObj(objname, data, p, t, at);
		ResolvedSaveObject rso2 = createResolvedWSObj(objname2, data, p, t, at);
		ResolvedMongoWSID rwsi = (ResolvedMongoWSID) mwdb.resolveWorkspace(
				wsi);
		
		startSaveObject(rwsi, rso, 1, at);
		mwdb.saveObjects(user, rwsi, Arrays.asList(rso2));

		//possible race condition 1 - no version provided, version not yet
		//saved, version count not yet incremented
		ObjectIDResolvedWS oidrw = new ObjectIDResolvedWS(rwsi,
				rso.getObjectIdentifier().getName());
		Set<ObjectIDResolvedWS> oidset = new HashSet<ObjectIDResolvedWS>(
				Arrays.asList(oidrw));
		failGetObjectsNoSuchObjectExcp(oidset,
				String.format("No object with name %s exists in workspace %s",
						objname, rwsi.getID()));
		
		try {
			mwdb.copyObject(user, oidrw, new ObjectIDResolvedWS(rwsi, "foo"));
			fail("copied object with no version");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getMessage(),
					is(String.format("No object with name %s exists in workspace %s",
							objname, rwsi.getID())));
		}
		
		mwdb.cloneWorkspace(user, rwsi, wsi2.getName(), false, null,
				new WorkspaceUserMetadata());
		ResolvedMongoWSID rwsi2 = (ResolvedMongoWSID) mwdb.resolveWorkspace(
				wsi2);
		ObjectIDResolvedWS oidrw2_1 = new ObjectIDResolvedWS(rwsi2,
				rso.getObjectIdentifier().getName());
		failGetObjectsNoSuchObjectExcp(new HashSet<ObjectIDResolvedWS>(
					Arrays.asList(oidrw2_1)),
					String.format("No object with name %s exists in workspace %s",
							objname, rwsi2.getID()));

		ObjectIDResolvedWS oidrw2_2 = new ObjectIDResolvedWS(rwsi2,
				rso2.getObjectIdentifier().getName());
		
		long id = mwdb.getObjectInformation(new HashSet<ObjectIDResolvedWS>(
				Arrays.asList(oidrw2_2)), false, true, false, true).get(oidrw2_2).getObjectId();
		assertThat("correct object id", id, is(1L));

		
		//possible race condition 2 - as 1, but version provided
		ObjectIDResolvedWS oidrwWithVer = new ObjectIDResolvedWS(rwsi,
				rso.getObjectIdentifier().getName(), 1);
		Set<ObjectIDResolvedWS> oidsetver = new HashSet<ObjectIDResolvedWS>();
		oidsetver.add(oidrwWithVer);
		failGetObjectsNoSuchObjectExcp(oidsetver,
				String.format("No object with name %s exists in workspace %s",
						objname, rwsi.getID()));
		
		try {
			mwdb.copyObject(user, oidrwWithVer, new ObjectIDResolvedWS(rwsi, "foo"));
			fail("copied object with no version");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getMessage(),
					is(String.format("No object with name %s exists in workspace %s",
							objname, rwsi.getID())));
		}
		
		
		//race condition 3 - as 1, but with version incremented 
		//set the version to 1 in the workspace object. This state can
		//occur if a get happens between the increment and the save of the
		//version, although it's really rare
		jdb.getCollection("workspaceObjects")
			.update("{id: 1, ws: #}", rwsi.getID())
			.with("{$inc: {numver: 1}}");
		
		mwdb.cloneWorkspace(user, rwsi, wsi3.getName(), false, null,
				new WorkspaceUserMetadata());
		ResolvedMongoWSID rwsi3 = (ResolvedMongoWSID) mwdb.resolveWorkspace(
				wsi3);
		ObjectIDResolvedWS oidrw3_1 = new ObjectIDResolvedWS(rwsi3,
				rso.getObjectIdentifier().getName());
		failGetObjectsNoSuchObjectExcp(new HashSet<ObjectIDResolvedWS>(
				Arrays.asList(oidrw3_1)),
				String.format("No object with name %s exists in workspace %s",
						objname, rwsi3.getID()));

		ObjectIDResolvedWS oidrw3_2 = new ObjectIDResolvedWS(rwsi3,
				rso2.getObjectIdentifier().getName());
		id = mwdb.getObjectInformation(new HashSet<ObjectIDResolvedWS>(
				Arrays.asList(oidrw3_2)), false, true, false, true).get(oidrw3_2).getObjectId();
		assertThat("correct object id", id, is(1L));
		
		try {
			mwdb.copyObject(user, oidrw, new ObjectIDResolvedWS(rwsi, "foo"));
			fail("copied object with no version");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getMessage(),
					is(String.format("No object with name %s exists in workspace %s",
							objname, rwsi.getID())));
		}
		
		try {
			mwdb.copyObject(user, oidrwWithVer, new ObjectIDResolvedWS(rwsi, "foo"));
			fail("copied object with no version");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getMessage(),
					is(String.format("No object with id 1 (name %s) and version 1 exists in workspace %s (name %s)",
							objname, rwsi.getID(), rwsi.getName())));
		}
		
		failGetObjectsNoSuchObjectExcp(oidsetver,
				String.format("No object with id 1 (name %s) and version 1 exists in workspace %s (name %s)",
						objname, rwsi.getID(), rwsi.getName()));
		
		assertNull("can't get object mid save", mwdb.getObjectInformation(
				oidsetver, false, false, false, false).get(0));
		try {
			mwdb.getObjectInformation(oidsetver, false, true, false, true);
			fail("got object with no version");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getMessage(),
					is(String.format("No object with id 1 (name %s) and version 1 exists in workspace %s (name %s)",
							objname, rwsi.getID(), rwsi.getName())));
		}
	}

	private void failGetObjectsNoSuchObjectExcp(
			Set<ObjectIDResolvedWS> oidsetver, String msg)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException, TypedObjectExtractionException {
		final Map<ObjectIDResolvedWS, Set<ObjectPaths>> paths =
				new HashMap<ObjectIDResolvedWS, Set<ObjectPaths>>();
		for (final ObjectIDResolvedWS o: oidsetver) {
			paths.put(o, null);
		}
		try {
			mwdb.getObjects(paths, false, true);
			fail("operated on object with no version");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getMessage(),
					is(msg));
		}
	}

	private ResolvedSaveObject createResolvedWSObj(String objname,
			final Map<String, Object> data, Provenance p, TypeDefId t,
			AbsoluteTypeDefId at) throws Exception {
		WorkspaceSaveObject wso = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer(objname),
				new UObject(data), t, null, p, false);
		ResolvedSaveObject rso = wso.resolve(
				new DummyTypedObjectValidationReport(at, wso.getData()),
				new HashSet<Reference>(), new LinkedList<Reference>(),
				new HashMap<IdReferenceType, Set<RemappedId>>());
		return rso;
	}
	
	class IDnPackage {
		IDName idname;
		ObjectSavePackage pkg;
		
		public IDnPackage(IDName idname, ObjectSavePackage pkg) {
			super();
			this.idname = idname;
			this.pkg = pkg;
		}
	}
	
	private IDnPackage startSaveObject(
			final ResolvedMongoWSID rwsi,
			final ResolvedSaveObject rso,
			final int objid,
			final AbsoluteTypeDefId abstype) throws Exception {
		
		Constructor<ObjectSavePackage> objConst =
				ObjectSavePackage.class.getDeclaredConstructor();
		objConst.setAccessible(true);
		ObjectSavePackage pkg = objConst.newInstance();
		Field wo = pkg.getClass().getDeclaredField("wo");
		wo.setAccessible(true);
		wo.set(pkg, rso);
		Field td = pkg.getClass().getDeclaredField("td");
		td.setAccessible(true);
		td.set(pkg, new TypeData(rso.getRep().createJsonWritable(), abstype));
		
		Method incrementWorkspaceCounter = mwdb.getClass()
				.getDeclaredMethod("incrementWorkspaceCounter", ResolvedMongoWSID.class,
						int.class);
		incrementWorkspaceCounter.setAccessible(true);
		incrementWorkspaceCounter.invoke(mwdb, rwsi, 1);
		
		Method saveWorkspaceObject = mwdb.getClass()
				.getDeclaredMethod("saveWorkspaceObject", ResolvedMongoWSID.class,
						long.class, String.class);
		saveWorkspaceObject.setAccessible(true);
		String name = rso.getObjectIdentifier().getName();
		IDName idn = (IDName) saveWorkspaceObject.invoke(mwdb, rwsi, objid, name);
		return new IDnPackage(idn, pkg);
	}
	
	@Test
	public void refCounting() throws Exception {
		final String refcntspec =
				"module RefCount {" +
					"/* @id ws */" +
					"typedef string reference;" +
					"/* @optional ref */" + 
					"typedef structure {" +
						"reference ref;" +
					"} RefType;" +
				"};";
		
		String mod = "RefCount";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		types.requestModuleRegistration(userfoo, mod);
		types.resolveModuleRegistration(mod, true);
		types.compileNewTypeSpec(userfoo, refcntspec, Arrays.asList("RefType"), null, null, false, null);
		TypeDefId refcounttype = new TypeDefId(new TypeDefName(mod, "RefType"), 0, 1);
		
		WorkspaceIdentifier wspace = new WorkspaceIdentifier("refcount");
		long wsid = ws.createWorkspace(userfoo, wspace.getName(), false, null, null).getId();
		Provenance emptyprov = new Provenance(userfoo);
		Map<String, Object> data1 = new HashMap<String, Object>();
		data1.put("foo", 3);
		
		for (int i = 1; i < 5; i++) {
			for (int j = 0; j < 4; j++) {
				ws.saveObjects(userfoo, wspace, Arrays.asList(
						new WorkspaceSaveObject(new ObjectIDNoWSNoVer("obj" + i),
								new UObject(data1), SAFE_TYPE, null, emptyprov, false)),
								fac);
			}
		}
		// now we've got a 4x4 set of objects
		
		int[][] expected = new int[5][5];
		
		for (int i = 0; i < 16; i++) {
			int obj = (int) (Math.random() * 4.0) + 1;
			int ver = (int) (Math.random() * 4.0) + 1;
			expected[obj][ver]++;
			if (i % 2 == 0) {
				ws.saveObjects(userfoo, wspace, Arrays.asList(
						new WorkspaceSaveObject(new UObject(withRef(data1, wsid, "obj" + obj, ver)),
						refcounttype, null, emptyprov, false)), fac);
			} else {
				ws.saveObjects(userfoo, wspace, Arrays.asList(
						new WorkspaceSaveObject(new UObject(withRef(data1, wsid, obj, ver)),
						refcounttype, null, emptyprov, false)), fac);
			}
		}
		checkRefCounts(wsid, expected, 1);
		WorkspaceIdentifier wspace2 = new WorkspaceIdentifier("refcount2");
		ws.createWorkspace(userfoo, wspace2.getName(), false, null, null).getId();
		
		for (int i = 1; i <= 16; i++) {
			ws.copyObject(userfoo, new ObjectIdentifier(wspace, "auto" + (i + 4)),
					new ObjectIdentifier(wspace2, "obj" + i));
		}
		checkRefCounts(wsid, expected, 2);
		
		WorkspaceIdentifier wspace3 = new WorkspaceIdentifier("refcount3");
		ws.cloneWorkspace(userfoo, wspace2, wspace3.getName(), false, null, null);
		checkRefCounts(wsid, expected, 3);
		
		for (int i = 1; i <= 16; i++) {
			ws.revertObject(userfoo, new ObjectIdentifier(wspace3, "obj" + i));
		}
		checkRefCounts(wsid, expected, 4);
		
	}

	private void checkRefCounts(long wsid, int[][] expected, int factor) {
		for (int i = 1; i < 5; i++) {
			@SuppressWarnings("unchecked")
			Map<String, Object> obj = jdb.getCollection("workspaceObjects")
					.findOne("{ws: #, id: #}", wsid, i).as(Map.class);
			@SuppressWarnings("unchecked")
			List<Integer> refcnts = (List<Integer>) obj.get("refcnt");
			for (int j = 0; j < 4; j++) {
				assertThat("correct ref count", refcnts.get(j), is(expected[i][j + 1] * factor));
			}
		}
	}
	
	private Map<String, Object> withRef(Map<String, Object> map, long wsid,
			int name, int ver) {
		return withRef(map, wsid, "" + name, ver);
	}
	
	private Map<String, Object> withRef(Map<String, Object> map, long wsid,
			String name, int ver) {
		map.put("ref", wsid + "/" + name + "/" + ver);
		return map;
	}
	
	@Test
	public void testCopyAndRevertTags() throws Exception {
		testCopyRevert(false, "copyrevert");
		testCopyRevert(true, "copyreverthide");
	}

	private void testCopyRevert(Boolean hide,
			String wsprefix) throws Exception {
		
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		WorkspaceIdentifier copyrev = new WorkspaceIdentifier(wsprefix);
		long wsid = ws.createWorkspace(userfoo, copyrev.getName(), false, null, null).getId();
		
		Map<String, Object> data = new HashMap<String, Object>();
		ws.saveObjects(userfoo, copyrev, Arrays.asList(
				new WorkspaceSaveObject(new UObject(data), SAFE_TYPE, null, new Provenance(userfoo), hide)),
				fac);
		ws.saveObjects(userfoo, copyrev, Arrays.asList(
				new WorkspaceSaveObject(new UObject(data), SAFE_TYPE, null, new Provenance(userfoo), hide)),
				fac);
		ws.saveObjects(userfoo, copyrev, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer(2), new UObject(data), SAFE_TYPE,
						null, new Provenance(userfoo), hide)), fac);
		ws.saveObjects(userfoo, copyrev, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer(2), new UObject(data), SAFE_TYPE,
						null, new Provenance(userfoo), hide)), fac);
		ws.copyObject(userfoo, new ObjectIdentifier(copyrev, 2, 2),
				new ObjectIdentifier(copyrev, "auto3"));
		ws.copyObject(userfoo, new ObjectIdentifier(copyrev, 2),
				new ObjectIdentifier(copyrev, "auto4"));
		ws.revertObject(userfoo, new ObjectIdentifier(copyrev, "auto4", 2));
		
		checkRefCntInit(wsid, 3, 1);
		checkRefCntInit(wsid, 4, 4);
		
		@SuppressWarnings("rawtypes")
		List<Map> objverlist = iterToList(jdb.getCollection("workspaceObjVersions")
				.find("{ws: #, id: #}", wsid, 3).as(Map.class));
		assertThat("Only copied version once", objverlist.size(), is(1));
		@SuppressWarnings("unchecked")
		Map<String, Object> objver = (Map<String, Object>) objverlist.get(0);
		assertThat("correct copy location", (String) objver.get("copied"), is(wsid + "/2/2"));
		
		@SuppressWarnings("rawtypes")
		List<Map> objverlist2 = iterToList(jdb.getCollection("workspaceObjVersions")
				.find("{ws: #, id: #}", wsid, 4).as(Map.class));
		assertThat("Correct version count", 4, is(objverlist2.size()));
		Map<Integer, String> cpexpec = new HashMap<Integer, String>();
		Map<Integer, Integer> revexpec = new HashMap<Integer, Integer>();
		cpexpec.put(1, wsid + "/2/1");
		cpexpec.put(2, wsid + "/2/2");
		cpexpec.put(3, wsid + "/2/3");
		cpexpec.put(4, wsid + "/2/2");
		revexpec.put(1, null);
		revexpec.put(2, null);
		revexpec.put(3, null);
		revexpec.put(4, 2);
		for (@SuppressWarnings("rawtypes") Map m: objverlist2) {
			@SuppressWarnings("unchecked")
			Map<String, Object> m2 = (Map<String, Object>) m;
			int ver = (Integer) m2.get("ver");
			assertThat("copy pointer ok", (String) m2.get("copied"), is(cpexpec.get(ver)));
			assertThat("revert pointer ok", (Integer) m2.get("revert"), is(revexpec.get(ver)));
			
		}
		
		long wsid2 = ws.cloneWorkspace(userfoo, copyrev, wsprefix + "2", false, null, null).getId();
		
		checkRefCntInit(wsid2, 3, 1);
		checkRefCntInit(wsid2, 4, 4);
		
		@SuppressWarnings("rawtypes")
		List<Map> objverlist3 = iterToList(jdb.getCollection("workspaceObjVersions")
				.find("{ws: #, id: #}", wsid2, 3).as(Map.class));
		assertThat("Only copied version once", objverlist.size(), is(1));
		@SuppressWarnings("unchecked")
		Map<String, Object> objver3 = (Map<String, Object>) objverlist3.get(0);
		assertThat("correct copy location", (String) objver3.get("copied"), is(wsid + "/3/1"));
		
		@SuppressWarnings("rawtypes")
		List<Map> objverlist4 = iterToList(jdb.getCollection("workspaceObjVersions")
				.find("{ws: #, id: #}", wsid2, 4).as(Map.class));
		assertThat("Correct version count", 4, is(objverlist4.size()));
		Map<Integer, String> cpexpec2 = new HashMap<Integer, String>();
		Map<Integer, Integer> revexpec2 = new HashMap<Integer, Integer>();
		cpexpec2.put(1, wsid + "/4/1");
		cpexpec2.put(2, wsid + "/4/2");
		cpexpec2.put(3, wsid + "/4/3");
		cpexpec2.put(4, wsid + "/4/4");
		revexpec2.put(1, null);
		revexpec2.put(2, null);
		revexpec2.put(3, null);
		revexpec2.put(4, null);
		for (@SuppressWarnings("rawtypes") Map m: objverlist4) {
			@SuppressWarnings("unchecked")
			Map<String, Object> m2 = (Map<String, Object>) m;
			int ver = (Integer) m2.get("ver");
			assertThat("copy pointer ok", (String) m2.get("copied"), is(cpexpec2.get(ver)));
			assertThat("revert pointer ok", (Integer) m2.get("revert"), is(revexpec2.get(ver)));
		}
	}

	private void checkRefCntInit(long wsid, int objid, int vers) {
		@SuppressWarnings("rawtypes")
		List<Map> objlist = iterToList(jdb.getCollection("workspaceObjects")
				.find("{ws: #, id: #}", wsid, objid).as(Map.class));
		assertThat("Only one object per id", objlist.size(), is(1));
		@SuppressWarnings("unchecked")
		List<Integer> refcnts = (List<Integer>) objlist.get(0).get("refcnt");
		List<Integer> expected = new LinkedList<Integer>();
		for (int i = 0; i < vers; i++) {
			expected.add(0);
		}
		assertThat("refcnt array init correctly", refcnts, is(expected));
	}
	
	private <T> List<T> iterToList(Iterable<T> iter) {
		List<T> list = new LinkedList<T>();
		for (T item: iter) {
			list.add(item);
		}
		return list;
	}
	
	@Test
	public void dates() throws Exception {
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		WorkspaceIdentifier dates = new WorkspaceIdentifier("dates");
		long wsid = ws.createWorkspace(userfoo, dates.getName(), false, null, null).getId();
		
		Map<String, Object> data = new HashMap<String, Object>();
		ws.saveObjects(userfoo, dates, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("orig"), new UObject(data),
						SAFE_TYPE, null, new Provenance(userfoo), false)),
						fac);
		Date orig = getDate(wsid, 1);
		ws.copyObject(userfoo, new ObjectIdentifier(dates, "orig"),
				new ObjectIdentifier(dates, "copy"));
		Date copy = getDate(wsid, 2);
		ObjectIdentifier copyobj = new ObjectIdentifier(dates, "copy");
		ws.revertObject(userfoo, copyobj);
		Date revert = getDate(wsid, 2);
		ws.renameObject(userfoo, copyobj, "foobar");
		Date rename = getDate(wsid, 2);
		ObjectIdentifier foobar = new ObjectIdentifier(dates, "foobar");
		ws.setObjectsDeleted(userfoo, Arrays.asList(foobar), true);
		Date delete = getDate(wsid, 2);
		ws.setObjectsDeleted(userfoo, Arrays.asList(foobar), false);
		Date undelete = getDate(wsid, 2);
		
		assertTrue("copy date after orig", orig.before(copy));
		assertTrue("rev date after copy", copy.before(revert));
		assertTrue("ren date after rev", revert.before(rename));
		assertTrue("del date after ren", rename.before(delete));
		assertTrue("undel date after del", delete.before(undelete));
		assertDateisRecent(orig);
		assertDateisRecent(copy);
		assertDateisRecent(revert);
		assertDateisRecent(rename);
		assertDateisRecent(delete);
		assertDateisRecent(undelete);
	}

	private Date getDate(long wsid, int id) {
		@SuppressWarnings("rawtypes")
		Map obj = jdb.getCollection("workspaceObjects")
				.findOne("{ws: #, id: #}", wsid, id).as(Map.class);
		return (Date) obj.get("moddate");
	}

	private void assertDateisRecent(Date orig) {
		Date now = new Date();
		int onemin = 1000 * 60;
		assertTrue("date is recent", now.getTime() - orig.getTime() < onemin);
	}
}