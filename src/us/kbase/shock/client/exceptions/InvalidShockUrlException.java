package us.kbase.shock.client.exceptions;

public class InvalidShockUrlException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public InvalidShockUrlException() { super(); }
	public InvalidShockUrlException(String message) { super(message); }
	public InvalidShockUrlException(String message, Throwable cause) { super(message, cause); }
	public InvalidShockUrlException(Throwable cause) { super(cause); }
}
