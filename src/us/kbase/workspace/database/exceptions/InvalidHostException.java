package us.kbase.workspace.database.exceptions;

/** 
 * Thrown when the host provided for a database is not valid.
 * @author gaprice@lbl.gov
 *
 */
public class InvalidHostException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	public InvalidHostException() { super(); }
	public InvalidHostException(String message) { super(message); }
	public InvalidHostException(String message, Throwable cause) { super(message, cause); }
	public InvalidHostException(Throwable cause) { super(cause); }
}
