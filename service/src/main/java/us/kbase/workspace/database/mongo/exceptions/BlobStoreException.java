package us.kbase.workspace.database.mongo.exceptions;

import us.kbase.workspace.database.exceptions.WorkspaceDBException;

/** 
 * Thrown when a problem with the workspace blob store occurs.
 * @author gaprice@lbl.gov
 *
 */
public class BlobStoreException extends WorkspaceDBException {

	private static final long serialVersionUID = 1L;
	
	public BlobStoreException() { super(); }
	public BlobStoreException(String message) { super(message); }
	public BlobStoreException(String message, Throwable cause) { super(message, cause); }
	public BlobStoreException(Throwable cause) { super(cause); }
}
