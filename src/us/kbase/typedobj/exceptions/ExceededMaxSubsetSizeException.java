package us.kbase.typedobj.exceptions;

/**
 * Exception thrown when automatic ws searchable subset exceeds max size
 * @author msneddon
 *
 */
public class ExceededMaxSubsetSizeException extends TypedObjectException {

	private static final long serialVersionUID = 5180995570770398248L;

	public ExceededMaxSubsetSizeException(String message) {
		super(message);
	}

	public ExceededMaxSubsetSizeException(String message, Throwable e) {
		super(message,e);
	}
}
