package us.kbase.workspace.test.scripts;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthUser;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.test.TestException;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.test.JsonTokenStreamOCStat;
import us.kbase.workspace.test.WorkspaceTestCommon;

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
	
	protected static WorkspaceServer SERVER1 = null;
	protected static WorkspaceClient CLIENT1 = null;
	protected static WorkspaceClient CLIENT2 = null;  // This client connects to SERVER1 as well
	protected static String USER1 = null;
	protected static String USER2 = null;
	protected static String USER3 = null;
	protected static AuthUser AUTH_USER1 = null;
	protected static AuthUser AUTH_USER2 = null;
	
	static {
		JsonTokenStreamOCStat.register();
	}
	
	private static List<TempFilesManager> tfms =
			new LinkedList<TempFilesManager>();;
	
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
	public void runTests() {
		runCommandAndCheckForSuccess(new String[]{"bash","-c","perl test/scripts/test-server-up.t "+getTestURL()});
		runCommandAndCheckForSuccess(new String[]{"bash","-c","perl test/scripts/test-basic-responses.t"});
		runCommandAndCheckForSuccess(new String[]{"bash","-c","perl test/scripts/test-script-client-config.t "+getTestURL()});
		runCommandAndCheckForSuccess(new String[]{"bash","-c","perl test/scripts/test-type-registering.t "+getTestURL()});
	}

	private static String getTestURL() {
		int testport = SERVER1.getServerPort();
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
		CLIENT1.setIsInsecureHttpConnectionAllowed(true);
		CLIENT2.setIsInsecureHttpConnectionAllowed(true);
		System.out.println("Starting tests");
	}

	private static WorkspaceServer startupWorkspaceServer(int dbNum)
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
		ws.add("temp-dir", "tempForJSONRPCLayerTester");
		ini.store(iniFile);
		iniFile.deleteOnExit();
		
		//set up env
		Map<String, String> env = getenv();
		env.put("KB_DEPLOYMENT_CONFIG", iniFile.getAbsolutePath());
		env.put("KB_SERVICE_NAME", "Workspace");

		WorkspaceServer.clearConfigForTests();
		WorkspaceServer server = new WorkspaceServer();
		tfms.add(server.getTempFilesManager());
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
		JsonTokenStreamOCStat.showStat();
	}
	
}
