package us.kbase.shock.client;

import java.util.regex.Pattern;

public class ShockId {
	
	//8-4-4-4-12
	private static final Pattern UUID =
			Pattern.compile("[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}");

	public final String id;

	public ShockId(String id) throws IllegalArgumentException {
		if (!UUID.matcher(id).matches()) {
			throw new IllegalArgumentException("id must be a UUID hex string");
		}
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
