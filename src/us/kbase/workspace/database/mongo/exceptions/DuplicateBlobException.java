package us.kbase.workspace.database.mongo.exceptions;

/** 
 * Thrown when attempting to save a blob that already exists.
 * @author gaprice@lbl.gov
 *
 */
public class DuplicateBlobException extends BlobStoreException {

	private static final long serialVersionUID = 1L;
	
	public DuplicateBlobException() { super(); }
	public DuplicateBlobException(String message) { super(message); }
	public DuplicateBlobException(String message, Throwable cause) { super(message, cause); }
	public DuplicateBlobException(Throwable cause) { super(cause); }
}
