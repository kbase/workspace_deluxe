package us.kbase.workspace.database;

import java.util.Set;


/** A set of references from or to a workspace object.
 * @author gaprice@lbl.gov
 *
 */
public interface ObjectReferenceSet {

	/** The reference of the source or target object.
	 * @return the object reference.
	 */
	public Reference getObjectReference();
	
	/** The references to or from the workspace object.
	 * @return the set of references.
	 */
	public Set<Reference> getReferenceSet();
	
	
	/** Whether the references are incoming or outgoing references.
	 * @return true the references are incoming, false if outgoing.
	 */
	public boolean isIncoming();
	
	/** Check if this reference set contains a reference. The object reference
	 * is not included in this check.
	 * @param r the reference to check
	 * @return true if the reference is included in this set, false if not.
	 */
	public boolean contains(Reference r);
	
	/** Check if this reference set contains a reference. The object reference
	 * is not included in this check.
	 * @param oi the ObjectIdentifier containing the reference. The
	 * ObjectIdentifier must be absolute.
	 * @return true if the reference is included in this set, false if not.
	 */
	public boolean contains(ObjectIdentifier oi);
	
	@Override
	public String toString();
	@Override
	public int hashCode();
	@Override
	public boolean equals(Object obj);
}
