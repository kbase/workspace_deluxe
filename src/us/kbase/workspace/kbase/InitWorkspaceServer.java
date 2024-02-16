package us.kbase.workspace.kbase;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

import us.kbase.abstracthandle.AbstractHandleClient;
import us.kbase.auth.AuthException;
import us.kbase.auth.AuthToken;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.sampleservice.SampleServiceClient;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.typedobj.core.LocalTypeProvider;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeProvider;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactoryBuilder;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.Workspace;
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
import us.kbase.workspace.kbase.admin.AdministrationCommandSetInstaller;
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
	// TODO CODE try and clean this mess up. Nulls everywhere, long methods.
	//			some places report errors to the InitReporter, some throw exceptions.
	// TODO CODE drop the idea of reporting all errors and going into fail mode.
	//			Just throw an exception immediately, much simpler.
	//			Test what happens in tomcat / Jetty first though.
	// TODO CODE Drop all references to Glassfish and streamline Tomcat setup.
	
	public static final String COL_S3_OBJECTS = "s3_objects";
	
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
		private final WorkspaceServerMethods wsmeth;
		private final WorkspaceAdministration wsadmin;
		private final TypeServerMethods types;
		
		public WorkspaceInitResults(
				final WorkspaceServerMethods wsmeth,
				final WorkspaceAdministration wsadmin,
				final TypeServerMethods types) {
			this.wsmeth = wsmeth;
			this.wsadmin = wsadmin;
			this.types = types;
		}

		public WorkspaceServerMethods getWsmeth() {
			return wsmeth;
		}

		public WorkspaceAdministration getWsAdmin() {
			return wsadmin;
		}
		
		public TypeServerMethods getTypes() {
			return types;
		}
	}
	
	public static void setMaximumUniqueIdCountForTests(final int count) {
		maxUniqueIdCountPerCall = count;
	}
	
	public static WorkspaceInitResults initWorkspaceServer(
			final KBaseWorkspaceConfig cfg,
			final ConfigurableAuthService auth,
			final InitReporter rep) {
		final TempFilesManager tfm = initTempFilesManager(cfg.getTempDir(), rep);
		
		// TODO CODE move this into buildWorkspace. Change so rep isn't used for fails
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

		final WorkspaceInitResults init;
		try {
			init = buildWorkspace(cfg, auth, hsc, tfm, rep);
		} catch (WorkspaceInitException e) {
			// tested manually, if you make changes test again
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
				e.printStackTrace(new PrintStream(baos, true, UTF_8.name()));
				rep.reportFail(e.getLocalizedMessage());
				rep.reportFail(baos.toString(UTF_8.name()));
				rep.reportFail("Server startup failed - all calls will error out.");
			} catch (UnsupportedEncodingException uee) {
				throw new RuntimeException("Welp that's weird", uee);
			}
			return null;
		}
		rep.reportInfo(String.format("Initialized %s backend", cfg.getBackendType().name()));
		final String mem = String.format(
				"Started workspace server instance %s. Free mem: %s Total mem: %s, Max mem: %s",
				++instanceCount, Runtime.getRuntime().freeMemory(),
				Runtime.getRuntime().totalMemory(),
				Runtime.getRuntime().maxMemory());
		rep.reportInfo(mem);
		return init;
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

	private static WorkspaceInitResults buildWorkspace(
			final KBaseWorkspaceConfig cfg,
			final ConfigurableAuthService auth,
			final AbstractHandleClient hsc,
			final TempFilesManager tfm,
			final InitReporter rep) // DO NOT use the rep to report failures. Throw instead.
			throws WorkspaceInitException {
		// this method is a bit long but it's more or less trivial what's going on here
		
		final MongoDatabase db = buildMongo(cfg, cfg.getDBname()).getDatabase(cfg.getDBname());
		
		final BlobStore bs = setupBlobStore(db, cfg);
		
		final Optional<TypeDelegation> typeDelegator = getTypeDelegator(cfg, rep);
		final TypeProvider typeProvider;
		final TypeServerMethods types;
		if (typeDelegator.isPresent()) {
			types = typeDelegator.get().delegator;
			typeProvider = typeDelegator.get().typeProvider;
		} else {
			// see https://jira.mongodb.org/browse/JAVA-2656
			final MongoDatabase mongoTypes = buildMongo(cfg, cfg.getTypeDBName())
					.getDatabase(cfg.getTypeDBName());
			final TypeDefinitionDB typeDB;
			try {
				typeDB = new TypeDefinitionDB(new MongoTypeStorage(mongoTypes));
				// TODO CODE Mongo exceptions should be wrapped and not exposed
			} catch (TypeStorageException | MongoException e) {
				throw new WorkspaceInitException("Couldn't set up the type database: "
						+ e.getLocalizedMessage(), e);
			}
			// merge these 2 classes?
			types = new LocalTypeServerMethods(new Types(typeDB));
			typeProvider = new LocalTypeProvider(typeDB);
		}
		final MongoWorkspaceDB mongoWS;
		try {
			mongoWS = new MongoWorkspaceDB(db, bs);
		} catch (WorkspaceDBException wde) {
			throw new WorkspaceInitException(
					"Error initializing the workspace database: " +
					wde.getLocalizedMessage(), wde);
		}
		final Workspace ws;
		try {
			ws = new Workspace(
					mongoWS,
					new ResourceUsageConfigurationBuilder().build(),
					new TypedObjectValidator(typeProvider),
					tfm,
					loadListeners(cfg));
		} catch (WorkspaceCommunicationException e) { // this is really hard to test
			throw new WorkspaceInitException(e.getMessage(), e);
		}
		final IdReferenceHandlerSetFactoryBuilder builder = IdReferenceHandlerSetFactoryBuilder
				.getBuilder(maxUniqueIdCountPerCall)
				.withFactory(new HandleIdHandlerFactory(hsc))
				.withFactory(getShockIdHandlerFactory(cfg, auth))
				.withFactory(getSampleIdHandlerFactory(cfg, auth, rep))
				.build();
		final WorkspaceServerMethods wsmeth = new WorkspaceServerMethods(ws, builder, auth);
		final WorkspaceAdministration.Builder adminbuilder = WorkspaceAdministration.getBuilder(
				getAdminHandler(cfg, ws), (user, token) -> wsmeth.validateUser(user, token));
		if (typeDelegator.isPresent()) {
			AdministrationCommandSetInstaller.install(
					adminbuilder, wsmeth, typeDelegator.get().delegator);
		} else {
			AdministrationCommandSetInstaller.install(
					adminbuilder, wsmeth, (LocalTypeServerMethods) types);
		}
		return new WorkspaceInitResults(wsmeth, adminbuilder.build(), types);
	}
	
	private static class TypeDelegation {
		private final TypeProvider typeProvider;
		private final TypeClient delegator;

		public TypeDelegation(
				final TypeProvider typeProvider,
				final TypeClient delegator) {
			this.typeProvider = typeProvider;
			this.delegator = delegator;
		}
	}

	private static Optional<TypeDelegation> getTypeDelegator(
			final KBaseWorkspaceConfig cfg,
			final InitReporter rep) // DO NOT use the rep to report failures. Throw instead.
			throws WorkspaceInitException {
		if (cfg.getTypeDelegationTarget() == null) {
			return Optional.empty();
		}
		final WorkspaceClient cli = new WorkspaceClient(cfg.getTypeDelegationTarget());
		try {
			cli.ver();
		} catch (IOException | JsonClientException e) {
			// this code was tested manually. If you make changes retest
			throw new WorkspaceInitException(
					"Failed contacting type delegation workspace: " + e.getMessage(), e);
		}
		if (cfg.getTypeDelegationTarget().getProtocol().equals("http")) {
			rep.reportInfo("Warning - the Type Delegation url uses insecure http. " +
					"https is recommended.");
			cli.setIsInsecureHttpConnectionAllowed(true);
		}
		return Optional.of(new TypeDelegation(
				DelegatingTypeProvider.getBuilder(cli).build(),
				new TypeClient(
						cfg.getTypeDelegationTarget(),
						new TypeClient.WorkspaceClientProvider() {
			
							@Override
							public WorkspaceClient getClient(
									final URL workspaceURL,
									final AuthToken token)
									throws UnauthorizedException, IOException {
								return new WorkspaceClient(workspaceURL, token);
							}
							
							@Override
							public WorkspaceClient getClient(final URL workspaceURL)
									throws UnauthorizedException, IOException {
								return cli;
							}
						}
				)
		));
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
			// TODO CODE move all this warning code to the config class
			// easier to unit test, less code in this disaster of a class
			rep.reportInfo("Warning - the Sample Service url uses insecure http. " +
					"https is recommended.");
			cli.setIsInsecureHttpConnectionAllowed(true);
		}
		return new SampleIdHandlerFactory(cli);
	}

	public static MongoClient buildMongo(final KBaseWorkspaceConfig c, final String dbName)
			throws WorkspaceInitException {
		//TODO ZLATER MONGO handle shards & replica sets
		final MongoClientSettings.Builder mongoBuilder = MongoClientSettings.builder()
				.retryWrites(c.getMongoRetryWrites())
				.applyToClusterSettings(builder -> builder.hosts(
						Arrays.asList(new ServerAddress(c.getMongoHost()))));
		try {
			if (c.getMongoUser() != null) {
				final MongoCredential creds = MongoCredential.createCredential(
						c.getMongoUser(), dbName, c.getMongoPassword().toCharArray());
				// unclear if and when it's safe to clear the password
				return MongoClients.create(mongoBuilder.credential(creds).build());
			} else {
				return MongoClients.create(mongoBuilder.build());
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
			final KBaseWorkspaceConfig cfg)
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
