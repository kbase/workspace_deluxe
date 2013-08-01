package us.kbase.workspace.database;

import us.kbase.workspace.Workspace;

public interface Database {
	
	public abstract Workspace createWorkspace(String name);
	public abstract Workspace createWorkspace(String name, String description);
	public abstract Workspace createWorkspace(String name, boolean globalread);
	public abstract Workspace createWorkspace(String name, boolean globalread,
			String description);

}
