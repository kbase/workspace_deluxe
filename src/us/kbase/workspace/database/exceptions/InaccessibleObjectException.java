package us.kbase.workspace.database.exceptions;

import us.kbase.workspace.database.ObjectIDResolvedWS;

/** 
 * Thrown when the requested object cannot be accessed.
 * @author gaprice@lbl.gov
 *
 */
public class InaccessibleObjectException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	private final ObjectIDResolvedWS oi;
	
	public InaccessibleObjectException(final String message) {
		super(message);
		this.oi = null;
	}
	public InaccessibleObjectException(final String message,
			final ObjectIDResolvedWS oi) {
		super(message);
		this.oi = oi;
	}
	
	public InaccessibleObjectException(final String message,
			final Throwable cause) {
		super(message, cause);
		this.oi = null;
	}
	
	public InaccessibleObjectException(final String message,
			final ObjectIDResolvedWS oi, final Throwable cause) {
		super(message, cause);
		this.oi = oi;
	}
	
	public ObjectIDResolvedWS getInaccessibleObject() {
		return oi;
	}
}
