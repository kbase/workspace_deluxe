package us.kbase.workspace.workspaces;

import java.util.Date;


public interface WorkspaceMetaData {
	
	public int getId();
	public String getName();
	public String getOwner();
	public Date getModDate();
	public Permission getUserPermission();
	public boolean isGloballyReadable();
}
