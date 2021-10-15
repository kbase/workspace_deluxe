package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
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

import com.mongodb.DB;
import com.mongodb.MongoClient;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.typedobj.core.LocalTypeProvider;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactoryBuilder;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.database.ListObjectsParameters;
import us.kbase.workspace.database.ListObjectsParameters.Builder;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
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
	
	private static final String WS_DB = WorkspaceIntegrationWithGridFSTest.class.getSimpleName();
	private static final String TYPE_DB = WS_DB + "_types";
	
	private static MongoController MONGO;
	private static TempFilesManager TFM;
	private static Workspace WORK;
	
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
		
		final DB wsdb = new MongoClient("localhost:" + MONGO.getServerPort()).getDB(WS_DB);
		WorkspaceTestCommon.destroyWSandTypeDBs(wsdb, TYPE_DB);
		TFM = new TempFilesManager(new File(TestCommon.getTempDir()));
		TFM.cleanup();
		
		final TypeDefinitionDB typeDB = new TypeDefinitionDB(
				new MongoTypeStorage(wsdb.getSisterDB(TYPE_DB)));
		final TypedObjectValidator val = new TypedObjectValidator(new LocalTypeProvider(typeDB));
		WORK = new Workspace(
				new MongoWorkspaceDB(wsdb, new GridFSBlobStore(wsdb), TFM),
				new ResourceUsageConfigurationBuilder().build(),
				val);
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
		try (final MongoClient mongoClient = new MongoClient(
				"localhost:" + MONGO.getServerPort())) {
			TestCommon.destroyDB(mongoClient.getDatabase(WS_DB));
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
		return new ObjectInformation(
				id, "o" + id, type.getTypeString(), SOME_DATE, version, USER, wsi,
				SAFE_DATA_MD5, SAFE_DATA_SIZE, null);
	}
	
	private List<ObjectInformation> cleanDates(final List<ObjectInformation> toBeCleaned) {
		return toBeCleaned.stream().map(o -> new ObjectInformation(
				o.getObjectId(),
				o.getObjectName(),
				o.getTypeString(),
				SOME_DATE,
				o.getVersion(),
				o.getSavedBy(),
				new ResolvedWorkspaceID(o.getWorkspaceId(), o.getWorkspaceName(), false, false),
				o.getCheckSum(),
				o.getSize(),
				o.getUserMetaData()))
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
		final Provenance p = new Provenance(USER);
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
	public void sortWithVersionsNoTypeFilterAndNoObjectRange() throws Exception {
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
	public void sortWithVersionsNoTypeFilterWithObjectRange() throws Exception {
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
	public void sortWithNoVersionsNoTypeFilterAndNoObjectRange() throws Exception {
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
	public void sortWithNoVersionsNoTypeFilterWithObjectRange() throws Exception {
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
	public void sortWithVersionsFullTypeFilterAndNoObjectRange() throws Exception {
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
	public void sortWithoutVersionsFullTypeFilterAndNoObjectRange() throws Exception {
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
	public void sortWithVersionsMajorVersionTypeFilterAndNoObjectRange() throws Exception {
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
	public void sortWithNoVersionsMajorVersionTypeFilterAndNoObjectRange() throws Exception {
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
	public void sortWithVersionsTypeNameFilterAndNoObjectRange() throws Exception {
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

}
