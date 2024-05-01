package us.kbase.test.typedobj.db;

import java.util.List;

import us.kbase.typedobj.db.TypeStorage;

public interface TestTypeStorage extends TypeStorage {
	public void addTypeStorageListener(TypeStorageListener lst);
	public List<TypeStorageListener> getTypeStorageListeners();
	public void removeTypeStorageListener(TypeStorageListener lst);
	public void removeAllTypeStorageListeners();
	public TypeStorage getInnerStorage();
}
