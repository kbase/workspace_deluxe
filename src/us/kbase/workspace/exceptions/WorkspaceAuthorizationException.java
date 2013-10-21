package us.kbase.workspace.exceptions;

import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.WorkspaceIdentifier;

/** 
 * Thrown when an unauthorized action is attempted.
 * @author gaprice@lbl.gov
 *
 */
public class WorkspaceAuthorizationException extends WorkspaceException {

	private static final long serialVersionUID = 1L;
	
	private ObjectIdentifier deniedObject = null;
	private WorkspaceIdentifier deniedWS = null;
	
	public WorkspaceAuthorizationException() { super(); }
	public WorkspaceAuthorizationException(String message) { super(message); }
	public WorkspaceAuthorizationException(String message, Throwable cause) { super(message, cause); }
	public WorkspaceAuthorizationException(Throwable cause) { super(cause); }
	
	public void addDeniedCause(final ObjectIdentifier oi) {
		deniedObject = oi;
	}
	
	public ObjectIdentifier getDeniedObject() {
		return deniedObject;
	}
	public void addDeniedCause(final WorkspaceIdentifier wsi) {
		deniedWS = wsi;
	}
	
	public WorkspaceIdentifier getDeniedWorkspace() {
		return deniedWS;
	}
}
