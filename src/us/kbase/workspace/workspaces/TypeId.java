package us.kbase.workspace.workspaces;

public class TypeId {
	
	final WorkspaceType type;
	final Integer majorVersion;
	final Integer minorVersion;

	public TypeId(WorkspaceType type, int majorVersion, int minorVersion) {
		if (type == null) {
			throw new NullPointerException("type cannot be null");
		}
		if (majorVersion < 0 || minorVersion < 0) {
			throw new IllegalArgumentException("version numbers must be >= 0");
		}
		this.type = type;
		this.majorVersion = majorVersion;
		this.minorVersion = minorVersion;
	}
	
	public TypeId(WorkspaceType type, int majorVersion) {
		if (type == null) {
			throw new NullPointerException("type cannot be null");
		}
		if (majorVersion < 0) {
			throw new IllegalArgumentException("version numbers must be >= 0");
		}
		this.type = type;
		this.majorVersion = majorVersion;
		this.minorVersion = null;
	}
	
	public TypeId(WorkspaceType type) {
		if (type == null) {
			throw new NullPointerException("type cannot be null");
		}
		this.type = type;
		this.majorVersion = null;
		this.minorVersion = null;
	}
	
	public WorkspaceType getType() {
		return type;
	}
	
	public Integer getMajorVersion() {
		return majorVersion;
	}
	
	public Integer getMinorVersion() {
		return minorVersion;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((majorVersion == null) ? 0 : majorVersion.hashCode());
		result = prime * result
				+ ((minorVersion == null) ? 0 : minorVersion.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
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
		if (!(obj instanceof TypeId)) {
			return false;
		}
		TypeId other = (TypeId) obj;
		if (majorVersion == null) {
			if (other.majorVersion != null) {
				return false;
			}
		} else if (!majorVersion.equals(other.majorVersion)) {
			return false;
		}
		if (minorVersion == null) {
			if (other.minorVersion != null) {
				return false;
			}
		} else if (!minorVersion.equals(other.minorVersion)) {
			return false;
		}
		if (type == null) {
			if (other.type != null) {
				return false;
			}
		} else if (!type.equals(other.type)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "TypeId [type=" + type + ", majorVersion=" + majorVersion
				+ ", minorVersion=" + minorVersion + "]";
	}

}
