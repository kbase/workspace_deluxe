package us.kbase.typedobj.exceptions;

/**
 * Exception thrown when extracted metadata size exceeds the max limit
 * @author msneddon
 *
 */
public class ExceededMaxMetadataSizeException extends TypedObjectException {

	private static final long serialVersionUID = -7822796072916430003L;

	public ExceededMaxMetadataSizeException(String message) {
		super(message);
	}

	public ExceededMaxMetadataSizeException(String message, Throwable e) {
		super(message,e);
	}
}
