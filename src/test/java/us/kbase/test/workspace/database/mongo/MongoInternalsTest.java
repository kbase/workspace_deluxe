package us.kbase.test.workspace.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static us.kbase.test.common.TestCommon.assertCloseToNow;
import static us.kbase.test.common.TestCommon.assertExceptionCorrect;
import static us.kbase.test.common.TestCommon.now;
import static us.kbase.test.workspace.WorkspaceTestCommon.basicProv;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bson.Document;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.common.service.UObject;
import us.kbase.testutils.controllers.mongo.MongoController;
import us.kbase.common.utils.sortjson.UTF8JsonSorterFactory;
import us.kbase.test.common.TestCommon;
import us.kbase.test.typedobj.DummyValidatedTypedObject;
import us.kbase.test.workspace.workspace.WorkspaceTester;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.LocalTypeProvider;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactoryBuilder;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.RemappedId;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedSaveObject;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.UncheckedUserMetadata;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.IDName;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.ObjectSavePackage;
import us.kbase.workspace.database.provenance.Provenance;

import com.google.common.collect.ImmutableMap;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

/*
 * These tests are naughty because they bypass the public API and test the internals directly.
 * In general, you shouldn't do that. For some of these tests, there's no good way to
 * test some of the code without sticking our filthy paws into the class guts (like
 * code that pulls DB data, modifies it, and saves it back while atomically checking it
 * hasn't changed since the pull and looping if it has), but some code was written too
 * protectively and probably could be refactored so the testing accessing internals aren't
 * necessary.
 */


public class MongoInternalsTest {

	private static MongoDatabase db;
	private static MongoWorkspaceDB mwdb;
	private static Workspace ws;
	private static Types types;
	private static MongoController mongo;
	private static MongoClient mongoClient;

	private static final IdReferenceHandlerSetFactory fac = IdReferenceHandlerSetFactoryBuilder
			.getBuilder(100).build().getFactory(null);

	public static final TypeDefId SAFE_TYPE =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);
	public static final TypeDefId OTHER_TYPE_NO_VER = new TypeDefId(
			new TypeDefName("SomeModule", "OtherType"));

	@BeforeClass
	public static void setUpClass() throws Exception {
		mongo = new MongoController(TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using mongo temp dir " +
				mongo.getTempDir());
		TestCommon.stfuLoggers();
		mongoClient = MongoClients.create("mongodb://localhost:" + mongo.getServerPort());
		db = mongoClient.getDatabase("MongoInternalsTest");
		final MongoDatabase tdb = mongoClient.getDatabase("MongoInternalsTest_types");
		TestCommon.destroyDB(db);
		TestCommon.destroyDB(tdb);

		final TempFilesManager tfm = new TempFilesManager(new File(TestCommon.getTempDir()));
		final TypeDefinitionDB typeDefDB = new TypeDefinitionDB(new MongoTypeStorage(tdb));
		TypedObjectValidator val = new TypedObjectValidator(
				new LocalTypeProvider(typeDefDB));
		mwdb = new MongoWorkspaceDB(db, new GridFSBlobStore(db));
		ws = new Workspace(mwdb, new ResourceUsageConfigurationBuilder().build(), val, tfm);

		//make a general spec that tests that don't worry about typechecking can use
		WorkspaceUser foo = new WorkspaceUser("foo");
		//simple spec
		types = new Types(typeDefDB);
		types.requestModuleRegistration(foo, "SomeModule");
		types.resolveModuleRegistration("SomeModule", true);
		types.compileNewTypeSpec(foo,
				"module SomeModule {" +
				"    /* @optional thing */" +
				"    typedef structure {" +
				"        string thing;" +
				"    } AType;" +
				"    /* @optional thing */" +
				"    typedef structure {" +
				"        string thing;" +
				"    } OtherType;" +
				"};",
				Arrays.asList("AType", "OtherType"), null, null, false, null);
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
		TestCommon.destroyDB(db);
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
		final Provenance p = basicProv(user1);

		// make a normal workspace & save objects
		ws.createWorkspace(user2, "bar", false, null,
				new WorkspaceUserMetadata());
		final WorkspaceIdentifier std = new WorkspaceIdentifier(1);
		ws.saveObjects(user2, std, Arrays.asList(
				new WorkspaceSaveObject(getRandomName(),
						new UObject(mt), SAFE_TYPE, null, p, false)),
				fac);
		final ObjectIdentifier stdobj = ObjectIdentifier.getBuilder(std).withID(1L).build();
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
		final ObjectIdentifier clnobj = ObjectIdentifier.getBuilder(cloning).withID(1L).build();

		final Document update = new Document("$set", new Document("cloning", true))
				.append("$unset", new Document("name", "").append("moddate", ""));
		db.getCollection("workspaces").updateOne(new Document("ws", 2), update);

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
				ObjectIdentifier.getBuilder(cloning).withName("foo").build(),
				new InaccessibleObjectException("Object foo cannot be " +
						"accessed: No workspace with id 2 exists", null));
		WorkspaceTester.failCopy(ws, user1, clnobj,
				ObjectIdentifier.getBuilder(std).withName("foo").build(), noObjExcp);

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
		final ObjectIdentifier oc = ObjectIdentifier.getBuilder(clnobj)
				.withReferencePath(Arrays.asList(stdobj)).build();
		WorkspaceTester.failGetReferencedObjects(ws, user1, Arrays.asList(oc),
				noObjExcp, false, new HashSet<>(Arrays.asList(0)));

		// test get subset
		final ObjectIdentifier os = ObjectIdentifier.getBuilder(clnobj)
				.withSubsetSelection(new SubsetSelection(Arrays.asList("/foo"))).build();
		WorkspaceTester.failGetSubset(ws, user1, Arrays.asList(os), noObjExcp);

		//test get ws desc
		WorkspaceTester.failGetWorkspaceDesc(ws, user1, cloning, noWSExcp);

		// test list objects
		WorkspaceTester.failListObjects(ws, user1, Arrays.asList(std, cloning),
				null, noWSExcp);

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
		WorkspaceTester.failSetWorkspaceOwner(ws, cloning,
				new WorkspaceUser("barbaz"), Optional.of("barbaz"), noWSExcp);

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
					is(ObjectIdentifier.getBuilder(cloning).withID(1L).build()));
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
		final Document ws = db.getCollection("workspaces").find(new Document("ws", id)).first();
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
		final Set<Map<String, Object>> acls = new HashSet<>();
		for (final Document acl: db.getCollection("workspaceACLs").find(new Document("id", id))) {
			/* fucking LazyBSONObjects, what the hell was mongo thinking */
			// ^ this comment might be moot with the switch to Document
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
			final Document ws,
			final Map<String, String> meta) {
		final Set<Map<String, String>> gotmeta = new HashSet<>();
		/* for some reason sometimes (but not always) get a LazyBsonList here
		 * which doesn't support listIterator which equals() uses, but this seems
		 * to fix it. Doesn't support toMap() either.
		 */
		// ^ this comment might be moot with the switch to Document.
		@SuppressWarnings("unchecked")
		final List<Document> shittymeta = (List<Document>) ws.get("meta");
		for (Document o: shittymeta) {
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
	public void raceConditionRevertObjectId() throws Exception {
		//TODO TEST more tests like this to test internals that can't be tested otherwise

		WorkspaceIdentifier wsi = new WorkspaceIdentifier("ws");
		WorkspaceUser user = new WorkspaceUser("u");
		long wsid = ws.createWorkspace(user, wsi.getName(), false, null, null).getId();

		final Map<String, Object> data = new HashMap<String, Object>();
		Map<String, String> meta = new HashMap<String, String>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		meta.put("metastuff", "meta");
		final Provenance p = Provenance.getBuilder(new WorkspaceUser("kbasetest2"), now())
				.withWorkspaceID(wsid).build();
		TypeDefId t = new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);
		AbsoluteTypeDefId at = new AbsoluteTypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);

		WorkspaceSaveObject wso = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("testobj"), new UObject(data), t,
				new WorkspaceUserMetadata(meta), p, false);
		final DummyValidatedTypedObject dummy =
				new DummyValidatedTypedObject(at, wso.getData());
		dummy.calculateRelabeledSize();
		dummy.sort(new UTF8JsonSorterFactory(100000));
		final ResolvedWorkspaceID rwsi = mwdb.resolveWorkspace(wsi);
		ResolvedSaveObject rso = wso.resolve(
				rwsi,
				dummy,
				new HashSet<Reference>(), new LinkedList<Reference>(),
				new HashMap<IdReferenceType, Set<RemappedId>>());
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
		final Provenance p = Provenance.getBuilder(new WorkspaceUser("kbasetest2"), now())
				.withWorkspaceID(wsid).build();
		TypeDefId t = new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);
		AbsoluteTypeDefId at = new AbsoluteTypeDefId(
				new TypeDefName("SomeModule", "AType"), 0, 1);

		final ResolvedWorkspaceID rwsi = mwdb.resolveWorkspace(wsi);
		ResolvedSaveObject rso = createResolvedWSObj(rwsi, objname, data, p, t, at);
		ResolvedSaveObject rso2 = createResolvedWSObj(rwsi, objname2, data, p, t, at);

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
		db.getCollection("workspaceObjects").updateOne(
				new Document("id", 1).append("ws", rwsi.getID()),
				new Document("$inc", new Document("numver", 1)));

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
				oidsetver, false, false, false, false).get(oidrwWithVer));
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
			final Set<ObjectIDResolvedWS> oidsetver,
			final String msg)
			throws WorkspaceCommunicationException {
		try {
			mwdb.getObjects(oidsetver, true, false, true);
			fail("operated on object with no version");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception message", nsoe.getMessage(), is(msg));
		}
	}

	private ResolvedSaveObject createResolvedWSObj(
			final ResolvedWorkspaceID rwsi,
			final String objname,
			final Map<String, Object> data,
			final Provenance p,
			final TypeDefId t,
			final AbsoluteTypeDefId at)
			throws Exception {
		final WorkspaceSaveObject wso = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer(objname),
				new UObject(data), t, null, p, false);
		final DummyValidatedTypedObject dummy =
				new DummyValidatedTypedObject(at, wso.getData());
		dummy.calculateRelabeledSize();
		dummy.sort(new UTF8JsonSorterFactory(100000));
		final ResolvedSaveObject rso = wso.resolve(
				rwsi,
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
		final Provenance emptyprov = basicProv(userfoo);
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
			ws.copyObject(userfoo,
					ObjectIdentifier.getBuilder(wspace).withName("auto" + (i + 4)).build(),
					ObjectIdentifier.getBuilder(wspace2).withName("obj" + i).build()
					);
		}
		checkRefCounts(wsid, expected, 2);

		WorkspaceIdentifier wspace3 = new WorkspaceIdentifier("refcount3");
		ws.cloneWorkspace(userfoo, wspace2, wspace3.getName(), false, null,
				null, null);
		checkRefCounts(wsid, expected, 3);

		for (int i = 1; i <= 16; i++) {
			ws.revertObject(
					userfoo, ObjectIdentifier.getBuilder(wspace3).withName("obj" + i).build());
		}
		checkRefCounts(wsid, expected, 4);

	}

	private void checkRefCounts(long wsid, int[][] expected, int factor) {
		for (int i = 1; i < 5; i++) {
			final Document obj = db.getCollection("workspaceObjects")
					.find(new Document("ws", wsid).append("id", i)).first();
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
		final Provenance p = basicProv(userfoo);
		ws.saveObjects(userfoo, copyrev, Arrays.asList(
				new WorkspaceSaveObject(getRandomName(), new UObject(data), SAFE_TYPE, null,
						p, hide)), fac);
		ws.saveObjects(userfoo, copyrev, Arrays.asList(
				new WorkspaceSaveObject(getRandomName(), new UObject(data), SAFE_TYPE, null,
						p, hide)), fac);
		ws.saveObjects(userfoo, copyrev, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer(2), new UObject(data), SAFE_TYPE,
						null, p, hide)), fac);
		ws.saveObjects(userfoo, copyrev, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer(2), new UObject(data), SAFE_TYPE,
						null, p, hide)), fac);
		ws.copyObject(
				userfoo,
				ObjectIdentifier.getBuilder(copyrev).withID(2L).withVersion(2).build(),
				ObjectIdentifier.getBuilder(copyrev).withName("auto3").build()
				);
		ws.copyObject(
				userfoo,
				ObjectIdentifier.getBuilder(copyrev).withID(2L).build(),
				ObjectIdentifier.getBuilder(copyrev).withName("auto4").build()
				);
		ws.revertObject(
				userfoo,
				ObjectIdentifier.getBuilder(copyrev).withName("auto4").withVersion(2).build()
				);

		checkRefCntInit(wsid, 3, 1);
		checkRefCntInit(wsid, 4, 4);

		List<Document> objverlist = iterToList(db.getCollection("workspaceObjVersions")
				.find(new Document("ws", wsid).append("id", 3)));
		assertThat("Only copied version once", objverlist.size(), is(1));
		Document objver = objverlist.get(0);
		assertThat("correct copy location", (String) objver.get("copied"), is(wsid + "/2/2"));

		List<Document> objverlist2 = iterToList(db.getCollection("workspaceObjVersions")
				.find(new Document("ws", wsid).append("id", 4)));
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
		for (final Document m: objverlist2) {
			int ver = (Integer) m.get("ver");
			assertThat("copy pointer ok", (String) m.get("copied"), is(cpexpec.get(ver)));
			assertThat("revert pointer ok", (Integer) m.get("revert"), is(revexpec.get(ver)));

		}

		long wsid2 = ws.cloneWorkspace(userfoo, copyrev, wsprefix + "2",
				false, null, null, null).getId();

		checkRefCntInit(wsid2, 3, 1);
		checkRefCntInit(wsid2, 4, 4);

		List<Document> objverlist3 = iterToList(db.getCollection("workspaceObjVersions")
				.find(new Document("ws", wsid2).append("id", 3)));
		assertThat("Only copied version once", objverlist.size(), is(1));
		Document objver3 = objverlist3.get(0);
		assertThat("correct copy location", (String) objver3.get("copied"), is(wsid + "/3/1"));

		List<Document> objverlist4 = iterToList(db.getCollection("workspaceObjVersions")
				.find(new Document("ws", wsid2).append("id", 4)));
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
		for (final Document m: objverlist4) {
			int ver = (Integer) m.get("ver");
			assertThat("copy pointer ok", (String) m.get("copied"), is(cpexpec2.get(ver)));
			assertThat("revert pointer ok", (Integer) m.get("revert"), is(revexpec2.get(ver)));
		}
	}

	private void checkRefCntInit(long wsid, int objid, int vers) {
		List<Document> objlist = iterToList(db.getCollection("workspaceObjects")
				.find(new Document("ws", wsid).append("id", objid)));
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
						SAFE_TYPE, null, basicProv(userfoo), false)),
				fac);
		Date orig = getDate(wsid, 1);
		ws.copyObject(
				userfoo,
				ObjectIdentifier.getBuilder(dates).withName("orig").build(),
				ObjectIdentifier.getBuilder(dates).withName("copy").build()
				);
		Date copy = getDate(wsid, 2);
		ObjectIdentifier copyobj = ObjectIdentifier.getBuilder(dates).withName("copy").build();
		ws.revertObject(userfoo, copyobj);
		Date revert = getDate(wsid, 2);
		ws.renameObject(userfoo, copyobj, "foobar");
		Date rename = getDate(wsid, 2);
		ObjectIdentifier foobar = ObjectIdentifier.getBuilder(dates).withName("foobar").build();
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
		final Document obj = db.getCollection("workspaceObjects")
				.find(new Document("ws", wsid).append("id", id)).first();
		return (Date) obj.get("moddate");
	}

	private void assertDateisRecent(Date orig) {
		Date now = new Date();
		int onemin = 1000 * 60;
		assertTrue("date is recent", now.getTime() - orig.getTime() < onemin);
	}

	private void typeFieldsSetUp() throws Exception {
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("typefields");
		ws.createWorkspace(user, wsi.getName(), false, null, null).getId();

		final Map<String, Object> data = new HashMap<>();
		final Provenance p = basicProv(user);
		ws.saveObjects(user, wsi, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("orig"), new UObject(data),
						SAFE_TYPE, null, p, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("orig2"), new UObject(data),
						OTHER_TYPE_NO_VER, null, p, false)),
				fac);
	}

	private void checkTypeFields(final int wsid, final int id1, final int id2) {
		checkTypeFields(wsid, id1, id2, 1, 1);
	}

	private void checkTypeFields(
			final int wsid, final int id1, final int id2, final int ver1, final int ver2) {
		final MongoCollection<Document> col = db.getCollection("workspaceObjVersions");
		final Document obj1 = col.find(
				new Document("ws", wsid).append("id", id1).append("ver", ver1)).first();
		final Document obj2 = col.find(
				new Document("ws", wsid).append("id", id2).append("ver", ver2)).first();

		assertThat("incorrect type name", obj1.get("tyname"), is("SomeModule.AType"));
		assertThat("incorrect type maj", obj1.get("tymaj"), is(0));
		assertThat("incorrect type maj", obj1.get("tymin"), is(1));
		// still present for rollbacks only
		assertThat("incorrect type", obj1.get("type"), is("SomeModule.AType-0.1"));

		assertThat("incorrect type", obj2.get("type"), is("SomeModule.OtherType-1.0"));
		assertThat("incorrect type maj", obj2.get("tymaj"), is(1));
		assertThat("incorrect type maj", obj2.get("tymin"), is(0));
		// still present for rollbacks only
		assertThat("incorrect type name", obj2.get("tyname"), is("SomeModule.OtherType"));
	}

	@Test
	public void typeFieldsOnSaveObjects() throws Exception {
		/* Test that the type fields are set up correctly when saving objects.
		 * The old 'type' field is internal only and not exposed in the API as it's only
		 * present for rollbacks.
		 */
		typeFieldsSetUp();
		checkTypeFields(1, 1, 2);
	}

	@Test
	public void typeFieldsOnCopiedObjects() throws Exception {
		/* Test that the 4 type fields are copied correctly */
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("typefields");

		typeFieldsSetUp();

		final ObjectIdentifier.Builder b = ObjectIdentifier.getBuilder(wsi);
		ws.copyObject(user, b.withID(1L).build(), b.withName("new1").build());
		ws.copyObject(user, b.withID(2L).build(), b.withName("new2").build());
		checkTypeFields(1, 3, 4);
	}

	@Test
	public void typeFieldsOnRevertedObjects() throws Exception {
		/* Test that the 4 type fields are reverted correctly */
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("typefields");

		typeFieldsSetUp();

		ws.revertObject(user, ObjectIdentifier.getBuilder(wsi).withID(1L).build());
		ws.revertObject(user, ObjectIdentifier.getBuilder(wsi).withID(2L).build());

		checkTypeFields(1, 1, 2, 2, 2);
	}

	@Test
	public void typeFieldsOnClone() throws Exception {
		/* Test that the 4 type fields are copied correctly on a workspace clone */
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("typefields");

		typeFieldsSetUp();

		ws.cloneWorkspace(user, wsi, "typefields2", false, null, null, null);

		checkTypeFields(2, 1, 2);
	}
	
	private Method getSetWorkspaceMetadataInternalMethod() throws Exception {
		final Method m = MongoWorkspaceDB.class.getDeclaredMethod(
				"_internal_setMeta",
				int.class,
				int.class,
				String.class,
				Document.class,
				Document.class,
				String.class);
		m.setAccessible(true);
		return m;
	}
	
	private ResolvedWorkspaceID setWorkspaceMetadataCreateWorkspace(
			Map<String, String> meta) throws Exception {
		final WorkspaceUser user = new WorkspaceUser("u");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(1, "foo", false, false);
		ws.createWorkspace(user, rwsi.getName(), false, null, new WorkspaceUserMetadata(meta));
		return rwsi;
	}
	
	@Test
	public void setWorkspaceMetadataInternalsPassAfterPreviousAttempts() throws Exception {
		final Method m = getSetWorkspaceMetadataInternalMethod();
		final ResolvedWorkspaceID rwsi = setWorkspaceMetadataCreateWorkspace(
				ImmutableMap.of("a", "b"));
		final Instant wscreate = mwdb.getWorkspaceInformation(null, rwsi).getModDate();
		final Instant result = (Instant) m.invoke(
				mwdb,
				3,
				5,
				"workspaces",
				new Document("ws", 1).append("meta", Arrays.asList(
						ImmutableMap.of("k", "a", "v", "b"))),
				new Document("meta", Arrays.asList(
						ImmutableMap.of("k", "a", "v", "x"), ImmutableMap.of("k", "y", "v", "z"))),
				"moddate"
		);
		assertCloseToNow(result);
		assertThat("moddate changed", result.isAfter(wscreate), is(true));
		final WorkspaceInformation wi = mwdb.getWorkspaceInformation(null, rwsi);
		assertThat("incorrect workspace info", wi, is(WorkspaceInformation.getBuilder()
				.withID(1)
				.withMaximumObjectID(0)
				.withUserPermission(Permission.NONE)
				.withModificationDate(result.truncatedTo(ChronoUnit.MILLIS)) // mongo truncates
				.withName("foo")
				.withOwner(new WorkspaceUser("u"))
				.withUserMetadata(new UncheckedUserMetadata(ImmutableMap.of(
						"a", "x", "y", "z")))
				.build()
		));
	}
	
	@Test
	public void setWorkspaceMetadataInternalsPassAfterPreviousAttemptsNoModDate()
			throws Exception {
		final Method m = getSetWorkspaceMetadataInternalMethod();
		final ResolvedWorkspaceID rwsi = setWorkspaceMetadataCreateWorkspace(
				ImmutableMap.of("a", "b"));
		final Instant wscreate = mwdb.getWorkspaceInformation(null, rwsi).getModDate();
		
		final Instant result = (Instant) m.invoke(
				mwdb,
				3,
				5,
				"workspaces",
				new Document("ws", 1).append("meta", Arrays.asList(
						ImmutableMap.of("k", "a", "v", "b"))),
				new Document("meta", Arrays.asList(
						ImmutableMap.of("k", "a", "v", "x"), ImmutableMap.of("k", "y", "v", "z"))),
				null
		);
		assertCloseToNow(result);
		assertThat("moddate changed", result.isAfter(wscreate), is(true));
		final WorkspaceInformation wi = mwdb.getWorkspaceInformation(null, rwsi);
		assertThat("incorrect workspace info", wi, is(WorkspaceInformation.getBuilder()
				.withID(1)
				.withMaximumObjectID(0)
				.withUserPermission(Permission.NONE)
				.withModificationDate(wscreate)
				.withName("foo")
				.withOwner(new WorkspaceUser("u"))
				.withUserMetadata(new UncheckedUserMetadata(ImmutableMap.of(
						"a", "x", "y", "z")))
				.build()
		));
	}
	
	@Test
	public void setWorkspaceMetadataInternalsNoUpdateAfterPreviousAttempts() throws Exception {
		final Method m = getSetWorkspaceMetadataInternalMethod();
		final ResolvedWorkspaceID rwsi = setWorkspaceMetadataCreateWorkspace(
				ImmutableMap.of("a", "b"));
		
		final Instant result = (Instant) m.invoke(
				mwdb,
				3,
				5,
				"workspaces",
				new Document("ws", 1).append("meta", Arrays.asList(
						ImmutableMap.of("k", "a", "v", "c"))),
				new Document("meta", Arrays.asList(
						ImmutableMap.of("k", "a", "v", "x"), ImmutableMap.of("k", "y", "v", "z"))),
				"moddate"
		);
		assertThat("incorrect time", result, is(nullValue()));
		final Map<String, String> meta = mwdb.getWorkspaceInformation(
				new WorkspaceUser("foo"), rwsi).getUserMeta().getMetadata();
		assertThat("incorrect meta", meta, is(ImmutableMap.of("a", "b")));
	}
	
	@Test
	public void setWorkspaceMetadataInternalsFailAfterPreviousAttempts() throws Exception {
		final Method m = getSetWorkspaceMetadataInternalMethod();
		final ResolvedWorkspaceID rwsi = setWorkspaceMetadataCreateWorkspace(
				ImmutableMap.of("a", "b"));
		try {
			m.invoke(
					mwdb,
					5,
					5,
					"workspaces",
					new Document("ws", 1).append("meta", Arrays.asList(
							ImmutableMap.of("k", "a", "v", "c"))),
					new Document("meta", Arrays.asList(
							ImmutableMap.of("k", "a", "v", "x"),
							ImmutableMap.of("k", "y", "v", "z"))),
					"moddate"
			);
		} catch (InvocationTargetException e) {
			assertExceptionCorrect(e.getCause(), new WorkspaceCommunicationException(
					"Failed to update metadata 5 times"));
		}
		final Map<String, String> meta = mwdb.getWorkspaceInformation(
				new WorkspaceUser("foo"), rwsi).getUserMeta().getMetadata();
		assertThat("incorrect meta", meta, is(ImmutableMap.of("a", "b")));
	}


}
