package us.kbase.typedobj.db;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModuleInfo {
	private String moduleName;
	private Map<String, Long> includedModuleNameToVersion = new LinkedHashMap<String, Long>();
	private Map<String, TypeInfo> types = new LinkedHashMap<String, TypeInfo>();
	private Map<String, FuncInfo> funcs = new LinkedHashMap<String, FuncInfo>();
	private String description;
	private String md5hash;
	private long versionTime;
	
	public ModuleInfo() {
	}
	
	public String getModuleName() {
		return moduleName;
	}
	
	public void setModuleName(String moduleName) {
		this.moduleName = moduleName;
	}
	
	public Map<String, Long> getIncludedModuleNameToVersion() {
		return includedModuleNameToVersion;
	}
	
	public void setIncludedModuleNameToVersion(
			Map<String, Long> includedModuleNameToVersion) {
		this.includedModuleNameToVersion = includedModuleNameToVersion;
	}
	
	public Map<String, TypeInfo> getTypes() {
		return types;
	}
	
	public void setTypes(Map<String, TypeInfo> types) {
		this.types = types;
	}
	
	public Map<String, FuncInfo> getFuncs() {
		return funcs;
	}
	
	public void setFuncs(Map<String, FuncInfo> funcs) {
		this.funcs = funcs;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public String getMd5hash() {
		return md5hash;
	}
	
	public void setMd5hash(String md5hash) {
		this.md5hash = md5hash;
	}
	
	public long getVersionTime() {
		return versionTime;
	}
	
	public void setVersionTime(long versionTime) {
		this.versionTime = versionTime;
	}
}
