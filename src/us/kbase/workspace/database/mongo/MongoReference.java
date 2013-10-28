package us.kbase.workspace.database.mongo;

import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.Reference;

public class MongoReference implements Reference {
	
	private final long workspaceID;
	private final long objectID;
	private final int version;

	MongoReference(long workspaceID, long objectID, int version) {
		if (workspaceID < 1 || objectID < 1 || version < 1) {
			throw new IllegalArgumentException("All arguments must be > 0");
		}
		this.workspaceID = workspaceID;
		this.objectID = objectID;
		this.version = version;
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
	public String toString() {
		return workspaceID + ObjectIdentifier.REFERENCE_SEP + objectID +
				ObjectIdentifier.REFERENCE_SEP + version;
	}

}
