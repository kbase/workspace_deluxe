package us.kbase.typedobj.db;

import java.util.List;
import java.util.Set;

public abstract class TypeStorage {

	public abstract boolean checkModuleExist(String moduleName);

	public abstract boolean checkModuleRecordsExist(String moduleName);

	public abstract boolean checkTypeSchemaRecordExists(String moduleName, String typeName, String version);

	public abstract String getTypeSchemaRecord(String moduleName, String typeName, String version);

	public abstract String getTypeParseRecord(String moduleName, String typeName, String version);
	
	public abstract String getModuleSpecRecord(String moduleName);

	public abstract String getModuleInfoRecord(String moduleName);
	
	public abstract String getFuncParseRecord(String moduleName, String typeName, String version);

	public abstract List<String> getAllRegisteredModules();

	public abstract Set<RefInfo> getTypeRefsByDep(String depModule, String depType, String version);

	public abstract Set<RefInfo> getTypeRefsByRef(String refModule, String refType, String version);

	public abstract Set<RefInfo> getFuncRefsByDep(String depModule, String depFunc, String version);

	public abstract Set<RefInfo> getFuncRefsByRef(String refModule, String refType, String version);

	///////////////////////////////////// CHANGES //////////////////////////////////////////
	
	public abstract void createModule(String moduleName);

	public abstract void writeTypeSchemaRecord(String moduleName, String typeName, String version, String document);

	public abstract void writeTypeParseRecord(String moduleName, String typeName, String version, String document);

	public abstract void removeAllTypeRecords(String moduleName, String typeName);

	public abstract void removeAllFuncRecords(String moduleName, String funcName);

	public abstract void writeModuleSpecRecordBackup(String moduleName, String specDocument, long backupTime);

	public abstract void writeModuleSpecRecord(String moduleName, String specDocument);

	public abstract void writeModuleInfoRecordBackup(String moduleName, String infoText, long backupTime);

	public abstract void writeModuleInfoRecord(String moduleName, String infoText);

	public abstract boolean removeTypeRecordsForVersion(String moduleName, String typeName, String version);

	public abstract void writeFuncParseRecord(String moduleName, String funcName, String version,
			String parseText);

	public abstract void removeModule(String moduleName);

	public abstract void removeTypeRefs(String depModule, String depType, String version);

	public abstract void removeFuncRefs(String depModule, String depFunc, String version);

	public abstract void addRefs(Set<RefInfo> typeRefs, Set<RefInfo> funcRefs);

	////////////////////////////////////// TESTING ///////////////////////////////////////////
	
	public abstract void removeAllRefs();
	
	public abstract long getStorageCurrentTime();
}