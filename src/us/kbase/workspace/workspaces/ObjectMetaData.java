package us.kbase.workspace.workspaces;

import java.util.Date;
import java.util.Map;

public interface ObjectMetaData {
	
	public int getObjectId();
	public String getObjectName();
	public TypeId getTypeId();
	public Date getCreatedDate();
	public int getVersion();
	public String getCreator();
	public int getWorkspaceId();
	public String getCheckSum();
	public Map<String, Object> getUserMetaData();
}
