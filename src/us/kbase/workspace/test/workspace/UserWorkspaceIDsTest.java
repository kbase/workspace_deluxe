package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

import org.junit.Test;

import com.google.common.base.Optional;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.UserWorkspaceIDs;
import us.kbase.workspace.database.WorkspaceUser;

public class UserWorkspaceIDsTest {
	
	@Test
	public void equals() {
		EqualsVerifier.forClass(UserWorkspaceIDs.class).usingGetClass().verify();
	}
	
	@Test
	public void minimalConstructor() {
		final UserWorkspaceIDs uwi = new UserWorkspaceIDs(
				null, Permission.READ, Collections.emptyList(), Collections.emptyList());
		assertThat("incorrect user", uwi.getUser(), is(Optional.absent()));
		assertThat("incorrect perm", uwi.getPerm(), is(Permission.READ));
		assertThat("incorrect wsids", uwi.getWorkspaceIDs(), is(new TreeSet<>()));
		assertThat("incorrect pub wsids", uwi.getPublicWorkspaceIDs(),
				is(new HashSet<>()));
		assertThat("incorrect toString", uwi.toString(), is(
				"UserWorkspaceIDs [workspaceIDs=[], publicWorkspaceIDs=[], " +
				"user=Optional.absent(), perm=READ]"));
	}
	
	@Test
	public void fullConstructor() {
		final UserWorkspaceIDs uwi = new UserWorkspaceIDs(
				new WorkspaceUser("foo"), Permission.ADMIN, Arrays.asList(9L, 7L, 3L, 5L, 9L),
				Arrays.asList(9L, 5L, 6L, 5L));
		assertThat("incorrect user", uwi.getUser(), is(Optional.of(new WorkspaceUser("foo"))));
		assertThat("incorrect perm", uwi.getPerm(), is(Permission.ADMIN));
		assertThat("incorrect wsids", uwi.getWorkspaceIDs(),
				is(new TreeSet<>(Arrays.asList(3L, 5L, 7L, 9L))));
		assertThat("incorrect pub wsids", uwi.getPublicWorkspaceIDs(),
				is(new TreeSet<>(Arrays.asList(5L, 6L, 9L))));
		assertThat("incorrect toString", uwi.toString(), is(
				"UserWorkspaceIDs [workspaceIDs=[3, 5, 7, 9], publicWorkspaceIDs=[5, 6, 9], " +
				"user=Optional.of(User [user=foo]), perm=ADMIN]"));
	}
	
	@Test
	public void failOnNulls() {
		failConstruct(new WorkspaceUser("foo"), null, Collections.emptyList(),
				Collections.emptyList(), "perm");
		failConstruct(new WorkspaceUser("foo"), Permission.WRITE, null,
				Collections.emptyList(), "workspaceIDs");
		failConstruct(new WorkspaceUser("foo"), Permission.WRITE, Arrays.asList(4L, null),
				Collections.emptyList(), "null item in workspaceIDs");
		failConstruct(new WorkspaceUser("foo"), Permission.WRITE, Collections.emptyList(),
				null, "publicWorkspaceIDs");
		failConstruct(new WorkspaceUser("foo"), Permission.WRITE, Collections.emptyList(),
				Arrays.asList(6L, null), "null item in publicWorkspaceIDs");
	}

	private void failConstruct(
			final WorkspaceUser user,
			final Permission perm,
			final List<Long> wsids,
			final List<Long> pubids,
			final String exception) {
		try {
			new UserWorkspaceIDs(user, perm, wsids, pubids);
			fail("expected exception");
		} catch (NullPointerException e) {
			TestCommon.assertExceptionCorrect(e, new NullPointerException(exception));
		}
	}
	
	@Test
	public void immutable() {
		final UserWorkspaceIDs uwi = new UserWorkspaceIDs(
				new WorkspaceUser("foo"), Permission.ADMIN, Arrays.asList(2L, 7L, 9L),
				Arrays.asList(9L, 5L, 6L));
		
		try {
			uwi.getWorkspaceIDs().add(8L);
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// passes
		}
		try {
			uwi.getPublicWorkspaceIDs().add(8L);
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// passes
		}
	}
	

}
