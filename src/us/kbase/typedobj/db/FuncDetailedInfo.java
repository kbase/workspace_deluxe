package us.kbase.typedobj.db;

import java.util.List;
import java.util.Map;

public class FuncDetailedInfo {
	private String funcDefId;
	private String description;
	private String specDef;
	private List<Long> moduleVersions;
	private List<String> funcVersions;
	private List<String> usedTypeDefIds;
	
	public FuncDetailedInfo(String funcDefId, String description, String specDef, 
			List<Long> moduleVersions, List<String> funcVersions, 
			List<String> usedTypeDefIds) {
		this.funcDefId = funcDefId;
		this.description = description;
		this.specDef = specDef;
		this.moduleVersions = moduleVersions;
		this.funcVersions = funcVersions;
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
	
	public List<Long> getModuleVersions() {
		return moduleVersions;
	}
	
	public List<String> getFuncVersions() {
		return funcVersions;
	}
	
	public List<String> getUsedTypeDefIds() {
		return usedTypeDefIds;
	}
}
