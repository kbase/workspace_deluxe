package us.kbase.typedobj.db;

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
	public static final String TABLE_MODULE_INFO = "module_info";
	public static final String TABLE_MODULE_OWNER = "module_owner";
	public static final String TABLE_MODULE_SPEC = "module_spec";
	public static final String TABLE_MODULE_INFO_BAK = "module_info_bak";
	public static final String TABLE_MODULE_SPEC_BAK = "module_spec_bak";
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
			for (RefInfo ref : typeRefs)
				refs.insert(ref);
		}
		if (funcRefs.size() > 0) {
			MongoCollection refs = jdb.getCollection(TABLE_FUNC_REFS);
			for (RefInfo ref : funcRefs)
				refs.insert(ref);
		}
	}
	
	@Override
	public boolean checkModuleInfoRecordExist(String moduleName) throws TypeStorageException {
		return getModuleInfoOrNull(moduleName) != null;
	}
	
	@Override
	public boolean checkModuleSpecRecordExist(String moduleName) throws TypeStorageException {
		return getModuleSpecOrNull(moduleName) != null;
	}

	private ModuleInfo getModuleInfoOrNull(String moduleName) {
		MongoCollection infos = jdb.getCollection(TABLE_MODULE_INFO);
		ModuleInfo info = infos.findOne("{moduleName:#}", moduleName).as(ModuleInfo.class);
		return info;
	}

	private String getModuleSpecOrNull(String moduleName) {
		MongoCollection specs = jdb.getCollection(TABLE_MODULE_SPEC);
		ModuleSpec spec = specs.findOne("{moduleName:#}", moduleName).as(ModuleSpec.class);
		return spec == null ? null : spec.document;
	}

	@Override
	public boolean checkTypeSchemaRecordExists(String moduleName,
			String typeName, String version) throws TypeStorageException {
		throw new TypeStorageException("Method is not supported yet");
	}
	
	@Override
	public List<String> getAllRegisteredModules() throws TypeStorageException {
		MongoCollection infos = jdb.getCollection(TABLE_MODULE_INFO);
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
	public ModuleInfo getModuleInfoRecord(String moduleName)
			throws TypeStorageException {
		ModuleInfo ret = getModuleInfoOrNull(moduleName);
		if (ret == null)
			throw new TypeStorageException("Info-record was not found for module: " + moduleName);
		return ret;
	}
	
	@Override
	public String getModuleSpecRecord(String moduleName)
			throws TypeStorageException {
		String ret = getModuleSpecOrNull(moduleName);
		if (ret == null)
			throw new TypeStorageException("Spec-record was not found for module: " + moduleName);
		return ret;
	}
	
	@Override
	public long getStorageCurrentTime() throws TypeStorageException {
		return System.currentTimeMillis();
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
		jdb.getCollection(TABLE_MODULE_SPEC_BAK).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_INFO_BAK).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_SPEC).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_INFO).remove("{moduleName:#}", moduleName);
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
			String version, String parseText) throws TypeStorageException {
		MongoCollection recs = jdb.getCollection(TABLE_MODULE_FUNC_PARSE);
		recs.remove("{moduleName:#,funcName:#,version:#}", moduleName, funcName, version);
		FuncRecord rec = new FuncRecord();
		rec.setModuleName(moduleName);
		rec.setFuncName(funcName);
		rec.setVersion(version);
		rec.setDocument(parseText);
		recs.save(rec);
	}
	
	@Override
	public void writeModuleInfoRecord(String moduleName, ModuleInfo info)
			throws TypeStorageException {
		MongoCollection infos = jdb.getCollection(TABLE_MODULE_INFO);
		infos.remove("{moduleName:#}", moduleName);
		infos.insert(info);
	}
	
	@Override
	public void writeModuleInfoRecordBackup(String moduleName, ModuleInfo info,
			long backupTime) throws TypeStorageException {
		MongoCollection infoBaks = jdb.getCollection(TABLE_MODULE_INFO_BAK);
		ModuleInfoBak bak = new ModuleInfoBak();
		bak.setModuleName(moduleName);
		bak.setInfo(info);
		bak.setBackupTime(backupTime);
		infoBaks.insert(bak);
	}
	
	@Override
	public void writeModuleSpecRecord(String moduleName, String specDocument)
			throws TypeStorageException {
		MongoCollection specs = jdb.getCollection(TABLE_MODULE_SPEC);
		specs.remove("{moduleName:#}", moduleName);
		ModuleSpec spec = new ModuleSpec();
		spec.setModuleName(moduleName);
		spec.setDocument(specDocument);
		specs.insert(spec);
	}
	
	@Override
	public void writeModuleSpecRecordBackup(String moduleName,
			String specDocument, long backupTime) throws TypeStorageException {
		MongoCollection specBaks = jdb.getCollection(TABLE_MODULE_SPEC_BAK);
		ModuleSpec spec = new ModuleSpec();
		spec.setModuleName(moduleName);
		spec.setDocument(specDocument);
		spec.setBackupTime(backupTime);
		specBaks.insert(spec);
	}
	
	@Override
	public void writeTypeParseRecord(String moduleName, String typeName,
			String version, String document) throws TypeStorageException {
		MongoCollection recs = jdb.getCollection(TABLE_MODULE_TYPE_PARSE);
		recs.remove("{moduleName:#,typeName:#,version:#}", moduleName, typeName, version);
		TypeRecord rec = new TypeRecord();
		rec.setModuleName(moduleName);
		rec.setTypeName(typeName);
		rec.setVersion(version);
		rec.setDocument(document);
		recs.save(rec);
	}
	
	@Override
	public void writeTypeSchemaRecord(String moduleName, String typeName,
			String version, String document) throws TypeStorageException {
		MongoCollection recs = jdb.getCollection(TABLE_MODULE_TYPE_SCHEMA);
		recs.remove("{moduleName:#,typeName:#,version:#}", moduleName, typeName, version);
		TypeRecord rec = new TypeRecord();
		rec.setModuleName(moduleName);
		rec.setTypeName(typeName);
		rec.setVersion(version);
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
		private long backupTime;
		
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
		
		public long getBackupTime() {
			return backupTime;
		}
		
		public void setBackupTime(long backupTime) {
			this.backupTime = backupTime;
		}
	}
	
	public static class ModuleInfoBak {
		private String moduleName;
		private ModuleInfo info;
		private long backupTime;
		
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
		
		public long getBackupTime() {
			return backupTime;
		}
		
		public void setBackupTime(long backupTime) {
			this.backupTime = backupTime;
		}
	}
	
	public class TypeRecord {
		private String moduleName; 
		private String typeName;
		private String version; 
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
		
		public String getDocument() {
			return document;
		}
		
		public void setDocument(String document) {
			this.document = document;
		}
	}
}
