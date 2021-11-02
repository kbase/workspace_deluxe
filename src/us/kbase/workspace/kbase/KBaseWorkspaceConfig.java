package us.kbase.workspace.kbase;

import static java.util.Objects.requireNonNull;
import static us.kbase.workspace.database.Util.checkString;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
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

import org.ini4j.Ini;

import com.google.common.collect.ImmutableMap;

import software.amazon.awssdk.regions.Region;
import us.kbase.common.service.JsonServerServlet;

public class KBaseWorkspaceConfig {
	
	//TODO CONFIG How the config is created could use a rethink. Would be much simpler just throwing an exception rather than collecting errors.
	/* I think it was originally this way because Glassfish would do (?? I forget) when
	 * the WS threw an exception, which we wanted to avoid, and so we made the service start
	 * but throw errors for all calls (e.g. "the service did not start correctly, check the logs".
	 * Now the standard container is Tomcat so try with that and see what makes sense.
	 * On the other hand, it's nice to see all the errors at once *shrug*
	 */
	
	// TODO CONFIG the config docs include the port, but the port is ignored in deploy.cfg
	//    The tomcat cutover didn't handle the documentation update apparently
	//    I would prefer if all the configuration was in one place, namely deploy.cfg,
	//    rather than scattered around multiple files
	//TODO JAVADOCS
	//TODO CODE use optionals instead of nulls.
	//TODO CODE consider returning classes containing related parameters rather than individual parameters.
	
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
	private static final String BACKEND_REGION = "backend-region";
	private static final String BACKEND_CONTAINER = "backend-container";
	private static final String BACKEND_SSC_SSL = "backend-trust-all-ssl-certificates";
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
	
	// shock / blobstore info 
	private static final String BYTESTREAM_USER = "bytestream-user";
	private static final String BYTESTREAM_TOKEN = "bytestream-token";
	private static final String BYTESTREAM_URL = "bytestream-url";
	
	// sample service info 
	private static final String SAMPLE_SERVICE_URL = "sample-service-url";
	private static final String SAMPLE_SERVICE_TOKEN = "sample-service-administrator-token";
	
	//handle service info
	private static final String IGNORE_HANDLE_SERVICE = "ignore-handle-service";
	private static final String HANDLE_SERVICE_URL = "handle-service-url";
	private static final String HANDLE_SERVICE_TOKEN = "handle-service-token";
	// for backwards compatibility
	private static final String HANDLE_MANAGER_TOKEN = "handle-manager-token";
	
	// listeners
	private static final String LISTENERS = "listeners";
	private static final String LISTENER_PREFIX = "listener-";
	private static final String LISTENER_CLASS = "-class";
	private static final String LISTENER_CONFIG = "-config-";
	
	//directory for temp files
	private static final String TEMP_DIR = "temp-dir";
	
	private static final String TRUE_STR = "true";
	
	// the auth2 urls are checked when getting the url
	private static final List<String> REQUIRED_PARAMS = Arrays.asList(
			HOST, DB, TYPE_DB, TEMP_DIR, BACKEND_TYPE);
	
	private static final Map<String, List<String>> BACKEND_TYPES = ImmutableMap.of(
			BackendType.S3.name(), Arrays.asList(BACKEND_TOKEN, BACKEND_URL, BACKEND_USER,
					BACKEND_CONTAINER, BACKEND_REGION),
			BackendType.GridFS.name(), Collections.emptyList());
	
	private final String host;
	private final String db;
	private final String typedb;
	private final BackendType backendType;
	private final Region backendRegion;
	private final String backendContainer;
	private final URL backendURL;
	private final String backendUser;
	private final String backendToken;
	private final boolean backendTrustAllCerts;
	private final String tempDir;
	private final URL bytestreamURL;
	private final String bytestreamUser;
	private final String bytestreamToken;
	private final URL sampleServiceURL;
	private final String sampleServiceToken;
	private final String workspaceAdmin;
	private final String mongoUser;
	private final String mongoPassword;
	private final URL authURL;
	private final URL auth2URL;
	private final Set<String> adminRoles;
	private final Set<String> adminReadOnlyRoles;
	private final boolean ignoreHandleService;
	private final URL handleServiceURL;
	private final String handleServiceToken;
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
	
	@SuppressWarnings("serial")
	public static class KBaseWorkspaceConfigException extends Exception {
		
		/** Create the exception.
		 * @param message the exception message.
		 */
		public KBaseWorkspaceConfigException(final String message) {
			super(message);
		}
		
		/** Create the exception with a cause.
		 * @param message the exception message.
		 * @param cause the cause of this exception.
		 */
		public KBaseWorkspaceConfigException(final String message, final Throwable cause) {
			super(message, cause);
		}
	}
	
	public KBaseWorkspaceConfig(final Path configFile, final String configSection)
			throws KBaseWorkspaceConfigException {
		this(getConfigMap(configFile, configSection));
	}

	private static Map<String, String> getConfigMap(
			final Path configFile,
			final String configSection)
			throws KBaseWorkspaceConfigException {
		final Ini ini;
		try {
			ini = new Ini(configFile.toFile());
		} catch (IOException ioe) {
			throw new KBaseWorkspaceConfigException(
					String.format("Could not read from configuration file %s: %s",
							configFile, ioe.getLocalizedMessage()),
					ioe);
		}
		final Map<String, String> config = ini.get(configSection);
		if (config == null) {
			throw new KBaseWorkspaceConfigException(
					String.format("The configuration file %s has no section %s",
							configFile, configSection));
		}
		return config;
	}

	/** Create the configuration from a mapping as provided by {@link JsonServerServlet}.
	 * @param config the configuration
	 */
	public KBaseWorkspaceConfig(final Map<String, String> config) {
		// this is a really long constructor. Clean up at some point
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
			backendRegion = null;
			backendContainer = null;
			backendTrustAllCerts = false;
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
			backendContainer = nullIfEmpty(config.get(BACKEND_CONTAINER));
			backendRegion = getRegion(config, BACKEND_REGION, paramErrors);
			backendTrustAllCerts = TRUE_STR.equals(nullIfEmpty(config.get(BACKEND_SSC_SSL)));
		}

		bytestreamURL = getUrl(config, BYTESTREAM_URL, paramErrors, false);
		if (bytestreamURL == null) {
			bytestreamUser = null;
			bytestreamToken = null;
		} else {
			bytestreamUser = nullIfEmpty(config.get(BYTESTREAM_USER));
			bytestreamToken = nullIfEmpty(config.get(BYTESTREAM_TOKEN));
			if (bytestreamUser == null || bytestreamToken == null) {
				paramErrors.add(String.format(
						"Must provide %s and %s parameters in config file if %s is provided",
						BYTESTREAM_USER, BYTESTREAM_TOKEN, BYTESTREAM_URL));
			}
		}
		
		sampleServiceURL = getUrl(config, SAMPLE_SERVICE_URL, paramErrors, false);
		if (sampleServiceURL == null) {
			sampleServiceToken = null;
		} else {
			sampleServiceToken = nullIfEmpty(config.get(SAMPLE_SERVICE_TOKEN));
			if (sampleServiceToken == null) {
				paramErrors.add(String.format("If %s is supplied, %s is required",
						SAMPLE_SERVICE_URL, SAMPLE_SERVICE_TOKEN));
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
			handleServiceToken = null;
		} else {
			final String token = nullIfEmpty(config.get(HANDLE_SERVICE_TOKEN));
			final String managertoken = nullIfEmpty(config.get(HANDLE_MANAGER_TOKEN));
			final URL hsURL = getUrl(config, HANDLE_SERVICE_URL, paramErrors, true);
			if (token == null && managertoken == null) {
				handleServiceToken = null;
				handleServiceURL = null;
				paramErrors.add(String.format(
						"Must provide param %s in config file", HANDLE_SERVICE_TOKEN));
			} else {
				handleServiceURL = hsURL;
				handleServiceToken = token == null ? managertoken : token;
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
					paramErrors.add("Invalid listener configuration item: " + key.trim());
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
		// TODO CODE move this up top where it's easier to see & alter, document
		final List<String> paramSet = new LinkedList<String>(
				Arrays.asList(HOST, DB, TYPE_DB, MONGO_USER, KBASE_AUTH_URL, KBASE_AUTH2_URL,
						KBASE_AUTH_ADMIN_READ_ONLY_ROLES, KBASE_AUTH_ADMIN_FULL_ROLES,
						BACKEND_TYPE, BACKEND_URL, BACKEND_USER, BACKEND_REGION,
						BACKEND_CONTAINER, BACKEND_SSC_SSL));
		if (!ignoreHandleService) {
			paramSet.addAll(Arrays.asList(HANDLE_SERVICE_URL));
		}
		if (bytestreamURL != null) {
			paramSet.addAll(Arrays.asList(BYTESTREAM_URL, BYTESTREAM_USER));
		}
		if (sampleServiceURL != null) {
			paramSet.addAll(Arrays.asList(SAMPLE_SERVICE_URL));
		}
		for (final String s: paramSet) {
			if (!nullOrEmpty(cfg.get(s))) {
				// TODO CODE this should probably be the actual instance value so defaults
				// are shown vs the map entry. Means quite a bit more code/annoyance though.
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
	
	// assume optional for now
	private static Region getRegion(
			final Map<String, String> wsConfig,
			final String configKey,
			final List<String> errors) {
		final String regionStr = wsConfig.get(configKey);
		if (nullOrEmpty(regionStr)) {
			return null;
		}
		return Region.of(regionStr.trim());
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
	
	public String getBackendContainer() {
		return backendContainer;
	}
	
	public Region getBackendRegion() {
		return backendRegion;
	}
	
	public boolean getBackendTrustAllCerts() {
		return backendTrustAllCerts;
	}

	public String getTempDir() {
		return tempDir;
	}

	public URL getBytestreamURL() {
		return bytestreamURL;
	}
	
	public String getBytestreamUser() {
		return bytestreamUser;
	}
	
	public String getBytestreamToken() {
		return bytestreamToken;
	}
	
	public URL getSampleServiceURL() {
		return sampleServiceURL;
	}
	
	public String getSampleServiceToken() {
		return sampleServiceToken;
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

	public String getHandleServiceToken() {
		return handleServiceToken;
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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((adminReadOnlyRoles == null) ? 0 : adminReadOnlyRoles.hashCode());
		result = prime * result + ((adminRoles == null) ? 0 : adminRoles.hashCode());
		result = prime * result + ((auth2URL == null) ? 0 : auth2URL.hashCode());
		result = prime * result + ((authURL == null) ? 0 : authURL.hashCode());
		result = prime * result + ((backendContainer == null) ? 0 : backendContainer.hashCode());
		// Region doesn't implement hashcode or equals
		result = prime * result + ((backendRegion == null) ? 0 : backendRegion.id().hashCode());
		result = prime * result + ((backendToken == null) ? 0 : backendToken.hashCode());
		result = prime * result + (backendTrustAllCerts ? 1231 : 1237);
		result = prime * result + ((backendType == null) ? 0 : backendType.hashCode());
		result = prime * result + ((backendURL == null) ? 0 : backendURL.hashCode());
		result = prime * result + ((backendUser == null) ? 0 : backendUser.hashCode());
		result = prime * result + ((bytestreamToken == null) ? 0 : bytestreamToken.hashCode());
		result = prime * result + ((bytestreamURL == null) ? 0 : bytestreamURL.hashCode());
		result = prime * result + ((bytestreamUser == null) ? 0 : bytestreamUser.hashCode());
		result = prime * result + ((db == null) ? 0 : db.hashCode());
		result = prime * result + ((errors == null) ? 0 : errors.hashCode());
		result = prime * result + ((handleServiceToken == null) ? 0 : handleServiceToken.hashCode());
		result = prime * result + ((handleServiceURL == null) ? 0 : handleServiceURL.hashCode());
		result = prime * result + ((host == null) ? 0 : host.hashCode());
		result = prime * result + (ignoreHandleService ? 1231 : 1237);
		result = prime * result + ((infoMessages == null) ? 0 : infoMessages.hashCode());
		result = prime * result + ((listenerConfigs == null) ? 0 : listenerConfigs.hashCode());
		result = prime * result + ((mongoPassword == null) ? 0 : mongoPassword.hashCode());
		result = prime * result + ((mongoUser == null) ? 0 : mongoUser.hashCode());
		result = prime * result + ((paramReport == null) ? 0 : paramReport.hashCode());
		result = prime * result + ((sampleServiceToken == null) ? 0 : sampleServiceToken.hashCode());
		result = prime * result + ((sampleServiceURL == null) ? 0 : sampleServiceURL.hashCode());
		result = prime * result + ((tempDir == null) ? 0 : tempDir.hashCode());
		result = prime * result + ((typedb == null) ? 0 : typedb.hashCode());
		result = prime * result + ((workspaceAdmin == null) ? 0 : workspaceAdmin.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		KBaseWorkspaceConfig other = (KBaseWorkspaceConfig) obj;
		if (adminReadOnlyRoles == null) {
			if (other.adminReadOnlyRoles != null)
				return false;
		} else if (!adminReadOnlyRoles.equals(other.adminReadOnlyRoles))
			return false;
		if (adminRoles == null) {
			if (other.adminRoles != null)
				return false;
		} else if (!adminRoles.equals(other.adminRoles))
			return false;
		if (auth2URL == null) {
			if (other.auth2URL != null)
				return false;
		} else if (!auth2URL.equals(other.auth2URL))
			return false;
		if (authURL == null) {
			if (other.authURL != null)
				return false;
		} else if (!authURL.equals(other.authURL))
			return false;
		if (backendContainer == null) {
			if (other.backendContainer != null)
				return false;
		} else if (!backendContainer.equals(other.backendContainer))
			return false;
		if (backendRegion == null) {
			if (other.backendRegion != null)
				return false;
		// Region doesn't implement hashcode or equals
		} else if (!backendRegion.id().equals(
				other.backendRegion == null ? null : other.backendRegion.id()))
			return false;
		if (backendToken == null) {
			if (other.backendToken != null)
				return false;
		} else if (!backendToken.equals(other.backendToken))
			return false;
		if (backendTrustAllCerts != other.backendTrustAllCerts)
			return false;
		if (backendType != other.backendType)
			return false;
		if (backendURL == null) {
			if (other.backendURL != null)
				return false;
		} else if (!backendURL.equals(other.backendURL))
			return false;
		if (backendUser == null) {
			if (other.backendUser != null)
				return false;
		} else if (!backendUser.equals(other.backendUser))
			return false;
		if (bytestreamToken == null) {
			if (other.bytestreamToken != null)
				return false;
		} else if (!bytestreamToken.equals(other.bytestreamToken))
			return false;
		if (bytestreamURL == null) {
			if (other.bytestreamURL != null)
				return false;
		} else if (!bytestreamURL.equals(other.bytestreamURL))
			return false;
		if (bytestreamUser == null) {
			if (other.bytestreamUser != null)
				return false;
		} else if (!bytestreamUser.equals(other.bytestreamUser))
			return false;
		if (db == null) {
			if (other.db != null)
				return false;
		} else if (!db.equals(other.db))
			return false;
		if (errors == null) {
			if (other.errors != null)
				return false;
		} else if (!errors.equals(other.errors))
			return false;
		if (handleServiceToken == null) {
			if (other.handleServiceToken != null)
				return false;
		} else if (!handleServiceToken.equals(other.handleServiceToken))
			return false;
		if (handleServiceURL == null) {
			if (other.handleServiceURL != null)
				return false;
		} else if (!handleServiceURL.equals(other.handleServiceURL))
			return false;
		if (host == null) {
			if (other.host != null)
				return false;
		} else if (!host.equals(other.host))
			return false;
		if (ignoreHandleService != other.ignoreHandleService)
			return false;
		if (infoMessages == null) {
			if (other.infoMessages != null)
				return false;
		} else if (!infoMessages.equals(other.infoMessages))
			return false;
		if (listenerConfigs == null) {
			if (other.listenerConfigs != null)
				return false;
		} else if (!listenerConfigs.equals(other.listenerConfigs))
			return false;
		if (mongoPassword == null) {
			if (other.mongoPassword != null)
				return false;
		} else if (!mongoPassword.equals(other.mongoPassword))
			return false;
		if (mongoUser == null) {
			if (other.mongoUser != null)
				return false;
		} else if (!mongoUser.equals(other.mongoUser))
			return false;
		if (paramReport == null) {
			if (other.paramReport != null)
				return false;
		} else if (!paramReport.equals(other.paramReport))
			return false;
		if (sampleServiceToken == null) {
			if (other.sampleServiceToken != null)
				return false;
		} else if (!sampleServiceToken.equals(other.sampleServiceToken))
			return false;
		if (sampleServiceURL == null) {
			if (other.sampleServiceURL != null)
				return false;
		} else if (!sampleServiceURL.equals(other.sampleServiceURL))
			return false;
		if (tempDir == null) {
			if (other.tempDir != null)
				return false;
		} else if (!tempDir.equals(other.tempDir))
			return false;
		if (typedb == null) {
			if (other.typedb != null)
				return false;
		} else if (!typedb.equals(other.typedb))
			return false;
		if (workspaceAdmin == null) {
			if (other.workspaceAdmin != null)
				return false;
		} else if (!workspaceAdmin.equals(other.workspaceAdmin))
			return false;
		return true;
	}
	
}
