package us.kbase.workspace.database.mongo;

import java.io.IOException;

import org.apache.commons.io.IOUtils;

import us.kbase.workspace.database.mongo.exceptions.DuplicateBlobException;
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
	public void saveBlob(TypeData td) throws DuplicateBlobException {
		if(td.getData() == null) {
			throw new RuntimeException("No data in typedata object");
		}
		GridFSInputFile gif = gfs.createFile(td.getData().getBytes());
		gif.setId(td.getChsum());
		gif.setFilename(td.getChsum());
		try {
			gif.save();
		} catch (MongoException.DuplicateKey dk) {
			throw new DuplicateBlobException(
					"Attempt to add a duplicate blob with id " + td.getChsum());
		}
	}

	@Override
	public String getBlob(TypeData td) throws NoSuchBlobException {
		DBObject query = new BasicDBObject();
		query.put("_id", td.getChsum());
		GridFSDBFile out = gfs.findOne(query);
		if (out == null) {
			throw new NoSuchBlobException(
					"Attempt to retrieve non-existant blob with MD5 " + 
					td.getChsum());
		}
		try {
			return IOUtils.toString(out.getInputStream(), "UTF-8");
		} catch (IOException ioe) {
			//should never happen
			throw new RuntimeException("GridFS is apparently buggy"); 
		}
	}

	@Override
	public void removeBlob(TypeData td) {
		DBObject query = new BasicDBObject();
		query.put("_id", td.getChsum());
		gfs.remove(query);
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
