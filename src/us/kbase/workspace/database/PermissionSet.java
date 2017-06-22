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
	
	private static final Perms NO_PERMS = new Perms(Permission.NONE, false);
	
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
	private final AllUsers globalUser;
	private final Map<ResolvedWorkspaceID, Perms> perms;
	
	private PermissionSet(
			final WorkspaceUser user,
			final AllUsers globalUser,
			final Map<ResolvedWorkspaceID, Perms> perms) {
		this.user = user;
		this.globalUser = globalUser;
		this.perms = perms;
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
	public AllUsers getGlobalUser() {
		return globalUser;
	}
	
	/** Returns the user's explicit permission for the workspace.
	 * Returns NONE if the workspace does not exist in this permission set.
	 * @param rwsi the workspace of interest
	 * @return the user's explicit permission.
	 */
	public Permission getUserPermission(final ResolvedWorkspaceID rwsi) {
		if (!hasWorkspace(rwsi)) {
			return Permission.NONE;
		}
		return perms.get(rwsi).getPerm();
	}

	/** Returns whether the user has a particular permission for the workspace
	 * @param rwsi the workspace of interest
	 * @param perm the permission to check
	 * @return whether the user has that permission
	 */
	public boolean hasUserPermission(final ResolvedWorkspaceID rwsi, final Permission perm) {
		return getUserPermission(rwsi).compareTo(perm) > -1;
	}
	
	/** Returns the user's overall permission for the workspace, taking world-readability into
	 * account.
	 * Returns NONE if the workspace does not exist in this permission set.
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
	
	/** Returns whether the user has a particular permission for the workspace,
	 * taking world-readability into account
	 * @param rwsi the workspace of interest
	 * @param perm the permission to check
	 * @return the user's overall permission
	 */
	public boolean hasPermission(final ResolvedWorkspaceID rwsi, final Permission perm) {
		return getPermission(rwsi).compareTo(perm) > -1;
	}

	/** Check whether a workspace is world readable or not.
	 * Returns false if the workspace does not exist in this permission set.
	 * @param rwsi the workspace to check.
	 * @return true if the workspace is world-readable, false otherwise.
	 */
	public boolean isWorldReadable(final ResolvedWorkspaceID rwsi) {
		if (!hasWorkspace(rwsi)) {
			return false;
		}
		return perms.get(rwsi).isWorldReadable();
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
	
	@Override
	public String toString() {
		return "PermissionSet [user=" + user + ", globalUser="
				+ globalUser + ", perms=" + perms + "]";
	}
	
	/** Create a new permission set builder.
	 * @param user the user to whom the permissions apply.
	 * @param globalUser the name of the global user, usually '*'.
	 * @return a new builder.
	 */
	public static Builder getBuilder(final WorkspaceUser user, final AllUsers globalUser) {
		return new Builder(user, globalUser);
	}
	
	/** A permission set builder.
	 * @author gaprice@lbl.gov
	 *
	 */
	public static class Builder {
		
		private final WorkspaceUser user;
		private final AllUsers globalUser;
		private final Map<ResolvedWorkspaceID, Perms> perms = new HashMap<>();
		
		private Builder(final WorkspaceUser user, final AllUsers globalUser) {
			if (globalUser == null) {
				throw new IllegalArgumentException(
						"Global user cannot be null");
			}
			this.user = user;
			this.globalUser = globalUser;
		}
	
	
		/** Add a workspace to this permission set.
		 * @param rwsi the workspace.
		 * @param userPerm the user's permission for the workspace.
		 * @param globalPerm the global permission for the workspace, either READ or NONE.
		 * @return this builder.
		 */
		public Builder withWorkspace(
				final ResolvedWorkspaceID rwsi,
				Permission userPerm,
				final Permission globalPerm) {
			checkWS(rwsi);
			if (userPerm == null) {
				userPerm = Permission.NONE;
			}
			if (user == null && !Permission.NONE.equals(userPerm)) {
				throw new IllegalArgumentException(
						"anonymous users can't have user specific permissions");
			}
			if (globalPerm != null && Permission.READ.compareTo(globalPerm) < 0) {
				throw new IllegalArgumentException(
						"Illegal global permission: " + globalPerm);
			}
			final boolean globalread = Permission.READ.equals(globalPerm);
			if (userPerm.equals(Permission.NONE) && !globalread) {
				throw new IllegalArgumentException("Cannot add unreadable workspace");
			}
			perms.put(rwsi, new Perms(userPerm, globalread));
			return this;
		}
	
		private void checkWS(final ResolvedWorkspaceID rwsi) {
			if (rwsi == null) {
				throw new IllegalArgumentException("Workspace ID cannot be null");
			}
			if (perms.containsKey(rwsi)) {
				throw new IllegalArgumentException("Permissions for workspace " + 
						rwsi.getID() + " have already been set");
			}
		}
		
		/** Add an unreadable workspace to this permission set.
		 * @param rwsi
		 * @return this builder.
		 */
		public Builder withUnreadableWorkspace(final ResolvedWorkspaceID rwsi) {
			checkWS(rwsi);
			perms.put(rwsi, NO_PERMS);
			return this;
		}
		
		/** Returns true if a particular workspace is in this permission set builder.
		 * @param ws the workspace to check.
		 * @return true if the workspace exists in this permission set builder.
		 */
		public boolean hasWorkspace(final ResolvedWorkspaceID ws) {
			return perms.containsKey(ws);
		}
		
		/** Build the permissions set.
		 * @return the new permissions set.
		 */
		public PermissionSet build() {
			return new PermissionSet(user, globalUser, perms);
		}
	}
}
