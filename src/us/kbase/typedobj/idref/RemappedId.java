package us.kbase.typedobj.idref;

/** An ID that has been remapped to another form - often an absolutized version of a mutable ID.
 * @author gaprice@lbl.gov
 *
 */
public interface RemappedId {
	
	/** Returns the remapped ID.
	 * @return the remapped ID.
	 */
	public String getId();
}
