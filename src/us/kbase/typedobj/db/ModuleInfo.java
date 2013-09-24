package us.kbase.typedobj.db;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModuleInfo {
	private String moduleName;
	private List<String> includedModuleNames = new ArrayList<String>();
	private Map<String, TypeInfo> types = new LinkedHashMap<String, TypeInfo>();
	private Map<String, FuncInfo> funcs = new LinkedHashMap<String, FuncInfo>();
	
	public String getModuleName() {
		return moduleName;
	}
	
	public void setModuleName(String moduleName) {
		this.moduleName = moduleName;
	}
	
	public List<String> getIncludedModuleNames() {
		return includedModuleNames;
	}
	
	public void setIncludedModuleNames(List<String> includedModuleNames) {
		this.includedModuleNames = includedModuleNames;
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
}
