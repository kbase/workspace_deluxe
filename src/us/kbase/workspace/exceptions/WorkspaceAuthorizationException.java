package us.kbase.workspace.exceptions;

/** 
 * Thrown when an unauthorized action is attempted.
 * @author gaprice@lbl.gov
 *
 */
public class WorkspaceAuthorizationException extends WorkspaceException {

	private static final long serialVersionUID = 1L;
	
	public WorkspaceAuthorizationException() { super(); }
	public WorkspaceAuthorizationException(String message) { super(message); }
	public WorkspaceAuthorizationException(String message, Throwable cause) { super(message, cause); }
	public WorkspaceAuthorizationException(Throwable cause) { super(cause); }
}
