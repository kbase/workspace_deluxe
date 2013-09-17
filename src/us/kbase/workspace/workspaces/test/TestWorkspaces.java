package us.kbase.workspace.workspaces.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectMetaData;
import us.kbase.workspace.database.ObjectUserMetaData;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceMetaData;
import us.kbase.workspace.database.WorkspaceObjectID;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.mongo.MongoDatabase;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspace.workspaces.Provenance;
import us.kbase.workspace.workspaces.TypeId;
import us.kbase.workspace.workspaces.WorkspaceSaveObject;
import us.kbase.workspace.workspaces.WorkspaceType;
import us.kbase.workspace.workspaces.Workspaces;

//TODO test vs. auth'd mongo
@RunWith(Parameterized.class)
public class TestWorkspaces {

	public static final Workspaces[] TEST_WORKSPACES = new Workspaces[2];
	public static final String LONG_TEXT_PART = "Passersby were amazed by the unusually large amounts of blood. ";
	public static String LONG_TEXT = "";
	
	public static final WorkspaceUser SOMEUSER = new WorkspaceUser("auser");
	public static final WorkspaceUser AUSER = new WorkspaceUser("a");
	public static final WorkspaceUser BUSER = new WorkspaceUser("b");
	public static final WorkspaceUser CUSER = new WorkspaceUser("c");
	public static final AllUsers STARUSER = new AllUsers('*');

	@Parameters
	public static Collection<Object[]> generateData() throws Exception {
		setUpWorkspaces();
		return Arrays.asList(new Object[][] {
				{TEST_WORKSPACES[0]},
				{TEST_WORKSPACES[1]}
		});
	}
	
	public final Workspaces ws;
	
	public static void setUpWorkspaces() throws Exception {
		String shockuser = System.getProperty("test.user.noemail");
		String shockpwd = System.getProperty("test.pwd.noemail");
		WorkspaceTestCommon.destroyAndSetupDB(1, "gridFS", null);
		WorkspaceTestCommon.destroyAndSetupDB(2, "shock", shockuser);
		String host = WorkspaceTestCommon.getHost();
		String mUser = WorkspaceTestCommon.getMongoUser();
		String mPwd = WorkspaceTestCommon.getMongoPwd();
		String db1 = WorkspaceTestCommon.getDB1();
		String db2 = WorkspaceTestCommon.getDB2();
		
		Database gfs = null;
		Database shock = null;
		if (mUser != null) {
			gfs = new MongoDatabase(host, db1, shockpwd, mUser, mPwd);
			shock = new MongoDatabase(host, db2, shockpwd, mUser, mPwd);
		} else {
			gfs = new MongoDatabase(host, db1, shockpwd);
			shock = new MongoDatabase(host, db2, shockpwd);
		}
		TEST_WORKSPACES[0] = new Workspaces(gfs);
		assertTrue("GridFS backend setup failed", TEST_WORKSPACES[0].getBackendType().equals("GridFS"));
		TEST_WORKSPACES[1] = new Workspaces(shock);
		assertTrue("Shock backend setup failed", TEST_WORKSPACES[1].getBackendType().equals("Shock"));
		
		for (int i = 0; i < 17; i++) {
			LONG_TEXT += LONG_TEXT_PART;
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
	
	private void checkWSMeta(WorkspaceMetaData meta, WorkspaceUser owner, String name,
			Permission perm, boolean globalread, int id, Date moddate) {
		checkWSMeta(meta, owner, name, perm, globalread);
		assertThat("ws id correct", meta.getId(), is(id));
		assertThat("ws mod date correct", meta.getModDate(), is(moddate));
	}
	
	private void checkWSMeta(WorkspaceMetaData meta, WorkspaceUser owner, String name,
			Permission perm, boolean globalread) {
		assertThat("ws owner correct", meta.getOwner(), is(owner));
		assertThat("ws name correct", meta.getName(), is(name));
		assertThat("ws permissions correct", meta.getUserPermission(), is(perm));
		assertThat("ws global read correct", meta.isGloballyReadable(), is(globalread));
	}
	
	@Test
	public void testCreateWorkspaceAndGetMeta() throws Exception {
		WorkspaceMetaData meta = ws.createWorkspace(SOMEUSER, "foo", false, "eeswaffertheen");
		checkWSMeta(meta, SOMEUSER, "foo", Permission.OWNER, false);
		int id = meta.getId();
		WorkspaceIdentifier wsi = new WorkspaceIdentifier(id);
		Date moddate = meta.getModDate();
		meta = ws.getWorkspaceMetaData(SOMEUSER, new WorkspaceIdentifier(id));
		checkWSMeta(meta, SOMEUSER, "foo", Permission.OWNER, false, id, moddate);
		meta = ws.getWorkspaceMetaData(SOMEUSER, new WorkspaceIdentifier("foo"));
		checkWSMeta(meta, SOMEUSER, "foo", Permission.OWNER, false, id, moddate);
		
		try {
			ws.getWorkspaceMetaData(BUSER, wsi);
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
			ws.getWorkspaceMetaData(BUSER, wsi); //will fail if perms are wrong
		}
		
		WorkspaceUser anotheruser = new WorkspaceUser("anotherfnuser");
		meta = ws.createWorkspace(anotheruser, "anotherfnuser:MrT", true, "Ipitythefoolthatdon'teatMrTbreakfastcereal");
		checkWSMeta(meta, anotheruser, "anotherfnuser:MrT", Permission.OWNER, true);
		id = meta.getId();
		moddate = meta.getModDate();
		meta = ws.getWorkspaceMetaData(anotheruser, new WorkspaceIdentifier(id));
		checkWSMeta(meta, anotheruser, "anotherfnuser:MrT", Permission.OWNER, true, id, moddate);
		meta = ws.getWorkspaceMetaData(anotheruser, new WorkspaceIdentifier("anotherfnuser:MrT"));
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
				"A workspace name cannot be null and must have at least one character"));
		userWS.add(new TestRig(crap, "",
				"A workspace name cannot be null and must have at least one character"));
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
					is("User cannot be null or the empty string"));
		}
		try {
			new WorkspaceUser("");
			fail("able to create user with empty string");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("User cannot be null or the empty string"));
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
	public void permissions() throws Exception {
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
			ws.getWorkspaceMetaData(null, wsiNG);
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
		WorkspaceMetaData meta= ws.getWorkspaceMetaData(null, wsiGL);
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
	
	private void checkObjMeta(ObjectMetaData meta, int id, String name, String type,
			int version, WorkspaceUser user, int wsid, String chksum, int size) {
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
	
	@Test
	public void saveObjectsAndGetMeta() throws Exception {
		WorkspaceUser bum = new WorkspaceUser("bum");
		WorkspaceIdentifier read = new WorkspaceIdentifier("saveobjread");
		WorkspaceIdentifier priv = new WorkspaceIdentifier("saveobj");
		ws.createWorkspace(bum, read.getIdentifierString(), true, null);
		int readid = ws.getWorkspaceMetaData(bum, read).getId();
		int privid = ws.getWorkspaceMetaData(bum, read).getId();
		ws.createWorkspace(bum, priv.getIdentifierString(), false, null);
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, String> meta = new HashMap<String, String>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		meta.put("metastuff", "meta");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("meta2", "my hovercraft is full of eels");
		Provenance p = new Provenance("kbasetest2");
		TypeId t = new TypeId(new WorkspaceType("SomeModule", "AType"), 0, 1);
		p.addAction(new Provenance.ProvenanceAction().withServiceName("some service"));
		List<WorkspaceSaveObject> objects = new ArrayList<WorkspaceSaveObject>();
		objects.add(new WorkspaceSaveObject(new WorkspaceObjectID("3"), data, t, meta, p, false));
		objects.add(new WorkspaceSaveObject(new WorkspaceObjectID("3"), data, t, meta2, p, false));
		objects.add(new WorkspaceSaveObject(new WorkspaceObjectID("3-1"), data, t, meta, p, false));
		objects.add(new WorkspaceSaveObject(data, t, meta, p, false));
		objects.add(new WorkspaceSaveObject(data, t, meta, p, false));
		List<ObjectMetaData> objmeta = ws.saveObjects(bum, read, objects);
		String chksum = "36c4f68f2c98971b9736839232eb08f4";
		System.out.println("\n*** test meta 1***");
		System.out.println(objmeta);
		checkObjMeta(objmeta.get(0), 1, "3", t.getTypeString(), 1, bum, readid, chksum, 23);
		checkObjMeta(objmeta.get(1), 1, "3", t.getTypeString(), 2, bum, readid, chksum, 23);
		checkObjMeta(objmeta.get(2), 2, "3-1", t.getTypeString(), 1, bum, readid, chksum, 23);
		checkObjMeta(objmeta.get(3), 3, "3-2", t.getTypeString(), 1, bum, readid, chksum, 23);
		checkObjMeta(objmeta.get(4), 4, "4", t.getTypeString(), 1, bum, readid, chksum, 23);
		
		List<ObjectIdentifier> loi = new ArrayList<ObjectIdentifier>();
		loi.add(new ObjectIdentifier(read, 1));
		loi.add(new ObjectIdentifier(read, 1, 1));
		loi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), "3"));
		loi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), "3", 1));
		loi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), 1));
		loi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), 1, 1));
		loi.add(new ObjectIdentifier(read, "3"));
		loi.add(new ObjectIdentifier(read, "3", 1));
		loi.add(new ObjectIdentifier(read, "3-2"));
		loi.add(new ObjectIdentifier(read, 3));
		loi.add(new ObjectIdentifier(read, "3-2", 1));
		loi.add(new ObjectIdentifier(read, 3, 1));
		List<ObjectUserMetaData> usermeta = ws.getObjectMetaData(bum, loi);
		checkObjMeta(usermeta.get(0), 1, "3", t.getTypeString(), 2, bum, readid, chksum, 23);
		checkObjMeta(usermeta.get(1), 1, "3", t.getTypeString(), 1, bum, readid, chksum, 23);
		checkObjMeta(usermeta.get(2), 1, "3", t.getTypeString(), 2, bum, readid, chksum, 23);
		checkObjMeta(usermeta.get(3), 1, "3", t.getTypeString(), 1, bum, readid, chksum, 23);
		checkObjMeta(usermeta.get(4), 1, "3", t.getTypeString(), 2, bum, readid, chksum, 23);
		checkObjMeta(usermeta.get(5), 1, "3", t.getTypeString(), 1, bum, readid, chksum, 23);
		checkObjMeta(usermeta.get(6), 1, "3", t.getTypeString(), 2, bum, readid, chksum, 23);
		checkObjMeta(usermeta.get(7), 1, "3", t.getTypeString(), 1, bum, readid, chksum, 23);
		checkObjMeta(usermeta.get(8), 3, "3-2", t.getTypeString(), 1, bum, readid, chksum, 23);
		checkObjMeta(usermeta.get(9), 3, "3-2", t.getTypeString(), 1, bum, readid, chksum, 23);
		checkObjMeta(usermeta.get(10), 3, "3-2", t.getTypeString(), 1, bum, readid, chksum, 23);
		checkObjMeta(usermeta.get(11), 3, "3-2", t.getTypeString(), 1, bum, readid, chksum, 23);
		
		
		ws.saveObjects(bum, priv, objects);
		//TODO test meta is correct, test getting meta is correct, run through all possible errors
		//TODO test another user can't read w/o correct privs
		
		objects.clear();
		objects.add(new WorkspaceSaveObject(new WorkspaceObjectID(2), data, t, meta2, p, false));
		objmeta = ws.saveObjects(bum, new WorkspaceIdentifier("saveobjread"), objects);
		System.out.println("\n*** test meta 2***");
		System.out.println(objmeta);
		checkObjMeta(objmeta.get(0), 2, "3-1", t.getTypeString(), 2, bum, readid, chksum, 23);
		//TODO test meta is correct, test getting meta is correct, run through all possible errors
	}
}
