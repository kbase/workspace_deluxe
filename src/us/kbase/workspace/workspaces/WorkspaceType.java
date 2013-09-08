package us.kbase.workspace.workspaces;

import static us.kbase.workspace.util.Util.checkString;

public class WorkspaceType {
	
	private final String module;
	private final String name;
	
	public WorkspaceType(String module, String name) {
		checkString(module, "module");
		checkString(name, "name");
		this.module = module;
		this.name = name;
	}
	
	public String getModule() {
		return module;
	}
	
	public String getName() {
		return name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((module == null) ? 0 : module.hashCode());
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
		if (!(obj instanceof WorkspaceType)) {
			return false;
		}
		WorkspaceType other = (WorkspaceType) obj;
		if (module == null) {
			if (other.module != null) {
				return false;
			}
		} else if (!module.equals(other.module)) {
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
	
	@Override
	public String toString() {
		return "WorkspaceType [module=" + module + ", name=" + name + "]";
	}

}
