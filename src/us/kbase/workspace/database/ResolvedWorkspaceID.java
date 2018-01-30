package us.kbase.workspace.database;

import static us.kbase.common.utils.StringUtils.checkString;

/** A workspace ID that has been confirmed against the database.
 * 
 * Note that a set of resolved IDs will be consistent if they're pulled from
 * the database at the same time, but may not be consistent if not pulled in
 * the same batch.
 * 
 * The name and locked and deletion states of the workspace are correct at the moment of retrieval
 * from the database, but are not updated further.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class ResolvedWorkspaceID {
	
	//TODO CODE merge with workspace info, no need for two classes for essentially the same thing - although maybe memory concerns are relevant
	/* maybe the way to do it is to allow requesting certain fields? feels like premature
	 * optimization though, and the optional fields would need to be in a map or something so
	 * they don't take up space in the object memory, although a map would be almost as bad.
	 * there are cases where many RWSIDs are loaded into memory at once
	 */
	private final long id;
	private final String wsname;
	private final boolean locked;
	private final boolean deleted;
	
	/** Create a resolved workspace ID.
	 * @param id the workspace ID.
	 * @param name the name of the workspace.
	 * @param locked true if the workspace is locked.
	 * @param deleted true if the workspace is deleted.
	 */
	public ResolvedWorkspaceID(
			final long id,
			final String name,
			final boolean locked,
			final boolean deleted) {
		if (id < 1) {
			throw new IllegalArgumentException("ID must be > 0");
		}
		checkString(name, "name", WorkspaceIdentifier.MAX_NAME_LENGTH);
		this.id = id;
		this.wsname = name;
		this.locked = locked;
		this.deleted = deleted;
	}

	/** Get the ID of the workspace.
	 * @return the workspace ID.
	 */
	public long getID() {
		return id;
	}
	
	/** Get the name of the workspace at the time it was retrieved from the database.
	 * @return the workspace name.
	 */
	public String getName() {
		return wsname;
	}
	
	/** Determine whether the workspace is locked.
	 * @return true if the workspace is locked.
	 */
	public boolean isLocked() {
		return locked;
	}
	
	/** Determine whether the workspace is deleted.
	 * @return true if the workspace is deleted.
	 */
	public boolean isDeleted() {
		return deleted;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ResolvedWorkspaceID [id=");
		builder.append(id);
		builder.append(", wsname=");
		builder.append(wsname);
		builder.append(", locked=");
		builder.append(locked);
		builder.append(", deleted=");
		builder.append(deleted);
		builder.append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (deleted ? 1231 : 1237);
		result = prime * result + (int) (id ^ (id >>> 32));
		result = prime * result + (locked ? 1231 : 1237);
		result = prime * result + ((wsname == null) ? 0 : wsname.hashCode());
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
		ResolvedWorkspaceID other = (ResolvedWorkspaceID) obj;
		if (deleted != other.deleted) {
			return false;
		}
		if (id != other.id) {
			return false;
		}
		if (locked != other.locked) {
			return false;
		}
		if (wsname == null) {
			if (other.wsname != null) {
				return false;
			}
		} else if (!wsname.equals(other.wsname)) {
			return false;
		}
		return true;
	}

}
