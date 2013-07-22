package us.kbase.shock.client.exceptions;

public class ShockNoFileException extends ShockHttpException {

	private static final long serialVersionUID = 1L;
	
	public ShockNoFileException(int code) {
		super(code);
	}
	public ShockNoFileException(int code, String message) {
		super(code, message);
	}
	public ShockNoFileException(int code, String message,
			Throwable cause) {
		super(code, message, cause);
	}
	public ShockNoFileException(int code, Throwable cause) {
		super(code, cause);
	}
}
