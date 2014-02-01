package us.kbase.workspace.database.exceptions;

import us.kbase.workspace.database.ObjectIDResolvedWS;

/** 
 * Thrown when an object does not have an requested reference.
 * @author gaprice@lbl.gov
 *
 */
public class NoSuchReferenceException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	private final ObjectIDResolvedWS roi;
	private final ObjectIDResolvedWS ref;
	
	public NoSuchReferenceException(final String message,
			final ObjectIDResolvedWS oi, final ObjectIDResolvedWS ref) {
		super(message);
		this.roi = oi;
		this.ref = ref;
	}
	
	public NoSuchReferenceException(final String message,
			final ObjectIDResolvedWS oi, final ObjectIDResolvedWS ref,
			final Throwable cause) {
		super(message, cause);
		this.roi = oi;
		this.ref = ref;
	}
	
	public ObjectIDResolvedWS getResolvedInaccessibleObject() {
		return roi;
	}

	public ObjectIDResolvedWS getRef() {
		return ref;
	}
	
}
