package us.kbase.workspace.database.mongo;

import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.Reference;

//could make this an inner class and provide method to get parent to make
//sure MRs don't cross instances of the MongoDBDatabase
//same for the other Mongo* classes that implement an interface
//probably not worth the trouble
public class MongoReference implements Reference {
	
	private final long workspaceID;
	private final long objectID;
	private final int version;

	MongoReference(final long workspaceID, final long objectID,
			final int version) {
		if (workspaceID < 1 || objectID < 1 || version < 1) {
			throw new IllegalArgumentException("All arguments must be > 0");
		}
		this.workspaceID = workspaceID;
		this.objectID = objectID;
		this.version = version;
	}
	
	MongoReference(final String ref) {
		final ObjectIdentifier oi = ObjectIdentifier.parseObjectReference(ref);
		if (!oi.isAbsolute()) {
			throw new IllegalArgumentException(String.format(
					"ref %s is not an absolute reference", ref));
		}
		workspaceID = oi.getWorkspaceIdentifier().getId();
		objectID = oi.getId();
		version = oi.getVersion();
	}

	@Override
	public long getWorkspaceID() {
		return workspaceID;
	}

	@Override
	public long getObjectID() {
		return objectID;
	}

	@Override
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

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (objectID ^ (objectID >>> 32));
		result = prime * result + version;
		result = prime * result + (int) (workspaceID ^ (workspaceID >>> 32));
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
		MongoReference other = (MongoReference) obj;
		if (objectID != other.objectID)
			return false;
		if (version != other.version)
			return false;
		if (workspaceID != other.workspaceID)
			return false;
		return true;
	}
}
