package us.kbase.workspace.database;

import java.util.List;

import us.kbase.typedobj.core.SubsetSelection;

/** An object identifier and subset selection for an object, along with either 1) A reference path
 * from the object to the target object, or 2) an instruction that the object specified is the
 * target object and access must be verified via a search through the object reference graph.
 * @author gaprice@lbl.gov
 *
 */
public class ObjIDWithRefPathAndSubset extends ObjectIDWithRefPath {

	//TODO TEST unit tests
	
	private final SubsetSelection subset;
	
	/** Create an object identifier for an object. The permissions for this object must be 
	 * ascertained via a search up the object reference DAG until a readable object is found,
	 * granting permission to read this object.
	 * @param id the identifier of the target object.
	 * @param subset the subset of the object to return.
	 */
	public ObjIDWithRefPathAndSubset(
			final ObjectIdentifier id,
			final SubsetSelection subset) {
		super(id);
		this.subset = subset == null ? SubsetSelection.EMPTY : subset;
	}

	/** Create an object identifier for an object which is at the head of an explicitly defined
	 * reference path that grants access to a target object at the end of the path.
	 * @param id the identifier for the object at the head of the reference path.
	 * @param refpath the reference path from the head of the path (not inclusive) to the target
	 * object.
	 * @param subset the subset of the object to return.
	 */
	public ObjIDWithRefPathAndSubset(
			final ObjectIdentifier id,
			final List<ObjectIdentifier> refpath,
			final SubsetSelection subset) {
		super(id, refpath);
		this.subset = subset == null ? SubsetSelection.EMPTY : subset;
	}
	
	
	/** Get the subset of the object to return.
	 * @return the subset selection for the object.
	 */
	public SubsetSelection getSubSet() {
		return subset;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((subset == null) ? 0 : subset.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		ObjIDWithRefPathAndSubset other = (ObjIDWithRefPathAndSubset) obj;
		if (subset == null) {
			if (other.subset != null)
				return false;
		} else if (!subset.equals(other.subset))
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ObjIDWithChainsAndSubset [subset=");
		builder.append(subset);
		builder.append(", getRefPath()=");
		builder.append(getRefPath());
		builder.append(", getWorkspaceIdentifier()=");
		builder.append(getWorkspaceIdentifier());
		builder.append(", getName()=");
		builder.append(getName());
		builder.append(", getId()=");
		builder.append(getId());
		builder.append(", getVersion()=");
		builder.append(getVersion());
		builder.append("]");
		return builder.toString();
	}

}
