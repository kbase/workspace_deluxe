package us.kbase.workspace.database.exceptions;

/** 
 * Thrown when the requested workspace doesn't exist.
 * @author gaprice@lbl.gov
 *
 */
public class NoSuchWorkspaceException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	public NoSuchWorkspaceException() { super(); }
	public NoSuchWorkspaceException(String message) { super(message); }
	public NoSuchWorkspaceException(String message, Throwable cause) { super(message, cause); }
	public NoSuchWorkspaceException(Throwable cause) { super(cause); }
}
