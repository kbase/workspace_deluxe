package us.kbase.shock.client.exceptions;

/** 
 * Parent class for all Shock exceptions.
 * @author gaprice@lbl.gov
 *
 */
public class ShockException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public ShockException() { super(); }
	public ShockException(String message) { super(message); }
	public ShockException(String message, Throwable cause) { super(message, cause); }
	public ShockException(Throwable cause) { super(cause); }
}
