package us.kbase.shock.client;

/**
 * Represents a shock node ID.
 * @author gaprice@lbl.gov
 *
 */
public class ShockNodeId extends ShockId{

	/**
	 * Construct a node ID.
	 * @param id the ID to create.
	 * @throws IllegalArgumentException if the id is not a valid shock node ID.
	 */
	public ShockNodeId(String id) throws IllegalArgumentException {
		super(id);
	}
}
