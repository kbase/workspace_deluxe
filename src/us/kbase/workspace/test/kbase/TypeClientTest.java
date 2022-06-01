package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.list;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.GetModuleInfoParams;
import us.kbase.workspace.GrantModuleOwnershipParams;
import us.kbase.workspace.ListAllTypesParams;
import us.kbase.workspace.ListModuleVersionsParams;
import us.kbase.workspace.ListModulesParams;
import us.kbase.workspace.ModuleInfo;
import us.kbase.workspace.ModuleVersions;
import us.kbase.workspace.RegisterTypespecCopyParams;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.RemoveModuleOwnershipParams;
import us.kbase.workspace.TypeInfo;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.kbase.TypeClient;
import us.kbase.workspace.kbase.TypeDelegationException;
import us.kbase.workspace.kbase.TypeClient.WorkspaceClientProvider;

public class TypeClientTest {

	// rather than test every method with and without a token, we stagger token usage
	// same for http vs. https urls
	
	// the tests use identity for equals for SDK compiled classes since that's the expected
	// behavior and doing otherwise would blow up the tests
	
	// note the urls are weird as it appears that calling equals on a url actually tries to
	// access the site. Bad urls seem to be a lot faster than urls that might hit a real
	// server
	
	private static class TestMocks {
		private final WorkspaceClient wc;
		private final WorkspaceClientProvider wcp;
		private final TypeClient tc;

		public TestMocks(
				final WorkspaceClient wc,
				final WorkspaceClientProvider wcp,
				final TypeClient dtsm) {
			this.wc = wc;
			this.wcp = wcp;
			this.tc = dtsm;
		}
	}
	
	private TestMocks initTestMocks(final URL targetURL) {
		final WorkspaceClient wc = mock(WorkspaceClient.class);
		final WorkspaceClientProvider wcp = mock(WorkspaceClientProvider.class);
		final TypeClient tc = new TypeClient(targetURL, wcp);
		return new TestMocks(wc, wcp, tc);
	}
	
	@Test
	public void construct() throws Exception {
		final TypeClient tc = initTestMocks(new URL("https://cipherisalwaysabadguy.com")).tc;
		assertThat("incorrect url",
				tc.getTargetURL(), is(new URL("https://cipherisalwaysabadguy.com")));
	}
	
	@Test
	public void constructFail() throws Exception {
		failConstruct(
				null, mock(WorkspaceClientProvider.class), new NullPointerException("targetURL"));
		failConstruct(new URL("http://foo.com"), null, new NullPointerException("wsprovider"));
	}
	
	private void failConstruct(
			final URL url,
			final WorkspaceClientProvider wcp,
			final Exception expected) {
		try {
			new TypeClient(url, wcp);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void grantModuleOwnership() throws Exception {
		for (final boolean asAdmin: list(true, false)) {
			// admin should make no difference
			final TestMocks m = initTestMocks(new URL("https://whee1111.com"));
			
			when(m.wcp.getClient(new URL("https://whee1111.com"))).thenReturn(m.wc);
			
			final GrantModuleOwnershipParams params = new GrantModuleOwnershipParams()
					.withMod("m");
			m.tc.grantModuleOwnership(params, null, asAdmin);

			verify(m.wc).grantModuleOwnership(params);
			verify(m.wc, never()).setIsInsecureHttpConnectionAllowed(true);
		}
	}
	
	@Test
	public void removeModuleOwnership() throws Exception {
		for (final boolean asAdmin: list(true, false)) {
			// admin should make no difference
			final TestMocks m = initTestMocks(new URL("http://whoo1111.com"));
			
			when(m.wcp.getClient(new URL("http://whoo1111.com"), new AuthToken("t", "u")))
					.thenReturn(m.wc);
			
			final RemoveModuleOwnershipParams params = new RemoveModuleOwnershipParams()
					.withMod("mod");
			m.tc.removeModuleOwnership(params, new AuthToken("t", "u"), asAdmin);
			verify(m.wc).removeModuleOwnership(params);
			verify(m.wc).setIsInsecureHttpConnectionAllowed(true);
		}
	}
	
	@Test
	public void requestModuleOwnership() throws Exception {
		final TestMocks m = initTestMocks(new URL("https://whoopsie1111.com"));
		
		when(m.wcp.getClient(new URL("https://whoopsie1111.com"))).thenReturn(m.wc);
		
		m.tc.requestModuleOwnership("somemod", null);
		verify(m.wc).requestModuleOwnership("somemod");
		verify(m.wc, never()).setIsInsecureHttpConnectionAllowed(true);
	}
	
	@Test
	public void registerTypespec() throws Exception {
		final TestMocks m = initTestMocks(new URL("http://insecure111.com"));
		
		final RegisterTypespecParams params = new RegisterTypespecParams().withMod("m2");
		when(m.wcp.getClient(new URL("http://insecure111.com"), new AuthToken("t1", "u1")))
				.thenReturn(m.wc);
		when(m.wc.registerTypespec(params)).thenReturn(ImmutableMap.of("foo", "bar"));

		final Map<String, String> res = m.tc.registerTypespec(params, new AuthToken("t1", "u1"));
		assertThat("incorrect result", res, is(ImmutableMap.of("foo", "bar")));
		verify(m.wc).setIsInsecureHttpConnectionAllowed(true);
	}
	
	@Test
	public void registerTypespecCopy() throws Exception {
		final TestMocks m = initTestMocks(new URL("https://rtc111.com"));
		
		final RegisterTypespecCopyParams params = new RegisterTypespecCopyParams().withMod("m2");
		when(m.wcp.getClient(new URL("https://rtc111.com"))).thenReturn(m.wc);
		when(m.wc.registerTypespecCopy(params)).thenReturn(42L);
		
		final long res = m.tc.registerTypespecCopy(params, null);
		assertThat("incorrect result", res, is(42L));
		verify(m.wc, never()).setIsInsecureHttpConnectionAllowed(true);
	}
	
	@Test
	public void releaseModule() throws Exception {
		final TestMocks m = initTestMocks(new URL("http://rm1111xxxx.com"));
		
		when(m.wcp.getClient(new URL("http://rm1111xxxx.com"), new AuthToken("t7", "u7")))
				.thenReturn(m.wc);
		when(m.wc.releaseModule("moddie")).thenReturn(list("yay"));
		
		final List<String> res = m.tc.releaseModule("moddie", new AuthToken("t7", "u7"));
		assertThat("incorrect result", res, is(list("yay")));
		verify(m.wc).setIsInsecureHttpConnectionAllowed(true);
	}
	
	@Test
	public void listModules() throws Exception {
		final TestMocks m = initTestMocks(new URL("https://lm.com"));
		
		final ListModulesParams params = new ListModulesParams().withOwner("Trump Holdings Inc");
		when(m.wcp.getClient(new URL("https://lm.com"))).thenReturn(m.wc);
		when(m.wc.listModules(params)).thenReturn(list("well done steak", "ketchup"));
		
		final List<String> res = m.tc.listModules(params);
		assertThat("incorrect result", res, is(list("well done steak", "ketchup")));
		verify(m.wc, never()).setIsInsecureHttpConnectionAllowed(true);
	}
	
	@Test
	public void listModuleVersions() throws Exception {
		final TestMocks m = initTestMocks(new URL("http://lmv1111xx.com"));
		
		final ListModuleVersionsParams params = new ListModuleVersionsParams().withMod("m3");
		final ModuleVersions mv = new ModuleVersions().withMod("m3 plus 1");
		when(m.wcp.getClient(new URL("http://lmv1111xx.com"))).thenReturn(m.wc);
		when(m.wc.listModuleVersions(params)).thenReturn(mv);

		final ModuleVersions res = m.tc.listModuleVersions(params, null);
		assertThat("incorrect result", res, is(mv));
		verify(m.wc).setIsInsecureHttpConnectionAllowed(true);
	}
	
	@Test
	public void getModuleInfo() throws Exception {
		final TestMocks m = initTestMocks(new URL("https://gmi.com"));
		
		final GetModuleInfoParams params = new GetModuleInfoParams().withMod("m42");
		final ModuleInfo mi = new ModuleInfo().withVer(89L);
		when(m.wcp.getClient(new URL("https://gmi.com"), new AuthToken("t7", "u8")))
				.thenReturn(m.wc);
		when(m.wc.getModuleInfo(params)).thenReturn(mi);

		final ModuleInfo res = m.tc.getModuleInfo(params, new AuthToken("t7", "u8"));
		assertThat("incorrect result", res, is(mi));
		verify(m.wc, never()).setIsInsecureHttpConnectionAllowed(true);
	}

	@Test
	public void getJsonSchema() throws Exception {
		final TestMocks m = initTestMocks(new URL("http://gjc.com"));
		
		when(m.wcp.getClient(new URL("http://gjc.com"))).thenReturn(m.wc);
		when(m.wc.getJsonschema("sometype")).thenReturn("jssooooonscheeeemaaaa");
		
		final String res = m.tc.getJsonschema("sometype", null);
		assertThat("incorrect result", res, is("jssooooonscheeeemaaaa"));
		verify(m.wc).setIsInsecureHttpConnectionAllowed(true);
	}
	
	@Test
	public void translateFromMD5Types() throws Exception {
		final TestMocks m = initTestMocks(new URL("https://tfmt111.com"));
		
		when(m.wcp.getClient(new URL("https://tfmt111.com"))).thenReturn(m.wc);
		when(m.wc.translateFromMD5Types(list("foo"))).thenReturn(
				ImmutableMap.of("foo", list("bar")));

		final Map<String, List<String>> res = m.tc.translateFromMD5Types(list("foo"));
		assertThat("incorrect result", res, is(ImmutableMap.of("foo", list("bar"))));
		verify(m.wc, never()).setIsInsecureHttpConnectionAllowed(true);
	}
	
	@Test
	public void translateToMD5Types() throws Exception {
		final TestMocks m = initTestMocks(new URL("http://ttmt1111.com"));
		
		when(m.wcp.getClient(new URL("http://ttmt1111.com"), new AuthToken("t", "u")))
				.thenReturn(m.wc);
		when(m.wc.translateToMD5Types(list("foo"))).thenReturn(ImmutableMap.of("foo", "bar"));
		
		final Map<String, String> res = m.tc.translateToMD5Types(
				list("foo"), new AuthToken("t", "u"));
		assertThat("incorrect result", res, is(ImmutableMap.of("foo", "bar")));
		verify(m.wc).setIsInsecureHttpConnectionAllowed(true);
	}
	
	@Test
	public void getTypeInfo() throws Exception {
		final TestMocks m = initTestMocks(new URL("https://gti.com"));
		
		final TypeInfo ti = new TypeInfo().withDescription("some type or other");
		when(m.wcp.getClient(new URL("https://gti.com"))).thenReturn(m.wc);
		when(m.wc.getTypeInfo("type67")).thenReturn(ti);

		final TypeInfo res = m.tc.getTypeInfo("type67", null);
		assertThat("incorrect result", res, is(ti));
		verify(m.wc, never()).setIsInsecureHttpConnectionAllowed(true);
	}
	
	@Test
	public void getAllTypeInfo() throws Exception {
		final TestMocks m = initTestMocks(new URL("http://gati1111.com"));
		
		final TypeInfo ti = new TypeInfo().withDescription("some type or other");
		when(m.wcp.getClient(new URL("http://gati1111.com"), new AuthToken("secrits", "someguy")))
				.thenReturn(m.wc);
		when(m.wc.getAllTypeInfo("somemod")).thenReturn(list(ti));

		final List<TypeInfo> res = m.tc.getAllTypeInfo(
				"somemod", new AuthToken("secrits", "someguy"));
		assertThat("incorrect result", res, is(list(ti)));
		verify(m.wc).setIsInsecureHttpConnectionAllowed(true);
		
	}

	@SuppressWarnings("deprecation")
	@Test
	public void getFuncInfo() throws Exception {
		final TestMocks m = initTestMocks(new URL("https://gfi.com"));
		
		final us.kbase.workspace.FuncInfo fi = new us.kbase.workspace.FuncInfo()
				.withDescription("some function or other");
		when(m.wcp.getClient(new URL("https://gfi.com"))).thenReturn(m.wc);
		when(m.wc.getFuncInfo("funcky chicken")).thenReturn(fi);

		final us.kbase.workspace.FuncInfo res = m.tc.getFuncInfo("funcky chicken", null);
		assertThat("incorrect result", res, is(fi));
		verify(m.wc, never()).setIsInsecureHttpConnectionAllowed(true);
	}
	
	@SuppressWarnings("deprecation")
	@Test
	public void getAllFuncInfo() throws Exception {
		final TestMocks m = initTestMocks(new URL("http://gafi1111.com"));
		
		final us.kbase.workspace.FuncInfo fi = new us.kbase.workspace.FuncInfo()
				.withDescription("some function or other");
		when(m.wcp.getClient(new URL("http://gafi1111.com"), new AuthToken(
						"password123", "Executive VP of Network Security")))
				.thenReturn(m.wc);
		when(m.wc.getAllFuncInfo("somemod")).thenReturn(list(fi));

		final List<us.kbase.workspace.FuncInfo> res = m.tc.getAllFuncInfo("somemod", new AuthToken(
				"password123", "Executive VP of Network Security"));
		assertThat("incorrect result", res, is(list(fi)));
		verify(m.wc).setIsInsecureHttpConnectionAllowed(true);
	}
	
	@Test
	public void listAllTypes() throws Exception {
		final TestMocks m = initTestMocks(new URL("https://lat.com"));
		
		final ListAllTypesParams params = new ListAllTypesParams().withWithEmptyModules(1L);
		when(m.wcp.getClient(new URL("https://lat.com"))).thenReturn(m.wc);
		when(m.wc.listAllTypes(params))
				.thenReturn(ImmutableMap.of("foo", ImmutableMap.of("bar", "baz")));

		final Map<String, Map<String, String>> res = m.tc.listAllTypes(params, null);
		assertThat("incorrect result",
				res, is(ImmutableMap.of("foo", ImmutableMap.of("bar", "baz"))));
		verify(m.wc, never()).setIsInsecureHttpConnectionAllowed(true);
	}
	
	@Test
	public void administrate() throws Exception {
		final TestMocks m = initTestMocks(new URL("http://adminfakexxxx.com"));
		
		final UObject cmd = new UObject("foo");
		final UObject out = new UObject("out");
		when(m.wcp.getClient(new URL("http://adminfakexxxx.com"), new AuthToken("token", "user")))
				.thenReturn(m.wc);
		when(m.wc.administer(cmd)).thenReturn(out);
		
		final UObject got = m.tc.administer(cmd, new AuthToken("token", "user"));
		assertThat("incorrect result", got, is(out));
		verify(m.wc).setIsInsecureHttpConnectionAllowed(true);
	}
	
	/* #####  FAILURE TESTS
	 * Since all the methods under the hood delegate to the same code that
	 * handles failures, we just test each failure mode once, rather than testing the same thing
	 * over and over for all 18 odd methods.
	 * 
	 * If you change how this works be sure to change the tests as well.
	 * #####
	 */
	
	@Test
	public void getClientFailNoTokenUnauthorizedException() throws Exception {
		final String url = "https://donate.doctorswithoutborders.org/secure/donate";
		final TestMocks m = initTestMocks(new URL(url));
		
		when(m.wcp.getClient(new URL(url))).thenThrow(new UnauthorizedException("whoops"));
		
		try {
			m.tc.getJsonschema("Foo.bar", null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(
					got, new RuntimeException("This should be impossible"));
			TestCommon.assertExceptionCorrect(got.getCause(), new UnauthorizedException("whoops"));
		}
	}
	
	@Test
	public void getClientFailWithTokenIOException() throws Exception {
		final String url = "https://poopyfartbutts.com";
		final TestMocks m = initTestMocks(new URL(url));
		
		when(m.wcp.getClient(new URL(url), new AuthToken("t", "u")))
				.thenThrow(new IOException("serious tests for serious people"));
		try {
			m.tc.getTypeInfo("Mod.type", new AuthToken("t", "u"));
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(
					got, new RuntimeException("This should be impossible"));
			TestCommon.assertExceptionCorrect(
					got.getCause(), new IOException("serious tests for serious people"));
		}
	}
	
	@Test
	public void executeFailJsonClientException() throws Exception {
		final String url = "https://elegantliving.svalbard.gov";
		final TestMocks m = initTestMocks(new URL(url));
		
		when(m.wcp.getClient(new URL(url))).thenReturn(m.wc);
		when(m.wc.releaseModule("MyMod")).thenThrow(new JsonClientException("dang bruh"));
		
		try {
			m.tc.releaseModule("MyMod", null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new TypeDelegationException("dang bruh"));
			TestCommon.assertExceptionCorrect(
					got.getCause(), new JsonClientException("dang bruh"));
		}
	}
	
	@Test
	public void executeFailIOException() throws Exception {
		final String url = "https://how-to-avoid-getting-eaten-by-a-polar-bear.svalbard.gov";
		final TestMocks m = initTestMocks(new URL(url));
		
		when(m.wcp.getClient(new URL(url))).thenReturn(m.wc);
		doThrow(new IOException("ow my liver")).when(m.wc).requestModuleOwnership("Mod");
		
		try {
			m.tc.requestModuleOwnership("Mod", null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new TypeDelegationException("ow my liver"));
			TestCommon.assertExceptionCorrect(got.getCause(), new IOException("ow my liver"));
		}
	}
	
	@Test
	public void delegateFailOnExecuteServerException() throws Exception {
		final String url = "https://svalbard-beach-activies.svalbard.gov";
		final TestMocks m = initTestMocks(new URL(url));
		
		when(m.wcp.getClient(new URL(url))).thenReturn(m.wc);
		when(m.wc.getAllTypeInfo("Somemod")).thenThrow(new ServerException(
				"reindeer prodding",
				42,
				"SomeException",
				"server side\nstacktrace goes here\nline 1: int foo = 0"));
		try {
			m.tc.getAllTypeInfo("Somemod", null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(
					got, new TypeDelegationException("reindeer prodding"));
			TestCommon.assertExceptionCorrect(got.getCause(), new TypeDelegationException(
					"server side\nstacktrace goes here\nline 1: int foo = 0"));
			TestCommon.assertExceptionCorrect(
					got.getCause().getCause(),
					new ServerException("reindeer prodding", 42, "SomeException"));
		}
	}
}
