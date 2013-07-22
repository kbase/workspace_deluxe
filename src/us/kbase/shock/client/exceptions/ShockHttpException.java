package us.kbase.shock.client.exceptions;

import java.util.List;

public class ShockHttpException extends ShockException {

	private static final long serialVersionUID = 1L;
	private final int code;
	private final List<String> errors;
	
	public ShockHttpException(int code, List<String> errors) { 
		super();
		this.code = code;
		this.errors = errors;
	}
	
	public ShockHttpException(int code, List<String> errors, String message) {
		super(message);
		this.code = code;
		this.errors = errors;
	}
	
	public ShockHttpException(int code, List<String> errors, String message,
			Throwable cause) { 
		super(message, cause);
		this.code = code;
		this.errors = errors;
	}
	
	public ShockHttpException(int code, List<String> errors, Throwable cause) {
		super(cause);
		this.code = code;
		this.errors = errors;
	}
	
	public int getHttpCode() {
		return code;
	}
	
	public List<String> getShockErrors() {
		return errors;
	}

	@Override
	public String toString() {
		 String s = getClass().getName();
		 String message = getLocalizedMessage();
		return ((message != null) ? (s + ": " + message) : s) + 
				" [code=" + code + ", errors=" + errors + "]";
	}
}
