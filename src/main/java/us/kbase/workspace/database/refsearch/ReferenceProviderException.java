package us.kbase.workspace.database.refsearch;

/** An exception thrown when a failure occurs in a reference provider.
 * @author gaprice@lbl.gov
 *
 */
@SuppressWarnings("serial")
public class ReferenceProviderException extends Exception {
	
	/** Construct the exception.
	 * @param message an exception message.
	 * @cause the cause of the exception.
	 */
	public ReferenceProviderException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
