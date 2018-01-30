package us.kbase.workspace.kbase;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class KBaseWorkspaceConfig {
	
	//TODO TEST unit tests
	//TODO JAVADOCS
	
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
	
	// listeners
	private static final String LISTENERS = "listeners";
	private static final String LISTENER_PREFIX = "listener-";
	private static final String LISTENER_CLASS = "-class";
	private static final String LISTENER_CONFIG = "-config-";
	
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
	private final List<ListenerConfig> listenerConfigs;
	
	public static class ListenerConfig {
		
		private final String listenerClass;
		private final Map<String, String> config;
		
		private ListenerConfig(
				final String listenerClass,
				final Map<String, String> config) {
			this.listenerClass = listenerClass;
			this.config = Collections.unmodifiableMap(config);
		}

		public String getListenerClass() {
			return listenerClass;
		}

		public Map<String, String> getConfig() {
			return config;
		}
	}

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
		listenerConfigs = getListenerConfigs(config, paramErrors);
		errors = Collections.unmodifiableList(paramErrors);
		infoMessages = Collections.unmodifiableList(infoMsgs);
		paramReport = generateParamReport(config);
	}
	
	private List<ListenerConfig> getListenerConfigs(
			final Map<String, String> config,
			final List<String> paramErrors) {
		final String listenersStr = config.get(LISTENERS);
		if (listenersStr == null || listenersStr.trim().isEmpty()) {
			return Collections.emptyList();
		}
		final List<ListenerConfig> ret = new LinkedList<>();
		final List<String> listeners = Arrays.asList(listenersStr.split(","));
		for (final String name: listeners) {
			final String listenerStart = LISTENER_PREFIX + name;
			final String classStr = config.get(listenerStart + LISTENER_CLASS);
			if (nullOrEmpty(classStr)) {
				paramErrors.add("Missing listener class: " + listenerStart + LISTENER_CLASS);
			}
			final Map<String, String> cfg = getListenerConfig(
					config, listenerStart + LISTENER_CONFIG, paramErrors);
			ret.add(new ListenerConfig(classStr, cfg));
		}
		return Collections.unmodifiableList(ret);
	}
	
	private Map<String, String> getListenerConfig(
			final Map<String, String> config,
			final String prefix,
			final List<String> paramErrors) {
		final Map<String, String> ret = new HashMap<>();
		for (final String key: config.keySet()) {
			if (key.startsWith(prefix)) {
				final String ckey = key.replaceFirst(prefix, "");
				if (ckey.trim().isEmpty()) {
					paramErrors.add("Invalid listener configuration item: " + key);
				}
				ret.put(ckey, config.get(key));
			}
		}
		return ret;
	}

	private boolean nullOrEmpty(final String s) {
		if (s == null || s.trim().isEmpty()) {
			return true;
		}
		return false;
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
		if (!listenerConfigs.isEmpty()) {
			final List<String> listeners = listenerConfigs.stream().map(l -> l.getListenerClass())
					.collect(Collectors.toList());
			params += LISTENERS + "=" + String.join(",", listeners);
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

	public List<ListenerConfig> getListenerConfigs() {
		return listenerConfigs;
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
