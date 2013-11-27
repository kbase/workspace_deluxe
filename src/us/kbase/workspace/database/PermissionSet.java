package us.kbase.workspace.database;

import java.util.Set;

/**
 * A set of workspace permissions for a user. This object is not updated
 * further after retrieval from the database.
 * @author gaprice@lbl.gov
 *
 */
public interface PermissionSet {
	
	public WorkspaceUser getUser();
	public User getGlobalUser();
	/** Returns the user's explicit permission for the workspace
	 * @param rwsi the workspace of interest
	 * @return the user's explicit permission
	 */
	public Permission getUserPermission(ResolvedWorkspaceID rwsi);
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
	/** Returns whether the user has a particular permission for the workspace,
	 * taking world-readability into account
	 * @param rwsi the workspace of interest
	 * @param perm the permission to check
	 * @return the user's overall permission
	 */
	public boolean hasPermission(ResolvedWorkspaceID rwsi, Permission perm);
	public boolean isWorldReadable(ResolvedWorkspaceID rwsi);
	public Set<ResolvedWorkspaceID> getWorkspaces();
	public boolean isEmpty();
	public boolean hasUnreadableWorkspace();
}
