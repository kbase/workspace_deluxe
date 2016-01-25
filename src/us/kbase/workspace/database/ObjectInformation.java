package us.kbase.workspace.database;

import java.util.Date;


public interface ObjectInformation {
	
	public long getObjectId();
	public String getObjectName();
	public String getTypeString();
	public long getSize();
	public Date getSavedDate();
	public int getVersion();
	public WorkspaceUser getSavedBy();
	public long getWorkspaceId();
	public String getWorkspaceName();
	public String getCheckSum();
	public UncheckedUserMetadata getUserMetaData();
	public boolean equals(Object obj);
	public int hashCode();
}
