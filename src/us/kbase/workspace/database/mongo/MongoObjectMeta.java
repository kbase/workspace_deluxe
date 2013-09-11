package us.kbase.workspace.database.mongo;

import java.util.Date;

import us.kbase.workspace.workspaces.ObjectMetaData;

public class MongoObjectMeta implements ObjectMetaData {
	
	final private int id;
	final private String name;
	final private String type;
	final private Date createdDate;
	final private int version;
	final private String creator;
	final private int workspaceId;
	final private String chksum;
	final private Object userMeta;
	final private int size;
	
	public MongoObjectMeta(int id, String name, String typeString, Date createdDate,
			int version, String creator, int workspaceid, String chksum,
			Object userMeta, int size) {
		//no error checking for now, add if needed
		this.id = id;
		this.name = name;
		this.type = typeString;
		this.createdDate = createdDate;
		this.version = version;
		this.creator = creator;
		this.workspaceId = workspaceid;
		this.chksum = chksum;
		this.userMeta = userMeta;
		this.size = size;
		
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
	public String getTypeString() {
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
	public Object getUserMetaData() {
		return userMeta;
	}
	
	@Override
	public int getSize() {
		return size;
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
