package us.kbase.workspace.database.mongo;

import java.util.Date;
import java.util.Map;

import us.kbase.workspace.workspaces.ObjectMetaData;
import us.kbase.workspace.workspaces.TypeId;

public class MongoObjectMeta implements ObjectMetaData {
	
	final private int id;
	final private String name;
	final private TypeId type;
	final private Date createdDate;
	final private int version;
	final private String creator;
	final private int workspaceId;
	final private String chksum;
	final private Map<String, Object> userMeta;
	
	public MongoObjectMeta(int id, String name, TypeId type, Date createdDate,
			int version, String creator, int workspaceid, String chksum,
			Map<String, Object> userMeta) {
		//no error checking for now, add if needed
		this.id = id;
		this.name = name;
		this.type = type;
		this.createdDate = createdDate;
		this.version = version;
		this.creator = creator;
		this.workspaceId = workspaceid;
		this.chksum = chksum;
		this.userMeta = userMeta;
		
	}

	@Override
	public int getObjectId() {
		return id;
	}

	@Override
	public String getObjectName() {
		return name;
	}

	@Override
	public TypeId getTypeId() {
		return type;
	}

	@Override
	public Date getCreatedDate() {
		return createdDate;
	}

	@Override
	public int getVersion() {
		return version;
	}

	@Override
	public String getCreator() {
		return creator;
	}

	@Override
	public int getWorkspaceId() {
		return workspaceId;
	}

	@Override
	public String getCheckSum() {
		return chksum;
	}
	
	//meta is mutable
	@Override
	public Map<String, Object> getUserMetaData() {
		return userMeta;
	}

	@Override
	public String toString() {
		return "MongoObjectMeta [id=" + id + ", name=" + name + ", type="
				+ type + ", createdDate=" + createdDate + ", version="
				+ version + ", creator=" + creator + ", workspaceId="
				+ workspaceId + ", chksum=" + chksum + ", userMeta=" + userMeta
				+ "]";
	}

}
