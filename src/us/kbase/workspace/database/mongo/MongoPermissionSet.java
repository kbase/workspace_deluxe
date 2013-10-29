package us.kbase.workspace.database.mongo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceUser;

public class MongoPermissionSet implements PermissionSet {
	
	private final WorkspaceUser user;
	private final Map<ResolvedWorkspaceID, Permission> userPerms = 
			new HashMap<ResolvedWorkspaceID, Permission>();
	private final Map<ResolvedWorkspaceID, Boolean> worldRead = 
			new HashMap<ResolvedWorkspaceID, Boolean>();
	
	MongoPermissionSet(final WorkspaceUser user) {
		this.user = user;
	}

	@Override
	public WorkspaceUser getUser() {
		return user;
	}
	
	void setPermission(final ResolvedMongoWSID rwsi, Permission userPerm,
			final Permission globalPerm) {
		if (rwsi == null) {
			throw new IllegalArgumentException(
					"Mongo workspace ID cannot be null");
		}
		if (userPerm == null) {
			userPerm = Permission.NONE;
		}
		if (globalPerm != null && Permission.READ.compareTo(globalPerm) < 0) {
			throw new IllegalArgumentException(
					"Illegal global permission in database: " + globalPerm);
		}
		userPerms.put(rwsi, userPerm);
		worldRead.put(rwsi, Permission.READ.equals(globalPerm));
	}
	
	@Override
	public Permission getPermission(ResolvedWorkspaceID rwsi) {
		final Permission p = getUserPermission(rwsi);
		if (Permission.NONE.equals(p)) {
			return isWorldReadable(rwsi) ? Permission.READ : p;
		} else {
			return p;
		}
	}

	@Override
	public Permission getUserPermission(ResolvedWorkspaceID rwsi) {
		if (!userPerms.containsKey(rwsi)) {
			throw new IllegalArgumentException(
					"Workspace not registered: " + rwsi);
		}
		return userPerms.get(rwsi);
	}

	@Override
	public boolean isWorldReadable(ResolvedWorkspaceID rwsi) {
		if (!worldRead.containsKey(rwsi)) {
			throw new IllegalArgumentException(
					"Workspace not registered: " + rwsi);
		}
		return worldRead.get(rwsi);
	}

	@Override
	public Set<ResolvedWorkspaceID> getWorkspaces() {
		return userPerms.keySet();
	}
}
