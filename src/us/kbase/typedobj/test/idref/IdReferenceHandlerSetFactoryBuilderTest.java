package us.kbase.typedobj.test.idref;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.set;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import us.kbase.auth.AuthToken;
import us.kbase.common.test.TestCommon;
import us.kbase.typedobj.idref.IdReference;
import us.kbase.typedobj.idref.IdReferenceHandlerSet;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory.IdReferenceHandlerFactory;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactoryBuilder;
import us.kbase.typedobj.idref.IdReferenceType;

public class IdReferenceHandlerSetFactoryBuilderTest {
	
	@Test
	public void buildEmpty() throws Exception {
		final IdReferenceHandlerSetFactoryBuilder b = IdReferenceHandlerSetFactoryBuilder
				.getBuilder(0).build();
		
		final IdReferenceHandlerSetFactory f = b.getFactory(null);
		
		final IdReferenceHandlerSet<String> s = f.createHandlers(String.class);
		
		assertThat("incorrect types", s.getIDTypes(), is(set()));
		assertThat("incorrect size", s.size(), is(0));
		assertThat("incorrect max size", s.getMaximumIdCount(), is(0));
	}
	
	@Test
	public void buildWithFactoriesWithToken() throws Exception {
		buildWithFactories(new AuthToken("token", "fake"));
	}
	
	@Test
	public void buildWithFactoriesWithNullToken() throws Exception {
		buildWithFactories(null);
	}

	private void buildWithFactories(final AuthToken token) throws Exception {
		final IdReferenceHandlerFactory fac1 = mock(IdReferenceHandlerFactory.class);
		final IdReferenceHandlerFactory fac2 = mock(IdReferenceHandlerFactory.class);
		final IdReferenceHandlerFactory fac3 = mock(IdReferenceHandlerFactory.class);
		
		@SuppressWarnings("unchecked")
		final IdReferenceHandler<Long> h1 = mock(IdReferenceHandler.class);
		@SuppressWarnings("unchecked")
		final IdReferenceHandler<Long> h2 = mock(IdReferenceHandler.class);
		@SuppressWarnings("unchecked")
		final IdReferenceHandler<Long> h3 = mock(IdReferenceHandler.class);
		
		when(fac1.getIDType()).thenReturn(new IdReferenceType("t1"));
		when(fac2.getIDType()).thenReturn(new IdReferenceType("t2"));
		when(fac3.getIDType()).thenReturn(new IdReferenceType("t2")); // test overwrite
		
		final IdReferenceHandlerSetFactoryBuilder b = IdReferenceHandlerSetFactoryBuilder
				.getBuilder(8)
				.withFactory(fac1)
				.withFactory(fac2)
				.withFactory(fac3)
				.build();
		
		final IdReferenceHandlerSetFactory f = b.getFactory(token);

		when(fac1.createHandler(Long.class, token)).thenReturn(h1);
		when(fac2.createHandler(Long.class, token)).thenReturn(h2);
		when(fac3.createHandler(Long.class, token)).thenReturn(h3);
		
		final IdReferenceHandlerSet<Long> s = f.createHandlers(Long.class);
		
		assertThat("incorrect types", s.getIDTypes(),
				is(set(new IdReferenceType("t1"), new IdReferenceType("t2"))));
		assertThat("incorrect size", s.size(), is(0));
		assertThat("incorrect max size", s.getMaximumIdCount(), is(8));
		
		s.associateObject(8L);
		s.addStringId(new IdReference<String>(new IdReferenceType("t2"), "whee", null));
		s.addStringId(new IdReference<String>(
				new IdReferenceType("t1"), "whoo", Arrays.asList("a1", "a2")));
		
		verifyNoMoreInteractions(h2); // overwritten
		verify(h1).addId(8L, "whoo", Arrays.asList("a1", "a2"));
		verify(h3).addId(8L, "whee", Collections.emptyList());
	}
	
	@Test
	public void getBuilderFail() throws Exception {
		try {
			IdReferenceHandlerSetFactoryBuilder.getBuilder(-1);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got,
					new IllegalArgumentException("maxUniqueIdCount must be at least 0"));
		}
	}
	
	@Test
	public void withFactoryFail() throws Exception {
		final IdReferenceHandlerSetFactoryBuilder.Builder b = IdReferenceHandlerSetFactoryBuilder
				.getBuilder(1);
		
		withFactoryFail(b, null, new NullPointerException("factory cannot be null"));
		
		final IdReferenceHandlerFactory fac = mock(IdReferenceHandlerFactory.class);
		when(fac.getIDType()).thenReturn(null);
		
		withFactoryFail(b, fac, new NullPointerException("factory returned null for ID type"));
	}
	
	private void withFactoryFail(
			final IdReferenceHandlerSetFactoryBuilder.Builder b,
			final IdReferenceHandlerFactory fac,
			final Exception expected) {
		try {
			b.withFactory(fac);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}

}
