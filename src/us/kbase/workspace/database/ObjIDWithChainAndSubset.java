package us.kbase.workspace.database;

import java.util.List;

import us.kbase.typedobj.core.SubsetSelection;

public class ObjIDWithChainAndSubset extends ObjectIDWithRefPath {

	//TODO TEST unit tests
	//TODO JAVADOC
	
	private final SubsetSelection subset;
	
	public ObjIDWithChainAndSubset(
			final ObjectIdentifier id,
			final List<ObjectIdentifier> refpath,
			final SubsetSelection subset) {
		super(id, refpath);
		this.subset = subset == null ? SubsetSelection.EMPTY : subset;
	}
	
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
		ObjIDWithChainAndSubset other = (ObjIDWithChainAndSubset) obj;
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
