package us.kbase.workspace.kbase.admin;

import static java.util.Objects.requireNonNull;
import static us.kbase.workspace.database.Util.checkNoNullsOrEmpties;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.auth.AuthToken;
import us.kbase.workspace.database.WorkspaceUser;

/** An administrator handler that contacts the KBase Authentication service to determine
 * administration credentials.
 * @author gaprice@lbl.gov
 *
 */
public class KBaseAuth2AdminHandler implements AdministratorHandler {
	
	//TODO NEWAUTH DOCS
	//TODO NEWAUTH add to build - need 2 new config items
	//TODO NEWAUTH integration tests
	//TODO CODE retries for gets (should handled to some extent by the http client)

	private static final String UNSUPPORTED =
			"This operation is delegated to the KBase Authentication service";
	private static final Set<String> EXPECTED_AUTH_KEYS = Collections.unmodifiableSet(
			new HashSet<>(Arrays.asList("gitcommithash", "version", "servertime")));
	
	private final ObjectMapper MAPPER = new ObjectMapper();
	
	private final Set<String> readOnlyRoles;
	private final Set<String> fullAdminRoles;
	private final CloseableHttpClient client;
	private final URI rootAuthURI;
	private final URI me;
	
	/** Create the administrator handler.
	 * @param client the Apache HTTP client to use with the handler.
	 * @param rootAuthURL the root URL of the KBase authentication service.
	 * @param readOnlyRoles the set of KBase authentication service roles that should grant
	 * read only administrator privileges if a user possesses at least one of the roles.
	 * @param fullAdminRoles the set of KBase authentication service roles that should grant full
	 * administrator privileges.
	 * @throws URISyntaxException if the URL is not a valid URI.
	 * @throws AdministratorHandlerException If the auth server response is invalid.
	 */
	public KBaseAuth2AdminHandler(
			final CloseableHttpClient client,
			final URL rootAuthURL,
			final Set<String> readOnlyRoles,
			final Set<String> fullAdminRoles)
			throws URISyntaxException, AdministratorHandlerException {
		requireNonNull(rootAuthURL, "rootAuthURL");
		checkNoNullsOrEmpties(readOnlyRoles, "readOnlyRoles");
		checkNoNullsOrEmpties(fullAdminRoles, "fullAdminRoles");
		this.client = requireNonNull(client, "client");
		this.readOnlyRoles = Collections.unmodifiableSet(new HashSet<>(readOnlyRoles));
		this.fullAdminRoles = Collections.unmodifiableSet(new HashSet<>(fullAdminRoles));
		
		if (rootAuthURL.toString().endsWith("/")) {
			this.rootAuthURI = rootAuthURL.toURI();
		} else {
			try {
				this.rootAuthURI = new URL(rootAuthURL.toString() + "/").toURI();
			} catch (MalformedURLException e) {
				throw new RuntimeException("This should be impossible", e);
			}
		}
		this.me = this.rootAuthURI.resolve("api/V2/me");
		final Map<String, Object> resp = get(httpGet(this.rootAuthURI));
		final Set<String> missingKeys = new HashSet<>(EXPECTED_AUTH_KEYS);
		missingKeys.removeAll(resp.keySet());
		if (!missingKeys.isEmpty()) {
			throw new AdministratorHandlerException(String.format(
					"%s does not appear to be the KBase authentication server. " +
					"Missing root JSON keys: %s", rootAuthURL, missingKeys));
		}
	}
	
	/** Get the root URI of the authentication service.
	 * @return the URI.
	 */
	public URI getRootAuthURI() {
		return rootAuthURI;
	}
	
	/** Get the set of roles that specify a user has read only administrator permission.
	 * @return the roles.
	 */
	public Set<String> getReadOnlyRoles() {
		return readOnlyRoles;
	}

	/** Get the set of roles that specify a user has full administrator permission.
	 * @return the roles.
	 */
	public Set<String> getFullAdminRoles() {
		return fullAdminRoles;
	}

	private Map<String, Object> get(final HttpGet httpGet)
			throws AdministratorHandlerException {
		final CloseableHttpResponse response;
		try {
			response = client.execute(httpGet);
		} catch (IOException e) {
			throw new AdministratorHandlerException(
					"Unable to contact the KBase Authentication server: " + e.getMessage(), e);
		}
		try {
			@SuppressWarnings("unchecked")
			final Map<String, Object> respobj = MAPPER.readValue(
					response.getEntity().getContent(), Map.class);
			return respobj;
		} catch (JsonParseException jpe) {
			throw new AdministratorHandlerException(String.format(
					"Invalid KBase authentication server response. Server said %s %s" +
					". JSON parser said %s", 
					response.getStatusLine().getStatusCode(),
					response.getStatusLine().getReasonPhrase(), 
					jpe.getLocalizedMessage()), jpe);
		} catch (IOException e) {
			throw new AdministratorHandlerException(String.format(
					"Invalid KBase authentication server response. Server said %s %s.",
					response.getStatusLine().getStatusCode(),
					response.getStatusLine().getReasonPhrase()), e);
		} finally {
			try {
				response.close();
			} catch (IOException e) {
				throw new RuntimeException("I give up.");
			}
		}
	}

	private HttpGet httpGet(final URI url) {
		final HttpGet httpGet = new HttpGet(url);
		httpGet.setHeader("accept", "application/json");
		return httpGet;
	}
	
	private HttpGet httpGet(final URI uri, final AuthToken token) {
		final HttpGet httpGet = httpGet(uri);
		httpGet.setHeader("authorization", token.getToken());
		return httpGet;
	}

	@Override
	public Set<WorkspaceUser> getAdmins() throws AdministratorHandlerException {
		throw new UnsupportedOperationException(UNSUPPORTED);
	}

	@Override
	public void addAdmin(final WorkspaceUser user) throws AdministratorHandlerException {
		throw new UnsupportedOperationException(UNSUPPORTED);
	}

	@Override
	public void removeAdmin(final WorkspaceUser user) throws AdministratorHandlerException {
		throw new UnsupportedOperationException(UNSUPPORTED);
	}

	@Override
	public AdminRole getAdminRole(final AuthToken token) throws AdministratorHandlerException {
		requireNonNull(token, "token");
		final Map<String, Object> resp = get(httpGet(this.me, token));
		if (resp.containsKey("error")) {
			// don't really see a need to check error codes here, but can add later
			// assume this is a legit error package
			@SuppressWarnings("unchecked")
			final Map<String, String> err = (Map<String, String>) resp.get("error");
			throw new AdministratorHandlerException(
					"KBase authentication service reported an error: " + err.get("message"));
		}
		if (hasRole(resp, this.fullAdminRoles)) {
			return AdminRole.ADMIN;
		} else if (hasRole(resp, this.readOnlyRoles)) {
			return AdminRole.READ_ONLY;
		} else {
			return AdminRole.NONE;
		}
	}

	private boolean hasRole(final Map<String, Object> resp, final Set<String> roles) {
		final Set<String> rolecopy = new HashSet<>(roles);
		@SuppressWarnings("unchecked")
		final List<String> customRoles = (List<String>) resp.get("customroles");
		rolecopy.removeAll(customRoles);
		return rolecopy.size() < roles.size();
	}
	
	public static void main(String[] args) throws Exception {
		final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
		cm.setMaxTotal(1000); //perhaps these should be configurable
		cm.setDefaultMaxPerRoute(1000);
		//TODO set timeouts for the client for 1/2m for conn req timeout and std timeout
		final CloseableHttpClient client = HttpClients.custom().setConnectionManager(cm).build();
		final AdministratorHandler h = new KBaseAuth2AdminHandler(
				client,
				new URL("https://ci.kbase.us/services/auth"),
				new HashSet<>(Arrays.asList("WS_READ_ADMIN_TEST", "OTHER_ROLE")),
				new HashSet<>(Arrays.asList("WS_ADMIN_TEST", "OTHER_ROLE2")));
		
		System.out.println(h.getAdminRole(new AuthToken(args[0], "user")));
	}

}
