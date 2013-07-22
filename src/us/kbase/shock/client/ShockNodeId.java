package us.kbase.shock.client;

import java.util.UUID;

public class ShockNodeId {

	public final String id;

	public ShockNodeId(String id) {
		UUID.fromString(id); //test valid uuid
		this.id = id;
	}
	
	public String toString() {
		return id;
	}
}
