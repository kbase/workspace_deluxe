package us.kbase.typedobj.db;

import java.util.List;

public class TypeDetailedInfo {
	private String typeDefId;
	private String description;
	private String specDef;
	private List<Long> moduleVersions;
	private List<Long> releasedModuleVersions;
	private List<String> typeVersions;
	private List<String> releasedTypeVersions;
	private List<String> usingFuncDefIds;
	private List<String> usingTypeDefIds;
	private List<String> usedTypeDefIds;
	
	public TypeDetailedInfo(String typeDefId, String description, String specDef, 
			List<Long> moduleVersions, List<Long> releasedModuleVersions, 
			List<String> typeVersions, List<String> releasedTypeVersions, 
			List<String> usingFuncDefIds, List<String> usingTypeDefIds, 
			List<String> usedTypeDefIds) {
		this.typeDefId = typeDefId;
		this.description = description;
		this.specDef = specDef;
		this.moduleVersions = moduleVersions;
		this.releasedModuleVersions = releasedModuleVersions;
		this.typeVersions = typeVersions;
		this.releasedTypeVersions = releasedTypeVersions;
		this.usingFuncDefIds = usingFuncDefIds;
		this.usingTypeDefIds = usingTypeDefIds;
		this.usedTypeDefIds = usedTypeDefIds;
	}
	
	public String getTypeDefId() {
		return typeDefId;
	}
	
	public String getDescription() {
		return description;
	}
	
	public String getSpecDef() {
		return specDef;
	}
	
	public List<Long> getModuleVersions() {
		return moduleVersions;
	}
	
	public List<Long> getReleasedModuleVersions() {
		return releasedModuleVersions;
	}
	
	public List<String> getTypeVersions() {
		return typeVersions;
	}
	
	public List<String> getReleasedTypeVersions() {
		return releasedTypeVersions;
	}
	
	public List<String> getUsingFuncDefIds() {
		return usingFuncDefIds;
	}
	
	public List<String> getUsingTypeDefIds() {
		return usingTypeDefIds;
	}
	
	public List<String> getUsedTypeDefIds() {
		return usedTypeDefIds;
	}
}
