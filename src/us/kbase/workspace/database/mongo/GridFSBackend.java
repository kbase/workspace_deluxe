package us.kbase.workspace.database.mongo;

import java.io.IOException;

import org.apache.commons.io.IOUtils;

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
	public void saveBlob(TypeData td) throws BlobStoreCommunicationException {
		if(td.getData() == null) {
			throw new RuntimeException("No data in typedata object");
		}
//		td.setGridFS();
		GridFSInputFile gif = gfs.createFile(td.getData().getBytes());
		gif.setId(td.getChksum());
		gif.setFilename(td.getChksum());
		try {
			gif.save();
		} catch (MongoException.DuplicateKey dk) {
			// already here, done
//			throw new DuplicateBlobException(
//					"Attempt to add a duplicate blob with id " + td.getChksum());
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not write to the mongo database", me);
		}
	}

	@Override
	public String getBlob(TypeData td) throws NoSuchBlobException,
			BlobStoreCommunicationException {
//		if (!td.isGridFSBlob()) {
//			throw new IllegalStateException(
//					"This data is not stored in gridFS");
//		}
		DBObject query = new BasicDBObject();
		query.put("_id", td.getChksum());
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
					td.getChksum());
		}
		try {
			return IOUtils.toString(out.getInputStream(), "UTF-8");
		} catch (IOException ioe) {
			//should never happen
			throw new RuntimeException("GridFS is apparently buggy"); 
		}
	}

	@Override
	public void removeBlob(TypeData td) throws BlobStoreCommunicationException {
//		if (!td.isGridFSBlob()) {
//			throw new IllegalStateException(
//					"This data is not stored in gridFS");
//		}
		DBObject query = new BasicDBObject();
		query.put("_id", td.getChksum());
		try {
			gfs.remove(query);
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not write to the mongo database", me);
		}
	}

	@Override
	public String getExternalIdentifier(TypeData td) {
		return null;
	}

	@Override
	public String getStoreType() {
		return "GridFS";
	}


}
