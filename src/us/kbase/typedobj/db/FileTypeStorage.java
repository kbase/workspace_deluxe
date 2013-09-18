package us.kbase.typedobj.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FileTypeStorage extends TypeStorage {

	private ObjectMapper mapper;

	private File dbFolder;
	private Set<RefInfo> typeRefs = null;
	private Set<RefInfo> funcRefs = null;

	/**
	 * Set up a new DB pointing to the specified db folder.  The contents
	 * should have a separate folder for each module named with the module
	 * name, and in each folder a set of .json files with json schema
	 * documents named the same as the type names.
	 * @param dbFolderPath
	 */
	public FileTypeStorage(String dbFolderPath) throws FileNotFoundException {
		// initialize the base class with a null json schema factory
		super();
		dbFolder = new File(dbFolderPath);
		if(!dbFolder.isDirectory()) {
			throw new FileNotFoundException("Cannot create SimpleTypeDefinitionDB from given db location:"+dbFolder.getPath());
		}
		mapper = new ObjectMapper();
		typeRefs = loadRefs(getTypeRefFile());
		funcRefs = loadRefs(getFuncRefFile());
	}
	
	private File getTypeRefFile() {
		return new File(dbFolder, "typerefs.json");
	}

	private File getFuncRefFile() {
		return new File(dbFolder, "funcrefs.json");
	}

	private Set<RefInfo> loadRefs(File f) {
		try {
			if (!f.exists())
				return new TreeSet<RefInfo>();
			List<RefInfo> ret = mapper.readValue(f, new TypeReference<List<RefInfo>>() {});
			return new TreeSet<RefInfo>(ret);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
	
	@Override
	public Set<RefInfo> getTypeRefsByDep(String depModule, String depType, String version) {
		Set<RefInfo> ret = new TreeSet<RefInfo>();
		for (RefInfo ri : typeRefs) {
			if (ri.getDepModule().equals(depModule) && ri.getDepName().equals(depType) &&
					ri.getDepVersion().equals(version))
				ret.add(ri);
		}
		return ret;
	}

	@Override
	public Set<RefInfo> getTypeRefsByRef(String refModule, String refType, String version) {
		Set<RefInfo> ret = new TreeSet<RefInfo>();
		for (RefInfo ri : typeRefs) {
			if (ri.getRefModule().equals(refModule) && ri.getRefName().equals(refType) &&
					ri.getRefVersion().equals(version))
				ret.add(ri);
		}
		return ret;
	}

	@Override
	public Set<RefInfo> getFuncRefsByDep(String depModule, String depFunc, String version) {
		Set<RefInfo> ret = new TreeSet<RefInfo>();
		for (RefInfo ri : funcRefs) {
			if (ri.getDepModule().equals(depModule) && ri.getDepName().equals(depFunc) &&
					ri.getDepVersion().equals(version))
				ret.add(ri);
		}
		return ret;
	}
	
	@Override
	public Set<RefInfo> getFuncRefsByRef(String refModule, String refType, String version) {
		Set<RefInfo> ret = new TreeSet<RefInfo>();
		for (RefInfo ri : funcRefs) {
			if (ri.getRefModule().equals(refModule) && ri.getRefName().equals(refType) &&
					ri.getRefVersion().equals(version))
				ret.add(ri);
		}
		return ret;
	}

	private void saveRefs(Set<RefInfo> refs, File f) {
		try {
			mapper.writeValue(f, refs);
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
	
	@Override
	public void removeTypeRefs(String depModule, String depType, String version) {
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
	public void removeFuncRefs(String depModule, String depFunc, String version) {
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
	public void addRefs(Set<RefInfo> typeRefs, Set<RefInfo> funcRefs) {
		this.typeRefs.addAll(typeRefs);
		this.funcRefs.addAll(funcRefs);
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
	public boolean checkModuleExist(String moduleName) {
		File dir = getModuleDir(moduleName);
		return dir.exists() && dir.isDirectory();
	}
	
	@Override
	public boolean checkModuleRecordsExist(String moduleName) {
		return getModuleInfoFile(moduleName, null).exists() && 
				getModuleSpecFile(moduleName, null).exists();
	}
	
	@Override
	public boolean checkTypeSchemaRecordExists(String moduleName,
			String typeName, String version) {
		File schemaDocument = getTypeSchemaFile(moduleName, typeName, version);
		return schemaDocument != null && schemaDocument.canRead();
	}
	
	private String readFile(File file) throws IOException {
		StringBuilder ret = new StringBuilder();
		BufferedReader br = new BufferedReader(new FileReader(file));
		String line;
		while ( (line=br.readLine()) != null ) {
			ret.append(line).append('\n');
		}
		br.close();
		return ret.toString();
	}

	@Override
	public String getTypeSchemaRecord(String moduleName, String typeName, String version) {
		File f = getTypeSchemaFile(moduleName, typeName, version);
		if (f.exists() && f.isFile() && f.canRead()) {
			try {
				return readFile(f);
			} catch (IOException ignore) {}
		}
		return null;
	}
	
	@Override
	public String getTypeParseRecord(String moduleName, String typeName, String version) {
		File f = getTypeParseFile(moduleName, typeName, version);
		if (f.exists() && f.isFile() && f.canRead()) {
			try {
				return readFile(f);
			} catch (IOException ignore) {}
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

	private void writeFile(File location, String document) throws FileNotFoundException {
		PrintWriter pw = new PrintWriter(location);
		pw.print(document);
		pw.close();
	}

	@Override
	public void writeTypeSchemaRecord(String moduleName, String typeName,
			String version, String document) {
		try {
			writeFile(getTypeSchemaFile(moduleName, typeName, version), document);
		} catch (FileNotFoundException e) {
			throw new IllegalStateException("Unexpected internal error: " + e.getMessage(), e);
		}
	}
	
	@Override
	public void writeTypeParseRecord(String moduleName, String typeName,
			String version, String document) {
		try {
			writeFile(getTypeParseFile(moduleName, typeName, version), document);
		} catch (FileNotFoundException e) {
			throw new IllegalStateException("Unexpected internal error: " + e.getMessage(), e);
		}
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
	public void createModule(String moduleName) {
		File dir = getModuleDir(moduleName);
		dir.mkdir();
	}
	
	@Override
	public void writeModuleSpecRecord(String moduleName,
			String specDocument) {
		try {
			writeFile(getModuleSpecFile(moduleName, null), specDocument);
		} catch (FileNotFoundException ex) {
			throw new IllegalStateException(ex);
		}
	}
	
	
	@Override
	public void writeModuleSpecRecordBackup(String moduleName, String specDocument, long backupTime) {
		try {
			writeFile(getModuleSpecFile(moduleName, backupTime), specDocument);
		} catch (FileNotFoundException ex) {
			throw new IllegalStateException(ex);
		}
	}
	
	@Override
	public void writeModuleInfoRecord(String moduleName, String infoText) {
		try {
			writeFile(getModuleInfoFile(moduleName, null), infoText);
		} catch (FileNotFoundException ex) {
			throw new IllegalStateException(ex);
		}
	}
	
	@Override
	public void writeModuleInfoRecordBackup(String moduleName, String infoText, long backupTime) {
		try {
			writeFile(getModuleInfoFile(moduleName, backupTime), infoText);
		} catch (FileNotFoundException ex) {
			throw new IllegalStateException(ex);
		}
	}
	
	@Override
	public String getModuleInfoRecord(String moduleName) {
		try {
			return readFile(getModuleInfoFile(moduleName, null));
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
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
	public String getModuleSpecRecord(String moduleName) {
		try {
			return readFile(getModuleSpecFile(moduleName, null));
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
		
	private File getFuncParseFile(String moduleName, String typeName, String version) {
		return new File(new File(getModuleDir(moduleName), "func." + typeName).getAbsolutePath() + "." + version + ".prs");
	}

	@Override
	public void writeFuncParseRecord(String moduleName, String funcName,
			String version, String parseText) {
		try {
			writeFile(getFuncParseFile(moduleName, funcName, version), parseText);
		} catch (IOException ex) {
			throw new IllegalStateException("Unexpected internal error: " + ex.getMessage(), ex);
		}
	}
	
	@Override
	public String getFuncParseRecord(String moduleName, String typeName, String version) {
		File f = getFuncParseFile(moduleName, typeName, version);
		if (f.exists() && f.isFile() && f.canRead()) {
			try {
				return readFile(f);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
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
