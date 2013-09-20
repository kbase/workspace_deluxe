package us.kbase.common.service;

/** 
 * Raised when a user cannot be authorized.
 * @author gaprice@lbl.gov
 *
 */
public class UnauthorizedException extends JsonClientException {

	private static final long serialVersionUID = 1L;

	
	public UnauthorizedException(String message) {super(message);}
	public UnauthorizedException(String message, Throwable cause) {super(message, cause);}
}
