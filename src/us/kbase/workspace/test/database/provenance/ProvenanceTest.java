package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.inst;
import static us.kbase.common.test.TestCommon.list;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.provenance.Provenance;
import us.kbase.workspace.database.provenance.ProvenanceAction;

public class ProvenanceTest {
	
	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(Provenance.class).usingGetClass().verify();
	}

	@Test
	public void buildMinimal() throws Exception {
		final Provenance p = Provenance.getBuilder(
				new WorkspaceUser("u"), inst(4500)).build();
		
		assertThat("incorrect actions", p.getActions(), is(Collections.emptyList()));
		assertThat("incorrect date", p.getDate(), is(inst(4500)));
		assertThat("incorrect user", p.getUser(), is(new WorkspaceUser("u")));
		assertThat("incorrect wsid", p.getWorkspaceID(), is(Optional.empty()));
	}
	
	@Test
	public void buildMaximal1Action() throws Exception {
		final Provenance p = Provenance.getBuilder(
				new WorkspaceUser("user"), inst(1234))
				.withWorkspaceID(1L)
				.withAction(ProvenanceAction.getBuilder().withCaller("c").build())
				.build();
		
		assertThat("incorrect actions", p.getActions(), is(list(
				ProvenanceAction.getBuilder().withCaller("c").build())));
		assertThat("incorrect date", p.getDate(), is(inst(1234)));
		assertThat("incorrect user", p.getUser(), is(new WorkspaceUser("user")));
		assertThat("incorrect wsid", p.getWorkspaceID(), is(Optional.of(1L)));
	}
	
	@Test
	public void buildMaximal3Actions() throws Exception {
		final Provenance p = Provenance.getBuilder(
				new WorkspaceUser("user1"), inst(10000))
				.withWorkspaceID(15671L)
				.withAction(ProvenanceAction.getBuilder().withCaller("c").build())
				.withAction(ProvenanceAction.getBuilder().withScript("s").build())
				.withAction(ProvenanceAction.getBuilder().withDescription("d").build())
				.build();
		
		assertThat("incorrect actions", p.getActions(), is(list(
				ProvenanceAction.getBuilder().withCaller("c").build(),
				ProvenanceAction.getBuilder().withScript("s").build(),
				ProvenanceAction.getBuilder().withDescription("d").build()
				)));
		assertThat("incorrect date", p.getDate(), is(inst(10000)));
		assertThat("incorrect user", p.getUser(), is(new WorkspaceUser("user1")));
		assertThat("incorrect wsid", p.getWorkspaceID(), is(Optional.of(15671L)));
	}
	
	@Test
	public void buildWithNullWsid() throws Exception {
		final Provenance p = Provenance.getBuilder(
				new WorkspaceUser("user"), inst(1234))
				.withWorkspaceID(null)
				.build();
		
		assertThat("incorrect actions", p.getActions(), is(Collections.emptyList()));
		assertThat("incorrect date", p.getDate(), is(inst(1234)));
		assertThat("incorrect user", p.getUser(), is(new WorkspaceUser("user")));
		assertThat("incorrect wsid", p.getWorkspaceID(), is(Optional.empty()));
	}
	
	@Test
	public void immutableActions() throws Exception {
		// test empty list
		final Provenance p = Provenance.getBuilder(
				new WorkspaceUser("user"), inst(1234))
				.build();
		try {
			p.getActions().add(ProvenanceAction.getBuilder().withCaller("c").build());
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
		
		// test filled list
		final Provenance p2 = Provenance.getBuilder(
				new WorkspaceUser("user"), inst(1234))
				.withAction(ProvenanceAction.getBuilder().withCaller("c").build())
				.build();
		try {
			p2.getActions().remove(0);
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
	}
	
	@Test
	public void getBuilderFail() throws Exception {
		failGetBuilder(null, inst(0), new NullPointerException("user"));
		failGetBuilder(new WorkspaceUser("u"), null, new NullPointerException("date"));
	}
	
	private void failGetBuilder(
			final WorkspaceUser u,
			final Instant date,
			final Exception expected) {
		try {
			Provenance.getBuilder(u, date);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void withWorkspaceIDFail() throws Exception {
		failWithWorkspaceID(0L, new IllegalArgumentException("workspace ID must be > 0"));
		failWithWorkspaceID(-1L, new IllegalArgumentException("workspace ID must be > 0"));
		failWithWorkspaceID(-100L, new IllegalArgumentException("workspace ID must be > 0"));
	}
	
	private void failWithWorkspaceID(final Long workspaceID, final Exception expected) {
		try {
			Provenance.getBuilder(new WorkspaceUser("u"), inst(0)).withWorkspaceID(workspaceID);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void withProvenanceActionFail() throws Exception {
		try {
			Provenance.getBuilder(new WorkspaceUser("u"), inst(0)).withAction(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("action"));
		}
		
	}
	
	@Test
	public void updateWorkspaceIDNoIDs() throws Exception {
		final Provenance p = Provenance.getBuilder(new WorkspaceUser("u"), inst(0)).build();
		final Provenance newp = p.updateWorkspaceID(null);
		
		assertThat("provenance identity true", p == newp, is(true));
	}
	
	@Test
	public void updateWorkspaceIDSameIDs() throws Exception {
		final Provenance p = Provenance.getBuilder(new WorkspaceUser("u"), inst(0))
				.withWorkspaceID(75L).build();
		final Provenance newp = p.updateWorkspaceID(75L);
		
		assertThat("provenance identity true", p == newp, is(true));
	}
	
	@Test
	public void updateWorkspaceIDRemoveID() throws Exception {
		final Provenance p = Provenance.getBuilder(new WorkspaceUser("u"), inst(0))
				.withWorkspaceID(75L).build();
		final Provenance newp = p.updateWorkspaceID(null);
		
		assertThat("incorrect provenance",
				newp, is(Provenance.getBuilder(new WorkspaceUser("u"), inst(0)).build()));
	}
	
	@Test
	public void updateWorkspaceIDAddID() throws Exception {
		final Provenance p = Provenance.getBuilder(new WorkspaceUser("u"), inst(0)).build();
		final Provenance newp = p.updateWorkspaceID(34L);
		
		assertThat("incorrect provenance", newp, is(Provenance.getBuilder(
				new WorkspaceUser("u"), inst(0)).withWorkspaceID(34L).build()));
	}
	
	@Test
	public void updateWorkspaceIDFail() throws Exception {
		failUpdateWorkspaceID(0L, new IllegalArgumentException("workspace ID must be > 0"));
		failUpdateWorkspaceID(-1L, new IllegalArgumentException("workspace ID must be > 0"));
		failUpdateWorkspaceID(-1234L, new IllegalArgumentException("workspace ID must be > 0"));
	}
	
	private void failUpdateWorkspaceID(final Long wsid, final Exception expected) {
		try {
			Provenance.getBuilder(new WorkspaceUser("u"), inst(0)).build().updateWorkspaceID(wsid);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
}
