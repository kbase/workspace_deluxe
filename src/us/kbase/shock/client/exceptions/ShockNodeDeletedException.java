package us.kbase.shock.client.exceptions;

/**
 * Thrown when a method is called on a 
 * {@link us.kbase.shock.client.ShockNode ShockNode} on which the 
 * {@link us.kbase.shock.client.ShockNode#delete() delete()} method was 
 * previously called.
 * @author gaprice@lbl.gov
 *
 */
public class ShockNodeDeletedException extends RuntimeException {
	
	private static final long serialVersionUID = 1L;
	
	public ShockNodeDeletedException() { super(); }
	public ShockNodeDeletedException(String message) { super(message); }
	public ShockNodeDeletedException(String message, Throwable cause) { super(message, cause); }
	public ShockNodeDeletedException(Throwable cause) { super(cause); }
}
