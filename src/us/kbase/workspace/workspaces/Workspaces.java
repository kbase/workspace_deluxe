package us.kbase.workspace.workspaces;

import java.util.List;

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
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
			boolean globalread, String description) {
		new WorkspaceIdentifier(wsname, user); //check for errors
		if(description != null && description.length() > MAX_WS_DESCRIPTION) {
			description = description.substring(0, MAX_WS_DESCRIPTION);
		}
		return db.createWorkspace(user, wsname, globalread, description);
	}
	
	public String getWorkspaceDescription(WorkspaceIdentifier workspace)
			throws NoSuchWorkspaceException {
		return db.getWorkspaceDescription(workspace);
		
	}

	public void setPermissions(String userName, WorkspaceIdentifier wsi,
			List<String> users, Permission permission) throws
			NoSuchWorkspaceException, WorkspaceAuthorizationException {
		System.out.println(db.getPermission(wsi,  userName));
		if(db.getPermission(wsi, userName) != Permission.ADMIN) {
			throw new WorkspaceAuthorizationException(String.format(
					"User %s does not have permission to set permissions on workspace %s",
					userName, wsi.getIdentifierString()));
		}
		db.setPermissions(wsi, users, permission);
	}
}
