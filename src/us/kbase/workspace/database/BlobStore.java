package us.kbase.workspace.database;

public interface BlobStore {
	
	public void saveBlob(TypeData td);
	
	public String getBlob(TypeData td);
	
	public String getExternalIdentifier(TypeData td); 
}
