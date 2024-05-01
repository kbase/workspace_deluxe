package us.kbase.typedobj.db;

public class OwnerInfo {
	private String ownerUserId;
	private boolean withChangeOwnersPrivilege;
	private String moduleName;
	
	public String getOwnerUserId() {
		return ownerUserId;
	}
	
	public void setOwnerUserId(String ownerUserId) {
		this.ownerUserId = ownerUserId;
	}

	public boolean isWithChangeOwnersPrivilege() {
		return withChangeOwnersPrivilege;
	}
	
	public void setWithChangeOwnersPrivilege(boolean withChangeOwnersPrivilege) {
		this.withChangeOwnersPrivilege = withChangeOwnersPrivilege;
	}
	
	public String getModuleName() {
		return moduleName;
	}
	
	public void setModuleName(String moduleName) {
		this.moduleName = moduleName;
	}
}
