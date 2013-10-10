package us.kbase.workspace.database.mongo;

import java.io.IOException;
import java.net.URL;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

public class ShockBackend implements BlobStore {
	
	private String user;
	private String password;
	private BasicShockClient client;
	private DBCollection mongoCol;
	
	private static final String IDX_UNIQ = "unique";
	
	public ShockBackend(DBCollection collection, URL url, String user,
			String password) throws BlobStoreAuthorizationException,
			BlobStoreException {
		if (collection == null) {
			throw new NullPointerException("Collection cannot be null");
		}
		this.mongoCol = collection;
		final DBObject dbo = new BasicDBObject();
		dbo.put(Fields.SHOCK_CHKSUM, 1);
		final DBObject opts = new BasicDBObject();
		opts.put(IDX_UNIQ, 1);
		mongoCol.ensureIndex(dbo, opts);
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
		final AuthUser u;
		try {
			u = AuthService.login(user, password);
		} catch (AuthException ae) {
			throw new BlobStoreAuthorizationException(
					"Could not authenticate backend user " + user, ae);
		} catch (IOException ioe) {
			throw new BlobStoreCommunicationException(
					"Could not connect to the shock backend auth provider: " +
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
	public void saveBlob(MD5 md5, String data) throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException {
		if(data == null) {
			throw new IllegalArgumentException("data cannot be null");
		}
		try {
			getNode(md5);
			return; //already saved
		} catch (NoSuchBlobException nb) {
			//go ahead, need to save
		}
		checkAuth();
		final ShockNode sn;
		try {
			sn = client.addNode(data.getBytes(), "workspace_" + md5.getMD5());
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
		dbo.put(Fields.SHOCK_CHKSUM, md5.getMD5());
		try {
			dbo.put(Fields.SHOCK_NODE, sn.getId().getId());
			dbo.put(Fields.SHOCK_VER, sn.getVersion().getVersion());
		} catch (ShockNodeDeletedException d) {
			throw new RuntimeException("Shock is returning deleted nodes");
		}
		final DBObject query = new BasicDBObject();
		query.put(Fields.SHOCK_CHKSUM, md5.getMD5());
		try {
			mongoCol.update(query, dbo, true, false);
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not write to the mongo database", me);
		}
	}
	
	private String getNode(MD5 md5) throws
			BlobStoreCommunicationException, NoSuchBlobException {
		final DBObject query = new BasicDBObject();
		query.put(Fields.SHOCK_CHKSUM, md5.getMD5());
		final DBObject ret;
		try {
			ret = mongoCol.findOne(query);
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not read from the mongo database", me);
		}
		if (ret == null) {
			throw new NoSuchBlobException("No blob saved with chksum "
					+ md5.getMD5());
		}
		return (String) ret.get(Fields.SHOCK_NODE);
	}

	@Override
	public String getBlob(MD5 md5) throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException, NoSuchBlobException {
		checkAuth();
		final String node = getNode(md5);
		
		final String ret;
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
	public void removeBlob(MD5 md5) throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException {
		checkAuth();
		final String node;
		try {
			node = getNode(md5);
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
		query.put(Fields.SHOCK_CHKSUM, md5.getMD5());
		mongoCol.remove(query);
	}
	
	/**
	 * this is for testing purposes only - leave Shock in the state we found it
	 */
	public void removeAllBlobs() throws BlobStoreCommunicationException,
			BlobStoreAuthorizationException {
		final DBCursor ret;
		try {
			ret = mongoCol.find();
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not read from the mongo database", me);
		}
		for (final DBObject o: ret) {
			removeBlob(new MD5((String) o.get(Fields.SHOCK_CHKSUM)));
		}
		
	}

	@Override
	public String getExternalIdentifier(MD5 md5) throws
			BlobStoreCommunicationException, NoSuchBlobException {
		return getNode(md5);
	}

	@Override
	public String getStoreType() {
		return "Shock";
	}
}
