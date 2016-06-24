package us.kbase.workspace.database.mongo;

import java.util.List;

import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.Writable;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.exceptions.FileCacheIOException;
import us.kbase.workspace.database.exceptions.FileCacheLimitExceededException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

public interface BlobStore {
	
	
	/** Save a blob to the Blob Store. Note that the blob store is not
	 * guaranteed to call releaseResources() on the Writer.
	 * @param md5 the md5 of the blob.
	 * @param data the blob.
	 * @param sorted true if the data is sorted, false otherwise.
	 * @throws BlobStoreAuthorizationException if the blobstore is not
	 * authorized to write to the blob store backend. 
	 * @throws BlobStoreCommunicationException if a communication error with
	 * the blob store backend occurs.
	 */
	public void saveBlob(MD5 md5, Writable data, boolean sorted)
			throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException;
	
	public ByteArrayFileCache getBlob(MD5 md5, ByteArrayFileCacheManager bafcMan)
			throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException, NoSuchBlobException,
			FileCacheLimitExceededException, FileCacheIOException;
	
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
	
	public String getExternalIdentifier(MD5 md5) throws
		BlobStoreCommunicationException, NoSuchBlobException;
	
	public String getStoreType();

	/** Returns the status of the blob store's dependencies.
	 * @return the dependency status.
	 */
	public List<DependencyStatus> status();
}
