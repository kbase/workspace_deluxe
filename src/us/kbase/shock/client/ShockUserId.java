package us.kbase.shock.client;

/**
 * Represents a shock user ID.
 * @author gaprice@lbl.gov
 *
 */
public class ShockUserId extends ShockId{

	/**
	 * Construct a user ID.
	 * @param id the ID to create.
	 * @throws IllegalArgumentException if the id is not a valid shock user ID.
	 */
	public ShockUserId(String id) throws IllegalArgumentException {
		super(id);
	}
}
