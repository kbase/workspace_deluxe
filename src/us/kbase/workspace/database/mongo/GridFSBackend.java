package us.kbase.workspace.database.mongo;

import java.io.IOException;
import java.io.InputStream;

import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.Writable;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.exceptions.FileCacheIOException;
import us.kbase.workspace.database.exceptions.FileCacheLimitExceededException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

import com.gc.iotools.stream.os.OutputStreamToInputStream;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;

public class GridFSBackend implements BlobStore {
	
	private final GridFS gfs;
	
	public GridFSBackend(DB mongodb) {
		gfs = new GridFS(mongodb);
	}

	@Override
	public void saveBlob(final MD5 md5, final Writable data)
			throws BlobStoreCommunicationException {
		if(data == null || md5 == null) {
			throw new IllegalArgumentException("Arguments cannot be null");
		}
		final OutputStreamToInputStream<String> osis =
				new OutputStreamToInputStream<String>() {
					
			@Override
			protected String doRead(InputStream is) throws Exception {
				final GridFSInputFile gif = gfs.createFile(is, true);
				gif.setId(md5.getMD5());
				gif.setFilename(md5.getMD5());
				try {
					gif.save();
				} catch (MongoException.DuplicateKey dk) {
					// already here, done
				} catch (MongoException me) {
					throw new BlobStoreCommunicationException(
							"Could not write to the mongo database", me);
				}
//				is.close(); closing the is has caused deadlocks in other applications
				return null;
			}
		};
		try {
			//writes in UTF8
			data.write(osis);
			data.releaseResources();
		} catch (IOException ioe) {
			throw new RuntimeException("Something is broken", ioe);
		} finally {
			try {
				osis.close();
			} catch (IOException ioe) {
				throw new RuntimeException("Something is broken", ioe);
			}
		}
	}

	@Override
	public ByteArrayFileCache getBlob(final MD5 md5,
			final ByteArrayFileCacheManager bafcMan)
			throws NoSuchBlobException, BlobStoreCommunicationException,
			FileCacheIOException, FileCacheLimitExceededException {
		final DBObject query = new BasicDBObject();
		query.put(Fields.MONGO_ID, md5.getMD5());
		final GridFSDBFile out;
		try {
			out = gfs.findOne(query);
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not write to the mongo database", me);
		}
		if (out == null) {
			throw new NoSuchBlobException(
					"Attempt to retrieve non-existant blob with chksum " + 
							md5.getMD5());
		}
		
		final InputStream file = out.getInputStream();
		try {
			return bafcMan.createBAFC(file, true);
		} finally {
			try {
				file.close();
			} catch (IOException ioe) {
				throw new RuntimeException("Something is broken", ioe);
			}
		}
	}

	@Override
	public void removeBlob(MD5 md5) throws BlobStoreCommunicationException {
		final DBObject query = new BasicDBObject();
		query.put(Fields.MONGO_ID, md5.getMD5());
		try {
			gfs.remove(query);
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not write to the mongo database", me);
		}
	}

	@Override
	public String getExternalIdentifier(MD5 md5) {
		return null;
	}

	@Override
	public String getStoreType() {
		return "GridFS";
	}


}
