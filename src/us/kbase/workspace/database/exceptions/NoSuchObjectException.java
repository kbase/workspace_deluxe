package us.kbase.workspace.database.exceptions;

import us.kbase.workspace.database.ObjectIDResolvedWS;

/** 
 * Thrown when the requested workspace doesn't exist.
 * @author gaprice@lbl.gov
 *
 */
public class NoSuchObjectException extends InaccessibleObjectException {

	private static final long serialVersionUID = 1L;
	
	public NoSuchObjectException(final String message) {
		super(message);
	}
	
	public NoSuchObjectException(final String message, 
			final ObjectIDResolvedWS oi) {
		super(message, oi);
	}
	
	public NoSuchObjectException(final String message,
			final Throwable cause) {
		super(message, cause);
	}
	
	public NoSuchObjectException(final String message,
			final ObjectIDResolvedWS oi, final Throwable cause) {
		super(message, oi, cause);
	}
}
