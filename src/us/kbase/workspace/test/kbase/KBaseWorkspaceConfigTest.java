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
	
	private static class ExpectedConfig {
		
		public URL auth2URL = null;
		public URL authURL = null;
		public Set<String> adminReadOnlyRoles = set();
		public Set<String> adminRoles = set();
		public String workspaceAdmin = null;
		public String mongohost = null;
		public String mongoDBname = null;
		public String typeDBname = null;
		public String mongoPwd = null;
		public String mongoUser = null;
		public List<ListenerConfig> listenerConfigs = Collections.emptyList();
		public BackendType backendType = null;
		public URL backendURL = null;
		public String backendUser = null;
		public String backendToken = null;
		public String backendContiner = null;
		public Region backendRegion = null;
		public boolean backendTrustAllCerts = false;
		public URL handleServiceURL = null;
		public String handleServiceToken = null;
		public boolean ignoreHandleService = false;
		public URL bytestreamURL = null;
		public String bytestreamUser = null;
		public String bytestreamToken = null;
		public URL sampleServiceURL = null;
		public String sampleServiceToken = null;
		public String tempDir = null;
		public List<String> infoMessages = Collections.emptyList();
		public String paramReport = null;
		public boolean hasErrors = false;
		public List<String> errors = Collections.emptyList();

		public ExpectedConfig withAdminReadOnlyRoles(final Set<String> adminReadOnlyRoles) {
			this.adminReadOnlyRoles = adminReadOnlyRoles;
			return this;
		}

		public ExpectedConfig withAdminRoles(final Set<String> adminRoles) {
			this.adminRoles = adminRoles;
			return this;
		}

		public ExpectedConfig withAuth2URL(final URL auth2url) {
			auth2URL = auth2url;
			return this;
		}

		public ExpectedConfig withAuthURL(final URL authURL) {
			this.authURL = authURL;
			return this;
		}

		public ExpectedConfig withBackendToken(final String backendToken) {
			this.backendToken = backendToken;
			return this;
		}

		public ExpectedConfig withBackendType(final BackendType backendType) {
			this.backendType = backendType;
			return this;
		}

		public ExpectedConfig withBackendURL(final URL backendURL) {
			this.backendURL = backendURL;
			return this;
		}

		public ExpectedConfig withBackendUser(final String backendUser) {
			this.backendUser = backendUser;
			return this;
		}

		public ExpectedConfig withBackendContainer(final String backendContiner) {
			this.backendContiner = backendContiner;
			return this;
		}

		public ExpectedConfig withBackendRegion(final Region backendRegion) {
			this.backendRegion = backendRegion;
			return this;
		}
		
		public ExpectedConfig withBackendTrustAllCerts(final boolean backendTrustAllCerts) {
			this.backendTrustAllCerts = backendTrustAllCerts;
			return this;
		}

		public ExpectedConfig withMongoDBname(final String mongoDBname) {
			this.mongoDBname = mongoDBname;
			return this;
		}

		public ExpectedConfig withErrors(final List<String> errors) {
			this.errors = errors;
			return this;
		}

		public ExpectedConfig withHandleServiceToken(final String handleServiceToken) {
			this.handleServiceToken = handleServiceToken;
			return this;
		}

		public ExpectedConfig withHandleServiceURL(final URL handleServiceURL) {
			this.handleServiceURL = handleServiceURL;
			return this;
		}

		public ExpectedConfig withMongohost(final String mongohost) {
			this.mongohost = mongohost;
			return this;
		}

		public ExpectedConfig withInfoMessages(final List<String> infoMessages) {
			this.infoMessages = infoMessages;
			return this;
		}

		public ExpectedConfig withListenerConfigs(final List<ListenerConfig> listenerConfigs) {
			this.listenerConfigs = listenerConfigs;
			return this;
		}

		public ExpectedConfig withMongoPwd(final String mongoPwd) {
			this.mongoPwd = mongoPwd;
			return this;
		}

		public ExpectedConfig withMongoUser(final String mongoUser) {
			this.mongoUser = mongoUser;
			return this;
		}

		public ExpectedConfig withParamReport(final String paramReport) {
			this.paramReport = paramReport;
			return this;
		}

		public ExpectedConfig withBytestreamToken(final String bytestreamToken) {
			this.bytestreamToken = bytestreamToken;
			return this;
		}

		public ExpectedConfig withBytestreamURL(final URL bytestreamURL) {
			this.bytestreamURL = bytestreamURL;
			return this;
		}

		public ExpectedConfig withBytestreamUser(final String bytestreamUser) {
			this.bytestreamUser = bytestreamUser;
			return this;
		}

		public ExpectedConfig withSampleServiceToken(final String sampleServiceToken) {
			this.sampleServiceToken = sampleServiceToken;
			return this;
		}

		public ExpectedConfig withSampleServiceURL(final URL sampleServiceURL) {
			this.sampleServiceURL = sampleServiceURL;
			return this;
		}

		public ExpectedConfig withTempDir(final String tempDir) {
			this.tempDir = tempDir;
			return this;
		}

		public ExpectedConfig withTypeDBname(final String typeDBname) {
			this.typeDBname = typeDBname;
			return this;
		}

		public ExpectedConfig withWorkspaceAdmin(final String workspaceAdmin) {
			this.workspaceAdmin = workspaceAdmin;
			return this;
		}

		public ExpectedConfig withHasErrors(final boolean hasErrors) {
			this.hasErrors = hasErrors;
			return this;
		}

		public ExpectedConfig withIgnoreHandleService(final boolean ignoreHandleService) {
			this.ignoreHandleService = ignoreHandleService;
			return this;
		}
	}
	
	private void assertConfigCorrect(final Map<String, String> cfg, final ExpectedConfig exp)
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
					kwc.getAdminReadOnlyRoles(), is(exp.adminReadOnlyRoles));
			assertThat("incorrect admin roles", kwc.getAdminRoles(), is(exp.adminRoles));
			assertThat("incorrect auth2 url", kwc.getAuth2URL(), is(exp.auth2URL));
			assertThat("incorrect auth url", kwc.getAuthURL(), is(exp.authURL));
			assertThat("incorrect backend token", kwc.getBackendToken(), is(exp.backendToken));
			assertThat("incorrect backend type", kwc.getBackendType(), is(exp.backendType));
			assertThat("incorrect backend url", kwc.getBackendURL(), is(exp.backendURL));
			assertThat("incorrect backend user", kwc.getBackendUser(), is(exp.backendUser));
			assertThat("incorrect backend container",
					kwc.getBackendContainer(), is(exp.backendContiner));
			assertThat("incorrect backend region", kwc.getBackendRegion(), is(exp.backendRegion));
			assertThat("incorrect backend trust certs",
					kwc.getBackendTrustAllCerts(), is(exp.backendTrustAllCerts));
			assertThat("incorrect db", kwc.getDBname(), is(exp.mongoDBname));
			assertThat("incorrect errors", kwc.getErrors(), is(exp.errors));
			assertThat("incorrect srvc token",
					kwc.getHandleServiceToken(), is(exp.handleServiceToken));
			assertThat("incorrect srvc url", kwc.getHandleServiceURL(), is(exp.handleServiceURL));
			assertThat("incorrect host", kwc.getHost(), is(exp.mongohost));
			assertThat("incorrect info msgs", kwc.getInfoMessages(), is(exp.infoMessages));
			assertThat("incorrect listeners", kwc.getListenerConfigs(), is(exp.listenerConfigs));
			assertThat("incorrect mongo pwd", kwc.getMongoPassword(), is(exp.mongoPwd));
			assertThat("incorrect mongo user", kwc.getMongoUser(), is(exp.mongoUser));
			assertThat("incorrect param report", kwc.getParamReport(), is(exp.paramReport));
			assertThat("incorrect bytestream token",
					kwc.getBytestreamToken(), is(exp.bytestreamToken));
			assertThat("incorrect bytestream url", kwc.getBytestreamURL(), is(exp.bytestreamURL));
			assertThat("incorrect bytestream user",
					kwc.getBytestreamUser(), is(exp.bytestreamUser));
			assertThat("incorrect sample token",
					kwc.getSampleServiceToken(), is(exp.sampleServiceToken));
			assertThat("incorrect sample url", kwc.getSampleServiceURL(), is(exp.sampleServiceURL));
			assertThat("incorrect temp dir", kwc.getTempDir(), is(exp.tempDir));
			assertThat("incorrect type db", kwc.getTypeDBName(), is(exp.typeDBname));
			assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), is(exp.workspaceAdmin));
			assertThat("incorrect has err", kwc.hasErrors(), is(exp.hasErrors));
			assertThat("incorrect ignore hs",
					kwc.ignoreHandleService(), is(exp.ignoreHandleService));
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
				cfg,
				new ExpectedConfig()
						.withAuth2URL(new URL(CI_SERV + "auth"))
						.withAuthURL(new URL(AUTH_LEGACY_URL))
						.withMongohost("somehost")
						.withMongoDBname("somedb")
						.withTypeDBname("typedb")
						.withBackendType(BackendType.GridFS)
						.withInfoMessages(Arrays.asList(IGNORE_HANDLE))
						.withParamReport(paramReport)
						.withTempDir("temp")
						.withIgnoreHandleService(true)
				);
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
				.with("backend-trust-all-ssl-certificates", "     true    ")
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
				"backend-trust-all-ssl-certificates=true\n" +
				"handle-service-url=" + CI_SERV + "handle_service\n" +
				"bytestream-url=" + CI_SERV + "shock-api2\n" +
				"bytestream-user=otheruser\n" +
				"sample-service-url=" + CI_SERV +"sample_service2\n" +
				"mongodb-pwd=[redacted for your safety and comfort]\n" +
				"listeners=us.kbase.MyListener,us.kbase.MyListener2\n";
		
		assertConfigCorrect(
				cfg,
				new ExpectedConfig()
						.withAuth2URL(new URL(CI_SERV + "auth"))
						.withAuthURL(new URL(AUTH_LEGACY_URL))
						.withAdminReadOnlyRoles(set("role1", "role2"))
						.withAdminRoles(set("role3", "role4"))
						.withWorkspaceAdmin("wsadminuser")
						.withMongohost("somehost")
						.withMongoDBname("somedb")
						.withTypeDBname("typedb")
						.withMongoUser("muser")
						.withMongoPwd("mpwd")
						.withListenerConfigs(Arrays.asList(
								new ListenerConfig("us.kbase.MyListener",
										ImmutableMap.of("key1", "value1", "key2", "value2")),
								new ListenerConfig("us.kbase.MyListener2",
										ImmutableMap.of("key1", "value3"))))
						.withBackendType(BackendType.S3)
						.withBackendURL(new URL("http://localhost:34567"))
						.withBackendUser("someuser")
						.withBackendToken("token token token")
						.withBackendContainer("mahbukkit")
						.withBackendRegion(Region.of("a-lovely-region"))
						.withBackendTrustAllCerts(true)
						.withHandleServiceURL(new URL(CI_SERV + "handle_service"))
						.withHandleServiceToken("hstoken")
						.withBytestreamURL(new URL(CI_SERV + "shock-api2"))
						.withBytestreamUser("otheruser")
						.withBytestreamToken("token token")
						.withSampleServiceURL(new URL(CI_SERV + "sample_service2"))
						.withSampleServiceToken("sstoken2")
						.withTempDir("temp")
						.withParamReport(paramReport)
				);
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
				cfg,
				new ExpectedConfig()
						.withAuth2URL(new URL(CI_SERV + "auth"))
						.withAuthURL(new URL(AUTH_LEGACY_URL))
						.withMongohost("somehost")
						.withMongoDBname("somedb")
						.withTypeDBname("typedb")
						.withBackendType(BackendType.GridFS)
						.withHandleServiceURL(new URL(CI_SERV + "handle_service"))
						.withHandleServiceToken("hmtoken")
						.withSampleServiceURL(new URL(CI_SERV + "sample_service3"))
						.withSampleServiceToken("sstoken3")
						.withParamReport(paramReport)
						.withTempDir("temp")
				);
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
				cfg,
				new ExpectedConfig()
						.withParamReport(paramReport)
						.withSampleServiceURL(new URL("https://foo.com"))
						.withHasErrors(true)
						.withErrors(errors)
				);
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
				cfg,
				new ExpectedConfig()
						.withParamReport(paramReport)
						.withSampleServiceURL(new URL("https://foo2.com"))
						.withHasErrors(true)
						.withErrors(errors)
				);
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
				cfg,
				new ExpectedConfig()
						.withMongohost("somehost")
						.withMongoDBname("somedb")
						.withTypeDBname("typedb")
						.withBackendType(BackendType.S3)
						.withBackendUser("buser")
						.withBackendToken("bet")
						.withBackendContainer("foo")
						.withBackendRegion(Region.of("over there"))
						.withHandleServiceToken("hmtoken")
						.withParamReport(paramReport)
						.withTempDir("temp")
						.withHasErrors(true)
						.withErrors(errors)
				);
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
				cfg,
				new ExpectedConfig()
						.withAuth2URL(new URL(CI_SERV + "auth"))
						.withAuthURL(new URL(AUTH_LEGACY_URL))
						.withMongohost("somehost")
						.withMongoDBname("somedb")
						.withTypeDBname("somedb")
						.withBackendType(BackendType.GridFS)
						.withIgnoreHandleService(true)
						.withParamReport(paramReport)
						.withTempDir("temp")
						.withInfoMessages(Arrays.asList(IGNORE_HANDLE))
						.withHasErrors(true)
						.withErrors(errors)
				);
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
				cfg,
				new ExpectedConfig()
						.withAuth2URL(new URL(CI_SERV + "auth"))
						.withAuthURL(new URL(AUTH_LEGACY_URL))
						.withMongohost("somehost")
						.withMongoDBname("somedb")
						.withTypeDBname("typedb")
						.withIgnoreHandleService(true)
						.withParamReport(paramReport)
						.withTempDir("temp")
						.withInfoMessages(Arrays.asList(IGNORE_HANDLE))
						.withHasErrors(true)
						.withErrors(errors)
				);
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
				cfg,
				new ExpectedConfig()
						.withAuth2URL(new URL(CI_SERV + "auth"))
						.withAuthURL(new URL(AUTH_LEGACY_URL))
						.withMongohost("somehost")
						.withMongoDBname("somedb")
						.withTypeDBname("typedb")
						.withBackendType(BackendType.GridFS)
						.withIgnoreHandleService(true)
						.withBytestreamURL(new URL("https://foo.com"))
						.withBytestreamUser(bytestreamUser)
						.withBytestreamToken(bytestreamToken)
						.withParamReport(paramReport)
						.withTempDir("temp")
						.withInfoMessages(Arrays.asList(IGNORE_HANDLE))
						.withHasErrors(true)
						.withErrors(errors)
				);
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
				cfg,
				new ExpectedConfig()
						.withAuth2URL(new URL(CI_SERV + "auth"))
						.withAuthURL(new URL(AUTH_LEGACY_URL))
						.withMongohost("somehost")
						.withMongoDBname("somedb")
						.withTypeDBname("typedb")
						.withBackendType(BackendType.S3)
						.withIgnoreHandleService(true)
						.withParamReport(paramReport)
						.withTempDir("temp")
						.withInfoMessages(Arrays.asList(IGNORE_HANDLE))
						.withHasErrors(true)
						.withErrors(errors)
				);
	}
}
