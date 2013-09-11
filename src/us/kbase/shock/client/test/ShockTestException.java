package us.kbase.shock.client.test;

/** 
 * Thrown when an exception occurs while setting up shock tests.
 * @author gaprice@lbl.gov
 *
 */
public class ShockTestException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public ShockTestException() { super(); }
	public ShockTestException(String message) { super(message); }
	public ShockTestException(String message, Throwable cause) { super(message, cause); }
	public ShockTestException(Throwable cause) { super(cause); }
}
