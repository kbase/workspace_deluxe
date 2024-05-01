package us.kbase.workspace.database.exceptions;

/** 
 * Thrown when a workspace already exists.
 * @author gaprice@lbl.gov
 *
 */
public class PreExistingWorkspaceException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	public PreExistingWorkspaceException() { super(); }
	public PreExistingWorkspaceException(String message) { super(message); }
	public PreExistingWorkspaceException(String message, Throwable cause) { super(message, cause); }
	public PreExistingWorkspaceException(Throwable cause) { super(cause); }
}
