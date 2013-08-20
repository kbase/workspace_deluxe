package us.kbase.workspace.database;

import us.kbase.workspace.Workspace;

public interface Database {

	public Workspace createWorkspace(String owner, String name,
			boolean globalread, String description);

	public String getBackendType(); 

}
