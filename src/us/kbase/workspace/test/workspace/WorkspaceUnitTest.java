package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.now;
import static us.kbase.common.test.TestCommon.list;
import static us.kbase.common.test.TestCommon.set;
import static us.kbase.workspace.test.WorkspaceTestCommon.basicProv;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.common.collect.ImmutableMap;

import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.NoObjectDataException;
import us.kbase.workspace.database.provenance.Provenance;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.UObject;
import us.kbase.common.test.TestCommon;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypeProvider.TypeFetchException;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.core.ValidatedTypedObject;
import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.typedobj.exceptions.TypedObjectValidationException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.TooManyIdsException;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactoryBuilder;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.DynamicConfig;
import us.kbase.workspace.database.DynamicConfig.DynamicConfigUpdate;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder.ResourceUsageConfiguration;

public class WorkspaceUnitTest {

	// these are unit tests only, as opposed to the integration tests in WorkspaceTest.
	// eventually most of the integration tests should be moved to unit tests, and the 
	// remaining tests simplified.
	
	private static final String UNI;
	static {
		final StringBuilder b = new StringBuilder();
		b.append("a");
		for (int i = 0; i < 1005; i++) {
			b.append("𐍆");
		}
		UNI = b.toString();
		assertThat(UNI.codePointCount(0, UNI.length()), is(1006));
		assertThat(UNI.length(), is(2011));
	}
	
	private TestMocks initMocks() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator val = mock(TypedObjectValidator.class);
		final TempFilesManager tfm = mock(TempFilesManager.class);
		
		final ResourceUsageConfiguration b = new ResourceUsageConfigurationBuilder().build();
		final Workspace ws = new Workspace(db, b, val, tfm);
		
		return new TestMocks(db, val, b, ws, tfm);
	}
	
	private class TestMocks {
		private final WorkspaceDatabase db;
		private final TypedObjectValidator val;
		@SuppressWarnings("unused")
		private final ResourceUsageConfiguration cfg;
		private final Workspace ws;
		@SuppressWarnings("unused")
		private TempFilesManager tfm;
		
		private TestMocks(
				final WorkspaceDatabase db,
				final TypedObjectValidator val,
				final ResourceUsageConfiguration cfg,
				final Workspace ws,
				final TempFilesManager tfm) {
			this.db = db;
			this.val = val;
			this.cfg = cfg;
			this.ws = ws;
			this.tfm = tfm;
		}
	}
	
	@Test
	public void setConfigOnStartup() throws Exception {
		final TestMocks mocks = initMocks();
		final DynamicConfigUpdate d = DynamicConfigUpdate.getBuilder()
				.withBackendScaling(1).build();
		// will have been called in initMocks
		verify(mocks.db).setConfig(d, false);
	}
	
	@Test
	public void setConfig() throws Exception {
		final TestMocks mocks = initMocks();
		mocks.ws.setConfig(DynamicConfigUpdate.getBuilder().withBackendScaling(21).build());
		verify(mocks.db).setConfig(
				DynamicConfigUpdate.getBuilder().withBackendScaling(21).build(), true);
	}
	
	@Test
	public void getConfig() throws Exception {
		final TestMocks mocks = initMocks();
		when(mocks.db.getConfig()).thenReturn(
				DynamicConfig.getBuilder().withBackendScaling(6).build());
		assertThat("incorrect config", mocks.ws.getConfig(), is(
				DynamicConfig.getBuilder().withBackendScaling(6).build()
				));
	}
	
	@Test
	public void setConfigFail() throws Exception {
		try {
			initMocks().ws.setConfig(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("config"));
		}
	}
	
	@Test
	public void setWorkspaceMetadataFailNullMeta() throws Exception {
		try {
			initMocks().ws.setWorkspaceMetadata(
					new WorkspaceUser("u"), new WorkspaceIdentifier(1), null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("meta"));
		}
	}
	
	@Test
	public void setWorkspaceDescriptionNull() throws Exception {
		setWorkspaceDescription(null, null);
	}
	
	@Test
	public void setWorkspaceDescription() throws Exception {
		final String input = UNI.substring(0, 1997) + "a";
		assertThat(input.codePointCount(0, input.length()), is(1000));
		assertThat(input.length(), is(1998));
		final String expected = UNI.substring(0, 1997) + "a";
		setWorkspaceDescription(input, expected);
	}
	
	@Test
	public void setWorkspaceDescriptionPruneDescription() throws Exception {
		final String input = UNI;
		final String expected = UNI.substring(0, 1999);
		setWorkspaceDescription(input, expected);
	}

	private void setWorkspaceDescription(final String input, final String expected)
			throws Exception {
		final TestMocks mocks = initMocks();
		
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ws", false, false);
		when(mocks.db.resolveWorkspaces(set(new WorkspaceIdentifier("ws")))).thenReturn(
				ImmutableMap.of(new WorkspaceIdentifier("ws"), rwsi));
		
		when(mocks.db.getPermissions(new WorkspaceUser("foo"), set(rwsi))).thenReturn(
				PermissionSet.getBuilder(new WorkspaceUser("foo"), new AllUsers('*'))
						.withWorkspace(rwsi, Permission.ADMIN, Permission.NONE)
						.build());
		
		final long id = mocks.ws.setWorkspaceDescription(
				new WorkspaceUser("foo"), new WorkspaceIdentifier("ws"), input, false);
		
		verify(mocks.db).setWorkspaceDescription(rwsi, expected);
		
		assertThat("incorrect id", id, is(24L));
	}
	
	@Test
	public void setWorkspaceDescriptionAsAdmin() throws Exception {
		final TestMocks mocks = initMocks();
		
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ws", false, false);
		when(mocks.db.resolveWorkspaces(set(new WorkspaceIdentifier("ws")))).thenReturn(
				ImmutableMap.of(new WorkspaceIdentifier("ws"), rwsi));
		
		final long id = mocks.ws.setWorkspaceDescription(
				null, new WorkspaceIdentifier("ws"), "foo", true);
		
		verify(mocks.db).setWorkspaceDescription(rwsi, "foo");
		
		assertThat("incorrect id", id, is(24L));
	}
	
	@Test
	public void getDescriptionAsAdmin() throws Exception {
		final TestMocks mocks = initMocks();
		
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ws", false, false);
		when(mocks.db.resolveWorkspaces(set(new WorkspaceIdentifier("ws")))).thenReturn(
				ImmutableMap.of(new WorkspaceIdentifier("ws"), rwsi));
		
		when(mocks.db.getWorkspaceDescription(rwsi)).thenReturn("my desc");
		
		assertThat("incorrect desc", mocks.ws.getWorkspaceDescription(
				null, new WorkspaceIdentifier("ws"), true),
				is("my desc"));
	}
	
	@Test
	public void setWorkspaceDescriptionFailNulls() throws Exception {
		setWorkspaceDescriptionFail(initMocks().ws, null, false, new NullPointerException("wsi"));
	}
	
	@Test
	public void setWorkspaceDescriptionAsAdminFailLocked() throws Exception {
		// lock w/o admin is tested in the integration tests.
		final TestMocks mocks = initMocks();
		
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ws", true, false);
		when(mocks.db.resolveWorkspaces(set(new WorkspaceIdentifier("ws")))).thenReturn(
				ImmutableMap.of(new WorkspaceIdentifier("ws"), rwsi));
		
		setWorkspaceDescriptionFail(mocks.ws, new WorkspaceIdentifier("ws"), true,
				new WorkspaceAuthorizationException(
						"The workspace with id 24, name ws, is locked and may not be modified"));
	}
	
	private void setWorkspaceDescriptionFail(
			final Workspace ws,
			final WorkspaceIdentifier wsi,
			final boolean asAdmin,
			final Exception expected)
			throws Exception {
		// TODO TEST there are other ways to fail, but they're already tested in the int tests
		try {
			ws.setWorkspaceDescription(null, wsi, null, asAdmin);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	
	@Test
	public void createWorkspaceWithNullDescription() throws Exception {
		createWorkspaceWithDescription(null, null);
	}
	
	@Test
	public void createWorkspaceWithDescription() throws Exception {
		final String input = UNI.substring(0, 1997) + "a";
		assertThat(input.codePointCount(0, input.length()), is(1000));
		assertThat(input.length(), is(1998));
		final String expected = UNI.substring(0, 1997) + "a";
		setWorkspaceDescription(input, expected);
	}
	
	@Test
	public void createWorkspaceWithDescriptionPruneDescription() throws Exception {
		final String input = UNI;
		final String expected = UNI.substring(0, 1999);
		createWorkspaceWithDescription(input, expected);
	}

	private void createWorkspaceWithDescription(final String input, final String expected)
			throws Exception {
		final TestMocks mocks = initMocks();
		
		final WorkspaceInformation wsinfo = WorkspaceInformation.getBuilder()
				.withID(45).withName("wsname").withOwner(new WorkspaceUser("foo"))
				.withMaximumObjectID(0).withModificationDate(Instant.ofEpochMilli(1))
				.withUserPermission(Permission.OWNER).build();
		
		when(mocks.db.createWorkspace(
				new WorkspaceUser("foo"), "wsname", false, expected, new WorkspaceUserMetadata()))
				.thenReturn(wsinfo);
		
		final WorkspaceInformation wsinforet = mocks.ws.createWorkspace(
				new WorkspaceUser("foo"), "wsname", false, input, new WorkspaceUserMetadata());
		
		assertThat("incorrect wsinfo", wsinforet, is(wsinfo));
	}
	
	@Test
	public void cloneWorkspaceWithNulls() throws Exception {
		cloneWorkspaceWithDescription(null, null);
	}
	
	@Test
	public void cloneWorkspaceWithDescription() throws Exception {
		final String input = UNI.substring(0, 1997) + "a";
		assertThat(input.codePointCount(0, input.length()), is(1000));
		assertThat(input.length(), is(1998));
		final String expected = UNI.substring(0, 1997) + "a";
		cloneWorkspaceWithDescription(input, expected);
	}

	@Test
	public void cloneWorkspaceWithDescriptionPruneDescription() throws Exception {
		final String input = UNI;
		final String expected = UNI.substring(0, 1999);
		cloneWorkspaceWithDescription(input, expected);
	}

	private void cloneWorkspaceWithDescription(final String input, final String expected)
			throws Exception {
		
		final TestMocks mocks = initMocks();
		
		final WorkspaceInformation wsinfo = WorkspaceInformation.getBuilder()
				.withID(45).withName("wsname").withOwner(new WorkspaceUser("foo"))
				.withMaximumObjectID(0).withModificationDate(Instant.ofEpochMilli(1))
				.withUserPermission(Permission.OWNER).build();
		
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(43, "old", false, false);
		when(mocks.db.resolveWorkspaces(set(new WorkspaceIdentifier("old")))).thenReturn(
				ImmutableMap.of(new WorkspaceIdentifier("old"), rwsi));
		
		when(mocks.db.getPermissions(new WorkspaceUser("foo"), set(rwsi))).thenReturn(
				PermissionSet.getBuilder(new WorkspaceUser("foo"), new AllUsers('*'))
						.withWorkspace(rwsi, Permission.NONE, Permission.READ)
						.build());
		
		when(mocks.db.cloneWorkspace(
				new WorkspaceUser("foo"), rwsi, "new", false, expected,
				new WorkspaceUserMetadata(), set()))
				.thenReturn(wsinfo);
		
		final WorkspaceInformation wsinforet = mocks.ws.cloneWorkspace(
				new WorkspaceUser("foo"), new WorkspaceIdentifier("old"),
				"new", false, input, new WorkspaceUserMetadata(), set());
		
		assertThat("incorrect wsinfo", wsinforet, is(wsinfo));
	}
	
	@Test
	public void getObjectsFailMissingData() throws Exception {
		/* tests the case where there's an object in the database but no corresponding data
		 * in the blobstore. Should never happen in practice.
		 */
		
		final TestMocks mocks = initMocks();

		final Date now = new Date();
		final WorkspaceUser u = new WorkspaceUser("u1");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(1);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(1, "foo", false, false);
		final Provenance p = basicProv(u);
		final List<ObjectIdentifier> objs = list(
				ObjectIdentifier.getBuilder(wsi).withID(1L).build());
		final Set<ObjectIDResolvedWS> robjs = set(new ObjectIDResolvedWS(rwsi, 1));
		final Map<ObjectIDResolvedWS, WorkspaceObjectData.Builder> data = new HashMap<>();
		data.put(
				new ObjectIDResolvedWS(rwsi, 1),
				WorkspaceObjectData.getBuilder(
						ObjectInformation.getBuilder()
								.withObjectID(1)
								.withObjectName("foo")
								.withType(new AbsoluteTypeDefId(new TypeDefName("Foo.Bar"), 2, 1))
								.withSavedDate(now)
								.withVersion(1)
								.withSavedBy(u)
								.withWorkspace(rwsi)
								.withChecksum("chcksm")
								.withSize(12)
								.build(),
						p)
				);
		when(mocks.db.resolveWorkspaces(set(wsi), false)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(mocks.db.getPermissions(null, set(rwsi))).thenReturn(PermissionSet.getBuilder(
				null, Workspace.ALL_USERS)
				.withWorkspace(rwsi, Permission.NONE, Permission.READ)
				.build());
		when(mocks.db.getObjects(robjs, true, false, true)).thenReturn(data);
		when(mocks.db.getConfig()).thenReturn(
				DynamicConfig.getBuilder().withBackendScaling(3).build());
		// arguments are a list of WOD builders and ByteArrayFileCacheManager, both of
		// which are created in the method and are only equal based on identity
		doThrow(new NoObjectDataException("oopsie"))
				.when(mocks.db).addDataToObjects(any(), any(), eq(3));
		
		failGetObjects(mocks.ws, objs, false, new CorruptWorkspaceDBException("oopsie"));
	}
	
	@Test
	public void getObjectsBackendScaling() throws Exception {
		// Tests that the backend scaling parameter is passed correctly to the object data method.
		getObjectsBackendScaling(1);
		getObjectsBackendScaling(3);
		getObjectsBackendScaling(5);
		getObjectsBackendScaling(1000);
	}
	
	private void getObjectsBackendScaling(final int scaling) throws Exception {
		final TestMocks mocks = initMocks();

		final Date now = new Date();
		final WorkspaceUser u = new WorkspaceUser("u1");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(1);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(1, "foo", false, false);
		final Provenance p = basicProv(u);
		final List<ObjectIdentifier> objs = list(
				ObjectIdentifier.getBuilder(wsi).withID(1L).build());
		final Set<ObjectIDResolvedWS> robjs = set(new ObjectIDResolvedWS(rwsi, 1));
		final Map<ObjectIDResolvedWS, WorkspaceObjectData.Builder> data = new HashMap<>();
		data.put(
				new ObjectIDResolvedWS(rwsi, 1),
				WorkspaceObjectData.getBuilder(
						ObjectInformation.getBuilder()
							.withObjectID(1)
							.withObjectName("foo")
							.withType(new AbsoluteTypeDefId(new TypeDefName("Foo.Bar"), 2, 1))
							.withSavedDate(now)
							.withVersion(1)
							.withSavedBy(u)
							.withWorkspace(rwsi)
							.withChecksum("chcksm")
							.withSize(12)
							.build(),
						p)
				);
		when(mocks.db.resolveWorkspaces(set(wsi), false)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(mocks.db.getPermissions(null, set(rwsi))).thenReturn(PermissionSet.getBuilder(
				null, Workspace.ALL_USERS)
				.withWorkspace(rwsi, Permission.NONE, Permission.READ)
				.build());
		when(mocks.db.getObjects(robjs, true, false, true)).thenReturn(data);
		when(mocks.db.getConfig()).thenReturn(
				DynamicConfig.getBuilder().withBackendScaling(scaling).build());
		
		final List<WorkspaceObjectData> got = mocks.ws.getObjects(null, objs, false, false, false);
		assertThat("incorrect count", got.size(), is(1));
		final WorkspaceObjectData wod = got.get(0);
		// no overridden equals method for WOD as expected
		assertThat("incorrect info", wod.getObjectInfo(), is(ObjectInformation.getBuilder()
				.withObjectID(1)
				.withObjectName("foo")
				.withType(new AbsoluteTypeDefId(new TypeDefName("Foo.Bar"), 2, 1))
				.withSavedDate(now)
				.withVersion(1)
				.withSavedBy(u)
				.withWorkspace(rwsi)
				.withChecksum("chcksm")
				.withSize(12)
				.build()));
		assertThat("incorrect prov", wod.getProvenance(), is(p));
		assertThat("incorrect data", wod.getSerializedData(), is(Optional.empty()));
		assertThat("incorrect copy ref", wod.getCopyReference(), is(Optional.empty()));
		assertThat("incorrect ext ids", wod.getExtractedIds(), is(Collections.emptyMap()));
		assertThat("incorrect refs", wod.getReferences(), is(Collections.emptyList()));
		assertThat("incorrect has data", wod.hasData(), is(false));
		assertThat("incorrect copy inaccessible", wod.isCopySourceInaccessible(), is(false));
		assertThat("incorrect subset", wod.getSubsetSelection(), is(SubsetSelection.EMPTY));
		
		// WOD & builder have no equals, so any() it is for this test
		verify(mocks.db)
				.addDataToObjects(any(), any(ByteArrayFileCacheManager.class), eq(scaling));
	}
	
	@Test
	public void getObjects10K() throws Exception {
		final TestMocks mocks = initMocks();
		
		final Date now = new Date();
		final WorkspaceUser u = new WorkspaceUser("u1");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(1);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(1, "foo", false, false);
		final Provenance p = basicProv(u);
		final ObjectIdentifier.Builder oi = ObjectIdentifier.getBuilder(wsi).withID(1L);
		final List<ObjectIdentifier> objs = LongStream.range(1, 10001)
				.mapToObj(i -> oi.withID(i).build()).collect(Collectors.toList());
		final Set<ObjectIDResolvedWS> robjs = LongStream.range(1, 10001)
				.mapToObj(i -> new ObjectIDResolvedWS(rwsi, i)).collect(Collectors.toSet());
		final Map<ObjectIDResolvedWS, WorkspaceObjectData.Builder> data = LongStream
				.range(1, 10001)
				.mapToObj(i -> i)
				.collect(Collectors.toMap(
						i -> new ObjectIDResolvedWS(rwsi, i),
						i -> WorkspaceObjectData.getBuilder(
									ObjectInformation.getBuilder()
									.withObjectID(i)
									.withObjectName("foo" + i)
									.withType(new AbsoluteTypeDefId(
											new TypeDefName("Foo.Bar"), 2, 1))
									.withSavedDate(now)
									.withVersion(1)
									.withSavedBy(u)
									.withWorkspace(rwsi)
									.withChecksum("chcksm")
									.withSize(12)
									.build(),
								p)));
		
		when(mocks.db.resolveWorkspaces(set(wsi), false)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(mocks.db.getPermissions(null, set(rwsi))).thenReturn(PermissionSet.getBuilder(
				null, Workspace.ALL_USERS)
				.withWorkspace(rwsi, Permission.NONE, Permission.READ)
				.build());
		when(mocks.db.getObjects(robjs, true, false, true)).thenReturn(data);
		
		final List<WorkspaceObjectData> got = mocks.ws.getObjects(null, objs, true, false, false);
		for (int i = 0; i < 1000; i++) {
			// no overridden equals method for WOD as expected
			final WorkspaceObjectData wod = got.get(i);
			assertThat("incorrect info", wod.getObjectInfo(), is(
					ObjectInformation.getBuilder()
						.withObjectID(i + 1)
						.withObjectName("foo" + (i + 1))
						.withType(new AbsoluteTypeDefId(new TypeDefName("Foo.Bar"), 2, 1))
						.withSavedDate(now)
						.withVersion(1)
						.withSavedBy(u)
						.withWorkspace(rwsi)
						.withChecksum("chcksm")
						.withSize(12)
						.build()));
			assertThat("incorrect prov", wod.getProvenance(), is(p));
			assertThat("incorrect data", wod.getSerializedData(), is(Optional.empty()));
			assertThat("incorrect copy ref", wod.getCopyReference(), is(Optional.empty()));
			assertThat("incorrect ext ids", wod.getExtractedIds(), is(Collections.emptyMap()));
			assertThat("incorrect refs", wod.getReferences(), is(Collections.emptyList()));
			assertThat("incorrect has data", wod.hasData(), is(false));
			assertThat("incorrect copy inaccessible", wod.isCopySourceInaccessible(), is(false));
			assertThat("incorrect subset", wod.getSubsetSelection(), is(SubsetSelection.EMPTY));
		}
	}
	
	@Test
	public void getObjectsFailBadArgs() throws Exception {
		final TestMocks mocks = initMocks();
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(1);
		final ObjectIdentifier.Builder oi = ObjectIdentifier.getBuilder(wsi).withID(1L);
		
		failGetObjects(mocks.ws, null, false, new NullPointerException("objs"));
		failGetObjects(mocks.ws, list(oi.build(), null), false, new NullPointerException(
				"object list cannot contain nulls"));
		final List<ObjectIdentifier> objs = LongStream.range(1, 10002)
				.mapToObj(i -> oi.withID(i).build()).collect(Collectors.toList());
		failGetObjects(mocks.ws, objs, false, new IllegalArgumentException(
				"At most 10000 objects can be requested at once"));
	}
	
	private void failGetObjects(
			final Workspace ws,
			final List<ObjectIdentifier> objs,
			final boolean noData,
			final Exception expected) {
		try {
			ws.getObjects(null, objs, noData, false, false);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	private final String SAVE_OBJECT_FAIL_VAL_TYPE_STRING = "Mod.Type-3.2";
	private final int SAVE_OBJECT_FAIL_VAL_MAX_IDS = 602214;
	private final UObject SAVE_OBJECT_FAIL_GOOD_DATA = new UObject(ImmutableMap.of("a", "b"));
	private final UObject SAVE_OBJECT_FAIL_BAD_DATA = new UObject(ImmutableMap.of("c", "d"));
	private final WorkspaceSaveObject SAVE_OBJECT_FAIL_MEDIOCRE_OBJECT = new WorkspaceSaveObject(
			new ObjectIDNoWSNoVer(67),
			SAVE_OBJECT_FAIL_GOOD_DATA,
			TypeDefId.fromTypeString(SAVE_OBJECT_FAIL_VAL_TYPE_STRING),
			new WorkspaceUserMetadata(),
			Provenance.getBuilder(new WorkspaceUser("u"), now()).build(),
			false);
	
	private WorkspaceSaveObject saveObjectValidationFailGetBadObject(final ObjectIDNoWSNoVer id) {
		return new WorkspaceSaveObject(
				id,
				SAVE_OBJECT_FAIL_BAD_DATA,
				TypeDefId.fromTypeString(SAVE_OBJECT_FAIL_VAL_TYPE_STRING),
				new WorkspaceUserMetadata(),
				Provenance.getBuilder(new WorkspaceUser("u"), now()).build(),
				false);
	}
	
	@Test
	public void saveObjectValidationFailNoSuchModuleException() throws Exception {
		failSaveObjectValidationException(
				list(saveObjectValidationFailGetBadObject(new ObjectIDNoWSNoVer(42))),
				new NoSuchModuleException("no mod"),
				new TypedObjectValidationException(
						"Object #1, 42 failed type checking: no mod"));
	}
	
	@Test
	public void saveObjectValidationFailNoSuchTypeException() throws Exception {
		final WorkspaceSaveObject obj = SAVE_OBJECT_FAIL_MEDIOCRE_OBJECT;
		
		final WorkspaceSaveObject badobject = saveObjectValidationFailGetBadObject(
				new ObjectIDNoWSNoVer("my_name"));
		failSaveObjectValidationException(
				list(obj, badobject),
				new NoSuchTypeException("no type"),
				new TypedObjectValidationException(
						"Object #2, my_name failed type checking: no type"));
	}
	
	@Test
	public void saveObjectValidationFailTooManyIDsException() throws Exception {
		final WorkspaceSaveObject obj = SAVE_OBJECT_FAIL_MEDIOCRE_OBJECT;
		
		final WorkspaceSaveObject badobject = saveObjectValidationFailGetBadObject(
				new ObjectIDNoWSNoVer(893));
		failSaveObjectValidationException(
				list(obj, obj, badobject, obj),
				new TooManyIdsException("OMG I totally can't handle all these eyedeeeeees"),
				new TypedObjectValidationException(
						"Object #3, 893 failed type checking - the number of unique IDs in the "
						+ "saved objects exceeds the maximum allowed, "
						+ SAVE_OBJECT_FAIL_VAL_MAX_IDS));
	}
	
	@Test
	public void saveObjectValidationFailJsonParseException() throws Exception {
		final WorkspaceSaveObject obj = SAVE_OBJECT_FAIL_MEDIOCRE_OBJECT;
		
		final WorkspaceSaveObject badobject = saveObjectValidationFailGetBadObject(
				new ObjectIDNoWSNoVer("my_other_name"));
		failSaveObjectValidationException(
				list(obj, obj, obj, badobject, obj, obj),
				new JsonParseException(null, "parse parse parse crap"),
				new TypedObjectValidationException(
						"Object #4, my_other_name failed type checking - a fatal JSON "
						+ "processing error occurred: parse parse parse crap"));
	}
	
	@Test
	public void saveObjectValidationFailIOException() throws Exception {
		final WorkspaceSaveObject obj = SAVE_OBJECT_FAIL_MEDIOCRE_OBJECT;
		
		final WorkspaceSaveObject badobject = saveObjectValidationFailGetBadObject(
				new ObjectIDNoWSNoVer(2));
		failSaveObjectValidationException(
				list(badobject, obj, obj),
				new IOException("The tubes are down again, someone unclog them"),
				new TypedObjectValidationException(
						"Object #1, 2 failed type checking - a fatal IO "
						+ "error occurred: The tubes are down again, someone unclog them"));
	}
	
	@Test
	public void saveObjectValidationFailTypeFetchException() throws Exception {
		final WorkspaceSaveObject obj = SAVE_OBJECT_FAIL_MEDIOCRE_OBJECT;
		
		final WorkspaceSaveObject badobject = saveObjectValidationFailGetBadObject(
				new ObjectIDNoWSNoVer("fetching"));
		failSaveObjectValidationException(
				list(obj, badobject, obj, obj),
				new TypeFetchException("the types won't come when you call them, wtf"),
				new TypedObjectValidationException(
						"Object #2, fetching failed type checking - a fatal error occurred "
						+ "attempting to fetch the type specification: the types won't come "
						+ "when you call them, wtf"));
	}

	public void failSaveObjectValidationException(
			final List<WorkspaceSaveObject> objects,
			final Exception thown,
			final Exception expected)
			throws Exception {
		final TestMocks mocks = initMocks();
		
		final WorkspaceUser user = new WorkspaceUser("u");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(6);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(6, "foo", false, false);
		final ValidatedTypedObject vto = mock(ValidatedTypedObject.class);

		when(mocks.db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(mocks.db.getPermissions(user, set(rwsi))).thenReturn(PermissionSet
				.getBuilder(user, Workspace.ALL_USERS)
				.withWorkspace(rwsi, Permission.WRITE, Permission.NONE)
				.build());
		
		when(mocks.val.validate(
				// We use identity equality on the data for mock matching since UObject doesn't
				// have an equals method (which it shouldn't)
				eq(SAVE_OBJECT_FAIL_GOOD_DATA),
				eq(TypeDefId.fromTypeString(SAVE_OBJECT_FAIL_VAL_TYPE_STRING)),
				// Don't worry about checking the ID handler is correct, that's not the point of
				// this test
				any()))
				.thenReturn(vto);
		when(vto.isInstanceValid()).thenReturn(true);
		
		when(mocks.val.validate(
				eq(SAVE_OBJECT_FAIL_BAD_DATA),
				eq(TypeDefId.fromTypeString(SAVE_OBJECT_FAIL_VAL_TYPE_STRING)),
				any()))
				.thenThrow(thown);
		
		final Exception got = failSaveObjects(
				mocks.ws,
				user,
				wsi,
				objects,
				IdReferenceHandlerSetFactoryBuilder.getBuilder(SAVE_OBJECT_FAIL_VAL_MAX_IDS)
						.build().getFactory(new AuthToken("t", "u")),
				expected);
		TestCommon.assertExceptionCorrect(got.getCause(), thown);
	}
	
	private Exception failSaveObjects(
			final Workspace ws,
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final List<WorkspaceSaveObject> objects,
			final IdReferenceHandlerSetFactory idHandlerFac,
			final Exception expected) {
		try {
			ws.saveObjects(user, wsi, objects, idHandlerFac);
			fail("expected exception");
			return null;
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
			return got;
		}
	}
}
