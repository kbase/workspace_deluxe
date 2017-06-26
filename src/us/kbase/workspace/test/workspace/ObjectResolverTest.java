package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.set;

import java.util.Arrays;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIDWithRefPath;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectReferenceSet;
import us.kbase.workspace.database.ObjectResolver;
import us.kbase.workspace.database.ObjectResolver.ObjectResolution;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceUser;

public class ObjectResolverTest {

	/* ObjectResolver is, as of 6/25/17, mostly covered by WorkspaceTest. New tests should
	 * be written here and old tests moved to start slimming down the insanity of the latter class.
	 */
	//TODO TEST add tests for 100% coverage of ObjectResolver
	
	@Test
	public void searchOnReadableDeletedObject() throws Exception {
		// tests a bug where a deleted but readable object would not be accessible via search
		final WorkspaceDatabase wsdb = mock(WorkspaceDatabase.class);
		
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("wsfoo");
		final ResolvedWorkspaceID rwsi = new ResolvedWorkspaceID(3, "wsfoo", false, false);
		final ObjectIdentifier objid = new ObjectIdentifier(wsi, "objfoo");
		
		final ObjectIDWithRefPath obj = new ObjectIDWithRefPath(objid);
		final ObjectIDResolvedWS objresws = new ObjectIDResolvedWS(rwsi, "objfoo");
		final Reference ref = new Reference("3/24/1");
		final Reference topref = new Reference("3/27/1");
		final ObjectIDResolvedWS objres = new ObjectIDResolvedWS(rwsi, 24, 1);
		
		final WorkspaceUser user = new WorkspaceUser("userfoo");
		
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
}
