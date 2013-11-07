package us.kbase.workspace.database.mongo;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;

import us.kbase.typedobj.core.MD5;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

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

	/* (non-Javadoc)
	 * @see us.kbase.workspace.database.BlobStore#saveBlob(us.kbase.workspace.database.TypeData)
	 */
	@Override
	public void saveBlob(MD5 md5, String data) throws BlobStoreCommunicationException {
		if(data == null || md5 == null) {
			throw new IllegalArgumentException("Arguments cannot be null");
		}
		//use input stream to avoid making copy of data in memory
		GridFSInputFile gif = gfs.createFile(new ReaderInputStream(
				new StringReader(data), Charset.defaultCharset()), true);
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
	}

	@Override
	public String getBlob(MD5 md5) throws NoSuchBlobException,
			BlobStoreCommunicationException {
		DBObject query = new BasicDBObject();
		query.put(Fields.MONGO_ID, md5.getMD5());
		GridFSDBFile out;
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
		try {
			return IOUtils.toString(out.getInputStream(), "UTF-8");
		} catch (IOException ioe) {
			//should never happen
			throw new RuntimeException("GridFS is apparently buggy"); 
		}
	}

	@Override
	public void removeBlob(MD5 md5) throws BlobStoreCommunicationException {
		DBObject query = new BasicDBObject();
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
