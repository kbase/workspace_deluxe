package us.kbase.workspace.database.mongo.exceptions;

/** 
 * Thrown when the blob store communication fails.
 * @author gaprice@lbl.gov
 *
 */
public class BlobStoreCommunicationException extends BlobStoreException {

	private static final long serialVersionUID = 1L;
	
	public BlobStoreCommunicationException() { super(); }
	public BlobStoreCommunicationException(String message) { super(message); }
	public BlobStoreCommunicationException(String message, Throwable cause) { super(message, cause); }
	public BlobStoreCommunicationException(Throwable cause) { super(cause); }
}
