package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.set;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import nl.jqno.equalsverifier.EqualsVerifier;
import software.amazon.awssdk.regions.Region;
import us.kbase.common.test.MapBuilder;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.kbase.BackendType;
import us.kbase.workspace.kbase.KBaseWorkspaceConfig;
import us.kbase.workspace.kbase.KBaseWorkspaceConfig.ListenerConfig;

public class KBaseWorkspaceConfigTest {
	
	private final static String CI_SERV = "https://ci.kbase.us/services/";
	private final static String AUTH_LEGACY_URL =
			CI_SERV + "auth/api/legacy/KBase/Sessions/Login";
	private final static List<String> MT = Collections.emptyList();
	private final static String IGNORE_HANDLE = 
			"Ignoring Handle Service config. Objects with handle IDs will fail typechecking.";
	private final static String MISSING_PARAM = "Must provide param %s in config file";

	@Test
	public void equals() throws Exception {
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
		
		final KBaseWorkspaceConfig kwc = new KBaseWorkspaceConfig(cfg);
		
		assertThat("incorrect admin read roles", kwc.getAdminReadOnlyRoles(), is(set()));
		assertThat("incorrect admin roles", kwc.getAdminRoles(), is(set()));
		assertThat("incorrect auth2 url", kwc.getAuth2URL(), is(new URL(CI_SERV + "auth")));
		assertThat("incorrect auth url", kwc.getAuthURL(), is(new URL(AUTH_LEGACY_URL)));
		assertThat("incorrect backend token", kwc.getBackendToken(), nullValue());
		assertThat("incorrect backend type", kwc.getBackendType(), is(BackendType.GridFS));
		assertThat("incorrect backend url", kwc.getBackendURL(), nullValue());
		assertThat("incorrect backend user", kwc.getBackendUser(), nullValue());
		assertThat("incorrect backend container", kwc.getBackendContainer(), nullValue());
		assertThat("incorrect backend region", kwc.getBackendRegion(), nullValue());
		assertThat("incorrect backend trust certs", kwc.getBackendTrustAllCerts(), is(false));
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(MT));
		assertThat("incorrect srvc token", kwc.getHandleServiceToken(), nullValue());
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(), nullValue());
		assertThat("incorrect host", kwc.getHost(), is("somehost"));
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Arrays.asList(IGNORE_HANDLE)));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(MT));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
		assertThat("incorrect bytestream token", kwc.getBytestreamToken(), nullValue());
		assertThat("incorrect bytestream url", kwc.getBytestreamURL(), nullValue());
		assertThat("incorrect bytestream user", kwc.getBytestreamUser(), nullValue());
		assertThat("incorrect sample token", kwc.getSampleServiceToken(), nullValue());
		assertThat("incorrect sample url", kwc.getSampleServiceURL(), nullValue());
		assertThat("incorrect temp dir", kwc.getTempDir(), is("temp"));
		assertThat("incorrect type db", kwc.getTypeDBName(), is("typedb"));
		assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), nullValue());
		assertThat("incorrect has err", kwc.hasErrors(), is(false));
		assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(true));
	}
	
	@Test
	public void configMaximalShock() throws Exception {
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
				.with("backend-type", "   Shock   ")
				.with("backend-url", "   " + CI_SERV + "shock-api    ")
				.with("backend-user", "    someuser    ")
				.with("backend-token", "    token token token    ")
				.with("backend-trust-all-ssl-certificates", "    true    ")
				.with("handle-service-token", "    hstoken    ")
				.with("handle-manager-token", "    hmtoken    ")  // test service takes precedence
				.with("handle-service-url", "     " + CI_SERV + "handle_service    ")
				.with("sample-service-url", "     " + CI_SERV + "sample_service    ")
				.with("sample-service-administrator-token", "    sstoken    ")
				.with("bytestream-url", "   " + CI_SERV + "shock-api2    ")
				.with("bytestream-user", "    otheruser    ")
				.with("bytestream-token", "    token token    ")
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
				"backend-type=Shock\n" +
				"backend-url=" + CI_SERV + "shock-api\n" +
				"backend-user=someuser\n" +
				"backend-trust-all-ssl-certificates=true\n" +
				"handle-service-url=" + CI_SERV + "handle_service\n" +
				"bytestream-url=" + CI_SERV + "shock-api2\n" +
				"bytestream-user=otheruser\n" +
				"sample-service-url=" + CI_SERV +"sample_service\n" +
				"mongodb-pwd=[redacted for your safety and comfort]\n" +
				"listeners=us.kbase.MyListener,us.kbase.MyListener2\n";
		
		final KBaseWorkspaceConfig kwc = new KBaseWorkspaceConfig(cfg);
		
		assertThat("incorrect admin read roles", kwc.getAdminReadOnlyRoles(),
				is(set("role1", "role2")));
		assertThat("incorrect admin roles", kwc.getAdminRoles(), is(set("role3", "role4")));
		assertThat("incorrect auth2 url", kwc.getAuth2URL(), is(new URL(CI_SERV + "auth")));
		assertThat("incorrect auth url", kwc.getAuthURL(), is(new URL(AUTH_LEGACY_URL)));
		assertThat("incorrect backend token", kwc.getBackendToken(), is("token token token"));
		assertThat("incorrect backend type", kwc.getBackendType(), is(BackendType.Shock));
		assertThat("incorrect backend url", kwc.getBackendURL(),
				is(new URL(CI_SERV + "shock-api")));
		assertThat("incorrect backend user", kwc.getBackendUser(), is("someuser"));
		assertThat("incorrect backend container", kwc.getBackendContainer(), nullValue());
		assertThat("incorrect backend region", kwc.getBackendRegion(), nullValue());
		assertThat("incorrect backend trust certs", kwc.getBackendTrustAllCerts(), is(true));
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(MT));
		assertThat("incorrect srvc token", kwc.getHandleServiceToken(), is("hstoken"));
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(),
				is(new URL(CI_SERV + "handle_service")));
		assertThat("incorrect host", kwc.getHost(), is("somehost"));
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Collections.emptyList()));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Arrays.asList(
				new ListenerConfig("us.kbase.MyListener",
						ImmutableMap.of("key1", "value1", "key2", "value2")),
				new ListenerConfig("us.kbase.MyListener2", ImmutableMap.of("key1", "value3")))));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), is("mpwd"));
		assertThat("incorrect mongo user", kwc.getMongoUser(), is("muser"));
		assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
		assertThat("incorrect bytestream token", kwc.getBytestreamToken(), is("token token"));
		assertThat("incorrect bytestream url", kwc.getBytestreamURL(),
				is(new URL(CI_SERV + "shock-api2")));
		assertThat("incorrect bytestream user", kwc.getBytestreamUser(), is("otheruser"));
		assertThat("incorrect sample token", kwc.getSampleServiceToken(), is("sstoken"));
		assertThat("incorrect sample url", kwc.getSampleServiceURL(),
				is(new URL(CI_SERV + "sample_service")));
		assertThat("incorrect temp dir", kwc.getTempDir(), is("temp"));
		assertThat("incorrect type db", kwc.getTypeDBName(), is("typedb"));
		assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), is("wsadminuser"));
		assertThat("incorrect has err", kwc.hasErrors(), is(false));
		assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(false));
	}
	
	@Test
	public void configMaximalS3() throws Exception {
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
				.with("handle-manager-token", "    hmtoken    ")
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
		
		final KBaseWorkspaceConfig kwc = new KBaseWorkspaceConfig(cfg);
		
		assertThat("incorrect admin read roles", kwc.getAdminReadOnlyRoles(),
				is(set("role1", "role2")));
		assertThat("incorrect admin roles", kwc.getAdminRoles(), is(set("role3", "role4")));
		assertThat("incorrect auth2 url", kwc.getAuth2URL(), is(new URL(CI_SERV + "auth")));
		assertThat("incorrect auth url", kwc.getAuthURL(), is(new URL(AUTH_LEGACY_URL)));
		assertThat("incorrect backend token", kwc.getBackendToken(), is("token token token"));
		assertThat("incorrect backend type", kwc.getBackendType(), is(BackendType.S3));
		assertThat("incorrect backend url", kwc.getBackendURL(),
				is(new URL("http://localhost:34567")));
		assertThat("incorrect backend user", kwc.getBackendUser(), is("someuser"));
		assertThat("incorrect backend container", kwc.getBackendContainer(), is("mahbukkit"));
		assertThat("incorrect backend region", kwc.getBackendRegion(),
				is(Region.of("a-lovely-region")));
		assertThat("incorrect backend trust certs", kwc.getBackendTrustAllCerts(), is(false));
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(MT));
		assertThat("incorrect srvc token", kwc.getHandleServiceToken(), is("hmtoken"));
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(),
				is(new URL(CI_SERV + "handle_service")));
		assertThat("incorrect host", kwc.getHost(), is("somehost"));
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Collections.emptyList()));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Arrays.asList(
				new ListenerConfig("us.kbase.MyListener",
						ImmutableMap.of("key1", "value1", "key2", "value2")),
				new ListenerConfig("us.kbase.MyListener2", ImmutableMap.of("key1", "value3")))));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), is("mpwd"));
		assertThat("incorrect mongo user", kwc.getMongoUser(), is("muser"));
		assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
		assertThat("incorrect bytestream token", kwc.getBytestreamToken(), is("token token"));
		assertThat("incorrect bytestream url", kwc.getBytestreamURL(),
				is(new URL(CI_SERV + "shock-api2")));
		assertThat("incorrect bytestream user", kwc.getBytestreamUser(), is("otheruser"));
		assertThat("incorrect sample token", kwc.getSampleServiceToken(), is("sstoken2"));
		assertThat("incorrect sample url", kwc.getSampleServiceURL(),
				is(new URL(CI_SERV + "sample_service2")));
		assertThat("incorrect temp dir", kwc.getTempDir(), is("temp"));
		assertThat("incorrect type db", kwc.getTypeDBName(), is("typedb"));
		assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), is("wsadminuser"));
		assertThat("incorrect has err", kwc.hasErrors(), is(false));
		assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(false));
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
				.with("handle-service-token", "hstoken")
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
		
		final KBaseWorkspaceConfig kwc = new KBaseWorkspaceConfig(cfg);
		
		assertThat("incorrect admin read roles", kwc.getAdminReadOnlyRoles(), is(set()));
		assertThat("incorrect admin roles", kwc.getAdminRoles(), is(set()));
		assertThat("incorrect auth2 url", kwc.getAuth2URL(), is(new URL(CI_SERV + "auth")));
		assertThat("incorrect auth url", kwc.getAuthURL(), is(new URL(AUTH_LEGACY_URL)));
		assertThat("incorrect backend token", kwc.getBackendToken(), nullValue());
		assertThat("incorrect backend type", kwc.getBackendType(), is(BackendType.GridFS));
		assertThat("incorrect backend url", kwc.getBackendURL(), nullValue());
		assertThat("incorrect backend user", kwc.getBackendUser(), nullValue());
		assertThat("incorrect backend container", kwc.getBackendContainer(), nullValue());
		assertThat("incorrect backend region", kwc.getBackendRegion(), nullValue());
		assertThat("incorrect backend trust certs", kwc.getBackendTrustAllCerts(), is(false));
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(MT));
		assertThat("incorrect srvc token", kwc.getHandleServiceToken(), is("hstoken"));
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(),
				is(new URL(CI_SERV + "handle_service")));
		assertThat("incorrect host", kwc.getHost(), is("somehost"));
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Collections.emptyList()));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Collections.emptyList()));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
		assertThat("incorrect bytestream token", kwc.getBytestreamToken(), nullValue());
		assertThat("incorrect bytestream url", kwc.getBytestreamURL(), nullValue());
		assertThat("incorrect bytestream user", kwc.getBytestreamUser(), nullValue());
		assertThat("incorrect sample token", kwc.getSampleServiceToken(), is("sstoken3"));
		assertThat("incorrect sample url", kwc.getSampleServiceURL(),
				is(new URL(CI_SERV + "sample_service3")));
		assertThat("incorrect temp dir", kwc.getTempDir(), is("temp"));
		assertThat("incorrect type db", kwc.getTypeDBName(), is("typedb"));
		assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), nullValue());
		assertThat("incorrect has err", kwc.hasErrors(), is(false));
		assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(false));
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
	public void constructFailNullMap() throws Exception {
		try {
			new KBaseWorkspaceConfig(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("config"));
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
		
		final KBaseWorkspaceConfig kwc = new KBaseWorkspaceConfig(cfg);
		
		assertThat("incorrect admin read roles", kwc.getAdminReadOnlyRoles(), is(set()));
		assertThat("incorrect admin roles", kwc.getAdminRoles(), is(set()));
		assertThat("incorrect auth2 url", kwc.getAuth2URL(), nullValue());
		assertThat("incorrect auth url", kwc.getAuthURL(), nullValue());
		assertThat("incorrect backend token", kwc.getBackendToken(), nullValue());
		assertThat("incorrect db", kwc.getDBname(), nullValue());
		assertThat("incorrect errors", kwc.getErrors(), is(errors));
		assertThat("incorrect srvc token", kwc.getHandleServiceToken(), nullValue());
		assertThat("incorrect backend type", kwc.getBackendType(), nullValue());
		assertThat("incorrect backend url", kwc.getBackendURL(), nullValue());
		assertThat("incorrect backend user", kwc.getBackendUser(), nullValue());
		assertThat("incorrect backend container", kwc.getBackendContainer(), nullValue());
		assertThat("incorrect backend region", kwc.getBackendRegion(), nullValue());
		assertThat("incorrect backend trust certs", kwc.getBackendTrustAllCerts(), is(false));
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(), nullValue());
		assertThat("incorrect host", kwc.getHost(), nullValue());
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Collections.emptyList()));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Collections.emptyList()));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(),
				is("mongodb-user=user\nsample-service-url=https://foo.com\n"));
		assertThat("incorrect bytestream token", kwc.getBytestreamToken(), nullValue());
		assertThat("incorrect bytestream url", kwc.getBytestreamURL(), nullValue());
		assertThat("incorrect bytestream user", kwc.getBytestreamUser(), nullValue());
		assertThat("incorrect sample token", kwc.getSampleServiceToken(), nullValue());
		assertThat("incorrect sample url", kwc.getSampleServiceURL(),
				is(new URL("https://foo.com")));
		assertThat("incorrect temp dir", kwc.getTempDir(), nullValue());
		assertThat("incorrect type db", kwc.getTypeDBName(), nullValue());
		assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), nullValue());
		assertThat("incorrect has err", kwc.hasErrors(), is(true));
		assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(false));
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
				"Invalid listener configuration item: listener-listener2-config-    \t   ");
		
		final KBaseWorkspaceConfig kwc = new KBaseWorkspaceConfig(cfg);
		
		assertThat("incorrect admin read roles", kwc.getAdminReadOnlyRoles(), is(set()));
		assertThat("incorrect admin roles", kwc.getAdminRoles(), is(set()));
		assertThat("incorrect auth2 url", kwc.getAuth2URL(), nullValue());
		assertThat("incorrect auth url", kwc.getAuthURL(), nullValue());
		assertThat("incorrect backend token", kwc.getBackendToken(), nullValue());
		assertThat("incorrect backend type", kwc.getBackendType(), nullValue());
		assertThat("incorrect backend url", kwc.getBackendURL(), nullValue());
		assertThat("incorrect backend user", kwc.getBackendUser(), nullValue());
		assertThat("incorrect backend container", kwc.getBackendContainer(), nullValue());
		assertThat("incorrect backend region", kwc.getBackendRegion(), nullValue());
		assertThat("incorrect backend trust certs", kwc.getBackendTrustAllCerts(), is(false));
		assertThat("incorrect db", kwc.getDBname(), nullValue());
		assertThat("incorrect errors", kwc.getErrors(), is(errors));
		assertThat("incorrect srvc token", kwc.getHandleServiceToken(), nullValue());
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(), nullValue());
		assertThat("incorrect host", kwc.getHost(), nullValue());
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Collections.emptyList()));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Collections.emptyList()));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(),
				is("sample-service-url=https://foo2.com\n"));
		assertThat("incorrect bytestream token", kwc.getBytestreamToken(), nullValue());
		assertThat("incorrect bytestream url", kwc.getBytestreamURL(), nullValue());
		assertThat("incorrect bytestream user", kwc.getBytestreamUser(), nullValue());
		assertThat("incorrect sample token", kwc.getSampleServiceToken(), nullValue());
		assertThat("incorrect sample url", kwc.getSampleServiceURL(),
				is(new URL("https://foo2.com")));
		assertThat("incorrect temp dir", kwc.getTempDir(), nullValue());
		assertThat("incorrect type db", kwc.getTypeDBName(), nullValue());
		assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), nullValue());
		assertThat("incorrect has err", kwc.hasErrors(), is(true));
		assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(false));
	}
	
	@Test
	public void configFailBadURLs() throws Exception {
		final Map<String, String> cfg = MapBuilder.<String, String>newHashMap()
				.with("mongodb-host", "    somehost    ")
				.with("mongodb-database", "    somedb   ")
				.with("mongodb-type-database", "    typedb   ")
				.with("backend-type", "Shock")
				.with("backend-token", "   bet   ")
				.with("backend-user", "   buser  ")
				.with("backend-url", "    crappy ass url for backend   ")
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
				"backend-type=Shock\n" +
				"backend-url=crappy ass url for backend\n" +
				"backend-user=buser\n" +
				"handle-service-url=crappy ass url4\n";
		
		final String err = "Invalid url for parameter %s: crappy ass url%s";
		
		final List<String> errors = Arrays.asList(
				String.format(err, "auth-service-url", ""),
				String.format(err, "auth2-service-url", "2"),
				String.format(err, "backend-url", " for backend"),
				String.format(err, "bytestream-url", " for shock"),
				String.format(err, "sample-service-url", "5"),
				String.format(err, "handle-service-url", "4"));
		
		final KBaseWorkspaceConfig kwc = new KBaseWorkspaceConfig(cfg);
		
		assertThat("incorrect admin read roles", kwc.getAdminReadOnlyRoles(), is(set()));
		assertThat("incorrect admin roles", kwc.getAdminRoles(), is(set()));
		assertThat("incorrect auth2 url", kwc.getAuth2URL(), nullValue());
		assertThat("incorrect auth url", kwc.getAuthURL(), nullValue());
		assertThat("incorrect backend token", kwc.getBackendToken(), is("bet"));
		assertThat("incorrect backend type", kwc.getBackendType(), is(BackendType.Shock));
		assertThat("incorrect backend url", kwc.getBackendURL(), nullValue());
		assertThat("incorrect backend user", kwc.getBackendUser(), is("buser"));
		assertThat("incorrect backend container", kwc.getBackendContainer(), nullValue());
		assertThat("incorrect backend region", kwc.getBackendRegion(), nullValue());
		assertThat("incorrect backend trust certs", kwc.getBackendTrustAllCerts(), is(false));
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(errors));
		assertThat("incorrect srvc token", kwc.getHandleServiceToken(), is("hmtoken"));
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(), nullValue());
		assertThat("incorrect host", kwc.getHost(), is("somehost"));
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Collections.emptyList()));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Collections.emptyList()));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
		assertThat("incorrect bytestream token", kwc.getBytestreamToken(), nullValue());
		assertThat("incorrect bytestream url", kwc.getBytestreamURL(), nullValue());
		assertThat("incorrect bytestream user", kwc.getBytestreamUser(), nullValue());
		assertThat("incorrect sample token", kwc.getSampleServiceToken(), nullValue());
		assertThat("incorrect sample url", kwc.getSampleServiceURL(), nullValue());
		assertThat("incorrect temp dir", kwc.getTempDir(), is("temp"));
		assertThat("incorrect type db", kwc.getTypeDBName(), is("typedb"));
		assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), nullValue());
		assertThat("incorrect has err", kwc.hasErrors(), is(true));
		assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(false));
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
		
		final KBaseWorkspaceConfig kwc = new KBaseWorkspaceConfig(cfg);
		
		assertThat("incorrect admin read roles", kwc.getAdminReadOnlyRoles(), is(set()));
		assertThat("incorrect admin roles", kwc.getAdminRoles(), is(set()));
		assertThat("incorrect auth2 url", kwc.getAuth2URL(), is(new URL(CI_SERV + "auth")));
		assertThat("incorrect auth url", kwc.getAuthURL(), is(new URL(AUTH_LEGACY_URL)));
		assertThat("incorrect backend token", kwc.getBackendToken(), nullValue());
		assertThat("incorrect backend type", kwc.getBackendType(), is(BackendType.GridFS));
		assertThat("incorrect backend url", kwc.getBackendURL(), nullValue());
		assertThat("incorrect backend user", kwc.getBackendUser(), nullValue());
		assertThat("incorrect backend container", kwc.getBackendContainer(), nullValue());
		assertThat("incorrect backend region", kwc.getBackendRegion(), nullValue());
		assertThat("incorrect backend trust certs", kwc.getBackendTrustAllCerts(), is(false));
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(errors));
		assertThat("incorrect srvc token", kwc.getHandleServiceToken(), nullValue());
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(), nullValue());
		assertThat("incorrect host", kwc.getHost(), is("somehost"));
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Arrays.asList(IGNORE_HANDLE)));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Collections.emptyList()));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
		assertThat("incorrect bytestream token", kwc.getBytestreamToken(), nullValue());
		assertThat("incorrect bytestream url", kwc.getBytestreamURL(), nullValue());
		assertThat("incorrect bytestream user", kwc.getBytestreamUser(), nullValue());
		assertThat("incorrect sample token", kwc.getSampleServiceToken(), nullValue());
		assertThat("incorrect sample url", kwc.getSampleServiceURL(), nullValue());
		assertThat("incorrect temp dir", kwc.getTempDir(), is("temp"));
		assertThat("incorrect type db", kwc.getTypeDBName(), is("somedb"));
		assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), nullValue());
		assertThat("incorrect has err", kwc.hasErrors(), is(true));
		assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(true));
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
		
		final KBaseWorkspaceConfig kwc = new KBaseWorkspaceConfig(cfg);
		
		assertThat("incorrect admin read roles", kwc.getAdminReadOnlyRoles(), is(set()));
		assertThat("incorrect admin roles", kwc.getAdminRoles(), is(set()));
		assertThat("incorrect auth2 url", kwc.getAuth2URL(), is(new URL(CI_SERV + "auth")));
		assertThat("incorrect auth url", kwc.getAuthURL(), is(new URL(AUTH_LEGACY_URL)));
		assertThat("incorrect backend token", kwc.getBackendToken(), nullValue());
		assertThat("incorrect backend type", kwc.getBackendType(), nullValue());
		assertThat("incorrect backend url", kwc.getBackendURL(), nullValue());
		assertThat("incorrect backend user", kwc.getBackendUser(), nullValue());
		assertThat("incorrect backend container", kwc.getBackendContainer(), nullValue());
		assertThat("incorrect backend region", kwc.getBackendRegion(), nullValue());
		assertThat("incorrect backend trust certs", kwc.getBackendTrustAllCerts(), is(false));
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(errors));
		assertThat("incorrect srvc token", kwc.getHandleServiceToken(), nullValue());
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(), nullValue());
		assertThat("incorrect host", kwc.getHost(), is("somehost"));
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Arrays.asList(IGNORE_HANDLE)));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Collections.emptyList()));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
		assertThat("incorrect bytestream token", kwc.getBytestreamToken(), nullValue());
		assertThat("incorrect bytestream url", kwc.getBytestreamURL(), nullValue());
		assertThat("incorrect bytestream user", kwc.getBytestreamUser(), nullValue());
		assertThat("incorrect sample token", kwc.getSampleServiceToken(), nullValue());
		assertThat("incorrect sample url", kwc.getSampleServiceURL(), nullValue());
		assertThat("incorrect temp dir", kwc.getTempDir(), is("temp"));
		assertThat("incorrect type db", kwc.getTypeDBName(), is("typedb"));
		assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), nullValue());
		assertThat("incorrect has err", kwc.hasErrors(), is(true));
		assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(true));
	}
	
	@Test
	public void configFailShockParamsMissing() throws Exception {
		configFailShockParamsMissing(null, null, "user", "bytestream-user=user\n");
		configFailShockParamsMissing(null, "    \t    ", "user", "bytestream-user=user\n");
		configFailShockParamsMissing("    \t   ", "token", null, "");
		configFailShockParamsMissing("    \t   ", "token", "   \t     ", "");
	}

	private void configFailShockParamsMissing(
			final String backendParam,
			String bytestreamToken,
			String bytestreamUser,
			final String paramReportLast)
			throws Exception {
		final Map<String, String> cfg = MapBuilder.<String, String>newHashMap()
				.with("mongodb-host", "    somehost    ")
				.with("mongodb-database", "    somedb   ")
				.with("mongodb-type-database", "    typedb   ")
				.with("backend-type", "Shock")
				.with("backend-token", backendParam)
				.with("backend-user", backendParam)
				.with("backend-url", backendParam)
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
				"backend-type=Shock\n" +
				"bytestream-url=https://foo.com\n" + 
				paramReportLast;
		
		final String err = "Must provide Shock param %s in config file";
		
		final List<String> errors = Arrays.asList(
				String.format(err, "backend-token"),
				String.format(err, "backend-url"),
				String.format(err, "backend-user"),
				"Must provide bytestream-user and bytestream-token parameters in config file if " +
				"bytestream-url is provided");
		
		final KBaseWorkspaceConfig kwc = new KBaseWorkspaceConfig(cfg);
		
		bytestreamToken = bytestreamToken == null || bytestreamToken.trim().isEmpty() ?
				null : bytestreamToken;
		bytestreamUser = bytestreamUser == null || bytestreamUser.trim().isEmpty() ?
				null : bytestreamUser;
		
		assertThat("incorrect admin read roles", kwc.getAdminReadOnlyRoles(), is(set()));
		assertThat("incorrect admin roles", kwc.getAdminRoles(), is(set()));
		assertThat("incorrect auth2 url", kwc.getAuth2URL(), is(new URL(CI_SERV + "auth")));
		assertThat("incorrect auth url", kwc.getAuthURL(), is(new URL(AUTH_LEGACY_URL)));
		assertThat("incorrect backend token", kwc.getBackendToken(), nullValue());
		assertThat("incorrect backend type", kwc.getBackendType(), is(BackendType.Shock));
		assertThat("incorrect backend url", kwc.getBackendURL(), nullValue());
		assertThat("incorrect backend user", kwc.getBackendUser(), nullValue());
		assertThat("incorrect backend container", kwc.getBackendContainer(), nullValue());
		assertThat("incorrect backend region", kwc.getBackendRegion(), nullValue());
		assertThat("incorrect backend trust certs", kwc.getBackendTrustAllCerts(), is(false));
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(errors));
		assertThat("incorrect srvc token", kwc.getHandleServiceToken(), nullValue());
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(), nullValue());
		assertThat("incorrect host", kwc.getHost(), is("somehost"));
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Arrays.asList(IGNORE_HANDLE)));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Collections.emptyList()));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
		assertThat("incorrect bytestream token", kwc.getBytestreamToken(), is(bytestreamToken));
		assertThat("incorrect bytestream url", kwc.getBytestreamURL(),
				is(new URL("https://foo.com")));
		assertThat("incorrect bytestream user", kwc.getBytestreamUser(), is(bytestreamUser));
		assertThat("incorrect sample token", kwc.getSampleServiceToken(), nullValue());
		assertThat("incorrect sample url", kwc.getSampleServiceURL(), nullValue());
		assertThat("incorrect temp dir", kwc.getTempDir(), is("temp"));
		assertThat("incorrect type db", kwc.getTypeDBName(), is("typedb"));
		assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), nullValue());
		assertThat("incorrect has err", kwc.hasErrors(), is(true));
		assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(true));
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
		
		final KBaseWorkspaceConfig kwc = new KBaseWorkspaceConfig(cfg);
		
		assertThat("incorrect admin read roles", kwc.getAdminReadOnlyRoles(), is(set()));
		assertThat("incorrect admin roles", kwc.getAdminRoles(), is(set()));
		assertThat("incorrect auth2 url", kwc.getAuth2URL(), is(new URL(CI_SERV + "auth")));
		assertThat("incorrect auth url", kwc.getAuthURL(), is(new URL(AUTH_LEGACY_URL)));
		assertThat("incorrect backend token", kwc.getBackendToken(), nullValue());
		assertThat("incorrect backend type", kwc.getBackendType(), is(BackendType.S3));
		assertThat("incorrect backend url", kwc.getBackendURL(), nullValue());
		assertThat("incorrect backend user", kwc.getBackendUser(), nullValue());
		assertThat("incorrect backend container", kwc.getBackendContainer(), nullValue());
		assertThat("incorrect backend region", kwc.getBackendRegion(), nullValue());
		assertThat("incorrect backend trust certs", kwc.getBackendTrustAllCerts(), is(false));
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(errors));
		assertThat("incorrect srvc token", kwc.getHandleServiceToken(), nullValue());
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(), nullValue());
		assertThat("incorrect host", kwc.getHost(), is("somehost"));
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Arrays.asList(IGNORE_HANDLE)));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Collections.emptyList()));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
		assertThat("incorrect bytestream token", kwc.getBytestreamToken(), is(nullValue()));
		assertThat("incorrect bytestream url", kwc.getBytestreamURL(), is(nullValue()));
		assertThat("incorrect bytestream user", kwc.getBytestreamUser(), is(nullValue()));
		assertThat("incorrect sample token", kwc.getSampleServiceToken(), nullValue());
		assertThat("incorrect sample url", kwc.getSampleServiceURL(), nullValue());
		assertThat("incorrect temp dir", kwc.getTempDir(), is("temp"));
		assertThat("incorrect type db", kwc.getTypeDBName(), is("typedb"));
		assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), nullValue());
		assertThat("incorrect has err", kwc.hasErrors(), is(true));
		assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(true));
	}
}
