package us.kbase.workspace.database.mongo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.slf4j.LoggerFactory;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.RefreshingToken;
import us.kbase.auth.TokenExpiredException;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.Writable;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.exceptions.FileCacheIOException;
import us.kbase.workspace.database.exceptions.FileCacheLimitExceededException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gc.iotools.stream.base.ExecutionModel;
import com.gc.iotools.stream.base.ExecutorServiceFactory;
import com.gc.iotools.stream.os.OutputStreamToInputStream;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

public class ShockBlobStore implements BlobStore {
	
	private final BasicShockClient client;
	private final DBCollection mongoCol;
	private final RefreshingToken token;
	
	private static final int TOKEN_REFRESH_INTERVAL = 24 * 60 * 60;
	private static final String IDX_UNIQ = "unique";
	
	public ShockBlobStore(final DBCollection mongoCollection,
			final URL url, final String user, final String password)
			throws BlobStoreAuthorizationException,
			BlobStoreException {
		if (mongoCollection == null || url == null
				|| user == null || password == null) {
			throw new NullPointerException(
					"Arguments cannot be null");
		}
		this.mongoCol = mongoCollection;
		final DBObject dbo = new BasicDBObject();
		dbo.put(Fields.SHOCK_CHKSUM, 1);
		final DBObject opts = new BasicDBObject();
		opts.put(IDX_UNIQ, 1);
		mongoCol.createIndex(dbo, opts);
		token = getToken(user, password);
		try {
			client = new BasicShockClient(url, getToken());
		} catch (InvalidShockUrlException isue) {
			throw new BlobStoreException(
					"The shock url " + url + " is invalid", isue);
		} catch (ShockHttpException she) {
			throw new BlobStoreException(
					"Shock appears to be misconfigured - the client could not initialize. Shock said: "
					+ she.getLocalizedMessage(), she);
		} catch (TokenExpiredException ete) {
			throw new BlobStoreException( //uh... this should never happen
					"The token retrieved from the auth service is already " +
					"expired", ete);
		} catch (IOException ioe) {
			throw new BlobStoreCommunicationException(
					"Could not connect to the shock backend: " +
					ioe.getLocalizedMessage(), ioe);
		}
		//TODO check that a few nodes exist to ensure we're pointing at the right Shock instance
	}
	
	@Override
	public List<DependencyStatus> status() {
		//note failures are tested manually for now, if you make changes test
		//things still work
		final String version;
		try {
			version = client.getRemoteVersion();
		} catch (IOException e) {
			LoggerFactory.getLogger(getClass())
				.error("Failed to connect to Shock", e);
			return Arrays.asList(new DependencyStatus(
					false, "Cannot connect to Shock: " +
					e.getMessage(), "Shock", "Unknown"));
		} catch (InvalidShockUrlException e) {
			LoggerFactory.getLogger(getClass())
				.error("Invalid Shock URL", e);
			return Arrays.asList(new DependencyStatus(
					false, "Invalid Shock URL: " +
					e.getMessage(), "Shock", "Unknown"));
		}
		return Arrays.asList(new DependencyStatus(
				true, "OK", "Shock", version));
	}
	
	private RefreshingToken getToken(final String user, final String pwd)
			throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException {
		try {
			return AuthService.getRefreshingToken(
					user, pwd, TOKEN_REFRESH_INTERVAL);
		} catch (AuthException ae) {
			throw new BlobStoreAuthorizationException(
					"Could not authenticate backend user", ae);
		} catch (IOException ioe) {
			throw new BlobStoreCommunicationException(
					"Could not connect to the shock backend auth provider: " +
					ioe.getLocalizedMessage(), ioe);
		}
	}
	
	private AuthToken getToken()
			throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException {
		try {
			return token.getToken();
		} catch (AuthException ae) {
			throw new BlobStoreAuthorizationException(
					"Could not authenticate backend user", ae);
		} catch (IOException ioe) {
			throw new BlobStoreCommunicationException(
					"Could not connect to the shock backend auth provider: " +
							ioe.getLocalizedMessage(), ioe);
		}
	}
	
	private void updateAuth()
			throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException {
		try {
			client.updateToken(getToken());
		} catch (TokenExpiredException ete) {
			throw new RuntimeException(
					"Auth service is handing out expired tokens", ete);
		}
	}

	@Override
	public void saveBlob(final MD5 md5, final Writable data,
			final boolean sorted)
			throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException {
		if (md5 == null || data == null) {
			throw new NullPointerException("Arguments cannot be null");
		}
		try {
			getNode(md5);
			return; //already saved
		} catch (NoSuchBlobException nb) {
			//go ahead, need to save
		}
		updateAuth();
		final ShockNode sn;
		final OutputStreamToInputStream<ShockNode> osis =
				new OutputStreamToInputStream<ShockNode>() {
					
			@Override
			protected ShockNode doRead(InputStream is) throws Exception {
				final ShockNode sn;
				try {
					sn = client.addNode(is, "workspace_" + md5.getMD5(),
							"JSON");
				} catch (TokenExpiredException ete) {
					//this should be impossible
					throw new RuntimeException("Token magically expired: "
							+ ete.getLocalizedMessage(), ete);
				} catch (JsonProcessingException jpe) {
					//this should be impossible
					throw new RuntimeException("Attribute serialization failed: "
							+ jpe.getLocalizedMessage(), jpe);
				} catch (IOException ioe) {
					throw new BlobStoreCommunicationException(
							"Could not connect to the shock backend: " +
									ioe.getLocalizedMessage(), ioe);
				} catch (ShockHttpException she) {
					throw new BlobStoreCommunicationException(
							"Failed to create shock node: " +
									she.getLocalizedMessage(), she);
				}
//				is.close(); closing the stream has caused deadlocks in other applications
				return sn;
			}
		};
		try {
			//writes in UTF8
			data.write(osis);
		} catch (IOException ioe) {
			//no way to test this easily, manually tested for now.
			//be sure to test manually if making changes
			if (ioe.getCause().getClass().equals(
					BlobStoreCommunicationException.class)) {
				throw (BlobStoreCommunicationException) ioe.getCause();
			}
			throw new RuntimeException(
					"IO Error during streaming of data to Shock: "
					+ ioe.getLocalizedMessage(), ioe);
		} finally {
			try {
				osis.close();
			} catch (IOException ioe) {
				//no way to test this easily, manually tested for now.
				//be sure to test manually if making changes
				if (ioe.getCause().getClass().equals(
						BlobStoreCommunicationException.class)) {
					throw (BlobStoreCommunicationException) ioe.getCause();
				}
				throw new RuntimeException(
						"Couldn't close Shock output stream: " +
								ioe.getLocalizedMessage(), ioe);
			}
		}
		try {
			sn = osis.getResult();
		} catch (InterruptedException ie) {
			throw new RuntimeException(
					"Interrupt trying to retrieve ShockNode from EasyStream instance: "
					+ ie.getLocalizedMessage(), ie);
		} catch (ExecutionException ee) {
			throw new RuntimeException(
					"Excecution error trying to retrieve ShockNode from EasyStream instance: "
					+ ee.getLocalizedMessage(), ee);
		}
		final DBObject dbo = new BasicDBObject();
		dbo.put(Fields.SHOCK_CHKSUM, md5.getMD5());
		dbo.put(Fields.SHOCK_NODE, sn.getId().getId());
		dbo.put(Fields.SHOCK_VER, sn.getVersion().getVersion());
		dbo.put(Fields.SHOCK_SORTED, sorted);
		final DBObject query = new BasicDBObject();
		query.put(Fields.SHOCK_CHKSUM, md5.getMD5());
		try {
			//possible that this was inserted just prior to saving the object
			//so do update vs. insert since the data must be the same
			mongoCol.update(query, dbo, true, false);
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not write to the mongo database", me);
		}
	}
	
	private String getNode(final MD5 md5) throws
			BlobStoreCommunicationException, NoSuchBlobException {
		return (String) getBlobEntry(md5).get(Fields.SHOCK_NODE);
	}

	private DBObject getBlobEntry(final MD5 md5)
			throws BlobStoreCommunicationException, NoSuchBlobException {
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
		return ret;
	}

	@Override
	public ByteArrayFileCache getBlob(final MD5 md5,
			final ByteArrayFileCacheManager bafcMan)
			throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException, NoSuchBlobException,
			FileCacheLimitExceededException, FileCacheIOException {
		updateAuth();
		final DBObject entry = getBlobEntry(md5);
		final String node = (String)entry.get(Fields.SHOCK_NODE);
		final boolean sorted;
		if (!entry.containsField(Fields.SHOCK_SORTED)) {
			sorted = false;
		} else {
			sorted = (Boolean)entry.get(Fields.SHOCK_SORTED);
		}
		
		final OutputStreamToInputStream<ByteArrayFileCache> osis =
				new OutputStreamToInputStream<ByteArrayFileCache>(true,
						ExecutorServiceFactory.getExecutor(
								ExecutionModel.THREAD_PER_INSTANCE), 10000000) { //speeds up by 2-3x
					
			@Override
			protected ByteArrayFileCache doRead(InputStream is) throws Exception {
				return bafcMan.createBAFC(is, true, sorted);
			}
		};
		try {
			client.getFile(new ShockNodeId(node), osis);
		} catch (TokenExpiredException ete) {
			//this should be impossible
			throw new RuntimeException("Things are broke", ete);
		} catch (IOException ioe) {
			if (ioe.getCause() instanceof FileCacheLimitExceededException) {
				throw (FileCacheLimitExceededException) ioe.getCause();
			}
			if (ioe.getCause() instanceof FileCacheIOException) {
				throw (FileCacheIOException) ioe.getCause();
			}
			throw new BlobStoreCommunicationException(
					"Could not connect to the shock backend: " +
					ioe.getLocalizedMessage(), ioe);
		} catch (ShockHttpException she) {
			throw new BlobStoreCommunicationException(
					"Failed to retrieve shock node: " +
					she.getLocalizedMessage(), she);
		}
		try {
			osis.close();
		} catch (IOException ioe) {
			throw new RuntimeException("Something is broken", ioe);
		}
		try {
			return osis.getResult();
		} catch (InterruptedException ie) {
			throw new RuntimeException("Something is broken", ie);
		} catch (ExecutionException ee) {
			throw new RuntimeException("Something is broken", ee);
		}
	}

	@Override
	public void removeBlob(final MD5 md5)
			throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException {
		updateAuth();
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
			for (final DBObject o: ret) {
				removeBlob(new MD5((String) o.get(Fields.SHOCK_CHKSUM)));
			}
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not read from the mongo database", me);
		}
	}

	@Override
	public String getExternalIdentifier(final MD5 md5) throws
			BlobStoreCommunicationException, NoSuchBlobException {
		return getNode(md5);
	}

	@Override
	public String getStoreType() {
		return "Shock";
	}
}
