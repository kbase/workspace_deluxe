package us.kbase.workspace.database.mongo;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectReferenceSet;
import us.kbase.workspace.database.Reference;

public class MongoObjRefSet implements ObjectReferenceSet {

	//TODO TEST unit tests
	
	private Reference objref;
	private Set<Reference> refs;
	private boolean incoming;
	
	MongoObjRefSet(
			final Reference objref,
			final Set<MongoReference> refs,
			final boolean incoming) {
		super();
		if (objref == null || refs == null) {
			throw new NullPointerException("objref and refs cannot be null");
		}
		this.objref = objref;
		this.refs = Collections.unmodifiableSet(
				new HashSet<Reference>(refs));
		this.incoming = incoming;
	}

	@Override
	public Reference getObjectReference() {
		return objref;
	}

	@Override
	public Set<Reference> getReferenceSet() {
		return refs;
	}

	@Override
	public boolean isIncoming() {
		return incoming;
	}
	
	@Override
	public boolean contains(final Reference r) {
		return refs.contains(r);
	}
	
	@Override
	public boolean contains(final ObjectIdentifier oi) {
		if (!oi.isAbsolute()) {
			throw new IllegalArgumentException("oi must be absolute");
		}
		return refs.contains(new MongoReference(
				oi.getWorkspaceIdentifier().getId(), oi.getId(),
				oi.getVersion()));
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("MongoObjRefSet [objref=");
		builder.append(objref);
		builder.append(", refs=");
		builder.append(refs);
		builder.append(", incoming=");
		builder.append(incoming);
		builder.append("]");
		return builder.toString();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (incoming ? 1231 : 1237);
		result = prime * result + ((objref == null) ? 0 : objref.hashCode());
		result = prime * result + ((refs == null) ? 0 : refs.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MongoObjRefSet other = (MongoObjRefSet) obj;
		if (incoming != other.incoming)
			return false;
		if (objref == null) {
			if (other.objref != null)
				return false;
		} else if (!objref.equals(other.objref))
			return false;
		if (refs == null) {
			if (other.refs != null)
				return false;
		} else if (!refs.equals(other.refs))
			return false;
		return true;
	}
}
