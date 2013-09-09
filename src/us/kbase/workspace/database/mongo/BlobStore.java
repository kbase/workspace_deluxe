package us.kbase.workspace.database.mongo;

import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

public interface BlobStore {
	
	public void saveBlob(MD5 md5, String data) throws BlobStoreAuthorizationException,
		BlobStoreCommunicationException;
	
	public String getBlob(MD5 md5) throws BlobStoreAuthorizationException,
		BlobStoreCommunicationException, NoSuchBlobException;
	
	public void removeBlob(MD5 md5) throws BlobStoreAuthorizationException,
		BlobStoreCommunicationException;
	
	public String getExternalIdentifier(MD5 md5) throws
		BlobStoreCommunicationException, NoSuchBlobException;
	
	public String getStoreType();
}
