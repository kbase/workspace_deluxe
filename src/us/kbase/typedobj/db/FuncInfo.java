package us.kbase.typedobj.db;

public class FuncInfo {
	private String funcName;
	private String funcVersion;
	private boolean isSupported = true;
	private String releaseVersion;
	
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
		return isSupported;
	}
	
	public void setSupported(boolean isSupported) {
		this.isSupported = isSupported;
	}
	
	public String getReleaseVersion() {
		return releaseVersion;
	}
	
	public void setReleaseVersion(String releaseVersion) {
		this.releaseVersion = releaseVersion;
	}
}
