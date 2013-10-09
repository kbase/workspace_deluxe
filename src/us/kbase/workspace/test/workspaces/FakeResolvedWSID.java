package us.kbase.workspace.test.workspaces;

import us.kbase.workspace.database.ResolvedWorkspaceID;

public class FakeResolvedWSID implements ResolvedWorkspaceID {
	
	private final int id;
	
	public FakeResolvedWSID(int id) {
		if (id < 1) {
			throw new IllegalArgumentException("ID must be >0");
		}
		this.id = id;
	}

	@Override
	public int getID() {
		return id;
	}

	@Override
	public String toString() {
		return "FakeResolvedWSID [id=" + id + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
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
		if (!(obj instanceof FakeResolvedWSID)) {
			return false;
		}
		FakeResolvedWSID other = (FakeResolvedWSID) obj;
		if (id != other.id) {
			return false;
		}
		return true;
	}

}
