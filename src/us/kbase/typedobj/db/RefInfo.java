package us.kbase.typedobj.db;

public class RefInfo implements Comparable<RefInfo> {
	private String depModule;
	private String depName;
	private String depVersion;
	private String refModule;
	private String refName;
	private String refVersion;
	
	public RefInfo() {
	}
	
	public String getDepModule() {
		return depModule;
	}
	
	public void setDepModule(String depModule) {
		this.depModule = depModule;
	}
	
	public String getDepName() {
		return depName;
	}
	
	public void setDepName(String depName) {
		this.depName = depName;
	}
	
	public String getDepVersion() {
		return depVersion;
	}
	
	public void setDepVersion(String depVersion) {
		this.depVersion = depVersion;
	}
	
	public String getRefModule() {
		return refModule;
	}
	
	public void setRefModule(String refModule) {
		this.refModule = refModule;
	}
	
	public String getRefName() {
		return refName;
	}
	
	public void setRefName(String refName) {
		this.refName = refName;
	}
	
	public String getRefVersion() {
		return refVersion;
	}
	
	public void setRefVersion(String refVersion) {
		this.refVersion = refVersion;
	}
	
	@Override
	public String toString() {
		return depModule + "." + depName + "." + depVersion + "->" + 
	refModule + "." + refName + "." + refVersion;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj != null && obj instanceof RefInfo) {
			return toString().equals(obj.toString());
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return toString().hashCode();
	}
	
	@Override
	public int compareTo(RefInfo o) {
		return toString().compareTo(o.toString());
	}
}
