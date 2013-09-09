package us.kbase.workspace.database.mongo;

import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.DuplicateBlobException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

public interface BlobStore {
	
	public void saveBlob(TypeData td) throws BlobStoreAuthorizationException,
		BlobStoreCommunicationException, DuplicateBlobException;
	
	public String getBlob(TypeData td) throws BlobStoreAuthorizationException,
		BlobStoreCommunicationException, NoSuchBlobException;
	
	public void removeBlob(TypeData td) throws BlobStoreAuthorizationException,
		BlobStoreCommunicationException;
	
	public String getExternalIdentifier(TypeData td);
	
	public String getStoreType();
}
