package us.kbase.workspace.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
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
import us.kbase.workspace.workspaces.WorkspaceMetaData;

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
	
	private static WorkspaceServer server = null;
	private static ServerThread sthread = null;
	private static WorkspaceClient client1 = null;
	private static WorkspaceClient client2 = null;
	
	private static class ServerThread extends Thread {
		
		public void run() {
			try {
				server.startupServer(20000);
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
		Map<String, String> unomdifiable = System.getenv();
		Class<?> cu = unomdifiable.getClass();
		Field m = cu.getDeclaredField("m");
		m.setAccessible(true);
		return (Map<String, String>) m.get(unomdifiable);
	}
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		//TODO deal with all this common code
		String u1 = System.getProperty("test.user1");
		String u2 = System.getProperty("test.user2");
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
		server = new WorkspaceServer();
		sthread = new ServerThread();
		sthread.start();
		//TODO poll server to see if it's up - need access to Server instance
		System.out.println("Main thread waiting 20 s for server to start up");
//		while(!server.jettyServerStarted) {
//			System.out.println(1);
//			Thread.sleep(1000);
//		}
		Thread.sleep(20000);
		System.out.println("Started test server on port " + 20000);
		System.out.println("Starting tests");
		client1 = new WorkspaceClient(new URL("http://localhost:20000"), u1, p1);
		client2 = new WorkspaceClient(new URL("http://localhost:20000"), u2, p2);
		client1.setAuthAllowedForHttp(true);
		client2.setAuthAllowedForHttp(true);
	}
	
	@AfterClass
	public static void tearDownClass() {
		System.out.println("Killing server");
		//TODO shutdown server by using shutdown method - need access to Server instance
		sthread.interrupt();
		System.out.println("Done");
	}
	
	@Test
	public void createWSandgetMetaData() throws Exception {
		Tuple6<Integer, String, String, String, String, String> meta =
				client1.createWorkspace(new CreateWorkspaceParams()
					.withWorkspace("foo")
					.withGlobalread("r")
					.withDescription("boogabooga"));
		Tuple6<Integer, String, String, String, String, String> metaget =
				client1.getWorkspaceMetadata(new WorkspaceIdentity()
						.withWorkspace("foo"));
		assertThat("ids are equal", meta.getE1(), is(metaget.getE1()));
		
	}

}
