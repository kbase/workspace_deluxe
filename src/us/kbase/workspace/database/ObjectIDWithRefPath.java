package us.kbase.workspace.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/** An object identifier for an object, along with either 1) A reference path from the object to
 * the target object, or 2) an instruction that the object specified is the target object and
 * access must be verified via a search through the object reference graph.
 * @author gaprice@lbl.gov
 *
 */
public class ObjectIDWithRefPath extends ObjectIdentifier {

	//TODO TEST unit tests
	
	private final List<ObjectIdentifier> refpath;
	private final boolean lookup;
	
	/** Create an object identifier for an object. The permissions for this object must be 
	 * ascertained via a search up the object reference DAG until a readable object is found,
	 * granting permission to read this object.
	 * @param id the identifier of the target object.
	 */
	public ObjectIDWithRefPath(final ObjectIdentifier id) {
		super(id);
		lookup = true;
		refpath = Collections.unmodifiableList(new LinkedList<ObjectIdentifier>());
	}
	
	/** Create an object identifier for an object which is at the head of an explicitly defined
	 * reference path that grants access to a target object at the end of the path.
	 * @param id the identifier for the object at the head of the reference path.
	 * @param refpath the reference path from the head of the path (not inclusive) to the target
	 * object.
	 */
	public ObjectIDWithRefPath(
			final ObjectIdentifier id,
			List<ObjectIdentifier> refpath) {
		super(id);
		if (refpath == null) {
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
		lookup = false;
	}
	
	/** Returns the reference path to the target object, excluding the first object in the path.
	 * @return the reference path.
	 */
	public List<ObjectIdentifier> getRefPath() {
		return refpath;
	}
	
	/** Returns whether this object identifier has a reference path.
	 * @return true if this object identifier has a reference path, false otherwise.
	 */
	public boolean hasRefPath() {
		return !refpath.isEmpty();
	}
	
	/** Returns the last (target) object identifier in the reference path.
	 * @return the last object identifier in the reference path.
	 * @throws IllegalStateException if this object has no reference path.
	 */
	public ObjectIdentifier getLast() {
		if (!hasRefPath()) {
			throw new IllegalStateException(
					"This object identifier has no reference chain");
		}
		return refpath.get(refpath.size() - 1);
	}
	
	/** Returns true if this object identifier represents a target object for which the permissions
	 * must be ascertained by a object reference DAG search.
	 * @return
	 */
	public boolean isLookupRequired() {
		return lookup;
	}

	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (lookup ? 1231 : 1237);
		result = prime * result + ((refpath == null) ? 0 : refpath.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ObjectIDWithRefPath other = (ObjectIDWithRefPath) obj;
		if (lookup != other.lookup) {
			return false;
		}
		if (refpath == null) {
			if (other.refpath != null) {
				return false;
			}
		} else if (!refpath.equals(other.refpath)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ObjectIDWithRefPath [refpath=");
		builder.append(refpath);
		builder.append(", lookup=");
		builder.append(lookup);
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
