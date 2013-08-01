package us.kbase.workspace.exceptions;

/** 
 * Thrown when an exception occurs regarding the workspace.
 * @author gaprice@lbl.gov
 *
 */
public class WorkspaceException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public WorkspaceException() { super(); }
	public WorkspaceException(String message) { super(message); }
	public WorkspaceException(String message, Throwable cause) { super(message, cause); }
	public WorkspaceException(Throwable cause) { super(cause); }
}
