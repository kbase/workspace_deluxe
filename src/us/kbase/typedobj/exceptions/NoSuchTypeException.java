package us.kbase.typedobj.exceptions;

/**
 * Exception thrown when the definition of a typed object cannot be found in
 * the typed object database.
 * @author msneddon
 *
 */
public class NoSuchTypeException extends TypedObjectException {

	public NoSuchTypeException(String message) {
		super(message);
	}

	public NoSuchTypeException(String message, Throwable e) {
		super(message,e);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -3322709388934700434L;

}
