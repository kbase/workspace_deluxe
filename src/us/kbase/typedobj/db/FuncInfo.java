package us.kbase.typedobj.db;

public class FuncInfo {
	private String funcName;
	private String funcVersion;
	private boolean supported = true;
	
	public String getFuncName() {
		return funcName;
	}
	
	public void setFuncName(String funcName) {
		this.funcName = funcName;
	}
	
	public String getFuncVersion() {
		return funcVersion;
	}
	
	public void setFuncVersion(String funcVersion) {
		this.funcVersion = funcVersion;
	}
			
	public boolean isSupported() {
		return supported;
	}
	
	public void setSupported(boolean isSupported) {
		this.supported = isSupported;
	}
}
