package us.kbase.workspace.database.mongo;

import static us.kbase.common.utils.StringUtils.checkString;

import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceIdentifier;

class ResolvedMongoWSID implements ResolvedWorkspaceID {
	
	private final long id;
	private final String wsname;
	
	public ResolvedMongoWSID(final String name, final long id) {
		if (id < 1) {
			throw new IllegalArgumentException("ID must be >0");
		}
		checkString(name, "name", WorkspaceIdentifier.MAX_NAME_LENGTH);
		this.id = id;
		this.wsname = name;
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
	public String toString() {
		return "ResolvedMongoWSID [id=" + id + ", wsname=" + wsname + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (id ^ (id >>> 32));
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
		if (!(obj instanceof ResolvedMongoWSID)) {
			return false;
		}
		ResolvedMongoWSID other = (ResolvedMongoWSID) obj;
		if (id != other.id) {
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
