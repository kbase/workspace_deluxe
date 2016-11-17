package us.kbase.workspace.database.exceptions;

import us.kbase.workspace.database.ObjectIDResolvedWS;

/** 
 * Thrown when the requested object doesn't exist.
 * @author gaprice@lbl.gov
 *
 */
public class NoSuchObjectException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	private final ObjectIDResolvedWS roi;
	
	public NoSuchObjectException(final String message, final ObjectIDResolvedWS roi) {
		super(message);
		this.roi = roi;
	}
	
	public NoSuchObjectException(
			final String message,
			final ObjectIDResolvedWS roi,
			final Throwable cause) {
		super(message, cause);
		this.roi = roi;
	}
	
	public ObjectIDResolvedWS getResolvedInaccessibleObject() {
		return roi;
	}
}
