package us.kbase.test.workspace.kbase.admin;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static us.kbase.test.common.TestCommon.set;

import org.junit.Test;

import us.kbase.auth.AuthToken;
import us.kbase.test.common.TestCommon;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.kbase.admin.AdminRole;
import us.kbase.workspace.kbase.admin.AdministratorHandlerException;
import us.kbase.workspace.kbase.admin.DefaultAdminHandler;

public class DefaultAdminHandlerTest {
	
	@Test
	public void failConstruct() {
		try {
			new DefaultAdminHandler(null, null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("ws"));
		}
	}
	
	@Test
	public void listAdminsNoStartupAdmin() throws Exception {
		final Workspace ws = mock(Workspace.class);
		final DefaultAdminHandler dah = new DefaultAdminHandler(ws, null);
		
		when(ws.getAdmins()).thenReturn(set(new WorkspaceUser("foo"), new WorkspaceUser("bar")));
		
		assertThat("incorrect admins", dah.getAdmins(), is(
				set(new WorkspaceUser("foo"), new WorkspaceUser("bar"))));
	}
	
	@Test
	public void listAdminsWithStartupAdmin() throws Exception {
		final Workspace ws = mock(Workspace.class);
		final DefaultAdminHandler dah = new DefaultAdminHandler(ws, new WorkspaceUser("baz"));
		
		when(ws.getAdmins()).thenReturn(set(new WorkspaceUser("foo"), new WorkspaceUser("bar")));
		
		assertThat("incorrect admins", dah.getAdmins(), is(
				set(new WorkspaceUser("foo"), new WorkspaceUser("bar"),
						new WorkspaceUser("baz"))));
	}
	
	@Test
	public void failListAdmins() throws Exception {
		final Workspace ws = mock(Workspace.class);
		final DefaultAdminHandler dah = new DefaultAdminHandler(ws, new WorkspaceUser("baz"));
		
		when(ws.getAdmins()).thenThrow(new WorkspaceCommunicationException("whee"));
		
		try {
			dah.getAdmins();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new AdministratorHandlerException(
					"Couldn't retrieve list of administrators: whee"));
		}
	}
	
	@Test
	public void addAdmin() throws Exception {
		final Workspace ws = mock(Workspace.class);
		final DefaultAdminHandler dah = new DefaultAdminHandler(ws, new WorkspaceUser("baz"));
		
		dah.addAdmin(new WorkspaceUser("foo"));
		
		verify(ws).addAdmin(new WorkspaceUser("foo"));
	}
	
	@Test
	public void failAddAdmin() throws Exception {
		final Workspace ws = mock(Workspace.class);
		final DefaultAdminHandler dah = new DefaultAdminHandler(ws, new WorkspaceUser("baz"));
		
		doThrow(new WorkspaceCommunicationException("foo"))
				.when(ws).addAdmin(new WorkspaceUser("foo"));
		
		try {
			dah.addAdmin(new WorkspaceUser("foo"));
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new AdministratorHandlerException(
					"Couldn't add administrator: foo"));
		}
	}
	
	@Test
	public void removeAdmin() throws Exception {
		final Workspace ws = mock(Workspace.class);
		final DefaultAdminHandler dah = new DefaultAdminHandler(ws, new WorkspaceUser("baz"));
		
		dah.removeAdmin(new WorkspaceUser("foo"));
		
		verify(ws).removeAdmin(new WorkspaceUser("foo"));
	}

	@Test
	public void failRemoveAdmin() throws Exception {
		final Workspace ws = mock(Workspace.class);
		final DefaultAdminHandler dah = new DefaultAdminHandler(ws, new WorkspaceUser("baz"));
		
		doThrow(new WorkspaceCommunicationException("foo"))
				.when(ws).removeAdmin(new WorkspaceUser("foo"));
		
		try {
			dah.removeAdmin(new WorkspaceUser("foo"));
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new AdministratorHandlerException(
					"Couldn't remove administrator: foo"));
		}
	}
	
	@Test
	public void getAdminRoleWithMatchingStartupAdmin() throws Exception {
		final Workspace ws = mock(Workspace.class);
		final DefaultAdminHandler dah = new DefaultAdminHandler(ws, new WorkspaceUser("baz"));
		
		assertThat("incorrect role", dah.getAdminRole(new AuthToken("fake", "baz")),
				is(AdminRole.ADMIN));
	}
	
	@Test
	public void getAdminRoleWithoutMatchingStartupAdmin() throws Exception {
		final Workspace ws = mock(Workspace.class);
		final DefaultAdminHandler dah = new DefaultAdminHandler(ws, new WorkspaceUser("baz"));
		
		when(ws.isAdmin(new WorkspaceUser("bar"))).thenReturn(true);
		
		assertThat("incorrect role", dah.getAdminRole(new AuthToken("fake", "bar")),
				is(AdminRole.ADMIN));
	}
	
	@Test
	public void getAdminRoleWithoutStartupAdmin() throws Exception {
		final Workspace ws = mock(Workspace.class);
		final DefaultAdminHandler dah = new DefaultAdminHandler(ws, null);
		
		when(ws.isAdmin(new WorkspaceUser("bar"))).thenReturn(true);
		
		assertThat("incorrect role", dah.getAdminRole(new AuthToken("fake", "bar")),
				is(AdminRole.ADMIN));
	}
	
	@Test
	public void getAdminRoleWithNoneRole() throws Exception {
		final Workspace ws = mock(Workspace.class);
		final DefaultAdminHandler dah = new DefaultAdminHandler(ws, new WorkspaceUser("baz"));
		
		when(ws.isAdmin(new WorkspaceUser("bar"))).thenReturn(false);
		
		assertThat("incorrect role", dah.getAdminRole(new AuthToken("fake", "bar")),
				is(AdminRole.NONE));
	}
	
	@Test
	public void getAdminRoleFail() throws Exception {
		final Workspace ws = mock(Workspace.class);
		final DefaultAdminHandler dah = new DefaultAdminHandler(ws, null);
		
		failGetAdminRole(dah, null, new NullPointerException("token"));
		
		when(ws.isAdmin(new WorkspaceUser("baz")))
				.thenThrow(new WorkspaceCommunicationException("arrg"));
		failGetAdminRole(dah, new AuthToken("fake", "baz"), new AdministratorHandlerException(
				"Couldn't verify administrator: arrg"));
	}

	private void failGetAdminRole(
			final DefaultAdminHandler dah,
			final AuthToken token,
			final Exception expected) {
		try {
			dah.getAdminRole(token);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
}
