package us.kbase.workspace.database.exceptions;

/** 
 * Thrown when the requested workspace doesn't exist.
 * @author gaprice@lbl.gov
 *
 */
public class NoSuchObjectException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	public NoSuchObjectException() { super(); }
	public NoSuchObjectException(String message) { super(message); }
	public NoSuchObjectException(String message, Throwable cause) { super(message, cause); }
	public NoSuchObjectException(Throwable cause) { super(cause); }
}
