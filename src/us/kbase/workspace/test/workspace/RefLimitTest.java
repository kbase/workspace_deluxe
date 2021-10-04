package us.kbase.workspace.test.workspace;

import static us.kbase.common.test.TestCommon.assertExceptionCorrect;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Optional;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.workspace.database.RefLimit;

public class RefLimitTest {
	
	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(RefLimit.class).usingGetClass().verify();
	}
	
	@Test
	public void buildEmpty() throws Exception {
		final RefLimit rl1 = RefLimit.buildEmpty();
		final RefLimit rl2 = RefLimit.build(null, null, null);
		final RefLimit rl3 = RefLimit.build(0L, 0L, 0);
		final RefLimit rl4 = RefLimit.build(-100L, -100L, -100);
		
		for (final RefLimit rl: Arrays.asList(rl1, rl2, rl3, rl4)) {
			
			assertThat("incorrect wsid", rl.getWorkspaceID(), is(Optional.empty()));
			assertThat("incorrect objid", rl.getObjectID(), is(Optional.empty()));
			assertThat("incorrect ver", rl.getVersion(), is(Optional.empty()));
			assertThat("incorrect present", rl.isPresent(), is(false));
			assertThat("incorrect present", rl.isEmpty(), is(true));
		}	
	}
	
	public void buildWithWsid() throws Exception {
		RefLimit rl = RefLimit.build(1L, null, null);
		
		assertThat("incorrect wsid", rl.getWorkspaceID(), is(Optional.of(1L)));
		assertThat("incorrect objid", rl.getObjectID(), is(Optional.empty()));
		assertThat("incorrect ver", rl.getVersion(), is(Optional.empty()));
		assertThat("incorrect present", rl.isPresent(), is(true));
		assertThat("incorrect present", rl.isEmpty(), is(false));

		rl = RefLimit.build(10L, null, null);
		
		assertThat("incorrect wsid", rl.getWorkspaceID(), is(Optional.of(10L)));
		assertThat("incorrect objid", rl.getObjectID(), is(Optional.empty()));
		assertThat("incorrect ver", rl.getVersion(), is(Optional.empty()));
		assertThat("incorrect present", rl.isPresent(), is(true));
		assertThat("incorrect present", rl.isEmpty(), is(false));
	}
	
	@Test
	public void buildWithObjId() throws Exception {
		RefLimit rl = RefLimit.build(1L, 1L, null);
		
		assertThat("incorrect wsid", rl.getWorkspaceID(), is(Optional.of(1L)));
		assertThat("incorrect objid", rl.getObjectID(), is(Optional.of(1L)));
		assertThat("incorrect ver", rl.getVersion(), is(Optional.empty()));
		assertThat("incorrect present", rl.isPresent(), is(true));
		assertThat("incorrect present", rl.isEmpty(), is(false));

		rl = RefLimit.build(1L, 7L, null);
		
		assertThat("incorrect wsid", rl.getWorkspaceID(), is(Optional.of(1L)));
		assertThat("incorrect objid", rl.getObjectID(), is(Optional.of(7L)));
		assertThat("incorrect ver", rl.getVersion(), is(Optional.empty()));
		assertThat("incorrect present", rl.isPresent(), is(true));
		assertThat("incorrect present", rl.isEmpty(), is(false));
	}
	
	@Test
	public void buildWithVer() throws Exception {
		RefLimit rl = RefLimit.build(1L, 1L, 1);
		
		assertThat("incorrect wsid", rl.getWorkspaceID(), is(Optional.of(1L)));
		assertThat("incorrect objid", rl.getObjectID(), is(Optional.of(1L)));
		assertThat("incorrect ver", rl.getVersion(), is(Optional.of(1)));
		assertThat("incorrect present", rl.isPresent(), is(true));
		assertThat("incorrect present", rl.isEmpty(), is(false));

		rl = RefLimit.build(1L, 1L, 42);
		
		assertThat("incorrect wsid", rl.getWorkspaceID(), is(Optional.of(1L)));
		assertThat("incorrect objid", rl.getObjectID(), is(Optional.of(1L)));
		assertThat("incorrect ver", rl.getVersion(), is(Optional.of(42)));
		assertThat("incorrect present", rl.isPresent(), is(true));
		assertThat("incorrect present", rl.isEmpty(), is(false));
	}

	@Test
	public void buildFail() throws Exception {
		final String err1 = "If a version is specified in a reference limit the object ID " +
				"and workspace ID must also be specified";
		failBuild(null, null, 1, new IllegalArgumentException(err1));
		failBuild(0L, 0L, 1, new IllegalArgumentException(err1));
		failBuild(1L, null, 1, new IllegalArgumentException(err1));
		failBuild(1L, 0L, 1, new IllegalArgumentException(err1));
		failBuild(null, 1L, 1, new IllegalArgumentException(err1));
		failBuild(0L, 1L, 1, new IllegalArgumentException(err1));
		
		final String err2 = "If an object ID is specified in a reference limit the " +
				"workspace ID must also be specified";
		failBuild(null, 1L, null, new IllegalArgumentException(err2));
		failBuild(0L, 1L, null, new IllegalArgumentException(err2));
	}
	
	private void failBuild(
			final Long wsid,
			final Long objid,
			final Integer ver,
			final Exception expected) {
		try {
			RefLimit.build(wsid, objid, ver);
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, expected);
		}
		
	}


}
