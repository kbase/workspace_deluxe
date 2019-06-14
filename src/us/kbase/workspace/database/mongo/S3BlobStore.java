package us.kbase.workspace.database.mongo;

import java.util.List;

import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.Restreamable;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.exceptions.FileCacheIOException;
import us.kbase.workspace.database.exceptions.FileCacheLimitExceededException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

public class S3BlobStore implements BlobStore {
	
	public S3BlobStore() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void saveBlob(final MD5 md5, final Restreamable data, final boolean sorted)
			throws BlobStoreAuthorizationException, BlobStoreCommunicationException {
		// TODO Auto-generated method stub

	}

	@Override
	public ByteArrayFileCache getBlob(final MD5 md5, final ByteArrayFileCacheManager bafcMan)
			throws BlobStoreAuthorizationException, BlobStoreCommunicationException,
				NoSuchBlobException, FileCacheLimitExceededException, FileCacheIOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void removeBlob(final MD5 md5)
			throws BlobStoreAuthorizationException, BlobStoreCommunicationException {
		// TODO Auto-generated method stub

	}

	@Override
	public String getExternalIdentifier(final MD5 md5)
			throws BlobStoreCommunicationException, NoSuchBlobException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<DependencyStatus> status() {
		// TODO Auto-generated method stub
		return null;
	}

}
