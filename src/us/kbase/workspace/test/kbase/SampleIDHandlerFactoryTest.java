package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;

import org.junit.Test;
import org.mockito.ArgumentMatcher;

import com.google.common.collect.ImmutableMap;

import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.test.TestCommon;
import us.kbase.sampleservice.GetSampleACLsParams;
import us.kbase.sampleservice.SampleACLs;
import us.kbase.sampleservice.UpdateSampleACLsParams;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.SimpleRemappedId;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandlerException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.NoSuchIdException;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet.IdReferencePermissionHandler;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet.IdReferencePermissionHandlerException;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.kbase.SampleIdHandlerFactory;
import us.kbase.workspace.kbase.SampleServiceClientWrapper;

public class SampleIDHandlerFactoryTest {
	
	private static boolean VERBOSE = false;
	
	private static void printVerbose(
			final Object expected,
			final Object got,
			final boolean matches) {
		if (VERBOSE && !matches) {
			System.out.printf("Expected:\n%s\nGot:\n%s\n", expected, got);
		}
	}
	
	private static boolean oEq(final Object o1, final Object o2) {
		return Objects.equals(o1, o2);
	}
	
	private class GetSampleACLParamsMatcher implements ArgumentMatcher<GetSampleACLsParams> {
		
		private final GetSampleACLsParams expected;

		public GetSampleACLParamsMatcher(final GetSampleACLsParams p) {
			this.expected = p;
		}
		
		@Override
		public boolean matches(final GetSampleACLsParams got) {
			if (got == null) { // the when() method causes the matcher to be called with a null
				return false;  // argument
			}
			// how I wish the SDK generated hashCode and equals
			final boolean matches =
					oEq(expected.getAdditionalProperties(), got.getAdditionalProperties())
					&& oEq(expected.getAsAdmin(), got.getAsAdmin())
					&& oEq(expected.getId(), got.getId());
			printVerbose(expected, got, matches);
			return matches;
		}
	}
	
	private class UpdateSampleACLParamsMatcher implements ArgumentMatcher<UpdateSampleACLsParams> {
		
		private final UpdateSampleACLsParams expected;

		public UpdateSampleACLParamsMatcher(final UpdateSampleACLsParams p) {
			this.expected = p;
		}
		
		@Override
		public boolean matches(final UpdateSampleACLsParams got) {
			// how I wish the SDK generated hashCode and equals
			final boolean matches = 
					oEq(expected.getAdditionalProperties(), got.getAdditionalProperties())
					&& oEq(expected.getAsAdmin(), got.getAsAdmin())
					&& oEq(expected.getId(), got.getId())
					&& oEq(expected.getAdmin(), got.getAdmin())
					&& oEq(expected.getAtLeast(), got.getAtLeast())
					&& oEq(expected.getPublicRead(), got.getPublicRead())
					&& oEq(expected.getRead(), got.getRead())
					&& oEq(expected.getRemove(), got.getRemove())
					&& oEq(expected.getWrite(), got.getWrite());
			printVerbose(expected, got, matches);
			return matches;
		}
	}
	
	@Test
	public void getIDType() throws Exception {
		final SampleServiceClientWrapper cli = mock(SampleServiceClientWrapper.class);
		
		assertThat("incorrect ID type", new SampleIdHandlerFactory(cli).getIDType(),
				is(new IdReferenceType("sample")));
	}
	
	@Test
	public void getDependenciesNoop() throws Exception {
		assertThat("incorrect dependencies",
				new SampleIdHandlerFactory(null).getDependencyStatus(),
				is(Collections.emptyList()));
	}
	
	@Test
	public void getDependencies() throws Exception {
		final SampleServiceClientWrapper cli = mock(SampleServiceClientWrapper.class);
		
		when(cli.status()).thenReturn(ImmutableMap.of("version", "8.7.3-fake"));
		
		assertThat("incorrect dependencies",
				new SampleIdHandlerFactory(cli).getDependencyStatus(),
				is(Arrays.asList(new DependencyStatus(
						true, "OK", "Sample service", "8.7.3-fake"))));
	}
	
	@Test
	public void getDependenciesFailIOException() throws Exception {
		final SampleServiceClientWrapper cli = mock(SampleServiceClientWrapper.class);
		
		when(cli.status()).thenThrow(new IOException("whoopsie"));
		
		assertThat("incorrect dependencies",
				new SampleIdHandlerFactory(cli).getDependencyStatus(),
				is(Arrays.asList(new DependencyStatus(
						false, "whoopsie", "Sample service", "Unknown"))));
	}
	
	@Test
	public void getDependenciesFailJsonClientException() throws Exception {
		final SampleServiceClientWrapper cli = mock(SampleServiceClientWrapper.class);
		
		when(cli.status()).thenThrow(new JsonClientException("whoopsie2"));
		
		assertThat("incorrect dependencies",
				new SampleIdHandlerFactory(cli).getDependencyStatus(),
				is(Arrays.asList(new DependencyStatus(
						false, "whoopsie2", "Sample service", "Unknown"))));
	}
	
	@Test
	public void anonymousPermissionNoop() throws Exception {
		// verify that asking for no read permissions causes no errors
		final IdReferencePermissionHandler h = new SampleIdHandlerFactory(null)
				.createPermissionHandler();
		
		h.addReadPermission(null);
		h.addReadPermission(Collections.emptySet());
	}
	
	@Test
	public void addReadPermissionAnonymous() throws Exception {
		final SampleServiceClientWrapper cli = mock(SampleServiceClientWrapper.class);
		
		addReadPermissionAnonymous(cli, new SampleIdHandlerFactory(cli).createPermissionHandler());
	}
	
	@Test
	public void addReadPermissionAnonymousWithNullUser() throws Exception {
		final SampleServiceClientWrapper cli = mock(SampleServiceClientWrapper.class);
		
		addReadPermissionAnonymous(
				cli, new SampleIdHandlerFactory(cli).createPermissionHandler(null));
	}
		
	private void addReadPermissionAnonymous(
			final SampleServiceClientWrapper mockcli,
			final IdReferencePermissionHandler h)
			throws Exception {
		
		h.addReadPermission(new HashSet<>(Arrays.asList("foo", "bar")));
		
		verify(mockcli).updateSampleAcls(argThat(new UpdateSampleACLParamsMatcher(
				new UpdateSampleACLsParams()
					.withAsAdmin(1L).withId("foo").withPublicRead(1L))));
		verify(mockcli).updateSampleAcls(argThat(new UpdateSampleACLParamsMatcher(
				new UpdateSampleACLsParams()
					.withAsAdmin(1L).withId("bar").withPublicRead(1L))));
	}
	
	@Test
	public void addReadPermission() throws Exception {
		final SampleServiceClientWrapper cli = mock(SampleServiceClientWrapper.class);
		
		final IdReferencePermissionHandler h = new SampleIdHandlerFactory(cli)
				.createPermissionHandler("user1");
		
		h.addReadPermission(new HashSet<>(Arrays.asList("foo", "bar")));
		
		verify(cli).updateSampleAcls(argThat(new UpdateSampleACLParamsMatcher(
				new UpdateSampleACLsParams().withAsAdmin(1L).withAtLeast(1L)
						.withId("foo").withRead(Arrays.asList("user1")))));
		verify(cli).updateSampleAcls(argThat(new UpdateSampleACLParamsMatcher(
				new UpdateSampleACLsParams().withAsAdmin(1L).withAtLeast(1L)
						.withId("bar").withRead(Arrays.asList("user1")))));
	}
	
	@Test
	public void addReadPermissionFailNoClient() throws Exception {
		final String err = "The workspace is not currently connected to the Sample Service " +
				"and cannot process Sample IDs.";
		addReadPermissionFail(
				new SampleIdHandlerFactory(null).createPermissionHandler(),
				Arrays.asList("foo"),
				new IdReferencePermissionHandlerException(err));
		
	}
	
	@Test
	public void addReadPermissionFailIOException() throws Exception {
		addReadPermissionFailClientException(
				new IOException("aw dang"),
				new IdReferencePermissionHandlerException(
						"There was an IO problem while attempting to set Sample ACLs: aw dang"));
	}
	
	@Test
	public void addReadPermissionFailUnauthorizedException() throws Exception {
		addReadPermissionFailClientException(
				new UnauthorizedException("oh you naughty person"),
				new IdReferencePermissionHandlerException(
						"Unable to contact the Sample Service - the Workspace credentials " +
						"were rejected: oh you naughty person"));
	}
	
	@Test
	public void addReadPermissionFailServerException() throws Exception {
		addReadPermissionFailClientException(
				new ServerException("you wound me sir", 1, "my name"),
				new IdReferencePermissionHandlerException(
						"The Sample Service reported a problem while attempting to set " +
						"Sample ACLs: you wound me sir"));
	}
	
	@Test
	public void addReadPermissionFailClientException() throws Exception {
		addReadPermissionFailClientException(
				new JsonClientException("bollocks"),
				new IdReferencePermissionHandlerException(
						"There was an unexpected problem while contacting the Sample Service " +
						"to set Sample ACLs: bollocks"));
	}
	
	private void addReadPermissionFailClientException(
			final Exception thrown, final Exception expected) throws Exception {
		final SampleServiceClientWrapper cli = mock(SampleServiceClientWrapper.class);
		
		final IdReferencePermissionHandler h = new SampleIdHandlerFactory(cli)
				.createPermissionHandler("user1");
		
		doThrow(thrown).when(cli).updateSampleAcls(
				argThat(new UpdateSampleACLParamsMatcher(
						new UpdateSampleACLsParams().withAsAdmin(1L).withAtLeast(1L)
								.withId("bar").withRead(Arrays.asList("user1")))));
		
		addReadPermissionFail(h, Arrays.asList("foo", "bar"), expected);
	}
	
	private void addReadPermissionFail(
			final IdReferencePermissionHandler h,
			final Collection<String> ids,
			final Exception expected) {
		try {
			h.addReadPermission(ids);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void sampleIDHandlerGetIDType() throws Exception {
		final SampleServiceClientWrapper cli = mock(SampleServiceClientWrapper.class);
		
		assertThat(
				"incorrect ID type",
				new SampleIdHandlerFactory(cli)
						.createHandler(String.class, new AuthToken("foo", "bar")).getIdType(),
				is(new IdReferenceType("sample")));
	}
	
	@Test
	public void sampleIDHandlerFailConstruct() throws Exception {
		final SampleServiceClientWrapper cli = mock(SampleServiceClientWrapper.class);
		
		try {
			new SampleIdHandlerFactory(cli).createHandler(null, null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("userToken"));
		}
	}
	
	@Test
	public void sampleIDHandlerProcessIDsNoIDs() throws Exception {
		// just check that adding no ids throws no errors
		
		final IdReferenceHandler<String> h = new SampleIdHandlerFactory(null)
				.createHandler(String.class, new AuthToken("t", "user1"));
		
		h.processIds();
		
		assertThat("incorrect ids", h.getRemappedIds("foo"), is(Collections.emptySet()));
	}
	
	@Test
	public void sampleIDHandlerProcessIDs() throws Exception {
		// pretty much has to run the full workflow to see results
		// any attributes passed in to addId() are ignored
		
		final SampleServiceClientWrapper cli = mock(SampleServiceClientWrapper.class);
		
		final IdReferenceHandler<Integer> h = new SampleIdHandlerFactory(cli)
				.createHandler(Integer.class, new AuthToken("t", "user1"));
		
		assertThat("incorrect uniqueness", h.addId(9, "id1", null), is(true));
		assertThat("incorrect uniqueness", h.addId(9, "id1", null), is(false));
		assertThat("incorrect uniqueness", h.addId(9, "id2", null), is(true));
		assertThat("incorrect uniqueness", h.addId(3, "id2", null), is(true));
		
		when(cli.getSampleAcls(argThat(new GetSampleACLParamsMatcher(
				new GetSampleACLsParams().withAsAdmin(1L).withId("id1")))))
				.thenReturn(new SampleACLs()
						.withOwner("user1").withAdmin(Collections.emptyList()));
		when(cli.getSampleAcls(argThat(new GetSampleACLParamsMatcher(
				new GetSampleACLsParams().withAsAdmin(1L).withId("id2")))))
				.thenReturn(new SampleACLs()
						.withOwner("user2").withAdmin(Arrays.asList("user3", "user1")));

		h.processIds();
		
		assertThat("incorrect id", h.getRemappedId("id1"), is(new SimpleRemappedId("id1")));
		assertThat("incorrect id", h.getRemappedId("id2"), is(new SimpleRemappedId("id2")));
		
		assertThat("incorrect ids", h.getRemappedIds(9), is(new HashSet<>(Arrays.asList(
				new SimpleRemappedId("id1"), new SimpleRemappedId("id2")))));
		
		assertThat("incorrect ids", h.getRemappedIds(3), is(new HashSet<>(Arrays.asList(
				new SimpleRemappedId("id2")))));
		
		assertThat("incorrect ids", h.getRemappedIds(1), is(Collections.emptySet()));
	}
	
	@Test
	public void addIdFailNoClient() throws Exception {
		final IdReferenceHandler<String> h = new SampleIdHandlerFactory(null)
				.createHandler(String.class, new AuthToken("t", "user1"));
		
		try {
			h.addId("foo", "bar", null);
			fail("expected exception");
		} catch (IdReferenceException got) {
			assertIDReferenceHandlerExceptionCorrect(got, new IdReferenceException(
					"Found sample id bar. The workspace service currently does not have a " +
					"connection to the sample service and so cannot process objects containing " +
					"sample IDs.",
					new IdReferenceType("sample"), "foo", "bar", null, null));
		}
	}
	
	@Test
	public void processIdsFailIOException() throws Exception {
		processIdsFail(
				new IOException("oh fuddle"),
				new IdReferenceHandlerException("There was a communication error while trying " +
				"to contact the Sample Service: oh fuddle", new IdReferenceType("sample"), null));
	}
	
	@Test
	public void processIdsFailUnauthorizedException() throws Exception {
		processIdsFail(
				new UnauthorizedException("What's all this then?"),
				new IdReferenceHandlerException("Unable to contact the Sample Service - " +
						"the Workspace credentials were rejected: What's all this then?",
						new IdReferenceType("sample"), null));
	}
	
	@Test
	public void processIdsFailJsonClientException() throws Exception {
		processIdsFail(
				new JsonClientException("Fishy fishy fishy fishy fish"),
				new IdReferenceHandlerException("There was an unexpected error while trying to " +
						"contact the Sample Service: Fishy fishy fishy fishy fish",
						new IdReferenceType("sample"), null));
	}
	
	@Test
	public void processIdsFailServerException() throws Exception {
		processIdsFail(
				new ServerException("Colander fried rice", 1, "some name"),
				new IdReferenceException("The Sample Service reported a problem while " +
						"attempting to get Sample ACLs: Colander fried rice",
						new IdReferenceType("sample"), 9, "id1", null, null));
	}
	
	@Test
	public void processIdsFailNoAdminPermission() throws Exception {
		final SampleServiceClientWrapper cli = mock(SampleServiceClientWrapper.class);
		
		final IdReferenceHandler<Integer> h = new SampleIdHandlerFactory(cli)
				.createHandler(Integer.class, new AuthToken("t", "user1"));
		
		assertThat("incorrect uniqueness", h.addId(9, "id1", null), is(true));
		assertThat("incorrect uniqueness", h.addId(3, "id2", null), is(true));
		
		when(cli.getSampleAcls(argThat(new GetSampleACLParamsMatcher(
				new GetSampleACLsParams().withAsAdmin(1L).withId("id1")))))
				.thenReturn(new SampleACLs()
						.withOwner("user1").withAdmin(Collections.emptyList()));
		when(cli.getSampleAcls(argThat(new GetSampleACLParamsMatcher(
				new GetSampleACLsParams().withAsAdmin(1L).withId("id2")))))
				.thenReturn(new SampleACLs()
						.withOwner("user2").withAdmin(Arrays.asList("user3", "user4")));

		try {
			h.processIds();
			fail("expected exception");
		} catch (IdReferenceHandlerException got) {
			assertIDReferenceHandlerExceptionCorrect(got, new IdReferenceException(
					"User user1 does not have administrative permissions for sample id2",
					new IdReferenceType("sample"), 3, "id2", null, null));
		}
	}
	
	@Test
	public void getRemappedIDFail() throws Exception {
		final SampleServiceClientWrapper cli = mock(SampleServiceClientWrapper.class);
		
		final IdReferenceHandler<Integer> h = new SampleIdHandlerFactory(cli)
				.createHandler(Integer.class, new AuthToken("t", "user1"));
		
		assertThat("incorrect uniqueness", h.addId(9, "id1", null), is(true));
		
		when(cli.getSampleAcls(argThat(new GetSampleACLParamsMatcher(
				new GetSampleACLsParams().withAsAdmin(1L).withId("id1")))))
				.thenReturn(new SampleACLs()
						.withOwner("user1").withAdmin(Collections.emptyList()));

		h.processIds();
		
		
		try {
			h.getRemappedId("id3");
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NoSuchIdException(
					"No such ID contained in this mapper: id3"));
		}
	}
	
	private void processIdsFail(final Exception thrown, final IdReferenceHandlerException expected)
			throws Exception {
		final SampleServiceClientWrapper cli = mock(SampleServiceClientWrapper.class);
		
		final IdReferenceHandler<Integer> h = new SampleIdHandlerFactory(cli)
				.createHandler(Integer.class, new AuthToken("t", "user1"));
		
		assertThat("incorrect uniqueness", h.addId(9, "id1", null), is(true));
		
		when(cli.getSampleAcls(argThat(new GetSampleACLParamsMatcher(
				new GetSampleACLsParams().withAsAdmin(1L).withId("id1")))))
				.thenThrow(thrown);
				
		try {
			h.processIds();
			fail("expected exception");
		} catch (IdReferenceHandlerException got) {
			assertIDReferenceHandlerExceptionCorrect(got, expected);
		}
	}

	private void assertIDReferenceHandlerExceptionCorrect(
			final IdReferenceHandlerException got,
			final IdReferenceHandlerException expected) {
		TestCommon.assertExceptionCorrect(got, expected);
		assertThat("incorrect id type", got.getIdType(), is(expected.getIdType()));
		if (expected instanceof IdReferenceException) {
			final IdReferenceException got2 = (IdReferenceException) got;
			final IdReferenceException expected2 = (IdReferenceException) expected;
			assertThat("incorrect assobj", got2.getAssociatedObject(),
					is(expected2.getAssociatedObject()));
			assertThat("incorrect id", got2.getId(), is(expected2.getId()));
			assertThat("incorrect id ref", got2.getIdReference(),
					is(expected2.getIdReference()));
			assertThat("incorrect id attribs", got2.getIdAttributes(),
					is(expected2.getIdAttributes()));
			
		}
	}
}
