package us.kbase.shock.client.exceptions;

/** 
 * Thrown when the shock server responds with an error message.
 * @author gaprice@lbl.gov
 *
 */
public class ShockHttpException extends ShockException {

	private static final long serialVersionUID = 1L;
	private final int code;
	
	/** 
	 * Construct the exception with a http error code.
	 * @param code the http error code that shock passed back to the client.
	 */
	public ShockHttpException(int code) { 
		super();
		this.code = code;
	}
	
	/** 
	 * Construct the exception with a http error code and message.
	 * @param code the http error code that shock passed back to the client.
	 * @param message the error message reported by shock.
	 */
	public ShockHttpException(int code, String message) {
		super(message);
		this.code = code;
	}
	
	/** 
	 * Construct the exception with a http error code, message, and root
	 * cause exception.
	 * @param code the http error code that shock passed back to the client.
	 * @param message the error message reported by shock.
	 * @param cause the exception that caused this exception. Rarely used.
	 */
	public ShockHttpException(int code, String message,
			Throwable cause) { 
		super(message, cause);
		this.code = code;
	}
	
	/**
	 * Construct the exception with a http error code and root
	 * cause exception.
	 * @param code the http error code that shock passed back to the client.
	 * @param cause the exception that caused this exception. Rarely used.
	 */
	public ShockHttpException(int code, Throwable cause) {
		super(cause);
		this.code = code;
	}
	
	/** 
	 * Returns the shock http code.
	 * @return the http error code that shock passed back to the client.
	 */
	public int getHttpCode() {
		return code;
	}

	/* (non-Javadoc)
	 * @see java.lang.Throwable#toString()
	 */
	@Override
	public String toString() {
		String s = getClass().getName() + ": " + code;
		String message = getLocalizedMessage();
		return message != null ? s + " " + message : s;
	}
}
