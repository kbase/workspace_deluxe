package us.kbase.workspace.kbase;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;

import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.UnauthorizedException;
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
import us.kbase.workspace.WorkspaceServer;

/** The documentation for the methods here is identical to the corresponding methods in
 * {@link WorkspaceServer}. Refer to that class for documentation.
 */
public interface TypeServerMethods {
	
	void grantModuleOwnership(
			final GrantModuleOwnershipParams params,
			final AuthToken token,
			final boolean asAdmin)
			throws TypeStorageException, NoSuchPrivilegeException, TypeDelegationException;
	
	void removeModuleOwnership(
			final RemoveModuleOwnershipParams params,
			final AuthToken token,
			final boolean asAdmin)
			throws NoSuchPrivilegeException, TypeStorageException, TypeDelegationException;

	void requestModuleOwnership(final String mod, final AuthToken token)
			throws TypeStorageException, TypeDelegationException;
	
	Map<String,String> registerTypespec(
			final RegisterTypespecParams params,
			final AuthToken token)
			throws SpecParseException, TypeStorageException, NoSuchPrivilegeException,
				NoSuchModuleException, TypeDelegationException;

	Long registerTypespecCopy(
			final RegisterTypespecCopyParams params,
			final AuthToken token)
			throws UnauthorizedException, MalformedURLException, IOException, JsonClientException,
				NoSuchModuleException, TypeStorageException, SpecParseException,
				NoSuchPrivilegeException, TypeDelegationException;

	List<String> releaseModule(final String mod, final AuthToken token)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException,
				TypeDelegationException;
	
	List<String> listModules(final ListModulesParams params)
			throws TypeStorageException, TypeDelegationException;

	ModuleVersions listModuleVersions(
			final ListModuleVersionsParams params,
			final AuthToken token)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException,
				NoSuchTypeException, TypeDelegationException;
	
	ModuleInfo getModuleInfo(
			final GetModuleInfoParams params,
			final AuthToken token)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException,
				TypeDelegationException;
	
	String getJsonschema(final String type, final AuthToken token)
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException,
				TypeDelegationException;

	Map<String,List<String>> translateFromMD5Types(final List<String> md5Types)
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException,
				TypeDelegationException;
	
	Map<String,String> translateToMD5Types(
			final List<String> semTypes,
			final AuthToken token)
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException,
				TypeDelegationException;
	
	TypeInfo getTypeInfo(final String type, final AuthToken token)
			throws NoSuchModuleException, TypeStorageException, NoSuchTypeException,
				TypeDelegationException;
	
	List<TypeInfo> getAllTypeInfo(final String mod, final AuthToken token)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException,
				NoSuchTypeException, TypeDelegationException;
	
	FuncInfo getFuncInfo(final String func, final AuthToken token)
			throws NoSuchModuleException, TypeStorageException, NoSuchFuncException,
				TypeDelegationException;
	
	List<FuncInfo> getAllFuncInfo(final String mod, final AuthToken token)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException,
				NoSuchFuncException, TypeDelegationException;

	Map<String,Map<String,String>> listAllTypes(
			final ListAllTypesParams params,
			final AuthToken token)
			throws TypeStorageException, NoSuchModuleException, TypeDelegationException;
}