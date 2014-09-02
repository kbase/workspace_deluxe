package us.kbase.workspace.test.scripts;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.DB;
import com.mongodb.MongoClient;

import us.kbase.abstracthandle.AbstractHandleClient;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthUser;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.test.TestException;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.common.test.controllers.mysql.MySQLController;
import us.kbase.common.test.controllers.shock.ShockController;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.test.JsonTokenStreamOCStat;
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspace.test.controllers.handle.HandleServiceController;

/*
 * These tests are specifically for testing the WS CLI written in perl.
 * The actual tests for scripts are written in perl and can be executed individually
 * against a WS server running on localhost on the default port set in deploy.cfg.
 * 
 * This class is designed to wrap all of these tests, instantiate a new temporary
 * test WS service, and run the script tests against this service.
 * 
 */
public class ScriptTestRunner {
	
	//TODO needs to run w/o dev container
	
	protected static WorkspaceClient CLIENT1 = null;
	protected static WorkspaceClient CLIENT2 = null;  // This client connects to SERVER1 as well
	protected static String USER1 = null;
	protected static String USER2 = null;
	protected static String USER3 = null;
	protected static AuthUser AUTH_USER1 = null;
	protected static AuthUser AUTH_USER2 = null;
	
	private static MySQLController MYSQL;
	private static MongoController MONGO;
	private static ShockController SHOCK;
	
	private static HandleServiceController HANDLE;
	private static WorkspaceServer SERVER;
	
	private static AbstractHandleClient HANDLE_CLIENT;
	
	final private static String TMP_FILE_SUBDIR = "tempForScriptTestRunner";
	
	static {
		JsonTokenStreamOCStat.register();
	}
	
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
	private static Map<String, String> getenv() throws NoSuchFieldException,
			SecurityException, IllegalArgumentException, IllegalAccessException {
		Map<String, String> unmodifiable = System.getenv();
		Class<?> cu = unmodifiable.getClass();
		Field m = cu.getDeclaredField("m");
		m.setAccessible(true);
		return (Map<String, String>) m.get(unmodifiable);
	}
	
	@Test
	public void runTestServerUp() {
		runCommandAndCheckForSuccess(new String[]{"bash","-c","perl test/scripts/test-server-up.t "+getTestURL()});
	}
	
	@Test
	public void runTestBasicResponses() {
		runCommandAndCheckForSuccess(new String[]{"bash","-c","perl test/scripts/test-basic-responses.t"});
	}
	
	
	
	@Test
	public void runTestScriptClientConfig() {
		runCommandAndCheckForSuccess(new String[]{"bash","-c","perl test/scripts/test-script-client-config.t "+getTestURL()});
	}
	
	@Test
	public void runTestTypeRegistering() {
		runCommandAndCheckForSuccess(new String[]{"bash","-c","perl test/scripts/test-type-registering.t "+getTestURL()});
	}

	
	
	private static String getTestURL() {
		int testport = SERVER.getServerPort();
		return "http://localhost:"+testport;
	}
	
	private void runCommandAndCheckForSuccess(String [] command) {
		System.out.println("==============");
		System.out.println("Executing test: '" + concatCmdArray(command) + "'");
		StringBuffer stderr = new StringBuffer();
		StringBuffer stdout = new StringBuffer();
		int exitValue = 1;
		Process p;
		try {
			p = Runtime.getRuntime().exec(command);
			p.waitFor();
			exitValue = p.exitValue();
			BufferedReader out_reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			BufferedReader err_reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));

			String line = "";
			while ((line = out_reader.readLine())!= null) { stderr.append("  STDERR: "+line + "\n"); }
			while ((line = err_reader.readLine())!= null) { stdout.append("  STDOUT: "+line + "\n"); }

		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if(exitValue==0) {
			System.out.println("   ...pass...");
		} else {
			System.out.println("   ...fail... (error code: "+exitValue+")");
			System.out.println(stdout);
			System.out.println(stderr);
		}
		
		assertTrue("Running test: "+concatCmdArray(command)+" returned error code "+exitValue, exitValue==0);
	}
	
	private static String concatCmdArray(String [] command) {
		StringBuilder concatCmd = new StringBuilder();
		for(int k=0; k<command.length; k++) {
			if(k!=0) { concatCmd.append(" "); }
			concatCmd.append(command[k]);
		}
		return concatCmd.toString();
	}
	
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		USER1 = System.getProperty("test.user1");
		USER2 = System.getProperty("test.user2");
		String u3 = System.getProperty("test.user3");
		if (USER1.equals(USER2)) {
			throw new TestException("All the test users must be unique: " + 
					StringUtils.join(Arrays.asList(USER1, USER2, u3), " "));
		}
		if (USER1.equals(u3)) {
			throw new TestException("All the test users must be unique: " + 
					StringUtils.join(Arrays.asList(USER1, USER2, u3), " "));
		}
		if (USER2.equals(u3)) {
			throw new TestException("All the test users must be unique: " + 
					StringUtils.join(Arrays.asList(USER1, USER2, u3), " "));
		}
		String p1 = System.getProperty("test.pwd1");
		String p2 = System.getProperty("test.pwd2");
		String p3 = System.getProperty("test.pwd3");
		
		try {
			AuthService.login(u3, p3);
		} catch (Exception e) {
			throw new TestException("Could not log in test user test.user3: " + u3, e);
		}
		
		WorkspaceTestCommon.stfuLoggers();
		
		String tempDir = Paths.get(WorkspaceTestCommon.getTempDir())
							.resolve(TMP_FILE_SUBDIR).toString();
		
		MONGO = new MongoController(WorkspaceTestCommon.getMongoExe(),
				Paths.get(tempDir));
		System.out.println("Using Mongo temp dir " + MONGO.getTempDir());
		final String mongohost = "localhost:" + MONGO.getServerPort();
		MongoClient mongoClient = new MongoClient(mongohost);

		SHOCK = new ShockController(
				WorkspaceTestCommon.getShockExe(),
				Paths.get(tempDir),
				u3,
				mongohost,
				"JSONRPCLayerHandleTest_ShockDB",
				"foo",
				"foo");
		System.out.println("Using Shock temp dir " + SHOCK.getTempDir());

		MYSQL = new MySQLController(
				WorkspaceTestCommon.getMySQLExe(),
				WorkspaceTestCommon.getMySQLInstallExe(),
				Paths.get(tempDir));
		System.out.println("Using MySQL temp dir " + MYSQL.getTempDir());
		
		HANDLE = new HandleServiceController(
				WorkspaceTestCommon.getPlackupExe(),
				WorkspaceTestCommon.getHandleServicePSGI(),
				WorkspaceTestCommon.getHandleManagerPSGI(),
				u3,
				MYSQL,
				"http://localhost:" + SHOCK.getServerPort(),
				u3,
				p3,
				WorkspaceTestCommon.getHandlePERL5LIB(),
				Paths.get(tempDir));
		System.out.println("Using Handle Service temp dir " +
				HANDLE.getTempDir());
		
		
		SERVER = startupWorkspaceServer(mongohost,
				mongoClient.getDB("JSONRPCLayerHandleTester"), 
				"JSONRPCLayerHandleTester_types",
				u3, p3);
		int port = SERVER.getServerPort();
		System.out.println("Started test workspace server on port " + port);
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
		CLIENT1.setIsInsecureHttpConnectionAllowed(true);
		CLIENT2.setIsInsecureHttpConnectionAllowed(true);
		
		HANDLE_CLIENT = new AbstractHandleClient(new URL("http://localhost:" +
				HANDLE.getHandleServerPort()), USER1, p1);
		HANDLE_CLIENT.setIsInsecureHttpConnectionAllowed(true);
		
	}
	
	private static WorkspaceServer startupWorkspaceServer(
			String mongohost,
			DB db,
			String typedb,
			String handleUser,
			String handlePwd)
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
		System.out.println("Created temporary config file: " +
				iniFile.getAbsolutePath());
		Ini ini = new Ini();
		Section ws = ini.add("Workspace");
		ws.add("mongodb-host", mongohost);
		ws.add("mongodb-database", db.getName());
		ws.add("backend-secret", "foo");
		ws.add("handle-service-url", "http://localhost:" +
				HANDLE.getHandleServerPort());
		ws.add("handle-manager-url", "http://localhost:" +
				HANDLE.getHandleManagerPort());
		ws.add("handle-manager-user", handleUser);
		ws.add("handle-manager-pwd", handlePwd);
		ws.add("ws-admin", USER2);
		ws.add("temp-dir", Paths.get(WorkspaceTestCommon.getTempDir())
				.resolve(TMP_FILE_SUBDIR));
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
			System.out.print("Killing workspace server... ");
			SERVER.stopServer();
			System.out.println("Done");
		}
		if (HANDLE != null) {
			System.out.print("Destroying handle service... ");
			HANDLE.destroy(WorkspaceTestCommon.getDeleteTempFiles());
			System.out.println("Done");
		}
		if (SHOCK != null) {
			System.out.print("Destroying shock service... ");
			SHOCK.destroy(WorkspaceTestCommon.getDeleteTempFiles());
			System.out.println("Done");
		}
		if (MONGO != null) {
			System.out.print("Destroying mongo test service... ");
			MONGO.destroy(WorkspaceTestCommon.getDeleteTempFiles());
			System.out.println("Done");
		}
		if (MYSQL != null) {
			System.out.print("Destroying mysql test service... ");
			MYSQL.destroy(WorkspaceTestCommon.getDeleteTempFiles());
			System.out.println("Done");
		}
	}
	
}
