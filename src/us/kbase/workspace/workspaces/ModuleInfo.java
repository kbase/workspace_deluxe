package us.kbase.workspace.workspaces;

import java.util.List;
import java.util.Map;

import us.kbase.typedobj.core.AbsoluteTypeDefId;

public class ModuleInfo {
	
	private final String typespec;
	private final List<String> owners;
	private final Long version;
	private final String description;
	private final Map<AbsoluteTypeDefId, String> types;
	
	ModuleInfo(final String typespec, final List<String> owners,
			final Long version, final String description,
			final Map<AbsoluteTypeDefId, String> types) {
		//skip null checking, probably not needed
		this.typespec = typespec;
		this.owners = owners;
		this.version = version;
		this.description = description;
		this.types = types;
	}

	public String getTypespec() {
		return typespec;
	}

	public List<String> getOwners() {
		return owners;
	}

	public Long getVersion() {
		return version;
	}

	public String getDescription() {
		return description;
	}

	public Map<AbsoluteTypeDefId, String> getTypes() {
		return types;
	}

	@Override
	public String toString() {
		return "ModuleInfo [typespec=" + typespec + ", owners=" + owners
				+ ", version=" + version + ", description=" + description
				+ ", types=" + types + "]";
	}

}
