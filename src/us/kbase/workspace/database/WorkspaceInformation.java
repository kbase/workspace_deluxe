package us.kbase.workspace.database;

import static us.kbase.workspace.database.Util.nonNull;
import static us.kbase.common.utils.StringUtils.checkString;

import java.time.Instant;

public class WorkspaceInformation {
	
	//TODO CODE add is deleted.
	//TODO NOW JAVADOC
	//TODO NOW TEST
	
	public static final String UNLOCKED = "unlocked";
	public static final String LOCKED = "locked";
	public static final String PUBLISHED = "published";
	
	final private long id;
	final private String name;
	final private WorkspaceUser owner;
	final private Instant modDate;
	final private long maxObjectID;
	final private Permission userPermission;
	final private boolean globalRead;
	final private boolean locked;
	final private UncheckedUserMetadata usermeta;
	
	private WorkspaceInformation(
			final long id,
			final String name,
			final WorkspaceUser owner,
			final Instant modDate,
			final long maxObjectID,
			final Permission userPermission,
			final boolean globalRead,
			final boolean locked,
			final UncheckedUserMetadata meta) {
		this.id = id;
		this.name = name;
		this.owner = owner;
		this.modDate = modDate;
		this.maxObjectID = maxObjectID;
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

	public Instant getModDate() {
		return modDate;
	}

	public long getMaximumObjectID() {
		return maxObjectID;
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
		builder.append(maxObjectID);
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
		result = prime * result + (globalRead ? 1231 : 1237);
		result = prime * result + (int) (id ^ (id >>> 32));
		result = prime * result + (locked ? 1231 : 1237);
		result = prime * result + (int) (maxObjectID ^ (maxObjectID >>> 32));
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
		if (globalRead != other.globalRead) {
			return false;
		}
		if (id != other.id) {
			return false;
		}
		if (locked != other.locked) {
			return false;
		}
		if (maxObjectID != other.maxObjectID) {
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
	
	// note required info
	public static Builder getBuilder() {
		return new Builder();
	}
	
	// note required info
	public static class Builder {
		
		// required
		private Long id = null;
		private String name = null;
		private WorkspaceUser owner = null;
		private Instant modDate = null;
		private Long maxObjectID = null;
		private Permission userPermission = null;
		// optional 
		private boolean globalRead = false;
		private boolean locked = false;
		private UncheckedUserMetadata usermeta =
				new UncheckedUserMetadata(new WorkspaceUserMetadata());
		
		private Builder() {}
		
		public WorkspaceInformation build() {
			nonNull(id, "id");
			// seems like overkill to run WorkspaceIdentifier.checkWorkspaceName() seeing as this
			// class is created from DB info, not user supplied info
			nonNull(name, "name");
			nonNull(owner, "owner");
			nonNull(modDate, "modDate");
			nonNull(maxObjectID, "maxObjectID");
			nonNull(userPermission, "userPermission");
			return new WorkspaceInformation(id, name, owner, modDate, maxObjectID, userPermission,
					globalRead, locked, usermeta);
		}
		
		// required
		public Builder withID(final long id) {
			if (id < 1) {
				throw new IllegalArgumentException("id must be > 0");
			}
			this.id = id;
			return this;
		}
		
		//required
		public Builder withName(final String name) {
			checkString(name, "name");
			this.name = name;
			return this;
		}
		
		//required
		public Builder withOwner(final WorkspaceUser owner) {
			nonNull(owner, "owner");
			this.owner = owner;
			return this;
		}
		
		//required
		public Builder withModificationDate(final Instant modDate) {
			nonNull(modDate, "modDate");
			this.modDate = modDate;
			return this;
		}
		
		//required
		public Builder withMaximumObjectID(final long maxObjectID) {
			if (maxObjectID < 0) {
				throw new IllegalArgumentException("max id must be at least 0");
			}
			this.maxObjectID = maxObjectID;
			return this;
		}
		
		// required
		public Builder withUserPermission(final Permission perm) {
			nonNull(perm, "perm");
			this.userPermission = perm;
			return this;
		}
		
		public Builder withGlobalRead(final boolean globalRead) {
			this.globalRead = globalRead;
			return this;
		}
		
		public Builder withLocked(final boolean locked) {
			this.locked = locked;
			return this;
		}
		
		public Builder withUserMetadata(final UncheckedUserMetadata meta) {
			nonNull(meta, "meta");
			this.usermeta = meta;
			return this;
		}
	}
}
