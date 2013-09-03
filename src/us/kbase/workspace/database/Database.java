package us.kbase.workspace.database;

import java.util.List;
import java.util.Map;

import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.workspaces.Permission;
import us.kbase.workspace.workspaces.WorkspaceIdentifier;
import us.kbase.workspace.workspaces.WorkspaceMetaData;

public interface Database {

	public String getBackendType();

	public WorkspaceMetaData createWorkspace(String owner, String wsname,
			boolean globalread, String description) throws PreExistingWorkspaceException;
	
	public void setPermissions(WorkspaceIdentifier wsi, List<String> users,
			Permission perm) throws NoSuchWorkspaceException;
	
	public Permission getPermission(String user, WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException;

	Map<String, Permission> getUserAndGlobalPermission(String user,
			WorkspaceIdentifier wsi) throws NoSuchWorkspaceException;
	
	public Map<String, Permission> getAllPermissions(
			WorkspaceIdentifier wsi) throws NoSuchWorkspaceException;

	public WorkspaceMetaData getWorkspaceMetadata(String user,
			WorkspaceIdentifier wsi) throws NoSuchWorkspaceException;

	public String getWorkspaceDescription(WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException;

	public void setAllUsersSymbol(String allUsers);

}
