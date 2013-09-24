package us.kbase.typedobj.db;

import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.typedobj.exceptions.TypeStorageException;

public abstract class TypeStorage {

	public abstract boolean checkModuleInfoRecordExist(String moduleName) throws TypeStorageException;

	public abstract boolean checkModuleSpecRecordExist(String moduleName) throws TypeStorageException;

	public abstract boolean checkTypeSchemaRecordExists(String moduleName, String typeName, String version) throws TypeStorageException;

	public abstract String getTypeSchemaRecord(String moduleName, String typeName, String version) throws TypeStorageException;

	public abstract String getTypeParseRecord(String moduleName, String typeName, String version) throws TypeStorageException;
	
	public abstract String getModuleSpecRecord(String moduleName) throws TypeStorageException;

	public abstract ModuleInfo getModuleInfoRecord(String moduleName) throws TypeStorageException;
	
	public abstract String getFuncParseRecord(String moduleName, String typeName, String version) throws TypeStorageException;

	public abstract List<String> getAllRegisteredModules() throws TypeStorageException;

	public abstract Set<RefInfo> getTypeRefsByDep(String depModule, String depType, String version) throws TypeStorageException;

	public abstract Set<RefInfo> getTypeRefsByRef(String refModule, String refType, String version) throws TypeStorageException;

	public abstract Set<RefInfo> getFuncRefsByDep(String depModule, String depFunc, String version) throws TypeStorageException;

	public abstract Set<RefInfo> getFuncRefsByRef(String refModule, String refType, String version) throws TypeStorageException;

	public abstract List<OwnerInfo> getNewModuleRegistrationRequests() throws TypeStorageException;
	
	public abstract Map<String, OwnerInfo> getOwnersForModule(String moduleName) throws TypeStorageException;
	
	///////////////////////////////////// CHANGES //////////////////////////////////////////
	
	public abstract void writeTypeSchemaRecord(String moduleName, String typeName, String version, String document) throws TypeStorageException;

	public abstract void writeTypeParseRecord(String moduleName, String typeName, String version, String document) throws TypeStorageException;

	public abstract void removeAllTypeRecords(String moduleName, String typeName) throws TypeStorageException;

	public abstract void removeAllFuncRecords(String moduleName, String funcName) throws TypeStorageException;

	public abstract void writeModuleSpecRecordBackup(String moduleName, String specDocument, long backupTime) throws TypeStorageException;

	public abstract void writeModuleSpecRecord(String moduleName, String specDocument) throws TypeStorageException;

	public abstract void writeModuleInfoRecordBackup(String moduleName, ModuleInfo infoText, long backupTime) throws TypeStorageException;

	public abstract void writeModuleInfoRecord(String moduleName, ModuleInfo info) throws TypeStorageException;

	public abstract boolean removeTypeRecordsForVersion(String moduleName, String typeName, String version) throws TypeStorageException;

	public abstract void writeFuncParseRecord(String moduleName, String funcName, String version,
			String parseText) throws TypeStorageException;

	public abstract void removeModule(String moduleName) throws TypeStorageException;

	public abstract void removeTypeRefs(String depModule, String depType, String version) throws TypeStorageException;

	public abstract void removeFuncRefs(String depModule, String depFunc, String version) throws TypeStorageException;

	public abstract void addRefs(Set<RefInfo> typeRefs, Set<RefInfo> funcRefs) throws TypeStorageException;

	public abstract void addNewModuleRegistrationRequest(String moduleName, String userId) throws TypeStorageException;
	
	public abstract void removeNewModuleRegistrationRequest(String moduleName, String userId) throws TypeStorageException;
	
	public abstract void addOwnerToModule(String moduleName, String userId, boolean withChangeOwnersPrivilege) throws TypeStorageException;

	public abstract void removeOwnerFromModule(String moduleName, String userId) throws TypeStorageException;

	////////////////////////////////////// TESTING ///////////////////////////////////////////
	
	public abstract void removeAllRefs() throws TypeStorageException;
	
	public abstract long getStorageCurrentTime() throws TypeStorageException;
}