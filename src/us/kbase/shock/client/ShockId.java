package us.kbase.shock.client;

import java.util.UUID;

public class ShockId {

	public final String id;

	public ShockId(String id) throws IllegalArgumentException {
		UUID.fromString(id); //test valid uuid
		this.id = id;
	}
		
	public String getId() {
		return id;
	}

	@Override
	public String toString() {
		return getClass().getName() + " [id=" + id + "]";
	}
	
	public boolean equals(Object obj) {
		if (this == obj) {return true;}
		if (!(obj instanceof ShockUserId)) {return false;}
		return id.equals(((ShockId)obj).id); 
	}
}
