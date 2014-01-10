package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.StringReader;
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
import java.util.Map.Entry;
import java.util.Set;

import junit.framework.Assert;

import org.apache.commons.lang3.text.WordUtils;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DB;

import us.kbase.common.test.TestException;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.db.FuncDetailedInfo;
import us.kbase.typedobj.db.ModuleDefId;
import us.kbase.typedobj.db.TypeDetailedInfo;
import us.kbase.typedobj.exceptions.NoSuchFuncException;
import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.typedobj.exceptions.TypedObjectValidationException;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.DefaultReferenceParser;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Provenance.ProvenanceAction;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.ShockBackend;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;
import us.kbase.workspace.kbase.Util;
import us.kbase.workspace.lib.ModuleInfo;
import us.kbase.workspace.lib.WorkspaceSaveObject;
import us.kbase.workspace.lib.Workspace;
import us.kbase.workspace.test.WorkspaceTestCommon;

//TODO make sure ordered lists stay ordered
@RunWith(Parameterized.class)
public class TestWorkspace {
	
	//true if no net access since shock requires access to globus to work
	private static final boolean SKIP_SHOCK = false;

	private static final ObjectMapper mapper = new ObjectMapper();
	
	private static final String LONG_TEXT_PART = "Passersby were amazed by the unusually large amounts of blood. ";
	private static String LONG_TEXT = "";
	static {
		for (int i = 0; i < 17; i++) {
			LONG_TEXT += LONG_TEXT_PART;
		}
	}
	private static String TEXT100 = "";
	static {
		for (int i = 0; i < 10; i++) {
			TEXT100 += "aaaaabbbbb";
		}
	}
	private static String TEXT101 = TEXT100 + "f";
	private static String TEXT1000 = "";
	static {
		for (int i = 0; i < 10; i++) {
			TEXT1000 += TEXT100;
		}
	}
	
	private static ShockBackend sbe = null;
	
	private static final WorkspaceUser SOMEUSER = new WorkspaceUser("auser");
	private static final WorkspaceUser AUSER = new WorkspaceUser("a");
	private static final WorkspaceUser BUSER = new WorkspaceUser("b");
	private static final WorkspaceUser CUSER = new WorkspaceUser("c");
	private static final AllUsers STARUSER = new AllUsers('*');
	
	private static final WorkspaceIdentifier lockWS = new WorkspaceIdentifier("lock");
	
	private static final TypeDefId SAFE_TYPE1 =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);
	private static final TypeDefId SAFE_TYPE2 =
			new TypeDefId(new TypeDefName("SomeModule", "AType2"), 0, 1);
	private static final TypeDefId SAFE_TYPE1_10 =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 1, 0);
	private static final TypeDefId SAFE_TYPE2_10 =
			new TypeDefId(new TypeDefName("SomeModule", "AType2"), 1, 0);
	private static final TypeDefId SAFE_TYPE1_20 =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 2, 0);
	private static final TypeDefId SAFE_TYPE2_20 =
			new TypeDefId(new TypeDefName("SomeModule", "AType2"), 2, 0);
	private static final TypeDefId SAFE_TYPE2_21 =
			new TypeDefId(new TypeDefName("SomeModule", "AType2"), 2, 1);

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
	}
	
	private static final Map<String, Workspace> configs =
			new HashMap<String, Workspace>();
	private final Workspace ws;
	
	public TestWorkspace(String config) throws Exception {
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

		WorkspaceDatabase wsdb = null;
		if (mUser != null) {
			wsdb = new MongoWorkspaceDB(host, db1, shockpwd, mUser, mPwd,
					kidlpath, null);
		} else {
			wsdb = new MongoWorkspaceDB(host, db1, shockpwd, "foo", "foo",
					kidlpath, null);
		}
		Workspace work = new Workspace(wsdb, new DefaultReferenceParser());
		assertTrue("Backend setup failed", work.getBackendType().equals(WordUtils.capitalize(type)));
		installSpecs(work);
		if ("shock".equals(type)) {
			sbe = new ShockBackend(db, "shock_",
					new URL(WorkspaceTestCommon.getShockUrl()), shockuser, shockpwd);
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
						"reference ref;" +
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
				"module UnreleasedModule {/* @optional thing */ typedef structure {string thing;} AType;};",
				Arrays.asList("AType"), null, null, false, null);
	}
	
	private static void printMem(String startmsg) {
		System.out.println(startmsg);
		System.out.println("free mem: " + Runtime.getRuntime().freeMemory());
		System.out.println(" max mem: " + Runtime.getRuntime().maxMemory());
		System.out.println(" ttl mem: " + Runtime.getRuntime().maxMemory());
	}
	
	@Test
	public void testWorkspaceDescription() throws Exception {
		WorkspaceInformation ltinfo = ws.createWorkspace(SOMEUSER, "lt", false, LONG_TEXT);
		WorkspaceInformation ltpinfo = ws.createWorkspace(SOMEUSER, "ltp", false, LONG_TEXT_PART);
		WorkspaceInformation ltninfo = ws.createWorkspace(SOMEUSER, "ltn", false, null);
		String desc = ws.getWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("lt"));
		assertThat("Workspace description incorrect", desc, is(LONG_TEXT.substring(0, 1000)));
		desc = ws.getWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("ltp"));
		assertThat("Workspace description incorrect", desc, is(LONG_TEXT_PART));
		desc = ws.getWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("ltn"));
		assertNull("Workspace description incorrect", desc);
		
		ws.setWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("lt"), LONG_TEXT_PART);
		ws.setWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("ltp"), null);
		ws.setWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("ltn"), LONG_TEXT);
		
		WorkspaceInformation ltinfo2 = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier("lt"));
		WorkspaceInformation ltpinfo2 = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier("ltp"));
		WorkspaceInformation ltninfo2 = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier("ltn"));
		
		assertTrue("date updated on set ws desc", ltinfo2.getModDate().after(ltinfo.getModDate()));
		assertTrue("date updated on set ws desc", ltpinfo2.getModDate().after(ltpinfo.getModDate()));
		assertTrue("date updated on set ws desc", ltninfo2.getModDate().after(ltninfo.getModDate()));
		
		desc = ws.getWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("lt"));
		assertThat("Workspace description incorrect", desc, is(LONG_TEXT_PART));
		desc = ws.getWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("ltp"));
		assertNull("Workspace description incorrect", desc);
		desc = ws.getWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("ltn"));
		assertThat("Workspace description incorrect", desc, is(LONG_TEXT.substring(0, 1000)));
		
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("lt");
		failSetWSDesc(AUSER, wsi, "foo", new WorkspaceAuthorizationException(
				"User a may not set description on workspace lt"));
		failSetWSDesc(null, wsi, "foo", new WorkspaceAuthorizationException(
				"Anonymous users may not set description on workspace lt"));
		ws.setPermissions(SOMEUSER, wsi, Arrays.asList(AUSER), Permission.WRITE);
		failSetWSDesc(AUSER, wsi, "foo", new WorkspaceAuthorizationException(
				"User a may not set description on workspace lt"));
		ws.setPermissions(SOMEUSER, wsi, Arrays.asList(AUSER), Permission.ADMIN);
		ws.setWorkspaceDescription(AUSER, wsi, "wooga");
		assertThat("ws desc ok", ws.getWorkspaceDescription(SOMEUSER, wsi), is("wooga"));
		
		ws.setWorkspaceDeleted(SOMEUSER, wsi, true);
		failSetWSDesc(SOMEUSER, wsi, "foo", new NoSuchWorkspaceException(
				"Workspace lt is deleted", wsi));
		ws.setWorkspaceDeleted(SOMEUSER, wsi, false);
		failSetWSDesc(SOMEUSER, new WorkspaceIdentifier("ltfake"), "foo", new NoSuchWorkspaceException(
				"No workspace with name ltfake exists", wsi));
		
		try {
			ws.getWorkspaceDescription(BUSER, wsi);
			fail("Got ws desc w/o read perms");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("exception message ok", e.getLocalizedMessage(),
					is("User b may not read workspace lt"));
		}
		for (Permission p: Permission.values()) {
			if (p.compareTo(Permission.NONE) <= 0 || p.compareTo(Permission.OWNER) >= 0) {
				continue;
			}
			ws.setPermissions(SOMEUSER, wsi, Arrays.asList(BUSER), p);
			ws.getWorkspaceDescription(BUSER, wsi); //will fail if perms are wrong
		}
		
		ws.lockWorkspace(SOMEUSER, wsi);
		failSetWSDesc(SOMEUSER, wsi, "foo", new WorkspaceAuthorizationException(
				"The workspace with id " + ltinfo.getId() + ", name lt, is locked and may not be modified"));
	
	}
	
	private void failSetWSDesc(WorkspaceUser user,
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

	private void checkWSInfo(WorkspaceIdentifier wsi, WorkspaceUser owner, String name,
			long objs, Permission perm, boolean globalread, long id, Date moddate,
			String lockstate) throws Exception {
		checkWSInfo(ws.getWorkspaceInformation(owner, wsi), owner, name, objs,
				perm, globalread, id, moddate, lockstate);
	}
	
	private void checkWSInfo(WorkspaceInformation info, WorkspaceUser owner, String name,
			long objs, Permission perm, boolean globalread, long id, Date moddate,
			String lockstate) {
		checkWSInfo(info, owner, name, objs, perm, globalread, lockstate);
		assertThat("ws id correct", info.getId(), is(id));
		assertThat("ws mod date correct", info.getModDate(), is(moddate));
	}
	
	private void checkWSInfo(WorkspaceInformation info, WorkspaceUser owner, String name,
			long objs, Permission perm, boolean globalread, String lockstate) {
		assertDateisRecent(info.getModDate());
		assertThat("ws owner correct", info.getOwner(), is(owner));
		assertThat("ws name correct", info.getName(), is(name));
		assertThat("ws max obj correct", info.getApproximateObjects(), is(objs));
		assertThat("ws permissions correct", info.getUserPermission(), is(perm));
		assertThat("ws global read correct", info.isGloballyReadable(), is(globalread));
		assertThat("ws lockstate correct", info.getLockState(), is(lockstate));
	}
	
	@Test
	public void testCreateWorkspaceAndGetMeta() throws Exception {
		WorkspaceInformation info = ws.createWorkspace(SOMEUSER, "foo", false, "eeswaffertheen");
		checkWSInfo(info, SOMEUSER, "foo", 0, Permission.OWNER, false, "unlocked");
		long id = info.getId();
		WorkspaceIdentifier wsi = new WorkspaceIdentifier(id);
		Date moddate = info.getModDate();
		info = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier(id));
		checkWSInfo(info, SOMEUSER, "foo", 0, Permission.OWNER, false, id, moddate, "unlocked");
		info = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier("foo"));
		checkWSInfo(info, SOMEUSER, "foo", 0, Permission.OWNER, false, id, moddate, "unlocked");
		
		try {
			ws.getWorkspaceInformation(BUSER, wsi);
			fail("Got metadata w/o read perms");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("exception message ok", e.getLocalizedMessage(),
					is("User b may not read workspace " + id));
		}
		for (Permission p: Permission.values()) {
			if (p.compareTo(Permission.NONE) <= 0 || p.compareTo(Permission.OWNER) >= 0) {
				continue;
			}
			ws.setPermissions(SOMEUSER, wsi, Arrays.asList(BUSER), p);
			ws.getWorkspaceInformation(BUSER, wsi); //will fail if perms are wrong
		}
		
		WorkspaceUser anotheruser = new WorkspaceUser("anotherfnuser");
		info = ws.createWorkspace(anotheruser, "anotherfnuser:MrT", true, "Ipitythefoolthatdon'teatMrTbreakfastcereal");
		checkWSInfo(info, anotheruser, "anotherfnuser:MrT", 0, Permission.OWNER, true, "unlocked");
		id = info.getId();
		moddate = info.getModDate();
		info = ws.getWorkspaceInformation(anotheruser, new WorkspaceIdentifier(id));
		checkWSInfo(info, anotheruser, "anotherfnuser:MrT", 0, Permission.OWNER, true, id, moddate, "unlocked");
		info = ws.getWorkspaceInformation(anotheruser, new WorkspaceIdentifier("anotherfnuser:MrT"));
		checkWSInfo(info, anotheruser, "anotherfnuser:MrT", 0, Permission.OWNER, true, id, moddate, "unlocked");
		
		ws.setGlobalPermission(anotheruser, new WorkspaceIdentifier("anotherfnuser:MrT"), Permission.NONE);
	}
	
	@Test
	public void testCreateWorkspaceAndWorkspaceIdentifierWithBadInput()
			throws Exception {
		class TestRig {
			public final WorkspaceUser user;
			public final String wsname;
			public final String excep;
			public TestRig(WorkspaceUser user, String wsname, String exception) {
				this.user = user;
				this.wsname = wsname;
				this.excep = exception;
			}
		}
		WorkspaceUser crap = new WorkspaceUser("afaeaafe");
		List<TestRig> userWS = new ArrayList<TestRig>();
		//test a few funny chars in the ws name
		userWS.add(new TestRig(crap, "afe_aff*afea",
				"Illegal character in workspace name afe_aff*afea: *"));
		userWS.add(new TestRig(crap, "afe_aff-afea",
				"Illegal character in workspace name afe_aff-afea: -"));
		userWS.add(new TestRig(crap, "afeaff/af*ea",
				"Illegal character in workspace name afeaff/af*ea: /"));
		userWS.add(new TestRig(crap, "af?eaff*afea",
				"Illegal character in workspace name af?eaff*afea: ?"));
		//check missing ws name
		userWS.add(new TestRig(crap, null,
				"Workspace name cannot be null or the empty string"));
		userWS.add(new TestRig(crap, "",
				"Workspace name cannot be null or the empty string"));
		//check long names
		userWS.add(new TestRig(crap, TEXT101,
				"Workspace name exceeds the maximum length of 100"));
		//check missing user and/or workspace name in compound name
		userWS.add(new TestRig(crap, ":",
				"Workspace name missing from :"));
		userWS.add(new TestRig(crap, "foo:",
				"Workspace name missing from foo:"));
		userWS.add(new TestRig(crap, ":foo",
				"User name missing from :foo"));
		//check multiple delims
		userWS.add(new TestRig(crap, "foo:a:foo",
				"Workspace name foo:a:foo may only contain one : delimiter"));
		userWS.add(new TestRig(crap, "foo::foo",
				"Workspace name foo::foo may only contain one : delimiter"));
		
		for (TestRig testdata: userWS) {
			String wksps = testdata.wsname;
			try {
				new WorkspaceIdentifier(wksps);
				fail(String.format("able to create workspace identifier with illegal input ws %s",
						wksps));
			} catch (IllegalArgumentException e) {
				assertThat("incorrect exception message", e.getLocalizedMessage(),
						is(testdata.excep));
			}
		}
		
		//check missing user
		userWS.add(new TestRig(null, "foo",
				"user cannot be null"));
		//user must match prefix
		userWS.add(new TestRig(SOMEUSER, "notauser:foo", 
				"Workspace name notauser:foo must only contain the user name "
				+ SOMEUSER.getUser() + " prior to the : delimiter"));
		
		for (TestRig testdata: userWS) {
			WorkspaceUser user = testdata.user;
			String wksps = testdata.wsname;
			try {
				ws.createWorkspace(user, wksps, false, "iswaffertheen");
				fail(String.format("able to create workspace with illegal input user: %s ws %s",
						user, wksps));
			} catch (IllegalArgumentException e) {
				assertThat("incorrect exception message", e.getLocalizedMessage(),
						is(testdata.excep));
			}
			try {
				new WorkspaceIdentifier(wksps, user);
				fail(String.format("able to create workspace identifier with illegal input user: %s ws %s",
						user, wksps));
			} catch (IllegalArgumentException e) {
				assertThat("incorrect exception message", e.getLocalizedMessage(),
						is(testdata.excep));
			}
		}
	}
	
	@Test
	public void preExistingWorkspace() throws Exception {
		ws.createWorkspace(AUSER, "preexist", false, null);
		try {
			ws.createWorkspace(BUSER, "preexist", false, null);
			fail("able to create same workspace twice");
		} catch (PreExistingWorkspaceException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Workspace preexist already exists"));
		}
	}
	
	@Test
	public void createIllegalUser() throws Exception {
		try {
			new WorkspaceUser("*");
			fail("able to create user with illegal character");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Illegal character in user name *: *"));
		}
		try {
			new WorkspaceUser(null);
			fail("able to create user with null");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Username cannot be null or the empty string"));
		}
		try {
			new WorkspaceUser("");
			fail("able to create user with empty string");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Username cannot be null or the empty string"));
		}
		try {
			new WorkspaceUser(TEXT101);
			fail("able to create user with long string");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Username exceeds the maximum length of 100"));
		}
		try {
			new AllUsers('$');
			fail("able to create AllUser with illegal char");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Disallowed character: $"));
		}
		
	}
	
	@Test
	public void workspacePermissions() throws Exception {
		//setup
		WorkspaceIdentifier wsiNG = new WorkspaceIdentifier("perms_noglobal");
		ws.createWorkspace(AUSER, "perms_noglobal", false, null);
		WorkspaceIdentifier wsiGL = new WorkspaceIdentifier("perms_global");
		ws.createWorkspace(AUSER, "perms_global", true, "globaldesc");
		Map<User, Permission> expect = new HashMap<User, Permission>();
		
		//try some illegal ops
		try {
			ws.getWorkspaceDescription(null, wsiNG);
			fail("Able to get private workspace description with no user name");
		} catch (Exception e) {
			assertThat("Correct exception message", e.getLocalizedMessage(),
					is("Anonymous users may not read workspace perms_noglobal"));
		}
		try {
			ws.getWorkspaceInformation(null, wsiNG);
			fail("Able to get private workspace metadata with no user name");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("Correct exception message", e.getLocalizedMessage(),
					is("Anonymous users may not read workspace perms_noglobal"));
		}
		try {
			ws.setPermissions(AUSER, wsiNG, Arrays.asList(AUSER, BUSER, CUSER), Permission.OWNER);
			fail("was able to set owner permissions");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Cannot set owner permission"));
		}
		try {
			ws.setPermissions(BUSER, wsiNG, Arrays.asList(AUSER, BUSER, CUSER), Permission.READ);
			fail("was able to set permissions with unauth'd username");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("User b may not set permissions on workspace perms_noglobal"));
		}
		//check basic permissions for new private and public workspaces
		expect.put(AUSER, Permission.OWNER);
		assertThat("ws has correct perms for owner", ws.getPermissions(AUSER, wsiNG), is(expect));
		expect.put(STARUSER, Permission.READ);
		assertThat("ws has correct perms for owner", ws.getPermissions(AUSER, wsiGL), is(expect));
		expect.clear();
		expect.put(BUSER, Permission.NONE);
		assertThat("ws has correct perms for random user", ws.getPermissions(BUSER, wsiNG), is(expect));
		expect.put(STARUSER, Permission.READ);
		assertThat("ws has correct perms for random user", ws.getPermissions(BUSER, wsiGL), is(expect));
		//test read permissions
		assertThat("can read public workspace description", ws.getWorkspaceDescription(null, wsiGL),
				is("globaldesc"));
		WorkspaceInformation info= ws.getWorkspaceInformation(null, wsiGL);
		checkWSInfo(info, AUSER, "perms_global", 0, Permission.NONE, true, "unlocked");
		ws.setPermissions(AUSER, wsiNG, Arrays.asList(AUSER, BUSER, CUSER), Permission.READ);
		expect.clear();
		expect.put(AUSER, Permission.OWNER);
		expect.put(BUSER, Permission.READ);
		expect.put(CUSER, Permission.READ);
		assertThat("ws doesn't replace owner perms", ws.getPermissions(AUSER, wsiNG), is(expect));
		expect.clear();
		expect.put(BUSER, Permission.READ);
		assertThat("no permission leakage", ws.getPermissions(BUSER, wsiNG), is(expect));
		try {
			ws.setPermissions(BUSER, wsiNG, Arrays.asList(AUSER, BUSER, CUSER), Permission.READ);
			fail("was able to set permissions with unauth'd username");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("User b may not set permissions on workspace perms_noglobal"));
		}
		//test write permissions
		ws.setPermissions(AUSER, wsiNG, Arrays.asList(BUSER), Permission.WRITE);
		expect.put(AUSER, Permission.OWNER);
		expect.put(BUSER, Permission.WRITE);
		expect.put(CUSER, Permission.READ);
		assertThat("ws doesn't replace owner perms", ws.getPermissions(AUSER, wsiNG), is(expect));
		expect.clear();
		expect.put(BUSER, Permission.WRITE);
		assertThat("no permission leakage", ws.getPermissions(BUSER, wsiNG), is(expect));
		try {
			ws.setPermissions(BUSER, wsiNG, Arrays.asList(AUSER, BUSER, CUSER), Permission.READ);
			fail("was able to set permissions with unauth'd username");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("User b may not set permissions on workspace perms_noglobal"));
		}
		//test admin permissions
		ws.setPermissions(AUSER, wsiNG, Arrays.asList(BUSER), Permission.ADMIN);
		expect.put(AUSER, Permission.OWNER);
		expect.put(BUSER, Permission.ADMIN);
		expect.put(CUSER, Permission.READ);
		assertThat("ws doesn't replace owner perms", ws.getPermissions(AUSER, wsiNG), is(expect));
		assertThat("admin can see all perms", ws.getPermissions(BUSER, wsiNG), is(expect));
		ws.setPermissions(BUSER, wsiNG, Arrays.asList(AUSER, CUSER), Permission.WRITE);
		expect.put(CUSER, Permission.WRITE);
		assertThat("ws doesn't replace owner perms", ws.getPermissions(AUSER, wsiNG), is(expect));
		assertThat("admin can correctly set perms", ws.getPermissions(BUSER, wsiNG), is(expect));
		//test remove permissions
		ws.setPermissions(BUSER, wsiNG, Arrays.asList(AUSER, CUSER), Permission.NONE);
		expect.remove(CUSER);
		assertThat("ws doesn't replace owner perms", ws.getPermissions(AUSER, wsiNG), is(expect));
		assertThat("admin can't overwrite owner perms", ws.getPermissions(BUSER, wsiNG), is(expect));
		
		ws.setGlobalPermission(AUSER, new WorkspaceIdentifier("perms_global"), Permission.NONE);
	}
	
	private void checkObjInfo(ObjectInformation info, long id,
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
	
	private void assertDateisRecent(Date orig) {
		Date now = new Date();
		int onemin = 1000 * 60;
		assertTrue("date is recent", now.getTime() - orig.getTime() < onemin);
	}
	
	@Test
	public void saveObjectsAndGetMetaSimple() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceUser bar = new WorkspaceUser("bar");
		WorkspaceIdentifier read = new WorkspaceIdentifier("saveobjread");
		WorkspaceIdentifier priv = new WorkspaceIdentifier("saveobj");
		ws.createWorkspace(foo, read.getIdentifierString(), true, null);
		ws.createWorkspace(foo, priv.getIdentifierString(), false, null);
		long readid = ws.getWorkspaceInformation(foo, read).getId();
		long privid = ws.getWorkspaceInformation(foo, priv).getId();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> data2 = new HashMap<String, Object>();
		Map<String, String> meta = new HashMap<String, String>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		JsonNode savedata = mapper.valueToTree(data);
		data2.put("fubar2", moredata);
		JsonNode savedata2 = mapper.valueToTree(data2);
		meta.put("metastuff", "meta");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("meta2", "my hovercraft is full of eels");
		Provenance p = new Provenance(new WorkspaceUser("kbasetest2"));
		p.addAction(new Provenance.ProvenanceAction().withServiceName("some service"));
		List<WorkspaceSaveObject> objects = new ArrayList<WorkspaceSaveObject>();
		
		try {
			ws.saveObjects(foo, read, objects);
			fail("Saved no objects");
		} catch (IllegalArgumentException e) {
			assertThat("correct except", e.getLocalizedMessage(), is("No data provided"));
		}
		
		try {
			ws.getObjects(foo, new ArrayList<ObjectIdentifier>());
			fail("called method with no identifiers");
		} catch (IllegalArgumentException e) {
			assertThat("correct except", e.getLocalizedMessage(), is("No object identifiers provided"));
		}
		
		try {
			ws.getObjectInformation(foo, new ArrayList<ObjectIdentifier>(), true);
			fail("called method with no identifiers");
		} catch (IllegalArgumentException e) {
			assertThat("correct except", e.getLocalizedMessage(), is("No object identifiers provided"));
		}
		
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto3"), savedata, SAFE_TYPE1, meta, p, false));
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto3"), savedata2, SAFE_TYPE1, meta2, p, false));
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto3-1"), savedata, SAFE_TYPE1, meta, p, false));
		objects.add(new WorkspaceSaveObject(savedata2, SAFE_TYPE1, meta2, p, false));
		objects.add(new WorkspaceSaveObject(savedata, SAFE_TYPE1, meta, p, false));
		List<ObjectInformation> objinfo = ws.saveObjects(foo, read, objects);
		String chksum1 = "36c4f68f2c98971b9736839232eb08f4";
		String chksum2 = "3c59f762140806c36ab48a152f28e840";
		checkObjInfo(objinfo.get(0), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(objinfo.get(1), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo.get(2), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(objinfo.get(3), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo.get(4), 4, "auto4", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		
		List<ObjectIdentifier> loi = new ArrayList<ObjectIdentifier>();
		loi.add(new ObjectIdentifier(read, 1));
		loi.add(new ObjectIdentifier(read, 1, 1));
		loi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), "auto3"));
		loi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), "auto3", 1));
		loi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), 1));
		loi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), 1, 1));
		loi.add(new ObjectIdentifier(read, "auto3"));
		loi.add(new ObjectIdentifier(read, "auto3", 1));
		loi.add(new ObjectIdentifier(read, "auto3-2"));
		loi.add(new ObjectIdentifier(read, 3));
		loi.add(new ObjectIdentifier(read, "auto3-2", 1));
		loi.add(new ObjectIdentifier(read, 3, 1));
		List<WorkspaceObjectData> retdata = ws.getObjects(foo, loi);
		List<ObjectInformation> objinfo2 = ws.getObjectInformation(foo, loi, true);
		List<ObjectInformation> objinfo2NoMeta = ws.getObjectInformation(foo, loi, false);
		checkObjInfo(objinfo2.get(0), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2.get(1), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(objinfo2.get(2), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2.get(3), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(objinfo2.get(4), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2.get(5), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(objinfo2.get(6), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2.get(7), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(objinfo2.get(8), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2.get(9), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2.get(10), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2.get(11), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2NoMeta.get(0), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, null);
		checkObjInfo(objinfo2NoMeta.get(1), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, null);
		checkObjInfo(objinfo2NoMeta.get(2), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, null);
		checkObjInfo(objinfo2NoMeta.get(3), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, null);
		checkObjInfo(objinfo2NoMeta.get(4), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, null);
		checkObjInfo(objinfo2NoMeta.get(5), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, null);
		checkObjInfo(objinfo2NoMeta.get(6), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, null);
		checkObjInfo(objinfo2NoMeta.get(7), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, null);
		checkObjInfo(objinfo2NoMeta.get(8), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, null);
		checkObjInfo(objinfo2NoMeta.get(9), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, null);
		checkObjInfo(objinfo2NoMeta.get(10), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, null);
		checkObjInfo(objinfo2NoMeta.get(11), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, null);
		checkObjInfo(retdata.get(0).getObjectInfo(), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(retdata.get(1).getObjectInfo(), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(retdata.get(2).getObjectInfo(), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(retdata.get(3).getObjectInfo(), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(retdata.get(4).getObjectInfo(), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(retdata.get(5).getObjectInfo(), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(retdata.get(6).getObjectInfo(), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(retdata.get(7).getObjectInfo(), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(retdata.get(8).getObjectInfo(), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(retdata.get(9).getObjectInfo(), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(retdata.get(10).getObjectInfo(), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(retdata.get(11).getObjectInfo(), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		assertThat("correct data", retdata.get(0).getData(), is((Object) data2));
		assertThat("correct data", retdata.get(1).getData(), is((Object) data));
		assertThat("correct data", retdata.get(2).getData(), is((Object) data2));
		assertThat("correct data", retdata.get(3).getData(), is((Object) data));
		assertThat("correct data", retdata.get(4).getData(), is((Object) data2));
		assertThat("correct data", retdata.get(5).getData(), is((Object) data));
		assertThat("correct data", retdata.get(6).getData(), is((Object) data2));
		assertThat("correct data", retdata.get(7).getData(), is((Object) data));
		assertThat("correct data", retdata.get(8).getData(), is((Object) data2));
		assertThat("correct data", retdata.get(9).getData(), is((Object) data2));
		assertThat("correct data", retdata.get(10).getData(), is((Object) data2));
		assertThat("correct data", retdata.get(11).getData(), is((Object) data2));
		
		ws.saveObjects(foo, priv, objects);
		
		objects.clear();
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer(2), savedata, SAFE_TYPE1, meta2, p, false));
		objinfo = ws.saveObjects(foo, read, objects);
		ws.saveObjects(foo, priv, objects);
		checkObjInfo(objinfo.get(0), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum1, 23, meta2);
		objinfo2 = ws.getObjectInformation(foo, Arrays.asList(new ObjectIdentifier(read, 2)), true);
		checkObjInfo(objinfo2.get(0), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum1, 23, meta2);
		
		ws.getObjectInformation(bar, Arrays.asList(new ObjectIdentifier(read, 2)), true); //should work
		try {
			ws.getObjectInformation(bar, Arrays.asList(new ObjectIdentifier(priv, 2)), true);
			fail("Able to get obj meta from private workspace");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception message", ioe.getLocalizedMessage(),
					is("Object 2 cannot be accessed: User bar may not read workspace saveobj"));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is(new ObjectIdentifier(priv, 2)));
		}
		ws.getObjects(bar, Arrays.asList(new ObjectIdentifier(read, 2))); //should work
		try {
			ws.getObjects(bar, Arrays.asList(new ObjectIdentifier(priv, 2)));
			fail("Able to get obj data from private workspace");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception message", ioe.getLocalizedMessage(),
					is("Object 2 cannot be accessed: User bar may not read workspace saveobj"));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is(new ObjectIdentifier(priv, 2)));
		}
		ws.setPermissions(foo, priv, Arrays.asList(bar), Permission.READ);
		objinfo2 = ws.getObjectInformation(bar, Arrays.asList(new ObjectIdentifier(priv, 2)), true);
		checkObjInfo(objinfo2.get(0), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 2, foo, privid, priv.getName(), chksum1, 23, meta2);
		retdata = ws.getObjects(bar, Arrays.asList(new ObjectIdentifier(priv, 2)));
		checkObjInfo(retdata.get(0).getObjectInfo(), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 2, foo, privid, priv.getName(), chksum1, 23, meta2);
		assertThat("correct data", retdata.get(0).getData(), is((Object) data));
		try {
			ws.saveObjects(bar, priv, objects);
			fail("saved objects to unwritable workspace");
		} catch (WorkspaceAuthorizationException auth) {
			assertThat("correct exception message", auth.getLocalizedMessage(),
					is("User bar may not write to workspace saveobj"));
		}
		ws.setPermissions(foo, priv, Arrays.asList(bar), Permission.WRITE);
		objinfo = ws.saveObjects(bar, priv, objects);
		checkObjInfo(objinfo.get(0), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 3, bar, privid, priv.getName(), chksum1, 23, meta2);
		
		ws.setGlobalPermission(foo, read, Permission.NONE);
	}
	
	@Test
	public void saveObjectWithTypeChecking() throws Exception {
		final String specTypeCheck1 =
				"module TestTypeChecking {" +
					"/* @id ws */" +
					"typedef string reference;" +
					"/* @optional ref */" + 
					"typedef structure {" +
						"int foo;" +
						"list<int> bar;" +
						"string baz;" +
						"reference ref;" +
					"} CheckType;" +
				"};";
		
		final String specTypeCheck2 =
				"module TestTypeChecking {" +
					"/* @id ws */" +
					"typedef string reference;" +
					"/* @optional ref */" + 
					"typedef structure {" +
						"int foo;" +
						"list<int> bar;" +
						"int baz;" +
						"reference ref;" +
					"} CheckType;" +
				"};";
		
		final String specTypeCheckRefs =
				"module TestTypeCheckingRefType {" +
					"/* @id ws TestTypeChecking.CheckType */" +
					"typedef string reference;" +
					"/* @optional refmap */" +
					"typedef structure {" +
						"int foo;" +
						"list<int> bar;" +
						"string baz;" +
						"reference ref;" +
						"mapping<reference, string> refmap;" + 
					"} CheckRefType;" +
				"};";
		
		String mod = "TestTypeChecking";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		ws.requestModuleRegistration(userfoo, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(userfoo, specTypeCheck1, Arrays.asList("CheckType"), null, null, false, null);
		TypeDefId abstype0 = new TypeDefId(new TypeDefName(mod, "CheckType"), 0, 1);
		TypeDefId abstype1 = new TypeDefId(new TypeDefName(mod, "CheckType"), 1, 0);
		TypeDefId abstype2 = new TypeDefId(new TypeDefName(mod, "CheckType"), 2, 0);
		TypeDefId relmintype0 = new TypeDefId(new TypeDefName(mod, "CheckType"), 0);
		TypeDefId relmintype1 = new TypeDefId(new TypeDefName(mod, "CheckType"), 1);
		TypeDefId relmintype2 = new TypeDefId(new TypeDefName(mod, "CheckType"), 2);
		TypeDefId relmaxtype = new TypeDefId(new TypeDefName(mod, "CheckType"));
		
		// test basic type checking with different versions
		WorkspaceIdentifier wspace = new WorkspaceIdentifier("typecheck");
		ws.createWorkspace(userfoo, wspace.getName(), false, null);
		Provenance emptyprov = new Provenance(userfoo);
		Map<String, Object> data1 = new HashMap<String, Object>();
		data1.put("foo", 3);
		data1.put("baz", "astring");
		data1.put("bar", Arrays.asList(-3, 1, 234567890));
		
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(data1, abstype0, null, emptyprov, false))); //should work
		
		failSave(userfoo, wspace, data1, new TypeDefId("NoModHere.Foo"), emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nModule doesn't exist: NoModHere"));
		failSave(userfoo, wspace, data1, new TypeDefId("SomeModule.Foo"), emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: SomeModule.Foo"));
		
		failSave(userfoo, wspace, data1, relmintype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: TestTypeChecking.CheckType-0"));
		failSave(userfoo, wspace, data1, relmintype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: TestTypeChecking.CheckType-1"));
		failSave(userfoo, wspace, data1, abstype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to read type schema record: 'TestTypeChecking.CheckType'"));
		failSave(userfoo, wspace, data1, relmaxtype, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: TestTypeChecking.CheckType"));
		
		ws.releaseTypes(userfoo, mod);
		
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, relmaxtype, null, emptyprov, false)));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, abstype0, null, emptyprov, false)));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, abstype1, null, emptyprov, false)));
		failSave(userfoo, wspace, data1, relmintype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: TestTypeChecking.CheckType-0"));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, relmintype1, null, emptyprov, false)));
		failSave(userfoo, wspace, data1, relmintype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: TestTypeChecking.CheckType-2"));
		
		ws.compileNewTypeSpec(userfoo, specTypeCheck2, null, null, null, false, null);
		
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, relmaxtype, null, emptyprov, false)));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, relmintype1, null, emptyprov, false)));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, abstype0, null, emptyprov, false)));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, abstype1, null, emptyprov, false)));
		failSave(userfoo, wspace, data1, abstype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (string) does not match any allowed primitive type (allowed: [\"integer\"]), at /baz"));
		failSave(userfoo, wspace, data1, relmintype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: TestTypeChecking.CheckType-2"));
		
		
		Map<String, Object> newdata = new HashMap<String, Object>(data1);
		newdata.put("baz", 1);
		ws.saveObjects(userfoo, wspace, Arrays.asList(
					new WorkspaceSaveObject(newdata, abstype2 , null, emptyprov, false)));
		failSave(userfoo, wspace, newdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, newdata, abstype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, newdata, relmaxtype, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, newdata, relmintype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, newdata, relmintype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: TestTypeChecking.CheckType-2"));
		
		ws.releaseTypes(userfoo, mod);
		
		failSave(userfoo, wspace, data1, relmaxtype, emptyprov, 
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (string) does not match any allowed primitive type (allowed: [\"integer\"]), at /baz"));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, relmintype1, null, emptyprov, false)));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, abstype0, null, emptyprov, false)));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, abstype1, null, emptyprov, false)));
		failSave(userfoo, wspace, data1, abstype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (string) does not match any allowed primitive type (allowed: [\"integer\"]), at /baz"));
		failSave(userfoo, wspace, data1, relmintype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (string) does not match any allowed primitive type (allowed: [\"integer\"]), at /baz"));
		
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(newdata, abstype2 , null, emptyprov, false)));
		failSave(userfoo, wspace, newdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, newdata, abstype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(newdata, relmaxtype, null, emptyprov, false)));
		failSave(userfoo, wspace, newdata, relmintype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(newdata, relmintype2, null, emptyprov, false)));
		
		
		// test non-parseable references and typechecking with object count
		List<WorkspaceSaveObject> data = new ArrayList<WorkspaceSaveObject>();
		data.add(new WorkspaceSaveObject(data1, abstype0, null, emptyprov, false));
		Map<String, Object> data2 = new HashMap<String, Object>(data1);
		data2.put("bar", Arrays.asList(-3, 1, "anotherstring"));
		data.add(new WorkspaceSaveObject(data2, abstype0, null, emptyprov, false));
		try {
			ws.saveObjects(userfoo, wspace, data);
		} catch (TypedObjectValidationException tove) {
			assertThat("correct exception", tove.getLocalizedMessage(),
					is("Object #2 failed type checking:\ninstance type (string) does not match any allowed primitive type (allowed: [\"integer\"]), at /bar/2"));
		}
		Map<String, Object> data3 = new HashMap<String, Object>(data1);
		data3.put("ref", "typecheck/1/1");
		data.set(1, new WorkspaceSaveObject(data3, abstype0, null, emptyprov, false));
		ws.saveObjects(userfoo, wspace, data); //should work
		
		Map<String, Object> data4 = new HashMap<String, Object>(data1);
		data4.put("ref", "foo/bar/baz");
		data.set(1, new WorkspaceSaveObject(data4, abstype0, null, emptyprov, false));
		try {
			ws.saveObjects(userfoo, wspace, data);
		} catch (TypedObjectValidationException tove) {
			assertThat("correct exception", tove.getLocalizedMessage(),
					is("Object #2 has unparseable reference foo/bar/baz: Unable to parse version portion of object reference foo/bar/baz to an integer"));
		}
		Provenance goodids = new Provenance(userfoo);
		goodids.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("typecheck/1/1")));
		data.set(1, new WorkspaceSaveObject(data3, abstype0, null, goodids, false));
		ws.saveObjects(userfoo, wspace, data); //should work
		
		Provenance badids = new Provenance(userfoo);
		badids.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("foo/bar/baz")));
		data.set(1, new WorkspaceSaveObject(data3, abstype0, null, badids, false));
		try {
			ws.saveObjects(userfoo, wspace, data);
		} catch (TypedObjectValidationException tove) {
			assertThat("correct exception", tove.getLocalizedMessage(),
					is("Object #2 has unparseable provenance reference foo/bar/baz: Unable to parse version portion of object reference foo/bar/baz to an integer"));
		}
		
		//test inaccessible references due to missing, deleted, or unreadable workspaces
		Map<String, Object> refdata = new HashMap<String, Object>(data1);
		refdata.put("ref", "thereisnoworkspaceofthisname/2/1");
		failSave(userfoo, wspace, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 has inaccessible reference thereisnoworkspaceofthisname/2/1: Object 2 cannot be accessed: No workspace with name thereisnoworkspaceofthisname exists"));
		Provenance nowsref = new Provenance(userfoo);
		nowsref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("thereisnoworkspaceofthisname/2/1")));
		failSave(userfoo, wspace, data1, abstype0, nowsref,
				new TypedObjectValidationException(
						"Object #1 has inaccessible provenance reference thereisnoworkspaceofthisname/2/1: Object 2 cannot be accessed: No workspace with name thereisnoworkspaceofthisname exists"));
		
		ws.createWorkspace(userfoo, "tobedeleted", false, null);
		ws.setWorkspaceDeleted(userfoo, new WorkspaceIdentifier("tobedeleted"), true);
		refdata.put("ref", "tobedeleted/2/1");
		failSave(userfoo, wspace, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 has inaccessible reference tobedeleted/2/1: Object 2 cannot be accessed: Workspace tobedeleted is deleted"));
		Provenance delwsref = new Provenance(userfoo);
		delwsref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("tobedeleted/2/1")));
		failSave(userfoo, wspace, data1, abstype0, delwsref,
				new TypedObjectValidationException(
						"Object #1 has inaccessible provenance reference tobedeleted/2/1: Object 2 cannot be accessed: Workspace tobedeleted is deleted"));
		
		ws.createWorkspace(new WorkspaceUser("stingyuser"), "stingyworkspace", false, null);
		refdata.put("ref", "stingyworkspace/2/1");
		failSave(userfoo, wspace, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 has inaccessible reference stingyworkspace/2/1: Object 2 cannot be accessed: User foo may not read workspace stingyworkspace"));
		Provenance privwsref = new Provenance(userfoo);
		privwsref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("stingyworkspace/2/1")));
		failSave(userfoo, wspace, data1, abstype0, privwsref,
				new TypedObjectValidationException(
						"Object #1 has inaccessible provenance reference stingyworkspace/2/1: Object 2 cannot be accessed: User foo may not read workspace stingyworkspace"));
		
		//test inaccessible reference due to missing or deleted objects, incl bad versions
		ws.createWorkspace(userfoo, "referencetesting", false, null);
		WorkspaceIdentifier reftest = new WorkspaceIdentifier("referencetesting");
		ws.saveObjects(userfoo, reftest, Arrays.asList(
				new WorkspaceSaveObject(newdata, abstype2 , null, emptyprov, false)));
		
		refdata.put("ref", "referencetesting/1/1");
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(refdata, abstype1 , null, emptyprov, false)));
		Provenance goodref = new Provenance(userfoo);
		goodref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("referencetesting/1/1")));
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(refdata, abstype1 , null, goodref, false)));
		
		refdata.put("ref", "referencetesting/2/1");
		long refwsid = ws.getWorkspaceInformation(userfoo, reftest).getId();
		failSave(userfoo, wspace, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 has inaccessible reference referencetesting/2/1: No object with id 2 exists in workspace "
								+ refwsid));
		Provenance noobjref = new Provenance(userfoo);
		noobjref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("referencetesting/2/1")));
		failSave(userfoo, wspace, data1, abstype0, noobjref,
				new TypedObjectValidationException(
						"Object #1 has inaccessible provenance reference referencetesting/2/1: No object with id 2 exists in workspace "
								+ refwsid));
		
		ws.saveObjects(userfoo, reftest, Arrays.asList(
				new WorkspaceSaveObject(newdata, abstype2 , null, emptyprov, false)));
		ws.setObjectsDeleted(userfoo, Arrays.asList(new ObjectIdentifier(reftest, 2)), true);
		failSave(userfoo, wspace, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(String.format(
						"Object #1 has inaccessible reference referencetesting/2/1: Object 2 (name auto2) in workspace %s has been deleted",
								refwsid)));
		Provenance delobjref = new Provenance(userfoo);
		delobjref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("referencetesting/2/1")));
		failSave(userfoo, wspace, data1, abstype0, delobjref,
				new TypedObjectValidationException(String.format(
						"Object #1 has inaccessible provenance reference referencetesting/2/1: Object 2 (name auto2) in workspace %s has been deleted",
								refwsid)));
		
		refdata.put("ref", "referencetesting/1/2");
		failSave(userfoo, wspace, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 has inaccessible reference referencetesting/1/2: No object with id 1 (name auto1) and version 2 exists in workspace "
								+ refwsid));
		Provenance noverref = new Provenance(userfoo);
		noverref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("referencetesting/1/2")));
		failSave(userfoo, wspace, data1, abstype0, noverref,
				new TypedObjectValidationException(
						"Object #1 has inaccessible provenance reference referencetesting/1/2: No object with id 1 (name auto1) and version 2 exists in workspace "
								+ refwsid));
		
		//TODO test references against garbage collected objects
		
		//test reference type checking
		String refmod = "TestTypeCheckingRefType";
		ws.requestModuleRegistration(userfoo, refmod);
		ws.resolveModuleRegistration(refmod, true);
		ws.compileNewTypeSpec(userfoo, specTypeCheckRefs, Arrays.asList("CheckRefType"), null, null, false, null);
		TypeDefId absreftype0 = new TypeDefId(new TypeDefName(refmod, "CheckRefType"), 0, 1);

		ws.createWorkspace(userfoo, "referencetypecheck", false, null);
		WorkspaceIdentifier reftypecheck = new WorkspaceIdentifier("referencetypecheck");
		long reftypewsid = ws.getWorkspaceInformation(userfoo, reftypecheck).getId();
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(newdata, SAFE_TYPE1 , null, emptyprov, false)));
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(newdata, abstype2 , null, emptyprov, false)));
		
		refdata.put("ref", "referencetypecheck/2/1");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false))); //should work
		
		refdata.put("ref", "referencetypecheck/2");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false))); //should work
		
		refdata.put("ref", "referencetypecheck/auto2/1");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false))); //should work
		
		refdata.put("ref", "referencetypecheck/auto2");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false))); //should work
		
		refdata.put("ref", reftypewsid + "/2/1");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false))); //should work
		
		refdata.put("ref", reftypewsid + "/2");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false))); //should work
		
		refdata.put("ref", reftypewsid + "/auto2/1");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false))); //should work
		
		refdata.put("ref", reftypewsid + "/auto2");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false))); //should work
		
		refdata.put("ref", "referencetypecheck/1/1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1: The type SomeModule.AType-0.1 of reference referencetypecheck/1/1 contained in this object is not allowed for this object's type, TestTypeCheckingRefType.CheckRefType-0.1"));
		
		refdata.put("ref", "referencetypecheck/1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1: The type SomeModule.AType-0.1 of reference referencetypecheck/1 contained in this object is not allowed for this object's type, TestTypeCheckingRefType.CheckRefType-0.1"));
		
		refdata.put("ref", "referencetypecheck/auto1/1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1: The type SomeModule.AType-0.1 of reference referencetypecheck/auto1/1 contained in this object is not allowed for this object's type, TestTypeCheckingRefType.CheckRefType-0.1"));
		
		refdata.put("ref", "referencetypecheck/auto1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1: The type SomeModule.AType-0.1 of reference referencetypecheck/auto1 contained in this object is not allowed for this object's type, TestTypeCheckingRefType.CheckRefType-0.1"));
		
		refdata.put("ref", reftypewsid + "/1/1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1: The type SomeModule.AType-0.1 of reference " + reftypewsid + "/1/1 contained in this object is not allowed for this object's type, TestTypeCheckingRefType.CheckRefType-0.1"));
		
		refdata.put("ref", reftypewsid + "/1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1: The type SomeModule.AType-0.1 of reference " + reftypewsid + "/1 contained in this object is not allowed for this object's type, TestTypeCheckingRefType.CheckRefType-0.1"));
		
		refdata.put("ref", reftypewsid + "/auto1/1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1: The type SomeModule.AType-0.1 of reference " + reftypewsid + "/auto1/1 contained in this object is not allowed for this object's type, TestTypeCheckingRefType.CheckRefType-0.1"));
		
		refdata.put("ref", reftypewsid + "/auto1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1: The type SomeModule.AType-0.1 of reference " + reftypewsid + "/auto1 contained in this object is not allowed for this object's type, TestTypeCheckingRefType.CheckRefType-0.1"));
		
		//check references were rewritten correctly
		for (int i = 3; i < 11; i++) {
			WorkspaceObjectData wod = ws.getObjects(userfoo, Arrays.asList(
					new ObjectIdentifier(reftypecheck, i))).get(0);
			@SuppressWarnings("unchecked")
			Map<String, Object> obj = (Map<String, Object>) wod.getData();
			assertThat("reference rewritten correctly", (String) obj.get("ref"), is(reftypewsid + "/2/1"));
			assertThat("reference included correctly", wod.getReferences(),
					is(Arrays.asList(reftypewsid + "/2/1")));
		}
		
		//test the edge case where two keys in a hash resolve to the same reference
		refdata.put("ref", "referencetypecheck/2/1");
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("referencetypecheck/2/1", "pootypoot");
		refmap.put("referencetypecheck/auto2/1", "pootypoot");
		assertThat("refmap has 2 refs", refmap.size(), is(2));
		refdata.put("refmap", refmap);
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1: Two references in a single hash are identical when resolved, resulting in a loss of data: relabeling 'referencetypecheck/2/1' to '"
						+ reftypewsid + "/2/1' failed because the field name already exists at /refmap"));
	}
	
	private void failSave(WorkspaceUser user, WorkspaceIdentifier wsi, 
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
	
	@Test
	public void saveProvenance() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceIdentifier prov = new WorkspaceIdentifier("provenance");
		ws.createWorkspace(foo, prov.getName(), false, null);
		long wsid = ws.getWorkspaceInformation(foo, prov).getId();
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("foo", "bar");
		Provenance emptyprov = new Provenance(foo);
		
		//already tested bad references in saveObjectWithTypeChecking, won't test again here
		
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, emptyprov, false)));
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, emptyprov, false)));
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, emptyprov, false)));
		
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto1"), data, SAFE_TYPE1, null, emptyprov, false)));
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto1"), data, SAFE_TYPE1, null, emptyprov, false)));
		
		
		Provenance p = new Provenance(foo);
		p.addAction(new ProvenanceAction()
				.withCommandLine("A command line")
				.withDescription("descrip")
				.withIncomingArgs(Arrays.asList("a", "b", "c"))
				.withMethod("method")
				.withMethodParameters(Arrays.asList((Object) data, data, data))
				.withOutgoingArgs(Arrays.asList("d", "e", "f"))
				.withScript("script")
				.withScriptVersion("2.1")
				.withServiceName("service")
				.withServiceVersion("3")
				.withTime(new Date(45))
				.withWorkspaceObjects(Arrays.asList("provenance/auto3", "provenance/auto1/2")));
		p.addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList("provenance/auto2/1", "provenance/auto1")));
		
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, p, false)));
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("provenance/auto3", wsid + "/3/1");
		refmap.put("provenance/auto1/2", wsid + "/1/2");
		refmap.put("provenance/auto2/1", wsid + "/2/1");
		refmap.put("provenance/auto1", wsid + "/1/3");
		
		Provenance got = ws.getObjects(foo, Arrays.asList(new ObjectIdentifier(prov, 4))).get(0).getProvenance();
		checkProvenanceCorrect(p, got, refmap);
		
		try {
			new WorkspaceSaveObject(data, SAFE_TYPE1, null, null, false);
			fail("saved without provenance");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Neither data, provenance, nor type may be null"));
		}
		try {
			new WorkspaceSaveObject(new ObjectIDNoWSNoVer("foo"), SAFE_TYPE1, null, null, false);
			fail("saved without provenance");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Neither data, provenance, nor type may be null"));
		}
		try {
			new Provenance(null);
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("user cannot be null"));
		}
		try {
			Provenance pv = new Provenance(foo);
			pv.addAction(null);
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("action cannot be null"));
		}
		
		//Test minimal provenance
		Provenance p2 = new Provenance(foo);
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, p2, false)));
		Provenance got2 = ws.getObjects(foo, Arrays.asList(new ObjectIdentifier(prov, 5))).get(0).getProvenance();
		Date date2 = got2.getDate();
		checkProvenanceCorrect(p2, got2, new HashMap<String, String>());
		got2 = ws.getObjects(foo, Arrays.asList(new ObjectIdentifier(prov, 5))).get(0).getProvenance();
		assertThat("Prov date constant", got2.getDate(), is(date2));
		//make sure passing nulls for ws obj lists doesn't kill anything
		Provenance p3 = new Provenance(foo);
		p3.addAction(new ProvenanceAction().withWorkspaceObjects(null));
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, p3, false)));
		Provenance got3 = ws.getObjects(foo, Arrays.asList(new ObjectIdentifier(prov, 6))).get(0).getProvenance();
		checkProvenanceCorrect(p3, got3, new HashMap<String, String>());
		
		Provenance p4 = new Provenance(foo);
		ProvenanceAction pa = new ProvenanceAction();
		pa.setWorkspaceObjects(null);
		p4.addAction(pa);
		p3.addAction(new ProvenanceAction().withWorkspaceObjects(null));
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, p4, false)));
		Provenance got4 = ws.getObjects(foo, Arrays.asList(new ObjectIdentifier(prov, 7))).get(0).getProvenance();
		checkProvenanceCorrect(p4, got4, new HashMap<String, String>());
	}
	
	//if refmap != null expected is a Provenance object. Otherwise it's a subclass
	// with an implemented getResolvedObjects method.
	private void checkProvenanceCorrect(Provenance expected, Provenance got,
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
	
	@Test 
	public void saveLargeProvenance() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceIdentifier prov = new WorkspaceIdentifier("bigprov");
		ws.createWorkspace(foo, prov.getName(), false, null);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("foo", "bar");
		List<Object> methparams = new ArrayList<Object>();
		for (int i = 1; i < 997; i++) {
			methparams.add(TEXT1000);
		}
		Provenance p = new Provenance(foo);
		p.addAction(new ProvenanceAction().withMethodParameters(methparams));
		ws.saveObjects(foo, prov, Arrays.asList( //should work
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, p, false)));
		
		
		methparams.add(TEXT1000);
		Provenance p2 = new Provenance(foo);
		p2.addAction(new ProvenanceAction().withMethodParameters(methparams));
		try {
			ws.saveObjects(foo, prov, Arrays.asList(
					new WorkspaceSaveObject(data, SAFE_TYPE1, null, p, false)));
			fail("saved too big prov");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Object #1 provenance size 1000272 exceeds limit of 1000000"));
		}
	}
	
	@Test
	public void saveWithLargeSubdata() throws Exception {
		final String specSubdata =
				"module TestSubdata {" +
					"/* @searchable ws_subset subset */" +
					"typedef structure {" +
						"list<string> subset;" +
					"} SubSetType;" +
				"};";
		String mod = "TestSubdata";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		ws.requestModuleRegistration(userfoo, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(userfoo, specSubdata, Arrays.asList("SubSetType"), null, null, false, null);
		TypeDefId subsettype = new TypeDefId(new TypeDefName(mod, "SubSetType"), 0, 1);
		
		WorkspaceIdentifier subdataws = new WorkspaceIdentifier("bigsubdata");
		ws.createWorkspace(userfoo, subdataws.getName(), false, null);
		Map<String, Object> data = new HashMap<String, Object>();
		List<String> subdata = new LinkedList<String>();
		data.put("subset", subdata);
		for (int i = 0; i < 14955; i++) {
			subdata.add(TEXT1000);
		}
		ws.saveObjects(userfoo, subdataws, Arrays.asList( //should work
				new WorkspaceSaveObject(data, subsettype, null, new Provenance(userfoo), false)));
		
		subdata.add(TEXT1000);
		try {
			ws.saveObjects(userfoo, subdataws, Arrays.asList(
					new WorkspaceSaveObject(data, subsettype, null, new Provenance(userfoo), false)));
			fail("saved too big subdata");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Object #1 subdata size 15000880 exceeds limit of 15000000"));
		}
	}
	
	@Test @Ignore
	public void saveWithBigData() throws Exception {
//		System.gc();
//		printMem("*** starting saveWithBigData, ran gc ***");
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		
		WorkspaceIdentifier bigdataws = new WorkspaceIdentifier("bigdata");
		ws.createWorkspace(userfoo, bigdataws.getName(), false, null);
		Map<String, Object> data = new HashMap<String, Object>();
		List<String> subdata = new LinkedList<String>();
		data.put("subset", subdata);
		for (int i = 0; i < 997008; i++) {
			//force allocation of a new char[]
			subdata.add("" + TEXT1000);
		}
//		printMem("*** created object ***");
		ws.saveObjects(userfoo, bigdataws, Arrays.asList( //should work
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, new Provenance(userfoo), false)));
//		printMem("*** saved object ***");
		subdata.add("" + TEXT1000);
		try {
			ws.saveObjects(userfoo, bigdataws, Arrays.asList(
					new WorkspaceSaveObject(data, SAFE_TYPE1, null, new Provenance(userfoo), false)));
			fail("saved too big data");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Object #1 data size 1000000039 exceeds limit of 1000000000"));
		}
		data = null;
		subdata = null;
//		System.gc();
		
//		printMem("*** released refs ***");
		
		@SuppressWarnings("unchecked")
		Map<String, Object> newdata = (Map<String, Object>) ws.getObjects(
				userfoo, Arrays.asList(new ObjectIdentifier(bigdataws, 1))).get(0).getData();
//		printMem("*** retrieved object ***");
//		System.gc();
//		printMem("*** ran gc after retrieve ***");
		
		assertThat("correct obj keys", newdata.keySet(),
				is((Set<String>) new HashSet<String>(Arrays.asList("subset"))));
		@SuppressWarnings("unchecked")
		List<String> newsd = (List<String>) newdata.get("subset");
		assertThat("correct subdata size", newsd.size(), is(997008));
		for (String s: newsd) {
			assertThat("correct string in subdata", s, is(TEXT1000));
		}
//		newdata = null;
//		newsd = null;
//		printMem("*** released refs ***");
//		System.gc();
//		printMem("*** ran gc, exiting saveWithBigMeta ***");
	}
	
	@Test(timeout=40000)
	public void tenKrefs() throws Exception {
		final String specRef =
				"module Test10KRefs {\n" +
					"typedef structure {\n" +
						"float foo;\n" +
						"list<int> bar;\n" +
						"string baz;\n" +
						"mapping <string, mapping<string, int>> map;\n" +
					"} ToRefType;\n" +
					"/* @id ws Test10KRefs.ToRefType */\n" +
					"typedef string reference;\n" +
					"typedef structure {\n" +
						"mapping<reference, string> map;\n" +
					"} FromRefType;\n" + 
				"};\n";
		String mod = "Test10KRefs";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		ws.requestModuleRegistration(userfoo, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(userfoo, specRef,
				Arrays.asList("ToRefType", "FromRefType"), null, null, false, null);
		TypeDefId toRef = new TypeDefId(new TypeDefName(mod, "ToRefType"), 0, 1);
		TypeDefId fromRef = new TypeDefId(new TypeDefName(mod, "FromRefType"), 0, 1);
		
		WorkspaceIdentifier wspace = new WorkspaceIdentifier("tenKrefs");
		WorkspaceInformation wi = ws.createWorkspace(userfoo, wspace.getName(), false, null);
		long wsid = wi.getId();
		Provenance emptyprov = new Provenance(userfoo);
		Map<String, Object> torefdata = new HashMap<String, Object>();
		torefdata.put("foo", 3.2);
		torefdata.put("baz", "astring");
		torefdata.put("bar", Arrays.asList(-3, 1, 234567890));
		Map<String, Integer> inner2 = new HashMap<String, Integer>();
		inner2.put("Foo", 3);
		inner2.put("bar", 6);
		inner2.put("baz", 42);
		Map<String, Map<String, Integer>> inner1 = new HashMap<String, Map<String,Integer>>();
		inner1.put("string1", inner2);
		inner1.put("string2", inner2);
		torefdata.put("map", inner1);
		
		List<WorkspaceSaveObject> wsos = new LinkedList<WorkspaceSaveObject>();
		Map<String, Object> refdata = new HashMap<String, Object>();
		Map<String, String> refs = new HashMap<String, String>();
		refdata.put("map", refs);
		
		Set<String> expectedRefs = new HashSet<String>();
		for (int i = 1; i < 10001; i++) {
			wsos.add(new WorkspaceSaveObject(torefdata, toRef, null, emptyprov, false));
			refs.put("tenKrefs/auto" + i, "expected " + i);
			expectedRefs.add(wsid + "/" + i + "/" + 1);
		}
		ws.saveObjects(userfoo, wspace, wsos);
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(refdata, fromRef, null, emptyprov, false)));
		
		WorkspaceObjectData wod = ws.getObjects(userfoo,
				Arrays.asList(new ObjectIdentifier(wspace, "auto10001")))
				.get(0);
		
		@SuppressWarnings("unchecked")
		Map<String, Object> ret = (Map<String, Object>) wod.getData();
		@SuppressWarnings("unchecked")
		Map<String, String> retrefs = (Map<String, String>) ret.get("map");
		for (Entry<String, String> es: retrefs.entrySet()) {
			long expected = Long.parseLong(es.getValue().split(" ")[1]);
			ObjectIdentifier oi = ObjectIdentifier.parseObjectReference(es.getKey());
			assertThat("reference ws is correct", oi.getWorkspaceIdentifier().getId(), is(wsid));
			assertThat("reference id is correct", oi.getId(), is(expected));
			assertThat("reference ver is correct", oi.getVersion(), is(1));
		}
		assertThat("returned refs correct", new HashSet<String>(wod.getReferences()),
				is(expectedRefs));
		//TODO put this test in the JSONRPCLayer tests
	}
	
	@Test(timeout=60000)
	public void unicode() throws Exception {
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		
		WorkspaceIdentifier unicode = new WorkspaceIdentifier("unicode");
		ws.createWorkspace(userfoo, unicode.getName(), false, null);
		Map<String, Object> data = new HashMap<String, Object>();
		List<String> subdata = new LinkedList<String>();
		StringBuilder sb = new StringBuilder();
		//19 ttl bytes in UTF-8
		sb.appendCodePoint(0x10310);
		sb.appendCodePoint(0x4A);
		sb.appendCodePoint(0x103B0);
		sb.appendCodePoint(0x120);
		sb.appendCodePoint(0x1D120);
		sb.appendCodePoint(0x0A90);
		sb.appendCodePoint(0x6A);
		String test = sb.toString();
		
		data.put("subset", subdata);
		for (int i = 0; i < 6000000; i++) {
			subdata.add(test);
		}
		ws.saveObjects(userfoo, unicode, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, new Provenance(userfoo), false)));
		@SuppressWarnings("unchecked")
		Map<String, Object> newdata = (Map<String, Object>) ws.getObjects(
				userfoo, Arrays.asList(new ObjectIdentifier(unicode, 1))).get(0).getData();
		assertThat("correct obj keys", newdata.keySet(),
				is((Set<String>) new HashSet<String>(Arrays.asList("subset"))));
		@SuppressWarnings("unchecked")
		List<String> newsd = (List<String>) newdata.get("subset");
		assertThat("correct subdata size", newsd.size(), is(6000000));
		for (String s: newsd) {
			assertThat("correct string in subdata", s, is(test));
		}
		
		data.clear();
		data.put(test, "foo");
		ws.saveObjects(userfoo, unicode, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, new Provenance(userfoo), false)));
		@SuppressWarnings("unchecked")
		Map<String, Object> newdata2 = (Map<String, Object>) ws.getObjects(
				userfoo, Arrays.asList(new ObjectIdentifier(unicode, 2))).get(0).getData();
		assertThat("unicode key correct", newdata2.keySet(),
				is((Set<String>) new HashSet<String>(Arrays.asList(test))));
		assertThat("value correct", (String) newdata2.get(test), is("foo"));
	}
	
	@Test
	public void bigUserMetaErrors() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceIdentifier read = new WorkspaceIdentifier("bigmeta");
		ws.createWorkspace(foo, read.getIdentifierString(), false, null);
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, String> smallmeta = new HashMap<String, String>();
		smallmeta.put("foo", "bar");
		Map<String, String> meta = new HashMap<String, String>();
		data.put("fubar", "bar");
		JsonNode savedata = mapper.valueToTree(data);
		for (int i = 0; i < 18; i++) {
			meta.put(Integer.toString(i), LONG_TEXT); //> 16Mb now
		}
		try {
			ws.saveObjects(foo, read, Arrays.asList(new WorkspaceSaveObject(
					new ObjectIDNoWSNoVer("bigmeta"), savedata, SAFE_TYPE1, meta,
					new Provenance(foo), false)));
			fail("saved object with > 16kb metadata");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Metadata is > 16000 bytes"));
		}
		try {
			ws.saveObjects(foo, read, Arrays.asList(new WorkspaceSaveObject(
					new ObjectIDNoWSNoVer(3), savedata, SAFE_TYPE1, meta,
					new Provenance(foo), false)));
			fail("saved object with > 16kb metadata");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Metadata is > 16000 bytes"));
		}
	}
	
	@Test
	public void saveWithWrongObjectId() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceIdentifier read = new WorkspaceIdentifier("wrongobjid");
		ws.createWorkspace(foo, read.getIdentifierString(), false, null);
		Map<String, Object> data = new HashMap<String, Object>();
		JsonNode savedata = mapper.valueToTree(data);
		try {
			ws.saveObjects(foo, read, Arrays.asList(new WorkspaceSaveObject(
					new ObjectIDNoWSNoVer(3), savedata, SAFE_TYPE1, null,
					new Provenance(foo), false)));
			fail("saved object with non-existant id");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception", nsoe.getLocalizedMessage(),
					is("There is no object with id 3"));
		}
		
	}
	
	@Test
	public void unserializableData() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceIdentifier read = new WorkspaceIdentifier("unserializable");
		ws.createWorkspace(foo, read.getIdentifierString(), false, null);
		Object data = new StringReader("foo");
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("foo", "bar");
		try {
			ws.saveObjects(foo, read, Arrays.asList(new WorkspaceSaveObject(
					new ObjectIDNoWSNoVer("jframe"), data, SAFE_TYPE1, meta,
					new Provenance(foo), false)));
			fail("saved unserializable object");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Cannot serialize data"));
		}
	}
	
	@Test
	public void getNonexistantObjects() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceIdentifier read = new WorkspaceIdentifier("nonexistantobjects");
		ws.createWorkspace(foo, read.getIdentifierString(), false, null);
		long readid = ws.getWorkspaceInformation(foo, read).getId();
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("fubar", "thingy");
		JsonNode savedata = mapper.valueToTree(data);
		List<WorkspaceSaveObject> objects = new ArrayList<WorkspaceSaveObject>();
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("myname"),
				savedata, SAFE_TYPE1, null, new Provenance(foo), false));
		ws.saveObjects(foo, read, objects);
		getNonExistantObject(foo, new ObjectIdentifier(read, 2),
				"No object with id 2 exists in workspace " + readid);
		getNonExistantObject(foo, new ObjectIdentifier(read, 1, 2),
				"No object with id 1 (name myname) and version 2 exists in workspace " + readid);
		getNonExistantObject(foo, new ObjectIdentifier(read, "myname2"),
				"No object with name myname2 exists in workspace " + readid);
		getNonExistantObject(foo, new ObjectIdentifier(read, "myname", 2),
				"No object with id 1 (name myname) and version 2 exists in workspace " + readid);
	}

	private void getNonExistantObject(WorkspaceUser foo, ObjectIdentifier oi,
			String exception) throws Exception {
		try {
			ws.getObjectInformation(foo, Arrays.asList(oi), false);
			fail("got non-existant object");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getLocalizedMessage(), 
					is(exception));
		}
	}
	
	private void testObjectIdentifier(String goodId) {
		new ObjectIdentifier(new WorkspaceIdentifier("foo"), goodId);
		FakeResolvedWSID fakews = new FakeResolvedWSID(1);
		new ObjectIDResolvedWS(fakews, goodId);
//		new ObjectIDResolvedWSNoVer(fakews, goodId);
		new ObjectIDNoWSNoVer(goodId);
	}
	
	private void testObjectIdentifier(String goodId, int version) {
		new ObjectIdentifier(new WorkspaceIdentifier("foo"), goodId, version);
		FakeResolvedWSID fakews = new FakeResolvedWSID(1);
		new ObjectIDResolvedWS(fakews, goodId, version);
//		new ObjectIDResolvedWSNoVer(fakews, goodId);
		new ObjectIDNoWSNoVer(goodId);
	}
	
	private void testObjectIdentifier(int goodId) {
		new ObjectIdentifier(new WorkspaceIdentifier("foo"), goodId);
		FakeResolvedWSID fakews = new FakeResolvedWSID(1);
		new ObjectIDResolvedWS(fakews, goodId);
//		new ObjectIDResolvedWSNoVer(fakews, goodId);
		new ObjectIDNoWSNoVer(goodId);
	}
	
	private void testObjectIdentifier(int goodId, int version) {
		new ObjectIdentifier(new WorkspaceIdentifier("foo"), goodId, version);
		FakeResolvedWSID fakews = new FakeResolvedWSID(1);
		new ObjectIDResolvedWS(fakews, goodId, version);
//		new ObjectIDResolvedWSNoVer(fakews, goodId);
		new ObjectIDNoWSNoVer(goodId);
	}
	
	private void testObjectIdentifier(WorkspaceIdentifier badWS, String badId,
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
	
	private void testObjectIdentifier(WorkspaceIdentifier badWS, String badId,
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
	
	private void testObjectIdentifier(WorkspaceIdentifier badWS, int badId,
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
	
	private void testObjectIdentifier(WorkspaceIdentifier badWS,
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
	
	private void testCreate(WorkspaceIdentifier goodWs, String name,
			Long id) {
		ObjectIdentifier.create(goodWs, name, id);
		ObjectIDNoWSNoVer.create(name, id);
		
	}
	
	
	private void testCreateVer(WorkspaceIdentifier goodWs, String name, Long id,
			Integer ver) {
		ObjectIdentifier.create(goodWs, name, id, ver);
	}
	
	private void testCreate(WorkspaceIdentifier badWS, String name,
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
	
	private void testCreateVer(WorkspaceIdentifier badWS, String name,
			Long id, Integer ver, String exception) {
		try {
			ObjectIdentifier.create(badWS, name, id, ver);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}
	
	private void testRef(String ref) {
		ObjectIdentifier.parseObjectReference(ref);
	}
	
	private void testRef(String ref, String exception) {
		try {
			ObjectIdentifier.parseObjectReference(ref);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}
	
	@Test
	public void objectIDs() throws Exception {
		WorkspaceIdentifier goodWs = new WorkspaceIdentifier("foo");
		testObjectIdentifier("f|o.A-1_2");
		testObjectIdentifier("f|o.A-1_2", 1);
		testObjectIdentifier(null, "foo", "wsi cannot be null");
		testObjectIdentifier(goodWs, null, "Object name cannot be null or the empty string");
		testObjectIdentifier(goodWs, "", "Object name cannot be null or the empty string");
		testObjectIdentifier(goodWs, "f|o.A-1_2+", "Illegal character in object name f|o.A-1_2+: +");
		testObjectIdentifier(goodWs, "f|o.A-1_2", 0, "Object version must be > 0");
		testObjectIdentifier(goodWs, TEXT101, "Object name exceeds the maximum length of 100");
		testObjectIdentifier(1);
		testObjectIdentifier(1, 1);
		testObjectIdentifier(null, 1, "wsi cannot be null");
		testObjectIdentifier(goodWs, 0, "Object id must be > 0");
		testObjectIdentifier(goodWs, 0, 1, "Object id must be > 0");
		testObjectIdentifier(goodWs, 1, 0, "Object version must be > 0");
		testCreate(goodWs, "f|o.A-1_2", null);
		testCreate(goodWs, null, 1L);
		testCreate(null, "boo", null, "wsi cannot be null");
		testCreate(goodWs, TEXT101, null, "Object name exceeds the maximum length of 100");
		testCreate(goodWs, null, null, "Must provide one and only one of object name (was: null) or id (was: null)");
		testCreate(goodWs, "boo", 1L, "Must provide one and only one of object name (was: boo) or id (was: 1)");
		testCreateVer(goodWs, "boo", null, 1);
		testCreateVer(goodWs, null, 1L, 1);
		testCreateVer(goodWs, "boo", null, null);
		testCreateVer(goodWs, null, 1L, null);
		testCreateVer(goodWs, "boo", null, 0, "Object version must be > 0");
		testCreateVer(goodWs, TEXT101, null, 1, "Object name exceeds the maximum length of 100");
		testCreateVer(goodWs, null, 1L, 0, "Object version must be > 0");
		testRef("foo/bar");
		testRef("foo/bar/1");
		testRef("foo/bar/1/2", "Illegal number of separators / in object reference foo/bar/1/2");
		testRef("foo/" + TEXT101 + "/1", "Object name exceeds the maximum length of 100");
		testRef("foo/bar/n", "Unable to parse version portion of object reference foo/bar/n to an integer");
		testRef("foo", "Illegal number of separators / in object reference foo");
		testRef("1/2");
		testRef("1/2/3");
		testRef("1/2/3/4", "Illegal number of separators / in object reference 1/2/3/4");
		testRef("1/2/n", "Unable to parse version portion of object reference 1/2/n to an integer");
		testRef("1", "Illegal number of separators / in object reference 1");
		testRef("foo/2");
		testRef("2/foo");
		testRef("foo/2/1");
		testRef("2/foo/1");
	}
	
	@Test
	public void deleteUndelete() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("deleteundelete");
		WorkspaceIdentifier read = new WorkspaceIdentifier("deleteundelete");
		long wsid = ws.createWorkspace(foo, read.getIdentifierString(), false, "descrip").getId();
		Map<String, String> data1 = new HashMap<String, String>();
		Map<String, String> data2 = new HashMap<String, String>();
		data1.put("data", "1");
		data2.put("data", "2");
		WorkspaceSaveObject sobj1 = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("obj"), data1, SAFE_TYPE1, null, new Provenance(foo), false);
		ws.saveObjects(foo, read, Arrays.asList(sobj1,
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("obj"), data2, SAFE_TYPE1,
				null, new Provenance(foo), false)));
		ObjectIdentifier o1 = new ObjectIdentifier(read, "obj", 1);
		ObjectIdentifier o2 = new ObjectIdentifier(read, "obj", 2);
		
		Map<ObjectIdentifier, Object> idToData = new HashMap<ObjectIdentifier, Object>();
		idToData.put(o1, data1);
		idToData.put(o2, data2);
		List<ObjectIdentifier> objs = new ArrayList<ObjectIdentifier>(idToData.keySet());
		
		checkNonDeletedObjs(foo, idToData);
		List<ObjectIdentifier> obj1 = new ArrayList<ObjectIdentifier>(Arrays.asList(o1));
		List<ObjectIdentifier> obj2 = new ArrayList<ObjectIdentifier>(Arrays.asList(o2));
		try {
			ws.setObjectsDeleted(new WorkspaceUser("bar"), obj1, true);
			fail("deleted objects w/o auth");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception", ioe.getLocalizedMessage(),
					is("Object obj cannot be accessed: User bar may not delete objects from workspace deleteundelete"));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is(o1));
		}
		try {
			ws.setObjectsDeleted(new WorkspaceUser("bar"), obj1, false);
			fail("undeleted objects w/o auth");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception", ioe.getLocalizedMessage(),
					is("Object obj cannot be accessed: User bar may not undelete objects from workspace deleteundelete"));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is(o1));
		}
		ws.setObjectsDeleted(foo, obj1, true);
		String err = String.format("Object 1 (name obj) in workspace %s has been deleted", wsid);
		failToGetDeletedObjects(foo, objs, err);
		failToGetDeletedObjects(foo, obj1, err);
		failToGetDeletedObjects(foo, obj2, err);
		
		try {
			ws.setObjectsDeleted(foo, obj2, true); //should have no effect
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception", nsoe.getLocalizedMessage(),
					is("Object 1 (name obj) in workspace " + wsid + " has been deleted"));
		}
		failToGetDeletedObjects(foo, objs, err);
		failToGetDeletedObjects(foo, obj1, err);
		failToGetDeletedObjects(foo, obj2, err);
		
		ws.setObjectsDeleted(foo, obj2, false);
		checkNonDeletedObjs(foo, idToData);
		
		ws.setObjectsDeleted(foo, obj1, false);//should have no effect
		checkNonDeletedObjs(foo, idToData);
		
		ws.setObjectsDeleted(foo, obj2, true);
		failToGetDeletedObjects(foo, objs, err);
		failToGetDeletedObjects(foo, obj1, err);
		failToGetDeletedObjects(foo, obj2, err);

		//save should undelete
		ws.saveObjects(foo, read, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("obj"), data1,
						SAFE_TYPE1, null, new Provenance(foo), false)));
		ObjectIdentifier o3 = new ObjectIdentifier(read, "obj", 3);
		idToData.put(o3, data1);
		objs = new ArrayList<ObjectIdentifier>(idToData.keySet());
		
		checkNonDeletedObjs(foo, idToData);
		assertThat("can get ws description", ws.getWorkspaceDescription(foo, read),
				is("descrip"));
		checkWSInfo(ws.getWorkspaceInformation(foo, read), foo, "deleteundelete", 1, Permission.OWNER, false, "unlocked");
		WorkspaceUser bar = new WorkspaceUser("bar");
		ws.setPermissions(foo, read, Arrays.asList(bar), Permission.ADMIN);
		Map<User, Permission> p = new HashMap<User, Permission>();
		p.put(foo, Permission.OWNER);
		p.put(bar, Permission.ADMIN);
		assertThat("can get perms", ws.getPermissions(foo, read), is(p));
		try {
			ws.setWorkspaceDeleted(bar, read, true);
			fail("Non owner deleted workspace");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is("User bar may not delete workspace deleteundelete"));
		}
		WorkspaceInformation read1 = ws.getWorkspaceInformation(foo, read);
		ws.setWorkspaceDeleted(foo, read, true);
		WorkspaceInformation read2 = ws.listWorkspaces(foo, true, true).get(0);
		try {
			ws.getWorkspaceDescription(foo, read);
			fail("got description from deleted workspace");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is("Workspace deleteundelete is deleted"));
		}
		try {
			ws.getWorkspaceInformation(foo, read);
			fail("got meta from deleted workspace");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is("Workspace deleteundelete is deleted"));
		}
		try {
			ws.setPermissions(foo, read, Arrays.asList(bar), Permission.NONE);
			fail("set perms on deleted workspace");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is("Workspace deleteundelete is deleted"));
		}
		try {
			ws.getPermissions(foo, read);
			fail("got perms from deleted workspace");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is("Workspace deleteundelete is deleted"));
		}
		try {
			ws.getObjects(bar, objs);
			fail("got objs from deleted workspace");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception msg", ioe.getLocalizedMessage(),
					is("Object obj cannot be accessed: Workspace deleteundelete is deleted"));
		}
		try {
			ws.getObjectInformation(bar, objs, false);
			fail("got obj meta from deleted workspace");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception msg", ioe.getLocalizedMessage(),
					is("Object obj cannot be accessed: Workspace deleteundelete is deleted"));
		}
		try {
			ws.saveObjects(bar, read, Arrays.asList(sobj1));
			fail("saved objs from deleted workspace");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is("Workspace deleteundelete is deleted"));
		}
		try {
			ws.setObjectsDeleted(bar, obj1, true);
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception msg", ioe.getLocalizedMessage(),
					is("Object obj cannot be accessed: Workspace deleteundelete is deleted"));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is(o1));
		}
		ws.setWorkspaceDeleted(foo, read, false);
		WorkspaceInformation read3 = ws.getWorkspaceInformation(foo, read);
		checkNonDeletedObjs(foo, idToData);
		assertThat("can get ws description", ws.getWorkspaceDescription(foo, read),
				is("descrip"));
		checkWSInfo(ws.getWorkspaceInformation(foo, read), foo, "deleteundelete", 1, Permission.OWNER, false, "unlocked");
		ws.setPermissions(foo, read, Arrays.asList(bar), Permission.ADMIN);
		assertThat("can get perms", ws.getPermissions(foo, read), is(p));
		
		assertTrue("date changed on delete", read1.getModDate().before(read2.getModDate()));
		assertTrue("date changed on undelete", read2.getModDate().before(read3.getModDate()));
	}

	private void checkNonDeletedObjs(WorkspaceUser foo,
			Map<ObjectIdentifier, Object> idToData) throws Exception {
		List<ObjectIdentifier> objs = new ArrayList<ObjectIdentifier>(idToData.keySet());
		List<WorkspaceObjectData> d = ws.getObjects(foo, objs);
		for (int i = 0; i < d.size(); i++) {
			assertThat("can get correct data from undeleted objects",
					d.get(i).getData(), is((Object) idToData.get(objs.get(i))));
		}
	}

	private void failToGetDeletedObjects(WorkspaceUser user,
			List<ObjectIdentifier> objs, String exception) throws Exception {
		try {
			ws.getObjects(user, objs);
			fail("got deleted objects");
		} catch (NoSuchObjectException e) {
			assertThat("correct exception", e.getLocalizedMessage(), is(exception));
		}
		try {
			ws.getObjectInformation(user, objs, true);
			fail("got deleted object's metadata");
		} catch (NoSuchObjectException e) {
			assertThat("correct exception", e.getLocalizedMessage(), is(exception));
		}
	}
	
	@Test
	public void testTypeMd5s() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		String typeDefName = "SomeModule.AType";
		Map<String,String> type2md5 = ws.translateToMd5Types(Arrays.asList(typeDefName + "-1.0"));
		Assert.assertEquals(1, type2md5.size());
		String md5TypeDef = type2md5.get(typeDefName + "-1.0");
		Assert.assertNotNull(md5TypeDef);
		Map<String, List<String>> md52semantic = ws.translateFromMd5Types(Arrays.asList(md5TypeDef));
		Assert.assertEquals(1, md52semantic.size());
		List<String> semList = md52semantic.get(md5TypeDef);
		Assert.assertNotNull(semList);
		Assert.assertEquals(2, semList.size());
		for (String semText : semList) {
			TypeDefId semTypeDef = TypeDefId.fromTypeString(semText);
			Assert.assertEquals(typeDefName, semTypeDef.getType().getTypeString());
			String verText = semTypeDef.getVerString();
			Assert.assertTrue("0.1".equals(verText) || "1.0".equals(verText));
		}
	}
	
	@Test
	public void testListModules() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		Map<String,String> moduleNamesInList = new HashMap<String,String>();
		for(String mod:ws.listModules(null)) {
			moduleNamesInList.put(mod, "");
		}
		Assert.assertTrue(moduleNamesInList.containsKey("SomeModule"));
		Assert.assertTrue(moduleNamesInList.containsKey("TestModule"));
	}
	
	@Test
	public void testListModuleVersions() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		Assert.assertEquals(3, ws.getModuleVersions("SomeModule", null).size());
		Assert.assertEquals(4, ws.getModuleVersions("SomeModule", new WorkspaceUser("foo")).size());
		Assert.assertEquals(2, ws.getModuleVersions("TestModule", null).size());
		Assert.assertEquals(5, ws.getModuleVersions("TestModule", new WorkspaceUser("foo")).size());
	}
	
	@Test
	public void testGetModuleInfo() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		ModuleInfo m = ws.getModuleInfo(null, new ModuleDefId("TestModule"));
		Assert.assertTrue(m.isReleased());
		Map<String,String> funcNamesInList = new HashMap<String,String>();
		for(String func : m.getFunctions() ){
			funcNamesInList.put(func, "");
		}
		Assert.assertTrue(funcNamesInList.containsKey("TestModule.getFeature-2.0"));
		Assert.assertTrue(funcNamesInList.containsKey("TestModule.getGenome-1.0"));

		Map<String,String> typeNamesInList = new HashMap<String,String>();
		for(Entry<AbsoluteTypeDefId, String> type : m.getTypes().entrySet() ){
			typeNamesInList.put(type.getKey().getTypeString(),"");
		}
		Assert.assertTrue(typeNamesInList.containsKey("TestModule.Genome-2.0"));
		Assert.assertTrue(typeNamesInList.containsKey("TestModule.Feature-1.0"));
		
		try {
			ws.getModuleInfo(null, new ModuleDefId("MadeUpModuleThatIsNotThere"));
			fail("getModuleInfo of non existant module should throw a NoSuchModuleException");
		} catch (NoSuchModuleException e) {}
		ModuleInfo m2 = ws.getModuleInfo(new WorkspaceUser("foo"), new ModuleDefId("UnreleasedModule"));
		Assert.assertEquals("foo", m2.getOwners().get(0));
		Assert.assertFalse(m2.isReleased());
		List<Long> verList = ws.getModuleVersions("UnreleasedModule", new WorkspaceUser("foo"));
		Assert.assertEquals(1, verList.size());
		Assert.assertEquals(m2.getVersion(), verList.get(0));
	}
	
	@Test
	public void testGetJsonSchema() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		try {
			ws.getJsonSchema(new TypeDefId("TestModule.NonExistantType"));
			fail("getJsonSchema of non existant type should throw a NoSuchTypeException");
		} catch (NoSuchTypeException e) {}
		
		// get several different schemas, make sure that no exceptions are thrown and it is valid json!
		String schema = ws.getJsonSchema(new TypeDefId(new TypeDefName("TestModule.Genome"),2,0));
		ObjectMapper mapper = new ObjectMapper();
		JsonNode schemaNode = mapper.readTree(schema);
		Assert.assertEquals("Genome", schemaNode.get("id").asText());
		
		schema = ws.getJsonSchema(new TypeDefId(new TypeDefName("TestModule.Genome"),2));
		schemaNode = mapper.readTree(schema);
		Assert.assertEquals("Genome", schemaNode.get("id").asText());
		
		schema = ws.getJsonSchema(new TypeDefId("TestModule.Genome"));
		schemaNode = mapper.readTree(schema);
		Assert.assertEquals("Genome", schemaNode.get("id").asText());
	}
	
	@Test
	public void testGetTypeInfo() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		TypeDetailedInfo info = ws.getTypeInfo("TestModule.Genome", false);
		Assert.assertEquals("TestModule.Genome-2.0",info.getTypeDefId());
		info = ws.getTypeInfo("TestModule.Feature", false);
		Assert.assertEquals("TestModule.Feature-1.0",info.getTypeDefId());
	}
	
	@Test
	public void testGetFuncInfo() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		try {
			ws.getFuncInfo("NoModuleThatExists.getFeature", false);
			fail("getFuncInfo of non existant module should throw a NoSuchModuleException");
		} catch (NoSuchModuleException e) {}
		try {
			ws.getFuncInfo("TestModule.noFunctionThatIKnowOf", false);
			fail("getFuncInfo of non existant module should throw a NoSuchFuncException");
		} catch (NoSuchFuncException e) {}
		
		FuncDetailedInfo info = ws.getFuncInfo("TestModule.getFeature", false);
		Assert.assertEquals("TestModule.getFeature-2.0",info.getFuncDefId());
		info = ws.getFuncInfo("TestModule.getGenome-1.0", false);
		Assert.assertEquals("TestModule.getGenome-1.0",info.getFuncDefId());
	}
	
	private void setUpCopyWorkspaces(WorkspaceUser user1, WorkspaceUser user2,
			String refws, String ws1, String ws2) throws Exception {
		TypeDefId reftype = new TypeDefId(new TypeDefName("CopyRev", "RefType"), 1, 0);
		
		WorkspaceIdentifier refs = new WorkspaceIdentifier(refws);
		ws.createWorkspace(user1, refs.getName(), false, null);
		LinkedList<WorkspaceSaveObject> refobjs = new LinkedList<WorkspaceSaveObject>();
		for (int i = 0; i < 4; i++) {
			refobjs.add(new WorkspaceSaveObject(new HashMap<String, String>(),
					SAFE_TYPE1, null, new Provenance(user1), false));
		}
		ws.saveObjects(user1, refs, refobjs);
		List<WorkspaceSaveObject> wso = Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto2"), new HashMap<String, String>(),
				SAFE_TYPE1, null, new Provenance(user1), false));
		ws.saveObjects(user1, refs, wso);
		ws.saveObjects(user1, refs, wso);
		
		
		Map<String, String> meta1 = makeSimpleMeta("foo", "bar");
		Map<String, String> meta2 = makeSimpleMeta("foo", "baz");
		Map<String, String> meta3 = makeSimpleMeta("foo", "bak");
		Map<String, String> data1 = makeRefData(refws + "/auto2/2");
		Map<String, String> data2 = makeRefData(refws + "/auto4");
		Map<String, String> data3 = makeRefData(refws + "/auto1");
		
		Provenance prov1 = new Provenance(user1);
		prov1.addAction(new ProvenanceAction()
				.withCommandLine("A command line")
				.withDescription("descrip")
				.withIncomingArgs(Arrays.asList("a", "b", "c"))
				.withMethod("method")
				.withMethodParameters(Arrays.asList((Object) meta1))
				.withOutgoingArgs(Arrays.asList("d", "e", "f"))
				.withScript("script")
				.withScriptVersion("2.1")
				.withServiceName("service")
				.withServiceVersion("3")
				.withTime(new Date(45))
				.withWorkspaceObjects(Arrays.asList(refws + "/auto3", refws + "/auto2/2")));
		prov1.addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList(refws + "/auto2/1", refws + "/auto1")));
		Provenance prov2 = new Provenance(user1);
		Provenance prov3 = new Provenance(user1);
		prov2.addAction(new ProvenanceAction(prov1.getActions().get(0)).withServiceVersion("4")
				.withWorkspaceObjects(Arrays.asList(refws + "/auto2")));
		prov3.addAction(new ProvenanceAction(prov1.getActions().get(0)).withServiceVersion("5")
				.withWorkspaceObjects(Arrays.asList(refws + "/auto3/1")));
		
		WorkspaceIdentifier cp1 = new WorkspaceIdentifier(ws1);
		WorkspaceIdentifier cp2 = new WorkspaceIdentifier(ws2);
		ws.createWorkspace(user1, cp1.getName(), false, null).getId();
		ws.createWorkspace(user2, cp2.getName(), false, null).getId();
		saveObject(user1, cp1, meta1, data1, reftype, "hide", prov1, true);
		saveObject(user1, cp1, meta2, data2, reftype, "hide", prov2, true);
		saveObject(user1, cp1, meta3, data3, reftype, "hide", prov2, true);
		saveObject(user1, cp1, meta1, data1, reftype, "orig", prov1);
		saveObject(user1, cp1, meta2, data2, reftype, "orig", prov2);
		saveObject(user1, cp1, meta3, data3, reftype, "orig", prov3);
		saveObject(user1, cp1, meta1, data1, reftype, "hidetarget", prov1, true);
	}
	
	@Test
	public void copyRevert() throws Exception {
		WorkspaceUser user1 = new WorkspaceUser("foo");
		WorkspaceUser user2 = new WorkspaceUser("bar");
		
		String wsrefs = "copyrevertrefs";
		String ws1 = "copyrevert1";
		String ws2 = "copyrevert2";
		setUpCopyWorkspaces(user1, user2, wsrefs, ws1, ws2);
		WorkspaceIdentifier cp1 = new WorkspaceIdentifier(ws1);
		WorkspaceIdentifier cp2 = new WorkspaceIdentifier(ws2);
		long wsid1 = ws.getWorkspaceInformation(user1, cp1).getId();
		long wsid2 = ws.getWorkspaceInformation(user2, cp2).getId();
		
		List<ObjectInformation> objs = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "hide"));
		ObjectInformation save11 = objs.get(0);
		ObjectInformation save12 = objs.get(1);
		ObjectInformation save13 = objs.get(2);
		
		//copy entire stack of hidden objects
		ObjectInformation copied = ws.copyObject(user1,
				ObjectIdentifier.parseObjectReference("copyrevert1/hide"),
				ObjectIdentifier.parseObjectReference("copyrevert1/copyhide"));
		compareObjectAndInfo(save13, copied, user1, wsid1, cp1.getName(), 4, "copyhide", 3);
		List<ObjectInformation> copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, 4));
		compareObjectAndInfo(save11, copystack.get(0), user1, wsid1, cp1.getName(), 4, "copyhide", 1);
		compareObjectAndInfo(save12, copystack.get(1), user1, wsid1, cp1.getName(), 4, "copyhide", 2);
		compareObjectAndInfo(save13, copystack.get(2), user1, wsid1, cp1.getName(), 4, "copyhide", 3);
		checkUnhiddenObjectCount(user1, cp1, 6, 10);
		
		
		objs = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "orig"));
		save11 = objs.get(0);
		save12 = objs.get(1);
		save13 = objs.get(2);
		
		//copy stack of unhidden objects
		copied = ws.copyObject(user1,
				ObjectIdentifier.parseObjectReference("copyrevert1/orig"),
				ObjectIdentifier.parseObjectReference("copyrevert1/copied"));
		compareObjectAndInfo(save13, copied, user1, wsid1, cp1.getName(), 5, "copied", 3);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "copied"));
		compareObjectAndInfo(save11, copystack.get(0), user1, wsid1, cp1.getName(), 5, "copied", 1);
		compareObjectAndInfo(save12, copystack.get(1), user1, wsid1, cp1.getName(), 5, "copied", 2);
		compareObjectAndInfo(save13, copystack.get(2), user1, wsid1, cp1.getName(), 5, "copied", 3);
		checkUnhiddenObjectCount(user1, cp1, 9, 13);
		
		//copy visible object to pre-existing hidden object
		copied = ws.copyObject(user1,
				ObjectIdentifier.parseObjectReference("copyrevert1/orig"),
				new ObjectIdentifier(cp1, "hidetarget"));
		compareObjectAndInfo(save13, copied, user1, wsid1, cp1.getName(), 3, "hidetarget", 2);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, 3));
		//0 is original object
		compareObjectAndInfo(save13, copystack.get(1), user1, wsid1, cp1.getName(), 3, "hidetarget", 2);
		checkUnhiddenObjectCount(user1, cp1, 9, 14);
		
		//copy hidden object to pre-existing visible object
		copied = ws.copyObject(user1, new ObjectIdentifier(cp1, "orig"),
				new ObjectIdentifier(cp1, 5));
		compareObjectAndInfo(save13, copied, user1, wsid1, cp1.getName(), 5, "copied", 4);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, 5));
		compareObjectAndInfo(save13, copystack.get(3), user1, wsid1, cp1.getName(), 5, "copied", 4);
		checkUnhiddenObjectCount(user1, cp1, 10, 15);
		
		//copy specific version to existing object
		copied = ws.copyObject(user1,
				new ObjectIdentifier(new WorkspaceIdentifier(wsid1), 2, 2),
				ObjectIdentifier.parseObjectReference("copyrevert1/copied"));
		compareObjectAndInfo(save12, copied, user1, wsid1, cp1.getName(), 5, "copied", 5);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "copied"));
		compareObjectAndInfo(save11, copystack.get(0), user1, wsid1, cp1.getName(), 5, "copied", 1);
		compareObjectAndInfo(save12, copystack.get(1), user1, wsid1, cp1.getName(), 5, "copied", 2);
		compareObjectAndInfo(save13, copystack.get(2), user1, wsid1, cp1.getName(), 5, "copied", 3);
		compareObjectAndInfo(save13, copystack.get(3), user1, wsid1, cp1.getName(), 5, "copied", 4);
		compareObjectAndInfo(save12, copystack.get(4), user1, wsid1, cp1.getName(), 5, "copied", 5);
		checkUnhiddenObjectCount(user1, cp1, 11, 16);
		
		//copy specific version to  hidden existing object
		copied = ws.copyObject(user1,
				new ObjectIdentifier(new WorkspaceIdentifier(wsid1), 2, 2),
				ObjectIdentifier.parseObjectReference("copyrevert1/hidetarget"));
		compareObjectAndInfo(save12, copied, user1, wsid1, cp1.getName(), 3, "hidetarget", 3);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "hidetarget"));
		//0 is original object
		compareObjectAndInfo(save13, copystack.get(1), user1, wsid1, cp1.getName(), 3, "hidetarget", 2);
		compareObjectAndInfo(save12, copystack.get(2), user1, wsid1, cp1.getName(), 3, "hidetarget", 3);
		checkUnhiddenObjectCount(user1, cp1, 11, 17);
		
		//copy specific version to new object
		copied = ws.copyObject(user1,
				new ObjectIdentifier(new WorkspaceIdentifier(wsid1), 2, 2),
				ObjectIdentifier.parseObjectReference("copyrevert1/newobj"));
		compareObjectAndInfo(save12, copied, user1, wsid1, cp1.getName(), 6, "newobj", 1);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "newobj"));
		compareObjectAndInfo(save12, copystack.get(0), user1, wsid1, cp1.getName(), 6, "newobj", 1);
		checkUnhiddenObjectCount(user1, cp1, 12, 18);
		
		//revert normal object
		copied = ws.revertObject(user1,
				ObjectIdentifier.parseObjectReference("copyrevert1/copied/2"));
		compareObjectAndInfo(save12, copied, user1, wsid1, cp1.getName(), 5, "copied", 6);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "copied"));
		compareObjectAndInfo(save11, copystack.get(0), user1, wsid1, cp1.getName(), 5, "copied", 1);
		compareObjectAndInfo(save12, copystack.get(1), user1, wsid1, cp1.getName(), 5, "copied", 2);
		compareObjectAndInfo(save13, copystack.get(2), user1, wsid1, cp1.getName(), 5, "copied", 3);
		compareObjectAndInfo(save13, copystack.get(3), user1, wsid1, cp1.getName(), 5, "copied", 4);
		compareObjectAndInfo(save12, copystack.get(4), user1, wsid1, cp1.getName(), 5, "copied", 5);
		compareObjectAndInfo(save12, copystack.get(5), user1, wsid1, cp1.getName(), 5, "copied", 6);
		checkUnhiddenObjectCount(user1, cp1, 13, 19);
		
		//revert hidden object
		copied = ws.revertObject(user1,
				ObjectIdentifier.parseObjectReference("copyrevert1/hidetarget/2"));
		compareObjectAndInfo(save13, copied, user1, wsid1, cp1.getName(), 3, "hidetarget", 4);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "hidetarget"));
		//0 is original object
		compareObjectAndInfo(save13, copystack.get(1), user1, wsid1, cp1.getName(), 3, "hidetarget", 2);
		compareObjectAndInfo(save12, copystack.get(2), user1, wsid1, cp1.getName(), 3, "hidetarget", 3);
		compareObjectAndInfo(save13, copystack.get(3), user1, wsid1, cp1.getName(), 3, "hidetarget", 4);
		checkUnhiddenObjectCount(user1, cp1, 13, 20);
		
		//copy to new ws
		ws.setPermissions(user2, cp2, Arrays.asList(user1), Permission.WRITE);
		copied = ws.copyObject(user1,
				ObjectIdentifier.parseObjectReference("copyrevert1/orig"),
				ObjectIdentifier.parseObjectReference("copyrevert2/copied"));
		compareObjectAndInfo(save13, copied, user1, wsid2, cp2.getName(), 1, "copied", 3);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp2, "copied"));
		compareObjectAndInfo(save11, copystack.get(0), user1, wsid2, cp2.getName(), 1, "copied", 1);
		compareObjectAndInfo(save12, copystack.get(1), user1, wsid2, cp2.getName(), 1, "copied", 2);
		compareObjectAndInfo(save13, copystack.get(2), user1, wsid2, cp2.getName(), 1, "copied", 3);
		checkUnhiddenObjectCount(user1, cp2, 3, 3);
		checkUnhiddenObjectCount(user1, cp1, 13, 20);

		failCopy(null, new ObjectIdentifier(cp1, "whooga"),
				new ObjectIdentifier(cp1, "hidetarget"), new InaccessibleObjectException(
						"Object whooga cannot be accessed: Anonymous users may not read workspace copyrevert1"));
		failRevert(null, new ObjectIdentifier(cp1, "whooga"), new InaccessibleObjectException(
						"Object whooga cannot be accessed: Anonymous users may not write to workspace copyrevert1"));
		
		failCopy(user1, new ObjectIdentifier(cp1, "foo"),
				new ObjectIdentifier(cp1, "bar"), new NoSuchObjectException(
						"No object with name foo exists in workspace " + wsid1));
		failRevert(user1, new ObjectIdentifier(cp1, "foo"),  new NoSuchObjectException(
						"No object with name foo exists in workspace " + wsid1));
		failCopy(user1, new ObjectIdentifier(cp1, "orig"),
				new ObjectIdentifier(cp1, "hidetarget", 5), new NoSuchObjectException(
						"No object with id 3 (name hidetarget) and version 5 exists in workspace " + wsid1));
		failRevert(user1, new ObjectIdentifier(cp1, "orig", 4),  new NoSuchObjectException(
						"No object with id 2 (name orig) and version 4 exists in workspace " + wsid1));
		failCopy(user1, new ObjectIdentifier(cp1, "orig"),
				new ObjectIdentifier(cp1, 7), new NoSuchObjectException(
						"Copy destination is specified as object id 7 in workspace " + wsid1 + " which does not exist."));
		
		ws.setObjectsDeleted(user1, Arrays.asList(new ObjectIdentifier(cp1, "copied")), true);
		failCopy(user1, new ObjectIdentifier(cp1, "copied"),
				new ObjectIdentifier(cp1, "hidetarget"), new NoSuchObjectException(
						"Object 5 (name copied) in workspace " + wsid1 + " has been deleted"));
		failRevert(user1, new ObjectIdentifier(cp1, "copied"), new NoSuchObjectException(
						"Object 5 (name copied) in workspace " + wsid1 + " has been deleted"));
		failCopy(user1, new ObjectIdentifier(cp1, "orig"),
				new ObjectIdentifier(cp1, "copied"), new NoSuchObjectException(
						"Object 5 (name copied) in workspace " + wsid1 + " has been deleted"));
		
		ws.copyObject(user1, new ObjectIdentifier(cp1, "orig"), new ObjectIdentifier(cp2, "foo")); //should work
		ws.setWorkspaceDeleted(user2, cp2, true);
		failCopy(user1, new ObjectIdentifier(cp1, "orig"), new ObjectIdentifier(cp2, "foo1"),
				new InaccessibleObjectException("Object foo1 cannot be accessed: Workspace copyrevert2 is deleted"));
		failCopy(user1, new ObjectIdentifier(cp2, "foo"), new ObjectIdentifier(cp2, "foo1"),
				new InaccessibleObjectException("Object foo cannot be accessed: Workspace copyrevert2 is deleted"));
		failRevert(user1, new ObjectIdentifier(cp2, "foo"),
				new InaccessibleObjectException("Object foo cannot be accessed: Workspace copyrevert2 is deleted"));
		
		ws.setWorkspaceDeleted(user2, cp2, false);
		ws.setPermissions(user2, cp2, Arrays.asList(user1), Permission.READ);
		ws.copyObject(user1,  new ObjectIdentifier(cp2, "foo"), new ObjectIdentifier(cp1, "foo")); //should work
		failCopy(user1,  new ObjectIdentifier(cp1, "foo"), new ObjectIdentifier(cp2, "foo"),
				new InaccessibleObjectException("Object foo cannot be accessed: User foo may not write to workspace copyrevert2"));
		failRevert(user1,  new ObjectIdentifier(cp2, "foo", 1),
				new InaccessibleObjectException("Object foo cannot be accessed: User foo may not write to workspace copyrevert2"));
		
		ws.setPermissions(user2, cp2, Arrays.asList(user1), Permission.NONE);
		failCopy(user1,  new ObjectIdentifier(cp2, "foo"), new ObjectIdentifier(cp1, "foo"),
				new InaccessibleObjectException("Object foo cannot be accessed: User foo may not read workspace copyrevert2"));
		
		ws.setPermissions(user2, cp2, Arrays.asList(user1), Permission.WRITE);
		ws.lockWorkspace(user2, cp2);
		failCopy(user1, new ObjectIdentifier(cp1, "orig"),
				new ObjectIdentifier(cp2, "foo2"), new InaccessibleObjectException(
						"Object foo2 cannot be accessed: The workspace with id " + wsid2 + ", name copyrevert2, is locked and may not be modified"));
		failRevert(user1, new ObjectIdentifier(cp2, "foo1", 1), new InaccessibleObjectException(
						"Object foo1 cannot be accessed: The workspace with id " + wsid2 + ", name copyrevert2, is locked and may not be modified"));
	}

	private void failCopy(WorkspaceUser user, ObjectIdentifier from, ObjectIdentifier to, Exception e) {
		try {
			ws.copyObject(user, from, to);
			fail("copied object sucessfully but expected fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	private void failRevert(WorkspaceUser user, ObjectIdentifier from, Exception e) {
		try {
			ws.revertObject(user, from);
			fail("reverted object sucessfully but expected fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}

	private void checkUnhiddenObjectCount(WorkspaceUser user,
			WorkspaceIdentifier wsi, int unhidden, int all)
			throws Exception {
		List<ObjectInformation> objs =
				ws.listObjects(user, Arrays.asList(wsi), null, false, false, true, false);
		assertThat("orig objects hidden", objs.size(), is(unhidden));
		objs =
				ws.listObjects(user, Arrays.asList(wsi), null, true, false, true, false);
		assertThat("orig objects hidden", objs.size(), is(all));
	}
	
	private Map<String, String> makeSimpleMeta(String key, String value) {
		Map<String, String> map = new HashMap<String, String>();
		map.put(key, value);
		return map;
	}
	
	private Map<String, String> makeRefData(String ref) {
		Map<String, String> data = new HashMap<String, String>();
		data.put("ref", ref);
		return data;
	}

	private void compareObjectAndInfo(ObjectInformation original,
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
		
	}

	private void compareObjectInfo(ObjectInformation original,
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

	private ObjectInformation saveObject(WorkspaceUser user, WorkspaceIdentifier wsi,
			Map<String, String> meta, Map<String, String> data, TypeDefId type,
			String name, Provenance prov)
			throws Exception {
		return saveObject(user, wsi, meta, data, type, name, prov, false);
	}
	
	private ObjectInformation saveObject(WorkspaceUser user, WorkspaceIdentifier wsi,
			Map<String, String> meta, Map<String, String> data, TypeDefId type, String name,
			Provenance prov, boolean hide)
			throws Exception {
		return ws.saveObjects(user, wsi, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer(name), data,
						type, meta, prov, hide))).get(0);
	}
	
	@Test
	public void cloneWorkspace() throws Exception {
		WorkspaceUser user1 = new WorkspaceUser("foo");
		WorkspaceUser user2 = new WorkspaceUser("bar");
		
		String wsrefs = "clonerefs";
		String ws1 = "clone1";
		setUpCopyWorkspaces(user1, user2, wsrefs, ws1, "cloneunused");
		WorkspaceIdentifier cp1 = new WorkspaceIdentifier(ws1);
		WorkspaceIdentifier clone1 = new WorkspaceIdentifier("newclone");
		WorkspaceInformation info1 = ws.cloneWorkspace(user1, cp1, clone1.getName(), false, null);
		
		checkWSInfo(clone1, user1, "newclone", 3, Permission.OWNER, false, info1.getId(),
				info1.getModDate(), "unlocked");
		assertNull("desc ok", ws.getWorkspaceDescription(user1, clone1));
		
		List<ObjectInformation> objs = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "hide"));
		ObjectInformation save11 = objs.get(0);
		ObjectInformation save12 = objs.get(1);
		ObjectInformation save13 = objs.get(2);
		
		objs = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "orig"));
		ObjectInformation save21 = objs.get(0);
		ObjectInformation save22 = objs.get(1);
		ObjectInformation save23 = objs.get(2);
		
		objs = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "hidetarget"));
		ObjectInformation save31 = objs.get(0);
		
		List<ObjectInformation> hideobjs = ws.getObjectHistory(user1, new ObjectIdentifier(clone1, "hide"));
		long id = hideobjs.get(0).getObjectId();
		compareObjectAndInfo(save11, hideobjs.get(0), user1, info1.getId(), clone1.getName(), id, "hide", 1);
		compareObjectAndInfo(save12, hideobjs.get(1), user1, info1.getId(), clone1.getName(), id, "hide", 2);
		compareObjectAndInfo(save13, hideobjs.get(2), user1, info1.getId(), clone1.getName(), id, "hide", 3);
		
		List<ObjectInformation> origobjs = ws.getObjectHistory(user1, new ObjectIdentifier(clone1, "orig"));
		id = origobjs.get(0).getObjectId();
		compareObjectAndInfo(save21, origobjs.get(0), user1, info1.getId(), clone1.getName(), id, "orig", 1);
		compareObjectAndInfo(save22, origobjs.get(1), user1, info1.getId(), clone1.getName(), id, "orig", 2);
		compareObjectAndInfo(save23, origobjs.get(2), user1, info1.getId(), clone1.getName(), id, "orig", 3);
		
		List<ObjectInformation> hidetarget = ws.getObjectHistory(user1, new ObjectIdentifier(clone1, "hidetarget"));
		id = hidetarget.get(0).getObjectId();
		compareObjectAndInfo(save31, hidetarget.get(0), user1, info1.getId(), clone1.getName(), id, "hidetarget", 1);
		checkUnhiddenObjectCount(user1, clone1, 3, 7);
		
		
		ws.setObjectsDeleted(user1, Arrays.asList(new ObjectIdentifier(cp1, "hide")), true);
		
		WorkspaceIdentifier clone2 = new WorkspaceIdentifier("newclone2");
		WorkspaceInformation info2 = ws.cloneWorkspace(user1, cp1, clone2.getName(), true, "my desc");
		
		checkWSInfo(clone2, user1, "newclone2", 2, Permission.OWNER, true, info2.getId(),
				info2.getModDate(), "unlocked");
		assertThat("desc ok", ws.getWorkspaceDescription(user1, clone2), is("my desc"));
		
		origobjs = ws.getObjectHistory(user1, new ObjectIdentifier(clone2, "orig"));
		id = origobjs.get(0).getObjectId();
		compareObjectAndInfo(save21, origobjs.get(0), user1, info2.getId(), clone2.getName(), id, "orig", 1);
		compareObjectAndInfo(save22, origobjs.get(1), user1, info2.getId(), clone2.getName(), id, "orig", 2);
		compareObjectAndInfo(save23, origobjs.get(2), user1, info2.getId(), clone2.getName(), id, "orig", 3);
		
		hidetarget = ws.getObjectHistory(user1, new ObjectIdentifier(clone2, "hidetarget"));
		id = hidetarget.get(0).getObjectId();
		compareObjectAndInfo(save31, hidetarget.get(0), user1, info2.getId(), clone2.getName(), id, "hidetarget", 1);
		checkUnhiddenObjectCount(user1, clone2, 3, 4);
		
		ws.setWorkspaceDeleted(user1, cp1, true);
		failClone(user1, cp1, "fakename", new NoSuchWorkspaceException("Workspace clone1 is deleted", cp1));
		ws.setWorkspaceDeleted(user1, cp1, false);
		ws.setObjectsDeleted(user1, Arrays.asList(new ObjectIdentifier(cp1, "hide")), true);
		
		failClone(null, cp1, "fakename", new WorkspaceAuthorizationException("Anonymous users may not read workspace clone1"));
		//workspaceIdentifier used in the workspace method to check ws names tested extensively elsewhere, so just
		// a couple tests here
		failClone(user1, cp1, "bar:fakename", new IllegalArgumentException(
				"Workspace name bar:fakename must only contain the user name foo prior to the : delimiter"));
		failClone(user1, cp1, "foo:fake(name", new IllegalArgumentException(
				"Illegal character in workspace name foo:fake(name: ("));
		failClone(user2, cp1, "fakename", new WorkspaceAuthorizationException("User bar may not read workspace clone1"));
		failClone(user1, cp1, "newclone2", new PreExistingWorkspaceException("Workspace newclone2 already exists"));
		failClone(user1, new WorkspaceIdentifier("noclone"), "fakename",
				new NoSuchWorkspaceException("No workspace with name noclone exists", cp1));
		
		ws.lockWorkspace(user1, cp1);
		
		WorkspaceIdentifier clone3 = new WorkspaceIdentifier("newclone3");
		WorkspaceInformation info3 = ws.cloneWorkspace(user1, cp1, clone3.getName(), false, "my desc2");
		
		checkWSInfo(clone3, user1, "newclone3", 2, Permission.OWNER, false, info3.getId(),
				info3.getModDate(), "unlocked");
		assertThat("desc ok", ws.getWorkspaceDescription(user1, clone3), is("my desc2"));
		
		origobjs = ws.getObjectHistory(user1, new ObjectIdentifier(clone3, "orig"));
		id = origobjs.get(0).getObjectId();
		compareObjectAndInfo(save21, origobjs.get(0), user1, info3.getId(), clone3.getName(), id, "orig", 1);
		compareObjectAndInfo(save22, origobjs.get(1), user1, info3.getId(), clone3.getName(), id, "orig", 2);
		compareObjectAndInfo(save23, origobjs.get(2), user1, info3.getId(), clone3.getName(), id, "orig", 3);
		
		hidetarget = ws.getObjectHistory(user1, new ObjectIdentifier(clone3, "hidetarget"));
		id = hidetarget.get(0).getObjectId();
		compareObjectAndInfo(save31, hidetarget.get(0), user1, info3.getId(), clone3.getName(), id, "hidetarget", 1);
		checkUnhiddenObjectCount(user1, clone3, 3, 4);
		
		WorkspaceIdentifier clone4 = new WorkspaceIdentifier("newclone4");
		ws.cloneWorkspace(user1, cp1, clone4.getName(), true, LONG_TEXT);
		assertThat("desc ok", ws.getWorkspaceDescription(user1, clone4), is(LONG_TEXT.subSequence(0, 1000)));
		
		ws.setGlobalPermission(user1, clone2, Permission.NONE);
		ws.setGlobalPermission(user1, clone4, Permission.NONE);
	}

	private void failClone(WorkspaceUser user, WorkspaceIdentifier wsi,
			String name, Exception e) {
		try {
			ws.cloneWorkspace(user, wsi, name, false, null);
			fail("expected clone to fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	@Test
	public void lockWorkspace() throws Exception {
		WorkspaceUser user = new WorkspaceUser("lockuser");
		WorkspaceUser user2 = new WorkspaceUser("lockuser2");
		WorkspaceIdentifier wsi = lockWS;
		long wsid = ws.createWorkspace(user, wsi.getName(), false, null).getId();
		ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), false)));
		ObjectIdentifier oi = new ObjectIdentifier(wsi, "auto1");
		//these should work
		WorkspaceInformation info = ws.lockWorkspace(user, wsi);
		checkWSInfo(info, user, "lock", 1, Permission.OWNER, false, "locked");
		ws.getObjects(user, Arrays.asList(oi));
		ws.cloneWorkspace(user, wsi, "lockclone", false, null);
		ws.copyObject(user, oi, new ObjectIdentifier(new WorkspaceIdentifier("lockclone"), "foo"));
		ws.setPermissions(user, wsi, Arrays.asList(user2), Permission.WRITE);
		ws.setPermissions(user, wsi, Arrays.asList(user2), Permission.NONE);
		ws.getPermissions(user, wsi);
		ws.getWorkspaceDescription(user, wsi);
		ws.getWorkspaceInformation(user, wsi);
		ws.listObjects(user, Arrays.asList(wsi), null, false, false, false, false);
		
		//these should not work
		try {
			ws.lockWorkspace(user, new WorkspaceIdentifier("nolock"));
			fail("locked non existant ws");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("No workspace with name nolock exists"));
		}
		ws.createWorkspace(user, "lock2", false, "foo");
		WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("lock2");
		try {
			ws.lockWorkspace(null, wsi2);
			fail("locked w/o creds");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("Anonymous users may not lock workspace lock2"));
		}
		try {
			ws.lockWorkspace(user2, wsi2);
			fail("locked w/o creds");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("User lockuser2 may not lock workspace lock2"));
		}
		ws.setWorkspaceDeleted(user, wsi2, true);
		try {
			ws.lockWorkspace(user, wsi2);
			fail("locked deleted ws");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("Workspace lock2 is deleted"));
		}
		try {
			ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
					new HashMap<String, String>(), SAFE_TYPE1, null,
					new Provenance(user), false)));
			fail("saved to locked workspace");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.copyObject(user, oi, new ObjectIdentifier(wsi, "foo"));
			fail("copied to locked workspace");
		} catch (InaccessibleObjectException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("Object foo cannot be accessed: The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.revertObject(user, oi);
			fail("revert to locked workspace");
		} catch (InaccessibleObjectException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("Object auto1 cannot be accessed: The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.lockWorkspace(user, wsi);
			fail("locked locked workspace");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.renameObject(user, oi, "boo");
			fail("renamed locked workspace obj");
		} catch (InaccessibleObjectException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("Object auto1 cannot be accessed: The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.renameWorkspace(user, wsi, "foo");
			fail("renamed locked workspace obj");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.setObjectsDeleted(user, Arrays.asList(oi), true);
			fail("deleted locked workspace obj");
		} catch (InaccessibleObjectException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("Object auto1 cannot be accessed: The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.setObjectsHidden(user, Arrays.asList(oi), true);
			fail("hid locked workspace obj");
		} catch (InaccessibleObjectException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("Object auto1 cannot be accessed: The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.setWorkspaceDeleted(user, wsi, true);
			fail("deleted locked workspace");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.setWorkspaceDescription(user, wsi, "wugga");
			fail("set desc on locked ws");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		
		try {
			ws.getWorkspaceDescription(user2, wsi);
			fail("bad access to locked workspace");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("User lockuser2 may not read workspace lock"));
		}
		
		//should work
		ws.setGlobalPermission(user, wsi, Permission.READ);
		checkWSInfo(ws.getWorkspaceInformation(user, wsi),
				user, "lock", 1, Permission.OWNER, true, "published");
		checkWSInfo(ws.getWorkspaceInformation(user2, wsi),
				user, "lock", 1, Permission.NONE, true, "published");
		ws.getWorkspaceDescription(user2, wsi);
		
		//shouldn't
		try {
			ws.setGlobalPermission(user, wsi, Permission.NONE);
			fail("bad access to locked workspace");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
	}
	
	@Test
	public void renameObject() throws Exception {
		WorkspaceUser user = new WorkspaceUser("renameObjUser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("renameObj");
		WorkspaceUser user2 = new WorkspaceUser("renameObjUser2");
		WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("renameObj2");
		long wsid1 = ws.createWorkspace(user, wsi.getName(), false, null).getId();
		ws.createWorkspace(user2, wsi2.getName(), false, null);
		ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), false)));
		ws.saveObjects(user2, wsi2, Arrays.asList(new WorkspaceSaveObject(
				new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), false)));
		ObjectInformation info = ws.renameObject(user, new ObjectIdentifier(wsi, "auto1"), "mynewname");
		checkObjInfo(info, 1L, "mynewname", SAFE_TYPE1.getTypeString(), 1, user,
				wsid1, "renameObj", "99914b932bd37a50b983c5e7c90ae93b", 2, null);
		String newname = ws.listObjects(user, Arrays.asList(wsi), null, false,
				false, false, false).get(0).getObjectName();
		assertThat("object renamed", newname, is("mynewname"));
		
		ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("myoldname"), new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), false)));
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "bad%name", new IllegalArgumentException(
				"Illegal character in object name bad%name: %"));
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "myoldname", new IllegalArgumentException(
				"There is already an object in the workspace named myoldname"));
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "mynewname", new IllegalArgumentException(
				"Object is already named mynewname"));
		failObjRename(user, new ObjectIdentifier(wsi, "bar"), "foo", new NoSuchObjectException(
				"No object with name bar exists in workspace " + wsid1));
		failObjRename(user, new ObjectIdentifier(wsi2, "auto1"), "foo",
				new InaccessibleObjectException(
						"Object auto1 cannot be accessed: User renameObjUser may not rename objects in workspace renameObj2"));
		failObjRename(null, new ObjectIdentifier(wsi2, "auto1"), "foo",
				new InaccessibleObjectException(
						"Object auto1 cannot be accessed: Anonymous users may not rename objects in workspace renameObj2"));
		
		ws.setObjectsDeleted(user, Arrays.asList(new ObjectIdentifier(wsi, "mynewname")), true);
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "foo", new InaccessibleObjectException(
				"Object 1 (name mynewname) in workspace " + wsid1 + " has been deleted"));
		ws.setWorkspaceDeleted(user, wsi, true);
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "foo", new InaccessibleObjectException(
				"Object mynewname cannot be accessed: Workspace renameObj is deleted"));
		ws.setWorkspaceDeleted(user, wsi, false);
		failObjRename(user, new ObjectIdentifier(new WorkspaceIdentifier("renameObjfake"), "mynewname"), "foo", new InaccessibleObjectException(
				"Object mynewname cannot be accessed: No workspace with name renameObjfake exists"));
		ws.lockWorkspace(user, wsi);
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "foo", new InaccessibleObjectException(
				"Object mynewname cannot be accessed: The workspace with id " + wsid1 + ", name renameObj, is locked and may not be modified"));
	}

	private void failObjRename(WorkspaceUser user, ObjectIdentifier oi,
			String newname, Exception e) {
		try {
			ws.renameObject(user, oi, newname);
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	@Test
	public void renameWorkspace() throws Exception {
		WorkspaceUser user = new WorkspaceUser("renameWSUser");
		WorkspaceUser user2 = new WorkspaceUser("renameWSUser2");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("renameWS");
		WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("renameWS2");
		WorkspaceInformation info1 = ws.createWorkspace(user, wsi.getName(), false, null);
		WorkspaceIdentifier newwsi = new WorkspaceIdentifier(user.getUser() + ":newRenameWS");
		Thread.sleep(2); //make sure timestamp is different on rename
		WorkspaceInformation info2 = ws.renameWorkspace(user, wsi, newwsi.getName());
		checkWSInfo(info2, user, newwsi.getName(), 0, Permission.OWNER, false, "unlocked");
		assertTrue("date updated on ws rename", info2.getModDate().after(info1.getModDate()));
		checkWSInfo(ws.getWorkspaceInformation(user, newwsi),
				user, newwsi.getName(), 0, Permission.OWNER, false, "unlocked");
		
		failWSRename(user, newwsi, "foo|bar",
				new IllegalArgumentException("Illegal character in workspace name foo|bar: |"));
		failWSRename(user, newwsi, "foo:foobar",
				new IllegalArgumentException(
						"Workspace name foo:foobar must only contain the user name renameWSUser prior to the : delimiter"));
		
		ws.createWorkspace(user2, wsi2.getName(), false, null);
		ws.setPermissions(user2, wsi2, Arrays.asList(user), Permission.WRITE);
		failWSRename(user, newwsi, "renameWS2",
				new IllegalArgumentException("There is already a workspace named renameWS2"));
		failWSRename(user, newwsi, newwsi.getName(),
				new IllegalArgumentException("Workspace is already named renameWSUser:newRenameWS"));
		failWSRename(user, new WorkspaceIdentifier(newwsi.getName() + "a"), newwsi.getName(),
				new NoSuchWorkspaceException("No workspace with name renameWSUser:newRenameWSa exists", wsi));
		failWSRename(user, wsi2, newwsi.getName(),
				new WorkspaceAuthorizationException("User renameWSUser may not rename workspace renameWS2"));
		failWSRename(null, newwsi, "renamefoo",
				new WorkspaceAuthorizationException("Anonymous users may not rename workspace renameWSUser:newRenameWS"));
		ws.setWorkspaceDeleted(user, newwsi, true);
		failWSRename(user, newwsi, "renamefoo",
				new NoSuchWorkspaceException("Workspace " + newwsi.getName() + " is deleted", newwsi));
		ws.setWorkspaceDeleted(user, newwsi, false);
		ws.lockWorkspace(user, newwsi);
		failWSRename(user, newwsi, "renamefoo",
				new WorkspaceAuthorizationException("The workspace with id " + info1.getId() +
						", name " + newwsi.getName() + ", is locked and may not be modified"));
		
	}

	private void failWSRename(WorkspaceUser user, WorkspaceIdentifier wsi, String newname,
			Exception e) {
		try {
			ws.renameWorkspace(user, wsi, newname);
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	@Test
	public void setGlobalRead() throws Exception {
		WorkspaceUser user = new WorkspaceUser("setGlobalUser");
		WorkspaceUser user2 = new WorkspaceUser("setGlobalUser2");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("global");
		long wsid = ws.createWorkspace(user, wsi.getName(), false, null).getId();
		
		failGetWorkspaceDesc(user2, wsi, new WorkspaceAuthorizationException(
				"User setGlobalUser2 may not read workspace global"));
		ws.setGlobalPermission(user, wsi, Permission.READ);
		assertThat("read set correctly", ws.getPermissions(user, wsi).get(new AllUsers('*')),
				is(Permission.READ));
		ws.getWorkspaceDescription(user2, wsi);
		failSetGlobalPerm(user, wsi, Permission.WRITE, new IllegalArgumentException(
				"Global permissions cannot be greater than read"));
		failSetGlobalPerm(user2, wsi, Permission.NONE, new WorkspaceAuthorizationException(
				"User setGlobalUser2 may not set global permission on workspace global"));
		failSetGlobalPerm(null, wsi, Permission.NONE, new WorkspaceAuthorizationException(
				"Anonymous users may not set global permission on workspace global"));
		ws.setWorkspaceDeleted(user, wsi, true);
		failSetGlobalPerm(user, wsi, Permission.NONE, new NoSuchWorkspaceException(
				"Workspace global is deleted", wsi));
		ws.setWorkspaceDeleted(user, wsi, false);
		
		ws.setGlobalPermission(user, wsi, Permission.NONE);
		ws.lockWorkspace(user, wsi);
		failSetGlobalPerm(user, wsi, Permission.NONE, new WorkspaceAuthorizationException(
				"The workspace with id " + wsid + ", name global, is locked and may not be modified"));
		
		//this is tested in lockWorkspace
//		ws.setGlobalPermission(user, wsi, Permission.READ);
//		assertThat("read set correctly on locked ws", ws.getPermissions(user, wsi).get(new AllUsers('*')),
//			is(Permission.READ));
	}
	
	private void failGetWorkspaceDesc(WorkspaceUser user, WorkspaceIdentifier wsi,
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
	
	private void failSetGlobalPerm(WorkspaceUser user, WorkspaceIdentifier wsi,
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
	
	@Test
	public void hiddenObjects() throws Exception {
		WorkspaceUser user = new WorkspaceUser("hideObjUser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("hideObj");
		WorkspaceUser user2 = new WorkspaceUser("hideObjUser2");
		long wsid1 = ws.createWorkspace(user, wsi.getName(), false, null).getId();
		ObjectInformation auto1 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), false))).get(0);
		ObjectInformation auto2 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), true))).get(0);
		ObjectInformation obj1 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("obj1"), new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), true))).get(0);
		
		Map<Long, ObjectInformation> expected = new HashMap<Long, ObjectInformation>();
		expected.put(1L, auto1);
		checkObjectSet(ws.listObjects(user, Arrays.asList(wsi), null, false, false, false, true), expected);
		
		expected.put(2L, auto2);
		expected.put(3L, obj1);
		checkObjectSet(ws.listObjects(user, Arrays.asList(wsi), null, true, false, false, true), expected);
		
		ws.setObjectsHidden(user, Arrays.asList(new ObjectIdentifier(wsi, 3), new ObjectIdentifier(wsi, "auto2")), false);
		checkObjectSet(ws.listObjects(user, Arrays.asList(wsi), null, false, false, false, true), expected);
		
		ws.setObjectsHidden(user, Arrays.asList(new ObjectIdentifier(wsi, 1), new ObjectIdentifier(wsi, "obj1")), true);
		expected.remove(1L);
		expected.remove(3L);
		checkObjectSet(ws.listObjects(user, Arrays.asList(wsi), null, false, false, false, true), expected);
		
		failSetHide(user, new ObjectIdentifier(wsi, "fake"), true, new NoSuchObjectException(
				"No object with name fake exists in workspace " + wsid1));
		failSetHide(user, new ObjectIdentifier(new WorkspaceIdentifier("fake"), "fake"), true, new InaccessibleObjectException(
				"Object fake cannot be accessed: No workspace with name fake exists"));
		
		failSetHide(user2, new ObjectIdentifier(wsi, "auto1"), true, new InaccessibleObjectException(
				"Object auto1 cannot be accessed: User hideObjUser2 may not hide objects from workspace hideObj"));
		failSetHide(null, new ObjectIdentifier(wsi, "auto1"), true, new InaccessibleObjectException(
				"Object auto1 cannot be accessed: Anonymous users may not hide objects from workspace hideObj"));
		
		ws.setObjectsDeleted(user, Arrays.asList(new ObjectIdentifier(wsi, 3)), true);
		failSetHide(user, new ObjectIdentifier(wsi, 3), true, new NoSuchObjectException(
				"Object 3 (name obj1) in workspace " + wsid1 + " has been deleted"));
		ws.setObjectsDeleted(user, Arrays.asList(new ObjectIdentifier(wsi, 3)), false);
		
		ws.setWorkspaceDeleted(user, wsi, true);
		failSetHide(user, new ObjectIdentifier(new WorkspaceIdentifier("fake"), "fake"), true, new InaccessibleObjectException(
				"Object fake cannot be accessed: No workspace with name fake exists"));
		ws.setWorkspaceDeleted(user, wsi, false);
		
		ws.lockWorkspace(user, wsi);
		failSetHide(user, new ObjectIdentifier(wsi, 3), true, new InaccessibleObjectException(
				"Object 3 cannot be accessed: The workspace with id " + wsid1 +
				", name hideObj, is locked and may not be modified"));
	}

	private void failSetHide(WorkspaceUser user, ObjectIdentifier oi, boolean hide,
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

	private void checkObjectSet(List<ObjectInformation> listObjects,
			Map<Long, ObjectInformation> expected) {
		Set<Long> seen = new HashSet<Long>();
		for (ObjectInformation oi: listObjects) {
			long oid = oi.getObjectId();
			if (seen.contains(oid)) {
				fail("Saw same objectid twice");
			}
			seen.add(oid);
			assertThat("object info for id " + oid + " correct", oi, is(expected.get(oid)));
		}
		assertThat("checked all objects", seen, is(expected.keySet()));
	}
	
	@Test
	public void listWorkspaces() throws Exception {
		WorkspaceUser user = new WorkspaceUser("listUser");
		WorkspaceUser user2 = new WorkspaceUser("listUser2");
		WorkspaceInformation wsinf1_1 = ws.createWorkspace(user, "list1_1", false, null);
		WorkspaceInformation wsinf1_2 = ws.createWorkspace(user, "list1_2", true, null);
		WorkspaceInformation wsinf1_3 = ws.createWorkspace(user, "list1_3", false, null);
		ws.setWorkspaceDeleted(user, new WorkspaceIdentifier("list1_3"), true);
		
		ws.createWorkspace(user2, "list2_1", false, null);
		ws.setPermissions(user2, new WorkspaceIdentifier("list2_1"), Arrays.asList(user), Permission.READ);
		WorkspaceInformation wsinf2_1 = ws.getWorkspaceInformation(user, new WorkspaceIdentifier("list2_1"));
		@SuppressWarnings("unused")
		WorkspaceInformation wsinf2_2 = ws.createWorkspace(user2, "list2_2", false, null);
		ws.setPermissions(user2, new WorkspaceIdentifier("list2_2"), Arrays.asList(user), Permission.READ);
		ws.setWorkspaceDeleted(user2, new WorkspaceIdentifier("list2_2"), true);
		ws.createWorkspace(user2, "list2_3", true, null);
		WorkspaceInformation wsinf2_3 = ws.getWorkspaceInformation(user, new WorkspaceIdentifier("list2_3"));
		@SuppressWarnings("unused")
		WorkspaceInformation wsinf2_4 = ws.createWorkspace(user2, "list2_4", true, null);
		ws.setWorkspaceDeleted(user2, new WorkspaceIdentifier("list2_4"), true);
		@SuppressWarnings("unused")
		WorkspaceInformation wsinf2_5 = ws.createWorkspace(user2, "list2_5", false, null);
		
		Map<WorkspaceInformation, Boolean> expected = new HashMap<WorkspaceInformation, Boolean>();
		expected.put(wsinf1_1, false);
		expected.put(wsinf1_2, false);
		expected.put(wsinf2_1, false);
		checkWSInfoList(ws.listWorkspaces(user, true, false), expected);
		
		expected.put(wsinf2_3, false);
		WorkspaceInformation locked = null;
		try {
			locked = ws.getWorkspaceInformation(user, lockWS);
		} catch (NoSuchWorkspaceException nswe) {
			//ignore - means that the locking ws test has not been run yet
		}
		if (locked != null) {
			expected.put(locked, false);
		}
		checkWSInfoList(ws.listWorkspaces(user, false, false), expected);
		
		expected.put(wsinf1_3, true);
		checkWSInfoList(ws.listWorkspaces(user, false, true), expected);
		
		expected.remove(wsinf2_3);
		expected.remove(locked);
		checkWSInfoList(ws.listWorkspaces(user, true, true), expected);
		
		expected.clear();
		expected.put(wsinf2_3, false);
		if (locked != null) {
			expected.put(locked, false);
		}
		WorkspaceUser newb = new WorkspaceUser("listUserAZillion");
		expected.put(ws.getWorkspaceInformation(newb, new WorkspaceIdentifier("list1_2")), false);
		checkWSInfoList(ws.listWorkspaces(newb, false, false), expected);
		expected.clear();
		checkWSInfoList(ws.listWorkspaces(newb, true, false), expected);
	}

	private void checkWSInfoList(List<WorkspaceInformation> ws,
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
				fail("got id " + wi.getId() + ", but not in expected");
			}
			if (!expected.get(idToInf.get(wi.getId()))) {
				assertThat("workspace correct", wi, is(idToInf.get(wi.getId())));
			} else {
				compareWorkspaceInfoLessTimeStamp(wi, idToInf.get(wi.getId()));
			}
		}
		assertThat("listed correct workspaces", got, is(idToInf.keySet()));
	}

	private void compareWorkspaceInfoLessTimeStamp(WorkspaceInformation got,
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
	
	@Test
	public void listObjectsAndHistory() throws Exception {
		WorkspaceUser user = new WorkspaceUser("listObjUser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("listObj1");
		WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("listObj2");
		WorkspaceUser user2 = new WorkspaceUser("listObjUser2");
		long wsid1 = ws.createWorkspace(user, wsi.getName(), false, null).getId();
		long wsid2 = ws.createWorkspace(user2, wsi2.getName(), false, null).getId();
		ws.setPermissions(user2, wsi2, Arrays.asList(user), Permission.WRITE);
		
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("metastuff", "meta");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("meta2", "my hovercraft is full of eels");
		
		Map<String, Object> passTCdata = new HashMap<String, Object>();
		passTCdata.put("thing", "athing");
		
		ObjectInformation std = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("std"), new HashMap<String, String>(), SAFE_TYPE1, meta,
				new Provenance(user), false))).get(0);
		ObjectInformation stdnometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "std")), false).get(0);
		
		ObjectInformation objstack1 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("objstack"), new HashMap<String, String>(), SAFE_TYPE1_10, meta2,
				new Provenance(user), false))).get(0);
		ObjectInformation objstack1nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "objstack", 1)), false).get(0);
		
		ObjectInformation objstack2 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("objstack"), passTCdata, SAFE_TYPE1_20, meta,
				new Provenance(user), false))).get(0);
		ObjectInformation objstack2nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "objstack", 2)), false).get(0);
		
		ObjectInformation type2_1 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("type2"), new HashMap<String, String>(), SAFE_TYPE2, meta2,
				new Provenance(user), false))).get(0);
		ObjectInformation type2_1nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "type2", 1)), false).get(0);
		
		ObjectInformation type2_2 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("type2"), new HashMap<String, String>(), SAFE_TYPE2_10, meta,
				new Provenance(user), false))).get(0);
		ObjectInformation type2_2nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "type2", 2)), false).get(0);
		
		ObjectInformation type2_3 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("type2"), passTCdata, SAFE_TYPE2_20, meta2,
				new Provenance(user), false))).get(0);
		ObjectInformation type2_3nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "type2", 3)), false).get(0);
		
		ObjectInformation type2_4 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("type2"), passTCdata, SAFE_TYPE2_21, meta2,
				new Provenance(user), false))).get(0);
		ObjectInformation type2_4nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "type2", 4)), false).get(0);
		
		ObjectInformation stdws2 = ws.saveObjects(user, wsi2, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("stdws2"), new HashMap<String, String>(), SAFE_TYPE1, meta,
				new Provenance(user), false))).get(0);
		ObjectInformation stdws2nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi2, "stdws2")), false).get(0);
		
		ObjectInformation hidden = ws.saveObjects(user, wsi2, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("hidden"), new HashMap<String, String>(), SAFE_TYPE1, meta2,
				new Provenance(user), false))).get(0);
		ObjectInformation hiddennometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi2, "hidden")), false).get(0);
		ws.setObjectsHidden(user, Arrays.asList(new ObjectIdentifier(wsi2, "hidden")), true);
		
		ObjectInformation deleted = ws.saveObjects(user, wsi2, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("deleted"), new HashMap<String, String>(), SAFE_TYPE1, meta,
				new Provenance(user), false))).get(0);
		ObjectInformation deletednometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi2, "deleted")), false).get(0);
		ws.setObjectsDeleted(user, Arrays.asList(new ObjectIdentifier(wsi2, "deleted")), true);
		
		ObjectInformation lock = null;
		ObjectInformation locknometa = null;
		List<ObjectInformation> foo = ws.listObjects(user, Arrays.asList(lockWS), null, false, false, false, true);
		if (foo.size() > 1) {
			fail("found more than one object in the locked workspace, this is unexpected");
		}
		if (foo.size() == 1) {
			lock = foo.get(0);
			locknometa = ws.listObjects(user, Arrays.asList(lockWS), null, false, false, false, false).get(0);
		}
		
		TypeDefId allType1 = new TypeDefId(SAFE_TYPE1.getType().getTypeString());
		TypeDefId allType2 = new TypeDefId(SAFE_TYPE2.getType().getTypeString());
		ArrayList<WorkspaceIdentifier> emptyWS = new ArrayList<WorkspaceIdentifier>();
		
		checkObjInfoList(ws.listObjects(user, Arrays.asList(wsi, wsi2), null, true, true, true, true),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2, hidden, deleted));
		checkObjInfoList(ws.listObjects(user, Arrays.asList(wsi, wsi2), null, false, true, true, true),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2, deleted));
		checkObjInfoList(ws.listObjects(user, Arrays.asList(wsi, wsi2), null, true, false, true, true),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2, hidden));
		checkObjInfoList(ws.listObjects(user, Arrays.asList(wsi, wsi2), null, false, false, true, true),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2));
		checkObjInfoList(ws.listObjects(user, Arrays.asList(wsi, wsi2), null, true, true, false, true),
				Arrays.asList(std, objstack2, type2_4, stdws2, hidden, deleted));
		checkObjInfoList(ws.listObjects(user, Arrays.asList(wsi, wsi2), null, false, false, false, false),
				Arrays.asList(stdnometa, objstack2nometa, type2_4nometa, stdws2nometa));
		checkObjInfoList(ws.listObjects(user, Arrays.asList(wsi, wsi2), null, true, true, true, false),
				Arrays.asList(stdnometa, objstack1nometa, objstack2nometa, type2_1nometa,
						type2_2nometa, type2_3nometa, type2_4nometa,
						stdws2nometa, hiddennometa, deletednometa));
		checkObjInfoList(ws.listObjects(user, emptyWS, allType1, true, true, true, true),
				setUpListObjectsExpected(Arrays.asList(std, objstack1, objstack2,
						stdws2, hidden, deleted), lock));
		checkObjInfoList(ws.listObjects(user, Arrays.asList(wsi), allType1, true, true, true, true),
				Arrays.asList(std, objstack1, objstack2));
		checkObjInfoList(ws.listObjects(user, Arrays.asList(wsi2), allType1, true, true, true, true),
				Arrays.asList(stdws2, hidden, deleted));
		checkObjInfoList(ws.listObjects(user, emptyWS, allType2, true, true, true, true),
				Arrays.asList(type2_1, type2_2, type2_3, type2_4));
		checkObjInfoList(ws.listObjects(user, Arrays.asList(wsi2), allType2, true, true, true, true),
				new ArrayList<ObjectInformation>());
		checkObjInfoList(ws.listObjects(user, emptyWS, SAFE_TYPE1, true, true, true, true),
				setUpListObjectsExpected(Arrays.asList(std, stdws2, hidden, deleted), lock));
		checkObjInfoList(ws.listObjects(user, emptyWS, SAFE_TYPE1, true, true, true, false),
				setUpListObjectsExpected(Arrays.asList(stdnometa, stdws2nometa, hiddennometa,
						deletednometa), locknometa));
		checkObjInfoList(ws.listObjects(user, emptyWS, SAFE_TYPE1_10, true, true, true, true),
				Arrays.asList(objstack1));
		checkObjInfoList(ws.listObjects(user, emptyWS, SAFE_TYPE1_20, true, true, true, true),
				Arrays.asList(objstack2));
		checkObjInfoList(ws.listObjects(user, emptyWS, SAFE_TYPE2, true, true, true, true),
				Arrays.asList(type2_1));
		checkObjInfoList(ws.listObjects(user, emptyWS, SAFE_TYPE2_10, true, true, true, true),
				Arrays.asList(type2_2));
		checkObjInfoList(ws.listObjects(user, emptyWS, SAFE_TYPE2_20, true, true, true, true),
				Arrays.asList(type2_3));
		checkObjInfoList(ws.listObjects(user, emptyWS, new TypeDefId(SAFE_TYPE2_20.getType(),
				SAFE_TYPE2_20.getMajorVersion()), true, true, true, true),
				Arrays.asList(type2_3, type2_4));
		checkObjInfoList(ws.listObjects(user, emptyWS, new TypeDefId(SAFE_TYPE2_10.getType(),
				SAFE_TYPE2_10.getMajorVersion()), true, true, true, true),
				Arrays.asList(type2_2));
		
		checkObjInfoList(ws.listObjects(user2, emptyWS, allType1, true, true, true, true),
				setUpListObjectsExpected(Arrays.asList(stdws2, hidden, deleted), lock));
		checkObjInfoList(ws.listObjects(user2, Arrays.asList(wsi2), null, true, true, true, true),
				Arrays.asList(stdws2, hidden, deleted));
		checkObjInfoList(ws.listObjects(user2, emptyWS, allType2, true, true, true, true),
				new ArrayList<ObjectInformation>());
//		
		failListObjects(user, new ArrayList<WorkspaceIdentifier>(), null, true, true, true, true,
				new IllegalArgumentException("At least one filter must be specified."));
		failListObjects(user2, Arrays.asList(wsi, wsi2), null, true, true, true, true,
				new WorkspaceAuthorizationException("User listObjUser2 may not read workspace listObj1"));
		failListObjects(null, Arrays.asList(wsi, wsi2), null, true, true, true, true,
				new WorkspaceAuthorizationException("Anonymous users may not read workspace listObj1"));
		failListObjects(user, Arrays.asList(wsi2, new WorkspaceIdentifier("listfake")), null, true, true, true, true,
				new NoSuchWorkspaceException("No workspace with name listfake exists", wsi));
		
		ws.createWorkspace(user, "listdel", false, null);
		ws.setWorkspaceDeleted(user, new WorkspaceIdentifier("listdel"), true);
		failListObjects(user, Arrays.asList(wsi2, new WorkspaceIdentifier("listdel")), null, true, true, true, true,
				new NoSuchWorkspaceException("Workspace listdel is deleted", wsi));
		
		assertThat("correct object history for std", 
				ws.getObjectHistory(user, new ObjectIdentifier(wsi, "std")),
				is(Arrays.asList(std)));
		assertThat("correct object history for type2", 
				ws.getObjectHistory(user, new ObjectIdentifier(wsi, "type2")),
				is(Arrays.asList(type2_1, type2_2, type2_3, type2_4)));
		assertThat("correct object history for type2", 
				ws.getObjectHistory(user, new ObjectIdentifier(wsi, 3)),
				is(Arrays.asList(type2_1, type2_2, type2_3, type2_4)));
		assertThat("correct object history for type2", 
				ws.getObjectHistory(user, new ObjectIdentifier(wsi, "type2", 3)),
				is(Arrays.asList(type2_1, type2_2, type2_3, type2_4)));
		assertThat("correct object history for type2", 
				ws.getObjectHistory(user, new ObjectIdentifier(wsi, 3, 4)),
				is(Arrays.asList(type2_1, type2_2, type2_3, type2_4)));
		assertThat("correct object history for objstack", 
				ws.getObjectHistory(user, new ObjectIdentifier(wsi, "objstack")),
				is(Arrays.asList(objstack1, objstack2)));
		assertThat("correct object history for stdws2", 
				ws.getObjectHistory(user2, new ObjectIdentifier(wsi2, "stdws2")),
				is(Arrays.asList(stdws2)));
		
		failGetObjectHistory(user, new ObjectIdentifier(wsi, "booger"),
				new NoSuchObjectException("No object with name booger exists in workspace " + wsid1));
		failGetObjectHistory(user, new ObjectIdentifier(new WorkspaceIdentifier("listObjectsfake"), "booger"),
				new InaccessibleObjectException("Object booger cannot be accessed: No workspace with name listObjectsfake exists"));
		failGetObjectHistory(user, new ObjectIdentifier(new WorkspaceIdentifier("listdel"), "booger"),
				new InaccessibleObjectException("Object booger cannot be accessed: Workspace listdel is deleted"));
		failGetObjectHistory(user2, new ObjectIdentifier(wsi, 3),
				new InaccessibleObjectException("Object 3 cannot be accessed: User listObjUser2 may not read workspace listObj1"));
		failGetObjectHistory(null, new ObjectIdentifier(wsi, 3),
				new InaccessibleObjectException("Object 3 cannot be accessed: Anonymous users may not read workspace listObj1"));
		failGetObjectHistory(user2, new ObjectIdentifier(wsi2, "deleted"),
				new InaccessibleObjectException("Object 3 (name deleted) in workspace " + wsid2 + " has been deleted"));
		
	}
	
	private void failGetObjectHistory(WorkspaceUser user,
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

	private List<ObjectInformation> setUpListObjectsExpected(List<ObjectInformation> expec,
			ObjectInformation possNull) {
		return setUpListObjectsExpected(expec, Arrays.asList(possNull));
	}
	
	private List<ObjectInformation> setUpListObjectsExpected(List<ObjectInformation> expec,
			List<ObjectInformation> possNull) {
		List<ObjectInformation> ret = new LinkedList<ObjectInformation>(expec);
		for (ObjectInformation oi: possNull) {
			if (oi != null) {
				ret.add(oi);
			}
		}
		return ret;
		
	}

	private void failListObjects(WorkspaceUser user,
			List<WorkspaceIdentifier> wsis, TypeDefId type, boolean showHidden,
			boolean showDeleted, boolean showAllVers, boolean includeMetaData,
			Exception e) {
		try {
			ws.listObjects(user, wsis, type, showHidden, showDeleted, showAllVers, includeMetaData);
			fail("listed obj when should fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}

	private void checkObjInfoList(List<ObjectInformation> got,
			List<ObjectInformation> expected) {
		HashSet<ObjectInformation> g = new HashSet<ObjectInformation>();
		for (ObjectInformation oi: got) {
			if (g.contains(oi)) {
				fail("Got same object info twice: " + oi);
			}
			g.add(oi);
		}
		assertThat("listed correct workspaces", g, is(new HashSet<ObjectInformation>(expected)));
	}
	
	@Test
	public void getObjectSubdata() throws Exception {
		WorkspaceUser user = new WorkspaceUser("subUser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("subData");
		WorkspaceUser user2 = new WorkspaceUser("subUser2");
		long wsid1 = ws.createWorkspace(user, wsi.getName(), false, null).getId();
		
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("metastuff", "meta");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("meta2", "my hovercraft is full of eels");
		
		Map<String, Object> data1 = createData(
				"{\"map\": {\"id1\": {\"id\": 1," +
				"					  \"thing\": \"foo\"}}," +
				"			\"id2\": {\"id\": 2," +
				"					  \"thing\": \"foo2\"}}," +
				"			\"id3\": {\"id\": 3," +
				"					  \"thing\": \"foo3\"},"
				);
		
		Map<String, Object> data2 = createData("{\"foo\": \"bar2\"}");
		System.out.println(data1);
		System.out.println(data2);
		
		
		//TODO provenance
		
		ObjectInformation o1 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("o1"), data1, SAFE_TYPE1, meta,
				new Provenance(user), false))).get(0);
		ObjectInformation o2 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("o2"), data2, SAFE_TYPE1, meta2,
				new Provenance(user), false))).get(0);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> createData(String json)
			throws JsonParseException, JsonMappingException, IOException {
		return new ObjectMapper().readValue(json, Map.class);
	}
}
