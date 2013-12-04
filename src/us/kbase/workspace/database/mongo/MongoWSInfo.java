package us.kbase.workspace.database.mongo;

import java.util.Date;

import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceUser;

public class MongoWSInfo implements WorkspaceInformation {
	
	public static final String UNLOCKED = "unlocked";
	public static final String LOCKED = "locked";
	public static final String PUBLISHED = "published";
	
	final private long id;
	final private String name;
	final private WorkspaceUser owner;
	final private Date modDate;
	final private long approxObjs;
	final private Permission userPermission;
	final private boolean globalRead;
	final private boolean locked;
	
	MongoWSInfo(final long id, final String name, final WorkspaceUser owner,
			final Date modDate, final long approxObjs,
			final Permission userPermission, final boolean globalRead,
			final boolean locked) {
		this.id = id;
		this.name = name;
		this.owner = owner;
		this.modDate = modDate;
		this.approxObjs = approxObjs;
		this.userPermission = userPermission;
		this.globalRead = globalRead;
		this.locked = locked;
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
	public boolean isLocked() {
		return locked;
	}
	
	@Override
	public String getLockState() {
		if (!locked) {
			return UNLOCKED;
		}
		if (!isGloballyReadable()) {
			return LOCKED;
		}
		return PUBLISHED;
	}

	@Override
	public String toString() {
		return "MongoWSMeta [id=" + id + ", name=" + name + ", owner=" + owner
				+ ", modDate=" + modDate + ", userPermission=" + userPermission 
				+ ", globalRead=" + globalRead + "]";
	}
}
