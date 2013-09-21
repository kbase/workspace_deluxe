package us.kbase.typedobj.exceptions;

/**
 * Exception thrown when problems related to storage subsystem occur in
 * the typed object database.
 * @author rsutormin
 *
 */
public class TypeStorageException extends TypedObjectException {

	private static final long serialVersionUID = 3169019078907064824L;

	public TypeStorageException(String message) {
		super(message);
	}

	public TypeStorageException(String message, Throwable e) {
		super(message,e);
	}

	public TypeStorageException(Throwable e) {
		super(e.getMessage() == null ? "Unknown error" : e.getMessage(), e);
	}
}
