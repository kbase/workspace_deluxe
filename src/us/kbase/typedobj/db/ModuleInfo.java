package us.kbase.typedobj.db;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModuleInfo {
	private String moduleName;
	private String owner;
	private String email;
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
	
	public String getOwner() {
		return owner;
	}
	
	public void setOwner(String owner) {
		this.owner = owner;
	}
	
	public String getEmail() {
		return email;
	}
	
	public void setEmail(String email) {
		this.email = email;
	}
}
