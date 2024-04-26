package us.kbase.test.workspace.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static us.kbase.test.common.TestCommon.set;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Test;

import us.kbase.auth.AuthToken;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockACL;
import us.kbase.shock.client.ShockACLType;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.ShockUserId;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockAuthorizationException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.ShockNoNodeException;
import us.kbase.test.common.TestCommon;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.SimpleRemappedId;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandlerException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.NoSuchIdException;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet.IdReferencePermissionHandler;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet.IdReferencePermissionHandlerException;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.kbase.BytestreamIdHandlerFactory;
import us.kbase.workspace.kbase.BytestreamIdHandlerFactory.BytestreamClientCloner;

public class BytestreamIdHandlerFactoryTest {
	
	private static final List<String> MTL = Collections.emptyList();
	
	@SafeVarargs
	private static <T> List<T> list(T... objects) {
		return Arrays.asList(objects);
	}
	
	@Test
	public void type() throws Exception {
		final BasicShockClient cli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		assertThat("incorrect type", new BytestreamIdHandlerFactory(cli, cloner).getIDType(),
				is(new IdReferenceType("bytestream")));
		assertThat("incorrect type", BytestreamIdHandlerFactory.TYPE,
				is(new IdReferenceType("bytestream")));
	}
	
	@Test
	public void constructFail() throws Exception {
		final BasicShockClient cli = mock(BasicShockClient.class);
		
		try {
			new BytestreamIdHandlerFactory(cli, null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("cloner"));
		}
	}
	
	@Test
	public void getDependenciesNoop() throws Exception {
		assertThat("incorrect dependencies",
				new BytestreamIdHandlerFactory(null, null).getDependencyStatus(),
				is(Collections.emptyList()));
	}
	
	@Test
	public void getDependencies() throws Exception {
		final BasicShockClient cli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		when(cli.getRemoteVersion()).thenReturn("8.6.3-fake");
		
		assertThat("incorrect dependencies",
				new BytestreamIdHandlerFactory(cli, cloner).getDependencyStatus(),
				is(Arrays.asList(new DependencyStatus(
						true, "OK", "Linked Shock for IDs", "8.6.3-fake"))));
	}
	
	@Test
	public void getDependenciesFailIOException() throws Exception {
		final BasicShockClient cli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		when(cli.getRemoteVersion()).thenThrow(new IOException("oh dang"));
		
		assertThat("incorrect dependencies",
				new BytestreamIdHandlerFactory(cli, cloner).getDependencyStatus(),
				is(Arrays.asList(new DependencyStatus(
						false, "oh dang", "Linked Shock for IDs", "Unknown"))));
	}
	
	@Test
	public void getDependenciesFailInvalidShockURLException() throws Exception {
		final BasicShockClient cli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		when(cli.getRemoteVersion()).thenThrow(new InvalidShockUrlException("poopie"));
		
		assertThat("incorrect dependencies",
				new BytestreamIdHandlerFactory(cli, cloner).getDependencyStatus(),
				is(Arrays.asList(new DependencyStatus(
						false, "poopie", "Linked Shock for IDs", "Unknown"))));
	}
	
	@Test
	public void addReadPermissionNoIDs() throws Exception {
		final BasicShockClient cli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferencePermissionHandler h1 = new BytestreamIdHandlerFactory(cli, cloner)
				.createPermissionHandler();
		final IdReferencePermissionHandler h2 = new BytestreamIdHandlerFactory(cli, cloner)
				.createPermissionHandler("user");
		
		h1.addReadPermission(null);
		h2.addReadPermission(null);
		h1.addReadPermission(Collections.emptyList());
		h2.addReadPermission(Collections.emptyList());
		
		verifyZeroInteractions(cli);
		verifyZeroInteractions(cloner);
	}
	
	@Test
	public void addReadPermissionNullUser() throws Exception {
		final BasicShockClient cli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferencePermissionHandler h = new BytestreamIdHandlerFactory(cli, cloner)
				.createPermissionHandler();
		final UUID id1 = UUID.randomUUID();
		final UUID id2 = UUID.randomUUID();
		
		h.addReadPermission(set(id1.toString(), id2.toString()));
		
		verify(cli).setPubliclyReadable(new ShockNodeId(id1.toString()), true);
		verify(cli).setPubliclyReadable(new ShockNodeId(id2.toString()), true);
		
		verifyNoMoreInteractions(cli);
		verifyZeroInteractions(cloner);
	}
	
	@Test
	public void addReadPermissionWithUser() throws Exception {
		final BasicShockClient cli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferencePermissionHandler h = new BytestreamIdHandlerFactory(cli, cloner)
				.createPermissionHandler("user1");
		final UUID id1 = UUID.randomUUID();
		final UUID id2 = UUID.randomUUID();
		
		h.addReadPermission(set(id1.toString(), id2.toString()));
		
		verify(cli).addToNodeAcl(
				new ShockNodeId(id1.toString()), Arrays.asList("user1"), ShockACLType.READ);
		verify(cli).addToNodeAcl(
				new ShockNodeId(id2.toString()), Arrays.asList("user1"), ShockACLType.READ);
		
		verifyNoMoreInteractions(cli);
		verifyZeroInteractions(cloner);
	}
	
	@Test
	public void addReadPermissionFailNoClient() throws Exception {
		addReadPermissionFailWithAndWithoutUser(
				new BytestreamIdHandlerFactory(null, null), "foo", set("id"),
				new IdReferencePermissionHandlerException(
						"There is no connection configured for bytestream storage and " +
						"bytestream IDs cannot be processed."));
	}
	
	@Test
	public void addReadPermissionFailBadID() throws Exception {
		final BasicShockClient cli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
	
		addReadPermissionFailWithAndWithoutUser(
				new BytestreamIdHandlerFactory(cli, cloner), "foo",
				set(UUID.randomUUID().toString(), "id"),
				new IdReferencePermissionHandlerException("Illegal bytestream ID: id"));
	}
	
	@Test
	public void addReadPermissionFailIOError() throws Exception {
		final BasicShockClient cli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final UUID id = UUID.randomUUID();
		
		doThrow(new IOException("whoopsie"))
				.when(cli).setPubliclyReadable(new ShockNodeId(id.toString()), true);
		doThrow(new IOException("whoopsie"))
				.when(cli).addToNodeAcl(
						new ShockNodeId(id.toString()), Arrays.asList("foo"), ShockACLType.READ);
	
		addReadPermissionFailWithAndWithoutUser(
				new BytestreamIdHandlerFactory(cli, cloner), "foo",
				set(UUID.randomUUID().toString(), id.toString()),
				new IdReferencePermissionHandlerException(String.format(
						"There was an IO problem while attempting to set bytestream ACLs on " +
						"node %s: whoopsie", id.toString())));
	}
	
	@Test
	public void addReadPermissionFailShockError() throws Exception {
		final BasicShockClient cli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final UUID id = UUID.randomUUID();
		
		doThrow(new ShockHttpException(400, "darn heck"))
				.when(cli).setPubliclyReadable(new ShockNodeId(id.toString()), true);
		doThrow(new ShockHttpException(400, "darn heck"))
				.when(cli).addToNodeAcl(
						new ShockNodeId(id.toString()), Arrays.asList("foo"), ShockACLType.READ);
	
		addReadPermissionFailWithAndWithoutUser(
				new BytestreamIdHandlerFactory(cli, cloner), "foo",
				set(UUID.randomUUID().toString(), id.toString()),
				new IdReferencePermissionHandlerException(String.format(
						"Bytestream storage reported a problem while attempting to set ACLs " +
						"on node %s: darn heck", id.toString())));
	}
	
	private void addReadPermissionFailWithAndWithoutUser(
			final BytestreamIdHandlerFactory fac,
			final String user,
			final Collection<String> ids,
			final Exception expected) {
		try {
			fac.createPermissionHandler().addReadPermission(ids);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
		try {
			fac.createPermissionHandler(user).addReadPermission(ids);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void createHandlerFail() throws Exception {
		final BasicShockClient cli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		try {
			new BytestreamIdHandlerFactory(cli, cloner).createHandler(Long.class, null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("userToken"));
		}
	}
	
	@Test
	public void processIDsGetType() throws Exception {
		final BasicShockClient cli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferenceHandler<Long> h = new BytestreamIdHandlerFactory(cli, cloner)
				.createHandler(Long.class, new AuthToken("token", "user"));
		
		assertThat("incorrect type", h.getIdType(), is(new IdReferenceType("bytestream")));
	}
	
	@Test
	public void processIDsNoIDs() throws Exception {
		final BasicShockClient cli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferenceHandler<Long> h = new BytestreamIdHandlerFactory(cli, cloner)
				.createHandler(Long.class, new AuthToken("token", "user"));
		
		h.processIds();
		
		verifyZeroInteractions(cli);
		verifyZeroInteractions(cloner);
	}
	
	@Test
	public void processIDs() throws Exception {
		/* For the id handler portion of the code, it's not really possible to test without
		 * going through the entire process flow, so the tests are going to be largish.
		 */
		final BasicShockClient adminCli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferenceHandler<String> h = new BytestreamIdHandlerFactory(adminCli, cloner)
				.createHandler(String.class, new AuthToken("token", "user"));
		
		final String id_foo_1 = UUID.randomUUID().toString();
//		System.out.println("id_foo_1: " + id_foo_1);
		final String id_foo_2 = UUID.randomUUID().toString();
//		System.out.println("id_foo_2: " + id_foo_2);
		final String id_bar_1 = UUID.randomUUID().toString();
//		System.out.println("id_bar_1: " + id_bar_1);
		final String id_bar_2 = id_foo_2;
		
		assertThat("incorrect uniq", h.addId("foo", id_foo_1, null), is(true));
		assertThat("incorrect uniq", h.addId("foo", id_foo_2, null), is(true));
		assertThat("incorrect uniq", h.addId("foo", id_foo_2, null), is(false));
		assertThat("incorrect uniq", h.addId("bar", id_bar_1, null), is(true));
		assertThat("incorrect uniq", h.addId("bar", id_bar_2, null), is(true));
		
		final BasicShockClient cloned = mock(BasicShockClient.class);
		when(cloner.clone(adminCli)).thenReturn(cloned);
		
		when(adminCli.getToken()).thenReturn(new AuthToken("token", "admin"));

		setUpShockACLResponse(cloned, id_foo_1, "admin", list("foo"), list("bar"));
		setUpShockACLResponse(cloned, id_foo_2, "user", list("someu"), MTL);
		setUpShockACLResponse(cloned, id_bar_1, "user", MTL, list("whee"));
		
		setUpShockOwnResponse(adminCli, id_foo_2, "admin", list("someu2"), MTL);
		setUpShockOwnResponse(adminCli, id_bar_1, "admin", MTL, list("whee2"));
		
		h.processIds();
		
		verify(cloned).updateToken(new AuthToken("token", "user"));
		// the same ID in 2 objects should only result in 1 call
		verify(cloned, times(1)).getACLs(new ShockNodeId(id_foo_1));
		verify(cloned, times(1)).getACLs(new ShockNodeId(id_foo_2));
		verify(cloned, times(1)).getACLs(new ShockNodeId(id_bar_1));
		verify(adminCli, times(1)).removeFromNodeAcl(
				new ShockNodeId(id_foo_1), Arrays.asList("foo"), ShockACLType.WRITE);
		verify(adminCli, times(1)).removeFromNodeAcl(
				new ShockNodeId(id_foo_1), Arrays.asList("bar"), ShockACLType.DELETE);
		verify(adminCli, times(1)).removeFromNodeAcl(
				new ShockNodeId(id_foo_2), Arrays.asList("someu2"), ShockACLType.WRITE);
		verify(adminCli, times(1)).removeFromNodeAcl(
				new ShockNodeId(id_bar_1), Arrays.asList("whee2"), ShockACLType.DELETE);
		verify(adminCli, times(1)).addToNodeAcl(
				new ShockNodeId(id_foo_2), Arrays.asList("admin"), ShockACLType.OWNER);
		verify(adminCli, times(1)).addToNodeAcl(
				new ShockNodeId(id_bar_1), Arrays.asList("admin"), ShockACLType.OWNER);
		
		assertThat("incorrect id", h.getRemappedId(id_foo_1), is(toSRD(id_foo_1)));
		assertThat("incorrect id", h.getRemappedId(id_foo_2), is(toSRD(id_foo_2)));
		assertThat("incorrect id", h.getRemappedId(id_bar_1), is(toSRD(id_bar_1)));
		assertThat("incorrect id", h.getRemappedId(id_bar_2), is(toSRD(id_foo_2)));
		
		assertThat("incorrect ids", h.getRemappedIds("foo"),
				is(set(toSRD(id_foo_1), toSRD(id_foo_2))));
		assertThat("incorrect ids", h.getRemappedIds("bar"),
				is(set(toSRD(id_bar_1), toSRD(id_foo_2))));
		assertThat("incorrect ids", h.getRemappedIds("baz"), is(set()));
	}
	
	/* test a few more special cases for removing users from node ACLs
	 * when the node is owned by the workspace.
	 */
	
	@Test
	public void processIDsOwnedNodeWithWSOwnerOnlyInACL() throws Exception {
		// no acls should be modified since only the owner exists in the acls.
		final BasicShockClient adminCli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferenceHandler<String> h = new BytestreamIdHandlerFactory(adminCli, cloner)
				.createHandler(String.class, new AuthToken("token", "user"));
		
		final String id = UUID.randomUUID().toString();
		h.addId("foo", id, null);
		
		final BasicShockClient cloned = mock(BasicShockClient.class);
		when(cloner.clone(adminCli)).thenReturn(cloned);
		
		when(adminCli.getToken()).thenReturn(new AuthToken("token", "admin"));

		setUpShockACLResponse(cloned, id, "admin", list("admin"), list("admin"));
		
		h.processIds();
		
		verify(cloned).updateToken(new AuthToken("token", "user"));
		verify(adminCli, times(0)).removeFromNodeAcl(any(), any(), any());
		verify(adminCli, times(0)).addToNodeAcl(any(), any(), any());
	}
	
	@Test
	public void processIDsOwnedNodeWithWriteUsers() throws Exception {
		final BasicShockClient adminCli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferenceHandler<String> h = new BytestreamIdHandlerFactory(adminCli, cloner)
				.createHandler(String.class, new AuthToken("token", "user"));
		
		final String id = UUID.randomUUID().toString();
		h.addId("foo", id, null);
		
		final BasicShockClient cloned = mock(BasicShockClient.class);
		when(cloner.clone(adminCli)).thenReturn(cloned);
		
		when(adminCli.getToken()).thenReturn(new AuthToken("token", "admin"));

		setUpShockACLResponse(cloned, id, "admin", list("admin", "foo", "bar"), list("admin"));
		
		h.processIds();
		
		verify(cloned).updateToken(new AuthToken("token", "user"));
		verify(adminCli, times(1)).removeFromNodeAcl(
				new ShockNodeId(id), Arrays.asList("foo", "bar"), ShockACLType.WRITE);
		verify(adminCli, times(0)).removeFromNodeAcl(any(), any(), eq(ShockACLType.DELETE));
		verify(adminCli, times(0)).addToNodeAcl(any(), any(), any());
	}
	
	@Test
	public void processIDsOwnedNodeWithDeleteUsers() throws Exception {
		final BasicShockClient adminCli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferenceHandler<String> h = new BytestreamIdHandlerFactory(adminCli, cloner)
				.createHandler(String.class, new AuthToken("token", "user"));
		
		final String id = UUID.randomUUID().toString();
		h.addId("foo", id, null);
		
		final BasicShockClient cloned = mock(BasicShockClient.class);
		when(cloner.clone(adminCli)).thenReturn(cloned);
		
		when(adminCli.getToken()).thenReturn(new AuthToken("token", "admin"));

		setUpShockACLResponse(cloned, id, "admin", list("admin"), list("admin", "baz", "bat"));
		
		h.processIds();
		
		verify(cloned).updateToken(new AuthToken("token", "user"));
		verify(adminCli, times(0)).removeFromNodeAcl(any(), any(), eq(ShockACLType.WRITE));
		verify(adminCli, times(1)).removeFromNodeAcl(
				new ShockNodeId(id), Arrays.asList("baz", "bat"), ShockACLType.DELETE);
		verify(adminCli, times(0)).addToNodeAcl(any(), any(), any());
	}
	
	@Test
	public void processIDsOwnedNodeWithWriteDeleteUsers() throws Exception {
		final BasicShockClient adminCli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferenceHandler<String> h = new BytestreamIdHandlerFactory(adminCli, cloner)
				.createHandler(String.class, new AuthToken("token", "user"));
		
		final String id = UUID.randomUUID().toString();
		h.addId("foo", id, null);
		
		final BasicShockClient cloned = mock(BasicShockClient.class);
		when(cloner.clone(adminCli)).thenReturn(cloned);
		
		when(adminCli.getToken()).thenReturn(new AuthToken("token", "admin"));

		setUpShockACLResponse(
				cloned, id, "admin", list("admin", "whee", "whoo"), list("bing", "bong"));
		
		h.processIds();
		
		verify(cloned).updateToken(new AuthToken("token", "user"));
		verify(adminCli, times(1)).removeFromNodeAcl(
				new ShockNodeId(id), Arrays.asList("whee", "whoo"), ShockACLType.WRITE);
		verify(adminCli, times(1)).removeFromNodeAcl(
				new ShockNodeId(id), Arrays.asList("bing", "bong"), ShockACLType.DELETE);
		verify(adminCli, times(0)).addToNodeAcl(any(), any(), any());
	}

	private SimpleRemappedId toSRD(final String id) {
		return new SimpleRemappedId(id);
	}

	// ugh, the shock client needs to be way easier to test with.
	private void setUpShockOwnResponse(
			final BasicShockClient client,
			final String shockID,
			final String owner,
			final List<String> writeUsers,
			final List<String> deleteUsers)
			throws Exception {
		final ShockACL acl = mock(ShockACL.class);
		when(client.addToNodeAcl(
				new ShockNodeId(shockID), Arrays.asList(owner), ShockACLType.OWNER))
				.thenReturn(acl);
		setUpShockACLMock(acl, owner, writeUsers, deleteUsers);
	}

	private void setUpShockACLMock(
			final ShockACL aclmock,
			final String owner,
			final List<String> writeUsers,
			final List<String> deleteUsers) {
		final ShockUserId shockUser = mock(ShockUserId.class);
		when(shockUser.getUsername()).thenReturn(owner);
		when(aclmock.getOwner()).thenReturn(shockUser);
		// you can't set up a mock inside .thenReturn()
		final List<ShockUserId> writes = setUpACL(writeUsers);
		when(aclmock.getWrite()).thenReturn(writes);
		final List<ShockUserId> deleted = setUpACL(deleteUsers);
		when(aclmock.getDelete()).thenReturn(deleted);
	}

	// ugh, the shock client needs to be way easier to test with.
	private void setUpShockACLResponse(
			final BasicShockClient client,
			final String shockID,
			final String owner,
			final List<String> writeUsers,
			final List<String> deleteUsers)
			throws Exception {
		final ShockACL acl = mock(ShockACL.class);
		when(client.getACLs(new ShockNodeId(shockID))).thenReturn(acl);
		setUpShockACLMock(acl, owner, writeUsers, deleteUsers);
	}

	private List<ShockUserId> setUpACL(final List<String> users) {
		return users.stream().map(u -> {
			final ShockUserId uid = mock(ShockUserId.class);
			when(uid.getUsername()).thenReturn(u);
			return uid;
		}).collect(Collectors.toList());
	}
	
	@Test
	public void addIdImplFailNoClient() throws Exception {
		final IdReferenceHandler<Long> h = new BytestreamIdHandlerFactory(null, null)
				.createHandler(Long.class, new AuthToken("token", "user"));
		
		addIdImplFail(h, 4L, "i", Arrays.asList("foo", "bar"), new IdReferenceException(
				"Found bytestream id i. There is no connection configured for bytestream " +
				"IDs and so objects containing bytestream IDs cannot be processed.",
				new IdReferenceType("bytestream"), 4L, "i", null, null));
	}
	
	@Test
	public void addIdImplFailBadID() throws Exception {
		final BasicShockClient cli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferenceHandler<String> h = new BytestreamIdHandlerFactory(cli, cloner)
				.createHandler(String.class, new AuthToken("token", "user"));
		
		addIdImplFail(h, "foo", "id", Arrays.asList("foo", "bat"), new IdReferenceException(
				"Illegal bytestream ID: id",
				new IdReferenceType("bytestream"), "foo", "id", null, null));
	}
	
	private <T> void addIdImplFail(
			final IdReferenceHandler<T> h,
			final T associatedObject,
			final String id,
			final List<String> attributes,
			final IdReferenceException expected)
			throws Exception {
		
		try {
			h.addId(associatedObject, id, attributes);
			fail("expected exception");
		} catch (IdReferenceException got) {
			TestCommon.assertExceptionCorrect(got, expected);
			assertThat("incorrect assobj", got.getAssociatedObject(),
					is(expected.getAssociatedObject()));
			assertThat("incorrect id", got.getId(), is(expected.getId()));
			assertThat("incorrect id ref", got.getIdReference(), is(expected.getIdReference()));
			assertThat("incorrect id attribs", got.getIdAttributes(),
					is(expected.getIdAttributes()));
			assertThat("incorrect id type", got.getIdType(), is(expected.getIdType()));
		}
	}
	
	@Test
	public void processIDsFailOnCloneIOException() throws Exception {
		processIDsFailOnClone(
				new IOException("crap"),
				new IdReferenceHandlerException("Error contacting bytestream storage to " +
						"validate IDs: crap",
						new IdReferenceType("bytestream"), null));
	}
	
	@Test
	public void processIDsFailOnCloneInvalidURLException() throws Exception {
		processIDsFailOnClone(
				new InvalidShockUrlException("poop"),
				new IdReferenceHandlerException("Error contacting bytestream storage to " +
						"validate IDs: poop",
						new IdReferenceType("bytestream"), null));
	}

	private void processIDsFailOnClone(
			final Exception thrown,
			final IdReferenceHandlerException expected)
			throws Exception {
		final BasicShockClient adminCli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferenceHandler<String> h = new BytestreamIdHandlerFactory(adminCli, cloner)
				.createHandler(String.class, new AuthToken("token", "user"));
		
		final String id = UUID.randomUUID().toString();
		
		h.addId("foo", id, null);
		
		when(cloner.clone(adminCli)).thenThrow(thrown);
		
		processIDsFail(h, expected);
	}
	
	@Test
	public void processIDsFailOnGetACLsAuthException() throws Exception {
		processIDsFailOnGetACLs(
				new ShockAuthorizationException(400, "booga booga booga"),
				new IdReferenceException(
						"User someuser cannot read bytestream node " +
						"51b68baa-ef40-4be1-a072-03814d61280e",
						new IdReferenceType("bytestream"), "foo",
						"51b68baa-ef40-4be1-a072-03814d61280e", null, null));
	}
	
	@Test
	public void processIDsFailOnGetACLsNoNodeException() throws Exception {
		processIDsFailOnGetACLs(
				new ShockNoNodeException(400, "hey nonny nonny"),
				new IdReferenceException(
						"Bytestream node 51b68baa-ef40-4be1-a072-03814d61280e does not exist",
						new IdReferenceType("bytestream"), "foo",
						"51b68baa-ef40-4be1-a072-03814d61280e", null, null));
	}
	
	@Test
	public void processIDsFailOnGetACLsIOException() throws Exception {
		processIDsFailOnGetACLs(
				new IOException("rats"),
				new IdReferenceHandlerException(
						"There was an IO problem while attempting to contact bytestream " +
						"storage to process IDs: rats",
						new IdReferenceType("bytestream"), null));
	}
	
	@Test
	public void processIDsFailOnGetACLsShockHTTPException() throws Exception {
		processIDsFailOnGetACLs(
				new ShockHttpException(400, "my pants are on fire"),
				new IdReferenceHandlerException(
						"Bytestream storage reported a problem while attempting to " +
						"process IDs: my pants are on fire",
						new IdReferenceType("bytestream"), null));
	}

	private void processIDsFailOnGetACLs(
			final Exception thrown,
			final IdReferenceHandlerException expected)
			throws Exception {
		final BasicShockClient adminCli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferenceHandler<String> h = new BytestreamIdHandlerFactory(adminCli, cloner)
				.createHandler(String.class, new AuthToken("token", "someuser"));
		
		final String id = "51b68baa-ef40-4be1-a072-03814d61280e";
		
		h.addId("foo", id, null);
		
		final BasicShockClient cloned = mock(BasicShockClient.class);
		when(cloner.clone(adminCli)).thenReturn(cloned);
		
		when(adminCli.getToken()).thenReturn(new AuthToken("token", "admin"));

		when(cloned.getACLs(new ShockNodeId(id))).thenThrow(thrown);
		
		processIDsFail(h, expected);
	}
	
	@Test
	public void processIDsFailNotOwner() throws Exception {
		final BasicShockClient adminCli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferenceHandler<String> h = new BytestreamIdHandlerFactory(adminCli, cloner)
				.createHandler(String.class, new AuthToken("token", "someuser"));
		
		final String id = "51b68baa-ef40-4be1-a072-03814d61280e";
		
		h.addId("foo", id, null);
		
		final BasicShockClient cloned = mock(BasicShockClient.class);
		when(cloner.clone(adminCli)).thenReturn(cloned);
		
		when(adminCli.getToken()).thenReturn(new AuthToken("token", "admin"));
		
		setUpShockACLResponse(cloned, id, "notadmin", MTL, MTL);
		
		processIDsFail(h, new IdReferenceException(
						"User someuser does not own bytestream node " +
						"51b68baa-ef40-4be1-a072-03814d61280e",
						new IdReferenceType("bytestream"), "foo",
						"51b68baa-ef40-4be1-a072-03814d61280e", null, null));
	}
	
	@Test
	public void processIDsFailOnRemoveACLIOException() throws Exception {
		processIDsFailOnRemoveACL(
				new IOException("poopy doopy"),
				new IdReferenceHandlerException(
						"There was an IO problem while attempting to contact bytestream " +
						"storage to process IDs: poopy doopy",
						new IdReferenceType("bytestream"), null),
				"writeUser",
				null);
	}
	
	@Test
	public void processIDsFailOnRemoveACLShockHTTPException() throws Exception {
		processIDsFailOnRemoveACL(
				new ShockHttpException(400, "plectrum"),
				new IdReferenceHandlerException(
						"Bytestream storage reported a problem while attempting to process " +
						"IDs: plectrum",
						new IdReferenceType("bytestream"), null),
				"deleteUser",
				null);
	}

	private void processIDsFailOnRemoveACL(
			final Exception thrown,
			final IdReferenceHandlerException expected,
			final String writeUser,
			final String deleteUser)
			throws Exception {
		final BasicShockClient adminCli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferenceHandler<String> h = new BytestreamIdHandlerFactory(adminCli, cloner)
				.createHandler(String.class, new AuthToken("token", "someuser"));
		
		final String id = "51b68baa-ef40-4be1-a072-03814d61280e";
		
		h.addId("foo", id, null);
		
		final BasicShockClient cloned = mock(BasicShockClient.class);
		when(cloner.clone(adminCli)).thenReturn(cloned);
		
		when(adminCli.getToken()).thenReturn(new AuthToken("token", "admin"));
		
		setUpShockACLResponse(cloned, id, "admin", listOrEmpty(writeUser), listOrEmpty(deleteUser));

		when(adminCli.removeFromNodeAcl(
				new ShockNodeId(id), listOrEmpty(writeUser), ShockACLType.WRITE))
				.thenThrow(thrown);
		when(adminCli.removeFromNodeAcl(
				new ShockNodeId(id), listOrEmpty(deleteUser), ShockACLType.DELETE))
				.thenThrow(thrown);
		
		processIDsFail(h, expected);
	}
	
	private List<String> listOrEmpty(final String s) {
		if (s == null) {
			return MTL;
		}
		return Arrays.asList(s);
		
	}
	
	@Test
	public void processIDsFailOnOwnIOException() throws Exception {
		processIDsFailOnOwn(
				new IOException("poopy doopy"),
				new IdReferenceHandlerException(
						"There was an IO problem while attempting to contact bytestream " +
						"storage to alter nodes: poopy doopy",
						new IdReferenceType("bytestream"), null));
	}
	
	@Test
	public void processIDsFailOnShockHTTPException() throws Exception {
		processIDsFailOnOwn(
				new ShockHttpException(400, "plectrum"),
				new IdReferenceHandlerException(
						"Bytestream storage reported a problem while attempting to alter " +
						"nodes: plectrum",
						new IdReferenceType("bytestream"), null));
	}

	private void processIDsFailOnOwn(
			final Exception thrown,
			final IdReferenceHandlerException expected)
			throws Exception {
		final BasicShockClient adminCli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferenceHandler<String> h = new BytestreamIdHandlerFactory(adminCli, cloner)
				.createHandler(String.class, new AuthToken("token", "someuser"));
		
		final String id = "51b68baa-ef40-4be1-a072-03814d61280e";
		
		h.addId("foo", id, null);
		
		final BasicShockClient cloned = mock(BasicShockClient.class);
		when(cloner.clone(adminCli)).thenReturn(cloned);
		
		when(adminCli.getToken()).thenReturn(new AuthToken("token", "admin"));
		
		setUpShockACLResponse(cloned, id, "someuser", MTL, MTL);

		when(adminCli.addToNodeAcl(
				new ShockNodeId(id), Arrays.asList("admin"), ShockACLType.OWNER))
				.thenThrow(thrown);
		
		processIDsFail(h, expected);
	}
	
	private <T> void processIDsFail(
			final IdReferenceHandler<T> h,
			final IdReferenceHandlerException expected)
			throws Exception {
		try {
			h.processIds();
			fail("expected exception");
		} catch (IdReferenceHandlerException got) {
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
	
	@Test
	public void getRemappedIDFail() throws Exception {
		final BasicShockClient adminCli = mock(BasicShockClient.class);
		final BytestreamClientCloner cloner = mock(BytestreamClientCloner.class);
		
		final IdReferenceHandler<String> h = new BytestreamIdHandlerFactory(adminCli, cloner)
				.createHandler(String.class, new AuthToken("token", "user"));
		
		final String id = UUID.randomUUID().toString();
		
		h.addId("foo", id, null);
		
		final BasicShockClient cloned = mock(BasicShockClient.class);
		when(cloner.clone(adminCli)).thenReturn(cloned);
		
		when(adminCli.getToken()).thenReturn(new AuthToken("token", "admin"));

		setUpShockACLResponse(cloned, id, "admin", MTL, MTL);
		
		h.processIds();
		
		assertThat("incorrect id", h.getRemappedId(id), is(toSRD(id)));
		
		final String badID = UUID.randomUUID().toString();
		try {
			h.getRemappedId(badID);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NoSuchIdException(
					"No such ID contained in this mapper: " + badID));
		}
	}

}