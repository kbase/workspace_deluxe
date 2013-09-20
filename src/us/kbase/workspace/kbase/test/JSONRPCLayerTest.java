package us.kbase.workspace.kbase.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.JsonClientException;
import us.kbase.ServerException;
import us.kbase.Tuple10;
import us.kbase.Tuple6;
import us.kbase.Tuple9;
import us.kbase.UObject;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.test.WorkspaceTestCommon;

/*
 * These tests are specifically for testing the JSON-RPC communications between
 * the client, up to the invocation of the {@link us.kbase.workspace.workspaces.Workspaces}
 * methods. As such they do not test the full functionality of the Workspaces methods;
 * {@link us.kbase.workspace.workspaces.test.TestWorkspaces} handles that. This means
 * that only one backend (the simplest gridFS backend) is tested here, while TestWorkspaces
 * tests all backends and {@link us.kbase.workspace.database.Database} implementations.
 */
public class JSONRPCLayerTest {
	
	private static WorkspaceServer SERVER = null;
	private static WorkspaceClient CLIENT1 = null;
	private static String USERNOEMAIL = null;
	private static String USER1 = null;
	private static WorkspaceClient CLIENT2 = null;
	private static String USER2 = null;
	private static WorkspaceClient CLIENT_NO_AUTH = null;
	
	private static SimpleDateFormat DATE_FORMAT =
			new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	
	private static class ServerThread extends Thread {
		
		public void run() {
			try {
				SERVER.startupServer();
			} catch (Exception e) {
				System.err.println("Can't start server:");
				e.printStackTrace();
			}
		}
	}
	
	//TODO saveObject, getObject and getObjectMeta tests
	
	//http://quirkygba.blogspot.com/2009/11/setting-environment-variables-in-java.html
	@SuppressWarnings("unchecked")
	public static Map<String, String> getenv() throws NoSuchFieldException,
			SecurityException, IllegalArgumentException, IllegalAccessException {
		Map<String, String> unmodifiable = System.getenv();
		Class<?> cu = unmodifiable.getClass();
		Field m = cu.getDeclaredField("m");
		m.setAccessible(true);
		return (Map<String, String>) m.get(unmodifiable);
	}
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		//TODO catch exceptions and print nice errors - next deploy
		USER1 = System.getProperty("test.user1");
		USER2 = System.getProperty("test.user2");
		USERNOEMAIL = System.getProperty("test.user.noemail");
		String p1 = System.getProperty("test.pwd1");
		String p2 = System.getProperty("test.pwd2");
		WorkspaceTestCommon.destroyAndSetupDB(1, "gridFS", null);
		
		//write the server config file:
		File iniFile = File.createTempFile("test", ".cfg", new File("./"));
		iniFile.deleteOnExit();
		System.out.println("Created temporary config file: " + iniFile.getAbsolutePath());
		Ini ini = new Ini();
		Section ws = ini.add("Workspace");
		ws.add("mongodb-host", WorkspaceTestCommon.getHost());
		ws.add("mongodb-database", WorkspaceTestCommon.getDB1());
		ws.add("mongodb-user", WorkspaceTestCommon.getMongoUser());
		ws.add("mongodb-pwd", WorkspaceTestCommon.getMongoPwd());
		ws.add("backend-secret", "");
		ini.store(iniFile);
		
		//set up env
		Map<String, String> env = getenv();
		env.put("KB_DEPLOYMENT_CONFIG", iniFile.getAbsolutePath());
		env.put("KB_SERVICE_NAME", "Workspace");

		SERVER = new WorkspaceServer();
		new ServerThread().start();
		System.out.println("Main thread waiting for server to start up");
		while(SERVER.getServerPort() == null) {
			Thread.sleep(1000);
		}
		int port = SERVER.getServerPort();
		System.out.println("Started test server on port " + port);
		System.out.println("Starting tests");
		CLIENT1 = new WorkspaceClient(new URL("http://localhost:" + port), USER1, p1);
		CLIENT2 = new WorkspaceClient(new URL("http://localhost:" + port), USER2, p2);
		CLIENT_NO_AUTH = new WorkspaceClient(new URL("http://localhost:" + port));
		CLIENT1.setAuthAllowedForHttp(true);
		CLIENT2.setAuthAllowedForHttp(true);
		CLIENT_NO_AUTH.setAuthAllowedForHttp(true);
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (SERVER != null) {
			System.out.print("Killing server... ");
			SERVER.stopServer();
			System.out.println("Done");
		}
	}
	
	@Test
	public void createWSandCheck() throws Exception {
		Tuple6<Integer, String, String, String, String, String> meta =
				CLIENT1.createWorkspace(new CreateWorkspaceParams()
					.withWorkspace("foo")
					.withGlobalread("r")
					.withDescription("boogabooga"));
		Tuple6<Integer, String, String, String, String, String> metaget =
				CLIENT1.getWorkspaceMetadata(new WorkspaceIdentity()
						.withWorkspace("foo"));
		assertThat("ids are equal", meta.getE1(), is(metaget.getE1()));
		assertThat("moddates equal", meta.getE4(), is(metaget.getE4()));
		for (Tuple6<Integer, String, String, String, String, String> m:
				Arrays.asList(meta, metaget)) {
			assertThat("ws name correct", m.getE2(), is("foo"));
			assertThat("user name correct", m.getE3(), is(USER1));
			assertThat("permission correct", m.getE5(), is("a"));
			assertThat("global read correct", m.getE6(), is("r"));
		}
		assertThat("description correct", CLIENT1.getWorkspaceDescription(
				new WorkspaceIdentity().withWorkspace("foo")), is("boogabooga"));
	}
	
	@Test
	public void createWSBadGlobal() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("gl1")); //should work fine w/o globalread
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
		.withWorkspace("gl2").withGlobalread("n")); //should work fine w/o globalread
		assertThat("globalread correct", CLIENT1.getWorkspaceMetadata(
				new WorkspaceIdentity().withWorkspace("gl1")).getE6(), is("n"));
		assertThat("globalread correct", CLIENT1.getWorkspaceMetadata(
				new WorkspaceIdentity().withWorkspace("gl2")).getE6(), is("n"));
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("gl_fail").withGlobalread("w"));
			fail("call succeeded w/ illegal global read param");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("globalread must be n or r"));
		}
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("gl_fail").withGlobalread("a"));
			fail("call succeeded w/ illegal global read param");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("globalread must be n or r"));
		}
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("gl_fail").withGlobalread("b"));
			fail("call succeeded w/ illegal global read param");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("globalread must be n or r"));
		}
	}
	
	@Test
	public void createWSNoAuth() throws Exception {
		try {
			CLIENT_NO_AUTH.createWorkspace(new CreateWorkspaceParams().withWorkspace("noauth"));
			fail("created workspace without auth");
		} catch (IllegalStateException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("RPC method requires authentication but neither user nor token was set"));
		}
	}

	@Test
	public void setBadPermissions() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("badperms"));
		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("badperms")
					.withUsers(Arrays.asList(USER2)));
			fail("able to set null permission");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("No such permission: null"));
		
		}
		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("badperms")
					.withNewPermission("f").withUsers(Arrays.asList(USER2)));
			fail("able to set illegal permission");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("No such permission: f"));
		
		}
		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("badperms")
					.withNewPermission("r").withUsers(new ArrayList<String>()));
			fail("able to set permission with no users");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Must provide at least one user"));
		}
		
		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("badperms")
					.withNewPermission("r").withUsers(Arrays.asList(USER2,
					"thisisnotarealuserihopeotherwisethistestwillfailandthatdbeabadthing")));
			fail("able to set  permission with bad user");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("User thisisnotarealuserihopeotherwisethistestwillfailandthatdbeabadthing is not a valid user"));
		}
		Map<String, String> expected = new HashMap<String, String>();
		expected.put(USER1, "a");
		Map<String, String> perms = CLIENT1.getPermissions(new WorkspaceIdentity().withWorkspace("badperms"));
		assertThat("Bad permissions were added to a workspace", perms, is(expected));
	}
	
	@Test
	public void permissions() throws IOException, JsonClientException {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("permspriv")
				.withDescription("foo"));
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("permsglob")
				.withGlobalread("r").withDescription("bar"));
		//should work, global read
		CLIENT2.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permsglob"));
		CLIENT_NO_AUTH.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permsglob"));
		CLIENT_NO_AUTH.getWorkspaceMetadata(new WorkspaceIdentity().withWorkspace("permsglob"));
		
		try {
			CLIENT_NO_AUTH.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permspriv"));
			fail("able to read workspace desc with no auth");
		} catch (ServerException e) {
			assertThat("exception message corrent", e.getLocalizedMessage(),
					is("Anonymous users may not read workspace permspriv"));
		}
		
		try {
			CLIENT_NO_AUTH.getWorkspaceMetadata(new WorkspaceIdentity().withWorkspace("permspriv"));
			fail("able to read workspace desc with no auth");
		} catch (ServerException e) {
			assertThat("exception message corrent", e.getLocalizedMessage(),
					is("Anonymous users may not read workspace permspriv"));
		}
		
		try {
			CLIENT2.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permspriv"));
			fail("Able to get ws desc without read perms");
		} catch (ServerException e) {
			assertThat("Correct excp message", e.getLocalizedMessage(),
					is("User kbasetest2 may not read workspace permspriv"));
		}
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("r").withUsers(Arrays.asList(USER2)));
		CLIENT2.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permspriv")); //should work, now readable
		
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		objects.add(new ObjectSaveData().withData(new UObject("some crap"))
				.withType("SomeRandom.Type"));
		try {
			CLIENT2.saveObjects(new SaveObjectsParams()
				.withWorkspace("permspriv").withObjects(objects));
		} catch (ServerException e) {
			assertThat("correcet exception", e.getLocalizedMessage(),
					is("User kbasetest2 may not write to workspace permspriv"));
		}
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		CLIENT2.saveObjects(new SaveObjectsParams()
			.withWorkspace("permspriv").withObjects(objects)); //should work
		Map<String, String> expected = new HashMap<String, String>();
		expected.put(USER1, "a");
		expected.put(USER2, "w");
		Map<String, String> perms = CLIENT1.getPermissions(new WorkspaceIdentity()
			.withWorkspace("permspriv"));
		assertThat("Permissions set correctly", perms, is(expected));
		
		try {
			CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
					.withNewPermission("a").withUsers(Arrays.asList(USER1)));
		} catch (ServerException e) {
			assertThat("Correct excp message", e.getLocalizedMessage(),
					is("User kbasetest2 may not set permissions on workspace permspriv"));
		}
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("a").withUsers(Arrays.asList(USER2)));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("w").withUsers(Arrays.asList(USERNOEMAIL))); //should work
		expected.put(USER1, "a");
		expected.put(USER2, "a");
		expected.put(USERNOEMAIL, "w");
		perms = CLIENT2.getPermissions(new WorkspaceIdentity()
			.withWorkspace("permspriv"));
		assertThat("Permissions set correctly", perms, is(expected));
	}
	
	@Test
	public void badIdent() throws Exception {
		try {
			CLIENT1.getPermissions(new WorkspaceIdentity());
			fail("got non-existant workspace");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Must provide one and only one of workspace name (was: null) or id (was: null)"));
		}
	}
	
	@Test
	public void workspaceIDprocessing() throws Exception {
		String ws = "idproc";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws)
				.withDescription("foo"));
		Tuple6<Integer, String, String, String, String, String> meta =
				CLIENT1.getWorkspaceMetadata(new WorkspaceIdentity().withWorkspace(ws));
		//these should work
		CLIENT1.setPermissions(new SetPermissionsParams().withId(meta.getE1())
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace(meta.getE2())
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		
		try {
			CLIENT1.setPermissions(new SetPermissionsParams()
					.withUsers(Arrays.asList(USER2)).withNewPermission("w"));
			fail("able set perms without providing ws id or name");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Must provide one and only one of workspace name (was: null) or id (was: null)"));
		}
		
		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withId(meta.getE1())
					.withNewPermission("w").withUsers(Arrays.asList(USER2))
					.withWorkspace(meta.getE2()));
			fail("able to specify workspace by id and name");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is(String.format(
					"Must provide one and only one of workspace name (was: idproc) or id (was: %s)",
					meta.getE1())));
		}
		
		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withId(-1)
					.withNewPermission("w").withUsers(Arrays.asList(USER2)));
			fail("able to specify workspace by id and name");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Workspace id must be > 0"));
		}
		
		//should work 
		CLIENT1.setPermissions(new SetPermissionsParams()
				.withWorkspace("kb|ws." + meta.getE1())
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
					.withWorkspace("kb|ws." + meta.getE1()));
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Illegal character in workspace name kb|ws." 
					+ meta.getE1() + ": |"));
		}
		
	
	}
	
	private void saveBadObject(List<ObjectSaveData> objects, String exception) 
			throws Exception {
		try {
			CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("savebadpkg")
					.withObjects(objects));
			fail("saved invalid data package");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is(exception));
		}
	}
	
	@Test
	public void saveBadPackages() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("savebadpkg")
				.withDescription("foo"));
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		objects.add(new ObjectSaveData().withData(new UObject("some crap"))
				.withType("SomeRandom.Type"));
		SaveObjectsParams sop = new SaveObjectsParams()
			.withWorkspace("permspriv").withObjects(objects);
		sop.setAdditionalProperties("foo", "bar");
		sop.setAdditionalProperties("baz", "faz");
		try {
			CLIENT1.saveObjects(sop);
			fail("allowed unexpected args");
		} catch (ServerException e) {
			String[] exp = e.getLocalizedMessage().split(":");
			String[] args = exp[1].trim().split("\\s");
			HashSet<String> argset = new HashSet<String>(Arrays.asList(args));
			assertThat("correct exception message", exp[0],
					is("Unexpected arguments in SaveObjectsParams"));
			assertThat("correct args list", argset,
					is(new HashSet<String>(Arrays.asList("foo", "baz"))));
		}
		
		objects.get(0).setAdditionalProperties("wugga", "boo");
		saveBadObject(objects, "Unexpected arguments in ObjectSaveData: wugga");
		
		objects.set(0, new ObjectSaveData().withName("myname").withObjid(1));
		saveBadObject(objects, "Must provide one and only one of object name (was: myname) or id (was: 1)");
		
		objects.set(0, new ObjectSaveData().withName("myname+"));
		saveBadObject(objects, "Illegal character in object name myname+: +");
		
		objects.set(0, new ObjectSaveData().withObjid(0));
		saveBadObject(objects, "Object id must be > 0");
		
		objects.set(0, new ObjectSaveData());
		saveBadObject(objects, "Object 1 has no data");
		
		objects.add(0, new ObjectSaveData().withData(new UObject("foo")).withType("Foo.Bar"));
		saveBadObject(objects, "Object 2 has no data");
		
		objects.clear();
		objects.add(new ObjectSaveData().withData(new UObject("foo")));
		saveBadObject(objects, "Object 1 type error: Moduletype cannot be null or the empty string");
		
		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withType(""));
		saveBadObject(objects, "Object 1 type error: Moduletype cannot be null or the empty string");
		
		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withType("F.B").withTver(""));
		saveBadObject(objects, "Object 1 type error: Typeversion cannot be an empty string");
		
		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withType("foo"));
		saveBadObject(objects, "Object 1 type error: Type foo could not be split into a module and name");
		
		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withType("foo.bar").withTver("1.2.3"));
		saveBadObject(objects, "Object 1 type error: Type version string 1.2.3 could not be parsed to a version");
		
		//TODO provenance testing
	}
	
	@Test
	public void saveAndGetObjects() throws Exception {
		
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("saveget"));
		int wsid = CLIENT1.getWorkspaceMetadata(
				new WorkspaceIdentity().withWorkspace("saveget")).getE1();
		
		//save some objects to get
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> data2 = new HashMap<String, Object>();
		Map<String, String> meta = new HashMap<String, String>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		data2.put("fubar2", moredata);
		meta.put("metastuff", "meta");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("meta2", "my hovercraft is full of eels");
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("saveget")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withMetadata(meta).withType("Foo.Bar")); // will be "1"
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withMetadata(meta).withType("Genome.Wugga").withTver("2")); // will be "2"
		objects.add(new ObjectSaveData().withData(new UObject(data2))
				.withMetadata(meta2).withType("Wiggle.Wugga").withTver("2.1")
				.withName("foo")); 
		
		List<Tuple9<Integer, String, String, String, Integer, String, Integer, String, Integer>> retmet =
				CLIENT1.saveObjects(soc);
		
		assertThat("num metas correct", retmet.size(), is(3));
		checkMeta(retmet.get(0), 1, "1", "Foo.Bar-0.0", 1, USER1, wsid, "36c4f68f2c98971b9736839232eb08f4", 23);
		checkMeta(retmet.get(1), 2, "2", "Genome.Wugga-2.0", 1, USER1, wsid, "36c4f68f2c98971b9736839232eb08f4", 23);
		checkMeta(retmet.get(2), 3, "foo", "Wiggle.Wugga-2.1", 1, USER1, wsid, "3c59f762140806c36ab48a152f28e840", 24);
		
		
		objects.clear();
		objects.add(new ObjectSaveData().withData(new UObject(data2))
				.withMetadata(meta2).withType("Wiggle.Wugga").withTver("2.1")
				.withObjid(2));
		
		retmet = CLIENT1.saveObjects(soc);
		
		assertThat("num metas correct", retmet.size(), is(1));
		checkMeta(retmet.get(0), 2, "2", "Wiggle.Wugga-2.1", 2, USER1, wsid, "3c59f762140806c36ab48a152f28e840", 24);
		
		List<ObjectIdentity> loi = new ArrayList<ObjectIdentity>();
		loi.add(new ObjectIdentity().withRef("saveget/2/1"));
		loi.add(new ObjectIdentity().withRef("kb|ws." + wsid + ".obj.2.ver.1"));
		loi.add(new ObjectIdentity().withRef(wsid + ".2.1"));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withName("2").withVer(1));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withObjid(2).withVer(1));
		loi.add(new ObjectIdentity().withWsid(wsid).withName("2").withVer(1));
		loi.add(new ObjectIdentity().withWsid(wsid).withObjid(2).withVer(1));
		checkSavedObjects(loi, 2, "2", "Genome.Wugga-2.0", 1, USER1,
				wsid, "36c4f68f2c98971b9736839232eb08f4", 23, meta, data);
		
		loi.clear();
		loi.add(new ObjectIdentity().withRef("saveget/2"));
		loi.add(new ObjectIdentity().withRef("kb|ws." + wsid + ".obj.2"));
		loi.add(new ObjectIdentity().withRef(wsid + ".2"));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withName("2"));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withObjid(2));
		loi.add(new ObjectIdentity().withWsid(wsid).withName("2"));
		loi.add(new ObjectIdentity().withWsid(wsid).withObjid(2));
		checkSavedObjects(loi, 2, "2", "Wiggle.Wugga-2.1", 2, USER1,
				wsid, "3c59f762140806c36ab48a152f28e840", 24, meta2, data2);
		//TODO lots more tests here
		//TODO try some bad refs and id/name combos
	}
	
	private void checkSavedObjects(List<ObjectIdentity> loi, int id, String name,
			String type, int ver, String user, int wsid, String chksum, int size,
			Map<String, String> meta, Map<String, Object> data) throws Exception {
		List<ObjectData> retdata = CLIENT1.getObjects(loi);
		assertThat("num data correct", retdata.size(), is(loi.size()));
		for (ObjectData o: retdata) {
			checkData(o, id, name, type, ver, user, wsid,
					chksum, size, meta, data);
		}
		
		List<Tuple10<Integer, String, String, String, Integer, String, Integer,
				String, Integer, Map<String, String>>> retusermeta =
				CLIENT1.getObjectMetadata(loi);
		
		assertThat("num usermeta correct", retusermeta.size(), is(loi.size()));
		for (Tuple10<Integer, String, String, String, Integer, String, Integer,
				String, Integer, Map<String, String>> o: retusermeta) {
			checkUserMeta(o, id, name, type, ver, user, wsid,
					chksum, size, meta);
		}
	}

	private void checkData(ObjectData retdata, int id, String name,
			String typeString, int ver, String user, int wsid, String chksum,
			int size, Map<String, String> meta, Map<String, Object> data) 
			throws Exception {
		
		assertThat("object data is correct", retdata.getData().asClassInstance(Object.class),
				is((Object) data));
		
		checkUserMeta(retdata.getMeta(), id, name, typeString, ver, user, wsid, chksum, size, meta);
	}

	private void checkUserMeta(
			Tuple10<Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String, String>> usermeta,
			int id, String name, String typeString, int ver, String user,
			int wsid, String chksum, int size, Map<String, String> meta)
			throws Exception {
		
		assertThat("id is correct", usermeta.getE1(), is(id));
		assertThat("name is correct", usermeta.getE2(), is(name));
		assertThat("type is correct", usermeta.getE3(), is(typeString));
		DATE_FORMAT.parse(usermeta.getE4()); //should throw error if bad format
		assertThat("version is correct", usermeta.getE5(), is(ver));
		assertThat("user is correct", usermeta.getE6(), is(user));
		assertThat("wsid is correct", usermeta.getE7(), is(wsid));
		assertThat("chksum is correct", usermeta.getE8(), is(chksum));
		assertThat("size is correct", usermeta.getE9(), is(size));
		assertThat("meta is correct", usermeta.getE10(), is(meta));
	}
	private void checkMeta(
			Tuple9<Integer, String, String, String, Integer, String, Integer, String, Integer> usermeta,
			int id, String name, String typeString, int ver, String user,
			int wsid, String chksum, int size) throws Exception {
		
		assertThat("id is correct", usermeta.getE1(), is(id));
		assertThat("name is correct", usermeta.getE2(), is(name));
		assertThat("type is correct", usermeta.getE3(), is(typeString));
		DATE_FORMAT.parse(usermeta.getE4()); //should throw error if bad format
		assertThat("version is correct", usermeta.getE5(), is(ver));
		assertThat("user is correct", usermeta.getE6(), is(user));
		assertThat("wsid is correct", usermeta.getE7(), is(wsid));
		assertThat("chksum is correct", usermeta.getE8(), is(chksum));
		assertThat("size is correct", usermeta.getE9(), is(size));
	}
	
}
