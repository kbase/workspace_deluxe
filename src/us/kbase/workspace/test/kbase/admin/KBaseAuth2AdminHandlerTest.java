package us.kbase.workspace.test.kbase.admin;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.set;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.junit.Test;

import org.mockito.ArgumentMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import us.kbase.auth.AuthToken;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.kbase.admin.AdminRole;
import us.kbase.workspace.kbase.admin.AdministratorHandlerException;
import us.kbase.workspace.kbase.admin.KBaseAuth2AdminHandler;

public class KBaseAuth2AdminHandlerTest {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	private static class HttpGetMatcher implements ArgumentMatcher<HttpGet> {

		private final URI expectedURI;
		private final List<Map<String, String>> headers;
		
		public HttpGetMatcher(final URI expectedURI, final List<Map<String, String>> headers) {
			this.expectedURI = expectedURI;
			this.headers = headers;
		}
		
		@Override
		public boolean matches(final HttpGet httpGet) {
			if (!httpGet.getURI().equals(expectedURI)) {
				return false;
			}
			if (httpGet.getAllHeaders().length != headers.size()) {
				return false;
			}
			int index = 0;
			for (final Header h: httpGet.getAllHeaders()) {
				final Map<String, String> expected = headers.get(index);
				if (!h.getName().equals(expected.get("name"))) {
					return false;
				}
				if (!h.getValue().equals(expected.get("value"))) {
					return false;
				}
				index++;
			}
			return true;
		}
		
	}
	
	private static class Mocks {
		private final CloseableHttpClient client;
		private final CloseableHttpResponse resp;
		
		private Mocks(CloseableHttpClient client, CloseableHttpResponse resp) {
			this.client = client;
			this.resp = resp;
		}
	}
	
	private Mocks setUpConstructorMocks(final String url) throws Exception {
		final CloseableHttpClient client = mock(CloseableHttpClient.class);
		final CloseableHttpResponse resp = mock(CloseableHttpResponse.class);
		final HttpEntity entity = mock(HttpEntity.class);
		
		when(resp.getEntity()).thenReturn(entity);
		when(entity.getContent()).thenReturn(new ByteArrayInputStream(MAPPER.writeValueAsBytes(
				ImmutableMap.of("gitcommithash", "foo", "version", "bar", "servertime", 1))));
		
		doReturn(resp).when(client).execute(argThat(new HttpGetMatcher(
				new URI(url),
				Arrays.asList(ImmutableMap.of("name", "accept", "value", "application/json")))));
		return new Mocks(client, resp);
	}
	
	@Test
	public void constructWithNoSlash() throws Exception {
		constructWithURL("https://foo.com");
	}
	
	@Test
	public void constructWithSlash() throws Exception {
		constructWithURL("https://foo.com/");
	}

	private void constructWithURL(final String url) throws Exception {
		final Mocks mocks = setUpConstructorMocks("https://foo.com/");

		final KBaseAuth2AdminHandler h = new KBaseAuth2AdminHandler(
				mocks.client,
				new URL(url),
				set("WS_READ", "KBASE_READS"),
				set("WS_ADMIN", "KBASE_ADMIN"));
		
		assertThat("incorrect url", h.getRootAuthURI(), is(new URI("https://foo.com/")));
		assertThat("incorrect read roles", h.getReadOnlyRoles(),
				is(set("WS_READ", "KBASE_READS")));
		assertThat("incorrect read roles", h.getFullAdminRoles(),
				is(set("WS_ADMIN", "KBASE_ADMIN")));
		
		verify(mocks.resp).close();
	}
	
	@Test
	public void immutable() throws Exception {
		final Mocks mocks = setUpConstructorMocks("https://foo.com/");
		
		final Set<String> read = new HashSet<>(set("WS_READ", "KBASE_READS"));
		final Set<String> admin = new HashSet<>(set("WS_ADMIN", "KBASE_ADMIN"));
		
		final KBaseAuth2AdminHandler h = new KBaseAuth2AdminHandler(
				mocks.client,
				new URL("https://foo.com"),
				read,
				admin);
		
		assertThat("incorrect read roles", h.getReadOnlyRoles(),
				is(set("WS_READ", "KBASE_READS")));
		assertThat("incorrect read roles", h.getFullAdminRoles(),
				is(set("WS_ADMIN", "KBASE_ADMIN")));
		
		read.add("foo");
		admin.add("bar");
		
		assertThat("incorrect read roles", h.getReadOnlyRoles(),
				is(set("WS_READ", "KBASE_READS")));
		assertThat("incorrect read roles", h.getFullAdminRoles(),
				is(set("WS_ADMIN", "KBASE_ADMIN")));
		
		try {
			h.getReadOnlyRoles().add("foo");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
		try {
			h.getFullAdminRoles().add("foo");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
	}
	
	@Test
	public void failConstructNulls() throws Exception {
		final CloseableHttpClient c = mock(CloseableHttpClient.class);
		final URL u = new URL("http://foo.com");
		final Set<String> r = set();
		final Set<String> n = set("foo", null);
		
		failConstruct(null, u, r, r, new NullPointerException("client"));
		failConstruct(c, null, r, r, new NullPointerException("rootAuthURL"));
		failConstruct(c, u, null, r, new NullPointerException("readOnlyRoles"));
		failConstruct(c, u, n, r, new IllegalArgumentException(
				"Null or whitespace only string in collection readOnlyRoles"));
		failConstruct(c, u, r, null, new NullPointerException("fullAdminRoles"));
		failConstruct(c, u, r, n, new IllegalArgumentException(
				"Null or whitespace only string in collection fullAdminRoles"));
	}
	
	@Test
	public void failConstructOnGet() throws Exception {
		final CloseableHttpClient client = mock(CloseableHttpClient.class);
		
		doThrow(new IOException("crappers")).when(client).execute(argThat(new HttpGetMatcher(
				new URI("https://bar.com/"),
				Arrays.asList(ImmutableMap.of("name", "accept", "value", "application/json")))));
		
		failConstruct(client, new URL("https://bar.com"), set(), set(),
				new AdministratorHandlerException(
						"Unable to contact the KBase Authentication server: crappers"));
	}
	
	@Test
	public void failConstructOnGetEntityContent() throws Exception {
		final CloseableHttpClient client = mock(CloseableHttpClient.class);
		final CloseableHttpResponse resp = mock(CloseableHttpResponse.class);
		final HttpEntity entity = mock(HttpEntity.class);
		
		when(resp.getEntity()).thenReturn(entity);
		when(resp.getStatusLine()).thenReturn(
				new BasicStatusLine(new ProtocolVersion("http", 1, 1), 404, "Not Found"));
		when(entity.getContent()).thenThrow(new IOException("No content to see here"));
		
		doReturn(resp).when(client).execute(argThat(new HttpGetMatcher(
				new URI("https://foobar.com/"),
				Arrays.asList(ImmutableMap.of("name", "accept", "value", "application/json")))));
		
		failConstruct(client, new URL("https://foobar.com/"), set(), set(),
				new AdministratorHandlerException(
						"Invalid KBase authentication server response. " +
						"Server said 404 Not Found."));
	}
	
	@Test
	public void failConstructOnJSONParse() throws Exception {
		final CloseableHttpClient client = mock(CloseableHttpClient.class);
		final CloseableHttpResponse resp = mock(CloseableHttpResponse.class);
		final HttpEntity entity = mock(HttpEntity.class);
		
		when(resp.getEntity()).thenReturn(entity);
		when(resp.getStatusLine()).thenReturn(
				new BasicStatusLine(new ProtocolVersion("http", 1, 1), 400, "Bad Request"));
		when(entity.getContent()).thenReturn(
				new ByteArrayInputStream("No JSON here bub".getBytes()));
		
		doReturn(resp).when(client).execute(argThat(new HttpGetMatcher(
				new URI("https://foobar.com/"),
				Arrays.asList(ImmutableMap.of("name", "accept", "value", "application/json")))));
		
		try {
			new KBaseAuth2AdminHandler(client, new URL("https://foobar.com"), set(), set());
			fail("expected exception");
		} catch (AdministratorHandlerException got) {
			assertThat("incorrect exception", got.getMessage(), containsString(
					"Invalid KBase authentication server response. Server said 400 Bad Request. " +
					"JSON parser said Unrecognized token 'No': was expecting 'null', 'true', " +
					"'false' or NaN"));
			assertThat("incorrect exception", got.getMessage(), containsString(
					"; line: 1, column: 4]"));
		}
	}
	
	@Test
	public void failConstructResponseClose() throws Exception {
		final CloseableHttpClient client = mock(CloseableHttpClient.class);
		final CloseableHttpResponse resp = mock(CloseableHttpResponse.class);
		final HttpEntity entity = mock(HttpEntity.class);
		
		when(resp.getEntity()).thenReturn(entity);
		when(entity.getContent()).thenReturn(new ByteArrayInputStream(MAPPER.writeValueAsBytes(
				ImmutableMap.of("gitcommithash", "foo", "version", "bar", "servertime", 1))));
		
		doReturn(resp).when(client).execute(argThat(new HttpGetMatcher(
				new URI("http://foo.com/"),
				Arrays.asList(ImmutableMap.of("name", "accept", "value", "application/json")))));
		
		doThrow(new IOException("nope")).when(resp).close();
		
		failConstruct(client, new URL("http://foo.com"), set(), set(),
				new RuntimeException("I give up."));
	}
	
	//TODO AUTH2 restore when testmode root endpoint returns same as regular root
//	@Test
	public void failConstructMissingKeys() throws Exception {
		final CloseableHttpClient client = mock(CloseableHttpClient.class);
		final CloseableHttpResponse resp = mock(CloseableHttpResponse.class);
		final HttpEntity entity = mock(HttpEntity.class);
		
		when(resp.getEntity()).thenReturn(entity);
		when(entity.getContent()).thenReturn(new ByteArrayInputStream(MAPPER.writeValueAsBytes(
				ImmutableMap.of("version", "bar", "servertime", 1))));
		
		doReturn(resp).when(client).execute(argThat(new HttpGetMatcher(
				new URI("https://fake.com/"),
				Arrays.asList(ImmutableMap.of("name", "accept", "value", "application/json")))));
		
		failConstruct(client, new URL("https://fake.com/"), set(), set(),
				new AdministratorHandlerException(
						"https://fake.com/ does not appear to be the KBase authentication " +
						"server. Missing root JSON keys: [gitcommithash]"));
	}
	
	private void failConstruct(
			final CloseableHttpClient client,
			final URL url,
			final Set<String> readRoles,
			final Set<String> adminRoles,
			final Exception expected) {
		try {
			new KBaseAuth2AdminHandler(client, url, readRoles, adminRoles);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}

	@Test
	public void noRoles() throws Exception {
		getAdminRole(
				Collections.emptyList(),
				set(),
				set(),
				AdminRole.NONE);
	}
	
	@Test
	public void notAdmin() throws Exception {
		getAdminRole(
				Arrays.asList("RE_ADMIN", "ID_MAPPER_ADMIN"),
				set("WS_READ", "KBASE_READ"),
				set("WS_ADMIN", "KBASE_ADMIN"),
				AdminRole.NONE);
	}
	
	@Test
	public void readAdmin() throws Exception {
		getAdminRole(
				Arrays.asList("WS_READ", "ID_MAPPER_ADMIN"),
				set("WS_READ", "KBASE_READ"),
				set("WS_ADMIN", "KBASE_ADMIN"),
				AdminRole.READ_ONLY);
	}
	
	@Test
	public void fullAdmin() throws Exception {
		getAdminRole(
				Arrays.asList("WS_ADMIN", "ID_MAPPER_ADMIN"),
				set("WS_READ", "KBASE_READ"),
				set("WS_ADMIN", "KBASE_ADMIN"),
				AdminRole.ADMIN);
	}
	
	@Test
	public void fullAndReadAdmin() throws Exception {
		getAdminRole(
				Arrays.asList("WS_READ", "WS_ADMIN", "ID_MAPPER_ADMIN"),
				set("WS_READ", "KBASE_READ"),
				set("WS_ADMIN", "KBASE_ADMIN"),
				AdminRole.ADMIN);
	}

	private void getAdminRole(
			final List<String> returnedRoles,
			final Set<String> readRoles,
			final Set<String> adminRoles,
			final AdminRole expectedRole)
			throws Exception {
		final Mocks mocks = setUpConstructorMocks("https://fakeurlforsure.com/");
		final CloseableHttpResponse resp2 = mock(CloseableHttpResponse.class);
		final HttpEntity entity2 = mock(HttpEntity.class);
		
		when(resp2.getEntity()).thenReturn(entity2);
		when(entity2.getContent()).thenReturn(new ByteArrayInputStream(MAPPER.writeValueAsBytes(
				ImmutableMap.of("customroles", returnedRoles))));
		
		doReturn(resp2).when(mocks.client).execute(argThat(new HttpGetMatcher(
				new URI("https://fakeurlforsure.com/api/V2/me"),
				Arrays.asList(
						ImmutableMap.of("name", "accept", "value", "application/json"),
						ImmutableMap.of("name", "authorization", "value", "tokentoken")))));
		
		final KBaseAuth2AdminHandler handler = new KBaseAuth2AdminHandler(
				mocks.client,
				new URL("https://fakeurlforsure.com"),
				readRoles,
				adminRoles);
		assertThat("incorrect admin role",
				handler.getAdminRole(new AuthToken("tokentoken", "fake")),
				is(expectedRole));
		
		verify(mocks.resp).close();
		verify(resp2).close();
	}

	@Test
	public void failGetAdminRoleNull() throws Exception {
		final Mocks mocks = setUpConstructorMocks("https://fakeurlforsure.com/");
		
		final KBaseAuth2AdminHandler handler = new KBaseAuth2AdminHandler(
				mocks.client,
				new URL("https://fakeurlforsure.com"),
				set(),
				set());
		failGetAdminRole(handler, null, new NullPointerException("token"));
	}
	
	@Test
	public void failGetAdminRoleOnGet() throws Exception {
		final Mocks mocks = setUpConstructorMocks("https://fakeurlforsure.com/");
		
		doThrow(new IOException("whoopsie daisy")).when(mocks.client).execute(
				argThat(new HttpGetMatcher(new URI("https://fakeurlforsure.com/api/V2/me"),
				Arrays.asList(
						ImmutableMap.of("name", "accept", "value", "application/json"),
						ImmutableMap.of("name", "authorization", "value", "tokentoken")))));
		
		final KBaseAuth2AdminHandler handler = new KBaseAuth2AdminHandler(
				mocks.client,
				new URL("https://fakeurlforsure.com"),
				set(),
				set());
		
		failGetAdminRole(handler, new AuthToken("tokentoken", "fake"),
				new AdministratorHandlerException(
						"Unable to contact the KBase Authentication server: whoopsie daisy"));
	}
	
	@Test
	public void failGetAdminRoleOnGetEntityContent() throws Exception {
		final Mocks mocks = setUpConstructorMocks("https://fakeurlforsure.com/");
		final CloseableHttpResponse resp2 = mock(CloseableHttpResponse.class);
		final HttpEntity entity2 = mock(HttpEntity.class);
		
		when(resp2.getEntity()).thenReturn(entity2);
		when(resp2.getStatusLine()).thenReturn(
				new BasicStatusLine(new ProtocolVersion("http", 1, 1), 418, "I'm a teapot"));
		when(entity2.getContent()).thenThrow(new IOException("well darn it to heck"));
		
		doReturn(resp2).when(mocks.client).execute(argThat(new HttpGetMatcher(
				new URI("https://fakeurlforsure.com/api/V2/me"),
				Arrays.asList(
						ImmutableMap.of("name", "accept", "value", "application/json"),
						ImmutableMap.of("name", "authorization", "value", "tokentoken")))));
		
		final KBaseAuth2AdminHandler handler = new KBaseAuth2AdminHandler(
				mocks.client,
				new URL("https://fakeurlforsure.com"),
				set(),
				set());
		failGetAdminRole(handler, new AuthToken("tokentoken", "fake"),
				new AdministratorHandlerException("Invalid KBase authentication server " +
						"response. Server said 418 I'm a teapot."));
	}
	
	@Test
	public void failGetAdminRoleOnJSONParse() throws Exception {
		final Mocks mocks = setUpConstructorMocks("https://fakeurlforsure.com/");
		final CloseableHttpResponse resp2 = mock(CloseableHttpResponse.class);
		final HttpEntity entity2 = mock(HttpEntity.class);
		
		when(resp2.getEntity()).thenReturn(entity2);
		when(resp2.getStatusLine()).thenReturn(
				new BasicStatusLine(new ProtocolVersion("http", 1, 1), 420, "Enhance your calm"));
		when(entity2.getContent()).thenReturn(
				new ByteArrayInputStream("- I'm YAML. Screw you.".getBytes()));
		
		doReturn(resp2).when(mocks.client).execute(argThat(new HttpGetMatcher(
				new URI("https://fakeurlforsure.com/api/V2/me"),
				Arrays.asList(
						ImmutableMap.of("name", "accept", "value", "application/json"),
						ImmutableMap.of("name", "authorization", "value", "tokentoken")))));
		
		final KBaseAuth2AdminHandler handler = new KBaseAuth2AdminHandler(
				mocks.client,
				new URL("https://fakeurlforsure.com"),
				set(),
				set());
		
		try {
			handler.getAdminRole(new AuthToken("tokentoken", "fake"));
			fail("expected exception");
		} catch (Exception got) {
			assertThat("incorrect exception", got.getMessage(), containsString(
					"Invalid KBase authentication server response. " +
					"Server said 420 Enhance your calm. JSON parser said Unexpected " +
					"character (' ' (code 32)) in numeric value: expected digit (0-9) to " +
					"follow minus sign, for valid numeric value"));
			assertThat("incorrect exception", got.getMessage(), containsString(
					"; line: 1, column: 3]"));
		}
	}
	
	@Test
	public void failGetAdminRoleOnResponseClose() throws Exception {
		final Mocks mocks = setUpConstructorMocks("https://fakeurlforsure.com/");
		final CloseableHttpResponse resp2 = mock(CloseableHttpResponse.class);
		final HttpEntity entity2 = mock(HttpEntity.class);
		
		when(resp2.getEntity()).thenReturn(entity2);
		when(entity2.getContent()).thenReturn(new ByteArrayInputStream(MAPPER.writeValueAsBytes(
				ImmutableMap.of("customroles", set()))));
		
		doReturn(resp2).when(mocks.client).execute(argThat(new HttpGetMatcher(
				new URI("https://fakeurlforsure.com/api/V2/me"),
				Arrays.asList(
						ImmutableMap.of("name", "accept", "value", "application/json"),
						ImmutableMap.of("name", "authorization", "value", "tokentoken")))));
		
		doThrow(new IOException("crap")).when(resp2).close();
		
		final KBaseAuth2AdminHandler handler = new KBaseAuth2AdminHandler(
				mocks.client,
				new URL("https://fakeurlforsure.com"),
				set(),
				set());
		
		failGetAdminRole(handler, new AuthToken("tokentoken", "fake"),
				new RuntimeException("I give up."));
	}
	
	@Test
	public void failGetAdminRoleServerError() throws Exception {
		final Mocks mocks = setUpConstructorMocks("https://fakeurlforsure.com/");
		final CloseableHttpResponse resp2 = mock(CloseableHttpResponse.class);
		final HttpEntity entity2 = mock(HttpEntity.class);
		
		when(resp2.getEntity()).thenReturn(entity2);
		when(entity2.getContent()).thenReturn(new ByteArrayInputStream(MAPPER.writeValueAsBytes(
				ImmutableMap.of("error", ImmutableMap.of("message", "lrn2client lol")))));
		
		doReturn(resp2).when(mocks.client).execute(argThat(new HttpGetMatcher(
				new URI("https://fakeurlforsure.com/api/V2/me"),
				Arrays.asList(
						ImmutableMap.of("name", "accept", "value", "application/json"),
						ImmutableMap.of("name", "authorization", "value", "token")))));
		
		final KBaseAuth2AdminHandler handler = new KBaseAuth2AdminHandler(
				mocks.client,
				new URL("https://fakeurlforsure.com"),
				set(),
				set());
		
		failGetAdminRole(handler, new AuthToken("token", "fake"),
				new AdministratorHandlerException(
						"KBase authentication service reported an error: lrn2client lol"));
	}
	
	private void failGetAdminRole(
			final KBaseAuth2AdminHandler handler,
			final AuthToken token,
			final Exception expected) {
		try {
			handler.getAdminRole(token);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void failGetAdmins() throws Exception {
		failOpDelgated(h -> h.getAdmins());
	}
	
	@Test
	public void failAddAdmin() throws Exception {
		failOpDelgated(h -> h.addAdmin(new WorkspaceUser("foo")));
	}
	
	@Test
	public void failRemoveAdmin() throws Exception {
		failOpDelgated(h -> h.removeAdmin(new WorkspaceUser("foo")));
	}
	
	private static interface OpExcept {
		void apply(KBaseAuth2AdminHandler h) throws Exception;
	}
	
	private void failOpDelgated(final OpExcept op) throws Exception {
		final Mocks mocks = setUpConstructorMocks("https://foo.com/");
		final KBaseAuth2AdminHandler handler = new KBaseAuth2AdminHandler(
				mocks.client,
				new URL("https://foo.com"),
				set(),
				set());
		
		try {
			op.apply(handler);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new UnsupportedOperationException(
					"This operation is delegated to the KBase Authentication service"));
		}
	}
	
}
