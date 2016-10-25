package us.kbase.workspace.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ObjectIDWithRefPath extends ObjectIdentifier {

	//TODO TEST unit tests
	//TODO JAVADOC
	
	private List<ObjectIdentifier> refpath;
	
	public ObjectIDWithRefPath(
			final ObjectIdentifier id,
			List<ObjectIdentifier> refpath) {
		super(id);
		if (refpath == null || refpath.isEmpty()) {
			refpath = new LinkedList<ObjectIdentifier>();
		} 
		this.refpath = Collections.unmodifiableList(
				new ArrayList<ObjectIdentifier>(refpath));
		
		for (final ObjectIdentifier oi: this.refpath) {
			if (oi == null) {
				throw new IllegalArgumentException(
						"Nulls are not allowed in reference chains");
			}
		}
	}
	
	public List<ObjectIdentifier> getRefPath() {
		return refpath;
	}
	
	public boolean hasChain() {
		return !refpath.isEmpty();
	}
	
	public ObjectIdentifier getLast() {
		if (!hasChain()) {
			throw new IllegalStateException(
					"This object identifier has no reference chain");
		}
		return refpath.get(refpath.size() - 1);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((refpath == null) ? 0 : refpath.hashCode());
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
		if (refpath == null) {
			if (other.refpath != null)
				return false;
		} else if (!refpath.equals(other.refpath))
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ObjectIDWithRefChains [refpath=");
		builder.append(refpath);
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
