package us.kbase.test.typedobj.idref;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static us.kbase.test.common.TestCommon.set;

import java.util.Collection;

import org.junit.Test;

import us.kbase.test.common.TestCommon;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory.IdReferenceHandlerFactory;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactoryBuilder;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet.IdReferencePermissionHandler;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.NoSuchIdReferenceHandlerException;

public class IdReferencePermissionHandlerSetTest {
	
	@Test
	public void emptySet() throws Exception {
		final IdReferencePermissionHandlerSet s = IdReferenceHandlerSetFactoryBuilder.getBuilder(1)
				.build().createPermissionHandler();
		
		assertThat("incorrect types", s.getIDTypes(), is(set()));
	}
	
	@Test
	public void emptySetWithUser() throws Exception {
		final IdReferencePermissionHandlerSet s = IdReferenceHandlerSetFactoryBuilder.getBuilder(1)
				.build().createPermissionHandler("foo");
		
		assertThat("incorrect types", s.getIDTypes(), is(set()));
	}
	
	@Test
	public void withHandlers() throws Exception {
		final IdReferenceHandlerFactory fac1 = mock(IdReferenceHandlerFactory.class);
		final IdReferenceHandlerFactory fac2 = mock(IdReferenceHandlerFactory.class);
		when(fac1.getIDType()).thenReturn(new IdReferenceType("handle"));
		when(fac2.getIDType()).thenReturn(new IdReferenceType("shock"));
		
		final IdReferencePermissionHandler h1 = mock(IdReferencePermissionHandler.class);
		final IdReferencePermissionHandler h2 = mock(IdReferencePermissionHandler.class);
		when(fac1.createPermissionHandler()).thenReturn(h1);
		when(fac2.createPermissionHandler()).thenReturn(h2);
		
		final IdReferencePermissionHandlerSet s = IdReferenceHandlerSetFactoryBuilder.getBuilder(1)
				.withFactory(fac1)
				.withFactory(fac2)
				.build().createPermissionHandler();
		
		assertThat("incorrect types", s.getIDTypes(),
				is(set(new IdReferenceType("handle"), new IdReferenceType("shock"))));
		assertThat("incorrect has type", s.hasHandler(new IdReferenceType("handle")), is(true));
		assertThat("incorrect has type", s.hasHandler(new IdReferenceType("shock")), is(true));
		assertThat("incorrect has type", s.hasHandler(new IdReferenceType("shocky")), is(false));
		
		s.addReadPermission(new IdReferenceType("handle"),
				set("KBHNDLE_1", "KBHNDLE_6", "KBHNDLE_78"));
		
		verify(h1).addReadPermission(set("KBHNDLE_1", "KBHNDLE_6", "KBHNDLE_78"));
		verifyZeroInteractions(h2);
	}
	
	@Test
	public void withHandlersWithUser() throws Exception {
		final IdReferenceHandlerFactory fac1 = mock(IdReferenceHandlerFactory.class);
		final IdReferenceHandlerFactory fac2 = mock(IdReferenceHandlerFactory.class);
		when(fac1.getIDType()).thenReturn(new IdReferenceType("handle"));
		when(fac2.getIDType()).thenReturn(new IdReferenceType("shock"));
		
		final IdReferencePermissionHandler h1 = mock(IdReferencePermissionHandler.class);
		final IdReferencePermissionHandler h2 = mock(IdReferencePermissionHandler.class);
		when(fac1.createPermissionHandler("user1")).thenReturn(h1);
		when(fac2.createPermissionHandler("user1")).thenReturn(h2);
		
		final IdReferencePermissionHandlerSet s = IdReferenceHandlerSetFactoryBuilder.getBuilder(1)
				.withFactory(fac1)
				.withFactory(fac2)
				.build().createPermissionHandler("user1");
		
		assertThat("incorrect types", s.getIDTypes(),
				is(set(new IdReferenceType("handle"), new IdReferenceType("shock"))));
		assertThat("incorrect has type", s.hasHandler(new IdReferenceType("handle")), is(true));
		assertThat("incorrect has type", s.hasHandler(new IdReferenceType("shock")), is(true));
		assertThat("incorrect has type", s.hasHandler(new IdReferenceType("shocky")), is(false));
		
		s.addReadPermission(new IdReferenceType("handle"),
				set("KBHNDLE_1", "KBHNDLE_6", "KBHNDLE_78"));
		
		verify(h1).addReadPermission(set("KBHNDLE_1", "KBHNDLE_6", "KBHNDLE_78"));
		verifyZeroInteractions(h2);
	}
	
	@Test
	public void addReadPermissionFailBadArgs() throws Exception {
		final IdReferenceHandlerFactory fac1 = mock(IdReferenceHandlerFactory.class);
		when(fac1.getIDType()).thenReturn(new IdReferenceType("handle"));
		
		final IdReferencePermissionHandler h1 = mock(IdReferencePermissionHandler.class);
		when(fac1.createPermissionHandler()).thenReturn(h1);
		
		final IdReferencePermissionHandlerSet s = IdReferenceHandlerSetFactoryBuilder.getBuilder(1)
				.withFactory(fac1)
				.build().createPermissionHandler();
		
		final IdReferenceType h = new IdReferenceType("handle");
		
		addReadPermissionFail(s, null, set(), new NullPointerException("idType"));
		addReadPermissionFail(s, new IdReferenceType("shock"), set(),
				new NoSuchIdReferenceHandlerException(
						"There is no handler registered for the ID type shock"));
		addReadPermissionFail(s, h, null, new NullPointerException("ids"));
		addReadPermissionFail(s, h, set("foo", null), new IllegalArgumentException(
				"Null or whitespace only string in collection ids"));
		addReadPermissionFail(s, h, set("foo", "   \t    "), new IllegalArgumentException(
				"Null or whitespace only string in collection ids"));
	}
	
	private void addReadPermissionFail(
			final IdReferencePermissionHandlerSet s,
			final IdReferenceType type,
			final Collection<String> ids,
			final Exception expected) {
		try {
			s.addReadPermission(type, ids);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void hasTypeFailNull() throws Exception {
		final IdReferenceHandlerFactory fac1 = mock(IdReferenceHandlerFactory.class);
		when(fac1.getIDType()).thenReturn(new IdReferenceType("handle"));
		
		final IdReferencePermissionHandler h1 = mock(IdReferencePermissionHandler.class);
		when(fac1.createPermissionHandler()).thenReturn(h1);
		
		final IdReferencePermissionHandlerSet s = IdReferenceHandlerSetFactoryBuilder.getBuilder(1)
				.withFactory(fac1)
				.build().createPermissionHandler();
		
		try {
			s.hasHandler(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("idType"));
		}
	}

}
