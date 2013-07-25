package us.kbase.shock.client.exceptions;

/** Thrown when a {@link us.kbase.shock.client.ShockNode ShockNode} has had the
 * {@link us.kbase.shock.client.ShockNode#delete() delete()} method called 
 * and a further method is called
 * @author gaprice@lbl.gov
 *
 */
public class ShockNodeDeletedException extends ShockException {

	private static final long serialVersionUID = 1L;
	
	public ShockNodeDeletedException() { super(); }
	public ShockNodeDeletedException(String message) { super(message); }
	public ShockNodeDeletedException(String message, Throwable cause) { super(message, cause); }
	public ShockNodeDeletedException(Throwable cause) { super(cause); }
}
