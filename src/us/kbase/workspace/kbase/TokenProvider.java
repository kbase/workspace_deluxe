package us.kbase.workspace.kbase;

import java.io.IOException;
import java.net.URL;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthToken;

/** Class to abstract away the difference between a standard and
 * refreshing token.
 * @author gaprice@lbl.gov
 *
 */
public class TokenProvider {
	
	//TODO AUTH LATER remove RefreshingToken
	//TODO TEST unit tests

	private final AuthToken token;
	@SuppressWarnings("deprecation")
	private final us.kbase.auth.RefreshingToken rToken;
	private final URL authURL;
	
	/** Create a token provider with a standard token.
	 * @param token a token.
	 * @param authURL the URL of the authorization service that created this
	 * token.
	 */
	public TokenProvider(final AuthToken token, final URL authURL) {
		if (token == null) {
			throw new NullPointerException("token");
		}
		if (authURL == null) {
			throw new NullPointerException();
		}
		this.token = token;
		this.rToken = null;
		this.authURL = authURL;
	}
	
	/** Create a token provider with a refreshing token.
	 * @param rToken a refreshing token.
	 * @param authURL the URL of the authorization service that created this
	 * token.
	 * 
	 * @deprecated
	 */
	public TokenProvider(
			final us.kbase.auth.RefreshingToken rToken,
			final URL authURL) {
		if (rToken == null) {
			throw new NullPointerException("rToken");
		}
		if (authURL == null) {
			throw new NullPointerException();
		}
		this.rToken = rToken;
		this.token = null;
		this.authURL = authURL;
	}
	
	/** Get the URL of the authorization service that created this token.
	 * @return
	 */
	public URL getAuthURL() {
		return authURL;
	}
	
	/** Get the token held in this token provider.
	 * @return a token.
	 * @throws AuthException if the refreshing token could not refresh.
	 * @throws IOException if an IO error occurred while the refreshing token
	 * was refreshing.
	 */
	public AuthToken getToken() throws AuthException, IOException {
		if (rToken == null) {
			return token;
		} else {
			@SuppressWarnings("deprecation")
			final AuthToken t = rToken.getToken();
			return t;
		}
	}
	
}
