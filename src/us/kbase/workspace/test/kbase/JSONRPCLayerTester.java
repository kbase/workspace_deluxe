package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
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

import org.apache.commons.lang3.StringUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

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
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.workspace.AlterWorkspaceMetadataParams;
import us.kbase.workspace.ExternalDataUnit;
import us.kbase.workspace.GetObjectInfoNewParams;
import us.kbase.workspace.ListObjectsParams;
import us.kbase.workspace.ListWorkspaceInfoParams;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectProvenanceInfo;
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
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.test.JsonTokenStreamOCStat;
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspace.test.workspace.FakeObjectInfo;
import us.kbase.workspace.test.workspace.FakeResolvedWSID;

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
	public static final String REF_TYPE ="RefSpec.Ref-0.1";
	
	public static final Map<String, String> MT_META =
			new HashMap<String, String>();
	
	private static List<TempFilesManager> tfms =
			new LinkedList<TempFilesManager>();;
	
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
	
	//http://quirkygba.blogspot.com/2009/11/setting-environment-variables-in-java.html
	@SuppressWarnings("unchecked")
	protected static Map<String, String> getenv() throws NoSuchFieldException,
			SecurityException, IllegalArgumentException, IllegalAccessException {
		Map<String, String> unmodifiable = System.getenv();
		Class<?> cu = unmodifiable.getClass();
		Field m = cu.getDeclaredField("m");
		m.setAccessible(true);
		return (Map<String, String>) m.get(unmodifiable);
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
		
		WorkspaceTestCommon.stfuLoggers();
		mongo = new MongoController(WorkspaceTestCommon.getMongoExe(),
				Paths.get(WorkspaceTestCommon.getTempDir()));
		System.out.println("Using mongo temp dir " + mongo.getTempDir());
		
		final String mongohost = "localhost:" + mongo.getServerPort();
		MongoClient mongoClient = new MongoClient(mongohost);
		
		SERVER1 = startupWorkspaceServer(mongohost,
				mongoClient.getDB("JSONRPCLayerTester1"), 
				"JSONRPCLayerTester1_types");
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
		CLIENT1.setIsInsecureHttpConnectionAllowed(true);
		CLIENT2.setIsInsecureHttpConnectionAllowed(true);
		CLIENT_NO_AUTH.setIsInsecureHttpConnectionAllowed(true);
		CLIENT1.setStreamingModeOn(true); //for JSONRPCLayerLongTest
		
		//set up a basic type for test use that doesn't worry about type checking
		CLIENT1.requestModuleOwnership("SomeModule");
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
		
		SERVER2 = startupWorkspaceServer(mongohost,
				mongoClient.getDB("JSONRPCLayerTester2"), 
				"JSONRPCLayerTester2_types");
		System.out.println("Started test server 2 on port " + SERVER2.getServerPort());
		WorkspaceClient clientForSrv2 = new WorkspaceClient(new URL("http://localhost:" + 
				SERVER2.getServerPort()), USER2, p2);
		clientForSrv2.setIsInsecureHttpConnectionAllowed(true);
		clientForSrv2.requestModuleOwnership("SomeModule");
		administerCommand(clientForSrv2, "approveModRequest", "module", "SomeModule");
		clientForSrv2.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("module SomeModule {/* @optional thing */ typedef structure {int thing;} AType;};")
			.withNewTypes(Arrays.asList("AType")));
		clientForSrv2.releaseModule("SomeModule");
		clientForSrv2.requestModuleOwnership("DepModule");
		administerCommand(clientForSrv2, "approveModRequest", "module", "DepModule");
		clientForSrv2.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("#include <SomeModule>\n" +
					"module DepModule {typedef structure {SomeModule.AType thing;} BType;};")
			.withNewTypes(Arrays.asList("BType")));
		clientForSrv2.releaseModule("DepModule");
		clientForSrv2.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("module SomeModule {/* @optional thing */ typedef structure {string thing;} AType;};")
			.withNewTypes(Collections.<String>emptyList()));
		clientForSrv2.releaseModule("SomeModule");
		clientForSrv2.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("#include <SomeModule>\n" +
					"module DepModule {typedef structure {SomeModule.AType thing;} BType;};")
			.withNewTypes(Collections.<String>emptyList()));
		clientForSrv2.releaseModule("DepModule");
		clientForSrv2.requestModuleOwnership("UnreleasedModule");
		administerCommand(clientForSrv2, "approveModRequest", "module", "UnreleasedModule");
		clientForSrv2.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("module UnreleasedModule {typedef int AType; funcdef aFunc(AType param) returns ();};")
			.withNewTypes(Arrays.asList("AType")));
		CLIENT_FOR_SRV2 = clientForSrv2;
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
		ini.store(iniFile);
		iniFile.deleteOnExit();
		
		//set up env
		Map<String, String> env = getenv();
		env.put("KB_DEPLOYMENT_CONFIG", iniFile.getAbsolutePath());
		env.put("KB_SERVICE_NAME", "Workspace");

		WorkspaceServer.setIgnoreHandleServiceForTests(true);
		WorkspaceServer.clearConfigForTests();
		WorkspaceServer.setMaximumUniqueIdCountForTests(MAX_UNIQUE_IDS_PER_CALL);
		WorkspaceServer server = new WorkspaceServer();
		//as of 3/10/14 out of 64 objects this would force 15 to be written as temp files
		server.setResourceUsageConfiguration(
				new ResourceUsageConfigurationBuilder(
						server.getWorkspaceResourceUsageConfig())
				.withMaxIncomingDataMemoryUsage(24)
				.withMaxReturnedDataMemoryUsage(24).build());
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
		if (mongo != null) {
			System.out.println("destroying mongo temp files");
			mongo.destroy(WorkspaceTestCommon.getDeleteTempFiles());
		}
		JsonTokenStreamOCStat.showStat();
	}
	
	public static void assertNoTempFilesExist(TempFilesManager tfm)
			throws Exception {
		assertNoTempFilesExist(Arrays.asList(tfm));
	}
	
	
	public static void assertNoTempFilesExist(List<TempFilesManager> tfms)
			throws Exception {
		int i = 0;
		try {
			for (TempFilesManager tfm: tfms) {
				if (!tfm.isEmpty()) {
					// Allow <=10 seconds to finish all activities
					for (; i < 100; i++) {
						Thread.sleep(100);
						if (tfm.isEmpty())
							break;
					}
				}
				assertThat("There are tempfiles: " + tfm.getTempFileList(), tfm.isEmpty(), is(true));
			}
		} finally {
			for (TempFilesManager tfm: tfms)
				tfm.cleanup();
		}
	}
	
	@After
	public void cleanupTempFilesAfterTest() throws Exception {
		assertNoTempFilesExist(tfms);
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

	protected void saveBadObject(List<ObjectSaveData> objects, String exception) 
			throws Exception {
		try {
			CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("savebadpkg")
					.withObjects(objects));
			fail("saved invalid data package");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is(exception));
		}
	}
	
	protected void checkProvenance(String user, ObjectIdentity id,
			List<ProvenanceAction> prov, Map<String, String> refmap,
			Map<String, String> timemap) throws Exception {
		List<ObjectData> ret = CLIENT1.getObjects(Arrays.asList(id));
		assertThat("user correct", ret.get(0).getCreator(), is(user));
		assertTrue("created within last 10 mins", 
				DATE_FORMAT.parse(ret.get(0).getCreated())
				.after(getOlderDate(10 * 60 * 1000)));
		checkProvenance(prov, ret.get(0).getProvenance(), refmap, timemap);
		
		List<ObjectProvenanceInfo> p = CLIENT1.getObjectProvenance(Arrays.asList(id));
		assertThat("user correct", p.get(0).getCreator(), is(user));
		assertTrue("created within last 10 mins", 
				DATE_FORMAT.parse(p.get(0).getCreated())
				.after(getOlderDate(10 * 60 * 1000)));
		checkProvenance(prov, p.get(0).getProvenance(), refmap, timemap);
		
		ret = CLIENT1.getObjectSubset(objIDToSubObjID(Arrays.asList(id)));
		assertThat("user correct", ret.get(0).getCreator(), is(user));
		assertTrue("created within last 10 mins", 
				DATE_FORMAT.parse(ret.get(0).getCreated())
				.after(getOlderDate(10 * 60 * 1000)));
		checkProvenance(prov, ret.get(0).getProvenance(), refmap, timemap);
	}
	
	protected Date getOlderDate(long ms) {
		long now = new Date().getTime();
		return new Date(now - ms);
	}
	
	protected void saveProvWithBadTime(String time, String exception) throws Exception {
		UObject data = new UObject(new HashMap<String, Object>());
		SaveObjectsParams sop = new SaveObjectsParams().withWorkspace("provenance")
				.withObjects(Arrays.asList(
						new ObjectSaveData().withData(data).withType(SAFE_TYPE)
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
				.withData(data).withType(SAFE_TYPE)
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
			Map<String, String> timemap) {
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
			assertThat("time equal", gotpa.getTime(), is(timemap.get(exppa.getTime())));
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

	private void checkProvenanceExternalData(
			List<ExternalDataUnit> got,
			List<ExternalDataUnit> exp, Map<String, String> timemap) {
		if (exp == null) {
			assertThat("prov eternal data empty", got.size(), is(0));
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
			assertThat("same resource rel date", g.getResourceReleaseDate(),
					is (timemap.get(e.getResourceReleaseDate())));
			assertThat("same resource url", g.getResourceUrl(), is (e.getResourceUrl()));
			assertThat("same resource ver", g.getResourceVersion(), is (e.getResourceVersion()));
		}
		
	}

	protected void failGetObjectInfoNew(GetObjectInfoNewParams params, String exception)
			throws Exception {
		try {
			CLIENT1.getObjectInfoNew(params);
			fail("got object with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
	}

	@SuppressWarnings("deprecation")
	protected void failGetObjects(List<ObjectIdentity> loi, String exception)
			throws Exception {
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
			CLIENT1.getObjectInfoNew(new GetObjectInfoNewParams().withObjects(loi));
			fail("got info with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
		//deprecated, remove when removed from code.
		try {
			CLIENT1.getObjectInfo(loi, 0L);
			fail("got info with bad id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
		try {
			CLIENT1.listReferencingObjects(loi);
			fail("got referring objs with bad id");
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
		List<ObjectData> retdata = CLIENT1.getObjects(loi);
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
		
		List<ObjectProvenanceInfo> prov = CLIENT1.getObjectProvenance(loi);
		assertThat("num prov correct", prov.size(), is(loi.size()));
		for (ObjectProvenanceInfo p: prov) {
			checkInfo(p.getInfo(), id, name, type, ver, user, wsid, wsname,
					chksum, size, meta);
		}
		
		List<Tuple11<Long, String, String, String, Long, String, Long, String,
				String, Long, Map<String, String>>> retusermeta =
				CLIENT1.getObjectInfoNew(new GetObjectInfoNewParams()
						.withObjects(loi).withIncludeMetadata(1L));

		assertThat("num usermeta correct", retusermeta.size(), is(loi.size()));
		for (Tuple11<Long, String, String, String, Long, String, Long,
				String, String, Long, Map<String, String>> o: retusermeta) {
			checkInfo(o, id, name, type, ver, user, wsid, wsname,
					chksum, size, meta);
		}

		//deprecated, remove when removed from code.
		retusermeta = CLIENT1.getObjectInfo(loi, 1L);
		
		assertThat("num usermeta correct", retusermeta.size(), is(loi.size()));
		for (Tuple11<Long, String, String, String, Long, String, Long,
				String, String, Long, Map<String, String>> o: retusermeta) {
			checkInfo(o, id, name, type, ver, user, wsid, wsname,
					chksum, size, meta);
		}
		
		retusermeta = CLIENT1.getObjectInfoNew(new GetObjectInfoNewParams().withObjects(loi));

		assertThat("num usermeta correct", retusermeta.size(), is(loi.size()));
		for (Tuple11<Long, String, String, String, Long, String, Long,
				String, String, Long, Map<String, String>> o: retusermeta) {
			checkInfo(o, id, name, type, ver, user, wsid, wsname,
					chksum, size, null);
		}
		
		//deprecated, remove when removed from code.
		retusermeta = CLIENT1.getObjectInfo(loi, 0L);

		assertThat("num usermeta correct", retusermeta.size(), is(loi.size()));
		for (Tuple11<Long, String, String, String, Long, String, Long,
				String, String, Long, Map<String, String>> o: retusermeta) {
			checkInfo(o, id, name, type, ver, user, wsid, wsname,
					chksum, size, null);
		}
	}

	protected List<SubObjectIdentity> objIDToSubObjID(List<ObjectIdentity> loi) {
		LinkedList<SubObjectIdentity> ret = new LinkedList<SubObjectIdentity>();
		for (ObjectIdentity oi: loi) {
			SubObjectIdentity soi = new SubObjectIdentity().withName(oi.getName())
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
	
	protected void checkData(ObjectData retdata, long id, String name,
			String typeString, int ver, String user, long wsid, String wsname,
			String chksum, long size, Map<String, String> meta, Map<String, Object> data) 
			throws Exception {
		
		assertThat("object data is correct", retdata.getData().asClassInstance(Object.class),
				is((Object) data));
		
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

	protected void checkData(List<ObjectIdentity> loi, Map<String, Object> data)
			throws Exception {
		assertThat("expected loi size is 1", loi.size(), is(1));
		assertThat("can get data", CLIENT1.getObjects(loi).get(0).getData()
				.asClassInstance(Object.class), is((Object) data));
		assertThat("can get data", CLIENT1.getObjectSubset(objIDToSubObjID(loi))
				.get(0).getData().asClassInstance(Object.class), is((Object) data));
	}

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
		
		List<ObjectProvenanceInfo> prov = CLIENT1.getObjectProvenance(loi);
		compareObjectInfo(prov.get(0).getInfo(), prov.get(1).getInfo(), wsname, wsid, name, id, ver);
		assertThat("creator same", prov.get(1).getCreator(), is(prov.get(0).getCreator()));
		assertThat("created same", prov.get(1).getCreated(), is(prov.get(0).getCreated()));
		assertThat("prov same", prov.get(1).getProvenance(), is(prov.get(0).getProvenance()));
		assertThat("refs same", prov.get(1).getRefs(), is(prov.get(0).getRefs()));
		assertThat("copy ref correct", prov.get(1).getCopied(), is(expectedCopy));
		assertThat("copy visibility correct", prov.get(1).getCopySourceInaccessible(), is(0L));
		
		List<ObjectData> objs = CLIENT1.getObjects(loi);
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
	
	protected String addSec(String time) throws Exception {
		return DATE_FORMAT.format(DATE_FORMAT.parse(time).getTime() + 1000);
	}
	
	protected String subSec(String time) throws Exception {
		return DATE_FORMAT.format(DATE_FORMAT.parse(time).getTime() - 1000);
	}

	protected void checkWSInfoList(
			List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> got,
			List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> expected,
			List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> notexpected) {
		checkWSInfoList(got, expected, notexpected, false);
	}
	
	protected void checkWSInfoList(
			List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> got,
			List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> expected,
			List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> notexpected,
			boolean testDates) {
		Map<Long, Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> expecmap = 
				new HashMap<Long, Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>>();
		for (Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> inf: expected) {
			expecmap.put(inf.getE1(), inf);
		}
		Set<Long> seen = new HashSet<Long>();
		Set<Long> seenexp = new HashSet<Long>();
		Set<Long> notexp = new HashSet<Long>();
		for (Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> inf: notexpected) {
			notexp.add(inf.getE1());
		}
		for (Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info: got) {
			if (seen.contains(info.getE1())) {
				fail("Saw same workspace twice");
			}
			if (notexp.contains(info.getE1())) {
				fail("Got unexpected workspace id " + info.getE1());
			}
			if (!expecmap.containsKey(info.getE1())) {
				continue; // only two users so really impossible to list a controlled set of ws
				// if this is important add a 3rd user and client
			}
			seenexp.add(info.getE1());
			Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> exp =
					expecmap.get(info.getE1());
			assertThat("ids correct", info.getE1(), is(exp.getE1()));
			if (testDates) {
				assertThat("moddates correct", info.getE4(), is(exp.getE4())); 
			}
			assertThat("ws name correct", info.getE2(), is(exp.getE2()));
			assertThat("user name correct", info.getE3(), is(exp.getE3()));
			assertThat("obj counts are 0", info.getE5(), is(exp.getE5()));
			assertThat("permission correct", info.getE6(), is(exp.getE6()));
			assertThat("global read correct", info.getE7(), is(exp.getE7()));
			assertThat("lockstate correct", info.getE8(), is(exp.getE8()));
			
		}
		assertThat("got same ws ids", seenexp, is(expecmap.keySet()));
	}

	protected void checkObjectPagination(String wsname,
			Long skip, Long limit, int minid, int maxid) 
			throws Exception {
		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> res =
				CLIENT1.listObjects(new ListObjectsParams()
				.withWorkspaces(Arrays.asList(wsname)).withSkip(skip)
				.withLimit(limit));
				
		assertThat("correct number of objects returned", res.size(), is(maxid - minid + 1));
		for (Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> oi: res) {
			if (oi.getE1() < minid || oi.getE1() > maxid) {
				fail(String.format("ObjectID out of test bounds: %s min %s max %s",
						oi.getE1(), minid, maxid));
			}
		}
	}
	
	protected void failListObjectsByDate(String date, String exception) throws Exception {
		try {
			CLIENT1.listObjects(new ListObjectsParams().withAfter(date));
			fail("listed obj info with bad date");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exception));
		}
		try {
			CLIENT1.listObjects(new ListObjectsParams().withBefore(date));
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
		Set<FakeObjectInfo> g = objInfoToFakeObjInfo(got);
		Set<FakeObjectInfo> e = objInfoToFakeObjInfo(expected);
		assertThat("got same unordered objects", g, is(e));
		
	}

	protected Set<FakeObjectInfo> objInfoToFakeObjInfo(
			List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> tpl)
			throws Exception {
		Set<FakeObjectInfo> s = new HashSet<FakeObjectInfo>();
		for (Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> t: tpl) {
			s.add(new FakeObjectInfo(t.getE1(), t.getE2(), t.getE3(), DATE_FORMAT.parse(t.getE4()),
					t.getE5().intValue(), new WorkspaceUser(t.getE6()), 
					new FakeResolvedWSID(t.getE8(), t.getE7()), t.getE9(),
					t.getE10(), t.getE11()));
		}
		return s;
	}
	
	protected void failListObjects(List<String> wsnames, List<Long> wsids,
			String type, String perm, Map<String, String> meta, Long showHidden,
			Long showDeleted, Long allVers, Long includeMeta, String exp)
			throws Exception {
		failListObjects(wsnames, wsids, type, perm, meta, showHidden,
				showDeleted, allVers, includeMeta, -1, -1, exp);
	}

	protected void failListObjects(List<String> wsnames, List<Long> wsids,
			String type, String perm, Map<String, String> meta, Long showHidden,
			Long showDeleted, Long allVers, Long includeMeta, long skip, long limit, String exp)
			throws Exception {
		try {
			CLIENT1.listObjects(new ListObjectsParams().withWorkspaces(wsnames)
					.withIds(wsids).withType(type).withShowHidden(showHidden)
					.withShowDeleted(showDeleted).withShowAllVersions(allVers)
					.withIncludeMetadata(includeMeta).withPerm(perm).withMeta(meta)
					.withSkip(skip).withLimit(limit));
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

	protected void getReferencedObjectsCheckData(List<ObjectData> exp) throws IOException,
			JsonClientException, Exception {
		List<ObjectData> res = CLIENT1.getReferencedObjects(Arrays.asList(
				Arrays.asList(new ObjectIdentity().withRef("referenced/ref"), new ObjectIdentity().withRef("referencedPriv/one")),
				Arrays.asList(new ObjectIdentity().withRef("referenced/prov"), new ObjectIdentity().withRef("referencedPriv/two"))));
		compareData(exp, res);
	}
	
	protected void failGetReferencedObjects(List<List<ObjectIdentity>> chains,
			String excep) throws Exception {
		try {
			CLIENT1.getReferencedObjects(chains);
			fail("got referenced objects with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(excep));
		}
	}

	protected void checkAdmins(WorkspaceClient cli, List<String> expadmins)
			throws Exception {
		List<String> admins = cli.administer(new UObject(createData(
				"{\"command\": \"listAdmins\"}"))).asInstance();
		Set<String> got = new HashSet<String>(admins);
		Set<String> expected = new HashSet<String>(expadmins);
		assertTrue("correct admins", got.containsAll(expected));
		assertThat("only the one built in admin", expected.size() + 1, is(got.size()));
		
	}
	
	protected void failAdmin(WorkspaceClient cli, String cmd, String exp)
			throws Exception {
		failAdmin(cli, createData(cmd), exp);
	}
		
	protected void failAdmin(WorkspaceClient cli, Map<String, Object> cmd,
			String exp)
			throws Exception {
		try {
			cli.administer(new UObject(cmd));
			fail("ran admin command with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
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
