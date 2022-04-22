package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.list;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;

import com.google.common.collect.ImmutableMap;

import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.FuncInfo;
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
import us.kbase.workspace.kbase.DelegatingTypeServerMethods;
import us.kbase.workspace.kbase.WorkspaceDelegator;
import us.kbase.workspace.kbase.WorkspaceDelegator.WorkspaceCommand;

public class DelegatingTypesServerMethodsTest {
	
	// rather than test every method with and without a token, we stagger token usage
	
	// the only way to test that the passed command is correct is to run it
	
	// the tests use identity for equals for SDK compiled classes since that's the expected
	// behavior and doing otherwise would blow up the tests
	
	private static class TestMocks {
		private final WorkspaceClient wc;
		private final WorkspaceDelegator del;
		private final DelegatingTypeServerMethods dtsm;

		public TestMocks(
				final WorkspaceClient wc,
				final WorkspaceDelegator del,
				final DelegatingTypeServerMethods dtsm) {
			this.wc = wc;
			this.del = del;
			this.dtsm = dtsm;
		}
	}
	
	private TestMocks initTestMocks() {
		final WorkspaceClient wc = mock(WorkspaceClient.class);
		final WorkspaceDelegator d = mock(WorkspaceDelegator.class);
		final DelegatingTypeServerMethods m = new DelegatingTypeServerMethods(d);
		return new TestMocks(wc, d, m);
	}
	
	@Test
	public void constructFail() throws Exception {
		try {
			new DelegatingTypeServerMethods(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("delegator"));
		}
		
	}
	
	private <T> void checkNullMethod(final TestMocks mocks, final AuthToken token)
			throws Exception {
		@SuppressWarnings("unchecked")
		final ArgumentCaptor<WorkspaceCommand<T>> cmdcap = ArgumentCaptor.forClass(
				WorkspaceCommand.class);
		
		verify(mocks.del).delegate(eq(token), cmdcap.capture());
		
		assertThat("incorrect result", cmdcap.getValue().execute(mocks.wc), nullValue());
	}
	
	@Test
	public void grantModuleOwnership() throws Exception {
		for (final boolean asAdmin: list(true, false)) {
			// admin should make no difference
			final TestMocks m = initTestMocks();
			
			final GrantModuleOwnershipParams params = new GrantModuleOwnershipParams()
					.withMod("m");
			m.dtsm.grantModuleOwnership(params, null, asAdmin);
			
			checkNullMethod(m, null);
			verify(m.wc).grantModuleOwnership(params);
		}
	}
	
	@Test
	public void removeModuleOwnership() throws Exception {
		for (final boolean asAdmin: list(true, false)) {
			// admin should make no difference
			final TestMocks m = initTestMocks();
			
			final RemoveModuleOwnershipParams params = new RemoveModuleOwnershipParams()
					.withMod("mod");
			m.dtsm.removeModuleOwnership(params, new AuthToken("t", "u"), asAdmin);
			
			checkNullMethod(m, new AuthToken("t", "u"));
			verify(m.wc).removeModuleOwnership(params);
		}
	}
	
	@Test
	public void requestModuleOwnership() throws Exception {
		final TestMocks m = initTestMocks();
		
		m.dtsm.requestModuleOwnership("somemod", null);
		
		checkNullMethod(m, null);
		verify(m.wc).requestModuleOwnership("somemod");
	}
	
	private class WSCmdMatcher<T> implements ArgumentMatcher<WorkspaceCommand<T>> {
		
		private final WorkspaceClient cmdtarget;
		private final T expected;

		public WSCmdMatcher(final WorkspaceClient cmdtarget, final T expected) {
			this.cmdtarget = cmdtarget;
			this.expected = expected;
		}

		@Override
		public boolean matches(final WorkspaceCommand<T> cmdcap) {
			try {
				final T res = cmdcap.execute(cmdtarget);
				if (!expected.equals(res)) {
					System.out.println(String.format(
							"%s: Command result did not match. Got:\n%s\nExpected:\n%s\n",
							getClass().getSimpleName(), res, expected));
					return false;
				}
			} catch (JsonClientException | IOException e) {
				System.out.println(getClass().getSimpleName() + " error:");
				e.printStackTrace();
				return false;
			}
			return true;
		}
	}
	
	
	@Test
	public void registerTypespec() throws Exception {
		final TestMocks m = initTestMocks();
		
		final RegisterTypespecParams params = new RegisterTypespecParams().withMod("m2");
		when(m.wc.registerTypespec(params)).thenReturn(ImmutableMap.of("foo", "bar"));
		when(m.del.delegate(
				eq(new AuthToken("t1", "u1")),
				argThat(new WSCmdMatcher<>(m.wc, ImmutableMap.of("foo", "bar"))))).thenReturn(
						ImmutableMap.of("foo", "bar"));
		
		final Map<String, String> res = m.dtsm.registerTypespec(params, new AuthToken("t1", "u1"));
		assertThat("incorrect result", res, is(ImmutableMap.of("foo", "bar")));
	}
	
	@Test
	public void registerTypespecCopy() throws Exception {
		final TestMocks m = initTestMocks();
		
		final RegisterTypespecCopyParams params = new RegisterTypespecCopyParams().withMod("m2");
		when(m.wc.registerTypespecCopy(params)).thenReturn(42L);
		when(m.del.delegate(eq(null), argThat(new WSCmdMatcher<>(m.wc, 42L)))).thenReturn(42L);
		
		final long res = m.dtsm.registerTypespecCopy(params, null);
		assertThat("incorrect result", res, is(42L));
	}
	
	@Test
	public void releaseModule() throws Exception {
		final TestMocks m = initTestMocks();
		
		when(m.wc.releaseModule("moddie")).thenReturn(list("yay"));
		when(m.del.delegate(
				eq(new AuthToken("t7", "u7")),
				argThat(new WSCmdMatcher<>(m.wc, list("yay"))))).thenReturn(list("yay"));
		
		final List<String> res = m.dtsm.releaseModule("moddie", new AuthToken("t7", "u7"));
		assertThat("incorrect result", res, is(list("yay")));
	}
	
	@Test
	public void listModules() throws Exception {
		final TestMocks m = initTestMocks();
		
		final ListModulesParams params = new ListModulesParams().withOwner("Trump Holdings Inc");
		when(m.wc.listModules(params)).thenReturn(list("well done steak", "ketchup"));
		when(m.del.delegate(
				eq(null),
				argThat(new WSCmdMatcher<>(m.wc, list("well done steak", "ketchup")))))
				.thenReturn(list("well done steak", "ketchup"));

		final List<String> res = m.dtsm.listModules(params);
		assertThat("incorrect result", res, is(list("well done steak", "ketchup")));
	}
	
	@Test
	public void listModuleVersions() throws Exception {
		final TestMocks m = initTestMocks();
		
		final ListModuleVersionsParams params = new ListModuleVersionsParams().withMod("m3");
		final ModuleVersions mv = new ModuleVersions().withMod("m3 plus 1");
		when(m.wc.listModuleVersions(params)).thenReturn(mv);
		when(m.del.delegate(eq(null), argThat(new WSCmdMatcher<>(m.wc, mv)))).thenReturn(mv);

		final ModuleVersions res = m.dtsm.listModuleVersions(params, null);
		assertThat("incorrect result", res, is(mv));
	}
	
	@Test
	public void getModuleInfo() throws Exception {
		final TestMocks m = initTestMocks();
		
		final GetModuleInfoParams params = new GetModuleInfoParams().withMod("m42");
		final ModuleInfo mi = new ModuleInfo().withVer(89L);
		when(m.wc.getModuleInfo(params)).thenReturn(mi);
		when(m.del.delegate(eq(new AuthToken("t7", "u8")), argThat(new WSCmdMatcher<>(m.wc, mi))))
				.thenReturn(mi);

		final ModuleInfo res = m.dtsm.getModuleInfo(params, new AuthToken("t7", "u8"));
		assertThat("incorrect result", res, is(mi));
	}

	@Test
	public void getJsonSchema() throws Exception {
		final TestMocks m = initTestMocks();
		
		when(m.wc.getJsonschema("sometype")).thenReturn("jssooooonscheeeemaaaa");
		when(m.del.delegate(eq(null), argThat(new WSCmdMatcher<>(m.wc, "jssooooonscheeeemaaaa"))))
				.thenReturn("jssooooonscheeeemaaaa");
		
		final String res = m.dtsm.getJsonschema("sometype", null);
		assertThat("incorrect result", res, is("jssooooonscheeeemaaaa"));
	}
	
	@Test
	public void translateFromMD5Types() throws Exception {
		final TestMocks m = initTestMocks();
		
		when(m.wc.translateFromMD5Types(list("foo"))).thenReturn(
				ImmutableMap.of("foo", list("bar")));
		when(m.del.delegate(
				eq(null),
				argThat(new WSCmdMatcher<>(m.wc, ImmutableMap.of("foo", list("bar"))))))
				.thenReturn(ImmutableMap.of("foo", list("bar")));
		
		final Map<String, List<String>> res = m.dtsm.translateFromMD5Types(list("foo"));
		assertThat("incorrect result", res, is(ImmutableMap.of("foo", list("bar"))));
	}
	
	@Test
	public void translateToMD5Types() throws Exception {
		final TestMocks m = initTestMocks();
		
		when(m.wc.translateToMD5Types(list("foo"))).thenReturn(ImmutableMap.of("foo", "bar"));
		when(m.del.delegate(
				eq(new AuthToken("t", "u")),
				argThat(new WSCmdMatcher<>(m.wc, ImmutableMap.of("foo", "bar")))))
				.thenReturn(ImmutableMap.of("foo", "bar"));
		
		final Map<String, String> res = m.dtsm.translateToMD5Types(
				list("foo"), new AuthToken("t", "u"));
		assertThat("incorrect result", res, is(ImmutableMap.of("foo", "bar")));
	}
	
	@Test
	public void getTypeInfo() throws Exception {
		final TestMocks m = initTestMocks();
		
		final TypeInfo ti = new TypeInfo().withDescription("some type or other");
		when(m.wc.getTypeInfo("type67")).thenReturn(ti);
		when(m.del.delegate(eq(null), argThat(new WSCmdMatcher<>(m.wc, ti)))).thenReturn(ti);

		final TypeInfo res = m.dtsm.getTypeInfo("type67", null);
		assertThat("incorrect result", res, is(ti));
	}
	
	@Test
	public void getAllTypeInfo() throws Exception {
		final TestMocks m = initTestMocks();
		
		final TypeInfo ti = new TypeInfo().withDescription("some type or other");
		when(m.wc.getAllTypeInfo("somemod")).thenReturn(list(ti));
		when(m.del.delegate(
				eq(new AuthToken("secrits", "someguy")),
				argThat(new WSCmdMatcher<>(m.wc, list(ti)))))
				.thenReturn(list(ti));

		final List<TypeInfo> res = m.dtsm.getAllTypeInfo(
				"somemod", new AuthToken("secrits", "someguy"));
		assertThat("incorrect result", res, is(list(ti)));
	}

	@Test
	public void getFuncInfo() throws Exception {
		final TestMocks m = initTestMocks();
		
		final FuncInfo fi = new FuncInfo().withDescription("some function or other");
		when(m.wc.getFuncInfo("funcky chicken")).thenReturn(fi);
		when(m.del.delegate(eq(null), argThat(new WSCmdMatcher<>(m.wc, fi)))).thenReturn(fi);

		final FuncInfo res = m.dtsm.getFuncInfo("funcky chicken", null);
		assertThat("incorrect result", res, is(fi));
	}
	
	@Test
	public void getAllFuncInfo() throws Exception {
		final TestMocks m = initTestMocks();
		
		final FuncInfo fi = new FuncInfo().withDescription("some function or other");
		when(m.wc.getAllFuncInfo("somemod")).thenReturn(list(fi));
		when(m.del.delegate(
				eq(new AuthToken("password123", "Executive VP of Network Security")),
				argThat(new WSCmdMatcher<>(m.wc, list(fi)))))
				.thenReturn(list(fi));

		final List<FuncInfo> res = m.dtsm.getAllFuncInfo("somemod", new AuthToken(
				"password123", "Executive VP of Network Security"));
		assertThat("incorrect result", res, is(list(fi)));
	}
	
	@Test
	public void listAllTypes() throws Exception {
		final TestMocks m = initTestMocks();
		
		final ListAllTypesParams params = new ListAllTypesParams().withWithEmptyModules(1L);
		when(m.wc.listAllTypes(params))
				.thenReturn(ImmutableMap.of("foo", ImmutableMap.of("bar", "baz")));
		when(m.del.delegate(
				eq(null),
				argThat(new WSCmdMatcher<>(
						m.wc, ImmutableMap.of("foo", ImmutableMap.of("bar", "baz"))))))
				.thenReturn(ImmutableMap.of("foo", ImmutableMap.of("bar", "baz")));


		final Map<String, Map<String, String>> res = m.dtsm.listAllTypes(params, null);
		assertThat("incorrect result",
				res, is(ImmutableMap.of("foo", ImmutableMap.of("bar", "baz"))));
	}
	
}
