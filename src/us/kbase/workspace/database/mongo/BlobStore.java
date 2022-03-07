package us.kbase.workspace.database.mongo;

import java.util.List;

import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.Restreamable;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.exceptions.FileCacheIOException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

public interface BlobStore {
	
	
	/** Save a blob to the Blob Store. Note that the blob store is not
	 * guaranteed to call close() on the input stream.
	 * @param md5 the md5 of the blob.
	 * @param data the blob.
	 * @param sorted true if the data is sorted, false otherwise.
	 * @throws BlobStoreAuthorizationException if the blobstore is not
	 * authorized to write to the blob store backend. 
	 * @throws BlobStoreCommunicationException if a communication error with
	 * the blob store backend occurs.
	 */
	public void saveBlob(MD5 md5, Restreamable data, boolean sorted)
			throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException;
	
	/** Get a blob.
	 * @param md5 the md5 of the blob.
	 * @param bafcMan a data manager to manage the blob data.
	 * @return the blob data.
	 * @throws BlobStoreAuthorizationException if the blobstore is not
	 * authorized to write to the blob store backend. 
	 * @throws BlobStoreCommunicationException if a communication error with
	 * the blob store backend occurs. 
	 * @throws NoSuchBlobException if there is no blob matching the md5
	 * @throws FileCacheIOException if the data manager throws an IO exception.
	 */
	public ByteArrayFileCache getBlob(
			MD5 md5,
			ByteArrayFileCacheManager bafcMan)
			throws BlobStoreAuthorizationException,
				BlobStoreCommunicationException, NoSuchBlobException, FileCacheIOException;
	
	/**
	 * Do not call removeBlob when saveBlob could be run by other threads or
	 * applications. Doing so could result in an inconsistent state in the
	 * database.
	 * 
	 * @param md5
	 * @throws BlobStoreAuthorizationException
	 * @throws BlobStoreCommunicationException
	 */
	public void removeBlob(MD5 md5) throws BlobStoreAuthorizationException,
		BlobStoreCommunicationException;
	
	/** Returns the status of the blob store's dependencies.
	 * @return the dependency status.
	 */
	public List<DependencyStatus> status();
}
