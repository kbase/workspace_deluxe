package us.kbase.workspace.test.workspace;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static us.kbase.common.test.TestCommon.set;

import java.util.Arrays;

import org.junit.Test;

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

	@Test
	public void createWorkspace1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		final WorkspaceUserMetadata meta = new WorkspaceUserMetadata();
		
		//TODO NOW change to value class
		final WorkspaceInformation info = mock(WorkspaceInformation.class);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.createWorkspace(new WorkspaceUser("foo"), "ws", false, null, meta))
				.thenReturn(info);
		
		when(info.getId()).thenReturn(6L);
		
		ws.createWorkspace(new WorkspaceUser("foo"), "ws", false, null, null);
		
		verify(l).createWorkspace(6L);
	}
	
	@Test
	public void createWorkspace2Listeners() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		final WorkspaceUserMetadata meta = new WorkspaceUserMetadata();
		
		//TODO NOW change to value class
		final WorkspaceInformation info = mock(WorkspaceInformation.class);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.createWorkspace(new WorkspaceUser("foo"), "ws", false, null, meta))
				.thenReturn(info);
		
		when(info.getId()).thenReturn(6L);
		
		ws.createWorkspace(new WorkspaceUser("foo"), "ws", false, null, null);
		
		verify(l1).createWorkspace(6L);
		verify(l2).createWorkspace(6L);
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
		
		//TODO NOW change to value class
		final WorkspaceInformation info = mock(WorkspaceInformation.class);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.READ, Permission.NONE).build());
		when(db.cloneWorkspace(user, rwsi, "whee", false, null, meta, null)).thenReturn(info);
		
		when(info.getId()).thenReturn(7L);
		
		ws.cloneWorkspace(user, wsi, "whee", false, null, null, null);
		
		verify(l).cloneWorkspace(7L);
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
		
		//TODO NOW change to value class
		final WorkspaceInformation info = mock(WorkspaceInformation.class);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.READ, Permission.NONE).build());
		when(db.cloneWorkspace(user, rwsi, "whee", false, null, meta, null)).thenReturn(info);
		
		when(info.getId()).thenReturn(7L);
		
		ws.cloneWorkspace(user, wsi, "whee", false, null, null, null);
		
		verify(l1).cloneWorkspace(7L);
		verify(l2).cloneWorkspace(7L);
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
	
	
	
}
