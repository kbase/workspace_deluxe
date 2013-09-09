package us.kbase.workspace.database.mongo;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.AuthUser;
import us.kbase.auth.TokenExpiredException;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.ShockNodeDeletedException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;
import us.kbase.workspace.workspaces.TypeId;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

public class ShockBackend implements BlobStore {
	
	private String user;
	private String password;
	private BasicShockClient client;
	private DBCollection mongoCol;
	
	private static final String CHKSUM = "chksum";
	private static final String NODE = "node";
	private static final String VER = "ver";
	
	public ShockBackend(DBCollection collection, URL url, String user,
			String password) throws BlobStoreAuthorizationException,
			BlobStoreException {
		if (collection == null) {
			throw new NullPointerException("Collection cannot be null");
		}
		this.mongoCol = collection;
		final DBObject dbo = new BasicDBObject();
		dbo.put(CHKSUM, 1);
		mongoCol.ensureIndex(dbo);
		this.user = user;
		this.password = password;
		try {
			client = new BasicShockClient(url, getToken());
		} catch (InvalidShockUrlException isue) {
			throw new BlobStoreException(
					"The shock url " + url + " is invalid", isue);
		} catch (TokenExpiredException ete) {
			throw new BlobStoreException( //uh... this should never happen
					"The token retrieved from the auth service is already " +
					"expired", ete);
		} catch (IOException ioe) {
			throw new BlobStoreCommunicationException(
					"Could not connect to the shock backend: " +
					ioe.getLocalizedMessage(), ioe);
		}
	}
	
	private AuthToken getToken() throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException {
		AuthUser u = null;
		try {
			u = AuthService.login(user, password);
		} catch (AuthException ae) {
			throw new BlobStoreAuthorizationException(
					"Could not authenticate backend user " + user, ae);
		} catch (IOException ioe) {
			throw new BlobStoreCommunicationException(
					"Could not connect to the shock backend: " +
					ioe.getLocalizedMessage(), ioe);
		}
		return u.getToken();
	}
	
	private void checkAuth() throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException {
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
	public void saveBlob(TypeData td) throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException {
		try {
			getNode(td);
			return; //already saved
		} catch (NoSuchBlobException nb) {
			//go ahead, need to save
		}
		checkAuth();
		String data = td.getData();
		if(data == null) {
			throw new RuntimeException("No data in typedata object");
		}
		TypeId type = td.getType();
		if(type == null) {
			throw new RuntimeException("No type in typedata object");
		}
//		Map<String, Object> attribs = new HashMap<>();
//		Map<String,Object> workattribs = new HashMap<>();
//		workattribs.put("module", type.getType().getModule());
//		workattribs.put("type", type.getType().getName());
//		workattribs.put("major-version", type.getMajorVersion());
//		workattribs.put("minor-version", type.getMinorVersion());
//		attribs.put("workspace", workattribs);
		ShockNode sn = null;
		try {
			sn = client.addNode(data.getBytes(), "workspace_" + td.getChksum());
		} catch (TokenExpiredException ete) {
			//this should be impossible
			throw new RuntimeException("Things are broke", ete);
		} catch (JsonProcessingException jpe) {
			//this should be impossible
			throw new RuntimeException("Things are broke", jpe);
		} catch (IOException ioe) {
			throw new BlobStoreCommunicationException(
					"Could not connect to the shock backend: " +
					ioe.getLocalizedMessage(), ioe);
		} catch (ShockHttpException she) {
			throw new BlobStoreCommunicationException(
					"Failed to create shock node: " +
					she.getLocalizedMessage(), she);
		}
		final DBObject dbo = new BasicDBObject();
		dbo.put(CHKSUM, td.getChksum());
		try {
			dbo.put(NODE, sn.getId().getId());
			dbo.put(VER, sn.getVersion().getVersion());
		} catch (ShockNodeDeletedException d) {
			throw new RuntimeException("Shock is returning deleted nodes");
		}
		final DBObject query = new BasicDBObject();
		query.put(CHKSUM, td.getChksum());
		try {
			mongoCol.update(query, dbo, true, false);
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not write to the mongo database", me);
		}
//		td.addShockInformation(sn);
	}
	
	private String getNode(TypeData td) throws
			BlobStoreCommunicationException, NoSuchBlobException {
		final DBObject query = new BasicDBObject();
		query.put(CHKSUM, td.getChksum());
		DBObject ret;
		try {
			ret = mongoCol.findOne(query);
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not read from the mongo database", me);
		}
		if (ret == null) {
			throw new NoSuchBlobException("No blob saved with chksum "
					+ td.getChksum());
		}
		return (String) ret.get(NODE);
	}

	@Override
	public String getBlob(TypeData td) throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException, NoSuchBlobException {
		checkAuth();
		final String node = getNode(td);
		
		String ret = null;
		try {
			ret = new String(client.getFile(new ShockNodeId(node)));
		} catch (TokenExpiredException ete) {
			//this should be impossible
			throw new RuntimeException("Things are broke", ete);
		} catch (IOException ioe) {
			throw new BlobStoreCommunicationException(
					"Could not connect to the shock backend: " +
					ioe.getLocalizedMessage(), ioe);
		} catch (ShockHttpException she) {
			throw new BlobStoreCommunicationException(
					"Failed to retrieve shock node", she);
		}
		return ret;
	}

	@Override
	public void removeBlob(TypeData td) throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException {
		checkAuth();
		String node;
		try {
			node = getNode(td);
		} catch (NoSuchBlobException nb) {
			return; //already gone
		}
		
		try {
			client.deleteNode(new ShockNodeId(node));
		} catch (TokenExpiredException ete) {
			//this should be impossible
			throw new RuntimeException("Things are broke", ete);
		} catch (IOException ioe) {
			throw new BlobStoreCommunicationException(
					"Could not connect to the shock backend: " +
					ioe.getLocalizedMessage(), ioe);
		} catch (ShockHttpException she) {
			//No way to tell ATM if the node was never there or something else happened
			throw new BlobStoreCommunicationException(
					"Failed to delete shock node: " +
					she.getLocalizedMessage(), she);
		}
		final DBObject query = new BasicDBObject();
		query.put(CHKSUM, td.getChksum());
		mongoCol.remove(query);
	}

	@Override
	public String getExternalIdentifier(TypeData td) throws
			BlobStoreCommunicationException, NoSuchBlobException {
		return getNode(td);
	}

	@Override
	public String getStoreType() {
		return "Shock";
	}
}
