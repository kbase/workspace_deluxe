package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zafarkhaja.semver.Version;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.test.TestCommon;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.typedobj.core.TempFileListener;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.db.FuncDetailedInfo;
import us.kbase.typedobj.db.ModuleDefId;
import us.kbase.typedobj.db.TypeDetailedInfo;
import us.kbase.typedobj.exceptions.NoSuchFuncException;
import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchPrivilegeException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;
import us.kbase.typedobj.exceptions.TypedObjectValidationException;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.ListObjectsParameters;
import us.kbase.workspace.database.ModuleInfo;
import us.kbase.workspace.database.ObjIDWithRefPathAndSubset;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIDWithRefPath;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Provenance.ExternalData;
import us.kbase.workspace.database.Provenance.ProvenanceAction;
import us.kbase.workspace.database.Provenance.SubAction;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder.ResourceUsageConfiguration;
import us.kbase.workspace.database.UncheckedUserMetadata;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.UserWorkspaceIDs;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.WorkspaceUserMetadata.MetadataSizeException;
import us.kbase.workspace.database.exceptions.DeletedObjectException;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchReferenceException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.refsearch.ReferenceSearchMaximumSizeExceededException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;

public class WorkspaceTest extends WorkspaceTester {

	public WorkspaceTest(String config, String backend,
			Integer maxMemoryUsePerCall) throws Exception {
		super(config, backend, maxMemoryUsePerCall);
	}

	@Test
	public void status() throws Exception {
		List<DependencyStatus> deps = ws.status();
		assertThat("incorrect number of dependencies", deps.size(), is(2));
		DependencyStatus mwdb = deps.get(0);
		assertThat("incorrect fail", mwdb.isOk(), is(true));
		assertThat("incorrect name", mwdb.getName(), is("MongoDB"));
		assertThat("incorrect status", mwdb.getStatus(), is("OK"));
		//should throw an error if not a semantic version
		Version.valueOf(mwdb.getVersion());
		
		DependencyStatus blob = deps.get(1);
		assertThat("incorrect fail", blob.isOk(), is(true));
		String n = blob.getName();
		assertThat("incorrect name", n.equals("Shock") || n.equals("GridFS"),
				is(true));
		assertThat("incorrect status", blob.getStatus(), is("OK"));
		//should throw an error if not a semantic version
		Version.valueOf(blob.getVersion());
	}
	
	@Test
	public void illegalWorkspaceNamesTemporarilyAllowed() throws Exception {
		/* tests the officially illegal but temporarily allowed workspace names of the style
		 * user:integer. These were allowed for integers > the java max int due to a bug, but
		 * for backwards compatibility reasons we can't fix it yet.
		 * TODO LATER change these to failing tests when they can be fixed
		 */
		ws.createWorkspace(new WorkspaceUser("fake"), "clone", true, null, null);
		final WorkspaceIdentifier cloneid = new WorkspaceIdentifier("clone");
		
		final Map<String, WorkspaceUser> badnames = new HashMap<>();
		badnames.put("foo:-3000000000", new WorkspaceUser("foo")); // long
		badnames.put("user2:3000000000", new WorkspaceUser("user2")); // long
		badnames.put("bar:90000000000000000000", new WorkspaceUser("bar")); // > long
		for (final Entry<String, WorkspaceUser> b: badnames.entrySet()) {
			new WorkspaceIdentifier(b.getKey(), b.getValue());
			final WorkspaceInformation w = ws.createWorkspace(
					b.getValue(), b.getKey(), false, null, null);
			assertThat("unexpected workspace name", w.getName(), is(b.getKey()));
			assertThat("unexpected user", w.getOwner(), is(b.getValue()));
			ws.renameWorkspace(b.getValue(), new WorkspaceIdentifier(
					b.getKey()), UUID.randomUUID().toString());
			
			final WorkspaceInformation c = ws.cloneWorkspace(
					b.getValue(), cloneid, b.getKey(), false, null, null, null);
			assertThat("unexpected workspace name", c.getName(), is(b.getKey()));
			assertThat("unexpected user", c.getOwner(), is(b.getValue()));
			ws.renameWorkspace(b.getValue(), new WorkspaceIdentifier(
					b.getKey()), UUID.randomUUID().toString());
			
			final String renamename = b.getValue().getUser() + ":nicename";
			ws.createWorkspace(b.getValue(), renamename, false, null, null);
			final WorkspaceInformation r = ws.renameWorkspace(
					b.getValue(), new WorkspaceIdentifier(renamename), b.getKey());
			assertThat("unexpected workspace name", r.getName(), is(b.getKey()));
			assertThat("unexpected user", r.getOwner(), is(b.getValue()));
			
			
		}
	}
	
	@Test
	public void workspaceDescription() throws Exception {
		WorkspaceInformation ltinfo = ws.createWorkspace(SOMEUSER, "lt", false, LONG_TEXT, null);
		WorkspaceInformation ltpinfo = ws.createWorkspace(SOMEUSER, "ltp", false, LONG_TEXT_PART, null);
		WorkspaceInformation ltninfo = ws.createWorkspace(SOMEUSER, "ltn", false, null, null);
		String desc = ws.getWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("lt"));
		assertThat("Workspace description incorrect", desc, is(LONG_TEXT.substring(0, 1000)));
		desc = ws.getWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("ltp"));
		assertThat("Workspace description incorrect", desc, is(LONG_TEXT_PART));
		desc = ws.getWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("ltn"));
		assertNull("Workspace description incorrect", desc);
		
		ws.setWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("lt"), LONG_TEXT_PART);
		ws.setWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("ltp"), null);
		ws.setWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("ltn"), LONG_TEXT);
		
		WorkspaceInformation ltinfo2 = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier("lt"));
		WorkspaceInformation ltpinfo2 = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier("ltp"));
		WorkspaceInformation ltninfo2 = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier("ltn"));
		
		assertTrue("date updated on set ws desc", ltinfo2.getModDate().isAfter(ltinfo.getModDate()));
		assertTrue("date updated on set ws desc", ltpinfo2.getModDate().isAfter(ltpinfo.getModDate()));
		assertTrue("date updated on set ws desc", ltninfo2.getModDate().isAfter(ltninfo.getModDate()));
		
		desc = ws.getWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("lt"));
		assertThat("Workspace description incorrect", desc, is(LONG_TEXT_PART));
		desc = ws.getWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("ltp"));
		assertNull("Workspace description incorrect", desc);
		desc = ws.getWorkspaceDescription(SOMEUSER, new WorkspaceIdentifier("ltn"));
		assertThat("Workspace description incorrect", desc, is(LONG_TEXT.substring(0, 1000)));
		
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("lt");
		failSetWSDesc(AUSER, wsi, "foo", new WorkspaceAuthorizationException(
				"User a may not set description on workspace lt"));
		failSetWSDesc(null, wsi, "foo", new WorkspaceAuthorizationException(
				"Anonymous users may not set description on workspace lt"));
		ws.setPermissions(SOMEUSER, wsi, Arrays.asList(AUSER), Permission.WRITE);
		failSetWSDesc(AUSER, wsi, "foo", new WorkspaceAuthorizationException(
				"User a may not set description on workspace lt"));
		ws.setPermissions(SOMEUSER, wsi, Arrays.asList(AUSER), Permission.ADMIN);
		ws.setWorkspaceDescription(AUSER, wsi, "wooga");
		assertThat("ws desc ok", ws.getWorkspaceDescription(SOMEUSER, wsi), is("wooga"));
		
		ws.setWorkspaceDeleted(SOMEUSER, wsi, true);
		failSetWSDesc(SOMEUSER, wsi, "foo", new NoSuchWorkspaceException(
				"Workspace lt is deleted", wsi));
		ws.setWorkspaceDeleted(SOMEUSER, wsi, false);
		failSetWSDesc(SOMEUSER, new WorkspaceIdentifier("ltfake"), "foo", new NoSuchWorkspaceException(
				"No workspace with name ltfake exists", wsi));
		
		try {
			ws.getWorkspaceDescription(BUSER, wsi);
			fail("Got ws desc w/o read perms");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("exception message ok", e.getLocalizedMessage(),
					is("User b may not read workspace lt"));
		}
		for (Permission p: Permission.values()) {
			if (p.compareTo(Permission.NONE) <= 0 || p.compareTo(Permission.OWNER) >= 0) {
				continue;
			}
			ws.setPermissions(SOMEUSER, wsi, Arrays.asList(BUSER), p);
			ws.getWorkspaceDescription(BUSER, wsi); //will fail if perms are wrong
		}
		
		ws.lockWorkspace(SOMEUSER, wsi);
		failSetWSDesc(SOMEUSER, wsi, "foo", new WorkspaceAuthorizationException(
				"The workspace with id " + ltinfo.getId() + ", name lt, is locked and may not be modified"));
	}
	
	@Test
	public void createWorkspaceAndGetInfo() throws Exception {
		String wsname = "foo_.-bar";
		WorkspaceInformation info = ws.createWorkspace(SOMEUSER, wsname, false, "eeswaffertheen", null);
		checkWSInfo(info, SOMEUSER, wsname, 0, Permission.OWNER, false, "unlocked", MT_MAP);
		long id = info.getId();
		WorkspaceIdentifier wsi = new WorkspaceIdentifier(id);
		Instant moddate = info.getModDate();
		info = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier(id));
		checkWSInfo(info, SOMEUSER, wsname, 0, Permission.OWNER, false, id, moddate, "unlocked", MT_MAP);
		info = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier(wsname));
		checkWSInfo(info, SOMEUSER, wsname, 0, Permission.OWNER, false, id, moddate, "unlocked", MT_MAP);
		
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("foo", "bar");
		meta.put("baz", "bash");
		WorkspaceInformation info2 = ws.createWorkspace(SOMEUSER, "foo2", true,
				"eeswaffertheen2", new WorkspaceUserMetadata(meta));
		checkWSInfo(info2, SOMEUSER, "foo2", 0, Permission.OWNER, true, "unlocked", meta);
		checkWSInfo(new WorkspaceIdentifier("foo2"), SOMEUSER, "foo2", 0, 
				Permission.OWNER, true, info2.getId(), info2.getModDate(), "unlocked", meta);
		
		try {
			ws.getWorkspaceInformation(BUSER, wsi);
			fail("Got metadata w/o read perms");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("exception message ok", e.getLocalizedMessage(),
					is("User b may not read workspace " + id));
		}
		for (Permission p: Permission.values()) {
			if (p.compareTo(Permission.NONE) <= 0 || p.compareTo(Permission.OWNER) >= 0) {
				continue;
			}
			ws.setPermissions(SOMEUSER, wsi, Arrays.asList(BUSER), p);
			ws.getWorkspaceInformation(BUSER, wsi); //will fail if perms are wrong
		}
		
		WorkspaceUser anotheruser = new WorkspaceUser("anotherfnuser");
		info = ws.createWorkspace(anotheruser, "anotherfnuser:MrT", true, "Ipitythefoolthatdon'teatMrTbreakfastcereal", null);
		checkWSInfo(info, anotheruser, "anotherfnuser:MrT", 0, Permission.OWNER, true, "unlocked", MT_MAP);
		id = info.getId();
		moddate = info.getModDate();
		info = ws.getWorkspaceInformation(anotheruser, new WorkspaceIdentifier(id));
		checkWSInfo(info, anotheruser, "anotherfnuser:MrT", 0, Permission.OWNER, true, id, moddate, "unlocked", MT_MAP);
		info = ws.getWorkspaceInformation(anotheruser, new WorkspaceIdentifier("anotherfnuser:MrT"));
		checkWSInfo(info, anotheruser, "anotherfnuser:MrT", 0, Permission.OWNER, true, id, moddate, "unlocked", MT_MAP);
		
		ws.setGlobalPermission(anotheruser, new WorkspaceIdentifier("anotherfnuser:MrT"), Permission.NONE);
		ws.setGlobalPermission(SOMEUSER, new WorkspaceIdentifier("foo2"), Permission.NONE);
	}
	
	@Test
	public void adminGetObjectAndInfoStandard() throws Exception {
		final WorkspaceUser user = new WorkspaceUser("blahblah");
		final WorkspaceUser admin = new WorkspaceUser("admin");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("somews");
		
		final IdReferenceHandlerSetFactory idfac = getIdFactory();
		
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		
		final ObjectInformation oi = ws.saveObjects(user, wsi, Arrays.asList(
				new WorkspaceSaveObject(
						new ObjectIDNoWSNoVer("foo"), new HashMap<>(), SAFE_TYPE1, null,
						new Provenance(user), false)), idfac).get(0);
		
		final List<WorkspaceObjectData> obj = ws.getObjects(admin, Arrays.asList(
				new ObjectIdentifier(wsi, 1)), false, false, true);
		
		checkObjectAndInfo(obj.get(0), oi, new HashMap<>());
		destroyGetObjectsResources(obj);
		
		final ObjectInformation objin = ws.getObjectInformation(admin, Arrays.asList(
				new ObjectIdentifier(wsi, 1)), false, false, true).get(0);
		
		checkObjInfo(objin, 1L, "foo", SAFE_TYPE1.getTypeString(), 1, user, 1L, "somews",
				"99914b932bd37a50b983c5e7c90ae93b", 2, null,
				Arrays.asList(new Reference("1/1/1")));
	}
	
	@Test
	public void adminGetObjectAndInfoStandardFailDeleted() throws Exception {
		final WorkspaceUser user = new WorkspaceUser("blahblah");
		final WorkspaceUser admin = new WorkspaceUser("admin");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("somews");
		
		final IdReferenceHandlerSetFactory idfac = getIdFactory();
		
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		
		ws.saveObjects(user, wsi, Arrays.asList(
				new WorkspaceSaveObject(
						new ObjectIDNoWSNoVer("foo"), new HashMap<>(), SAFE_TYPE1, null,
						new Provenance(user), false)), idfac).get(0);
		
		final ObjectIdentifier oid = new ObjectIdentifier(wsi, 1);
		ws.setObjectsDeleted(user, Arrays.asList(oid), true);
		final ObjectIDResolvedWS resobj = new ObjectIDResolvedWS(
				new ResolvedWorkspaceID(1, "somews", false, false), 1);
		
		final DeletedObjectException e = failGetObjectsAsAdmin(
				admin, Arrays.asList(new ObjectIdentifier(wsi, 1)),
				new DeletedObjectException(
						"Object 1 (name foo) in workspace 1 (name somews) has been deleted",
						resobj));
		assertThat("incorrect source object", e.getResolvedInaccessibleObject(),
				is(resobj));
		
		final DeletedObjectException e2 = failGetObjectInfoAsAdmin(
				admin, Arrays.asList(new ObjectIdentifier(wsi, 1)),
				new DeletedObjectException(
						"Object 1 (name foo) in workspace 1 (name somews) has been deleted",
						resobj));
		assertThat("incorrect source object", e2.getResolvedInaccessibleObject(),
				is(resobj));
	}
	
	private <T extends Exception> T failGetObjectsAsAdmin(
			final WorkspaceUser admin,
			final List<ObjectIdentifier> objs,
			final T e) {
		try {
			ws.getObjects(admin, objs, false, false, true);
			fail("expected exception");
			return null;
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
			@SuppressWarnings("unchecked")
			final T got2 = (T) got;
			return got2;
		}
	}
	
	private <T extends Exception> T failGetObjectInfoAsAdmin(
			final WorkspaceUser admin,
			final List<ObjectIdentifier> objs,
			final T e) {
		try {
			ws.getObjectInformation(admin, objs, false, false, true);
			fail("expected exception");
			return null;
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
			@SuppressWarnings("unchecked")
			final T got2 = (T) got;
			return got2;
		}
	}
	
	@Test
	public void adminGetObjectAndInfoPath() throws Exception {
		final WorkspaceUser user = new WorkspaceUser("blahblah");
		final WorkspaceUser admin = new WorkspaceUser("admin");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("somews");
		
		final IdReferenceHandlerSetFactory idfac = getIdFactory();
		
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		
		final ObjectInformation oi = ws.saveObjects(user, wsi, Arrays.asList(
				new WorkspaceSaveObject(
						new ObjectIDNoWSNoVer("foo"), new HashMap<>(), SAFE_TYPE1, null,
						new Provenance(user), false)), idfac).get(0);
		
		ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("foo2"), ImmutableMap.of("refs", Arrays.asList("1/1")),
				REF_TYPE, null, new Provenance(user), false)), idfac).get(0);
		
		final List<ObjectIdentifier> objid = Arrays.asList(new ObjectIdentifier(wsi, 1));
		ws.setObjectsDeleted(user, objid, true);
		
		final List<WorkspaceObjectData> obj = ws.getObjects(admin, Arrays.asList(
				new ObjectIDWithRefPath(new ObjectIdentifier(wsi, 2), objid)), false, false, true);
		
		checkObjectAndInfo(obj.get(0), oi.updateReferencePath(Arrays.asList(
				new Reference("1/2/1"), new Reference("1/1/1"))), new HashMap<>());
		destroyGetObjectsResources(obj);
		
		final ObjectInformation objin = ws.getObjectInformation(admin, Arrays.asList(
				new ObjectIDWithRefPath(new ObjectIdentifier(wsi, 2), objid)), false, false, true)
				.get(0);
		
		checkObjInfo(objin, 1L, "foo", SAFE_TYPE1.getTypeString(), 1, user, 1L, "somews",
				"99914b932bd37a50b983c5e7c90ae93b", 2, null,
				Arrays.asList(new Reference("1/2/1"), new Reference("1/1/1")));
	}
	
	@Test
	public void adminGetObjectAndInfoPathFailDeleted() throws Exception {
		final WorkspaceUser user = new WorkspaceUser("blahblah");
		final WorkspaceUser admin = new WorkspaceUser("admin");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("somews");
		
		final IdReferenceHandlerSetFactory idfac = getIdFactory();
		
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		
		ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("foo"), new HashMap<>(), SAFE_TYPE1, null,
				new Provenance(user), false)), idfac).get(0);
		
		ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("foo2"), ImmutableMap.of("refs", Arrays.asList("1/1")),
				REF_TYPE, null, new Provenance(user), false)), idfac).get(0);
		
		final List<ObjectIdentifier> objid = Arrays.asList(new ObjectIdentifier(wsi, 1));
		ws.setObjectsDeleted(user, objid, true);
		final ObjectIdentifier objid2 = new ObjectIdentifier(wsi, 2);
		ws.setObjectsDeleted(user, Arrays.asList(objid2), true);
		
		final ObjectIDWithRefPath objref2 = new ObjectIDWithRefPath(objid2, objid);
		
		final InaccessibleObjectException e = failGetObjectsAsAdmin(admin, Arrays.asList(
				objref2), new InaccessibleObjectException(
						"Object 2 (name foo2) in workspace 1 (name somews) has been deleted",
						objid2));
		assertThat("incorrect source object", e.getInaccessibleObject(), is(objref2));
		
		final InaccessibleObjectException e2 = failGetObjectInfoAsAdmin(admin, Arrays.asList(
				objref2), new InaccessibleObjectException(
						"Object 2 (name foo2) in workspace 1 (name somews) has been deleted",
						objid2));
		
		assertThat("incorrect source object", e2.getInaccessibleObject(), is(objref2));
	}
	
	@Test
	public void adminGetObjectAndInfoSearch() throws Exception {
		final WorkspaceUser user = new WorkspaceUser("blahblah");
		final WorkspaceUser admin = new WorkspaceUser("admin");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("somews");
		
		final IdReferenceHandlerSetFactory idfac = getIdFactory();
		
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		
		final ObjectInformation oi = ws.saveObjects(user, wsi, Arrays.asList(
				new WorkspaceSaveObject(
						new ObjectIDNoWSNoVer("foo"), new HashMap<>(), SAFE_TYPE1, null,
						new Provenance(user), false)), idfac).get(0);
		
		ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("foo2"), ImmutableMap.of("refs", Arrays.asList("1/1")),
				REF_TYPE, null, new Provenance(user), false)), idfac).get(0);
		
		final List<ObjectIdentifier> objid = Arrays.asList(new ObjectIdentifier(wsi, 1));
		ws.setObjectsDeleted(user, objid, true);
		
		final List<WorkspaceObjectData> obj = ws.getObjects(admin, Arrays.asList(
				new ObjectIDWithRefPath(objid.get(0))), false, false, true);
		
		checkObjectAndInfo(obj.get(0), oi.updateReferencePath(Arrays.asList(
				new Reference("1/2/1"), new Reference("1/1/1"))), new HashMap<>());
		destroyGetObjectsResources(obj);
		
		final ObjectInformation objin = ws.getObjectInformation(admin, Arrays.asList(
				new ObjectIDWithRefPath(objid.get(0))), false, false, true)
				.get(0);
		
		checkObjInfo(objin, 1L, "foo", SAFE_TYPE1.getTypeString(), 1, user, 1L, "somews",
				"99914b932bd37a50b983c5e7c90ae93b", 2, null,
				Arrays.asList(new Reference("1/2/1"), new Reference("1/1/1")));
	}
	
	@Test
	public void adminGetObjectAndInfoSearchFailDeleted() throws Exception {
		final WorkspaceUser user = new WorkspaceUser("blahblah");
		final WorkspaceUser admin = new WorkspaceUser("admin");
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier("somews");
		
		final IdReferenceHandlerSetFactory idfac = getIdFactory();
		
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		
		ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("foo"), new HashMap<>(), SAFE_TYPE1, null,
				new Provenance(user), false)), idfac).get(0);
		
		ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("foo2"), ImmutableMap.of("refs", Arrays.asList("1/1")),
				REF_TYPE, null, new Provenance(user), false)), idfac).get(0);
		
		final List<ObjectIdentifier> objid = Arrays.asList(new ObjectIdentifier(wsi, 1));
		ws.setObjectsDeleted(user, objid, true);
		final ObjectIdentifier objid2 = new ObjectIdentifier(wsi, 2);
		ws.setObjectsDeleted(user, Arrays.asList(objid2), true);
		
		final ObjectIDWithRefPath objref2 = new ObjectIDWithRefPath(objid.get(0));
	
		final InaccessibleObjectException e = failGetObjectsAsAdmin(admin, Arrays.asList(
				objref2), new InaccessibleObjectException(
						"The latest version of object 1 in workspace somews is not accessible " +
						"to user admin", objid2));
		assertThat("incorrect source object", e.getInaccessibleObject(), is(objref2));
		
		final InaccessibleObjectException e2 = failGetObjectInfoAsAdmin(admin, Arrays.asList(
				objref2), new InaccessibleObjectException(
						"The latest version of object 1 in workspace somews is not accessible " +
						"to user admin", objid2));
		assertThat("incorrect source object", e2.getInaccessibleObject(), is(objref2));
	}
	
	@Test
	public void adminGetObjectHistory() throws Exception {
		WorkspaceUser user = new WorkspaceUser("listObjHistUser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("listObjHist1");
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		
		final Provenance p = new Provenance(user);
		final ObjectInformation obj1 = saveObject(user, wsi, ImmutableMap.of("foo", "bar1"),
				ImmutableMap.of("foo", "bar1"), SAFE_TYPE1, "std", p);
		final ObjectInformation obj2 = saveObject(user, wsi, ImmutableMap.of("foo", "bar2"),
				ImmutableMap.of("foo", "bar2"), SAFE_TYPE1, "std", p);
		final ObjectInformation obj3 = saveObject(user, wsi, ImmutableMap.of("foo", "bar3"),
				ImmutableMap.of("foo", "bar3"), SAFE_TYPE1, "std", p);
		
		final List<ObjectInformation> vers = ws.getObjectHistory(
				null, new ObjectIdentifier(wsi, 1), true);
		
		assertThat("incorrect object versions", vers, is(Arrays.asList(obj1, obj2, obj3)));
	}
	
	@Test
	public void adminGetWorkspaceInfo() throws Exception {
		WorkspaceUser user = new WorkspaceUser("blahblah");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("somews");
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("foo", "bar");
		meta.put("foo2", "bar2");
		
		WorkspaceInformation info = ws.createWorkspace(user, wsi.getName(),
				false, null, new WorkspaceUserMetadata(meta));
		
		final WorkspaceInformation wsinfo = ws.getWorkspaceInformationAsAdmin(wsi);
		checkWSInfo(wsinfo, user, wsi.getName(), 0, Permission.NONE, false, 1, info.getModDate(),
				"unlocked", meta);
		
		failGetWorkspaceInfoAsAdmin(null,
				new NullPointerException("Workspace identifier cannot be null"));
		
		failGetWorkspaceInfoAsAdmin(new WorkspaceIdentifier(100),
				new NoSuchWorkspaceException("No workspace with id 100 exists",
						new WorkspaceIdentifier(100)));
		
		ws.setWorkspaceDeleted(user, wsi, true);
		failGetWorkspaceInfoAsAdmin(wsi,
				new NoSuchWorkspaceException("Workspace somews is deleted", wsi));
	}
	
	@Test
	public void adminListObjects() throws Exception {
		WorkspaceUser user = new WorkspaceUser("listObjUser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("listObj1");
		WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("listObj2");
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		ws.createWorkspace(user, wsi2.getName(), false, null, null);
		
		final Provenance p = new Provenance(user);
		final ObjectInformation std1 = saveObject(user, wsi, null,
				ImmutableMap.of("foo", "bar"), SAFE_TYPE2, "std", p);
		final ObjectInformation del = saveObject(user, wsi, null,
				ImmutableMap.of("foo", "bar"), SAFE_TYPE1, "std2", p);
		final ObjectInformation std2 = saveObject(user, wsi2, null,
				ImmutableMap.of("foo", "bar"), SAFE_TYPE1, "std3", p);
		
		ws.setObjectsDeleted(user, Arrays.asList(new ObjectIdentifier(wsi, 2)), true);
		
		ListObjectsParameters lop = new ListObjectsParameters(Arrays.asList(wsi, wsi2))
				.withIncludeMetaData(true);
		compareObjectInfo(ws.listObjects(lop), Arrays.asList(std1, std2));
		
		lop = new ListObjectsParameters(Arrays.asList(wsi)).withIncludeMetaData(true);
		compareObjectInfo(ws.listObjects(lop), Arrays.asList(std1));
		
		lop = new ListObjectsParameters(Arrays.asList(wsi, wsi2)).withShowOnlyDeleted(true)
				.withIncludeMetaData(true);
		compareObjectInfo(ws.listObjects(lop), Arrays.asList(del));
		
		lop = new ListObjectsParameters(Arrays.asList(wsi, wsi2)).withShowDeleted(true)
				.withIncludeMetaData(true);
		compareObjectInfo(ws.listObjects(lop), Arrays.asList(std1, del, std2));

		lop = new ListObjectsParameters(Arrays.asList(wsi, wsi2), SAFE_TYPE1)
				.withIncludeMetaData(true).withShowDeleted(true);
		compareObjectInfo(ws.listObjects(lop), Arrays.asList(del, std2));
	}
	
	@Test
	public void adminListObjectsFailConstructParams() {
		final Exception e = new IllegalArgumentException(
				"Must provide between 1 and 1000 workspaces");
		final List<WorkspaceIdentifier> wsis = new LinkedList<>();
		for (int i = 1; i < 1002; i++) {
			wsis.add(new WorkspaceIdentifier(i));
		}
		failConstructListObjectsParams(null, e);
		failConstructListObjectsParams(Collections.<WorkspaceIdentifier>emptyList(), e);
		failConstructListObjectsParams(wsis, e);
		failConstructListObjectsParams(null, SAFE_TYPE1, e);
		failConstructListObjectsParams(
				Collections.<WorkspaceIdentifier>emptyList(), SAFE_TYPE1, e);
		failConstructListObjectsParams(wsis, SAFE_TYPE1, e);
		failConstructListObjectsParams(Arrays.asList(new WorkspaceIdentifier("foo")),
				null, new NullPointerException("Type cannot be null"));
	}
	
	private void failConstructListObjectsParams(
			final List<WorkspaceIdentifier> wsis,
			final Exception e) {
		try {
			new ListObjectsParameters(wsis);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	private void failConstructListObjectsParams(
			final List<WorkspaceIdentifier> wsis,
			final TypeDefId type,
			final Exception e) {
		try {
			new ListObjectsParameters(wsis, type);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void workspaceMetadata() throws Exception {
		WorkspaceUser user = new WorkspaceUser("blahblah");
		WorkspaceUser user2 = new WorkspaceUser("blahblah2");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("workspaceMetadata");
		WorkspaceIdentifier wsiNo =
				new WorkspaceIdentifier("workspaceNoMetadata");
		WorkspaceIdentifier wsiNo2 =
				new WorkspaceIdentifier("workspaceNoMetadata2");
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("foo", "bar");
		meta.put("foo2", "bar2");
		meta.put("some", "meta");
		WorkspaceInformation info = ws.createWorkspace(user, wsi.getName(),
				false, null, new WorkspaceUserMetadata(meta));
		ws.setPermissions(user, wsi, Arrays.asList(user2), Permission.ADMIN);
		checkWSInfo(info, user, wsi.getName(), 0, Permission.OWNER, false,
				info.getId(), info.getModDate(), "unlocked", meta);
		checkWSInfo(wsi, user, wsi.getName(), 0, Permission.OWNER, false,
				info.getId(), info.getModDate(), "unlocked", meta);
		WorkspaceInformation infoNo = ws.createWorkspace(user, wsiNo.getName(),
				false, null, null);
		checkWSInfo(infoNo, user, wsiNo.getName(), 0, Permission.OWNER, false,
				infoNo.getId(), infoNo.getModDate(), "unlocked", MT_MAP);
		checkWSInfo(wsiNo, user, wsiNo.getName(), 0, Permission.OWNER, false,
				infoNo.getId(), infoNo.getModDate(), "unlocked", MT_MAP);
		WorkspaceInformation infoNo2 = ws.createWorkspace(user, wsiNo2.getName(),
				false, null, null);
		
		
		meta.put("foo2", "bar3"); //replace
		Map<String, String> putmeta = new HashMap<String, String>();
		putmeta.put("foo2", "bar3");
		ws.setWorkspaceMetadata(user, wsi, new WorkspaceUserMetadata(putmeta), null);
		final Instant d1 = checkWSInfo(wsi, user, wsi.getName(), 0, Permission.OWNER, false,
				info.getId(), "unlocked", meta);
		meta.put("foo3", "bar4"); //new
		putmeta.clear();
		putmeta.put("foo3", "bar4");
		ws.setWorkspaceMetadata(user, wsi, new WorkspaceUserMetadata(putmeta), null);
		final Instant d2 = checkWSInfo(wsi, user, wsi.getName(), 0, Permission.OWNER, false,
				info.getId(), "unlocked", meta);
		
		putmeta.clear();
		putmeta.put("foo3", "bar5"); //replace
		putmeta.put("some.garbage", "with.dots"); //new
		putmeta.put("foo", "whoa this is new"); //replace
		putmeta.put("no, this part is new", "prunker"); //new
		meta.put("foo3", "bar5");
		meta.put("some.garbage", "with.dots");
		meta.put("foo", "whoa this is new");
		meta.put("no, this part is new", "prunker");
		ws.setWorkspaceMetadata(user, wsi, new WorkspaceUserMetadata(putmeta), null);
		final Instant d3 = checkWSInfo(wsi, user, wsi.getName(), 0, Permission.OWNER, false,
				info.getId(), "unlocked", meta);
		
		Map<String, String> newmeta = new HashMap<String, String>();
		newmeta.put("new", "meta");
		ws.setWorkspaceMetadata(user, wsiNo, new WorkspaceUserMetadata(newmeta),
				Collections.emptyList());
		final Instant nod1 = checkWSInfo(wsiNo, user, wsiNo.getName(), 0, Permission.OWNER, false,
				infoNo.getId(), "unlocked", newmeta);
		
		assertDatesAscending(infoNo.getModDate(), nod1);
		
		meta.remove("foo2");
		ws.setWorkspaceMetadata(user, wsi, null, Arrays.asList("foo2"));
		final Instant d4 = checkWSInfo(wsi, user, wsi.getName(), 0, Permission.OWNER, false,
				info.getId(), "unlocked", meta);
		meta.remove("some");
		ws.setWorkspaceMetadata(user2, wsi, null, Arrays.asList("some"));
		final Instant d5 = checkWSInfo(wsi, user, wsi.getName(), 0, Permission.OWNER, false,
				info.getId(), "unlocked", meta);
		ws.setWorkspaceMetadata(user, wsi, null, Arrays.asList("fake")); //no effect
		checkWSInfo(wsi, user, wsi.getName(), 0, Permission.OWNER, false, info.getId(), d5, "unlocked", meta);
		
		assertDatesAscending(info.getModDate(), d1, d2, d3, d4, d5);
		
		checkWSInfo(wsiNo2, user, wsiNo2.getName(), 0, Permission.OWNER, false, infoNo2.getId(), infoNo2.getModDate(), "unlocked", MT_MAP);
		ws.setWorkspaceMetadata(user, wsiNo2, null, Arrays.asList("somekey")); //should do nothing
		checkWSInfo(wsiNo2, user, wsiNo2.getName(), 0, Permission.OWNER, false, infoNo2.getId(), infoNo2.getModDate(), "unlocked", MT_MAP);
		
		
		ws.setPermissions(user, wsi, Arrays.asList(user2), Permission.WRITE);
		failWSMeta(user2, wsi, "foo", "val", new WorkspaceAuthorizationException(
				"User blahblah2 may not alter metadata for workspace workspaceMetadata"));
		failWSMeta(null, wsi, "foo", "val", new WorkspaceAuthorizationException(
				"Anonymous users may not alter metadata for workspace workspaceMetadata"));
		failWSMeta(user2, new WorkspaceIdentifier("thisiswayfake"), "foo", "val", new NoSuchWorkspaceException(
				"No workspace with name thisiswayfake exists", wsi));
		ws.setWorkspaceDeleted(user, wsi, true);
		failWSMeta(user, wsi, "foo", "val", new NoSuchWorkspaceException(
				"Workspace workspaceMetadata is deleted", wsi));
		ws.setWorkspaceDeleted(user, wsi, false);
		
		putmeta.clear();
		for (int i = 0; i < 147; i++) {
			putmeta.put("" + i, TEXT100);
		}
		ws.createWorkspace(user, "wsmetafake", false, null,
				new WorkspaceUserMetadata(putmeta)); //should work
		failWSSetMeta(user, wsi, putmeta, new IllegalArgumentException(
				"Updated metadata exceeds allowed size of 16000B"));
		
		ws.setWorkspaceMetadata(user, wsiNo, new WorkspaceUserMetadata(putmeta), null); //should work
		putmeta.put("148", TEXT100);
		failWSSetMeta(user, wsiNo2, putmeta, new MetadataSizeException(
				"Metadata exceeds maximum of 16000B"));
	}
	
	@Test
	public void workspaceMetadataRemoveMultiple() throws Exception {
		// tests passing null & empty metadata
		WorkspaceUser user = new WorkspaceUser("blahblah");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("workspaceMetadata");
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("foo", "bar");
		meta.put("foo2", "bar2");
		meta.put("some", "meta");
		ws.createWorkspace(user, wsi.getName(), false, null, new WorkspaceUserMetadata(meta));
		
		ws.setWorkspaceMetadata(user, wsi, null, Arrays.asList("foo", "some"));
		final Map<String, String> gotmeta = ws.getWorkspaceInformation(user, wsi)
				.getUserMeta().getMetadata();
		assertThat("incorrect metadata", gotmeta, is(ImmutableMap.of("foo2", "bar2")));
		
		ws.setWorkspaceMetadata(user, wsi, new WorkspaceUserMetadata(meta), null);
		ws.setWorkspaceMetadata(user, wsi, new WorkspaceUserMetadata(),
				Arrays.asList("foo2"));
		
		final Map<String, String> gotmeta2 = ws.getWorkspaceInformation(user, wsi)
				.getUserMeta().getMetadata();
		assertThat("incorrect metadata", gotmeta2,
				is(ImmutableMap.of("foo", "bar", "some", "meta")));
	}
	
	@Test
	public void workspaceMetadataRemoveFail() throws Exception {
		final WorkspaceUser user = new WorkspaceUser("user");
		ws.createWorkspace(user, "foo", false, null, null);
		failWSSetMeta(ws, user, new WorkspaceIdentifier(1), null,
				Arrays.asList("foo", null), new NullPointerException("null metadata keys are not allowed"));
	}
	
	@Test
	public void createWorkspaceAndWorkspaceIdentifierWithBadInput()
			throws Exception {
		class TestRig {
			public final WorkspaceUser user;
			public final String wsname;
			public final String excep;
			public TestRig(WorkspaceUser user, String wsname, String exception) {
				this.user = user;
				this.wsname = wsname;
				this.excep = exception;
			}
		}
		WorkspaceUser crap = new WorkspaceUser("afaeaafe");
		List<TestRig> userWS = new ArrayList<TestRig>();
		//test a few funny chars in the ws name
		userWS.add(new TestRig(crap, "afe_aff*afea",
				"Illegal character in workspace name afe_aff*afea: *"));
		userWS.add(new TestRig(crap, "afe_aff%afea",
				"Illegal character in workspace name afe_aff%afea: %"));
		userWS.add(new TestRig(crap, "afeaff/af*ea",
				"Illegal character in workspace name afeaff/af*ea: /"));
		userWS.add(new TestRig(crap, "af?eaff*afea",
				"Illegal character in workspace name af?eaff*afea: ?"));
		userWS.add(new TestRig(crap, "-64", "Workspace names cannot be integers: -64"));
		userWS.add(new TestRig(crap, "3456789012",
				"Workspace names cannot be integers: 3456789012")); //long
		userWS.add(new TestRig(crap, "45678901234567890123",
				"Workspace names cannot be integers: 45678901234567890123")); // > long
		//check missing ws name
		userWS.add(new TestRig(crap, null,
				"Workspace name cannot be null or the empty string"));
		userWS.add(new TestRig(crap, "",
				"Workspace name cannot be null or the empty string"));
		//check long names
		userWS.add(new TestRig(crap, TEXT256,
				"Workspace name exceeds the maximum length of 255"));
		//check missing user and/or workspace name in compound name
		userWS.add(new TestRig(crap, ":",
				"Workspace name missing from :"));
		userWS.add(new TestRig(crap, "foo:",
				"Workspace name missing from foo:"));
		userWS.add(new TestRig(crap, ":foo",
				"User name missing from :foo"));
		//check multiple delims
		userWS.add(new TestRig(crap, "foo:a:foo",
				"Workspace name foo:a:foo may only contain one : delimiter"));
		userWS.add(new TestRig(crap, "foo::foo",
				"Workspace name foo::foo may only contain one : delimiter"));
		
		for (TestRig testdata: userWS) {
			String wksps = testdata.wsname;
			try {
				new WorkspaceIdentifier(wksps);
				fail(String.format("able to create workspace identifier with illegal input ws %s",
						wksps));
			} catch (IllegalArgumentException e) {
				assertThat("incorrect exception message", e.getLocalizedMessage(),
						is(testdata.excep));
			}
		}
		
		//check missing user
		userWS.add(new TestRig(null, "foo",
				"user cannot be null"));
		//user must match prefix
		userWS.add(new TestRig(SOMEUSER, "notauser:foo", 
				"Workspace name notauser:foo must only contain the user name "
				+ SOMEUSER.getUser() + " prior to the : delimiter"));
		//no ints
		userWS.add(new TestRig(new WorkspaceUser("foo"), "foo:64",
				"Workspace names cannot be integers: foo:64"));
		
		for (TestRig testdata: userWS) {
			WorkspaceUser user = testdata.user;
			String wksps = testdata.wsname;
			try {
				ws.createWorkspace(user, wksps, false, "iswaffertheen", null);
				fail(String.format("able to create workspace with illegal input user: %s ws %s",
						user, wksps));
			} catch (IllegalArgumentException e) {
				assertThat("incorrect exception message", e.getLocalizedMessage(),
						is(testdata.excep));
			}
			try {
				new WorkspaceIdentifier(wksps, user);
				fail(String.format("able to create workspace identifier with illegal input user: %s ws %s",
						user, wksps));
			} catch (IllegalArgumentException e) {
				assertThat("incorrect exception message", e.getLocalizedMessage(),
						is(testdata.excep));
			}
		}
	}
	
	@Test
	public void preExistingWorkspace() throws Exception {
		ws.createWorkspace(AUSER, "preexist", false, null, null);
		failCreateWorkspace(BUSER, "preexist", false, null, null,
				new PreExistingWorkspaceException("Workspace name preexist is already in use"));
		ws.setWorkspaceDeleted(AUSER, new WorkspaceIdentifier("preexist"), true);
		failCreateWorkspace(BUSER, "preexist", false, null, null,
				new PreExistingWorkspaceException("Workspace name preexist is already in use"));
		failCreateWorkspace(AUSER, "preexist", false, null, null,
				new PreExistingWorkspaceException(
						"Workspace name preexist is already in use by a deleted workspace"));
	}
	
	@Test
	public void createIllegalUser() throws Exception {
		try {
			new WorkspaceUser("*");
			fail("able to create user with illegal character");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Illegal character in user name *: *"));
		}
		try {
			new WorkspaceUser(null);
			fail("able to create user with null");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Username cannot be null or the empty string"));
		}
		try {
			new WorkspaceUser("");
			fail("able to create user with empty string");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Username cannot be null or the empty string"));
		}
		try {
			new WorkspaceUser(TEXT101);
			fail("able to create user with long string");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Username exceeds the maximum length of 100"));
		}
		try {
			new AllUsers('$');
			fail("able to create AllUser with illegal char");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Disallowed character: $"));
		}
		
	}
	
	@Test
	public void setWorkspaceOwner() throws Exception {
		WorkspaceUser u1 = new WorkspaceUser("foo");
		WorkspaceUser u2 = new WorkspaceUser("bar");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("wsfoo");
		ws.createWorkspace(u1, wsi.getName(), false, null, null);
		Map<String, String> mt = new HashMap<String, String>();
		
		//basic test
		WorkspaceInformation wsinfo =
				ws.setWorkspaceOwner(u1, wsi, u2, Optional.<String>absent(), false);
		checkWSInfo(wsinfo, u2, wsi.getName(), 0L, Permission.OWNER, false, "unlocked", mt);
		Map<User, Permission> pexp = new HashMap<User, Permission>();
		pexp.put(u1, Permission.ADMIN);
		pexp.put(u2, Permission.OWNER);
		assertThat("permissions correct", ws.getPermissions(
				u2, Arrays.asList(wsi)).get(0), is (pexp));
		
		failSetWorkspaceOwner(null, wsi, u2, Optional.<String>absent(), true,
				new IllegalArgumentException("bar already owns workspace wsfoo"));
		failSetWorkspaceOwner(u2, wsi, u2, Optional.<String>absent(), false,
				new IllegalArgumentException("bar already owns workspace wsfoo"));
		
		failSetWorkspaceOwner(null, wsi, null, Optional.<String>absent(), true,
				new NullPointerException("newUser cannot be null"));
		failSetWorkspaceOwner(u2, wsi, null, Optional.<String>absent(), false,
				new NullPointerException("newUser cannot be null"));
		
		failSetWorkspaceOwner(u1, wsi, u2, null, false, new NullPointerException("newName"));
		
		failSetWorkspaceOwner(null, null, u1, Optional.<String>absent(), true,
				new NullPointerException("wsi cannot be null"));
		failSetWorkspaceOwner(u2, null, u1, Optional.<String>absent(), false,
				new NullPointerException("wsi cannot be null"));
		
		WorkspaceIdentifier fake = new WorkspaceIdentifier("wsfoofake");
		failSetWorkspaceOwner(null, fake, u2, Optional.<String>absent(), true,
				new NoSuchWorkspaceException("No workspace with name wsfoofake exists", fake));
		failSetWorkspaceOwner(u2, fake, u2, Optional.<String>absent(), false,
				new NoSuchWorkspaceException("No workspace with name wsfoofake exists", fake));
		
		failSetWorkspaceOwner(null, wsi, u1, Optional.<String>absent(), false,
				new WorkspaceAuthorizationException("Anonymous users may not change the owner of workspace wsfoo"));
		failSetWorkspaceOwner(u1, wsi, u1, Optional.<String>absent(), false,
				new WorkspaceAuthorizationException("User foo may not change the owner of workspace wsfoo"));
		
		//test as admin
		wsinfo = ws.setWorkspaceOwner(null, wsi, u1, Optional.<String>absent(), true);
		checkWSInfo(wsinfo, u1, wsi.getName(), 0L, Permission.OWNER, false, "unlocked", mt);
		pexp.put(u1, Permission.OWNER);
		pexp.put(u2, Permission.ADMIN);
		assertThat("permissions correct", ws.getPermissions(
				u2, Arrays.asList(wsi)).get(0), is (pexp));
		
		//test basic name change
		wsinfo = ws.setWorkspaceOwner(u1, wsi, u2, Optional.of("wsfoonew"), false);
		checkWSInfo(wsinfo, u2, "wsfoonew", 0L, Permission.OWNER, false, "unlocked", mt);
		wsi = new WorkspaceIdentifier("wsfoonew");
		
		//illegal name change to invalid user
		final Optional<String> newName = Optional.of("bar:wsfoo");
		failSetWorkspaceOwner(u2, wsi, u1, newName, false, new IllegalArgumentException(
				"Workspace name bar:wsfoo must only contain the user name foo " +
				"prior to the : delimiter"));
		failSetWorkspaceOwner(null, wsi, u1, newName, true, new IllegalArgumentException(
				"Workspace name bar:wsfoo must only contain the user name foo " +
				"prior to the : delimiter"));
		
		//test failing name when ws is prefixed by previous user name
		ws.renameWorkspace(u2, wsi, "bar:wsfoo");
		wsi = new WorkspaceIdentifier("bar:wsfoo");
		failSetWorkspaceOwner(u2, wsi, u1, Optional.of("bar:wsfoo"), false,
				new IllegalArgumentException("Workspace name bar:wsfoo must only contain " +
						"the user name foo prior to the : delimiter"));
		
		//test auto rename of workspace
		wsinfo = ws.setWorkspaceOwner(u2, wsi, u1, Optional.<String>absent(), false);
		wsi = new WorkspaceIdentifier("foo:wsfoo");
		checkWSInfo(wsinfo, u1, wsi.getName(), 0L, Permission.OWNER, false, "unlocked", mt);
		
		//test manual rename of workspace
		wsinfo = ws.setWorkspaceOwner(u1, wsi, u2, Optional.of("bar:wsfoo"), false);
		wsi = new WorkspaceIdentifier("bar:wsfoo");
		checkWSInfo(wsinfo, u2, wsi.getName(), 0L, Permission.OWNER, false, "unlocked", mt);
		
		//test rename to preexisting workspace
		final Optional<String> newName2 = Optional.of("foo:wsfoo2");
		ws.createWorkspace(u1, "foo:wsfoo2", false, null, null);
		failSetWorkspaceOwner(u2, wsi, u1, newName2, false,
				new IllegalArgumentException("There is already a workspace named foo:wsfoo2"));
		failSetWorkspaceOwner(null, wsi, u1, newName2, true,
				new IllegalArgumentException("There is already a workspace named foo:wsfoo2"));
		
		//test rename with same name
		ws.renameWorkspace(u2, wsi, "wsfoo");
		wsi = new WorkspaceIdentifier("wsfoo");
		wsinfo = ws.setWorkspaceOwner(u2, wsi, u1, Optional.of("wsfoo"), false);
		checkWSInfo(wsinfo, u1, wsi.getName(), 0L, Permission.OWNER, false, "unlocked", mt);
	}
	
	@Test
	public void permissionsBulk() throws Exception {
		/* This test was added after the getPermissions method was converted
		 * to take a list of workspaces rather than a single workspace.
		 * Hence it mostly tests the aspects of the method dealing with
		 * multiple workspaces - the prior tests, which exercise the same
		 * method, test the remainder of the functionality.
		 */
		WorkspaceIdentifier wiow = new WorkspaceIdentifier("permmass-owner");
		WorkspaceIdentifier wiad = new WorkspaceIdentifier("permmass-admin");
		WorkspaceIdentifier wiwr = new WorkspaceIdentifier("permmass-write");
		WorkspaceIdentifier wird = new WorkspaceIdentifier("permmass-read");
		WorkspaceIdentifier wigr = new WorkspaceIdentifier("permmass-globalread");
		WorkspaceIdentifier wino = new WorkspaceIdentifier("permmass-none");
		ws.createWorkspace(AUSER, wiow.getName(), false, null, null).getId();
		ws.createWorkspace(BUSER, wiad.getName(), false, null, null).getId();
		ws.createWorkspace(BUSER, wiwr.getName(), false, null, null).getId();
		ws.createWorkspace(CUSER, wird.getName(), false, null, null).getId();
		ws.createWorkspace(CUSER, wigr.getName(), false, null, null).getId();
		ws.createWorkspace(CUSER, wino.getName(), false, null, null).getId();
		ws.setPermissions(BUSER, wiad, Arrays.asList(AUSER), Permission.ADMIN);
		ws.setPermissions(BUSER, wiwr, Arrays.asList(AUSER), Permission.WRITE);
		ws.setPermissions(CUSER, wird, Arrays.asList(AUSER), Permission.READ);
		ws.setGlobalPermission(CUSER, wigr, Permission.READ);
		
		
		List<WorkspaceIdentifier> wsis = new LinkedList<WorkspaceIdentifier>(
				Arrays.asList(wiow, wiad, wiwr, wird, wigr, wino));
		Map<User, Permission> e1 = new HashMap<User, Permission>();
		e1.put(AUSER, Permission.OWNER);
		Map<User, Permission> e2 = new HashMap<User, Permission>();
		e2.put(AUSER, Permission.ADMIN);
		e2.put(BUSER, Permission.OWNER);
		Map<User, Permission> e3 = new HashMap<User, Permission>();
		e3.put(AUSER, Permission.WRITE);
		e3.put(BUSER, Permission.OWNER);
		Map<User, Permission> e4 = new HashMap<User, Permission>();
		e4.put(AUSER, Permission.READ);
		Map<User, Permission> e5 = new HashMap<User, Permission>();
		e5.put(AUSER, Permission.NONE);
		e5.put(STARUSER, Permission.READ);
		Map<User, Permission> e6 = new HashMap<User, Permission>();
		e6.put(AUSER, Permission.NONE);
		List<Map<User, Permission>> exp = Arrays.asList(e1, e2, e3, e4, e5, e6);
		List<Map<User, Permission>> got = ws.getPermissions(AUSER, wsis);
		assertThat("got correct mass permissions", got, is(exp));
		ws.setGlobalPermission(CUSER, wigr, Permission.NONE);
		
		failGetPermissions(AUSER, null, new NullPointerException(
				"wslist cannot be null"));
		
		List<WorkspaceIdentifier> huge = new LinkedList<WorkspaceIdentifier>();
		for (int i = 1; i <= 1002; i++) {
			huge.add(new WorkspaceIdentifier(i));
		}
		failGetPermissions(AUSER, huge, new IllegalArgumentException(
				"Maximum number of workspaces allowed for input is 1000"));
		
		ws.setWorkspaceDeleted(AUSER, wiow, true);
		failGetPermissions(AUSER, wsis, new NoSuchWorkspaceException(
				String.format("Workspace %s is deleted", wiow.getName()), wiow));
		ws.setWorkspaceDeleted(AUSER, wiow, false);
		
		wsis.add(new WorkspaceIdentifier("permmass-doesntexist"));
		failGetPermissions(AUSER, wsis, new NoSuchWorkspaceException(
				"No workspace with name permmass-doesntexist exists", wiow));
		
		wsis.remove(wsis.size() - 1);
		wsis.add(new WorkspaceIdentifier(100000000));
		failGetPermissions(AUSER, wsis, new NoSuchWorkspaceException(
				"No workspace with id 100000000 exists", wiow));
	}
	
	@Test
	public void permissionsAsAdmin() throws Exception {
		/* only tests the asAdmin method. Remainder of the permissions tests test everything else.
		 */
		WorkspaceIdentifier wiow = new WorkspaceIdentifier("owner");
		WorkspaceIdentifier wiad = new WorkspaceIdentifier("admin");
		WorkspaceIdentifier wiwr = new WorkspaceIdentifier("write");
		WorkspaceIdentifier wird = new WorkspaceIdentifier("read");
		WorkspaceIdentifier wigr = new WorkspaceIdentifier("globalread");
		WorkspaceIdentifier wino = new WorkspaceIdentifier("none");
		ws.createWorkspace(AUSER, wiow.getName(), false, null, null).getId();
		ws.createWorkspace(BUSER, wiad.getName(), false, null, null).getId();
		ws.createWorkspace(BUSER, wiwr.getName(), false, null, null).getId();
		ws.createWorkspace(CUSER, wird.getName(), false, null, null).getId();
		ws.createWorkspace(CUSER, wigr.getName(), false, null, null).getId();
		ws.createWorkspace(CUSER, wino.getName(), false, null, null).getId();
		ws.setPermissions(BUSER, wiad, Arrays.asList(AUSER), Permission.ADMIN);
		ws.setPermissions(BUSER, wiwr, Arrays.asList(AUSER), Permission.WRITE);
		ws.setPermissions(CUSER, wird, Arrays.asList(AUSER), Permission.READ);
		ws.setGlobalPermission(CUSER, wigr, Permission.READ);
		
		
		List<WorkspaceIdentifier> wsis = new LinkedList<WorkspaceIdentifier>(
				Arrays.asList(wiow, wiad, wiwr, wird, wigr, wino));
		
				Map<User, Permission> e1 = new HashMap<User, Permission>();
		e1.put(AUSER, Permission.OWNER);
		Map<User, Permission> e2 = new HashMap<User, Permission>();
		e2.put(AUSER, Permission.ADMIN);
		e2.put(BUSER, Permission.OWNER);
		Map<User, Permission> e3 = new HashMap<User, Permission>();
		e3.put(AUSER, Permission.WRITE);
		e3.put(BUSER, Permission.OWNER);
		Map<User, Permission> e4 = new HashMap<User, Permission>();
		e4.put(CUSER, Permission.OWNER);
		e4.put(AUSER, Permission.READ);
		Map<User, Permission> e5 = new HashMap<User, Permission>();
		e5.put(CUSER, Permission.OWNER);
		e5.put(STARUSER, Permission.READ);
		Map<User, Permission> e6 = new HashMap<User, Permission>();
		e6.put(CUSER, Permission.OWNER);
		List<Map<User, Permission>> exp = Arrays.asList(e1, e2, e3, e4, e5, e6);
		List<Map<User, Permission>> got = ws.getPermissionsAsAdmin(wsis);
		assertThat("got correct mass permissions", got, is(exp));
		ws.setGlobalPermission(CUSER, wigr, Permission.NONE);
		
		failGetPermissionsAsAdmin(null, new NullPointerException(
				"wslist cannot be null"));
		
		List<WorkspaceIdentifier> huge = new LinkedList<WorkspaceIdentifier>();
		for (int i = 1; i <= 1002; i++) {
			huge.add(new WorkspaceIdentifier(i));
		}
		failGetPermissionsAsAdmin(huge, new IllegalArgumentException(
				"Maximum number of workspaces allowed for input is 1000"));
		
		ws.setWorkspaceDeleted(AUSER, wiow, true);
		failGetPermissionsAsAdmin(wsis, new NoSuchWorkspaceException(
				String.format("Workspace %s is deleted", wiow.getName()), wiow));
		ws.setWorkspaceDeleted(AUSER, wiow, false);
		
		wsis.add(new WorkspaceIdentifier("doesntexist"));
		failGetPermissionsAsAdmin(wsis, new NoSuchWorkspaceException(
				"No workspace with name doesntexist exists", wiow));
		
		wsis.remove(wsis.size() - 1);
		wsis.add(new WorkspaceIdentifier(100000000));
		failGetPermissionsAsAdmin(wsis, new NoSuchWorkspaceException(
				"No workspace with id 100000000 exists", wiow));
		
	}

	@Test
	public void permissions() throws Exception {
		//setup
		WorkspaceIdentifier wsiNG = new WorkspaceIdentifier("perms_noglobal");
		ws.createWorkspace(AUSER, "perms_noglobal", false, null, null);
		WorkspaceIdentifier wsiGL = new WorkspaceIdentifier("perms_global");
		ws.createWorkspace(AUSER, "perms_global", true, "globaldesc", null);
		Map<User, Permission> expect = new HashMap<User, Permission>();
		
		//try some illegal ops
		try {
			ws.getWorkspaceDescription(null, wsiNG);
			fail("Able to get private workspace description with no user name");
		} catch (Exception e) {
			assertThat("Correct exception message", e.getLocalizedMessage(),
					is("Anonymous users may not read workspace perms_noglobal"));
		}
		try {
			ws.getWorkspaceInformation(null, wsiNG);
			fail("Able to get private workspace metadata with no user name");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("Correct exception message", e.getLocalizedMessage(),
					is("Anonymous users may not read workspace perms_noglobal"));
		}
		failSetPermissions(null, wsiNG, Arrays.asList(AUSER, BUSER, CUSER), Permission.READ,
				new WorkspaceAuthorizationException(
						"Anonymous users may not set permissions on workspace perms_noglobal"));
		failSetPermissions(null, wsiNG, null, Permission.READ,
				new IllegalArgumentException("The users list may not be null or empty"));
		failSetPermissions(null, wsiNG, new LinkedList<WorkspaceUser>(), Permission.READ,
				new IllegalArgumentException("The users list may not be null or empty"));
		failSetPermissions(AUSER, wsiNG, Arrays.asList(AUSER, BUSER, CUSER), Permission.OWNER,
				new IllegalArgumentException("Cannot set owner permission"));
		failSetPermissions(BUSER, wsiNG, Arrays.asList(AUSER, BUSER, CUSER), Permission.READ,
				new WorkspaceAuthorizationException("User b may not set permissions on workspace perms_noglobal"));
		//check basic permissions for new private and public workspaces
		expect.put(AUSER, Permission.OWNER);
		assertThat("ws has correct perms for owner", ws.getPermissions(
				AUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		expect.put(STARUSER, Permission.READ);
		assertThat("ws has correct perms for owner", ws.getPermissions(
				AUSER, Arrays.asList(wsiGL)).get(0), is(expect));
		expect.clear();
		expect.put(BUSER, Permission.NONE);
		assertThat("ws has correct perms for random user", ws.getPermissions(
				BUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		expect.put(STARUSER, Permission.READ);
		assertThat("ws has correct perms for random user", ws.getPermissions(
				BUSER, Arrays.asList(wsiGL)).get(0), is(expect));
		//test read permissions
		assertThat("can read public workspace description", ws.getWorkspaceDescription(null, wsiGL),
				is("globaldesc"));
		WorkspaceInformation info = ws.getWorkspaceInformation(null, wsiGL);
		checkWSInfo(info, AUSER, "perms_global", 0, Permission.NONE, true, "unlocked", MT_MAP);
		final long id = ws.setPermissions(AUSER, wsiNG, Arrays.asList(AUSER, BUSER, CUSER),
				Permission.READ);
		assertThat("incorrect ws id", id, is(1L));
		expect.clear();
		expect.put(AUSER, Permission.OWNER);
		expect.put(BUSER, Permission.READ);
		expect.put(CUSER, Permission.READ);
		assertThat("ws doesn't replace owner perms", ws.getPermissions(
				AUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		expect.clear();
		expect.put(BUSER, Permission.READ);
		assertThat("no permission leakage", ws.getPermissions(
				BUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		
		failSetPermissions(BUSER, wsiNG, Arrays.asList(AUSER, BUSER, CUSER), Permission.READ,
				new WorkspaceAuthorizationException(
						"User b may not alter other user's permissions on workspace perms_noglobal"));
		failSetPermissions(BUSER, wsiNG, Arrays.asList(BUSER), Permission.WRITE,
				new WorkspaceAuthorizationException(
						"User b may only reduce their permission level on workspace perms_noglobal"));
		
		//asAdmin testing
		final long id2 = ws.setPermissions(BUSER, wsiNG, Arrays.asList(BUSER), Permission.ADMIN,
				true);
		assertThat("incorrect ws id", id2, is(1L));
		expect.put(AUSER, Permission.OWNER);
		expect.put(BUSER, Permission.ADMIN);
		expect.put(CUSER, Permission.READ);
		assertThat("asAdmin boolean works", ws.getPermissions(
				BUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		final long id3 = ws.setPermissions(BUSER, wsiNG, Arrays.asList(BUSER), Permission.READ);
		assertThat("incorrect ws id", id3, is(1L));
		expect.clear();
		expect.put(BUSER, Permission.READ);
		assertThat("reduce own permissions", ws.getPermissions(
				BUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		final long id4 = ws.setPermissions(null, wsiNG, Arrays.asList(BUSER), Permission.ADMIN,
				true);
		assertThat("incorrect ws id", id4, is(1L));
		expect.put(AUSER, Permission.OWNER);
		expect.put(BUSER, Permission.ADMIN);
		expect.put(CUSER, Permission.READ);
		assertThat("asAdmin boolean works with null user",ws.getPermissions(
				BUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		ws.setPermissions(AUSER, wsiNG, Arrays.asList(BUSER), Permission.READ);
		expect.clear();
		expect.put(BUSER, Permission.READ);
		assertThat("reduced permissions", ws.getPermissions(
				BUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		
		
		ws.setPermissions(BUSER, wsiNG, Arrays.asList(BUSER), Permission.READ); //should have no effect
		expect.clear();
		expect.put(AUSER, Permission.OWNER);
		expect.put(BUSER, Permission.READ);
		expect.put(CUSER, Permission.READ);
		assertThat("user setting same perms has no effect", ws.getPermissions(
				AUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		expect.clear();
		expect.put(BUSER, Permission.READ);
		assertThat("setting own perms to same has no effect", ws.getPermissions(
				BUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		
		ws.setPermissions(BUSER, wsiNG, Arrays.asList(BUSER), Permission.NONE);
		expect.clear();
		expect.put(AUSER, Permission.OWNER);
		expect.put(CUSER, Permission.READ);
		assertThat("user removed own perms", ws.getPermissions(
				AUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		expect.clear();
		expect.put(BUSER, Permission.NONE);
		assertThat("can remove own perms", ws.getPermissions(
				BUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		
		//test write permissions
		ws.setPermissions(AUSER, wsiNG, Arrays.asList(BUSER), Permission.WRITE);
		expect.put(AUSER, Permission.OWNER);
		expect.put(BUSER, Permission.WRITE);
		expect.put(CUSER, Permission.READ);
		assertThat("ws doesn't replace owner perms", ws.getPermissions(
				AUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		assertThat("write perms allow viewing all perms", ws.getPermissions(
				BUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		expect.clear();
		expect.put(CUSER, Permission.READ);
		assertThat("no permission leakage", ws.getPermissions(
				CUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		failSetPermissions(BUSER, wsiNG, Arrays.asList(AUSER, BUSER, CUSER), Permission.READ,
				new WorkspaceAuthorizationException(
						"User b may not alter other user's permissions on workspace perms_noglobal"));
		//test admin permissions
		ws.setPermissions(AUSER, wsiNG, Arrays.asList(BUSER), Permission.ADMIN);
		expect.put(AUSER, Permission.OWNER);
		expect.put(BUSER, Permission.ADMIN);
		expect.put(CUSER, Permission.READ);
		assertThat("ws doesn't replace owner perms", ws.getPermissions(
				AUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		assertThat("admin can see all perms", ws.getPermissions(
				BUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		ws.setPermissions(BUSER, wsiNG, Arrays.asList(AUSER, CUSER), Permission.WRITE);
		expect.put(CUSER, Permission.WRITE);
		assertThat("ws doesn't replace owner perms", ws.getPermissions(
				AUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		assertThat("admin can correctly set perms", ws.getPermissions(
				BUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		//test remove permissions
		ws.setPermissions(BUSER, wsiNG, Arrays.asList(AUSER, CUSER), Permission.NONE);
		expect.remove(CUSER);
		assertThat("ws doesn't replace owner perms", ws.getPermissions(
				AUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		assertThat("admin can't overwrite owner perms", ws.getPermissions(
				BUSER, Arrays.asList(wsiNG)).get(0), is(expect));
		
		ws.setGlobalPermission(AUSER, new WorkspaceIdentifier("perms_global"), Permission.NONE);
	}
	
	@Test
	public void permissionsWithNoUser() throws Exception {
		/* Tests the case that no user credentials are supplied and thus the
		 * user is null. Only globally readable workspaces should return
		 * permissions other than NONE.
		 */
		
		WorkspaceIdentifier wsiNG = new WorkspaceIdentifier("PnoU_noglobal");
		ws.createWorkspace(AUSER, "PnoU_noglobal", false, null, null);
		WorkspaceIdentifier wsiGL = new WorkspaceIdentifier("PnoU_global");
		ws.createWorkspace(AUSER, "PnoU_global", true, "globaldesc", null);
		
		Map<User, Permission> expect = new HashMap<User, Permission>();
		
		assertThat("No permissions for private WS",
				ws.getPermissions(null, Arrays.asList(wsiNG)).get(0),
				is(expect));
		
		expect.put(STARUSER, Permission.READ);
		
		assertThat("Read permissions for public WS",
				ws.getPermissions(null, Arrays.asList(wsiGL)).get(0),
				is(expect));
		
		ws.setGlobalPermission(AUSER, wsiGL, Permission.NONE);
	}
	
	@Test
	public void saveObjectsAndGetMetaSimple() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceUser bar = new WorkspaceUser("bar");
		
		IdReferenceHandlerSetFactory foofac = getIdFactory();
		IdReferenceHandlerSetFactory barfac = getIdFactory();
		
		WorkspaceIdentifier read = new WorkspaceIdentifier("saveobjread");
		WorkspaceIdentifier priv = new WorkspaceIdentifier("saveobj");
		WorkspaceInformation readinfo = ws.createWorkspace(
				foo, read.getIdentifierString(), true, null, null);
		WorkspaceInformation privinfo = ws.createWorkspace(
				foo, priv.getIdentifierString(), false, null, null);
		Instant readLastDate = readinfo.getModDate();
		Instant privLastDate = privinfo.getModDate();
		long readid = readinfo.getId();
		long privid = privinfo.getId();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> data2 = new HashMap<String, Object>();
		Map<String, String> premeta = new HashMap<String, String>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		JsonNode savedata = MAPPER.valueToTree(data);
		data2.put("fubar2", moredata);
		JsonNode savedata2 = MAPPER.valueToTree(data2);
		premeta.put("metastuff", "meta");
		WorkspaceUserMetadata meta = new WorkspaceUserMetadata(premeta);
		Map<String, String> premeta2 = new HashMap<String, String>();
		premeta2.put("meta2", "my hovercraft is full of eels");
		WorkspaceUserMetadata meta2 = new WorkspaceUserMetadata(premeta2);
		Provenance p = new Provenance(new WorkspaceUser("kbasetest2"));
		p.addAction(new Provenance.ProvenanceAction().withServiceName("some service"));
		List<WorkspaceSaveObject> objects = new ArrayList<WorkspaceSaveObject>();
		
		try {
			ws.saveObjects(foo, read, objects, foofac);
			fail("Saved no objects");
		} catch (IllegalArgumentException e) {
			assertThat("correct except", e.getLocalizedMessage(), is("No data provided"));
		}
		
		failGetObjects(foo, new ArrayList<ObjectIdentifier>(), new IllegalArgumentException(
				"No object identifiers provided"));
		
		try {
			ws.getObjectInformation(foo, new ArrayList<ObjectIdentifier>(), true, false);
			fail("called method with no identifiers");
		} catch (IllegalArgumentException e) {
			assertThat("correct except", e.getLocalizedMessage(), is("No object identifiers provided"));
		}
		
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto3"), savedata, SAFE_TYPE1,
				meta, p, false));
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto3"), savedata2, SAFE_TYPE1,
				meta2, p, false));
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto3-1"), savedata, SAFE_TYPE1,
				meta, p, false));
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto3-2"), savedata2,
				SAFE_TYPE1, meta2, p, false));
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto4"), savedata, SAFE_TYPE1,
				meta, p, false));
		
		readLastDate = ws.getWorkspaceInformation(foo, read).getModDate();
		List<ObjectInformation> objinfo = ws.saveObjects(foo, read, objects, foofac);
		readLastDate = assertWorkspaceDateUpdated(foo, read, readLastDate, "ws date modified on save");
		String chksum1 = "36c4f68f2c98971b9736839232eb08f4";
		String chksum2 = "3c59f762140806c36ab48a152f28e840";
		checkObjInfo(objinfo.get(0), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid,
				read.getName(), chksum1, 23, premeta, Arrays.asList(new Reference(readid, 1, 1)));
		checkObjInfo(objinfo.get(1), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid,
				read.getName(), chksum2, 24, premeta2, Arrays.asList(new Reference(readid, 1, 2)));
		checkObjInfo(objinfo.get(2), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 1, foo, readid,
				read.getName(), chksum1, 23, premeta, Arrays.asList(new Reference(readid, 2, 1)));
		checkObjInfo(objinfo.get(3), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid,
				read.getName(), chksum2, 24, premeta2, Arrays.asList(new Reference(readid, 3, 1)));
		checkObjInfo(objinfo.get(4), 4, "auto4", SAFE_TYPE1.getTypeString(), 1, foo, readid,
				read.getName(), chksum1, 23, premeta, Arrays.asList(new Reference(readid, 4, 1)));
		
		List<ObjectIdentifier> loi = new ArrayList<ObjectIdentifier>();
		loi.add(new ObjectIdentifier(read, 1));
		loi.add(new ObjectIdentifier(read, 1, 1));
		loi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), "auto3"));
		loi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), "auto3", 1));
		loi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), 1));
		loi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), 1, 1));
		loi.add(new ObjectIdentifier(read, "auto3"));
		loi.add(new ObjectIdentifier(read, "auto3", 1));
		loi.add(new ObjectIdentifier(read, "auto3-2"));
		loi.add(new ObjectIdentifier(read, 3));
		loi.add(new ObjectIdentifier(read, "auto3-2", 1));
		loi.add(new ObjectIdentifier(read, 3, 1));

		List<ObjectInformation> objinfo2 = ws.getObjectInformation(foo, loi, true, false);
		List<ObjectInformation> objinfo2NoMeta = ws.getObjectInformation(foo, loi, false, false);
		checkObjInfo(objinfo2.get(0), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo,
				readid, read.getName(), chksum2, 24, premeta2,
				Arrays.asList(new Reference(readid, 1, 2)));
		checkObjInfo(objinfo2.get(1), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum1, 23, premeta,
				Arrays.asList(new Reference(readid, 1, 1)));
		checkObjInfo(objinfo2.get(2), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo,
				readid, read.getName(), chksum2, 24, premeta2,
				Arrays.asList(new Reference(readid, 1, 2)));
		checkObjInfo(objinfo2.get(3), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum1, 23, premeta,
				Arrays.asList(new Reference(readid, 1, 1)));
		checkObjInfo(objinfo2.get(4), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo,
				readid, read.getName(), chksum2, 24, premeta2,
				Arrays.asList(new Reference(readid, 1, 2)));
		checkObjInfo(objinfo2.get(5), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum1, 23, premeta,
				Arrays.asList(new Reference(readid, 1, 1)));
		checkObjInfo(objinfo2.get(6), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo,
				readid, read.getName(), chksum2, 24, premeta2,
				Arrays.asList(new Reference(readid, 1, 2)));
		checkObjInfo(objinfo2.get(7), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum1, 23, premeta,
				Arrays.asList(new Reference(readid, 1, 1)));
		checkObjInfo(objinfo2.get(8), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum2, 24, premeta2,
				Arrays.asList(new Reference(readid, 3, 1)));
		checkObjInfo(objinfo2.get(9), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum2, 24, premeta2,
				Arrays.asList(new Reference(readid, 3, 1)));
		checkObjInfo(objinfo2.get(10), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum2, 24, premeta2,
				Arrays.asList(new Reference(readid, 3, 1)));
		checkObjInfo(objinfo2.get(11), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum2, 24, premeta2,
				Arrays.asList(new Reference(readid, 3, 1)));
		checkObjInfo(objinfo2NoMeta.get(0), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo,
				readid, read.getName(), chksum2, 24, null,
				Arrays.asList(new Reference(readid, 1, 2)));
		checkObjInfo(objinfo2NoMeta.get(1), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum1, 23, null,
				Arrays.asList(new Reference(readid, 1, 1)));
		checkObjInfo(objinfo2NoMeta.get(2), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo,
				readid, read.getName(), chksum2, 24, null,
				Arrays.asList(new Reference(readid, 1, 2)));
		checkObjInfo(objinfo2NoMeta.get(3), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum1, 23, null,
				Arrays.asList(new Reference(readid, 1, 1)));
		checkObjInfo(objinfo2NoMeta.get(4), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo,
				readid, read.getName(), chksum2, 24, null,
				Arrays.asList(new Reference(readid, 1, 2)));
		checkObjInfo(objinfo2NoMeta.get(5), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum1, 23, null,
				Arrays.asList(new Reference(readid, 1, 1)));
		checkObjInfo(objinfo2NoMeta.get(6), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo,
				readid, read.getName(), chksum2, 24, null,
				Arrays.asList(new Reference(readid, 1, 2)));
		checkObjInfo(objinfo2NoMeta.get(7), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum1, 23, null,
				Arrays.asList(new Reference(readid, 1, 1)));
		checkObjInfo(objinfo2NoMeta.get(8), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum2, 24, null,
				Arrays.asList(new Reference(readid, 3, 1)));
		checkObjInfo(objinfo2NoMeta.get(9), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum2, 24, null,
				Arrays.asList(new Reference(readid, 3, 1)));
		checkObjInfo(objinfo2NoMeta.get(10), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum2, 24, null,
				Arrays.asList(new Reference(readid, 3, 1)));
		checkObjInfo(objinfo2NoMeta.get(11), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo,
				readid, read.getName(), chksum2, 24, null,
				Arrays.asList(new Reference(readid, 3, 1)));
		
		List<ObjectInformation> retinfo = new ArrayList<ObjectInformation>();
		final ResolvedWorkspaceID fakews = new ResolvedWorkspaceID(
				readid, read.getName(), false, false);
		UncheckedUserMetadata umeta = new UncheckedUserMetadata(meta);
		UncheckedUserMetadata umeta2 = new UncheckedUserMetadata(meta2);
		retinfo.add(new ObjectInformation(1L, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 2,
				foo, fakews, chksum2, 24L, umeta2));
		retinfo.add(new ObjectInformation(1, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 1,
				foo, fakews, chksum1, 23, umeta));
		retinfo.add(new ObjectInformation(1, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 2,
				foo, fakews, chksum2, 24, umeta2));
		retinfo.add(new ObjectInformation(1, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 1,
				foo, fakews, chksum1, 23, umeta));
		retinfo.add(new ObjectInformation(1, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 2,
				foo, fakews, chksum2, 24, umeta2));
		retinfo.add(new ObjectInformation(1, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 1,
				foo, fakews, chksum1, 23, umeta));
		retinfo.add(new ObjectInformation(1, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 2,
				foo, fakews, chksum2, 24, umeta2));
		retinfo.add(new ObjectInformation(1, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 1,
				foo, fakews, chksum1, 23, umeta));
		retinfo.add(new ObjectInformation(3, "auto3-2", SAFE_TYPE1.getTypeString(), new Date(), 1,
				foo, fakews, chksum2, 24, umeta2));
		retinfo.add(new ObjectInformation(3, "auto3-2", SAFE_TYPE1.getTypeString(), new Date(), 1,
				foo, fakews, chksum2, 24, umeta2));
		retinfo.add(new ObjectInformation(3, "auto3-2", SAFE_TYPE1.getTypeString(), new Date(), 1,
				foo, fakews, chksum2, 24, umeta2));
		retinfo.add(new ObjectInformation(3, "auto3-2", SAFE_TYPE1.getTypeString(), new Date(), 1,
				foo, fakews, chksum2, 24, umeta2));
		List<Map<String, Object>> retdata = Arrays.asList(
				data2, data, data2, data, data2, data, data2, data, data2, data2, data2, data2);
		checkObjectAndInfo(foo, loi, retinfo, retdata);
		
		privLastDate = ws.getWorkspaceInformation(foo, priv).getModDate();
		ws.saveObjects(foo, priv, objects, foofac);
		privLastDate = assertWorkspaceDateUpdated(foo, read, privLastDate, "ws date modified on save");
		
		objects.clear();
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer(2), savedata, SAFE_TYPE1, meta2, p, false));
		objinfo = ws.saveObjects(foo, read, objects, foofac);
		ws.saveObjects(foo, priv, objects, foofac);
		checkObjInfo(objinfo.get(0), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 2, foo, readid,
				read.getName(), chksum1, 23, premeta2, Arrays.asList(new Reference(readid, 2, 2)));
		objinfo2 = ws.getObjectInformation(foo, Arrays.asList(new ObjectIdentifier(read, 2)), true,
				false);
		checkObjInfo(objinfo2.get(0), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 2, foo, readid,
				read.getName(), chksum1, 23, premeta2, Arrays.asList(new Reference(readid, 2, 2)));
		
		ws.getObjectInformation(bar, Arrays.asList(new ObjectIdentifier(read, 2)), true, false); //should work
		try {
			ws.getObjectInformation(bar, Arrays.asList(new ObjectIdentifier(priv, 2)), true, false);
			fail("Able to get obj meta from private workspace");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception message", ioe.getLocalizedMessage(),
					is("Object 2 cannot be accessed: User bar may not read workspace saveobj"));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is(new ObjectIdentifier(priv, 2)));
		}
		successGetObjects(bar, Arrays.asList(new ObjectIdentifier(read, 2)));
		try {
			ws.getObjects(bar, Arrays.asList(new ObjectIdentifier(priv, 2)));
			fail("Able to get obj data from private workspace");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception message", ioe.getLocalizedMessage(),
					is("Object 2 cannot be accessed: User bar may not read workspace saveobj"));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is(new ObjectIdentifier(priv, 2)));
		}

		ws.setPermissions(foo, priv, Arrays.asList(bar), Permission.READ);
		objinfo2 = ws.getObjectInformation(bar, Arrays.asList(new ObjectIdentifier(priv, 2)), true,
				false);
		checkObjInfo(objinfo2.get(0), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 2, foo, privid,
				priv.getName(), chksum1, 23, premeta2, Arrays.asList(new Reference(privid, 2, 2)));
		
		checkObjectAndInfo(bar, Arrays.asList(new ObjectIdentifier(priv, 2)),
				Arrays.asList(new ObjectInformation(2L, "auto3-1", SAFE_TYPE1.getTypeString(),
						new Date(), 2, foo,
						new ResolvedWorkspaceID(privid, priv.getName(), false, false),
						chksum1, 23L, umeta2)), Arrays.asList(data));
		
		failSave(bar, priv, objects, new WorkspaceAuthorizationException("User bar may not write to workspace saveobj"));
		
		ws.setPermissions(foo, priv, Arrays.asList(bar), Permission.WRITE);
		objinfo = ws.saveObjects(bar, priv, objects, barfac);
		checkObjInfo(objinfo.get(0), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 3, bar, privid,
				priv.getName(), chksum1, 23, premeta2, Arrays.asList(new Reference(privid, 2, 3)));
		
		failGetObjects(foo, Arrays.asList(new ObjectIdentifier(read, "booger")),
				new NoSuchObjectException("No object with name booger exists in workspace 1 " +
						"(name saveobjread)", null));
		failGetObjects(foo, Arrays.asList(new ObjectIdentifier(
				new WorkspaceIdentifier("saveAndGetFakefake"), "booger")),
				new InaccessibleObjectException("Object booger cannot be accessed: No workspace " +
						"with name saveAndGetFakefake exists", null));
		ws.setPermissions(foo, priv, Arrays.asList(bar), Permission.NONE);
		failGetObjects(bar, Arrays.asList(new ObjectIdentifier(priv, 3)),
				new InaccessibleObjectException("Object 3 cannot be accessed: User bar may not " +
						"read workspace saveobj", null));
		failGetObjects(null, Arrays.asList(new ObjectIdentifier(priv, 3)),
				new InaccessibleObjectException("Object 3 cannot be accessed: Anonymous users "+
						"may not read workspace saveobj", null));
	}
	
	@Test
	public void getInaccessibleObjectsAsNulls() throws Exception {
		//test get object info where null is returned instead of exception
		
		// set up
		WorkspaceUser user1 = new WorkspaceUser("foo");
		WorkspaceUser user2 = new WorkspaceUser("bar");
		
		IdReferenceHandlerSetFactory foofac = getIdFactory();
		
		WorkspaceIdentifier glblws = new WorkspaceIdentifier("readglbl");
		WorkspaceIdentifier readws = new WorkspaceIdentifier("read");
		WorkspaceIdentifier privws = new WorkspaceIdentifier("priv");
		WorkspaceIdentifier delws = new WorkspaceIdentifier("del");
		WorkspaceInformation glblwsinf = ws.createWorkspace(
				user1, glblws.getIdentifierString(), true, null, null);
		WorkspaceInformation readwsinf = ws.createWorkspace(
				user1, readws.getIdentifierString(), false, null, null);
		WorkspaceInformation privwsinf = ws.createWorkspace(
				user1, privws.getIdentifierString(), false, null, null);
		WorkspaceInformation delwsinf = ws.createWorkspace(
				user1, delws.getIdentifierString(), false, null, null);
		long glblid = glblwsinf.getId();
		long readid = readwsinf.getId();
		long delid = delwsinf.getId();
		long privid = privwsinf.getId();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> data2 = new HashMap<String, Object>();
		Map<String, String> premeta = new HashMap<String, String>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		data2.put("fubar2", moredata);
		premeta.put("metastuff", "meta");
		WorkspaceUserMetadata meta = new WorkspaceUserMetadata(premeta);
		Map<String, String> premeta2 = new HashMap<String, String>();
		premeta2.put("meta2", "my hovercraft is full of eels");
		WorkspaceUserMetadata meta2 = new WorkspaceUserMetadata(premeta2);
		Provenance p = new Provenance(new WorkspaceUser("kbasetest2"));
		p.addAction(new Provenance.ProvenanceAction()
				.withServiceName("some service"));
		List<WorkspaceSaveObject> objects =
				new ArrayList<WorkspaceSaveObject>();
		
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("type1"),
				data, SAFE_TYPE1, meta, p, false));
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("type2del"),
				data2, SAFE_TYPE1, meta2, p, false));
		ObjectInformation rdobjinfo = ws.saveObjects(
				user1, readws, objects, foofac).get(0);
		ws.setObjectsDeleted(user1, Arrays.asList(
				new ObjectIdentifier(readws, "type2del")), true);
		
		objects.remove(1);
		
		ws.saveObjects(user1, privws, objects, foofac).get(0);
		ws.saveObjects(user1, delws, objects, foofac).get(0);
		ws.setWorkspaceDeleted(user1, delws, true);
		ObjectInformation globjinfo = ws.saveObjects(
				user1, glblws, objects, foofac).get(0);
		
		ws.setPermissions(user1, readws, Arrays.asList(user2),
				Permission.READ);
		ws.setPermissions(user1, privws, Arrays.asList(user2),
				Permission.NONE);
		
		//tests
		
		// test bad workspace name, bad object name, bad ids, bad ver
		List<ObjectIdentifier> nullloi = new ArrayList<ObjectIdentifier>();
		nullloi.add(new ObjectIdentifier(new WorkspaceIdentifier(glblid), 1));
		nullloi.add(new ObjectIdentifier(glblws, "booger"));
		nullloi.add(new ObjectIdentifier(
				new WorkspaceIdentifier("saveAndGetFakefake"), 1));
		nullloi.add(new ObjectIdentifier(glblws, "type1", 1));
		nullloi.add(new ObjectIdentifier(new WorkspaceIdentifier(5), 1));
		nullloi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), 1));
		nullloi.add(new ObjectIdentifier(readws, 1, 2));
		nullloi.add(new ObjectIdentifier(readws, 3));
		nullloi.add(new ObjectIdentifier(readws, "type1"));
		nullloi.add(new ObjectIdentifier(readws, 1, 1));
		
		checkObjectAndInfoWithNulls(user2, nullloi, Arrays.asList(
				globjinfo, null, null, globjinfo, null, rdobjinfo, null, null,
				rdobjinfo, rdobjinfo),
				Arrays.asList(
				data, null, null, data, null, data, null, null, data, data));
		
		// test with anonymous user
		checkObjectAndInfoWithNulls(null, nullloi, Arrays.asList(
				globjinfo, null, null, globjinfo, null, null, null, null,
				null, null),
				Arrays.asList(
				data, null, null, data, null, null, null, null, null, null));
		
		// test unreadable workspace
		nullloi.clear();
		nullloi.add(new ObjectIdentifier(new WorkspaceIdentifier(glblid), 1));
		nullloi.add(new ObjectIdentifier(privws, 1));
		nullloi.add(new ObjectIdentifier(readws, "type1", 1));
		nullloi.add(new ObjectIdentifier(
				new WorkspaceIdentifier(privid), "type1"));
		nullloi.add(new ObjectIdentifier(privws, "type1", 1));
		nullloi.add(new ObjectIdentifier(readws, 1));
		
		checkObjectAndInfoWithNulls(user2, nullloi, Arrays.asList(
				globjinfo, null, rdobjinfo, null, null, rdobjinfo),
				Arrays.asList(
				data, null,  data, null, null, data));
		
		// test deleted object and workspace
		nullloi.clear();
		nullloi.add(new ObjectIdentifier(readws, 1, 1));
		nullloi.add(new ObjectIdentifier(delws, 1));
		nullloi.add(new ObjectIdentifier(delws, 1, 1));
		nullloi.add(new ObjectIdentifier(
				new WorkspaceIdentifier(delid), "type1"));
		nullloi.add(new ObjectIdentifier(glblws, "type1", 1));
		nullloi.add(new ObjectIdentifier(readws, 2));
		nullloi.add(new ObjectIdentifier(readws, "type2", 1));
		nullloi.add(new ObjectIdentifier(readws, "type1", 1));
		
		checkObjectAndInfoWithNulls(user2, nullloi, Arrays.asList(
				rdobjinfo, null, null, null, globjinfo, null, null, rdobjinfo),
				Arrays.asList(
				data, null, null, null, data, null, null, data));
	}

	@Test
	public void metadataExtracted() throws Exception {
		String module = "TestMetaData";
		String spec =
				"module " + module + " {" +
					"/* @metadata ws val \n@metadata ws length(l) as Length of list*/"+
					"typedef structure { string val; list<int> l; } MyType;" +
				"};";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		types.requestModuleRegistration(userfoo, module);
		types.resolveModuleRegistration(module, true);
		types.compileNewTypeSpec(userfoo, spec, Arrays.asList("MyType"), null, null, false, null);
		TypeDefId MyType = new TypeDefId(new TypeDefName(module, "MyType"), 0, 1);
		WorkspaceIdentifier wspace = new WorkspaceIdentifier("metadatatest");
		ws.createWorkspace(userfoo, wspace.getName(), false, null, null);
		Provenance emptyprov = new Provenance(userfoo);
		
		// save an object and get back object info
		Map<String, Object> d1 = new LinkedHashMap<String, Object>();
		String val = "i should be a metadata";
		d1.put("val", val);
		d1.put("l", Arrays.asList(1,2,3,4,5,6,7,8));
		
		Map<String, String> metadata = new HashMap<String, String>();
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("d1"),d1, MyType,
						new WorkspaceUserMetadata(metadata), emptyprov, false)),
				getIdFactory());
		List <ObjectInformation> oi = ws.getObjectInformation(userfoo, Arrays.asList(new ObjectIdentifier(wspace, "d1")), true, true);
		assertThat("Getting back an object that was saved with automatic metadata extraction", oi,
				is(notNullValue()));
		assertThat("Getting back an object that was saved with automatic metadata extraction",
				oi.get(0), is(notNullValue()));
		
		// check that automatic metadata fields were populated correctly, and nothing else was added
		Map<String,String> savedUserMetaData = new HashMap<String, String>(
				oi.get(0).getUserMetaData().getMetadata());
		for(Entry<String,String> m : savedUserMetaData.entrySet()) {
			if(m.getKey().equals("val")) 
				assertThat("Extracted metadata must be correct", m.getValue(), is(val));
			if(m.getKey().equals("Length of list"))
				assertThat("Extracted metadata must be correct", m.getValue(), is("8"));
		}
		savedUserMetaData.remove("val");
		savedUserMetaData.remove("Length of list");
		assertThat("Only metadata we wanted was extracted", savedUserMetaData.size(), is(0));
		
		// now we do the same thing, but make sure 1) metadata set was added, and 2) metadata is overridden
		// by the extracted metadata
		metadata.put("Length of list","i am pretty sure it was 7");
		metadata.put("my_special_metadata", "yes");
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("d2"),d1, MyType,
						new WorkspaceUserMetadata(metadata), emptyprov, false)),
				getIdFactory());
		List <ObjectInformation> oi2 = ws.getObjectInformation(userfoo, Arrays.asList(new ObjectIdentifier(wspace, "d2")), true, true);
		assertThat("Getting back an object that was saved with automatic metadata extraction",
				oi2, is(notNullValue()));
		assertThat("Getting back an object that was saved with automatic metadata extraction",
				oi2.get(0), is(notNullValue()));
		
		savedUserMetaData = new HashMap<String, String>(
				oi2.get(0).getUserMetaData().getMetadata());
		for(Entry<String,String> m : savedUserMetaData.entrySet()) {
			if(m.getKey().equals("val"))
				assertThat("Extracted metadata must be correct", m.getValue(),
						is(val));
			if(m.getKey().equals("Length of list"))
				assertThat("Extracted metadata must be correct", m.getValue(),
						is("8"));
			if(m.getKey().equals("my_special_metadata"))
				assertThat("Extracted metadata must be correct", m.getValue(),
						is("yes"));
		}
		savedUserMetaData.remove("val");
		savedUserMetaData.remove("Length of list");
		savedUserMetaData.remove("my_special_metadata");
		assertThat("Only metadata we wanted was extracted", savedUserMetaData.size(), is(0));
	}
	
	@Test
	public void metadataExtractedLargeTest() throws Exception {
		//make sure all temporary files are deleted when errors occur here
		final ResourceUsageConfiguration oldcfg = ws.getResourceConfig();
		final ResourceUsageConfigurationBuilder build =
				new ResourceUsageConfigurationBuilder(oldcfg);
		ws.setResourceConfig(build.withMaxIncomingDataMemoryUsage(1).build());
		tfm.cleanup();
		
		String module = "TestLargeMetadata";
		String typeName = "BigMeta";
		String nestmeta = "." + TEXT100.substring(16) + "." +
				TEXT100 + TEXT100 + "." + TEXT100 + TEXT100 + "." +
				TEXT100 + TEXT100 + "." + TEXT100 + TEXT100;
		String oknestmeta = "oknest" + nestmeta;
		String badnestmeta = "badnest" + nestmeta;
		String spec =
				"module " + module + " {" +
					"typedef structure {" +
						"string " + TEXT100 + TEXT100 + ";" +
					"} nested1;" +
					"typedef structure {" +
						"nested1 " + TEXT100 + TEXT100 + ";" +
					"} nested2;" +
					"typedef structure {" +
						"nested2 " + TEXT100 + TEXT100 + ";" +
					"} nested3;" +
					"typedef structure {" +
						"nested3 " + TEXT100 + TEXT100 + ";" +
					"} nested4;" +
					"typedef structure {" +
						"nested4 " + TEXT100.substring(16) + ";" +
					"} nested5;" +
						
					"/*" +
					"@metadata ws val\n" +
					"@metadata ws val2\n" +
					"@metadata ws val3\n" +
					"@metadata ws length(l) as Length of list\n" +
					"@metadata ws " + oknestmeta + "\n" + 
					"@metadata ws " + badnestmeta + "\n" +
					"@optional oknest\n" +
					"@optional badnest\n" +
					"@optional val2\n" +
					"@optional val3\n" +
					"*/" +
					"typedef structure {" +
						"string a;" + 
						"string val;" +
						"string val2;" +
						"string val3;" +
						"list<int> l;" +
						"nested5 oknest;" +
						"nested5 badnest;" +
					"} " + typeName + ";" +
				"};";
		WorkspaceUser user = new WorkspaceUser("foo");
		types.requestModuleRegistration(user, module);
		types.resolveModuleRegistration(module, true);
		types.compileNewTypeSpec(user, spec, Arrays.asList(typeName), null, null,
				false, null);
		TypeDefId type = new TypeDefId(
				new TypeDefName(module, typeName), 0, 1);
		Provenance mtprov = new Provenance(user);
		WorkspaceIdentifier wsi = new WorkspaceIdentifier(
				"metadataExtractedLargeTest");
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		
		Map<String, Object> dBig = new LinkedHashMap<String, Object>();
		dBig.put("l", Arrays.asList(1,2,3,4,5,6,7,8));
		dBig.put("a", "a"); //force sort

		//test fail on large extracted values
		dBig.put("val", TEXT1000.substring(103));
		saveObject(user, wsi, null, dBig, type, "foo", mtprov); //should work
		dBig.put("val", TEXT1000.substring(102));
		failSave(user, wsi, "bar", dBig, type, mtprov,
				new IllegalArgumentException(
						"Object #1, bar: Total size of metadata key + value exceeds maximum of 900B for key val"));
		
		StringBuilder unicode = new StringBuilder();
		for (int i = 0; i < 224; i++) {
			unicode.appendCodePoint(0x1D120);
		}
		dBig.put("val", unicode.toString() + "a");
		saveObject(user, wsi, null, dBig, type, "foo", mtprov); //should work
		dBig.put("val", unicode.toString() + "af");
		failSave(user, wsi, "whee", dBig, type, mtprov,
				new IllegalArgumentException(
						"Object #1, whee: Total size of metadata key + value exceeds maximum of 900B for key val"));
		
		// test fail on extracted keys
		Map<String, String> n1 = new HashMap<String, String>();
		n1.put(TEXT100 + TEXT100, "thing");
		Map<String, Object> n2 = new HashMap<String, Object>();
		n2.put(TEXT100 + TEXT100, n1);
		Map<String, Object> n3 = new HashMap<String, Object>();
		n3.put(TEXT100 + TEXT100, n2);
		Map<String, Object> n4 = new HashMap<String, Object>();
		n4.put(TEXT100 + TEXT100, n3);
		Map<String, Object> n5 = new HashMap<String, Object>();
		n5.put(TEXT100.substring(16), n4);
		dBig.put("val", "foo");
		dBig.put("oknest", n5);
		saveObject(user, wsi, null, dBig, type, "foo", mtprov); //should work
		dBig.remove("oknest");
		dBig.put("badnest", n5);
		failSave(user, wsi, "baz", dBig, type, mtprov,
				new IllegalArgumentException(
						"Object #1, baz: Total size of metadata key + value exceeds maximum of 900B for key "
								+ badnestmeta));
		dBig.remove("badnest");
		
		// test fail when extracted metadata > limit
		StringBuilder bigVal = new StringBuilder();
		for (int i = 0; i < 16; i++) {
			bigVal.append(TEXT1000); //> 16kb now
		}
		dBig.put("val", bigVal.toString());
		failSave(user, wsi, "bigextractedmeta", dBig, type, mtprov,
				new IllegalArgumentException(
						"Object #1, bigextractedmeta: Extracted metadata from object exceeds limit of 16000B"));
		
		// test fail when supplied metadata + extracted metadata > limit
		Map<String, String> meta = new HashMap<String, String>();
		for (int i = 0; i < 17; i++) {
			meta.put("k" + (i < 10 ? "0" + i : i), TEXT1000.substring(103));
		}
		meta.put("val2", "wheee");
		meta.put("val3", TEXT1000.substring(653));
		dBig.put("val", TEXT100);
		dBig.put("val2", TEXT100);
		saveObject(user, wsi, meta, dBig, type, "whocares", mtprov); //should work
		
		meta.put("val3", TEXT1000.substring(652));
		failSave(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("whooop"), dBig, type,
				new WorkspaceUserMetadata(meta), mtprov, false)),
				new IllegalArgumentException(
						"Object #1, whooop: The user-provided metadata, when updated with object-extracted metadata, exceeds the allowed maximum of 16000B"));
	
		ws.setResourceConfig(oldcfg);
		TestCommon.assertNoTempFilesExist(tfm);
	}
	
	@Test
	public void metadataSaveLargeKeyValue() throws Exception {
		/* Test that large metadata keys & values (as allowed by the metadata
		 * container class) actually save. In mongo 2.4, objects with large
		 * metadata would save but the metadata wouldn't be indexed. In 2.6, an
		 * error is thrown.
		 */
		WorkspaceUser user = new WorkspaceUser("foo");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("foo");
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		Provenance mtprov = new Provenance(user);
		
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("a", TEXT1000.substring(101));
		saveObject(user, wsi, meta, new HashMap<String, String>(), SAFE_TYPE1, "foo", mtprov);
		
		meta.clear();
		meta.put(TEXT1000.substring(101), "b");
		saveObject(user, wsi, meta, new HashMap<String, String>(), SAFE_TYPE1, "foo", mtprov);
		
		meta.clear();
		meta.put(TEXT1000.substring(500), TEXT1000.substring(600));
		saveObject(user, wsi, meta, new HashMap<String, String>(), SAFE_TYPE1, "foo", mtprov);
	}
	
	@Test
	public void encodings() throws Exception {
		WorkspaceUser user = new WorkspaceUser("encodings");
		WorkspaceIdentifier wspace = new WorkspaceIdentifier("encodings");
		ws.createWorkspace(user, wspace.getName(), false, null, null);
		Provenance emptyprov = new Provenance(user);
		
		StringBuffer sb = new StringBuffer();
		sb.appendCodePoint(0x1F082);
		sb.append("a");
		sb.appendCodePoint(0x1F0C6);
		sb.append("b");
		sb.appendCodePoint(0x23824);
		sb.append("c");
		sb.appendCodePoint(0x1685);
		sb.append("d");
		sb.appendCodePoint(0x13B2);
		sb.append("e");
		sb.appendCodePoint(0x06E9);
		
		String s = sb.toString() + sb.toString();
		Map<String, Object> craycraymap = new HashMap<String, Object>();
		craycraymap.put(s + "42", Arrays.asList(s, s + "woot", s));
		craycraymap.put(s + "6", s);
		craycraymap.put(s + "3012", 1);
		String jsondata = MAPPER.writeValueAsString(craycraymap);
		
		List<Charset> csets = Arrays.asList(Charset.forName("UTF-8"),
				Charset.forName("UTF-16LE"), Charset.forName("UTF-16BE"),
				Charset.forName("UTF-32LE"), Charset.forName("UTF-32BE"));
		List<WorkspaceSaveObject> objs = new LinkedList<WorkspaceSaveObject>();
		for (Charset cs: csets) {
			objs.add(new WorkspaceSaveObject(getRandomName(),
					new JsonTokenStream(jsondata.getBytes(cs)),
					SAFE_TYPE1, null, emptyprov, false));
		}
		
		ws.saveObjects(user, wspace, objs, getIdFactory());
		List<WorkspaceObjectData> ret = ws.getObjects(user, Arrays.asList(
				new ObjectIdentifier(wspace, 1),
				new ObjectIdentifier(wspace, 2),
				new ObjectIdentifier(wspace, 3),
				new ObjectIdentifier(wspace, 4),
				new ObjectIdentifier(wspace, 5)));
		try {
			for (WorkspaceObjectData wod: ret) {
				assertThat("got correct object input in various encodings", getData(wod),
						is((Object) craycraymap));
		}
		} finally {
			destroyGetObjectsResources(ret);
		}
	}

	@Test
	public void saveNonStructuralObjects() throws Exception {
		String module = "TestNonStruct";
		String spec =
				"module " + module + " {" +
					"typedef string type1;" +
					"typedef list<string> type2;" +
					"typedef mapping<string, string> type3;" +
					"typedef tuple<string, string> type4;" +
					"typedef structure { string val; } type5;" +
				"};";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		types.requestModuleRegistration(userfoo, module);
		types.resolveModuleRegistration(module, true);
		types.compileNewTypeSpec(userfoo, spec, Arrays.asList(
				"type1", "type2", "type3", "type4", "type5"), 
				null, null, false, null);
		TypeDefId abstype1 = new TypeDefId(new TypeDefName(module, "type1"), 0, 1);
		TypeDefId abstype2 = new TypeDefId(new TypeDefName(module, "type2"), 0, 1);
		TypeDefId abstype3 = new TypeDefId(new TypeDefName(module, "type3"), 0, 1);
		TypeDefId abstype4 = new TypeDefId(new TypeDefName(module, "type4"), 0, 1);
		TypeDefId abstype5 = new TypeDefId(new TypeDefName(module, "type5"), 0, 1);
		WorkspaceIdentifier wspace = new WorkspaceIdentifier("nonstruct");
		ws.createWorkspace(userfoo, wspace.getName(), false, null, null);
		Provenance emptyprov = new Provenance(userfoo);
		Map<String, String> data3 = new HashMap<String, String>();
		data3.put("val", "2");
		try {
			ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
					getRandomName(), "data1", abstype1, null, emptyprov, false)),
					getIdFactory());
			fail("Method works but shouldn't");
		} catch (TypedObjectValidationException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains("structure"), is(true));
		}
		try {
			ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
					getRandomName(), Arrays.asList("data2"), abstype2, null, emptyprov, false)),
					getIdFactory());
			fail("Method works but shouldn't");
		} catch (TypedObjectValidationException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains("structure"), is(true));
		}
		try {
			ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
					getRandomName(), data3, abstype3, null, emptyprov, false)),
					getIdFactory());
			fail("Method works but shouldn't");
		} catch (TypedObjectValidationException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains("structure"), is(true));
		}
		try {
			ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
					getRandomName(), Arrays.asList("data4", "data4"), 
					abstype4, null, emptyprov, false)), getIdFactory());
			fail("Method works but shouldn't");
		} catch (TypedObjectValidationException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains("structure"), is(true));
		}
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data3, abstype5, null, emptyprov, false)),
				getIdFactory());
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void saveNulls() throws Exception {
		String module = "TestNull";
		String spec =
				"module " + module + " {" +
					"typedef structure { " +
					"  string val1; " +
					"  int val2; " +
					"  float val3; " +
					"} type1; " +
					"typedef structure { " +
					"  list<string> val; " +
					"} type2;" +
					"typedef structure { " +
					"  mapping<string,string> val; " +
					"} type3;" +
					"typedef structure { " +
					"  tuple<string,string> val; " +
					"} type4;" +
					"typedef structure { " +
					"  list<int> val; " +
					"} type5;" +
					"typedef structure { " +
					"  list<float> val; " +
					"} type6;" +
				"};";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		types.requestModuleRegistration(userfoo, module);
		types.resolveModuleRegistration(module, true);
		types.compileNewTypeSpec(userfoo, spec, Arrays.asList(
				"type1", "type2", "type3", "type4", "type5", "type6"), 
				null, null, false, null);
		WorkspaceIdentifier wspace = new WorkspaceIdentifier("nulls");
		ws.createWorkspace(userfoo, wspace.getName(), false, null, null);
		Provenance emptyprov = new Provenance(userfoo);
		TypeDefId abstype1 = new TypeDefId(new TypeDefName(module, "type1"), 0, 1);
		TypeDefId abstype2 = new TypeDefId(new TypeDefName(module, "type2"), 0, 1);
		TypeDefId abstype3 = new TypeDefId(new TypeDefName(module, "type3"), 0, 1);
		TypeDefId abstype4 = new TypeDefId(new TypeDefName(module, "type4"), 0, 1);
		TypeDefId abstype5 = new TypeDefId(new TypeDefName(module, "type5"), 0, 1);
		TypeDefId abstype6 = new TypeDefId(new TypeDefName(module, "type6"), 0, 1);
		Set<String> keys = new TreeSet<String>(Arrays.asList("val1", "val2", "val3"));
		
		//TODO TEST should try these tests with bytes vs. maps
		Map<String, Object> data1 = new LinkedHashMap<String, Object>();
		data1.put("val3", null);
		data1.put("val2", null);
		data1.put("val1", null);
		assertThat(keys, is(new TreeSet<String>(data1.keySet())));
		assertThat(data1.containsKey("val1"), is(true));
		assertThat(data1.get("val1"), is(nullValue()));
		long data1id = ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data1, abstype1, null, emptyprov, false)),
				getIdFactory()).get(0).getObjectId();
		final List<WorkspaceObjectData> objects = ws.getObjects(
				userfoo, Arrays.asList(new ObjectIdentifier(wspace, data1id)));
		final Map<String, Object> data1copy;
		try {
				data1copy = (Map<String, Object>) getData(objects.get(0));
		} finally {
			destroyGetObjectsResources(objects);
		}
		assertThat(keys, is(new TreeSet<String>(data1copy.keySet())));
		
		Map<String, Object> data2 = new LinkedHashMap<String, Object>();
		data2.put("val", null);
		failSave(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data2, abstype2, null, emptyprov, false)), 
				new TypedObjectValidationException(String.format(
						"Object #1, %s failed type checking:\ninstance type (null) does not " +
						"match any allowed primitive type (allowed: [\"array\"]), at /val",
						getLastRandomName())));
		data2.put("val", Arrays.asList((String)null));
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data2, abstype2, null, emptyprov, false)),
				getIdFactory());
		
		Map<String, Object> data3 = new LinkedHashMap<String, Object>();
		data3.put("val", null);
		failSave(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data3, abstype3, null, emptyprov, false)), 
				new TypedObjectValidationException(String.format(
						"Object #1, %s failed type checking:\ninstance type (null) does not match " +
						"any allowed primitive type (allowed: [\"object\"]), at /val",
						getLastRandomName())));
		Map<String, Object> innerMap = new LinkedHashMap<String, Object>();
		innerMap.put("key", null);
		data3.put("val", innerMap);
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data3, abstype3, null, emptyprov, false)),
				getIdFactory());
		innerMap.put(null, "foo");
		
		failSave(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data3, abstype3, null, emptyprov, false)), 
				new TypedObjectValidationException(String.format(
						"Object #1, %s failed type checking:\nKeys in maps/structures may not " +
						"be null", getLastRandomName())));
		
		Map<String, Object> data4 = new LinkedHashMap<String, Object>();
		data4.put("val", null);
		failSave(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data4, abstype4, null, emptyprov, false)), 
				new TypedObjectValidationException(String.format(
						"Object #1, %s failed type checking:\ninstance type (null) does not " +
						"match any allowed primitive type (allowed: [\"array\"]), at /val",
						getLastRandomName())));
		data4.put("val", Arrays.asList((String)null, (String)null));
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data4, abstype4, null, emptyprov, false)),
				getIdFactory());
		
		Map<String, Object> data5 = new LinkedHashMap<String, Object>();
		data5.put("val", Arrays.asList(2, (Integer)null, 1));
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data5, abstype5, null, emptyprov, false)),
				getIdFactory());
		
		Map<String, Object> data6 = new LinkedHashMap<String, Object>();
		data6.put("val", Arrays.asList(1.2, (Float)null, 3.6));
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data6, abstype6, null, emptyprov, false)),
				getIdFactory());
	}

	@Test
	public void saveEmptyStringKey() throws Exception {
		WorkspaceUser user = new WorkspaceUser("foo");

		WorkspaceIdentifier wspace = new WorkspaceIdentifier("saveEmptyStringKey");
		ws.createWorkspace(user, wspace.getName(), false, null, null);
		Provenance mtprov = new Provenance(user);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("", 3);
		//should work
		ws.saveObjects(user, wspace, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data, SAFE_TYPE1, null, mtprov, false)
				), getIdFactory());
		final List<WorkspaceObjectData> objects = ws.getObjects(
				user, Arrays.asList(new ObjectIdentifier(wspace, 1)));
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> dataObj = (Map<String, Object>) getData(objects.get(0));
			assertThat("data saved correctly", dataObj, is(data));
		} finally {
			destroyGetObjectsResources(objects);
		}
	}
	
	@Test
	public void saveObjectWithTypeChecking() throws Exception {
		final String specTypeCheck1 =
				"module TestTypeChecking {" +
					"/* @id ws */" +
					"typedef string reference;" +
					"typedef string some_id2;" +
					"/* @optional ref */ " + 
					"typedef structure {" +
						"int foo;" +
						"list<int> bar;" +
						"string baz;" +
						"reference ref;" +
					"} CheckType;" +
				"};";
		
		final String specTypeCheck2 =
				"module TestTypeChecking {" +
					"/* @id ws */" +
					"typedef string reference;" +
					"/* @optional ref\n" + 
					"   @optional map */" +
					"typedef structure {" +
						"int foo;" +
						"list<int> bar;" +
						"int baz;" +
						"reference ref;" +
						"mapping<string, string> map;" +
					"} CheckType;" +
				"};";
		
		final String specTypeCheckRefs =
				"module TestTypeCheckingRefType {" +
					"/* @id ws TestTypeChecking.CheckType */" +
					"typedef string reference;" +
					"/* @optional refmap */" +
					"typedef structure {" +
						"int foo;" +
						"list<int> bar;" +
						"string baz;" +
						"reference ref;" +
						"mapping<reference, string> refmap;" + 
					"} CheckRefType;" +
				"};";
		
		String mod = "TestTypeChecking";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		types.requestModuleRegistration(userfoo, mod);
		types.resolveModuleRegistration(mod, true);
		types.compileNewTypeSpec(userfoo, specTypeCheck1, Arrays.asList("CheckType"), null, null, false, null);
		TypeDefId abstype0 = new TypeDefId(new TypeDefName(mod, "CheckType"), 0, 1);
		TypeDefId abstype1 = new TypeDefId(new TypeDefName(mod, "CheckType"), 1, 0);
		TypeDefId abstype2 = new TypeDefId(new TypeDefName(mod, "CheckType"), 2, 0);
		TypeDefId relmintype0 = new TypeDefId(new TypeDefName(mod, "CheckType"), 0);
		TypeDefId relmintype1 = new TypeDefId(new TypeDefName(mod, "CheckType"), 1);
		TypeDefId relmintype2 = new TypeDefId(new TypeDefName(mod, "CheckType"), 2);
		TypeDefId relmaxtype = new TypeDefId(new TypeDefName(mod, "CheckType"));
		
		// test basic type checking with different versions
		WorkspaceIdentifier wspace = new WorkspaceIdentifier("typecheck");
		ws.createWorkspace(userfoo, wspace.getName(), false, null, null);
		Provenance emptyprov = new Provenance(userfoo);
		Map<String, Object> data1 = new HashMap<String, Object>();
		data1.put("foo", 3);
		data1.put("baz", "astring");
		data1.put("bar", Arrays.asList(-3, 1, 234567890));
		
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(getRandomName(), data1, abstype0, null, emptyprov, false)),
				getIdFactory()); //should work
		
		final ObjectIDNoWSNoVer fail = new ObjectIDNoWSNoVer("fail");
		failSave(userfoo, wspace, fail, data1, new TypeDefId("NoModHere.Foo"), emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\nModule doesn't exist: NoModHere"));
		failSave(userfoo, wspace, fail, data1, new TypeDefId("SomeModule.Foo"), emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\nUnable to locate type: " +
						"SomeModule.Foo"));
		
		failSave(userfoo, wspace, fail, data1, relmintype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\nThis type wasn't released yet " +
						"and you should be an owner to access unreleased version information"));
		failSave(userfoo, wspace, fail, data1, relmintype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\nUnable to locate type: " +
						"TestTypeChecking.CheckType-1"));
		failSave(userfoo, wspace, fail, data1, abstype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\nUnable to locate type: " +
						"TestTypeChecking.CheckType-1.0"));
		failSave(userfoo, wspace, fail, data1, relmaxtype, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\nThis type wasn't released yet " + 
						"and you should be an owner to access unreleased version information"));
		
		types.releaseTypes(userfoo, mod);
		
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject( //should work
				getRandomName(), data1, relmaxtype, null, emptyprov, false)),
				getIdFactory());
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject( //should work
				getRandomName(), data1, abstype0, null, emptyprov, false)),
				getIdFactory());
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject( //should work
				getRandomName(), data1, abstype1, null, emptyprov, false)),
				getIdFactory());
		failSave(userfoo, wspace, fail, data1, relmintype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\nThis type wasn't released yet " +
						"and you should be an owner to access unreleased version information"));
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject( //should work
				getRandomName(), data1, relmintype1, null, emptyprov, false)),
				getIdFactory());
		failSave(userfoo, wspace, fail, data1, relmintype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\nUnable to locate type: " +
						"TestTypeChecking.CheckType-2"));
		
		types.compileNewTypeSpec(userfoo, specTypeCheck2, null, null, null, false, null);
		
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject( //should work
				getRandomName(), data1, relmaxtype, null, emptyprov, false)),
				getIdFactory());
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject( //should work
				getRandomName(), data1, relmintype1, null, emptyprov, false)),
				getIdFactory());
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject( //should work
				getRandomName(), data1, abstype0, null, emptyprov, false)),
				getIdFactory());
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject( //should work
				getRandomName(), data1, abstype1, null, emptyprov, false)),
				getIdFactory());
		failSave(userfoo, wspace, fail, data1, abstype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\ninstance type (string) does " +
						"not match any allowed primitive type (allowed: [\"integer\"]), at /baz"));
		failSave(userfoo, wspace, fail, data1, relmintype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\nThis type wasn't released yet " +
						"and you should be an owner to access unreleased version information"));
		
		
		Map<String, Object> newdata = new HashMap<String, Object>(data1);
		newdata.put("baz", 1);
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), newdata, abstype2 , null, emptyprov, false)),
				getIdFactory());
		failSave(userfoo, wspace, fail, newdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\ninstance type (integer) does " +
						"not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, fail, newdata, abstype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\ninstance type (integer) does " +
						"not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, fail, newdata, relmaxtype, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\ninstance type (integer) does " +
						"not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, fail, newdata, relmintype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\ninstance type (integer) does " +
						"not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, fail, newdata, relmintype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\nThis type wasn't released yet " +
						"and you should be an owner to access unreleased version information"));
		
		types.releaseTypes(userfoo, mod);
		
		failSave(userfoo, wspace, fail, data1, relmaxtype, emptyprov, 
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\ninstance type (string) does " +
						"not match any allowed primitive type (allowed: [\"integer\"]), at /baz"));
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject( //should work
				getRandomName(), data1, relmintype1, null, emptyprov, false)),
				getIdFactory());
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject( //should work
				getRandomName(), data1, abstype0, null, emptyprov, false)),
				getIdFactory());
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject( //should work
				getRandomName(), data1, abstype1, null, emptyprov, false)),
				getIdFactory());
		failSave(userfoo, wspace, fail, data1, abstype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\ninstance type (string) does " +
						"not match any allowed primitive type (allowed: [\"integer\"]), at /baz"));
		failSave(userfoo, wspace, fail, data1, relmintype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\ninstance type (string) does " +
						"not match any allowed primitive type (allowed: [\"integer\"]), at /baz"));
		
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), newdata, abstype2 , null, emptyprov, false)),
				getIdFactory());
		failSave(userfoo, wspace, fail, newdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\ninstance type (integer) does " +
						"not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, fail, newdata, abstype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\ninstance type (integer) does " +
						"not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject( //should work
				getRandomName(), newdata, relmaxtype, null, emptyprov, false)),
				getIdFactory());
		failSave(userfoo, wspace, fail, newdata, relmintype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail failed type checking:\ninstance type (integer) does " +
						"not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject( //should work
				getRandomName(), newdata, relmintype2, null, emptyprov, false)),
				getIdFactory());
		
		
		// test non-parseable references and typechecking with object count
		List<WorkspaceSaveObject> data = new ArrayList<WorkspaceSaveObject>();
		
		final ObjectIDNoWSNoVer obj1 = new ObjectIDNoWSNoVer("obj1");
		final ObjectIDNoWSNoVer obj2 = new ObjectIDNoWSNoVer("obj2");
		data.add(new WorkspaceSaveObject(obj1, data1, abstype0, null, emptyprov, false));
		Map<String, Object> data2 = new HashMap<String, Object>(data1);
		data2.put("bar", Arrays.asList(-3, 1, "anotherstring"));
		data.add(new WorkspaceSaveObject(obj2, data2, abstype0, null, emptyprov, false));
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
				"Object #2, obj2 failed type checking:\ninstance type (string) does not " +
				"match any allowed primitive type (allowed: [\"integer\"]), at /bar/2"));
		
		data.set(1, new WorkspaceSaveObject(obj2, data2, abstype2, null, emptyprov, false));
		@SuppressWarnings("unchecked")
		List<Integer> intlist = (List<Integer>) data2.get("bar");
		intlist.set(2, 42);
		Map<String, Object> inner = new HashMap<String, Object>();
		inner.put("amapkey", 42);
		data2.put("map", inner);
		data2.put("baz", 1);
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
				"Object #2, obj2 failed type checking:\ninstance type (integer) does not match " +
				"any allowed primitive type (allowed: [\"string\"]), at /map/amapkey"));
		
		Map<String, Object> data3 = new HashMap<String, Object>(data1);
		data3.put("ref", "typecheck/1/1");
		data.set(1, new WorkspaceSaveObject(obj2, data3, abstype0, null, emptyprov, false));
		ws.saveObjects(userfoo, wspace, data, getIdFactory()); //should work
		
		Map<String, Object> data4 = new HashMap<String, Object>(data1);
		data4.put("ref", "foo/bar/baz");
		final ObjectIDNoWSNoVer obj3 = new ObjectIDNoWSNoVer("obj3");
		data.set(1, new WorkspaceSaveObject(obj3, data4, abstype0, null, emptyprov, false));
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
				"Object #2, obj3 has unparseable reference foo/bar/baz: Unable to parse version " +
				"portion of object reference foo/bar/baz to an integer at /ref"));
		
		Map<String, Object> data5 = new HashMap<String, Object>(data1);
		data5.put("ref", null);
		data.set(1, new WorkspaceSaveObject(obj3, data5, abstype0, null, emptyprov, false));
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
				"Object #2, obj3 failed type checking:\ninstance type (null) not allowed for " +
				"ID reference (allowed: [\"string\"]), at /ref"));
		
		Map<String, Object> data6 = new HashMap<String, Object>(data1);
		data6.put("ref", "");
		data.set(1, new WorkspaceSaveObject(obj3, data6, abstype0, null, emptyprov, false));
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
				"Object #2, obj3 failed type checking:\nUnparseable id  of type ws: IDs may not " +
				"be null or the empty string at /ref"));
		
		
		Provenance goodids = new Provenance(userfoo);
		goodids.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("typecheck/1/1")));
		data.set(1, new WorkspaceSaveObject(obj3, data3, abstype0, null, goodids, false));
		ws.saveObjects(userfoo, wspace, data, getIdFactory()); //should work
		
		Provenance badids = new Provenance(userfoo);
		badids.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("foo/bar/baz")));
		final ObjectIDNoWSNoVer obj4 = new ObjectIDNoWSNoVer("obj4");
		data.set(1, new WorkspaceSaveObject(obj4, data3, abstype0, null, badids, false));
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
				"Object #2, obj4 has unparseable provenance reference foo/bar/baz: Unable to " +
				"parse version portion of object reference foo/bar/baz to an integer"));
		
		badids = new Provenance(userfoo);
		badids.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList((String) null)));
		data.set(1, new WorkspaceSaveObject(obj4, data3, abstype0, null, badids, false));
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
				"Object #2, obj4 has a null provenance reference"));
		
		badids = new Provenance(userfoo);
		badids.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("")));
		data.set(1, new WorkspaceSaveObject(obj4, data3, abstype0, null, badids, false));
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
				"Object #2, obj4 has invalid provenance reference: IDs may not be null or the " +
				"empty string"));
		
		//test inaccessible references due to missing, deleted, or unreadable workspaces
		Map<String, Object> refdata = new HashMap<String, Object>(data1);
		refdata.put("ref", "thereisnoworkspaceofthisname/2/1");
		failSave(userfoo, wspace, fail, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail has invalid reference: No read access to id " +
						"thereisnoworkspaceofthisname/2/1: Object 2 cannot be accessed: No " +
						"workspace with name thereisnoworkspaceofthisname exists at /ref"));
		Provenance nowsref = new Provenance(userfoo);
		nowsref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("thereisnoworkspaceofthisname/2/1")));
		failSave(userfoo, wspace, new ObjectIDNoWSNoVer(67), data1, abstype0, nowsref,
				new TypedObjectValidationException(
						"Object #1, 67 has invalid provenance reference: No read access to id " +
						"thereisnoworkspaceofthisname/2/1: Object 2 cannot be accessed: No " +
						"workspace with name thereisnoworkspaceofthisname exists"));
		
		ws.createWorkspace(userfoo, "tobedeleted", false, null, null);
		ws.setWorkspaceDeleted(userfoo, new WorkspaceIdentifier("tobedeleted"), true);
		refdata.put("ref", "tobedeleted/2/1");
		failSave(userfoo, wspace, fail, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail has invalid reference: No read access to id " +
						"tobedeleted/2/1: Object 2 cannot be accessed: Workspace tobedeleted is " +
						"deleted at /ref"));
		Provenance delwsref = new Provenance(userfoo);
		delwsref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("tobedeleted/2/1")));
		failSave(userfoo, wspace, fail, data1, abstype0, delwsref,
				new TypedObjectValidationException(
						"Object #1, fail has invalid provenance reference: No read access to " +
						"id tobedeleted/2/1: Object 2 cannot be accessed: Workspace tobedeleted " +
						"is deleted"));
		
		ws.createWorkspace(new WorkspaceUser("stingyuser"), "stingyworkspace", false, null, null);
		refdata.put("ref", "stingyworkspace/2/1");
		failSave(userfoo, wspace, fail, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1, fail has invalid reference: No read access to id " +
						"stingyworkspace/2/1: Object 2 cannot be accessed: User foo may not " +
						"read workspace stingyworkspace at /ref"));
		Provenance privwsref = new Provenance(userfoo);
		privwsref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("stingyworkspace/2/1")));
		failSave(userfoo, wspace, fail, data1, abstype0, privwsref,
				new TypedObjectValidationException(
						"Object #1, fail has invalid provenance reference: No read access to " +
						"id stingyworkspace/2/1: Object 2 cannot be accessed: User foo may not " +
						"read workspace stingyworkspace"));
		
		//test inaccessible reference due to missing or deleted objects, incl bad versions
		ws.createWorkspace(userfoo, "referencetesting", false, null, null);
		WorkspaceIdentifier reftest = new WorkspaceIdentifier("referencetesting");
		ws.saveObjects(userfoo, reftest, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto1"), newdata, abstype2 , null, emptyprov, false)),
				getIdFactory());
		
		refdata.put("ref", "referencetesting/1/1");
		ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), refdata, abstype1 , null, emptyprov, false)), getIdFactory());
		Provenance goodref = new Provenance(userfoo);
		goodref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("referencetesting/1/1")));
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(getRandomName(), refdata, abstype1 , null, goodref,
						false)), getIdFactory());
		
		refdata.put("ref", "referencetesting/2/1");
		long refwsid = ws.getWorkspaceInformation(userfoo, reftest).getId();
		failSave(userfoo, wspace, fail, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(String.format(
						"Object #1, fail has invalid reference: There is no object with id " +
						"referencetesting/2/1: No object with id 2 exists in workspace %s " +
						 "(name referencetesting) at /ref", refwsid)));
		Provenance noobjref = new Provenance(userfoo);
		noobjref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(
				Arrays.asList("referencetesting/2/1")));
		failSave(userfoo, wspace, fail, data1, abstype0, noobjref,
				new TypedObjectValidationException(String.format(
						"Object #1, fail has invalid provenance reference: There is no object " +
						"with id referencetesting/2/1: No object with id 2 exists in workspace " +
						"%s (name referencetesting)", refwsid)));
		
		ws.saveObjects(userfoo, reftest, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto2"), newdata, abstype2 , null, emptyprov, false)),
				getIdFactory());
		ws.setObjectsDeleted(userfoo, Arrays.asList(new ObjectIdentifier(reftest, 2)), true);
		failSave(userfoo, wspace, fail, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(String.format(
						"Object #1, fail has invalid reference: There is no object with id " +
						"referencetesting/2/1: Object 2 (name auto2) in workspace %s " +
						"(name referencetesting) has been deleted at /ref", refwsid)));
		Provenance delobjref = new Provenance(userfoo);
		delobjref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(
				Arrays.asList("referencetesting/2/1")));
		failSave(userfoo, wspace, fail, data1, abstype0, delobjref,
				new TypedObjectValidationException(String.format(
						"Object #1, fail has invalid provenance reference: There is no object " +
						"with id referencetesting/2/1: Object 2 (name auto2) in workspace %s " +
						"(name referencetesting) has been deleted", refwsid)));
		
		refdata.put("ref", "referencetesting/1/2");
		failSave(userfoo, wspace, fail, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(String.format(
						"Object #1, fail has invalid reference: There is no object with id " +
						"referencetesting/1/2: No object with id 1 (name auto1) and version 2 " +
						"exists in workspace %s (name referencetesting) at /ref", refwsid)));
		Provenance noverref = new Provenance(userfoo);
		noverref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(
				Arrays.asList("referencetesting/1/2")));
		failSave(userfoo, wspace, fail, data1, abstype0, noverref,
				new TypedObjectValidationException(String.format(
						"Object #1, fail has invalid provenance reference: There is no object " +
						"with id referencetesting/1/2: No object with id 1 (name auto1) and " +
						"version 2 exists in workspace %s (name referencetesting)", refwsid)));
		
		//TODO GC test references against garbage collected objects
		
		//test reference type checking
		String refmod = "TestTypeCheckingRefType";
		types.requestModuleRegistration(userfoo, refmod);
		types.resolveModuleRegistration(refmod, true);
		types.compileNewTypeSpec(userfoo, specTypeCheckRefs, Arrays.asList("CheckRefType"), null, null, false, null);
		TypeDefId absreftype0 = new TypeDefId(new TypeDefName(refmod, "CheckRefType"), 0, 1);

		ws.createWorkspace(userfoo, "referencetypecheck", false, null, null);
		WorkspaceIdentifier reftypecheck = new WorkspaceIdentifier("referencetypecheck");
		long reftypewsid = ws.getWorkspaceInformation(userfoo, reftypecheck).getId();
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto1"), newdata, SAFE_TYPE1 , null, emptyprov, false)),
				getIdFactory());
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto2"), newdata, abstype2 , null, emptyprov, false)),
				getIdFactory());
		
		refdata.put("ref", "referencetypecheck/2/1");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), refdata, absreftype0, null, emptyprov, false)),
				getIdFactory()); //should work
		
		refdata.put("ref", "referencetypecheck/2");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), refdata, absreftype0, null, emptyprov, false)),
				getIdFactory()); //should work
		
		refdata.put("ref", "referencetypecheck/auto2/1");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), refdata, absreftype0, null, emptyprov, false)),
				getIdFactory()); //should work
		
		refdata.put("ref", "referencetypecheck/auto2");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), refdata, absreftype0, null, emptyprov, false)),
				getIdFactory()); //should work
		
		refdata.put("ref", reftypewsid + "/2/1");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), refdata, absreftype0, null, emptyprov, false)),
				getIdFactory()); //should work
		
		refdata.put("ref", reftypewsid + "/2");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), refdata, absreftype0, null, emptyprov, false)),
				getIdFactory()); //should work
		
		refdata.put("ref", reftypewsid + "/auto2/1");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), refdata, absreftype0, null, emptyprov, false)),
				getIdFactory()); //should work
		
		refdata.put("ref", reftypewsid + "/auto2");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), refdata, absreftype0, null, emptyprov, false)),
				getIdFactory()); //should work

		String err = "Object #1, fail has invalid reference: The type " +
				"SomeModule.AType-0.1 of reference %s in this object is not " + 
				"allowed - allowed types are [TestTypeChecking.CheckType] at /ref";
		
		refdata.put("ref", "referencetypecheck/1/1");
		failSave(userfoo, reftypecheck, fail, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						"referencetypecheck/1/1")));
		
		refdata.put("ref", "referencetypecheck/1");
		failSave(userfoo, reftypecheck, fail, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						"referencetypecheck/1")));
		
		refdata.put("ref", "referencetypecheck/auto1/1");
		failSave(userfoo, reftypecheck, fail, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						"referencetypecheck/auto1/1")));
		
		refdata.put("ref", "referencetypecheck/auto1");
		failSave(userfoo, reftypecheck, fail, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						"referencetypecheck/auto1")));
		
		refdata.put("ref", reftypewsid + "/1/1");
		failSave(userfoo, reftypecheck, fail, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						reftypewsid + "/1/1")));
		
		refdata.put("ref", reftypewsid + "/1");
		failSave(userfoo, reftypecheck, fail, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						reftypewsid + "/1")));
		
		refdata.put("ref", reftypewsid + "/auto1/1");
		failSave(userfoo, reftypecheck, fail, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						reftypewsid + "/auto1/1")));
		
		refdata.put("ref", reftypewsid + "/auto1");
		failSave(userfoo, reftypecheck, fail, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						reftypewsid + "/auto1")));

		//check references were rewritten correctly
		for (int i = 3; i < 11; i++) {
			WorkspaceObjectData wod = ws.getObjects(userfoo, Arrays.asList(
					new ObjectIdentifier(reftypecheck, i))).get(0);
			try {
				@SuppressWarnings("unchecked")
				Map<String, Object> obj = (Map<String, Object>) getData(wod);
				assertThat("reference rewritten correctly",
						(String) obj.get("ref"), is(reftypewsid + "/2/1"));
			} finally {
				destroyGetObjectsResources(Arrays.asList(wod));
			}
			assertThat("reference included correctly", wod.getReferences(),
					is(Arrays.asList(reftypewsid + "/2/1")));
			
			WorkspaceObjectData inf = ws.getObjects(userfoo, Arrays.asList(
					new ObjectIdentifier(reftypecheck, i)), true).get(0);
			assertThat("sub obj reference included correctly", inf.getReferences(),
					is(Arrays.asList(reftypewsid + "/2/1")));
		}
	}
	
	@Test
	public void wsIdErrorOrder() throws Exception {
		//test that an id error returns the right id if multiple IDs exist
		WorkspaceUser user = new WorkspaceUser("user1");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("wsIdErrorOrder");
		ws.createWorkspace(user, wsi.getName(), false, null, null).getId();
		List<WorkspaceSaveObject> objs = new LinkedList<WorkspaceSaveObject>();
		Map<String, Object> d = new HashMap<String, Object>();
		Provenance mtprov = new Provenance(user);
		objs.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto1"), d, SAFE_TYPE1, null,
				mtprov, false));
		ws.saveObjects(user, wsi, objs, new IdReferenceHandlerSetFactory(0));
		
		Provenance p = new Provenance(user).addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList(
						wsi.getName() + "/auto1", wsi.getName() + "/auto2")));
		objs.set(0, new WorkspaceSaveObject(getRandomName(), d, SAFE_TYPE1, null, p, false));
		failSave(user, wsi, objs, new TypedObjectValidationException(String.format(
				"Object #1, %s has invalid provenance reference: There is no object with id " +
				"wsIdErrorOrder/auto2: No object with name auto2 exists in workspace 1 " +
				"(name wsIdErrorOrder)", getLastRandomName())));
		
	}
	
	@Test
	public void genericIdExtraction() throws Exception {
		
		String idtype1 = "someid";
		String idtype2 = "someid2";
//		String idtypeint = "someintid";
		String mod = "TestIDExtraction";
		String type = "IdType";
		final String idSpec =
				"module " + mod + " {\n" +
					"/* @id " + idtype1 + " */\n" +
					"typedef string some_id;\n" +
					"/* @id " + idtype2 + " */\n" +
					"typedef string some_id2;\n" +
//					"/* @id " + idtypeint + " */" +
//					"typedef int int_id;" +
					
					"/* @optional an_id\n" +
					"   @optional an_id2\n" +
//					"   @optional an_int_id */" +
					"*/" +
					"typedef structure {\n" +
						"some_id an_id;\n" +
						"some_id2 an_id2;\n" +
//						"int_id an_int_id;" +
					"} " + type + ";\n" +
				"};\n";
		WorkspaceUser user = new WorkspaceUser("foo");
		types.requestModuleRegistration(user, mod);
		types.resolveModuleRegistration(mod, true);
		types.compileNewTypeSpec(user, idSpec, Arrays.asList(type),
				null, null, false, null);
		TypeDefId idtype = new TypeDefId(new TypeDefName(mod, type), 0, 1);
		
		// test basic type checking with different versions
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("idextract");
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		Provenance emptyprov = new Provenance(user);
		List<WorkspaceSaveObject> data = new LinkedList<WorkspaceSaveObject>();
		data.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto1"),
				new HashMap<String, Object>(), idtype, null, emptyprov, false));
		
		Map<String, Object> iddata = new HashMap<String, Object>();
		

		IdReferenceHandlerSetFactory fac = getIdFactory().addFactory(
				new TestIDReferenceHandlerFactory(new IdReferenceType(idtype1)));

		data.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto2"),
				iddata, idtype, null, emptyprov, false));
		iddata.put("an_id", "id here");
		iddata.put("an_id2", "foo");
//		iddata.put("an_int_id", 34);
		ws.saveObjects(user, wsi, data, fac); //should work
		Map<String, List<String>> expected = new HashMap<String, List<String>>();
		ObjectIdentifier obj1 = new ObjectIdentifier(wsi, "auto1");
		checkExternalIds(user, obj1, expected);
		
		expected.put(idtype1, Arrays.asList("id here"));
		ObjectIdentifier obj2 = new ObjectIdentifier(wsi, "auto2");
		checkExternalIds(user, obj2, expected);
		
		fac.addFactory(new TestIDReferenceHandlerFactory(new IdReferenceType(idtype2)));
		data.set(0, renameWSO(data.get(0), "auto3"));
		data.set(1, renameWSO(data.get(1), "auto4"));
		ws.saveObjects(user, wsi, data, fac); //should work
		expected.put(idtype2, Arrays.asList("foo"));
		ObjectIdentifier obj4 = new ObjectIdentifier(wsi, "auto4");
		checkExternalIds(user, obj4, expected);
		
		ObjectIdentifier copied = new ObjectIdentifier(wsi, "copied");
		ws.copyObject(user, obj4, copied);
		checkExternalIds(user, copied, expected);
		
		WorkspaceIdentifier clone = new WorkspaceIdentifier("idextract_cloned");
		ws.cloneWorkspace(user, wsi, clone.getName(), false, null, null, null);
		ObjectIdentifier clonedobj = new ObjectIdentifier(clone, "copied");
		checkExternalIds(user, clonedobj, expected);
		
		ws.saveObjects(user, wsi, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("copied"),
						new HashMap<String, Object>(), idtype, null, emptyprov, false)),
						fac);
		ws.revertObject(user, new ObjectIdentifier(wsi, "copied", 1));
		checkExternalIds(user, new ObjectIdentifier(wsi, "copied", 3), expected);
		
		expected.clear();
		ws.revertObject(user, new ObjectIdentifier(wsi, "copied", 2));
		checkExternalIds(user, new ObjectIdentifier(wsi, "copied", 4), expected);
		
//		//check int ids
//		fac.addFactory(new TestIDReferenceHandlerFactory(new IdReferenceType(idtypeint)));
//		
//		ws.saveObjects(user, wsi, data, fac); //should work
//		expected.put(idtype1, Arrays.asList("id here"));
//		expected.put(idtype2, Arrays.asList("foo"));
//		expected.put(idtypeint, Arrays.asList("34"));
//		checkExternalIds(user, new ObjectIdentifier(wsi, "auto7"), expected);
//		
//		iddata.put("an_int_id", null);
//		
//		failSave(user, wsi, data, fac, new TypedObjectValidationException(
//				"Object #2 failed type checking:\ninstance type (null) not allowed for ID reference (allowed: [\"integer\"]), at /an_int_id"));
		
		iddata.put("an_id", "parseExcept");
		failSave(user, wsi, data, fac, new TypedObjectValidationException(
				"Object #2, auto4 failed type checking:\nUnparseable id parseExcept of type " +
				"someid: Parse exception for ID parseExcept at /an_id"));
		
		iddata.clear();
		iddata.put("an_id2", "refExcept");
		failSave(user, wsi, data, fac, new TypedObjectValidationException(
				"Object #2, auto4 failed type checking:\nInvalid id refExcept of type someid2: " +
				"Reference exception for ID refExcept at /an_id2"));
		
		iddata.clear();
		iddata.put("an_id", "genExcept");
		failSave(user, wsi, data, fac, new TypedObjectValidationException(
				"Object #2, auto4 failed type checking:\nId handling error for id type someid: " +
				"General exception for ID genExcept at /an_id"));
		
		iddata.put("an_id", "procParseExcept");
		failSave(user, wsi, data, fac, new TypedObjectValidationException(
				"Object #2, auto4 has unparseable reference procParseExcept: Process Parse " +
				"exception for ID procParseExcept at /an_id"));
		
		iddata.clear();
		iddata.put("an_id2", "procRefExcept");
		failSave(user, wsi, data, fac, new TypedObjectValidationException(
				"Object #2, auto4 has invalid reference: Process Reference exception for ID " +
				"procRefExcept at /an_id2"));
		
		iddata.clear();
		iddata.put("an_id", "procGenExcept");
		failSave(user, wsi, data, fac, new TypedObjectValidationException(
				"An error occured while processing IDs: Process General exception for ID procGenExcept"));
	}
	
	
	private WorkspaceSaveObject renameWSO(final WorkspaceSaveObject obj, final String name) {
		return new WorkspaceSaveObject(new ObjectIDNoWSNoVer(name), obj.getData(), obj.getType(),
				obj.getUserMeta(), obj.getProvenance(), obj.isHidden());
	}

	@Test
	public void wsIDHandling() throws Exception {
		String mod = "WsIDHandling";
		String type = "IdType";
		final String idSpec =
				"module " + mod + " {\n" +
		
					"/* @optional foo */\n" +
					"typedef structure {\n" +
						"int foo;\n" +
					"} Type1;\n" +
		
					"/* @optional foo */\n" +
					"typedef structure {\n" +
						"int foo;\n" +
					"} Type2;\n" +
					
					"/* @optional foo */\n" +
					"typedef structure {\n" +
						"int foo;\n" +
					"} Type3;\n" +
					
					"/* @id ws */\n" +
					"typedef string ws_any;\n" +
					
					"/* @id ws " + mod + ".Type1 */\n" +
					"typedef string ws_1;\n" +
					
					"/* @id ws " + mod + ".Type2 */\n" +
					"typedef string ws_2;\n" +
					
					"/* @id ws " + mod + ".Type3 */\n" +
					"typedef string ws_3;\n" +
					
					"/* @id ws " + mod + ".Type1 " + mod + ".Type2 */\n" +
					"typedef string ws_12;\n" +
					
					"/* @id ws " + mod + ".Type1 " + mod + ".Type3 */\n" +
					"typedef string ws_13;\n" +
					
					"/* @id ws " + mod + ".Type2 " + mod + ".Type3 */\n" +
					"typedef string ws_23;\n" +

					"/* @optional ws_any ws_1 ws_2 ws_3 ws_12 ws_13 ws_23 */\n" +
					"typedef structure {\n" +
						"list<ws_any> ws_any;\n" +
						"list<mapping<ws_1, int>> ws_1;\n" +
						"list<tuple<string, ws_2>> ws_2;\n" +
						"list<list<ws_3>> ws_3;\n" +
						"list<ws_12> ws_12;\n" +
						"list<ws_13> ws_13;\n" +
						"list<ws_23> ws_23;\n" +
					"} " + type + ";\n" +
				"};\n";
		WorkspaceUser user = new WorkspaceUser("foo");
		types.requestModuleRegistration(user, mod);
		types.resolveModuleRegistration(mod, true);
		types.compileNewTypeSpec(user, idSpec, Arrays.asList(type, "Type1", "Type2", "Type3"),
				null, null, false, null);
		TypeDefId type1 = new TypeDefId(new TypeDefName(mod, "Type1"), 0, 1);
		TypeDefId type2 = new TypeDefId(new TypeDefName(mod, "Type2"), 0, 1);
		TypeDefId type3 = new TypeDefId(new TypeDefName(mod, "Type3"), 0, 1);
		
		TypeDefId idtype = new TypeDefId(new TypeDefName(mod, type), 0, 1);
		
		// test basic type checking with different versions
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("wsIDHandling");
		ws.createWorkspace(user, wsi.getName(), false, null, null).getId();
		Provenance emptyprov = new Provenance(user);
		List<WorkspaceSaveObject> objs = new LinkedList<WorkspaceSaveObject>();
		IdReferenceHandlerSetFactory fac = new IdReferenceHandlerSetFactory(3);
		
		Map<String, Object> mt = new HashMap<String, Object>();
		objs.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("t1"), mt, type1, null, emptyprov, false));
		objs.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("t2"), mt, type2, null, emptyprov, false));
		objs.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("t3"), mt, type3, null, emptyprov, false));
		ws.saveObjects(user, wsi, objs, fac);
		
		String ref1 = wsi.getName() + "/t1";
		String ref2 = wsi.getName() + "/t2";
		String ref3 = wsi.getName() + "/t3";
		
		List<String> all3 = Arrays.asList(ref1, ref2, ref3);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("ws_any", all3);
		Map<String, Integer> innermap = new HashMap<String, Integer>();
		data.put("ws_1", Arrays.asList(innermap));
		innermap.put(ref1, 3);
		ArrayList<List<String>> innertuple = new ArrayList<List<String>>();
		data.put("ws_2", innertuple);
		innertuple.add(Arrays.asList("foo", ref2));
		ArrayList<String> innerlist = new ArrayList<String>();
		data.put("ws_3", Arrays.asList(innerlist));
		innerlist.add(ref3);
		data.put("ws_12", Arrays.asList(ref1, ref2));
		data.put("ws_13", Arrays.asList(ref1, ref3));
		data.put("ws_23", Arrays.asList(ref2, ref3));
		
		objs.clear();
		objs.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("foo"), data, idtype, null,
				emptyprov, false));
		//should work
		ws.saveObjects(user, wsi, objs, fac);
		
		innermap.put(ref2, 4);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1, foo has invalid reference: The type WsIDHandling.Type2-0.1 of " +
				"reference wsIDHandling/t2 in this object is not allowed - allowed types are " +
				"[WsIDHandling.Type1] at /ws_1/0/wsIDHandling/t2"));
		
		innermap.remove(ref2);
		innermap.put(ref3, 6);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1, foo has invalid reference: The type WsIDHandling.Type3-0.1 of " +
				"reference wsIDHandling/t3 in this object is not allowed - allowed types are " +
				"[WsIDHandling.Type1] at /ws_1/0/wsIDHandling/t3"));
		
		innermap.remove(ref3);
		innertuple.add(Arrays.asList("bar", ref1));
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1, foo has invalid reference: The type WsIDHandling.Type1-0.1 of " +
				"reference wsIDHandling/t1 in this object is not allowed - allowed types are " +
				"[WsIDHandling.Type2] at /ws_2/1/1"));
		
		innertuple.clear();
		innertuple.add(Arrays.asList("baz", ref3));
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1, foo has invalid reference: The type WsIDHandling.Type3-0.1 of " +
				"reference wsIDHandling/t3 in this object is not allowed - allowed types are " +
				"[WsIDHandling.Type2] at /ws_2/0/1"));
		
		innertuple.set(0, Arrays.asList("foo", ref2));
		innerlist.add(ref1);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1, foo has invalid reference: The type WsIDHandling.Type1-0.1 of " +
				"reference wsIDHandling/t1 in this object is not allowed - allowed types are " +
				"[WsIDHandling.Type3] at /ws_3/0/1"));
		
		innerlist.set(1, ref3);
		innerlist.add(ref2);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1, foo has invalid reference: The type WsIDHandling.Type2-0.1 of " +
				"reference wsIDHandling/t2 in this object is not allowed - allowed types are " +
				"[WsIDHandling.Type3] at /ws_3/0/2"));
		
		innerlist.remove(2);
		innerlist.remove(1);
		data.put("ws_12", all3);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1, foo has invalid reference: The type WsIDHandling.Type3-0.1 of " +
				"reference wsIDHandling/t3 in this object is not allowed - allowed types are " +
				"[WsIDHandling.Type1, WsIDHandling.Type2] at /ws_12/2"));
		
		data.put("ws_12", Arrays.asList(ref1, ref2));
		data.put("ws_13", all3);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1, foo has invalid reference: The type WsIDHandling.Type2-0.1 of " +
				"reference wsIDHandling/t2 in this object is not allowed - allowed types are " +
				"[WsIDHandling.Type1, WsIDHandling.Type3] at /ws_13/1"));
		
		data.put("ws_13", Arrays.asList(ref1, ref3));
		data.put("ws_23", all3);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1, foo has invalid reference: The type WsIDHandling.Type1-0.1 of " +
				"reference wsIDHandling/t1 in this object is not allowed - allowed types are " +
				"[WsIDHandling.Type2, WsIDHandling.Type3] at /ws_23/0"));
		
		//test id path returns on parse and inaccessible object exceptions
		data.put("ws_23", Arrays.asList(ref2, ref3));
		innertuple.set(0, Arrays.asList("foo", "YourMotherWasAHamster"));
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1, foo has unparseable reference YourMotherWasAHamster: Illegal number " +
				"of separators / in object reference YourMotherWasAHamster at /ws_2/0/1"));
		
		innertuple.set(0, Arrays.asList("foo", ref2));
		data.remove("ws_any");
		ws.setObjectsDeleted(user, Arrays.asList(new ObjectIdentifier(wsi, "t1")), true);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1, foo has invalid reference: There is no object with id " +
				"wsIDHandling/t1: Object 1 (name t1) in workspace 1 (name wsIDHandling) has " +
				"been deleted at /ws_12/0"));
	}
	
	@Test
	public void maxIdsPerCall() throws Exception {
		String idtype1 = "someid";
		String idtype2 = "someid2";
		String mod = "TestMaxId";
		String listtype = "ListIdType";
		final String idSpec =
				"module " + mod + " {\n" +
					"/* @id ws */\n" +
					"typedef string ws_id;\n" +
					"/* @id " + idtype1 + " */\n" +
					"typedef string some_id;\n" +
					"/* @id " + idtype2 + " */\n" +
					"typedef string some_id2;\n" +
					"/* @id " + idtype1 + " attrib1 */\n" +
					"typedef string some_id_a1;\n" +
					"/* @id " + idtype1 + " attrib2 */\n" +
					"typedef string some_id_a2;\n" +
					"/* @optional ws_ids\n" + 
					"   @optional some_ids\n" +
					"   @optional some_ids2\n" +
					"   @optional some_ids_a1\n" +
					"   @optional some_ids_a2\n" +
					"*/\n" + 
					"typedef structure {\n" +
						"list<ws_id> ws_ids;\n" +
						"list<some_id> some_ids;\n" +
						"list<some_id2> some_ids2;\n" +
						"list<some_id_a1> some_ids_a1;\n" +
						"list<some_id_a2> some_ids_a2;\n" +
					"} " + listtype + ";\n" +
				"};\n";
		WorkspaceUser user = new WorkspaceUser("foo");
		types.requestModuleRegistration(user, mod);
		types.resolveModuleRegistration(mod, true);
		types.compileNewTypeSpec(user, idSpec, Arrays.asList(listtype),
				null, null, false, null);
		TypeDefId listidtype = new TypeDefId(new TypeDefName(mod, listtype), 0, 1);
		
		// test basic type checking with different versions
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("maxids");
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		Provenance emptyprov = new Provenance(user);
		
		IdReferenceHandlerSetFactory fac = makeFacForMaxIDTests(
				Arrays.asList(idtype1, idtype2), user, 8);
		ws.saveObjects(user, wsi, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto1"),
						MT_MAP, listidtype, null, emptyprov, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto2"),
						MT_MAP, listidtype, null, emptyprov, false))
				, fac);
		
		Map<String, Object> data1 = new HashMap<String, Object>();
		data1.put("ws_ids", Arrays.asList("maxids/auto1", "maxids/auto2", "maxids/auto1"));
		data1.put("some_ids", Arrays.asList("foo", "bar", "foo"));
		data1.put("some_ids2", Arrays.asList("foo", "baz", "foo"));
		data1.put("some_ids_a1", Arrays.asList("foo", "bak", "foo"));
		data1.put("some_ids_a2", Arrays.asList("foo", "baf", "foo"));
		
		//should work
		final List<WorkspaceSaveObject> objs1 = Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data1, listidtype, null, emptyprov, false));
		ws.saveObjects(user, wsi, objs1, fac);
		
		fac = makeFacForMaxIDTests(Arrays.asList(idtype1, idtype2), user, 7);
		failSave(user, wsi, objs1, fac, new TypedObjectValidationException(
				"Failed type checking at object #1 - the number of unique IDs in the saved objects exceeds the maximum allowed, 7"));
		
		Provenance p = new Provenance(user).addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList(
						"maxids/auto1", "maxids/auto2", "maxids/auto1")));
		
		fac = makeFacForMaxIDTests(Arrays.asList(idtype1, idtype2), user, 10);
		objs1.set(0, new WorkspaceSaveObject(getRandomName(), data1, listidtype, null, p, false));
		//should work
		ws.saveObjects(user, wsi, objs1, fac);
		fac = makeFacForMaxIDTests(Arrays.asList(idtype1, idtype2), user, 9);
		failSave(user, wsi, objs1, fac, new TypedObjectValidationException(
				"Failed type checking at object #1 - the number of unique IDs in the saved objects exceeds the maximum allowed, 9"));
		
		final List<WorkspaceSaveObject> objs2 = Arrays.asList(
				new WorkspaceSaveObject(getRandomName(), data1, listidtype, null, emptyprov,
						false),
				new WorkspaceSaveObject(getRandomName(), data1, listidtype, null, emptyprov,
						false));
		
		fac = makeFacForMaxIDTests(Arrays.asList(idtype1, idtype2), user, 16);
		
		//should work
		ws.saveObjects(user, wsi, objs2, fac);
		
		fac = makeFacForMaxIDTests(Arrays.asList(idtype1, idtype2), user, 15);
		failSave(user, wsi, objs2, fac, new TypedObjectValidationException(
				"Failed type checking at object #2 - the number of unique IDs in the saved objects exceeds the maximum allowed, 15"));
		
		final List<WorkspaceSaveObject> objs3 = Arrays.asList(
				new WorkspaceSaveObject(getRandomName(), data1, listidtype, null, p,
						false),
				new WorkspaceSaveObject(getRandomName(), data1, listidtype, null, p,
						false));
		fac = makeFacForMaxIDTests(Arrays.asList(idtype1, idtype2), user, 20);
		
		//should work
		ws.saveObjects(user, wsi, objs3, fac);
		
		fac = makeFacForMaxIDTests(Arrays.asList(idtype1, idtype2), user, 19);
		failSave(user, wsi, objs3, fac, new TypedObjectValidationException(
				"Failed type checking at object #2 - the number of unique IDs in the saved objects exceeds the maximum allowed, 19"));
	}

	private IdReferenceHandlerSetFactory makeFacForMaxIDTests(List<String> idtypes,
			WorkspaceUser user, int max) {
		IdReferenceHandlerSetFactory fac = new IdReferenceHandlerSetFactory(max);
//				.addFactory(ws.getHandlerFactory(user));
		for (String idtype: idtypes) {
			fac.addFactory(new TestIDReferenceHandlerFactory(
					new IdReferenceType(idtype)));
		}
		return fac;
	}
	
	@Test
	public void referenceClash() throws Exception {
		String mod = "TestTypeCheckingErr";
		final String specTypeCheck1 =
				"module " + mod + " {" +
					"typedef structure {" +
						"int foo;" +
						"list<int> bar;" +
						"string baz;" +
					"} CheckType;" +
				"};";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		Provenance emptyprov = new Provenance(userfoo);
		types.requestModuleRegistration(userfoo, mod);
		types.resolveModuleRegistration(mod, true);
		types.compileNewTypeSpec(userfoo, specTypeCheck1, Arrays.asList("CheckType"), null, null, false, null);
		types.releaseTypes(userfoo, mod);
		TypeDefId abstype0 = new TypeDefId(new TypeDefName(mod, "CheckType"), 1, 0);
		String wsName = "reftypecheckerror";
		ws.createWorkspace(userfoo, wsName, false, null, null);
		WorkspaceIdentifier reftypecheck = new WorkspaceIdentifier(wsName);
		Map<String, Object> refdata = new HashMap<String, Object>();
		refdata.put("foo", 3);
		refdata.put("baz", "astring");
		refdata.put("bar", Arrays.asList(-3, 1, 234567890));
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto1"), refdata, abstype0 , null, emptyprov, false)),
				getIdFactory());
		String refmod = "TestTypeCheckingRefTypeErr";
		final String specTypeCheckRefs =
				"module " + refmod + " {" +
					"/* @id ws " + mod + ".CheckType */" +
					"typedef string reference;" +
					"/* @optional refmap */" +
					"typedef structure {" +
						"int foo;" +
						"list<int> bar;" +
						"string baz;" +
						"reference ref;" +
						"mapping<reference, string> refmap;" + 
					"} CheckRefType;" +
				"};";
		types.requestModuleRegistration(userfoo, refmod);
		types.resolveModuleRegistration(refmod, true);
		types.compileNewTypeSpec(userfoo, specTypeCheckRefs, Arrays.asList("CheckRefType"), null, null, false, null);
		types.releaseTypes(userfoo, refmod);
		TypeDefId absreftype0 = new TypeDefId(new TypeDefName(refmod, "CheckRefType"), 1, 0);
		long reftypewsid = ws.getWorkspaceInformation(userfoo, reftypecheck).getId();
		//test the edge case where two keys in a hash resolve to the same reference
		refdata.put("ref", wsName + "/1/1");
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put(wsName + "/1/1", "pootypoot");
		refmap.put(wsName + "/auto1/1", "pootypoot");
		assertThat("refmap has 2 refs", refmap.size(), is(2));
		refdata.put("refmap", refmap);
		failSave(userfoo, reftypecheck, getRandomName(), refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(
						"Object #1, %s: Two references in a single hash are identical when " +
						"resolved, resulting in a loss of data: Duplicated key '%s/1/1' was " +
						"found at /refmap", getLastRandomName(), reftypewsid)));
	}
	
	@Test
	public void saveProvenance() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceIdentifier prov = new WorkspaceIdentifier("provenance");
		ws.createWorkspace(foo, prov.getName(), false, null, null);
		long wsid = ws.getWorkspaceInformation(foo, prov).getId();
		WorkspaceIdentifier provid = new WorkspaceIdentifier(wsid);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("foo", "bar");
		Provenance emptyprov = new Provenance(foo);
		
		//already tested bad references in saveObjectWithTypeChecking, won't test again here
		
		ws.saveObjects(foo, prov, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto1"), data, SAFE_TYPE1, null, emptyprov, false)),
				getIdFactory());
		ws.saveObjects(foo, prov, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto2"), data, SAFE_TYPE1, null, emptyprov, false)),
				getIdFactory());
		ws.saveObjects(foo, prov, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto3"), data, SAFE_TYPE1, null, emptyprov, false)),
				getIdFactory());
		
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto1"), data, SAFE_TYPE1, null, emptyprov, false)),
				getIdFactory());
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto1"), data, SAFE_TYPE1, null, emptyprov, false)),
				getIdFactory());
		
		List<ExternalData> ed = new LinkedList<ExternalData>();
		ed.add(new ExternalData()
				.withDataId("data id")
				.withDataUrl("http://somedata.org/somedata")
				.withDescription("a description")
				.withResourceName("resource")
				.withResourceReleaseDate(new Date(62))
				.withResourceUrl("http://somedata.org")
				.withResourceVersion("1.2.3")
				);
		ed.add(new ExternalData().withDataId("data id2"));
		
		Map<String, String> custom = new HashMap<String, String>();
		custom.put("foo", "bar");
		custom.put("baz", "whee");
		
		List<SubAction> sa = new ArrayList<SubAction>();
		sa.add(new SubAction()
				.withCodeUrl("http://github.com/animeweirdo/tentaclegen")
				.withCommit("aaaaaaaaaaaaaaaaaaaaaaaa")
				.withEndpointUrl("http://tentacool.com/tentaclegen")
				.withName("Tentacle Generator")
				.withVer("102.1.0")
				);
		
		Provenance p = new Provenance(foo);
		p.addAction(new ProvenanceAction()
				.withCaller("A caller")
				.withCommandLine("A command line")
				.withDescription("descrip")
				.withIncomingArgs(Arrays.asList("a", "b", "c"))
				.withMethod("method")
				.withMethodParameters(Arrays.asList((Object) data, data, data))
				.withOutgoingArgs(Arrays.asList("d", "e", "f"))
				.withScript("script")
				.withScriptVersion("2.1")
				.withServiceName("service")
				.withServiceVersion("3")
				.withTime(new Date(45))
				.withExternalData(ed)
				.withCustom(custom)
				.withSubActions(sa)
				.withWorkspaceObjects(Arrays.asList("provenance/auto3", "provenance/auto1/2")));
		p.addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList("provenance/auto2/1", "provenance/auto1")));
		
		ws.saveObjects(foo, prov, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data, SAFE_TYPE1, null, p, false)),
				getIdFactory());
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("provenance/auto3", wsid + "/3/1");
		refmap.put("provenance/auto1/2", wsid + "/1/2");
		refmap.put("provenance/auto2/1", wsid + "/2/1");
		refmap.put("provenance/auto1", wsid + "/1/3");
		
		checkProvenanceCorrect(foo, p, new ObjectIdentifier(provid, 4), refmap);
		
		try {
			new WorkspaceSaveObject(getRandomName(), data, SAFE_TYPE1, null, null, false);
			fail("saved without provenance");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Neither id, provenance, data, nor type may be null"));
		}
		try {
			new Provenance(null);
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("user cannot be null"));
		}
		try {
			Provenance pv = new Provenance(foo);
			pv.addAction(null);
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("action cannot be null"));
		}
		
		//Test minimal provenance
		Provenance p2 = new Provenance(foo);
		ws.saveObjects(foo, prov, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data, SAFE_TYPE1, null, p2, false)),
				getIdFactory());
		List<Date> dates = checkProvenanceCorrect(foo, p2, new ObjectIdentifier(provid, 5),
				new HashMap<String, String>());
		final List<WorkspaceObjectData> objects = ws.getObjects(
				foo, Arrays.asList(new ObjectIdentifier(prov, 5)));
		destroyGetObjectsResources(objects); // don't need the data
		Provenance got2 = objects.get(0).getProvenance();
		assertThat("Prov date constant", got2.getDate(), is(dates.get(0)));
		Provenance gotProv2 = ws.getObjects(foo, Arrays.asList(
				new ObjectIdentifier(prov, 5)), true).get(0).getProvenance();
		assertThat("Prov date constant", gotProv2.getDate(), is(dates.get(1)));
		assertThat("Prov dates same", got2.getDate(), is(gotProv2.getDate()));
		//make sure passing nulls for ws obj lists doesn't kill anything
		Provenance p3 = new Provenance(foo);
		p3.addAction(new ProvenanceAction().withWorkspaceObjects(null));
		ws.saveObjects(foo, prov, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data, SAFE_TYPE1, null, p3, false)),
				getIdFactory());
		checkProvenanceCorrect(foo, p3, new ObjectIdentifier(provid, 6),
				new HashMap<String, String>());
		
		Provenance p4 = new Provenance(foo);
		ProvenanceAction pa = new ProvenanceAction();
		pa.setWorkspaceObjects(null);
		p4.addAction(pa);
		p3.addAction(new ProvenanceAction().withWorkspaceObjects(null));
		ws.saveObjects(foo, prov, Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), data, SAFE_TYPE1, null, p4, false)),
				getIdFactory());
		checkProvenanceCorrect(foo, p4, new ObjectIdentifier(provid, 7),
				new HashMap<String, String>());
	}
	
	@Test 
	public void saveLargeProvenance() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceIdentifier prov = new WorkspaceIdentifier("bigprov");
		ws.createWorkspace(foo, prov.getName(), false, null, null);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("foo", "bar");
		List<Object> methparams = new ArrayList<Object>();
		for (int i = 1; i < 997; i++) {
			methparams.add(TEXT1000);
		}
		Provenance p = new Provenance(foo);
		p.addAction(new ProvenanceAction().withMethodParameters(methparams));
		ws.saveObjects(foo, prov, Arrays.asList( //should work
				new WorkspaceSaveObject(getRandomName(), data, SAFE_TYPE1, null, p, false)),
				getIdFactory());
		
		
		methparams.add(TEXT1000);
		Provenance p2 = new Provenance(foo);
		p2.addAction(new ProvenanceAction().withMethodParameters(methparams));
		try {
			ws.saveObjects(foo, prov, Arrays.asList(
					new WorkspaceSaveObject(getRandomName(), data, SAFE_TYPE1, null, p, false)),
					getIdFactory());
			fail("saved too big prov");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is(String.format("Object #1, %s provenance size 1000348 exceeds limit of " +
							"1000000", getLastRandomName())));
		}
	}
	
	@Test
	public void saveWithWrongObjectId() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceIdentifier read = new WorkspaceIdentifier("wrongobjid");
		ws.createWorkspace(foo, read.getIdentifierString(), false, null, null);
		Map<String, Object> data = new HashMap<String, Object>();
		JsonNode savedata = MAPPER.valueToTree(data);
		try {
			ws.saveObjects(foo, read, Arrays.asList(new WorkspaceSaveObject(
					new ObjectIDNoWSNoVer(3), savedata, SAFE_TYPE1, null,
					new Provenance(foo), false)),
					getIdFactory());
			fail("saved object with non-existant id");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception", nsoe.getLocalizedMessage(),
					is("There is no object with id 3"));
		}
	}

	@Test
	public void saveWithReferencePaths() throws Exception {
		final String modname = "TestSaveRefPaths";
		final String type1name = "Type1";
		final String type2name = "Type2";
		final String type1RefName = "Type1Ref";
		final String type2RefName = "Type2Ref";
		final String type12RefName = "Type12Ref";
		
		final String spec =
				"module " + modname + " {" +
					"/* @id ws */" +
					"typedef string ref;" +
					"/* @optional refs */ " + 
					"typedef structure {" +
						"list<ref> refs;" +
					"} " + type1name + ";" +
					
					"/* @optional refs */ " + 
					"typedef structure {" +
						"list<ref> refs;" +
					"} " + type2name + ";" +
					
					"/* @id ws " + modname + "." + type1name + " */" +
					"typedef string ref1;" +
					"/* @optional refs */ " + 
					"typedef structure {" +
						"list<ref1> refs;" +
					"} " + type1RefName + ";" +
						
					"/* @id ws " + modname + "." + type2name + " */" +
					"typedef string ref2;" +
					"/* @optional refs */ " + 
					"typedef structure {" +
						"list<ref2> refs;" +
					"} " + type2RefName + ";" +
						
					"/* @id ws " + modname + "." + type1name + " " +
						modname + "." + type2name + " */" +
					"typedef string ref12;" +
					"/* @optional refs */ " + 
					"typedef structure {" +
						"list<ref12> refs;" +
					"} " + type12RefName + ";" +
				"};";
		
		final WorkspaceUser admin = new WorkspaceUser("admin");
		types.requestModuleRegistration(admin, modname);
		types.resolveModuleRegistration(modname, true);
		types.compileNewTypeSpec(admin, spec, Arrays.asList(type1name, type2name, type1RefName,
				type2RefName, type12RefName),
				null, null, false, null);
		types.releaseTypes(admin, modname);
		final TypeDefId type1 = new TypeDefId(new TypeDefName(modname, type1name), 1, 0);
		final TypeDefId type2 = new TypeDefId(new TypeDefName(modname, type2name), 1, 0);
		final TypeDefId type1Ref = new TypeDefId(new TypeDefName(modname, type1RefName), 1, 0);
		final TypeDefId type2Ref = new TypeDefId(new TypeDefName(modname, type2RefName), 1, 0);
		final TypeDefId type12Ref = new TypeDefId(new TypeDefName(modname, type12RefName), 1, 0);
		
		final WorkspaceUser u1 = new WorkspaceUser("user1");
		final WorkspaceUser u2 = new WorkspaceUser("user2");
		
		final WorkspaceIdentifier readws = new WorkspaceIdentifier("readws");
		final WorkspaceIdentifier privws = new WorkspaceIdentifier("privws");
		final WorkspaceIdentifier testws = new WorkspaceIdentifier("testws");
		ws.createWorkspace(u1, readws.getName(), false, null, null);
		ws.setPermissions(u1, readws, Arrays.asList(u2), Permission.WRITE);
		ws.createWorkspace(u2, privws.getName(), false, null, null);
		ws.createWorkspace(u1, testws.getName(), false, null, null);
		
		final Provenance p2 = new Provenance(u2);
		final String leaf1Name = "leaf1";
		// 2/1/1
		saveObject(u2, privws, makeMeta(1), MT_MAP, type1, leaf1Name, p2);
		final String leaf1_1ref = privws.getName() + "/" + leaf1Name + "/" + 1;
		
		// 2/1/2
		saveObject(u2, privws, makeMeta(2), MT_MAP, type2, leaf1Name, p2);
		final String leaf1_2ref = privws.getName() + "/" + leaf1Name + "/" + 2;
		
		// 2/2/1
		final String delLeafName = "delleaf";
		saveObject(u2, privws, makeMeta(3), MT_MAP, type1, delLeafName, p2);
		final String delLeafRef = privws.getName() + "/" + delLeafName + "/" + 1;

		// 1/1/1
		final String readleafName = "readable";
		saveObject(u2, readws, makeMeta(4), MT_MAP, type1, readleafName, p2);
		final String readleafRef = readws.getName() + "/" + readleafName + "/" + 1;
		
		/* LEVEL 1 REFS */
		
		// 2/3/1 this ref points to leaf 1-1 and del leaf
		final String ref1Name = "ref1";
		saveObject(u2, privws, MT_MAP, makeRefData(leaf1_1ref, delLeafRef), type2, ref1Name, p2);
		ws.setObjectsDeleted(u2, Arrays.asList(new ObjectIdentifier(privws, delLeafName)), true);
		final String ref1ref = privws.getName() + "/" + ref1Name + "/" + 1;
		
		// 2/4/1
		final String ref2Name = "ref2"; // 1 hop
		saveObject(u2, privws, MT_MAP, makeRefData(leaf1_2ref), type2, ref2Name, p2);
		final String ref2ref = privws.getName() + "/" + ref2Name + "/" + 1;
		
		// 1/2/1
		final String readableRefName = "readableRef";
		saveObject(u2, readws, MT_MAP, makeRefData(readleafRef), type1, readableRefName, p2);
		
		/* LEVEL 2 REFS */
		
		// 2/5/1
		final String refref1Name = "refref1"; // 2 hops
		final Provenance p2withRef = new Provenance(u2).addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList(ref1ref)));
		saveObject(u2, privws, MT_MAP, MT_MAP, SAFE_TYPE1, refref1Name, p2withRef);
		final String refref1ref = privws.getName() + "/" + refref1Name + "/" + 1;
		
		// 1/3/1
		final String refref2Name = "refref2"; // 2 hops
		saveObject(u2, readws, MT_MAP, makeRefData(ref2ref), type1, refref2Name, p2);
		
		/* LEVEL 3 REFS */
		
		// 1/4/1
		final String refrefref1Name = "refrefref1"; // 3 hops
		saveObject(u2, readws, MT_MAP, makeRefData(refref1ref), type2, refrefref1Name, p2);

		// test saving an object with reference paths
		// tests saving an object with a ref path to a deleted object
		// tests mixing reference paths with standard references
		final LinkedHashMap<String, String> refPaths1 = new LinkedHashMap<>();
		refPaths1.put("  \nreadws/4 ; 2/refref1/1;2/3 ;  	2/leaf1/1;  ", "2/1/1");
		refPaths1.put("readws/readable", "1/1/1");
		
		final LinkedHashMap<String, String> refPaths2 = new LinkedHashMap<>();
		refPaths2.put("1/3/1 ; \n 2/ref2/1 ; 	privws/1", "2/1/2");
		refPaths2.put("  	readws/4 ; 2/refref1/1;2/3 ;  	privws/delleaf; \n ", "2/2/1");
		refPaths2.put("1/readableRef;readws/1", "1/1/1");
		
		successSaveWithRefPaths(u1, testws, type1, Arrays.asList(refPaths1, refPaths2));
		
		// test saving an object with a ref path to an object in a deleted workspace
		refPaths2.put("readws/1;   ", "1/1/1");
		ws.setWorkspaceDeleted(u2, privws, true);
		successSaveWithRefPaths(u1, testws, type1, Arrays.asList(refPaths1, refPaths2));
		ws.setWorkspaceDeleted(u2, privws, false);
		
		// test saving an object with refs that specify types, but cover all types
		successSaveWithRefPaths(u1, testws, type12Ref, Arrays.asList(refPaths1, refPaths2));
		
		// test saving object with provenance references
		final WorkspaceIdentifier testwsid = new WorkspaceIdentifier(3);
		Provenance prefs = new Provenance(u1).addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList(
						"  \nreadws/4 ; 2/refref1/1;2/3 ;  	2/leaf1/1;  ",
						"readws/readable")));
		saveObject(u1, testws, MT_MAP, MT_MAP, SAFE_TYPE1, "provtest1", prefs);
		checkProvenanceCorrect(u1, prefs, new ObjectIdentifier(testwsid, "provtest1"), refPaths1);

		prefs = new Provenance(u1).addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList(
						"1/3/1 ; \n 2/ref2/1 ; 	privws/1",
						"  	readws/4 ; 2/refref1/1;2/3 ;  	privws/delleaf; \n ",
						"1/readableRef;readws/1")));
		saveObject(u1, testws, MT_MAP, MT_MAP, SAFE_TYPE1, "provtest2", prefs);
		checkProvenanceCorrect(u1, prefs, new ObjectIdentifier(testwsid, "provtest2"), refPaths2);
		
		// test fail save on bad reference
		// also tests failing on objects with id attributes
		final Provenance p1 = new Provenance(u1);
		final ObjectIDNoWSNoVer fail = new ObjectIDNoWSNoVer("fail");
		failSave(u1, testws, fail,
				makeRefData("  \nreadws/4 ; 2/refref1/1;2/3 ;  	2/leaf1/1;  ", "readws/readable",
				"1/3/1 ; \n 2/ref1/1 ; 	privws/1"), type1Ref, p1,
				new TypedObjectValidationException(
						"Object #1, fail has invalid reference: Reference path starting with " +
						"1/3/1, position 1: Object 1/3/1 does not contain a reference to " +
						"2/ref1/1 at /refs/2"));
		failSave(u1, testws, fail,
				makeRefData("  \nreadws/4 ; 2/refref1/1;2/4 ;  	2/leaf1/1;  ", "readws/readable",
				"1/3/1 ; \n 2/ref2/1 ; 	privws/1"), type1, p1, new TypedObjectValidationException(
						"Object #1, fail has invalid reference: Reference path starting with " +
						"readws/4, position 2: Object 2/refref1/1 does not contain a reference " +
						"to 2/4 at /refs/0"));
		
		// test fail save on bad reference parse
		// also tests failing on objects with id attributes
		failSave(u1, testws, fail, makeRefData("readws/4;;2/refref1/1"), type1, p1,
				new TypedObjectValidationException("Object #1, fail has unparseable reference " +
						"readws/4;;2/refref1/1: ID parse error in reference string " +
						"readws/4;;2/refref1/1 at position 2: reference cannot be null or the " +
						"empty string at /refs/0"));
		failSave(u1, testws, fail, makeRefData(" ; ; "), type2Ref, p1,
				new TypedObjectValidationException("Object #1, fail has unparseable reference " +
						" ; ; : ID parse error in reference string " +
						" ; ;  at position 1: reference cannot be null or the " +
						"empty string at /refs/0"));
		failSave(u1, testws, fail, makeRefData("readws/4;1;2/refref1/1"), type12Ref, p1,
				new TypedObjectValidationException("Object #1, fail has unparseable reference " +
						"readws/4;1;2/refref1/1: ID parse error in reference string " +
						"readws/4;1;2/refref1/1 at position 2: Illegal number of separators / " +
						"in object reference 1 at /refs/0"));
		
		// test fail save on bad reference type
		failSave(u1, testws, fail,
				makeRefData("  \nreadws/4 ; 2/refref1/1;2/3 ;  	2/leaf1/1;  ", "readws/readable",
				"1/3/1 ; \n 2/ref2/1 ; 	privws/1"), type1Ref, p1,
				new TypedObjectValidationException(
						"Object #1, fail has invalid reference: The type " +
						"TestSaveRefPaths.Type2-1.0 of reference " + 
						"1/3/1 ; \n 2/ref2/1 ; \tprivws/1 in this object is not " +
						"allowed - allowed types are [TestSaveRefPaths.Type1] at /refs/2"));
		failSave(u1, testws, fail,
				makeRefData("  \nreadws/4 ; 2/refref1/1;2/3 ;  	2/leaf1/1;  ",
				"1/3/1 ; \n 2/ref2/1 ; 	privws/1"), type2Ref, p1,
				new TypedObjectValidationException(
						"Object #1, fail has invalid reference: The type " +
						"TestSaveRefPaths.Type1-1.0 of reference " +
						"  \nreadws/4 ; 2/refref1/1;2/3 ;  	2/leaf1/1;   in " +
						"this object is not allowed - allowed types are " +
						"[TestSaveRefPaths.Type2] at /refs/0"));
		
		// test fail on inaccessible path head - just doing one basic test for this type of
		// thing, most tests like this are handled in saveObjectWithTypeChecking()
		failSave(u1, testws, fail,
				makeRefData("  2/refref1/1;2/3 ;  	2/leaf1/1;  ", "readws/readable",
				"1/3/1 ; \n 2/ref2/1 ; 	privws/1"), type1Ref, p1,
				new TypedObjectValidationException(
						"Object #1, fail has invalid reference: No read access to id " +
						"  2/refref1/1;2/3 ;  \t2/leaf1/1;  : Object refref1 cannot be " +
						"accessed: User user1 may not read workspace 2 at /refs/0"));
		
		/*  test failing on provenance references. Mechanisms are mostly the same so not 
		 * copying every test. Can't fail on bad ref type, obviously
		 */
		
		//bad ref
		final Map<String, Object> mt = new HashMap<>();
		Provenance pfail = new Provenance(u1).addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList(
						"  \nreadws/4 ; 2/refref1/1;2/3 ;  	2/leaf1/1;  ",
						"readws/readable",
						"1/3/1 ; \n 2/ref1/1 ; 	privws/1")));
		failSave(u1, testws, fail, mt, type1Ref, pfail,
				new TypedObjectValidationException(
						"Object #1, fail has invalid provenance reference: Reference path " +
						"starting with 1/3/1, position 1: Object 1/3/1 does not contain a " +
						"reference to 2/ref1/1"));
		
		// bad parse
		pfail = new Provenance(u1).addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList("readws/4;;2/refref1/1")));
		failSave(u1, testws, fail, mt, type1, pfail,
				new TypedObjectValidationException("Object #1, fail has unparseable provenance " +
						"reference readws/4;;2/refref1/1: ID parse error in reference string " +
						"readws/4;;2/refref1/1 at position 2: reference cannot be null or the " +
						"empty string"));
		
		// inaccessible head
		pfail = new Provenance(u1).addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList(
						"  \nreadws/4 ; 2/refref1/1;2/3 ;  	2/leaf1/1;  ",
						"readws/readable",
						"1/3/1 ; \n 2/ref2/1 ; 	privws/1",
						"1/5/1 ; 1/1/1")));
		failSave(u1, testws, fail, mt, type1Ref, pfail,
				new TypedObjectValidationException(
						"Object #1, fail has invalid provenance reference: No read access to id " +
						"1/5/1 ; 1/1/1: No object with id 5 exists in workspace 1 (name readws)"));
	}

	private void successSaveWithRefPaths(
			final WorkspaceUser u1,
			final WorkspaceIdentifier testws,
			final TypeDefId type1,
			final List<LinkedHashMap<String, String>> list)
			throws Exception {
		final List<WorkspaceSaveObject> objs = new LinkedList<>();
		final List<List<String>> resolvedRefs = new LinkedList<>();
		for (int i = 0; i < list.size(); i++) {
			final LinkedHashMap<String, String> h = list.get(i);
			final List<String> refs = new LinkedList<>();
			final List<String> rrefs = new LinkedList<>();
			for (final Entry<String, String> e: h.entrySet()) {
				refs.add(e.getKey());
				rrefs.add(e.getValue());
			}
			final Map<String, Object> incdata = new HashMap<>();
			incdata.put("refs", refs);
			objs.add(new WorkspaceSaveObject(getRandomName(), incdata, type1, null,
					new Provenance(u1), false));
			resolvedRefs.add(rrefs);
		}
		final List<ObjectInformation> ois = ws.saveObjects(
				u1, testws, objs, new IdReferenceHandlerSetFactory(100000));
		final List<ObjectIdentifier> idents = new LinkedList<>();
		for (final ObjectInformation oi: ois) {
			// should probably add a ref -> oi method
			idents.add(ObjectIdentifier.parseObjectReference(
					oi.getReferencePath().get(0).toString()));
		}
		List<WorkspaceObjectData> d = null;
		try {
			d = ws.getObjects(u1, idents);
			for (int i = 0; i < list.size(); i++) {
				final Map<String, Object> data = d.get(i).getSerializedData().getUObject()
						.asClassInstance(new TypeReference<Map<String, Object>>() {});
				final Map<String, Object> dataexp = new HashMap<>();
				dataexp.put("refs", resolvedRefs.get(i));
				assertThat("incorrect save data", dataexp, is(data));
			}
		} finally {
			destroyGetObjectsResources(d);
		}
	}
	
	@Test
	public void unserializableData() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceIdentifier read = new WorkspaceIdentifier("unserializable");
		ws.createWorkspace(foo, read.getIdentifierString(), false, null, null);
		Object data = new StringReader("foo");
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("foo", "bar");
		try {
			ws.saveObjects(foo, read, Arrays.asList(new WorkspaceSaveObject(
					new ObjectIDNoWSNoVer("jframe"), data, SAFE_TYPE1,
					new WorkspaceUserMetadata(meta),
					new Provenance(foo), false)),
					getIdFactory());
			fail("saved unserializable object");
		} catch (IllegalArgumentException iae) {
			assertThat("Actual exception: " + iae.getMessage(), iae.getMessage(), 
					is("UObject can not serialize object of this type: java.io.StringReader"));
		}
	}
	
	@Test
	public void getNonexistantObjects() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceIdentifier read = new WorkspaceIdentifier("nonexistentobjects");
		ws.createWorkspace(foo, read.getIdentifierString(), false, null, null);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("fubar", "thingy");
		JsonNode savedata = MAPPER.valueToTree(data);
		List<WorkspaceSaveObject> objects = new ArrayList<WorkspaceSaveObject>();
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("myname"),
				savedata, SAFE_TYPE1, null, new Provenance(foo), false));
		ws.saveObjects(foo, read, objects, getIdFactory());
		getNonExistantObject(foo, new ObjectIdentifier(read, 2),
				"No object with id 2 exists in workspace 1 (name nonexistentobjects)");
		getNonExistantObject(foo, new ObjectIdentifier(read, 1, 2),
				"No object with id 1 (name myname) and version 2 exists in workspace 1 " +
				"(name nonexistentobjects)");
		getNonExistantObject(foo, new ObjectIdentifier(read, "myname2"),
				"No object with name myname2 exists in workspace 1 (name nonexistentobjects)");
		getNonExistantObject(foo, new ObjectIdentifier(read, "myname", 2),
				"No object with id 1 (name myname) and version 2 exists in workspace 1 " +
				"(name nonexistentobjects)");
	}

	@Test
	public void objectIDs() throws Exception {
		WorkspaceIdentifier goodWs = new WorkspaceIdentifier("foo");
		testObjectIdentifier("f|o.A-1_2");
		testObjectIdentifier("f|o.A-1_2", 1);
		testObjectIdentifier(null, "foo", "wsi cannot be null");
		testObjectIdentifier(goodWs, null, "Object name cannot be null or the empty string");
		testObjectIdentifier(goodWs, "", "Object name cannot be null or the empty string");
		testObjectIdentifier(goodWs, "f|o.A-1_2+", "Illegal character in object name f|o.A-1_2+: +");
		testObjectIdentifier(goodWs, "-1", "Object names cannot be integers: -1");
		testObjectIdentifier(goodWs, "2345678901", "Object names cannot be integers: 2345678901"); //long
		testObjectIdentifier(goodWs, "23456789012345678901",
				"Object names cannot be integers: 23456789012345678901"); // > long
		testObjectIdentifier(goodWs, "15", "Object names cannot be integers: 15");
		testObjectIdentifier(goodWs, "f|o.A-1_2", 0, "Object version must be > 0");
		testObjectIdentifier(goodWs, TEXT256, "Object name exceeds the maximum length of 255");
		testObjectIdentifier(1);
		testObjectIdentifier(1, 1);
		testObjectIdentifier(null, 1, "wsi cannot be null");
		testObjectIdentifier(goodWs, 0, "Object id must be > 0");
		testObjectIdentifier(goodWs, 0, 1, "Object id must be > 0");
		testObjectIdentifier(goodWs, 1, 0, "Object version must be > 0");
		testCreate(goodWs, "f|o.A-1_2", null);
		testCreate(goodWs, null, 1L);
		testCreate(null, "boo", null, "wsi cannot be null");
		testCreate(goodWs, TEXT256, null, "Object name exceeds the maximum length of 255");
		testCreate(goodWs, null, null, "Must provide one and only one of object name (was: null) or id (was: null)");
		testCreate(goodWs, "boo", 1L, "Must provide one and only one of object name (was: boo) or id (was: 1)");
		testCreate(goodWs, "-1", null, "Object names cannot be integers: -1");
		testCreate(goodWs, "15", null, "Object names cannot be integers: 15");
		testCreateVer(goodWs, "boo", null, 1);
		testCreateVer(goodWs, null, 1L, 1);
		testCreateVer(goodWs, "boo", null, null);
		testCreateVer(goodWs, null, 1L, null);
		testCreateVer(goodWs, "boo", null, 0, "Object version must be > 0");
		testCreateVer(goodWs, TEXT256, null, 1, "Object name exceeds the maximum length of 255");
		testCreateVer(goodWs, null, 1L, 0, "Object version must be > 0");
		testRef("foo/bar");
		testRef("foo/bar/1");
		testRef("foo/bar/1/2", "Illegal number of separators / in object reference foo/bar/1/2");
		testRef("foo/" + TEXT256 + "/1", "Object name exceeds the maximum length of 255");
		testRef("foo/bar/n", "Unable to parse version portion of object reference foo/bar/n to an integer");
		testRef("foo", "Illegal number of separators / in object reference foo");
		testRef("1/2");
		testRef("1/2/3");
		testRef("1/2/3/4", "Illegal number of separators / in object reference 1/2/3/4");
		testRef("1/2/n", "Unable to parse version portion of object reference 1/2/n to an integer");
		testRef("1", "Illegal number of separators / in object reference 1");
		testRef("foo/2");
		testRef("2/foo");
		testRef("foo/2/1");
		testRef("2/foo/1");
	}
	
	@Test
	public void deleteUndelete() throws Exception {
		WorkspaceUser user = new WorkspaceUser("deleteundelete");
		WorkspaceIdentifier read = new WorkspaceIdentifier("deleteundelete");
		WorkspaceInformation readinfo = ws.createWorkspace(user,
				read.getIdentifierString(), false, "descrip", null);
		long wsid = readinfo.getId();
		Instant lastReadDate = readinfo.getModDate();
		Map<String, String> data1 = new HashMap<String, String>();
		Map<String, String> data2 = new HashMap<String, String>();
		data1.put("data", "1");
		data2.put("data", "2");
		WorkspaceSaveObject sobj1 = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("obj"), data1, SAFE_TYPE1, null, new Provenance(user), false);
		ws.saveObjects(user, read, Arrays.asList(sobj1,
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("obj"), data2, SAFE_TYPE1,
				null, new Provenance(user), false)), getIdFactory());
		ObjectIdentifier o1 = new ObjectIdentifier(read, "obj", 1);
		ObjectIdentifier o2 = new ObjectIdentifier(read, "obj", 2);
		
		Map<ObjectIdentifier, Object> idToData = new HashMap<ObjectIdentifier, Object>();
		idToData.put(o1, data1);
		idToData.put(o2, data2);
		List<ObjectIdentifier> objs = new ArrayList<ObjectIdentifier>(idToData.keySet());
		
		checkNonDeletedObjs(user, idToData);
		List<ObjectIdentifier> obj1 = new ArrayList<ObjectIdentifier>(Arrays.asList(o1));
		List<ObjectIdentifier> obj2 = new ArrayList<ObjectIdentifier>(Arrays.asList(o2));
		try {
			ws.setObjectsDeleted(new WorkspaceUser("bar"), obj1, true);
			fail("deleted objects w/o auth");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception", ioe.getLocalizedMessage(),
					is("Object obj cannot be accessed: User bar may not delete objects from workspace deleteundelete"));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is(o1));
		}
		try {
			ws.setObjectsDeleted(new WorkspaceUser("bar"), obj1, false);
			fail("undeleted objects w/o auth");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception", ioe.getLocalizedMessage(),
					is("Object obj cannot be accessed: User bar may not undelete objects from workspace deleteundelete"));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is(o1));
		}
		lastReadDate = ws.getWorkspaceInformation(user, read).getModDate();
		ws.setObjectsDeleted(user, obj1, true);
		lastReadDate = assertWorkspaceDateUpdated(user, read, lastReadDate,
				"ws date updated on delete");
		String err = String.format("Object 1 (name obj) in workspace %s (name deleteundelete) " +
				"has been deleted", wsid);
		failToGetDeletedObjects(user, objs, err);
		failToGetDeletedObjects(user, obj1, err);
		failToGetDeletedObjects(user, obj2, err);
		
		try {
			ws.setObjectsDeleted(user, obj2, true); //should have no effect
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception", nsoe.getLocalizedMessage(), is(err));
		}
		failToGetDeletedObjects(user, objs, err);
		failToGetDeletedObjects(user, obj1, err);
		failToGetDeletedObjects(user, obj2, err);
		
		lastReadDate = ws.getWorkspaceInformation(user, read).getModDate();
		ws.setObjectsDeleted(user, obj2, false);
		lastReadDate = assertWorkspaceDateUpdated(user, read, lastReadDate, "ws date updated on undelete");
		checkNonDeletedObjs(user, idToData);
		
		lastReadDate = ws.getWorkspaceInformation(user, read).getModDate();
		ws.setObjectsDeleted(user, obj1, false);//should have no effect
		lastReadDate = assertWorkspaceDateUpdated(user, read, lastReadDate, "ws date updated on undelete");
		checkNonDeletedObjs(user, idToData);
		
		lastReadDate = ws.getWorkspaceInformation(user, read).getModDate();
		ws.setObjectsDeleted(user, obj2, true);
		lastReadDate = assertWorkspaceDateUpdated(user, read, lastReadDate, "ws date updated on delete");
		failToGetDeletedObjects(user, objs, err);
		failToGetDeletedObjects(user, obj1, err);
		failToGetDeletedObjects(user, obj2, err);

		//save should undelete
		ws.saveObjects(user, read, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("obj"), data1,
						SAFE_TYPE1, null, new Provenance(user), false)), getIdFactory());
		ObjectIdentifier o3 = new ObjectIdentifier(read, "obj", 3);
		idToData.put(o3, data1);
		objs = new ArrayList<ObjectIdentifier>(idToData.keySet());
		
		checkNonDeletedObjs(user, idToData);
		assertThat("can get ws description", ws.getWorkspaceDescription(user, read),
				is("descrip"));
		checkWSInfo(ws.getWorkspaceInformation(user, read), user, "deleteundelete", 1, Permission.OWNER, false, "unlocked", MT_MAP);
		WorkspaceUser bar = new WorkspaceUser("bar");
		ws.setPermissions(user, read, Arrays.asList(bar), Permission.ADMIN);
		Map<User, Permission> p = new HashMap<User, Permission>();
		p.put(user, Permission.OWNER);
		p.put(bar, Permission.ADMIN);
		assertThat("can get perms", ws.getPermissions(user, Arrays.asList(read)).get(0), is(p));
		failDeleteWorkspace(bar, read, true, new WorkspaceAuthorizationException(
				"User bar may not delete workspace deleteundelete"));
		failDeleteWorkspace(bar, read, false, new WorkspaceAuthorizationException(
				"User bar may not undelete workspace deleteundelete"));
		failDeleteWorkspace(bar, new WorkspaceIdentifier(100), true,
				new NoSuchWorkspaceException("No workspace with id 100 exists",
						new WorkspaceIdentifier(100)));
		failDeleteWorkspace(bar, new WorkspaceIdentifier("nows"), true,
				new NoSuchWorkspaceException("No workspace with name nows exists",
						new WorkspaceIdentifier("nows")));
		
		WorkspaceInformation read1 = ws.getWorkspaceInformation(user, read);
		final long id = ws.setWorkspaceDeleted(user, read, true);
		assertThat("incorrect ws id", id, is(1L));
		WorkspaceInformation read2 = ws.listWorkspaces(user, null, null, null,
				null, null, true, true, false).get(0);
		try {
			ws.getWorkspaceDescription(user, read);
			fail("got description from deleted workspace");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is("Workspace deleteundelete is deleted"));
		}
		try {
			ws.getWorkspaceInformation(user, read);
			fail("got meta from deleted workspace");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is("Workspace deleteundelete is deleted"));
		}
		try {
			ws.setPermissions(user, read, Arrays.asList(bar), Permission.NONE);
			fail("set perms on deleted workspace");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is("Workspace deleteundelete is deleted"));
		}
		try {
			ws.getPermissions(user, Arrays.asList(read));
			fail("got perms from deleted workspace");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is("Workspace deleteundelete is deleted"));
		}
		failGetObjects(bar, objs, new InaccessibleObjectException("Object obj cannot be " +
				"accessed: Workspace deleteundelete is deleted", null));
		try {
			ws.getObjectInformation(bar, objs, false, false);
			fail("got obj meta from deleted workspace");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception msg", ioe.getLocalizedMessage(),
					is("Object obj cannot be accessed: Workspace deleteundelete is deleted"));
		}
		try {
			ws.saveObjects(bar, read, Arrays.asList(sobj1), getIdFactory());
			fail("saved objs from deleted workspace");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is("Workspace deleteundelete is deleted"));
		}
		try {
			ws.setObjectsDeleted(bar, obj1, true);
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception msg", ioe.getLocalizedMessage(),
					is("Object obj cannot be accessed: Workspace deleteundelete is deleted"));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is(o1));
		}
		final long id2 = ws.setWorkspaceDeleted(user, read, false);
		assertThat("incorrect ws id", id2, is(1L));
		WorkspaceInformation read3 = ws.getWorkspaceInformation(user, read);
		checkNonDeletedObjs(user, idToData);
		assertThat("can get ws description", ws.getWorkspaceDescription(user, read),
				is("descrip"));
		checkWSInfo(ws.getWorkspaceInformation(user, read), user, "deleteundelete", 1,
				Permission.OWNER, false, "unlocked", MT_MAP);
		ws.setPermissions(user, read, Arrays.asList(bar), Permission.ADMIN);
		assertThat("can get perms", ws.getPermissions(
				user, Arrays.asList(read)).get(0), is(p));
		
		assertTrue("date changed on delete", read1.getModDate().isBefore(read2.getModDate()));
		assertTrue("date changed on undelete", read2.getModDate().isBefore(read3.getModDate()));
	}
	
	@Test
	public void adminDeleteWorkspace() throws Exception {
		// test un/delete workspace as admin
		final WorkspaceUser u1 = new WorkspaceUser("foo");
		final WorkspaceUser u2 = new WorkspaceUser("bar");
		final WorkspaceIdentifier delws = new WorkspaceIdentifier("foobar");
		final WorkspaceIdentifier delwsid = new WorkspaceIdentifier(1L);
		ws.createWorkspace(u1, delws.getName(), false, "whee", null);
		
		final WorkspaceIdentifier nows = new WorkspaceIdentifier("nows");
		
		//test undelete
		ws.setWorkspaceDeleted(u1, delws, true);
		failGetWorkspaceDesc(u1, delws, new NoSuchWorkspaceException(
				"Workspace foobar is deleted", delws));
		failDeleteWorkspace(u2, delwsid, false, new WorkspaceAuthorizationException(
				"User bar may not undelete workspace 1"));
		failDeleteWorkspaceAsAdmin(u2, delws, false, false,
				new WorkspaceAuthorizationException("User bar may not undelete workspace foobar"));
		failDeleteWorkspaceAsAdmin(u2, delws, true, true,
				new NoSuchWorkspaceException("Workspace foobar is deleted", delws));
		failDeleteWorkspaceAsAdmin(u2, nows, false, true,
				new NoSuchWorkspaceException("No workspace with name nows exists", delws));
		ws.setWorkspaceDeleted(u2, delwsid, false, true);
		assertThat("incorrect ws desc", ws.getWorkspaceDescription(u1, delws), is("whee"));
		
		//test delete
		failDeleteWorkspace(u2, delwsid, true, new WorkspaceAuthorizationException(
				"User bar may not delete workspace 1"));
		failDeleteWorkspaceAsAdmin(u2, delws, true, false,
				new WorkspaceAuthorizationException("User bar may not delete workspace foobar"));
		failDeleteWorkspaceAsAdmin(u2, nows, true, true,
				new NoSuchWorkspaceException("No workspace with name nows exists", delws));
		ws.setWorkspaceDeleted(u2, delwsid, true, true);
		failGetWorkspaceDesc(u1, delws, new NoSuchWorkspaceException(
				"Workspace foobar is deleted", delws));
	}
	
	@Test
	public void adminDeleteWorkspaceFailLocked() throws Exception {
		final WorkspaceUser u1 = new WorkspaceUser("foo");
		final WorkspaceIdentifier delws = new WorkspaceIdentifier("foobar");
		ws.createWorkspace(u1, delws.getName(), false, "whee", null);
		
		ws.lockWorkspace(u1, delws);
		failDeleteWorkspaceAsAdmin(u1, delws, true, true, new WorkspaceAuthorizationException(
				"The workspace with id 1, name foobar, is locked and may not be modified"));
	}

	@Test
	public void testTypeMd5s() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		String typeDefName = "SomeModule.AType";
		Map<String,String> type2md5 = types.translateToMd5Types(Arrays.asList(typeDefName + "-1.0"),null);
		assertThat(type2md5.size(), is(1));
		String md5TypeDef = type2md5.get(typeDefName + "-1.0");
		assertThat(md5TypeDef, is(notNullValue()));
		Map<String, List<String>> md52semantic = types.translateFromMd5Types(Arrays.asList(md5TypeDef));
		assertThat(md52semantic.size(), is(1));
		List<String> semList = md52semantic.get(md5TypeDef);
		assertThat(semList, is(notNullValue()));
		assertThat(semList.size(), is(2));
		for (String semText : semList) {
			TypeDefId semTypeDef = TypeDefId.fromTypeString(semText);
			assertThat(semTypeDef.getType().getTypeString(), is(typeDefName));
			String verText = semTypeDef.getVerString();
			assertThat("0.1".equals(verText) || "1.0".equals(verText), is(true));
		}
	}
	
	@Test
	public void testListModules() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		Map<String,String> moduleNamesInList = new HashMap<String,String>();
		for(String mod:types.listModules(null)) {
			moduleNamesInList.put(mod, "");
		}
		assertThat(moduleNamesInList.containsKey("SomeModule"), is(true));
		assertThat(moduleNamesInList.containsKey("TestModule"), is(true));
	}
	
	@Test
	public void testListModuleVersions() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		assertThat(types.getModuleVersions("SomeModule", null).size(), is(3));
		assertThat(types.getModuleVersions("SomeModule", new WorkspaceUser("foo")).size(), is(4));
		assertThat(types.getModuleVersions("TestModule", null).size(), is(2));
		assertThat(types.getModuleVersions("TestModule", new WorkspaceUser("foo")).size(), is(5));
	}
	
	@Test
	public void testGetModuleInfo() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		ModuleInfo m = types.getModuleInfo(null, new ModuleDefId("TestModule"));
		assertThat(m.isReleased(), is(true));
		Map<String,String> funcNamesInList = new HashMap<String,String>();
		for(String func : m.getFunctions() ){
			funcNamesInList.put(func, "");
		}
		assertThat(funcNamesInList.containsKey("TestModule.getFeature-2.0"), is(true));
		assertThat(funcNamesInList.containsKey("TestModule.getGenome-1.0"), is(true));

		Map<String,String> typeNamesInList = new HashMap<String,String>();
		for(Entry<AbsoluteTypeDefId, String> type : m.getTypes().entrySet() ){
			typeNamesInList.put(type.getKey().getTypeString(),"");
		}
		assertThat(typeNamesInList.containsKey("TestModule.Genome-2.0"), is(true));
		assertThat(typeNamesInList.containsKey("TestModule.Feature-1.0"), is(true));
		
		try {
			types.getModuleInfo(null, new ModuleDefId("MadeUpModuleThatIsNotThere"));
			fail("getModuleInfo of non existant module should throw a NoSuchModuleException");
		} catch (NoSuchModuleException e) {}
		ModuleInfo m2 = types.getModuleInfo(new WorkspaceUser("foo"), new ModuleDefId("UnreleasedModule"));
		assertThat(m2.getOwners().get(0), is("foo"));
		assertThat(m2.isReleased(), is(false));
		List<Long> verList = types.getModuleVersions("UnreleasedModule", new WorkspaceUser("foo"));
		assertThat(verList.size(), is(1));
		assertThat(verList.get(0), is(m2.getVersion()));
	}
	
	@Test
	public void testGetJsonSchema() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		try {
			types.getJsonSchema(new TypeDefId("TestModule.NonExistantType"), null);
			fail("getJsonSchema of non existant type should throw a NoSuchTypeException");
		} catch (NoSuchTypeException e) {}
		
		// get several different schemas, make sure that no exceptions are thrown and it is valid json!
		String schema = types.getJsonSchema(new TypeDefId(new TypeDefName("TestModule.Genome"),2,0), null);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode schemaNode = mapper.readTree(schema);
		assertThat(schemaNode.get("id").asText(), is("Genome"));
		
		schema = types.getJsonSchema(new TypeDefId(new TypeDefName("TestModule.Genome"),2), null);
		schemaNode = mapper.readTree(schema);
		assertThat(schemaNode.get("id").asText(), is("Genome"));
		
		schema = types.getJsonSchema(new TypeDefId("TestModule.Genome"), null);
		schemaNode = mapper.readTree(schema);
		assertThat(schemaNode.get("id").asText(), is("Genome"));
	}
	
	@Test
	public void testGetTypeInfo() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		TypeDetailedInfo info = types.getTypeInfo("TestModule.Genome", false, null);
		assertThat(info.getTypeDefId(), is("TestModule.Genome-2.0"));
		assertThat(info.getReleasedModuleVersions().size(), is(1));
		assertThat(info.getReleasedTypeVersions().size(), is(2));
		info = types.getTypeInfo("TestModule.Feature", false, null);
		assertThat(info.getTypeDefId(), is("TestModule.Feature-1.0"));
		assertThat(info.getReleasedModuleVersions().size(), is(2));
		assertThat(info.getReleasedTypeVersions().size(), is(1));
		TypeDetailedInfo info2 = types.getTypeInfo("UnreleasedModule.AType-0.1", false, new WorkspaceUser("foo"));
		assertThat(info2.getUsingFuncDefIds().size(), is(1));
		assertThat(info2.getModuleVersions().size(), is(1));
		assertThat(info2.getTypeVersions().size(), is(1));
		assertThat(info2.getReleasedModuleVersions().size(), is(0));
		assertThat(info2.getReleasedTypeVersions().size(), is(0));
		assertThat(info2.getJsonSchema().contains("kidl-structure"), is(true));
		assertThat(info2.getParsingStructure().contains("Bio::KBase::KIDL::KBT::Typedef"),
				is(true));
	}
	
	@Test
	public void testGetFuncInfo() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		try {
			types.getFuncInfo("NoModuleThatExists.getFeature", false, null);
			fail("getFuncInfo of non existant module should throw a NoSuchModuleException");
		} catch (NoSuchModuleException e) {}
		try {
			types.getFuncInfo("TestModule.noFunctionThatIKnowOf", false, null);
			fail("getFuncInfo of non existant module should throw a NoSuchFuncException");
		} catch (NoSuchFuncException e) {}
		FuncDetailedInfo info = types.getFuncInfo("TestModule.getFeature", false, null);
		assertThat(info.getFuncDefId(), is("TestModule.getFeature-2.0"));
		assertThat(info.getReleasedModuleVersions().size(), is(1));
		assertThat(info.getReleasedFuncVersions().size(), is(2));
		info = types.getFuncInfo("TestModule.getGenome-1.0", false, null);
		assertThat(info.getFuncDefId(), is("TestModule.getGenome-1.0"));
		assertThat(info.getReleasedModuleVersions().size(), is(1));
		assertThat(info.getReleasedFuncVersions().size(), is(1));
		FuncDetailedInfo info2 = types.getFuncInfo("UnreleasedModule.aFunc-0.1", false, new WorkspaceUser("foo"));
		assertThat(info2.getUsedTypeDefIds().size(), is(1));
		assertThat(info2.getModuleVersions().size(), is(1));
		assertThat(info2.getFuncVersions().size(), is(1));
		assertThat(info2.getReleasedModuleVersions().size(), is(0));
		assertThat(info2.getReleasedFuncVersions().size(), is(0));
		assertThat(info2.getParsingStructure().contains("Bio::KBase::KIDL::KBT::Funcdef"),
				is(true));
	}
	
	private void setUpCopyWorkspaces(WorkspaceUser user1, WorkspaceUser user2,
			String refws, String ws1, String ws2) throws Exception {
		TypeDefId reftype = new TypeDefId(new TypeDefName("CopyRev", "RefType"), 1, 0);
		
		WorkspaceIdentifier refs = new WorkspaceIdentifier(refws);
		ws.createWorkspace(user1, refs.getName(), false, null, null);
		LinkedList<WorkspaceSaveObject> refobjs = new LinkedList<WorkspaceSaveObject>();
		for (int i = 0; i < 4; i++) {
			refobjs.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto" + (i + 1)),
					new HashMap<String, String>(), SAFE_TYPE1, null, new Provenance(user1),
					false));
		}
		ws.saveObjects(user1, refs, refobjs, getIdFactory());
		List<WorkspaceSaveObject> wso = Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto2"), new HashMap<String, String>(),
				SAFE_TYPE1, null, new Provenance(user1), false));
		ws.saveObjects(user1, refs, wso, getIdFactory());
		ws.saveObjects(user1, refs, wso, getIdFactory());
		
		
		Map<String, String> meta1 = makeSimpleMeta("foo", "bar");
		Map<String, String> meta2 = makeSimpleMeta("foo", "baz");
		Map<String, String> meta3 = makeSimpleMeta("foo", "bak");
		Map<String, Object> data1 = makeRefData(refws + "/auto2/2");
		Map<String, Object> data2 = makeRefData(refws + "/auto4");
		Map<String, Object> data3 = makeRefData(refws + "/auto1");
		
		Provenance prov1 = new Provenance(user1);
		prov1.addAction(new ProvenanceAction()
				.withCommandLine("A command line")
				.withDescription("descrip")
				.withIncomingArgs(Arrays.asList("a", "b", "c"))
				.withMethod("method")
				.withMethodParameters(Arrays.asList((Object) meta1))
				.withOutgoingArgs(Arrays.asList("d", "e", "f"))
				.withScript("script")
				.withScriptVersion("2.1")
				.withServiceName("service")
				.withServiceVersion("3")
				.withTime(new Date(45))
				.withWorkspaceObjects(Arrays.asList(refws + "/auto3", refws + "/auto2/2")));
		prov1.addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList(refws + "/auto2/1", refws + "/auto1")));
		Provenance prov2 = new Provenance(user1);
		Provenance prov3 = new Provenance(user1);
		prov2.addAction(new ProvenanceAction(prov1.getActions().get(0)).withServiceVersion("4")
				.withWorkspaceObjects(Arrays.asList(refws + "/auto2")));
		prov3.addAction(new ProvenanceAction(prov1.getActions().get(0)).withServiceVersion("5")
				.withWorkspaceObjects(Arrays.asList(refws + "/auto3/1")));
		
		WorkspaceIdentifier cp1 = new WorkspaceIdentifier(ws1);
		WorkspaceIdentifier cp2 = new WorkspaceIdentifier(ws2);
		ws.createWorkspace(user1, cp1.getName(), false, null, null).getId();
		ws.createWorkspace(user2, cp2.getName(), false, null, null).getId();
		saveObject(user1, cp1, meta1, data1, reftype, "hide", prov1, true);
		saveObject(user1, cp1, meta2, data2, reftype, "hide", prov2, true);
		saveObject(user1, cp1, meta3, data3, reftype, "hide", prov2, true);
		saveObject(user1, cp1, meta1, data1, reftype, "orig", prov1);
		saveObject(user1, cp1, meta2, data2, reftype, "orig", prov2);
		saveObject(user1, cp1, meta3, data3, reftype, "orig", prov3);
		saveObject(user1, cp1, meta1, data1, reftype, "hidetarget", prov1, true);
	}
	
	@Test
	public void copyRevert() throws Exception {
		WorkspaceUser user1 = new WorkspaceUser("foo");
		WorkspaceUser user2 = new WorkspaceUser("bar");
		
		String wsrefs = "copyrevertrefs";
		String ws1 = "copyrevert1";
		String ws2 = "copyrevert2";
		setUpCopyWorkspaces(user1, user2, wsrefs, ws1, ws2);
		WorkspaceIdentifier cp1 = new WorkspaceIdentifier(ws1);
		WorkspaceIdentifier cp2 = new WorkspaceIdentifier(ws2);
		WorkspaceInformation cp1info = ws.getWorkspaceInformation(user1, cp1);
		WorkspaceInformation cp2info = ws.getWorkspaceInformation(user2, cp2);
		long wsid1 = cp1info.getId();
		long wsid2 = cp2info.getId();
		Instant cp1LastDate = cp1info.getModDate();
		Instant cp2LastDate = cp2info.getModDate();
		
		ObjectIdentifier oihide = new ObjectIdentifier(cp1, "hide");
		List<ObjectInformation> objs = ws.getObjectHistory(user1, oihide);
		ObjectInformation save11 = objs.get(0);
		ObjectInformation save12 = objs.get(1);
		ObjectInformation save13 = objs.get(2);
		
		WorkspaceObjectData wod = ws.getObjects(user1, Arrays.asList(oihide)).get(0);
		destroyGetObjectsResources(Arrays.asList(wod));
		WorkspaceObjectData woi = ws.getObjects(user1, Arrays.asList(oihide), true).get(0);
		assertThat("copy ref for obj is null", wod.getCopyReference(), is((Reference) null));
		assertThat("copy ref for prov is null", woi.getCopyReference(), is((Reference) null));
		
		//copy entire stack of hidden objects
		cp1LastDate = ws.getWorkspaceInformation(user1, cp1).getModDate();
		ObjectInformation copied = ws.copyObject(user1,
				ObjectIdentifier.parseObjectReference("copyrevert1/hide"),
				ObjectIdentifier.parseObjectReference("copyrevert1/copyhide"));
		cp1LastDate = assertWorkspaceDateUpdated(user1, cp1, cp1LastDate, "ws date updated on copy");
		compareObjectAndInfo(save13, copied, user1, wsid1, cp1.getName(), 4, "copyhide", 3);
		List<ObjectInformation> copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, 4));
		compareObjectAndInfo(save11, copystack.get(0), user1, wsid1, cp1.getName(), 4, "copyhide", 1);
		compareObjectAndInfo(save12, copystack.get(1), user1, wsid1, cp1.getName(), 4, "copyhide", 2);
		compareObjectAndInfo(save13, copystack.get(2), user1, wsid1, cp1.getName(), 4, "copyhide", 3);
		checkUnhiddenObjectCount(user1, cp1, 6, 10);
		
		objs = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "orig"));
		save11 = objs.get(0);
		save12 = objs.get(1);
		save13 = objs.get(2);
		
		//copy stack of unhidden objects
		copied = ws.copyObject(user1,
				ObjectIdentifier.parseObjectReference("copyrevert1/orig"),
				ObjectIdentifier.parseObjectReference("copyrevert1/copied"));
		cp1LastDate = assertWorkspaceDateUpdated(user1, cp1, cp1LastDate, "ws date updated on copy");
		compareObjectAndInfo(save13, copied, user1, wsid1, cp1.getName(), 5, "copied", 3);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "copied"));
		compareObjectAndInfo(save11, copystack.get(0), user1, wsid1, cp1.getName(), 5, "copied", 1);
		compareObjectAndInfo(save12, copystack.get(1), user1, wsid1, cp1.getName(), 5, "copied", 2);
		compareObjectAndInfo(save13, copystack.get(2), user1, wsid1, cp1.getName(), 5, "copied", 3);
		checkUnhiddenObjectCount(user1, cp1, 9, 13);
		
		//copy visible object to pre-existing hidden object
		copied = ws.copyObject(user1,
				ObjectIdentifier.parseObjectReference("copyrevert1/orig"),
				new ObjectIdentifier(cp1, "hidetarget"));
		cp1LastDate = assertWorkspaceDateUpdated(user1, cp1, cp1LastDate, "ws date updated on copy");
		compareObjectAndInfo(save13, copied, user1, wsid1, cp1.getName(), 3, "hidetarget", 2);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, 3));
		//0 is original object
		compareObjectAndInfo(save13, copystack.get(1), user1, wsid1, cp1.getName(), 3, "hidetarget", 2);
		checkUnhiddenObjectCount(user1, cp1, 9, 14);
		
		//copy hidden object to pre-existing visible object
		//check that the to version is ignored
		copied = ws.copyObject(user1, new ObjectIdentifier(cp1, "orig"),
				new ObjectIdentifier(cp1, 5, 600));
		compareObjectAndInfo(save13, copied, user1, wsid1, cp1.getName(), 5, "copied", 4);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, 5));
		compareObjectAndInfo(save13, copystack.get(3), user1, wsid1, cp1.getName(), 5, "copied", 4);
		checkUnhiddenObjectCount(user1, cp1, 10, 15);
		
		//copy specific version to existing object
		copied = ws.copyObject(user1,
				new ObjectIdentifier(new WorkspaceIdentifier(wsid1), 2, 2),
				ObjectIdentifier.parseObjectReference("copyrevert1/copied"));
		compareObjectAndInfo(save12, copied, user1, wsid1, cp1.getName(), 5, "copied", 5);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "copied"));
		compareObjectAndInfo(save11, copystack.get(0), user1, wsid1, cp1.getName(), 5, "copied", 1);
		compareObjectAndInfo(save12, copystack.get(1), user1, wsid1, cp1.getName(), 5, "copied", 2);
		compareObjectAndInfo(save13, copystack.get(2), user1, wsid1, cp1.getName(), 5, "copied", 3);
		compareObjectAndInfo(save13, copystack.get(3), user1, wsid1, cp1.getName(), 5, "copied", 4);
		compareObjectAndInfo(save12, copystack.get(4), user1, wsid1, cp1.getName(), 5, "copied", 5);
		checkUnhiddenObjectCount(user1, cp1, 11, 16);
		
		//copy specific version to  hidden existing object
		copied = ws.copyObject(user1,
				new ObjectIdentifier(new WorkspaceIdentifier(wsid1), 2, 2),
				ObjectIdentifier.parseObjectReference("copyrevert1/hidetarget"));
		compareObjectAndInfo(save12, copied, user1, wsid1, cp1.getName(), 3, "hidetarget", 3);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "hidetarget"));
		//0 is original object
		compareObjectAndInfo(save13, copystack.get(1), user1, wsid1, cp1.getName(), 3, "hidetarget", 2);
		compareObjectAndInfo(save12, copystack.get(2), user1, wsid1, cp1.getName(), 3, "hidetarget", 3);
		checkUnhiddenObjectCount(user1, cp1, 11, 17);
		
		//copy specific version to new object
		copied = ws.copyObject(user1,
				new ObjectIdentifier(new WorkspaceIdentifier(wsid1), 2, 2),
				ObjectIdentifier.parseObjectReference("copyrevert1/newobj"));
		compareObjectAndInfo(save12, copied, user1, wsid1, cp1.getName(), 6, "newobj", 1);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "newobj"));
		compareObjectAndInfo(save12, copystack.get(0), user1, wsid1, cp1.getName(), 6, "newobj", 1);
		checkUnhiddenObjectCount(user1, cp1, 12, 18);
		
		//revert normal object
		cp1LastDate = ws.getWorkspaceInformation(user1, cp1).getModDate();
		copied = ws.revertObject(user1,
				ObjectIdentifier.parseObjectReference("copyrevert1/copied/2"));
		cp1LastDate = assertWorkspaceDateUpdated(user1, cp1, cp1LastDate, "ws date updated on revert");
		compareObjectAndInfo(save12, copied, user1, wsid1, cp1.getName(), 5, "copied", 6);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "copied"));
		compareObjectAndInfo(save11, copystack.get(0), user1, wsid1, cp1.getName(), 5, "copied", 1);
		compareObjectAndInfo(save12, copystack.get(1), user1, wsid1, cp1.getName(), 5, "copied", 2);
		compareObjectAndInfo(save13, copystack.get(2), user1, wsid1, cp1.getName(), 5, "copied", 3);
		compareObjectAndInfo(save13, copystack.get(3), user1, wsid1, cp1.getName(), 5, "copied", 4);
		compareObjectAndInfo(save12, copystack.get(4), user1, wsid1, cp1.getName(), 5, "copied", 5);
		compareObjectAndInfo(save12, copystack.get(5), user1, wsid1, cp1.getName(), 5, "copied", 6);
		checkUnhiddenObjectCount(user1, cp1, 13, 19);
		
		//revert hidden object
		copied = ws.revertObject(user1,
				ObjectIdentifier.parseObjectReference("copyrevert1/hidetarget/2"));
		cp1LastDate = assertWorkspaceDateUpdated(user1, cp1, cp1LastDate, "ws date updated on revert");
		compareObjectAndInfo(save13, copied, user1, wsid1, cp1.getName(), 3, "hidetarget", 4);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "hidetarget"));
		//0 is original object
		compareObjectAndInfo(save13, copystack.get(1), user1, wsid1, cp1.getName(), 3, "hidetarget", 2);
		compareObjectAndInfo(save12, copystack.get(2), user1, wsid1, cp1.getName(), 3, "hidetarget", 3);
		compareObjectAndInfo(save13, copystack.get(3), user1, wsid1, cp1.getName(), 3, "hidetarget", 4);
		checkUnhiddenObjectCount(user1, cp1, 13, 20);
		
		//copy to new ws
		ws.setPermissions(user2, cp2, Arrays.asList(user1), Permission.WRITE);
		cp2LastDate = ws.getWorkspaceInformation(user1, cp2).getModDate();
		copied = ws.copyObject(user1,
				ObjectIdentifier.parseObjectReference("copyrevert1/orig"),
				ObjectIdentifier.parseObjectReference("copyrevert2/copied"));
		cp2LastDate = assertWorkspaceDateUpdated(user1, cp2, cp2LastDate, "ws date updated on copy");
		compareObjectAndInfo(save13, copied, user1, wsid2, cp2.getName(), 1, "copied", 3);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp2, "copied"));
		compareObjectAndInfo(save11, copystack.get(0), user1, wsid2, cp2.getName(), 1, "copied", 1);
		compareObjectAndInfo(save12, copystack.get(1), user1, wsid2, cp2.getName(), 1, "copied", 2);
		compareObjectAndInfo(save13, copystack.get(2), user1, wsid2, cp2.getName(), 1, "copied", 3);
		checkUnhiddenObjectCount(user1, cp2, 3, 3);
		checkUnhiddenObjectCount(user1, cp1, 13, 20);
		
		//copy to deleted object
		ws.setObjectsDeleted(user1, Arrays.asList(
				ObjectIdentifier.parseObjectReference("copyrevert1/copied")), true);
		copied = ws.copyObject(user1,
				ObjectIdentifier.parseObjectReference("copyrevert1/orig"),
				ObjectIdentifier.parseObjectReference("copyrevert1/copied"));
		compareObjectAndInfo(save13, copied, user1, wsid1, cp1.getName(), 5, "copied", 7);
		copystack = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "copied"));
		compareObjectAndInfo(save11, copystack.get(0), user1, wsid1, cp1.getName(), 5, "copied", 1);
		compareObjectAndInfo(save12, copystack.get(1), user1, wsid1, cp1.getName(), 5, "copied", 2);
		compareObjectAndInfo(save13, copystack.get(2), user1, wsid1, cp1.getName(), 5, "copied", 3);
		compareObjectAndInfo(save13, copystack.get(3), user1, wsid1, cp1.getName(), 5, "copied", 4);
		compareObjectAndInfo(save12, copystack.get(4), user1, wsid1, cp1.getName(), 5, "copied", 5);
		compareObjectAndInfo(save12, copystack.get(5), user1, wsid1, cp1.getName(), 5, "copied", 6);
		compareObjectAndInfo(save13, copystack.get(6), user1, wsid1, cp1.getName(), 5, "copied", 7);
		checkUnhiddenObjectCount(user1, cp1, 14, 21);

		failCopy(null, new ObjectIdentifier(cp1, "whooga"),
				new ObjectIdentifier(cp1, "hidetarget"), new InaccessibleObjectException(
						"Object whooga cannot be accessed: Anonymous users may not read " +
						"workspace copyrevert1", null));
		failRevert(null, new ObjectIdentifier(cp1, "whooga"), new InaccessibleObjectException(
				"Object whooga cannot be accessed: Anonymous users may not write to " +
				"workspace copyrevert1", null));
		
		failCopy(user1, new ObjectIdentifier(cp1, "foo"),
				new ObjectIdentifier(cp1, "bar"), new NoSuchObjectException(
						"No object with name foo exists in workspace 2 (name copyrevert1)", null));
		failRevert(user1, new ObjectIdentifier(cp1, "foo"),  new NoSuchObjectException(
				"No object with name foo exists in workspace 2 (name copyrevert1)", null));
		failRevert(user1, new ObjectIdentifier(cp1, "orig", 4),  new NoSuchObjectException(
				"No object with id 2 (name orig) and version 4 exists in workspace 2 " +
						"(name copyrevert1)", null));
		failCopy(user1, new ObjectIdentifier(cp1, "orig"),
				new ObjectIdentifier(cp1, 7), new NoSuchObjectException(
						"Copy destination is specified as object id 7 in workspace 2 " +
						"which does not exist.", null));
		
		ws.setObjectsDeleted(user1, Arrays.asList(new ObjectIdentifier(cp1, "copied")), true);
		failCopy(user1, new ObjectIdentifier(cp1, "copied"),
				new ObjectIdentifier(cp1, "hidetarget"), new NoSuchObjectException(
						"Object 5 (name copied) in workspace 2 (name copyrevert1) has been " +
						"deleted", null));
		failRevert(user1, new ObjectIdentifier(cp1, "copied"), new NoSuchObjectException(
				"Object 5 (name copied) in workspace 2 (name copyrevert1) has been deleted",
				null));
		
		cp2LastDate = ws.getWorkspaceInformation(user1, cp2).getModDate();
		ws.copyObject(user1, new ObjectIdentifier(cp1, "orig"), new ObjectIdentifier(cp2, "foo")); //should work
		cp2LastDate = assertWorkspaceDateUpdated(user1, cp2, cp2LastDate, "ws date updated on copy");
		ws.setWorkspaceDeleted(user2, cp2, true);
		failCopy(user1, new ObjectIdentifier(cp1, "orig"), new ObjectIdentifier(cp2, "foo1"),
				new InaccessibleObjectException("Object foo1 cannot be accessed: Workspace " +
						"copyrevert2 is deleted", null));
		failCopy(user1, new ObjectIdentifier(cp2, "foo"), new ObjectIdentifier(cp2, "foo1"),
				new InaccessibleObjectException("Object foo cannot be accessed: Workspace " +
						"copyrevert2 is deleted", null));
		failRevert(user1, new ObjectIdentifier(cp2, "foo"),
				new InaccessibleObjectException("Object foo cannot be accessed: Workspace " +
						"copyrevert2 is deleted", null));
		
		ws.setWorkspaceDeleted(user2, cp2, false);
		ws.setPermissions(user2, cp2, Arrays.asList(user1), Permission.READ);
		ws.copyObject(user1,  new ObjectIdentifier(cp2, "foo"), new ObjectIdentifier(cp1, "foo")); //should work
		failCopy(user1,  new ObjectIdentifier(cp1, "foo"), new ObjectIdentifier(cp2, "foo"),
				new InaccessibleObjectException("Object foo cannot be accessed: User foo may " +
						"not write to workspace copyrevert2", null));
		failRevert(user1,  new ObjectIdentifier(cp2, "foo", 1),
				new InaccessibleObjectException("Object foo cannot be accessed: User foo may " +
						"not write to workspace copyrevert2", null));
		
		ws.setPermissions(user2, cp2, Arrays.asList(user1), Permission.NONE);
		failCopy(user1,  new ObjectIdentifier(cp2, "foo"), new ObjectIdentifier(cp1, "foo"),
				new InaccessibleObjectException("Object foo cannot be accessed: User foo may " +
						"not read workspace copyrevert2", null));
		
		ws.setPermissions(user2, cp2, Arrays.asList(user1), Permission.WRITE);
		ws.lockWorkspace(user2, cp2);
		failCopy(user1, new ObjectIdentifier(cp1, "orig"),
				new ObjectIdentifier(cp2, "foo2"), new InaccessibleObjectException(
						"Object foo2 cannot be accessed: The workspace with id " + wsid2 +
						", name copyrevert2, is locked and may not be modified",
						null));
		failRevert(user1, new ObjectIdentifier(cp2, "foo1", 1), new InaccessibleObjectException(
				"Object foo1 cannot be accessed: The workspace with id " + wsid2 +
				", name copyrevert2, is locked and may not be modified", null));
	}

	private void checkUnhiddenObjectCount(WorkspaceUser user,
			WorkspaceIdentifier wsi, int unhidden, int all)
			throws Exception {
		ListObjectsParameters lop = new ListObjectsParameters(
				user, Arrays.asList(wsi))
				.withShowAllVersions(true);
		List<ObjectInformation> objs = ws.listObjects(lop);
		assertThat("orig objects hidden", objs.size(), is(unhidden));
		
		lop.withShowHidden(true);
		objs = ws.listObjects(lop);
		assertThat("orig objects hidden", objs.size(), is(all));
	}
	
	@Test
	public void copyReferenceVisibility() throws Exception {
		WorkspaceUser user1 = new WorkspaceUser("foo");
		WorkspaceUser user2 = new WorkspaceUser("foo2");
		WorkspaceIdentifier wsiSource1 = new WorkspaceIdentifier("copyRefVisSource1");
		WorkspaceIdentifier wsiSource2 = new WorkspaceIdentifier("copyRefVisSource2");
		WorkspaceIdentifier wsiCopied = new WorkspaceIdentifier("copyRefVisCopied");
		long wsid1 = ws.createWorkspace(user1, wsiSource1.getName(), false, null, null).getId();
		ws.setPermissions(user1, wsiSource1, Arrays.asList(user2), Permission.READ);
		long wsid2 = ws.createWorkspace(user1, wsiSource2.getName(), false, null, null).getId();
		ws.setPermissions(user1, wsiSource2, Arrays.asList(user2), Permission.READ);
		ws.createWorkspace(user2, wsiCopied.getName(), false, null, null);
		WorkspaceIdentifier wsiCid = new WorkspaceIdentifier(3);
		
		Provenance emptyprov1 = new Provenance(user1);
		Provenance emptyprov2 = new Provenance(user2);
		List<WorkspaceSaveObject> data = new LinkedList<WorkspaceSaveObject>();
		data.add(new WorkspaceSaveObject(getRandomName(), new HashMap<String, Object>(),
				SAFE_TYPE1, null, emptyprov1, false));
		
		ws.saveObjects(user1, wsiSource1, data, new IdReferenceHandlerSetFactory(0));
		ws.saveObjects(user1, wsiSource2, setRandomNames(data),
				new IdReferenceHandlerSetFactory(0));
		final ObjectIdentifier source1 = new ObjectIdentifier(wsiSource1, 1);
		final ObjectIdentifier source2 = new ObjectIdentifier(wsiSource2, 1);
		ObjectIdentifier copied1 = new ObjectIdentifier(wsiCid, "foo");
		ObjectIdentifier copied2 = new ObjectIdentifier(wsiCid, "foo1");
		ws.copyObject(user2, source1, copied1);
		ws.copyObject(user2, source2, copied2);
		copied1 = new ObjectIdentifier(wsiCid, 1, 1);
		copied2 = new ObjectIdentifier(wsiCid, 2, 1);
		
		ws.saveObjects(user2, wsiCopied, setRandomNames(data),
				new IdReferenceHandlerSetFactory(0));
		final ObjectIdentifier nocopy = new ObjectIdentifier(wsiCid, 3, 1);

		data.clear();
		Map<String, Object> ref = new HashMap<String, Object>();
		ref.put("refs", Arrays.asList(wsiCopied.getName() + "/foo"));
		data.add(new WorkspaceSaveObject(getRandomName(), ref, REF_TYPE, null, emptyprov2, false));
		ws.saveObjects(user2, wsiCopied, data, new IdReferenceHandlerSetFactory(1));
		ObjectIDWithRefPath copyoc1 = new ObjectIDWithRefPath(new ObjectIdentifier(wsiCopied, 4L),
				Arrays.asList(copied1));
		
		ref.put("refs", Arrays.asList(wsiCopied.getName() + "/foo1"));
		ws.saveObjects(user2, wsiCopied, setRandomNames(data), new IdReferenceHandlerSetFactory(1));
		ObjectIDWithRefPath copyoc2 = new ObjectIDWithRefPath(new ObjectIdentifier(wsiCopied, 5L),
				Arrays.asList(copied2));
		
		ref.put("refs", Arrays.asList(wsiCopied.getName() + "/3"));
		ws.saveObjects(user2, wsiCopied, setRandomNames(data), new IdReferenceHandlerSetFactory(1));
		ObjectIDWithRefPath nocopyoc = new ObjectIDWithRefPath(new ObjectIdentifier(wsiCopied, 6L),
				Arrays.asList(nocopy));
		
		
		final Reference expectedRef1 = new Reference(wsid1, 1, 1);
		final Reference expectedRef2 = new Reference(wsid2, 1, 1);
		List<ObjectIdentifier> testobjs = Arrays.asList(copied1, nocopy, copied2);
		List<ObjectIdentifier> testocs = new LinkedList<ObjectIdentifier>(
				Arrays.asList(copyoc1, nocopyoc, copyoc2));
		
		List<Reference> refnullref = Arrays.asList(
				expectedRef1, (Reference) null, expectedRef2);
		List<Reference> nullnullref = Arrays.asList(
				(Reference) null, (Reference) null, expectedRef2);
		List<Reference> refnullnull = Arrays.asList(
				expectedRef1, (Reference) null, (Reference) null);
		
		List<Boolean> fff = Arrays.asList(false, false, false);
		List<Boolean> tff = Arrays.asList(true, false, false);
		List<Boolean> fft = Arrays.asList(false, false, true);
		
		checkCopyReference(user2, testobjs, testocs, refnullref, fff);
		
		//check 1st ref
		ws.setPermissions(user1, wsiSource1, Arrays.asList(user2), Permission.NONE);
		checkCopyReference(user2, testobjs, testocs, nullnullref, tff);
		ws.setPermissions(user1, wsiSource1, Arrays.asList(user2), Permission.READ);
		checkCopyReference(user2, testobjs, testocs, refnullref, fff);
		
		ws.setObjectsDeleted(user1, Arrays.asList(source1), true);
		checkCopyReference(user2, testobjs, testocs, nullnullref, tff);
		ws.setObjectsDeleted(user1, Arrays.asList(source1), false);
		checkCopyReference(user2, testobjs, testocs, refnullref, fff);
		
		ws.setWorkspaceDeleted(user1, wsiSource1, true);
		checkCopyReference(user2, testobjs, testocs, nullnullref, tff);
		ws.setWorkspaceDeleted(user1, wsiSource1, false);
		checkCopyReference(user2, testobjs, testocs, refnullref, fff);
		
		//check 2nd ref
		ws.setPermissions(user1, wsiSource2, Arrays.asList(user2), Permission.NONE);
		checkCopyReference(user2, testobjs, testocs, refnullnull, fft);
		ws.setPermissions(user1, wsiSource2, Arrays.asList(user2), Permission.READ);
		checkCopyReference(user2, testobjs, testocs, refnullref, fff);
		
		ws.setObjectsDeleted(user1, Arrays.asList(source2), true);
		checkCopyReference(user2, testobjs, testocs, refnullnull, fft);
		ws.setObjectsDeleted(user1, Arrays.asList(source2), false);
		checkCopyReference(user2, testobjs, testocs, refnullref, fff);
		
		ws.setWorkspaceDeleted(user1, wsiSource2, true);
		checkCopyReference(user2, testobjs, testocs, refnullnull, fft);
		ws.setWorkspaceDeleted(user1, wsiSource2, false);
		checkCopyReference(user2, testobjs, testocs, refnullref, fff);
	}

	private void checkCopyReference(
			final WorkspaceUser user,
			final List<ObjectIdentifier> testobjs,
			final List<ObjectIdentifier> testocs,
			final List<Reference> testRef,
			final List<Boolean> copyAccessible)
			throws Exception {
		
		List<List<WorkspaceObjectData>> infos =
				new LinkedList<List<WorkspaceObjectData>>();
		
		infos.add(ws.getObjects(user, testobjs, true));
		final List<WorkspaceObjectData> objects =
				ws.getObjects(user, testobjs);
		destroyGetObjectsResources(objects); // don't need the data
		infos.add(objects);
		final List<WorkspaceObjectData> objects2 =
				ws.getObjects(user, testocs);
		destroyGetObjectsResources(objects2); // ditto
		infos.add(objects2);
		
		for (List<WorkspaceObjectData> info: infos) {
			for (int i = 0; i < info.size(); i++) {
				WorkspaceObjectData inf = info.get(i);
				assertThat("correct reference ", inf.getCopyReference() == null ? null :
					inf.getCopyReference(), is(testRef.get(i)));
				assertThat("correct inaccessibility", inf.isCopySourceInaccessible(),
						is(copyAccessible.get(i)));
			}
		}
	}
	
	@Test
	public void cloneWithExclude() throws Exception {
		WorkspaceUser user = new WorkspaceUser("user1");
		WorkspaceIdentifier source = new WorkspaceIdentifier("source");
		ws.createWorkspace(user, source.getName(), false, null, null).getId();
		List<WorkspaceSaveObject> objects = new LinkedList<WorkspaceSaveObject>();
		Provenance p = new Provenance(user);
		final HashMap<String, String> mt = new HashMap<String, String>();
		objects.add(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("object1"), mt, SAFE_TYPE1, null, p, false));
		objects.add(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("object2"), mt, SAFE_TYPE1, null, p, false));
		objects.add(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("object3"), mt, SAFE_TYPE1, null, p, false));
		ws.saveObjects(user, source, objects, getIdFactory());
		
		ws.cloneWorkspace(user, source, "new1", false, null, null, null);
		checkWsObjectNames(user, "new1", "object1", "object2", "object3");
		
		ws.cloneWorkspace(user, source, "new2", false, null, null,
				new HashSet<ObjectIDNoWSNoVer>());
		checkWsObjectNames(user, "new2", "object1", "object2", "object3");
		
		Set<ObjectIDNoWSNoVer> exclude = new HashSet<ObjectIDNoWSNoVer>();
		
		exclude.add(new ObjectIDNoWSNoVer("object2"));
		ws.cloneWorkspace(user, source, "new3", false, null, null, exclude);
		checkWsObjectNames(user, "new3", "object1", "object3");
		
		exclude.add(new ObjectIDNoWSNoVer(1));
		ws.cloneWorkspace(user, source, "new4", false, null, null, exclude);
		checkWsObjectNames(user, "new4", "object3");
		
		exclude.remove(new ObjectIDNoWSNoVer("object2"));
		ws.cloneWorkspace(user, source, "new5", false, null, null, exclude);
		checkWsObjectNames(user, "new5", "object2", "object3");
		
		exclude.clear();
		exclude.add(new ObjectIDNoWSNoVer("object4"));
		failClone(user, source, "foo", null, exclude,
				new NoSuchObjectException(
						"No object with name object4 exists in workspace 1 (name source)", null));
		
		ws.setObjectsDeleted(user, Arrays.asList(
				new ObjectIdentifier(source, "object3")), true);
		exclude.clear();
		exclude.add(new ObjectIDNoWSNoVer("object3"));
		failClone(user, source, "foo", null, exclude, new DeletedObjectException(
				"Object 3 (name object3) in workspace 1 (name source) has been deleted", null));
	}
	
	private void checkWsObjectNames(WorkspaceUser user, String wsName,
			String... objnames) throws Exception {
		List<ObjectInformation> objs = ws.listObjects(
				new ListObjectsParameters(user,
				Arrays.asList(new WorkspaceIdentifier(wsName))));
		Set<String> expected = new HashSet<String>(Arrays.asList(objnames));
		Set<String> got = new HashSet<String>();
		for (ObjectInformation o: objs) {
			got.add(o.getObjectName());
		}
		assertThat("Got incorrect list of objects", got, is(expected));
	}
	
	@Test
	public void clonePreserveIDs() throws Exception {
		/* test that cloning a workspace preserves the object IDs of the old
		 * workspace. Issues - mongo return order, deleted objects, excluded
		 * objects
		 */
		WorkspaceUser user = new WorkspaceUser("foo");
		WorkspaceIdentifier source = new WorkspaceIdentifier("source");
		Map<String, String> mt = new HashMap<String, String>();
		Provenance p = new Provenance(user);
		
		ws.createWorkspace(user, source.getName(), false, null, null);
		List<WorkspaceSaveObject> objects = Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("o1"), mt,
						SAFE_TYPE1, null, p, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("o2"), mt,
						SAFE_TYPE1, null, p, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("o3"), mt,
						SAFE_TYPE1, null, p, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("o4"), mt,
						SAFE_TYPE1, null, p, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("o5"), mt,
						SAFE_TYPE1, null, p, false)
				);
		
		ws.saveObjects(user, source, objects, getIdFactory());
		
		//delete an object, should not get cloned and id should not be reused
		ws.setObjectsDeleted(user, Arrays.asList(
				new ObjectIdentifier(source, 2)), true);
		
		//set the touch order to 5 3 1 4 (going to exclude 4)
		//this doesn't guarantee the mongo order will change, but it's the
		//best that can be done
		ws.saveObjects(user, source, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer(1), mt,
						SAFE_TYPE1, null, p, false)), getIdFactory());
		ws.saveObjects(user, source, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer(3), mt,
						SAFE_TYPE1, null, p, false)), getIdFactory());
		ws.saveObjects(user, source, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer(5), mt,
						SAFE_TYPE1, null, p, false)), getIdFactory());
		
		//clone
		WorkspaceIdentifier target = new WorkspaceIdentifier("target");
		ws.cloneWorkspace(user, source, target.getName(), false, null, null,
				new HashSet<>(Arrays.asList(new ObjectIDNoWSNoVer(4))));
		
		//check ids are preserved
		List<ObjectInformation> ol = ws.listObjects(new ListObjectsParameters(
				user, Arrays.asList(target)));
		Set<Long> seen = new HashSet<>();
		
		for (ObjectInformation oi: ol) {
			seen.add(oi.getObjectId());
			assertThat("didn't preserve id", "o" + oi.getObjectId(),
					is(oi.getObjectName()));
		}
		assertThat("incorrect object list", seen,
				is((Set<Long>) new HashSet<>(Arrays.asList(1L, 3L, 5L))));
		failGetObjects(user, Arrays.asList(new ObjectIdentifier(target, 2)),
				new NoSuchObjectException(
						"No object with id 2 exists in workspace 2 (name target)", null));
		failGetObjects(user, Arrays.asList(new ObjectIdentifier(target, 4)),
				new NoSuchObjectException(
						"No object with id 4 exists in workspace 2 (name target)", null));
	}

	@Test
	public void cloneEmpty() throws Exception {
		// test cloning an empty workspace
		WorkspaceUser user = new WorkspaceUser("foo");
		WorkspaceIdentifier source = new WorkspaceIdentifier("source");
		Provenance p = new Provenance(user);
		Map<String, String> mt = new HashMap<String, String>();
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("foo", "bar");
		
		ws.createWorkspace(user, source.getName(), true, "desc",
				new WorkspaceUserMetadata(meta));
		WorkspaceIdentifier target = new WorkspaceIdentifier("target");
		WorkspaceInformation i = ws.cloneWorkspace(user, source, 
				target.getName(), false, null, null, null);
		checkWSInfo(target, user, target.getName(), 0L, Permission.OWNER,
				false, 2, i.getModDate(), "unlocked", mt);
		assertThat("incorrect description",
				ws.getWorkspaceDescription(user, target), is((String) null));
		
		//test excluding and deleted objects
		List<WorkspaceSaveObject> objects = Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("o1"), mt,
						SAFE_TYPE1, null, p, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("o2"), mt,
						SAFE_TYPE1, null, p, false)
				);
		ws.saveObjects(user, source, objects, getIdFactory());
		ws.setObjectsDeleted(user, Arrays.asList(
				new ObjectIdentifier(source, 2)), true);
		
		WorkspaceIdentifier target2 = new WorkspaceIdentifier("target2");
		WorkspaceInformation i2 = ws.cloneWorkspace(user, source,
				target2.getName(), true, null, null,
				new HashSet<>(Arrays.asList(new ObjectIDNoWSNoVer(1))));
		checkWSInfo(target2, user, target2.getName(), 0L, Permission.OWNER,
				true, 3, i2.getModDate(), "unlocked", mt);
		assertThat("incorrect description",
				ws.getWorkspaceDescription(user, target2), is((String) null));
	}
	
	@Test
	public void cloneCopySave() throws Exception {
		/* Test copying and saving into a cloned workspace. If the cloning
		 * code is broken, stack overflows can occur.
		 */
		
		WorkspaceUser user = new WorkspaceUser("foo");
		WorkspaceIdentifier source = new WorkspaceIdentifier("source");
		Map<String, String> mt = new HashMap<String, String>();
		Provenance p = new Provenance(user);
		
		ws.createWorkspace(user, source.getName(), false, null, null);
		List<WorkspaceSaveObject> objects = Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("o1"), mt,
						SAFE_TYPE1, null, p, false)
				);
		
		ws.saveObjects(user, source, objects, getIdFactory());
		
		//clone
		WorkspaceIdentifier target = new WorkspaceIdentifier("target");
		ws.cloneWorkspace(
				user, source, target.getName(), false, null, null, null);
		
		ObjectInformation oi = ws.saveObjects(user, target, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("bar"), mt,
						SAFE_TYPE1, null, p, false)),
				getIdFactory()).get(0);
		checkObjInfo(oi, 2L, "bar", SAFE_TYPE1.getTypeString(), 1, user, 2, "target",
				"99914b932bd37a50b983c5e7c90ae93b", 2, mt,
				Arrays.asList(new Reference(2, 2, 1)));
		ObjectInformation oi2 = ws.copyObject(user,
				new ObjectIdentifier(source, "o1"),
				new ObjectIdentifier(target, "foo"));
		checkObjInfo(oi2, 3L, "foo", SAFE_TYPE1.getTypeString(), 1, user, 2, "target",
				"99914b932bd37a50b983c5e7c90ae93b", 2, mt,
				Arrays.asList(new Reference(2, 3, 1)));
		
		WorkspaceInformation i = ws.getWorkspaceInformation(user, target);
		checkWSInfo(target, user, target.getName(), 3L, Permission.OWNER,
				false, 2, i.getModDate(), "unlocked", mt);
	}
	
	@Test
	public void cloneWorkspace() throws Exception {
		WorkspaceUser user1 = new WorkspaceUser("foo");
		WorkspaceUser user2 = new WorkspaceUser("bar");
		
		String wsrefs = "clonerefs";
		String ws1 = "clone1";
		setUpCopyWorkspaces(user1, user2, wsrefs, ws1, "cloneunused");
		WorkspaceIdentifier cp1 = new WorkspaceIdentifier(ws1);
		WorkspaceIdentifier clone1 = new WorkspaceIdentifier("newclone");
		
		Map<String, String> premeta = new HashMap<String, String>();
		premeta.put("clone", "workspace");
		WorkspaceUserMetadata meta = new WorkspaceUserMetadata(premeta);
		
		WorkspaceInformation info1 = ws.cloneWorkspace(
				user1, cp1, clone1.getName(), false, null, meta, null);
		
		checkWSInfo(clone1, user1, "newclone", 3, Permission.OWNER, false, info1.getId(),
				info1.getModDate(), "unlocked", premeta);
		assertNull("desc ok", ws.getWorkspaceDescription(user1, clone1));
		
		List<ObjectInformation> objs = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "hide"));
		ObjectInformation save11 = objs.get(0);
		ObjectInformation save12 = objs.get(1);
		ObjectInformation save13 = objs.get(2);
		
		objs = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "orig"));
		ObjectInformation save21 = objs.get(0);
		ObjectInformation save22 = objs.get(1);
		ObjectInformation save23 = objs.get(2);
		
		objs = ws.getObjectHistory(user1, new ObjectIdentifier(cp1, "hidetarget"));
		ObjectInformation save31 = objs.get(0);
		
		List<ObjectInformation> hideobjs = ws.getObjectHistory(user1, new ObjectIdentifier(clone1, "hide"));
		long id = hideobjs.get(0).getObjectId();
		compareObjectAndInfo(save11, hideobjs.get(0), user1, info1.getId(), clone1.getName(), id, "hide", 1);
		compareObjectAndInfo(save12, hideobjs.get(1), user1, info1.getId(), clone1.getName(), id, "hide", 2);
		compareObjectAndInfo(save13, hideobjs.get(2), user1, info1.getId(), clone1.getName(), id, "hide", 3);
		
		List<ObjectInformation> origobjs = ws.getObjectHistory(user1, new ObjectIdentifier(clone1, "orig"));
		id = origobjs.get(0).getObjectId();
		compareObjectAndInfo(save21, origobjs.get(0), user1, info1.getId(), clone1.getName(), id, "orig", 1);
		compareObjectAndInfo(save22, origobjs.get(1), user1, info1.getId(), clone1.getName(), id, "orig", 2);
		compareObjectAndInfo(save23, origobjs.get(2), user1, info1.getId(), clone1.getName(), id, "orig", 3);
		
		List<ObjectInformation> hidetarget = ws.getObjectHistory(user1, new ObjectIdentifier(clone1, "hidetarget"));
		id = hidetarget.get(0).getObjectId();
		compareObjectAndInfo(save31, hidetarget.get(0), user1, info1.getId(), clone1.getName(), id, "hidetarget", 1);
		checkUnhiddenObjectCount(user1, clone1, 3, 7);
		
		
		ws.setObjectsDeleted(user1, Arrays.asList(new ObjectIdentifier(cp1, "hide")), true);
		
		WorkspaceIdentifier clone2 = new WorkspaceIdentifier("newclone2");
		WorkspaceInformation info2 = ws.cloneWorkspace(
				user1, cp1, clone2.getName(), true, "my desc", null, null);
		
		checkWSInfo(clone2, user1, "newclone2", 3, Permission.OWNER, true, info2.getId(),
				info2.getModDate(), "unlocked", MT_MAP);
		assertThat("desc ok", ws.getWorkspaceDescription(user1, clone2), is("my desc"));
		
		origobjs = ws.getObjectHistory(user1, new ObjectIdentifier(clone2, "orig"));
		id = origobjs.get(0).getObjectId();
		compareObjectAndInfo(save21, origobjs.get(0), user1, info2.getId(), clone2.getName(), id, "orig", 1);
		compareObjectAndInfo(save22, origobjs.get(1), user1, info2.getId(), clone2.getName(), id, "orig", 2);
		compareObjectAndInfo(save23, origobjs.get(2), user1, info2.getId(), clone2.getName(), id, "orig", 3);
		
		hidetarget = ws.getObjectHistory(user1, new ObjectIdentifier(clone2, "hidetarget"));
		id = hidetarget.get(0).getObjectId();
		compareObjectAndInfo(save31, hidetarget.get(0), user1, info2.getId(), clone2.getName(), id, "hidetarget", 1);
		checkUnhiddenObjectCount(user1, clone2, 3, 4);
		
		ws.setWorkspaceDeleted(user1, cp1, true);
		failClone(user1, cp1, "fakename", null, new NoSuchWorkspaceException("Workspace clone1 is deleted", cp1));
		ws.setWorkspaceDeleted(user1, cp1, false);
		ws.setObjectsDeleted(user1, Arrays.asList(new ObjectIdentifier(cp1, "hide")), true);
		
		failClone(null, cp1, "fakename", null, new WorkspaceAuthorizationException("Anonymous users may not read workspace clone1"));
		failClone(user1, null, "fakename", null,
				new NullPointerException("Workspace identifier cannot be null"));
		//workspaceIdentifier used in the workspace method to check ws names tested extensively elsewhere, so just
		// a couple tests here
		failClone(user1, cp1, "bar:fakename", null, new IllegalArgumentException(
				"Workspace name bar:fakename must only contain the user name foo prior to the : delimiter"));
		failClone(user1, cp1, "9", null, new IllegalArgumentException(
				"Workspace names cannot be integers: 9"));
		failClone(user1, cp1, "foo:9", null, new IllegalArgumentException(
				"Workspace names cannot be integers: foo:9"));
		//TODO LATER readd this test when ws names are fixed so user:int is not allowed
//		failClone(user1, cp1, "foo:45678901234567890123", null, new IllegalArgumentException(
//				"Workspace names cannot be integers: foo:45678901234567890123"));
		failClone(user1, cp1, "foo:fake(name", null, new IllegalArgumentException(
				"Illegal character in workspace name foo:fake(name: ("));
		failClone(user2, cp1, "fakename", null, new WorkspaceAuthorizationException("User bar may not read workspace clone1"));
		failClone(user1, cp1, "newclone2", null, new PreExistingWorkspaceException(
				"Workspace name newclone2 is already in use"));
		failClone(user1, new WorkspaceIdentifier("noclone"), "fakename", null,
				new NoSuchWorkspaceException("No workspace with name noclone exists", cp1));
		
		ws.lockWorkspace(user1, cp1);
		
		WorkspaceIdentifier clone3 = new WorkspaceIdentifier("newclone3");
		WorkspaceInformation info3 = ws.cloneWorkspace(
				user1, cp1, clone3.getName(), false, "my desc2", meta, null);
		
		checkWSInfo(clone3, user1, "newclone3", 3, Permission.OWNER, false, info3.getId(),
				info3.getModDate(), "unlocked", premeta);
		assertThat("desc ok", ws.getWorkspaceDescription(user1, clone3), is("my desc2"));
		
		origobjs = ws.getObjectHistory(user1, new ObjectIdentifier(clone3, "orig"));
		id = origobjs.get(0).getObjectId();
		compareObjectAndInfo(save21, origobjs.get(0), user1, info3.getId(), clone3.getName(), id, "orig", 1);
		compareObjectAndInfo(save22, origobjs.get(1), user1, info3.getId(), clone3.getName(), id, "orig", 2);
		compareObjectAndInfo(save23, origobjs.get(2), user1, info3.getId(), clone3.getName(), id, "orig", 3);
		
		hidetarget = ws.getObjectHistory(user1, new ObjectIdentifier(clone3, "hidetarget"));
		id = hidetarget.get(0).getObjectId();
		compareObjectAndInfo(save31, hidetarget.get(0), user1, info3.getId(), clone3.getName(), id, "hidetarget", 1);
		checkUnhiddenObjectCount(user1, clone3, 3, 4);
		
		WorkspaceIdentifier clone4 = new WorkspaceIdentifier("newclone4");
		ws.cloneWorkspace(
				user1, cp1, clone4.getName(), true, LONG_TEXT, null, null);
		assertThat("desc ok", ws.getWorkspaceDescription(user1, clone4), is(LONG_TEXT.subSequence(0, 1000)));
		
		ws.setGlobalPermission(user1, clone2, Permission.NONE);
		ws.setGlobalPermission(user1, clone4, Permission.NONE);
	}

	@Test
	public void lockWorkspace() throws Exception {
		WorkspaceUser user = new WorkspaceUser("lockuser");
		WorkspaceUser user2 = new WorkspaceUser("lockuser2");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("lock");
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("some meta", "for u");
		long wsid = ws.createWorkspace(user, wsi.getName(), false, null,
				new WorkspaceUserMetadata(meta)).getId();
		ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto1"), new HashMap<String, String>(), SAFE_TYPE1,
				new WorkspaceUserMetadata(), new Provenance(user), false)), getIdFactory());
		ObjectIdentifier oi = new ObjectIdentifier(wsi, "auto1");
		//these should work
		WorkspaceInformation info = ws.lockWorkspace(user, wsi);
		checkWSInfo(info, user, "lock", 1, Permission.OWNER, false, "locked", meta);
		successGetObjects(user, Arrays.asList(oi));
		ws.cloneWorkspace(user, wsi, "lockclone", false, null, null, null);
		ws.copyObject(user, oi, new ObjectIdentifier(new WorkspaceIdentifier("lockclone"), "foo"));
		ws.setPermissions(user, wsi, Arrays.asList(user2), Permission.WRITE);
		ws.setPermissions(user, wsi, Arrays.asList(user2), Permission.NONE);
		ws.getPermissions(user, Arrays.asList(wsi));
		ws.getWorkspaceDescription(user, wsi);
		ws.getWorkspaceInformation(user, wsi);
		ws.listObjects(new ListObjectsParameters(user, Arrays.asList(wsi)));
		
		//these should not work
		try {
			ws.lockWorkspace(user, new WorkspaceIdentifier("nolock"));
			fail("locked non existant ws");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("No workspace with name nolock exists"));
		}
		ws.createWorkspace(user, "lock2", false, "foo", null);
		WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("lock2");
		try {
			ws.lockWorkspace(null, wsi2);
			fail("locked w/o creds");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("Anonymous users may not lock workspace lock2"));
		}
		try {
			ws.lockWorkspace(user2, wsi2);
			fail("locked w/o creds");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("User lockuser2 may not lock workspace lock2"));
		}
		ws.setWorkspaceDeleted(user, wsi2, true);
		try {
			ws.lockWorkspace(user, wsi2);
			fail("locked deleted ws");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("Workspace lock2 is deleted"));
		}
		try {
			ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(getRandomName(),
					new HashMap<String, String>(), SAFE_TYPE1,
					new WorkspaceUserMetadata(),
					new Provenance(user), false)), getIdFactory());
			fail("saved to locked workspace");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.copyObject(user, oi, new ObjectIdentifier(wsi, "foo"));
			fail("copied to locked workspace");
		} catch (InaccessibleObjectException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("Object foo cannot be accessed: The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.revertObject(user, oi);
			fail("revert to locked workspace");
		} catch (InaccessibleObjectException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("Object auto1 cannot be accessed: The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.lockWorkspace(user, wsi);
			fail("locked locked workspace");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.renameObject(user, oi, "boo");
			fail("renamed locked workspace obj");
		} catch (InaccessibleObjectException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("Object auto1 cannot be accessed: The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.renameWorkspace(user, wsi, "foo");
			fail("renamed locked workspace obj");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.setObjectsDeleted(user, Arrays.asList(oi), true);
			fail("deleted locked workspace obj");
		} catch (InaccessibleObjectException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("Object auto1 cannot be accessed: The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.setObjectsHidden(user, Arrays.asList(oi), true);
			fail("hid locked workspace obj");
		} catch (InaccessibleObjectException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("Object auto1 cannot be accessed: The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.setWorkspaceDeleted(user, wsi, true);
			fail("deleted locked workspace");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		try {
			ws.setWorkspaceDescription(user, wsi, "wugga");
			fail("set desc on locked ws");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		
		try {
			ws.getWorkspaceDescription(user2, wsi);
			fail("bad access to locked workspace");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("User lockuser2 may not read workspace lock"));
		}
		failWSMeta(user, wsi, "some meta", "val", new WorkspaceAuthorizationException(
				"The workspace with id " + wsid +
				", name lock, is locked and may not be modified"));
		
		//should work
		ws.setGlobalPermission(user, wsi, Permission.READ);
		checkWSInfo(ws.getWorkspaceInformation(user, wsi),
				user, "lock", 1, Permission.OWNER, true, "published", meta);
		checkWSInfo(ws.getWorkspaceInformation(user2, wsi),
				user, "lock", 1, Permission.NONE, true, "published", meta);
		ws.getWorkspaceDescription(user2, wsi);
		
		//shouldn't
		try {
			ws.setGlobalPermission(user, wsi, Permission.NONE);
			fail("bad access to locked workspace");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception", e.getLocalizedMessage(),
					is("The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
	}
	
	@Test
	public void renameObject() throws Exception {
		WorkspaceUser user = new WorkspaceUser("renameObjUser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("renameObj");
		WorkspaceUser user2 = new WorkspaceUser("renameObjUser2");
		WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("renameObj2");
		WorkspaceInformation info1 = ws.createWorkspace(user, wsi.getName(), false, null, null);
		long wsid1 = info1.getId();
		Instant lastWSDate = info1.getModDate();
		ws.createWorkspace(user2, wsi2.getName(), false, null, null);
		ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto1"), new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), false)), getIdFactory());
		ws.saveObjects(user2, wsi2, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto2"), new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), false)), getIdFactory());
		lastWSDate = ws.getWorkspaceInformation(user, wsi).getModDate();
		ObjectInformation info = ws.renameObject(user, new ObjectIdentifier(wsi, "auto1"),
				"mynewname");
		assertWorkspaceDateUpdated(user, wsi, lastWSDate, "ws date updated on rename");
		checkObjInfo(info, 1L, "mynewname", SAFE_TYPE1.getTypeString(), 1, user, wsid1,
				"renameObj", "99914b932bd37a50b983c5e7c90ae93b", 2, null,
				Arrays.asList(new Reference(wsid1, 1, 1)));
		String newname = ws.listObjects(new ListObjectsParameters(user, Arrays.asList(wsi)))
				.get(0).getObjectName();
		assertThat("object renamed", newname, is("mynewname"));
		
		ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("myoldname"), new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), false)), getIdFactory());
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "bad%name", new IllegalArgumentException(
				"Illegal character in object name bad%name: %"));
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "2", new IllegalArgumentException(
				"Object names cannot be integers: 2"));
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "12345678901234567890",
				new IllegalArgumentException(
						"Object names cannot be integers: 12345678901234567890"));
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "myoldname", new IllegalArgumentException(
				"There is already an object in the workspace named myoldname"));
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "mynewname", new IllegalArgumentException(
				"Object is already named mynewname"));
		failObjRename(user, new ObjectIdentifier(wsi, "bar"), "foo", new NoSuchObjectException(
				"No object with name bar exists in workspace 1 (name renameObj)", null));
		failObjRename(user, new ObjectIdentifier(wsi2, "auto1"), "foo",
				new InaccessibleObjectException("Object auto1 cannot be accessed: User " +
						"renameObjUser may not rename objects in workspace renameObj2",
						null));
		failObjRename(null, new ObjectIdentifier(wsi2, "auto1"), "foo",
				new InaccessibleObjectException("Object auto1 cannot be accessed: Anonymous " +
						"users may not rename objects in workspace renameObj2",
						null));
		
		ws.setObjectsDeleted(user, Arrays.asList(new ObjectIdentifier(wsi, "mynewname")), true);
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "foo",
				new NoSuchObjectException("Object 1 (name mynewname) in workspace 1 " +
						"(name renameObj) has been deleted", null));
		ws.setWorkspaceDeleted(user, wsi, true);
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "foo",
				new InaccessibleObjectException("Object mynewname cannot be accessed: " +
						"Workspace renameObj is deleted", null));
		ws.setWorkspaceDeleted(user, wsi, false);
		failObjRename(user, new ObjectIdentifier(
				new WorkspaceIdentifier("renameObjfake"), "mynewname"), "foo",
				new InaccessibleObjectException("Object mynewname cannot be accessed: No " +
						"workspace with name renameObjfake exists", null));
		ws.lockWorkspace(user, wsi);
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "foo",
				new InaccessibleObjectException("Object mynewname cannot be accessed: The " +
						"workspace with id " + wsid1 + ", name renameObj, is locked and may " +
						"not be modified", null));
	}

	@Test
	public void renameWorkspace() throws Exception {
		WorkspaceUser user = new WorkspaceUser("renameWSUser");
		WorkspaceUser user2 = new WorkspaceUser("renameWSUser2");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("renameWS");
		WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("renameWS2");
		
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("?", "42");
		meta.put("Panic", "towel");
		WorkspaceInformation info1 = ws.createWorkspace(user, wsi.getName(),
				false, null, new WorkspaceUserMetadata(meta));
		WorkspaceIdentifier newwsi = new WorkspaceIdentifier(user.getUser() + ":newRenameWS");
		Thread.sleep(2); //make sure timestamp is different on rename
		WorkspaceInformation info2 = ws.renameWorkspace(user, wsi, newwsi.getName());
		checkWSInfo(info2, user, newwsi.getName(), 0, Permission.OWNER, false, "unlocked", meta);
		assertTrue("date updated on ws rename", info2.getModDate().isAfter(info1.getModDate()));
		checkWSInfo(ws.getWorkspaceInformation(user, newwsi),
				user, newwsi.getName(), 0, Permission.OWNER, false, "unlocked", meta);
		
		failWSRename(user, newwsi, "foo|bar",
				new IllegalArgumentException("Illegal character in workspace name foo|bar: |"));
		failWSRename(user, newwsi, "renameWSUser:9",
				new IllegalArgumentException("Workspace names cannot be integers: renameWSUser:9"));
		failWSRename(user, newwsi, "9",
				new IllegalArgumentException("Workspace names cannot be integers: 9"));
		failWSRename(user, newwsi, "90123456789012345678", new IllegalArgumentException(
				"Workspace names cannot be integers: 90123456789012345678"));
		failWSRename(user, newwsi, "foo:foobar",
				new IllegalArgumentException(
						"Workspace name foo:foobar must only contain the user name renameWSUser prior to the : delimiter"));
		
		ws.createWorkspace(user2, wsi2.getName(), false, null, null);
		ws.setPermissions(user2, wsi2, Arrays.asList(user), Permission.WRITE);
		failWSRename(user, newwsi, "renameWS2",
				new IllegalArgumentException("There is already a workspace named renameWS2"));
		failWSRename(user, newwsi, newwsi.getName(),
				new IllegalArgumentException("Workspace is already named renameWSUser:newRenameWS"));
		failWSRename(user, new WorkspaceIdentifier(newwsi.getName() + "a"), newwsi.getName(),
				new NoSuchWorkspaceException("No workspace with name renameWSUser:newRenameWSa exists", wsi));
		failWSRename(user, wsi2, newwsi.getName(),
				new WorkspaceAuthorizationException("User renameWSUser may not rename workspace renameWS2"));
		failWSRename(null, newwsi, "renamefoo",
				new WorkspaceAuthorizationException("Anonymous users may not rename workspace renameWSUser:newRenameWS"));
		ws.setWorkspaceDeleted(user, newwsi, true);
		failWSRename(user, newwsi, "renamefoo",
				new NoSuchWorkspaceException("Workspace " + newwsi.getName() + " is deleted", newwsi));
		ws.setWorkspaceDeleted(user, newwsi, false);
		ws.lockWorkspace(user, newwsi);
		failWSRename(user, newwsi, "renamefoo",
				new WorkspaceAuthorizationException("The workspace with id " + info1.getId() +
						", name " + newwsi.getName() + ", is locked and may not be modified"));
		
	}

	@Test
	public void setGlobalRead() throws Exception {
		WorkspaceUser user = new WorkspaceUser("setGlobalUser");
		WorkspaceUser user2 = new WorkspaceUser("setGlobalUser2");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("global");
		long wsid = ws.createWorkspace(user, wsi.getName(), false, null, null).getId();
		
		failGetWorkspaceDesc(user2, wsi, new WorkspaceAuthorizationException(
				"User setGlobalUser2 may not read workspace global"));
		final long id = ws.setGlobalPermission(user, wsi, Permission.READ);
		assertThat("incorrect returned id", id, is(1L));
		assertThat("read set correctly", ws.getPermissions(user,
				Arrays.asList(wsi)).get(0).get(new AllUsers('*')),
				is(Permission.READ));
		ws.getWorkspaceDescription(user2, wsi);
		failSetGlobalPerm(user, null, Permission.READ, new IllegalArgumentException(
				"Workspace identifier cannot be null"));
		failSetGlobalPerm(user, wsi, Permission.WRITE, new IllegalArgumentException(
				"Global permissions cannot be greater than read"));
		failSetGlobalPerm(user2, wsi, Permission.NONE, new WorkspaceAuthorizationException(
				"User setGlobalUser2 may not set global permission on workspace global"));
		failSetGlobalPerm(null, wsi, Permission.NONE, new WorkspaceAuthorizationException(
				"Anonymous users may not set global permission on workspace global"));
		ws.setWorkspaceDeleted(user, wsi, true);
		failSetGlobalPerm(user, wsi, Permission.NONE, new NoSuchWorkspaceException(
				"Workspace global is deleted", wsi));
		ws.setWorkspaceDeleted(user, wsi, false);
		
		final long id2 = ws.setGlobalPermission(user, wsi, Permission.NONE);
		assertThat("incorrect returned id", id2, is(1L));
		
		ws.lockWorkspace(user, wsi);
		failSetGlobalPerm(user, wsi, Permission.NONE, new WorkspaceAuthorizationException(
				"The workspace with id " + wsid + ", name global, is locked and may not be modified"));
		
		//this is tested in lockWorkspace
//		ws.setGlobalPermission(user, wsi, Permission.READ);
//		assertThat("read set correctly on locked ws", ws.getPermissions(user, wsi).get(new AllUsers('*')),
//			is(Permission.READ));
	}
	
	@Test
	public void hiddenObjects() throws Exception {
		WorkspaceUser user = new WorkspaceUser("hideObjUser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("hideObj");
		WorkspaceUser user2 = new WorkspaceUser("hideObjUser2");
		long wsid1 = ws.createWorkspace(user, wsi.getName(), false, null, null).getId();
		ObjectInformation auto1 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto1"), new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), false)), getIdFactory()).get(0);
		ObjectInformation auto2 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto2"), new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), true)), getIdFactory()).get(0);
		ObjectInformation obj1 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("obj1"), new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), true)), getIdFactory()).get(0);
		
		List<ObjectInformation> expected = new ArrayList<ObjectInformation>();
		expected.add(auto1);
		ListObjectsParameters lop = new ListObjectsParameters(user, Arrays.asList(wsi))
				.withIncludeMetaData(true);
		compareObjectInfo(ws.listObjects(lop), expected);
		
		expected.add(auto2);
		expected.add(obj1);
		compareObjectInfo(ws.listObjects(lop.withShowHidden(true)), expected);
		
		ws.setObjectsHidden(user, Arrays.asList(new ObjectIdentifier(wsi, 3), new ObjectIdentifier(wsi, "auto2")), false);
		compareObjectInfo(ws.listObjects(lop.withShowHidden(false)), expected);
		
		ws.setObjectsHidden(user, Arrays.asList(new ObjectIdentifier(wsi, 1), new ObjectIdentifier(wsi, "obj1")), true);
		expected.remove(auto1);
		expected.remove(obj1);
		compareObjectInfo(ws.listObjects(lop), expected);
		
		failSetHide(user, new ObjectIdentifier(wsi, "fake"), true, new NoSuchObjectException(
				"No object with name fake exists in workspace 1 (name hideObj)", null));
		failSetHide(user, new ObjectIdentifier(new WorkspaceIdentifier("fake"), "fake"), true,
				new InaccessibleObjectException("Object fake cannot be accessed: No workspace " +
						"with name fake exists", null));
		
		failSetHide(user2, new ObjectIdentifier(wsi, "auto1"), true,
				new InaccessibleObjectException("Object auto1 cannot be accessed: User " +
						"hideObjUser2 may not hide objects from workspace hideObj",
						null));
		failSetHide(null, new ObjectIdentifier(wsi, "auto1"), true,
				new InaccessibleObjectException("Object auto1 cannot be accessed: Anonymous " +
						"users may not hide objects from workspace hideObj",
						null));
		
		ws.setObjectsDeleted(user, Arrays.asList(new ObjectIdentifier(wsi, 3)), true);
		failSetHide(user, new ObjectIdentifier(wsi, 3), true, new NoSuchObjectException(
				"Object 3 (name obj1) in workspace 1 (name hideObj) has been deleted", null));
		ws.setObjectsDeleted(user, Arrays.asList(new ObjectIdentifier(wsi, 3)), false);
		
		ws.setWorkspaceDeleted(user, wsi, true);
		failSetHide(user, new ObjectIdentifier(new WorkspaceIdentifier("fake"), "fake"), true,
				new InaccessibleObjectException("Object fake cannot be accessed: No workspace " +
						"with name fake exists", null));
		ws.setWorkspaceDeleted(user, wsi, false);
		
		ws.lockWorkspace(user, wsi);
		failSetHide(user, new ObjectIdentifier(wsi, 3), true, new InaccessibleObjectException(
				"Object 3 cannot be accessed: The workspace with id " + wsid1 +
				", name hideObj, is locked and may not be modified", null));
	}

	@Test
	public void listWorkspaces() throws Exception {
		WorkspaceUser user = new WorkspaceUser("listUser");
		WorkspaceUser user2 = new WorkspaceUser("listUser2");
		WorkspaceUser user3 = new WorkspaceUser("listUser3");
		
		Map<String, String> premeta1 = new HashMap<String, String>();
		premeta1.put("this is", "some meta meta");
		premeta1.put("bro", "heim");
		WorkspaceUserMetadata meta1 = new WorkspaceUserMetadata(premeta1);
		
		Map<String, String> premeta2 = new HashMap<String, String>();
		premeta2.put("suckmaster", "burstingfoam");
		WorkspaceUserMetadata meta2 = new WorkspaceUserMetadata(premeta2);
		
		WorkspaceInformation stdws = ws.createWorkspace(user, "stdws", false, null, meta1);
		WorkspaceInformation globalws = ws.createWorkspace(user, "globalws", true, null, meta2);
		WorkspaceInformation deletedws = ws.createWorkspace(user, "deletedws", false, null, null);
		ws.setWorkspaceDeleted(user, new WorkspaceIdentifier("deletedws"), true);
		
		ws.createWorkspace(user2, "readable", false, null, meta1);
		ws.setPermissions(user2, new WorkspaceIdentifier("readable"), Arrays.asList(user), Permission.READ);
		WorkspaceInformation readable = ws.getWorkspaceInformation(user, new WorkspaceIdentifier("readable"));
		ws.createWorkspace(user2, "writeable", false, null, meta2);
		ws.setPermissions(user2, new WorkspaceIdentifier("writeable"), Arrays.asList(user), Permission.WRITE);
		WorkspaceInformation writeable = ws.getWorkspaceInformation(user, new WorkspaceIdentifier("writeable"));
		ws.createWorkspace(user2, "adminable", false, null, null);
		ws.setPermissions(user2, new WorkspaceIdentifier("adminable"), Arrays.asList(user), Permission.ADMIN);
		WorkspaceInformation adminable = ws.getWorkspaceInformation(user, new WorkspaceIdentifier("adminable"));
		
		@SuppressWarnings("unused")
		WorkspaceInformation delreadable = ws.createWorkspace(user2, "delreadable", false, null, meta1);
		ws.setPermissions(user2, new WorkspaceIdentifier("delreadable"), Arrays.asList(user), Permission.READ);
		ws.setWorkspaceDeleted(user2, new WorkspaceIdentifier("delreadable"), true);
		ws.createWorkspace(user2, "globalreadable", true, null, meta2);
		WorkspaceInformation globalreadable = ws.getWorkspaceInformation(user, new WorkspaceIdentifier("globalreadable"));
		@SuppressWarnings("unused")
		WorkspaceInformation deletedglobalreadable =
				ws.createWorkspace(user2, "deletedglobalreadable", true, null, null);
		ws.setWorkspaceDeleted(user2, new WorkspaceIdentifier("deletedglobalreadable"), true);
		@SuppressWarnings("unused")
		WorkspaceInformation unreadable = ws.createWorkspace(user2, "unreadable", false, null, meta1);
		
		ws.createWorkspace(user3, "listuser3ws", false, null, null);
		ws.setPermissions(user3, new WorkspaceIdentifier("listuser3ws"), Arrays.asList(user), Permission.READ);
		WorkspaceInformation listuser3 = ws.getWorkspaceInformation(user, new WorkspaceIdentifier("listuser3ws"));
		ws.createWorkspace(user3, "listuser3glws", true, null, meta2);
		WorkspaceInformation listuser3gl = ws.getWorkspaceInformation(user, new WorkspaceIdentifier("listuser3glws"));
		
		
		Map<WorkspaceInformation, Boolean> expected = new HashMap<WorkspaceInformation, Boolean>();
		expected.put(stdws, false);
		expected.put(globalws, false);
		expected.put(readable, false);
		expected.put(writeable, false);
		expected.put(adminable, false);
		expected.put(listuser3, false);
		checkWSInfoList(ws.listWorkspaces(user, null, null, null, null, null,
				true, false, false), expected);
		checkWSInfoList(ws.listWorkspaces(user, null, null,
				new WorkspaceUserMetadata(MT_MAP), null, null, true, false,
				false), expected);
		
		expected.put(globalreadable, false);
		expected.put(listuser3gl, false);
		checkWSInfoList(ws.listWorkspaces(user, null, null, null, null, null, false, false, false), expected);
		
		expected.put(deletedws, true);
		checkWSInfoList(ws.listWorkspaces(user, null, null, null, null, null, false, true, false), expected);
		
		expected.remove(globalreadable);
		expected.remove(listuser3gl);
		checkWSInfoList(ws.listWorkspaces(user, null, null, null, null, null, true, true, false), expected);
		checkWSInfoList(ws.listWorkspaces(user, Permission.NONE, null, null, null, null, true, true, false), expected);
		checkWSInfoList(ws.listWorkspaces(user, Permission.READ, null, null, null, null, true, true, false), expected);

		expected.remove(readable);
		expected.remove(listuser3);
		checkWSInfoList(ws.listWorkspaces(user, Permission.WRITE, null, null, null, null, true, true, false), expected);
		expected.remove(writeable);
		checkWSInfoList(ws.listWorkspaces(user, Permission.ADMIN, null, null, null, null, true, true, false), expected);
		
		expected.clear();
		expected.put(globalreadable, false);
		expected.put(listuser3gl, false);
		WorkspaceUser newb = new WorkspaceUser("listUserAZillion");
		expected.put(ws.getWorkspaceInformation(newb, new WorkspaceIdentifier("globalws")), false);
		checkWSInfoList(ws.listWorkspaces(newb, null, null, null, null, null, false, false, false), expected);
		expected.clear();
		checkWSInfoList(ws.listWorkspaces(newb, null, null, null, null, null, false, false, true), expected);
		checkWSInfoList(ws.listWorkspaces(newb, null, null, null, null, null, true, false, false), expected);
		
		expected.put(deletedws, true);
		checkWSInfoList(ws.listWorkspaces(user, null, null, null, null, null, false, false, true), expected);
		checkWSInfoList(ws.listWorkspaces(user, null, null, null, null, null, false, true, true), expected);
		checkWSInfoList(ws.listWorkspaces(user, null, null, null, null, null, true, true, true), expected);
		checkWSInfoList(ws.listWorkspaces(user, null, null, null, null, null, false, false, true), expected);
		
		expected.clear();
		expected.put(stdws, false);
		expected.put(globalws, false);
		checkWSInfoList(ws.listWorkspaces(user, null, Arrays.asList(user), null, null, null, false,
				false, false), expected);
		expected.put(readable, false);
		expected.put(writeable, false);
		expected.put(adminable, false);
		expected.put(globalreadable, false);
		checkWSInfoList(ws.listWorkspaces(user, null, Arrays.asList(user, user2), null, null, null, false,
				false, false), expected);
		expected.put(listuser3, false);
		expected.put(listuser3gl, false);
		checkWSInfoList(ws.listWorkspaces(user, null, Arrays.asList(user, user2, user3), null, null, null, false,
				false, false), expected);
		expected.remove(globalreadable);
		expected.remove(listuser3gl);
		checkWSInfoList(ws.listWorkspaces(user, null, Arrays.asList(user, user2, user3), null, null, null, true,
				false, false), expected);
		expected.remove(stdws);
		expected.remove(globalws);
		checkWSInfoList(ws.listWorkspaces(user, null, Arrays.asList(user2, user3), null, null, null, true,
				false, false), expected);
		expected.remove(readable);
		expected.remove(writeable);
		expected.remove(adminable);
		checkWSInfoList(ws.listWorkspaces(user, null, Arrays.asList(user3), null, null, null, true,
				false, false), expected);
		
		Map<String, String> querymeta = new HashMap<String, String>();
		querymeta.put("suckmaster", "burstingfoam");
		expected.clear();
		expected.put(globalws, false);
		expected.put(writeable, false);
		expected.put(globalreadable, false);
		expected.put(listuser3gl, false);
		checkWSInfoList(ws.listWorkspaces(user, null, null,
				new WorkspaceUserMetadata(querymeta), null, null, false,
				false, false), expected);
		
		querymeta.clear();
		querymeta.put("this is", "some meta meta");
		expected.clear();
		expected.put(stdws, false);
		expected.put(readable, false);
		checkWSInfoList(ws.listWorkspaces(user, null, null,
				new WorkspaceUserMetadata(querymeta), null, null, false,
				false, false), expected);
		
		querymeta.clear();
		querymeta.put("bro", "heim");
		checkWSInfoList(ws.listWorkspaces(user, null, null,
				new WorkspaceUserMetadata(querymeta), null, null, false,
				false, false), expected);
		
		try {
			ws.listWorkspaces(user, null, null, meta1, null, null, false, false, false);
			fail("listed ws with bad meta");
		} catch (IllegalArgumentException exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is("Only one metadata spec allowed"));
		}
		
		ws.setGlobalPermission(user2, new WorkspaceIdentifier("globalreadable"), Permission.NONE);
		ws.setWorkspaceDeleted(user2, new WorkspaceIdentifier("deletedglobalreadable"), false);
		ws.setGlobalPermission(user2, new WorkspaceIdentifier("deletedglobalreadable"), Permission.NONE);
		ws.setGlobalPermission(user, new WorkspaceIdentifier("globalws"), Permission.NONE);
		ws.setGlobalPermission(user3, new WorkspaceIdentifier("listuser3glws"), Permission.NONE);
	}
	
	@Test
	public void listWorkspacesByDate() throws Exception {
		WorkspaceUser u = new WorkspaceUser("listwsbydate");
		WorkspaceInformation i1 = ws.createWorkspace(u, "listwsbydate1", false, null, null);
		Thread.sleep(100);
		WorkspaceInformation i2 = ws.createWorkspace(u, "listwsbydate2", false, null, null);
		Thread.sleep(100);
		WorkspaceInformation i3 = ws.createWorkspace(u, "listwsbydate3", false, null, null);
		Thread.sleep(100);
		WorkspaceInformation i4 = ws.createWorkspace(u, "listwsbydate4", false, null, null);
		Thread.sleep(100);
		WorkspaceInformation i5 = ws.createWorkspace(u, "listwsbydate5", false, null, null);
		final Date beforeall = Date.from(i1.getModDate().minusMillis(1));
		final Date afterall = Date.from(i5.getModDate().plusMillis(1));
		final Date d2 = Date.from(i2.getModDate());
		final Date d3 = Date.from(i3.getModDate());
		final Date d4 = Date.from(i4.getModDate());
		final Date d5 = Date.from(i5.getModDate());
		checkWSInfoList(ws.listWorkspaces(u, null, null, null, null, null, true, false, false),
				Arrays.asList(i1, i2, i3, i4, i5));
		checkWSInfoList(ws.listWorkspaces(u, null, null, null, beforeall, afterall, true, false, false),
				Arrays.asList(i1, i2, i3, i4, i5));
		checkWSInfoList(ws.listWorkspaces(u, null, null, null, afterall, beforeall, true, false, false),
				new ArrayList<WorkspaceInformation>());
		checkWSInfoList(ws.listWorkspaces(u, null, null, null, d3, d4, true, false, false),
				new ArrayList<WorkspaceInformation>());
		checkWSInfoList(ws.listWorkspaces(u, null, null, null, d2, d4, true, false, false),
				Arrays.asList(i3));
		checkWSInfoList(ws.listWorkspaces(u, null, null, null, d2, null, true, false, false),
				Arrays.asList(i3, i4, i5));
		checkWSInfoList(ws.listWorkspaces(u, null, null, null, null, d4, true, false, false),
				Arrays.asList(i1, i2, i3));
		checkWSInfoList(ws.listWorkspaces(u, null, null, null,
				Date.from(i2.getModDate().minusMillis(1)), d5, true, false, false),
				Arrays.asList(i2, i3, i4));
	}
	
	@Test
	public void listWorkspaceIDs() throws Exception {
		final WorkspaceUser u1 = new WorkspaceUser("u1");
		final WorkspaceUser u2 = new WorkspaceUser("u2");
		final WorkspaceIdentifier wiown = new WorkspaceIdentifier("own");
		final WorkspaceIdentifier wiadmin = new WorkspaceIdentifier("admin");
		final WorkspaceIdentifier wiwrite = new WorkspaceIdentifier("write");
		final WorkspaceIdentifier wiread = new WorkspaceIdentifier("read");
		final WorkspaceIdentifier wipub = new WorkspaceIdentifier("pub");
		final WorkspaceIdentifier winone = new WorkspaceIdentifier("none");
		final WorkspaceIdentifier widelpub = new WorkspaceIdentifier("delpub");
		final WorkspaceIdentifier wideladmin = new WorkspaceIdentifier("deladmin");
		
		ws.createWorkspace(u1, wiown.getName(), false, null, null);
		ws.createWorkspace(u2, wiadmin.getName(), false, null, null);
		ws.createWorkspace(u2, wiwrite.getName(), false, null, null);
		ws.createWorkspace(u2, wiread.getName(), false, null, null);
		ws.createWorkspace(u2, wipub.getName(), true, null, null);
		ws.createWorkspace(u2, winone.getName(), false, null, null);
		ws.createWorkspace(u2, widelpub.getName(), true, null, null);
		ws.createWorkspace(u2, wideladmin.getName(), false, null, null);
		
		ws.setPermissions(u2, wiadmin, Arrays.asList(u1), Permission.ADMIN);
		ws.setPermissions(u2, wideladmin, Arrays.asList(u1), Permission.ADMIN);
		ws.setPermissions(u2, wiwrite, Arrays.asList(u1), Permission.WRITE);
		ws.setPermissions(u2, wiread, Arrays.asList(u1), Permission.READ);
		
		ws.setWorkspaceDeleted(u2, widelpub, true);
		ws.setWorkspaceDeleted(u2, wideladmin, true);
		
		assertThat("incorrect workspaces", ws.listWorkspaceIDs(u1, null, false),
				is(new UserWorkspaceIDs(u1, Permission.READ,
						new HashSet<>(Arrays.asList(1L, 2L, 3L, 4L)),
						new HashSet<>(Arrays.asList(5L)))));
		
		assertThat("incorrect workspaces", ws.listWorkspaceIDs(u1, Permission.NONE, false),
				is(new UserWorkspaceIDs(u1, Permission.READ,
						new HashSet<>(Arrays.asList(1L, 2L, 3L, 4L)),
						new HashSet<>(Arrays.asList(5L)))));
		
		assertThat("incorrect workspaces", ws.listWorkspaceIDs(u1, Permission.READ, false),
				is(new UserWorkspaceIDs(u1, Permission.READ,
						new HashSet<>(Arrays.asList(1L, 2L, 3L, 4L)),
						new HashSet<>(Arrays.asList(5L)))));
		
		assertThat("incorrect workspaces", ws.listWorkspaceIDs(u1, Permission.NONE, true),
				is(new UserWorkspaceIDs(u1, Permission.READ,
						new HashSet<>(Arrays.asList(1L, 2L, 3L, 4L)),
						new HashSet<>())));
		
		assertThat("incorrect workspaces", ws.listWorkspaceIDs(null, Permission.NONE, false),
				is(new UserWorkspaceIDs(null, Permission.READ,
						new HashSet<>(),
						new HashSet<>(Arrays.asList(5L)))));
		
		assertThat("incorrect workspaces", ws.listWorkspaceIDs(null, Permission.NONE, true),
				is(new UserWorkspaceIDs(null, Permission.READ,
						new HashSet<>(),
						new HashSet<>())));
		
		assertThat("incorrect workspaces", ws.listWorkspaceIDs(u1, Permission.WRITE, false),
				is(new UserWorkspaceIDs(u1, Permission.WRITE,
						new HashSet<>(Arrays.asList(1L, 2L, 3L)),
						new HashSet<>())));
		
		assertThat("incorrect workspaces", ws.listWorkspaceIDs(u1, Permission.ADMIN, false),
				is(new UserWorkspaceIDs(u1, Permission.ADMIN,
						new HashSet<>(Arrays.asList(1L, 2L)),
						new HashSet<>())));
		
		assertThat("incorrect workspaces", ws.listWorkspaceIDs(u1, Permission.OWNER, false),
				is(new UserWorkspaceIDs(u1, Permission.OWNER,
						new HashSet<>(Arrays.asList(1L)),
						new HashSet<>())));
	}
	
	@Test
	public void listObjectsWithDeletedObjects() throws Exception {
		/* Test that deleted objects only show up in the objects list when 
		 * requested *and* when the user has permission to write to the
		 * workspace, which is required for listing deleted objects.
		 */
		WorkspaceUser u1 = new WorkspaceUser("listObjDelUser1");
		WorkspaceUser u2 = new WorkspaceUser("listObjDelUser2");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("listObjDel");
		ws.createWorkspace(u1, wsi.getName(), false, null, null);
		ws.setPermissions(u1, wsi, Arrays.asList(u2), Permission.READ);
		
		ObjectInformation std = ws.saveObjects(u1, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("std"), new HashMap<String, String>(), SAFE_TYPE1,
				null, new Provenance(u1), false)), getIdFactory()).get(0);
		ObjectInformation del = ws.saveObjects(u1, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("del"), new HashMap<String, String>(), SAFE_TYPE1,
				null, new Provenance(u1), false)), getIdFactory()).get(0);
		ws.setObjectsDeleted(u1, Arrays.asList(new ObjectIdentifier(wsi, "del")), true);
		
		ListObjectsParameters lop = new ListObjectsParameters(u1, Arrays.asList(wsi))
				.withIncludeMetaData(true);
		//test user1 - owner. Should always see deleted if requested.
		compareObjectInfo(ws.listObjects(lop), Arrays.asList(std));
		compareObjectInfo(ws.listObjects(lop.withShowDeleted(true)),
				Arrays.asList(std, del));
		compareObjectInfo(ws.listObjects(lop.withShowDeleted(false).withShowOnlyDeleted(true)),
				Arrays.asList(del));
		
		lop = new ListObjectsParameters(u2, Arrays.asList(wsi))
				.withIncludeMetaData(true);
		//test user2 with only read perms. Should never see deleted objects.
		compareObjectInfo(ws.listObjects(lop), Arrays.asList(std));
		compareObjectInfo(ws.listObjects(lop.withShowDeleted(true)),
				Arrays.asList(std));
		compareObjectInfo(ws.listObjects(lop.withShowDeleted(false).withShowOnlyDeleted(true)),
				new LinkedList<ObjectInformation>());
		
		//test user2 with write perms. Should always see deleted if requested.
		ws.setPermissions(u1, wsi, Arrays.asList(u2), Permission.WRITE);
		compareObjectInfo(ws.listObjects(lop.withShowOnlyDeleted(false)), Arrays.asList(std));
		compareObjectInfo(ws.listObjects(lop.withShowDeleted(true)),
				Arrays.asList(std, del));
		compareObjectInfo(ws.listObjects(lop.withShowDeleted(false).withShowOnlyDeleted(true)),
				Arrays.asList(del));
		
	}
	
	@Test
	public void listObjectsWithDeletedWorkspace() throws Exception {
		/* Test that objects from a deleted workspace don't show up in 
		 * listObjects output.
		 */
		WorkspaceUser u1 = new WorkspaceUser("listObjDelWSUser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("listObjDelWS");
		WorkspaceIdentifier wsdel = new WorkspaceIdentifier("listObjDelWS_Deleted");
		ws.createWorkspace(u1, wsi.getName(), false, null, null);
		ws.createWorkspace(u1, wsdel.getName(), false, null, null);
		
		Map<String, String> pmeta = new HashMap<String, String>();
		pmeta.put("test", "listObjDelWS");
		WorkspaceUserMetadata meta = new WorkspaceUserMetadata(pmeta);
		
		ObjectInformation std = ws.saveObjects(u1, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("std"), new HashMap<String, String>(), SAFE_TYPE1,
				meta, new Provenance(u1), false)), getIdFactory()).get(0);
		ws.saveObjects(u1, wsdel, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("del"), new HashMap<String, String>(), SAFE_TYPE1,
				meta, new Provenance(u1), false)), getIdFactory()).get(0);
		ws.setWorkspaceDeleted(u1, wsdel, true);
		
		ListObjectsParameters lop = new ListObjectsParameters(u1, SAFE_TYPE1)
				.withMetadata(meta).withIncludeMetaData(true);
		compareObjectInfo(ws.listObjects(lop), Arrays.asList(std));
		compareObjectInfo(ws.listObjects(lop.withShowDeleted(true)),
				Arrays.asList(std));
		compareObjectInfo(ws.listObjects(lop.withShowDeleted(false).withShowOnlyDeleted(true)),
				new LinkedList<ObjectInformation>());
	}
	
	@Test
	public void listObjectsAndHistory() throws Exception {
		WorkspaceUser user = new WorkspaceUser("listObjUser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("listObj1");
		WorkspaceIdentifier readable = new WorkspaceIdentifier("listObjread");
		WorkspaceIdentifier writeable = new WorkspaceIdentifier("listObjwrite");
		WorkspaceIdentifier adminable = new WorkspaceIdentifier("listObjadmin");
		WorkspaceIdentifier thirdparty = new WorkspaceIdentifier("thirdparty");
		WorkspaceUser user2 = new WorkspaceUser("listObjUser2");
		ws.createWorkspace(user, wsi.getName(), false, null, null).getId();
		ws.createWorkspace(user2, readable.getName(), false, null, null).getId();
		ws.setPermissions(user2, readable, Arrays.asList(user), Permission.READ);
		ws.createWorkspace(user2, writeable.getName(), false, null, null).getId();
		ws.setPermissions(user2, writeable, Arrays.asList(user), Permission.WRITE);
		ws.createWorkspace(user2, adminable.getName(), false, null, null).getId();
		ws.setPermissions(user2, adminable, Arrays.asList(user), Permission.ADMIN);
		WorkspaceUser user3 = new WorkspaceUser("listObjUser3");
		ws.createWorkspace(user3, thirdparty.getName(), true, null, null).getId();
		
		Map<String, String> pmeta = new HashMap<String, String>();
		pmeta.put("meta1", "1");
		Map<String, String> pmeta2 = new HashMap<String, String>();
		pmeta2.put("meta2", "2");
		Map<String, String> pmeta3 = new HashMap<String, String>();
		pmeta3.put("meta3", "3");
		Map<String, String> pmeta32 = new HashMap<String, String>();
		pmeta32.put("meta3", "3");
		pmeta32.put("meta2", "2");
		WorkspaceUserMetadata meta = new WorkspaceUserMetadata(pmeta);
		WorkspaceUserMetadata meta2 = new WorkspaceUserMetadata(pmeta2);
		WorkspaceUserMetadata meta3 = new WorkspaceUserMetadata(pmeta3);
		WorkspaceUserMetadata meta32 = new WorkspaceUserMetadata(pmeta32);
		
		
		Map<String, Object> passTCdata = new HashMap<String, Object>();
		passTCdata.put("thing", "athing");
		
		ObjectInformation std = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("std"), new HashMap<String, String>(), SAFE_TYPE1,
				null, new Provenance(user), false)), getIdFactory()).get(0);
		ObjectInformation stdnometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "std")), false, false).get(0);
		
		ObjectInformation objstack1 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("objstack"), new HashMap<String, String>(), SAFE_TYPE1_10, meta,
				new Provenance(user), false)), getIdFactory()).get(0);
		ObjectInformation objstack1nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "objstack", 1)), false, false).get(0);
		
		ObjectInformation objstack2 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("objstack"), passTCdata, SAFE_TYPE1_20, meta2,
				new Provenance(user), false)), getIdFactory()).get(0);
		ObjectInformation objstack2nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "objstack", 2)), false, false).get(0);
		
		ObjectInformation type2_1 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("type2"), new HashMap<String, String>(), SAFE_TYPE2, meta,
				new Provenance(user), false)), getIdFactory()).get(0);
		ObjectInformation type2_1nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "type2", 1)), false, false).get(0);
		
		ObjectInformation type2_2 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("type2"), new HashMap<String, String>(), SAFE_TYPE2_10, meta2,
				new Provenance(user), false)), getIdFactory()).get(0);
		ObjectInformation type2_2nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "type2", 2)), false, false).get(0);
		
		ObjectInformation type2_3 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("type2"), passTCdata, SAFE_TYPE2_20, meta32,
				new Provenance(user), false)), getIdFactory()).get(0);
		ObjectInformation type2_3nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "type2", 3)), false, false).get(0);
		
		ObjectInformation type2_4 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("type2"), passTCdata, SAFE_TYPE2_21, meta3,
				new Provenance(user), false)), getIdFactory()).get(0);
		ObjectInformation type2_4nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "type2", 4)), false, false).get(0);
		
		ObjectInformation stdws2 = ws.saveObjects(user2, writeable, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("stdws2"), new HashMap<String, String>(), SAFE_TYPE1, meta,
				new Provenance(user), false)), getIdFactory()).get(0);
		ObjectInformation stdws2nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(writeable, "stdws2")), false, false).get(0);
		
		ObjectInformation hidden = ws.saveObjects(user, writeable, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("hidden"), new HashMap<String, String>(), SAFE_TYPE1, meta2,
				new Provenance(user), false)), getIdFactory()).get(0);
		ObjectInformation hiddennometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(writeable, "hidden")), false, false).get(0);
		ws.setObjectsHidden(user, Arrays.asList(new ObjectIdentifier(writeable, "hidden")), true);
		
		ObjectInformation deleted = ws.saveObjects(user2, writeable, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("deleted"), new HashMap<String, String>(), SAFE_TYPE1, meta32,
				new Provenance(user), false)), getIdFactory()).get(0);
		ObjectInformation deletednometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(writeable, "deleted")), false, false).get(0);
		ws.setObjectsDeleted(user, Arrays.asList(new ObjectIdentifier(writeable, "deleted")), true);
		
		ObjectInformation readobj = ws.saveObjects(user2, readable, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("readobj"), new HashMap<String, String>(), SAFE_TYPE1, meta3,
				new Provenance(user), false)), getIdFactory()).get(0);
		ObjectInformation readobjnometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(readable, "readobj")), false, false).get(0);
		
		ObjectInformation adminobj = ws.saveObjects(user2, adminable, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("adminobj"), new HashMap<String, String>(), SAFE_TYPE1, meta3,
				new Provenance(user), false)), getIdFactory()).get(0);
		ObjectInformation adminobjnometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(adminable, "adminobj")), false, false).get(0);
		
		ObjectInformation thirdobj = ws.saveObjects(user3, thirdparty, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("thirdobj"), new HashMap<String, String>(), SAFE_TYPE1, meta,
				new Provenance(user), false)), getIdFactory()).get(0);
		ObjectInformation thirdobjnometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(thirdparty, "thirdobj")), false, false).get(0);
		//this should be invisible to anyone except user3
		ws.saveObjects(user3, thirdparty, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("thirdobjdel"), new HashMap<String, String>(), SAFE_TYPE1, meta,
				new Provenance(user), false)), getIdFactory()).get(0);
		ws.setObjectsDeleted(user3, Arrays.asList(new ObjectIdentifier(thirdparty, "thirdobjdel")), true);
		
		TypeDefId allType1 = new TypeDefId(SAFE_TYPE1.getType().getTypeString());
		TypeDefId allType2 = new TypeDefId(SAFE_TYPE2.getType().getTypeString());
		
		//test with anon user
		ListObjectsParameters lop = new ListObjectsParameters((WorkspaceUser) null, SAFE_TYPE1)
				.withShowDeleted(true).withIncludeMetaData(true);
		compareObjectInfo(ws.listObjects(lop), Arrays.asList(thirdobj));
		compareObjectInfo(ws.listObjects(lop.withExcludeGlobal(true)),
				new LinkedList<ObjectInformation>());
		
		//test basics
		lop = new ListObjectsParameters(user, Arrays.asList(wsi, writeable))
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true);
		compareObjectInfo(ws.listObjects(lop),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2, hidden, deleted));
		compareObjectInfo(ws.listObjects(lop.withShowOnlyDeleted(true)),
				Arrays.asList(deleted));
		compareObjectInfo(ws.listObjects(new ListObjectsParameters(user, Arrays.asList(wsi))
				.withShowHidden(true).withShowDeleted(true).withShowOnlyDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true)),
				new ArrayList<ObjectInformation>());
		compareObjectInfo(ws.listObjects(lop.withShowHidden(false).withShowOnlyDeleted(false)),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2, deleted));
		compareObjectInfo(ws.listObjects(lop.withShowHidden(true).withShowDeleted(false)),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2, hidden));
		compareObjectInfo(ws.listObjects(lop.withShowHidden(false)),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2));
		compareObjectInfo(ws.listObjects(lop.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(false)),
				Arrays.asList(std, objstack2, type2_4, stdws2, hidden, deleted));
		compareObjectInfo(ws.listObjects(lop.withShowHidden(false).withShowDeleted(false)
				.withIncludeMetaData(false)),
				Arrays.asList(stdnometa, objstack2nometa, type2_4nometa, stdws2nometa));
		compareObjectInfo(ws.listObjects(lop.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true)),
				Arrays.asList(stdnometa, objstack1nometa, objstack2nometa, type2_1nometa,
						type2_2nometa, type2_3nometa, type2_4nometa,
						stdws2nometa, hiddennometa, deletednometa));
		
		lop = new ListObjectsParameters(user, allType1)
				.withShowHidden(true).withShowDeleted(true).withShowAllVersions(true)
				.withIncludeMetaData(true);
		compareObjectInfo(ws.listObjects(lop), Arrays.asList(std, objstack1,
				objstack2, stdws2, hidden, deleted, readobj, adminobj,
				thirdobj));
		compareObjectInfo(ws.listObjects(lop.withSavers(new ArrayList<WorkspaceUser>())),
				Arrays.asList(std, objstack1, objstack2, stdws2, hidden,
						deleted, readobj, adminobj, thirdobj));
		
		//exclude globally readable workspaces
		compareObjectInfo(ws.listObjects(lop.withExcludeGlobal(true)),
				Arrays.asList(std, objstack1, objstack2, stdws2, hidden,
						deleted, readobj, adminobj));
		//if the globally readable workspace is explicitly listed, should ignore excludeGlobal
		lop = new ListObjectsParameters(user, Arrays.asList(wsi, writeable, thirdparty))
				.withShowHidden(true).withShowDeleted(true).withShowAllVersions(true)
				.withIncludeMetaData(true);
		compareObjectInfo(ws.listObjects(lop),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2, hidden, deleted, thirdobj));
		compareObjectInfo(ws.listObjects(lop.withExcludeGlobal(true)),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2, hidden, deleted, thirdobj));
		
		//test user filtering
		lop = new ListObjectsParameters(user, allType1)
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true);
		compareObjectInfo(ws.listObjects(lop.withSavers(Arrays.asList(user, user2, user3))),
				Arrays.asList(std, objstack1, objstack2, stdws2, hidden,
						deleted, readobj, adminobj, thirdobj));
		compareObjectInfo(ws.listObjects(lop.withSavers(Arrays.asList(user2, user3))),
				Arrays.asList(stdws2, deleted, readobj, adminobj, thirdobj));
		compareObjectInfo(ws.listObjects(lop.withSavers(Arrays.asList(user, user3))),
				Arrays.asList(std, hidden, objstack1, objstack2, thirdobj));
		compareObjectInfo(ws.listObjects(lop.withSavers(Arrays.asList(user3))),
				Arrays.asList(thirdobj));
		compareObjectInfo(ws.listObjects(lop.withSavers(Arrays.asList(user))),
				Arrays.asList(std, hidden, objstack1, objstack2));
		
		//meta filtering
		lop = new ListObjectsParameters(user, Arrays.asList(wsi, writeable))
				.withMetadata(new WorkspaceUserMetadata())
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true);
		compareObjectInfo(ws.listObjects(lop),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2, hidden, deleted));
		compareObjectInfo(ws.listObjects(lop.withMetadata(meta)),
				Arrays.asList(objstack1, type2_1, stdws2));
		compareObjectInfo(ws.listObjects(lop.withMetadata(meta2)),
				Arrays.asList(objstack2, type2_2, type2_3, hidden, deleted));
		compareObjectInfo(ws.listObjects(lop.withMetadata(meta3)),
				Arrays.asList(type2_3, type2_4, deleted));
		compareObjectInfo(ws.listObjects(lop.withMetadata(meta).withShowAllVersions(false)),
				Arrays.asList(stdws2));
		compareObjectInfo(ws.listObjects(lop.withMetadata(meta2)),
				Arrays.asList(objstack2, hidden, deleted));
		
		//type filtering
		compareObjectInfo(ws.listObjects(new ListObjectsParameters(user, Arrays.asList(wsi), allType1)
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true)),
				Arrays.asList(std, objstack1, objstack2));
		compareObjectInfo(ws.listObjects(new ListObjectsParameters(user, Arrays.asList(writeable), allType1)
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true)),
				Arrays.asList(stdws2, hidden, deleted));
		compareObjectInfo(ws.listObjects(new ListObjectsParameters(user, allType2)
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true)),
				Arrays.asList(type2_1, type2_2, type2_3, type2_4));
		compareObjectInfo(ws.listObjects(new ListObjectsParameters(user, Arrays.asList(writeable), allType2)
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true)),
				new ArrayList<ObjectInformation>());
		
		//permission filtering
		lop = new ListObjectsParameters(user, SAFE_TYPE1)
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true);
		compareObjectInfo(ws.listObjects(lop),
				Arrays.asList(std, stdws2, hidden, deleted,
						readobj, adminobj, thirdobj));
		compareObjectInfo(ws.listObjects(lop.withIncludeMetaData(false)),
				Arrays.asList(stdnometa, stdws2nometa, hiddennometa,
						deletednometa, readobjnometa, adminobjnometa, thirdobjnometa));
		compareObjectInfo(ws.listObjects(lop.withMinimumPermission(Permission.NONE)),
				Arrays.asList(stdnometa, stdws2nometa, hiddennometa,
						deletednometa, readobjnometa, adminobjnometa, thirdobjnometa));
		compareObjectInfo(ws.listObjects(lop.withMinimumPermission(Permission.READ)),
				Arrays.asList(stdnometa, stdws2nometa, hiddennometa,
						deletednometa, readobjnometa, adminobjnometa, thirdobjnometa));
		compareObjectInfo(ws.listObjects(lop.withMinimumPermission(Permission.WRITE)),
				Arrays.asList(stdnometa, stdws2nometa, hiddennometa, deletednometa, adminobjnometa));
		compareObjectInfo(ws.listObjects(lop.withMinimumPermission(Permission.ADMIN)),
				Arrays.asList(stdnometa, adminobjnometa));
		
		//more type filtering
		compareObjectInfo(ws.listObjects(new ListObjectsParameters(user, SAFE_TYPE1_10)
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true)),
				Arrays.asList(objstack1));
		compareObjectInfo(ws.listObjects(new ListObjectsParameters(user, SAFE_TYPE1_20)
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true)),
				Arrays.asList(objstack2));
		compareObjectInfo(ws.listObjects(new ListObjectsParameters(user, SAFE_TYPE2)
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true)),
				Arrays.asList(type2_1));
		compareObjectInfo(ws.listObjects(new ListObjectsParameters(user, SAFE_TYPE2_10)
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true)),
				Arrays.asList(type2_2));
		compareObjectInfo(ws.listObjects(new ListObjectsParameters(user, SAFE_TYPE2_20)
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true)),
				Arrays.asList(type2_3));
		compareObjectInfo(ws.listObjects(new ListObjectsParameters(
				user, new TypeDefId(SAFE_TYPE2_20.getType(), SAFE_TYPE2_20.getMajorVersion()))
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true)),
				Arrays.asList(type2_3, type2_4));
		compareObjectInfo(ws.listObjects(new ListObjectsParameters(
				user, new TypeDefId(SAFE_TYPE2_10.getType(), SAFE_TYPE2_10.getMajorVersion()))
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true)),
				Arrays.asList(type2_2));
		
		compareObjectInfo(ws.listObjects(new ListObjectsParameters(user2, allType1)
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true)),
				Arrays.asList(stdws2, hidden, deleted, readobj,
						adminobj, thirdobj));
		compareObjectInfo(ws.listObjects(new ListObjectsParameters(user2, Arrays.asList(writeable))
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true)),
				Arrays.asList(stdws2, hidden, deleted));
		compareObjectInfo(ws.listObjects(new ListObjectsParameters(user2, allType2)
				.withShowHidden(true).withShowDeleted(true)
				.withShowAllVersions(true).withIncludeMetaData(true)),
				new ArrayList<ObjectInformation>());
		
		//TODO MOVE move these to unit tests for LOP
		//TODO TEST you can test the 2 arg constructor, just cast the null idiot
		// can't test 2 argument constructor with the 2nd constructor argument
		// null since then constructor is ambiguous
		try {
			new ListObjectsParameters(user, new LinkedList<WorkspaceIdentifier>());
			fail("Created list objs param with bad init");
		} catch (IllegalArgumentException e) {
			assertThat("Correct exception msg", e.getLocalizedMessage(),
					is("Must provide at least one workspace"));
		}
		try {
			new ListObjectsParameters(user, null, SAFE_TYPE1);
			fail("Created list objs param with bad init");
		} catch (IllegalArgumentException e) {
			assertThat("Correct exception msg", e.getLocalizedMessage(),
					is("Must provide at least one workspace"));
		}
		try {
			new ListObjectsParameters(user, new LinkedList<WorkspaceIdentifier>(), SAFE_TYPE1);
			fail("Created list objs param with bad init");
		} catch (IllegalArgumentException e) {
			assertThat("Correct exception msg", e.getLocalizedMessage(),
					is("Must provide at least one workspace"));
		}
		try {
			new ListObjectsParameters(user, Arrays.asList(wsi), null);
			fail("Created list objs param with bad init");
		} catch (NullPointerException e) {
			assertThat("Correct exception msg", e.getLocalizedMessage(),
					is("Type cannot be null"));
		}
	
		failListObjects(user2, Arrays.asList(wsi, writeable), null,
				new WorkspaceAuthorizationException("User listObjUser2 may not read workspace listObj1"));
		failListObjects(null, Arrays.asList(wsi), null,
				new WorkspaceAuthorizationException("Anonymous users may not read workspace listObj1"));
		failListObjects(user, Arrays.asList(writeable, new WorkspaceIdentifier("listfake")), null,
				new NoSuchWorkspaceException("No workspace with name listfake exists", wsi));
		failListObjects(user, Arrays.asList(wsi, writeable), meta32.getMetadata(),
				new IllegalArgumentException("Only one metadata spec allowed"));
		
		ws.createWorkspace(user, "listdel", false, null, null);
		ws.setWorkspaceDeleted(user, new WorkspaceIdentifier("listdel"), true);
		failListObjects(user, Arrays.asList(writeable, new WorkspaceIdentifier("listdel")), null,
				new NoSuchWorkspaceException("Workspace listdel is deleted", wsi));
		
		assertThat("correct object history for std", 
				ws.getObjectHistory(user, new ObjectIdentifier(wsi, "std")),
				is(Arrays.asList(std)));
		assertThat("correct object history for type2", 
				ws.getObjectHistory(user, new ObjectIdentifier(wsi, "type2")),
				is(Arrays.asList(type2_1, type2_2, type2_3, type2_4)));
		assertThat("correct object history for type2", 
				ws.getObjectHistory(user, new ObjectIdentifier(wsi, 3)),
				is(Arrays.asList(type2_1, type2_2, type2_3, type2_4)));
		assertThat("correct object history for type2", 
				ws.getObjectHistory(user, new ObjectIdentifier(wsi, "type2", 3)),
				is(Arrays.asList(type2_1, type2_2, type2_3, type2_4)));
		assertThat("correct object history for type2", 
				ws.getObjectHistory(user, new ObjectIdentifier(wsi, 3, 4)),
				is(Arrays.asList(type2_1, type2_2, type2_3, type2_4)));
		assertThat("correct object history for objstack", 
				ws.getObjectHistory(user, new ObjectIdentifier(wsi, "objstack")),
				is(Arrays.asList(objstack1, objstack2)));
		assertThat("correct object history for stdws2", 
				ws.getObjectHistory(user2, new ObjectIdentifier(writeable, "stdws2")),
				is(Arrays.asList(stdws2)));
		
		failGetObjectHistory(user, new ObjectIdentifier(wsi, "booger"), new NoSuchObjectException(
				"No object with name booger exists in workspace 1 (name listObj1)", null));
		failGetObjectHistory(user, new ObjectIdentifier(new WorkspaceIdentifier("listObjectsfake"), "booger"),
				new InaccessibleObjectException("Object booger cannot be accessed: No workspace " +
						"with name listObjectsfake exists", null));
		failGetObjectHistory(user, new ObjectIdentifier(new WorkspaceIdentifier("listdel"), "booger"),
				new InaccessibleObjectException("Object booger cannot be accessed: Workspace " +
						"listdel is deleted", null));
		failGetObjectHistory(user2, new ObjectIdentifier(wsi, 3),
				new InaccessibleObjectException("Object 3 cannot be accessed: User listObjUser2 " +
						"may not read workspace listObj1", null));
		failGetObjectHistory(null, new ObjectIdentifier(wsi, 3),
				new InaccessibleObjectException("Object 3 cannot be accessed: Anonymous users " +
						"may not read workspace listObj1", null));
		failGetObjectHistory(user2, new ObjectIdentifier(writeable, "deleted"),
				new DeletedObjectException("Object 3 (name deleted) in workspace 3 " +
						"(name listObjwrite) has been deleted", null));
		
		ws.setGlobalPermission(user3, new WorkspaceIdentifier("thirdparty"), Permission.NONE);
	}

	@Test
	public void getNamesByPrefix() throws Exception {
		WorkspaceUser u = new WorkspaceUser("getNamesByPrefix");
		WorkspaceIdentifier wsi1 = new WorkspaceIdentifier("getNamesByPrefix1");
		WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("getNamesByPrefix2");
		WorkspaceIdentifier wsiMT = new WorkspaceIdentifier("getNamesByPrefixMT");
		WorkspaceIdentifier wsiGR = new WorkspaceIdentifier("getNamesByPrefixGR");
		for (WorkspaceIdentifier wi: Arrays.asList(wsi1, wsi2, wsiMT, wsiGR)) {
			ws.createWorkspace(u, wi.getName(), false, null, null);
		}
		Map<String, String> mt = new HashMap<String, String>();
		Provenance p = new Provenance(u);
		
		ws.setGlobalPermission(u, wsiGR, Permission.READ);
		ws.saveObjects(u, wsi1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("aaa"), mt,
						SAFE_TYPE1, null, p, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("aba"), mt,
						SAFE_TYPE1, null, p, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("abc"), mt,
						SAFE_TYPE1, null, p, true),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("abcdel"), mt,
						SAFE_TYPE1, null, p, false)
				), getIdFactory());
		ws.setObjectsDeleted(u,
				Arrays.asList(new ObjectIdentifier(wsi1, "abcdel")), true);
		ws.saveObjects(u, wsi2, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("aca"), mt,
						SAFE_TYPE1, null, p, false)), getIdFactory());
		ws.saveObjects(u, wsiGR, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("abd"), mt,
						SAFE_TYPE1, null, p, false)), getIdFactory());
		
		List<String> mtList = new LinkedList<String>();
		List<String> wsi2n = Arrays.asList("aca");
		List<String> wsiGRn = Arrays.asList("abd");
		
		//check various prefixes
		checkGetByPrefix(u, Arrays.asList(wsi1, wsi2, wsiMT, wsiGR),
				"", true, 1000, Arrays.asList(
				Arrays.asList("aaa", "aba", "abc"), wsi2n, mtList, wsiGRn));
		checkGetByPrefix(u, Arrays.asList(wsi1, wsi2, wsiMT, wsiGR),
				"a", true, 10, Arrays.asList(
				Arrays.asList("aaa", "aba", "abc"), wsi2n, mtList, wsiGRn));
		checkGetByPrefix(u, Arrays.asList(wsi1, wsi2, wsiGR),
				"ab", true, 10, Arrays.asList(
				Arrays.asList("aba", "abc"), mtList, wsiGRn));

		//check no hidden objects
		checkGetByPrefix(u, Arrays.asList(wsi1, wsi2, wsiGR),
				"ab", false, 10, Arrays.asList(
				Arrays.asList("aba"), mtList, wsiGRn));
		
		//test with null user
		checkGetByPrefix(null, Arrays.asList(wsiGR),
				"", false, 3, Arrays.asList(wsiGRn));
		
		//test with no workspaces
		checkGetByPrefix(u, new LinkedList<WorkspaceIdentifier>(),
				"", true, 1000, new LinkedList<List<String>> ());
	}
	
	private void checkGetByPrefix(WorkspaceUser user,
			WorkspaceIdentifier wsi,
			String prefix,
			List<String> results)
			throws Exception {
		checkGetByPrefix(user, Arrays.asList(wsi), prefix, false, 1000,
				Arrays.asList(results));
	}
	
	private void checkGetByPrefix(WorkspaceUser user,
			List<WorkspaceIdentifier> wsis,
			String prefix,
			boolean includeHidden,
			int limit,
			List<List<String>> results)
			throws Exception {
		List<Set<String>> exp = new LinkedList<Set<String>>();
		for (List<String> r: results) {
			exp.add(new HashSet<String>(r));
		}
		List<Set<String>> got = new LinkedList<Set<String>>();
		List<List<String>> ret = ws.getNamesByPrefix(
				user, wsis, prefix, includeHidden, limit);
		for (List<String> r: ret) {
			got.add(new HashSet<String>(r));
		}
		
		assertThat("correct returned names", got, is(exp));
	}
	
	@Test
	public void getNameByPrefixRegex() throws Exception {
		WorkspaceUser u = new WorkspaceUser("getNamesByPrefix");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("getNamesByPrefix1");
		ws.createWorkspace(u, wsi.getName(), false, null, null);
		Map<String, String> mt = new HashMap<String, String>();
		Provenance p = new Provenance(u);
		List<String> mtlist = new LinkedList<String>();
		
		ws.saveObjects(u, wsi, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("foo|bar.baz-thing_woo"), mt,
						SAFE_TYPE1, null, p, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("aba"), mt,
						SAFE_TYPE1, null, p, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("abc"), mt,
						SAFE_TYPE1, null, p, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("ab."), mt,
						SAFE_TYPE1, null, p, false)
				), getIdFactory());
		
		checkGetByPrefix(u, wsi, "f",
				Arrays.asList("foo|bar.baz-thing_woo"));
		checkGetByPrefix(u, wsi, "foo|bar.baz-thing_",
				Arrays.asList("foo|bar.baz-thing_woo"));
		
		//regex patterns shouldn't work
		checkGetByPrefix(u, wsi, ".*", mtlist);
		checkGetByPrefix(u, wsi, "ab.", Arrays.asList("ab."));
		checkGetByPrefix(u, wsi, "\\w", mtlist);
		checkGetByPrefix(u, wsi, "ab[a-c]", mtlist);
		checkGetByPrefix(u, wsi, "ab(a|c)", mtlist);
		checkGetByPrefix(u, wsi, "aba|abc", mtlist);
		// end a quote, which is what Pattern.quote does to make a literal sequence
		checkGetByPrefix(u, wsi, "\\E.*", mtlist);
		
	}
	
	@Test
	public void getNamesByPrefixLimit() throws Exception {
		WorkspaceUser u = new WorkspaceUser("getNamesByPrefix");
		WorkspaceIdentifier wsi1 = new WorkspaceIdentifier("getNamesByPrefix1");
		WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("getNamesByPrefix2");
		for (WorkspaceIdentifier wi: Arrays.asList(wsi1, wsi2)) {
			ws.createWorkspace(u, wi.getName(), false, null, null);
		}
		Map<String, String> mt = new HashMap<String, String>();
		Provenance p = new Provenance(u);
		
		ws.saveObjects(u, wsi1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("aaa"), mt,
						SAFE_TYPE1, null, p, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("aba"), mt,
						SAFE_TYPE1, null, p, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("abc"), mt,
						SAFE_TYPE1, null, p, false)
				), getIdFactory());
		ws.saveObjects(u, wsi2, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("aca"), mt,
						SAFE_TYPE1, null, p, false)), getIdFactory());
		
		List<String> wsi2n = Arrays.asList("aca");
		
		checkGetByPrefixLimit(u, Arrays.asList(wsi1), 1, 1, Arrays.asList(
				Arrays.asList("aaa", "aba", "abc")));
		checkGetByPrefixLimit(u, Arrays.asList(wsi1), 4, 3, Arrays.asList(
				Arrays.asList("aaa", "aba", "abc")));
		checkGetByPrefixLimit(u, Arrays.asList(wsi1), 0, 3, Arrays.asList(
				Arrays.asList("aaa", "aba", "abc")));
		checkGetByPrefixLimit(u, Arrays.asList(wsi1), 3, 3, Arrays.asList(
				Arrays.asList("aaa", "aba", "abc")));
		checkGetByPrefixLimit(u, Arrays.asList(wsi1), 2, 2, Arrays.asList(
				Arrays.asList("aaa", "aba", "abc")));
		checkGetByPrefixLimit(u, Arrays.asList(wsi1, wsi2), 4, 4, Arrays.asList(
				Arrays.asList("aaa", "aba", "abc"), wsi2n));
		checkGetByPrefixLimit(u, Arrays.asList(wsi1, wsi2), 5, 4, Arrays.asList(
				Arrays.asList("aaa", "aba", "abc"), wsi2n));
		checkGetByPrefixLimit(u, Arrays.asList(wsi1, wsi2), 2, 2, Arrays.asList(
				Arrays.asList("aaa", "aba", "abc"), wsi2n));
	}
	
	private void checkGetByPrefixLimit(
			WorkspaceUser u,
			List<WorkspaceIdentifier> wsis,
			int limit,
			int expectedCount,
			List<List<String>> possibleContents)
			throws Exception {
		List<Set<String>> possibles = new LinkedList<Set<String>>();
		for (List<String> r: possibleContents) {
			possibles.add(new HashSet<String>(r));
		}
		List<Set<String>> got = new LinkedList<Set<String>>();
		List<List<String>> ret = ws.getNamesByPrefix(
				u, wsis, "", false, limit);
		for (List<String> r: ret) {
			got.add(new HashSet<String>(r));
		}
		assertThat("correct number of returned name lists", got.size(),
				is(possibles.size()));
		int count = 0;
		for (int i = 0; i < got.size(); i++) {
			count += got.get(i).size();
			for (String name: got.get(i)) {
				assertThat("returned name " + name + " exists in list of possible names",
						possibles.get(i).contains(name), is(true));
			}
			
		}
		assertThat("correct number of objects returned", count, is(expectedCount));
	}

	@Test
	public void getNamesByPrefixBadArgs() throws Exception {
		WorkspaceUser u = new WorkspaceUser("foo");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("foo");
		ws.createWorkspace(u, wsi.getName(), false, null, null);
		WorkspaceIdentifier wsidel = new WorkspaceIdentifier("del");
		ws.createWorkspace(u, wsidel.getName(), false, null, null);
		ws.setWorkspaceDeleted(u, wsidel, true);
		List<WorkspaceIdentifier> wsis = Arrays.asList(wsi);
		
		List<WorkspaceIdentifier> wsislarge =
				new LinkedList<WorkspaceIdentifier>();
		for (int i = 0; i < 1001; i++) {
			wsislarge.add(new WorkspaceIdentifier("foo" + i));
		}
		
		failGetNamesByPrefix(u, null, null, false, 1000,
				new NullPointerException("Workspace list cannot be null"));
		failGetNamesByPrefix(u, wsislarge, null, false, 1000,
				new IllegalArgumentException(
						"Maximum number of workspaces allowed for input is 1000"));
		failGetNamesByPrefix(u, wsis, null, false, 1000,
				new NullPointerException("prefix cannot be null"));
		failGetNamesByPrefix(u, wsis, "", false, 1001,
				new IllegalArgumentException("limit cannot be greater than 1000"));
		failGetNamesByPrefix(null, wsis, "", false, 1000,
				new WorkspaceAuthorizationException(
						"Anonymous users may not read workspace foo"));
		failGetNamesByPrefix(new WorkspaceUser("bar"), wsis, "", false, 1000,
				new WorkspaceAuthorizationException(
						"User bar may not read workspace foo"));
		failGetNamesByPrefix(u, Arrays.asList(new WorkspaceIdentifier("bar")),
				"", false, 1000,
				new NoSuchWorkspaceException("No workspace with name bar exists", wsi));
		failGetNamesByPrefix(u, Arrays.asList(wsidel),
				"", false, 1000,
				new NoSuchWorkspaceException("Workspace del is deleted", wsi));
		
	}

	@Test
	public void listObjectsByDate() throws Exception {
		WorkspaceUser u = new WorkspaceUser("listObjsByDate");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("listObjsByDateWS");
		ws.createWorkspace(u, wsi.getName(), false, null, null);
		Map<String, String> data = new HashMap<String, String>();
		Provenance p = new Provenance(u);
		ObjectInformation o1 = saveObject(u, wsi, null, data, SAFE_TYPE1, "o1", p);
		Thread.sleep(100);
		ObjectInformation o2 = saveObject(u, wsi, null, data, SAFE_TYPE1, "o2", p);
		Thread.sleep(100);
		ObjectInformation o3 = saveObject(u, wsi, null, data, SAFE_TYPE1, "o3", p);
		Thread.sleep(100);
		ObjectInformation o4 = saveObject(u, wsi, null, data, SAFE_TYPE1, "o4", p);
		Thread.sleep(100);
		ObjectInformation o5 = saveObject(u, wsi, null, data, SAFE_TYPE1, "o5", p);
		Date beforeall = new Date(o1.getSavedDate().getTime() - 1);
		Date afterall = new Date(o5.getSavedDate().getTime() + 1);
		
		ListObjectsParameters lop = new ListObjectsParameters(u, Arrays.asList(wsi))
			.withIncludeMetaData(true);
		compareObjectInfo(ws.listObjects(lop),
				Arrays.asList(o1, o2, o3, o4, o5));
		compareObjectInfo(ws.listObjects(lop.withAfter(beforeall).withBefore(afterall)),
				Arrays.asList(o1, o2, o3, o4, o5));
		compareObjectInfo(ws.listObjects(lop.withAfter(afterall).withBefore(beforeall)),
				new ArrayList<ObjectInformation>());
		compareObjectInfo(ws.listObjects(lop.withAfter(o3.getSavedDate()).withBefore(o4.getSavedDate())),
				new ArrayList<ObjectInformation>());
		compareObjectInfo(ws.listObjects(lop.withAfter(o2.getSavedDate()).withBefore(null)),
				Arrays.asList(o3, o4, o5));
		compareObjectInfo(ws.listObjects(lop.withAfter(null).withBefore(o4.getSavedDate())),
				Arrays.asList(o1, o2, o3));
		compareObjectInfo(ws.listObjects(lop.withAfter(o2.getSavedDate()).withBefore(o4.getSavedDate())),
				Arrays.asList(o3));
		compareObjectInfo(ws.listObjects(lop.withAfter(new Date(o2.getSavedDate().getTime() -1))
				.withBefore(o5.getSavedDate())),
				Arrays.asList(o2, o3, o4));
	}
	
	@Test
	public void listObjectsLimit() throws Exception {
		/* Test the limit parameters on list_objects. In particular,
		 * test that hidden/deleted/early version objects are taken into
		 * account properly.
		 */
		
		WorkspaceUser user = new WorkspaceUser("skiplimitUser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("skiplimit1");
		ws.createWorkspace(user, wsi.getName(), false, null, null).getId();
		
		List<WorkspaceSaveObject> objs = new LinkedList<WorkspaceSaveObject>();
		for (int i = 0; i < 200; i++) {
			objs.add(new WorkspaceSaveObject(getRandomName(), new HashMap<String, String>(),
					SAFE_TYPE1, null, new Provenance(user), false));
		}
		ws.saveObjects(user, wsi, objs, new IdReferenceHandlerSetFactory(0));

		//simple tests on full object ranges, depends on natural mongo ordering
		checkObjectLimit(user, wsi, 0, 1, 200);
		checkObjectLimit(user, wsi, 1, 1, 1);
		checkObjectLimit(user, wsi, 99, 1, 99);
		checkObjectLimit(user, wsi, 100, 1, 100);
		checkObjectLimit(user, wsi, 101, 1, 101);
		checkObjectLimit(user, wsi, 300, 1, 200);
		
		objs.clear();
		for (int i = 191; i < 201; i++) {
			final Map<String, String> meta = new HashMap<String, String>();
			meta.put("num", "" + (i/10 + 1));
			objs.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer(i),
					new HashMap<String, String>(), SAFE_TYPE1,
					new WorkspaceUserMetadata(meta), new Provenance(user),
					false));
		}
		ws.saveObjects(user, wsi, objs, new IdReferenceHandlerSetFactory(0));
		
		//test versions
		//skips over the old versions internally since they're interleaved in the last 20 objects
		checkObjectLimit(user, wsi, 195, 1, 195);
		checkObjectLimit(user, wsi, 200, 1, 200);
		checkObjectLimit(user, wsi, 210, 1, 200);
		checkObjectLimit(user, wsi, 220, 1, 200);
		
		
		wsi = new WorkspaceIdentifier("skiplimit2");
		ws.createWorkspace(user, wsi.getName(), false, null, null).getId();
		objs.clear();
		for (int i = 0; i < 20; i++) {
			objs.add(new WorkspaceSaveObject(getRandomName(), new HashMap<String, String>(),
					SAFE_TYPE1, null, new Provenance(user), false));
		}
		ws.saveObjects(user, wsi, objs, new IdReferenceHandlerSetFactory(0));
		
		List<ObjectIdentifier> loi = new LinkedList<ObjectIdentifier>();
		loi.add(new ObjectIdentifier(wsi, 5));
		loi.add(new ObjectIdentifier(wsi, 15));
		ws.setObjectsDeleted(user, loi, true);
		
		loi.clear();
		loi.add(new ObjectIdentifier(wsi, 7));
		loi.add(new ObjectIdentifier(wsi, 17));
		ws.setObjectsHidden(user, loi, true);
		
		objs.clear();
		for (int i = 21; i < 31; i++) {
			objs.add(new WorkspaceSaveObject(getRandomName(), new HashMap<String, String>(),
					SAFE_TYPE1, null, new Provenance(user), false));
		}
		ws.saveObjects(user, wsi, objs, new IdReferenceHandlerSetFactory(0));
		
		//test object pagination with deleted and hidden objects
		checkObjectLimit(user, wsi, 5, 1, 6, nums(5));
		checkObjectLimit(user, wsi, 6, 1, 8, nums(5, 7));
		checkObjectLimit(user, wsi, 12, 1, 14, nums(5, 7));
		checkObjectLimit(user, wsi, 13, 1, 16, nums(5, 7, 15));
		checkObjectLimit(user, wsi, 14, 1, 18, nums(5, 7, 15, 17));
		checkObjectLimit(user, wsi, 14, 1, 18, nums(5, 7, 15, 17));
		checkObjectLimit(user, wsi, 15, 1, 19, nums(5, 7, 15, 17));
		checkObjectLimit(user, wsi, 16, 1, 20, nums(5, 7, 15, 17));
		checkObjectLimit(user, wsi, 17, 1, 21, nums(5, 7, 15, 17));
		checkObjectLimit(user, wsi, 18, 1, 22, nums(5, 7, 15, 17));
		checkObjectLimit(user, wsi, 26, 1, 30, nums(5, 7, 15, 17));
		
		wsi = new WorkspaceIdentifier("skiplimit3");
		ws.createWorkspace(user, wsi.getName(), false, null, null).getId();
		objs.clear();
		for (int i = 1; i < 251; i++) {
			objs.add(new WorkspaceSaveObject(getRandomName(), new HashMap<String, String>(),
					SAFE_TYPE1, null, new Provenance(user), false));
		}
		ws.saveObjects(user, wsi, objs, new IdReferenceHandlerSetFactory(0));
		
		for (int i = 1; i < 251; i++) {
			List<ObjectIdentifier> oi =
					Arrays.asList(new ObjectIdentifier(wsi, i));
			if (i % 2 == 0) {
				ws.setObjectsDeleted(user, oi, true);
			} else {
				ws.setObjectsHidden(user, oi, true);
			}
		}
		objs.clear();
		for (int i = 251; i < 301; i++) {
			objs.add(new WorkspaceSaveObject(getRandomName(), new HashMap<String, String>(),
					SAFE_TYPE1, null, new Provenance(user), false));
		}
		ws.saveObjects(user, wsi, objs, new IdReferenceHandlerSetFactory(0));
		
		//test several rounds of retrieving objects (the minimum # of objects
		//pulled from mongo at a time is 100)
		checkObjectLimit(user, wsi, 5, 251, 255);
		checkObjectLimit(user, wsi, 10, 251, 260);
		checkObjectLimit(user, wsi, 60, 251, 300);
	}
	
	private Set<Long> nums(Integer... nums) {
		Set<Long> ret = new HashSet<Long>();
		for (int i = 0; i < nums.length; i++) {
			ret.add(new Long(nums[i]));
		}
		return ret;
	}
	
	@Test
	public void listObjectsFilterByObjectID() throws Exception {
		/* test filtering list objects results by object ID */
		WorkspaceUser user = new WorkspaceUser("filterID");
		WorkspaceIdentifier wsi1 = new WorkspaceIdentifier("filterID1");
		WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("filterID2");
		
		ws.createWorkspace(user, wsi1.getName(), false, null, null);
		ws.createWorkspace(user, wsi2.getName(), false, null, null);
		
		List<WorkspaceSaveObject> objs = new LinkedList<WorkspaceSaveObject>();
		for (int i = 0; i < 10; i++) {
			objs.add(new WorkspaceSaveObject(getRandomName(), new HashMap<String, String>(),
					SAFE_TYPE1, null, new Provenance(user), false));
		}
		ws.saveObjects(user, wsi1, objs, new IdReferenceHandlerSetFactory(0));
		ws.saveObjects(user, wsi2, objs, new IdReferenceHandlerSetFactory(0));
		
		checkObjectFilter(user, Arrays.asList(wsi1, wsi2), -1L, -1L, 1, 10);
		checkObjectFilter(user, Arrays.asList(wsi1, wsi2), 1L, 11L, 1, 10);
		checkObjectFilter(user, Arrays.asList(wsi1, wsi2), 2L, 9L, 2, 9);
		checkObjectFilter(user, Arrays.asList(wsi1), 2L, 9L, 2, 9);
		checkObjectFilter(user, Arrays.asList(wsi1), 2L, 100L, 2, 10);
		checkObjectFilter(user, Arrays.asList(wsi1), -100L, 1L, 1, 1);
		checkObjectFilter(user, Arrays.asList(wsi1), 3L, 3L, 3, 3);
		checkObjectFilter(user, Arrays.asList(wsi1), 10L, 100L, 10, 10);
		checkObjectFilter(user, Arrays.asList(wsi1), 10L, -1L, 10, 10);
	}
	
	private void checkObjectFilter(
			WorkspaceUser user,
			List<WorkspaceIdentifier> wsis,
			long minObjectID,
			long maxObjectID,
			int minIDexpected,
			int maxIDexpected) 
			throws Exception {
		
		List<ObjectInformation> res = ws.listObjects(
				new ListObjectsParameters(user, wsis)
				.withMinObjectID(minObjectID).withMaxObjectID(maxObjectID));
		assertThat("correct number of objects returned", res.size(),
				is(wsis.size() * (maxIDexpected - minIDexpected + 1)));
		for (ObjectInformation oi: res) {
			if (oi.getObjectId() < minIDexpected ||
					oi.getObjectId() > maxIDexpected) {
				fail(String.format("ObjectID out of test bounds: %s min %s max %s",
						oi.getObjectId(), minIDexpected, maxIDexpected));
			}
		}
	}
	
	@Test
	public void listObjectsSort() throws Exception {
		/* Currently list objects will sort the results if no other filters than the object ID
		 * filters are active. Test that this is true.
		 * Sort is wsid asc, objid asc, ver desc.
		 */
		WorkspaceUser user = new WorkspaceUser("u");
		WorkspaceIdentifier wsi1 = new WorkspaceIdentifier("listsort1");
		ws.createWorkspace(user, wsi1.getName(), false, null, null).getId();
		WorkspaceIdentifier wsi2 = new WorkspaceIdentifier("listsort2");
		ws.createWorkspace(user, wsi2.getName(), false, null, null).getId();
		final Provenance p = new Provenance(user);
		final Map<String, String> meta = ImmutableMap.of("foo", "bar");
		
		// save 6 objects
		saveObject(user, wsi2, meta, MT_MAP, SAFE_TYPE1, "w2o1", p);
		saveObject(user, wsi2, meta, MT_MAP, SAFE_TYPE1, "w2o2", p);
		saveObject(user, wsi2, meta, MT_MAP, SAFE_TYPE1, "w2o3", p);
		saveObject(user, wsi1, meta, MT_MAP, SAFE_TYPE1, "w1o1", p);
		saveObject(user, wsi1, meta, MT_MAP, SAFE_TYPE1, "w1o2", p);
		saveObject(user, wsi1, meta, MT_MAP, SAFE_TYPE1, "w1o3", p);
		
		// more or less randomly saved versions on top of the 6 objects
		saveObject(user, wsi2, meta, MT_MAP, SAFE_TYPE1, "w2o3", p);
		saveObject(user, wsi2, meta, MT_MAP, SAFE_TYPE1, "w2o1", p);
		saveObject(user, wsi1, meta, MT_MAP, SAFE_TYPE1, "w1o2", p);
		saveObject(user, wsi1, meta, MT_MAP, SAFE_TYPE1, "w1o2", p);
		saveObject(user, wsi2, meta, MT_MAP, SAFE_TYPE1, "w2o3", p);
		saveObject(user, wsi1, meta, MT_MAP, SAFE_TYPE1, "w1o3", p);
		saveObject(user, wsi2, meta, MT_MAP, SAFE_TYPE1, "w2o2", p);
		saveObject(user, wsi1, meta, MT_MAP, SAFE_TYPE1, "w1o1", p);
		saveObject(user, wsi2, meta, MT_MAP, SAFE_TYPE1, "w2o2", p);
		saveObject(user, wsi1, meta, MT_MAP, SAFE_TYPE1, "w1o1", p);
		saveObject(user, wsi1, meta, MT_MAP, SAFE_TYPE1, "w1o3", p);
		saveObject(user, wsi2, meta, MT_MAP, SAFE_TYPE1, "w2o1", p);
		
		// sorted, with and without object id filters
		assertOrdered(new ListObjectsParameters(Arrays.asList(wsi1, wsi2)), true);
		assertOrdered(new ListObjectsParameters(Arrays.asList(wsi1, wsi2))
				.withMaxObjectID(6L).withMinObjectID(1L), true);
		
		//unsorted (at least with descending versions)
		// type filter
		assertOrdered(new ListObjectsParameters(user, SAFE_TYPE1), false);
		// after date filter
		assertOrdered(new ListObjectsParameters(Arrays.asList(wsi1, wsi2))
				.withAfter(Date.from(Instant.now().minusSeconds(100))), false);
		// before date filter
		assertOrdered(new ListObjectsParameters(Arrays.asList(wsi1, wsi2))
				.withBefore(Date.from(Instant.now())), false);
		// user filter
		assertOrdered(new ListObjectsParameters(Arrays.asList(wsi1, wsi2))
				.withSavers(Arrays.asList(user)), false);
		// meta filter
		assertOrdered(new ListObjectsParameters(Arrays.asList(wsi1, wsi2))
				.withMetadata(new WorkspaceUserMetadata(meta)), false);
	}

	private void assertOrdered(final ListObjectsParameters params, final boolean expectOrdered)
			throws Exception {
		final List<ObjectInformation> objs = ws.listObjects(params.withShowAllVersions(true));
//		System.out.println("printing sorted objs");
//		for (final ObjectInformation o: objs) {
//			System.out.println(o);
//		}
		boolean isOrdered = true;
		final Iterator<ObjectInformation> iter = objs.iterator();
		for (int ws = 1; ws < 3; ws++) {
			for (int obj = 1; obj < 4; obj++) {
				for (int ver = 3; ver > 0; ver--) {
					final ObjectInformation oi = iter.next();
					if (ws != oi.getWorkspaceId() ||
							obj != oi.getObjectId() ||
							ver != oi.getVersion()) {
						isOrdered = false;
						if (expectOrdered) {
							fail(String.format(
									"Expected ordered list. Failed at %s/%s/%s, got %s/%s/%s",
									ws, obj, ver,
									oi.getWorkspaceId(), oi.getObjectId(), oi.getVersion()));
						}
					}
				}
			}
		}
		if (!expectOrdered && isOrdered) {
			fail("Expected unordered list, was ordered.");
		}
	}

	@Test
	public void getObjectSubdata() throws Exception {
		/* note most tests are performed at the same time as getObjects, so
		 * only issues specific to subsets are tested here
		 */
		WorkspaceUser user = new WorkspaceUser("subUser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("subData");
		WorkspaceUser user2 = new WorkspaceUser("subUser2");
		long wsid1 = ws.createWorkspace(user, wsi.getName(), false, null, null).getId();
		
		TypeDefId reftype = new TypeDefId(new TypeDefName("CopyRev", "RefType"), 1, 0);
		
		Map<String, String> pmeta = new HashMap<String, String>();
		pmeta.put("metastuff", "meta");
		Map<String, String> pmeta2 = new HashMap<String, String>();
		pmeta2.put("meta2", "my hovercraft is full of eels");
		WorkspaceUserMetadata meta = new WorkspaceUserMetadata(pmeta);
		WorkspaceUserMetadata meta2 = new WorkspaceUserMetadata(pmeta2);
		
		Provenance p1 = new Provenance(user);
		p1.addAction(new ProvenanceAction().withDescription("provenance 1")
				.withWorkspaceObjects(Arrays.asList("subData/auto1")));
		Provenance p2 = new Provenance(user);
		p2.addAction(new ProvenanceAction().withDescription("provenance 2")
				.withWorkspaceObjects(Arrays.asList("subData/auto2")));

		Map<String, Object> data1 = createData(
				"{\"map\": {\"id1\": {\"id\": 1," +
				"					  \"thing\": \"foo\"}," +
				"			\"id2\": {\"id\": 2," +
				"					  \"thing\": \"foo2\"}," +
				"			\"id3\": {\"id\": 3," +
				"					  \"thing\": \"foo3\"}" +
				"			}," +
				" \"refs\": [\"subData/auto1\"]" +
				"}"
				);
		
		Map<String, Object> data2 = createData(
				"{\"array\": [{\"id\": 1," +
				"			   \"thing\": \"foo\"}," +
				"			  {\"id\": 2," +
				"			   \"thing\": \"foo2\"}," +
				"			  {\"id\": 3," +
				"			   \"thing\": \"foo3\"}" +
				"			  ]," +
				" \"refs\": [\"subData/auto2\"]" +
				"}"
				);
		
		Map<String, Object> data3 = createData(
				"{\"array\": [{\"id\": 1," +
				"			   \"thing\": \"foo\"}," +
				"			  {\"id\": 2," +
				"			   \"thing\": \"foo2\"}," +
				"			   null," +
				"			  {\"id\": 4," +
				"			   \"thing\": \"foo4\"}" +
				"			  ]," +
				" \"refs\": [\"subData/auto2\"]" +
				"}"
				);
		
		ws.saveObjects(user, wsi, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto1"), data1, SAFE_TYPE1,
						meta, new Provenance(user), false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto2"), data1, SAFE_TYPE1,
						meta, new Provenance(user), false)),
				getIdFactory());
		ObjectInformation o1 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("o1"), data1, reftype, meta,
				p1, false)), getIdFactory()).get(0);
		ObjectInformation o2 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("o2"), data2, reftype, meta2,
				p2, false)), getIdFactory()).get(0);
		ObjectInformation o3 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("o3"), data3, reftype, meta,
				p2, false)), getIdFactory()).get(0);
		ObjectIdentifier oident1 = new ObjectIdentifier(wsi, "o1");
		ObjectIdentifier oident2 = new ObjectIdentifier(wsi, 4);
		ObjectIdentifier oident3 = ObjectIdentifier.parseObjectReference("subData/o3");
		
		List<String> refs1 = Arrays.asList(wsid1 + "/1/1");
		Map<String, String> refmap1 = new HashMap<String, String>();
		refmap1.put("subData/auto1", wsid1 + "/1/1");
		List<String> refs2 = Arrays.asList(wsid1 + "/2/1");
		Map<String, String> refmap2 = new HashMap<String, String>();
		refmap2.put("subData/auto2", wsid1 + "/2/1");
		
		List<WorkspaceObjectData> got = ws.getObjects(user, 
				new LinkedList<ObjectIdentifier>(Arrays.asList(
				new ObjIDWithRefPathAndSubset(oident1, null, new SubsetSelection(
						Arrays.asList("/map/id3", "/map/id1"))),
				new ObjIDWithRefPathAndSubset(oident1, null, new SubsetSelection(
						Arrays.asList("/map/id2"))),
				new ObjIDWithRefPathAndSubset(oident2, null, new SubsetSelection(
						Arrays.asList("/array/2", "/array/0"))),
				new ObjIDWithRefPathAndSubset(oident3, null, new SubsetSelection(
						Arrays.asList("/array/2", "/array/0", "/array/3"))))));
		Map<String, Object> expdata1 = createData(
				"{\"map\": {\"id1\": {\"id\": 1," +
				"					  \"thing\": \"foo\"}," +
				"			\"id3\": {\"id\": 3," +
				"					  \"thing\": \"foo3\"}" +
				"			}" +
				"}"
				);
		Map<String, Object> expdata2 = createData(
				"{\"map\": {\"id2\": {\"id\": 2," +
				"					  \"thing\": \"foo2\"}" +
				"			}" +
				"}"
				);
		Map<String, Object> expdata3 = createData(
				"{\"array\": [{\"id\": 1," +
				"			   \"thing\": \"foo\"}," +
				"			  {\"id\": 3," +
				"			   \"thing\": \"foo3\"}" +
				"			  ]" +
				"}"
				);
		Map<String, Object> expdata4 = createData(
				"{\"array\": [{\"id\": 1," +
				"			   \"thing\": \"foo\"}," +
				"			   null," +
				"			  {\"id\": 4," +
				"			   \"thing\": \"foo4\"}" +
				"			  ]" +
				"}"
				);
		try {
			compareObjectAndInfo(got.get(0), o1, p1, expdata1, refs1, refmap1);
			compareObjectAndInfo(got.get(1), o1, p1, expdata2, refs1, refmap1);
			compareObjectAndInfo(got.get(2), o2, p2, expdata3, refs2, refmap2);
			compareObjectAndInfo(got.get(3), o3, p2, expdata4, refs2, refmap2);
		} finally {
			destroyGetObjectsResources(got);
		}
		
		// new test for extractor that fails on an array OOB
		failGetSubset(user, Arrays.asList(
				new ObjIDWithRefPathAndSubset(oident2, null, new SubsetSelection(
						Arrays.asList("/array/3", "/array/0")))),
				new TypedObjectExtractionException(
						"Invalid selection: no array element exists at position '3', at: /array/3"));
		
		got = ws.getObjects(user, new ArrayList<ObjectIdentifier>(Arrays.asList(
				new ObjIDWithRefPathAndSubset(oident1, null, new SubsetSelection(
						Arrays.asList("/map/*/thing"))),
				new ObjIDWithRefPathAndSubset(oident2, null, new SubsetSelection(
						Arrays.asList("/array/[*]/thing"))))));
		expdata1 = createData(
				"{\"map\": {\"id1\": {\"thing\": \"foo\"}," +
				"			\"id2\": {\"thing\": \"foo2\"}," +
				"			\"id3\": {\"thing\": \"foo3\"}" +
				"			}" +
				"}"
				);
		
		expdata2 = createData(
				"{\"array\": [{\"thing\": \"foo\"}," +
				"			  {\"thing\": \"foo2\"}," +
				"			  {\"thing\": \"foo3\"}" +
				"			  ]" +
				"}"
				);
		try {
			compareObjectAndInfo(got.get(0), o1, p1, expdata1, refs1, refmap1);
			compareObjectAndInfo(got.get(1), o2, p2, expdata2, refs2, refmap2);
		} finally {
			destroyGetObjectsResources(got);
		}
		
		failGetSubset(user, Arrays.asList(
				new ObjIDWithRefPathAndSubset(oident1, null, new SubsetSelection(
						Arrays.asList("/map/id1/id/5")))),
				new TypedObjectExtractionException(
						"Invalid selection: the path given specifies fields or elements that do not exist "
						+ "because data at this location is a scalar value (i.e. string, integer, float), at: /map/id1/id"));
		failGetSubset(user2, Arrays.asList(
				new ObjIDWithRefPathAndSubset(oident1, null, new SubsetSelection(
						Arrays.asList("/map/*/thing")))),
				new InaccessibleObjectException("Object o1 cannot be accessed: User subUser2 " +
						"may not read workspace subData", null));
		
		try {
			ws.getObjects(user2, new LinkedList<ObjectIdentifier>(
					Arrays.asList(new ObjIDWithRefPathAndSubset(
					new ObjectIdentifier(wsi, 2), null, null))));
			fail("Able to get obj data from private workspace");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception message", ioe.getLocalizedMessage(),
					is("Object 2 cannot be accessed: User subUser2 may not read workspace subData"));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is((ObjectIdentifier) new ObjIDWithRefPathAndSubset(
							new ObjectIdentifier(wsi, 2), null, null)));
		}
	}

	@Test
	public void getReferencingObjects() throws Exception {
		WorkspaceUser user1 = new WorkspaceUser("refUser");
		WorkspaceUser user2 = new WorkspaceUser("refUser2");
		WorkspaceIdentifier wsitar1 = new WorkspaceIdentifier("refstarget1");
		WorkspaceIdentifier wsitar2 = new WorkspaceIdentifier("refstarget2");
		WorkspaceIdentifier wsisrc1 = new WorkspaceIdentifier("refssource1");
		WorkspaceIdentifier wsisrc2 = new WorkspaceIdentifier("refssource2");
		WorkspaceIdentifier wsisrc2noaccess = new WorkspaceIdentifier("refssource2noaccess");
		WorkspaceIdentifier wsisrcdel1 = new WorkspaceIdentifier("refssourcedel1");
		WorkspaceIdentifier wsisrc2gl = new WorkspaceIdentifier("refssourcegl");
		
		long wsid = ws.createWorkspace(user1, wsitar1.getName(), false, null, null).getId();
		ws.setPermissions(user1, wsitar1, Arrays.asList(user2), Permission.READ);
		ws.createWorkspace(user2, wsitar2.getName(), false, null, null);
		ws.setPermissions(user2, wsitar2, Arrays.asList(user1), Permission.READ);
		ws.createWorkspace(user1, wsisrc1.getName(), false, null, null);
		ws.createWorkspace(user2, wsisrc2.getName(), false, null, null);
		ws.setPermissions(user2, wsisrc2, Arrays.asList(user1), Permission.READ);
		ws.createWorkspace(user2, wsisrc2noaccess.getName(), false, null, null);
		ws.createWorkspace(user1, wsisrcdel1.getName(), false, null, null);
		ws.createWorkspace(user2, wsisrc2gl.getName(), true, null, null);
		
		TypeDefId reftype = new TypeDefId(new TypeDefName("CopyRev", "RefType"), 1, 0);
		
		Map<String, String> pmeta1 = new HashMap<String, String>();
		pmeta1.put("metastuff", "meta");
		Map<String, String> pmeta2 = new HashMap<String, String>();
		pmeta2.put("meta2", "my hovercraft is full of eels");
		WorkspaceUserMetadata meta1 = new WorkspaceUserMetadata(pmeta1);
		WorkspaceUserMetadata meta2 = new WorkspaceUserMetadata(pmeta2);
		

		Map<String, Object> mtdata = new HashMap<String, Object>();
		Provenance p1 = new Provenance(user1);
		
		//test objects with no references or no accessible references
		ws.saveObjects(user1, wsitar1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("norefs"), mtdata,
						SAFE_TYPE1, null, p1, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("deletedref"),
						mtdata, SAFE_TYPE1, null, p1, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("unreadableref"),
						mtdata, SAFE_TYPE1, null, p1, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("deletedprovref"),
						mtdata, SAFE_TYPE1, null, p1, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("unreadableprovref"),
						mtdata, SAFE_TYPE1, null, p1, false)), getIdFactory());
		
		Map<String, Object> refdata = new HashMap<String, Object>();
		
		refdata.put("refs", Arrays.asList("refstarget1/deletedref"));
		ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("delrefptr"),
						refdata, reftype, null, p1, false)), getIdFactory());
		ws.setObjectsDeleted(user1, Arrays.asList(
				new ObjectIdentifier(wsisrc1, "delrefptr")), true);
		refdata.put("refs", Arrays.asList("refstarget1/unreadableref"));
		ws.saveObjects(user2, wsisrc2noaccess, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("unreadrefptr"),
						refdata, reftype, null, p1, false)), getIdFactory());
		
		ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("deletedprovrefptr"),
						mtdata, SAFE_TYPE1, null,
						new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/deletedprovref"))),
						false)), getIdFactory());
		ws.setObjectsDeleted(user1, Arrays.asList(
				new ObjectIdentifier(wsisrc1, "deletedprovrefptr")), true);
		ws.saveObjects(user2, wsisrc2noaccess, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("unreadableprovrefptr"),
						mtdata, SAFE_TYPE1, null,
						new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/unreadableprovref"))),
						false)), getIdFactory());
		
		List<Set<ObjectInformation>> mtrefs = new ArrayList<Set<ObjectInformation>>();
		mtrefs.add(new HashSet<ObjectInformation>());
		for (String name: Arrays.asList("norefs", "deletedref", "unreadableref",
				"deletedprovref", "unreadableprovref")) {
			assertThat("ref lists empty", ws.getReferencingObjects(user1,
					Arrays.asList(new ObjectIdentifier(wsitar1, name))),
					is(mtrefs));
		}
				
		ws.saveObjects(user1, wsitar1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("stk"), mtdata, SAFE_TYPE1,
						meta1, p1, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("stk"), mtdata, SAFE_TYPE1,
						meta2, p1, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("single"), mtdata, SAFE_TYPE1,
						meta1, p1, false)), getIdFactory());
		ws.saveObjects(user2, wsitar2, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("stk2"), mtdata, SAFE_TYPE1,
						meta1, p1, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("stk2"), mtdata, SAFE_TYPE1,
						meta2, p1, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("single2"), mtdata, SAFE_TYPE1,
						meta1, p1, false)), getIdFactory());
		
		refdata.put("refs", Arrays.asList("refstarget1/stk/1"));
		ObjectInformation stdref1 = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("stdref"), refdata,
						reftype, meta1,
						new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/stk/1"))), false)),
						getIdFactory()).get(0);
		refdata.put("refs", Arrays.asList("refstarget1/stk/2"));
		ObjectInformation stdref2 = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("stdref"), refdata,
						reftype, meta2, new Provenance(user1), false)), 
						getIdFactory()).get(0);
		refdata.put("refs", Arrays.asList("refstarget1/stk"));
		ObjectInformation hiddenref = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("hiddenref"), refdata,
						reftype, meta1, new Provenance(user1), true)),
						getIdFactory()).get(0);
		refdata.put("refs", Arrays.asList("refstarget2/stk2"));
		@SuppressWarnings("unused")
		ObjectInformation delref = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("delref"), refdata,
						reftype, meta1,
						new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/stk/2"))), true)),
						getIdFactory()).get(0);
		ws.setObjectsDeleted(user1, Arrays.asList(new ObjectIdentifier(wsisrc1, "delref")), true);
		
		refdata.put("refs", Arrays.asList("refstarget1/single"));
		ObjectInformation readable = ws.saveObjects(user2, wsisrc2, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("readable"), refdata,
						reftype, meta2, new Provenance(user2), true)),
						getIdFactory()).get(0);
		
		refdata.put("refs", Arrays.asList("refstarget2/stk2/2"));
		@SuppressWarnings("unused")
		ObjectInformation unreadable = ws.saveObjects(user2, wsisrc2noaccess, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("unreadable"), refdata,
						reftype, meta1, new Provenance(user2), true)),
						getIdFactory()).get(0);
		
		refdata.put("refs", Arrays.asList("refstarget2/single2/1"));
		@SuppressWarnings("unused")
		ObjectInformation wsdeletedreadable1 = ws.saveObjects(user1, wsisrcdel1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("wsdeletedreadable1"), refdata,
						reftype, meta2, new Provenance(user1), false)),
						getIdFactory()).get(0);
		ws.setWorkspaceDeleted(user1, wsisrcdel1, true);
		
		refdata.put("refs", Arrays.asList("refstarget2/stk2/1"));
		ObjectInformation globalrd = ws.saveObjects(user2, wsisrc2gl, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("globalrd"), refdata,
						reftype, meta1, new Provenance(user2).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/single/1"))), false)),
						getIdFactory()).get(0);
		
		List<ObjectIdentifier> objs = Arrays.asList(
				new ObjectIdentifier(wsitar1, "stk"),
				new ObjectIdentifier(wsitar1, "stk", 2),
				new ObjectIdentifier(wsitar1, "stk", 1));
		assertThat("got correct refs", ws.getReferencingObjects(user1, objs),
				is(Arrays.asList(
						oiset(stdref2, hiddenref),
						oiset(stdref2, hiddenref),
						oiset(stdref1))));
		@SuppressWarnings("deprecation")
		List<Integer> d = ws.getReferencingObjectCounts(user1, objs);
		assertThat("got correct refcounts", d,
				is(Arrays.asList(3, 3, 1)));
		
		Set<ObjectInformation> mtoiset = new HashSet<ObjectInformation>();
		
		objs = Arrays.asList(
				new ObjectIdentifier(wsitar2, "stk2"),
				new ObjectIdentifier(wsitar2, "stk2", 2),
				new ObjectIdentifier(wsitar2, "stk2", 1));
		assertThat("got correct refs", ws.getReferencingObjects(user1, objs),
				is(Arrays.asList(
						mtoiset,
						mtoiset,
						oiset(globalrd))));
		@SuppressWarnings("deprecation")
		List<Integer> d2 = ws.getReferencingObjectCounts(user1, objs);
		assertThat("got correct refcounts", d2,
				is(Arrays.asList(2, 2, 1)));
		
		objs = Arrays.asList(
				new ObjectIdentifier(wsitar1, "single"),
				new ObjectIdentifier(wsitar1, "single", 1),
				new ObjectIdentifier(wsitar2, "single2"),
				new ObjectIdentifier(wsitar2, "single2", 1));
		assertThat("got correct refs", ws.getReferencingObjects(user1,objs),
				is(Arrays.asList(
						oiset(readable, globalrd),
						oiset(readable, globalrd),
						mtoiset,
						mtoiset)));
		@SuppressWarnings("deprecation")
		List<Integer> d3 = ws.getReferencingObjectCounts(user1, objs);
		assertThat("got correct refcounts", d3,
				is(Arrays.asList(2, 2, 1, 1)));
		
		
		ObjectInformation pstdref1 = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("pstdref"), mtdata,
						SAFE_TYPE1, meta1,
						new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/stk/1"))), false)),
						getIdFactory()).get(0);
		ObjectInformation pstdref2 = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("pstdref"), mtdata,
						SAFE_TYPE1, meta2, new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/stk/2"))), false)),
						getIdFactory()).get(0);
		ObjectInformation phiddenref = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("phiddenref"), mtdata,
						SAFE_TYPE1, meta1, new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/stk"))), true)),
						getIdFactory()).get(0);
		@SuppressWarnings("unused")
		ObjectInformation pdelref = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("pdelref"), mtdata,
						SAFE_TYPE1, meta1,
						new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget2/stk2"))), true)),
						getIdFactory()).get(0);
		ws.setObjectsDeleted(user1, Arrays.asList(new ObjectIdentifier(wsisrc1, "pdelref")), true);
		
		ObjectInformation preadable = ws.saveObjects(user2, wsisrc2, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("preadable"), mtdata,
						SAFE_TYPE1, meta2, new Provenance(user2).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/single"))), true)),
						getIdFactory()).get(0);
		
		@SuppressWarnings("unused")
		ObjectInformation punreadable = ws.saveObjects(user2, wsisrc2noaccess, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("punreadable"), mtdata,
						SAFE_TYPE1, meta1, new Provenance(user2).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget2/stk2/2"))), true)),
						getIdFactory()).get(0);
		
		ws.setWorkspaceDeleted(user1, wsisrcdel1, false);
		@SuppressWarnings("unused")
		ObjectInformation pwsdeletedreadable1 = ws.saveObjects(user1, wsisrcdel1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("pwsdeletedreadable1"), mtdata,
						SAFE_TYPE1, meta2, new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget2/single2/1"))), false)),
						getIdFactory()).get(0);
		ws.setWorkspaceDeleted(user1, wsisrcdel1, true);
		
		ObjectInformation pglobalrd = ws.saveObjects(user2, wsisrc2gl, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("pglobalrd"), mtdata,
						SAFE_TYPE1, meta1, new Provenance(user2).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget2/stk2/1"))), false)),
						getIdFactory()).get(0);
		
		objs = Arrays.asList(
				new ObjectIdentifier(wsitar1, "stk"),
				new ObjectIdentifier(wsitar1, "stk", 2),
				new ObjectIdentifier(wsitar1, "stk", 1));
		assertThat("got correct refs", ws.getReferencingObjects(user1, objs),
				is(Arrays.asList(
						oiset(stdref2, hiddenref, pstdref2, phiddenref),
						oiset(stdref2, hiddenref, pstdref2, phiddenref),
						oiset(stdref1, pstdref1))));
		@SuppressWarnings("deprecation")
		List<Integer> d4 = ws.getReferencingObjectCounts(user1, objs);
		assertThat("got correct refcounts", d4,
				is(Arrays.asList(5, 5, 2)));
		
		objs = Arrays.asList(
				new ObjectIdentifier(wsitar2, "stk2"),
				new ObjectIdentifier(wsitar2, "stk2", 2),
				new ObjectIdentifier(wsitar2, "stk2", 1));
		assertThat("got correct refs", ws.getReferencingObjects(user1, objs),
				is(Arrays.asList(
						mtoiset,
						mtoiset,
						oiset(globalrd, pglobalrd))));
		@SuppressWarnings("deprecation")
		List<Integer> d5 = ws.getReferencingObjectCounts(user1, objs);
		assertThat("got correct refcounts", d5,
				is(Arrays.asList(4, 4, 2)));
		
		objs = Arrays.asList(
				new ObjectIdentifier(wsitar1, "single"),
				new ObjectIdentifier(wsitar1, "single", 1),
				new ObjectIdentifier(wsitar2, "single2"),
				new ObjectIdentifier(wsitar2, "single2", 1));
		assertThat("got correct refs", ws.getReferencingObjects(user1, objs),
				is(Arrays.asList(
						oiset(readable, globalrd, preadable),
						oiset(readable, globalrd, preadable),
						mtoiset,
						mtoiset)));
		@SuppressWarnings("deprecation")
		List<Integer> d6 = ws.getReferencingObjectCounts(user1, objs);
		assertThat("got correct refcounts", d6,
				is(Arrays.asList(3, 3, 2, 2)));
		
		try {
			ws.getReferencingObjects(user2, Arrays.asList(
					new ObjectIdentifier(wsisrc1, 1)));
			fail("Able to get ref obj data from private workspace");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception message", ioe.getLocalizedMessage(),
					is("Object 1 cannot be accessed: User refUser2 may not read workspace refssource1"));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is(new ObjectIdentifier(wsisrc1, 1)));
		}
		try {
			
			@SuppressWarnings({ "deprecation", "unused" })
			List<Integer> d7 = ws.getReferencingObjectCounts(user2, Arrays.asList(
					new ObjectIdentifier(wsisrc1, 1)));
			fail("Able to get ref obj count from private workspace");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception message", ioe.getLocalizedMessage(),
					is("Object 1 cannot be accessed: User refUser2 may not read workspace refssource1"));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is(new ObjectIdentifier(wsisrc1, 1)));
		}
		
		try {
			@SuppressWarnings({ "deprecation", "unused" })
			List<Integer> d8 = ws.getReferencingObjectCounts(user1, Arrays.asList(
					new ObjectIdentifier(wsitar1, "single", 2)));
			fail("Able to get ref obj count for non-existant obj version");
		} catch (NoSuchObjectException ioe) {
			assertThat("correct exception message", ioe.getLocalizedMessage(),
					is("No object with id 7 (name single) and version 2 exists in workspace " + wsid));
			ObjectIDResolvedWS resobj = ioe.getResolvedInaccessibleObject();
			assertThat("correct ws id in returned oid", resobj.getWorkspaceIdentifier().getID(),
					is(wsid));
			assertThat("correct ws name in returned oid", resobj.getWorkspaceIdentifier().getName(),
					is(wsitar1.getName()));
			assertThat("correct objid in returned oid", resobj.getId(),
					is((Long) null));
			assertThat("correct obj name in returned oid", resobj.getName(),
					is("single"));
			assertThat("correct obj ver in returned oid", resobj.getVersion(),
					is(2));
		}
		
		ws.setGlobalPermission(user2, wsisrc2gl, Permission.NONE);
	}
	
	@Test
	public void getObjectsMixedCalls() throws Exception {
		WorkspaceUser user1 = new WorkspaceUser("u1");
		WorkspaceUser user2 = new WorkspaceUser("u2");
		WorkspaceIdentifier wsaccessible = new WorkspaceIdentifier("accessible");
		WorkspaceIdentifier wshidden = new WorkspaceIdentifier("hidden");
		ws.createWorkspace(user1, wsaccessible.getName(), false, null, null);
		ws.setPermissions(user1, wsaccessible, Arrays.asList(user2),
				Permission.WRITE);
		ws.createWorkspace(user2, wshidden.getName(), false, null, null);
		
		Map<String, Object> data1 = createData(
				"{\"map\": {\"id1\": {\"id\": 1," +
				"					  \"thing\": \"foo\"}," +
				"			\"id2\": {\"id\": 2," +
				"					  \"thing\": \"foo2\"}," +
				"			\"id3\": {\"id\": 3," +
				"					  \"thing\": \"foo3\"}" +
				"			}" +
				"}"
				);
		Map<String, Object> data1id1 = createData(
				"{\"map\": {" +
				"			\"id1\": {\"id\": 1," +
				"					  \"thing\": \"foo\"}" +
				"			}" +
				"}"
				);
		Map<String, Object> data1id2 = createData(
				"{\"map\": {" +
				"			\"id2\": {\"id\": 2," +
				"					  \"thing\": \"foo2\"}" +
				"			}" +
				"}"
				);
		Map<String, Object> data1id3 = createData(
				"{\"map\": {" +
				"			\"id3\": {\"id\": 3," +
				"					  \"thing\": \"foo3\"}" +
				"			}" +
				"}"
				);
		
		String data2string =
				"{\"map\": {\"id21\": {\"id\": 1," +
				"					  \"thing\": \"foo\"}," +
				"			\"id22\": {\"id\": 2," +
				"					  \"thing\": \"foo2\"}," +
				"			\"id23\": {\"id\": 3," +
				"					  \"thing\": \"foo3\"}" +
				"			}," +
				" \"refs\": [\"hidden/leaf1\"]" +
				"}";
		Map<String, Object> data2 = createData(data2string);
		Map<String, Object> data2resolved = createData(
				data2string.replace("hidden/leaf1", "2/1/1"));
		
		Map<String, Object> data2id21 = createData(
				"{\"map\": {" +
				"			\"id21\": {\"id\": 1," +
				"					  \"thing\": \"foo\"}" +
				"			}" +
				"}"
				);
		
		Map<String, Object> data2id22 = createData(
				"{\"map\": {" +
				"			\"id22\": {\"id\": 2," +
				"					  \"thing\": \"foo2\"}" +
				"			}" +
				"}"
				);
		Map<String, Object> data2id23 = createData(
				"{\"map\": {" +
				"			\"id23\": {\"id\": 3," +
				"					  \"thing\": \"foo3\"}" +
				"			}" +
				"}"
				);
		Map<String, Object> data2map = createData(
				"{\"map\": {\"id21\": {\"id\": 1," +
				"					  \"thing\": \"foo\"}," +
				"			\"id22\": {\"id\": 2," +
				"					  \"thing\": \"foo2\"}," +
				"			\"id23\": {\"id\": 3," +
				"					  \"thing\": \"foo3\"}" +
				"			}" +
				"}");
		
		Provenance pU2_1 = new Provenance(user2);
		pU2_1.addAction(new ProvenanceAction().withCaller("random data"));
		
		Provenance pU1_1 = new Provenance(user2);
		pU1_1.addAction(new ProvenanceAction().withMethod("method"));
		
		Provenance pU2_2 = new Provenance(user2);
		pU2_2.addAction(new ProvenanceAction().withDescription("a desc"));
		
		Map<String, String> meta1 = new HashMap<String, String>();
		meta1.put("some", "very special metadata");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("some", "very special metadata2");
		
		ObjectInformation leaf1 = saveObject(user2, wshidden, meta1, data1,
				SAFE_TYPE1, "leaf1", pU2_1);
		ObjectIdentifier leaf1oi = new ObjectIdentifier(wshidden, "leaf1");
		ObjectInformation leaf2 = saveObject(user1, wsaccessible, meta2, data2,
				SAFE_TYPE1, "leaf2", pU1_1);
		ObjectIdentifier leaf2oi = new ObjectIdentifier(wsaccessible, "leaf2");
		
		TypeDefId reftype = new TypeDefId(
				new TypeDefName("CopyRev", "RefType"), 1, 0);
		
		
		ObjectInformation simpleref = saveObject(user2, wsaccessible, meta2,
				data2, reftype, "simpleref", pU2_2);
		ObjectIdentifier simplerefoi = new ObjectIdentifier(
				wsaccessible, "simpleref");
		
		// test single call with different types of operations
		final List<String> refs = Arrays.asList("2/1/1");
		final Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("hidden/leaf1", "2/1/1");
		List<WorkspaceObjectData> lwod = ws.getObjects(user1, Arrays.asList(
				leaf2oi,
				simplerefoi,
				(ObjectIdentifier) new ObjectIDWithRefPath(simplerefoi, Arrays.asList(leaf1oi)),
				(ObjectIdentifier) new ObjectIDWithRefPath(leaf1oi), // auto lookup
				(ObjectIdentifier) new ObjIDWithRefPathAndSubset(leaf2oi, null,
						new SubsetSelection(Arrays.asList("/map/id22"))),
				(ObjectIdentifier) new ObjIDWithRefPathAndSubset(leaf2oi, null,
						new SubsetSelection(Arrays.asList("/map"))),
				(ObjectIdentifier) new ObjIDWithRefPathAndSubset(simplerefoi, null,
						new SubsetSelection(Arrays.asList("/map/id23"))),
				(ObjectIdentifier) new ObjIDWithRefPathAndSubset(simplerefoi,
						Arrays.asList(leaf1oi),
						new SubsetSelection(Arrays.asList("/map/id1"))),
				(ObjectIdentifier) new ObjIDWithRefPathAndSubset(leaf1oi, // auto lookup
						new SubsetSelection(Arrays.asList("/map/id2"))),
				(ObjectIdentifier) new ObjIDWithRefPathAndSubset(simplerefoi,
						Arrays.asList(leaf1oi),
						new SubsetSelection(Arrays.asList("/map/id3"))),
				(ObjectIdentifier) new ObjectIDWithRefPath(leaf2oi), // auto lookup
				(ObjectIdentifier) new ObjIDWithRefPathAndSubset(simplerefoi, //auto lookup
						new SubsetSelection(Arrays.asList("/map/id21")))
				));
		leaf1 = leaf1.updateReferencePath(Arrays.asList(
				new Reference(1, 2, 1), new Reference(2, 1, 1)));
		try {
			assertThat("correct list size", lwod.size(), is(12));
			compareObjectAndInfo(lwod.get(0), leaf2, pU1_1, data2, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(1), simpleref, pU2_2, data2resolved, refs, refmap);
			compareObjectAndInfo(lwod.get(2), leaf1, pU2_1, data1, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(3), leaf1, pU2_1, data1, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(4), leaf2, pU1_1, data2id22, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(5), leaf2, pU1_1, data2map, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(6), simpleref, pU2_2, data2id23, refs, refmap);
			compareObjectAndInfo(lwod.get(7), leaf1, pU2_1, data1id1, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(8), leaf1, pU2_1, data1id2, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(9), leaf1, pU2_1, data1id3, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(10), leaf2, pU1_1, data2, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(11), simpleref, pU2_2, data2id21, refs, refmap);
		} finally {
			destroyGetObjectsResources(lwod);
		}
		// test getting provenance only
		lwod = ws.getObjects(user1, Arrays.asList(
				leaf2oi,
				simplerefoi,
				(ObjectIdentifier) new ObjectIDWithRefPath(leaf1oi), // auto lookup
				(ObjectIdentifier) new ObjectIDWithRefPath(simplerefoi, Arrays.asList(leaf1oi)),
				(ObjectIdentifier) new ObjectIDWithRefPath(leaf2oi) // auto lookup
				), true);
		try {
			assertThat("correct list size", lwod.size(), is(5));
			compareObjectAndInfo(lwod.get(0), leaf2, pU1_1, null, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(1), simpleref, pU2_2, null, refs, refmap);
			compareObjectAndInfo(lwod.get(2), leaf1, pU2_1, null, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(3), leaf1, pU2_1, null, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(4), leaf2, pU1_1, null, MT_LIST, MT_MAP);
		} finally {
			destroyGetObjectsResources(lwod);
		}
		// test getting info only
		final List<ObjectInformation> loi = ws.getObjectInformation(user1, Arrays.asList(
				leaf2oi,
				simplerefoi,
				(ObjectIdentifier) new ObjectIDWithRefPath(leaf1oi), // auto lookup
				(ObjectIdentifier) new ObjectIDWithRefPath(simplerefoi, Arrays.asList(leaf1oi)),
				(ObjectIdentifier) new ObjectIDWithRefPath(simplerefoi, null),
				(ObjectIdentifier) new ObjectIDWithRefPath(leaf2oi) // auto lookup
				), true, false);
		assertThat("object info different", loi,
				is(Arrays.asList(leaf2, simpleref, leaf1, leaf1, simpleref, leaf2)));
	}
	
	@Test
	public void getReferencedObjectsBySearch() throws Exception {
		final WorkspaceUser user1 = new WorkspaceUser("u1");
		final WorkspaceUser user2 = new WorkspaceUser("u2");
		final WorkspaceIdentifier wsUser1 = new WorkspaceIdentifier("wsu1");
		final WorkspaceIdentifier wsUser2 = new WorkspaceIdentifier("wsu2");
		final WorkspaceIdentifier wsDel = new WorkspaceIdentifier("wsDel");
		final WorkspaceIdentifier wsUser2acc = new WorkspaceIdentifier("wsu2acc");
		// create, but never use, this workspace so the workspace id list isn't empty
		final WorkspaceIdentifier idlist = new WorkspaceIdentifier("idlist");
		ws.createWorkspace(user1, wsUser1.getName(), false, null, null);
		ws.setPermissions(user1, wsUser1, Arrays.asList(user2), Permission.WRITE);
		ws.createWorkspace(user2, wsUser2.getName(), false, null, null);
		ws.createWorkspace(user1, wsDel.getName(), false, null, null);
		ws.setPermissions(user1, wsDel, Arrays.asList(user2), Permission.WRITE);
		ws.createWorkspace(user2, wsUser2acc.getName(), false, null, null);
		ws.createWorkspace(user1, idlist.getName(), false, null, null);
		
		final TypeDefId reftype = new TypeDefId(new TypeDefName("CopyRev", "RefType"), 1, 0);
		
		final Provenance p2 = new Provenance(user2);
		final String leaf1Name = "leaf1";
		final ObjectInformation leaf1_1 = saveObject(user2, wsUser2, makeMeta(1), MT_MAP,
				SAFE_TYPE1, leaf1Name, p2);
		final String leaf1_1ref = wsUser2.getName() + "/" + leaf1Name + "/" + 1;
		ObjectInformation leaf1_2 = saveObject(user2, wsUser2, makeMeta(2), MT_MAP,
				SAFE_TYPE1, leaf1Name, p2);
		final String leaf1_2ref = wsUser2.getName() + "/" + leaf1Name + "/" + 2;
		final String delLeafName = "delleaf";
		ObjectInformation delleaf = saveObject(user2, wsUser2, makeMeta(3), MT_MAP,
				SAFE_TYPE1, delLeafName, p2);
		final String delLeafRef = wsUser2.getName() + "/" + delLeafName + "/" + 1;

		// this leaf will only have a len 2 path, id is 3
		final String path2LeafName = "path2leaf";
		ObjectInformation path2 = saveObject(user2, wsUser2, makeMeta(4), MT_MAP,
				SAFE_TYPE1, path2LeafName, p2);
		final String path2LeafRef = wsUser2.getName() + "/" + path2LeafName + "/" + 1;
		
		/* LEVEL 1 REFS */
		
		// this ref points to leaf 1-1 and del leaf, so will test refs pointing away from the
		// target object in the DAG
		final String ref1Name = "ref1"; // 1 hop
		saveObject(user2, wsUser2, MT_MAP, makeRefData(leaf1_1ref, delLeafRef), reftype, ref1Name,
				p2);
		ws.setObjectsDeleted(user2, Arrays.asList(new ObjectIdentifier(wsUser2, delLeafName)),
				true);
		final String ref1ref = wsUser2.getName() + "/" + ref1Name + "/" + 1;
		
		final String ref2Name = "ref2"; // 1 hop
		saveObject(user2, wsUser2, MT_MAP, makeRefData(leaf1_2ref), reftype, ref2Name, p2);
		final String ref2ref = wsUser2.getName() + "/" + ref2Name + "/" + 1;
		
		final String path2refName = "path2ref";
		saveObject(user2, wsUser1, MT_MAP, makeRefData(path2LeafRef), reftype, path2refName, p2);
		
		/* LEVEL 2 REFS */
		
		final String refref1Name = "refref1"; // 2 hops
		final Provenance p2withRef = new Provenance(user2).addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList(ref1ref)));
		saveObject(user2, wsUser2, MT_MAP, MT_MAP, SAFE_TYPE1, refref1Name, p2withRef);
		final String refref1ref = wsUser2.getName() + "/" + refref1Name + "/" + 1;
		
		// will traverse this ref but get nowhere since it's inaccessible and nothing refs it
		final String deadEndRef1 = "deadEnd1"; // 2 hops
		saveObject(user2, wsUser2, MT_MAP, makeRefData(ref1ref), reftype, deadEndRef1, p2);
		
		// will traverse this ref but get nowhere since ws is deleted
		final String delRef1 = "delRef1"; // 2 hops
		saveObject(user2, wsDel, MT_MAP, makeRefData(ref1ref), reftype, delRef1, p2);
		ws.setWorkspaceDeleted(user1, wsDel, true);
		
		final String refref2Name = "refref2"; // 2 hops
		saveObject(user2, wsUser1, MT_MAP, makeRefData(ref2ref), reftype, refref2Name, p2);
		
		/* LEVEL 3 REFS */
		
		final String refrefref1Name = "refrefref1"; // 3 hops
		saveObject(user2, wsUser1, MT_MAP, makeRefData(refref1ref), reftype, refrefref1Name, p2);
		
		final String refrefref1AccName = "refrefref1acc"; // 3 hops
		saveObject(user2, wsUser2acc, MT_MAP, makeRefData(refref1ref), reftype, refrefref1AccName,
				p2);
		
		
		//check target objects can't be accessed
		failGetObjects(user1, Arrays.asList(ObjectIdentifier.parseObjectReference(leaf1_1ref)),
				new InaccessibleObjectException("Object leaf1 cannot be accessed: User " +
						"u1 may not read workspace wsu2", null));
		failGetObjects(user1, Arrays.asList(ObjectIdentifier.parseObjectReference(leaf1_2ref)),
				new InaccessibleObjectException("Object leaf1 cannot be accessed: User " +
						"u1 may not read workspace wsu2", null));
		failGetObjects(user1, Arrays.asList(ObjectIdentifier.parseObjectReference(delLeafRef)),
				new InaccessibleObjectException("Object delleaf cannot be accessed: User " +
						"u1 may not read workspace wsu2", null));
		
		/* get multiple objects at the same time via various selectors
		 * not including delLeaf so that there will be a reference returned in the lookup that
		 * doesn't point to the target object
		 */
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier(2);
		final List<ObjectIdentifier> a = new LinkedList<ObjectIdentifier>();
		a.add(new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, 1, 1)));
		a.add(new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, 1, 2)));
		a.add(new ObjectIDWithRefPath(new ObjectIdentifier(wsi2, 1, 1)));
		a.add(new ObjectIDWithRefPath(new ObjectIdentifier(wsi2, 1)));
		a.add(new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, leaf1Name, 1)));
		a.add(new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, leaf1Name)));
		a.add(new ObjectIDWithRefPath(new ObjectIdentifier(wsi2, leaf1Name, 1)));
		a.add(new ObjectIDWithRefPath(new ObjectIdentifier(wsi2, leaf1Name, 2)));
		a.add(new ObjectIDWithRefPath(new ObjectIdentifier(wsi2, path2LeafName, 1))); // 1 hop path
		final List<WorkspaceObjectData> lwod = ws.getObjects(user1, a);
		
		final ObjectInformation leaf1_1stdPath = leaf1_1.updateReferencePath(Arrays.asList(
				new Reference("1/3/1"), new Reference("2/6/1"), new Reference("2/4/1"),
				new Reference("2/1/1")));
		leaf1_2 = leaf1_2.updateReferencePath(Arrays.asList(new Reference("1/2/1"),
				new Reference("2/5/1"), new Reference("2/1/2")));
		path2 = path2.updateReferencePath(Arrays.asList(
				new Reference("1/1/1"), new Reference("2/3/1")));
		try {
			assertThat("correct list size", lwod.size(), is(9));
			compareObjectAndInfo(lwod.get(0), leaf1_1stdPath, p2, MT_MAP, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(1), leaf1_2, p2, MT_MAP, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(2), leaf1_1stdPath, p2, MT_MAP, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(3), leaf1_2, p2, MT_MAP, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(4), leaf1_1stdPath, p2, MT_MAP, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(5), leaf1_2, p2, MT_MAP, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(6), leaf1_1stdPath, p2, MT_MAP, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(7), leaf1_2, p2, MT_MAP, MT_LIST, MT_MAP);
			compareObjectAndInfo(lwod.get(8), path2, p2, MT_MAP, MT_LIST, MT_MAP);
		} finally {
			destroyGetObjectsResources(lwod);
		}
		
		// test getting only an object with a 1 hop path
		checkReferencedObject(user1, new ObjectIDWithRefPath(new ObjectIdentifier(
				wsUser2, path2LeafName, 1)), path2, p2, MT_MAP, MT_LIST, MT_MAP);
		
		// test getting an object anonymously
		ws.setGlobalPermission(user1, wsUser1, Permission.READ);
		checkReferencedObject(null, new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, 1, 1)),
				leaf1_1stdPath, p2, MT_MAP, MT_LIST, MT_MAP);
		ws.setGlobalPermission(user1, wsUser1, Permission.NONE);
		
		// test getting a deleted object
		delleaf = delleaf.updateReferencePath(Arrays.asList(
				new Reference("1/3/1"), new Reference("2/6/1"), new Reference("2/4/1"),
				new Reference("2/2/1")));
		checkReferencedObject(user1, new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, 2, 1)),
				delleaf, p2, MT_MAP, MT_LIST, MT_MAP);
		
		// test getting an object in a deleted workspace
			//that's readable
		ws.setPermissions(user2, wsUser2, Arrays.asList(user1), Permission.READ);
		ws.setWorkspaceDeleted(user2, wsUser2, true);
		checkReferencedObject(user1, new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, 1, 1)),
				leaf1_1stdPath, p2, MT_MAP, MT_LIST, MT_MAP);
			//that's unreadable
		ws.setWorkspaceDeleted(user2, wsUser2, false);
		ws.setPermissions(user2, wsUser2, Arrays.asList(user1), Permission.NONE);
		ws.setWorkspaceDeleted(user2, wsUser2, true);
		checkReferencedObject(user1, new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, 1, 1)),
				leaf1_1stdPath, p2, MT_MAP, MT_LIST, MT_MAP);
		ws.setWorkspaceDeleted(user2, wsUser2, false);
		
		// test getting an object that has direct access
		final ObjectInformation direct = saveObject(user2, wsUser1, makeMeta(100), MT_MAP,
				SAFE_TYPE1, leaf1Name, p2); // id is 4
		checkReferencedObject(user1, new ObjectIDWithRefPath(new ObjectIdentifier(wsUser1, 4, 1)),
				direct, p2, MT_MAP, MT_LIST, MT_MAP);
		
		//fail getting an object anonymously
		failGetReferencedObjects(null, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsUser2, 1))),
				new InaccessibleObjectException("The latest version of object 1 in workspace " +
						"wsu2 is not accessible to anonymous users", null));
		
		//fail getting an object with no references
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsUser2, deadEndRef1))),
				new InaccessibleObjectException("The latest version of object deadEnd1 in " +
						"workspace wsu2 is not accessible to user u1", null));
		
		// fail getting an object due to a bad identifier
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(new WorkspaceIdentifier(3), 1))),
				new InaccessibleObjectException("The latest version of object 1 in workspace 3 " +
						"is not accessible to user u1", null));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(new WorkspaceIdentifier("foo"), 1, 1))),
				new InaccessibleObjectException("Version 1 of object 1 in workspace foo is not " +
						"accessible to user u1", null));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsUser2, 10, 1))), new InaccessibleObjectException(
						"Version 1 of object 10 in workspace wsu2 is not accessible to user u1",
						null));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsUser2, "foo"))), new InaccessibleObjectException(
						"The latest version of object foo in workspace wsu2 is not accessible " +
						"to user u1", null));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsUser2, 1, 10))), new InaccessibleObjectException(
						"Version 10 of object 1 in workspace wsu2 is not accessible to user u1",
						null));
		
		// fail getting an object because the head of the path is deleted
			// for a 1 hop path
		ws.setObjectsDeleted(user1, Arrays.asList(new ObjectIdentifier(wsUser1, 1, 1)), true);
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsUser2, 3, 1))), new InaccessibleObjectException(
						"Version 1 of object 3 in workspace wsu2 is not accessible to user u1",
						null));
		ws.setObjectsDeleted(user1, Arrays.asList(new ObjectIdentifier(wsUser1, 1, 1)), false);
			// for a 3 hop path
		ws.setObjectsDeleted(user1, Arrays.asList(new ObjectIdentifier(wsUser1, 3, 1)), true);
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsUser2, 1, 1))), new InaccessibleObjectException(
						"Version 1 of object 1 in workspace wsu2 is not accessible to user u1",
						null));
		ws.setObjectsDeleted(user1, Arrays.asList(new ObjectIdentifier(wsUser1, 3, 1)), false);
		
		/* fail getting an object because the head of the path is in a deleted workspace and
		 * then test accessing the object from a newly readable workspace
		 */
		ws.setWorkspaceDeleted(user1, wsUser1, true);
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsUser2, 1, 1))), new InaccessibleObjectException(
						"Version 1 of object 1 in workspace wsu2 is not accessible to user u1",
						null));
		ws.setPermissions(user2, wsUser2acc, Arrays.asList(user1), Permission.READ);
		checkReferencedObject(user1, new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, 1, 1)),
				leaf1_1.updateReferencePath(Arrays.asList(new Reference("4/1/1"),
						new Reference("2/6/1"), new Reference("2/4/1"), new Reference("2/1/1"))),
				p2, MT_MAP, MT_LIST, MT_MAP);
		ws.setPermissions(user2, wsUser2acc, Arrays.asList(user1), Permission.NONE);
		ws.setWorkspaceDeleted(user1, wsUser1, false);
		
		/* fail getting an object because the user has access to no workspaces */
		ws.setWorkspaceDeleted(user1, wsUser1, true);
		ws.setWorkspaceDeleted(user1, idlist, true);
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsUser2, 1, 1))), new InaccessibleObjectException(
						"Version 1 of object 1 in workspace wsu2 is not accessible to user u1",
						null));
		ws.setWorkspaceDeleted(user1, wsUser1, false);
		ws.setWorkspaceDeleted(user1, idlist, false);
		
		/* test object position is maintained when failing to get an object by standard methods, 
		 * a ref path and by lookup at the same time 
		 */
		failGetReferencedObjects(user1, Arrays.asList(
				new ObjectIDWithRefPath(new ObjectIdentifier(wsUser1, 3), null), // should work
				new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, 1)), // should work
				new ObjectIDWithRefPath(new ObjectIdentifier(wsUser1, 2), Arrays.asList(
						new ObjectIdentifier(wsUser2, ref2Name),
						new ObjectIdentifier(wsUser2, 10)))),
				new NoSuchReferenceException("Reference path #3 starting with object 2 " +
						"in workspace wsu1, position 2: Object ref2 in workspace wsu2 does " +
						"not contain a reference to object 10 in workspace wsu2",
						0, 0, null, null, null),
				Sets.newHashSet(2));
	
		// fail getting objects due to exceeding the allowed search size
		try {
			ws.setMaximumObjectSearchCount(1); // tests first time check
			assertThat("incorrect obj search count", ws.getMaximumObjectSearchCount(), is(1));
			failGetReferencedObjects(user1, Arrays.asList(
					new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, 1, 1)), // 7 nodes
					new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, 1, 2))), // 3 nodes
					new ReferenceSearchMaximumSizeExceededException(
							"Reached reference search limit"));
			
			ws.setMaximumObjectSearchCount(9); // test later check
			failGetReferencedObjects(user1, Arrays.asList(
					new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, 1, 1)), // 7 nodes
					new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, 1, 2))), // 3 nodes
					new ReferenceSearchMaximumSizeExceededException(
							"Reached reference search limit"),
					false, Sets.newHashSet(0)); //checks for nulls under the hood
			
			ws.setMaximumObjectSearchCount(10);
			final List<ObjectIdentifier> objs = new LinkedList<ObjectIdentifier>();
			objs.add(new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, 1, 1))); // 7 nodes
			objs.add(new ObjectIDWithRefPath(new ObjectIdentifier(wsUser2, 1, 2))); // 3 nodes
			destroyGetObjectsResources(ws.getObjects(user1, objs)); // should work
			
		} finally {
			ws.setMaximumObjectSearchCount(10000);
		}
	}
	
	@Test
	public void searchOnReadableDeletedObject() throws Exception {
		// test for a bug where a search on a readable deleted object would fail with a deleted
		// object exception
		WorkspaceUser user = new WorkspaceUser("user");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("wsi");
		
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		
		final Map<String, String> meta = ImmutableMap.of("foo", "bar");
		saveObject(user, wsi, meta, meta, SAFE_TYPE1, "leaf", new Provenance(user));
		final ObjectIdentifier leaf_id = new ObjectIdentifier(wsi, "leaf");
		
		final Map<String, Object> data = ImmutableMap.of("refs", Arrays.asList("wsi/leaf"));
		saveObject(user, wsi, meta, data, REF_TYPE, "ref", new Provenance(user));
		
		ws.setObjectsDeleted(user, Arrays.asList(leaf_id), true);
		
		final ObjectIDWithRefPath oidrefs = new ObjectIDWithRefPath(leaf_id);
		
		final ObjectInformation ret = ws.getObjectInformation(
				user, Arrays.asList(oidrefs), false, false).get(0);
		
		checkObjInfo(ret, 1L, "leaf", SAFE_TYPE1.getTypeString(), 1, user, 1, "wsi",
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13, null,
				Arrays.asList(new Reference("1/2/1"), new Reference("1/1/1")));
	}
	
	@Test
	public void searchOnReadableObject() throws Exception {
		// test for a bug (never checked in) where a search on a readable object failed
		WorkspaceUser user = new WorkspaceUser("user");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("wsi");
		
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		
		final Map<String, String> meta = ImmutableMap.of("foo", "bar");
		saveObject(user, wsi, meta, meta, SAFE_TYPE1, "leaf", new Provenance(user));
		final ObjectIdentifier leaf_id = new ObjectIdentifier(wsi, "leaf");
		
		final ObjectIDWithRefPath oidrefs = new ObjectIDWithRefPath(leaf_id);
		
		final ObjectInformation ret = ws.getObjectInformation(
				user, Arrays.asList(oidrefs), false, false).get(0);
		
		checkObjInfo(ret, 1L, "leaf", SAFE_TYPE1.getTypeString(), 1, user, 1, "wsi",
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13, null,
				Arrays.asList(new Reference("1/1/1")));
	}
	
	@Test
	public void getReferencedObjectsByPath() throws Exception {
		WorkspaceUser user1 = new WorkspaceUser("refedUser");
		WorkspaceUser user2 = new WorkspaceUser("refedUser2");
		WorkspaceIdentifier wsiacc1n = new WorkspaceIdentifier("refedaccessible");
		WorkspaceIdentifier wsiacc2n = new WorkspaceIdentifier("refedaccessible2");
		WorkspaceIdentifier wsiun1n = new WorkspaceIdentifier("refedunacc");
		WorkspaceIdentifier wsiun2n = new WorkspaceIdentifier("refedunacc2");
		WorkspaceIdentifier wsideln = new WorkspaceIdentifier("refeddel");
		
		ws.createWorkspace(user1, wsiacc1n.getName(), false, null, null);
		WorkspaceIdentifier wsiacc1 = new WorkspaceIdentifier(1);
		ws.setPermissions(user1, wsiacc1, Arrays.asList(user2), Permission.WRITE);
		ws.createWorkspace(user2, wsiacc2n.getName(), true, null, null);
		WorkspaceIdentifier wsiacc2 = new WorkspaceIdentifier(2);
		long wsidun1 = ws.createWorkspace(user2, wsiun1n.getName(), false, null, null).getId();
		WorkspaceIdentifier wsiun1 = new WorkspaceIdentifier(3);
		long wsidun2 = ws.createWorkspace(user2, wsiun2n.getName(), false, null, null).getId();
		WorkspaceIdentifier wsiun2 = new WorkspaceIdentifier(4);
		ws.createWorkspace(user2, wsideln.getName(), false, null, null);
		WorkspaceIdentifier wsidel = new WorkspaceIdentifier(5);
		
		TypeDefId reftype = new TypeDefId(new TypeDefName("CopyRev", "RefType"), 1, 0);
		
		Map<String, String> meta1 = new HashMap<String, String>();
		meta1.put("some", "very special metadata");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("some", "very special metadata2");
		
		Map<String, String> mtdata = new HashMap<String, String>();
		Map<String, Object> data1 = createData(
				"{\"thing1\": \"whoop whoop\"," +
				" \"thing2\": \"aroooga\"}");
		Map<String, Object> data2 = createData(
				"{\"thing3\": \"whoop whoop\"," +
				" \"thing4\": \"aroooga\"}");
		
		// save objects and basic accessibility checks
		ObjectInformation leaf1_1 = saveObject(user2, wsiun1, meta1, data1, SAFE_TYPE1, "leaf1", new Provenance(user2));
		ObjectInformation leaf1_2 = saveObject(user2, wsiun1, meta1, data1, SAFE_TYPE1, "leaf1", new Provenance(user2));
		ObjectIdentifier leaf1oi1 = new ObjectIdentifier(wsiun1, 1, 1);
		ObjectIdentifier leaf1oi2 = new ObjectIdentifier(wsiun1, 1, 2);
		failGetObjects(user1, Arrays.asList(new ObjectIdentifier(wsiun1n, "leaf1")),
				new InaccessibleObjectException("Object leaf1 cannot be accessed: User " +
						"refedUser may not read workspace refedunacc", null));
		failGetObjects(user1, Arrays.asList(leaf1oi1), new InaccessibleObjectException(
				"Object 1 cannot be accessed: User refedUser may not read workspace 3", null));
		ObjectInformation leaf2 = saveObject(user2, wsiun2, meta2, data2, SAFE_TYPE1, "leaf2", new Provenance(user2));
		ObjectIdentifier leaf2oi = new ObjectIdentifier(wsiun2, 1, 1);
		failGetObjects(user1, Arrays.asList(new ObjectIdentifier(wsiun2n, "leaf2")),
				new InaccessibleObjectException("Object leaf2 cannot be accessed: User " +
						"refedUser may not read workspace refedunacc2", null));
		failGetObjects(user1, Arrays.asList(leaf2oi), new InaccessibleObjectException(
				"Object 1 cannot be accessed: User refedUser may not read workspace 4", null));
		saveObject(user2, wsiun2, meta2, data2, SAFE_TYPE1, "unlinked", new Provenance(user2));
		ObjectIdentifier unlinkedoi = new ObjectIdentifier(wsiun2, 2, 1);
		failGetObjects(user1, Arrays.asList(new ObjectIdentifier(wsiun2n, "unlinked")),
				new InaccessibleObjectException("Object unlinked cannot be accessed: User " +
						"refedUser may not read workspace refedunacc2", null));
		failGetObjects(user1, Arrays.asList(unlinkedoi), new InaccessibleObjectException(
				"Object 2 cannot be accessed: User refedUser may not read workspace 4", null));
		
		final String leaf1r1 = "refedunacc/leaf1/1";
		saveObject(user2, wsiacc1, MT_MAP, makeRefData(leaf1r1),reftype,
				"simpleref", new Provenance(user2));
		final String leaf1r2 = "refedunacc/leaf1/2";
		saveObject(user2, wsiacc1, MT_MAP, makeRefData(leaf1r2),reftype,
				"simpleref", new Provenance(user2));
		final String leaf2r = "refedunacc2/leaf2";
		saveObject(user2, wsiacc2, MT_MAP, makeRefData(leaf2r),reftype,
				"simpleref2", new Provenance(user2));
		
		/*
		 * At this point:
		 * wsiun1 (3): 
		 *   leaf1 (1) v 1 & 2
		 *   
		 * wsiun2 (4):
		 *   leaf2 (1)
		 *   unlinked (2)
		 *   
		 * wsiacc1 (1):
		 *   simpleref (1) v1 -> wsiun1/leaf1 v1
		 *                 v2 -> wsiun1/leaf1 v2
		 *   provref (2) v1 -> wsiun1/leaf1 v1
		 * 
		 * wsiacc2 (2):
		 *   simpleref (1) -> wsiun2/leaf2 v1
		 *   provref2 (2) v1 -> wsiun2/leaf2 v1
		 */
		
		saveObject(user2, wsiacc1, MT_MAP, mtdata, SAFE_TYPE1, "provref", new Provenance(user2)
				.addAction(new ProvenanceAction().withWorkspaceObjects(
						Arrays.asList(leaf1r1))));
		saveObject(user2, wsiacc2, MT_MAP, mtdata, SAFE_TYPE1, "provref2", new Provenance(user2)
				.addAction(new ProvenanceAction().withWorkspaceObjects(
						Arrays.asList(leaf2r))));
		
		// check one hop reference dive works
		final HashMap<String, String> mtmap = new HashMap<String, String>();
		final LinkedList<String> mtlist = new LinkedList<String>();
		final Reference sr11 = new Reference(1, 1, 1);
		final Reference sr12 = new Reference(1, 1, 2);
		final Reference sr2 = new Reference(2, 1, 1);
		final Reference l11 = new Reference(3, 1, 1);
		final Reference l12 = new Reference(3, 1, 2);
		final Reference l2 = new Reference(4, 1, 1);
		final Reference pr1 = new Reference(1, 2, 1);
		final Reference pr2 = new Reference(2, 2, 1);
		checkReferencedObject(user1, new ObjectIDWithRefPath(
				new ObjectIdentifier(wsiacc1, "simpleref", 1),Arrays.asList(leaf1oi1)),
				leaf1_1.updateReferencePath(Arrays.asList(sr11, l11)), new Provenance(user2),
				data1, mtlist, mtmap);
		checkReferencedObject(user1, new ObjectIDWithRefPath(
				new ObjectIdentifier(wsiacc1n, "simpleref", 2), Arrays.asList(leaf1oi2)),
				leaf1_2.updateReferencePath(Arrays.asList(sr12, l12)), new Provenance(user2),
				data1, mtlist, mtmap);
		checkReferencedObject(user1, new ObjectIDWithRefPath(new ObjectIdentifier(wsiacc1, 1),
				Arrays.asList(leaf1oi2)), leaf1_2.updateReferencePath(Arrays.asList(sr12, l12)),
				new Provenance(user2), data1, mtlist, mtmap);
		checkReferencedObject(user1, new ObjectIDWithRefPath(
				new ObjectIdentifier(wsiacc2, "simpleref2"), Arrays.asList(leaf2oi)),
				leaf2.updateReferencePath(Arrays.asList(sr2, l2)), new Provenance(user2),
				data2, mtlist, mtmap);
		checkReferencedObject(user1, new ObjectIDWithRefPath(
				new ObjectIdentifier(wsiacc1, "provref"), Arrays.asList(leaf1oi1)),
				leaf1_1.updateReferencePath(Arrays.asList(pr1, l11)), new Provenance(user2),
				data1, mtlist, mtmap);
		checkReferencedObject(user1, new ObjectIDWithRefPath(
				new ObjectIdentifier(wsiacc2, "provref2"), Arrays.asList(leaf2oi)),
				leaf2.updateReferencePath(Arrays.asList(pr2, l2)), new Provenance(user2),
				data2, mtlist, mtmap);
		
		//fail on one hop bad Reference paths
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsiacc2n, "simpleref2"), Arrays.asList(leaf1oi1))),
				new NoSuchReferenceException("Reference path #1 starting with object simpleref2 " +
						"in workspace refedaccessible2, position 1: Object simpleref2 in " +
						"workspace refedaccessible2 does not contain a reference to object 1 " +
						"version 1 in workspace 3", 0, 0, null, null, null));
		
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsiacc1, "simpleref"), Arrays.asList(leaf1oi1))),
				new NoSuchReferenceException("Reference path #1 starting with object simpleref " +
						"in workspace 1, position 1: Object simpleref in workspace 1 " +
						"does not contain a reference to object 1 version 1 in workspace 3",
						0, 0, null, null, null));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsiacc1n, "simpleref", 2), Arrays.asList(leaf1oi1))),
				new NoSuchReferenceException("Reference path #1 starting with object simpleref " +
						"version 2 in workspace refedaccessible, position 1: Object simpleref " +
						"version 2 in workspace refedaccessible does not contain a reference to " +
						"object 1 version 1 in workspace 3", 0, 0, null, null, null));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsiacc1n, 1, 1), Arrays.asList(leaf1oi2))),
				new NoSuchReferenceException("Reference path #1 starting with object 1 version " +
						"1 in workspace refedaccessible, position 1: Object 1 version 1 in " +
						"workspace refedaccessible does not contain a reference to object 1 " +
						"version 2 in workspace 3", 0, 0, null, null, null));
		
		// set up 2 hop Reference paths with deleted objects & ws in the mix
		final String deleted1 = "del1";
		final String deleted2 = "del2";
		final ObjectInformation del1 = saveObject(user2, wsiun1, meta2,
				makeRefData(leaf1r1, leaf2r), reftype, deleted1, new Provenance(user2));
		final ObjectIdentifier del1oi = new ObjectIdentifier(wsiun1, 2, 1);
		final Reference del1ref = new Reference(3, 2, 1);
		final Provenance p = new Provenance(user2).addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList(leaf1r1, leaf2r)));
		final ObjectInformation del2 = saveObject(user2, wsiun2, meta1, makeRefData(),
				reftype, deleted2, p);
		final ObjectIdentifier del2oi = new ObjectIdentifier(wsiun2, 3, 1);
		final Reference del2ref = new Reference(4, 3, 1);
		saveObject(user2, wsidel, meta1, makeRefData(leaf2r), reftype, "delws", new Provenance(user2));
		final ObjectIdentifier delwsoi = new ObjectIdentifier(wsidel, 1, 1);
		final Reference delwsref = new Reference(5, 1, 1);
		
		final String delpointer12 = "delptr12";
		final String delpointer2 = "delptr2";
		final String deppointerWorkspace = "delptrws";
		saveObject(user2, wsiacc1, MT_MAP, makeRefData("refedunacc/del1", "refedunacc2/del2"),
				reftype, delpointer12, new Provenance(user2));
		final ObjectIdentifier delptr12oi = new ObjectIdentifier(wsiacc1, 3);
		final Reference dp12 = new Reference(1, 3, 1);
		saveObject(user2, wsiacc2, MT_MAP, makeRefData("refedunacc2/del2"),
				reftype, delpointer2, new Provenance(user2));
		final ObjectIdentifier delptr2oi = new ObjectIdentifier(wsiacc2, 3);
		final Reference dp2 = new Reference(2, 3, 1);
		saveObject(user2, wsiacc2, MT_MAP, makeRefData("refeddel/delws"),
				reftype, deppointerWorkspace, new Provenance(user2));
		final ObjectIdentifier delptrwsoi = new ObjectIdentifier(wsiacc2, 4);
		final Reference dpws = new Reference(2, 4, 1);
		ws.setObjectsDeleted(user2, Arrays.asList(del1oi, del2oi), true);
		ws.setWorkspaceDeleted(user2, wsidel, true);
		
		/*
		 * At this point:
		 * wsiun1 (3): 
		 *   leaf1 (1) v 1 & 2
		 *   del1 (deleted) (2) -> wsiun1/leaf1 v1
		 *                      -> wsiun2/leaf2 v1 
		 *   
		 * wsiun2 (4):
		 *   leaf2 (1)
		 *   unlinked (2)
		 *   del2 (deleted, prov refs) (3) -> wsiun1/leaf1 v1
		 *                                 -> wsiun2/leaf2 v1
		 * 
		 * wsidel (5) (deleted):
		 *   delws (1) -> wsiun2/leaf2 v1
		 * 
		 * wsiacc1 (1):
		 *   simpleref (1) v1 -> wsiun1/leaf1 v1
		 *                 v2 -> wsiun1/leaf1 v2
		 *   provref (2) v1 -> wsiun1/leaf1 v1
		 *   delptr12 (3) -> wssun1/del1 v1
		 *                -> wssun2/del2 v1
		 * 
		 * wsiacc2 (2):
		 *   simpleref (1) -> wsiun2/leaf2 v1
		 *   provref2 (2) v1 -> wsiun2/leaf2 v1
		 *   delptr2 (3) -> wsiun2/del2 v1
		 *   delptrws (4) -> wsidel/delws v1
		 *   
		 * Not listing the earlier prov refs here, they're simple
		 */
		
		
		// test 2 hop Reference paths with absolute references
		// also tests that two different paths to the same object are resolved correctly, e.g.
		// the returned path is correct for both objects
		List<ObjectIdentifier> a = new LinkedList<ObjectIdentifier>();
		a.add(new ObjectIDWithRefPath(delptr12oi, Arrays.asList(del1oi, leaf1oi1)));
		a.add(new ObjectIDWithRefPath(delptr12oi, Arrays.asList(del1oi, leaf2oi)));
		a.add(new ObjectIDWithRefPath(delptr12oi, Arrays.asList(del2oi, leaf1oi1)));
		a.add(new ObjectIDWithRefPath(delptrwsoi, Arrays.asList(delwsoi, leaf2oi)));
		a.add(new ObjectIDWithRefPath(delptr12oi, Arrays.asList(del2oi, leaf2oi)));
		a.add(new ObjectIDWithRefPath(delptr2oi, Arrays.asList(del2oi, leaf1oi1)));
		a.add(new ObjectIDWithRefPath(delptr2oi, Arrays.asList(del2oi, leaf2oi)));
		List<WorkspaceObjectData> lwod = ws.getObjects(user1, a);
		try {
			assertThat("correct list size", lwod.size(), is(7));
			compareObjectAndInfo(lwod.get(0),
					leaf1_1.updateReferencePath(Arrays.asList(dp12, del1ref, l11)),
					new Provenance(user2), data1, mtlist, mtmap);
			compareObjectAndInfo(lwod.get(1),
					leaf2.updateReferencePath(Arrays.asList(dp12, del1ref, l2)),
					new Provenance(user2), data2, mtlist, mtmap);
			compareObjectAndInfo(lwod.get(2),
					leaf1_1.updateReferencePath(Arrays.asList(dp12, del2ref, l11)),
					new Provenance(user2), data1, mtlist, mtmap);
			compareObjectAndInfo(lwod.get(3),
					leaf2.updateReferencePath(Arrays.asList(dpws, delwsref, l2)),
					new Provenance(user2), data2, mtlist, mtmap);
			compareObjectAndInfo(lwod.get(4),
					leaf2.updateReferencePath(Arrays.asList(dp12, del2ref, l2)),
					new Provenance(user2), data2, mtlist, mtmap);
			compareObjectAndInfo(lwod.get(5),
					leaf1_1.updateReferencePath(Arrays.asList(dp2, del2ref, l11)),
					new Provenance(user2), data1, mtlist, mtmap);
			compareObjectAndInfo(lwod.get(6),
					leaf2.updateReferencePath(Arrays.asList(dp2, del2ref, l2)),
					new Provenance(user2), data2, mtlist, mtmap);
		} finally {
			destroyGetObjectsResources(lwod);
		}
		List<ObjectInformation> loi = ws.getObjectInformation(user1, a, true, false);
		assertThat("object info not same", loi, is(Arrays.asList(
				leaf1_1.updateReferencePath(Arrays.asList(dp12, del1ref, l11)),
				leaf2.updateReferencePath(Arrays.asList(dp12, del1ref, l2)),
				leaf1_1.updateReferencePath(Arrays.asList(dp12, del2ref, l11)),
				leaf2.updateReferencePath(Arrays.asList(dpws, delwsref, l2)),
				leaf2.updateReferencePath(Arrays.asList(dp12, del2ref, l2)),
				leaf1_1.updateReferencePath(Arrays.asList(dp2, del2ref, l11)),
				leaf2.updateReferencePath(Arrays.asList(dp2, del2ref, l2)))));
		
		checkReferencedObject(user1, new ObjectIDWithRefPath(delptr12oi, Arrays.asList(del1oi)),
				del1.updateReferencePath(Arrays.asList(dp12, del1ref)), new Provenance(user2),
				makeRefData(wsidun1 + "/1/1", wsidun2 + "/1/1"),
				Arrays.asList(wsidun1 + "/1/1", wsidun2 + "/1/1"),  mtmap);
		Map<String, String> provmap = new HashMap<String, String>();
		provmap.put(leaf1r1, wsidun1 + "/1/1");
		provmap.put(leaf2r, wsidun2 + "/1/1");
		checkReferencedObject(user1, new ObjectIDWithRefPath(delptr12oi, Arrays.asList(del2oi)),
				del2.updateReferencePath(Arrays.asList(dp12, del2ref)), p, makeRefData(), mtlist,
				provmap);
		
		// test 2 hop Reference paths with temporary references
		ObjectIdentifier leaf2tempWS = new ObjectIdentifier(wsiun2n, 1, 1);
		ObjectIdentifier leaf2tempID = new ObjectIdentifier(wsiun2, "leaf2", 1);
		ObjectIdentifier leaf2nover = new ObjectIdentifier(wsiun2, 1);
		a.clear();
		a.add(new ObjectIDWithRefPath(delptr12oi, Arrays.asList(del1oi, leaf2tempWS)));
		a.add(new ObjectIDWithRefPath(delptr12oi, Arrays.asList(del1oi, leaf2tempID)));
		a.add(new ObjectIDWithRefPath(delptr12oi, Arrays.asList(del2oi, leaf2nover)));
		lwod = ws.getObjects(user1, a);
		try {
			compareObjectAndInfo(lwod.get(0),
					leaf2.updateReferencePath(Arrays.asList(dp12, del1ref, l2)),
					new Provenance(user2), data2, mtlist, mtmap);
			compareObjectAndInfo(lwod.get(1),
					leaf2.updateReferencePath(Arrays.asList(dp12, del1ref, l2)),
					new Provenance(user2), data2, mtlist, mtmap);
			compareObjectAndInfo(lwod.get(2),
					leaf2.updateReferencePath(Arrays.asList(dp12, del2ref, l2)),
					new Provenance(user2), data2, mtlist, mtmap);
		} finally {
			destroyGetObjectsResources(lwod);
		}
		loi = ws.getObjectInformation(user1, a, true, false);
		assertThat("object info not same", loi, is(Arrays.asList(
				leaf2.updateReferencePath(Arrays.asList(dp12, del1ref, l2)),
				leaf2.updateReferencePath(Arrays.asList(dp12, del1ref, l2)),
				leaf2.updateReferencePath(Arrays.asList(dp12, del2ref, l2)))));
		
		
		// fail on 2 hop chains with absolute references
		ObjectIDWithRefPath goodchain = new ObjectIDWithRefPath(delptr12oi, Arrays.asList(
				del1oi, leaf1oi1));
		
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsiacc2n, delpointer2), Arrays.asList(del1oi, leaf1oi1))),
				new NoSuchReferenceException("Reference path #1 starting with object delptr2 "+
						"in workspace refedaccessible2, position 1: Object delptr2 in workspace " +
						"refedaccessible2 does not contain a reference to object 2 version 1 in " +
						"workspace 3", 0, 0, null, null, null));
		failGetReferencedObjects(user1, Arrays.asList(goodchain, goodchain,
				new ObjectIDWithRefPath(delptr12oi, Arrays.asList(del1oi, unlinkedoi))),
				new NoSuchReferenceException("Reference path #3 starting with object 3 in " +
						"workspace 1, position 2: Object 2 version 1 in workspace 3 does "+
						"not contain a reference to object 2 version 1 in workspace 4",
						0, 0, null, null, null), Sets.newHashSet(2));
		failGetReferencedObjects(user1, Arrays.asList(goodchain,
				new ObjectIDWithRefPath(delptr12oi, Arrays.asList(del1oi, 
						new ObjectIdentifier(wsiun2, 3, 1))), goodchain),
				new NoSuchReferenceException("Reference path #2 starting with object 3 in " +
						"workspace 1, position 2: Object 2 version 1 in workspace 3 does not " +
						"contain a reference to object 3 version 1 in workspace 4",
						0, 0, null, null, null), Sets.newHashSet(1));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(delptr12oi,
				Arrays.asList(del2oi, new ObjectIdentifier(wsiun1, 1, 3)))),
				new NoSuchReferenceException("Reference path #1 starting with object 3 " +
						"in workspace 1, position 2: Object 3 version 1 in workspace 4 does not " +
						"contain a reference to object 1 version 3 in workspace 3",
						0, 0, null, null, null));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(delptr12oi,
				Arrays.asList(del2oi, new ObjectIdentifier(new WorkspaceIdentifier(6), 1, 3)))),
				new NoSuchReferenceException("Reference path #1 starting with object 3 in " +
						"workspace 1, position 2: Object 3 version 1 in workspace 4 does not " +
						"contain a reference to object 1 version 3 in workspace 6",
						0, 0, null, null, null));
		
		// fail on 2 hop chains with temporary references
		ObjectIdentifier leaf1badTempWs = new ObjectIdentifier(
				new WorkspaceIdentifier("foo"), 1, 1);
		ObjectIdentifier leaf1badTempID = new ObjectIdentifier(wsiun1, "leaf2", 1);
		ObjectIdentifier leaf1nover = new ObjectIdentifier(wsiun1, 1);
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(delptr12oi,
				Arrays.asList(del1oi, leaf1badTempWs))),
				new NoSuchReferenceException("Reference path #1 starting with object 3 in " +
						"workspace 1, position 2: Object 2 version 1 in workspace 3 does not " +
						"contain a reference to object 1 version 1 in workspace foo",
						0, 0, null, null, null));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(delptr12oi,
				Arrays.asList(del2oi, leaf1badTempID))),
				new NoSuchReferenceException("Reference path #1 starting with object 3 in " +
						"workspace 1, position 2: Object 3 version 1 in workspace 4 does not " +
						"contain a reference to object leaf2 version 1 in workspace 3",
						0, 0, null, null, null));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(delptr12oi,
				Arrays.asList(del2oi, leaf1nover))),
				new NoSuchReferenceException("Reference path #1 starting with object 3 in " +
						"workspace 1, position 2: Object 3 version 1 in workspace 4 does not " +
						"contain a reference to object 1 in workspace 3", 0, 0, null, null, null));
		
		// test various ways the root object could be inaccessible
		failGetReferencedObjects(user2, new ArrayList<ObjectIDWithRefPath>(),
				new IllegalArgumentException("No object identifiers provided"));
		failGetReferencedObjects(user2, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsiun1, "leaf3"),
				Arrays.asList(new ObjectIdentifier(wsiun1, 1, 1)))),
				new InaccessibleObjectException("No object with name leaf3 exists in workspace " +
						"3 (name refedunacc)", null));
		failGetReferencedObjects(user2, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsiun1, "leaf1", 3),
				Arrays.asList(new ObjectIdentifier(wsiun1, 1, 1)))),
				new InaccessibleObjectException(
						"No object with id 1 (name leaf1) and version 3 exists in workspace 3 " +
						"(name refedunacc)", null));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(new ObjectIdentifier(new WorkspaceIdentifier("fakefakefake"), "leaf1"),
				Arrays.asList(new ObjectIdentifier(wsiun1, 1, 1)))),
				new InaccessibleObjectException("Object leaf1 cannot be accessed: No workspace " +
						"with name fakefakefake exists", null));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectIDWithRefPath(new ObjectIdentifier(wsiun1n, "leaf1"),
				Arrays.asList(new ObjectIdentifier(wsiun1, 1, 1)))),
				new InaccessibleObjectException("Object leaf1 cannot be accessed: User " +
						"refedUser may not read workspace refedunacc", null));
		failGetReferencedObjects(null, Arrays.asList(new ObjectIDWithRefPath(new ObjectIdentifier(wsiun1, "leaf1"),
				Arrays.asList(new ObjectIdentifier(wsiun1, 1, 1)))),
				new InaccessibleObjectException("Object leaf1 cannot be accessed: Anonymous " +
						"users may not read workspace 3", null));
		ws.setObjectsDeleted(user2, Arrays.asList(new ObjectIdentifier(wsiun1, "leaf1")), true);
		failGetReferencedObjects(user2, Arrays.asList(new ObjectIDWithRefPath(
				new ObjectIdentifier(wsiun1n, "leaf1"),
				Arrays.asList(new ObjectIdentifier(wsiun1, 1, 1)))),
				new InaccessibleObjectException("Object 1 (name leaf1) in workspace 3 " +
						"(name refedunacc) has been deleted", null));
		ws.setObjectsDeleted(user2, Arrays.asList(new ObjectIdentifier(wsiun1, "leaf1")), false);
		ws.setWorkspaceDeleted(user2, wsiun1, true);
		failGetReferencedObjects(user2, Arrays.asList(new ObjectIDWithRefPath(new ObjectIdentifier(wsiun1n, "leaf1"),
				Arrays.asList(new ObjectIdentifier(wsiun1, 1, 1)))),
				new InaccessibleObjectException("Object leaf1 cannot be accessed: Workspace " +
						"refedunacc is deleted", null));
	}

	@Test
	public void objectChain() throws Exception {
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("foo");
		ObjectIdentifier oi = new ObjectIdentifier(wsi, "thing");
		failCreateObjectChain(null, new ArrayList<ObjectIdentifier>(),
				new IllegalArgumentException("id cannot be null"));
		failCreateObjectChain(oi, Arrays.asList(oi, null, oi),
				new IllegalArgumentException("Nulls are not allowed in reference chains"));
	}
	
	@Test
	public void grantRemoveOwnership() throws Exception {
		WorkspaceUser user = new WorkspaceUser("foo");
		String moduleName = "SharedModule";
		types.requestModuleRegistration(user, moduleName);
		types.resolveModuleRegistration(moduleName, true);
		types.compileNewTypeSpec(user, "module " + moduleName + " {typedef int MainType;};", 
				Arrays.asList("MainType"), null, null, false, null);
		types.releaseTypes(user, moduleName);
		WorkspaceUser user2 = new WorkspaceUser("bar");
		try {
			types.compileNewTypeSpec(user2, "module " + moduleName + " {typedef string MainType;};", 
					Collections.<String>emptyList(), null, null, false, null);
			fail("expected exception");
		} catch (NoSuchPrivilegeException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains("not in list of owners"),
					is(true));
		}
		types.grantModuleOwnership(moduleName, user2.getUser(), false, user, false);
		types.compileNewTypeSpec(user2, "module " + moduleName + " {typedef string MainType;};", 
				Collections.<String>emptyList(), null, null, false, null);
		WorkspaceUser user3 = new WorkspaceUser("baz");
		try {
			types.grantModuleOwnership(moduleName, user3.getUser(), false, user2, false);
			fail("expected exception");
		} catch (NoSuchPrivilegeException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains("can not change privileges"),
					is(true));
		}
		types.grantModuleOwnership(moduleName, user2.getUser(), true, user, false);
		types.grantModuleOwnership(moduleName, user3.getUser(), false, user2, false);
		types.removeModuleOwnership(moduleName, user3.getUser(), user2, false);
		types.removeModuleOwnership(moduleName, user2.getUser(), user, false);
		try {
			types.compileNewTypeSpec(user2, "module " + moduleName + " {typedef float MainType;};", 
					Collections.<String>emptyList(), null, null, false, null);
			fail("expected exception");
		} catch (NoSuchPrivilegeException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains("not in list of owners"),
					is(true));
		}
	}
	
	@Test
	public void removeTypeTest() throws Exception {
		WorkspaceUser user = new WorkspaceUser("foo");
		String moduleName = "MyMod3";
		types.requestModuleRegistration(user, moduleName);
		types.resolveModuleRegistration(moduleName, true);
		types.compileNewTypeSpec(user, "module " + moduleName + " {" +
				"typedef structure {string foo; list<int> bar; int baz;} AType; " +
				"typedef structure {string whooo;} BType;};", 
				Arrays.asList("AType", "BType"), null, null, false, null);
		types.compileTypeSpec(user, moduleName, Collections.<String>emptyList(),
				Arrays.asList("BType"), Collections.<String, Long>emptyMap(), false);
		List<Long> vers = types.getModuleVersions(moduleName, user);
		Collections.sort(vers);
		assertThat(vers.size(), is(2));
		assertThat(types.getModuleInfo(
				user, new ModuleDefId(moduleName, vers.get(0))).getTypes().size(), is(2));
		assertThat(types.getModuleInfo(
				user, new ModuleDefId(moduleName, vers.get(1))).getTypes().size(), is(1));
		assertThat(types.getModuleVersions(new TypeDefId(moduleName + ".BType", "0.1"), user),
				is(Arrays.asList(vers.get(0))));
		types.releaseTypes(user, moduleName);
		assertThat(types.getModuleVersions(new TypeDefId(moduleName + ".AType"), null).size(),
				is(1));
		assertThat(types.getTypeInfo(moduleName + ".AType", false, null).getTypeDefId(),
				is(moduleName + ".AType-1.0"));
	}
	
	@Test
	public void admin() throws Exception {
		assertThat("no admins before adding any", ws.getAdmins(),
				is((Set<WorkspaceUser>) new HashSet<WorkspaceUser>()));
		ws.addAdmin(new WorkspaceUser("adminguy"));
		Set<WorkspaceUser> expected = new HashSet<WorkspaceUser>();
		expected.add(new WorkspaceUser("adminguy"));
		assertThat("correct admins", ws.getAdmins(),
				is(expected));
		assertTrue("correctly detected as admin",
				ws.isAdmin(new WorkspaceUser("adminguy")));
		assertFalse("correctly detected as not an admin",
				ws.isAdmin(new WorkspaceUser("adminguy2")));
		
		ws.addAdmin(new WorkspaceUser("adminguy2"));
		expected.add(new WorkspaceUser("adminguy2"));
		assertThat("correct admins", ws.getAdmins(),
				is(expected));
		assertTrue("correctly detected as admin",
				ws.isAdmin(new WorkspaceUser("adminguy")));
		assertTrue("correctly detected as admin",
				ws.isAdmin(new WorkspaceUser("adminguy2")));
		assertFalse("correctly detected as not an admin",
				ws.isAdmin(new WorkspaceUser("adminguy3")));
		
		ws.removeAdmin(new WorkspaceUser("adminguy"));
		expected.remove(new WorkspaceUser("adminguy"));
		assertThat("correct admins", ws.getAdmins(),
				is(expected));
		assertFalse("correctly detected as not an admin",
				ws.isAdmin(new WorkspaceUser("adminguy")));
		assertTrue("correctly detected as admin",
				ws.isAdmin(new WorkspaceUser("adminguy2")));
		assertFalse("correctly detected as not an admin",
				ws.isAdmin(new WorkspaceUser("adminguy3")));
	}
	
	@Test
	public void getAllWorkspaceOwners() throws Exception {
		Set<WorkspaceUser> startusers = ws.getAllWorkspaceOwners();
		String userprefix = "getAllWorkspaceOwners";
		Set<WorkspaceUser> users = new HashSet<WorkspaceUser>();
		for (int i = 0; i < 4; i++) {
			String u = userprefix + i;
			users.add(new WorkspaceUser(u));
			ws.createWorkspace(new WorkspaceUser(u), u + ":" + userprefix,
					false, null, null);
		}
		Set<WorkspaceUser> newusers = ws.getAllWorkspaceOwners();
		newusers.removeAll(startusers);
		assertThat("got correct list of workspace users", newusers, is(users));
	}
	
	@Test
	public void sortForMD5() throws Exception {
		WorkspaceUser user = new WorkspaceUser("md5user");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("sorting");
		ws.createWorkspace(user, wsi.getIdentifierString(), false, null, null);
		Map<String, Object> data = new LinkedHashMap<String, Object>();
		data.put("g", 7);
		data.put("d", 4);
		data.put("a", 1);
		data.put("e", 5);
		data.put("b", 2);
		data.put("f", 6);
		data.put("c", 3);
		String expected = "{\"a\":1,\"b\":2,\"c\":3,\"d\":4,\"e\":5,\"f\":6,\"g\":7}";
		String md5 = DigestUtils.md5Hex(expected);
		assertThat("md5 correct", md5, is("f906e268b16cbfa1c302c6bb51a6b784"));
		
		JsonNode savedata = MAPPER.valueToTree(data);
		Provenance p = new Provenance(new WorkspaceUser("kbasetest2"));
		List<WorkspaceSaveObject> objects = Arrays.asList(new WorkspaceSaveObject(
				getRandomName(), savedata, SAFE_TYPE1, null, p, false));
		List<ObjectInformation> objinfo = ws.saveObjects(user, wsi, objects,
				getIdFactory());
		assertThat("workspace calculated md5 correct", objinfo.get(0).getCheckSum(),
				is(md5));
		objinfo = ws.getObjectInformation(user, Arrays.asList(new ObjectIdentifier(wsi, 1)), false, false);
		assertThat("workspace calculated md5 correct", objinfo.get(0).getCheckSum(),
				is(md5));
	}
	
	@Test
	public void maxObjectSize() throws Exception {
		WorkspaceUser user = new WorkspaceUser("MOSuser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("maxObjectSize");
		ws.createWorkspace(user, wsi.getIdentifierString(), false, null, null);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("foo", "9012345678");
		ResourceUsageConfiguration oldcfg = ws.getResourceConfig();
		ResourceUsageConfigurationBuilder build =
				new ResourceUsageConfigurationBuilder(oldcfg);
		ws.setResourceConfig(build.withMaxObjectSize(20).build());
		saveObject(user, wsi, null, data, SAFE_TYPE1, "foo", new Provenance(user)); //should work
		data.put("foo", "90123456789");
		failSave(user, wsi, Arrays.asList(
				new WorkspaceSaveObject(getRandomName(), data, SAFE_TYPE1, null,
				new Provenance(user), false)), new IllegalArgumentException(String.format(
						"Object #1, %s data size 21 exceeds limit of 20", getLastRandomName())));
		ws.setResourceConfig(oldcfg);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void maxReturnedObjectSize() throws Exception {

		TypeDefId reftype = new TypeDefId(
				new TypeDefName("CopyRev", "RefType"), 1, 0);
		WorkspaceUser user = new WorkspaceUser("MROSuser");
		WorkspaceIdentifier wsiorig = new WorkspaceIdentifier("maxReturnedObjectSize");
		ws.createWorkspace(user, wsiorig.getIdentifierString(),
				false, null, null).getId();
		WorkspaceIdentifier wsi = new WorkspaceIdentifier(1);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("fo", "90");
		data.put("ba", "3");
		saveObject(user, wsi, null, data, SAFE_TYPE1,
				"foo", new Provenance(user));
		ObjectIdentifier oi1 = new ObjectIdentifier(wsi, 1, 1);
		saveObject(user, wsi, null, data, SAFE_TYPE1, "foo2", new Provenance(user));
		ObjectIdentifier oi2 = new ObjectIdentifier(wsi, 2, 1);
		List<ObjectIdentifier> oi1l = Arrays.asList(oi1);
		List<ObjectIdentifier> oi2l = Arrays.asList(oi2);
		Map<String, Object> refdata = new HashMap<String, Object>();
		refdata.put("refs", Arrays.asList(wsiorig.getName() + "/foo/1"));
		saveObject(user, wsi, null, refdata, reftype, "ref", new Provenance(user));
		refdata.put("refs", Arrays.asList(wsiorig.getName() + "/foo2/1"));
		saveObject(user, wsi, null, refdata, reftype, "ref2", new Provenance(user));
		ObjectIdentifier ref = new ObjectIdentifier(wsi, "ref", 1);
		ObjectIdentifier ref2 = new ObjectIdentifier(wsi, "ref2", 1);
		List<ObjectIdentifier> refchain = new LinkedList<ObjectIdentifier>(
				Arrays.asList(new ObjectIDWithRefPath(ref, oi1l)));
		List<ObjectIDWithRefPath> refchain2 = 
				Arrays.asList(new ObjectIDWithRefPath(ref, oi1l),
				new ObjectIDWithRefPath(ref2, oi2l));
		TestCommon.assertNoTempFilesExist(tfm);
		
		ResourceUsageConfiguration oldcfg = ws.getResourceConfig();
		try {
			ResourceUsageConfigurationBuilder build =
					new ResourceUsageConfigurationBuilder(
							oldcfg).withMaxObjectSize(1);
			
			ws.setResourceConfig(build.withMaxReturnedDataSize(20).build());
			List<ObjectIdentifier> ois1l = new LinkedList<ObjectIdentifier>(
					Arrays.asList(new ObjIDWithRefPathAndSubset(oi1, null,
					new SubsetSelection(Arrays.asList("/fo")))));
			List<ObjectIdentifier> ois1lmt = new LinkedList<ObjectIdentifier>(
					Arrays.asList(new ObjIDWithRefPathAndSubset(oi1, null,
					new SubsetSelection(new ArrayList<String>()))));
			successGetObjects(user, oi1l);
			destroyGetObjectsResources(ws.getObjects(user, ois1l));
			destroyGetObjectsResources(ws.getObjects(user, ois1lmt));
			destroyGetObjectsResources(ws.getObjects(user, refchain));
			TestCommon.assertNoTempFilesExist(tfm);
			ws.setResourceConfig(build.withMaxReturnedDataSize(19).build());
			String errstr = "Too much data requested from the workspace at once; data requested " + 
					"including potential subsets is %sB which exceeds maximum of %s.";
			IllegalArgumentException err = new IllegalArgumentException(String.format(errstr, 20, 19));
			failGetObjects(user, oi1l, err, true);
			TestCommon.assertNoTempFilesExist(tfm);
			failGetSubset(user, (List<ObjIDWithRefPathAndSubset>)(List<?>) ois1l, err);
			TestCommon.assertNoTempFilesExist(tfm);
			failGetSubset(user, (List<ObjIDWithRefPathAndSubset>)(List<?>) ois1lmt, err);
			TestCommon.assertNoTempFilesExist(tfm);
			failGetReferencedObjects(user,
					(List<ObjectIDWithRefPath>)(List<?>) refchain, err, true);
			TestCommon.assertNoTempFilesExist(tfm);
			
			ws.setResourceConfig(build.withMaxReturnedDataSize(40).build());
			List<ObjectIdentifier> two = Arrays.asList(oi1, oi2);
			List<ObjectIdentifier> mixed = Arrays.asList(oi1,
					new ObjectIDWithRefPath(ref2, oi2l));
			List<ObjIDWithRefPathAndSubset> ois1l2 = Arrays.asList(
					new ObjIDWithRefPathAndSubset(oi1, null, new SubsetSelection(Arrays.asList("/fo"))),
					new ObjIDWithRefPathAndSubset(oi1, null, new SubsetSelection(Arrays.asList("/ba"))));
			List<ObjIDWithRefPathAndSubset> bothoi = Arrays.asList(
					new ObjIDWithRefPathAndSubset(oi1, null, new SubsetSelection(Arrays.asList("/fo"))),
					new ObjIDWithRefPathAndSubset(oi2, null, new SubsetSelection(Arrays.asList("/ba"))));
			successGetObjects(user, two);
			successGetObjects(user, mixed);
			destroyGetObjectsResources(ws.getObjects(user,
					(List<ObjectIdentifier>)(List<?>) ois1l2));
			destroyGetObjectsResources(ws.getObjects(user,
					(List<ObjectIdentifier>)(List<?>) bothoi));
			destroyGetObjectsResources(ws.getObjects(user,
					(List<ObjectIdentifier>)(List<?>) refchain2));
			TestCommon.assertNoTempFilesExist(tfm);
			ws.setResourceConfig(build.withMaxReturnedDataSize(39).build());
			err = new IllegalArgumentException(String.format(errstr, 40, 39));
			failGetObjects(user, two, err, true);
			TestCommon.assertNoTempFilesExist(tfm);
			failGetObjects(user, mixed, err, true);
			TestCommon.assertNoTempFilesExist(tfm);
			failGetSubset(user, ois1l2, err);
			TestCommon.assertNoTempFilesExist(tfm);
			failGetSubset(user, bothoi, err);
			TestCommon.assertNoTempFilesExist(tfm);
			failGetReferencedObjects(user, refchain2, err, true);
			TestCommon.assertNoTempFilesExist(tfm);
			
			List<ObjectIdentifier> all = new LinkedList<ObjectIdentifier>();
			all.addAll(ois1l2);
			all.addAll(bothoi);
			ws.setResourceConfig(build.withMaxReturnedDataSize(60).build());
			destroyGetObjectsResources(ws.getObjects(user, all));
			ws.setResourceConfig(build.withMaxReturnedDataSize(59).build());
			err = new IllegalArgumentException(String.format(errstr, 60, 59));
			failGetSubset(user, (List<ObjIDWithRefPathAndSubset>)(List<?>) all, err);
			TestCommon.assertNoTempFilesExist(tfm);
		} finally {
			ws.setResourceConfig(oldcfg);
		}
	}
	
	@Test
	public void useFileVsMemoryForData() throws Exception {
		WorkspaceUser user = new WorkspaceUser("sortfilemem");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("sortFileMem");
		ws.createWorkspace(user, wsi.getIdentifierString(), false, null, null);
		Map<String, Object> data1 = new LinkedHashMap<String, Object>();
		data1.put("z", 1);
		data1.put("y", 2);
		
		Provenance p = new Provenance(user);
		List<WorkspaceSaveObject> objs = new ArrayList<WorkspaceSaveObject>();
		
		objs.add(new WorkspaceSaveObject(getRandomName(), data1, SAFE_TYPE1, null, p, false));
		
		final int[] filesCreated = {0};
		TempFileListener listener = new TempFileListener() {
			
			@Override
			public void createdTempFile(File f) {
				filesCreated[0]++;
				
			}
		};
		ws.getTempFilesManager().addListener(listener);
		ws.getTempFilesManager().cleanup(); //these tests don't clean up after each test
		ResourceUsageConfiguration oldcfg = ws.getResourceConfig();
		ResourceUsageConfigurationBuilder build =
				new ResourceUsageConfigurationBuilder(oldcfg);
		
		//single file stays in memory
		ws.setResourceConfig(build.withMaxIncomingDataMemoryUsage(13).build());
		ws.saveObjects(user, wsi, objs, getIdFactory());
		assertThat("created no temp files on save", filesCreated[0], is(0));
		ws.setResourceConfig(build.withMaxReturnedDataMemoryUsage(13).build());
		ObjectIdentifier oi1 = new ObjectIdentifier(wsi, 1);
		ws.getObjects(user, Arrays.asList(oi1));
		assertThat("created no temp files on get", filesCreated[0], is(0));
		ws.getObjects(user, new ArrayList<ObjectIdentifier>(Arrays.asList(
				new ObjIDWithRefPathAndSubset(oi1, null,
				new SubsetSelection(Arrays.asList("z")))))).get(0).getSerializedData().destroy();
		assertThat("created 1 temp file on get subdata", filesCreated[0], is(1));
		TestCommon.assertNoTempFilesExist(ws.getTempFilesManager());
		
		//files go to disk except for small subdata
		filesCreated[0] = 0;
		ws.setResourceConfig(build.withMaxIncomingDataMemoryUsage(12).build());
		objs.set(0, renameWSO(objs.get(0), "foo"));
		ws.saveObjects(user, wsi, objs, getIdFactory());
		assertThat("created temp files on save", filesCreated[0], is(2));
		TestCommon.assertNoTempFilesExist(ws.getTempFilesManager());
		
		filesCreated[0] = 0;
		ws.setResourceConfig(build.withMaxReturnedDataMemoryUsage(12).build());
		ObjectIdentifier oi2 = new ObjectIdentifier(wsi, 2);
		ws.getObjects(user, Arrays.asList(oi2)).get(0).getSerializedData().destroy();
		assertThat("created 1 temp files on get", filesCreated[0], is(1));
		TestCommon.assertNoTempFilesExist(ws.getTempFilesManager());
		
		filesCreated[0] = 0;
		ws.getObjects(user, new ArrayList<ObjectIdentifier>(Arrays.asList(
				new ObjIDWithRefPathAndSubset(oi2, null,
				new SubsetSelection(Arrays.asList("z")))))).get(0).getSerializedData().destroy();
		assertThat("created 1 temp files on get subdata part object", filesCreated[0], is(1));
		TestCommon.assertNoTempFilesExist(ws.getTempFilesManager());
		
		filesCreated[0] = 0;
		ws.getObjects(user, new ArrayList<ObjectIdentifier>(Arrays.asList(
				new ObjIDWithRefPathAndSubset(oi2, null,
				new SubsetSelection(Arrays.asList("z", "y")))))).get(0).getSerializedData().destroy();
		assertThat("created 2 temp files on get subdata full object", filesCreated[0], is(2));
		TestCommon.assertNoTempFilesExist(ws.getTempFilesManager());
		
		// test with multiple objects
		Map<String, Object> data2 = new LinkedHashMap<String, Object>();
		data2.put("w", 1);
		data2.put("f", 2);
		//already sorted so only one temp file will be created
		Map<String, Object> data3 = new LinkedHashMap<String, Object>();
		data3.put("x", 1);
		data3.put("z", 2);
		objs.set(0, renameWSO(objs.get(0), "foo1"));
		objs.add(new WorkspaceSaveObject(getRandomName(), data2, SAFE_TYPE1, null, p, false));
		objs.add(new WorkspaceSaveObject(getRandomName(), data3, SAFE_TYPE1, null, p, false));

		//multiple objects in memory
		filesCreated[0] = 0;
		ws.setResourceConfig(build.withMaxIncomingDataMemoryUsage(39).build());
		ws.saveObjects(user, wsi, objs, getIdFactory());
		assertThat("created no temp files on save", filesCreated[0], is(0));
		
		ws.setResourceConfig(build.withMaxReturnedDataMemoryUsage(39).build());
		List<ObjectIdentifier> ois = Arrays.asList(new ObjectIdentifier(wsi, 3),
				new ObjectIdentifier(wsi, 4), new ObjectIdentifier(wsi, 5));
		for (WorkspaceObjectData wod: ws.getObjects(user, ois)) {
			wod.getSerializedData().destroy();
		}
		assertThat("created no temp files on get", filesCreated[0], is(0));
		TestCommon.assertNoTempFilesExist(ws.getTempFilesManager());
		
		//multiple objects to file
		ws.setResourceConfig(build.withMaxIncomingDataMemoryUsage(38).build());
		filesCreated[0] = 0;
		objs.set(0, renameWSO(objs.get(0), "foo2"));
		objs.set(1, renameWSO(objs.get(1), "bar"));
		objs.set(2, renameWSO(objs.get(2), "baz"));
		ws.saveObjects(user, wsi, objs, getIdFactory());
		// two files per data - 1 for relabeling, 1 for sort
		// except for sorted file, then just 1 for relabeling
		assertThat("created temp files on save", filesCreated[0], is(5));
		TestCommon.assertNoTempFilesExist(ws.getTempFilesManager());
		
		filesCreated[0] = 0;
		ws.setResourceConfig(build.withMaxReturnedDataMemoryUsage(38).build());
		for (WorkspaceObjectData wod: ws.getObjects(user, ois)) {
			wod.getSerializedData().destroy();
		}
		assertThat("created 1 temp files on get", filesCreated[0], is(1));
		TestCommon.assertNoTempFilesExist(ws.getTempFilesManager());
		
		filesCreated[0] = 0;
		ws.setResourceConfig(build.withMaxReturnedDataMemoryUsage(25).build());
		for (WorkspaceObjectData wod: ws.getObjects(user, ois)) {
			wod.getSerializedData().destroy();
		}
		assertThat("created 2 temp files on get", filesCreated[0], is(2));
		TestCommon.assertNoTempFilesExist(ws.getTempFilesManager());
		
		//test with a referenced object and a standard object
		TypeDefId reftype = new TypeDefId(
				new TypeDefName("CopyRev", "RefType"), 1, 0);
		Map<String, Object> refdata = new HashMap<String, Object>();
		refdata.put("refs", Arrays.asList(wsi.getName() + "/2/1"));
		saveObject(user, wsi, null, refdata, reftype, "ref", new Provenance(user));
		ObjectIdentifier ref = new ObjectIdentifier(wsi, "ref", 1);
		List<ObjectIdentifier> refAndStd = Arrays.asList(oi1,
				new ObjectIDWithRefPath(ref, Arrays.asList(oi2)));

		// ref obj and std obj in memory
		filesCreated[0] = 0;
		ws.setResourceConfig(build.withMaxReturnedDataMemoryUsage(39).build());
		for (WorkspaceObjectData wod: ws.getObjects(user, refAndStd)) {
			wod.getSerializedData().destroy();
		}
		assertThat("created no temp files on get", filesCreated[0], is(0));
		TestCommon.assertNoTempFilesExist(ws.getTempFilesManager());
		
		// ref obj and std obj to files
		filesCreated[0] = 0;
		ws.setResourceConfig(build.withMaxReturnedDataMemoryUsage(38).build());
		for (WorkspaceObjectData wod: ws.getObjects(user, ois)) {
			wod.getSerializedData().destroy();
		}
		assertThat("created 1 temp files on get", filesCreated[0], is(1));
		TestCommon.assertNoTempFilesExist(ws.getTempFilesManager());
		
		filesCreated[0] = 0;
		ws.setResourceConfig(build.withMaxReturnedDataMemoryUsage(25).build());
		for (WorkspaceObjectData wod: ws.getObjects(user, ois)) {
			wod.getSerializedData().destroy();
		}
		assertThat("created 2 temp files on get", filesCreated[0], is(2));
		TestCommon.assertNoTempFilesExist(ws.getTempFilesManager());
		
		// clean up and reset config
		ws.getTempFilesManager().removeListener(listener);
		ws.setResourceConfig(oldcfg);
	}
	
	@Test
	public void storedDataIsSorted() throws Exception {
		WorkspaceUser user = new WorkspaceUser("dataIsSorted");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("dataissorted");
		ws.createWorkspace(user, wsi.getIdentifierString(), false, null, null);
		Map<String, Object> data1 = new LinkedHashMap<String, Object>();
		data1.put("z", 1);
		data1.put("y", 2);
		String expected = "{\"y\":2,\"z\":1}";
		
		Provenance p = new Provenance(user);
		List<WorkspaceSaveObject> objs = new ArrayList<WorkspaceSaveObject>();
		objs.add(new WorkspaceSaveObject(getRandomName(), data1, SAFE_TYPE1, null, p, false));
		ws.saveObjects(user, wsi, objs, getIdFactory());
		WorkspaceObjectData o = ws.getObjects(
				user, Arrays.asList(new ObjectIdentifier(wsi, 1))).get(0);
		try {
			String data = IOUtils.toString(o.getSerializedData().getJSON());
			assertThat("data is sorted", data, is(expected));
		} finally {
			destroyGetObjectsResources(Arrays.asList(o));
		}
		assertThat("data marked as sorted", o.getSerializedData().isSorted(),
				is(true));
	}
	
	@Test
	public void exceedSortMemory() throws Exception {
		WorkspaceUser user = new WorkspaceUser("exceedSortMem");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("exceedsortmem");
		ws.createWorkspace(user, wsi.getIdentifierString(), false, null, null);
		Provenance p = new Provenance(user);
		List<WorkspaceSaveObject> objs = new ArrayList<WorkspaceSaveObject>();
		
		String safejson = "{\"z\":\"a\"}";
		String json = "{\"z\":\"a\",\"b\":\"d\"}";
		objs.add(new WorkspaceSaveObject(getRandomName(), new JsonTokenStream(safejson),
				SAFE_TYPE1, null, p, false));
		objs.add(new WorkspaceSaveObject(getRandomName(), new JsonTokenStream(json), SAFE_TYPE1,
				null, p, false));
		
		ResourceUsageConfiguration oldcfg = ws.getResourceConfig();
		ResourceUsageConfigurationBuilder build =
				new ResourceUsageConfigurationBuilder(oldcfg)
				.withMaxIncomingDataMemoryUsage(1);
		int maxmem = 8 + 64 + 8 + 64;
		ws.setResourceConfig(build.withMaxRelabelAndSortMemoryUsage(maxmem).build());
		ws.saveObjects(user, wsi, objs, getIdFactory());
		
		ws.setResourceConfig(build.withMaxRelabelAndSortMemoryUsage(maxmem - 1).build());
		try {
			ws.saveObjects(user, wsi, objs, getIdFactory());
			fail("sorted w/ too little mem");
		} catch (TypedObjectValidationException tove) {
			assertThat("got correct exception", tove.getMessage(), is(String.format(
					"Object #2, %s: Memory necessary for sorting map keys exceeds the limit " + 
					"%s bytes at /", getLastRandomName(), maxmem - 1)));
		}
		ws.setResourceConfig(oldcfg);
	}
}
