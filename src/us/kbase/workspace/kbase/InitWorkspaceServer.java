package us.kbase.workspace.kbase;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoDatabase;

import us.kbase.abstracthandle.AbstractHandleClient;
import us.kbase.auth.AuthConfig;
import us.kbase.auth.AuthException;
import us.kbase.auth.AuthToken;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.sampleservice.SampleServiceClient;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.typedobj.core.LocalTypeProvider;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactoryBuilder;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.exceptions.WorkspaceDBException;
import us.kbase.workspace.database.mongo.BlobStore;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.S3BlobStore;
import us.kbase.workspace.database.mongo.S3ClientWithPresign;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.kbase.KBaseWorkspaceConfig.ListenerConfig;
import us.kbase.workspace.kbase.BytestreamIdHandlerFactory.BytestreamClientCloner;
import us.kbase.workspace.kbase.admin.AdministratorHandler;
import us.kbase.workspace.kbase.admin.AdministratorHandlerException;
import us.kbase.workspace.kbase.admin.DefaultAdminHandler;
import us.kbase.workspace.kbase.admin.KBaseAuth2AdminHandler;
import us.kbase.workspace.kbase.admin.WorkspaceAdministration;
import us.kbase.workspace.listener.ListenerInitializationException;
import us.kbase.workspace.listener.WorkspaceEventListener;
import us.kbase.workspace.listener.WorkspaceEventListenerFactory;

public class InitWorkspaceServer {
	
	// TODO TEST unittests... are going to be a real pain.
	// TODO JAVADOC
	// TODO CODE try and clean this mess up a little. Nulls everywhere, long methods.
	//			some places report errors to the InitReporter, some throw exceptions.
	// TODO CODE drop the idea of reporting all errors and going into fail mode.
	//			Just throw an exception immediately, much simpler.
	//			Test what happens in tomcat / Jetty first though.
	// TODO CODE Drop all references to Glassfish and streamline Tomcat setup.
	
	public static final String COL_S3_OBJECTS = "s3_objects";
	
	private static final int ADMIN_CACHE_MAX_SIZE = 100; // seems like more than enough admins
	private static final int ADMIN_CACHE_EXP_TIME_MS = 5 * 60 * 1000; // cache admin role for 5m
	
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
		private final Workspace ws;
		private final WorkspaceServerMethods wsmeth;
		private final WorkspaceAdministration wsadmin;
		private final Types types;
		
		public WorkspaceInitResults(
				final Workspace ws,
				final WorkspaceServerMethods wsmeth,
				final WorkspaceAdministration wsadmin,
				final Types types) {
			super();
			this.ws = ws;
			this.wsmeth = wsmeth;
			this.wsadmin = wsadmin;
			this.types = types;
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
	}
	
	public static void setMaximumUniqueIdCountForTests(final int count) {
		maxUniqueIdCountPerCall = count;
	}
	
	public static WorkspaceInitResults initWorkspaceServer(
			final KBaseWorkspaceConfig cfg,
			final InitReporter rep) {
		final TempFilesManager tfm = initTempFilesManager(cfg.getTempDir(), rep);
		
		final ConfigurableAuthService auth = setUpAuthClient(cfg, rep);
		if (rep.isFailed()) {
			rep.reportFail("Server startup failed - all calls will error out.");
			return null;
		} 
		
		AbstractHandleClient hsc = null;
		if (!cfg.ignoreHandleService()) {
			final AuthToken handleMgrToken = getHandleToken(cfg, rep, auth);
			if (!rep.isFailed()) {
				hsc = getHandleServiceClient(cfg.getHandleServiceURL(), handleMgrToken, rep);
			}
		}
		
		if (rep.isFailed()) {
			rep.reportFail("Server startup failed - all calls will error out.");
			return null;
		} 
		rep.reportInfo("Starting server using connection parameters:\n" + cfg.getParamReport());
		rep.reportInfo("Temporary file location: " + tfm.getTempDir());

		final WorkspaceDependencies wsdeps;
		final AdministratorHandler ah;
		final Workspace ws;
		try {
			wsdeps = getDependencies(cfg, auth, rep);
			// TODO CODE build ws in getDependencies & return in class
			ws = new Workspace(
					wsdeps.mongoWS,
					new ResourceUsageConfigurationBuilder().build(),
					wsdeps.validator,
					tfm,
					wsdeps.listeners);
			ah = getAdminHandler(cfg, ws);
		} catch (WorkspaceInitException | WorkspaceCommunicationException e) {
			e.printStackTrace(System.err);
			rep.reportFail(e.getLocalizedMessage());
			rep.reportFail("Server startup failed - all calls will error out.");
			return null;
		}
		rep.reportInfo(String.format("Initialized %s backend", cfg.getBackendType().name()));
		final Types types = new Types(wsdeps.typeDB);
		final IdReferenceHandlerSetFactoryBuilder builder = IdReferenceHandlerSetFactoryBuilder
				.getBuilder(maxUniqueIdCountPerCall)
				.withFactory(new HandleIdHandlerFactory(hsc))
				.withFactory(wsdeps.shockFac)
				.withFactory(wsdeps.sampleFac)
				.build();
		WorkspaceServerMethods wsmeth = new WorkspaceServerMethods(ws, types, builder, auth);
		WorkspaceAdministration wsadmin = new WorkspaceAdministration(
				wsmeth, types, ah, ADMIN_CACHE_MAX_SIZE, ADMIN_CACHE_EXP_TIME_MS);
		final String mem = String.format(
				"Started workspace server instance %s. Free mem: %s Total mem: %s, Max mem: %s",
				++instanceCount, Runtime.getRuntime().freeMemory(),
				Runtime.getRuntime().totalMemory(),
				Runtime.getRuntime().maxMemory());
		rep.reportInfo(mem);
		return new WorkspaceInitResults(ws, wsmeth, wsadmin, types);
	}
	
	private static AdministratorHandler getAdminHandler(
			final KBaseWorkspaceConfig cfg,
			final Workspace ws)
			throws WorkspaceInitException {
		if (cfg.getAdminReadOnlyRoles().isEmpty() && cfg.getAdminRoles().isEmpty()) {
			final String a = cfg.getWorkspaceAdmin();
			final WorkspaceUser admin = a == null || a.trim().isEmpty() ?
					null : new WorkspaceUser(a);
			return new DefaultAdminHandler(ws, admin);
		} else {
			final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
			cm.setMaxTotal(1000); //perhaps these should be configurable
			cm.setDefaultMaxPerRoute(1000);
			//TODO set timeouts for the client for 1/2m for conn req timeout and std timeout
			final CloseableHttpClient client = HttpClients.custom().setConnectionManager(cm)
					.build();
			try {
				return new KBaseAuth2AdminHandler(
						client,
						cfg.getAuth2URL(),
						cfg.getAdminReadOnlyRoles(),
						cfg.getAdminRoles());
			} catch (URISyntaxException | AdministratorHandlerException e) {
				throw new WorkspaceInitException(
						"Could not start the KBase auth2 adminstrator handler: " +
						e.getMessage(), e);
			}
		}
	}

	private static class WorkspaceDependencies {
		public TypeDefinitionDB typeDB;
		public TypedObjectValidator validator;
		public WorkspaceDatabase mongoWS;
		public BytestreamIdHandlerFactory shockFac;
		public SampleIdHandlerFactory sampleFac;
		public List<WorkspaceEventListener> listeners;
	}
	
	private static WorkspaceDependencies getDependencies(
			final KBaseWorkspaceConfig cfg,
			final ConfigurableAuthService auth,
			final InitReporter rep) // DO NOT use the rep to report failures. Throw instead.
			throws WorkspaceInitException {
		
		final WorkspaceDependencies deps = new WorkspaceDependencies();
		final MongoDatabase db = buildMongo(cfg, cfg.getDBname()).getDatabase(cfg.getDBname());
		
		final BlobStore bs = setupBlobStore(db, cfg, auth);
		
		// see https://jira.mongodb.org/browse/JAVA-2656
		final MongoDatabase typeDB = buildMongo(cfg, cfg.getTypeDBName())
				.getDatabase(cfg.getTypeDBName());
		
		try {
			deps.typeDB = new TypeDefinitionDB(new MongoTypeStorage(typeDB));
		} catch (TypeStorageException e) {
			throw new WorkspaceInitException("Couldn't set up the type database: "
					+ e.getLocalizedMessage(), e);
		}
		deps.validator = new TypedObjectValidator(new LocalTypeProvider(deps.typeDB));
		try {
			deps.mongoWS = new MongoWorkspaceDB(db, bs);
		} catch (WorkspaceDBException wde) {
			throw new WorkspaceInitException(
					"Error initializing the workspace database: " +
					wde.getLocalizedMessage(), wde);
		}
		deps.shockFac = getShockIdHandlerFactory(cfg, auth);
		deps.sampleFac = getSampleIdHandlerFactory(cfg, auth, rep);
		
		deps.listeners = loadListeners(cfg);
		return deps;
	}

	private static BytestreamIdHandlerFactory getShockIdHandlerFactory(
			final KBaseWorkspaceConfig cfg,
			final ConfigurableAuthService auth)
			throws WorkspaceInitException {
		if (cfg.getBytestreamURL() == null) {
			return new BytestreamIdHandlerFactory(null, null);
		}
		final AuthToken shockToken = getKBaseToken(
				cfg.getBytestreamUser(), cfg.getBytestreamToken(), "shock", auth);
		final BasicShockClient bsc;
		try {
			bsc = new BasicShockClient(cfg.getBytestreamURL(), shockToken);
		} catch (InvalidShockUrlException | ShockHttpException | IOException e) {
			throw new WorkspaceInitException(
					"Couldn't contact Shock server configured for Shock ID links: " +
			e.getMessage(), e);
		}
		return new BytestreamIdHandlerFactory(bsc, new BytestreamClientCloner() {
					
					@Override
					public BasicShockClient clone(final BasicShockClient source)
							throws IOException, InvalidShockUrlException {
						return new BasicShockClient(source.getShockUrl());
					}
				});
	}
	
	private static SampleIdHandlerFactory getSampleIdHandlerFactory(
			final KBaseWorkspaceConfig cfg,
			final ConfigurableAuthService auth,
			final InitReporter rep) // DO NOT use the rep to report failures. Throw instead.
			throws WorkspaceInitException {
		if (cfg.getSampleServiceURL() == null) {
			return new SampleIdHandlerFactory(null);
		}
		// We deliberately don't verify a connection to the sample service here, as
		// the sample service has a dependency on the workspace on startup. Checking the
		// connection here would cause a deadlock. Instead it'll fail when someone tries to
		// create or get an object with sample IDs in it.
		// This points to the fact that the sample service and workspace service are
		// tightly coupled, although the workspace service dependency on the sample
		// service is optional.
		final AuthToken t = getToken(cfg.getSampleServiceToken(), auth, "Sample Service");
		final SampleServiceClient cli;
		try {
			cli = new SampleServiceClient(cfg.getSampleServiceURL(), t);
		} catch (UnauthorizedException | IOException e) {
			// these exceptions are not actually thrown by the code. The generated client
			// needs an update
			throw new RuntimeException("It should be impossible for this exception to get "+
					"thrown, but here we are", e);
		}
		if (cfg.getSampleServiceURL().getProtocol().equals("http")) {
			rep.reportInfo("Warning - the Sample Service url uses insecure http. " +
					"https is recommended.");
			cli.setIsInsecureHttpConnectionAllowed(true);
		}
		return new SampleIdHandlerFactory(cli);
	}

	public static MongoClient buildMongo(final KBaseWorkspaceConfig c, final String dbName)
			throws WorkspaceInitException {
		//TODO ZLATER MONGO handle shards & replica sets
		final MongoClientOptions opts = MongoClientOptions.builder()
				.retryWrites(c.getMongoRetryWrites()).build();
		try {
			if (c.getMongoUser() != null) {
				final MongoCredential creds = MongoCredential.createCredential(
						c.getMongoUser(), dbName, c.getMongoPassword().toCharArray());
				// unclear if and when it's safe to clear the password
				return new MongoClient(new ServerAddress(c.getHost()), creds, opts);
			} else {
				return new MongoClient(new ServerAddress(c.getHost()), opts);
			}
		} catch (MongoException e) {
			LoggerFactory.getLogger(InitWorkspaceServer.class).error(
					"Failed to connect to MongoDB: " + e.getMessage(), e);
			throw new WorkspaceInitException("Failed to connect to MongoDB: " + e.getMessage(), e);
		}
	}
	
	private static List<WorkspaceEventListener> loadListeners(final KBaseWorkspaceConfig cfg)
			throws WorkspaceInitException {
		final List<WorkspaceEventListener> wels = new LinkedList<>();
		for (final ListenerConfig lc: cfg.getListenerConfigs()) {
			final WorkspaceEventListenerFactory fac = loadFac(lc.getListenerClass());
			try {
				wels.add(fac.configure(lc.getConfig()));
			} catch (ListenerInitializationException e) {
				throw new WorkspaceInitException(String.format(
						"Error initializing listener %s: %s",
						lc.getListenerClass(), e.getMessage()), e);
			}
		}
		return wels;
	}

	private static WorkspaceEventListenerFactory loadFac(final String className)
			throws WorkspaceInitException {
		final Class<?> cls;
		try {
			cls = Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new WorkspaceInitException(String.format(
					"Cannot find class %s: %s", className, e.getMessage()), e);
		}
		final Set<Class<?>> interfaces = new HashSet<>(Arrays.asList(cls.getInterfaces()));
		if (!interfaces.contains(WorkspaceEventListenerFactory.class)) {
			throw new WorkspaceInitException(String.format(
					"Module %s must implement %s interface",
					className, WorkspaceEventListenerFactory.class.getName()));
		}
		@SuppressWarnings("unchecked")
		final Class<WorkspaceEventListenerFactory> inter =
				(Class<WorkspaceEventListenerFactory>) cls;
		try {
			return inter.getDeclaredConstructor().newInstance();
		} catch (IllegalAccessException | InstantiationException | IllegalArgumentException |
				InvocationTargetException | NoSuchMethodException | SecurityException e) {
			throw new WorkspaceInitException(String.format(
					"Module %s could not be instantiated: %s", className, e.getMessage()), e);
		}
	}

	private static AuthToken getKBaseToken(
			final String user,
			final String token,
			final String source,
			final ConfigurableAuthService auth)
			throws WorkspaceInitException {
		final AuthToken t = getToken(token, auth, source);
		if (!t.getUserName().equals(user)) {
			throw new WorkspaceInitException(String.format(
					"The username from the %s token, %s, does " +
					"not match the %s username, %s",
					source, t.getUserName(), source, user));
		}
		return t;
	}
	
	private static AuthToken getToken(
			final String token,
			final ConfigurableAuthService auth,
			final String source)
			throws WorkspaceInitException {
		if (token == null) {
			throw new WorkspaceInitException(
					"No token provided for " + source + " in configuration");
		}
		try {
			return auth.validateToken(token);
		} catch (AuthException e) {
			throw new WorkspaceInitException(
					"Couldn't log in with " + source + " credentials: " + e.getMessage(), e);
		} catch (IOException e) {
			throw new WorkspaceInitException(
					"Couldn't contact the auth service to validate the " + source + " token: "
					+ e.getMessage(), e);
		}
	}

	private static BlobStore setupBlobStore(
			final MongoDatabase db,
			final KBaseWorkspaceConfig cfg,
			final ConfigurableAuthService auth)
			throws WorkspaceInitException {
		
		if (cfg.getBackendType().equals(BackendType.GridFS)) {
			return new GridFSBlobStore(db);
		}
		if (cfg.getBackendType().equals(BackendType.S3)) {
			try {
				final S3ClientWithPresign cli = new S3ClientWithPresign(
						cfg.getBackendURL(),
						cfg.getBackendUser(),
						cfg.getBackendToken(),
						cfg.getBackendRegion(),
						cfg.getBackendTrustAllCerts());
				return new S3BlobStore(
						db.getCollection(COL_S3_OBJECTS),
						cli,
						cfg.getBackendContainer());
			} catch (URISyntaxException e) {
				throw new WorkspaceInitException("S3 url is not a valid URI: " +
						e.getMessage(), e);
			} catch (BlobStoreCommunicationException e) {
				throw new WorkspaceInitException("Error communicating with the blob store: " +
						e.getMessage(), e);
			} catch (IllegalArgumentException e) {
				throw new WorkspaceInitException("Illegal S3 bucket name: " + e.getMessage(), e);
			}
		}
		throw new WorkspaceInitException("Unknown backend type: " + cfg.getBackendType().name());
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
	
	private static AuthToken getHandleToken(
			final KBaseWorkspaceConfig cfg,
			final InitReporter rep,
			final ConfigurableAuthService auth) {
		try {
			return auth.validateToken(cfg.getHandleServiceToken());
		} catch (AuthException e) {
			rep.reportFail("Invalid handle service token: " + e.getMessage());
		} catch (IOException e) {
			rep.reportFail("Couldn't contact the auth service to obtain a token " +
					"for the handle service: " + e.getLocalizedMessage());
		}
		return null;
	}
	
	private static AbstractHandleClient getHandleServiceClient(
			final URL handleServiceUrl,
			final AuthToken handleMgrToken,
			final InitReporter rep) {
		final AbstractHandleClient cli;
		try {
			cli = new AbstractHandleClient(
					handleServiceUrl, handleMgrToken);
			if (handleServiceUrl.getProtocol().equals("http")) {
				rep.reportInfo("Warning - the Handle Service url uses insecure http. " +
						"https is recommended.");
				cli.setIsInsecureHttpConnectionAllowed(true);
			}
			cli.isOwner(Arrays.asList("FAKEHANDLE_-100"));
		} catch (Exception e) {
			rep.reportFail("Could not establish a connection to the Handle Service at "
					+ handleServiceUrl + ": " + e.getMessage());
			return null;
		}

		return cli;
	}
	
	private static ConfigurableAuthService setUpAuthClient(
			final KBaseWorkspaceConfig cfg,
			final InitReporter rep) {
		final AuthConfig c = new AuthConfig();
		if (cfg.getAuth2URL().getProtocol().equals("http")) {
			c.withAllowInsecureURLs(true);
			rep.reportInfo("Warning - the Auth Service MKII url uses insecure http. " +
					"https is recommended.");
		}
		if (cfg.getAuthURL().getProtocol().equals("http")) {
			c.withAllowInsecureURLs(true);
			rep.reportInfo(
					"Warning - the Auth Service url uses insecure http. https is recommended.");
		}
		try {
			final URL globusURL = cfg.getAuth2URL().toURI().resolve("api/legacy/globus").toURL();
			c.withGlobusAuthURL(globusURL).withKBaseAuthServerURL(cfg.getAuthURL());
		} catch (URISyntaxException | MalformedURLException e) {
			rep.reportFail("Invalid Auth Service url: " + cfg.getAuth2URL());
			return null;
		}
		try {
			return new ConfigurableAuthService(c);
		} catch (IOException e) {
			rep.reportFail("Couldn't connect to authorization service at " +
					c.getAuthServerURL() + " : " + e.getLocalizedMessage());
			return null;
		}
	}

	
	public static class WorkspaceInitException extends Exception {
		
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
