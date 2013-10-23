package us.kbase.workspace.database.exceptions;

import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIdentifier;

/** 
 * Thrown when the requested object cannot be accessed.
 * @author gaprice@lbl.gov
 *
 */
public class InaccessibleObjectException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	private final ObjectIDResolvedWS roi;
	private final ObjectIdentifier oi;
	
	public InaccessibleObjectException(final String message) {
		super(message);
		this.roi = null;
		this.oi = null;
	}

	public InaccessibleObjectException(final String message,
			final Throwable cause) {
		super(message, cause);
		this.roi = null;
		this.oi = null;
	}
	
	public InaccessibleObjectException(final String message,
			final ObjectIDResolvedWS oi) {
		super(message);
		this.roi = oi;
		this.oi = null;
	}
	
	
	public InaccessibleObjectException(final String message,
			final ObjectIDResolvedWS oi, final Throwable cause) {
		super(message, cause);
		this.roi = oi;
		this.oi = null;
	}
	
	public InaccessibleObjectException(final String message,
			final ObjectIdentifier oi) {
		super(message);
		this.roi = null;
		this.oi = oi;
	}
	
	public InaccessibleObjectException(final String message,
			final ObjectIdentifier oi, final Throwable cause) {
		super(message, cause);
		this.roi = null;
		this.oi = oi;
	}
	
	public ObjectIDResolvedWS getResolvedInaccessibleObject() {
		return roi;
	}
	
	public ObjectIdentifier getInaccessibleObject() {
		return oi;
	}
}
