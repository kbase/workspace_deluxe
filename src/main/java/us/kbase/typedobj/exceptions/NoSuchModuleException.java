package us.kbase.typedobj.exceptions;

/**
 * Exception thrown when the definition of a module cannot be found in
 * the typed object database.
 * @author rsutormin
 *
 */
public class NoSuchModuleException extends TypedObjectException {

	private static final long serialVersionUID = 7800170258901767317L;

	public NoSuchModuleException(String message) {
		super(message);
	}

	public NoSuchModuleException(String message, Throwable e) {
		super(message,e);
	}
}
