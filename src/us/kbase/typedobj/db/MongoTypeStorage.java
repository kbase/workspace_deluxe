package us.kbase.typedobj.db;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import us.kbase.typedobj.exceptions.TypeStorageException;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mongodb.DB;

public class MongoTypeStorage implements TypeStorage {
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

	public static final int MAX_REQUESTS_BY_USER = 30;
	
	public MongoTypeStorage(DB db) {
		jdb = new Jongo(db);
		ensureIndeces();
	}
	
	private void ensureIndeces() {
		MongoCollection reqs = jdb.getCollection(TABLE_MODULE_REQUEST);
		reqs.ensureIndex("{moduleName:1}", "{unique:false}");
		reqs.ensureIndex("{ownerUserId:1}", "{unique:false}");
		MongoCollection vers = jdb.getCollection(TABLE_MODULE_VERSION);
		vers.ensureIndex("{moduleName:1}", "{unique:true}");
		MongoCollection owns = jdb.getCollection(TABLE_MODULE_OWNER);
		owns.ensureIndex("{moduleName:1,ownerUserId:1}", "{unique:true}");
		MongoCollection infos = jdb.getCollection(TABLE_MODULE_INFO_HISTORY);
		infos.ensureIndex("{moduleName:1,versionTime:1}", "{unique:true}");
		MongoCollection specs = jdb.getCollection(TABLE_MODULE_SPEC_HISTORY);
		specs.ensureIndex("{moduleName:1,versionTime:1}", "{unique:true}");
		MongoCollection tschs = jdb.getCollection(TABLE_MODULE_TYPE_SCHEMA);
		tschs.ensureIndex("{moduleName:1,typeName:1,version:1}", "{unique:true}");
		tschs.ensureIndex("{moduleName:1,moduleVersion:1}", "{unique:false}");
		MongoCollection tprs = jdb.getCollection(TABLE_MODULE_TYPE_PARSE);
		tprs.ensureIndex("{moduleName:1,typeName:1,version:1}", "{unique:true}");
		tprs.ensureIndex("{moduleName:1,moduleVersion:1}", "{unique:false}");
		MongoCollection fprs = jdb.getCollection(TABLE_MODULE_FUNC_PARSE);
		fprs.ensureIndex("{moduleName:1,funcName:1,version:1}", "{unique:true}");
		fprs.ensureIndex("{moduleName:1,moduleVersion:1}", "{unique:false}");
		MongoCollection frefs = jdb.getCollection(TABLE_FUNC_REFS);
		frefs.ensureIndex("{depModule:1,depName:1,depVersion:1}", "{unique:false}");
		frefs.ensureIndex("{refModule:1,refName:1,refVersion:1}", "{unique:false}");
		frefs.ensureIndex("{depModule:1,depModuleVersion:1}", "{unique:false}");
		MongoCollection trefs = jdb.getCollection(TABLE_TYPE_REFS);
		trefs.ensureIndex("{depModule:1,depName:1,depVersion:1}", "{unique:false}");
		trefs.ensureIndex("{refModule:1,refName:1,refVersion:1}", "{unique:false}");
		trefs.ensureIndex("{depModule:1,depModuleVersion:1}", "{unique:false}");
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
		ModuleInfo info = infos.findOne("{moduleName:#, versionTime:#}", moduleName, 
				version).as(ModuleInfo.class);
		return info;
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
	
	@SuppressWarnings("unchecked")
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
		return Sets.newTreeSet(refs.find("{refModule:#,refName:#,refVersion:#}",
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
		return getProjection(infos, "{moduleName:#}", "versionTime", Long.class, moduleName);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected <T> List<T> getProjection(MongoCollection infos,
			String whereCondition, String selectField, Class<T> type, Object... params)
			throws TypeStorageException {
		ArrayList<Map> data = Lists.newArrayList(infos.find(whereCondition, params).projection(
				"{" + selectField + ":1}").as(Map.class));
		List<T> ret = new ArrayList<T>();
		for (Map<?,?> item : data) {
			Object value = item.get(selectField);
			if (value == null || !(type.isInstance(value)))
				throw new TypeStorageException("Value is wrong: " + value);
			ret.add((T)value);
		}
		return ret;
	}
	
	@Override
	public List<String> getAllTypeVersions(String moduleName, String typeName) throws TypeStorageException {
		MongoCollection schemas = jdb.getCollection(TABLE_MODULE_TYPE_SCHEMA);
		return getProjection(schemas, "{moduleName:#,typeName:#}", "version", String.class, 
				moduleName, typeName);
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
	
	@SuppressWarnings("unchecked")
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
	
	@SuppressWarnings("unchecked")
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
	public void removeAllData() throws TypeStorageException {
		jdb.getCollection(TABLE_TYPE_REFS).remove();
		jdb.getCollection(TABLE_FUNC_REFS).remove();
		jdb.getCollection(TABLE_MODULE_TYPE_PARSE).remove();
		jdb.getCollection(TABLE_MODULE_TYPE_SCHEMA).remove();
		jdb.getCollection(TABLE_MODULE_FUNC_PARSE).remove();
		jdb.getCollection(TABLE_MODULE_REQUEST).remove();
		jdb.getCollection(TABLE_MODULE_OWNER).remove();
		jdb.getCollection(TABLE_MODULE_SPEC_HISTORY).remove();
		jdb.getCollection(TABLE_MODULE_INFO_HISTORY).remove();
		jdb.getCollection(TABLE_MODULE_VERSION).remove();
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
		jdb.getCollection(TABLE_TYPE_REFS).remove("{depModule:#}", moduleName);
		jdb.getCollection(TABLE_TYPE_REFS).remove("{refModule:#}", moduleName);
		jdb.getCollection(TABLE_FUNC_REFS).remove("{depModule:#}", moduleName);
		jdb.getCollection(TABLE_FUNC_REFS).remove("{refModule:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_TYPE_SCHEMA).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_TYPE_PARSE).remove("{moduleName:#}", moduleName);
		jdb.getCollection(TABLE_MODULE_FUNC_PARSE).remove("{moduleName:#}", moduleName);
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
		if (vers.findOne("{moduleName:#}", moduleName).as(ModuleVersion.class) == null) {
			ModuleVersion ver = new ModuleVersion();
			ver.setModuleName(moduleName);
			ver.setVersionTime(version);
			vers.insert(ver);
		} else {
			vers.update("{moduleName:#}", moduleName).with("{$set: {versionTime: #}}", version);
		}
	}

	@Override
	public void writeModuleRecords(ModuleInfo info,  String specDocument, long version) 
			throws TypeStorageException {
		writeModuleVersion(info.getModuleName(), version);
		writeModuleInfo(info, version);
		MongoCollection specs = jdb.getCollection(TABLE_MODULE_SPEC_HISTORY);
		ModuleSpec spec = new ModuleSpec();
		spec.setModuleName(info.getModuleName());
		spec.setDocument(specDocument);
		spec.setVersionTime(version);
		specs.insert(spec);
	}

	@Override
	public void initModuleInfoRecord(ModuleInfo info) throws TypeStorageException {
		long version = generateNewModuleVersion(info.getModuleName());
		writeModuleVersion(info.getModuleName(), version);
		writeModuleInfo(info, version);
	}
	
	private void writeModuleInfo(ModuleInfo info, long version) throws TypeStorageException {
		MongoCollection infos = jdb.getCollection(TABLE_MODULE_INFO_HISTORY);
		info.setVersionTime(version);
		infos.insert(info);
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
		int prevCount = Lists.newArrayList(recs.find("{ownerUserId:#}", 
				userId).as(OwnerInfo.class)).size();
		if (prevCount >= MAX_REQUESTS_BY_USER)
			throw new TypeStorageException("User " + userId + " has maximal count " +
					"of requests: " + MAX_REQUESTS_BY_USER);
		if (recs.findOne("{moduleName:#}", moduleName).as(OwnerInfo.class) != null)
			throw new TypeStorageException("Registration of module " + moduleName + " was already requested");
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
		List<OwnerInfo> owners = Lists.newArrayList(recs.find("{moduleName:#}", moduleName).as(OwnerInfo.class));
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
	
	@Override
	public Map<String, Long> listObjects() throws TypeStorageException {
		String[] tables = {
			TABLE_FUNC_REFS,
			TABLE_MODULE_FUNC_PARSE,
			TABLE_MODULE_INFO_HISTORY,
			TABLE_MODULE_OWNER,
			TABLE_MODULE_REQUEST,
			TABLE_MODULE_SPEC_HISTORY,
			TABLE_MODULE_TYPE_PARSE,
			TABLE_MODULE_TYPE_SCHEMA,
			TABLE_MODULE_VERSION,
			TABLE_TYPE_REFS
		};
		Map<String, Long> ret = new TreeMap<String, Long>();
		for (String table : tables) {
			MongoCollection recs = jdb.getCollection(table);
			List<Object> rows = Lists.newArrayList(recs.find().as(Object.class));
			ret.put(table, (long)rows.size());
		}
		return ret;
	}
	
	@Override
	public void removeModuleVersionAndSwitchIfNotCurrent(String moduleName,
			long versionToDelete, long versionToSwitchTo)
			throws TypeStorageException {
		jdb.getCollection(TABLE_TYPE_REFS).remove("{depModule:#,depModuleVersion:#}", moduleName, versionToDelete);
		jdb.getCollection(TABLE_FUNC_REFS).remove("{depModule:#,depModuleVersion:#}", moduleName, versionToDelete);
		jdb.getCollection(TABLE_MODULE_TYPE_SCHEMA).remove("{moduleName:#,moduleVersion:#}", moduleName, versionToDelete);
		jdb.getCollection(TABLE_MODULE_TYPE_PARSE).remove("{moduleName:#,moduleVersion:#}", moduleName, versionToDelete);
		jdb.getCollection(TABLE_MODULE_FUNC_PARSE).remove("{moduleName:#,moduleVersion:#}", moduleName, versionToDelete);
		jdb.getCollection(TABLE_MODULE_SPEC_HISTORY).remove("{moduleName:#,versionTime:#}", moduleName, versionToDelete);
		jdb.getCollection(TABLE_MODULE_INFO_HISTORY).remove("{moduleName:#,versionTime:#}", moduleName, versionToDelete);
		if (versionToSwitchTo != getLastModuleVersion(moduleName))
			writeModuleVersion(moduleName, versionToSwitchTo);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void copyModuleVersion(String moduleName, long version, long versionCopy) throws TypeStorageException {
		long versionCopyD = versionCopy - version;
		Set<RefInfo> typeRefs = Sets.newTreeSet(jdb.getCollection(TABLE_TYPE_REFS).find("{depModule:#,depModuleVersion:#}",
				moduleName, version).as(RefInfo.class));
		for (RefInfo ri : typeRefs) {
			ri.setDepModuleVersion(versionCopy);
			ri.setDepVersion(versionCopyD + ri.getDepVersion());
			if (ri.getRefModule().equals(moduleName))
				ri.setRefVersion(versionCopyD + ri.getRefVersion());				
		}
		Set<RefInfo> funcRefs = Sets.newTreeSet(jdb.getCollection(TABLE_FUNC_REFS).find("{depModule:#,depModuleVersion:#}",
				moduleName, version).as(RefInfo.class));
		for (RefInfo ri : funcRefs) {
			ri.setDepModuleVersion(versionCopy);
			ri.setDepVersion(versionCopyD + ri.getDepVersion());
			if (ri.getRefModule().equals(moduleName))
				ri.setRefVersion(versionCopyD + ri.getRefVersion());				
		}
		addRefs(typeRefs, funcRefs);
		////////////////////////////////////// Type schemas
		MongoCollection schemas = jdb.getCollection(TABLE_MODULE_TYPE_SCHEMA);
		Map[] schArr = load(schemas, "{moduleName:#,moduleVersion:#}", Map.class, moduleName, version);
		for (Map tr : schArr) {
			tr.put("moduleVersion", versionCopy);
			tr.put("version", "" + versionCopyD + tr.get("version"));
			tr.remove("_id");
		}
		schemas.insert((Object[])schArr);
		////////////////////////////////////// Type schemas
		MongoCollection typePrs = jdb.getCollection(TABLE_MODULE_TYPE_PARSE);
		Map[] typePrsArr = load(typePrs, "{moduleName:#,moduleVersion:#}", Map.class, moduleName, version);
		for (Map tr : typePrsArr) {
			tr.put("moduleVersion", versionCopy);
			tr.put("version", "" + versionCopyD + tr.get("version"));
			tr.remove("_id");
		}
		typePrs.insert((Object[])typePrsArr);
		////////////////////////////////////// Type schemas
		MongoCollection funcPrs = jdb.getCollection(TABLE_MODULE_FUNC_PARSE);
		Map[] funcPrsArr = load(funcPrs, "{moduleName:#,moduleVersion:#}", Map.class, moduleName, version);
		for (Map tr : funcPrsArr) {
			tr.put("moduleVersion", versionCopy);
			tr.put("version", "" + versionCopyD + tr.get("version"));
			tr.remove("_id");
		}
		funcPrs.insert((Object[])funcPrsArr);
		String spec = getModuleSpecRecord(moduleName, version);
		ModuleInfo info = getModuleInfoRecord(moduleName, version);
		for (String type : info.getTypes().keySet()) {
			info.getTypes().get(type).setTypeVersion(versionCopyD + info.getTypes().get(type).getTypeVersion());
			info.getTypes().get(type).setReleaseVersion(versionCopyD + info.getTypes().get(type).getReleaseVersion());
		}
		for (String func : info.getFuncs().keySet()) {
			info.getFuncs().get(func).setFuncVersion(versionCopyD + info.getFuncs().get(func).getFuncVersion());
			info.getFuncs().get(func).setReleaseVersion(versionCopyD + info.getFuncs().get(func).getReleaseVersion());
		}
		info.setVersionTime(versionCopy);
		writeModuleRecords(info, spec, versionCopy);
	}

	@SuppressWarnings("unchecked")
	private <T> T[] load(MongoCollection col, String query, Class<T> type, Object... params) {
		List<T> list = Lists.newArrayList(col.find(query, params).as(type));
		T[] ret = (T[])Array.newInstance(type, list.size());
		return list.toArray(ret);
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
