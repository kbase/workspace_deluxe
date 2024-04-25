package us.kbase.workspace.database.refsearch;

import us.kbase.workspace.database.Reference;

/** An exception thrown when a search from a particular reference is exhausted without meeting
 * the search criteria.
 * @author gaprice@lbl.gov
 *
 */
@SuppressWarnings("serial")
public class ReferenceSearchFailedException extends Exception {
	
	private final Reference failedRef;
	
	/** Construct the exception.
	 * @param failedOn the reference for which the search failed.
	 */
	public ReferenceSearchFailedException(final Reference failedOn) {
		super();
		failedRef = failedOn;
	}
	
	/** Get the reference for which the search failed.
	 * @return the reference for which the search failed.
	 */
	public Reference getFailedReference() {
		return failedRef;
	}
}