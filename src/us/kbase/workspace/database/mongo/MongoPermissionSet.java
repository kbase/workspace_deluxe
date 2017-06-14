package us.kbase.workspace.database.mongo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.WorkspaceUser;

public class MongoPermissionSet implements PermissionSet {
	
	//TODO TEST unit tests 
	
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
	
	MongoPermissionSet(final WorkspaceUser user, final User globalUser) {
		if (globalUser == null) {
			throw new IllegalArgumentException(
					"Global user cannot be null");
		}
		this.user = user;
		this.globalUser = globalUser;
	}

	@Override
	public WorkspaceUser getUser() {
		return user;
	}
	
	@Override
	public User getGlobalUser() {
		return globalUser;
	}
	
	void setPermission(final ResolvedMongoWSID rwsi, Permission userPerm,
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

	private void checkWS(final ResolvedMongoWSID rwsi) {
		if (rwsi == null) {
			throw new IllegalArgumentException("Mongo workspace ID cannot be null");
		}
		if (perms.containsKey(rwsi)) {
			throw new IllegalArgumentException("Permissions for workspace " + 
					rwsi.getID() + " have already been set");
		}
	}
	
	void setUnreadable(final ResolvedMongoWSID rwsi) {
		checkWS(rwsi);
		perms.put(rwsi, NO_PERMS);
	}
	
	@Override
	public Permission getPermission(final ResolvedWorkspaceID rwsi,
			final boolean returnNone) {
		if (returnNone && !hasWorkspace(rwsi)) {
			return Permission.NONE;
		}
		return getPermission(rwsi);
	}
	
	@Override
	public Permission getPermission(final ResolvedWorkspaceID rwsi) {
		final Permission p = getUserPermission(rwsi);
		if (Permission.NONE.equals(p)) {
			return isWorldReadable(rwsi) ? Permission.READ : p;
		} else {
			return p;
		}
	}
	
	@Override
	public boolean hasPermission(final ResolvedWorkspaceID rwsi,
			final Permission perm) {
		return getPermission(rwsi).compareTo(perm) > -1;
	}

	@Override
	public Permission getUserPermission(final ResolvedWorkspaceID rwsi,
			final boolean returnNone) {
		if (returnNone && !hasWorkspace(rwsi)) {
			return Permission.NONE;
		}
		return getUserPermission(rwsi);
	}
	
	@Override
	public Permission getUserPermission(final ResolvedWorkspaceID rwsi) {
		if (!perms.containsKey(rwsi)) {
			throw new IllegalArgumentException(
					"Workspace not registered: " + rwsi);
		}
		return perms.get(rwsi).getPerm();
	}
	
	@Override
	public boolean hasUserPermission(final ResolvedWorkspaceID rwsi,
			final Permission perm) {
		return getUserPermission(rwsi).compareTo(perm) > -1;
	}

	@Override
	public boolean isWorldReadable(final ResolvedWorkspaceID rwsi,
			final boolean returnFalse) {
		if (returnFalse && !hasWorkspace(rwsi)) {
			return false;
		}
		return isWorldReadable(rwsi);
	}
	
	@Override
	public boolean isWorldReadable(final ResolvedWorkspaceID rwsi) {
		if (!perms.containsKey(rwsi)) {
			throw new IllegalArgumentException(
					"Workspace not registered: " + rwsi);
		}
		return perms.get(rwsi).isWorldReadable();
	}

	@Override
	public Set<ResolvedWorkspaceID> getWorkspaces() {
		return Collections.unmodifiableSet(perms.keySet());
	}
	
	@Override
	public boolean hasWorkspace(final ResolvedWorkspaceID ws) {
		return perms.containsKey(ws);
	}
	
	@Override
	public boolean isEmpty() {
		return perms.isEmpty();
	}

	@Override
	public String toString() {
		return "MongoPermissionSet [user=" + user + ", globalUser="
				+ globalUser + ", perms=" + perms + "]";
	}
}
