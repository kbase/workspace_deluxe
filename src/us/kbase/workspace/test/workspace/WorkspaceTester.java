package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;


import org.apache.commons.lang3.text.WordUtils;
import org.junit.AfterClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import us.kbase.common.test.TestException;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.DefaultReferenceParser;
import us.kbase.workspace.database.ObjectChain;
import us.kbase.workspace.database.ObjectChainResolvedWS;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Provenance.ProvenanceAction;
import us.kbase.workspace.database.SubObjectIdentifier;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.ShockBackend;
import us.kbase.workspace.kbase.Util;
import us.kbase.workspace.lib.WorkspaceSaveObject;
import us.kbase.workspace.lib.Workspace;
import us.kbase.workspace.test.JsonTokenStreamOCStat;
import us.kbase.workspace.test.WorkspaceTestCommon;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DB;

@RunWith(Parameterized.class)
public class WorkspaceTester {
	
	//true if no net access since shock requires access to globus to work
	private static final boolean SKIP_SHOCK = true;

	protected static final ObjectMapper mapper = new ObjectMapper();
	
	protected static final String LONG_TEXT_PART = "Passersby were amazed by the unusually large amounts of blood. ";
	protected static String LONG_TEXT = "";
	static {
		for (int i = 0; i < 17; i++) {
			LONG_TEXT += LONG_TEXT_PART;
		}
	}
	protected static String TEXT100 = "";
	static {
		for (int i = 0; i < 10; i++) {
			TEXT100 += "aaaaabbbbb";
		}
	}
	protected static String TEXT101 = TEXT100 + "f";
	protected static String TEXT255 = TEXT100 + TEXT100 + TEXT100.substring(0, 55);
	protected static String TEXT256 = TEXT255 + "f";
	protected static String TEXT1000 = "";
	static {
		for (int i = 0; i < 10; i++) {
			TEXT1000 += TEXT100;
		}
	}
	
	protected static final Map<String, String> MT_META = new HashMap<String, String>();
	
	private static ShockBackend sbe = null;
	
	protected static final WorkspaceUser SOMEUSER = new WorkspaceUser("auser");
	protected static final WorkspaceUser AUSER = new WorkspaceUser("a");
	protected static final WorkspaceUser BUSER = new WorkspaceUser("b");
	protected static final WorkspaceUser CUSER = new WorkspaceUser("c");
	protected static final AllUsers STARUSER = new AllUsers('*');
	
	protected static final TypeDefId SAFE_TYPE1 =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);
	protected static final TypeDefId SAFE_TYPE2 =
			new TypeDefId(new TypeDefName("SomeModule", "AType2"), 0, 1);
	protected static final TypeDefId SAFE_TYPE1_10 =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 1, 0);
	protected static final TypeDefId SAFE_TYPE2_10 =
			new TypeDefId(new TypeDefName("SomeModule", "AType2"), 1, 0);
	protected static final TypeDefId SAFE_TYPE1_20 =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 2, 0);
	protected static final TypeDefId SAFE_TYPE2_20 =
			new TypeDefId(new TypeDefName("SomeModule", "AType2"), 2, 0);
	protected static final TypeDefId SAFE_TYPE2_21 =
			new TypeDefId(new TypeDefName("SomeModule", "AType2"), 2, 1);

	static {
		JsonTokenStreamOCStat.register();
	}
	
	@Parameters
	public static Collection<Object[]> generateData() throws Exception {
		printMem("*** startup ***");
		List<Object[]> tests;
		if (SKIP_SHOCK) {
			System.out.println("Skipping shock backend tests");
			tests = Arrays.asList(new Object[][] {
					{"mongo"}
			});
		} else {
			tests = Arrays.asList(new Object[][] {
					{"mongo"},
					{"shock"}
			});
		}
		printMem("*** startup complete ***");
		return tests;
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (sbe != null) {
			System.out.println("deleting all shock nodes");
			sbe.removeAllBlobs();
		}
		JsonTokenStreamOCStat.showStat();
	}
	
	private static final Map<String, Workspace> configs =
			new HashMap<String, Workspace>();
	protected final Workspace ws;
	
	public WorkspaceTester(String config) throws Exception {
		if (!configs.containsKey(config)) {
			if ("shock".equals(config)) {
				configs.put(config, setUpShock());
			} else if("mongo".equals(config)) {
				configs.put(config, setUpMongo());
			} else {
				throw new TestException("Unknown test config: " + config);
			}
		}
		ws = configs.get(config);
	}
	
	private Workspace setUpMongo() throws Exception {
		return setUpWorkspaces("gridFS", "foo", "foo");
	}
	
	private Workspace setUpShock() throws Exception {
		String shockuser = System.getProperty("test.user1");
		String shockpwd = System.getProperty("test.pwd1");
		return setUpWorkspaces("shock", shockuser, shockpwd);
	}
	
	private Workspace setUpWorkspaces(String type, String shockuser,
			String shockpwd) throws Exception {
		DB db = WorkspaceTestCommon.destroyAndSetupDB(1, type, shockuser);
		String host = WorkspaceTestCommon.getHost();
		String mUser = WorkspaceTestCommon.getMongoUser();
		String mPwd = WorkspaceTestCommon.getMongoPwd();
		String db1 = WorkspaceTestCommon.getDB1();
		final String kidlpath = new Util().getKIDLpath();
		
		TempFilesManager tfm = TempFilesManager.forTests();
		tfm.cleanup();
		WorkspaceDatabase wsdb = null;
		if (mUser != null) {
			wsdb = new MongoWorkspaceDB(host, db1, shockpwd, mUser, mPwd,
					kidlpath, null, tfm);
		} else {
			wsdb = new MongoWorkspaceDB(host, db1, shockpwd, "foo", "foo",
					kidlpath, null, tfm);
		}
		Workspace work = new Workspace(wsdb, new DefaultReferenceParser());
		assertTrue("Backend setup failed", work.getBackendType().equals(WordUtils.capitalize(type)));
		installSpecs(work);
		if ("shock".equals(type)) {
			sbe = new ShockBackend(db, "shock_", new URL(WorkspaceTestCommon.getShockUrl()), 
					shockuser, shockpwd);
		}
		return work;
	}
		
	private void installSpecs(Workspace work) throws Exception {
		//make a general spec that tests that don't worry about typechecking can use
		WorkspaceUser foo = new WorkspaceUser("foo");
		//simple spec
		work.requestModuleRegistration(foo, "SomeModule");
		work.resolveModuleRegistration("SomeModule", true);
		work.compileNewTypeSpec(foo, 
				"module SomeModule {" +
					"/* @optional thing */" +
					"typedef structure {" +
						"string thing;" +
					"} AType;" +
					"/* @optional thing */" +
					"typedef structure {" +
						"string thing;" +
					"} AType2;" +
				"};",
				Arrays.asList("AType", "AType2"), null, null, false, null);
		work.releaseTypes(foo, "SomeModule");
		work.compileNewTypeSpec(foo, 
				"module SomeModule {" +
					"typedef structure {" +
						"string thing;" +
					"} AType;" +
					"typedef structure {" +
						"string thing;" +
					"} AType2;" +
				"};",
				null, null, null, false, null);
		work.releaseTypes(foo, "SomeModule");
		work.compileNewTypeSpec(foo, 
				"module SomeModule {" +
					"typedef structure {" +
						"string thing;" +
					"} AType;" +
					"/* @optional thing2 */" +
					"typedef structure {" +
						"string thing;" +
						"string thing2;" +
					"} AType2;" +
				"};",
				null, null, null, false, null);
		work.releaseTypes(foo, "SomeModule");
		
		//spec that simply references another object
		final String specRefType =
				"module CopyRev {" +
					"/* @id ws */" +
					"typedef string reference;" +
					"typedef structure {" +
						"list<reference> refs;" +
					"} RefType;" +
				"};";
		
		String mod = "CopyRev";
		work.requestModuleRegistration(foo, mod);
		work.resolveModuleRegistration(mod, true);
		work.compileNewTypeSpec(foo, specRefType, Arrays.asList("RefType"), null, null, false, null);
		work.releaseTypes(foo, mod);

		// more complicated spec with two released versions and 1 unreleased version for type
		// registration tests
		work.requestModuleRegistration(foo, "TestModule");
		work.resolveModuleRegistration("TestModule", true);
		work.compileNewTypeSpec(foo, 
				"module TestModule { " +
						"typedef structure {string name; string seq;} Feature; "+
						"typedef structure {string name; list<Feature> features;} Genome; "+
						"typedef structure {string private_stuff;} InternalObj; "+
						"funcdef getFeature(string fid, string pattern) returns (Feature);" +
						"};",
						Arrays.asList("Feature","Genome"), null, null, false, null);
		work.releaseTypes(foo, "TestModule");
		work.compileNewTypeSpec(foo, 
				"module TestModule { " +
						"typedef structure {string name; string seq;} Feature; "+
						"typedef structure {string name; list<Feature> feature_list;} Genome; "+
						"typedef structure {string private_stuff;} InternalObj; "+
						"funcdef getFeature(string fid) returns (Feature);" +
						"};",
						null, null, null, false, null);
		work.compileNewTypeSpec(foo, 
				"module TestModule { " +
						"typedef structure {string name; string seq;} Feature; "+
						"typedef structure {string name; list<Feature> feature_list;} Genome; "+
						"typedef structure {string private_stuff;} InternalObj; "+
						"funcdef getFeature(string fid) returns (Feature);" +
						"funcdef getGenome(string gid) returns (Genome);" +
						"};",
						null, null, null, false, null);
		work.releaseTypes(foo, "TestModule");

		work.requestModuleRegistration(foo, "UnreleasedModule");
		work.resolveModuleRegistration("UnreleasedModule", true);
		work.compileNewTypeSpec(foo, 
				"module UnreleasedModule {/* @optional thing */ typedef structure {string thing;} AType; funcdef aFunc(AType param) returns ();};",
				Arrays.asList("AType"), null, null, false, null);
	}
	
	private static void printMem(String startmsg) {
		System.out.println(startmsg);
		System.out.println("free mem: " + Runtime.getRuntime().freeMemory());
		System.out.println(" max mem: " + Runtime.getRuntime().maxMemory());
		System.out.println(" ttl mem: " + Runtime.getRuntime().maxMemory());
	}
	
	protected void failSetWSDesc(WorkspaceUser user,
			WorkspaceIdentifier wsi, String description,
			Exception e) throws Exception {
		try {
			ws.setWorkspaceDescription(user, wsi, description);
			fail("set ws desc when should fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}

	protected void checkWSInfo(WorkspaceIdentifier wsi, WorkspaceUser owner, String name,
			long objs, Permission perm, boolean globalread, long id, Date moddate,
			String lockstate, Map<String, String> meta) throws Exception {
		checkWSInfo(ws.getWorkspaceInformation(owner, wsi), owner, name, objs,
				perm, globalread, id, moddate, lockstate, meta);
	}
	
	protected Date checkWSInfo(WorkspaceIdentifier wsi, WorkspaceUser owner, String name,
			long objs, Permission perm, boolean globalread, long id,
			String lockstate, Map<String, String> meta) throws Exception {
		WorkspaceInformation info = ws.getWorkspaceInformation(owner, wsi);
		checkWSInfo(info, owner, name, objs, perm, globalread, lockstate, meta);
		assertThat("ws id correct", info.getId(), is(id));
		return info.getModDate();
	}
	
	protected void checkWSInfo(WorkspaceInformation info, WorkspaceUser owner, String name,
			long objs, Permission perm, boolean globalread, long id, Date moddate,
			String lockstate, Map<String, String> meta) {
		checkWSInfo(info, owner, name, objs, perm, globalread, lockstate, meta);
		assertThat("ws id correct", info.getId(), is(id));
		assertThat("ws mod date correct", info.getModDate(), is(moddate));
	}
	
	protected void checkWSInfo(WorkspaceInformation info, WorkspaceUser owner, String name,
			long objs, Permission perm, boolean globalread, String lockstate,
			Map<String, String> meta) {
		assertDateisRecent(info.getModDate());
		assertThat("ws owner correct", info.getOwner(), is(owner));
		assertThat("ws name correct", info.getName(), is(name));
		assertThat("ws max obj correct", info.getApproximateObjects(), is(objs));
		assertThat("ws permissions correct", info.getUserPermission(), is(perm));
		assertThat("ws global read correct", info.isGloballyReadable(), is(globalread));
		assertThat("ws lockstate correct", info.getLockState(), is(lockstate));
		assertThat("ws meta correct", info.getUserMeta(), is(meta));
	}
	
	protected void assertDatesAscending(Date... dates) {
		for (int i = 1; i < dates.length; i++) {
			assertTrue("dates are ascending", dates[i-1].before(dates[i]));
		}
	}
	
	protected void failWSMeta(WorkspaceUser user, WorkspaceIdentifier wsi,
			String key, String value, Exception e) throws Exception {
		failWSRemoveMeta(user, wsi, key, e);
		Map<String, String> meta = new HashMap<String, String>();
		meta.put(key, value);
		failWSSetMeta(user, wsi, meta, e);
	}

	protected void failWSRemoveMeta(WorkspaceUser user, WorkspaceIdentifier wsi,
			String key, Exception e) {
		try {
			ws.removeWorkspaceMetadata(user, wsi, key);
			fail("expected remove ws meta to fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}

	protected void failWSSetMeta(WorkspaceUser user, WorkspaceIdentifier wsi,
			Map<String, String> meta, Exception e) {
		try {
			ws.setWorkspaceMetadata(user, wsi, meta);
			fail("expected set ws meta to fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	protected void failCreateWorkspace(WorkspaceUser user, String name,
			boolean global, Map<String,String> meta, String description, Exception e)
			throws Exception {
		try {
			ws.createWorkspace(user, name, global, description, meta);
			fail("created workspace w/ bad args");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	protected void failSetPermissions(WorkspaceUser user, WorkspaceIdentifier wsi,
			List<WorkspaceUser> users, Permission perm, Exception e) throws Exception {
		try {
			ws.setPermissions(user, wsi, users, perm);
			fail("set perms when should fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	protected void checkObjInfo(ObjectInformation info, long id,
			String name, String type, int version, WorkspaceUser user,
			long wsid, String wsname, String chksum, long size,
			Map<String, String> usermeta) {
		assertDateisRecent(info.getSavedDate());
		assertThat("Object id correct", info.getObjectId(), is(id));
		assertThat("Object name is correct", info.getObjectName(), is(name));
		assertThat("Object type is correct", info.getTypeString(), is(type));
		assertThat("Object version is correct", info.getVersion(), is(version));
		assertThat("Object user is correct", info.getSavedBy(), is(user));
		assertThat("Object workspace id is correct", info.getWorkspaceId(), is(wsid));
		assertThat("Object workspace name is correct", info.getWorkspaceName(), is(wsname));
		assertThat("Object chksum is correct", info.getCheckSum(), is(chksum));
		assertThat("Object size is correct", info.getSize(), is(size));
		assertThat("Object user meta is correct", info.getUserMetaData(), is(usermeta));
	}
	
	protected void assertDateisRecent(Date orig) {
		Date now = new Date();
		int onemin = 1000 * 60;
		assertTrue("date is recent", now.getTime() - orig.getTime() < onemin);
	}

	protected Date assertWorkspaceDateUpdated(WorkspaceUser user,
			WorkspaceIdentifier wsi, Date lastDate, String assertion)
			throws Exception {
		Date readCurrentDate = ws.getWorkspaceInformation(user, wsi).getModDate();
		assertTrue(assertion, readCurrentDate.after(lastDate));
		return readCurrentDate;
	}
	
	protected void failSave(WorkspaceUser user, WorkspaceIdentifier wsi, List<WorkspaceSaveObject> wso,
			Exception exp)
			throws Exception {
		try {
			ws.saveObjects(user, wsi, wso);
			fail("Saved bad objects");
		} catch (Exception e) {
			assertThat("correct exception type", e, is(exp.getClass()));
			assertThat("correct exception", e.getLocalizedMessage(), is(exp.getLocalizedMessage()));
		}
	}

	protected void checkObjectAndInfo(WorkspaceUser bar,
			List<ObjectIdentifier> ids, List<FakeObjectInfo> fakeinfo,
			List<Map<String, Object>> data) throws Exception {
		List<WorkspaceObjectData> retdata = ws.getObjects(bar, ids);
		List<WorkspaceObjectData> retdata2 = ws.getObjectsSubSet(bar, objIDToSubObjID(ids));
		Iterator<WorkspaceObjectData> ret1 = retdata.iterator();
		Iterator<WorkspaceObjectData> ret2 = retdata2.iterator();
		Iterator<FakeObjectInfo> info = fakeinfo.iterator();
		Iterator<Map<String, Object>> dataiter = data.iterator();
		while (ret1.hasNext()) {
			FakeObjectInfo i = info.next();
			Map<String, Object> d = dataiter.next();
			checkObjectAndInfo(ret1.next(), i , d);
			checkObjectAndInfo(ret2.next(), i , d);
		}
		if (ret2.hasNext() || info.hasNext() || dataiter.hasNext()) {
			fail("mismatched iter counts");
		}
	}

	protected void checkObjectAndInfo(WorkspaceObjectData wod,
			FakeObjectInfo info, Map<String, Object> data) {
		checkObjInfo(wod.getObjectInfo(), info.getObjectId(), info.getObjectName(),
				info.getTypeString(), info.getVersion(), info.getSavedBy(),
				info.getWorkspaceId(), info.getWorkspaceName(), info.getCheckSum(),
				info.getSize(), info.getUserMetaData());
		assertThat("correct data", wod.getData(), is((Object) data));
		
	}

	protected void successGetObjects(WorkspaceUser user,
			List<ObjectIdentifier> objs) throws Exception {
		ws.getObjects(user, objs);
		ws.getObjectsSubSet(user, objIDToSubObjID(objs));
	}
	
	protected void failGetObjects(WorkspaceUser user, List<ObjectIdentifier> objs,
			Exception e) 
			throws Exception {
		try {
			successGetObjects(user, objs);
			fail("called get objects with bad args");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
		try {
			ws.getObjectsSubSet(user, objIDToSubObjID(objs));
			fail("called get subobjects with bad args");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
		try {
			ws.getReferencingObjects(user, objs);
			fail("called get refing objects with bad args");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
		try {
			ws.getReferencingObjectCounts(user, objs);
			fail("called get refing objects with bad args");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}

	protected List<SubObjectIdentifier> objIDToSubObjID(
			List<ObjectIdentifier> objs) {
		List<SubObjectIdentifier> soi = new ArrayList<SubObjectIdentifier>();
		for (ObjectIdentifier oi: objs) {
			soi.add(new SubObjectIdentifier(oi, null));
		}
		return soi;
	}
	
	protected void failSave(WorkspaceUser user, WorkspaceIdentifier wsi, 
			Map<String, Object> data, TypeDefId type, Provenance prov,
			Throwable exception) throws Exception{
		try {
			ws.saveObjects(user, wsi, Arrays.asList(
					new WorkspaceSaveObject(data, type, null, prov, false)));
			fail("Saved bad object");
		} catch (Exception e) {
			if (e instanceof NullPointerException) {
				e.printStackTrace();
			}
			assertThat("correct exception", e.getLocalizedMessage(),
					is(exception.getLocalizedMessage()));
			assertThat("correct exception type", e, is(exception.getClass()));
		}
	}
	
	protected List<Date> checkProvenanceCorrect(WorkspaceUser foo, Provenance prov,
			ObjectIdentifier obj, Map<String, String> refmap) throws Exception {
		Provenance pgot = ws.getObjects(foo, Arrays.asList(obj)).get(0).getProvenance();
		checkProvenanceCorrect(prov, pgot, refmap);
		Provenance pgot2 = ws.getObjectsSubSet(foo, objIDToSubObjID(Arrays.asList(obj)))
				.get(0).getProvenance();
		checkProvenanceCorrect(prov, pgot2,refmap);
		return Arrays.asList(pgot.getDate(), pgot2.getDate());
	}
	
	//if refmap != null expected is a Provenance object. Otherwise it's a subclass
	// with an implemented getResolvedObjects method.
	protected void checkProvenanceCorrect(Provenance expected, Provenance got,
			Map<String, String> refmap) {
		assertThat("user equal", got.getUser(), is(expected.getUser()));
		assertThat("same number actions", got.getActions().size(),
				is(expected.getActions().size()));
		if (refmap == null) {
			assertThat("dates are the same", got.getDate(), is(expected.getDate()));
		} else {
			assertDateisRecent(got.getDate());
		}
		
		Iterator<ProvenanceAction> gotAct = got.getActions().iterator();
		Iterator<ProvenanceAction> expAct = expected.getActions().iterator();
		while (gotAct.hasNext()) {
			ProvenanceAction gotpa = gotAct.next();
			ProvenanceAction exppa = expAct.next();
			assertThat("cmd line equal", gotpa.getCommandLine(), is(exppa.getCommandLine()));
			assertThat("desc equal", gotpa.getDescription(), is(exppa.getDescription()));
			assertThat("inc args equal", gotpa.getIncomingArgs(), is(exppa.getIncomingArgs()));
			assertThat("method equal", gotpa.getMethod(), is(exppa.getMethod()));
			assertThat("meth params equal", gotpa.getMethodParameters(), is(exppa.getMethodParameters()));
			assertThat("out args equal", gotpa.getOutgoingArgs(), is(exppa.getOutgoingArgs()));
			assertThat("script equal", gotpa.getScript(), is(exppa.getScript()));
			assertThat("script ver equal", gotpa.getScriptVersion(), is(exppa.getScriptVersion()));
			assertThat("service equal", gotpa.getServiceName(), is(exppa.getServiceName()));
			assertThat("serv ver equal", gotpa.getServiceVersion(), is(exppa.getServiceVersion()));
			assertThat("time equal", gotpa.getTime(), is(exppa.getTime()));
			assertThat("refs equal", gotpa.getWorkspaceObjects(), is(exppa.getWorkspaceObjects()));
			assertThat("correct number resolved refs", gotpa.getResolvedObjects().size(),
					is(gotpa.getWorkspaceObjects().size()));
			if (refmap != null) {
				Iterator<String> gotrefs = gotpa.getWorkspaceObjects().iterator();
				Iterator<String> gotresolvedrefs = gotpa.getResolvedObjects().iterator();
				while (gotrefs.hasNext()) {
					assertThat("ref resolved correctly", gotresolvedrefs.next(),
							is(refmap.get(gotrefs.next())));
				}
			} else {
				assertThat("resolved refs equal", gotpa.getResolvedObjects(),
						is(exppa.getResolvedObjects()));
			}
		}
	}

	protected void getNonExistantObject(WorkspaceUser foo, ObjectIdentifier oi,
			String exception) throws Exception {
		try {
			ws.getObjectInformation(foo, Arrays.asList(oi), false, false);
			fail("got non-existant object");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getLocalizedMessage(), 
					is(exception));
		}
	}
	
	protected void testObjectIdentifier(String goodId) {
		new ObjectIdentifier(new WorkspaceIdentifier("foo"), goodId);
		FakeResolvedWSID fakews = new FakeResolvedWSID(1);
		new ObjectIDResolvedWS(fakews, goodId);
//		new ObjectIDResolvedWSNoVer(fakews, goodId);
		new ObjectIDNoWSNoVer(goodId);
	}
	
	protected void testObjectIdentifier(String goodId, int version) {
		new ObjectIdentifier(new WorkspaceIdentifier("foo"), goodId, version);
		FakeResolvedWSID fakews = new FakeResolvedWSID(1);
		new ObjectIDResolvedWS(fakews, goodId, version);
//		new ObjectIDResolvedWSNoVer(fakews, goodId);
		new ObjectIDNoWSNoVer(goodId);
	}
	
	protected void testObjectIdentifier(int goodId) {
		new ObjectIdentifier(new WorkspaceIdentifier("foo"), goodId);
		FakeResolvedWSID fakews = new FakeResolvedWSID(1);
		new ObjectIDResolvedWS(fakews, goodId);
//		new ObjectIDResolvedWSNoVer(fakews, goodId);
		new ObjectIDNoWSNoVer(goodId);
	}
	
	protected void testObjectIdentifier(int goodId, int version) {
		new ObjectIdentifier(new WorkspaceIdentifier("foo"), goodId, version);
		FakeResolvedWSID fakews = new FakeResolvedWSID(1);
		new ObjectIDResolvedWS(fakews, goodId, version);
//		new ObjectIDResolvedWSNoVer(fakews, goodId);
		new ObjectIDNoWSNoVer(goodId);
	}
	
	protected void testObjectIdentifier(WorkspaceIdentifier badWS, String badId,
			String exception) {
		try {
			new ObjectIdentifier(badWS, badId);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
		FakeResolvedWSID fakews = null;
		if (badWS != null) {
			fakews = new FakeResolvedWSID(1);
		} else {
			exception = "r" + exception;
		}
		try {
			new ObjectIDResolvedWS(fakews, badId);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
//		try {
//			new ObjectIDResolvedWSNoVer(fakews, badId);
//			fail("Initialized invalid object id");
//		} catch (IllegalArgumentException e) {
//			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
//		}
		if (badWS != null) {
			try {
				new ObjectIDNoWSNoVer(badId);
				fail("Initialized invalid object id");
			} catch (IllegalArgumentException e) {
				assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
			}
		}
	}
	
	protected void testObjectIdentifier(WorkspaceIdentifier badWS, String badId,
			int version, String exception) {
		try {
			new ObjectIdentifier(new WorkspaceIdentifier("foo"), badId, version);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
		FakeResolvedWSID fakews = null;
		if (badWS != null) {
			fakews = new FakeResolvedWSID(1);
		} else {
			exception = "r" + exception;
		}
		try {
			new ObjectIDResolvedWS(fakews, badId, version);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}
	
	protected void testObjectIdentifier(WorkspaceIdentifier badWS, int badId,
			String exception) {
		try {
			new ObjectIdentifier(badWS, badId);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
		FakeResolvedWSID fakews = null;
		if (badWS != null) {
			fakews = new FakeResolvedWSID(1);
		} else {
			exception = "r" + exception;
		}
		try {
			new ObjectIDResolvedWS(fakews, badId);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
//		try {
//			new ObjectIDResolvedWSNoVer(fakews, badId);
//			fail("Initialized invalid object id");
//		} catch (IllegalArgumentException e) {
//			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
//		}
		if (badWS != null) {
			try {
				new ObjectIDNoWSNoVer(badId);
				fail("Initialized invalid object id");
			} catch (IllegalArgumentException e) {
				assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
			}
		}
	}
	
	protected void testObjectIdentifier(WorkspaceIdentifier badWS,
			int badId, int version, String exception) {
		try {
			new ObjectIdentifier(badWS, badId, version);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
		FakeResolvedWSID fakews = null;
		if (badWS != null) {
			fakews = new FakeResolvedWSID(1);
		} else {
			exception = "r" + exception;
		}
		try {
			new ObjectIDResolvedWS(fakews, badId, version);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}
	
	protected void testCreate(WorkspaceIdentifier goodWs, String name,
			Long id) {
		ObjectIdentifier.create(goodWs, name, id);
		ObjectIDNoWSNoVer.create(name, id);
		
	}
	
	protected void testCreateVer(WorkspaceIdentifier goodWs, String name, Long id,
			Integer ver) {
		ObjectIdentifier.create(goodWs, name, id, ver);
	}
	
	protected void testCreate(WorkspaceIdentifier badWS, String name,
			Long id, String exception) {
		try {
			ObjectIdentifier.create(badWS, name, id);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
		if (badWS != null) {
			try {
				ObjectIDNoWSNoVer.create(name, id);
				fail("Initialized invalid object id");
			} catch (IllegalArgumentException e) {
				assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
			}
		}
	}
	
	protected void testCreateVer(WorkspaceIdentifier badWS, String name,
			Long id, Integer ver, String exception) {
		try {
			ObjectIdentifier.create(badWS, name, id, ver);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}
	
	protected void testRef(String ref) {
		ObjectIdentifier.parseObjectReference(ref);
	}
	
	protected void testRef(String ref, String exception) {
		try {
			ObjectIdentifier.parseObjectReference(ref);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}

	protected void checkNonDeletedObjs(WorkspaceUser foo,
			Map<ObjectIdentifier, Object> idToData) throws Exception {
		List<ObjectIdentifier> objs = new ArrayList<ObjectIdentifier>(idToData.keySet());
		List<WorkspaceObjectData> d = ws.getObjects(foo, objs);
		for (int i = 0; i < d.size(); i++) {
			assertThat("can get correct data from undeleted objects",
					d.get(i).getData(), is((Object) idToData.get(objs.get(i))));
		}
		d = ws.getObjectsSubSet(foo, objIDToSubObjID(objs));
		for (int i = 0; i < d.size(); i++) {
			assertThat("can get correct data from undeleted objects",
					d.get(i).getData(), is((Object) idToData.get(objs.get(i))));
		}
	}

	protected void failToGetDeletedObjects(WorkspaceUser user,
			List<ObjectIdentifier> objs, String exception) throws Exception {
		failGetObjects(user, objs, new NoSuchObjectException(exception));
		try {
			ws.getObjectInformation(user, objs, true, false);
			fail("got deleted object's history");
		} catch (NoSuchObjectException e) {
			assertThat("correct exception", e.getLocalizedMessage(), is(exception));
		}
	}

	protected void failCopy(WorkspaceUser user, ObjectIdentifier from, ObjectIdentifier to, Exception e) {
		try {
			ws.copyObject(user, from, to);
			fail("copied object sucessfully but expected fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	protected void failRevert(WorkspaceUser user, ObjectIdentifier from, Exception e) {
		try {
			ws.revertObject(user, from);
			fail("reverted object sucessfully but expected fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	protected Map<String, String> makeSimpleMeta(String key, String value) {
		Map<String, String> map = new HashMap<String, String>();
		map.put(key, value);
		return map;
	}
	
	protected Map<String, List<String>> makeRefData(String... refs) {
		Map<String, List<String>> data = new HashMap<String, List<String>>();
		data.put("refs", Arrays.asList(refs));
		return data;
	}
	

	protected void compareObjectAndInfo(ObjectInformation original,
			ObjectInformation copied, WorkspaceUser user, long wsid, String wsname, 
			long objectid, String objname, int version) throws Exception {
		compareObjectInfo(original, copied, user, wsid, wsname, objectid,
				objname, version);
		WorkspaceObjectData orig = ws.getObjects(original.getSavedBy(), Arrays.asList(
				new ObjectIdentifier(new WorkspaceIdentifier(original.getWorkspaceId()),
						original.getObjectId(), original.getVersion()))).get(0);
		WorkspaceObjectData copy = ws.getObjects(copied.getSavedBy(), Arrays.asList(
				new ObjectIdentifier(new WorkspaceIdentifier(copied.getWorkspaceId()),
						copied.getObjectId(), copied.getVersion()))).get(0);
		compareObjectInfo(orig.getObjectInfo(), copy.getObjectInfo(), user, wsid, wsname, objectid,
				objname, version);
		assertThat("returned data same", copy.getData(), is(orig.getData()));
		assertThat("returned refs same", copy.getReferences(), is(orig.getReferences()));
		checkProvenanceCorrect(orig.getProvenance(), copy.getProvenance(), null);
		
		WorkspaceObjectData origsub = ws.getObjectsSubSet(original.getSavedBy(), Arrays.asList(
				new SubObjectIdentifier(new ObjectIdentifier(new WorkspaceIdentifier(
						original.getWorkspaceId()),
						original.getObjectId(), original.getVersion()), null))).get(0);
		WorkspaceObjectData copysub = ws.getObjectsSubSet(copied.getSavedBy(), Arrays.asList(
				new SubObjectIdentifier(new ObjectIdentifier(new WorkspaceIdentifier(
						copied.getWorkspaceId()),
						copied.getObjectId(), copied.getVersion()), null))).get(0);
		compareObjectInfo(origsub.getObjectInfo(), copysub.getObjectInfo(), user, wsid, wsname, objectid,
				objname, version);
		assertThat("returned data same", copysub.getData(), is(origsub.getData()));
		assertThat("returned refs same", copysub.getReferences(), is(origsub.getReferences()));
		checkProvenanceCorrect(origsub.getProvenance(), copysub.getProvenance(), null);
	}
	
	protected void compareObjectAndInfo(WorkspaceObjectData got,
			ObjectInformation info, Provenance prov, Map<String, ? extends Object> data,
			List<String> refs, Map<String, String> refmap)
			throws Exception {
		assertThat("object info same", got.getObjectInfo(), is(info));
		assertThat("returned data same", got.getData(), is((Object)data));
		assertThat("returned data jsonnode same", got.getDataAsTokens().getAsJsonNode(),
				is(new ObjectMapper().valueToTree(data)));
		assertThat("returned refs same", new HashSet<String>(got.getReferences()),
				is(new HashSet<String>(refs)));
		checkProvenanceCorrect(prov, got.getProvenance(), refmap);
	}

	protected void compareObjectInfo(ObjectInformation original,
			ObjectInformation copied, WorkspaceUser user, long wsid,
			String wsname, long objectid, String objname, int version) {
		assertThat("checksum same", copied.getCheckSum(), is(original.getCheckSum()));
		assertThat("correct object id", copied.getObjectId(), is(objectid));
		assertThat("correct object name", copied.getObjectName(), is(objname));
		assertThat("correct user", copied.getSavedBy(), is(user));
		assertTrue("copy date after orig", copied.getSavedDate().after(original.getSavedDate()));
		assertDateisRecent(original.getSavedDate());
		assertDateisRecent(copied.getSavedDate());
		assertThat("size correct", copied.getSize(), is(original.getSize()));
		assertThat("type correct", copied.getTypeString(), is(original.getTypeString()));
		assertThat("meta correct", copied.getUserMetaData(), is(original.getUserMetaData()));
		assertThat("version correct", copied.getVersion(), is(version));
		assertThat("wsid correct", copied.getWorkspaceId(), is(wsid));
		assertThat("ws name correct", copied.getWorkspaceName(), is(wsname));
	}

	protected ObjectInformation saveObject(WorkspaceUser user, WorkspaceIdentifier wsi,
			Map<String, String> meta, Map<String, ? extends Object> data, TypeDefId type,
			String name, Provenance prov)
			throws Exception {
		return saveObject(user, wsi, meta, data, type, name, prov, false);
	}
	
	protected ObjectInformation saveObject(WorkspaceUser user, WorkspaceIdentifier wsi,
			Map<String, String> meta, Map<String, ? extends Object> data,
			TypeDefId type, String name, Provenance prov, boolean hide)
			throws Exception {
		if (name == null) {
			return ws.saveObjects(user, wsi, Arrays.asList(
					new WorkspaceSaveObject(data, type, meta, prov, hide)))
					.get(0);
		}
		return ws.saveObjects(user, wsi, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer(name), data,
						type, meta, prov, hide))).get(0);
	}

	protected void failClone(WorkspaceUser user, WorkspaceIdentifier wsi,
			String name, Map<String, String> meta, Exception e) {
		try {
			ws.cloneWorkspace(user, wsi, name, false, null, meta);
			fail("expected clone to fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}

	protected void failObjRename(WorkspaceUser user, ObjectIdentifier oi,
			String newname, Exception e) {
		try {
			ws.renameObject(user, oi, newname);
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}

	protected void failWSRename(WorkspaceUser user, WorkspaceIdentifier wsi, String newname,
			Exception e) {
		try {
			ws.renameWorkspace(user, wsi, newname);
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	protected void failGetWorkspaceDesc(WorkspaceUser user, WorkspaceIdentifier wsi,
			Exception e) throws Exception {
		try {
			ws.getWorkspaceDescription(user, wsi);
			fail("got ws desc when should fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	protected void failSetGlobalPerm(WorkspaceUser user, WorkspaceIdentifier wsi,
			Permission perm, Exception e) throws Exception {
		try {
			ws.setGlobalPermission(user, wsi, perm);
			fail("set global perms when should fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	protected void failSetHide(WorkspaceUser user, ObjectIdentifier oi, boolean hide,
			Exception e) throws Exception {
		try {
			ws.setObjectsHidden(user, Arrays.asList(oi), hide);
			fail("un/hid obj when should fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	//checks exact dates
	protected void checkWSInfoList(List<WorkspaceInformation> ws,
			List<WorkspaceInformation> expected) {
		Map<WorkspaceInformation, Boolean> m =
				new HashMap<WorkspaceInformation, Boolean>();
		for (WorkspaceInformation wi: expected) {
			m.put(wi, false);
		}
		checkWSInfoList(ws, m);
	}
	
	protected void checkWSInfoList(List<WorkspaceInformation> ws,
			Map<WorkspaceInformation, Boolean> expected) {
		Map<Long, WorkspaceInformation> idToInf = new HashMap<Long, WorkspaceInformation>();
		for (WorkspaceInformation wi: expected.keySet()) {
			idToInf.put(wi.getId(), wi);
		}
		Set<Long> got = new HashSet<Long>();
		for (WorkspaceInformation wi: ws) {
			if (got.contains(wi.getId())) {
				fail("Same workspace listed twice");
			}
			got.add(wi.getId());
			if (!idToInf.containsKey(wi.getId())) {
				System.out.println(expected);
				System.out.println(ws);
				System.out.println(got);
				fail("got id " + wi.getId() + ", but not in expected: " + wi);
			}
			if (!expected.get(idToInf.get(wi.getId()))) {
				assertThat("workspace correct", wi, is(idToInf.get(wi.getId())));
			} else {
				compareWorkspaceInfoLessTimeStamp(wi, idToInf.get(wi.getId()));
			}
		}
		assertThat("listed correct workspaces", got, is(idToInf.keySet()));
	}

	protected void compareWorkspaceInfoLessTimeStamp(WorkspaceInformation got,
			WorkspaceInformation expected) {
		assertThat("ws id correct", got.getId(), is(expected.getId()));
		assertDateisRecent(got.getModDate());
		assertThat("ws owner correct", got.getOwner(), is(expected.getOwner()));
		assertThat("ws name correct", got.getName(), is(expected.getName()));
		assertThat("ws max obj correct", got.getApproximateObjects(), is(expected.getApproximateObjects()));
		assertThat("ws permissions correct", got.getUserPermission(), is(expected.getUserPermission()));
		assertThat("ws global read correct", got.isGloballyReadable(), is(expected.isGloballyReadable()));
		assertThat("ws lockstate correct", got.getLockState(), is(expected.getLockState()));
		
	}
	
	protected void checkObjectPagination(WorkspaceUser user, WorkspaceIdentifier wsi,
			int skip, int limit, int minid, int maxid) 
			throws Exception {
		List<ObjectInformation> res = ws.listObjects(user, Arrays.asList(wsi), null, null, null, 
				null, null, null, false, false, false, false, false, false, skip, limit);
		assertThat("correct number of objects returned", res.size(), is(maxid - minid + 1));
		for (ObjectInformation oi: res) {
			if (oi.getObjectId() < minid || oi.getObjectId() > maxid) {
				fail(String.format("ObjectID out of test bounds: %s min %s max %s",
						oi.getObjectId(), minid, maxid));
			}
		}
	}
	
	protected void failGetObjectHistory(WorkspaceUser user,
			ObjectIdentifier oi, Exception e) {
		try {
			ws.getObjectHistory(user, oi);
			fail("listed obj hist when should fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}

	protected List<ObjectInformation> setUpListObjectsExpected(List<ObjectInformation> expec,
			ObjectInformation possNull) {
		return setUpListObjectsExpected(expec, Arrays.asList(possNull));
	}
	
	protected List<ObjectInformation> setUpListObjectsExpected(List<ObjectInformation> expec,
			List<ObjectInformation> possNull) {
		List<ObjectInformation> ret = new LinkedList<ObjectInformation>(expec);
		for (ObjectInformation oi: possNull) {
			if (oi != null) {
				ret.add(oi);
			}
		}
		return ret;
		
	}

	protected void failListObjects(WorkspaceUser user,
			List<WorkspaceIdentifier> wsis, TypeDefId type, Map<String, String> meta,
			boolean showHidden, boolean showDeleted, boolean showAllDeleted,
			boolean showAllVers, boolean includeMetaData,
			Exception e) {
		try {
			ws.listObjects(user, wsis, type, null, null, meta, null, null, showHidden, showDeleted, showAllDeleted,
					showAllVers, includeMetaData, false, -1, -1);
			fail("listed obj when should fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	protected void compareObjectInfo(List<ObjectInformation> got,
			List<ObjectInformation> expected) {
		HashSet<ObjectInformation> g = new HashSet<ObjectInformation>();
		for (ObjectInformation oi: got) {
			if (g.contains(oi)) {
				fail("Got same object info twice: " + oi);
			}
			g.add(oi);
		}
		assertThat("listed correct objects", g, is(new HashSet<ObjectInformation>(expected)));
	}
	
	protected void failGetSubset(WorkspaceUser user, List<SubObjectIdentifier> objs,
			Exception e)
			throws Exception {
		try {
			ws.getObjectsSubSet(user, objs);
			fail("got subobjs obj when should fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	protected void failGetReferencedObjects(WorkspaceUser user, List<ObjectChain> chains,
			Exception e) throws Exception {
		try {
			ws.getReferencedObjects(user, chains);
			fail("called getReferencedObjects with bad args");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	protected void checkReferencedObject(WorkspaceUser user, ObjectChain chain,
			ObjectInformation oi, Provenance p, Map<String, ? extends Object> data,
			List<String> refs, Map<String, String> refmap) throws Exception {
		WorkspaceObjectData wod = ws.getReferencedObjects(user,
				Arrays.asList(chain)).get(0);
		compareObjectAndInfo(wod, oi, p, data, refs, refmap);
	}
	
	protected void failCreateObjectChain(ObjectIdentifier oi, List<ObjectIdentifier> chain,
			Exception e) {
		try {
			new ObjectChain(oi, chain);
			fail("bad args to object chain");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
		ObjectIDResolvedWS roi = oi == null ? null : oi.resolveWorkspace(new FakeResolvedWSID(1));
		
		List<ObjectIDResolvedWS> loi = null;
		if (chain != null) {
			loi = new LinkedList<ObjectIDResolvedWS>();
			for (ObjectIdentifier o: chain) {
				loi.add(o == null ? null : o.resolveWorkspace(new FakeResolvedWSID(1)));
			}
		}
		try {
			new ObjectChainResolvedWS(roi, loi);
			fail("bad args to resolved object chain");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
		
		
	}
	
	protected Set<ObjectInformation> oiset(ObjectInformation... ois) {
		return new HashSet<ObjectInformation>(Arrays.asList(ois));
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> createData(String json)
			throws JsonParseException, JsonMappingException, IOException {
		return new ObjectMapper().readValue(json, Map.class);
	}
}