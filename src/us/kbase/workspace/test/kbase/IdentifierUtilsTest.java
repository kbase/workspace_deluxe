package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

import us.kbase.common.test.TestCommon;
import us.kbase.typedobj.core.ObjectPaths;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSpecification;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.database.ObjIDWithChainAndSubset;
import us.kbase.workspace.database.ObjectIDWithRefPath;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.kbase.IdentifierUtils;

public class IdentifierUtilsTest {
	
	/* Note - doesn't test subobject identifiers, should be fully tested in
	 * the JSONRPCLayer tests and they're deprecated.
	 */
	
	private void expectFailProcessWorkspaceIdentifier(
			final WorkspaceIdentity wsi,
			final Exception exp) {
		try {
			IdentifierUtils.processWorkspaceIdentifier(wsi);
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
			IdentifierUtils.processWorkspaceIdentifier(name, id);
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
		WorkspaceIdentifier pwsi = IdentifierUtils
				.processWorkspaceIdentifier(wsi);
		assertThat("incorrect wsi name", pwsi.getName(), is(name));
		assertThat("incorrect wsi id", pwsi.getId(), is(id));
		assertThat("incorrect id string", pwsi.getIdentifierString(),
				is(idstring));
		pwsi = IdentifierUtils.processWorkspaceIdentifier(
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
			IdentifierUtils.processObjectIdentifier(oi);
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
		ObjectIdentifier poi = IdentifierUtils
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
		ObjectIdentifier poi = IdentifierUtils
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
		ObjectIdentifier poi = IdentifierUtils
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
		final ObjectIdentity oi = new ObjectIdentity().withRef("yeah/boyeeee");
		oi.withName("foo");
		expectFailProcessObjectIdentifier(oi, new IllegalArgumentException(
				"Object reference yeah/boyeeee provided; cannot provide any " +
				"other means of identifying an object. Object name: foo"));
		
		oi.withName(null);
		oi.withObjid(1L);
		expectFailProcessObjectIdentifier(oi, new IllegalArgumentException(
				"Object reference yeah/boyeeee provided; cannot provide any " +
				"other means of identifying an object. Object id: 1"));
		
		oi.withObjid(null);
		oi.withVer(1L);
		expectFailProcessObjectIdentifier(oi, new IllegalArgumentException(
				"Object reference yeah/boyeeee provided; cannot provide any " +
				"other means of identifying an object. Version: 1"));
		
		oi.withVer(null);
		oi.withWorkspace("foo");
		expectFailProcessObjectIdentifier(oi, new IllegalArgumentException(
				"Object reference yeah/boyeeee provided; cannot provide any " +
				"other means of identifying an object. Workspace: foo"));
		
		oi.withWorkspace(null);
		oi.withWsid(1L);
		expectFailProcessObjectIdentifier(oi, new IllegalArgumentException(
				"Object reference yeah/boyeeee provided; cannot provide any " +
				"other means of identifying an object. Workspace id: 1"));
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
			IdentifierUtils.processObjectIdentifier(
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
			IdentifierUtils.processObjectReference(null);
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
	
	private void expectFailProcessObjectIdentifiers(
			final List<ObjectIdentity> ois,
			final Exception exp) {
		try {
			IdentifierUtils.processObjectIdentifiers(ois);
			fail("Expected exception");
		} catch (Exception e) {
			TestCommon.assertExceptionCorrect(e, exp);
		}
	}
	
	@Test
	public void failObjectIdentityListNull() throws Exception {
		expectFailProcessObjectIdentifiers(null, new NullPointerException(
				"The object identifier list cannot be null"));
	}
	
	@Test
	public void failObjectIdentityListEmpty() throws Exception {
		expectFailProcessObjectIdentifiers(new LinkedList<ObjectIdentity>(),
				new IllegalArgumentException(
						"No object identifiers provided"));
	}
	
	@Test
	public void failObjectIdentityListNullEntry() throws Exception {
		final List<ObjectIdentity> ois = new LinkedList<>();
		ois.add(new ObjectIdentity().withRef("foo/bar"));
		ois.add(null);
		ois.add(new ObjectIdentity().withRef("baz/bat"));
		expectFailProcessObjectIdentifiers(ois, new IllegalArgumentException(
				"Error on ObjectIdentity #2: ObjectIdentity cannot be null"));
	}
	
	@Test
	public void failObjectIdentityListBadEntry() throws Exception {
		final List<ObjectIdentity> ois = new LinkedList<>();
		ois.add(new ObjectIdentity().withRef("foo/bar"));
		ois.add(new ObjectIdentity().withRef("baz/bat"));
		ois.add(new ObjectIdentity().withRef("foo/1").withVer(1L));
		expectFailProcessObjectIdentifiers(ois, new IllegalArgumentException(
				"Error on ObjectIdentity #3: Object reference foo/1 " +
				"provided; cannot provide any other means of identifying an " +
				"object. Version: 1"));
	}
	
	@Test
	public void successObjectIdentityList() throws Exception {
		final List<ObjectIdentity> ois = new LinkedList<>();
		ois.add(new ObjectIdentity().withRef("foo/bar"));
		ois.add(new ObjectIdentity().withWorkspace("baz").withName("bat"));
		ois.add(new ObjectIdentity().withWsid(1L).withObjid(2L).withVer(3L));
		final List<ObjectIdentifier> pois = IdentifierUtils
				.processObjectIdentifiers(ois);
		assertThat("incorrect object count", pois.size(), is(3));
		checkObjectIdentifier(pois.get(0), "foo", null, "bar", null, null,
				"foo/bar", "bar", false);
		checkObjectIdentifier(pois.get(1), "baz", null, "bat", null, null,
				"baz/bat", "bat", false);
		checkObjectIdentifier(pois.get(2), null, 1L, null, 2L, 3L,
				"1/2/3", "2", true);
	}
	
	private void expectFailProcessObjectSpecifications(
			final List<ObjectSpecification> oss,
			final Exception exp) {
		try {
			IdentifierUtils.processObjectSpecifications(oss);
			fail("Expected exception");
		} catch (Exception e) {
			TestCommon.assertExceptionCorrect(e, exp);
		}
	}
	
	private void expectSuccessProcessSimpleObjectSpecification(
			final ObjectSpecification os,
			final String wsname,
			final Long wsid,
			final String name,
			final Long id,
			final Long ver,
			final String refstring,
			final String idstring,
			final boolean isAbsolute) {
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(os);
		final List<ObjectIdentifier> ret = IdentifierUtils
				.processObjectSpecifications(oss);
		assertThat("incorrect return size", ret.size(), is(1));
		final ObjectIdentifier oi = ret.get(0);
		assertThat("incorrect class", oi instanceof ObjectIDWithRefPath,
				is(false));
		assertThat("incorrect class", oi instanceof ObjIDWithChainAndSubset,
				is(false));
		
		checkObjectIdentifier(oi, wsname, wsid, name, id, ver, refstring,
				idstring, isAbsolute);
	}
	
	@Test
	public void failObjectSpecListNull() throws Exception {
		expectFailProcessObjectSpecifications(null, new NullPointerException(
				"The object specification list cannot be null"));
	}
	
	@Test
	public void failObjectSpecListEmpty() throws Exception {
		expectFailProcessObjectSpecifications(
				new LinkedList<ObjectSpecification>(),
				new IllegalArgumentException(
						"No object specifications provided"));
	}
	
	@Test
	public void failObjectSpecNullSpec() throws Exception {
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(new ObjectSpecification().withRef("3/4"));
		oss.add(null);
		oss.add(new ObjectSpecification().withWorkspace("foo")
				.withName("bar"));
		expectFailProcessObjectSpecifications(oss, new NullPointerException(
				"Objects in the object specification list cannot be null"));
	}
	
	@Test
	public void failObjectSpecAdditionalArgs() throws Exception {
		final List<ObjectSpecification> oss = new LinkedList<>();
		final ObjectSpecification os = new ObjectSpecification()
				.withWsid(1L).withObjid(2L);
		os.setAdditionalProperties("foo", "bar");
		oss.add(os);
		oss.add(new ObjectSpecification().withRef("3/4"));
		oss.add(new ObjectSpecification().withWorkspace("foo")
				.withName("bar"));
		expectFailProcessObjectSpecifications(oss,
				new IllegalArgumentException("Error on ObjectSpecification " +
				"#1: Unexpected arguments in ObjectSpecification: foo"));
		
	}
	
	@Test
	public void failObjectSpecObjPathNullRef() throws Exception {
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(new ObjectSpecification().withRef("3/4"));
		final ObjectSpecification os = new ObjectSpecification()
				.withWsid(1L).withObjid(2L)
				.withObjPath(Arrays.asList(new ObjectIdentity()
						.withRef("foo/bar"), null));
		oss.add(new ObjectSpecification().withWorkspace("foo")
				.withName("bar"));
		oss.add(os);
		expectFailProcessObjectSpecifications(oss,
				new IllegalArgumentException(
						"Error on ObjectSpecification #3: Invalid object id " +
						"at position #3: ObjectIdentity cannot be null"));
	}
	
	@Test
	public void failObjectSpecToObjPathBadRef() throws Exception {
		final List<ObjectSpecification> oss = new LinkedList<>();
		final ObjectSpecification os = new ObjectSpecification()
				.withWsid(1L).withObjid(2L)
				.withToObjPath(Arrays.asList(
						new ObjectIdentity().withObjid(1L),
						new ObjectIdentity().withName("foo").withWsid(2L)));
		oss.add(os);
		oss.add(new ObjectSpecification().withRef("3/4"));
		oss.add(new ObjectSpecification().withWorkspace("foo")
				.withName("bar"));
		expectFailProcessObjectSpecifications(oss,
				new IllegalArgumentException(
						"Error on ObjectSpecification #1: Invalid object id " +
						"at position #1: Must provide one and only one of " +
						"workspace name (was: null) or id (was: null)"));
	}
	
	@Test
	public void failObjectSpecObjRefPathBadRef() throws Exception {
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(new ObjectSpecification().withRef("3/4"));
		final ObjectSpecification os = new ObjectSpecification()
				.withWsid(1L).withObjid(2L)
				.withObjRefPath(Arrays.asList("foo/bar", "baz"));
		oss.add(os);
		oss.add(new ObjectSpecification().withWorkspace("foo")
				.withName("bar"));
		expectFailProcessObjectSpecifications(oss,
				new IllegalArgumentException(
						"Error on ObjectSpecification #2: Invalid object " +
						"reference (baz) at position #3: Illegal number of " +
						"separators / in object reference baz"));
	}
	
	@Test
	public void failObjectSpecToObjRefPathNullRef() throws Exception {
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(new ObjectSpecification().withRef("3/4"));
		final ObjectSpecification os = new ObjectSpecification()
				.withWsid(1L).withObjid(2L)
				.withToObjRefPath(Arrays.asList("foo/bar", (String) null));
		oss.add(os);
		oss.add(new ObjectSpecification().withWorkspace("foo")
				.withName("bar"));
		expectFailProcessObjectSpecifications(oss,
				new IllegalArgumentException(
						"Error on ObjectSpecification #2: Invalid object " +
						"reference (null) at position #2: Reference string " +
						"cannot be null"));
	}
	
	@Test
	public void failObjectSpecStringRefPathWithOtherRefPaths()
			throws Exception {
		final ObjectIdentity oi = new ObjectIdentity().withName("foo")
				.withWsid(1L);
		final IllegalArgumentException exp = new IllegalArgumentException(
				"Error on ObjectSpecification #1: Only one of the 5 options " +
				"for specifying an object reference path is allowed");
		
		//process object spec mutates the spec
		List<ObjectSpecification> oss = makeOSforSpecificTest();
		oss.get(0).withObjPath(Arrays.asList(oi));
		expectFailProcessObjectSpecifications(oss, exp);
		
		oss = makeOSforSpecificTest();
		oss.get(0).withToObjPath(Arrays.asList(oi));
		expectFailProcessObjectSpecifications(oss, exp);
		
		oss = makeOSforSpecificTest();
		oss.get(0).withObjRefPath(Arrays.asList("foo/bar"));
		expectFailProcessObjectSpecifications(oss, exp);

		oss = makeOSforSpecificTest();
		oss.get(0).withToObjRefPath(Arrays.asList("foo/bar"));
		expectFailProcessObjectSpecifications(oss, exp);
	}
	
	@Test
	public void failObjectSpecMultipleRefPaths() throws Exception {
		// doesn't tests string ref path, tested in another test
		final ObjectIdentity oi = new ObjectIdentity().withName("foo")
				.withWsid(1L);
		final IllegalArgumentException exp = new IllegalArgumentException(
				"Error on ObjectSpecification #2: Only one of the 5 options " +
				"for specifying an object reference path is allowed");
		
		final ObjectSpecification os = new ObjectSpecification()
				.withObjid(1L).withWorkspace("foo")
				.withObjPath(new LinkedList<ObjectIdentity>())
				.withToObjPath(new LinkedList<ObjectIdentity>())
				.withObjRefPath(new LinkedList<String>())
				.withToObjRefPath(new LinkedList<String>());
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(new ObjectSpecification().withWsid(1L).withObjid(2L));
		oss.add(os);
		
		os.withObjPath(Arrays.asList(oi))
			.withObjRefPath(Arrays.asList("ref"));
		expectFailProcessObjectSpecifications(oss, exp);

		os.withObjPath(new LinkedList<ObjectIdentity>())
			.withToObjPath(Arrays.asList(oi));
		expectFailProcessObjectSpecifications(oss, exp);

		os.withToObjPath(new LinkedList<ObjectIdentity>())
			.withToObjRefPath(Arrays.asList("ref"));
		expectFailProcessObjectSpecifications(oss, exp);
	
		os.withToObjRefPath(new LinkedList<String>())
			.withObjPath(Arrays.asList(oi));
		expectFailProcessObjectSpecifications(oss, exp);
	}
	
	public List<ObjectSpecification> makeOSforSpecificTest() {
		final ObjectSpecification os = new ObjectSpecification()
				.withRef("foo/bar \n; \n baz/bat ;whee/whoa");
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(os);
		return oss;
	}
	
	@Test
	public void successObjectSpecRefWithSemiColon() throws Exception {
		expectSuccessProcessSimpleObjectSpecification(
				new ObjectSpecification().withRef("foo/bar \n; \n"),
				"foo", null, "bar", null, null, "foo/bar", "bar", false);
	}
	
	@Test
	public void successObjectSpecNoRef() throws Exception {
		expectSuccessProcessSimpleObjectSpecification(new ObjectSpecification()
				.withWsid(1L).withObjid(2L).withVer(3L),
				null, 1L, null, 2L, 3L, "1/2/3", "2", true);
	}
	
	@Test
	public void successObjectEmptyObjectPaths() throws Exception {
		// the important check is the class type check 
		expectSuccessProcessSimpleObjectSpecification(new ObjectSpecification()
				.withWsid(1L).withObjid(2L).withVer(3L)
				.withIncluded(new LinkedList<String>()),
				null, 1L, null, 2L, 3L, "1/2/3", "2", true);
	}
	
	@Test
	public void successObjectSpecRefStringPathEmptyLists() throws Exception {
		//also tests that empty lists are ignored for the other path types
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(new ObjectSpecification()
				.withObjPath(new LinkedList<ObjectIdentity>())
				.withToObjPath(new LinkedList<ObjectIdentity>())
				.withObjRefPath(new LinkedList<String>())
				.withToObjRefPath(new LinkedList<String>())
				.withRef("foo/bar \n; \n baz/bat ;whee/whoa"));
		final List<ObjectIdentifier> ret = IdentifierUtils
				.processObjectSpecifications(oss);
		assertThat("incorrect return size", ret.size(), is(1));
		final ObjectIdentifier oi = ret.get(0);
		assertThat("incorrect class", oi instanceof ObjectIDWithRefPath,
				is(true));
		assertThat("incorrect class", oi instanceof ObjIDWithChainAndSubset,
				is(false));
		
		checkObjectIdentifier(oi, "foo", null, "bar", null, null, "foo/bar",
				"bar", false);
		
		final ObjectIDWithRefPath oirc = (ObjectIDWithRefPath) oi;
		assertThat("incorrect hasChain()", oirc.hasChain(), is(true));
		final List<ObjectIdentifier> chain = oirc.getRefPath();
		assertThat("incorrect chain size", chain.size(), is(2));
		
		checkObjectIdentifier(chain.get(0), "baz", null, "bat", null, null,
				"baz/bat", "bat", false);
		checkObjectIdentifier(chain.get(1), "whee", null, "whoa", null, null,
				"whee/whoa", "whoa", false);
	}
	
	@Test
	public void successObjectSpecRefStringPathNullLists() throws Exception {
		//also tests that null lists are ignored for the other path types
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(new ObjectSpecification()
				.withRef("foo/bar \n; \n baz/bat ;whee/whoa"));
		final List<ObjectIdentifier> ret = IdentifierUtils
				.processObjectSpecifications(oss);
		assertThat("incorrect return size", ret.size(), is(1));
		final ObjectIdentifier oi = ret.get(0);
		assertThat("incorrect class", oi instanceof ObjectIDWithRefPath,
				is(true));
		assertThat("incorrect class", oi instanceof ObjIDWithChainAndSubset,
				is(false));
		
		checkObjectIdentifier(oi, "foo", null, "bar", null, null, "foo/bar",
				"bar", false);
		
		final ObjectIDWithRefPath oirc = (ObjectIDWithRefPath) oi;
		assertThat("incorrect hasChain()", oirc.hasChain(), is(true));
		final List<ObjectIdentifier> chain = oirc.getRefPath();
		assertThat("incorrect chain size", chain.size(), is(2));
		
		checkObjectIdentifier(chain.get(0), "baz", null, "bat", null, null,
				"baz/bat", "bat", false);
		checkObjectIdentifier(chain.get(1), "whee", null, "whoa", null, null,
				"whee/whoa", "whoa", false);
	}
	
	@Test
	public void sucessObjectSpecIncludedDefaultStrict() throws Exception {
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(new ObjectSpecification().withRef("foo/bar")
				.withIncluded(Arrays.asList("baz", "bat")));
		final List<ObjectIdentifier> ret = IdentifierUtils
				.processObjectSpecifications(oss);
		assertThat("incorrect return size", ret.size(), is(1));
		final ObjIDWithChainAndSubset oi =
				(ObjIDWithChainAndSubset) ret.get(0);
		
		checkObjectIdentifier(oi, "foo", null, "bar", null, null, "foo/bar",
				"bar", false);
		assertThat("incorrect hasChain()", oi.hasChain(), is(false));
		assertThat("has ref chain", oi.getRefPath(), is((List<ObjectIdentifier>)
				new LinkedList<ObjectIdentifier>()));
		final ObjectPaths op = oi.getPaths();
		final List<String> paths = new LinkedList<>();
		for (final String p: op) {
			paths.add(p);
		}
		assertThat("incorrect object paths", paths,
				is(Arrays.asList("baz", "bat")));
		assertThat("incorrect strict maps", op.isStrictMaps(), is(false));
		assertThat("incorrect strict arrays", op.isStrictArrays(), is(true));
	}
	
	@Test
	public void sucessObjectSpecIncludedFalseStrict() throws Exception {
		//also tests emtpy lists are ignored for paths
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(new ObjectSpecification().withRef("foo/bar")
				.withIncluded(Arrays.asList("whiz"))
				.withStrictArrays(0L)
				.withStrictMaps(0L)
				.withObjPath(new LinkedList<ObjectIdentity>())
				.withToObjPath(new LinkedList<ObjectIdentity>())
				.withObjRefPath(new LinkedList<String>())
				.withToObjRefPath(new LinkedList<String>()));
		final List<ObjectIdentifier> ret = IdentifierUtils
				.processObjectSpecifications(oss);
		assertThat("incorrect return size", ret.size(), is(1));
		final ObjIDWithChainAndSubset oi =
				(ObjIDWithChainAndSubset) ret.get(0);
		
		checkObjectIdentifier(oi, "foo", null, "bar", null, null, "foo/bar",
				"bar", false);
		assertThat("incorrect hasChain()", oi.hasChain(), is(false));
		assertThat("has ref chain", oi.getRefPath(), is((List<ObjectIdentifier>)
				new LinkedList<ObjectIdentifier>()));
		final ObjectPaths op = oi.getPaths();
		final List<String> paths = new LinkedList<>();
		for (final String p: op) {
			paths.add(p);
		}
		assertThat("incorrect object paths", paths,
				is(Arrays.asList("whiz")));
		assertThat("incorrect strict maps", op.isStrictMaps(), is(false));
		assertThat("incorrect strict arrays", op.isStrictArrays(), is(false));
	}
	
	@Test
	public void sucessObjectSpecIncludedTrueStrict() throws Exception {
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(new ObjectSpecification().withRef("foo/bar")
				.withIncluded(Arrays.asList("whiz", "towel", "bleah"))
				.withStrictArrays(1L)
				.withStrictMaps(1L));
		final List<ObjectIdentifier> ret = IdentifierUtils
				.processObjectSpecifications(oss);
		assertThat("incorrect return size", ret.size(), is(1));
		final ObjIDWithChainAndSubset oi =
				(ObjIDWithChainAndSubset) ret.get(0);
		
		checkObjectIdentifier(oi, "foo", null, "bar", null, null, "foo/bar",
				"bar", false);
		assertThat("incorrect hasChain()", oi.hasChain(), is(false));
		assertThat("has ref chain", oi.getRefPath(), is((List<ObjectIdentifier>)
				new LinkedList<ObjectIdentifier>()));
		final ObjectPaths op = oi.getPaths();
		final List<String> paths = new LinkedList<>();
		for (final String p: op) {
			paths.add(p);
		}
		assertThat("incorrect object paths", paths,
				is(Arrays.asList("whiz", "towel", "bleah")));
		assertThat("incorrect strict maps", op.isStrictMaps(), is(true));
		assertThat("incorrect strict arrays", op.isStrictArrays(), is(true));
	}
	
	@Test
	public void successObjectSpecRefsObjPathLen2() throws Exception {
		//also tests that empty lists are ignored for the other path types
		//also also tests ObjectPath + ref ObjPath
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(new ObjectSpecification().withWsid(3L).withObjid(2L)
				.withObjPath(Arrays.asList(new ObjectIdentity()
						.withName("whee").withWorkspace("whoo")))
				.withToObjPath(new LinkedList<ObjectIdentity>())
				.withObjRefPath(new LinkedList<String>())
				.withToObjRefPath(new LinkedList<String>())
				.withIncluded(Arrays.asList("bar")));
		final List<ObjectIdentifier> ret = IdentifierUtils
				.processObjectSpecifications(oss);
		assertThat("incorrect return size", ret.size(), is(1));
		final ObjIDWithChainAndSubset oi =
				(ObjIDWithChainAndSubset) ret.get(0);
		
		checkObjectIdentifier(oi, null, 3L, null, 2L, null, "3/2", "2", false);
		
		assertThat("incorrect hasChain()", oi.hasChain(), is(true));
		final ObjectPaths op = oi.getPaths();
		final List<String> paths = new LinkedList<>();
		for (final String p: op) {
			paths.add(p);
		}
		assertThat("incorrect object paths", paths,
				is(Arrays.asList("bar")));
		assertThat("incorrect strict maps", op.isStrictMaps(), is(false));
		assertThat("incorrect strict arrays", op.isStrictArrays(), is(true));
		
		assertThat("incorrect hasChain()", oi.hasChain(), is(true));
		final List<ObjectIdentifier> chain = oi.getRefPath();
		assertThat("incorrect chain size", chain.size(), is(1));
		
		checkObjectIdentifier(chain.get(0), "whoo", null, "whee", null, null,
				"whoo/whee", "whee", false);
	}
	
	@Test
	public void successObjectSpecRefsToObjPathLen2() throws Exception {
		//also tests that empty lists are ignored for the other path types
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(new ObjectSpecification().withWsid(3L).withObjid(2L)
				.withToObjPath(Arrays.asList(new ObjectIdentity()
						.withName("whee").withWorkspace("whoo")))
				.withObjPath(new LinkedList<ObjectIdentity>())
				.withObjRefPath(new LinkedList<String>())
				.withToObjRefPath(new LinkedList<String>()));
		final List<ObjectIdentifier> ret = IdentifierUtils
				.processObjectSpecifications(oss);
		assertThat("incorrect return size", ret.size(), is(1));

		final ObjectIdentifier oi = ret.get(0);
		assertThat("incorrect class", oi instanceof ObjectIDWithRefPath,
				is(true));
		assertThat("incorrect class", oi instanceof ObjIDWithChainAndSubset,
				is(false));
		
		checkObjectIdentifier(oi, "whoo", null, "whee", null, null,
				"whoo/whee", "whee", false);
		
		final ObjectIDWithRefPath oirc = (ObjectIDWithRefPath) oi;
		assertThat("incorrect hasChain()", oirc.hasChain(), is(true));
		final List<ObjectIdentifier> chain = oirc.getRefPath();
		assertThat("incorrect chain size", chain.size(), is(1));
		
		checkObjectIdentifier(chain.get(0), null, 3L, null, 2L, null, "3/2",
				"2", false);
	}
	
	@Test
	public void successObjectSpecRefsObjRefPathLen2() throws Exception {
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(new ObjectSpecification().withWsid(3L).withObjid(4L)
				.withVer(1L)
				.withObjRefPath(Arrays.asList("bar/2")));
		final List<ObjectIdentifier> ret = IdentifierUtils
				.processObjectSpecifications(oss);
		assertThat("incorrect return size", ret.size(), is(1));

		final ObjectIdentifier oi = ret.get(0);
		assertThat("incorrect class", oi instanceof ObjectIDWithRefPath,
				is(true));
		assertThat("incorrect class", oi instanceof ObjIDWithChainAndSubset,
				is(false));
		
		checkObjectIdentifier(oi, null, 3L, null, 4L, 1L,
				"3/4/1", "4", true);
		
		final ObjectIDWithRefPath oirc = (ObjectIDWithRefPath) oi;
		assertThat("incorrect hasChain()", oirc.hasChain(), is(true));
		final List<ObjectIdentifier> chain = oirc.getRefPath();
		assertThat("incorrect chain size", chain.size(), is(1));
		
		checkObjectIdentifier(chain.get(0), "bar", null, null, 2L, null,
				"bar/2", "2", false);
	}
	
	@Test
	public void successObjectSpecRefsObjRefPathLen4() throws Exception {
		final List<ObjectSpecification> oss = new LinkedList<>();
		oss.add(new ObjectSpecification().withWsid(3L).withObjid(4L)
				.withToObjRefPath(Arrays.asList("bar/2", "5/6/7", "biz/baz")));
		final List<ObjectIdentifier> ret = IdentifierUtils
				.processObjectSpecifications(oss);
		assertThat("incorrect return size", ret.size(), is(1));

		final ObjectIdentifier oi = ret.get(0);
		assertThat("incorrect class", oi instanceof ObjectIDWithRefPath,
				is(true));
		assertThat("incorrect class", oi instanceof ObjIDWithChainAndSubset,
				is(false));
		
		checkObjectIdentifier(oi, "bar", null, null, 2L, null,
				"bar/2", "2", false);
		
		final ObjectIDWithRefPath oirc = (ObjectIDWithRefPath) oi;
		assertThat("incorrect hasChain()", oirc.hasChain(), is(true));
		final List<ObjectIdentifier> chain = oirc.getRefPath();
		assertThat("incorrect chain size", chain.size(), is(3));
		
		checkObjectIdentifier(chain.get(0), null, 5L, null, 6L, 7L,
				"5/6/7", "6", true);
		checkObjectIdentifier(chain.get(1), "biz", null, "baz", null, null,
				"biz/baz", "baz", false);
		checkObjectIdentifier(chain.get(2), null, 3L, null, 4L, null,
				"3/4", "4", false);
	}

}
