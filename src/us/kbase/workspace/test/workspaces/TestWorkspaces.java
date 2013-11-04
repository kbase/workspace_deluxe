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
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DB;

import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.typedobj.exceptions.TypedObjectValidationException;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.DefaultReferenceParser;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.ObjectInfoUserMeta;
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
//import us.kbase.workspace.database.mongo.ObjectIDResolvedWSNoVer;
import us.kbase.workspace.database.mongo.ShockBackend;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;
import us.kbase.workspace.kbase.Util;
import us.kbase.common.test.TestException;
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspace.workspaces.WorkspaceSaveObject;
import us.kbase.workspace.workspaces.Workspaces;


//TODO make sure ordered lists stay ordered
//TODO test subdata access from independent mongo DB instance
@RunWith(Parameterized.class)
public class TestWorkspaces {

	private static final ObjectMapper mapper = new ObjectMapper();
	
	public static final Workspaces[] TEST_WORKSPACES = new Workspaces[2];
	public static final String LONG_TEXT_PART = "Passersby were amazed by the unusually large amounts of blood. ";
	public static String LONG_TEXT = "";
	public static String TEXT101;
	static {
		for (int i = 0; i < 10; i++) {
			TEXT101 += "aaaaabbbbb";
		}
		TEXT101 += "f";
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
		setUpWorkspaces();
		return Arrays.asList(new Object[][] {
				{TEST_WORKSPACES[0]},
				{TEST_WORKSPACES[1]}
		});
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		System.out.println("deleting all shock nodes");
		sbe.removeAllBlobs();
	}
	
	public final Workspaces ws;
	
	public static void setUpWorkspaces() throws Exception {
		String shockuser = System.getProperty("test.user.noemail");
		String shockpwd = System.getProperty("test.pwd.noemail");
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
			shock = new MongoWorkspaceDB(host, db2, shockpwd, kidlpath, null,
					mUser, mPwd);
		} else {
			gfs = new MongoWorkspaceDB(host, db1, shockpwd, kidlpath, null);
			shock = new MongoWorkspaceDB(host, db2, shockpwd, kidlpath, null);
		}
		TEST_WORKSPACES[0] = new Workspaces(gfs, new DefaultReferenceParser());
		assertTrue("GridFS backend setup failed", TEST_WORKSPACES[0].getBackendType().equals("GridFS"));
		TEST_WORKSPACES[1] = new Workspaces(shock, new DefaultReferenceParser());
		assertTrue("Shock backend setup failed", TEST_WORKSPACES[1].getBackendType().equals("Shock"));
		sbe = new ShockBackend(data2.getCollection("shockData"),
				new URL(WorkspaceTestCommon.getShockUrl()), shockuser, shockpwd);
		for (int i = 0; i < 17; i++) {
			LONG_TEXT += LONG_TEXT_PART;
		}
		//make a general spec that tests that don't worry about typechecking can use
		WorkspaceUser foo = new WorkspaceUser("foo");
		for (Integer i: Arrays.asList(0,1)) {
			TEST_WORKSPACES[i].requestModuleRegistration(foo, "SomeModule");
			TEST_WORKSPACES[i].resolveModuleRegistration("SomeModule", true);
			TEST_WORKSPACES[i].compileNewTypeSpec(foo, 
					"module SomeModule {/* @optional thing */ typedef structure {string thing;} AType;};",
					Arrays.asList("AType"), null, null, false, null);
			TEST_WORKSPACES[i].releaseTypes(foo, "SomeModule");
		}
	}
	
	public TestWorkspaces(Workspaces ws) {
		this.ws = ws;
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
			Permission perm, boolean globalread, long id, Date moddate) {
		checkWSMeta(meta, owner, name, perm, globalread);
		assertThat("ws id correct", meta.getId(), is(id));
		assertThat("ws mod date correct", meta.getModDate(), is(moddate));
	}
	
	private void checkWSMeta(WorkspaceInformation meta, WorkspaceUser owner, String name,
			Permission perm, boolean globalread) {
		assertThat("ws owner correct", meta.getOwner(), is(owner));
		assertThat("ws name correct", meta.getName(), is(name));
		assertThat("ws permissions correct", meta.getUserPermission(), is(perm));
		assertThat("ws global read correct", meta.isGloballyReadable(), is(globalread));
	}
	
	@Test
	public void testCreateWorkspaceAndGetMeta() throws Exception {
		WorkspaceInformation meta = ws.createWorkspace(SOMEUSER, "foo", false, "eeswaffertheen");
		checkWSMeta(meta, SOMEUSER, "foo", Permission.OWNER, false);
		long id = meta.getId();
		WorkspaceIdentifier wsi = new WorkspaceIdentifier(id);
		Date moddate = meta.getModDate();
		meta = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier(id));
		checkWSMeta(meta, SOMEUSER, "foo", Permission.OWNER, false, id, moddate);
		meta = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier("foo"));
		checkWSMeta(meta, SOMEUSER, "foo", Permission.OWNER, false, id, moddate);
		
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
		checkWSMeta(meta, anotheruser, "anotherfnuser:MrT", Permission.OWNER, true);
		id = meta.getId();
		moddate = meta.getModDate();
		meta = ws.getWorkspaceInformation(anotheruser, new WorkspaceIdentifier(id));
		checkWSMeta(meta, anotheruser, "anotherfnuser:MrT", Permission.OWNER, true, id, moddate);
		meta = ws.getWorkspaceInformation(anotheruser, new WorkspaceIdentifier("anotherfnuser:MrT"));
		checkWSMeta(meta, anotheruser, "anotherfnuser:MrT", Permission.OWNER, true, id, moddate);
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
				+ SOMEUSER + " prior to the : delimiter"));
		
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
		checkWSMeta(meta, AUSER, "perms_global", Permission.NONE, true);
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
	
	private void checkObjMeta(ObjectInformation meta, long id, String name, String type,
			int version, WorkspaceUser user, long wsid, String chksum, long size) {
		if (meta instanceof ObjectInfoUserMeta) {
			throw new TestException("missed testing meta");
		}
		assertThat("Date is a date class", meta.getCreatedDate(), is(Date.class));
		assertThat("Object id correct", meta.getObjectId(), is(id));
		assertThat("Object name is correct", meta.getObjectName(), is(name));
		assertThat("Object type is correct", meta.getTypeString(), is(type));
		assertThat("Object version is correct", meta.getVersion(), is(version));
		assertThat("Object user is correct", meta.getCreator(), is(user));
		assertThat("Object workspace id is correct", meta.getWorkspaceId(), is(wsid));
		assertThat("Object chksum is correct", meta.getCheckSum(), is(chksum));
		assertThat("Object size is correct", meta.getSize(), is(size));
	}
	
	private void checkObjMeta(ObjectInfoUserMeta meta, long id,
			String name, String type, int version, WorkspaceUser user,
			long wsid, String chksum, long size, Map<String, String> usermeta) {
		assertThat("Date is a date class", meta.getCreatedDate(), is(Date.class));
		assertThat("Object id correct", meta.getObjectId(), is(id));
		assertThat("Object name is correct", meta.getObjectName(), is(name));
		assertThat("Object type is correct", meta.getTypeString(), is(type));
		assertThat("Object version is correct", meta.getVersion(), is(version));
		assertThat("Object user is correct", meta.getCreator(), is(user));
		assertThat("Object workspace id is correct", meta.getWorkspaceId(), is(wsid));
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
			ws.getObjectInformation(foo, new ArrayList<ObjectIdentifier>());
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
		checkObjMeta(objmeta.get(0), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum1, 23);
		checkObjMeta(objmeta.get(1), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, chksum2, 24);
		checkObjMeta(objmeta.get(2), 2, "auto3-1", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum1, 23);
		checkObjMeta(objmeta.get(3), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum2, 24);
		checkObjMeta(objmeta.get(4), 4, "auto4", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum1, 23);
		
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
		List<ObjectInfoUserMeta> usermeta = ws.getObjectInformation(foo, loi);
		checkObjMeta(usermeta.get(0), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, chksum2, 24, meta2);
		checkObjMeta(usermeta.get(1), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum1, 23, meta);
		checkObjMeta(usermeta.get(2), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, chksum2, 24, meta2);
		checkObjMeta(usermeta.get(3), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum1, 23, meta);
		checkObjMeta(usermeta.get(4), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, chksum2, 24, meta2);
		checkObjMeta(usermeta.get(5), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum1, 23, meta);
		checkObjMeta(usermeta.get(6), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, chksum2, 24, meta2);
		checkObjMeta(usermeta.get(7), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum1, 23, meta);
		checkObjMeta(usermeta.get(8), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum2, 24, meta2);
		checkObjMeta(usermeta.get(9), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum2, 24, meta2);
		checkObjMeta(usermeta.get(10), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum2, 24, meta2);
		checkObjMeta(usermeta.get(11), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum2, 24, meta2);
		checkObjMeta(retdata.get(0).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, chksum2, 24, meta2);
		checkObjMeta(retdata.get(1).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum1, 23, meta);
		checkObjMeta(retdata.get(2).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, chksum2, 24, meta2);
		checkObjMeta(retdata.get(3).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum1, 23, meta);
		checkObjMeta(retdata.get(4).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, chksum2, 24, meta2);
		checkObjMeta(retdata.get(5).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum1, 23, meta);
		checkObjMeta(retdata.get(6).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 2, foo, readid, chksum2, 24, meta2);
		checkObjMeta(retdata.get(7).getMeta(), 1, "auto3", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum1, 23, meta);
		checkObjMeta(retdata.get(8).getMeta(), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum2, 24, meta2);
		checkObjMeta(retdata.get(9).getMeta(), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum2, 24, meta2);
		checkObjMeta(retdata.get(10).getMeta(), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum2, 24, meta2);
		checkObjMeta(retdata.get(11).getMeta(), 3, "auto3-2", SAFE_TYPE.getTypeString(), 1, foo, readid, chksum2, 24, meta2);
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
		checkObjMeta(objmeta.get(0), 2, "auto3-1", SAFE_TYPE.getTypeString(), 2, foo, readid, chksum1, 23);
		usermeta = ws.getObjectInformation(foo, Arrays.asList(new ObjectIdentifier(read, 2)));
		checkObjMeta(usermeta.get(0), 2, "auto3-1", SAFE_TYPE.getTypeString(), 2, foo, readid, chksum1, 23, meta2);
		
		ws.getObjectInformation(bar, Arrays.asList(new ObjectIdentifier(read, 2))); //should work
		try {
			ws.getObjectInformation(bar, Arrays.asList(new ObjectIdentifier(priv, 2)));
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
		usermeta = ws.getObjectInformation(bar, Arrays.asList(new ObjectIdentifier(priv, 2)));
		checkObjMeta(usermeta.get(0), 2, "auto3-1", SAFE_TYPE.getTypeString(), 2, foo, privid, chksum1, 23, meta2);
		retdata = ws.getObjects(bar, Arrays.asList(new ObjectIdentifier(priv, 2)));
		checkObjMeta(retdata.get(0).getMeta(), 2, "auto3-1", SAFE_TYPE.getTypeString(), 2, foo, privid, chksum1, 23, meta2);
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
		checkObjMeta(objmeta.get(0), 2, "auto3-1", SAFE_TYPE.getTypeString(), 3, bar, privid, chksum1, 23);
	}
	
	public static final String TEST_TYPE_CHECKING1 =
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
	
	public static final String TEST_TYPE_CHECKING2 =
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
	
	@Test
	public void saveObjectWithTypeChecking() throws Exception {
		//TODO test ref rewriting
		//TODO test full provenance save, retrieve, ref rewrite
		String mod = "TestTypeChecking";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		ws.requestModuleRegistration(userfoo, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(userfoo, TEST_TYPE_CHECKING1, Arrays.asList("CheckType"), null, null, false, null);
		TypeDefId abstype0 = new TypeDefId(new TypeDefName(mod, "CheckType"), 0, 1);
		TypeDefId abstype1 = new TypeDefId(new TypeDefName(mod, "CheckType"), 1, 0);
		TypeDefId abstype2 = new TypeDefId(new TypeDefName(mod, "CheckType"), 2, 0);
		TypeDefId relmintype0 = new TypeDefId(new TypeDefName(mod, "CheckType"), 0);
		TypeDefId relmintype1 = new TypeDefId(new TypeDefName(mod, "CheckType"), 1);
		TypeDefId relmintype2 = new TypeDefId(new TypeDefName(mod, "CheckType"), 2);
		TypeDefId relmaxtype = new TypeDefId(new TypeDefName(mod, "CheckType"));
		
		
		WorkspaceIdentifier wspace = new WorkspaceIdentifier("typecheck");
		ws.createWorkspace(userfoo, wspace.getName(), false, null);
		Provenance emptyprov = new Provenance(userfoo);
		Map<String, Object> data1 = new HashMap<String, Object>();
		data1.put("foo", 3);
		data1.put("baz", "astring");
		data1.put("bar", Arrays.asList(-3, 1, 234567890));
		List<WorkspaceSaveObject> data = new ArrayList<WorkspaceSaveObject>();
		data.add(new WorkspaceSaveObject(data1, abstype0, null, emptyprov, false));
		
		ws.saveObjects(userfoo, wspace, data); //should work
		failSave(userfoo, wspace, data1, relmintype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: TestTypeChecking.CheckType-0"));
		failSave(userfoo, wspace, data1, relmintype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: TestTypeChecking.CheckType-1"));
		try {
			//TODO the below should work, but exception is wrong
//			failSave(userfoo, wspace, data1, abstype1, emptyprov,
//					new TypedObjectValidationException(
//							"Object #1 failed type checking:\nType schema record was not found for TestTypeChecking.CheckType.1.0"));
			ws.saveObjects(userfoo, wspace, Arrays.asList(
					new WorkspaceSaveObject(data1, abstype1, null, emptyprov, false)));
		} catch (TypeStorageException tse) {
			//TODO this is a bug, shouldn't have to catch here
		}
		failSave(userfoo, wspace, data1, relmaxtype, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: TestTypeChecking.CheckType"));
		
		ws.releaseTypes(userfoo, mod);
		
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, relmaxtype, null, emptyprov, false)));
		
		ws.saveObjects(userfoo, wspace, data); //should still work
		failSave(userfoo, wspace, data1, relmintype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: TestTypeChecking.CheckType-0"));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, relmintype1, null, emptyprov, false)));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, relmaxtype, null, emptyprov, false)));
		
		ws.compileNewTypeSpec(userfoo, TEST_TYPE_CHECKING2, null, null, null, false, null);
		
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, relmaxtype, null, emptyprov, false)));
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
		failSave(userfoo, wspace, newdata, relmaxtype, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, newdata, relmintype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		
		
		
		
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
	}
	
	private void failSave(WorkspaceUser user, WorkspaceIdentifier wsi, 
			Map<String, Object> data, TypeDefId type, Provenance prov,
			Throwable exception) throws Exception{
		try {
			ws.saveObjects(user, wsi, Arrays.asList(
					new WorkspaceSaveObject(data, type, null, prov, false)));
			fail("Saved bad object");
		} catch (Exception e) {
			assertThat("correct exception type", e, is(exception.getClass()));
			assertThat("correct exception", e.getLocalizedMessage(),
					is(exception.getLocalizedMessage()));
		}
	}
	
	@Test
	public void bigUserMetaAndObjectErrors() throws Exception {
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
			fail("saved object with > 16Mb metadata");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Metadata is > 16000 bytes"));
		}
		try {
			ws.saveObjects(foo, read, Arrays.asList(new WorkspaceSaveObject(
					new ObjectIDNoWSNoVer(3), savedata, SAFE_TYPE, meta,
					new Provenance(foo), false)));
			fail("saved object with > 16Mb metadata");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Metadata is > 16000 bytes"));
		}
		
//		List<WorkspaceSaveObject> objects = new ArrayList<WorkspaceSaveObject>();
//		objects.add(new WorkspaceSaveObject(new WorkspaceObjectID("foo"), savedata, SAFE_TYPE, smallmeta, null, false));
//		objects.add(new WorkspaceSaveObject(new WorkspaceObjectID("foo1"), savedata, SAFE_TYPE, meta, null, false));
//		try {
//			ws.saveObjects(foo, read, objects);
//			fail("saved object with > 16Mb metadata");
//		} catch (IllegalArgumentException iae) {
//			assertThat("correct exception", iae.getLocalizedMessage(),
//					is("Metadata is > 16000 bytes"));
//		}
//		objects.clear();
//		objects.add(new WorkspaceSaveObject(new WorkspaceObjectID("foo"), savedata, SAFE_TYPE, smallmeta, null, false));
//		objects.add(new WorkspaceSaveObject(savedata, SAFE_TYPE, meta, null, false));
//		try {
//			ws.saveObjects(foo, read, objects);
//			fail("saved object with > 16Mb metadata");
//		} catch (IllegalArgumentException iae) {
//			assertThat("correct exception", iae.getLocalizedMessage(),
//					is("Metadata is > 16000 bytes"));
//		}
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
			ws.getObjectInformation(foo, Arrays.asList(oi));
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
		checkWSMeta(ws.getWorkspaceInformation(foo, read), foo, "deleteundelete", Permission.OWNER, false);
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
			ws.getObjectInformation(bar, objs);
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
		checkWSMeta(ws.getWorkspaceInformation(foo, read), foo, "deleteundelete", Permission.OWNER, false);
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
			ws.getObjectInformation(user, objs);
			fail("got deleted object's metadata");
		} catch (NoSuchObjectException e) {
			assertThat("correct exception", e.getLocalizedMessage(), is(exception));
		}
	}
	
	@Test
	public void testTypeMd5s() throws Exception {
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
	public void testListModuleVersions() throws Exception {
		Assert.assertEquals(1, ws.getModuleVersions("SomeModule", null).size());
		Assert.assertEquals(2, ws.getModuleVersions("SomeModule", new WorkspaceUser("foo")).size());
	}
}
