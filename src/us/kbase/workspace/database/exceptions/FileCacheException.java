package us.kbase.workspace.database.exceptions;

/** 
 * Parent class of file cache errors.
 * @author gaprice@lbl.gov
 *
 */
public class FileCacheException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	public FileCacheException() { super(); }
	public FileCacheException(String message) { super(message); }
	public FileCacheException(String message, Throwable cause) { super(message, cause); }
	public FileCacheException(Throwable cause) { super(cause); }
}
