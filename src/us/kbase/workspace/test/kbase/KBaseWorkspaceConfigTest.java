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
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(MT));
		assertThat("incorrect mngr token", kwc.getHandleManagerToken(), nullValue());
		assertThat("incorrect mngr url", kwc.getHandleManagerURL(), nullValue());
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(), nullValue());
		assertThat("incorrect host", kwc.getHost(), is("somehost"));
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Arrays.asList(IGNORE_HANDLE)));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(MT));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
		assertThat("incorrect temp dir", kwc.getTempDir(), is("temp"));
		assertThat("incorrect type db", kwc.getTypeDBName(), is("typedb"));
		assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), nullValue());
		assertThat("incorrect has err", kwc.hasErrors(), is(false));
		assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(true));
	}
	
	@Test
	public void configMaximal() throws Exception {
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
				.with("handle-manager-token", "    hmtoken    ")
				.with("handle-manager-url", "    " + CI_SERV + "handle_mngr     ")
				.with("handle-service-url", "     " + CI_SERV + "handle_service    ")
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
				"handle-service-url=" + CI_SERV + "handle_service\n" +
				"handle-manager-url=" + CI_SERV + "handle_mngr\n" +
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
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(MT));
		assertThat("incorrect mngr token", kwc.getHandleManagerToken(), is("hmtoken"));
		assertThat("incorrect mngr url", kwc.getHandleManagerURL(),
				is(new URL(CI_SERV + "handle_mngr")));
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
				.with("handle-manager-token", "hmtoken")
				.with("handle-manager-url", CI_SERV + "handle_mngr")
				.with("handle-service-url", CI_SERV + "handle_service")
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
				"handle-manager-url=" + CI_SERV + "handle_mngr\n";
		
		final KBaseWorkspaceConfig kwc = new KBaseWorkspaceConfig(cfg);
		
		assertThat("incorrect admin read roles", kwc.getAdminReadOnlyRoles(), is(set()));
		assertThat("incorrect admin roles", kwc.getAdminRoles(), is(set()));
		assertThat("incorrect auth2 url", kwc.getAuth2URL(), is(new URL(CI_SERV + "auth")));
		assertThat("incorrect auth url", kwc.getAuthURL(), is(new URL(AUTH_LEGACY_URL)));
		assertThat("incorrect backend token", kwc.getBackendToken(), nullValue());
		assertThat("incorrect backend type", kwc.getBackendType(), is(BackendType.GridFS));
		assertThat("incorrect backend url", kwc.getBackendURL(), nullValue());
		assertThat("incorrect backend user", kwc.getBackendUser(), nullValue());
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(MT));
		assertThat("incorrect mngr token", kwc.getHandleManagerToken(), is("hmtoken"));
		assertThat("incorrect mngr url", kwc.getHandleManagerURL(),
				is(new URL(CI_SERV + "handle_mngr")));
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(),
				is(new URL(CI_SERV + "handle_service")));
		assertThat("incorrect host", kwc.getHost(), is("somehost"));
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Collections.emptyList()));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Collections.emptyList()));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
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
				.with("handle-manager-url", null)
				.with("handle-service-url", null)
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
				"Must provide both mongodb-user and mongodb-pwd params in config file if " +
						"MongoDB authentication is to be used",
				String.format(MISSING_PARAM, "handle-service-url"),
				String.format(MISSING_PARAM, "handle-manager-url"),
				String.format(MISSING_PARAM, "handle-manager-token"),
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
		assertThat("incorrect mngr token", kwc.getHandleManagerToken(), nullValue());
		assertThat("incorrect backend type", kwc.getBackendType(), nullValue());
		assertThat("incorrect backend url", kwc.getBackendURL(), nullValue());
		assertThat("incorrect backend user", kwc.getBackendUser(), nullValue());
		assertThat("incorrect mngr url", kwc.getHandleManagerURL(), nullValue());
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(), nullValue());
		assertThat("incorrect host", kwc.getHost(), nullValue());
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Collections.emptyList()));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Collections.emptyList()));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(), is("mongodb-user=user\n"));
		assertThat("incorrect temp dir", kwc.getTempDir(), nullValue());
		assertThat("incorrect type db", kwc.getTypeDBName(), nullValue());
		assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), nullValue());
		assertThat("incorrect has err", kwc.hasErrors(), is(true));
		assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(false));
	}
	
	//TODO NOW Manual init tests
	//TODO NOW Documentation incl release notes (admin notes)
	//TODO NOW deploy template
	
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
				.with("handle-manager-url", "   \t    ")
				.with("handle-service-url", "   \t    ")
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
				"Must provide both mongodb-user and mongodb-pwd params in config file if " +
						"MongoDB authentication is to be used",
				String.format(MISSING_PARAM, "handle-service-url"),
				String.format(MISSING_PARAM, "handle-manager-url"),
				String.format(MISSING_PARAM, "handle-manager-token"),
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
		assertThat("incorrect db", kwc.getDBname(), nullValue());
		assertThat("incorrect errors", kwc.getErrors(), is(errors));
		assertThat("incorrect mngr token", kwc.getHandleManagerToken(), nullValue());
		assertThat("incorrect mngr url", kwc.getHandleManagerURL(), nullValue());
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(), nullValue());
		assertThat("incorrect host", kwc.getHost(), nullValue());
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Collections.emptyList()));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Collections.emptyList()));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(), is(""));
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
				.with("temp-dir", "   temp   ")
				.with("auth-service-url", "   crappy ass url   ")
				.with("auth2-service-url", "   crappy ass url2   ")
				.with("handle-manager-token", "    hmtoken    ")
				.with("handle-manager-url", "   crappy ass url3   ")
				.with("handle-service-url", "   crappy ass url4   ")
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
				"handle-service-url=crappy ass url4\n" +
				"handle-manager-url=crappy ass url3\n";
		
		final String err = "Invalid url for parameter %s: crappy ass url%s";
		
		final List<String> errors = Arrays.asList(
				String.format(err, "auth-service-url", ""),
				String.format(err, "auth2-service-url", "2"),
				String.format(err, "backend-url", " for backend"),
				String.format(err, "handle-service-url", "4"),
				String.format(err, "handle-manager-url", "3"));
		
		final KBaseWorkspaceConfig kwc = new KBaseWorkspaceConfig(cfg);
		
		assertThat("incorrect admin read roles", kwc.getAdminReadOnlyRoles(), is(set()));
		assertThat("incorrect admin roles", kwc.getAdminRoles(), is(set()));
		assertThat("incorrect auth2 url", kwc.getAuth2URL(), nullValue());
		assertThat("incorrect auth url", kwc.getAuthURL(), nullValue());
		assertThat("incorrect backend token", kwc.getBackendToken(), is("bet"));
		assertThat("incorrect backend type", kwc.getBackendType(), is(BackendType.Shock));
		assertThat("incorrect backend url", kwc.getBackendURL(), nullValue());
		assertThat("incorrect backend user", kwc.getBackendUser(), is("buser"));
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(errors));
		assertThat("incorrect mngr token", kwc.getHandleManagerToken(), is("hmtoken"));
		assertThat("incorrect mngr url", kwc.getHandleManagerURL(), nullValue());
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(), nullValue());
		assertThat("incorrect host", kwc.getHost(), is("somehost"));
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Collections.emptyList()));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Collections.emptyList()));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
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
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(errors));
		assertThat("incorrect mngr token", kwc.getHandleManagerToken(), nullValue());
		assertThat("incorrect mngr url", kwc.getHandleManagerURL(), nullValue());
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(), nullValue());
		assertThat("incorrect host", kwc.getHost(), is("somehost"));
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Arrays.asList(IGNORE_HANDLE)));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Collections.emptyList()));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
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
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(errors));
		assertThat("incorrect mngr token", kwc.getHandleManagerToken(), nullValue());
		assertThat("incorrect mngr url", kwc.getHandleManagerURL(), nullValue());
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(), nullValue());
		assertThat("incorrect host", kwc.getHost(), is("somehost"));
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Arrays.asList(IGNORE_HANDLE)));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Collections.emptyList()));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
		assertThat("incorrect temp dir", kwc.getTempDir(), is("temp"));
		assertThat("incorrect type db", kwc.getTypeDBName(), is("typedb"));
		assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), nullValue());
		assertThat("incorrect has err", kwc.hasErrors(), is(true));
		assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(true));
	}
	
	@Test
	public void configFailShockParamsNull() throws Exception {
		final Map<String, String> cfg = MapBuilder.<String, String>newHashMap()
				.with("mongodb-host", "    somehost    ")
				.with("mongodb-database", "    somedb   ")
				.with("mongodb-type-database", "    typedb   ")
				.with("backend-type", "Shock")
				.with("backend-token", null)
				.with("backend-user", null)
				.with("backend-url", null)
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
				"backend-type=Shock\n";
		
		final String err = "Must provide Shock param %s in config file";
		
		final List<String> errors = Arrays.asList(
				String.format(err, "backend-token"),
				String.format(err, "backend-url"),
				String.format(err, "backend-user"));
		
		final KBaseWorkspaceConfig kwc = new KBaseWorkspaceConfig(cfg);
		
		assertThat("incorrect admin read roles", kwc.getAdminReadOnlyRoles(), is(set()));
		assertThat("incorrect admin roles", kwc.getAdminRoles(), is(set()));
		assertThat("incorrect auth2 url", kwc.getAuth2URL(), is(new URL(CI_SERV + "auth")));
		assertThat("incorrect auth url", kwc.getAuthURL(), is(new URL(AUTH_LEGACY_URL)));
		assertThat("incorrect backend token", kwc.getBackendToken(), nullValue());
		assertThat("incorrect backend type", kwc.getBackendType(), is(BackendType.Shock));
		assertThat("incorrect backend url", kwc.getBackendURL(), nullValue());
		assertThat("incorrect backend user", kwc.getBackendUser(), nullValue());
		assertThat("incorrect db", kwc.getDBname(), is("somedb"));
		assertThat("incorrect errors", kwc.getErrors(), is(errors));
		assertThat("incorrect mngr token", kwc.getHandleManagerToken(), nullValue());
		assertThat("incorrect mngr url", kwc.getHandleManagerURL(), nullValue());
		assertThat("incorrect srvc url", kwc.getHandleServiceURL(), nullValue());
		assertThat("incorrect host", kwc.getHost(), is("somehost"));
		assertThat("incorrect info msgs", kwc.getInfoMessages(), is(Arrays.asList(IGNORE_HANDLE)));
		assertThat("incorrect listeners", kwc.getListenerConfigs(), is(Collections.emptyList()));
		assertThat("incorrect mongo pwd", kwc.getMongoPassword(), nullValue());
		assertThat("incorrect mongo user", kwc.getMongoUser(), nullValue());
		assertThat("incorrect param report", kwc.getParamReport(), is(paramReport));
		assertThat("incorrect temp dir", kwc.getTempDir(), is("temp"));
		assertThat("incorrect type db", kwc.getTypeDBName(), is("typedb"));
		assertThat("incorrect ws admin", kwc.getWorkspaceAdmin(), nullValue());
		assertThat("incorrect has err", kwc.hasErrors(), is(true));
		assertThat("incorrect ignore hs", kwc.ignoreHandleService(), is(true));
	}

}
