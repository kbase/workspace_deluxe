package us.kbase.test.workspace.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.google.common.collect.Sets;

import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.refsearch.ReferenceSearchTree;

public class ReferenceSearchTreeTest {

	@Test
	public void construct() throws Exception {
		final ReferenceSearchTree tree = new ReferenceSearchTree(new Reference(3, 5, 7));
		assertThat("incorrect tree root", tree.getRoot(), is(new Reference(3, 5, 7)));
		assertThat("complete with no search", tree.isComplete(), is(false));
		assertThat("path with no search", tree.isPathFound(), is(false));
		try {
			tree.getPath();
			fail("got path from tree without search");
		} catch (IllegalStateException e) {
			assertThat("incorrect exception message", e.getMessage(),
					is("Search is not complete or failed"));
		}
	}
	
	@Test
	public void failConstruct() throws Exception {
		try {
			new ReferenceSearchTree(null);
			fail("created tree with no root");
		} catch (NullPointerException e) {
			assertThat("incorrect exception message", e.getMessage(), is("root"));
		}
	}
	
	@Test
	public void successfulSearch() throws Exception {
		final Reference root = new Reference(2, 5, 4);
		final ReferenceSearchTree tree = new ReferenceSearchTree(root);
		
		// first round of updates
		final Map<Reference, Map<Reference, Boolean>> nr = new HashMap<>();
		nr.put(root, new HashMap<Reference, Boolean>());
		final Reference r1 = new Reference(1, 1, 1);
		final Reference r2 = new Reference(2, 2, 2);
		nr.get(root).put(r1, false);
		nr.get(root).put(r2, false);
		nr.get(root).put(new Reference(3, 3, 3), false); // dead branch
		assertThat("incorrect returned references", tree.updateTree(nr), is((Set<Reference>)
				Sets.newHashSet(r1, r2, new Reference(3, 3, 3))));
		assertThat("early complete", tree.isComplete(), is(false));
		assertThat("early path found", tree.isPathFound(), is(false));
		
		// 2nd round. Need a third round to exercise dead leaf skipping
		nr.clear();
		final Reference r12 = new Reference(1, 2, 1);
		final Reference r13 = new Reference(1, 3, 1);
		final Reference r22 = new Reference(2, 2, 1);
		nr.put(r1, new HashMap<Reference, Boolean>());
		nr.get(r1).put(r12, false);
		nr.get(r1).put(r13, false);
		nr.put(r2, new HashMap<Reference, Boolean>());
		nr.get(r2).put(r22, false);
		assertThat("incorrect returned references", tree.updateTree(nr),
				is((Set<Reference>) Sets.newHashSet(r12, r13, r22)));
		assertThat("early complete", tree.isComplete(), is(false));
		assertThat("early path found", tree.isPathFound(), is(false));
		
		// 3rd round
		nr.clear();
		final Reference r122 = new Reference(1, 2, 2);
		final Reference r132 = new Reference(1, 3, 2);
		final Reference r222 = new Reference(2, 2, 2);
		nr.put(r12, new HashMap<Reference, Boolean>());
		nr.get(r12).put(r122, false);
		nr.put(r13, new HashMap<Reference, Boolean>());
		nr.get(r13).put(r132, true);
		nr.put(r22, new HashMap<Reference, Boolean>());
		nr.get(r22).put(r222, false);
		assertThat("incorrect returned references", tree.updateTree(nr),
				is((Set<Reference>) Sets.<Reference>newHashSet()));
		
		assertThat("bad root", tree.getRoot(), is(root));
		assertThat("incomplete search", tree.isComplete(), is(true));
		assertThat("path not found", tree.isPathFound(), is(true));
		assertThat("incorrect path", tree.getPath(), is(Arrays.asList(r132, r13, r1, root)));
		
		failUpdateTreeOnCompletedSearch(tree);
	}
	
	@Test
	public void failSearch() throws Exception {
		final Reference root = new Reference(2, 5, 4);
		final ReferenceSearchTree tree = new ReferenceSearchTree(root);
		
		// first round of updates
		final Map<Reference, Map<Reference, Boolean>> nr = new HashMap<>();
		nr.put(root, new HashMap<Reference, Boolean>());
		final Reference r1 = new Reference(1, 1, 1);
		final Reference r2 = new Reference(2, 2, 2);
		nr.get(root).put(r1, false);
		nr.get(root).put(r2, false);
		nr.get(root).put(new Reference(3, 3, 3), false); // dead branch
		assertThat("incorrect returned references", tree.updateTree(nr), is((Set<Reference>)
				Sets.newHashSet(r1, r2, new Reference(3, 3, 3))));
		assertThat("early complete", tree.isComplete(), is(false));
		assertThat("early path found", tree.isPathFound(), is(false));
		
		// 2nd round
		nr.clear();
		final Reference r12 = new Reference(1, 2, 1);
		final Reference r13 = new Reference(1, 3, 1);
		final Reference r22 = new Reference(2, 2, 1);
		nr.put(r1, new HashMap<Reference, Boolean>());
		nr.get(r1).put(r12, false);
		nr.get(r1).put(r13, false);
		nr.put(r2, new HashMap<Reference, Boolean>());
		nr.get(r2).put(r22, false);
		assertThat("incorrect returned references", tree.updateTree(nr),
				is((Set<Reference>) Sets.newHashSet(r12, r13, r22)));
		assertThat("early complete", tree.isComplete(), is(false));
		assertThat("early path found", tree.isPathFound(), is(false));
		
		// 3rd round
		nr.clear();
		final Reference r10 = new Reference(10, 1, 1);
		nr.put(r10, new HashMap<Reference, Boolean>());
		nr.get(r10).put(new Reference(1, 2, 10), true);
		nr.put(r12, new HashMap<Reference, Boolean>());
		assertThat("incorrect returned references", tree.updateTree(nr),
				is((Set<Reference>) Sets.<Reference>newHashSet()));
		
		assertThat("bad root", tree.getRoot(), is(root));
		assertThat("incomplete search", tree.isComplete(), is(true));
		assertThat("path found when no path exists", tree.isPathFound(), is(false));
		
		failUpdateTreeOnCompletedSearch(tree);
	}

	private void failUpdateTreeOnCompletedSearch(final ReferenceSearchTree tree) {
		final Map<Reference, Map<Reference, Boolean>> mt = new HashMap<>();
		try {
			tree.updateTree(mt);
			fail("updated tree after search complete");
		} catch (IllegalStateException e) {
			assertThat("incorrect exception message", e.getMessage(), is("Search is complete"));
		}
	}
	
	@Test
	public void cycle() throws Exception {
		// this also effectively tests diamond patterns in directed graphs
		final Reference root = new Reference(2, 5, 4);
		final ReferenceSearchTree tree = new ReferenceSearchTree(root);
		
		// first round of updates
		final Map<Reference, Map<Reference, Boolean>> nr = new HashMap<>();
		nr.put(root, new HashMap<Reference, Boolean>());
		final Reference r1 = new Reference(1, 1, 1);
		nr.get(root).put(r1, false);
		assertThat("incorrect returned references", tree.updateTree(nr), is((Set<Reference>)
				Sets.newHashSet(r1)));
		assertThat("early complete", tree.isComplete(), is(false));
		assertThat("early path found", tree.isPathFound(), is(false));
		
		// second round of updates
		nr.clear();
		nr.put(r1, new HashMap<Reference, Boolean>());
		final Reference r2 = new Reference(2, 1, 1);
		nr.get(r1).put(r2, false);
		assertThat("incorrect returned references", tree.updateTree(nr), is((Set<Reference>)
				Sets.newHashSet(r2)));
		assertThat("early complete", tree.isComplete(), is(false));
		assertThat("early path found", tree.isPathFound(), is(false));
		
		// third round of updates
		nr.clear();
		nr.put(r2, new HashMap<Reference, Boolean>());
		nr.get(r2).put(r1, true);
		assertThat("incorrect returned references", tree.updateTree(nr),
				is((Set<Reference>) Sets.<Reference>newHashSet()));
		
		assertThat("bad root", tree.getRoot(), is(root));
		assertThat("incomplete search", tree.isComplete(), is(true));
		assertThat("path found when no path exists", tree.isPathFound(), is(false));
		
		failUpdateTreeOnCompletedSearch(tree);
	}
	
}
