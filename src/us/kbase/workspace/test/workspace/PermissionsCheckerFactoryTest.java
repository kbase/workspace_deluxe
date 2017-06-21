package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.set;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
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
	public void buildFactoryFail() {
		try {
			new PermissionsCheckerFactory(null, null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("db"));
		}
	}
	
	@Test
	public void setOperationFail() {
		failSetOperation(null, new IllegalArgumentException(
				"operation cannot be null or the empty string"));
		failSetOperation("", new IllegalArgumentException(
				"operation cannot be null or the empty string"));
		//TODO CODE add whitespace when java common string checker deals with whitespace
	}
	
	public void failSetOperation(final String op, final Exception e) {
		try {
			// just test on the workspace checker since they all inherit from the same abstract
			// class
			new PermissionsCheckerFactory(mock(WorkspaceDatabase.class), new WorkspaceUser("f"))
					.getWorkspaceChecker(
							Arrays.asList(new WorkspaceIdentifier(1)), Permission.ADMIN)
					.withOperation(op);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void checkReadWorkspacesSuccessUnlocked() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final WorkspaceIdentifier wsi42 = new WorkspaceIdentifier("thing");
		final ResolvedWorkspaceID res3 = new ResolvedWorkspaceID(3, "yay", false, false);
		final ResolvedWorkspaceID res42 = new ResolvedWorkspaceID(42, "thing", false, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3, wsi42))).thenReturn(ImmutableMap.of(
				wsi3, res3, wsi42, res42));
		
		when(db.getPermissions(u, set(res3, res42))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res3, Permission.WRITE, Permission.NONE)
						.withWorkspace(res42, Permission.ADMIN, Permission.READ)
						.build());
		
		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> res =
				permfac.getWorkspaceChecker(Arrays.asList(wsi3, wsi42), Permission.WRITE)
				.withOperation("whee")
				.check();
		
		assertThat("incorrect resolved workspaces", res,
				is(ImmutableMap.of(wsi3, res3, wsi42, res42)));
	}
	
	@Test
	public void checkReadWorkspacesSuccessLocked() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final WorkspaceIdentifier wsi42 = new WorkspaceIdentifier("thing");
		final ResolvedWorkspaceID res3 = new ResolvedWorkspaceID(3, "yay", true, false);
		final ResolvedWorkspaceID res42 = new ResolvedWorkspaceID(42, "thing", false, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3, wsi42))).thenReturn(ImmutableMap.of(
				wsi3, res3, wsi42, res42));
		
		when(db.getPermissions(u, set(res3, res42))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res3, Permission.WRITE, Permission.NONE)
						.withWorkspace(res42, Permission.ADMIN, Permission.READ)
						.build());
		
		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> res =
				permfac.getWorkspaceChecker(Arrays.asList(wsi3, wsi42), Permission.READ)
				.withOperation("whee")
				.check();
		
		assertThat("incorrect resolved workspaces", res,
				is(ImmutableMap.of(wsi3, res3, wsi42, res42)));
	}
	
	@Test
	public void checkWorkspacesFailGetBuilder() throws Exception {
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(1);
		final List<WorkspaceIdentifier> wsis = Arrays.asList(wsi);
		
		failGetWorkspacesChecker(null, Permission.READ, new NullPointerException("workspaces"));
		failGetWorkspacesChecker(wsis, null, new NullPointerException("perm"));
		failGetWorkspacesChecker(Collections.emptyList(), Permission.READ,
				new IllegalArgumentException("No workspace identifiers provided"));
		failGetWorkspacesChecker(Arrays.asList(wsi, null), Permission.READ,
				new NullPointerException("null object in workspaces"));
		
	}
	
	private void failGetWorkspacesChecker(
			final List<WorkspaceIdentifier> wsis,
			final Permission perm,
			final Exception e) {
		try {
			new PermissionsCheckerFactory(mock(WorkspaceDatabase.class), new WorkspaceUser("f"))
				.getWorkspaceChecker(wsis, perm);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void checkWorkspacesFailLocked() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(3);
		final ResolvedWorkspaceID res = new ResolvedWorkspaceID(3, "yay", true, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, res));
		
		when(db.getPermissions(u, set(res))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res, Permission.WRITE, Permission.NONE)
						.build());

		failCheckWorkspaces(permfac.getWorkspaceChecker(Arrays.asList(wsi), Permission.WRITE)
				.withOperation("whee"), new WorkspaceAuthorizationException(
						"The workspace with id 3, name yay, is locked and may not be modified"));
	}
	
	@Test
	public void checkWorkspacesFailPermission() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(3);
		final ResolvedWorkspaceID res = new ResolvedWorkspaceID(3, "yay", false, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, res));
		
		when(db.getPermissions(u, set(res))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res, Permission.WRITE, Permission.NONE)
						.build());

		failCheckWorkspaces(permfac.getWorkspaceChecker(Arrays.asList(wsi), Permission.ADMIN),
				new WorkspaceAuthorizationException(
						"User foo may not administrate workspace 3"));
	}
	
	@Test
	public void checkWorkspacesFailPermissionAnonUserAndCustomOp() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = null;
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(3);
		final ResolvedWorkspaceID res = new ResolvedWorkspaceID(3, "yay", false, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, res));
		
		when(db.getPermissions(u, set(res))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res, Permission.NONE, Permission.READ)
						.build());

		failCheckWorkspaces(permfac.getWorkspaceChecker(Arrays.asList(wsi), Permission.ADMIN)
				.withOperation("tweak"),
				new WorkspaceAuthorizationException(
						"Anonymous users may not tweak workspace 3"));
	}
	
	
	private void failCheckWorkspaces(
			final PermissionsCheckerFactory.WorkspacePermissionsChecker checker,
			final Exception e) {
		try {
			checker.check();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	
}
