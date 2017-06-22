package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Paths;
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
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import us.kbase.auth.AuthConfig;
import us.kbase.auth.AuthToken;
import us.kbase.auth.AuthUser;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple9;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.TestException;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.workspace.AlterWorkspaceMetadataParams;
import us.kbase.workspace.ExternalDataUnit;
import us.kbase.workspace.GetObjectInfo3Params;
import us.kbase.workspace.GetObjectInfo3Results;
import us.kbase.workspace.GetObjects2Params;
import us.kbase.workspace.ListObjectsParams;
import us.kbase.workspace.ListWorkspaceInfoParams;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ObjectSpecification;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.RenameObjectParams;
import us.kbase.workspace.RenameWorkspaceParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.SetWorkspaceDescriptionParams;
import us.kbase.workspace.SubAction;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.UncheckedUserMetadata;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.kbase.InitWorkspaceServer;
import us.kbase.workspace.test.JsonTokenStreamOCStat;
import us.kbase.workspace.test.WorkspaceTestCommon;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DB;
import com.mongodb.MongoClient;

/*
 * These tests are specifically for testing the JSON-RPC communications between
 * the client, up to the invocation of the {@link us.kbase.workspace.workspaces.Workspaces}
 * methods. As such they do not test the full functionality of the Workspaces methods;
 * {@link us.kbase.workspace.test.workspaces.TestWorkspaces} handles that. This means
 * that only one backend (the simplest gridFS backend) is tested here, while TestWorkspaces
 * tests all backends and {@link us.kbase.workspace.database.WorkspaceDatabase} implementations.
 */
public class JSONRPCLayerTester {
	
	private static final String DB_WS_NAME_1 = "JSONRPCLayerTester1";
	private static final String DB_TYPE_NAME_1 = "JSONRPCLayerTester1_types";
	private static final String DB_WS_NAME_2 = "JSONRPCLayerTester2";
	private static final String DB_TYPE_NAME_2 = "JSONRPCLayerTester2_types";
	
	protected static WorkspaceServer SERVER1 = null;
	protected static WorkspaceClient CLIENT1 = null;
	protected static WorkspaceClient CLIENT2 = null;  // This client connects to SERVER1 as well
	protected static WorkspaceClient CLIENT3 = null;  // This client connects to SERVER1 as well
	protected static String USER1 = null;
	protected static String USER2 = null;
	protected static String USER3 = null;
	protected static String STARUSER = "*";
	protected static AuthUser AUTH_USER1 = null;
	protected static AuthUser AUTH_USER2 = null;
	protected static WorkspaceServer SERVER2 = null;
	protected static WorkspaceClient CLIENT_FOR_SRV2 = null;  // This client connects to SERVER2
	protected static WorkspaceClient CLIENT_NO_AUTH = null;
	
	protected static ObjectMapper MAPPER = new ObjectMapper();
	protected final static int MAX_UNIQUE_IDS_PER_CALL = 4;
	
	private static MongoController mongo;
	
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
	public static final String SAFE_TYPE1 = "SomeModule.AType-1.0";
	public static final String REF_TYPE ="RefSpec.Ref-0.1";
	
	public static final Map<String, String> MT_META =
			new HashMap<String, String>();
	
	private static List<TempFilesManager> TFMS =
			new LinkedList<TempFilesManager>();
	
	protected static class ServerThread extends Thread {
		private WorkspaceServer server;
		
		protected ServerThread(WorkspaceServer server) {
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
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		System.out.println("Using auth url " + TestCommon.getAuthUrl());
		final ConfigurableAuthService auth = new ConfigurableAuthService(
				new AuthConfig().withKBaseAuthServerURL(
						TestCommon.getAuthUrl())
				.withAllowInsecureURLs(true));
		final AuthToken t1 = TestCommon.getToken(1, auth);
		final AuthToken t2 = TestCommon.getToken(2, auth);
		final AuthToken t3 = TestCommon.getToken(3, auth);
		USER1 = t1.getUserName();
		USER2 = t2.getUserName();
		USER3 = t3.getUserName();
		if (USER1.equals(USER2) || USER2.equals(USER3) || USER1.equals(USER3)) {
			throw new TestException("All the test users must be unique: " + 
					StringUtils.join(Arrays.asList(USER1, USER2, USER3), " "));
		}
		
		TestCommon.stfuLoggers();
		mongo = new MongoController(TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using mongo temp dir " + mongo.getTempDir());
		
		final String mongohost = "localhost:" + mongo.getServerPort();
		MongoClient mongoClient = new MongoClient(mongohost);
		
		SERVER1 = startupWorkspaceServer(
				mongohost, mongoClient.getDB(DB_WS_NAME_1), DB_TYPE_NAME_1);
		int port = SERVER1.getServerPort();
		System.out.println("Started test server 1 on port " + port);
		try {
			CLIENT1 = new WorkspaceClient(new URL("http://localhost:" + port), t1);
		} catch (UnauthorizedException ue) {
			throw new TestException("Unable to login with test.user1: " + USER1 +
					"\nPlease check the credentials in the test configuration.", ue);
		}
		try {
			CLIENT2 = new WorkspaceClient(new URL("http://localhost:" + port), t2);
		} catch (UnauthorizedException ue) {
			throw new TestException("Unable to login with test.user2: " + USER2 +
					"\nPlease check the credentials in the test configuration.", ue);
		}
		try {
			CLIENT3 = new WorkspaceClient(new URL("http://localhost:" + port), t3);
		} catch (UnauthorizedException ue) {
			throw new TestException("Unable to login with test.user3: " + USER3 +
					"\nPlease check the credentials in the test configuration.", ue);
		}
		AUTH_USER1 = auth.getUserFromToken(t1);
		AUTH_USER2 = auth.getUserFromToken(t2);

		SERVER2 = startupWorkspaceServer(
				mongohost, mongoClient.getDB(DB_WS_NAME_2), DB_TYPE_NAME_2);
		CLIENT_FOR_SRV2 = new WorkspaceClient(new URL("http://localhost:" + 
					SERVER2.getServerPort()), t2);
		CLIENT_NO_AUTH = new WorkspaceClient(new URL("http://localhost:" + port));
		CLIENT1.setIsInsecureHttpConnectionAllowed(true);
		CLIENT2.setIsInsecureHttpConnectionAllowed(true);
		CLIENT3.setIsInsecureHttpConnectionAllowed(true);
		CLIENT_NO_AUTH.setIsInsecureHttpConnectionAllowed(true);
		CLIENT1.setStreamingModeOn(true); //for JSONRPCLayerLongTest
		
		//set up a basic type for test use that doesn't worry about type checking
		try {
			CLIENT1.requestModuleOwnership("SomeModule");
		} catch (ServerException se) {
			System.out.println(se.getData());
			throw se;
		}
		administerCommand(CLIENT2, "approveModRequest", "module", "SomeModule");
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("module SomeModule {/* @optional thing */ typedef structure {string thing;} AType;};")
			.withNewTypes(Arrays.asList("AType")));
		CLIENT1.releaseModule("SomeModule");
		
		//set up a type with references
		final String specParseRef =
				"module RefSpec {" +
					"/* @id ws */" +
					"typedef string reference;" +
					"typedef structure {" +
						"reference ref;" +
					"} Ref;" +
				"};";
		CLIENT1.requestModuleOwnership("RefSpec");
		administerCommand(CLIENT2, "approveModRequest", "module", "RefSpec");
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec(specParseRef)
			.withNewTypes(Arrays.asList("Ref")));
		
		
		System.out.println("Started test server 2 on port " + SERVER2.getServerPort());
		CLIENT_FOR_SRV2.setIsInsecureHttpConnectionAllowed(true);
		
		CLIENT_FOR_SRV2.requestModuleOwnership("SomeModule");
		administerCommand(CLIENT_FOR_SRV2, "approveModRequest", "module", "SomeModule");
		
		// SomeModule ver 1
		CLIENT_FOR_SRV2.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("module SomeModule {/* @optional thing */ typedef structure {int thing;} AType;};")
			.withNewTypes(Arrays.asList("AType")));
		CLIENT_FOR_SRV2.releaseModule("SomeModule");
//		System.out.println(CLIENT_FOR_SRV2.getModuleInfo(new GetModuleInfoParams()
//				.withMod("SomeModule")));
		
		CLIENT_FOR_SRV2.requestModuleOwnership("DepModule");
		administerCommand(CLIENT_FOR_SRV2, "approveModRequest", "module", "DepModule");
		
		// DepModule ver 1 and 2 (2 comes from the release) (relies on SomeModule ver1)
		CLIENT_FOR_SRV2.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("#include <SomeModule>\n" +
					"module DepModule {typedef structure {SomeModule.AType thing;} BType;};")
			.withNewTypes(Arrays.asList("BType")));
		CLIENT_FOR_SRV2.releaseModule("DepModule");
		
		// SomeModule ver 2
		CLIENT_FOR_SRV2.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("module SomeModule {/* @optional thing */ typedef structure {string thing;} AType;};")
			.withNewTypes(Collections.<String>emptyList()));
		CLIENT_FOR_SRV2.releaseModule("SomeModule");
		
		// DepModule ver 2 (relies on SomeModule ver 2)
		CLIENT_FOR_SRV2.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("#include <SomeModule>\n" +
					"module DepModule {typedef structure {SomeModule.AType thing;} BType;};")
			.withNewTypes(Collections.<String>emptyList()));
		CLIENT_FOR_SRV2.releaseModule("DepModule");
		
		// DepModule ver 3 (relies on SomeModule ver 2)
		CLIENT_FOR_SRV2.registerTypespec(new RegisterTypespecParams()
				.withDryrun(0L)
				.withSpec("#include <SomeModule>\n" +
						"module DepModule {typedef structure {SomeModule.AType thing2;} BType;};")
				.withNewTypes(Collections.<String>emptyList()));
		CLIENT_FOR_SRV2.releaseModule("DepModule");
		
		CLIENT_FOR_SRV2.requestModuleOwnership("UnreleasedModule");
		administerCommand(CLIENT_FOR_SRV2, "approveModRequest", "module", "UnreleasedModule");
		CLIENT_FOR_SRV2.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("module UnreleasedModule {typedef int AType; funcdef aFunc(AType param) returns ();};")
			.withNewTypes(Arrays.asList("AType")));
		System.out.println("Starting tests");
	}

	public static void administerCommand(WorkspaceClient client, String command, String... params) throws IOException,
			JsonClientException {
		Map<String, String> releasemod = new HashMap<String, String>();
		releasemod.put("command", command);
		for (int i = 0; i < params.length / 2; i++)
			releasemod.put(params[i * 2], params[i * 2 + 1]);
		client.administer(new UObject(releasemod));
	}

	private static WorkspaceServer startupWorkspaceServer(
			final String mongohost,
			final DB db,
			final String typedb)
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
		System.out.println("Created temporary config file: " + iniFile.getAbsolutePath());
		Ini ini = new Ini();
		Section ws = ini.add("Workspace");
		ws.add("mongodb-host", mongohost);
		ws.add("mongodb-database", db.getName());
		ws.add("auth-service-url", TestCommon.getAuthUrl());
		ws.add("auth-service-url-allow-insecure", "true");
		ws.add("globus-url", TestCommon.getGlobusUrl());
		ws.add("backend-secret", "foo");
		ws.add("ws-admin", USER2);
		ws.add("temp-dir", Paths.get(TestCommon.getTempDir())
				.resolve("tempForJSONRPCLayerTester"));
		ws.add("ignore-handle-service", "true");
		ini.store(iniFile);
		iniFile.deleteOnExit();
		
		//set up env
		Map<String, String> env = TestCommon.getenv();
		env.put("KB_DEPLOYMENT_CONFIG", iniFile.getAbsolutePath());
		env.put("KB_SERVICE_NAME", "Workspace");

		WorkspaceServer.clearConfigForTests();
		InitWorkspaceServer.setMaximumUniqueIdCountForTests(
				MAX_UNIQUE_IDS_PER_CALL);
		WorkspaceServer server = new WorkspaceServer();
		//as of 3/10/14 out of 64 objects this would force 15 to be written as temp files
		server.setResourceUsageConfiguration(
				new ResourceUsageConfigurationBuilder(
						server.getWorkspaceResourceUsageConfig())
				.withMaxIncomingDataMemoryUsage(24)
				.withMaxReturnedDataMemoryUsage(24).build());
		TFMS.add(server.getTempFilesManager());
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
		if (mongo != null) {
			System.out.println("destroying mongo temp files");
			mongo.destroy(TestCommon.getDeleteTempFiles());
		}
		JsonTokenStreamOCStat.showStat();
	}
	@Before
	public void clearDB() throws Exception {
		DB wsdb1 = GetMongoDB.getDB("localhost:" + mongo.getServerPort(),
				DB_WS_NAME_1);
		DB wsdb2 = GetMongoDB.getDB("localhost:" + mongo.getServerPort(),
				DB_WS_NAME_2);
		TestCommon.destroyDB(wsdb1);
		TestCommon.destroyDB(wsdb2);
	}
	
	@After
	public void cleanupTempFilesAfterTest() throws Exception {
		TestCommon.assertNoTempFilesExist(TFMS);
	}
	
	protected String getRandomName() {
		return UUID.randomUUID().toString().replace("-", "");
	}
	
	protected void checkWS(Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info,
			long id, String moddate, String name, String user, long objects, String perm,
			String globalperm, String lockstat, String desc, Map<String, String> meta) 
			throws Exception {
		assertThat("ids correct", info.getE1(), is(id));
		assertThat("moddates correct", info.getE4(), is(moddate));
		assertThat("ws name correct", info.getE2(), is(name));
		assertThat("user name correct", info.getE3(), is(user));
		assertThat("obj counts correct", info.getE5(), is(objects));
		assertThat("permission correct", info.getE6(), is(perm));
		assertThat("global read correct", info.getE7(), is(globalperm));
		assertThat("lockstate correct", info.getE8(), is(lockstat));
		assertThat("meta correct", info.getE9(), is(meta));
		assertThat("description correct", CLIENT1.getWorkspaceDescription(
				new WorkspaceIdentity().withWorkspace(name)), is(desc));
	}

	protected void failSetWSDesc(SetWorkspaceDescriptionParams swdp, String excep)
			throws Exception {
		try {
			CLIENT1.setWorkspaceDescription(swdp);
			fail("set ws desc with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(excep));
		}
	}
	
	protected static class StringEpoch {
		public final Long epoch;
		public final String time;
		
		public StringEpoch(long epoch) {
			this.epoch = epoch;
			this.time = null;
		}
		
		public StringEpoch(String time) {
			this.time = time;
			this.epoch = null;
		}
		public StringEpoch(long epoch, String time) {
			this.time = time;
			this.epoch = epoch;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((epoch == null) ? 0 : epoch.hashCode());
			result = prime * result + ((time == null) ? 0 : time.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			StringEpoch other = (StringEpoch) obj;
			if (epoch == null) {
				if (other.epoch != null)
					return false;
			} else if (!epoch.equals(other.epoch))
				return false;
			if (time == null) {
				if (other.time != null)
					return false;
			} else if (!time.equals(other.time))
				return false;
			return true;
		}
	}
	
	@SuppressWarnings("deprecation")
	protected void checkProvenance(String user, ObjectIdentity id,
			List<ProvenanceAction> prov, Map<String, String> refmap,
			Map<StringEpoch, StringEpoch> timemap) throws Exception {
		Date tenback = getOlderDate(10 * 60 * 1000);
		Date tenfor = getNewerDate(10 * 60 * 1000);
		
		//get objs 2 prov
		ObjectData ret1p = CLIENT1.getObjects2(new GetObjects2Params()
			.withNoData(1L)
			.withObjects(Arrays.asList(toObjSpec(id)))).getData().get(0);
		assertThat("user correct", ret1p.getCreator(), is(user));
		assertThat("wsid correct", ret1p.getOrigWsid(), is(id.getWsid()));
		Date created = DATE_FORMAT.parse(ret1p.getCreated());
		assertTrue("created within last 10 mins", created.after(tenback));
		assertTrue("epoch within last 10 mins", new Date(ret1p.getEpoch())
				.after(tenback));
		assertTrue("not saved in future", created.before(tenfor));
		assertTrue("epoch not in future", new Date(ret1p.getEpoch())
				.before(tenfor));
		checkProvenance(prov, ret1p.getProvenance(), refmap, timemap);
		assertNull("got unrequested data", ret1p.getData());
		
		//get objs 2
		ObjectData ret1 = CLIENT1.getObjects2(new GetObjects2Params()
			.withObjects(Arrays.asList(toObjSpec(id)))).getData().get(0);
		assertThat("user correct", ret1.getCreator(), is(user));
		assertThat("wsid correct", ret1.getOrigWsid(), is(id.getWsid()));
		created = DATE_FORMAT.parse(ret1.getCreated());
		assertTrue("created within last 10 mins", created.after(tenback));
		assertTrue("epoch within last 10 mins", new Date(ret1.getEpoch())
				.after(tenback));
		assertTrue("not saved in future", created.before(tenfor));
		assertTrue("epoch not in future", new Date(ret1.getEpoch())
				.before(tenfor));
		checkProvenance(prov, ret1.getProvenance(), refmap, timemap);
		
		//get objs
		ObjectData ret = CLIENT1.getObjects(Arrays.asList(id)).get(0);
		assertThat("user correct", ret.getCreator(), is(user));
		assertThat("wsid correct", ret.getOrigWsid(), is(id.getWsid()));
		created = DATE_FORMAT.parse(ret.getCreated());
		assertTrue("created within last 10 mins", created.after(tenback));
		assertTrue("epoch within last 10 mins", new Date(ret.getEpoch())
				.after(tenback));
		assertTrue("not saved in future", created.before(tenfor));
		assertTrue("epoch not in future", new Date(ret.getEpoch())
				.before(tenfor));
		checkProvenance(prov, ret.getProvenance(), refmap, timemap);
		ret = null;
		
		// get prov
		us.kbase.workspace.ObjectProvenanceInfo p = CLIENT1.getObjectProvenance(
				Arrays.asList(id)).get(0);
		assertThat("user correct", p.getCreator(), is(user));
		assertThat("wsid correct", p.getOrigWsid(), is(id.getWsid()));
		created = DATE_FORMAT.parse(p.getCreated());
		assertTrue("created within last 10 mins", created.after(tenback));
		assertTrue("epoch within last 10 mins", new Date(p.getEpoch())
				.after(tenback));
		assertTrue("not saved in future", created.before(tenfor));
		assertTrue("epoch not in future", new Date(p.getEpoch())
				.before(tenfor));
		checkProvenance(prov, p.getProvenance(), refmap, timemap);
		p = null;
		
		// get subset
		ret = CLIENT1.getObjectSubset(objIDToSubObjID(Arrays.asList(id)))
				.get(0);
		assertThat("user correct", ret.getCreator(), is(user));
		assertThat("wsid correct", ret.getOrigWsid(), is(id.getWsid()));
		created = DATE_FORMAT.parse(ret.getCreated());
		assertTrue("created within last 10 mins", created.after(tenback));
		assertTrue("epoch within last 10 mins", new Date(ret.getEpoch())
				.after(tenback));
		assertTrue("not saved in future", created.before(tenfor));
		assertTrue("epoch not in future", new Date(ret.getEpoch())
				.before(tenfor));
		checkProvenance(prov, ret.getProvenance(), refmap, timemap);
	}
	
	protected Date getOlderDate(long ms) {
		long now = new Date().getTime();
		return new Date(now - ms);
	}
	
	protected Date getNewerDate(long ms) {
		long now = new Date().getTime();
		return new Date(now + ms);
	}
	
	protected void saveProvWithBadTime(String time, String exception) throws Exception {
		UObject data = new UObject(new HashMap<String, Object>());
		SaveObjectsParams sop = new SaveObjectsParams().withWorkspace("provenance")
				.withObjects(Arrays.asList(
						new ObjectSaveData().withData(data).withType(SAFE_TYPE)
						.withName(getRandomName())
						.withProvenance(Arrays.asList(new ProvenanceAction()
						.withTime(time)))));
		try {
			CLIENT1.saveObjects(sop);
			fail("save w/ prov w/ bad time");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
		
		sop.setObjects(Arrays.asList(new ObjectSaveData()
				.withData(data).withType(SAFE_TYPE).withName(getRandomName())
				.withProvenance(Arrays.asList(new ProvenanceAction()
						.withExternalData(Arrays.asList(
								new ExternalDataUnit()
									.withResourceReleaseDate(time)))))));
		
		try {
			CLIENT1.saveObjects(sop);
			fail("save w/ prov w/ bad time");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
	}
	
	protected void checkProvenance(List<ProvenanceAction> expected,
			List<ProvenanceAction> got, Map<String, String> refmap,
			Map<StringEpoch, StringEpoch> timemap) throws Exception {
		assertThat("same number actions", got.size(),
				is(expected.size()));
		
		Iterator<ProvenanceAction> gotAct = got.iterator();
		Iterator<ProvenanceAction> expAct = expected.iterator();
		while (gotAct.hasNext()) {
			ProvenanceAction gotpa = gotAct.next();
			ProvenanceAction exppa = expAct.next();
			assertThat("cmd line equal", gotpa.getScriptCommandLine(), is(exppa.getScriptCommandLine()));
			assertThat("desc equal", gotpa.getDescription(), is(exppa.getDescription()));
			assertThat("inc args equal", gotpa.getIntermediateIncoming(), is(exppa.getIntermediateIncoming()));
			assertThat("method equal", gotpa.getMethod(), is(exppa.getMethod()));
			if (gotpa.getMethodParams() == null) {
				assertThat("method param counts are both null", gotpa.getMethodParams(),
						is(exppa.getMethodParams()));
			} else {
				assertThat("method param count equal", gotpa.getMethodParams().size(),
						is(exppa.getMethodParams().size()));
				Iterator<UObject> gotmeth = gotpa.getMethodParams().iterator();
				Iterator<UObject> expmeth = exppa.getMethodParams().iterator();
				while(gotmeth.hasNext()) {
					assertThat("meth params equal", gotmeth.next().asClassInstance(Object.class),
							is(expmeth.next().asClassInstance(Object.class)));
				}
			}
			assertThat("out args equal", gotpa.getIntermediateOutgoing(), is(exppa.getIntermediateOutgoing()));
			assertThat("script equal", gotpa.getScript(), is(exppa.getScript()));
			assertThat("script ver equal", gotpa.getScriptVer(), is(exppa.getScriptVer()));
			assertThat("service equal", gotpa.getService(), is(exppa.getService()));
			assertThat("serv ver equal", gotpa.getServiceVer(), is(exppa.getServiceVer()));
			checkProvenanceExternalData(gotpa.getExternalData(), exppa.getExternalData(), timemap);
			checkProvenanceSubActions(gotpa.getSubactions(), exppa.getSubactions());
			if (exppa.getCustom() == null) {
				assertTrue("custom fields empty", gotpa.getCustom().isEmpty()); 
			} else {
				assertThat("custom equal", gotpa.getCustom(), is(exppa.getCustom()));
			}
			assertThat("caller equal", gotpa.getCaller(), is(exppa.getCaller()));
			StringEpoch se = getStringEpoch(exppa, timemap);
			assertThat("time equal", gotpa.getTime(), is(se.time));
			assertThat("epoch equal", gotpa.getEpoch(), is(se.epoch));
			assertThat("refs equal", gotpa.getInputWsObjects(),
					is(exppa.getInputWsObjects() == null ? new ArrayList<String>() :
						exppa.getInputWsObjects()));
			assertThat("correct number resolved refs", gotpa.getResolvedWsObjects().size(),
					is(gotpa.getInputWsObjects().size()));
			Iterator<String> gotrefs = gotpa.getInputWsObjects().iterator();
			Iterator<String> gotresolvedrefs = gotpa.getResolvedWsObjects().iterator();
			while (gotrefs.hasNext()) {
				assertThat("ref resolved correctly", gotresolvedrefs.next(),
						is(refmap.get(gotrefs.next())));
			}
		}
	}

	private void checkProvenanceSubActions(
			List<SubAction> got,
			List<SubAction> exp) {
		if (exp == null) {
			assertThat("prov subactions empty", got.size(), is(0));
			return;
		}
		assertThat("prov subactions same size", got.size(), is(exp.size()));
		Iterator<SubAction> giter = got.iterator();
		Iterator<SubAction> eiter = exp.iterator();
		while (giter.hasNext()) {
			SubAction g = giter.next();
			SubAction e = eiter.next();
			assertThat("same code url", g.getCodeUrl(), is(e.getCodeUrl()));
			assertThat("same commit", g.getCommit(), is(e.getCommit()));
			assertThat("same endpoint url", g.getEndpointUrl(),
					is(e.getEndpointUrl()));
			assertThat("same name", g.getName(), is(e.getName()));
			assertThat("same version", g.getVer(), is(e.getVer()));
		}
		
	}
	
	private void checkProvenanceExternalData (
			List<ExternalDataUnit> got,
			List<ExternalDataUnit> exp, Map<StringEpoch, StringEpoch> timemap)
			throws Exception {
		if (exp == null) {
			assertThat("prov external data empty", got.size(), is(0));
			return;
		}
		assertThat("prov external data same size", got.size(), is(exp.size()));
		Iterator<ExternalDataUnit> giter = got.iterator();
		Iterator<ExternalDataUnit> eiter = exp.iterator();
		while (giter.hasNext()) {
			ExternalDataUnit g = giter.next();
			ExternalDataUnit e = eiter.next();
			assertThat("same data id", g.getDataId(), is (e.getDataId()));
			assertThat("same data url", g.getDataUrl(), is (e.getDataUrl()));
			assertThat("same description", g.getDescription(), is (e.getDescription()));
			assertThat("same resource name", g.getResourceName(), is (e.getResourceName()));
			StringEpoch se = getStringEpoch(e, timemap);
			assertThat("same resource rel date", g.getResourceReleaseDate(),
					is(se.time));
			assertThat("same resource rel epoch", g.getResourceReleaseEpoch(),
					is(se.epoch));
			assertThat("same resource url", g.getResourceUrl(), is (e.getResourceUrl()));
			assertThat("same resource ver", g.getResourceVersion(), is (e.getResourceVersion()));
		}
		
	}
	
	private StringEpoch getStringEpoch(ExternalDataUnit edu,
			Map<StringEpoch, StringEpoch> timemap) {
		if (edu.getResourceReleaseDate() != null) {
			return timemap.get(new StringEpoch(edu.getResourceReleaseDate()));
		} else if (edu.getResourceReleaseEpoch() != null){
			return timemap.get(new StringEpoch(edu.getResourceReleaseEpoch()));
		}
		return new StringEpoch(null);
	}
	
	private StringEpoch getStringEpoch(ProvenanceAction edu,
			Map<StringEpoch, StringEpoch> timemap) {
		if (edu.getTime() != null) {
			return timemap.get(new StringEpoch(edu.getTime()));
		} else if (edu.getEpoch() != null){
			return timemap.get(new StringEpoch(edu.getEpoch()));
		}
		return new StringEpoch(null);
	}

	@SuppressWarnings("deprecation")
	protected void failGetObjectInfo(final GetObjectInfo3Params params, final String exception)
			throws Exception {
		try {
			CLIENT1.getObjectInfo3(params);
			fail("got object with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
		final us.kbase.workspace.GetObjectInfoNewParams newp =
				new us.kbase.workspace.GetObjectInfoNewParams()
						.withObjects(params.getObjects())
						.withIgnoreErrors(params.getIgnoreErrors())
						.withIncludeMetadata(params.getIncludeMetadata());
		for (final String key: params.getAdditionalProperties().keySet()) {
			newp.setAdditionalProperties(key, params.getAdditionalProperties().get(key));
		}
		failGetObjectInfoNew(newp, exception.replace("GetObjectInfo3", "GetObjectInfoNew"));
	}
	
	@SuppressWarnings("deprecation")
	private void failGetObjectInfoNew(
			final us.kbase.workspace.GetObjectInfoNewParams params,
			final String exception)
			throws Exception {
		try {
			CLIENT1.getObjectInfoNew(params);
			fail("got object with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
	}
	
	protected ObjectSpecification toObjSpec(final ObjectIdentity oi) {
		if (oi == null) {
			return null;
		}
		ObjectSpecification ret = new ObjectSpecification()
			.withName(oi.getName())
			.withObjid(oi.getObjid())
			.withRef(oi.getRef())
			.withVer(oi.getVer())
			.withWorkspace(oi.getWorkspace())
			.withWsid(oi.getWsid());
		
		for (Entry<String, Object> e: oi.getAdditionalProperties().entrySet()) {
			ret.setAdditionalProperties(e.getKey(), e.getValue());
		}
		return ret;
	}
	
	protected List<ObjectSpecification> toObjSpec(final List<ObjectIdentity> oi) {
		final List<ObjectSpecification> ret = new LinkedList<ObjectSpecification>();
		for (ObjectIdentity o: oi) {
			ret.add(toObjSpec(o));
		}
		return ret;
	}
	
	private String exceptMessageOItoOS(final String message) {
		return message.replace("identifiers", "specifications")
				.replace("ObjectIdentity", "ObjectSpecification");
	}

	@SuppressWarnings("deprecation")
	protected void failGetObjects(List<ObjectIdentity> loi, String exception)
			throws Exception {
		try {
			CLIENT1.getObjects2(new GetObjects2Params()
				.withObjects(toObjSpec(loi)));
			fail("got object with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exceptMessageOItoOS(exception)));
		}
		try {
			CLIENT1.getObjects2(new GetObjects2Params().withNoData(1L)
				.withObjects(toObjSpec(loi)));
			fail("got object with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exceptMessageOItoOS(exception)));
		}
		try {
			CLIENT1.getObjectInfo3(new GetObjectInfo3Params()
				.withObjects(toObjSpec(loi)));
			fail("got info with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exceptMessageOItoOS(exception)));
		}
		try {
			CLIENT1.listReferencingObjects(loi);
			fail("got referring objs with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
		// deprecated, remove when able.
		try {
			CLIENT1.getObjects(loi);
			fail("got object with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
		try {
			CLIENT1.getObjectSubset(objIDToSubObjID(loi));
			fail("got object with bad id: " + loi);
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception.replace("ObjectIdentity", "SubObjectIdentity")));
		}
		try {
			CLIENT1.getObjectProvenance(loi);
			fail("got object provenance with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
		try {
			CLIENT1.getObjectInfoNew(new us.kbase.workspace.GetObjectInfoNewParams()
				.withObjects(toObjSpec(loi)));
			fail("got info with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exceptMessageOItoOS(exception)));
		}
		try {
			CLIENT1.getObjectInfo(loi, 0L);
			fail("got info with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
		
		try {
			CLIENT1.listReferencingObjectCounts(loi);
			fail("got referring obj counts with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
	}
	
	@SuppressWarnings("deprecation")
	protected void checkSavedObjects(List<ObjectIdentity> loi, long id, String name,
			String type, int ver, String user, long wsid, String wsname, String chksum, long size,
			Map<String, String> meta, Map<String, Object> data) throws Exception {
		
		List<ObjectData> retdata = CLIENT1.getObjects2(new GetObjects2Params()
				.withObjects(toObjSpec(loi))).getData();
		assertThat("num data correct", retdata.size(), is(loi.size()));
		for (ObjectData o: retdata) {
			checkData(o, id, name, type, ver, user, wsid, wsname,
					chksum, size, meta, data);
		}
		retdata = CLIENT1.getObjects(loi);
		assertThat("num data correct", retdata.size(), is(loi.size()));
		for (ObjectData o: retdata) {
			checkData(o, id, name, type, ver, user, wsid, wsname,
					chksum, size, meta, data);
		}
		retdata = CLIENT1.getObjectSubset(objIDToSubObjID(loi));
		assertThat("num data correct", retdata.size(), is(loi.size()));
		for (ObjectData o: retdata) {
			checkData(o, id, name, type, ver, user, wsid, wsname,
					chksum, size, meta, data);
		}
		
		List<ObjectData> prov2 = CLIENT1.getObjects2(new GetObjects2Params()
			.withNoData(1L)
			.withObjects(toObjSpec(loi))).getData();
			assertThat("num data correct", prov2.size(), is(loi.size()));
		for (ObjectData p: prov2) {
			checkInfo(p.getInfo(), id, name, type, ver, user, wsid, wsname,
					chksum, size, meta);
			assertNull("got unrequested data", p.getData());
		}
		
		List<us.kbase.workspace.ObjectProvenanceInfo> prov =
				CLIENT1.getObjectProvenance(loi);
		assertThat("num prov correct", prov.size(), is(loi.size()));
		for (us.kbase.workspace.ObjectProvenanceInfo p: prov) {
			checkInfo(p.getInfo(), id, name, type, ver, user, wsid, wsname,
					chksum, size, meta);
		}
		
		//obj info 3 with metadata
		GetObjectInfo3Results info3 = CLIENT1.getObjectInfo3(new GetObjectInfo3Params()
				.withObjects(toObjSpec(loi)).withIncludeMetadata(1L)
				.withIgnoreErrors(0L));
		List<Tuple11<Long, String, String, String, Long, String, Long, String,
				String, Long, Map<String, String>>> retusermeta = info3.getInfos();

		assertThat("num usermeta correct", retusermeta.size(), is(loi.size()));
		for (int i = 0; i < retusermeta.size(); i ++) {
			Tuple11<Long, String, String, String, Long, String, Long,
				String, String, Long, Map<String, String>> o = retusermeta.get(i);
			checkInfo(o, id, name, type, ver, user, wsid, wsname, chksum, size, meta);
			assertThat("path incorrect", info3.getPaths().get(i),
					is(Arrays.asList(o.getE7() + "/" + o.getE1() + "/" + o.getE5())));
		}
		
		//obj info new with metadata
		retusermeta = CLIENT1.getObjectInfoNew(new us.kbase.workspace.GetObjectInfoNewParams()
						.withObjects(toObjSpec(loi)).withIncludeMetadata(1L)
						.withIgnoreErrors(0L));

		assertThat("num usermeta correct", retusermeta.size(), is(loi.size()));
		for (Tuple11<Long, String, String, String, Long, String, Long,
				String, String, Long, Map<String, String>> o: retusermeta) {
			checkInfo(o, id, name, type, ver, user, wsid, wsname,
					chksum, size, meta);
		}
		
		//obj info with metadata
		retusermeta = CLIENT1.getObjectInfo(loi, 1L);
		
		assertThat("num usermeta correct", retusermeta.size(), is(loi.size()));
		for (Tuple11<Long, String, String, String, Long, String, Long,
				String, String, Long, Map<String, String>> o: retusermeta) {
			checkInfo(o, id, name, type, ver, user, wsid, wsname,
					chksum, size, meta);
		}
		
		// obj info 3 without metadata
		info3 = CLIENT1.getObjectInfo3(new us.kbase.workspace.GetObjectInfo3Params()
				.withObjects(toObjSpec(loi)));
		retusermeta = info3.getInfos();

		assertThat("num usermeta correct", retusermeta.size(), is(loi.size()));
		for (int i = 0; i < retusermeta.size(); i ++) {
			Tuple11<Long, String, String, String, Long, String, Long,
			String, String, Long, Map<String, String>> o = retusermeta.get(i);
			checkInfo(o, id, name, type, ver, user, wsid, wsname, chksum, size, null);
			assertThat("path incorrect", info3.getPaths().get(i),
					is(Arrays.asList(o.getE7() + "/" + o.getE1() + "/" + o.getE5())));
		}

		// obj info new without metadata
		retusermeta = CLIENT1.getObjectInfoNew(new us.kbase.workspace.GetObjectInfoNewParams()
			.withObjects(toObjSpec(loi)));

		assertThat("num usermeta correct", retusermeta.size(), is(loi.size()));
		for (Tuple11<Long, String, String, String, Long, String, Long,
				String, String, Long, Map<String, String>> o: retusermeta) {
			checkInfo(o, id, name, type, ver, user, wsid, wsname,
					chksum, size, null);
		}
		
		// obj info without metadata
		retusermeta = CLIENT1.getObjectInfo(loi, 0L);

		assertThat("num usermeta correct", retusermeta.size(), is(loi.size()));
		for (Tuple11<Long, String, String, String, Long, String, Long,
				String, String, Long, Map<String, String>> o: retusermeta) {
			checkInfo(o, id, name, type, ver, user, wsid, wsname,
					chksum, size, null);
		}
	}

	@SuppressWarnings("deprecation")
	protected List<us.kbase.workspace.SubObjectIdentity> objIDToSubObjID(
			List<ObjectIdentity> loi) {
		LinkedList<us.kbase.workspace.SubObjectIdentity> ret =
				new LinkedList<us.kbase.workspace.SubObjectIdentity>();
		for (ObjectIdentity oi: loi) {
			us.kbase.workspace.SubObjectIdentity soi =
					new us.kbase.workspace.SubObjectIdentity().withName(oi.getName())
					.withObjid(oi.getObjid()).withRef(oi.getRef())
					.withVer(oi.getVer()).withWorkspace(oi.getWorkspace())
					.withWsid(oi.getWsid());
			for (Entry<String, Object> e: oi.getAdditionalProperties().entrySet()) {
				soi.setAdditionalProperties(e.getKey(), e.getValue());
			}
			ret.add(soi);
		}
		return ret;
	}

	protected void compareData(List<ObjectData> expected, List<ObjectData> got) 
			throws Exception {
		
		assertThat("same number of ObjectData", got.size(), is(expected.size()));
		Iterator<ObjectData> eIter = expected.iterator();
		Iterator<ObjectData> gIter = got.iterator();
		while (eIter.hasNext()) {
			ObjectData exp = eIter.next();
			ObjectData gt = gIter.next();
			
			compareObjectInfo(gt.getInfo(), exp.getInfo());
			assertThat("object data is correct", gt.getData().asClassInstance(Object.class),
					is(exp.getData().asClassInstance(Object.class)));
			assertThat("creator same", gt.getCreator(), is(exp.getCreator()));
			assertThat("created same", gt.getCreated(), is(exp.getCreated()));
			assertThat("prov same", gt.getProvenance(), is(exp.getProvenance()));
			assertThat("refs same", gt.getRefs(), is(exp.getRefs()));
		}
	}
	
	protected void compareInfo(
			List<Tuple11<Long, String, String, String, Long, String, Long,
			String, String, Long, Map<String, String>>> info,
			List<ObjectData> exp) throws Exception {
		
		assertThat("not same number of ObjectInfos", info.size(), is(exp.size()));
		Iterator<ObjectData> eIter = exp.iterator();
		Iterator<Tuple11<Long, String, String, String, Long, String, Long,
			String, String, Long, Map<String, String>>> gIter = info.iterator();
		while (eIter.hasNext()) {
			ObjectData e = eIter.next();
			Tuple11<Long, String, String, String, Long, String, Long, String,
				String, Long, Map<String, String>> gt = gIter.next();
			compareObjectInfo(gt, e.getInfo());
		}
	}
	
	protected void checkData(ObjectData retdata, long id, String name,
			String typeString, int ver, String user, long wsid, String wsname,
			String chksum, long size, Map<String, String> meta, Map<String, Object> data) 
			throws Exception {
		
		assertThat("object data incorrect", retdata.getData().asClassInstance(Object.class),
				is((Object) data));
		assertThat("incorrect object path", retdata.getPath(),
				is(Arrays.asList(wsid + "/" + id + "/" + ver)));
		
		checkInfo(retdata.getInfo(), id, name, typeString, ver, user,
				wsid, wsname, chksum, size, meta);
	}

	protected void checkInfo(
			Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> infousermeta,
			long id, String name, String typeString, int ver, String user,
			long wsid, String wsname, String chksum, long size, Map<String, String> meta)
			throws Exception {
		
		assertThat("id is correct", infousermeta.getE1(), is(id));
		assertThat("name is correct", infousermeta.getE2(), is(name));
		assertThat("type is correct", infousermeta.getE3(), is(typeString));
		DATE_FORMAT.parse(infousermeta.getE4()); //should throw error if bad format
		assertThat("version is correct", (int) infousermeta.getE5().longValue(), is(ver));
		assertThat("user is correct", infousermeta.getE6(), is(user));
		assertThat("wsid is correct", infousermeta.getE7(), is(wsid));
		assertThat("ws name is correct", infousermeta.getE8(), is(wsname));
		assertThat("chksum is correct", infousermeta.getE9(), is(chksum));
		assertThat("size is correct", infousermeta.getE10(), is(size));
		assertThat("meta is correct", infousermeta.getE11(), is(meta));
	}
	
	protected static Thread watchForMem(final String header, final boolean[] threadStopWrapper) {
		Thread ret = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					Runtime r = Runtime.getRuntime();
					long freeMem = r.freeMemory();
					long totalMem = r.totalMemory();
					long usedMem = totalMem - freeMem;
					System.out.println(header + ". used: " + usedMem + " total: " + totalMem);
					if (threadStopWrapper[0])
						break;
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		});
		ret.start();
		return ret;
	}
	
	protected static void waitForGC(String header, long maxUsedMem) throws InterruptedException {
		while (true) {
			long freeMem = Runtime.getRuntime().freeMemory();
			long totalMem = Runtime.getRuntime().totalMemory();
			long usedMem = totalMem - freeMem;
			System.out.println(header + ". used: " + usedMem + " total: " + totalMem);
			if (usedMem < maxUsedMem)
				break;
			System.gc();
			Thread.sleep(1000);
		}
	}

	@SuppressWarnings("deprecation")
	protected void checkData(List<ObjectIdentity> loi, Map<String, Object> data)
			throws Exception {
		assertThat("expected loi size is 1", loi.size(), is(1));
		assertThat("can get data", CLIENT1.getObjects2(new GetObjects2Params()
				.withObjects(toObjSpec(loi))).getData().get(0).getData()
				.asClassInstance(Object.class), is((Object) data));
		assertThat("can get data", CLIENT1.getObjects(loi).get(0).getData()
				.asClassInstance(Object.class), is((Object) data));
		assertThat("can get data", CLIENT1.getObjectSubset(objIDToSubObjID(loi))
				.get(0).getData().asClassInstance(Object.class), is((Object) data));
	}

	@SuppressWarnings("deprecation")
	protected void compareObjectInfoAndData(
			Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> orig,
			Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> copied,
			String wsname, long wsid, String name, long id, int ver) 
			throws Exception {
		compareObjectInfo(orig, copied, wsname, wsid, name, id, ver);
		
		List<ObjectIdentity> loi = Arrays.asList(new ObjectIdentity().withWsid(orig.getE7())
				.withObjid(orig.getE1()).withVer(orig.getE5()), 
				new ObjectIdentity().withWsid(copied.getE7())
				.withObjid(copied.getE1()).withVer(copied.getE5()));
		
		String expectedCopy = orig.getE7() + "/" + orig.getE1() + "/" + orig.getE5();
		
		List<ObjectData> prov2 = CLIENT1.getObjects2(new GetObjects2Params()
				.withObjects(toObjSpec(loi)).withNoData(1L)).getData();
		compareObjectInfo(prov2.get(0).getInfo(), prov2.get(1).getInfo(), wsname, wsid, name, id, ver);
		assertThat("creator same", prov2.get(1).getCreator(), is(prov2.get(0).getCreator()));
		assertThat("created same", prov2.get(1).getCreated(), is(prov2.get(0).getCreated()));
		assertThat("prov same", prov2.get(1).getProvenance(), is(prov2.get(0).getProvenance()));
		assertThat("refs same", prov2.get(1).getRefs(), is(prov2.get(0).getRefs()));
		assertThat("copy ref correct", prov2.get(1).getCopied(), is(expectedCopy));
		assertThat("copy visibility correct", prov2.get(1).getCopySourceInaccessible(), is(0L));
		assertNull("got unrequested data", prov2.get(0).getData());
		assertNull("got unrequested data", prov2.get(1).getData());
		
		List<us.kbase.workspace.ObjectProvenanceInfo> prov = CLIENT1.getObjectProvenance(loi);
		compareObjectInfo(prov.get(0).getInfo(), prov.get(1).getInfo(), wsname, wsid, name, id, ver);
		assertThat("creator same", prov.get(1).getCreator(), is(prov.get(0).getCreator()));
		assertThat("created same", prov.get(1).getCreated(), is(prov.get(0).getCreated()));
		assertThat("prov same", prov.get(1).getProvenance(), is(prov.get(0).getProvenance()));
		assertThat("refs same", prov.get(1).getRefs(), is(prov.get(0).getRefs()));
		assertThat("copy ref correct", prov.get(1).getCopied(), is(expectedCopy));
		assertThat("copy visibility correct", prov.get(1).getCopySourceInaccessible(), is(0L));
		
		List<ObjectData> objs = CLIENT1.getObjects2(new GetObjects2Params()
				.withObjects(toObjSpec(loi))).getData();
		compareObjectInfo(objs.get(0).getInfo(), objs.get(1).getInfo(), wsname, wsid, name, id, ver);
		assertThat("creator same", objs.get(1).getCreator(), is(objs.get(0).getCreator()));
		assertThat("created same", objs.get(1).getCreated(), is(objs.get(0).getCreated()));
		assertThat("data same", objs.get(1).getData().asClassInstance(Map.class),
				is(objs.get(0).getData().asClassInstance(Map.class)));
		assertThat("prov same", objs.get(1).getProvenance(), is(objs.get(0).getProvenance()));
		assertThat("refs same", objs.get(1).getRefs(), is(objs.get(0).getRefs()));
		assertThat("copy ref correct", objs.get(1).getCopied(), is(expectedCopy));
		assertThat("copy visibility correct", objs.get(1).getCopySourceInaccessible(), is(0L));
		
		objs = CLIENT1.getObjects(loi);
		compareObjectInfo(objs.get(0).getInfo(), objs.get(1).getInfo(), wsname, wsid, name, id, ver);
		assertThat("creator same", objs.get(1).getCreator(), is(objs.get(0).getCreator()));
		assertThat("created same", objs.get(1).getCreated(), is(objs.get(0).getCreated()));
		assertThat("data same", objs.get(1).getData().asClassInstance(Map.class),
				is(objs.get(0).getData().asClassInstance(Map.class)));
		assertThat("prov same", objs.get(1).getProvenance(), is(objs.get(0).getProvenance()));
		assertThat("refs same", objs.get(1).getRefs(), is(objs.get(0).getRefs()));
		assertThat("copy ref correct", objs.get(1).getCopied(), is(expectedCopy));
		assertThat("copy visibility correct", objs.get(1).getCopySourceInaccessible(), is(0L));
		
		objs = CLIENT1.getObjectSubset(objIDToSubObjID(loi));
		compareObjectInfo(objs.get(0).getInfo(), objs.get(1).getInfo(), wsname, wsid, name, id, ver);
		assertThat("creator same", objs.get(1).getCreator(), is(objs.get(0).getCreator()));
		assertThat("created same", objs.get(1).getCreated(), is(objs.get(0).getCreated()));
		assertThat("data same", objs.get(1).getData().asClassInstance(Map.class),
				is(objs.get(0).getData().asClassInstance(Map.class)));
		assertThat("prov same", objs.get(1).getProvenance(), is(objs.get(0).getProvenance()));
		assertThat("refs same", objs.get(1).getRefs(), is(objs.get(0).getRefs()));
		assertThat("copy ref correct", objs.get(1).getCopied(), is(expectedCopy));
		assertThat("copy visibility correct", objs.get(1).getCopySourceInaccessible(), is(0L));
	}

	protected void compareObjectInfo(
			Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> orig,
			Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> copied,
			String wsname, long wsid, String name, long id, int ver) 
			throws Exception {
		assertThat("id is correct", copied.getE1(), is(id));
		assertThat("name is correct", copied.getE2(), is(name));
		assertThat("type is correct", copied.getE3(), is(orig.getE3()));
		DATE_FORMAT.parse(copied.getE4()); //should throw error if bad format
		assertThat("version is correct", (int) copied.getE5().longValue(), is(ver));
		assertThat("user is correct", copied.getE6(), is(orig.getE6()));
		assertThat("wsid is correct", copied.getE7(), is(wsid));
		assertThat("ws name is correct", copied.getE8(), is(wsname));
		assertThat("chksum is correct", copied.getE9(), is(orig.getE9()));
		assertThat("size is correct", copied.getE10(), is(orig.getE10()));
		assertThat("meta is correct", copied.getE11(), is(orig.getE11()));
	}

	protected void failObjRename(RenameObjectParams rop,
			String excep) throws Exception {
		try {
			CLIENT1.renameObject(rop);
			fail("renamed with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(excep));
		}
	}

	protected void failWSRename(RenameWorkspaceParams rwp,
			String excep) throws Exception {
		try {
			CLIENT1.renameWorkspace(rwp);
			fail("renamed with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(excep));
		}
	}
	
	protected void failGetWSDesc(final WorkspaceIdentity wsi, final String exp) throws Exception {
		try {
			CLIENT1.getWorkspaceDescription(wsi);
			fail("got desc from WS when expected failure");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(), is(exp));
		}
	}

	protected void failSetGlobalPerm(SetGlobalPermissionsParams sgpp,
			String exp) throws Exception {
		try {
			CLIENT1.setGlobalPermission(sgpp);
			fail("set global perms with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}

	protected void failHideUnHide(ObjectIdentity badoi, String exp)
			throws Exception {
		try {
			CLIENT1.hideObjects(Arrays.asList(badoi));
			fail("hide obj with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
		try {
			CLIENT1.unhideObjects(Arrays.asList(badoi));
			fail("unhide obj with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}

	protected void checkExpectedObjNums(
			List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> objs,
			Set<Long> expected) {
		Set<Long> got = new HashSet<Long>();
		for (Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> o: objs) {
			if (got.contains(o.getE1())) {
				fail("Say same object twice");
			}
			got.add(o.getE1());
		}
		assertThat("correct object ids in list", got, is(expected));
	}
	
	protected void failListWorkspaceByDate(String date, String exception) throws Exception {
		try {
			CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withAfter(date));
			fail("listed workspace info with bad date");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
		try {
			CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withBefore(date));
			fail("listed workspace info with bad date");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
	}
	
	protected long addSec(String time) throws Exception {
		return DATE_FORMAT.parse(time).getTime() + 1000;
	}
	
	protected long subSec(String time) throws Exception {
		return DATE_FORMAT.parse(time).getTime() - 1000;
	}

	protected void checkWSInfoList(
			List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> got,
			List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> expected) {
		checkWSInfoList(got, expected, false);
	}
	
	protected void checkWSInfoList(
			List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> got,
			List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> expected,
			boolean testDates) {
		
		assertThat("got expected number of workspaces", got.size(), is(expected.size()));
		Map<Long, Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> expecmap = 
				new HashMap<Long, Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>>();
		for (Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> inf: expected) {
			expecmap.put(inf.getE1(), inf);
		}
		Set<Long> seen = new HashSet<Long>();
		for (Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info: got) {
			if (seen.contains(info.getE1())) {
				fail("Saw same workspace twice");
			}
			Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> exp =
					expecmap.get(info.getE1());
			assertThat("ids correct", info.getE1(), is(exp.getE1()));
			if (testDates) {
				assertThat("moddates correct", info.getE4(), is(exp.getE4())); 
			}
			assertThat("ws name correct", info.getE2(), is(exp.getE2()));
			assertThat("user name correct", info.getE3(), is(exp.getE3()));
			assertThat("obj counts correct", info.getE5(), is(exp.getE5()));
			assertThat("permission correct", info.getE6(), is(exp.getE6()));
			assertThat("global read correct", info.getE7(), is(exp.getE7()));
			assertThat("lockstate correct", info.getE8(), is(exp.getE8()));
			
		}
	}

	protected void checkObjectPagination(String wsname,
			Long limit, int minid, int maxid) 
			throws Exception {
		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> res =
				CLIENT1.listObjects(new ListObjectsParams()
				.withWorkspaces(Arrays.asList(wsname))
				.withLimit(limit));
				
		assertThat("correct number of objects returned", res.size(), is(maxid - minid + 1));
		for (Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> oi: res) {
			if (oi.getE1() < minid || oi.getE1() > maxid) {
				fail(String.format("ObjectID out of test bounds: %s min %s max %s",
						oi.getE1(), minid, maxid));
			}
		}
	}
	
	protected void failListObjectsByDate(String ws, String date, String exception) throws Exception {
		try {
			CLIENT1.listObjects(new ListObjectsParams().withAfter(date).withWorkspaces(Arrays.asList(ws)));
			fail("listed obj info with bad date");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
		try {
			CLIENT1.listObjects(new ListObjectsParams().withBefore(date).withWorkspaces(Arrays.asList(ws)));
			fail("listed obj info with bad date");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
	}
	
	protected void compareObjectInfo(
			List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> got,
			List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> expected)
			throws Exception {
		compareObjectInfo(got, expected, true);
	}

	protected void compareObjectInfo(
			List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> got,
			List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> expected,
			boolean compareOrder)
			throws Exception {
		assertThat("same number of objects", got.size(), is(expected.size()));
		Iterator<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> gotiter =
				got.iterator();
		Iterator<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> expiter =
				expected.iterator();
		if (compareOrder) {
			while (gotiter.hasNext()) {
				compareObjectInfo(gotiter.next(), expiter.next(), false);
			}
			return;
		}
		Set<ObjectInformation> g = tupleObjInfoToObjInfo(got);
		Set<ObjectInformation> e = tupleObjInfoToObjInfo(expected);
		assertThat("got same unordered objects", g, is(e));
		
	}

	protected Set<ObjectInformation> tupleObjInfoToObjInfo(
			List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> tpl)
			throws Exception {
		Set<ObjectInformation> s = new HashSet<>();
		for (Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> t: tpl) {
			s.add(new ObjectInformation(t.getE1(), t.getE2(), t.getE3(), DATE_FORMAT.parse(t.getE4()),
					t.getE5().intValue(), new WorkspaceUser(t.getE6()), 
					new ResolvedWorkspaceID(t.getE7(), t.getE8(), false, false), t.getE9(),
					t.getE10(), new UncheckedUserMetadata(t.getE11())));
		}
		return s;
	}
	
	protected void failListObjects(List<String> wsnames, List<Long> wsids,
			String type, String perm, Map<String, String> meta, Long showHidden,
			Long showDeleted, Long allVers, Long includeMeta, String exp)
			throws Exception {
		failListObjects(wsnames, wsids, type, perm, meta, showHidden,
				showDeleted, allVers, includeMeta, -1, exp);
	}

	protected void failListObjects(List<String> wsnames, List<Long> wsids,
			String type, String perm, Map<String, String> meta, Long showHidden,
			Long showDeleted, Long allVers, Long includeMeta, long limit, String exp)
			throws Exception {
		try {
			CLIENT1.listObjects(new ListObjectsParams().withWorkspaces(wsnames)
					.withIds(wsids).withType(type).withShowHidden(showHidden)
					.withShowDeleted(showDeleted).withShowAllVersions(allVers)
					.withIncludeMetadata(includeMeta).withPerm(perm).withMeta(meta)
					.withLimit(limit));
			fail("listed objects with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}

	protected void checkListObjects(List<String> wsnames, List<Long> wsids, String type, String perm,
			List<String> savedby, Map<String, String> meta, Long showHidden,
			Long showDeleted, Long showOnlyDeleted, Long allVers, Long includeMeta, Long excludeGlobal,
			List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> expected,
			boolean nullMeta) throws Exception {
		Map<Long, Map<Long, Map<Long, Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>>>> expec =
				new HashMap<Long, Map<Long, Map<Long, Tuple11<Long,String,String,String,Long,String,Long,String,String,Long,Map<String,String>>>>>();
		
		Map<Long, Map<Long, Set<Long>>> seenSet = new HashMap<Long, Map<Long, Set<Long>>>();
		Map<Long, Map<Long, Set<Long>>> expectedSet = new HashMap<Long, Map<Long, Set<Long>>>();
		
		for (Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> e: expected) {
			if (!expec.containsKey(e.getE7())) {
				expec.put(e.getE7(), new HashMap<Long, Map<Long, Tuple11<Long,String,String,String,Long,String,Long,String,String,Long,Map<String,String>>>>());
				expectedSet.put(e.getE7(), new HashMap<Long, Set<Long>>());
			}
			if (!expec.get(e.getE7()).containsKey(e.getE1())) {
				expec.get(e.getE7()).put(e.getE1(), new HashMap<Long, Tuple11<Long,String,String,String,Long,String,Long,String,String,Long,Map<String,String>>>());
				expectedSet.get(e.getE7()).put(e.getE1(), new HashSet<Long>());
			}
			expec.get(e.getE7()).get(e.getE1()).put(e.getE5(), e);
			expectedSet.get(e.getE7()).get(e.getE1()).add(e.getE5());
		}
		for (Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> g:
			CLIENT1.listObjects(new ListObjectsParams().withWorkspaces(wsnames)
					.withIds(wsids).withType(type).withShowHidden(showHidden)
					.withShowDeleted(showDeleted).withShowOnlyDeleted(showOnlyDeleted)
					.withShowAllVersions(allVers).withIncludeMetadata(includeMeta)
					.withPerm(perm).withSavedby(savedby).withMeta(meta)
					.withExcludeGlobal(excludeGlobal))) {
			if (seenSet.containsKey(g.getE7()) && seenSet.get(g.getE7()).containsKey(g.getE1()) &&
					seenSet.get(g.getE7()).get(g.getE1()).contains(g.getE5())) {
				fail("Saw same object twice: " + g);
			}
			if (!seenSet.containsKey(g.getE7())) {
				seenSet.put(g.getE7(), new HashMap<Long, Set<Long>>());
			}
			if (!seenSet.get(g.getE7()).containsKey(g.getE1())) {
				seenSet.get(g.getE7()).put(g.getE1(), new HashSet<Long>());
			}
			seenSet.get(g.getE7()).get(g.getE1()).add(g.getE5());
			if (!expec.containsKey(g.getE7()) || !expec.get(g.getE7()).containsKey(g.getE1()) ||
					!expec.get(g.getE7()).get(g.getE1()).containsKey(g.getE5())) {
				fail("listed unexpected object: " + g);
			}
			compareObjectInfo(g, expec.get(g.getE7()).get(g.getE1()).get(g.getE5()), nullMeta);
		}
		assertThat("listed correct objects", seenSet, is (expectedSet));
	}
	
	protected void compareObjectInfo(
			Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> got,
			Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> expec) 
			throws Exception {
		compareObjectInfo(got, expec, false);
	}
	
	protected void compareObjectInfo(
			Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> got,
			Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> expec,
			boolean nullMeta) 
			throws Exception {
		assertThat("id is correct", got.getE1(), is(expec.getE1()));
		assertThat("name is correct", got.getE2(), is(expec.getE2()));
		assertThat("type is correct", got.getE3(), is(expec.getE3()));
		assertThat("date is correct", got.getE4(), is(expec.getE4()));
		assertThat("version is correct", got.getE5(), is(expec.getE5()));
		assertThat("user is correct", got.getE6(), is(expec.getE6()));
		assertThat("wsid is correct", got.getE7(), is(expec.getE7()));
		assertThat("ws name is correct", got.getE8(), is(expec.getE8()));
		assertThat("chksum is correct", got.getE9(), is(expec.getE9()));
		assertThat("size is correct", got.getE10(), is(expec.getE10()));
		assertThat("meta is correct", got.getE11(), is(nullMeta ? null : expec.getE11()));
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> createData(String json)
			throws JsonParseException, JsonMappingException, IOException {
		return new ObjectMapper().readValue(json, Map.class);
	}

	@SuppressWarnings("deprecation")
	protected void failGetReferencedObjects(List<List<ObjectIdentity>> chains,
			String excep) throws Exception {
		try {
			CLIENT1.getReferencedObjects(chains);
			fail("got referenced objects with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(excep));
		}
		List<ObjectSpecification> osl = new LinkedList<ObjectSpecification>();
		List<ObjectSpecification> osr = new LinkedList<ObjectSpecification>();
		if (chains == null) {
			osl = null;
			osr = null;
		} else {
			for (List<ObjectIdentity> loi: chains) {
				if (loi == null || loi.size() < 2 || loi.get(0) == null) {
					osl.add(null);
					osr.add(null);
				} else {
					ObjectSpecification os1 = toObjSpec(loi.get(0));
					os1.withObjPath(loi.subList(1, loi.size()));
					osl.add(os1);
					
					ObjectSpecification os2 = toObjSpec(loi.get(0));
					os2.withObjRefPath(toRefs(loi.subList(1, loi.size())));
					osr.add(os2);
				}
			}
		}
		if (excep.equals("refChains may not be null")) {
			excep = "The object specification list cannot be null";
		}
		// this is super goofy but it does point out that the new way of
		// specifying chains has many fewer failure modes
		if (excep.contains("The object identifier list cannot be null") ||
			excep.contains("No object identifiers provided") ||
			excep.contains("The minimum size of a reference chain is 2 ObjectIdentities")) {
			excep = "Objects in the object specification list cannot be null";
		}
		
		// oh my god I'm disgusting and evil
		String refex = excep;
		String[] e = excep.split(":", 3);
		if (e.length == 3 && excep.startsWith("Error on")) {
			int chainnum = Integer.parseInt(e[0].substring(e[0].length() - 1));
			int oidnum = Integer.parseInt(e[1].substring(e[1].length() - 1));
			if (oidnum == 1) {
				excep = "Error on ObjectSpecification #" + chainnum + ":" +
						e[2];
				refex = excep;
			} else {
				excep = "Error on ObjectSpecification #" + chainnum + 
						": Invalid object id at position #" + oidnum +
						":" + e[2];
				String ref = osr.get(chainnum - 1).getObjRefPath()
						.get(oidnum - 2);
				if (e[2].equals(" ObjectIdentity cannot be null")) { // this is sick and wrong 
					e[2] = " reference cannot be null or the empty string";
				}
				refex = String.format("Error on ObjectSpecification #%s" + 
						": Invalid object reference (%s) at position #%s:%s",
						chainnum, ref, oidnum,
						e[2].replace("ObjectIdentity", "Reference string"));
			}
		} // i feel so ashamed
		
		failGetObjects2(osl, excep);
		failGetObjectInfo(new GetObjectInfo3Params().withObjects(osl),
				excep);
		if (excep.contains("Unexpected arguments in ObjectIdentity: foo")) {
			return; // can't have UAs in a string ref
		}
		failGetObjects2(osr, refex);
		failGetObjectInfo(new GetObjectInfo3Params().withObjects(osr),
				refex);
	}

	private void failGetObjects2(
			final List<ObjectSpecification> oss,
			final String excep)
			throws Exception {
		try {
			CLIENT1.getObjects2(new GetObjects2Params().withObjects(oss));
			fail("got objects with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(excep));
		}
	}

	private List<String> toRefs(List<ObjectIdentity> loi) {
		List<String> refs = new LinkedList<String>();
		for (ObjectIdentity oi: loi) {
			if (oi == null) {
				refs.add(null);
			} else {
				refs.add(toRefs(oi));
			}
		}
		return refs;
	}

	private String toRefs(ObjectIdentity oi) {
		if (oi.getRef() != null) {
			return oi.getRef();
		}
		String ref = "";
		if (oi.getWorkspace() != null) {
			ref += oi.getWorkspace();
		} else {
			ref += oi.getWsid();
		}
		ref += "/";
		if (oi.getName() != null) {
			ref += oi.getName();
		} else {
			ref += oi.getObjid();
		}
		if (oi.getVer() != null) {
			ref += "/" + oi.getVer();
		}
		return ref;
	}

	protected void checkAdmins(WorkspaceClient cli, List<String> expadmins)
			throws Exception {
		List<String> admins = cli.administer(new UObject(createData(
				"{\"command\": \"listAdmins\"}"))).asInstance();
		Set<String> got = new HashSet<String>(admins);
		Set<String> expected = new HashSet<String>(expadmins);
		assertThat("correct admins", got, is(expected));
		
	}
	
	protected void failAdmin(WorkspaceClient cli, String cmd, String exp)
			throws Exception {
		failAdmin(cli, createData(cmd), exp);
	}
		
	protected void failAdmin(
			final WorkspaceClient cli,
			final Map<String, Object> cmd,
			final String exp)
			throws Exception {
		try {
			cli.administer(new UObject(cmd));
			fail("ran admin command with bad params");
		} catch (ServerException se) {
			assertServerExceptionCorrect(se, exp);
		}
	}

	private void assertServerExceptionCorrect(final ServerException se, final String exp) {
		assertThat("incorrect exception message. Client side:\n"
				+ ExceptionUtils.getStackTrace(se) +
				"\nServer side:\n" + se.getData(),
				se.getLocalizedMessage(), is(exp));
		
	}

	protected void checkModRequests(Map<String, String> mod2owner)
			throws Exception {
		List<Map<String,Object>> reqs = CLIENT2.administer(new UObject(createData(
				"{\"command\": \"listModRequests\"}"))).asInstance();
		Map<String, String> gotMods = new HashMap<String, String>();
		for (Map<String, Object> r: reqs) {
			gotMods.put((String) r.get("moduleName"), (String) r.get("ownerUserId"));
		}
		assertThat("module req list ok", gotMods, is(mod2owner));
		
	}

	protected Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> list2ObjTuple11(
			List<Object> got) {
		@SuppressWarnings("unchecked")
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> ret =
				new Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>()
				.withE1(new Long((Integer) got.get(0)))
				.withE2((String) got.get(1))
				.withE3((String) got.get(2))
				.withE4((String) got.get(3))
				.withE5(new Long((Integer) got.get(4)))
				.withE6((String) got.get(5))
				.withE7(new Long((Integer) got.get(6)))
				.withE8((String) got.get(7))
				.withE9((String) got.get(8))
				.withE10(new Long((Integer) got.get(9)))
				.withE11((Map<String, String>) got.get(10));
		return ret;
	}

	protected Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> list2WSTuple9(
			List<Object> got) {
		@SuppressWarnings("unchecked")
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> ret =
				new Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>()
				.withE1(new Long((Integer) got.get(0)))
				.withE2((String) got.get(1))
				.withE3((String) got.get(2))
				.withE4((String) got.get(3))
				.withE5(new Long((Integer) got.get(4)))
				.withE6((String) got.get(5))
				.withE7((String) got.get(6))
				.withE8((String) got.get(7))
				.withE9((Map<String, String>) got.get(8));
		return ret;
	}
	
	protected void failAlterWSMeta(WorkspaceClient cli, AlterWorkspaceMetadataParams awmp,
			String excep) throws Exception {
		try {
			cli.alterWorkspaceMetadata(awmp);
			fail("altered meta with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(excep));
		}
	}
}
