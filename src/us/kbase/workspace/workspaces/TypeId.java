package us.kbase.workspace.workspaces;

import static us.kbase.workspace.util.Util.checkString;

public class TypeId {
	
	private final String module;
	private final String name;
	private final Integer majorVersion;
	private final Integer minorVersion;

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

}
