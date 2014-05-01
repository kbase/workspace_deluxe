package us.kbase.typedobj.core;

public class JsonTokenValidationException extends Exception {
	private static final long serialVersionUID = 1L;

	public JsonTokenValidationException() {
	}

	public JsonTokenValidationException(String message) {
		super(message);
	}

	public JsonTokenValidationException(Throwable cause) {
		super(cause);
	}
}
