package us.kbase.workspace.database.refsearch;

import us.kbase.workspace.database.Reference;

public class ReferenceTreeNode {
	
	//TODO NOW TEST Unit tests
	//TODO NOW JAVADOC
	
	private final long workspaceID;
	private final long objectID;
	private final int version;
	private final ReferenceTreeNode parent;
	
	public ReferenceTreeNode(final Reference ref, final ReferenceTreeNode parent) {
		super();
		this.workspaceID = ref.getWorkspaceID();
		this.objectID = ref.getObjectID();
		this.version = ref.getVersion();
		this.parent = parent;
	}

	public ReferenceTreeNode getParent() {
		return parent;
	}

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
