package us.kbase.workspace.workspaces;

import java.util.List;
import java.util.Map;

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;

public class Workspaces {
	
	private final static int MAX_WS_DESCRIPTION = 1000;
	private static final String ALL_USERS = "*";
	
	private final Database db;
	
	public Workspaces(Database db) {
		if (db == null) {
			throw new NullPointerException("db");
		}
		this.db = db;
		db.setAllUsersSymbol(ALL_USERS);
	}
	
	public WorkspaceMetaData createWorkspace(String user, String wsname,
			boolean globalread, String description) throws
			PreExistingWorkspaceException {
		new WorkspaceIdentifier(wsname, user); //check for errors
		if(description != null && description.length() > MAX_WS_DESCRIPTION) {
			description = description.substring(0, MAX_WS_DESCRIPTION);
		}
		return db.createWorkspace(user, wsname, globalread, description);
	}
	
	public String getWorkspaceDescription(String user, WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceAuthorizationException {
		if(Permission.READ.compareTo(db.getPermission(user, wsi)) > 0 ) {
			throw new WorkspaceAuthorizationException(String.format(
					"User %s does not have permission to read workspace %s",
					user, wsi.getIdentifierString()));
		}
		return db.getWorkspaceDescription(wsi);
	}

	public void setPermissions(String user, WorkspaceIdentifier wsi,
			List<String> users, Permission permission) throws
			NoSuchWorkspaceException, WorkspaceAuthorizationException {
		if (Permission.OWNER.compareTo(permission) <= 0) {
			throw new IllegalArgumentException("Cannot set owner permission");
		}
		if(Permission.ADMIN.compareTo(db.getPermission(user, wsi)) > 0) {
			throw new WorkspaceAuthorizationException(String.format(
					"User %s does not have permission to set permissions on workspace %s",
					user, wsi.getIdentifierString()));
		}
		db.setPermissions(wsi, users, permission);
	}

	public Map<String, Permission> getPermissions(String user,
				WorkspaceIdentifier wsi) throws NoSuchWorkspaceException {
		Map<String, Permission> perms = db.getUserAndGlobalPermission(user, wsi);
		if (Permission.ADMIN.compareTo(perms.get(user)) > 0) {
			return perms;
		}
		return db.getAllPermissions(wsi);
	}

	public WorkspaceMetaData getWorkspaceMetaData(String user,
				WorkspaceIdentifier wsi) throws WorkspaceAuthorizationException,
				NoSuchWorkspaceException {
		if(Permission.READ.compareTo(db.getPermission(user, wsi)) > 0) {
			throw new WorkspaceAuthorizationException(String.format(
					"User %s does not have permission to read workspace %s",
					user, wsi.getIdentifierString()));
		}
		return db.getWorkspaceMetadata(user, wsi);
	}
	
	public String getBackendType() {
		return db.getBackendType();
	}
}
