package us.kbase.workspace.database;

import java.util.Date;



public interface WorkspaceInformation {
	
	public long getId();
	public String getName();
	public WorkspaceUser getOwner();
	public Date getModDate();
	public long getApproximateObjects();
	public Permission getUserPermission();
	public boolean isGloballyReadable();
}
