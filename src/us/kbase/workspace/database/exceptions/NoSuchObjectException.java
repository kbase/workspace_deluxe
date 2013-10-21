package us.kbase.workspace.database.exceptions;

import us.kbase.workspace.database.ObjectIdentifier;

/** 
 * Thrown when the requested workspace doesn't exist.
 * @author gaprice@lbl.gov
 *
 */
public class NoSuchObjectException extends WorkspaceDBException { //InaccessibleObjectException {

	private static final long serialVersionUID = 1L;
	
	public NoSuchObjectException(final String message//,
//			final ObjectIdentifier oi) {
			) {
		super(message); //, oi);
	}
	
	public NoSuchObjectException(final String message,
			//final ObjectIdentifier oi,
			final Throwable cause) {
		super(message, cause); //oi, cause);
	}
}
