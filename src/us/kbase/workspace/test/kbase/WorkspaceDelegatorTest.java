package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;

import org.junit.Test;

import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.kbase.TypeDelegationException;
import us.kbase.workspace.kbase.WorkspaceDelegator;
import us.kbase.workspace.kbase.WorkspaceDelegator.WorkspaceClientProvider;
import us.kbase.workspace.kbase.WorkspaceDelegator.WorkspaceCommand;

public class WorkspaceDelegatorTest {
	
	@Test
	public void construct() throws Exception {
		final WorkspaceDelegator wd = new WorkspaceDelegator(
				new URL("https://foo.com"), mock(WorkspaceClientProvider.class));
		assertThat("incorrect url", wd.getTargetWorkspace(), is(new URL("https://foo.com")));
	}
	
	@Test
	public void constructFail() throws Exception {
		failConstruct(null, mock(WorkspaceClientProvider.class),
				new NullPointerException("workspaceURL"));
		failConstruct(new URL("https://foo.com"), null, new NullPointerException("provider"));
	}
	
	private void failConstruct(
			final URL url,
			final WorkspaceClientProvider provider,
			final Exception expected) {
		try {
			new WorkspaceDelegator(url, provider);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}

	// since SDK generated classes don't have equals() we use the simplest possible methods
	// to avoid a million asserts to check the object contents

	private static class TestMocks {
		private final WorkspaceClient wc;
		private final WorkspaceClientProvider wcp;
		private final WorkspaceDelegator del;

		public TestMocks(
				final WorkspaceClient wc,
				final WorkspaceClientProvider wcp,
				final WorkspaceDelegator del) {
			this.wc = wc;
			this.wcp = wcp;
			this.del = del;
		}
	}
	
	private TestMocks initTestMocks(final URL wsURL) {
		final WorkspaceClient wc = mock(WorkspaceClient.class);
		final WorkspaceClientProvider p = mock(WorkspaceClientProvider.class);
		final WorkspaceDelegator d = new WorkspaceDelegator(wsURL, p);
		return new TestMocks(wc, p, d);
	}
	
	@Test
	public void delegateWithNoTokenNoResultNoSecurity() throws Exception {
		final TestMocks m = initTestMocks(new URL("http://bar.com"));
		
		when(m.wcp.getClient(new URL("http://bar.com"))).thenReturn(m.wc);
		
		final Void res = m.del.delegate(
				null, c -> {c.deleteObjects(Collections.emptyList()); return null;});
		assertThat("incorrect result", res, nullValue());
		verify(m.wc).setIsInsecureHttpConnectionAllowed(true);
		verify(m.wc).deleteObjects(Collections.emptyList());
	}
	
	@Test
	public void delegateWithTokenWithResultWithSecurity() throws Exception {
		final TestMocks m = initTestMocks(new URL("https://bar2.com"));
		
		when(m.wcp.getClient(new URL("https://bar2.com"), new AuthToken("t", "u")))
				.thenReturn(m.wc);
		when(m.wc.getJsonschema("mytype")).thenReturn("totally jsonschema right here");
		
		final String res = m.del.delegate(new AuthToken("t", "u"), c -> c.getJsonschema("mytype"));
		assertThat("incorrect result", res, is("totally jsonschema right here"));
		verify(m.wc, never()).setIsInsecureHttpConnectionAllowed(true);
	}
	
	@Test
	public void delegateFailOnGetClientNoTokenUnauthorizedException() throws Exception {
		final String url = "https://donate.doctorswithoutborders.org/secure/donate";
		final TestMocks m = initTestMocks(new URL(url));
		
		when(m.wcp.getClient(new URL(url))).thenThrow(new UnauthorizedException("whoops"));
		
		final Exception got = failDelegate(m.del, null, c -> c.status(),
				new RuntimeException("This should be impossible"));
		TestCommon.assertExceptionCorrect(got.getCause(), new UnauthorizedException("whoops"));
	}
	
	@Test
	public void delegateFailOnGetClientWithTokenIOException() throws Exception {
		final String url = "https://poopyfartbutts.com";
		final TestMocks m = initTestMocks(new URL(url));
		
		when(m.wcp.getClient(new URL(url), new AuthToken("t", "u")))
				.thenThrow(new IOException("serious tests for serious people"));
		
		final Exception got = failDelegate(m.del, new AuthToken("t", "u"), c -> c.status(),
				new RuntimeException("This should be impossible"));
		TestCommon.assertExceptionCorrect(
				got.getCause(), new IOException("serious tests for serious people"));
	}
	
	@Test
	public void delegateFailOnExecuteJsonClientException() throws Exception {
		final String url = "https://elegantliving.svalbard.gov";
		final TestMocks m = initTestMocks(new URL(url));
		
		when(m.wcp.getClient(new URL(url))).thenReturn(m.wc);
		when(m.wc.getTypeInfo("sometype")).thenThrow(new JsonClientException("dang bruh"));
		
		final Exception got = failDelegate(m.del, null, c -> c.getTypeInfo("sometype"),
				new TypeDelegationException("dang bruh"));
		TestCommon.assertExceptionCorrect(got.getCause(), new JsonClientException("dang bruh"));
	}
	
	@Test
	public void delegateFailOnExecuteIOException() throws Exception {
		final String url = "https://how-to-avoid-getting-eaten-by-a-polar-bear.svalbard.gov";
		final TestMocks m = initTestMocks(new URL(url));
		
		when(m.wcp.getClient(new URL(url))).thenReturn(m.wc);
		when(m.wc.getTypeInfo("sometype")).thenThrow(new IOException("ow my liver"));
		
		final Exception got = failDelegate(m.del, null, c -> c.getTypeInfo("sometype"),
				new TypeDelegationException("ow my liver"));
		TestCommon.assertExceptionCorrect(got.getCause(), new IOException("ow my liver"));
	}
	
	@Test
	public void delegateFailOnExecuteServerException() throws Exception {
		final String url = "https://svalbard-beach-activies.svalbard.gov";
		final TestMocks m = initTestMocks(new URL(url));
		
		when(m.wcp.getClient(new URL(url))).thenReturn(m.wc);
		when(m.wc.getTypeInfo("sometype")).thenThrow(new ServerException(
				"reindeer prodding",
				42,
				"SomeException",
				"server side\nstacktrace goes here\nline 1: int foo = 0"));
		
		final Exception got = failDelegate(m.del, null, c -> c.getTypeInfo("sometype"),
				new TypeDelegationException("reindeer prodding"));
		TestCommon.assertExceptionCorrect(got.getCause(), new TypeDelegationException(
				"server side\nstacktrace goes here\nline 1: int foo = 0"));
		TestCommon.assertExceptionCorrect(
				got.getCause().getCause(),
				new ServerException("reindeer prodding", 42, "SomeException"));
	}
	
	private <T> Exception failDelegate(
			final WorkspaceDelegator d,
			final AuthToken t,
			final WorkspaceCommand<T> c,
			final Exception expected) {
		try {
			d.delegate(t, c);
			fail("expected exception");
			return null; // can't ever actually get here
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
			return got;
		}
	}
}
