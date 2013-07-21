package us.kbase.shock.client.exceptions;

public class ShockAuthorizationException extends ShockHttpException {

	private static final long serialVersionUID = 1L;
	private static final int AUTH_CODE = 401;
	
	public ShockAuthorizationException() { super(AUTH_CODE); }
	public ShockAuthorizationException(String message) { super(AUTH_CODE, message); }
	public ShockAuthorizationException(String message, Throwable cause) { super(AUTH_CODE, message, cause); }
	public ShockAuthorizationException(Throwable cause) { super(AUTH_CODE, cause); }
}
