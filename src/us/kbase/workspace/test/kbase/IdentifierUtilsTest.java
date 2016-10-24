package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;

import us.kbase.common.test.TestCommon;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.kbase.KBaseIdentifierFactory;

public class IdentifierUtilsTest {
	
	private void expectFailProcessWorkspaceIdentifier(
			final WorkspaceIdentity wsi,
			final Exception exp) {
		try {
			KBaseIdentifierFactory.processWorkspaceIdentifier(wsi);
			fail("Expected exception");
		} catch (Exception e) {
			TestCommon.assertExceptionCorrect(e, exp);
		}
	}
	
	private void expectFailProcessWorkspaceIdentifier(
			final String name,
			final Long id,
			final Exception exp) {
		try {
			KBaseIdentifierFactory.processWorkspaceIdentifier(name, id);
			fail("Expected exception");
		} catch (Exception e) {
			TestCommon.assertExceptionCorrect(e, exp);
		}
	}
	
	private void expectSuccessProcessWorkspaceIdentity(
			final WorkspaceIdentity wsi,
			final String name,
			final Long id,
			final String idstring) throws Exception {
		WorkspaceIdentifier pwsi = KBaseIdentifierFactory
				.processWorkspaceIdentifier(wsi);
		assertThat("incorrect wsi name", pwsi.getName(), is(name));
		assertThat("incorrect wsi id", pwsi.getId(), is(id));
		assertThat("incorrect is string", pwsi.getIdentifierString(),
				is(idstring));
		pwsi = KBaseIdentifierFactory.processWorkspaceIdentifier(
				wsi.getWorkspace(), wsi.getId());
		assertThat("incorrect wsi name", pwsi.getName(), is(name));
		assertThat("incorrect wsi id", pwsi.getId(), is(id));
		assertThat("incorrect is string", pwsi.getIdentifierString(),
				is(idstring));
	}

	@Test
	public void failNullWorkspaceIdentity() throws Exception {
		expectFailProcessWorkspaceIdentifier(null, new NullPointerException(
				"WorkspaceIdentifier cannot be null"));
	}
	
	@Test
	public void failWorkspaceIdentityAddlArgs() throws Exception {
		final WorkspaceIdentity wsi = new WorkspaceIdentity();
		wsi.setAdditionalProperties("foo", "bar");
		expectFailProcessWorkspaceIdentifier(wsi, new IllegalArgumentException(
				"Unexpected arguments in WorkspaceIdentity: foo"));
	}
	
	@Test
	public void failWorkspaceIdAndName() throws Exception {
		expectFailProcessWorkspaceIdentifier("foo", 2L,
				new IllegalArgumentException(
				"Must provide one and only one of workspace name (was: foo) " +
				"or id (was: 2)"));
		
	}
	
	@Test
	public void failWorkspaceIdNorName() throws Exception {
		expectFailProcessWorkspaceIdentifier(null, null,
				new IllegalArgumentException(
				"Must provide one and only one of workspace name " +
				"(was: null) or id (was: null)"));
		
	}
	
	@Test
	public void successWorkspaceIdentityIdString() throws Exception {
		final WorkspaceIdentity wsi = new WorkspaceIdentity()
				.withWorkspace("foobar");
		expectSuccessProcessWorkspaceIdentity(wsi, "foobar", null, "foobar");
	}

	@Test
	public void successWorkspaceIdentityIdLong() throws Exception {
		final WorkspaceIdentity wsi = new WorkspaceIdentity().withId(2L);
		expectSuccessProcessWorkspaceIdentity(wsi, null, 2L, "2");
	}
	
	@Test
	public void successWorkspaceIdentityKBID() throws Exception {
		final WorkspaceIdentity wsi = new WorkspaceIdentity()
				.withWorkspace("kb|ws.2");
		expectSuccessProcessWorkspaceIdentity(wsi, null, 2L, "2");
	}
}
