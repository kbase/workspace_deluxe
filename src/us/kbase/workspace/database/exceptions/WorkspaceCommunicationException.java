package us.kbase.workspace.database.exceptions;

/** 
 * Thrown when a problem with the workspace database occurs.
 * @author gaprice@lbl.gov
 *
 */
public class WorkspaceCommunicationException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	public WorkspaceCommunicationException() { super(); }
	public WorkspaceCommunicationException(String message) { super(message); }
	public WorkspaceCommunicationException(String message, Throwable cause) { super(message, cause); }
	public WorkspaceCommunicationException(Throwable cause) { super(cause); }
}
