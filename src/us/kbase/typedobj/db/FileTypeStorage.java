package us.kbase.typedobj.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import us.kbase.typedobj.exceptions.TypeStorageException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FileTypeStorage extends TypeStorage {

	private ObjectMapper mapper;

	private File dbFolder;
	private Set<RefInfo> typeRefs = null;
	private Set<RefInfo> funcRefs = null;
	private List<OwnerInfo> requests = null;
	private List<OwnerInfo> owners = null;

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
		return new File(dbFolder, "requests.json");
	}

	private File getOwnersFile() {
		return new File(dbFolder, "owners.json");
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
		saveOwners(owners, getRequestFile());
	}
	
	@Override
	public List<OwnerInfo> getNewModuleRegistrationRequests()
			throws TypeStorageException {
		return copy(requests);
	}
	
	@Override
	public Map<String, OwnerInfo> getOwnersForModule(String moduleName)
			throws TypeStorageException {
		Map<String, OwnerInfo> ret = new HashMap<String, OwnerInfo>();
		for (OwnerInfo oi : owners) {
			if (oi.getModuleName().equals(moduleName))
				ret.put(oi.getOwnerUserId(), copy(oi));
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
		return new File(dbFolder, "typerefs.json");
	}

	private File getFuncRefFile() {
		return new File(dbFolder, "funcrefs.json");
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
	public void removeTypeRefs(String depModule, String depType, String version) throws TypeStorageException {
		Set<RefInfo> ret = new TreeSet<RefInfo>();
		for (RefInfo ri : typeRefs) {
			if (ri.getDepModule().equals(depModule) && ri.getDepName().equals(depType) &&
					ri.getDepVersion().equals(version))
				continue;
			ret.add(ri);
		}
		typeRefs = ret;
		saveRefs(typeRefs, getTypeRefFile());
	}

	@Override
	public void removeFuncRefs(String depModule, String depFunc, String version) throws TypeStorageException {
		Set<RefInfo> ret = new TreeSet<RefInfo>();
		for (RefInfo ri : funcRefs) {
			if (ri.getDepModule().equals(depModule) && ri.getDepName().equals(depFunc) &&
					ri.getDepVersion().equals(version))
				continue;
			ret.add(ri);
		}
		funcRefs = ret;
		saveRefs(funcRefs, getFuncRefFile());
	}

	@Override
	public void addRefs(Set<RefInfo> typeRefs, Set<RefInfo> funcRefs) throws TypeStorageException {
		this.typeRefs.addAll(copy(typeRefs));
		this.funcRefs.addAll(copy(funcRefs));
		saveRefs(this.typeRefs, getTypeRefFile());
		saveRefs(this.funcRefs, getFuncRefFile());
	}

	@Override
	public void removeAllRefs() {
		getTypeRefFile().delete();
		getFuncRefFile().delete();
		typeRefs = new TreeSet<RefInfo>();
		funcRefs = new TreeSet<RefInfo>();
	}
	
	private File getModuleDir(String moduleName) {
		return new File(dbFolder.getAbsolutePath(), moduleName);
	}

	private String getTypeFilePrefix(String moduleName, String typeName) {
		return new File(getModuleDir(moduleName), "type." + typeName).getAbsolutePath();
	}

	private File getTypeSchemaFile(String moduleName, String typeName, String version) {
		return new File(getTypeFilePrefix(moduleName, typeName) + "." + version + ".json");
	}

	private File getTypeParseFile(String moduleName, String typeName, String version) {
		return new File(getTypeFilePrefix(moduleName, typeName) + "." + version + ".prs");
	}
	
	@Override
	public boolean checkModuleInfoRecordExist(String moduleName) {
		return getModuleInfoFile(moduleName, null).exists();
	}

	@Override
	public boolean checkModuleSpecRecordExist(String moduleName) {
		return getModuleSpecFile(moduleName, null).exists();
	}
	
	@Override
	public boolean checkTypeSchemaRecordExists(String moduleName,
			String typeName, String version) {
		File schemaDocument = getTypeSchemaFile(moduleName, typeName, version);
		return schemaDocument != null && schemaDocument.canRead();
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
	public String getTypeParseRecord(String moduleName, String typeName, String version) throws TypeStorageException {
		File f = getTypeParseFile(moduleName, typeName, version);
		if (f.exists() && f.isFile() && f.canRead()) {
			return readFile(f);
		}
		return null;
	}
	
	@Override
	public List<String> getAllRegisteredModules() {
		List<String> ret = new ArrayList<String>();
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
			String version, String document) throws TypeStorageException {
		writeFile(getTypeSchemaFile(moduleName, typeName, version), document);
	}
	
	@Override
	public void writeTypeParseRecord(String moduleName, String typeName,
			String version, String document) throws TypeStorageException {
		writeFile(getTypeParseFile(moduleName, typeName, version), document);
	}
	
	@Override
	public void removeAllTypeRecords(String moduleName, String typeName) {
		for (File f : getModuleDir(moduleName).listFiles())
			if (f.isFile() && f.getName().startsWith("type." + typeName))
				f.delete();
	}
	
	@Override
	public void removeAllFuncRecords(String moduleName, String funcName) {
		for (File f : getModuleDir(moduleName).listFiles())
			if (f.isFile() && f.getName().startsWith("func." + funcName))
				f.delete();
	}
	
	private File getModuleSpecFile(String moduleName, Long time) {
		String newSuffix = time == null ? "" : ("." + time);
		return new File(getModuleDir(moduleName), "module" + newSuffix + ".spec");
	}

	private File getModuleInfoFile(String moduleName, Long time) {
		String newSuffix = time == null ? "" : ("." + time);
		return new File(getModuleDir(moduleName), "module" + newSuffix + ".info");
	}

	@Override
	public void writeModuleSpecRecord(String moduleName,
			String specDocument) throws TypeStorageException {
		writeFile(getModuleSpecFile(moduleName, null), specDocument);
	}
	
	
	@Override
	public void writeModuleSpecRecordBackup(String moduleName, String specDocument, long backupTime) throws TypeStorageException {
		writeFile(getModuleSpecFile(moduleName, backupTime), specDocument);
	}
	
	@Override
	public void writeModuleInfoRecord(String moduleName, ModuleInfo info) throws TypeStorageException {
		String infoText;
		try {
			infoText = mapper.writeValueAsString(info);
		} catch (JsonProcessingException e) {
			throw new TypeStorageException(e);
		}
		writeFile(getModuleInfoFile(moduleName, null), infoText);
	}
	
	@Override
	public void writeModuleInfoRecordBackup(String moduleName, ModuleInfo info, long backupTime) throws TypeStorageException {
		String infoText;
		try {
			infoText = mapper.writeValueAsString(info);
		} catch (JsonProcessingException e) {
			throw new TypeStorageException(e);
		}
		writeFile(getModuleInfoFile(moduleName, backupTime), infoText);
	}
	
	@Override
	public ModuleInfo getModuleInfoRecord(String moduleName) throws TypeStorageException {
		String infoText = readFile(getModuleInfoFile(moduleName, null));
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
	public String getModuleSpecRecord(String moduleName) throws TypeStorageException {
		return readFile(getModuleSpecFile(moduleName, null));
	}
		
	private File getFuncParseFile(String moduleName, String typeName, String version) {
		return new File(new File(getModuleDir(moduleName), "func." + typeName).getAbsolutePath() + "." + version + ".prs");
	}

	@Override
	public void writeFuncParseRecord(String moduleName, String funcName,
			String version, String parseText) throws TypeStorageException {
		writeFile(getFuncParseFile(moduleName, funcName, version), parseText);
	}
	
	@Override
	public String getFuncParseRecord(String moduleName, String typeName, String version) throws TypeStorageException {
		File f = getFuncParseFile(moduleName, typeName, version);
		if (f.exists() && f.isFile() && f.canRead()) {
			return readFile(f);
		}
		return null;
	}
	
	@Override
	public boolean removeTypeRecordsForVersion(String moduleName,
			String typeName, String version) {
		File f1 = getTypeSchemaFile(moduleName, typeName, version);
		if (!f1.exists())
			return false;
		File f2 = getTypeParseFile(moduleName, typeName, version);
		f1.delete();
		f2.delete();
		return true;
	}
	
	@Override
	public long getStorageCurrentTime() {
		return System.currentTimeMillis();
	}
}
