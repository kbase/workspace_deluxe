package us.kbase.workspace.workspaces;

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;

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
}
