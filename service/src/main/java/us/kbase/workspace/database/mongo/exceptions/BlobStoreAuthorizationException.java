package us.kbase.workspace.database.mongo.exceptions;

/** 
 * Thrown when authorization to a database is denied.
 * @author gaprice@lbl.gov
 *
 */
public class BlobStoreAuthorizationException extends BlobStoreException {

	private static final long serialVersionUID = 1L;
	
	public BlobStoreAuthorizationException() { super(); }
	public BlobStoreAuthorizationException(String message) { super(message); }
	public BlobStoreAuthorizationException(String message, Throwable cause) { super(message, cause); }
	public BlobStoreAuthorizationException(Throwable cause) { super(cause); }
}
