package us.kbase.test.workspace.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static us.kbase.test.common.TestCommon.set;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import us.kbase.abstracthandle.AbstractHandleClient;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.test.common.TestCommon;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet.IdReferencePermissionHandler;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet.IdReferencePermissionHandlerException;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.kbase.HandleIdHandlerFactory;

public class HandleIdHandlerFactoryTest {

	/* Currently this only tests the permissions chunk of the factory, since
	 * the ID handling part is not unit testable as it creates a handle client that talks
	 * directly to the handle service.
	 * 
	 * TODO CODE pass in a getHandleClient(url, token) function to the constructor to make unit testable
	 * or getHandleClient(client, token)
	 */
	
	@Test
	public void getIDType() throws Exception {
		assertThat("incorrect type", new HandleIdHandlerFactory(null).getIDType(),
				is(new IdReferenceType("handle")));
	}
	
	@Test
	public void getDependenciesNoop() throws Exception {
		assertThat("incorrect dependencies",
				new HandleIdHandlerFactory(null).getDependencyStatus(),
				is(Collections.emptyList()));
	}
	
	@Test
	public void getDependencies() throws Exception {
		final AbstractHandleClient cli = mock(AbstractHandleClient.class);
		
		when(cli.status()).thenReturn(ImmutableMap.of("version", "8.6.3-fake"));
		
		assertThat("incorrect dependencies",
				new HandleIdHandlerFactory(cli).getDependencyStatus(),
				is(Arrays.asList(new DependencyStatus(
						true, "OK", "Handle service", "8.6.3-fake"))));
	}
	
	@Test
	public void getDependenciesFailIOException() throws Exception {
		final AbstractHandleClient cli = mock(AbstractHandleClient.class);
		
		when(cli.status()).thenThrow(new IOException("whoops"));
		
		assertThat("incorrect dependencies",
				new HandleIdHandlerFactory(cli).getDependencyStatus(),
				is(Arrays.asList(new DependencyStatus(
						false, "whoops", "Handle service", "Unknown"))));
	}
	
	@Test
	public void getDependenciesFailJsonClientException() throws Exception {
		final AbstractHandleClient cli = mock(AbstractHandleClient.class);
		
		when(cli.status()).thenThrow(new JsonClientException("whoops2"));
		
		assertThat("incorrect dependencies",
				new HandleIdHandlerFactory(cli).getDependencyStatus(),
				is(Arrays.asList(new DependencyStatus(
						false, "whoops2", "Handle service", "Unknown"))));
	}
	
	@Test
	public void addReadPermissionsNoop() throws Exception {
		setPermissionsReadNoop(null);
		setPermissionsReadNoop(set());
	}

	private void setPermissionsReadNoop(final Collection<String> ids) throws Exception {
		final AbstractHandleClient c = mock(AbstractHandleClient.class);
		final HandleIdHandlerFactory f = new HandleIdHandlerFactory(c);
		
		final IdReferencePermissionHandler h = f.createPermissionHandler();
		
		h.addReadPermission(ids);
		
		verifyZeroInteractions(c);
	}
	
	@Test
	public void addReadPermissions() throws Exception {
		final AbstractHandleClient c = mock(AbstractHandleClient.class);
		final HandleIdHandlerFactory f = new HandleIdHandlerFactory(c);
		
		final IdReferencePermissionHandler h = f.createPermissionHandler();
		
		h.addReadPermission(Arrays.asList("foo", "bar"));
		
		verify(c).setPublicRead(Arrays.asList("foo", "bar"));
	}
	
	@Test
	public void addReadPermissionsWithUser() throws Exception {
		final AbstractHandleClient c = mock(AbstractHandleClient.class);
		final HandleIdHandlerFactory f = new HandleIdHandlerFactory(c);
		
		final IdReferencePermissionHandler h = f.createPermissionHandler("user");
		
		h.addReadPermission(Arrays.asList("foo", "bar"));
		
		verify(c).addReadAcl(Arrays.asList("foo", "bar"), "user");
	}
	
	@Test
	public void addReadPermissionsFailNullClient() throws Exception {
		final HandleIdHandlerFactory f = new HandleIdHandlerFactory(null);
		final IdReferencePermissionHandler h = f.createPermissionHandler("user");
		addReadPermissionsFail(h, new IdReferencePermissionHandlerException(
				"The workspace is not currently connected to the Handle Service and cannot " +
				"process Handle ids."));
	}
	
	@Test
	public void addReadPermissionsFailNoUserIOException() throws Exception {
		addReadPermissionsFailNoUser(new IOException("oopsie"),
				"There was an IO problem while attempting to set Handle ACLs: oopsie");
	}

	@Test
	public void addReadPermissionsFailNoUserUnauthorizedException() throws Exception {
		addReadPermissionsFailNoUser(new UnauthorizedException("oh you naughty person"),
				"Unable to contact the Handle Service - the Workspace credentials were " +
				"rejected: oh you naughty person");
	}
	
	@Test
	public void addReadPermissionsFailNoUserServerException() throws Exception {
		addReadPermissionsFailNoUser(new ServerException("well poop", 1, "foo"),
				"The Handle Service reported a problem while attempting to set Handle ACLs: " +
				"well poop");
	}
	
	@Test
	public void addReadPermissionsFailNoUserJsonClientException() throws Exception {
		addReadPermissionsFailNoUser(new JsonClientException("dang it to heck"),
				"There was an unexpected problem while contacting the Handle Service to set " +
				"Handle ACLs: dang it to heck");
	}
	
	private void addReadPermissionsFailNoUser(final Exception ex, final String err)
			throws IOException, JsonClientException {
		final AbstractHandleClient c = mock(AbstractHandleClient.class);
		final HandleIdHandlerFactory f = new HandleIdHandlerFactory(c);
		final IdReferencePermissionHandler h = f.createPermissionHandler();
		
		doThrow(ex).when(c).setPublicRead(Arrays.asList("foo"));
		
		addReadPermissionsFail(h, new IdReferencePermissionHandlerException(err));
	}
	
	@Test
	public void addReadPermissionsFailWithUserIOException() throws Exception {
		addReadPermissionsFailWithUser(new IOException("oopsie"),
				"There was an IO problem while attempting to set Handle ACLs: oopsie");
	}

	@Test
	public void addReadPermissionsFailWithUserUnauthorizedException() throws Exception {
		addReadPermissionsFailWithUser(new UnauthorizedException("oh you naughty person"),
				"Unable to contact the Handle Service - the Workspace credentials were " +
				"rejected: oh you naughty person");
	}
	
	@Test
	public void addReadPermissionsFailWithUserServerException() throws Exception {
		addReadPermissionsFailWithUser(new ServerException("well poop", 1, "foo"),
				"The Handle Service reported a problem while attempting to set Handle ACLs: " +
				"well poop");
	}
	
	@Test
	public void addReadPermissionsFailWithUserJsonClientException() throws Exception {
		addReadPermissionsFailWithUser(new JsonClientException("dang it to heck"),
				"There was an unexpected problem while contacting the Handle Service to set " +
				"Handle ACLs: dang it to heck");
	}
	
	private void addReadPermissionsFailWithUser(final Exception ex, final String err)
			throws IOException, JsonClientException {
		final AbstractHandleClient c = mock(AbstractHandleClient.class);
		final HandleIdHandlerFactory f = new HandleIdHandlerFactory(c);
		final IdReferencePermissionHandler h = f.createPermissionHandler("user");
		
		doThrow(ex).when(c).addReadAcl(Arrays.asList("foo"), "user");
		
		addReadPermissionsFail(h, new IdReferencePermissionHandlerException(err));
	}
	
	private void addReadPermissionsFail(
			final IdReferencePermissionHandler h,
			final Exception expected) {
		
		try {
			h.addReadPermission(Arrays.asList("foo"));
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
}
