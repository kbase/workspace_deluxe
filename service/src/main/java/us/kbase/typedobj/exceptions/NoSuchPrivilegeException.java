package us.kbase.typedobj.exceptions;

/**
 * Exception thrown when the definition of a module cannot be found in
 * the typed object database.
 * @author rsutormin
 *
 */
public class NoSuchPrivilegeException extends TypedObjectException {

	private static final long serialVersionUID = -5548902492885094934L;

	public NoSuchPrivilegeException(String message) {
		super(message);
	}

	public NoSuchPrivilegeException(String message, Throwable e) {
		super(message,e);
	}
}
