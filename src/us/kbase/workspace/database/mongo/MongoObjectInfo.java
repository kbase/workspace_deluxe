package us.kbase.workspace.database.mongo;

import java.util.Date;
import java.util.Map;

import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.WorkspaceUser;

public class MongoObjectInfo implements ObjectInformation {
	
	final private long id;
	final private String name;
	final private String type;
	final private Date savedDate;
	final private int version;
	final private WorkspaceUser savedBy;
	final private long workspaceId;
	final private String workspaceName;
	final private String chksum;
	final private long size;
	final private Map<String, String> meta;
	
	MongoObjectInfo(final long id, final String name,
			final String typeString, final Date createdDate, final int version,
			final WorkspaceUser creator, final ResolvedMongoWSID workspaceid,
			final String chksum, final long size,
			final Map<String, String> meta) {
		//no error checking for now, add if needed
		this.id = id;
		this.name = name;
		this.type = typeString;
		this.savedDate = createdDate;
		this.version = version;
		this.savedBy = creator;
		this.workspaceId = workspaceid.getID();
		this.workspaceName = workspaceid.getName();
		this.chksum = chksum;
		this.size = size;
		this.meta = meta;
	}

	@Override
	public long getObjectId() {
		return id;
	}

	@Override
	public String getObjectName() {
		return name;
	}

	@Override
	public String getTypeString() {
		return type;
	}

	@Override
	public Date getSavedDate() {
		return savedDate;
	}

	@Override
	public int getVersion() {
		return version;
	}

	@Override
	public WorkspaceUser getSavedBy() {
		return savedBy;
	}

	@Override
	public long getWorkspaceId() {
		return workspaceId;
	}
	
	@Override
	public String getWorkspaceName() {
		return workspaceName;
	}

	@Override
	public String getCheckSum() {
		return chksum;
	}
	
	
	@Override
	public long getSize() {
		return size;
	}
	
	@Override
	public Map<String, String> getUserMetaData() {
		return meta;
	}

	@Override
	public String toString() {
		return "MongoObjectInfo [id=" + id + ", name=" + name + ", type="
				+ type + ", savedDate=" + savedDate + ", version=" + version
				+ ", savedBy=" + savedBy + ", workspaceId=" + workspaceId
				+ ", workspaceName=" + workspaceName + ", chksum=" + chksum
				+ ", size=" + size + ", meta=" + meta + "]";
	}

}
