package us.kbase.workspace.workspaces;

import java.util.List;
import java.util.Map;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.workspace.database.WorkspaceUser;

public class ModuleInfo {
	
	private final String typespec;
	private final WorkspaceUser owner;
	private final Long version;
	private final String description;
	private final Map<AbsoluteTypeDefId, String> types;
	
	ModuleInfo(final String typespec, final WorkspaceUser owner,
			final Long version, final String description,
			final Map<AbsoluteTypeDefId, String> types) {
		//skip null checking, probably not needed
		this.typespec = typespec;
		this.owner = owner;
		this.version = version;
		this.description = description;
		this.types = types;
	}

	public String getTypespec() {
		return typespec;
	}

	public WorkspaceUser getOwner() {
		return owner;
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
		return "ModuleInfo [typespec=" + typespec + ", owner=" + owner
				+ ", version=" + version + ", description=" + description
				+ ", types=" + types + "]";
	}

}
