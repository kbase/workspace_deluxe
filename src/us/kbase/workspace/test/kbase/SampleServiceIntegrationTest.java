package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.controllers.ControllerCommon.findFreePort;
import static us.kbase.workspace.test.kbase.JSONRPCLayerTester.administerCommand;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.arangodb.ArangoDatabase;
import com.arangodb.entity.CollectionType;
import com.arangodb.model.CollectionCreateOptions;
import com.github.zafarkhaja.semver.Version;
import com.google.common.collect.ImmutableMap;

import us.kbase.auth.AuthToken;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.sampleservice.SampleServiceClient;
import us.kbase.test.auth2.authcontroller.AuthController;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.test.controllers.arango.ArangoController;
import us.kbase.workspace.test.controllers.sample.SampleServiceController;
import us.kbase.workspace.test.kbase.JSONRPCLayerTester.ServerThread;

public class SampleServiceIntegrationTest {

	private static ArangoController ARANGO;
	private static MongoController MONGO;
	private static SampleServiceController SAMPLE;
	private static AuthController AUTH;
	private static WorkspaceServer SERVER;
	
	private static final String USER1 = "user1";
	private static final String USER2 = "user2";
	private static final String USER3 = "user3";
	private static final String SAMPLE_SERVICE_FULL_ADMIN_ROLE = "SamServFull";
	
	private static AuthToken SAMPLE_SERVICE_ADMIN_TOKEN = null;

	private static WorkspaceClient CLIENT1;
	private static WorkspaceClient CLIENT2;
	private static WorkspaceClient CLIENT3;
	private static WorkspaceClient CLIENT_NOAUTH;
	
	private static SampleServiceClient SAMPLE_CLIENT;
	
	private static final String ARANGO_DB = "ws_sample_service_integration_test";
	private static final String ARANGO_USER = "arangouser";
	private static final String ARANGO_PWD = "arangopwd";
	private static final String ARANGO_COL_SAMPLE = "sampleCol";
	private static final String ARANGO_COL_VER = "verCol";
	private static final String ARANGO_COL_VER_EDGE = "verEdgeCol";
	private static final String ARANGO_COL_NODE = "nodeCol";
	private static final String ARANGO_COL_NODE_EDGE = "nodeEdgeCol";
	private static final String ARANGO_COL_DATA_LINK = "dataLinkCol";
	private static final String ARANGO_COL_WS_OBJ = "wsObjCol";
	private static final String ARANGO_COL_SCHEMA = "schemaCol";

	@BeforeClass
	public static void setUpClass() throws Exception {
		TestCommon.stfuLoggers();

		MONGO = new MongoController(
				TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using Mongo temp dir " + MONGO.getTempDir());
		final String mongohost = "localhost:" + MONGO.getServerPort();

		ARANGO = new ArangoController(
				TestCommon.getArangoExe(),
				TestCommon.getArangoJS(),
				Paths.get(TestCommon.getTempDir()));
		
		// set up auth
		final String dbname = SampleServiceIntegrationTest.class.getSimpleName() + "Auth";
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
		TestCommon.createCustomRole(authURL, SAMPLE_SERVICE_FULL_ADMIN_ROLE, "sample admin role");
		TestCommon.setUserRoles(authURL, USER3, Arrays.asList(SAMPLE_SERVICE_FULL_ADMIN_ROLE));
		final AuthToken t1 = new AuthToken(token1, USER1);
		final AuthToken t2 = new AuthToken(token2, USER2);
		SAMPLE_SERVICE_ADMIN_TOKEN = new AuthToken(token3, USER3);
		
		// this is pretty wacky and demonstrates the tight binding between the sample
		// service and the workspace.
		final int sampleServicePort = findFreePort();

		SERVER = startupWorkspaceServer(mongohost,
				SampleServiceIntegrationTest.class.getSimpleName(),
				SampleServiceIntegrationTest.class.getSimpleName() + "_types",
				new URL("http://localhost:" + sampleServicePort),
				SAMPLE_SERVICE_ADMIN_TOKEN
				);

		int port = SERVER.getServerPort();
		System.out.println("Started test workspace server on port " + port);

		final URL url = new URL("http://localhost:" + port);
		CLIENT1 = new WorkspaceClient(url, t1);
		CLIENT2 = new WorkspaceClient(url, t2);
		CLIENT3 = new WorkspaceClient(url, SAMPLE_SERVICE_ADMIN_TOKEN);
		CLIENT_NOAUTH = new WorkspaceClient(url);
		CLIENT1.setIsInsecureHttpConnectionAllowed(true);
		CLIENT2.setIsInsecureHttpConnectionAllowed(true);
		CLIENT3.setIsInsecureHttpConnectionAllowed(true);
		CLIENT_NOAUTH.setIsInsecureHttpConnectionAllowed(true);

		setUpSpecs();
		
		setUpSampleDB();
		SAMPLE = new SampleServiceController(
				sampleServicePort,
				ARANGO,
				TestCommon.getSampleServiceDir(),
				Paths.get(TestCommon.getTempDir()),
				authURL,
				token2,
				SAMPLE_SERVICE_FULL_ADMIN_ROLE,
				new URL("http://localhost:" + port),
				token2, // we won't be doing any calls that require ws auth
				new SampleServiceController.SampleServiceArangoParameters(
						ARANGO_USER,
						ARANGO_PWD,
						ARANGO_DB,
						ARANGO_COL_SAMPLE,
						ARANGO_COL_VER,
						ARANGO_COL_VER_EDGE,
						ARANGO_COL_NODE,
						ARANGO_COL_NODE_EDGE,
						ARANGO_COL_DATA_LINK,
						ARANGO_COL_WS_OBJ,
						ARANGO_COL_SCHEMA)
				);
		
		SAMPLE_CLIENT = new SampleServiceClient(
				new URL("http://localhost:" + SAMPLE.getPort()), t1);
		SAMPLE_CLIENT.setIsInsecureHttpConnectionAllowed(true);
		System.out.println(String.format("Running sample service v %s at http://localhost:%s",
				SAMPLE_CLIENT.status().get("version"), SAMPLE.getPort()));
	}
	
	private static void setUpSampleDB() throws Exception {
		ARANGO.getClient().createUser(ARANGO_USER, ARANGO_PWD);
		ARANGO.getClient().createDatabase(ARANGO_DB);
		final ArangoDatabase db = ARANGO.getClient().db(ARANGO_DB);

		db.grantAccess(ARANGO_USER);
		
		final CollectionCreateOptions edge = new CollectionCreateOptions()
				.type(CollectionType.EDGES);
		db.createCollection(ARANGO_COL_SAMPLE);
		db.createCollection(ARANGO_COL_VER);
		db.createCollection(ARANGO_COL_VER_EDGE, edge);
		db.createCollection(ARANGO_COL_NODE);
		db.createCollection(ARANGO_COL_NODE_EDGE, edge);
		db.createCollection(ARANGO_COL_DATA_LINK, edge);
		db.createCollection(ARANGO_COL_WS_OBJ);
		db.createCollection(ARANGO_COL_SCHEMA);
	}

	private static void setUpSpecs() throws Exception {
		final String handlespec =
				"module Samples {" +
					"/* @id sample */" +
					"typedef string sample;" +
					"typedef structure {" +
						"list<sample> samples;" +
					"} SList;" +
					"/* @id ws */" +
					"typedef string wsid;" +
					"typedef structure {" +
						"wsid id;" +
					"} SRef;" +
				"};";
		CLIENT1.requestModuleOwnership("Samples");
		administerCommand(CLIENT2, "approveModRequest", "module", "Samples");
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec(handlespec)
			.withNewTypes(Arrays.asList("SList", "SRef")));
	}

	private static WorkspaceServer startupWorkspaceServer(
			final String mongohost,
			final String db,
			final String typedb,
			final URL sampleServiceURL,
			final AuthToken sampleServiceToken)
			throws Exception {

		//write the server config file:
		File iniFile = File.createTempFile("test", ".cfg",
				new File(TestCommon.getTempDir()));
		if (iniFile.exists()) {
			iniFile.delete();
		}
		System.out.println("Created temporary config file: " + iniFile.getAbsolutePath());
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
		ws.add("backend-type", "GridFS");
		ws.add("ws-admin", USER2);
		ws.add("ignore-handle-service", "true");
		ws.add("sample-service-url", sampleServiceURL.toString());
		ws.add("sample-service-administrator-token", sampleServiceToken.getToken());
		ws.add("temp-dir", Paths.get(TestCommon.getTempDir())
				.resolve("tempFor" + SampleServiceIntegrationTest.class.getSimpleName()));
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
		if (SAMPLE != null) {
			SAMPLE.destroy(TestCommon.getDeleteTempFiles(), true);
		}
		if (SERVER != null) {
			System.out.print("Killing workspace server... ");
			SERVER.stopServer();
			System.out.println("Done");
		}
		if (AUTH != null) {
			AUTH.destroy(TestCommon.getDeleteTempFiles());
		}
		if (ARANGO != null) {
			ARANGO.destroy(TestCommon.getDeleteTempFiles());
		}
		if (MONGO != null) {
			MONGO.destroy(TestCommon.getDeleteTempFiles());
		}
	}
	
	@Test
	public void status() throws Exception {
		// only test the parts of the status that are relevant for the sample service
		final Map<String, Object> status = CLIENT1.status();
		
//		System.out.println(status);
		
		assertThat("incorrect status keys", status.keySet(), is(new HashSet<>(Arrays.asList(
				"state", "message", "dependencies", "version", "git_url", "freemem", "totalmem",
				"maxmem"))));
		
		assertThat("incorrect state", status.get("state"), is("OK"));

		@SuppressWarnings("unchecked")
		final List<Map<String, String>> deps =
				(List<Map<String, String>>) status.get("dependencies");
		assertThat("incorrect dependency count", deps.size(), is(3));
		
		assertThat("incorrect dep 1", deps.get(0).get("name"), is("MongoDB"));
		assertThat("incorrect dep 2", deps.get(1).get("name"), is("GridFS"));
		
		final Map<String, String> sample = deps.get(2);
		Version.valueOf((String) sample.get("version")); // tests it's a valid semver
		sample.remove("version");
		
		assertThat("incorrect sample dep", sample, is(ImmutableMap.of(
				"name", "Sample service", "state", "OK", "message", "OK")));
		
		
	}
	
}
