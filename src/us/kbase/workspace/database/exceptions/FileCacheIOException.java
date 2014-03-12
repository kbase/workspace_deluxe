package us.kbase.workspace.database.exceptions;

/** 
 * Thrown when a file cache exceeds its allotted space.
 * @author gaprice@lbl.gov
 *
 */
public class FileCacheIOException extends FileCacheException {

	private static final long serialVersionUID = 1L;
	
	public FileCacheIOException() { super(); }
	public FileCacheIOException(String message) { super(message); }
	public FileCacheIOException(String message, Throwable cause) { super(message, cause); }
	public FileCacheIOException(Throwable cause) { super(cause); }
}
