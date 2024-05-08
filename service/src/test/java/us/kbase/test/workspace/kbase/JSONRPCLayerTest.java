package us.kbase.test.workspace.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.test.common.TestCommon.assertExceptionCorrect;
import static us.kbase.test.common.TestCommon.list;
import static us.kbase.test.common.TestCommon.set;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple12;
import us.kbase.common.service.Tuple7;
import us.kbase.common.service.Tuple9;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.workspace.AlterAdminObjectMetadataParams;
import us.kbase.workspace.AlterWorkspaceMetadataParams;
import us.kbase.workspace.CloneWorkspaceParams;
import us.kbase.workspace.CopyObjectParams;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.ExternalDataUnit;
import us.kbase.workspace.GetModuleInfoParams;
import us.kbase.workspace.GetNamesByPrefixParams;
import us.kbase.workspace.GetNamesByPrefixResults;
import us.kbase.workspace.GetObjectInfo3Params;
import us.kbase.workspace.GetObjectInfo3Results;
import us.kbase.workspace.GetObjects2Params;
import us.kbase.workspace.GetObjects2Results;
import us.kbase.workspace.GetPermissionsMassParams;
import us.kbase.workspace.ListAllTypesParams;
import us.kbase.workspace.ListModuleVersionsParams;
import us.kbase.workspace.ListModulesParams;
import us.kbase.workspace.ListObjectsParams;
import us.kbase.workspace.ListWorkspaceIDsParams;
import us.kbase.workspace.ListWorkspaceIDsResults;
import us.kbase.workspace.ListWorkspaceInfoParams;
import us.kbase.workspace.ModuleVersions;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectMetadataUpdate;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ObjectSpecification;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.RegisterTypespecCopyParams;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.RenameObjectParams;
import us.kbase.workspace.RenameWorkspaceParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.SetWorkspaceDescriptionParams;
import us.kbase.workspace.SubAction;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.WorkspacePermissions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zafarkhaja.semver.Version;
import com.google.common.collect.ImmutableMap;

/*
 * These tests are specifically for testing the JSON-RPC communications between
 * the client, up to the invocation of the {@link us.kbase.workspace.workspaces.Workspaces}
 * methods. As such they do not test the full functionality of the Workspaces methods;
 * {@link us.kbase.workspace.test.workspaces.WorkspaceTest} handles that. This means
 * that only one backend (the simplest gridFS backend) is tested here, while WorkspaceTest
 * tests all backends and {@link us.kbase.workspace.database.WorkspaceDatabase} implementations.
 *
 * Many of these tests are far too long and should be rewritten.
 */
public class JSONRPCLayerTest extends JSONRPCLayerTester {

	private static final String VER = "0.15.0";

	@Test
	public void ver() throws Exception {
		assertThat("got correct version", CLIENT_NO_AUTH.ver(), is(VER));
	}

	@Test
	public void status() throws Exception {
		final Map<String, Object> st = CLIENT1.status();
		
		assertThat("incorrect status keys", st.keySet(), is(set(
				"state", "message", "dependencies", "version", "git_url", "git_commit",
				"freemem", "totalmem", "maxmem")));

		//top level items
		assertThat("incorrect state", st.get("state"), is("OK"));
		assertThat("incorrect message", st.get("message"), is("OK"));
		assertThat("incorrect version", st.get("version"), is(VER));
		assertThat("incorrect git url", st.get("git_url"),
				is("https://github.com/kbase/workspace_deluxe"));
		final String gc = (String) st.get("git_commit");
		if (gc.length() == 40) { // it's a git hash, probably running from gradle
			assertThat("is SHA1 hash", gc.matches("^[a-fA-F0-9]{40}$"), is(true));
		} else { // probably running from an IDE
			assertThat("incorrect git commit", st.get("git_commit"),
					is("Missing git commit file gitcommit, "
							+ "should be in us.kbase.workspace.gitcommit"));
		}
		checkMem(st.get("freemem"), "freemem");
		checkMem(st.get("totalmem"), "totalmem");
		checkMem(st.get("maxmem"), "maxmem");

		//deps
		@SuppressWarnings("unchecked")
		final List<Map<String, String>> deps = (List<Map<String, String>>) st.get("dependencies");
		assertThat("missing dependencies", deps.size(), is(2));

		final Iterator<Map<String, String>> gotiter = deps.iterator();
		for (final String name:  list("MongoDB", "GridFS")) {
			final Map<String, String> g = gotiter.next();
			assertThat("incorrect name", g.get("name"), is(name));
			assertThat("incorrect state", g.get("state"), is("OK"));
			assertThat("incorrect message", g.get("message"), is("OK"));
			Version.valueOf((String) g.get("version"));
		}
	}

	private void checkMem(final Object num, final String name)
			throws Exception {
		if (num instanceof Integer) {
			assertThat("bad " + name, (Integer) num > 0, is(true));
		} else {
			assertThat("bad " + name, (Long) num > 0, is(true));
		}
	}

	@Test
	public void createWSandCheck() throws Exception {
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("fry", "laurie");
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info =
				CLIENT1.createWorkspace(new CreateWorkspaceParams()
					.withWorkspace("foo")
					.withGlobalread("r")
					.withDescription("boogabooga")
					.withMeta(meta));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> infoget =
				CLIENT1.getWorkspaceInfo(new WorkspaceIdentity()
						.withWorkspace("foo"));
		checkWS(info, info.getE1(), info.getE4(), "foo", USER1, 0, "a", "r", "unlocked", "boogabooga", meta);
		checkWS(infoget, info.getE1(), info.getE4(), "foo", USER1, 0, "a", "r", "unlocked", "boogabooga", meta);
	}

	@Test
	public void setWorkspaceDescription() throws Exception {
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> meta =
				CLIENT1.createWorkspace(new CreateWorkspaceParams()
					.withWorkspace("wsdesc"));
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("wsdesc");
		CLIENT1.setWorkspaceDescription(new SetWorkspaceDescriptionParams()
				.withDescription("foobar").withWorkspace("wsdesc"));
		assertThat("correct ws desc", CLIENT1.getWorkspaceDescription(wsi),
				is("foobar"));
		SetWorkspaceDescriptionParams swdp = new SetWorkspaceDescriptionParams()
				.withDescription("foo").withId(meta.getE1());
		swdp.setAdditionalProperties("baz", "foo");
		failSetWSDesc(swdp, "Unexpected arguments in SetWorkspaceDescriptionParams: baz");
		failSetWSDesc(new SetWorkspaceDescriptionParams(),
				"Must provide one and only one of workspace name (was: null) or id (was: null)");
		failSetWSDesc(new SetWorkspaceDescriptionParams().withWorkspace("foo").withId(1L),
				"Must provide one and only one of workspace name (was: foo) or id (was: 1)");

		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace("wsdesc").withNewPermission("n"));
	}

	@Test
	public void createWSBadGlobal() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("gl1")); //should work fine w/o globalread
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
		.withWorkspace("gl2").withGlobalread("n")); //should work fine w/o globalread
		assertThat("globalread correct", CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("gl1")).getE7(), is("n"));
		assertThat("globalread correct", CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("gl2")).getE7(), is("n"));
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("gl_fail").withGlobalread("w"));
			fail("call succeeded w/ illegal global read param");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("globalread must be n or r"));
		}
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("gl_fail").withGlobalread("a"));
			fail("call succeeded w/ illegal global read param");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("globalread must be n or r"));
		}
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("gl_fail").withGlobalread("b"));
			fail("call succeeded w/ illegal global read param");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("globalread must be n or r"));
		}

		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace("gl1").withNewPermission("n"));
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace("gl2").withNewPermission("n"));
	}

	@Test
	public void createWSNoAuth() throws Exception {
		try {
			CLIENT_NO_AUTH.createWorkspace(new CreateWorkspaceParams().withWorkspace("noauth"));
			fail("created workspace without auth");
		} catch (UnauthorizedException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("RPC method requires authentication but credentials " +
							"were not provided"));
		}
	}

	@Test
	public void setBadPermissions() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("badperms"));
		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("badperms")
					.withUsers(Arrays.asList(USER2)));
			fail("able to set null permission");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("No such permission: null"));

		}
		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("badperms")
					.withNewPermission("f").withUsers(Arrays.asList(USER2)));
			fail("able to set illegal permission");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("No such permission: f"));

		}
		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("badperms")
					.withNewPermission("r").withUsers(new ArrayList<String>()));
			fail("able to set permission with no users");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Must provide at least one user"));
		}

		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("badperms")
					.withNewPermission("r").withUsers(Arrays.asList(USER2,
					"thisisnotarealuserihopeotherwisethistestwillfailandthatdbeabadthing")));
			fail("able to set  permission with bad user");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("User thisisnotarealuserihopeotherwisethistestwillfailandthatdbeabadthing is not a valid user"));
		}
		Map<String, String> expected = new HashMap<String, String>();
		expected.put(USER1, "a");
		@SuppressWarnings("deprecation")
		Map<String, String> perms = CLIENT1.getPermissions(new WorkspaceIdentity().withWorkspace("badperms"));
		assertThat("Bad permissions were added to a workspace", perms, is(expected));

		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
		.withWorkspace("badperms").withNewPermission("n"));
	}

	@SuppressWarnings("deprecation")
	@Test
	public void permissions() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("permspriv")
				.withDescription("foo"));
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("permsglob")
				.withGlobalread("r").withDescription("bar"));
		//should work, global read
		CLIENT2.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permsglob"));
		CLIENT_NO_AUTH.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permsglob"));
		CLIENT_NO_AUTH.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("permsglob"));

		try {
			CLIENT_NO_AUTH.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permspriv"));
			fail("able to read workspace desc with no auth");
		} catch (ServerException e) {
			assertThat("exception message corrent", e.getLocalizedMessage(),
					is("Anonymous users may not read workspace permspriv"));
		}

		try {
			CLIENT_NO_AUTH.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("permspriv"));
			fail("able to read workspace desc with no auth");
		} catch (ServerException e) {
			assertThat("exception message corrent", e.getLocalizedMessage(),
					is("Anonymous users may not read workspace permspriv"));
		}

		try {
			CLIENT2.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permspriv"));
			fail("Able to get ws desc without read perms");
		} catch (ServerException e) {
			assertThat("Correct excp message", e.getLocalizedMessage(),
					is("User "+USER2+" may not read workspace permspriv"));
		}
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("r").withUsers(Arrays.asList(USER2)));
		CLIENT2.getWorkspaceDescription(new WorkspaceIdentity().withWorkspace("permspriv")); //should work, now readable

		Map<String, String> data = new HashMap<String, String>();
		data.put("foo", "bar");
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		objects.add(new ObjectSaveData().withData(new UObject(data)).withName("fail")
				.withType(SAFE_TYPE));
		try {
			CLIENT2.saveObjects(new SaveObjectsParams()
				.withWorkspace("permspriv").withObjects(objects));
		} catch (ServerException e) {
			assertThat("correcet exception", e.getLocalizedMessage(),
					is("User "+USER2+" may not write to workspace permspriv"));
		}
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		CLIENT2.saveObjects(new SaveObjectsParams()
			.withWorkspace("permspriv").withObjects(objects)); //should work
		Map<String, String> expected = new HashMap<String, String>();
		expected.put(USER1, "a");
		expected.put(USER2, "w");
		Map<String, String> perms = CLIENT1.getPermissions(new WorkspaceIdentity()
			.withWorkspace("permspriv"));
		assertThat("Permissions set correctly", perms, is(expected));

		try {
			CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
					.withNewPermission("a").withUsers(Arrays.asList(USER1)));
		} catch (ServerException e) {
			assertThat("Correct excp message", e.getLocalizedMessage(),
					is("User "+USER2+" may not alter other user's permissions on workspace permspriv"));
		}
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("a").withUsers(Arrays.asList(USER2)));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("w").withUsers(Arrays.asList(USER3))); //should work
		expected.put(USER1, "a");
		expected.put(USER2, "a");
		expected.put(USER3, "w");
		perms = CLIENT2.getPermissions(new WorkspaceIdentity()
			.withWorkspace("permspriv"));
		assertThat("Permissions set correctly", perms, is(expected));

		//test setting perms on multiple users at same time
		//TODO TEST add clearCaches method to auth client & use here
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("permspriv")
				.withNewPermission("n").withUsers(Arrays.asList(USER2, USER3)));
		expected.remove(USER2);
		expected.remove(USER3);
		perms = CLIENT1.getPermissions(new WorkspaceIdentity()
			.withWorkspace("permspriv"));
		assertThat("Permissions set correctly", perms, is(expected));

		List<WorkspaceIdentity> wslist = Arrays.asList(
				new WorkspaceIdentity().withWorkspace("permspriv"),
				new WorkspaceIdentity().withWorkspace("permsglob"));
		WorkspacePermissions permsl = CLIENT1.getPermissionsMass(
				new GetPermissionsMassParams().withWorkspaces(wslist));
		List<Map<String, String>> exp = new LinkedList<Map<String,String>>();
		exp.add(expected);
		Map<String, String> gl = new HashMap<String, String>();
		gl.put(STARUSER, "r");
		gl.put(USER1, "a");
		exp.add(gl);
		assertThat("Permissions read correctly", permsl.getPerms(),
				is(exp));

		GetPermissionsMassParams p = new GetPermissionsMassParams()
			.withWorkspaces(wslist);
		p.setAdditionalProperties("foo", "bar");
		try {
			CLIENT1.getPermissionsMass(p);
			fail("passed extra args");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getLocalizedMessage(),
					is("Unexpected arguments in GetPermissionsMassParams: foo"));
		}


		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace("permspriv").withNewPermission("n"));
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace("permsglob").withNewPermission("n"));
	}

	@SuppressWarnings("deprecation")
	@Test
	public void permissionsWithNoCreds() throws Exception {
		/* Tests the case for getting permissions for a workspace without
		 * supplying credentials.
		 */
		final WorkspaceIdentity privWS = new WorkspaceIdentity()
				.withWorkspace("PnoCpriv");
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("PnoCpriv"));
		final WorkspaceIdentity globWS = new WorkspaceIdentity()
				.withWorkspace("PnoCglob");
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("PnoCglob").withGlobalread("r"));

		Map<String, String> expected1 = new HashMap<String, String>();
		Map<String, String> res = CLIENT_NO_AUTH.getPermissions(privWS);

		assertThat("No perms for private WS", res, is(expected1));

		Map<String, String> expected2 = new HashMap<String, String>();
		expected2.put(STARUSER, "r");
		res = CLIENT_NO_AUTH.getPermissions(globWS);

		assertThat("Read perm for global WS", res, is(expected2));

		WorkspacePermissions wpres = CLIENT_NO_AUTH.getPermissionsMass(
				new GetPermissionsMassParams().withWorkspaces(
						Arrays.asList(privWS, globWS)));

		assertThat("Mass perms correct for user w/o creds", wpres.getPerms(),
				is(Arrays.asList(expected1, expected2)));

		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams()
				.withWorkspace("PnoCglob").withNewPermission("n"));
	}

	@SuppressWarnings("deprecation")
	@Test
	public void badIdent() throws Exception {
		try {
			CLIENT1.getPermissions(new WorkspaceIdentity());
			fail("got non-existant workspace");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Must provide one and only one of workspace name (was: null) or id (was: null)"));
		}
	}

	@Test
	public void workspaceIDprocessing() throws Exception {
		String ws = "idproc";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws)
				.withDescription("foo"));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> meta =
				CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace(ws));
		//these should work
		CLIENT1.setPermissions(new SetPermissionsParams().withId(meta.getE1())
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace(meta.getE2())
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));

		try {
			CLIENT1.setPermissions(new SetPermissionsParams()
					.withUsers(Arrays.asList(USER2)).withNewPermission("w"));
			fail("able set perms without providing ws id or name");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Must provide one and only one of workspace name (was: null) or id (was: null)"));
		}

		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withId(meta.getE1())
					.withNewPermission("w").withUsers(Arrays.asList(USER2))
					.withWorkspace(meta.getE2()));
			fail("able to specify workspace by id and name");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is(String.format(
					"Must provide one and only one of workspace name (was: idproc) or id (was: %s)",
					meta.getE1())));
		}

		try {
			CLIENT1.setPermissions(new SetPermissionsParams().withId(-1L)
					.withNewPermission("w").withUsers(Arrays.asList(USER2)));
			fail("able to specify workspace by id and name");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Workspace id must be > 0"));
		}

		//should work
		CLIENT1.setPermissions(new SetPermissionsParams()
				.withWorkspace(meta.getE2())
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));

		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
					.withWorkspace("kb|ws." + meta.getE1()));
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Illegal character in workspace name kb|ws."
					+ meta.getE1() + ": |"));
		}
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams()
					.withWorkspace(TEXT256));
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Workspace name exceeds the maximum length of 255"));
		}
	}

	@Test
	public void saveBadPackages() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("savebadpkg"));
		final List<ObjectSaveData> objects = new ArrayList<>();
		objects.add(new ObjectSaveData().withData(new UObject("some crap"))
				.withType("SomeRandom.Type"));
		final SaveObjectsParams sop = new SaveObjectsParams().withWorkspace("permspriv")
				.withObjects(objects);
		sop.setAdditionalProperties("foo", "bar");
		sop.setAdditionalProperties("baz", "faz");
		try {
			CLIENT1.saveObjects(sop);
			fail("allowed unexpected args");
		} catch (ServerException e) {
			String[] exp = e.getLocalizedMessage().split(":");
			String[] args = exp[1].trim().split("\\s");
			HashSet<String> argset = new HashSet<String>(Arrays.asList(args));
			assertThat("correct exception message", exp[0],
					is("Unexpected arguments in SaveObjectsParams"));
			assertThat("correct args list", argset,
					is(new HashSet<String>(Arrays.asList("foo", "baz"))));
		}

		final ObjectSaveData safe = new ObjectSaveData()
				.withData(new UObject("foo"))
				.withName("bar")
				.withType(SAFE_TYPE);

		failSaveObjects(list(safe, new ObjectSaveData().withName("myname").withObjid(1L)),
				"Object #2: Must provide one and only one of object name (was: myname) or id "
				+ "(was: 1)");

		objects.set(0, new ObjectSaveData());
		failSaveObjects(objects, "Object #1: Must provide one and only one of object name " +
				"(was: null) or id (was: null)");

		objects.set(0, new ObjectSaveData().withName("myname+"));
		failSaveObjects(objects, "Object #1: Illegal character in object name myname+: +");

		objects.set(0, new ObjectSaveData().withName(TEXT256));
		failSaveObjects(objects, "Object #1: Object name exceeds the maximum length of 255");

		objects.set(0, new ObjectSaveData().withObjid(0L));
		failSaveObjects(objects, "Object #1: Object id must be > 0");

		final ObjectSaveData osd = new ObjectSaveData().withName("myname");
		osd.setAdditionalProperties("wugga", "boo");
		objects.set(0, osd);
		failSaveObjects(list(safe, safe, osd),
				"Object #3, myname: Unexpected arguments in ObjectSaveData: wugga");

		objects.set(0, new ObjectSaveData().withName("foo"));
		failSaveObjects(objects, "Object #1, foo: no data");

		failSaveObjects(list(safe, new ObjectSaveData().withObjid(42L)), "Object #2, 42: no data");

		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withName("foo"));
		failSaveObjects(objects, "Object #1, foo: Typestring cannot be null or the " +
				"empty string");

		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withType(null)
				.withName("foo"));
		failSaveObjects(objects, "Object #1, foo: Typestring cannot be null or the " +
				"empty string");

		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withType("")
				.withName("foo"));
		failSaveObjects(objects, "Object #1, foo: Typestring cannot be null or the " +
				"empty string");

		objects.set(0, new ObjectSaveData().withData(new UObject("foo")).withType("foo")
				.withName("foo"));
		failSaveObjects(objects, "Object #1, foo: Type foo could not be split into a " +
				"module and name");

		failSaveObjects(list(safe, safe, safe,
				new ObjectSaveData().withData(new UObject("foo")).withType("foo.bar-1.2.3")
						.withObjid(1L)),
				"Object #4, 1: Type version string 1.2.3 could not be parsed to a version");
	}

	private void failSaveObjects(final List<ObjectSaveData> objects, final String exception)
			throws Exception {
		try {
			CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("savebadpkg")
					.withObjects(objects));
			fail("saved invalid data package");
		} catch (ServerException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is(exception));
		}
	}

	@Test
	public void saveObjectFailNull() throws Exception {
		final SaveObjectsParams sop = new SaveObjectsParams().withWorkspace("foo")
				.withObjects(list(
						new ObjectSaveData()
								.withData(new UObject("foo"))
								.withName("bar")
								.withType(SAFE_TYPE),
						new ObjectSaveData()
								.withData(new UObject("foo"))
								.withName("bat")
								.withType(SAFE_TYPE),
						null
				));
		failSaveObjects(sop, "Object #3: is null");
	}

	@Test
	public void saveProvenance() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("provenance"));
		long wsid = CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("provenance")).getE1();
		UObject data = new UObject(new HashMap<String, Object>());
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("provenance")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(data).withName("auto1")
						.withType(SAFE_TYPE))));

		SaveObjectsParams sop = new SaveObjectsParams().withWorkspace("provenance")
				.withObjects(objects);
		List<ExternalDataUnit> edu = new LinkedList<ExternalDataUnit>();
		edu.add(new ExternalDataUnit()
				.withDataId("data id")
				.withDataUrl("http://somedata.org/somedata")
				.withDescription("a description")
				.withResourceName("resource")
				.withResourceReleaseDate("2013-04-26T12:52:06-0800")
				.withResourceUrl("http://somedata.org")
				.withResourceVersion("1.2.3")
				);
		edu.add(new ExternalDataUnit().withDataId("foo"));

		Map<String, String> cs = new HashMap<String, String>();
		cs.put("The", "Donald");
		cs.put("TedCruz", "ElbowToFace");

		List<SubAction> sa = new LinkedList<SubAction>();
		sa.add(new SubAction()
				.withCodeUrl("https://github.com/woot/woot")
				.withCommit("aaaa")
				.withEndpointUrl("http://myserver.com/")
				.withName("name")
				.withVer("0.1.2")
				);

		List<ProvenanceAction> prov = Arrays.asList(
				new ProvenanceAction()
					.withDescription("desc")
					.withCaller("a caller")
					.withInputWsObjects(Arrays.asList("provenance/auto1/1"))
					.withIntermediateIncoming(Arrays.asList("a", "b", "c"))
					.withIntermediateOutgoing(Arrays.asList("d", "e", "f"))
					.withMethod("meth")
					.withMethodParams(Arrays.asList(new UObject("foo"),
							new UObject(new HashMap<String, String>()),
							new UObject(Arrays.asList("foo", "bar"))))
					.withResolvedWsObjects(Arrays.asList("will be ignored"))
					.withScript("script")
					.withScriptCommandLine("cmd line")
					.withScriptVer("1")
					.withService("serv")
					.withServiceVer("2")
					.withExternalData(edu)
					.withCustom(cs)
					.withSubactions(sa)
					.withTime("2013-04-26T12:52:06-0800"),
				new ProvenanceAction()
					.withCaller("c")
					// set arrays to empty lists since that's what the workspace will now return
					.withIntermediateIncoming(Collections.emptyList())
					.withMethodParams(Collections.emptyList())
					.withIntermediateOutgoing(Collections.emptyList())
					);
		objects.add(new ObjectSaveData().withData(data).withType(SAFE_TYPE).withName("auto2")
				.withProvenance(prov));
		CLIENT1.saveObjects(sop);
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("provenance/auto1/1", wsid + "/1/1");
		Map<StringEpoch, StringEpoch> timemap =
				new HashMap<StringEpoch, StringEpoch>();
		timemap.put(new StringEpoch("2013-04-26T12:52:06-0800"),
				new StringEpoch(1367009526000L, "2013-04-26T20:52:06+0000"));
		ObjectIdentity id = new ObjectIdentity().withWsid(wsid).withObjid(2L);
		checkProvenance(USER1, id, prov, refmap, timemap);

		ProvenanceAction pa = new ProvenanceAction();
		pa.setAdditionalProperties("foo", "bar");
		objects.set(0, new ObjectSaveData().withData(data).withType(SAFE_TYPE).withName("auto3")
				.withProvenance(Arrays.asList(pa)));

		try {
			CLIENT1.saveObjects(sop);
			fail("save w/ prov w/ extra fields");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Object #1, auto3: Provenance action #1: Unexpected arguments in "
							+ "ProvenanceAction: foo"));
		}

		ExternalDataUnit edusingle = new ExternalDataUnit();
		edusingle.setAdditionalProperties("baz", "bar");
		pa = new ProvenanceAction().withExternalData(Arrays.asList(edusingle));
		objects.set(0, new ObjectSaveData().withData(data).withType(SAFE_TYPE)
				.withProvenance(Arrays.asList(pa)).withName("auto4"));

		try {
			CLIENT1.saveObjects(sop);
			fail("save prov external data w/ extra fields");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Object #1, auto4: Provenance action #1: External data unit #1: "
							+ "Unexpected arguments in ExternalDataUnit: baz"));
		}

		edusingle.getAdditionalProperties().clear();
		edusingle.withResourceReleaseDate("2013-04-26T12:52:06-0800")
			.withResourceReleaseEpoch(1L);

		try {
			CLIENT1.saveObjects(sop);
			fail("save prov external data w/ ambiguous time");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Object #1, auto4: Provenance action #1: External data unit #1: Cannot"
							+ " specify both time and epoch in external data unit"));
		}

		pa.withExternalData(null).withTime("2013-04-26T12:52:06-0800")
			.withEpoch(1L);
		try {
			CLIENT1.saveObjects(sop);
			fail("save prov w/ ambiguous time");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Object #1, auto4: Provenance action #1: Cannot specify both time and"
							+ " epoch in provenance action"));
		}

		// test time conversion to a standard format
		saveProvWithGoodTime("provenance",
				new StringEpoch("2013-04-26T23:52:06-0800"),
				new StringEpoch(1367049126000L, "2013-04-27T07:52:06+0000"));
		saveProvWithGoodTime("provenance",
				new StringEpoch("2013-04-26T23:52:06Z"),
				new StringEpoch(1367020326000L, "2013-04-26T23:52:06+0000"));
		saveProvWithGoodTime("provenance",
				new StringEpoch("2013-04-26T23:52:06.145Z"),
				new StringEpoch(1367020326145L, "2013-04-26T23:52:06+0000"));
		saveProvWithGoodTime("provenance",
				new StringEpoch("2013-04-26T23:52:06.14Z"),
				new StringEpoch(1367020326140L, "2013-04-26T23:52:06+0000"));
		saveProvWithGoodTime("provenance",
				new StringEpoch("2013-04-26T23:52:06.1Z"),
				new StringEpoch(1367020326100L, "2013-04-26T23:52:06+0000"));
		saveProvWithGoodTime("provenance",
				new StringEpoch("2013-04-26T23:52:06.14-0800"),
				new StringEpoch(1367049126140L, "2013-04-27T07:52:06+0000"));
		saveProvWithGoodTime("provenance",
				new StringEpoch(1367049126140L),
				new StringEpoch(1367049126140L, "2013-04-27T07:52:06+0000"));

		CLIENT1.setPermissions(new SetPermissionsParams().withId(wsid)
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("provenance")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(data)
						.withType(SAFE_TYPE).withName("whoops"))));
		checkProvenance(USER2, new ObjectIdentity().withName("whoops")
				.withWsid(wsid), new ArrayList<ProvenanceAction>(),
				new HashMap<String, String>(),
				new HashMap<StringEpoch, StringEpoch>());
	}
	@Test
	public void saveProvWithBadTime() throws Exception {
		// most tests for bad dates are in the arg utils tests, so we just do a single case here
		final String err = "Unparseable date: Text '2013-04-26T25:52:06-0800' could not be "
				+ "parsed: Invalid value for HourOfDay (valid values 0 - 23): 25";
		final ProvenanceAction pa = new ProvenanceAction().withTime("2013-04-26T25:52:06-0800");
		final SaveObjectsParams sop = new SaveObjectsParams().withWorkspace("provenance")
				.withObjects(list(
						new ObjectSaveData()
								.withData(new UObject("foo"))
								.withType(SAFE_TYPE)
								.withName("foobarbaz")
								.withProvenance(list(pa))));
		failSaveObjects(sop, "Object #1, foobarbaz: Provenance action #1: " + err);

		pa.withTime(null).withExternalData(list(new ExternalDataUnit()
				.withResourceReleaseDate("2013-04-26T25:52:06-0800")));
		failSaveObjects(sop, "Object #1, foobarbaz: Provenance action #1: External data unit #1: "
				+ err);
	}

	private void failSaveObjects(final SaveObjectsParams sop, final String exception)
			throws Exception {
		try {
			CLIENT1.saveObjects(sop);
			fail("expected exception");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(), is(exception));
		}
	}

	private void saveProvWithGoodTime(String workspace, StringEpoch inputTime,
			StringEpoch expectedTime) throws Exception {
		UObject data = new UObject(new HashMap<String, Object>());

		List<ProvenanceAction> prov = new LinkedList<ProvenanceAction>();
		final ProvenanceAction pa = new ProvenanceAction()
				// workspace always returns lists for these now
				.withIntermediateIncoming(Collections.emptyList())
				.withIntermediateOutgoing(Collections.emptyList())
				.withMethodParams(Collections.emptyList());
		if (inputTime.time != null) {
			prov.add(pa.withTime(inputTime.time)
					.withExternalData(Arrays.asList(
							new ExternalDataUnit()
								.withResourceReleaseDate(inputTime.time))));
		} else {
			prov.add(pa.withEpoch(inputTime.epoch)
					.withExternalData(Arrays.asList(
							new ExternalDataUnit()
								.withResourceReleaseEpoch(inputTime.epoch))));
		}
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		objects.add(new ObjectSaveData().withData(data).withType(SAFE_TYPE)
				.withProvenance(prov).withName(getRandomName()));
		SaveObjectsParams sop = new SaveObjectsParams().withWorkspace(workspace)
				.withObjects(objects);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> oi =
				CLIENT1.saveObjects(sop).get(0);
		Long objid = oi.getE1();
		Long wsid = oi.getE7();
		Map<String, String> refmap = new HashMap<String, String>();
		Map<StringEpoch, StringEpoch> timemap =
				new HashMap<StringEpoch, StringEpoch>();
		timemap.put(inputTime, expectedTime);
		ObjectIdentity id = new ObjectIdentity().withWsid(wsid).withObjid(objid);
		checkProvenance(USER1, id, prov, refmap, timemap);
	}

	@Test
	public void saveProvenanceFail() throws Exception {
		// Test a subset of cases that can cause a save failure because of provenance input
		// Mostly tests the overall error handling mechanisms rather than testing every single
		// failure case, which should be and are covered by unit tests.
		final ObjectSaveData safe = new ObjectSaveData()
				.withName("foo")
				.withData(new UObject(Collections.emptyMap()))
				.withType(SAFE_TYPE1);
		final ProvenanceAction safep = new ProvenanceAction().withInputWsObjects(list("1/1/1"));

		final ObjectSaveData target = new ObjectSaveData()
				.withData(new UObject(Collections.emptyMap()))
				.withType(SAFE_TYPE1);

		// null item in provenance
		final ProvenanceAction pa = new ProvenanceAction().withInputWsObjects(list("1/1/1", null));
		failSaveObjects(list(safe, safe, target.withProvenance(list(safep, pa)).withObjid(4L)),
				"Object #3, 4: Provenance action #2: Invalid workspace object provenenance "
				+ "reference at position 2: refpath cannot be null or the empty string");

		// empty string in provenance
		pa.withInputWsObjects(list("1/1/1", "2/2/2", "  \t   \n "));
		failSaveObjects(list(target.withProvenance(list(pa)).withObjid(null).withName("foo")),
				"Object #1, foo: Provenance action #1: Invalid workspace object provenenance "
				+ "reference at position 3: Illegal number of separators '/' in object reference"
				+ " '  \t   \n '");

		// Illegal URL in external data unit
		final ProvenanceAction pa2 = new ProvenanceAction().withExternalData(list(
				new ExternalDataUnit().withDataId("d"),
				new ExternalDataUnit().withDataId("e"),
				new ExternalDataUnit().withDataId("f"),
				new ExternalDataUnit().withDataUrl("snailmail://1cyclotronroad.berkeley.ca")
				));
		failSaveObjects(list(safe, target.withProvenance(list(safep, safep, pa2))
				.withName("thinger")),
				"Object #2, thinger: Provenance action #3: External data unit #4: Illegal data"
				+ " url 'snailmail://1cyclotronroad.berkeley.ca': unknown protocol: snailmail");

		// Empty sub action
		final ProvenanceAction pa3 = new ProvenanceAction().withSubactions(list(
				new SubAction().withCommit("c"),
				new SubAction().withEndpointUrl(
						"Isn't that an Avengers movie? wait that's something different")
				));
		failSaveObjects(list(target.withProvenance(list(pa3)).withObjid(789L).withName(null)),
				"Object #1, 789: Provenance action #1: Sub action #2: Illegal endpoint url "
				+ "'Isn't that an Avengers movie? wait that's something different': no protocol: "
				+ "Isn't that an Avengers movie? wait that's something different");
	}

	@Test
	public void saveObjectsWithLargeString() throws Exception {
		String wsName = "largestring";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(wsName));
		String largeString = generateLargeString(1234567);  // longer than 1 megabyte
		Map<String, Object> data = new LinkedHashMap<String, Object>();
		data.put("z", "1");
		data.put("thing", largeString);  // data is not sorted
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(wsName)
				.withObjects(Arrays.asList(
						new ObjectSaveData().withName("obj1").withType(SAFE_TYPE).withData(new UObject(data)),
						new ObjectSaveData().withName("obj2").withType(SAFE_TYPE).withData(new UObject(data)))));
		List<ObjectData> ret = CLIENT1.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(
				new ObjectSpecification().withRef(wsName + "/obj2"),
				new ObjectSpecification().withRef(wsName + "/obj1")))).getData();
		for (ObjectData obj : ret) {
			String largeString2 = (String)obj.getData().asClassInstance(Map.class).get("thing");
			if (!largeString2.equals(largeString))
				fail("Observed large string is: " + largeString2);
		}
	}

	private static String generateLargeString(int length) {
		char[] chars = new char[length];
		for (int i = 0; i < length; i++)
			chars[i] = (char)(32 + (i % (127 - 32)));
		return new String(chars);
	}

	@Test
	public void saveAndGetObjects() throws Exception {

		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("saveget"));
		long wsid = CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("saveget")).getE1();

		//save some objects to get
		final Map<String, Object> moredata = ImmutableMap.of("foo", "bar");
		final Map<String, Object> data = ImmutableMap.of("fubar", moredata);
		final Map<String, Object> data2 = ImmutableMap.of("fubar2", moredata);
		final Map<String, String> meta = ImmutableMap.of("metastuff", "meta");
		final Map<String, String> meta2 = ImmutableMap.of("meta2", "my hovercraft is full of eels");
		final Map<String, String> mtmeta = Collections.emptyMap();

		final List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		final SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("saveget")
				.withObjects(objects);

		try {
			CLIENT1.saveObjects(soc);
			fail("called save with no data");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("No data provided"));
		}

		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withMeta(meta).withType(SAFE_TYPE).withName("auto1")); // will be "1"
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withMeta(meta).withType(SAFE_TYPE).withName("auto2")); // will be "2"
		objects.add(new ObjectSaveData().withData(new UObject(data2))
				.withMeta(meta2).withType(SAFE_TYPE).withName("foo"));

		List<Tuple11<Long, String, String, String, Long, String, Long, String,
			String, Long, Map<String, String>>> retmet =
				CLIENT1.saveObjects(soc);

		assertThat("max obj count correct", CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("saveget")).getE5(), is(3L));

		assertThat("num metas correct", retmet.size(), is(3));
		checkInfo(retmet.get(0), 1, "auto1", SAFE_TYPE, 1, USER1, wsid, "saveget", "36c4f68f2c98971b9736839232eb08f4", 23, meta);
		checkInfo(retmet.get(1), 2, "auto2", SAFE_TYPE, 1, USER1, wsid, "saveget", "36c4f68f2c98971b9736839232eb08f4", 23, meta);
		checkInfo(retmet.get(2), 3, "foo", SAFE_TYPE, 1, USER1, wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);

		ObjectIdentity ojbid = new ObjectIdentity().withWorkspace("saveget")
				.withName("auto1");
		Map<String, List<String>> exp = new HashMap<String, List<String>>();
		ObjectData objo = CLIENT1.getObjects2(new GetObjects2Params()
			.withObjects(toObjSpec(Arrays.asList(ojbid)))
			.withSkipExternalSystemUpdates(1L) // should have no effect
			.withBatchExternalSystemUpdates(1L) // should have no effect
			).getData().get(0);
		assertThat("extracted ids empty", objo.getExtractedIds(), is(exp));
		@SuppressWarnings("deprecation")
		ObjectData obj = CLIENT1.getObjects(Arrays.asList(ojbid)).get(0);
		assertThat("extracted ids empty", obj.getExtractedIds(), is(exp));
		@SuppressWarnings("deprecation")
		ObjectData obj2 = CLIENT1.getObjectSubset(objIDToSubObjID(Arrays.asList(ojbid))).get(0);
		assertThat("extracted ids empty", obj2.getExtractedIds(), is(exp));
		@SuppressWarnings("deprecation")
		Map<String, List<String>> extids =
				CLIENT1.getObjectProvenance(Arrays.asList(ojbid)).get(0).getExtractedIds();
		assertThat("extracted ids empty", extids, is(exp));


		objects.clear();
		objects.add(new ObjectSaveData().withData(new UObject(data2))
				.withMeta(meta2).withType(SAFE_TYPE).withObjid(2L));

		// tests saving with workspace id instead of name
		soc.withWorkspace(null).withId(wsid);
		retmet = CLIENT1.saveObjects(soc);

		assertThat("num metas correct", retmet.size(), is(1));
		checkInfo(retmet.get(0), 2, "auto2", SAFE_TYPE, 2, USER1, wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);

		List<ObjectIdentity> loi = new ArrayList<ObjectIdentity>();
		loi.add(new ObjectIdentity().withRef("saveget/2/1"));
		loi.add(new ObjectIdentity().withRef(wsid + "/2/1"));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withName("auto2").withVer(1L));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withObjid(2L).withVer(1L));
		loi.add(new ObjectIdentity().withWsid(wsid).withName("auto2").withVer(1L));
		loi.add(new ObjectIdentity().withWsid(wsid).withObjid(2L).withVer(1L));
		checkSavedObjects(loi, 2, "auto2", SAFE_TYPE, 1, USER1,
				wsid, "saveget", "36c4f68f2c98971b9736839232eb08f4", 23, meta, mtmeta, data);
		loi.clear();
		// w/o versions
		loi.add(new ObjectIdentity().withRef("saveget/2"));
		loi.add(new ObjectIdentity().withRef(wsid + "/2"));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withName("auto2"));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withObjid(2L));
		loi.add(new ObjectIdentity().withWsid(wsid).withName("auto2"));
		loi.add(new ObjectIdentity().withWsid(wsid).withObjid(2L));
		// w/ versions
		loi.add(new ObjectIdentity().withRef("saveget/2/2"));
		loi.add(new ObjectIdentity().withRef(wsid + "/2/2"));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withName("auto2").withVer(2L));
		loi.add(new ObjectIdentity().withWorkspace("saveget").withObjid(2L).withVer(2L));
		loi.add(new ObjectIdentity().withWsid(wsid).withName("auto2").withVer(2L));
		loi.add(new ObjectIdentity().withWsid(wsid).withObjid(2L).withVer(2L));

		checkSavedObjects(loi, 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2, mtmeta, data2);

		failGetObjects(new ArrayList<ObjectIdentity>(), "No object identifiers provided");

		// try some bad refs and id/name combos
		loi.clear();
		loi.add(new ObjectIdentity().withRef("saveget/2"));
		loi.add(new ObjectIdentity().withRef("kb|wss." + wsid + ".obj.2"));
		failGetObjects(loi, "Error on ObjectIdentity #2: Illegal number of separators '/' in "
				+ "object reference 'kb|wss." + wsid + ".obj.2'");

		loi.set(1, new ObjectIdentity().withRef("saveget/1"));
		loi.add(new ObjectIdentity().withRef("kb|ws." + wsid));
		failGetObjects(loi, "Error on ObjectIdentity #3: Illegal number of separators '/' in "
				+ "object reference 'kb|ws." + wsid + "'");

		//there are 32 different ways to get this type of error. Just try a few.
		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withName("2"));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. Object name: 2");

		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withObjid(2L));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. Object id: 2");

		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withVer(2L));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. Version: 2");

		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withWorkspace("saveget"));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. Workspace: saveget");

		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withWsid(wsid));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. Workspace id: " + wsid);

		loi.set(2, new ObjectIdentity().withRef("kb|ws." + wsid).withWsid(wsid).withWorkspace("saveget").withVer(2L));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object reference kb|ws." + wsid + " provided; cannot provide any other means of identifying an object. Workspace: saveget Workspace id: " + wsid + " Version: 2");

		ObjectIdentity oi = new ObjectIdentity().withRef("saveget/1");
		oi.setAdditionalProperties("foo", "bar");
		loi.set(2, oi);
		failGetObjects(loi, "Error on ObjectIdentity #3: Unexpected arguments in ObjectIdentity: foo");

		loi.set(2, new ObjectIdentity().withWorkspace("kb|ws." + wsid).withObjid(2L));
		failGetObjects(loi, "Error on ObjectIdentity #3: Illegal character in workspace name kb|ws." + wsid + ": |");

		loi.set(2, new ObjectIdentity().withWsid(wsid).withObjid(-1L));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object id must be > 0");
		loi.set(2, new ObjectIdentity().withWsid(wsid).withObjid(1L).withVer(0L));
		failGetObjects(loi, "Error on ObjectIdentity #3: Object version must be > 0");
		loi.set(2, new ObjectIdentity().withWsid(wsid).withObjid(1L).withVer(Integer.MAX_VALUE + 1L));
		failGetObjects(loi, "Error on ObjectIdentity #3: Maximum object version is " + Integer.MAX_VALUE);

		loi.set(2, new ObjectIdentity().withWorkspace("ultrafakeworkspace").withObjid(1L).withVer(1L));
		failGetObjects(loi, "Object 1 cannot be accessed: No workspace with name ultrafakeworkspace exists");
		loi.set(2, new ObjectIdentity().withWsid(20000000000000000L).withObjid(1L).withVer(1L));
		failGetObjects(loi, "Object 1 cannot be accessed: No workspace with id 20000000000000000 exists");
		loi.set(2, new ObjectIdentity().withWsid(wsid).withObjid(300L).withVer(1L));
		failGetObjects(loi, "No object with id 300 exists in workspace 1 (name saveget)");
		loi.set(2, new ObjectIdentity().withWsid(wsid).withName("ultrafakeobj").withVer(1L));
		failGetObjects(loi, "No object with name ultrafakeobj exists in workspace 1 " +
				"(name saveget)");

		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("setgetunreadableto1"));
		loi.set(2, new ObjectIdentity().withWorkspace("setgetunreadableto1").withObjid(1L).withVer(1L));
		failGetObjects(loi, "Object 1 cannot be accessed: User " + USER1 + " may not read workspace setgetunreadableto1");

		//test get_object_info w/o errors
		GetObjectInfo3Params p = new GetObjectInfo3Params().withObjects(
				Arrays.asList(toObjSpec(loi.get(0))));
		p.setAdditionalProperties("wooga", "foo");
		failGetObjectInfo(p, "Unexpected arguments in GetObjectInfo3Params: wooga");
		failGetObjectInfo(new GetObjectInfo3Params().withObjects(null),
				"The object specification list cannot be null");

		List<ObjectSpecification> nullloi = new ArrayList<ObjectSpecification>();
		nullloi.add(new ObjectSpecification().withWorkspace("ultrafakeworkspace").withObjid(1L).withVer(1L));
		nullloi.add(new ObjectSpecification().withWsid(20000000000000000L).withObjid(1L).withVer(1L));
		nullloi.add(new ObjectSpecification().withRef("saveget/2"));
		nullloi.add(new ObjectSpecification().withWsid(wsid).withObjid(300L).withVer(1L));
		nullloi.add(new ObjectSpecification().withRef(wsid + "/2"));
		nullloi.add(new ObjectSpecification().withWsid(wsid).withName("ultrafakeobj").withVer(1L));
		nullloi.add(new ObjectSpecification().withWorkspace("setgetunreadableto1").withObjid(1L).withVer(1L));

		GetObjectInfo3Results nullret3 = CLIENT1.getObjectInfo3(new GetObjectInfo3Params()
				.withObjects(nullloi).withIgnoreErrors(1L).withIncludeMetadata(1L));
		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>>> nullret = nullret3.getInfos();

		assertNull("Got object info when expected null", nullret.get(0));
		assertNull("Got object info when expected null", nullret.get(1));
		checkInfo(nullret.get(2), 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);
		assertNull("Got object info when expected null", nullret.get(3));
		checkInfo(nullret.get(4), 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);
		assertNull("Got object info when expected null", nullret.get(5));
		assertNull("Got object info when expected null", nullret.get(6));
		final List<String> targetPath = Arrays.asList(wsid + "/2/2");
		assertThat("incorrect paths", nullret3.getPaths(), is(Arrays.asList(
				null, null, targetPath, null, targetPath, null, null)));

		@SuppressWarnings("deprecation")
		final List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>>> nullret2 = CLIENT1.getObjectInfoNew(
						new us.kbase.workspace.GetObjectInfoNewParams()
								.withObjects(nullloi).withIgnoreErrors(1L)
								.withIncludeMetadata(1L));

		assertNull("Got object info when expected null", nullret2.get(0));
		assertNull("Got object info when expected null", nullret2.get(1));
		checkInfo(nullret2.get(2), 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);
		assertNull("Got object info when expected null", nullret2.get(3));
		checkInfo(nullret2.get(4), 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);
		assertNull("Got object info when expected null", nullret2.get(5));
		assertNull("Got object info when expected null", nullret2.get(6));

		List<ObjectData> nullobj = CLIENT1.getObjects2(new GetObjects2Params()
			.withObjects(nullloi).withIgnoreErrors(1L)).getData();

		assertNull("Got object info when expected null", nullobj.get(0));
		assertNull("Got object info when expected null", nullobj.get(1));
		checkData(nullobj.get(2), 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2, data2);
		assertNull("Got object info when expected null", nullobj.get(3));
		checkData(nullobj.get(4), 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2, data2);
		assertNull("Got object info when expected null", nullobj.get(5));
		assertNull("Got object info when expected null", nullobj.get(6));

		CLIENT2.setPermissions(new SetPermissionsParams().withNewPermission("r")
				.withUsers(Arrays.asList(USER1)).withWorkspace("setgetunreadableto1"));
		CLIENT2.deleteWorkspace(new WorkspaceIdentity().withWorkspace("setgetunreadableto1"));
		CLIENT1.deleteObjects(Arrays.asList(new ObjectIdentity().withRef("saveget/1")));

		nullloi.set(2, new ObjectSpecification().withRef("saveget/1"));
		nullloi.set(5, new ObjectSpecification().withWorkspace("setgetunreadableto1")
				.withName("foo"));

		nullret3 = CLIENT1.getObjectInfo3(new GetObjectInfo3Params().withObjects(nullloi)
				.withIgnoreErrors(1L).withIncludeMetadata(1L));
		nullret = nullret3.getInfos();

		assertNull("Got object info when expected null", nullret.get(0));
		assertNull("Got object info when expected null", nullret.get(1));
		assertNull("Got object info when expected null", nullret.get(2));
		assertNull("Got object info when expected null", nullret.get(3));
		checkInfo(nullret.get(4), 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);
		assertNull("Got object info when expected null", nullret.get(5));
		assertNull("Got object info when expected null", nullret.get(6));
		assertThat("incorrect paths", nullret3.getPaths(), is(Arrays.asList(
				null, null, null, null, targetPath, null, null)));

		@SuppressWarnings("deprecation")
		final List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>>> nullret2_2 = CLIENT1.getObjectInfoNew(
						new us.kbase.workspace.GetObjectInfoNewParams().withObjects(nullloi)
						.withIgnoreErrors(1L).withIncludeMetadata(1L));

		assertNull("Got object info when expected null", nullret2_2.get(0));
		assertNull("Got object info when expected null", nullret2_2.get(1));
		assertNull("Got object info when expected null", nullret2_2.get(2));
		assertNull("Got object info when expected null", nullret2_2.get(3));
		checkInfo(nullret.get(4), 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2);
		assertNull("Got object info when expected null", nullret2_2.get(5));
		assertNull("Got object info when expected null", nullret2_2.get(6));

		nullobj = CLIENT1.getObjects2(new GetObjects2Params().withObjects(nullloi)
				.withIgnoreErrors(1L)).getData();

		assertNull("Got object info when expected null", nullobj.get(0));
		assertNull("Got object info when expected null", nullobj.get(1));
		assertNull("Got object info when expected null", nullobj.get(2));
		assertNull("Got object info when expected null", nullobj.get(3));
		checkData(nullobj.get(4), 2, "auto2", SAFE_TYPE, 2, USER1,
				wsid, "saveget", "3c59f762140806c36ab48a152f28e840", 24, meta2, data2);
		assertNull("Got object info when expected null", nullobj.get(5));
		assertNull("Got object info when expected null", nullobj.get(6));
	}

	// TODO TEST should test that getting objects with handles fail, but that's a pain to set up
	@Test
	public void saveObjectsFailNoHandleProcessor() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("nohandle")).getE1();

		saveObject2MethodsFail(CLIENT1, "nohandle", "foo", "HandleByteStream.ExtIDs",
				new UObject(ImmutableMap.of("h", "KBH_1")), new ServerException(
						"Object #1, foo failed type checking:\nInvalid id KBH_1 of type " +
						"handle: Found handle id KBH_1. The workspace service currently does " +
						"not have a connection to the handle service and so cannot process " +
						"objects containing handle IDs. at /h", 1, "n"));
	}

	// TODO TEST should test that getting objects with bs ids fail, but that's a pain to set up
	@Test
	public void saveObjectsFailNoShockProcessor() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("noshock")).getE1();

		final String id = UUID.randomUUID().toString();
		saveObject2MethodsFail(CLIENT1, "noshock", "foo", "HandleByteStream.ExtIDs",
				new UObject(ImmutableMap.of("s", id)),
				new ServerException(String.format(
						"Object #1, foo failed type checking:\nInvalid id %s of type " +
						"bytestream: Found bytestream id %s. There is no connection configured " +
						"for bytestream IDs and so objects containing bytestream IDs cannot be " +
						"processed. at /s", id, id), 1, "n"));
	}

	// TODO TEST should test that getting objects with samples fail, but that's a pain to set up
	@Test
	public void saveObjectsFailNoSampleProcessor() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("nosample")).getE1();

		// these error messages could be cleaned up a bit. They're caused by multiple layers
		// of code handling the lower layer's errors.
		// However, these errors should basically never happen in practice so meh.
		saveObject2MethodsFail(CLIENT1, "nosample", "foo", "Sample.SampleID",
				new UObject(ImmutableMap.of("s", "UUID_goes_here")), new ServerException(
						"Object #1, foo failed type checking:\nInvalid id UUID_goes_here of " +
						"type sample: Found sample id UUID_goes_here. The workspace service " +
						"currently does not have a connection to the sample service and so " +
						"cannot process objects containing sample IDs. at /s", 1, "n"));
	}


	private void saveObject2MethodsFail(
			final WorkspaceClient cli,
			final String workspace,
			final String name,
			final String type,
			final UObject data,
			final Exception expected) {
		try {
			@SuppressWarnings({ "deprecation", "unused" })
			final Object saveObject = cli.saveObject(new us.kbase.workspace.SaveObjectParams()
					.withData(data)
					.withId(name)
					.withWorkspace(workspace)
					.withType(type));
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, expected);
		}
		try {
			cli.saveObjects(new SaveObjectsParams()
					.withWorkspace(workspace)
					.withObjects(Arrays.asList(new ObjectSaveData()
							.withData(data)
							.withName(name)
							.withType(type))));
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, expected);
		}
	}

	@Test
	public void encodings() throws Exception {
		long wsid = CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("encodings")).getE1();

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


		//13 bytes in utf-8, 22 in 16, 40 in 32, byte -> file cutoff is 24
		String smallData = "{\"f\":\"" + sb.toString().substring(0, 3) + "\"}";
		@SuppressWarnings("unchecked")
		Map<String, Object> smallmapdata = MAPPER.readValue(smallData, Map.class);

		String s = sb.toString() + sb.toString();
		String data = "{\"" + s + "42\":[\"" + s + "\",\"" + s + "woot\",\"" +
																s + "\"]," +
					   "\"" + s + "6\":\"" + s + "\"," +
					   "\"" + s + "3012\":1}";
		@SuppressWarnings("unchecked")
		Map<String, Object> mapdata = MAPPER.readValue(data, Map.class);

		String req = "{\"method\":\"Workspace.save_objects\"," +
					  "\"version\":\"1.1\"," +
					  "\"id\":\"" + ("" + Math.random()).substring(2) + "\"," +
					  "\"params\":[{\"id\":" + wsid + "," +
								   "\"objects\": [{\"data\":%s," +
												  "\"type\":\"" + SAFE_TYPE + "\"," +
												  "\"name\":\"%s\"" +
												  "}" +
												 "]" +
								   "}" +
								  "]" +
					  "}";

		List<Charset> csets = Arrays.asList(Charset.forName("UTF-8"),
				Charset.forName("UTF-16LE"), Charset.forName("UTF-16BE"),
				Charset.forName("UTF-32LE"), Charset.forName("UTF-32BE"));

		for (String d: Arrays.asList(data, smallData)) {
			for (Charset cs: csets) {
				byte[] breq = String.format(req, d, getRandomName()).getBytes(cs);

				HttpURLConnection conn = (HttpURLConnection) CLIENT1.getURL()
						.openConnection();
				conn.setConnectTimeout(10000);
				conn.setDoOutput(true);
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Authorization",
						CLIENT1.getToken().getToken());
				conn.getOutputStream().write(breq);
				conn.getResponseCode();
				InputStream is = conn.getInputStream();
				int read = 1;
				while (read > -1) {
					read = is.read(breq);
				}
				is.close();
			}
		}
		for (long i = 1; i < 11; i++) {
			Map<String, Object> exp;
			if (i < 6) {
				exp = mapdata;
			} else {
				exp = smallmapdata;
			}
			Map<String, Object> ret = CLIENT1.getObjects2(new GetObjects2Params()
					.withObjects(
					Arrays.asList(new ObjectSpecification().withWsid(wsid)
							.withObjid(i)))).getData().get(0).getData().asInstance();
			assertThat("Got correct object back with sending various byte encodings to server",
					ret, is(exp));
		}
	}

	@SuppressWarnings("deprecation")
	@Test
	public void deprecatedMethods() throws Exception {
		CLIENT1.requestModuleOwnership("DepAnotherModule");
		administerCommand(CLIENT2, "approveModRequest", "module", "DepAnotherModule");
		CLIENT1.registerTypespec(new RegisterTypespecParams().withDryrun(0L)
			.withNewTypes(Arrays.asList("AType"))
			.withSpec(
					"module DepAnotherModule {" +
						"/* @optional thing */" +
						"typedef structure {" +
							"string thing;" +
						"} AType;" +
					"};")
			);
		String anotherType = "DepAnotherModule.AType-0.1";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("depsave"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("depsave2")
				.withGlobalread("r"));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo = CLIENT1.getWorkspaceInfo(
				new WorkspaceIdentity().withWorkspace("depsave"));
		long wsid = wsinfo.getE1();
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("depsave")
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));

		checkDepWSMeta(new us.kbase.workspace.GetWorkspacemetaParams()
				.withWorkspace("depsave"),
				"depsave", USER1, wsinfo.getE4(), 0, "a", "n", wsid);
		checkDepWSMeta(new us.kbase.workspace.GetWorkspacemetaParams()
				.withId(wsid),
				"depsave", USER1, wsinfo.getE4(), 0, "a", "n", wsid);
		checkDepWSMeta(new us.kbase.workspace.GetWorkspacemetaParams()
				.withWorkspace("depsave").withAuth(TOKEN2),
				"depsave", USER1, wsinfo.getE4(), 0, "w", "n", wsid);

		Tuple7<String, String, String, Long, String, String, Long> wsmeta =
				CLIENT1.getWorkspacemeta(new us.kbase.workspace.GetWorkspacemetaParams().withWorkspace("depsave"));
		Tuple7<String, String, String, Long, String, String, Long> wsmeta2 =
				CLIENT1.getWorkspacemeta(new us.kbase.workspace.GetWorkspacemetaParams().withWorkspace("depsave2"));

		List<Tuple7<String, String, String, Long, String, String, Long>> emptyWS =
				new ArrayList<Tuple7<String,String,String,Long,String,String,Long>>();

		checkWSInfoListDep(CLIENT1.listWorkspaces(new us.kbase.workspace.ListWorkspacesParams()
				.withExcludeGlobal(1L)),
				Arrays.asList(wsmeta), Arrays.asList(wsmeta2));
		checkWSInfoListDep(CLIENT1.listWorkspaces(new us.kbase.workspace.ListWorkspacesParams()),
				Arrays.asList(wsmeta, wsmeta2), emptyWS);

		//save some objects to get
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> data2 = new HashMap<String, Object>();
		Map<String, String> meta = new HashMap<String, String>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		data2.put("fubar2", moredata);
		meta.put("metastuff", "meta");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("meta2", "my hovercraft is full of eels");

		Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> obj1 =
				CLIENT1.saveObject(new us.kbase.workspace.SaveObjectParams().withId("obj1")
				.withMetadata(meta).withType(SAFE_TYPE).withWorkspace("depsave")
				.withData(new UObject(data)));

		Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> obj2 =
				CLIENT1.saveObject(new us.kbase.workspace.SaveObjectParams().withId("obj2")
				.withMetadata(meta2).withType(anotherType).withWorkspace("depsave")
				.withData(new UObject(data2)));

		Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> obj3 =
				CLIENT1.saveObject(new us.kbase.workspace.SaveObjectParams().withId("obj3")
				.withMetadata(meta2).withType(SAFE_TYPE).withWorkspace("depsave")
				.withData(new UObject(data)).withAuth(TOKEN2));

		checkDeprecatedSaveInfo(obj1, 1, "obj1", SAFE_TYPE, 1, USER1, wsid, "depsave", "36c4f68f2c98971b9736839232eb08f4", meta);
		checkDeprecatedSaveInfo(obj2, 2, "obj2", anotherType, 1, USER1, wsid, "depsave", "3c59f762140806c36ab48a152f28e840", meta2);
		checkDeprecatedSaveInfo(obj3, 3, "obj3", SAFE_TYPE, 1, USER2, wsid, "depsave", "36c4f68f2c98971b9736839232eb08f4", meta2);

		checkSavedObjectDep(new ObjectIdentity().withWorkspace("depsave").withName("obj1"),
				new ObjectIdentity().withWsid(wsid).withObjid(1L),
				1, "obj1", SAFE_TYPE, 1, USER1, wsid, "depsave", "36c4f68f2c98971b9736839232eb08f4",
				23, meta, data, TOKEN2);
		checkSavedObjectDep(new ObjectIdentity().withWorkspace("depsave").withName("obj2"),
				new ObjectIdentity().withWsid(wsid).withObjid(2L),
				2, "obj2", anotherType, 1, USER1, wsid, "depsave", "3c59f762140806c36ab48a152f28e840",
				24, meta2, data2, TOKEN2);
		checkSavedObjectDep(new ObjectIdentity().withWorkspace("depsave").withName("obj3"),
				new ObjectIdentity().withWsid(wsid).withObjid(3L),
				3, "obj3", SAFE_TYPE, 1, USER2, wsid, "depsave", "36c4f68f2c98971b9736839232eb08f4",
				23, meta2, data, TOKEN2);

		checkListObjectsDep("depsave", null, null, null, Arrays.asList(obj1, obj2, obj3));
		checkListObjectsDep("depsave", anotherType, null, null, Arrays.asList(obj2));
		CLIENT1.deleteObjects(Arrays.asList(new ObjectIdentity().withName("obj2").withWorkspace("depsave")));
		checkListObjectsDep("depsave", null, 0L, null, Arrays.asList(obj1, obj3));
		checkListObjectsDep("depsave", null, 1L, null, Arrays.asList(obj1, obj2, obj3));
		checkListObjectsDep("depsave", null, null, TOKEN2, Arrays.asList(obj1, obj3));

		String invalidToken = TOKEN2 + "a";
		String badFormatToken = "borkborkbork";
		String invalidTokenExp =
				"Auth service returned an error: 10020 Invalid token";


		failDepGetWSmeta(new us.kbase.workspace.GetWorkspacemetaParams()
				.withWorkspace("depsave").withAuth(invalidToken),
				invalidTokenExp);
		failDepGetWSmeta(new us.kbase.workspace.GetWorkspacemetaParams()
				.withWorkspace("depsave").withAuth(badFormatToken),
				invalidTokenExp);

		failDepListWs(new us.kbase.workspace.ListWorkspacesParams()
				.withAuth(invalidToken), invalidTokenExp);
		failDepListWs(new us.kbase.workspace.ListWorkspacesParams()
				.withAuth(badFormatToken), invalidTokenExp);

		failDepSaveObject(new us.kbase.workspace.SaveObjectParams().withId("obj3")
				.withMetadata(meta2).withType(SAFE_TYPE).withWorkspace("depsave")
				.withData(new UObject(data)).withAuth(invalidToken),
				invalidTokenExp);
		failDepSaveObject(new us.kbase.workspace.SaveObjectParams().withId("obj3")
				.withMetadata(meta2).withType(SAFE_TYPE).withWorkspace("depsave")
				.withData(new UObject(data)).withAuth(badFormatToken),
				invalidTokenExp);

		failDepGetObject(new us.kbase.workspace.GetObjectParams()
				.withWorkspace("depsave").withId("obj3").withAuth(invalidToken),
				invalidTokenExp);
		failDepGetObject(new us.kbase.workspace.GetObjectParams()
				.withWorkspace("depsave").withId("obj3").withAuth(badFormatToken),
				invalidTokenExp);

		failDepGetObjectmeta(new us.kbase.workspace.GetObjectmetaParams()
				.withWorkspace("depsave").withId("obj3").withAuth(invalidToken),
				invalidTokenExp);
		failDepGetObjectmeta(new us.kbase.workspace.GetObjectmetaParams()
				.withWorkspace("depsave").withId("obj3").withAuth(badFormatToken),
				invalidTokenExp);

		failDepListObjects(new us.kbase.workspace.ListWorkspaceObjectsParams()
				.withWorkspace("depsave").withType("thisisabadtype"),
				"Type thisisabadtype could not be split into a module and name");
		failDepListObjects(new us.kbase.workspace.ListWorkspaceObjectsParams()
				.withWorkspace("depsave").withAuth(invalidToken),
				invalidTokenExp);
		failDepListObjects(new us.kbase.workspace.ListWorkspaceObjectsParams()
				.withWorkspace("depsave").withAuth(badFormatToken),
				invalidTokenExp);
	}

	@SuppressWarnings("deprecation")
	private void failDepListObjects(us.kbase.workspace.ListWorkspaceObjectsParams lwop,
			String exp)
			throws Exception {
		try {
			CLIENT1.listWorkspaceObjects(lwop);
			fail("list objs dep with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}

	@SuppressWarnings("deprecation")
	private void checkListObjectsDep(String ws, String type, Long showDeleted, String auth,
			List<Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long>> expected)
			throws Exception {
		Map<Long, Map<Long, Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long>>> expec =
				new HashMap<Long, Map<Long, Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long>>>();

		Map<Long, Set<Long>> seenSet = new HashMap<Long, Set<Long>>();
		Map<Long, Set<Long>> expectedSet = new HashMap<Long, Set<Long>>();

		for (Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> e: expected) {
			if (!expec.containsKey(e.getE12())) {
				expec.put(e.getE12(), new HashMap<Long, Tuple12<String,String,String,Long,String,String,String,String,String,String,Map<String,String>,Long>>());
				expectedSet.put(e.getE12(), new HashSet<Long>());
			}
			expec.get(e.getE12()).put(e.getE4(), e);
			expectedSet.get(e.getE12()).add(e.getE4());
		}
		for (Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> g:
			CLIENT1.listWorkspaceObjects(new us.kbase.workspace.ListWorkspaceObjectsParams().withWorkspace(ws)
					 .withType(type).withShowDeletedObject(showDeleted).withAuth(auth))) {
			if (seenSet.containsKey(g.getE12()) &&
					seenSet.get(g.getE12()).contains(g.getE4())) {
				fail("Saw same object twice: " + g);
			}
			if (!seenSet.containsKey(g.getE12())) {
				seenSet.put(g.getE12(), new HashSet<Long>());
			}
			seenSet.get(g.getE12()).add(g.getE4());
			if (!expec.containsKey(g.getE12()) ||
					!expec.get(g.getE12()).containsKey(g.getE4())) {
				fail("listed unexpected object: " + g);
			}
			compareObjectInfoDep(g, expec.get(g.getE12()).get(g.getE4()));
		}
		assertThat("listed correct objects", seenSet, is (expectedSet));
	}

	private void compareObjectInfoDep(
			Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> got,
			Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> expec) {

		assertThat("name is correct", got.getE1(), is(expec.getE1()));
		assertThat("type is correct", got.getE2(), is(expec.getE2()));
		assertThat("date is correct", got.getE3(), is(expec.getE3()));
		assertThat("version is correct", got.getE4(), is(expec.getE4()));
		assertThat("command is correct", got.getE5(), is(expec.getE5()));
		assertThat("last modifier is correct", got.getE6(), is(expec.getE6()));
		assertThat("owner is correct", got.getE7(), is(expec.getE7()));
		assertThat("ws name is correct", got.getE8(), is(expec.getE8()));
		assertThat("ref is correct", got.getE9(), is(expec.getE9()));
		assertThat("chksum is correct", got.getE10(), is(expec.getE10()));
		assertThat("meta is correct", got.getE11(), is(expec.getE11()));
		assertThat("id is correct", got.getE12(), is(expec.getE12()));
	}

	@SuppressWarnings("deprecation")
	private void failDepListWs(us.kbase.workspace.ListWorkspacesParams lwp, String exp)
			throws Exception {
		try {
			CLIENT1.listWorkspaces(lwp);
			fail("get objmeta dep with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}

	private void checkWSInfoListDep(
			List<Tuple7<String, String, String, Long, String, String, Long>> got,
			List<Tuple7<String, String, String, Long, String, String, Long>> expected,
			List<Tuple7<String, String, String, Long, String, String, Long>> notexpected) {

		Map<Long, Tuple7<String, String, String, Long, String, String, Long>> expecmap =
				new HashMap<Long, Tuple7<String, String, String, Long, String, String, Long>>();
		for (Tuple7<String, String, String, Long, String, String, Long> inf: expected) {
			expecmap.put(inf.getE7(), inf);
		}
		Set<Long> seen = new HashSet<Long>();
		Set<Long> seenexp = new HashSet<Long>();
		Set<Long> notexp = new HashSet<Long>();
		for (Tuple7<String, String, String, Long, String, String, Long> inf: notexpected) {
			notexp.add(inf.getE7());
		}
		for (Tuple7<String, String, String, Long, String, String, Long> info: got) {
			if (seen.contains(info.getE7())) {
				fail("Saw same workspace twice");
			}
			if (notexp.contains(info.getE7())) {
				fail("Got unexpected workspace id " + info.getE1());
			}
			if (!expecmap.containsKey(info.getE7())) {
				continue; // only two users so really impossible to list a controlled set of ws
				// if this is important add a 3rd user and client
			}
			seenexp.add(info.getE7());
			Tuple7<String, String, String, Long, String, String, Long> exp =
					expecmap.get(info.getE7());
			assertThat("ws name correct", info.getE1(), is(exp.getE1()));
			assertThat("user name correct", info.getE2(), is(exp.getE2()));
			assertThat("moddates correct", info.getE3(), is(exp.getE3()));
			assertThat("obj counts are 0", info.getE4(), is(exp.getE4()));
			assertThat("permission correct", info.getE5(), is(exp.getE5()));
			assertThat("global read correct", info.getE6(), is(exp.getE6()));
			assertThat("wsid correct", info.getE7(), is(exp.getE7()));

		}
		assertThat("got same ws ids", seenexp, is(expecmap.keySet()));

	}

	@SuppressWarnings("deprecation")
	private void checkDepWSMeta(
			us.kbase.workspace.GetWorkspacemetaParams gomp,
			String name, String user, String moddate, long objects, String perm,
			String globalRead, long wsid)
			throws Exception {
		Tuple7<String, String, String, Long, String, String, Long> wsmeta =
				CLIENT1.getWorkspacemeta(gomp);
		assertThat("ws name correct", wsmeta.getE1(), is(name));
		assertThat("user name correct", wsmeta.getE2(), is(user));
		assertThat("moddates correct", wsmeta.getE3(), is(moddate));
		assertThat("obj counts are 0", wsmeta.getE4(), is(objects));
		assertThat("permission correct", wsmeta.getE5(), is(perm));
		assertThat("global read correct", wsmeta.getE6(), is(globalRead));
		assertThat("wsid correct", wsmeta.getE7(), is(wsid));

	}

	@SuppressWarnings("deprecation")
	private void failDepGetObjectmeta(us.kbase.workspace.GetObjectmetaParams gop, String exp)
			throws Exception {
		try {
			CLIENT1.getObjectmeta(gop);
			fail("get objmeta dep with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}

	@SuppressWarnings("deprecation")
	private void failDepGetWSmeta(us.kbase.workspace.GetWorkspacemetaParams gwp, String exp)
			throws Exception {
		try {
			CLIENT1.getWorkspacemeta(gwp);
			fail("get wsmeta dep with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}

	@SuppressWarnings("deprecation")
	private void failDepGetObject(us.kbase.workspace.GetObjectParams gop, String exp)
			throws Exception {
		try {
			CLIENT1.getObject(gop);
			fail("get obj dep with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}

	@SuppressWarnings("deprecation")
	private void checkSavedObjectDep(ObjectIdentity objnames, ObjectIdentity objids,
			final long id,
			final String name,
			final String type,
			final int ver,
			final String user,
			final long wsid,
			final String wsname,
			final String chksum,
			final int size,
			final Map<String, String> meta,
			final Map<String, Object> data,
			final String token)
			throws Exception {
		us.kbase.workspace.GetObjectOutput goo = CLIENT1.getObject(new us.kbase.workspace.GetObjectParams()
				.withId(objnames.getName()).withWorkspace(objnames.getWorkspace())
				.withInstance(objnames.getVer()));
		checkDeprecatedSaveInfo(goo.getMetadata(), id, name, type, ver, user,
				wsid, wsname, chksum, meta);
		assertThat("object data is correct", goo.getData().asClassInstance(Object.class),
				is((Object) data));
		goo = CLIENT1.getObject(new us.kbase.workspace.GetObjectParams()
				.withId(objnames.getName()).withWorkspace(objnames.getWorkspace())
				.withInstance(objnames.getVer())
				.withAuth(token));
		checkDeprecatedSaveInfo(goo.getMetadata(), id, name, type, ver, user,
				wsid, wsname, chksum, meta);
		assertThat("object data is correct", goo.getData().asClassInstance(Object.class),
				is((Object) data));

		Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> objmeta =
				CLIENT1.getObjectmeta(new us.kbase.workspace.GetObjectmetaParams()
				.withWorkspace(objnames.getWorkspace())
				.withId(objnames.getName()).withInstance(objnames.getVer()));
		checkDeprecatedSaveInfo(objmeta, id, name, type, ver, user,
				wsid, wsname, chksum, meta);
		objmeta =
				CLIENT1.getObjectmeta(new us.kbase.workspace.GetObjectmetaParams()
				.withWorkspace(objnames.getWorkspace())
				.withId(objnames.getName()).withInstance(objnames.getVer())
				.withAuth(TOKEN2));
		checkDeprecatedSaveInfo(objmeta, id, name, type, ver, user,
				wsid, wsname, chksum, meta);

		checkSavedObjects(Arrays.asList(objnames), id, name, type, ver, user, wsid, wsname, chksum, size, meta, data);
		checkSavedObjects(Arrays.asList(objids), id, name, type, ver, user, wsid, wsname, chksum, size, meta, data);

	}

	@SuppressWarnings("deprecation")
	private void failDepSaveObject(us.kbase.workspace.SaveObjectParams sop, String exp)
			throws Exception {
		try {
			CLIENT1.saveObject(sop);
			fail("dep save obj with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is(exp));
		}
	}

	private void checkDeprecatedSaveInfo(
			Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String, String>, Long> info,
			long id, String name, String type, int ver, String user,
			long wsid, String wsname, String chksum,
			Map<String, String> meta) throws Exception {
		assertThat("name is correct", info.getE1(), is(name));
		assertThat("type is correct", info.getE2(), is(type));
		DATE_FORMAT.parse(info.getE3()); //should throw error if bad format
		assertThat("version is correct", (int) info.getE4().longValue(), is(ver));
		assertThat("command is correct", info.getE5(), is(""));
		assertThat("last modifier is correct", info.getE6(), is(user));
		assertThat("owner is correct", info.getE7(), is(user));
		assertThat("ws name is correct", info.getE8(), is(wsname));
		assertThat("ref is correct", info.getE9(), is(""));
		assertThat("chksum is correct", info.getE10(), is(chksum));
		assertThat("meta is correct", info.getE11(), is(meta));
		assertThat("id is correct", info.getE12(), is(id));

	}

	@Test
	public void metadataBig() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("bigmeta"));

		Map<String, Object> moredata = new HashMap<String, Object>();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, String> meta = new HashMap<String, String>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		for (int i = 0; i < 18; i++) {
			meta.put("" + i, TEXT1000.substring(103)); //> 16Mb now
		}


		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("bigmeta")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data)).withType(SAFE_TYPE)
				.withName("foo"));
		objects.add(new ObjectSaveData().withData(new UObject(data)).withName("bar")
				.withType(SAFE_TYPE).withMeta(meta));

		try {
			CLIENT1.saveObjects(soc);
			fail("called save with too large meta");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Object #2, bar: Metadata exceeds maximum of 16000B"));
		}
		try {
			CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("bigmeta2")
					.withMeta(meta));
			fail("called createWS with too large meta");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Metadata exceeds maximum of 16000B"));
		}
	}

	@Test
	public void parseRef() throws Exception {
		final String specParseRef =
				"module TestKBaseRefParsing {" +
					"/* @id ws */" +
					"typedef string reference;" +
					"/* @optional ref5\n */" +
					"typedef structure {" +
						"reference ref1;" +
						"reference ref2;" +
						"reference ref3;" +
						"reference ref4;" +
						"reference ref5;" +
					"} ParseRef;" +
				"};";
		CLIENT1.requestModuleOwnership("TestKBaseRefParsing");
		administerCommand(CLIENT2, "approveModRequest", "module", "TestKBaseRefParsing");
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec(specParseRef)
			.withNewTypes(Arrays.asList("ParseRef")));
		String type ="TestKBaseRefParsing.ParseRef-0.1";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("parseref"));
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("parseref");
		long wsid = CLIENT1.getWorkspaceInfo(wsi).getE1();
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("fubar", "foo");
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("parseref")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("auto2"));
		CLIENT1.saveObjects(soc);
		data.clear();
		Set<String> expectedRefs = new HashSet<String>();
		data.put("ref1", wsid + "/1");
		expectedRefs.add(wsid + "/1/3");
		data.put("ref2", wsid + "/1/2");
		expectedRefs.add(wsid + "/1/2");
		data.put("ref3", wsid + "/2");
		expectedRefs.add(wsid + "/2/1");
		data.put("ref4", wsid + "/2/1");
		expectedRefs.add(wsid + "/2/1");
		objects.clear();
		objects.add(new ObjectSaveData().withData(new UObject(data)).withName("auto3")
				.withType(type));
		CLIENT1.saveObjects(soc);
		ObjectData od = CLIENT1.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(
				new ObjectSpecification().withWsid(wsid).withName("auto3"))))
				.getData().get(0);
		Map<String, String> refs = od.getData().asInstance();
		assertThat("correct ref parse/rewrite", refs.get("ref1"), is(wsid + "/1/3"));
		assertThat("correct ref parse/rewrite", refs.get("ref2"), is(wsid + "/1/2"));
		assertThat("correct ref parse/rewrite", refs.get("ref3"), is(wsid + "/2/1"));
		assertThat("correct ref parse/rewrite", refs.get("ref4"), is(wsid + "/2/1"));
		assertThat("correct refs returned", new HashSet<String>(od.getRefs()),
				is(expectedRefs));

		data.put("ref5", wsid + "/3");
		assertThat("test param hasn't changed", MAX_UNIQUE_IDS_PER_CALL, is(4));
		try {
			CLIENT1.saveObjects(soc);
			fail("saved object with too many refs");
		} catch (ServerException se) {
			assertThat("correct exception", se.getMessage(),
					is("Object #1, auto3 failed type checking - the number of unique IDs in the "
						+ "saved objects exceeds the maximum allowed, 4"));
		}

	}

	@Test
	public void deleteUndelete() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("del")
				.withDescription("foo"));
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("del");
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("del")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		CLIENT1.saveObjects(soc);
		List<ObjectIdentity> loi = Arrays.asList(new ObjectIdentity()
				.withRef("del/myname"));
		checkData(loi, data);
		CLIENT1.deleteObjects(loi);

		failGetObjects(loi, "Object 1 (name myname) in workspace 1 (name del) " +
				"has been deleted");

		CLIENT1.undeleteObjects(loi);
		checkData(loi, data);
		CLIENT1.deleteWorkspace(wsi);

		failGetObjects(loi, "Object myname cannot be accessed: Workspace del is deleted");

		failGetWSDesc(wsi, "Workspace del is deleted");
	}

	@Test
	public void copyRevert() throws Exception {
		long wsid = CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("copyrev")).getE1();
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("copyrev")
				.withNewPermission("r").withUsers(Arrays.asList(USER2)));
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("copyrev")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(moredata))
				.withType(SAFE_TYPE).withName("myname"));
		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> objs =
				CLIENT1.saveObjects(soc);

		ObjectIdentity nocopy = new ObjectIdentity().withWorkspace("copyrev").withName("myname");
		checkObjectCopy(CLIENT1, nocopy, null, 0L);

		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> copied =
				CLIENT1.copyObject(new CopyObjectParams().withFrom(new ObjectIdentity().withRef("copyrev/myname"))
				.withTo(new ObjectIdentity().withWsid(wsid).withName("myname2")));
		compareObjectInfoAndData(objs.get(1), copied, "copyrev", wsid, "myname2", 2L, 2);
		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> copystack =
				CLIENT1.getObjectHistory(new ObjectIdentity().withWsid(wsid).withName("myname2"));
		compareObjectInfoAndData(objs.get(0), copystack.get(0), "copyrev", wsid, "myname2", 2L, 1);
		compareObjectInfoAndData(objs.get(1), copystack.get(1), "copyrev", wsid, "myname2", 2L, 2);

		//check copy visibility
		ObjectIdentity c = new ObjectIdentity().withRef("copyrev/myname2");
		String ref = objs.get(1).getE7() + "/" + objs.get(1).getE1() + "/" + objs.get(1).getE5();
		CLIENT1.deleteObjects(Arrays.asList(new ObjectIdentity().withRef("copyrev/myname")));
		checkObjectCopy(CLIENT2, c, null, 1L);
		checkObjectCopyAsAdmin(CLIENT2, c, ref, 0L);
		CLIENT1.undeleteObjects(Arrays.asList(new ObjectIdentity().withRef("copyrev/myname")));
		checkObjectCopy(CLIENT2, c, ref, 0L);

		//test revert
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> rev =
				CLIENT1.revertObject(new ObjectIdentity().withWorkspace("copyrev").withObjid(2L)
				.withVer(1L));
		compareObjectInfoAndData(objs.get(0), rev, "copyrev", wsid, "myname2", 2L, 3);
		copystack = CLIENT1.getObjectHistory(new ObjectIdentity().withWsid(wsid).withName("myname2"));
		compareObjectInfoAndData(objs.get(0), copystack.get(0), "copyrev", wsid, "myname2", 2L, 1);
		compareObjectInfoAndData(objs.get(1), copystack.get(1), "copyrev", wsid, "myname2", 2L, 2);
		compareObjectInfoAndData(objs.get(0), copystack.get(2), "copyrev", wsid, "myname2", 2L, 3);

		CopyObjectParams cpo = new CopyObjectParams().withFrom(new ObjectIdentity().withRef("copyrev/myname"))
				.withTo(new ObjectIdentity().withWsid(wsid).withName("myname2"));
		cpo.setAdditionalProperties("foo", "bar");
		try {
			CLIENT1.copyObject(cpo);
			fail("copied with bad params");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getLocalizedMessage(),
					is("Unexpected arguments in CopyObjectParams: foo"));
		}
	}

	@SuppressWarnings("deprecation")
	private void checkObjectCopy(
			final WorkspaceClient cli,
			final ObjectIdentity nocopy,
			final String ref,
			final long copyInvisible)
			throws Exception {

		final ObjectData objp = cli.getObjects2(new GetObjects2Params().withNoData(1L)
			.withObjects(toObjSpec(Arrays.asList(nocopy)))).getData().get(0);
		checkCopyRef(objp, ref, copyInvisible);
		assertNull("got unrequested data", objp.getData());
		final ObjectData objo = cli.getObjects2(new GetObjects2Params().withNoData(0L)
			.withObjects(toObjSpec(Arrays.asList(nocopy)))).getData().get(0);
		checkCopyRef(objo, ref, copyInvisible);
		ObjectData obj = cli.getObjects(Arrays.asList(nocopy)).get(0);
		checkCopyRef(obj, ref, copyInvisible);
		obj = cli.getObjectSubset(objIDToSubObjID(Arrays.asList(nocopy))).get(0);
		checkCopyRef(obj, ref, copyInvisible);
		final us.kbase.workspace.ObjectProvenanceInfo prov =
				cli.getObjectProvenance(Arrays.asList(nocopy)).get(0);
		assertThat("copy ref is correct", prov.getCopied(), is(ref));
		assertThat("copy vis is correct", prov.getCopySourceInaccessible(), is(copyInvisible));
	}

	private void checkCopyRef(final ObjectData objp, final String ref, final long copyInvisible) {
		assertThat("copy ref is correct", objp.getCopied(), is(ref));
		assertThat("copy vis is correct", objp.getCopySourceInaccessible(), is(copyInvisible));
	}

	private void checkObjectCopyAsAdmin(
			final WorkspaceClient cli,
			final ObjectIdentity nocopy,
			final String ref,
			final long copyInvisible)
			throws Exception {
		final GetObjects2Params gop = new GetObjects2Params()
						.withObjects(toObjSpec(Arrays.asList(nocopy)))
						.withNoData(1L);
		final ImmutableMap<String, Object> admincmd = ImmutableMap.of(
				"command", "getObjects",
				"params", gop
				);
		final ObjectData objp = CLIENT2.administer(new UObject(admincmd))
				.asClassInstance(GetObjects2Results.class).getData().get(0);
		checkCopyRef(objp, ref, copyInvisible);
		assertThat("got unrequested data", objp.getData(), is(nullValue()));

		gop.withNoData(0L);
		final ObjectData objp2 = CLIENT2.administer(new UObject(admincmd))
				.asClassInstance(GetObjects2Results.class).getData().get(0);
		checkCopyRef(objp2, ref, copyInvisible);
	}

	@Test
	public void cloneWorkspace() throws Exception {
		String source = "clonesource";
		WorkspaceIdentity wssrc = new WorkspaceIdentity().withWorkspace(source);

		long wsid = CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(source)).getE1();
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace(source)
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(moredata))
				.withType(SAFE_TYPE).withName("myname"));

		CLIENT1.saveObjects(soc);

		Map<String, String> meta = new HashMap<String, String>();
		meta.put("Bowhale", "the avenger");

		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo =
				CLIENT1.cloneWorkspace(new CloneWorkspaceParams().withDescription("a desc")
				.withGlobalread("r").withWorkspace("newclone").withWsi(wssrc)
				.withMeta(meta));
		checkWS(wsinfo, wsinfo.getE1(), wsinfo.getE4(), "newclone", USER1, 1, "a", "r", "unlocked", "a desc", meta);

		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> objs =
				CLIENT1.getObjectHistory(new ObjectIdentity().withWsid(wsid).withName("myname"));
		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> copystack =
				CLIENT1.getObjectHistory(new ObjectIdentity().withWsid(wsinfo.getE1()).withName("myname"));
		compareObjectInfoAndData(objs.get(0), copystack.get(0), "newclone", wsinfo.getE1(), "myname", 1L, 1);
		compareObjectInfoAndData(objs.get(1), copystack.get(1), "newclone", wsinfo.getE1(), "myname", 1L, 2);

		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo2 =
				CLIENT1.cloneWorkspace(new CloneWorkspaceParams()
					.withWorkspace("newclone2").withWsi(wssrc)
					.withExclude(new LinkedList<ObjectIdentity>()));
		checkWS(wsinfo2, wsinfo2.getE1(), wsinfo2.getE4(), "newclone2", USER1, 1, "a", "n", "unlocked", null, MT_META);

		wsinfo = CLIENT1.cloneWorkspace(new CloneWorkspaceParams()
					.withWorkspace("newclone3").withWsi(wssrc)
					.withExclude(Arrays.asList(new ObjectIdentity().withObjid(1L))));
		assertThat("object exist in excluded clone", wsinfo.getE5(), is(0L));

		CloneWorkspaceParams cpo = new CloneWorkspaceParams().withWsi(new WorkspaceIdentity().withWorkspace("newclone"))
				.withWorkspace("fake");
		cpo.setAdditionalProperties("foo", "bar");
		try {
			CLIENT1.cloneWorkspace(cpo);
			fail("cloned with bad params");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getLocalizedMessage(),
					is("Unexpected arguments in CloneWorkspaceParams: foo"));
		}

		cpo = new CloneWorkspaceParams().withWsi(new WorkspaceIdentity().withWorkspace("newclone"))
				.withWorkspace("fake");
		try {
			CLIENT1.cloneWorkspace(cpo.withGlobalread("w"));
			fail("cloned with bad params");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getLocalizedMessage(),
					is("globalread must be n or r"));
		}

		cpo = new CloneWorkspaceParams().withWsi(new WorkspaceIdentity()
				.withWorkspace("newclone"))
				.withExclude(Arrays.asList(new ObjectIdentity().withName("bar"),
						new ObjectIdentity().withName("foo")
						.withObjid(1L)));
		try {
			CLIENT1.cloneWorkspace(cpo);
			fail("cloned with bad params");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getLocalizedMessage(),
					is("Error with excluded object #2: Must provide one and only one of object name (was: foo) or id (was: 1)"));
		}
	}

	@Test
	public void lockWorkspace() throws Exception {
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("Go to Spain", "there are millions of them");
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("lock");
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("lock")
						.withMeta(meta));
		long wsid = info.getE1();
		List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("lock")
				.withObjects(objects);
		objects.add(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("myname"));
		objects.add(new ObjectSaveData().withData(new UObject(moredata))
				.withType(SAFE_TYPE).withName("myname"));

		CLIENT1.saveObjects(soc);
		info = CLIENT1.getWorkspaceInfo(wsi); //saving changes the date

		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> lockinfo =
				CLIENT1.lockWorkspace(wsi);
		checkWS(lockinfo, wsid, info.getE4(), "lock", USER1, 1, "a", "n", "locked", null, meta);
		try {
			CLIENT1.setWorkspaceDescription(new SetWorkspaceDescriptionParams().withDescription("foo")
					.withWorkspace("lock"));
			fail("locked with bad params");
		} catch (ServerException se) {
			assertThat("correct exception msg", se.getLocalizedMessage(),
					is("The workspace with id " + wsid +
							", name lock, is locked and may not be modified"));
		}
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams().withWorkspace("lock")
				.withNewPermission("r"));
		checkWS(CLIENT1.getWorkspaceInfo(wsi), wsid, info.getE4(), "lock",
				USER1, 1, "a", "r", "published", null, meta);
	}

	@Test
	public void renameObject() throws Exception {
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("renameObj"));
		long wsid = wsinfo.getE1();
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("renameObj")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withType(SAFE_TYPE).withName("myname")));
		CLIENT1.saveObjects(soc);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> info =
				CLIENT1.renameObject(new RenameObjectParams().withNewName("mynewname")
				.withObj(new ObjectIdentity().withRef("renameObj/1")));
		checkInfo(info, 1, "mynewname", SAFE_TYPE, 1, USER1, wsid, "renameObj", "99914b932bd37a50b983c5e7c90ae93b", 2, null);
		info = CLIENT1.getObjectInfo3(new GetObjectInfo3Params().withObjects(
				Arrays.asList(new ObjectSpecification().withWorkspace("renameObj").withObjid(1L))))
				.getInfos().get(0);
		checkInfo(info, 1, "mynewname", SAFE_TYPE, 1, USER1, wsid, "renameObj", "99914b932bd37a50b983c5e7c90ae93b", 2, null);
		RenameObjectParams rop = new RenameObjectParams().withNewName("mynewname2")
				.withObj(new ObjectIdentity().withRef("renameObj/1"));
		rop.setAdditionalProperties("foo", "bar");
		failObjRename(rop, "Unexpected arguments in RenameObjectParams: foo");
		failObjRename(new RenameObjectParams().withNewName("foo")
				.withObj(new ObjectIdentity().withName("foo")),
				"Must provide one and only one of workspace name (was: null) or id (was: null)");
	}

	@Test
	public void renameWorkspace() throws Exception {
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("pimhole", "semprini");
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("renameWS")
						.withMeta(meta));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo2 =
				CLIENT1.renameWorkspace(new RenameWorkspaceParams().withWsi(
				new WorkspaceIdentity().withWorkspace("renameWS")).withNewName("newrenameWS"));
		checkWS(wsinfo2, wsinfo.getE1(), wsinfo2.getE4(), "newrenameWS", USER1,
				0, "a", "n", "unlocked", null, meta);
		wsinfo2 = CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("newrenameWS"));
		checkWS(wsinfo2, wsinfo.getE1(), wsinfo2.getE4(), "newrenameWS", USER1,
				0, "a", "n", "unlocked", null, meta);
		RenameWorkspaceParams rwp = new RenameWorkspaceParams()
				.withWsi(new WorkspaceIdentity().withWorkspace("newrenameWS"))
				.withNewName("foo");
		rwp.setAdditionalProperties("foo", "bar");
		failWSRename(rwp, "Unexpected arguments in RenameWorkspaceParams: foo");
		failWSRename(new RenameWorkspaceParams().withWsi(new WorkspaceIdentity()
				.withWorkspace("newrenameWS")), "Workspace name cannot be null or the empty string");
	}

	@Test
	public void setGlobalPermission() throws Exception {
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("setglobal"));
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("setglobal");
		assertThat("globalread is none", wsinfo.getE7(), is("n"));
		try {
			CLIENT2.getWorkspaceDescription(wsi);
			fail("got workspace desc w/o access");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("User " + USER2 + " may not read workspace setglobal"));
		}

		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams().withWorkspace("setglobal")
				.withNewPermission("r"));
		CLIENT2.getWorkspaceDescription(wsi);
		assertThat("globalread is r", CLIENT1.getWorkspaceInfo(wsi).getE7(), is("r"));
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams().withWorkspace("setglobal")
				.withNewPermission("n"));
		assertThat("globalread is r", CLIENT1.getWorkspaceInfo(wsi).getE7(), is("n"));
		SetGlobalPermissionsParams sgpp = new SetGlobalPermissionsParams()
				.withWorkspace("setglobal").withNewPermission("r");
		sgpp.setAdditionalProperties("bar", "foo");
		failSetGlobalPerm(sgpp, "Unexpected arguments in SetGlobalPermissionsParams: bar");
		SetGlobalPermissionsParams sgppgen = new SetGlobalPermissionsParams()
				.withWorkspace("setglobal");
		failSetGlobalPerm(sgppgen.withNewPermission("w"),
				"Global permissions cannot be greater than read");
		failSetGlobalPerm(sgppgen.withNewPermission("z"),
				"No such permission: z");
		failSetGlobalPerm(sgppgen.withNewPermission("r").withId(wsinfo.getE1()),
				"Must provide one and only one of workspace name (was: setglobal) or id (was: " +
				wsinfo.getE1() + ")");
	}

	@Test
	public void hiddenObjects() throws Exception {
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("hideObj"));
		long wsid = wsinfo.getE1();
		SaveObjectsParams soc = new SaveObjectsParams().withWorkspace("hideObj")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withType(SAFE_TYPE).withName("unhidden")));
		ObjectIdentity o1 = new ObjectIdentity().withRef("hideObj/1");
		CLIENT1.saveObjects(soc);
		soc = new SaveObjectsParams().withWorkspace("hideObj")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withType(SAFE_TYPE).withName("hidden").withHidden(1L)));
		ObjectIdentity o2 = new ObjectIdentity().withWorkspace("hideObj").withName("hidden");
		CLIENT1.saveObjects(soc);

		Set<Long> expected = new HashSet<Long>();
		expected.add(1L);
		checkExpectedObjNums(CLIENT1.listObjects(new ListObjectsParams().withIds(Arrays.asList(wsid))), expected);
		expected.add(2L);
		checkExpectedObjNums(CLIENT1.listObjects(new ListObjectsParams().withIds(Arrays.asList(wsid)).withShowHidden(1L)), expected);
		CLIENT1.unhideObjects(Arrays.asList(o2));
		checkExpectedObjNums(CLIENT1.listObjects(new ListObjectsParams().withIds(Arrays.asList(wsid))), expected);
		CLIENT1.hideObjects(Arrays.asList(o1));
		expected.remove(1L);
		checkExpectedObjNums(CLIENT1.listObjects(new ListObjectsParams().withIds(Arrays.asList(wsid))), expected);
		expected.add(1L);
		checkExpectedObjNums(CLIENT1.listObjects(new ListObjectsParams().withIds(Arrays.asList(wsid)).withShowHidden(1L)), expected);

		ObjectIdentity badoi = new ObjectIdentity().withWorkspace("hideObj").withName("hidden");
		badoi.setAdditionalProperties("urg", "bleah");

		failHideUnHide(badoi, "Error on ObjectIdentity #1: Unexpected arguments in ObjectIdentity: urg");
		failHideUnHide(new ObjectIdentity().withWorkspace("hideObj"),
				"Error on ObjectIdentity #1: Must provide one and only one of object name (was: null) or id (was: null)");
		failHideUnHide(new ObjectIdentity().withWorkspace("hideObj").withName("wootwoot"),
				"No object with name wootwoot exists in workspace 1 (name hideObj)");
	}

	@Test
	public void listWorkspaceInfo() throws Exception {

		Map<String, String> meta = new HashMap<String, String>();
		meta.put("credulous", "git");

		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("Flanders", "pidgeon murderer");

		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> std =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("liststd")
						.withMeta(meta));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("listglobalread")
				.withGlobalread("r"));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> globalread =
				CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("listglobalread"));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> deleted =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("listdeleted"));
		CLIENT1.deleteWorkspace(new WorkspaceIdentity().withWorkspace("listdeleted"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("listwrite")
				.withMeta(meta2));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("listwrite")
				.withNewPermission("w").withUsers(Arrays.asList(USER1)));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> write =
				CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("listwrite"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("listadmin"));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("listadmin")
				.withNewPermission("a").withUsers(Arrays.asList(USER1)));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> admin =
				CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("listadmin"));

		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()),
				Arrays.asList(std, globalread, write, admin));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(0L).withShowDeleted(0L).withShowOnlyDeleted(0L)),
				Arrays.asList(std, globalread, write, admin));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(0L).withShowDeleted(0L).withShowOnlyDeleted(0L)
				.withOwners(new ArrayList<String>())),
				Arrays.asList(std, globalread, write, admin));

		//filter on meta
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withMeta(meta)),
				Arrays.asList(std));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withMeta(meta2)),
				Arrays.asList(write));

		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withOwners(Arrays.asList(USER1))),
				Arrays.asList(std));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withOwners(Arrays.asList(USER2))),
				Arrays.asList(globalread, write, admin));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withOwners(Arrays.asList(USER1, USER2))),
				Arrays.asList(std, globalread, write, admin));

		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
					.withPerm("n")),
				Arrays.asList(std, globalread, write, admin));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withPerm("r")),
				Arrays.asList(std, globalread, write, admin));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withPerm("w")),
				Arrays.asList(std, write, admin));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withPerm("a")),
				Arrays.asList(std, admin));

		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(1L)),
				Arrays.asList(std, write, admin));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(1L).withShowDeleted(0L)),
				Arrays.asList(std, write, admin));

		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(1L).withShowDeleted(1L)),
				Arrays.asList(std, deleted, write, admin));

		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withShowDeleted(1L)),
				Arrays.asList(std, deleted, globalread, write, admin));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(0L).withShowDeleted(1L)),
				Arrays.asList(std, deleted, globalread, write, admin));

		checkWSInfoList(CLIENT2.listWorkspaceInfo(new ListWorkspaceInfoParams()),
				Arrays.asList(CLIENT2.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("listglobalread")),
						CLIENT2.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("listwrite")),
						CLIENT2.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace("listadmin"))));

		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(0L).withShowOnlyDeleted(1L)),
				Arrays.asList(deleted));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withExcludeGlobal(1L).withShowOnlyDeleted(1L)),
				Arrays.asList(deleted));
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withShowDeleted(1L).withShowOnlyDeleted(1L)),
				Arrays.asList(deleted));

		ListWorkspaceInfoParams lwip = new ListWorkspaceInfoParams();
		lwip.setAdditionalProperties("booga", "booga1");
		try {
			CLIENT1.listWorkspaceInfo(lwip);
			fail("list ws with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("Unexpected arguments in ListWorkspaceInfoParams: booga"));
		}
	}

	@Test
	public void listWorkspaceInfoByDate() throws Exception {
		List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> mt =
				new ArrayList<Tuple9<Long,String,String,String,Long,String,String,String,Map<String,String>>>();

		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> w1 =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("listWSByDate1"));
		Thread.sleep(2000);
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> w2 =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("listWSByDate2"));
		Thread.sleep(2000);
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> w3 =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("listWSByDate3"));
		long beforeallEpoch = subSec(w1.getE4()); //max res is 1s
		long afterallEpoch = addSec(w3.getE4());
		String beforeall = DATE_FORMAT.format(beforeallEpoch);
		String afterall = DATE_FORMAT.format(afterallEpoch);

		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)),
				Arrays.asList(w1, w2, w3), true);
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)
				.withAfter(beforeall).withBefore(afterall)),
				Arrays.asList(w1, w2, w3), true);
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)
				.withAfterEpoch(beforeallEpoch).withBeforeEpoch(afterallEpoch)),
				Arrays.asList(w1, w2, w3), true);
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)
				.withAfter(afterall).withBefore(beforeall)),
				mt, true);
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)
				.withAfterEpoch(afterallEpoch).withBeforeEpoch(beforeallEpoch)),
				mt, true);
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)
				.withAfter(DATE_FORMAT.format(addSec(w1.getE4())))),
				Arrays.asList(w2, w3), true);
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)
				.withAfterEpoch(addSec(w1.getE4()))),
				Arrays.asList(w2, w3), true);
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)
				.withBefore(DATE_FORMAT.format(subSec(w3.getE4())))),
				Arrays.asList(w1, w2), true);
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)
				.withBeforeEpoch(subSec(w3.getE4()))),
				Arrays.asList(w1, w2), true);
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)
				.withAfter(DATE_FORMAT.format(addSec(w1.getE4())))
				.withBefore(DATE_FORMAT.format(subSec(w3.getE4())))),
				Arrays.asList(w2), true);
		checkWSInfoList(CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams().withExcludeGlobal(1L)
				.withAfterEpoch(addSec(w1.getE4()))
				.withBeforeEpoch(subSec(w3.getE4()))),
				Arrays.asList(w2), true);

		failListWorkspaceByDate("crappy date",
				"Unparseable date: Text 'crappy date' could not be parsed at index 0");
		try {
			CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withAfter(beforeall).withAfterEpoch(1L));
			fail("Ran list ws with time & epoch");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Cannot specify both timestamp and epoch for after " +
							"parameter"));
		}
		try {
			CLIENT1.listWorkspaceInfo(new ListWorkspaceInfoParams()
				.withBefore(beforeall).withBeforeEpoch(1L));
			fail("Ran list ws with time & epoch");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Cannot specify both timestamp and epoch for before " +
							"parameter"));
		}
	}

	@Test
	public void listWorkspaceIDs() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("own"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("admin"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("write"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("read"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("pub")
				.withGlobalread("r"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("none"));

		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("admin")
				.withUsers(Arrays.asList(USER1)).withNewPermission("a"));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("write")
				.withUsers(Arrays.asList(USER1)).withNewPermission("w"));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("read")
				.withUsers(Arrays.asList(USER1)).withNewPermission("r"));

		checkListWSIDs(CLIENT1.listWorkspaceIds(new ListWorkspaceIDsParams()),
				Arrays.asList(1L, 2L, 3L, 4L), Arrays.asList());

		checkListWSIDs(CLIENT1.listWorkspaceIds(new ListWorkspaceIDsParams()
				.withPerm("r")),
				Arrays.asList(1L, 2L, 3L, 4L), Arrays.asList());

		checkListWSIDs(CLIENT1.listWorkspaceIds(new ListWorkspaceIDsParams()
				.withExcludeGlobal(0L)),
				Arrays.asList(1L, 2L, 3L, 4L), Arrays.asList(5L));

		checkListWSIDs(CLIENT1.listWorkspaceIds(new ListWorkspaceIDsParams()
				.withOnlyGlobal(1L)),
				Arrays.asList(), Arrays.asList(5L));

		checkListWSIDs(CLIENT_NO_AUTH.listWorkspaceIds(new ListWorkspaceIDsParams()),
				Arrays.asList(), Arrays.asList());

		checkListWSIDs(CLIENT_NO_AUTH.listWorkspaceIds(new ListWorkspaceIDsParams()
				.withExcludeGlobal(0L)),
				Arrays.asList(), Arrays.asList(5L));

		checkListWSIDs(CLIENT1.listWorkspaceIds(new ListWorkspaceIDsParams()
				.withPerm("a")),
				Arrays.asList(1L, 2L), Arrays.asList());
	}

	@Test
	public void failListWorkspaceIDs() throws Exception {
		final ListWorkspaceIDsParams p = new ListWorkspaceIDsParams();
		p.setAdditionalProperties("foo", "bar");
		try {
			CLIENT1.listWorkspaceIds(p);
			fail("expected exception");
		} catch (ServerException se) {
			assertThat("incorrect message", se.getMessage(), is(
					"Unexpected arguments in ListWorkspaceIDsParams: foo"));
		}
	}

	private void checkListWSIDs(
			final ListWorkspaceIDsResults ids,
			final List<Long> workspaces,
			final List<Long> pub) {
		assertThat("incorrect workspace ids", ids.getWorkspaces(), is(workspaces));
		assertThat("incorrect pub ids", ids.getPub(), is(pub));
	}

	@Test
	public void listObjectsAndHistory() throws Exception {
		CLIENT1.requestModuleOwnership("AnotherModule");
		administerCommand(CLIENT2, "approveModRequest", "module", "AnotherModule");
		CLIENT1.registerTypespec(new RegisterTypespecParams().withDryrun(0L)
			.withNewTypes(Arrays.asList("AType"))
			.withSpec(
					"module AnotherModule {" +
						"/* @optional thing */" +
						"typedef structure {" +
						"string thing;" +
						"} AType;" +
					"};")
			);
		CLIENT1.releaseModule("AnotherModule");
		CLIENT1.requestModuleOwnership("AnotherModule2");
		administerCommand(CLIENT2, "approveModRequest", "module", "AnotherModule2");
		CLIENT1.registerTypespec(new RegisterTypespecParams().withDryrun(0L)
			.withNewTypes(Arrays.asList("AType"))
			.withSpec(
					"module AnotherModule2 {" +
						"/* @optional thing */" +
						"typedef structure {" +
						"string thing;" +
						"} AType;" +
					"};")
			);
		CLIENT1.releaseModule("AnotherModule2");

		String anotherType = "AnotherModule.AType-0.1";
		String anotherType2 = "AnotherModule2.AType-0.1";

		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info1 =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("listObjs1"));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info2 =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("listObjs2"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("listObjsread"));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("listObjsread")
				.withNewPermission("w").withUsers(Arrays.asList(USER1)));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("listObjswrite"));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("listObjswrite")
				.withNewPermission("w").withUsers(Arrays.asList(USER1)));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("listObjsadmin"));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("listObjsadmin")
				.withNewPermission("a").withUsers(Arrays.asList(USER1)));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("listObjsGlobal")
				.withGlobalread("r"));

		List<String> allws = Arrays.asList("listObjs1", "listObjs2", "listObjsread",
				"listObjswrite", "listObjsadmin", "listObjsGlobal");


		Map<String, String> meta = new HashMap<String, String>();
		meta.put("meta1", "1");
		Map<String, String> meta2 = new HashMap<String, String>();
		meta2.put("meta2", "2");

		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> std1 =
				CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("listObjs1")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta).withType(anotherType).withName("std")))).get(0);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> std2 =
				CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("listObjs1")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta2).withType(anotherType2).withName("std")))).get(0);

		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> hidden =
				CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("listObjs2")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta).withType(anotherType).withName("hidden").withHidden(1L)))).get(0);

		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> deleted =
				CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("listObjs2")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta2).withType(anotherType).withName("deleted")))).get(0);
		CLIENT1.deleteObjects(Arrays.asList(new ObjectIdentity().withWorkspace("listObjs2").withName("deleted")));

		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> readable =
				CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("listObjsread")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta).withType(anotherType).withName("write")))).get(0);
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("listObjsread")
				.withNewPermission("r").withUsers(Arrays.asList(USER1)));
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> writeable =
				CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("listObjswrite")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta2).withType(anotherType).withName("write")))).get(0);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> adminable =
				CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("listObjsadmin")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta2).withType(anotherType).withName("admin")))).get(0);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> global =
				CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("listObjsGlobal")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withMeta(meta).withType(anotherType).withName("global")))).get(0);

		// holy shit these tests are unreadable
		checkListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), null, null, null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, std2, hidden, deleted), false);
		checkListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), null, null, null, null, 1L, 1L, null, 1L, 1L, 0L,
				Arrays.asList(std1, std2, hidden, deleted), false);
		checkListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), null, null, null, null, 1L, 1L, 1L, 1L, 1L, 0L,
				Arrays.asList(deleted), false);
		checkListObjects(Arrays.asList("listObjs1"), new ArrayList<Long>(), null, null, null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, std2), false);
		checkListObjects(new ArrayList<String>(), Arrays.asList(info1.getE1()), null, null, null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, std2), false);
		checkListObjects(Arrays.asList("listObjs2"), new ArrayList<Long>(), null, null, null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(hidden, deleted), false);
		checkListObjects(new ArrayList<String>(), Arrays.asList(info2.getE1()), null, null, null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(hidden, deleted), false);
		checkListObjects(Arrays.asList("listObjs1", "listObjs2"), new ArrayList<Long>(), null, null, null, null, 1L, 1L, 0L, 1L, 0L, 0L,
				Arrays.asList(std1, std2, hidden, deleted), true);
		checkListObjects(allws, new ArrayList<Long>(), anotherType, null, null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, deleted, readable, writeable, adminable, global), false);

		//user filtering
		checkListObjects(allws, new ArrayList<Long>(), anotherType, null,
				new ArrayList<String>(), null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, deleted, readable, writeable, adminable, global), false);
		checkListObjects(allws, new ArrayList<Long>(), anotherType, null,
				Arrays.asList(USER1, USER2), null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, deleted, readable, writeable, adminable, global), false);
		checkListObjects(allws, new ArrayList<Long>(), anotherType, null,
				Arrays.asList(USER1), null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, deleted), false);
		checkListObjects(allws, new ArrayList<Long>(), anotherType, null,
				Arrays.asList(USER2), null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(readable, writeable, adminable, global), false);

		//meta data testing
		checkListObjects(allws, new ArrayList<Long>(), anotherType, null, null,
				new HashMap<String, String>(), 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, deleted, readable, writeable, adminable, global), false);
		checkListObjects(allws, new ArrayList<Long>(), anotherType, null, null,
				meta, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, hidden, readable, global), false);
		checkListObjects(allws, new ArrayList<Long>(), anotherType, null, null,
				meta2, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(deleted, writeable, adminable), false);

		checkListObjects(allws, new ArrayList<Long>(), anotherType2, null, null, null, 1L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std2), false);
		checkListObjects(new ArrayList<String>(), Arrays.asList(info2.getE1(), info1.getE1()), null, null, null, null, null, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, std2, deleted), false);
		checkListObjects(Arrays.asList("listObjs2"), Arrays.asList(info1.getE1()), null, null, null, null, 0L, 1L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, std2, deleted), false);
		checkListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), null, null, null, null, 1L, null, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, std2, hidden), false);
		checkListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), null, null, null, null, 1L, 0L, 0L, 1L, 1L, 0L,
				Arrays.asList(std1, std2, hidden), false);
		checkListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), null, null, null, null, 1L, 1L, 0L, null, 1L, 0L,
				Arrays.asList(deleted, std2, hidden), false);
		checkListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), null, null, null, null, 1L, 1L, 0L, 0L, 1L, 0L,
				Arrays.asList(deleted, std2, hidden), false);

		failListObjects(Arrays.asList("listObjs1"), Arrays.asList(info2.getE1()), "Foo", null, null, 1L, 1L, 1L, 1L,
				"Type Foo could not be split into a module and name");
		failListObjects(Arrays.asList("listObjs1"), Arrays.asList(-1L), null, null, null, 1L, 1L, 1L, 1L,
				"Workspace id must be > 0");
		failListObjects(Arrays.asList("foo:bar:listObjs1"), Arrays.asList(1L), null, null, null, 1L, 1L, 1L, 1L,
				"Workspace name foo:bar:listObjs1 may only contain one : delimiter");
		failListObjects(Arrays.asList("listObjs1fake"), Arrays.asList(info2.getE1()), anotherType, null, null, 1L, 1L, 1L, 1L,
				"No workspace with name listObjs1fake exists");

		// test with illegal numbers of workspaces
		final String err = "At least one and no more than 10000 workspaces must be specified";
		failListObjects(null, null, null, null, null, 1L, 1L, 1L, 1L, err);
		failListObjects(Collections.emptyList(), Collections.emptyList(),
				null, null, null, 1L, 1L, 1L, 1L, err);
		final List<String> ws = IntStream.range(1, 5002).mapToObj(i -> "a" + i)
				.collect(Collectors.toList());
		final List<Long> longs = LongStream.range(1, 5001).mapToObj(i -> i)
				.collect(Collectors.toList());
		failListObjects(ws, longs, null, null, null, 1L, 1L, 1L, 1L, err);
		ws.remove(ws.size() - 1);
		longs.add(7000L);
		failListObjects(ws, longs, null, null, null, 1L, 1L, 1L, 1L, err);

		meta.put("this should", "force a fail");
		failListObjects(Arrays.asList("listObjs1"), Arrays.asList(1L), null, null, meta, 1L, 1L, 1L, 1L,
				"Only one metadata spec allowed");

		compareObjectInfo(CLIENT1.getObjectHistory(
				new ObjectIdentity().withRef("listObjs1/std")), Arrays.asList(std1, std2));
		compareObjectInfo(CLIENT1.getObjectHistory(
				new ObjectIdentity().withRef("listObjs2/hidden/1")), Arrays.asList(hidden));

		try {
			CLIENT1.getObjectHistory(new ObjectIdentity().withRef("listObjs1/hidden/1/3"));
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(), is(
					"Illegal number of separators '/' in object reference 'listObjs1/hidden/1/3'")
					);
		}
	}

	@Test
	public void listObjectsLimit() throws Exception {
		String ws = "pagination";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws));

		List<ObjectSaveData> objs = new LinkedList<ObjectSaveData>();
		for (int i = 0; i < 200; i++) {
			objs.add(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
					.withType(SAFE_TYPE).withName(getRandomName()));
		}
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(ws)
				.withObjects(objs));

		//this depends on the natural sort order of mongo
		checkObjectPagination(ws, null, 1, 200);
		checkObjectPagination(ws, 0L, 1, 200);
		checkObjectPagination(ws, 1L, 1, 1);
		checkObjectPagination(ws, 50L, 1, 50);
		checkObjectPagination(ws, 200L, 1, 200);
		checkObjectPagination(ws, 201L, 1, 200);

		failListObjects(Arrays.asList(ws), null, null, null, null, 0L, 0L,
				0L, 0L, 4000000000L, "Limit can be no greater than 2147483647");
	}

	@Test
	public void listObjectsByDate() throws Exception {
		ArrayList<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> mt =
				new ArrayList<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>>();
		String ws = "listObjsByDate";
		UObject d = new UObject(new HashMap<String, String>());
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws));
		SaveObjectsParams p = new SaveObjectsParams().withWorkspace(ws)
				.withObjects(Arrays.asList(new ObjectSaveData().withData(d)
						.withType(SAFE_TYPE).withName("o1")));
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> o1 =
				CLIENT1.saveObjects(p).get(0);
		p.getObjects().get(0).setName("o2");
		Thread.sleep(2000);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> o2 =
				CLIENT1.saveObjects(p).get(0);
		p.getObjects().get(0).setName("o3");
		Thread.sleep(2000);
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> o3 =
				CLIENT1.saveObjects(p).get(0);
		long beforeallEpoch = subSec(o1.getE4()); //max res is 1s
		long afterallEpoch = addSec(o3.getE4());
		String beforeall = DATE_FORMAT.format(beforeallEpoch);
		String afterall = DATE_FORMAT.format(afterallEpoch);

		ListObjectsParams lop = new ListObjectsParams().withWorkspaces(Arrays.asList(ws))
				.withIncludeMetadata(1L);
		compareObjectInfo(CLIENT1.listObjects(lop), Arrays.asList(o1, o2, o3), false);

		setDates(lop, beforeall, afterall);
		compareObjectInfo(CLIENT1.listObjects(lop), Arrays.asList(o1, o2, o3), false);
		setDates(lop, beforeallEpoch, afterallEpoch);
		compareObjectInfo(CLIENT1.listObjects(lop), Arrays.asList(o1, o2, o3), false);

		setDates(lop, afterall, beforeall);
		compareObjectInfo(CLIENT1.listObjects(lop), mt, false);
		setDates(lop, afterallEpoch, beforeallEpoch);
		compareObjectInfo(CLIENT1.listObjects(lop), mt, false);

		setDates(lop, DATE_FORMAT.format(addSec(o1.getE4())), null);
		compareObjectInfo(CLIENT1.listObjects(lop), Arrays.asList(o2, o3), false);
		setDates(lop, addSec(o1.getE4()), null);
		compareObjectInfo(CLIENT1.listObjects(lop), Arrays.asList(o2, o3), false);

		setDates(lop, null, DATE_FORMAT.format(subSec(o3.getE4())));
		compareObjectInfo(CLIENT1.listObjects(lop), Arrays.asList(o1, o2), false);
		setDates(lop, null, subSec(o3.getE4()));
		compareObjectInfo(CLIENT1.listObjects(lop), Arrays.asList(o1, o2), false);

		setDates(lop, DATE_FORMAT.format(addSec(o1.getE4())),
				DATE_FORMAT.format(subSec(o3.getE4())));
		compareObjectInfo(CLIENT1.listObjects(lop), Arrays.asList(o2), false);
		setDates(lop, addSec(o1.getE4()), subSec(o3.getE4()));
		compareObjectInfo(CLIENT1.listObjects(lop), Arrays.asList(o2), false);

		failListObjectsByDate(ws, "crappy obj date",
				"Unparseable date: Text 'crappy obj date' could not be parsed at index 0");
		try {
			CLIENT1.listObjects(new ListObjectsParams()
				.withWorkspaces(Arrays.asList(ws))
				.withAfter(beforeall).withAfterEpoch(1L));
			fail("Ran list ojb with time & epoch");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Cannot specify both timestamp and epoch for after " +
							"parameter"));
		}
		try {
			CLIENT1.listObjects(new ListObjectsParams()
				.withWorkspaces(Arrays.asList(ws))
				.withBefore(beforeall).withBeforeEpoch(1L));
			fail("Ran list obj with time & epoch");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Cannot specify both timestamp and epoch for before " +
							"parameter"));
		}
	}

	private void setDates(ListObjectsParams lop, Long after, Long before) {
		lop.withBefore(null).withAfter(null).withAfterEpoch(after)
				.withBeforeEpoch(before);
	}

	private void setDates(ListObjectsParams lop, String after, String before) {
		lop.withAfterEpoch(null).withBeforeEpoch(null).withBefore(before)
				.withAfter(after);
	}

	@Test
	public void listObjectsFilterByID() throws Exception {
		String ws = "listObjectsFilterByID";
		UObject d = new UObject(new HashMap<String, String>());
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws));
		List<ObjectSaveData> objs = new LinkedList<ObjectSaveData>();
		for (int i = 1; i < 11; i++) {
			objs.add(new ObjectSaveData().withData(d).withType(SAFE_TYPE)
					.withName(getRandomName()));
		}
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(ws)
				.withObjects(objs));

		checkObjectFilter(ws, -1, -1, 1, 10);
		checkObjectFilter(ws, 2, 5, 2, 5);
	}

	private void checkObjectFilter(
			String ws,
			long minObjectID,
			long maxObjectID,
			int minIDexpected,
			int maxIDexpected)
			throws Exception {

		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> res =
				CLIENT1.listObjects(new ListObjectsParams()
						.withWorkspaces(Arrays.asList(ws))
						.withMinObjectID(minObjectID)
						.withMaxObjectID(maxObjectID));

		assertThat("correct number of objects returned", res.size(),
				is(maxIDexpected - minIDexpected + 1));
		for (Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> oi: res) {
			if (oi.getE1() < minIDexpected ||
					oi.getE1() > maxIDexpected) {
				fail(String.format("ObjectID out of test bounds: %s min %s max %s",
						oi.getE1(), minIDexpected, maxIDexpected));
			}
		}
	}

	private class TstObjInfo {
		private long wsid;
		private long objid;
		private long ver;

		public TstObjInfo(long wsid, long objid, int ver) {
			this.wsid = wsid;
			this.objid = objid;
			this.ver = ver;
		}

		@Override
		public String toString() {
			return "TstObjInfo [wsid=" + wsid + ", objid=" + objid + ", ver=" + ver + "]";
		}
	}

	@Test
	public void listObjectsWithStartAfter() throws Exception {
		// This only tests that the start after parameter is passed correctly to the backend.
		// The various interactions with other parameters are tested in the workspace tests
		final String ws1 = "listObjectsWithStartFrom1";
		final String ws2 = "listObjectsWithStartFrom2";
		final UObject d = new UObject(new HashMap<String, String>());
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws1));
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws2));

		List<ObjectSaveData> objs = new LinkedList<ObjectSaveData>();
		objs.add(new ObjectSaveData().withData(d).withType(SAFE_TYPE).withName("o1"));
		objs.add(new ObjectSaveData().withData(d).withType(SAFE_TYPE).withName("o2"));
		objs.add(new ObjectSaveData().withData(d).withType(SAFE_TYPE).withName("o3"));
		objs.add(new ObjectSaveData().withData(d).withType(SAFE_TYPE).withName("o3"));  // v2
		objs.add(new ObjectSaveData().withData(d).withType(SAFE_TYPE).withName("o1"));  // v2

		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(ws1).withObjects(objs));

		objs.clear();
		objs.add(new ObjectSaveData().withData(d).withType(SAFE_TYPE).withName("o1"));
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(ws2).withObjects(objs));

		final ListObjectsParams lop = new ListObjectsParams().withIds(Arrays.asList(1L, 2L))
				.withShowAllVersions(1L);

		final List<TstObjInfo> expected = Arrays.asList(
				new TstObjInfo(1, 1, 2),
				new TstObjInfo(1, 1, 1),
				new TstObjInfo(1, 2, 1),
				new TstObjInfo(1, 3, 2),
				new TstObjInfo(1, 3, 1),
				new TstObjInfo(2, 1, 1)
				);

		checkStartafter(lop.withStartafter(null), expected);
		checkStartafter(lop.withStartafter("   \t    "), expected);
		checkStartafter(lop.withStartafter("0"), expected);
		checkStartafter(lop.withStartafter("0/0"), expected);
		checkStartafter(lop.withStartafter("0/0/0"), expected);
		checkStartafter(lop.withStartafter("1"), expected);
		checkStartafter(lop.withStartafter("1/1"), expected);
		checkStartafter(lop.withStartafter("1/1/3"), expected);
		checkStartafter(lop.withStartafter("2"), expected.subList(5, 6));
		checkStartafter(lop.withStartafter("2/1/"), expected.subList(5, 6));
		checkStartafter(lop.withStartafter("2/1/2"), expected.subList(5, 6));
		checkStartafter(lop.withStartafter("2/1/1"), Collections.emptyList());
		checkStartafter(lop.withStartafter("1/1/2"), expected.subList(1, 6));
		checkStartafter(lop.withStartafter("1/1/1"), expected.subList(2, 6));
		checkStartafter(lop.withStartafter("1/2/2"), expected.subList(2, 6));
		checkStartafter(lop.withStartafter("1/3/2"), expected.subList(4, 6));
		checkStartafter(lop.withStartafter("1/3/1"), expected.subList(5, 6));
	}

	@Test
	public void listObjectsWithStartafterFail() throws Exception {
		// test a non-exhaustive set of error conditions.
		final ListObjectsParams lop = new ListObjectsParams().withIds(Arrays.asList(1L));
		failListObjects(lop.withStartafter("foo"),
				 "Illegal integer workspace ID in reference string foo: foo");
		failListObjects(lop.withStartafter("1/2/  "),
				"Illegal integer version in reference string 1/2/  : ");
		failListObjects(lop.withStartafter("1/2/").withAfterEpoch(10000L),
				"If a starting reference for paging is provided, metadata, savers, " +
				"min/max object IDs, and timestamps cannot be set as filters.");
	}

	private void checkStartafter(final ListObjectsParams lop, final List<TstObjInfo> expected)
			throws Exception {
		final List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>>> res = CLIENT1.listObjects(lop);
		for (int i = 0; i < expected.size(); i++) {
			final TstObjInfo info = expected.get(i);
			assertThat("incorrect wsid for case " + info, res.get(i).getE7(), is(info.wsid));
			assertThat("incorrect objid for case " + info, res.get(i).getE1(), is(info.objid));
			assertThat("incorrect ver for case " + info, res.get(i).getE5(), is(info.ver));
		}
		assertThat("incorrect object count", res.size(), is(expected.size()));
	}

	@Test
	public void getNamesByPrefix() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("ws1"));
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("ws2")
				.withGlobalread("r"));
		List<WorkspaceIdentity> wsis = Arrays.asList(
				new WorkspaceIdentity().withWorkspace("ws1"),
				new WorkspaceIdentity().withWorkspace("ws2"));

		Map<String, String> data = new HashMap<String, String>();
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("ws1")
				.withObjects(Arrays.asList(
						new ObjectSaveData().withData(new UObject(data))
							.withType(SAFE_TYPE).withName("aba"),
						new ObjectSaveData().withData(new UObject(data))
							.withType(SAFE_TYPE).withName("abc").withHidden(1L)
						)));
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("ws2")
				.withObjects(Arrays.asList(
						new ObjectSaveData().withData(new UObject(data))
							.withType(SAFE_TYPE).withName("adb"))));

		List<WorkspaceIdentity> mt = new LinkedList<WorkspaceIdentity>();
		List<List<String>> mtres = new LinkedList<List<String>>();
		checkGetByPrefix(CLIENT1, mt, "", 0L, mtres);
		checkGetByPrefix(CLIENT1, wsis, "", 0L, Arrays.asList(
				Arrays.asList("aba"),
				Arrays.asList("adb")));
		checkGetByPrefix(CLIENT1, wsis, "a", 1L, Arrays.asList(
				Arrays.asList("aba", "abc"),
				Arrays.asList("adb")));
		checkGetByPrefix(CLIENT1, wsis, "ab", 1L, Arrays.asList(
				Arrays.asList("aba", "abc"),
				new LinkedList<String>()));
		checkGetByPrefix(CLIENT_NO_AUTH, Arrays.asList(wsis.get(1)), "a", 0L,
				Arrays.asList(Arrays.asList("adb")));

		try {
			CLIENT_NO_AUTH.getNamesByPrefix(new GetNamesByPrefixParams()
					.withPrefix("").withWorkspaces(wsis.subList(0, 1)));
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Anonymous users may not read workspace ws1"));
		}
	}

	private void checkGetByPrefix(
			WorkspaceClient cli, List<WorkspaceIdentity> wsis,
			String prefix,
			long includeHidden,
			List<List<String>> results)
			throws Exception {
		List<Set<String>> exp = new LinkedList<Set<String>>();
		for (List<String> r: results) {
			exp.add(new HashSet<String>(r));
		}
		List<Set<String>> got = new LinkedList<Set<String>>();
		GetNamesByPrefixResults ret = cli.getNamesByPrefix(
				new GetNamesByPrefixParams().withWorkspaces(wsis)
						.withIncludeHidden(includeHidden)
						.withPrefix(prefix));
		for (List<String> r: ret.getNames()) {
			got.add(new HashSet<String>(r));
		}

		assertThat("correct returned names", got, is(exp));
	}

	@Test
	public void getObjectSubset() throws Exception {
		/* note most tests are performed at the same time as getObjects, so
		 * only issues specific to subsets are tested here
		 */
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info1 =
				CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("subdata"));

		String strdata =
				"{\"foobar\":\"somestuff\"," +
				 "\"map\":{\"id1\":{\"id\":1," +
								   "\"thing\":\"foo\"}," +
						  "\"id2\":{\"id\":2," +
								   "\"thing\":\"foo2\"}," +
						  "\"id3\":{\"id\":3," +
								   "\"thing\":\"foo3\"}" +
						  "}" +
				"}";
		String md5 = DigestUtils.md5Hex(strdata);
		assertThat("md5 correct", md5, is("06c2ae8f77ad36e262bca7b186c944ec"));

		Map<String, Object> data = createData( // intentionally unsorted
				"{\"map\": {\"id1\": {\"id\": 1," +
				"					  \"thing\": \"foo\"}," +
				"			\"id2\": {\"id\": 2," +
				"					  \"thing\": \"foo2\"}," +
				"			\"id3\": {\"id\": 3," +
				"					  \"thing\": \"foo3\"}" +
				"			}," +
				" \"foobar\": \"somestuff\"" +
				"}"
				);

		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("subdata")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(data))
				.withType(SAFE_TYPE).withName("std")))).get(0);

		@SuppressWarnings("deprecation")
		ObjectData od = CLIENT1.getObjectSubset(Arrays.asList(
				new us.kbase.workspace.SubObjectIdentity().withRef("subdata/1")
				.withIncluded(Arrays.asList("/map/id1", "/map/id3")))).get(0);
		ObjectData odn = CLIENT1.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(
				new ObjectSpecification().withRef("subdata/1")
				.withStrictMaps(0L)
				.withIncluded(Arrays.asList(
						"/map/id1", "/map/id3", "/map/id4")))))
				.getData().get(0);
		Map<String, Object> expdata = createData(
				"{\"map\": {\"id1\": {\"id\": 1," +
				"					  \"thing\": \"foo\"}," +
				"			\"id3\": {\"id\": 3," +
				"					  \"thing\": \"foo3\"}" +
				"			}" +
				"}"
				);
		checkData(od, 1, "std", SAFE_TYPE, 1, USER1, info1.getE1(), "subdata",
				md5, 119, new HashMap<String, String>(),
				expdata);
		checkData(odn, 1, "std", SAFE_TYPE, 1, USER1, info1.getE1(), "subdata",
				md5, 119, new HashMap<String, String>(),
				expdata);

		try {
			CLIENT1.getObjects2(new GetObjects2Params().withObjects(Arrays.asList(
					new ObjectSpecification().withRef("subdata/1")
					.withStrictMaps(1L)
					.withIncluded(Arrays.asList("/map/id1", "/map/id4")))));
			fail("got objects with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("Invalid selection: data does not contain a field or " +
							"key named 'id4', at: /map/id4"));
		}

		try {
			@SuppressWarnings({ "deprecation", "unused" })
			ObjectData objectData = CLIENT1.getObjectSubset(Arrays.asList(
					new us.kbase.workspace.SubObjectIdentity().withRef("subdata/1")
					.withIncluded(Arrays.asList("/map/id1", "/map/id3/id/id/id/12")))).get(0);
			fail("listed objects with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("Invalid selection: the path given specifies fields or elements that do not exist "
							+ "because data at this location is a scalar value (i.e. string, integer, float), at: /map/id3/id"));
		}
		try {
			CLIENT1.getObjects2(new GetObjects2Params().withObjects(Arrays.asList(
					new ObjectSpecification().withRef("subdata/1")
					.withIncluded(Arrays.asList("/map/id1", "/map/id3/id/id/id/12")))))
					.getData().get(0);
			fail("listed objects with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("Invalid selection: the path given specifies fields or elements that do not exist "
							+ "because data at this location is a scalar value (i.e. string, integer, float), at: /map/id3/id"));
		}

		Map<String, Object> data2 = createData( // intentionally unsorted
				"{\"features\": " +
				"    [{\"id\": 1, \"thing\": \"foo\"}," +
				"     {\"id\": 2, \"thing\": \"foo2\"}," +
				"     {\"id\": 3, \"thing\": \"foo3\"}" +
				"    ]," +
				" \"foobar\": \"somestuff\"" +
				"}"
				);

		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("subdata")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(data2))
						.withType(SAFE_TYPE).withName("std2")))).get(0);

		try {
			@SuppressWarnings({ "unused", "deprecation" })
			ObjectData objectData = CLIENT1.getObjectSubset(Arrays.asList(
					new us.kbase.workspace.SubObjectIdentity().withRef("subdata/2")
					.withIncluded(Arrays.asList("/features/2", "/features/3")))).get(0);
			fail("listed objects with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("Invalid selection: no array element exists at position '3', at: /features/3"));
		}
		try {
			CLIENT1.getObjects2(new GetObjects2Params().withObjects(Arrays.asList(
					new ObjectSpecification().withRef("subdata/2")
					.withIncluded(Arrays.asList("/features/2", "/features/3")))))
					.getData().get(0);
			fail("listed objects with bad params");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("Invalid selection: no array element exists at position '3', at: /features/3"));
		}

		@SuppressWarnings("deprecation")
		ObjectData od2 = CLIENT1.getObjectSubset(Arrays.asList(
				new us.kbase.workspace.SubObjectIdentity().withRef("subdata/2").withStrictArrays(0L)
				.withIncluded(Arrays.asList("/features/2", "/features/3")))).get(0);
		Map<String, Object> od2map = od2.getData().asClassInstance(new TypeReference<Map<String, Object>>() {});
		assertThat(od2map.size(), is(1));
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> features = (List<Map<String, Object>>)od2map.get("features");
		assertThat(features.size(), is(1));
		assertThat(features.get(0).get("thing"), is("foo3"));

		ObjectData od2n = CLIENT1.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(
				new ObjectSpecification().withRef("subdata/2")
						.withStrictArrays(0L)
						.withIncluded(Arrays.asList(
								"/features/2", "/features/3", "/bar")))))
				.getData().get(0);
		Map<String, Object> od2nmap = od2n.getData().asClassInstance(new TypeReference<Map<String, Object>>() {});
		assertThat(od2nmap.size(), is(1));
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> featuresN = (List<Map<String, Object>>)od2nmap.get("features");
		assertThat(featuresN.size(), is(1));
		assertThat(featuresN.get(0).get("thing"), is("foo3"));
	}

	@Test
	public void listReferencingObjects() throws Exception {
		long wsid = CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("referingobjs")).getE1();

		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("referingobjs")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withType(SAFE_TYPE).withName("std")))).get(0);

		Map<String, Object> refdata = new HashMap<String, Object>();
		refdata.put("ref", "referingobjs/std/1");
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> ref =
				CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("referingobjs")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(refdata))
				.withType(REF_TYPE).withName("ref")))).get(0);

		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> prov =
				CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("referingobjs")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withType(SAFE_TYPE).withName("prov").withProvenance(Arrays.asList(
						new ProvenanceAction().withInputWsObjects(Arrays.asList("referingobjs/std/1"))))))).get(0);

		List<ObjectIdentity> loi = Arrays.asList(new ObjectIdentity().withRef("referingobjs/std/1"));
		List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>>> retrefs =
				CLIENT1.listReferencingObjects(loi);

		assertThat("one obj list returned", retrefs.size(), is(1));
		assertThat("two refs returned", retrefs.get(0).size(), is(2));
		compareObjectInfo(retrefs.get(0), Arrays.asList(ref, prov), false);
		@SuppressWarnings("deprecation")
		List<Long> refcnts = CLIENT1.listReferencingObjectCounts(loi);
		assertThat("got correct refcounts", refcnts, is(Arrays.asList(2L)));

		loi.set(0, new ObjectIdentity().withRef("referingobjs/std/2"));
		try {
			@SuppressWarnings({ "deprecation", "unused" })
			List<Long> foo = CLIENT1.listReferencingObjectCounts(loi);
			fail("got ref counts with bad obj id");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("No object with id 1 (name std) and version 2 exists in workspace " +
							wsid));
		}
	}

	@Test
	public void getReferencedObjects() throws Exception {

		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("referenced")).getE1();
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace("referenced")
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("referencedPriv")).getE1();

		Map<String, Object> data1 = createData("{\"foobar\": \"somestuff\"}");
		Map<String, Object> data2 = createData("{\"foobar\": \"somestuff2\"}");

		CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("referencedPriv")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(data1))
				.withType(SAFE_TYPE).withName("one"))));
		CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("referencedPriv")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(data2))
				.withType(SAFE_TYPE).withName("two"))));

		Map<String, Object> refdata = createData("{\"ref\": \"referencedPriv/one\"}");
		CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("referenced")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(refdata))
				.withType(REF_TYPE).withName("ref"))));

		CLIENT2.saveObjects(new SaveObjectsParams().withWorkspace("referenced")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(new HashMap<String, String>()))
				.withType(SAFE_TYPE).withName("prov").withProvenance(Arrays.asList(
						new ProvenanceAction().withInputWsObjects(Arrays.asList("referencedPriv/two")))))));


		List<ObjectData> exp = CLIENT2.getObjects2(new GetObjects2Params().withObjects(Arrays.asList(
				new ObjectSpecification().withRef("referencedPriv/one"),
				new ObjectSpecification().withRef("referencedPriv/two")))).getData();

		/* ws referenced = 1
		 * ws referencedPriv = 2
		 * obj one = 1
		 * obj two = 2
		 * obj ref = 1
		 * obj prov = 2
		 */
		try {
			getReferencedObjectsCheckData(exp);
		} catch (ServerException e) {
			System.out.println(e.getData());
			throw e;
		}

		try {
			CLIENT1.getObjects2(new GetObjects2Params().withObjects(Arrays.asList(
					new ObjectSpecification().withRef("referenced/ref").withObjRefPath(
							Arrays.asList("referencedPriv/one")).withObjPath(
							Arrays.asList(new ObjectIdentity().withRef("referencedPriv/one"))))));
			fail("get objects with bad params");
		} catch (ServerException se) {
			assertThat("wrong exception message", se.getLocalizedMessage(),
					is("Error on ObjectSpecification #1: Only one of the 6 " +
							"options for specifying an object reference " +
							"path is allowed"));
		}

		failGetReferencedObjects(null, "refChains may not be null");
		failGetReferencedObjects(Arrays.asList(null, Arrays.asList(null, new ObjectIdentity().withRef("referenced/ref"))),
				"Error on object chain #1: The object identifier list cannot be null");
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withRef("referenced/ref"),
				new ObjectIdentity().withRef("referencedPriv/one")), null),
				"Error on object chain #2: The object identifier list cannot be null");
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withRef("referenced/ref"),
				new ObjectIdentity().withRef("referencedPriv/one")), new ArrayList<ObjectIdentity>()),
				"Error on object chain #2: No object identifiers provided");
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withRef("referenced/ref"))),
				"Error on object chain #1: The minimum size of a reference chain is 2 ObjectIdentities");
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withRef("referenced/ref"), null)),
				"Error on object chain #1: Error on ObjectIdentity #2: ObjectIdentity cannot be null");
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withRef("referenced/ref").withName("foo"),
				new ObjectIdentity().withRef("referenced/ref"))),
				"Error on object chain #1: Error on ObjectIdentity #1: Object reference referenced/ref provided; cannot provide any other means of identifying an object. Object name: foo");

		ObjectIdentity oi = new ObjectIdentity().withRef("saveget/1");
		oi.setAdditionalProperties("foo", "bar");
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withRef("referenced/ref"),
				oi)), "Error on object chain #1: Error on ObjectIdentity #2: Unexpected arguments in ObjectIdentity: foo");

		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withWorkspace("referencedPriv").withName("one"),
				new ObjectIdentity().withRef("referencedPriv/two"))), "Object one cannot be accessed: User " + USER1 + " may not read workspace referencedPriv");
		failGetReferencedObjects(Arrays.asList(Arrays.asList(
				new ObjectIdentity().withWorkspace("referenced").withName("ref"),
				new ObjectIdentity().withRef("referencedPrivfake/two"))),
				"Reference path #1 starting with object ref in workspace referenced, position " +
				"1: Object ref in workspace referenced does not contain a reference to object " +
				"two in workspace referencedPrivfake");

		failGetReferencedObjects(Arrays.asList(Arrays.asList(
				new ObjectIdentity().withWorkspace("referenced").withName("ref"),
				new ObjectIdentity().withRef("referencedPriv/three"))),
				"Reference path #1 starting with object ref in workspace referenced, position " +
				"1: Object ref in workspace referenced does not contain a reference to object " +
				"three in workspace referencedPriv");

		CLIENT2.deleteObjects(Arrays.asList(new ObjectIdentity().withRef("referencedPriv/one"),
				new ObjectIdentity().withRef("referencedPriv/two")));
		CLIENT2.deleteWorkspace(new WorkspaceIdentity().withWorkspace("referencedPriv"));
		getReferencedObjectsCheckData(exp);

		CLIENT1.deleteObjects(Arrays.asList(new ObjectIdentity().withRef("referenced/ref")));
		failGetReferencedObjects(Arrays.asList(Arrays.asList(
				new ObjectIdentity().withRef("referenced/ref"),
				new ObjectIdentity().withRef("referencedPriv/one"))),
				"Object 1 (name ref) in workspace 1 (name referenced) has been deleted");
		CLIENT1.deleteWorkspace(new WorkspaceIdentity().withWorkspace("referenced"));
		failGetReferencedObjects(Arrays.asList(Arrays.asList(new ObjectIdentity().withRef("referenced/ref"),
				new ObjectIdentity().withRef("referencedPriv/one"))),
				"Object ref cannot be accessed: Workspace referenced is deleted");
	}

	@SuppressWarnings("deprecation")
	protected void getReferencedObjectsCheckData(final List<ObjectData> exp)
			throws Exception {

		//test get refed objs
		List<ObjectData> res = CLIENT1.getReferencedObjects(Arrays.asList(
				Arrays.asList(new ObjectIdentity().withRef("referenced/ref"),
						new ObjectIdentity().withRef("referencedPriv/one")),
				Arrays.asList(new ObjectIdentity().withRef("referenced/prov"),
						new ObjectIdentity().withRef("referencedPriv/two"))));
		compareData(exp, res);

		// test getobjs2 and getinfo with full ref path
		final List<ObjectSpecification> fullreflist = Arrays.asList(
				new ObjectSpecification()
						.withRef(" referenced/ref; \nreferencedPriv/one ; "),
				new ObjectSpecification()
						.withRef("referenced/prov;referencedPriv/two"));
		res = CLIENT1.getObjects2(new GetObjects2Params()
				.withObjects(fullreflist)).getData();
		compareData(exp, res);

		final List<List<String>> paths = Arrays.asList(Arrays.asList("1/1/1", "2/1/1"),
				Arrays.asList("1/2/1", "2/2/1"));
		GetObjectInfo3Results info = CLIENT1.getObjectInfo3(new GetObjectInfo3Params()
							.withObjects(fullreflist).withIncludeMetadata(1L));
		compareInfo(info.getInfos(), exp);
		assertThat("incorrect paths", info.getPaths(), is(paths));

		// test getobjs2 and getinfo with to ref path
		final List<ObjectSpecification> toreflist = Arrays.asList(
				new ObjectSpecification().withRef("referencedPriv/one")
						.withToObjRefPath(Arrays.asList("referenced/ref")),
				new ObjectSpecification().withRef("referencedPriv/two")
						.withToObjRefPath(Arrays.asList("referenced/prov")));
		res = CLIENT1.getObjects2(new GetObjects2Params()
				.withObjects(toreflist)).getData();
		compareData(exp, res);

		info = CLIENT1.getObjectInfo3(new GetObjectInfo3Params()
						.withObjects(toreflist).withIncludeMetadata(1L));
		compareInfo(info.getInfos(), exp);
		assertThat("incorrect paths", info.getPaths(), is(paths));

		// test getobjs2 and getinfo with from ref path
		final List<ObjectSpecification> reflist = Arrays.asList(
				new ObjectSpecification().withRef("referenced/ref").withObjRefPath(
						Arrays.asList("referencedPriv/one")),
				new ObjectSpecification().withRef("referenced/prov").withObjRefPath(
						Arrays.asList("referencedPriv/two")));
		res = CLIENT1.getObjects2(new GetObjects2Params().withObjects(reflist))
				.getData();
		compareData(exp, res);

		info = CLIENT1.getObjectInfo3(new GetObjectInfo3Params()
						.withObjects(reflist).withIncludeMetadata(1L));
		compareInfo(info.getInfos(), exp);
		assertThat("incorrect paths", info.getPaths(), is(paths));


		// test getobjs2 and getinfo with to obj path
		final List<ObjectSpecification> torefobjlist = Arrays.asList(
				new ObjectSpecification().withRef("referencedPriv/one")
						.withToObjPath(Arrays.asList(new ObjectIdentity()
								.withRef("referenced/ref"))),
				new ObjectSpecification().withRef("referencedPriv/two")
						.withToObjPath(Arrays.asList(new ObjectIdentity()
								.withRef("referenced/prov"))));
		res = CLIENT1.getObjects2(new GetObjects2Params()
				.withObjects(torefobjlist)).getData();
		compareData(exp, res);

		info = CLIENT1.getObjectInfo3(new GetObjectInfo3Params()
				.withObjects(torefobjlist).withIncludeMetadata(1L));
		compareInfo(info.getInfos(), exp);
		assertThat("incorrect paths", info.getPaths(), is(paths));

		// test getobjs2 and getinfo with from obj path
		final List<ObjectSpecification> refobjlist = Arrays.asList(
				new ObjectSpecification().withRef("referenced/ref")
						.withObjPath(Arrays.asList(new ObjectIdentity()
								.withRef("referencedPriv/one"))),
				new ObjectSpecification().withRef("referenced/prov")
						.withObjPath(Arrays.asList(new ObjectIdentity()
								.withRef("referencedPriv/two"))));
		res = CLIENT1.getObjects2(new GetObjects2Params().withObjects(refobjlist)).getData();
		compareData(exp, res);

		info = CLIENT1.getObjectInfo3(new GetObjectInfo3Params()
				.withObjects(refobjlist).withIncludeMetadata(1L));
		compareInfo(info.getInfos(), exp);
		assertThat("incorrect paths", info.getPaths(), is(paths));

		// test getobjs2 and getinfo with automatic lookup
		final List<ObjectSpecification> searchobjlist = Arrays.asList(
				new ObjectSpecification().withRef("referencedPriv/one").withFindReferencePath(1L),
				new ObjectSpecification().withRef("referencedPriv/two").withFindReferencePath(1L));
		res = CLIENT1.getObjects2(new GetObjects2Params().withObjects(searchobjlist)).getData();
		compareData(exp, res);

		info = CLIENT1.getObjectInfo3(new GetObjectInfo3Params()
				.withObjects(searchobjlist).withIncludeMetadata(1L));
		compareInfo(info.getInfos(), exp);
		assertThat("incorrect paths", info.getPaths(), is(paths));
	}
	
	@Test
	public void getAdminRole() throws Exception {
		assertThat("incorrect admin role", CLIENT_AA_ADMIN_NONE.getAdminRole().getAdminrole(),
				is("none"));
		assertThat("incorrect admin role", CLIENT_AA_ADMIN_READ.getAdminRole().getAdminrole(),
				is("read"));
		assertThat("incorrect admin role", CLIENT_AA_ADMIN_FULL.getAdminRole().getAdminrole(),
				is("full"));
	}

	@Test
	public void adminGetSetConfig() throws Exception {
		final Map<String, Object> gcCmd = ImmutableMap.of("command", "getConfig");
		failAdmin(CLIENT_AA_ADMIN_NONE, gcCmd, "User " + USER2 + " is not an admin");
		final Object ret = CLIENT_AA_ADMIN_READ.administer(new UObject(gcCmd))
				.asClassInstance(Object.class);
		assertThat("incorrect config", ret, is(ImmutableMap.of(
				"config", ImmutableMap.of("backend-file-retrieval-scaling", 1))));

		final Map<String, Object> command = ImmutableMap.of(
				"command", "setConfig",
				"params", ImmutableMap.of("set", ImmutableMap.of(
						"backend-file-retrieval-scaling", 4)));
		failAdmin(CLIENT_AA_ADMIN_READ, command,
				"Full administration rights required for this command");
		CLIENT_AA_ADMIN_FULL.administer(new UObject(command));
		final Object ret2 = CLIENT_AA_ADMIN_FULL.administer(new UObject(gcCmd))
				.asClassInstance(Object.class);
		assertThat("incorrect config", ret2, is(ImmutableMap.of(
				"config", ImmutableMap.of("backend-file-retrieval-scaling", 4))));

		final Map<String, Object> badcommand = ImmutableMap.of(
				"command", "setConfig",
				"params", ImmutableMap.of("set", ImmutableMap.of(
						"backend-file-retrieval-scaling", 8,
						"hello-im-a-hacker", 10000000)));
		failAdmin(CLIENT_AA_ADMIN_FULL, badcommand,
				"Unexpected key in configuration map: hello-im-a-hacker");
		final Object ret3 = CLIENT_AA_ADMIN_FULL.administer(new UObject(gcCmd))
				.asClassInstance(Object.class);
		assertThat("incorrect config", ret3, is(ImmutableMap.of(
				"config", ImmutableMap.of("backend-file-retrieval-scaling", 4))));
	}

	@Test
	public void adminAddRemoveList() throws Exception {
		checkAdmins(CLIENT2, Arrays.asList(USER2));
		failAdmin(CLIENT1, "{\"command\": \"listAdmins\"}", "User " + USER1 + " is not an admin");
		failAdmin(CLIENT2, "{\"command\": \"listAdmin\"}",
				"I don't know how to process the command: listAdmin");
		failAdmin(CLIENT2, "{\"command\": \"addAdmin\"," +
						   " \"user\": \"thisisnotavalidkbaseuserihopeorthistestwillfail\"}",
				"User thisisnotavalidkbaseuserihopeorthistestwillfail is not a valid user");
		CLIENT2.administer(new UObject(createData(
				"{\"command\": \"addAdmin\"," +
				" \"user\": \"" + USER1 + "\"}")));

		checkAdmins(CLIENT2, Arrays.asList(USER1, USER2));
		CLIENT1.administer(new UObject(createData(
				"{\"command\": \"removeAdmin\"," +
				" \"user\": \"" + USER1 + "\"}")));
		failAdmin(CLIENT1, "{\"command\": \"listAdmins\"}", "User " + USER1 + " is not an admin");
		checkAdmins(CLIENT2, Arrays.asList(USER2));

		CLIENT2.administer(new UObject(createData(
				"{\"command\": \"addAdmin\"," +
				" \"user\": \"" + USER1 + "\"}")));

		// since USER2 is set as an admin in the workspace ini file, this
		// should do nothing
		CLIENT1.administer(new UObject(createData(
				"{\"command\": \"removeAdmin\"," +
				" \"user\": \"" + USER2 + "\"}")));
		checkAdmins(CLIENT2, Arrays.asList(USER1, USER2));

		// add USER3 to admins and check USER3 has creds
		CLIENT1.administer(new UObject(createData(
				"{\"command\": \"addAdmin\"," +
				" \"user\": \"" + USER3 + "\"}")));
		checkAdmins(CLIENT3, Arrays.asList(USER1, USER2, USER3));

		//remove USER3 and check fail
		CLIENT1.administer(new UObject(createData(
				"{\"command\": \"removeAdmin\"," +
				" \"user\": \"" + USER3 + "\"}")));
		checkAdmins(CLIENT1, Arrays.asList(USER1, USER2));
		failAdmin(CLIENT3, "{\"command\": \"listAdmins\"}", "User " + USER3 + " is not an admin");

		CLIENT2.administer(new UObject(createData(
				"{\"command\": \"removeAdmin\"," +
				" \"user\": \"" + USER1 + "\"}")));
		checkAdmins(CLIENT2, Arrays.asList(USER2));
	}

	@Test
	public void adminAuth2Roles() throws Exception {
		final Map<String, Object> params = new HashMap<>();
		params.put("command", "createWorkspace");
		params.put("user", "user3");
		params.put("params", new CreateWorkspaceParams().withWorkspace("ws"));
		CLIENT_AA_ADMIN_FULL.administer(new UObject(params));

		// has read only role
		failAdmin(CLIENT_AA_ADMIN_READ, params,
				"Full administration rights required for this command");
		failAdmin(CLIENT_AA_ADMIN_NONE, params, "user2 is not an admin");

		params.put("command", "getWorkspaceInfo");
		params.put("params", new WorkspaceIdentity().withId(1L));
		final List<Object> wsinfo = CLIENT_AA_ADMIN_READ.administer(new UObject(params))
				.asClassInstance(new TypeReference<List<Object>>() {});
		assertThat("incorrect ws id", wsinfo.get(0), is(1));
		assertThat("incorrect ws name", wsinfo.get(1), is("ws"));
	}

	@Test
	public void adminModRequest() throws Exception {
		Map<String, String> mod2owner = new HashMap<String, String>();
		checkModRequests(mod2owner);
		CLIENT1.requestModuleOwnership("SomeMod");
		CLIENT1.requestModuleOwnership("SomeMod2");
		failAdmin(CLIENT1, "{\"command\": \"approveModRequest\"," +
				   " \"module\": \"SomeMod\"}", "User " + USER1 + " is not an admin");
		mod2owner.put("SomeMod", USER1);
		mod2owner.put("SomeMod2", USER1);
		checkModRequests(mod2owner);
		CLIENT2.administer(new UObject(createData(
				"{\"command\": \"approveModRequest\"," +
				" \"module\": \"SomeMod\"}")));
		mod2owner.remove("SomeMod");
		checkModRequests(mod2owner);
		CLIENT2.administer(new UObject(createData(
				"{\"command\": \"denyModRequest\"," +
				" \"module\": \"SomeMod2\"}")));
		mod2owner.remove("SomeMod2");
		checkModRequests(mod2owner);

		failAdmin(CLIENT2, "{\"command\": \"approveModRequest\"," +
						   " \"module\": \"SomeMod\"}", "There is no request for module SomeMod");
		failAdmin(CLIENT2, "{\"command\": \"approveModRequest\"," +
				   " \"module\": \"SomeMod3\"}", "There is no request for module SomeMod3");
		failAdmin(CLIENT2, "{\"command\": \"denyModRequest\"," +
				   " \"module\": \"SomeMod\"}", "There is no request for module SomeMod");
		failAdmin(CLIENT2, "{\"command\": \"denyModRequest\"," +
				   " \"module\": \"SomeMod3\"}", "There is no request for module SomeMod3");

		CLIENT1.registerTypespec(new RegisterTypespecParams()
				.withSpec("module SomeMod {typedef string foo;};")); //should work

		try {
			CLIENT1.registerTypespec(new RegisterTypespecParams()
					.withSpec("module SomeMod2 {typedef string foo;};"));
			fail("compiled spec without valid module");
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					containsString("Module SomeMod2 was not initialized"));
		}
	}

	@Test
	public void adminUserFacade() throws Exception {
		failAdmin(CLIENT2,
				"{\"command\": \"createWorkspace\"," +
				" \"user\": \"" + USER1 + "\"," +
				" \"params\": [{\"workspace\": \"" + USER1 + ":admintest\", \"globalread\": \"n\"," +
				"			   \"description\": \"mydesc\"}]}",
				"Unable to deserialize CreateWorkspaceParams out of params field");

		failAdmin(CLIENT2,
				"{\"command\": [\"createWorkspace\"]," +
				" \"user\": \"" + USER1 + "\"," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"globalread\": \"n\"," +
				"			   \"description\": \"mydesc\"}}",
				"Unable to deserialize a workspace admin command from the input");

		TypeReference<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> typeref
				= new TypeReference<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>>() {};
		TypeReference<List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>>> listtyperef
				= new TypeReference<List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>>>() {};

		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> wsinfo =
				CLIENT2.administer(new UObject(createData(
				"{\"command\": \"createWorkspace\"," +
				" \"user\": \"" + USER1 + "\"," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"globalread\": \"n\"," +
				"			   \"description\": \"mydesc\"}}")))
				.asClassInstance(typeref);

		List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> mt =
				new ArrayList<Tuple9<Long,String,String,String,Long,String,String,String,Map<String,String>>>();
		List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> notexpected =
				new ArrayList<Tuple9<Long,String,String,String,Long,String,String,String,Map<String,String>>>();
		notexpected.add(CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withWorkspace(USER1 + ":admintest")));
		checkWSInfoList(CLIENT2.listWorkspaceInfo(new ListWorkspaceInfoParams()), mt);

		List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> got =
				CLIENT2.administer(new UObject(createData(
						"{\"command\": \"listWorkspaces\"," +
						" \"user\": \"" + USER1 + "\"," +
						" \"params\": {}}"))).asClassInstance(listtyperef);
		checkWSInfoList(got, notexpected);

		checkWS(wsinfo, wsinfo.getE1(), wsinfo.getE4(), USER1 + ":admintest", USER1, 0, "a", "n", "unlocked", "mydesc", MT_META);
		CLIENT1.setGlobalPermission(new SetGlobalPermissionsParams().withWorkspace(USER1 + ":admintest")
				.withNewPermission("r"));

		checkWS(CLIENT1.getWorkspaceInfo(new WorkspaceIdentity().withId(wsinfo.getE1())),
				wsinfo.getE1(), wsinfo.getE4(), USER1 + ":admintest", USER1, 0, "a", "r", "unlocked", "mydesc", MT_META);
		try {
			CLIENT2.getWorkspaceDescription(new WorkspaceIdentity().withId(wsinfo.getE1()));
		} catch (ServerException se) {
			assertThat("correct excep message", se.getLocalizedMessage(),
					is("User " + USER2 + " cannot read workspace " + wsinfo.getE1()));
		}

		failAdmin(CLIENT2,
				"{\"command\": \"createWorkspace\"," +
				" \"user\": null," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"globalread\": \"r\"," +
				"			   \"description\": \"mydesc\"}}",
				"User may not be null");
		failAdmin(CLIENT2,
				"{\"command\": \"createWorkspace\"," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"globalread\": \"r\"," +
				"			   \"description\": \"mydesc\"}}",
				"User may not be null");

		failAdmin(CLIENT2,
				"{\"command\": \"createWorkspace\"," +
				" \"user\": \"thisisnotarealuserihopeorthistestwillfail\"," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"globalread\": \"r\"," +
				"			   \"description\": \"mydesc\"}}",
				"User thisisnotarealuserihopeorthistestwillfail is not a valid user");
		failAdmin(CLIENT2,
				"{\"command\": \"createWorkspace\"," +
				" \"user\": \"" + USER1 + "\"," +
				" \"params\": null}", "Method parameters CreateWorkspaceParams may not be null");

		@SuppressWarnings("unchecked")
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> objinfo =
				list2ObjTuple11(((List<List<Object>>) CLIENT2.administer(new UObject(createData(
				"{\"command\": \"saveObjects\"," +
				" \"user\": \"" + USER1 + "\"," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"objects\": " +
						"[{\"type\": \"" + SAFE_TYPE + "\", \"data\": {\"foo\": 1}, " +
						"\"name\": \"auto1\", \"meta\": {\"b\": 2}}]}}")))
						.asInstance()).get(0));
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("foo", 1);
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("b", "2");
		checkInfo(objinfo, 1, "auto1", SAFE_TYPE, 1, USER1, wsinfo.getE1(),
				 USER1 + ":admintest", "51014459947d55c836fe74faf224e54a", 9,
				 meta);
		checkSavedObjects(Arrays.asList(new ObjectIdentity().withRef( USER1 + ":admintest/auto1")),
				1, "auto1", SAFE_TYPE, 1, USER1, wsinfo.getE1(),
				 USER1 + ":admintest", "51014459947d55c836fe74faf224e54a", 9,
				 meta, data);

		failAdmin(CLIENT2,
				"{\"command\": \"saveObjects\"," +
				" \"user\": null," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"objects\": " +
						"[{\"type\": \"" + SAFE_TYPE + "\", \"data\": {\"foo\": 1}, " +
						"\"name\": \"bar\", \"meta\": {\"b\": 2}}]}}",
				 "User may not be null");
		failAdmin(CLIENT2,
				"{\"command\": \"saveObjects\"," +
				" \"user\": \"thisisalsonotavalidkbaseuserihope\"," +
				" \"params\": {\"workspace\": \"" + USER1 + ":admintest\", \"objects\": " +
						"[{\"type\": \"" + SAFE_TYPE + "\", \"data\": {\"foo\": 1}, " +
						"\"name\": \"bar\", \"meta\": {\"b\": 2}}]}}",
				"User thisisalsonotavalidkbaseuserihope is not a valid user");
		failAdmin(CLIENT2,
				"{\"command\": \"saveObjects\"," +
						" \"user\": \"" + USER1 + "\"," +
				" \"params\": null}",
				"Method parameters SaveObjectsParams may not be null");

		WorkspaceIdentity ws = new WorkspaceIdentity().withWorkspace(USER1 + ":admintest");

		Map<String, Object> adminParams = new HashMap<String, Object>();

		String wsstr = USER1 + ":admintest";

		adminParams.put("command", "setGlobalPermission");
		adminParams.put("user", USER1);
		adminParams.put("params", new SetGlobalPermissionsParams()
				.withWorkspace(wsstr).withNewPermission("n"));
		CLIENT2.administer(new UObject(adminParams));

		Map<String, String> expected = new HashMap<String, String>();
		expected.put(USER1, "a");
		assertThat("admin set global perm correctly", CLIENT1.getPermissionsMass(gPM(ws)).getPerms().get(0),
				is(expected));

		adminParams.put("params", new SetGlobalPermissionsParams()
				.withWorkspace(wsstr).withNewPermission("r"));
		CLIENT2.administer(new UObject(adminParams));
		expected.put("*", "r");
		assertThat("admin set global perm correctly", CLIENT1.getPermissionsMass(gPM(ws)).getPerms().get(0),
				is(expected));

		adminParams.put("user", USER2);
		failAdmin(CLIENT2, adminParams, "User " + USER2 + " may not set global permission on workspace " + wsstr);

		adminParams.put("command", "setPermissions");
		adminParams.put("params", new SetPermissionsParams().withWorkspace(wsstr)
				.withNewPermission("w").withUsers(Arrays.asList(USER2)));
		CLIENT2.administer(new UObject(adminParams));
		expected.put(USER2, "w");
		assertThat("admin set perm correctly", CLIENT1.getPermissionsMass(gPM(ws)).getPerms().get(0),
				is(expected));

		Map<String, Object> setWSownerParams = new HashMap<String, Object>();
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace(wsstr);
		setWSownerParams.put("wsi", wsi);
		setWSownerParams.put("new_user", USER2);
		setWSownerParams.put("new_name", "setWSOwnerParams");
		adminParams.put("command", "setWorkspaceOwner");
		adminParams.put("params", setWSownerParams);
		wsinfo = CLIENT2.administer(new UObject(adminParams)).asClassInstance(typeref);
		checkWS(wsinfo, wsinfo.getE1(), wsinfo.getE4(), "setWSOwnerParams", USER2, 1, "a", "r", "unlocked", "mydesc", MT_META);
		wsi = new WorkspaceIdentity().withWorkspace("setWSOwnerParams");
		assertThat("owner changed correctly", CLIENT1.getWorkspaceInfo(wsi).getE3(), is(USER2));

		setWSownerParams.put("wsi", wsi);
		setWSownerParams.put("new_user", null);
		try {
			CLIENT2.administer(new UObject(adminParams));
		} catch (ServerException se) {
			assertThat("correct exception", se.getMessage(),
					is("newUser cannot be null"));
		}
	}

	private GetPermissionsMassParams gPM(WorkspaceIdentity ws) {
		return new GetPermissionsMassParams().withWorkspaces(Arrays.asList(ws));
	}

	@Test
	public void adminGetPermissionsWithUser() throws Exception {
		WorkspaceIdentity ws = new WorkspaceIdentity().withWorkspace(USER1 + ":admintest");

		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws.getWorkspace()));

		Map<String, Object> adminParams = new HashMap<String, Object>();
		adminParams.put("command", "getPermissions");
		adminParams.put("user", USER1);
		adminParams.put("params", ws);
		@SuppressWarnings("unchecked")
		Map<String, String> res = CLIENT2.administer(new UObject(adminParams))
				.asClassInstance(Map.class);
		assertThat("admin gets correct params", res,
				is((Map<String, String>) ImmutableMap.of(USER1, "a")));

		adminParams.put("user", USER2);
		@SuppressWarnings("unchecked")
		Map<String, String> res2 = CLIENT2.administer(new UObject(adminParams))
				.asClassInstance(Map.class);
		assertThat("admin gets correct params", res2,
				is((Map<String, String>) ImmutableMap.of(USER2, "n")));

		adminParams.put("user", "thisisacrazykbaseuserthatdoesntexistforsure");
		failAdmin(CLIENT2, adminParams,
				"User thisisacrazykbaseuserthatdoesntexistforsure is not a valid user");
	}

	@Test
	public void adminGetPermissionsNoUser() throws Exception {
		WorkspaceIdentity ws = new WorkspaceIdentity().withWorkspace(USER1 + ":admintest");

		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws.getWorkspace()));

		Map<String, Object> adminParams = new HashMap<String, Object>();
		adminParams.put("command", "getPermissions");
		adminParams.put("params", ws);
		@SuppressWarnings("unchecked")
		Map<String, String> res = CLIENT2.administer(new UObject(adminParams))
				.asClassInstance(Map.class);
		assertThat("admin gets correct params", res,
				is((Map<String, String>) ImmutableMap.of(USER1, "a")));
	}

	@Test
	public void adminGetPermissionsMass() throws Exception {
		WorkspaceIdentity ws = new WorkspaceIdentity().withWorkspace(USER1 + ":admintest");
		WorkspaceIdentity ws2 = new WorkspaceIdentity().withWorkspace(USER1 + ":admintest2");

		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws.getWorkspace()));
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws2.getWorkspace()));
		CLIENT1.setPermissions(new SetPermissionsParams().withWorkspace(ws2.getWorkspace())
				.withNewPermission("r").withUsers(Arrays.asList(USER2)));

		Map<String, Object> adminParams = ImmutableMap.of(
				"command", "getPermissionsMass",
				"params", new GetPermissionsMassParams()
					.withWorkspaces(Arrays.asList(ws, ws2)));
		@SuppressWarnings("unchecked")
		Map<String, Object> res = CLIENT2.administer(new UObject(adminParams))
				.asClassInstance(Map.class);
		assertThat("admin gets correct params", res,
				is((Map<String, Object>) ImmutableMap.of("perms", (Object) Arrays.asList(
						ImmutableMap.of(USER1, "a"),
						ImmutableMap.of(USER1, "a", USER2, "r")))));
	}

	@Test
	public void adminGetWorkspaceInfo() throws Exception {
		WorkspaceIdentity ws = new WorkspaceIdentity().withWorkspace(USER1 + ":admintest");

		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace(ws.getWorkspace())
				.withDescription("whee")
				.withMeta(ImmutableMap.of("foo", "bar")));

		Tuple9<Long, String, String, String, Long, String, String, String,
				Map<String, String>> wsinfo = CLIENT2.administer(new UObject(ImmutableMap.of(
						"command", "getWorkspaceInfo",
						"params", new WorkspaceIdentity().withWorkspace(ws.getWorkspace()))))
				.asClassInstance(new TypeReference<Tuple9<Long, String, String, String,
						Long, String, String, String, Map<String, String>>>() {});

		checkWS(wsinfo, 1, wsinfo.getE4(), ws.getWorkspace(), USER1, 0, "n", "n", "unlocked",
				"whee", ImmutableMap.of("foo", "bar"));

		wsinfo = CLIENT2.administer(new UObject(ImmutableMap.of(
				"command", "getWorkspaceInfo",
				"params", new WorkspaceIdentity().withId(1L))))
				.asClassInstance(new TypeReference<Tuple9<Long, String, String, String,
						Long, String, String, String, Map<String, String>>>() {});

		checkWS(wsinfo, 1, wsinfo.getE4(), ws.getWorkspace(), USER1, 0, "n", "n", "unlocked",
				"whee", ImmutableMap.of("foo", "bar"));
	}

	@Test
	public void adminGetObjectAndInfo() throws Exception {
		final WorkspaceIdentity ws = new WorkspaceIdentity().withWorkspace(USER1 + ":admintest");

		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws.getWorkspace()));

		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(ws.getWorkspace())
				.withObjects(Arrays.asList(new ObjectSaveData()
						.withData(new UObject(ImmutableMap.of("foo", "bar")))
						.withName("whee")
						.withType(SAFE_TYPE))));

		final GetObjectInfo3Results ob = CLIENT2.administer(new UObject(ImmutableMap.of(
						"command", "getObjectInfo",
						"params", new GetObjectInfo3Params().withObjects(Arrays.asList(
								new ObjectSpecification().withRef("1/1")))
				))).asClassInstance(GetObjectInfo3Results.class);

		assertThat("incorrect object count", ob.getInfos().size(), is(1));
		checkInfo(ob.getInfos().get(0), 1, "whee", SAFE_TYPE, 1, USER1, 1L, ws.getWorkspace(),
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13, null);
		assertThat("incorrect ref path", ob.getPaths().get(0), is(Arrays.asList("1/1/1")));

		final GetObjects2Results ob2 = CLIENT2.administer(new UObject(ImmutableMap.of(
						"command", "getObjects",
						"params", new GetObjects2Params().withObjects(Arrays.asList(
								new ObjectSpecification().withRef("1/1")))
				))).asClassInstance(GetObjects2Results.class);

		assertThat("incorrect object count", ob2.getData().size(), is(1));
		checkData(ob2.getData().get(0), 1, "whee", SAFE_TYPE, 1, USER1, 1L, ws.getWorkspace(),
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13, new HashMap<>(),
				ImmutableMap.of("foo", "bar"));
		assertThat("correct path", ob2.getData().get(0).getPath(), is(Arrays.asList("1/1/1")));
	}

	@Test
	public void adminGetObjectAndInfoFail() throws Exception {
		final WorkspaceIdentity ws = new WorkspaceIdentity().withWorkspace(USER1 + ":admintest");

		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws.getWorkspace()));

		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(ws.getWorkspace())
				.withObjects(Arrays.asList(new ObjectSaveData()
						.withData(new UObject(ImmutableMap.of("foo", "bar")))
						.withName("whee")
						.withType(SAFE_TYPE))));

		CLIENT1.deleteObjects(Arrays.asList(new ObjectIdentity().withWsid(1L).withObjid(1L)));

		// mostly tests that the actual admin username is used in the error
		failAdmin(CLIENT2, ImmutableMap.of(
				"command", "getObjectInfo",
				"params", new GetObjectInfo3Params().withObjects(Arrays.asList(
						new ObjectSpecification().withRef("1/1").withFindReferencePath(1L)))
				), "The latest version of object 1 in workspace 1 is not accessible to user " +
						USER2);

		failAdmin(CLIENT2, ImmutableMap.of(
				"command", "getObjects",
				"params", new GetObjects2Params().withObjects(Arrays.asList(
						new ObjectSpecification().withRef("1/1").withFindReferencePath(1L)))
				), "The latest version of object 1 in workspace 1 is not accessible to user " +
						USER2);
	}

	@Test
	public void adminListWorkspaceIDs() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("own"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("read"));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("read")
				.withUsers(Arrays.asList(USER1)).withNewPermission("r"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams().withWorkspace("write"));
		CLIENT2.setPermissions(new SetPermissionsParams().withWorkspace("write")
				.withUsers(Arrays.asList(USER1)).withNewPermission("w"));

		final ListWorkspaceIDsResults res = CLIENT2.administer(new UObject(ImmutableMap.of(
				"command", "listWorkspaceIDs",
				"params", new ListWorkspaceIDsParams().withPerm("w"),
				"user", USER1)))
				.asClassInstance(ListWorkspaceIDsResults.class);
		assertThat("incorrect workspaces", res.getWorkspaces(), is(Arrays.asList(1L, 3L)));
		assertThat("incorrect pub workspaces", res.getPub(), is(Arrays.asList()));

		final Map<String, Object> cmd = new HashMap<>();
		cmd.put("command", "listWorkspaceIDs");
		cmd.put("params", null);
		cmd.put("user", USER1);
		failAdmin(CLIENT2, cmd, "Method parameters ListWorkspaceIDsParams may not be null");

		cmd.put("params", new ListWorkspaceIDsParams());
		cmd.put("user", null);
		failAdmin(CLIENT2, cmd, "User may not be null");
	}

	@Test
	public void adminListObjectsWithUser() throws Exception {
		final WorkspaceIdentity ws = new WorkspaceIdentity().withWorkspace(USER1 + ":admintest");

		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace(ws.getWorkspace()));

		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(ws.getWorkspace())
				.withObjects(Arrays.asList(new ObjectSaveData()
						.withData(new UObject(ImmutableMap.of("foo", "bar")))
						.withName("whee")
						.withType(SAFE_TYPE))));

		final List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>>> ob = CLIENT2.administer(new UObject(ImmutableMap.of(
						"command", "listObjects",
						"user", USER1,
						"params", new ListObjectsParams().withWorkspaces(
								Arrays.asList(ws.getWorkspace()))
				))).asClassInstance(new TypeReference<List<Tuple11<Long, String, String, String,
						Long, String, Long, String, String, Long, Map<String, String>>>>() {});

		assertThat("incorrect object count", ob.size(), is(1));
		checkInfo(ob.get(0), 1, "whee", SAFE_TYPE, 1, USER1, 1L, ws.getWorkspace(),
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13, null);
	}

	final TypeReference<List<Tuple11<Long, String, String, String,
			Long, String, Long, String, String, Long, Map<String, String>>>> OBJ_TYPEREF =
					new TypeReference<List<Tuple11<Long, String, String, String,
						Long, String, Long, String, String, Long, Map<String, String>>>>() {};

	@Test
	public void adminlistObjectsWithoutUser() throws Exception {
		final WorkspaceIdentity ws = new WorkspaceIdentity().withWorkspace(USER1 + ":admintest");
		final WorkspaceIdentity ws2 = new WorkspaceIdentity().withWorkspace(USER1 + ":admintest2");
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws.getWorkspace()));
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws2.getWorkspace()));

		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(ws.getWorkspace())
				.withObjects(Arrays.asList(new ObjectSaveData()
						.withData(new UObject(ImmutableMap.of("foo", "bar")))
						.withName("whee")
						.withType(SAFE_TYPE))));

		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(ws2.getWorkspace())
				.withObjects(Arrays.asList(new ObjectSaveData()
						.withData(new UObject(ImmutableMap.of("foo", "bar")))
						.withName("whee2")
						.withType(SAFE_TYPE1))));

		List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>>> ob = CLIENT2.administer(new UObject(ImmutableMap.of(
						"command", "listObjects",
						"params", new ListObjectsParams().withWorkspaces(
								Arrays.asList(ws.getWorkspace()))
				))).asClassInstance(OBJ_TYPEREF);

		assertThat("incorrect object count", ob.size(), is(1));
		checkInfo(ob.get(0), 1, "whee", SAFE_TYPE, 1, USER1, 1L, ws.getWorkspace(),
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13, null);

		ob = CLIENT2.administer(new UObject(ImmutableMap.of(
				"command", "listObjects",
				"params", new ListObjectsParams().withWorkspaces(
						Arrays.asList(ws.getWorkspace(), ws2.getWorkspace()))
						.withType(SAFE_TYPE1)
		))).asClassInstance(new TypeReference<List<Tuple11<Long, String, String, String,
				Long, String, Long, String, String, Long, Map<String, String>>>>() {});

		assertThat("incorrect object count", ob.size(), is(1));
		checkInfo(ob.get(0), 1, "whee2", SAFE_TYPE1, 1, USER1, 2L, ws2.getWorkspace(),
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13, null);
	}

	@Test
	public void adminListObjectsFailOnWorkspaceCounts() throws Exception {
		adminListObjectsFail(null, null);
		adminListObjectsFail(Collections.emptyList(), Collections.emptyList());

		final List<String> ws = IntStream.range(1, 5002).mapToObj(i -> "a" + i)
				.collect(Collectors.toList());
		final List<Long> longs = LongStream.range(1, 5001).mapToObj(i -> i)
				.collect(Collectors.toList());
		adminListObjectsFail(ws, longs);
		ws.remove(ws.size() - 1);
		longs.add(7000L);
		adminListObjectsFail(ws, longs);
	}

	private void adminListObjectsFail(final List<String> ws, final List<Long> ids)
			throws Exception {
		try {
			CLIENT2.administer(new UObject(ImmutableMap.of(
					"command", "listObjects",
					"params", new ListObjectsParams().withWorkspaces(ws).withIds(ids))));
			fail("expected exception");
		} catch (ServerException e) {
			assertThat("incorrect exception", e.getMessage(), is(
					"At least one and no more than 10000 workspaces must be specified"));
		}
	}

	@Test
	public void adminListObjectHistory() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("foo"));

		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("foo")
				.withObjects(Arrays.asList(new ObjectSaveData()
						.withData(new UObject(ImmutableMap.of("foo", "bar")))
						.withMeta(ImmutableMap.of("foo", "bar1"))
						.withName("whee")
						.withType(SAFE_TYPE))));

		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("foo")
				.withObjects(Arrays.asList(new ObjectSaveData()
						.withData(new UObject(ImmutableMap.of("foo", "bar")))
						.withMeta(ImmutableMap.of("foo", "bar2"))
						.withName("whee")
						.withType(SAFE_TYPE))));

		final List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>>> res = CLIENT2.administer(new UObject(ImmutableMap.of(
				"command", "getObjectHistory",
				"params", new ObjectIdentity().withWorkspace("foo").withName("whee"))))
				.asClassInstance(OBJ_TYPEREF);

		checkInfo(res.get(0), 1, "whee", SAFE_TYPE, 1, USER1, 1L, "foo",
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13, ImmutableMap.of("foo", "bar1"));
		checkInfo(res.get(1), 1, "whee", SAFE_TYPE, 2, USER1, 1L, "foo",
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13, ImmutableMap.of("foo", "bar2"));

		final Map<String, Object> cmd = new HashMap<>();
		cmd.put("command", "getObjectHistory");
		cmd.put("params", null);
		failAdmin(CLIENT2, cmd, "Method parameters ObjectIdentity may not be null");
	}

	@Test
	public void adminFailUserNotAdmin() throws Exception {
		failAdmin(CLIENT1, Collections.<String, Object>emptyMap(),
				"User " + USER1 + " is not an admin");
	}

	@Test
	public void adminDeleteWorkspace() throws Exception {
		final WorkspaceIdentity delws = new WorkspaceIdentity().withWorkspace("delws");
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(delws.getWorkspace())
				.withDescription("foo"));
		final WorkspaceIdentity delwsid = new WorkspaceIdentity().withId(1L);
		final Map<String, Object> params = new HashMap<>();
		params.put("command", "deleteWorkspace");
		params.put("params", delws);

		//test delete
		CLIENT2.administer(new UObject(params));
		failGetWSDesc(delws, "Workspace delws is deleted");
		params.put("params", new WorkspaceIdentity().withWorkspace("foo"));
		failAdmin(CLIENT2, params, "No workspace with name foo exists");


		//test undelete
		params.put("command", "undeleteWorkspace");
		params.put("params", delwsid);
		CLIENT2.administer(new UObject(params));
		assertThat("incorrect ws description", CLIENT1.getWorkspaceDescription(delws), is("foo"));
		params.put("params", new WorkspaceIdentity().withId(2L));
		failAdmin(CLIENT2, params, "No workspace with id 2 exists");
	}

	@Test
	public void adminSetWorkspaceDescription() throws Exception {
		final WorkspaceIdentity ws = new WorkspaceIdentity().withWorkspace("setdescws");
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws.getWorkspace())
				.withDescription("foo"));
		final WorkspaceIdentity wsid = new WorkspaceIdentity().withId(1L);

		// update with name
		final Map<String, Object> params = new HashMap<>();
		params.put("command", "setWorkspaceDescription");
		params.put("params", new SetWorkspaceDescriptionParams()
				.withDescription("new desc")
				.withWorkspace("setdescws"));

		CLIENT2.administer(new UObject(params));
		assertThat("incorrect desc", CLIENT1.getWorkspaceDescription(wsid), is("new desc"));

		// update with id
		params.put("params", new SetWorkspaceDescriptionParams()
				.withDescription("new desc2")
				.withId(1L));

		CLIENT2.administer(new UObject(params));
		assertThat("incorrect desc", CLIENT1.getWorkspaceDescription(wsid), is("new desc2"));
	}

	@Test
	public void adminGetWorkspaceDescription() throws Exception {
		final WorkspaceIdentity ws = new WorkspaceIdentity().withWorkspace("getdescws");
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(ws.getWorkspace())
				.withDescription("my desc"));

		// get by name
		final Map<String, Object> params = new HashMap<>();
		params.put("command", "getWorkspaceDescription");
		params.put("params", new WorkspaceIdentity().withWorkspace("getdescws"));

		assertThat("incorrect description", CLIENT2.administer(new UObject(params)).asScalar(),
				is("my desc"));

		// get by id
		params.put("params", new WorkspaceIdentity().withId(1L));

		assertThat("incorrect description", CLIENT2.administer(new UObject(params)).asScalar(),
				is("my desc"));
	}

	@Test
	public void alterAdminObjectMetadata() throws Exception {
		final Map<String, Object> data = ImmutableMap.of("foo", "bar");
		final UObject udata = new UObject(data); 
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("objadminmeta"));
		
		final ImmutableMap<String, String> meta1 = ImmutableMap.of("1", "2");
		final ImmutableMap<String, String> meta2 = ImmutableMap.of("3", "4");
		final ImmutableMap<String, String> meta3 = ImmutableMap.of("5", "6");
		final ImmutableMap<String, String> meta4 = ImmutableMap.of("7", "8");
		CLIENT1.saveObjects(new SaveObjectsParams()
				.withId(1L)
				.withObjects(Arrays.asList(
						new ObjectSaveData().withType(SAFE_TYPE).withName("one").withData(udata)
								.withMeta(meta1),
						new ObjectSaveData().withType(SAFE_TYPE).withName("two").withData(udata)
								.withMeta(meta2),
						new ObjectSaveData().withType(SAFE_TYPE).withName("two").withData(udata)
								.withMeta(meta3),
						new ObjectSaveData().withType(SAFE_TYPE).withName("three").withData(udata)
								.withMeta(meta4)
				))
		);
		final ObjectIdentity oi1 = new ObjectIdentity().withRef("1/1/1");
		final ObjectIdentity oi2_1 = new ObjectIdentity().withRef("1/2/1");
		final ObjectIdentity oi2_2 = new ObjectIdentity().withRef("1/2/2");
		final ObjectIdentity oi3 = new ObjectIdentity().withRef("1/3/1");
		
		CLIENT2.alterAdminObjectMetadata(new AlterAdminObjectMetadataParams()
				.withUpdates(Arrays.asList(
						new ObjectMetadataUpdate()
								.withOi(new ObjectIdentity().withRef("1/1"))
								.withNew(ImmutableMap.of("a", "b", "c", "d")),
						new ObjectMetadataUpdate()
								.withOi(new ObjectIdentity()
										.withWorkspace("objadminmeta")
										.withName("two")
										.withVer(1L)
								)
								.withNew(ImmutableMap.of("e", "f", "h", "pointed stick")),
						new ObjectMetadataUpdate()
								.withOi(new ObjectIdentity().withWsid(1L).withObjid(2L))
								.withNew(ImmutableMap.of("i", "j", "k", "iter var"))
				))
		);
		checkSavedObjects(list(oi1), 1L, "one", SAFE_TYPE, 1, USER1, 1L, "objadminmeta",
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13L, meta1,
				ImmutableMap.of("a", "b", "c", "d"), data);
		checkSavedObjects(list(oi2_1), 2L, "two", SAFE_TYPE, 1, USER1, 1L, "objadminmeta",
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13L, meta2,
				ImmutableMap.of("e", "f", "h", "pointed stick"), data);
		checkSavedObjects(list(oi2_2), 2L, "two", SAFE_TYPE, 2, USER1, 1L, "objadminmeta",
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13L, meta3,
				ImmutableMap.of("i", "j", "k", "iter var"), data);
		checkSavedObjects(list(oi3), 3L, "three", SAFE_TYPE, 1, USER1, 1L, "objadminmeta",
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13L, meta4, MT_META, data);
		
		// test remove
		CLIENT2.alterAdminObjectMetadata(new AlterAdminObjectMetadataParams()
				.withUpdates(Arrays.asList(
						new ObjectMetadataUpdate()
								.withOi(new ObjectIdentity().withRef("1/1"))
								.withNew(ImmutableMap.of("x", "y"))
								.withRemove(Arrays.asList("c")),
						new ObjectMetadataUpdate()
								.withOi(new ObjectIdentity()
										.withWorkspace("objadminmeta")
										.withName("two")
										.withVer(1L)
								)
								.withNew(ImmutableMap.of("e", "fresh fruit")),
						new ObjectMetadataUpdate()
								.withOi(new ObjectIdentity().withWsid(1L).withObjid(2L))
								.withRemove(Arrays.asList("i", "k"))
				))
		);
		checkSavedObjects(list(oi1), 1L, "one", SAFE_TYPE, 1, USER1, 1L, "objadminmeta",
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13L, meta1,
				ImmutableMap.of("a", "b", "x", "y"), data);
		checkSavedObjects(list(oi2_1), 2L, "two", SAFE_TYPE, 1, USER1, 1L, "objadminmeta",
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13L, meta2,
				ImmutableMap.of("e", "fresh fruit", "h", "pointed stick"), data);
		checkSavedObjects(list(oi2_2), 2L, "two", SAFE_TYPE, 2, USER1, 1L, "objadminmeta",
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13L, meta3, MT_META, data);
		checkSavedObjects(list(oi3), 3L, "three", SAFE_TYPE, 1, USER1, 1L, "objadminmeta",
				"9bb58f26192e4ba00f01e2e7b136bbd8", 13L, meta4, MT_META, data);
	}
	
	
	@Test
	public void alterAdminObjectMetaFailPerms() throws Exception {
		final String err = "Full workspace serivce administrator permissions are required to "
				+ "alter object metadata";
		// standard admin handler
		alterAdminObjectMetaFail(CLIENT1, new AlterAdminObjectMetadataParams(),
				new ServerException(err, 1, "name"));
		// auth service admin handler
		alterAdminObjectMetaFail(CLIENT_AA_ADMIN_NONE, new AlterAdminObjectMetadataParams(),
				new ServerException(err, 1, "name"));
		alterAdminObjectMetaFail(CLIENT_AA_ADMIN_READ, new AlterAdminObjectMetadataParams(),
				new ServerException(err, 1, "name"));
	}
	
	@Test
	public void alterAdminObjectMetaFail() throws Exception {
		// test a couple examples of failing calls; unit tests cover everything so no need to get
		// too thorough
		final AlterAdminObjectMetadataParams params = new AlterAdminObjectMetadataParams()
				.withUpdates(Arrays.asList(new ObjectMetadataUpdate()
						.withOi(new ObjectIdentity().withRef("3/1"))
				));
		alterAdminObjectMetaFail( CLIENT2, params, new ServerException(
				"Error processing update index 0: A metadata update is required", 1, "name"));
		
		params.getUpdates().get(0).withRemove(Arrays.asList("foo"));
		alterAdminObjectMetaFail(CLIENT2, params, new ServerException(
				"Object 1 cannot be accessed: No workspace with id 3 exists", 1, "name"));
	}
	
	private void alterAdminObjectMetaFail(
			final WorkspaceClient client,
			final AlterAdminObjectMetadataParams update,
			final Exception expected) {
		try {
			client.alterAdminObjectMetadata(update);
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, expected);
		}
	}

	@Test
	public void getAllWorkspaceOwners() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("getAllWorkspaceOwners1"));
		CLIENT2.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("getAllWorkspaceOwners2"));
		String cmd = "{\"command\":\"listWorkspaceOwners\"}";
		List<String> owners = CLIENT2.administer(new UObject(
				new JsonTokenStream(cmd))).asInstance();
		Set<String> expected = new HashSet<String>(Arrays.asList(USER1, USER2));
		assertThat("returned expected users", new HashSet<String>(owners),
				is(expected));

		failAdmin(CLIENT1, cmd, "User " + USER1 + " is not an admin");
	}

	@Test
	public void checkFloat() throws Exception {
		final String specFloat =
				"module FloatSpec {" +
					"typedef structure {" +
						"float f;" +
					"} F;" +
				"};";
		CLIENT1.requestModuleOwnership("FloatSpec");
		administerCommand(CLIENT2, "approveModRequest", "module", "FloatSpec");
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec(specFloat)
			.withNewTypes(Arrays.asList("F")));
		String type = "FloatSpec.F-0.1";

		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("float"));
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("f", 1.3e10);

		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("float")
				.withObjects(Arrays.asList(new ObjectSaveData().withData(new UObject(data))
				.withType(type).withName("f"))));

		Map<String, Object> got = CLIENT1.getObjects2(new GetObjects2Params()
				.withObjects(Arrays.asList(new ObjectSpecification()
				.withWorkspace("float").withName("f")))).getData().get(0).getData().asInstance();
		assertThat("got correct float back", got, is(data));
	}

	@Test
	public void alterWorkspaceMetadata() throws Exception {
		Map<String, String> meta = new HashMap<String, String>();
		meta.put("foo", "bar");
		WorkspaceIdentity wsi = new WorkspaceIdentity().withWorkspace("metadata");
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info =
				CLIENT1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace(wsi.getWorkspace()).withMeta(meta));
		checkWS(info, info.getE1(), info.getE4(), wsi.getWorkspace(), USER1, 0, "a",
				"n", "unlocked", null, meta);

		Map<String, String> newmeta = new HashMap<String, String>();
		newmeta.put("baz", "bing");
		newmeta.put("baf", "bat");
		meta.put("baz", "bing");
		meta.put("baf", "bat");
		meta.remove("foo");
		CLIENT1.alterWorkspaceMetadata(new AlterWorkspaceMetadataParams()
				.withRemove(Arrays.asList("foo")).withNew(newmeta)
				.withWsi(wsi));
		Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>> info1 =
				CLIENT1.getWorkspaceInfo(wsi);
		checkWS(info1, info.getE1(), info1.getE4(), wsi.getWorkspace(), USER1, 0, "a",
				"n", "unlocked", null, meta);

		meta.remove("baz");
		CLIENT1.alterWorkspaceMetadata(new AlterWorkspaceMetadataParams()
				.withRemove(Arrays.asList("baz")).withWsi(wsi));
		info1 = CLIENT1.getWorkspaceInfo(wsi);
		checkWS(info1, info.getE1(), info1.getE4(), wsi.getWorkspace(), USER1, 0, "a",
		"n", "unlocked", null, meta);

		newmeta.clear();
		newmeta.put("baf", "thing");
		newmeta.put("123", "456");
		meta.put("baf", "thing");
		meta.put("123", "456");
		CLIENT1.alterWorkspaceMetadata(new AlterWorkspaceMetadataParams()
				.withNew(newmeta).withWsi(wsi));
		info1 = CLIENT1.getWorkspaceInfo(wsi);
		checkWS(info1, info.getE1(), info1.getE4(), wsi.getWorkspace(), USER1, 0, "a",
		"n", "unlocked", null, meta);

		failAlterWSMeta(CLIENT1, new AlterWorkspaceMetadataParams().withRemove(Arrays.asList("foo")),
				"WorkspaceIdentifier cannot be null");
		failAlterWSMeta(CLIENT1, new AlterWorkspaceMetadataParams().withWsi(wsi),
				"Must provide metadata keys to add or remove");
		failAlterWSMeta(CLIENT1, new AlterWorkspaceMetadataParams().withWsi(wsi)
				.withRemove(new LinkedList<String>()).withNew(MT_META),
				"Must provide metadata keys to add or remove");
		failAlterWSMeta(CLIENT2, new AlterWorkspaceMetadataParams().withWsi(wsi)
				.withNew(newmeta),
				"User " + USER2 + " may not alter metadata for workspace " + wsi.getWorkspace());

		AlterWorkspaceMetadataParams p = new AlterWorkspaceMetadataParams();
		p.setAdditionalProperties("foo", "bar");
		failAlterWSMeta(CLIENT1, p, "Unexpected arguments in AlterWorkspaceMetadataParams: foo");
	}

	@Test
	public void testTypeMD5() throws Exception {
		String typeDefName = "SomeModule.AType";
		Map<String,String> type2md5 = CLIENT1.translateToMD5Types(Arrays.asList(typeDefName));
		String md5TypeDef = type2md5.get(typeDefName);
		assertThat(md5TypeDef, is(notNullValue()));
		Map<String, List<String>> md52semantic = CLIENT1.translateFromMD5Types(Arrays.asList(md5TypeDef));
		assertThat(md52semantic.size(), is(1));
		assertThat(md52semantic.get(md5TypeDef).contains("SomeModule.AType-1.0"), is(true));
	}

	@Test
	public void testGetInfo() throws Exception {
		WorkspaceClient cl = new WorkspaceClient(new URL("http://localhost:" +
				SERVER2.getServerPort()));
		String module = "UnreleasedModule";
		try {
			cl.getModuleInfo(new GetModuleInfoParams().withMod(module));
			fail();
		} catch (Exception ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains(
					"Module wasn't uploaded: UnreleasedModule"), is(true));
		}
		assertThat(cl.listModuleVersions(new ListModuleVersionsParams()
				.withType("UnreleasedModule.AType-0.1")).getVers().size(), is(1));
		assertThat(cl.getJsonschema("UnreleasedModule.AType-0.1").length() > 0, is(true));
		cl = CLIENT_FOR_SRV2;
		assertThat(new HashSet<String>(cl.listModules(
				new ListModulesParams().withOwner(USER2))).contains("UnreleasedModule"), is(true));
		assertThat(cl.getModuleInfo(new GetModuleInfoParams().withMod(module))
				.getIsReleased(), is(0L));
		assertThat(cl.listModuleVersions(new ListModuleVersionsParams().withMod(module))
				.getVers().size(), is(1));
		assertThat(cl.getTypeInfo("UnreleasedModule.AType").getTypeVers().size(), is(1));
		assertThat(cl.getTypeInfo("UnreleasedModule.AType-0.1").getTypeVers().size(), is(1));
		assertThat(cl.getJsonschema("UnreleasedModule.AType").length() > 0, is(true));
		@SuppressWarnings("deprecation")
		final List<String> funcVers = cl.getFuncInfo("UnreleasedModule.aFunc").getFuncVers();
		assertThat(funcVers.size(), is(1));
		try {
			cl.getTypeInfo("UnreleasedModule.AType-0.2");
			fail();
		} catch (Exception ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains(
					"Unable to locate type: UnreleasedModule.AType-0.2"), is(true));
		}
		try {
			cl.getJsonschema("UnreleasedModule.AType-0.2");
			fail();
		} catch (Exception ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains(
					"Unable to locate type: UnreleasedModule.AType-0.2"), is(true));
		}
	}

	@Test
	public void testSpecSync() throws Exception {
		CLIENT1.requestModuleOwnership("DepModule");
		administerCommand(CLIENT2, "approveModRequest", "module", "DepModule");
		final String urlForSrv2 = "http://localhost:" + SERVER2.getServerPort();
		final ModuleVersions vers = CLIENT_FOR_SRV2.listModuleVersions(
				new ListModuleVersionsParams().withMod("DepModule"));
		final String excStart = "Can not find local module SomeModule synchronized with " +
				"external version";
		//TODO TEST restore this part of the test when the MD5s are the same whether run in eclipse or via ant test
//		final String excEnd = "(md5=b38fc31dbccc829bba38a59e313c564e)";
		/* the first two versions of DepModule don't have the necessary version of SomeModule
		 * registered on server 1, and so registration will fail. version 3+ will succeed.
		 */
		int count = 0;
		for (long ver : vers.getVers()) {
			boolean ok = true;
			try {
				CLIENT1.registerTypespecCopy(new RegisterTypespecCopyParams()
					.withExternalWorkspaceUrl(urlForSrv2).withMod("DepModule")
					.withVersion(ver));
			} catch (Exception e) {
				ok = false;
				if (count < 2) {
					assertThat(String.format("Count %s: Incorrect exception start. Msg: %s",
							count, e.getMessage()), e.getMessage().startsWith(excStart), is(true));
					//TODO TEST restore this part of the test when the MD5s are the same whether run in eclipse or via ant test
//					assertThat(String.format("Count %s: Incorrect exception end. Msg: %s",
//							count, e.getMessage()), e.getMessage().endsWith(excEnd), is(true));
				} else {
					fail(String.format("Got exception when expected success on count %s: %s",
							count, e));
				}
			}
			if (ok) {
				if (count < 2) {
					fail("Register succeeded when fail expected on count " + count);
				}
				final String type = "DepModule.BType-" + (count - 1) + ".0";
				CLIENT1.releaseModule("DepModule");
				final Set<String> types = CLIENT1.getModuleInfo(
						new GetModuleInfoParams().withMod("DepModule")).getTypes().keySet();
				assertThat("Incorrect types on count " + count, types,
						is((Set<String>) new HashSet<>(Arrays.asList(type))));
			}
			count++;
		}
		assertThat("incorrect number of specs processed", count, is(4));
	}

	@Test
	public void testTypeAndModuleLookups() throws Exception {
		final String spec =
				"module TestModule { " +
						"typedef structure {string name; string seq;} Feature; "+
						"typedef structure {string name; list<Feature> features;} Genome; "+
						"typedef structure {string private_stuff;} InternalObj; "+
						"funcdef getFeature(string fid, string pattern) returns (Feature);" +
				"};";
		CLIENT1.requestModuleOwnership("TestModule");
		administerCommand(CLIENT2, "approveModRequest", "module", "TestModule");
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec(spec)
			.withNewTypes(Arrays.asList("Feature","Genome")));
		CLIENT1.releaseModule("TestModule");

		// make sure the list of modules includes the TestModule
		Map<String,String> moduleNamesInList = new HashMap<String,String>();
		for(String mod: CLIENT1.listModules(new ListModulesParams())) {
			moduleNamesInList.put(mod, "");
		}
		assertThat(moduleNamesInList.containsKey("TestModule"), is(true));

		// make sure that we can list the versions of this module, there should be just 2 visible to client1...
		assertThat(CLIENT1.listModuleVersions(new ListModuleVersionsParams().withMod("TestModule"))
				.getVers().size(), is(2));

		// make sure we can retrieve module info
		assertThat(CLIENT1.getModuleInfo(new GetModuleInfoParams().withMod("TestModule"))
				.getTypes().size(), is(2));

		// make sure we can get a json schema and parse it as a json document
		ObjectMapper map = new ObjectMapper();
		JsonNode schema = map.readTree(CLIENT1.getJsonschema("TestModule.Feature"));
		assertThat(schema.get("id").asText(), is("Feature"));

		// make sure we can get type info
		assertThat(CLIENT1.getTypeInfo("TestModule.Feature-1").getTypeDef(),
				is("TestModule.Feature-1.0"));

		// make sure we can get func info
		@SuppressWarnings("deprecation")
		final String funcDef = CLIENT1.getFuncInfo("TestModule.getFeature").getFuncDef();
		assertThat(funcDef, is("TestModule.getFeature-1.0"));
	}

	@Test
	public void moduleOwnerShipAndSpecRegistration() throws Exception {
		WorkspaceClient cl = CLIENT2;
		cl.setIsInsecureHttpConnectionAllowed(true);
		cl.requestModuleOwnership("TestModule2");
		administerCommand(CLIENT2, "approveModRequest", "module", "TestModule2");
		cl.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("module TestModule2{ typedef string StringType;};"));
		try {
			CLIENT1.registerTypespec(new RegisterTypespecParams()
				.withDryrun(0L)
				.withSpec("module TestModule2{ typedef int IntegerType;};"));
			fail("registered typespec to module with no perms");
		} catch (ServerException ex) {
			assertThat("got correct exception message", ex.getMessage(),
					is("User " + USER1 + " is not in list of owners of module TestModule2"));
		}

		CLIENT2.administer(new UObject(createData(
				"{\"command\": \"grantModuleOwnership\"," +
				" \"params\": {\"new_owner\": \"" + USER1 + "\", \"mod\": \"TestModule2\"," +
				"			   \"with_grant_option\": 1}}")));

		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("module TestModule2{ typedef int IntegerType;};"));

		CLIENT2.administer(new UObject(createData(
				"{\"command\": \"removeModuleOwnership\"," +
				" \"params\": {\"old_owner\": \"" + USER1 + "\", \"mod\": \"TestModule2\"}}")));

		try {
			CLIENT1.registerTypespec(new RegisterTypespecParams()
				.withDryrun(0L)
				.withSpec("module TestModule2{ typedef int IntegerType;};"));
			fail("registered typespec to module with no perms");
		} catch (ServerException ex) {
			assertThat("got correct exception message", ex.getMessage(),
					is("User " + USER1 + " is not in list of owners of module TestModule2"));
		}
	}

	@Test
	public void testListAllTypes() throws Exception {
		assertThat(CLIENT1.listAllTypes(new ListAllTypesParams().withWithEmptyModules(1L))
				.containsKey("RefSpec"), is(true));
		Map<String, Map<String, String>> types = CLIENT1.listAllTypes(new ListAllTypesParams());
		assertThat(types.containsKey("RefSpec"), is(false));
		assertThat(types.containsKey("SomeModule"), is(true));
		assertThat(types.get("SomeModule").get("AType"), is("1.0"));
	}

	@Test
	public void testGetAllTypeAndFuncInfo() throws Exception {
		assertThat(CLIENT1.getAllTypeInfo("RefSpec").size(), is(1));
		@SuppressWarnings("deprecation")
		final int size = CLIENT_FOR_SRV2.getAllFuncInfo("UnreleasedModule").size();
		assertThat(size, is(1));
	}

	@Test
	public void testModuleDiamondDependency() throws Exception {
		/////////////////////////// D v1 ////////////////////////////
		String modD = "TestModuleD";
		CLIENT1.requestModuleOwnership(modD);
		administerCommand(CLIENT2, "approveModRequest", "module", modD);
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withNewTypes(Arrays.asList("dType"))
			.withSpec("module " + modD + " { typedef int dType; };"));
		CLIENT1.releaseModule(modD);
		long dVer1 = CLIENT1.getModuleInfo(new GetModuleInfoParams().withMod(modD)).getVer();
		/////////////////////////// B depends on D v1 ////////////////////////////
		String modB = "TestModuleB";
		CLIENT1.requestModuleOwnership(modB);
		administerCommand(CLIENT2, "approveModRequest", "module", modB);
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withNewTypes(Arrays.asList("bType"))
			.withSpec("#include <" + modD + ">\n" +
					"module " + modB + " { " +
					"typedef " + modD + ".dType bType; " +
					"};"));
		CLIENT1.releaseModule(modB);
		long bVer = CLIENT1.getModuleInfo(new GetModuleInfoParams().withMod(modB)).getVer();
		//////////////////////////// D v2 ///////////////////////////
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withSpec("module " + modD + " { typedef string dType; };"));
		CLIENT1.releaseModule(modD);
		long dVer2 = CLIENT1.getModuleInfo(new GetModuleInfoParams().withMod(modD)).getVer();
		//////////////////////////// C depends on D v2 //////////////////////////
		String modC = "TestModuleC";
		CLIENT1.requestModuleOwnership(modC);
		administerCommand(CLIENT2, "approveModRequest", "module", modC);
		CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withNewTypes(Arrays.asList("cType"))
			.withSpec("#include <" + modD + ">\n" +
					"module " + modC + " { " +
					"typedef " + modD + ".dType cType; " +
					"};"));
		CLIENT1.releaseModule(modC);
		long cVer = CLIENT1.getModuleInfo(new GetModuleInfoParams().withMod(modC)).getVer();
		/////////////////////////// A depends on B and C ////////////////////////////
		String modA = "TestModuleA";
		CLIENT1.requestModuleOwnership(modA);
		administerCommand(CLIENT2, "approveModRequest", "module", modA);
		try {
			CLIENT1.registerTypespec(new RegisterTypespecParams()
			.withDryrun(0L)
			.withNewTypes(Arrays.asList("aType"))
			.withSpec("" +
					"#include <" + modB + ">\n" +
					"#include <" + modC + ">\n" +
					"module " + modA + " { " +
					"typedef structure {" +
					" " + modB + ".bType valB; " +
					" " + modC + ".cType valC; " +
					"} aType; " +
					"};"));
			fail("Diamond dependency could not be allowed");
		} catch (ServerException ex) {
			String expectedError = "Incompatible module dependecies: TestModuleD(" + dVer1 + ")<-TestModuleB(" +
					bVer + ")<-RootModule and TestModuleD(" + dVer2 + ")<-TestModuleC(" + cVer + ")<-RootModule";
			assertThat(ex.getMessage(), is(expectedError));
		}
	}
}