package us.kbase.workspace.database;

import java.util.List;

import us.kbase.typedobj.core.ObjectPaths;

public class ObjIDWithChainAndSubset extends ObjectIDWithRefPath {

	//TODO TEST unit tests
	
	private final ObjectPaths paths;
	
	public ObjIDWithChainAndSubset(
			final ObjectIdentifier id,
			final List<ObjectIdentifier> chain,
			final ObjectPaths subset) {
		super(id, chain);
		this.paths = subset == null ? ObjectPaths.EMPTY : subset;
	}
	
	public ObjectPaths getPaths() {
		return paths;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((paths == null) ? 0 : paths.hashCode());
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
		if (paths == null) {
			if (other.paths != null)
				return false;
		} else if (!paths.equals(other.paths))
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ObjIDWithChainsAndSubset [paths=");
		builder.append(paths);
		builder.append(", getChain()=");
		builder.append(getChain());
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
