package us.kbase.workspace.database.exceptions;

/** 
 * Thrown when the workspace database is corrupt.
 * @author gaprice@lbl.gov
 *
 */
public class CorruptWorkspaceDBException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	public CorruptWorkspaceDBException(String message) { super(message); }
	public CorruptWorkspaceDBException(String message, Throwable cause) { super(message, cause); }
}
