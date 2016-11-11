package us.kbase.workspace.database.refsearch;

/** An exception thrown when a search has traversed the maximum number of references allowed.
 * @author gaprice@lbl.gov
 *
 */
@SuppressWarnings("serial")
public class ReferenceSearchMaximumSizeExceededException extends Exception {
	
	/** Construct the exception.
	 * @param message an exception message.
	 */
	public ReferenceSearchMaximumSizeExceededException(final String message) {
		super(message);
	}
}
