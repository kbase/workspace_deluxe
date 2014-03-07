package us.kbase.typedobj.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.codec.digest.DigestUtils;

import us.kbase.typedobj.exceptions.TypeStorageException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FileTypeStorage implements TypeStorage {

	private ObjectMapper mapper;

	private File dbFolder;
	private Set<RefInfo> typeRefs = null;
	private Set<RefInfo> funcRefs = null;
	private List<OwnerInfo> requests = null;
	private List<OwnerInfo> owners = null;
	
	private static final String TYPEREFS_ROOT_FILE = "typerefs.json";
	private static final String FUNCREFS_ROOT_FILE = "funcrefs.json";
	private static final String REQUESTS_ROOT_FILE = "requests.json";
	private static final String OWNERS_ROOT_FILE = "owners.json";
	private static final Set<String> rootFileNames = Collections.unmodifiableSet(
			new HashSet<String>(Arrays.asList(TYPEREFS_ROOT_FILE, FUNCREFS_ROOT_FILE,
					REQUESTS_ROOT_FILE, OWNERS_ROOT_FILE)));

	/**
	 * Set up a new DB pointing to the specified db folder.  The contents
	 * should have a separate folder for each module named with the module
	 * name, and in each folder a set of .json files with json schema
	 * documents named the same as the type names.
	 * @param dbFolderPath
	 * @throws TypeStorageException 
	 */
	public FileTypeStorage(String dbFolderPath) throws FileNotFoundException, TypeStorageException {
		// initialize the base class with a null json schema factory
		super();
		dbFolder = new File(dbFolderPath);
		if(!dbFolder.isDirectory()) {
			throw new FileNotFoundException("Cannot create SimpleTypeDefinitionDB from given db location:"+dbFolder.getPath());
		}
		mapper = new ObjectMapper();
		typeRefs = loadRefs(getTypeRefFile());
		funcRefs = loadRefs(getFuncRefFile());
		requests = loadOwnerInfos(getRequestFile());
		owners = loadOwnerInfos(getOwnersFile());
	}
	
	private File getRequestFile() {
		return new File(dbFolder, REQUESTS_ROOT_FILE);
	}

	private File getOwnersFile() {
		return new File(dbFolder, OWNERS_ROOT_FILE);
	}

	private List<OwnerInfo> loadOwnerInfos(File f) throws TypeStorageException {
		try {
			if (!f.exists())
				return new ArrayList<OwnerInfo>();
			return mapper.readValue(f, new TypeReference<List<OwnerInfo>>() {});
		} catch (Exception e) {
			throw new TypeStorageException(e);
		}
	}
	
	private static List<OwnerInfo> copy(List<OwnerInfo> input) {
		List<OwnerInfo> ret = new ArrayList<OwnerInfo>();
		for (OwnerInfo o1 : input) {
			ret.add(copy(o1));
		}
		return ret;
	}

	protected static OwnerInfo copy(OwnerInfo o1) {
		OwnerInfo o2 = new OwnerInfo();
		o2.setOwnerUserId(o1.getOwnerUserId());
		o2.setWithChangeOwnersPrivilege(o1.isWithChangeOwnersPrivilege());
		o2.setModuleName(o1.getModuleName());
		return o2;
	}

	private void saveOwners(List<OwnerInfo> owners, File f) throws TypeStorageException {
		try {
			mapper.writeValue(f, owners);
		} catch (Exception ex) {
			throw new TypeStorageException(ex);
		}
	}

	@Override
	public void addNewModuleRegistrationRequest(String moduleName, String userId)
			throws TypeStorageException {
		for (OwnerInfo oi : requests) {
			if (oi.getModuleName().equals(moduleName))
				throw new TypeStorageException("Registration of module " + moduleName + " was already requested");
		}
		if (checkModuleExist(moduleName))
			throw new TypeStorageException("Module " + moduleName + " was already registered");
		OwnerInfo oi = new OwnerInfo();
		oi.setOwnerUserId(userId);
		oi.setWithChangeOwnersPrivilege(true);
		oi.setModuleName(moduleName);
		requests.add(oi);
		saveOwners(requests, getRequestFile());
	}
	
	@Override
	public void addOwnerToModule(String moduleName, String userId,
			boolean withChangeOwnersPrivilege) throws TypeStorageException {
		OwnerInfo oi = new OwnerInfo();
		oi.setOwnerUserId(userId);
		oi.setWithChangeOwnersPrivilege(withChangeOwnersPrivilege);
		oi.setModuleName(moduleName);
		owners.add(oi);
		saveOwners(owners, getOwnersFile());
	}
	
	@Override
	public List<OwnerInfo> getNewModuleRegistrationRequests()
			throws TypeStorageException {
		return copy(requests);
	}
	
	@Override
	public String getOwnerForNewModuleRegistrationRequest(String moduleName)
			throws TypeStorageException {
		for (OwnerInfo oi : requests)
			if (oi.getModuleName().equals(moduleName))
				return oi.getOwnerUserId();
		throw new TypeStorageException("There is no request for module " + moduleName);
	}
	
	@Override
	public Map<String, OwnerInfo> getOwnersForModule(String moduleName)
			throws TypeStorageException {
		Map<String, OwnerInfo> ret = new TreeMap<String, OwnerInfo>();
		for (OwnerInfo oi : owners) {
			if (oi.getModuleName().equals(moduleName))
				ret.put(oi.getOwnerUserId(), copy(oi));
		}
		return ret;
	}
	
	@Override
	public Map<String, OwnerInfo> getModulesForOwner(String userId)
			throws TypeStorageException {
		Map<String, OwnerInfo> ret = new TreeMap<String, OwnerInfo>();
		for (OwnerInfo oi : owners) {
			if (oi.getOwnerUserId().equals(userId))
				ret.put(oi.getModuleName(), copy(oi));
		}
		return ret;
	}

	@Override
	public void removeNewModuleRegistrationRequest(String moduleName,
			String userId) throws TypeStorageException {
		for (Iterator<OwnerInfo> it = requests.iterator(); it.hasNext();) {
			if (it.next().getOwnerUserId().equals(userId)) {
				it.remove();
				break;
			}
		}
		saveOwners(requests, getRequestFile());
	}
	
	@Override
	public void removeOwnerFromModule(String moduleName, String userId)
			throws TypeStorageException {
		for (Iterator<OwnerInfo> it = owners.iterator(); it.hasNext();) {
			if (it.next().getOwnerUserId().equals(userId)) {
				it.remove();
				break;
			}
		}
		saveOwners(owners, getOwnersFile());
	}
	
	private File getTypeRefFile() {
		return new File(dbFolder, TYPEREFS_ROOT_FILE);
	}

	private File getFuncRefFile() {
		return new File(dbFolder, FUNCREFS_ROOT_FILE);
	}

	private Set<RefInfo> loadRefs(File f) throws TypeStorageException {
		try {
			if (!f.exists())
				return new TreeSet<RefInfo>();
			List<RefInfo> ret = mapper.readValue(f, new TypeReference<List<RefInfo>>() {});
			return new TreeSet<RefInfo>(ret);
		} catch (Exception e) {
			throw new TypeStorageException(e);
		}
	}
	
	private static Set<RefInfo> copy(Set<RefInfo> input) {
		Set<RefInfo> ret = new TreeSet<RefInfo>();
		for (RefInfo r1 : input) {
			RefInfo r2 = new RefInfo();
			r2.setDepModule(r1.getDepModule());
			r2.setDepName(r1.getDepName());
			r2.setDepVersion(r1.getDepVersion());
			r2.setDepModuleVersion(r1.getDepModuleVersion());
			r2.setRefModule(r1.getRefModule());
			r2.setRefName(r1.getRefName());
			r2.setRefVersion(r1.getRefVersion());
			ret.add(r2);
		}
		return ret;
	}
	
	@Override
	public Set<RefInfo> getTypeRefsByDep(String depModule, String depType, String version) {
		Set<RefInfo> ret = new TreeSet<RefInfo>();
		for (RefInfo ri : typeRefs) {
			if (ri.getDepModule().equals(depModule) && ri.getDepName().equals(depType) &&
					ri.getDepVersion().equals(version))
				ret.add(ri);
		}
		return copy(ret);
	}

	@Override
	public Set<RefInfo> getTypeRefsByRef(String refModule, String refType, String version) {
		Set<RefInfo> ret = new TreeSet<RefInfo>();
		for (RefInfo ri : typeRefs) {
			if (ri.getRefModule().equals(refModule) && ri.getRefName().equals(refType) &&
					ri.getRefVersion().equals(version))
				ret.add(ri);
		}
		return copy(ret);
	}

	@Override
	public Set<RefInfo> getFuncRefsByDep(String depModule, String depFunc, String version) {
		Set<RefInfo> ret = new TreeSet<RefInfo>();
		for (RefInfo ri : funcRefs) {
			if (ri.getDepModule().equals(depModule) && ri.getDepName().equals(depFunc) &&
					ri.getDepVersion().equals(version))
				ret.add(ri);
		}
		return copy(ret);
	}
	
	@Override
	public Set<RefInfo> getFuncRefsByRef(String refModule, String refType, String version) {
		Set<RefInfo> ret = new TreeSet<RefInfo>();
		for (RefInfo ri : funcRefs) {
			if (ri.getRefModule().equals(refModule) && ri.getRefName().equals(refType) &&
					ri.getRefVersion().equals(version))
				ret.add(ri);
		}
		return copy(ret);
	}

	private void saveRefs(Set<RefInfo> refs, File f) throws TypeStorageException {
		try {
			mapper.writeValue(f, refs);
		} catch (Exception ex) {
			throw new TypeStorageException(ex);
		}
	}
	
	@Override
	public void addRefs(Set<RefInfo> typeRefs, Set<RefInfo> funcRefs) throws TypeStorageException {
		this.typeRefs.addAll(copy(typeRefs));
		this.funcRefs.addAll(copy(funcRefs));
		saveRefs(this.typeRefs, getTypeRefFile());
		saveRefs(this.funcRefs, getFuncRefFile());
	}

	@Override
	public void removeAllData() {
		for (String moduleName : getAllRegisteredModules())
			removeModule(moduleName);
		getTypeRefFile().delete();
		getFuncRefFile().delete();
		getRequestFile().delete();
		getOwnersFile().delete();
		typeRefs = new TreeSet<RefInfo>();
		funcRefs = new TreeSet<RefInfo>();
		requests = new ArrayList<OwnerInfo>();
		owners = new ArrayList<OwnerInfo>();
	}
	
	private File getModuleDir(String moduleName) {
		return new File(dbFolder.getAbsolutePath(), moduleName);
	}

	private String getTypeFilePrefix(String moduleName, String typeName) {
		return new File(getModuleDir(moduleName), "type." + typeName).getAbsolutePath();
	}

	private File getTypeSchemaFile(String moduleName, String typeName, String version, long moduleVersion) {
		return new File(getTypeFilePrefix(moduleName, typeName) + "." + version + "-" + moduleVersion + ".json");
	}

	private List<File> getTypeSchemaFiles(String moduleName, String typeName, String version) throws TypeStorageException {
		return findFiles(moduleName, "type." + typeName + "." + version + "-", ".json");
	}

	private File getTypeSchemaFile(String moduleName, String typeName, String version) throws TypeStorageException {
		List<File> ret = getTypeSchemaFiles(moduleName, typeName, version);
		if (ret.isEmpty())
			throw new TypeStorageException("No type schema file was found for: " + moduleName + "." + typeName + "." + version);
		return ret.get(0);
	}

	@Override
	public Map<String, Boolean> getAllTypeVersions(String moduleName, String typeName) throws TypeStorageException {
		Map<String, Boolean> ret = new LinkedHashMap<String, Boolean>();
		for (Map.Entry<Long, Boolean> entry : getAllModuleVersions(moduleName).entrySet()) {
			long moduleVersion = entry.getKey();
			ModuleInfo info = getModuleInfoRecord(moduleName, moduleVersion);
			if (info.getTypes().containsKey(typeName)) {
				TypeInfo ti = info.getTypes().get(typeName);
				if (!ti.isSupported())
					continue;
				String typeVer = ti.getTypeVersion();
				boolean prevTypeRet = ret.containsKey(typeVer) ? ret.get(typeVer) : false;
				boolean newTypeRet = entry.getValue();
				ret.put(typeVer, prevTypeRet || newTypeRet);
			}
		}
		return ret;
	}
	
	@Override
	public Map<String, Boolean> getAllFuncVersions(String moduleName, String funcName) throws TypeStorageException {
		Map<String, Boolean> ret = new LinkedHashMap<String, Boolean>();
		for (Map.Entry<Long, Boolean> entry : getAllModuleVersions(moduleName).entrySet()) {
			long moduleVersion = entry.getKey();
			ModuleInfo info = getModuleInfoRecord(moduleName, moduleVersion);
			if (info.getFuncs().containsKey(funcName)) {
				String funcVer = info.getFuncs().get(funcName).getFuncVersion();
				boolean prevFuncRet = ret.containsKey(funcVer) ? ret.get(funcVer) : false;
				boolean newFuncRet = entry.getValue();
				ret.put(funcVer, prevFuncRet || newFuncRet);
			}
		}
		return ret;
	}
	
	private File getTypeParseFile(String moduleName, String typeName, String version, long moduleVersion) {
		return new File(getTypeFilePrefix(moduleName, typeName) + "." + version + "-" + moduleVersion + ".prs");
	}

	private List<File> getTypeParseFiles(String moduleName, String typeName, String version) throws TypeStorageException {
		return findFiles(moduleName, "type." + typeName + "." + version + "-", ".prs");
	}
	
	private File getTypeParseFile(String moduleName, String typeName, String version) throws TypeStorageException {
		List<File> ret = getTypeParseFiles(moduleName, typeName, version);
		if (ret.isEmpty())
			throw new TypeStorageException("No type parsing file was found for: " + moduleName + "." + typeName + "." + version);
		return ret.get(0);
	}

	@Override
	public boolean checkModuleInfoRecordExist(String moduleName, long version) {
		return getModuleInfoFile(moduleName, version).exists();
	}

	@Override
	public boolean checkModuleSpecRecordExist(String moduleName, long version) {
		return getModuleSpecFile(moduleName, version).exists();
	}
	
	@Override
	public boolean checkTypeSchemaRecordExists(String moduleName,
			String typeName, String version) throws TypeStorageException {
		try {
			File schemaDocument = getTypeSchemaFile(moduleName, typeName, version);
			return schemaDocument != null && schemaDocument.canRead();
		} catch (TypeStorageException ex) {
			return false;
		}
	}
	
	private String readFile(File file) throws TypeStorageException {
		try {
			StringBuilder ret = new StringBuilder();
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line;
			while ( (line=br.readLine()) != null ) {
				ret.append(line).append('\n');
			}
			br.close();
			return ret.toString();
		} catch (Exception ex) {
			throw new TypeStorageException(ex);
		}
	}

	@Override
	public String getTypeSchemaRecord(String moduleName, String typeName, String version) throws TypeStorageException {
		File f = getTypeSchemaFile(moduleName, typeName, version);
		if (f.exists() && f.isFile() && f.canRead()) {
			return readFile(f);
		}
		return null;
	}
	
	@Override
	public String getTypeMd5(String moduleName, String typeName, String version)
			throws TypeStorageException {
		String json = getTypeSchemaRecord(moduleName, typeName, version);
		if (json == null)
			throw new TypeStorageException("No json schema for type " + moduleName + "." + typeName + "." + version);
		return DigestUtils.md5Hex(json);
	}
	
	@Override
	public List<String> getTypeVersionsByMd5(String moduleName, String typeName,
			String md5) throws TypeStorageException {
		List<String> ret = new ArrayList<String>();
		for (String version : getAllTypeVersions(moduleName, typeName).keySet()) {
			String curMd5 = getTypeMd5(moduleName, typeName, version);
			if (curMd5.equals(md5))
				ret.add(version);
		}
		return ret;
	}

	
	@Override
	public String getTypeParseRecord(String moduleName, String typeName, String version) throws TypeStorageException {
		File f = getTypeParseFile(moduleName, typeName, version);
		if (f.exists() && f.isFile() && f.canRead()) {
			return readFile(f);
		}
		return null;
	}
	
	@Override
	public Set<String> getAllRegisteredModules(boolean withUnsupported) throws TypeStorageException {
		if (withUnsupported) {
			return getAllRegisteredModules();
		}
		Set<String> ret = new TreeSet<String>();
		for (String mod : getAllRegisteredModules())
			if (getModuleSupportedState(mod))
				ret.add(mod);
		return ret;
	}
	
	private Set<String> getAllRegisteredModules() {
		Set<String> ret = new TreeSet<String>();
		for (File sub : dbFolder.listFiles())
			if (sub.isDirectory())
				ret.add(sub.getName());
		return ret;
	}

	private void writeFile(File location, String document) throws TypeStorageException {
		try {
			if (!location.getParentFile().exists())
				location.getParentFile().mkdir();
			PrintWriter pw = new PrintWriter(location);
			pw.print(document);
			pw.close();
		} catch (Exception ex) {
			throw new TypeStorageException(ex);
		}
	}

	@Override
	public void writeTypeSchemaRecord(String moduleName, String typeName,
			String version, long moduleVersion, String document, String md5) throws TypeStorageException {
		for (File f : getTypeSchemaFiles(moduleName, typeName, version))
			f.delete();
		writeFile(getTypeSchemaFile(moduleName, typeName, version, moduleVersion), document);
	}
		
	@Override
	public void writeTypeParseRecord(String moduleName, String typeName,
			String version, long moduleVersion, String document) throws TypeStorageException {
		for (File f : getTypeParseFiles(moduleName, typeName, version))
			f.delete();
		writeFile(getTypeParseFile(moduleName, typeName, version, moduleVersion), document);
	}
	
	private File getModuleSpecFile(String moduleName, long time) {
		return new File(getModuleDir(moduleName), "module." + time + ".spec");
	}

	private File getModuleInfoFile(String moduleName, long time) {
		return new File(getModuleDir(moduleName), "module." + time + ".info");
	}	
	
	@Override
	public void writeModuleRecords(ModuleInfo info, String specDocument, long time) 
			throws TypeStorageException {
		info.setVersionTime(time);
		writeFile(getModuleSpecFile(info.getModuleName(), time), specDocument);
		writeModuleInfoRecord(info);
	}
	
	private void writeModuleInfoRecord(ModuleInfo info) throws TypeStorageException {
		String infoText;
		try {
			infoText = mapper.writeValueAsString(info);
		} catch (JsonProcessingException e) {
			throw new TypeStorageException(e);
		}
		writeFile(getModuleInfoFile(info.getModuleName(), info.getVersionTime()), infoText);
	}
	
	@Override
	public void initModuleInfoRecord(ModuleInfo info) throws TypeStorageException {
		long version = generateNewModuleVersion(info.getModuleName());
		info.setVersionTime(version);
		writeModuleInfoRecord(info);
	}
	
	@Override
	public ModuleInfo getModuleInfoRecord(String moduleName, long version) throws TypeStorageException {
		String infoText = readFile(getModuleInfoFile(moduleName, version));
		try {
			return mapper.readValue(infoText, ModuleInfo.class);
		} catch (Exception ex) {
			throw new TypeStorageException(ex);
		}
	}
	
	@Override
	public void removeModule(String moduleName) {
		File moduleDir = getModuleDir(moduleName);
		for (File f : moduleDir.listFiles())
			f.delete();
		moduleDir.delete();
	}

	@Override
	public String getModuleSpecRecord(String moduleName, long version) throws TypeStorageException {
		return readFile(getModuleSpecFile(moduleName, version));
	}
		
	private List<File> findFiles(String moduleName, String prefix, String suffix) {
		List<File> ret = new ArrayList<File>();
		for (File f : getModuleDir(moduleName).listFiles())
			if (f.isFile() && f.getName().startsWith(prefix) && f.getName().endsWith(suffix))
				ret.add(f);
		return ret;
	}
	
	private File getFuncParseFile(String moduleName, String funcName, String version, long moduleVersion) {
		return new File(new File(getModuleDir(moduleName), "func." + funcName).getAbsolutePath() + "." + version + "-" + moduleVersion + ".prs");
	}

	private List<File> getFuncParseFiles(String moduleName, String funcName, String version) throws TypeStorageException {
		return findFiles(moduleName, "func." + funcName + "." + version + "-", ".prs");
	}
	
	private File getFuncParseFile(String moduleName, String funcName, String version) throws TypeStorageException {
		List<File> ret = getFuncParseFiles(moduleName, funcName, version);
		if (ret.isEmpty())
			throw new TypeStorageException("No function parsing file was found for: " + moduleName + "." + funcName + "." + version);
		return ret.get(0);
	}

	@Override
	public void writeFuncParseRecord(String moduleName, String funcName,
			String version, long moduleVersion, String parseText) throws TypeStorageException {
		for (File f : getFuncParseFiles(moduleName, funcName, version))
			f.delete();
		writeFile(getFuncParseFile(moduleName, funcName, version, moduleVersion), parseText);
	}
	
	@Override
	public String getFuncParseRecord(String moduleName, String typeName, String version) throws TypeStorageException {
		File f = getFuncParseFile(moduleName, typeName, version);
		if (f.exists() && f.isFile() && f.canRead()) {
			return readFile(f);
		}
		return null;
	}
		
	private List<String> findFileMidParts(String moduleName, String prefix, String suffix) {
		List<String> ret = new ArrayList<String>();
		for (File f : findFiles(moduleName, prefix, suffix))
			if (f.getName().length() > prefix.length() + suffix.length() && 
					f.getName().startsWith(prefix) && f.getName().endsWith(suffix))
				ret.add(f.getName().substring(prefix.length(), f.getName().length() - suffix.length()));
		return ret;
	}
	
	@Override
	public TreeMap<Long, Boolean> getAllModuleVersions(String moduleName)
			throws TypeStorageException {
		Set<Long> ret = new TreeSet<Long>();
		for (String text : findFileMidParts(moduleName, "module.", ".info"))
			ret.add(Long.parseLong(text));
		for (String text : findFileMidParts(moduleName, "module.", ".spec"))
			ret.add(Long.parseLong(text));
		long releaseVer = getLastReleasedModuleVersion(moduleName);
		TreeMap<Long, Boolean> map = new TreeMap<Long, Boolean>();
		for (long ver : ret) {
			ModuleInfo info = getModuleInfoRecord(moduleName, ver);
			map.put(ver, ver <= releaseVer && info.isReleased());
		}
		return map;
	}
	
	private File getModuleReleaseVersionFile(String moduleName) {
		return new File(getModuleDir(moduleName), "module.release.version");
	}
	
	@Override
	public long getLastReleasedModuleVersion(String moduleName) throws TypeStorageException {
		File f = getModuleReleaseVersionFile(moduleName);
		if (!f.exists())
			throw new TypeStorageException("No version information for module: " + moduleName);
		String text = readFile(f);
		if (text.endsWith("\n"))
			text = text.substring(0, text.length() - 1);
		if (text.indexOf('\t') > 0)
			text = text.substring(0, text.indexOf('\t'));
		return Long.parseLong(text.trim());
	}
	
	@Override
	public void setModuleReleaseVersion(String moduleName, long version)
			throws TypeStorageException {
		ModuleInfo info = getModuleInfoRecord(moduleName, version);
		info.setReleased(true);
		writeModuleInfoRecord(info);
		boolean supported = getModuleSupportedState(moduleName);
		writeFile(getModuleReleaseVersionFile(moduleName), "" + version + "\t" + supported);
	}

	@Override
	public boolean getModuleSupportedState(String moduleName)
			throws TypeStorageException {
		File f = getModuleReleaseVersionFile(moduleName);
		if (!f.exists())
			return true;
		String text = readFile(f);
		if (text.endsWith("\n"))
			text = text.substring(0, text.length() - 1);
		if (text.indexOf('\t') > 0)
			text = text.substring(text.indexOf('\t') + 1);
		return Boolean.parseBoolean(text.trim());
	}
	
	@Override
	public void changeModuleSupportedState(String moduleName, boolean supported)
			throws TypeStorageException {
		long version = getLastReleasedModuleVersion(moduleName);
		writeFile(getModuleReleaseVersionFile(moduleName), "" + version + "\t" + supported);
	}
	
	@Override
	public long getLastModuleVersionWithUnreleased(String moduleName)
			throws TypeStorageException {
		TreeMap<Long, Boolean> ret = (TreeMap<Long, Boolean>)getAllModuleVersions(moduleName);
		if (ret.isEmpty())
			throw new TypeStorageException("No version information for module: " + moduleName);
		return ret.lastKey();
	}
	
	@Override
	public boolean checkModuleExist(String moduleName) throws TypeStorageException {
		if (!getModuleDir(moduleName).exists())
			return false;
		return !getAllModuleVersions(moduleName).isEmpty();
	}
	
	@Override
	public long generateNewModuleVersion(String moduleName) throws TypeStorageException {
		long ret = System.currentTimeMillis();
		if (checkModuleExist(moduleName)) {
			long lastVersion = getLastModuleVersionWithUnreleased(moduleName);
			if (ret <= lastVersion)
				ret = lastVersion + 1;
		}
		return ret;
	}

	@Override
	public Map<String, Long> listObjects() throws TypeStorageException {
		
		Map<String, Long> ret = new TreeMap<String, Long>();
		for (File f1 : dbFolder.listFiles()) {
			if (f1.isFile()) {
				if (!rootFileNames.contains(f1.getName()))
					continue;
				try {
					List<?> list = mapper.readValue(f1, List.class);
					ret.put(f1.getName(), (long)list.size());
				} catch (Exception e) {
					throw new TypeStorageException(e);
				}
			} else {
				for (File f2 : f1.listFiles()) {
					ret.put(f1.getName() + "/" + f2.getName(), f2.length());
				}
			}
		}
		return ret;
	}
	
	@Override
	public void removeModuleVersionAndSwitchIfNotCurrent(String moduleName, 
			long versionToDelete, long versionToSwitchTo) throws TypeStorageException {
		Set<RefInfo> typeRefs2 = new TreeSet<RefInfo>();
		for (RefInfo ri : typeRefs) {
			if (ri.getDepModule().equals(moduleName) && ri.getDepModuleVersion() == versionToDelete)
				continue;
			typeRefs2.add(ri);
		}
		typeRefs = typeRefs2;
		saveRefs(typeRefs, getTypeRefFile());
		Set<RefInfo> funcRefs2 = new TreeSet<RefInfo>();
		for (RefInfo ri : funcRefs) {
			if (ri.getDepModule().equals(moduleName) && ri.getDepModuleVersion() == versionToDelete)
				continue;
			funcRefs2.add(ri);
		}
		funcRefs = funcRefs2;
		saveRefs(funcRefs, getFuncRefFile());
		for (File f : findFiles(moduleName, "type.", "-" + versionToDelete + ".json"))
			f.delete();
		for (File f : findFiles(moduleName, "type.", "-" + versionToDelete + ".prs"))
			f.delete();
		for (File f : findFiles(moduleName, "func.", "-" + versionToDelete + ".prs"))
			f.delete();
		File spec = getModuleSpecFile(moduleName, versionToDelete);
		if (spec.exists())
			spec.delete();
		File info = getModuleInfoFile(moduleName, versionToDelete);
		if (info.exists())
			info.delete();
		if (versionToSwitchTo != getLastModuleVersionWithUnreleased(moduleName))
			throw new TypeStorageException("Last module version should be: " + versionToSwitchTo);
	}
	
	@Override
	public Map<Long, Boolean> getModuleVersionsForTypeVersion(String moduleName,
			String typeName, String typeVersion) throws TypeStorageException {
		Map<Long, Boolean> ret = new LinkedHashMap<Long, Boolean>();
		for (Map.Entry<Long, Boolean> entry : getAllModuleVersions(moduleName).entrySet()) {
			long moduleVersion = entry.getKey();
			ModuleInfo info = getModuleInfoRecord(moduleName, moduleVersion);
			TypeInfo ti = info.getTypes().get(typeName);
			if (ti != null && ti.isSupported() && ti.getTypeVersion().equals(typeVersion))
				ret.put(moduleVersion, entry.getValue());
		}
		return ret;
	}
	
	@Override
	public Map<Long, Boolean> getModuleVersionsForFuncVersion(String moduleName, 
			String funcName, String funcVersion) throws TypeStorageException {
		Map<Long, Boolean> ret = new LinkedHashMap<Long, Boolean>();
		for (Map.Entry<Long, Boolean> entry : getAllModuleVersions(moduleName).entrySet()) {
			long moduleVersion = entry.getKey();
			ModuleInfo info = getModuleInfoRecord(moduleName, moduleVersion);
			if (info.getFuncs().containsKey(funcName) && info.getFuncs().get(funcName).getFuncVersion().equals(funcVersion))
				ret.put(moduleVersion, entry.getValue());
		}
		return ret;
	}
}
