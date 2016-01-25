package us.kbase.workspace.database.mongo;

import java.util.Date;

import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.UncheckedUserMetadata;
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
	final private UncheckedUserMetadata meta;
	
	MongoObjectInfo(final long id, final String name,
			final String typeString, final Date createdDate, final int version,
			final WorkspaceUser creator, final ResolvedMongoWSID workspaceid,
			final String chksum, final long size,
			final UncheckedUserMetadata meta) {
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
	public UncheckedUserMetadata getUserMetaData() {
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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((chksum == null) ? 0 : chksum.hashCode());
		result = prime * result + (int) (id ^ (id >>> 32));
		result = prime * result + ((meta == null) ? 0 : meta.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((savedBy == null) ? 0 : savedBy.hashCode());
		result = prime * result
				+ ((savedDate == null) ? 0 : savedDate.hashCode());
		result = prime * result + (int) (size ^ (size >>> 32));
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		result = prime * result + version;
		result = prime * result + (int) (workspaceId ^ (workspaceId >>> 32));
		result = prime * result
				+ ((workspaceName == null) ? 0 : workspaceName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MongoObjectInfo other = (MongoObjectInfo) obj;
		if (chksum == null) {
			if (other.chksum != null)
				return false;
		} else if (!chksum.equals(other.chksum))
			return false;
		if (id != other.id)
			return false;
		if (meta == null) {
			if (other.meta != null)
				return false;
		} else if (!meta.equals(other.meta))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (savedBy == null) {
			if (other.savedBy != null)
				return false;
		} else if (!savedBy.equals(other.savedBy))
			return false;
		if (savedDate == null) {
			if (other.savedDate != null)
				return false;
		} else if (!savedDate.equals(other.savedDate))
			return false;
		if (size != other.size)
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		if (version != other.version)
			return false;
		if (workspaceId != other.workspaceId)
			return false;
		if (workspaceName == null) {
			if (other.workspaceName != null)
				return false;
		} else if (!workspaceName.equals(other.workspaceName))
			return false;
		return true;
	}

}
