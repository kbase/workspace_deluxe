package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.set;
import static us.kbase.common.test.controllers.ControllerCommon.makeTempDirs;

import java.io.BufferedWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import nl.jqno.equalsverifier.EqualsVerifier;
import software.amazon.awssdk.regions.Region;
import us.kbase.common.test.MapBuilder;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.kbase.BackendType;
import us.kbase.workspace.kbase.KBaseWorkspaceConfig;
import us.kbase.workspace.kbase.KBaseWorkspaceConfig.KBaseWorkspaceConfigException;
import us.kbase.workspace.kbase.KBaseWorkspaceConfig.ListenerConfig;

public class KBaseWorkspaceConfigTest {
	
	private final static String CI_SERV = "https://ci.kbase.us/services/";
	private final static String AUTH_LEGACY_URL =
			CI_SERV + "auth/api/legacy/KBase/Sessions/Login";
	private final static List<String> MT = Collections.emptyList();
	private final static String IGNORE_HANDLE = 
			"Ignoring Handle Service config. Objects with handle IDs will fail typechecking.";
	private final static String MISSING_PARAM = "Must provide param %s in config file";
	
	private static Path TEMP_DIR;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		TEMP_DIR = makeTempDirs(
				Paths.get(TestCommon.getTempDir()),
				KBaseWorkspaceConfigTest.class.getSimpleName(),
				Collections.emptyList());
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (TEMP_DIR != null && TestCommon.getDeleteTempFiles()) {
			FileUtils.deleteDirectory(TEMP_DIR.toFile());
		}
	}
	
	private <T> List<T> list(@SuppressWarnings("unchecked") T... things) {
		return Arrays.asList(things);
	}

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(KBaseWorkspaceConfig.class).usingGetClass().verify();
		EqualsVerifier.forClass(ListenerConfig.class).usingGetClass().verify();
	}
	
	@Test
	public void listenerConfig() throws Exception {
		final ListenerConfig lc = new ListenerConfig("class", ImmutableMap.of("foo", "bar"));
		
		assertThat("incorrect class", lc.getListenerClass(), is("class"));
		assertThat("incorrect config", lc.getConfig(), is(ImmutableMap.of("foo", "bar")));
	}
	
	@Test
	public void listenerConfigImmutable() throws Exception {
		final Map<String, String> cfg = new HashMap<>();
		cfg.put("foo", "bar");
		final ListenerConfig lc = new ListenerConfig("class", cfg);
		
		try {
			lc.getConfig().put("baz", "bat");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passes.
		}
		
		cfg.put("baz", "bat");
		assertThat("incorrect config", lc.getConfig(), is(ImmutableMap.of("foo", "bar")));
	}
	
	@Test
	public void listenerConstructFailBadArgs() throws Exception {
		final Map<String, String> mt = Collections.emptyMap();
		
		listenerConstructFail(null, mt, new IllegalArgumentException(
				"listenerClass cannot be null or whitespace only"));
		listenerConstructFail("    \t    ", mt, new IllegalArgumentException(
				"listenerClass cannot be null or whitespace only"));
		
		listenerConstructFail("l", null, new NullPointerException("config"));
	}
	
	private void listenerConstructFail(
			final String clazz,
			final Map<String, String> config,
			final Exception expected) {
		try {
			new ListenerConfig(clazz, config);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void constructFailNullMap() throws Exception {
		try {
			new KBaseWorkspaceConfig(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("config"));
		}
	}
	
	@Test
	public void loadFromFileFail() throws Exception {
		final Path fakefile = Paths.get("superfakefile_princesspeachismyirlgf");
		failLoadFromFile(fakefile, "thing", new KBaseWorkspaceConfigException(String.format(
				"Could not read from configuration file superfakefile_princesspeachismyirlgf: " +
						"%s (No such file or directory)", fakefile.toAbsolutePath())));

		final Path notsofakefile = TEMP_DIR.resolve("imnotfakeimnotfake");
		Files.write(notsofakefile, list(""), StandardCharsets.UTF_8);
		failLoadFromFile(notsofakefile, "thing", new KBaseWorkspaceConfigException(String.format(
				"The configuration file %s/imnotfakeimnotfake has no section thing", TEMP_DIR)));
		
		final Path realfile = TEMP_DIR.resolve("keepinitreal");
		Files.write(realfile, list("[Workspace]", "mongodb-host=foo"), StandardCharsets.UTF_8);
		failLoadFromFile(realfile, null, new KBaseWorkspaceConfigException(String.format(
				"The configuration file %s/keepinitreal has no section null", TEMP_DIR)));
		failLoadFromFile(realfile, "Workspacey", new KBaseWorkspaceConfigException(String.format(
				"The configuration file %s/keepinitreal has no section Workspacey", TEMP_DIR)));
	}
	
	private void failLoadFromFile(
			final Path file,
			final String section,
			final Exception expected) {
		try {
			new KBaseWorkspaceConfig(file, section);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	// TODO NOW_NEXT_PR reorder the args to be more sensible
	// good heavens, what what absolutely ghastly code. I may faint
	// hey kids, don't try this at home
	private void assertConfigCorrect(
			final Map<String, String> cfg,
			final Set<String> adminReadOnlyRoles,
			final Set<String> adminRoles,
			final URL auth2URL,
			final URL authURL,
			final String backendToken,
			final BackendType backendType,
			final URL backendURL,
			final String backendUser,
			final String backendContiner,
			final Region backendRegion,
			final boolean backendTrustAllCerts,
			final String dbname,
			final List<String> errors,
			final String handleServiceToken,
			final URL handleServiceURL,
			final String host,
			final List<String> infoMessages,
			final List<ListenerConfig> listenerConfigs,
			final String mongoPwd,
			final String mongoUser,
			final String paramReport,
			final String bytestreamToken,
			final URL bytestreamURL,
			final String bytestreamUser,
			final String sampleServiceToken,
			final URL sampleServiceURL,
			final String tempDir,
			final String typeDBname,
			final String workspaceAdmin,
			final boolean hasErrors,
			final boolean ignoreHandleService)
			throws Exception {
		final Path tempfile = Files.createTempFile(TEMP_DIR, TestCommon.getMethodName(2), "");
		try (final BufferedWriter w = Files.newBufferedWriter(tempfile)) {
			w.write("[Workspace]\n");
			for (final Entry<String, String> e: cfg.entrySet()) {
				w.write(String.format("%s=%s\n",
						e.getKey(), e.getValue() == null ? "" : e.getValue()));
			}
		}
		for (final KBaseWorkspaceConfig kwc: list(
				new KBaseWorkspaceConfig(cfg),
				new KBaseWorkspaceConfig(tempfile, "Workspace")
				)) {
		
			assertThat("incorrect admin read roles",
					kwc.getAdminReadOnlyRoles(), is(adminReadOnlyRoles));
			assertThat("incorrect admin roles", kwc.getAdminRoles(), is(adminRoles));
			assertThat("incorrect auth2 url", kwc.getAuth2URL(), is(auth2URL));
			assertThat("incorrect auth url", kwc.getAuthURL(), is(authURL));
			assertThat("incorrect backend token", kwc.getBackendToken(), is(backendToken));
			assertThat("incorrect backend type", kwc.getBackendType(), is(backendType));
			assertThat("incorrect backend url", kwc.getBackendURL(), is(backendURL));
			assertThat("incorrect backend user", kwc.getBackendUser(), is(backendUser));
			assertThat("incorrect backend container",
					kwc.getBackendContainer(), is(backendContiner));
			assertThat("incorrect backend region", kwc.getBackendRegion(), is(backendRegion));
			assertThat("incorrect backend trust certs",
					kwc.getBackendTrustAllCerts(), is(backendTrustAllCerts));
			assertThat("incorrect db", kwc.getDBname(), is(dbname));
			assertThat("incorrect errors", kwc.getErrors(), is(errors));
			assertThat("incorrect srvc token",
					kwc.getHandleServiceToken(), is(handleServiceToken));
			assertThat("incorrect srvc url", kwc.getHandleServiceURL(), is(handleServiceURL));
			assertThat("incorrect host", kwc.getHost(), is(host));
			assertThat("incorrect info msgs", kwc.getInfoMessages(), is(infoMessages));
			assertThat("incorrect listeners", kwc.getListenerConfigs(), is(listenerConfigs));
			assertThat("incorrect mongo pwd", kwc.getMongoPassword(), is(mongoPwd));
			assertThat("incorrect mongo user", kwc.getMongoUser(), is(mongoUser));
			assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
			assertThat("incorrect bytestream token",
					kwc.getBytestreamToken(), is(bytestreamToken));
			assertThat("incorrect bytestream url", kwc.getBytestreamURL(), is(bytestreamURL));
			assertThat("incorrect bytestream user", kwc.getBytestreamUser(), is(bytestreamUser));
			assertThat("incorrect sample token",
					kwc.getSampleServiceToken(), is(sampleServiceToken));
			assertThat("incorrect sample url", kwc.getSampleServiceURL(), is(sampleServiceURL));
			assertThat("incorrect temp dir", kwc.getTempDir(), is(tempDir));
			assertThat("incorrect type db", kwc.getTypeDBName(), is(typeDBname));
			assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), is(workspaceAdmin));
			assertThat("incorrect has err", kwc.hasErrors(), is(hasErrors));
			assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(ignoreHandleService));
		}
	}
	
	@Test
	public void configMinimal() throws Exception {
		final Map<String, String> cfg = MapBuilder.<String, String>newHashMap()
				.with("mongodb-host", "somehost")
				.with("mongodb-database", "somedb")
				.with("mongodb-type-database", "typedb")
				.with("backend-type", "   GridFS   ")
				.with("temp-dir", "temp")
				.with("auth-service-url", AUTH_LEGACY_URL)
				.with("auth2-service-url", CI_SERV + "auth")
				.with("ignore-handle-service", "blearg")
				.build();
		
		final String paramReport =
				"mongodb-host=somehost\n" +
				"mongodb-database=somedb\n" +
				"mongodb-type-database=typedb\n" +
				"auth-service-url=" + AUTH_LEGACY_URL + "\n" +
				"auth2-service-url=" + CI_SERV + "auth\n" +
				"backend-type=GridFS\n";
		
		assertConfigCorrect(
				cfg,									// config under test
				set(),									// admin read only roles
				set(),									// admin roles
				new URL(CI_SERV + "auth"),				// auth root url
				new URL(AUTH_LEGACY_URL),				// auth legacy api url
				null,									// backend token
				BackendType.GridFS,						// backend type
				null,									// backend url
				null,									// backend user
				null,									// backend container
				null,									// backend region
				false,									// backend trust all certs
				"somedb",								// mongo db
				MT,										// errors
				null,									// handle service token
				null,									// handle service url
				"somehost",								// mongo host
				Arrays.asList(IGNORE_HANDLE),			// info messages
				Collections.emptyList(),				// listener configs
				null,									// mongo password
				null,									// mongo user
				paramReport,							// param report
				null,									// bytestream token
				null,									// bytestream URL
				null,									// bytestream user
				null,									// sample service token
				null,									// sample service URL
				"temp",									// temp dir
				"typedb",								// mongo type db name
				null,									// workspace admin
				false,									// has errors
				true);									// ignore handle service
	}
	
	@Test
	public void configMaximalS3() throws Exception {
		// also tests full sample service config
		final Map<String, String> cfg = MapBuilder.<String, String>newHashMap()
				.with("mongodb-host", "    somehost    ")
				.with("mongodb-database", "    somedb   ")
				.with("mongodb-type-database", "     typedb     ")
				.with("temp-dir", "   temp   ")
				.with("auth-service-url", "    " + AUTH_LEGACY_URL + "    ")
				.with("auth2-service-url", "   " + CI_SERV + "auth     ")
				.with("mongodb-user", "   muser   ")
				.with("mongodb-pwd", "    mpwd    ")
				.with("ws-admin", "    wsadminuser     ")
				.with("auth2-ws-admin-read-only-roles", "   role1,   ,   role2   , ")
				.with("auth2-ws-admin-full-roles", "   role3,   ,   role4   , ")
				.with("backend-type", "   S3   ")
				.with("backend-url", "   http://localhost:34567    ")
				.with("backend-user", "    someuser    ")
				.with("backend-token", "    token token token    ")
				.with("backend-container", "   mahbukkit   ")
				.with("backend-region", "   a-lovely-region   ")
				.with("backend-trust-all-ssl-certificates", "trudat")
				.with("handle-service-token", "    hstoken    ")
				.with("handle-manager-token", "    hmtoken    ")  // test service takes precedence
				.with("handle-manager-url", "    " + CI_SERV + "handle_mngr     ")
				.with("handle-service-url", "     " + CI_SERV + "handle_service    ")
				.with("bytestream-url", "   " + CI_SERV + "shock-api2    ")
				.with("bytestream-user", "    otheruser    ")
				.with("bytestream-token", "    token token    ")
				.with("sample-service-url", "     " + CI_SERV + "sample_service2    ")
				.with("sample-service-administrator-token", "    sstoken2    ")
				.with("listeners", "listener1,   ,   listener2  , ")
				.with("listener-listener1-class", "    us.kbase.MyListener     ")
				.with("listener-listener1-config-key1", "value1")
				.with("listener-listener1-config-key2", "value2")
				.with("listener-listener2-class", "    us.kbase.MyListener2     ")
				.with("listener-listener2-config-key1", "value3")
				.build();
		
		final String paramReport =
				"mongodb-host=somehost\n" +
				"mongodb-database=somedb\n" +
				"mongodb-type-database=typedb\n" +
				"mongodb-user=muser\n" +
				"auth-service-url=" + AUTH_LEGACY_URL + "\n" +
				"auth2-service-url=" + CI_SERV + "auth\n" +
				"auth2-ws-admin-read-only-roles=role1,   ,   role2   ,\n" +
				"auth2-ws-admin-full-roles=role3,   ,   role4   ,\n" +
				"backend-type=S3\n" +
				"backend-url=http://localhost:34567\n" +
				"backend-user=someuser\n" + 
				"backend-region=a-lovely-region\n" +
				"backend-container=mahbukkit\n" +
				"backend-trust-all-ssl-certificates=trudat\n" +
				"handle-service-url=" + CI_SERV + "handle_service\n" +
				"bytestream-url=" + CI_SERV + "shock-api2\n" +
				"bytestream-user=otheruser\n" +
				"sample-service-url=" + CI_SERV +"sample_service2\n" +
				"mongodb-pwd=[redacted for your safety and comfort]\n" +
				"listeners=us.kbase.MyListener,us.kbase.MyListener2\n";
		
		assertConfigCorrect(
				cfg,									// config under test
				set("role1", "role2"),					// admin read only roles
				set("role3", "role4"),					// admin roles
				new URL(CI_SERV + "auth"),				// auth root url
				new URL(AUTH_LEGACY_URL),				// auth legacy api url
				"token token token",					// backend token
				BackendType.S3,							// backend type
				new URL("http://localhost:34567"),		// backend url
				"someuser",								// backend user
				"mahbukkit",							// backend container
				Region.of("a-lovely-region"),			// backend region
				false,									// backend trust all certs
				"somedb",								// mongo db
				MT,										// errors
				"hstoken",								// handle service token
				new URL(CI_SERV + "handle_service"),	// handle service url
				"somehost",								// mongo host
				Collections.emptyList(),				// info messages
				Arrays.asList(							// listener configs
						new ListenerConfig("us.kbase.MyListener",
								ImmutableMap.of("key1", "value1", "key2", "value2")),
						new ListenerConfig("us.kbase.MyListener2",
								ImmutableMap.of("key1", "value3"))),
				"mpwd",									// mongo password
				"muser",								// mongo user
				paramReport,							// param report
				"token token",							// bytestream token
				new URL(CI_SERV + "shock-api2"),		// bytestream URL
				"otheruser",							// bytestream user
				"sstoken2",								// sample service token
				new URL(CI_SERV + "sample_service2"),	// sample service URL
				"temp",									// temp dir
				"typedb",								// mongo type db name
				"wsadminuser",							// workspace admin
				false,									// has errors
				false);									// ignore handle service
	}
	
	@Test
	public void configWithWhitespace() throws Exception {
		final Map<String, String> cfg = MapBuilder.<String, String>newHashMap()
				.with("mongodb-host", "somehost")
				.with("mongodb-database", "somedb")
				.with("mongodb-type-database", "     typedb     ")
				.with("temp-dir", "temp")
				.with("auth-service-url", AUTH_LEGACY_URL)
				.with("auth2-service-url", CI_SERV + "auth")
				.with("mongodb-user", "   \t    ")
				.with("mongodb-pwd", "   \t    ")
				.with("ws-admin", "   \t    ")
				.with("ignore-handle-service", "   \t   ")
				.with("auth2-ws-admin-read-only-roles", "   \t    ")
				.with("auth2-ws-admin-full-roles", "   \t    ")
				.with("backend-type", "   GridFS   ")
				.with("backend-token", "   \t    ")
				.with("backend-user", "   \t    ")
				.with("backend-url", "   \t    ")
				.with("backend-container", "   \t    ")
				.with("backend-trust-all-ssl-certificates", "   \t    ")
				.with("backend-region", "   \t    ")
				.with("handle-manager-token", "       hmtoken   ") // test that backwards compat ok
				.with("handle-service-url", CI_SERV + "handle_service")
				.with("bytestream-token", "   \t    ")
				.with("bytestream-user", "   \t    ")
				.with("bytestream-url", "   \t    ")
				.with("sample-service-url", "     " + CI_SERV + "sample_service3    ")
				.with("sample-service-administrator-token", "    sstoken3    ")
				.with("listeners", "   \t    ")
				.build();
		
		final String paramReport =
				"mongodb-host=somehost\n" +
				"mongodb-database=somedb\n" +
				"mongodb-type-database=typedb\n" +
				"auth-service-url=" + AUTH_LEGACY_URL + "\n" +
				"auth2-service-url=" + CI_SERV + "auth\n" +
				"backend-type=GridFS\n" +
				"handle-service-url=" + CI_SERV + "handle_service\n" +
				"sample-service-url=" + CI_SERV +"sample_service3\n";
		
		assertConfigCorrect(
				cfg,									// config under test
				set(),									// admin read only roles
				set(),									// admin roles
				new URL(CI_SERV + "auth"),				// auth root url
				new URL(AUTH_LEGACY_URL),				// auth legacy api url
				null,									// backend token
				BackendType.GridFS,						// backend type
				null,									// backend url
				null,									// backend user
				null,									// backend container
				null,									// backend region
				false,									// backend trust all certs
				"somedb",								// mongo db
				MT,										// errors
				"hmtoken",								// handle service token
				new URL(CI_SERV + "handle_service"),	// handle service url
				"somehost",								// mongo host
				Collections.emptyList(),				// info messages
				Collections.emptyList(),				// listener configs
				null,									// mongo password
				null,									// mongo user
				paramReport,							// param report
				null,									// bytestream token
				null,									// bytestream URL
				null,									// bytestream user
				"sstoken3",								// sample service token
				new URL(CI_SERV + "sample_service3"),	// sample service URL
				"temp",									// temp dir
				"typedb",								// mongo type db name
				null,									// workspace admin
				false,									// has errors
				false);									// ignore handle service
	}
	
	@Test
	public void immutable() throws Exception {
		final Map<String, String> cfg = MapBuilder.<String, String>newHashMap()
				.with("mongodb-host", "somehost")
				.with("mongodb-database", "somedb")
				.with("temp-dir", "temp")
				.with("auth-service-url", AUTH_LEGACY_URL)
				.with("auth2-service-url", CI_SERV + "auth")
				.with("ignore-handle-service", "true")
				.with("auth2-ws-admin-read-only-roles", " r1  \t    ")
				.with("auth2-ws-admin-full-roles", "   \t   r3 ")
				.with("listeners", "listener1,   ,    , ")
				.with("listener-listener1-class", "    us.kbase.MyListener     ")
				.build();
		
		final KBaseWorkspaceConfig kwc = new KBaseWorkspaceConfig(cfg);
		
		try {
			kwc.getAdminReadOnlyRoles().add("foo");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
		
		try {
			kwc.getAdminRoles().add("foo");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
		
		try {
			kwc.getErrors().add("foo");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
		
		try {
			kwc.getInfoMessages().add("foo");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
		
		try {
			kwc.getListenerConfigs().add(new ListenerConfig("c", Collections.emptyMap()));
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
	}
	
	@Test
	public void configFailNullRequiredEntries() throws Exception {
		final Map<String, String> cfg = MapBuilder.<String, String>newHashMap()
				.with("mongodb-host", null)
				.with("mongodb-database", null)
				.with("mongodb-type-database", null)
				.with("mongodb-user", "user")
				.with("mongodb-pwd", null)
				.with("temp-dir", null)
				.with("auth-service-url", null)
				.with("auth2-service-url", null)
				.with("backend-type", null)
				.with("handle-manager-token", null)
				.with("handle-service-url", null)
				.with("sample-service-url", "https://foo.com")
				.with("sample-service-administrator-token", null)
				.with("listeners", "listener1,   ,   listener2  , ")
				.with("listener-listener1-class", null)
				.with("listener-listener1-config-key1", "value1")
				.with("listener-listener1-config-key2", "value2")
				.with("listener-listener2-class", "us.kbase.MyListener2")
				.with("listener-listener2-config-", "value3")
				.build();
		
		final List<String> errors = Arrays.asList(
				String.format(MISSING_PARAM, "mongodb-host"),
				String.format(MISSING_PARAM, "mongodb-database"),
				String.format(MISSING_PARAM, "mongodb-type-database"),
				String.format(MISSING_PARAM, "temp-dir"),
				String.format(MISSING_PARAM, "backend-type"),
				String.format(MISSING_PARAM, "auth-service-url"),
				String.format(MISSING_PARAM, "auth2-service-url"),
				"If sample-service-url is supplied, sample-service-administrator-token is " +
						"required",
				"Must provide both mongodb-user and mongodb-pwd params in config file if " +
						"MongoDB authentication is to be used",
				String.format(MISSING_PARAM, "handle-service-url"),
				String.format(MISSING_PARAM, "handle-service-token"),
				"Missing listener class: listener-listener1-class",
				"Invalid listener configuration item: listener-listener2-config-");
		
		final String paramReport = "mongodb-user=user\nsample-service-url=https://foo.com\n";
		
		assertConfigCorrect(
				cfg,									// config under test
				set(),									// admin read only roles
				set(),									// admin roles
				null,									// auth root url
				null,									// auth legacy api url
				null,									// backend token
				null,									// backend type
				null,									// backend url
				null,									// backend user
				null,									// backend container
				null,									// backend region
				false,									// backend trust all certs
				null,									// mongo db
				errors,									// errors
				null,									// handle service token
				null,									// handle service url
				null,									// mongo host
				Collections.emptyList(),				// info messages
				Collections.emptyList(),				// listener configs
				null,									// mongo password
				null,									// mongo user
				paramReport,							// param report
				null,									// bytestream token
				null,									// bytestream URL
				null,									// bytestream user
				null,									// sample service token
				new URL("https://foo.com"),				// sample service URL
				null,									// temp dir
				null,									// mongo type db name
				null,									// workspace admin
				true,									// has errors
				false);									// ignore handle service
	}
	
	@Test
	public void configFailWhitespaceRequiredEntries() throws Exception {
		final Map<String, String> cfg = MapBuilder.<String, String>newHashMap()
				.with("mongodb-host", "   \t    ")
				.with("mongodb-database", "   \t    ")
				.with("mongodb-type-database", "   \t    ")
				.with("mongodb-user", "   \t    ")
				.with("mongodb-pwd", "pwd")
				.with("backend-type", "   \t    ")
				.with("temp-dir", "   \t    ")
				.with("auth-service-url", "   \t    ")
				.with("auth2-service-url", "   \t    ")
				.with("handle-manager-token", "   \t    ")
				.with("handle-service-url", "   \t    ")
				.with("sample-service-url", "https://foo2.com")
				.with("sample-service-administrator-token", "    \t    ")
				.with("listeners", "listener1,   ,   listener2  , ")
				.with("listener-listener1-class", "   \t    ")
				.with("listener-listener1-config-key1", "value1")
				.with("listener-listener1-config-key2", "value2")
				.with("listener-listener2-class", "us.kbase.MyListener2")
				.with("listener-listener2-config-    \t   ", "value3")
				.build();
		
		final List<String> errors = Arrays.asList(
				String.format(MISSING_PARAM, "mongodb-host"),
				String.format(MISSING_PARAM, "mongodb-database"),
				String.format(MISSING_PARAM, "mongodb-type-database"),
				String.format(MISSING_PARAM, "temp-dir"),
				String.format(MISSING_PARAM, "backend-type"),
				String.format(MISSING_PARAM, "auth-service-url"),
				String.format(MISSING_PARAM, "auth2-service-url"),
				"If sample-service-url is supplied, sample-service-administrator-token is " +
						"required",
				"Must provide both mongodb-user and mongodb-pwd params in config file if " +
						"MongoDB authentication is to be used",
				String.format(MISSING_PARAM, "handle-service-url"),
				String.format(MISSING_PARAM, "handle-service-token"),
				"Missing listener class: listener-listener1-class",
				"Invalid listener configuration item: listener-listener2-config-");
		
		final String paramReport = "sample-service-url=https://foo2.com\n";
		
		assertConfigCorrect(
				cfg,									// config under test
				set(),									// admin read only roles
				set(),									// admin roles
				null,									// auth root url
				null,									// auth legacy api url
				null,									// backend token
				null,									// backend type
				null,									// backend url
				null,									// backend user
				null,									// backend container
				null,									// backend region
				false,									// backend trust all certs
				null,									// mongo db
				errors,										// errors
				null,									// handle service token
				null,									// handle service url
				null,									// mongo host
				Collections.emptyList(),				// info messages
				Collections.emptyList(),				// listener configs
				null,									// mongo password
				null,									// mongo user
				paramReport,							// param report
				null,									// bytestream token
				null,									// bytestream URL
				null,									// bytestream user
				null,									// sample service token
				new URL("https://foo2.com"),			// sample service URL
				null,									// temp dir
				null,									// mongo type db name
				null,									// workspace admin
				true,									// has errors
				false);									// ignore handle service
	}
	
	@Test
	public void configFailBadURLs() throws Exception {
		final Map<String, String> cfg = MapBuilder.<String, String>newHashMap()
				.with("mongodb-host", "    somehost    ")
				.with("mongodb-database", "    somedb   ")
				.with("mongodb-type-database", "    typedb   ")
				.with("backend-type", "S3")
				.with("backend-token", "   bet   ")
				.with("backend-user", "   buser  ")
				.with("backend-url", "    crappy ass url for backend   ")
				.with("backend-container", "foo")
				.with("backend-region", " over there")
				.with("bytestream-url", "    crappy ass url for shock   ")
				.with("temp-dir", "   temp   ")
				.with("auth-service-url", "   crappy ass url   ")
				.with("auth2-service-url", "   crappy ass url2   ")
				.with("handle-manager-token", "    hmtoken    ")
				.with("handle-service-url", "   crappy ass url4   ")
				.with("sample-service-url", "   crappy ass url5   ")
				.with("sample-service-administration-token", "   t   ")
				.build();
		
		final String paramReport =
				"mongodb-host=somehost\n" +
				"mongodb-database=somedb\n" +
				"mongodb-type-database=typedb\n" +
				"auth-service-url=crappy ass url\n" +
				"auth2-service-url=crappy ass url2\n" +
				"backend-type=S3\n" +
				"backend-url=crappy ass url for backend\n" +
				"backend-user=buser\n" +
				"backend-region=over there\n" +
				"backend-container=foo\n" +
				"handle-service-url=crappy ass url4\n";
		
		final String err = "Invalid url for parameter %s: crappy ass url%s";
		
		final List<String> errors = Arrays.asList(
				String.format(err, "auth-service-url", ""),
				String.format(err, "auth2-service-url", "2"),
				String.format(err, "backend-url", " for backend"),
				String.format(err, "bytestream-url", " for shock"),
				String.format(err, "sample-service-url", "5"),
				String.format(err, "handle-service-url", "4"));
		
		assertConfigCorrect(
				cfg,									// config under test
				set(),									// admin read only roles
				set(),									// admin roles
				null,									// auth root url
				null,									// auth legacy api url
				"bet",									// backend token
				BackendType.S3,							// backend type
				null,									// backend url
				"buser",								// backend user
				"foo",									// backend container
				Region.of("over there"),				// backend region
				false,									// backend trust all certs
				"somedb",								// mongo db
				errors,									// errors
				"hmtoken",								// handle service token
				null,									// handle service url
				"somehost",								// mongo host
				Collections.emptyList(),				// info messages
				Collections.emptyList(),				// listener configs
				null,									// mongo password
				null,									// mongo user
				paramReport,							// param report
				null,									// bytestream token
				null,									// bytestream URL
				null,									// bytestream user
				null,									// sample service token
				null,									// sample service URL
				"temp",									// temp dir
				"typedb",								// mongo type db name
				null,									// workspace admin
				true,									// has errors
				false);									// ignore handle service
	}
	
	@Test
	public void configFailDuplicateDBs() throws Exception {
		final Map<String, String> cfg = MapBuilder.<String, String>newHashMap()
				.with("mongodb-host", "    somehost    ")
				.with("mongodb-database", "    somedb   ")
				.with("mongodb-type-database", "    somedb   ")
				.with("backend-type", "GridFS")
				.with("temp-dir", "   temp   ")
				.with("auth-service-url", AUTH_LEGACY_URL)
				.with("auth2-service-url", CI_SERV + "auth")
				.with("ignore-handle-service", "foo")
				.build();
		
		final String paramReport =
				"mongodb-host=somehost\n" +
				"mongodb-database=somedb\n" +
				"mongodb-type-database=somedb\n" +
				"auth-service-url=" + AUTH_LEGACY_URL + "\n" +
				"auth2-service-url=" + CI_SERV + "auth\n" +
				"backend-type=GridFS\n";
		
		final List<String> errors = Arrays.asList(
				"The parameters mongodb-database and mongodb-type-database have the same " +
				"value, somedb");
		
		assertConfigCorrect(
				cfg,									// config under test
				set(),									// admin read only roles
				set(),									// admin roles
				new URL(CI_SERV + "auth"),				// auth root url
				new URL(AUTH_LEGACY_URL),				// auth legacy api url
				null,									// backend token
				BackendType.GridFS,						// backend type
				null,									// backend url
				null,									// backend user
				null,									// backend container
				null,									// backend region
				false,									// backend trust all certs
				"somedb",								// mongo db
				errors,									// errors
				null,									// handle service token
				null,									// handle service url
				"somehost",								// mongo host
				Arrays.asList(IGNORE_HANDLE),			// info messages
				Collections.emptyList(),				// listener configs
				null,									// mongo password
				null,									// mongo user
				paramReport,							// param report
				null,									// bytestream token
				null,									// bytestream URL
				null,									// bytestream user
				null,									// sample service token
				null,									// sample service URL
				"temp",									// temp dir
				"somedb",								// mongo type db name
				null,									// workspace admin
				true,									// has errors
				true);									// ignore handle service
	}
	
	@Test
	public void configFailIllegalBackendType() throws Exception {
		final Map<String, String> cfg = MapBuilder.<String, String>newHashMap()
				.with("mongodb-host", "    somehost    ")
				.with("mongodb-database", "    somedb   ")
				.with("mongodb-type-database", "    typedb   ")
				.with("backend-type", "   GreedFS   ")
				.with("temp-dir", "   temp   ")
				.with("auth-service-url", AUTH_LEGACY_URL)
				.with("auth2-service-url", CI_SERV + "auth")
				.with("ignore-handle-service", "foo")
				.build();
		
		final String paramReport =
				"mongodb-host=somehost\n" +
				"mongodb-database=somedb\n" +
				"mongodb-type-database=typedb\n" +
				"auth-service-url=" + AUTH_LEGACY_URL + "\n" +
				"auth2-service-url=" + CI_SERV + "auth\n" +
				"backend-type=GreedFS\n";
		
		final List<String> errors = Arrays.asList("Illegal backend type: GreedFS");
		
		assertConfigCorrect(
				cfg,									// config under test
				set(),									// admin read only roles
				set(),									// admin roles
				new URL(CI_SERV + "auth"),				// auth root url
				new URL(AUTH_LEGACY_URL),				// auth legacy api url
				null,									// backend token
				null,									// backend type
				null,									// backend url
				null,									// backend user
				null,									// backend container
				null,									// backend region
				false,									// backend trust all certs
				"somedb",								// mongo db
				errors,									// errors
				null,									// handle service token
				null,									// handle service url
				"somehost",								// mongo host
				Arrays.asList(IGNORE_HANDLE),			// info messages
				Collections.emptyList(),				// listener configs
				null,									// mongo password
				null,									// mongo user
				paramReport,							// param report
				null,									// bytestream token
				null,									// bytestream URL
				null,									// bytestream user
				null,									// sample service token
				null,									// sample service URL
				"temp",									// temp dir
				"typedb",								// mongo type db name
				null,									// workspace admin
				true,									// has errors
				true);									// ignore handle service
	}
	
	@Test
	public void configFailBytestreamParamsMissing() throws Exception {
		configFailBytestreamParamsMissing(null, "user", "bytestream-user=user\n");
		configFailBytestreamParamsMissing("    \t    ", "user", "bytestream-user=user\n");
		configFailBytestreamParamsMissing("token", null, "");
		configFailBytestreamParamsMissing("token", "   \t     ", "");
	}

	private void configFailBytestreamParamsMissing(
			String bytestreamToken,
			String bytestreamUser,
			final String paramReportLast)
			throws Exception {
		final Map<String, String> cfg = MapBuilder.<String, String>newHashMap()
				.with("mongodb-host", "    somehost    ")
				.with("mongodb-database", "    somedb   ")
				.with("mongodb-type-database", "    typedb   ")
				.with("backend-type", "GridFS")
				.with("bytestream-token", bytestreamToken)
				.with("bytestream-user", bytestreamUser)
				.with("bytestream-url", "https://foo.com")
				.with("temp-dir", "   temp   ")
				.with("auth-service-url", AUTH_LEGACY_URL)
				.with("auth2-service-url", CI_SERV + "auth")
				.with("ignore-handle-service", "foo")
				.build();
		
		final String paramReport =
				"mongodb-host=somehost\n" +
				"mongodb-database=somedb\n" +
				"mongodb-type-database=typedb\n" +
				"auth-service-url=" + AUTH_LEGACY_URL + "\n" +
				"auth2-service-url=" + CI_SERV + "auth\n" +
				"backend-type=GridFS\n" +
				"bytestream-url=https://foo.com\n" + 
				paramReportLast;
		
		final List<String> errors = Arrays.asList(
				"Must provide bytestream-user and bytestream-token parameters in config file if " +
				"bytestream-url is provided");
		
		bytestreamToken = bytestreamToken == null || bytestreamToken.trim().isEmpty() ?
				null : bytestreamToken;
		bytestreamUser = bytestreamUser == null || bytestreamUser.trim().isEmpty() ?
				null : bytestreamUser;

		assertConfigCorrect(
				cfg,									// config under test
				set(),									// admin read only roles
				set(),									// admin roles
				new URL(CI_SERV + "auth"),				// auth root url
				new URL(AUTH_LEGACY_URL),				// auth legacy api url
				null,									// backend token
				BackendType.GridFS,						// backend type
				null,									// backend url
				null,									// backend user
				null,									// backend container
				null,									// backend region
				false,									// backend trust all certs
				"somedb",								// mongo db
				errors,									// errors
				null,									// handle service token
				null,									// handle service url
				"somehost",								// mongo host
				Arrays.asList(IGNORE_HANDLE),			// info messages
				Collections.emptyList(),				// listener configs
				null,									// mongo password
				null,									// mongo user
				paramReport,							// param report
				bytestreamToken,						// bytestream token
				new URL("https://foo.com"),				// bytestream URL
				bytestreamUser,							// bytestream user
				null,									// sample service token
				null,									// sample service URL
				"temp",									// temp dir
				"typedb",								// mongo type db name
				null,									// workspace admin
				true,									// has errors
				true);									// ignore handle service
	}
	
	@Test
	public void configFailS3ParamsMissing() throws Exception {
		configFailS3ParamsMissing(null);
		configFailS3ParamsMissing("    \t   ");
	}

	private void configFailS3ParamsMissing(final String backendParam) throws Exception {
		final Map<String, String> cfg = MapBuilder.<String, String>newHashMap()
				.with("mongodb-host", "    somehost    ")
				.with("mongodb-database", "    somedb   ")
				.with("mongodb-type-database", "    typedb   ")
				.with("backend-type", "S3")
				.with("backend-token", backendParam)
				.with("backend-user", backendParam)
				.with("backend-url", backendParam)
				.with("backend-container", backendParam)
				.with("backend-region", backendParam)
				.with("temp-dir", "   temp   ")
				.with("auth-service-url", AUTH_LEGACY_URL)
				.with("auth2-service-url", CI_SERV + "auth")
				.with("ignore-handle-service", "foo")
				.build();
		
		final String paramReport =
				"mongodb-host=somehost\n" +
				"mongodb-database=somedb\n" +
				"mongodb-type-database=typedb\n" +
				"auth-service-url=" + AUTH_LEGACY_URL + "\n" +
				"auth2-service-url=" + CI_SERV + "auth\n" +
				"backend-type=S3\n";
		
		final String err = "Must provide S3 param %s in config file";
		
		final List<String> errors = Arrays.asList(
				String.format(err, "backend-token"),
				String.format(err, "backend-url"),
				String.format(err, "backend-user"),
				String.format(err, "backend-container"),
				String.format(err, "backend-region"));
		
		assertConfigCorrect(
				cfg,									// config under test
				set(),									// admin read only roles
				set(),									// admin roles
				new URL(CI_SERV + "auth"),				// auth root url
				new URL(AUTH_LEGACY_URL),				// auth legacy api url
				null,									// backend token
				BackendType.S3,							// backend type
				null,									// backend url
				null,									// backend user
				null,									// backend container
				null,									// backend region
				false,									// backend trust all certs
				"somedb",								// mongo db
				errors,										// errors
				null,									// handle service token
				null,									// handle service url
				"somehost",								// mongo host
				Arrays.asList(IGNORE_HANDLE),			// info messages
				Collections.emptyList(),				// listener configs
				null,									// mongo password
				null,									// mongo user
				paramReport,							// param report
				null,									// bytestream token
				null,									// bytestream URL
				null,									// bytestream user
				null,									// sample service token
				null,									// sample service URL
				"temp",									// temp dir
				"typedb",								// mongo type db name
				null,									// workspace admin
				true,									// has errors
				true);									// ignore handle service
	}
}
