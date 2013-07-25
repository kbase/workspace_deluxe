package us.kbase.shock.client.exceptions;

/** 
 * Thrown when the person represented by the client token does not have
 * authorization for the requested action on the shock server.
 * @author gaprice@lbl.gov
 *
 */
public class ShockAuthorizationException extends ShockHttpException {

	private static final long serialVersionUID = 1L;
	
	public ShockAuthorizationException(int code) {
		super(code);
	}
	public ShockAuthorizationException(int code, String message) {
		super(code, message);
	}
	public ShockAuthorizationException(int code, String message,
			Throwable cause) {
		super(code, message, cause);
	}
	public ShockAuthorizationException(int code, Throwable cause) {
		super(code, cause);
	}
}
