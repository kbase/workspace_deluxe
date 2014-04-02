package us.kbase.workspace.test.scripts;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthUser;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple9;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.test.TestException;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.workspace.AlterWorkspaceMetadataParams;
import us.kbase.workspace.GetObjectInfoNewParams;
import us.kbase.workspace.ListObjectsParams;
import us.kbase.workspace.ListWorkspaceInfoParams;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.RenameObjectParams;
import us.kbase.workspace.RenameWorkspaceParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.SetWorkspaceDescriptionParams;
import us.kbase.workspace.SubObjectIdentity;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.test.JsonTokenStreamOCStat;
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspace.test.workspace.FakeObjectInfo;
import us.kbase.workspace.test.workspace.FakeResolvedWSID;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
	protected static WorkspaceServer SERVER2 = null;
	protected static WorkspaceClient CLIENT_FOR_SRV2 = null;  // This client connects to SERVER2
	protected static WorkspaceClient CLIENT_NO_AUTH = null;
	
	protected static SimpleDateFormat DATE_FORMAT =
			new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
	static {
		DATE_FORMAT.setLenient(false);
	}
	
	protected final static String TEXT256;
	static {
		String foo = "";
		for (int i = 0; i < 25; i++) {
			foo += "aaaaabbbbb";
		}
		foo += "aaaaaf";
		TEXT256 = foo;
	}
	protected final static String TEXT1000;
	static {
		String foo = "";
		for (int i = 0; i < 100; i++) {
			foo += "aaaaabbbbb";
		}
		TEXT1000 = foo;
	}
	
	static {
		JsonTokenStreamOCStat.register();
	}
	
	public static final String SAFE_TYPE = "SomeModule.AType-0.1";
	public static final String REF_TYPE ="RefSpec.Ref-0.1";
	
	public static final Map<String, String> MT_META =
			new HashMap<String, String>();
	
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
	public void test() {
		int testport = SERVER1.getServerPort();
		String testurl = "http://localhost:"+testport;
		
		StringBuffer stderr = new StringBuffer();
		StringBuffer stdout = new StringBuffer();

		Process p;
		try {
			p = Runtime.getRuntime().exec(new String[]{"bash","-c","perl test/scripts/test-server-up.t "+testurl});
			p.waitFor();
			int retValue = p.exitValue();
			System.out.println("ret value:" + retValue);
			BufferedReader out_reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			BufferedReader err_reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));

			String line = "";
			while ((line = out_reader.readLine())!= null) { stderr.append(line + "\n"); }
			while ((line = err_reader.readLine())!= null) { stdout.append(line + "\n"); }

		} catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("stdout:"+stdout.toString());
		System.out.println("stderr:"+stderr.toString());
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
		CLIENT_NO_AUTH = new WorkspaceClient(new URL("http://localhost:" + port));
		CLIENT1.setAuthAllowedForHttp(true);
		CLIENT2.setAuthAllowedForHttp(true);
		CLIENT_NO_AUTH.setAuthAllowedForHttp(true);
//		
//		//set up a basic type for test use that doesn't worry about type checking
//		CLIENT1.requestModuleOwnership("SomeModule");
//		administerCommand(CLIENT2, "approveModRequest", "module", "SomeModule");
//		CLIENT1.registerTypespec(new RegisterTypespecParams()
//			.withDryrun(0L)
//			.withSpec("module SomeModule {/* @optional thing */ typedef structure {string thing;} AType;};")
//			.withNewTypes(Arrays.asList("AType")));
//		CLIENT1.releaseModule("SomeModule");
//		
//		//set up a type with references
//		final String specParseRef =
//				"module RefSpec {" +
//					"/* @id ws */" +
//					"typedef string reference;" +
//					"typedef structure {" +
//						"reference ref;" +
//					"} Ref;" +
//				"};";
//		CLIENT1.requestModuleOwnership("RefSpec");
//		administerCommand(CLIENT2, "approveModRequest", "module", "RefSpec");
//		CLIENT1.registerTypespec(new RegisterTypespecParams()
//			.withDryrun(0L)
//			.withSpec(specParseRef)
//			.withNewTypes(Arrays.asList("Ref")));
//		
//		SERVER2 = startupWorkspaceServer(2);
//		System.out.println("Started test server 2 on port " + SERVER2.getServerPort());
//		WorkspaceClient clientForSrv2 = new WorkspaceClient(new URL("http://localhost:" + 
//				SERVER2.getServerPort()), USER2, p2);
//		clientForSrv2.setAuthAllowedForHttp(true);
//		clientForSrv2.requestModuleOwnership("SomeModule");
//		administerCommand(clientForSrv2, "approveModRequest", "module", "SomeModule");
//		clientForSrv2.registerTypespec(new RegisterTypespecParams()
//			.withDryrun(0L)
//			.withSpec("module SomeModule {/* @optional thing */ typedef structure {int thing;} AType;};")
//			.withNewTypes(Arrays.asList("AType")));
//		clientForSrv2.releaseModule("SomeModule");
//		clientForSrv2.requestModuleOwnership("DepModule");
//		administerCommand(clientForSrv2, "approveModRequest", "module", "DepModule");
//		clientForSrv2.registerTypespec(new RegisterTypespecParams()
//			.withDryrun(0L)
//			.withSpec("#include <SomeModule>\n" +
//					"module DepModule {typedef structure {SomeModule.AType thing;} BType;};")
//			.withNewTypes(Arrays.asList("BType")));
//		clientForSrv2.releaseModule("DepModule");
//		clientForSrv2.registerTypespec(new RegisterTypespecParams()
//			.withDryrun(0L)
//			.withSpec("module SomeModule {/* @optional thing */ typedef structure {string thing;} AType;};")
//			.withNewTypes(Collections.<String>emptyList()));
//		clientForSrv2.releaseModule("SomeModule");
//		clientForSrv2.registerTypespec(new RegisterTypespecParams()
//			.withDryrun(0L)
//			.withSpec("#include <SomeModule>\n" +
//					"module DepModule {typedef structure {SomeModule.AType thing;} BType;};")
//			.withNewTypes(Collections.<String>emptyList()));
//		clientForSrv2.releaseModule("DepModule");
//		clientForSrv2.requestModuleOwnership("UnreleasedModule");
//		administerCommand(clientForSrv2, "approveModRequest", "module", "UnreleasedModule");
//		clientForSrv2.registerTypespec(new RegisterTypespecParams()
//			.withDryrun(0L)
//			.withSpec("module UnreleasedModule {typedef int AType; funcdef aFunc(AType param) returns ();};")
//			.withNewTypes(Arrays.asList("AType")));
//		CLIENT_FOR_SRV2 = clientForSrv2;
		System.out.println("Starting tests");
	}

	protected static void administerCommand(WorkspaceClient client, String command, String... params) throws IOException,
			JsonClientException {
		Map<String, String> releasemod = new HashMap<String, String>();
		releasemod.put("command", command);
		for (int i = 0; i < params.length / 2; i++)
			releasemod.put(params[i * 2], params[i * 2 + 1]);
		client.administer(new UObject(releasemod));
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
		server.setMaxMemUseForReturningObjects(24); //as of 3/10/14 out of 64 objects this would force 15 to be written as temp files
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
		if (SERVER2 != null) {
			System.out.print("Killing server 2... ");
			SERVER2.stopServer();
			System.out.println("Done");
		}
		JsonTokenStreamOCStat.showStat();
	}
	
}
