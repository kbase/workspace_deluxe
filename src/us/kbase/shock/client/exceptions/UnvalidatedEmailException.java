package us.kbase.shock.client.exceptions;

/** 
 * Thrown when a users's email address has not been validated.
 * @author gaprice@lbl.gov
 *
 */
public class UnvalidatedEmailException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public UnvalidatedEmailException() { super(); }
	public UnvalidatedEmailException(String message) { super(message); }
	public UnvalidatedEmailException(String message, Throwable cause) { super(message, cause); }
	public UnvalidatedEmailException(Throwable cause) { super(cause); }
}
