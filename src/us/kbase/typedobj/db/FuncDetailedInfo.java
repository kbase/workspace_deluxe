package us.kbase.typedobj.db;

import java.util.List;

public class FuncDetailedInfo {
	private String funcDefId;
	private String description;
	private String specDef;
	private String parsingStructure;
	private List<Long> moduleVersions;
	private List<Long> releasedModuleVersions;
	private List<String> funcVersions;
	private List<String> releasedFuncVersions;
	private List<String> usedTypeDefIds;
	
	public FuncDetailedInfo(String funcDefId, String description, 
			String specDef, String parsingStructure,
			List<Long> moduleVersions, List<Long> releasedModuleVersions, 
			List<String> funcVersions, List<String> releasedFuncVersions,
			List<String> usedTypeDefIds) {
		this.funcDefId = funcDefId;
		this.description = description;
		this.specDef = specDef;
		this.parsingStructure = parsingStructure;
		this.moduleVersions = moduleVersions;
		this.releasedModuleVersions = releasedModuleVersions;
		this.funcVersions = funcVersions;
		this.releasedFuncVersions = releasedFuncVersions;
		this.usedTypeDefIds = usedTypeDefIds;
	}
	
	public String getFuncDefId() {
		return funcDefId;
	}
	
	public String getDescription() {
		return description;
	}
	
	public String getSpecDef() {
		return specDef;
	}
	
	public String getParsingStructure() {
		return parsingStructure;
	}
	
	public List<Long> getModuleVersions() {
		return moduleVersions;
	}
	
	public List<Long> getReleasedModuleVersions() {
		return releasedModuleVersions;
	}
	
	public List<String> getFuncVersions() {
		return funcVersions;
	}
	
	public List<String> getReleasedFuncVersions() {
		return releasedFuncVersions;
	}
	
	public List<String> getUsedTypeDefIds() {
		return usedTypeDefIds;
	}
}
