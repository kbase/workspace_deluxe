package us.kbase.typedobj.exceptions;

/**
 * Exception thrown when the a typed object fails validation against the type
 * JSON schema.
 * @author gaprice@lbl.gov
 *
 */
public class TypedObjectValidationException extends TypedObjectException {

	private static final long serialVersionUID = -3322709388934700434L;
	
	public TypedObjectValidationException(String message) {
		super(message);
	}

	public TypedObjectValidationException(String message, Throwable e) {
		super(message,e);
	}

}
