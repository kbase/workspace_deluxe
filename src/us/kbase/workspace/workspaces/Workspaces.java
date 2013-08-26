package us.kbase.workspace.workspaces;

import java.util.List;
import java.util.Map;

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;

public class Workspaces {
	
	private final static int MAX_WS_DESCRIPTION = 1000;
	
	private final Database db;
	
	public Workspaces(Database db) {
		if (db == null) {
			throw new NullPointerException("db");
		}
		this.db = db;
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
	
	public String getWorkspaceDescription(String user, WorkspaceIdentifier workspace)
			throws NoSuchWorkspaceException, WorkspaceAuthorizationException {
		if(Permission.READ.compareTo(db.getPermission(workspace, user)) > 0 ) {
			throw new WorkspaceAuthorizationException(String.format(
					"User %s does not have permission to read workspace %s",
					user, workspace.getIdentifierString()));
		}
		return db.getWorkspaceDescription(workspace);
	}

	public void setPermissions(String user, WorkspaceIdentifier wsi,
			List<String> users, Permission permission) throws
			NoSuchWorkspaceException, WorkspaceAuthorizationException {
		if(Permission.ADMIN.compareTo(db.getPermission(wsi, user)) > 0) {
			throw new WorkspaceAuthorizationException(String.format(
					"User %s does not have permission to set permissions on workspace %s",
					user, wsi.getIdentifierString()));
		}
		db.setPermissions(wsi, users, permission);
	}

	public Map<String, Permission> getPermissions(WorkspaceIdentifier wsi,
			String user) throws NoSuchWorkspaceException {
		Map<String, Permission> perms = db.getUserAndGlobalPermission(wsi, user);
		if (Permission.ADMIN.compareTo(perms.get(user)) > 0) {
			return perms;
		}
		return db.getAllPermissions(wsi, user);
	}

	public WorkspaceMetaData getWorkspaceMetaData(WorkspaceIdentifier wksp,
				String user) throws WorkspaceAuthorizationException,
				NoSuchWorkspaceException {
		if(Permission.READ.compareTo(db.getPermission(wksp, user)) > 0) {
			throw new WorkspaceAuthorizationException(String.format(
					"User %s does not have permission to read workspace %s",
					user, wksp.getIdentifierString()));
		}
		return db.getWorkspaceMetadata(wksp, user);
	}
}
