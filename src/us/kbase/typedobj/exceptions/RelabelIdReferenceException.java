package us.kbase.typedobj.exceptions;

/**
 * Exception thrown when an ID fails relabeling
 * @author msneddon
 *
 */
public class RelabelIdReferenceException extends TypedObjectException {

	public RelabelIdReferenceException(String message) {
		super(message);
	}

	public RelabelIdReferenceException(String message, Throwable e) {
		super(message,e);
	}

	private static final long serialVersionUID = -3322709388934700434L;

}
