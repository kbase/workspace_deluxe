package us.kbase.workspace.database;

import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.workspaces.WorkspaceObjectCollection;

public interface Database {

	public String getBackendType();
	
	public ResolvedWorkspaceID resolveWorkspace(final WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException;
	
	public Map<WorkspaceIdentifier, ResolvedWorkspaceID> resolveWorkspaces(
			Set<WorkspaceIdentifier> wsis) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException;

	public WorkspaceMetaData createWorkspace(WorkspaceUser owner, String wsname,
			boolean globalread, String description) throws
			PreExistingWorkspaceException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException;
	
	public void setPermissions(ResolvedWorkspaceID wsi,
			List<WorkspaceUser> users, Permission perm) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public Permission getPermission(WorkspaceUser user, ResolvedWorkspaceID wsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public Map<ResolvedWorkspaceID, Permission> getPermissions(
			WorkspaceUser user, Set<ResolvedWorkspaceID> wsis)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	public Map<User, Permission> getUserAndGlobalPermission(WorkspaceUser user,
			ResolvedWorkspaceID wsi) throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException;
	
	public Map<User, Permission> getAllPermissions(
			ResolvedWorkspaceID wsi) throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException;

	public WorkspaceMetaData getWorkspaceMetadata(WorkspaceUser user,
			ResolvedWorkspaceID wsi) throws CorruptWorkspaceDBException,
			WorkspaceCommunicationException;

	public String getWorkspaceDescription(ResolvedWorkspaceID wsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public List<ObjectMetaData> saveObjects(WorkspaceUser user, //TODO resolved ws ids
			WorkspaceObjectCollection objects) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, NoSuchObjectException;
}
