package us.kbase.shock.client.exceptions;

/** Parent class for all Shock exceptions.
 * @author gaprice@lbl.gov
 *
 */
public class ExpiredTokenException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public ExpiredTokenException() { super(); }
	public ExpiredTokenException(String message) { super(message); }
	public ExpiredTokenException(String message, Throwable cause) { super(message, cause); }
	public ExpiredTokenException(Throwable cause) { super(cause); }
}
