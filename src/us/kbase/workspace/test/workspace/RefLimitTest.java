package us.kbase.workspace.test.workspace;

import static us.kbase.common.test.TestCommon.assertExceptionCorrect;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.MapBuilder;
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
			assertThat("incorrect toString",
					rl.toString(), is("RefLimit [wsid=null, objid=null, ver=null]"));
		}	
	}
	
	@Test
	public void buildWithWsid() throws Exception {
		RefLimit rl = RefLimit.build(1L, null, null);
		
		assertThat("incorrect wsid", rl.getWorkspaceID(), is(Optional.of(1L)));
		assertThat("incorrect objid", rl.getObjectID(), is(Optional.empty()));
		assertThat("incorrect ver", rl.getVersion(), is(Optional.empty()));
		assertThat("incorrect present", rl.isPresent(), is(true));
		assertThat("incorrect present", rl.isEmpty(), is(false));
		assertThat("incorrect toString",
				rl.toString(), is("RefLimit [wsid=1, objid=null, ver=null]"));

		rl = RefLimit.build(10L, null, null);
		
		assertThat("incorrect wsid", rl.getWorkspaceID(), is(Optional.of(10L)));
		assertThat("incorrect objid", rl.getObjectID(), is(Optional.empty()));
		assertThat("incorrect ver", rl.getVersion(), is(Optional.empty()));
		assertThat("incorrect present", rl.isPresent(), is(true));
		assertThat("incorrect present", rl.isEmpty(), is(false));
		assertThat("incorrect toString",
				rl.toString(), is("RefLimit [wsid=10, objid=null, ver=null]"));
	}
	
	@Test
	public void buildWithObjId() throws Exception {
		RefLimit rl = RefLimit.build(1L, 1L, null);
		
		assertThat("incorrect wsid", rl.getWorkspaceID(), is(Optional.of(1L)));
		assertThat("incorrect objid", rl.getObjectID(), is(Optional.of(1L)));
		assertThat("incorrect ver", rl.getVersion(), is(Optional.empty()));
		assertThat("incorrect present", rl.isPresent(), is(true));
		assertThat("incorrect present", rl.isEmpty(), is(false));
		assertThat("incorrect toString",
				rl.toString(), is("RefLimit [wsid=1, objid=1, ver=null]"));

		rl = RefLimit.build(1L, 7L, null);
		
		assertThat("incorrect wsid", rl.getWorkspaceID(), is(Optional.of(1L)));
		assertThat("incorrect objid", rl.getObjectID(), is(Optional.of(7L)));
		assertThat("incorrect ver", rl.getVersion(), is(Optional.empty()));
		assertThat("incorrect present", rl.isPresent(), is(true));
		assertThat("incorrect present", rl.isEmpty(), is(false));
		assertThat("incorrect toString",
				rl.toString(), is("RefLimit [wsid=1, objid=7, ver=null]"));
	}
	
	@Test
	public void buildWithVer() throws Exception {
		RefLimit rl = RefLimit.build(1L, 1L, 1);
		
		assertThat("incorrect wsid", rl.getWorkspaceID(), is(Optional.of(1L)));
		assertThat("incorrect objid", rl.getObjectID(), is(Optional.of(1L)));
		assertThat("incorrect ver", rl.getVersion(), is(Optional.of(1)));
		assertThat("incorrect present", rl.isPresent(), is(true));
		assertThat("incorrect present", rl.isEmpty(), is(false));
		assertThat("incorrect toString",
				rl.toString(), is("RefLimit [wsid=1, objid=1, ver=1]"));

		rl = RefLimit.build(1L, 1L, 42);
		
		assertThat("incorrect wsid", rl.getWorkspaceID(), is(Optional.of(1L)));
		assertThat("incorrect objid", rl.getObjectID(), is(Optional.of(1L)));
		assertThat("incorrect ver", rl.getVersion(), is(Optional.of(42)));
		assertThat("incorrect present", rl.isPresent(), is(true));
		assertThat("incorrect present", rl.isEmpty(), is(false));
		assertThat("incorrect toString",
				rl.toString(), is("RefLimit [wsid=1, objid=1, ver=42]"));
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

	@Test
	public void fromRefString() throws Exception {
		final Map<String, RefLimit> testCases = MapBuilder.<String, RefLimit>newHashMap()
				.with(null, RefLimit.buildEmpty())
				.with("   \t   ", RefLimit.buildEmpty())
				.with("   0   ", RefLimit.buildEmpty())
				.with("   -1   ", RefLimit.buildEmpty())
				.with("   1   ", RefLimit.build(1L, null, null))
				.with("   6   ", RefLimit.build(6L, null, null))
				.with("   6   /", RefLimit.build(6L, null, null))
				.with("   6 / 0  ", RefLimit.build(6L, null, null))
				.with("   6 / -1  ", RefLimit.build(6L, null, null))
				.with("   6 / 1  ", RefLimit.build(6L, 1L, null))
				.with("   6 /  \t  24  ", RefLimit.build(6L, 24L, null))
				.with("   6 / 24   /", RefLimit.build(6L, 24L, null))
				.with("   6 / 24   / 0  ", RefLimit.build(6L, 24L, null))
				.with("   6 / 24   / -1  ", RefLimit.build(6L, 24L, null))
				.with("   6 / 24   /  \t 89  ", RefLimit.build(6L, 24L, 89))
				.with("   6 / 24   /  \t 3000000000  ", RefLimit.build(6L, 24L, Integer.MAX_VALUE))
				.build();
		
		for (final Entry<String, RefLimit> e: testCases.entrySet()) {
			assertThat("incorrect ref limit from string " + e.getKey(),
					RefLimit.fromRefString(e.getKey()), is(e.getValue()));
		}
	}
	
	@Test
	public void fromRefStringFail() throws Exception {
		failFromRefString("   /  ", new IllegalArgumentException(
				"Illegal integer workspace ID in reference string    /  : "));
		failFromRefString(" myws /  ", new IllegalArgumentException(
				"Illegal integer workspace ID in reference string  myws /  : myws"));
		failFromRefString(" 3.2 /  ", new IllegalArgumentException(
				"Illegal integer workspace ID in reference string  3.2 /  : 3.2"));
		failFromRefString(" 1 /  ", new IllegalArgumentException(
				"Illegal integer object ID in reference string  1 /  : "));
		failFromRefString(" 1 /  myobj   / ", new IllegalArgumentException(
				"Illegal integer object ID in reference string  1 /  myobj   / : myobj"));
		failFromRefString(" 1 /  42.42   / ", new IllegalArgumentException(
				"Illegal integer object ID in reference string  1 /  42.42   / : 42.42"));
		failFromRefString(" 1 /  2   / ", new IllegalArgumentException(
				"Illegal integer version in reference string  1 /  2   / : "));
		failFromRefString(" 1 /  2   / thingy", new IllegalArgumentException(
				"Illegal integer version in reference string  1 /  2   / thingy: thingy"));
		failFromRefString(" 1 /  2   / 3.1416", new IllegalArgumentException(
				"Illegal integer version in reference string  1 /  2   / 3.1416: 3.1416"));
		failFromRefString(" 1 /  2   / 3 / 4", new IllegalArgumentException(
				"Illegal reference string, expected no more than 2 separators: " +
				" 1 /  2   / 3 / 4"));
	}
	
	private void failFromRefString(final String ref, final Exception expected) {
		try {
			RefLimit.fromRefString(ref);
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void decrementVersionIncrementingObjectID() throws Exception {
		final Map<RefLimit, RefLimit> testCases = MapBuilder.<RefLimit, RefLimit>newHashMap()
				.with(RefLimit.build(null, null, null), RefLimit.build(null, null, null))
				.with(RefLimit.build(1L, null, null), RefLimit.build(1L, null, null))
				.with(RefLimit.build(10L, null, null), RefLimit.build(10L, null, null))
				.with(RefLimit.build(1L, 1L, null), RefLimit.build(1L, 1L, null))
				.with(RefLimit.build(1L, 3L, null), RefLimit.build(1L, 3L, null))
				.with(RefLimit.build(1L, 1L, 10), RefLimit.build(1L, 1L, 9))
				.with(RefLimit.build(1L, 1L, 2), RefLimit.build(1L, 1L, 1))
				.with(RefLimit.build(1L, 1L, 1), RefLimit.build(1L, 2L, null))
				.with(RefLimit.build(24L, 36L, 1), RefLimit.build(24L, 37L, null))
				.build();
		
		for (final Entry<RefLimit, RefLimit> tc: testCases.entrySet()) {
			assertThat("incorrect ref " + tc.getKey(),
					tc.getKey().decrementVersionIncrementingObjectID(), is(tc.getValue()));
		}
	}
}
