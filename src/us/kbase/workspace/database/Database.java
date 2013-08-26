package us.kbase.workspace.database;

import java.util.List;
import java.util.Map;

import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.workspaces.Permission;
import us.kbase.workspace.workspaces.WorkspaceIdentifier;
import us.kbase.workspace.workspaces.WorkspaceMetaData;

public interface Database {

	public WorkspaceMetaData createWorkspace(String owner, String name,
			boolean globalread, String description) throws PreExistingWorkspaceException;
	public String getWorkspaceDescription(WorkspaceIdentifier workspace)
			throws NoSuchWorkspaceException;
	public void setPermissions(WorkspaceIdentifier workspace, List<String> users,
			Permission perm) throws NoSuchWorkspaceException;
	public Permission getPermission(WorkspaceIdentifier workspace, String user)
			throws NoSuchWorkspaceException;

	public String getBackendType();
	public Map<String, Permission> getPermissions(WorkspaceIdentifier wsi,
			String userName) throws NoSuchWorkspaceException;
	public WorkspaceMetaData getWorkspaceMetadata(WorkspaceIdentifier wksp,
			String user) throws NoSuchWorkspaceException; 

}
