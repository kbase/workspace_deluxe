package us.kbase.workspace.database.exceptions;

import us.kbase.workspace.database.WorkspaceIdentifier;

/** 
 * Thrown when the requested workspace doesn't exist.
 * @author gaprice@lbl.gov
 *
 */
public class NoSuchWorkspaceException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	private final WorkspaceIdentifier wsi;
	
	public NoSuchWorkspaceException(final String message,
			final WorkspaceIdentifier wsi) {
		super(message);
		this.wsi = wsi;
	}
	public NoSuchWorkspaceException(final String message,
			final WorkspaceIdentifier wsi, final Throwable cause) {
		super(message, cause);
		this.wsi = wsi;
	}
	
	public WorkspaceIdentifier getMissingWorkspace() {
		return wsi;
	}
}
