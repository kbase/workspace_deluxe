package us.kbase.workspace.database.mongo;

import java.util.Date;
import java.util.Map;

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
	final private Map<String, String> usermeta;
	
	MongoWSInfo(final long id, final String name, final WorkspaceUser owner,
			final Date modDate, final long approxObjs,
			final Permission userPermission, final boolean globalRead,
			final boolean locked, final Map<String, String> usermeta) {
		this.id = id;
		this.name = name;
		this.owner = owner;
		this.modDate = modDate;
		this.approxObjs = approxObjs;
		this.userPermission = userPermission;
		this.globalRead = globalRead;
		this.locked = locked;
		this.usermeta = usermeta;
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
	public Map<String, String> getUserMeta() {
		return usermeta;
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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (approxObjs ^ (approxObjs >>> 32));
		result = prime * result + (globalRead ? 1231 : 1237);
		result = prime * result + (int) (id ^ (id >>> 32));
		result = prime * result + (locked ? 1231 : 1237);
		result = prime * result + ((modDate == null) ? 0 : modDate.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((owner == null) ? 0 : owner.hashCode());
		result = prime * result
				+ ((userPermission == null) ? 0 : userPermission.hashCode());
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
		MongoWSInfo other = (MongoWSInfo) obj;
		if (approxObjs != other.approxObjs)
			return false;
		if (globalRead != other.globalRead)
			return false;
		if (id != other.id)
			return false;
		if (locked != other.locked)
			return false;
		if (modDate == null) {
			if (other.modDate != null)
				return false;
		} else if (!modDate.equals(other.modDate))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (owner == null) {
			if (other.owner != null)
				return false;
		} else if (!owner.equals(other.owner))
			return false;
		if (userPermission != other.userPermission)
			return false;
		return true;
	}
}
