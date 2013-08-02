package us.kbase.workspace.database;

import com.mongodb.DB;

public class GridFSBackend implements BlobStore {
	
	public GridFSBackend(DB mongodb) {
		//TODO
	}

	@Override
	public void saveBlob(TypeData td) {
		// TODO Auto-generated method stub
	}

	@Override
	public String getBlob(TypeData td) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getExternalIdentifier(TypeData td) {
		// TODO Auto-generated method stub
		return null;
	}

}
