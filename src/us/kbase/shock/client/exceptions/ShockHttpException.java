package us.kbase.shock.client.exceptions;

public class ShockHttpException extends ShockException {

	private static final long serialVersionUID = 1L;
	private final int code;
	
	public ShockHttpException(int code) { 
		super();
		this.code = code;
	}
	
	public ShockHttpException(int code, String message) {
		super(message);
		this.code = code;
	}
	
	public ShockHttpException(int code, String message,
			Throwable cause) { 
		super(message, cause);
		this.code = code;
	}
	
	public ShockHttpException(int code, Throwable cause) {
		super(cause);
		this.code = code;
	}
	
	public int getHttpCode() {
		return code;
	}

	@Override
	public String toString() {
		 String s = getClass().getName() + ": " + code;
		 String message = getLocalizedMessage();
		return message != null ? s + " " + message : s;
	}
}
