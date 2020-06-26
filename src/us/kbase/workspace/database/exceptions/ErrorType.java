package us.kbase.workspace.database.exceptions;

/** An enum representing the type of a particular error.
 * @author gaprice@lbl.gov
 *
 */

// TODO ERROR change the current exception types to *LegacyException and use error code in new exceptions
public enum ErrorType {
	
	// many of these are unused currently but will be used in the future
	
	/** The authentication service returned an error. */
	AUTHENTICATION_FAILED	(10000, "Authentication failed"),
	/** No token was provided when required */
	NO_TOKEN				(10010, "No authentication token"),
	/** The token provided is not valid. */
	INVALID_TOKEN			(10020, "Invalid token"),
	/** The user is not authorized to perform the requested action. */
	UNAUTHORIZED			(20000, "Unauthorized"),
	/** A required input parameter was not provided. */
	MISSING_PARAMETER		(30000, "Missing input parameter"),
	/** An input parameter had an illegal value. */
	ILLEGAL_PARAMETER		(30001, "Illegal input parameter"),
	/** The provided user name was not legal. */
	ILLEGAL_USER_NAME		(30010, "Illegal user name"),
	/** The provided user does not exist. */
	NO_SUCH_USER			(50000, "No such user"),
	/** The provided workspace does not exist. */
	NO_SUCH_WORKSPACE		(50010, "No such group"),
	/** The provided workspace is deleted. */
	DELETED_WORKSPACE		(50020, "Workspace is deleted"),
	/** The requested operation is not supported. */
	UNSUPPORTED_OP			(100000, "Unsupported operation");
	
	private final int errcode;
	private final String error;
	
	private ErrorType(final int errcode, final String error) {
		this.errcode = errcode;
		this.error = error;
	}

	/** Get the error code for the error type.
	 * @return the error code.
	 */
	public int getErrorCode() {
		return errcode;
	}

	/** Get a text description of the error type.
	 * @return the error.
	 */
	public String getError() {
		return error;
	}

}
