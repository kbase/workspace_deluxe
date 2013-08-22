package us.kbase.workspace.database;

import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.workspaces.WorkspaceMetaData;

public interface Database {

	public WorkspaceMetaData createWorkspace(String owner, String name,
			boolean globalread, String description);
	public String getWorkspaceDescription(int workspaceId) throws NoSuchWorkspaceException;
	public String getWorkspaceDescription(String workspaceName) throws NoSuchWorkspaceException;

	public String getBackendType(); 

}
