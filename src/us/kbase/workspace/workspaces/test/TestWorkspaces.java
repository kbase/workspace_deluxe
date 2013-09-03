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

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.mongo.MongoDatabase;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;
import us.kbase.workspace.workspaces.Permission;
import us.kbase.workspace.workspaces.WorkspaceIdentifier;
import us.kbase.workspace.workspaces.WorkspaceMetaData;
import us.kbase.workspace.workspaces.Workspaces;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

//TODO test vs. auth'd mongo
@RunWith(Parameterized.class)
public class TestWorkspaces {

	public static final String M_USER = "test.mongo.user";
	public static final String M_PWD = "test.mongo.pwd";
	public static Workspaces[] TEST_WORKSPACES = new Workspaces[2];
	public static final String LONG_TEXT_PART = "Passersby were amazed by the unusually large amounts of blood. ";
	public static String LONG_TEXT = "";

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
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
		String host = System.getProperty("test.mongo.host");
		String db1 = System.getProperty("test.mongo.db1");
		String db2 = System.getProperty("test.mongo.db2");
		String mUser = System.getProperty(M_USER);
		String mPwd = System.getProperty(M_PWD);
		String shockurl = System.getProperty("test.shock.url");
		String shockuser = System.getProperty("test.user.noemail");
		String shockpwd = System.getProperty("test.pwd.noemail");
		
		if (mUser.equals("")) {
			mUser = null;
		}
		if (mPwd.equals("")) {
			mPwd = null;
		}
		if (mUser == null ^ mPwd == null) {
			System.err.println(String.format("Must provide both %s and %s ",
					M_USER, M_PWD) + "params for testing if authentication " + 
					"is to be used");
			System.exit(1);
		}
		System.out.print("Mongo auth params are user: " + mUser + " pwd: ");
		if (mPwd != null && mPwd.length() > 0) {
			System.out.println("[redacted]");
		} else {
			System.out.println(mPwd);
		}
		//Set up mongo backend database
		DB mdb = new MongoClient(host).getDB(db1);
		if (mUser != null) {
			mdb.authenticate(mUser, mPwd.toCharArray());
		}
		DBObject dbo = new BasicDBObject();
		mdb.getCollection("settings").remove(dbo);
		mdb.getCollection("workspaces").remove(dbo);
		mdb.getCollection("workspaceACLs").remove(dbo);
		mdb.getCollection("workspaceCounter").remove(dbo);
		dbo.put("backend", "gridFS");
		mdb.getCollection("settings").insert(dbo);
		Database db = null;
		if (mUser != null) {
			db = new MongoDatabase(host, db1, shockpwd, mUser, mPwd);
		} else {
			db = new MongoDatabase(host, db1, shockpwd);
		}
		TEST_WORKSPACES[0] = new Workspaces(db);
		assertTrue("GridFS backend setup failed", TEST_WORKSPACES[0].getBackendType().equals("GridFS"));
		
		//Set up shock backend database
		mdb = new MongoClient(host).getDB(db2);
		if (mUser != null) {
			mdb.authenticate(mUser, mPwd.toCharArray());
		}
		dbo = new BasicDBObject();
		mdb.getCollection("settings").remove(dbo);
		mdb.getCollection("workspaces").remove(dbo);
		mdb.getCollection("workspaceACLs").remove(dbo);
		mdb.getCollection("workspaceCounter").remove(dbo);
		dbo.put("backend", "shock");
		dbo.put("shock_user", shockuser);
		dbo.put("shock_location", shockurl);
		mdb.getCollection("settings").insert(dbo);
		if (mUser != null) {
			db = new MongoDatabase(host, db2, shockpwd, mUser, mPwd);
		} else {
			db = new MongoDatabase(host, db2, shockpwd);
		}
		TEST_WORKSPACES[1] = new Workspaces(db);
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
		ws.createWorkspace("auser", "lt", false, LONG_TEXT);
		ws.createWorkspace("auser", "ltp", false, LONG_TEXT_PART);
		ws.createWorkspace("auser", "ltn", false, null);
		String desc = ws.getWorkspaceDescription("auser", new WorkspaceIdentifier("lt"));
		assertThat("Workspace description incorrect", desc, is(LONG_TEXT.substring(0, 1000)));
		desc = ws.getWorkspaceDescription("auser", new WorkspaceIdentifier("ltp"));
		assertThat("Workspace description incorrect", desc, is(LONG_TEXT_PART));
		desc = ws.getWorkspaceDescription("auser", new WorkspaceIdentifier("ltn"));
		assertNull("Workspace description incorrect", desc);
		
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("lt");
		try {
			ws.getWorkspaceDescription("b", wsi);
			fail("Got ws desc w/o read perms");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("exception message ok", e.getLocalizedMessage(),
					is("User b does not have permission to read workspace lt"));
		}
		for (Permission p: Permission.values()) {
			if (p.compareTo(Permission.NONE) <= 0 || p.compareTo(Permission.OWNER) >= 0) {
				continue;
			}
			ws.setPermissions("auser", wsi, Arrays.asList("b"), p);
			ws.getWorkspaceDescription("b", wsi); //will fail if perms are wrong
		}
	}
	
	private void checkMeta(WorkspaceMetaData meta, String owner, String name,
			Permission perm, boolean globalread, int id, Date moddate) {
		checkMeta(meta, owner, name, perm, globalread);
		assertThat("ws id correct", meta.getId(), is(id));
		assertThat("ws mod date correct", meta.getModDate(), is(moddate));
	}
	
	private void checkMeta(WorkspaceMetaData meta, String owner, String name,
			Permission perm, boolean globalread) {
		assertThat("ws owner correct", meta.getOwner(), is(owner));
		assertThat("ws name correct", meta.getName(), is(name));
		assertThat("ws permissions correct", meta.getUserPermission(), is(perm));
		assertThat("ws global read correct", meta.isGloballyReadable(), is(globalread));
	}
	
	@Test
	public void testCreateWorkspaceAndGetMeta() throws Exception {
		WorkspaceMetaData meta = ws.createWorkspace("auser", "foo", false, "eeswaffertheen");
		checkMeta(meta, "auser", "foo", Permission.OWNER, false);
		int id = meta.getId();
		WorkspaceIdentifier wsi = new WorkspaceIdentifier(id);
		Date moddate = meta.getModDate();
		meta = ws.getWorkspaceMetaData("auser", new WorkspaceIdentifier(id));
		checkMeta(meta, "auser", "foo", Permission.OWNER, false, id, moddate);
		meta = ws.getWorkspaceMetaData("auser", new WorkspaceIdentifier("foo"));
		checkMeta(meta, "auser", "foo", Permission.OWNER, false, id, moddate);
		
		try {
			ws.getWorkspaceMetaData("b", wsi);
			fail("Got metadata w/o read perms");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("exception message ok", e.getLocalizedMessage(),
					is("User b does not have permission to read workspace " + id));
		}
		for (Permission p: Permission.values()) {
			if (p.compareTo(Permission.NONE) <= 0 || p.compareTo(Permission.OWNER) >= 0) {
				continue;
			}
			ws.setPermissions("auser", wsi, Arrays.asList("b"), p);
			ws.getWorkspaceMetaData("b", wsi); //will fail if perms are wrong
		}
		
		
		meta = ws.createWorkspace("anotherfnuser", "anotherfnuser:MrT", true, "Ipitythefoolthatdon'teatMrTbreakfastcereal");
		checkMeta(meta, "anotherfnuser", "anotherfnuser:MrT", Permission.OWNER, true);
		id = meta.getId();
		moddate = meta.getModDate();
		meta = ws.getWorkspaceMetaData("anotherfnuser", new WorkspaceIdentifier(id));
		checkMeta(meta, "anotherfnuser", "anotherfnuser:MrT", Permission.OWNER, true, id, moddate);
		meta = ws.getWorkspaceMetaData("anotherfnuser", new WorkspaceIdentifier("anotherfnuser:MrT"));
		checkMeta(meta, "anotherfnuser", "anotherfnuser:MrT", Permission.OWNER, true, id, moddate);
	}
	
	@Test
	public void testCreateWorkspaceAndWorkspaceIdentifierWithBadInput()
			throws Exception {
		List<List<String>> userWS = new ArrayList<List<String>>();
		//test a few funny chars in the ws name
		userWS.add(Arrays.asList("afaeaafe", "afe_aff*afea",
				"Illegal character in workspace name afe_aff*afea: *"));
		userWS.add(Arrays.asList("afaeaafe", "afe_aff-afea",
				"Illegal character in workspace name afe_aff-afea: -"));
		userWS.add(Arrays.asList("afaeaafe", "afeaff/af*ea",
				"Illegal character in workspace name afeaff/af*ea: /"));
		userWS.add(Arrays.asList("afaeaafe", "af?eaff*afea",
				"Illegal character in workspace name af?eaff*afea: ?"));
		//check missing ws name
		userWS.add(Arrays.asList("afaeaafe", null,
				"name cannot be null and must have at least one character"));
		userWS.add(Arrays.asList("afaeaafe", "",
				"name cannot be null and must have at least one character"));
		//check missing user and/or workspace name in compound name
		userWS.add(Arrays.asList("afaeaafe", ":",
				"Workspace name missing from :"));
		userWS.add(Arrays.asList("afaeaafe", "foo:",
				"Workspace name missing from foo:"));
		userWS.add(Arrays.asList("afaeaafe", ":foo",
				"User name missing from :foo"));
		//check multiple delims
		userWS.add(Arrays.asList("afaeaafe", "foo:a:foo",
				"Workspace name foo:a:foo may only contain one : delimiter"));
		userWS.add(Arrays.asList("afaeaafe", "foo::foo",
				"Workspace name foo::foo may only contain one : delimiter"));
		
		for (List<String> testdata: userWS) {
			String wksps = testdata.get(1);
			try {
				new WorkspaceIdentifier(wksps);
				fail(String.format("able to create workspace identifier with illegal input ws %s",
						wksps));
			} catch (IllegalArgumentException e) {
				assertThat("incorrect exception message", e.getLocalizedMessage(),
						is(testdata.get(2)));
			}
		}
		
		//check missing user name
		userWS.add(Arrays.asList(null, "foo",
				"user cannot be null and must have at least one character"));
		userWS.add(Arrays.asList("", "foo",
				"user cannot be null and must have at least one character"));
		//user must match prefix
		userWS.add(Arrays.asList("auser", "notauser:foo", 
				"Workspace name notauser:foo must only contain the user name auser prior to the : delimiter"));
		
		for (List<String> testdata: userWS) {
			String user = testdata.get(0);
			String wksps = testdata.get(1);
			try {
				ws.createWorkspace(user, wksps, false, "iswaffertheen");
				fail(String.format("able to create workspace with illegal input user: %s ws %s",
						user, wksps));
			} catch (IllegalArgumentException e) {
				assertThat("incorrect exception message", e.getLocalizedMessage(),
						is(testdata.get(2)));
			}
			try {
				new WorkspaceIdentifier(wksps, user);
				fail(String.format("able to create workspace identifier with illegal input user: %s ws %s",
						user, wksps));
			} catch (IllegalArgumentException e) {
				assertThat("incorrect exception message", e.getLocalizedMessage(),
						is(testdata.get(2)));
			}
		}
	}
	
	@Test
	public void preExistingWorkspace() throws Exception {
		ws.createWorkspace("a", "preexist", false, null);
		try {
			ws.createWorkspace("b", "preexist", false, null);
			fail("able to create same workspace twice");
		} catch (PreExistingWorkspaceException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Workspace preexist already exists"));
		}
	}
	
	@Test
	public void createWorkspaceWithIllegalUser() throws Exception {
		try {
			ws.createWorkspace("*", "foo", false, null);
			fail("able to create workspace with illegal character in username");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Illegal user name: *"));
		}
	}
	
	@Test
	public void permissions() throws Exception {
		//setup
		WorkspaceIdentifier wsiNG = new WorkspaceIdentifier("perms_noglobal");
		ws.createWorkspace("a", "perms_noglobal", false, null);
		WorkspaceIdentifier wsiGL = new WorkspaceIdentifier("perms_global");
		ws.createWorkspace("a", "perms_global", true, null);
		Map<String, Permission> expect = new HashMap<String, Permission>();
		//try some illegal ops
		try {
			ws.setPermissions("a", wsiNG, Arrays.asList("a", "b", "c", "*"), Permission.READ);
			fail("was able to set permissions with illegal username");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Illegal user name: *"));
		}
		try {
			ws.setPermissions("a", wsiNG, Arrays.asList("a", "b", "c"), Permission.OWNER);
			fail("was able to set owner permissions");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Cannot set owner permission"));
		}
		try {
			ws.setPermissions("b", wsiNG, Arrays.asList("a", "b", "c"), Permission.READ);
			fail("was able to set permissions with unauth'd username");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("User b does not have permission to set permissions on workspace perms_noglobal"));
		}
		//check basic permissions for new private and public workspaces
		expect.put("a", Permission.OWNER);
		assertThat("ws has correct perms for owner", ws.getPermissions("a", wsiNG), is(expect));
		expect.put("*", Permission.READ);
		assertThat("ws has correct perms for owner", ws.getPermissions("a", wsiGL), is(expect));
		expect.clear();
		expect.put("b", Permission.NONE);
		assertThat("ws has correct perms for random user", ws.getPermissions("b", wsiNG), is(expect));
		expect.put("*", Permission.READ);
		assertThat("ws has correct perms for random user", ws.getPermissions("b", wsiGL), is(expect));
		//test read permissions
		ws.setPermissions("a", wsiNG, Arrays.asList("a", "b", "c"), Permission.READ);
		expect.clear();
		expect.put("a", Permission.OWNER);
		expect.put("b", Permission.READ);
		expect.put("c", Permission.READ);
		assertThat("ws doesn't replace owner perms", ws.getPermissions("a", wsiNG), is(expect));
		expect.clear();
		expect.put("b", Permission.READ);
		assertThat("no permission leakage", ws.getPermissions("b", wsiNG), is(expect));
		try {
			ws.setPermissions("b", wsiNG, Arrays.asList("a", "b", "c"), Permission.READ);
			fail("was able to set permissions with unauth'd username");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("User b does not have permission to set permissions on workspace perms_noglobal"));
		}
		//test write permissions
		ws.setPermissions("a", wsiNG, Arrays.asList("b"), Permission.WRITE);
		expect.put("a", Permission.OWNER);
		expect.put("b", Permission.WRITE);
		expect.put("c", Permission.READ);
		assertThat("ws doesn't replace owner perms", ws.getPermissions("a", wsiNG), is(expect));
		expect.clear();
		expect.put("b", Permission.WRITE);
		assertThat("no permission leakage", ws.getPermissions("b", wsiNG), is(expect));
		try {
			ws.setPermissions("b", wsiNG, Arrays.asList("a", "b", "c"), Permission.READ);
			fail("was able to set permissions with unauth'd username");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("User b does not have permission to set permissions on workspace perms_noglobal"));
		}
		//test admin permissions
		ws.setPermissions("a", wsiNG, Arrays.asList("b"), Permission.ADMIN);
		expect.put("a", Permission.OWNER);
		expect.put("b", Permission.ADMIN);
		expect.put("c", Permission.READ);
		assertThat("ws doesn't replace owner perms", ws.getPermissions("a", wsiNG), is(expect));
		assertThat("admin can see all perms", ws.getPermissions("b", wsiNG), is(expect));
		ws.setPermissions("b", wsiNG, Arrays.asList("a", "c"), Permission.WRITE);
		expect.put("c", Permission.WRITE);
		assertThat("ws doesn't replace owner perms", ws.getPermissions("a", wsiNG), is(expect));
		assertThat("admin can correctly set perms", ws.getPermissions("b", wsiNG), is(expect));
		//test remove permissions
		ws.setPermissions("b", wsiNG, Arrays.asList("a", "c"), Permission.NONE);
		expect.remove("c");
		assertThat("ws doesn't replace owner perms", ws.getPermissions("a", wsiNG), is(expect));
		assertThat("admin can't overwrite owner perms", ws.getPermissions("b", wsiNG), is(expect));
		
		
	}
}
