package us.kbase.workspace.database.mongo;

import static us.kbase.workspace.util.Util.checkString;

import us.kbase.workspace.database.ResolvedWorkspaceID;

public class ResolvedMongoWSID implements ResolvedWorkspaceID {
	
	private final int id;
	private final String name;
	
	public ResolvedMongoWSID(int id, String name) {
		if (id < 1) {
			throw new IllegalArgumentException("ID must be >0");
		}
		checkString(name, "name");
		this.id = id;
		this.name = name;
	}

	@Override
	public String getNameAtResolutionTime() {
		return name;
	}

	@Override
	public int getID() {
		return id;
	}

	@Override
	public String toString() {
		return "ResolvedMongoWSID [id=" + id + ", name=" + name + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

}
