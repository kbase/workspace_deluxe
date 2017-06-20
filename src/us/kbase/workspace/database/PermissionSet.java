package us.kbase.workspace.database;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A set of workspace permissions for a user. This object is not updated
 * further after retrieval from the database.
 * 
 * Note that this permission set can include deleted workspaces.
 * @author gaprice@lbl.gov
 *
 */
public class PermissionSet {
	
	//TODO NOW TEST unit tests 
	//TODO NOW builder
	
	private static class Perms {
		private final Permission perm;
		private final boolean worldRead;
		
		public Perms(final Permission perm, final boolean worldReadable) {
			this.perm = perm;
			this.worldRead = worldReadable;
		}

		public Permission getPerm() {
			return perm;
		}

		public boolean isWorldReadable() {
			return worldRead;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("Perms [perm=");
			builder.append(perm);
			builder.append(", worldRead=");
			builder.append(worldRead);
			builder.append("]");
			return builder.toString();
		}
	}
	private final WorkspaceUser user;
	private final User globalUser;
	private final Map<ResolvedWorkspaceID, Perms> perms = 
			new HashMap<ResolvedWorkspaceID, Perms>();
	private static final Perms NO_PERMS = new Perms(Permission.NONE, false);
	
	/** Create a new permission set.
	 * @param user the user to whom the permissions apply.
	 * @param globalUser the name of the global user, usually '*'.
	 */
	public PermissionSet(final WorkspaceUser user, final User globalUser) {
		if (globalUser == null) {
			throw new IllegalArgumentException(
					"Global user cannot be null");
		}
		this.user = user;
		this.globalUser = globalUser;
	}

	/** Get the user for whom the permissions apply.
	 * @return the user name.
	 */
	public WorkspaceUser getUser() {
		return user;
	}
	
	/** Get the global (e.g. for global permissions) username, usually '*'.
	 * @return the global username
	 */
	public User getGlobalUser() {
		return globalUser;
	}
	
	/** Returns the user's explicit permission for the workspace
	 * @param rwsi the workspace of interest
	 * @return the user's explicit permission
	 */
	public Permission getUserPermission(final ResolvedWorkspaceID rwsi) {
		if (!perms.containsKey(rwsi)) {
			throw new IllegalArgumentException(
					"Workspace not registered: " + rwsi);
		}
		return perms.get(rwsi).getPerm();
	}

	/** Returns the user's explicit permission for the workspace
	 * @param rwsi the workspace of interest
	 * @param returnNone return Permission.NONE as the permission if the
	 * workspace does not exist in the permission set rather than throwing an
	 * error
	 * @return the user's explicit permission
	 */
	public Permission getUserPermission(final ResolvedWorkspaceID rwsi, final boolean returnNone) {
		if (returnNone && !hasWorkspace(rwsi)) {
			return Permission.NONE;
		}
		return getUserPermission(rwsi);
	}
	
	/** Returns whether the user has a particular permission for the workspace
	 * @param rwsi the workspace of interest
	 * @param perm the permission to check
	 * @return whether the user has that permission
	 */
	public boolean hasUserPermission(final ResolvedWorkspaceID rwsi, final Permission perm) {
		return getUserPermission(rwsi).compareTo(perm) > -1;
	}
	
	/** Returns the user's overall permission for the workspace, taking
	 * world-readability into account
	 * @param rwsi the workspace of interest
	 * @return the user's overall permission
	 */
	public Permission getPermission(final ResolvedWorkspaceID rwsi) {
		final Permission p = getUserPermission(rwsi);
		if (Permission.NONE.equals(p)) {
			return isWorldReadable(rwsi) ? Permission.READ : p;
		} else {
			return p;
		}
	}
	
	/** Returns the user's overall permission for the workspace, taking
	 * world-readability into account
	 * @param rwsi the workspace of interest
	 * @param returnNone return Permission.NONE as the permission if the
	 * workspace does not exist in the permission set rather than throwing an
	 * error
	 * @return the user's overall permission
	 */
	public Permission getPermission(final ResolvedWorkspaceID rwsi, final boolean returnNone) {
		if (returnNone && !hasWorkspace(rwsi)) {
			return Permission.NONE;
		}
		return getPermission(rwsi);
	}

	/** Returns whether the user has a particular permission for the workspace,
	 * taking world-readability into account
	 * @param rwsi the workspace of interest
	 * @param perm the permission to check
	 * @return the user's overall permission
	 */
	public boolean hasPermission(final ResolvedWorkspaceID rwsi, final Permission perm) {
		return getPermission(rwsi).compareTo(perm) > -1;
	}

	/** Check whether a workspace is world readable or not. Throws an
	 * exception if the workspace does not exist in the permission set.
	 * @param rwsi the workspace to check.
	 * @return true if the workspace is world-readable, false otherwise.
	 */
	public boolean isWorldReadable(final ResolvedWorkspaceID rwsi) {
		if (!perms.containsKey(rwsi)) {
			throw new IllegalArgumentException(
					"Workspace not registered: " + rwsi);
		}
		return perms.get(rwsi).isWorldReadable();
	}
	
	/** Check whether a workspace is world readable or not.
	 * @param rwsi the workspace to check.
	 * @param returnFalse set true to return false rather than throw an
	 * exception if the workspace does not exist in this permission set.
	 * @return true if the workspace is world-readable, false otherwise.
	 */
	public boolean isWorldReadable(final ResolvedWorkspaceID rwsi, final boolean returnFalse) {
		if (returnFalse && !hasWorkspace(rwsi)) {
			return false;
		}
		return isWorldReadable(rwsi);
	}

	/** Returns the set of workspaces in this permission set. Workspaces may
	 * be deleted.
	 * @return the set of workspaces in this permission set.
	 */
	public Set<ResolvedWorkspaceID> getWorkspaces() {
		return Collections.unmodifiableSet(perms.keySet());
	}
	
	/** Returns true if a particular workspace is in this permission set.
	 * @param ws the workspace to check.
	 * @return true if the workspace exists in this permission set.
	 */
	public boolean hasWorkspace(final ResolvedWorkspaceID ws) {
		return perms.containsKey(ws);
	}
	
	/** Returns true if there are no workspaces in this permission set.
	 * @return true if the permission set has no workspaces.
	 */
	public boolean isEmpty() {
		return perms.isEmpty();
	}
	
	/** Add a workspace to this permission set.
	 * @param rwsi the workspace.
	 * @param userPerm the user's permission for the workspace.
	 * @param globalPerm the global permission for the workspace, either READ or NONE.
	 */
	public void setPermission(
			final ResolvedWorkspaceID rwsi,
			Permission userPerm,
			final Permission globalPerm) {
		checkWS(rwsi);
		if (userPerm == null) {
			userPerm = Permission.NONE;
		}
		if (globalPerm != null && Permission.READ.compareTo(globalPerm) < 0) {
			throw new IllegalArgumentException(
					"Illegal global permission in database: " + globalPerm);
		}
		final boolean globalread = Permission.READ.equals(globalPerm);
		if (userPerm.equals(Permission.NONE) && !globalread) {
			throw new IllegalArgumentException("Cannot add unreadable workspace");
		}
		perms.put(rwsi, new Perms(userPerm, globalread));
	}

	private void checkWS(final ResolvedWorkspaceID rwsi) {
		if (rwsi == null) {
			throw new IllegalArgumentException("Mongo workspace ID cannot be null");
		}
		if (perms.containsKey(rwsi)) {
			throw new IllegalArgumentException("Permissions for workspace " + 
					rwsi.getID() + " have already been set");
		}
	}
	
	/** Add an unreadable workspace to this Permission set.
	 * @param rwsi
	 */
	public void setUnreadable(final ResolvedWorkspaceID rwsi) {
		checkWS(rwsi);
		perms.put(rwsi, NO_PERMS);
	}

	@Override
	public String toString() {
		return "MongoPermissionSet [user=" + user + ", globalUser="
				+ globalUser + ", perms=" + perms + "]";
	}
}
