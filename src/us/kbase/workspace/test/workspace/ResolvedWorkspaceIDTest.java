package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.ResolvedWorkspaceID;


public class ResolvedWorkspaceIDTest {
	
	private static final String s255;
	private static final String s256;
	static {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 255 / 5; i++) {
			sb.append("abcde");
		}
		s255 = sb.toString();
		sb.append("f");
		s256 = sb.toString();
	}
	
	@Test
	public void equals() {
		EqualsVerifier.forClass(ResolvedWorkspaceID.class).usingGetClass().verify();
	}
	
	@Test
	public void construct1() {
		final ResolvedWorkspaceID r = new ResolvedWorkspaceID(2, s255, false, true);
		
		assertThat("incorrect name", r.getName(), is(s255));
		assertThat("incorrect id", r.getID(), is(2L));
		assertThat("incorrect locked", r.isLocked(), is(false));
		assertThat("incorrect deleted", r.isDeleted(), is(true));
	}
	
	@Test
	public void construct2() {
		final ResolvedWorkspaceID r = new ResolvedWorkspaceID(1, "a", true, false);
		
		assertThat("incorrect name", r.getName(), is("a"));
		assertThat("incorrect id", r.getID(), is(1L));
		assertThat("incorrect locked", r.isLocked(), is(true));
		assertThat("incorrect deleted", r.isDeleted(), is(false));
	}
	
	@Test
	public void constructFail() {
		failConstruct(0, "foo", new IllegalArgumentException("ID must be > 0"));
		failConstruct(1, null, new IllegalArgumentException(
				"name cannot be null or the empty string"));
		//TODO TEST add whitespace when java common checks for whitespace
		failConstruct(1, "", new IllegalArgumentException(
				"name cannot be null or the empty string"));
		failConstruct(1, s256, new IllegalArgumentException(
				"name exceeds the maximum length of 255"));
	}
	
	private void failConstruct(
			final long id,
			final String name,
			final Exception e) {
		try {
			new ResolvedWorkspaceID(id, name, false, false);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void string() {
		final ResolvedWorkspaceID r = new ResolvedWorkspaceID(2, "foo", false, true);
		assertThat("incorrect toString", r.toString(),
				is("ResolvedWorkspaceID [id=2, wsname=foo, locked=false, deleted=true]"));
	}

}
