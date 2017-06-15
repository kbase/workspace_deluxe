package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static us.kbase.workspace.test.kbase.JSONRPCLayerTester.administerCommand;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.zafarkhaja.semver.Version;
import com.mongodb.DB;
import com.mongodb.MongoClient;

import us.kbase.abstracthandle.AbstractHandleClient;
import us.kbase.abstracthandle.Handle;
import us.kbase.auth.AuthConfig;
import us.kbase.auth.AuthToken;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.TestException;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.common.test.controllers.mysql.MySQLController;
import us.kbase.common.test.controllers.shock.ShockController;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockACL;
import us.kbase.shock.client.ShockACLType;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.ShockUserId;
import us.kbase.typedobj.idref.IdReference;
import us.kbase.typedobj.idref.IdReferenceHandlerSet;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.TooManyIdsException;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GetObjects2Params;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ObjectSpecification;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.kbase.HandleIdHandlerFactory;
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspace.test.controllers.handle.HandleServiceController;

public class HandleTest {

	private static MySQLController MYSQL;
	private static MongoController MONGO;
	private static ShockController SHOCK;
	private static HandleServiceController HANDLE;
	private static WorkspaceServer SERVER;
	
	private static String USER1;
	private static String USER2;
	private static ShockUserId SHOCK_USER1;
	private static ShockUserId SHOCK_USER2;
	
	private static WorkspaceClient CLIENT1;
	private static WorkspaceClient CLIENT2;
	private static WorkspaceClient CLIENT_NOAUTH;

	private static AbstractHandleClient HANDLE_CLIENT;
	
	private static ShockACLType READ_ACL = ShockACLType.READ;
	
	private static String HANDLE_TYPE = "HandleList.HList-0.1";
	private static String HANDLE_REF_TYPE = "HandleList.HRef-0.1";
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		final ConfigurableAuthService auth = new ConfigurableAuthService(
				new AuthConfig().withKBaseAuthServerURL(
						TestCommon.getAuthUrl()));
		final AuthToken t1 = TestCommon.getToken(1, auth);
		final AuthToken t2 = TestCommon.getToken(2, auth);
		final AuthToken t3 = TestCommon.getToken(3, auth);
		USER1 = t1.getUserName();
		USER2 = t2.getUserName();
		String u3 = t3.getUserName();
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
		TestCommon.stfuLoggers();
		
		MONGO = new MongoController(TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using Mongo temp dir " + MONGO.getTempDir());
		final String mongohost = "localhost:" + MONGO.getServerPort();
		MongoClient mongoClient = new MongoClient(mongohost);

		SHOCK = new ShockController(
				TestCommon.getShockExe(),
				TestCommon.getShockVersion(),
				Paths.get(TestCommon.getTempDir()),
				u3,
				mongohost,
				"JSONRPCLayerHandleTest_ShockDB",
				"foo",
				"foo",
				TestCommon.getGlobusUrl());
		System.out.println("Using globus url " + TestCommon.getGlobusUrl());
		System.out.println("Shock controller version: " + SHOCK.getVersion());
		if (SHOCK.getVersion() == null) {
			System.out.println(
					"Unregistered version - Shock may not start correctly");
		}
		System.out.println("Using Shock temp dir " + SHOCK.getTempDir());

		MYSQL = new MySQLController(
				TestCommon.getMySQLExe(),
				TestCommon.getMySQLInstallExe(),
				Paths.get(TestCommon.getTempDir()));
		System.out.println("Using MySQL temp dir " + MYSQL.getTempDir());
		
		HANDLE = new HandleServiceController(
				WorkspaceTestCommon.getPlackupExe(),
				WorkspaceTestCommon.getHandleServicePSGI(),
				WorkspaceTestCommon.getHandleManagerPSGI(),
				u3,
				MYSQL,
				"http://localhost:" + SHOCK.getServerPort(),
				t3,
				WorkspaceTestCommon.getHandlePERL5LIB(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.getAuthUrl());
		System.out.println("Using auth url " + TestCommon.getAuthUrl());
		System.out.println("Using Handle Service temp dir " + HANDLE.getTempDir());
		
		
		SERVER = startupWorkspaceServer(mongohost,
				mongoClient.getDB("JSONRPCLayerHandleTester"), 
				"JSONRPCLayerHandleTester_types", t3);
		int port = SERVER.getServerPort();
		System.out.println("Started test workspace server on port " + port);
		
		final URL url = new URL("http://localhost:" + port);
		try {
			CLIENT1 = new WorkspaceClient(url, t1);
		} catch (UnauthorizedException ue) {
			throw new TestException("Unable to login with test.user1: " + USER1 +
					"\nPlease check the credentials in the test configuration.", ue);
		}
		try {
			CLIENT2 = new WorkspaceClient(url, t2);
		} catch (UnauthorizedException ue) {
			throw new TestException("Unable to login with test.user2: " + USER2 +
					"\nPlease check the credentials in the test configuration.", ue);
		}
		CLIENT_NOAUTH = new WorkspaceClient(url);
		CLIENT1.setIsInsecureHttpConnectionAllowed(true);
		CLIENT2.setIsInsecureHttpConnectionAllowed(true);
		CLIENT_NOAUTH.setIsInsecureHttpConnectionAllowed(true);
		
		setUpSpecs();
		
		HANDLE_CLIENT = new AbstractHandleClient(new URL("http://localhost:" +
				HANDLE.getHandleServerPort()), t1);
		HANDLE_CLIENT.setIsInsecureHttpConnectionAllowed(true);
		
		BasicShockClient bsc = new BasicShockClient(new URL("http://localhost:"
				+ SHOCK.getServerPort()), CLIENT1.getToken());
		SHOCK_USER1 = bsc.addNode().getACLs().getOwner();
		bsc.updateToken(CLIENT2.getToken());
		SHOCK_USER2 = bsc.addNode().getACLs().getOwner();
	}

	private static void setUpSpecs() throws Exception {
		final String handlespec =
				"module HandleList {" +
					"/* @id handle */" +
					"typedef string handle;" +
					"typedef structure {" +
						"list<handle> handles;" +
					"} HList;" +
					"/* @id ws */" +
					"typedef string wsid;" +
					"typedef structure {" +
						"wsid id;" +
					"} HRef;" +
				"};";
		CLIENT1.requestModuleOwnership("HandleList");
		administerCommand(CLIENT2, "approveModRequest", "module", "HandleList");
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec(handlespec)
			.withNewTypes(Arrays.asList("HList", "HRef")));
	}
	
	private static WorkspaceServer startupWorkspaceServer(
			String mongohost,
			DB db,
			String typedb,
			AuthToken handleToken)
			throws InvalidHostException, UnknownHostException, IOException,
			NoSuchFieldException, IllegalAccessException, Exception,
			InterruptedException {
		
		WorkspaceTestCommon.initializeGridFSWorkspaceDB(db, typedb);

		//write the server config file:
		File iniFile = File.createTempFile("test", ".cfg",
				new File(TestCommon.getTempDir()));
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
		ws.add("auth-service-url", TestCommon.getAuthUrl());
		ws.add("globus-url", TestCommon.getGlobusUrl());
		ws.add("handle-service-url", "http://localhost:" +
				HANDLE.getHandleServerPort());
		ws.add("handle-manager-url", "http://localhost:" +
				HANDLE.getHandleManagerPort());
		ws.add("ws-admin", USER2);
		ws.add("handle-manager-token", handleToken.getToken());
		ws.add("temp-dir", Paths.get(TestCommon.getTempDir())
				.resolve("tempForJSONRPCLayerTester"));
		ini.store(iniFile);
		iniFile.deleteOnExit();

		//set up env
		Map<String, String> env = TestCommon.getenv();
		env.put("KB_DEPLOYMENT_CONFIG", iniFile.getAbsolutePath());
		env.put("KB_SERVICE_NAME", "Workspace");

		WorkspaceServer.clearConfigForTests();
		WorkspaceServer server = new WorkspaceServer();
		new JSONRPCLayerTester.ServerThread(server).start();
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
			HANDLE.destroy(TestCommon.getDeleteTempFiles());
		}
		if (SHOCK != null) {
			SHOCK.destroy(TestCommon.getDeleteTempFiles());
		}
		if (MONGO != null) {
			MONGO.destroy(TestCommon.getDeleteTempFiles());
		}
		if (MYSQL != null) {
			MYSQL.destroy(TestCommon.getDeleteTempFiles());
		}
	}

	@Test
	public void status() throws Exception {
		final Map<String, Object> st = CLIENT1.status();
		
		//top level items
		assertThat("incorrect state", st.get("state"), is((Object) "OK"));
		assertThat("incorrect message", st.get("message"), is((Object) "OK"));
		// should throw an error if not a valid semver
		Version.valueOf((String) st.get("version")); 
		assertThat("incorrect git url", st.get("git_url"),
				is((Object) "https://github.com/kbase/workspace_deluxe"));
		checkMem(st.get("freemem"), "freemem");
		checkMem(st.get("totalmem"), "totalmem");
		checkMem(st.get("maxmem"), "maxmem");
		
		//deps
		@SuppressWarnings("unchecked")
		final List<Map<String, String>> deps =
				(List<Map<String, String>>) st.get("dependencies");
		assertThat("missing dependencies", deps.size(), is(4));
		
		final List<List<String>> exp = new ArrayList<List<String>>();
		exp.add(Arrays.asList("MongoDB", "true"));
		exp.add(Arrays.asList("GridFS", "true"));
		exp.add(Arrays.asList("Handle service", "false"));
		exp.add(Arrays.asList("Handle manager", "false"));
		final Iterator<List<String>> expiter = exp.iterator();
		final Iterator<Map<String, String>> gotiter = deps.iterator();
		while (expiter.hasNext()) {
			final Map<String, String> g = gotiter.next();
			final List<String> e = expiter.next();
			assertThat("incorrect name", (String) g.get("name"), is(e.get(0)));
			assertThat("incorrect state", g.get("state"), is((Object) "OK"));
			assertThat("incorrect message", g.get("message"),
					is((Object) "OK"));
			if (e.get(1).equals("false")) {
				assertThat("incorrect version", (String) g.get("version"),
						is("Unknown"));
			} else {
				Version.valueOf((String) g.get("version"));
			}
		}
	}
	
	private void checkMem(final Object num, final String name)
			throws Exception {
		if (num instanceof Integer) {
			assertThat("bad " + name, (Integer) num > 0, is(true));
		} else {
			assertThat("bad " + name, (Long) num > 0, is(true));
		}
	}
	
	@SuppressWarnings("deprecation")
	@Test
	public void basicHandleTest() throws Exception {
		String workspace = "basichandle";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(workspace));
		Handle h1 = HANDLE_CLIENT.newHandle();
		List<String> handleList = new LinkedList<String>();
		handleList.add(h1.getHid());
		Map<String, Object> handleobj = new HashMap<String, Object>();
		handleobj.put("handles", handleList);
		try {
			CLIENT1.saveObjects(new SaveObjectsParams()
					.withWorkspace(workspace)
					.withObjects(Arrays.asList(
							new ObjectSaveData().withData(new UObject(handleobj)).withName("foo")
							.withType(HANDLE_TYPE))));
		} catch (ServerException se) {
			System.out.println(se.getData());
			throw se;
		}

		/* should be impossible for a non-owner to save a handle containing
		 * object where they don't own the shock nodes, even with all other
		 * privileges
		 */
		BasicShockClient bsc = new BasicShockClient(
				new URL("http://localhost:" + SHOCK.getServerPort()), CLIENT1.getToken());
		@SuppressWarnings("unused")
		ShockACL acl = bsc.addToNodeAcl(new ShockNodeId(h1.getId()),
				Arrays.asList(USER2), ShockACLType.ALL);
		String workspace2 = "basichandle2";
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace(workspace2));
		try {
			CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace(workspace2)
					.withObjects(Arrays.asList(
							new ObjectSaveData().withData(new UObject(handleobj)).withName("foo2")
							.withType(HANDLE_TYPE))));
			fail("saved object with bad handle");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getMessage(),
					is("An error occured while processing IDs: " +
						"The Handle Service reported that at least one of " +
						"the handles contained in the objects in this call " +
						"is not accessible - it may not exist, or the " +
						"supplied credentials may not own the node, or some " +
						"other reason. The call cannot complete."));
		}
		
		bsc.removeFromNodeAcl(new ShockNodeId(h1.getId()),
				Arrays.asList(USER2), ShockACLType.ALL);
		
		List<ShockUserId> oneuser = Arrays.asList(SHOCK_USER1);
		List<ShockUserId> twouser = Arrays.asList(SHOCK_USER1, SHOCK_USER2);
		
		ShockNode node = bsc.getNode(new ShockNodeId(h1.getId()));
		
		checkReadAcl(node, oneuser);

		// test that user2 can get shock nodes even though permissions have
		// been removed when user2 can read the workspace object
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace(workspace)
				.withUsers(Arrays.asList(USER2)).withNewPermission("r"));
		//get objects2
				
		ObjectData ret1 = CLIENT2.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(new ObjectSpecification()
					.withWorkspace(workspace)
					.withObjid(1L)))).getData().get(0);
		checkHandleError(ret1.getHandleError(), ret1.getHandleStacktrace());
		
		checkReadAcl(node, twouser);
		node.removeFromNodeAcl(Arrays.asList(USER2), READ_ACL);
		checkReadAcl(node, oneuser);
		
		//get objects
		ObjectData ret = CLIENT2.getObjects(Arrays.asList(new ObjectIdentity().withWorkspace(workspace)
				.withObjid(1L))).get(0);
		checkHandleError(ret.getHandleError(), ret.getHandleStacktrace());
		
		checkReadAcl(node, twouser);
		node.removeFromNodeAcl(Arrays.asList(USER2), READ_ACL);
		checkReadAcl(node, oneuser);

		//object subset
		ret = CLIENT2.getObjectSubset(Arrays.asList(new us.kbase.workspace.SubObjectIdentity().withWorkspace(workspace)
				.withObjid(1L))).get(0);
		checkHandleError(ret.getHandleError(), ret.getHandleStacktrace());
		
		checkReadAcl(node, twouser);
		node.removeFromNodeAcl(Arrays.asList(USER2), READ_ACL);
		checkReadAcl(node, oneuser);

		//object provenance
		us.kbase.workspace.ObjectProvenanceInfo ret2 = CLIENT2.getObjectProvenance(Arrays.asList(
				new ObjectIdentity().withWorkspace(workspace)
				.withObjid(1L))).get(0);
		checkHandleError(ret2.getHandleError(), ret2.getHandleStacktrace());
		
		
		checkReadAcl(node, twouser);
		node.removeFromNodeAcl(Arrays.asList(USER2), READ_ACL);
		checkReadAcl(node, oneuser);
		
		//object by ref chain
		Map<String, String> refdata = new HashMap<String, String>();
		refdata.put("id", workspace + "/1");
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(workspace)
				.withObjects(Arrays.asList(
						new ObjectSaveData().withData(new UObject(refdata)).withName("foo3")
						.withType(HANDLE_REF_TYPE))));
		ret = CLIENT2.getReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withWorkspace(workspace)
				.withObjid(2L), new ObjectIdentity().withWorkspace(workspace)
				.withObjid(1L)))).get(0);
		checkHandleError(ret.getHandleError(), ret.getHandleStacktrace());
		
		checkReadAcl(node, twouser);
		
		//test error message for deleted node
		node.delete();
		
		ObjectData wod = CLIENT2.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(new ObjectSpecification()
					.withWorkspace(workspace)
					.withObjid(1L)))).getData().get(0);
		
		@SuppressWarnings("unchecked")
		Map<String, Object> retdata = wod.getData().asClassInstance(Map.class);
		assertThat("got correct data", retdata, is(handleobj));
		
		assertThat("got correct error message", wod.getHandleError(),
				is("The Handle Manager reported a problem while attempting to set Handle ACLs: Unable to set acl(s) on handles "
						+ h1.getHid()));
		assertTrue("got correct stacktrace", wod.getHandleStacktrace().startsWith(
				"us.kbase.common.service.ServerException: Unable to set acl(s) on handles "
						+ h1.getHid()));
	}
	
	@Test
	public void publicWorkspaceHandleTest() throws Exception {
		String workspace = "publicWS";
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace(workspace).withGlobalread("r"));
		Handle h1 = HANDLE_CLIENT.newHandle();
		List<String> handleList = new LinkedList<String>();
		handleList.add(h1.getHid());
		Map<String, Object> handleobj = new HashMap<String, Object>();
		handleobj.put("handles", handleList);
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(workspace)
				.withObjects(Arrays.asList(
						new ObjectSaveData().withData(new UObject(handleobj)).withName("foo")
						.withType(HANDLE_TYPE))));
		
		// check that there's only one user in the ACL
		BasicShockClient bsc = new BasicShockClient(
				new URL("http://localhost:" + SHOCK.getServerPort()),
				CLIENT1.getToken());
		List<ShockUserId> oneuser = Arrays.asList(SHOCK_USER1);
		List<ShockUserId> twouser = Arrays.asList(SHOCK_USER1, SHOCK_USER2);
		
		ShockNode node = bsc.getNode(new ShockNodeId(h1.getId()));
		
		checkReadAcl(node, oneuser);
		
		// check users without explicit access to the workspace can get
		// object & nodes
		ObjectData ret1 = CLIENT2.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(new ObjectSpecification()
					.withWorkspace(workspace)
					.withObjid(1L)))).getData().get(0);
		checkHandleError(ret1.getHandleError(), ret1.getHandleStacktrace());
		checkReadAcl(node, twouser);
		
		checkPublicRead(node, false);
		// check that anonymous users can get the object & shock nodes
		CLIENT_NOAUTH.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(new ObjectSpecification()
					.withWorkspace(workspace)
					.withObjid(1L)))).getData().get(0);
		checkHandleError(ret1.getHandleError(), ret1.getHandleStacktrace());
		checkPublicRead(node, true);
		
		//test error message for deleted node with no auth
		node.delete();
		ObjectData wod = CLIENT_NOAUTH.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(new ObjectSpecification()
					.withWorkspace(workspace)
					.withObjid(1L)))).getData().get(0);
		
		@SuppressWarnings("unchecked")
		Map<String, Object> retdata = wod.getData().asClassInstance(Map.class);
		assertThat("got correct data", retdata, is(handleobj));
		
		assertThat("got correct error message", wod.getHandleError(),
				is("The Handle Manager reported a problem while attempting to set Handle ACLs: Unable to set acl(s) on handles "
						+ h1.getHid()));
		assertTrue("got correct stacktrace", wod.getHandleStacktrace().startsWith(
				"us.kbase.common.service.ServerException: Unable to set acl(s) on handles "
						+ h1.getHid()));
		
	}

	private void checkPublicRead(
			final ShockNode node,
			final boolean publicRead)
			throws Exception {
		assertThat("correct public read state",
				node.getACLs().isPublicallyReadable(), is(publicRead));
	}

	private void checkHandleError(String err, String stack) {
		if (err != null || stack != null) {
			throw new TestException("Handle service reported an error: "
					+ err + "\n" + stack);
		}
	}
	
	private void checkReadAcl(ShockNode node, List<ShockUserId> uuids)
			throws Exception {
		assertThat("correct shock acls", node.getACLs().getRead(),
				is(uuids));
		
	}
	
	@Test
	public void badHandle() throws Exception {
		String workspace = "nullhandle";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(workspace));
		List<String> handleList = new LinkedList<String>();
		handleList.add(null);
		Map<String, Object> handleobj = new HashMap<String, Object>();
		handleobj.put("handles", handleList);
		try {
			CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(workspace)
					.withObjects(Arrays.asList(
							new ObjectSaveData().withData(new UObject(handleobj)).withName("foo")
							.withType(HANDLE_TYPE))));
			fail("saved null handle");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getMessage(),
					is("Object #1, foo failed type checking:\ninstance type (null) not allowed " +
							"for ID reference (allowed: [\"string\"]), at /handles/0"));
		}
		handleList.set(0, "");
		try {
			CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(workspace)
					.withObjects(Arrays.asList(
							new ObjectSaveData().withData(new UObject(handleobj)).withName("foo1")
							.withType(HANDLE_TYPE))));
			fail("saved bad handle");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getMessage(),
					is("Object #1, foo1 failed type checking:\nUnparseable id  of type handle: " +
							"IDs may not be null or the empty string at /handles/0"));
		}
	}
	
	@Test
	public void idCount() throws Exception {
		IdReferenceType type = HandleIdHandlerFactory.type;
		IdReferenceHandlerSetFactory fac = new IdReferenceHandlerSetFactory(4);
		fac.addFactory(new HandleIdHandlerFactory(new URL("http://localhost:"
				+ HANDLE.getHandleServerPort()), CLIENT1.getToken()));
		IdReferenceHandlerSet<String> handlers = fac.createHandlers(String.class);
		handlers.associateObject("foo");
		handlers.addStringId(new IdReference<String>(type, "KBH_1", null));
		handlers.addStringId(new IdReference<String>(type, "KBH_1", null));
		handlers.addStringId(new IdReference<String>(type, "KBH_2", null));
		handlers.associateObject("foo1");
		handlers.addStringId(new IdReference<String>(type, "KBH_1", null));
		assertThat("id count correct", handlers.size(), is(3));
		handlers.addStringId(new IdReference<String>(type, "KBH_2", null));
		try {
			handlers.addStringId(new IdReference<String>(type, "KBH_3", null));
			fail("exceeded max IDs");
		} catch (TooManyIdsException e) {
			assertThat("correct exception msg", e.getMessage(),
					is("Maximum ID count of 4 exceeded"));
		}
		
	}
}
