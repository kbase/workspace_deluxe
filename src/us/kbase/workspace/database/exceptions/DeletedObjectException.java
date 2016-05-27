package us.kbase.workspace.database.exceptions;

import us.kbase.workspace.database.ObjectIDResolvedWS;

/** 
 * Thrown when the requested object is in a deleted state.
 * @author gaprice@lbl.gov
 *
 */
public class DeletedObjectException extends NoSuchObjectException {

	private static final long serialVersionUID = 1L;

	public DeletedObjectException(String message) {
		super(message);
	}

	public DeletedObjectException(String message, ObjectIDResolvedWS oi) {
		super(message, oi);
	}

	public DeletedObjectException(String message, Throwable cause) {
		super(message, cause);
	}

	public DeletedObjectException(String message, ObjectIDResolvedWS oi,
			Throwable cause) {
		super(message, oi, cause);
	}

}
