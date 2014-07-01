package us.kbase.workspace.database;

import us.kbase.typedobj.idref.RemappedId;

public interface Reference extends RemappedId {
	
	public long getWorkspaceID();
	public long getObjectID();
	public int getVersion();
	@Override
	public String toString();
}
