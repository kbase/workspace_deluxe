package us.kbase.workspace.database.mongo;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.AuthUser;
import us.kbase.auth.TokenExpiredException;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockHttpException;
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
		try {
			client = new BasicShockClient(url, getToken());
		} catch (InvalidShockUrlException isue) {
			throw new WorkspaceBackendException(
					"The shock url " + url + " is invalid", isue);
		} catch (TokenExpiredException ete) {
			throw new WorkspaceBackendException( //uh... this should never happen
					"The token retrieved from the auth service is already " +
					"expired", ete);
		} catch (IOException ioe) {
			throw new WorkspaceBackendException(
					"Could not connect to the shock backend: " +
					ioe.getLocalizedMessage(), ioe);
		}
	}
	
	private AuthToken getToken() throws DBAuthorizationException,
			WorkspaceBackendException {
		AuthUser u = null;
		try {
			u = AuthService.login(user, password);
		} catch (AuthException ae) {
			throw new DBAuthorizationException(
					"Could not authenticate backend user " + user, ae);
		} catch (IOException ioe) {
			throw new WorkspaceBackendException(
					"Could not connect to the shock backend: " +
					ioe.getLocalizedMessage(), ioe);
		}
		return u.getToken();
	}
	
	private void checkAuth() throws DBAuthorizationException,
			WorkspaceBackendException {
		if(client.isTokenExpired()) {
			try {
				client.updateToken(getToken());
			} catch (TokenExpiredException ete) {
				throw new RuntimeException(
						"Auth service is handing out expired tokens", ete);
			}
		}
	}

	@Override
	public void saveBlob(TypeData td) throws DBAuthorizationException,
			WorkspaceBackendException {
		checkAuth();
		String data = td.getData();
		if(data == null) {
			throw new RuntimeException("No data in typedata object");
		}
		WorkspaceType type = td.getType();
		if(type == null) {
			throw new RuntimeException("No type in typedata object");
		}
		Map<String, Object> attribs = new HashMap<>();
		Map<String,Object> workattribs = new HashMap<>();
		workattribs.put("typeowner", type.getOwner());
		workattribs.put("module", type.getModule());
		workattribs.put("type", type.getType());
		workattribs.put("version", type.getVersion());
		attribs.put("workspace", workattribs);
		ShockNode sn = null;
		try {
			sn = client.addNode(attribs, data.getBytes(),
				"workspace_" + td.getChsum());
		} catch (TokenExpiredException ete) {
			//this should be impossible
			throw new RuntimeException("Things are broke", ete);
		} catch (JsonProcessingException jpe) {
			//this should be impossible
			throw new RuntimeException("Things are broke", jpe);
		} catch (IOException ioe) {
			throw new WorkspaceBackendException(
					"Could not connect to the shock backend: " +
					ioe.getLocalizedMessage(), ioe);
		} catch (ShockHttpException she) {
			throw new WorkspaceBackendException(
					"Failed to create shock node: " +
					she.getLocalizedMessage(), she);
		}
		td.addShockInformation(sn);
	}

	@Override
	public String getBlob(TypeData td) throws DBAuthorizationException,
			WorkspaceBackendException {
		checkAuth();
		String ret = null;
		try {
			ret = new String(client.getFile(td.getShockNodeId()));
		} catch (TokenExpiredException ete) {
			//this should be impossible
			throw new RuntimeException("Things are broke", ete);
		} catch (IOException ioe) {
			throw new WorkspaceBackendException(
					"Could not connect to the shock backend: " +
					ioe.getLocalizedMessage(), ioe);
		} catch (ShockHttpException she) {
			throw new WorkspaceBackendException(
					"Failed to create shock node", she);
		}
		return ret;
	}

	@Override
	public void removeBlob(TypeData td) throws DBAuthorizationException,
			WorkspaceBackendException {
		checkAuth();
		try {
			client.deleteNode(td.getShockNodeId());
		} catch (TokenExpiredException ete) {
			//this should be impossible
			throw new RuntimeException("Things are broke", ete);
		} catch (IOException ioe) {
			throw new WorkspaceBackendException(
					"Could not connect to the shock backend: " +
					ioe.getLocalizedMessage(), ioe);
		} catch (ShockHttpException she) {
			//No way to tell ATM if the node was never there or something else happened
			throw new WorkspaceBackendException(
					"Failed to delete shock node: " +
					she.getLocalizedMessage(), she);
		}
	}

	@Override
	public String getExternalIdentifier(TypeData td) {
		return td.getShockNodeId().getId();
	}

	@Override
	public String getStoreType() {
		return "Shock";
	}
}
