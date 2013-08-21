package us.kbase.workspace.database;

import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;

public interface Database {

	public WorkspaceMetaData createWorkspace(String owner, String name,
			boolean globalread, String description);
	public String getWorkspaceDescription(int workspaceId) throws NoSuchWorkspaceException;
	public String getWorkspaceDescription(String workspaceName) throws NoSuchWorkspaceException;

	public String getBackendType(); 

}
