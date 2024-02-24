package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.productivity.java.syslog4j.SyslogIF;

import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.JsonServerSyslog;
import us.kbase.common.service.UObject;
import us.kbase.common.service.JsonServerSyslog.SyslogOutput;
import us.kbase.common.service.Tuple11;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.test.auth2.authcontroller.AuthController;
import us.kbase.workspace.AlterAdminObjectMetadataParams;
import us.kbase.workspace.CopyObjectParams;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GetObjectInfo3Params;
import us.kbase.workspace.GetObjects2Params;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectMetadataUpdate;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ObjectSpecification;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.RenameObjectParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.database.DynamicConfig;
import us.kbase.workspace.test.WorkspaceServerThread;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

/** Tests application logging only - not the standard logging that comes with
 * JsonServerServlet.
 * @author gaprice@lbl.gov
 *
 */
public class LoggingTest {

	// TODO TEST convert these to unit tests

	private static final String ARGUTILS = "us.kbase.workspace.kbase.ArgUtils";
	private static final String SERV = "us.kbase.workspace.WorkspaceServer";
	private static final String WSMETH = "us.kbase.workspace.kbase.WorkspaceServerMethods";

	private static final String DB_WS_NAME = "LoggingTest";
	private static final String DB_TYPE_NAME = "LoggingTest_Types";

	private static int INFO = JsonServerSyslog.LOG_LEVEL_INFO;
//	private static int ERR = JsonServerSyslog.LOG_LEVEL_ERR;

	private static final String ATYPE = "SomeModule.AType";
	private static final String BTYPE = "SomeModule.BType";
	private static final String REFTYPE = "SomeModule.RefType";

	private static final String USER1 = "user1";
	private static final String USER2 = "user2";
	private static MongoController mongo;
	private static AuthController authc;
	private static WorkspaceServer SERVER;
	private static WorkspaceClient CLIENT1;
	private static WorkspaceClient CLIENT2;
	private static SysLogOutputMock logout;

	@BeforeClass
	public static void setUpClass() throws Exception {
		logout = new SysLogOutputMock();

//		WorkspaceTestCommon.stfuLoggers();
		mongo = new MongoController(TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using mongo temp dir " + mongo.getTempDir());

		// set up auth
		final String dbname = LoggingTest.class.getSimpleName() + "Auth";
		authc = new AuthController(
				"localhost:" + mongo.getServerPort(),
				dbname,
				Paths.get(TestCommon.getTempDir()));
		final URL authURL = new URL("http://localhost:" + authc.getServerPort() + "/testmode");
		System.out.println("started auth server at " + authURL);
		TestCommon.createAuthUser(authURL, USER1, "display1");
		final String token1 = TestCommon.createLoginToken(authURL, USER1);
		TestCommon.createAuthUser(authURL, USER2, "display2");
		final String token2 = TestCommon.createLoginToken(authURL, USER2);
		final AuthToken t1 = new AuthToken(token1, USER1);
		final AuthToken t2 = new AuthToken(token2, USER2);

		final String mongohost = "localhost:" + mongo.getServerPort();

		SERVER = startupWorkspaceServer(mongohost, DB_WS_NAME, DB_TYPE_NAME);
		SERVER.changeSyslogOutput(logout);
		int port = SERVER.getServerPort();
		System.out.println("Started test server 1 on port " + port);
		CLIENT1 = new WorkspaceClient(new URL("http://localhost:" + port), t1);
		CLIENT2 = new WorkspaceClient(new URL("http://localhost:" + port), t2);

		CLIENT1.setIsInsecureHttpConnectionAllowed(true);
		CLIENT2.setIsInsecureHttpConnectionAllowed(true);

		//set up a basic type for test use that doesn't worry about type checking
		CLIENT1.requestModuleOwnership("SomeModule");
		administerCommand(CLIENT2, "approveModRequest", "module", "SomeModule");
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec(
					"module SomeModule {" +
						"/* @optional thing */" +
						"typedef structure {" +
							"string thing;" +
						"} AType;" +
						"/* @optional thing */" +
						"typedef structure {" +
							"string thing;" +
						"} BType;" +
						"/* @id ws */" +
						"typedef string ref;" +
						"typedef structure {" +
							"ref r;" +
						"} RefType;" +
					"};"
					)
			.withNewTypes(Arrays.asList("AType", "BType", "RefType")));
		CLIENT1.releaseModule("SomeModule");
	}

	public static void administerCommand(WorkspaceClient client,
			String command, String... params)
			throws IOException, JsonClientException {
		Map<String, String> releasemod = new HashMap<String, String>();
		releasemod.put("command", command);
		for (int i = 0; i < params.length / 2; i++)
			releasemod.put(params[i * 2], params[i * 2 + 1]);
		client.administer(new UObject(releasemod));
	}

	private static WorkspaceServer startupWorkspaceServer(
			final String mongohost,
			final String db,
			final String typedb)
			throws Exception {

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
		ws.add("auth2-service-url", "http://localhost:" + authc.getServerPort() + "/testmode/");
		ws.add("backend-type", "GridFS");
		ws.add("ws-admin", USER2);
		ws.add("temp-dir", Paths.get(TestCommon.getTempDir())
				.resolve("tempForLoggingTest"));
		ws.add("ignore-handle-service", "true");
		ini.store(iniFile);
		iniFile.deleteOnExit();

		//set up env
		Map<String, String> env = TestCommon.getenv();
		env.put("KB_DEPLOYMENT_CONFIG", iniFile.getAbsolutePath());

		final WorkspaceServer server = new WorkspaceServer(true);
		new WorkspaceServerThread(server).start();
		System.out.println("Main thread waiting for server to start up");
		while (server.getServerPort() == null) {
			Thread.sleep(1000);
		}
		return server;
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		if (SERVER != null) {
			System.out.print("Killing server... ");
			SERVER.stopServer();
			System.out.println("Done");
		}
		if (authc != null) {
			authc.destroy(TestCommon.getDeleteTempFiles());
		}
		if (mongo != null) {
			System.out.println("destroying mongo temp files");
			mongo.destroy(TestCommon.getDeleteTempFiles());
		}
	}

	@Before
	public void clearDB() throws Exception {
		logout.reset();
		try (final MongoClient mcli = MongoClients.create("mongodb://localhost:" + mongo.getServerPort())) {
			final MongoDatabase wsdb = mcli.getDatabase(DB_WS_NAME);
			TestCommon.destroyDB(wsdb);
			wsdb.getCollection("dyncfg").insertOne(
					new Document("key", DynamicConfig.KEY_BACKEND_SCALING).append("value", 1));
		}
	}

	private static class LogEvent {
		public int level;
		public String message;

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("LogEvent [level=");
			builder.append(level);
			builder.append(", message=");
			builder.append(message);
			builder.append("]");
			return builder.toString();
		}
	}

	private static class SysLogOutputMock extends SyslogOutput {

		public List<LogEvent> events = new LinkedList<LogEvent>();

		@Override
		public void logToSystem(SyslogIF log, int level, String message) {
			LogEvent e = new LogEvent();
			e.level = level;
			e.message = message;
			events.add(e);
		}

		@Override
		public PrintWriter logToFile(File f, PrintWriter pw, int level,
				String message) throws Exception {
			throw new UnsupportedOperationException();
		}

		public void reset() {
			events.clear();
		}
	}

	private static class ExpectedLog {

		public int level;
		public String method;
		public String message;
		public String user;
		public String caller;

		public ExpectedLog(final int level,
				final String method,
				final String message,
				final String user,
				final String caller) {
			this.level = level;
			this.method = method;
			this.message = message;
			this.user = user;
			this.caller = caller;
		}
	}

	private static class LogObjExp extends ExpectedLog {

		public LogObjExp(final String method, final String message, final String caller) {
			super(INFO, method, message, USER1, caller);
		}
		
		public LogObjExp(
				final String method,
				final String message,
				final String caller,
				final String user) {
			super(INFO, method, message, user, caller);
		}
	}

	private void checkLogging(List<ExpectedLog> expected) throws Exception {
		checkLogging(expected, expected.size());
	}

	private void checkLogging(List<ExpectedLog> expected, int eventCount)
			throws Exception {
		assertThat("correct # of logging events", logout.events.size(),
				is(eventCount));
		Iterator<ExpectedLog> i = expected.iterator();
		Iterator<LogEvent> e = logout.events.iterator();
		String callID = null;
		while (i.hasNext()) {
			ExpectedLog exp = i.next();
			LogEvent got = e.next();
			assertThat("correct level", got.level, is(exp.level));
			String call = checkMessage(got.message, exp);
			if (callID == null) {
				callID = call;
			} else {
				assertThat("same call IDs for all calls", call, is(callID));
			}
		}

	}

	private String checkMessage(String message, ExpectedLog exp) {
//		System.out.println(message);
		String[] parts = message.split(":", 2);
		String[] headerParts = parts[0].split("]\\s*\\[");
		assertThat("server name correct", headerParts[0].substring(1),
				is("Workspace"));
		assertThat("record type correct", headerParts[1],
				is(exp.level == INFO ? "INFO" : "ERR"));
		double epochms = Double.valueOf(headerParts[2]) * 1000;
		long now = new Date().getTime();
		assertThat("log date < 5s ago", now - epochms < 5000, is(true));
		//3 is user running the service
		assertThat("caller correct", headerParts[4], is(exp.caller));
		//5 is pid
		assertThat("ip correct", headerParts[6], is("127.0.0.1"));
		assertThat("remote user correct", headerParts[7], is(exp.user));
		assertThat("module correct", headerParts[8], is("Workspace"));
		assertThat("method correct", headerParts[9], is(exp.method));
		String callID = headerParts[10].substring(
				0, headerParts[10].length() - 1);

		assertThat("full message correct", parts[1].trim(), is(exp.message));
		return callID;
	}


	@Test
	public void logObjects() throws Exception {
		/* test various methods that log the object ID and type when run
		 * Doesn't bother with deprecated fns
		 */
		String ws = "myws";
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace(ws));
		logout.reset();

		// save objects
		List<ObjectSaveData> d = new LinkedList<ObjectSaveData>();
		for (String name: Arrays.asList("foo", "bar", "baz")) {
			d.add(new ObjectSaveData()
					.withData(new UObject(new HashMap<String, Object>()))
					.withName(name)
					.withType(name.equals("bar") ? BTYPE : ATYPE));
		}
		CLIENT1.saveObjects(new SaveObjectsParams()
				.withWorkspace(ws)
				.withObjects(d));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("save_objects", "start method", SERV),
				new LogObjExp("save_objects", "Object 1/1/1 SomeModule.AType-1.0", ARGUTILS),
				new LogObjExp("save_objects", "Object 1/2/1 SomeModule.BType-1.0", ARGUTILS),
				new LogObjExp("save_objects", "Object 1/3/1 SomeModule.AType-1.0", ARGUTILS),
				new LogObjExp("save_objects", "end method", SERV))));
		logout.reset();
		
		// alter admin meta
		CLIENT2.alterAdminObjectMetadata(new AlterAdminObjectMetadataParams()
				.withUpdates(Arrays.asList(
						new ObjectMetadataUpdate()
								.withOi(new ObjectIdentity().withRef("myws/foo"))
								.withRemove(Arrays.asList("a")),
						new ObjectMetadataUpdate()
								.withOi(new ObjectIdentity().withRef("1/3/1"))
								.withRemove(Arrays.asList("a"))
				))
		);
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("alter_admin_object_metadata", "start method", SERV, USER2),
				new LogObjExp("alter_admin_object_metadata", "Object 1/1/1", WSMETH, USER2),
				new LogObjExp("alter_admin_object_metadata", "Object 1/3/1", WSMETH, USER2),
				new LogObjExp("alter_admin_object_metadata", "end method", SERV, USER2))));
		logout.reset();
				
		// rename
		CLIENT1.renameObject(new RenameObjectParams()
				.withNewName("bak")
				.withObj(new ObjectIdentity().withRef("1/1/1")));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("rename_object", "start method", SERV),
				new LogObjExp("rename_object",
						"Object 1/1/1 SomeModule.AType-1.0", ARGUTILS),
				new LogObjExp("rename_object", "end method", SERV))));
		logout.reset();

		// copy
		CLIENT1.copyObject(new CopyObjectParams()
				.withFrom(new ObjectIdentity().withRef("1/2"))
				.withTo(new ObjectIdentity().withRef("1/1")));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("copy_object", "start method", SERV),
				new LogObjExp("copy_object",
						"Object 1/1/2 SomeModule.BType-1.0", ARGUTILS),
				new LogObjExp("copy_object", "end method", SERV))));
		logout.reset();

		// revert
		CLIENT1.revertObject(new ObjectIdentity().withRef("1/1/1"));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("revert_object", "start method", SERV),
				new LogObjExp("revert_object",
						"Object 1/1/3 SomeModule.AType-1.0", ARGUTILS),
				new LogObjExp("revert_object", "end method", SERV))));
		logout.reset();

		// history
		CLIENT1.getObjectHistory(new ObjectIdentity().withRef("1/1"));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("get_object_history", "start method", SERV),
				new LogObjExp("get_object_history",
						"Object 1/1/1 SomeModule.AType-1.0", ARGUTILS),
				new LogObjExp("get_object_history",
						"Object 1/1/2 SomeModule.BType-1.0", ARGUTILS),
				new LogObjExp("get_object_history",
						"Object 1/1/3 SomeModule.AType-1.0", ARGUTILS),
				new LogObjExp("get_object_history", "end method", SERV))));
		logout.reset();

		// get info
		CLIENT1.getObjectInfo3(new GetObjectInfo3Params()
				.withObjects(Arrays.asList(
						new ObjectSpecification().withRef("1/1/2"),
						new ObjectSpecification().withRef("1/1/1"))));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("get_object_info3", "start method", SERV),
				new LogObjExp("get_object_info3",
						"Object 1/1/2 SomeModule.BType-1.0", ARGUTILS),
				new LogObjExp("get_object_info3",
						"Object 1/1/1 SomeModule.AType-1.0", ARGUTILS),
				new LogObjExp("get_object_info3", "end method", SERV))));
		logout.reset();

		// get info
		@SuppressWarnings({ "deprecation", "unused" })
		final List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
						Map<String, String>>> foo = CLIENT1.getObjectInfoNew(
				new us.kbase.workspace.GetObjectInfoNewParams().withObjects(Arrays.asList(
						new ObjectSpecification().withRef("1/1/2"),
						new ObjectSpecification().withRef("1/1/1"))));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("get_object_info_new", "start method", SERV),
				new LogObjExp("get_object_info_new",
						"Object 1/1/2 SomeModule.BType-1.0", ARGUTILS),
				new LogObjExp("get_object_info_new",
						"Object 1/1/1 SomeModule.AType-1.0", ARGUTILS),
				new LogObjExp("get_object_info_new", "end method", SERV))));
		logout.reset();

		//get objs2
		CLIENT1.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(
				new ObjectSpecification().withRef("1/1/2"),
				new ObjectSpecification().withRef("1/1/1"))));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("get_objects2", "start method", SERV),
				new LogObjExp("get_objects2",
						"Object 1/1/2 SomeModule.BType-1.0", ARGUTILS),
				new LogObjExp("get_objects2",
						"Object 1/1/1 SomeModule.AType-1.0", ARGUTILS),
				new LogObjExp("get_objects2", "end method", SERV))));
		logout.reset();

		// get objs
		@SuppressWarnings({ "deprecation", "unused" })
		List<ObjectData> objects = CLIENT1.getObjects(Arrays.asList(
				new ObjectIdentity().withRef("1/1/2"),
				new ObjectIdentity().withRef("1/1/1")));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("get_objects", "start method", SERV),
				new LogObjExp("get_objects",
						"Object 1/1/2 SomeModule.BType-1.0", ARGUTILS),
				new LogObjExp("get_objects",
						"Object 1/1/1 SomeModule.AType-1.0", ARGUTILS),
				new LogObjExp("get_objects", "end method", SERV))));
		logout.reset();

		// get subobjs
		@SuppressWarnings({ "unused", "deprecation" })
		List<ObjectData> objectSubset = CLIENT1.getObjectSubset(Arrays.asList(
				new us.kbase.workspace.SubObjectIdentity()
						.withIncluded(Arrays.asList("/")).withRef("1/1/2"),
				new us.kbase.workspace.SubObjectIdentity()
						.withIncluded(Arrays.asList("/")).withRef("1/1/1")));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("get_object_subset", "start method", SERV),
				new LogObjExp("get_object_subset",
						"Object 1/1/2 SomeModule.BType-1.0", ARGUTILS),
				new LogObjExp("get_object_subset",
						"Object 1/1/1 SomeModule.AType-1.0", ARGUTILS),
				new LogObjExp("get_object_subset", "end method", SERV))));
		logout.reset();

		// get prov
		@SuppressWarnings({ "unused", "deprecation" })
		List<us.kbase.workspace.ObjectProvenanceInfo> objectProvenance =
				CLIENT1.getObjectProvenance(Arrays.asList(
				new ObjectIdentity().withRef("1/1/2"),
				new ObjectIdentity().withRef("1/1/1")));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("get_object_provenance", "start method", SERV),
				new LogObjExp("get_object_provenance",
						"Object 1/1/2 SomeModule.BType-1.0", ARGUTILS),
				new LogObjExp("get_object_provenance",
						"Object 1/1/1 SomeModule.AType-1.0", ARGUTILS),
				new LogObjExp("get_object_provenance", "end method", SERV))));
		logout.reset();

		// get ref'd objects
		Map<String, String> r = new HashMap<String, String>();
		r.put("r", "1/1/2");
		CLIENT1.saveObjects(new SaveObjectsParams()
				.withWorkspace(ws)
				.withObjects(Arrays.asList(
						new ObjectSaveData()
							.withData(new UObject(r))
							.withName("ref")
							.withType(REFTYPE))));
		logout.reset();
		@SuppressWarnings({ "unused", "deprecation" })
		List<ObjectData> referencedObjects =
				CLIENT1.getReferencedObjects(Arrays.asList(Arrays.asList(
				new ObjectIdentity().withRef("1/ref/1"),
				new ObjectIdentity().withRef("1/1/2"))));
		checkLogging(convertLogObjExp(Arrays.asList(
				new LogObjExp("get_referenced_objects", "start method", SERV),
				new LogObjExp("get_referenced_objects",
						"Object 1/1/2 SomeModule.BType-1.0", ARGUTILS),
				new LogObjExp("get_referenced_objects", "end method", SERV))));
		logout.reset();

	}

	private List<ExpectedLog> convertLogObjExp(List<LogObjExp> logobj) {
		List<ExpectedLog> e = new LinkedList<LoggingTest.ExpectedLog>();
		for (LogObjExp l: logobj) {
			e.add((ExpectedLog) l);
		}
		return e;
	}
}
