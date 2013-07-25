package us.kbase.shock.client.exceptions;

/**
 * Thrown on an attempt to get a file from a shock node that has no file.
 * @author gaprice@lbl.gov
 *
 */
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
