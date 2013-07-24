package us.kbase.shock.client;

import java.util.ArrayList;
import java.util.List;

public class ShockACL extends ShockData {

	private ShockUserId owner;
	private List<ShockUserId> read;
	private List<ShockUserId> write;
	private List<ShockUserId> delete;
	
	private ShockACL(){}

	public ShockUserId getOwner() {
		return owner;
	}

	public List<ShockUserId> getRead() {
		if (read == null) {return null;}
		return new ArrayList<ShockUserId>(read);
	}

	public List<ShockUserId> getWrite() {
		if (write == null) {return null;}
		return new ArrayList<ShockUserId>(write);
	}

	public List<ShockUserId> getDelete() {
		if (delete == null) {return null;}
		return new ArrayList<ShockUserId>(delete);
	}
	
	@Override
	public boolean equals(Object obj) {
		//this is repulsive. Rethink this later.
		if (this == obj) {return true;}
		if (!(obj instanceof ShockACL)) {return false;}
		ShockACL acl = (ShockACL)obj;
		if ((this.owner == null ^ acl.owner == null) ||
			(this.read == null ^ acl.read == null) ||
			(this.write == null ^ acl.write == null) ||
			(this.delete == null ^ acl.delete == null)) {
			return false;
		}
		return ((this.owner == null && acl.owner == null) || this.owner.equals(acl.owner)) &&
				((this.read == null && acl.read == null) || this.read.equals(acl.read)) &&
				((this.write == null && acl.write == null) || this.write.equals(acl.write)) &&
				((this.delete == null && acl.delete == null) || this.delete.equals(acl.delete));
	}

	@Override
	public String toString() {
		return "ShockACL [owner=" + owner + ", read=" + read + ", write="
				+ write + ", delete=" + delete + "]";
	}
}
