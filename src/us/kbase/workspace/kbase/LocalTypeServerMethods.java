package us.kbase.workspace.kbase;

import static us.kbase.common.utils.ServiceUtils.checkAddlArgs;
import static us.kbase.workspace.kbase.ArgUtils.longToBoolean;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.db.FuncDetailedInfo;
import us.kbase.typedobj.db.ModuleDefId;
import us.kbase.typedobj.db.TypeChange;
import us.kbase.typedobj.db.TypeDetailedInfo;
import us.kbase.typedobj.exceptions.NoSuchFuncException;
import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchPrivilegeException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.typedobj.exceptions.SpecParseException;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.workspace.FuncInfo;
import us.kbase.workspace.GetModuleInfoParams;
import us.kbase.workspace.GrantModuleOwnershipParams;
import us.kbase.workspace.ListAllTypesParams;
import us.kbase.workspace.ListModuleVersionsParams;
import us.kbase.workspace.ListModulesParams;
import us.kbase.workspace.ModuleInfo;
import us.kbase.workspace.ModuleVersions;
import us.kbase.workspace.RegisterTypespecCopyParams;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.RemoveModuleOwnershipParams;
import us.kbase.workspace.TypeInfo;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.WorkspaceUser;

public class LocalTypeServerMethods implements TypeServerMethods {
	
	// TODO TEST unit tests, covered by integration tests for now
	// TODO JAVADOC
	
	private final Types types;

	public LocalTypeServerMethods(final Types types) {
		this.types = types;
	}
	
	public Types getTypes() {
		return types;
	}

	private WorkspaceUser getUser(final AuthToken token) {
		return token == null ? null : new WorkspaceUser(token.getUserName());
	}

	@Override
	public void grantModuleOwnership(
			final GrantModuleOwnershipParams params,
			final AuthToken token,
			final boolean asAdmin)
			throws TypeStorageException, NoSuchPrivilegeException {
		checkAddlArgs(params.getAdditionalProperties(), GrantModuleOwnershipParams.class);
		// TODO CODE it looks like this never validated the user name, should be fixed
		types.grantModuleOwnership(params.getMod(), params.getNewOwner(),
				longToBoolean(params.getWithGrantOption()), getUser(token), asAdmin);
	}
	
	@Override
	public void removeModuleOwnership(
			final RemoveModuleOwnershipParams params,
			final AuthToken token,
			final boolean asAdmin)
			throws NoSuchPrivilegeException, TypeStorageException {
		checkAddlArgs(params.getAdditionalProperties(), RemoveModuleOwnershipParams.class);
		types.removeModuleOwnership(params.getMod(), params.getOldOwner(),
				getUser(token), asAdmin);
	}

	@Override
	public void requestModuleOwnership(final String mod, final AuthToken token)
			throws TypeStorageException {
		types.requestModuleRegistration(getUser(token), mod);
	}
	
	@Override
	public Map<String,String> registerTypespec(
			final RegisterTypespecParams params,
			final AuthToken token)
			throws SpecParseException, TypeStorageException, NoSuchPrivilegeException,
				NoSuchModuleException {
		//TODO improve parse errors, don't need include path, currentlyCompiled
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		if (!(params.getMod() == null) ^ (params.getSpec() == null)) {
			throw new IllegalArgumentException(
					"Must provide either a spec or module name");
		}
		final List<String> add = params.getNewTypes() != null ?
				params.getNewTypes() : new ArrayList<String>();
		final List<String> rem = params.getRemoveTypes() != null ?
				params.getRemoveTypes() : new ArrayList<String>();
		final Map<String, Long> deps = params.getDependencies() != null ?
				params.getDependencies() : new HashMap<String, Long>();
		final Map<TypeDefName, TypeChange> res;
		if (params.getMod() != null) {
			 res = types.compileTypeSpec(
					getUser(token), params.getMod(),
					add, rem, deps, params.getDryrun() == null ? true :
						params.getDryrun() != 0);
		} else {
			res = types.compileNewTypeSpec(
					getUser(token), params.getSpec(),
					add, rem, deps, params.getDryrun() == null ? true :
						params.getDryrun() != 0, params.getPrevVer());
		}
		final Map<String, String> returnVal = new HashMap<String, String>();
		for (final TypeChange tc: res.values()) {
			if (!tc.isUnregistered()) {
				returnVal.put(tc.getTypeVersion().getTypeString(),
						tc.getJsonSchema());
			}
		}
		return returnVal;
	}
	
	@Override
	public Long registerTypespecCopy(
			final RegisterTypespecCopyParams params,
			final AuthToken token)
			throws UnauthorizedException, MalformedURLException, IOException, JsonClientException,
				NoSuchModuleException, TypeStorageException, SpecParseException,
				NoSuchPrivilegeException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		if (params.getExternalWorkspaceUrl() == null) {
			throw new IllegalArgumentException(
					"Must provide a URL for an external workspace service");
		}
		if (params.getMod() == null) {
			throw new IllegalArgumentException(
					"Must provide a module name");
		}
		final WorkspaceClient client = new WorkspaceClient(
				new URL(params.getExternalWorkspaceUrl()), token);
		if (!params.getExternalWorkspaceUrl().startsWith("https:")) {
			client.setIsInsecureHttpConnectionAllowed(true);
		}
		final GetModuleInfoParams gmiparams = new GetModuleInfoParams()
			.withMod(params.getMod()).withVer(params.getVersion());
		final us.kbase.workspace.ModuleInfo extInfo = client.getModuleInfo(gmiparams);
		final Map<String, String> includesToMd5 = new HashMap<String, String>();
		for (final Map.Entry<String, Long> entry : extInfo.getIncludedSpecVersion().entrySet()) {
			final String includedModule = entry.getKey();
			final long extIncludedVer = entry.getValue();
			final GetModuleInfoParams includeParams = new GetModuleInfoParams()
				.withMod(includedModule).withVer(extIncludedVer);
			final us.kbase.workspace.ModuleInfo extIncludedInfo =
					client.getModuleInfo(includeParams);
			includesToMd5.put(includedModule, extIncludedInfo.getChsum());
		}
		final String userId = token.getUserName();
		final String specDocument = extInfo.getSpec();
		final Set<String> extTypeSet = new LinkedHashSet<String>();
		for (final String typeDef : extInfo.getTypes().keySet()) {
			extTypeSet.add(TypeDefId.fromTypeString(typeDef).getType().getName());
		}
		return types.compileTypeSpecCopy(params.getMod(), specDocument,
				extTypeSet, userId, includesToMd5, extInfo.getIncludedSpecVersion());
	}

	@Override
	public List<String> releaseModule(final String mod, final AuthToken token)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		return types.releaseTypes(getUser(token), mod).stream()
				.map(t -> t.getTypeString()).collect(Collectors.toList());
	}
	
	@Override
	public List<String> listModules(final ListModulesParams params) throws TypeStorageException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		return types.listModules(params.getOwner() == null ?
				null : new WorkspaceUser(params.getOwner()));
	}

	@Override
	public ModuleVersions listModuleVersions(
			final ListModuleVersionsParams params,
			final AuthToken token)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException,
				NoSuchTypeException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		if (!(params.getMod() == null ^ params.getType() == null)) {
			throw new IllegalArgumentException(
					"Must provide either a module name or a type");
		}
		final List<Long> vers;
		final String module;
		if (params.getMod() != null) {
			vers = types.getModuleVersions(
					params.getMod(), getUser(token));
			module = params.getMod();
		} else {
			final TypeDefId type = TypeDefId.fromTypeString(params.getType());
			vers = types.getModuleVersions(type, getUser(token));
			module = type.getType().getModule();
		}
		return new ModuleVersions().withMod(module).withVers(vers);
	}
	
	@Override
	public ModuleInfo getModuleInfo(
			final GetModuleInfoParams params,
			final AuthToken token)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		if (params.getMod() == null) {
			throw new IllegalArgumentException(
					"Must provide a module name");
		}
		final ModuleDefId module;
		if (params.getVer() != null) {
			module = new ModuleDefId(params.getMod(), params.getVer());
		} else {
			module = new ModuleDefId(params.getMod());
		}
		final us.kbase.workspace.database.ModuleInfo mi =
				types.getModuleInfo(getUser(token), module);
		final Map<String, String> types = new HashMap<String, String>();
		for (final AbsoluteTypeDefId t: mi.getTypes().keySet()) {
			types.put(t.getTypeString(), mi.getTypes().get(t));
		}
		return new ModuleInfo()
				.withDescription(mi.getDescription())
				.withOwners(mi.getOwners())
				.withSpec(mi.getTypespec())
				.withVer(mi.getVersion())
				.withTypes(types)
				.withIncludedSpecVersion(mi.getIncludedSpecVersions())
				.withChsum(mi.getMd5hash())
				.withFunctions(mi.getFunctions())
				.withIsReleased(mi.isReleased() ? 1L : 0L);
	}
	
	@Override
	public String getJsonschema(final String type, final AuthToken token)
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		return types.getJsonSchema(TypeDefId.fromTypeString(type), getUser(token));
	}
	
	@Override
	public Map<String,List<String>> translateFromMD5Types(final List<String> md5Types)
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		return types.translateFromMd5Types(md5Types);
	}
	
	@Override
	public Map<String,String> translateToMD5Types(
			final List<String> semTypes,
			final AuthToken token)
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		return types.translateToMd5Types(semTypes, getUser(token));
	}
	
	@Override
	public TypeInfo getTypeInfo(final String type, final AuthToken token)
			throws NoSuchModuleException, TypeStorageException, NoSuchTypeException {
		final TypeDetailedInfo tdi = types.getTypeInfo(
				type, true, getUser(token));
		return new TypeInfo().withTypeDef(tdi.getTypeDefId())
				.withDescription(tdi.getDescription())
				.withSpecDef(tdi.getSpecDef())
				.withJsonSchema(tdi.getJsonSchema())
				.withParsingStructure(tdi.getParsingStructure())
				.withModuleVers(tdi.getModuleVersions())
				.withReleasedModuleVers(tdi.getReleasedModuleVersions())
				.withTypeVers(tdi.getTypeVersions())
				.withReleasedTypeVers(tdi.getReleasedTypeVersions())
				.withUsingFuncDefs(tdi.getUsingFuncDefIds())
				.withUsingTypeDefs(tdi.getUsingTypeDefIds())
				.withUsedTypeDefs(tdi.getUsedTypeDefIds());
	}
	
	@Override
	public List<TypeInfo> getAllTypeInfo(final String mod, final AuthToken token)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException,
				NoSuchTypeException {
		final ModuleInfo mi = getModuleInfo(new GetModuleInfoParams().withMod(mod), token);
		final List<TypeInfo> returnVal = new ArrayList<>();
		for (String typeDef : mi.getTypes().keySet()) {
			returnVal.add(getTypeInfo(typeDef, token));
		}
		return returnVal;
	}
	
	@Override
	public FuncInfo getFuncInfo(final String func, final AuthToken token)
			throws NoSuchModuleException, TypeStorageException, NoSuchFuncException {
		final FuncDetailedInfo fdi = types.getFuncInfo(func, true, getUser(token));
		return new FuncInfo().withFuncDef(fdi.getFuncDefId())
				.withDescription(fdi.getDescription())
				.withSpecDef(fdi.getSpecDef())
				.withParsingStructure(fdi.getParsingStructure())
				.withModuleVers(fdi.getModuleVersions())
				.withReleasedModuleVers(fdi.getReleasedModuleVersions())
				.withFuncVers(fdi.getFuncVersions())
				.withReleasedFuncVers(fdi.getReleasedFuncVersions())
				.withUsedTypeDefs(fdi.getUsedTypeDefIds());
	}
	
	@Override
	public List<FuncInfo> getAllFuncInfo(final String mod, final AuthToken token)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException,
				NoSuchFuncException {
		final ModuleInfo mi = getModuleInfo(new GetModuleInfoParams().withMod(mod), token);
		final List<FuncInfo> returnVal = new ArrayList<FuncInfo>();
		for (String funcDef : mi.getFunctions()) {
			returnVal.add(getFuncInfo(funcDef, token));
		}
		return returnVal;
	}

	@Override
	public Map<String,Map<String,String>> listAllTypes(
			final ListAllTypesParams params,
			final AuthToken token)
			throws TypeStorageException, NoSuchModuleException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		return types.listAllTypes(params.getWithEmptyModules() != null &&
				params.getWithEmptyModules() != 0L);
	}
}
