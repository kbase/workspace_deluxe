package us.kbase.workspace.database.exceptions;

import us.kbase.workspace.database.ObjectIDResolvedWS;

/** 
 * Thrown when the requested object is in a deleted state.
 * @author gaprice@lbl.gov
 *
 */
public class DeletedObjectException extends NoSuchObjectException {

	private static final long serialVersionUID = 1L;

	public DeletedObjectException(final String message, final ObjectIDResolvedWS oi) {
		super(message, oi);
	}

	public DeletedObjectException(
			final String message,
			final ObjectIDResolvedWS oi,
			final Throwable cause) {
		super(message, oi, cause);
	}

}
