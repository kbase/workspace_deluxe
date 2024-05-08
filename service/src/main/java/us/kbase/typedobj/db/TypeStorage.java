package us.kbase.typedobj.db;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import us.kbase.typedobj.exceptions.TypeStorageException;

public interface TypeStorage {

	public boolean checkModuleExist(String moduleName) throws TypeStorageException;

	public long getLastReleasedModuleVersion(String moduleName) throws TypeStorageException;

	public long getLastModuleVersionWithUnreleased(String moduleName) throws TypeStorageException;

	public TreeMap<Long, Boolean> getAllModuleVersions(String moduleName) throws TypeStorageException;

	public long generateNewModuleVersion(String moduleName) throws TypeStorageException;

	public boolean checkModuleInfoRecordExist(String moduleName, long version) throws TypeStorageException;

	public boolean checkModuleSpecRecordExist(String moduleName, long version) throws TypeStorageException;

	public String getModuleSpecRecord(String moduleName, long version) throws TypeStorageException;

	public ModuleInfo getModuleInfoRecord(String moduleName, long version) throws TypeStorageException;

	public Set<String> getAllRegisteredModules(boolean withUnsupported) throws TypeStorageException;

	public List<OwnerInfo> getNewModuleRegistrationRequests() throws TypeStorageException;

	public String getOwnerForNewModuleRegistrationRequest(String moduleName) throws TypeStorageException;

	public Map<String, OwnerInfo> getOwnersForModule(String moduleName) throws TypeStorageException;

	public boolean checkTypeSchemaRecordExists(String moduleName, String typeName, String version) throws TypeStorageException;

	public String getTypeSchemaRecord(String moduleName, String typeName, String version) throws TypeStorageException;

	public String getTypeMd5(String moduleName, String typeName, String version) throws TypeStorageException;

	public List<String> getTypeVersionsByMd5(String moduleName, String typeName, String md5) throws TypeStorageException;

	public String getTypeParseRecord(String moduleName, String typeName, String version) throws TypeStorageException;

	public Set<RefInfo> getTypeRefsByDep(String depModule, String depType, String version) throws TypeStorageException;

	public Set<RefInfo> getTypeRefsByRef(String refModule, String refType, String version) throws TypeStorageException;

	public Map<String, Boolean> getAllTypeVersions(String moduleName, String typeName) throws TypeStorageException;

	public Map<String, Boolean> getAllFuncVersions(String moduleName, String funcName) throws TypeStorageException;

	public String getFuncParseRecord(String moduleName, String typeName, String version) throws TypeStorageException;

	public Set<RefInfo> getFuncRefsByDep(String depModule, String depFunc, String version) throws TypeStorageException;

	public Set<RefInfo> getFuncRefsByRef(String refModule, String refType, String version) throws TypeStorageException;
	
	public Map<String, OwnerInfo> getModulesForOwner(String userId) throws TypeStorageException;

	public Map<Long, Boolean> getModuleVersionsForTypeVersion(String moduleName, String typeName, String typeVersion) throws TypeStorageException;

	public Map<Long, Boolean> getModuleVersionsForFuncVersion(String moduleName, String funcName, String funcVersion) throws TypeStorageException;

	public boolean getModuleSupportedState(String moduleName) throws TypeStorageException;

	///////////////////////////////////// CHANGES //////////////////////////////////////////
	
	public void writeTypeSchemaRecord(String moduleName, String typeName, String version, long moduleVersion, String document, String md5) throws TypeStorageException;

	public void writeTypeParseRecord(String moduleName, String typeName, String version, long moduleVersion, String document) throws TypeStorageException;

	public void writeModuleRecords(ModuleInfo info, String specDocument, long version) throws TypeStorageException;

	public void initModuleInfoRecord(ModuleInfo info) throws TypeStorageException;

	public void writeFuncParseRecord(String moduleName, String funcName, String version, long moduleVersion,
			String parseText) throws TypeStorageException;

	public void addRefs(Set<RefInfo> typeRefs, Set<RefInfo> funcRefs) throws TypeStorageException;

	public void addNewModuleRegistrationRequest(String moduleName, String userId) throws TypeStorageException;
	
	public void removeNewModuleRegistrationRequest(String moduleName, String userId) throws TypeStorageException;
	
	public void addOwnerToModule(String moduleName, String userId, boolean withChangeOwnersPrivilege) throws TypeStorageException;

	public void removeOwnerFromModule(String moduleName, String userId) throws TypeStorageException;

	public void removeModuleVersionAndSwitchIfNotCurrent(String moduleName, long versionToDelete, long versionToSwitchTo) throws TypeStorageException;
	
	public void setModuleReleaseVersion(String moduleName, long version) throws TypeStorageException;
	
	public void changeModuleSupportedState(String moduleName, boolean supported) throws TypeStorageException;
	
	////////////////////////////////////// TESTING ///////////////////////////////////////////
	
	public void removeAllData() throws TypeStorageException;
	
	public Map<String, Long> listObjects() throws TypeStorageException;
}