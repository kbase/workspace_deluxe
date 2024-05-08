package us.kbase.workspace.database.exceptions;

/** 
 * Thrown when the workspace database cannot be initialized.
 * @author gaprice@lbl.gov
 *
 */
public class WorkspaceDBInitializationException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	public WorkspaceDBInitializationException() { super(); }
	public WorkspaceDBInitializationException(String message) { super(message); }
	public WorkspaceDBInitializationException(String message, Throwable cause) { super(message, cause); }
	public WorkspaceDBInitializationException(Throwable cause) { super(cause); }
}
