package us.kbase.workspace.workspaces;

public class AbsoluteTypeId extends TypeId{

	public AbsoluteTypeId(String module, String name, int majorVersion, int minorVersion) {
		super(module, name, majorVersion, minorVersion);
	}
	
	@Override
	public String toString() {
		return "AbsoluteTypeId [module=" + module + ", name=" + name
				+ ", majorVersion=" + majorVersion + ", minorVersion="
				+ minorVersion + "]";
	}

}
