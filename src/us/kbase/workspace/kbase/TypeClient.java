package us.kbase.workspace.kbase;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
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

/** An type client that delegates type operations to another service.
 * Note that any asAdmin param toggles are ignored - administration methods should be delegated
 * to {@link #administer(UObject, AuthToken)} rather than the standard methods.
 */
public class TypeClient implements TypeServerMethods {
	
	/* Once the type server is completely split from the workspace, add another constructor
	 * that takes a type service URL and a TypeServiceClient provider and add if blocks in
	 * the methods to use the right client.
	 * 
	 * An alternate approach would be to make a wrapper than can wrap and hide the differences
	 * between a workspace client and a type client but that seems like more work for the same
	 * result.
	 */
	
	/** Provides a workspace client. */
	public interface WorkspaceClientProvider {
		
		/** Get a client.
		 * @param workspaceURL the workspace URL.
		 * @return the client.
		 * @throws UnauthorizedException if an authorization error occurs.
		 * @throws IOException if an IOError occurs.
		 */
		WorkspaceClient getClient(final URL workspaceURL)
				throws UnauthorizedException, IOException;
		
		/** Get a client.
		 * @param workspaceURL the workspace URL.
		 * @param token the user's token.
		 * @return the client.
		 * @throws UnauthorizedException if an authorization error occurs.
		 * @throws IOException if an IOError occurs.
		 */
		WorkspaceClient getClient(final URL workspaceURL, final AuthToken token)
				 throws UnauthorizedException, IOException;
	}
	
	private final WorkspaceClientProvider wsprovider;
	private final URL targetURL;

	/** Create the type client.
	 * @param targetURL the URL of a workspace to which type methods will be delegated.
	 * @param wsprovider a workspace client provider.
	 */
	public TypeClient(final URL targetURL, final WorkspaceClientProvider wsprovider) {
		this.targetURL = requireNonNull(targetURL, "targetURL");
		this.wsprovider = requireNonNull(wsprovider, "wsprovider");
	}
	
	/** Get the URL of the service to which type operations will be delegated.
	 * @return the URL
	 */
	public URL getTargetURL() {
		return targetURL;
	}

	@Override
	public void grantModuleOwnership(
			final GrantModuleOwnershipParams params,
			final AuthToken token,
			final boolean asAdmin) // admin methods should not call this when delegating
			throws TypeDelegationException {
		delegate(token, c -> {c.grantModuleOwnership(params); return null;});
	}

	@Override
	public void removeModuleOwnership(
			final RemoveModuleOwnershipParams params,
			final AuthToken token,
			final boolean asAdmin) // admin methods should not call this when delegating
			throws TypeDelegationException {
		delegate(token, c -> {c.removeModuleOwnership(params); return null;});
	}

	@Override
	public void requestModuleOwnership(final String mod, final AuthToken token)
			throws TypeDelegationException {
		delegate(token, c -> {c.requestModuleOwnership(mod); return null;});
	}

	@Override
	public Map<String, String> registerTypespec(
			final RegisterTypespecParams params,
			final AuthToken token)
			throws TypeDelegationException {
		return delegate(token, c -> c.registerTypespec(params));
	}

	@Override
	public Long registerTypespecCopy(
			final RegisterTypespecCopyParams params,
			final AuthToken token)
			throws TypeDelegationException {
		return delegate(token, c -> c.registerTypespecCopy(params));
	}

	@Override
	public List<String> releaseModule(
			final String mod,
			final AuthToken token)
			throws TypeDelegationException {
		return delegate(token, c -> c.releaseModule(mod));
	}

	@Override
	public List<String> listModules(final ListModulesParams params)
			throws TypeDelegationException {
		return delegate(null, c -> c.listModules(params));
	}

	@Override
	public ModuleVersions listModuleVersions(
			final ListModuleVersionsParams params,
			final AuthToken token)
			throws TypeDelegationException {
		return delegate(token, c -> c.listModuleVersions(params));
	}

	@Override
	public ModuleInfo getModuleInfo(final GetModuleInfoParams params, final AuthToken token)
			throws TypeDelegationException {
		return delegate(token, c -> c.getModuleInfo(params));
	}

	@Override
	public String getJsonschema(final String type, final AuthToken token)
			throws TypeDelegationException {
		return delegate(token, c -> c.getJsonschema(type));
	}

	@Override
	public Map<String, List<String>> translateFromMD5Types(final List<String> md5Types)
			throws TypeDelegationException {
		return delegate(null, c -> c.translateFromMD5Types(md5Types));
	}

	@Override
	public Map<String, String> translateToMD5Types(
			final List<String> semTypes,
			final AuthToken token)
			throws TypeDelegationException {
		return delegate(token, c -> c.translateToMD5Types(semTypes));
	}

	@Override
	public TypeInfo getTypeInfo(final String type, final AuthToken token)
			throws TypeDelegationException {
		return delegate(token, c -> c.getTypeInfo(type));
	}

	@Override
	public List<TypeInfo> getAllTypeInfo(final String mod, final AuthToken token)
			throws TypeDelegationException {
		return delegate(token, c -> c.getAllTypeInfo(mod));
	}

	@Override
	public FuncInfo getFuncInfo(final String func, final AuthToken token)
			throws TypeDelegationException {
		return delegate(token, c -> c.getFuncInfo(func));
	}

	@Override
	public List<FuncInfo> getAllFuncInfo(final String mod, final AuthToken token)
			throws TypeDelegationException {
		return delegate(token, c -> c.getAllFuncInfo(mod));
	}

	@Override
	public Map<String, Map<String, String>> listAllTypes(
			final ListAllTypesParams params,
			final AuthToken token)
			throws TypeDelegationException {
		return delegate(token, c -> c.listAllTypes(params));
	}
	
	/** Run an administration command.
	 * @param command the command.
	 * @param token the user's token.
	 * @return the results of the command.
	 * @throws TypeDelegationException if a delegation exception occurs.
	 */
	public UObject administer(final UObject command, final AuthToken token)
			throws TypeDelegationException {
		return delegate(token, c -> c.administer(command));
	}
	
	@FunctionalInterface
	private interface WorkspaceCommand<T> {
		
		T execute(WorkspaceClient client) throws ServerException, JsonClientException, IOException;
	}
	
	private <T> T delegate(final AuthToken token, final WorkspaceCommand<T> cmd)
			throws TypeDelegationException {
		final WorkspaceClient client;
		try {
			if (token == null) {
				client = wsprovider.getClient(targetURL);
			} else {
				client = wsprovider.getClient(targetURL, token);
			}
		} catch (UnauthorizedException | IOException e) {
			// This should probably be handled by the provider, but it makes it easier for
			// the provider to be written. The client constructors never actually throw these
			// for URL / token combinations.
			throw new RuntimeException("This should be impossible", e);
		}
		if (targetURL.getProtocol().equals("http")) {
			client.setIsInsecureHttpConnectionAllowed(true);
		}
		try {
			return cmd.execute(client);
		} catch (ServerException e) {
			// This is nasty tricksy hobbitses stuff right here. Don't want the data to show up
			// in the exception message but don't want to lose it either
			// Surely there's some better way to handle this but I'm not seeing one for now
			throw new TypeDelegationException(e.getMessage(),
					new TypeDelegationException(e.getData(), e));
		} catch (JsonClientException | IOException e) {
			throw new TypeDelegationException(e.getMessage(), e);
		}
	}

}
