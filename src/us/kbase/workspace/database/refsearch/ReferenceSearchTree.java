package us.kbase.workspace.database.refsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.workspace.database.ObjectReferenceSet;
import us.kbase.workspace.database.Reference;

public class ReferenceSearchTree {
	
	//TODO NOW TEST Unit tests
	//TODO NOW JAVADOC

	/* Could prune tree from already seen nodes and dead ends if memory use becomes an issue */
	
	private final Reference root;
	private final Set<Long> workspaceIDs;
	private final Set<Reference> tree = new HashSet<>();
	private List<ReferenceTreeNode> leaves = new LinkedList<>();
	private List<Reference> path = null;
	boolean complete = false;

	public ReferenceSearchTree(final Reference root, final Set<Long> workspaceIDs) {
		if (root == null) {
			throw new NullPointerException("root");
		}
		if (workspaceIDs == null || workspaceIDs.isEmpty()) {
			throw new IllegalArgumentException("workspaceIDs cannot be null or empty");
		}
		this.workspaceIDs = workspaceIDs;
		this.root = root;
		tree.add(root);
		leaves.add(new ReferenceTreeNode(root, null));
	}
	
	private void checkComplete() {
		if (complete) {
			throw new IllegalStateException("Search is complete");
		}
	}

	public Set<Reference> checkForPaths(final Map<Reference, Boolean> readableAndExists) {
		checkComplete();
		final Set<Reference> leavesToReturn = new HashSet<>();
		for (final ReferenceTreeNode n: leaves) {
			final Reference r = n.getReference();
			if (readableAndExists.containsKey(r) && readableAndExists.get(r)) {
				generatePath(n);
				leavesToReturn.clear();
				break;
			}
			leavesToReturn.add(r);
		}
		if (leavesToReturn.isEmpty()) {
			complete = true;
			tree.clear();
			leaves = null;
		}
		return leavesToReturn;
	}
	
	public Set<Reference> updateTree(final Map<Reference, ObjectReferenceSet> newrefs) {
		checkComplete();
		final List<ReferenceTreeNode> newleaves = new LinkedList<>();
		final Set<Reference> checkread = new HashSet<>();
		for (final ReferenceTreeNode leaf: leaves) {
			final Reference r = leaf.getReference();
			if (newrefs.containsKey(r)) {
				for (final Reference newleaf: newrefs.get(r).getReferenceSet()) {
					// if newleaf already seen, an <= length path exists in the tree
					if (!tree.contains(newleaf)) {
						tree.add(newleaf);
						newleaves.add(new ReferenceTreeNode(newleaf, leaf));
						if (workspaceIDs.contains(newleaf.getWorkspaceID())) {
							checkread.add(newleaf);
						}
					}
				}
			}
		}
		leaves = newleaves;
		return checkread;
	}
	
	private void generatePath(final ReferenceTreeNode leaf) {
		final List<Reference> foundpath = new ArrayList<>();
		ReferenceTreeNode pos = leaf;
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
