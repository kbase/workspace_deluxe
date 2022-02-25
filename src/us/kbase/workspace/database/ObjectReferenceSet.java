package us.kbase.workspace.database;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


/** A set of references from or to a workspace object.
 * @author gaprice@lbl.gov
 *
 */
public class ObjectReferenceSet {
	
	//TODO TEST unittests
	
	private Reference objref;
	private Set<Reference> refs;
	private boolean incoming;
	
	/** Create a reference set.
	 * @param objref the reference of the object with which the reference set is associated.
	 * @param refs the reference set.
	 * @param incoming whether the reference set is incoming (true) or outgoing (false) references
	 * to or from the object. 
	 */
	public ObjectReferenceSet(
			final Reference objref,
			final Set<Reference> refs,
			final boolean incoming) {
		super();
		if (objref == null || refs == null) {
			throw new NullPointerException("objref and refs cannot be null");
		}
		this.objref = objref;
		this.refs = Collections.unmodifiableSet(new HashSet<Reference>(refs));
		this.incoming = incoming;
	}

	/** The reference of the source or target object.
	 * @return the object reference.
	 */
	public Reference getObjectReference() {
		return objref;
	}
	
	/** The references to or from the workspace object.
	 * @return the set of references.
	 */
	public Set<Reference> getReferenceSet() {
		return refs;
	}
	
	
	/** Whether the references are incoming or outgoing references.
	 * @return true the references are incoming, false if outgoing.
	 */
	public boolean isIncoming() {
		return incoming;
	}
	
	/** Check if this reference set contains a reference. The object reference
	 * is not included in this check.
	 * @param r the reference to check
	 * @return true if the reference is included in this set, false if not.
	 */
	public boolean contains(Reference r) {
		return refs.contains(r);
	}
	
	/** Check if this reference set contains a reference. The object reference
	 * is not included in this check.
	 * @param oi the ObjectIdentifier containing the reference. The
	 * ObjectIdentifier must be absolute.
	 * @return true if the reference is included in this set, false if not.
	 */
	public boolean contains(ObjectIdentifier oi) {
		if (!oi.isAbsolute()) {
			throw new IllegalArgumentException("oi must be absolute");
		}
		return refs.contains(new Reference(oi.getWorkspaceIdentifier().getId(), oi.getID(),
				oi.getVersion()));
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ObjectReferenceSet [objref=");
		builder.append(objref);
		builder.append(", refs=");
		builder.append(refs);
		builder.append(", incoming=");
		builder.append(incoming);
		builder.append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (incoming ? 1231 : 1237);
		result = prime * result + ((objref == null) ? 0 : objref.hashCode());
		result = prime * result + ((refs == null) ? 0 : refs.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ObjectReferenceSet other = (ObjectReferenceSet) obj;
		if (incoming != other.incoming) {
			return false;
		}
		if (objref == null) {
			if (other.objref != null) {
				return false;
			}
		} else if (!objref.equals(other.objref)) {
			return false;
		}
		if (refs == null) {
			if (other.refs != null) {
				return false;
			}
		} else if (!refs.equals(other.refs)) {
			return false;
		}
		return true;
	}
	
	
}
