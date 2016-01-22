package us.kbase.workspace.test.kbase;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
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

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthUser;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.service.JsonServerSyslog.SyslogOutput;
import us.kbase.common.test.TestException;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.WorkspaceClient;
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
	
	//TODO L go through all this crap and cut unnecessary stuff

	private static final String DB_WS_NAME = "LoggingTest";
	private static final String DB_TYPE_NAME = "LoggingTest_Types";

	private static final String ATYPE = "SomeModule.AType";
	private static final String BTYPE = "SomeModule.BType";
	private static String USER1;
	private static String USER2;
	private static MongoController mongo;
	private static WorkspaceServer SERVER;
	private static WorkspaceClient CLIENT1;
	private static WorkspaceClient CLIENT2;
	private static AuthUser AUTH_USER1;
	private static AuthUser AUTH_USER2;
	private static WorkspaceClient CLIENT_NO_AUTH;
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

		CLIENT_NO_AUTH = new WorkspaceClient(new URL("http://localhost:" + port));
		CLIENT1.setIsInsecureHttpConnectionAllowed(true);
		CLIENT2.setIsInsecureHttpConnectionAllowed(true);
		CLIENT_NO_AUTH.setIsInsecureHttpConnectionAllowed(true);
		CLIENT1.setStreamingModeOn(true); //for JSONRPCLayerLongTest
		
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
					"};"
					)
			.withNewTypes(Arrays.asList("AType", "BType")));
		CLIENT1.releaseModule("SomeModule");
	}
	
	//TODO move to common, used everywhere
	//http://quirkygba.blogspot.com/2009/11/setting-environment-variables-in-java.html
	@SuppressWarnings("unchecked")
	protected static Map<String, String> getenv() throws NoSuchFieldException,
			SecurityException, IllegalArgumentException, IllegalAccessException {
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
			throws InvalidHostException, UnknownHostException, IOException,
			NoSuchFieldException, IllegalAccessException, Exception,
			InterruptedException {
		WorkspaceTestCommon.initializeGridFSWorkspaceDB(db, typedb);
		
		//write the server config file:
		File iniFile = File.createTempFile("test", ".cfg",
				new File(WorkspaceTestCommon.getTempDir()));
		if (iniFile.exists()) {
			iniFile.delete();
		}
		System.out.println("Created temporary config file: " + iniFile.getAbsolutePath());
		Ini ini = new Ini();
		Section ws = ini.add("Workspace");
		ws.add("mongodb-host", mongohost);
		ws.add("mongodb-database", db.getName());
		ws.add("backend-secret", "foo");
		ws.add("ws-admin", USER2);
		ws.add("kbase-admin-user", USER1);
		ws.add("kbase-admin-pwd", user1Password);
		ws.add("temp-dir", Paths.get(WorkspaceTestCommon.getTempDir()).resolve("tempForJSONRPCLayerTester"));
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
		public String ip;
		public String method;
		public String url;
		public String fullMessage;
		public String serviceName = "TestDocServer";

		public ExpectedLog(int level, String ip, String method) {
			this.level = level;
			this.ip = ip;
			this.method = method;
		}
		
		public ExpectedLog withURL(String url) {
			this.url = url;
			return this;
		}
		
		public ExpectedLog withFullMsg(String msg) {
			this.fullMessage = msg;
			return this;
		}
		
		public ExpectedLog withServiceName(String name) {
			this.serviceName = name;
			return this;
		}
	}

	@Test
	public void saveObject() throws Exception {
		String ws = "myws";
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace(ws));
		logout.reset();
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
		for (LogEvent l: logout.events) {
			System.out.println(l);
		}
	}
}
