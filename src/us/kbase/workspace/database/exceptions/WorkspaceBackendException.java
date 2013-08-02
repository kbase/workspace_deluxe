package us.kbase.workspace.database.exceptions;

/** 
 * Thrown when a problem with the workspace backend occurs.
 * @author gaprice@lbl.gov
 *
 */
public class WorkspaceBackendException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	public WorkspaceBackendException() { super(); }
	public WorkspaceBackendException(String message) { super(message); }
	public WorkspaceBackendException(String message, Throwable cause) { super(message, cause); }
	public WorkspaceBackendException(Throwable cause) { super(cause); }
}
