package us.kbase.typedobj.exceptions;


/**
 * Base class of all exceptions thrown by the Typed Object library.
 * @author msneddon
 *
 */
public class TypedObjectException extends Exception {

	public TypedObjectException(String message) {
		super(message);
	}

	public TypedObjectException(String message, Throwable e) {
		super(message,e);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -3317046508635322514L;

}
