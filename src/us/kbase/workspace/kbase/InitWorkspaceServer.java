package us.kbase.workspace.kbase;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.marshall.MarshallingException;

import com.mongodb.DB;

import us.kbase.abstracthandle.AbstractHandleClient;
import us.kbase.auth.AuthConfig;
import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.auth.RefreshingToken;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.mongo.exceptions.MongoAuthException;
import us.kbase.common.service.ServerException;
import us.kbase.handlemngr.HandleMngrClient;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.mongo.BlobStore;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.ShockBlobStore;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreException;


public class InitWorkspaceServer {
	
	//TODO move files around appropriately
	
	private static final int TOKEN_REFRESH_INTERVAL_SEC = 24 * 60 * 60;
	private static final String COL_SETTINGS = "settings";
	private static final String COL_SHOCK_NODES = "shock_nodeMap";
	
	
	private static int maxUniqueIdCountPerCall = 100000;

	private static int instanceCount = 0;
	private static boolean wasTempFileCleaningDone = false;
	
	public interface InitReporter {
		public void reportInfo(final String info);
		public void reportFail(final String fail);
	}
	
	public static class WorkspaceInitResults {
		private Workspace ws;
		private WorkspaceServerMethods wsmeth;
		private WorkspaceAdministration wsadmin;
		private TempFilesManager tfm;
		private URL handleManagerUrl;
		private RefreshingToken handleMgrToken;
		
		public WorkspaceInitResults(
				final Workspace ws,
				final WorkspaceServerMethods wsmeth,
				final WorkspaceAdministration wsadmin,
				final TempFilesManager tfm,
				final URL handleManagerUrl,
				final RefreshingToken handleMgrToken) {
			super();
			this.ws = ws;
			this.wsmeth = wsmeth;
			this.wsadmin = wsadmin;
			this.tfm = tfm;
			this.handleManagerUrl = handleManagerUrl;
			this.handleMgrToken = handleMgrToken;
		}

		public Workspace getWs() {
			return ws;
		}

		public WorkspaceServerMethods getWsmeth() {
			return wsmeth;
		}

		public WorkspaceAdministration getWsAdmin() {
			return wsadmin;
		}

		public TempFilesManager getTempFilesManager() {
			return tfm;
		}

		public URL getHandleManagerUrl() {
			return handleManagerUrl;
		}

		public RefreshingToken getHandleMgrToken() {
			return handleMgrToken;
		}
	}
	
	public static void setMaximumUniqueIdCountForTests(final int count) {
		maxUniqueIdCountPerCall = count;
	}
	
	public static WorkspaceInitResults initWorkspaceServer(
			final KBaseWorkspaceConfig cfg,
			final InitReporter rep) {
		
		//TODO pretty sure the logic here is messed up, check
		TempFilesManager tfm = initTempFilesManager(cfg.getTempDir(), rep);
		boolean failed = tfm == null;
		
		RefreshingToken handleMgrToken = null;
		if (!cfg.ignoreHandleService()) {
			handleMgrToken = getHandleToken(cfg, rep);
			failed = failed || handleMgrToken == null;
			if (!failed) {
				failed = handleServiceConnectionFails(cfg.getHandleServiceURL(),
						handleMgrToken, rep);
			}
			if (!failed) {
				failed = handleManagerConnectionFails(cfg.getHandleManagerURL(),
						handleMgrToken, rep);
			}
		}
		
		if (failed) {
			rep.reportFail("Server startup failed - all calls will error out.");
			return null;
		} 
		rep.reportInfo("Starting server using connection parameters:\n" +
				cfg.getParamReport());
		rep.reportInfo("Temporary file location: " + tfm.getTempDir());

		final WorkspaceDatabase db = getDB(
				cfg.getHost(), cfg.getDBname(), cfg.getBackendSecret(),
				cfg.getMongoUser(), cfg.getMongoPassword(), tfm,
				cfg.getMongoReconnectAttempts(), rep);
		if (db == null) {
			rep.reportFail(
					"Server startup failed - all calls will error out.");
			return null;
		}
		rep.reportInfo(String.format("Initialized %s backend",
				db.getBackendType()));
		Workspace ws = new Workspace(db,
				new ResourceUsageConfigurationBuilder().build(),
				new KBaseReferenceParser());
		WorkspaceServerMethods wsmeth = new WorkspaceServerMethods(
				ws, cfg.getHandleServiceURL(),
				maxUniqueIdCountPerCall,
				setUpAuthClient(cfg.getKbaseAdminUser(),
						cfg.getKbaseAdminPassword(), rep));
		WorkspaceAdministration wsadmin = new WorkspaceAdministration(
				ws, wsmeth, cfg.getWorkspaceAdmin());
		final String mem = String.format(
				"Started workspace server instance %s. Free mem: %s Total mem: %s, Max mem: %s",
				++instanceCount, Runtime.getRuntime().freeMemory(),
				Runtime.getRuntime().totalMemory(),
				Runtime.getRuntime().maxMemory());
		rep.reportInfo(mem);
		return new WorkspaceInitResults(
				ws, wsmeth, wsadmin, tfm, cfg.getHandleManagerURL(),
				handleMgrToken);
	}
	
	private static WorkspaceDatabase getDB(final String host, final String dbs,
			final String secret, final String user, final String pwd,
			final TempFilesManager tfm, final int mongoReconnectRetry,
			final InitReporter rep) {
		
		final DB db = getMongoDBInstance(host, dbs, user, pwd,
				mongoReconnectRetry, rep);
		if (db == null) {return null;}
		
		final Settings settings = getSettings(db, rep);
		if (settings == null) {return null;}
		
		final String bsType = settings.isGridFSBackend() ? "GridFS" : "Shock";
		final BlobStore bs = setupBlobStore(db, bsType, settings.getShockUrl(),
				settings.getShockUser(), secret, rep);
		if (bs == null) {return null;}
		
		final DB typeDB = getMongoDBInstance(host, settings.getTypeDatabase(),
				user, pwd, mongoReconnectRetry, rep);
		if (typeDB == null) {return null;}
		
		TypedObjectValidator typeValidator = null;
		try {
			typeValidator = new TypedObjectValidator(new TypeDefinitionDB(
					new MongoTypeStorage(typeDB)));
		} catch (TypeStorageException e) {
			rep.reportFail("Couldn't set up the type database: " +
					e.getLocalizedMessage());
			return null;
		}
		
		return new MongoWorkspaceDB(db, bs, tfm, typeValidator);
	}
	
	private static BlobStore setupBlobStore(
			final DB db,
			final String blobStoreType,
			final String blobStoreURL,
			final String blobStoreUser,
			final String blobStoreSecret,
			final InitReporter rep) {
		
		if (blobStoreType.equals("GridFS")) {
			return new GridFSBlobStore(db);
		}
		if (blobStoreType.equals("Shock")) {
			URL shockurl = null;
			try {
				shockurl = new URL(blobStoreURL);
			} catch (MalformedURLException mue) {
				rep.reportFail("Workspace database settings document has bad shock url: "
								+ blobStoreURL);
				return null;
			}
			BlobStore bs = null;
			try {
				bs = new ShockBlobStore(db.getCollection(COL_SHOCK_NODES),
						shockurl, blobStoreUser, blobStoreSecret);
			} catch (BlobStoreAuthorizationException e) {
				rep.reportFail(
						"Not authorized to access the blob store backend database: "
						+ e.getLocalizedMessage());
			} catch (BlobStoreException e) {
				rep.reportFail(
						"The blob store backend database could not be initialized: " +
						e.getLocalizedMessage());
			}
			// TODO if shock, check a few random nodes to make sure they match
			// the internal representation, die otherwise
			return bs;
		}
		rep.reportFail("Unknown backend type: " + blobStoreType);
		return null;
	}

	private static DB getMongoDBInstance(final String host, final String dbs,
			final String user, final String pwd, final int mongoReconnectRetry,
			final InitReporter rep) {
		DB db = null;
		try {
		if (user != null) {
			db = GetMongoDB.getDB(
					host, dbs, user, pwd, mongoReconnectRetry, 10);
		} else {
			db = GetMongoDB.getDB(host, dbs, mongoReconnectRetry, 10);
		}
		} catch (InterruptedException ie) {
			rep.reportFail("Connection to MongoDB was interrupted. This should never " +
					"happen and indicates a programming problem. Error: " +
					ie.getLocalizedMessage());
		} catch (UnknownHostException uhe) {
			rep.reportFail("Couldn't find mongo host " + host + ": " +
					uhe.getLocalizedMessage());
		} catch (IOException io) {
			rep.reportFail("Couldn't connect to mongo host " + host + ": " +
					io.getLocalizedMessage());
		} catch (MongoAuthException ae) {
			rep.reportFail("Not authorized for mongo database " + dbs + ": " +
					ae.getLocalizedMessage());
		} catch (InvalidHostException ihe) {
			rep.reportFail(host + " is an invalid mongo database host: "  +
					ihe.getLocalizedMessage());
		}
		return db;
	}
	
	private static Settings getSettings(final DB db, final InitReporter rep) {
		
		if (!db.collectionExists(COL_SETTINGS)) {
			rep.reportFail(
					"There is no settings collection in the workspace database");
			return null;
		}
		MongoCollection settings = new Jongo(db).getCollection(COL_SETTINGS);
		if (settings.count() != 1) {
			rep.reportFail(
					"More than one settings document exists in the workspace database settings collection");
			return null;
		}
		Settings wsSettings = null;
		try {
			wsSettings = settings.findOne().as(Settings.class);
		} catch (MarshallingException me) {
			Throwable ex1 = me.getCause();
			if (ex1 == null) {
				rep.reportFail(
						"Unable to unmarshal settings workspace database document: " +
						me.getLocalizedMessage());
			} else {
				Throwable ex2 = ex1.getCause();
				if (ex2 == null) {
					ex2 = ex1;
				}
				rep.reportFail(
						"Unable to unmarshal settings workspace database document: " +
								ex2.getLocalizedMessage());
			}
			return null;
		}
		if (db.getName().equals(wsSettings.getTypeDatabase())) {
			rep.reportFail(
					"The type database name is the same as the workspace database name: "
							+ db.getName());
			return null;
		}
		return wsSettings;
	}

	private static TempFilesManager initTempFilesManager(
			final String tempDir,
			final InitReporter rep) {
		try {
			final TempFilesManager tfm = new TempFilesManager(
					new File(tempDir));
			if (!wasTempFileCleaningDone) {
				wasTempFileCleaningDone = true;
				tfm.cleanup();
			}
			return tfm;
		} catch (Exception e) {
			rep.reportFail(e.getLocalizedMessage());
			return null;
		}
	}
	
	private static RefreshingToken getHandleToken(
			final KBaseWorkspaceConfig cfg,
			final InitReporter rep) {
		try {
			return AuthService.getRefreshingToken(cfg.getHandleManagerUser(),
					cfg.getHandleManagerPassword(), TOKEN_REFRESH_INTERVAL_SEC);
		} catch (AuthException e) {
			rep.reportFail("Couldn't log in with handle manager credentials for user " +
					cfg.getHandleManagerUser() + ": " + e.getLocalizedMessage());
		} catch (IOException e) {
			rep.reportFail("Couldn't contact the auth service to obtain a token for the handle manager: "
					+ e.getLocalizedMessage());
		}
		return null;
	}
	

	private static boolean handleServiceConnectionFails(
			final URL handleServiceUrl,
			final RefreshingToken handleMgrToken,
			final InitReporter rep) {
		try {
			final AbstractHandleClient cli = new AbstractHandleClient(
					handleServiceUrl, handleMgrToken.getToken());
			if (handleServiceUrl.getProtocol().equals("http")) {
				rep.reportInfo("Warning - the Handle Service url uses insecure http. https is recommended.");
				cli.setIsInsecureHttpConnectionAllowed(true);
			}
			cli.areReadable(new LinkedList<String>());
		} catch (Exception e) {
			if (!(e instanceof ServerException) ||
					!e.getMessage().contains(
							"can not execute select * from Handle")) {
				rep.reportFail("Could not establish a connection to the Handle Service at "
						+ handleServiceUrl + ": " + e.getMessage());
				return true;
			}
		}
		return false;
	}
	
	private static boolean handleManagerConnectionFails(
			final URL handleManagerUrl,
			final RefreshingToken handleMgrToken,
			final InitReporter rep) {
		try {
			final HandleMngrClient cli = new HandleMngrClient(
					handleManagerUrl, handleMgrToken.getToken());
			if (handleManagerUrl.getProtocol().equals("http")) {
				rep.reportInfo("Warning - the Handle Manager url uses insecure http. https is recommended.");
				cli.setIsInsecureHttpConnectionAllowed(true);
			}
			cli.addReadAcl(Arrays.asList("FAKEHANDLE_-100"), "fakeuser");
		} catch (Exception e) {
			if (!(e instanceof ServerException) ||
					!e.getMessage().contains(
							"Unable to set acl(s) on handles FAKEHANDLE_-100")) {
				rep.reportFail("Could not establish a connection to the Handle Manager Service at "
						+ handleManagerUrl + ": " + e.getMessage());
				return true;
			}
		}
		return false;
	}
	
	private static ConfigurableAuthService setUpAuthClient(
			final String kbaseAdminUser,
			final String kbaseAdminPwd,
			final InitReporter rep) {
		AuthConfig c = new AuthConfig();
		ConfigurableAuthService auth;
		try {
			auth = new ConfigurableAuthService(c);
			c.withRefreshingToken(auth.getRefreshingToken(
					kbaseAdminUser, kbaseAdminPwd,
					TOKEN_REFRESH_INTERVAL_SEC));
			return auth;
		} catch (AuthException e) {
			rep.reportFail("Couldn't log in the KBase administrative user " +
					kbaseAdminUser + " : " + e.getLocalizedMessage());
		} catch (IOException e) {
			rep.reportFail("Couldn't connect to authorization service at " +
					c.getAuthServerURL() + " : " + e.getLocalizedMessage());
		}
		return null;
	}

}
