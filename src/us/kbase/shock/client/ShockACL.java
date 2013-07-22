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
		return new ArrayList<ShockUserId>(read);
	}

	public List<ShockUserId> getWrite() {
		return new ArrayList<ShockUserId>(write);
	}

	public List<ShockUserId> getDelete() {
		return new ArrayList<ShockUserId>(delete);
	}

	@Override
	public String toString() {
		return "ShockACL [owner=" + owner + ", read=" + read + ", write="
				+ write + ", delete=" + delete + "]";
	}
}
