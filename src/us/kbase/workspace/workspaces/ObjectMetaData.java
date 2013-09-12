package us.kbase.workspace.workspaces;

import java.util.Date;

public interface ObjectMetaData {
	
	public int getObjectId();
	public String getObjectName();
	public String getTypeString();
	public int getSize();
	public Date getCreatedDate();
	public int getVersion();
	public WorkspaceUser getCreator();
	public int getWorkspaceId();
	public String getCheckSum();
}
