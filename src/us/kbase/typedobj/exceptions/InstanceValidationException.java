package us.kbase.typedobj.exceptions;

/**
 * Exception thrown when an instance cannot be validated against a typed object schema
 * @author msneddon
 *
 */
public class InstanceValidationException extends TypedObjectException {

	public InstanceValidationException(String message) {
		super(message);
	}

	public InstanceValidationException(String message, Throwable e) {
		super(message,e);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -3322709388934700434L;

}
