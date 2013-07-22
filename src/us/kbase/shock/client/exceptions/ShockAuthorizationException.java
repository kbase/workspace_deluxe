package us.kbase.shock.client.exceptions;

import java.util.List;

public class ShockAuthorizationException extends ShockHttpException {

	private static final long serialVersionUID = 1L;
	public static final int AUTH_CODE = 401;
	
	public ShockAuthorizationException(List<String> errors) {
		super(AUTH_CODE, errors);
	}
	public ShockAuthorizationException(List<String> errors, String message) {
		super(AUTH_CODE, errors, message);
	}
	public ShockAuthorizationException(List<String> errors, String message,
			Throwable cause) {
		super(AUTH_CODE, errors, message, cause);
	}
	public ShockAuthorizationException(List<String> errors, Throwable cause) {
		super(AUTH_CODE, errors, cause);
	}
}
