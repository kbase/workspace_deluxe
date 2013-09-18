package us.kbase.typedobj.exceptions;

/**
 * Exception thrown when a schema document retrieved from the typed object db
 * is not valid or could not be read
 * @author msneddon
 *
 */
public class BadJsonSchemaDocumentException extends TypedObjectException {

	public BadJsonSchemaDocumentException(String message) {
		super(message);
	}

	public BadJsonSchemaDocumentException(String message, Throwable e) {
		super(message,e);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -3322709388934700434L;

}
