package us.kbase.workspace.kbase;

import java.io.IOException;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthToken;

/** Temporary class to abstract away the difference between a standard and
 * refreshing token. Only needed during transition to new auth service.
 * @author gaprice@lbl.gov
 *
 */
public class TokenProvider {
	
	//TODO AUTH LATER remove this class and use bare AuthTokens

	private final AuthToken token;
	@SuppressWarnings("deprecation")
	private final us.kbase.auth.RefreshingToken rToken;
	
	public TokenProvider(final AuthToken token) {
		if (token == null) {
			throw new NullPointerException("token");
		}
		this.token = token;
		this.rToken = null;
	}
	
	public TokenProvider(
			@SuppressWarnings("deprecation")
			final us.kbase.auth.RefreshingToken rToken) {
		if (rToken == null) {
			throw new NullPointerException("rToken");
		}
		this.rToken = rToken;
		this.token = null;
	}
	
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
