package us.kbase.workspace.listener;

/** Thrown when a workspace event listener cannot be initialized.
 * @author gaprice@lbl.gov
 *
 */
@SuppressWarnings("serial")
public class ListenerInitializationException extends Exception {
	
	public ListenerInitializationException(final String message) {
		super(message);
	}
	
	public ListenerInitializationException(final String message, final Throwable cause) {
		super(message, cause);
	}
	

}
