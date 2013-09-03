package us.kbase.workspace.database;

import java.util.List;
import java.util.Map;

import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.workspaces.Permission;
import us.kbase.workspace.workspaces.WorkspaceIdentifier;
import us.kbase.workspace.workspaces.WorkspaceMetaData;

public interface Database {

	public String getBackendType();

	public WorkspaceMetaData createWorkspace(String owner, String wsname,
			boolean globalread, String description) throws
			PreExistingWorkspaceException, WorkspaceCommunicationException;
	
	public void setPermissions(WorkspaceIdentifier wsi, List<String> users,
			Permission perm) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException;
	
	public Permission getPermission(String user, WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException;

	Map<String, Permission> getUserAndGlobalPermission(String user,
			WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException;
	
	public Map<String, Permission> getAllPermissions(
			WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException;

	public WorkspaceMetaData getWorkspaceMetadata(String user,
			WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException;

	public String getWorkspaceDescription(WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException;

	public void setAllUsersSymbol(String allUsers);

}
