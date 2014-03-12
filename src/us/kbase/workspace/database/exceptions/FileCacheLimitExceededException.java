package us.kbase.workspace.database.exceptions;

/** 
 * Thrown when a file cache exceeds its allotted space.
 * @author gaprice@lbl.gov
 *
 */
public class FileCacheLimitExceededException extends FileCacheException {

	private static final long serialVersionUID = 1L;
	
	public FileCacheLimitExceededException() { super(); }
	public FileCacheLimitExceededException(String message) { super(message); }
	public FileCacheLimitExceededException(String message, Throwable cause) { super(message, cause); }
	public FileCacheLimitExceededException(Throwable cause) { super(cause); }
}
