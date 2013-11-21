package us.kbase.workspace.database.mongo;

import java.util.Date;

import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceUser;

public class MongoWSInfo implements WorkspaceInformation {
	
	final private long id;
	final private String name;
	final private WorkspaceUser owner;
	final private Date modDate;
	final private long approxObjs;
	final private Permission userPermission;
	final private boolean globalRead;
	
	MongoWSInfo(final long id, final String name, final WorkspaceUser owner,
			final Date modDate, final long approxObjs,
			final Permission userPermission, final boolean globalRead) {
		this.id = id;
		this.name = name;
		this.owner = owner;
		this.modDate = modDate;
		this.approxObjs = approxObjs;
		this.userPermission = userPermission;
		this.globalRead = globalRead;
		
	}

	@Override
	public long getId() {
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
	public long getApproximateObjects() {
		return approxObjs;
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
