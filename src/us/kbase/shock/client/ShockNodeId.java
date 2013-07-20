package us.kbase.shock.client;

public class ShockNodeId {

	public final String id;

	public ShockNodeId(String id) {
		//TODO error checking
		this.id = id;
	}
	
	public String toString() {
		return id;
	}
}
