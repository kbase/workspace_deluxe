package us.kbase.workspace.workspaces;

public class AbsoluteTypeId extends TypeId{

	public AbsoluteTypeId(WorkspaceType type, int majorVersion, int minorVersion) {
		super(type, majorVersion, minorVersion);
	}
	
	@Override
	public String toString() {
		return "AbsoluteTypeId [type=" + type + ", majorVersion=" +
				majorVersion + ", minorVersion=" + minorVersion + "]";
	}
	
	public String getTypeString() {
		return type.getModule() + "." + type.getName() + "-" + majorVersion +
				"." + minorVersion;
	}
}