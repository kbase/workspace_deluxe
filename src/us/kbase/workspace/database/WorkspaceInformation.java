package us.kbase.workspace.database;

import static us.kbase.workspace.database.Util.nonNull;
import static us.kbase.common.utils.StringUtils.checkString;

import java.time.Instant;

/** A value class containing various information about a workspace, including the id, owner,
 * and other parameters. Note that this class is immutable and cannot not update its state once
 * created, which means it will become out of date as the workspace storage system is changed.
 * @author gaprice@lbl.gov
 *
 */
public class WorkspaceInformation {
	
	//TODO CODE add is deleted.
	
	//enum for this?
	private static final String UNLOCKED = "unlocked";
	private static final String LOCKED = "locked";
	private static final String PUBLISHED = "published";
	
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

	/** Get the workspace's ID.
	 * @return the ID.
	 */
	public long getId() {
		return id;
	}

	/** Get the workspace's name.
	 * @return the name.
	 */
	public String getName() {
		return name;
	}

	/** Get the workspace owner.
	 * @return the owner.
	 */
	public WorkspaceUser getOwner() {
		return owner;
	}

	/** Get the date the workspace was last modified.
	 * @return the modification date.
	 */
	public Instant getModDate() {
		return modDate;
	}

	/** Get the ID of the last object that was created in the workspace. Note that the object
	 * corresponding to this ID may have been deleted.
	 * @return the largest ID that has ever existed in the workspace.
	 */
	public long getMaximumObjectID() {
		return maxObjectID;
	}

	// this doesn't really make sense to keep in the workspace information...? not sure.
	/** Get the permission the user that requested this workspace information possesses.
	 * The user may or may not be the same as the owner.
	 * @return the user's permission. 
	 */
	public Permission getUserPermission() {
		return userPermission;
	}

	/** Get whether all users can read the workspace.
	 * @return true if the workspace is globally readable.
	 */
	public boolean isGloballyReadable() {
		return globalRead;
	}
	
	/** Get whether the workspace is locked and cannot be altered.
	 * @return true if the workspace is locked.
	 */
	public boolean isLocked() {
		return locked;
	}

	/** Get any metadata associated with the workspace.
	 * @return the workspace metadata.
	 */
	public UncheckedUserMetadata getUserMeta() {
		return usermeta;
	}
	
	/** Returns a string reflecting the lock state of the workspace. One of:
	 * <ul>
	 * <li>unlocked - the workspace is not locked and may be altered.</li>
	 * <li>locked - the workspace is locked and cannot be altered other than to make it globally
	 * readable.</li>
	 * <li>published - the workspace is locked and globally readable and cannot be altered.</li>
	 * </ul>
	 * @return the lock state of the workspace.
	 */
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
		StringBuilder builder2 = new StringBuilder();
		builder2.append("WorkspaceInformation [id=");
		builder2.append(id);
		builder2.append(", name=");
		builder2.append(name);
		builder2.append(", owner=");
		builder2.append(owner);
		builder2.append(", modDate=");
		builder2.append(modDate);
		builder2.append(", maxObjectID=");
		builder2.append(maxObjectID);
		builder2.append(", userPermission=");
		builder2.append(userPermission);
		builder2.append(", globalRead=");
		builder2.append(globalRead);
		builder2.append(", locked=");
		builder2.append(locked);
		builder2.append(", usermeta=");
		builder2.append(usermeta);
		builder2.append("]");
		return builder2.toString();
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
	
	/** Get a builder for a workspace information instance. Note that several builder arguments
	 * are required and the {@link Builder#build()} method will fail if they are not set.
	 * @return a new builder.
	 */
	public static Builder getBuilder() {
		return new Builder();
	}
	
	/** A builder for a workspace information instance. Note that several builder arguments
	 * are required and the {@link Builder#build()} method will fail if they are not set.
	 * @author gaprice@lbl.gov
	 *
	 */
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
		
		/** Build the workspace information instance. This method will fail with a null pointer
		 * exception if any of the required parameters are not set.
		 * @return a new workspace information.
		 */
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
		
		/** Set the workspace's ID (required).
		 * @param id the ID.
		 * @return this builder.
		 */
		public Builder withID(final long id) {
			if (id < 1) {
				throw new IllegalArgumentException("id must be > 0");
			}
			this.id = id;
			return this;
		}
		
		/** Set the workspace's name (required).
		 * Note that other than a null and empty string check, this value is not validated as
		 * per {@link WorkspaceIdentifier#checkWorkspaceName(String, WorkspaceUser)} since this
		 * class is expected to be created based on values from a database rather than user input.
		 * @param name the name.
		 * @return this builder.
		 */
		public Builder withName(final String name) {
			checkString(name, "name");
			this.name = name;
			return this;
		}
		
		/** Set the workspace's owner (required).
		 * @param owner the owner.
		 * @return this builder.
		 */
		public Builder withOwner(final WorkspaceUser owner) {
			nonNull(owner, "owner");
			this.owner = owner;
			return this;
		}
		
		/** Set the last modification date for the workspace (required).
		 * @param modDate the last modification date.
		 * @return this builder.
		 */
		public Builder withModificationDate(final Instant modDate) {
			nonNull(modDate, "modDate");
			this.modDate = modDate;
			return this;
		}
		
		/** Set the maximum object ID that exists or has existed in the workspace (required).
		 * @param maxObjectID the maximum object ID.
		 * @return this builder.
		 */
		public Builder withMaximumObjectID(final long maxObjectID) {
			if (maxObjectID < 0) {
				throw new IllegalArgumentException("max id must be at least 0");
			}
			this.maxObjectID = maxObjectID;
			return this;
		}
		
		/** Set the permission of the user that requested this workspace information (required).
		 * @param perm the user's permission.
		 * @return this builder.
		 */
		public Builder withUserPermission(final Permission perm) {
			nonNull(perm, "perm");
			this.userPermission = perm;
			return this;
		}
		
		/** Set whether this workspace is globally readable (default false).
		 * @param globalRead true to set the workspace as globally readable.
		 * @return this builder.
		 */
		public Builder withGlobalRead(final boolean globalRead) {
			this.globalRead = globalRead;
			return this;
		}
		
		/** Set whether this workspace is locked (default false).
		 * @param locked true to set the workspace as locked.
		 * @return this builder.
		 */
		public Builder withLocked(final boolean locked) {
			this.locked = locked;
			return this;
		}
		
		/** Set the metadata associated with the workspace.
		 * @param meta the metadata.
		 * @return this builder.
		 */
		public Builder withUserMetadata(final UncheckedUserMetadata meta) {
			nonNull(meta, "meta");
			this.usermeta = meta;
			return this;
		}
	}
}
