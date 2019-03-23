package us.kbase.workspace.kbase;

import static java.util.Objects.requireNonNull;
import static us.kbase.workspace.database.Util.checkString;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;

public class KBaseWorkspaceConfig {
	
	//TODO CODE How the config is created could use a rethink. Would be much simpler just throwing an exception rather than collecting errors.
	/* I think it was originally this way because Glassfish would do (?? I forget) when
	 * the WS threw an exception, which we wanted to avoid, and so we made the service start
	 * but throw errors for all calls (e.g. "the service did not start correctly, check the logs".
	 * Now the standard container is Tomcat so try with that and see what makes sense.
	 * On the other hand, it's nice to see all the errors at once *shrug*
	 */
	
	//TODO JAVADOCS
	
	//required deploy parameters
	private static final String HOST = "mongodb-host";
	private static final String DB = "mongodb-database";
	private static final String TYPE_DB = "mongodb-type-database";
	//startup workspace admin user
	private static final String WSADMIN = "ws-admin";
	// backend params
	private static final String BACKEND_TYPE = "backend-type";
	private static final String BACKEND_USER = "backend-user";
	private static final String BACKEND_TOKEN = "backend-token";
	private static final String BACKEND_URL = "backend-url";
	//mongo db auth params:
	private static final String MONGO_USER = "mongodb-user";
	private static final String MONGO_PWD = "mongodb-pwd";
	
	//auth servers
	private static final String KBASE_AUTH_URL = "auth-service-url";
	private static final String KBASE_AUTH2_URL = "auth2-service-url";
	
	//admin roles
	private static final String KBASE_AUTH_ADMIN_READ_ONLY_ROLES =
			"auth2-ws-admin-read-only-roles";
	private static final String KBASE_AUTH_ADMIN_FULL_ROLES =
			"auth2-ws-admin-full-roles";
	
	// shock info 
	private static final String SHOCK_USER = "shock-user";
	private static final String SHOCK_TOKEN = "shock-token";
	private static final String SHOCK_URL = "shock-url";
	
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
	
	// the auth2 urls are checked when getting the url
	private static final List<String> REQUIRED_PARAMS = Arrays.asList(
			HOST, DB, TYPE_DB, TEMP_DIR, BACKEND_TYPE);
	
	private static final Map<String, List<String>> BACKEND_TYPES = ImmutableMap.of(
			BackendType.Shock.name(), Arrays.asList(BACKEND_TOKEN, BACKEND_URL, BACKEND_USER),
			BackendType.GridFS.name(), Collections.emptyList());
	
	private final String host;
	private final String db;
	private final String typedb;
	private final BackendType backendType;
	private final URL backendURL;
	private final String backendUser;
	private final String backendToken;
	private final String tempDir;
	private final URL shockURL;
	private final String shockUser;
	private final String shockToken;
	private final String workspaceAdmin;
	private final String mongoUser;
	private final String mongoPassword;
	private final URL authURL;
	private final URL auth2URL;
	private final Set<String> adminRoles;
	private final Set<String> adminReadOnlyRoles;
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
		
		public ListenerConfig(
				final String listenerClass,
				final Map<String, String> config) {
			requireNonNull(config, "config");
			this.listenerClass = checkString(listenerClass, "listenerClass");
			this.config = Collections.unmodifiableMap(new HashMap<>(config));
		}

		public String getListenerClass() {
			return listenerClass;
		}

		public Map<String, String> getConfig() {
			return config;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((config == null) ? 0 : config.hashCode());
			result = prime * result + ((listenerClass == null) ? 0 : listenerClass.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			ListenerConfig other = (ListenerConfig) obj;
			if (config == null) {
				if (other.config != null) {
					return false;
				}
			} else if (!config.equals(other.config)) {
				return false;
			}
			if (listenerClass == null) {
				if (other.listenerClass != null) {
					return false;
				}
			} else if (!listenerClass.equals(other.listenerClass)) {
				return false;
			}
			return true;
		}
	}

	public KBaseWorkspaceConfig(final Map<String, String> config) {
		requireNonNull(config, "config");
		final List<String> paramErrors = new ArrayList<String>();
		final List<String> infoMsgs = new ArrayList<String>();
		
		// should just make a method for getting a String that ensures the string is there
		for (final String param: REQUIRED_PARAMS) {
			final String paramval = config.get(param);
			if (nullOrEmpty(paramval)) {
				paramErrors.add("Must provide param " + param + " in config file");
			}
		}
		host = nullIfEmpty(config.get(HOST));
		db = nullIfEmpty(config.get(DB));
		typedb = nullIfEmpty(config.get(TYPE_DB));
		if (db != null && db.equals(typedb)) {
			paramErrors.add(String.format("The parameters %s and %s have the same value, %s",
					DB, TYPE_DB, db));
		}
		tempDir = nullIfEmpty(config.get(TEMP_DIR));
		
		authURL = getUrl(config, KBASE_AUTH_URL, paramErrors, true);
		auth2URL = getUrl(config, KBASE_AUTH2_URL, paramErrors, true);
		
		adminRoles = getStringSet(config, KBASE_AUTH_ADMIN_FULL_ROLES);
		adminReadOnlyRoles = getStringSet(config, KBASE_AUTH_ADMIN_READ_ONLY_ROLES);
		
		final String bet = nullIfEmpty(config.get(BACKEND_TYPE));
		if (!BACKEND_TYPES.containsKey(bet)) {
			if (bet != null) {
				paramErrors.add("Illegal backend type: " + bet);
			}
			backendType = null;
			backendToken = null;
			backendURL = null;
			backendUser = null;
		} else {
			backendType = BackendType.valueOf(bet);
			for (final String param: BACKEND_TYPES.get(backendType.name())) {
				if (nullOrEmpty(config.get(param))) {
					paramErrors.add(String.format(
							"Must provide %s param %s in config file", backendType, param));
				}
			}
			backendToken = nullIfEmpty(config.get(BACKEND_TOKEN));
			backendURL = getUrl(config, BACKEND_URL, paramErrors, false);
			backendUser = nullIfEmpty(config.get(BACKEND_USER));
		}

		shockURL = getUrl(config, SHOCK_URL, paramErrors, false);
		if (shockURL == null) {
			shockUser = null;
			shockToken = null;
		} else {
			shockUser = nullIfEmpty(config.get(SHOCK_USER));
			shockToken = nullIfEmpty(config.get(SHOCK_TOKEN));
			if (shockUser == null || shockToken == null) {
				paramErrors.add(String.format(
						"Must provide %s and %s parameters in config file if %s is provided",
						SHOCK_USER, SHOCK_TOKEN, SHOCK_URL));
			}
		}
		workspaceAdmin = nullIfEmpty(config.get(WSADMIN));
		
		final String muser = nullIfEmpty(config.get(MONGO_USER));
		final String mpwd = nullIfEmpty(config.get(MONGO_PWD));
		final boolean hasUser = muser != null;
		final boolean hasPwd = mpwd != null;
		
		if (hasUser ^ hasPwd) {
			paramErrors.add(String.format("Must provide both %s and %s params in config file if " +
					"MongoDB authentication is to be used", MONGO_USER, MONGO_PWD));
			mongoUser = null;
			mongoPassword = null;
		} else {
			mongoUser = muser;
			mongoPassword = mpwd;
		}
		
		ignoreHandleService = !nullOrEmpty(config.get(IGNORE_HANDLE_SERVICE));
		if (ignoreHandleService) {
			infoMsgs.add("Ignoring Handle Service config. Objects with " +
					"handle IDs will fail typechecking.");
			handleServiceURL = null;
			handleManagerURL = null;
			handleManagerToken = null;
		} else {
			final String token = nullIfEmpty(config.get(HANDLE_MANAGER_TOKEN));
			final URL hsURL = getUrl(config, HANDLE_SERVICE_URL, paramErrors, true);
			final URL hmURL = getUrl(config, HANDLE_MANAGER_URL, paramErrors, true);
			if (token == null) {
				handleManagerToken = null;
				handleServiceURL = null;
				handleManagerURL = null;
				paramErrors.add(String.format(
						"Must provide param %s in config file", HANDLE_MANAGER_TOKEN));
			} else {
				handleServiceURL = hsURL;
				handleManagerURL = hmURL;
				handleManagerToken = token;
			}
		}
		
		listenerConfigs = getListenerConfigs(config, paramErrors);
		errors = Collections.unmodifiableList(paramErrors);
		infoMessages = Collections.unmodifiableList(infoMsgs);
		paramReport = generateParamReport(config);
	}
	
	private Set<String> getStringSet(final Map<String, String> config, final String configKey) {
		final String set = config.get(configKey);
		if (nullOrEmpty(set)) {
			return Collections.emptySet();
		}
		final Set<String> ret = new HashSet<>();
		for (final String s: set.split(",")) {
			if (!s.trim().isEmpty()) {
				ret.add(s.trim());
			}
		}
		return Collections.unmodifiableSet(ret);
	}

	private List<ListenerConfig> getListenerConfigs(
			final Map<String, String> config,
			final List<String> paramErrors) {
		final String listenersStr = config.get(LISTENERS);
		if (nullOrEmpty(listenersStr)) {
			return Collections.emptyList();
		}
		final List<ListenerConfig> ret = new LinkedList<>();
		final List<String> listeners = Arrays.asList(listenersStr.split(","));
		for (String name: listeners) {
			name = name.trim();
			if (name.isEmpty()) {
				continue;
			}
			final String listenerStart = LISTENER_PREFIX + name;
			final String classStr = config.get(listenerStart + LISTENER_CLASS);
			if (nullOrEmpty(classStr)) {
				paramErrors.add("Missing listener class: " + listenerStart + LISTENER_CLASS);
			} else {
				final Map<String, String> cfg = getListenerConfig(
						config, listenerStart + LISTENER_CONFIG, paramErrors);
				if (cfg != null) {
					ret.add(new ListenerConfig(classStr, cfg));
				}
			}
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
					return null;
				}
				ret.put(ckey, config.get(key));
			}
		}
		return ret;
	}

	private static String nullIfEmpty(final String s) {
		if (nullOrEmpty(s)) {
			return null;
		}
		return s.trim();
	}
	
	private static boolean nullOrEmpty(final String s) {
		if (s == null || s.trim().isEmpty()) {
			return true;
		}
		return false;
	}

	private String generateParamReport(final Map<String, String> cfg) {
		String params = "";
		final List<String> paramSet = new LinkedList<String>(
				Arrays.asList(HOST, DB, TYPE_DB, MONGO_USER, KBASE_AUTH_URL, KBASE_AUTH2_URL,
						KBASE_AUTH_ADMIN_READ_ONLY_ROLES, KBASE_AUTH_ADMIN_FULL_ROLES,
						BACKEND_TYPE, BACKEND_URL, BACKEND_USER));
		if (!ignoreHandleService) {
			paramSet.addAll(Arrays.asList(HANDLE_SERVICE_URL, HANDLE_MANAGER_URL));
		}
		if (shockURL != null) {
			paramSet.addAll(Arrays.asList(SHOCK_URL, SHOCK_USER));
		}
		for (final String s: paramSet) {
			if (!nullOrEmpty(cfg.get(s))) {
				params += s + "=" + cfg.get(s).trim() + "\n";
			}
		}
		if (mongoPassword != null) {
			params += MONGO_PWD + "=[redacted for your safety and comfort]\n";
		}
		if (!listenerConfigs.isEmpty()) {
			final List<String> listeners = listenerConfigs.stream().map(l -> l.getListenerClass())
					.collect(Collectors.toList());
			params += LISTENERS + "=" + String.join(",", listeners) + "\n";
		}
		return params;
	}

	private static URL getUrl(
			final Map<String, String> wsConfig,
			final String configKey,
			final List<String> errors,
			final boolean required) {
		final String urlStr = wsConfig.get(configKey);
		if (nullOrEmpty(urlStr)) {
			if (required) {
				errors.add("Must provide param " + configKey + " in config file");
			}
			return null;
		}
		try {
			return new URL(urlStr);
		} catch (MalformedURLException e) {
			errors.add("Invalid url for parameter " + configKey + ": " + urlStr.trim());
		}
		return null;
	}
	
	public String getHost() {
		return host;
	}

	public String getDBname() {
		return db;
	}
	
	public String getTypeDBName() {
		return typedb;
	}

	public URL getAuthURL() {
		return authURL;
	}
	
	public URL getAuth2URL() {
		return auth2URL;
	}
	
	public Set<String> getAdminRoles() {
		return adminRoles;
	}
	
	public Set<String> getAdminReadOnlyRoles() {
		return adminReadOnlyRoles;
	}
	
	public BackendType getBackendType() {
		return backendType;
	}
	
	public URL getBackendURL() {
		return backendURL;
	}
	
	public String getBackendUser() {
		return backendUser;
	}
	
	public String getBackendToken() {
		return backendToken;
	}

	public String getTempDir() {
		return tempDir;
	}

	public URL getShockURL() {
		return shockURL;
	}
	
	public String getShockUser() {
		return shockUser;
	}
	
	public String getShockToken() {
		return shockToken;
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
