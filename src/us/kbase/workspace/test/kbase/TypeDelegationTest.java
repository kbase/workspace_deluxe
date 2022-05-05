package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.list;
import static us.kbase.workspace.test.kbase.JSONRPCLayerTester.startupWorkspaceServer;

import java.net.URL;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;

import us.kbase.auth.AuthToken;
import us.kbase.common.service.UObject;
import us.kbase.common.test.MapBuilder;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.test.auth2.authcontroller.AuthController;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.database.DynamicConfig;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;

/* Tests 2 workspaces, one of which delegates type handling to the other.
 * Doesn't do in depth type tests; those are relegated to the regular JSONRPCTest classes.
 * Mostly just checks that the methods to indeed delegate, since under the hood the delegators
 * just pass requests and responses as is.
 */
public class TypeDelegationTest {
	
	private static final String CLS = TypeDelegationTest.class.getSimpleName();
	private static final String DB_NAME_WS_PRIMARY = CLS + "_primary_ws";
	private static final String DB_NAME_WS_PRIMARY_TYPES = CLS + "_primary_types";
	private static final String DB_NAME_WS_SECONDARY = CLS + "_secondary_ws";
	
	private static WorkspaceServer TYPE_SERVER;
	private static URL TYPE_SERVER_URL;
	private static WorkspaceServer DELEGATION_SERVER;
	private static WorkspaceClient TYPE_CLIENT;
	private static WorkspaceClient TYPE_CLIENT_ADMIN;
	private static WorkspaceClient DELEGATION_CLIENT;
	private static WorkspaceClient DELEGATION_CLIENT_ADMIN;
	private static final String USER1 = "user1";
	private static final String USER2 = "user2";
	
	private static MongoController MONGO;
	private static AuthController AUTHC;
	
	@BeforeClass
	public static void setUp() throws Exception {
		TestCommon.stfuLoggers();
		MONGO = new MongoController(TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using mongo temp dir " + MONGO.getTempDir());
		final String mongohost = "localhost:" + MONGO.getServerPort();
		
		// set up auth
		final String dbname = JSONRPCLayerTester.class.getSimpleName() + "Auth";
		AUTHC = new AuthController(
				TestCommon.getJarsDir(),
				"localhost:" + MONGO.getServerPort(),
				dbname,
				Paths.get(TestCommon.getTempDir()));
		final URL authURL = new URL("http://localhost:" + AUTHC.getServerPort() + "/testmode");
		System.out.println("started auth server at " + authURL);
		TestCommon.createAuthUser(authURL, USER1, "display1");
		final String token1 = TestCommon.createLoginToken(authURL, USER1);
		TestCommon.createAuthUser(authURL, USER2, "display2");
		final String token2 = TestCommon.createLoginToken(authURL, USER2);
		final AuthToken t1 = new AuthToken(token1, USER1);
		final AuthToken t2 = new AuthToken(token2, USER2);
		
		TYPE_SERVER = startupWorkspaceServer(
				mongohost,
				DB_NAME_WS_PRIMARY,
				DB_NAME_WS_PRIMARY_TYPES,
				null,
				AUTHC.getServerPort(),
				null,
				null,
				null,
				USER2,
				CLS + "_temp_types",
				100000,
				new LinkedList<>(),
				new ResourceUsageConfigurationBuilder().build());
		
		int typeport = TYPE_SERVER.getServerPort();
		System.out.println("Started test type server on port " + typeport);
		TYPE_SERVER_URL = new URL("http://localhost:" + typeport);
		TYPE_CLIENT = new WorkspaceClient(TYPE_SERVER_URL, t1);
		TYPE_CLIENT_ADMIN = new WorkspaceClient(TYPE_SERVER_URL, t2);
		TYPE_CLIENT.setIsInsecureHttpConnectionAllowed(true);
		TYPE_CLIENT_ADMIN.setIsInsecureHttpConnectionAllowed(true);
		
		DELEGATION_SERVER = startupWorkspaceServer(
				mongohost,
				DB_NAME_WS_SECONDARY,
				null,
				TYPE_SERVER_URL,
				AUTHC.getServerPort(),
				null,
				null,
				null,
				USER2,
				CLS + "_temp_delegation",
				100000,
				new LinkedList<>(),
				new ResourceUsageConfigurationBuilder().build());
		
		int delport = DELEGATION_SERVER.getServerPort();
		System.out.println("Started test delegating server on port " + delport);
		final URL wsurl_del = new URL("http://localhost:" + delport);
		DELEGATION_CLIENT = new WorkspaceClient(wsurl_del, t1);
		DELEGATION_CLIENT_ADMIN = new WorkspaceClient(wsurl_del, t2);
		DELEGATION_CLIENT.setIsInsecureHttpConnectionAllowed(true);
		DELEGATION_CLIENT_ADMIN.setIsInsecureHttpConnectionAllowed(true);
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (TYPE_SERVER != null) {
			TYPE_SERVER.stopServer();
		}
		if (DELEGATION_SERVER != null) {
			DELEGATION_SERVER.stopServer();
		}
		if (AUTHC != null) {
			AUTHC.destroy(TestCommon.getDeleteTempFiles());
		}
		if (MONGO != null) {
			MONGO.destroy(TestCommon.getDeleteTempFiles());
		}
	}
	
	@Before
	public void clearDB() throws Exception {
		// since we're testing types, we nuke the type database as well as the workspace db
		try (final MongoClient mcli = new MongoClient("localhost:" + MONGO.getServerPort())) {
			for (final String name: list(
					DB_NAME_WS_PRIMARY, DB_NAME_WS_PRIMARY_TYPES, DB_NAME_WS_SECONDARY)) {
				final MongoDatabase wsdb = mcli.getDatabase(name);
				TestCommon.destroyDB(wsdb);
				// this will also insert into the type db but the collection is unused so meh
				wsdb.getCollection("dyncfg").insertOne(
						new Document("key", DynamicConfig.KEY_BACKEND_SCALING).append("value", 1));
			}
		}
	}
	
	private Object admin(final WorkspaceClient c, final Map<String, Object> cmd)
			throws Exception {
		final UObject res = c.administer(new UObject(cmd));
		return res == null ? null : res.asClassInstance(Object.class);
	}
	
	public Object listModRequests(final WorkspaceClient cli) throws Exception {
		return admin(cli, ImmutableMap.of("command", "listModRequests"));
	}
	
	@Test
	public void delegationTarget() throws Exception {
		// checks the two delegation target methods
		final Object ret = admin(
				DELEGATION_CLIENT_ADMIN, ImmutableMap.of("command", "getTypeDelegationTarget"));
		assertThat("incorrect mod reqs", ret, is(ImmutableMap.of(
				"delegateTarget", TYPE_SERVER_URL.toString())));
		
		final Object ret2 = admin(
				TYPE_CLIENT_ADMIN, ImmutableMap.of("command", "getTypeDelegationTarget"));
		assertThat("incorrect mod reqs", ret2, is(MapBuilder.<String, Object>newHashMap()
				.with("delegateTarget", null).build()));
	}
	
	@Test
	public void requestModuleAndDeny() throws Exception {
		// checks:
		// requestModuleOwnership
		// admin listModRequests
		// admin denyModRequest
		DELEGATION_CLIENT.requestModuleOwnership("MyMod");
		final Object ret = listModRequests(DELEGATION_CLIENT_ADMIN);
		final List<Map<String, Object>> expected = list(ImmutableMap.of(
				"ownerUserId", "user1",
				"withChangeOwnersPrivilege", true,
				"moduleName", "MyMod"));
		assertThat("incorrect mod reqs", ret, is(expected));
		
		// now check that the module shows as requested on the delegated workspace
		final Object ret2 = listModRequests(TYPE_CLIENT_ADMIN);
		assertThat("incorrect mod reqs", ret2, is(expected));
		
		final Object ret3 = admin(DELEGATION_CLIENT_ADMIN, ImmutableMap.of(
				"command", "denyModRequest", "module", "MyMod"));
		assertThat("incorrect deny mod", ret3, is(nullValue()));
		
		final Object ret4 = listModRequests(DELEGATION_CLIENT_ADMIN);
		assertThat("incorrect mod reqs", ret4, is(list()));
		final Object ret5 = listModRequests(TYPE_CLIENT_ADMIN);
		assertThat("incorrect mod reqs", ret5, is(list()));
	}
}
