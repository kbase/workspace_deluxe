package us.kbase.workspace.kbase;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;

import us.kbase.auth.AuthToken;
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

/** An implementation of the type service methods that delegate to another workspace service.
 * Note that any asAdmin param toggles are ignored - administration methods should be delegated
 * to the administration interface, not here. 
 */
public class DelegatingTypeServerMethods implements TypeServerMethods {
	
	private final WorkspaceDelegator delegator;
	
	/** Create the type service methods delegator.
	 * @param delegator a workspace method delegator.
	 */
	public DelegatingTypeServerMethods(final WorkspaceDelegator delegator) {
		this.delegator = requireNonNull(delegator, "delegator");
	}
	
	@Override
	public void grantModuleOwnership(
			final GrantModuleOwnershipParams params,
			final AuthToken token,
			final boolean asAdmin) // admin methods should not call this when delegating
			throws TypeDelegationException {
		delegator.delegate(token, c -> {c.grantModuleOwnership(params); return null;});
	}

	@Override
	public void removeModuleOwnership(
			final RemoveModuleOwnershipParams params,
			final AuthToken token,
			final boolean asAdmin) // admin methods should not call this when delegating
			throws TypeDelegationException {
		delegator.delegate(token, c -> {c.removeModuleOwnership(params); return null;});
	}

	@Override
	public void requestModuleOwnership(final String mod, final AuthToken token)
			throws TypeDelegationException {
		delegator.delegate(token, c -> {c.requestModuleOwnership(mod); return null;});
	}

	@Override
	public Map<String, String> registerTypespec(
			final RegisterTypespecParams params,
			final AuthToken token)
			throws TypeDelegationException {
		return delegator.delegate(token, c -> c.registerTypespec(params));
	}

	@Override
	public Long registerTypespecCopy(
			final RegisterTypespecCopyParams params,
			final AuthToken token)
			throws TypeDelegationException {
		return delegator.delegate(token, c -> c.registerTypespecCopy(params));
	}

	@Override
	public List<String> releaseModule(final String mod, final AuthToken token)
			throws TypeDelegationException {
		return delegator.delegate(token, c -> c.releaseModule(mod));
	}

	@Override
	public List<String> listModules(final ListModulesParams params)
			throws TypeDelegationException {
		return delegator.delegate(null, c -> c.listModules(params));
	}

	@Override
	public ModuleVersions listModuleVersions(
			final ListModuleVersionsParams params,
			final AuthToken token)
			throws TypeDelegationException {
		return delegator.delegate(token, c -> c.listModuleVersions(params));
	}

	@Override
	public ModuleInfo getModuleInfo(
			final GetModuleInfoParams params,
			final AuthToken token)
			throws TypeDelegationException {
		return delegator.delegate(token, c -> c.getModuleInfo(params));
	}

	@Override
	public String getJsonschema(final String type, final AuthToken token)
			throws TypeDelegationException {
		return delegator.delegate(token, c -> c.getJsonschema(type));
	}

	@Override
	public Map<String, List<String>> translateFromMD5Types(final List<String> md5Types)
			throws TypeDelegationException {
		return delegator.delegate(null, c -> c.translateFromMD5Types(md5Types));
	}

	@Override
	public Map<String, String> translateToMD5Types(
			final List<String> semTypes,
			final AuthToken token)
			throws TypeDelegationException {
		return delegator.delegate(token, c -> c.translateToMD5Types(semTypes));
	}

	@Override
	public TypeInfo getTypeInfo(final String type, final AuthToken token)
			throws TypeDelegationException {
		return delegator.delegate(token, c -> c.getTypeInfo(type));
	}

	@Override
	public List<TypeInfo> getAllTypeInfo(final String mod, final AuthToken token)
			throws TypeDelegationException {
		return delegator.delegate(token, c -> c.getAllTypeInfo(mod));
	}

	@Override
	public FuncInfo getFuncInfo(final String func, final AuthToken token)
			throws TypeDelegationException {
		return delegator.delegate(token, c -> c.getFuncInfo(func));
	}

	@Override
	public List<FuncInfo> getAllFuncInfo(final String mod, final AuthToken token)
			throws TypeDelegationException {
		return delegator.delegate(token, c -> c.getAllFuncInfo(mod));
	}

	@Override
	public Map<String, Map<String, String>> listAllTypes(
			final ListAllTypesParams params,
			final AuthToken token)
			throws TypeDelegationException {
		return delegator.delegate(token, c -> c.listAllTypes(params));
	}

}
