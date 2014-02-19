package us.kbase.workspace.database.mongo;

import com.fasterxml.jackson.databind.JsonNode;

import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.validatornew.Writable;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

public interface BlobStore {
	
	public void saveBlob(MD5 md5, Writable data) throws BlobStoreAuthorizationException,
		BlobStoreCommunicationException;
	
	public JsonNode getBlob(MD5 md5) throws BlobStoreAuthorizationException,
		BlobStoreCommunicationException, NoSuchBlobException;
	
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
}
