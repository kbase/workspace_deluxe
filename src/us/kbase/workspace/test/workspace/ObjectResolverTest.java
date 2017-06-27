package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.set;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.mockito.invocation.Invocation;

import com.google.common.collect.ImmutableMap;

import us.kbase.common.test.TestCommon;
import us.kbase.common.test.TestException;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIDWithRefPath;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectReferenceSet;
import us.kbase.workspace.database.ObjectResolver;
import us.kbase.workspace.database.ObjectResolver.Builder;
import us.kbase.workspace.database.ObjectResolver.ObjectResolution;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;

public class ObjectResolverTest {

	/* ObjectResolver is, as of 6/25/17, mostly covered by WorkspaceTest. New tests should
	 * be written here and old tests moved to start slimming down the insanity of the latter class.
	 */
	//TODO TEST add tests for 100% coverage of ObjectResolver
	
	@Test
	public void resolveStandardObject() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("wsfoo");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier obj = new ObjectIdentifier(wsi, "objfoo");
		final ObjectIDResolvedWS objresws = new ObjectIDResolvedWS(rwsi, "objfoo");
		
		when(wsdb.resolveWorkspaces(set(wsi), false)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(wsdb.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi, Permission.READ, Permission.NONE)
				.build());
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user)
				.withObject(obj).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(obj)));
		assertThat("incorrect object resolution", or.getObjectResolution(obj),
				is(ObjectResolution.NO_PATH));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set(obj)));
		assertThat("incorrect resolved object", or.getResolvedObject(obj), is(objresws));
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false),
				is(set(objresws)));
		assertNoReferencePath(or, obj);
	}
	
	@Test
	public void resolveStandardObjectFailUnreadable() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("wsfoo");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier obj = new ObjectIdentifier(wsi, "objfoo");
		
		when(wsdb.resolveWorkspaces(set(wsi), false)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(wsdb.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		
		final Builder or = ObjectResolver.getBuilder(wsdb, user).withObject(obj);
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failResolve(
				or, new InaccessibleObjectException(
				"Object objfoo cannot be accessed: User userfoo may not read workspace wsfoo",
				obj));
		
		assertThat("incorrect inaccessible object", e.getInaccessibleObject(), is(obj));
	}
	
	@Test
	public void resolveStandardObjectNullUnreadable() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("wsfoo");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier obj = new ObjectIdentifier(wsi, "objfoo");
		
		when(wsdb.resolveWorkspaces(set(wsi), false)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(wsdb.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user).withObject(obj)
				.withIgnoreInaccessible(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(obj)));
		assertThat("incorrect object resolution", or.getObjectResolution(obj),
				is(ObjectResolution.INACCESSIBLE));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertNoResolvedObject(or, obj);
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false),
				is(set()));
		assertNoReferencePath(or, obj);
		
	}
	
	@Test
	public void resolveStandardObjectAsAdminUnreadable() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("wsfoo");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier obj = new ObjectIdentifier(wsi, "objfoo");
		final ObjectIDResolvedWS objresws = new ObjectIDResolvedWS(rwsi, "objfoo");
		
		when(wsdb.resolveWorkspaces(set(wsi), false)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(wsdb.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user)
				.withObject(obj).withAsAdmin(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(obj)));
		assertThat("incorrect object resolution", or.getObjectResolution(obj),
				is(ObjectResolution.NO_PATH));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set(obj)));
		assertThat("incorrect resolved object", or.getResolvedObject(obj), is(objresws));
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false),
				is(set(objresws)));
		assertNoReferencePath(or, obj);
	}
	
	@Test
	public void resolvePathObject() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi1 = new WorkspaceIdentifier("wsfoo1");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier head = new ObjectIdentifier(wsi1, "objfoo");
		final ObjectIdentifier path1 = new ObjectIdentifier(wsi1, "objfoo2");
		final ObjectIdentifier path2 = new ObjectIdentifier(wsi2, "objfoo");
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(head, Arrays.asList(
				path1, path2, pathend));
		
		final ObjectIDResolvedWS headresws = new ObjectIDResolvedWS(rwsi1, "objfoo");
		final ObjectIDResolvedWS path1resws = new ObjectIDResolvedWS(rwsi1, "objfoo2");
		final ObjectIDResolvedWS path2resws = new ObjectIDResolvedWS(rwsi2, "objfoo");
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference headref = new Reference("3/6/3");
		final Reference path1ref = new Reference("3/5/1");
		final Reference path2ref = new Reference("4/8/5");
		final Reference pathendref = new Reference("4/1/1");
		final ObjectIDResolvedWS pathendresfinal = new ObjectIDResolvedWS(rwsi2, 1, 1);
		
		when(wsdb.resolveWorkspaces(set(wsi1), false)).thenReturn(ImmutableMap.of(wsi1, rwsi1));
		when(wsdb.getPermissions(user, set(rwsi1))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.getObjectOutgoingReferences(set(headresws), true, false, true)).thenReturn(
				ImmutableMap.of(headresws, new ObjectReferenceSet(headref,
						set(path1ref, new Reference("62/1/13")), false)));
		when(wsdb.resolveWorkspaces(set(wsi1, wsi2), true)).thenReturn(ImmutableMap.of(
				wsi1, rwsi1, wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi1, rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.getObjectOutgoingReferences(
				set(path1resws, path2resws, pathendresws), false, true, false)).thenReturn(
				ImmutableMap.of(
						path1resws, new ObjectReferenceSet(path1ref,
								set(path2ref, new Reference("62/2/13")), false),
						path2resws, new ObjectReferenceSet(path2ref,
								set(pathendref, new Reference("42/2/13")), false),
						pathendresws, new ObjectReferenceSet(pathendref,
								set(new Reference("62/2/13")), false)));
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user)
				.withObject(objpath).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.PATH));
		assertThat("incorrect path objects", or.getObjects(true), is(set(objpath)));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertThat("incorrect resolved object", or.getResolvedObject(objpath), is(pathendresfinal));
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true),
				is(set(pathendresfinal)));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertThat("incorrec ref path", or.getReferencePath(objpath), is(Arrays.asList(
				headref, path1ref, path2ref, pathendref)));
	}

	@Test
	public void resolvePathObjectFailUnreadable() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi1 = new WorkspaceIdentifier("wsfoo1");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier head = new ObjectIdentifier(wsi1, "objfoo");
		final ObjectIdentifier path1 = new ObjectIdentifier(wsi1, "objfoo2");
		final ObjectIdentifier path2 = new ObjectIdentifier(wsi2, "objfoo");
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(head, Arrays.asList(
				path1, path2, pathend));
		
		when(wsdb.resolveWorkspaces(set(wsi1), false)).thenReturn(ImmutableMap.of(wsi1, rwsi1));
		when(wsdb.getPermissions(user, set(rwsi1))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());

		final Builder or = ObjectResolver.getBuilder(wsdb, user)
				.withObject(objpath);
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failResolve(
				or, new InaccessibleObjectException(
				"Object objfoo cannot be accessed: User userfoo may not read workspace wsfoo1",
				objpath));
		
		assertThat("incorrect inaccessible object", e.getInaccessibleObject(), is(objpath));
	}
	
	@Test
	public void resolvePathObjectNullUnreadable() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi1 = new WorkspaceIdentifier("wsfoo1");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier head = new ObjectIdentifier(wsi1, "objfoo");
		final ObjectIdentifier path1 = new ObjectIdentifier(wsi1, "objfoo2");
		final ObjectIdentifier path2 = new ObjectIdentifier(wsi2, "objfoo");
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(head, Arrays.asList(
				path1, path2, pathend));
		
		when(wsdb.resolveWorkspaces(set(wsi1), false)).thenReturn(ImmutableMap.of(wsi1, rwsi1));
		when(wsdb.getPermissions(user, set(rwsi1))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());

		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user)
				.withObject(objpath).withIgnoreInaccessible(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.INACCESSIBLE));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertNoResolvedObject(or, objpath);
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertNoReferencePath(or, objpath);
	}
	
	@Test
	public void resolvePathObjectFailDeleted() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi1 = new WorkspaceIdentifier("wsfoo1");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier head = new ObjectIdentifier(wsi1, "objfoo");
		final ObjectIdentifier path1 = new ObjectIdentifier(wsi1, "objfoo2");
		final ObjectIdentifier path2 = new ObjectIdentifier(wsi2, "objfoo");
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(head, Arrays.asList(
				path1, path2, pathend));
		
		final ObjectIDResolvedWS headresws = new ObjectIDResolvedWS(rwsi1, "objfoo");
		
		when(wsdb.resolveWorkspaces(set(wsi1), false)).thenReturn(ImmutableMap.of(wsi1, rwsi1));
		when(wsdb.getPermissions(user, set(rwsi1))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.getObjectOutgoingReferences(set(headresws), true, false, true)).thenThrow(
				new NoSuchObjectException(
						"Object objfoo in workspace 3 (name wsfoo) is deleted", headresws));
		
		final Builder or = ObjectResolver.getBuilder(wsdb, user)
				.withObject(objpath);
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failResolve(
				or, new InaccessibleObjectException(
				"Object objfoo in workspace 3 (name wsfoo) is deleted", objpath));
		
		assertThat("incorrect inaccessible object", e.getInaccessibleObject(), is(objpath));
	}
	
	@Test
	public void resolvePathObjectNullDeleted() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi1 = new WorkspaceIdentifier("wsfoo1");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier head = new ObjectIdentifier(wsi1, "objfoo");
		final ObjectIdentifier path1 = new ObjectIdentifier(wsi1, "objfoo2");
		final ObjectIdentifier path2 = new ObjectIdentifier(wsi2, "objfoo");
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(head, Arrays.asList(
				path1, path2, pathend));
		
		final ObjectIDResolvedWS headresws = new ObjectIDResolvedWS(rwsi1, "objfoo");
		
		when(wsdb.resolveWorkspaces(set(wsi1), true)).thenReturn(ImmutableMap.of(wsi1, rwsi1));
		when(wsdb.getPermissions(user, set(rwsi1))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.getObjectOutgoingReferences(set(headresws), false, false, false)).thenReturn(
				Collections.emptyMap());
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user)
				.withObject(objpath).withIgnoreInaccessible(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.INACCESSIBLE));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertNoResolvedObject(or, objpath);
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertNoReferencePath(or, objpath);
	}
	
	@Test
	public void resolvePathObjectAsAdminUnreadable() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi1 = new WorkspaceIdentifier("wsfoo1");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier head = new ObjectIdentifier(wsi1, "objfoo");
		final ObjectIdentifier path1 = new ObjectIdentifier(wsi1, "objfoo2");
		final ObjectIdentifier path2 = new ObjectIdentifier(wsi2, "objfoo");
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(head, Arrays.asList(
				path1, path2, pathend));
		
		final ObjectIDResolvedWS headresws = new ObjectIDResolvedWS(rwsi1, "objfoo");
		final ObjectIDResolvedWS path1resws = new ObjectIDResolvedWS(rwsi1, "objfoo2");
		final ObjectIDResolvedWS path2resws = new ObjectIDResolvedWS(rwsi2, "objfoo");
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference headref = new Reference("3/6/3");
		final Reference path1ref = new Reference("3/5/1");
		final Reference path2ref = new Reference("4/8/5");
		final Reference pathendref = new Reference("4/1/1");
		final ObjectIDResolvedWS pathendresfinal = new ObjectIDResolvedWS(rwsi2, 1, 1);
		
		when(wsdb.resolveWorkspaces(set(wsi1), false)).thenReturn(ImmutableMap.of(wsi1, rwsi1));
		when(wsdb.getPermissions(user, set(rwsi1))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectOutgoingReferences(set(headresws), true, false, true)).thenReturn(
				ImmutableMap.of(headresws, new ObjectReferenceSet(headref,
						set(path1ref, new Reference("62/1/13")), false)));
		when(wsdb.resolveWorkspaces(set(wsi1, wsi2), true)).thenReturn(ImmutableMap.of(
				wsi1, rwsi1, wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi1, rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectOutgoingReferences(
				set(path1resws, path2resws, pathendresws), false, true, false)).thenReturn(
				ImmutableMap.of(
						path1resws, new ObjectReferenceSet(path1ref,
								set(path2ref, new Reference("62/2/13")), false),
						path2resws, new ObjectReferenceSet(path2ref,
								set(pathendref, new Reference("42/2/13")), false),
						pathendresws, new ObjectReferenceSet(pathendref,
								set(new Reference("62/2/13")), false)));
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user)
				.withObject(objpath).withAsAdmin(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.PATH));
		assertThat("incorrect path objects", or.getObjects(true), is(set(objpath)));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertThat("incorrect resolved object", or.getResolvedObject(objpath), is(pathendresfinal));
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true),
				is(set(pathendresfinal)));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertThat("incorrec ref path", or.getReferencePath(objpath), is(Arrays.asList(
				headref, path1ref, path2ref, pathendref)));
	}
	
	@Test
	public void resolvePathObjectAsAdminFailDeleted() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi1 = new WorkspaceIdentifier("wsfoo1");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier head = new ObjectIdentifier(wsi1, "objfoo");
		final ObjectIdentifier path1 = new ObjectIdentifier(wsi1, "objfoo2");
		final ObjectIdentifier path2 = new ObjectIdentifier(wsi2, "objfoo");
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(head, Arrays.asList(
				path1, path2, pathend));
		
		final ObjectIDResolvedWS headresws = new ObjectIDResolvedWS(rwsi1, "objfoo");
		
		when(wsdb.resolveWorkspaces(set(wsi1), false)).thenReturn(ImmutableMap.of(wsi1, rwsi1));
		when(wsdb.getPermissions(user, set(rwsi1))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectOutgoingReferences(set(headresws), true, false, true)).thenThrow(
				new NoSuchObjectException(
						"Object objfoo in workspace 3 (name wsfoo) is deleted", headresws));
		
		final Builder or = ObjectResolver.getBuilder(wsdb, user)
				.withObject(objpath).withAsAdmin(true);
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failResolve(
				or, new InaccessibleObjectException(
				"Object objfoo in workspace 3 (name wsfoo) is deleted", objpath));
		
		assertThat("incorrect inaccessible object", e.getInaccessibleObject(), is(objpath));
	}
	
	@Test
	public void resolvePathObjectAsAdminNullDeleted() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi1 = new WorkspaceIdentifier("wsfoo1");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier head = new ObjectIdentifier(wsi1, "objfoo");
		final ObjectIdentifier path1 = new ObjectIdentifier(wsi1, "objfoo2");
		final ObjectIdentifier path2 = new ObjectIdentifier(wsi2, "objfoo");
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(head, Arrays.asList(
				path1, path2, pathend));
		
		final ObjectIDResolvedWS headresws = new ObjectIDResolvedWS(rwsi1, "objfoo");
		
		when(wsdb.resolveWorkspaces(set(wsi1), true)).thenReturn(ImmutableMap.of(wsi1, rwsi1));
		when(wsdb.getPermissions(user, set(rwsi1))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectOutgoingReferences(set(headresws), false, false, false)).thenReturn(
				Collections.emptyMap());
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user)
				.withObject(objpath).withIgnoreInaccessible(true).withAsAdmin(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.INACCESSIBLE));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertNoResolvedObject(or, objpath);
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertNoReferencePath(or, objpath);
	}
	
	
	@Test
	public void searchSingleObjectAccessible() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference pathendref = new Reference("4/1/1");
		final ObjectIDResolvedWS pathendresfinal = new ObjectIDResolvedWS(rwsi2, 1, 1);
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi2, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi2, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, true));
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user)
				.withObject(objpath).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.PATH));
		assertThat("incorrect path objects", or.getObjects(true), is(set(objpath)));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertThat("incorrect resolved object", or.getResolvedObject(objpath), is(pathendresfinal));
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true),
				is(set(pathendresfinal)));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertThat("incorrec ref path", or.getReferencePath(objpath), is(Arrays.asList(
				pathendref)));
	}
	
	@Test
	public void searchSingleObjectFailUnreadable() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference pathendref = new Reference("4/1/1");
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, true));
		
		// loop 1
		when(wsdb.getObjectIncomingReferences(set(pathendref))).thenReturn(ImmutableMap.of(
				pathendref, new ObjectReferenceSet(pathendref, set(), true)));
		
		final Builder or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath);
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failResolve(
				or, new InaccessibleObjectException("The latest version of object objfoo2 " +
						"in workspace wsfoo2 is not accessible to user userfoo", objpath));
		
		assertThat("incorrect inaccessible object", e.getInaccessibleObject(), is(objpath));

		assertMethodInvoked(wsdb, "getObjectIncomingReferences", Arrays.asList(set(pathendref)));
	}
	
	@Test
	public void searchSingleObjectNullUnreadable() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference pathendref = new Reference("4/1/1");
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, true));
		
		// loop 1
		when(wsdb.getObjectIncomingReferences(set(pathendref))).thenReturn(ImmutableMap.of(
				pathendref, new ObjectReferenceSet(pathendref, set(), true)));
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath)
				.withIgnoreInaccessible(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.INACCESSIBLE));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertNoResolvedObject(or, objpath);
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertNoReferencePath(or, objpath);
		
		assertMethodInvoked(wsdb, "getObjectIncomingReferences", Arrays.asList(set(pathendref)));
	}
	
	@Test
	public void searchSingleObjectFailDeleted() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference pathendref = new Reference("4/1/1");
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi2, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi2, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, false));
		
		// loop 1
		when(wsdb.getObjectIncomingReferences(set(pathendref))).thenReturn(ImmutableMap.of(
				pathendref, new ObjectReferenceSet(pathendref, set(), true)));
		
		final Builder or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath);
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failResolve(
				or, new InaccessibleObjectException("The latest version of object objfoo2 " +
						"in workspace wsfoo2 is not accessible to user userfoo", objpath));
		
		assertThat("incorrect inaccessible object", e.getInaccessibleObject(), is(objpath));

		assertMethodInvoked(wsdb, "getObjectIncomingReferences", Arrays.asList(set(pathendref)));
	}
	
	@Test
	public void searchSingleObjectNullDeleted() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference pathendref = new Reference("4/1/1");
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi2, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi2, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, false));
		
		// loop 1
		when(wsdb.getObjectIncomingReferences(set(pathendref))).thenReturn(ImmutableMap.of(
				pathendref, new ObjectReferenceSet(pathendref, set(), true)));
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath)
				.withIgnoreInaccessible(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.INACCESSIBLE));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertNoResolvedObject(or, objpath);
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertNoReferencePath(or, objpath);
		
		assertMethodInvoked(wsdb, "getObjectIncomingReferences", Arrays.asList(set(pathendref)));
	}
	
	@Test
	public void searchSingleObjectAsAdminUnreadable() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference pathendref = new Reference("4/1/1");
		final ObjectIDResolvedWS pathendresfinal = new ObjectIDResolvedWS(rwsi2, 1, 1);
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, true));
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user)
				.withObject(objpath).withAsAdmin(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.PATH));
		assertThat("incorrect path objects", or.getObjects(true), is(set(objpath)));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertThat("incorrect resolved object", or.getResolvedObject(objpath), is(pathendresfinal));
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true),
				is(set(pathendresfinal)));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertThat("incorrec ref path", or.getReferencePath(objpath), is(Arrays.asList(
				pathendref)));
	}
	
	@Test
	public void searchSingleObjectAsAdminFailDeleted() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference pathendref = new Reference("4/1/1");
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi2, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi2, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, false));
		
		// loop 1
		when(wsdb.getObjectIncomingReferences(set(pathendref))).thenReturn(ImmutableMap.of(
				pathendref, new ObjectReferenceSet(pathendref, set(), true)));
		
		final Builder or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath)
				.withAsAdmin(true);
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failResolve(
				or, new InaccessibleObjectException("The latest version of object objfoo2 " +
						"in workspace wsfoo2 is not accessible to user userfoo", objpath));
		
		assertThat("incorrect inaccessible object", e.getInaccessibleObject(), is(objpath));

		assertMethodInvoked(wsdb, "getObjectIncomingReferences", Arrays.asList(set(pathendref)));
	}
	
	@Test
	public void searchSingleObjectAsAdminNullDeleted() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference pathendref = new Reference("4/1/1");
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi2, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi2, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, false));
		
		// loop 1
		when(wsdb.getObjectIncomingReferences(set(pathendref))).thenReturn(ImmutableMap.of(
				pathendref, new ObjectReferenceSet(pathendref, set(), true)));
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath)
				.withIgnoreInaccessible(true).withAsAdmin(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.INACCESSIBLE));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertNoResolvedObject(or, objpath);
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertNoReferencePath(or, objpath);
		
		assertMethodInvoked(wsdb, "getObjectIncomingReferences", Arrays.asList(set(pathendref)));
	}
	
	@Test
	public void search() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference headref = new Reference("3/6/3");
		final Reference path1ref = new Reference("3/5/1");
		final Reference path2ref = new Reference("4/8/5");
		final Reference pathendref = new Reference("4/1/1");
		final ObjectIDResolvedWS pathendresfinal = new ObjectIDResolvedWS(rwsi2, 1, 1);
		
		final Reference garbageref1 = new Reference("64/2/1");
		final Reference garbageref2 = new Reference("64/5/1");
		final Reference garbageref3 = new Reference("67/13/1");
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, true));
		when(wsdb.getObjectIncomingReferences(set(pathendref))).thenReturn(ImmutableMap.of(
				pathendref, new ObjectReferenceSet(
						pathendref, set(path2ref, garbageref1), true)));
		when(wsdb.getObjectIncomingReferences(set(path2ref, garbageref1))).thenReturn(
				ImmutableMap.of(
						path2ref, new ObjectReferenceSet(
								path2ref, set(path1ref, garbageref2), true),
						garbageref1, new ObjectReferenceSet(garbageref1, set(), true)));
		when(wsdb.getObjectExistsRef(set(path1ref))).thenReturn(
				ImmutableMap.of(path1ref, false));
		when(wsdb.getObjectIncomingReferences(set(path1ref, garbageref2))).thenReturn(
				ImmutableMap.of(
						path1ref, new ObjectReferenceSet(
								path1ref, set(headref, garbageref3), true),
						garbageref2, new ObjectReferenceSet(garbageref2, set(), true)));
		when(wsdb.getObjectExistsRef(set(headref))).thenReturn(
				ImmutableMap.of(headref, true));
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user)
				.withObject(objpath).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.PATH));
		assertThat("incorrect path objects", or.getObjects(true), is(set(objpath)));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertThat("incorrect resolved object", or.getResolvedObject(objpath), is(pathendresfinal));
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true),
				is(set(pathendresfinal)));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertThat("incorrec ref path", or.getReferencePath(objpath), is(Arrays.asList(
				headref, path1ref, path2ref, pathendref)));
	}

	@Test
	public void searchFailNoReadableWorkspaces() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());

		final Builder or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath);
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failResolve(
				or, new InaccessibleObjectException("The latest version of object objfoo2 " +
						"in workspace wsfoo2 is not accessible to user userfoo", objpath));
		
		assertThat("incorrect inaccessible object", e.getInaccessibleObject(), is(objpath));
	}
	
	@Test
	public void searchNullNoReadableWorkspaces() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());

		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath)
				.withIgnoreInaccessible(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.INACCESSIBLE));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertNoResolvedObject(or, objpath);
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertNoReferencePath(or, objpath);
	}
	
	@Test
	public void searchFailNoObjectsInReadableWorkspaces() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ResolvedWorkspaceID rwsireadable = new ResolvedWorkspaceID(5, "wsfoo3", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference headref = new Reference("3/6/3");
		final Reference path1ref = new Reference("3/5/1");
		final Reference path2ref = new Reference("4/8/5");
		final Reference pathendref = new Reference("4/1/1");
		
		final Reference garbageref1 = new Reference("64/2/1");
		final Reference garbageref2 = new Reference("64/5/1");
		final Reference garbageref3 = new Reference("67/13/1");
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsireadable, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, true));
		when(wsdb.getObjectIncomingReferences(set(pathendref))).thenReturn(ImmutableMap.of(
				pathendref, new ObjectReferenceSet(
						pathendref, set(path2ref, garbageref1), true)));
		when(wsdb.getObjectIncomingReferences(set(path2ref, garbageref1))).thenReturn(
				ImmutableMap.of(
						path2ref, new ObjectReferenceSet(
								path2ref, set(path1ref, garbageref2), true),
						garbageref1, new ObjectReferenceSet(garbageref1, set(), true)));
		when(wsdb.getObjectIncomingReferences(set(path1ref, garbageref2))).thenReturn(
				ImmutableMap.of(
						path1ref, new ObjectReferenceSet(
								path1ref, set(headref, garbageref3), true),
						garbageref2, new ObjectReferenceSet(garbageref2, set(), true)));
		
		final Builder or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath);
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failResolve(
				or, new InaccessibleObjectException("The latest version of object objfoo2 " +
						"in workspace wsfoo2 is not accessible to user userfoo", objpath));
		
		assertThat("incorrect inaccessible object", e.getInaccessibleObject(), is(objpath));
		
		// ensure the last lookup occurred
		assertMethodInvoked(wsdb, "getObjectIncomingReferences",
				Arrays.asList(set(path1ref, garbageref2)));
	}
	
	@Test
	public void searchNullNoObjectsInReadableWorkspaces() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ResolvedWorkspaceID rwsireadable = new ResolvedWorkspaceID(5, "wsfoo3", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference headref = new Reference("3/6/3");
		final Reference path1ref = new Reference("3/5/1");
		final Reference path2ref = new Reference("4/8/5");
		final Reference pathendref = new Reference("4/1/1");
		
		final Reference garbageref1 = new Reference("64/2/1");
		final Reference garbageref2 = new Reference("64/5/1");
		final Reference garbageref3 = new Reference("67/13/1");
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsireadable, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, true));
		when(wsdb.getObjectIncomingReferences(set(pathendref))).thenReturn(ImmutableMap.of(
				pathendref, new ObjectReferenceSet(
						pathendref, set(path2ref, garbageref1), true)));
		when(wsdb.getObjectIncomingReferences(set(path2ref, garbageref1))).thenReturn(
				ImmutableMap.of(
						path2ref, new ObjectReferenceSet(
								path2ref, set(path1ref, garbageref2), true),
						garbageref1, new ObjectReferenceSet(garbageref1, set(), true)));
		when(wsdb.getObjectIncomingReferences(set(path1ref, garbageref2))).thenReturn(
				ImmutableMap.of(
						path1ref, new ObjectReferenceSet(
								path1ref, set(headref, garbageref3), true),
						garbageref2, new ObjectReferenceSet(garbageref2, set(), true)));
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath)
				.withIgnoreInaccessible(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.INACCESSIBLE));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertNoResolvedObject(or, objpath);
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertNoReferencePath(or, objpath);
		
		// ensure the last lookup occurred
		assertMethodInvoked(wsdb, "getObjectIncomingReferences",
				Arrays.asList(set(path1ref, garbageref2)));
	}
	
	@Test
	public void searchFailDeletedObjects() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference headref = new Reference("3/6/3");
		final Reference path1ref = new Reference("3/5/1");
		final Reference path2ref = new Reference("4/8/5");
		final Reference pathendref = new Reference("4/1/1");
		
		final Reference garbageref1 = new Reference("64/2/1");
		final Reference garbageref2 = new Reference("64/5/1");
		final Reference garbageref3 = new Reference("67/13/1");
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, true));
		when(wsdb.getObjectIncomingReferences(set(pathendref))).thenReturn(ImmutableMap.of(
				pathendref, new ObjectReferenceSet(
						pathendref, set(path2ref, garbageref1), true)));
		when(wsdb.getObjectIncomingReferences(set(path2ref, garbageref1))).thenReturn(
				ImmutableMap.of(
						path2ref, new ObjectReferenceSet(
								path2ref, set(path1ref, garbageref2), true),
						garbageref1, new ObjectReferenceSet(garbageref1, set(), true)));
		when(wsdb.getObjectExistsRef(set(path1ref))).thenReturn(
				ImmutableMap.of(path1ref, false));
		when(wsdb.getObjectIncomingReferences(set(path1ref, garbageref2))).thenReturn(
				ImmutableMap.of(
						path1ref, new ObjectReferenceSet(
								path1ref, set(headref, garbageref3), true),
						garbageref2, new ObjectReferenceSet(garbageref2, set(), true)));
		when(wsdb.getObjectExistsRef(set(headref))).thenReturn(
				ImmutableMap.of(headref, false));
		
		final Builder or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath);
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failResolve(
				or, new InaccessibleObjectException("The latest version of object objfoo2 " +
						"in workspace wsfoo2 is not accessible to user userfoo", objpath));
		
		assertThat("incorrect inaccessible object", e.getInaccessibleObject(), is(objpath));
		
		// ensure the last lookup occurred
		assertMethodInvoked(wsdb, "getObjectExistsRef", Arrays.asList(set(headref)));
	}
	
	@Test
	public void searchNullDeletedObjects() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference headref = new Reference("3/6/3");
		final Reference path1ref = new Reference("3/5/1");
		final Reference path2ref = new Reference("4/8/5");
		final Reference pathendref = new Reference("4/1/1");
		
		final Reference garbageref1 = new Reference("64/2/1");
		final Reference garbageref2 = new Reference("64/5/1");
		final Reference garbageref3 = new Reference("67/13/1");
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, true));
		when(wsdb.getObjectIncomingReferences(set(pathendref))).thenReturn(ImmutableMap.of(
				pathendref, new ObjectReferenceSet(
						pathendref, set(path2ref, garbageref1), true)));
		when(wsdb.getObjectIncomingReferences(set(path2ref, garbageref1))).thenReturn(
				ImmutableMap.of(
						path2ref, new ObjectReferenceSet(
								path2ref, set(path1ref, garbageref2), true),
						garbageref1, new ObjectReferenceSet(garbageref1, set(), true)));
		when(wsdb.getObjectExistsRef(set(path1ref))).thenReturn(
				ImmutableMap.of(path1ref, false));
		when(wsdb.getObjectIncomingReferences(set(path1ref, garbageref2))).thenReturn(
				ImmutableMap.of(
						path1ref, new ObjectReferenceSet(
								path1ref, set(headref, garbageref3), true),
						garbageref2, new ObjectReferenceSet(garbageref2, set(), true)));
		when(wsdb.getObjectExistsRef(set(headref))).thenReturn(
				ImmutableMap.of(headref, false));
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath)
				.withIgnoreInaccessible(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.INACCESSIBLE));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertNoResolvedObject(or, objpath);
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertNoReferencePath(or, objpath);
		
		// ensure the last lookup occurred
		assertMethodInvoked(wsdb, "getObjectExistsRef", Arrays.asList(set(headref)));
	}
	
	@Test
	public void searchFailNoStartingObject() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(Collections.emptyMap());
		
		final Builder or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath);
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failResolve(
				or, new InaccessibleObjectException("The latest version of object objfoo2 " +
						"in workspace wsfoo2 is not accessible to user userfoo", objpath));
		
		assertThat("incorrect inaccessible object", e.getInaccessibleObject(), is(objpath));
		
		// ensure the last lookup occurred
		assertMethodInvoked(wsdb, "getObjectReference", Arrays.asList(set(pathendresws)));
	}
	
	@Test
	public void searchNullNoStartingObject() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(Collections.emptyMap());
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath)
				.withIgnoreInaccessible(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.INACCESSIBLE));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertNoResolvedObject(or, objpath);
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertNoReferencePath(or, objpath);
		
		// ensure the last lookup occurred
		assertMethodInvoked(wsdb, "getObjectReference", Arrays.asList(set(pathendresws)));
	}
	
	@Test
	public void searchFailNoStartingWorkspace() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(Collections.emptyMap());
		
		final Builder or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath);
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failResolve(
				or, new InaccessibleObjectException("The latest version of object objfoo2 " +
						"in workspace wsfoo2 is not accessible to user userfoo", objpath));
		
		assertThat("incorrect inaccessible object", e.getInaccessibleObject(), is(objpath));
		
		// ensure the last lookup occurred
		assertMethodInvoked(wsdb, "resolveWorkspaces", Arrays.asList(set(wsi2), true));
	}
	
	@Test
	public void searchNullNoStartingWorkspace() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(Collections.emptyMap());
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath)
				.withIgnoreInaccessible(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.INACCESSIBLE));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertNoResolvedObject(or, objpath);
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertNoReferencePath(or, objpath);
		
		// ensure the last lookup occurred
		assertMethodInvoked(wsdb, "resolveWorkspaces", Arrays.asList(set(wsi2), true));
	}
	
	@Test
	public void searchAsAdminNoReadableWorkspaces() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference headref = new Reference("3/6/3");
		final Reference path1ref = new Reference("3/5/1");
		final Reference path2ref = new Reference("4/8/5");
		final Reference pathendref = new Reference("4/1/1");
		final ObjectIDResolvedWS pathendresfinal = new ObjectIDResolvedWS(rwsi2, 1, 1);
		
		final Reference garbageref1 = new Reference("64/2/1");
		final Reference garbageref2 = new Reference("64/5/1");
		final Reference garbageref3 = new Reference("67/13/1");
		
		//set up
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, false));
		
		// loop 1
		when(wsdb.getObjectIncomingReferences(set(pathendref))).thenReturn(ImmutableMap.of(
				pathendref, new ObjectReferenceSet(
						pathendref, set(path2ref, garbageref1), true)));
		when(wsdb.getObjectExistsRef(set(path2ref, garbageref1))).thenReturn(
				ImmutableMap.of(path2ref, false, garbageref1, false));
		
		// loop 2
		when(wsdb.getObjectIncomingReferences(set(path2ref, garbageref1))).thenReturn(
				ImmutableMap.of(
						path2ref, new ObjectReferenceSet(
								path2ref, set(path1ref, garbageref2), true),
						garbageref1, new ObjectReferenceSet(garbageref1, set(), true)));
		when(wsdb.getObjectExistsRef(set(path1ref, garbageref2))).thenReturn(
				ImmutableMap.of(path1ref, false, garbageref2, false));
		
		// loop 3
		when(wsdb.getObjectIncomingReferences(set(path1ref, garbageref2))).thenReturn(
				ImmutableMap.of(
						path1ref, new ObjectReferenceSet(
								path1ref, set(headref, garbageref3), true),
						garbageref2, new ObjectReferenceSet(garbageref2, set(), true)));
		when(wsdb.getObjectExistsRef(set(headref, garbageref3))).thenReturn(
				ImmutableMap.of(headref, true));
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user)
				.withObject(objpath).withAsAdmin(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.PATH));
		assertThat("incorrect path objects", or.getObjects(true), is(set(objpath)));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertThat("incorrect resolved object", or.getResolvedObject(objpath), is(pathendresfinal));
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true),
				is(set(pathendresfinal)));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertThat("incorrec ref path", or.getReferencePath(objpath), is(Arrays.asList(
				headref, path1ref, path2ref, pathendref)));
	}
	
	@Test
	public void searchAsAdminFailDeletedObjects() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference headref = new Reference("3/6/3");
		final Reference path1ref = new Reference("3/5/1");
		final Reference path2ref = new Reference("4/8/5");
		final Reference pathendref = new Reference("4/1/1");
		
		final Reference garbageref1 = new Reference("64/2/1");
		final Reference garbageref2 = new Reference("64/5/1");
		final Reference garbageref3 = new Reference("67/13/1");
		
		//set up
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, false));
		
		// loop 1
		when(wsdb.getObjectIncomingReferences(set(pathendref))).thenReturn(ImmutableMap.of(
				pathendref, new ObjectReferenceSet(
						pathendref, set(path2ref, garbageref1), true)));
		when(wsdb.getObjectExistsRef(set(path2ref, garbageref1))).thenReturn(
				ImmutableMap.of(path2ref, false, garbageref1, false));
		
		// loop 2
		when(wsdb.getObjectIncomingReferences(set(path2ref, garbageref1))).thenReturn(
				ImmutableMap.of(
						path2ref, new ObjectReferenceSet(
								path2ref, set(path1ref, garbageref2), true),
						garbageref1, new ObjectReferenceSet(garbageref1, set(), true)));
		when(wsdb.getObjectExistsRef(set(path1ref, garbageref2))).thenReturn(
				ImmutableMap.of(path1ref, false, garbageref2, false));
		
		// loop 3
		when(wsdb.getObjectIncomingReferences(set(path1ref, garbageref2))).thenReturn(
				ImmutableMap.of(
						path1ref, new ObjectReferenceSet(
								path1ref, set(headref, garbageref3), true),
						garbageref2, new ObjectReferenceSet(garbageref2, set(), true)));
		when(wsdb.getObjectExistsRef(set(headref, garbageref3))).thenReturn(
				ImmutableMap.of(headref, false));
		
		final Builder or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath)
				.withAsAdmin(true);
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failResolve(
				or, new InaccessibleObjectException("The latest version of object objfoo2 " +
						"in workspace wsfoo2 is not accessible to user userfoo", objpath));
		
		assertThat("incorrect inaccessible object", e.getInaccessibleObject(), is(objpath));
		
		// ensure the last lookup occurred
		assertMethodInvoked(wsdb, "getObjectExistsRef", Arrays.asList(set(headref, garbageref3)));
	}
	
	@Test
	public void searchAsAdminNullDeletedObjects() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		final Reference headref = new Reference("3/6/3");
		final Reference path1ref = new Reference("3/5/1");
		final Reference path2ref = new Reference("4/8/5");
		final Reference pathendref = new Reference("4/1/1");
		
		final Reference garbageref1 = new Reference("64/2/1");
		final Reference garbageref2 = new Reference("64/5/1");
		final Reference garbageref3 = new Reference("67/13/1");
		
		//set up
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(ImmutableMap.of(
				pathendresws, pathendref));
		when(wsdb.getObjectExistsRef(set(pathendref))).thenReturn(
				ImmutableMap.of(pathendref, false));
		
		// loop 1
		when(wsdb.getObjectIncomingReferences(set(pathendref))).thenReturn(ImmutableMap.of(
				pathendref, new ObjectReferenceSet(
						pathendref, set(path2ref, garbageref1), true)));
		when(wsdb.getObjectExistsRef(set(path2ref, garbageref1))).thenReturn(
				ImmutableMap.of(path2ref, false, garbageref1, false));
		
		// loop 2
		when(wsdb.getObjectIncomingReferences(set(path2ref, garbageref1))).thenReturn(
				ImmutableMap.of(
						path2ref, new ObjectReferenceSet(
								path2ref, set(path1ref, garbageref2), true),
						garbageref1, new ObjectReferenceSet(garbageref1, set(), true)));
		when(wsdb.getObjectExistsRef(set(path1ref, garbageref2))).thenReturn(
				ImmutableMap.of(path1ref, false, garbageref2, false));
		
		// loop 3
		when(wsdb.getObjectIncomingReferences(set(path1ref, garbageref2))).thenReturn(
				ImmutableMap.of(
						path1ref, new ObjectReferenceSet(
								path1ref, set(headref, garbageref3), true),
						garbageref2, new ObjectReferenceSet(garbageref2, set(), true)));
		when(wsdb.getObjectExistsRef(set(headref, garbageref3))).thenReturn(
				ImmutableMap.of(headref, false));
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath)
				.withIgnoreInaccessible(true).withAsAdmin(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.INACCESSIBLE));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertNoResolvedObject(or, objpath);
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertNoReferencePath(or, objpath);
		
		// ensure the last lookup occurred
		assertMethodInvoked(wsdb, "getObjectExistsRef", Arrays.asList(set(headref, garbageref3)));
	}
	
	@Test
	public void searchAsAdminFailNoStartingObject() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(Collections.emptyMap());
		
		final Builder or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath)
				.withAsAdmin(true);
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failResolve(
				or, new InaccessibleObjectException("The latest version of object objfoo2 " +
						"in workspace wsfoo2 is not accessible to user userfoo", objpath));
		
		assertThat("incorrect inaccessible object", e.getInaccessibleObject(), is(objpath));
		
		// ensure the last lookup occurred
		assertMethodInvoked(wsdb, "getObjectReference", Arrays.asList(set(pathendresws)));
	}
	
	@Test
	public void searchAsAdminNullNoStartingObject() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ResolvedWorkspaceID rwsi2 = new ResolvedWorkspaceID(4, "wsfoo2", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		final ObjectIDResolvedWS pathendresws = new ObjectIDResolvedWS(rwsi2, "objfoo2");
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(ImmutableMap.of(wsi2, rwsi2));
		when(wsdb.getPermissions(user, set(rwsi2))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.build());
		when(wsdb.getObjectReference(set(pathendresws))).thenReturn(Collections.emptyMap());
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath)
				.withIgnoreInaccessible(true).withAsAdmin(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.INACCESSIBLE));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertNoResolvedObject(or, objpath);
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertNoReferencePath(or, objpath);
		
		// ensure the last lookup occurred
		assertMethodInvoked(wsdb, "getObjectReference", Arrays.asList(set(pathendresws)));
	}
	
	@Test
	public void searchAsAdminFailNoStartingWorkspace() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(Collections.emptyMap());
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath)
				.withIgnoreInaccessible(true).withAsAdmin(true).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(objpath)));
		assertThat("incorrect object resolution", or.getObjectResolution(objpath),
				is(ObjectResolution.INACCESSIBLE));
		assertThat("incorrect path objects", or.getObjects(true), is(set()));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertNoResolvedObject(or, objpath);
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true), is(set()));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertNoReferencePath(or, objpath);
		
		// ensure the last lookup occurred
		assertMethodInvoked(wsdb, "resolveWorkspaces", Arrays.asList(set(wsi2), true));
	}
	
	@Test
	public void searchAsAdminNullNoStartingWorkspace() throws Exception {
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("wsfoo2");
		final ResolvedWorkspaceID rwsi1 = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier pathend = new ObjectIdentifier(wsi2, "objfoo2");
		final ObjectIDWithRefPath objpath = new ObjectIDWithRefPath(pathend);
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi1, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi2), true)).thenReturn(Collections.emptyMap());
		
		final Builder or = ObjectResolver.getBuilder(wsdb, user).withObject(objpath)
				.withAsAdmin(true);
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failResolve(
				or, new InaccessibleObjectException("The latest version of object objfoo2 " +
						"in workspace wsfoo2 is not accessible to user userfoo", objpath));
		
		assertThat("incorrect inaccessible object", e.getInaccessibleObject(), is(objpath));
		
		// ensure the last lookup occurred
		assertMethodInvoked(wsdb, "resolveWorkspaces", Arrays.asList(set(wsi2), true));
	}
	
	@Test
	public void searchOnReadableDeletedObject() throws Exception {
		// tests a bug where a deleted but readable object would not be accessible via search
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("wsfoo");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier objid = new ObjectIdentifier(wsi, "objfoo");
		
		final ObjectIDWithRefPath obj = new ObjectIDWithRefPath(objid);
		final ObjectIDResolvedWS objresws = new ObjectIDResolvedWS(rwsi, "objfoo");
		final Reference ref = new Reference("3/24/1");
		final Reference topref = new Reference("3/27/1");
		final ObjectIDResolvedWS objres = new ObjectIDResolvedWS(rwsi, 24, 1);
		
		when(wsdb.getPermissions(user, Permission.READ, false)).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.resolveWorkspaces(set(wsi), true)).thenReturn(ImmutableMap.of(wsi, rwsi));
		when(wsdb.getPermissions(user, set(rwsi))).thenReturn(
				PermissionSet.getBuilder(user, new AllUsers('*'))
				.withWorkspace(rwsi, Permission.READ, Permission.NONE)
				.build());
		when(wsdb.getObjectReference(set(objresws))).thenReturn(ImmutableMap.of(
				objresws, ref));
		when(wsdb.getObjectExistsRef(set(ref))).thenReturn(ImmutableMap.of(ref, false));
		when(wsdb.getObjectIncomingReferences(set(ref))).thenReturn(ImmutableMap.of(
				ref, new ObjectReferenceSet(ref, set(topref), true)));
		when(wsdb.getObjectExistsRef(set(topref))).thenReturn(ImmutableMap.of(topref, true));
		
		final ObjectResolver or = ObjectResolver.getBuilder(wsdb, user)
				.withObject(obj).resolve();
		
		assertThat("incorrect objects", or.getObjects(), is(Arrays.asList(obj)));
		assertThat("incorrect object resolution", or.getObjectResolution(obj),
				is(ObjectResolution.PATH));
		assertThat("incorrect path objects", or.getObjects(true), is(set(obj)));
		assertThat("incorrect pathless objects", or.getObjects(false), is(set()));
		assertThat("incorrect resolved object", or.getResolvedObject(obj), is(objres));
		assertThat("incorrect path resolved objects", or.getResolvedObjects(true),
				is(set(objres)));
		assertThat("incorrect pathless resolved objects", or.getResolvedObjects(false), is(set()));
		assertThat("incorrect ref path", or.getReferencePath(obj), is(Arrays.asList(
				topref, ref)));
	}
	
	private void assertMethodInvoked(
			final WorkspaceDatabase wsdb,
			final String methodname,
			final List<?> args) {
		for (final Invocation inv: mockingDetails(wsdb).getInvocations()) {
			if (inv.getMethod().getName().equals(methodname) &&
					Arrays.equals(inv.getArguments(), args.toArray())) {
				return;
			}
		}
		throw new TestException(String.format(
				"invocation of method %s with arguments %s not found", methodname, args));
	}
	
	private Exception failResolve(final Builder or, final Exception e) {
		try {
			or.resolve();
			fail("expected exception");
			return null;
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
			return got;
		}
	}

	private void assertNoResolvedObject(final ObjectResolver or, final ObjectIdentifier obj) {
		try {
			or.getResolvedObject(obj);
			fail("expected no resolved object");
		} catch (Exception e) {
			TestCommon.assertExceptionCorrect(
					e, new IllegalArgumentException("Object is inaccessible"));
		}
	}

	private void assertNoReferencePath(final ObjectResolver or, final ObjectIdentifier obj) {
		try {
			or.getReferencePath(obj);
			fail("expected no reference path");
		} catch (Exception e) {
			TestCommon.assertExceptionCorrect(
					e, new IllegalArgumentException("No reference path is available"));
		}
	}
}
