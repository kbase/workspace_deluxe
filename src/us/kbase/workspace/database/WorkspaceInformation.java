package us.kbase.workspace.database;

import java.util.Date;

public class WorkspaceInformation {
	
	//TODO NOW builder.
	//TODO CODE add is deleted.
	//TODO NOW JAVADOC
	//TODO NOW TEST
	
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
	final private UncheckedUserMetadata usermeta;
	
	public WorkspaceInformation(
			final long id,
			final String name,
			final WorkspaceUser owner,
			final Date modDate,
			final long approxObjs,
			final Permission userPermission,
			final boolean globalRead,
			final boolean locked,
			final UncheckedUserMetadata meta) {
		this.id = id;
		this.name = name;
		this.owner = owner;
		this.modDate = modDate;
		this.approxObjs = approxObjs;
		this.userPermission = userPermission;
		this.globalRead = globalRead;
		this.locked = locked;
		this.usermeta = meta;
	}

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public WorkspaceUser getOwner() {
		return owner;
	}

	public Date getModDate() {
		return modDate;
	}

	public long getApproximateObjects() { //TODO NOW change to max objects
		return approxObjs;
	}

	public Permission getUserPermission() {
		return userPermission;
	}

	public boolean isGloballyReadable() {
		return globalRead;
	}
	
	public boolean isLocked() {
		return locked;
	}
	

	public UncheckedUserMetadata getUserMeta() {
		return usermeta;
	}
	
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
		StringBuilder builder = new StringBuilder();
		builder.append("WorkspaceInformation [id=");
		builder.append(id);
		builder.append(", name=");
		builder.append(name);
		builder.append(", owner=");
		builder.append(owner);
		builder.append(", modDate=");
		builder.append(modDate);
		builder.append(", approxObjs=");
		builder.append(approxObjs);
		builder.append(", userPermission=");
		builder.append(userPermission);
		builder.append(", globalRead=");
		builder.append(globalRead);
		builder.append(", locked=");
		builder.append(locked);
		builder.append(", usermeta=");
		builder.append(usermeta);
		builder.append("]");
		return builder.toString();
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
		result = prime * result + ((userPermission == null) ? 0 : userPermission.hashCode());
		result = prime * result + ((usermeta == null) ? 0 : usermeta.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		WorkspaceInformation other = (WorkspaceInformation) obj;
		if (approxObjs != other.approxObjs) {
			return false;
		}
		if (globalRead != other.globalRead) {
			return false;
		}
		if (id != other.id) {
			return false;
		}
		if (locked != other.locked) {
			return false;
		}
		if (modDate == null) {
			if (other.modDate != null) {
				return false;
			}
		} else if (!modDate.equals(other.modDate)) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (owner == null) {
			if (other.owner != null) {
				return false;
			}
		} else if (!owner.equals(other.owner)) {
			return false;
		}
		if (userPermission != other.userPermission) {
			return false;
		}
		if (usermeta == null) {
			if (other.usermeta != null) {
				return false;
			}
		} else if (!usermeta.equals(other.usermeta)) {
			return false;
		}
		return true;
	}
}
