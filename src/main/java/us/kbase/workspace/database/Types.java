package us.kbase.workspace.database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.db.FuncDetailedInfo;
import us.kbase.typedobj.db.FuncInfo;
import us.kbase.typedobj.db.ModuleDefId;
import us.kbase.typedobj.db.OwnerInfo;
import us.kbase.typedobj.db.TypeChange;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.db.TypeDetailedInfo;
import us.kbase.typedobj.exceptions.NoSuchFuncException;
import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchPrivilegeException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.typedobj.exceptions.SpecParseException;
import us.kbase.typedobj.exceptions.TypeStorageException;

public class Types {
	
	private final TypeDefinitionDB typedb;
	
	public Types(final TypeDefinitionDB typeDB) {
		if (typeDB == null) {
			throw new NullPointerException("typeDB cannot be null");
		}
		this.typedb = typeDB;
	}
	
	private String getUser(WorkspaceUser user) {
		return user == null ? null : user.getUser();
	}
	
	public void requestModuleRegistration(final WorkspaceUser user,
			final String module) throws TypeStorageException {
		if (typedb.isValidModule(module)) {
			throw new IllegalArgumentException(module +
					" module already exists");
		}
		typedb.requestModuleRegistration(module, user.getUser());
	}
	
	public List<OwnerInfo> listModuleRegistrationRequests() throws
			TypeStorageException {
		try {
			return typedb.getNewModuleRegistrationRequests(null, true);
		} catch (NoSuchPrivilegeException nspe) {
			throw new RuntimeException(
					"Something is broken in the administration system", nspe);
		}
	}
	
	public void resolveModuleRegistration(final String module,
			final boolean approve)
			throws TypeStorageException {
		try {
			if (approve) {
				typedb.approveModuleRegistrationRequest(null, module, true);
			} else {
				typedb.refuseModuleRegistrationRequest(null, module, true);
			}
		} catch (NoSuchPrivilegeException nspe) {
			throw new RuntimeException(
					"Something is broken in the administration system", nspe);
		}
	}
	
	//TODO should return the spec version as well?
	public Map<TypeDefName, TypeChange> compileNewTypeSpec(
			final WorkspaceUser user,
			final String typespec,
			final List<String> newtypes,
			final List<String> removeTypes,
			final Map<String, Long> moduleVers,
			final boolean dryRun,
			final Long previousVer)
			throws SpecParseException, TypeStorageException,
			NoSuchPrivilegeException, NoSuchModuleException {
		return typedb.registerModule(typespec, newtypes, removeTypes,
				getUser(user), dryRun, moduleVers, previousVer);
	}
	
	public Map<TypeDefName, TypeChange> compileTypeSpec(
			final WorkspaceUser user,
			final String module,
			final List<String> newtypes,
			final List<String> removeTypes,
			final Map<String, Long> moduleVers,
			boolean dryRun)
			throws SpecParseException, TypeStorageException,
			NoSuchPrivilegeException, NoSuchModuleException {
		return typedb.refreshModule(module, newtypes, removeTypes,
				user.getUser(), dryRun, moduleVers);
	}
	
	public List<AbsoluteTypeDefId> releaseTypes(final WorkspaceUser user,
			final String module)
			throws NoSuchModuleException, TypeStorageException,
			NoSuchPrivilegeException {
		return typedb.releaseModule(module, user.getUser(), false);
	}
	
	public String getJsonSchema(final TypeDefId type, WorkspaceUser user) throws
			NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		return typedb.getJsonSchemaDocument(type, getUser(user));
	}
	
	public List<String> listModules(WorkspaceUser user)
			throws TypeStorageException {
		if (user == null) {
			return typedb.getAllRegisteredModules();
		}
		return typedb.getModulesByOwner(user.getUser());
	}
	
	public ModuleInfo getModuleInfo(final WorkspaceUser user, final ModuleDefId module)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		String userId = getUser(user);
		final us.kbase.typedobj.db.ModuleInfo moduleInfo =
				typedb.getModuleInfo(module, userId, false);
		List<String> functions = new ArrayList<String>();
		for (FuncInfo fi : moduleInfo.getFuncs().values())
			functions.add(module.getModuleName() + "." + fi.getFuncName() + "-" + 
					fi.getFuncVersion());
		return new ModuleInfo(typedb.getModuleSpecDocument(module, userId, false),
				typedb.getModuleOwners(module.getModuleName()),
				moduleInfo.getVersionTime(),  moduleInfo.getDescription(),
				typedb.getJsonSchemasForAllTypes(module, userId, false), 
				moduleInfo.getIncludedModuleNameToVersion(), moduleInfo.getMd5hash(), 
				new ArrayList<String>(functions), moduleInfo.isReleased());
	}
	
	public List<Long> getModuleVersions(final String module, WorkspaceUser user)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		if (user != null && typedb.isOwnerOfModule(module, user.getUser()))
			return typedb.getAllModuleVersionsWithUnreleased(module, user.getUser(), false);
		return typedb.getAllModuleVersions(module);
	}
	
	public List<Long> getModuleVersions(final TypeDefId type, WorkspaceUser user) 
			throws NoSuchModuleException, TypeStorageException, NoSuchTypeException {
		final List<ModuleDefId> mods =
				typedb.findModuleVersionsByTypeVersion(type, getUser(user));
		final List<Long> vers = new ArrayList<Long>();
		for (final ModuleDefId m: mods) {
			vers.add(m.getVersion());
		}
		return vers;
	}

	public HashMap<String, List<String>> translateFromMd5Types(List<String> md5TypeList) 
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		//return typedb.getTypeVersionsForMd5(md5TypeDef);
		HashMap<String, List<String>> ret = new LinkedHashMap<String, List<String>>();
		for (String md5TypeDef : md5TypeList) {
			List<AbsoluteTypeDefId> semantList = typedb.getTypeVersionsForMd5(TypeDefId.fromTypeString(md5TypeDef));
			List<String> retList = new ArrayList<String>();
			for (AbsoluteTypeDefId semantTypeDef : semantList)
				retList.add(semantTypeDef.getTypeString());
			ret.put(md5TypeDef, retList);
		}
		return ret;
	}

	public Map<String,String> translateToMd5Types(List<String> semanticTypeList,
			WorkspaceUser user) 
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		HashMap<String, String> ret = new LinkedHashMap<String, String>();
		for (String semantString : semanticTypeList) {
			TypeDefId semantTypeDef = TypeDefId.fromTypeString(semantString);
			ret.put(semantString, typedb.getTypeMd5Version(semantTypeDef, getUser(user)).getTypeString());
		}
		return ret;
	}
	
	public long compileTypeSpecCopy(
			final String moduleName,
			final String specDocument,
			final Set<String> extTypeSet,
			final String userId,
			final Map<String, String> includesToMd5,
			final Map<String, Long> extIncludedSpecVersions)
			throws NoSuchModuleException, TypeStorageException, SpecParseException,
			NoSuchPrivilegeException {
		final long lastLocalVer = typedb.getLatestModuleVersionWithUnreleased
				(moduleName, userId, false);
		final Map<String, Long> moduleVersionRestrictions = new HashMap<>();
		for (Map.Entry<String, String> entry : includesToMd5.entrySet()) {
			final String includedModule = entry.getKey();
			final String md5 = entry.getValue();
			final long extIncludedVer = extIncludedSpecVersions.get(includedModule);
			final List<ModuleDefId> localIncludeVersions = new ArrayList<>(
					typedb.findModuleVersionsByMD5(includedModule, md5));
			if (localIncludeVersions.size() == 0) {
				throw new NoSuchModuleException("Can not find local module " + includedModule +
						" synchronized with external version " + extIncludedVer +
						" (md5=" + md5 + ")");
			}
			final us.kbase.typedobj.db.ModuleInfo localIncludedInfo =
					typedb.getModuleInfo(includedModule, localIncludeVersions.get(0).getVersion());
			moduleVersionRestrictions.put(
					localIncludedInfo.getModuleName(), localIncludedInfo.getVersionTime());
		}
		final Set<String> prevTypes = new HashSet<>(
				typedb.getModuleInfo(moduleName, lastLocalVer).getTypes().keySet());
		final Set<String> typesToSave = new HashSet<>(extTypeSet);
		final Set<String> allTypes = new HashSet<>(prevTypes);
		allTypes.addAll(typesToSave);
		final List<String> typesToUnregister = new ArrayList<>();
		for (final String typeName : allTypes) {
			if (prevTypes.contains(typeName)) {
				if (typesToSave.contains(typeName)) {
					typesToSave.remove(typeName);
				} else {
					//TODO TEST this is untested
					typesToUnregister.add(typeName);
				}
			}
		}
		typedb.registerModule(specDocument, new ArrayList<>(typesToSave), typesToUnregister,
				userId, false, moduleVersionRestrictions);
		return typedb.getLatestModuleVersionWithUnreleased(moduleName, userId, false);
	}
	
	public TypeDetailedInfo getTypeInfo(String typeDef, boolean markTypeLinks, WorkspaceUser user) 
			throws NoSuchModuleException, TypeStorageException, NoSuchTypeException {
		String userId = getUser(user);
		return typedb.getTypeDetailedInfo(TypeDefId.fromTypeString(typeDef), markTypeLinks, userId);
	}
	
	public FuncDetailedInfo getFuncInfo(String funcDef, boolean markTypeLinks, WorkspaceUser user) 
			throws NoSuchModuleException, TypeStorageException, NoSuchFuncException {
		TypeDefId tempDef = TypeDefId.fromTypeString(funcDef);
		String userId = getUser(user);
		return typedb.getFuncDetailedInfo(tempDef.getType().getModule(), 
				tempDef.getType().getName(), tempDef.getVerString(), markTypeLinks, userId);
	}

	public void grantModuleOwnership(String moduleName, String newOwner, boolean withGrantOption,
			WorkspaceUser user, boolean isAdmin) throws TypeStorageException, NoSuchPrivilegeException {
		typedb.addOwnerToModule(getUser(user), moduleName, newOwner, withGrantOption, isAdmin);
	}
	
	public void removeModuleOwnership(String moduleName, String oldOwner, WorkspaceUser user, 
			boolean isAdmin) throws NoSuchPrivilegeException, TypeStorageException {
		typedb.removeOwnerFromModule(getUser(user), moduleName, oldOwner, isAdmin);
	}
	
	public Map<String, Map<String, String>> listAllTypes(boolean withEmptyModules) 
			throws TypeStorageException, NoSuchModuleException {
		Map<String, Map<String, String>> ret = new TreeMap<String, Map<String, String>>();
		for (String moduleName : typedb.getAllRegisteredModules()) {
			Map<String, String> typeMap = new TreeMap<String, String>();
			for (String key : typedb.getModuleInfo(moduleName).getTypes().keySet())
				typeMap.put(typedb.getModuleInfo(moduleName).getTypes().get(key).getTypeName(), 
						typedb.getModuleInfo(moduleName).getTypes().get(key).getTypeVersion());
			if (withEmptyModules || !typeMap.isEmpty())
				ret.put(moduleName, typeMap);
		}
		return ret;
	}
}
