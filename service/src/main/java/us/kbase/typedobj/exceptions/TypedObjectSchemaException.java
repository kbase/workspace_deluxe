package us.kbase.typedobj.exceptions;

/**
 * Exception thrown when a schema document retrieved from the typed object db
 * is not valid or could not be read
 * @author msneddon
 *
 */
public class TypedObjectSchemaException extends TypedObjectException {

	private static final long serialVersionUID = -7997106217710220872L;

	public TypedObjectSchemaException(String message) {
		super(message);
	}

	public TypedObjectSchemaException(String message, Throwable e) {
		super(message,e);
	}


}
