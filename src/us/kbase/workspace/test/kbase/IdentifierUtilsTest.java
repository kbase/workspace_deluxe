package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;

import us.kbase.common.test.TestCommon;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.kbase.KBaseIdentifierFactory;

public class IdentifierUtilsTest {
	
	/* Note - doesn't test subobject identifiers, should be fully tested in
	 * the JSONRPCLayer tests and they're deprecated.
	 */
	
	private void expectFailProcessWorkspaceIdentifier(
			final WorkspaceIdentity wsi,
			final Exception exp) {
		try {
			KBaseIdentifierFactory.processWorkspaceIdentifier(wsi);
			fail("Expected exception");
		} catch (Exception e) {
			TestCommon.assertExceptionCorrect(e, exp);
		}
	}
	
	private void expectFailProcessWorkspaceIdentifier(
			final String name,
			final Long id,
			final Exception exp) {
		try {
			KBaseIdentifierFactory.processWorkspaceIdentifier(name, id);
			fail("Expected exception");
		} catch (Exception e) {
			TestCommon.assertExceptionCorrect(e, exp);
		}
	}
	
	private void expectSuccessProcessWorkspaceIdentity(
			final WorkspaceIdentity wsi,
			final String name,
			final Long id,
			final String idstring) throws Exception {
		WorkspaceIdentifier pwsi = KBaseIdentifierFactory
				.processWorkspaceIdentifier(wsi);
		assertThat("incorrect wsi name", pwsi.getName(), is(name));
		assertThat("incorrect wsi id", pwsi.getId(), is(id));
		assertThat("incorrect id string", pwsi.getIdentifierString(),
				is(idstring));
		pwsi = KBaseIdentifierFactory.processWorkspaceIdentifier(
				wsi.getWorkspace(), wsi.getId());
		assertThat("incorrect wsi name", pwsi.getName(), is(name));
		assertThat("incorrect wsi id", pwsi.getId(), is(id));
		assertThat("incorrect id string", pwsi.getIdentifierString(),
				is(idstring));
	}

	@Test
	public void failNullWorkspaceIdentity() throws Exception {
		expectFailProcessWorkspaceIdentifier(null, new NullPointerException(
				"WorkspaceIdentifier cannot be null"));
	}
	
	@Test
	public void failWorkspaceIdentityAddlArgs() throws Exception {
		final WorkspaceIdentity wsi = new WorkspaceIdentity();
		wsi.setAdditionalProperties("foo", "bar");
		expectFailProcessWorkspaceIdentifier(wsi, new IllegalArgumentException(
				"Unexpected arguments in WorkspaceIdentity: foo"));
	}
	
	@Test
	public void failWorkspaceIdAndName() throws Exception {
		expectFailProcessWorkspaceIdentifier("foo", 2L,
				new IllegalArgumentException(
				"Must provide one and only one of workspace name (was: foo) " +
				"or id (was: 2)"));
		
	}
	
	@Test
	public void failWorkspaceIdNorName() throws Exception {
		expectFailProcessWorkspaceIdentifier(null, null,
				new IllegalArgumentException(
				"Must provide one and only one of workspace name " +
				"(was: null) or id (was: null)"));
		
	}
	
	@Test
	public void successWorkspaceIdentityIdString() throws Exception {
		final WorkspaceIdentity wsi = new WorkspaceIdentity()
				.withWorkspace("foobar");
		expectSuccessProcessWorkspaceIdentity(wsi, "foobar", null, "foobar");
	}

	@Test
	public void successWorkspaceIdentityIdLong() throws Exception {
		final WorkspaceIdentity wsi = new WorkspaceIdentity().withId(2L);
		expectSuccessProcessWorkspaceIdentity(wsi, null, 2L, "2");
	}
	
	@Test
	public void successWorkspaceIdentityKBID() throws Exception {
		final WorkspaceIdentity wsi = new WorkspaceIdentity()
				.withWorkspace("kb|ws.2");
		expectSuccessProcessWorkspaceIdentity(wsi, null, 2L, "2");
	}
	
	private void expectFailProcessObjectIdentifier(
			final ObjectIdentity oi,
			final Exception exp) {
		try {
			KBaseIdentifierFactory.processObjectIdentifier(oi);
			fail("Expected exception");
		} catch (Exception e) {
			TestCommon.assertExceptionCorrect(e, exp);
		}
	}
	

	private void expectSuccessProcessObjectIdentity(
			final String wsname,
			final Long wsid,
			final String name,
			final Long id,
			final Long ver,
			final String ref,
			final String idstring,
			final boolean isAbsolute)
			throws Exception {
		final ObjectIdentity oi = new ObjectIdentity().withWorkspace(wsname)
				.withWsid(wsid).withName(name).withObjid(id).withVer(ver);
		expectSuccessProcessObjectIdentity(oi, wsname, wsid, name, id, ver,
				ref, idstring, isAbsolute);
		
		final ObjectIdentity refoi = new ObjectIdentity().withRef(ref);
		expectSuccessProcessObjectIdentity(refoi, wsname, wsid, name, id, ver,
				ref, idstring, isAbsolute);
		
		expectSuccessProcessObjectIdentity(wsname, wsid, name, id, ver, wsname,
				wsid, name, id, ver, ref, idstring, isAbsolute);
		
		expectSuccessProcessObjectIdentity(ref, wsname, wsid, name, id, ver,
				ref, idstring, isAbsolute);
	}
	
	private void expectSuccessProcessObjectIdentity(
			final String wsnameprovided,
			final Long wsidprovided,
			final String nameprovided,
			final Long idprovided,
			final Long verprovided,
			final String wsname,
			final Long wsid,
			final String name,
			final Long id,
			final Long ver,
			final String refstring,
			final String idstring,
			final boolean isAbsolute)
			throws Exception {
		ObjectIdentifier poi = KBaseIdentifierFactory
				.processObjectIdentifier(wsnameprovided, wsidprovided,
						nameprovided, idprovided, verprovided);
		checkObjectIdentifier(poi, wsname, wsid, name, id, ver, refstring,
				idstring, isAbsolute);
	}
	
	private void expectSuccessProcessObjectIdentity(
			final String ref,
			final String wsname,
			final Long wsid,
			final String name,
			final Long id,
			final Long ver,
			final String refstring,
			final String idstring,
			final boolean isAbsolute)
			throws Exception {
		ObjectIdentifier poi = KBaseIdentifierFactory
				.processObjectReference(ref);
		checkObjectIdentifier(poi, wsname, wsid, name, id, ver, refstring,
				idstring, isAbsolute);
	}
	
	private void expectSuccessProcessObjectIdentity(
			final ObjectIdentity oi,
			final String wsname,
			final Long wsid,
			final String name,
			final Long id,
			final Long ver,
			final String refstring,
			final String idstring,
			final boolean isAbsolute)
			throws Exception {
		ObjectIdentifier poi = KBaseIdentifierFactory
				.processObjectIdentifier(oi);
		checkObjectIdentifier(poi, wsname, wsid, name, id, ver, refstring,
				idstring, isAbsolute);
	}

	private void checkObjectIdentifier(
			final ObjectIdentifier poi,
			final String wsname,
			final Long wsid,
			final String name,
			final Long id,
			final Long ver,
			final String refstring,
			final String idstring,
			final boolean isAbsolute) {
		assertThat("incorrect oi ws name",
				poi.getWorkspaceIdentifier().getName(), is(wsname));
		assertThat("incorrect oi ws id", poi.getWorkspaceIdentifier().getId(),
				is(wsid));
		assertThat("incorrect oi name", poi.getName(), is(name));
		assertThat("incorrect oi id", poi.getId(), is(id));
		assertThat("incorrect oi ver", poi.getVersion(), is(ver == null ?
				null : ver.intValue()));
		
		assertThat("incorrect oi ref", poi.getReferenceString(),
				is(refstring));
		assertThat("incorrect oi id string", poi.getIdentifierString(),
				is(idstring));
		assertThat("incorrect oi isabsolute", poi.isAbsolute(),
				is(isAbsolute));
	}
	
	@Test
	public void failObjectIdentityNull() throws Exception {
		expectFailProcessObjectIdentifier(null, new NullPointerException(
				"ObjectIdentity cannot be null"));
	}
	
	@Test
	public void failObjectIdentityAddlArgs() throws Exception {
		final ObjectIdentity oi = new ObjectIdentity();
		oi.setAdditionalProperties("foo", "bar");
		expectFailProcessObjectIdentifier(oi, new IllegalArgumentException(
				"Unexpected arguments in ObjectIdentity: foo"));
	}
	
	@Test
	public void failObjectIdentityRefAndMore() throws Exception {
		final ObjectIdentity oi = new ObjectIdentity().withName("foo")
				.withObjid(1L).withRef("yeah/whoo").withVer(2L)
				.withWorkspace("baz").withWsid(3L);
		expectFailProcessObjectIdentifier(oi, new IllegalArgumentException(
				"Object reference yeah/whoo provided; cannot provide any " +
				"other means of identifying an object. Workspace: baz " +
				"Workspace id: 3 Object name: foo Object id: 1 Version: 2"));
	}
	
	@Test
	public void failObjectIdentityLargeVer() throws Exception {
		final Long ver = (long) Integer.MAX_VALUE + 1L;
		final IllegalArgumentException e = new IllegalArgumentException(
				"Maximum object version is " + Integer.MAX_VALUE);
		final ObjectIdentity oi = new ObjectIdentity().withWorkspace("baz")
				.withName("foo").withVer(ver);
		expectFailProcessObjectIdentifier(oi, e);
		try {
			KBaseIdentifierFactory.processObjectIdentifier(
					"baz", null, "foo", null, ver);
			fail("expected exception");
		} catch (IllegalArgumentException iae) {
			assertThat("incorrect exception message", iae.getMessage(),
					is(e.getMessage()));
		}
	}
	
	@Test
	public void failNullObjectRef() throws Exception {
		try {
			KBaseIdentifierFactory.processObjectReference(null);
			fail("expected exception");
		} catch (NullPointerException e) {
			assertThat("incorrect exception", e.getMessage(),
					is("Reference string cannot be null"));
		}
	}
	
	@Test
	public void successObjectIdentityStringIDs() throws Exception {
		final String wsname = "foo";
		final Long wsid = null;
		final String name = "bar";
		final Long id = null;
		final Long ver = null;
		final String ref = "foo/bar";
		final String idstring = "bar";
		final boolean isAbsolute = false;
		expectSuccessProcessObjectIdentity(wsname, wsid, name, id, ver,
				ref, idstring, isAbsolute);
	}

	@Test
	public void successObjectIdentityLongIDs() throws Exception {
		final String wsname = null;
		final Long wsid = 3L;
		final String name = null;
		final Long id = 5L;
		final Long ver = null;
		final String ref = "3/5";
		final String idstring = "5";
		final boolean isAbsolute = false;
		expectSuccessProcessObjectIdentity(wsname, wsid, name, id, ver,
				ref, idstring, isAbsolute);
	}
	
	@Test
	public void successObjectIdentityWithVersAbsolute() throws Exception {
		final String wsname = null;
		final Long wsid = 3L;
		final String name = null;
		final Long id = 5L;
		final Long ver = 42L;
		final String ref = "3/5/42";
		final String idstring = "5";
		final boolean isAbsolute = true;
		expectSuccessProcessObjectIdentity(wsname, wsid, name, id, ver,
				ref, idstring, isAbsolute);
	}
	
	@Test
	public void successObjectIdentityWithVers() throws Exception {
		final String wsname = "foo";
		final Long wsid = null;
		final String name = null;
		final Long id = 5L;
		final Long ver = 42L;
		final String ref = "foo/5/42";
		final String idstring = "5";
		final boolean isAbsolute = false;
		expectSuccessProcessObjectIdentity(wsname, wsid, name, id, ver,
				ref, idstring, isAbsolute);
	}
	
	@Test
	public void successObjectIdentityWithKBRef() throws Exception {
		final String ref = "kb|ws.4.obj.3";
		
		
		final ObjectIdentity refoi = new ObjectIdentity().withRef(ref);
		expectSuccessProcessObjectIdentity(refoi, null, 4L, null, 3L, null,
				"4/3", "3", false);
		
		expectSuccessProcessObjectIdentity(ref, null, 4L, null, 3L, null,
				"4/3", "3", false);
	}
	
	@Test
	public void successObjectIdentityWithKBRefAndVer() throws Exception {
		final String ref = "kb|ws.4.obj.3.ver.2";
		
		
		final ObjectIdentity refoi = new ObjectIdentity().withRef(ref);
		expectSuccessProcessObjectIdentity(refoi, null, 4L, null, 3L, 2L,
				"4/3/2", "3", true);
		
		expectSuccessProcessObjectIdentity(refoi, null, 4L, null, 3L, 2L,
				"4/3/2", "3", true);
	}
	
	//TODO NOW finish testing verifyRefOnly
}
