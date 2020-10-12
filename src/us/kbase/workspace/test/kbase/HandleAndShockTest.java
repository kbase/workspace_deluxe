package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.set;
import static us.kbase.workspace.test.kbase.JSONRPCLayerTester.administerCommand;
import us.kbase.workspace.test.kbase.JSONRPCLayerTester.ServerThread;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.zafarkhaja.semver.Version;
import com.google.common.collect.ImmutableMap;

import us.kbase.abstracthandle.AbstractHandleClient;
import us.kbase.abstracthandle.Handle;
import us.kbase.auth.AuthToken;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UObject;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.TestException;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockACLType;
import us.kbase.shock.client.ShockFileInformation;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.ShockUserId;
import us.kbase.test.auth2.authcontroller.AuthController;
import us.kbase.typedobj.idref.IdReference;
import us.kbase.typedobj.idref.IdReferenceHandlerSet;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.TooManyIdsException;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactoryBuilder;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GetObjects2Params;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ObjectSpecification;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.kbase.HandleIdHandlerFactory;
import us.kbase.workspace.test.controllers.handle.HandleServiceController;
import us.kbase.workspace.test.controllers.shock.ShockController;

public class HandleAndShockTest {

	/* The Handle ID tests were written years before the Shock ID tests, and cover a lot
	 * of cases that are general to both ID types, such as making sure all the methods that
	 * allow retrieving an object set the permissions correctly. As such, the Shock ID tests
	 * concentrate on testing cases that are unique to Shock IDs.
	 */

	/* This test also performs an integration test on the Shock backend since we have to
	 * use Shock here anyway.
	 */

	private static MongoController MONGO;
	private static ShockController SHOCK;
	private static HandleServiceController HANDLE;
	private static AuthController AUTH;
	private static WorkspaceServer SERVER;

	private static final String USER1 = "user1";
	private static final String USER2 = "user2";
	private static final String USER3 = "user3";
	private static ShockUserId SHOCK_USER1;
	private static ShockUserId SHOCK_USER2;
	private static ShockUserId SHOCK_USER3;
	private static final String HANDLE_ADMIN_ROLE = "HANDLE_ADMIN_ROLE";

	private static WorkspaceClient CLIENT1;
	private static WorkspaceClient CLIENT2;
	private static WorkspaceClient CLIENT3;
	private static WorkspaceClient CLIENT_NOAUTH;

	private static AuthToken HANDLE_SRVC_TOKEN;

	private static AbstractHandleClient HANDLE_CLIENT;
	private static BasicShockClient WS_OWNED_SHOCK;
	private static BasicShockClient SHOCK_CLIENT_1;
	private static BasicShockClient SHOCK_CLIENT_2;
	
	private static ShockACLType READ_ACL = ShockACLType.READ;

	private static String HANDLE_TYPE = "HandleByteStreamList.HList-0.1";
	private static String SHOCK_TYPE = "HandleByteStreamList.SList-0.1";
	private static String HANDLE_REF_TYPE = "HandleByteStreamList.HRef-0.1";
	
	private static String HANDLE_SERVICE_TEST_DB = "handle_service_test_handle_db";

	@BeforeClass
	public static void setUpClass() throws Exception {
		TestCommon.stfuLoggers();

		MONGO = new MongoController(TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using Mongo temp dir " + MONGO.getTempDir());
		final String mongohost = "localhost:" + MONGO.getServerPort();

		// set up auth
		final String dbname = HandleAndShockTest.class.getSimpleName() + "Auth";
		AUTH = new AuthController(
				TestCommon.getJarsDir(),
				"localhost:" + MONGO.getServerPort(),
				dbname,
				Paths.get(TestCommon.getTempDir()));
		final URL authURL = new URL("http://localhost:" + AUTH.getServerPort() + "/testmode");
		System.out.println("started auth server at " + authURL);

		TestCommon.createAuthUser(authURL, USER1, "display1");
		final String token1 = TestCommon.createLoginToken(authURL, USER1);
		TestCommon.createAuthUser(authURL, USER2, "display2");
		final String token2 = TestCommon.createLoginToken(authURL, USER2);
		TestCommon.createAuthUser(authURL, USER3, "display3");
		final String token3 = TestCommon.createLoginToken(authURL, USER3);
		TestCommon.createCustomRole(authURL, HANDLE_ADMIN_ROLE, "handle admin role");
		TestCommon.setUserRoles(authURL, USER3, Arrays.asList(HANDLE_ADMIN_ROLE));
		final AuthToken t1 = new AuthToken(token1, USER1);
		final AuthToken t2 = new AuthToken(token2, USER2);
		HANDLE_SRVC_TOKEN = new AuthToken(token3, USER3);

		SHOCK = new ShockController(
				TestCommon.getShockExe(),
				TestCommon.getShockVersion(),
				Paths.get(TestCommon.getTempDir()),
				"user3",
				mongohost,
				"JSONRPCLayerHandleTest_ShockDB",
				null,
				null,
				new URL(authURL.toString() + "/api/legacy/globus"));
		final URL shockURL = new URL("http://localhost:" + SHOCK.getServerPort());
		System.out.println("Shock controller version: " + SHOCK.getVersion());
		if (SHOCK.getVersion() == null) {
			System.out.println(
					"Unregistered version - Shock may not start correctly");
		}
		System.out.println("Using Shock temp dir " + SHOCK.getTempDir());
		WS_OWNED_SHOCK = new BasicShockClient(shockURL, HANDLE_SRVC_TOKEN);
		SHOCK_CLIENT_1 = new BasicShockClient(shockURL, t1);
		SHOCK_CLIENT_2 = new BasicShockClient(shockURL, t2);

		HANDLE = new HandleServiceController(
				MONGO,
				shockURL.toString(),
				HANDLE_SRVC_TOKEN,
				Paths.get(TestCommon.getTempDir()),
				new URL(authURL.toString()),
				HANDLE_ADMIN_ROLE,
				TestCommon.getHandleServiceDir(),
				HANDLE_SERVICE_TEST_DB);
		System.out.println("Using Handle Service temp dir " + HANDLE.getTempDir());
		System.out.println("Started Handle Service on port " + HANDLE.getHandleServerPort());

		SERVER = startupWorkspaceServer(mongohost,
				"HandleAndShockTest",
				"HandleAndShockTest_types",
				shockURL,
				t2,
				HANDLE_SRVC_TOKEN,
				HANDLE_SRVC_TOKEN);

		int port = SERVER.getServerPort();
		System.out.println("Started test workspace server on port " + port);

		final URL url = new URL("http://localhost:" + port);
		CLIENT1 = new WorkspaceClient(url, t1);
		CLIENT2 = new WorkspaceClient(url, t2);
		CLIENT3 = new WorkspaceClient(url, HANDLE_SRVC_TOKEN);
		CLIENT_NOAUTH = new WorkspaceClient(url);
		CLIENT1.setIsInsecureHttpConnectionAllowed(true);
		CLIENT2.setIsInsecureHttpConnectionAllowed(true);
		CLIENT3.setIsInsecureHttpConnectionAllowed(true);
		CLIENT_NOAUTH.setIsInsecureHttpConnectionAllowed(true);

		setUpSpecs();

		HANDLE_CLIENT = new AbstractHandleClient(new URL("http://localhost:" +
				HANDLE.getHandleServerPort()), t1);
		HANDLE_CLIENT.setIsInsecureHttpConnectionAllowed(true);
		try {
			HANDLE_CLIENT.areReadable(Arrays.asList("fake_handle_id"));
		} catch (Exception e) {
			System.out.println("Could not successfullly run methods on the Handle Service");
			System.out.println(e.getMessage());
			throw e;
		}

		BasicShockClient bsc = new BasicShockClient(new URL("http://localhost:"
				+ SHOCK.getServerPort()), CLIENT1.getToken());
		SHOCK_USER1 = bsc.addNode().getACLs().getOwner();
		bsc.updateToken(CLIENT2.getToken());
		SHOCK_USER2 = bsc.addNode().getACLs().getOwner();
		bsc.updateToken(HANDLE_SRVC_TOKEN);
		SHOCK_USER3 = bsc.addNode().getACLs().getOwner();

		System.out.println("finished HandleService setup");
	}

	private static void setUpSpecs() throws Exception {
		final String handlespec =
				"module HandleByteStreamList {" +
					"/* @id handle */" +
					"typedef string handle;" +
					"typedef structure {" +
						"list<handle> handles;" +
					"} HList;" +
					"/* @id bytestream */" +
					"typedef string bs;" +
					"typedef structure {" +
						"list<bs> ids;" +
					"} SList;" +
					"/* @id ws */" +
					"typedef string wsid;" +
					"typedef structure {" +
						"wsid id;" +
					"} HRef;" +
				"};";
		CLIENT1.requestModuleOwnership("HandleByteStreamList");
		administerCommand(CLIENT2, "approveModRequest", "module", "HandleByteStreamList");
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec(handlespec)
			.withNewTypes(Arrays.asList("HList", "SList", "HRef")));
	}

	private static WorkspaceServer startupWorkspaceServer(
			final String mongohost,
			final String db,
			final String typedb,
			final URL shockURL,
			final AuthToken shockToken,
			final AuthToken handleToken,
			final AuthToken shockLinkToken)
			throws InvalidHostException, UnknownHostException, IOException,
				NoSuchFieldException, IllegalAccessException, Exception,
				InterruptedException {

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
		ws.add("mongodb-database", db);
		ws.add("mongodb-type-database", typedb);
		ws.add("backend-secret", "foo");
		ws.add("auth-service-url-allow-insecure", "true");
		ws.add("auth-service-url", "http://localhost:" + AUTH.getServerPort() +
				"/testmode/api/legacy/KBase");
		ws.add("auth2-service-url", "http://localhost:" + AUTH.getServerPort() + "/testmode/");
		ws.add("backend-type", "Shock");
		ws.add("backend-url", shockURL.toString());
		ws.add("backend-user", shockToken.getUserName());
		ws.add("backend-token", shockToken.getToken());
		ws.add("bytestream-url", shockURL.toString());
		ws.add("bytestream-user", shockLinkToken.getUserName());
		ws.add("bytestream-token", shockLinkToken.getToken());
		ws.add("ws-admin", USER2);
		ws.add("handle-service-url", "http://localhost:" + HANDLE.getHandleServerPort());
		ws.add("handle-service-token", handleToken.getToken());
		ws.add("temp-dir", Paths.get(TestCommon.getTempDir())
				.resolve("tempForHandleAndShockTest"));
		ini.store(iniFile);
		iniFile.deleteOnExit();

		//set up env
		Map<String, String> env = TestCommon.getenv();
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

	//TODO TEST should clear DBs between tests

	@AfterClass
	public static void tearDownClass() throws Exception {
		if (SERVER != null) {
			System.out.print("Killing workspace server... ");
			SERVER.stopServer();
			System.out.println("Done");
		}
		if (HANDLE != null) {
			HANDLE.destroy(TestCommon.getDeleteTempFiles(), false);
		}
		if (SHOCK != null) {
			SHOCK.destroy(TestCommon.getDeleteTempFiles());
		}
		if (AUTH != null) {
			AUTH.destroy(TestCommon.getDeleteTempFiles());
		}
		if (MONGO != null) {
			MONGO.destroy(TestCommon.getDeleteTempFiles());
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

		final Iterator<Map<String, String>> gotiter = deps.iterator();
		for (final String name: Arrays.asList(
				"MongoDB", "Shock", "Linked Shock for IDs", "Handle service")) {
			final Map<String, String> g = gotiter.next();
			assertThat("incorrect name", (String) g.get("name"), is(name));
			assertThat("incorrect state", g.get("state"), is((Object) "OK"));
			assertThat("incorrect message", g.get("message"), is((Object) "OK"));
			Version.valueOf((String) g.get("version"));
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
		// this test exercises deprecated methods. It should not be changed until the
		// deprecated methods are removed from the workspace API.
		String workspace = "basichandle";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(workspace));
		BasicShockClient bsc = new BasicShockClient(
				new URL("http://localhost:" + SHOCK.getServerPort()), CLIENT1.getToken());

		final ShockNode node = bsc.addNode();
		String shock_id = node.getId().getId();
		final Handle handle = new Handle();
		handle.setId(shock_id);
		handle.setFileName("empty_file");
		handle.setUrl(bsc.getShockUrl().toString());
		handle.setType("shock");
		String handle_id = HANDLE_CLIENT.persistHandle(handle);

		List<String> handleList = new LinkedList<String>();
		handleList.add(handle_id);
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
		bsc.addToNodeAcl(new ShockNodeId(shock_id), Arrays.asList(USER2), ShockACLType.ALL);
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

		bsc.removeFromNodeAcl(new ShockNodeId(shock_id),
				Arrays.asList(USER2), ShockACLType.ALL);

		List<ShockUserId> oneuser = Arrays.asList(SHOCK_USER1);
		List<ShockUserId> twouser = Arrays.asList(SHOCK_USER1, SHOCK_USER2);

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
		checkExternalIDError(ret1.getHandleError(), ret1.getHandleStacktrace());

		checkReadAcl(node, twouser);
		node.removeFromNodeAcl(Arrays.asList(USER2), READ_ACL);
		checkReadAcl(node, oneuser);

		//get objects
		ObjectData ret = CLIENT2.getObjects(Arrays.asList(new ObjectIdentity().withWorkspace(workspace)
				.withObjid(1L))).get(0);
		checkExternalIDError(ret.getHandleError(), ret.getHandleStacktrace());

		checkReadAcl(node, twouser);
		node.removeFromNodeAcl(Arrays.asList(USER2), READ_ACL);
		checkReadAcl(node, oneuser);

		//object subset
		ret = CLIENT2.getObjectSubset(Arrays.asList(new us.kbase.workspace.SubObjectIdentity().withWorkspace(workspace)
				.withObjid(1L))).get(0);
		checkExternalIDError(ret.getHandleError(), ret.getHandleStacktrace());

		checkReadAcl(node, twouser);
		node.removeFromNodeAcl(Arrays.asList(USER2), READ_ACL);
		checkReadAcl(node, oneuser);

		//object provenance
		us.kbase.workspace.ObjectProvenanceInfo ret2 = CLIENT2.getObjectProvenance(Arrays.asList(
				new ObjectIdentity().withWorkspace(workspace)
				.withObjid(1L))).get(0);
		checkExternalIDError(ret2.getHandleError(), ret2.getHandleStacktrace());

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
		checkExternalIDError(ret.getHandleError(), ret.getHandleStacktrace());

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
						is("The Handle Manager reported a problem while attempting to set Handle ACLs: 'Unable to set acl(s) on handles " + handle_id + "'"));
		assertThat("incorrect stacktrace", wod.getHandleStacktrace(),
						startsWith("us.kbase.typedobj.idref.IdReferencePermissionHandlerSet$" +
										"IdReferencePermissionHandlerException: " +
										"The Handle Manager reported a problem while attempting to set Handle " +
										"ACLs: 'Unable to set acl(s) on handles " + handle_id));
	}

	@Test
	public void saveAndGetWithShockIDs() throws Exception {
		// tests shock nodes that are already owned by the WS (but readable by the user)
		// as well as nodes merely owned by the user.
		final String workspace = "basicshock";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(workspace));
		final ShockNode n1 = WS_OWNED_SHOCK.addNode(ImmutableMap.of("foo", "bar"),
				new ByteArrayInputStream("contents".getBytes()), "fname", "text");
		n1.addToNodeAcl(Arrays.asList(USER1), ShockACLType.ALL);
		// test that user1's write & delete creds are removed from the ws-owned node
		checkReadAcl(n1, Arrays.asList(SHOCK_USER3, SHOCK_USER1));
		checkWriteAcl(n1, Arrays.asList(SHOCK_USER3, SHOCK_USER1));
		checkDeleteAcl(n1, Arrays.asList(SHOCK_USER3, SHOCK_USER1));
		final ShockNode n2 = SHOCK_CLIENT_1.addNode(ImmutableMap.of("foo", "bar2"),
				new ByteArrayInputStream("contents2".getBytes()), "fname2", "text2");
		try {
			// expect n1 to stay the same, n2 to be changed to a new shock ID
			CLIENT1.saveObjects(new SaveObjectsParams()
					.withWorkspace(workspace)
					.withObjects(Arrays.asList(
							new ObjectSaveData()
							.withData(new UObject(ImmutableMap.of("ids", Arrays.asList(
									n1.getId().getId(), n2.getId().getId()))))
							.withName("foo")
							.withType(SHOCK_TYPE))));
		} catch (ServerException se) {
			System.out.println(se.getData());
			throw se;
		}
		checkReadAcl(n1, Arrays.asList(SHOCK_USER3, SHOCK_USER1));
		checkWriteAcl(n1, Arrays.asList(SHOCK_USER3));
		checkDeleteAcl(n1, Arrays.asList(SHOCK_USER3));
		checkReadAcl(n2, Arrays.asList(SHOCK_USER1));
		checkPublicRead(n1, false);
		checkPublicRead(n2, false);
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace(workspace)
				.withNewPermission("r").withUsers(Arrays.asList(USER2, USER3)));

		final GetObjects2Params gop = new GetObjects2Params()
				.withObjects(Arrays.asList(new ObjectSpecification()
						.withWorkspace(workspace).withName("foo")));

		// get the object with the workspace user that owns linked shock nodes
		final ObjectData od = CLIENT3.getObjects2(gop).getData().get(0);
		checkExternalIDError(od.getHandleError(), od.getHandleStacktrace());
		checkReadAcl(n1, Arrays.asList(SHOCK_USER3, SHOCK_USER1));
		checkReadAcl(n2, Arrays.asList(SHOCK_USER1, SHOCK_USER3));
		
		final Map<String, Object> data = od.getData().asClassInstance(
				new TypeReference<Map<String, Object>>() {});
		@SuppressWarnings("unchecked")
		final List<String> shockids = (List<String>) data.get("ids");
		assertThat("incorrect shock node", shockids.get(0), is(n1.getId().getId()));
		final ShockNode new2 = WS_OWNED_SHOCK.getNode(new ShockNodeId(shockids.get(1)));
		assertThat("node unexpectedly updated", new2.getId(), is(n2.getId()));
		checkReadAcl(n2, Arrays.asList(SHOCK_USER1, SHOCK_USER3));
		
		// get the object with user 1
		CLIENT1.getObjects2(gop);
		checkReadAcl(n2, Arrays.asList(SHOCK_USER1, SHOCK_USER3));
		
		// get the object with user 2
		CLIENT2.getObjects2(gop);
		checkReadAcl(n1, Arrays.asList(SHOCK_USER3, SHOCK_USER1, SHOCK_USER2));
		checkReadAcl(n2, Arrays.asList(SHOCK_USER1, SHOCK_USER3, SHOCK_USER2));
		
		// remove user 2 privs, and re get
		WS_OWNED_SHOCK.removeFromNodeAcl(n1.getId(), Arrays.asList(USER2), ShockACLType.READ);
		WS_OWNED_SHOCK.removeFromNodeAcl(n2.getId(), Arrays.asList(USER2), ShockACLType.READ);
		checkReadAcl(n1, Arrays.asList(SHOCK_USER3, SHOCK_USER1));
		checkReadAcl(n2, Arrays.asList(SHOCK_USER1, SHOCK_USER3));
		CLIENT2.getObjects2(gop);
		checkReadAcl(n1, Arrays.asList(SHOCK_USER3, SHOCK_USER1, SHOCK_USER2));
		checkReadAcl(n2, Arrays.asList(SHOCK_USER1, SHOCK_USER3, SHOCK_USER2));
		
		// check extracted IDS
		assertThat("incorrect extracted ids", od.getExtractedIds().keySet(),
				is(set("bytestream")));
		assertThat("incorrect extracted ids",
				new HashSet<>(od.getExtractedIds().get("bytestream")),
				is(set(n1.getId().getId(), n2.getId().getId())));
		
		// check nodes have the same contents
		checkNode(WS_OWNED_SHOCK, n1.getId(), ImmutableMap.of("foo", "bar"),
				"contents", "fname", "text");
		checkNode(WS_OWNED_SHOCK, n2.getId(), ImmutableMap.of("foo", "bar2"),
				"contents2", "fname2", "text2");

		checkPublicRead(n1, false);
		checkPublicRead(n2, false);
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace(workspace).withNewPermission("r"));

		// get the object with anon user
		CLIENT_NOAUTH.getObjects2(gop);
		checkReadAcl(n1, Arrays.asList(SHOCK_USER3, SHOCK_USER1, SHOCK_USER2));
		checkReadAcl(n2, Arrays.asList(SHOCK_USER1, SHOCK_USER3, SHOCK_USER2));
		checkPublicRead(n1, true);
		checkPublicRead(n2, true);
	}

	private void checkNode(
			final BasicShockClient cli,
			final ShockNodeId id,
			final Object attrib,
			final String file,
			final String filename,
			final String format)
			throws Exception {
		final ShockNode sn = cli.getNode(id);
		final ShockFileInformation fi = sn.getFileInformation();
		assertThat("incorrect attribs", sn.getAttributes(), is(attrib));
		assertThat("incorrect filename", fi.getName(), is(filename));
		assertThat("incorrect format", fi.getFormat(), is(format));
		assertThat("incorrect file", IOUtils.toString(sn.getFile()), is(file));
	}

	@Test
	public void saveWithShockIDFail() throws Exception {
		final String workspace = "shocksavefail";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(workspace));

		saveWithShockIDFail(CLIENT1, workspace, null, new ServerException(
				"Object #1, foo failed type checking:\ninstance type (null) not allowed for " +
				"ID reference (allowed: [\"string\"]), at /ids/0", 1, "n"));

		saveWithShockIDFail(CLIENT1, workspace, "badbytestreamid", new ServerException(
				"Object #1, foo failed type checking:\nUnparseable id badbytestreamid of " +
				"type bytestream: Illegal bytestream ID: badbytestreamid at /ids/0", 1, "n"));

		final String id = UUID.randomUUID().toString();
		saveWithShockIDFail(CLIENT1, workspace, id, new ServerException(String.format(
				"Object #1, foo has invalid reference: Bytestream node %s does not exist at " +
				"/ids/0", id), 1, "n"));

		final ShockNode sn = WS_OWNED_SHOCK.addNode();
		saveWithShockIDFail(CLIENT1, workspace, sn.getId().getId(), new ServerException(
				String.format("Object #1, foo has invalid reference: User user1 cannot " +
				"read bytestream node %s at /ids/0", sn.getId().getId()), 1, "n"));
		
		final ShockNode sn2 = SHOCK_CLIENT_2.addNode();
		sn2.addToNodeAcl(Arrays.asList(USER1), ShockACLType.READ);
		saveWithShockIDFail(CLIENT1, workspace, sn2.getId().getId(), new ServerException(
				String.format("Object #1, foo has invalid reference: User user1 does not " +
				"own bytestream node %s at /ids/0", sn2.getId().getId()), 1, "n"));
	}

	private void saveWithShockIDFail(
			final WorkspaceClient cli,
			final String workspace,
			final String id,
			final Exception expected) {
		try {
			CLIENT1.saveObjects(new SaveObjectsParams()
					.withWorkspace(workspace)
					.withObjects(Arrays.asList(
							new ObjectSaveData()
							.withData(new UObject(ImmutableMap.of("ids", Arrays.asList(id))))
							.withName("foo")
							.withType(SHOCK_TYPE))));
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}

	@Test
	public void getWithShockIDsFail() throws Exception {
		final String workspace = "shockgetfail";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(workspace));
		final ShockNode n1 = WS_OWNED_SHOCK.addNode();
		n1.addToNodeAcl(Arrays.asList(USER1), ShockACLType.READ);
		final ShockNodeId id = n1.getId();
		try {
			CLIENT1.saveObjects(new SaveObjectsParams()
					.withWorkspace(workspace)
					.withObjects(Arrays.asList(
							new ObjectSaveData()
							.withData(new UObject(ImmutableMap.of("ids", Arrays.asList(
									id.getId()))))
							.withName("foo")
							.withType(SHOCK_TYPE))));
		} catch (ServerException se) {
			System.out.println(se.getData());
			throw se;
		}

		n1.delete();
		final ObjectData wod = CLIENT1.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(new ObjectSpecification()
					.withWorkspace(workspace).withName("foo")))).getData().get(0);

		getWithShockIDsFail(id, wod);

		// anon user
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace(workspace).withNewPermission("r"));
		final ObjectData anonwod = CLIENT_NOAUTH.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(new ObjectSpecification()
						.withWorkspace(workspace).withName("foo")))).getData().get(0);

		getWithShockIDsFail(id, anonwod);
	}

	private void getWithShockIDsFail(final ShockNodeId id, final ObjectData wod) {
		@SuppressWarnings("unchecked")
		Map<String, Object> retdata = wod.getData().asClassInstance(Map.class);
		assertThat("got correct data", retdata, is(ImmutableMap.of("ids", Arrays.asList(
				id.getId()))));

		final String err = String.format(
				"Bytestream storage reported a problem while attempting to set ACLs on node %s: " +
				"Node not found", id.getId());
		assertThat("got correct error message", wod.getHandleError(), is(err));


		assertThat("incorrect stacktrace", wod.getHandleStacktrace(),
				startsWith("us.kbase.typedobj.idref.IdReferencePermissionHandlerSet$" +
						"IdReferencePermissionHandlerException: " + err));
	}

	@Test
	public void publicWorkspaceHandleTest() throws Exception {
		String workspace = "publicWS";
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace(workspace).withGlobalread("r"));

		BasicShockClient bsc = new BasicShockClient(
				new URL("http://localhost:" + SHOCK.getServerPort()), CLIENT1.getToken());

		final ShockNode node = bsc.addNode();
		String shock_id = node.getId().getId();

		final Handle handle = new Handle();
		handle.setId(shock_id);
		handle.setFileName("empty_file");
		handle.setUrl(bsc.getShockUrl().toString());
		handle.setType("shock");
		String handle_id = HANDLE_CLIENT.persistHandle(handle);

		List<String> handleList = new LinkedList<String>();
		handleList.add(handle_id);
		Map<String, Object> handleobj = new HashMap<String, Object>();
		handleobj.put("handles", handleList);
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(workspace)
				.withObjects(Arrays.asList(
						new ObjectSaveData().withData(new UObject(handleobj)).withName("foo")
						.withType(HANDLE_TYPE))));

		// check that there's only one user in the ACL
		List<ShockUserId> oneuser = Arrays.asList(SHOCK_USER1);
		List<ShockUserId> twouser = Arrays.asList(SHOCK_USER1, SHOCK_USER2);

		checkReadAcl(node, oneuser);

		// check users without explicit access to the workspace can get
		// object & nodes
		ObjectData ret1 = CLIENT2.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(new ObjectSpecification()
					.withWorkspace(workspace)
					.withObjid(1L)))).getData().get(0);
		checkExternalIDError(ret1.getHandleError(), ret1.getHandleStacktrace());
		checkReadAcl(node, twouser);
		checkPublicRead(node, false);
		// check that anonymous users can get the object & shock nodes
		CLIENT_NOAUTH.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(new ObjectSpecification()
					.withWorkspace(workspace)
					.withObjid(1L)))).getData().get(0);
		checkExternalIDError(ret1.getHandleError(), ret1.getHandleStacktrace());
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
						is("The Handle Manager reported a problem while attempting to set Handle ACLs: 'Unable to set acl(s) on handles " + handle_id + "'"));
		assertThat("incorrect stacktrace", wod.getHandleStacktrace(),
						startsWith("us.kbase.typedobj.idref.IdReferencePermissionHandlerSet$" +
										"IdReferencePermissionHandlerException: " +
										"The Handle Manager reported a problem while attempting to set Handle " +
										"ACLs: 'Unable to set acl(s) on handles " + handle_id));
	}

	private void checkPublicRead(
			final ShockNode node,
			final boolean publicRead)
			throws Exception {
		assertThat("correct public read state",
				node.getACLs().isPublicallyReadable(), is(publicRead));
	}

	private void checkExternalIDError(String err, String stack) {
		if (err != null || stack != null) {
			throw new TestException("External service reported an error: "
					+ err + "\n" + stack);
		}
	}

	private void checkReadAcl(final ShockNode node, final List<ShockUserId> users)
			throws Exception {
		assertThat("correct shock acls", node.getACLs().getRead(), is(users));
	}

	private void checkWriteAcl(final ShockNode node, final List<ShockUserId> users)
			throws Exception {
		assertThat("correct shock acls", node.getACLs().getWrite(), is(users));
	}

	private void checkDeleteAcl(final ShockNode node, final List<ShockUserId> users)
			throws Exception {
		assertThat("correct shock acls", node.getACLs().getDelete(), is(users));
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
		IdReferenceType type = HandleIdHandlerFactory.TYPE;
		IdReferenceHandlerSetFactory fac = IdReferenceHandlerSetFactoryBuilder.getBuilder(4)
				.build().getFactory(CLIENT1.getToken());
		final AbstractHandleClient client = new AbstractHandleClient(
				new URL("http://localhost:" + HANDLE.getHandleServerPort()), HANDLE_SRVC_TOKEN);
		fac.addFactory(new HandleIdHandlerFactory(client));
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
