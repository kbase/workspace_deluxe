package us.kbase.typedobj.db;

public class TypeInfo {
	private String typeName;
	private String typeVersion;
	private boolean isSupported = true;
	private String releaseVersion;
	
	public String getTypeName() {
		return typeName;
	}
	
	public void setTypeName(String typeName) {
		this.typeName = typeName;
	}
	
	public String getTypeVersion() {
		return typeVersion;
	}
	
	public void setTypeVersion(String typeVersion) {
		this.typeVersion = typeVersion;
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
