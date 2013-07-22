package us.kbase.shock.client;

import java.util.UUID;

public class ShockNodeId {

	public final String id;

	public ShockNodeId(String id) throws IllegalArgumentException {
		UUID.fromString(id); //test valid uuid
		this.id = id;
	}
		
	public String getId() {
		return id;
	}

	@Override
	public String toString() {
		return "ShockNodeId [id=" + id + "]";
	}
}
