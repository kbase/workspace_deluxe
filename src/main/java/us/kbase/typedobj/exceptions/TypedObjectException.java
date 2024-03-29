package us.kbase.typedobj.exceptions;


/**
 * Base class of all exceptions thrown by the Typed Object library.
 * @author msneddon
 *
 */
public class TypedObjectException extends Exception {

	private static final long serialVersionUID = -8916866328091885334L;

	public TypedObjectException(String message) {
		super(message);
	}

	public TypedObjectException(String message, Throwable e) {
		super(message,e);
	}
	
}
