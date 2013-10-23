package us.kbase.workspace.database;

public interface Reference {
	
	public long getWorkspaceID();
	public long getObjectID();
	public int getVersion();
	@Override
	public String toString();
}
