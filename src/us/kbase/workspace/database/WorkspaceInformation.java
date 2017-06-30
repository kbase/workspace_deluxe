package us.kbase.workspace.database;

import java.util.Date;

public interface WorkspaceInformation {
	
	//TODO NOW get rid of this interface and make a value class with a builder.
	//TODO CODE add is deleted.
	
	public long getId();
	public String getName();
	public WorkspaceUser getOwner();
	public Date getModDate();
	public long getApproximateObjects(); //TODO NOW change to max objects
	public Permission getUserPermission();
	public boolean isGloballyReadable();
	public boolean isLocked();
	public String getLockState();
	public UncheckedUserMetadata getUserMeta();
	public boolean equals(Object obj);
	public int hashCode();
}
