package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import junit.framework.Assert;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.ObjectPaths;
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
import us.kbase.workspace.database.ModuleInfo;
import us.kbase.workspace.database.ObjectChain;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Provenance.ExternalData;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.Provenance.ProvenanceAction;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder.ResourceUsageConfiguration;
import us.kbase.workspace.database.SubObjectIdentifier;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceObjectInformation;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchReferenceException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;
import us.kbase.workspace.test.kbase.JSONRPCLayerTester;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WorkspaceTest extends WorkspaceTester {


	public WorkspaceTest(String config, String backend,
			Integer maxMemoryUsePerCall) throws Exception {
		super(config, backend, maxMemoryUsePerCall);
	}

	private static final WorkspaceIdentifier lockWS = new WorkspaceIdentifier("lock");
	
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
		
		assertTrue("date updated on set ws desc", ltinfo2.getModDate().after(ltinfo.getModDate()));
		assertTrue("date updated on set ws desc", ltpinfo2.getModDate().after(ltpinfo.getModDate()));
		assertTrue("date updated on set ws desc", ltninfo2.getModDate().after(ltninfo.getModDate()));
		
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
		checkWSInfo(info, SOMEUSER, wsname, 0, Permission.OWNER, false, "unlocked", MT_META);
		long id = info.getId();
		WorkspaceIdentifier wsi = new WorkspaceIdentifier(id);
		Date moddate = info.getModDate();
		info = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier(id));
		checkWSInfo(info, SOMEUSER, wsname, 0, Permission.OWNER, false, id, moddate, "unlocked", MT_META);
		info = ws.getWorkspaceInformation(SOMEUSER, new WorkspaceIdentifier(wsname));
		checkWSInfo(info, SOMEUSER, wsname, 0, Permission.OWNER, false, id, moddate, "unlocked", MT_META);
		
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("foo", "bar");
		meta.put("baz", "bash");
		WorkspaceInformation info2 = ws.createWorkspace(SOMEUSER, "foo2", true, "eeswaffertheen2", meta);
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
		checkWSInfo(info, anotheruser, "anotherfnuser:MrT", 0, Permission.OWNER, true, "unlocked", MT_META);
		id = info.getId();
		moddate = info.getModDate();
		info = ws.getWorkspaceInformation(anotheruser, new WorkspaceIdentifier(id));
		checkWSInfo(info, anotheruser, "anotherfnuser:MrT", 0, Permission.OWNER, true, id, moddate, "unlocked", MT_META);
		info = ws.getWorkspaceInformation(anotheruser, new WorkspaceIdentifier("anotherfnuser:MrT"));
		checkWSInfo(info, anotheruser, "anotherfnuser:MrT", 0, Permission.OWNER, true, id, moddate, "unlocked", MT_META);
		
		Map<String, String> bigmeta = new HashMap<String, String>();
		for (int i = 0; i < 141; i++) {
			bigmeta.put("thing" + i, TEXT100);
		}
		ws.createWorkspace(SOMEUSER, "foo3", false, "eeswaffertheen", bigmeta);
		bigmeta.put("thing", TEXT100);
		try {
			ws.createWorkspace(SOMEUSER, "foo4", false, "eeswaffertheen", bigmeta);
			fail("created ws with > 16kb metadata");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Metadata size of 16076 is > 16000 bytes"));
		}
		
		ws.setGlobalPermission(anotheruser, new WorkspaceIdentifier("anotherfnuser:MrT"), Permission.NONE);
		ws.setGlobalPermission(SOMEUSER, new WorkspaceIdentifier("foo2"), Permission.NONE);
	}
	
	@Test
	public void workspaceMetadata() throws Exception {
		WorkspaceUser user = new WorkspaceUser("blahblah");
		WorkspaceUser user2 = new WorkspaceUser("blahblah2");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("workspaceMetadata");
		WorkspaceIdentifier wsiNo = new WorkspaceIdentifier("workspaceNoMetadata");
		WorkspaceIdentifier wsiNo2 = new WorkspaceIdentifier("workspaceNoMetadata2");
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("foo", "bar");
		meta.put("foo2", "bar2");
		meta.put("some", "meta");
		WorkspaceInformation info = ws.createWorkspace(user, wsi.getName(), false, null, meta);
		ws.setPermissions(user, wsi, Arrays.asList(user2), Permission.ADMIN);
		checkWSInfo(info, user, wsi.getName(), 0, Permission.OWNER, false, info.getId(), info.getModDate(), "unlocked", meta);
		checkWSInfo(wsi, user, wsi.getName(), 0, Permission.OWNER, false, info.getId(), info.getModDate(), "unlocked", meta);
		WorkspaceInformation infoNo = ws.createWorkspace(user, wsiNo.getName(), false, null, null);
		checkWSInfo(infoNo, user, wsiNo.getName(), 0, Permission.OWNER, false, infoNo.getId(), infoNo.getModDate(), "unlocked", MT_META);
		checkWSInfo(wsiNo, user, wsiNo.getName(), 0, Permission.OWNER, false, infoNo.getId(), infoNo.getModDate(), "unlocked", MT_META);
		WorkspaceInformation infoNo2 = ws.createWorkspace(user, wsiNo2.getName(), false, null, null);
		
		
		meta.put("foo2", "bar3"); //replace
		Map<String, String> putmeta = new HashMap<String, String>();
		putmeta.put("foo2", "bar3");
		ws.setWorkspaceMetadata(user, wsi, putmeta);
		Date d1 = checkWSInfo(wsi, user, wsi.getName(), 0, Permission.OWNER, false, info.getId(), "unlocked", meta);
		meta.put("foo3", "bar4"); //new
		putmeta.clear();
		putmeta.put("foo3", "bar4");
		ws.setWorkspaceMetadata(user, wsi, putmeta);
		Date d2 = checkWSInfo(wsi, user, wsi.getName(), 0, Permission.OWNER, false, info.getId(), "unlocked", meta);
		
		putmeta.clear();
		putmeta.put("foo3", "bar5"); //replace
		putmeta.put("some.garbage", "with.dots"); //new
		putmeta.put("foo", "whoa this is new"); //replace
		putmeta.put("no, this part is new", "prunker"); //new
		meta.put("foo3", "bar5");
		meta.put("some.garbage", "with.dots");
		meta.put("foo", "whoa this is new");
		meta.put("no, this part is new", "prunker");
		ws.setWorkspaceMetadata(user, wsi, putmeta);
		Date d3 = checkWSInfo(wsi, user, wsi.getName(), 0, Permission.OWNER, false, info.getId(), "unlocked", meta);
		
		Map<String, String> newmeta = new HashMap<String, String>();
		newmeta.put("new", "meta");
		ws.setWorkspaceMetadata(user, wsiNo, newmeta);
		Date nod1 = checkWSInfo(wsiNo, user, wsiNo.getName(), 0, Permission.OWNER, false, infoNo.getId(), "unlocked", newmeta);
		
		assertDatesAscending(infoNo.getModDate(), nod1);
		
		meta.remove("foo2");
		ws.removeWorkspaceMetadata(user, wsi, "foo2");
		Date d4 = checkWSInfo(wsi, user, wsi.getName(), 0, Permission.OWNER, false, info.getId(), "unlocked", meta);
		meta.remove("some");
		ws.removeWorkspaceMetadata(user2, wsi, "some");
		Date d5 = checkWSInfo(wsi, user, wsi.getName(), 0, Permission.OWNER, false, info.getId(), "unlocked", meta);
		ws.removeWorkspaceMetadata(user, wsi, "fake"); //no effect
		checkWSInfo(wsi, user, wsi.getName(), 0, Permission.OWNER, false, info.getId(), d5, "unlocked", meta);
		
		assertDatesAscending(info.getModDate(), d1, d2, d3, d4, d5);
		
		checkWSInfo(wsiNo2, user, wsiNo2.getName(), 0, Permission.OWNER, false, infoNo2.getId(), infoNo2.getModDate(), "unlocked", MT_META);
		ws.removeWorkspaceMetadata(user, wsiNo2, "somekey"); //should do nothing
		checkWSInfo(wsiNo2, user, wsiNo2.getName(), 0, Permission.OWNER, false, infoNo2.getId(), infoNo2.getModDate(), "unlocked", MT_META);
		
		
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
		ws.createWorkspace(user, "wsmetafake", false, null, putmeta); //should work
		failWSSetMeta(user, wsi, putmeta, new IllegalArgumentException(
				"Updated metadata size of 16013 is > 16000 bytes"));
		
		ws.setWorkspaceMetadata(user, wsiNo, putmeta); //should work
		putmeta.put("148", TEXT100);
		failWSSetMeta(user, wsiNo2, putmeta, new IllegalArgumentException(
				"Updated metadata size of 16023 is > 16000 bytes"));
		
		failWSSetMeta(user, wsi, null, new IllegalArgumentException(
				"Metadata cannot be null or empty"));
		failWSSetMeta(user, wsi, MT_META, new IllegalArgumentException(
				"Metadata cannot be null or empty"));
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
		userWS.add(new TestRig(crap, "64",
				"Workspace names cannot be integers: 64"));
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
		WorkspaceInformation wsinfo = ws.setWorkspaceOwner(u1, wsi, u2, null, false);
		checkWSInfo(wsinfo, u2, wsi.getName(), 0L, Permission.OWNER, false, "unlocked", mt);
		Map<User, Permission> pexp = new HashMap<User, Permission>();
		pexp.put(u1, Permission.ADMIN);
		pexp.put(u2, Permission.OWNER);
		assertThat("permissions correct", ws.getPermissions(u2, wsi), is (pexp));
		
		failSetWorkspaceOwner(null, wsi, u2, null, true,
				new IllegalArgumentException("bar already owns workspace wsfoo"));
		failSetWorkspaceOwner(u2, wsi, u2, null, false,
				new IllegalArgumentException("bar already owns workspace wsfoo"));
		
		failSetWorkspaceOwner(null, wsi, null, null, true,
				new NullPointerException("newUser cannot be null"));
		failSetWorkspaceOwner(u2, wsi, null, null, false,
				new NullPointerException("newUser cannot be null"));
		
		failSetWorkspaceOwner(null, null, u1, null, true,
				new NullPointerException("wsi cannot be null"));
		failSetWorkspaceOwner(u2, null, u1, null, false,
				new NullPointerException("wsi cannot be null"));
		
		WorkspaceIdentifier fake = new WorkspaceIdentifier("wsfoofake");
		failSetWorkspaceOwner(null, fake, u2, null, true,
				new NoSuchWorkspaceException("No workspace with name wsfoofake exists", fake));
		failSetWorkspaceOwner(u2, fake, u2, null, false,
				new NoSuchWorkspaceException("No workspace with name wsfoofake exists", fake));
		
		failSetWorkspaceOwner(null, wsi, u1, null, false,
				new WorkspaceAuthorizationException("Anonymous users may not change the owner of workspace wsfoo"));
		failSetWorkspaceOwner(u1, wsi, u1, null, false,
				new WorkspaceAuthorizationException("User foo may not change the owner of workspace wsfoo"));
		
		//test as admin
		wsinfo = ws.setWorkspaceOwner(null, wsi, u1, null, true);
		checkWSInfo(wsinfo, u1, wsi.getName(), 0L, Permission.OWNER, false, "unlocked", mt);
		pexp.put(u1, Permission.OWNER);
		pexp.put(u2, Permission.ADMIN);
		assertThat("permissions correct", ws.getPermissions(u2, wsi), is (pexp));
		
		//test basic name change
		wsinfo = ws.setWorkspaceOwner(u1, wsi, u2, "wsfoonew", false);
		checkWSInfo(wsinfo, u2, "wsfoonew", 0L, Permission.OWNER, false, "unlocked", mt);
		wsi = new WorkspaceIdentifier("wsfoonew");
		
		//illegal name change to invalid user
		failSetWorkspaceOwner(u2, wsi, u1, "bar:wsfoo", false,
				new IllegalArgumentException("Workspace name bar:wsfoo must only contain the user name foo prior to the : delimiter"));
		failSetWorkspaceOwner(null, wsi, u1, "bar:wsfoo", true,
				new IllegalArgumentException("Workspace name bar:wsfoo must only contain the user name foo prior to the : delimiter"));
		
		//test auto rename of workspace
		ws.renameWorkspace(u2, wsi, "bar:wsfoo");
		wsi = new WorkspaceIdentifier("bar:wsfoo");
		wsinfo = ws.setWorkspaceOwner(u2, wsi, u1, null, false);
		wsi = new WorkspaceIdentifier("foo:wsfoo");
		checkWSInfo(wsinfo, u1, wsi.getName(), 0L, Permission.OWNER, false, "unlocked", mt);
		
		//test manual rename of workspace
		wsinfo = ws.setWorkspaceOwner(u1, wsi, u2, "bar:wsfoo", false);
		wsi = new WorkspaceIdentifier("bar:wsfoo");
		checkWSInfo(wsinfo, u2, wsi.getName(), 0L, Permission.OWNER, false, "unlocked", mt);
		
		//test rename to preexisting workspace
		ws.createWorkspace(u1, "foo:wsfoo2", false, null, null);
		failSetWorkspaceOwner(u2, wsi, u1, "foo:wsfoo2", false,
				new IllegalArgumentException("There is already a workspace named foo:wsfoo2"));
		failSetWorkspaceOwner(null, wsi, u1, "foo:wsfoo2", true,
				new IllegalArgumentException("There is already a workspace named foo:wsfoo2"));
		
		//test rename with same name
		ws.renameWorkspace(u2, wsi, "wsfoo");
		wsi = new WorkspaceIdentifier("wsfoo");
		wsinfo = ws.setWorkspaceOwner(u2, wsi, u1, "wsfoo", false);
		checkWSInfo(wsinfo, u1, wsi.getName(), 0L, Permission.OWNER, false, "unlocked", mt);
	}
	
	
	private void failSetWorkspaceOwner(WorkspaceUser user, WorkspaceIdentifier wsi,
			WorkspaceUser newuser, String name, boolean asAdmin,
			Exception expected) throws Exception {
		try {
			ws.setWorkspaceOwner(user, wsi, newuser, name, asAdmin);
			fail("expected set owner to fail");
		} catch (Exception got) {
			assertThat("correct exception", got.getLocalizedMessage(),
					is(expected.getLocalizedMessage()));
			assertThat("correct exception type", got, is(expected.getClass()));
		}
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
		assertThat("ws has correct perms for owner", ws.getPermissions(AUSER, wsiNG), is(expect));
		expect.put(STARUSER, Permission.READ);
		assertThat("ws has correct perms for owner", ws.getPermissions(AUSER, wsiGL), is(expect));
		expect.clear();
		expect.put(BUSER, Permission.NONE);
		assertThat("ws has correct perms for random user", ws.getPermissions(BUSER, wsiNG), is(expect));
		expect.put(STARUSER, Permission.READ);
		assertThat("ws has correct perms for random user", ws.getPermissions(BUSER, wsiGL), is(expect));
		//test read permissions
		assertThat("can read public workspace description", ws.getWorkspaceDescription(null, wsiGL),
				is("globaldesc"));
		WorkspaceInformation info = ws.getWorkspaceInformation(null, wsiGL);
		checkWSInfo(info, AUSER, "perms_global", 0, Permission.NONE, true, "unlocked", MT_META);
		ws.setPermissions(AUSER, wsiNG, Arrays.asList(AUSER, BUSER, CUSER), Permission.READ);
		expect.clear();
		expect.put(AUSER, Permission.OWNER);
		expect.put(BUSER, Permission.READ);
		expect.put(CUSER, Permission.READ);
		assertThat("ws doesn't replace owner perms", ws.getPermissions(AUSER, wsiNG), is(expect));
		expect.clear();
		expect.put(BUSER, Permission.READ);
		assertThat("no permission leakage", ws.getPermissions(BUSER, wsiNG), is(expect));
		
		failSetPermissions(BUSER, wsiNG, Arrays.asList(AUSER, BUSER, CUSER), Permission.READ,
				new WorkspaceAuthorizationException(
						"User b may not alter other user's permissions on workspace perms_noglobal"));
		failSetPermissions(BUSER, wsiNG, Arrays.asList(BUSER), Permission.WRITE,
				new WorkspaceAuthorizationException(
						"User b may only reduce their permission level on workspace perms_noglobal"));
		
		//asAdmin testing
		ws.setPermissions(BUSER, wsiNG, Arrays.asList(BUSER), Permission.ADMIN, true);
		expect.put(AUSER, Permission.OWNER);
		expect.put(BUSER, Permission.ADMIN);
		expect.put(CUSER, Permission.READ);
		assertThat("asAdmin boolean works", ws.getPermissions(BUSER, wsiNG), is(expect));
		ws.setPermissions(BUSER, wsiNG, Arrays.asList(BUSER), Permission.READ);
		expect.clear();
		expect.put(BUSER, Permission.READ);
		assertThat("reduce own permissions", ws.getPermissions(BUSER, wsiNG), is(expect));
		ws.setPermissions(null, wsiNG, Arrays.asList(BUSER), Permission.ADMIN, true);
		expect.put(AUSER, Permission.OWNER);
		expect.put(BUSER, Permission.ADMIN);
		expect.put(CUSER, Permission.READ);
		assertThat("asAdmin boolean works with null user",
				ws.getPermissions(BUSER, wsiNG), is(expect));
		ws.setPermissions(AUSER, wsiNG, Arrays.asList(BUSER), Permission.READ);
		expect.clear();
		expect.put(BUSER, Permission.READ);
		assertThat("reduced permissions", ws.getPermissions(BUSER, wsiNG), is(expect));
		
		
		ws.setPermissions(BUSER, wsiNG, Arrays.asList(BUSER), Permission.READ); //should have no effect
		expect.clear();
		expect.put(AUSER, Permission.OWNER);
		expect.put(BUSER, Permission.READ);
		expect.put(CUSER, Permission.READ);
		assertThat("user setting same perms has no effect", ws.getPermissions(AUSER, wsiNG), is(expect));
		expect.clear();
		expect.put(BUSER, Permission.READ);
		assertThat("setting own perms to same has no effect", ws.getPermissions(BUSER, wsiNG), is(expect));
		
		ws.setPermissions(BUSER, wsiNG, Arrays.asList(BUSER), Permission.NONE);
		expect.clear();
		expect.put(AUSER, Permission.OWNER);
		expect.put(CUSER, Permission.READ);
		assertThat("user removed own perms", ws.getPermissions(AUSER, wsiNG), is(expect));
		expect.clear();
		expect.put(BUSER, Permission.NONE);
		assertThat("can remove own perms", ws.getPermissions(BUSER, wsiNG), is(expect));
		
		//test write permissions
		ws.setPermissions(AUSER, wsiNG, Arrays.asList(BUSER), Permission.WRITE);
		expect.put(AUSER, Permission.OWNER);
		expect.put(BUSER, Permission.WRITE);
		expect.put(CUSER, Permission.READ);
		assertThat("ws doesn't replace owner perms", ws.getPermissions(AUSER, wsiNG), is(expect));
		assertThat("write perms allow viewing all perms", ws.getPermissions(BUSER, wsiNG), is(expect));
		expect.clear();
		expect.put(CUSER, Permission.READ);
		assertThat("no permission leakage", ws.getPermissions(CUSER, wsiNG), is(expect));
		failSetPermissions(BUSER, wsiNG, Arrays.asList(AUSER, BUSER, CUSER), Permission.READ,
				new WorkspaceAuthorizationException(
						"User b may not alter other user's permissions on workspace perms_noglobal"));
		//test admin permissions
		ws.setPermissions(AUSER, wsiNG, Arrays.asList(BUSER), Permission.ADMIN);
		expect.put(AUSER, Permission.OWNER);
		expect.put(BUSER, Permission.ADMIN);
		expect.put(CUSER, Permission.READ);
		assertThat("ws doesn't replace owner perms", ws.getPermissions(AUSER, wsiNG), is(expect));
		assertThat("admin can see all perms", ws.getPermissions(BUSER, wsiNG), is(expect));
		ws.setPermissions(BUSER, wsiNG, Arrays.asList(AUSER, CUSER), Permission.WRITE);
		expect.put(CUSER, Permission.WRITE);
		assertThat("ws doesn't replace owner perms", ws.getPermissions(AUSER, wsiNG), is(expect));
		assertThat("admin can correctly set perms", ws.getPermissions(BUSER, wsiNG), is(expect));
		//test remove permissions
		ws.setPermissions(BUSER, wsiNG, Arrays.asList(AUSER, CUSER), Permission.NONE);
		expect.remove(CUSER);
		assertThat("ws doesn't replace owner perms", ws.getPermissions(AUSER, wsiNG), is(expect));
		assertThat("admin can't overwrite owner perms", ws.getPermissions(BUSER, wsiNG), is(expect));
		
		ws.setGlobalPermission(AUSER, new WorkspaceIdentifier("perms_global"), Permission.NONE);
	}
		
	@Test
	public void saveObjectsAndGetMetaSimple() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceUser bar = new WorkspaceUser("bar");
		
		IdReferenceHandlerSetFactory foofac = getIdFactory(foo);
		IdReferenceHandlerSetFactory barfac = getIdFactory(bar);
		
		WorkspaceIdentifier read = new WorkspaceIdentifier("saveobjread");
		WorkspaceIdentifier priv = new WorkspaceIdentifier("saveobj");
		WorkspaceInformation readinfo = ws.createWorkspace(
				foo, read.getIdentifierString(), true, null, null);
		WorkspaceInformation privinfo = ws.createWorkspace(
				foo, priv.getIdentifierString(), false, null, null);
		Date readLastDate = readinfo.getModDate();
		Date privLastDate = privinfo.getModDate();
		long readid = readinfo.getId();
		long privid = privinfo.getId();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> data2 = new HashMap<String, Object>();
		Map<String, String> meta = new HashMap<String, String>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		JsonNode savedata = MAPPER.valueToTree(data);
		data2.put("fubar2", moredata);
		JsonNode savedata2 = MAPPER.valueToTree(data2);
		meta.put("metastuff", "meta");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("meta2", "my hovercraft is full of eels");
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
		
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto3"), savedata, SAFE_TYPE1, meta, p, false));
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto3"), savedata2, SAFE_TYPE1, meta2, p, false));
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto3-1"), savedata, SAFE_TYPE1, meta, p, false));
		objects.add(new WorkspaceSaveObject(savedata2, SAFE_TYPE1, meta2, p, false));
		objects.add(new WorkspaceSaveObject(savedata, SAFE_TYPE1, meta, p, false));
		
		readLastDate = ws.getWorkspaceInformation(foo, read).getModDate();
		List<ObjectInformation> objinfo = ws.saveObjects(foo, read, objects, foofac);
		readLastDate = assertWorkspaceDateUpdated(foo, read, readLastDate, "ws date modified on save");
		String chksum1 = "36c4f68f2c98971b9736839232eb08f4";
		String chksum2 = "3c59f762140806c36ab48a152f28e840";
		checkObjInfo(objinfo.get(0), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(objinfo.get(1), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo.get(2), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(objinfo.get(3), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo.get(4), 4, "auto4", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		
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
		checkObjInfo(objinfo2.get(0), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2.get(1), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(objinfo2.get(2), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2.get(3), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(objinfo2.get(4), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2.get(5), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(objinfo2.get(6), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2.get(7), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		checkObjInfo(objinfo2.get(8), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2.get(9), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2.get(10), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2.get(11), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, meta2);
		checkObjInfo(objinfo2NoMeta.get(0), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, null);
		checkObjInfo(objinfo2NoMeta.get(1), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, null);
		checkObjInfo(objinfo2NoMeta.get(2), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, null);
		checkObjInfo(objinfo2NoMeta.get(3), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, null);
		checkObjInfo(objinfo2NoMeta.get(4), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, null);
		checkObjInfo(objinfo2NoMeta.get(5), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, null);
		checkObjInfo(objinfo2NoMeta.get(6), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, null);
		checkObjInfo(objinfo2NoMeta.get(7), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, null);
		checkObjInfo(objinfo2NoMeta.get(8), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, null);
		checkObjInfo(objinfo2NoMeta.get(9), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, null);
		checkObjInfo(objinfo2NoMeta.get(10), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, null);
		checkObjInfo(objinfo2NoMeta.get(11), 3, "auto3-2", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum2, 24, null);
		
		List<FakeObjectInfo> retinfo = new ArrayList<FakeObjectInfo>();
		FakeResolvedWSID fakews = new FakeResolvedWSID(read.getName(), readid);
		retinfo.add(new FakeObjectInfo(1L, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 2, foo, fakews, chksum2, 24L, meta2));
		retinfo.add(new FakeObjectInfo(1, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 1, foo, fakews, chksum1, 23, meta));
		retinfo.add(new FakeObjectInfo(1, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 2, foo, fakews, chksum2, 24, meta2));
		retinfo.add(new FakeObjectInfo(1, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 1, foo, fakews, chksum1, 23, meta));
		retinfo.add(new FakeObjectInfo(1, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 2, foo, fakews, chksum2, 24, meta2));
		retinfo.add(new FakeObjectInfo(1, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 1, foo, fakews, chksum1, 23, meta));
		retinfo.add(new FakeObjectInfo(1, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 2, foo, fakews, chksum2, 24, meta2));
		retinfo.add(new FakeObjectInfo(1, "auto3", SAFE_TYPE1.getTypeString(), new Date(), 1, foo, fakews, chksum1, 23, meta));
		retinfo.add(new FakeObjectInfo(3, "auto3-2", SAFE_TYPE1.getTypeString(), new Date(), 1, foo, fakews, chksum2, 24, meta2));
		retinfo.add(new FakeObjectInfo(3, "auto3-2", SAFE_TYPE1.getTypeString(), new Date(), 1, foo, fakews, chksum2, 24, meta2));
		retinfo.add(new FakeObjectInfo(3, "auto3-2", SAFE_TYPE1.getTypeString(), new Date(), 1, foo, fakews, chksum2, 24, meta2));
		retinfo.add(new FakeObjectInfo(3, "auto3-2", SAFE_TYPE1.getTypeString(), new Date(), 1, foo, fakews, chksum2, 24, meta2));
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
		checkObjInfo(objinfo.get(0), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum1, 23, meta2);
		objinfo2 = ws.getObjectInformation(foo, Arrays.asList(new ObjectIdentifier(read, 2)), true, false);
		checkObjInfo(objinfo2.get(0), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum1, 23, meta2);
		
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
		objinfo2 = ws.getObjectInformation(bar, Arrays.asList(new ObjectIdentifier(priv, 2)), true, false);
		checkObjInfo(objinfo2.get(0), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 2, foo, privid, priv.getName(), chksum1, 23, meta2);
		
		checkObjectAndInfo(bar, Arrays.asList(new ObjectIdentifier(priv, 2)),
				Arrays.asList(new FakeObjectInfo(2L, "auto3-1", SAFE_TYPE1.getTypeString(),
						new Date(), 2, foo, new FakeResolvedWSID(priv.getName(), privid),
						chksum1, 23L, meta2)), Arrays.asList(data));
		
		failSave(bar, priv, objects, new WorkspaceAuthorizationException("User bar may not write to workspace saveobj"));
		
		ws.setPermissions(foo, priv, Arrays.asList(bar), Permission.WRITE);
		objinfo = ws.saveObjects(bar, priv, objects, barfac);
		checkObjInfo(objinfo.get(0), 2, "auto3-1", SAFE_TYPE1.getTypeString(), 3, bar, privid, priv.getName(), chksum1, 23, meta2);
		
		failGetObjects(foo, Arrays.asList(new ObjectIdentifier(read, "booger")),
				new NoSuchObjectException("No object with name booger exists in workspace " + readid));
		failGetObjects(foo, Arrays.asList(new ObjectIdentifier(new WorkspaceIdentifier("saveAndGetFakefake"), "booger")),
				new InaccessibleObjectException("Object booger cannot be accessed: No workspace with name saveAndGetFakefake exists"));
		ws.setPermissions(foo, priv, Arrays.asList(bar), Permission.NONE);
		failGetObjects(bar, Arrays.asList(new ObjectIdentifier(priv, 3)),
				new InaccessibleObjectException("Object 3 cannot be accessed: User bar may not read workspace saveobj"));
		failGetObjects(null, Arrays.asList(new ObjectIdentifier(priv, 3)),
				new InaccessibleObjectException("Object 3 cannot be accessed: Anonymous users may not read workspace saveobj"));
		
		//test get object info where null is returned instead of exception
		List<ObjectIdentifier> nullloi = new ArrayList<ObjectIdentifier>();
		nullloi.add(new ObjectIdentifier(read, 1));
		nullloi.add(new ObjectIdentifier(read, "booger"));
		nullloi.add(new ObjectIdentifier(new WorkspaceIdentifier("saveAndGetFakefake"), "booger"));
		nullloi.add(new ObjectIdentifier(read, 1, 1));
		
		List<ObjectInformation> nullobjinfo = ws.getObjectInformation(foo, nullloi, true, true);
		checkObjInfo(nullobjinfo.get(0), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		assertNull("Obj info is null for inaccessible object", nullobjinfo.get(1));
		assertNull("Obj info is null for inaccessible object", nullobjinfo.get(2));
		checkObjInfo(nullobjinfo.get(3), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		
		nullloi.clear();
		nullloi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), "auto3"));
		nullloi.add(new ObjectIdentifier(priv, 2));
		nullloi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), "auto3", 1));
		nullloi.add(new ObjectIdentifier(priv, 3));
		nullloi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), 1));
		
		nullobjinfo = ws.getObjectInformation(bar, nullloi, false, true);
		checkObjInfo(nullobjinfo.get(0), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, null);
		assertNull("Obj info is null for inaccessible object", nullobjinfo.get(1));
		checkObjInfo(nullobjinfo.get(2), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, null);
		assertNull("Obj info is null for inaccessible object", nullobjinfo.get(1));
		checkObjInfo(nullobjinfo.get(4), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, null);
		
		nullloi.clear();
		nullloi.add(new ObjectIdentifier(new WorkspaceIdentifier(readid), 1, 1));
		nullloi.add(new ObjectIdentifier(priv, 3));
		nullloi.add(new ObjectIdentifier(read, "auto3"));
		
		nullobjinfo = ws.getObjectInformation(null, nullloi, true, true);
		checkObjInfo(nullobjinfo.get(0), 1, "auto3", SAFE_TYPE1.getTypeString(), 1, foo, readid, read.getName(), chksum1, 23, meta);
		assertNull("Obj info is null for inaccessible object", nullobjinfo.get(1));
		checkObjInfo(nullobjinfo.get(2), 1, "auto3", SAFE_TYPE1.getTypeString(), 2, foo, readid, read.getName(), chksum2, 24, meta2);
		
		ws.setObjectsDeleted(foo, Arrays.asList(new ObjectIdentifier(priv, 3)), true);
		ws.setWorkspaceDeleted(foo, read, true);
		
		nullobjinfo = ws.getObjectInformation(null, nullloi, true, true);
		assertNull("Obj info is null for inaccessible object", nullobjinfo.get(0));
		assertNull("Obj info is null for inaccessible object", nullobjinfo.get(1));
		assertNull("Obj info is null for inaccessible object", nullobjinfo.get(2));
		
		ws.setWorkspaceDeleted(foo, read, false);
		ws.setGlobalPermission(foo, read, Permission.NONE);
	}
	
	@Test
	public void saveObjectsAndTestExtractedMeta() throws Exception {
		String module = "TestMetaData";
		String spec =
				"module " + module + " {" +
					"/* @metadata ws val \n@metadata ws length(l) as Length of list*/"+
					"typedef structure { string val; list<int> l; } MyType;" +
				"};";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		ws.requestModuleRegistration(userfoo, module);
		ws.resolveModuleRegistration(module, true);
		ws.compileNewTypeSpec(userfoo, spec, Arrays.asList("MyType"), null, null, false, null);
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
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("d1"),d1, MyType, metadata, emptyprov, false)),
				getIdFactory(userfoo));
		List <ObjectInformation> oi = ws.getObjectInformation(userfoo, Arrays.asList(new ObjectIdentifier(wspace, "d1")), true, true);
		Assert.assertNotNull("Getting back an object that was saved with automatic metadata extraction", oi);
		Assert.assertNotNull("Getting back an object that was saved with automatic metadata extraction", oi.get(0));
		
		// check that automatic metadata fields were populated correctly, and nothing else was added
		Map<String,String> savedUserMetaData = oi.get(0).getUserMetaData();
		for(Entry<String,String> m : savedUserMetaData.entrySet()) {
			if(m.getKey().equals("val")) 
				Assert.assertTrue("Extracted metadata must be correct",m.getValue().equals(val));
			if(m.getKey().equals("Length of list"))
				Assert.assertTrue("Extracted metadata must be correct",m.getValue().equals("8"));
		}
		savedUserMetaData.remove("val");
		savedUserMetaData.remove("Length of list");
		Assert.assertEquals("Only metadata we wanted was extracted", 0, savedUserMetaData.size());
		
		// now we do the same thing, but make sure 1) metadata set was added, and 2) metadata is overridden
		// by the extracted metadata
		metadata.put("Length of list","i am pretty sure it was 7");
		metadata.put("my_special_metadata", "yes");
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("d2"),d1, MyType, metadata, emptyprov, false)),
				getIdFactory(userfoo));
		List <ObjectInformation> oi2 = ws.getObjectInformation(userfoo, Arrays.asList(new ObjectIdentifier(wspace, "d2")), true, true);
		Assert.assertNotNull("Getting back an object that was saved with automatic metadata extraction", oi2);
		Assert.assertNotNull("Getting back an object that was saved with automatic metadata extraction", oi2.get(0));
		
		savedUserMetaData = oi2.get(0).getUserMetaData();
		for(Entry<String,String> m : savedUserMetaData.entrySet()) {
			if(m.getKey().equals("val"))
				Assert.assertTrue("Extracted metadata must be correct",m.getValue().equals(val));
			if(m.getKey().equals("Length of list"))
				Assert.assertTrue("Extracted metadata must be correct",m.getValue().equals("8"));
			if(m.getKey().equals("my_special_metadata"))
				Assert.assertTrue("Extracted metadata must be correct",m.getValue().equals("yes"));
		}
		savedUserMetaData.remove("val");
		savedUserMetaData.remove("Length of list");
		savedUserMetaData.remove("my_special_metadata");
		Assert.assertEquals("Only metadata we wanted was extracted", 0, savedUserMetaData.size());
		
		// finally, test that if we exceed the metadata extraction limit, we fail
		Map<String, Object> dBig = new LinkedHashMap<String, Object>();
		dBig.put("l", Arrays.asList(1,2,3,4,5,6,7,8));
		StringBuilder bigVal = new StringBuilder();
		for (int i = 0; i < 18; i++) {
			bigVal.append(LONG_TEXT); //> 16kb now
		}
		dBig.put("val", bigVal.toString());
		try {
			ws.saveObjects(userfoo, wspace, Arrays.asList(new WorkspaceSaveObject(
					new ObjectIDNoWSNoVer("bigextractedmeta"), dBig, MyType, null,
					emptyprov, false)), getIdFactory(userfoo));
			fail("saved object with > 16kb of extracted metadata");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Object #1, bigextractedmeta : Object metadata size (19309 bytes) after adding metadata field 'val' exceeds limit of 16000 bytes"));
		}
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
			objs.add(new WorkspaceSaveObject(new JsonTokenStream(jsondata.getBytes(cs)),
					SAFE_TYPE1, null, emptyprov, false));
		}
		
		ws.saveObjects(user, wspace, objs, getIdFactory(user));
		List<WorkspaceObjectData> ret = ws.getObjects(user, Arrays.asList(
				new ObjectIdentifier(wspace, 1),
				new ObjectIdentifier(wspace, 2),
				new ObjectIdentifier(wspace, 3),
				new ObjectIdentifier(wspace, 4),
				new ObjectIdentifier(wspace, 5)));
		
		for (WorkspaceObjectData wod: ret) {
			assertThat("got correct object input in various encodings",
					wod.getData(), is((Object) craycraymap));
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
		ws.requestModuleRegistration(userfoo, module);
		ws.resolveModuleRegistration(module, true);
		ws.compileNewTypeSpec(userfoo, spec, Arrays.asList(
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
			ws.saveObjects(userfoo, wspace, Arrays.asList(
					new WorkspaceSaveObject("data1", abstype1, null, emptyprov, false)),
					getIdFactory(userfoo));
			Assert.fail("Method works but shouldn't");
		} catch (TypedObjectValidationException ex) {
			Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("structure"));
		}
		try {
			ws.saveObjects(userfoo, wspace, Arrays.asList(
					new WorkspaceSaveObject(Arrays.asList("data2"), 
							abstype2, null, emptyprov, false)), getIdFactory(userfoo));
			Assert.fail("Method works but shouldn't");
		} catch (TypedObjectValidationException ex) {
			Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("structure"));
		}
		try {
			ws.saveObjects(userfoo, wspace, Arrays.asList(
					new WorkspaceSaveObject(data3, abstype3, null, emptyprov, false)),
					getIdFactory(userfoo));
			Assert.fail("Method works but shouldn't");
		} catch (TypedObjectValidationException ex) {
			Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("structure"));
		}
		try {
			ws.saveObjects(userfoo, wspace, Arrays.asList(
					new WorkspaceSaveObject(Arrays.asList("data4", "data4"), 
							abstype4, null, emptyprov, false)), getIdFactory(userfoo));
			Assert.fail("Method works but shouldn't");
		} catch (TypedObjectValidationException ex) {
			Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("structure"));
		}
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(data3, abstype5, null, emptyprov, false)),
				getIdFactory(userfoo));
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
		ws.requestModuleRegistration(userfoo, module);
		ws.resolveModuleRegistration(module, true);
		ws.compileNewTypeSpec(userfoo, spec, Arrays.asList(
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
		
		//TODO should try these tests with bytes vs. maps
		Map<String, Object> data1 = new LinkedHashMap<String, Object>();
		data1.put("val3", null);
		data1.put("val2", null);
		data1.put("val1", null);
		Assert.assertEquals(keys, new TreeSet<String>(data1.keySet()));
		Assert.assertTrue(data1.containsKey("val1"));
		Assert.assertNull(data1.get("val1"));
		long data1id = ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(data1, abstype1, null, emptyprov, false)),
				getIdFactory(userfoo)).get(0).getObjectId();
		Map<String, Object> data1copy = (Map<String, Object>)ws.getObjects(userfoo, Arrays.asList(
				new ObjectIdentifier(wspace, data1id))).get(0).getData();
		Assert.assertEquals(keys, new TreeSet<String>(data1copy.keySet()));
		
		Map<String, Object> data2 = new LinkedHashMap<String, Object>();
		data2.put("val", null);
		failSave(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(data2, abstype2, null, emptyprov, false)), 
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (null) does not match any allowed primitive type (allowed: [\"array\"]), at /val"));
		data2.put("val", Arrays.asList((String)null));
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(data2, abstype2, null, emptyprov, false)),
				getIdFactory(userfoo));
		
		Map<String, Object> data3 = new LinkedHashMap<String, Object>();
		data3.put("val", null);
		failSave(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(data3, abstype3, null, emptyprov, false)), 
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (null) does not match any allowed primitive type (allowed: [\"object\"]), at /val"));
		Map<String, Object> innerMap = new LinkedHashMap<String, Object>();
		innerMap.put("key", null);
		data3.put("val", innerMap);
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(data3, abstype3, null, emptyprov, false)),
				getIdFactory(userfoo));
		innerMap.put(null, "foo");
		
		failSave(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(data3, abstype3, null, emptyprov, false)), 
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nKeys in maps/structures may not be null"));
		
		Map<String, Object> data4 = new LinkedHashMap<String, Object>();
		data4.put("val", null);
		failSave(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(data4, abstype4, null, emptyprov, false)), 
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (null) does not match any allowed primitive type (allowed: [\"array\"]), at /val"));
		data4.put("val", Arrays.asList((String)null, (String)null));
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(data4, abstype4, null, emptyprov, false)),
				getIdFactory(userfoo));
		
		Map<String, Object> data5 = new LinkedHashMap<String, Object>();
		data5.put("val", Arrays.asList(2, (Integer)null, 1));
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(data5, abstype5, null, emptyprov, false)),
				getIdFactory(userfoo));
		
		Map<String, Object> data6 = new LinkedHashMap<String, Object>();
		data6.put("val", Arrays.asList(1.2, (Float)null, 3.6));
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(data6, abstype6, null, emptyprov, false)),
				getIdFactory(userfoo));
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
		ws.requestModuleRegistration(userfoo, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(userfoo, specTypeCheck1, Arrays.asList("CheckType"), null, null, false, null);
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
				new WorkspaceSaveObject(data1, abstype0, null, emptyprov, false)),
				getIdFactory(userfoo)); //should work
		
		failSave(userfoo, wspace, data1, new TypeDefId("NoModHere.Foo"), emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nModule doesn't exist: NoModHere"));
		failSave(userfoo, wspace, data1, new TypeDefId("SomeModule.Foo"), emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: SomeModule.Foo"));
		
		failSave(userfoo, wspace, data1, relmintype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nThis type wasn't released yet and you should be an owner to access unreleased version information"));
		failSave(userfoo, wspace, data1, relmintype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: TestTypeChecking.CheckType-1"));
		failSave(userfoo, wspace, data1, abstype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: TestTypeChecking.CheckType-1.0"));
		failSave(userfoo, wspace, data1, relmaxtype, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nThis type wasn't released yet and you should be an owner to access unreleased version information"));
		
		ws.releaseTypes(userfoo, mod);
		
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, relmaxtype, null, emptyprov, false)),
				getIdFactory(userfoo));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, abstype0, null, emptyprov, false)),
				getIdFactory(userfoo));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, abstype1, null, emptyprov, false)),
				getIdFactory(userfoo));
		failSave(userfoo, wspace, data1, relmintype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nThis type wasn't released yet and you should be an owner to access unreleased version information"));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, relmintype1, null, emptyprov, false)),
				getIdFactory(userfoo));
		failSave(userfoo, wspace, data1, relmintype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nUnable to locate type: TestTypeChecking.CheckType-2"));
		
		ws.compileNewTypeSpec(userfoo, specTypeCheck2, null, null, null, false, null);
		
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, relmaxtype, null, emptyprov, false)),
				getIdFactory(userfoo));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, relmintype1, null, emptyprov, false)),
				getIdFactory(userfoo));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, abstype0, null, emptyprov, false)),
				getIdFactory(userfoo));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, abstype1, null, emptyprov, false)),
				getIdFactory(userfoo));
		failSave(userfoo, wspace, data1, abstype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (string) does not match any allowed primitive type (allowed: [\"integer\"]), at /baz"));
		failSave(userfoo, wspace, data1, relmintype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nThis type wasn't released yet and you should be an owner to access unreleased version information"));
		
		
		Map<String, Object> newdata = new HashMap<String, Object>(data1);
		newdata.put("baz", 1);
		ws.saveObjects(userfoo, wspace, Arrays.asList(
					new WorkspaceSaveObject(newdata, abstype2 , null, emptyprov, false)),
					getIdFactory(userfoo));
		failSave(userfoo, wspace, newdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, newdata, abstype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, newdata, relmaxtype, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, newdata, relmintype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, newdata, relmintype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\nThis type wasn't released yet and you should be an owner to access unreleased version information"));
		
		ws.releaseTypes(userfoo, mod);
		
		failSave(userfoo, wspace, data1, relmaxtype, emptyprov, 
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (string) does not match any allowed primitive type (allowed: [\"integer\"]), at /baz"));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, relmintype1, null, emptyprov, false)),
				getIdFactory(userfoo));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, abstype0, null, emptyprov, false)),
				getIdFactory(userfoo));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(data1, abstype1, null, emptyprov, false)),
				getIdFactory(userfoo));
		failSave(userfoo, wspace, data1, abstype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (string) does not match any allowed primitive type (allowed: [\"integer\"]), at /baz"));
		failSave(userfoo, wspace, data1, relmintype2, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (string) does not match any allowed primitive type (allowed: [\"integer\"]), at /baz"));
		
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(newdata, abstype2 , null, emptyprov, false)),
				getIdFactory(userfoo));
		failSave(userfoo, wspace, newdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		failSave(userfoo, wspace, newdata, abstype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(newdata, relmaxtype, null, emptyprov, false)),
				getIdFactory(userfoo));
		failSave(userfoo, wspace, newdata, relmintype1, emptyprov,
				new TypedObjectValidationException(
						"Object #1 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /baz"));
		ws.saveObjects(userfoo, wspace, Arrays.asList( //should work
				new WorkspaceSaveObject(newdata, relmintype2, null, emptyprov, false)),
				getIdFactory(userfoo));
		
		
		// test non-parseable references and typechecking with object count
		List<WorkspaceSaveObject> data = new ArrayList<WorkspaceSaveObject>();
		data.add(new WorkspaceSaveObject(data1, abstype0, null, emptyprov, false));
		Map<String, Object> data2 = new HashMap<String, Object>(data1);
		data2.put("bar", Arrays.asList(-3, 1, "anotherstring"));
		data.add(new WorkspaceSaveObject(data2, abstype0, null, emptyprov, false));
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
					"Object #2 failed type checking:\ninstance type (string) does not match any allowed primitive type (allowed: [\"integer\"]), at /bar/2"));
		
		data.set(1, new WorkspaceSaveObject(data2, abstype2, null, emptyprov, false));
		@SuppressWarnings("unchecked")
		List<Integer> intlist = (List<Integer>) data2.get("bar");
		intlist.set(2, 42);
		Map<String, Object> inner = new HashMap<String, Object>();
		inner.put("amapkey", 42);
		data2.put("map", inner);
		data2.put("baz", 1);
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
				"Object #2 failed type checking:\ninstance type (integer) does not match any allowed primitive type (allowed: [\"string\"]), at /map/amapkey"));
		
		Map<String, Object> data3 = new HashMap<String, Object>(data1);
		data3.put("ref", "typecheck/1/1");
		data.set(1, new WorkspaceSaveObject(data3, abstype0, null, emptyprov, false));
		ws.saveObjects(userfoo, wspace, data, getIdFactory(userfoo)); //should work
		
		Map<String, Object> data4 = new HashMap<String, Object>(data1);
		data4.put("ref", "foo/bar/baz");
		data.set(1, new WorkspaceSaveObject(data4, abstype0, null, emptyprov, false));
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
				"Object #2 has unparseable reference foo/bar/baz: Unable to parse version portion of object reference foo/bar/baz to an integer at /ref"));
		
		Map<String, Object> data5 = new HashMap<String, Object>(data1);
		data5.put("ref", null);
		data.set(1, new WorkspaceSaveObject(data5, abstype0, null, emptyprov, false));
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
				"Object #2 failed type checking:\ninstance type (null) not allowed for ID reference (allowed: [\"string\"]), at /ref"));
		
		Map<String, Object> data6 = new HashMap<String, Object>(data1);
		data6.put("ref", "");
		data.set(1, new WorkspaceSaveObject(data6, abstype0, null, emptyprov, false));
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
				"Object #2 failed type checking:\nUnparseable id  of type ws: IDs may not be null or the empty string at /ref"));
		
		
		Provenance goodids = new Provenance(userfoo);
		goodids.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("typecheck/1/1")));
		data.set(1, new WorkspaceSaveObject(data3, abstype0, null, goodids, false));
		ws.saveObjects(userfoo, wspace, data, getIdFactory(userfoo)); //should work
		
		Provenance badids = new Provenance(userfoo);
		badids.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("foo/bar/baz")));
		data.set(1, new WorkspaceSaveObject(data3, abstype0, null, badids, false));
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
				"Object #2 has unparseable provenance reference foo/bar/baz: Unable to parse version portion of object reference foo/bar/baz to an integer"));
		
		badids = new Provenance(userfoo);
		badids.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList((String) null)));
		data.set(1, new WorkspaceSaveObject(data3, abstype0, null, badids, false));
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
				"Object #2 has a null provenance reference"));
		
		badids = new Provenance(userfoo);
		badids.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("")));
		data.set(1, new WorkspaceSaveObject(data3, abstype0, null, badids, false));
		failSave(userfoo, wspace, data, new TypedObjectValidationException(
				"Object #2 has invalid provenance reference: IDs may not be null or the empty string"));
		
		//test inaccessible references due to missing, deleted, or unreadable workspaces
		Map<String, Object> refdata = new HashMap<String, Object>(data1);
		refdata.put("ref", "thereisnoworkspaceofthisname/2/1");
		failSave(userfoo, wspace, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 has invalid reference: No read access to id thereisnoworkspaceofthisname/2/1: Object 2 cannot be accessed: No workspace with name thereisnoworkspaceofthisname exists at /ref"));
		Provenance nowsref = new Provenance(userfoo);
		nowsref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("thereisnoworkspaceofthisname/2/1")));
		failSave(userfoo, wspace, data1, abstype0, nowsref,
				new TypedObjectValidationException(
						"Object #1 has invalid provenance reference: No read access to id thereisnoworkspaceofthisname/2/1: Object 2 cannot be accessed: No workspace with name thereisnoworkspaceofthisname exists"));
		
		ws.createWorkspace(userfoo, "tobedeleted", false, null, null);
		ws.setWorkspaceDeleted(userfoo, new WorkspaceIdentifier("tobedeleted"), true);
		refdata.put("ref", "tobedeleted/2/1");
		failSave(userfoo, wspace, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 has invalid reference: No read access to id tobedeleted/2/1: Object 2 cannot be accessed: Workspace tobedeleted is deleted at /ref"));
		Provenance delwsref = new Provenance(userfoo);
		delwsref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("tobedeleted/2/1")));
		failSave(userfoo, wspace, data1, abstype0, delwsref,
				new TypedObjectValidationException(
						"Object #1 has invalid provenance reference: No read access to id tobedeleted/2/1: Object 2 cannot be accessed: Workspace tobedeleted is deleted"));
		
		ws.createWorkspace(new WorkspaceUser("stingyuser"), "stingyworkspace", false, null, null);
		refdata.put("ref", "stingyworkspace/2/1");
		failSave(userfoo, wspace, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 has invalid reference: No read access to id stingyworkspace/2/1: Object 2 cannot be accessed: User foo may not read workspace stingyworkspace at /ref"));
		Provenance privwsref = new Provenance(userfoo);
		privwsref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("stingyworkspace/2/1")));
		failSave(userfoo, wspace, data1, abstype0, privwsref,
				new TypedObjectValidationException(
						"Object #1 has invalid provenance reference: No read access to id stingyworkspace/2/1: Object 2 cannot be accessed: User foo may not read workspace stingyworkspace"));
		
		//test inaccessible reference due to missing or deleted objects, incl bad versions
		ws.createWorkspace(userfoo, "referencetesting", false, null, null);
		WorkspaceIdentifier reftest = new WorkspaceIdentifier("referencetesting");
		ws.saveObjects(userfoo, reftest, Arrays.asList(
				new WorkspaceSaveObject(newdata, abstype2 , null, emptyprov, false)),
				getIdFactory(userfoo));
		
		refdata.put("ref", "referencetesting/1/1");
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(refdata, abstype1 , null, emptyprov, false)),
				getIdFactory(userfoo));
		Provenance goodref = new Provenance(userfoo);
		goodref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("referencetesting/1/1")));
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(refdata, abstype1 , null, goodref, false)),
				getIdFactory(userfoo));
		
		refdata.put("ref", "referencetesting/2/1");
		long refwsid = ws.getWorkspaceInformation(userfoo, reftest).getId();
		failSave(userfoo, wspace, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 has invalid reference: There is no object with id referencetesting/2/1: No object with id 2 exists in workspace "
								+ refwsid + " at /ref"));
		Provenance noobjref = new Provenance(userfoo);
		noobjref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("referencetesting/2/1")));
		failSave(userfoo, wspace, data1, abstype0, noobjref,
				new TypedObjectValidationException(
						"Object #1 has invalid provenance reference: There is no object with id referencetesting/2/1: No object with id 2 exists in workspace "
								+ refwsid));
		
		ws.saveObjects(userfoo, reftest, Arrays.asList(
				new WorkspaceSaveObject(newdata, abstype2 , null, emptyprov, false)),
				getIdFactory(userfoo));
		ws.setObjectsDeleted(userfoo, Arrays.asList(new ObjectIdentifier(reftest, 2)), true);
		failSave(userfoo, wspace, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(String.format(
						"Object #1 has invalid reference: There is no object with id referencetesting/2/1: Object 2 (name auto2) in workspace %s has been deleted at /ref",
								refwsid)));
		Provenance delobjref = new Provenance(userfoo);
		delobjref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("referencetesting/2/1")));
		failSave(userfoo, wspace, data1, abstype0, delobjref,
				new TypedObjectValidationException(String.format(
						"Object #1 has invalid provenance reference: There is no object with id referencetesting/2/1: Object 2 (name auto2) in workspace %s has been deleted",
								refwsid)));
		
		refdata.put("ref", "referencetesting/1/2");
		failSave(userfoo, wspace, refdata, abstype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1 has invalid reference: There is no object with id referencetesting/1/2: No object with id 1 (name auto1) and version 2 exists in workspace "
								+ refwsid + " at /ref"));
		Provenance noverref = new Provenance(userfoo);
		noverref.addAction(new Provenance.ProvenanceAction().withWorkspaceObjects(Arrays.asList("referencetesting/1/2")));
		failSave(userfoo, wspace, data1, abstype0, noverref,
				new TypedObjectValidationException(
						"Object #1 has invalid provenance reference: There is no object with id referencetesting/1/2: No object with id 1 (name auto1) and version 2 exists in workspace "
								+ refwsid));
		
		//TODO test references against garbage collected objects
		
		//test reference type checking
		String refmod = "TestTypeCheckingRefType";
		ws.requestModuleRegistration(userfoo, refmod);
		ws.resolveModuleRegistration(refmod, true);
		ws.compileNewTypeSpec(userfoo, specTypeCheckRefs, Arrays.asList("CheckRefType"), null, null, false, null);
		TypeDefId absreftype0 = new TypeDefId(new TypeDefName(refmod, "CheckRefType"), 0, 1);

		ws.createWorkspace(userfoo, "referencetypecheck", false, null, null);
		WorkspaceIdentifier reftypecheck = new WorkspaceIdentifier("referencetypecheck");
		long reftypewsid = ws.getWorkspaceInformation(userfoo, reftypecheck).getId();
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(newdata, SAFE_TYPE1 , null, emptyprov, false)),
				getIdFactory(userfoo));
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(newdata, abstype2 , null, emptyprov, false)),
				getIdFactory(userfoo));
		
		refdata.put("ref", "referencetypecheck/2/1");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false)),
				getIdFactory(userfoo)); //should work
		
		refdata.put("ref", "referencetypecheck/2");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false)),
				getIdFactory(userfoo)); //should work
		
		refdata.put("ref", "referencetypecheck/auto2/1");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false)),
				getIdFactory(userfoo)); //should work
		
		refdata.put("ref", "referencetypecheck/auto2");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false)),
				getIdFactory(userfoo)); //should work
		
		refdata.put("ref", reftypewsid + "/2/1");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false)),
				getIdFactory(userfoo)); //should work
		
		refdata.put("ref", reftypewsid + "/2");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false)),
				getIdFactory(userfoo)); //should work
		
		refdata.put("ref", reftypewsid + "/auto2/1");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false)),
				getIdFactory(userfoo)); //should work
		
		refdata.put("ref", reftypewsid + "/auto2");
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, absreftype0, null, emptyprov, false)),
				getIdFactory(userfoo)); //should work

		String err = "Object #1 has invalid reference: The type " +
				"SomeModule.AType-0.1 of reference %s in this object is not " + 
				"allowed - allowed types are [TestTypeChecking.CheckType] at /ref";
		
		refdata.put("ref", "referencetypecheck/1/1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						"referencetypecheck/1/1")));
		
		refdata.put("ref", "referencetypecheck/1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						"referencetypecheck/1")));
		
		refdata.put("ref", "referencetypecheck/auto1/1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						"referencetypecheck/auto1/1")));
		
		refdata.put("ref", "referencetypecheck/auto1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						"referencetypecheck/auto1")));
		
		refdata.put("ref", reftypewsid + "/1/1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						reftypewsid + "/1/1")));
		
		refdata.put("ref", reftypewsid + "/1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						reftypewsid + "/1")));
		
		refdata.put("ref", reftypewsid + "/auto1/1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						reftypewsid + "/auto1/1")));
		
		refdata.put("ref", reftypewsid + "/auto1");
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(String.format(err,
						reftypewsid + "/auto1")));

		//check references were rewritten correctly
		for (int i = 3; i < 11; i++) {
			WorkspaceObjectData wod = ws.getObjects(userfoo, Arrays.asList(
					new ObjectIdentifier(reftypecheck, i))).get(0);
			WorkspaceObjectData wodsub = ws.getObjectsSubSet(userfoo, Arrays.asList(
					new SubObjectIdentifier(new ObjectIdentifier(reftypecheck, i), null))).get(0);
			@SuppressWarnings("unchecked")
			Map<String, Object> obj = (Map<String, Object>) wod.getData();
			@SuppressWarnings("unchecked")
			Map<String, Object> subobj = (Map<String, Object>) wodsub.getData();
			assertThat("reference rewritten correctly", (String) obj.get("ref"),
					is(reftypewsid + "/2/1"));
			assertThat("reference included correctly", wod.getReferences(),
					is(Arrays.asList(reftypewsid + "/2/1")));
			assertThat("sub obj reference rewritten correctly", (String) subobj.get("ref"),
					is(reftypewsid + "/2/1"));
			assertThat("sub obj reference included correctly", wodsub.getReferences(),
					is(Arrays.asList(reftypewsid + "/2/1")));
			
			WorkspaceObjectInformation inf = ws.getObjectProvenance(userfoo, Arrays.asList(
					new ObjectIdentifier(reftypecheck, i))).get(0);
			assertThat("sub obj reference included correctly", inf.getReferences(),
					is(Arrays.asList(reftypewsid + "/2/1")));
		}
	}
	
	@Test
	public void wsIdErrorOrder() throws Exception {
		//test that an id error returns the right id if multiple IDs exist
		WorkspaceUser user = new WorkspaceUser("user1");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("wsIdErrorOrder");
		long wsid = ws.createWorkspace(user, wsi.getName(), false, null, null).getId();
		List<WorkspaceSaveObject> objs = new LinkedList<WorkspaceSaveObject>();
		Map<String, Object> d = new HashMap<String, Object>();
		Provenance mtprov = new Provenance(user);
		objs.add(new WorkspaceSaveObject(d, SAFE_TYPE1, null, mtprov, false));
		ws.saveObjects(user, wsi, objs, new IdReferenceHandlerSetFactory(0));
		
		Provenance p = new Provenance(user).addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList(
						wsi.getName() + "/auto1", wsi.getName() + "/auto2")));
		objs.set(0, new WorkspaceSaveObject(d, SAFE_TYPE1, null, p, false));
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1 has invalid provenance reference: There is no object with id wsIdErrorOrder/auto2: No object with name auto2 exists in workspace "
				+ wsid));
		
	}
	
	@Test
	public void duplicateAutoIds() throws Exception {
		WorkspaceUser user = new WorkspaceUser("user1");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("dupAutoIds");
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		List<WorkspaceSaveObject> objs = new LinkedList<WorkspaceSaveObject>();
		Map<String, Object> d1 = new HashMap<String, Object>();
		Map<String, Object> d2 = new HashMap<String, Object>();
		d2.put("d", 2);
		Provenance mtprov = new Provenance(user);
		objs.add(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto5-foo"), d1, SAFE_TYPE1, null, mtprov, false));
		objs.add(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto5-1-1"), d1, SAFE_TYPE1, null, mtprov, false));
		objs.add(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto5"), d1, SAFE_TYPE1, null, mtprov, false));
		objs.add(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto5-1"), d1, SAFE_TYPE1, null, mtprov, false));
		objs.add(new WorkspaceSaveObject(d2, SAFE_TYPE1, null, mtprov, false));
		
		ws.saveObjects(user, wsi, objs, new IdReferenceHandlerSetFactory(0));
		WorkspaceObjectData d =  ws.getObjects(user, Arrays.asList(
				new ObjectIdentifier(wsi, "auto5-2"))).get(0);
		assertThat("auto named correctly", d.getData(), is((Object) d2));
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
		ws.requestModuleRegistration(user, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(user, idSpec, Arrays.asList(type),
				null, null, false, null);
		TypeDefId idtype = new TypeDefId(new TypeDefName(mod, type), 0, 1);
		
		// test basic type checking with different versions
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("idextract");
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		Provenance emptyprov = new Provenance(user);
		List<WorkspaceSaveObject> data = new LinkedList<WorkspaceSaveObject>();
		data.add(new WorkspaceSaveObject(new HashMap<String, Object>(), idtype, null, emptyprov, false));
		
		Map<String, Object> iddata = new HashMap<String, Object>();
		

		IdReferenceHandlerSetFactory fac = getIdFactory(user).addFactory(
				new TestIDReferenceHandlerFactory(new IdReferenceType(idtype1)));

		data.add(new WorkspaceSaveObject(iddata, idtype, null, emptyprov, false));
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
		ws.saveObjects(user, wsi, data, fac); //should work
		expected.put(idtype2, Arrays.asList("foo"));
		ObjectIdentifier obj4 = new ObjectIdentifier(wsi, "auto4");
		checkExternalIds(user, obj4, expected);
		
		ObjectIdentifier copied = new ObjectIdentifier(wsi, "copied");
		ws.copyObject(user, obj4, copied);
		checkExternalIds(user, copied, expected);
		
		WorkspaceIdentifier clone = new WorkspaceIdentifier("idextract_cloned");
		ws.cloneWorkspace(user, wsi, clone.getName(), false, null, null);
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
				"Object #2 failed type checking:\nUnparseable id parseExcept of type someid: Parse exception for ID parseExcept at /an_id"));
		
		iddata.clear();
		iddata.put("an_id2", "refExcept");
		failSave(user, wsi, data, fac, new TypedObjectValidationException(
				"Object #2 failed type checking:\nInvalid id refExcept of type someid2: Reference exception for ID refExcept at /an_id2"));
		
		iddata.clear();
		iddata.put("an_id", "genExcept");
		failSave(user, wsi, data, fac, new TypedObjectValidationException(
				"Object #2 failed type checking:\nId handling error for id type someid: General exception for ID genExcept at /an_id"));
		
		iddata.put("an_id", "procParseExcept");
		failSave(user, wsi, data, fac, new TypedObjectValidationException(
				"Object #2 has unparseable reference procParseExcept: Process Parse exception for ID procParseExcept at /an_id"));
		
		iddata.clear();
		iddata.put("an_id2", "procRefExcept");
		failSave(user, wsi, data, fac, new TypedObjectValidationException(
				"Object #2 has invalid reference: Process Reference exception for ID procRefExcept at /an_id2"));
		
		iddata.clear();
		iddata.put("an_id", "procGenExcept");
		failSave(user, wsi, data, fac, new TypedObjectValidationException(
				"An error occured while processing IDs: Process General exception for ID procGenExcept"));
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
		ws.requestModuleRegistration(user, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(user, idSpec, Arrays.asList(type, "Type1", "Type2", "Type3"),
				null, null, false, null);
		TypeDefId type1 = new TypeDefId(new TypeDefName(mod, "Type1"), 0, 1);
		TypeDefId type2 = new TypeDefId(new TypeDefName(mod, "Type2"), 0, 1);
		TypeDefId type3 = new TypeDefId(new TypeDefName(mod, "Type3"), 0, 1);
		
		TypeDefId idtype = new TypeDefId(new TypeDefName(mod, type), 0, 1);
		
		// test basic type checking with different versions
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("wsIDHandling");
		long wsid = ws.createWorkspace(user, wsi.getName(), false, null, null).getId();
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
		objs.add(new WorkspaceSaveObject(data, idtype, null, emptyprov, false));
		//should work
		ws.saveObjects(user, wsi, objs, fac);
		
		innermap.put(ref2, 4);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1 has invalid reference: The type WsIDHandling.Type2-0.1 of reference wsIDHandling/t2 in this object is not allowed - allowed types are [WsIDHandling.Type1] at /ws_1/0/wsIDHandling/t2"));
		
		innermap.remove(ref2);
		innermap.put(ref3, 6);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1 has invalid reference: The type WsIDHandling.Type3-0.1 of reference wsIDHandling/t3 in this object is not allowed - allowed types are [WsIDHandling.Type1] at /ws_1/0/wsIDHandling/t3"));
		
		innermap.remove(ref3);
		innertuple.add(Arrays.asList("bar", ref1));
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1 has invalid reference: The type WsIDHandling.Type1-0.1 of reference wsIDHandling/t1 in this object is not allowed - allowed types are [WsIDHandling.Type2] at /ws_2/1/1"));
		
		innertuple.clear();
		innertuple.add(Arrays.asList("baz", ref3));
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1 has invalid reference: The type WsIDHandling.Type3-0.1 of reference wsIDHandling/t3 in this object is not allowed - allowed types are [WsIDHandling.Type2] at /ws_2/0/1"));
		
		innertuple.set(0, Arrays.asList("foo", ref2));
		innerlist.add(ref1);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1 has invalid reference: The type WsIDHandling.Type1-0.1 of reference wsIDHandling/t1 in this object is not allowed - allowed types are [WsIDHandling.Type3] at /ws_3/0/1"));
		
		innerlist.set(1, ref3);
		innerlist.add(ref2);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1 has invalid reference: The type WsIDHandling.Type2-0.1 of reference wsIDHandling/t2 in this object is not allowed - allowed types are [WsIDHandling.Type3] at /ws_3/0/2"));
		
		innerlist.remove(2);
		innerlist.remove(1);
		data.put("ws_12", all3);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1 has invalid reference: The type WsIDHandling.Type3-0.1 of reference wsIDHandling/t3 in this object is not allowed - allowed types are [WsIDHandling.Type1, WsIDHandling.Type2] at /ws_12/2"));
		
		data.put("ws_12", Arrays.asList(ref1, ref2));
		data.put("ws_13", all3);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1 has invalid reference: The type WsIDHandling.Type2-0.1 of reference wsIDHandling/t2 in this object is not allowed - allowed types are [WsIDHandling.Type1, WsIDHandling.Type3] at /ws_13/1"));
		
		data.put("ws_13", Arrays.asList(ref1, ref3));
		data.put("ws_23", all3);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1 has invalid reference: The type WsIDHandling.Type1-0.1 of reference wsIDHandling/t1 in this object is not allowed - allowed types are [WsIDHandling.Type2, WsIDHandling.Type3] at /ws_23/0"));
		
		
		//test id path returns on parse and inaccessible object exceptions
		data.put("ws_23", Arrays.asList(ref2, ref3));
		innertuple.set(0, Arrays.asList("foo", "YourMotherWasAHamster"));
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1 has unparseable reference YourMotherWasAHamster: Illegal number of separators / in object reference YourMotherWasAHamster at /ws_2/0/1"));
		
		innertuple.set(0, Arrays.asList("foo", ref2));
		data.remove("ws_any");
		ws.setObjectsDeleted(user, Arrays.asList(new ObjectIdentifier(wsi, "t1")), true);
		failSave(user, wsi, objs, new TypedObjectValidationException(
				"Object #1 has invalid reference: There is no object with id wsIDHandling/t1: Object 1 (name t1) in workspace " +
						wsid + " has been deleted at /ws_12/0"));
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
		ws.requestModuleRegistration(user, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(user, idSpec, Arrays.asList(listtype),
				null, null, false, null);
		TypeDefId listidtype = new TypeDefId(new TypeDefName(mod, listtype), 0, 1);
		
		// test basic type checking with different versions
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("maxids");
		ws.createWorkspace(user, wsi.getName(), false, null, null);
		Provenance emptyprov = new Provenance(user);
		List<WorkspaceSaveObject> objs = new LinkedList<WorkspaceSaveObject>();
		WorkspaceSaveObject mtobj = new WorkspaceSaveObject(
				new HashMap<String, String>(), listidtype, null, emptyprov, false);
		objs.add(mtobj);
		objs.add(mtobj);
		
		IdReferenceHandlerSetFactory fac = makeFacForMaxIDTests(
				Arrays.asList(idtype1, idtype2), user, 8);
		ws.saveObjects(user, wsi, objs, fac);
		objs.clear();
		
		Map<String, Object> data1 = new HashMap<String, Object>();
		data1.put("ws_ids", Arrays.asList("maxids/auto1", "maxids/auto2", "maxids/auto1"));
		data1.put("some_ids", Arrays.asList("foo", "bar", "foo"));
		data1.put("some_ids2", Arrays.asList("foo", "baz", "foo"));
		data1.put("some_ids_a1", Arrays.asList("foo", "bak", "foo"));
		data1.put("some_ids_a2", Arrays.asList("foo", "baf", "foo"));
		objs.add(new WorkspaceSaveObject(data1, listidtype, null, emptyprov, false));
		
		//should work
		ws.saveObjects(user, wsi, objs, fac);
		
		fac = makeFacForMaxIDTests(Arrays.asList(idtype1, idtype2), user, 7);
		failSave(user, wsi, objs, fac, new TypedObjectValidationException(
				"Failed type checking at object #1 - the number of unique IDs in the saved objects exceeds the maximum allowed, 7"));
		
		Provenance p = new Provenance(user).addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList(
						"maxids/auto1", "maxids/auto2", "maxids/auto1")));
		
		fac = makeFacForMaxIDTests(Arrays.asList(idtype1, idtype2), user, 10);
		objs.set(0, new WorkspaceSaveObject(data1, listidtype, null, p, false));
		//should work
		ws.saveObjects(user, wsi, objs, fac);
		fac = makeFacForMaxIDTests(Arrays.asList(idtype1, idtype2), user, 9);
		failSave(user, wsi, objs, fac, new TypedObjectValidationException(
				"Failed type checking at object #1 - the number of unique IDs in the saved objects exceeds the maximum allowed, 9"));
		
		objs.set(0, new WorkspaceSaveObject(data1, listidtype, null, emptyprov, false));
		objs.add(new WorkspaceSaveObject(data1, listidtype, null, emptyprov, false));
		fac = makeFacForMaxIDTests(Arrays.asList(idtype1, idtype2), user, 16);
		
		//should work
		ws.saveObjects(user, wsi, objs, fac);
		
		fac = makeFacForMaxIDTests(Arrays.asList(idtype1, idtype2), user, 15);
		failSave(user, wsi, objs, fac, new TypedObjectValidationException(
				"Failed type checking at object #2 - the number of unique IDs in the saved objects exceeds the maximum allowed, 15"));
		
		objs.set(0, new WorkspaceSaveObject(data1, listidtype, null, p, false));
		objs.set(1, new WorkspaceSaveObject(data1, listidtype, null, p, false));
		fac = makeFacForMaxIDTests(Arrays.asList(idtype1, idtype2), user, 20);
		
		//should work
		ws.saveObjects(user, wsi, objs, fac);
		
		fac = makeFacForMaxIDTests(Arrays.asList(idtype1, idtype2), user, 19);
		failSave(user, wsi, objs, fac, new TypedObjectValidationException(
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
		ws.requestModuleRegistration(userfoo, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(userfoo, specTypeCheck1, Arrays.asList("CheckType"), null, null, false, null);
		ws.releaseTypes(userfoo, mod);
		TypeDefId abstype0 = new TypeDefId(new TypeDefName(mod, "CheckType"), 1, 0);
		String wsName = "reftypecheckerror";
		ws.createWorkspace(userfoo, wsName, false, null, null);
		WorkspaceIdentifier reftypecheck = new WorkspaceIdentifier(wsName);
		Map<String, Object> refdata = new HashMap<String, Object>();
		refdata.put("foo", 3);
		refdata.put("baz", "astring");
		refdata.put("bar", Arrays.asList(-3, 1, 234567890));
		ws.saveObjects(userfoo, reftypecheck, Arrays.asList(
				new WorkspaceSaveObject(refdata, abstype0 , null, emptyprov, false)),
				getIdFactory(userfoo));
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
		ws.requestModuleRegistration(userfoo, refmod);
		ws.resolveModuleRegistration(refmod, true);
		ws.compileNewTypeSpec(userfoo, specTypeCheckRefs, Arrays.asList("CheckRefType"), null, null, false, null);
		ws.releaseTypes(userfoo, refmod);
		TypeDefId absreftype0 = new TypeDefId(new TypeDefName(refmod, "CheckRefType"), 1, 0);
		long reftypewsid = ws.getWorkspaceInformation(userfoo, reftypecheck).getId();
		//test the edge case where two keys in a hash resolve to the same reference
		refdata.put("ref", wsName + "/1/1");
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put(wsName + "/1/1", "pootypoot");
		refmap.put(wsName + "/auto1/1", "pootypoot");
		assertThat("refmap has 2 refs", refmap.size(), is(2));
		refdata.put("refmap", refmap);
		failSave(userfoo, reftypecheck, refdata, absreftype0, emptyprov,
				new TypedObjectValidationException(
						"Object #1: Two references in a single hash are identical when resolved, resulting in a loss of data: " +
						"Duplicated key '" + reftypewsid + "/1/1' was found at /refmap"));
	}
	
	@Test
	public void saveProvenance() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceIdentifier prov = new WorkspaceIdentifier("provenance");
		ws.createWorkspace(foo, prov.getName(), false, null, null);
		long wsid = ws.getWorkspaceInformation(foo, prov).getId();
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("foo", "bar");
		Provenance emptyprov = new Provenance(foo);
		
		//already tested bad references in saveObjectWithTypeChecking, won't test again here
		
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, emptyprov, false)),
				getIdFactory(foo));
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, emptyprov, false)),
				getIdFactory(foo));
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, emptyprov, false)),
				getIdFactory(foo));
		
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto1"), data, SAFE_TYPE1, null, emptyprov, false)),
				getIdFactory(foo));
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto1"), data, SAFE_TYPE1, null, emptyprov, false)),
				getIdFactory(foo));
		
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
		
		Provenance p = new Provenance(foo);
		p.addAction(new ProvenanceAction()
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
				.withWorkspaceObjects(Arrays.asList("provenance/auto3", "provenance/auto1/2")));
		p.addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList("provenance/auto2/1", "provenance/auto1")));
		
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, p, false)),
				getIdFactory(foo));
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("provenance/auto3", wsid + "/3/1");
		refmap.put("provenance/auto1/2", wsid + "/1/2");
		refmap.put("provenance/auto2/1", wsid + "/2/1");
		refmap.put("provenance/auto1", wsid + "/1/3");
		
		checkProvenanceCorrect(foo, p, new ObjectIdentifier(prov, 4), refmap);
		
		try {
			new WorkspaceSaveObject(data, SAFE_TYPE1, null, null, false);
			fail("saved without provenance");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Neither data, provenance, nor type may be null"));
		}
		try {
			new WorkspaceSaveObject(new ObjectIDNoWSNoVer("foo"), SAFE_TYPE1, null, null, false);
			fail("saved without provenance");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Neither data, provenance, nor type may be null"));
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
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, p2, false)),
				getIdFactory(foo));
		List<Date> dates = checkProvenanceCorrect(foo, p2, new ObjectIdentifier(prov, 5),
				new HashMap<String, String>());
		Provenance got2 = ws.getObjects(foo, Arrays.asList(new ObjectIdentifier(prov, 5))).get(0).getProvenance();
		assertThat("Prov date constant", got2.getDate(), is(dates.get(0)));
		Provenance gotsub2 = ws.getObjectsSubSet(foo, Arrays.asList(new SubObjectIdentifier(
				new ObjectIdentifier(prov, 5), null))).get(0).getProvenance();
		assertThat("Prov date constant", gotsub2.getDate(), is(dates.get(1)));
		assertThat("Prov dates same", got2.getDate(), is(gotsub2.getDate()));
		Provenance gotProv2 = ws.getObjectProvenance(foo, Arrays.asList(
				new ObjectIdentifier(prov, 5))).get(0).getProvenance();
		assertThat("Prov date constant", gotProv2.getDate(), is(dates.get(2)));
		assertThat("Prov dates same", got2.getDate(), is(gotProv2.getDate()));
		//make sure passing nulls for ws obj lists doesn't kill anything
		Provenance p3 = new Provenance(foo);
		p3.addAction(new ProvenanceAction().withWorkspaceObjects(null));
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, p3, false)),
				getIdFactory(foo));
		checkProvenanceCorrect(foo, p3, new ObjectIdentifier(prov, 6),
				new HashMap<String, String>());
		
		Provenance p4 = new Provenance(foo);
		ProvenanceAction pa = new ProvenanceAction();
		pa.setWorkspaceObjects(null);
		p4.addAction(pa);
		p3.addAction(new ProvenanceAction().withWorkspaceObjects(null));
		ws.saveObjects(foo, prov, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, p4, false)),
				getIdFactory(foo));
		checkProvenanceCorrect(foo, p4, new ObjectIdentifier(prov, 7),
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
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, p, false)),
				getIdFactory(foo));
		
		
		methparams.add(TEXT1000);
		Provenance p2 = new Provenance(foo);
		p2.addAction(new ProvenanceAction().withMethodParameters(methparams));
		try {
			ws.saveObjects(foo, prov, Arrays.asList(
					new WorkspaceSaveObject(data, SAFE_TYPE1, null, p, false)),
					getIdFactory(foo));
			fail("saved too big prov");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Object #1 provenance size 1000290 exceeds limit of 1000000"));
		}
	}
	
	@Test
	public void saveWithLargeSubdata() throws Exception {
		final String specSubdata =
				"module TestSubdata {" +
					"/* @searchable ws_subset subset */" +
					"typedef structure {" +
						"list<string> subset;" +
					"} SubSetType;" +
				"};";
		String mod = "TestSubdata";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		ws.requestModuleRegistration(userfoo, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(userfoo, specSubdata, Arrays.asList("SubSetType"), null, null, false, null);
		TypeDefId subsettype = new TypeDefId(new TypeDefName(mod, "SubSetType"), 0, 1);
		
		WorkspaceIdentifier subdataws = new WorkspaceIdentifier("bigsubdata");
		ws.createWorkspace(userfoo, subdataws.getName(), false, null, null);
		Map<String, Object> data = new HashMap<String, Object>();
		List<String> subdata = new LinkedList<String>();
		data.put("subset", subdata);
		for (int i = 0; i < 14955; i++) {
			subdata.add(TEXT1000);
		}
		ws.saveObjects(userfoo, subdataws, Arrays.asList( //should work
				new WorkspaceSaveObject(data, subsettype, null, new Provenance(userfoo), false)),
				getIdFactory(userfoo));
		
		subdata.add(TEXT1000);
		try {
			ws.saveObjects(userfoo, subdataws, Arrays.asList(
					new WorkspaceSaveObject(data, subsettype, null, new Provenance(userfoo), false)),
					getIdFactory(userfoo));
			fail("saved too big subdata");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Object #1 subdata size exceeds limit of 15000000"));
		}
	}
	
	@Test
	public void bigUserMetaErrors() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceIdentifier read = new WorkspaceIdentifier("bigmeta");
		ws.createWorkspace(foo, read.getIdentifierString(), false, null, null);
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, String> smallmeta = new HashMap<String, String>();
		smallmeta.put("foo", "bar");
		Map<String, String> meta = new HashMap<String, String>();
		data.put("fubar", "bar");
		JsonNode savedata = MAPPER.valueToTree(data);
		for (int i = 0; i < 18; i++) {
			meta.put(Integer.toString(i), LONG_TEXT); //> 16Mb now
		}
		try {
			ws.saveObjects(foo, read, Arrays.asList(new WorkspaceSaveObject(
					new ObjectIDNoWSNoVer("bigmeta"), savedata, SAFE_TYPE1, meta,
					new Provenance(foo), false)),
					getIdFactory(foo));
			fail("saved object with > 16kb metadata");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Metadata size of 19413 is > 16000 bytes"));
		}
		try {
			ws.saveObjects(foo, read, Arrays.asList(new WorkspaceSaveObject(
					new ObjectIDNoWSNoVer(3), savedata, SAFE_TYPE1, meta,
					new Provenance(foo), false)),
					getIdFactory(foo));
			fail("saved object with > 16kb metadata");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Metadata size of 19413 is > 16000 bytes"));
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
					getIdFactory(foo));
			fail("saved object with non-existant id");
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception", nsoe.getLocalizedMessage(),
					is("There is no object with id 3"));
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
					new ObjectIDNoWSNoVer("jframe"), data, SAFE_TYPE1, meta,
					new Provenance(foo), false)),
					getIdFactory(foo));
			fail("saved unserializable object");
		} catch (IllegalArgumentException iae) {
			assertThat("Actual exception: " + iae.getMessage(), iae.getMessage(), 
					is("UObject can not serialize object of this type: java.io.StringReader"));
		}
	}
	
	@Test
	public void getNonexistantObjects() throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		WorkspaceIdentifier read = new WorkspaceIdentifier("nonexistantobjects");
		ws.createWorkspace(foo, read.getIdentifierString(), false, null, null);
		long readid = ws.getWorkspaceInformation(foo, read).getId();
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("fubar", "thingy");
		JsonNode savedata = MAPPER.valueToTree(data);
		List<WorkspaceSaveObject> objects = new ArrayList<WorkspaceSaveObject>();
		objects.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("myname"),
				savedata, SAFE_TYPE1, null, new Provenance(foo), false));
		ws.saveObjects(foo, read, objects, getIdFactory(foo));
		getNonExistantObject(foo, new ObjectIdentifier(read, 2),
				"No object with id 2 exists in workspace " + readid);
		getNonExistantObject(foo, new ObjectIdentifier(read, 1, 2),
				"No object with id 1 (name myname) and version 2 exists in workspace " + readid);
		getNonExistantObject(foo, new ObjectIdentifier(read, "myname2"),
				"No object with name myname2 exists in workspace " + readid);
		getNonExistantObject(foo, new ObjectIdentifier(read, "myname", 2),
				"No object with id 1 (name myname) and version 2 exists in workspace " + readid);
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
		Date lastReadDate = readinfo.getModDate();
		Map<String, String> data1 = new HashMap<String, String>();
		Map<String, String> data2 = new HashMap<String, String>();
		data1.put("data", "1");
		data2.put("data", "2");
		WorkspaceSaveObject sobj1 = new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("obj"), data1, SAFE_TYPE1, null, new Provenance(user), false);
		ws.saveObjects(user, read, Arrays.asList(sobj1,
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("obj"), data2, SAFE_TYPE1,
				null, new Provenance(user), false)), getIdFactory(user));
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
		lastReadDate = assertWorkspaceDateUpdated(user, read, lastReadDate, "ws date updated on delete");
		String err = String.format("Object 1 (name obj) in workspace %s has been deleted", wsid);
		failToGetDeletedObjects(user, objs, err);
		failToGetDeletedObjects(user, obj1, err);
		failToGetDeletedObjects(user, obj2, err);
		
		try {
			ws.setObjectsDeleted(user, obj2, true); //should have no effect
		} catch (NoSuchObjectException nsoe) {
			assertThat("correct exception", nsoe.getLocalizedMessage(),
					is("Object 1 (name obj) in workspace " + wsid + " has been deleted"));
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
						SAFE_TYPE1, null, new Provenance(user), false)), getIdFactory(user));
		ObjectIdentifier o3 = new ObjectIdentifier(read, "obj", 3);
		idToData.put(o3, data1);
		objs = new ArrayList<ObjectIdentifier>(idToData.keySet());
		
		checkNonDeletedObjs(user, idToData);
		assertThat("can get ws description", ws.getWorkspaceDescription(user, read),
				is("descrip"));
		checkWSInfo(ws.getWorkspaceInformation(user, read), user, "deleteundelete", 1, Permission.OWNER, false, "unlocked", MT_META);
		WorkspaceUser bar = new WorkspaceUser("bar");
		ws.setPermissions(user, read, Arrays.asList(bar), Permission.ADMIN);
		Map<User, Permission> p = new HashMap<User, Permission>();
		p.put(user, Permission.OWNER);
		p.put(bar, Permission.ADMIN);
		assertThat("can get perms", ws.getPermissions(user, read), is(p));
		try {
			ws.setWorkspaceDeleted(bar, read, true);
			fail("Non owner deleted workspace");
		} catch (WorkspaceAuthorizationException e) {
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is("User bar may not delete workspace deleteundelete"));
		}
		WorkspaceInformation read1 = ws.getWorkspaceInformation(user, read);
		ws.setWorkspaceDeleted(user, read, true);
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
			ws.getPermissions(user, read);
			fail("got perms from deleted workspace");
		} catch (NoSuchWorkspaceException e) {
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is("Workspace deleteundelete is deleted"));
		}
		failGetObjects(bar, objs, new InaccessibleObjectException(
				"Object obj cannot be accessed: Workspace deleteundelete is deleted"));
		try {
			ws.getObjectInformation(bar, objs, false, false);
			fail("got obj meta from deleted workspace");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception msg", ioe.getLocalizedMessage(),
					is("Object obj cannot be accessed: Workspace deleteundelete is deleted"));
		}
		try {
			ws.saveObjects(bar, read, Arrays.asList(sobj1), getIdFactory(bar));
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
		ws.setWorkspaceDeleted(user, read, false);
		WorkspaceInformation read3 = ws.getWorkspaceInformation(user, read);
		checkNonDeletedObjs(user, idToData);
		assertThat("can get ws description", ws.getWorkspaceDescription(user, read),
				is("descrip"));
		checkWSInfo(ws.getWorkspaceInformation(user, read), user, "deleteundelete", 1, Permission.OWNER, false, "unlocked", MT_META);
		ws.setPermissions(user, read, Arrays.asList(bar), Permission.ADMIN);
		assertThat("can get perms", ws.getPermissions(user, read), is(p));
		
		assertTrue("date changed on delete", read1.getModDate().before(read2.getModDate()));
		assertTrue("date changed on undelete", read2.getModDate().before(read3.getModDate()));
	}

	@Test
	public void testTypeMd5s() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		String typeDefName = "SomeModule.AType";
		Map<String,String> type2md5 = ws.translateToMd5Types(Arrays.asList(typeDefName + "-1.0"),null);
		Assert.assertEquals(1, type2md5.size());
		String md5TypeDef = type2md5.get(typeDefName + "-1.0");
		Assert.assertNotNull(md5TypeDef);
		Map<String, List<String>> md52semantic = ws.translateFromMd5Types(Arrays.asList(md5TypeDef));
		Assert.assertEquals(1, md52semantic.size());
		List<String> semList = md52semantic.get(md5TypeDef);
		Assert.assertNotNull(semList);
		Assert.assertEquals(2, semList.size());
		for (String semText : semList) {
			TypeDefId semTypeDef = TypeDefId.fromTypeString(semText);
			Assert.assertEquals(typeDefName, semTypeDef.getType().getTypeString());
			String verText = semTypeDef.getVerString();
			Assert.assertTrue("0.1".equals(verText) || "1.0".equals(verText));
		}
	}
	
	@Test
	public void testListModules() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		Map<String,String> moduleNamesInList = new HashMap<String,String>();
		for(String mod:ws.listModules(null)) {
			moduleNamesInList.put(mod, "");
		}
		Assert.assertTrue(moduleNamesInList.containsKey("SomeModule"));
		Assert.assertTrue(moduleNamesInList.containsKey("TestModule"));
	}
	
	@Test
	public void testListModuleVersions() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		Assert.assertEquals(3, ws.getModuleVersions("SomeModule", null).size());
		Assert.assertEquals(4, ws.getModuleVersions("SomeModule", new WorkspaceUser("foo")).size());
		Assert.assertEquals(2, ws.getModuleVersions("TestModule", null).size());
		Assert.assertEquals(5, ws.getModuleVersions("TestModule", new WorkspaceUser("foo")).size());
	}
	
	@Test
	public void testGetModuleInfo() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		ModuleInfo m = ws.getModuleInfo(null, new ModuleDefId("TestModule"));
		Assert.assertTrue(m.isReleased());
		Map<String,String> funcNamesInList = new HashMap<String,String>();
		for(String func : m.getFunctions() ){
			funcNamesInList.put(func, "");
		}
		Assert.assertTrue(funcNamesInList.containsKey("TestModule.getFeature-2.0"));
		Assert.assertTrue(funcNamesInList.containsKey("TestModule.getGenome-1.0"));

		Map<String,String> typeNamesInList = new HashMap<String,String>();
		for(Entry<AbsoluteTypeDefId, String> type : m.getTypes().entrySet() ){
			typeNamesInList.put(type.getKey().getTypeString(),"");
		}
		Assert.assertTrue(typeNamesInList.containsKey("TestModule.Genome-2.0"));
		Assert.assertTrue(typeNamesInList.containsKey("TestModule.Feature-1.0"));
		
		try {
			ws.getModuleInfo(null, new ModuleDefId("MadeUpModuleThatIsNotThere"));
			fail("getModuleInfo of non existant module should throw a NoSuchModuleException");
		} catch (NoSuchModuleException e) {}
		ModuleInfo m2 = ws.getModuleInfo(new WorkspaceUser("foo"), new ModuleDefId("UnreleasedModule"));
		Assert.assertEquals("foo", m2.getOwners().get(0));
		Assert.assertFalse(m2.isReleased());
		List<Long> verList = ws.getModuleVersions("UnreleasedModule", new WorkspaceUser("foo"));
		Assert.assertEquals(1, verList.size());
		Assert.assertEquals(m2.getVersion(), verList.get(0));
	}
	
	@Test
	public void testGetJsonSchema() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		try {
			ws.getJsonSchema(new TypeDefId("TestModule.NonExistantType"), null);
			fail("getJsonSchema of non existant type should throw a NoSuchTypeException");
		} catch (NoSuchTypeException e) {}
		
		// get several different schemas, make sure that no exceptions are thrown and it is valid json!
		String schema = ws.getJsonSchema(new TypeDefId(new TypeDefName("TestModule.Genome"),2,0), null);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode schemaNode = mapper.readTree(schema);
		Assert.assertEquals("Genome", schemaNode.get("id").asText());
		
		schema = ws.getJsonSchema(new TypeDefId(new TypeDefName("TestModule.Genome"),2), null);
		schemaNode = mapper.readTree(schema);
		Assert.assertEquals("Genome", schemaNode.get("id").asText());
		
		schema = ws.getJsonSchema(new TypeDefId("TestModule.Genome"), null);
		schemaNode = mapper.readTree(schema);
		Assert.assertEquals("Genome", schemaNode.get("id").asText());
	}
	
	@Test
	public void testGetTypeInfo() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		TypeDetailedInfo info = ws.getTypeInfo("TestModule.Genome", false, null);
		Assert.assertEquals("TestModule.Genome-2.0",info.getTypeDefId());
		Assert.assertEquals(1, info.getReleasedModuleVersions().size());
		Assert.assertEquals(2, info.getReleasedTypeVersions().size());
		info = ws.getTypeInfo("TestModule.Feature", false, null);
		Assert.assertEquals("TestModule.Feature-1.0",info.getTypeDefId());
		Assert.assertEquals(2, info.getReleasedModuleVersions().size());
		Assert.assertEquals(1, info.getReleasedTypeVersions().size());
		TypeDetailedInfo info2 = ws.getTypeInfo("UnreleasedModule.AType-0.1", false, new WorkspaceUser("foo"));
		Assert.assertEquals(1, info2.getUsingFuncDefIds().size());
		Assert.assertEquals(1, info2.getModuleVersions().size());
		Assert.assertEquals(1, info2.getTypeVersions().size());
		Assert.assertEquals(0, info2.getReleasedModuleVersions().size());
		Assert.assertEquals(0, info2.getReleasedTypeVersions().size());
		Assert.assertTrue(info2.getJsonSchema().contains("kidl-structure"));
		Assert.assertTrue(info2.getParsingStructure().contains("Bio::KBase::KIDL::KBT::Typedef"));
	}
	
	@Test
	public void testGetFuncInfo() throws Exception {
		//see setUpWorkspaces() to find where needed specs are loaded
		try {
			ws.getFuncInfo("NoModuleThatExists.getFeature", false, null);
			fail("getFuncInfo of non existant module should throw a NoSuchModuleException");
		} catch (NoSuchModuleException e) {}
		try {
			ws.getFuncInfo("TestModule.noFunctionThatIKnowOf", false, null);
			fail("getFuncInfo of non existant module should throw a NoSuchFuncException");
		} catch (NoSuchFuncException e) {}
		FuncDetailedInfo info = ws.getFuncInfo("TestModule.getFeature", false, null);
		Assert.assertEquals("TestModule.getFeature-2.0",info.getFuncDefId());
		Assert.assertEquals(1, info.getReleasedModuleVersions().size());
		Assert.assertEquals(2, info.getReleasedFuncVersions().size());
		info = ws.getFuncInfo("TestModule.getGenome-1.0", false, null);
		Assert.assertEquals("TestModule.getGenome-1.0",info.getFuncDefId());
		Assert.assertEquals(1, info.getReleasedModuleVersions().size());
		Assert.assertEquals(1, info.getReleasedFuncVersions().size());
		FuncDetailedInfo info2 = ws.getFuncInfo("UnreleasedModule.aFunc-0.1", false, new WorkspaceUser("foo"));
		Assert.assertEquals(1, info2.getUsedTypeDefIds().size());
		Assert.assertEquals(1, info2.getModuleVersions().size());
		Assert.assertEquals(1, info2.getFuncVersions().size());
		Assert.assertEquals(0, info2.getReleasedModuleVersions().size());
		Assert.assertEquals(0, info2.getReleasedFuncVersions().size());
		Assert.assertTrue(info2.getParsingStructure().contains("Bio::KBase::KIDL::KBT::Funcdef"));
	}
	
	private void setUpCopyWorkspaces(WorkspaceUser user1, WorkspaceUser user2,
			String refws, String ws1, String ws2) throws Exception {
		TypeDefId reftype = new TypeDefId(new TypeDefName("CopyRev", "RefType"), 1, 0);
		
		WorkspaceIdentifier refs = new WorkspaceIdentifier(refws);
		ws.createWorkspace(user1, refs.getName(), false, null, null);
		LinkedList<WorkspaceSaveObject> refobjs = new LinkedList<WorkspaceSaveObject>();
		for (int i = 0; i < 4; i++) {
			refobjs.add(new WorkspaceSaveObject(new HashMap<String, String>(),
					SAFE_TYPE1, null, new Provenance(user1), false));
		}
		ws.saveObjects(user1, refs, refobjs, getIdFactory(user1));
		List<WorkspaceSaveObject> wso = Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("auto2"), new HashMap<String, String>(),
				SAFE_TYPE1, null, new Provenance(user1), false));
		ws.saveObjects(user1, refs, wso, getIdFactory(user1));
		ws.saveObjects(user1, refs, wso, getIdFactory(user1));
		
		
		Map<String, String> meta1 = makeSimpleMeta("foo", "bar");
		Map<String, String> meta2 = makeSimpleMeta("foo", "baz");
		Map<String, String> meta3 = makeSimpleMeta("foo", "bak");
		Map<String, List<String>> data1 = makeRefData(refws + "/auto2/2");
		Map<String, List<String>> data2 = makeRefData(refws + "/auto4");
		Map<String, List<String>> data3 = makeRefData(refws + "/auto1");
		
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
		Date cp1LastDate = cp1info.getModDate();
		Date cp2LastDate = cp2info.getModDate();
		
		ObjectIdentifier oihide = new ObjectIdentifier(cp1, "hide");
		List<ObjectInformation> objs = ws.getObjectHistory(user1, oihide);
		ObjectInformation save11 = objs.get(0);
		ObjectInformation save12 = objs.get(1);
		ObjectInformation save13 = objs.get(2);
		
		WorkspaceObjectData wod = ws.getObjects(user1, Arrays.asList(oihide)).get(0);
		WorkspaceObjectData swod = ws.getObjectsSubSet(user1, objIDToSubObjID(Arrays.asList(oihide))).get(0);
		WorkspaceObjectInformation woi = ws.getObjectProvenance(user1, Arrays.asList(oihide)).get(0);
		assertThat("copy ref for obj is null", wod.getCopyReference(), is((Reference) null));
		assertThat("copy ref for sub obj is null", swod.getCopyReference(), is((Reference) null));
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
						"Object whooga cannot be accessed: Anonymous users may not read workspace copyrevert1"));
		failRevert(null, new ObjectIdentifier(cp1, "whooga"), new InaccessibleObjectException(
						"Object whooga cannot be accessed: Anonymous users may not write to workspace copyrevert1"));
		
		failCopy(user1, new ObjectIdentifier(cp1, "foo"),
				new ObjectIdentifier(cp1, "bar"), new NoSuchObjectException(
						"No object with name foo exists in workspace " + wsid1));
		failRevert(user1, new ObjectIdentifier(cp1, "foo"),  new NoSuchObjectException(
						"No object with name foo exists in workspace " + wsid1));
		failRevert(user1, new ObjectIdentifier(cp1, "orig", 4),  new NoSuchObjectException(
						"No object with id 2 (name orig) and version 4 exists in workspace " + wsid1));
		failCopy(user1, new ObjectIdentifier(cp1, "orig"),
				new ObjectIdentifier(cp1, 7), new NoSuchObjectException(
						"Copy destination is specified as object id 7 in workspace " + wsid1 + " which does not exist."));
		
		ws.setObjectsDeleted(user1, Arrays.asList(new ObjectIdentifier(cp1, "copied")), true);
		failCopy(user1, new ObjectIdentifier(cp1, "copied"),
				new ObjectIdentifier(cp1, "hidetarget"), new NoSuchObjectException(
						"Object 5 (name copied) in workspace " + wsid1 + " has been deleted"));
		failRevert(user1, new ObjectIdentifier(cp1, "copied"), new NoSuchObjectException(
						"Object 5 (name copied) in workspace " + wsid1 + " has been deleted"));
		//now works
//		failCopy(user1, new ObjectIdentifier(cp1, "orig"),
//				new ObjectIdentifier(cp1, "copied"), new NoSuchObjectException(
//						"Object 5 (name copied) in workspace " + wsid1 + " has been deleted"));
		
		cp2LastDate = ws.getWorkspaceInformation(user1, cp2).getModDate();
		ws.copyObject(user1, new ObjectIdentifier(cp1, "orig"), new ObjectIdentifier(cp2, "foo")); //should work
		cp2LastDate = assertWorkspaceDateUpdated(user1, cp2, cp2LastDate, "ws date updated on copy");
		ws.setWorkspaceDeleted(user2, cp2, true);
		failCopy(user1, new ObjectIdentifier(cp1, "orig"), new ObjectIdentifier(cp2, "foo1"),
				new InaccessibleObjectException("Object foo1 cannot be accessed: Workspace copyrevert2 is deleted"));
		failCopy(user1, new ObjectIdentifier(cp2, "foo"), new ObjectIdentifier(cp2, "foo1"),
				new InaccessibleObjectException("Object foo cannot be accessed: Workspace copyrevert2 is deleted"));
		failRevert(user1, new ObjectIdentifier(cp2, "foo"),
				new InaccessibleObjectException("Object foo cannot be accessed: Workspace copyrevert2 is deleted"));
		
		ws.setWorkspaceDeleted(user2, cp2, false);
		ws.setPermissions(user2, cp2, Arrays.asList(user1), Permission.READ);
		ws.copyObject(user1,  new ObjectIdentifier(cp2, "foo"), new ObjectIdentifier(cp1, "foo")); //should work
		failCopy(user1,  new ObjectIdentifier(cp1, "foo"), new ObjectIdentifier(cp2, "foo"),
				new InaccessibleObjectException("Object foo cannot be accessed: User foo may not write to workspace copyrevert2"));
		failRevert(user1,  new ObjectIdentifier(cp2, "foo", 1),
				new InaccessibleObjectException("Object foo cannot be accessed: User foo may not write to workspace copyrevert2"));
		
		ws.setPermissions(user2, cp2, Arrays.asList(user1), Permission.NONE);
		failCopy(user1,  new ObjectIdentifier(cp2, "foo"), new ObjectIdentifier(cp1, "foo"),
				new InaccessibleObjectException("Object foo cannot be accessed: User foo may not read workspace copyrevert2"));
		
		ws.setPermissions(user2, cp2, Arrays.asList(user1), Permission.WRITE);
		ws.lockWorkspace(user2, cp2);
		failCopy(user1, new ObjectIdentifier(cp1, "orig"),
				new ObjectIdentifier(cp2, "foo2"), new InaccessibleObjectException(
						"Object foo2 cannot be accessed: The workspace with id " + wsid2 + ", name copyrevert2, is locked and may not be modified"));
		failRevert(user1, new ObjectIdentifier(cp2, "foo1", 1), new InaccessibleObjectException(
						"Object foo1 cannot be accessed: The workspace with id " + wsid2 + ", name copyrevert2, is locked and may not be modified"));
	}

	private void checkUnhiddenObjectCount(WorkspaceUser user,
			WorkspaceIdentifier wsi, int unhidden, int all)
			throws Exception {
		List<ObjectInformation> objs =
				ws.listObjects(user, Arrays.asList(wsi), null, null, null, null, 
						null, null, false, false, false, true, false, false, -1, -1);
		assertThat("orig objects hidden", objs.size(), is(unhidden));
		objs =
				ws.listObjects(user, Arrays.asList(wsi), null, null, null, null, 
						null, null, true, false, false, true, false, false, -1, -1);
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
		
		Provenance emptyprov1 = new Provenance(user1);
		Provenance emptyprov2 = new Provenance(user2);
		List<WorkspaceSaveObject> data = new LinkedList<WorkspaceSaveObject>();
		data.add(new WorkspaceSaveObject(new HashMap<String, Object>(), SAFE_TYPE1, null, emptyprov1, false));
		
		ws.saveObjects(user1, wsiSource1, data, new IdReferenceHandlerSetFactory(0));
		ws.saveObjects(user1, wsiSource2, data, new IdReferenceHandlerSetFactory(0));
		final ObjectIdentifier source1 = new ObjectIdentifier(wsiSource1, 1);
		final ObjectIdentifier source2 = new ObjectIdentifier(wsiSource2, 1);
		final ObjectIdentifier copied1 = new ObjectIdentifier(wsiCopied, "foo");
		final ObjectIdentifier copied2 = new ObjectIdentifier(wsiCopied, "foo1");
		ws.copyObject(user2, source1, copied1);
		ws.copyObject(user2, source2, copied2);
		
		ws.saveObjects(user2, wsiCopied, data, new IdReferenceHandlerSetFactory(0));
		final ObjectIdentifier nocopy = new ObjectIdentifier(wsiCopied, 3L);

		data.clear();
		Map<String, Object> ref = new HashMap<String, Object>();
		ref.put("refs", Arrays.asList(wsiCopied.getName() + "/foo"));
		data.add(new WorkspaceSaveObject(ref, REF_TYPE, null, emptyprov2, false));
		ws.saveObjects(user2, wsiCopied, data, new IdReferenceHandlerSetFactory(1));
		ObjectChain copyoc1 = new ObjectChain(new ObjectIdentifier(wsiCopied, 4L),
				Arrays.asList(copied1));
		
		ref.put("refs", Arrays.asList(wsiCopied.getName() + "/foo1"));
		ws.saveObjects(user2, wsiCopied, data, new IdReferenceHandlerSetFactory(1));
		ObjectChain copyoc2 = new ObjectChain(new ObjectIdentifier(wsiCopied, 5L),
				Arrays.asList(copied2));
		
		ref.put("refs", Arrays.asList(wsiCopied.getName() + "/3"));
		ws.saveObjects(user2, wsiCopied, data, new IdReferenceHandlerSetFactory(1));
		ObjectChain nocopyoc = new ObjectChain(new ObjectIdentifier(wsiCopied, 6L),
				Arrays.asList(nocopy));
		
		
		final TestReference expectedRef1 = new TestReference(wsid1, 1, 1);
		final TestReference expectedRef2 = new TestReference(wsid2, 1, 1);
		List<ObjectIdentifier> testobjs = Arrays.asList(copied1, nocopy, copied2);
		List<ObjectChain> testocs = Arrays.asList(copyoc1, nocopyoc, copyoc2);
		
		List<TestReference> refnullref = Arrays.asList(
				expectedRef1, (TestReference) null, expectedRef2);
		List<TestReference> nullnullref = Arrays.asList(
				(TestReference) null, (TestReference) null, expectedRef2);
		List<TestReference> refnullnull = Arrays.asList(
				expectedRef1, (TestReference) null, (TestReference) null);
		
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

	private void checkCopyReference(WorkspaceUser user, List<ObjectIdentifier> testobjs,
			List<ObjectChain> testocs, List<TestReference> testRef, List<Boolean> copyAccessible) throws Exception {
		
		List<List<WorkspaceObjectInformation>> infos =
				new LinkedList<List<WorkspaceObjectInformation>>();
		
		infos.add(ws.getObjectProvenance(user, testobjs));
		infos.add(fromObjectData(ws.getObjects(user, testobjs)));
		infos.add(fromObjectData(ws.getObjectsSubSet(user, objIDToSubObjID(testobjs))));
		infos.add(fromObjectData(ws.getReferencedObjects(user, testocs)));
		
		for (List<WorkspaceObjectInformation> info: infos) {
			for (int i = 0; i < info.size(); i++) {
				WorkspaceObjectInformation inf = info.get(i);
				assertThat("correct reference ", inf.getCopyReference() == null ? null :
					new TestReference(inf.getCopyReference()), is(testRef.get(i)));
				assertThat("correct inaccessibility", inf.isCopySourceInaccessible(),
						is(copyAccessible.get(i)));
				
			}
		}
		
	}
	
	private List<WorkspaceObjectInformation> fromObjectData(
			List<WorkspaceObjectData> data) {
		List<WorkspaceObjectInformation> ret = new LinkedList<WorkspaceObjectInformation>();
		for (WorkspaceObjectData d: data) {
			ret.add((WorkspaceObjectInformation) d);
		}
		return ret;
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
		
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("clone", "workspace");
		
		WorkspaceInformation info1 = ws.cloneWorkspace(user1, cp1, clone1.getName(), false, null, meta);
		
		checkWSInfo(clone1, user1, "newclone", 3, Permission.OWNER, false, info1.getId(),
				info1.getModDate(), "unlocked", meta);
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
		WorkspaceInformation info2 = ws.cloneWorkspace(user1, cp1, clone2.getName(), true, "my desc", null);
		
		checkWSInfo(clone2, user1, "newclone2", 2, Permission.OWNER, true, info2.getId(),
				info2.getModDate(), "unlocked", MT_META);
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
		//workspaceIdentifier used in the workspace method to check ws names tested extensively elsewhere, so just
		// a couple tests here
		failClone(user1, cp1, "bar:fakename", null, new IllegalArgumentException(
				"Workspace name bar:fakename must only contain the user name foo prior to the : delimiter"));
		failClone(user1, cp1, "9", null, new IllegalArgumentException(
				"Workspace names cannot be integers: 9"));
		failClone(user1, cp1, "foo:9", null, new IllegalArgumentException(
				"Workspace names cannot be integers: foo:9"));
		failClone(user1, cp1, "foo:fake(name", null, new IllegalArgumentException(
				"Illegal character in workspace name foo:fake(name: ("));
		failClone(user2, cp1, "fakename", null, new WorkspaceAuthorizationException("User bar may not read workspace clone1"));
		failClone(user1, cp1, "newclone2", null, new PreExistingWorkspaceException(
				"Workspace name newclone2 is already in use"));
		failClone(user1, new WorkspaceIdentifier("noclone"), "fakename", null,
				new NoSuchWorkspaceException("No workspace with name noclone exists", cp1));
		
		ws.lockWorkspace(user1, cp1);
		
		WorkspaceIdentifier clone3 = new WorkspaceIdentifier("newclone3");
		WorkspaceInformation info3 = ws.cloneWorkspace(user1, cp1, clone3.getName(), false, "my desc2", meta);
		
		checkWSInfo(clone3, user1, "newclone3", 2, Permission.OWNER, false, info3.getId(),
				info3.getModDate(), "unlocked", meta);
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
		ws.cloneWorkspace(user1, cp1, clone4.getName(), true, LONG_TEXT, null);
		assertThat("desc ok", ws.getWorkspaceDescription(user1, clone4), is(LONG_TEXT.subSequence(0, 1000)));
		
		Map<String, String> bigmeta = new HashMap<String, String>();
		for (int i = 0; i < 141; i++) {
			bigmeta.put("thing" + i, TEXT100);
		}
		ws.cloneWorkspace(user1, cp1, "fakename", false, "eeswaffertheen", bigmeta);
		bigmeta.put("thing", TEXT100);
		failClone(user1, cp1, "fakename", bigmeta, new IllegalArgumentException(
				"Metadata size of 16076 is > 16000 bytes"));
		
		ws.setGlobalPermission(user1, clone2, Permission.NONE);
		ws.setGlobalPermission(user1, clone4, Permission.NONE);
	}

	@Test
	public void lockWorkspace() throws Exception {
		WorkspaceUser user = new WorkspaceUser("lockuser");
		WorkspaceUser user2 = new WorkspaceUser("lockuser2");
		WorkspaceIdentifier wsi = lockWS;
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("some meta", "for u");
		long wsid = ws.createWorkspace(user, wsi.getName(), false, null, meta).getId();
		ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), false)), getIdFactory(user));
		ObjectIdentifier oi = new ObjectIdentifier(wsi, "auto1");
		//these should work
		WorkspaceInformation info = ws.lockWorkspace(user, wsi);
		checkWSInfo(info, user, "lock", 1, Permission.OWNER, false, "locked", meta);
		successGetObjects(user, Arrays.asList(oi));
		ws.cloneWorkspace(user, wsi, "lockclone", false, null, null);
		ws.copyObject(user, oi, new ObjectIdentifier(new WorkspaceIdentifier("lockclone"), "foo"));
		ws.setPermissions(user, wsi, Arrays.asList(user2), Permission.WRITE);
		ws.setPermissions(user, wsi, Arrays.asList(user2), Permission.NONE);
		ws.getPermissions(user, wsi);
		ws.getWorkspaceDescription(user, wsi);
		ws.getWorkspaceInformation(user, wsi);
		ws.listObjects(user, Arrays.asList(wsi), null, null, null, null, 
				null, null, false, false, false, false, false, false, -1, -1);
		
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
			ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
					new HashMap<String, String>(), SAFE_TYPE1, null,
					new Provenance(user), false)), getIdFactory(user));
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
		failWSMeta(user2, wsi, "some meta", "val", new WorkspaceAuthorizationException(
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
		Date lastWSDate = info1.getModDate();
		ws.createWorkspace(user2, wsi2.getName(), false, null, null);
		ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), false)), getIdFactory(user));
		ws.saveObjects(user2, wsi2, Arrays.asList(new WorkspaceSaveObject(
				new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), false)), getIdFactory(user));
		lastWSDate = ws.getWorkspaceInformation(user, wsi).getModDate();
		ObjectInformation info = ws.renameObject(user, new ObjectIdentifier(wsi, "auto1"), "mynewname");
		assertWorkspaceDateUpdated(user, wsi, lastWSDate, "ws date updated on rename");
		checkObjInfo(info, 1L, "mynewname", SAFE_TYPE1.getTypeString(), 1, user,
				wsid1, "renameObj", "99914b932bd37a50b983c5e7c90ae93b", 2, null);
		String newname = ws.listObjects(user, Arrays.asList(wsi), null, null, null, null, 
				null, null, false, false,false, false, false, false, -1, -1).get(0).getObjectName();
		assertThat("object renamed", newname, is("mynewname"));
		
		ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("myoldname"), new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), false)), getIdFactory(user));
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "bad%name", new IllegalArgumentException(
				"Illegal character in object name bad%name: %"));
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "2", new IllegalArgumentException(
				"Object names cannot be integers: 2"));
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "myoldname", new IllegalArgumentException(
				"There is already an object in the workspace named myoldname"));
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "mynewname", new IllegalArgumentException(
				"Object is already named mynewname"));
		failObjRename(user, new ObjectIdentifier(wsi, "bar"), "foo", new NoSuchObjectException(
				"No object with name bar exists in workspace " + wsid1));
		failObjRename(user, new ObjectIdentifier(wsi2, "auto1"), "foo",
				new InaccessibleObjectException(
						"Object auto1 cannot be accessed: User renameObjUser may not rename objects in workspace renameObj2"));
		failObjRename(null, new ObjectIdentifier(wsi2, "auto1"), "foo",
				new InaccessibleObjectException(
						"Object auto1 cannot be accessed: Anonymous users may not rename objects in workspace renameObj2"));
		
		ws.setObjectsDeleted(user, Arrays.asList(new ObjectIdentifier(wsi, "mynewname")), true);
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "foo", new InaccessibleObjectException(
				"Object 1 (name mynewname) in workspace " + wsid1 + " has been deleted"));
		ws.setWorkspaceDeleted(user, wsi, true);
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "foo", new InaccessibleObjectException(
				"Object mynewname cannot be accessed: Workspace renameObj is deleted"));
		ws.setWorkspaceDeleted(user, wsi, false);
		failObjRename(user, new ObjectIdentifier(new WorkspaceIdentifier("renameObjfake"), "mynewname"), "foo", new InaccessibleObjectException(
				"Object mynewname cannot be accessed: No workspace with name renameObjfake exists"));
		ws.lockWorkspace(user, wsi);
		failObjRename(user, new ObjectIdentifier(wsi, "mynewname"), "foo", new InaccessibleObjectException(
				"Object mynewname cannot be accessed: The workspace with id " + wsid1 + ", name renameObj, is locked and may not be modified"));
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
		WorkspaceInformation info1 = ws.createWorkspace(user, wsi.getName(), false, null, meta);
		WorkspaceIdentifier newwsi = new WorkspaceIdentifier(user.getUser() + ":newRenameWS");
		Thread.sleep(2); //make sure timestamp is different on rename
		WorkspaceInformation info2 = ws.renameWorkspace(user, wsi, newwsi.getName());
		checkWSInfo(info2, user, newwsi.getName(), 0, Permission.OWNER, false, "unlocked", meta);
		assertTrue("date updated on ws rename", info2.getModDate().after(info1.getModDate()));
		checkWSInfo(ws.getWorkspaceInformation(user, newwsi),
				user, newwsi.getName(), 0, Permission.OWNER, false, "unlocked", meta);
		
		failWSRename(user, newwsi, "foo|bar",
				new IllegalArgumentException("Illegal character in workspace name foo|bar: |"));
		failWSRename(user, newwsi, "renameWSUser:9",
				new IllegalArgumentException("Workspace names cannot be integers: renameWSUser:9"));
		failWSRename(user, newwsi, "9",
				new IllegalArgumentException("Workspace names cannot be integers: 9"));
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
		ws.setGlobalPermission(user, wsi, Permission.READ);
		assertThat("read set correctly", ws.getPermissions(user, wsi).get(new AllUsers('*')),
				is(Permission.READ));
		ws.getWorkspaceDescription(user2, wsi);
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
		
		ws.setGlobalPermission(user, wsi, Permission.NONE);
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
				new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), false)), getIdFactory(user)).get(0);
		ObjectInformation auto2 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), true)), getIdFactory(user)).get(0);
		ObjectInformation obj1 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("obj1"), new HashMap<String, String>(), SAFE_TYPE1, null,
				new Provenance(user), true)), getIdFactory(user)).get(0);
		
		List<ObjectInformation> expected = new ArrayList<ObjectInformation>();
		expected.add(auto1);
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi), null, null, null, null, 
				null, null, false, false, false, false, true, false, -1, -1), expected);
		
		expected.add(auto2);
		expected.add(obj1);
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi), null, null, null, null, 
				null, null, true, false, false, false, true, false, -1, -1), expected);
		
		ws.setObjectsHidden(user, Arrays.asList(new ObjectIdentifier(wsi, 3), new ObjectIdentifier(wsi, "auto2")), false);
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi), null, null, null, null, 
				null, null, false, false, false, false, true, false, -1, -1), expected);
		
		ws.setObjectsHidden(user, Arrays.asList(new ObjectIdentifier(wsi, 1), new ObjectIdentifier(wsi, "obj1")), true);
		expected.remove(auto1);
		expected.remove(obj1);
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi), null, null, null, null, 
				null, null, false, false, false, false, true, false, -1, -1), expected);
		
		failSetHide(user, new ObjectIdentifier(wsi, "fake"), true, new NoSuchObjectException(
				"No object with name fake exists in workspace " + wsid1));
		failSetHide(user, new ObjectIdentifier(new WorkspaceIdentifier("fake"), "fake"), true, new InaccessibleObjectException(
				"Object fake cannot be accessed: No workspace with name fake exists"));
		
		failSetHide(user2, new ObjectIdentifier(wsi, "auto1"), true, new InaccessibleObjectException(
				"Object auto1 cannot be accessed: User hideObjUser2 may not hide objects from workspace hideObj"));
		failSetHide(null, new ObjectIdentifier(wsi, "auto1"), true, new InaccessibleObjectException(
				"Object auto1 cannot be accessed: Anonymous users may not hide objects from workspace hideObj"));
		
		ws.setObjectsDeleted(user, Arrays.asList(new ObjectIdentifier(wsi, 3)), true);
		failSetHide(user, new ObjectIdentifier(wsi, 3), true, new NoSuchObjectException(
				"Object 3 (name obj1) in workspace " + wsid1 + " has been deleted"));
		ws.setObjectsDeleted(user, Arrays.asList(new ObjectIdentifier(wsi, 3)), false);
		
		ws.setWorkspaceDeleted(user, wsi, true);
		failSetHide(user, new ObjectIdentifier(new WorkspaceIdentifier("fake"), "fake"), true, new InaccessibleObjectException(
				"Object fake cannot be accessed: No workspace with name fake exists"));
		ws.setWorkspaceDeleted(user, wsi, false);
		
		ws.lockWorkspace(user, wsi);
		failSetHide(user, new ObjectIdentifier(wsi, 3), true, new InaccessibleObjectException(
				"Object 3 cannot be accessed: The workspace with id " + wsid1 +
				", name hideObj, is locked and may not be modified"));
	}

	@Test
	public void listWorkspaces() throws Exception {
		WorkspaceUser user = new WorkspaceUser("listUser");
		WorkspaceUser user2 = new WorkspaceUser("listUser2");
		WorkspaceUser user3 = new WorkspaceUser("listUser3");
		
		Map<String, String> meta1 = new HashMap<String, String>();
		meta1.put("this is", "some meta meta");
		meta1.put("bro", "heim");
		
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("suckmaster", "burstingfoam");
		
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
		checkWSInfoList(ws.listWorkspaces(user, null, null, null, null, null, true, false, false), expected);
		checkWSInfoList(ws.listWorkspaces(user, null, null, MT_META, null, null, true, false, false), expected);
		
		expected.put(globalreadable, false);
		expected.put(listuser3gl, false);
		WorkspaceInformation locked = null;
		try {
			locked = ws.getWorkspaceInformation(user, lockWS);
		} catch (NoSuchWorkspaceException nswe) {
			//ignore - means that the locking ws test has not been run yet
		}
		if (locked != null) {
			expected.put(locked, false);
		}
		checkWSInfoList(ws.listWorkspaces(user, null, null, null, null, null, false, false, false), expected);
		
		expected.put(deletedws, true);
		checkWSInfoList(ws.listWorkspaces(user, null, null, null, null, null, false, true, false), expected);
		
		expected.remove(globalreadable);
		expected.remove(locked);
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
		if (locked != null) {
			expected.put(locked, false);
		}
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
		checkWSInfoList(ws.listWorkspaces(user, null, null, querymeta, null, null, false,
				false, false), expected);
		
		querymeta.clear();
		querymeta.put("this is", "some meta meta");
		expected.clear();
		expected.put(stdws, false);
		expected.put(readable, false);
		checkWSInfoList(ws.listWorkspaces(user, null, null, querymeta, null, null, false,
				false, false), expected);
		
		querymeta.clear();
		querymeta.put("bro", "heim");
		checkWSInfoList(ws.listWorkspaces(user, null, null, querymeta, null, null, false,
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
		Date beforeall = new Date(i1.getModDate().getTime() - 1);
		Date afterall = new Date(i5.getModDate().getTime() + 1);
		checkWSInfoList(ws.listWorkspaces(u, null, null, null, null, null, true, false, false),
				Arrays.asList(i1, i2, i3, i4, i5));
		checkWSInfoList(ws.listWorkspaces(u, null, null, null, beforeall, afterall, true, false, false),
				Arrays.asList(i1, i2, i3, i4, i5));
		checkWSInfoList(ws.listWorkspaces(u, null, null, null, afterall, beforeall, true, false, false),
				new ArrayList<WorkspaceInformation>());
		checkWSInfoList(ws.listWorkspaces(u, null, null, null, i3.getModDate(), i4.getModDate(), true, false, false),
				new ArrayList<WorkspaceInformation>());
		checkWSInfoList(ws.listWorkspaces(u, null, null, null, i2.getModDate(), i4.getModDate(), true, false, false),
				Arrays.asList(i3));
		checkWSInfoList(ws.listWorkspaces(u, null, null, null, i2.getModDate(), null, true, false, false),
				Arrays.asList(i3, i4, i5));
		checkWSInfoList(ws.listWorkspaces(u, null, null, null, null, i4.getModDate(), true, false, false),
				Arrays.asList(i1, i2, i3));
		checkWSInfoList(ws.listWorkspaces(u, null, null, null, new Date(i2.getModDate().getTime() - 1),
				i5.getModDate(), true, false, false),
				Arrays.asList(i2, i3, i4));
		
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
		long wsid1 = ws.createWorkspace(user, wsi.getName(), false, null, null).getId();
		ws.createWorkspace(user2, readable.getName(), false, null, null).getId();
		ws.setPermissions(user2, readable, Arrays.asList(user), Permission.READ);
		long wsidwrite = ws.createWorkspace(user2, writeable.getName(), false, null, null).getId();
		ws.setPermissions(user2, writeable, Arrays.asList(user), Permission.WRITE);
		ws.createWorkspace(user2, adminable.getName(), false, null, null).getId();
		ws.setPermissions(user2, adminable, Arrays.asList(user), Permission.ADMIN);
		WorkspaceUser user3 = new WorkspaceUser("listObjUser3");
		ws.createWorkspace(user3, thirdparty.getName(), true, null, null).getId();
		
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("meta1", "1");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("meta2", "2");
		Map<String, String> meta3 = new HashMap<String, String>();
		meta3.put("meta3", "3");
		Map<String, String> meta32 = new HashMap<String, String>();
		meta32.put("meta3", "3");
		meta32.put("meta2", "2");
		
		
		Map<String, Object> passTCdata = new HashMap<String, Object>();
		passTCdata.put("thing", "athing");
		
		ObjectInformation std = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("std"), new HashMap<String, String>(), SAFE_TYPE1,
				null, new Provenance(user), false)), getIdFactory(user)).get(0);
		ObjectInformation stdnometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "std")), false, false).get(0);
		
		ObjectInformation objstack1 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("objstack"), new HashMap<String, String>(), SAFE_TYPE1_10, meta,
				new Provenance(user), false)), getIdFactory(user)).get(0);
		ObjectInformation objstack1nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "objstack", 1)), false, false).get(0);
		
		ObjectInformation objstack2 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("objstack"), passTCdata, SAFE_TYPE1_20, meta2,
				new Provenance(user), false)), getIdFactory(user)).get(0);
		ObjectInformation objstack2nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "objstack", 2)), false, false).get(0);
		
		ObjectInformation type2_1 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("type2"), new HashMap<String, String>(), SAFE_TYPE2, meta,
				new Provenance(user), false)), getIdFactory(user)).get(0);
		ObjectInformation type2_1nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "type2", 1)), false, false).get(0);
		
		ObjectInformation type2_2 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("type2"), new HashMap<String, String>(), SAFE_TYPE2_10, meta2,
				new Provenance(user), false)), getIdFactory(user)).get(0);
		ObjectInformation type2_2nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "type2", 2)), false, false).get(0);
		
		ObjectInformation type2_3 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("type2"), passTCdata, SAFE_TYPE2_20, meta32,
				new Provenance(user), false)), getIdFactory(user)).get(0);
		ObjectInformation type2_3nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "type2", 3)), false, false).get(0);
		
		ObjectInformation type2_4 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("type2"), passTCdata, SAFE_TYPE2_21, meta3,
				new Provenance(user), false)), getIdFactory(user)).get(0);
		ObjectInformation type2_4nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(wsi, "type2", 4)), false, false).get(0);
		
		ObjectInformation stdws2 = ws.saveObjects(user2, writeable, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("stdws2"), new HashMap<String, String>(), SAFE_TYPE1, meta,
				new Provenance(user), false)), getIdFactory(user2)).get(0);
		ObjectInformation stdws2nometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(writeable, "stdws2")), false, false).get(0);
		
		ObjectInformation hidden = ws.saveObjects(user, writeable, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("hidden"), new HashMap<String, String>(), SAFE_TYPE1, meta2,
				new Provenance(user), false)), getIdFactory(user)).get(0);
		ObjectInformation hiddennometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(writeable, "hidden")), false, false).get(0);
		ws.setObjectsHidden(user, Arrays.asList(new ObjectIdentifier(writeable, "hidden")), true);
		
		ObjectInformation deleted = ws.saveObjects(user2, writeable, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("deleted"), new HashMap<String, String>(), SAFE_TYPE1, meta32,
				new Provenance(user), false)), getIdFactory(user2)).get(0);
		ObjectInformation deletednometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(writeable, "deleted")), false, false).get(0);
		ws.setObjectsDeleted(user, Arrays.asList(new ObjectIdentifier(writeable, "deleted")), true);
		
		ObjectInformation readobj = ws.saveObjects(user2, readable, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("readobj"), new HashMap<String, String>(), SAFE_TYPE1, meta3,
				new Provenance(user), false)), getIdFactory(user2)).get(0);
		ObjectInformation readobjnometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(readable, "readobj")), false, false).get(0);
		
		ObjectInformation adminobj = ws.saveObjects(user2, adminable, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("adminobj"), new HashMap<String, String>(), SAFE_TYPE1, meta3,
				new Provenance(user), false)), getIdFactory(user2)).get(0);
		ObjectInformation adminobjnometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(adminable, "adminobj")), false, false).get(0);
		
		ObjectInformation thirdobj = ws.saveObjects(user3, thirdparty, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("thirdobj"), new HashMap<String, String>(), SAFE_TYPE1, meta,
				new Provenance(user), false)), getIdFactory(user3)).get(0);
		ObjectInformation thirdobjnometa = ws.getObjectInformation(user,
				Arrays.asList(new ObjectIdentifier(thirdparty, "thirdobj")), false, false).get(0);
		
		ObjectInformation lock = null;
		ObjectInformation locknometa = null;
		try {
			List<ObjectInformation> foo = ws.listObjects(user, Arrays.asList(lockWS),
					null, null, null, null, null, null, false, false, false, false, true, false, -1, -1);
			if (foo.size() > 1) {
				fail("found more than one object in the locked workspace, this is unexpected");
			}
			if (foo.size() == 1) {
				lock = foo.get(0);
				locknometa = ws.listObjects(user, Arrays.asList(lockWS), null, null, 
						null, null, null, null, false, false, false, false, false, false, -1, -1).get(0);
			}
		} catch (NoSuchWorkspaceException nswe) {
			//do nothing, lock workspace wasn't created yet
		}
		
		TypeDefId allType1 = new TypeDefId(SAFE_TYPE1.getType().getTypeString());
		TypeDefId allType2 = new TypeDefId(SAFE_TYPE2.getType().getTypeString());
		ArrayList<WorkspaceIdentifier> emptyWS = new ArrayList<WorkspaceIdentifier>();
		
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable), null, null, null, null, 
				null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2, hidden, deleted));
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable), null, null, null, null, 
				null, null, true, true, true, true, true, false, -1, -1),
				Arrays.asList(deleted));
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi), null, null, null, null, 
				null, null, true, true, true, true, true, false, -1, -1),
				new ArrayList<ObjectInformation>());
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable), null, null, null, null, 
				null, null, false, true, false, true, true, false, -1, -1),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2, deleted));
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable), null, null, null, null, 
				null, null, true, false, false, true, true, false, -1, -1),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2, hidden));
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable), null, null, null, null, 
				null, null, false, false, false, true, true, false, -1, -1),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2));
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable), null, null, null, null, 
				null, null, true, true, false, false, true, false, -1, -1),
				Arrays.asList(std, objstack2, type2_4, stdws2, hidden, deleted));
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable), null, null, null, null, 
				null, null, false, false, false, false, false, false, -1, -1),
				Arrays.asList(stdnometa, objstack2nometa, type2_4nometa, stdws2nometa));
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable), null, null, null, null, 
				null, null, true, true, false, true, false, false, -1, -1),
				Arrays.asList(stdnometa, objstack1nometa, objstack2nometa, type2_1nometa,
						type2_2nometa, type2_3nometa, type2_4nometa,
						stdws2nometa, hiddennometa, deletednometa));
		compareObjectInfo(ws.listObjects(user, emptyWS, allType1, null, null, null, 
				null, null, true, true, false, true, true, false, -1, -1),
				setUpListObjectsExpected(Arrays.asList(std, objstack1, objstack2,
						stdws2, hidden, deleted, readobj, adminobj, thirdobj), lock));
		compareObjectInfo(ws.listObjects(user, emptyWS, allType1, null, new ArrayList<WorkspaceUser>(), 
				null, null, null, true, true, false, true, true, false, -1, -1),
				setUpListObjectsExpected(Arrays.asList(std, objstack1, objstack2,
						stdws2, hidden, deleted, readobj, adminobj, thirdobj), lock));
		
		//exclude globally readable workspaces
		compareObjectInfo(ws.listObjects(user, emptyWS, allType1, null, null, null, 
				null, null, true, true, false, true, true, true, -1, -1),
				Arrays.asList(std, objstack1, objstack2, stdws2, hidden,
						deleted, readobj, adminobj));
		//if the globally readable workspace is explicitly listed, should ignore excludeGlobal
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable, thirdparty),
				null, null, null, null, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2, hidden, deleted, thirdobj));
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable, thirdparty),
				null, null, null, null, null, null, true, true, false, true, true, true, -1, -1),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2, hidden, deleted, thirdobj));
		
		//test user filtering
		compareObjectInfo(ws.listObjects(user, emptyWS, allType1, null,
				Arrays.asList(user, user2, user3), null, 
				null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(std, objstack1, objstack2, stdws2, hidden,
						deleted, readobj, adminobj, thirdobj));
		compareObjectInfo(ws.listObjects(user, emptyWS, allType1, null,
				Arrays.asList(user2, user3), null, 
				null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(stdws2, deleted, readobj, adminobj, thirdobj));
		compareObjectInfo(ws.listObjects(user, emptyWS, allType1, null,
				Arrays.asList(user, user3), null, 
				null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(std, hidden, objstack1, objstack2, thirdobj));
		compareObjectInfo(ws.listObjects(user, emptyWS, allType1, null,
				Arrays.asList(user3), null, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(thirdobj));
		compareObjectInfo(ws.listObjects(user, emptyWS, allType1, null,
				Arrays.asList(user), null, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(std, hidden, objstack1, objstack2));
		
		//meta filtering
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable), null, null, null,
				new HashMap<String, String>(), null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(std, objstack1, objstack2, type2_1, type2_2, type2_3, type2_4,
						stdws2, hidden, deleted));
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable), null, null, null,
				meta, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(objstack1, type2_1, stdws2));
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable), null, null, null,
				meta2, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(objstack2, type2_2, type2_3, hidden, deleted));
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable), null, null, null,
				meta3, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(type2_3, type2_4, deleted));
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable), null, null, null,
				meta, null, null, true, true, false, false, true, false, -1, -1),
				Arrays.asList(stdws2));
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi, writeable), null, null, null,
				meta2, null, null, true, true, false, false, true, false, -1, -1),
				Arrays.asList(objstack2, hidden, deleted));
		
		compareObjectInfo(ws.listObjects(user, Arrays.asList(wsi), allType1, null, null, 
				null, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(std, objstack1, objstack2));
		compareObjectInfo(ws.listObjects(user, Arrays.asList(writeable), allType1, null, null, 
				null, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(stdws2, hidden, deleted));
		compareObjectInfo(ws.listObjects(user, emptyWS, allType2, null, null, 
				null, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(type2_1, type2_2, type2_3, type2_4));
		compareObjectInfo(ws.listObjects(user, Arrays.asList(writeable), allType2, null, null, 
				null, null, null, true, true, false, true, true, false, -1, -1),
				new ArrayList<ObjectInformation>());
		compareObjectInfo(ws.listObjects(user, emptyWS, SAFE_TYPE1, null, null, 
				null, null, null, true, true, false, true, true, false, -1, -1),
				setUpListObjectsExpected(Arrays.asList(std, stdws2, hidden, deleted,
						readobj, adminobj, thirdobj), lock));
		compareObjectInfo(ws.listObjects(user, emptyWS, SAFE_TYPE1, null, null, 
				null, null, null, true, true, false, true, false, false, -1, -1),
				setUpListObjectsExpected(Arrays.asList(stdnometa, stdws2nometa, hiddennometa,
						deletednometa, readobjnometa, adminobjnometa, thirdobjnometa), locknometa));
		compareObjectInfo(ws.listObjects(user, emptyWS, SAFE_TYPE1, Permission.NONE, null, 
				null, null, null, true, true, false, true, false, false, -1, -1),
				setUpListObjectsExpected(Arrays.asList(stdnometa, stdws2nometa, hiddennometa,
						deletednometa, readobjnometa, adminobjnometa, thirdobjnometa), locknometa));
		compareObjectInfo(ws.listObjects(user, emptyWS, SAFE_TYPE1, Permission.READ, null, 
				null, null, null, true, true, false, true, false, false, -1, -1),
				setUpListObjectsExpected(Arrays.asList(stdnometa, stdws2nometa, hiddennometa,
						deletednometa, readobjnometa, adminobjnometa, thirdobjnometa), locknometa));
		compareObjectInfo(ws.listObjects(user, emptyWS, SAFE_TYPE1, Permission.WRITE, null, 
				null, null, null, true, true, false, true, false, false, -1, -1),
				Arrays.asList(stdnometa, stdws2nometa, hiddennometa, deletednometa, adminobjnometa));
		compareObjectInfo(ws.listObjects(user, emptyWS, SAFE_TYPE1, Permission.ADMIN, null, 
				null, null, null, true, true, false, true, false, false, -1, -1),
				Arrays.asList(stdnometa, adminobjnometa));
		compareObjectInfo(ws.listObjects(user, emptyWS, SAFE_TYPE1_10, null, null, 
				null, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(objstack1));
		compareObjectInfo(ws.listObjects(user, emptyWS, SAFE_TYPE1_20, null, null, 
				null, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(objstack2));
		compareObjectInfo(ws.listObjects(user, emptyWS, SAFE_TYPE2, null, null, 
				null, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(type2_1));
		compareObjectInfo(ws.listObjects(user, emptyWS, SAFE_TYPE2_10, null, null, 
				null, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(type2_2));
		compareObjectInfo(ws.listObjects(user, emptyWS, SAFE_TYPE2_20, null, null, 
				null, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(type2_3));
		compareObjectInfo(ws.listObjects(user, emptyWS, new TypeDefId(SAFE_TYPE2_20.getType(),
				SAFE_TYPE2_20.getMajorVersion()), null, null, null, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(type2_3, type2_4));
		compareObjectInfo(ws.listObjects(user, emptyWS, new TypeDefId(SAFE_TYPE2_10.getType(),
				SAFE_TYPE2_10.getMajorVersion()), null, null, null, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(type2_2));
		
		compareObjectInfo(ws.listObjects(user2, emptyWS, allType1, null, null, 
				null, null, null, true, true, false, true, true, false, -1, -1),
				setUpListObjectsExpected(Arrays.asList(stdws2, hidden, deleted, readobj,
						adminobj, thirdobj), lock));
		compareObjectInfo(ws.listObjects(user2, Arrays.asList(writeable), null, 
				null, null, null, null, null, true, true, false, true, true, false, -1, -1),
				Arrays.asList(stdws2, hidden, deleted));
		compareObjectInfo(ws.listObjects(user2, emptyWS, allType2, null, null, 
				null, null, null, true, true, false, true, true, false, -1, -1),
				new ArrayList<ObjectInformation>());
//		
		failListObjects(user, new ArrayList<WorkspaceIdentifier>(), null, null, true, true, false, true, true,
				new IllegalArgumentException("At least one filter must be specified."));
		failListObjects(user2, Arrays.asList(wsi, writeable), null, null, true, true, false, true, true,
				new WorkspaceAuthorizationException("User listObjUser2 may not read workspace listObj1"));
		failListObjects(null, Arrays.asList(wsi, writeable), null, null, true, true, false, true, true,
				new WorkspaceAuthorizationException("Anonymous users may not read workspace listObj1"));
		failListObjects(user, Arrays.asList(writeable, new WorkspaceIdentifier("listfake")), null, null, true, true, false, true, true,
				new NoSuchWorkspaceException("No workspace with name listfake exists", wsi));
		failListObjects(user, Arrays.asList(wsi, writeable), null, meta32, true, true, false, true, true,
				new IllegalArgumentException("Only one metadata spec allowed"));
		
		ws.createWorkspace(user, "listdel", false, null, null);
		ws.setWorkspaceDeleted(user, new WorkspaceIdentifier("listdel"), true);
		failListObjects(user, Arrays.asList(writeable, new WorkspaceIdentifier("listdel")), null, null, true, true, false, true, true,
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
		
		failGetObjectHistory(user, new ObjectIdentifier(wsi, "booger"),
				new NoSuchObjectException("No object with name booger exists in workspace " + wsid1));
		failGetObjectHistory(user, new ObjectIdentifier(new WorkspaceIdentifier("listObjectsfake"), "booger"),
				new InaccessibleObjectException("Object booger cannot be accessed: No workspace with name listObjectsfake exists"));
		failGetObjectHistory(user, new ObjectIdentifier(new WorkspaceIdentifier("listdel"), "booger"),
				new InaccessibleObjectException("Object booger cannot be accessed: Workspace listdel is deleted"));
		failGetObjectHistory(user2, new ObjectIdentifier(wsi, 3),
				new InaccessibleObjectException("Object 3 cannot be accessed: User listObjUser2 may not read workspace listObj1"));
		failGetObjectHistory(null, new ObjectIdentifier(wsi, 3),
				new InaccessibleObjectException("Object 3 cannot be accessed: Anonymous users may not read workspace listObj1"));
		failGetObjectHistory(user2, new ObjectIdentifier(writeable, "deleted"),
				new InaccessibleObjectException("Object 3 (name deleted) in workspace " + wsidwrite + " has been deleted"));
		
		ws.setGlobalPermission(user3, new WorkspaceIdentifier("thirdparty"), Permission.NONE);
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
		
		compareObjectInfo(ws.listObjects(u, Arrays.asList(wsi), null, null, null,
				null, null, null, false, false, false, false, true, false, -1, -1),
				Arrays.asList(o1, o2, o3, o4, o5));
		compareObjectInfo(ws.listObjects(u, Arrays.asList(wsi), null, null, null,
				null, beforeall, afterall, false, false, false, false, true, false, -1, -1),
				Arrays.asList(o1, o2, o3, o4, o5));
		compareObjectInfo(ws.listObjects(u, Arrays.asList(wsi), null, null, null,
				null, afterall, beforeall, false, false, false, false, true, false, -1, -1),
				new ArrayList<ObjectInformation>());
		compareObjectInfo(ws.listObjects(u, Arrays.asList(wsi), null, null, null,
				null, o3.getSavedDate(), o4.getSavedDate(), false, false, false, false, true, false, -1, -1),
				new ArrayList<ObjectInformation>());
		compareObjectInfo(ws.listObjects(u, Arrays.asList(wsi), null, null, null,
				null, o2.getSavedDate(), null, false, false, false, false, true, false, -1, -1),
				Arrays.asList(o3, o4, o5));
		compareObjectInfo(ws.listObjects(u, Arrays.asList(wsi), null, null, null,
				null, null, o4.getSavedDate(), false, false, false, false, true, false, -1, -1),
				Arrays.asList(o1, o2, o3));
		compareObjectInfo(ws.listObjects(u, Arrays.asList(wsi), null, null, null,
				null, o2.getSavedDate(), o4.getSavedDate(), false, false, false, false, true, false, -1, -1),
				Arrays.asList(o3));
		compareObjectInfo(ws.listObjects(u, Arrays.asList(wsi), null, null, null,
				null, new Date(o2.getSavedDate().getTime() -1), o5.getSavedDate(), false, false, false, false, true, false, -1, -1),
				Arrays.asList(o2, o3, o4));
		
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
		
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("metastuff", "meta");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("meta2", "my hovercraft is full of eels");
		
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
				new WorkspaceSaveObject(data1, SAFE_TYPE1, meta, new Provenance(user), false),
				new WorkspaceSaveObject(data1, SAFE_TYPE1, meta, new Provenance(user), false)),
				getIdFactory(user));
		ObjectInformation o1 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("o1"), data1, reftype, meta,
				p1, false)), getIdFactory(user)).get(0);
		ObjectInformation o2 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("o2"), data2, reftype, meta2,
				p2, false)), getIdFactory(user)).get(0);
		ObjectInformation o3 = ws.saveObjects(user, wsi, Arrays.asList(new WorkspaceSaveObject(
				new ObjectIDNoWSNoVer("o3"), data3, reftype, meta,
				p2, false)), getIdFactory(user)).get(0);
		ObjectIdentifier oident1 = new ObjectIdentifier(wsi, "o1");
		ObjectIdentifier oident2 = new ObjectIdentifier(wsi, 4);
		ObjectIdentifier oident3 = ObjectIdentifier.parseObjectReference("subData/o3");
		
		List<String> refs1 = Arrays.asList(wsid1 + "/1/1");
		Map<String, String> refmap1 = new HashMap<String, String>();
		refmap1.put("subData/auto1", wsid1 + "/1/1");
		List<String> refs2 = Arrays.asList(wsid1 + "/2/1");
		Map<String, String> refmap2 = new HashMap<String, String>();
		refmap2.put("subData/auto2", wsid1 + "/2/1");
		
		List<WorkspaceObjectData> got = ws.getObjectsSubSet(user, Arrays.asList(
				new SubObjectIdentifier(oident1, new ObjectPaths(
						Arrays.asList("/map/id3", "/map/id1"))),
				new SubObjectIdentifier(oident1, new ObjectPaths(
						Arrays.asList("/map/id2"))),
				new SubObjectIdentifier(oident2, new ObjectPaths(
						Arrays.asList("/array/2", "/array/0"))),
				new SubObjectIdentifier(oident3, new ObjectPaths(
						Arrays.asList("/array/2", "/array/0", "/array/3")))));
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
		compareObjectAndInfo(got.get(0), o1, p1, expdata1, refs1, refmap1);
		compareObjectAndInfo(got.get(1), o1, p1, expdata2, refs1, refmap1);
		compareObjectAndInfo(got.get(2), o2, p2, expdata3, refs2, refmap2);
		compareObjectAndInfo(got.get(3), o3, p2, expdata4, refs2, refmap2);
		
		// new test for extractor that fails on an array OOB
		failGetSubset(user, Arrays.asList(
				new SubObjectIdentifier(oident2, new ObjectPaths(
						Arrays.asList("/array/3", "/array/0")))),
				new TypedObjectExtractionException(
						"Invalid selection: no array element exists at position '3', at: /array/3"));
		
		got = ws.getObjectsSubSet(user, Arrays.asList(
				new SubObjectIdentifier(oident1, new ObjectPaths(
						Arrays.asList("/map/*/thing"))),
				new SubObjectIdentifier(oident2, new ObjectPaths(
						Arrays.asList("/array/[*]/thing")))));
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
		compareObjectAndInfo(got.get(0), o1, p1, expdata1, refs1, refmap1);
		compareObjectAndInfo(got.get(1), o2, p2, expdata2, refs2, refmap2);
		
		failGetSubset(user, Arrays.asList(
				new SubObjectIdentifier(oident1, new ObjectPaths(
						Arrays.asList("/map/id1/id/5")))),
				new TypedObjectExtractionException(
						"Invalid selection: the path given specifies fields or elements that do not exist "
						+ "because data at this location is a scalar value (i.e. string, integer, float), at: /map/id1/id"));
		failGetSubset(user2, Arrays.asList(
				new SubObjectIdentifier(oident1, new ObjectPaths(
						Arrays.asList("/map/*/thing")))),
				new InaccessibleObjectException(
						"Object o1 cannot be accessed: User subUser2 may not read workspace subData"));
		
		try {
			ws.getObjectsSubSet(user2, Arrays.asList(new SubObjectIdentifier(
					new ObjectIdentifier(wsi, 2), null)));
			fail("Able to get obj data from private workspace");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception message", ioe.getLocalizedMessage(),
					is("Object 2 cannot be accessed: User subUser2 may not read workspace subData"));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is(new ObjectIdentifier(wsi, 2)));
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
		
		ws.createWorkspace(user1, wsitar1.getName(), false, null, null);
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
		
		Map<String, String> meta1 = new HashMap<String, String>();
		meta1.put("metastuff", "meta");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("meta2", "my hovercraft is full of eels");
		

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
						mtdata, SAFE_TYPE1, null, p1, false)), getIdFactory(user1));
		
		Map<String, Object> refdata = new HashMap<String, Object>();
		
		refdata.put("refs", Arrays.asList("refstarget1/deletedref"));
		ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("delrefptr"),
						refdata, reftype, null, p1, false)), getIdFactory(user1));
		ws.setObjectsDeleted(user1, Arrays.asList(
				new ObjectIdentifier(wsisrc1, "delrefptr")), true);
		refdata.put("refs", Arrays.asList("refstarget1/unreadableref"));
		ws.saveObjects(user2, wsisrc2noaccess, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("unreadrefptr"),
						refdata, reftype, null, p1, false)), getIdFactory(user2));
		
		ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("deletedprovrefptr"),
						mtdata, SAFE_TYPE1, null,
						new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/deletedprovref"))),
						false)), getIdFactory(user1));
		ws.setObjectsDeleted(user1, Arrays.asList(
				new ObjectIdentifier(wsisrc1, "deletedprovrefptr")), true);
		ws.saveObjects(user2, wsisrc2noaccess, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("unreadableprovrefptr"),
						mtdata, SAFE_TYPE1, null,
						new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/unreadableprovref"))),
						false)), getIdFactory(user2));
		
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
						meta1, p1, false)), getIdFactory(user1));
		ws.saveObjects(user2, wsitar2, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("stk2"), mtdata, SAFE_TYPE1,
						meta1, p1, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("stk2"), mtdata, SAFE_TYPE1,
						meta2, p1, false),
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("single2"), mtdata, SAFE_TYPE1,
						meta1, p1, false)), getIdFactory(user2));
		
		refdata.put("refs", Arrays.asList("refstarget1/stk/1"));
		ObjectInformation stdref1 = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("stdref"), refdata,
						reftype, meta1,
						new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/stk/1"))), false)),
						getIdFactory(user1)).get(0);
		refdata.put("refs", Arrays.asList("refstarget1/stk/2"));
		ObjectInformation stdref2 = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("stdref"), refdata,
						reftype, meta2, new Provenance(user1), false)), 
						getIdFactory(user1)).get(0);
		refdata.put("refs", Arrays.asList("refstarget1/stk"));
		ObjectInformation hiddenref = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("hiddenref"), refdata,
						reftype, meta1, new Provenance(user1), true)),
						getIdFactory(user1)).get(0);
		refdata.put("refs", Arrays.asList("refstarget2/stk2"));
		@SuppressWarnings("unused")
		ObjectInformation delref = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("delref"), refdata,
						reftype, meta1,
						new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/stk/2"))), true)),
						getIdFactory(user1)).get(0);
		ws.setObjectsDeleted(user1, Arrays.asList(new ObjectIdentifier(wsisrc1, "delref")), true);
		
		refdata.put("refs", Arrays.asList("refstarget1/single"));
		ObjectInformation readable = ws.saveObjects(user2, wsisrc2, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("readable"), refdata,
						reftype, meta2, new Provenance(user2), true)),
						getIdFactory(user2)).get(0);
		
		refdata.put("refs", Arrays.asList("refstarget2/stk2/2"));
		@SuppressWarnings("unused")
		ObjectInformation unreadable = ws.saveObjects(user2, wsisrc2noaccess, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("unreadable"), refdata,
						reftype, meta1, new Provenance(user2), true)),
						getIdFactory(user2)).get(0);
		
		refdata.put("refs", Arrays.asList("refstarget2/single2/1"));
		@SuppressWarnings("unused")
		ObjectInformation wsdeletedreadable1 = ws.saveObjects(user1, wsisrcdel1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("wsdeletedreadable1"), refdata,
						reftype, meta2, new Provenance(user1), false)),
						getIdFactory(user1)).get(0);
		ws.setWorkspaceDeleted(user1, wsisrcdel1, true);
		
		refdata.put("refs", Arrays.asList("refstarget2/stk2/1"));
		ObjectInformation globalrd = ws.saveObjects(user2, wsisrc2gl, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("globalrd"), refdata,
						reftype, meta1, new Provenance(user2).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/single/1"))), false)),
						getIdFactory(user2)).get(0);
		
		List<ObjectIdentifier> objs = Arrays.asList(
				new ObjectIdentifier(wsitar1, "stk"),
				new ObjectIdentifier(wsitar1, "stk", 2),
				new ObjectIdentifier(wsitar1, "stk", 1));
		assertThat("got correct refs", ws.getReferencingObjects(user1, objs),
				is(Arrays.asList(
						oiset(stdref2, hiddenref),
						oiset(stdref2, hiddenref),
						oiset(stdref1))));
		assertThat("got correct refcounts", ws.getReferencingObjectCounts(user1, objs),
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
		assertThat("got correct refcounts", ws.getReferencingObjectCounts(user1, objs),
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
		assertThat("got correct refcounts", ws.getReferencingObjectCounts(user1, objs),
				is(Arrays.asList(2, 2, 1, 1)));
		
		
		ObjectInformation pstdref1 = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("pstdref"), mtdata,
						SAFE_TYPE1, meta1,
						new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/stk/1"))), false)),
						getIdFactory(user1)).get(0);
		ObjectInformation pstdref2 = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("pstdref"), mtdata,
						SAFE_TYPE1, meta2, new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/stk/2"))), false)),
						getIdFactory(user1)).get(0);
		ObjectInformation phiddenref = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("phiddenref"), mtdata,
						SAFE_TYPE1, meta1, new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/stk"))), true)),
						getIdFactory(user1)).get(0);
		@SuppressWarnings("unused")
		ObjectInformation pdelref = ws.saveObjects(user1, wsisrc1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("pdelref"), mtdata,
						SAFE_TYPE1, meta1,
						new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget2/stk2"))), true)),
						getIdFactory(user1)).get(0);
		ws.setObjectsDeleted(user1, Arrays.asList(new ObjectIdentifier(wsisrc1, "pdelref")), true);
		
		ObjectInformation preadable = ws.saveObjects(user2, wsisrc2, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("preadable"), mtdata,
						SAFE_TYPE1, meta2, new Provenance(user2).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget1/single"))), true)),
						getIdFactory(user2)).get(0);
		
		@SuppressWarnings("unused")
		ObjectInformation punreadable = ws.saveObjects(user2, wsisrc2noaccess, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("punreadable"), mtdata,
						SAFE_TYPE1, meta1, new Provenance(user2).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget2/stk2/2"))), true)),
						getIdFactory(user2)).get(0);
		
		ws.setWorkspaceDeleted(user1, wsisrcdel1, false);
		@SuppressWarnings("unused")
		ObjectInformation pwsdeletedreadable1 = ws.saveObjects(user1, wsisrcdel1, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("pwsdeletedreadable1"), mtdata,
						SAFE_TYPE1, meta2, new Provenance(user1).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget2/single2/1"))), false)),
						getIdFactory(user1)).get(0);
		ws.setWorkspaceDeleted(user1, wsisrcdel1, true);
		
		ObjectInformation pglobalrd = ws.saveObjects(user2, wsisrc2gl, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("pglobalrd"), mtdata,
						SAFE_TYPE1, meta1, new Provenance(user2).addAction(new ProvenanceAction()
						.withWorkspaceObjects(Arrays.asList("refstarget2/stk2/1"))), false)),
						getIdFactory(user2)).get(0);
		
		objs = Arrays.asList(
				new ObjectIdentifier(wsitar1, "stk"),
				new ObjectIdentifier(wsitar1, "stk", 2),
				new ObjectIdentifier(wsitar1, "stk", 1));
		assertThat("got correct refs", ws.getReferencingObjects(user1, objs),
				is(Arrays.asList(
						oiset(stdref2, hiddenref, pstdref2, phiddenref),
						oiset(stdref2, hiddenref, pstdref2, phiddenref),
						oiset(stdref1, pstdref1))));
		assertThat("got correct refcounts", ws.getReferencingObjectCounts(user1, objs),
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
		assertThat("got correct refcounts", ws.getReferencingObjectCounts(user1, objs),
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
		assertThat("got correct refcounts", ws.getReferencingObjectCounts(user1, objs),
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
			ws.getReferencingObjectCounts(user2, Arrays.asList(
					new ObjectIdentifier(wsisrc1, 1)));
			fail("Able to get ref obj count from private workspace");
		} catch (InaccessibleObjectException ioe) {
			assertThat("correct exception message", ioe.getLocalizedMessage(),
					is("Object 1 cannot be accessed: User refUser2 may not read workspace refssource1"));
			assertThat("correct object returned", ioe.getInaccessibleObject(),
					is(new ObjectIdentifier(wsisrc1, 1)));
		}
		
		ws.setGlobalPermission(user2, wsisrc2gl, Permission.NONE);
	}
	
	@Test
	public void getReferencedObjects() throws Exception {
		WorkspaceUser user1 = new WorkspaceUser("refedUser");
		WorkspaceUser user2 = new WorkspaceUser("refedUser2");
		WorkspaceIdentifier wsiacc1 = new WorkspaceIdentifier("refedaccessible");
		WorkspaceIdentifier wsiacc2 = new WorkspaceIdentifier("refedaccessible2");
		WorkspaceIdentifier wsiun1 = new WorkspaceIdentifier("refedunacc");
		WorkspaceIdentifier wsiun2 = new WorkspaceIdentifier("refedunacc2");
		WorkspaceIdentifier wsidel = new WorkspaceIdentifier("refeddel");
		
		ws.createWorkspace(user1, wsiacc1.getName(), false, null, null);
		ws.setPermissions(user1, wsiacc1, Arrays.asList(user2), Permission.WRITE);
		ws.createWorkspace(user2, wsiacc2.getName(), true, null, null);
		long wsidun1 = ws.createWorkspace(user2, wsiun1.getName(), false, null, null).getId();
		long wsidun2 = ws.createWorkspace(user2, wsiun2.getName(), false, null, null).getId();
		ws.createWorkspace(user2, wsidel.getName(), false, null, null);
		
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
		
		ObjectInformation leaf1 = saveObject(user2, wsiun1, meta1, data1, SAFE_TYPE1, "leaf1", new Provenance(user2));
		ObjectIdentifier leaf1oi = new ObjectIdentifier(wsiun1, "leaf1");
		failGetObjects(user1, Arrays.asList(leaf1oi), new InaccessibleObjectException(
				"Object leaf1 cannot be accessed: User refedUser may not read workspace refedunacc"));
		ObjectInformation leaf2 = saveObject(user2, wsiun2, meta2, data2, SAFE_TYPE1, "leaf2", new Provenance(user2));
		ObjectIdentifier leaf2oi = new ObjectIdentifier(wsiun2, "leaf2");
		failGetObjects(user1, Arrays.asList(leaf2oi), new InaccessibleObjectException(
				"Object leaf2 cannot be accessed: User refedUser may not read workspace refedunacc2"));
		saveObject(user2, wsiun2, meta2, data2, SAFE_TYPE1, "unlinked", new Provenance(user2));
		ObjectIdentifier unlinkedoi = new ObjectIdentifier(wsiun2, "unlinked");
		failGetObjects(user1, Arrays.asList(unlinkedoi), new InaccessibleObjectException(
				"Object unlinked cannot be accessed: User refedUser may not read workspace refedunacc2"));
		
		final String leaf1r = "refedunacc/leaf1";
		saveObject(user2, wsiacc1, MT_META, makeRefData(leaf1r),reftype,
				"simpleref", new Provenance(user2));
		final String leaf2r = "refedunacc2/leaf2";
		saveObject(user2, wsiacc2, MT_META, makeRefData(leaf2r),reftype,
				"simpleref2", new Provenance(user2));
		
		saveObject(user2, wsiacc1, MT_META, mtdata, SAFE_TYPE1, "provref", new Provenance(user2)
				.addAction(new ProvenanceAction().withWorkspaceObjects(
						Arrays.asList(leaf1r))));
		saveObject(user2, wsiacc2, MT_META, mtdata, SAFE_TYPE1, "provref2", new Provenance(user2)
				.addAction(new ProvenanceAction().withWorkspaceObjects(
						Arrays.asList(leaf2r))));
		
		final HashMap<String, String> mtmap = new HashMap<String, String>();
		final LinkedList<String> mtlist = new LinkedList<String>();
		checkReferencedObject(user1, new ObjectChain(new ObjectIdentifier(wsiacc1, "simpleref"),
				Arrays.asList(leaf1oi)), leaf1, new Provenance(user2), data1, mtlist, mtmap);
		checkReferencedObject(user1, new ObjectChain(new ObjectIdentifier(wsiacc2, "simpleref2"),
				Arrays.asList(leaf2oi)), leaf2, new Provenance(user2), data2, mtlist, mtmap);
		checkReferencedObject(user1, new ObjectChain(new ObjectIdentifier(wsiacc1, "provref"),
				Arrays.asList(leaf1oi)), leaf1, new Provenance(user2), data1, mtlist, mtmap);
		checkReferencedObject(user1, new ObjectChain(new ObjectIdentifier(wsiacc2, "provref2"),
				Arrays.asList(leaf2oi)), leaf2, new Provenance(user2), data2, mtlist, mtmap);
		
		failGetReferencedObjects(user1, Arrays.asList(new ObjectChain(new ObjectIdentifier(wsiacc2, "simpleref2"),
				Arrays.asList(leaf1oi))), new NoSuchReferenceException(
				"The object simpleref2 in workspace refedaccessible2 does not contain the reference " +
				wsidun1 + "/1/1", null, null));
		
		ObjectInformation del1 = saveObject(user2, wsiun1, meta2,
				makeRefData(leaf1r, leaf2r), reftype, "del1", new Provenance(user2));
		ObjectIdentifier del1oi = new ObjectIdentifier(wsiun1, "del1");
		final Provenance p = new Provenance(user2).addAction(new ProvenanceAction()
				.withWorkspaceObjects(Arrays.asList(leaf1r, leaf2r)));
		ObjectInformation del2 = saveObject(user2, wsiun2, meta1, makeRefData(),
				reftype, "del2", p);
		ObjectIdentifier del2oi = new ObjectIdentifier(wsiun2, "del2");
		saveObject(user2, wsidel, meta1, makeRefData(leaf2r), reftype, "delws", new Provenance(user2));
		ObjectIdentifier delwsoi = new ObjectIdentifier(wsidel, "delws");
		
		saveObject(user2, wsiacc1, MT_META, makeRefData("refedunacc/del1", "refedunacc2/del2"),
				reftype, "delptr12", new Provenance(user2));
		ObjectIdentifier delptr12oi = new ObjectIdentifier(wsiacc1, "delptr12");
		saveObject(user2, wsiacc2, MT_META, makeRefData("refedunacc2/del2"),
				reftype, "delptr2", new Provenance(user2));
		ObjectIdentifier delptr2oi = new ObjectIdentifier(wsiacc2, "delptr2");
		saveObject(user2, wsiacc2, MT_META, makeRefData("refeddel/delws"),
				reftype, "delptrws", new Provenance(user2));
		ObjectIdentifier delptrwsoi = new ObjectIdentifier(wsiacc2, "delptrws");
		ws.setObjectsDeleted(user2, Arrays.asList(del1oi, del2oi), true);
		ws.setWorkspaceDeleted(user2, wsidel, true);
		
		List<WorkspaceObjectData> lwod = ws.getReferencedObjects(user1, Arrays.asList(
				new ObjectChain(delptr12oi, Arrays.asList(del1oi, leaf1oi)),
				new ObjectChain(delptr12oi, Arrays.asList(del1oi, leaf2oi)),
				new ObjectChain(delptr12oi, Arrays.asList(del2oi, leaf1oi)),
				new ObjectChain(delptrwsoi, Arrays.asList(delwsoi, leaf2oi)),
				new ObjectChain(delptr12oi, Arrays.asList(del2oi, leaf2oi)),
				new ObjectChain(delptr2oi, Arrays.asList(del2oi, leaf1oi)),
				new ObjectChain(delptr2oi, Arrays.asList(del2oi, leaf2oi))
				));
		assertThat("correct list size", lwod.size(), is(7));
		compareObjectAndInfo(lwod.get(0), leaf1, new Provenance(user2), data1, mtlist, mtmap);
		compareObjectAndInfo(lwod.get(1), leaf2, new Provenance(user2), data2, mtlist, mtmap);
		compareObjectAndInfo(lwod.get(2), leaf1, new Provenance(user2), data1, mtlist, mtmap);
		compareObjectAndInfo(lwod.get(3), leaf2, new Provenance(user2), data2, mtlist, mtmap);
		compareObjectAndInfo(lwod.get(4), leaf2, new Provenance(user2), data2, mtlist, mtmap);
		compareObjectAndInfo(lwod.get(5), leaf1, new Provenance(user2), data1, mtlist, mtmap);
		compareObjectAndInfo(lwod.get(6), leaf2, new Provenance(user2), data2, mtlist, mtmap);
		
		checkReferencedObject(user1, new ObjectChain(delptr12oi, Arrays.asList(del1oi)),
				del1, new Provenance(user2), makeRefData(wsidun1 + "/1/1", wsidun2 + "/1/1"),
				Arrays.asList(wsidun1 + "/1/1", wsidun2 + "/1/1"),  mtmap);
		Map<String, String> provmap = new HashMap<String, String>();
		provmap.put(leaf1r, wsidun1 + "/1/1");
		provmap.put(leaf2r, wsidun2 + "/1/1");
		checkReferencedObject(user1, new ObjectChain(delptr12oi, Arrays.asList(del2oi)),
				del2, p, makeRefData(), mtlist, provmap);
		
		failGetReferencedObjects(user1, Arrays.asList(new ObjectChain(delptr2oi,
				Arrays.asList(del1oi, leaf1oi))), new NoSuchReferenceException(
				"The object delptr2 in workspace refedaccessible2 does not contain the reference " +
				wsidun1 + "/2/1", null, null));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectChain(delptr12oi,
				Arrays.asList(del1oi, unlinkedoi))), new NoSuchReferenceException(
				"The object del1 in workspace refedunacc does not contain the reference " +
				wsidun2 + "/2/1", null, null));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectChain(delptr12oi,
				Arrays.asList(del1oi, new ObjectIdentifier(wsiun1, "leaf2")))), new NoSuchObjectException(
				"No object with name leaf2 exists in workspace " + wsidun1, null, null));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectChain(delptr12oi,
				Arrays.asList(del1oi, new ObjectIdentifier(wsiun1, "leaf1", 2)))), new NoSuchObjectException(
				"No object with id 1 (name leaf1) and version 2 exists in workspace " + wsidun1, null, null));
		
		failGetReferencedObjects(user2, new ArrayList<ObjectChain>(),
				new IllegalArgumentException("No object identifiers provided"));
		failGetReferencedObjects(user2, Arrays.asList(new ObjectChain(new ObjectIdentifier(wsiun1, "leaf3"),
				Arrays.asList(new ObjectIdentifier(wsiun1, "leaf1")))),
				new InaccessibleObjectException("No object with name leaf3 exists in workspace " + wsidun1));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectChain(new ObjectIdentifier(new WorkspaceIdentifier("fakefakefake"), "leaf1"),
				Arrays.asList(new ObjectIdentifier(wsiun1, "leaf1")))),
				new InaccessibleObjectException("Object leaf1 cannot be accessed: No workspace with name fakefakefake exists"));
		failGetReferencedObjects(user1, Arrays.asList(new ObjectChain(new ObjectIdentifier(wsiun1, "leaf1"),
				Arrays.asList(new ObjectIdentifier(wsiun1, "leaf1")))),
				new InaccessibleObjectException("Object leaf1 cannot be accessed: User refedUser may not read workspace refedunacc"));
		failGetReferencedObjects(null, Arrays.asList(new ObjectChain(new ObjectIdentifier(wsiun1, "leaf1"),
				Arrays.asList(new ObjectIdentifier(wsiun1, "leaf1")))),
				new InaccessibleObjectException("Object leaf1 cannot be accessed: Anonymous users may not read workspace refedunacc"));
		ws.setObjectsDeleted(user2, Arrays.asList(new ObjectIdentifier(wsiun1, "leaf1")), true);
		failGetReferencedObjects(user2, Arrays.asList(new ObjectChain(new ObjectIdentifier(wsiun1, "leaf1"),
				Arrays.asList(new ObjectIdentifier(wsiun1, "leaf1")))),
				new InaccessibleObjectException("Object 1 (name leaf1) in workspace " + 
				wsidun1 + " has been deleted"));
		ws.setObjectsDeleted(user2, Arrays.asList(new ObjectIdentifier(wsiun1, "leaf1")), false);
		ws.setWorkspaceDeleted(user2, wsiun1, true);
		failGetReferencedObjects(user2, Arrays.asList(new ObjectChain(new ObjectIdentifier(wsiun1, "leaf1"),
				Arrays.asList(new ObjectIdentifier(wsiun1, "leaf1")))),
				new InaccessibleObjectException("Object leaf1 cannot be accessed: Workspace refedunacc is deleted"));
		
		ws.setGlobalPermission(user2, wsiacc2, Permission.NONE);
	}

	@Test
	public void objectChain() throws Exception {
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("foo");
		ObjectIdentifier oi = new ObjectIdentifier(wsi, "thing");
		failCreateObjectChain(null, new ArrayList<ObjectIdentifier>(),
				new IllegalArgumentException("Neither head nor chain can be null"));
		failCreateObjectChain(oi, null,
				new IllegalArgumentException("Neither head nor chain can be null"));
		failCreateObjectChain(oi, new ArrayList<ObjectIdentifier>(),
				new IllegalArgumentException("Chain cannot be empty"));
		failCreateObjectChain(oi, Arrays.asList(oi, null, oi),
				new IllegalArgumentException("Nulls are not allowed in reference chains"));
	}
	
	@Test
	public void grantRemoveOwnership() throws Exception {
		WorkspaceUser user = new WorkspaceUser("foo");
		String moduleName = "SharedModule";
		ws.requestModuleRegistration(user, moduleName);
		ws.resolveModuleRegistration(moduleName, true);
		ws.compileNewTypeSpec(user, "module " + moduleName + " {typedef int MainType;};", 
				Arrays.asList("MainType"), null, null, false, null);
		ws.releaseTypes(user, moduleName);
		WorkspaceUser user2 = new WorkspaceUser("bar");
		try {
			ws.compileNewTypeSpec(user2, "module " + moduleName + " {typedef string MainType;};", 
					Collections.<String>emptyList(), null, null, false, null);
			Assert.fail();
		} catch (NoSuchPrivilegeException ex) {
			Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("not in list of owners"));
		}
		ws.grantModuleOwnership(moduleName, user2.getUser(), false, user, false);
		ws.compileNewTypeSpec(user2, "module " + moduleName + " {typedef string MainType;};", 
				Collections.<String>emptyList(), null, null, false, null);
		WorkspaceUser user3 = new WorkspaceUser("baz");
		try {
			ws.grantModuleOwnership(moduleName, user3.getUser(), false, user2, false);
			Assert.fail();
		} catch (NoSuchPrivilegeException ex) {
			Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("can not change privileges"));
		}
		ws.grantModuleOwnership(moduleName, user2.getUser(), true, user, false);
		ws.grantModuleOwnership(moduleName, user3.getUser(), false, user2, false);
		ws.removeModuleOwnership(moduleName, user3.getUser(), user2, false);
		ws.removeModuleOwnership(moduleName, user2.getUser(), user, false);
		try {
			ws.compileNewTypeSpec(user2, "module " + moduleName + " {typedef float MainType;};", 
					Collections.<String>emptyList(), null, null, false, null);
			Assert.fail();
		} catch (NoSuchPrivilegeException ex) {
			Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("not in list of owners"));
		}
	}
	
	@Test
	public void removeTypeTest() throws Exception {
		WorkspaceUser user = new WorkspaceUser("foo");
		String moduleName = "MyMod3";
		ws.requestModuleRegistration(user, moduleName);
		ws.resolveModuleRegistration(moduleName, true);
		ws.compileNewTypeSpec(user, "module " + moduleName + " {" +
				"typedef structure {string foo; list<int> bar; int baz;} AType; " +
				"typedef structure {string whooo;} BType;};", 
				Arrays.asList("AType", "BType"), null, null, false, null);
		ws.compileTypeSpec(user, moduleName, Collections.<String>emptyList(),
				Arrays.asList("BType"), Collections.<String, Long>emptyMap(), false);
		List<Long> vers = ws.getModuleVersions(moduleName, user);
		Collections.sort(vers);
		Assert.assertEquals(2, vers.size());
		Assert.assertEquals(2, ws.getModuleInfo(user, new ModuleDefId(moduleName, vers.get(0))).getTypes().size());
		Assert.assertEquals(1, ws.getModuleInfo(user, new ModuleDefId(moduleName, vers.get(1))).getTypes().size());
		Assert.assertEquals(Arrays.asList(vers.get(0)), ws.getModuleVersions(new TypeDefId(moduleName + ".BType", "0.1"), user));
		ws.releaseTypes(user, moduleName);
		Assert.assertEquals(1, ws.getModuleVersions(new TypeDefId(moduleName + ".AType"), null).size());
		Assert.assertEquals(moduleName + ".AType-1.0", ws.getTypeInfo(moduleName + ".AType", false, null).getTypeDefId());
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
		List<WorkspaceSaveObject> objects = Arrays.asList(
				new WorkspaceSaveObject(savedata, SAFE_TYPE1, null, p, false));
		List<ObjectInformation> objinfo = ws.saveObjects(user, wsi, objects,
				getIdFactory(user));
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
				new WorkspaceSaveObject(data, SAFE_TYPE1, null,
				new Provenance(user), false)), new IllegalArgumentException(
						"Object #1 data size 21 exceeds limit of 20"));
		ws.setResourceConfig(oldcfg);
	}
	
	@Test
	public void maxReturnedObjectSize() throws Exception {

		TypeDefId reftype = new TypeDefId(new TypeDefName("CopyRev", "RefType"), 1, 0);
		WorkspaceUser user = new WorkspaceUser("MROSuser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("maxReturnedObjectSize");
		ws.createWorkspace(user, wsi.getIdentifierString(), false, null, null);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("fo", "90");
		data.put("ba", "3");
		saveObject(user, wsi, null, data, SAFE_TYPE1, "foo", new Provenance(user));
		ObjectIdentifier oi1 = new ObjectIdentifier(wsi, "foo", 1);
		saveObject(user, wsi, null, data, SAFE_TYPE1, "foo2", new Provenance(user));
		ObjectIdentifier oi2 = new ObjectIdentifier(wsi, "foo2", 1);
		List<ObjectIdentifier> oi1l = Arrays.asList(oi1);
		List<ObjectIdentifier> oi2l = Arrays.asList(oi2);
		Map<String, Object> refdata = new HashMap<String, Object>();
		refdata.put("refs", Arrays.asList(wsi.getName() + "/foo/1"));
		saveObject(user, wsi, null, refdata, reftype, "ref", new Provenance(user));
		refdata.put("refs", Arrays.asList(wsi.getName() + "/foo2/1"));
		saveObject(user, wsi, null, refdata, reftype, "ref2", new Provenance(user));
		ObjectIdentifier ref = new ObjectIdentifier(wsi, "ref", 1);
		ObjectIdentifier ref2 = new ObjectIdentifier(wsi, "ref2", 1);
		List<ObjectChain> refchain = Arrays.asList(new ObjectChain(ref, oi1l));
		List<ObjectChain> refchain2 = Arrays.asList(new ObjectChain(ref, oi1l),
				new ObjectChain(ref2, oi2l));
		
		ResourceUsageConfiguration oldcfg = ws.getResourceConfig();
		ResourceUsageConfigurationBuilder build =
				new ResourceUsageConfigurationBuilder(
						oldcfg).withMaxObjectSize(1);
		
		ws.setResourceConfig(build.withMaxReturnedDataSize(20).build());
		List<SubObjectIdentifier> ois1l = Arrays.asList(new SubObjectIdentifier(oi1,
				new ObjectPaths(Arrays.asList("/fo"))));
		List<SubObjectIdentifier> ois1lmt = Arrays.asList(new SubObjectIdentifier(oi1,
				new ObjectPaths(new ArrayList<String>())));
		successGetObjects(user, oi1l);
		ws.getObjectsSubSet(user, ois1l);
		ws.getObjectsSubSet(user, ois1lmt);
		ws.getReferencedObjects(user, refchain);
		ws.setResourceConfig(build.withMaxReturnedDataSize(19).build());
		String errstr = "Too much data requested from the workspace at once; data requested " + 
				"including potential subsets is %sB which  exceeds maximum of %s.";
		IllegalArgumentException err = new IllegalArgumentException(String.format(errstr, 20, 19));
		failGetObjects(user, oi1l, err, true);
		failGetSubset(user, ois1l, err);
		failGetSubset(user, ois1lmt, err);
		failGetReferencedObjects(user, refchain, err);
		
		ws.setResourceConfig(build.withMaxReturnedDataSize(40).build());
		List<ObjectIdentifier> two = Arrays.asList(oi1, oi2);
		List<SubObjectIdentifier> ois1l2 = Arrays.asList(
				new SubObjectIdentifier(oi1, new ObjectPaths(Arrays.asList("/fo"))),
				new SubObjectIdentifier(oi1, new ObjectPaths(Arrays.asList("/ba"))));
		List<SubObjectIdentifier> bothoi = Arrays.asList(
				new SubObjectIdentifier(oi1, new ObjectPaths(Arrays.asList("/fo"))),
				new SubObjectIdentifier(oi2, new ObjectPaths(Arrays.asList("/ba"))));
		successGetObjects(user, two);
		ws.getObjectsSubSet(user, ois1l2);
		ws.getObjectsSubSet(user, bothoi);
		ws.getReferencedObjects(user, refchain2);
		ws.setResourceConfig(build.withMaxReturnedDataSize(39).build());
		err = new IllegalArgumentException(String.format(errstr, 40, 39));
		failGetObjects(user, two, err, true);
		failGetSubset(user, ois1l2, err);
		failGetSubset(user, bothoi, err);
		failGetReferencedObjects(user, refchain2, err);
		
		List<SubObjectIdentifier> all = new LinkedList<SubObjectIdentifier>();
		all.addAll(ois1l2);
		all.addAll(bothoi);
		ws.setResourceConfig(build.withMaxReturnedDataSize(60).build());
		ws.getObjectsSubSet(user, all);
		ws.setResourceConfig(build.withMaxReturnedDataSize(59).build());
		err = new IllegalArgumentException(String.format(errstr, 60, 59));
		failGetSubset(user, all, err);
		
		ws.setResourceConfig(oldcfg);
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
		
		objs.add(new WorkspaceSaveObject(data1, SAFE_TYPE1, null, p, false));
		
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
		ws.saveObjects(user, wsi, objs, getIdFactory(user));
		assertThat("created no temp files on save", filesCreated[0], is(0));
		ws.setResourceConfig(build.withMaxReturnedDataMemoryUsage(13).build());
		ObjectIdentifier oi = new ObjectIdentifier(wsi, 1);
		ws.getObjects(user, Arrays.asList(oi));
		assertThat("created no temp files on get", filesCreated[0], is(0));
		ws.getObjectsSubSet(user, Arrays.asList(new SubObjectIdentifier(oi,
				new ObjectPaths(Arrays.asList("z"))))).get(0).getDataAsTokens().destroy();
		assertThat("created 1 temp file on get subdata", filesCreated[0], is(1));
		JSONRPCLayerTester.assertNoTempFilesExist(ws.getTempFilesManager());
		
		//files go to disk except for small subdata
		filesCreated[0] = 0;
		ws.setResourceConfig(build.withMaxIncomingDataMemoryUsage(12).build());
		ws.saveObjects(user, wsi, objs, getIdFactory(user));
		assertThat("created temp files on save", filesCreated[0], is(2));
		JSONRPCLayerTester.assertNoTempFilesExist(ws.getTempFilesManager());
		
		filesCreated[0] = 0;
		ws.setResourceConfig(build.withMaxReturnedDataMemoryUsage(12).build());
		oi = new ObjectIdentifier(wsi, 2);
		ws.getObjects(user, Arrays.asList(oi)).get(0).getDataAsTokens().destroy();
		assertThat("created 1 temp files on get", filesCreated[0], is(1));
		JSONRPCLayerTester.assertNoTempFilesExist(ws.getTempFilesManager());
		
		filesCreated[0] = 0;
		ws.getObjectsSubSet(user, Arrays.asList(new SubObjectIdentifier(oi,
				new ObjectPaths(Arrays.asList("z"))))).get(0).getDataAsTokens().destroy();
		assertThat("created 1 temp files on get subdata part object", filesCreated[0], is(1));
		JSONRPCLayerTester.assertNoTempFilesExist(ws.getTempFilesManager());
		
		filesCreated[0] = 0;
		ws.getObjectsSubSet(user, Arrays.asList(new SubObjectIdentifier(oi,
				new ObjectPaths(Arrays.asList("z", "y"))))).get(0).getDataAsTokens().destroy();
		assertThat("created 2 temp files on get subdata full object", filesCreated[0], is(2));
		JSONRPCLayerTester.assertNoTempFilesExist(ws.getTempFilesManager());
		
		// test with multiple objects
		Map<String, Object> data2 = new LinkedHashMap<String, Object>();
		data2.put("w", 1);
		data2.put("f", 2);
		//already sorted so no temp files will be created
		Map<String, Object> data3 = new LinkedHashMap<String, Object>();
		data3.put("x", 1);
		data3.put("z", 2);
		objs.add(new WorkspaceSaveObject(data2, SAFE_TYPE1, null, p, false));
		objs.add(new WorkspaceSaveObject(data3, SAFE_TYPE1, null, p, false));

		//multiple objects in memory
		filesCreated[0] = 0;
		ws.setResourceConfig(build.withMaxIncomingDataMemoryUsage(39).build());
		ws.saveObjects(user, wsi, objs, getIdFactory(user));
		assertThat("created no temp files on save", filesCreated[0], is(0));
		
		ws.setResourceConfig(build.withMaxReturnedDataMemoryUsage(39).build());
		List<ObjectIdentifier> ois = Arrays.asList(new ObjectIdentifier(wsi, 3),
				new ObjectIdentifier(wsi, 4), new ObjectIdentifier(wsi, 5));
		for (WorkspaceObjectData wod: ws.getObjects(user, ois)) {
			wod.getDataAsTokens().destroy();
		}
		assertThat("created no temp files on get", filesCreated[0], is(0));
		JSONRPCLayerTester.assertNoTempFilesExist(ws.getTempFilesManager());
		
		//multiple objects to file
		ws.setResourceConfig(build.withMaxIncomingDataMemoryUsage(38).build());
		filesCreated[0] = 0;
		ws.saveObjects(user, wsi, objs, getIdFactory(user));
		//two files per data - 1 for relabeling, 1 for sort
		assertThat("created temp files on save", filesCreated[0], is(4));
		JSONRPCLayerTester.assertNoTempFilesExist(ws.getTempFilesManager());
		
		filesCreated[0] = 0;
		ws.setResourceConfig(build.withMaxReturnedDataMemoryUsage(38).build());
		for (WorkspaceObjectData wod: ws.getObjects(user, ois)) {
			wod.getDataAsTokens().destroy();
		}
		assertThat("created 1 temp files on get", filesCreated[0], is(1));
		JSONRPCLayerTester.assertNoTempFilesExist(ws.getTempFilesManager());
		
		filesCreated[0] = 0;
		ws.setResourceConfig(build.withMaxReturnedDataMemoryUsage(25).build());
		for (WorkspaceObjectData wod: ws.getObjects(user, ois)) {
			wod.getDataAsTokens().destroy();
		}
		assertThat("created 2 temp files on get", filesCreated[0], is(2));
		JSONRPCLayerTester.assertNoTempFilesExist(ws.getTempFilesManager());
		
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
		objs.add(new WorkspaceSaveObject(data1, SAFE_TYPE1, null, p, false));
		ws.saveObjects(user, wsi, objs, getIdFactory(user));
		WorkspaceObjectData o = ws.getObjects(
				user, Arrays.asList(new ObjectIdentifier(wsi, 1))).get(0);
		String data = IOUtils.toString(o.getDataAsTokens().getJSON());
		assertThat("data is sorted", data, is(expected));
		assertThat("data marked as sorted", o.getDataAsTokens().isSorted(),
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
		objs.add(new WorkspaceSaveObject(new JsonTokenStream(safejson), SAFE_TYPE1, null, p, false));
		objs.add(new WorkspaceSaveObject(new JsonTokenStream(json), SAFE_TYPE1, null, p, false));
		
		ResourceUsageConfiguration oldcfg = ws.getResourceConfig();
		ResourceUsageConfigurationBuilder build =
				new ResourceUsageConfigurationBuilder(oldcfg)
				.withMaxIncomingDataMemoryUsage(1);
		int maxmem = 8 + 64 + 8 + 64;
		ws.setResourceConfig(build.withMaxRelabelAndSortMemoryUsage(maxmem).build());
		ws.saveObjects(user, wsi, objs, getIdFactory(user));
		
		ws.setResourceConfig(build.withMaxRelabelAndSortMemoryUsage(maxmem - 1).build());
		try {
			ws.saveObjects(user, wsi, objs, getIdFactory(user));
			fail("sorted w/ too little mem");
		} catch (TypedObjectValidationException tove) {
			assertThat("got correct exception", tove.getMessage(),
					is("Object #2: Memory necessary for sorting map keys exceeds the limit " + 
							(maxmem - 1) + " bytes at /"));
		}
		ws.setResourceConfig(oldcfg);
	}
}
