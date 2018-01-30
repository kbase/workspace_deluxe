package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static us.kbase.common.test.TestCommon.assertExceptionCorrect;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.LoggerFactory;

import us.kbase.auth.AuthConfig;
import us.kbase.auth.AuthToken;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.TestException;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.common.test.controllers.shock.ShockController;
import us.kbase.typedobj.core.LocalTypeProvider;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.ListObjectsParameters;
import us.kbase.workspace.database.ObjIDWithRefPathAndSubset;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIDWithRefPath;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Provenance.SubAction;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.Provenance.ExternalData;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.Provenance.ProvenanceAction;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.mongo.BlobStore;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.ShockBlobStore;
import us.kbase.workspace.test.JsonTokenStreamOCStat;
import us.kbase.workspace.test.WorkspaceTestCommon;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.mongodb.DB;

@RunWith(Parameterized.class)
public class WorkspaceTester {
	
	//true if no net access since shock requires access to globus to work
	private static final boolean SKIP_SHOCK = false;

	protected static final ObjectMapper MAPPER = new ObjectMapper();
	
	protected static final String LONG_TEXT_PART =
			"Passersby were amazed by the unusually large amounts of blood. ";
	protected static String LONG_TEXT = "";
	static {
		for (int i = 0; i < 17; i++) {
			LONG_TEXT += LONG_TEXT_PART;
		}
	}
	protected static String TEXT100 = "";
	static {
		for (int i = 0; i < 10; i++) {
			TEXT100 += "aaaaabbbbb";
		}
	}
	protected static String TEXT101 = TEXT100 + "f";
	protected static String TEXT255 = TEXT100 + TEXT100 + TEXT100.substring(0, 55);
	protected static String TEXT256 = TEXT255 + "f";
	protected static String TEXT1000 = "";
	static {
		for (int i = 0; i < 10; i++) {
			TEXT1000 += TEXT100;
		}
	}
	
	static {
		//stfu EasyStream
		((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
				.setLevel(Level.OFF);
	}
	
	protected static final Map<String, String> MT_MAP = new HashMap<>();
	protected static final List<String> MT_LIST = new LinkedList<>();
	
	public static final String DB_WS_NAME = "WorkspaceBackendTest";
	public static final String DB_TYPE_NAME = "WorkspaceBackendTest_types";
			
	
	private static MongoController mongo = null;
	private static ShockController shock = null;
	protected static TempFilesManager tfm;
	
	protected static final WorkspaceUser SOMEUSER = new WorkspaceUser("auser");
	protected static final WorkspaceUser AUSER = new WorkspaceUser("a");
	protected static final WorkspaceUser BUSER = new WorkspaceUser("b");
	protected static final WorkspaceUser CUSER = new WorkspaceUser("c");
	protected static final AllUsers STARUSER = new AllUsers('*');
	
	protected static final TypeDefId SAFE_TYPE1 =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);
	protected static final TypeDefId SAFE_TYPE2 =
			new TypeDefId(new TypeDefName("SomeModule", "AType2"), 0, 1);
	protected static final TypeDefId SAFE_TYPE1_10 =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 1, 0);
	protected static final TypeDefId SAFE_TYPE2_10 =
			new TypeDefId(new TypeDefName("SomeModule", "AType2"), 1, 0);
	protected static final TypeDefId SAFE_TYPE1_20 =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 2, 0);
	protected static final TypeDefId SAFE_TYPE2_20 =
			new TypeDefId(new TypeDefName("SomeModule", "AType2"), 2, 0);
	protected static final TypeDefId SAFE_TYPE2_21 =
			new TypeDefId(new TypeDefName("SomeModule", "AType2"), 2, 1);
	
	protected static final TypeDefId REF_TYPE =
			new TypeDefId(new TypeDefName("CopyRev", "RefType"), 0, 1);

	static {
		JsonTokenStreamOCStat.register();
	}
	
	@Parameters
	public static Collection<Object[]> generateData() throws Exception {
		printMem("*** startup ***");
		List<Object[]> tests;
		if (SKIP_SHOCK) {
			System.out.println("Skipping shock backend tests");
			tests = Arrays.asList(new Object[][] {
					{"mongo", "mongo", null},
					{"mongoUseFile", "mongo", 1}
			});
		} else {
			tests = Arrays.asList(new Object[][] {
					{"mongo", "mongo", null},
					{"mongoUseFile", "mongo", 1},
					{"shock", "shock", null}
			});
		}
		printMem("*** startup complete ***");
		return tests;
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (shock != null) {
			shock.destroy(TestCommon.getDeleteTempFiles());
		}
		if (mongo != null) {
			mongo.destroy(TestCommon.getDeleteTempFiles());
		}
		System.out.println("deleting temporary files");
		tfm.cleanup();
		JsonTokenStreamOCStat.showStat();
	}
	
	@Before
	public void clearDB() throws Exception {
		DB wsdb = GetMongoDB.getDB("localhost:" + mongo.getServerPort(),
				DB_WS_NAME);
		TestCommon.destroyDB(wsdb);
	}
	
	@After
	public void after() throws Exception {
		TestCommon.assertNoTempFilesExist(tfm);
	}
	
	private static final Map<String, WSandTypes> CONFIGS =
			new HashMap<String, WSandTypes>();
	protected final Workspace ws;
	protected final Types types;
	
	public WorkspaceTester(String config, String backend,
			Integer maxMemoryUsePerCall)
			throws Exception {
		if (mongo == null) {
			mongo = new MongoController(TestCommon.getMongoExe(),
					Paths.get(TestCommon.getTempDir()),
					TestCommon.useWiredTigerEngine());
			System.out.println("Using Mongo temp dir " + mongo.getTempDir());
			System.out.println("Started test mongo instance at localhost:" +
					mongo.getServerPort());
		}
		if (!CONFIGS.containsKey(config)) {
			DB wsdb = GetMongoDB.getDB("localhost:" + mongo.getServerPort(),
					DB_WS_NAME);
			WorkspaceTestCommon.destroyWSandTypeDBs(wsdb,
					DB_TYPE_NAME);
			System.out.println("Starting test suite with parameters:");
			System.out.println(String.format(
					"\tConfig: %s, Backend: %s, MaxMemPerCall: %s",
					config, backend, maxMemoryUsePerCall));
			if ("shock".equals(backend)) {
				CONFIGS.put(config, setUpShock(wsdb, maxMemoryUsePerCall));
			} else if("mongo".equals(backend)) {
				CONFIGS.put(config, setUpMongo(wsdb, maxMemoryUsePerCall));
			} else {
				throw new TestException("Unknown backend: " + config);
			}
		}
		ws = CONFIGS.get(config).ws;
		types = CONFIGS.get(config).types;
	}
	
	private static class WSandTypes {
		public Workspace ws;
		public Types types;
		public WSandTypes(Workspace ws, Types types) {
			super();
			this.ws = ws;
			this.types = types;
		}
	}
	
	private WSandTypes setUpMongo(DB wsdb, Integer maxMemoryUsePerCall)
			throws Exception {
		return setUpWorkspaces(wsdb, new GridFSBlobStore(wsdb),
				maxMemoryUsePerCall);
	}
	
	private WSandTypes setUpShock(DB wsdb, Integer maxMemoryUsePerCall)
			throws Exception {
		final ConfigurableAuthService auth = new ConfigurableAuthService(
				new AuthConfig().withKBaseAuthServerURL(
						TestCommon.getAuthUrl()));
		System.out.println(String.format("Logging in shock user at %s",
				TestCommon.getAuthUrl()));
		final AuthToken t = TestCommon.getToken(1, auth);
		if (shock == null) {
			shock = new ShockController(
					TestCommon.getShockExe(),
					TestCommon.getShockVersion(),
					Paths.get(TestCommon.getTempDir()),
					"***---fakeuser---***",
					"localhost:" + mongo.getServerPort(),
					"WorkspaceTester_ShockDB",
					"foo",
					"foo",
					TestCommon.getGlobusUrl());
			System.out.println("Shock controller version: " +
					shock.getVersion());
			if (shock.getVersion() == null) {
				System.out.println(
						"Unregistered version - Shock may not start correctly");
			}
			System.out.println("Using Shock temp dir " + shock.getTempDir());
		}
		URL shockUrl = new URL("http://localhost:" + shock.getServerPort());
		BlobStore bs = new ShockBlobStore(wsdb.getCollection("shock_nodes"), shockUrl, t);
		return setUpWorkspaces(wsdb, bs, maxMemoryUsePerCall);
	}
	
	private WSandTypes setUpWorkspaces(
			DB db,
			BlobStore bs,
			Integer maxMemoryUsePerCall)
					throws Exception {
		
		tfm = new TempFilesManager(
				new File(TestCommon.getTempDir()));
		tfm.cleanup();
		
		final TypeDefinitionDB typeDefDB = new TypeDefinitionDB(
				new MongoTypeStorage(GetMongoDB.getDB(
						"localhost:" + mongo.getServerPort(),
						DB_TYPE_NAME)));
		TypedObjectValidator val = new TypedObjectValidator(
				new LocalTypeProvider(typeDefDB));
		MongoWorkspaceDB mwdb = new MongoWorkspaceDB(db, bs, tfm);
		Workspace work = new Workspace(mwdb, new ResourceUsageConfigurationBuilder().build(), val);
		Types t = new Types(typeDefDB);
		if (maxMemoryUsePerCall != null) {
			final ResourceUsageConfigurationBuilder build =
					new ResourceUsageConfigurationBuilder(work.getResourceConfig());
			work.setResourceConfig(build.withMaxIncomingDataMemoryUsage(maxMemoryUsePerCall)
					.withMaxReturnedDataMemoryUsage(maxMemoryUsePerCall).build());
		}
		installSpecs(t);
		return new WSandTypes(work, t);
	}
		
	private void installSpecs(Types t) throws Exception {
		//make a general spec that tests that don't worry about typechecking can use
		WorkspaceUser foo = new WorkspaceUser("foo");
		//simple spec
		t.requestModuleRegistration(foo, "SomeModule");
		t.resolveModuleRegistration("SomeModule", true);
		t.compileNewTypeSpec(foo, 
				"module SomeModule {" +
					"/* @optional thing */" +
					"typedef structure {" +
						"string thing;" +
					"} AType;" +
					"/* @optional thing */" +
					"typedef structure {" +
						"string thing;" +
					"} AType2;" +
				"};",
				Arrays.asList("AType", "AType2"), null, null, false, null);
		t.releaseTypes(foo, "SomeModule");
		t.compileNewTypeSpec(foo, 
				"module SomeModule {" +
					"typedef structure {" +
						"string thing;" +
					"} AType;" +
					"typedef structure {" +
						"string thing;" +
					"} AType2;" +
				"};",
				null, null, null, false, null);
		t.releaseTypes(foo, "SomeModule");
		t.compileNewTypeSpec(foo, 
				"module SomeModule {" +
					"typedef structure {" +
						"string thing;" +
					"} AType;" +
					"/* @optional thing2 */" +
					"typedef structure {" +
						"string thing;" +
						"string thing2;" +
					"} AType2;" +
				"};",
				null, null, null, false, null);
		t.releaseTypes(foo, "SomeModule");
		
		//spec that simply references another object
		final String specRefType =
				"module CopyRev {" +
					"/* @id ws */" +
					"typedef string reference;" +
					"typedef structure {" +
						"list<reference> refs;" +
					"} RefType;" +
				"};";
		
		String mod = "CopyRev";
		t.requestModuleRegistration(foo, mod);
		t.resolveModuleRegistration(mod, true);
		t.compileNewTypeSpec(foo, specRefType, Arrays.asList("RefType"), null, null, false, null);
		t.releaseTypes(foo, mod);

		// more complicated spec with two released versions and 1 unreleased version for type
		// registration tests
		t.requestModuleRegistration(foo, "TestModule");
		t.resolveModuleRegistration("TestModule", true);
		t.compileNewTypeSpec(foo, 
				"module TestModule { " +
						"typedef structure {string name; string seq;} Feature; "+
						"typedef structure {string name; list<Feature> features;} Genome; "+
						"typedef structure {string private_stuff;} InternalObj; "+
						"funcdef getFeature(string fid, string pattern) returns (Feature);" +
						"};",
						Arrays.asList("Feature","Genome"), null, null, false, null);
		t.releaseTypes(foo, "TestModule");
		t.compileNewTypeSpec(foo, 
				"module TestModule { " +
						"typedef structure {string name; string seq;} Feature; "+
						"typedef structure {string name; list<Feature> feature_list;} Genome; "+
						"typedef structure {string private_stuff;} InternalObj; "+
						"funcdef getFeature(string fid) returns (Feature);" +
						"};",
						null, null, null, false, null);
		t.compileNewTypeSpec(foo, 
				"module TestModule { " +
						"typedef structure {string name; string seq;} Feature; "+
						"typedef structure {string name; list<Feature> feature_list;} Genome; "+
						"typedef structure {string private_stuff;} InternalObj; "+
						"funcdef getFeature(string fid) returns (Feature);" +
						"funcdef getGenome(string gid) returns (Genome);" +
						"};",
						null, null, null, false, null);
		t.releaseTypes(foo, "TestModule");

		t.requestModuleRegistration(foo, "UnreleasedModule");
		t.resolveModuleRegistration("UnreleasedModule", true);
		t.compileNewTypeSpec(foo, 
				"module UnreleasedModule {/* @optional thing */ typedef structure {string thing;} AType; funcdef aFunc(AType param) returns ();};",
				Arrays.asList("AType"), null, null, false, null);
	}
	
	private static void printMem(String startmsg) {
		System.out.println(startmsg);
		System.out.println("free mem: " + Runtime.getRuntime().freeMemory());
		System.out.println(" max mem: " + Runtime.getRuntime().maxMemory());
		System.out.println(" ttl mem: " + Runtime.getRuntime().maxMemory());
	}
	
	private static String lastRandomName = null;
	
	protected static String getLastRandomName() {
		return lastRandomName;
	}
	
	protected static ObjectIDNoWSNoVer getRandomName() {
		//since UUIDs can be all #s
		lastRandomName = "a" + UUID.randomUUID().toString().replace("-", "");
		return new ObjectIDNoWSNoVer(lastRandomName);
	}
	

	protected List<WorkspaceSaveObject> setRandomNames(final List<WorkspaceSaveObject> data) {
		final List<WorkspaceSaveObject> ret = new LinkedList<>();
		for (final WorkspaceSaveObject d: data) {
			ret.add(new WorkspaceSaveObject(getRandomName(), d.getData(), d.getType(),
					d.getUserMeta(), d.getProvenance(), d.isHidden()));
		}
		return ret;
	}
	
	protected IdReferenceHandlerSetFactory getIdFactory() {
		return new IdReferenceHandlerSetFactory(100000);
	}
	
	protected Object getData(final WorkspaceObjectData wod) throws Exception {
		return wod.getSerializedData().getUObject().asClassInstance(Object.class);
	}
	
	protected void failSetWSDesc(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final String description,
			final Exception e)
			throws Exception {
		failSetWSDesc(ws, user, wsi, description, e);
	}
	
	public static void failSetWSDesc(
			final Workspace ws,
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final String description,
			final Exception e)
			throws Exception {
		try {
			ws.setWorkspaceDescription(user, wsi, description);
			fail("set ws desc when should fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}

	protected void checkWSInfo(
			final WorkspaceIdentifier wsi,
			final WorkspaceUser owner,
			final String name,
			final long objs,
			final Permission perm,
			final boolean globalread,
			final long id,
			final Instant moddate,
			final String lockstate,
			final Map<String, String> meta)
			throws Exception {
		checkWSInfo(ws.getWorkspaceInformation(owner, wsi), owner, name, objs,
				perm, globalread, id, moddate, lockstate, meta);
	}
	
	protected Instant checkWSInfo(WorkspaceIdentifier wsi, WorkspaceUser owner, String name,
			long objs, Permission perm, boolean globalread, long id,
			String lockstate, Map<String, String> meta) throws Exception {
		WorkspaceInformation info = ws.getWorkspaceInformation(owner, wsi);
		checkWSInfo(info, owner, name, objs, perm, globalread, lockstate, meta);
		assertThat("ws id correct", info.getId(), is(id));
		return info.getModDate();
	}
	
	protected void checkWSInfo(
			final WorkspaceInformation info,
			final WorkspaceUser owner,
			final String name,
			final long objs,
			final Permission perm,
			final boolean globalread,
			final long id,
			final Instant moddate,
			final String lockstate,
			final Map<String, String> meta) {
		checkWSInfo(info, owner, name, objs, perm, globalread, lockstate, meta);
		assertThat("ws id correct", info.getId(), is(id));
		assertThat("ws mod date correct", info.getModDate(), is(moddate));
	}
	
	protected void checkWSInfo(WorkspaceInformation info, WorkspaceUser owner, String name,
			long objs, Permission perm, boolean globalread, String lockstate,
			Map<String, String> meta) {
		assertDateisRecent(info.getModDate());
		assertThat("ws owner correct", info.getOwner(), is(owner));
		assertThat("ws name correct", info.getName(), is(name));
		assertThat("ws max obj correct", info.getMaximumObjectID(), is(objs));
		assertThat("ws permissions correct", info.getUserPermission(), is(perm));
		assertThat("ws global read correct", info.isGloballyReadable(), is(globalread));
		assertThat("ws lockstate correct", info.getLockState(), is(lockstate));
		assertThat("ws meta correct", info.getUserMeta().getMetadata(), is(meta));
	}
	
	protected void assertDatesAscending(final Instant... dates) {
		for (int i = 1; i < dates.length; i++) {
			assertTrue("dates are ascending", dates[i-1].isBefore(dates[i]));
		}
	}
	
	protected void failWSMeta(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final String key,
			final String value,
			final Exception e)
			throws Exception {
		failWSMeta(ws, user, wsi, key, value, e);
	}
	
	public static void failWSMeta(
			final Workspace ws,
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final String key,
			final String value,
			final Exception e)
			throws Exception {
		failWSRemoveMeta(ws, user, wsi, key, e);
		Map<String, String> meta = new HashMap<String, String>();
		meta.put(key, value);
		failWSSetMeta(ws, user, wsi, meta, Collections.emptyList(), e);
	}

	protected void failWSRemoveMeta(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final String key,
			final Exception e) {
		failWSRemoveMeta(ws, user, wsi, key, e);
	}
	
	public static void failWSRemoveMeta(
			final Workspace ws,
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final String key,
			final Exception e) {
		try {
			ws.setWorkspaceMetadata(user, wsi, null, Arrays.asList(key));
			fail("expected remove ws meta to fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}

	protected void failWSSetMeta(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final Map<String, String> meta,
			final Exception e) {
		failWSSetMeta(ws, user, wsi, meta, Collections.emptyList(), e);
	}
	
	public static void failWSSetMeta(
			final Workspace ws,
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final Map<String, String> meta,
			final List<String> remove,
			final Exception e) {
		try {
			ws.setWorkspaceMetadata(user, wsi, new WorkspaceUserMetadata(meta), remove);
			fail("expected set ws meta to fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected void failCreateWorkspace(WorkspaceUser user, String name,
			boolean global, Map<String,String> meta, String description, Exception e)
			throws Exception {
		try {
			ws.createWorkspace(user, name, global, description,
					new WorkspaceUserMetadata(meta));
			fail("created workspace w/ bad args");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected void failSetPermissions(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final List<WorkspaceUser> users,
			final Permission perm, 
			final Exception e)
			throws Exception {
		failSetPermissions(ws, user, wsi, users, perm, e);
	}
	
	public static void failSetPermissions(
			final Workspace ws,
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final List<WorkspaceUser> users,
			final Permission perm, 
			final Exception e)
			throws Exception {
		try {
			ws.setPermissions(user, wsi, users, perm);
			fail("set perms when should fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected void failGetPermissions(
			final WorkspaceUser user,
			final List<WorkspaceIdentifier> wsis,
			final Exception e)
			throws Exception {
		failGetPermissions(ws, user, wsis, e);
	}
	
	public static void failGetPermissions(
			final Workspace ws,
			final WorkspaceUser user,
			final List<WorkspaceIdentifier> wsis,
			final Exception e)
			throws Exception {
		try {
			ws.getPermissions(user, wsis);
			fail("get perms when should fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected void failGetPermissionsAsAdmin(
			final List<WorkspaceIdentifier> wsis,
			final Exception e) {
		try {
			ws.getPermissionsAsAdmin(wsis);
			fail("get perms as admin should fail");
		} catch (Exception got) {
			assertExceptionCorrect(got, e);
		}
	}
	
	protected void checkObjInfo(
			final ObjectInformation info,
			final long id,
			final String name,
			final String type,
			final int version,
			final WorkspaceUser user,
			final long wsid,
			final String wsname,
			final String chksum,
			final long size,
			final Map<String, String> usermeta,
			final List<Reference> refpath) {
		
		assertDateisRecent(info.getSavedDate());
		assertThat("Object id incorrect", info.getObjectId(), is(id));
		assertThat("Object name is incorrect", info.getObjectName(), is(name));
		assertThat("Object type is incorrect", info.getTypeString(), is(type));
		assertThat("Object version is incorrect", info.getVersion(), is(version));
		assertThat("Object user is incorrect", info.getSavedBy(), is(user));
		assertThat("Object workspace id is incorrect", info.getWorkspaceId(), is(wsid));
		assertThat("Object workspace name is incorrect", info.getWorkspaceName(), is(wsname));
		assertThat("Object chksum is incorrect", info.getCheckSum(), is(chksum));
		assertThat("Object size is incorrect", info.getSize(), is(size));
		Map<String, String> meta = info.getUserMetaData() == null ? null :
			info.getUserMetaData().getMetadata();
		assertThat("Object user meta is incorrect", meta, is(usermeta));
		assertThat("Object refpath incorrect", info.getReferencePath(), is(refpath));
	}
	
	protected void assertDateisRecent(final Date orig) {
		assertDateisRecent(orig.toInstant());
	}
	
	protected void assertDateisRecent(final Instant orig) {
		final Instant now = Instant.now();
		assertThat("date is older than 1m", orig.until(now, ChronoUnit.SECONDS) < 60, is(true));
		assertThat("date is in future", now.compareTo(orig) >= 0, is(true));
	}

	protected Instant assertWorkspaceDateUpdated(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final Instant lastDate,
			final String assertion)
			throws Exception {
		final Instant readCurrentDate = ws.getWorkspaceInformation(user, wsi).getModDate();
		assertTrue(assertion, readCurrentDate.isAfter(lastDate));
		return readCurrentDate;
	}
	
	protected void failSave(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final ObjectIDNoWSNoVer id,
			final Map<String, Object> data,
			final TypeDefId type,
			final Provenance prov,
			final Exception exception)
			throws Exception {
		final WorkspaceSaveObject wso = new WorkspaceSaveObject(id, data, type, null, prov, false);
		failSave(user, wsi, Arrays.asList(wso), exception);
	}
	
	protected void failSave(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi, 
			final String objectName,
			final Map<String, Object> data,
			final TypeDefId type,
			final Provenance prov,
			final Exception exception)
			throws Exception {
		failSave(user, wsi, new ObjectIDNoWSNoVer(objectName), data, type, prov, exception);
	}
	
	protected void failSave(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final List<WorkspaceSaveObject> wso,
			final Exception exp)
					throws Exception {
		failSave(user, wsi, wso, getIdFactory(), exp);
	}
	
	protected void failSave(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final List<WorkspaceSaveObject> wso,
			final IdReferenceHandlerSetFactory fac,
			final Exception exp)
			throws Exception {
		failSave(ws, user, wsi, wso, fac, exp);
	}
	
	public static void failSave(
			final Workspace ws,
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final List<WorkspaceSaveObject> wso,
			final IdReferenceHandlerSetFactory fac,
			final Exception exp)
			throws Exception {
		try {
			ws.saveObjects(user, wsi, wso, fac);
			fail("Saved bad objects");
		} catch (Exception e) {
			assertExceptionCorrect(e, exp);
		}
	}
	
	protected void checkObjectAndInfoWithNulls(WorkspaceUser user,
			List<ObjectIdentifier> ids, List<ObjectInformation> expected,
			List<Map<String, Object>> expdata) throws Exception {
		List<WorkspaceObjectData> gotdata = ws.getObjects(user, ids, false, true, false);
		try {
			List<WorkspaceObjectData> gotprov = ws.getObjects(user, ids, true, true, false);
			List<ObjectInformation> gotinfo =
					ws.getObjectInformation(user, ids, true, true);
			Iterator<WorkspaceObjectData> gotdatai = gotdata.iterator();
			Iterator<WorkspaceObjectData> gotprovi = gotprov.iterator();
			Iterator<ObjectInformation> gotinfoi = gotinfo.iterator();
			Iterator<ObjectInformation> expinfoi = expected.iterator();
			Iterator<Map<String, Object>> expdatai = expdata.iterator();
			while (gotdatai.hasNext()) {
				ObjectInformation einf = expinfoi.next();
				Map<String, Object> edata = expdatai.next();
				WorkspaceObjectData gprov = gotprovi.next();
				ObjectInformation ginf = gotinfoi.next();
				WorkspaceObjectData gdata = gotdatai.next();
				if (einf == null) {
					assertNull("expected null prov", gprov);
					assertNull("expected null info", ginf);
					assertNull("expected null data", gdata);
				} else {
					@SuppressWarnings("unchecked")
					Map<String, Object> d =
					(Map<String, Object>) getData(gdata);
					assertThat("got expected obj info from getInfo",
							ginf, is(einf));
					assertThat("got expected obj info from getObj",
							gdata.getObjectInfo(), is(einf));
					assertThat("got expected obj info from getObjProv",
							gprov.getObjectInfo(), is(einf));
					assertThat("got expected data", d, is(edata));
				}
			}
			if (expinfoi.hasNext() || expdatai.hasNext() || gotprovi.hasNext() ||
					gotinfoi.hasNext() || gotdatai.hasNext()) {
				fail("mismatched iter counts");
			}
		} finally {
			destroyGetObjectsResources(gotdata);
		}
	}

	// only checks that the received object info->saved date is recent, doesn't directly compare to
	// provided obj info. 
	protected void checkObjectAndInfo(
			final WorkspaceUser user,
			final List<ObjectIdentifier> ids,
			final List<ObjectInformation> objinfo,
			final List<Map<String, Object>> data)
			throws Exception {
		final List<WorkspaceObjectData> retdata = ws.getObjects(user, ids);
		try {
			final List<WorkspaceObjectData> provdata = ws.getObjects(user, ids, true);
			final Iterator<WorkspaceObjectData> ret1 = retdata.iterator();
			final Iterator<WorkspaceObjectData> provi = provdata.iterator();
			final Iterator<ObjectInformation> info = objinfo.iterator();
			final Iterator<Map<String, Object>> dataiter = data.iterator();
			while (ret1.hasNext()) {
				final ObjectInformation inf = info.next();
				final Map<String, Object> d = dataiter.next();
				final WorkspaceObjectData woprov = provi.next();
				final WorkspaceObjectData wod1 = ret1.next();
				checkObjectAndInfo(wod1, inf, d);
				checkObjInfo(
						woprov.getObjectInfo(),
						inf.getObjectId(),
						inf.getObjectName(),
						inf.getTypeString(),
						inf.getVersion(),
						inf.getSavedBy(),
						inf.getWorkspaceId(),
						inf.getWorkspaceName(),
						inf.getCheckSum(),
						inf.getSize(),
						inf.getUserMetaData().getMetadata(),
						inf.getReferencePath());
			}
			if (info.hasNext() || dataiter.hasNext() || provi.hasNext()) {
				fail("mismatched iter counts");
			}
		} finally {
			destroyGetObjectsResources(retdata);
		}
	}
	
	// only checks that the wod->object info->saved date is recent, doesn't directly compare to
	// provided obj info. 
	protected void checkObjectAndInfo(
			final WorkspaceObjectData wod,
			final ObjectInformation info,
			final Map<String, Object> data)
			throws Exception {
		checkObjInfo(
				wod.getObjectInfo(),
				info.getObjectId(),
				info.getObjectName(),
				info.getTypeString(),
				info.getVersion(),
				info.getSavedBy(),
				info.getWorkspaceId(),
				info.getWorkspaceName(),
				info.getCheckSum(),
				info.getSize(),
				info.getUserMetaData().getMetadata(),
				info.getReferencePath());
		assertThat("correct data", getData(wod), is((Object) data));
		
	}

	protected void successGetObjects(WorkspaceUser user,
			List<ObjectIdentifier> objs) throws Exception {
		destroyGetObjectsResources(ws.getObjects(user, objs));
		destroyGetObjectsResources(ws.getObjects(user, objs, false));
	}
	
	public static void destroyGetObjectsResources(
			final List<WorkspaceObjectData> objects) {
		for (WorkspaceObjectData wo: objects) {
			try {
				if (wo != null) {
					wo.destroy();
				}
			} catch (RuntimeException | Error e) {
				//continue;
			}
		}
	}

	protected void failGetObjects(
			final WorkspaceUser user,
			final List<ObjectIdentifier> objs,
			final Exception e) 
			throws Exception {
		failGetObjects(user, objs, e, false);
	}
	
	protected void failGetObjects(
			final WorkspaceUser user,
			final List<ObjectIdentifier> objs,
			final Exception e,
			final boolean onlyCheckReturningData) 
			throws Exception {
		failGetObjects(ws, user, objs, e, onlyCheckReturningData);
	}
	
	public static void failGetObjects(
			final Workspace ws,
			final WorkspaceUser user,
			final List<ObjectIdentifier> objs,
			final Exception e,
			final boolean onlyCheckReturningData) 
			throws Exception {
		try {
			ws.getObjects(user, objs);
			fail("called get objects with bad args");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
		if (onlyCheckReturningData) {
			return;
		}
		try {
			ws.getObjects(user, objs, true);
			fail("called get objects with bad args");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
		try {
			ws.getObjectInformation(user, objs, true, false);
			fail("called get objects with bad args");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
		try {
			@SuppressWarnings({ "deprecation", "unused" })
			List<Integer> f = ws.getReferencingObjectCounts(user, objs);
			fail("called get refing objects with bad args");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected void failGetReferencedObjects(
			final WorkspaceUser user,
			final List<ObjectIDWithRefPath> objs,
			final Exception e)
			throws Exception {
		
		failGetReferencedObjects(user, objs, e, false);
	}
	
	protected void failGetReferencedObjects(
			final WorkspaceUser user,
			final List<ObjectIDWithRefPath> objs,
			final Exception e,
			final Set<Integer> nulls)
			throws Exception {
		
		failGetReferencedObjects(user, objs, e, false, nulls);
	}
	
	protected void failGetReferencedObjects(
			final WorkspaceUser user,
			final List<ObjectIDWithRefPath> objs,
			final Exception e,
			final boolean onlyTestReturningData)
			throws Exception {
		Set<Integer> nulls = new HashSet<Integer>();
		int count = 0;
		for (@SuppressWarnings("unused") ObjectIDWithRefPath foo: objs) {
			nulls.add(count);
			count++;
		}
		failGetReferencedObjects(user, objs, e, onlyTestReturningData, nulls);
	}
	
	protected void failGetReferencedObjects(
			final WorkspaceUser user,
			final List<ObjectIDWithRefPath> objs,
			final Exception e,
			final boolean onlyTestReturningData,
			final Set<Integer> nulls)
			throws Exception {
		failGetReferencedObjects(ws, user, objs, e, onlyTestReturningData, nulls);
	}
	
	@SuppressWarnings("unchecked")
	public static void failGetReferencedObjects(
			final Workspace ws,
			final WorkspaceUser user,
			final List<ObjectIDWithRefPath> objs,
			final Exception e,
			final boolean onlyTestReturningData,
			final Set<Integer> nulls)
			throws Exception {
		try {
			ws.getObjects(user, (List<ObjectIdentifier>)(List<?>) objs);
			fail("called get objects with bad args");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
		if (onlyTestReturningData) {
			return;
		}
		try {
			ws.getObjectInformation(user,
					(List<ObjectIdentifier>)(List<?>) objs, true, false);
			fail("called get object info with bad args");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
		if (objs.isEmpty() || nulls == null) {
			return;
		}
		//test that getting objinfo with bad args returns null when requested
		List<ObjectInformation> infonulls = ws.getObjectInformation(user,
				(List<ObjectIdentifier>)(List<?>) objs, true, true);
		List<WorkspaceObjectData> datanulls = ws.getObjects(user,
				(List<ObjectIdentifier>)(List<?>) objs, true, true, false);
		for (int i = 0; i < infonulls.size(); i++) {
			if (nulls.contains(i)) {
				assertNull("objinfo is not null", infonulls.get(i));
				assertNull("objdata is not null", datanulls.get(i));
			} else {
				assertNotNull("objectinfo is null", infonulls.get(i));
				assertNotNull("objectdata is null", datanulls.get(i));
			}
		}
	}
	
	protected void failGetSubset(
			final WorkspaceUser user,
			final List<ObjIDWithRefPathAndSubset> objs,
			final Exception e)
			throws Exception {
		failGetSubset(ws, user, objs, e);
	}
	
	@SuppressWarnings("unchecked")
	public static void failGetSubset(
			final Workspace ws,
			final WorkspaceUser user,
			final List<ObjIDWithRefPathAndSubset> objs,
			final Exception e)
			throws Exception {
		try {
			ws.getObjects(user, (List<ObjectIdentifier>)(List<?>) objs);
			fail("got subobjs obj when should fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected List<Date> checkProvenanceCorrect(
			final WorkspaceUser foo,
			final Provenance prov,
			final ObjectIdentifier obj,
			final Map<String, String> refmap)
					throws Exception {
		final List<WorkspaceObjectData> objects =
				ws.getObjects(foo, Arrays.asList(obj));
		destroyGetObjectsResources(objects); // don't need the data
		Provenance pgot = objects.get(0).getProvenance();
		checkProvenanceCorrect(prov, pgot, refmap,
				obj.getWorkspaceIdentifier().getId());
		Provenance pgot2 = ws.getObjects(foo, Arrays.asList(obj), true)
				.get(0).getProvenance();
		checkProvenanceCorrect(prov, pgot2,refmap,
				obj.getWorkspaceIdentifier().getId());
		return Arrays.asList(pgot.getDate(), pgot2.getDate());
	}
	
	//if refmap != null expected is a Provenance object. Otherwise it's a subclass
	// with an implemented getResolvedObjects method.
	protected void checkProvenanceCorrect(Provenance expected, Provenance got,
			Map<String, String> refmap, long wsid) {
		assertThat("user equal", got.getUser(), is(expected.getUser()));
		assertThat("same number actions", got.getActions().size(),
				is(expected.getActions().size()));
		assertThat("wsid correct", got.getWorkspaceID(), is(wsid));
		if (refmap == null) {
			assertThat("dates are the same", got.getDate(), is(expected.getDate()));
		} else {
			assertDateisRecent(got.getDate());
		}
		
		Iterator<ProvenanceAction> gotAct = got.getActions().iterator();
		Iterator<ProvenanceAction> expAct = expected.getActions().iterator();
		while (gotAct.hasNext()) {
			ProvenanceAction gotpa = gotAct.next();
			ProvenanceAction exppa = expAct.next();
			assertThat("caller equal", gotpa.getCaller(), is(exppa.getCaller()));
			assertThat("cmd line equal", gotpa.getCommandLine(), is(exppa.getCommandLine()));
			assertThat("desc equal", gotpa.getDescription(), is(exppa.getDescription()));
			assertThat("inc args equal", gotpa.getIncomingArgs(), is(exppa.getIncomingArgs()));
			assertThat("method equal", gotpa.getMethod(), is(exppa.getMethod()));
			assertThat("meth params equal", gotpa.getMethodParameters(), is(exppa.getMethodParameters()));
			assertThat("out args equal", gotpa.getOutgoingArgs(), is(exppa.getOutgoingArgs()));
			assertThat("script equal", gotpa.getScript(), is(exppa.getScript()));
			assertThat("script ver equal", gotpa.getScriptVersion(), is(exppa.getScriptVersion()));
			assertThat("service equal", gotpa.getServiceName(), is(exppa.getServiceName()));
			assertThat("serv ver equal", gotpa.getServiceVersion(), is(exppa.getServiceVersion()));
			assertThat("time equal", gotpa.getTime(), is(exppa.getTime()));
			assertThat("custom fields equal", gotpa.getCustom(), is(exppa.getCustom()));
			checkProvenanceExternalData(gotpa.getExternalData(), exppa.getExternalData());
			checkProvenanceSubActions(gotpa.getSubActions(), exppa.getSubActions());
			assertThat("refs equal", gotpa.getWorkspaceObjects(), is(exppa.getWorkspaceObjects()));
			assertThat("correct number resolved refs", gotpa.getResolvedObjects().size(),
					is(gotpa.getWorkspaceObjects().size()));
			if (refmap != null) {
				Iterator<String> gotrefs = gotpa.getWorkspaceObjects().iterator();
				Iterator<String> gotresolvedrefs = gotpa.getResolvedObjects().iterator();
				while (gotrefs.hasNext()) {
					assertThat("ref resolved correctly", gotresolvedrefs.next(),
							is(refmap.get(gotrefs.next())));
				}
			} else {
				assertThat("resolved refs equal", gotpa.getResolvedObjects(),
						is(exppa.getResolvedObjects()));
			}
		}
	}

	private void checkProvenanceSubActions(
			List<SubAction> got, List<SubAction> exp) {
		assertThat("prov subactions same size", got.size(), is(exp.size()));
		Iterator<SubAction> giter = got.iterator();
		Iterator<SubAction> eiter = exp.iterator();
		while (giter.hasNext()) {
			SubAction g = giter.next();
			SubAction e = eiter.next();
			assertThat("same code url", g.getCodeUrl(), is (e.getCodeUrl()));
			assertThat("same commit", g.getCommit(), is (e.getCommit()));
			assertThat("same endpoint", g.getEndpointUrl(), is (e.getEndpointUrl()));
			assertThat("same name", g.getName(), is (e.getName()));
			assertThat("same ver", g.getVer(), is (e.getVer()));
		}
	}

	private void checkProvenanceExternalData(List<ExternalData> got,
			List<ExternalData> exp) {
		assertThat("prov external data same size", got.size(), is(exp.size()));
		Iterator<ExternalData> giter = got.iterator();
		Iterator<ExternalData> eiter = exp.iterator();
		while (giter.hasNext()) {
			ExternalData g = giter.next();
			ExternalData e = eiter.next();
			assertThat("same data id", g.getDataId(), is (e.getDataId()));
			assertThat("same data url", g.getDataUrl(), is (e.getDataUrl()));
			assertThat("same description", g.getDescription(), is (e.getDescription()));
			assertThat("same resource name", g.getResourceName(), is (e.getResourceName()));
			assertThat("same resource rel date", g.getResourceReleaseDate(), is (e.getResourceReleaseDate()));
			assertThat("same resource url", g.getResourceUrl(), is (e.getResourceUrl()));
			assertThat("same resource ver", g.getResourceVersion(), is (e.getResourceVersion()));
		}
	}

	//TODO TEST this looks like it's redundant to failGetObjects
	protected void getNonExistantObject(WorkspaceUser foo, ObjectIdentifier oi,
			String exception) throws Exception {
		try {
			ws.getObjectInformation(foo, Arrays.asList(oi), false, false);
			fail("got non-existant object");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getLocalizedMessage(), 
					is(exception));
		}
	}
	
	private static final ResolvedWorkspaceID RWSID =
			new ResolvedWorkspaceID(1, "foo", false, false);
	
	protected void testObjectIdentifier(String goodId) {
		new ObjectIdentifier(new WorkspaceIdentifier("foo"), goodId);
		new ObjectIDResolvedWS(RWSID, goodId);
//		new ObjectIDResolvedWSNoVer(fakews, goodId);
		new ObjectIDNoWSNoVer(goodId);
	}
	
	protected void testObjectIdentifier(String goodId, int version) {
		new ObjectIdentifier(new WorkspaceIdentifier("foo"), goodId, version);
		new ObjectIDResolvedWS(RWSID, goodId, version);
//		new ObjectIDResolvedWSNoVer(fakews, goodId);
		new ObjectIDNoWSNoVer(goodId);
	}
	
	protected void testObjectIdentifier(int goodId) {
		new ObjectIdentifier(new WorkspaceIdentifier("foo"), goodId);
		new ObjectIDResolvedWS(RWSID, goodId);
//		new ObjectIDResolvedWSNoVer(fakews, goodId);
		new ObjectIDNoWSNoVer(goodId);
	}
	
	protected void testObjectIdentifier(int goodId, int version) {
		new ObjectIdentifier(new WorkspaceIdentifier("foo"), goodId, version);
		new ObjectIDResolvedWS(RWSID, goodId, version);
//		new ObjectIDResolvedWSNoVer(fakews, goodId);
		new ObjectIDNoWSNoVer(goodId);
	}
	
	protected void testObjectIdentifier(WorkspaceIdentifier badWS, String badId,
			String exception) {
		try {
			new ObjectIdentifier(badWS, badId);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
		ResolvedWorkspaceID fakews = null;
		if (badWS != null) {
			fakews = RWSID;
		} else {
			exception = "r" + exception;
		}
		try {
			new ObjectIDResolvedWS(fakews, badId);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
		if (badWS != null) {
			try {
				new ObjectIDNoWSNoVer(badId);
				fail("Initialized invalid object id");
			} catch (IllegalArgumentException e) {
				assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
			}
		}
	}
	
	protected void testObjectIdentifier(WorkspaceIdentifier badWS, String badId,
			int version, String exception) {
		try {
			new ObjectIdentifier(new WorkspaceIdentifier("foo"), badId, version);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
		ResolvedWorkspaceID fakews = null;
		if (badWS != null) {
			fakews = RWSID;
		} else {
			exception = "r" + exception;
		}
		try {
			new ObjectIDResolvedWS(fakews, badId, version);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}
	
	protected void testObjectIdentifier(WorkspaceIdentifier badWS, int badId,
			String exception) {
		try {
			new ObjectIdentifier(badWS, badId);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
		ResolvedWorkspaceID fakews = null;
		if (badWS != null) {
			fakews = RWSID;
		} else {
			exception = "r" + exception;
		}
		try {
			new ObjectIDResolvedWS(fakews, badId);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
//		try {
//			new ObjectIDResolvedWSNoVer(fakews, badId);
//			fail("Initialized invalid object id");
//		} catch (IllegalArgumentException e) {
//			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
//		}
		if (badWS != null) {
			try {
				new ObjectIDNoWSNoVer(badId);
				fail("Initialized invalid object id");
			} catch (IllegalArgumentException e) {
				assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
			}
		}
	}
	
	protected void testObjectIdentifier(WorkspaceIdentifier badWS,
			int badId, int version, String exception) {
		try {
			new ObjectIdentifier(badWS, badId, version);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
		ResolvedWorkspaceID fakews = null;
		if (badWS != null) {
			fakews = RWSID;
		} else {
			exception = "r" + exception;
		}
		try {
			new ObjectIDResolvedWS(fakews, badId, version);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}
	
	protected void testCreate(WorkspaceIdentifier goodWs, String name,
			Long id) {
		ObjectIdentifier.create(goodWs, name, id);
		ObjectIDNoWSNoVer.create(name, id);
		
	}
	
	protected void testCreateVer(WorkspaceIdentifier goodWs, String name, Long id,
			Integer ver) {
		ObjectIdentifier.create(goodWs, name, id, ver);
	}
	
	protected void testCreate(WorkspaceIdentifier badWS, String name,
			Long id, String exception) {
		try {
			ObjectIdentifier.create(badWS, name, id);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
		if (badWS != null) {
			try {
				ObjectIDNoWSNoVer.create(name, id);
				fail("Initialized invalid object id");
			} catch (IllegalArgumentException e) {
				assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
			}
		}
	}
	
	protected void testCreateVer(WorkspaceIdentifier badWS, String name,
			Long id, Integer ver, String exception) {
		try {
			ObjectIdentifier.create(badWS, name, id, ver);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}
	
	protected void testRef(String ref) {
		ObjectIdentifier.parseObjectReference(ref);
	}
	
	protected void testRef(String ref, String exception) {
		try {
			ObjectIdentifier.parseObjectReference(ref);
			fail("Initialized invalid object id");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}

	protected void checkNonDeletedObjs(WorkspaceUser foo,
			Map<ObjectIdentifier, Object> idToData) throws Exception {
		List<ObjectIdentifier> objs = new ArrayList<ObjectIdentifier>(idToData.keySet());
		List<WorkspaceObjectData> d = ws.getObjects(foo, objs);
		try {
			for (int i = 0; i < d.size(); i++) {
				assertThat("can get correct data from undeleted objects", getData(d.get(i)),
						is((Object) idToData.get(objs.get(i))));
			}
		} finally {
			destroyGetObjectsResources(d);
		}
	}

	//TODO TEST this looks like it's redundant to failGetObjects
	protected void failToGetDeletedObjects(WorkspaceUser user,
			List<ObjectIdentifier> objs, String exception) throws Exception {
		failGetObjects(user, objs, new NoSuchObjectException(exception, null));
		try {
			ws.getObjectInformation(user, objs, true, false);
			fail("got deleted object's history");
		} catch (NoSuchObjectException e) {
			assertThat("correct exception", e.getLocalizedMessage(), is(exception));
		}
	}

	protected void failCopy(
			final WorkspaceUser user,
			final ObjectIdentifier from,
			final ObjectIdentifier to,
			final Exception e) {
		failCopy(ws, user, from, to, e);
	}
		
	public static void failCopy(
			final Workspace ws,
			final WorkspaceUser user,
			final ObjectIdentifier from,
			final ObjectIdentifier to,
			final Exception e) {
		try {
			ws.copyObject(user, from, to);
			fail("copied object sucessfully but expected fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected void failRevert(
			final WorkspaceUser user,
			final ObjectIdentifier from,
			final Exception e) {
		failRevert(ws, user, from, e);
	}
	
	public static void failRevert(
			final Workspace ws,
			final WorkspaceUser user,
			final ObjectIdentifier from,
			final Exception e) {
		try {
			ws.revertObject(user, from);
			fail("reverted object sucessfully but expected fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected Map<String, String> makeSimpleMeta(String key, String value) {
		Map<String, String> map = new HashMap<String, String>();
		map.put(key, value);
		return map;
	}
	
	protected Map<String, Object> makeRefData(String... refs) {
		Map<String, Object> data = new HashMap<>();
		data.put("refs", Arrays.asList(refs));
		return data;
	}
	
	protected Map<String, String> makeMeta(final int id) {
		return makeSimpleMeta("m", "" + id);
	}

	protected void compareObjectAndInfo(ObjectInformation original,
			ObjectInformation copied, WorkspaceUser user, long wsid, String wsname, 
			long objectid, String objname, int version) throws Exception {
		compareObjectInfo(original, copied, user, wsid, wsname, objectid,
				objname, version);
		
		WorkspaceObjectData orig = null;
		WorkspaceObjectData copy = null;
		
		try {
			Reference expectedCopyRef = new Reference(original.getWorkspaceId(),
					original.getObjectId(), original.getVersion());
			
			//getObjects
			orig = ws.getObjects(original.getSavedBy(), Arrays.asList(
					new ObjectIdentifier(new WorkspaceIdentifier(original.getWorkspaceId()),
							original.getObjectId(), original.getVersion()))).get(0);
			copy = ws.getObjects(copied.getSavedBy(), Arrays.asList(
					new ObjectIdentifier(new WorkspaceIdentifier(copied.getWorkspaceId()),
							copied.getObjectId(), copied.getVersion()))).get(0);
			compareObjectInfo(orig.getObjectInfo(), copy.getObjectInfo(), user, wsid, wsname, objectid,
					objname, version);
			assertThat("returned data same", getData(copy), is(getData(orig)));
			assertThat("returned refs same", copy.getReferences(), is(orig.getReferences()));
			assertThat("copy ref correct", copy.getCopyReference(), is(expectedCopyRef));
			checkProvenanceCorrect(orig.getProvenance(), copy.getProvenance(),
					null, original.getWorkspaceId());
			
			//getObjectProvenance
			WorkspaceObjectData originfo = ws.getObjects(original.getSavedBy(),
					Arrays.asList(
							new ObjectIdentifier(new WorkspaceIdentifier(original.getWorkspaceId()),
							original.getObjectId(), original.getVersion())), true).get(0);
			WorkspaceObjectData copyinfo = ws.getObjects(copied.getSavedBy(),
					Arrays.asList(
					new ObjectIdentifier(new WorkspaceIdentifier(copied.getWorkspaceId()),
							copied.getObjectId(), copied.getVersion())), true).get(0);
			compareObjectInfo(originfo.getObjectInfo(), copyinfo.getObjectInfo(), user, wsid, wsname, objectid,
					objname, version);
			assertThat("returned refs same", copyinfo.getReferences(), is(originfo.getReferences()));
			assertThat("copy ref correct", copyinfo.getCopyReference(), is(expectedCopyRef));
			checkProvenanceCorrect(originfo.getProvenance(), copyinfo.getProvenance(),
					null, original.getWorkspaceId());
		} finally {
			if (orig != null) {
				destroyGetObjectsResources(Arrays.asList(orig));
			}
			if (copy != null) {
				destroyGetObjectsResources(Arrays.asList(copy));
			}
		}
	}
	
	protected void compareObjectAndInfo(WorkspaceObjectData got,
			ObjectInformation info, Provenance prov, Map<String, ? extends Object> data,
			List<String> refs, Map<String, String> refmap)
			throws Exception {
		assertThat("object info same", got.getObjectInfo(), is(info));
		if (data == null) {
			assertNull("returned data when requested provenance only",
					got.getSerializedData());
		} else {
			assertThat("returned data same", getData(got), is((Object)data));
		}
		assertThat("returned refs same", new HashSet<String>(got.getReferences()),
				is(new HashSet<String>(refs)));
		checkProvenanceCorrect(prov, got.getProvenance(), refmap, info.getWorkspaceId());
	}

	protected void compareObjectInfo(ObjectInformation original,
			ObjectInformation copied, WorkspaceUser user, long wsid,
			String wsname, long objectid, String objname, int version) {
		assertThat("checksum same", copied.getCheckSum(), is(original.getCheckSum()));
		assertThat("correct object id", copied.getObjectId(), is(objectid));
		assertThat("correct object name", copied.getObjectName(), is(objname));
		assertThat("correct user", copied.getSavedBy(), is(user));
		assertTrue("copy date after orig", copied.getSavedDate().after(original.getSavedDate()));
		assertDateisRecent(original.getSavedDate());
		assertDateisRecent(copied.getSavedDate());
		assertThat("size correct", copied.getSize(), is(original.getSize()));
		assertThat("type correct", copied.getTypeString(), is(original.getTypeString()));
		assertThat("meta correct", copied.getUserMetaData(), is(original.getUserMetaData()));
		assertThat("version correct", copied.getVersion(), is(version));
		assertThat("wsid correct", copied.getWorkspaceId(), is(wsid));
		assertThat("ws name correct", copied.getWorkspaceName(), is(wsname));
	}

	protected ObjectInformation saveObject(WorkspaceUser user, WorkspaceIdentifier wsi,
			Map<String, String> meta, Map<String, ? extends Object> data, TypeDefId type,
			String name, Provenance prov)
			throws Exception {
		return saveObject(user, wsi, meta, data, type, name, prov, false);
	}
	
	protected ObjectInformation saveObject(WorkspaceUser user, WorkspaceIdentifier wsi,
			Map<String, String> meta, Map<String, ? extends Object> data,
			TypeDefId type, String name, Provenance prov, boolean hide)
			throws Exception {
		final IdReferenceHandlerSetFactory fac = new IdReferenceHandlerSetFactory(100000);
		return ws.saveObjects(user, wsi, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer(name), data,
						type, new WorkspaceUserMetadata(meta), prov, hide)), fac)
				.get(0);
	}

	protected void failClone(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final String name,
			final Map<String, String> meta,
			final Exception e) {
		failClone(user, wsi, name, meta, null, e);
	}
	
	protected void failClone(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final String name,
			final Map<String, String> meta,
			final Set<ObjectIDNoWSNoVer> exclude,
			final Exception e) {
		failClone(ws, user, wsi, name, meta, exclude, e);
	}
	
	public static void failClone(
			final Workspace ws,
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final String name,
			final Map<String, String> meta,
			final Set<ObjectIDNoWSNoVer> exclude,
			final Exception e) {
		try {
			ws.cloneWorkspace(user, wsi, name, false, null,
					new WorkspaceUserMetadata(meta), exclude);
			fail("expected clone to fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}

	protected void failObjRename(
			final WorkspaceUser user,
			final ObjectIdentifier oi,
			final String newname,
			final Exception e) {
		failObjRename(ws, user, oi, newname, e);
	}
	
	public static void failObjRename(
			final Workspace ws,
			final WorkspaceUser user,
			final ObjectIdentifier oi,
			final String newname,
			final Exception e) {
		try {
			ws.renameObject(user, oi, newname);
			fail("expected rename to fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}

	protected void failWSRename(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final String newname,
			final Exception e) {
		failWSRename(ws, user, wsi, newname, e);
	}
	
	public static void failWSRename(
			final Workspace ws,
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final String newname,
			final Exception e) {
		try {
			ws.renameWorkspace(user, wsi, newname);
			fail("expected rename to fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected void failDeleteWorkspace(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final boolean delete,
			final Exception e) {
		try {
			ws.setWorkspaceDeleted(user, wsi, delete);
			fail("Non owner deleted workspace");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected void failDeleteWorkspaceAsAdmin(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final boolean delete,
			final boolean asAdmin,
			final Exception e) {
		try {
			ws.setWorkspaceDeleted(user, wsi, delete, asAdmin);
			fail("Non owner deleted workspace");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected void failSetWorkspaceOwner(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final WorkspaceUser newuser,
			final Optional<String> name,
			final boolean asAdmin,
			final Exception expected)
			throws Exception {
		failSetWorkspaceOwner(ws, user, wsi, newuser, name, asAdmin, expected);
	}
	
	public static void failSetWorkspaceOwner(
			final Workspace ws,
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final WorkspaceUser newuser,
			final Optional<String> name,
			final boolean asAdmin,
			final Exception expected)
			throws Exception {
		try {
			ws.setWorkspaceOwner(user, wsi, newuser, name, asAdmin);
			fail("expected set owner to fail");
		} catch (Exception got) {
			assertExceptionCorrect(got, expected);
		}
	}
	
	protected void failGetWorkspaceDesc(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final Exception e)
			throws Exception {
		failGetWorkspaceDesc(ws, user, wsi, e);
	}
	
	public static void failGetWorkspaceDesc(
			final Workspace ws,
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final Exception e)
			throws Exception {
		try {
			ws.getWorkspaceDescription(user, wsi);
			fail("got ws desc when should fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected void failGetWorkspaceInfoAsAdmin(
			final WorkspaceIdentifier wsi,
			final Exception e) {
		try {
			ws.getWorkspaceInformationAsAdmin(wsi);
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, e);
		}
	}
	
	protected void failSetGlobalPerm(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final Permission perm,
			final Exception e)
			throws Exception {
		failSetGlobalPerm(ws, user, wsi, perm, e);
	}
	
	public static void failSetGlobalPerm(
			final Workspace ws,
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final Permission perm,
			final Exception e)
			throws Exception {
		try {
			ws.setGlobalPermission(user, wsi, perm);
			fail("set global perms when should fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected void failSetHide(
			final WorkspaceUser user,
			final ObjectIdentifier oi,
			final boolean hide,
			final Exception e)
			throws Exception {
		failSetHide(ws, user, oi, hide, e);
	}
	
	public static void failSetHide(
			final Workspace ws,
			final WorkspaceUser user,
			final ObjectIdentifier oi,
			final boolean hide,
			final Exception e)
			throws Exception {
		try {
			ws.setObjectsHidden(user, Arrays.asList(oi), hide);
			fail("un/hid obj when should fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	//checks exact dates
	protected void checkWSInfoList(List<WorkspaceInformation> ws,
			List<WorkspaceInformation> expected) {
		Map<WorkspaceInformation, Boolean> m =
				new HashMap<WorkspaceInformation, Boolean>();
		for (WorkspaceInformation wi: expected) {
			m.put(wi, false);
		}
		checkWSInfoList(ws, m);
	}
	
	protected void checkWSInfoList(List<WorkspaceInformation> ws,
			Map<WorkspaceInformation, Boolean> expected) {
		Map<Long, WorkspaceInformation> idToInf = new HashMap<Long, WorkspaceInformation>();
		for (WorkspaceInformation wi: expected.keySet()) {
			idToInf.put(wi.getId(), wi);
		}
		Set<Long> got = new HashSet<Long>();
		for (WorkspaceInformation wi: ws) {
			if (got.contains(wi.getId())) {
				fail("Same workspace listed twice");
			}
			got.add(wi.getId());
			if (!idToInf.containsKey(wi.getId())) {
				System.out.println(expected);
				System.out.println(ws);
				System.out.println(got);
				fail("got id " + wi.getId() + ", but not in expected: " + wi);
			}
			if (!expected.get(idToInf.get(wi.getId()))) {
				assertThat("workspace correct", wi, is(idToInf.get(wi.getId())));
			} else {
				compareWorkspaceInfoLessTimeStamp(wi, idToInf.get(wi.getId()));
			}
		}
		assertThat("listed correct workspaces", got, is(idToInf.keySet()));
	}

	protected void compareWorkspaceInfoLessTimeStamp(WorkspaceInformation got,
			WorkspaceInformation expected) {
		assertThat("ws id correct", got.getId(), is(expected.getId()));
		assertDateisRecent(got.getModDate());
		assertThat("ws owner correct", got.getOwner(), is(expected.getOwner()));
		assertThat("ws name correct", got.getName(), is(expected.getName()));
		assertThat("ws max obj correct", got.getMaximumObjectID(), is(expected.getMaximumObjectID()));
		assertThat("ws permissions correct", got.getUserPermission(), is(expected.getUserPermission()));
		assertThat("ws global read correct", got.isGloballyReadable(), is(expected.isGloballyReadable()));
		assertThat("ws lockstate correct", got.getLockState(), is(expected.getLockState()));
		
	}
	
	/* depends on inherent mongo ordering */
	protected void checkObjectLimit(WorkspaceUser user, WorkspaceIdentifier wsi,
			int limit, int minid, int maxid)
			throws Exception {
		checkObjectLimit(user, wsi, limit, minid, maxid,
				new HashSet<Long>());
	}
	
	/* depends on inherent mongo ordering */
	protected void checkObjectLimit(WorkspaceUser user, WorkspaceIdentifier wsi,
			int limit, int minid, int maxid, Set<Long> exlude) 
			throws Exception {
		List<ObjectInformation> res = ws.listObjects(
				new ListObjectsParameters(user, Arrays.asList(wsi))
				.withLimit(limit));
		assertThat("correct number of objects returned", res.size(),
				is(maxid - minid + 1 - exlude.size()));
		for (ObjectInformation oi: res) {
			if (oi.getObjectId() < minid || oi.getObjectId() > maxid) {
				fail(String.format("ObjectID out of test bounds: %s min %s max %s",
						oi.getObjectId(), minid, maxid));
			}
			if (exlude.contains(oi.getObjectId())) {
				fail("Got object ID that should have been excluded: " +
						oi.getObjectId());
			}
		}
	}
	
	protected void failGetObjectHistory(
			final WorkspaceUser user,
			final ObjectIdentifier oi,
			final Exception e) {
		failGetObjectHistory(ws, user, oi, e);
	}
	
	public static void failGetObjectHistory(
			final Workspace ws,
			final WorkspaceUser user,
			final ObjectIdentifier oi,
			final Exception e) {
		try {
			ws.getObjectHistory(user, oi);
			fail("listed obj hist when should fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}

	protected void failListObjects(
			final WorkspaceUser user,
			final List<WorkspaceIdentifier> wsis,
			final Map<String, String> meta,
			final Exception e) {
		failListObjects(ws, user, wsis, meta, e);
	}
	
	public static void failListObjects(
			final Workspace ws,
			final WorkspaceUser user,
			final List<WorkspaceIdentifier> wsis,
			final Map<String, String> meta,
			final Exception e) {
		try {
			ws.listObjects(new ListObjectsParameters(user, wsis)
					.withMetadata(new WorkspaceUserMetadata(meta)));
			fail("listed obj when should fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected void failGetNamesByPrefix(
			final WorkspaceUser user,
			final List<WorkspaceIdentifier> wsis,
			final String prefix,
			final boolean includeHidden,
			final int limit,
			final Exception e)
			throws Exception {
		failGetNamesByPrefix(ws, user, wsis, prefix, includeHidden, limit, e);
	}
	
	public static void failGetNamesByPrefix(
			final Workspace ws,
			final WorkspaceUser user,
			final List<WorkspaceIdentifier> wsis,
			final String prefix,
			final boolean includeHidden,
			final int limit,
			final Exception e)
			throws Exception {
		try {
			ws.getNamesByPrefix(user, wsis, prefix, includeHidden, limit);
			fail("got names by prefix with bad args");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected void compareObjectInfo(List<ObjectInformation> got,
			List<ObjectInformation> expected) {
		HashSet<ObjectInformation> g = new HashSet<ObjectInformation>();
		for (ObjectInformation oi: got) {
			if (g.contains(oi)) {
				fail("Got same object info twice: " + oi);
			}
			g.add(oi);
		}
		assertThat("listed correct objects", g, is(new HashSet<ObjectInformation>(expected)));
	}
	
	protected void checkReferencedObject(
			final WorkspaceUser user,
			final ObjectIDWithRefPath chain,
			final ObjectInformation oi,
			final Provenance p,
			final Map<String, ? extends Object> data,
			final List<String> refs,
			final Map<String, String> refmap)
			throws Exception {
		ObjectInformation info = ws.getObjectInformation(user,
				Arrays.asList((ObjectIdentifier) chain), true, false).get(0);
		WorkspaceObjectData wod = ws.getObjects(user,
				Arrays.asList((ObjectIdentifier)chain)).get(0);
		try {
			compareObjectAndInfo(wod, oi, p, data, refs, refmap);
		} finally {
			destroyGetObjectsResources(Arrays.asList(wod));
		}
		assertThat("object info same", info, is(oi));
	}
	
	protected void failCreateObjectChain(ObjectIdentifier oi, List<ObjectIdentifier> chain,
			Exception e) {
		try {
			new ObjectIDWithRefPath(oi, chain);
			fail("bad args to object chain");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	protected Set<ObjectInformation> oiset(ObjectInformation... ois) {
		return new HashSet<ObjectInformation>(Arrays.asList(ois));
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> createData(String json)
			throws JsonParseException, JsonMappingException, IOException {
		return new ObjectMapper().readValue(json, Map.class);
	}

	protected void checkExternalIds(WorkspaceUser user, ObjectIdentifier obj,
			Map<String, List<String>> expected)
			throws Exception {
		List<ObjectIdentifier> o = Arrays.asList(obj);
		WorkspaceObjectData wod = ws.getObjects(user, o).get(0);
		wod.destroy(); // don't need the actual data
		WorkspaceObjectData woi = ws.getObjects(user, o, true).get(0);
		
		assertThat("get objs correct ext ids", new HashMap<>(wod.getExtractedIds()), is(expected));
		assertThat("get prov correct ext ids", new HashMap<>(woi.getExtractedIds()), is(expected));
	}
}
