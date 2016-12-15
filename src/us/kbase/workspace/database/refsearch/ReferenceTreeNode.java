package us.kbase.workspace.database.refsearch;

import us.kbase.workspace.database.Reference;

/** A node in a reference graph search tree.
 * @author gaprice@lbl.gov
 *
 */
public class ReferenceTreeNode {
	
	private final Reference ref;
	private final ReferenceTreeNode parent;
	
	/** Create a new search tree node.
	 * @param ref the reference that corresponds to this node.
	 * @param parent the parent of this node in the search tree. null for the root node.
	 */
	public ReferenceTreeNode(final Reference ref, final ReferenceTreeNode parent) {
		super();
		// no point in unpacking the ref since the search tree keeps a set of every reference in
		// the tree
		if (ref == null) {
			throw new NullPointerException("ref");
		}
		this.ref = ref;
		this.parent = parent;
	}

	/** Get the parent of this node.
	 * @return the node parent, or null if this is a root node.
	 */
	public ReferenceTreeNode getParent() {
		return parent;
	}

	/** Get the reference corresponding to this node.
	 * @return this node's reference.
	 */
	public Reference getReference() {
		return ref;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ReferenceTreeNode [ref = ");
		builder.append(ref);
		builder.append(", parent ref = ");
		builder.append(parent == null ? "null" : parent.getReference().toString());
		builder.append("]");
		return builder.toString();
	}
}
