package us.kbase.workspace.database.refsearch;

import us.kbase.workspace.database.Reference;

/** A node in a reference graph search tree.
 * @author gaprice@lbl.gov
 *
 */
public class ReferenceTreeNode {
	
	//TODO NOW TEST Unit tests
	
	private final long workspaceID;
	private final long objectID;
	private final int version;
	private final ReferenceTreeNode parent;
	
	/** Create a new search tree node.
	 * @param ref the reference that corresponds to this node.
	 * @param parent the parent of this node in the search tree. null for the root node.
	 */
	public ReferenceTreeNode(final Reference ref, final ReferenceTreeNode parent) {
		super();
		this.workspaceID = ref.getWorkspaceID();
		this.objectID = ref.getObjectID();
		this.version = ref.getVersion();
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
		return new Reference(workspaceID, objectID, version);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ReferenceTreeNode [ref = ");
		builder.append(getReference().toString());
		builder.append(", parent ref = ");
		builder.append(parent == null ? "null" : parent.getReference().toString());
		builder.append("]");
		return builder.toString();
	}
	
	
}
