package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import static us.kbase.common.test.TestCommon.set;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.PermissionSet.Builder;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceUser;

public class PermissionsSetTest {
	
	private static final ResolvedWorkspaceID RWSID1 =
			new ResolvedWorkspaceID(1, "someworkspace1", false, false);
	private static final ResolvedWorkspaceID RWSID2 =
			new ResolvedWorkspaceID(2, "someworkspace2", false, false);
	private static final ResolvedWorkspaceID RWSID3 =
			new ResolvedWorkspaceID(3, "someworkspace3", false, false);
	private static final ResolvedWorkspaceID RWSID4 =
			new ResolvedWorkspaceID(4, "someworkspace4", false, false);
	private static final ResolvedWorkspaceID RWSID5 =
			new ResolvedWorkspaceID(5, "someworkspace5", false, false);
	private static final ResolvedWorkspaceID RWSID6 =
			new ResolvedWorkspaceID(6, "someworkspace6", false, false);
	
	@Test
	public void buildEmpty() {
		final PermissionSet p = PermissionSet.getBuilder(
				new WorkspaceUser("foo"), new AllUsers('*')).build();
		
		assertThat("incorrect user", p.getUser(), is(new WorkspaceUser("foo")));
		assertThat("incorrect global user", p.getGlobalUser(), is(new AllUsers('*')));
		
		assertThat("incorrect workspaces", p.getWorkspaces(), is(set()));
		assertThat("incorrect is empty", p.isEmpty(), is(true));
		
		assertThat("incorrect has ws", p.hasWorkspace(RWSID1), is(false));
		
		assertThat("incorrect get perm", p.getPermission(RWSID1), is(Permission.NONE));
		assertThat("incorrect get user perm", p.getUserPermission(RWSID1), is(Permission.NONE));
		assertThat("incorrect world read", p.isWorldReadable(RWSID1), is(false));
		
		assertThat("incorrect has perm", p.hasPermission(RWSID1, Permission.READ), is(false));
		assertThat("incorrect has user perm", p.hasUserPermission(RWSID1, Permission.READ),
				is(false));
	}
	
	@Test
	public void buildWithNullUser() {
		final PermissionSet p = PermissionSet.getBuilder(
				null, new AllUsers('*'))
				.withUnreadableWorkspace(RWSID1)
				.withWorkspace(RWSID2, Permission.NONE, Permission.READ)
				.build();
		
		assertThat("incorrect user", p.getUser(), is(nullValue()));
		assertThat("incorrect global user", p.getGlobalUser(), is(new AllUsers('*')));
		
		assertThat("incorrect workspaces", p.getWorkspaces(), is(set(RWSID1, RWSID2)));
		assertThat("incorrect is empty", p.isEmpty(), is(false));
		
		assertThat("incorrect has ws", p.hasWorkspace(RWSID1), is(true));
		assertThat("incorrect has ws", p.hasWorkspace(RWSID2), is(true));
		assertThat("incorrect has ws", p.hasWorkspace(RWSID3), is(false));
		
		assertThat("incorrect get perm", p.getPermission(RWSID1), is(Permission.NONE));
		assertThat("incorrect get user perm", p.getUserPermission(RWSID1), is(Permission.NONE));
		assertThat("incorrect world read", p.isWorldReadable(RWSID1), is(false));
		
		assertThat("incorrect has perm", p.hasPermission(RWSID1, Permission.READ), is(false));
		assertThat("incorrect has user perm", p.hasUserPermission(RWSID1, Permission.READ),
				is(false));
		
		assertThat("incorrect get perm", p.getPermission(RWSID2), is(Permission.READ));
		assertThat("incorrect get user perm", p.getUserPermission(RWSID2), is(Permission.NONE));
		assertThat("incorrect world read", p.isWorldReadable(RWSID2), is(true));
		
		assertThat("incorrect has perm", p.hasPermission(RWSID2, Permission.READ), is(true));
		assertThat("incorrect has perm", p.hasPermission(RWSID2, Permission.WRITE), is(false));
		assertThat("incorrect has user perm", p.hasUserPermission(RWSID2, Permission.READ),
				is(false));
	}
	
	@Test
	public void buildWithVariousPermissions() {
		final PermissionSet p = PermissionSet.getBuilder(
				new WorkspaceUser("foo"), new AllUsers('*'))
				.withWorkspace(RWSID1, Permission.NONE, Permission.READ)
				.withWorkspace(RWSID2, Permission.READ, Permission.NONE)
				.withWorkspace(RWSID3, Permission.WRITE, Permission.READ)
				.withWorkspace(RWSID4, Permission.ADMIN, Permission.NONE)
				.withWorkspace(RWSID5, Permission.OWNER, Permission.NONE)
				.build();
		
		assertThat("incorrect user", p.getUser(), is(new WorkspaceUser("foo")));
		assertThat("incorrect global user", p.getGlobalUser(), is(new AllUsers('*')));
		
		assertThat("incorrect workspaces", p.getWorkspaces(),
				is(set(RWSID1, RWSID2, RWSID3, RWSID4, RWSID5)));
		assertThat("incorrect is empty", p.isEmpty(), is(false));
		
		assertThat("incorrect has ws", p.hasWorkspace(RWSID6), is(false));
		
		checkPerms(p, RWSID1, Permission.READ, Permission.NONE, true,
				Arrays.asList(true, true, false, false, false),
				Arrays.asList(true, false, false, false, false));
		
		checkPerms(p, RWSID2, Permission.READ, Permission.READ, false,
				Arrays.asList(true, true, false, false, false),
				Arrays.asList(true, true, false, false, false));
		
		checkPerms(p, RWSID3, Permission.WRITE, Permission.WRITE, true,
				Arrays.asList(true, true, true, false, false),
				Arrays.asList(true, true, true, false, false));
		
		checkPerms(p, RWSID4, Permission.ADMIN, Permission.ADMIN, false,
				Arrays.asList(true, true, true, true, false),
				Arrays.asList(true, true, true, true, false));
		
		checkPerms(p, RWSID5, Permission.OWNER, Permission.OWNER, false,
				Arrays.asList(true, true, true, true, true),
				Arrays.asList(true, true, true, true, true));
	}
	
	@Test
	public void buildWithNullPermissions() {
		// only tests specific effects of null permissions
		final PermissionSet p = PermissionSet.getBuilder(
				new WorkspaceUser("foo"), new AllUsers('*'))
				.withWorkspace(RWSID1, null, Permission.READ)
				.withWorkspace(RWSID2, Permission.READ, null)
				.build();
		
		checkPerms(p, RWSID1, Permission.READ, Permission.NONE, true,
				Arrays.asList(true, true, false, false, false),
				Arrays.asList(true, false, false, false, false));
		
		checkPerms(p, RWSID2, Permission.READ, Permission.READ, false,
				Arrays.asList(true, true, false, false, false),
				Arrays.asList(true, true, false, false, false));
	}

	private void checkPerms(
			final PermissionSet p,
			final ResolvedWorkspaceID ws,
			final Permission perm,
			final Permission userperm,
			final boolean worldreadable,
			final List<Boolean> perms,
			final List<Boolean> userperms) {
		
		assertThat("incorrect get perm", p.getPermission(ws), is(perm));
		assertThat("incorrect get user perm", p.getUserPermission(ws), is(userperm));
		assertThat("incorrect world read", p.isWorldReadable(ws), is(worldreadable));
		
		for (int i = 0; i < Permission.values().length; i++) {
			assertThat("incorrect has perm", p.hasPermission(ws, Permission.values()[i]),
					is(perms.get(i)));
			assertThat("incorrect has user perm", p.hasUserPermission(ws, Permission.values()[i]),
					is(userperms.get(i)));
		}
	}
	
	@Test
	public void string() {
		final PermissionSet p = PermissionSet.getBuilder(
				new WorkspaceUser("foo"), new AllUsers('*'))
				// only test one item since would need to sort the internal map for consistent
				// results
				.withWorkspace(RWSID1, Permission.WRITE, Permission.READ)
				.build();
		
		assertThat("incorrect toString", p.toString(),
				is("PermissionSet [user=User [user=foo], globalUser=AllUsers [user=*], " +
						"perms={ResolvedWorkspaceID [id=1, wsname=someworkspace1, locked=false, " +
						"deleted=false]=Perms [perm=WRITE, worldRead=true]}]"));
	}
	
	@Test
	public void builderHasWorkspace() {
		final Builder p = PermissionSet.getBuilder(
				new WorkspaceUser("foo"), new AllUsers('*'))
				.withWorkspace(RWSID1, Permission.NONE, Permission.READ)
				.withWorkspace(RWSID5, Permission.OWNER, Permission.NONE);
		
		assertThat("incorrect has ws", p.hasWorkspace(RWSID1), is(true));
		assertThat("incorrect has ws", p.hasWorkspace(RWSID2), is(false));
		assertThat("incorrect has ws", p.hasWorkspace(RWSID5), is(true));
	}
	
	@Test
	public void buildFailStart() {
		try {
			PermissionSet.getBuilder(null, null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"Global user cannot be null"));
		}
	}
	
	@Test
	public void buildFailAddWorkspace() {
		final Builder p = PermissionSet.getBuilder(new WorkspaceUser("foo"), new AllUsers('*'))
				.withWorkspace(RWSID2, Permission.READ, Permission.NONE);
		
		failBuildWithWorkspace(p, null, Permission.READ, Permission.NONE,
				new IllegalArgumentException("Workspace ID cannot be null"));
		failBuildWithWorkspace(p, RWSID2, Permission.READ, Permission.NONE,
				new IllegalArgumentException("Permissions for workspace 2 have already been set"));
		
		failBuildWithWorkspace(p, RWSID1, Permission.NONE, Permission.NONE,
				new IllegalArgumentException("Cannot add unreadable workspace"));
		failBuildWithWorkspace(p, RWSID1, null, null,
				new IllegalArgumentException("Cannot add unreadable workspace"));
		
		failBuildWithWorkspace(p, RWSID1, Permission.READ, Permission.WRITE,
				new IllegalArgumentException("Illegal global permission: WRITE"));
		failBuildWithWorkspace(p, RWSID1, Permission.READ, Permission.ADMIN,
				new IllegalArgumentException("Illegal global permission: ADMIN"));
		failBuildWithWorkspace(p, RWSID1, Permission.READ, Permission.OWNER,
				new IllegalArgumentException("Illegal global permission: OWNER"));
	}
	
	@Test
	public void buildFailAddWorkspaceAnonUser() {
		final Builder p = PermissionSet.getBuilder(null, new AllUsers('*'));
		
		failBuildWithWorkspace(p, RWSID1, Permission.READ, Permission.READ,
				new IllegalArgumentException(
						"anonymous users can't have user specific permissions"));
		failBuildWithWorkspace(p, RWSID1, Permission.WRITE, Permission.READ,
				new IllegalArgumentException(
						"anonymous users can't have user specific permissions"));
		failBuildWithWorkspace(p, RWSID1, Permission.ADMIN, Permission.READ,
				new IllegalArgumentException(
						"anonymous users can't have user specific permissions"));
		failBuildWithWorkspace(p, RWSID1, Permission.OWNER, Permission.READ,
				new IllegalArgumentException(
						"anonymous users can't have user specific permissions"));
	}
	
	private void failBuildWithWorkspace(
			final Builder p,
			final ResolvedWorkspaceID ws,
			final Permission userPerm,
			final Permission globalPerm,
			final Exception e) {
		try {
			p.withWorkspace(ws, userPerm, globalPerm);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void buildFailAddUnreadableWorkspace() {
		final Builder p = PermissionSet.getBuilder(new WorkspaceUser("foo"), new AllUsers('*'))
				.withWorkspace(RWSID2, Permission.READ, Permission.NONE);
		
		failBuildWithUnreadableWorkspace(p, null,
				new IllegalArgumentException("Workspace ID cannot be null"));
		failBuildWithUnreadableWorkspace(p, RWSID2,
				new IllegalArgumentException("Permissions for workspace 2 have already been set"));
	}
	
	private void failBuildWithUnreadableWorkspace(
			final Builder p,
			final ResolvedWorkspaceID ws,
			final Exception e) {
		try {
			p.withUnreadableWorkspace(ws);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
			

}
