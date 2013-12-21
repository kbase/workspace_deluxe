package us.kbase.workspace.test.workspace;

import us.kbase.workspace.database.ResolvedWorkspaceID;

public class FakeResolvedWSID implements ResolvedWorkspaceID {
	
	private final long id;
	private final String name;
	
	public FakeResolvedWSID(long id) {
		if (id < 1) {
			throw new IllegalArgumentException("ID must be >0");
		}
		this.id = id;
		this.name = null;
	}
	
	public FakeResolvedWSID(String name, long id) {
		if (id < 1) {
			throw new IllegalArgumentException("ID must be >0");
		}
		this.id = id;
		this.name = name;
	}

	@Override
	public long getID() {
		return id;
	}
	
	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean isLocked() {
		return false;
	}
	
	@Override
	public String toString() {
		return "FakeResolvedWSID [id=" + id + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (id ^ (id >>> 32));
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
