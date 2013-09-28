package us.kbase.typedobj.db.test;

import us.kbase.typedobj.exceptions.TypeStorageException;

public interface TypeStorageListener {
	public void onMethodStart(String method, Object[] params) throws TypeStorageException;
	public void onMethodEnd(String method, Object[] params, Object ret) throws TypeStorageException;
}
