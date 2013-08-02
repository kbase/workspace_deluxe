package us.kbase.workspace.database;

import java.io.IOException;
import java.net.URL;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthUser;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.exceptions.ExpiredTokenException;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.workspace.database.exceptions.DBAuthorizationException;
import us.kbase.workspace.database.exceptions.WorkspaceBackendException;

public class ShockBackend implements BlobStore {
	
	private String user;
	private String password;
	private BasicShockClient client;
	
	public ShockBackend(URL url, String user, String password) throws
			DBAuthorizationException, WorkspaceBackendException {
		this.user = user;
		this.password = password;
		AuthUser u = null;
		try {
			u = AuthService.login(user, password);
		} catch (Exception e) { //TODO better granularity on errors
			throw new DBAuthorizationException(
					"Could not authenticate backend user " + user, e);
		}
		try {
			client = new BasicShockClient(url, u.getToken());
		} catch (InvalidShockUrlException isue) {
			throw new WorkspaceBackendException(
					"The shock url " + url + " is invalid", isue);
		} catch (ExpiredTokenException ete) {
			throw new WorkspaceBackendException( //uh... this should never happen
					"The token retrieved from the auth service is already " +
					"expired", ete);
		} catch (IOException ioe) {
			throw new WorkspaceBackendException(
					"Could not connect to the shock backend: " +
					ioe.getLocalizedMessage(), ioe);
		}
	}

	@Override
	public void saveBlob(TypeData td) {
		// TODO Auto-generated method stub
	}

	@Override
	public String getBlob(TypeData td) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getExternalIdentifier(TypeData td) {
		// TODO Auto-generated method stub
		return null;
	}

}
