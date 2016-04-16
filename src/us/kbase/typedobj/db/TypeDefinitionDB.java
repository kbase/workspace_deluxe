package us.kbase.typedobj.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import org.apache.commons.codec.digest.DigestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import us.kbase.jkidl.StaticIncludeProvider;
import us.kbase.kidl.KbAnnotationId;
import us.kbase.kidl.KbAnnotationSearch;
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
import us.kbase.typedobj.core.JsonTokenValidationSchema;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.exceptions.*;

/**
 * This class is the primary interface for storing and retrieving versioned typed
 * object definitions and association meta information.
 * 
 * @author msneddon
 * @author rsutormin
 *
 */
public class TypeDefinitionDB {

	private enum Change {
		noChange, backwardCompatible, notCompatible;
		
		public static Change joinChanges(Change c1, Change c2) {
			return Change.values()[Math.max(c1.ordinal(), c2.ordinal())];
		}
	}

	/**
	 * The Jackson ObjectMapper which can translate a raw Json Schema document to a JsonTree
	 */
	protected ObjectMapper mapper;
		
	private final TypeStorage storage;
	private final Object moduleStateLock = new Object(); 
	private final Map<String, ModuleState> moduleStates = new HashMap<String, ModuleState>();
	private final ThreadLocal<Map<String,Integer>> localReadLocks = new ThreadLocal<Map<String,Integer>>(); 
	private final LoadingCache<String, ModuleInfo> moduleInfoCache;
	private final LoadingCache<AbsoluteTypeDefId, String> typeJsonSchemaCache;
	
	private static final SemanticVersion defaultVersion = new SemanticVersion(0, 1);
	private static final SemanticVersion releaseVersion = new SemanticVersion(1, 0);
	private static final long maxDeadLockWaitTime = 120000;
	

	/**
	 * Set up a new DB pointing to the specified storage object
	 * @param storage
	 * @throws TypeStorageException 
	 */
	public TypeDefinitionDB(TypeStorage storage)
			throws TypeStorageException {
		this(storage, 100);
	}

	public TypeDefinitionDB(TypeStorage storage, int cacheSize)
			throws TypeStorageException {
		this.mapper = new ObjectMapper();
		this.storage = storage;
		moduleInfoCache = CacheBuilder.newBuilder().maximumSize(cacheSize).build(
				new CacheLoader<String, ModuleInfo>() {
					@Override
					public ModuleInfo load(String moduleName) throws TypeStorageException, NoSuchModuleException {
						if (!TypeDefinitionDB.this.storage.checkModuleExist(moduleName))
							throwNoSuchModuleException(moduleName);	
						long lastVer = TypeDefinitionDB.this.storage.getLastReleasedModuleVersion(moduleName);
						if (!TypeDefinitionDB.this.storage.checkModuleInfoRecordExist(moduleName, lastVer))
							throw new NoSuchModuleException("Module wasn't uploaded: " + moduleName);	
						if (!TypeDefinitionDB.this.storage.getModuleSupportedState(moduleName))
							throw new NoSuchModuleException("Module " + moduleName + " is no longer supported");
						return TypeDefinitionDB.this.storage.getModuleInfoRecord(moduleName, lastVer);
					}
				});
		typeJsonSchemaCache = CacheBuilder.newBuilder().maximumSize(cacheSize).build(
				new CacheLoader<AbsoluteTypeDefId, String>() {
					@Override
					public String load(AbsoluteTypeDefId typeDefId) throws TypeStorageException, NoSuchModuleException, NoSuchTypeException {
						String moduleName = typeDefId.getType().getModule();
						if (!TypeDefinitionDB.this.storage.checkModuleExist(moduleName))
							throwNoSuchModuleException(moduleName);	
						String typeName = typeDefId.getType().getName();
						SemanticVersion schemaDocumentVer = new SemanticVersion(typeDefId.getMajorVersion(), 
								typeDefId.getMinorVersion());
						String jsonSchemaDocument = TypeDefinitionDB.this.storage.getTypeSchemaRecord(
								moduleName, typeName, schemaDocumentVer.toString());
						if (jsonSchemaDocument == null)
							throw new NoSuchTypeException("Unable to read type schema record: '"+moduleName+"."+typeName+"'");
						return jsonSchemaDocument;
					}
				});
	}
	
	
	/**
	 * Retrieve a Json Schema Document for the most recent version of the typed object specified
	 * @param typeDefName
	 * @return
	 * @throws NoSuchTypeException
	 * @throws NoSuchModuleException
	 * @throws TypeStorageException
	 */
	public String getJsonSchemaDocument(final TypeDefName typeDefName) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		return getJsonSchemaDocument(new TypeDefId(typeDefName));
	}
	
	private ModuleState getModuleState(String moduleName) {
		synchronized (moduleStateLock) {
			ModuleState ret = moduleStates.get(moduleName);
			if (ret == null) {
				ret = new ModuleState();
				moduleStates.put(moduleName, ret);
			}
			return ret;
		}
	}
	
	private int getLocalReadLocks(String moduleName) {
		Map<String, Integer> map = localReadLocks.get();
		if (map == null) {
			map = new HashMap<String, Integer>();
			localReadLocks.set(map);
		}
		Integer ret = map.get(moduleName);
		if (ret == null)
			return 0;
		return ret;
	}

	private void setLocalReadLocks(String moduleName, int locks) {
		Map<String, Integer> map = localReadLocks.get();
		if (map == null) {
			map = new HashMap<String, Integer>();
			localReadLocks.set(map);
		}
		map.put(moduleName, locks);
	}

	private void requestReadLock(String moduleName) throws NoSuchModuleException, TypeStorageException {
		if (moduleInfoCache.getIfPresent(moduleName) == null) {
			if (!storage.checkModuleExist(moduleName))
				throw new NoSuchModuleException("Module doesn't exist: " + moduleName);
		}
		requestReadLockNM(moduleName);
	}
		
	private void requestReadLockNM(String moduleName) throws TypeStorageException {
		int lrl = getLocalReadLocks(moduleName);
		if (lrl == 0) {
			final ModuleState ms = getModuleState(moduleName);
			synchronized (ms) {
				long startTime = System.currentTimeMillis();
				while (ms.writerCount > 0) {
					try {
						ms.wait(10000);
					} catch (InterruptedException ignore) {}
					if (System.currentTimeMillis() - startTime > maxDeadLockWaitTime)
						throw new IllegalStateException("Looks like deadlock");
				}
				ms.readerCount++;
				//new Exception("moduleName=" + moduleName + ", readerCount=" + ms.readerCount).printStackTrace(System.out);
			}
		}
		setLocalReadLocks(moduleName, lrl + 1);
	}
	
	private void releaseReadLock(String moduleName) {
		final ModuleState ms = getModuleState(moduleName);
		int lrl = getLocalReadLocks(moduleName);
		lrl--;
		setLocalReadLocks(moduleName, lrl);
		if (lrl == 0) {
			synchronized (ms) {
				if (ms.readerCount == 0)
					throw new IllegalStateException("Can not release empty read lock");
				ms.readerCount--;
				//new Exception("moduleName=" + moduleName + ", readerCount=" + ms.readerCount).printStackTrace(System.out);
				ms.notifyAll();
			}		
		}
	}
	
	private void requestWriteLock(String moduleName) {
		final ModuleState ms = getModuleState(moduleName);
		synchronized (ms) {
			if (ms.writerCount > 0)
				throw new IllegalStateException("Concurent changes of module " + moduleName);
			ms.writerCount++;
			//new Exception("moduleName=" + moduleName + ", writerCount=" + ms.writerCount).printStackTrace(System.out);
			long startTime = System.currentTimeMillis();
			while (ms.readerCount > 0) {
				try {
					ms.wait(10000);
				} catch (InterruptedException ignore) {}
				if (System.currentTimeMillis() - startTime > maxDeadLockWaitTime) {
					ms.writerCount--;
					throw new IllegalStateException("Looks like deadlock");
				}
			}
		}
	}
	
	private void releaseWriteLock(String moduleName) {
		final ModuleState ms = getModuleState(moduleName);
		synchronized (ms) {
			if (ms.writerCount == 0)
				throw new IllegalStateException("Can not release empty write lock");
			ms.writerCount--;
			//new Exception("moduleName=" + moduleName + ", writerCount=" + ms.writerCount).printStackTrace(System.out);
			ms.notifyAll();
		}		
	}
	
	/**
	 * Retrieve a Json Schema Document for the typed object specified.  If no version numbers
	 * are indicated, the latest version is returned.  If the major version only is specified,
	 * then the latest version that is backwards compatible with the major version is returned.
	 * If exact major/minor version numbers are given, that is the exact version that is returned.
	 * @param typeDefId
	 * @return
	 * @throws NoSuchTypeException
	 * @throws NoSuchModuleException
	 * @throws TypeStorageException
	 */
	public String getJsonSchemaDocument(final TypeDefId typeDefId)
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		String moduleName = typeDefId.getType().getModule();
		requestReadLock(moduleName);
		try {
			return getJsonSchemaDocumentNL(typeDefId, null);
		} finally {
			releaseReadLock(moduleName);
		}
	}

	/**
	 * Retrieve a Json Schema Document for the typed object specified.  If no version numbers
	 * are indicated, the latest version is returned (including unreleased versions in case userId
	 * points to owner of module of requested type).  If the major version only is specified,
	 * then the latest version that is backwards compatible with the major version is returned.
	 * If exact major/minor version numbers are given, that is the exact version that is returned.
	 * @param typeDefId
	 * @param userId
	 * @return
	 * @throws NoSuchTypeException
	 * @throws NoSuchModuleException
	 * @throws TypeStorageException
	 */
	public String getJsonSchemaDocument(final TypeDefId typeDefId, String userId)
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		String moduleName = typeDefId.getType().getModule();
		requestReadLock(moduleName);
		try {
			return getJsonSchemaDocumentNL(typeDefId, userId);
		} finally {
			releaseReadLock(moduleName);
		}
	}

	private String getJsonSchemaDocumentNL(final TypeDefId typeDefId, String userId)
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		AbsoluteTypeDefId absTypeDefId = resolveTypeDefIdNL(typeDefId, 
				isOwnerOfModule(typeDefId.getType().getModule(), userId));
		String ret;
		try {
			ret = typeJsonSchemaCache.get(absTypeDefId);
		} catch (ExecutionException e) {
			if (e.getCause() != null) {
				if (e.getCause() instanceof NoSuchModuleException) {
					throw (NoSuchModuleException)e.getCause();
				} else if (e.getCause() instanceof NoSuchTypeException) {
					throw (NoSuchTypeException)e.getCause();
				} else if (e.getCause() instanceof TypeStorageException) {
					throw (TypeStorageException)e.getCause();
				} else {
					throw new TypeStorageException(e.getCause().getMessage(), e.getCause());
				}
			} else {
				throw new TypeStorageException(e.getMessage(), e);
			}
		}
		if (ret == null)
			throw new NoSuchTypeException("Unable to read type schema record for type: " + absTypeDefId.getTypeString());
		return ret;
	}
	
	private long getLastReleasedModuleVersion(String moduleName) throws TypeStorageException {
		ModuleInfo info = moduleInfoCache.getIfPresent(moduleName);
		if (info != null)
			return info.getVersionTime();
		return storage.getLastReleasedModuleVersion(moduleName);
	}

	private long findModuleVersion(ModuleDefId moduleDef) throws NoSuchModuleException, TypeStorageException {
		try {
			return findModuleVersion(moduleDef, null, false);
		} catch (NoSuchPrivilegeException e) {
			throw new IllegalStateException(e);  // It will not happen cause we use null userId.
		}
	}
	
	private long findModuleVersion(ModuleDefId moduleDef, String userId, boolean isAdmin) 
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		if (moduleDef.getVersion() == null) {
			checkModuleSupported(moduleDef.getModuleName());
			if (isOwnerOfModule(moduleDef.getModuleName(), userId))
				return getLatestModuleVersionWithUnreleased(moduleDef.getModuleName(), userId, isAdmin);
			return getLastReleasedModuleVersion(moduleDef.getModuleName());
		}
		long version = moduleDef.getVersion();
		if (!storage.checkModuleInfoRecordExist(moduleDef.getModuleName(), version))
			throw new NoSuchModuleException("There is no information about module " + moduleDef.getModuleName() + 
					" for version " + version);
		return version;
	}

	public Map<AbsoluteTypeDefId, String> getJsonSchemasForAllTypes(ModuleDefId moduleDef) 
			throws NoSuchModuleException, TypeStorageException {
		try {
			return getJsonSchemasForAllTypes(moduleDef, null, false);
		} catch (NoSuchPrivilegeException e) {
			throw new IllegalStateException(e);  // It will not happen cause we use null userId.
		}
	}
	
	public Map<AbsoluteTypeDefId, String> getJsonSchemasForAllTypes(ModuleDefId moduleDef, String userId,
			boolean isAdmin) throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		String moduleName = moduleDef.getModuleName();
		requestReadLock(moduleName);
		try {
			long moduleVersion = findModuleVersion(moduleDef, userId, isAdmin);
			ModuleInfo info = storage.getModuleInfoRecord(moduleName, moduleVersion);
			Map<AbsoluteTypeDefId, String> ret = new LinkedHashMap<AbsoluteTypeDefId, String>();
			for (TypeInfo ti : info.getTypes().values()) {
				if (!ti.isSupported())
					continue;
				String typeVersionText = ti.getTypeVersion();
				String jsonSchema = storage.getTypeSchemaRecord(moduleName, ti.getTypeName(), typeVersionText);
				SemanticVersion typeVer = new SemanticVersion(typeVersionText);
				ret.put(new AbsoluteTypeDefId(new TypeDefName(moduleName, ti.getTypeName()), 
						typeVer.getMajor(), typeVer.getMinor()), jsonSchema);
			}
			return ret;
		} finally {
			releaseReadLock(moduleName);
		}
	}
	
	/**
	 * Given a typeDefId that may not be valid or have major/minor versions defined,
	 * attempt to lookup if a specific type definition can be resolved in the database.
	 * If a specific type definition is found, it is returned; else an exception is thrown.
	 * @param typeDefId
	 * @return
	 * @throws NoSuchTypeException
	 * @throws NoSuchModuleException
	 * @throws TypeStorageException
	 */
	public AbsoluteTypeDefId resolveTypeDefId(final TypeDefId typeDefId) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		String moduleName = typeDefId.getType().getModule();
		requestReadLock(moduleName);
		try {
			return resolveTypeDefIdNL(typeDefId, false);
		} finally {
			releaseReadLock(moduleName);
		}
	}

	private AbsoluteTypeDefId resolveTypeDefIdNL(final TypeDefId typeDefId, boolean withUnreleased) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		if (typeDefId.isAbsolute() && typeDefId.getMd5() == null) {
			AbsoluteTypeDefId ret = new AbsoluteTypeDefId(typeDefId.getType(),
					typeDefId.getMajorVersion(), typeDefId.getMinorVersion());
			if (typeJsonSchemaCache.getIfPresent(ret) != null)
				return ret;
		}
		String moduleName = typeDefId.getType().getModule();
		checkModuleRegistered(moduleName);
		SemanticVersion schemaDocumentVer = findTypeVersion(typeDefId, withUnreleased);
		if (schemaDocumentVer == null) {
			if ((!typeDefId.isAbsolute()) && (!withUnreleased) && findTypeVersion(typeDefId, true) != null)
				throw new NoSuchTypeException("This type wasn't released yet and you should be an owner to access unreleased version information");
			throwNoSuchTypeException(typeDefId);
		}
		String typeName = typeDefId.getType().getName();
		AbsoluteTypeDefId ret = new AbsoluteTypeDefId(new TypeDefName(moduleName, typeName),
				schemaDocumentVer.getMajor(), schemaDocumentVer.getMinor());
		if (typeJsonSchemaCache.getIfPresent(ret) == null) {
			if (!storage.checkTypeSchemaRecordExists(moduleName,typeName,schemaDocumentVer.toString()))
				throwNoSuchTypeException(typeDefId);
		}
		return new AbsoluteTypeDefId(new TypeDefName(moduleName,typeName),schemaDocumentVer.getMajor(),schemaDocumentVer.getMinor());
	}
	
	/**
	 * Retrieve a Json Schema object that can be used for json validation for the most recent
	 * version of the typed object specified
	 * @param typeDefName
	 * @return
	 * @throws NoSuchTypeException
	 * @throws NoSuchModuleException
	 * @throws TypeStorageException
	 * @throws TypedObjectSchemaException 
	 */
	public JsonTokenValidationSchema getJsonSchema(
			final TypeDefName typeDefName)
			throws NoSuchTypeException, NoSuchModuleException,
			TypeStorageException, TypedObjectSchemaException {
		return getJsonSchema(new TypeDefId(typeDefName));
	}
	
	/**
	 * Retrieve a Json Schema objec tha can be used for json validation for the typed object specified.
	 * If no version numbers are indicated, the latest version is returned.  If the major version only
	 * is specified, then the latest version that is backwards compatible with the major version is returned.
	 * If exact major/minor version numbers are given, that is the exact version that is returned.
	 * @param typeDefId
	 * @return
	 * @throws NoSuchTypeException
	 * @throws NoSuchModuleException
	 * @throws TypeStorageException
	 * @throws TypedObjectSchemaException 
	 */
	public JsonTokenValidationSchema getJsonSchema(final TypeDefId typeDefId)
			throws NoSuchTypeException, NoSuchModuleException,
			TypeStorageException, TypedObjectSchemaException {
		String moduleName = typeDefId.getType().getModule();
		requestReadLock(moduleName);
		try {
			final String jsonSchemaDocument =
					getJsonSchemaDocumentNL(typeDefId, null);
			return JsonTokenValidationSchema.parseJsonSchema(jsonSchemaDocument);
		} finally {
			releaseReadLock(moduleName);
		}
	}
	
	/**
	 * Convert a Json Schema Document into a Json Schema object that can be used for json validation.
	 * @param jsonSchemaDocument
	 * @return
	 * @throws TypeStorageException
	 * @throws TypedObjectSchemaException 
	 */
	protected JsonTokenValidationSchema jsonSchemaFromString(String jsonSchemaDocument)
			throws TypeStorageException, TypedObjectSchemaException {
		return JsonTokenValidationSchema.parseJsonSchema(jsonSchemaDocument);
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
		return getTypeParsingDocument(new TypeDefId(type));
	}

	/**
	 * Check if module spec-file was registered at least once.
	 * @param moduleName
	 * @return true if module spec-file was registered at least once
	 * @throws TypeStorageException
	 */
	public boolean isValidModule(String moduleName) throws TypeStorageException {
		try {
			requestReadLock(moduleName);
		} catch (NoSuchModuleException ignore) {
			return false;
		}
		try {
			return isValidModuleNL(moduleName, null);
		} finally {
			releaseReadLock(moduleName);
		}
	}

	private boolean isValidModuleNL(String moduleName, Long version) throws TypeStorageException {
		if (moduleInfoCache.getIfPresent(moduleName) == null) {
			if (!storage.checkModuleExist(moduleName))
				return false;
		}
		if (version == null) {
			if (!isModuleSupported(moduleName))
				return false;
			version = getLastReleasedModuleVersion(moduleName);
		}
		return storage.checkModuleInfoRecordExist(moduleName, version) && 
				storage.checkModuleSpecRecordExist(moduleName, version);
	}
	
	private void checkModule(String moduleName, Long version) throws NoSuchModuleException, TypeStorageException {
		if (!isValidModuleNL(moduleName, version))
			throw new NoSuchModuleException("Module wasn't uploaded: " + moduleName);
	}

	private void checkModuleRegistered(String moduleName) throws NoSuchModuleException, TypeStorageException {
		if (moduleInfoCache.getIfPresent(moduleName) != null)
			return;
		if (!storage.checkModuleExist(moduleName)) 
			throwNoSuchModuleException(moduleName);
		if(!storage.checkModuleInfoRecordExist(moduleName, getLastReleasedModuleVersion(moduleName)))
			throw new NoSuchModuleException("Module wasn't uploaded: " + moduleName);
	}
	
	/**
	 * Determine if the type is registered and valid.
	 * @param typeDefName
	 * @return true if valid, false otherwise
	 * @throws TypeStorageException
	 */
	public boolean isValidType(TypeDefName typeDefName) throws TypeStorageException {
		return isValidType(new TypeDefId(typeDefName));
	}
	
	/**
	 * Determines if the type is registered and valid.  If version numbers are set, the specific version
	 * specified must also resolve to a valid type definition. 
	 * @param typeDef
	 * @return true if valid, false otherwise
	 * @throws TypeStorageException
	 */
	public boolean isValidType(TypeDefId typeDefId) throws TypeStorageException {
		String moduleName = typeDefId.getType().getModule();
		try {
			requestReadLock(moduleName);
		} catch (NoSuchModuleException e) {
			return false;
		}
		try {
			String typeName = typeDefId.getType().getName();
			if (!storage.checkModuleExist(moduleName))
				return false;
			if (!storage.checkModuleInfoRecordExist(moduleName, 
					getLastReleasedModuleVersion(moduleName)))
				return false;
			SemanticVersion ver = findTypeVersion(typeDefId, false);
			if (ver == null)
				return false;
			return storage.checkTypeSchemaRecordExists(moduleName, typeName, ver.toString());
		} finally {
			releaseReadLock(moduleName);
		}
	}

	private boolean isTypePresent(String moduleName, String typeName) throws TypeStorageException {
		ModuleInfo mi;
		try {
			mi = getModuleInfoNL(moduleName);
		} catch (NoSuchModuleException e) {
			return false;
		}
		return mi.getTypes().get(typeName) != null;
	}

	private SemanticVersion findTypeVersion(TypeDefId typeDef, boolean withUnreleased) throws TypeStorageException {
		if (typeDef.isAbsolute()) {
			if (typeDef.getMd5() != null) {
				SemanticVersion ret = null;
				for (String verText : storage.getTypeVersionsByMd5(typeDef.getType().getModule(), 
						typeDef.getType().getName(), typeDef.getMd5().getMD5())) {
					SemanticVersion version = new SemanticVersion(verText);
					if (ret == null || ret.compareTo(version) < 0)
						ret = version;
				}
				return ret;
			}
			return new SemanticVersion(typeDef.getMajorVersion(), typeDef.getMinorVersion());
		}
		if (!isModuleSupported(typeDef.getType().getModule())) {
			return null;
		}
		if (typeDef.getMajorVersion() != null) {
			Map<String, Boolean> versions = storage.getAllTypeVersions(typeDef.getType().getModule(), 
					typeDef.getType().getName());
			SemanticVersion ret = null;
			for (String verText : versions.keySet()) {
				if (versions.get(verText) || withUnreleased) {
					SemanticVersion ver = new SemanticVersion(verText);
					if (ver.getMajor() == typeDef.getMajorVersion() && 
							(ret == null || ret.compareTo(ver) < 0))
						ret = ver;
				}
			}
			return ret;
		}
		ModuleInfo mi;
		try {
			String moduleName = typeDef.getType().getModule();
			if (withUnreleased) {
				mi = getModuleInfoNL(moduleName, storage.getLastModuleVersionWithUnreleased(moduleName));
			} else {
				mi = getModuleInfoNL(typeDef.getType().getModule());
			}
		} catch (NoSuchModuleException e) {
			return null;
		}
		TypeInfo ti = mi.getTypes().get(typeDef.getType().getName());
		if (ti == null || (!ti.isSupported()) || ti.getTypeVersion() == null)
			return null;
		return new SemanticVersion(ti.getTypeVersion());
	}

	public AbsoluteTypeDefId getTypeMd5Version(TypeDefName typeDef) 
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		return getTypeMd5Version(new TypeDefId(typeDef), null);
	}
	
	public AbsoluteTypeDefId getTypeMd5Version(TypeDefId typeDef, String userId) 
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		String moduleName = typeDef.getType().getModule();
		requestReadLock(moduleName);
		try {
			SemanticVersion version = findTypeVersion(typeDef, isOwnerOfModule(moduleName, userId));
			if (version == null)
				throwNoSuchTypeException(typeDef);
			return new AbsoluteTypeDefId(typeDef.getType(),
					new MD5(storage.getTypeMd5(moduleName, 
					typeDef.getType().getName(), version.toString())));
		} finally {
			releaseReadLock(moduleName);
		}
	}

	public List<AbsoluteTypeDefId> getTypeVersionsForMd5(TypeDefId typeDef) 
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		if (typeDef.getMd5() == null)
			throw new NoSuchTypeException("MD5 part is not defined for type " + typeDef.getTypeString());
		String moduleName = typeDef.getType().getModule();
		requestReadLock(moduleName);
		try {
			List<String> versions = storage.getTypeVersionsByMd5(moduleName, typeDef.getType().getName(), 
					typeDef.getMd5().getMD5());
			List<AbsoluteTypeDefId> ret = new ArrayList<AbsoluteTypeDefId>();
			for (String ver : versions) {
				SemanticVersion sver = new SemanticVersion(ver);
				ret.add(new AbsoluteTypeDefId(typeDef.getType(), sver.getMajor(), sver.getMinor()));
			}
			return ret;
		} finally {
			releaseReadLock(moduleName);
		}
	}

	private SemanticVersion findLastTypeVersion(String moduleName, String typeName, 
			boolean withNoLongerSupported) throws TypeStorageException {
		if (!isTypePresent(moduleName, typeName))
			return null;
		ModuleInfo mi;
		try {
			mi = getModuleInfoNL(moduleName);
		} catch (NoSuchModuleException e) {
			return null;
		}
		return findLastTypeVersion(mi, typeName, withNoLongerSupported);
	}
	
	private SemanticVersion findLastTypeVersion(ModuleInfo module, String typeName, 
			boolean withNoLongerSupported) {
		TypeInfo ti = module.getTypes().get(typeName);
		if (ti == null || !(ti.isSupported() || withNoLongerSupported) || ti.getTypeVersion() == null)
			return null;
		return new SemanticVersion(ti.getTypeVersion());
	}
	
	protected void throwNoSuchModuleException(String module) throws NoSuchModuleException {
		throw new NoSuchModuleException("Module " + module + " was not initialized. For that you must request ownership of the module, and your request must be approved.");
	}
	
	protected void throwNoSuchTypeException(String moduleName, String typeName,
			String version) throws NoSuchTypeException {
		throw new NoSuchTypeException("Unable to locate type: '"+moduleName+"."+typeName+"'" + 
				(version == null ? "" : (" for version " + version)));
	}

	protected void throwNoSuchTypeException(TypeDefId typeDef) throws NoSuchTypeException {
		throw new NoSuchTypeException("Unable to locate type: " + typeDef.getTypeString());
	}

	protected void throwNoSuchFuncException(String moduleName, String funcName,
			String version) throws NoSuchFuncException {
		throw new NoSuchFuncException("Unable to locate function: '"+moduleName+"."+funcName+"'" + 
				(version == null ? "" : (" for version " + version)));
	}

	public List<String> getAllRegisteredTypes(String moduleName) 
			throws NoSuchModuleException, TypeStorageException {
		checkModuleSupported(moduleName);
		return getAllRegisteredTypes(new ModuleDefId(moduleName));
	}
	
	public List<String> getAllRegisteredTypes(ModuleDefId moduleDef) 
			throws NoSuchModuleException, TypeStorageException {
		requestReadLock(moduleDef.getModuleName());
		try {
			List<String> ret = new ArrayList<String>();
			for (TypeInfo typeInfo : getModuleInfoNL(moduleDef.getModuleName(), 
					findModuleVersion(moduleDef)).getTypes().values())
				if (typeInfo.isSupported())
					ret.add(typeInfo.getTypeName());
			return ret;
		} finally {
			releaseReadLock(moduleDef.getModuleName());
		}
	}
	
	/**
	 * Return latest version of specified type. Version has two level structure of integers 
	 * divided by dot like &lt;major&gt;.&lt;minor&gt;
	 * @param moduleName
	 * @param typeName
	 * @return latest version of specified type
	 */
	public String getLatestTypeVersion(TypeDefName type) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		String moduleName = type.getModule();
		requestReadLock(moduleName);
		try {
			checkModule(type.getModule(), null);
			SemanticVersion ret = findLastTypeVersion(type.getModule(), type.getName(), false);
			if (ret == null)
				throwNoSuchTypeException(type.getModule(), type.getName(), null);
			return ret.toString();
		} finally {
			releaseReadLock(moduleName);
		}
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
		return saveType(mi, ti, jsonSchemaDocument, specParsing, notBackwardCompatible, 
				dependencies, newModuleVersion);
	}
	
	private String saveType(ModuleInfo mi, TypeInfo ti, String jsonSchemaDocument,
			KbTypedef specParsing, boolean notBackwardCompatible, Set<RefInfo> dependencies, 
			long newModuleVersion) throws NoSuchModuleException, TypeStorageException {
		SemanticVersion version = getIncrementedVersion(mi, ti.getTypeName(),
				notBackwardCompatible);
		ti.setTypeVersion(version.toString());
		return saveType(mi, ti, jsonSchemaDocument, specParsing, dependencies, newModuleVersion);
	}

	protected SemanticVersion getIncrementedVersion(ModuleInfo mi, String typeName,
			boolean notBackwardCompatible) {
		SemanticVersion version = findLastTypeVersion(mi, typeName, true);
		if (version == null) {
			version = defaultVersion;
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
		return version;
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
		String md5 = DigestUtils.md5Hex(jsonSchemaDocument);
		storage.writeTypeSchemaRecord(mi.getModuleName(), ti.getTypeName(), ti.getTypeVersion(), 
				newModuleVersion, jsonSchemaDocument, md5);
		writeTypeParsingFile(mi.getModuleName(), ti.getTypeName(), ti.getTypeVersion(), 
				specParsing, newModuleVersion);
		return ti.getTypeVersion();
	}

	private void updateInternalRefVersion(RefInfo ri, ModuleInfo mi) {
		if (ri.getRefVersion() == null) {
			if (!ri.getRefModule().equals(mi.getModuleName()))
				throw new IllegalStateException("Type reference has no refVersion but reference " +
						"is not internal: " + ri);
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
	
	private boolean checkUserIsOwnerOrAdmin(String moduleName, String userId, boolean isAdmin) 
			throws NoSuchPrivilegeException, TypeStorageException {
		if (isAdmin)
			return true;
		Map<String, OwnerInfo> owners = storage.getOwnersForModule(moduleName);
		if (!owners.containsKey(userId))
			throw new NoSuchPrivilegeException("User " + userId + " is not in list of owners of module " + 
					moduleName);
		return owners.get(userId).isWithChangeOwnersPrivilege();
	}

	public List<String> getModuleOwners(String moduleName) throws NoSuchModuleException, TypeStorageException {
		requestReadLock(moduleName);
		try {
			checkModuleRegistered(moduleName);
			return new ArrayList<String>(storage.getOwnersForModule(moduleName).keySet());
		} finally {
			releaseReadLock(moduleName);
		}
	}

	public boolean isOwnerOfModule(String moduleName, String userId) throws NoSuchModuleException, TypeStorageException {
		if (userId == null)
			return false;
		requestReadLock(moduleName);
		try {
			checkModuleRegistered(moduleName);
			return storage.getOwnersForModule(moduleName).containsKey(userId);
		} finally {
			releaseReadLock(moduleName);
		}
	}

	/**
	 * Change major version of every registered type to 1.0 for types of version 0.x or set module releaseVersion to currentVersion.
	 * @param moduleName
	 * @param userId
	 * @return new versions of types
	 */
	public List<AbsoluteTypeDefId> releaseModule(String moduleName, String userId, boolean isAdmin)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		checkUserIsOwnerOrAdmin(moduleName, userId, isAdmin);
		checkModuleRegistered(moduleName);
		checkModuleSupported(moduleName);
		long version = storage.getLastModuleVersionWithUnreleased(moduleName);
		checkModule(moduleName, version);
		ModuleInfo info = storage.getModuleInfoRecord(moduleName, version);
		requestWriteLock(moduleName);
		try {
			Map<String, String> typesTo10 = new LinkedHashMap<String, String>();
			for (String type : info.getTypes().keySet())
				if (new SemanticVersion(info.getTypes().get(type).getTypeVersion()).getMajor() == 0)
					typesTo10.put(type, info.getTypes().get(type).getTypeVersion());
			List<String> funcsTo10 = new ArrayList<String>();
			for (String func : info.getFuncs().keySet())
				if (new SemanticVersion(info.getFuncs().get(func).getFuncVersion()).getMajor() == 0)
					funcsTo10.add(func);
			if (typesTo10.size() > 0 || funcsTo10.size() > 0) {
				info.setUploadUserId(userId);
				info.setUploadMethod("releaseModule");
				long transactionStartTime = storage.generateNewModuleVersion(moduleName);
				try {
					Set<RefInfo> newFuncRefs = new TreeSet<RefInfo>();
					Set<RefInfo> newTypeRefs = new TreeSet<RefInfo>();
					for (String typeName : typesTo10.keySet()) {
						TypeInfo ti = info.getTypes().get(typeName);
						ti.setTypeVersion(releaseVersion.toString());
					}
					for (String typeName : typesTo10.keySet()) {
						String oldTypeVer = typesTo10.get(typeName);
						String jsonSchemaDocument = storage.getTypeSchemaRecord(moduleName, typeName, oldTypeVer);
						Set<RefInfo> deps = storage.getTypeRefsByDep(moduleName, typeName, oldTypeVer);
						Set<RefInfo> usingTypes = storage.getTypeRefsByRef(moduleName, typeName, oldTypeVer);
						Set<RefInfo> usingFuncs = storage.getFuncRefsByRef(moduleName, typeName, oldTypeVer);
						try {
							KbTypedef specParsing = getTypeParsingDocumentNL(new TypeDefId(moduleName + "." + typeName, oldTypeVer), false);
							saveType(info, info.getTypes().get(typeName), jsonSchemaDocument, specParsing, deps, transactionStartTime);
							newTypeRefs.addAll(deps);
						} catch (NoSuchTypeException ex) {
							throw new IllegalStateException(ex);  // Can not occur anyways
						}
						for (RefInfo ref : usingTypes) 
							if (ref.getDepModule().equals(moduleName)) { // This reference is internal
								ref.setRefVersion(releaseVersion.toString());
								newTypeRefs.add(ref);
							}
						for (RefInfo ref : usingFuncs) 
							if (ref.getDepModule().equals(moduleName)) { // This reference is internal
								ref.setRefVersion(releaseVersion.toString());
								newFuncRefs.add(ref);
							}
					}
					for (String funcName : funcsTo10) {
						FuncInfo fi = info.getFuncs().get(funcName);
						Set<RefInfo> deps = storage.getFuncRefsByDep(moduleName, funcName, fi.getFuncVersion());
						try {
							KbFuncdef specParsing = getFuncParsingDocumentNL(moduleName, funcName, fi.getFuncVersion());
							fi.setFuncVersion(releaseVersion.toString());
							saveFunc(info, fi, specParsing, deps, transactionStartTime);
							newFuncRefs.addAll(deps);
						} catch (NoSuchFuncException ex) {
							throw new IllegalStateException(ex);  // Can not occur anyways
						}
					}
					String specDocument = storage.getModuleSpecRecord(info.getModuleName(), version);
					writeModuleInfoSpec(info, specDocument, transactionStartTime);
					storage.addRefs(newTypeRefs, newFuncRefs);
					storage.setModuleReleaseVersion(moduleName, transactionStartTime);
					transactionStartTime = -1;
				} finally {
					if (transactionStartTime > 0)
						rollbackModuleTransaction(moduleName, transactionStartTime);
				}
			} else {
				storage.setModuleReleaseVersion(moduleName, version);
			}
			removeModuleInfoFromCache(moduleName);
		} finally {
			releaseWriteLock(moduleName);
		}
		List<AbsoluteTypeDefId> ret = new ArrayList<AbsoluteTypeDefId>();
		for (TypeInfo ti : info.getTypes().values()) {
			SemanticVersion typeVersion = new SemanticVersion(ti.getTypeVersion());
			ret.add(new AbsoluteTypeDefId(new TypeDefName(moduleName, ti.getTypeName()), 
					typeVersion.getMajor(), typeVersion.getMinor()));
		}
		return ret;
	}
	
	/**
	 * Given a moduleName, a typeName and version, return the JSON Schema document for the type. If 
	 * version parameter is null (no version number is specified) then the latest version of document 
	 * will be returned.
	 * @param moduleName
	 * @param typeName
	 * @param version
	 * @return JSON Schema document as a String
	 * @throws NoSuchTypeException
	 */
	public KbTypedef getTypeParsingDocument(TypeDefId typeDef) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		String moduleName = typeDef.getType().getModule();
		requestReadLock(moduleName);
		try {
			return getTypeParsingDocumentNL(typeDef, false);
		} finally {
			releaseReadLock(moduleName);
		}
	}
		
	private KbTypedef getTypeParsingDocumentNL(TypeDefId typeDef, boolean withUnreleased) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		String moduleName = typeDef.getType().getModule();
		String typeName = typeDef.getType().getName();
		checkModuleRegistered(moduleName);
		SemanticVersion documentVer = findTypeVersion(typeDef, withUnreleased);
		if (documentVer == null)
			throwNoSuchTypeException(typeDef);
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
	
	private void rollbackModuleTransaction(String moduleName, long versionTime) {
		try {
			TreeSet<Long> allVers = new TreeSet<Long>(storage.getAllModuleVersions(moduleName).keySet());
			if (allVers.last() == versionTime) {
				allVers.remove(allVers.last());
			}
			storage.removeModuleVersionAndSwitchIfNotCurrent(moduleName, versionTime, allVers.last());
		} catch (Throwable ignore) {
			ignore.printStackTrace();
		}
	}

	private void writeModuleInfoSpec(ModuleInfo info, String specDocument, 
			long backupTime) throws TypeStorageException {
		storage.writeModuleRecords(info, specDocument, backupTime);
		removeModuleInfoFromCache(info.getModuleName());
	}

	public String getModuleSpecDocument(String moduleName) 
			throws NoSuchModuleException, TypeStorageException {
		requestReadLock(moduleName);
		try {
			checkModuleSupported(moduleName);
			return storage.getModuleSpecRecord(moduleName, getLastReleasedModuleVersion(moduleName));
		} finally {
			releaseReadLock(moduleName);
		}
	}

	public String getModuleSpecDocument(String moduleName, long version) 
			throws NoSuchModuleException, TypeStorageException {
		requestReadLock(moduleName);
		try {
			checkModule(moduleName, version);
			return storage.getModuleSpecRecord(moduleName, version);
		} finally {
			releaseReadLock(moduleName);
		}
	}

	public String getModuleSpecDocument(ModuleDefId moduleDef) 
			throws NoSuchModuleException, TypeStorageException {
		try {
			return getModuleSpecDocument(moduleDef, null, false);
		} catch (NoSuchPrivilegeException e) {
			throw new IllegalStateException(e);  // It will not happen cause we use null userId.
		}
	}
	
	public String getModuleSpecDocument(ModuleDefId moduleDef, String userId, boolean isAdmin) 
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		String moduleName = moduleDef.getModuleName();
		requestReadLock(moduleName);
		try {
			checkModuleRegistered(moduleName);
			long version = findModuleVersion(moduleDef, userId, isAdmin);
			checkModule(moduleName, version);
			return storage.getModuleSpecRecord(moduleName, version);
		} finally {
			releaseReadLock(moduleName);
		}
	}

	public void cleanupCaches() {
		moduleInfoCache.cleanUp();
		typeJsonSchemaCache.cleanUp();
	}
	
	private ModuleInfo copyOf(ModuleInfo input) throws TypeStorageException {
		ModuleInfo ret = new ModuleInfo();
		ret.setDescription(input.getDescription());
		for (Map.Entry<String, FuncInfo> entry : input.getFuncs().entrySet()) {
			FuncInfo fi = new FuncInfo();
			fi.setFuncName(entry.getValue().getFuncName());
			fi.setFuncVersion(entry.getValue().getFuncVersion());
			fi.setSupported(entry.getValue().isSupported());
			ret.getFuncs().put(entry.getKey(), fi);
		}
		ret.getIncludedModuleNameToVersion().putAll(input.getIncludedModuleNameToVersion());
		ret.setMd5hash(input.getMd5hash());
		ret.setModuleName(input.getModuleName());
		ret.setReleased(input.isReleased());
		for (Map.Entry<String, TypeInfo> entry : input.getTypes().entrySet()) {
			TypeInfo ti = new TypeInfo();
			ti.setTypeName(entry.getValue().getTypeName());
			ti.setTypeVersion(entry.getValue().getTypeVersion());
			ti.setSupported(entry.getValue().isSupported());
			ret.getTypes().put(entry.getKey(), ti);
		}
		ret.setUploadComment(input.getUploadComment());
		ret.setUploadMethod(input.getUploadMethod());
		ret.setUploadUserId(input.getUploadUserId());
		ret.setVersionTime(input.getVersionTime());
		return ret;
	}
		
	private ModuleInfo getModuleInfoNL(String moduleName) 
			throws NoSuchModuleException, TypeStorageException {
		checkModuleSupported(moduleName);
		ModuleInfo ret;
		try {
			ret = moduleInfoCache.get(moduleName);
		} catch (ExecutionException e) {
			if (e.getCause() != null) {
				if (e.getCause() instanceof NoSuchModuleException) {
					throw (NoSuchModuleException)e.getCause();
				} else if (e.getCause() instanceof TypeStorageException) {
					throw (TypeStorageException)e.getCause();
				} else {
					throw new TypeStorageException(e.getCause().getMessage(), e.getCause());
				}
			} else {
				throw new TypeStorageException(e.getMessage(), e);
			}
		}
		return copyOf(ret);
	}
	
	public ModuleInfo getModuleInfo(String moduleName) 
			throws NoSuchModuleException, TypeStorageException {
		requestReadLock(moduleName);
		try {
			return getModuleInfoNL(moduleName);
		} finally {
			releaseReadLock(moduleName);
		}
	}

	private ModuleInfo getModuleInfoNL(String moduleName, long version) 
			throws NoSuchModuleException, TypeStorageException {
		checkModuleRegistered(moduleName);
		ModuleInfo ret = moduleInfoCache.getIfPresent(moduleName);
		if (ret != null && ret.getVersionTime() == version)
			return copyOf(ret);
		return storage.getModuleInfoRecord(moduleName, version);
	}
	
	public ModuleInfo getModuleInfo(String moduleName, long version) 
			throws NoSuchModuleException, TypeStorageException {
		requestReadLock(moduleName);
		try {
			return getModuleInfoNL(moduleName, version);
		} finally {
			releaseReadLock(moduleName);
		}
	}

	public ModuleInfo getModuleInfo(ModuleDefId moduleDef) 
			throws NoSuchModuleException, TypeStorageException {
		try {
			return getModuleInfo(moduleDef, null, false);
		} catch (NoSuchPrivilegeException e) {
			throw new IllegalStateException(e);  // It will not happen cause we use null userId.
		}
	}
	
	public ModuleInfo getModuleInfo(ModuleDefId moduleDef, String userId, boolean isAdmin) 
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		String moduleName = moduleDef.getModuleName();
		requestReadLock(moduleName);
		try {
			return getModuleInfoNL(moduleName, findModuleVersion(moduleDef, userId, isAdmin));
		} finally {
			releaseReadLock(moduleName);
		}
	}

	public long getLatestModuleVersion(String moduleName) 
			throws NoSuchModuleException, TypeStorageException {
		requestReadLock(moduleName);
		try {
			checkModuleRegistered(moduleName);
			checkModuleSupported(moduleName);
			return getLastReleasedModuleVersion(moduleName);
		} finally {
			releaseReadLock(moduleName);
		}
	}
	
	public long getLatestModuleVersionWithUnreleased(String moduleName, String userId, boolean isAdmin) 
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		requestReadLock(moduleName);
		try {
			checkModuleRegistered(moduleName);
			checkUserIsOwnerOrAdmin(moduleName, userId, isAdmin);
			return storage.getLastModuleVersionWithUnreleased(moduleName);
		} finally {
			releaseReadLock(moduleName);
		}
	}
	
	public List<Long> getAllModuleVersions(String moduleName) 
			throws NoSuchModuleException, TypeStorageException {
		requestReadLock(moduleName);
		try {
			checkModuleRegistered(moduleName);
			checkModuleSupported(moduleName);
			return getAllModuleVersionsNL(moduleName, false);
		} finally {
			releaseReadLock(moduleName);
		}
	}

	public List<Long> getAllModuleVersionsWithUnreleased(String moduleName, String ownerUserId, boolean isAdmin) 
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		requestReadLock(moduleName);
		try {
			checkModuleRegistered(moduleName);
			checkModuleSupported(moduleName);
			checkUserIsOwnerOrAdmin(moduleName, ownerUserId, isAdmin);
			return getAllModuleVersionsNL(moduleName, true);
		} finally {
			releaseReadLock(moduleName);
		}
	}

	private List<Long> getAllModuleVersionsNL(String moduleName, boolean withUnreleased) throws TypeStorageException {
		TreeMap<Long, Boolean> map = storage.getAllModuleVersions(moduleName);
		List<Long> ret = new ArrayList<Long>();
		for (Map.Entry<Long, Boolean> enrty : map.entrySet())
			if ((withUnreleased || enrty.getValue()) && enrty.getKey() != map.firstKey())
				ret.add(enrty.getKey());
		return ret;
	}
	
	public List<String> getAllRegisteredFuncs(String moduleName) 
			throws NoSuchModuleException, TypeStorageException {
		requestReadLock(moduleName);
		try {
			List<String> ret = new ArrayList<String>();
			for (FuncInfo info : getModuleInfoNL(moduleName).getFuncs().values()) 
				if (info.isSupported())
					ret.add(info.getFuncName());
			return ret;
		} finally {
			releaseReadLock(moduleName);
		}
	}

	private SemanticVersion findLastFuncVersion(String moduleName, String funcName) throws TypeStorageException {
		return findLastFuncVersion(moduleName, funcName, false);
	}
	
	private SemanticVersion findLastFuncVersion(String moduleName, String funcName, boolean withUnreleased) throws TypeStorageException {
		try {
			ModuleInfo mi = withUnreleased ? getModuleInfoNL(moduleName, 
					storage.getLastModuleVersionWithUnreleased(moduleName)) : getModuleInfoNL(moduleName);
			return findLastFuncVersion(mi, funcName, false);
		} catch (NoSuchModuleException e) {
			return null;
		}
	}
		
	private SemanticVersion findLastFuncVersion(ModuleInfo mi, String funcName, 
			boolean withNotSupported) {
		FuncInfo fi = mi.getFuncs().get(funcName);
		if (fi == null || !(fi.isSupported() || withNotSupported) || fi.getFuncVersion() == null)
			return null;
		return new SemanticVersion(fi.getFuncVersion());
	}

	/**
	 * Return latest version of specified type. Version has two level structure of integers 
	 * divided by dot like &lt;major&gt;.&lt;minor&gt;
	 * @param moduleName
	 * @param funcName
	 * @return latest version of specified type
	 */
	public String getLatestFuncVersion(String moduleName, String funcName)
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException {
		requestReadLock(moduleName);
		try {
			checkModule(moduleName, null);
			SemanticVersion ret = findLastFuncVersion(moduleName, funcName);
			if (ret == null)
				throwNoSuchFuncException(moduleName, funcName, null);
			return ret.toString();
		} finally {
			releaseReadLock(moduleName);
		}
	}
	
	private String saveFunc(ModuleInfo mi, String funcName, KbFuncdef specParsingDocument, 
			boolean notBackwardCompatible, Set<RefInfo> dependencies, long newModuleVersion) 
					throws NoSuchModuleException, TypeStorageException {
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
			version = defaultVersion;
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
			Set<RefInfo> dependencies, long newModuleVersion) 
					throws NoSuchModuleException, TypeStorageException {
		if (dependencies != null)
			for (RefInfo dep : dependencies) {
				dep.setDepVersion(fi.getFuncVersion());
				dep.setDepModuleVersion(newModuleVersion);
				updateInternalRefVersion(dep, mi);
			}
		writeFuncParsingFile(mi.getModuleName(), fi.getFuncName(), fi.getFuncVersion(), 
				specParsingDocument, newModuleVersion);
		return fi.getFuncVersion();
	}
	
	private void writeFuncParsingFile(String moduleName, String funcName, String version, 
			KbFuncdef document, long newModuleVersion) 
			throws TypeStorageException {
		try {
			StringWriter sw = new StringWriter();
			mapper.writeValue(sw, document.getData());
			sw.close();
			storage.writeFuncParseRecord(moduleName, funcName, version.toString(), 
					newModuleVersion, sw.toString());
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

	public KbFuncdef getFuncParsingDocument(String moduleName, String funcName,
			String version) throws NoSuchFuncException, NoSuchModuleException, TypeStorageException {
		requestReadLock(moduleName);
		try {
			return getFuncParsingDocumentNL(moduleName, funcName, version);
		} finally {
			releaseReadLock(moduleName);
		}
	}
	
	private KbFuncdef getFuncParsingDocumentNL(String moduleName, String funcName,
			String version) throws NoSuchFuncException, NoSuchModuleException, TypeStorageException {
		checkModuleRegistered(moduleName);
		SemanticVersion curVersion = version == null ? findLastFuncVersion(moduleName, funcName) : 
			new SemanticVersion(version);
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
		ti.setSupported(false);
	}
	
	public void stopTypeSupport(TypeDefName type, String userId, String uploadComment,
			boolean isAdmin) throws NoSuchTypeException, NoSuchModuleException, 
			TypeStorageException, NoSuchPrivilegeException, SpecParseException {
		String moduleName = type.getModule();
		String typeName = type.getName();
		saveModule(getModuleSpecDocument(moduleName), Collections.<String>emptySet(), 
				new HashSet<String>(Arrays.asList(typeName)), userId, false, 
				Collections.<String,Long>emptyMap(), null, "stopTypeSupport", uploadComment, isAdmin);
	}
	
	private void stopFuncSupport(ModuleInfo info, String funcName, long newModuleVersion) 
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException {
		FuncInfo fi = info.getFuncs().get(funcName);
		if (fi == null)
			throwNoSuchFuncException(info.getModuleName(), funcName, null);
		fi.setSupported(false);
	}
	
	public void removeModule(String moduleName, String userId, boolean isAdmin) 
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		requestWriteLock(moduleName);
		try {
			checkAdmin(userId, isAdmin);
			checkModuleRegistered(moduleName);
			storage.removeModule(moduleName);
			removeModuleInfoFromCache(moduleName);
		} finally {
			releaseWriteLock(moduleName);
		}
	}
	
	/**
	 * @return all names of registered modules
	 */
	public List<String> getAllRegisteredModules() throws TypeStorageException {
		return new ArrayList<String>(storage.getAllRegisteredModules(false));
	}
	
	private String getTypeVersion(TypeDefId typeDef) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		checkModuleRegistered(typeDef.getType().getModule());
		SemanticVersion ret = findTypeVersion(typeDef, false);
		if (ret == null)
			throwNoSuchTypeException(typeDef);
		return ret.toString();
	}

	public Set<RefInfo> getTypeRefsByDep(TypeDefId depTypeDef) 
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		String depModule = depTypeDef.getType().getModule();
		requestReadLock(depModule);
		try {
			String depType = depTypeDef.getType().getName();
			String version = getTypeVersion(depTypeDef);
			return getTypeRefsByDepNL(depModule, depType, version, false);
		} finally {
			releaseReadLock(depModule);
		}
	}
	
	private Set<RefInfo> getTypeRefsByDepNL(String depModule, String depType, 
			String version, boolean withUnreleased) throws TypeStorageException {
		Set<RefInfo> set = storage.getTypeRefsByDep(depModule, depType, version);
		Set<RefInfo> ret;
		if (withUnreleased) {
			ret = set;
		} else {
			ret = new LinkedHashSet<RefInfo>();
			for (RefInfo ref : set)
				if (checkTypeReleased(ref.getRefModule(), ref.getRefName(), ref.getRefVersion()))
					ret.add(ref);
		}
		return ret;
	}
	
	public Set<RefInfo> getTypeRefsByRef(TypeDefId refTypeDef) 
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		String refModule = refTypeDef.getType().getModule();
		requestReadLock(refModule);
		try {
			String refType = refTypeDef.getType().getName();
			String version = getTypeVersion(refTypeDef);
			return getTypeRefsByRefNL(refModule, refType, version, null);
		} finally {
			releaseReadLock(refModule);
		}
	}

	private Set<RefInfo> getTypeRefsByRefNL(String refModule, String refType, 
			String version, String userId) 
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		Set<RefInfo> set = storage.getTypeRefsByRef(refModule, refType, version);
		Set<RefInfo> ret = new LinkedHashSet<RefInfo>();
		for (RefInfo ref : set) {
			boolean isOwner = isOwnerOfModule(ref.getDepModule(), userId);
			if (isOwner || checkTypeReleased(ref.getDepModule(), ref.getDepName(), 
					ref.getDepVersion()))
				ret.add(ref);
		}
		return ret;
	}
	
	public Set<RefInfo> getFuncRefsByDep(String depModule, String depFunc) 
			throws TypeStorageException, NoSuchModuleException, NoSuchFuncException {
		requestReadLock(depModule);
		try {
			checkModuleRegistered(depModule);
			checkModuleSupported(depModule);
			return getFuncRefsByDepNL(depModule, depFunc, null, false);
		} finally {
			releaseReadLock(depModule);
		}
	}

	public Set<RefInfo> getFuncRefsByDep(String depModule, String depFunc, 
			String version) throws TypeStorageException, NoSuchModuleException, NoSuchFuncException {
		requestReadLock(depModule);
		try {
			checkModuleRegistered(depModule);
			if (version == null) {
				SemanticVersion sVer = findLastFuncVersion(depModule, depFunc);
				if (sVer == null)
					throwNoSuchFuncException(depModule, depFunc, version);
				version = sVer.toString();
			}
			return getFuncRefsByDepNL(depModule, depFunc, version, false);
		} finally {
			releaseReadLock(depModule);
		}
	}
	
	private Set<RefInfo> getFuncRefsByDepNL(String depModule, String depFunc, 
			String version, boolean withUnreleased) throws TypeStorageException {
		Set<RefInfo> set = storage.getFuncRefsByDep(depModule, depFunc, version);
		Set<RefInfo> ret;
		if (withUnreleased) {
			ret = set;
		} else {
			ret = new LinkedHashSet<RefInfo>();
			for (RefInfo ref : set)
				if (checkTypeReleased(ref.getRefModule(), ref.getRefName(), ref.getRefVersion()))
					ret.add(ref);
		}
		return ret;
	}

	public Set<RefInfo> getFuncRefsByRef(TypeDefId refTypeDef, String userId) 
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		String refModule = refTypeDef.getType().getModule();
		requestReadLock(refModule);
		try {
			String refType = refTypeDef.getType().getName();
			String version = getTypeVersion(refTypeDef);
			return getFuncRefsByRefNL(refModule, refType, version, userId);
		} finally {
			releaseReadLock(refModule);
		}
	}
	
	public Set<RefInfo> getFuncRefsByRef(TypeDefId refTypeDef) 
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		String refModule = refTypeDef.getType().getModule();
		requestReadLock(refModule);
		try {
			String refType = refTypeDef.getType().getName();
			String version = getTypeVersion(refTypeDef);
			return getFuncRefsByRefNL(refModule, refType, version, null);
		} finally {
			releaseReadLock(refModule);
		}
	}
	
	private Set<RefInfo> getFuncRefsByRefNL(String refModule, String refType, 
			String version, String userId) 
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		Set<RefInfo> set = storage.getFuncRefsByRef(refModule, refType, version);
		Set<RefInfo> ret = new LinkedHashSet<RefInfo>();
		for (RefInfo ref : set) {
			boolean isOwner = isOwnerOfModule(ref.getDepModule(), userId);
			if (isOwner || checkFuncReleased(ref.getDepModule(), ref.getDepName(), 
					ref.getDepVersion()))
				ret.add(ref);
		}
		return ret;
	}
	
	private boolean checkTypeReleased(String moduleName, String typeName, String version) 
			throws TypeStorageException {
		Map<Long, Boolean> moduleVerToReleased = 
				storage.getModuleVersionsForTypeVersion(moduleName, typeName, version);
		for (boolean released : moduleVerToReleased.values())
			if (released)
				return true;
		return false;
	}

	private boolean checkFuncReleased(String moduleName, String funcName, String version) 
			throws TypeStorageException {
		Map<Long, Boolean> moduleVerToReleased = 
				storage.getModuleVersionsForFuncVersion(moduleName, funcName, version);
		for (boolean released : moduleVerToReleased.values())
			if (released)
				return true;
		return false;
	}

	public void requestModuleRegistration(String moduleName, String ownerUserId)
			throws TypeStorageException {
		TypeDefName.checkTypeName(moduleName, "Module name");
		requestReadLockNM(moduleName);
		try {
			storage.addNewModuleRegistrationRequest(moduleName, ownerUserId);
		} finally {
			releaseReadLock(moduleName);
		}
	}
	
	public List<OwnerInfo> getNewModuleRegistrationRequests(String adminUserId, boolean isAdmin) 
			throws NoSuchPrivilegeException, TypeStorageException {
		checkAdmin(adminUserId, isAdmin);
		return storage.getNewModuleRegistrationRequests();
	}

	private void checkAdmin(String adminUserId, boolean isAdmin)
			throws NoSuchPrivilegeException {
		if (!isAdmin)
			throw new NoSuchPrivilegeException("User " + adminUserId + " should be administrator");
	}
	
	public void approveModuleRegistrationRequest(String adminUserId, String newModuleName, boolean isAdmin) 
			throws TypeStorageException, NoSuchPrivilegeException {
		checkAdmin(adminUserId, isAdmin);
		requestWriteLock(newModuleName);
		try {
			String newOwnerUserId = storage.getOwnerForNewModuleRegistrationRequest(newModuleName);
			autoGenerateModuleInfo(newModuleName, newOwnerUserId);
			storage.removeNewModuleRegistrationRequest(newModuleName, newOwnerUserId);
			// TODO: send notification to e-mail of requesting user
		} finally {
			releaseWriteLock(newModuleName);
		}
	}

	public void refuseModuleRegistrationRequest(String adminUserId, String newModuleName, boolean isAdmin) 
			throws TypeStorageException, NoSuchPrivilegeException {
		checkAdmin(adminUserId, isAdmin);
		requestWriteLock(newModuleName);
		try {
			String newOwnerUserId = storage.getOwnerForNewModuleRegistrationRequest(newModuleName);
			storage.removeNewModuleRegistrationRequest(newModuleName, newOwnerUserId);
			// TODO: send notification to e-mail of requesting user
		} finally {
			releaseWriteLock(newModuleName);
		}
	}

	private void autoGenerateModuleInfo(String moduleName, String ownerUserId) throws TypeStorageException {
		if (storage.checkModuleExist(moduleName))
			throw new IllegalStateException("Module " + moduleName + " was already registered");
		ModuleInfo info = new ModuleInfo();
		info.setModuleName(moduleName);
		info.setReleased(true);
		storage.initModuleInfoRecord(info);
		storage.addOwnerToModule(moduleName, ownerUserId, true);
		storage.setModuleReleaseVersion(moduleName, info.getVersionTime());
	}

	public Map<TypeDefName, TypeChange> registerModule(String specDocument, 
			String userId) throws SpecParseException, 
			TypeStorageException, NoSuchPrivilegeException, NoSuchModuleException {
		return registerModule(specDocument, Collections.<String>emptyList(), userId);
	}

	public Map<TypeDefName, TypeChange> registerModule(String specDocument, 
			List<String> typesToSave, String userId) throws SpecParseException, 
			TypeStorageException, NoSuchPrivilegeException, NoSuchModuleException {
		return registerModule(specDocument, typesToSave, Collections.<String>emptyList(), userId);
	}
	
	public Map<TypeDefName, TypeChange> registerModule(String specDocument, 
			List<String> typesToSave, List<String> typesToUnregister, String userId) 
					throws SpecParseException, TypeStorageException, NoSuchPrivilegeException, 
					NoSuchModuleException {
		return registerModule(specDocument, typesToSave, typesToUnregister, userId, false);
	}

	public Map<TypeDefName, TypeChange> registerModule(String specDocument, 
			List<String> typesToSave, List<String> typesToUnregister, String userId, 
			boolean dryMode) throws SpecParseException, TypeStorageException, NoSuchPrivilegeException, 
			NoSuchModuleException {
		return registerModule(specDocument, typesToSave, typesToUnregister, userId, dryMode, 
				Collections.<String, Long>emptyMap());
	}

	public Map<TypeDefName, TypeChange> registerModule(String specDocument, 
			List<String> typesToSave, List<String> typesToUnregister, String userId, 
			boolean dryMode, Map<String, Long> moduleVersionRestrictions) 
					throws SpecParseException, TypeStorageException, NoSuchPrivilegeException, 
					NoSuchModuleException {
		return registerModule(specDocument, typesToSave, typesToUnregister, userId, dryMode, 
				moduleVersionRestrictions, null);
	}

	public Map<TypeDefName, TypeChange> registerModule(String specDocument, 
			List<String> typesToSave, List<String> typesToUnregister, String userId, 
			boolean dryMode, Map<String, Long> moduleVersionRestrictions, 
			Long prevModuleVersion) 
					throws SpecParseException, TypeStorageException, NoSuchPrivilegeException, 
					NoSuchModuleException {
		return registerModule(specDocument, typesToSave, typesToUnregister, userId, dryMode, 
				moduleVersionRestrictions, prevModuleVersion, "", false);
	}
	
	public Map<TypeDefName, TypeChange> registerModule(String specDocument, 
			List<String> typesToSave, List<String> typesToUnregister, String userId, 
			boolean dryMode, Map<String, Long> moduleVersionRestrictions, Long prevModuleVersion,
			String uploadComment, boolean isAdmin) throws SpecParseException, TypeStorageException, 
			NoSuchPrivilegeException, NoSuchModuleException {
		final Set<String> unreg;
		if (typesToUnregister == null) {
			unreg = new HashSet<String>();
		} else {
			unreg = new HashSet<String>(typesToUnregister);
		}
		final Set<String> save;
		if (typesToSave == null) {
			save = new HashSet<String>();
		} else {
			save = new HashSet<String>(typesToSave);
		}
		return saveModule(specDocument, save, unreg, userId, dryMode,
				moduleVersionRestrictions, prevModuleVersion, "registerModule",
				uploadComment, isAdmin);
	}

	public Map<TypeDefName, TypeChange> refreshModule(String moduleName, 
			String userId) throws SpecParseException, 
			TypeStorageException, NoSuchModuleException, NoSuchPrivilegeException {
		return refreshModule(moduleName, Collections.<String>emptyList(), userId);
	}

	public Map<TypeDefName, TypeChange> refreshModule(String moduleName, 
			List<String> typesToSave, String userId) throws SpecParseException, 
			TypeStorageException, NoSuchModuleException, NoSuchPrivilegeException {
		return refreshModule(moduleName, typesToSave, Collections.<String>emptyList(), userId);
	}
	
	public Map<TypeDefName, TypeChange> refreshModule(String moduleName, 
			List<String> typesToSave, List<String> typesToUnregister, String userId) 
					throws SpecParseException, TypeStorageException, NoSuchModuleException, 
					NoSuchPrivilegeException {
		return refreshModule(moduleName, typesToSave, typesToUnregister, userId, false);
	}

	public Map<TypeDefName, TypeChange> refreshModule(String moduleName, 
			List<String> typesToSave, List<String> typesToUnregister, String userId, 
			boolean dryMode) throws SpecParseException, TypeStorageException, NoSuchModuleException, 
			NoSuchPrivilegeException {
		return refreshModule(moduleName, typesToSave, typesToUnregister, userId, dryMode, 
				Collections.<String, Long>emptyMap());
	}

	public Map<TypeDefName, TypeChange> refreshModule(String moduleName, 
			List<String> typesToSave, List<String> typesToUnregister, String userId, 
			boolean dryMode, Map<String, Long> moduleVersionRestrictions) 
					throws SpecParseException, TypeStorageException, NoSuchModuleException, 
					NoSuchPrivilegeException {
		return refreshModule(moduleName, typesToSave, typesToUnregister, userId, dryMode, 
				moduleVersionRestrictions, "", false);
	}
	
	public Map<TypeDefName, TypeChange> refreshModule(String moduleName, 
			List<String> typesToSave, List<String> typesToUnregister, String userId, 
			boolean dryMode, Map<String, Long> moduleVersionRestrictions, String uploadComment,
			boolean isAdmin) throws SpecParseException, TypeStorageException, NoSuchModuleException, 
			NoSuchPrivilegeException {
		String specDocument = getModuleSpecDocument(moduleName, 
				storage.getLastModuleVersionWithUnreleased(moduleName));
		return saveModule(specDocument, new HashSet<String>(typesToSave), 
				new HashSet<String>(typesToUnregister), userId, dryMode, moduleVersionRestrictions, 
				null, "refreshModule", uploadComment, isAdmin);
	}

	private String correctSpecIncludes(String specDocument, List<String> includedModules) 
			throws SpecParseException {
		try {
			StringWriter withGoodImports = new StringWriter();
			PrintWriter pw = null; // new PrintWriter(withGoodImports);
			BufferedReader br = new BufferedReader(new StringReader(specDocument));
			List<String> headerLines = new ArrayList<String>();
			while (true) {
				String l = br.readLine();
				if (l == null)
					break;
				if (pw == null) {
					if (l.trim().isEmpty()) {
						headerLines.add("");
						continue;
					}
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
						headerLines.add("#include <" + l + ".types>");
					} else {
						pw = new PrintWriter(withGoodImports);
						for (String hl : headerLines)
							pw.println(hl);
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
			Map<String, ModuleInfo> moduleToInfo, Map<String, Long> moduleVersionRestrictions) 
					throws SpecParseException, NoSuchModuleException {
		try {
			Map<String, IncludeDependentPath> moduleToPath = new HashMap<String, IncludeDependentPath>();
			StaticIncludeProvider sip = new StaticIncludeProvider();
			for (String iModule : includedModules) {
				Long iVersion = moduleVersionRestrictions.get(iModule);
				if (iVersion == null)
					iVersion = getLatestModuleVersion(iModule);
				saveIncludedModuleRecusive(new IncludeDependentPath(), iModule, iVersion, 
						moduleToPath, moduleVersionRestrictions, sip);
			}
			for (IncludeDependentPath path : moduleToPath.values())
				moduleToInfo.put(path.info.getModuleName(), path.info);
			StringReader r = new StringReader(specDocument);
			Map<?,?> parseMap = KidlParser.parseSpecInt(r, moduleToTypeToSchema, sip);
			List<KbService> services = KidlParser.parseSpec(parseMap);
			if (services.size() != 1)
				throw new SpecParseException("Spec-file should consist of only one service");
			if (services.get(0).getModules().size() != 1)
				throw new SpecParseException("Spec-file should consist of only one module");
			return services.get(0).getModules().get(0);
		} catch (NoSuchModuleException ex) {
			throw ex;
		} catch (SpecParseException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new SpecParseException("Unexpected error during spec-file parsing: " + ex.getMessage(), ex);			
		}
	}

	private Map<TypeDefName, TypeChange> saveModule(String specDocument, 
			Set<String> addedTypes, Set<String> unregisteredTypes, String userId, 
			boolean dryMode, Map<String, Long> moduleVersionRestrictions, Long prevModuleVersion,
			String uploadMethod, String uploadComment, boolean isAdmin) throws SpecParseException, TypeStorageException, 
			NoSuchPrivilegeException, NoSuchModuleException {
		List<String> includedModules = new ArrayList<String>();
		specDocument = correctSpecIncludes(specDocument, includedModules);
		long transactionStartTime = -1;
		Map<String, Map<String, String>> moduleToTypeToSchema = new HashMap<String, Map<String, String>>();
		Map<String, ModuleInfo> moduleToInfo = new HashMap<String, ModuleInfo>();
		KbModule module = compileSpecFile(specDocument, includedModules, moduleToTypeToSchema, moduleToInfo, 
				moduleVersionRestrictions);
		final String moduleName = module.getModuleName();
		/* There's not really any way to test the next 11 lines.
		 * Module name requests check for bad module names.
		 * The Perl TC chokes on type names > 250 chars so any test with
		 * type names > than that fails (if this is fixed or we stop running
		 * 'both' type tests then add the test back).
		 * The TCs should catch missing names or bad characters and throw an
		 * exception before this point.
		 * That being said, it's not bad to have a safeguard here. 
		 */
		//TODO NOW TYPEDB add tests for this after removing 'both' type tests 
		TypeDefName.checkTypeName(moduleName, "Module name");
		for (final KbModuleComp comp: module.getModuleComponents()){
			if (comp instanceof KbTypedef) {
				TypeDefName.checkTypeName(((KbTypedef) comp).getName(),
						"Type name");
			}
			if (comp instanceof KbFuncdef) {
				TypeDefName.checkTypeName(((KbFuncdef) comp).getName(),
						"Function name");
			}
		}
		
		checkModuleRegistered(moduleName);
		checkModuleSupported(moduleName);
		checkUserIsOwnerOrAdmin(moduleName, userId, isAdmin);
		long realPrevVersion = storage.getLastModuleVersionWithUnreleased(moduleName);
		if (prevModuleVersion != null) {
			if (realPrevVersion != prevModuleVersion)
				throw new SpecParseException("Concurrent modification: previous module version is " + 
						realPrevVersion + " (but should be " + prevModuleVersion + ")");
		}
		requestWriteLock(moduleName);
		try {
			try {
				ModuleInfo info = getModuleInfoNL(moduleName, realPrevVersion);
				boolean isNew = !storage.checkModuleSpecRecordExist(moduleName, info.getVersionTime());
				String prevMd5 = info.getMd5hash();
				info.setMd5hash(DigestUtils.md5Hex(mapper.writeValueAsString(module.getData())));
				info.setDescription(module.getComment());
				Map<String, Long> includedModuleNameToVersion = new LinkedHashMap<String, Long>();
				for (String iModule : includedModules)
					includedModuleNameToVersion.put(iModule, moduleToInfo.get(iModule).getVersionTime());
				Map<String, Long> prevIncludes = info.getIncludedModuleNameToVersion();
				info.setIncludedModuleNameToVersion(includedModuleNameToVersion);
				info.setUploadUserId(userId);
				info.setUploadMethod(uploadMethod);
				info.setUploadComment(uploadComment == null ? "" : uploadComment);
				info.setReleased(false);
				Map<String, String> typeToSchema = moduleToTypeToSchema.get(moduleName);
				if (typeToSchema == null) {
					typeToSchema = Collections.<String, String>emptyMap();
				}
				Set<String> oldRegisteredTypes = new HashSet<String>();
				Set<String> oldRegisteredFuncs = new HashSet<String>();
				if (!isNew) {
					for (TypeInfo typeInfo : info.getTypes().values())
						if (typeInfo.isSupported())
							oldRegisteredTypes.add(typeInfo.getTypeName());
					for (FuncInfo funcInfo : info.getFuncs().values()) 
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
				Map<TypeDefName, TypeChange> ret = new LinkedHashMap<TypeDefName, TypeChange>();
				for (KbModuleComp comp : module.getModuleComponents()) {
					if (comp instanceof KbTypedef) {
						KbTypedef type = (KbTypedef)comp;
						allNewTypes.add(type.getName());
						if (newRegisteredTypes.contains(type.getName())) {
							String jsonSchemaDocument = typeToSchema.get(type.getName());
							if (jsonSchemaDocument == null)
								throw new SpecParseException("Json schema wasn't generated for type: " + type.getName());
							Change change = findTypeChange(info, type);
							if (change == Change.noChange) {
								String prevJsonSchema = storage.getTypeSchemaRecord(moduleName, type.getName(), 
										info.getTypes().get(type.getName()).getTypeVersion());
								if (jsonSchemaDocument.trim().equals(prevJsonSchema.trim())) {
									continue;
								}
								change = Change.backwardCompatible;
							}
							Set<RefInfo> dependencies = extractTypeRefs(type, moduleToInfo, newRegisteredTypes);
							jsonSchemaFromString(jsonSchemaDocument);
							boolean notBackwardCompatible = (change == Change.notCompatible);
							comps.add(new ComponentChange(true, false, type.getName(), jsonSchemaDocument, type, null, 
									notBackwardCompatible, dependencies));
							TypeDefName typeDefName = new TypeDefName(info.getModuleName(), type.getName());
							SemanticVersion newVer = getIncrementedVersion(info, type.getName(), notBackwardCompatible);
							ret.put(typeDefName, new TypeChange(false, new AbsoluteTypeDefId(typeDefName, newVer.getMajor(), 
									newVer.getMinor()), jsonSchemaDocument));
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
					TypeDefName typeDefName = new TypeDefName(info.getModuleName(), typeName);
					ret.put(typeDefName, new TypeChange(true, null, null));
				}
				for (String funcName : oldRegisteredFuncs) {
					if (!allNewFuncs.contains(funcName)) {
						comps.add(new ComponentChange(false, true, funcName, null, null, null, false, null));
					}
				}
				if (prevMd5 != null && prevMd5.equals(info.getMd5hash()) && prevIncludes.isEmpty() && 
						info.getIncludedModuleNameToVersion().isEmpty() && comps.isEmpty()) {
					String prevSpec = storage.getModuleSpecRecord(moduleName, info.getVersionTime());
					if (prevSpec.equals(specDocument))
						throw new SpecParseException("There is no difference between previous and current versions of " +
								"module " + moduleName);
				}
				if (!dryMode) {
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
					writeModuleInfoSpec(info, specDocument, transactionStartTime);
					storage.addRefs(createdTypeRefs, createdFuncRefs);
					transactionStartTime = -1;
				}
				return ret;
			} catch (NoSuchModuleException ex) {
				throw ex;
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
		} finally {
			releaseWriteLock(moduleName);
		}
	}
	
	private Change findTypeChange(ModuleInfo info, KbTypedef newType) 
			throws SpecParseException, NoSuchTypeException, NoSuchModuleException, TypeStorageException, JsonProcessingException {
		if (!info.getTypes().containsKey(newType.getName()))
			return Change.notCompatible;
		TypeInfo ti = info.getTypes().get(newType.getName());
		KbTypedef oldType = getTypeParsingDocumentNL(new TypeDefId(info.getModuleName() + "." + ti.getTypeName(), 
				ti.getTypeVersion()), false);
		KbAnnotationSearch oldAnnSWS = oldType.getAnnotations() == null ? null : 
			oldType.getAnnotations().getSearchable();
		String oldAnnSWSSchema = oldAnnSWS == null ? "" : mapper.writeValueAsString(oldAnnSWS.toJson());
		KbAnnotationSearch newAnnSWS = newType.getAnnotations() == null ? null : 
			newType.getAnnotations().getSearchable();
		String newAnnSWSSchema = newAnnSWS == null ? "" : mapper.writeValueAsString(newAnnSWS.toJson());
		if (!oldAnnSWSSchema.equals(newAnnSWSSchema)) {
			//System.out.println("TypeDefinitionDB: oldSWS: " + oldAnnSWSSchema);
			//System.out.println("TypeDefinitionDB: newSWS: " + newAnnSWSSchema);
			return Change.notCompatible;
		}
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
			KbType oldAliasType = oldIType.getAliasType();
			KbType newAliasType = newIType.getAliasType();
			if (oldAliasType instanceof KbScalar && newAliasType instanceof KbScalar &&
					((KbScalar)oldAliasType).getScalarType() == KbScalar.Type.stringType &&
					((KbScalar)newAliasType).getScalarType() == KbScalar.Type.stringType) {
				KbAnnotationId oldAnnId = oldIType.getAnnotations() == null ? null : 
					oldIType.getAnnotations().getIdReference();
				String oldAnnIdSchema = oldAnnId == null ? "" : (oldAnnId.getType() + ", " + 
					oldAnnId.getAttributes());
				KbAnnotationId newAnnId = newIType.getAnnotations() == null ? null : 
					newIType.getAnnotations().getIdReference();
				String newAnnIdSchema = newAnnId == null ? "" : (newAnnId.getType() + ", " + 
						newAnnId.getAttributes());
				if (!oldAnnIdSchema.equals(newAnnIdSchema)) {
					//System.out.println("TypeDefinitionDB: oldref: " + oldAnnIdSchema);
					//System.out.println("TypeDefinitionDB: newref: " + newAnnIdSchema);
					return Change.notCompatible;
				}
			}
			return findChange(oldAliasType, newAliasType);
		} else if (newType instanceof KbList) {
			KbList oldIType = (KbList)oldType;
			KbList newIType = (KbList)newType;
			return findChange(oldIType.getElementType(), newIType.getElementType());
		} else if (newType instanceof KbMapping) {
			KbMapping oldIType = (KbMapping)oldType;
			KbMapping newIType = (KbMapping)newType;
			Change keyChange = findChange(oldIType.getKeyType(), newIType.getKeyType());
			if (keyChange == Change.notCompatible)
				return keyChange;
			Change valueChange = findChange(oldIType.getValueType(), newIType.getValueType());
			return Change.joinChanges(keyChange, valueChange);
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
			return Change.noChange;
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
			return ret;
		}
		throw new SpecParseException("Unknown type class: " + newType.getClass().getSimpleName());
	}

	private Change findFuncChange(ModuleInfo info, KbFuncdef newFunc) 
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException, SpecParseException {
		if (!info.getFuncs().containsKey(newFunc.getName())) {
			return Change.notCompatible;
		}
		FuncInfo fi = info.getFuncs().get(newFunc.getName());
		KbFuncdef oldFunc = getFuncParsingDocumentNL(info.getModuleName(), fi.getFuncName(), fi.getFuncVersion());
		if (oldFunc.getParameters().size() != newFunc.getParameters().size() ||
				oldFunc.getReturnType().size() != newFunc.getReturnType().size()) {
			return Change.notCompatible;
		}
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
	
	private void saveIncludedModuleRecusive(IncludeDependentPath parent, 
			String moduleName, long version, Map<String, IncludeDependentPath> savedModules, 
			Map<String, Long> moduleVersionRestrictions, StaticIncludeProvider sip) 
			throws NoSuchModuleException, IOException, TypeStorageException, SpecParseException {
		ModuleInfo info = getModuleInfoNL(moduleName, version);
		IncludeDependentPath currentPath = new IncludeDependentPath(info, parent);
		Long restriction = moduleVersionRestrictions.get(moduleName);
		if (restriction != null && version != restriction) 
			throw new SpecParseException("Version of dependent module " + currentPath + " " +
					"is not compatible with module version restriction: " + restriction);
		if (savedModules.containsKey(moduleName)) {
			IncludeDependentPath alreadyPath = savedModules.get(moduleName);
			if (alreadyPath.info.getVersionTime() != currentPath.info.getVersionTime())
				throw new SpecParseException("Incompatible module dependecies: " + alreadyPath + 
						" and " + currentPath);
			return;
		}
		String spec = getModuleSpecDocument(moduleName, version);
		if (sip != null)
			sip.addSpecFile(moduleName, spec);
		savedModules.put(moduleName, currentPath);
		for (Map.Entry<String, Long> entry : info.getIncludedModuleNameToVersion().entrySet()) {
			String includedModule = entry.getKey();
			long includedVersion = entry.getValue();
			saveIncludedModuleRecusive(currentPath, includedModule, includedVersion, 
					savedModules, moduleVersionRestrictions, sip);
		}
	}
	
	public void addOwnerToModule(String knownOwnerUserId, String moduleName, String newOwnerUserId, 
			boolean withChangeOwnersPrivilege, boolean isAdmin) throws TypeStorageException, NoSuchPrivilegeException {
		checkUserCanChangePrivileges(knownOwnerUserId, moduleName, isAdmin);
		storage.addOwnerToModule(moduleName, newOwnerUserId, withChangeOwnersPrivilege);
	}

	public void removeOwnerFromModule(String knownOwnerUserId, String moduleName, String removedOwnerUserId,
			boolean isAdmin) throws NoSuchPrivilegeException, TypeStorageException {
		checkUserCanChangePrivileges(knownOwnerUserId, moduleName, isAdmin);
		storage.removeOwnerFromModule(moduleName, removedOwnerUserId);
	}
	
	private void checkUserCanChangePrivileges(String knownOwnerUserId,
			String moduleName, boolean isAdmin) throws NoSuchPrivilegeException, TypeStorageException {
		boolean canChangeOwnersPrivilege = checkUserIsOwnerOrAdmin(moduleName, knownOwnerUserId, isAdmin);
		if (!canChangeOwnersPrivilege)
			throw new NoSuchPrivilegeException("User " + knownOwnerUserId + " can not change " +
					"privileges for module " + moduleName);
	}
	
	public String getModuleDescription(String moduleName) 
			throws TypeStorageException, NoSuchModuleException {
		return getModuleInfo(moduleName).getDescription();
	}

	public String getModuleDescription(String moduleName, long version) 
			throws TypeStorageException, NoSuchModuleException {
		return getModuleInfo(moduleName, version).getDescription();
	}

	public String getModuleDescription(ModuleDefId moduleDef) 
			throws TypeStorageException, NoSuchModuleException {
		return getModuleInfo(moduleDef).getDescription();
	}

	public String getTypeDescription(TypeDefId typeDef) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		return getTypeParsingDocument(typeDef).getComment();
	}
	
	public String getFuncDescription(String moduleName, String funcName, String version) 
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException {
		return getFuncParsingDocument(moduleName, funcName, version).getComment();
	}

	public String getModuleMD5(String moduleName) 
			throws NoSuchModuleException, TypeStorageException {
		return getModuleInfo(moduleName).getMd5hash();
	}

	public String getModuleMD5(String moduleName, long version) 
			throws TypeStorageException, NoSuchModuleException {
		return getModuleInfo(moduleName, version).getMd5hash();
	}
	
	public String getModuleMD5(ModuleDefId moduleDef) 
			throws NoSuchModuleException, TypeStorageException {
		return getModuleInfo(moduleDef).getMd5hash();
	}
	
	public Set<ModuleDefId> findModuleVersionsByMD5(String moduleName, String md5) 
			throws NoSuchModuleException, TypeStorageException {
		requestReadLock(moduleName);
		try {
			Set<ModuleDefId> ret = new LinkedHashSet<ModuleDefId>();
			for (long version : getAllModuleVersionsNL(moduleName, false)) {
				ModuleInfo info = getModuleInfoNL(moduleName, version);
				if (md5.equals(info.getMd5hash()))
					ret.add(new ModuleDefId(moduleName, version));
			}
			return ret;
		} finally {
			releaseReadLock(moduleName);
		}
	}

	public List<ModuleDefId> findModuleVersionsByTypeVersion(TypeDefId typeDef, String userId) 
			throws NoSuchModuleException, TypeStorageException, NoSuchTypeException {
		String moduleName = typeDef.getType().getModule();
		requestReadLock(moduleName);
		try {
			Map<Long, Boolean> verReleased = findModuleVersionsByTypeVersionNL(typeDef, isOwnerOfModule(moduleName, userId));
			List<ModuleDefId> ret = new ArrayList<ModuleDefId>();
			for (long moduleVersion : verReleased.keySet()) 
				ret.add(new ModuleDefId(moduleName, moduleVersion));
			return ret;
		} finally {
			releaseReadLock(moduleName);
		}
	}
	
	public Map<Long, Boolean> findModuleVersionsByTypeVersionNL(TypeDefId typeDef, boolean isOwner) 
			throws NoSuchModuleException, TypeStorageException, NoSuchTypeException {
		String moduleName = typeDef.getType().getModule();
		boolean withUnreleased = typeDef.isAbsolute() || isOwner;
		typeDef = resolveTypeDefIdNL(typeDef, isOwner);
		Map<Long, Boolean> ret = new TreeMap<Long, Boolean>();
		Map<Long, Boolean> moduleVersions = storage.getModuleVersionsForTypeVersion(moduleName, 
				typeDef.getType().getName(), typeDef.getVerString());
		if (withUnreleased && !isOwner) {
			for (boolean isReleased : moduleVersions.values()) 
				if (isReleased) {
					withUnreleased = false;
					break;
				}
		}
		for (long moduleVersion : moduleVersions.keySet()) {
			boolean isReleased = moduleVersions.get(moduleVersion);
			if (withUnreleased || isReleased)
				ret.put(moduleVersion, isReleased);
		}
		return ret;
	}

	private Map<Long, Boolean> findModuleVersionsByFuncVersionNL(String moduleName, 
			String funcName, String version) 
			throws NoSuchModuleException, TypeStorageException, NoSuchFuncException {
		boolean withUnreleased = version != null;
		if (version == null) {
			SemanticVersion sv = findLastFuncVersion(moduleName, funcName);
			if (sv == null)
				throwNoSuchFuncException(moduleName, funcName, null);
			version = sv.toString();
		}
		Map<Long, Boolean> ret = new TreeMap<Long, Boolean>();
		Map<Long, Boolean> moduleVersions = storage.getModuleVersionsForFuncVersion(
				moduleName, funcName, version);
		if (withUnreleased) {
			for (boolean isReleased : moduleVersions.values()) 
				if (isReleased) {
					withUnreleased = false;
					break;
				}
		}
		for (long moduleVersion : moduleVersions.keySet()) {
			boolean isReleased = moduleVersions.get(moduleVersion);
			if (withUnreleased || isReleased)
				ret.put(moduleVersion, isReleased);
		}
		return ret;
	}

	public List<String> getModulesByOwner(String userId) throws TypeStorageException {
		return filterNotsupportedModules(storage.getModulesForOwner(userId).keySet());
	}

	private List<String> filterNotsupportedModules(Collection<String> input) throws TypeStorageException {
		List<String> ret = new ArrayList<String>();
		Set<String> supported = new HashSet<String>(storage.getAllRegisteredModules(false));
		for (String mod : input)
			if (supported.contains(mod))
				ret.add(mod);
		return ret;
	}
	
	private void checkModuleSupported(String moduleName) 
			throws TypeStorageException, NoSuchModuleException {
		if (!isModuleSupported(moduleName))
			throw new NoSuchModuleException("Module " + moduleName + " is no longer supported");
	}
	
	private boolean isModuleSupported(String moduleName) throws TypeStorageException {
		if (moduleInfoCache.getIfPresent(moduleName) != null)
			return true;
		return storage.getModuleSupportedState(moduleName);
	}
	
	public void stopModuleSupport(String moduleName, String userId, boolean isAdmin) 
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		checkModuleRegistered(moduleName);
		checkAdmin(userId, isAdmin);
		requestWriteLock(moduleName);
		try {
			storage.changeModuleSupportedState(moduleName, false);
			removeModuleInfoFromCache(moduleName);
		} finally {
			releaseWriteLock(moduleName);
		}
	}
	
	public void resumeModuleSupport(String moduleName, String userId, boolean isAdmin)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		checkModuleRegistered(moduleName);
		checkAdmin(userId, isAdmin);
		requestWriteLock(moduleName);
		try {
			storage.changeModuleSupportedState(moduleName, true);
		} finally {
			releaseWriteLock(moduleName);
		}
	}
	
	private void removeModuleInfoFromCache(String moduleName) {
		moduleInfoCache.invalidate(moduleName);		
	}
	
	public TypeDetailedInfo getTypeDetailedInfo(TypeDefId typeDef, boolean markLinksInSpec, String userId) 
			throws NoSuchModuleException, TypeStorageException, NoSuchTypeException {
		String moduleName = typeDef.getType().getModule();
		requestReadLock(moduleName);
		try {
			boolean isOwner = isOwnerOfModule(moduleName, userId);
			typeDef = resolveTypeDefIdNL(typeDef, isOwner);
			KbTypedef parsing = getTypeParsingDocumentNL(typeDef, isOwner);
			String description = parsing.getComment();
			String typeName = typeDef.getType().getName();
			Map<Long, Boolean> moduleVerRelease = findModuleVersionsByTypeVersionNL(typeDef, isOwner);
			Map<String, ModuleInfo> infoMap = getModuleInfoWithIncluded(typeDef.getType().getModule(),
					moduleVerRelease.keySet().iterator().next());
			Map<String, String> localUnregTypeToSpec = new LinkedHashMap<String, String>();
			String specDef = "typedef " + getTypeSpecText(moduleName, parsing.getAliasType(), infoMap, 
					localUnregTypeToSpec, markLinksInSpec) + " " + typeName + ";";
			specDef = combineLocalUnregParts(description, specDef, localUnregTypeToSpec);
			List<Long> moduleVersions = new ArrayList<Long>(moduleVerRelease.keySet());
			List<Long> releasedModuleVersions = new ArrayList<Long>();
			for (long moduleVer : moduleVerRelease.keySet())
				if (moduleVerRelease.get(moduleVer))
					releasedModuleVersions.add(moduleVer);
			Map<String, Boolean> semanticToReleased = 
					storage.getAllTypeVersions(moduleName, typeName);
			List<String> typeVersions = new ArrayList<String>();
			List<String> releasedTypeVersions = new ArrayList<String>();
			for (String semantic : semanticToReleased.keySet()) {
				String typeVer = new TypeDefId(typeDef.getType().getTypeString(), semantic).getTypeString();
				if (semanticToReleased.get(semantic) || isOwner)
					typeVersions.add(typeVer);
				if (semanticToReleased.get(semantic))
					releasedTypeVersions.add(typeVer);
			}
			String typeVer = typeDef.getVerString();
			Set<RefInfo> funcRefs = getFuncRefsByRefNL(moduleName, typeName, typeVer, userId);
			List<String> usingFuncDefIds = new ArrayList<String>();
			for (RefInfo ref : funcRefs)
				usingFuncDefIds.add(ref.getDepModule() + "." + ref.getDepName() + "-" + ref.getDepVersion());
			Set<RefInfo> usingRefs = getTypeRefsByRefNL(moduleName, typeName, typeVer, userId);
			List<String> usingTypeDefIds = new ArrayList<String>();
			for (RefInfo ref : usingRefs)
				usingTypeDefIds.add(ref.getDepModule() + "." + ref.getDepName() + "-" + ref.getDepVersion());
			Set<RefInfo> usedRefs = getTypeRefsByDepNL(moduleName, typeName, typeVer, isOwner);
			List<String> usedTypeDefIds = new ArrayList<String>();
			for (RefInfo ref : usedRefs)
				usedTypeDefIds.add(ref.getRefModule() + "." + ref.getRefName() + "-" + ref.getRefVersion());
			String jsonSchema = getJsonSchemaDocumentNL(typeDef, userId);
			String parsingStructure = mapper.writeValueAsString(parsing.toJson());
			return new TypeDetailedInfo(typeDef.getTypeString(), description, specDef, jsonSchema, 
					parsingStructure, moduleVersions, releasedModuleVersions, typeVersions, 
					releasedTypeVersions, usingFuncDefIds, usingTypeDefIds, usedTypeDefIds);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Unxpected error", e);
		} finally {
			releaseReadLock(moduleName);
		}
	}

	public FuncDetailedInfo getFuncDetailedInfo(String moduleName, String funcName, 
			String version, boolean markLinksInSpec, String userId) 
			throws NoSuchModuleException, TypeStorageException, NoSuchFuncException {
		requestReadLock(moduleName);
		try {
			boolean isOwner = isOwnerOfModule(moduleName, userId);
			if (version == null) {
				SemanticVersion sv = findLastFuncVersion(moduleName, funcName, isOwner);
				if (sv == null) {
					if ((!isOwner) && findLastFuncVersion(moduleName, funcName, true) != null)
						throw new NoSuchFuncException("This function wasn't released yet and you should be an owner to access unreleased version information");
					throwNoSuchFuncException(moduleName, funcName, null);
				}
				version = sv.toString();
			}
			Map<Long, Boolean> moduleVerRelease = findModuleVersionsByFuncVersionNL(moduleName, funcName, version);
			KbFuncdef parsing = getFuncParsingDocumentNL(moduleName, funcName, version);
			String description = parsing.getComment();
			parsing.getParameters();
			Map<String, ModuleInfo> infoMap = getModuleInfoWithIncluded(
					moduleName, moduleVerRelease.keySet().iterator().next());
			Map<String, String> localUnregTypeToSpec = new LinkedHashMap<String, String>();
			String specDef = "funcdef " + funcName + "(" + getParamsSpecText(moduleName, 
					parsing.getParameters(), infoMap, localUnregTypeToSpec, markLinksInSpec) + ") " +
					"returns (" + getParamsSpecText(moduleName, parsing.getReturnType(), 
							infoMap, localUnregTypeToSpec, markLinksInSpec) + ") " +
					"authentication " + parsing.getAuthentication() + ";";
			specDef = combineLocalUnregParts(description, specDef, localUnregTypeToSpec);
			List<Long> moduleVersions = new ArrayList<Long>(moduleVerRelease.keySet());
			List<Long> releasedModuleVersions = new ArrayList<Long>();
			for (long moduleVer : moduleVerRelease.keySet())
				if (moduleVerRelease.get(moduleVer))
				releasedModuleVersions.add(moduleVer);
			Map<String, Boolean> semanticToReleased = 
					storage.getAllFuncVersions(moduleName, funcName);
			List<String> funcVersions = new ArrayList<String>();
			List<String> releasedFuncVersions = new ArrayList<String>();
			for (String semantic : semanticToReleased.keySet()) {
				if (semanticToReleased.get(semantic) || isOwner)
					funcVersions.add(moduleName + "." + funcName + "-" + semantic);
				if (semanticToReleased.get(semantic))
					releasedFuncVersions.add(moduleName + "." + funcName + "-" + semantic);
			}
			Set<RefInfo> usedRefs = getFuncRefsByDepNL(moduleName, funcName, version, isOwner);
			List<String> usedTypeDefIds = new ArrayList<String>();
			for (RefInfo ref : usedRefs)
				usedTypeDefIds.add(ref.getRefModule() + "." + ref.getRefName() + "-" + ref.getRefVersion());
			String parsingStructure = mapper.writeValueAsString(parsing.toJson());
			return new FuncDetailedInfo(moduleName + "." + funcName + "-" + version, 
					description, specDef, parsingStructure, moduleVersions, releasedModuleVersions, 
					funcVersions, releasedFuncVersions, usedTypeDefIds);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Unxpected error", e);
		} finally {
			releaseReadLock(moduleName);
		}
	}

	private String combineLocalUnregParts(String comment, String spec, Map<String, String> localUnregParts) {
		StringBuilder ret = new StringBuilder();
		for (String part : localUnregParts.values()) {
			ret.append(part).append("\n\n");
		}
		if (comment != null && comment.trim().length() > 0)
			ret.append("/*\n").append(comment).append("\n*/\n");
		ret.append(spec);
		return ret.toString();
	}
	
	private void collectModuleInfoWithIncluded(String moduleName, long version, 
			Map<String, ModuleInfo> ret) throws NoSuchModuleException, TypeStorageException {
		if (ret.containsKey(moduleName))
			return;
		ModuleInfo info = getModuleInfoNL(moduleName, version);
		ret.put(moduleName, info);
		for (Map.Entry<String, Long> entry : info.getIncludedModuleNameToVersion().entrySet()) {
			String includedModule = entry.getKey();
			long includedVersion = entry.getValue();
			collectModuleInfoWithIncluded(includedModule, includedVersion, ret);
		}
	}

	private Map<String, ModuleInfo> getModuleInfoWithIncluded(String moduleName, 
			long version) throws NoSuchModuleException, TypeStorageException {
		Map<String, ModuleInfo> ret = new LinkedHashMap<String, ModuleInfo>();
		collectModuleInfoWithIncluded(moduleName, version, ret);
		return ret;
	}
	
	private static String getTypeSpecText(String curModule, KbType type, 
			Map<String, ModuleInfo> infoMap, Map<String, String> retLocalUnregTypeToSpec, 
			boolean markLinksInSpec) {
		if (type instanceof KbTypedef) {
			KbTypedef td = (KbTypedef)type;
			boolean local = td.getModule().equals(curModule);
			TypeInfo ti = infoMap.get(td.getModule()).getTypes().get(td.getName());
			String version = ti == null ? null : ti.getTypeVersion();
			if (markLinksInSpec && version != null) {
				return "#" + td.getModule() + "." + td.getName() + "-" + version + "#";
			} else {
				if (local && version == null) {
					String comment = td.getComment();
					String spec = "typedef " + getTypeSpecText(curModule, td.getAliasType(), infoMap, 
							retLocalUnregTypeToSpec, markLinksInSpec) + " " + td.getName() + ";";
					if (comment != null && comment.trim().length() > 0)
						spec = "/*\n" + comment + "\n*/\n" + spec;
					retLocalUnregTypeToSpec.put(td.getName(), spec);
				}
				String ret = td.getName();
				if (!local)
					ret = td.getModule() + "." + ret;
				return ret;
			}
		} else if (type instanceof KbScalar) {
			KbScalar sc = (KbScalar)type;
			return sc.getSpecName();
		} else if (type instanceof KbList) {
			KbList ls = (KbList)type;
			return "list<" + getTypeSpecText(curModule, ls.getElementType(), infoMap, 
					retLocalUnregTypeToSpec, markLinksInSpec) + ">";
		} else if (type instanceof KbMapping) {
			KbMapping mp = (KbMapping)type;
			return "mapping<" + getTypeSpecText(curModule, mp.getKeyType(), infoMap, 
					retLocalUnregTypeToSpec, markLinksInSpec) + ", " + getTypeSpecText(curModule, 
							mp.getValueType(), infoMap, retLocalUnregTypeToSpec, markLinksInSpec) + ">";
		} else if (type instanceof KbTuple) {
			KbTuple tp = (KbTuple)type;
			StringBuilder ret = new StringBuilder();
			for (KbType iType : tp.getElementTypes()) {
				if (ret.length() > 0)
					ret.append(", ");
				ret.append(getTypeSpecText(curModule, iType, infoMap, retLocalUnregTypeToSpec,
						markLinksInSpec));
			}
			return "tuple<" + ret + ">";
		} else if (type instanceof KbStruct) {
			KbStruct st = (KbStruct)type;
			StringBuilder ret = new StringBuilder("structure {\n");
			for (KbStructItem item : st.getItems()) {
				ret.append("  ").append(getTypeSpecText(curModule, item.getItemType(), infoMap, 
						retLocalUnregTypeToSpec, markLinksInSpec));
				ret.append(" ").append(item.getName()).append(";\n");
			}
			ret.append("}");
			return ret.toString();
		} else if (type instanceof KbUnspecifiedObject) {
			return "UnspecifiedObject";
		}
		return "Unknown type: " + type.getClass().getSimpleName();
	}
	
	private static String getParamsSpecText(String curModule, List<KbParameter> params,
			Map<String, ModuleInfo> infoMap, Map<String, String> retLocalUnregTypeToSpec, 
			boolean markLinksInSpec) {
		StringBuilder ret = new StringBuilder();
		for (KbParameter param : params) {
			if (ret.length() > 0)
				ret.append(", ");
			ret.append(getTypeSpecText(curModule, param.getType(), infoMap, 
					retLocalUnregTypeToSpec, markLinksInSpec));
			if (param.getName() != null)
				ret.append(" ").append(param.getName());
		}
		return ret.toString();
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
		
		public ComponentChange(boolean isType, boolean isDeletion, String name, 
				String jsonSchemaDocument, KbTypedef typeParsing, KbFuncdef funcParsing, 
				boolean notBackwardCompatible, Set<RefInfo> dependencies) {
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
	
	private static class IncludeDependentPath {
		ModuleInfo info;
		IncludeDependentPath parent;

		public IncludeDependentPath() {
			info = new ModuleInfo();
			info.setModuleName("RootModule");
		}
		
		public IncludeDependentPath(ModuleInfo info, IncludeDependentPath parent) {
			this.info = info;
			this.parent = parent;
		}

		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder();
			for (IncludeDependentPath cur = this; cur != null; cur = cur.parent) {
				if (ret.length() > 0)
					ret.append("<-");
				ret.append(cur.info.getModuleName());
				if (cur.info.getVersionTime() > 0)
					ret.append('(').append(cur.info.getVersionTime()).append(')');
			}
			return ret.toString();
		}
	}
	
	private static class ModuleState {
		int readerCount = 0;
		int writerCount = 0;
	}
}
