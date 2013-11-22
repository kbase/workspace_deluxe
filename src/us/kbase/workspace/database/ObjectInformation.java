package us.kbase.workspace.database;

import java.util.Date;


public interface ObjectInformation {
	
	public long getObjectId();
	public String getObjectName();
	public String getTypeString();
	public long getSize();
	public Date getCreatedDate();
	public int getVersion();
	public WorkspaceUser getCreator();
	public long getWorkspaceId();
	public String getWorkspaceName();
	public String getCheckSum();
	public int hashCode();
	public boolean equals(Object obj);
}
