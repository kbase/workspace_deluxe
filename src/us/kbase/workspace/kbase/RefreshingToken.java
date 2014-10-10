package us.kbase.workspace.kbase;

import static us.kbase.common.utils.StringUtils.checkString;

import java.io.IOException;
import java.util.Date;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;

public class RefreshingToken {
	//TODO move to auth client
	
	final private String user;
	final private String password;
	final private int refreshIntervalSec;
	private AuthToken token;
	private Date refreshDate;
	
	public RefreshingToken(
			final String user,
			final String password,
			final int refreshIntervalSeconds)
			throws AuthException, IOException {
		checkString(user, "user");
		checkString(password, "password");
		if (refreshIntervalSeconds < 0) {
			throw new IllegalArgumentException(
					"refreshInterval must be 0 or greater");
		}
		this.user = user;
		this.password = password;
		this.refreshIntervalSec = refreshIntervalSeconds * 1000;
		this.token = AuthService.login(user, password).getToken();
		this.refreshDate = new Date();
	}
	
	public AuthToken getToken() throws AuthException, IOException {
		if (new Date().getTime() - refreshDate.getTime() > refreshIntervalSec) {
			//TODO 1 retry (in auth service client)
			this.token = AuthService.login(user, password).getToken();
			this.refreshDate = new Date();
		}
		return token;
	}
}
