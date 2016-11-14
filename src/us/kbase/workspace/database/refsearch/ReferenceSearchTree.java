package us.kbase.workspace.database.refsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.workspace.database.Reference;

public class ReferenceSearchTree {
	
	//TODO NOW TEST Unit tests
	//TODO NOW JAVADOC

	/* Could prune tree from already seen nodes and dead ends if memory use becomes an issue */
	
	private final Reference root;
	private final Set<Reference> tree = new HashSet<>();
	private List<ReferenceTreeNode> leaves = new LinkedList<>();
	private List<Reference> path = null;
	boolean complete = false;

	public ReferenceSearchTree(final Reference root) {
		if (root == null) {
			throw new NullPointerException("root");
		}
		this.root = root;
		tree.add(root);
		leaves.add(new ReferenceTreeNode(root, null));
	}
	
	private void checkComplete() {
		if (complete) {
			throw new IllegalStateException("Search is complete");
		}
	}

	public Set<Reference> updateTree(final Map<Reference, Map<Reference, Boolean>> newrefs) {
		checkComplete();
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
	
	public Reference getRoot() {
		return root;
	}
	
	public boolean isComplete() {
		return complete;
	}
	
	public boolean isPathFound() {
		return path != null;
	}
	
	public List<Reference> getPath() {
		if (path == null) {
			throw new IllegalStateException("Search is not complete or failed");
		}
		return path;
	}
	
}
