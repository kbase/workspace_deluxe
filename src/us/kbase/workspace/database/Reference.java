package us.kbase.workspace.database;

import us.kbase.typedobj.idref.RemappedId;

/** A absolute reference to a workspace object. getID() returns the reference as a string the the
 * form X/Y/Z where
 * 
 * X is the workspace ID
 * Y is the object ID
 * Z if the object version
 * 
 * @author gaprice@lbl.gov
 *
 */
public class Reference implements RemappedId {
	
	private final long workspaceID;
	private final long objectID;
	private final int version;

	/** Create a reference to an object using numeric IDs.
	 * @param workspaceID the workspace ID of the object.
	 * @param objectID the ID of the object.
	 * @param version the version of the object.
	 */
	public Reference(final long workspaceID, final long objectID, final int version) {
		if (workspaceID < 1 || objectID < 1 || version < 1) {
			throw new IllegalArgumentException("All arguments must be > 0");
		}
		this.workspaceID = workspaceID;
		this.objectID = objectID;
		this.version = version;
	}
	
	/** Create a reference to an object using a reference string in the format X/Y/Z as defined
	 * above.
	 * @param ref a reference string.
	 */
	public Reference(final String ref) {
		final ObjectIdentifier oi = ObjectIdentifier.parseObjectReference(ref);
		if (!oi.isAbsolute()) {
			throw new IllegalArgumentException(String.format(
					"ref %s is not an absolute reference", ref));
		}
		workspaceID = oi.getWorkspaceIdentifier().getId();
		objectID = oi.getId();
		version = oi.getVersion();
	}

	/** Returns the ID of the workspac for this object.
	 * @return the workspace ID.
	 */
	public long getWorkspaceID() {
		return workspaceID;
	}

	/** Returns the ID for this object.
	 * @return the object ID.
	 */
	public long getObjectID() {
		return objectID;
	}

	/** Returns the version of this object.
	 * @return the object version.
	 */
	public int getVersion() {
		return version;
	}

	@Override
	public String getId() {
		return toString();
	}
	
	@Override
	public String toString() {
		return workspaceID + ObjectIdentifier.REFERENCE_SEP + objectID +
				ObjectIdentifier.REFERENCE_SEP + version;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (objectID ^ (objectID >>> 32));
		result = prime * result + version;
		result = prime * result + (int) (workspaceID ^ (workspaceID >>> 32));
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
		Reference other = (Reference) obj;
		if (objectID != other.objectID) {
			return false;
		}
		if (version != other.version) {
			return false;
		}
		if (workspaceID != other.workspaceID) {
			return false;
		}
		return true;
	}
}
