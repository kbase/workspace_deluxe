package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.Assert;

import org.apache.commons.lang3.StringUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.Tuple10;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple7;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.test.TestException;
import us.kbase.workspace.CompileTypespecParams;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GetModuleInfoParams;
import us.kbase.workspace.ListModuleVersionsParams;
import us.kbase.workspace.ListModulesParams;
import us.kbase.workspace.ModuleVersions;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.test.WorkspaceTestCommon;

/*
 * These tests are specifically for testing the JSON-RPC communications between
 * the client, up to the invocation of the {@link us.kbase.workspace.workspaces.Workspaces}
 * methods. As such they do not test the full functionality of the Workspaces methods;
 * {@link us.kbase.workspace.test.workspaces.TestWorkspaces} handles that. This means
 * that only one backend (the simplest gridFS backend) is tested here, while TestWorkspaces
 * tests all backends and {@link us.kbase.workspace.database.WorkspaceDatabase} implementations.
 */
public class JSONRPCLayerTest {
	
	private static boolean printMemUsage = false;
	
	private static WorkspaceServer SERVER1 = null;
	private static WorkspaceClient CLIENT1 = null;
	private static String USER3 = null;
	private static String USER1 = null;
	private static WorkspaceClient CLIENT2 = null;  // This client connects to SERVER1 as well
	private static String USER2 = null;
	private static WorkspaceClient CLIENT_NO_AUTH = null;
	private static WorkspaceServer SERVER2 = null;
	private static WorkspaceClient CLIENT_FOR_SRV2 = null;  // This client connects to SERVER2
	
	private static SimpleDateFormat DATE_FORMAT =
			new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
	static {
		DATE_FORMAT.setLenient(false);
	}
	
	private final static String TEXT101;
	static {
		String foo = "";
		for (int i = 0; i < 10; i++) {
			foo += "aaaaabbbbb";
		}
		foo += "f";
		TEXT101 = foo;
	}
	private final static String TEXT1000;
	static {
		String foo = "";
		for (int i = 0; i < 100; i++) {
			foo += "aaaaabbbbb";
		}
		TEXT1000 = foo;
	}
	
	
	public static final String SAFE_TYPE = "SomeModule.AType-0.1";
	
	private static class ServerThread extends Thread {
		private WorkspaceServer server;
		
		private ServerThread(WorkspaceServer server) {
			this.server = server;
		}
		
		public void run() {
			try {
				server.startupServer();
			} catch (Exception e) {
				System.err.println("Can't start server:");
				e.printStackTrace();
			}
		}
	}
	
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
		USER3 = System.getProperty("test.user3");
		if (USER1.equals(USER2) || USER2.equals(USER3) || USER1.equals(USER3)) {
			throw new TestException("All the test users must be unique: " + 
					StringUtils.join(Arrays.asList(USER1, USER2, USER3), " "));
		}
		String p1 = System.getProperty("test.pwd1");
		String p2 = System.getProperty("test.pwd2");
		SERVER1 = startupWorkspaceServer(1);
		int port = SERVER1.getServerPort();
		System.out.println("Started test server 1 on port " + port);
		try {
			CLIENT1 = new WorkspaceClient(new URL("http://localhost:" + port), USER1, p1);
		} catch (UnauthorizedException ue) {
			throw new TestException("Unable to login with test.user1: " + USER1 +
					"\nPlease check the credentials in the test configuration.", ue);
		}
		try {
			CLIENT2 = new WorkspaceClient(new URL("http://localhost:" + port), USER2, p2);
		} catch (UnauthorizedException ue) {
			throw new TestException("Unable to login with test.user2: " + USER2 +
					"\nPlease check the credentials in the test configuration.", ue);
		}
		AuthToken t = AuthService.login(USER1, p1).getToken();
		if (!AuthService.isValidUserName(Arrays.asList(USER3), t)
				.containsKey(USER3)) {
			throw new TestException(USER3 + " is not a valid kbase user");
		}
		CLIENT_NO_AUTH = new WorkspaceClient(new URL("http://localhost:" + port));
		CLIENT1.setAuthAllowedForHttp(true);
		CLIENT2.setAuthAllowedForHttp(true);
		CLIENT_NO_AUTH.setAuthAllowedForHttp(true);
		//set up a basic type for test use that doesn't worry about type checking
		CLIENT1.requestModuleOwnership("SomeModule");
		administerCommand(CLIENT2, "approveModRequest", "module", "SomeModule");
		CLIENT1.compileTypespec(new CompileTypespecParams()
			.withDryrun(0L)
			.withSpec("module SomeModule {/* @optional thing */ typedef structure {string thing;} AType;};")
			.withNewTypes(Arrays.asList("AType")));
		CLIENT1.releaseModule("SomeModule");
		SERVER2 = startupWorkspaceServer(2);
		System.out.println("Started test server 2 on port " + SERVER2.getServerPort());
		WorkspaceClient clientForSrv2 = new WorkspaceClient(new URL("http://localhost:" + 
				SERVER2.getServerPort()), USER2, p2);
		clientForSrv2.setAuthAllowedForHttp(true);
		clientForSrv2.requestModuleOwnership("SomeModule");
		administerCommand(clientForSrv2, "approveModRequest", "module", "SomeModule");
		clientForSrv2.compileTypespec(new CompileTypespecParams()
			.withDryrun(0L)
			.withSpec("module SomeModule {/* @optional thing */ typedef structure {int thing;} AType;};")
			.withNewTypes(Arrays.asList("AType")));
		clientForSrv2.releaseModule("SomeModule");
		clientForSrv2.requestModuleOwnership("DepModule");
		administerCommand(clientForSrv2, "approveModRequest", "module", "DepModule");
		clientForSrv2.compileTypespec(new CompileTypespecParams()
			.withDryrun(0L)
			.withSpec("#include <SomeModule>\n" +
					"module DepModule {typedef structure {SomeModule.AType thing;} BType;};")
			.withNewTypes(Arrays.asList("BType")));
		clientForSrv2.releaseModule("DepModule");
		clientForSrv2.compileTypespec(new CompileTypespecParams()
			.withDryrun(0L)
			.withSpec("module SomeModule {/* @optional thing */ typedef structure {string thing;} AType;};")
			.withNewTypes(Collections.<String>emptyList()));
		clientForSrv2.releaseModule("SomeModule");
		clientForSrv2.compileTypespec(new CompileTypespecParams()
			.withDryrun(0L)
			.withSpec("#include <SomeModule>\n" +
					"module DepModule {typedef structure {SomeModule.AType thing;} BType;};")
			.withNewTypes(Collections.<String>emptyList()));
		clientForSrv2.releaseModule("DepModule");
		CLIENT_FOR_SRV2 = clientForSrv2;
		System.out.println("Starting tests");
	}

	public static void administerCommand(WorkspaceClient client, String command, String... params) throws IOException,
			JsonClientException {
		Map<String, String> releasemod = new HashMap<String, String>();
		releasemod.put("command", "approveModRequest");
		for (int i = 0; i < params.length / 2; i++)
			releasemod.put(params[i * 2], params[i * 2 + 1]);
		client.administer(new UObject(releasemod));
	}

	public static WorkspaceServer startupWorkspaceServer(int dbNum)
			throws InvalidHostException, UnknownHostException, IOException,
			NoSuchFieldException, IllegalAccessException, Exception,
			InterruptedException {
		WorkspaceTestCommon.destroyAndSetupDB(dbNum, "gridFS", null);
		
		//write the server config file:
		File iniFile = File.createTempFile("test", ".cfg", new File("./"));
		if (iniFile.exists()) {
			iniFile.delete();
		}
		System.out.println("Created temporary config file: " + iniFile.getAbsolutePath());
		Ini ini = new Ini();
		Section ws = ini.add("Workspace");
		ws.add("mongodb-host", WorkspaceTestCommon.getHost());
		String dbName = dbNum == 1 ? WorkspaceTestCommon.getDB1() : 
			WorkspaceTestCommon.getDB2();
		ws.add("mongodb-database", dbName);
		ws.add("mongodb-user", WorkspaceTestCommon.getMongoUser());
		ws.add("mongodb-pwd", WorkspaceTestCommon.getMongoPwd());
		ws.add("backend-secret", "");
		ws.add("ws-admin", USER2);
		ini.store(iniFile);
		iniFile.deleteOnExit();
		
		//set up env
		Map<String, String> env = getenv();
		env.put("KB_DEPLOYMENT_CONFIG", iniFile.getAbsolutePath());
		env.put("KB_SERVICE_NAME", "Workspace");

		WorkspaceServer.clearConfigForTests();
		WorkspaceServer server = new WorkspaceServer();
		new ServerThread(server).start();
		System.out.println("Main thread waiting for server to start up");
		while (server.getServerPort() == null) {
			Thread.sleep(1000);
		}
		return server;
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (SERVER1 != null) {
			System.out.print("Killing server 1... ");
			SERVER1.stopServer();
			System.out.println("Done");
		}
		if (SERVER2 != null) {
			System.out.print("Killing server 2... ");
			SERVER2.stopServer();
			System.out.println("Done");
		}
	}
	
	@Test
	public void createWSandCheck() throws Exception {
		Tuple7<Long, String, String, String, Long, String, String> meta =
				CLIENT1.createWorkspace(new CreateWorkspaceParams()
					.withWorkspace("foo")
					.withGlobalread("r")
					.withDescription("boogabooga"));
		Tuple7<Long, String, String, String, Long, String, String> metaget =
				CLIENT1.getWorkspaceInfo(new WorkspaceIdentity()
						.withWorkspace("foo"));
		assertThat("ids are equal", meta.getE1(), is(metaget.getE1()));
		assertThat("moddates equal", meta.getE4(), is(metaget.getE4()));
		for (Tuple7<Long, String, String, String, Long, String, String> m:
				Arrays.asList(meta, metaget)) {
			assertThat("ws name correct", m.getE2(), is("foo"));
			assertThat("user name correct", m.getE3(), is(USER1));
			assertThat("obj counts are 0", m.getE5(), is(0L));
			assertThat("permission correct", m.getE6(), is("a"));
			assertThat("global read correct", m.getE7(), is("r"));
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
		assertThat("globalread correct", CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("gl1")).getE7(), is("n"));
		assertThat("globalread correct", CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("gl2")).getE7(), is("n"));
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
		} catch (UnauthorizedException e) {
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
		CLIENT_NO_AUTH.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("permsglob"));
		
		try {
			CLIENT_NO_AUTH.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permspriv"));
			fail("able to read workspace desc with no auth");
		} catch (ServerException e) {
			assertThat("exception message corrent", e.getLocalizedMessage(),
					is("Anonymous users may not read workspace permspriv"));
		}
		
		try {
			CLIENT_NO_AUTH.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("permspriv"));
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
					is("User "+USER2+" may not read workspace permspriv"));
		}
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("r").withUsers(Arrays.asList(USER2)));
		CLIENT2.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permspriv")); //should work, now readable
		
		Map<String, String> data = new HashMap<String, String>();
		data.put("foo", "bar");
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE));
		try {
			CLIENT2.saveObjects(new SaveObjectsParams()
				.withWorkspace("permspriv").withObjects(objects));
		} catch (ServerException e) {
			assertThat("correcet exception", e.getLocalizedMessage(),
					is("User "+USER2+" may not write to workspace permspriv"));
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
					is("User "+USER2+" may not set permissions on workspace permspriv"));
		}
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("a").withUsers(Arrays.asList(USER2)));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("w").withUsers(Arrays.asList(USER3))); //should work
		expected.put(USER1, "a");
		expected.put(USER2, "a");
		expected.put(USER3, "w");
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
		Tuple7<Long, String, String, String, Long, String, String> meta =
				CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace(ws));
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
			CLIENT1.setPermissions(new SetPermissionsParams().withId(-1L)
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
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
					.withWorkspace(TEXT101));
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Workspace name exceeds the maximum length of 100"));
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
		
		objects.set(0, new ObjectSaveData().withName("myname").withObjid(1L));
		saveBadObject(objects, "Must provide one and only one of object name (was: myname) or id (was: 1)");
		
		objects.set(0, new ObjectSaveData().withName("myname+"));
		saveBadObject(objects, "Illegal character in object name myname+: +");
		
		objects.set(0, new ObjectSaveData().withName(TEXT101));
		saveBadObject(objects, "Object name exceeds the maximum length of 100");
		
		objects.set(0, new ObjectSaveData().withObjid(0L));
		saveBadObject(objects, "Object id must be > 0");
		
		objects.set(0, new ObjectSaveData());
		saveBadObject(objects, "Object 1 has no data");
		
		objects.add(0, new ObjectSaveData().withData(new UObject("foo")).withType("Foo.Bar"));
		saveBadObject(objects, "Object 2 has no data");
		
		objects.clear();
		objects.add(new ObjectSaveData().withData(new UObject("foo")));
		saveBadObject(objects, "Object 1 type error: Typestring cannot be null or the empty string");
		
		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withType(null));
		saveBadObject(objects, "Object 1 type error: Typestring cannot be null or the empty string");
		
		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withType(""));
		saveBadObject(objects, "Object 1 type error: Typestring cannot be null or the empty string");
		
		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withType("foo"));
		saveBadObject(objects, "Object 1 type error: Type foo could not be split into a module and name");
		
		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withType("foo.bar-1.2.3"));
		saveBadObject(objects, "Object 1 type error: Type version string 1.2.3 could not be parsed to a version");
	}
	
	@Test
	public void saveProvenance() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("provenance"));
		long wsid = CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("provenance")).getE1();
		UObject data = new UObject(new HashMap<String, Object>());
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("provenance")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(data)
						.withType(SAFE_TYPE))));
		
		SaveObjectsParams sop = new SaveObjectsParams().withWorkspace("provenance")
				.withObjects(objects);
		List<ProvenanceAction> prov = Arrays.asList(
				new ProvenanceAction()
					.withDescription("desc")
					.withInputWsObjects(Arrays.asList("provenance/auto1/1"))
					.withIntermediateIncoming(Arrays.asList("a", "b", "c"))
					.withIntermediateOutgoing(Arrays.asList("d", "e", "f"))
					.withMethod("meth")
					.withMethodParams(Arrays.asList(new UObject("foo"),
							new UObject(new HashMap<String, String>()),
							new UObject(Arrays.asList("foo", "bar"))))
					.withResolvedWsObjects(Arrays.asList("will be ignored"))
					.withScript("script")
					.withScriptCommandLine("cmd line")
					.withScriptVer("1")
					.withService("serv")
					.withServiceVer("2")
					.withTime("2013-04-26T12:52:06-0800"),
				new ProvenanceAction());
		objects.add(new ObjectSaveData().withData(data).withType(SAFE_TYPE)
				.withProvenance(prov));
		CLIENT1.saveObjects(sop);
		List<ObjectData> ret = CLIENT1.getObjects(Arrays.asList(
				new ObjectIdentity().withWorkspace("provenance").withObjid(2L)));
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("provenance/auto1/1", wsid + "/1/1");
		Map<String, String> timemap = new HashMap<String, String>();
		timemap.put("2013-04-26T12:52:06-0800", "2013-04-26T20:52:06+0000");
		assertThat("user correct", ret.get(0).getCreator(), is(USER1));
		assertTrue("created within last 10 mins", 
				DATE_FORMAT.parse(ret.get(0).getCreated())
				.after(getOlderDate(10 * 60 * 1000)));
		
		checkProvenance(prov, ret.get(0).getProvenance(), refmap, timemap);
		
		ProvenanceAction pa = new ProvenanceAction();
		pa.setAdditionalProperties("foo", "bar");
		objects.set(0, new ObjectSaveData().withData(data).withType(SAFE_TYPE)
				.withProvenance(Arrays.asList(pa)));
		try {
			CLIENT1.saveObjects(sop);
			fail("save w/ prov w/ extra fields");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Unexpected arguments in ProvenanceAction: foo"));
		}
		
		saveProvWithBadTime("2013-04-26T25:52:06-0800");
		saveProvWithBadTime("2013-04-26T23:52:06-8000");
		saveProvWithBadTime("2013-04-35T23:52:06-0800");
		saveProvWithBadTime("2013-13-26T23:52:06-0800");
		
		CLIENT1.setPermissions(new SetPermissionsParams().withId(wsid)
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("provenance")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(data)
						.withType(SAFE_TYPE).withName("whoops"))));
		ObjectData d = CLIENT1.getObjects(Arrays.asList(new ObjectIdentity()
			.withName("whoops").withWorkspace("provenance"))).get(0);
		assertThat("user correct", d.getCreator(), is(USER2));
		assertTrue("created within last 10 mins", 
				DATE_FORMAT.parse(d.getCreated())
				.after(getOlderDate(10 * 60 * 1000)));
	}
	
	private Date getOlderDate(long ms) {
		long now = new Date().getTime();
		return new Date(now - ms);
	}
	
	private void saveProvWithBadTime(String time) throws Exception {
		UObject data = new UObject(new HashMap<String, Object>());
		SaveObjectsParams sop = new SaveObjectsParams().withWorkspace("provenance")
				.withObjects(Arrays.asList(
						new ObjectSaveData().withData(data).withType(SAFE_TYPE)
						.withProvenance(Arrays.asList(new ProvenanceAction()
						.withTime(time)))));
		try {
			CLIENT1.saveObjects(sop);
			fail("save w/ prov w/ bad time");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(String.format("Unparseable date: \"%s\"", time)));
		}
	}
	
	private void checkProvenance(List<ProvenanceAction> expected,
			List<ProvenanceAction> got, Map<String, String> refmap,
			Map<String, String> timemap) {
		assertThat("same number actions", got.size(),
				is(expected.size()));
		
		Iterator<ProvenanceAction> gotAct = got.iterator();
		Iterator<ProvenanceAction> expAct = expected.iterator();
		while (gotAct.hasNext()) {
			ProvenanceAction gotpa = gotAct.next();
			ProvenanceAction exppa = expAct.next();
			assertThat("cmd line equal", gotpa.getScriptCommandLine(), is(exppa.getScriptCommandLine()));
			assertThat("desc equal", gotpa.getDescription(), is(exppa.getDescription()));
			assertThat("inc args equal", gotpa.getIntermediateIncoming(), is(exppa.getIntermediateIncoming()));
			assertThat("method equal", gotpa.getMethod(), is(exppa.getMethod()));
			if (gotpa.getMethodParams() == null) {
				assertThat("method param counts are both null", gotpa.getMethodParams(),
						is(exppa.getMethodParams()));
			} else {
				assertThat("method param count equal", gotpa.getMethodParams().size(),
						is(exppa.getMethodParams().size()));
				Iterator<UObject> gotmeth = gotpa.getMethodParams().iterator();
				Iterator<UObject> expmeth = exppa.getMethodParams().iterator();
				while(gotmeth.hasNext()) {
					assertThat("meth params equal", gotmeth.next().asClassInstance(Object.class),
							is(expmeth.next().asClassInstance(Object.class)));
				}
			}
			assertThat("out args equal", gotpa.getIntermediateOutgoing(), is(exppa.getIntermediateOutgoing()));
			assertThat("script equal", gotpa.getScript(), is(exppa.getScript()));
			assertThat("script ver equal", gotpa.getScriptVer(), is(exppa.getScriptVer()));
			assertThat("service equal", gotpa.getService(), is(exppa.getService()));
			assertThat("serv ver equal", gotpa.getServiceVer(), is(exppa.getServiceVer()));
			assertThat("time equal", gotpa.getTime(), is(timemap.get(exppa.getTime())));
			assertThat("refs equal", gotpa.getInputWsObjects(),
					is(exppa.getInputWsObjects() == null ? new ArrayList<String>() :
						exppa.getInputWsObjects()));
			assertThat("correct number resolved refs", gotpa.getResolvedWsObjects().size(),
					is(gotpa.getInputWsObjects().size()));
			Iterator<String> gotrefs = gotpa.getInputWsObjects().iterator();
			Iterator<String> gotresolvedrefs = gotpa.getResolvedWsObjects().iterator();
			while (gotrefs.hasNext()) {
				assertThat("ref resolved correctly", gotresolvedrefs.next(),
						is(refmap.get(gotrefs.next())));
			}
		}
	}

	@Test
	public void saveAndGetObjects() throws Exception {
		
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("saveget"));
		long wsid = CLIENT1.getWorkspaceInfo(
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
		
		try {
			CLIENT1.saveObjects(soc);
			fail("called save with no data");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("No data provided"));
		}
		
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withMeta(meta).withType(SAFE_TYPE)); // will be "1"
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withMeta(meta).withType(SAFE_TYPE)); // will be "2"
		objects.add(new ObjectSaveData().withData(new UObject(data2))
				.withMeta(meta2).withType(SAFE_TYPE).withName("foo"));
		
		List<Tuple10<Long, String, String, String, Long, String, Long, String, String, Long>> retmet =
				CLIENT1.saveObjects(soc);

		assertThat("max obj count correct", CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("saveget")).getE5(), is(3L));
		
		assertThat("num metas correct", retmet.size(), is(3));
		checkInfo(retmet.get(0), 1, "auto1", SAFE_TYPE, 1, USER1, wsid, "saveget", "36c4f68f2c98971b9736839232eb08f4", 23);
		checkInfo(retmet.get(1), 2, "auto2", SAFE_TYPE, 1, USER1, wsid, "saveget", "36c4f68f2c98971b9736839232eb08f4", 23);
		checkInfo(retmet.get(2), 3, "foo", SAFE_TYPE, 1, USER1, wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24);
		
		
		objects.clear();
		objects.add(new ObjectSaveData().withData(new UObject(data2))
				.withMeta(meta2).withType(SAFE_TYPE).withObjid(2L));
		
		retmet = CLIENT1.saveObjects(soc);
		
		assertThat("num metas correct", retmet.size(), is(1));
		checkInfo(retmet.get(0), 2, "auto2", SAFE_TYPE, 2, USER1, wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24);
		
		List<ObjectIdentity> loi = new ArrayList<ObjectIdentity>();
		loi.add(new ObjectIdentity().withRef("saveget/2/1"));
		loi.add(new ObjectIdentity().withRef("kb|ws." + wsid + ".obj.2.ver.1"));
		loi.add(new ObjectIdentity().withRef(wsid + "/2/1"));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withName("auto2").withVer(1L));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withObjid(2L).withVer(1L));
		loi.add(new ObjectIdentity().withWsid(wsid).withName("auto2").withVer(1L));
		loi.add(new ObjectIdentity().withWsid(wsid).withObjid(2L).withVer(1L));
		checkSavedObjects(loi, 2, "auto2", SAFE_TYPE, 1, USER1,
				wsid, "saveget", "36c4f68f2c98971b9736839232eb08f4", 23, meta, data);
		
		loi.clear();
		// w/o versions
		loi.add(new ObjectIdentity().withRef("saveget/2"));
		loi.add(new ObjectIdentity().withRef("kb|ws." + wsid + ".obj.2"));
		loi.add(new ObjectIdentity().withRef(wsid + "/2"));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withName("auto2"));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withObjid(2L));
		loi.add(new ObjectIdentity().withWsid(wsid).withName("auto2"));
		loi.add(new ObjectIdentity().withWsid(wsid).withObjid(2L));
		// w/ versions
		loi.add(new ObjectIdentity().withRef("saveget/2/2"));
		loi.add(new ObjectIdentity().withRef("kb|ws." + wsid + ".obj.2.ver.2"));
		loi.add(new ObjectIdentity().withRef(wsid + "/2/2"));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withName("auto2").withVer(2L));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withObjid(2L).withVer(2L));
		loi.add(new ObjectIdentity().withWsid(wsid).withName("auto2").withVer(2L));
		loi.add(new ObjectIdentity().withWsid(wsid).withObjid(2L).withVer(2L));
		
		checkSavedObjects(loi, 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2, data2);
		
		try {
			CLIENT1.getObjects(new ArrayList<ObjectIdentity>());
			fail("called get obj with no ids");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("No object identifiers provided"));
		}
		
		try {
			CLIENT1.getObjectInfo(new ArrayList<ObjectIdentity>());
			fail("called get meta with no ids");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("No object identifiers provided"));
		}
		
		// try some bad refs and id/name combos
		loi.clear();
		loi.add(new ObjectIdentity().withRef("saveget/2"));
		loi.add(new ObjectIdentity().withRef("kb|wss." + wsid + ".obj.2"));
		getObjectWBadParams(loi, "Error on ObjectIdentity #2: Illegal number of separators / in object reference kb|wss." + wsid + ".obj.2");
		
		loi.set(1, new ObjectIdentity().withRef("saveget/1"));
		loi.add(new ObjectIdentity().withRef("kb|ws." + wsid));
		getObjectWBadParams(loi, "Error on ObjectIdentity #3: Illegal number of separators / in object reference kb|ws." + wsid);
		
		//there are 32 different ways to get this type of error. Just try a few.
		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withName("2"));
		getObjectWBadParams(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. object name: 2");
		
		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withObjid(2L));
		getObjectWBadParams(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. object id: 2");

		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withVer(2L));
		getObjectWBadParams(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. version: 2");

		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withWorkspace("saveget"));
		getObjectWBadParams(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. workspace: saveget");

		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withWsid(wsid));
		getObjectWBadParams(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. workspace id: " + wsid);
		
		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withWsid(wsid).withWorkspace("saveget").withVer(2L));
		getObjectWBadParams(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. workspace: saveget workspace id: " + wsid + " version: 2");
		
		ObjectIdentity oi = new ObjectIdentity().withRef("saveget/1");
		oi.setAdditionalProperties("foo", "bar");
		loi.set(2, oi);
		getObjectWBadParams(loi, "Error on ObjectIdentity #3: Unexpected arguments in ObjectIdentity: foo");
		
		loi.set(2, new ObjectIdentity().withWorkspace("kb|wss." + wsid).withObjid(2L));
		getObjectWBadParams(loi, "Error on ObjectIdentity #3: Illegal character in workspace name kb|wss." + wsid + ": |");
		
		loi.set(2, new ObjectIdentity().withWorkspace("kb|ws." + wsid).withObjid(-1L));
		getObjectWBadParams(loi, "Error on ObjectIdentity #3: Object id must be > 0");
		loi.set(2, new ObjectIdentity().withWorkspace("kb|ws." + wsid).withObjid(1L).withVer(0L));
		getObjectWBadParams(loi, "Error on ObjectIdentity #3: Object version must be > 0");
		loi.set(2, new ObjectIdentity().withWorkspace("kb|ws." + wsid).withObjid(1L).withVer(Integer.MAX_VALUE + 1L));
		getObjectWBadParams(loi, "Error on ObjectIdentity #3: Maximum object version is " + Integer.MAX_VALUE);
		
		
	}
	
	private void getObjectWBadParams(List<ObjectIdentity> loi, String exception)
			throws Exception {
		try {
			CLIENT1.getObjects(loi);
			fail("got object with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
		try {
			CLIENT1.getObjectInfo(loi);
			fail("got meta with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
	}
	
	private void checkSavedObjects(List<ObjectIdentity> loi, long id, String name,
			String type, int ver, String user, long wsid, String wsname, String chksum, long size,
			Map<String, String> meta, Map<String, Object> data) throws Exception {
		List<ObjectData> retdata = CLIENT1.getObjects(loi);
		assertThat("num data correct", retdata.size(), is(loi.size()));
		for (ObjectData o: retdata) {
			checkData(o, id, name, type, ver, user, wsid, wsname,
					chksum, size, meta, data);
		}
		
		List<Tuple11<Long, String, String, String, Long, String, Long, String,
				String, Long, Map<String, String>>> retusermeta =
				CLIENT1.getObjectInfo(loi);
		
		assertThat("num usermeta correct", retusermeta.size(), is(loi.size()));
		for (Tuple11<Long, String, String, String, Long, String, Long,
				String, String, Long, Map<String, String>> o: retusermeta) {
			checkInfoUserMeta(o, id, name, type, ver, user, wsid, wsname,
					chksum, size, meta);
		}
	}

	private void checkData(ObjectData retdata, long id, String name,
			String typeString, int ver, String user, long wsid, String wsname,
			String chksum, long size, Map<String, String> meta, Map<String, Object> data) 
			throws Exception {
		
		assertThat("object data is correct", retdata.getData().asClassInstance(Object.class),
				is((Object) data));
		
		checkInfoUserMeta(retdata.getInfo(), id, name, typeString, ver, user,
				wsid, wsname, chksum, size, meta);
	}

	private void checkInfoUserMeta(
			Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> infousermeta,
			long id, String name, String typeString, int ver, String user,
			long wsid, String wsname, String chksum, long size, Map<String, String> meta)
			throws Exception {
		
		assertThat("id is correct", infousermeta.getE1(), is(id));
		assertThat("name is correct", infousermeta.getE2(), is(name));
		assertThat("type is correct", infousermeta.getE3(), is(typeString));
		DATE_FORMAT.parse(infousermeta.getE4()); //should throw error if bad format
		assertThat("version is correct", (int) infousermeta.getE5().longValue(), is(ver));
		assertThat("user is correct", infousermeta.getE6(), is(user));
		assertThat("wsid is correct", infousermeta.getE7(), is(wsid));
		assertThat("ws name is correct", infousermeta.getE8(), is(wsname));
		assertThat("chksum is correct", infousermeta.getE9(), is(chksum));
		assertThat("size is correct", infousermeta.getE10(), is(size));
		assertThat("meta is correct", infousermeta.getE11(), is(meta));
	}
	private void checkInfo(
			Tuple10<Long, String, String, String, Long, String, Long, String, String, Long> objinfo,
			long id, String name, String typeString, int ver, String user,
			long wsid, String wsname, String chksum, long size) throws Exception {
		
		assertThat("id is correct", objinfo.getE1(), is(id));
		assertThat("name is correct", objinfo.getE2(), is(name));
		assertThat("type is correct", objinfo.getE3(), is(typeString));
		DATE_FORMAT.parse(objinfo.getE4()); //should throw error if bad format
		assertThat("version is correct", (int) objinfo.getE5().longValue(), is(ver));
		assertThat("user is correct", objinfo.getE6(), is(user));
		assertThat("wsid is correct", objinfo.getE7(), is(wsid));
		assertThat("ws name is correct", objinfo.getE8(), is(wsname));
		assertThat("chksum is correct", objinfo.getE9(), is(chksum));
		assertThat("size is correct", objinfo.getE10(), is(size));
	}
	
	@Test
	public void saveBigMeta() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("bigmeta"));

		Map<String, Object> moredata = new HashMap<String, Object>();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, String> meta = new HashMap<String, String>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		for (int i = 0; i < 16; i++) {
			meta.put(Integer.toString(i), TEXT1000); //> 16Mb now
		}
		
		
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("bigmeta")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data)).withType(SAFE_TYPE));
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withMeta(meta));
		
		try {
			CLIENT1.saveObjects(soc);
			fail("called save with too large meta");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Object 2 save error: Metadata is > 16000 bytes"));
		}
	}
	
	@Ignore //TODO unignore when mem issues sorted
	@Test
	public void saveBigData() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("bigdata"));
		
		final boolean[] threadStopWrapper1 = {false};
		Thread t1 = null;
		if (printMemUsage) {
			waitForGC("[JSONRPCLayerTest.saveBigData] Used memory before preparation", 1000000000L);
			System.out.println("----------------------------------------------------------------------");
			t1 = watchForMem("[JSONRPCLayerTest.saveBigData] Used memory during preparation", threadStopWrapper1);
		}
		Map<String, Object> data = new HashMap<String, Object>();
		List<String> subdata = new LinkedList<String>();
		data.put("subset", subdata);
		for (int i = 0; i < 997008; i++) {
			//force allocation of a new char[]
			subdata.add("" + TEXT1000);
		}
		
		final boolean[] threadStopWrapper2 = {false};
		Thread t2 = null;
		if (printMemUsage) {
			threadStopWrapper1[0] = true;
			t1.join();
			System.out.println("----------------------------------------------------------------------");
			t2 = watchForMem("[JSONRPCLayerTest.saveBigData] Used memory during saveObject", threadStopWrapper2);
		}
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("bigdata")
				.withObjects(Arrays.asList(new ObjectSaveData().withType(SAFE_TYPE)
						.withData(new UObject(data)))));
		if (printMemUsage) {
			threadStopWrapper2[0] = true;
			t2.join();
		}
		
		data = null;
		subdata = null;
		
		final boolean[] threadStopWrapper3 = {false};
		Thread t3 = null;
		if (printMemUsage) {
			System.out.println("----------------------------------------------------------------------");
			waitForGC("[JSONRPCLayerTest.saveBigData] Used memory before getObject", 1000000000L);
			System.out.println("----------------------------------------------------------------------");
			t3 = watchForMem("[JSONRPCLayerTest.saveBigData] Used memory during getObject", threadStopWrapper3);
		}
		// need 3g to get to this point
		data = CLIENT1.getObjects(Arrays.asList(new ObjectIdentity().withObjid(1L)
				.withWorkspace("bigdata"))).get(0).getData().asInstance();
		if (printMemUsage) {
			threadStopWrapper3[0] = true;
			t3.join();
			System.out.println("----------------------------------------------------------------------");
			waitForGC("[JSONRPCLayerTest.saveBigData] Used memory after getObject", 3000000000L);
		}
		//need 6g to get past readValueAsTree() in UObjectDeserializer
		assertThat("correct obj keys", data.keySet(),
				is((Set<String>) new HashSet<String>(Arrays.asList("subset"))));
		@SuppressWarnings("unchecked")
		List<String> newsd = (List<String>) data.get("subset");
		assertThat("correct subdata size", newsd.size(), is(997008));
		for (String s: newsd) {
			assertThat("correct string in subdata", s, is(TEXT1000));
		}
	}

	private static Thread watchForMem(final String header, final boolean[] threadStopWrapper) {
		Thread ret = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					Runtime r = Runtime.getRuntime();
					long freeMem = r.freeMemory();
					long totalMem = r.totalMemory();
					long usedMem = totalMem - freeMem;
					System.out.println(header + ": " + usedMem);
					if (threadStopWrapper[0])
						break;
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		});
		ret.start();
		return ret;
	}
	
	private static void waitForGC(String header, long maxUsedMem) throws InterruptedException {
		while (true) {
			long freeMem = Runtime.getRuntime().freeMemory();
			long totalMem = Runtime.getRuntime().totalMemory();
			long usedMem = totalMem - freeMem;
			System.out.println(header + ": " + usedMem);
			if (usedMem < maxUsedMem)
				break;
			System.gc();
			Thread.sleep(1000);
		}
	}
	
	@Test
	public void unicode() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("unicode"));
		
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
		
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("unicode")
				.withObjects(Arrays.asList(new ObjectSaveData().withType(SAFE_TYPE)
						.withData(new UObject(data)))));
		data = CLIENT1.getObjects(Arrays.asList(new ObjectIdentity().withObjid(1L)
				.withWorkspace("unicode"))).get(0).getData().asInstance();
		
		assertThat("correct obj keys", data.keySet(),
				is((Set<String>) new HashSet<String>(Arrays.asList("subset"))));
		@SuppressWarnings("unchecked")
		List<String> newsd = (List<String>) data.get("subset");
		assertThat("correct subdata size", newsd.size(), is(6000000));
		for (String s: newsd) {
			assertThat("correct string in subdata", s, is(test));
		}
		
		data.clear();
		data.put(test, "foo");
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("unicode")
				.withObjects(Arrays.asList(new ObjectSaveData().withType(SAFE_TYPE)
						.withData(new UObject(data)))));
		data = CLIENT1.getObjects(Arrays.asList(new ObjectIdentity().withObjid(2L)
				.withWorkspace("unicode"))).get(0).getData().asInstance();
		
		assertThat("unicode key correct", data.keySet(),
				is((Set<String>) new HashSet<String>(Arrays.asList(test))));
		assertThat("value correct", (String) data.get(test), is("foo"));
	}
	
	@Test
	public void parseRef() throws Exception {
		final String specParseRef =
				"module TestKBaseRefParsing {" +
					"/* @id ws */" +
					"typedef string reference;" +
					"typedef structure {" +
						"reference ref1;" +
						"reference ref2;" + 
						"reference ref3;" +
						"reference ref4;" +
					"} ParseRef;" +
				"};";
		CLIENT1.requestModuleOwnership("TestKBaseRefParsing");
		administerCommand(CLIENT2, "approveModRequest", "module", "TestKBaseRefParsing");
		CLIENT1.compileTypespec(new CompileTypespecParams()
			.withDryrun(0L)
			.withSpec(specParseRef)
			.withNewTypes(Arrays.asList("ParseRef")));
		String type ="TestKBaseRefParsing.ParseRef-0.1";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("parseref"));
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("parseref");
		long wsid = CLIENT1.getWorkspaceInfo(wsi).getE1();
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("fubar", "foo");
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("parseref")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE));
		CLIENT1.saveObjects(soc);
		data.clear();
		data.put("ref1", "kb|ws." + wsid + ".obj.1");
		data.put("ref2", "kb|ws." + wsid + ".obj.1.ver.2");
		data.put("ref3", "kb|ws." + wsid + ".obj.2");
		data.put("ref4", "kb|ws." + wsid + ".obj.2.ver.1");
		objects.clear();
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(type));
		CLIENT1.saveObjects(soc);
		Map<String, String> refs = CLIENT1.getObjects(Arrays.asList(
				new ObjectIdentity().withWsid(wsid).withName("auto3")))
				.get(0).getData().asInstance();
		assertThat("correct ref parse/rewrite", refs.get("ref1"), is(wsid + "/1/3"));
		assertThat("correct ref parse/rewrite", refs.get("ref2"), is(wsid + "/1/2"));
		assertThat("correct ref parse/rewrite", refs.get("ref3"), is(wsid + "/2/1"));
		assertThat("correct ref parse/rewrite", refs.get("ref4"), is(wsid + "/2/1"));
	}
	
	@Test
	public void deleteUndelete() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("delundel")
				.withDescription("foo"));
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("delundel");
		long wsid = CLIENT1.getWorkspaceInfo(wsi).getE1();
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("delundel")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		CLIENT1.saveObjects(soc);
		List<ObjectIdentity> loi = Arrays.asList(new ObjectIdentity()
				.withRef("delundel/myname"));
		assertThat("can get data", CLIENT1.getObjects(loi).get(0).getData()
				.asClassInstance(Object.class), is((Object) data));
		CLIENT1.deleteObjects(loi);
		try {
			CLIENT1.getObjects(loi);
			fail("got deleted object");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("Object 1 (name myname) in workspace " + wsid + " has been deleted"));
		}
		CLIENT1.undeleteObjects(loi);
		assertThat("can get data", CLIENT1.getObjects(loi).get(0).getData()
				.asClassInstance(Object.class), is((Object) data));
		CLIENT1.deleteWorkspace(wsi);
		try {
			CLIENT1.getObjects(loi);
			fail("got deleted object");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("Object myname cannot be accessed: Workspace delundel is deleted"));
		}
		try {
			CLIENT1.getWorkspaceDescription(wsi);
			fail("got desc from deleted WS");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("Workspace delundel is deleted"));
		}
		CLIENT1.undeleteWorkspace(wsi);
		assertThat("can get data", CLIENT1.getObjects(loi).get(0).getData()
				.asClassInstance(Object.class), is((Object) data));
		assertThat("can get description", CLIENT1.getWorkspaceDescription(wsi),
				is("foo"));
		CLIENT1.deleteObjects(loi);
		try {
			CLIENT1.getObjects(loi);
			fail("got deleted object");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("Object 1 (name myname) in workspace " + wsid + " has been deleted"));
		}
		CLIENT1.saveObjects(soc);
		assertThat("can get data", CLIENT1.getObjects(loi).get(0).getData()
				.asClassInstance(Object.class), is((Object) data));
	}
	
	@Test
	public void testTypeMD5() throws Exception {
		String typeDefName = "SomeModule.AType";
		Map<String,String> type2md5 = CLIENT1.translateToMD5Types(Arrays.asList(typeDefName));
		String md5TypeDef = type2md5.get(typeDefName);
		Assert.assertNotNull(md5TypeDef);
		Map<String, List<String>> md52semantic = CLIENT1.translateFromMD5Types(Arrays.asList(md5TypeDef));
		Assert.assertEquals(1, md52semantic.size());
		Assert.assertTrue(md52semantic.get(md5TypeDef).contains("SomeModule.AType-1.0"));
	}
	
	@Test
	public void testSpecSync() throws Exception {
		CLIENT1.requestModuleOwnership("DepModule");
		administerCommand(CLIENT2, "approveModRequest", "module", "DepModule");
		String urlForSrv2 = "http://localhost:" + SERVER2.getServerPort();
		ModuleVersions vers = CLIENT_FOR_SRV2.listModuleVersions(
				new ListModuleVersionsParams().withMod("DepModule"));
		long lastVer = CLIENT_FOR_SRV2.getModuleInfo(
				new GetModuleInfoParams().withMod("DepModule")).getVer();
		for (long ver : vers.getVers()) {
			boolean ok = true;
			try {
				CLIENT1.compileTypespecCopy(urlForSrv2, "DepModule", ver);
			} catch (Exception ignore) {
				ok = false;
			}
			Assert.assertEquals(ver == lastVer, ok);
			if (ok) {
				CLIENT1.releaseModule("DepModule");
				Assert.assertTrue(CLIENT1.getModuleInfo(new GetModuleInfoParams().withMod(
						"DepModule")).getTypes().containsKey("DepModule.BType-1.0"));
			}
		}
	}
	
	@Test
	public void testTypeAndModuleLookups() throws Exception {
		final String spec =
				"module TestModule { " +
						"typedef structure {string name; string seq;} Feature; "+
						"typedef structure {string name; list<Feature> features;} Genome; "+
						"typedef structure {string private_stuff;} InternalObj; "+
						"funcdef getFeature(string fid, string pattern) returns (Feature);" +
				"};";
		CLIENT1.requestModuleOwnership("TestModule");
		administerCommand(CLIENT2, "approveModRequest", "module", "TestModule");
		CLIENT1.compileTypespec(new CompileTypespecParams()
			.withDryrun(0L)
			.withSpec(spec)
			.withNewTypes(Arrays.asList("Feature","Genome")));
		CLIENT1.releaseModule("TestModule");
		
		// make sure the list of modules includes the TestModule
		Map<String,String> moduleNamesInList = new HashMap<String,String>();
		for(String mod: CLIENT1.listModules(new ListModulesParams())) {
			moduleNamesInList.put(mod, "");
		}
		Assert.assertTrue(moduleNamesInList.containsKey("TestModule"));
		
		// make sure that we can list the versions of this module, there should be just 2 visible to client1...
		Assert.assertEquals(
				2,
				CLIENT1.listModuleVersions(new ListModuleVersionsParams().withMod("TestModule")).getVers().size());
		
		// make sure we can retrieve module info
		Assert.assertEquals(
				2,
				CLIENT1.getModuleInfo(new GetModuleInfoParams().withMod("TestModule")).getTypes().size());
		
		// make sure we can get a json schema and parse it as a json document
		ObjectMapper map = new ObjectMapper();
		JsonNode schema = map.readTree(CLIENT1.getJsonschema("TestModule.Feature"));
		Assert.assertEquals("Feature", schema.get("id").asText());
		
		// make sure we can get type info
		Assert.assertEquals("TestModule.Feature-1.0",CLIENT1.getTypeInfo("TestModule.Feature-1").getTypeDef());
		
		// make sure we can get func info
		Assert.assertEquals("TestModule.getFeature-1.0",CLIENT1.getFuncInfo("TestModule.getFeature").getFuncDef());
	}
	
}
