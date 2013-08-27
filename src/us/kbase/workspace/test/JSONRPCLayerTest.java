package us.kbase.workspace.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import static us.kbase.workspace.test.RegexMatcher.matches;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;

import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

import us.kbase.Tuple6;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.WorkspaceServer;

/*
 * These tests are specifically for testing the JSON-RPC communications between
 * the client, up to the invocation of the {@link us.kbase.workspace.workspaces.Workspaces}
 * methods. As such they do not test the full functionality of the Workspaces methods;
 * {@link us.kbase.workspace.workspaces.test.TestWorkspaces} handles that. This means
 * that only one backend (the simplest gridFS backend) is tested here, while TestWorkspaces
 * tests all backends and {@link us.kbase.workspace.database.Database} implementations.
 */
public class JSONRPCLayerTest {
	
	public static final String M_USER = "test.mongo.user";
	public static final String M_PWD = "test.mongo.pwd";
	public static File INI_FILE;
	
	private static WorkspaceServer SERVER = null;
	private static ServerThread SERV_THREAD = null;
	private static WorkspaceClient CLIENT1 = null;
	private static String USER1 = null;
	private static WorkspaceClient CLIENT2 = null;
	private static String USER2 = null;
	private static WorkspaceClient CLIENT_NO_AUTH = null;
	
	private static class ServerThread extends Thread {
		
		public void run() {
			try {
				SERVER.startupServer(20000);
			} catch (InterruptedException ie) {
				System.out.println("I'm melting! I'm melting!");
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
		//TODO deal with all this common code
		USER1 = System.getProperty("test.user1");
		USER2 = System.getProperty("test.user2");
		String p1 = System.getProperty("test.pwd1");
		String p2 = System.getProperty("test.pwd2");
		String host = System.getProperty("test.mongo.host");
		String db = System.getProperty("test.mongo.db");
		String mUser = System.getProperty(M_USER);
		String mPwd = System.getProperty(M_PWD);

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
		DB mdb = new MongoClient(host).getDB(db);
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
		
		//write the server config file:
		INI_FILE = File.createTempFile("test", ".cfg", new File("./"));
		INI_FILE.deleteOnExit();
		System.out.println("Created temporary config file: " + INI_FILE.getAbsolutePath());
		Ini ini = new Ini();
		Section ws = ini.add("Workspace");
		ws.add("mongodb-host", host);
		ws.add("mongodb-database", db);
		ws.add("mongodb-user", mUser);
		ws.add("mongodb-pwd", mPwd);
		ws.add("backend-secret", "");
		ini.store(INI_FILE);
		
		//set up env
		Map<String, String> env = getenv();
		env.put("KB_DEPLOYMENT_CONFIG", INI_FILE.getAbsolutePath());
		env.put("KB_SERVICE_NAME", "Workspace");
		

		//TODO add method to use automatic port
		SERVER = new WorkspaceServer();
		SERV_THREAD = new ServerThread();
		SERV_THREAD.start();
		//TODO poll server to see if it's up - need access to Server instance
		System.out.println("Main thread waiting 20 s for server to start up");
//		while(!server.jettyServerStarted) {
//			System.out.println(1);
//			Thread.sleep(1000);
//		}
		Thread.sleep(20000);
		System.out.println("Started test server on port " + 20000);
		System.out.println("Starting tests");
		CLIENT1 = new WorkspaceClient(new URL("http://localhost:20000"), USER1, p1);
		CLIENT2 = new WorkspaceClient(new URL("http://localhost:20000"), USER2, p2);
		CLIENT_NO_AUTH = new WorkspaceClient(new URL("http://localhost:20000"));
		CLIENT1.setAuthAllowedForHttp(true);
		CLIENT2.setAuthAllowedForHttp(true);
		CLIENT_NO_AUTH.setAuthAllowedForHttp(true);
	}
	
	@AfterClass
	public static void tearDownClass() {
		System.out.println("Killing server");
		//TODO shutdown server by using shutdown method - need access to Server instance
		SERV_THREAD.interrupt();
		System.out.println("Done");
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
	
	@SuppressWarnings("unchecked")
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
		} catch (Exception e) {
			//TODO needs fixing once error handling in java is figured out
			assertThat("correct exception message", e.getLocalizedMessage(),
					matches("JSONRPC error received: \\{name=JSONRPCError, code=-32500, message=Error while executing method Workspace.create_workspace \\(us.kbase.workspace.WorkspaceServer:\\d+ - globalread must be n or r\\)\\}"));
		}
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("gl_fail").withGlobalread("a"));
			fail("call succeeded w/ illegal global read param");
		} catch (Exception e) {
			//TODO needs fixing once error handling in java is figured out
			assertThat("correct exception message", e.getLocalizedMessage(),
					matches("JSONRPC error received: \\{name=JSONRPCError, code=-32500, message=Error while executing method Workspace.create_workspace \\(us.kbase.workspace.WorkspaceServer:\\d+ - globalread must be n or r\\)\\}"));
		}
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("gl_fail").withGlobalread("b"));
			fail("call succeeded w/ illegal global read param");
		} catch (Exception e) {
			//TODO needs fixing once error handling in java is figured out
			assertThat("correct exception message", e.getLocalizedMessage(),
					matches("JSONRPC error received: \\{name=JSONRPCError, code=-32500, message=Error while executing method Workspace.create_workspace \\(us.kbase.workspace.WorkspaceServer:\\d+ - globalread must be n or r\\)\\}"));
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

}
