package us.kbase.workspace.workspaces;

import static us.kbase.workspace.util.Util.checkString;

public class TypeId {
	
	final String module;
	final String name;
	final Integer majorVersion;
	final Integer minorVersion;

	public TypeId(String module, String name, int majorVersion, int minorVersion) {
		checkString(module, "module");
		checkString(name, "name");
		if (majorVersion < 0 || minorVersion < 0) {
			throw new IllegalArgumentException("version numbers must be >= 0");
		}
		this.module = module;
		this.name = name;
		this.majorVersion = majorVersion;
		this.minorVersion = minorVersion;
	}
	
	public TypeId(String module, String name, int majorVersion) {
		checkString(module, "module");
		checkString(name, "name");
		if (majorVersion < 0) {
			throw new IllegalArgumentException("version numbers must be >= 0");
		}
		this.module = module;
		this.name = name;
		this.majorVersion = majorVersion;
		this.minorVersion = 0;
	}
	
	public TypeId(String module, String name) {
		checkString(module, "module");
		checkString(name, "name");
		this.module = module;
		this.name = name;
		this.majorVersion = null;
		this.minorVersion = null;
	}
	
	public String getModule() {
		return module;
	}
	
	public String getName() {
		return name;
	}
	
	public int getMajorVersion() {
		return majorVersion;
	}
	
	public int getMinorVersion() {
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
		result = prime * result + ((module == null) ? 0 : module.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		TypeId other = (TypeId) obj;
		if (majorVersion == null) {
			if (other.majorVersion != null)
				return false;
		} else if (!majorVersion.equals(other.majorVersion))
			return false;
		if (minorVersion == null) {
			if (other.minorVersion != null)
				return false;
		} else if (!minorVersion.equals(other.minorVersion))
			return false;
		if (module == null) {
			if (other.module != null)
				return false;
		} else if (!module.equals(other.module))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "TypeId [module=" + module + ", name=" + name
				+ ", majorVersion=" + majorVersion + ", minorVersion="
				+ minorVersion + "]";
	}

}
