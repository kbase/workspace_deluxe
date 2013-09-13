package us.kbase.workspace.database.mongo;

import java.util.Date;

import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.WorkspaceMetaData;
import us.kbase.workspace.database.WorkspaceUser;

public class MongoWSMeta implements WorkspaceMetaData {
	
	final private int id;
	final private String name;
	final private WorkspaceUser owner;
	final private Date modDate;
	final private Permission userPermission;
	final private boolean globalRead;
	
	public MongoWSMeta(int id, String name, WorkspaceUser owner, Date modDate,
			Permission userPermission, boolean globalRead) {
		this.id = id;
		this.name = name;
		this.owner = owner;
		this.modDate = modDate;
		this.userPermission = userPermission;
		this.globalRead = globalRead;
		
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public WorkspaceUser getOwner() {
		return owner;
	}

	@Override
	public Date getModDate() {
		return modDate;
	}


	@Override
	public Permission getUserPermission() {
		return userPermission;
	}

	@Override
	public boolean isGloballyReadable() {
		return globalRead;
	}

	@Override
	public String toString() {
		return "MongoWSMeta [id=" + id + ", name=" + name + ", owner=" + owner
				+ ", modDate=" + modDate + ", userPermission=" + userPermission 
				+ ", globalRead=" + globalRead + "]";
	}

}
