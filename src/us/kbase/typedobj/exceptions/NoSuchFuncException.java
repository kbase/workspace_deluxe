package us.kbase.typedobj.exceptions;

/**
 * Exception thrown when the definition of a function object cannot be found in
 * the typed object database.
 * @author rsutormin
 *
 */
public class NoSuchFuncException extends TypedObjectException {

	private static final long serialVersionUID = -5673956370660241549L;

	public NoSuchFuncException(String message) {
		super(message);
	}

	public NoSuchFuncException(String message, Throwable e) {
		super(message,e);
	}
}
