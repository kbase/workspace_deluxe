package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.list;
import static us.kbase.workspace.test.kbase.JSONRPCLayerTester.startupWorkspaceServer;

import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import us.kbase.auth.AuthToken;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UObject;
import us.kbase.common.test.MapBuilder;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.test.auth2.authcontroller.AuthController;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GetModuleInfoParams;
import us.kbase.workspace.GetObjects2Params;
import us.kbase.workspace.GrantModuleOwnershipParams;
import us.kbase.workspace.ListAllTypesParams;
import us.kbase.workspace.ListModuleVersionsParams;
import us.kbase.workspace.ListModulesParams;
import us.kbase.workspace.ModuleInfo;
import us.kbase.workspace.ModuleVersions;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ObjectSpecification;
import us.kbase.workspace.RegisterTypespecCopyParams;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.RemoveModuleOwnershipParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.TypeInfo;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.database.DynamicConfig;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.kbase.LocalTypeServerMethods;

/* Tests 2 workspaces, one of which delegates type handling to the other.
 * Doesn't do in depth type tests; those are relegated to the regular JSONRPCTest classes.
 * Mostly just checks that the methods do indeed delegate, since under the hood the delegators
 * just pass requests and responses as is.
 */
public class TypeDelegationTest {

	private static final String MYMOD_SPEC =
			"module MyMod {\n" +
			"    /* @optional foo */\n" +
			"    typedef structure {\n" +
			"        int foo;\n" +
			"    } Trivial;\n" +
			"    funcdef myfunc(int req) returns (int ret);\n" +
			"};\n";
	private static final String MYMOD_TRIVIAL_SCHEMA =
			"{\n  \"id\" : \"Trivial\",\n  \"description\" : \"@optional foo\",\n"
			+ "  \"type\" : \"object\",\n  \"original-type\" : \"kidl-structure\",\n"
			+ "  \"properties\" : {\n    \"foo\" : {\n      \"type\" : \"integer\",\n"
			+ "      \"original-type\" : \"kidl-int\"\n    }\n  },\n"
			+ "  \"additionalProperties\" : true\n}";
	// TODO CODE WTF is the parsing struct used for anyway? Can we ditch it?
	private static final String MYMOD_TRIVIAL_PARSING_STRUCT =
			"{\"!\":\"Bio::KBase::KIDL::KBT::Typedef\","
			+ "\"alias_type\":{\"!\":\"Bio::KBase::KIDL::KBT::Struct\",\"annotations\":{},"
			+ "\"items\":[{\"!\":\"Bio::KBase::KIDL::KBT::StructItem\","
			+ "\"item_type\":{\"!\":\"Bio::KBase::KIDL::KBT::Scalar\",\"annotations\":{},"
			+ "\"scalar_type\":\"int\"},\"name\":\"foo\",\"nullable\":\"0\"}],"
			+ "\"name\":\"Trivial\"},\"annotations\":{\"metadata\":{\"ws\":{}},"
			+ "\"optional\":[\"foo\"],\"searchable_ws_subset\":{\"fields\":{},\"keys\":{}},"
			+ "\"unknown_annotations\":{}},\"comment\":\"@optional foo\",\"module\":\"MyMod\","
			+ "\"name\":\"Trivial\"}";
	private static final String MYMOD_MYFUNC_PARSING_STRUCT =
			"{\"!\":\"Bio::KBase::KIDL::KBT::Funcdef\","
			+ "\"annotations\":{\"unknown_annotations\":{}},\"async\":\"0\","
			+ "\"authentication\":\"none\",\"comment\":\"\",\"name\":\"myfunc\","
			+ "\"parameters\":[{\"name\":\"req\","
			+ "\"type\":{\"!\":\"Bio::KBase::KIDL::KBT::Scalar\",\"annotations\":{},"
			+ "\"scalar_type\":\"int\"}}],\"return_type\":[{\"name\":\"ret\","
			+ "\"type\":{\"!\":\"Bio::KBase::KIDL::KBT::Scalar\",\"annotations\":{},"
			+ "\"scalar_type\":\"int\"}}]}";
	private static final String MYMOD2_SPEC =
			"module MyMod2 {\n" +
			"    typedef structure {\n" +
			"        int foo;\n" +
			"    } Foo;\n" +
			"};\n";
	private static final String MYMOD2_FOO_SCHEMA =
			"{\n  \"id\" : \"Foo\",\n  \"description\" : \"\",\n  \"type\" : \"object\",\n"
			+ "  \"original-type\" : \"kidl-structure\",\n"
			+ "  \"properties\" : {\n    \"foo\" : {\n      \"type\" : \"integer\",\n"
			+ "      \"original-type\" : \"kidl-int\"\n    }\n  },\n"
			+ "  \"additionalProperties\" : true,\n  \"required\" : [ \"foo\" ]\n}";

	private static final String CLS = TypeDelegationTest.class.getSimpleName();
	private static final String DB_NAME_WS = CLS + "_ws";
	private static final String DB_NAME_WS_TYPES = CLS + "_types";
	private static final String DB_NAME_WS_REMOTE = CLS + "_remote_ws";
	private static final String DB_NAME_WS_REMOTE_TYPES = CLS + "_remote_types";

	private static WorkspaceServer TYPE_SERVER;
	private static URL TYPE_SERVER_URL;
	private static WorkspaceServer DELEGATION_SERVER;
	// This is a server that's not part of the delegation cluster.
	// Used for testing transfer of typespecs from one server to another.
	private static WorkspaceServer REMOTE_SERVER;
	private static URL REMOTE_SERVER_URL;
	private static WorkspaceClient TYPE_CLIENT;
	private static WorkspaceClient TYPE_CLIENT_ADMIN;
	private static WorkspaceClient DELEGATION_CLIENT;
	private static WorkspaceClient DELEGATION_CLIENT_ADMIN;
	private static WorkspaceClient REMOTE_CLIENT;
	private static WorkspaceClient REMOTE_CLIENT_ADMIN;
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
		final String mongohost = "localhost:" + MONGO.getServerPort();
		System.out.println(String.format("Started mongo at %s with temp dir %s",
				mongohost, MONGO.getTempDir()));

		AUTHC = new AuthController(
				TestCommon.getJarsDir(),
				mongohost,
				CLS + "Auth",
				Paths.get(TestCommon.getTempDir()));
		final URL authURL = new URL("http://localhost:" + AUTHC.getServerPort() + "/testmode");
		System.out.println("started auth server at " + authURL + "\n");
		TestCommon.createAuthUser(authURL, USER1, "display1");
		final String token1 = TestCommon.createLoginToken(authURL, USER1);
		TestCommon.createAuthUser(authURL, USER2, "display2");
		final String token2 = TestCommon.createLoginToken(authURL, USER2);
		final AuthToken t1 = new AuthToken(token1, USER1);
		final AuthToken t2 = new AuthToken(token2, USER2);

		TYPE_SERVER = startupWorkspaceServer(
				mongohost,
				DB_NAME_WS,
				DB_NAME_WS_TYPES,
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

		final int typeport = TYPE_SERVER.getServerPort();
		System.out.println("Started test type server on port " + typeport + "\n");
		TYPE_SERVER_URL = new URL("http://localhost:" + typeport);
		TYPE_CLIENT = new WorkspaceClient(TYPE_SERVER_URL, t1);
		TYPE_CLIENT_ADMIN = new WorkspaceClient(TYPE_SERVER_URL, t2);
		TYPE_CLIENT.setIsInsecureHttpConnectionAllowed(true);
		TYPE_CLIENT_ADMIN.setIsInsecureHttpConnectionAllowed(true);

		DELEGATION_SERVER = startupWorkspaceServer(
				mongohost,
				DB_NAME_WS, // delegating WS can write to the same WS DB, just not the type DB
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

		final int delport = DELEGATION_SERVER.getServerPort();
		System.out.println("Started test delegating server on port " + delport + "\n");
		final URL wsurl_del = new URL("http://localhost:" + delport);
		DELEGATION_CLIENT = new WorkspaceClient(wsurl_del, t1);
		DELEGATION_CLIENT_ADMIN = new WorkspaceClient(wsurl_del, t2);
		DELEGATION_CLIENT.setIsInsecureHttpConnectionAllowed(true);
		DELEGATION_CLIENT_ADMIN.setIsInsecureHttpConnectionAllowed(true);

		REMOTE_SERVER = startupWorkspaceServer(
				mongohost,
				DB_NAME_WS_REMOTE,
				DB_NAME_WS_REMOTE_TYPES,
				null,
				AUTHC.getServerPort(),
				null,
				null,
				null,
				USER2,
				CLS + "_temp_remote",
				100000,
				new LinkedList<>(),
				new ResourceUsageConfigurationBuilder().build());

		int remport = REMOTE_SERVER.getServerPort();
		System.out.println("Started test remote server on port " + remport + "\n");
		REMOTE_SERVER_URL = new URL("http://localhost:" + remport);
		REMOTE_CLIENT = new WorkspaceClient(REMOTE_SERVER_URL, t1);
		REMOTE_CLIENT_ADMIN = new WorkspaceClient(REMOTE_SERVER_URL, t2);
		REMOTE_CLIENT.setIsInsecureHttpConnectionAllowed(true);
		REMOTE_CLIENT_ADMIN.setIsInsecureHttpConnectionAllowed(true);
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		if (TYPE_SERVER != null) {
			TYPE_SERVER.stopServer();
		}
		if (DELEGATION_SERVER != null) {
			DELEGATION_SERVER.stopServer();
		}
		if (REMOTE_SERVER != null) {
			REMOTE_SERVER.stopServer();
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
		try (final MongoClient mcli = MongoClients.create("mongodb://localhost:" + MONGO.getServerPort())) {
			for (final String name: list(
					DB_NAME_WS, DB_NAME_WS_TYPES, DB_NAME_WS_REMOTE, DB_NAME_WS_REMOTE_TYPES)) {
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

	private Object listModRequests(final WorkspaceClient cli) throws Exception {
		return admin(cli, ImmutableMap.of("command", "listModRequests"));
	}

	private void createModule(
			final String mod,
			final WorkspaceClient cli,
			final WorkspaceClient cli_admin)
			throws Exception {
		cli.requestModuleOwnership(mod);

		final Object approveMod = admin(cli_admin, ImmutableMap.of(
				"command", "approveModRequest", "module", mod));
		assertThat("incorrect approve mod", approveMod, is(nullValue()));
	}

	private void checkOwners(
			final String mod,
			final WorkspaceClient cli,
			final List<String> owners)
			throws Exception {
		final ModuleInfo modinfo = cli.getModuleInfo(
				new GetModuleInfoParams().withMod(mod));
		assertThat("incorrect owners", modinfo.getOwners(), is(owners));
	}

	@Test
	public void delegationTarget() throws Exception {
		// checks the delegation target method
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

	@Test
	public void registerSpecAndCheck() throws Exception {
		/* Runs through registering a type spec and then doing all the other stuff you can do
		 * with a spec, since the set up is already done
		 *
		 * checks:
		 * requestModuleOwnership
		 * admin approveModRequest
		 * listModules
		 * registerTypeSpec
		 * releaseModule
		 * listModuleVersions
		 * getAllFuncInfo
		 * getFuncInfo
		 * getAllTypeInfo
		 * getTypeInfo
		 * getModuleInfo
		 * getJsonSchema
		 * listAllTypes
		 * translateFromMD5Types
		 * translateToMD5Types
		 * grantModuleOwnership
		 * removeModuleOwnership
		 */
		createModule("MyMod", DELEGATION_CLIENT, DELEGATION_CLIENT_ADMIN);

		// check that the module shows up on both servers
		final List<String> listModD = DELEGATION_CLIENT.listModules(new ListModulesParams());
		assertThat("incorrect mod info", listModD, is(list("MyMod")));
		final List<String> listModT = TYPE_CLIENT.listModules(new ListModulesParams());
		assertThat("incorrect mod info", listModT, is(list("MyMod")));

		final Map<String, String> reg = DELEGATION_CLIENT.registerTypespec(
				new RegisterTypespecParams()
					.withDryrun(0L)
					.withNewTypes(list("Trivial"))
					.withSpec(MYMOD_SPEC)
				);
		assertThat("incorrect comp result", reg, is(ImmutableMap.of(
				"MyMod.Trivial-0.1", MYMOD_TRIVIAL_SCHEMA)));

		final List<String> rel = DELEGATION_CLIENT.releaseModule("MyMod");
		assertThat("incorrect release", rel, is(list("MyMod.Trivial-1.0")));

		// *** Run all the non-admin methods on both servers

		// listModuleVersions
		final ModuleVersions modversD = DELEGATION_CLIENT.listModuleVersions(
				new ListModuleVersionsParams().withMod("MyMod"));
		final long prereleaseModVer = modversD.getVers().get(0);
		final long releaseModVer = modversD.getVers().get(1);
		// Can't predict timestamps easily, so we just fill them in from the output
		final ModuleVersions expectedModVers = new ModuleVersions()
				.withMod("MyMod")
				// TODO BUGFIX ModuleVersions has a released_vers field which was added
				// to the spec but never implemented and is always null. Remove it from the spec
				// and properly document how the method works - there's differences if you're an
				// owner and also if you supply a type or a module.
				.withReleasedVers(null)
				.withVers(list(prereleaseModVer, releaseModVer));
		checkModVers(modversD, expectedModVers);
		final ModuleVersions modversT = DELEGATION_CLIENT.listModuleVersions(
				new ListModuleVersionsParams().withMod("MyMod"));
		checkModVers(modversT, expectedModVers);

		// getFuncInfo
		@SuppressWarnings("deprecation")
		final us.kbase.workspace.FuncInfo expectedFuncInfo = new us.kbase.workspace.FuncInfo()
				.withDescription("")
				.withFuncDef("MyMod.myfunc-1.0")
				.withFuncVers(list("MyMod.myfunc-0.1", "MyMod.myfunc-1.0"))
				// TODO CHECK why isn't this including all mod vers?
				.withModuleVers(list(releaseModVer))
				.withParsingStructure(MYMOD_MYFUNC_PARSING_STRUCT)
				.withReleasedFuncVers(list("MyMod.myfunc-1.0"))
				.withReleasedModuleVers(list(releaseModVer))
				.withSpecDef("funcdef myfunc(int req) returns (int ret) authentication none;")
				.withUsedTypeDefs(list());
		@SuppressWarnings("deprecation")
		final us.kbase.workspace.FuncInfo funcD = DELEGATION_CLIENT.getFuncInfo("MyMod.myfunc");
		checkFuncInfo(funcD, expectedFuncInfo);
		@SuppressWarnings("deprecation")
		final us.kbase.workspace.FuncInfo funcT = TYPE_CLIENT.getFuncInfo("MyMod.myfunc");
		checkFuncInfo(funcT, expectedFuncInfo);
		// getAllFuncInfo
		@SuppressWarnings("deprecation")
		final List<us.kbase.workspace.FuncInfo> allFuncD = DELEGATION_CLIENT.getAllFuncInfo(
				"MyMod");
		assertThat("incorrect func count", allFuncD.size(), is(1));
		checkFuncInfo(allFuncD.get(0), expectedFuncInfo);
		@SuppressWarnings("deprecation")
		final List<us.kbase.workspace.FuncInfo> allFuncT = TYPE_CLIENT.getAllFuncInfo("MyMod");
		assertThat("incorrect func count", allFuncT.size(), is(1));
		checkFuncInfo(allFuncT.get(0), expectedFuncInfo);

		// getTypeInfo
		final TypeInfo expectedTypeInfo = new TypeInfo()
				.withDescription("@optional foo")
				.withJsonSchema(MYMOD_TRIVIAL_SCHEMA)
				// TODO CHECK why isn't this including all mod vers?
				.withModuleVers(list(releaseModVer))
				.withParsingStructure(MYMOD_TRIVIAL_PARSING_STRUCT)
				.withReleasedModuleVers(list(releaseModVer))
				.withReleasedTypeVers(list("MyMod.Trivial-1.0"))
				.withSpecDef("/*\n@optional foo\n*/\ntypedef structure {\n  int foo;\n} Trivial;")
				.withTypeDef("MyMod.Trivial-1.0")
				.withTypeVers(list("MyMod.Trivial-0.1", "MyMod.Trivial-1.0"))
				.withUsedTypeDefs(list())
				.withUsingFuncDefs(list())
				.withUsingTypeDefs(list());
		final TypeInfo typesD = DELEGATION_CLIENT.getTypeInfo("MyMod.Trivial");
		checkTypeInfo(typesD, expectedTypeInfo);
		final TypeInfo typesT = TYPE_CLIENT.getTypeInfo("MyMod.Trivial");
		checkTypeInfo(typesT, expectedTypeInfo);
		// getAllTypeInfo
		final List<TypeInfo> allTypesD = DELEGATION_CLIENT.getAllTypeInfo("MyMod");
		assertThat("incorrect type count", allTypesD.size(), is(1));
		checkTypeInfo(allTypesD.get(0), expectedTypeInfo);
		final List<TypeInfo> allTypesT = TYPE_CLIENT.getAllTypeInfo("MyMod");
		assertThat("incorrect type count", allTypesT.size(), is(1));
		checkTypeInfo(allTypesT.get(0), expectedTypeInfo);

		// getModuleInfo
		final ModuleInfo expectedModuleInfo = new ModuleInfo()
				.withChsum("ad539ae2166a3796e024218ee74259e7")
				.withDescription("")
				.withFunctions(list("MyMod.myfunc-1.0"))
				.withIncludedSpecVersion(Collections.emptyMap())
				.withIsReleased(1L)
				.withOwners(list(USER1))
				.withSpec(MYMOD_SPEC)
				.withTypes(ImmutableMap.of("MyMod.Trivial-1.0", MYMOD_TRIVIAL_SCHEMA))
				.withVer(releaseModVer);
		final ModuleInfo modD = DELEGATION_CLIENT.getModuleInfo(
				new GetModuleInfoParams().withMod("MyMod"));
		checkModuleInfo(modD, expectedModuleInfo);
		final ModuleInfo modT = TYPE_CLIENT.getModuleInfo(
				new GetModuleInfoParams().withMod("MyMod"));
		checkModuleInfo(modT, expectedModuleInfo);

		// getJsonSchema
		final String jsonD = DELEGATION_CLIENT.getJsonschema("MyMod.Trivial");
		assertThat("incorrect json", jsonD, is(MYMOD_TRIVIAL_SCHEMA));
		final String jsonT = TYPE_CLIENT.getJsonschema("MyMod.Trivial");
		assertThat("incorrect json", jsonT, is(MYMOD_TRIVIAL_SCHEMA));

		// listAllTypes
		final Map<String, Map<String, String>> expectedListAllTypes = ImmutableMap.of(
				"MyMod", ImmutableMap.of("Trivial", "1.0"));
		final Map<String, Map<String, String>> listAllTypesD = DELEGATION_CLIENT.listAllTypes(
				new ListAllTypesParams());
		assertThat("incorrect all types", listAllTypesD, is(expectedListAllTypes));
		final Map<String, Map<String, String>> listAllTypesT = TYPE_CLIENT.listAllTypes(
				new ListAllTypesParams());
		assertThat("incorrect all types", listAllTypesT, is(expectedListAllTypes));

		// translate to MD5 types
		final ImmutableMap<String, String> translateToExpected = ImmutableMap.of(
				"MyMod.Trivial-1.0", "MyMod.Trivial-afc7b655b7baedae2f237fc0c45b6579");
		final Map<String, String> md5typesD = DELEGATION_CLIENT.translateToMD5Types(
				list("MyMod.Trivial-1.0"));
		assertThat("incorrect MD5 types", md5typesD, is(translateToExpected));
		final Map<String, String> md5typesT = TYPE_CLIENT.translateToMD5Types(
				list("MyMod.Trivial-1.0"));
		assertThat("incorrect MD5 types", md5typesT, is(translateToExpected));

		// translate from MD5 types
		final ImmutableMap<String, List<String>> translateFromExpected = ImmutableMap.of(
				"MyMod.Trivial-afc7b655b7baedae2f237fc0c45b6579",
				list("MyMod.Trivial-0.1", "MyMod.Trivial-1.0"));
		final Map<String, List<String>> stdTypesD = DELEGATION_CLIENT.translateFromMD5Types(
				list("MyMod.Trivial-afc7b655b7baedae2f237fc0c45b6579"));
		assertThat("incorrect types", stdTypesD, is(translateFromExpected));
		final Map<String, List<String>> stdTypesT = TYPE_CLIENT.translateFromMD5Types(
				list("MyMod.Trivial-afc7b655b7baedae2f237fc0c45b6579"));
		assertThat("incorrect types", stdTypesT, is(translateFromExpected));

		// grantModuleOwnership
		DELEGATION_CLIENT.grantModuleOwnership(new GrantModuleOwnershipParams()
				.withMod("MyMod").withNewOwner(USER2));
		checkOwners("MyMod", DELEGATION_CLIENT, list(USER1, USER2));
		checkOwners("MyMod", TYPE_CLIENT, list(USER1, USER2));

		// removeModuleOwnership
		DELEGATION_CLIENT.removeModuleOwnership(new RemoveModuleOwnershipParams()
				.withMod("MyMod").withOldOwner(USER2));
		checkOwners("MyMod", DELEGATION_CLIENT, list(USER1));
		checkOwners("MyMod", TYPE_CLIENT, list(USER1));
	}

	@Test
	public void registerTypespecCopy() throws Exception {
		/* Copy a typespec from the remote server to the target type server via a delegating
		 * workspace
		 */

		createModule("MyMod2", REMOTE_CLIENT, REMOTE_CLIENT_ADMIN);

		final Map<String, String> reg = REMOTE_CLIENT.registerTypespec(
				new RegisterTypespecParams()
					.withDryrun(0L)
					.withNewTypes(list("Foo"))
					.withSpec(MYMOD2_SPEC)
				);
		assertThat("incorrect comp result", reg, is(ImmutableMap.of(
				"MyMod2.Foo-0.1", MYMOD2_FOO_SCHEMA)));

		final List<String> rel = REMOTE_CLIENT.releaseModule("MyMod2");
		assertThat("incorrect release", rel, is(list("MyMod2.Foo-1.0")));

		// The module must exist on the local workspace server to copy the typespec into it
		// The error message when this is not the case sucks
		createModule("MyMod2", DELEGATION_CLIENT, DELEGATION_CLIENT_ADMIN);

		final long modver = DELEGATION_CLIENT.registerTypespecCopy(
				new RegisterTypespecCopyParams()
						.withExternalWorkspaceUrl(REMOTE_SERVER_URL.toString())
						.withMod("MyMod2"));

		final ModuleInfo expected = new ModuleInfo()
				.withChsum("7356b72b399ea4112e6c538019ccd466")
				.withDescription("")
				.withFunctions(list())
				.withIncludedSpecVersion(Collections.emptyMap())
				.withIsReleased(0L)
				.withOwners(list(USER1))
				.withSpec(MYMOD2_SPEC)
				.withTypes(ImmutableMap.of("MyMod2.Foo-0.1", MYMOD2_FOO_SCHEMA))
				.withVer(modver);
		final ModuleInfo modinfD = DELEGATION_CLIENT.getModuleInfo(new GetModuleInfoParams()
				.withMod("MyMod2"));
		checkModuleInfo(modinfD, expected);
		final ModuleInfo modinfT = TYPE_CLIENT.getModuleInfo(new GetModuleInfoParams()
				.withMod("MyMod2"));
		checkModuleInfo(modinfT, expected);
	}

	@Test
	public void typeMethodFail() throws Exception {
		/* Tests a failure when calling a type method to ensure the workspace
		 * delegator handles exceptions correctly
		 */
		try {
			DELEGATION_CLIENT.getModuleInfo(new GetModuleInfoParams().withMod("Fake"));
			fail("expected exception");
		} catch (ServerException got) {
			TestCommon.assertExceptionCorrect(
					got, new ServerException("Module doesn't exist: Fake", -1, "fake"));
			assertThat(
					"check type server stack trace is in the exception data from the "
					+ "delegating server",
					got.getData(),
					containsString(LocalTypeServerMethods.class.getSimpleName()));
		}
	}

	@Test
	public void adminGrantAndRemoveModuleOwnership() throws Exception {
		createModule("MyMod2", DELEGATION_CLIENT, DELEGATION_CLIENT_ADMIN);

		DELEGATION_CLIENT_ADMIN.administer(new UObject(ImmutableMap.of(
				"command", "grantModuleOwnership",
				"params", new GrantModuleOwnershipParams()
				.withMod("MyMod2")
				// type DB bug, user names are not validated for this command
				.withNewOwner("user3"))));

		// another type DB bug - you can't get module info without registering a spec
		DELEGATION_CLIENT.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withNewTypes(list("Foo"))
			.withSpec(MYMOD2_SPEC)
		);

		checkOwners("MyMod2", DELEGATION_CLIENT, list(USER1, "user3"));
		checkOwners("MyMod2", TYPE_CLIENT, list(USER1, "user3"));

		DELEGATION_CLIENT_ADMIN.administer(new UObject(ImmutableMap.of(
				"command", "removeModuleOwnership",
				"params", new RemoveModuleOwnershipParams()
						.withMod("MyMod2")
						.withOldOwner("user3"))));

		checkOwners("MyMod2", DELEGATION_CLIENT, list(USER1));
		checkOwners("MyMod2", TYPE_CLIENT, list(USER1));
	}

	@Test
	public void adminMethodFail() throws Exception {
		/* Tests a failure when calling an admin method to ensure the workspace
		 * delegator handles exceptions correctly
		 */
		try {
			DELEGATION_CLIENT_ADMIN.administer(new UObject(ImmutableMap.of(
					"command", "approveModRequest",
					"module", "Fake")));
			fail("expected exception");
		} catch (ServerException got) {
			TestCommon.assertExceptionCorrect(
					got, new ServerException("There is no request for module Fake", -1, "fake"));
			assertThat(
					"check type server stack trace is in the exception data from the "
					+ "delegating server",
					got.getData(),
					containsString(TypeDefinitionDB.class.getSimpleName()));
		}
	}

	@Test
	public void saveObject() throws Exception {
		/* Tests saving an object to a workspace that delegates type info
		 * Double checked that delegation was actually happening by putting a print statement
		 * in the delegating type provider class
		 */
		createModule("MyMod2", TYPE_CLIENT, TYPE_CLIENT_ADMIN);
		TYPE_CLIENT.registerTypespec(new RegisterTypespecParams()
				.withDryrun(0L)
				.withNewTypes(list("Foo"))
				.withSpec(MYMOD2_SPEC)
			);
		TYPE_CLIENT.releaseModule("MyMod2");

		TYPE_CLIENT.createWorkspace(new CreateWorkspaceParams().withWorkspace("ws"));
		DELEGATION_CLIENT.saveObjects(new SaveObjectsParams()
				.withId(1L)
				.withObjects(list(new ObjectSaveData()
						.withName("mysuperneatobj")
						.withType("MyMod2.Foo")
						.withData(new UObject(ImmutableMap.of("foo", 42)))
						))
				);
		final ObjectData ret = TYPE_CLIENT.getObjects2(new GetObjects2Params()
				.withObjects(list(new ObjectSpecification().withRef("1/1")))).getData().get(0);
		assertThat("incorrect name", ret.getInfo().getE2(), is("mysuperneatobj"));
		assertThat("incorrect type", ret.getInfo().getE3(), is("MyMod2.Foo-1.0"));
		assertThat("incorrect data", ret.getData().asClassInstance(Map.class),
				is(ImmutableMap.of("foo", 42)));
	}

	@Test
	public void saveObjectFail() throws Exception {
		/* Tests a few cases of type related failures to save an object. In particular, checks
		 * that errors thrown from the delegating type provider are handled correctly.
		 * Not really easily possible to test a TypeFetchError, can test it manually by
		 * uncommenting the line below that shuts down the type server.
		 */
		TYPE_CLIENT.createWorkspace(new CreateWorkspaceParams().withWorkspace("ws"));
		TYPE_CLIENT.setPermissions(new SetPermissionsParams()
				.withId(1L).withUsers(list(USER2)).withNewPermission("w"));
		final SaveObjectsParams p = new SaveObjectsParams()
				.withId(1L)
				.withObjects(list(new ObjectSaveData()
						.withName("mysuperneatobj")
						.withType("MyMod2.Foo")
						.withData(new UObject(ImmutableMap.of("foo", 42)))
						));
		// TYPE_SERVER.stopServer();

		// no such module errors
		failSaveObjects(DELEGATION_CLIENT_ADMIN, p,
				"Object #1, mysuperneatobj failed type checking: Module doesn't exist: MyMod2");
		// Although there are other no such module errors in the type DB code path, can't seem to
		// figure out how to trigger them without an in depth code review - if it's even possible

		createModule("MyMod2", TYPE_CLIENT, TYPE_CLIENT_ADMIN);

		// no such type errors
		failSaveObjects(DELEGATION_CLIENT_ADMIN, p,
				"Object #1, mysuperneatobj failed type checking: Unable to locate type: "
						+ "MyMod2.Foo");

		TYPE_CLIENT.registerTypespec(new RegisterTypespecParams()
				.withDryrun(0L)
				.withNewTypes(list("Foo"))
				.withSpec(MYMOD2_SPEC)
			);

		failSaveObjects(DELEGATION_CLIENT_ADMIN, p,
				"Object #1, mysuperneatobj failed type checking: This type wasn't released yet "
				+ "and you should be an owner to access unreleased version information");
	}

	private void failSaveObjects(
			final WorkspaceClient cli,
			final SaveObjectsParams params,
			final String expectedException) {
		try {
			cli.saveObjects(params);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(
					got, new ServerException(expectedException, -1, "foo"));
		}
	}

	private void checkModuleInfo(final ModuleInfo got, final ModuleInfo expected) {
		// the SDK classes not having equals sucks
		assertThat("incorrect add props",
				got.getAdditionalProperties(), is(expected.getAdditionalProperties()));
		assertThat("incorrect chsum", got.getChsum(), is(expected.getChsum()));
		assertThat("incorrect desc", got.getDescription(), is(expected.getDescription()));
		assertThat("incorrect funcs", got.getFunctions(), is(expected.getFunctions()));
		assertThat("incorrect incl spec vers",
				got.getIncludedSpecVersion(), is(expected.getIncludedSpecVersion()));
		assertThat("incorrect is rel", got.getIsReleased(), is(expected.getIsReleased()));
		assertThat("incorrect owners", got.getOwners(), is(expected.getOwners()));
		assertThat("incorrect spec", got.getSpec(), is(expected.getSpec()));
		assertThat("incorrect types", got.getTypes(), is(expected.getTypes()));
		assertThat("incorrect ver", got.getVer(), is(expected.getVer()));
	}

	private void checkTypeInfo(final TypeInfo got, final TypeInfo expected) {
		// the SDK classes not having equals suucks
		assertThat("incorrect add props",
				got.getAdditionalProperties(), is(expected.getAdditionalProperties()));
		assertThat("incorrect desc", got.getDescription(), is(expected.getDescription()));
		assertThat("incorrect schema",
				got.getJsonSchema(), is(expected.getJsonSchema()));
		assertThat("incorrect mod vers", got.getModuleVers(), is(expected.getModuleVers()));
		assertThat("incorrect parsing struct",
				got.getParsingStructure(), is(expected.getParsingStructure()));
		assertThat("incorrect released mod vers",
				got.getReleasedModuleVers(), is(expected.getReleasedModuleVers()));
		assertThat("incorrect released tvers",
				got.getReleasedTypeVers(), is(expected.getReleasedTypeVers()));
		assertThat("incorrect spec def", got.getSpecDef(), is(expected.getSpecDef()));
		assertThat("incorrect type def", got.getTypeDef(), is(expected.getTypeDef()));
		assertThat("incorrect tvers", got.getTypeVers(), is(expected.getTypeVers()));
		assertThat("incorrect used tdefs", got.getUsedTypeDefs(), is(expected.getUsedTypeDefs()));
		assertThat("incorrect using fdefs",
				got.getUsingFuncDefs(), is(expected.getUsingFuncDefs()));
		assertThat("incorrect using tdefs",
				got.getUsingTypeDefs(), is(expected.getUsingTypeDefs()));
	}

	@SuppressWarnings("deprecation")
	private void checkFuncInfo(
			final us.kbase.workspace.FuncInfo got,
			final us.kbase.workspace.FuncInfo expected) {
		// the SDK classes not having equals suuuucks
		assertThat("incorrect add props",
				got.getAdditionalProperties(), is(expected.getAdditionalProperties()));
		assertThat("incorrect desc", got.getDescription(), is(expected.getDescription()));
		assertThat("incorrect fdef", got.getFuncDef(), is(expected.getFuncDef()));
		assertThat("incorrect fvers", got.getFuncVers(), is(expected.getFuncVers()));
		assertThat("incorrect mod vers", got.getModuleVers(), is(expected.getModuleVers()));
		assertThat("incorrect parsing struct",
				got.getParsingStructure(), is(expected.getParsingStructure()));
		assertThat("incorrect released fvers",
				got.getReleasedFuncVers(), is(expected.getReleasedFuncVers()));
		assertThat("incorrect released mod vers",
				got.getReleasedModuleVers(), is(expected.getReleasedModuleVers()));
		assertThat("incorrect spec def", got.getSpecDef(), is(expected.getSpecDef()));
		assertThat("incorrect used tdefs", got.getUsedTypeDefs(), is(expected.getUsedTypeDefs()));
	}

	private void checkModVers(final ModuleVersions got, final ModuleVersions expected) {
		// the SDK classes not having equals suuuuuuuucks
		assertThat("incorrect add props",
				got.getAdditionalProperties(), is(expected.getAdditionalProperties()));
		assertThat("incorrect mod", got.getMod(), is(expected.getMod()));
		assertThat("incorrect rel vers", got.getReleasedVers(), is(expected.getReleasedVers()));
		assertThat("incorrect vers", got.getVers(), is(expected.getVers()));
	}
}
