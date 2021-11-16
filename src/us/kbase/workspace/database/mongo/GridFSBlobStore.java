package us.kbase.workspace.database.mongo;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.bson.BsonString;
import org.bson.Document;
import org.slf4j.LoggerFactory;

import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.Restreamable;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.exceptions.FileCacheIOException;
import us.kbase.workspace.database.exceptions.FileCacheLimitExceededException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;

public class GridFSBlobStore implements BlobStore {
	
	// TODO JAVADOC
	
	private static final String ERR_NO_DB_WRITE = "Could not write to the mongo database";
	private final GridFSBucket gfs;
	private final MongoDatabase db;
	
	public GridFSBlobStore(final MongoDatabase db) {
		this.db = requireNonNull(db, "db");
		this.gfs = GridFSBuckets.create(db);
	}

	@Override
	public void saveBlob(
			final MD5 md5,
			final Restreamable data,
			final boolean sorted)
			throws BlobStoreCommunicationException {
		if (data == null || md5 == null) {
			throw new NullPointerException("Arguments cannot be null");
		}
		if (getFileMetadata(md5) != null) {
			return; //already exists
		}
		try (final InputStream is = data.getInputStream()) {
			final GridFSUploadOptions opts = new GridFSUploadOptions()
					.metadata(new Document(Fields.GFS_SORTED, sorted));
			gfs.uploadFromStream(new BsonString(md5.getMD5()), md5.getMD5(), is, opts);
		} catch (IOException e) {
			throw new BlobStoreCommunicationException("Couldn't connect to the GridFS backend: " +
					e.getMessage(), e);
		} catch (MongoWriteException mwe) {
			// if the doc is already here, nothing to be done
			// this block is essentially impossible to test without removing the
			// getFileMetadata line above
			if (!MongoWorkspaceDB.isDuplicateKeyException(mwe)) {
				throw new BlobStoreCommunicationException(ERR_NO_DB_WRITE, mwe);
			}
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(ERR_NO_DB_WRITE, me);
		}
	}

	@Override
	public ByteArrayFileCache getBlob(final MD5 md5, final ByteArrayFileCacheManager bafcMan)
			throws NoSuchBlobException, BlobStoreCommunicationException,
				FileCacheIOException, FileCacheLimitExceededException {
		try {
			final Document out = getFileMetadata(md5);
			if (out == null) {
				throw new NoSuchBlobException(
						"Attempt to retrieve non-existant blob with chksum " + 
								md5.getMD5());
			}
			final boolean sorted = out.getBoolean(Fields.GFS_SORTED, false);
			try (final InputStream file = gfs.openDownloadStream(new BsonString(md5.getMD5()))) {
				return bafcMan.createBAFC(file, true, sorted);
			} catch (IOException ioe) {
				throw new RuntimeException("Something is broken", ioe);
			}	
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not read from the mongo database", me);
		}
	}

	private Document getFileMetadata(final MD5 md5) {
		final GridFSFile doc = gfs.find(new Document(Fields.MONGO_ID, md5.getMD5())).first();
		if (doc == null) {
			return null;
		}
		return doc.getMetadata() == null ? new Document() : doc.getMetadata();
	}

	@Override
	public void removeBlob(MD5 md5) throws BlobStoreCommunicationException {
		try {
			gfs.delete(new BsonString(md5.getMD5()));
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(ERR_NO_DB_WRITE, me);
		}
	}

	@Override
	public List<DependencyStatus> status() {
		//note failures are tested manually for now, if you make changes test
		//things still work
		//TODO TEST add tests exercising failures
		final String version;
		try {
			final Document bi = db.runCommand(new Document("buildInfo", 1));
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
