package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionsCheckerFactory;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;

public class PermissionsCheckerFactoryTest {

	@Test
	public void checkLockedSuccess() throws Exception {
		final ResolvedWorkspaceID locked = new ResolvedWorkspaceID(1, "foo", true, false);
		final ResolvedWorkspaceID unlocked = new ResolvedWorkspaceID(1, "foo", false, false);
		
		//none of these should throw an exception
		PermissionsCheckerFactory.checkLocked(Permission.NONE, locked);
		PermissionsCheckerFactory.checkLocked(Permission.READ, locked);
		PermissionsCheckerFactory.checkLocked(Permission.NONE, unlocked);
		PermissionsCheckerFactory.checkLocked(Permission.READ, unlocked);
		PermissionsCheckerFactory.checkLocked(Permission.WRITE, unlocked);
		PermissionsCheckerFactory.checkLocked(Permission.ADMIN, unlocked);
		PermissionsCheckerFactory.checkLocked(Permission.OWNER, unlocked);
	}
	
	@Test
	public void checkLockedFail() throws Exception {
		final ResolvedWorkspaceID locked = new ResolvedWorkspaceID(1, "foo", true, false);
		final WorkspaceAuthorizationException e = new WorkspaceAuthorizationException(
				"The workspace with id 1, name foo, is locked and may not be modified");
		
		failCheckLocked(Permission.WRITE, locked, e);
		failCheckLocked(Permission.ADMIN, locked, e);
		failCheckLocked(Permission.OWNER, locked, e);
	}
	
	private void failCheckLocked(
			final Permission perm,
			final ResolvedWorkspaceID ws,
			final Exception e) {
		try {
			PermissionsCheckerFactory.checkLocked(perm, ws);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void comparePermissionSuccess() throws Exception {
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(1);
		final String o = "whee";
		
		// none of these should throw an exception
		PermissionsCheckerFactory.comparePermission(u, Permission.NONE, Permission.NONE, wsi, o);
		PermissionsCheckerFactory.comparePermission(u, Permission.READ, Permission.READ, wsi, o);
		PermissionsCheckerFactory.comparePermission(u, Permission.READ, Permission.WRITE, wsi, o);
		PermissionsCheckerFactory.comparePermission(u, Permission.WRITE, Permission.WRITE, wsi, o);
		PermissionsCheckerFactory.comparePermission(u, Permission.WRITE, Permission.OWNER, wsi, o);
		PermissionsCheckerFactory.comparePermission(u, Permission.OWNER, Permission.OWNER, wsi, o);
	}
	
	@Test
	public void comparePermissionFail() throws Exception {
		final WorkspaceUser u = new WorkspaceUser("yermum");
		final WorkspaceIdentifier id = new WorkspaceIdentifier(1);
		final WorkspaceIdentifier name = new WorkspaceIdentifier("whee");
		
		failComparePermission(u, Permission.READ, Permission.NONE, id, "moisten",
				new WorkspaceAuthorizationException("User yermum may not moisten workspace 1"));
		
		failComparePermission(u, Permission.OWNER, Permission.ADMIN, name, "frangulate",
				new WorkspaceAuthorizationException(
						"User yermum may not frangulate workspace whee"));
		
		failComparePermission(null, Permission.WRITE, Permission.READ, id, "frot",
				new WorkspaceAuthorizationException(
						"Anonymous users may not frot workspace 1"));
	}
	
	private void failComparePermission(
			final WorkspaceUser user,
			final Permission required,
			final Permission available,
			final WorkspaceIdentifier wsi,
			final String operation,
			final Exception e) {
		try {
			PermissionsCheckerFactory.comparePermission(user, required, available, wsi, operation);
			fail("expected exception");
		} catch (WorkspaceAuthorizationException got) {
			TestCommon.assertExceptionCorrect(got, e);
			assertThat("incorrect denied workspace", got.getDeniedWorkspace(), is(wsi));
		}
	}
	
	@Test
	public void checkReadWorkspacesSuccess() {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(
				db, new WorkspaceUser("foo"));
		
		
	}
	
	
}
