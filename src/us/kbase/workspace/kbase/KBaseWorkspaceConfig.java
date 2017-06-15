package us.kbase.workspace.kbase;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class KBaseWorkspaceConfig {
	
	//TODO TEST unit tests
	
	//required deploy parameters:
	private static final String HOST = "mongodb-host";
	private static final String DB = "mongodb-database";
	//startup workspace admin user
	private static final String WSADMIN = "ws-admin";
	//required backend param:
	private static final String BACKEND_TOKEN = "backend-token";
	//mongo db auth params:
	private static final String MONGO_USER = "mongodb-user";
	private static final String MONGO_PWD = "mongodb-pwd";
	//mongo connection attempt limit
	private static final String MONGO_RECONNECT = "mongodb-retry";
	
	//auth servers
	private static final String KBASE_AUTH_URL = "auth-service-url";
	private static final String GLOBUS_AUTH_URL = "globus-url";
	
	//handle service / manager info
	private static final String IGNORE_HANDLE_SERVICE = "ignore-handle-service";
	private static final String HANDLE_SERVICE_URL = "handle-service-url";
	private static final String HANDLE_MANAGER_URL = "handle-manager-url";
	private static final String HANDLE_MANAGER_TOKEN = "handle-manager-token";
	
	//directory for temp files
	private static final String TEMP_DIR = "temp-dir";
	
	private static final List<String> REQUIRED_PARAMS = Arrays.asList(
			HOST, DB, TEMP_DIR, GLOBUS_AUTH_URL, KBASE_AUTH_URL);
	
	private final String host;
	private final String db;
	private final String backendToken;
	private final String tempDir;
	private final String workspaceAdmin;
	private final String mongoUser;
	private final String mongoPassword;
	private final URL authURL;
	private final URL globusURL;
	private final int mongoReconnectAttempts;
	private final boolean ignoreHandleService;
	private final URL handleServiceURL;
	private final URL handleManagerURL;
	private final String handleManagerToken;
	private final List<String> errors;
	private final List<String> infoMessages;
	private final String paramReport;

	public KBaseWorkspaceConfig(final Map<String, String> config) {
		if (config == null) {
			throw new NullPointerException("config cannot be null");
		}
		final List<String> paramErrors = new ArrayList<String>();
		final List<String> infoMsgs = new ArrayList<String>();
		
		for (final String param: REQUIRED_PARAMS) {
			final String paramval = config.get(param);
			if (paramval == null || paramval.isEmpty()) {
				paramErrors.add("Must provide param " + param + " in config file");
			}
		}
		host = config.get(HOST);
		db = config.get(DB);
		tempDir = config.get(TEMP_DIR);
		
		authURL = getUrl(config, KBASE_AUTH_URL, paramErrors);
		globusURL = getUrl(config, GLOBUS_AUTH_URL, paramErrors);
		
		final String beToken = config.get(BACKEND_TOKEN);
		if (beToken == null || beToken.trim().isEmpty()) {
			backendToken = null;
		} else {
			backendToken = beToken;
		}
		
		workspaceAdmin = config.get(WSADMIN); //doesn't matter what's here
		
		final String muser = config.get(MONGO_USER);
		final String mpwd = config.get(MONGO_PWD);
		final boolean hasUser = muser != null && !muser.isEmpty();
		final boolean hasPwd = mpwd != null && !mpwd.isEmpty();
		
		if (hasUser ^ hasPwd) {
			paramErrors.add(String.format("Must provide both %s and %s ",
					MONGO_USER, MONGO_PWD) + "params in config file if " +
					" MongoDB authentication is to be used");
		}
		
		if (hasUser && hasPwd) {
			mongoUser = muser;
			mongoPassword = mpwd;
		} else {
			mongoUser = null;
			mongoPassword = null;
		}
		
		final String ignoreHandle = config.get(IGNORE_HANDLE_SERVICE);
		ignoreHandleService = ignoreHandle != null && !ignoreHandle.isEmpty();
		if (ignoreHandleService) {
			infoMsgs.add("Ignoring Handle Service config. Objects with " +
					"handle IDs will fail typechecking.");
			handleServiceURL = null;
			handleManagerURL = null;
			handleManagerToken = null;
		} else {
			final String token = config.get(HANDLE_MANAGER_TOKEN);
			final URL hsURL = getUrl(config, HANDLE_SERVICE_URL, paramErrors);
			final URL hmURL = getUrl(config, HANDLE_MANAGER_URL, paramErrors);
			if (token == null || token.trim().isEmpty()) {
				handleManagerToken = null;
				handleServiceURL = null;
				handleManagerURL = null;
				paramErrors.add(String.format(
						"Must provide %s in config file", HANDLE_MANAGER_TOKEN));
			} else {
				handleServiceURL = hsURL;
				handleManagerURL = hmURL;
				handleManagerToken = token;
			}
		}
		
		mongoReconnectAttempts = getReconnectCount(config, infoMsgs);
		errors = Collections.unmodifiableList(paramErrors);
		infoMessages = Collections.unmodifiableList(infoMsgs);
		paramReport = generateParamReport(config);
	}
	
	private String generateParamReport(final Map<String, String> cfg) {
		String params = "";
		final List<String> paramSet = new LinkedList<String>(
				Arrays.asList(HOST, DB, MONGO_USER, GLOBUS_AUTH_URL,
						KBASE_AUTH_URL));
		if (!ignoreHandleService) {
			paramSet.addAll(Arrays.asList(HANDLE_SERVICE_URL, HANDLE_MANAGER_URL));
		}
		for (final String s: paramSet) {
			if (cfg.containsKey(s)) {
				params += s + "=" + cfg.get(s) + "\n";
			}
		}
		if (mongoPassword != null && !mongoPassword.isEmpty()) {
			params += MONGO_PWD + "=[redacted for your safety and comfort]\n";
		}
		return params;
	}

	private static URL getUrl(
			final Map<String, String> wsConfig,
			final String configKey,
			final List<String> errors) {
		final String urlStr = wsConfig.get(configKey);
		if (urlStr == null || urlStr.isEmpty()) {
			errors.add("Must provide param " + configKey + " in config file");
			return null;
		}
		try {
			return new URL(urlStr);
		} catch (MalformedURLException e) {
			errors.add("Invalid url for parameter " + configKey + ": " + urlStr);
		}
		return null;
	}
	
	private static int getReconnectCount(
			final Map<String, String> wsConfig,
			final List<String> infos) {
		final String rec = wsConfig.get(MONGO_RECONNECT);
		Integer recint = null;
		try {
			recint = Integer.parseInt(rec); 
		} catch (NumberFormatException nfe) {
			//do nothing
		}
		if (recint == null) {
			infos.add("Couldn't parse MongoDB reconnect value to an integer: " +
					rec + ", using 0");
			recint = 0;
		} else if (recint < 0) {
			infos.add("MongoDB reconnect value is < 0 (" + recint +
					"), using 0");
			recint = 0;
		} else {
			infos.add("MongoDB reconnect value is " + recint);
		}
		return recint;
	}

	public String getHost() {
		return host;
	}

	public String getDBname() {
		return db;
	}

	public URL getAuthURL() {
		return authURL;
	}
	
	public URL getGlobusURL() {
		return globusURL;
	}
	
	public String getBackendToken() {
		return backendToken;
	}

	public String getTempDir() {
		return tempDir;
	}

	public String getWorkspaceAdmin() {
		return workspaceAdmin;
	}

	public String getMongoUser() {
		return mongoUser;
	}

	public String getMongoPassword() {
		return mongoPassword;
	}

	public int getMongoReconnectAttempts() {
		return mongoReconnectAttempts;
	}

	public boolean ignoreHandleService() {
		return ignoreHandleService;
	}

	public URL getHandleServiceURL() {
		return handleServiceURL;
	}

	public URL getHandleManagerURL() {
		return handleManagerURL;
	}

	public String getHandleManagerToken() {
		return handleManagerToken;
	}

	public List<String> getErrors() {
		return errors;
	}

	public List<String> getInfoMessages() {
		return infoMessages;
	}
	
	public String getParamReport() {
		return paramReport;
	}
	
	public boolean hasErrors() {
		return !errors.isEmpty();
	}
}
