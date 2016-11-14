package us.kbase.workspace.database.refsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.workspace.database.Reference;

/** A search tree based on a workspace reference graph.
 * @author gaprice@lbl.gov
 *
 */
public class ReferenceSearchTree {
	
	//TODO NOW TEST Unit tests

	/* Could prune tree from already seen nodes and dead ends if memory use becomes an issue */
	
	private final Reference root;
	private final Set<Reference> tree = new HashSet<>();
	private List<ReferenceTreeNode> leaves = new LinkedList<>();
	private List<Reference> path = null;
	boolean complete = false;

	/** Construct a new search tree.
	 * @param root the root, or target, reference of the search.
	 */
	public ReferenceSearchTree(final Reference root) {
		if (root == null) {
			throw new NullPointerException("root");
		}
		this.root = root;
		tree.add(root);
		leaves.add(new ReferenceTreeNode(root, null));
	}
	
	/** Update the search tree with a new set of leaves. The tree's current leaves are checked
	 * against the incoming references and updated to be parents of the new references, which
	 * become the tree's new leaves. If one of the new references is a termination node, the 
	 * search completes and a path is generated from the termination node to the root node. If
	 * no new leaves are found in the input the search is terminated with no path generated.
	 * 
	 * Leaves that are already in the tree are ignored - e.g. diamond patterns in a directed graph
	 * or cycles will not re-enter the search tree.
	 * 
	 * @param newrefs a new set of leaves to add to the tree. Each reference is mapped to a set
	 * of adjacent references in the reference graph. Each adjacent reference is then mapped to a
	 * boolean which indicates whether the reference should terminate the search.
	 * @return the leaves that were added to the tree.
	 * @throws IllegalStateException if the search is already complete.
	 */
	public Set<Reference> updateTree(final Map<Reference, Map<Reference, Boolean>> newrefs) {
		if (complete) {
			throw new IllegalStateException("Search is complete");
		}
		final List<ReferenceTreeNode> newleaves = new LinkedList<>();
		final Set<Reference> retrefs = new HashSet<>();
		for (final ReferenceTreeNode leaf: leaves) {
			final Reference r = leaf.getReference();
			if (newrefs.containsKey(r)) {
				for (final Reference newleaf: newrefs.get(r).keySet()) {
					if (newrefs.get(r).get(newleaf)) { //search is done
						generatePath(leaf, newleaf);
						completeSearch();
						return new HashSet<>();
					}
					// if newleaf already seen, an <= length path exists in the tree
					if (!tree.contains(newleaf)) {
						tree.add(newleaf);
						newleaves.add(new ReferenceTreeNode(newleaf, leaf));
						retrefs.add(newleaf);
					}
				}
			}
		}
		leaves = newleaves;
		if (retrefs.isEmpty()) {
			completeSearch();
		}
		return retrefs;
	}

	private void completeSearch() {
		complete = true;
		tree.clear();
		leaves = null;
	}
	
	private void generatePath(final ReferenceTreeNode leaf, final Reference newleaf) {
		final List<Reference> foundpath = new ArrayList<>();
		ReferenceTreeNode pos = leaf;
		foundpath.add(newleaf);
		foundpath.add(pos.getReference());
		while (pos.getParent() != null) {
			pos = pos.getParent();
			foundpath.add(pos.getReference());
		}
		path = Collections.unmodifiableList(foundpath);
	}
	
	/** Returns the root of this search tree.
	 * @return the tree root.
	 */
	public Reference getRoot() {
		return root;
	}
	
	/** Check if the search is complete, whether by encountering a termination reference or
	 * exhausting the search.
	 * @return true if the search is complete or false otherwise.
	 */
	public boolean isComplete() {
		return complete;
	}
	
	/** Check if a path from a termination reference to the root reference was found. If false,
	 * the search was exhausted without finding a termination reference.
	 * @return true if a path was found, false otherwise.
	 */
	public boolean isPathFound() {
		return path != null;
	}
	
	/** Gets the path from the termination reference to the root reference of this search tree.
	 * @return the path from the termination reference to the root.
	 * @throws IllegalStateException if no path was found.
	 */
	public List<Reference> getPath() {
		if (path == null) {
			throw new IllegalStateException("Search is not complete or failed");
		}
		return path;
	}
	
}
