package us.kbase.workspace.test.kbase;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
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

import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.test.TestException;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspace.test.controllers.mongo.MongoController;
import us.kbase.workspace.test.controllers.mysql.MySQLController;
import us.kbase.workspace.test.controllers.shock.ShockController;

public class JSONRPCLayerHandleTest {

	private static final boolean DELETE_TEMP_DIRS_ON_EXIT = false;
	
	private static MySQLController mysql;
	private static MongoController mongo;
	private static ShockController shock;
	
	private static String USER1;
	private static String USER2;
	private static WorkspaceClient CLIENT1;
	private static WorkspaceClient CLIENT2;

	private static WorkspaceServer SERVER;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		USER1 = System.getProperty("test.user1");
		USER2 = System.getProperty("test.user2");
		if (USER1.equals(USER2)) {
			throw new TestException("All the test users must be unique: " + 
					StringUtils.join(Arrays.asList(USER1, USER2), " "));
		}
		String p1 = System.getProperty("test.pwd1");
		String p2 = System.getProperty("test.pwd2");
		
		WorkspaceTestCommon.stfuLoggers();
		
		mongo = new MongoController(WorkspaceTestCommon.getMongoExe(),
				Paths.get(WorkspaceTestCommon.getTempDir()),
				DELETE_TEMP_DIRS_ON_EXIT);
		final String mongohost = "localhost:" + mongo.getServerPort();
		MongoClient mongoClient = new MongoClient(mongohost);

		shock = new ShockController(
				WorkspaceTestCommon.getShockExe(),
				Paths.get(WorkspaceTestCommon.getTempDir()),
				"***---fakeuser---***",
				mongohost,
				"JSONRPCLayerHandleTest_ShockDB",
				"foo",
				"foo",
				DELETE_TEMP_DIRS_ON_EXIT);

		mysql = new MySQLController(
				WorkspaceTestCommon.getMySQLExe(),
				WorkspaceTestCommon.getMySQLInstallExe(),
				Paths.get(WorkspaceTestCommon.getTempDir()),
				DELETE_TEMP_DIRS_ON_EXIT);
		setUpHandleServiceMySQLTables(mysql.getClient());
		
		SERVER = startupWorkspaceServer(mongohost,
				mongoClient.getDB("JSONRPCLayerHandleTester"), 
				"JSONRPCLayerHandleTester_types");
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
	}

	private static void setUpHandleServiceMySQLTables(Connection connection)
			throws Exception {
		Statement s = connection.createStatement();
		s.execute("CREATE DATABASE hsi;");
	}

	private static WorkspaceServer startupWorkspaceServer(String mongohost,
			DB db, String typedb)
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
		ws.add("temp-dir", Paths.get(WorkspaceTestCommon.getTempDir()).resolve("tempForJSONRPCLayerTester"));
		//TODO needs handle service url
		ini.store(iniFile);
		iniFile.deleteOnExit();

		//set up env
		Map<String, String> env = JSONRPCLayerTester.getenv();
		env.put("KB_DEPLOYMENT_CONFIG", iniFile.getAbsolutePath());
		env.put("KB_SERVICE_NAME", "Workspace");

		WorkspaceServer.setIgnoreHandleServiceForTests(true); //TODO remove
		WorkspaceServer.clearConfigForTests();
		WorkspaceServer server = new WorkspaceServer();
		new JSONRPCLayerTester.ServerThread(server).start();
		System.out.println("Main thread waiting for server to start up");
		while (server.getServerPort() == null) {
			Thread.sleep(1000);
		}
		return server;
	}

	@Test
	public void foo() throws Exception {
		System.out.println("foo");
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		if (SERVER != null) {
			System.out.print("Killing workspace server... ");
			SERVER.stopServer();
			System.out.println("Done");
		}
		if (shock != null) {
			System.out.println("destroying shock temp files");
			shock.destroy();
		}
		if (mongo != null) {
			System.out.println("destroying mongo temp files");
			mongo.destroy();
		}
		if (mysql != null) {
			System.out.println("destroying mysql temp files");
			mysql.destroy();
		}
	}
}
