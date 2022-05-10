package us.kbase.workspace.kbase;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URL;

import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.workspace.WorkspaceClient;

/** Assists with delegating commands to another workspace instance. */
public class WorkspaceDelegator {
	
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
	
	private final URL workspaceURL;
	private final WorkspaceClientProvider provider;
	
	/** Create the delegator.
	 * @param workspaceURL the URL of the workspace to which to delegate.
	 * @param provider a workspace client provider.
	 */
	public WorkspaceDelegator(
			final URL workspaceURL,
			final WorkspaceClientProvider provider) {
		this.workspaceURL = requireNonNull(workspaceURL, "workspaceURL");
		this.provider = requireNonNull(provider, "provider");
	}
	
	/** Get the URL of the workspace to which this delegator will delegate.
	 * @return
	 */
	public URL getTargetWorkspace() {
		return workspaceURL;
	}
	
	/** A command applied to a workspace client.
	 *
	 * @param <T> the return type of the command.
	 */
	@FunctionalInterface
	public interface WorkspaceCommand<T> {
		
		/** Run a command on a workspace client.
		 * @param client the workspace client.
		 * @return the results of the command.
		 * @throws ServerException if the command throws a ServerException.
		 * @throws JsonClientException if the command throws a JsonClientException.
		 * @throws IOException if the command throws an IOException.
		 */
		T execute(WorkspaceClient client) throws ServerException, JsonClientException, IOException;
	}
	
	/** Delegate a workspace command to another workspace.
	 * @param <T> the return type of the command.
	 * @param token the user's token or null if none is provided.
	 * @param cmd the command to run.
	 * @return the result of the command.
	 * @throws TypeDelegationException if the delegation fails.
	 */
	public <T> T delegate(final AuthToken token, final WorkspaceCommand<T> cmd)
			throws TypeDelegationException {
		final WorkspaceClient client;
		try {
			if (token == null) {
				client = provider.getClient(workspaceURL);
			} else {
				client = provider.getClient(workspaceURL, token);
			}
		} catch (UnauthorizedException | IOException e) {
			// This should probably be handled by the provider, but it makes it easier for
			// the provider to be written. The client constructors never actually throw these
			// for URL / token combinations.
			throw new RuntimeException("This should be impossible", e);
		}
		if (workspaceURL.getProtocol().equals("http")) {
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
