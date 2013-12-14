package us.kbase.workspace.test.workspaces;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DB;

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
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.ShockBackend;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;
import us.kbase.workspace.kbase.Util;
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspace.workspaces.WorkspaceSaveObject;
import us.kbase.workspace.workspaces.Workspaces;
import us.kbase.workspace.workspaces.ModuleInfo;

//TODO make sure ordered lists stay ordered
//TODO test subdata access from independent mongo DB instance
@RunWith(Parameterized.class)
public class TestWorkspaces {
	
	//true if no net access since shock requires access to globus to work
	private static final boolean SKIP_SHOCK = false;

	private static final ObjectMapper mapper = new ObjectMapper();
	
	public static final Workspaces[] TEST_WORKSPACES = new Workspaces[2];
	public static final String LONG_TEXT_PART = "Passersby were amazed by the unusually large amounts of blood. ";
	public static String LONG_TEXT = "";
	public static String TEXT100 = "";
	static {
		for (int i = 0; i < 10; i++) {
			TEXT100 += "aaaaabbbbb";
		}
	}
	public static String TEXT101 = TEXT100 + "f";
	public static String TEXT1000 = "";
	static {
		for (int i = 0; i < 10; i++) {
			TEXT1000 += TEXT100;
		}
	}
	
	public static ShockBackend sbe;
	
	public static final WorkspaceUser SOMEUSER = new WorkspaceUser("auser");
	public static final WorkspaceUser AUSER = new WorkspaceUser("a");
	public static final WorkspaceUser BUSER = new WorkspaceUser("b");
	public static final WorkspaceUser CUSER = new WorkspaceUser("c");
	public static final AllUsers STARUSER = new AllUsers('*');
	
	public static final TypeDefId SAFE_TYPE =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);

	@Parameters
	public static Collection<Object[]> generateData() throws Exception {
		//TODO use 1 db at a time, do all init in the TW constructor
		printMem("*** startup ***");
		setUpWorkspaces();
		List<Object[]> tests;
		if (SKIP_SHOCK) {
			System.out.println("Skipping shock backend tests");
			tests = Arrays.asList(new Object[][] {
					{TEST_WORKSPACES[0]}
			});
		} else {
			tests = Arrays.asList(new Object[][] {
					{TEST_WORKSPACES[0]},
					{TEST_WORKSPACES[1]}
			});
		}
		printMem("*** startup complete ***");
		return tests;
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (!SKIP_SHOCK) {
			System.out.println("deleting all shock nodes");
			sbe.removeAllBlobs();
		}
	}
	
	public final Workspaces ws;
	
	public static void setUpWorkspaces() throws Exception {
		String shockuser = System.getProperty("test.user1");
		String shockpwd = System.getProperty("test.pwd1");
		WorkspaceTestCommon.destroyAndSetupDB(1, "gridFS", null);
		DB data2 = WorkspaceTestCommon.destroyAndSetupDB(2, "shock", shockuser);
		String host = WorkspaceTestCommon.getHost();
		String mUser = WorkspaceTestCommon.getMongoUser();
		String mPwd = WorkspaceTestCommon.getMongoPwd();
		String db1 = WorkspaceTestCommon.getDB1();
		String db2 = WorkspaceTestCommon.getDB2();
		final String kidlpath = new Util().getKIDLpath();
		
		WorkspaceDatabase gfs = null;
		WorkspaceDatabase shock = null;
		if (mUser != null) {
			gfs = new MongoWorkspaceDB(host, db1, shockpwd, kidlpath, null,
					mUser, mPwd);
			if (!SKIP_SHOCK) {
				shock = new MongoWorkspaceDB(host, db2, shockpwd, kidlpath, null,
						mUser, mPwd);
			}
		} else {
			gfs = new MongoWorkspaceDB(host, db1, shockpwd, kidlpath, null);
			if (!SKIP_SHOCK) {
				shock = new MongoWorkspaceDB(host, db2, shockpwd, kidlpath, null);
			}
		}
		TEST_WORKSPACES[0] = new Workspaces(gfs, new DefaultReferenceParser());
		assertTrue("GridFS backend setup failed", TEST_WORKSPACES[0].getBackendType().equals("GridFS"));
		if (!SKIP_SHOCK) {
			TEST_WORKSPACES[1] = new Workspaces(shock, new DefaultReferenceParser());
			assertTrue("Shock backend setup failed", TEST_WORKSPACES[1].getBackendType().equals("Shock"));
			sbe = new ShockBackend(data2, "shock_",
					new URL(WorkspaceTestCommon.getShockUrl()), shockuser, shockpwd);
		}
		for (int i = 0; i < 17; i++) {
			LONG_TEXT += LONG_TEXT_PART;
		}
		//make a general spec that tests that don't worry about typechecking can use
		WorkspaceUser foo = new WorkspaceUser("foo");
		int backends = 1 + (SKIP_SHOCK ? 0 : 1);
		for (int i = 0; i < backends; i ++) {
			//simple spec
			TEST_WORKSPACES[i].requestModuleRegistration(foo, "SomeModule");
			TEST_WORKSPACES[i].resolveModuleRegistration("SomeModule", true);
			TEST_WORKSPACES[i].compileNewTypeSpec(foo, 
					"module SomeModule {/* @optional thing */ typedef structure {string thing;} AType;};",
					Arrays.asList("AType"), null, null, false, null);
			TEST_WORKSPACES[i].releaseTypes(foo, "SomeModule");
			
			// more complicated spec with two released versions and 1 unreleased version for type
			// registration tests
			TEST_WORKSPACES[i].requestModuleRegistration(foo, "TestModule");
			TEST_WORKSPACES[i].resolveModuleRegistration("TestModule", true);
			TEST_WORKSPACES[i].compileNewTypeSpec(foo, 
					"module TestModule { " +
							"typedef structure {string name; string seq;} Feature; "+
							"typedef structure {string name; list<Feature> features;} Genome; "+
							"typedef structure {string private_stuff;} InternalObj; "+
							"funcdef getFeature(string fid, string pattern) returns (Feature);" +
					"};",
					Arrays.asList("Feature","Genome"), null, null, false, null);
			TEST_WORKSPACES[i].releaseTypes(foo, "TestModule");
			TEST_WORKSPACES[i].compileNewTypeSpec(foo, 
					"module TestModule { " +
							"typedef structure {string name; string seq;} Feature; "+
							"typedef structure {string name; list<Feature> feature_list;} Genome; "+
							"typedef structure {string private_stuff;} InternalObj; "+
							"funcdef getFeature(string fid) returns (Feature);" +
					"};",
					null, null, null, false, null);
			TEST_WORKSPACES[i].compileNewTypeSpec(foo, 
					"module TestModule { " +
							"typedef structure {string name; string seq;} Feature; "+
							"typedef structure {string name; list<Feature> feature_list;} Genome; "+
							"typedef structure {string private_stuff;} InternalObj; "+
							"funcdef getFeature(string fid) returns (Feature);" +
							"funcdef getGenome(string gid) returns (Genome);" +
					"};",
					null, null, null, false, null);
			TEST_WORKSPACES[i].releaseTypes(foo, "TestModule");
			
			TEST_WORKSPACES[i].requestModuleRegistration(foo, "UnreleasedModule");
			TEST_WORKSPACES[i].resolveModuleRegistration("UnreleasedModule", true);
			TEST_WORKSPACES[i].compileNewTypeSpec(foo, 
					"module UnreleasedModule {/* @optional thing */ typedef structure {string thing;} AType;};",
					Arrays.asList("AType"), null, null, false, null);
		}
	}
	
	public TestWorkspaces(Workspaces ws) {
		this.ws = ws;
	}
	private static void printMem(String startmsg) {
		System.out.println(startmsg);
		System.out.println("free mem: " + Runtime.getRuntime().freeMemory());
		System.out.println(" max mem: " + Runtime.getRuntime().maxMemory());
		System.out.println(" ttl mem: " + Runtime.getRuntime().maxMemory());
	}
	
	@Test
	public void testWorkspaceDescription() throws Exception {
		ws.createWorkspace(SOMEUSER, "lt", false, LONG_TEXT);
		ws.createWorkspace(SOMEUSER, "ltp", false, LONG_TEXT_PART);
		ws.createWorkspace(SOMEUSER, "ltn", false, null);
		String desc = ws.getWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("lt"));
		assertThat("Workspace description incorrect", desc, is(LONG_TEXT.substring(0, 1000)));
		desc = ws.getWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("ltp"));
		assertThat("Workspace description incorrect", desc, is(LONG_TEXT_PART));
		desc = ws.getWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("ltn"));
		assertNull("Workspace description incorrect", desc);
		
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("lt");
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
	}
	
	private void checkWSMeta(WorkspaceInformation meta, WorkspaceUser owner, String name,
			long objs, Permission perm, boolean globalread, long id, Date moddate,
			String lockstate) {
		checkWSMeta(meta, owner, name, objs, perm, globalread, lockstate);
		assertThat("ws id correct", meta.getId(), is(id));
		assertThat("ws mod date correct", meta.getModDate(), is(moddate));
	}
	
	private void checkWSMeta(WorkspaceInformation meta, WorkspaceUser owner, String name,
			long objs, Permission perm, boolean globalread, String lockstate) {
		assertThat("ws owner correct", meta.getOwner(), is(owner));
		assertThat("ws name correct", meta.getName(), is(name));
		assertThat("ws max obj correct", meta.getApproximateObjects(), is(objs));
		assertThat("ws permissions correct", meta.getUserPermission(), is(perm));
		assertThat("ws global read correct", meta.isGloballyReadable(), is(globalread));
		assertThat("ws lockstate correct", meta.getLockState(), is(lockstate));
	}
	
	@Test
	public void testCreateWorkspaceAndGetMeta() throws Exception {
		WorkspaceInformation meta = ws.createWorkspace(SOMEUSER, "foo", false, "eeswaffertheen");
		checkWSMeta(meta, SOMEUSER, "foo", 0, Permission.OWNER, false, "unlocked");
		long id = meta.getId();
		WorkspaceIdentifier wsi = new WorkspaceIdentifier(id);
		Date moddate = meta.getModDate();
		meta = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier(id));
		checkWSMeta(meta, SOMEUSER, "foo", 0, Permission.OWNER, false, id, moddate, "unlocked");
		meta = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier("foo"));
		checkWSMeta(meta, SOMEUSER, "foo", 0, Permission.OWNER, false, id, moddate, "unlocked");
		
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
		meta = ws.createWorkspace(anotheruser, "anotherfnuser:MrT", true, "Ipitythefoolthatdon'teatMrTbreakfastcereal");
		checkWSMeta(meta, anotheruser, "anotherfnuser:MrT", 0, Permission.OWNER, true, "unlocked");
		id = meta.getId();
		moddate = meta.getModDate();
		meta = ws.getWorkspaceInformation(anotheruser, new WorkspaceIdentifier(id));
		checkWSMeta(meta, anotheruser, "anotherfnuser:MrT", 0, Permission.OWNER, true, id, moddate, "unlocked");
		meta = ws.getWorkspaceInformation(anotheruser, new WorkspaceIdentifier("anotherfnuser:MrT"));
		checkWSMeta(meta, anotheruser, "anotherfnuser:MrT", 0, Permission.OWNER, true, id, moddate, "unlocked");
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
		WorkspaceInformation meta= ws.getWorkspaceInformation(null, wsiGL);
		checkWSMeta(meta, AUSER, "perms_global", 0, Permission.NONE, true, "unlocked");
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
	}
	
	private void checkObjMeta(ObjectInformation meta, long id,
			String name, String type, int version, WorkspaceUser user,
			long wsid, String wsname, String chksum, long size,
			Map<String, String> usermeta) {
		assertThat("Date is a date class", meta.getSavedDate(), is(Date.class));
		assertThat("Object id correct", meta.getObjectId(), is(id));
		assertThat("Object name is correct", meta.getObjectName(), is(name));
		assertThat("Object type is correct", meta.getTypeString(), is(type));
		assertThat("Object version is correct", meta.getVersion(), is(version));
		assertThat("Object user is correct", meta.getSavedBy(), is(user));
		assertThat("Object workspace id is correct", meta.getWorkspaceId(), is(wsid));
		assertThat("Object workspace name is correct", meta.getWorkspaceName(), is(wsname));
		assertThat("Object chksum is correct", meta.getCheckSum(), is(chksum));
		assertThat("Object size is correct", meta.getSize(), is(size));
		assertThat("Object user meta is correct", meta.getUserMetaData(), is(usermeta));
	}
	
	@Test
	public void simpleSaveObjectsAndGetMeta() throws Exception {
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
		
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto3"), savedata, SAFE_TYPE, meta, p, false));
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto3"), savedata2, SAFE_TYPE, meta2, p, false));
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto3-1"), savedata, SAFE_TYPE, meta, p, false));
		objects.add(new WorkspaceSaveObject(savedata2, SAFE_TYPE, meta2, p, false));
		objects.add(new WorkspaceSaveObject(savedata, SAFE_TYPE, meta, p, false));
		List<ObjectInformation> objmeta = ws.saveObjects(foo, read, objects);
		String chksum1 = "36c4f68f2c98971b9736839232eb08f4";
		String chksum2 = "3c59f762140806c36ab48a152f28e840";
		checkObjMeta(objmeta.get(0), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjMeta(objmeta.get(1), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(objmeta.get(2), 2, "auto3-1", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjMeta(objmeta.get(3), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(objmeta.get(4), 4, "auto4", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		
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
		List<ObjectInformation> usermeta = ws.getObjectInformation(foo, loi, true);
		List<ObjectInformation> usermetaNoMeta = ws.getObjectInformation(foo, loi, false);
		checkObjMeta(usermeta.get(0), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(usermeta.get(1), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjMeta(usermeta.get(2), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(usermeta.get(3), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjMeta(usermeta.get(4), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(usermeta.get(5), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjMeta(usermeta.get(6), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(usermeta.get(7), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjMeta(usermeta.get(8), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(usermeta.get(9), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(usermeta.get(10), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(usermeta.get(11), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(usermetaNoMeta.get(0), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, null);
		checkObjMeta(usermetaNoMeta.get(1), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, null);
		checkObjMeta(usermetaNoMeta.get(2), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, null);
		checkObjMeta(usermetaNoMeta.get(3), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, null);
		checkObjMeta(usermetaNoMeta.get(4), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, null);
		checkObjMeta(usermetaNoMeta.get(5), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, null);
		checkObjMeta(usermetaNoMeta.get(6), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, null);
		checkObjMeta(usermetaNoMeta.get(7), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, null);
		checkObjMeta(usermetaNoMeta.get(8), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, null);
		checkObjMeta(usermetaNoMeta.get(9), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, null);
		checkObjMeta(usermetaNoMeta.get(10), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, null);
		checkObjMeta(usermetaNoMeta.get(11), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, null);
		checkObjMeta(retdata.get(0).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(retdata.get(1).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjMeta(retdata.get(2).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(retdata.get(3).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjMeta(retdata.get(4).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(retdata.get(5).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjMeta(retdata.get(6).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(retdata.get(7).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjMeta(retdata.get(8).getMeta(), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(retdata.get(9).getMeta(), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(retdata.get(10).getMeta(), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjMeta(retdata.get(11).getMeta(), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
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
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer(2), savedata, SAFE_TYPE, meta2, p, false));
		objmeta = ws.saveObjects(foo, read, objects);
		ws.saveObjects(foo, priv, objects);
		checkObjMeta(objmeta.get(0), 2, "auto3-1", SAFE_TYPE.getTypeString(), 2, foo, readid, read.getName(), chksum1, 23, meta2);
		usermeta = ws.getObjectInformation(foo, Arrays.asList(new ObjectIdentifier(read, 2)), true);
		checkObjMeta(usermeta.get(0), 2, "auto3-1", SAFE_TYPE.getTypeString(), 2, foo, readid, read.getName(), chksum1, 23, meta2);
		
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
		usermeta = ws.getObjectInformation(bar, Arrays.asList(new ObjectIdentifier(priv, 2)), true);
		checkObjMeta(usermeta.get(0), 2, "auto3-1", SAFE_TYPE.getTypeString(), 2, foo, privid, priv.getName(), chksum1, 23, meta2);
		retdata = ws.getObjects(bar, Arrays.asList(new ObjectIdentifier(priv, 2)));
		checkObjMeta(retdata.get(0).getMeta(), 2, "auto3-1", SAFE_TYPE.getTypeString(), 2, foo, privid, priv.getName(), chksum1, 23, meta2);
		assertThat("correct data", retdata.get(0).getData(), is((Object) data));
		try {
			ws.saveObjects(bar, priv, objects);
			fail("saved objects to unwritable workspace");
		} catch (WorkspaceAuthorizationException auth) {
			assertThat("correct exception message", auth.getLocalizedMessage(),
					is("User bar may not write to workspace saveobj"));
		}
		ws.setPermissions(foo, priv, Arrays.asList(bar), Permission.WRITE);
		objmeta = ws.saveObjects(bar, priv, objects);
		checkObjMeta(objmeta.get(0), 2, "auto3-1", SAFE_TYPE.getTypeString(), 3, bar, privid, priv.getName(), chksum1, 23, meta2);
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
				new WorkspaceSaveObject(newdata, SAFE_TYPE , null, emptyprov, false)));
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
				new WorkspaceSaveObject(data, SAFE_TYPE, null, emptyprov, false)));
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE, null, emptyprov, false)));
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE, null, emptyprov, false)));
		
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto1"), data, SAFE_TYPE, null, emptyprov, false)));
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto1"), data, SAFE_TYPE, null, emptyprov, false)));
		
		
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
				new WorkspaceSaveObject(data, SAFE_TYPE, null, p, false)));
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("provenance/auto3", wsid + "/3/1");
		refmap.put("provenance/auto1/2", wsid + "/1/2");
		refmap.put("provenance/auto2/1", wsid + "/2/1");
		refmap.put("provenance/auto1", wsid + "/1/3");
		
		Provenance got = ws.getObjects(foo, Arrays.asList(new ObjectIdentifier(prov, 4))).get(0).getProvenance();
		checkProvenanceCorrect(p, got, refmap);
		
		try {
			new WorkspaceSaveObject(data, SAFE_TYPE, null, null, false);
			fail("saved without provenance");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Neither data, provenance, nor type may be null"));
		}
		try {
			new WorkspaceSaveObject(new ObjectIDNoWSNoVer("foo"), SAFE_TYPE, null, null, false);
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
				new WorkspaceSaveObject(data, SAFE_TYPE, null, p2, false)));
		Provenance got2 = ws.getObjects(foo, Arrays.asList(new ObjectIdentifier(prov, 5))).get(0).getProvenance();
		Date date2 = got2.getDate();
		checkProvenanceCorrect(p2, got2, new HashMap<String, String>());
		got2 = ws.getObjects(foo, Arrays.asList(new ObjectIdentifier(prov, 5))).get(0).getProvenance();
		assertThat("Prov date constant", got2.getDate(), is(date2));
		//make sure passing nulls for ws obj lists doesn't kill anything
		Provenance p3 = new Provenance(foo);
		p3.addAction(new ProvenanceAction().withWorkspaceObjects(null));
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE, null, p3, false)));
		Provenance got3 = ws.getObjects(foo, Arrays.asList(new ObjectIdentifier(prov, 6))).get(0).getProvenance();
		checkProvenanceCorrect(p3, got3, new HashMap<String, String>());
		
		Provenance p4 = new Provenance(foo);
		ProvenanceAction pa = new ProvenanceAction();
		pa.setWorkspaceObjects(null);
		p4.addAction(pa);
		p3.addAction(new ProvenanceAction().withWorkspaceObjects(null));
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE, null, p4, false)));
		Provenance got4 = ws.getObjects(foo, Arrays.asList(new ObjectIdentifier(prov, 7))).get(0).getProvenance();
		checkProvenanceCorrect(p4, got4, new HashMap<String, String>());
	}
	
	private Date getOlderDate(long ms) {
		long now = new Date().getTime();
		return new Date(now - ms);
	}
	
	private void checkProvenanceCorrect(Provenance expected, Provenance got,
			Map<String, String> refmap) {
		assertThat("user equal", got.getUser(), is(expected.getUser()));
		assertThat("same number actions", got.getActions().size(),
				is(expected.getActions().size()));
		assertTrue("date within the last 10 mins",
				got.getDate().after(getOlderDate(10 * 60 * 1000)));
		
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
			Iterator<String> gotrefs = gotpa.getWorkspaceObjects().iterator();
			Iterator<String> gotresolvedrefs = gotpa.getResolvedObjects().iterator();
			while (gotrefs.hasNext()) {
				assertThat("ref resolved correctly", gotresolvedrefs.next(),
						is(refmap.get(gotrefs.next())));
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
				new WorkspaceSaveObject(data, SAFE_TYPE, null, p, false)));
		
		
		methparams.add(TEXT1000);
		Provenance p2 = new Provenance(foo);
		p2.addAction(new ProvenanceAction().withMethodParameters(methparams));
		try {
			ws.saveObjects(foo, prov, Arrays.asList(
					new WorkspaceSaveObject(data, SAFE_TYPE, null, p, false)));
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
	
	@Test
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
				new WorkspaceSaveObject(data, SAFE_TYPE, null, new Provenance(userfoo), false)));
//		printMem("*** saved object ***");
		subdata.add("" + TEXT1000);
		try {
			ws.saveObjects(userfoo, bigdataws, Arrays.asList(
					new WorkspaceSaveObject(data, SAFE_TYPE, null, new Provenance(userfoo), false)));
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
	
	@Test
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
	
	@Test
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
				new WorkspaceSaveObject(data, SAFE_TYPE, null, new Provenance(userfoo), false)));
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
				new WorkspaceSaveObject(data, SAFE_TYPE, null, new Provenance(userfoo), false)));
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
					new ObjectIDNoWSNoVer("bigmeta"), savedata, SAFE_TYPE, meta,
					new Provenance(foo), false)));
			fail("saved object with > 16kb metadata");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Metadata is > 16000 bytes"));
		}
		try {
			ws.saveObjects(foo, read, Arrays.asList(new WorkspaceSaveObject(
					new ObjectIDNoWSNoVer(3), savedata, SAFE_TYPE, meta,
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
					new ObjectIDNoWSNoVer(3), savedata, SAFE_TYPE, null,
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
					new ObjectIDNoWSNoVer("jframe"), data, SAFE_TYPE, meta,
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
				savedata, SAFE_TYPE, null, new Provenance(foo), false));
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
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceIdentifier read = new WorkspaceIdentifier("deleteundelete");
		long wsid = ws.createWorkspace(foo, read.getIdentifierString(), false, "descrip").getId();
		Map<String, String> data1 = new HashMap<String, String>();
		Map<String, String> data2 = new HashMap<String, String>();
		data1.put("data", "1");
		data2.put("data", "2");
		WorkspaceSaveObject sobj1 = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("obj"), data1, SAFE_TYPE, null, new Provenance(foo), false);
		ws.saveObjects(foo, read, Arrays.asList(sobj1,
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("obj"), data2, SAFE_TYPE,
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
						SAFE_TYPE, null, new Provenance(foo), false)));
		ObjectIdentifier o3 = new ObjectIdentifier(read, "obj", 3);
		idToData.put(o3, data1);
		objs = new ArrayList<ObjectIdentifier>(idToData.keySet());
		
		checkNonDeletedObjs(foo, idToData);
		assertThat("can get ws description", ws.getWorkspaceDescription(foo, read),
				is("descrip"));
		checkWSMeta(ws.getWorkspaceInformation(foo, read), foo, "deleteundelete", 1, Permission.OWNER, false, "unlocked");
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
		ws.setWorkspaceDeleted(foo, read, true);
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
		checkNonDeletedObjs(foo, idToData);
		assertThat("can get ws description", ws.getWorkspaceDescription(foo, read),
				is("descrip"));
		checkWSMeta(ws.getWorkspaceInformation(foo, read), foo, "deleteundelete", 1, Permission.OWNER, false, "unlocked");
		ws.setPermissions(foo, read, Arrays.asList(bar), Permission.ADMIN);
		assertThat("can get perms", ws.getPermissions(foo, read), is(p));
		
	}

	private void checkNonDeletedObjs(WorkspaceUser foo,
			Map<ObjectIdentifier, Object> idToData) throws CorruptWorkspaceDBException,
			WorkspaceCommunicationException, InaccessibleObjectException {
		List<ObjectIdentifier> objs = new ArrayList<ObjectIdentifier>(idToData.keySet());
		List<WorkspaceObjectData> d = ws.getObjects(foo, objs);
		for (int i = 0; i < d.size(); i++) {
			assertThat("can get correct data from undeleted objects",
					d.get(i).getData(), is((Object) idToData.get(objs.get(i))));
		}
	}

	private void failToGetDeletedObjects(WorkspaceUser user,
			List<ObjectIdentifier> objs, String exception) throws Exception,
			WorkspaceCommunicationException, WorkspaceAuthorizationException {
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
		Map<String,String> type2md5 = ws.translateToMd5Types(Arrays.asList(typeDefName));
		Assert.assertEquals(1, type2md5.size());
		String md5TypeDef = type2md5.get(typeDefName);
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
		Assert.assertEquals(1, ws.getModuleVersions("SomeModule", null).size());
		Assert.assertEquals(2, ws.getModuleVersions("SomeModule", new WorkspaceUser("foo")).size());
		Assert.assertEquals(2, ws.getModuleVersions("TestModule", null).size());
		Assert.assertEquals(5, ws.getModuleVersions("TestModule", new WorkspaceUser("foo")).size());
	}
	
	@Test
	public void testGetModuleInfo() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		ModuleInfo m = ws.getModuleInfo(new ModuleDefId("TestModule"), null);
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
			ws.getModuleInfo(new ModuleDefId("MadeUpModuleThatIsNotThere"), null);
			fail("getModuleInfo of non existant module should throw a NoSuchModuleException");
		} catch (NoSuchModuleException e) {}
		ModuleInfo m2 = ws.getModuleInfo(new ModuleDefId("UnreleasedModule"), new WorkspaceUser("foo"));
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
	
	
}
