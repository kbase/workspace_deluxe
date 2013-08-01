package us.kbase.workspace.database.exceptions;

/** 
 * Thrown when authorization to a database is denied.
 * @author gaprice@lbl.gov
 *
 */
public class AuthorizationException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public AuthorizationException() { super(); }
	public AuthorizationException(String message) { super(message); }
	public AuthorizationException(String message, Throwable cause) { super(message, cause); }
	public AuthorizationException(Throwable cause) { super(cause); }
}
