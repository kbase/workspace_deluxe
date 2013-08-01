package us.kbase.workspace.database;

import us.kbase.workspace.Workspace;
import us.kbase.workspace.WorkspaceMetadata;

public interface Database {
	
	public abstract WorkspaceMetadata createWorkspace(Workspace ws);

}
