package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
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

import us.kbase.common.test.MapBuilder;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.PermissionsCheckerFactory;
import us.kbase.workspace.database.PermissionsCheckerFactory.SingleObjectPermissionsChecker;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
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
	public void buildFactory() {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		
		PermissionsCheckerFactory fac = new PermissionsCheckerFactory(db, null);
		assertThat("incorrect user", fac.getUser(), is(nullValue()));
		
		fac = new PermissionsCheckerFactory(db, new WorkspaceUser("foo"));
		assertThat("incorrect user", fac.getUser(), is(new WorkspaceUser("foo")));
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
	
	private void failSetOperation(final String op, final Exception e) {
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
	public void defaultOperations() throws Exception {
		// only going to test defaults for one method, since they're set in the abstract class
		defaultOpCheckWorkspacesFailPermission(Permission.READ, "read");
		defaultOpCheckWorkspacesFailPermission(Permission.WRITE, "write to");
		defaultOpCheckWorkspacesFailPermission(Permission.ADMIN, "administrate");
		defaultOpCheckWorkspacesFailPermission(Permission.OWNER, "administrate as an owner");
	}
	
	private void defaultOpCheckWorkspacesFailPermission(
			final Permission perm,
			final String op) throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(3);
		final ResolvedWorkspaceID res = new ResolvedWorkspaceID(3, "yay", false, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi))).thenReturn(ImmutableMap.of(wsi, res));
		
		when(db.getPermissions(u, set(res))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withUnreadableWorkspace(res)
						.build());

		final WorkspaceAuthorizationException e =
				(WorkspaceAuthorizationException) failCheckWorkspaces(
				permfac.getWorkspaceChecker(Arrays.asList(wsi), perm),
				new WorkspaceAuthorizationException("User foo may not " + op + " workspace 3"));
		
		assertThat("incorrect denied workspace", e.getDeniedWorkspace(), is(wsi));
	}
	
	
	/* Multiple workspace checking tests */
	
	@Test
	public void checkWorkspacesSuccessUnlocked() throws Exception {
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
	public void checkWorkspacesSuccessLocked() throws Exception {
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

		final WorkspaceAuthorizationException e =
				(WorkspaceAuthorizationException) failCheckWorkspaces(
				permfac.getWorkspaceChecker(Arrays.asList(wsi), Permission.ADMIN),
				new WorkspaceAuthorizationException("User foo may not administrate workspace 3"));
		
		assertThat("incorrect denied workspace", e.getDeniedWorkspace(), is(wsi));
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

		final WorkspaceAuthorizationException e =
				(WorkspaceAuthorizationException) failCheckWorkspaces(
						permfac.getWorkspaceChecker(Arrays.asList(wsi), Permission.ADMIN)
								.withOperation("tweak"),
				new WorkspaceAuthorizationException("Anonymous users may not tweak workspace 3"));
		
		assertThat("incorrect denied workspace", e.getDeniedWorkspace(), is(wsi));
	}
	
	private Exception failCheckWorkspaces(
			final PermissionsCheckerFactory.WorkspacePermissionsChecker checker,
			final Exception e) {
		try {
			checker.check();
			fail("expected exception");
			return null; // fail always throws an exception
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
			return got;
		}
	}
	
	/* Single workspace checking tests
	 * 
	 * Since the single workspace checker just wraps the multi workspace checker, only the
	 * wrapping code is tested.
	 * 
	 * If you change this arrangement you need to update the test code to take that into account.
	 */
	
	@Test
	public void checkWorkspaceSuccess() throws Exception {
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
		
		final ResolvedWorkspaceID resws = permfac.getWorkspaceChecker(wsi, Permission.WRITE)
				.withOperation("whee")
				.check();
		
		assertThat("incorrect resolved workspaces", resws, is(res));
	}
	
	@Test
	public void checkWorkspaceFailGetBuilder() throws Exception {
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(1);
		
		failGetWorkspaceChecker(null, Permission.READ, new NullPointerException(
				"Workspace identifier cannot be null"));
		failGetWorkspaceChecker(wsi, null, new NullPointerException("perm"));
	}
	
	private void failGetWorkspaceChecker(
			final WorkspaceIdentifier wsi,
			final Permission perm,
			final Exception e) {
		try {
			new PermissionsCheckerFactory(mock(WorkspaceDatabase.class), new WorkspaceUser("f"))
				.getWorkspaceChecker(wsi, perm);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void checkWorkspaceFailPermission() throws Exception {
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

		final WorkspaceAuthorizationException e =
				(WorkspaceAuthorizationException) failCheckWorkspace(
						permfac.getWorkspaceChecker(wsi, Permission.ADMIN)
								.withOperation("tickle"),
				new WorkspaceAuthorizationException("User foo may not tickle workspace 3"));
		
		assertThat("incorrect denied workspace", e.getDeniedWorkspace(), is(wsi));
	}
	
	private Exception failCheckWorkspace(
			final PermissionsCheckerFactory.SingleWorkspacePermissionsChecker checker,
			final Exception e) {
		try {
			checker.check();
			fail("expected exception");
			return null;
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
			return got;
		}
	}
	
	/* Object checking tests */
	
	@Test
	public void checkObjectsSuccessUnlocked() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final WorkspaceIdentifier wsi42 = new WorkspaceIdentifier("thing");
		final ObjectIdentifier obj3 = new ObjectIdentifier(wsi3, 2);
		final ObjectIdentifier obj42 = new ObjectIdentifier(wsi42, "entity");
		final ResolvedWorkspaceID res3 = new ResolvedWorkspaceID(3, "yay", false, false);
		final ResolvedWorkspaceID res42 = new ResolvedWorkspaceID(42, "thing", false, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3, wsi42), false)).thenReturn(ImmutableMap.of(
				wsi3, res3, wsi42, res42));
		
		when(db.getPermissions(u, set(res3, res42))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res3, Permission.WRITE, Permission.NONE)
						.withWorkspace(res42, Permission.ADMIN, Permission.READ)
						.build());
		
		final Map<ObjectIdentifier, ObjectIDResolvedWS> res =
				permfac.getObjectChecker(Arrays.asList(obj3, obj42), Permission.WRITE)
				.withOperation("whee")
				.check();
		
		assertThat("incorrect resolved objects", res, is(ImmutableMap.of(
				obj3, new ObjectIDResolvedWS(res3, 2),
				obj42, new ObjectIDResolvedWS(res42, "entity"))));
	}
	
	@Test
	public void checkObjectsSuccessLocked() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final WorkspaceIdentifier wsi42 = new WorkspaceIdentifier("thing");
		final ObjectIdentifier obj3 = new ObjectIdentifier(wsi3, 2);
		final ObjectIdentifier obj42 = new ObjectIdentifier(wsi42, "entity");
		final ResolvedWorkspaceID res3 = new ResolvedWorkspaceID(3, "yay", true, false);
		final ResolvedWorkspaceID res42 = new ResolvedWorkspaceID(42, "thing", false, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3, wsi42), false)).thenReturn(ImmutableMap.of(
				wsi3, res3, wsi42, res42));
		
		when(db.getPermissions(u, set(res3, res42))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res3, Permission.WRITE, Permission.NONE)
						.withWorkspace(res42, Permission.ADMIN, Permission.READ)
						.build());
		
		final Map<ObjectIdentifier, ObjectIDResolvedWS> res =
				permfac.getObjectChecker(Arrays.asList(obj3, obj42), Permission.READ)
				.withOperation("whee")
				.check();
		
		assertThat("incorrect resolved objects", res, is(ImmutableMap.of(
				obj3, new ObjectIDResolvedWS(res3, 2),
				obj42, new ObjectIDResolvedWS(res42, "entity"))));
	}
	
	@Test
	public void checkObjectsSuccessIgnoreInaccessibleWorkspaces() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final WorkspaceIdentifier wsi42 = new WorkspaceIdentifier("thing");
		final ObjectIdentifier obj3 = new ObjectIdentifier(wsi3, 2);
		final ObjectIdentifier obj42 = new ObjectIdentifier(wsi42, "entity");
		final ResolvedWorkspaceID res3 = new ResolvedWorkspaceID(3, "yay", false, false);
		final ResolvedWorkspaceID res42 = new ResolvedWorkspaceID(42, "thing", false, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3, wsi42), true)).thenReturn(ImmutableMap.of(
				wsi3, res3, wsi42, res42));
		
		when(db.getPermissions(u, set(res3, res42))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res3, Permission.WRITE, Permission.NONE)
						.withWorkspace(res42, Permission.READ, Permission.NONE)
						.build());
		
		final Map<ObjectIdentifier, ObjectIDResolvedWS> res =
				permfac.getObjectChecker(Arrays.asList(obj3, obj42), Permission.WRITE)
						.withOperation("whee")
						.withSuppressErrors(true)
						.check();
		
		assertThat("incorrect resolved objects", res, is(ImmutableMap.of(
				obj3, new ObjectIDResolvedWS(res3, 2))));
	}
	
	@Test
	public void checkObjectsSuccessIgnoreMissingWorkspaces() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final WorkspaceIdentifier wsi42 = new WorkspaceIdentifier("thing");
		final ObjectIdentifier obj3 = new ObjectIdentifier(wsi3, 2);
		final ObjectIdentifier obj42 = new ObjectIdentifier(wsi42, "entity");
		final ResolvedWorkspaceID res3 = new ResolvedWorkspaceID(3, "yay", false, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3, wsi42), true)).thenReturn(ImmutableMap.of(wsi3, res3));
		
		when(db.getPermissions(u, set(res3))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res3, Permission.WRITE, Permission.NONE)
						.build());
		
		final Map<ObjectIdentifier, ObjectIDResolvedWS> res =
				permfac.getObjectChecker(Arrays.asList(obj3, obj42), Permission.WRITE)
						.withOperation("whee")
						.withSuppressErrors(true)
						.check();
		
		assertThat("incorrect resolved objects", res, is(ImmutableMap.of(
				obj3, new ObjectIDResolvedWS(res3, 2))));
	}
	
	@Test
	public void checkObjectsSuccessRemoveDeletedWorkspaces() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final WorkspaceIdentifier wsi42 = new WorkspaceIdentifier("thing");
		final ObjectIdentifier obj3 = new ObjectIdentifier(wsi3, 2);
		final ObjectIdentifier obj42 = new ObjectIdentifier(wsi42, "entity");
		final ResolvedWorkspaceID res3 = new ResolvedWorkspaceID(3, "yay", false, false);
		final ResolvedWorkspaceID res42 = new ResolvedWorkspaceID(42, "thing", false, true);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3, wsi42), true)).thenReturn(
				MapBuilder.<WorkspaceIdentifier, ResolvedWorkspaceID>newHashMap()
				.with(wsi3, res3).with(wsi42, res42).build());
		
		when(db.getPermissions(u, set(res3))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res3, Permission.WRITE, Permission.NONE)
						.build());
		
		final Map<ObjectIdentifier, ObjectIDResolvedWS> res =
				permfac.getObjectChecker(Arrays.asList(obj3, obj42), Permission.WRITE)
						.withOperation("whee")
						.withSuppressErrors(true)
						.check();
		
		assertThat("incorrect resolved objects", res, is(ImmutableMap.of(
				obj3, new ObjectIDResolvedWS(res3, 2))));
	}
	
	@Test
	public void checkObjectsSuccessIncludeDeletedWorkspaces() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final WorkspaceIdentifier wsi42 = new WorkspaceIdentifier("thing");
		final ObjectIdentifier obj3 = new ObjectIdentifier(wsi3, 2);
		final ObjectIdentifier obj42 = new ObjectIdentifier(wsi42, "entity");
		final ResolvedWorkspaceID res3 = new ResolvedWorkspaceID(3, "yay", false, false);
		final ResolvedWorkspaceID res42 = new ResolvedWorkspaceID(42, "thing", false, true);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3, wsi42), true)).thenReturn(
				MapBuilder.<WorkspaceIdentifier, ResolvedWorkspaceID>newHashMap()
				.with(wsi3, res3).with(wsi42, res42).build());
		
		when(db.getPermissions(u, set(res3, res42))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res3, Permission.WRITE, Permission.NONE)
						.withWorkspace(res42, Permission.ADMIN, Permission.NONE)
						.build());
		
		final Map<ObjectIdentifier, ObjectIDResolvedWS> res =
				permfac.getObjectChecker(Arrays.asList(obj3, obj42), Permission.WRITE)
						.withOperation("whee")
						.withIncludeDeletedWorkspaces()
						.check();
		
		assertThat("incorrect resolved objects", res, is(ImmutableMap.of(
				obj3, new ObjectIDResolvedWS(res3, 2),
				obj42, new ObjectIDResolvedWS(res42, "entity"))));
	}
	
	@Test
	public void checkObjectsSuccessResetIncludeDeletedWorkspaces() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final WorkspaceIdentifier wsi42 = new WorkspaceIdentifier("thing");
		final ObjectIdentifier obj3 = new ObjectIdentifier(wsi3, 2);
		final ObjectIdentifier obj42 = new ObjectIdentifier(wsi42, "entity");
		final ResolvedWorkspaceID res3 = new ResolvedWorkspaceID(3, "yay", false, false);
		final ResolvedWorkspaceID res42 = new ResolvedWorkspaceID(42, "thing", false, true);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3, wsi42), true)).thenReturn(
				MapBuilder.<WorkspaceIdentifier, ResolvedWorkspaceID>newHashMap()
				.with(wsi3, res3).with(wsi42, res42).build());
		
		when(db.getPermissions(u, set(res3))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res3, Permission.WRITE, Permission.NONE)
						.build());
		
		final Map<ObjectIdentifier, ObjectIDResolvedWS> res =
				permfac.getObjectChecker(Arrays.asList(obj3, obj42), Permission.WRITE)
						.withOperation("whee")
						.withIncludeDeletedWorkspaces()
						.withSuppressErrors(true)
						.check();
		
		assertThat("incorrect resolved objects", res, is(ImmutableMap.of(
				obj3, new ObjectIDResolvedWS(res3, 2))));
	}
	
	@Test
	public void checkObjectsFailGetBuilder() throws Exception {
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(1);
		final ObjectIdentifier oi = new ObjectIdentifier(wsi, 2);
		final List<ObjectIdentifier> wsis = Arrays.asList(oi);
		
		failGetObjectsChecker(null, Permission.READ, new NullPointerException("objects"));
		failGetObjectsChecker(wsis, null, new NullPointerException("perm"));
		failGetObjectsChecker(Collections.emptyList(), Permission.READ,
				new IllegalArgumentException("No object identifiers provided"));
		failGetObjectsChecker(Arrays.asList(oi, null), Permission.READ,
				new NullPointerException("null object in objects"));
		
	}
	
	private void failGetObjectsChecker(
			final List<ObjectIdentifier> objs,
			final Permission perm,
			final Exception e) {
		try {
			new PermissionsCheckerFactory(mock(WorkspaceDatabase.class), new WorkspaceUser("f"))
				.getObjectChecker(objs, perm);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void checkObjectsFailNoSuchWorkspace() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final WorkspaceIdentifier wsi42 = new WorkspaceIdentifier("thing");
		final ObjectIdentifier obj3 = new ObjectIdentifier(wsi3, 2);
		final ObjectIdentifier obj42 = new ObjectIdentifier(wsi42, "entity");
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3, wsi42), false))
				.thenThrow(new NoSuchWorkspaceException("foo", wsi3));
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failCheckObjects(
				permfac.getObjectChecker(Arrays.asList(obj3, obj42), Permission.WRITE),
				new InaccessibleObjectException("Object 2 cannot be accessed: foo", obj3));
		
		assertThat("incorrect denied object", e.getInaccessibleObject(), is(obj3));
	}
	
	@Test
	public void checkObjectsFailLocked() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final WorkspaceIdentifier wsi42 = new WorkspaceIdentifier("thing");
		final ObjectIdentifier obj3 = new ObjectIdentifier(wsi3, 2);
		final ObjectIdentifier obj42 = new ObjectIdentifier(wsi42, "entity");
		final ResolvedWorkspaceID res3 = new ResolvedWorkspaceID(3, "yay", true, false);
		final ResolvedWorkspaceID res42 = new ResolvedWorkspaceID(42, "thing", false, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3, wsi42), false)).thenReturn(
				MapBuilder.<WorkspaceIdentifier, ResolvedWorkspaceID>newHashMap()
				.with(wsi3, res3).with(wsi42, res42).build());
		
		when(db.getPermissions(u, set(res3, res42))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res3, Permission.WRITE, Permission.NONE)
						.withWorkspace(res42, Permission.WRITE, Permission.NONE)
						.build());
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failCheckObjects(
				permfac.getObjectChecker(Arrays.asList(obj3, obj42), Permission.WRITE),
				new InaccessibleObjectException("Object 2 cannot be accessed: " +
						"The workspace with id 3, name yay, is locked and may not be modified",
						obj3));
		
		assertThat("incorrect denied object", e.getInaccessibleObject(), is(obj3));
	}
	
	@Test
	public void checkObjectsFailPermissions() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final WorkspaceIdentifier wsi42 = new WorkspaceIdentifier("thing");
		final ObjectIdentifier obj3 = new ObjectIdentifier(wsi3, 2);
		final ObjectIdentifier obj42 = new ObjectIdentifier(wsi42, "entity");
		// locked to test that permissions errors take precedence over locking
		final ResolvedWorkspaceID res3 = new ResolvedWorkspaceID(3, "yay", true, false);
		final ResolvedWorkspaceID res42 = new ResolvedWorkspaceID(42, "thing", false, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3, wsi42), false)).thenReturn(
				MapBuilder.<WorkspaceIdentifier, ResolvedWorkspaceID>newHashMap()
				.with(wsi3, res3).with(wsi42, res42).build());
		
		when(db.getPermissions(u, set(res3, res42))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res3, Permission.READ, Permission.NONE)
						.withWorkspace(res42, Permission.WRITE, Permission.NONE)
						.build());
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failCheckObjects(
				permfac.getObjectChecker(Arrays.asList(obj3, obj42), Permission.WRITE),
				new InaccessibleObjectException("Object 2 cannot be accessed: " +
						"User foo may not write to workspace 3",
						obj3));
		
		assertThat("incorrect denied object", e.getInaccessibleObject(), is(obj3));
	}
	
	@Test
	public void checkObjectsFailPermissionsResetSuppressErrors() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final WorkspaceIdentifier wsi42 = new WorkspaceIdentifier("thing");
		final ObjectIdentifier obj3 = new ObjectIdentifier(wsi3, 2);
		final ObjectIdentifier obj42 = new ObjectIdentifier(wsi42, "entity");
		// locked to test that permissions errors take precedence over locking
		final ResolvedWorkspaceID res3 = new ResolvedWorkspaceID(3, "yay", true, false);
		final ResolvedWorkspaceID res42 = new ResolvedWorkspaceID(42, "thing", false, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3, wsi42), false)).thenReturn(
				MapBuilder.<WorkspaceIdentifier, ResolvedWorkspaceID>newHashMap()
				.with(wsi3, res3).with(wsi42, res42).build());
		
		when(db.getPermissions(u, set(res3, res42))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res3, Permission.READ, Permission.NONE)
						.withWorkspace(res42, Permission.WRITE, Permission.NONE)
						.build());
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failCheckObjects(
				permfac.getObjectChecker(Arrays.asList(obj3, obj42), Permission.WRITE)
						.withIncludeDeletedWorkspaces()
						.withSuppressErrors(true)
						.withSuppressErrors(false),
				new InaccessibleObjectException("Object 2 cannot be accessed: " +
						"User foo may not write to workspace 3",
						obj3));
		
		assertThat("incorrect denied object", e.getInaccessibleObject(), is(obj3));
	}
	
	@Test
	public void checkObjectsFailPermissionsAnonUserWithCustomOp() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = null;
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final WorkspaceIdentifier wsi42 = new WorkspaceIdentifier("thing");
		final ObjectIdentifier obj3 = new ObjectIdentifier(wsi3, 2);
		final ObjectIdentifier obj42 = new ObjectIdentifier(wsi42, "entity");
		// locked to test that permissions errors take precedence over locking
		final ResolvedWorkspaceID res3 = new ResolvedWorkspaceID(3, "yay", true, false);
		final ResolvedWorkspaceID res42 = new ResolvedWorkspaceID(42, "thing", false, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3, wsi42), false)).thenReturn(
				MapBuilder.<WorkspaceIdentifier, ResolvedWorkspaceID>newHashMap()
				.with(wsi3, res3).with(wsi42, res42).build());
		
		when(db.getPermissions(u, set(res3, res42))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res3, Permission.NONE, Permission.READ)
						.withWorkspace(res42, Permission.NONE, Permission.READ)
						.build());
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failCheckObjects(
				permfac.getObjectChecker(Arrays.asList(obj3, obj42), Permission.WRITE)
				.withOperation("slobber on"),
				new InaccessibleObjectException("Object 2 cannot be accessed: " +
						"Anonymous users may not slobber on workspace 3",
						obj3));
		
		assertThat("incorrect denied object", e.getInaccessibleObject(), is(obj3));
	}
	
	private Exception failCheckObjects(
			final PermissionsCheckerFactory.ObjectPermissionsChecker checker,
			final Exception e) {
		try {
			checker.check();
			fail("expected exception");
			return null; // fail always throws an exception
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
			return got;
		}
	}
	
	/* Single object checking tests
	 * 
	 * Since the single objects checker just wraps the multi objects checker, only the
	 * wrapping code is tested.
	 * 
	 * If you change this arrangement you need to update the test code to take that into account.
	 */
	
	@Test
	public void checkObjectSuccess() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final ObjectIdentifier obj3 = new ObjectIdentifier(wsi3, 2);
		final ResolvedWorkspaceID res3 = new ResolvedWorkspaceID(3, "yay", false, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3), false)).thenReturn(ImmutableMap.of(wsi3, res3));
		
		when(db.getPermissions(u, set(res3))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res3, Permission.WRITE, Permission.NONE)
						.build());
		
		final ObjectIDResolvedWS res = permfac.getObjectChecker(obj3, Permission.WRITE).check();
		
		assertThat("incorrect resolved object", res, is(new ObjectIDResolvedWS(res3, 2)));
	}
	
	
	@Test
	public void checkObjectFailUnsupportedMethods() {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final ObjectIdentifier obj3 = new ObjectIdentifier(wsi3, 2);
		
		final SingleObjectPermissionsChecker checker = new PermissionsCheckerFactory(db, u)
				.getObjectChecker(obj3, Permission.READ);
		final UnsupportedOperationException expected = new UnsupportedOperationException(
				"Unsupported for single objects");
		
		try {
			checker.withIncludeDeletedWorkspaces();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
		
		try {
			checker.withSuppressErrors(false);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void checkObjectFailGetBuilder() throws Exception {
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(1);
		final ObjectIdentifier oi = new ObjectIdentifier(wsi, 2);
		
		failGetObjectChecker(null, Permission.READ, new NullPointerException(
				"Object identifier cannot be null"));
		failGetObjectChecker(oi, null, new NullPointerException("perm"));
	}
	
	private void failGetObjectChecker(
			final ObjectIdentifier oi,
			final Permission perm,
			final Exception e) {
		try {
			new PermissionsCheckerFactory(mock(WorkspaceDatabase.class), new WorkspaceUser("f"))
				.getObjectChecker(oi, perm);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void checkObjectFailPermissions() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final WorkspaceUser u = new WorkspaceUser("foo");
		final WorkspaceIdentifier wsi3 = new WorkspaceIdentifier(3);
		final ObjectIdentifier obj3 = new ObjectIdentifier(wsi3, 2);
		// locked to test that permissions errors take precedence over locking
		final ResolvedWorkspaceID res3 = new ResolvedWorkspaceID(3, "yay", true, false);
		
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, u);
		
		when(db.resolveWorkspaces(set(wsi3), false)).thenReturn(
				MapBuilder.<WorkspaceIdentifier, ResolvedWorkspaceID>newHashMap()
				.with(wsi3, res3).build());
		
		when(db.getPermissions(u, set(res3))).thenReturn(
				PermissionSet.getBuilder(u, new AllUsers('*'))
						.withWorkspace(res3, Permission.READ, Permission.NONE)
						.build());
		
		final InaccessibleObjectException e = (InaccessibleObjectException) failCheckObject(
				permfac.getObjectChecker(obj3, Permission.WRITE)
				.withOperation("bamboozle"),
				new InaccessibleObjectException("Object 2 cannot be accessed: " +
						"User foo may not bamboozle workspace 3",
						obj3));
		
		assertThat("incorrect denied object", e.getInaccessibleObject(), is(obj3));
	}
	
	private Exception failCheckObject(
			final PermissionsCheckerFactory.SingleObjectPermissionsChecker checker,
			final Exception e) {
		try {
			checker.check();
			fail("expected exception");
			return null; // fail always throws an exception
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
			return got;
		}
	}
	
}
