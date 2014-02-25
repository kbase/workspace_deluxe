package us.kbase.typedobj.core;

public class JsonTokenValidationException extends Exception {
	public JsonTokenValidationException() {
	}

	public JsonTokenValidationException(String message) {
		super(message);
	}

	public JsonTokenValidationException(Throwable cause) {
		super(cause);
	}
}
