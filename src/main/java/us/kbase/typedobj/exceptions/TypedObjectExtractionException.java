package us.kbase.typedobj.exceptions;

/**
 * Exception thrown when sub data cannot be extracted from an object,
 * used by TypedObjectExtractor
 * @author msneddon
 *
 */
public class TypedObjectExtractionException extends TypedObjectException {

	private static final long serialVersionUID = 7800170258901767317L;

	public TypedObjectExtractionException(String message) {
		super(message);
	}

	public TypedObjectExtractionException(String message, Throwable e) {
		super(message,e);
	}
}
