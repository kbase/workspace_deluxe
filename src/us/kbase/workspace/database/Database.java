package us.kbase.workspace.database;

import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.workspaces.ObjectMetaData;
import us.kbase.workspace.workspaces.Permission;
import us.kbase.workspace.workspaces.User;
import us.kbase.workspace.workspaces.WorkspaceUser;
import us.kbase.workspace.workspaces.WorkspaceIdentifier;
import us.kbase.workspace.workspaces.WorkspaceMetaData;
import us.kbase.workspace.workspaces.WorkspaceObjectCollection;

public interface Database {

	public String getBackendType();

	public WorkspaceMetaData createWorkspace(WorkspaceUser owner, String wsname,
			boolean globalread, String description) throws
			PreExistingWorkspaceException, WorkspaceCommunicationException;
	
	public void setPermissions(WorkspaceIdentifier wsi,
			List<WorkspaceUser> users, Permission perm) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException;
	
	public Permission getPermission(WorkspaceUser user, WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException;
	
	public Map<WorkspaceIdentifier, Permission> getPermissions(
			WorkspaceUser user, Set<WorkspaceIdentifier> wsis)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException;

	public Map<User, Permission> getUserAndGlobalPermission(WorkspaceUser user,
			WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public Map<User, Permission> getAllPermissions(
			WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException;

	public WorkspaceMetaData getWorkspaceMetadata(WorkspaceUser user,
			WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException;

	public String getWorkspaceDescription(WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException;
	
	public List<ObjectMetaData> saveObjects(WorkspaceUser user,
			WorkspaceObjectCollection objects) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, NoSuchObjectException;
}
