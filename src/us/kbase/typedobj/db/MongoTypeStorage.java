package us.kbase.typedobj.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import us.kbase.typedobj.exceptions.TypeStorageException;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mongodb.DB;

public class MongoTypeStorage extends TypeStorage {
	private Jongo jdb;
	
	public static final String TABLE_MODULE_REQUEST = "module_request";
	public static final String TABLE_MODULE_VERSION = "module_version";
	public static final String TABLE_MODULE_OWNER = "module_owner";
	public static final String TABLE_MODULE_INFO_HISTORY = "module_info_history";
	public static final String TABLE_MODULE_SPEC_HISTORY = "module_spec_history";
	public static final String TABLE_MODULE_TYPE_SCHEMA = "module_type_schema";
	public static final String TABLE_MODULE_TYPE_PARSE = "module_type_parse";
	public static final String TABLE_MODULE_FUNC_PARSE = "module_func_parse";
	public static final String TABLE_FUNC_REFS = "func_refs";
	public static final String TABLE_TYPE_REFS = "type_refs";

	public static final int MAX_REQUESTS_BY_USER = 10;
	
	public MongoTypeStorage(DB db) {
		jdb = new Jongo(db);
	}
	
	@Override
	public void addRefs(Set<RefInfo> typeRefs, Set<RefInfo> funcRefs)
			throws TypeStorageException {
		if (typeRefs.size() > 0) {
			MongoCollection refs = jdb.getCollection(TABLE_TYPE_REFS);
			for (RefInfo ref : typeRefs) {
				if (ref.getDepModuleVersion() == 0)
					throw new TypeStorageException("Dependent type's module version was not initialized");
				refs.insert(ref);
			}
		}
		if (funcRefs.size() > 0) {
			MongoCollection refs = jdb.getCollection(TABLE_FUNC_REFS);
			for (RefInfo ref : funcRefs) {
				if (ref.getDepModuleVersion() == 0)
					throw new TypeStorageException("Dependent function's module version was not initialized");
				refs.insert(ref);
			}
		}
	}
	
	@Override
	public long getLastModuleVersion(String moduleName) throws TypeStorageException {
		Long ret = getLastModuleVersionOrNull(moduleName);
		if (ret == null)
			throw new TypeStorageException("Module " + moduleName + " is not registered");
		return ret;
	}

	/*private void printModuleInfo(String moduleName, Long ret) throws TypeStorageException {
		List<Long> versions = getAllModuleVersions(moduleName);
		System.out.println("MongoTypeStorage: all module [" + moduleName + "] versions: " + versions);
		try {
			String infoText = new ObjectMapper().writeValueAsString(getModuleInfoRecord(moduleName, ret));
			System.out.println("MongoTypeStorage: module [" + moduleName + "] info for version " +ret + ": " + infoText);
		} catch (Exception e) {
			throw new TypeStorageException(e);
		}
	}*/
	
	@Override
	public boolean checkModuleExist(String moduleName) throws TypeStorageException {
		return getLastModuleVersionOrNull(moduleName) != null;
	}
	
	private Long getLastModuleVersionOrNull(String moduleName) throws TypeStorageException {
		MongoCollection vers = jdb.getCollection(TABLE_MODULE_VERSION);
		ModuleVersion ret = vers.findOne("{moduleName:#}", moduleName).as(ModuleVersion.class);
		return ret == null ? null : ret.versionTime;
	}
	
	@Override
	public boolean checkModuleInfoRecordExist(String moduleName, long version) throws TypeStorageException {
		return getModuleInfoOrNull(moduleName, version) != null;
	}
	
	@Override
	public boolean checkModuleSpecRecordExist(String moduleName, long version) throws TypeStorageException {
		return getModuleSpecOrNull(moduleName, version) != null;
	}

	private ModuleInfo getModuleInfoOrNull(String moduleName, long version) {
		MongoCollection infos = jdb.getCollection(TABLE_MODULE_INFO_HISTORY);
		ModuleInfoHistory info = infos.findOne("{moduleName:#, versionTime:#}", moduleName, 
				version).as(ModuleInfoHistory.class);
		return info == null ? null : info.getInfo();
	}

	private String getModuleSpecOrNull(String moduleName, long version) {
		MongoCollection specs = jdb.getCollection(TABLE_MODULE_SPEC_HISTORY);
		ModuleSpec spec = specs.findOne("{moduleName:#, versionTime:#}", moduleName, version).as(ModuleSpec.class);
		return spec == null ? null : spec.document;
	}

	@Override
	public boolean checkTypeSchemaRecordExists(String moduleName,
			String typeName, String version) throws TypeStorageException {
		throw new TypeStorageException("Method is not supported yet");
	}
	
	@Override
	public List<String> getAllRegisteredModules() throws TypeStorageException {
		MongoCollection infos = jdb.getCollection(TABLE_MODULE_VERSION);
		return infos.distinct("moduleName").as(String.class);
	}
	
	@Override
	public String getFuncParseRecord(String moduleName, String funcName,
			String version) throws TypeStorageException {
		MongoCollection docs = jdb.getCollection(TABLE_MODULE_FUNC_PARSE);
		Map<String, Object> ret = docs.findOne("{moduleName:#,funcName:#,version:#}", 
				moduleName, funcName, version).as(Map.class);
		if (ret == null)
			throw new TypeStorageException("Function parse record was not found " +
					"for " + moduleName + "." + funcName + "." + version);
		return ret.get("document").toString();
	}
	
	@Override
	public Set<RefInfo> getFuncRefsByDep(String depModule, String depFunc,
			String version) throws TypeStorageException {
		MongoCollection refs = jdb.getCollection(TABLE_FUNC_REFS);
		return Sets.newTreeSet(refs.find("{depModule:#,depName:#,depVersion:#}",
				depModule, depFunc, version).as(RefInfo.class));
	}
	
	@Override
	public Set<RefInfo> getFuncRefsByRef(String refModule, String refType,
			String version) throws TypeStorageException {
		MongoCollection refs = jdb.getCollection(TABLE_FUNC_REFS);
		return Sets.newTreeSet(refs.find("{depModule:#,depName:#,depVersion:#}",
				refModule, refType, version).as(RefInfo.class));
	}
	
	@Override
	public ModuleInfo getModuleInfoRecord(String moduleName, long version)
			throws TypeStorageException {
		ModuleInfo ret = getModuleInfoOrNull(moduleName, version);
		if (ret == null)
			throw new TypeStorageException("Info-record was not found for module: " + moduleName);
		return ret;
	}
	
	@Override
	public String getModuleSpecRecord(String moduleName, long version)
			throws TypeStorageException {
		String ret = getModuleSpecOrNull(moduleName, version);
		if (ret == null)
			throw new TypeStorageException("Spec-record was not found for module: " + moduleName);
		return ret;
	}
	
	@Override
	public List<Long> getAllModuleVersions(String moduleName) throws TypeStorageException {
		MongoCollection infos = jdb.getCollection(TABLE_MODULE_INFO_HISTORY);
		ArrayList<Map> data = Lists.newArrayList(infos.find("{moduleName:#}", moduleName).projection(
				"{versionTime:1}").as(Map.class));
		List<Long> ret = new ArrayList<Long>();
		for (Map<?,?> item : data) {
			Object value = item.get("versionTime");
			if (value == null || !(value instanceof Long))
				throw new TypeStorageException("Value is wrong: " + value);
			ret.add((Long)value);
		}
		return ret;
	}
	
	@Override
	public long generateNewModuleVersion(String moduleName) throws TypeStorageException {
		List<Long> data = getAllModuleVersions(moduleName);
		long ret = System.currentTimeMillis();
		for (long value : data)
			if (ret <= value)
				ret = value + 1;
		return ret;
	}
	
	@Override
	public String getTypeParseRecord(String moduleName, String typeName,
			String version) throws TypeStorageException {
		MongoCollection docs = jdb.getCollection(TABLE_MODULE_TYPE_PARSE);
		Map<String, Object> ret = docs.findOne("{moduleName:#,typeName:#,version:#}", 
				moduleName, typeName, version).as(Map.class);
		if (ret == null)
			throw new TypeStorageException("Type parse record was not found " +
					"for " + moduleName + "." + typeName + "." + version);
		return ret.get("document").toString();
	}
	
	@Override
	public Set<RefInfo> getTypeRefsByDep(String depModule, String depType,
			String version) throws TypeStorageException {
		MongoCollection refs = jdb.getCollection(TABLE_TYPE_REFS);
		return Sets.newTreeSet(refs.find("{depModule:#,depName:#,depVersion:#}",
				depModule, depType, version).as(RefInfo.class));
	}
	
	@Override
	public Set<RefInfo> getTypeRefsByRef(String refModule, String refType,
			String version) throws TypeStorageException {
		MongoCollection refs = jdb.getCollection(TABLE_TYPE_REFS);
		return Sets.newTreeSet(refs.find("{refModule:#,refName:#,refVersion:#}",
				refModule, refType, version).as(RefInfo.class));
	}
	
	@Override
	public String getTypeSchemaRecord(String moduleName, String typeName,
			String version) throws TypeStorageException {
		MongoCollection docs = jdb.getCollection(TABLE_MODULE_TYPE_SCHEMA);
		Map<String, Object> ret = docs.findOne("{moduleName:#,typeName:#,version:#}", 
				moduleName, typeName, version).as(Map.class);
		if (ret == null)
			throw new TypeStorageException("Type schema record was not found " +
					"for " + moduleName + "." + typeName + "." + version);
		return ret.get("document").toString();
	}
	
	@Override
	public void removeAllFuncRecords(String moduleName, String funcName)
			throws TypeStorageException {
		throw new TypeStorageException("Method is not supported yet");
	}
	
	@Override
	public void removeAllRefs() throws TypeStorageException {
		jdb.getCollection(TABLE_TYPE_REFS).remove();
		jdb.getCollection(TABLE_FUNC_REFS).remove();
	}
	
	@Override
	public void removeAllTypeRecords(String moduleName, String typeName)
			throws TypeStorageException {
		throw new TypeStorageException("Method is not supported yet");
	}
	
	@Override
	public void removeFuncRefs(String depModule, String depFunc, String version)
			throws TypeStorageException {
		jdb.getCollection(TABLE_FUNC_REFS).remove("{depModule:#,depName:#,depVersion:#}", 
				depModule, depFunc, version);
	}
	
	@Override
	public void removeModule(String moduleName) throws TypeStorageException {
		jdb.getCollection(TABLE_MODULE_TYPE_PARSE).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_FUNC_PARSE).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_TYPE_REFS).remove("{depModule:#}", moduleName);
		jdb.getCollection(TABLE_TYPE_REFS).remove("{refModule:#}", moduleName);
		jdb.getCollection(TABLE_FUNC_REFS).remove("{depModule:#}", moduleName);
		jdb.getCollection(TABLE_FUNC_REFS).remove("{refModule:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_TYPE_SCHEMA).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_TYPE_PARSE).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_FUNC_PARSE).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_SPEC_HISTORY).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_INFO_HISTORY).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_REQUEST).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_OWNER).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_SPEC_HISTORY).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_INFO_HISTORY).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_VERSION).remove("{moduleName:#}", moduleName);
	}
	
	@Override
	public boolean removeTypeRecordsForVersion(String moduleName,
			String typeName, String version) throws TypeStorageException {
		throw new TypeStorageException("Method is not supported yet");
	}
	
	@Override
	public void removeTypeRefs(String depModule, String depType, String version)
			throws TypeStorageException {
		jdb.getCollection(TABLE_TYPE_REFS).remove("{depModule:#,depName:#,depVersion:#}", depModule, depType, version);
	}
		
	@Override
	public void writeFuncParseRecord(String moduleName, String funcName,
			String version, long moduleVersion, String parseText) throws TypeStorageException {
		MongoCollection recs = jdb.getCollection(TABLE_MODULE_FUNC_PARSE);
		recs.remove("{moduleName:#,funcName:#,version:#}", moduleName, funcName, version);
		FuncRecord rec = new FuncRecord();
		rec.setModuleName(moduleName);
		rec.setFuncName(funcName);
		rec.setVersion(version);
		rec.setModuleVersion(moduleVersion);
		rec.setDocument(parseText);
		recs.save(rec);
	}
	
	private void writeModuleVersion(String moduleName, long version) throws TypeStorageException {
		MongoCollection vers = jdb.getCollection(TABLE_MODULE_VERSION);
		// TODO: it should be rewritten without deletion
		vers.remove("{moduleName:#}", moduleName);
		ModuleVersion ver = new ModuleVersion();
		ver.setModuleName(moduleName);
		ver.setVersionTime(version);
		vers.insert(ver);
	}

	@Override
	public void writeModuleRecords(String moduleName, ModuleInfo info, 
			String specDocument, long version) throws TypeStorageException {
		writeModuleVersion(moduleName, version);
		writeModuleInfo(moduleName, info, version);
		MongoCollection specs = jdb.getCollection(TABLE_MODULE_SPEC_HISTORY);
		ModuleSpec spec = new ModuleSpec();
		spec.setModuleName(moduleName);
		spec.setDocument(specDocument);
		spec.setVersionTime(version);
		specs.insert(spec);
	}

	@Override
	public void initModuleInfoRecord(String moduleName, ModuleInfo info) throws TypeStorageException {
		long version = generateNewModuleVersion(moduleName);
		writeModuleVersion(moduleName, version);
		writeModuleInfo(moduleName, info, version);
	}
	
	private void writeModuleInfo(String moduleName, ModuleInfo info, long version) throws TypeStorageException {
		MongoCollection infos = jdb.getCollection(TABLE_MODULE_INFO_HISTORY);
		ModuleInfoHistory ih = new ModuleInfoHistory();
		ih.setModuleName(moduleName);
		ih.setInfo(info);
		ih.setVersionTime(version);
		infos.insert(ih);
	}
	
	@Override
	public void writeTypeParseRecord(String moduleName, String typeName,
			String version, long moduleVersion, String document) throws TypeStorageException {
		MongoCollection recs = jdb.getCollection(TABLE_MODULE_TYPE_PARSE);
		recs.remove("{moduleName:#,typeName:#,version:#}", moduleName, typeName, version);
		TypeRecord rec = new TypeRecord();
		rec.setModuleName(moduleName);
		rec.setTypeName(typeName);
		rec.setVersion(version);
		rec.setModuleVersion(moduleVersion);
		rec.setDocument(document);
		recs.save(rec);
	}
	
	@Override
	public void writeTypeSchemaRecord(String moduleName, String typeName,
			String version, long moduleVersion, String document) throws TypeStorageException {
		MongoCollection recs = jdb.getCollection(TABLE_MODULE_TYPE_SCHEMA);
		recs.remove("{moduleName:#,typeName:#,version:#}", moduleName, typeName, version);
		TypeRecord rec = new TypeRecord();
		rec.setModuleName(moduleName);
		rec.setTypeName(typeName);
		rec.setVersion(version);
		rec.setModuleVersion(moduleVersion);
		rec.setDocument(document);
		recs.insert(rec);
	}
	
	@Override
	public void addNewModuleRegistrationRequest(String moduleName, String userId)
			throws TypeStorageException {
		MongoCollection recs = jdb.getCollection(TABLE_MODULE_REQUEST);
		int prevCount = Lists.newArrayList(recs.find("{moduleName:#,ownerUserId:#}", 
				moduleName, userId).as(OwnerInfo.class)).size();
		if (prevCount >= MAX_REQUESTS_BY_USER)
			throw new TypeStorageException("User " + userId + " has maximal count " +
					"of requests: " + MAX_REQUESTS_BY_USER);
		OwnerInfo rec = new OwnerInfo();
		rec.setOwnerUserId(userId);
		rec.setWithChangeOwnersPrivilege(true);
		rec.setModuleName(moduleName);
		recs.insert(rec);
	}
	
	@Override
	public void addOwnerToModule(String moduleName, String userId,
			boolean withChangeOwnersPrivilege) throws TypeStorageException {
		MongoCollection recs = jdb.getCollection(TABLE_MODULE_OWNER);
		recs.remove("{moduleName:#,ownerUserId:#}", moduleName, userId);
		OwnerInfo rec = new OwnerInfo();
		rec.setOwnerUserId(userId);
		rec.setWithChangeOwnersPrivilege(withChangeOwnersPrivilege);
		rec.setModuleName(moduleName);
		recs.insert(rec);
	}
	
	@Override
	public List<OwnerInfo> getNewModuleRegistrationRequests()
			throws TypeStorageException {
		MongoCollection recs = jdb.getCollection(TABLE_MODULE_REQUEST);
		return Lists.newArrayList(recs.find().as(OwnerInfo.class));
	}
	
	@Override
	public Map<String, OwnerInfo> getOwnersForModule(String moduleName)
			throws TypeStorageException {
		MongoCollection recs = jdb.getCollection(TABLE_MODULE_OWNER);
		List<OwnerInfo> owners = Lists.newArrayList(recs.find().as(OwnerInfo.class));
		Map<String, OwnerInfo> ret = new HashMap<String, OwnerInfo>();
		for (OwnerInfo oi : owners)
			ret.put(oi.getOwnerUserId(), oi);
		return ret;
	}
	
	@Override
	public void removeNewModuleRegistrationRequest(String moduleName,
			String userId) throws TypeStorageException {
		MongoCollection recs = jdb.getCollection(TABLE_MODULE_REQUEST);
		recs.remove("{moduleName:#,ownerUserId:#}", moduleName, userId);
	}
	
	@Override
	public void removeOwnerFromModule(String moduleName, String userId)
			throws TypeStorageException {
		MongoCollection recs = jdb.getCollection(TABLE_MODULE_OWNER);
		recs.remove("{moduleName:#,ownerUserId:#}", moduleName, userId);
	}
	
	public static class ModuleSpec {
		private String moduleName;
		private String document;
		private long versionTime;
		
		public String getModuleName() {
			return moduleName;
		}
		
		public void setModuleName(String moduleName) {
			this.moduleName = moduleName;
		}
		
		public String getDocument() {
			return document;
		}
		
		public void setDocument(String document) {
			this.document = document;
		}
		
		public long getVersionTime() {
			return versionTime;
		}
		
		public void setVersionTime(long versionTime) {
			this.versionTime = versionTime;
		}
	}
	
	public static class ModuleInfoHistory {
		private String moduleName;
		private ModuleInfo info;
		private long versionTime;
		
		public String getModuleName() {
			return moduleName;
		}
		
		public void setModuleName(String moduleName) {
			this.moduleName = moduleName;
		}
		
		public ModuleInfo getInfo() {
			return info;
		}
		
		public void setInfo(ModuleInfo info) {
			this.info = info;
		}
		
		public long getVersionTime() {
			return versionTime;
		}
		
		public void setVersionTime(long versionTime) {
			this.versionTime = versionTime;
		}
	}

	public static class ModuleVersion {
		private String moduleName;
		private long versionTime;
		
		public String getModuleName() {
			return moduleName;
		}
		
		public void setModuleName(String moduleName) {
			this.moduleName = moduleName;
		}
		
		public long getVersionTime() {
			return versionTime;
		}
		
		public void setVersionTime(long versionTime) {
			this.versionTime = versionTime;
		}
	}
	
	public class TypeRecord {
		private String moduleName; 
		private String typeName;
		private String version; 
		private long moduleVersion;
		private String document;
		
		public TypeRecord() {
		}
		
		public String getModuleName() {
			return moduleName;
		}
		
		public void setModuleName(String moduleName) {
			this.moduleName = moduleName;
		}
		
		public String getTypeName() {
			return typeName;
		}
		
		public void setTypeName(String typeName) {
			this.typeName = typeName;
		}
		
		public String getVersion() {
			return version;
		}
		
		public void setVersion(String version) {
			this.version = version;
		}
		
		public long getModuleVersion() {
			return moduleVersion;
		}
		
		public void setModuleVersion(long moduleVersion) {
			this.moduleVersion = moduleVersion;
		}
		
		public String getDocument() {
			return document;
		}
		
		public void setDocument(String document) {
			this.document = document;
		}
	}

	public class FuncRecord {
		private String moduleName; 
		private String funcName;
		private String version; 
		private long moduleVersion;
		private String document;
		
		public String getModuleName() {
			return moduleName;
		}
		
		public void setModuleName(String moduleName) {
			this.moduleName = moduleName;
		}

		public String getFuncName() {
			return funcName;
		}
		
		public void setFuncName(String funcName) {
			this.funcName = funcName;
		}
		
		public String getVersion() {
			return version;
		}
		
		public void setVersion(String version) {
			this.version = version;
		}
		
		public long getModuleVersion() {
			return moduleVersion;
		}
		
		public void setModuleVersion(long moduleVersion) {
			this.moduleVersion = moduleVersion;
		}
		
		public String getDocument() {
			return document;
		}
		
		public void setDocument(String document) {
			this.document = document;
		}
	}
}
