package us.kbase.workspace.test.workspace;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static us.kbase.common.test.TestCommon.set;

import java.time.Instant;
import java.util.Arrays;

import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder.ResourceUsageConfiguration;
import us.kbase.workspace.listener.WorkspaceEventListener;

public class WorkspaceListenerTest {
	
	public static final WorkspaceInformation WS_INFO = WorkspaceInformation.getBuilder()
			.withID(42)
			.withName("wsfoo")
			.withMaximumObjectID(300)
			.withModificationDate(Instant.now())
			.withOwner(new WorkspaceUser("userfoo"))
			.withUserPermission(Permission.OWNER)
			.build();

	@Test
	public void createWorkspace1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		final WorkspaceUserMetadata meta = new WorkspaceUserMetadata();
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.createWorkspace(new WorkspaceUser("foo"), "ws", false, null, meta))
				.thenReturn(WS_INFO);
		
		ws.createWorkspace(new WorkspaceUser("foo"), "ws", false, null, null);
		
		verify(l).createWorkspace(42L);
	}
	
	@Test
	public void createWorkspace2Listeners() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		final WorkspaceUserMetadata meta = new WorkspaceUserMetadata();
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.createWorkspace(new WorkspaceUser("foo"), "ws", false, null, meta))
				.thenReturn(WS_INFO);
		
		ws.createWorkspace(new WorkspaceUser("foo"), "ws", false, null, null);
		
		verify(l1).createWorkspace(42L);
		verify(l2).createWorkspace(42L);
	}
	
	@Test
	public void cloneWorkspace1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		final WorkspaceUserMetadata meta = new WorkspaceUserMetadata();
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.READ, Permission.NONE).build());
		when(db.cloneWorkspace(user, rwsi, "whee", false, null, meta, null)).thenReturn(WS_INFO);
		
		ws.cloneWorkspace(user, wsi, "whee", false, null, null, null);
		
		verify(l).cloneWorkspace(42L);
	}
	
	@Test
	public void cloneWorkspace2Listeners() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		final WorkspaceUserMetadata meta = new WorkspaceUserMetadata();
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.READ, Permission.NONE).build());
		when(db.cloneWorkspace(user, rwsi, "whee", false, null, meta, null)).thenReturn(WS_INFO);
		
		ws.cloneWorkspace(user, wsi, "whee", false, null, null, null);
		
		verify(l1).cloneWorkspace(42L);
		verify(l2).cloneWorkspace(42L);
	}
	
	@Test
	public void setWorkspaceMetadataNoUpdate() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		final WorkspaceUserMetadata meta = new WorkspaceUserMetadata();
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.ADMIN, Permission.NONE).build());
		
		ws.setWorkspaceMetadata(user, wsi, meta, null);
		
		verify(l, never()).setWorkspaceMetadata(24L);
	}
	
	@Test
	public void setWorkspaceMetadata1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		final WorkspaceUserMetadata meta = new WorkspaceUserMetadata(
				ImmutableMap.of("foo", "bar"));
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.ADMIN, Permission.NONE).build());
		
		ws.setWorkspaceMetadata(user, wsi, meta, null);
		
		verify(l).setWorkspaceMetadata(24L);
	}
	
	@Test
	public void setWorkspaceMetadata2Listeners() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		final WorkspaceUserMetadata meta = new WorkspaceUserMetadata(
				ImmutableMap.of("foo", "bar"));
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.ADMIN, Permission.NONE).build());
		
		ws.setWorkspaceMetadata(user, wsi, meta, null);
		
		verify(l1).setWorkspaceMetadata(24L);
		verify(l2).setWorkspaceMetadata(24L);
	}
	
	@Test
	public void setWorkspaceMetadataExceptionOnSetMeta() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		final WorkspaceUserMetadata meta = new WorkspaceUserMetadata(
				ImmutableMap.of("foo", "bar"));
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.ADMIN, Permission.NONE).build());
		
		doThrow(new WorkspaceCommunicationException("whee"))
				.when(db).setWorkspaceMeta(rwsi, meta);
		try {
			ws.setWorkspaceMetadata(user, wsi, meta, Arrays.asList("foo"));
		} catch(WorkspaceCommunicationException e) {
			//fine
		}
		
		verify(l, never()).setWorkspaceMetadata(24L);
	}
	
	@Test
	public void setWorkspaceMetadataExceptionOnFirstRemove() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		final WorkspaceUserMetadata meta = new WorkspaceUserMetadata(
				ImmutableMap.of("foo", "bar"));
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.ADMIN, Permission.NONE).build());
		
		doThrow(new WorkspaceCommunicationException("whee"))
				.when(db).removeWorkspaceMetaKey(rwsi, "foo");
		try {
			ws.setWorkspaceMetadata(user, wsi, meta, Arrays.asList("foo"));
		} catch(WorkspaceCommunicationException e) {
			//fine
		}
		
		verify(l).setWorkspaceMetadata(24L);
	}
	
	@Test
	public void setWorkspaceMetadataExceptionOnSecondRemove() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		final WorkspaceUserMetadata meta = new WorkspaceUserMetadata();
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.ADMIN, Permission.NONE).build());
		
		doThrow(new WorkspaceCommunicationException("whee"))
				.when(db).removeWorkspaceMetaKey(rwsi, "foo");
		try {
			ws.setWorkspaceMetadata(user, wsi, meta, Arrays.asList("bar", "foo"));
		} catch(WorkspaceCommunicationException e) {
			//fine
		}
		
		verify(l).setWorkspaceMetadata(24L);
	}
	
	@Test
	public void lockWorkspace1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.ADMIN, Permission.NONE).build());
		
		ws.lockWorkspace(user, wsi);
		
		verify(l).lockWorkspace(24L);
	}
	
	@Test
	public void lockWorkspace2Listeners() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.ADMIN, Permission.NONE).build());
		
		ws.lockWorkspace(user, wsi);
		
		verify(l1).lockWorkspace(24L);
		verify(l2).lockWorkspace(24L);
	}
	
	@Test
	public void renameWorkspace1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.OWNER, Permission.NONE).build());
		
		ws.renameWorkspace(user, wsi, "foobar");
		
		verify(l).renameWorkspace(24L, "foobar");
	}
	
	@Test
	public void renameWorkspace2Listeners() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1,l2));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.OWNER, Permission.NONE).build());
		
		ws.renameWorkspace(user, wsi, "foobar");
		
		verify(l1).renameWorkspace(24L, "foobar");
		verify(l2).renameWorkspace(24L, "foobar");
	}
	
	@Test
	public void setGlobalPermission1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspace(wsi)).thenReturn(rwsi);
		when(db.getPermission(user, rwsi)).thenReturn(Permission.ADMIN);
		
		ws.setGlobalPermission(user, wsi, Permission.READ);
		
		verify(l).setGlobalPermission(24L, Permission.READ);
	}
	
	@Test
	public void setGlobalPermission2Listeners() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.resolveWorkspace(wsi)).thenReturn(rwsi);
		when(db.getPermission(user, rwsi)).thenReturn(Permission.ADMIN);
		
		ws.setGlobalPermission(user, wsi, Permission.READ);
		
		verify(l1).setGlobalPermission(24L, Permission.READ);
		verify(l2).setGlobalPermission(24L, Permission.READ);
	}
	
	@Test
	public void setPermissions1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspace(wsi)).thenReturn(rwsi);
		when(db.getPermissions(user, rwsi)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.ADMIN, Permission.NONE).build());
		
		ws.setPermissions(user, wsi, Arrays.asList(new WorkspaceUser("foobar")), Permission.WRITE);
		
		verify(l).setPermissions(24, Permission.WRITE, Arrays.asList(new WorkspaceUser("foobar")));
	}
	
	@Test
	public void setPermissions2Listeners() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.resolveWorkspace(wsi)).thenReturn(rwsi);
		when(db.getPermissions(user, rwsi)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.ADMIN, Permission.NONE).build());
		
		ws.setPermissions(user, wsi, Arrays.asList(new WorkspaceUser("foobar")), Permission.WRITE);
		
		verify(l1).setPermissions(24, Permission.WRITE,
				Arrays.asList(new WorkspaceUser("foobar")));
		verify(l2).setPermissions(24, Permission.WRITE,
				Arrays.asList(new WorkspaceUser("foobar")));
	}
	
	@Test
	public void setWorkspaceDescription1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.OWNER, Permission.NONE).build());
		
		ws.setWorkspaceDescription(user, wsi, "foo");
		
		verify(l).setWorkspaceDescription(24L);
	}
	
	@Test
	public void setWorkspaceDescription2Listeners() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.OWNER, Permission.NONE).build());
		
		ws.setWorkspaceDescription(user, wsi, "foo");
		
		verify(l1).setWorkspaceDescription(24L);
		verify(l2).setWorkspaceDescription(24L);
	}
	
	@Test
	public void setWorkspaceOwner1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceUser newUser = new WorkspaceUser("bar");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "foobar", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.OWNER, Permission.NONE).build());
		when(db.getPermission(newUser, rwsi)).thenReturn(Permission.ADMIN);
		
		ws.setWorkspaceOwner(user, wsi, newUser, Optional.absent(), false);

		verify(l).setWorkspaceOwner(24L, newUser, Optional.absent());
	}
	
	@Test
	public void setWorkspaceOwner2Listeners() throws Exception {
		// also tests that a changed name is propagated
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceUser newUser = new WorkspaceUser("bar");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "foo:foobar", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.OWNER, Permission.NONE).build());
		when(db.getPermission(newUser, rwsi)).thenReturn(Permission.ADMIN);
		
		ws.setWorkspaceOwner(user, wsi, newUser, Optional.absent(), false);

		verify(l1).setWorkspaceOwner(24L, newUser, Optional.of("bar:foobar"));
		verify(l2).setWorkspaceOwner(24L, newUser, Optional.of("bar:foobar"));
	}
	
	@Test
	public void setWorkspaceDeleted1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspace(wsi, false)).thenReturn(rwsi);

		ws.setWorkspaceDeleted(user, wsi, true, true);

		verify(l).setWorkspaceDeleted(24, true);
	}
	
	@Test
	public void setWorkspaceDeleted2Listeners() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.resolveWorkspace(wsi, true)).thenReturn(rwsi);

		ws.setWorkspaceDeleted(user, wsi, false, true);

		verify(l1).setWorkspaceDeleted(24, false);
		verify(l2).setWorkspaceDeleted(24, false);
	}
}

