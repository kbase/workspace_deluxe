package us.kbase.workspace.database.exceptions;

/** 
 * Thrown when authorization to a database is denied.
 * @author gaprice@lbl.gov
 *
 */
public class DBAuthorizationException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	public DBAuthorizationException() { super(); }
	public DBAuthorizationException(String message) { super(message); }
	public DBAuthorizationException(String message, Throwable cause) { super(message, cause); }
	public DBAuthorizationException(Throwable cause) { super(cause); }
}
