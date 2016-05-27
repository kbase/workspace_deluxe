package us.kbase.workspace.database.exceptions;

import us.kbase.workspace.database.ObjectIdentifier;

/** 
 * Thrown when an object does not have an requested reference.
 * @author gaprice@lbl.gov
 *
 */
public class NoSuchReferenceException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	private final ObjectIdentifier roi;
	private final ObjectIdentifier ref;
	
	public NoSuchReferenceException(final String message,
			final ObjectIdentifier oi, final ObjectIdentifier ref) {
		super(message);
		this.roi = oi;
		this.ref = ref;
	}
	
	public NoSuchReferenceException(final String message,
			final ObjectIdentifier oi, final ObjectIdentifier ref,
			final Throwable cause) {
		super(message, cause);
		this.roi = oi;
		this.ref = ref;
	}
	
	public ObjectIdentifier getFromObject() {
		return roi;
	}

	public ObjectIdentifier getRef() {
		return ref;
	}
	
}
