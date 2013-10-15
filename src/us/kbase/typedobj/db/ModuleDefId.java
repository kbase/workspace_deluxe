package us.kbase.typedobj.db;

public class ModuleDefId {
	private final static String MOD_VER_SEP = "-";

	private final String moduleName;
	
	private Long version;
	
	public ModuleDefId(String moduleName) {
		this.moduleName = moduleName;
	}

	public ModuleDefId(String moduleName, long version) {
		this(moduleName);
		this.version = version;
	}
	
	public String getModuleName() {
		return moduleName;
	}
	
	public Long getVersion() {
		return version;
	}
	
	@Override
	public String toString() {
		String ret = moduleName + MOD_VER_SEP + (version == null ? "last" : version);
		return ret;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ModuleDefId))
			return false;
		return super.equals(obj);
	}
	
	@Override
	public int hashCode() {
		return toString().hashCode();
	}
}
