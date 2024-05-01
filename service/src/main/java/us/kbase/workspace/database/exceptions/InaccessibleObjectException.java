package us.kbase.workspace.database.exceptions;

import us.kbase.workspace.database.ObjectIdentifier;

/** 
 * Thrown when the requested object cannot be accessed.
 * @author gaprice@lbl.gov
 *
 */
public class InaccessibleObjectException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	private final ObjectIdentifier oi;
	

	public InaccessibleObjectException(final String message, final ObjectIdentifier oi) {
		super(message);
		this.oi = oi;
	}
	
	public InaccessibleObjectException(
			final String message,
			final ObjectIdentifier oi,
			final Throwable cause) {
		super(message, cause);
		this.oi = oi;
	}
	
	public ObjectIdentifier getInaccessibleObject() {
		return oi;
	}
}
