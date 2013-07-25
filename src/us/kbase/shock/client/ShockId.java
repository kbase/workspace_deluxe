package us.kbase.shock.client;

import java.util.regex.Pattern;

/**
 * Represents a shock ID.
 * @author gaprice@lbl.gov
 *
 */
public class ShockId {
	
	//8-4-4-4-12
	private static final Pattern UUID =
			Pattern.compile("[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}");

	private final String id;

	/**
	 * Constructs a shock ID.
	 * @param id the shock ID.
	 * @throws IllegalArgumentException if the ID is not a valid shock ID.
	 */
	public ShockId(String id) throws IllegalArgumentException {
		if (!UUID.matcher(id).matches()) {
			throw new IllegalArgumentException("id must be a UUID hex string");
		}
		this.id = id;
	}
		
	/**
	 * Returns the ID string.
	 * @return the ID string.
	 */
	public String getId() {
		return id;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return getClass().getName() + " [id=" + id + "]";
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if (this == obj) {return true;}
		if (!(obj instanceof ShockId)) {return false;}
		return id.equals(((ShockId)obj).id); 
	}
}
