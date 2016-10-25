package us.kbase.workspace.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ObjectIDWithRefPath extends ObjectIdentifier {

	//TODO TEST unit tests
	
	private List<ObjectIdentifier> chain;
	
	public ObjectIDWithRefPath(
			final ObjectIdentifier id,
			List<ObjectIdentifier> chain) {
		super(id);
		if (chain == null || chain.isEmpty()) {
			chain = new LinkedList<ObjectIdentifier>();
		} 
		this.chain = Collections.unmodifiableList(
				new ArrayList<ObjectIdentifier>(chain));
		
		for (final ObjectIdentifier oi: this.chain) {
			if (oi == null) {
				throw new IllegalArgumentException(
						"Nulls are not allowed in reference chains");
			}
		}
	}
	
	public List<ObjectIdentifier> getChain() {
		return chain;
	}
	
	public boolean hasChain() {
		return !chain.isEmpty();
	}
	
	public ObjectIdentifier getLast() {
		if (!hasChain()) {
			throw new IllegalStateException(
					"This object identifier has no reference chain");
		}
		return chain.get(chain.size() - 1);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((chain == null) ? 0 : chain.hashCode());
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
		ObjectIDWithRefPath other = (ObjectIDWithRefPath) obj;
		if (chain == null) {
			if (other.chain != null)
				return false;
		} else if (!chain.equals(other.chain))
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ObjectIDWithRefChains [chain=");
		builder.append(chain);
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
