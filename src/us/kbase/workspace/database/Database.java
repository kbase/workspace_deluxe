package us.kbase.workspace.database;

public interface Database {

	public WorkspaceMetaData createWorkspace(String owner, String name,
			boolean globalread, String description);

	public String getBackendType(); 

}
