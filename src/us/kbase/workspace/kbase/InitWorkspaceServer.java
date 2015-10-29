package us.kbase.workspace.kbase;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import us.kbase.abstracthandle.AbstractHandleClient;
import us.kbase.auth.AuthConfig;
import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.auth.RefreshingToken;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.mongo.exceptions.MongoAuthException;
import us.kbase.common.service.ServerException;
import us.kbase.handlemngr.HandleMngrClient;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.exceptions.WorkspaceDBException;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;


public class InitWorkspaceServer {
	
	//required deploy parameters:
	private static final String HOST = "mongodb-host";
	private static final String DB = "mongodb-database";
	//startup workspace admin user
	private static final String WSADMIN = "ws-admin";
	//required backend param:
	private static final String BACKEND_SECRET = "backend-secret"; 
	//mongo db auth params:
	private static final String USER = "mongodb-user";
	private static final String PWD = "mongodb-pwd";
	//mongo connection attempt limit
	private static final String MONGO_RECONNECT = "mongodb-retry";

	//credentials to use for user queries
	private static final String KBASE_ADMIN_USER = "kbase-admin-user";
	private static final String KBASE_ADMIN_PWD = "kbase-admin-pwd";

	//handle service / manager info
	private static final String IGNORE_HANDLE_SERVICE =
			"ignore_handle_service";
	private static final String HANDLE_SERVICE_URL = "handle-service-url";
	private static final String HANDLE_MANAGER_URL = "handle-manager-url";
	private static final String HANDLE_MANAGER_USER = "handle-manager-user";
	private static final String HANDLE_MANAGER_PWD = "handle-manager-pwd";
	private static final int TOKEN_REFRESH_INTERVAL = 24 * 60 * 60;
	private static int maxUniqueIdCountPerCall = 100000;

	//directory for temp files
	private static final String TEMP_DIR = "temp-dir";

	
	private final static int TOKEN_REFRESH_INTERVAL_SEC = 24 * 60 * 60;

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
			final Map<String,String> wsConfig,
			final InitReporter rep) {
		TempFilesManager tfm = initTempFilesManager(wsConfig, rep);
		boolean failed = tfm == null;
		final String host = wsConfig.get(HOST);
		if (host == null || host.isEmpty()) {
			rep.reportFail("Must provide param " + HOST + " in config file");
			failed = true;
		}
		final String dbs = wsConfig.get(DB);
		if (dbs == null || dbs.isEmpty()) {
			rep.reportFail("Must provide param " + DB + " in config file");
			failed = true;
		}
		final String secret = wsConfig.get(BACKEND_SECRET);
		if (secret == null || secret.isEmpty()) {
			rep.reportFail("Must provide param " + BACKEND_SECRET +
					" in config file");
			failed = true;
		}
		final String user = wsConfig.get(USER);
		final String pwd = wsConfig.get(PWD);
		final boolean hasUser = user != null && !user.isEmpty();
		final boolean hasPwd = pwd != null && !pwd.isEmpty();
		
		if (hasUser ^ hasPwd) {
			rep.reportFail(String.format("Must provide both %s and %s ",
					USER, PWD) + "params in config file if authentication " + 
					"is to be used");
			failed = true;
		}
		final String ignoreHandle = wsConfig.get(IGNORE_HANDLE_SERVICE);
		final boolean ignoreHandleService = 
				ignoreHandle != null && !ignoreHandle.isEmpty();
		URL handleServiceUrl = null;
		URL handleManagerUrl = null;
		RefreshingToken handleMgrToken = null;
		if (ignoreHandleService) {
			rep.reportInfo("Ignoring Handle Service config. Objects with handle IDs will fail typechecking.");
		} else {
			handleServiceUrl = getHandleUrl(wsConfig, HANDLE_SERVICE_URL, rep);
			failed = failed || handleServiceUrl == null;
			handleManagerUrl = getHandleUrl(wsConfig, HANDLE_MANAGER_URL, rep);
			failed = failed || handleManagerUrl == null;
			handleMgrToken = getHandleToken(wsConfig, rep);
			failed = failed || handleMgrToken == null;
			if (!failed) {
				failed = checkHandleServiceConnection(handleServiceUrl,
						handleMgrToken, rep);
			}
			if (!failed) {
				failed = checkHandleManagerConnection(handleManagerUrl,
						handleMgrToken, rep);
			}
		}
		
		if (!wsConfig.containsKey(KBASE_ADMIN_USER)) {
			rep.reportFail("Must provide param " + KBASE_ADMIN_USER +
					" in config file");
			failed = true;
		}
		final String adminUser = wsConfig.get(KBASE_ADMIN_USER);
		if (!wsConfig.containsKey(KBASE_ADMIN_PWD)) {
			rep.reportFail("Must provide param " + KBASE_ADMIN_PWD +
					" in config file");
			failed = true;
		}
		final String adminPwd = wsConfig.get(KBASE_ADMIN_PWD);
		
		if (failed) {
			rep.reportFail("Server startup failed - all calls will error out.");
			return null;
		} 
		String params = "";
		final List<String> paramSet = new LinkedList<String>(
				Arrays.asList(HOST, DB, USER));
		if (!ignoreHandleService) {
			paramSet.addAll(Arrays.asList(HANDLE_SERVICE_URL,
					HANDLE_MANAGER_URL, HANDLE_MANAGER_USER));
		}
		for (final String s: paramSet) {
			if (wsConfig.containsKey(s)) {
				params += s + "=" + wsConfig.get(s) + "\n";
			}
		}
		params += BACKEND_SECRET + "=[redacted for your safety and comfort]\n";
		if (pwd != null && !pwd.isEmpty()) {
			params += PWD + "=[redacted for your safety and comfort]\n";
		}
		rep.reportInfo("Starting server using connection parameters:\n" +
				params);
		rep.reportInfo("Temporary file location: " + tfm.getTempDir());
		final int mongoConnectRetry = getReconnectCount(wsConfig, rep);
		final WorkspaceDatabase db = getDB(host, dbs, secret, user, pwd,
				tfm, mongoConnectRetry, rep);
		if (db == null) {
			rep.reportFail(
					"Server startup failed - all calls will error out.");
			return null;
		}
		System.out.println(String.format("Initialized %s backend",
				db.getBackendType()));
		rep.reportInfo(String.format("Initialized %s backend",
				db.getBackendType()));
		Workspace ws = new Workspace(db,
				new ResourceUsageConfigurationBuilder().build(),
				new KBaseReferenceParser());
		WorkspaceServerMethods wsmeth = new WorkspaceServerMethods(
				ws, handleServiceUrl,
				maxUniqueIdCountPerCall,
				setUpAuthClient(adminUser, adminPwd, rep));
		WorkspaceAdministration wsadmin = new WorkspaceAdministration(
				ws, wsmeth, wsConfig.get(WSADMIN));
		final String mem = String.format(
				"Started workspace server instance %s. Free mem: %s Total mem: %s, Max mem: %s",
				++instanceCount, Runtime.getRuntime().freeMemory(),
				Runtime.getRuntime().totalMemory(),
				Runtime.getRuntime().maxMemory());
		rep.reportInfo(mem);
		return new WorkspaceInitResults(
				ws, wsmeth, wsadmin, tfm, handleManagerUrl, handleMgrToken);
	}
	
	private static WorkspaceDatabase getDB(final String host, final String dbs,
			final String secret, final String user, final String pwd,
			final TempFilesManager tfm, final int mongoReconnectRetry,
			final InitReporter rep) {
		try {
			if (user != null) {
				return new MongoWorkspaceDB(host, dbs, secret, user, pwd, tfm,
						mongoReconnectRetry);
			} else {
				return new MongoWorkspaceDB(host, dbs, secret, tfm,
						mongoReconnectRetry);
			}
		} catch (UnknownHostException uhe) {
			rep.reportFail("Couldn't find mongo host " + host + ": " +
					uhe.getLocalizedMessage());
		} catch (IOException io) {
			rep.reportFail("Couldn't connect to mongo host " + host + ": " +
					io.getLocalizedMessage());
		} catch (MongoAuthException ae) {
			rep.reportFail("Not authorized: " + ae.getLocalizedMessage());
		} catch (InvalidHostException ihe) {
			rep.reportFail(host + " is an invalid database host: "  +
					ihe.getLocalizedMessage());
		} catch (WorkspaceDBException uwde) {
			rep.reportFail("The workspace database is invalid: " +
					uwde.getLocalizedMessage());
		} catch (TypeStorageException tse) {
			rep.reportFail("There was a problem setting up the type storage system: " +
					tse.getLocalizedMessage());
		} catch (InterruptedException ie) {
			rep.reportFail("Connection to MongoDB was interrupted. This should never " +
					"happen and indicates a programming problem. Error: " +
					ie.getLocalizedMessage());
		}
		return null;
	}

	private static TempFilesManager initTempFilesManager(
			final Map<String, String> wsConfig,
			final InitReporter rep) {
		if (!wsConfig.containsKey(TEMP_DIR)) {
			rep.reportFail("Must provide param " + TEMP_DIR +
					" in config file");
			return null;
		}
		try {
			final TempFilesManager tfm = new TempFilesManager(
					new File(wsConfig.get(TEMP_DIR)));
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
	
	private static int getReconnectCount(
			final Map<String, String> wsConfig,
			final InitReporter rep) {
		final String rec = wsConfig.get(MONGO_RECONNECT);
		Integer recint = null;
		try {
			recint = Integer.parseInt(rec); 
		} catch (NumberFormatException nfe) {
			//do nothing
		}
		if (recint == null) {
			rep.reportInfo("Couldn't parse MongoDB reconnect value to an integer: " +
					rec + ", using 0");
			recint = 0;
		} else if (recint < 0) {
			rep.reportInfo("MongoDB reconnect value is < 0 (" + recint +
					"), using 0");
			recint = 0;
		} else {
			rep.reportInfo("MongoDB reconnect value is " + recint);
		}
		return recint;
	}
	

	private static URL getHandleUrl(
			final Map<String, String> wsConfig,
			final String configKey,
			final InitReporter rep) {
		final String urlStr = wsConfig.get(configKey);
		if (urlStr == null || urlStr.isEmpty()) {
			rep.reportFail("Must provide param " + configKey +
					" in config file");
			return null;
		}
		try {
			return new URL(urlStr);
		} catch (MalformedURLException e) {
			rep.reportFail("Invalid url for parameter " + configKey + ": " +
					urlStr);
		}
		return null;
	}
	
	private static RefreshingToken getHandleToken(
			final Map<String, String> wsConfig,
			final InitReporter rep) {
		final String user = wsConfig.get(HANDLE_MANAGER_USER);
		final String pwd =  wsConfig.get(HANDLE_MANAGER_PWD);
		if (user == null || user.isEmpty() || pwd == null || pwd.isEmpty()) {
			rep.reportFail("Must provide params " + HANDLE_MANAGER_USER +
					" and " + HANDLE_MANAGER_PWD + " in config file");
			return null;
		}
		try {
			return AuthService.getRefreshingToken(
					user, pwd, TOKEN_REFRESH_INTERVAL);
		} catch (AuthException e) {
			rep.reportFail("Couldn't log in with handle manager credentials for user " +
					user + ": " + e.getLocalizedMessage());
		} catch (IOException e) {
			rep.reportFail("Couldn't contact the auth service to obtain a token for the handle manager: "
					+ e.getLocalizedMessage());
		}
		return null;
	}
	

	private static boolean checkHandleServiceConnection(
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
	
	private static boolean checkHandleManagerConnection(
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
