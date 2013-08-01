package us.kbase.workspace.database.exceptions;

/** 
 * Thrown when the workspace database isn't properly initialized.
 * @author gaprice@lbl.gov
 *
 */
public class UninitializedWorkspaceDBException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	public UninitializedWorkspaceDBException() { super(); }
	public UninitializedWorkspaceDBException(String message) { super(message); }
	public UninitializedWorkspaceDBException(String message, Throwable cause) { super(message, cause); }
	public UninitializedWorkspaceDBException(Throwable cause) { super(cause); }
}
