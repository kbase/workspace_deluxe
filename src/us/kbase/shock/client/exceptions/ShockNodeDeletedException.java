package us.kbase.shock.client.exceptions;

/** Parent class for all Shock exceptions.
 * @author gaprice@lbl.gov
 *
 */
public class ShockNodeDeletedException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public ShockNodeDeletedException() { super(); }
	public ShockNodeDeletedException(String message) { super(message); }
	public ShockNodeDeletedException(String message, Throwable cause) { super(message, cause); }
	public ShockNodeDeletedException(Throwable cause) { super(cause); }
}
