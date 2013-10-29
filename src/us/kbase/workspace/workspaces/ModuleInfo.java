package us.kbase.workspace.workspaces;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import us.kbase.typedobj.core.AbsoluteTypeDefId;

public class ModuleInfo {
	
	private final String typespec;
	private final List<String> owners;
	private final Long version;
	private final String description;
	private final Map<AbsoluteTypeDefId, String> types;
	private Map<String, Long> includedSpecVersions = new LinkedHashMap<String, Long>();
	private String md5hash;
	
	ModuleInfo(final String typespec, final List<String> owners,
			final Long version, final String description,
			final Map<AbsoluteTypeDefId, String> types, 
			Map<String, Long> includedSpecVersions, String md5hash) {
		//skip null checking, probably not needed
		this.typespec = typespec;
		this.owners = owners;
		this.version = version;
		this.description = description;
		this.types = types;
		this.includedSpecVersions = includedSpecVersions;
		this.md5hash = md5hash;
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
	
	@Override
	public String toString() {
		return "ModuleInfo [typespec=" + typespec + ", owners=" + owners
				+ ", version=" + version + ", description=" + description
				+ ", types=" + types + "]";
	}

}
