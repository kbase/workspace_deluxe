package us.kbase.workspace.test.workspace;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static us.kbase.common.test.TestCommon.set;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.mockito.ArgumentMatcher;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.core.ValidatedTypedObject;
import us.kbase.typedobj.idref.IdReferenceHandlerSet;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.CopyResult;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInfoWithModDate;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.ResolvedObjectIDNoVer;
import us.kbase.workspace.database.ResolvedSaveObject;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder.ResourceUsageConfiguration;
import us.kbase.workspace.database.UncheckedUserMetadata;
import us.kbase.workspace.listener.WorkspaceEventListener;

public class WorkspaceListenerTest {
	
	public static final WorkspaceInformation WS_INFO = WorkspaceInformation.getBuilder()
			.withID(42)
			.withName("wsfoo")
			.withMaximumObjectID(300)
			.withModificationDate(Instant.ofEpochMilli(20000))
			.withOwner(new WorkspaceUser("userfoo"))
			.withUserPermission(Permission.OWNER)
			.build();
	
	public static final WorkspaceInformation WS_INFO_READABLE = WorkspaceInformation.getBuilder()
			.withID(42)
			.withName("wsfoo")
			.withMaximumObjectID(302)
			.withModificationDate(Instant.ofEpochMilli(30000))
			.withOwner(new WorkspaceUser("userfoo"))
			.withUserPermission(Permission.OWNER)
			.withGlobalRead(true)
			.build();
	
	public static final ObjectInformation OBJ_INFO = new ObjectInformation(
			42L, "whee", "a type", new Date(40000), 45, new WorkspaceUser("bar"),
			new ResolvedWorkspaceID(24, "whee", false, false), "chksum",
			20, new UncheckedUserMetadata(Collections.emptyMap()));

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
		
		verify(l).createWorkspace(42L, Instant.ofEpochMilli(20000));
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
		
		verify(l1).createWorkspace(42L, Instant.ofEpochMilli(20000));
		verify(l2).createWorkspace(42L, Instant.ofEpochMilli(20000));
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
		
		verify(l).cloneWorkspace(42L, false, Instant.ofEpochMilli(20000));
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
		when(db.cloneWorkspace(user, rwsi, "whee", true, null, meta, null))
				.thenReturn(WS_INFO_READABLE);
		
		ws.cloneWorkspace(user, wsi, "whee", true, null, null, null);
		
		verify(l1).cloneWorkspace(42L, true, Instant.ofEpochMilli(30000));
		verify(l2).cloneWorkspace(42L, true, Instant.ofEpochMilli(30000));
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
		
		verify(l, never()).setWorkspaceMetadata(anyLong(), any(Instant.class));
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
		when(db.setWorkspaceMeta(rwsi, meta)).thenReturn(Instant.ofEpochMilli(20000));

		
		ws.setWorkspaceMetadata(user, wsi, meta, null);
		
		verify(l).setWorkspaceMetadata(24L, Instant.ofEpochMilli(20000));
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
		when(db.setWorkspaceMeta(rwsi, meta)).thenReturn(Instant.ofEpochMilli(20000));

		
		ws.setWorkspaceMetadata(user, wsi, meta, null);
		
		verify(l1).setWorkspaceMetadata(24L, Instant.ofEpochMilli(20000));
		verify(l2).setWorkspaceMetadata(24L, Instant.ofEpochMilli(20000));
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
		
		verify(l, never()).setWorkspaceMetadata(anyLong(), any(Instant.class));
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
		when(db.setWorkspaceMeta(rwsi, meta)).thenReturn(Instant.ofEpochMilli(20000));
		
		doThrow(new WorkspaceCommunicationException("whee"))
				.when(db).removeWorkspaceMetaKey(rwsi, "foo");
		try {
			ws.setWorkspaceMetadata(user, wsi, meta, Arrays.asList("foo"));
		} catch(WorkspaceCommunicationException e) {
			//fine
		}
		
		verify(l).setWorkspaceMetadata(24L, Instant.ofEpochMilli(20000));
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
		when(db.setWorkspaceMeta(rwsi, meta)).thenReturn(Instant.ofEpochMilli(20000));
		when(db.removeWorkspaceMetaKey(rwsi, "bar")).thenReturn(Instant.ofEpochMilli(30000));
		
		doThrow(new WorkspaceCommunicationException("whee"))
				.when(db).removeWorkspaceMetaKey(rwsi, "foo");
		try {
			ws.setWorkspaceMetadata(user, wsi, meta, Arrays.asList("bar", "foo"));
		} catch(WorkspaceCommunicationException e) {
			//fine
		}
		
		verify(l).setWorkspaceMetadata(24L, Instant.ofEpochMilli(30000));
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
		when(db.lockWorkspace(rwsi)).thenReturn(Instant.ofEpochMilli(20000));
		
		ws.lockWorkspace(user, wsi);
		
		verify(l).lockWorkspace(24L, Instant.ofEpochMilli(20000));
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
		when(db.lockWorkspace(rwsi)).thenReturn(Instant.ofEpochMilli(20000));
		
		ws.lockWorkspace(user, wsi);
		
		verify(l1).lockWorkspace(24L, Instant.ofEpochMilli(20000));
		verify(l2).lockWorkspace(24L, Instant.ofEpochMilli(20000));
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
		when(db.renameWorkspace(rwsi, "foobar")).thenReturn(Instant.ofEpochMilli(10000));
		
		ws.renameWorkspace(user, wsi, "foobar");
		
		verify(l).renameWorkspace(24L, "foobar", Instant.ofEpochMilli(10000));
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
		when(db.renameWorkspace(rwsi, "foobar")).thenReturn(Instant.ofEpochMilli(10000));
		
		ws.renameWorkspace(user, wsi, "foobar");
		
		verify(l1).renameWorkspace(24L, "foobar", Instant.ofEpochMilli(10000));
		verify(l2).renameWorkspace(24L, "foobar", Instant.ofEpochMilli(10000));
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
		when(db.setGlobalPermission(rwsi, Permission.READ))
				.thenReturn(Instant.ofEpochMilli(50000));
		
		ws.setGlobalPermission(user, wsi, Permission.READ);
		
		verify(l).setGlobalPermission(24L, Permission.READ, Instant.ofEpochMilli(50000));
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
		when(db.setGlobalPermission(rwsi, Permission.READ))
				.thenReturn(Instant.ofEpochMilli(50000));
		
		ws.setGlobalPermission(user, wsi, Permission.READ);
		
		verify(l1).setGlobalPermission(24L, Permission.READ, Instant.ofEpochMilli(50000));
		verify(l2).setGlobalPermission(24L, Permission.READ, Instant.ofEpochMilli(50000));
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
		final List<WorkspaceUser> users = Arrays.asList(new WorkspaceUser("foobar"));
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspace(wsi)).thenReturn(rwsi);
		when(db.getPermissions(user, rwsi)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.ADMIN, Permission.NONE).build());
		when(db.setPermissions(rwsi, users, Permission.WRITE))
				.thenReturn(Instant.ofEpochMilli(40000));
		
		ws.setPermissions(user, wsi, users, Permission.WRITE);
		
		verify(l).setPermissions(24, Permission.WRITE, users, Instant.ofEpochMilli(40000));
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
		final List<WorkspaceUser> users = Arrays.asList(new WorkspaceUser("foobar"));
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.resolveWorkspace(wsi)).thenReturn(rwsi);
		when(db.getPermissions(user, rwsi)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.ADMIN, Permission.NONE).build());
		when(db.setPermissions(rwsi, users, Permission.WRITE))
				.thenReturn(Instant.ofEpochMilli(40000));
		
		ws.setPermissions(user, wsi, users, Permission.WRITE);
		
		verify(l1).setPermissions(24, Permission.WRITE, users, Instant.ofEpochMilli(40000));
		verify(l2).setPermissions(24, Permission.WRITE, users, Instant.ofEpochMilli(40000));
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
		when(db.setWorkspaceDescription(rwsi, "foo")).thenReturn(Instant.ofEpochMilli(30000));
		
		ws.setWorkspaceDescription(user, wsi, "foo");
		
		verify(l).setWorkspaceDescription(24L, Instant.ofEpochMilli(30000));
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
		when(db.setWorkspaceDescription(rwsi, "foo")).thenReturn(Instant.ofEpochMilli(30000));
		
		ws.setWorkspaceDescription(user, wsi, "foo");
		
		verify(l1).setWorkspaceDescription(24L, Instant.ofEpochMilli(30000));
		verify(l2).setWorkspaceDescription(24L, Instant.ofEpochMilli(30000));
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
		when(db.setWorkspaceOwner(rwsi, user, newUser, Optional.absent()))
				.thenReturn(Instant.ofEpochMilli(30000));
		
		ws.setWorkspaceOwner(user, wsi, newUser, Optional.absent(), false);

		verify(l).setWorkspaceOwner(24L, newUser, Optional.absent(),
				Instant.ofEpochMilli(30000));
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
		when(db.setWorkspaceOwner(rwsi, user, newUser, Optional.of("bar:foobar")))
				.thenReturn(Instant.ofEpochMilli(30000));
		
		ws.setWorkspaceOwner(user, wsi, newUser, Optional.absent(), false);

		verify(l1).setWorkspaceOwner(24L, newUser, Optional.of("bar:foobar"),
				Instant.ofEpochMilli(30000));
		verify(l2).setWorkspaceOwner(24L, newUser, Optional.of("bar:foobar"),
				Instant.ofEpochMilli(30000));
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
		when(db.getWorkspaceInformation(user, rwsi)).thenReturn(WS_INFO_READABLE);
		when(db.setWorkspaceDeleted(rwsi, true)).thenReturn(Instant.ofEpochMilli(20000));
		
		ws.setWorkspaceDeleted(user, wsi, true, true);

		verify(l).setWorkspaceDeleted(24, true, 302, Instant.ofEpochMilli(20000));
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
		when(db.getWorkspaceInformation(user, rwsi)).thenReturn(WS_INFO);
		when(db.setWorkspaceDeleted(rwsi, false)).thenReturn(Instant.ofEpochMilli(20000));

		ws.setWorkspaceDeleted(user, wsi, false, true);

		verify(l1).setWorkspaceDeleted(24, false, 300, Instant.ofEpochMilli(20000));
		verify(l2).setWorkspaceDeleted(24, false, 300, Instant.ofEpochMilli(20000));
	}
	
	@Test
	public void renameObject1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ObjectIdentifier oi = new ObjectIdentifier(wsi, "whee");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		final ObjectIDResolvedWS roi = new ObjectIDResolvedWS(rwsi, "whee");
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspaces(set(wsi), false)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.WRITE, Permission.NONE).build());
		when(db.renameObject(roi, "bollocks")).thenReturn(
				new ObjectInfoWithModDate(OBJ_INFO, Instant.ofEpochMilli(20000)));
		
		ws.renameObject(user, oi, "bollocks");

		verify(l).renameObject(24, 42, "bollocks", Instant.ofEpochMilli(20000));
	}
	
	@Test
	public void renameObject2Listeners() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ObjectIdentifier oi = new ObjectIdentifier(wsi, "whee");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		final ObjectIDResolvedWS roi = new ObjectIDResolvedWS(rwsi, "whee");
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.resolveWorkspaces(set(wsi), false)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.WRITE, Permission.NONE).build());
		when(db.renameObject(roi, "bollocks")).thenReturn(
				new ObjectInfoWithModDate(OBJ_INFO, Instant.ofEpochMilli(20000)));
		
		ws.renameObject(user, oi, "bollocks");

		verify(l1).renameObject(24, 42, "bollocks", Instant.ofEpochMilli(20000));
		verify(l2).renameObject(24, 42, "bollocks", Instant.ofEpochMilli(20000));
	}
	
	@Test
	public void revertObject1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ObjectIdentifier oi = new ObjectIdentifier(wsi, "whee");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		final ObjectIDResolvedWS roi = new ObjectIDResolvedWS(rwsi, "whee");
		
		final WorkspaceInformation wsinfo = WorkspaceInformation.getBuilder()
				.withID(24)
				.withName("ugh")
				.withMaximumObjectID(2)
				.withModificationDate(Instant.now())
				.withOwner(new WorkspaceUser("foo"))
				.withUserPermission(Permission.WRITE)
				.withGlobalRead(true)
				.build();
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspaces(set(wsi), false)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.WRITE, Permission.NONE).build());
		when(db.revertObject(user, roi)).thenReturn(OBJ_INFO);
		when(db.getWorkspaceInformation(user, rwsi)).thenReturn(wsinfo);
		
		ws.revertObject(user, oi);
		
		verify(l).revertObject(OBJ_INFO, true);
	}
	
	@Test
	public void revertObject2Listeners() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ObjectIdentifier oi = new ObjectIdentifier(wsi, "whee");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		final ObjectIDResolvedWS roi = new ObjectIDResolvedWS(rwsi, "whee");
		
		final WorkspaceInformation wsinfo = WorkspaceInformation.getBuilder()
				.withID(24)
				.withName("ugh")
				.withMaximumObjectID(2)
				.withModificationDate(Instant.now())
				.withOwner(new WorkspaceUser("foo"))
				.withUserPermission(Permission.WRITE)
				.build();
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.resolveWorkspaces(set(wsi), false)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.WRITE, Permission.NONE).build());
		when(db.revertObject(user, roi)).thenReturn(OBJ_INFO);
		when(db.getWorkspaceInformation(user, rwsi)).thenReturn(wsinfo);
		
		ws.revertObject(user, oi);
		
		verify(l1).revertObject(OBJ_INFO, false);
		verify(l2).revertObject(OBJ_INFO, false);
	}
	
	@Test
	public void deleteObjects1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ObjectIdentifier oi1 = new ObjectIdentifier(wsi, "whee");
		final ObjectIdentifier oi2 = new ObjectIdentifier(wsi, "whoo");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		final ObjectIDResolvedWS roi1 = new ObjectIDResolvedWS(rwsi, "whee");
		final ObjectIDResolvedWS roi2 = new ObjectIDResolvedWS(rwsi, "whoo");
		final ResolvedObjectIDNoVer roiv1 = new ResolvedObjectIDNoVer(rwsi, 16, "whee", true);
		final ResolvedObjectIDNoVer roiv2 = new ResolvedObjectIDNoVer(rwsi, 75, "whoo", true);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspaces(set(wsi), false)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.WRITE, Permission.NONE).build());
		when(db.setObjectsDeleted(set(roi1, roi2), true)).thenReturn(ImmutableMap.of(
				roiv1, Instant.ofEpochMilli(20000),
				roiv2, Instant.ofEpochMilli(30000)));

		ws.setObjectsDeleted(user, Arrays.asList(oi1, oi2), true);

		verify(l).setObjectDeleted(24, 16, true, Instant.ofEpochMilli(20000));
		verify(l).setObjectDeleted(24, 75, true, Instant.ofEpochMilli(30000));
	}
	
	@Test
	public void deleteObjects2Listeners() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ObjectIdentifier oi1 = new ObjectIdentifier(wsi, "whee");
		final ObjectIdentifier oi2 = new ObjectIdentifier(wsi, "whoo");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		final ObjectIDResolvedWS roi1 = new ObjectIDResolvedWS(rwsi, "whee");
		final ObjectIDResolvedWS roi2 = new ObjectIDResolvedWS(rwsi, "whoo");
		final ResolvedObjectIDNoVer roiv1 = new ResolvedObjectIDNoVer(rwsi, 16, "whee", false);
		final ResolvedObjectIDNoVer roiv2 = new ResolvedObjectIDNoVer(rwsi, 75, "whoo", false);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.resolveWorkspaces(set(wsi), false)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.WRITE, Permission.NONE).build());
		when(db.setObjectsDeleted(set(roi1, roi2), false)).thenReturn(ImmutableMap.of(
				roiv1, Instant.ofEpochMilli(20000),
				roiv2, Instant.ofEpochMilli(30000)));

		ws.setObjectsDeleted(user, Arrays.asList(oi1, oi2), false);

		verify(l1).setObjectDeleted(24, 16, false, Instant.ofEpochMilli(20000));
		verify(l1).setObjectDeleted(24, 75, false, Instant.ofEpochMilli(30000));
		verify(l2).setObjectDeleted(24, 16, false, Instant.ofEpochMilli(20000));
		verify(l2).setObjectDeleted(24, 75, false, Instant.ofEpochMilli(30000));
	}
	
	@Test
	public void copyObject1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ObjectIdentifier from = new ObjectIdentifier(wsi, "whoo");
		final ObjectIdentifier to = new ObjectIdentifier(wsi, "whee");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		final ObjectIDResolvedWS rfrom = new ObjectIDResolvedWS(rwsi, "whoo");
		final ObjectIDResolvedWS rto = new ObjectIDResolvedWS(rwsi, "whee");
		
		final WorkspaceInformation wsinfo = WorkspaceInformation.getBuilder()
				.withID(24)
				.withName("ugh")
				.withMaximumObjectID(2)
				.withModificationDate(Instant.now())
				.withOwner(new WorkspaceUser("foo"))
				.withUserPermission(Permission.WRITE)
				.withGlobalRead(true)
				.build();
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		when(db.resolveWorkspaces(set(wsi), false)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.WRITE, Permission.NONE).build());
		when(db.copyObject(user, rfrom, rto)).thenReturn(new CopyResult(OBJ_INFO, false));
		when(db.getWorkspaceInformation(user, rwsi)).thenReturn(wsinfo);

		ws.copyObject(user, from, to);
		
		verify(l).copyObject(OBJ_INFO, true);
	}
	
	@Test
	public void copyObject2Listeners() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ObjectIdentifier from = new ObjectIdentifier(wsi, "whoo");
		final ObjectIdentifier to = new ObjectIdentifier(wsi, "whee");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		final ObjectIDResolvedWS rfrom = new ObjectIDResolvedWS(rwsi, "whoo");
		final ObjectIDResolvedWS rto = new ObjectIDResolvedWS(rwsi, "whee");
		
		final WorkspaceInformation wsinfo = WorkspaceInformation.getBuilder()
				.withID(24)
				.withName("ugh")
				.withMaximumObjectID(2)
				.withModificationDate(Instant.now())
				.withOwner(new WorkspaceUser("foo"))
				.withUserPermission(Permission.WRITE)
				.build();
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		when(db.resolveWorkspaces(set(wsi), false)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.WRITE, Permission.NONE).build());
		when(db.copyObject(user, rfrom, rto)).thenReturn(new CopyResult(OBJ_INFO, true));
		when(db.getWorkspaceInformation(user, rwsi)).thenReturn(wsinfo);

		ws.copyObject(user, from, to);
		
		verify(l1).copyObject(24, 42, 45, Instant.ofEpochMilli(40000), false);
		verify(l2).copyObject(24, 42, 45, Instant.ofEpochMilli(40000), false);
	}
	
	public static class SaveObjectsAnswerMatcher implements
			ArgumentMatcher<List<ResolvedSaveObject>> {

		private final List<ResolvedSaveObject> objects;
		
		public SaveObjectsAnswerMatcher(final List<ResolvedSaveObject> objects) {
			this.objects = objects;
		}
		
		@Override
		public boolean matches(final List<ResolvedSaveObject> incobjs) {
			if (objects.size() != incobjs.size()) {
				return false;
			}
			for (int i = 0; i < objects.size(); i++) {
				final ResolvedSaveObject rso1 = objects.get(i);
				final ResolvedSaveObject rso2 = incobjs.get(i);
				if (!rso1.getObjectIdentifier().equals(rso2.getObjectIdentifier())) {
					return false;
				}
			}
			return true;
		}
	}
	
	@Test
	public void saveObject1Listener() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ValidatedTypedObject vto1 = mock(ValidatedTypedObject.class);
		final ValidatedTypedObject vto2 = mock(ValidatedTypedObject.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		final IdReferenceHandlerSetFactory fac = new IdReferenceHandlerSetFactory(100000);
		final WorkspaceSaveObject wso1 = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("foo1"),
				new HashMap<>(),
				new TypeDefId("foo.bar"), null, new Provenance(user), false);
		final WorkspaceSaveObject wso2 = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("foo2"),
				new HashMap<>(),
				new TypeDefId("foo.baz"), null, new Provenance(user), false);
		
		final ResolvedSaveObject rso1 = wso1.resolve(vto1, set(), Collections.emptyList(),
				Collections.emptyMap());
		final ResolvedSaveObject rso2 = wso2.resolve(vto1, set(), Collections.emptyList(),
				Collections.emptyMap());
		
		final ObjectInformation oi1 = new ObjectInformation(
				35, "foo1", "foo.bar-2.1", new Date(60000), 6, new WorkspaceUser("foo"),
				rwsi, "chcksum1", 18, null);
		
		final ObjectInformation oi2 = new ObjectInformation(
				76, "foo2", "foo.baz-1.0", new Date(70000), 1, new WorkspaceUser("foo"),
				rwsi, "chcksum2", 22, null);
		
		final WorkspaceInformation wsinfo = WorkspaceInformation.getBuilder()
				.withID(24)
				.withName("ugh")
				.withMaximumObjectID(2)
				.withModificationDate(Instant.now())
				.withOwner(new WorkspaceUser("foo"))
				.withUserPermission(Permission.WRITE)
				.build();
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.OWNER, Permission.NONE).build());
		
		when(tv.validate(isA(UObject.class), eq(new TypeDefId("foo.bar")),
				isA(IdReferenceHandlerSet.class))).thenReturn(vto1);
		when(tv.validate(isA(UObject.class), eq(new TypeDefId("foo.baz")),
				isA(IdReferenceHandlerSet.class))).thenReturn(vto2);
		when(vto1.isInstanceValid()).thenReturn(true);
		when(vto2.isInstanceValid()).thenReturn(true);
		when(vto1.getRelabeledSize()).thenReturn(6L);
		when(vto2.getRelabeledSize()).thenReturn(7L);
		
		when(db.getWorkspaceInformation(user, rwsi)).thenReturn(wsinfo);
		when(db.saveObjects(eq(user), eq(rwsi),
				argThat(new SaveObjectsAnswerMatcher(Arrays.asList(rso1, rso2)))))
				.thenReturn(Arrays.asList(oi1, oi2));
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		ws.saveObjects(user, wsi, Arrays.asList(wso1, wso2), fac);
		
		verify(l).saveObject(oi1, false);
		verify(l).saveObject(oi2, false);
	}
	
	@Test
	public void saveObject2Listeners() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ValidatedTypedObject vto1 = mock(ValidatedTypedObject.class);
		final ValidatedTypedObject vto2 = mock(ValidatedTypedObject.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder().build();
		final WorkspaceEventListener l1 = mock(WorkspaceEventListener.class);
		final WorkspaceEventListener l2 = mock(WorkspaceEventListener.class);
		
		final WorkspaceUser user = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(24);
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(24, "ugh", false, false);
		final IdReferenceHandlerSetFactory fac = new IdReferenceHandlerSetFactory(100000);
		final WorkspaceSaveObject wso1 = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("foo1"),
				new HashMap<>(),
				new TypeDefId("foo.bar"), null, new Provenance(user), false);
		final WorkspaceSaveObject wso2 = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("foo2"),
				new HashMap<>(),
				new TypeDefId("foo.baz"), null, new Provenance(user), false);
		
		final ResolvedSaveObject rso1 = wso1.resolve(vto1, set(), Collections.emptyList(),
				Collections.emptyMap());
		final ResolvedSaveObject rso2 = wso2.resolve(vto1, set(), Collections.emptyList(),
				Collections.emptyMap());
		
		final ObjectInformation oi1 = new ObjectInformation(
				35, "foo1", "foo.bar-2.1", new Date(60000), 6, new WorkspaceUser("foo"),
				rwsi, "chcksum1", 18, null);
		
		final ObjectInformation oi2 = new ObjectInformation(
				76, "foo2", "foo.baz-1.0", new Date(70000), 1, new WorkspaceUser("foo"),
				rwsi, "chcksum2", 22, null);
		
		final WorkspaceInformation wsinfo = WorkspaceInformation.getBuilder()
				.withID(24)
				.withName("ugh")
				.withMaximumObjectID(2)
				.withModificationDate(Instant.now())
				.withOwner(new WorkspaceUser("foo"))
				.withUserPermission(Permission.WRITE)
				.withGlobalRead(true)
				.build();
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(db.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
						.withWorkspace(rwsi, Permission.OWNER, Permission.NONE).build());
		
		when(tv.validate(isA(UObject.class), eq(new TypeDefId("foo.bar")),
				isA(IdReferenceHandlerSet.class))).thenReturn(vto1);
		when(tv.validate(isA(UObject.class), eq(new TypeDefId("foo.baz")),
				isA(IdReferenceHandlerSet.class))).thenReturn(vto2);
		when(vto1.isInstanceValid()).thenReturn(true);
		when(vto2.isInstanceValid()).thenReturn(true);
		when(vto1.getRelabeledSize()).thenReturn(6L);
		when(vto2.getRelabeledSize()).thenReturn(7L);
		
		when(db.getWorkspaceInformation(user, rwsi)).thenReturn(wsinfo);
		when(db.saveObjects(eq(user), eq(rwsi),
				argThat(new SaveObjectsAnswerMatcher(Arrays.asList(rso1, rso2)))))
				.thenReturn(Arrays.asList(oi1, oi2));
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l1, l2));
		
		ws.saveObjects(user, wsi, Arrays.asList(wso1, wso2), fac);
		
		verify(l1).saveObject(oi1, true);
		verify(l1).saveObject(oi2, true);
		verify(l2).saveObject(oi1, true);
		verify(l2).saveObject(oi2, true);
	}
}

