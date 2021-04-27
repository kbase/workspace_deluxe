package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import us.kbase.common.service.JsonClientCaller;
import us.kbase.common.service.RpcContext;
import us.kbase.common.test.TestCommon;
import us.kbase.sampleservice.GetSampleACLsParams;
import us.kbase.sampleservice.SampleACLs;
import us.kbase.sampleservice.UpdateSampleACLsParams;
import us.kbase.workspace.kbase.SampleServiceClientWrapper;

public class SampleServiceClientWrapperTest {
	
	/* These tests use identity for equality within the mocks and test results, which is a
	 * practice of which I'm not fond. However, in this case, the objects are always passed
	 * through as is and creating new, non-identity objects for the tests would require
	 * writing custom matchers for the objects, so it's not worth the trouble.
	 * 
	 * It'd be nice if the SDK generated equals and hashCode methods...
	 * 
	 * Also, the JsonRPCContext input parameter is ignored because it's unused in all cases
	 * I'm aware of and will not be used in the workspace.
	 */
	
	@Test
	public void constructStandardFail() throws Exception {
		try {
			new SampleServiceClientWrapper(null);
			fail("expected exception");
		} catch (NullPointerException e) {
			TestCommon.assertExceptionCorrect(e, new NullPointerException("caller"));
		}
	}
	
	@Test
	public void constructDynamicFail() throws Exception {
		final JsonClientCaller jcc = mock(JsonClientCaller.class);
		
		constructDynamicFail(null, "f", new NullPointerException("caller"));
		constructDynamicFail(jcc, null, new IllegalArgumentException(
				"serviceVersion cannot be null or whitespace only"));
		constructDynamicFail(jcc, "   \t    ", new IllegalArgumentException(
				"serviceVersion cannot be null or whitespace only"));
	}
	
	private void constructDynamicFail(
			final JsonClientCaller jcc,
			final String servVer,
			final Exception expected) {
		try {
			new SampleServiceClientWrapper(jcc, servVer);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}		
	}
	
	@Test
	public void getAclsStandard() throws Exception {
		final JsonClientCaller jcc = mock(JsonClientCaller.class);
		
		final GetSampleACLsParams args = new GetSampleACLsParams().withId("foo");
		final SampleACLs res = new SampleACLs().withOwner("bar");
		
		when(jcc.jsonrpcCall(
				eq("SampleService.get_sample_acls"),
				eq(Arrays.asList(args)),
				// the JCC creates a new typeref here and typerefs don't seem to have a
				// usable equals method, so just accept anything.
				any(),
				eq(true),
				eq(false),
				eq(new RpcContext[0]),
				eq(null)))
		.thenReturn(Arrays.asList(res));
		
		assertThat(
				"incorrect sample acl",
				new SampleServiceClientWrapper(jcc).getSampleAcls(args),
				is(res));
		
		verify(jcc).setDynamic(false);
	}
	
	@Test
	public void getAclsDynamic() throws Exception {
		final JsonClientCaller jcc = mock(JsonClientCaller.class);
		
		final GetSampleACLsParams args = new GetSampleACLsParams().withId("foo");
		final SampleACLs res = new SampleACLs().withOwner("bar");
		
		when(jcc.jsonrpcCall(
				eq("SampleService.get_sample_acls"),
				eq(Arrays.asList(args)),
				// the JCC creates a new typeref here and typerefs don't seem to have a
				// usable equals method, so just accept anything.
				any(),
				eq(true),
				eq(false),
				eq(new RpcContext[0]),
				eq("bar")))
		.thenReturn(Arrays.asList(res));
		
		assertThat(
				"incorrect sample acl",
				new SampleServiceClientWrapper(jcc, "bar").getSampleAcls(args),
				is(res));
		
		verify(jcc).setDynamic(true);
	}
	
	@Test
	public void updateAclsStandard() throws Exception {
		final JsonClientCaller jcc = mock(JsonClientCaller.class);
		
		final UpdateSampleACLsParams args = new UpdateSampleACLsParams().withId("bar");
		
		new SampleServiceClientWrapper(jcc).updateSampleAcls(args);
		
		verify(jcc).jsonrpcCall(
				eq("SampleService.update_sample_acls"),
				eq(Arrays.asList(args)),
				// the JCC creates a new typeref here and typerefs don't seem to have a
				// usable equals method, so just accept anything.
				any(),
				eq(false),
				eq(true),
				eq(new RpcContext[0]),
				eq(null));
		
		verify(jcc).setDynamic(false);
	}
	
	@Test
	public void updateAclsDynamic() throws Exception {
		final JsonClientCaller jcc = mock(JsonClientCaller.class);
		
		final UpdateSampleACLsParams args = new UpdateSampleACLsParams().withId("bar");
		
		new SampleServiceClientWrapper(jcc, "whee").updateSampleAcls(args);
		
		verify(jcc).jsonrpcCall(
				eq("SampleService.update_sample_acls"),
				eq(Arrays.asList(args)),
				// the JCC creates a new typeref here and typerefs don't seem to have a
				// usable equals method, so just accept anything.
				any(),
				eq(false),
				eq(true),
				eq(new RpcContext[0]),
				eq("whee"));
		
		verify(jcc).setDynamic(true);
	}
	
	@Test
	public void statusStandard() throws Exception {
		final JsonClientCaller jcc = mock(JsonClientCaller.class);

		final Map<String, Object> res = ImmutableMap.of("foo", "bat");
		
		when(jcc.jsonrpcCall(
				eq("SampleService.status"),
				eq(Collections.emptyList()),
				// the JCC creates a new typeref here and typerefs don't seem to have a
				// usable equals method, so just accept anything.
				any(),
				eq(true),
				eq(false),
				eq(new RpcContext[0]),
				eq(null)))
		.thenReturn(Arrays.asList(res));
		
		assertThat(
				"incorrect sample acl",
				new SampleServiceClientWrapper(jcc).status(),
				is(res));
		
		verify(jcc).setDynamic(false);
	}
	
	@Test
	public void statusDynamic() throws Exception {
		final JsonClientCaller jcc = mock(JsonClientCaller.class);

		final Map<String, Object> res = ImmutableMap.of("foo", "bat");
		
		when(jcc.jsonrpcCall(
				eq("SampleService.status"),
				eq(Collections.emptyList()),
				// the JCC creates a new typeref here and typerefs don't seem to have a
				// usable equals method, so just accept anything.
				any(),
				eq(true),
				eq(false),
				eq(new RpcContext[0]),
				eq("1.0.1")))
		.thenReturn(Arrays.asList(res));
		
		assertThat(
				"incorrect sample acl",
				new SampleServiceClientWrapper(jcc, "1.0.1").status(),
				is(res));
		
		verify(jcc).setDynamic(true);
	}
	
	/* the next 2 methods test building the client with the static helper method */
	
	@Test
	public void getClientStandard() throws Exception {
		statusStandardWithStaticBuilderMethod(null);
		statusStandardWithStaticBuilderMethod(Optional.empty());
		statusStandardWithStaticBuilderMethod(Optional.of("  \t   "));
	}
		
	private void statusStandardWithStaticBuilderMethod(final Optional<String> serviceVer)
			throws Exception {
		final JsonClientCaller jcc = mock(JsonClientCaller.class);

		final Map<String, Object> res = ImmutableMap.of("foo", "bat");
		
		when(jcc.jsonrpcCall(
				eq("SampleService.status"),
				eq(Collections.emptyList()),
				// the JCC creates a new typeref here and typerefs don't seem to have a
				// usable equals method, so just accept anything.
				any(),
				eq(true),
				eq(false),
				eq(new RpcContext[0]),
				eq(null)))
		.thenReturn(Arrays.asList(res));
		
		assertThat(
				"incorrect sample acl",
				SampleServiceClientWrapper.getClient(jcc, serviceVer).status(),
				is(res));
		
		verify(jcc).setDynamic(false);
	}
	
	@Test
	public void getClientDynamic() throws Exception {
		final JsonClientCaller jcc = mock(JsonClientCaller.class);

		final Map<String, Object> res = ImmutableMap.of("foo", "bat");
		
		when(jcc.jsonrpcCall(
				eq("SampleService.status"),
				eq(Collections.emptyList()),
				// the JCC creates a new typeref here and typerefs don't seem to have a
				// usable equals method, so just accept anything.
				any(),
				eq(true),
				eq(false),
				eq(new RpcContext[0]),
				eq("1.0.1")))
		.thenReturn(Arrays.asList(res));
		
		assertThat(
				"incorrect sample acl",
				SampleServiceClientWrapper.getClient(jcc, Optional.of("1.0.1")).status(),
				is(res));
		
		verify(jcc).setDynamic(true);
	}
	
	@Test
	public void getClientFail() throws Exception {
		try {
			SampleServiceClientWrapper.getClient(null, null);
			fail("expected exception");
		} catch (NullPointerException e) {
			TestCommon.assertExceptionCorrect(e, new NullPointerException("caller"));
		}
	}
	
}
