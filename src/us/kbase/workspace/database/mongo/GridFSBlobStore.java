package us.kbase.workspace.database.mongo;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.slf4j.LoggerFactory;

import us.kbase.typedobj.core.MD5;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.exceptions.FileCacheIOException;
import us.kbase.workspace.database.exceptions.FileCacheLimitExceededException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.DuplicateKeyException;
import com.mongodb.MongoException;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;

public class GridFSBlobStore implements BlobStore {
	
	private final GridFS gfs;
	
	public GridFSBlobStore(DB mongodb) {
		gfs = new GridFS(mongodb);
	}

	@Override
	public void saveBlob(final MD5 md5, final InputStream data,
			final boolean sorted)
			throws BlobStoreCommunicationException {
		if(data == null || md5 == null) {
			throw new NullPointerException("Arguments cannot be null");
		}
		if (getFile(md5) != null) {
			return; //already exists
		}
		final GridFSInputFile gif = gfs.createFile(data, true);
		gif.setId(md5.getMD5());
		gif.setFilename(md5.getMD5());
		gif.put(Fields.GFS_SORTED, sorted);
		try {
			gif.save();
		} catch (DuplicateKeyException dk) {
			// already here, done
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not write to the mongo database", me);
		}
	}

	@Override
	public ByteArrayFileCache getBlob(final MD5 md5,
			final ByteArrayFileCacheManager bafcMan)
			throws NoSuchBlobException, BlobStoreCommunicationException,
			FileCacheIOException, FileCacheLimitExceededException {
		final GridFSDBFile out;
		try {
			out = getFile(md5);
			if (out == null) {
				throw new NoSuchBlobException(
						"Attempt to retrieve non-existant blob with chksum " + 
								md5.getMD5());
			}
			final boolean sorted;
			if (!out.containsField(Fields.GFS_SORTED)) {
				sorted = false;
			} else {
				sorted = (Boolean)out.get(Fields.GFS_SORTED);
			}
			final InputStream file = out.getInputStream();
			try {
				return bafcMan.createBAFC(file, true, sorted);
			} finally {
				try {
					file.close();
				} catch (IOException ioe) {
					throw new RuntimeException("Something is broken", ioe);
				}
			}	
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not read from the mongo database", me);
		}
	}

	private GridFSDBFile getFile(final MD5 md5) {
		final GridFSDBFile out;
		final DBObject query = new BasicDBObject();
		query.put(Fields.MONGO_ID, md5.getMD5());
		out = gfs.findOne(query);
		return out;
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
	public List<DependencyStatus> status() {
		//note failures are tested manually for now, if you make changes test
		//things still work
		//TODO TEST add tests exercising failures
		final String version;
		try {
			final CommandResult bi = gfs.getDB().command("buildInfo");
			version = bi.getString("version");
		} catch (MongoException e) {
			LoggerFactory.getLogger(getClass())
				.error("Failed to connect to MongoDB", e);
			return Arrays.asList(new DependencyStatus(false,
					"Couldn't connect to MongoDB: " + e.getMessage(),
					"GridFS", "Unknown"));
		}
		return Arrays.asList(
				new DependencyStatus(true, "OK", "GridFS", version));
	}


}
