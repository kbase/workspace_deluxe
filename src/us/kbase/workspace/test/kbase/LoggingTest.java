package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.productivity.java.syslog4j.SyslogIF;

import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.JsonServerSyslog;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.service.JsonServerSyslog.SyslogOutput;
import us.kbase.common.test.TestException;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.workspace.CopyObjectParams;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GetObjectInfoNewParams;
import us.kbase.workspace.ListWorkspaceInfoParams;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.RenameObjectParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.SubObjectIdentity;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspace.test.kbase.JSONRPCLayerTester.ServerThread;

import com.mongodb.DB;
import com.mongodb.MongoClient;

/** Tests application logging only - not the standard logging that comes with
 * JsonServerServlet.
 * @author gaprice@lbl.gov
 *
 */
public class LoggingTest {
	
	//TODO it'd be nice if JsonServerServlet integrated with slf4j

	private static final String DB_WS_NAME = "LoggingTest";
	private static final String DB_TYPE_NAME = "LoggingTest_Types";
	
	private static int INFO = JsonServerSyslog.LOG_LEVEL_INFO;
//	private static int ERR = JsonServerSyslog.LOG_LEVEL_ERR;

	private static final String ATYPE = "SomeModule.AType";
	private static final String BTYPE = "SomeModule.BType";
	private static final String REFTYPE = "SomeModule.RefType";
	
	private static String USER1;
	private static String USER2;
	private static MongoController mongo;
	private static WorkspaceServer SERVER;
	private static WorkspaceClient CLIENT1;
	private static WorkspaceClient CLIENT2;
	private static SysLogOutputMock logout;

	@BeforeClass
	public static void setUpClass() throws Exception {
		logout = new SysLogOutputMock();
		
		USER1 = System.getProperty("test.user1");
		USER2 = System.getProperty("test.user2");
		if (USER1.equals(USER2)) {
			throw new TestException("All the test users must be unique: " + 
					StringUtils.join(Arrays.asList(USER1, USER2), " "));
		}
		String p1 = System.getProperty("test.pwd1");
		String p2 = System.getProperty("test.pwd2");
		
//		WorkspaceTestCommon.stfuLoggers();
		mongo = new MongoController(WorkspaceTestCommon.getMongoExe(),
				Paths.get(WorkspaceTestCommon.getTempDir()),
				WorkspaceTestCommon.useWiredTigerEngine());
		System.out.println("Using mongo temp dir " + mongo.getTempDir());
		
		final String mongohost = "localhost:" + mongo.getServerPort();
		MongoClient mongoClient = new MongoClient(mongohost);
		
		SERVER = startupWorkspaceServer(mongohost,
				mongoClient.getDB(DB_WS_NAME), 
				DB_TYPE_NAME, p1);
		SERVER.changeSyslogOutput(logout);
		int port = SERVER.getServerPort();
		System.out.println("Started test server 1 on port " + port);
		try {
			CLIENT1 = new WorkspaceClient(new URL("http://localhost:" + port),
					USER1, p1);
		} catch (UnauthorizedException ue) {
			throw new TestException("Unable to login with test.user1: " + USER1 +
					"\nPlease check the credentials in the test configuration.", ue);
		}
		try {
			CLIENT2 = new WorkspaceClient(new URL("http://localhost:" + port),
					USER2, p2);
		} catch (UnauthorizedException ue) {
			throw new TestException("Unable to login with test.user2: " + USER2 +
					"\nPlease check the credentials in the test configuration.", ue);
		}

		CLIENT1.setIsInsecureHttpConnectionAllowed(true);
		CLIENT2.setIsInsecureHttpConnectionAllowed(true);
		
		//set up a basic type for test use that doesn't worry about type checking
		CLIENT1.requestModuleOwnership("SomeModule");
		administerCommand(CLIENT2, "approveModRequest", "module", "SomeModule");
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec(
					"module SomeModule {" + 
						"/* @optional thing */" +
						"typedef structure {" +
							"string thing;" +
						"} AType;" +
						"/* @optional thing */" +
						"typedef structure {" +
							"string thing;" +
						"} BType;" +
						"/* @id ws */" +
						"typedef string ref;" +
						"typedef structure {" +
							"ref r;" +
						"} RefType;" +
					"};"
					)
			.withNewTypes(Arrays.asList("AType", "BType", "RefType")));
		CLIENT1.releaseModule("SomeModule");
	}
	
	//TODO move to common, used everywhere
	//http://quirkygba.blogspot.com/2009/11/setting-environment-variables-in-java.html
	@SuppressWarnings("unchecked")
	protected static Map<String, String> getenv() throws NoSuchFieldException,
			SecurityException, IllegalArgumentException,
			IllegalAccessException {
		Map<String, String> unmodifiable = System.getenv();
		Class<?> cu = unmodifiable.getClass();
		Field m = cu.getDeclaredField("m");
		m.setAccessible(true);
		return (Map<String, String>) m.get(unmodifiable);
	}

	public static void administerCommand(WorkspaceClient client,
			String command, String... params)
			throws IOException, JsonClientException {
		Map<String, String> releasemod = new HashMap<String, String>();
		releasemod.put("command", command);
		for (int i = 0; i < params.length / 2; i++)
			releasemod.put(params[i * 2], params[i * 2 + 1]);
		client.administer(new UObject(releasemod));
	}
	
	private static WorkspaceServer startupWorkspaceServer(String mongohost,
			DB db, String typedb, String user1Password)
			throws Exception {
		WorkspaceTestCommon.initializeGridFSWorkspaceDB(db, typedb);
		
		//write the server config file:
		File iniFile = File.createTempFile("test", ".cfg",
				new File(WorkspaceTestCommon.getTempDir()));
		if (iniFile.exists()) {
			iniFile.delete();
		}
		System.out.println("Created temporary config file: " +
				iniFile.getAbsolutePath());
		Ini ini = new Ini();
		Section ws = ini.add("Workspace");
		ws.add("mongodb-host", mongohost);
		ws.add("mongodb-database", db.getName());
		ws.add("backend-secret", "foo");
		ws.add("ws-admin", USER2);
		ws.add("kbase-admin-user", USER1);
		ws.add("kbase-admin-pwd", user1Password);
		ws.add("temp-dir", Paths.get(WorkspaceTestCommon.getTempDir())
				.resolve("tempForLoggingTest"));
		ws.add("ignore-handle-service", "true");
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
		if (SERVER != null) {
			System.out.print("Killing server... ");
			SERVER.stopServer();
			System.out.println("Done");
		}
		if (mongo != null) {
			System.out.println("destroying mongo temp files");
			mongo.destroy(WorkspaceTestCommon.deleteTempFiles());
		}
	}

	@Before
	public void clearDB() throws Exception {
		logout.reset();
		DB wsdb1 = GetMongoDB.getDB("localhost:" + mongo.getServerPort(),
				DB_WS_NAME);
		WorkspaceTestCommon.destroyDB(wsdb1);
	}
	
	private static class LogEvent {
		public int level;
		public String message;
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("LogEvent [level=");
			builder.append(level);
			builder.append(", message=");
			builder.append(message);
			builder.append("]");
			return builder.toString();
		}
	}
	
	private static class SysLogOutputMock extends SyslogOutput {
		
		public List<LogEvent> events = new LinkedList<LogEvent>(); 
		
		@Override
		public void logToSystem(SyslogIF log, int level, String message) {
			LogEvent e = new LogEvent();
			e.level = level;
			e.message = message;
			events.add(e);
		}
		
		@Override
		public PrintWriter logToFile(File f, PrintWriter pw, int level,
				String message) throws Exception {
			throw new UnsupportedOperationException();
		}
		
		public void reset() {
			events.clear();
		}
	}
	
	private static class ExpectedLog {
		
		public int level;
		public String method;
		public String message;
		public String user;
		boolean internal;

		public ExpectedLog(int level, String method, String message,
				String user, boolean internal) {
			this.level = level;
			this.method = method;
			this.message = message;
			this.user = user;
			this.internal = internal;
		}
	}
	
	private static class LogObjExp extends ExpectedLog {
		
		public LogObjExp(String method, String message, boolean internal) {
			super(INFO, method, message, USER1, internal);
		}
	}
	
	private void checkLogging(List<ExpectedLog> expected) throws Exception {
		checkLogging(expected, expected.size());
	}
	
	private void checkLogging(List<ExpectedLog> expected, int eventCount)
			throws Exception {
		assertThat("correct # of logging events", logout.events.size(),
				is(eventCount));
		Iterator<ExpectedLog> i = expected.iterator();
		Iterator<LogEvent> e = logout.events.iterator();
		String callID = null;
		while (i.hasNext()) {
			ExpectedLog exp = i.next();
			LogEvent got = e.next();
			assertThat("correct level", got.level, is(exp.level));
			String call = checkMessage(got.message, exp);
			if (callID == null) {
				callID = call;
			} else {
				assertThat("same call IDs for all calls", call, is(callID));
			}
		}
		
	}
	
	private String checkMessage(String message, ExpectedLog exp) {
//		System.out.println(message);
		String[] parts = message.split(":", 2);
		String[] headerParts = parts[0].split("]\\s*\\[");
		assertThat("server name correct", headerParts[0].substring(1),
				is("Workspace"));
		assertThat("record type correct", headerParts[1],
				is(exp.level == INFO ? "INFO" : "ERR"));
		double epochms = Double.valueOf(headerParts[2]) * 1000;
		long now = new Date().getTime();
		assertThat("log date < 5s ago", now - epochms < 5000, is(true));
		//3 is user running the service
		assertThat("caller correct", headerParts[4],
				is("us.kbase.workspace.WorkspaceServer" +
						(exp.internal ? "$1" : "")));
		//5 is pid
		assertThat("ip correct", headerParts[6], is("127.0.0.1"));
		assertThat("remote user correct", headerParts[7], is(exp.user));
		assertThat("module correct", headerParts[8], is("Workspace"));
		assertThat("method correct", headerParts[9], is(exp.method));
		String callID = headerParts[10].substring(
				0, headerParts[10].length() - 1);
		
		assertThat("full message correct", parts[1].trim(), is(exp.message));
		return callID;
	}
	

	@Test
	public void logObjects() throws Exception {
		/* test various methods that log the object ID and type when run
		 * Doesn't bother with deprecated fns 
		 */
		String ws = "myws";
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace(ws));
		logout.reset();
		
		// save objects
		List<ObjectSaveData> d = new LinkedList<ObjectSaveData>();
		for (String name: Arrays.asList("foo", "bar", "baz")) {
			d.add(new ObjectSaveData()
					.withData(new UObject(new HashMap<String, Object>()))
					.withName(name)
					.withType(name.equals("bar") ? BTYPE : ATYPE));
		}
		CLIENT1.saveObjects(new SaveObjectsParams()
				.withWorkspace(ws)
				.withObjects(d));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("save_objects", "start method", false),
				new LogObjExp("save_objects",
						"Object 1/1/1 SomeModule.AType-1.0", true),
				new LogObjExp("save_objects",
						"Object 1/2/1 SomeModule.BType-1.0", true),
				new LogObjExp("save_objects",
						"Object 1/3/1 SomeModule.AType-1.0", true),
				new LogObjExp("save_objects", "end method", false))));
		logout.reset();
		
		// rename
		CLIENT1.renameObject(new RenameObjectParams()
				.withNewName("bak")
				.withObj(new ObjectIdentity().withRef("1/1/1")));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("rename_object", "start method", false),
				new LogObjExp("rename_object",
						"Object 1/1/1 SomeModule.AType-1.0", true),
				new LogObjExp("rename_object", "end method", false))));
		logout.reset();
		
		// copy
		CLIENT1.copyObject(new CopyObjectParams()
				.withFrom(new ObjectIdentity().withRef("1/2"))
				.withTo(new ObjectIdentity().withRef("1/1")));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("copy_object", "start method", false),
				new LogObjExp("copy_object",
						"Object 1/1/2 SomeModule.BType-1.0", true),
				new LogObjExp("copy_object", "end method", false))));
		logout.reset();
		
		// revert
		CLIENT1.revertObject(new ObjectIdentity().withRef("1/1/1"));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("revert_object", "start method", false),
				new LogObjExp("revert_object",
						"Object 1/1/3 SomeModule.AType-1.0", true),
				new LogObjExp("revert_object", "end method", false))));
		logout.reset();
		
		// history
		CLIENT1.getObjectHistory(new ObjectIdentity().withRef("1/1"));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("get_object_history", "start method", false),
				new LogObjExp("get_object_history",
						"Object 1/1/1 SomeModule.AType-1.0", true),
				new LogObjExp("get_object_history",
						"Object 1/1/2 SomeModule.BType-1.0", true),
				new LogObjExp("get_object_history",
						"Object 1/1/3 SomeModule.AType-1.0", true),
				new LogObjExp("get_object_history", "end method", false))));
		logout.reset();
		
		// get info
		CLIENT1.getObjectInfoNew(new GetObjectInfoNewParams()
				.withObjects(Arrays.asList(
						new ObjectIdentity().withRef("1/1/2"),
						new ObjectIdentity().withRef("1/1/1"))));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("get_object_info_new", "start method", false),
				new LogObjExp("get_object_info_new",
						"Object 1/1/2 SomeModule.BType-1.0", true),
				new LogObjExp("get_object_info_new",
						"Object 1/1/1 SomeModule.AType-1.0", true),
				new LogObjExp("get_object_info_new", "end method", false))));
		logout.reset();
		
		// get objs
		CLIENT1.getObjects(Arrays.asList(
				new ObjectIdentity().withRef("1/1/2"),
				new ObjectIdentity().withRef("1/1/1")));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("get_objects", "start method", false),
				new LogObjExp("get_objects",
						"Object 1/1/2 SomeModule.BType-1.0", true),
				new LogObjExp("get_objects",
						"Object 1/1/1 SomeModule.AType-1.0", true),
				new LogObjExp("get_objects", "end method", false))));
		logout.reset();
		
		// get subobjs
		CLIENT1.getObjectSubset(Arrays.asList(
				new SubObjectIdentity().withIncluded(Arrays.asList("/"))
						.withRef("1/1/2"),
				new SubObjectIdentity().withIncluded(Arrays.asList("/"))
						.withRef("1/1/1")));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("get_object_subset", "start method", false),
				new LogObjExp("get_object_subset",
						"Object 1/1/2 SomeModule.BType-1.0", true),
				new LogObjExp("get_object_subset",
						"Object 1/1/1 SomeModule.AType-1.0", true),
				new LogObjExp("get_object_subset", "end method", false))));
		logout.reset();
		
		// get prov
		CLIENT1.getObjectProvenance(Arrays.asList(
				new ObjectIdentity().withRef("1/1/2"),
				new ObjectIdentity().withRef("1/1/1")));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("get_object_provenance", "start method", false),
				new LogObjExp("get_object_provenance",
						"Object 1/1/2 SomeModule.BType-1.0", true),
				new LogObjExp("get_object_provenance",
						"Object 1/1/1 SomeModule.AType-1.0", true),
				new LogObjExp("get_object_provenance", "end method", false))));
		logout.reset();
		
		// get ref'd objects
		Map<String, String> r = new HashMap<String, String>();
		r.put("r", "1/1/2");
		CLIENT1.saveObjects(new SaveObjectsParams()
				.withWorkspace(ws)
				.withObjects(Arrays.asList(
						new ObjectSaveData()
							.withData(new UObject(r))
							.withName("ref")
							.withType(REFTYPE))));
		logout.reset();
		CLIENT1.getReferencedObjects(Arrays.asList(Arrays.asList(
				new ObjectIdentity().withRef("1/ref/1"),
				new ObjectIdentity().withRef("1/1/2"))));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("get_referenced_objects", "start method", false),
				new LogObjExp("get_referenced_objects",
						"Object 1/1/2 SomeModule.BType-1.0", true),
				new LogObjExp("get_referenced_objects", "end method", false))));
		logout.reset();
										
	}

	private List<ExpectedLog> convertLogObjExp(List<LogObjExp> logobj) {
		List<ExpectedLog> e = new LinkedList<LoggingTest.ExpectedLog>();
		for (LogObjExp l: logobj) {
			e.add((ExpectedLog) l);
		}
		return e;
	}
	
	private static class AdminExp extends ExpectedLog {
		
		public AdminExp(String message, boolean internal) {
			super(INFO, "administer", message, USER2, internal);
		}
	}
	
	private List<ExpectedLog> convertAdminExp(List<AdminExp> logobj) {
		List<ExpectedLog> e = new LinkedList<LoggingTest.ExpectedLog>();
		for (AdminExp l: logobj) {
			e.add((ExpectedLog) l);
		}
		return e;
	}
	
	
	@Test
	public void administrators() throws Exception {
		Map<String, Object> ac = new HashMap<String, Object>();
		
		// add
		ac.put("command", "addAdmin");
		ac.put("user", USER1);
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("addAdmin " + USER1, true),
				new AdminExp("end method", false))));
		logout.reset();
		
		// remove
		ac.put("command", "removeAdmin");
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("removeAdmin " + USER1, true),
				new AdminExp("end method", false))));
		logout.reset();
		
		// list
		ac.put("command", "listAdmins");
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("listAdmins", true),
				new AdminExp("end method", false))));
		logout.reset();
		
	}
	
	@Test
	public void modules() throws Exception {
		Map<String, Object> ac = new HashMap<String, Object>();
		
		// list
		ac.put("command", "listModRequests");
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("listModRequests", true),
				new AdminExp("end method", false))));
		logout.reset();
		
		// approve
		CLIENT1.requestModuleOwnership("suckmaster");
		logout.reset();
		ac.put("command", "approveModRequest");
		ac.put("module", "suckmaster");
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("approveModRequest suckmaster", true),
				new AdminExp("end method", false))));
		logout.reset();
		
		// approve
		CLIENT1.requestModuleOwnership("burstingfoam");
		logout.reset();
		ac.put("command", "denyModRequest");
		ac.put("module", "burstingfoam");
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("denyModRequest burstingfoam", true),
				new AdminExp("end method", false))));
		logout.reset();
		
		// add owner
		ac.put("command", "grantModuleOwnership");
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("mod", "suckmaster");
		params.put("new_owner", USER2);
		ac.put("params", params);
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("grantModuleOwnership suckmaster " + USER2, true),
				new AdminExp("end method", false))));
		logout.reset();
		
		// remove owner
		ac.put("command", "removeModuleOwnership");
		params.put("mod", "suckmaster");
		params.put("old_owner", USER2);
		params.remove("new_owner");
		ac.put("params", params);
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("removeModuleOwnership suckmaster " + USER2, true),
				new AdminExp("end method", false))));
		logout.reset();
	}
	
	@Test
	public void workspaceSpecials() throws Exception {
		String ws = "myws";
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace(ws));
		logout.reset();
		
		Map<String, Object> ac = new HashMap<String, Object>();
		
		// set owner
		ac.put("command", "setWorkspaceOwner");
		Map<String, String> wsi = new HashMap<String, String>();
		wsi.put("workspace", "myws");
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("wsi", wsi);
		params.put("new_user", USER2);
		ac.put("params", params);
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("setWorkspaceOwner 1 " + USER2, true),
				new AdminExp("end method", false))));
		logout.reset();
		
		// list owners
		ac.put("command", "listWorkspaceOwners");
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("listWorkspaceOwners", true),
				new AdminExp("end method", false))));
		logout.reset();
		
	}
	
	@Test
	public void adminWorkspaceCommands() throws Exception {
		final String ws = "myws";
		Map<String, Object> ac = new HashMap<String, Object>();
		
		// create workspace
		ac.put("command", "createWorkspace");
		ac.put("user", USER1);
		ac.put("params", new CreateWorkspaceParams().withWorkspace(ws));
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("createWorkspace 1 " + USER1, true),
				new AdminExp("end method", false))));
		logout.reset();
		
		// set perms
		ac.put("command", "setPermissions");
		ac.remove("user");
		ac.put("params", new SetPermissionsParams().withWorkspace(ws)
				.withUsers(Arrays.asList(USER1, USER2))
				.withNewPermission("w"));
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("setPermissions null myws w " + USER1 + " " +
						USER2, true),
				new AdminExp("end method", false))));
		logout.reset();
		
		ac.put("params", new SetPermissionsParams().withId(1L)
				.withUsers(Arrays.asList(USER2))
				.withNewPermission("a"));
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("setPermissions 1 null a " + USER2, true),
				new AdminExp("end method", false))));
		logout.reset();
		
		// get perms
		ac.put("command", "getPermissions");
		ac.put("user", USER1);
		ac.put("params", new WorkspaceIdentity().withWorkspace(ws));
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("getPermissions null myws " + USER1, true),
				new AdminExp("end method", false))));
		logout.reset();
		
		ac.put("params", new WorkspaceIdentity().withId(1L));
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("getPermissions 1 null " + USER1, true),
				new AdminExp("end method", false))));
		logout.reset();
		
		// set global
		ac.put("command", "setGlobalPermission");
		ac.put("user", USER1);
		ac.put("params", new SetGlobalPermissionsParams().withWorkspace(ws)
				.withNewPermission("r"));
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("setGlobalPermission null myws r " + USER1, true),
				new AdminExp("end method", false))));
		logout.reset();
		
		ac.put("params", new SetGlobalPermissionsParams().withId(1L)
				.withNewPermission("n"));
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("setGlobalPermission 1 null n " + USER1, true),
				new AdminExp("end method", false))));
		logout.reset();
		
		// save objs
		ac.put("command", "saveObjects");
		ac.put("user", USER1);
		ac.put("params", new SaveObjectsParams().withWorkspace(ws)
				.withObjects(Arrays.asList(new ObjectSaveData()
						.withData(new UObject(new HashMap<String, String>()))
						.withName("foo")
						.withType(ATYPE))));
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("saveObjects " + USER1, true),
				new AdminExp("Object 1/1/1 SomeModule.AType-1.0", true),
				new AdminExp("end method", false))));
		logout.reset();
		
		// list ws
		ac.put("command", "listWorkspaces");
		ac.put("user", USER1);
		ac.put("params", new ListWorkspaceInfoParams());
		CLIENT2.administer(new UObject(ac));
		checkLogging(convertAdminExp(Arrays.asList(
				new AdminExp("start method", false),
				new AdminExp("listWorkspaces " + USER1, true),
				new AdminExp("end method", false))));
		logout.reset();
		
	}
}
