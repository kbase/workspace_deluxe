package us.kbase.workspace.database.exceptions;

import us.kbase.workspace.exceptions.WorkspaceException;

/** 
 * Thrown when an exception occurs regarding the workspace database.
 * @author gaprice@lbl.gov
 *
 */
public class WorkspaceDBException extends WorkspaceException {

	private static final long serialVersionUID = 1L;
	
	public WorkspaceDBException() { super(); }
	public WorkspaceDBException(String message) { super(message); }
	public WorkspaceDBException(String message, Throwable cause) { super(message, cause); }
	public WorkspaceDBException(Throwable cause) { super(cause); }
}
