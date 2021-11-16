package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.set;

import java.time.Instant;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;
import us.kbase.common.test.TestCommon;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.workspace.database.AllUsers;
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
	
	private TestMocks initMocks() {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator val = mock(TypedObjectValidator.class);
		final TempFilesManager tfm = mock(TempFilesManager.class);
		
		final ResourceUsageConfiguration b = new ResourceUsageConfigurationBuilder().build();
		final Workspace ws = new Workspace(db, b, val, tfm);
		
		return new TestMocks(db, val, b, ws, tfm);
	}
	
	private class TestMocks {
		private final WorkspaceDatabase db;
		@SuppressWarnings("unused")
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
		when(mocks.db.resolveWorkspace(new WorkspaceIdentifier("ws"))).thenReturn(rwsi);
		
		final long id = mocks.ws.setWorkspaceDescription(
				null, new WorkspaceIdentifier("ws"), "foo", true);
		
		verify(mocks.db).setWorkspaceDescription(rwsi, "foo");
		
		assertThat("incorrect id", id, is(24L));
	}
	
	@Test
	public void getDescriptionAsAdmin() throws Exception {
		final TestMocks mocks = initMocks();
		
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ws", false, false);
		when(mocks.db.resolveWorkspace(new WorkspaceIdentifier("ws"))).thenReturn(rwsi);
		
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
		when(mocks.db.resolveWorkspace(new WorkspaceIdentifier("ws"))).thenReturn(rwsi);
		
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
}
