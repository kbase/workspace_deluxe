package us.kbase.workspace.database.mongo.exceptions;

/** 
 * Thrown when attempting to retrieve a blob that doesn't exist.
 * @author gaprice@lbl.gov
 *
 */
public class NoSuchBlobException extends BlobStoreException {

	private static final long serialVersionUID = 1L;
	
	public NoSuchBlobException() { super(); }
	public NoSuchBlobException(String message) { super(message); }
	public NoSuchBlobException(String message, Throwable cause) { super(message, cause); }
	public NoSuchBlobException(Throwable cause) { super(cause); }
}
