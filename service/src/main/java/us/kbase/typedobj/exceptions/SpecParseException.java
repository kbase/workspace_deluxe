package us.kbase.typedobj.exceptions;

/**
 * Exception thrown when there are some problems occur during spec-file 
 * parsing process.
 * @author rsutormin
 *
 */
public class SpecParseException extends TypedObjectException {

	private static final long serialVersionUID = -7697707902617135728L;

	public SpecParseException(String message) {
		super(message);
	}

	public SpecParseException(String message, Throwable e) {
		super(message,e);
	}
}
