package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jongo.Jongo;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.common.service.UObject;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
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
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.mongo.IDName;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.ObjectSavePackage;
import us.kbase.workspace.database.mongo.ResolvedMongoWSID;
import us.kbase.workspace.database.mongo.TypeData;
import us.kbase.workspace.kbase.Util;
import us.kbase.workspace.test.WorkspaceTestCommon;

import com.mongodb.DB;
import com.mongodb.MongoClient;

public class MongoInternalsTest {
	
	private static Jongo jdb;
	private static MongoWorkspaceDB mwdb;
	private static Workspace ws;
	private static MongoController mongo;
	
	private static final IdReferenceHandlerSetFactory fac =
			new IdReferenceHandlerSetFactory(100);
	
	public static final TypeDefId SAFE_TYPE =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);

	@BeforeClass
	public static void setUpClass() throws Exception {
		mongo = new MongoController(WorkspaceTestCommon.getMongoExe(),
				Paths.get(WorkspaceTestCommon.getTempDir()));
		System.out.println("Using mongo temp dir " +
				mongo.getTempDir());
		WorkspaceTestCommon.stfuLoggers();
		String mongohost = "localhost:" + mongo.getServerPort();
		MongoClient mongoClient = new MongoClient(mongohost);
		final DB db = mongoClient.getDB("MongoInternalsTest");
		WorkspaceTestCommon.initializeGridFSWorkspaceDB(
				db, "MongoInternalsTest_types");
		jdb = new Jongo(db);
		final String kidlpath = new Util().getKIDLpath();
		
		TempFilesManager tfm = new TempFilesManager(
				new File(WorkspaceTestCommon.getTempDir()));
		mwdb = new MongoWorkspaceDB(mongohost, "MongoInternalsTest", "foo",
				"foo", "foo", kidlpath, null, tfm);
		ws = new Workspace(mwdb,
				new ResourceUsageConfigurationBuilder().build(),
				new DefaultReferenceParser());
		assertTrue("GridFS backend setup failed",
				ws.getBackendType().equals("GridFS"));

		//make a general spec that tests that don't worry about typechecking can use
		WorkspaceUser foo = new WorkspaceUser("foo");
		//simple spec
		ws.requestModuleRegistration(foo, "SomeModule");
		ws.resolveModuleRegistration("SomeModule", true);
		ws.compileNewTypeSpec(foo, 
				"module SomeModule {/* @optional thing */ typedef structure {string thing;} AType;};",
				Arrays.asList("AType"), null, null, false, null);
		ws.releaseTypes(foo, "SomeModule");
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (mongo != null) {
			mongo.destroy(WorkspaceTestCommon.getDeleteTempFiles());
		}
	}
	
	@Test
	public void raceConditionRevertObjectId() throws Exception {
		//TODO more tests like this to test internals that can't be tested otherwise
		
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("ws");
		WorkspaceUser user = new WorkspaceUser("u");
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		
		final Map<String, Object> data = new HashMap<String, Object>();
		Map<String, String> meta = new HashMap<String, String>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		meta.put("metastuff", "meta");
		Provenance p = new Provenance(new WorkspaceUser("kbasetest2"));
		TypeDefId t = new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);
		AbsoluteTypeDefId at = new AbsoluteTypeDefId(
				new TypeDefName("SomeModule", "AType"), 0, 1);
		
		WorkspaceSaveObject wso = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("testobj"),
				new UObject(data), t, meta, p, false);
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
	
	@Test
	public void setGetRaceCondition() throws Exception {
		String objname = "testobj";
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("setGetRace");
		WorkspaceUser user = new WorkspaceUser("u");
		long wsid = ws.createWorkspace(user, wsi.getName(), false, null, null).getId();
		
		final Map<String, Object> data = new HashMap<String, Object>();
		Provenance p = new Provenance(new WorkspaceUser("kbasetest2"));
		TypeDefId t = new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);
		AbsoluteTypeDefId at = new AbsoluteTypeDefId(
				new TypeDefName("SomeModule", "AType"), 0, 1);
		
		WorkspaceSaveObject wso = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer(objname),
				new UObject(data), t, null, p, false);
		ResolvedSaveObject rso = wso.resolve(
				new DummyTypedObjectValidationReport(at, wso.getData()),
				new HashSet<Reference>(), new LinkedList<Reference>(),
				new HashMap<IdReferenceType, Set<RemappedId>>());
		ResolvedMongoWSID rwsi = (ResolvedMongoWSID) mwdb.resolveWorkspace(
				wsi);
		
		IDnPackage inp = startSaveObject(rwsi, rso, 1, at);
		ObjectSavePackage pkg = inp.pkg;
		IDName r = inp.idname;
		ObjectIDResolvedWS oidrw = new ObjectIDResolvedWS(rwsi,
				wso.getObjectIdentifier().getName());

		//possible race condition 1 - no version provided, version not yet
		//saved, version count not yet incremented
		Set<ObjectIDResolvedWS> oidset = new HashSet<ObjectIDResolvedWS>();
		oidset.add(oidrw);
		try {
			mwdb.getObjects(oidset);
			fail("got objects with no version");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getMessage(),
					is(String.format("No object with name %s exists in workspace %s",
							objname, wsid)));
		}
		
		//possible race condition 2 - as 1, but version provided
		ObjectIDResolvedWS oidrwWithVer = new ObjectIDResolvedWS(rwsi,
				wso.getObjectIdentifier().getName(), 1);
		Set<ObjectIDResolvedWS> oidsetver = new HashSet<ObjectIDResolvedWS>();
		oidsetver.add(oidrwWithVer);
		try {
			mwdb.getObjects(oidsetver);
			fail("got objects with no version");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getMessage(),
					is(String.format("No object with name %s exists in workspace %s",
							objname, wsid)));
		}
		
		//TODO this reproduces the problem found very rarely in JGI jenkins tests
		//race condition on save/get
		jdb.getCollection("workspaceObjects").update("{id: 1, ws: #}",
				rwsi.getID())
			.with("{$inc: {numver: 1}}");
		mwdb.getObjects(oidsetver);
		
		//TODO fix these issues across the board - all methods.
		
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
		td.set(pkg, new TypeData(rso.getRep().createJsonWritable(), abstype, null));
		
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
		ws.requestModuleRegistration(userfoo, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(userfoo, refcntspec, Arrays.asList("RefType"), null, null, false, null);
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
	
	@SuppressWarnings("unchecked")
	@Test
	public void subdata() throws Exception {
		final String specSubdata =
				"module TestSubdata {\n" +
					"typedef structure {\n" +
						"int legs\n;" +
						"string patronymic;\n" +
						"int charisma;\n" +
					"} BugType;\n" +
					"\n" +
					"/* @searchable ws_subset booger bugs.legs\n" +
					"   @searchable ws_subset whanga.*.(legs,charisma)\n" +
					"   @searchable ws_subset foop.[*].(legs,patronymic)\n" +
					"   @searchable ws_subset keys_of(looloo)\n" +
					" */" +
					"typedef structure {" +
						"int booger;" +
						"int booger1;" +
						"BugType bugs;" +
						"BugType bugs1;" +
						"mapping<string, BugType> whanga;" +
						"mapping<string, BugType> whanga1;" +
						"list<BugType> foop;" +
						"list<BugType> foop1;" +
						"mapping<string, string> looloo;" +
						"mapping<string, string> looloo1;" +
					"} SubSetType;" +
				"};";
		String mod = "TestSubdata";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		ws.requestModuleRegistration(userfoo, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(userfoo, specSubdata, Arrays.asList("SubSetType"), null, null, false, null);
		TypeDefId subsettype = new TypeDefId(new TypeDefName(mod, "SubSetType"), 0, 1);
		
		WorkspaceIdentifier subdataws = new WorkspaceIdentifier("subset");
		ws.createWorkspace(userfoo, subdataws.getName(), false, null, null);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("booger", 6);
		Map<String, Object> bugs = new HashMap<String, Object>();
		bugs.put("legs", 3);
		bugs.put("patronymic", "pater");
		bugs.put("charisma", 18);
		data.put("bugs", bugs);
		Map<String, Object> whangabugs = new HashMap<String, Object>(bugs);
		Map<String, Map<String, Object>> whanga =
				new HashMap<String, Map<String, Object>>();
		whanga.put("foo", whangabugs);
		whanga.put("bar", whangabugs);
		data.put("whanga", whanga);
		Map<String, Object> foopbugs = new HashMap<String, Object>(bugs);
		data.put("foop", Arrays.asList(foopbugs, foopbugs, foopbugs));
		Map<String, String> looloo = new HashMap<String, String>();
		looloo.put("question", "42");
		looloo.put("answer", "?");
		data.put("looloo", looloo);
		
		//need to remove stuff from whanga and foop
		Map<String, Object> expected = new HashMap<String, Object>(data);
		
		data.put("booger1", 7);
		Map<String, Object> bugs1 = new HashMap<String, Object>();
		bugs1.put("legs", 2);
		bugs1.put("patronymic", "mater");
		bugs1.put("charisma", 3);
		data.put("bugs1", bugs1);
		Map<String, Map<String, Object>> whanga1 =
				new HashMap<String, Map<String, Object>>();
		whanga1.put("foo1", bugs1);
		whanga1.put("bar1", bugs1);
		data.put("whanga1", whanga1);
		data.put("foop1", Arrays.asList(bugs1, bugs1, bugs1));
		Map<String, String> looloo1 = new HashMap<String, String>();
		looloo1.put("question1", "43");
		looloo1.put("answer1", "!");
		data.put("looloo1", looloo1);
		
		ws.saveObjects(userfoo, subdataws, Arrays.asList(
				new WorkspaceSaveObject(new UObject(data), subsettype, null, new Provenance(userfoo), false)),
				fac);
		
		((Map<String, Object>) expected.get("bugs")).remove("patronymic");
		((Map<String, Object>) expected.get("bugs")).remove("charisma");
		for (Entry<String, Map<String, Object>> entry:
			((Map<String, Map<String, Object>>) expected.get("whanga")).entrySet()) {
			entry.getValue().remove("patronymic");
		}
		for (Map<String, Object> foop: (List<Map<String, Object>>) expected.get("foop")) {
			foop.remove("charisma");
		}
		expected.put("looloo", new HashSet<String>(Arrays.asList("question", "answer")));
		
		ResolvedWorkspaceID rwi = mwdb.resolveWorkspace(subdataws);
		ObjectIDResolvedWS oid = new ObjectIDResolvedWS(rwi, 1L);
		Map<String, Object> d = mwdb.getObjectSubData(new HashSet<ObjectIDResolvedWS>(Arrays.asList(
				new ObjectIDResolvedWS(rwi, 1L)))).get(oid);
						
		d.put("looloo", new HashSet<String>((List<String>) d.get("looloo")));
		assertThat("subdata is not correct", expected, is(d));
	}
	
	@Test
	public void escapeSubdataKeys() throws Exception {
		final String specSubdata =
				"module TestSubdataEscape {\n" +
					"/* @searchable ws_subset stuff\n" +
					" */" +
					"typedef structure {" +
						"mapping<string, list<mapping<string, string>>> stuff;" +
					"} SubSetEscapeType;" +
				"};";
		String mod = "TestSubdataEscape";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		ws.requestModuleRegistration(userfoo, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(userfoo, specSubdata, Arrays.asList("SubSetEscapeType"), null, null, false, null);
		TypeDefId subsettype = new TypeDefId(new TypeDefName(mod, "SubSetEscapeType"), 0, 1);
		WorkspaceIdentifier subdataws = new WorkspaceIdentifier("escapesubset");
		ws.createWorkspace(userfoo, subdataws.getName(), false, null, null);
		
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> expected = new HashMap<String, Object>();
		
		Map<String, String> dataSimpleCheck = new HashMap<String, String>();
		dataSimpleCheck.put("foo.bar", "foo");
		dataSimpleCheck.put("foo$Bar", "foo");
		dataSimpleCheck.put("foo%bar", "foo");
		dataSimpleCheck.put("foo@bar", "foo");
		
		Map<String, String> expectedSimpleCheck = new HashMap<String, String>();
		expectedSimpleCheck.put("foo%2ebar", "foo");
		expectedSimpleCheck.put("foo%24Bar", "foo");
		expectedSimpleCheck.put("foo%25bar", "foo");
		expectedSimpleCheck.put("foo@bar", "foo");
		
		Map<String, String> dataOverwrite = new HashMap<String, String>();
		for (int i = 0; i < 50; i++) {
			dataOverwrite.put("foo%2ebar", "foo");
		}
		dataOverwrite.put("foo.bar", "foo");
		for (int i = 0; i < 50; i++) {
			dataOverwrite.put("foo%2ebar", "foo");
		}
		Map<String, String> expectedOverwrite = new HashMap<String, String>();
		for (int i = 0; i < 50; i++) {
			expectedOverwrite.put("foo%252ebar", "foo");
		}
		expectedOverwrite.put("foo%2ebar", "foo");
		for (int i = 0; i < 50; i++) {
			expectedOverwrite.put("foo%252ebar", "foo");
		}
		Map<String, List<Map<String, String>>> datastuff =
				new HashMap<String, List<Map<String, String>>>();
		Map<String, List<Map<String, String>>> expectedstuff =
				new HashMap<String, List<Map<String, String>>>();
		datastuff.put("thing", Arrays.asList(dataSimpleCheck, dataOverwrite));
		expectedstuff.put("thing", Arrays.asList(expectedSimpleCheck, expectedOverwrite));
		datastuff.put("foo%$.bar", Arrays.asList(dataSimpleCheck));
		expectedstuff.put("foo%25%24%2ebar", Arrays.asList(expectedSimpleCheck));
		datastuff.put("foobar", Arrays.asList(dataSimpleCheck));
		expectedstuff.put("foobar", Arrays.asList(expectedSimpleCheck));
		data.put("stuff", datastuff);
		expected.put("stuff", expectedstuff);
		
		ws.saveObjects(userfoo, subdataws, Arrays.asList(
				new WorkspaceSaveObject(new UObject(data), subsettype, null, new Provenance(userfoo), false)),
				fac);
		
		ResolvedWorkspaceID rwi = mwdb.resolveWorkspace(subdataws);
		ObjectIDResolvedWS oid = new ObjectIDResolvedWS(rwi, 1L);
		Map<String, Object> d = mwdb.getObjectSubData(new HashSet<ObjectIDResolvedWS>(Arrays.asList(
				new ObjectIDResolvedWS(rwi, 1L)))).get(oid);
		
		assertThat("subdata is not correct", expected, is(d));
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