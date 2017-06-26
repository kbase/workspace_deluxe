package us.kbase.workspace.database.mongo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.slf4j.LoggerFactory;

import us.kbase.auth.AuthToken;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.typedobj.core.MD5;
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
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

public class ShockBlobStore implements BlobStore {
	
	private final BasicShockClient client;
	private final DBCollection mongoCol;
	
	private static final String IDX_UNIQ = "unique";
	
	public ShockBlobStore(
			final DBCollection mongoCollection,
			final URL url,
			final AuthToken token)
			throws BlobStoreAuthorizationException, BlobStoreException {
		if (mongoCollection == null || url == null || token == null) {
			throw new NullPointerException("Arguments cannot be null");
		}
		this.mongoCol = mongoCollection;
		final DBObject dbo = new BasicDBObject();
		dbo.put(Fields.SHOCK_CHKSUM, 1);
		final DBObject opts = new BasicDBObject();
		opts.put(IDX_UNIQ, 1);
		mongoCol.createIndex(dbo, opts);
		try {
			client = new BasicShockClient(url, token);
		} catch (InvalidShockUrlException isue) {
			throw new BlobStoreException(
					"The shock url " + url + " is invalid", isue);
		} catch (ShockHttpException she) {
			throw new BlobStoreException(
					"Shock appears to be misconfigured - the client could not initialize. Shock said: "
					+ she.getLocalizedMessage(), she);
		} catch (IOException ioe) {
			throw new BlobStoreCommunicationException(
					"Could not connect to the shock backend: " +
					ioe.getLocalizedMessage(), ioe);
		}
		//TODO DBCONSIST check that a few nodes exist to ensure we're pointing at the right Shock instance
	}
	
	@Override
	public List<DependencyStatus> status() {
		//note failures are tested manually for now, if you make changes test
		//things still work
		//TODO TEST add tests exercising failures
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
	
	@Override
	public void saveBlob(final MD5 md5, final InputStream data,
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
		final ShockNode sn;
		try {
			sn = client.addNode(data, "workspace_" + md5.getMD5(), "JSON");
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
		final DBObject entry = getBlobEntry(md5);
		final String node = (String)entry.get(Fields.SHOCK_NODE);
		final boolean sorted;
		if (!entry.containsField(Fields.SHOCK_SORTED)) {
			sorted = false;
		} else {
			sorted = (Boolean)entry.get(Fields.SHOCK_SORTED);
		}
		try {
			return bafcMan.createBAFC(client.getFile(new ShockNodeId(node)),
					true, sorted);
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
	}

	@Override
	public void removeBlob(final MD5 md5)
			throws BlobStoreAuthorizationException,
			BlobStoreCommunicationException {
		final String node;
		try {
			node = getNode(md5);
		} catch (NoSuchBlobException nb) {
			return; //already gone
		}
		
		try {
			client.deleteNode(new ShockNodeId(node));
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
}
