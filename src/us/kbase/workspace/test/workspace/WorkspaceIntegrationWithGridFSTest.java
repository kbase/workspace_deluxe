package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static us.kbase.workspace.test.WorkspaceTestCommon.basicProv;
import static us.kbase.workspace.test.WorkspaceTestCommon.SAFE_DATA;
import static us.kbase.workspace.test.WorkspaceTestCommon.SAFE_DATA_MD5;
import static us.kbase.workspace.test.WorkspaceTestCommon.SAFE_DATA_SIZE;
import static us.kbase.workspace.test.WorkspaceTestCommon.ATYPE;
import static us.kbase.workspace.test.WorkspaceTestCommon.ATYPE_1_0;
import static us.kbase.workspace.test.WorkspaceTestCommon.ATYPE2;
import static us.kbase.workspace.test.WorkspaceTestCommon.ATYPE2_2_1;
import static us.kbase.workspace.test.WorkspaceTestCommon.ATYPE2_2_0;
import static us.kbase.workspace.test.WorkspaceTestCommon.ATYPE2_1_0;
import static us.kbase.workspace.test.WorkspaceTestCommon.ATYPE2_0_1;

import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.LocalTypeProvider;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactoryBuilder;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.database.DynamicConfig;
import us.kbase.workspace.database.DynamicConfig.DynamicConfigUpdate;
import us.kbase.workspace.database.ListObjectsParameters;
import us.kbase.workspace.database.ListObjectsParameters.Builder;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.RefLimit;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.provenance.Provenance;
import us.kbase.workspace.test.WorkspaceTestCommon;

/** Workspace library level integration tests that don't need to be run against multiple
 * backends or with varying memory usage strategies. See the notes in {@link WorkspaceTest}.
 *
 * These tests cover user API agnostic behavior. API layers should also have their own
 * integration tests. As of this writing, the only API layer for the workspace is the JSONRPC
 * API implemented in {@link WorkspaceServer}.
 *
 * These tests all run against a GridFS backend for object storage.
 */
public class WorkspaceIntegrationWithGridFSTest {

	private static final WorkspaceUser USER = new WorkspaceUser("user");
	private static final Date SOME_DATE = Date.from(Instant.ofEpochMilli(10000));
	private static final WorkspaceIdentifier WS1 = new WorkspaceIdentifier("ws1");
	private static final ResolvedWorkspaceID RWS1 = new ResolvedWorkspaceID(
			1, "ws1", false, false);
	private static final WorkspaceIdentifier WS2 = new WorkspaceIdentifier("ws2");
	private static final ResolvedWorkspaceID RWS2 = new ResolvedWorkspaceID(
			2, "ws2", false, false);

	private static final String WSDB_NAME = WorkspaceIntegrationWithGridFSTest.class
			.getSimpleName();
	private static final String TYPEDB_NAME = WSDB_NAME + "_types";

	private static MongoController MONGO;
	private static TempFilesManager TFM;
	private static Workspace WORK;
	private static MongoDatabase WSDB;
	private static MongoDatabase TYPEDB;

	static {
		// mongo is really chatty
		((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
				.setLevel(Level.OFF);
	}

	@BeforeClass
	public static void setUpClass() throws Exception {
		MONGO = new MongoController(TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using Mongo temp dir " + MONGO.getTempDir());
		System.out.println("Started test mongo instance at localhost: " + MONGO.getServerPort());

		@SuppressWarnings("resource")
		final MongoClient mcli = MongoClients.create("mongodb://localhost:" + MONGO.getServerPort());
		WSDB = mcli.getDatabase(WSDB_NAME);
		TYPEDB = mcli.getDatabase(TYPEDB_NAME);
		TestCommon.destroyDB(WSDB);
		TestCommon.destroyDB(TYPEDB);
		TFM = new TempFilesManager(new File(TestCommon.getTempDir()));
		TFM.cleanup();

		final TypeDefinitionDB typeDB = new TypeDefinitionDB(new MongoTypeStorage(TYPEDB));
		final TypedObjectValidator val = new TypedObjectValidator(new LocalTypeProvider(typeDB));
		WORK = new Workspace(
				new MongoWorkspaceDB(WSDB, new GridFSBlobStore(WSDB)),
				new ResourceUsageConfigurationBuilder().build(),
				val,
				TFM);
		WorkspaceTestCommon.installBasicSpecs(new WorkspaceUser("user"), new Types(typeDB));
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		if (MONGO != null) {
			MONGO.destroy(TestCommon.getDeleteTempFiles());
		}
		if (TFM != null) {
			System.out.println("deleting temporary files");
			TFM.cleanup();
		}
	}

	@Before
	public void clearDB() throws Exception {
		try (final MongoClient mongoClient = MongoClients.create(
				"mongodb://localhost:" + MONGO.getServerPort())) {
			TestCommon.destroyDB(mongoClient.getDatabase(WSDB_NAME));
		}
	}

	@After
	public void after() throws Exception {
		TestCommon.assertNoTempFilesExist(TFM);
	}

	private ObjectIDNoWSNoVer objID(final String id) {
		return new ObjectIDNoWSNoVer(id);
	}

	private ObjectInformation oinf(
			final ResolvedWorkspaceID wsi,
			final int id,
			final int version,
			final TypeDefId type) {
		return ObjectInformation.getBuilder()
				.withObjectID(id)
				.withObjectName("o" + id)
				.withType(AbsoluteTypeDefId.fromTypeId(type))
				.withSavedDate(SOME_DATE)
				.withVersion(version)
				.withSavedBy(USER)
				.withWorkspace(wsi)
				.withChecksum(SAFE_DATA_MD5)
				.withSize(SAFE_DATA_SIZE)
				.build();
	}

	private List<ObjectInformation> cleanDates(final List<ObjectInformation> toBeCleaned) {
		return toBeCleaned.stream().map(o -> ObjectInformation.getBuilder()
				.withObjectID(o.getObjectId())
				.withObjectName(o.getObjectName())
				.withType(AbsoluteTypeDefId.fromAbsoluteTypeString(o.getTypeString()))
				.withSavedDate(SOME_DATE)
				.withVersion(o.getVersion())
				.withSavedBy(o.getSavedBy())
				.withWorkspace(new ResolvedWorkspaceID(
						o.getWorkspaceId(), o.getWorkspaceName(), false, false))
				.withChecksum(o.getCheckSum())
				.withSize(o.getSize())
				.withUserMetadata(o.getUserMetaData().orElse(null))
				.build())
				.collect(Collectors.toList());
	}

	private IdReferenceHandlerSetFactory getIdFactory() {
		return getIdFactory(100000);
	}
	private IdReferenceHandlerSetFactory getIdFactory(final int maxIDs) {
		return IdReferenceHandlerSetFactoryBuilder.getBuilder(maxIDs).build().getFactory(null);
	}

	private void saveObjects(
			final WorkspaceIdentifier wsi,
			final int start,				// inclusive
			final int end,					// exclusive
			final Function<Integer, TypeDefId> typeResolver)
			throws Exception {
		final Provenance p = basicProv(USER);
		final List<WorkspaceSaveObject> objects = IntStream.range(start, end)
				.mapToObj(i -> new WorkspaceSaveObject(
						objID("o" + (i + 1)), SAFE_DATA, typeResolver.apply(i), null, p, false))
				.collect(Collectors.toList());
		WORK.saveObjects(USER, wsi, objects, getIdFactory());
	}

	private void setUpTypeSort() throws Exception {
		WORK.createWorkspace(USER, WS1.getName(), false, null, null);
		WORK.createWorkspace(USER, WS2.getName(), false, null, null);
		final List<TypeDefId> types = Arrays.asList(
				ATYPE2_2_1, ATYPE2_2_0, ATYPE2_1_0, ATYPE2_0_1);
		saveObjects(WS1, 0, 4, i -> types.get(i % 4));
		saveObjects(WS1, 4, 5, i -> ATYPE_1_0);
		saveObjects(WS1, 0, 4, i -> types.get((i + 1) % 4)); // new versions per object
		saveObjects(WS2, 0, 1, i -> ATYPE2_1_0);
		saveObjects(WS1, 5, 9, i -> types.get((i + 3) % 4)); // account for extra object
		saveObjects(WS1, 5, 9, i -> types.get(i % 4));
	}

	private void checkSort(final ListObjectsParameters p, final List<ObjectInformation> expected)
			throws Exception {
		List<ObjectInformation> ret = cleanDates(WORK.listObjects(p));
		for (int i = 0; i < ret.size(); i++) { // easier to debug vs comparing entire list
			assertThat("incorrect object index " + i, ret.get(i), is(expected.get(i)));
		}
		assertThat("incorrect object count", ret.size(), is(expected.size()));
	}

	@Test
	public void dynamicConfig() throws Exception {
		// db should be detroyed at the beginning of every test
		final DynamicConfig empty = DynamicConfig.getBuilder().build();
		assertThat("incorrect config", WORK.getConfig(), is(empty));

		// but on creation config should be initialized
		createWorkspaceClass();
		final DynamicConfig expected1 = DynamicConfig.getBuilder().withBackendScaling(1).build();
		assertThat("incorrect config", WORK.getConfig(), is(expected1));

		// test that recreating the workspace doesn't set things back to the default
		WORK.setConfig(DynamicConfigUpdate.getBuilder().withBackendScaling(42).build());

		final DynamicConfig expected42 = DynamicConfig.getBuilder().withBackendScaling(42).build();
		assertThat("incorrect config", WORK.getConfig(), is(expected42));

		createWorkspaceClass();
		assertThat("incorrect config", WORK.getConfig(), is(expected42));
	}

	public Workspace createWorkspaceClass() throws Exception {
		return new Workspace(
				new MongoWorkspaceDB(WSDB, new GridFSBlobStore(WSDB)),
				new ResourceUsageConfigurationBuilder().build(),
				new TypedObjectValidator(new LocalTypeProvider(new TypeDefinitionDB(
						new MongoTypeStorage(TYPEDB)))),
				TFM
				);
	}

	@Test
	public void sortWithVersions() throws Exception {
		setUpTypeSort();

		final ListObjectsParameters p = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER).withShowAllVersions(true).build();

		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 1, 2, ATYPE2_2_0),
				oinf(RWS1, 1, 1, ATYPE2_2_1),
				oinf(RWS1, 2, 2, ATYPE2_1_0),
				oinf(RWS1, 2, 1, ATYPE2_2_0),
				oinf(RWS1, 3, 2, ATYPE2_0_1),
				oinf(RWS1, 3, 1, ATYPE2_1_0),
				oinf(RWS1, 4, 2, ATYPE2_2_1),
				oinf(RWS1, 4, 1, ATYPE2_0_1),
				oinf(RWS1, 5, 1, ATYPE_1_0),
				oinf(RWS1, 6, 2, ATYPE2_2_0),
				oinf(RWS1, 6, 1, ATYPE2_2_1),
				oinf(RWS1, 7, 2, ATYPE2_1_0),
				oinf(RWS1, 7, 1, ATYPE2_2_0),
				oinf(RWS1, 8, 2, ATYPE2_0_1),
				oinf(RWS1, 8, 1, ATYPE2_1_0),
				oinf(RWS1, 9, 2, ATYPE2_2_1),
				oinf(RWS1, 9, 1, ATYPE2_0_1),
				oinf(RWS2, 1, 1, ATYPE2_1_0)
				);
		checkSort(p, expected);
	}

	@Test
	public void sortWithVersionsWithStartFrom() throws Exception {
		// this test will cover most of the start from interactions, with other tests
		// having fewer cases
		setUpTypeSort();

		final Builder lob = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER).withShowAllVersions(true);

		// case 1: no UPA filter, ws only
		ListObjectsParameters p = lob.withStartFrom(RefLimit.build(1L, null, null)).build();

		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 1, 2, ATYPE2_2_0),
				oinf(RWS1, 1, 1, ATYPE2_2_1),
				oinf(RWS1, 2, 2, ATYPE2_1_0),
				oinf(RWS1, 2, 1, ATYPE2_2_0),
				oinf(RWS1, 3, 2, ATYPE2_0_1),
				oinf(RWS1, 3, 1, ATYPE2_1_0),
				oinf(RWS1, 4, 2, ATYPE2_2_1),
				oinf(RWS1, 4, 1, ATYPE2_0_1),
				oinf(RWS1, 5, 1, ATYPE_1_0),
				oinf(RWS1, 6, 2, ATYPE2_2_0),
				oinf(RWS1, 6, 1, ATYPE2_2_1),
				oinf(RWS1, 7, 2, ATYPE2_1_0),
				oinf(RWS1, 7, 1, ATYPE2_2_0),
				oinf(RWS1, 8, 2, ATYPE2_0_1),
				oinf(RWS1, 8, 1, ATYPE2_1_0),
				oinf(RWS1, 9, 2, ATYPE2_2_1),
				oinf(RWS1, 9, 1, ATYPE2_0_1),
				oinf(RWS2, 1, 1, ATYPE2_1_0)
				);
		checkSort(p, expected);

		// case 2: no UPA filter, id & ws
		p = lob.withStartFrom(RefLimit.build(1L, 1L, null)).build();
		checkSort(p, expected);

		// case 3: no UPA filter, full spec
		p = lob.withStartFrom(RefLimit.build(1L, 1L, 2)).build();
		checkSort(p, expected);

		// case 4: exclude first version in list
		p = lob.withStartFrom(RefLimit.build(1L, 1L, 1)).build();
		checkSort(p, expected.subList(1, expected.size()));

		// case 5: exclude objects and a version
		p = lob.withStartFrom(RefLimit.build(1L, 3L, 1)).build();
		checkSort(p, expected.subList(5, expected.size()));

		// case 6: exclude workspaces
		p = lob.withStartFrom(RefLimit.build(2L, null, null)).build();
		checkSort(p, expected.subList(17, expected.size()));

		// case 7: exclude workspaces with full spec
		p = lob.withStartFrom(RefLimit.build(2L, 1L, 1)).build();
		checkSort(p, expected.subList(17, expected.size()));

		// case 8: exclude everything by workspace
		p = lob.withStartFrom(RefLimit.build(3L, null, null)).build();
		checkSort(p, Collections.emptyList());

		// case 9: exclude everything by object
		p = lob.withStartFrom(RefLimit.build(2L, 2L, null)).build();
		checkSort(p, Collections.emptyList());

		// case 10: hidden and deleted objects
		WORK.setObjectsDeleted(USER, Arrays.asList(
				ObjectIdentifier.getBuilder(WS1).withID(7L).build()), true);
		WORK.setObjectsHidden(USER, Arrays.asList(
				ObjectIdentifier.getBuilder(WS1).withID(4L).build()), true);
		p = lob.withStartFrom(RefLimit.build(1L, 2L, 1)).build();
		expected = Arrays.asList(
				oinf(RWS1, 2, 1, ATYPE2_2_0),
				oinf(RWS1, 3, 2, ATYPE2_0_1),
				oinf(RWS1, 3, 1, ATYPE2_1_0),
				oinf(RWS1, 5, 1, ATYPE_1_0),
				oinf(RWS1, 6, 2, ATYPE2_2_0),
				oinf(RWS1, 6, 1, ATYPE2_2_1),
				oinf(RWS1, 8, 2, ATYPE2_0_1),
				oinf(RWS1, 8, 1, ATYPE2_1_0),
				oinf(RWS1, 9, 2, ATYPE2_2_1),
				oinf(RWS1, 9, 1, ATYPE2_0_1),
				oinf(RWS2, 1, 1, ATYPE2_1_0)
				);
		checkSort(p, expected);
	}

	@Test
	public void sortWithVersionsWithObjectRange() throws Exception {
		setUpTypeSort();

		final ListObjectsParameters p = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER).withShowAllVersions(true)
				.withMinObjectID(3).withMaxObjectID(7).build();

		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 3, 2, ATYPE2_0_1),
				oinf(RWS1, 3, 1, ATYPE2_1_0),
				oinf(RWS1, 4, 2, ATYPE2_2_1),
				oinf(RWS1, 4, 1, ATYPE2_0_1),
				oinf(RWS1, 5, 1, ATYPE_1_0),
				oinf(RWS1, 6, 2, ATYPE2_2_0),
				oinf(RWS1, 6, 1, ATYPE2_2_1),
				oinf(RWS1, 7, 2, ATYPE2_1_0),
				oinf(RWS1, 7, 1, ATYPE2_2_0)
				);
		checkSort(p, expected);
	}

	@Test
	public void sortWithNoVersions() throws Exception {
		setUpTypeSort();

		final ListObjectsParameters p = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER).build();

		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 1, 2, ATYPE2_2_0),
				oinf(RWS1, 2, 2, ATYPE2_1_0),
				oinf(RWS1, 3, 2, ATYPE2_0_1),
				oinf(RWS1, 4, 2, ATYPE2_2_1),
				oinf(RWS1, 5, 1, ATYPE_1_0),
				oinf(RWS1, 6, 2, ATYPE2_2_0),
				oinf(RWS1, 7, 2, ATYPE2_1_0),
				oinf(RWS1, 8, 2, ATYPE2_0_1),
				oinf(RWS1, 9, 2, ATYPE2_2_1),
				oinf(RWS2, 1, 1, ATYPE2_1_0)
				);
		checkSort(p, expected);
	}

	@Test
	public void sortWithNoVersionsWithStartFrom() throws Exception {
		setUpTypeSort();

		final Builder lob = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER);

		// case 1: no UPA filter, ws only
		ListObjectsParameters p = lob.withStartFrom(RefLimit.build(1L, null, null)).build();

		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 1, 2, ATYPE2_2_0),
				oinf(RWS1, 2, 2, ATYPE2_1_0),
				oinf(RWS1, 3, 2, ATYPE2_0_1),
				oinf(RWS1, 4, 2, ATYPE2_2_1),
				oinf(RWS1, 5, 1, ATYPE_1_0),
				oinf(RWS1, 6, 2, ATYPE2_2_0),
				oinf(RWS1, 7, 2, ATYPE2_1_0),
				oinf(RWS1, 8, 2, ATYPE2_0_1),
				oinf(RWS1, 9, 2, ATYPE2_2_1),
				oinf(RWS2, 1, 1, ATYPE2_1_0)
				);
		checkSort(p, expected);

		// case 2: no UPA filter, id & ws
		p = lob.withStartFrom(RefLimit.build(1L, 1L, null)).build();
		checkSort(p, expected);

		// case 3: no UPA filter, full spec
		p = lob.withStartFrom(RefLimit.build(1L, 1L, 2)).build();
		checkSort(p, expected);

		// case 4: exclude first object in list by version
		p = lob.withStartFrom(RefLimit.build(1L, 1L, 1)).build();
		checkSort(p, expected.subList(1, expected.size()));

		// case 5: exclude first object in list by object ID
		p = lob.withStartFrom(RefLimit.build(1L, 2L, null)).build();
		checkSort(p, expected.subList(1, expected.size()));

		// case 6: exclude objects
		p = lob.withStartFrom(RefLimit.build(1L, 3L, 1)).build();
		checkSort(p, expected.subList(3, expected.size()));

		// case 7: exclude workspaces
		p = lob.withStartFrom(RefLimit.build(2L, null, null)).build();
		checkSort(p, expected.subList(9, expected.size()));
	}

	@Test
	public void sortWithNoVersionsWithObjectRange() throws Exception {
		setUpTypeSort();

		final ListObjectsParameters p = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER)
				.withMinObjectID(2).withMaxObjectID(8).build();

		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 2, 2, ATYPE2_1_0),
				oinf(RWS1, 3, 2, ATYPE2_0_1),
				oinf(RWS1, 4, 2, ATYPE2_2_1),
				oinf(RWS1, 5, 1, ATYPE_1_0),
				oinf(RWS1, 6, 2, ATYPE2_2_0),
				oinf(RWS1, 7, 2, ATYPE2_1_0),
				oinf(RWS1, 8, 2, ATYPE2_0_1)
				);
		checkSort(p, expected);
	}

	@Test
	public void sortWithVersionsFullTypeFilter() throws Exception {
		setUpTypeSort();

		final Builder p = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER).withShowAllVersions(true);

		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 1, 1, ATYPE2_2_1),
				oinf(RWS1, 4, 2, ATYPE2_2_1),
				oinf(RWS1, 6, 1, ATYPE2_2_1),
				oinf(RWS1, 9, 2, ATYPE2_2_1)
				);
		checkSort(p.withType(ATYPE2_2_1).build(), expected);

		expected = Arrays.asList(
				oinf(RWS1, 1, 2, ATYPE2_2_0),
				oinf(RWS1, 2, 1, ATYPE2_2_0),
				oinf(RWS1, 6, 2, ATYPE2_2_0),
				oinf(RWS1, 7, 1, ATYPE2_2_0)
				);
		checkSort(p.withType(ATYPE2_2_0).build(), expected);

		expected = Arrays.asList(
				oinf(RWS1, 2, 2, ATYPE2_1_0),
				oinf(RWS1, 3, 1, ATYPE2_1_0),
				oinf(RWS1, 7, 2, ATYPE2_1_0),
				oinf(RWS1, 8, 1, ATYPE2_1_0),
				oinf(RWS2, 1, 1, ATYPE2_1_0)
				);
		checkSort(p.withType(ATYPE2_1_0).build(), expected);

		expected = Arrays.asList(
				oinf(RWS1, 3, 2, ATYPE2_0_1),
				oinf(RWS1, 4, 1, ATYPE2_0_1),
				oinf(RWS1, 8, 2, ATYPE2_0_1),
				oinf(RWS1, 9, 1, ATYPE2_0_1)
				);
		checkSort(p.withType(ATYPE2_0_1).build(), expected);
	}

	@Test
	public void sortWithVersionsFullTypeFilterWithStartFrom() throws Exception {
		setUpTypeSort();

		final Builder lob = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER).withShowAllVersions(true).withType(ATYPE2_1_0);

		// case 1: no UPA filtering
		ListObjectsParameters p = lob.withStartFrom(RefLimit.build(1L, 2L, 2)).build();
		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 2, 2, ATYPE2_1_0),
				oinf(RWS1, 3, 1, ATYPE2_1_0),
				oinf(RWS1, 7, 2, ATYPE2_1_0),
				oinf(RWS1, 8, 1, ATYPE2_1_0),
				oinf(RWS2, 1, 1, ATYPE2_1_0)
				);
		checkSort(p, expected);

		// case 1: UPA filter first object
		p = lob.withStartFrom(RefLimit.build(1L, 2L, 1)).build();
		checkSort(p, expected.subList(1, expected.size()));

		// case 2: UPA filter a few objects
		p = lob.withStartFrom(RefLimit.build(1L, 7L, 1)).build();
		checkSort(p, expected.subList(3, expected.size()));

		// case 3: UPA filter a workspace
		p = lob.withStartFrom(RefLimit.build(1L, 9L, 2)).build();
		checkSort(p, expected.subList(4, expected.size()));

		// case 4: UPA filter a workspace by the wsid
		p = lob.withStartFrom(RefLimit.build(2L, null, null)).build();
		checkSort(p, expected.subList(4, expected.size()));
	}

	@Test
	public void sortWithVersionsFullTypeFilterWithObjectRange() throws Exception {
		setUpTypeSort();

		final ListObjectsParameters p = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER).withShowAllVersions(true)
				.withMinObjectID(3).withMaxObjectID(7).withType(ATYPE2_1_0).build();

		final List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 3, 1, ATYPE2_1_0),
				oinf(RWS1, 7, 2, ATYPE2_1_0)
				);
		checkSort(p, expected);
	}

	@Test
	public void sortWithoutVersionsFullTypeFilter() throws Exception {
		setUpTypeSort();

		final Builder p = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER);

		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 4, 2, ATYPE2_2_1),
				oinf(RWS1, 9, 2, ATYPE2_2_1)
				);
		checkSort(p.withType(ATYPE2_2_1).build(), expected);

		expected = Arrays.asList(
				oinf(RWS1, 1, 2, ATYPE2_2_0),
				oinf(RWS1, 6, 2, ATYPE2_2_0)
				);
		checkSort(p.withType(ATYPE2_2_0).build(), expected);

		expected = Arrays.asList(
				oinf(RWS1, 2, 2, ATYPE2_1_0),
				oinf(RWS1, 7, 2, ATYPE2_1_0),
				oinf(RWS2, 1, 1, ATYPE2_1_0)
				);
		checkSort(p.withType(ATYPE2_1_0).build(), expected);

		expected = Arrays.asList(
				oinf(RWS1, 3, 2, ATYPE2_0_1),
				oinf(RWS1, 8, 2, ATYPE2_0_1)
				);
		checkSort(p.withType(ATYPE2_0_1).build(), expected);
	}

	@Test
	public void sortWithoutVersionsFullTypeFilterWithObjectRange() throws Exception {
		setUpTypeSort();

		final ListObjectsParameters p = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER).withMinObjectID(3).withMaxObjectID(7)
				.withType(ATYPE2_1_0).build();

		checkSort(p, Arrays.asList(oinf(RWS1, 7, 2, ATYPE2_1_0)));
	}


	@Test
	public void sortWithVersionsMajorVersionTypeFilter() throws Exception {
		setUpTypeSort();

		final Builder p = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER).withShowAllVersions(true);

		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 1, 2, ATYPE2_2_0),
				oinf(RWS1, 1, 1, ATYPE2_2_1),
				oinf(RWS1, 2, 1, ATYPE2_2_0),
				oinf(RWS1, 4, 2, ATYPE2_2_1),
				oinf(RWS1, 6, 2, ATYPE2_2_0),
				oinf(RWS1, 6, 1, ATYPE2_2_1),
				oinf(RWS1, 7, 1, ATYPE2_2_0),
				oinf(RWS1, 9, 2, ATYPE2_2_1)
				);
		checkSort(p.withType(new TypeDefId(ATYPE2, 2)).build(), expected);

		expected = Arrays.asList(
				oinf(RWS1, 2, 2, ATYPE2_1_0),
				oinf(RWS1, 3, 1, ATYPE2_1_0),
				oinf(RWS1, 7, 2, ATYPE2_1_0),
				oinf(RWS1, 8, 1, ATYPE2_1_0),
				oinf(RWS2, 1, 1, ATYPE2_1_0)
				);
		checkSort(p.withType(new TypeDefId(ATYPE2, 1)).build(), expected);

		expected = Arrays.asList(
				oinf(RWS1, 3, 2, ATYPE2_0_1),
				oinf(RWS1, 4, 1, ATYPE2_0_1),
				oinf(RWS1, 8, 2, ATYPE2_0_1),
				oinf(RWS1, 9, 1, ATYPE2_0_1)
				);
		checkSort(p.withType(new TypeDefId(ATYPE2, 0)).build(), expected);
	}

	@Test
	public void sortWithVersionsMajorVersionTypeFilterWithStartFrom() throws Exception {
		setUpTypeSort();

		final Builder lob = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER).withShowAllVersions(true).withType(new TypeDefId(ATYPE2, 2));

		// case 1: no UPA filtering
		ListObjectsParameters p = lob.withStartFrom(RefLimit.build(1L, 1L, null)).build();
		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 1, 2, ATYPE2_2_0),
				oinf(RWS1, 1, 1, ATYPE2_2_1),
				oinf(RWS1, 2, 1, ATYPE2_2_0),
				oinf(RWS1, 4, 2, ATYPE2_2_1),
				oinf(RWS1, 6, 2, ATYPE2_2_0),
				oinf(RWS1, 6, 1, ATYPE2_2_1),
				oinf(RWS1, 7, 1, ATYPE2_2_0),
				oinf(RWS1, 9, 2, ATYPE2_2_1)
				);
		checkSort(p, expected);

		// case 2: UPA filter a version
		p = lob.withStartFrom(RefLimit.build(1L, 1L, 1)).build();
		checkSort(p, expected.subList(1, expected.size()));

		// case 3: UPA filter some objects
		p = lob.withStartFrom(RefLimit.build(1L, 6L, 3)).build();
		checkSort(p, expected.subList(4, expected.size()));

		// case 4: UPA filter some objects and a version
		p = lob.withStartFrom(RefLimit.build(1L, 6L, 1)).build();
		checkSort(p, expected.subList(5, expected.size()));
	}

	@Test
	public void sortWithVersionsMajorVersionTypeFilterWithObjectRange() throws Exception {
		setUpTypeSort();

		final ListObjectsParameters p = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER).withShowAllVersions(true)
				.withMinObjectID(3).withMaxObjectID(8).withType(new TypeDefId(ATYPE2, 2)).build();

		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 4, 2, ATYPE2_2_1),
				oinf(RWS1, 6, 2, ATYPE2_2_0),
				oinf(RWS1, 6, 1, ATYPE2_2_1),
				oinf(RWS1, 7, 1, ATYPE2_2_0)
				);
		checkSort(p, expected);
	}

	@Test
	public void sortWithNoVersionsMajorVersionTypeFilter() throws Exception {
		setUpTypeSort();

		final Builder p = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER);

		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 1, 2, ATYPE2_2_0),
				oinf(RWS1, 4, 2, ATYPE2_2_1),
				oinf(RWS1, 6, 2, ATYPE2_2_0),
				oinf(RWS1, 9, 2, ATYPE2_2_1)
				);
		checkSort(p.withType(new TypeDefId(ATYPE2, 2)).build(), expected);

		expected = Arrays.asList(
				oinf(RWS1, 2, 2, ATYPE2_1_0),
				oinf(RWS1, 7, 2, ATYPE2_1_0),
				oinf(RWS2, 1, 1, ATYPE2_1_0)
				);
		checkSort(p.withType(new TypeDefId(ATYPE2, 1)).build(), expected);

		expected = Arrays.asList(
				oinf(RWS1, 3, 2, ATYPE2_0_1),
				oinf(RWS1, 8, 2, ATYPE2_0_1)
				);
		checkSort(p.withType(new TypeDefId(ATYPE2, 0)).build(), expected);
	}

	@Test
	public void sortWithNoVersionsMajorVersionTypeFilterWithObjectRange() throws Exception {
		setUpTypeSort();

		final ListObjectsParameters p = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER).withMinObjectID(5).withMaxObjectID(10)
				.withType(new TypeDefId(ATYPE2, 2)).build();

		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 6, 2, ATYPE2_2_0),
				oinf(RWS1, 9, 2, ATYPE2_2_1)
				);
		checkSort(p, expected);
	}

	@Test
	public void sortWithVersionsTypeNameFilter() throws Exception {
		// don't really think no version / object limit versions of this test are needed
		setUpTypeSort();

		final Builder p = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER).withShowAllVersions(true);

		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 1, 2, ATYPE2_2_0),
				oinf(RWS1, 1, 1, ATYPE2_2_1),
				oinf(RWS1, 2, 2, ATYPE2_1_0),
				oinf(RWS1, 2, 1, ATYPE2_2_0),
				oinf(RWS1, 3, 2, ATYPE2_0_1),
				oinf(RWS1, 3, 1, ATYPE2_1_0),
				oinf(RWS1, 4, 2, ATYPE2_2_1),
				oinf(RWS1, 4, 1, ATYPE2_0_1),
				oinf(RWS1, 6, 2, ATYPE2_2_0),
				oinf(RWS1, 6, 1, ATYPE2_2_1),
				oinf(RWS1, 7, 2, ATYPE2_1_0),
				oinf(RWS1, 7, 1, ATYPE2_2_0),
				oinf(RWS1, 8, 2, ATYPE2_0_1),
				oinf(RWS1, 8, 1, ATYPE2_1_0),
				oinf(RWS1, 9, 2, ATYPE2_2_1),
				oinf(RWS1, 9, 1, ATYPE2_0_1),
				oinf(RWS2, 1, 1, ATYPE2_1_0)
				);
		checkSort(p.withType(new TypeDefId(ATYPE2)).build(), expected);

		expected = Arrays.asList(oinf(RWS1, 5, 1, ATYPE_1_0));
		checkSort(p.withType(new TypeDefId(ATYPE)).build(), expected);
	}

	@Test
	public void sortWithVersionsTypeNameFilterWithStartFrom() throws Exception {
		setUpTypeSort();

		final Builder lop = ListObjectsParameters.getBuilder(Arrays.asList(WS1, WS2))
				.withUser(USER).withShowAllVersions(true).withType(new TypeDefId(ATYPE2));

		// case 1: no UPA filtering
		ListObjectsParameters p = lop.withStartFrom(RefLimit.build(1L, 1L, 3)).build();
		List<ObjectInformation> expected = Arrays.asList(
				oinf(RWS1, 1, 2, ATYPE2_2_0),
				oinf(RWS1, 1, 1, ATYPE2_2_1),
				oinf(RWS1, 2, 2, ATYPE2_1_0),
				oinf(RWS1, 2, 1, ATYPE2_2_0),
				oinf(RWS1, 3, 2, ATYPE2_0_1),
				oinf(RWS1, 3, 1, ATYPE2_1_0),
				oinf(RWS1, 4, 2, ATYPE2_2_1),
				oinf(RWS1, 4, 1, ATYPE2_0_1),
				oinf(RWS1, 6, 2, ATYPE2_2_0),
				oinf(RWS1, 6, 1, ATYPE2_2_1),
				oinf(RWS1, 7, 2, ATYPE2_1_0),
				oinf(RWS1, 7, 1, ATYPE2_2_0),
				oinf(RWS1, 8, 2, ATYPE2_0_1),
				oinf(RWS1, 8, 1, ATYPE2_1_0),
				oinf(RWS1, 9, 2, ATYPE2_2_1),
				oinf(RWS1, 9, 1, ATYPE2_0_1),
				oinf(RWS2, 1, 1, ATYPE2_1_0)
				);
		checkSort(p, expected);

		// case 2: UPA filter a version
		p = lop.withStartFrom(RefLimit.build(1L, 1L, 1)).build();
		checkSort(p, expected.subList(1, expected.size()));

		// case 3: UPA filter some objects
		p = lop.withStartFrom(RefLimit.build(1L, 4L, 2)).build();
		checkSort(p, expected.subList(6, expected.size()));
	}

}
