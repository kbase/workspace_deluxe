package us.kbase.workspace.database;

import java.util.List;
import java.util.Map;

import us.kbase.typedobj.core.AbsoluteTypeDefId;

public class ModuleInfo {
	
	private final String typespec;
	private final List<String> owners;
	private final Long version;
	private final String description;
	private final Map<AbsoluteTypeDefId, String> types;
	private Map<String, Long> includedSpecVersions;
	private String md5hash;
	private List<String> functions;
	private final boolean isReleased; 
	
	ModuleInfo(final String typespec, final List<String> owners,
			final Long version, final String description,
			final Map<AbsoluteTypeDefId, String> types, 
			Map<String, Long> includedSpecVersions, String md5hash,
			List<String> functions, boolean isReleased) {
		//skip null checking, probably not needed
		this.typespec = typespec;
		this.owners = owners;
		this.version = version;
		this.description = description;
		this.types = types;
		this.includedSpecVersions = includedSpecVersions;
		this.md5hash = md5hash;
		this.functions = functions;
		this.isReleased = isReleased;
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

	public Map<String, Long> getIncludedSpecVersions() {
		return includedSpecVersions;
	}
	
	public String getMd5hash() {
		return md5hash;
	}
	
	public List<String> getFunctions() {
		return functions;
	}

	public boolean isReleased() {
		return isReleased;
	}

	@Override
	public String toString() {
		return "ModuleInfo [typespec=" + typespec + ", owners=" + owners
				+ ", version=" + version + ", description=" + description
				+ ", types=" + types + ", includedSpecVersions="
				+ includedSpecVersions + ", md5hash=" + md5hash
				+ ", functions=" + functions + ", isReleased=" + isReleased
				+ "]";
	}
}
