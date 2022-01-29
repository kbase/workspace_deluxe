package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.controllers.ControllerCommon.findFreePort;
import static us.kbase.workspace.test.kbase.JSONRPCLayerTester.administerCommand;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.arangodb.ArangoDatabase;
import com.arangodb.entity.CollectionType;
import com.arangodb.model.CollectionCreateOptions;
import com.github.zafarkhaja.semver.Version;
import com.google.common.collect.ImmutableMap;
import com.mongodb.MongoClient;

import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UObject;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.TestException;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.sampleservice.CreateSampleParams;
import us.kbase.sampleservice.GetSampleACLsParams;
import us.kbase.sampleservice.Sample;
import us.kbase.sampleservice.SampleACLs;
import us.kbase.sampleservice.SampleAddress;
import us.kbase.sampleservice.SampleNode;
import us.kbase.sampleservice.SampleServiceClient;
import us.kbase.sampleservice.UpdateSampleACLsParams;
import us.kbase.test.auth2.authcontroller.AuthController;
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
import us.kbase.workspace.test.WorkspaceServerThread;
import us.kbase.workspace.test.controllers.arango.ArangoController;
import us.kbase.workspace.test.controllers.sample.SampleServiceController;

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
	
	private static final String WS_DB = ARANGO_DB;
	
	private static final String SAMPLE_TYPE = "Samples.SList-0.1";
	private static final String SAMPLE_REF_TYPE = "Samples.SRef-0.1";

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
				WS_DB,
				WS_DB + "_types",
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
		new WorkspaceServerThread(server).start();
		System.out.println("Main thread waiting for server to start up");
		while (server.getServerPort() == null) {
			Thread.sleep(1000);
		}
		return server;
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		if (SAMPLE != null) {
			SAMPLE.destroy(TestCommon.getDeleteTempFiles());
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
	
	
	@Before
	public void clearDB() throws Exception {
		try (final MongoClient cli = new MongoClient("localhost:" + MONGO.getServerPort())) {
			TestCommon.destroyDB(cli.getDatabase(WS_DB));
		}
		ARANGO.clearDatabase(ARANGO_DB, false);
	}
	

	private SampleAddress createGenericSample() throws IOException, JsonClientException {
		return SAMPLE_CLIENT.createSample(new CreateSampleParams()
				.withSample(new Sample()
					.withName("my name")
					.withNodeTree(Arrays.asList(new SampleNode()
							.withId("node1")
							.withType("BioReplicate")
							))
					));
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
	
	@Test
	public void saveSampleAndCheckACLChanges() throws Exception {
		final SampleAddress samadd = createGenericSample();
		SAMPLE_CLIENT.updateSampleAcls(new UpdateSampleACLsParams()
				.withId(samadd.getId())
				.withAdmin(Arrays.asList(USER2)));
		final String workspace = "basicsample";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(workspace));
		CLIENT1.setPermissions(new SetPermissionsParams()
				.withWorkspace(workspace)
				.withUsers(Arrays.asList(USER2, USER3))
				.withNewPermission("w"));
		
		for (final WorkspaceClient cli: Arrays.asList(CLIENT1, CLIENT2)) {
			// test users can save against sample as long as they have admin privs
			final String objName = cli.getToken().getUserName() + "obj";
			saveSampleObject(cli, workspace, objName, samadd.getId(), true);
			
			final ObjectData obj = getObject(cli, "1/" + objName + "/1");
			
			assertThat("incorrect object name", obj.getInfo().getE2(), is(objName));
			
			assertThat("incorrect extracted IDs", obj.getExtractedIds(), is(ImmutableMap.of(
					"sample", Arrays.asList(samadd.getId()))));
			
			assertThat("incorrect object", obj.getData().asClassInstance(Map.class),
					is(ImmutableMap.of("samples", Arrays.asList(samadd.getId()))));
		}
		
		final SampleACLs expected = addAdmin(initEmptyACLs().withOwner(USER1), USER2);
		assertAclsCorrect(SAMPLE_CLIENT, samadd.getId(), expected);
		
		// trigger ACL change, first skipping the ACL change with an input param
		getObject(CLIENT3, "1/" + USER1 + "obj/1", 1L);
		assertAclsCorrect(SAMPLE_CLIENT, samadd.getId(), expected);
		// now actually trigger the change
		getObject(CLIENT3, "1/" + USER1 + "obj/1");
		assertAclsCorrect(SAMPLE_CLIENT, samadd.getId(), addRead(expected, USER3));
	}
	
	@Test
	public void publicReadChange() throws Exception {
		final SampleAddress samadd = createGenericSample();
		final String workspace = "pubread";
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace(workspace)
				.withGlobalread("r"));
		
		saveSampleObject(CLIENT1, workspace, "myobj", samadd.getId(), true);
		
		// trigger ACL change for anon user
		getObject(CLIENT_NOAUTH, "1/myobj/1");
		assertAclsCorrect(SAMPLE_CLIENT, samadd.getId(), initEmptyACLs()
				.withOwner(USER1).withPublicRead(1L));
		
		// trigger ACL change for user without direct workspace access
		getObject(CLIENT2, "1/myobj/1");
		assertAclsCorrect(SAMPLE_CLIENT, samadd.getId(), addRead(initEmptyACLs()
				.withOwner(USER1).withPublicRead(1L), USER2));
	}
	
	@Test
	public void removeSampleACLAndGet() throws Exception {
		// Tests that a user can still read a sample after getting a referring object
		// even if they're removed from the sample ACLs.
		final SampleAddress samadd = createGenericSample();
		final String workspace = "removeACL";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(workspace));
		CLIENT1.setPermissions(new SetPermissionsParams()
				.withWorkspace(workspace)
				.withUsers(Arrays.asList(USER2))
				.withNewPermission("r"));
		
		saveSampleObject(CLIENT1, workspace, "myobj", samadd.getId(), true);
		getObject(CLIENT2, "1/myobj/1");
		
		final SampleACLs acls = addRead(initEmptyACLs().withOwner(USER1), USER2);
		assertAclsCorrect(SAMPLE_CLIENT, samadd.getId(), acls);
		
		SAMPLE_CLIENT.updateSampleAcls(new UpdateSampleACLsParams()
				.withId(samadd.getId())
				.withRemove(Arrays.asList(USER2)));
		acls.getRead().clear();
		assertAclsCorrect(SAMPLE_CLIENT, samadd.getId(), acls);

		getObject(CLIENT2, "1/myobj/1");
		assertAclsCorrect(SAMPLE_CLIENT, samadd.getId(), addRead(acls, USER2));
	}
	
	@SuppressWarnings("deprecation")
	@Test
	public void deprecatedMethods() throws Exception {
		// tests that the deprecated object retrieval methods trigger ACL changes
		final SampleAddress samadd = createGenericSample();
		final String workspace = "removeACL";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(workspace));
		CLIENT1.setPermissions(new SetPermissionsParams()
				.withWorkspace(workspace)
				.withUsers(Arrays.asList(USER2))
				.withNewPermission("r"));
		
		saveSampleObject(CLIENT1, workspace, "myobj", samadd.getId(), true);
		CLIENT1.saveObjects(new SaveObjectsParams()
				.withId(1L)
				.withObjects(Arrays.asList(new ObjectSaveData()
						.withName("ref")
						.withType(SAMPLE_REF_TYPE)
						.withData(new UObject(ImmutableMap.of("id", "1/1/1"))))));
		final SampleACLs acls = initEmptyACLs().withOwner(USER1);
		final SampleACLs readacls = addRead(initEmptyACLs().withOwner(USER1), USER2);
		
		// get objects
		final ObjectData obj = CLIENT2
				.getObjects(Arrays.asList(new ObjectIdentity().withName("myobj").withWsid(1L)))
				.get(0);
		checkExternalIDError(obj);
		
		assertThat("incorrect object", obj.getData().asClassInstance(Map.class),
				is(ImmutableMap.of("samples", Arrays.asList(samadd.getId()))));
		assertAclsCorrect(SAMPLE_CLIENT, samadd.getId(), readacls);

		// get object provenance
		removeUserFromSampleACLs(SAMPLE_CLIENT, samadd.getId(), USER2);
		assertAclsCorrect(SAMPLE_CLIENT, samadd.getId(), acls);
		final us.kbase.workspace.ObjectProvenanceInfo pobj = CLIENT2.getObjectProvenance(
				Arrays.asList(new ObjectIdentity().withObjid(1L).withWsid(1L))).get(0);
		checkExternalIDError(pobj.getHandleError(), pobj.getHandleStacktrace());
		
		assertThat("incorrect ids", pobj.getExtractedIds(),
				is(ImmutableMap.of("sample", Arrays.asList(samadd.getId()))));
		assertAclsCorrect(SAMPLE_CLIENT, samadd.getId(), readacls);
		
		// get object subset
		removeUserFromSampleACLs(SAMPLE_CLIENT, samadd.getId(), USER2);
		assertAclsCorrect(SAMPLE_CLIENT, samadd.getId(), acls);
		final ObjectData sobj = CLIENT2.getObjectSubset(Arrays.asList(
				new us.kbase.workspace.SubObjectIdentity()
						.withIncluded(Arrays.asList("samples"))
						.withWsid(1L)
						.withObjid(1L)))
				.get(0);
		checkExternalIDError(sobj);
		assertThat("incorrect object", sobj.getData().asClassInstance(Map.class),
				is(ImmutableMap.of("samples", Arrays.asList(samadd.getId()))));
		assertAclsCorrect(SAMPLE_CLIENT, samadd.getId(), readacls);
		
		// get object by ref
		removeUserFromSampleACLs(SAMPLE_CLIENT, samadd.getId(), USER2);
		assertAclsCorrect(SAMPLE_CLIENT, samadd.getId(), acls);
		final ObjectData robj = CLIENT2.getReferencedObjects(Arrays.asList(Arrays.asList(
				new ObjectIdentity().withRef("1/2/1"), new ObjectIdentity().withRef("1/1/1"))))
				.get(0);
		checkExternalIDError(robj);
		assertThat("incorrect object", robj.getData().asClassInstance(Map.class),
				is(ImmutableMap.of("samples", Arrays.asList(samadd.getId()))));
		assertAclsCorrect(SAMPLE_CLIENT, samadd.getId(), readacls);
	}
	
	@Test
	public void saveSampleFailBadID() throws Exception {
		createGenericSample(); // put a sample in the database
		String workspace = "failbadid";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(workspace));
		
		saveSampleObjectFail(CLIENT1, workspace, "whoops", null,
				new ServerException(
						"Object #1, whoops failed type checking:\ninstance type (null) not " +
						"allowed for ID reference (allowed: [\"string\"]), at /samples/0",
						1, "name"));
		
		saveSampleObjectFail(CLIENT1, workspace, "whoops", "fakesampleaddress",
				new ServerException(
						"Object #1, whoops has invalid reference: The Sample Service reported " +
						"a problem while attempting to get Sample ACLs: Sample service " +
						"error code 30001 Illegal input parameter: id fakesampleaddress " +
						"must be a UUID string at /samples/0", 1, "name"));
	}
	
	@Test
	public void saveSampleFailUnauthorized() throws Exception {
		final SampleAddress samadd = createGenericSample();
		SAMPLE_CLIENT.updateSampleAcls(new UpdateSampleACLsParams()
				.withId(samadd.getId())
				.withPublicRead(1L)
				.withWrite(Arrays.asList(USER3))
				.withRead(Arrays.asList(USER2)));
		String workspace = "failunauthed";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(workspace));
		CLIENT1.setPermissions(new SetPermissionsParams()
				.withWorkspace(workspace)
				.withUsers(Arrays.asList(USER2, USER3))
				.withNewPermission("w"));
		
		for (final WorkspaceClient cli: Arrays.asList(CLIENT2, CLIENT3)) {
			saveSampleObjectFail(cli, workspace, "foo", samadd.getId(), new ServerException(
					String.format(
						"Object #1, foo has invalid reference: User %s does not have " +
						"administrative permissions for sample %s at /samples/0",
						cli.getToken().getUserName(), samadd.getId()),
					1, "name"));
		}
		
	}
	
	private void saveSampleObject(
			final WorkspaceClient cli,
			final String workspace,
			final String objectName,
			final String sampleid)
			throws Exception {
		saveSampleObject(cli, workspace, objectName, sampleid, false);
	}
	
	private void saveSampleObject(
			final WorkspaceClient cli,
			final String workspace,
			final String objectName,
			final String sampleid,
			final boolean printException)
			throws Exception {
		try {
			cli.saveObjects(new SaveObjectsParams()
					.withWorkspace(workspace)
					.withObjects(Arrays.asList(
							new ObjectSaveData()
									.withData(new UObject(ImmutableMap.of(
											"samples", Arrays.asList(sampleid))))
									.withName(objectName)
									.withType(SAMPLE_TYPE))));
		} catch (ServerException se) {
			if (printException) {
				System.out.println(se.getData());
			}
			throw se;
		}
	}
	
	private void saveSampleObjectFail(
			final WorkspaceClient cli,
			final String workspace,
			final String objectName,
			final String sampleid,
			final Exception expected) {
		try {
			saveSampleObject(cli, workspace, objectName, sampleid);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	private void assertAclsCorrect(
			final SampleServiceClient cli,
			final String id,
			final SampleACLs expected)
			throws Exception {
		assertAclsCorrect(cli, id, expected, false);
		
	}
	
	private void assertAclsCorrect(
			final SampleServiceClient cli,
			final String id,
			final SampleACLs expected,
			final boolean printACLs)
			throws Exception {
		final SampleACLs acls = cli.getSampleAcls(new GetSampleACLsParams().withId(id));
		if (printACLs) {
			System.out.println(acls);
		}
		assertAclsCorrect(acls, expected);
		
	}
	
	private void assertAclsCorrect(final SampleACLs got, final SampleACLs expected) {
		// ^(^&*^^^(%_ no equals in SDK objects
		assertThat("incorrect ACL addl props", got.getAdditionalProperties(),
				is(Collections.emptyMap()));
		assertThat("incorrect is public", got.getPublicRead(), is(expected.getPublicRead()));
		assertThat("incorrect owner", got.getOwner(), is(expected.getOwner()));
		assertThat("incorrect admin", got.getAdmin(), is(expected.getAdmin()));
		assertThat("incorrect write", got.getWrite(), is(expected.getWrite()));
		assertThat("incorrect read", got.getRead(), is(expected.getRead()));
	}
	
	// Initializes with empty mutable arraylists for the ACLs and false for public read.
	// Owner is left as null.
	private SampleACLs initEmptyACLs() {
		return new SampleACLs()
				.withPublicRead(0L)
				.withAdmin(new ArrayList<>())
				.withWrite(new ArrayList<>())
				.withRead(new ArrayList<>());
	}
	
	// expects that there's a mutable list in the read field
	private SampleACLs addRead(final SampleACLs initedACLs, final String user) {
		initedACLs.getRead().add(user);
		return initedACLs;
	}
	
	// expects that there's a mutable list in the admin field
	private SampleACLs addAdmin(final SampleACLs initedACLs, final String user) {
		initedACLs.getAdmin().add(user);
		return initedACLs;
	}
	
	private void removeUserFromSampleACLs(
			final SampleServiceClient cli,
			final String id,
			final String user)
			throws Exception {
		cli.updateSampleAcls(new UpdateSampleACLsParams()
				.withId(id)
				.withRemove(Arrays.asList(USER2)));
	}

	private ObjectData getObject(final WorkspaceClient cli, final String ref) throws Exception {
		return getObject(cli, ref, null);
	}
	
	private ObjectData getObject(
			final WorkspaceClient cli,
			final String ref,
			final Long skipExternalACLUpdates)
			throws Exception {
		return checkExternalIDError(cli.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(new ObjectSpecification().withRef(ref)))
				.withSkipExternalSystemUpdates(skipExternalACLUpdates)
				).getData().get(0));
	}
	
	private ObjectData checkExternalIDError(final ObjectData obj) {
		checkExternalIDError(obj.getHandleError(), obj.getHandleStacktrace());
		return obj;
	}
	
	private void checkExternalIDError(final String err, final String stack) {
		if (err != null || stack != null) {
			throw new TestException("External service reported an error: "
					+ err + "\n" + stack);
		}
	}
}
