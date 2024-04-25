package us.kbase.workspace.kbase;

/** Thrown when attempting to get type information from another service fails. */
public class TypeDelegationException extends Exception {
	
	private static final long serialVersionUID = 1L;
	
	/** Create the exception.
	 * @param message the exception message.
	 */
	public TypeDelegationException(final String message) {
		super(message);
	}
	
	/** Create the exception.
	 * @param message the exception message.
	 * @param cause the cause of the exception, if any.
	 */
	public TypeDelegationException(final String message, final Throwable cause) {
		super(message, cause);
	}

}
