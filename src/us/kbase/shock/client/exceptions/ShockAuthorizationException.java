package us.kbase.shock.client.exceptions;

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
