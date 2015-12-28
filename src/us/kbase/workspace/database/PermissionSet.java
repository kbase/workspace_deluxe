package us.kbase.workspace.database;

import java.util.Set;

/**
 * A set of workspace permissions for a user. This object is not updated
 * further after retrieval from the database.
 * 
 * Note that this permission set can include deleted workspaces.
 * @author gaprice@lbl.gov
 *
 */
public interface PermissionSet {
	
	/** Get the user for whom the permissions apply.
	 * @return the user name.
	 */
	public WorkspaceUser getUser();
	
	/** Get the global (e.g. for global permissions) username, usually '*'.
	 * @return the global username
	 */
	public User getGlobalUser();
	
	/** Returns the user's explicit permission for the workspace
	 * @param rwsi the workspace of interest
	 * @return the user's explicit permission
	 */
	public Permission getUserPermission(ResolvedWorkspaceID rwsi);
	
	/** Returns the user's explicit permission for the workspace
	 * @param rwsi the workspace of interest
	 * @param returnNone return Permission.NONE as the permission if the
	 * workspace does not exist in the permission set rather than throwing an
	 * error
	 * @return the user's explicit permission
	 */
	public Permission getUserPermission(ResolvedWorkspaceID rwsi,
			boolean returnNone);
	
	/** Returns whether the user has a particular permission for the workspace
	 * @param rwsi the workspace of interest
	 * @param perm the permission to check
	 * @return whether the user has that permission
	 */
	public boolean hasUserPermission(ResolvedWorkspaceID rwsi, Permission perm);
	
	/** Returns the user's overall permission for the workspace, taking
	 * world-readability into account
	 * @param rwsi the workspace of interest
	 * @return the user's overall permission
	 */
	public Permission getPermission(ResolvedWorkspaceID rwsi);
	
	/** Returns the user's overall permission for the workspace, taking
	 * world-readability into account
	 * @param rwsi the workspace of interest
	 * @param returnNone return Permission.NONE as the permission if the
	 * workspace does not exist in the permission set rather than throwing an
	 * error
	 * @return the user's overall permission
	 */
	public Permission getPermission(ResolvedWorkspaceID rwsi,
			boolean returnNone);
	
	/** Returns whether the user has a particular permission for the workspace,
	 * taking world-readability into account
	 * @param rwsi the workspace of interest
	 * @param perm the permission to check
	 * @return the user's overall permission
	 */
	public boolean hasPermission(ResolvedWorkspaceID rwsi, Permission perm);
	
	/** Check whether a workspace is world readable or not. Throws an
	 * exception if the workspace does not exist in the permission set.
	 * @param rwsi the workspace to check.
	 * @return true if the workspace is world-readable, false otherwise.
	 */
	public boolean isWorldReadable(ResolvedWorkspaceID rwsi);
	
	/** Check whether a workspace is world readable or not.
	 * @param rwsi the workspace to check.
	 * @param returnFalse set true to return false rather than throw an
	 * exception if the workspace does not exist in this permission set.
	 * @return true if the workspace is world-readable, false otherwise.
	 */
	public boolean isWorldReadable(ResolvedWorkspaceID rwsi,
			boolean returnFalse);
	
	/** Returns the set of workspaces in this permission set. Workspaces may
	 * be deleted.
	 * @return the set of workspaces in this permission set.
	 */
	public Set<ResolvedWorkspaceID> getWorkspaces();
	
	/** Retuns true if a particular workpsace is in this permission set.
	 * @param ws the workspace to check.
	 * @return true if the workspace exists in this permission set.
	 */
	public boolean hasWorkspace(ResolvedWorkspaceID ws);
	
	/** Returns true if there are no workspaces in this permission set.
	 * @return true if the permission set has no workspaces.
	 */
	public boolean isEmpty();
}
