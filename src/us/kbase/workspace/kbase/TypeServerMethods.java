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

public interface TypeServerMethods {
	
	// TODO JAVADOC
	
	void grantModuleOwnership(
			final GrantModuleOwnershipParams params,
			final AuthToken authPart,
			final boolean asAdmin)
			throws TypeStorageException, NoSuchPrivilegeException;
	
	void removeModuleOwnership(
			final RemoveModuleOwnershipParams params,
			final AuthToken authPart,
			final boolean asAdmin)
			throws NoSuchPrivilegeException, TypeStorageException;

	void requestModuleOwnership(final String mod, final AuthToken authPart)
			throws TypeStorageException;
	
	Map<String,String> registerTypespec(
			final RegisterTypespecParams params,
			final AuthToken authPart)
			throws SpecParseException, TypeStorageException, NoSuchPrivilegeException,
				NoSuchModuleException;

	Long registerTypespecCopy(
			final RegisterTypespecCopyParams params,
			final AuthToken authPart)
			throws UnauthorizedException, MalformedURLException, IOException, JsonClientException,
				NoSuchModuleException, TypeStorageException, SpecParseException,
				NoSuchPrivilegeException;

	List<String> releaseModule(final String mod, final AuthToken authPart)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException;
	
	List<String> listModules(final ListModulesParams params) throws TypeStorageException;

	ModuleVersions listModuleVersions(
			final ListModuleVersionsParams params,
			final AuthToken authPart)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException,
				NoSuchTypeException;
	
	ModuleInfo getModuleInfo(
			final GetModuleInfoParams params,
			final AuthToken authPart)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException;
	
	String getJsonschema(final String type, final AuthToken authPart)
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException;

	Map<String,List<String>> translateFromMD5Types(final List<String> md5Types)
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException;
	
	Map<String,String> translateToMD5Types(
			final List<String> semTypes,
			final AuthToken authPart)
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException;
	
	TypeInfo getTypeInfo(final String type, final AuthToken authPart)
			throws NoSuchModuleException, TypeStorageException, NoSuchTypeException;
	
	List<TypeInfo> getAllTypeInfo(final String mod, final AuthToken authPart)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException,
				NoSuchTypeException;
	
	FuncInfo getFuncInfo(final String func, final AuthToken authPart)
			throws NoSuchModuleException, TypeStorageException, NoSuchFuncException;
	
	List<FuncInfo> getAllFuncInfo(final String mod, final AuthToken authPart)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException,
				NoSuchFuncException;

	Map<String,Map<String,String>> listAllTypes(
			final ListAllTypesParams params,
			final AuthToken authPart)
			throws TypeStorageException, NoSuchModuleException;
}
