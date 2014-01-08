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
import us.kbase.auth.AuthUser;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple12;
import us.kbase.common.service.Tuple8;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.test.TestException;
import us.kbase.workspace.CloneWorkspaceParams;
import us.kbase.workspace.CopyObjectParams;
import us.kbase.workspace.ListObjectsParams;
import us.kbase.workspace.RegisterTypespecCopyParams;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GetModuleInfoParams;
import us.kbase.workspace.ListModuleVersionsParams;
import us.kbase.workspace.ListModulesParams;
import us.kbase.workspace.ModuleVersions;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.RenameObjectParams;
import us.kbase.workspace.RenameWorkspaceParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.SetWorkspaceDescriptionParams;
import us.kbase.workspace.TypeInfo;
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
	private static WorkspaceClient CLIENT2 = null;  // This client connects to SERVER1 as well
	private static String USER1 = null;
	private static String USER2 = null;
	private static String USER3 = null;
	private static AuthUser AUTH_USER1 = null;
	private static AuthUser AUTH_USER2 = null;
	private static WorkspaceServer SERVER2 = null;
	private static WorkspaceClient CLIENT_FOR_SRV2 = null;  // This client connects to SERVER2
	private static WorkspaceClient CLIENT_NO_AUTH = null;
	
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
		AUTH_USER1 = AuthService.login(USER1, p1);
		AUTH_USER2 = AuthService.login(USER2, p2);
		if (!AuthService.isValidUserName(Arrays.asList(USER3), AUTH_USER1.getToken())
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
		CLIENT1.registerTypespec(new RegisterTypespecParams()
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
		clientForSrv2.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("module SomeModule {/* @optional thing */ typedef structure {int thing;} AType;};")
			.withNewTypes(Arrays.asList("AType")));
		clientForSrv2.releaseModule("SomeModule");
		clientForSrv2.requestModuleOwnership("DepModule");
		administerCommand(clientForSrv2, "approveModRequest", "module", "DepModule");
		clientForSrv2.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("#include <SomeModule>\n" +
					"module DepModule {typedef structure {SomeModule.AType thing;} BType;};")
			.withNewTypes(Arrays.asList("BType")));
		clientForSrv2.releaseModule("DepModule");
		clientForSrv2.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("module SomeModule {/* @optional thing */ typedef structure {string thing;} AType;};")
			.withNewTypes(Collections.<String>emptyList()));
		clientForSrv2.releaseModule("SomeModule");
		clientForSrv2.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("#include <SomeModule>\n" +
					"module DepModule {typedef structure {SomeModule.AType thing;} BType;};")
			.withNewTypes(Collections.<String>emptyList()));
		clientForSrv2.releaseModule("DepModule");
		clientForSrv2.requestModuleOwnership("UnreleasedModule");
		administerCommand(clientForSrv2, "approveModRequest", "module", "UnreleasedModule");
		clientForSrv2.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("module UnreleasedModule {typedef int AType; funcdef aFunc(AType param) returns ();};")
			.withNewTypes(Arrays.asList("AType")));
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
		Tuple8<Long, String, String, String, Long, String, String, String> meta =
				CLIENT1.createWorkspace(new CreateWorkspaceParams()
					.withWorkspace("foo")
					.withGlobalread("r")
					.withDescription("boogabooga"));
		Tuple8<Long, String, String, String, Long, String, String, String> metaget =
				CLIENT1.getWorkspaceInfo(new WorkspaceIdentity()
						.withWorkspace("foo"));
		checkWS(meta, meta.getE1(), meta.getE4(), "foo", USER1, 0, "a", "r", "unlocked", "boogabooga");
		checkWS(metaget, meta.getE1(), meta.getE4(), "foo", USER1, 0, "a", "r", "unlocked", "boogabooga");
	}
		
	private void checkWS(Tuple8<Long, String, String, String, Long, String, String, String> info,
			long id, String moddate, String name, String user, long objects, String perm,
			String globalperm, String lockstat, String desc) 
			throws Exception {
		assertThat("ids correct", info.getE1(), is(id));
		assertThat("moddates correct", info.getE4(), is(moddate));
		assertThat("ws name correct", info.getE2(), is(name));
		assertThat("user name correct", info.getE3(), is(user));
		assertThat("obj counts are 0", info.getE5(), is(objects));
		assertThat("permission correct", info.getE6(), is(perm));
		assertThat("global read correct", info.getE7(), is(globalperm));
		assertThat("lockstate correct", info.getE8(), is(lockstat));
		assertThat("description correct", CLIENT1.getWorkspaceDescription(
				new WorkspaceIdentity().withWorkspace(name)), is(desc));
	}
	
	@Test
	public void setWorkspaceDescription() throws Exception {
		Tuple8<Long, String, String, String, Long, String, String, String> meta =
				CLIENT1.createWorkspace(new CreateWorkspaceParams()
					.withWorkspace("wsdesc"));
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("wsdesc");
		CLIENT1.setWorkspaceDescription(new SetWorkspaceDescriptionParams()
				.withDescription("foobar").withWorkspace("wsdesc"));
		assertThat("correct ws desc", CLIENT1.getWorkspaceDescription(wsi),
				is("foobar"));
		SetWorkspaceDescriptionParams swdp = new SetWorkspaceDescriptionParams()
				.withDescription("foo").withId(meta.getE1());
		swdp.setAdditionalProperties("baz", "foo");
		failSetWSDesc(swdp, "Unexpected arguments in SetWorkspaceDescriptionParams: baz");
		failSetWSDesc(new SetWorkspaceDescriptionParams(),
				"Must provide one and only one of workspace name (was: null) or id (was: null)");
		failSetWSDesc(new SetWorkspaceDescriptionParams().withWorkspace("foo").withId(1L),
				"Must provide one and only one of workspace name (was: foo) or id (was: 1)");
	}
	
	private void failSetWSDesc(SetWorkspaceDescriptionParams swdp, String excep)
			throws Exception {
		try {
			CLIENT1.setWorkspaceDescription(swdp);
			fail("set ws desc with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(excep));
		}
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
		Tuple8<Long, String, String, String, Long, String, String, String> meta =
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
		
		List<Tuple11<Long, String, String, String, Long, String, Long, String,
			String, Long, Map<String, String>>> retmet =
				CLIENT1.saveObjects(soc);

		assertThat("max obj count correct", CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("saveget")).getE5(), is(3L));
		
		assertThat("num metas correct", retmet.size(), is(3));
		checkInfo(retmet.get(0), 1, "auto1", SAFE_TYPE, 1, USER1, wsid, "saveget", "36c4f68f2c98971b9736839232eb08f4", 23, meta);
		checkInfo(retmet.get(1), 2, "auto2", SAFE_TYPE, 1, USER1, wsid, "saveget", "36c4f68f2c98971b9736839232eb08f4", 23, meta);
		checkInfo(retmet.get(2), 3, "foo", SAFE_TYPE, 1, USER1, wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);
		
		
		objects.clear();
		objects.add(new ObjectSaveData().withData(new UObject(data2))
				.withMeta(meta2).withType(SAFE_TYPE).withObjid(2L));
		
		retmet = CLIENT1.saveObjects(soc);
		
		assertThat("num metas correct", retmet.size(), is(1));
		checkInfo(retmet.get(0), 2, "auto2", SAFE_TYPE, 2, USER1, wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);
		
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
			CLIENT1.getObjectInfo(new ArrayList<ObjectIdentity>(), 0L);
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
	
	@SuppressWarnings("deprecation")
	@Test
	public void deprecated_saveObject() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("depsave"));
		long wsid = CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("depsave")).getE1();
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("depsave")
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		
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
		
		Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> obj1 =
				CLIENT1.saveObject(new us.kbase.workspace.SaveObjectParams().withId("obj1")
				.withMetadata(meta).withType(SAFE_TYPE).withWorkspace("depsave")
				.withData(new UObject(data)));
		System.out.println(obj1);
		
		Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> obj2 =
				CLIENT1.saveObject(new us.kbase.workspace.SaveObjectParams().withId("obj2")
				.withMetadata(meta2).withType(SAFE_TYPE).withWorkspace("depsave")
				.withData(new UObject(data2)));
		System.out.println(obj2);
		
		Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> obj3 =
				CLIENT1.saveObject(new us.kbase.workspace.SaveObjectParams().withId("obj3")
				.withMetadata(meta2).withType(SAFE_TYPE).withWorkspace("depsave")
				.withData(new UObject(data)).withAuth(AUTH_USER2.getTokenString()));
		System.out.println(obj3);
		
		checkDeprecatedSaveInfo(obj1, 1, "obj1", SAFE_TYPE, 1, USER1, wsid, "depsave", "36c4f68f2c98971b9736839232eb08f4", meta);
		checkDeprecatedSaveInfo(obj2, 2, "obj2", SAFE_TYPE, 1, USER1, wsid, "depsave", "3c59f762140806c36ab48a152f28e840", meta2);
		checkDeprecatedSaveInfo(obj3, 3, "obj3", SAFE_TYPE, 1, USER2, wsid, "depsave", "36c4f68f2c98971b9736839232eb08f4", meta2);
		
		checkSavedObjects(Arrays.asList(new ObjectIdentity().withWsid(wsid).withObjid(1L)),
				1, "obj1", SAFE_TYPE, 1, USER1, wsid, "depsave", "36c4f68f2c98971b9736839232eb08f4",
				23, meta, data);
		checkSavedObjects(Arrays.asList(new ObjectIdentity().withWsid(wsid).withObjid(2L)),
				2, "obj2", SAFE_TYPE, 1, USER1, wsid, "depsave", "3c59f762140806c36ab48a152f28e840",
				24, meta2, data2);
		checkSavedObjects(Arrays.asList(new ObjectIdentity().withWsid(wsid).withObjid(3L)),
				3, "obj3", SAFE_TYPE, 1, USER2, wsid, "depsave", "36c4f68f2c98971b9736839232eb08f4",
				23, meta2, data);
		
		failDepSaveObject(new us.kbase.workspace.SaveObjectParams().withId("obj3")
				.withMetadata(meta2).withType(SAFE_TYPE).withWorkspace("depsave")
				.withData(new UObject(data)).withAuth(AUTH_USER2.getTokenString() + "a"),
				"Token is invalid");
		failDepSaveObject(new us.kbase.workspace.SaveObjectParams().withId("obj3")
				.withMetadata(meta2).withType(SAFE_TYPE).withWorkspace("depsave")
				.withData(new UObject(data)).withAuth("borkborkbork"),
				"Auth token is in the incorrect format, near 'borkborkbork'");
	}
	
	@SuppressWarnings("deprecation")
	private void failDepSaveObject(us.kbase.workspace.SaveObjectParams sop, String exp)
			throws Exception {
		try {
			CLIENT1.saveObject(sop);
			fail("hide obj with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
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
			CLIENT1.getObjectInfo(loi, 0L);
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
				CLIENT1.getObjectInfo(loi, 1L);
		
		assertThat("num usermeta correct", retusermeta.size(), is(loi.size()));
		for (Tuple11<Long, String, String, String, Long, String, Long,
				String, String, Long, Map<String, String>> o: retusermeta) {
			checkInfo(o, id, name, type, ver, user, wsid, wsname,
					chksum, size, meta);
		}
		
		retusermeta = CLIENT1.getObjectInfo(loi, 0L);

		assertThat("num usermeta correct", retusermeta.size(), is(loi.size()));
		for (Tuple11<Long, String, String, String, Long, String, Long,
				String, String, Long, Map<String, String>> o: retusermeta) {
			checkInfo(o, id, name, type, ver, user, wsid, wsname,
					chksum, size, null);
		}
	}

	private void checkData(ObjectData retdata, long id, String name,
			String typeString, int ver, String user, long wsid, String wsname,
			String chksum, long size, Map<String, String> meta, Map<String, Object> data) 
			throws Exception {
		
		assertThat("object data is correct", retdata.getData().asClassInstance(Object.class),
				is((Object) data));
		
		checkInfo(retdata.getInfo(), id, name, typeString, ver, user,
				wsid, wsname, chksum, size, meta);
	}

	private void checkInfo(
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
	
	private void checkDeprecatedSaveInfo(
			Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> info,
			long id, String name, String type, int ver, String user,
			long wsid, String wsname, String chksum,
			Map<String, String> meta) throws Exception {
		assertThat("name is correct", info.getE1(), is(name));
		assertThat("type is correct", info.getE2(), is(type));
		DATE_FORMAT.parse(info.getE3()); //should throw error if bad format
		assertThat("version is correct", (int) info.getE4().longValue(), is(ver));
		assertThat("command is correct", info.getE5(), is(""));
		assertThat("last modifier is correct", info.getE6(), is(user));
		assertThat("owner is correct", info.getE7(), is(user));
		assertThat("ws name is correct", info.getE8(), is(wsname));
		assertThat("ref is correct", info.getE9(), is(""));
		assertThat("chksum is correct", info.getE10(), is(chksum));
		assertThat("meta is correct", info.getE11(), is(meta));
		assertThat("id is correct", info.getE12(), is(id));
		
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
//			waitForGC("[JSONRPCLayerTest.saveBigData] Used memory before preparation", 1000000000L);
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
//			waitForGC("[JSONRPCLayerTest.saveBigData] Used memory before getObject", 1000000000L);
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
//			waitForGC("[JSONRPCLayerTest.saveBigData] Used memory after getObject", 3000000000L);
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
					System.out.println(header + ". used: " + usedMem + " total: " + totalMem);
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
	
	@SuppressWarnings("unused")
	private static void waitForGC(String header, long maxUsedMem) throws InterruptedException {
		while (true) {
			long freeMem = Runtime.getRuntime().freeMemory();
			long totalMem = Runtime.getRuntime().totalMemory();
			long usedMem = totalMem - freeMem;
			System.out.println(header + ". used: " + usedMem + " total: " + totalMem);
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
		CLIENT1.registerTypespec(new RegisterTypespecParams()
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
		Set<String> expectedRefs = new HashSet<String>();
		data.put("ref1", "kb|ws." + wsid + ".obj.1");
		expectedRefs.add(wsid + "/1/3");
		data.put("ref2", "kb|ws." + wsid + ".obj.1.ver.2");
		expectedRefs.add(wsid + "/1/2");
		data.put("ref3", "kb|ws." + wsid + ".obj.2");
		expectedRefs.add(wsid + "/2/1");
		data.put("ref4", "kb|ws." + wsid + ".obj.2.ver.1");
		expectedRefs.add(wsid + "/2/1");
		objects.clear();
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(type));
		CLIENT1.saveObjects(soc);
		ObjectData od = CLIENT1.getObjects(Arrays.asList(
				new ObjectIdentity().withWsid(wsid).withName("auto3")))
				.get(0);
		Map<String, String> refs = od.getData().asInstance();
		assertThat("correct ref parse/rewrite", refs.get("ref1"), is(wsid + "/1/3"));
		assertThat("correct ref parse/rewrite", refs.get("ref2"), is(wsid + "/1/2"));
		assertThat("correct ref parse/rewrite", refs.get("ref3"), is(wsid + "/2/1"));
		assertThat("correct ref parse/rewrite", refs.get("ref4"), is(wsid + "/2/1"));
		assertThat("correct refs returned", new HashSet<String>(od.getRefs()),
				is(expectedRefs));
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
	public void copyRevert() throws Exception {
		long wsid = CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("copyrev")).getE1();
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("copyrev")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(moredata))
				.withType(SAFE_TYPE).withName("myname"));
		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> objs =
				CLIENT1.saveObjects(soc);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> copied =
				CLIENT1.copyObject(new CopyObjectParams().withFrom(new ObjectIdentity().withRef("copyrev/myname"))
				.withTo(new ObjectIdentity().withWsid(wsid).withName("myname2")));
		compareObjectInfoAndData(objs.get(1), copied, "copyrev", wsid, "myname2", 2L, 2);
		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> copystack =
				CLIENT1.getObjectHistory(new ObjectIdentity().withWsid(wsid).withName("myname2"));
		compareObjectInfoAndData(objs.get(0), copystack.get(0), "copyrev", wsid, "myname2", 2L, 1);
		compareObjectInfoAndData(objs.get(1), copystack.get(1), "copyrev", wsid, "myname2", 2L, 2);
		
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> rev =
				CLIENT1.revertObject(new ObjectIdentity().withWorkspace("copyrev").withObjid(2L)
				.withVer(1L));
		compareObjectInfoAndData(objs.get(0), rev, "copyrev", wsid, "myname2", 2L, 3);
		copystack = CLIENT1.getObjectHistory(new ObjectIdentity().withWsid(wsid).withName("myname2"));
		compareObjectInfoAndData(objs.get(0), copystack.get(0), "copyrev", wsid, "myname2", 2L, 1);
		compareObjectInfoAndData(objs.get(1), copystack.get(1), "copyrev", wsid, "myname2", 2L, 2);
		compareObjectInfoAndData(objs.get(0), copystack.get(2), "copyrev", wsid, "myname2", 2L, 3);
		
		CopyObjectParams cpo = new CopyObjectParams().withFrom(new ObjectIdentity().withRef("copyrev/myname"))
				.withTo(new ObjectIdentity().withWsid(wsid).withName("myname2"));
		cpo.setAdditionalProperties("foo", "bar");
		try {
			CLIENT1.copyObject(cpo);
			fail("copied with bad params");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getLocalizedMessage(),
					is("Unexpected arguments in CopyObjectParams: foo"));
		}
	}
	
	private void compareObjectInfoAndData(
			Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> orig,
			Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> copied,
			String wsname, long wsid, String name, long id, int ver) 
			throws Exception {
		compareObjectInfo(orig, copied, wsname, wsid, name, id, ver);
		List<ObjectData> objs = CLIENT1.getObjects(Arrays.asList(new ObjectIdentity().withWsid(orig.getE7())
				.withObjid(orig.getE1()).withVer(orig.getE5()), 
				new ObjectIdentity().withWsid(copied.getE7())
				.withObjid(copied.getE1()).withVer(copied.getE5())));
		compareObjectInfo(objs.get(0).getInfo(), objs.get(1).getInfo(), wsname, wsid, name, id, ver);
		assertThat("creator same", objs.get(1).getCreator(), is(objs.get(0).getCreator()));
		assertThat("created same", objs.get(1).getCreated(), is(objs.get(0).getCreated()));
		assertThat("data same", objs.get(1).getData().asClassInstance(Map.class),
				is(objs.get(0).getData().asClassInstance(Map.class)));
		assertThat("prov same", objs.get(1).getProvenance(), is(objs.get(0).getProvenance()));
		assertThat("refs same", objs.get(1).getRefs(), is(objs.get(0).getRefs()));
	}

	private void compareObjectInfo(
			Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> orig,
			Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> copied,
			String wsname, long wsid, String name, long id, int ver) 
			throws Exception {
		assertThat("id is correct", copied.getE1(), is(id));
		assertThat("name is correct", copied.getE2(), is(name));
		assertThat("type is correct", copied.getE3(), is(orig.getE3()));
		DATE_FORMAT.parse(copied.getE4()); //should throw error if bad format
		assertThat("version is correct", (int) copied.getE5().longValue(), is(ver));
		assertThat("user is correct", copied.getE6(), is(orig.getE6()));
		assertThat("wsid is correct", copied.getE7(), is(wsid));
		assertThat("ws name is correct", copied.getE8(), is(wsname));
		assertThat("chksum is correct", copied.getE9(), is(orig.getE9()));
		assertThat("size is correct", copied.getE10(), is(orig.getE10()));
		assertThat("meta is correct", copied.getE11(), is(orig.getE11()));
		
	}
	
	@Test
	public void cloneWorkspace() throws Exception {
		String source = "clonesource";
		WorkspaceIdentity wssrc = new WorkspaceIdentity().withWorkspace(source);
		long wsid = CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(source)).getE1();
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace(source)
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(moredata))
				.withType(SAFE_TYPE).withName("myname"));
		
		CLIENT1.saveObjects(soc);
		
		Tuple8<Long, String, String, String, Long, String, String, String> wsinfo =
				CLIENT1.cloneWorkspace(new CloneWorkspaceParams().withDescription("a desc")
				.withGlobalread("r").withWorkspace("newclone").withWsi(wssrc));
		checkWS(wsinfo, wsinfo.getE1(), wsinfo.getE4(), "newclone", USER1, 1, "a", "r", "unlocked", "a desc");
		
		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> objs =
				CLIENT1.getObjectHistory(new ObjectIdentity().withWsid(wsid).withName("myname"));
		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> copystack =
				CLIENT1.getObjectHistory(new ObjectIdentity().withWsid(wsinfo.getE1()).withName("myname"));
		compareObjectInfoAndData(objs.get(0), copystack.get(0), "newclone", wsinfo.getE1(), "myname", 1L, 1);
		compareObjectInfoAndData(objs.get(1), copystack.get(1), "newclone", wsinfo.getE1(), "myname", 1L, 2);
		
		Tuple8<Long, String, String, String, Long, String, String, String> wsinfo2 =
				CLIENT1.cloneWorkspace(new CloneWorkspaceParams().withWorkspace("newclone2").withWsi(wssrc));
		checkWS(wsinfo2, wsinfo2.getE1(), wsinfo2.getE4(), "newclone2", USER1, 1, "a", "n", "unlocked", null);
		
		
		CloneWorkspaceParams cpo = new CloneWorkspaceParams().withWsi(new WorkspaceIdentity().withWorkspace("newclone"))
				.withWorkspace("fake");
		cpo.setAdditionalProperties("foo", "bar");
		try {
			CLIENT1.cloneWorkspace(cpo);
			fail("cloned with bad params");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getLocalizedMessage(),
					is("Unexpected arguments in CloneWorkspaceParams: foo"));
		}
		
		cpo = new CloneWorkspaceParams().withWsi(new WorkspaceIdentity().withWorkspace("newclone"))
				.withWorkspace("fake");
		try {
			CLIENT1.cloneWorkspace(cpo.withGlobalread("w"));
			fail("cloned with bad params");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getLocalizedMessage(),
					is("globalread must be n or r"));
		}
	}
	
	@Test
	public void lockWorkspace() throws Exception {
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("lock");
		Tuple8<Long, String, String, String, Long, String, String, String> info =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("lock"));
		long wsid = info.getE1();
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("lock")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(moredata))
				.withType(SAFE_TYPE).withName("myname"));
		
		CLIENT1.saveObjects(soc);
		
		Tuple8<Long, String, String, String, Long, String, String, String> lockinfo =
				CLIENT1.lockWorkspace(wsi);
		checkWS(lockinfo, wsid, info.getE4(), "lock", USER1, 1, "a", "n", "locked", null);
		try {
			CLIENT1.setWorkspaceDescription(new SetWorkspaceDescriptionParams().withDescription("foo")
					.withWorkspace("lock"));
			fail("cloned with bad params");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getLocalizedMessage(),
					is("The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams().withWorkspace("lock")
				.withNewPermission("r"));
		checkWS(CLIENT1.getWorkspaceInfo(wsi), wsid, info.getE4(), "lock",
				USER1, 1, "a", "r", "published", null);
	}
	
	@Test
	public void renameObject() throws Exception {
		Tuple8<Long, String, String, String, Long, String, String, String> wsinfo =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("renameObj"));
		long wsid = wsinfo.getE1();
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("renameObj")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withType(SAFE_TYPE).withName("myname")));
		CLIENT1.saveObjects(soc);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> info =
				CLIENT1.renameObject(new RenameObjectParams().withNewName("mynewname")
				.withObj(new ObjectIdentity().withRef("renameObj/1")));
		checkInfo(info, 1, "mynewname", SAFE_TYPE, 1, USER1, wsid, "renameObj", "99914b932bd37a50b983c5e7c90ae93b", 2, null);
		info = CLIENT1.getObjectInfo(Arrays.asList(new ObjectIdentity().withWorkspace("renameObj")
				.withObjid(1L)), 0L).get(0);
		checkInfo(info, 1, "mynewname", SAFE_TYPE, 1, USER1, wsid, "renameObj", "99914b932bd37a50b983c5e7c90ae93b", 2, null);
		RenameObjectParams rop = new RenameObjectParams().withNewName("mynewname2")
				.withObj(new ObjectIdentity().withRef("renameObj/1"));
		rop.setAdditionalProperties("foo", "bar");
		failObjRename(rop, "Unexpected arguments in RenameObjectParams: foo");
		failObjRename(new RenameObjectParams().withNewName("foo")
				.withObj(new ObjectIdentity().withName("foo")),
				"Must provide one and only one of workspace name (was: null) or id (was: null)");
	}

	private void failObjRename(RenameObjectParams rop,
			String excep) throws Exception {
		try {
			CLIENT1.renameObject(rop);
			fail("renamed with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(excep));
		}
	}
	
	@Test
	public void renameWorkspace() throws Exception {
		Tuple8<Long, String, String, String, Long, String, String, String> wsinfo =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("renameWS"));
		Tuple8<Long, String, String, String, Long, String, String, String> wsinfo2 =
				CLIENT1.renameWorkspace(new RenameWorkspaceParams().withWsi(
				new WorkspaceIdentity().withWorkspace("renameWS")).withNewName("newrenameWS"));
		checkWS(wsinfo2, wsinfo.getE1(), wsinfo2.getE4(), "newrenameWS", USER1,
				0, "a", "n", "unlocked", null);
		wsinfo2 = CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("newrenameWS"));
		checkWS(wsinfo2, wsinfo.getE1(), wsinfo2.getE4(), "newrenameWS", USER1,
				0, "a", "n", "unlocked", null);
		RenameWorkspaceParams rwp = new RenameWorkspaceParams()
				.withWsi(new WorkspaceIdentity().withWorkspace("newrenameWS"))
				.withNewName("foo");
		rwp.setAdditionalProperties("foo", "bar");
		failWSRename(rwp, "Unexpected arguments in RenameWorkspaceParams: foo");
		failWSRename(new RenameWorkspaceParams().withWsi(new WorkspaceIdentity()
				.withWorkspace("newrenameWS")), "Workspace name cannot be null or the empty string");
	}

	private void failWSRename(RenameWorkspaceParams rwp,
			String excep) throws Exception {
		try {
			CLIENT1.renameWorkspace(rwp);
			fail("renamed with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(excep));
		}
	}
	
	@Test
	public void setGlobalPermission() throws Exception {
		Tuple8<Long, String, String, String, Long, String, String, String> wsinfo =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("setglobal"));
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("setglobal");
		assertThat("globalread is none", wsinfo.getE7(), is("n"));
		try {
			CLIENT2.getWorkspaceDescription(wsi);
			fail("got workspace desc w/o access");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("User " + USER2 + " may not read workspace setglobal"));
		}
		
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams().withWorkspace("setglobal")
				.withNewPermission("r"));
		CLIENT2.getWorkspaceDescription(wsi);
		assertThat("globalread is r", CLIENT1.getWorkspaceInfo(wsi).getE7(), is("r"));
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams().withWorkspace("setglobal")
				.withNewPermission("n"));
		assertThat("globalread is r", CLIENT1.getWorkspaceInfo(wsi).getE7(), is("n"));
		SetGlobalPermissionsParams sgpp = new SetGlobalPermissionsParams()
				.withWorkspace("setglobal").withNewPermission("r");
		sgpp.setAdditionalProperties("bar", "foo");
		failSetGlobalPerm(sgpp, "Unexpected arguments in SetGlobalPermissionsParams: bar");
		SetGlobalPermissionsParams sgppgen = new SetGlobalPermissionsParams()
				.withWorkspace("setglobal");
		failSetGlobalPerm(sgppgen.withNewPermission("w"),
				"Global permissions cannot be greater than read");
		failSetGlobalPerm(sgppgen.withNewPermission("z"),
				"No such permission: z");
		failSetGlobalPerm(sgppgen.withNewPermission("r").withId(wsinfo.getE1()),
				"Must provide one and only one of workspace name (was: setglobal) or id (was: " +
				wsinfo.getE1() + ")");
	
	}

	private void failSetGlobalPerm(SetGlobalPermissionsParams sgpp,
			String exp) throws Exception {
		try {
			CLIENT1.setGlobalPermission(sgpp);
			fail("set global perms with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}
	
	@Test
	public void hiddenObjects() throws Exception {
		Tuple8<Long, String, String, String, Long, String, String, String> wsinfo =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("hideObj"));
		long wsid = wsinfo.getE1();
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("hideObj")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withType(SAFE_TYPE).withName("unhidden")));
		ObjectIdentity o1 = new ObjectIdentity().withRef("hideObj/1");
		CLIENT1.saveObjects(soc);
		soc = new SaveObjectsParams().withWorkspace("hideObj")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withType(SAFE_TYPE).withName("hidden").withHidden(1L)));
		ObjectIdentity o2 = new ObjectIdentity().withWorkspace("hideObj").withName("hidden");
		CLIENT1.saveObjects(soc);

		Set<Long> expected = new HashSet<Long>();
		expected.add(1L);
		checkExpectedObjNums(CLIENT1.listObjects(new ListObjectsParams().withIds(Arrays.asList(wsid))), expected);
		expected.add(2L);
		checkExpectedObjNums(CLIENT1.listObjects(new ListObjectsParams().withIds(Arrays.asList(wsid)).withShowHidden(1L)), expected);
		CLIENT1.unhideObjects(Arrays.asList(o2));
		checkExpectedObjNums(CLIENT1.listObjects(new ListObjectsParams().withIds(Arrays.asList(wsid))), expected);
		CLIENT1.hideObjects(Arrays.asList(o1));
		expected.remove(1L);
		checkExpectedObjNums(CLIENT1.listObjects(new ListObjectsParams().withIds(Arrays.asList(wsid))), expected);
		expected.add(1L);
		checkExpectedObjNums(CLIENT1.listObjects(new ListObjectsParams().withIds(Arrays.asList(wsid)).withShowHidden(1L)), expected);
		
		ObjectIdentity badoi = new ObjectIdentity().withWorkspace("hideObj").withName("hidden");
		badoi.setAdditionalProperties("urg", "bleah");
		
		failHideUnHide(badoi, "Error on ObjectIdentity #1: Unexpected arguments in ObjectIdentity: urg");
		failHideUnHide(new ObjectIdentity().withWorkspace("hideObj"),
				"Error on ObjectIdentity #1: Must provide one and only one of object name (was: null) or id (was: null)");
		failHideUnHide(new ObjectIdentity().withWorkspace("hideObj").withName("wootwoot"),
				"No object with name wootwoot exists in workspace " + wsid);
		
	}

	private void failHideUnHide(ObjectIdentity badoi, String exp)
			throws Exception {
		try {
			CLIENT1.hideObjects(Arrays.asList(badoi));
			fail("hide obj with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
		try {
			CLIENT1.unhideObjects(Arrays.asList(badoi));
			fail("unhide obj with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}

	private void checkExpectedObjNums(
			List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> objs,
			Set<Long> expected) {
		Set<Long> got = new HashSet<Long>();
		for (Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> o: objs) {
			if (got.contains(o.getE1())) {
				fail("Say same object twice");
			}
			got.add(o.getE1());
		}
		assertThat("correct object ids in list", got, is(expected));
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
	public void testGetInfo() throws Exception {
		WorkspaceClient cl = new WorkspaceClient(new URL("http://localhost:" + 
				SERVER2.getServerPort()));
		String module = "UnreleasedModule";
		try {
			cl.getModuleInfo(new GetModuleInfoParams().withMod(module));
			Assert.fail();
		} catch (Exception ex) {
			Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("Module wasn't uploaded: UnreleasedModule"));
		}
		cl = new WorkspaceClient(new URL("http://localhost:" + 
				SERVER2.getServerPort()), AUTH_USER2.getToken());
		cl.setAuthAllowedForHttp(true);
		Assert.assertTrue(new HashSet<String>(cl.listModules(new ListModulesParams().withOwner(USER2))).contains("UnreleasedModule"));
		Assert.assertEquals(0L, (long)cl.getModuleInfo(new GetModuleInfoParams().withMod(module)).getIsReleased());
		Assert.assertEquals(1, cl.listModuleVersions(new ListModuleVersionsParams().withMod(module)).getVers().size());
		cl.releaseModule("UnreleasedModule");
		TypeInfo ti = cl.getTypeInfo("UnreleasedModule.AType");  //-0.1");
		System.out.println(ti.getUsingFuncDefs());
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
				CLIENT1.registerTypespecCopy(new RegisterTypespecCopyParams()
					.withExternalWorkspaceUrl(urlForSrv2).withMod("DepModule")
					.withVersion(ver));
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
		CLIENT1.registerTypespec(new RegisterTypespecParams()
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
