package us.kbase.typedobj.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.cfg.ValidationConfiguration;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

import us.kbase.kidl.KbFuncdef;
import us.kbase.kidl.KbList;
import us.kbase.kidl.KbMapping;
import us.kbase.kidl.KbModule;
import us.kbase.kidl.KbModuleComp;
import us.kbase.kidl.KbParameter;
import us.kbase.kidl.KbScalar;
import us.kbase.kidl.KbService;
import us.kbase.kidl.KbStruct;
import us.kbase.kidl.KbStructItem;
import us.kbase.kidl.KbTuple;
import us.kbase.kidl.KbType;
import us.kbase.kidl.KbTypedef;
import us.kbase.kidl.KbUnspecifiedObject;
import us.kbase.kidl.KidlParser;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.validatorconfig.ValidationConfigurationFactory;
import us.kbase.typedobj.exceptions.*;

/**
 * This abstract base class provides methods/interface for retrieving a JSON Schema document and
 * JSON Schema validators for typed objects.
 * 
 * @author msneddon
 *
 */
public class TypeDefinitionDB {

	
	/**
	 * This is the factory used to create a JsonSchema object from a Json Schema
	 * document stored in the DB.
	 */
	protected JsonSchemaFactory jsonSchemaFactory; 
	
	/**
	 * The Jackson ObjectMapper which can translate a raw Json Schema document to
	 * a JsonTree that can be handled by the validator.
	 */
	protected ObjectMapper mapper;
	
	protected static SemanticVersion defaultVersion = new SemanticVersion(0, 1);
	protected static SemanticVersion releaseVersion = new SemanticVersion(1, 0);
	
	protected final TypeStorage storage;
	private final File parentTempDir;
	private final boolean withApprovalQueue;
	private final UserInfoProvider uip;
	
	enum Change {
		noChange, backwardCompatible, notCompatible;
		
		public static Change joinChanges(Change c1, Change c2) {
			return Change.values()[Math.max(c1.ordinal(), c2.ordinal())];
		}
	}
	
	/**
	 * Set up a new DB pointing to the specified db folder.  The contents
	 * should have a separate folder for each module named with the module
	 * name, and in each folder a set of .json files with json schema
	 * documents named the same as the type names.
	 * @param storage low level storage object
	 */
	public TypeDefinitionDB(TypeStorage storage, UserInfoProvider uip) {
		this(storage, null, uip);
	}
	
	public TypeDefinitionDB(TypeStorage storage, File tempDir, UserInfoProvider uip) {
		this(storage, tempDir, false, uip);
	}
	
	public TypeDefinitionDB(TypeStorage storage, File tempDir, boolean withApprovalQueue, UserInfoProvider uip) {
		this.mapper = new ObjectMapper();
		// Create the custom json schema factory for KBase typed objects and use this
		ValidationConfiguration kbcfg = ValidationConfigurationFactory.buildKBaseWorkspaceConfiguration();
		this.jsonSchemaFactory = JsonSchemaFactory.newBuilder()
									.setValidationConfiguration(kbcfg)
									.freeze();
		this.storage = storage;
		if (tempDir == null) {
			this.parentTempDir = new File(".");
		} else {
			this.parentTempDir = tempDir;
		}
		this.withApprovalQueue = withApprovalQueue;
		this.uip = uip;
	}
		
	/**
	 * Given a module and a type name, return true if the type exists, false otherwise
	 * @param moduleName
	 * @param typeName
	 * @return true if valid, false otherwise
	 * @throws TypeStorageException 
	 */
	public boolean isValidType(TypeDefName type) throws TypeStorageException {
		return isValidType(type.getModule(), type.getName(), null);
	}
		
	/**
	 * Given a moduleName and typeName, return the JSON Schema document for the type. No version
	 * number is specified, so the latest version on record is always the schema returned if the
	 * underlying Json Schema database supports versioned typed objects.
	 * @param moduleName
	 * @param typeName
	 * @return JSON Schema document as a String
	 * @throws NoSuchTypeException
	 */
	public String getJsonSchemaDocument(TypeDefName type) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		return getJsonSchemaDocument(type.getModule(), type.getName(), null);
	}

	/**
	 * The default implementation for getting a JsonSchema object that can be used as a validator.  This
	 * method creates a new JsonSchema object on each call.  If we implement caching of the validator
	 * objects for better performance, this is the method we would need to extend.
	 * @param moduleName
	 * @param typeName
	 * @return
	 * @throws NoSuchTypeException
	 */
	public JsonSchema getJsonSchema(TypeDefName type)
			throws NoSuchTypeException, NoSuchModuleException, BadJsonSchemaDocumentException, TypeStorageException
	{
		// first we retrieve the Json Schema document, this can throw a NoSuchTypeException
		String jsonSchemaDocument = getJsonSchemaDocument(type);
		
		// next we parse the document into a JsonSchema using our jsonSchemaFactory
		// if there are problems, we catch and throw up an error indicating a bad document
		return jsonSchemaFromString(type.getModule(), type.getName(), jsonSchemaDocument);
	}

	protected JsonSchema jsonSchemaFromString(String moduleName,
			String typeName, String jsonSchemaDocument)
			throws BadJsonSchemaDocumentException, TypeStorageException {
		try {
			JsonNode schemaRootNode = mapper.readTree(jsonSchemaDocument);
			return jsonSchemaFactory.getJsonSchema(schemaRootNode);
		} catch (Exception e) {
			throw new BadJsonSchemaDocumentException("schema for typed object '"+moduleName+"."+typeName+"' was not a valid or readable JSON document",e);
		}
	}
	
	/**
	 * Given a moduleName and typeName, return the SPEC parsing document for the type. No version
	 * number is specified, so the latest version of document will be returned.
	 * @param moduleName
	 * @param typeName
	 * @return JSON Schema document as a String
	 * @throws NoSuchTypeException
	 */
	public KbTypedef getTypeParsingDocument(TypeDefName type) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		return getTypeParsingDocument(type.getModule(), type.getName(), null);
	}

	public boolean isValidModule(String moduleName) throws TypeStorageException {
		if (!storage.checkModuleExist(moduleName))
			return false;
		long version = storage.getLastModuleVersion(moduleName);
		return storage.checkModuleInfoRecordExist(moduleName, version) && 
				storage.checkModuleSpecRecordExist(moduleName, version);
	}

	protected void checkModule(String moduleName) throws NoSuchModuleException, TypeStorageException {
		if (!isValidModule(moduleName))
			throw new NoSuchModuleException("Module wasn't uploaded: " + moduleName);
	}

	protected void checkModuleRegistered(String moduleName) throws NoSuchModuleException, TypeStorageException {
		if ((!storage.checkModuleExist(moduleName)) || (!storage.checkModuleInfoRecordExist(moduleName,
				storage.getLastModuleVersion(moduleName))))
			throw new NoSuchModuleException("Module wasn't registered: " + moduleName);
	}

	/**
	 * Given a module, a typeName and version, return true if the type exists, false otherwise.
	 * If version parameter is null (no version number is specified) then the latest version is 
	 * used for checking schema.
	 * @param moduleName
	 * @param typeName
	 * @param version
	 * @return true if valid, false otherwise
	 */
	public boolean isValidType(AbsoluteTypeDefId typeDef) throws TypeStorageException {
		return isValidType(typeDef.getType().getModule(), typeDef.getType().getName(), typeDef.getVerString());
	}
	
	private boolean isValidType(String moduleName, String typeName, String version) throws TypeStorageException {
		if (!isValidModule(moduleName))
			return false;
		SemanticVersion ver = findTypeVersion(moduleName, typeName, version);
		if (ver == null)
			return false;
		return storage.checkTypeSchemaRecordExists(moduleName, typeName, ver.toString());
	}

	private boolean isTypePresent(String moduleName, String typeName) throws TypeStorageException {
		ModuleInfo mi;
		try {
			mi = getModuleInfo(moduleName);
		} catch (NoSuchModuleException e) {
			return false;
		}
		return mi.getTypes().get(typeName) != null;
	}

	private SemanticVersion findTypeVersion(String moduleName, String typeName, 
			String versionText) throws TypeStorageException {
		return versionText == null ? findLastTypeVersion(moduleName, typeName, false) : 
			new SemanticVersion(versionText);
	}

	private SemanticVersion findLastTypeVersion(String moduleName, String typeName, 
			boolean withNoLongerSupported) throws TypeStorageException {
		if (!isTypePresent(moduleName, typeName))
			return null;
		ModuleInfo mi;
		try {
			mi = getModuleInfo(moduleName);
		} catch (NoSuchModuleException e) {
			return null;
		}
		return findLastTypeVersion(mi, typeName, withNoLongerSupported);
	}
	
	private SemanticVersion findLastTypeVersion(ModuleInfo module, String typeName, boolean withNoLongerSupported) {
		TypeInfo ti = module.getTypes().get(typeName);
		if (ti == null || !(ti.isSupported() || withNoLongerSupported))
			return null;
		if (ti.getTypeVersion() == null)
			return null;
		return new SemanticVersion(ti.getTypeVersion());
	}
	
	/**
	 * Given a moduleName, a typeName and version, return the JSON Schema document for the type. If 
	 * version parameter is null (no version number is specified) then the latest version is used for
	 * the schema returned if the underlying Json Schema database supports versioned typed objects.
	 * @param moduleName
	 * @param typeName
	 * @param version
	 * @return JSON Schema document as a String
	 * @throws NoSuchTypeException
	 */
	public String getJsonSchemaDocument(AbsoluteTypeDefId typeDef)
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		return getJsonSchemaDocument(typeDef.getType().getModule(), typeDef.getType().getName(), 
				typeDef.getVerString());
	}
	
	private String getJsonSchemaDocument(String moduleName, String typeName, String version)
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		checkModule(moduleName);
		// first make sure that the json schema document can be found
		SemanticVersion schemaDocumentVer = findTypeVersion(moduleName, typeName, version);
		if (schemaDocumentVer == null)
			throwNoSuchTypeException(moduleName, typeName, null);
		String ret = storage.getTypeSchemaRecord(moduleName, typeName, schemaDocumentVer.toString());
		if (ret == null)
			throw new NoSuchTypeException("Unable to read type schema record: '"+moduleName+"."+typeName+"'");
		return ret;
	}

	protected void throwNoSuchTypeException(String moduleName, String typeName,
			String version) throws NoSuchTypeException {
		throw new NoSuchTypeException("Unable to locate type: '"+moduleName+"."+typeName+"'" + 
				(version == null ? "" : (" for version " + version)));
	}

	protected void throwNoSuchFuncException(String moduleName, String funcName,
			String version) throws NoSuchFuncException {
		throw new NoSuchFuncException("Unable to locate function: '"+moduleName+"."+funcName+"'" + 
				(version == null ? "" : (" for version " + version)));
	}
	
	public List<String> getAllRegisteredTypes(String moduleName) 
			throws NoSuchModuleException, TypeStorageException {
		List<String> ret = new ArrayList<String>();
		for (TypeInfo typeInfo : getModuleInfo(moduleName).getTypes().values())
			if (typeInfo.isSupported())
				ret.add(typeInfo.getTypeName());
		return ret;
	}
	
	/**
	 * Return latest version of specified type. Version has two level structure of integers divided by dot like &lt;major&gt;.&lt;minor&gt;
	 * @param moduleName
	 * @param typeName
	 * @return latest version of specified type
	 */
	public String getLatestTypeVersion(TypeDefName type) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		checkModule(type.getModule());
		SemanticVersion ret = findLastTypeVersion(type.getModule(), type.getName(), false);
		if (ret == null)
			throwNoSuchTypeException(type.getModule(), type.getName(), null);
		return ret.toString();
	}
	
	private String saveType(ModuleInfo mi, String typeName, String jsonSchemaDocument,
			KbTypedef specParsing, boolean notBackwardCompatible, Set<RefInfo> dependencies,
			long newModuleVersion) throws NoSuchModuleException, TypeStorageException {
		TypeInfo ti = mi.getTypes().get(typeName);
		if (ti == null) {
			ti = new TypeInfo();
			ti.setTypeName(typeName);
			mi.getTypes().put(typeName, ti);
		}
		ti.setSupported(true);
		return saveType(mi, ti, jsonSchemaDocument, specParsing, notBackwardCompatible, dependencies, newModuleVersion);
	}
	
	private String saveType(ModuleInfo mi, TypeInfo ti, String jsonSchemaDocument,
			KbTypedef specParsing, boolean notBackwardCompatible, Set<RefInfo> dependencies, 
			long newModuleVersion) throws NoSuchModuleException, TypeStorageException {
		SemanticVersion version = findLastTypeVersion(mi, ti.getTypeName(), true);
		if (version == null) {
			version = new SemanticVersion(0, 1);
		} else {
			int major = version.getMajor();
			int minor = version.getMinor();
			if (major > 0 && notBackwardCompatible) {
				major++;
				minor = 0;
			} else {
				minor++;
			}
			version = new SemanticVersion(major, minor);
		}
		ti.setTypeVersion(version.toString());
		return saveType(mi, ti, jsonSchemaDocument, specParsing, dependencies, newModuleVersion);
	}
	
	private String saveType(ModuleInfo mi, TypeInfo ti, String jsonSchemaDocument,
			KbTypedef specParsing, Set<RefInfo> dependencies, long newModuleVersion) 
					throws NoSuchModuleException, TypeStorageException {
		if (dependencies != null)
			for (RefInfo ri : dependencies) {
				ri.setDepVersion(ti.getTypeVersion());
				ri.setDepModuleVersion(newModuleVersion);
				updateInternalRefVersion(ri, mi);
			}
		storage.writeTypeSchemaRecord(mi.getModuleName(), ti.getTypeName(), ti.getTypeVersion(), newModuleVersion, jsonSchemaDocument);
		storage.removeTypeRefs(mi.getModuleName(), ti.getTypeName(), ti.getTypeVersion());
		writeTypeParsingFile(mi.getModuleName(), ti.getTypeName(), ti.getTypeVersion(), specParsing, newModuleVersion);
		return ti.getTypeVersion();
	}

	private void updateInternalRefVersion(RefInfo ri, ModuleInfo mi) {
		if (ri.getRefVersion() == null) {
			if (!ri.getRefModule().equals(mi.getModuleName()))
				throw new IllegalStateException("Type reference has no refVersion but reference is not internal: " + ri);
		}
		if (ri.getRefModule().equals(mi.getModuleName())) {
			TypeInfo ti = mi.getTypes().get(ri.getRefName());
			if (ti == null)
				throw new IllegalStateException("Type reference was not found: " + ri);
			ri.setRefVersion(ti.getTypeVersion());
		}
	}
	
	private void writeTypeParsingFile(String moduleName, String typeName, String version, 
			KbTypedef document, long newModuleVersion) throws TypeStorageException {
		try {
			StringWriter sw = new StringWriter();
			mapper.writeValue(sw, document.getData());
			sw.close();
			storage.writeTypeParseRecord(moduleName, typeName, version, newModuleVersion, sw.toString());
		} catch (IOException ex) {
			throw new IllegalStateException("Unexpected internal error: " + ex.getMessage(), ex);
		}
	}
	
	private boolean checkUserIsOwnerOrAdmin(String moduleName, String userId) 
			throws NoSuchPrivilegeException, TypeStorageException {
		if (uip.isAdmin(userId))
			return true;
		Map<String, OwnerInfo> owners = storage.getOwnersForModule(moduleName);
		if (!owners.containsKey(userId))
			throw new NoSuchPrivilegeException("User " + userId + " is not in list of owners of module " + moduleName);
		return owners.get(userId).isWithChangeOwnersPrivilege();
	}
	
	/**
	 * Change major version from 0 to 1.
	 * @param moduleName
	 * @param typeName
	 * @param userId
	 * @return new version
	 * @throws NoSuchTypeException when current major version isn't 0
	 * @throws NoSuchPrivilegeException 
	 */
	public String releaseType(TypeDefName type, String userId)
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		String moduleName = type.getModule();
		String typeName = type.getName();
		checkUserIsOwnerOrAdmin(moduleName, userId);
		ModuleInfo info = getModuleInfo(moduleName);
		SemanticVersion curVersion = findLastTypeVersion(info, typeName, false);
		if (curVersion == null)
			throwNoSuchTypeException(moduleName, typeName, null);
		if (curVersion.getMajor() != 0)
			throwNoSuchTypeException(moduleName, typeName, "0.x");
		String jsonSchemaDocument = getJsonSchemaDocument(moduleName, typeName, null);
		KbTypedef specParsing = getTypeParsingDocument(moduleName, typeName, null);
		Set<RefInfo> deps = storage.getTypeRefsByDep(moduleName, typeName, curVersion.toString());
		SemanticVersion ret = releaseVersion;
		long transactionStartTime = storage.generateNewModuleVersion(moduleName);
		try {
			TypeInfo ti = info.getTypes().get(typeName);
			ti.setTypeVersion(ret.toString());
			writeModuleInfo(moduleName, info, transactionStartTime);
			saveType(info, ti, jsonSchemaDocument, specParsing, deps, transactionStartTime);
			storage.addRefs(deps, new TreeSet<RefInfo>());
			transactionStartTime = -1;
		} finally {
			if (transactionStartTime > 0)
				rollbackModuleTransaction(moduleName, transactionStartTime);
		}
		return ret.toString();
	}
	
	public void removeTypeForAllVersions(TypeDefName type, String userId)
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		String moduleName = type.getModule();
		String typeName = type.getName();
		checkUserIsOwnerOrAdmin(moduleName, userId);
		ModuleInfo info = getModuleInfo(moduleName);
		if (!info.getTypes().containsKey(typeName))
			throwNoSuchTypeException(moduleName, typeName, null);
		info.getTypes().remove(typeName);
		writeModuleInfo(moduleName, info, storage.generateNewModuleVersion(moduleName));
		storage.removeAllTypeRecords(moduleName, typeName);
	}

	public void removeFuncForAllVersions(String moduleName, String funcName, String userId)
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		checkUserIsOwnerOrAdmin(moduleName, userId);
		ModuleInfo info = getModuleInfo(moduleName);
		if (!info.getFuncs().containsKey(funcName))
			throwNoSuchFuncException(moduleName, funcName, null);
		info.getTypes().remove(funcName);
		writeModuleInfo(moduleName, info, storage.generateNewModuleVersion(moduleName));
		storage.removeAllFuncRecords(moduleName, funcName);
	}
	
	/**
	 * Given a moduleName, a typeName and version, return the JSON Schema document for the type. If 
	 * version parameter is null (no version number is specified) then the latest version of document will be returned.
	 * @param moduleName
	 * @param typeName
	 * @param version
	 * @return JSON Schema document as a String
	 * @throws NoSuchTypeException
	 */
	public KbTypedef getTypeParsingDocument(AbsoluteTypeDefId typeDef) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		return getTypeParsingDocument(typeDef.getType().getModule(), typeDef.getType().getName(), 
				typeDef.getVerString());
	}
	
	private KbTypedef getTypeParsingDocument(String moduleName, String typeName,
			String version) throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		checkModule(moduleName);
		SemanticVersion documentVer = findTypeVersion(moduleName, typeName, version);
		if (documentVer == null)
			throwNoSuchTypeException(moduleName, typeName, null);
		String ret = storage.getTypeParseRecord(moduleName, typeName, documentVer.toString());
		if (ret == null)
			throw new NoSuchTypeException("Unable to read type parse record: '"+moduleName+"."+typeName+"'");
		try {
			Map<?,?> data = mapper.readValue(ret, Map.class);
			return new KbTypedef().loadFromMap(data);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
		
	private void rollbackModuleTransaction(String moduleName, long transtactionStartTime) {
	}

	private void writeModuleInfo(String moduleName, ModuleInfo info, long backupTime) throws TypeStorageException {
		String specDocument = storage.getModuleSpecRecord(moduleName, storage.getLastModuleVersion(moduleName));
		storage.writeModuleRecords(moduleName, info, specDocument, backupTime);
	}

	private void writeModuleInfoSpec(String moduleName, ModuleInfo info, String specDocument, long backupTime) throws TypeStorageException {
		storage.writeModuleRecords(moduleName, info, specDocument, backupTime);
	}

	public String getModuleSpecDocument(String moduleName) 
			throws NoSuchModuleException, TypeStorageException {
		checkModule(moduleName);
		return storage.getModuleSpecRecord(moduleName, storage.getLastModuleVersion(moduleName));
	}
	
	public ModuleInfo getModuleInfo(String moduleName) 
			throws NoSuchModuleException, TypeStorageException {
		checkModuleRegistered(moduleName);
		return storage.getModuleInfoRecord(moduleName, storage.getLastModuleVersion(moduleName));
	}
		
	public List<String> getAllRegisteredFuncs(String moduleName) 
			throws NoSuchModuleException, TypeStorageException {
		List<String> ret = new ArrayList<String>();
		for (FuncInfo info : getModuleInfo(moduleName).getFuncs().values()) 
			if (info.isSupported())
				ret.add(info.getFuncName());
		return ret;
	}

	private SemanticVersion findLastFuncVersion(String moduleName, String funcName, 
			boolean withNotSupported) throws TypeStorageException {
		try {
			return findLastFuncVersion(getModuleInfo(moduleName), funcName, withNotSupported);
		} catch (NoSuchModuleException e) {
			return null;
		}
	}
		
	private SemanticVersion findLastFuncVersion(ModuleInfo mi, String funcName, boolean withNotSupported) {
		FuncInfo fi = mi.getFuncs().get(funcName);
		if (fi == null || !(fi.isSupported() || withNotSupported))
			return null;
		if (fi.getFuncVersion() == null)
			return null;
		return new SemanticVersion(fi.getFuncVersion());
	}

	/**
	 * Return latest version of specified type. Version has two level structure of integers divided by dot like &lt;major&gt;.&lt;minor&gt;
	 * @param moduleName
	 * @param funcName
	 * @return latest version of specified type
	 */
	public String getLatestFuncVersion(String moduleName, String funcName)
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException {
		checkModule(moduleName);
		SemanticVersion ret = findLastFuncVersion(moduleName, funcName, false);
		if (ret == null)
			throwNoSuchFuncException(moduleName, funcName, null);
		return ret.toString();
	}
	
	private String saveFunc(ModuleInfo mi, String funcName, KbFuncdef specParsingDocument, 
			boolean notBackwardCompatible, Set<RefInfo> dependencies, long newModuleVersion) throws NoSuchModuleException, TypeStorageException {
		FuncInfo fi = mi.getFuncs().get(funcName);
		if (fi == null) {
			fi = new FuncInfo();
			fi.setFuncName(funcName);
			mi.getFuncs().put(funcName, fi);
		}
		fi.setSupported(true);
		return saveFunc(mi, fi, specParsingDocument, notBackwardCompatible, dependencies, newModuleVersion);
	}
	
	private String saveFunc(ModuleInfo mi, FuncInfo fi, KbFuncdef specParsingDocument, 
			boolean notBackwardCompatible, Set<RefInfo> dependencies, long newModuleVersion) 
					throws NoSuchModuleException, TypeStorageException {
		SemanticVersion version = findLastFuncVersion(mi, fi.getFuncName(), true);
		if (version == null) {
			version = new SemanticVersion(0, 1);
		} else {
			int major = version.getMajor();
			int minor = version.getMinor();
			if (major > 0 && notBackwardCompatible) {
				major++;
				minor = 0;
			} else {
				minor++;
			}
			version = new SemanticVersion(major, minor);
		}
		fi.setFuncVersion(version.toString());
		return saveFunc(mi, fi, specParsingDocument, dependencies, newModuleVersion);
	}
		
	private String saveFunc(ModuleInfo mi, FuncInfo fi, KbFuncdef specParsingDocument, 
			Set<RefInfo> dependencies, long newModuleVersion) throws NoSuchModuleException, TypeStorageException {
		if (dependencies != null)
			for (RefInfo dep : dependencies) {
				dep.setDepVersion(fi.getFuncVersion());
				dep.setDepModuleVersion(newModuleVersion);
				updateInternalRefVersion(dep, mi);
			}
		writeFuncParsingFile(mi.getModuleName(), fi.getFuncName(), fi.getFuncVersion(), specParsingDocument, newModuleVersion);
		return fi.getFuncVersion();
	}
	
	private void writeFuncParsingFile(String moduleName, String funcName, String version, KbFuncdef document, long newModuleVersion) 
			throws TypeStorageException {
		try {
			StringWriter sw = new StringWriter();
			mapper.writeValue(sw, document.getData());
			sw.close();
			storage.writeFuncParseRecord(moduleName, funcName, version.toString(), newModuleVersion, sw.toString());
		} catch (TypeStorageException ex) {
			throw ex;
		} catch (IOException ex) {
			throw new IllegalStateException("Unexpected internal error: " + ex.getMessage(), ex);
		}
	}

	public KbFuncdef getFuncParsingDocument(String moduleName, String funcName) 
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException {
		return getFuncParsingDocument(moduleName, funcName, null);
	}

	/**
	 * Change major version from 0 to 1.
	 * @param moduleName
	 * @param funcName
	 * @return new version
	 * @throws NoSuchPrivilegeException 
	 * @throws NoSuchTypeException when current major version isn't 0
	 */
	public String releaseFunc(String moduleName, String funcName, String userId) 
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		checkUserIsOwnerOrAdmin(moduleName, userId);
		SemanticVersion curVersion = findLastFuncVersion(moduleName, funcName, false);
		if (curVersion == null)
			throwNoSuchFuncException(moduleName, funcName, null);
		if (curVersion.getMajor() != 0)
			throwNoSuchFuncException(moduleName, funcName, "0.x");
		KbFuncdef specParsing = getFuncParsingDocument(moduleName, funcName);
		Set<RefInfo> deps = storage.getFuncRefsByDep(moduleName, funcName, curVersion.toString());
		ModuleInfo info = getModuleInfo(moduleName);
		SemanticVersion ret = releaseVersion;
		long transactionStartTime = storage.generateNewModuleVersion(moduleName);
		try {
			FuncInfo fi = info.getFuncs().get(funcName);
			fi.setFuncVersion(ret.toString());
			writeModuleInfo(moduleName, info, transactionStartTime);
			saveFunc(info, fi, specParsing, deps, transactionStartTime);
			storage.addRefs(new TreeSet<RefInfo>(), deps);
			transactionStartTime = -1;
		} finally {
			if (transactionStartTime > 0)
				rollbackModuleTransaction(moduleName, transactionStartTime);
		}
		return ret.toString();
	}
	
	public KbFuncdef getFuncParsingDocument(String moduleName, String funcName,
			String version) throws NoSuchFuncException, NoSuchModuleException, TypeStorageException {
		checkModule(moduleName);
		SemanticVersion curVersion = version == null ? 
				findLastFuncVersion(moduleName, funcName, false) : new SemanticVersion(version);
		if (curVersion == null)
			throwNoSuchFuncException(moduleName, funcName, null);
		String ret = storage.getFuncParseRecord(moduleName, funcName, curVersion.toString());
		if (ret == null)
			throwNoSuchFuncException(moduleName, funcName, version);
		try {
			Map<?,?> data = mapper.readValue(ret, Map.class);
			return new KbFuncdef().loadFromMap(data, null);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
	
	private void stopTypeSupport(ModuleInfo mi, String typeName, long newModuleVersion) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		TypeInfo ti = mi.getTypes().get(typeName);
		if (ti == null)
			throwNoSuchTypeException(mi.getModuleName(), typeName, null);
		String jsonSchemaDocument = storage.getTypeSchemaRecord(mi.getModuleName(), typeName, ti.getTypeVersion());
		KbTypedef specParsing = getTypeParsingDocument(mi.getModuleName(), typeName, ti.getTypeVersion());
		saveType(mi, ti, jsonSchemaDocument, specParsing, false, null, newModuleVersion);
		ti.setSupported(false);
	}
	
	public void stopTypeSupport(TypeDefName type, String userId)
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		String moduleName = type.getModule();
		String typeName = type.getName();
		checkUserIsOwnerOrAdmin(moduleName, userId);
		ModuleInfo mi = getModuleInfo(moduleName);
		long transactionStartTime = storage.generateNewModuleVersion(moduleName);
		try {
			writeModuleInfo(moduleName, mi, transactionStartTime);
			stopTypeSupport(mi, typeName, transactionStartTime);
			transactionStartTime = -1;
		} finally {
			if (transactionStartTime > 0)
				rollbackModuleTransaction(moduleName, transactionStartTime);
		}
	}
	
	public void removeTypeVersion(AbsoluteTypeDefId typeDef, String userId) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		checkUserIsOwnerOrAdmin(typeDef.getType().getModule(), userId);
		checkModule(typeDef.getType().getModule());
		if (!storage.removeTypeRecordsForVersion(typeDef.getType().getModule(), typeDef.getType().getName(), 
				typeDef.getVerString()))
			throwNoSuchTypeException(typeDef.getType().getModule(), typeDef.getType().getName(), 
					typeDef.getVerString());
	}
	
	private void stopFuncSupport(ModuleInfo info, String funcName, long newModuleVersion) 
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException {
		FuncInfo fi = info.getFuncs().get(funcName);
		if (fi == null)
			throwNoSuchFuncException(info.getModuleName(), funcName, null);
		KbFuncdef specParsingDocument = getFuncParsingDocument(info.getModuleName(), funcName, fi.getFuncVersion());
		saveFunc(info, fi, specParsingDocument, false, null, newModuleVersion);
		fi.setSupported(false);
	}
	
	public void stopFuncSupport(String moduleName, String funcName, String userId)
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		checkUserIsOwnerOrAdmin(moduleName, userId);
		ModuleInfo mi = getModuleInfo(moduleName);
		long transactionStartTime = storage.generateNewModuleVersion(moduleName);
		try {
			writeModuleInfo(moduleName, mi, transactionStartTime);
			stopFuncSupport(mi, funcName, transactionStartTime);
			transactionStartTime = -1;
		} finally {
			if (transactionStartTime > 0)
				rollbackModuleTransaction(moduleName, transactionStartTime);
		}
	}
	
	public void removeModule(String moduleName, String userId) 
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		checkAdmin(userId);
		checkModuleRegistered(moduleName);
		storage.removeModule(moduleName);
	}
	
	/**
	 * @return all names of registered modules
	 */
	public List<String> getAllRegisteredModules() throws TypeStorageException {
		return storage.getAllRegisteredModules();
	}
	
	public void removeAllRefs(String userId) throws TypeStorageException, NoSuchPrivilegeException {
		checkAdmin(userId);
		storage.removeAllRefs();
	}
	
	public Set<RefInfo> getTypeRefsByDep(AbsoluteTypeDefId depTypeDef) throws TypeStorageException {
		String depModule = depTypeDef.getType().getModule();
		String depType = depTypeDef.getType().getName();
		String version = depTypeDef.getVerString();
		return storage.getTypeRefsByDep(depModule, depType, version);
	}
	
	public Set<RefInfo> getTypeRefsByRef(AbsoluteTypeDefId refTypeDef) throws TypeStorageException {
		String refModule = refTypeDef.getType().getModule();
		String refType = refTypeDef.getType().getName();
		String version = refTypeDef.getVerString();
		return storage.getTypeRefsByRef(refModule, refType, version);
	}
	
	public Set<RefInfo> getFuncRefsByDep(String depModule, String depFunc, 
			String version) throws TypeStorageException {
		return storage.getFuncRefsByDep(depModule, depFunc, version);
	}
	
	public Set<RefInfo> getFuncRefsByRef(AbsoluteTypeDefId refTypeDef) throws TypeStorageException {
		String refModule = refTypeDef.getType().getModule();
		String refType = refTypeDef.getType().getName();
		String version = refTypeDef.getVerString();
		return storage.getFuncRefsByRef(refModule, refType, version);
	}
	
	private File createTempDir() {
		File ret = new File(parentTempDir, "temp_" + System.currentTimeMillis());
		ret.mkdirs();
		return ret;
	}
	
	public void requestModuleRegistration(String moduleName, String ownerUserId)
			throws TypeStorageException {
		if (!withApprovalQueue)
			throw new IllegalStateException("Type definition db was created without approval queue, please use registerModule without request.");
		storage.addNewModuleRegistrationRequest(moduleName, ownerUserId);
	}
	
	public OwnerInfo getNextNewModuleRegistrationRequest(String adminUserId) 
			throws NoSuchPrivilegeException, TypeStorageException {
		checkAdmin(adminUserId);
		List<OwnerInfo> list = storage.getNewModuleRegistrationRequests();
		if (list.size() == 0)
			return null;
		return list.get(0);
	}

	private void checkAdmin(String adminUserId)
			throws NoSuchPrivilegeException {
		if (!uip.isAdmin(adminUserId))
			throw new NoSuchPrivilegeException("User " + adminUserId + " should be administrator");
	}
	
	public void approveModuleRegistrationRequest(String adminUserId, String newModuleName, 
			String newOwnerUserId) throws TypeStorageException, NoSuchPrivilegeException {
		if (!withApprovalQueue)
			throw new IllegalStateException("Type definition db was created without approval queue, please use registerModule without request.");
		checkAdmin(adminUserId);
		autoGenerateModuleInfo(newModuleName, newOwnerUserId);
		storage.removeNewModuleRegistrationRequest(newModuleName, newOwnerUserId);
		// TODO: send notification to e-mail of requesting user
	}
	
	private void autoGenerateModuleInfo(String moduleName, String ownerUserId) throws TypeStorageException {
		if (storage.checkModuleExist(moduleName))
			throw new IllegalStateException("Module " + moduleName + " was already registered");
		ModuleInfo info = new ModuleInfo();
		info.setModuleName(moduleName);
		storage.initModuleInfoRecord(moduleName, info);
		storage.addOwnerToModule(moduleName, ownerUserId, true);
	}
	
	public void registerModule(String specDocument, List<String> registeredTypes, 
			String userId) throws SpecParseException, TypeStorageException {
		saveModule(specDocument, true, new HashSet<String>(registeredTypes), new HashSet<String>(), userId);
	}
	
	private String correctSpecIncludes(String specDocument, List<String> includedModules) throws SpecParseException {
		try {
			StringWriter withGoodImports = new StringWriter();
			PrintWriter pw = null; // new PrintWriter(withGoodImports);
			BufferedReader br = new BufferedReader(new StringReader(specDocument));
			while (true) {
				String l = br.readLine();
				if (l == null)
					break;
				if (pw == null) {
					if (l.trim().isEmpty())
						continue;
					if (l.startsWith("#include")) {
						l = l.substring(8).trim();
						if (!(l.startsWith("<") && l.endsWith(">")))
							throw new IllegalStateException("Wrong include structure (it should be ...<file_path>): " + l);
						l = l.substring(1, l.length() - 1).trim();
						if (l.indexOf('/') >= 0)
							l = l.substring(l.lastIndexOf('/') + 1);
						if (l.indexOf('.') >= 0)
							l = l.substring(0, l.indexOf('.')).trim();
						includedModules.add(l);
					} else {
						pw = new PrintWriter(withGoodImports);
						for (String iModuleName : includedModules)
							pw.println("#include <" + iModuleName + ".types>");
						pw.println();
						pw.println(l);
					}
				} else {
					pw.println(l);
				}
			}
			br.close();
			pw.close();
			return withGoodImports.toString();
		} catch (Exception ex) {
			throw new SpecParseException("Unexpected error during parsing of spec-file include declarations: " + ex.getMessage(), ex);
		}
	}
	
	private KbModule compileSpecFile(String specDocument, List<String> includedModules,
			Map<String, Map<String, String>> moduleToTypeToSchema,
			Map<String, ModuleInfo> moduleToInfo) throws SpecParseException {
		File tempDir = createTempDir();
		try {
			File specFile = new File(tempDir, "currentlyCompiled.spec");
			writeFile(specDocument, specFile);
			for (String iModule : includedModules) {
				saveIncludedModuleRecusive(tempDir, iModule, moduleToInfo);
			}
			List<KbService> services = KidlParser.parseSpec(specFile, tempDir, moduleToTypeToSchema);
			if (services.size() != 1)
				throw new SpecParseException("Spec-file should consist of only one service");
			if (services.get(0).getModules().size() != 1)
				throw new SpecParseException("Spec-file should consist of only one module");
			return services.get(0).getModules().get(0);
		} catch (SpecParseException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new SpecParseException("Unexpected error during spec-file parsing: " + ex.getMessage(), ex);			
		} finally {
			deleteTempDir(tempDir);
		}
	}
	
	private void saveModule(String specDocument, boolean isNew, Set<String> addedTypes,
			Set<String> unregisteredTypes, String userId) 
					throws SpecParseException, TypeStorageException {
		List<String> includedModules = new ArrayList<String>();
		specDocument = correctSpecIncludes(specDocument, includedModules);
		//System.out.println("----------------------------------------------");
		//System.out.println("Spec-file:");
		//System.out.println(specDocument);
		String moduleName = null;
		long transactionStartTime = -1;
		try {
			Map<String, Map<String, String>> moduleToTypeToSchema = new HashMap<String, Map<String, String>>();
			Map<String, ModuleInfo> moduleToInfo = new HashMap<String, ModuleInfo>();
			KbModule module = compileSpecFile(specDocument, includedModules, moduleToTypeToSchema, moduleToInfo);
			moduleName = module.getModuleName();
			if (isNew) {
				if (withApprovalQueue) {
					checkModuleRegistered(moduleName);
					if (storage.checkModuleSpecRecordExist(moduleName, storage.getLastModuleVersion(moduleName)))
						throw new IllegalStateException("Module " + moduleName + " was already uploaded");
				} else {
					if (!storage.checkModuleExist(moduleName))
						autoGenerateModuleInfo(moduleName, userId);
				}
			} else {
				checkModule(moduleName);
			}
			checkUserIsOwnerOrAdmin(moduleName, userId);
			ModuleInfo info = getModuleInfo(moduleName);
			info.setIncludedModuleNames(new ArrayList<String>(includedModules));
			Map<String, String> typeToSchema = moduleToTypeToSchema.get(moduleName);
			if (typeToSchema == null)
				throw new SpecParseException("Json schema generation was missed for module: " + moduleName);
			Set<String> oldRegisteredTypes = new HashSet<String>();
			Set<String> oldRegisteredFuncs = new HashSet<String>();
			if (!isNew) {
				for (TypeInfo typeInfo : info.getTypes().values())
					if (typeInfo.isSupported())
						oldRegisteredTypes.add(typeInfo.getTypeName());
				for (FuncInfo funcInfo : getModuleInfo(moduleName).getFuncs().values()) 
					if (funcInfo.isSupported())
						oldRegisteredFuncs.add(funcInfo.getFuncName());
			}
			for (String type : unregisteredTypes) {
				if (!oldRegisteredTypes.contains(type))
					throw new SpecParseException("Type is in unregistering type list but was not already " +
							"registered: " + type);
			}
			for (String type : addedTypes) {
				if (oldRegisteredTypes.contains(type))
					throw new SpecParseException("Type was already registered before: " + type);
				if (unregisteredTypes.contains(type))
					throw new SpecParseException("Type couldn't be in both adding and unregistering lists: " + type);
			}
			Set<String> newRegisteredTypes = new HashSet<String>();
			newRegisteredTypes.addAll(oldRegisteredTypes);
			newRegisteredTypes.removeAll(unregisteredTypes);
			newRegisteredTypes.addAll(addedTypes);
			Set<String> allNewTypes = new HashSet<String>();
			Set<String> allNewFuncs = new HashSet<String>();
			List<ComponentChange> comps = new ArrayList<ComponentChange>();
			for (KbModuleComp comp : module.getModuleComponents()) {
				if (comp instanceof KbTypedef) {
					KbTypedef type = (KbTypedef)comp;
					allNewTypes.add(type.getName());
					if (newRegisteredTypes.contains(type.getName())) {
						if (typeToSchema.get(type.getName()) == null)
							throw new SpecParseException("Json schema wasn't generated for type: " + type.getName());
						Change change = findTypeChange(info, type);
						if (change == Change.noChange)
							continue;
						String jsonSchemaDocument = typeToSchema.get(type.getName());
						Set<RefInfo> dependencies = extractTypeRefs(type, moduleToInfo, newRegisteredTypes);
						jsonSchemaFromString(info.getModuleName(), type.getName(), jsonSchemaDocument);
						boolean notBackwardCompatible = (change == Change.notCompatible);
						comps.add(new ComponentChange(true, false, type.getName(), jsonSchemaDocument, type, null, 
								notBackwardCompatible, dependencies));
					}
				} else if (comp instanceof KbFuncdef) {
					KbFuncdef func = (KbFuncdef)comp;
					allNewFuncs.add(func.getName());
					Change change = findFuncChange(info, func);
					if (change == Change.noChange)
						continue;
					Set<RefInfo> dependencies = new TreeSet<RefInfo>();
					for (KbParameter param : func.getParameters())
						dependencies.addAll(extractTypeRefs(moduleName, func.getName(), param, moduleToInfo, newRegisteredTypes));
					for (KbParameter param : func.getReturnType())
						dependencies.addAll(extractTypeRefs(moduleName, func.getName(), param, moduleToInfo, newRegisteredTypes));					
					boolean notBackwardCompatible = (change == Change.notCompatible);
					comps.add(new ComponentChange(false, false, func.getName(), null, null, func, notBackwardCompatible, 
							dependencies));
				}
			}
			for (String type : addedTypes) {
				if (!allNewTypes.contains(type))
					throw new SpecParseException("Type is in adding type list but is not defined in spec-file: " + type);
			}
			for (String type : newRegisteredTypes) {
				if (!allNewTypes.contains(type))
					unregisteredTypes.add(type);
			}
			for (String typeName : unregisteredTypes) {
				comps.add(new ComponentChange(true, true, typeName, null, null, null, false, null));
			}
			for (String funcName : oldRegisteredFuncs) {
				if (!allNewFuncs.contains(funcName)) {
					comps.add(new ComponentChange(false, true, funcName, null, null, null, false, null));
				}
			}
			Set<RefInfo> createdTypeRefs = new TreeSet<RefInfo>();
			Set<RefInfo> createdFuncRefs = new TreeSet<RefInfo>();
			transactionStartTime = storage.generateNewModuleVersion(moduleName);
			for (ComponentChange comp : comps) {
				if (comp.isType) {
					if (comp.isDeletion) {
						stopTypeSupport(info, comp.name, transactionStartTime);						
					} else {
						saveType(info, comp.name, comp.jsonSchemaDocument, comp.typeParsing, comp.notBackwardCompatible, 
								comp.dependencies, transactionStartTime);					
						createdTypeRefs.addAll(comp.dependencies);
					}
				} else {
					if (comp.isDeletion) {
						stopFuncSupport(info, comp.name, transactionStartTime);
					} else {
						saveFunc(info, comp.name, comp.funcParsing, comp.notBackwardCompatible, comp.dependencies,
								transactionStartTime);
						createdFuncRefs.addAll(comp.dependencies);
					}
				}
			}
			writeModuleInfoSpec(moduleName, info, specDocument, transactionStartTime);
			storage.addRefs(createdTypeRefs, createdFuncRefs);
			transactionStartTime = -1;
		} catch (TypeStorageException ex) {
			throw ex;
		} catch (SpecParseException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new SpecParseException("Unexpected error during spec-file parsing: " + ex.getMessage(), ex);			
		} finally {
			try {
				if (transactionStartTime > 0) {
					rollbackModuleTransaction(moduleName, transactionStartTime);
				}
			} catch (Exception ignore) {}
		}
	}
	
	private Change findTypeChange(ModuleInfo info, KbTypedef newType) 
			throws SpecParseException, NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		if (!info.getTypes().containsKey(newType.getName()))
			return Change.notCompatible;
		TypeInfo ti = info.getTypes().get(newType.getName());
		KbTypedef oldType = getTypeParsingDocument(info.getModuleName(), ti.getTypeName(), ti.getTypeVersion());
		return findChange(oldType, newType);
	}
	
	private Change findChange(KbType oldType, KbType newType) throws SpecParseException {
		if (!oldType.getClass().equals(newType.getClass()))
			return Change.notCompatible;
		if (newType instanceof KbTypedef) {
			KbTypedef oldIType = (KbTypedef)oldType;
			KbTypedef newIType = (KbTypedef)newType;
			if (!newIType.getName().equals(oldIType.getName()))
				return Change.notCompatible;
			return findChange(oldIType.getAliasType(), newIType.getAliasType());
		} else if (newType instanceof KbList) {
			KbList oldIType = (KbList)oldType;
			KbList newIType = (KbList)newType;
			return findChange(oldIType.getElementType(), newIType.getElementType());
		} else if (newType instanceof KbMapping) {
			KbMapping oldIType = (KbMapping)oldType;
			KbMapping newIType = (KbMapping)newType;
			return findChange(oldIType.getValueType(), newIType.getValueType());
		} else if (newType instanceof KbTuple) {
			KbTuple oldIType = (KbTuple)oldType;
			KbTuple newIType = (KbTuple)newType;
			if (oldIType.getElementTypes().size() != newIType.getElementTypes().size())
				return Change.notCompatible;
			Change ret = Change.noChange;
			for (int pos = 0; pos < oldIType.getElementTypes().size(); pos++) {
				ret = Change.joinChanges(ret, findChange(oldIType.getElementTypes().get(pos), 
						newIType.getElementTypes().get(pos)));
				if (ret == Change.notCompatible)
					return ret;
			}
			return ret;
		} else if (newType instanceof KbUnspecifiedObject) {
			return Change.noChange;
		} else if (newType instanceof KbScalar) {
			KbScalar oldIType = (KbScalar)oldType;
			KbScalar newIType = (KbScalar)newType;
			if (oldIType.getScalarType() != newIType.getScalarType())
				return Change.notCompatible;
			String oldIdRefText = "" + oldIType.getIdReferences();
			String newIdRefText = "" + newIType.getIdReferences();
			return oldIdRefText.equals(newIdRefText) ? Change.noChange : Change.notCompatible;
		} else if (newType instanceof KbStruct) {
			KbStruct oldIType = (KbStruct)oldType;
			KbStruct newIType = (KbStruct)newType;
			Map<String, KbStructItem> newFields = new HashMap<String, KbStructItem>();
			for (KbStructItem item : newIType.getItems())
				newFields.put(item.getName(), item);
			Change ret = Change.noChange;
			for (KbStructItem oldItem : oldIType.getItems()) {
				if (!newFields.containsKey(oldItem.getName()))
					return Change.notCompatible;
				ret = Change.joinChanges(ret, findChange(oldItem.getItemType(), 
						newFields.get(oldItem.getName()).getItemType()));
				if (ret == Change.notCompatible)
					return ret;
				if (oldItem.isOptional() != newFields.get(oldItem.getName()).isOptional())
					return Change.notCompatible;
				newFields.remove(oldItem.getName());
			}
			for (KbStructItem newItem : newFields.values()) {
				if (!newItem.isOptional())
					return Change.notCompatible;
				ret = Change.joinChanges(ret, Change.backwardCompatible);
			}
		}
		throw new SpecParseException("Unknown type class: " + newType.getClass().getSimpleName());
	}

	private Change findFuncChange(ModuleInfo info, KbFuncdef newFunc) 
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException, SpecParseException {
		if (!info.getFuncs().containsKey(newFunc.getName()))
			return Change.notCompatible;
		FuncInfo fi = info.getFuncs().get(newFunc.getName());
		KbFuncdef oldFunc = getFuncParsingDocument(info.getModuleName(), fi.getFuncName(), fi.getFuncVersion());
		if (oldFunc.getParameters().size() != newFunc.getParameters().size() ||
				oldFunc.getReturnType().size() != newFunc.getReturnType().size())
			return Change.notCompatible;
		Change ret = Change.noChange;
		for (int pos = 0; pos < oldFunc.getParameters().size(); pos++) {
			KbParameter oldParam = oldFunc.getParameters().get(pos);
			KbParameter newParam = newFunc.getParameters().get(pos);
			ret = Change.joinChanges(ret, findChange(oldParam.getType(), newParam.getType()));
			if (ret == Change.notCompatible)
				return ret;
		}
		for (int pos = 0; pos < oldFunc.getReturnType().size(); pos++) {
			KbParameter oldRet = oldFunc.getReturnType().get(pos);
			KbParameter newRet = newFunc.getReturnType().get(pos);
			ret = Change.joinChanges(ret, findChange(oldRet.getType(), newRet.getType()));
			if (ret == Change.notCompatible)
				return ret;
		}
		return ret;
	}

	private Set<RefInfo> extractTypeRefs(KbTypedef main, Map<String, ModuleInfo> moduleToInfo,
			Set<String> mainRegisteredTypes) throws SpecParseException {
		Set<RefInfo> ret = new TreeSet<RefInfo>();
		collectTypeRefs(ret, main.getModule(), main.getName(), main.getAliasType(), moduleToInfo, mainRegisteredTypes);
		return ret;
	}

	private Set<RefInfo> extractTypeRefs(String module, String funcName, KbParameter main, 
			Map<String, ModuleInfo> moduleToInfo, Set<String> mainRegisteredTypes)
					throws SpecParseException {
		Set<RefInfo> ret = new TreeSet<RefInfo>();
		collectTypeRefs(ret, module, funcName, main.getType(), moduleToInfo, mainRegisteredTypes);
		return ret;
	}

	private void collectTypeRefs(Set<RefInfo> ret, String mainModule, String mainName, KbType internal, 
			Map<String, ModuleInfo> moduleToInfo, Set<String> mainRegisteredTypes) 
					throws SpecParseException {
		if (internal instanceof KbTypedef) {
			KbTypedef type = (KbTypedef)internal;
			boolean isOuterModule = !type.getModule().equals(mainModule);
			boolean terminal = isOuterModule || mainRegisteredTypes.contains(type.getName());
			if (terminal) {
				RefInfo ref = new RefInfo();
				ref.setDepModule(mainModule);
				ref.setDepName(mainName);
				ref.setRefModule(type.getModule());
				ref.setRefName(type.getName());
				if (isOuterModule) {
					ModuleInfo oModule = moduleToInfo.get(type.getModule());
					TypeInfo oType = null;
					if (oModule != null)
						oType = oModule.getTypes().get(type.getName());
					if (oType == null)
						throw new SpecParseException("Reference to external not registered " +
								"module/type is missing: " + type.getModule() + "." + type.getName());
					ref.setRefVersion(oType.getTypeVersion());
				}
				ret.add(ref);
			} else {
				collectTypeRefs(ret, mainModule, mainName, type.getAliasType(), moduleToInfo, mainRegisteredTypes);
			}
		} else if (internal instanceof KbList) {
			KbList type = (KbList)internal;
			collectTypeRefs(ret, mainModule, mainName, type.getElementType(), moduleToInfo, mainRegisteredTypes);
		} else if (internal instanceof KbMapping) {
			KbMapping type = (KbMapping)internal;
			collectTypeRefs(ret, mainModule, mainName, type.getValueType(), moduleToInfo, mainRegisteredTypes);
		} else if (internal instanceof KbStruct) {
			KbStruct type = (KbStruct)internal;
			for (KbStructItem item : type.getItems())
				collectTypeRefs(ret, mainModule, mainName, item.getItemType(), moduleToInfo, mainRegisteredTypes);				
		} else if (internal instanceof KbTuple) {
			KbTuple type = (KbTuple)internal;
			for (KbType iType : type.getElementTypes())
				collectTypeRefs(ret, mainModule, mainName, iType, moduleToInfo, mainRegisteredTypes);				
		}
	}
	
	private void saveIncludedModuleRecusive(File workDir, String moduleName, Map<String, ModuleInfo> savedModules) 
			throws NoSuchModuleException, IOException, TypeStorageException {
		if (savedModules.containsKey(moduleName))
			return;
		String spec = getModuleSpecDocument(moduleName);
		writeFile(spec, new File(workDir, moduleName + ".types"));
		ModuleInfo info = getModuleInfo(moduleName);
		savedModules.put(moduleName, info);
		for (String included : info.getIncludedModuleNames())
			saveIncludedModuleRecusive(workDir, included, savedModules);
	}
	
	private static void writeFile(String text, File f) throws IOException {
		FileWriter fw = new FileWriter(f);
		fw.write(text);
		fw.close();
	}
	
	private void deleteTempDir(File dir) {
		for (File f : dir.listFiles()) {
			if (f.isFile()) {
				f.delete();
			} else {
				deleteTempDir(f);
			}
		}
		dir.delete();
	}
	
	public void updateModule(String specDocument, List<String> addedTypes, List<String> unregisteredTypes,
			String userId) throws SpecParseException, TypeStorageException {
		saveModule(specDocument, false, new HashSet<String>(addedTypes), 
				new HashSet<String>(unregisteredTypes), userId);
	}
	
	public void addOwnerToModule(String knownOwnerUserId, String moduleName, String newOwnerUserId, 
			boolean withChangeOwnersPrivilege) throws TypeStorageException, NoSuchPrivilegeException {
		checkUserCanChangePrivileges(knownOwnerUserId, moduleName);
		storage.addOwnerToModule(moduleName, newOwnerUserId, withChangeOwnersPrivilege);
	}

	public void removeOwnerFromModule(String knownOwnerUserId, String moduleName, String removedOwnerUserId) 
			throws NoSuchPrivilegeException, TypeStorageException {
		checkUserCanChangePrivileges(knownOwnerUserId, moduleName);
		storage.removeOwnerFromModule(moduleName, removedOwnerUserId);
	}
	
	private void checkUserCanChangePrivileges(String knownOwnerUserId,
			String moduleName) throws NoSuchPrivilegeException, TypeStorageException {
		boolean canChangeOwnersPrivilege = checkUserIsOwnerOrAdmin(moduleName, knownOwnerUserId);
		if (!canChangeOwnersPrivilege)
			throw new NoSuchPrivilegeException("User " + knownOwnerUserId + " can not change " +
					"priviledges for module " + moduleName);
	}
	
	private static class ComponentChange {
		boolean isType;
		boolean isDeletion;
		String name;
		String jsonSchemaDocument;
		KbTypedef typeParsing;
		KbFuncdef funcParsing;
		boolean notBackwardCompatible;
		Set<RefInfo> dependencies;
		
		public ComponentChange(boolean isType, boolean isDeletion, String name, String jsonSchemaDocument, KbTypedef typeParsing,
				KbFuncdef funcParsing, boolean notBackwardCompatible, Set<RefInfo> dependencies) {
			this.isType = isType;
			this.isDeletion = isDeletion;
			this.name = name;
			this.jsonSchemaDocument = jsonSchemaDocument;
			this.typeParsing = typeParsing;
			this.funcParsing = funcParsing;
			this.notBackwardCompatible = notBackwardCompatible;
			this.dependencies = dependencies;
		}
	}
}
