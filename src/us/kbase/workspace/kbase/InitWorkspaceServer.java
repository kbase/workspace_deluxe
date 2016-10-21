package us.kbase.workspace.kbase;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.marshall.MarshallingException;

import com.mongodb.DB;
import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;

import us.kbase.abstracthandle.AbstractHandleClient;
import us.kbase.auth.AuthConfig;
import us.kbase.auth.AuthException;
import us.kbase.auth.AuthToken;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.mongo.exceptions.MongoAuthException;
import us.kbase.common.service.ServerException;
import us.kbase.handlemngr.HandleMngrClient;
import us.kbase.typedobj.core.LocalTypeProvider;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.WorkspaceDBException;
import us.kbase.workspace.database.mongo.BlobStore;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.ShockBlobStore;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreException;


public class InitWorkspaceServer {
	
	//TODO AUTH LATER Remove Refreshing tokens.
	//TODO TEST unittests
	
	private static final int TOKEN_REFRESH_INTERVAL_SEC = 24 * 60 * 60;
	private static final String COL_SETTINGS = "settings";
	private static final String COL_SHOCK_NODES = "shock_nodeMap";
	
	
	private static int maxUniqueIdCountPerCall = 100000;

	private static int instanceCount = 0;
	private static boolean wasTempFileCleaningDone = false;
	
	public static abstract class InitReporter {
		
		private boolean failed = false;
		
		public abstract void reportInfo(final String info);
		
		public void reportFail(final String fail) {
			failed = true;
			handleFail(fail);
		}
		
		public abstract void handleFail(final String fail);
		
		public boolean isFailed() {
			return failed;
		}
	}
	
	public static class WorkspaceInitResults {
		private Workspace ws;
		private WorkspaceServerMethods wsmeth;
		private WorkspaceAdministration wsadmin;
		private Types types;
		private URL handleManagerUrl;
		private TokenProvider handleMgrToken;
		
		public WorkspaceInitResults(
				final Workspace ws,
				final WorkspaceServerMethods wsmeth,
				final WorkspaceAdministration wsadmin,
				final Types types,
				final URL handleManagerUrl,
				final TokenProvider handleMgrToken) {
			super();
			this.ws = ws;
			this.wsmeth = wsmeth;
			this.wsadmin = wsadmin;
			this.types = types;
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
		
		public Types getTypes() {
			return types;
		}

		public URL getHandleManagerUrl() {
			return handleManagerUrl;
		}

		public TokenProvider getHandleMgrToken() {
			return handleMgrToken;
		}
	}
	
	public static void setMaximumUniqueIdCountForTests(final int count) {
		maxUniqueIdCountPerCall = count;
	}
	
	public static WorkspaceInitResults initWorkspaceServer(
			final KBaseWorkspaceConfig cfg,
			final InitReporter rep) {
		final TempFilesManager tfm = initTempFilesManager(cfg.getTempDir(),
				rep);
		
		final ConfigurableAuthService auth = setUpAuthClient(cfg, rep);

		TokenProvider handleMgrToken = null;
		if (!cfg.ignoreHandleService()) {
			handleMgrToken = getHandleToken(cfg, rep, auth);
			if (!rep.isFailed()) {
				checkHandleServiceConnection(cfg.getHandleServiceURL(),
						handleMgrToken, rep);
			}
			if (!rep.isFailed()) {
				checkHandleManagerConnection(cfg.getHandleManagerURL(),
						handleMgrToken, rep);
			}
		}
		
		if (rep.isFailed()) {
			rep.reportFail("Server startup failed - all calls will error out.");
			return null;
		} 
		rep.reportInfo("Starting server using connection parameters:\n" +
				cfg.getParamReport());
		rep.reportInfo("Temporary file location: " + tfm.getTempDir());

		final WorkspaceDependencies wsdeps;
		try {
			wsdeps = getDependencies(cfg, tfm, auth);
		} catch (WorkspaceInitException wie) {
			rep.reportFail(wie.getLocalizedMessage());
			rep.reportFail(
					"Server startup failed - all calls will error out.");
			return null;
		}
		rep.reportInfo(String.format("Initialized %s backend",
				wsdeps.mongoWS.getBackendType()));
		Workspace ws = new Workspace(wsdeps.mongoWS,
				new ResourceUsageConfigurationBuilder().build(),
				new KBaseReferenceParser(), wsdeps.validator);
		Types types = new Types(wsdeps.typeDB);
		WorkspaceServerMethods wsmeth = new WorkspaceServerMethods(
				ws, types, cfg.getHandleServiceURL(),
				maxUniqueIdCountPerCall, auth);
		WorkspaceAdministration wsadmin = new WorkspaceAdministration(
				ws, wsmeth, types, cfg.getWorkspaceAdmin());
		final String mem = String.format(
				"Started workspace server instance %s. Free mem: %s Total mem: %s, Max mem: %s",
				++instanceCount, Runtime.getRuntime().freeMemory(),
				Runtime.getRuntime().totalMemory(),
				Runtime.getRuntime().maxMemory());
		rep.reportInfo(mem);
		return new WorkspaceInitResults(
				ws, wsmeth, wsadmin, types, cfg.getHandleManagerURL(),
				handleMgrToken);
	}
	
	private static class WorkspaceDependencies {
		public TypeDefinitionDB typeDB;
		public TypedObjectValidator validator;
		public WorkspaceDatabase mongoWS;
	}
	
	private static WorkspaceDependencies getDependencies(
			final KBaseWorkspaceConfig cfg,
			final TempFilesManager tfm,
			final ConfigurableAuthService auth)
			throws WorkspaceInitException {
		
		final WorkspaceDependencies deps = new WorkspaceDependencies();
		
		final DB db = getMongoDBInstance(cfg.getHost(), cfg.getDBname(),
				cfg.getMongoUser(), cfg.getMongoPassword(),
				cfg.getMongoReconnectAttempts());
		
		final Settings settings = getSettings(db);
		final String bsType = settings.isGridFSBackend() ? "GridFS" : "Shock";
		
		final BlobStore bs = setupBlobStore(db, bsType, settings.getShockUrl(),
				settings.getShockUser(), cfg, auth);
		
		final DB typeDB = getMongoDBInstance(cfg.getHost(),
				settings.getTypeDatabase(), cfg.getMongoUser(),
				cfg.getMongoPassword(), cfg.getMongoReconnectAttempts());
		
		try {
			deps.typeDB = new TypeDefinitionDB(new MongoTypeStorage(typeDB));
		} catch (TypeStorageException e) {
			throw new WorkspaceInitException("Couldn't set up the type database: "
					+ e.getLocalizedMessage(), e);
		}
		deps.validator = new TypedObjectValidator(
				new LocalTypeProvider(deps.typeDB));
		try {
			deps.mongoWS = new MongoWorkspaceDB(db, bs, tfm);
		} catch (WorkspaceDBException wde) {
			throw new WorkspaceInitException(
					"Error initializing the workspace database: " +
					wde.getLocalizedMessage(), wde);
		}
		return deps;
	}
	
	private static TokenProvider getBackendToken(
			final String shockUserFromSettings,
			final KBaseWorkspaceConfig cfg,
			final ConfigurableAuthService auth)
			throws WorkspaceInitException {
		//TODO AUTH LATER remove refreshing token
		
		try {
			if (cfg.getBackendToken() == null) {
				@SuppressWarnings("deprecation")
				final TokenProvider tp = new TokenProvider(
						auth.getRefreshingToken(shockUserFromSettings,
								cfg.getBackendSecret(),
								TOKEN_REFRESH_INTERVAL_SEC),
						auth.getConfig().getAuthLoginURL());
				return tp;
			} else {
				final AuthToken t = auth.validateToken(cfg.getBackendToken());
				if (!t.getUserName().equals(shockUserFromSettings)) {
					throw new WorkspaceInitException(String.format(
							"The username from the backend token, %s, does " +
							"not match the backend username stored in the " +
							"database, %s",
							t.getUserName(), shockUserFromSettings));
				}
				return new TokenProvider(t);
			}
		} catch (AuthException e) {
			throw new WorkspaceInitException(
					"Couldn't log in with backend credentials for user " +
					shockUserFromSettings + ": " + e.getMessage(), e);
		} catch (IOException e) {
			throw new WorkspaceInitException(
					"Couldn't contact the auth service to obtain a token for the backend: "
					+ e.getMessage(), e);
		}
	}

	private static BlobStore setupBlobStore(
			final DB db,
			final String blobStoreType,
			final String blobStoreURL,
			final String shockUserFromSettings,
			final KBaseWorkspaceConfig cfg,
			final ConfigurableAuthService auth)
			throws WorkspaceInitException {
		
		if (blobStoreType.equals("GridFS")) {
			return new GridFSBlobStore(db);
		}
		if (blobStoreType.equals("Shock")) {
			final URL shockurl;
			try {
				shockurl = new URL(blobStoreURL);
			} catch (MalformedURLException mue) {
				throw new WorkspaceInitException(
						"Workspace database settings document has bad shock url: "
						+ blobStoreURL, mue);
			}
			final TokenProvider token = getBackendToken(
					shockUserFromSettings, cfg, auth);
			try {
				return new ShockBlobStore(db.getCollection(COL_SHOCK_NODES),
						shockurl, token);
			} catch (BlobStoreAuthorizationException e) {
				throw new WorkspaceInitException(
						"Not authorized to access the blob store backend database: "
						+ e.getLocalizedMessage(), e);
			} catch (BlobStoreException e) {
				throw new WorkspaceInitException(
						"The blob store backend database could not be initialized: " +
						e.getLocalizedMessage(), e);
			}
		}
		throw new WorkspaceInitException("Unknown backend type: " + blobStoreType);
	}

	private static DB getMongoDBInstance(final String host, final String dbs,
			final String user, final String pwd, final int mongoReconnectRetry)
			throws WorkspaceInitException {
		try {
		if (user != null) {
			return GetMongoDB.getDB(
					host, dbs, user, pwd, mongoReconnectRetry, 10);
		} else {
			return GetMongoDB.getDB(host, dbs, mongoReconnectRetry, 10);
		}
		} catch (InterruptedException ie) {
			throw new WorkspaceInitException(
					"Connection to MongoDB was interrupted. This should never "
					+ "happen and indicates a programming problem. Error: " +
					ie.getLocalizedMessage(), ie);
		} catch (UnknownHostException uhe) {
			throw new WorkspaceInitException("Couldn't find mongo host "
					+ host + ": " + uhe.getLocalizedMessage(), uhe);
		} catch (IOException | MongoTimeoutException e) {
			throw new WorkspaceInitException("Couldn't connect to mongo host " 
					+ host + ": " + e.getLocalizedMessage(), e);
		} catch (MongoException e) {
			throw new WorkspaceInitException(
					"There was an error connecting to the mongo database: " +
					e.getLocalizedMessage());
		} catch (MongoAuthException ae) {
			throw new WorkspaceInitException("Not authorized for mongo database "
					+ dbs + ": " + ae.getLocalizedMessage(), ae);
		} catch (InvalidHostException ihe) {
			throw new WorkspaceInitException(host +
					" is an invalid mongo database host: "  +
					ihe.getLocalizedMessage(), ihe);
		}
	}
	
	private static Settings getSettings(final DB db)
			throws WorkspaceInitException {
		
		if (!db.collectionExists(COL_SETTINGS)) {
			throw new WorkspaceInitException(
					"There is no settings collection in the workspace database");
		}
		MongoCollection settings = new Jongo(db).getCollection(COL_SETTINGS);
		if (settings.count() != 1) {
			throw new WorkspaceInitException(
					"More than one settings document exists in the workspace database settings collection");
		}
		final Settings wsSettings;
		try {
			wsSettings = settings.findOne().as(Settings.class);
		} catch (MarshallingException me) {
			Throwable ex = me.getCause();
			if (ex == null) {
				throw new WorkspaceInitException(
						"Unable to unmarshal settings workspace database document: " +
						me.getLocalizedMessage(), me);
			}
			ex = ex.getCause();
			if (ex == null || !(ex instanceof CorruptWorkspaceDBException)) {
				throw new WorkspaceInitException(
						"Unable to unmarshal settings workspace database document", me);
			}
			throw new WorkspaceInitException(
					"Unable to unmarshal settings workspace database document: " +
							ex.getLocalizedMessage(), ex);
		}
		if (db.getName().equals(wsSettings.getTypeDatabase())) {
			throw new WorkspaceInitException(
					"The type database name is the same as the workspace database name: "
							+ db.getName());
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
				// check the directory is writeable
				tfm.generateTempFile("startuptest", "tmp");
				wasTempFileCleaningDone = true;
				tfm.cleanup();
			}
			return tfm;
		} catch (Exception e) {
			rep.reportFail("There was an error initializing the temporary " +
					"file location: " + e.getLocalizedMessage());
			return null;
		}
	}
	
	private static TokenProvider getHandleToken(
			final KBaseWorkspaceConfig cfg,
			final InitReporter rep,
			final ConfigurableAuthService auth) {
		//TODO AUTH LATER remove refreshing token
		final boolean token = cfg.getHandleManagerToken() != null;
		try {
			final TokenProvider tp;
			if (token) {
				tp = new TokenProvider(auth.validateToken(
						cfg.getHandleManagerToken()));
			} else {
				@SuppressWarnings("deprecation")
				final TokenProvider tptemp = new TokenProvider(
						auth.getRefreshingToken(cfg.getHandleManagerUser(),
								cfg.getHandleManagerPassword(),
								TOKEN_REFRESH_INTERVAL_SEC),
						auth.getConfig().getAuthLoginURL());
				tp = tptemp;
			}
			return tp;
		} catch (AuthException e) {
			String err = "Couldn't log in with handle manager credentials: ";
			if (token) {
				err += "token was supplied";
			} else {
				err += "user " + cfg.getHandleManagerUser();
			}
			rep.reportFail(err + ": " + e.getMessage());
		} catch (IOException e) {
			rep.reportFail("Couldn't contact the auth service to obtain a token for the handle manager: "
					+ e.getLocalizedMessage());
		}
		return null;
	}
	

	private static void checkHandleServiceConnection(
			final URL handleServiceUrl,
			final TokenProvider handleMgrToken,
			final InitReporter rep) {
		try {
			final AbstractHandleClient cli = new AbstractHandleClient(
					handleServiceUrl, handleMgrToken.getToken());
			if (handleServiceUrl.getProtocol().equals("http")) {
				rep.reportInfo("Warning - the Handle Service url uses insecure http. https is recommended.");
				cli.setIsInsecureHttpConnectionAllowed(true);
			}
			cli.isOwner(new LinkedList<String>());
		} catch (Exception e) {
			if (!(e instanceof ServerException) || !e.getMessage().contains(
							"can not execute select * from Handle")) {
				rep.reportFail("Could not establish a connection to the Handle Service at "
						+ handleServiceUrl + ": " + e.getMessage());
			}
		}
	}
	
	private static void checkHandleManagerConnection(
			final URL handleManagerUrl,
			final TokenProvider handleMgrToken,
			final InitReporter rep) {
		try {
			final HandleMngrClient cli = new HandleMngrClient(
					handleManagerUrl, handleMgrToken.getToken());
			if (handleManagerUrl.getProtocol().equals("http")) {
				rep.reportInfo("Warning - the Handle Manager url uses insecure http. https is recommended.");
				cli.setIsInsecureHttpConnectionAllowed(true);
			}
			cli.setPublicRead(Arrays.asList("FAKEHANDLE_-100"));
		} catch (Exception e) {
			if (!(e instanceof ServerException) || !e.getMessage().contains(
							"Unable to set acl(s) on handles FAKEHANDLE_-100")) {
				rep.reportFail("Could not establish a connection to the Handle Manager Service at "
						+ handleManagerUrl + ": " + e.getMessage());
			}
		}
	}
	
	@SuppressWarnings("deprecation")
	private static ConfigurableAuthService setUpAuthClient(
			final KBaseWorkspaceConfig cfg,
			final InitReporter rep) {
		final AuthConfig c = new AuthConfig();
		if (cfg.getGlobusURL().getProtocol().equals("http")) {
			c.withAllowInsecureURLs(true);
			rep.reportInfo("Warning - the Globus url uses insecure http. https is recommended.");
		}
		if (cfg.getAuthURL().getProtocol().equals("http")) {
			c.withAllowInsecureURLs(true);
			rep.reportInfo("Warning - the Auth Service url uses insecure http. https is recommended.");
		}
		try {
			c.withGlobusAuthURL(cfg.getGlobusURL())
				.withKBaseAuthServerURL(cfg.getAuthURL());
		} catch (URISyntaxException e) {
			throw new RuntimeException("this should be impossible", e);
		}
		final boolean token = cfg.getKBaseAdminToken() != null;
		//TODO AUTH LATER remove refreshing token
		final ConfigurableAuthService auth;
		try {
			auth = new ConfigurableAuthService(c);
			if (token) {
				c.withToken(auth.validateToken(cfg.getKBaseAdminToken()));
			} else {
				c.withRefreshingToken(auth.getRefreshingToken(
						cfg.getKbaseAdminUser(), cfg.getKbaseAdminPassword(),
						TOKEN_REFRESH_INTERVAL_SEC));
			}
			return auth;
		} catch (AuthException e) {
			rep.reportFail("Couldn't log in the KBase administrative user " +
					(token ? "with a token" : cfg.getKbaseAdminUser()) + ": " +
					e.getMessage());
		} catch (IOException e) {
			rep.reportFail("Couldn't connect to authorization service at " +
					c.getAuthServerURL() + " : " + e.getLocalizedMessage());
		}
		return null;
	}

	
	private static class WorkspaceInitException extends Exception {
		
		private static final long serialVersionUID = 1L;

		public WorkspaceInitException(final String message) {
			super(message);
		}
		
		public WorkspaceInitException(final String message,
				final Throwable throwable) {
			super(message, throwable);
		}
	}
}
