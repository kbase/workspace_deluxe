package us.kbase.workspace.kbase.admin;

/** An exception thrown from an administrator handler.
 * @author gaprice@lbl.gov
 *
 */
@SuppressWarnings("serial")
public class AdministratorHandlerException extends Exception {

	public AdministratorHandlerException(final String message) {
		super(message);
	}
	
	public AdministratorHandlerException(final String message, final Throwable cause) {
		super(message, cause);
	}

}
