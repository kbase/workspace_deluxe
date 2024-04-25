package us.kbase.test.workspace.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.google.common.collect.Sets;

import us.kbase.test.common.TestCommon;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.refsearch.ReferenceGraphSearch;
import us.kbase.workspace.database.refsearch.ReferenceGraphTopologyProvider;
import us.kbase.workspace.database.refsearch.ReferenceProviderException;

public class ReferenceGraphSearchTest {

	//TODO TEST finish adding unit tests for this class. What isn't covered here is currently covered by WorkspaceTest, but these tests should eventually provide full coverage.
	
	private static class TestReferenceGraphTopologyProvider implements
			ReferenceGraphTopologyProvider {

		private final List<Map<Reference, Map<Reference, Boolean>>> responses;
		private int responseCount = 0;

		public TestReferenceGraphTopologyProvider(
				final List<Map<Reference, Map<Reference, Boolean>>> responses) {
			this.responses = responses;
		}
		
		@Override
		public Map<Reference, Map<Reference, Boolean>> getAssociatedReferences(
				final Set<Reference> sourceRefs)
				throws ReferenceProviderException {
			final Map<Reference, Map<Reference, Boolean>> resp = responses.get(responseCount);
			responseCount++;
			return resp;
		}
	}
	
	private void failCreate(
			final Set<Reference> startingRefs,
			final ReferenceGraphTopologyProvider refProvider,
			final int maximumSearchSize,
			final Exception exp) {
		try {
			new ReferenceGraphSearch(startingRefs, refProvider, maximumSearchSize,
					false);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, exp);
		}
	}
	
	@Test
	public void failConstructNullRefs() throws Exception {
		failCreate(null, new TestReferenceGraphTopologyProvider(null), 1,
				new IllegalArgumentException("startingRefs cannot be null or empty"));
	}
	
	@Test
	public void failConstructEmptyRefs() throws Exception {
		failCreate(Sets.<Reference>newHashSet(), new TestReferenceGraphTopologyProvider(null), 1,
				new IllegalArgumentException("startingRefs cannot be null or empty"));
	}
	
	@Test
	public void failConstructNullProvider() throws Exception {
		failCreate(Sets.newHashSet(new Reference(1, 1, 1)), null, 1,
				new NullPointerException("refProvider"));
	}
	
	@Test
	public void failConstructMaxSearchSize() throws Exception {
		failCreate(Sets.newHashSet(new Reference(1, 1, 1)),
				new TestReferenceGraphTopologyProvider(null), 0,
				new IllegalArgumentException("maximumSearchSize must be > 0"));
	}
	
	@Test
	public void failSearch() throws Exception {
		final Reference root = new Reference(1, 1, 1);
		final List<Map<Reference, Map<Reference, Boolean>>> responses = new LinkedList<>();
		responses.add(new HashMap<Reference, Map<Reference, Boolean>>());
		responses.add(new HashMap<Reference, Map<Reference, Boolean>>());
		responses.get(0).put(root, new HashMap<Reference, Boolean>());
		responses.get(0).get(root).put(new Reference(2, 1, 1), false);
		final ReferenceGraphSearch graph = new ReferenceGraphSearch(Sets.newHashSet(root),
				new TestReferenceGraphTopologyProvider(responses), 5, false);
		
		assertThat("impossible path found", graph.isPathFound(root), is(false));
		try {
			graph.getPath(root);
			fail("Got bad path");
		} catch (IllegalStateException e) {
			assertThat("incorrect exception message", e.getMessage(), is("No path for ref 1/1/1"));
		}
	}
}
