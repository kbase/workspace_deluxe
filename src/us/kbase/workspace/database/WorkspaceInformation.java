package us.kbase.workspace.database;

import java.util.Date;
import java.util.Map;

public interface WorkspaceInformation {
	
	public long getId();
	public String getName();
	public WorkspaceUser getOwner();
	public Date getModDate();
	public long getApproximateObjects();
	public Permission getUserPermission();
	public boolean isGloballyReadable();
	public boolean isLocked();
	public String getLockState();
	public Map<String, String> getUserMeta();
	public boolean equals(Object obj);
	public int hashCode();
}
