package us.kbase.workspace.database.exceptions;

/** 
 * Thrown when the requested object data doesn't exist.
 * @author gaprice@lbl.gov
 *
 */
public class NoObjectDataException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	public NoObjectDataException(final String message) {
		super(message);
	}
	
	public NoObjectDataException(final String message, final Throwable cause) {
		super(message, cause);
	}
	
}
