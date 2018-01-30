package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.assertExceptionCorrect;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.jongo.Jongo;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.service.UObject;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.common.utils.sortjson.UTF8JsonSorterFactory;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.LocalTypeProvider;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.RemappedId;
import us.kbase.typedobj.test.DummyValidatedTypedObject;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ListObjectsParameters;
import us.kbase.workspace.database.ObjIDWithRefPathAndSubset;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIDWithRefPath;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedSaveObject;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.exceptions.WorkspaceDBInitializationException;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.IDName;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.ObjectSavePackage;
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspace.test.workspace.WorkspaceTester;

import com.google.common.base.Optional;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

public class MongoInternalsTest {
	
	private static Jongo jdb;
	private static MongoWorkspaceDB mwdb;
	private static Workspace ws;
	private static Types types;
	private static MongoController mongo;
	private static MongoClient mongoClient;
	
	private static final IdReferenceHandlerSetFactory fac =
			new IdReferenceHandlerSetFactory(100);
	
	public static final TypeDefId SAFE_TYPE =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);

	@BeforeClass
	public static void setUpClass() throws Exception {
		mongo = new MongoController(TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using mongo temp dir " +
				mongo.getTempDir());
		TestCommon.stfuLoggers();
		String mongohost = "localhost:" + mongo.getServerPort();
		mongoClient = new MongoClient(mongohost);
		final DB db = mongoClient.getDB("MongoInternalsTest");
		String typedb = "MongoInternalsTest_types";
		WorkspaceTestCommon.destroyWSandTypeDBs(db, typedb);
		jdb = new Jongo(db);
		
		TempFilesManager tfm = new TempFilesManager(
				new File(TestCommon.getTempDir()));
		final TypeDefinitionDB typeDefDB = new TypeDefinitionDB(
				new MongoTypeStorage(GetMongoDB.getDB(mongohost, typedb)));
		TypedObjectValidator val = new TypedObjectValidator(
				new LocalTypeProvider(typeDefDB));
		mwdb = new MongoWorkspaceDB(db, new GridFSBlobStore(db), tfm);
		ws = new Workspace(mwdb, new ResourceUsageConfigurationBuilder().build(), val);

		//make a general spec that tests that don't worry about typechecking can use
		WorkspaceUser foo = new WorkspaceUser("foo");
		//simple spec
		types = new Types(typeDefDB);
		types.requestModuleRegistration(foo, "SomeModule");
		types.resolveModuleRegistration("SomeModule", true);
		types.compileNewTypeSpec(foo, 
				"module SomeModule {/* @optional thing */ typedef structure {string thing;} AType;};",
				Arrays.asList("AType"), null, null, false, null);
		types.releaseTypes(foo, "SomeModule");
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (mongo != null) {
			mongo.destroy(TestCommon.getDeleteTempFiles());
		}
	}
	
	@Before
	public void clearDB() throws Exception {
		TestCommon.destroyDB(jdb.getDatabase());
	}
	
	private static ObjectIDNoWSNoVer getRandomName() {
		return new ObjectIDNoWSNoVer(UUID.randomUUID().toString().replace("-", ""));
	}
	
	@Test
	public void cloneCreateWorkspace() throws Exception {
		/* test that creating a workspace to be cloned into creates the 
		 * correct state.
		 */
		WorkspaceUser foo = new WorkspaceUser("foo");
		final Map<String, String> m = new HashMap<>();
		m.put("foo", "bar");
		createWSForClone(foo, "myname", false, "desc1",
				new WorkspaceUserMetadata(m));
		checkClonedWorkspace(1L, null, foo, "desc1", m, false, false);
		
		//check that creating a workspace with the same name as the cloning
		//workspace works
		ws.createWorkspace(foo, "myname", false, null,
				new WorkspaceUserMetadata());
	}
	
	@Test
	public void cloneCompleteClone() throws Exception {
		/* test that completing a clone creates the correct state */
		
		WorkspaceUser user = new WorkspaceUser("foo");
		final Map<String, String> meta = new HashMap<>();
		
		final Method update = mwdb.getClass()
				.getDeclaredMethod("updateClonedWorkspaceInformation",
						WorkspaceUser.class,
						boolean.class,	//global read
						long.class,		// ws id
						String.class);	// name
		update.setAccessible(true);

		// test w global read
		createWSForClone(user, "baz", false, null,
				new WorkspaceUserMetadata());
		update.invoke(mwdb, user, true, 1L, "baz");
		checkClonedWorkspace(1, "baz", user, null, meta, true, true);
		
		//test w/o global read
		meta.put("foo", "bar1");
		createWSForClone(user, "whee", false, "mydesc",
				new WorkspaceUserMetadata(meta));
		update.invoke(mwdb, user, false, 2L, "whee2");
		checkClonedWorkspace(2, "whee2", user, "mydesc", meta, false, true);
		
		//test fail on bad id
		failUpdateClonedWS(user, 3, "foo", new IllegalStateException(
				"A programming error occurred: there is no workspace with " +
				"ID 3"));
		
		//test fail on existing name
		failUpdateClonedWS(user, 2, "whee2", new PreExistingWorkspaceException(
				"Workspace name whee2 already in use"));
	}
	
	@Test
	public void cloneWithWorkspaceInCloningState() throws Exception {
		/* test that cloning a workspace while another workspace is in the cloning state
		 * (could be due to multiple clones occurring at the same time or a clone stuck in mid
		 * clone state due to a DB or service failure) works.
		 * Prior implementations used a unique non-sparse name index and omitted the workspace name
		 * when inserting the record, which caused null to be added to the index. This means
		 * only one workspace could be in the cloning state at once, which is exceptionally bad
		 * when a clone fails and a record is left in the db in the cloning state.
		 */
		WorkspaceUser user = new WorkspaceUser("foo");
		
		final WorkspaceUserMetadata meta = new WorkspaceUserMetadata();
		createWSForClone(user, "baz", false, null, meta);
		ws.createWorkspace(user, "whee", false, null, meta);
		ws.cloneWorkspace(user, new WorkspaceIdentifier("whee"), "foo", false, null, meta, null);
		checkClonedWorkspace(2, "whee", user, null, new HashMap<String, String>(), false, true);
	}
	
	@Test
	public void cloningWorkspaceInaccessible() throws Exception {
		final WorkspaceUser user1 = new WorkspaceUser("shoopty");
		final WorkspaceUser user2 = new WorkspaceUser("whoop");
		
		final Map<String, String> mt = new HashMap<>();
		final Provenance p = new Provenance(user1);
		
		// make a normal workspace & save objects
		ws.createWorkspace(user2, "bar", false, null,
				new WorkspaceUserMetadata());
		final WorkspaceIdentifier std = new WorkspaceIdentifier(1);
		ws.saveObjects(user2, std, Arrays.asList(
				new WorkspaceSaveObject(getRandomName(),
						new UObject(mt), SAFE_TYPE, null, p, false)),
				fac);
		final ObjectIdentifier stdobj = new ObjectIdentifier(std, 1);
		ws.setPermissions(user2, std, Arrays.asList(user1), Permission.WRITE);
		
		// make a workspace to be cloned, save objects, and put into cloning
		// state
		ws.createWorkspace(user1, "baz", false, null,
				new WorkspaceUserMetadata());
		final WorkspaceIdentifier cloning = new WorkspaceIdentifier(2);
		ws.saveObjects(user1, cloning, Arrays.asList(
				new WorkspaceSaveObject(getRandomName(),
						new UObject(mt), SAFE_TYPE, null, p, false)),
				fac);
		final ObjectIdentifier clnobj = new ObjectIdentifier(cloning, 1);
		
		final DBObject cloneunset = new BasicDBObject();
		cloneunset.put("name", "");
		cloneunset.put("moddate", "");
		final DBObject update = new BasicDBObject(
				"$set", new BasicDBObject("cloning", true));
		update.put("$unset", cloneunset);
		jdb.getDatabase().getCollection("workspaces").update(
				new BasicDBObject("ws", 2), update);
		
		final NoSuchWorkspaceException noWSExcp = new NoSuchWorkspaceException(
				"No workspace with id 2 exists", cloning);
		final InaccessibleObjectException noObjExcp =
				new InaccessibleObjectException("Object 1 cannot be " +
				"accessed: No workspace with id 2 exists", null);

		//test clone
		WorkspaceTester.failClone(ws, user1, cloning, "whee", null, null,
				noWSExcp);
		
		//test copy to & from
		WorkspaceTester.failCopy(ws, user1, stdobj,
				new ObjectIdentifier(cloning, "foo"),
				new InaccessibleObjectException("Object foo cannot be " +
						"accessed: No workspace with id 2 exists", null));
		WorkspaceTester.failCopy(ws, user1, clnobj,
				new ObjectIdentifier(std, "foo"), noObjExcp);
		
		//test get names by prefix
		WorkspaceTester.failGetNamesByPrefix(ws, user1, Arrays.asList(cloning),
				"a", false, 1000, noWSExcp);
		
		//test get object history
		WorkspaceTester.failGetObjectHistory(ws, user1, clnobj, noObjExcp);
		
		//test various get objects methods
		WorkspaceTester.failGetObjects(ws, user1, Arrays.asList(clnobj),
				noObjExcp, false);
		
		// test get perms
		WorkspaceTester.failGetPermissions(ws, user1, Arrays.asList(cloning),
				noWSExcp);
		
		// test get referenced objects
		final ObjectIDWithRefPath oc = new ObjectIDWithRefPath(
				clnobj, Arrays.asList(stdobj));
		WorkspaceTester.failGetReferencedObjects(ws, user1, Arrays.asList(oc),
				noObjExcp, false, new HashSet<>(Arrays.asList(0)));
		
		// test get subset
		final ObjIDWithRefPathAndSubset os = new ObjIDWithRefPathAndSubset(
				clnobj, null, new SubsetSelection(Arrays.asList("/foo")));
		WorkspaceTester.failGetSubset(ws, user1, Arrays.asList(os), noObjExcp);
		
		//test get ws desc
		WorkspaceTester.failGetWorkspaceDesc(ws, user1, cloning, noWSExcp);
		
		// test list objects - both direct fail and ignoring objects in
		// cloning workspaces
		WorkspaceTester.failListObjects(ws, user1, Arrays.asList(std, cloning),
				null, noWSExcp);
		
		final List<ObjectInformation> listobj = ws.listObjects(
				new ListObjectsParameters(user1, SAFE_TYPE));
		assertThat("listed object count incorrect", listobj.size(), is(1));
		assertThat("listed obj ws id incorrect",
				listobj.get(0).getWorkspaceId(), is(1L));
		
		// test obj rename
		WorkspaceTester.failObjRename(ws, user1, clnobj, "foo", noObjExcp);
		
		//test revert
		WorkspaceTester.failRevert(ws, user1, clnobj, noObjExcp);
		
		//test save
		WorkspaceTester.failSave(ws, user1, cloning, Arrays.asList(
				new WorkspaceSaveObject(getRandomName(),
						new UObject(mt), SAFE_TYPE, null, p, false)),
				fac, noWSExcp);
		
		// test set global perm
		WorkspaceTester.failSetGlobalPerm(ws, user1, cloning, Permission.READ,
				noWSExcp);
		
		// test hide
		WorkspaceTester.failSetHide(ws, user1, clnobj, true, noObjExcp);
		
		// test set perms
		WorkspaceTester.failSetPermissions(ws, user1, cloning,
				Arrays.asList(new WorkspaceUser("foo1")), Permission.READ,
				noWSExcp);
		
		// test set desc
		WorkspaceTester.failSetWSDesc(ws, user1, cloning, "foo", noWSExcp);
		
		//test ws meta
		WorkspaceTester.failWSMeta(ws, user1, cloning, "fo", "bar", noWSExcp);
		
		//test ws rename
		WorkspaceTester.failWSRename(ws, user1, cloning, "foo", noWSExcp);
		
		//test set ws owner
		WorkspaceTester.failSetWorkspaceOwner(ws, user1, cloning,
				new WorkspaceUser("barbaz"), Optional.of("barbaz"), false, noWSExcp);
		
		//test list workspaces
		List<WorkspaceInformation> wsl = ws.listWorkspaces(
				user1, null, null, null, null, null, false, true, false);
		assertThat("listed ws count incorrect", wsl.size(), is(1));
		assertThat("listed ws id incorrect",
				wsl.get(0).getId(), is(1L));
		
		// test delete object
		try {
			ws.setObjectsDeleted(user1, Arrays.asList(clnobj), true);
			fail("set deleted on ws in clone state");
		} catch (InaccessibleObjectException e) {
			assertThat("incorrect exception", e.getMessage(),
					is(noObjExcp.getMessage()));
		}
		
		// test get workspace owners
		assertThat("got owner of cloning workspace",
				ws.getAllWorkspaceOwners(),
				is((Set<WorkspaceUser>) new HashSet<>(Arrays.asList(user2))));
		
		// test get referencing objects
		try {
			ws.getReferencingObjects(user1, Arrays.asList(clnobj));
			fail("Able to get ref obj data from cloning workspace");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception message", ioe.getLocalizedMessage(),
					is(noObjExcp.getMessage()));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is(new ObjectIdentifier(cloning, 1)));
		}
		
		// test get workspace info
		try {
			ws.getWorkspaceInformation(user1, cloning);
			fail("Got wsinfo from cloning ws");
		} catch (NoSuchWorkspaceException e) {
			assertThat("exception message ok", e.getLocalizedMessage(),
					is(noWSExcp.getMessage()));
		}
		
		// test lock workspace
		try {
			ws.lockWorkspace(user1, cloning);
			fail("locked cloning workspace");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is(noWSExcp.getMessage()));
		}
		
		// test delete workspace
		try {
			ws.setWorkspaceDeleted(user1, cloning, true);
			fail("deleted cloning workspace");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is(noWSExcp.getMessage()));
		}
		
	}
	
	private void failUpdateClonedWS(
			final WorkspaceUser user,
			final long id,
			final String name,
			final Exception exp) throws Exception {
		final Method update = mwdb.getClass()
				.getDeclaredMethod("updateClonedWorkspaceInformation",
						WorkspaceUser.class,
						boolean.class,	//global read
						long.class,		// ws id
						String.class);	// name
		update.setAccessible(true);
		try {
			update.invoke(mwdb, user, false, id, name);
		} catch (Exception got) {
			// exceptions are wrapped in invocation exception
			TestCommon.assertExceptionCorrect((Exception) got.getCause(), exp);
		}
	}
	
	private void checkClonedWorkspace(
			final long id,
			final String name,
			final WorkspaceUser owner,
			final String description,
			final Map<String, String> meta,
			final boolean globalRead,
			final boolean complete) {
		DB db = jdb.getDatabase();
		DBObject ws = db.getCollection("workspaces").findOne(
				new BasicDBObject("ws", id));
		assertThat("name was set incorrectly", (String) ws.get("name"),
				is((String) name));
		assertThat("owner set incorrectly", (String) ws.get("owner"),
				is(owner.getUser()));
		final Date minus1m = new Date(new Date().getTime() - (60 * 1000));
		if (complete) {
			assertThat("date set incorrectly",
					minus1m.before((Date) ws.get("moddate")), is(true));
		} else {
			assertThat("date shouldn't be set", (Date) ws.get("moddate"),
					is((Date) null));
		}
		assertThat("id set incorrectly", (long) ws.get("ws"), is(id)); //duh
		assertThat("deleted set incorrectly", (boolean) ws.get("del"),
				is(false));
		assertThat("num objs set incorrectly", (long) ws.get("numObj"),
				is(0L));
		assertThat("desc set incorrectly", (String) ws.get("desc"),
				is(description));
		assertThat("locked set incorrectly", (boolean) ws.get("lock"),
				is(false));
		assertCloneWSMetadataCorrect(ws, meta);
		assertThat("cloning set incorrectly", (Boolean) ws.get("cloning"),
				is((Boolean) (complete ? null : true)));
		assertCloneWSACLsCorrect(id, owner, globalRead, complete);
	}

	private void assertCloneWSACLsCorrect(
			final long id,
			final WorkspaceUser owner,
			final boolean globalRead,
			final boolean complete) {
		final DB db = jdb.getDatabase();
		final Set<Map<String, Object>> acls = new HashSet<>();
		for (final DBObject acl: db.getCollection("workspaceACLs")
				.find(new BasicDBObject("id", id))) {
			/* fucking LazyBSONObjects, what the hell was mongo thinking */
			Map<String, Object> a = new HashMap<>();
			for (final String k: acl.keySet()) {
				if (!k.equals("_id")) { //mongo id
					a.put(k, acl.get(k));
				}
			}
			acls.add(a);
		}
		final Set<Map<String, Object>> expacl = new HashSet<>();
		if (complete) {
			final Map<String, Object> useracl = new HashMap<>();
			useracl.put("id", id);
			useracl.put("perm", 40);
			useracl.put("user", owner.getUser());
			expacl.add(useracl);
			if (globalRead) {
				final Map<String, Object> globalacl = new HashMap<>();
				globalacl.put("id", id);
				globalacl.put("perm", 10);
				globalacl.put("user", "*");
				expacl.add(globalacl);
			}
		}
		assertThat("acls incorrect", acls, is(expacl));
	}

	private void assertCloneWSMetadataCorrect(
			final DBObject ws,
			final Map<String, String> meta) {
		final Set<Map<String, String>> gotmeta = new HashSet<>();
		/* for some reason sometimes (but not always) get a LazyBsonList here
		 * which doesn't support listIterator which equals uses, but this seems
		 * to fix it. Doesn't support toMap() either.
		 */
		@SuppressWarnings("unchecked")
		final List<DBObject> shittymeta = (List<DBObject>) ws.get("meta");
		for (DBObject o: shittymeta) {
			final Map<String, String> shittymetainner =
					new HashMap<String, String>();
			for (String k: o.keySet()) {
				shittymetainner.put(k, (String) o.get(k));
			}
			gotmeta.add(shittymetainner);
		}
		final Set<Map<String, String>> expmeta = new HashSet<>();
		for (final Entry<String, String> e: meta.entrySet()) {
			final Map<String, String> inner = new HashMap<>();
			inner.put("k", e.getKey());
			inner.put("v", e.getValue());
			expmeta.add(inner);
		}
		assertThat("meta set incorrectly", gotmeta, is(expmeta));
	}

	private void createWSForClone(
			final WorkspaceUser foo,
			final String wsname,
			final boolean global,
			final String desc,
			final WorkspaceUserMetadata inmeta)
			throws NoSuchMethodException, IllegalAccessException,
			InvocationTargetException {
		Method createClonedWorkspace = mwdb.getClass()
				.getDeclaredMethod("createWorkspace",
						WorkspaceUser.class,
						String.class,
						boolean.class,
						String.class,
						WorkspaceUserMetadata.class,
						boolean.class);
		createClonedWorkspace.setAccessible(true);
		createClonedWorkspace.invoke(mwdb, foo, wsname, global, desc,
				inmeta, true);
	}
	
	@Test
	public void startUpAndCheckConfigDoc() throws Exception {
		final DB db = mongoClient.getDB("startUpAndCheckConfigDoc");
		TempFilesManager tfm = new TempFilesManager(
				new File(TestCommon.getTempDir()));
		new MongoWorkspaceDB(db, new GridFSBlobStore(db), tfm);
		
		DBCursor c = db.getCollection("config").find();
		assertThat("Only one config doc", c.size(), is(1));
		DBObject cd = c.next();
		assertThat("correct config key & value", (String)cd.get("config"),
				is("config"));
		assertThat("not in update", (Boolean)cd.get("inupdate"), is(false));
		assertThat("schema v1", (Integer)cd.get("schemaver"), is(1));
		
		//check startup works with the config object in place
		MongoWorkspaceDB m = new MongoWorkspaceDB(
				db,  new GridFSBlobStore(db), tfm);
		WorkspaceInformation ws = m.createWorkspace(
				new WorkspaceUser("foo"), "bar", false, null,
				new WorkspaceUserMetadata());
		assertThat("check a ws field", ws.getName(), is("bar"));
		
	}
	
	@Test
	public void startUpWith2ConfigDocs() throws Exception {
		final DB db = mongoClient.getDB("startUpWith2ConfigDocs");
		
		final Map<String, Object> m = new HashMap<String, Object>();
		m.put("config", "config");
		m.put("inupdate", false);
		m.put("schemaver", 1);
		
		db.getCollection("config").insert(Arrays.asList(
				(DBObject) new BasicDBObject(m), new BasicDBObject(m)));
		
		failMongoWSStart(db, new CorruptWorkspaceDBException(
				"Found duplicate index keys in the database, " +
						"aborting startup"));
	}
	
	@Test
	public void startUpWithBadSchemaVersion() throws Exception {
		final DB db = mongoClient.getDB("startUpWithBadSchemaVersion");
		
		final DBObject cfg = new BasicDBObject("config", "config");
		cfg.put("inupdate", false);
		cfg.put("schemaver", 4);
		
		db.getCollection("config").insert(cfg);
		
		failMongoWSStart(db, new WorkspaceDBInitializationException(
				"Incompatible database schema. Server is v1, DB is v4"));
	}
	
	@Test
	public void startUpWithUpdateInProgress() throws Exception {
		final DB db = mongoClient.getDB("startUpWithUpdateInProgress");
		
		final DBObject cfg = new BasicDBObject("config", "config");
		cfg.put("inupdate", true);
		cfg.put("schemaver", 1);
		
		db.getCollection("config").insert(cfg);
		
		failMongoWSStart(db, new CorruptWorkspaceDBException(
				"The database is in the middle of an update from v1 of the " +
				"schema. Aborting startup."));
	}

	private void failMongoWSStart(final DB db, final Exception exp)
			throws Exception {
		TempFilesManager tfm = new TempFilesManager(
				new File(TestCommon.getTempDir()));
		try {
			new MongoWorkspaceDB(db, new GridFSBlobStore(db), tfm);
			fail("started mongo with bad config");
		} catch (Exception e) {
			assertExceptionCorrect(e, exp);
		}
	}
	
	@Test
	public void raceConditionRevertObjectId() throws Exception {
		//TODO TEST more tests like this to test internals that can't be tested otherwise
		
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("ws");
		WorkspaceUser user = new WorkspaceUser("u");
		long wsid = ws.createWorkspace(user, wsi.getName(), false, null, null)
				.getId();
		
		final Map<String, Object> data = new HashMap<String, Object>();
		Map<String, String> meta = new HashMap<String, String>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		meta.put("metastuff", "meta");
		Provenance p = new Provenance(new WorkspaceUser("kbasetest2"));
		setWsidOnProvenance(wsid, p);
		TypeDefId t = new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);
		AbsoluteTypeDefId at = new AbsoluteTypeDefId(
				new TypeDefName("SomeModule", "AType"), 0, 1);
		
		WorkspaceSaveObject wso = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("testobj"), new UObject(data), t,
				new WorkspaceUserMetadata(meta), p, false);
		final DummyValidatedTypedObject dummy =
				new DummyValidatedTypedObject(at, wso.getData());
		dummy.calculateRelabeledSize();
		dummy.sort(new UTF8JsonSorterFactory(100000));
		ResolvedSaveObject rso = wso.resolve(
				dummy,
				new HashSet<Reference>(), new LinkedList<Reference>(),
				new HashMap<IdReferenceType, Set<RemappedId>>());
		final ResolvedWorkspaceID rwsi = mwdb.resolveWorkspace(wsi);
		mwdb.saveObjects(user, rwsi, Arrays.asList(rso));
		
		IDnPackage inp = startSaveObject(rwsi, rso, 3, at);
		ObjectSavePackage pkg = inp.pkg;
		IDName r = inp.idname;
		
		Field name = pkg.getClass().getDeclaredField("name");
		name.setAccessible(true);
		Field idname = r.getClass().getDeclaredField("name");
		idname.setAccessible(true);
		name.set(pkg, idname.get(r));
		
		Method saveProvenance = mwdb.getClass()
				.getDeclaredMethod("saveProvenance", List.class);
		saveProvenance.setAccessible(true);
		saveProvenance.invoke(mwdb, Arrays.asList(pkg));
		
		Method saveObjectVersion = mwdb.getClass()
				.getDeclaredMethod("saveObjectVersion", WorkspaceUser.class,
						ResolvedWorkspaceID.class, long.class, ObjectSavePackage.class);
		saveObjectVersion.setAccessible(true);
		Field idid = r.getClass().getDeclaredField("id");
		idid.setAccessible(true);
		ObjectInformation md = (ObjectInformation) saveObjectVersion.invoke(
				mwdb, new WorkspaceUser("u"), rwsi, idid.get(r), pkg);
		assertThat("objectid is revised to existing object", md.getObjectId(), is(1L));
	}

	private void setWsidOnProvenance(long wsid, Provenance p)
			throws NoSuchMethodException, IllegalAccessException,
			InvocationTargetException {
		Method setWsid = p.getClass().getDeclaredMethod("setWorkspaceID",
				Long.class);
		setWsid.setAccessible(true);
		setWsid.invoke(p, new Long(wsid));
	}
	
	@Test
	public void setGetRaceCondition() throws Exception {
		String objname = "testobj";
		String objname2 = "testobj2";
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("setGetRace");
		WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("setGetRace2");
		WorkspaceIdentifier wsi3 = new WorkspaceIdentifier("setGetRace3");
		
		WorkspaceUser user = new WorkspaceUser("u");
		long wsid = ws.createWorkspace(user, wsi.getName(), false, null, null)
				.getId();
		
		final Map<String, Object> data = new HashMap<String, Object>();
		Provenance p = new Provenance(new WorkspaceUser("kbasetest2"));
		setWsidOnProvenance(wsid, p);
		TypeDefId t = new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);
		AbsoluteTypeDefId at = new AbsoluteTypeDefId(
				new TypeDefName("SomeModule", "AType"), 0, 1);
		
		ResolvedSaveObject rso = createResolvedWSObj(objname, data, p, t, at);
		ResolvedSaveObject rso2 = createResolvedWSObj(objname2, data, p, t, at);
		final ResolvedWorkspaceID rwsi = mwdb.resolveWorkspace(wsi);
		
		startSaveObject(rwsi, rso, 1, at);
		mwdb.saveObjects(user, rwsi, Arrays.asList(rso2)); //objid 2

		//possible race condition 1 - no version provided, version not yet
		//saved, version count not yet incremented
		ObjectIDResolvedWS oidrw = new ObjectIDResolvedWS(rwsi,
				rso.getObjectIdentifier().getName().get());
		Set<ObjectIDResolvedWS> oidset = new HashSet<ObjectIDResolvedWS>(
				Arrays.asList(oidrw));
		failGetObjectsNoSuchObjectExcp(oidset,
				String.format("No object with name %s exists in workspace %s (name setGetRace)",
						objname, rwsi.getID()));
		
		try {
			mwdb.copyObject(user, oidrw, new ObjectIDResolvedWS(rwsi, "foo"));
			fail("copied object with no version");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getMessage(), is(String.format(
					"No object with name %s exists in workspace %s (name setGetRace)",
					objname, rwsi.getID())));
		}
		
		mwdb.cloneWorkspace(user, rwsi, wsi2.getName(), false, null,
				new WorkspaceUserMetadata(), null);
		final ResolvedWorkspaceID rwsi2 = mwdb.resolveWorkspace(wsi2);
		ObjectIDResolvedWS oidrw2_1 = new ObjectIDResolvedWS(rwsi2,
				rso.getObjectIdentifier().getName().get());
		failGetObjectsNoSuchObjectExcp(new HashSet<ObjectIDResolvedWS>(
					Arrays.asList(oidrw2_1)), String.format(
							"No object with name %s exists in workspace %s (name setGetRace2)",
							objname, rwsi2.getID()));

		ObjectIDResolvedWS oidrw2_2 = new ObjectIDResolvedWS(rwsi2,
				rso2.getObjectIdentifier().getName().get());
		
		long id = mwdb.getObjectInformation(new HashSet<ObjectIDResolvedWS>(
				Arrays.asList(oidrw2_2)), false, true, false, true)
				.get(oidrw2_2).getObjectId();
		assertThat("correct object id", id, is(2L));

		
		//possible race condition 2 - as 1, but version provided
		ObjectIDResolvedWS oidrwWithVer = new ObjectIDResolvedWS(rwsi,
				rso.getObjectIdentifier().getName().get(), 1);
		Set<ObjectIDResolvedWS> oidsetver = new HashSet<ObjectIDResolvedWS>();
		oidsetver.add(oidrwWithVer);
		failGetObjectsNoSuchObjectExcp(oidsetver,
				String.format("No object with name %s exists in workspace %s (name setGetRace)",
						objname, rwsi.getID()));
		
		try {
			mwdb.copyObject(user, oidrwWithVer, new ObjectIDResolvedWS(rwsi, "foo"));
			fail("copied object with no version");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getMessage(), is(String.format(
					"No object with name %s exists in workspace %s (name setGetRace)",
							objname, rwsi.getID())));
		}
		
		
		//race condition 3 - as 1, but with version incremented 
		//set the version to 1 in the workspace object. This state can
		//occur if a get happens between the increment and the save of the
		//version, although it's really rare
		jdb.getCollection("workspaceObjects")
			.update("{id: 1, ws: #}", rwsi.getID())
			.with("{$inc: {numver: 1}}");
		
		mwdb.cloneWorkspace(user, rwsi, wsi3.getName(), false, null,
				new WorkspaceUserMetadata(), null);
		final ResolvedWorkspaceID rwsi3 = mwdb.resolveWorkspace(wsi3);
		ObjectIDResolvedWS oidrw3_1 = new ObjectIDResolvedWS(rwsi3,
				rso.getObjectIdentifier().getName().get());
		failGetObjectsNoSuchObjectExcp(new HashSet<ObjectIDResolvedWS>(
				Arrays.asList(oidrw3_1)),
				String.format("No object with name %s exists in workspace %s (name setGetRace3)",
						objname, rwsi3.getID()));

		ObjectIDResolvedWS oidrw3_2 = new ObjectIDResolvedWS(rwsi3,
				rso2.getObjectIdentifier().getName().get());
		id = mwdb.getObjectInformation(new HashSet<ObjectIDResolvedWS>(
				Arrays.asList(oidrw3_2)), false, true, false, true).get(oidrw3_2).getObjectId();
		assertThat("correct object id", id, is(2L));
		
		try {
			mwdb.copyObject(user, oidrw, new ObjectIDResolvedWS(rwsi, "foo"));
			fail("copied object with no version");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getMessage(), is(String.format(
					"No object with name %s exists in workspace %s",
					objname, rwsi.getID())));
		}
		
		try {
			mwdb.copyObject(user, oidrwWithVer, new ObjectIDResolvedWS(rwsi, "foo"));
			fail("copied object with no version");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getMessage(),
					is(String.format("No object with id 1 (name %s) and version 1 exists in workspace %s (name %s)",
							objname, rwsi.getID(), rwsi.getName())));
		}
		
		failGetObjectsNoSuchObjectExcp(oidsetver,
				String.format("No object with id 1 (name %s) and version 1 exists in workspace %s (name %s)",
						objname, rwsi.getID(), rwsi.getName()));
		
		assertNull("can't get object mid save", mwdb.getObjectInformation(
				oidsetver, false, false, false, false).get(0));
		try {
			mwdb.getObjectInformation(oidsetver, false, true, false, true);
			fail("got object with no version");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getMessage(),
					is(String.format("No object with id 1 (name %s) and version 1 exists in workspace %s (name %s)",
							objname, rwsi.getID(), rwsi.getName())));
		}
	}

	private void failGetObjectsNoSuchObjectExcp(
			Set<ObjectIDResolvedWS> oidsetver, String msg)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException, TypedObjectExtractionException {
		final Map<ObjectIDResolvedWS, Set<SubsetSelection>> paths =
				new HashMap<ObjectIDResolvedWS, Set<SubsetSelection>>();
		for (final ObjectIDResolvedWS o: oidsetver) {
			paths.put(o, null);
		}
		final ByteArrayFileCacheManager man = new ByteArrayFileCacheManager(
				10000, 10000, mwdb.getTempFilesManager());
		try {
			mwdb.getObjects(paths, man, 0, true, false, true);
			fail("operated on object with no version");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getMessage(),
					is(msg));
		}
	}

	private ResolvedSaveObject createResolvedWSObj(String objname,
			final Map<String, Object> data, Provenance p, TypeDefId t,
			AbsoluteTypeDefId at) throws Exception {
		WorkspaceSaveObject wso = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer(objname),
				new UObject(data), t, null, p, false);
		final DummyValidatedTypedObject dummy =
				new DummyValidatedTypedObject(at, wso.getData());
		dummy.calculateRelabeledSize();
		dummy.sort(new UTF8JsonSorterFactory(100000));
		ResolvedSaveObject rso = wso.resolve(
				dummy,
				new HashSet<Reference>(), new LinkedList<Reference>(),
				new HashMap<IdReferenceType, Set<RemappedId>>());
		return rso;
	}
	
	class IDnPackage {
		IDName idname;
		ObjectSavePackage pkg;
		
		public IDnPackage(IDName idname, ObjectSavePackage pkg) {
			super();
			this.idname = idname;
			this.pkg = pkg;
		}
	}
	
	private IDnPackage startSaveObject(
			final ResolvedWorkspaceID rwsi,
			final ResolvedSaveObject rso,
			final int objid,
			final AbsoluteTypeDefId abstype) throws Exception {
		
		Constructor<ObjectSavePackage> objConst =
				ObjectSavePackage.class.getDeclaredConstructor();
		objConst.setAccessible(true);
		ObjectSavePackage pkg = objConst.newInstance();
		Field wo = pkg.getClass().getDeclaredField("wo");
		wo.setAccessible(true);
		wo.set(pkg, rso);
		
		Method incrementWorkspaceCounter = mwdb.getClass()
				.getDeclaredMethod("incrementWorkspaceCounter",
						ResolvedWorkspaceID.class, long.class);
		incrementWorkspaceCounter.setAccessible(true);
		incrementWorkspaceCounter.invoke(mwdb, rwsi, 1);
		
		Method saveWorkspaceObject = mwdb.getClass()
				.getDeclaredMethod("saveWorkspaceObject", ResolvedWorkspaceID.class,
						long.class, String.class);
		saveWorkspaceObject.setAccessible(true);
		String name = rso.getObjectIdentifier().getName().get();
		IDName idn = (IDName) saveWorkspaceObject.invoke(mwdb, rwsi, objid, name);
		return new IDnPackage(idn, pkg);
	}
	
	@Test
	public void refCounting() throws Exception {
		final String refcntspec =
				"module RefCount {" +
					"/* @id ws */" +
					"typedef string reference;" +
					"/* @optional ref */" + 
					"typedef structure {" +
						"reference ref;" +
					"} RefType;" +
				"};";
		
		String mod = "RefCount";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		types.requestModuleRegistration(userfoo, mod);
		types.resolveModuleRegistration(mod, true);
		types.compileNewTypeSpec(userfoo, refcntspec, Arrays.asList("RefType"), null, null, false, null);
		TypeDefId refcounttype = new TypeDefId(new TypeDefName(mod, "RefType"), 0, 1);
		
		WorkspaceIdentifier wspace = new WorkspaceIdentifier("refcount");
		long wsid = ws.createWorkspace(userfoo, wspace.getName(), false, null, null).getId();
		Provenance emptyprov = new Provenance(userfoo);
		Map<String, Object> data1 = new HashMap<String, Object>();
		data1.put("foo", 3);
		
		for (int i = 1; i < 5; i++) {
			for (int j = 0; j < 4; j++) {
				ws.saveObjects(userfoo, wspace, Arrays.asList(
						new WorkspaceSaveObject(new ObjectIDNoWSNoVer("obj" + i),
								new UObject(data1), SAFE_TYPE, null, emptyprov, false)),
								fac);
			}
		}
		// now we've got a 4x4 set of objects
		
		int[][] expected = new int[5][5];
		
		for (int i = 0; i < 16; i++) {
			int obj = (int) (Math.random() * 4.0) + 1;
			int ver = (int) (Math.random() * 4.0) + 1;
			expected[obj][ver]++;
			if (i % 2 == 0) {
				ws.saveObjects(userfoo, wspace, Arrays.asList(
						new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto" + (i + 5)),
								new UObject(withRef(data1, wsid, "obj" + obj, ver)),
								refcounttype, null, emptyprov, false)), fac);
			} else {
				ws.saveObjects(userfoo, wspace, Arrays.asList(
						new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto" + (i + 5)),
								new UObject(withRef(data1, wsid, obj, ver)),
								refcounttype, null, emptyprov, false)), fac);
			}
		}
		checkRefCounts(wsid, expected, 1);
		WorkspaceIdentifier wspace2 = new WorkspaceIdentifier("refcount2");
		ws.createWorkspace(userfoo, wspace2.getName(), false, null, null).getId();
		
		for (int i = 1; i <= 16; i++) {
			ws.copyObject(userfoo, new ObjectIdentifier(wspace, "auto" + (i + 4)),
					new ObjectIdentifier(wspace2, "obj" + i));
		}
		checkRefCounts(wsid, expected, 2);
		
		WorkspaceIdentifier wspace3 = new WorkspaceIdentifier("refcount3");
		ws.cloneWorkspace(userfoo, wspace2, wspace3.getName(), false, null,
				null, null);
		checkRefCounts(wsid, expected, 3);
		
		for (int i = 1; i <= 16; i++) {
			ws.revertObject(userfoo, new ObjectIdentifier(wspace3, "obj" + i));
		}
		checkRefCounts(wsid, expected, 4);
		
	}

	private void checkRefCounts(long wsid, int[][] expected, int factor) {
		for (int i = 1; i < 5; i++) {
			@SuppressWarnings("unchecked")
			Map<String, Object> obj = jdb.getCollection("workspaceObjects")
					.findOne("{ws: #, id: #}", wsid, i).as(Map.class);
			@SuppressWarnings("unchecked")
			List<Integer> refcnts = (List<Integer>) obj.get("refcnt");
			for (int j = 0; j < 4; j++) {
				assertThat("correct ref count", refcnts.get(j), is(expected[i][j + 1] * factor));
			}
		}
	}
	
	private Map<String, Object> withRef(Map<String, Object> map, long wsid,
			int name, int ver) {
		return withRef(map, wsid, "" + name, ver);
	}
	
	private Map<String, Object> withRef(Map<String, Object> map, long wsid,
			String name, int ver) {
		map.put("ref", wsid + "/" + name + "/" + ver);
		return map;
	}
	
	@Test
	public void testCopyAndRevertTags() throws Exception {
		testCopyRevert(false, "copyrevert");
		testCopyRevert(true, "copyreverthide");
	}

	private void testCopyRevert(Boolean hide,
			String wsprefix) throws Exception {
		
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		WorkspaceIdentifier copyrev = new WorkspaceIdentifier(wsprefix);
		long wsid = ws.createWorkspace(userfoo, copyrev.getName(), false, null, null).getId();
		
		Map<String, Object> data = new HashMap<String, Object>();
		ws.saveObjects(userfoo, copyrev, Arrays.asList(
				new WorkspaceSaveObject(getRandomName(), new UObject(data), SAFE_TYPE, null,
						new Provenance(userfoo), hide)), fac);
		ws.saveObjects(userfoo, copyrev, Arrays.asList(
				new WorkspaceSaveObject(getRandomName(), new UObject(data), SAFE_TYPE, null,
						new Provenance(userfoo), hide)), fac);
		ws.saveObjects(userfoo, copyrev, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer(2), new UObject(data), SAFE_TYPE,
						null, new Provenance(userfoo), hide)), fac);
		ws.saveObjects(userfoo, copyrev, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer(2), new UObject(data), SAFE_TYPE,
						null, new Provenance(userfoo), hide)), fac);
		ws.copyObject(userfoo, new ObjectIdentifier(copyrev, 2, 2),
				new ObjectIdentifier(copyrev, "auto3"));
		ws.copyObject(userfoo, new ObjectIdentifier(copyrev, 2),
				new ObjectIdentifier(copyrev, "auto4"));
		ws.revertObject(userfoo, new ObjectIdentifier(copyrev, "auto4", 2));
		
		checkRefCntInit(wsid, 3, 1);
		checkRefCntInit(wsid, 4, 4);
		
		@SuppressWarnings("rawtypes")
		List<Map> objverlist = iterToList(jdb.getCollection("workspaceObjVersions")
				.find("{ws: #, id: #}", wsid, 3).as(Map.class));
		assertThat("Only copied version once", objverlist.size(), is(1));
		@SuppressWarnings("unchecked")
		Map<String, Object> objver = (Map<String, Object>) objverlist.get(0);
		assertThat("correct copy location", (String) objver.get("copied"), is(wsid + "/2/2"));
		
		@SuppressWarnings("rawtypes")
		List<Map> objverlist2 = iterToList(jdb.getCollection("workspaceObjVersions")
				.find("{ws: #, id: #}", wsid, 4).as(Map.class));
		assertThat("Correct version count", 4, is(objverlist2.size()));
		Map<Integer, String> cpexpec = new HashMap<Integer, String>();
		Map<Integer, Integer> revexpec = new HashMap<Integer, Integer>();
		cpexpec.put(1, wsid + "/2/1");
		cpexpec.put(2, wsid + "/2/2");
		cpexpec.put(3, wsid + "/2/3");
		cpexpec.put(4, wsid + "/2/2");
		revexpec.put(1, null);
		revexpec.put(2, null);
		revexpec.put(3, null);
		revexpec.put(4, 2);
		for (@SuppressWarnings("rawtypes") Map m: objverlist2) {
			@SuppressWarnings("unchecked")
			Map<String, Object> m2 = (Map<String, Object>) m;
			int ver = (Integer) m2.get("ver");
			assertThat("copy pointer ok", (String) m2.get("copied"), is(cpexpec.get(ver)));
			assertThat("revert pointer ok", (Integer) m2.get("revert"), is(revexpec.get(ver)));
			
		}
		
		long wsid2 = ws.cloneWorkspace(userfoo, copyrev, wsprefix + "2",
				false, null, null, null).getId();
		
		checkRefCntInit(wsid2, 3, 1);
		checkRefCntInit(wsid2, 4, 4);
		
		@SuppressWarnings("rawtypes")
		List<Map> objverlist3 = iterToList(jdb.getCollection("workspaceObjVersions")
				.find("{ws: #, id: #}", wsid2, 3).as(Map.class));
		assertThat("Only copied version once", objverlist.size(), is(1));
		@SuppressWarnings("unchecked")
		Map<String, Object> objver3 = (Map<String, Object>) objverlist3.get(0);
		assertThat("correct copy location", (String) objver3.get("copied"), is(wsid + "/3/1"));
		
		@SuppressWarnings("rawtypes")
		List<Map> objverlist4 = iterToList(jdb.getCollection("workspaceObjVersions")
				.find("{ws: #, id: #}", wsid2, 4).as(Map.class));
		assertThat("Correct version count", 4, is(objverlist4.size()));
		Map<Integer, String> cpexpec2 = new HashMap<Integer, String>();
		Map<Integer, Integer> revexpec2 = new HashMap<Integer, Integer>();
		cpexpec2.put(1, wsid + "/4/1");
		cpexpec2.put(2, wsid + "/4/2");
		cpexpec2.put(3, wsid + "/4/3");
		cpexpec2.put(4, wsid + "/4/4");
		revexpec2.put(1, null);
		revexpec2.put(2, null);
		revexpec2.put(3, null);
		revexpec2.put(4, null);
		for (@SuppressWarnings("rawtypes") Map m: objverlist4) {
			@SuppressWarnings("unchecked")
			Map<String, Object> m2 = (Map<String, Object>) m;
			int ver = (Integer) m2.get("ver");
			assertThat("copy pointer ok", (String) m2.get("copied"), is(cpexpec2.get(ver)));
			assertThat("revert pointer ok", (Integer) m2.get("revert"), is(revexpec2.get(ver)));
		}
	}

	private void checkRefCntInit(long wsid, int objid, int vers) {
		@SuppressWarnings("rawtypes")
		List<Map> objlist = iterToList(jdb.getCollection("workspaceObjects")
				.find("{ws: #, id: #}", wsid, objid).as(Map.class));
		assertThat("Only one object per id", objlist.size(), is(1));
		@SuppressWarnings("unchecked")
		List<Integer> refcnts = (List<Integer>) objlist.get(0).get("refcnt");
		List<Integer> expected = new LinkedList<Integer>();
		for (int i = 0; i < vers; i++) {
			expected.add(0);
		}
		assertThat("refcnt array init correctly", refcnts, is(expected));
	}
	
	private <T> List<T> iterToList(Iterable<T> iter) {
		List<T> list = new LinkedList<T>();
		for (T item: iter) {
			list.add(item);
		}
		return list;
	}
	
	@Test
	public void dates() throws Exception {
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		WorkspaceIdentifier dates = new WorkspaceIdentifier("dates");
		long wsid = ws.createWorkspace(userfoo, dates.getName(), false, null, null).getId();
		
		Map<String, Object> data = new HashMap<String, Object>();
		ws.saveObjects(userfoo, dates, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("orig"), new UObject(data),
						SAFE_TYPE, null, new Provenance(userfoo), false)),
						fac);
		Date orig = getDate(wsid, 1);
		ws.copyObject(userfoo, new ObjectIdentifier(dates, "orig"),
				new ObjectIdentifier(dates, "copy"));
		Date copy = getDate(wsid, 2);
		ObjectIdentifier copyobj = new ObjectIdentifier(dates, "copy");
		ws.revertObject(userfoo, copyobj);
		Date revert = getDate(wsid, 2);
		ws.renameObject(userfoo, copyobj, "foobar");
		Date rename = getDate(wsid, 2);
		ObjectIdentifier foobar = new ObjectIdentifier(dates, "foobar");
		ws.setObjectsDeleted(userfoo, Arrays.asList(foobar), true);
		Date delete = getDate(wsid, 2);
		ws.setObjectsDeleted(userfoo, Arrays.asList(foobar), false);
		Date undelete = getDate(wsid, 2);
		
		assertTrue("copy date after orig", orig.before(copy));
		assertTrue("rev date after copy", copy.before(revert));
		assertTrue("ren date after rev", revert.before(rename));
		assertTrue("del date after ren", rename.before(delete));
		assertTrue("undel date after del", delete.before(undelete));
		assertDateisRecent(orig);
		assertDateisRecent(copy);
		assertDateisRecent(revert);
		assertDateisRecent(rename);
		assertDateisRecent(delete);
		assertDateisRecent(undelete);
	}

	private Date getDate(long wsid, int id) {
		@SuppressWarnings("rawtypes")
		Map obj = jdb.getCollection("workspaceObjects")
				.findOne("{ws: #, id: #}", wsid, id).as(Map.class);
		return (Date) obj.get("moddate");
	}

	private void assertDateisRecent(Date orig) {
		Date now = new Date();
		int onemin = 1000 * 60;
		assertTrue("date is recent", now.getTime() - orig.getTime() < onemin);
	}
}