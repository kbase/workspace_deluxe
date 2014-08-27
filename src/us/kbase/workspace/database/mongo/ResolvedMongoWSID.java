package us.kbase.workspace.database.mongo;

import static us.kbase.common.utils.StringUtils.checkString;

import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceIdentifier;

public class ResolvedMongoWSID implements ResolvedWorkspaceID {
	
	private final long id;
	private final String wsname;
	private final boolean locked;
	private final boolean deleted;
	
	ResolvedMongoWSID(final String name, final long id,
			final boolean locked, final boolean deleted) {
		if (id < 1) {
			throw new IllegalArgumentException("ID must be >0");
		}
		checkString(name, "name", WorkspaceIdentifier.MAX_NAME_LENGTH);
		this.id = id;
		this.wsname = name;
		this.locked = locked;
		this.deleted = deleted;
	}

	@Override
	public long getID() {
		return id;
	}
	
	@Override
	public String getName() {
		return wsname;
	}
	
	@Override
	public boolean isLocked() {
		return locked;
	}
	
	@Override
	public boolean isDeleted() {
		return deleted;
	}

	@Override
	public String toString() {
		return "ResolvedMongoWSID [id=" + id + ", wsname=" + wsname
				+ ", locked=" + locked + ", deleted=" + deleted + "]";
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
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ResolvedMongoWSID other = (ResolvedMongoWSID) obj;
		if (deleted != other.deleted)
			return false;
		if (id != other.id)
			return false;
		if (locked != other.locked)
			return false;
		if (wsname == null) {
			if (other.wsname != null)
				return false;
		} else if (!wsname.equals(other.wsname))
			return false;
		return true;
	}

}
