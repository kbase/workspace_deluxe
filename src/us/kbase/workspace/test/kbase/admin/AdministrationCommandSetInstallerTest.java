package us.kbase.workspace.test.kbase.admin;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.assertLogEventsCorrect;
import static us.kbase.common.test.TestCommon.inst;
import static us.kbase.common.test.TestCommon.list;
import static us.kbase.common.test.TestCommon.set;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple9;
import us.kbase.common.service.UObject;
import us.kbase.common.test.MapBuilder;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.TestCommon.LogEvent;
import us.kbase.typedobj.db.OwnerInfo;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GetObjectInfo3Params;
import us.kbase.workspace.GetObjectInfo3Results;
import us.kbase.workspace.GetObjects2Params;
import us.kbase.workspace.GetObjects2Results;
import us.kbase.workspace.GrantModuleOwnershipParams;
import us.kbase.workspace.ListObjectsParams;
import us.kbase.workspace.ListWorkspaceIDsParams;
import us.kbase.workspace.ListWorkspaceIDsResults;
import us.kbase.workspace.ListWorkspaceInfoParams;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ObjectSpecification;
import us.kbase.workspace.RemoveModuleOwnershipParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.WorkspacePermissions;
import us.kbase.workspace.database.DynamicConfig;
import us.kbase.workspace.database.DynamicConfig.DynamicConfigUpdate;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.kbase.LocalTypeServerMethods;
import us.kbase.workspace.kbase.TypeServerMethods;
import us.kbase.workspace.kbase.WorkspaceDelegator;
import us.kbase.workspace.kbase.WorkspaceServerMethods;
import us.kbase.workspace.kbase.WorkspaceDelegator.WorkspaceCommand;
import us.kbase.workspace.kbase.admin.AdminRole;
import us.kbase.workspace.kbase.admin.AdministrationCommandSetInstaller;
import us.kbase.workspace.kbase.admin.AdministratorHandler;
import us.kbase.workspace.kbase.admin.WorkspaceAdministration;
import us.kbase.workspace.kbase.admin.WorkspaceAdministration.UserValidator;

public class AdministrationCommandSetInstallerTest {
	
	// Tests the workspace admin class with the standard set of handlers installed via the
	// handler set builder, concentrating on the installed commands. Non-command specific
	// tests are tested in the WorkspaceAdministration unit tests
	
	// these tests are waaaay more complicated than then need to be because the SDK
	// doesn't create equals & hashCode methods for its generated classes.
	// that and tuples
	
	private static List<ILoggingEvent> logEvents;
	
	@BeforeClass
	public static void beforeClass() {
		logEvents = TestCommon.setUpSLF4JTestLoggerAppender("us.kbase.workspace");
	}
	
	@Before
	public void before() {
		logEvents.clear();
	}
	
	private static class TestMocks {
		private final Workspace ws;
		private final WorkspaceServerMethods wsmeth;
		private final Types types;
		private final TypeServerMethods tsm;
		private final WorkspaceDelegator del;
		private final AdministratorHandler ah;
		private final WorkspaceAdministration admin;
		private final WorkspaceAdministration admindel;
		
		private TestMocks(
				final Workspace ws,
				final WorkspaceServerMethods wsmeth,
				final Types types,
				final TypeServerMethods tsm,
				final WorkspaceDelegator del,
				final AdministratorHandler ah,
				final WorkspaceAdministration admin,
				final WorkspaceAdministration admindel) {
			this.ws = ws;
			this.wsmeth = wsmeth;
			this.types = types;
			this.tsm = tsm;
			this.del = del;
			this.ah = ah;
			this.admin = admin;
			this.admindel = admindel;
		}
	}
	
	private TestMocks initTestMocks() {
		final Workspace ws = mock(Workspace.class);
		final WorkspaceServerMethods wsmeth = mock(WorkspaceServerMethods.class);
		when(wsmeth.getWorkspace()).thenReturn(ws);
		final Types types = mock(Types.class);
		final LocalTypeServerMethods tsm = mock(LocalTypeServerMethods.class);
		when(tsm.getTypes()).thenReturn(types);
		final WorkspaceDelegator del = mock(WorkspaceDelegator.class);
		final UserValidator userVal = (user, token) -> wsmeth.validateUser(user, token);
		final AdministratorHandler ah = mock(AdministratorHandler.class);
		final WorkspaceAdministration admin = AdministrationCommandSetInstaller.install(
				WorkspaceAdministration.getBuilder(ah, userVal), wsmeth, tsm)
				.withCacheMaxSize(0)
				.withCacheTimeMS(0)
				.build();
		final WorkspaceAdministration admindel = AdministrationCommandSetInstaller.install(
				WorkspaceAdministration.getBuilder(ah, userVal), wsmeth, del)
				.withCacheMaxSize(0)
				.withCacheTimeMS(0)
				.build();
		return new TestMocks(ws, wsmeth, types, tsm, del, ah, admin, admindel);
	}
	
	private void runCommandFail(
			final WorkspaceAdministration admin,
			final AuthToken t,
			final UObject command,
			final Exception expected) {
		try {
			admin.runCommand(t, command, null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void installFailNulls() throws Exception {
		final TestMocks m = initTestMocks();
		final LocalTypeServerMethods ltsm = (LocalTypeServerMethods) m.tsm;
		final UserValidator uval = mock(UserValidator.class);
		final WorkspaceAdministration.Builder b = WorkspaceAdministration.getBuilder(m.ah, uval);

		failInstall(null, m.wsmeth, ltsm, new NullPointerException("builder"));
		failInstall(null, m.wsmeth, m.del, new NullPointerException("builder"));
		failInstall(b, null, ltsm, new NullPointerException("wsmeth"));
		failInstall(b, null, m.del, new NullPointerException("wsmeth"));
		failInstall(b, m.wsmeth, (LocalTypeServerMethods) null, new NullPointerException("type"));
		failInstall(b, m.wsmeth, (WorkspaceDelegator) null, new NullPointerException("delegator"));
	}
	
	private void failInstall(
			final WorkspaceAdministration.Builder b,
			final WorkspaceServerMethods m,
			final WorkspaceDelegator d,
			final Exception expected) {
		try {
			AdministrationCommandSetInstaller.install(b, m, d);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	private void failInstall(
			final WorkspaceAdministration.Builder b,
			final WorkspaceServerMethods m,
			final LocalTypeServerMethods l,
			final Exception expected) {
		try {
			AdministrationCommandSetInstaller.install(b, m, l);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	/* ***********************************************
	 * Command error tests for error types that
	 * affect all commands without type delegation
	 * ***********************************************
	 */
	
	@Test
	public void failNotFullAdmin() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		when(mocks.ah.getAdminRole(new AuthToken("t", "user1"))).thenReturn(AdminRole.READ_ONLY);
		
		final List<String> commands = Arrays.asList("approveModRequest", "denyModRequest",
				"setWorkspaceOwner", "createWorkspace",
				"setPermissions", "setWorkspaceDescription", "setGlobalPermission",
				"saveObjects", "deleteWorkspace", "undeleteWorkspace", "grantModuleOwnership",
				"removeModuleOwnership", "setConfig");
		
		for (final String command: commands) {
			runCommandFail(
					mocks.admin,
					new AuthToken("t", "user1"),
					new UObject(ImmutableMap.of("command", command)),
					new IllegalArgumentException(
							"Full administration rights required for this command"));
		}
	}
	
	@Test
	public void failOnValidateUser() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		when(mocks.ah.getAdminRole(new AuthToken("t", "user1"))).thenReturn(AdminRole.ADMIN);
		when(mocks.wsmeth.validateUser(null, new AuthToken("t", "user1")))
				.thenThrow(new NullPointerException("User may not be null"));
		
		final List<String> commands = Arrays.asList("createWorkspace",
				"setGlobalPermission", "saveObjects", "listWorkspaces", "listWorkspaceIDs");
		
		for (final String command: commands) {
			runCommandFail(
					mocks.admin,
					new AuthToken("t", "user1"),
					new UObject(ImmutableMap.of("command", command)),
					new NullPointerException("User may not be null"));
		}
	}
	
	@Test
	public void failNoParams() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final Map<String, String> commandToClass = new HashMap<>();
		commandToClass.put("setWorkspaceOwner", "SetWorkspaceOwnerParams");
		commandToClass.put("createWorkspace", "CreateWorkspaceParams");
		commandToClass.put("setPermissions", "SetPermissionsParams");
		commandToClass.put("setWorkspaceDescription", "SetWorkspaceDescriptionParams");
		commandToClass.put("getWorkspaceDescription", "WorkspaceIdentity");
		commandToClass.put("getPermissions", "WorkspaceIdentity");
		commandToClass.put("getPermissionsMass", "GetPermissionsMassParams");
		commandToClass.put("getWorkspaceInfo", "WorkspaceIdentity");
		commandToClass.put("setGlobalPermission", "SetGlobalPermissionsParams");
		commandToClass.put("saveObjects", "SaveObjectsParams");
		commandToClass.put("getObjectInfo", "GetObjectInfo3Params");
		commandToClass.put("getObjectHistory", "ObjectIdentity");
		commandToClass.put("getObjects", "GetObjects2Params");
		commandToClass.put("listWorkspaces", "ListWorkspaceInfoParams");
		commandToClass.put("listWorkspaceIDs", "ListWorkspaceIDsParams");
		commandToClass.put("listObjects", "ListObjectsParams");
		commandToClass.put("deleteWorkspace", "WorkspaceIdentity");
		commandToClass.put("undeleteWorkspace", "WorkspaceIdentity");
		commandToClass.put("grantModuleOwnership", "GrantModuleOwnershipParams");
		commandToClass.put("removeModuleOwnership", "RemoveModuleOwnershipParams");
		commandToClass.put("setConfig", "SetConfigParams");
		
		for (final String commandStr: commandToClass.keySet()) {
			final UObject command = new UObject(ImmutableMap.of("command", commandStr,
					"user", "u1"));
			
			when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
			when(mocks.wsmeth.validateUser("u1", new AuthToken("tok", "fake")))
					.thenReturn(new WorkspaceUser("u1"));
			
			runCommandFail(mocks.admin, new AuthToken("tok", "fake"), command,
					new NullPointerException(
							"Method parameters " + commandToClass.get(commandStr) +
							" may not be null"));
		}
	}
	
	private static class MapErr {
		private final String clazz;
		private final String key;
		private final Object value;
		MapErr(String clazz, String key, Object value) {
			this.clazz = clazz;
			this.key = key;
			this.value = value;
		}
	}
	
	@Test
	public void failParamsMappingException() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final Map<String, MapErr> commandToClass = new HashMap<>();
		commandToClass.put("setWorkspaceOwner", new MapErr("SetWorkspaceOwnerParams", "wsi", 1));
		commandToClass.put("createWorkspace", new MapErr("CreateWorkspaceParams", "meta", "foo"));
		commandToClass.put("setPermissions", new MapErr("SetPermissionsParams", "id", "foo"));
		commandToClass.put("setWorkspaceDescription",
				new MapErr("SetWorkspaceDescriptionParams", "id", "foo"));
		commandToClass.put("getWorkspaceDescription",
				new MapErr("WorkspaceIdentity", "id", "foo"));
		commandToClass.put("getPermissions", new MapErr("WorkspaceIdentity", "id", "foo"));
		commandToClass.put("getPermissionsMass",
				new MapErr("GetPermissionsMassParams", "workspaces", "foo"));
		commandToClass.put("getWorkspaceInfo", new MapErr("WorkspaceIdentity", "id", "foo"));
		commandToClass.put("setGlobalPermission",
				new MapErr("SetGlobalPermissionsParams", "id", "foo"));
		commandToClass.put("saveObjects", new MapErr("SaveObjectsParams", "id", "foo"));
		commandToClass.put("getObjectInfo",
				new MapErr("GetObjectInfo3Params", "objects", "foo"));
		commandToClass.put("getObjectHistory", new MapErr("ObjectIdentity", "wsid", "foo"));
		commandToClass.put("getObjects", new MapErr("GetObjects2Params", "objects", "foo"));
		commandToClass.put("listWorkspaces",
				new MapErr("ListWorkspaceInfoParams", "owners", "foo"));
		commandToClass.put("listWorkspaceIDs",
				new MapErr("ListWorkspaceIDsParams", "onlyGlobal", "foo"));
		commandToClass.put("listObjects", new MapErr("ListObjectsParams", "ids", "foo"));
		commandToClass.put("deleteWorkspace", new MapErr("WorkspaceIdentity", "id", "foo"));
		commandToClass.put("undeleteWorkspace", new MapErr("WorkspaceIdentity", "id", "foo"));
		commandToClass.put("grantModuleOwnership",
				new MapErr("GrantModuleOwnershipParams", "with_grant_option", "foo"));
		commandToClass.put("removeModuleOwnership",
				new MapErr("RemoveModuleOwnershipParams", "mod", set("foo", "bar")));
		commandToClass.put("setConfig",
				new MapErr("SetConfigParams", "set", set("foo", "bar")));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "usah")))
				.thenReturn(AdminRole.ADMIN);
		when(mocks.wsmeth.validateUser("fake", new AuthToken("tok", "usah")))
				.thenReturn(new WorkspaceUser("fake"));
		
		for (final String commandStr: commandToClass.keySet()) {
			final MapErr me = commandToClass.get(commandStr);
			final UObject command = new UObject(ImmutableMap.of("command", commandStr,
					"user", "fake",
					"params", ImmutableMap.of(me.key, me.value)));
			
			try {
				mocks.admin.runCommand(new AuthToken("tok", "usah"), command, null);
				fail("expected exception for command " + commandStr);
			} catch (IllegalArgumentException got) {
				final String err = "incorrect message for exception:\n" +
						ExceptionUtils.getStackTrace(got);
				assertThat(err, got.getMessage(), containsString(
						"Unable to deserialize " + me.clazz + " out of params field: Cannot"));
			}
		}
	}
	
	/* ***********************************************
	 * Command specific tests without type delegation
	 * ***********************************************
	 */
	
	@Test
	public void getConfig() throws Exception {
		final TestMocks mocks = initTestMocks();
		final UObject command = new UObject(ImmutableMap.of("command", "getConfig"));
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.ws.getConfig()).thenReturn(DynamicConfig.getBuilder()
				.withBackendScaling(4).build());
		final Object ret = mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		assertThat("incorrect return", ret, is(ImmutableMap.of("config", ImmutableMap.of(
				"backend-file-retrieval-scaling", 4))));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"getConfig", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void setConfig() throws Exception {
		final TestMocks mocks = initTestMocks();
		final UObject command = new UObject(ImmutableMap.of(
				"command", "setConfig",
				"params", ImmutableMap.of("set", ImmutableMap.of(
						"backend-file-retrieval-scaling", 89))));
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"setConfig", AdministrationCommandSetInstaller.class));
		
		verify(mocks.ws).setConfig(DynamicConfigUpdate.getBuilder()
				.withBackendScaling(89).build());
	}
	
	@Test
	public void setConfigFail() throws Exception {
		// test one failure scenario, failures are covered well in the DynamicConfigUpdate
		// unit tests
		final TestMocks mocks = initTestMocks();
		final UObject command = new UObject(ImmutableMap.of(
				"command", "setConfig",
				"params", ImmutableMap.of("set", ImmutableMap.of(
						"backend-file-retrieval-scaling", "foo"))));
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		
		runCommandFail(mocks.admin, new AuthToken("tok", "fake"), command,
				new IllegalArgumentException(
						"backend-file-retrieval-scaling must be an integer > 0"));

		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"setConfig", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void listModRequests() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "listModRequests"));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		final OwnerInfo oi = new OwnerInfo();
		oi.setModuleName("mod");
		when(mocks.types.listModuleRegistrationRequests()).thenReturn(Arrays.asList(oi));
		
		@SuppressWarnings("unchecked")
		final List<OwnerInfo> o = (List<OwnerInfo>) mocks.admin.runCommand(
				new AuthToken("tok", "fake"), command, null);
		assertThat("incorrect return", o.get(0).getModuleName(), is("mod"));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"listModRequests", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void approveModRequest() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "approveModRequest",
				"module", "somemod"));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		
		mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"approveModRequest somemod", AdministrationCommandSetInstaller.class));
		
		verify(mocks.types).resolveModuleRegistration("somemod", true);
	}
	
	@Test
	public void denyModRequest() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "denyModRequest",
				"module", "somemod"));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		
		mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"denyModRequest somemod", AdministrationCommandSetInstaller.class));
		
		verify(mocks.types).resolveModuleRegistration("somemod", false);
	}
	
	@Test
	public void setWorkspaceOwnerWithNullArgs() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "setWorkspaceOwner",
				"params", ImmutableMap.of("wsi", ImmutableMap.of("id", 3))));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		when(mocks.ws.setWorkspaceOwner(
				// really ws will throw an exception if a null user is passed
				null, new WorkspaceIdentifier(3), null, Optional.empty(), true))
				.thenReturn(WorkspaceInformation.getBuilder()
						.withID(3)
						.withName("na")
						.withOwner(new WorkspaceUser("owner"))
						.withMaximumObjectID(6)
						.withModificationDate(inst(60000))
						.withUserPermission(Permission.NONE)
						.build());
		
		@SuppressWarnings("unchecked")
		final Tuple9<Long, String, String, String, Long, String, String, String,
				Map<String, String>> gross = (Tuple9<Long, String, String, String, Long, String,
						String, String, Map<String, String>>) mocks.admin.runCommand(
								new AuthToken("tok", "fake"), command, null);
		
		assertThat("ids correct", gross.getE1(), is(3L));
		assertThat("ws name correct", gross.getE2(), is("na"));
		assertThat("user name correct", gross.getE3(), is("owner"));
		assertThat("moddates correct", gross.getE4(), is("1970-01-01T00:01:00+0000"));
		assertThat("obj counts correct", gross.getE5(), is(6L));
		assertThat("permission correct", gross.getE6(), is("n"));
		assertThat("global read correct", gross.getE7(), is("n"));
		assertThat("lockstate correct", gross.getE8(), is("unlocked"));
		assertThat("meta correct", gross.getE9(), is(Collections.emptyMap()));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"setWorkspaceOwner 3 owner", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void setWorkspaceOwnerWithFullArgs() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "setWorkspaceOwner",
				"params", ImmutableMap.of(
						"wsi", ImmutableMap.of("workspace", "myws"),
						"new_user", "usern",
						"new_name", "usern:foo")));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		when(mocks.wsmeth.validateUser("usern", new AuthToken("tok", "fake")))
				.thenReturn(new WorkspaceUser("usern"));
		when(mocks.ws.setWorkspaceOwner(
				// really ws will throw an exception if a null user is passed
				null, new WorkspaceIdentifier("myws"), new WorkspaceUser("usern"),
						Optional.of("usern:foo"), true))
				.thenReturn(WorkspaceInformation.getBuilder()
						.withID(10)
						.withName("usern:foo")
						.withOwner(new WorkspaceUser("usern"))
						.withMaximumObjectID(22)
						.withModificationDate(inst(120000))
						.withUserPermission(Permission.NONE)
						.build());
		
		@SuppressWarnings("unchecked")
		final Tuple9<Long, String, String, String, Long, String, String, String,
				Map<String, String>> gross = (Tuple9<Long, String, String, String, Long, String,
						String, String, Map<String, String>>) mocks.admin.runCommand(
								new AuthToken("tok", "fake"), command, null);
		
		assertThat("ids correct", gross.getE1(), is(10L));
		assertThat("ws name correct", gross.getE2(), is("usern:foo"));
		assertThat("user name correct", gross.getE3(), is("usern"));
		assertThat("moddates correct", gross.getE4(), is("1970-01-01T00:02:00+0000"));
		assertThat("obj counts correct", gross.getE5(), is(22L));
		assertThat("permission correct", gross.getE6(), is("n"));
		assertThat("global read correct", gross.getE7(), is("n"));
		assertThat("lockstate correct", gross.getE8(), is("unlocked"));
		assertThat("meta correct", gross.getE9(), is(Collections.emptyMap()));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"setWorkspaceOwner 10 usern", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void createWorkspace() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "createWorkspace",
				"user", "user1",
				"params", ImmutableMap.of(
						"workspace", "ws1",
						"globalread", "r",
						"description", "d",
						"meta", ImmutableMap.of("foo", "bar"))));
		
		final Tuple9<Long, String, String, String, Long, String, String, String,
			Map<String, String>> grossret = new Tuple9<Long, String, String, String, Long, String,
					String, String, Map<String, String>>()
						.withE1(7L)
						.withE3("user1");
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		when(mocks.wsmeth.validateUser("user1", new AuthToken("tok", "fake")))
				.thenReturn(new WorkspaceUser("user1"));
		when(mocks.wsmeth.createWorkspace(
				argThat(new ArgumentMatcher<CreateWorkspaceParams>() {

						@Override
						public boolean matches(final CreateWorkspaceParams cwp) {
							return "ws1".equals(cwp.getWorkspace()) &&
									"r".equals(cwp.getGlobalread()) &&
									"d".equals(cwp.getDescription()) &&
									ImmutableMap.of("foo", "bar").equals(cwp.getMeta());
						}
					}),
				eq(new WorkspaceUser("user1")))).thenReturn(grossret);
		
		@SuppressWarnings("unchecked")
		final Tuple9<Long, String, String, String, Long, String, String, String,
				Map<String, String>> gross = (Tuple9<Long, String, String, String, Long, String,
						String, String, Map<String, String>>) mocks.admin.runCommand(
								new AuthToken("tok", "fake"), command, null);
		
		assertThat("incorrect return", gross, is(grossret)); // rely on identity
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"createWorkspace 7 user1", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void setPermissions() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "setPermissions",
				"params", ImmutableMap.of(
						"workspace", "ws1",
						"new_permission", "a",
						"users", Arrays.asList("u1", "u2"))));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		when(mocks.wsmeth.setPermissionsAsAdmin(
				argThat(new ArgumentMatcher<SetPermissionsParams>() {

						@Override
						public boolean matches(final SetPermissionsParams spp) {
							return "ws1".equals(spp.getWorkspace()) &&
									spp.getId() == null &&
									"a".equals(spp.getNewPermission()) &&
									Arrays.asList("u1", "u2").equals(spp.getUsers());
						}
					}),
				eq(new AuthToken("tok", "fake")))).thenReturn(24L);
		
		mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"setPermissions 24 a u1 u2", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void setWorkspaceDescription() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "setWorkspaceDescription",
				"params", ImmutableMap.of(
						"workspace", "ws1",
						"description", "desc")));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		when(mocks.ws.setWorkspaceDescription(null, new WorkspaceIdentifier("ws1"), "desc", true))
				.thenReturn(8L);
		
		mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"setWorkspaceDescription 8", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void getWorkspaceDescription() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "getWorkspaceDescription",
				"params", ImmutableMap.of(
						"workspace", "ws1")));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.ws.getWorkspaceDescription(null, new WorkspaceIdentifier("ws1"), true))
				.thenReturn("desc1");
		
		final String desc = (String) mocks.admin.runCommand(
				new AuthToken("tok", "fake"), command, null);
		
		assertThat("incorrect description", desc, is("desc1"));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"getWorkspaceDescription null ws1", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void getPermissionsNullUser() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "getPermissions",
				"params", ImmutableMap.of("id", 3)));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.wsmeth.getPermissions(argThat(new ArgumentMatcher<List<WorkspaceIdentity>>() {

						@Override
						public boolean matches(final List<WorkspaceIdentity> wsi) {
							if (wsi.size() != 1) {
								return false;
							}
							final WorkspaceIdentity ws = wsi.get(0);
							return ws.getId() == 3 && ws.getWorkspace() == null;
						}
				}),
				isNull(),
				eq(true)))
				.thenReturn(new WorkspacePermissions()
						.withPerms(Arrays.asList(ImmutableMap.of("user", "a", "user2", "r"))));
		
		@SuppressWarnings("unchecked")
		final Map<String, String> perms =  (Map<String, String>) mocks.admin.runCommand(
				new AuthToken("tok", "fake"), command, null);
		
		assertThat("incorrect perms", perms, is(ImmutableMap.of("user", "a", "user2", "r")));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"getPermissions 3 null", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void getPermissionsWithUser() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "getPermissions",
				"user", "auser",
				"params", ImmutableMap.of("workspace", "foo")));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.wsmeth.validateUser("auser", new AuthToken("tok", "fake")))
				.thenReturn(new WorkspaceUser("auser"));
		when(mocks.wsmeth.getPermissions(argThat(new ArgumentMatcher<WorkspaceIdentity>() {

						@Override
						public boolean matches(final WorkspaceIdentity wsi) {
							return wsi.getId() == null && "foo".equals(wsi.getWorkspace());
						}
				}),
				eq(new WorkspaceUser("auser"))))
				.thenReturn(ImmutableMap.of("user3", "w", "user10", "r"));
		
		@SuppressWarnings("unchecked")
		final Map<String, String> perms =  (Map<String, String>) mocks.admin.runCommand(
				new AuthToken("tok", "fake"), command, null);
		
		assertThat("incorrect perms", perms, is(ImmutableMap.of("user3", "w", "user10", "r")));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"getPermissions null foo auser", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void getPermissionsMass() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "getPermissionsMass",
				"params", ImmutableMap.of("workspaces", Arrays.asList(
						ImmutableMap.of("workspace", "ws"),
						ImmutableMap.of("id", 2)))));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.wsmeth.getPermissions(argThat(new ArgumentMatcher<List<WorkspaceIdentity>>() {
			
						@Override
						public boolean matches(final List<WorkspaceIdentity> wsi) {
							if (wsi.size() != 2) {
								return false;
							}
							final WorkspaceIdentity ws1 = wsi.get(0);
							final WorkspaceIdentity ws2 = wsi.get(1);
							return ws1.getId() == null && "ws".equals(ws1.getWorkspace()) &&
									ws2.getId() == 2 && ws2.getWorkspace() == null;
						}
				}),
				isNull(),
				eq(true)))
				.thenReturn(new WorkspacePermissions()
						.withPerms(Arrays.asList(
								ImmutableMap.of("user", "a", "user2", "r"),
								ImmutableMap.of("user3", "w", "user10", "r"))));
		
		final WorkspacePermissions perms = (WorkspacePermissions) mocks.admin.runCommand(
				new AuthToken("tok", "fake"), command, null);
		
		assertThat("incorrect perms", perms.getPerms(), is(Arrays.asList(
				ImmutableMap.of("user", "a", "user2", "r"),
				ImmutableMap.of("user3", "w", "user10", "r"))));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"getPermissionsMass 2 workspaces in input",
				AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void getWorkspaceInfo() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "getWorkspaceInfo",
				"params", ImmutableMap.of("workspace", "ws1")));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.ws.getWorkspaceInformationAsAdmin(new WorkspaceIdentifier("ws1")))
				.thenReturn(WorkspaceInformation.getBuilder()
						.withID(10)
						.withName("ws1")
						.withOwner(new WorkspaceUser("usern"))
						.withMaximumObjectID(22)
						.withModificationDate(inst(120000))
						.withUserPermission(Permission.NONE)
						.build());

		@SuppressWarnings("unchecked")
		final Tuple9<Long, String, String, String, Long, String, String, String,
				Map<String, String>> gross = (Tuple9<Long, String, String, String, Long, String,
						String, String, Map<String, String>>) mocks.admin.runCommand(
								new AuthToken("tok", "fake"), command, null);
		
		assertThat("ids correct", gross.getE1(), is(10L));
		assertThat("ws name correct", gross.getE2(), is("ws1"));
		assertThat("user name correct", gross.getE3(), is("usern"));
		assertThat("moddates correct", gross.getE4(), is("1970-01-01T00:02:00+0000"));
		assertThat("obj counts correct", gross.getE5(), is(22L));
		assertThat("permission correct", gross.getE6(), is("n"));
		assertThat("global read correct", gross.getE7(), is("n"));
		assertThat("lockstate correct", gross.getE8(), is("unlocked"));
		assertThat("meta correct", gross.getE9(), is(Collections.emptyMap()));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"getWorkspaceInfo 10", AdministrationCommandSetInstaller.class));
	}

	@Test
	public void setGlobalPermissions() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "setGlobalPermission",
				"user", "auser",
				"params", ImmutableMap.of("workspace", "foo", "new_permission", "r")));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		when(mocks.wsmeth.validateUser("auser", new AuthToken("tok", "fake")))
				.thenReturn(new WorkspaceUser("auser"));
		when(mocks.wsmeth.setGlobalPermission(
				argThat(new ArgumentMatcher<SetGlobalPermissionsParams>() {

						@Override
						public boolean matches(final SetGlobalPermissionsParams sgpp) {
							return sgpp.getId() == null &&
									"foo".equals(sgpp.getWorkspace()) &&
									"r".equals(sgpp.getNewPermission());
						}
				}),
				eq(new WorkspaceUser("auser"))))
				.thenReturn(65L);
		
		mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"setGlobalPermission 65 r auser", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void saveObjects() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		// for some reason I don't understand, initializing the UObject with just the
		// plain objects causes the test to fail due to the JsonTokenStream not being closed.
		// Hence turning it into a char array first.
		final UObject command = new UObject(new ObjectMapper().writeValueAsString(
				ImmutableMap.of("command", "saveObjects",
				"user", "auser",
				"params", ImmutableMap.of("workspace", "foo",
						"objects", Arrays.asList(ImmutableMap.of(
								"type", "Foo.Bar-2.1",
								"data", ImmutableMap.of("foo", "bar"),
								"name", "foobar"
								))))).toCharArray());
		
		final Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>> supergross = new Tuple11<Long, String, String, String, Long,
				String, Long, String, String, Long, Map<String, String>>()
						.withE1(24L)
						.withE2("foobar")
						.withE3("Foo.Bar-2.1")
						.withE4("1970-01-01T00:02:00+0000")
						.withE5(1L)
						.withE6("auser")
						.withE7(7L)
						.withE8("foo")
						.withE9("checksum")
						.withE10(78L)
						.withE11(Collections.emptyMap());
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		when(mocks.wsmeth.validateUser("auser", new AuthToken("tok", "fake")))
				.thenReturn(new WorkspaceUser("auser"));
		when(mocks.wsmeth.saveObjects(argThat(new ArgumentMatcher<SaveObjectsParams>() {

					@Override
					public boolean matches(final SaveObjectsParams sop) {
						if (sop.getObjects().size() != 1) {
							return false;
						}
						final ObjectSaveData osd = sop.getObjects().get(0);
						final Map<String, Object> data = osd.getData().asClassInstance(
								new TypeReference<Map<String, Object>>() {});
						return sop.getId() == null &&
								"foo".equals(sop.getWorkspace()) &&
								ImmutableMap.of("foo", "bar").equals(data) &&
								osd.getHidden() == null &&
								osd.getMeta() == null &&
								"foobar".equals(osd.getName()) &&
								osd.getObjid() == null &&
								osd.getProvenance() == null &&
								"Foo.Bar-2.1".equals(osd.getType());
					}
				}),
				eq(new WorkspaceUser("auser")),
				eq(new AuthToken("tok", "fake"))))
				.thenReturn(Arrays.asList(supergross));
		
		@SuppressWarnings({ "unchecked" })
		final List<Tuple11<Long, String, String, String, Long, String, Long, String, String,
				Long, Map<String, String>>> l =
				(List<Tuple11<Long, String, String, String, Long, String, Long, String, String,
						Long, Map<String, String>>>) mocks.admin.runCommand(
								new AuthToken("tok", "fake"), command, null);
		
		assertThat("incorrect size", l.size(), is(1));
		assertThat("incorrect tuple", l.get(0), is(supergross)); // rely on identity
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"saveObjects auser", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void getObjectInfo() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "getObjectInfo",
				"params", ImmutableMap.of(
						"includeMetadata", 0,
						"ignoreErrors", 1,
						"objects", Arrays.asList(
								ImmutableMap.of(
										"workspace", "ws",
										"name", "n",
										"to_obj_ref_path", Arrays.asList("1/2/3", "4/5/6")
								),
								ImmutableMap.of(
										"wsid", 25,
										"objid", 3,
										"ver", 22,
										"strict_maps", 1
								)
								))));
		
		final Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>> sg1 = new Tuple11<Long, String, String, String, Long,
				String, Long, String, String, Long, Map<String, String>>()
						.withE1(24L)
						.withE2("n")
						.withE3("Far.Boo-2.1")
						.withE4("1970-01-01T00:02:00+0000")
						.withE5(1L)
						.withE6("auser")
						.withE7(7L)
						.withE8("ws")
						.withE9("checksum")
						.withE10(78L)
						.withE11(Collections.emptyMap());
		
		final Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>> sg2 = new Tuple11<Long, String, String, String, Long,
				String, Long, String, String, Long, Map<String, String>>()
						.withE1(3L)
						.withE2("somename")
						.withE3("Mod.Type-0.2")
						.withE4("1970-01-01T00:01:00+0000")
						.withE5(22L)
						.withE6("buser")
						.withE7(25L)
						.withE8("somews")
						.withE9("checksum2")
						.withE10(79L)
						.withE11(Collections.emptyMap());
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.wsmeth.getObjectInformation(
				argThat(new ArgumentMatcher<GetObjectInfo3Params>() {

						@Override
						public boolean matches(final GetObjectInfo3Params goi) {
							if (goi.getObjects().size() != 2) {
								return false;
							}
							final ObjectSpecification os1 = goi.getObjects().get(0);
							final ObjectSpecification os2 = goi.getObjects().get(1);
							return goi.getIgnoreErrors() == 1 && goi.getIncludeMetadata() == 0 &&
									
									os1.getFindReferencePath() == null &&
									os1.getIncluded() == null &&
									"n".equals(os1.getName()) &&
									os1.getObjid() == null &&
									os1.getObjPath() == null &&
									os1.getObjRefPath() == null &&
									os1.getRef() == null &&
									os1.getStrictArrays() == null &&
									os1.getStrictMaps() == null &&
									os1.getToObjPath() == null &&
									Arrays.asList("1/2/3", "4/5/6").equals(os1.getToObjRefPath())
									&& os1.getVer() == null &&
									"ws".equals(os1.getWorkspace()) &&
									os1.getWsid() == null &&
									
									os2.getFindReferencePath() == null &&
									os2.getIncluded() == null &&
									os2.getName() == null &&
									os2.getObjid() == 3 &&
									os2.getObjPath() == null &&
									os2.getObjRefPath() == null &&
									os2.getRef() == null &&
									os2.getStrictArrays() == null &&
									os2.getStrictMaps() == 1 &&
									os2.getToObjPath() == null &&
									os2.getToObjRefPath() == null &&
									os2.getVer() == 22 &&
									os2.getWorkspace() == null &&
									os2.getWsid() == 25;
						}
				}),
				// is it really necessary to pass a workspace user for an admin request?
				eq(new WorkspaceUser("fake")), eq(true)))
				.thenReturn(new GetObjectInfo3Results()
						.withInfos(Arrays.asList(sg1, sg2))
						.withPaths(Arrays.asList(
								Arrays.asList("1/2/3", "4/5/6", "7/24/1"),
								Arrays.asList("25/3/22"))));
		
		final GetObjectInfo3Results res = (GetObjectInfo3Results) mocks.admin.runCommand(
				new AuthToken("tok", "fake"), command, null);
		
		// rely on identity
		assertThat("incorrect tuples", res.getInfos(), is(Arrays.asList(sg1, sg2)));
		assertThat("incorrect paths", res.getPaths(), is(Arrays.asList(
								Arrays.asList("1/2/3", "4/5/6", "7/24/1"),
								Arrays.asList("25/3/22"))));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"getObjectInfo", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void getObjectHistory() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "getObjectHistory",
				"params", ImmutableMap.of(
						"wsid", 22,
						"name", "objname",
						"ver", 3)));
		
		final Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>> sg1 = new Tuple11<Long, String, String, String, Long,
				String, Long, String, String, Long, Map<String, String>>()
						.withE1(24L)
						.withE2("objname")
						.withE3("Far.Boo-2.1")
						.withE4("1970-01-01T00:02:00+0000")
						.withE5(1L)
						.withE6("auser")
						.withE7(22L)
						.withE8("somews")
						.withE9("checksum")
						.withE10(78L)
						.withE11(Collections.emptyMap());
		
		final Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>> sg2 = new Tuple11<Long, String, String, String, Long,
				String, Long, String, String, Long, Map<String, String>>()
						.withE1(24L)
						.withE2("objname")
						.withE3("Far.Boo-2.3")
						.withE4("1970-01-01T00:04:00+0000")
						.withE5(2L)
						.withE6("auser")
						.withE7(22L)
						.withE8("somews")
						.withE9("checksum2")
						.withE10(79L)
						.withE11(Collections.emptyMap());
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.wsmeth.getObjectHistory(argThat(new ArgumentMatcher<ObjectIdentity>() {
		
					@Override
					public boolean matches(final ObjectIdentity oi) {
						return "objname".equals(oi.getName()) &&
								oi.getObjid() == null &&
								oi.getRef() == null &&
								oi.getVer() == 3 &&
								oi.getWorkspace() == null &&
								oi.getWsid() == 22;
					}
				}),
				eq(new WorkspaceUser("fake")),
				eq(true)))
				.thenReturn(Arrays.asList(sg1, sg2));
		
		final Object res = mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		// rely on identity
		assertThat("incorrect return", res, is(Arrays.asList(sg1, sg2)));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"getObjectHistory", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void getObjects() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "getObjects",
				"params", ImmutableMap.of(
						"no_data", 0,
						"ignoreErrors", 1,
						"objects", Arrays.asList(
								ImmutableMap.of(
										"workspace", "ws",
										"name", "n",
										"to_obj_ref_path", Arrays.asList("1/2/3", "4/5/6")
								),
								ImmutableMap.of(
										"wsid", 25,
										"objid", 3,
										"ver", 22,
										"strict_maps", 1
								)
								))));
		
		final Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>> sg1 = new Tuple11<Long, String, String, String, Long,
				String, Long, String, String, Long, Map<String, String>>()
						.withE1(24L)
						.withE2("n")
						.withE3("Far.Boo-2.1")
						.withE4("1970-01-01T00:02:00+0000")
						.withE5(1L)
						.withE6("auser")
						.withE7(7L)
						.withE8("ws")
						.withE9("checksum")
						.withE10(78L)
						.withE11(Collections.emptyMap());
		
		final Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>> sg2 = new Tuple11<Long, String, String, String, Long,
				String, Long, String, String, Long, Map<String, String>>()
						.withE1(3L)
						.withE2("somename")
						.withE3("Mod.Type-0.2")
						.withE4("1970-01-01T00:01:00+0000")
						.withE5(22L)
						.withE6("buser")
						.withE7(25L)
						.withE8("somews")
						.withE9("checksum2")
						.withE10(79L)
						.withE11(Collections.emptyMap());
		final ThreadLocal<List<WorkspaceObjectData>> resourcesToDelete = new ThreadLocal<>();
		
		final ObjectData od1 = new ObjectData()
				.withCopySourceInaccessible(1L)
				.withCreated("1970-01-01T00:02:00+0000")
				.withCreator("auser")
				.withData(new UObject(ImmutableMap.of("foo", "bar")))
				.withEpoch(7200000L)
				.withInfo(sg1)
				.withOrigWsid(7L)
				.withPath(Arrays.asList("1/2/3", "4/5/6", "7/24/1"));
		final ObjectData od2 = new ObjectData()
				.withCopySourceInaccessible(1L)
				.withCreated("1970-01-01T00:01:00+0000")
				.withCreator("buser")
				.withData(new UObject(ImmutableMap.of("foo", "bar")))
				.withEpoch(3600000L)
				.withInfo(sg2)
				.withOrigWsid(25L)
				.withPath(Arrays.asList("25/3/22"));

		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.wsmeth.getObjects(argThat(new ArgumentMatcher<GetObjects2Params>() {
		
					@Override
					public boolean matches(final GetObjects2Params gop) {
						if (gop.getObjects().size() != 2) {
							return false;
						}
						final ObjectSpecification os1 = gop.getObjects().get(0);
						final ObjectSpecification os2 = gop.getObjects().get(1);
						return gop.getIgnoreErrors() == 1 && gop.getNoData() == 0 &&
								
								os1.getFindReferencePath() == null &&
								os1.getIncluded() == null &&
								"n".equals(os1.getName()) &&
								os1.getObjid() == null &&
								os1.getObjPath() == null &&
								os1.getObjRefPath() == null &&
								os1.getRef() == null &&
								os1.getStrictArrays() == null &&
								os1.getStrictMaps() == null &&
								os1.getToObjPath() == null &&
								Arrays.asList("1/2/3", "4/5/6").equals(os1.getToObjRefPath())
								&& os1.getVer() == null &&
								"ws".equals(os1.getWorkspace()) &&
								os1.getWsid() == null &&
								
								os2.getFindReferencePath() == null &&
								os2.getIncluded() == null &&
								os2.getName() == null &&
								os2.getObjid() == 3 &&
								os2.getObjPath() == null &&
								os2.getObjRefPath() == null &&
								os2.getRef() == null &&
								os2.getStrictArrays() == null &&
								os2.getStrictMaps() == 1 &&
								os2.getToObjPath() == null &&
								os2.getToObjRefPath() == null &&
								os2.getVer() == 22 &&
								os2.getWorkspace() == null &&
								os2.getWsid() == 25;
					}
				}),
				eq(new WorkspaceUser("fake")),
				eq(true),
				eq(resourcesToDelete)))
				.thenReturn(new GetObjects2Results().withData(Arrays.asList(od1, od2)));
		
		final GetObjects2Results res = (GetObjects2Results) mocks.admin.runCommand(
				new AuthToken("tok", "fake"), command, resourcesToDelete);
	
		// rely on identity
		assertThat("incorrect dat", res.getData(), is(Arrays.asList(od1, od2)));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"getObjects", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void listWorkspaces() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "listWorkspaces",
				"user", "user1",
				"params", MapBuilder.newHashMap()
						.with("perm", "w")
						.with("owners", Arrays.asList("u1", "u2"))
						.with("meta", ImmutableMap.of("foo", "bar"))
						.with("after", "1970-01-01T00:01:00+0000")
						.with("before_epoch", 7200000)
						.with("excludeGlobal", 1)
						.build()
						));
		
		final Tuple9<Long, String, String, String, Long, String, String, String,
			Map<String, String>> g1 = new Tuple9<Long, String, String, String, Long, String,
					String, String, Map<String, String>>()
						.withE1(7L)
						.withE2("ws1")
						.withE3("u1")
						.withE4("1970-01-01T00:02:00+0000")
						.withE5(18L)
						.withE6("w")
						.withE7("n")
						.withE8("unlocked")
						.withE9(Collections.emptyMap());

		
		final Tuple9<Long, String, String, String, Long, String, String, String,
			Map<String, String>> g2 = new Tuple9<Long, String, String, String, Long, String,
					String, String, Map<String, String>>()
						.withE1(8L)
						.withE2("ws2")
						.withE3("u2")
						.withE4("1970-01-01T00:01:00+0000")
						.withE5(9L)
						.withE6("a")
						.withE7("r")
						.withE8("locked")
						.withE9(Collections.emptyMap());

		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.wsmeth.validateUser("user1", new AuthToken("tok", "fake")))
				.thenReturn(new WorkspaceUser("user1"));
		when(mocks.wsmeth.listWorkspaceInfo(
				argThat(new ArgumentMatcher<ListWorkspaceInfoParams>() {
		
					@Override
					public boolean matches(final ListWorkspaceInfoParams lwip) {
						return "w".equals(lwip.getPerm()) &&
								Arrays.asList("u1", "u2").equals(lwip.getOwners()) &&
								ImmutableMap.of("foo", "bar").equals(lwip.getMeta()) &&
								"1970-01-01T00:01:00+0000".equals(lwip.getAfter()) &&
								lwip.getAfterEpoch() == null &&
								lwip.getBefore() == null &&
								lwip.getBeforeEpoch() == 7200000 &&
								lwip.getExcludeGlobal() == 1 &&
								lwip.getShowDeleted() == null &&
								lwip.getShowOnlyDeleted() == null;
					}
				}),
				eq(new WorkspaceUser("user1"))))
				.thenReturn(Arrays.asList(g1, g2));
		
		final Object res = mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		// rely on identity
		assertThat("incorrect return", res, is(Arrays.asList(g1, g2)));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"listWorkspaces user1", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void listWorkspaceIDs() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "listWorkspaceIDs",
				"user", "user1",
				"params", ImmutableMap.of("perm", "w", "excludeGlobal", 0)));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.wsmeth.validateUser("user1", new AuthToken("tok", "fake")))
				.thenReturn(new WorkspaceUser("user1"));
		when(mocks.wsmeth.listWorkspaceIDs(argThat(new ArgumentMatcher<ListWorkspaceIDsParams>() {
		
					@Override
					public boolean matches(final ListWorkspaceIDsParams lwip) {
						return "w".equals(lwip.getPerm()) &&
								lwip.getExcludeGlobal() == 0 &&
								lwip.getOnlyGlobal() == null;
					}
				}),
				eq(new WorkspaceUser("user1"))))
				.thenReturn(new ListWorkspaceIDsResults()
						.withPub(Arrays.asList(1L, 2L))
						.withWorkspaces(Arrays.asList(3L, 4L)));
		
		final ListWorkspaceIDsResults res = (ListWorkspaceIDsResults) mocks.admin.runCommand(
				new AuthToken("tok", "fake"), command, null);
		
		// rely on identity
		assertThat("incorrect pub", res.getPub(), is(Arrays.asList(1L, 2L)));
		assertThat("incorrect ws", res.getWorkspaces(), is(Arrays.asList(3L, 4L)));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"listWorkspaceIDs user1", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void listObjectsWithUser() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "listObjects",
				"user", "user1",
				"params", MapBuilder.newHashMap()
						.with("ids", Arrays.asList(6L))
						.with("workspaces", Arrays.asList("ws2"))
						.with("type", "Foo.Bar-2")
						.with("perm", "w")
						.with("savedby", Arrays.asList("u1", "u2"))
						.with("meta", ImmutableMap.of("foo", "bar"))
						.with("after", "1970-01-01T00:01:00+0000")
						.with("before_epoch", 7200000)
						.with("excludeGlobal", 1)
						.with("minObjectID", 5)
						.with("limit", 100)
						.with("showOnlyDeleted", 0)
						.build()
						));
		
		final Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>> sg1 = new Tuple11<Long, String, String, String, Long,
						String, Long, String, String, Long, Map<String, String>>()
						.withE1(24L)
						.withE2("n")
						.withE3("Foo.Bar-2.1")
						.withE4("1970-01-01T00:02:00+0000")
						.withE5(1L)
						.withE6("u1")
						.withE7(6L)
						.withE8("ws1")
						.withE9("checksum")
						.withE10(78L)
						.withE11(Collections.emptyMap());

		final Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>> sg2 = new Tuple11<Long, String, String, String, Long,
					String, Long, String, String, Long, Map<String, String>>()
							.withE1(3L)
							.withE2("somename")
							.withE3("Foo.Bar-2.3")
							.withE4("1970-01-01T00:01:00+0000")
							.withE5(22L)
							.withE6("buser")
							.withE7(8L)
							.withE8("ws2")
							.withE9("checksum2")
							.withE10(79L)
							.withE11(Collections.emptyMap());
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.wsmeth.validateUser("user1", new AuthToken("tok", "fake")))
				.thenReturn(new WorkspaceUser("user1"));
		when(mocks.wsmeth.listObjects(argThat(new ArgumentMatcher<ListObjectsParams>() {
		
					@Override
					public boolean matches(final ListObjectsParams lop) {
						return Arrays.asList(6L).equals(lop.getIds()) &&
								Arrays.asList("ws2").equals(lop.getWorkspaces()) &&
								"Foo.Bar-2".equals(lop.getType()) &&
								"w".equals(lop.getPerm()) &&
								Arrays.asList("u1", "u2").equals(lop.getSavedby()) &&
								ImmutableMap.of("foo", "bar").equals(lop.getMeta()) &&
								"1970-01-01T00:01:00+0000".equals(lop.getAfter()) &&
								lop.getAfterEpoch() == null &&
								lop.getBefore() == null &&
								lop.getBeforeEpoch() == 7200000 &&
								lop.getExcludeGlobal() == 1 &&
								lop.getIncludeMetadata() == null &&
								lop.getLimit() == 100 &&
								lop.getMaxObjectID() == null &&
								lop.getMinObjectID() == 5 &&
								lop.getShowAllVersions() == null &&
								lop.getShowDeleted() == null &&
								lop.getShowHidden() == null &&
								lop.getShowOnlyDeleted() == 0;
					}
				}),
				eq(new WorkspaceUser("user1")),
				eq(false)))
				.thenReturn(Arrays.asList(sg1, sg2));
		
		final Object res = mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		// rely on identity
		assertThat("incorrect res", res, is(Arrays.asList(sg1, sg2)));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"listObjects user: user1", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void listObjectsNullUser() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "listObjects",
				"params", MapBuilder.newHashMap()
						.with("ids", Arrays.asList(6L))
						.with("workspaces", Arrays.asList("ws2"))
						.with("type", "Foo.Bar-2")
						.with("perm", "w")
						.with("savedby", Arrays.asList("u1", "u2"))
						.with("meta", ImmutableMap.of("foo", "bar"))
						.with("after", "1970-01-01T00:01:00+0000")
						.with("before_epoch", 7200000)
						.with("excludeGlobal", 1)
						.with("minObjectID", 5)
						.with("limit", 100)
						.with("showOnlyDeleted", 0)
						.build()
						));
		
		final Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>> sg1 = new Tuple11<Long, String, String, String, Long,
						String, Long, String, String, Long, Map<String, String>>()
						.withE1(24L)
						.withE2("n")
						.withE3("Foo.Bar-2.1")
						.withE4("1970-01-01T00:02:00+0000")
						.withE5(1L)
						.withE6("u1")
						.withE7(6L)
						.withE8("ws1")
						.withE9("checksum")
						.withE10(78L)
						.withE11(Collections.emptyMap());

		final Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
				Map<String, String>> sg2 = new Tuple11<Long, String, String, String, Long,
					String, Long, String, String, Long, Map<String, String>>()
							.withE1(3L)
							.withE2("somename")
							.withE3("Foo.Bar-2.3")
							.withE4("1970-01-01T00:01:00+0000")
							.withE5(22L)
							.withE6("buser")
							.withE7(8L)
							.withE8("ws2")
							.withE9("checksum2")
							.withE10(79L)
							.withE11(Collections.emptyMap());
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.wsmeth.listObjects(argThat(new ArgumentMatcher<ListObjectsParams>() {
		
					@Override
					public boolean matches(final ListObjectsParams lop) {
						return Arrays.asList(6L).equals(lop.getIds()) &&
								Arrays.asList("ws2").equals(lop.getWorkspaces()) &&
								"Foo.Bar-2".equals(lop.getType()) &&
								"w".equals(lop.getPerm()) &&
								Arrays.asList("u1", "u2").equals(lop.getSavedby()) &&
								ImmutableMap.of("foo", "bar").equals(lop.getMeta()) &&
								"1970-01-01T00:01:00+0000".equals(lop.getAfter()) &&
								lop.getAfterEpoch() == null &&
								lop.getBefore() == null &&
								lop.getBeforeEpoch() == 7200000 &&
								lop.getExcludeGlobal() == 1 &&
								lop.getIncludeMetadata() == null &&
								lop.getLimit() == 100 &&
								lop.getMaxObjectID() == null &&
								lop.getMinObjectID() == 5 &&
								lop.getShowAllVersions() == null &&
								lop.getShowDeleted() == null &&
								lop.getShowHidden() == null &&
								lop.getShowOnlyDeleted() == 0;
					}
				}),
				isNull(),
				eq(true)))
				.thenReturn(Arrays.asList(sg1, sg2));
		
		final Object res = mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		// rely on identity
		assertThat("incorrect res", res, is(Arrays.asList(sg1, sg2)));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"listObjects adminuser", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void deleteWorkspace() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "deleteWorkspace",
				"params", ImmutableMap.of("id", 7)));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		when(mocks.ws.setWorkspaceDeleted(null, new WorkspaceIdentifier(7), true, true))
			.thenReturn(7L);
		
		mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"deleteWorkspace 7", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void undeleteWorkspace() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "undeleteWorkspace",
				"params", ImmutableMap.of("workspace", "ws1")));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		when(mocks.ws.setWorkspaceDeleted(null, new WorkspaceIdentifier("ws1"), false, true))
			.thenReturn(8L);
		
		mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"undeleteWorkspace 8", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void listWorkspaceOwners() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "listWorkspaceOwners"));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.ws.getAllWorkspaceOwners()).thenReturn(set(
				new WorkspaceUser("u1"), new WorkspaceUser("u2")));
		
		@SuppressWarnings("unchecked")
		final List<String> ret = (List<String>) mocks.admin.runCommand(
				new AuthToken("tok", "fake"), command, null);
		
		assertThat("incorrect users", ret, is(Arrays.asList("u1", "u2")));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"listWorkspaceOwners", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void grantModuleOwnership() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "grantModuleOwnership",
				"params", ImmutableMap.of(
						"mod", "ModName",
						"new_owner", "owner",
						"with_grant_option", 0)));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);

		mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		verify(mocks.tsm).grantModuleOwnership(
				argThat(new ArgumentMatcher<GrantModuleOwnershipParams>() {
		
					@Override
					public boolean matches(final GrantModuleOwnershipParams gmop) {
						return "ModName".equals(gmop.getMod()) &&
								"owner".equals(gmop.getNewOwner()) &&
								gmop.getWithGrantOption() == 0;
					}
				}),
				
				isNull(),
				eq(true));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"grantModuleOwnership ModName owner", AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void removeModuleOwnership() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "removeModuleOwnership",
				"params", ImmutableMap.of(
						"mod", "ModName",
						"old_owner", "owner")));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);

		mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		verify(mocks.tsm).removeModuleOwnership(
				argThat(new ArgumentMatcher<RemoveModuleOwnershipParams>() {
		
					@Override
					public boolean matches(final RemoveModuleOwnershipParams gmop) {
						return "ModName".equals(gmop.getMod()) &&
								"owner".equals(gmop.getOldOwner());
					}
				}),
				
				isNull(),
				eq(true));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"removeModuleOwnership ModName owner", AdministrationCommandSetInstaller.class));
	}
	
	/* ***********************************************
	 * Type delegation tests
	 * ***********************************************
	 */
	
	@Test
	public void typeDelegationFailNotFullAdmin() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		when(mocks.ah.getAdminRole(new AuthToken("t", "user1"))).thenReturn(AdminRole.READ_ONLY);
		
		final List<String> commands = Arrays.asList(
				"approveModRequest", "denyModRequest",
				"grantModuleOwnership", "removeModuleOwnership");
		
		for (final String command: commands) {
			runCommandFail(
					mocks.admindel,
					new AuthToken("t", "user1"),
					new UObject(ImmutableMap.of("command", command)),
					new IllegalArgumentException(
							"Full administration rights required for this command"));
		}
	}
	
	@Test
	public void typeDelegationFailNoParams() throws Exception {
		final TestMocks mocks = initTestMocks();
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		
		final Map<String, String> commandToClass = ImmutableMap.of(
				"grantModuleOwnership", "GrantModuleOwnershipParams",
				"removeModuleOwnership", "RemoveModuleOwnershipParams");
		
		for (final String commandStr: commandToClass.keySet()) {
			final UObject command = new UObject(ImmutableMap.of("command", commandStr,
					"user", "u1"));
			
			runCommandFail(mocks.admindel, new AuthToken("tok", "fake"), command,
					new NullPointerException(String.format("Method parameters %s may not be null",
							commandToClass.get(commandStr))));
		}
	}
	
	@Test
	public void typeDelegationFailParamsMappingException() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final Map<String, MapErr> commandToClass = ImmutableMap.of(
				"grantModuleOwnership",
				new MapErr("GrantModuleOwnershipParams", "with_grant_option", "foo"),
				"removeModuleOwnership",
				new MapErr("RemoveModuleOwnershipParams", "mod", set("foo", "bar")));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "usah"))).thenReturn(AdminRole.ADMIN);
		
		for (final String commandStr: commandToClass.keySet()) {
			final MapErr me = commandToClass.get(commandStr);
			final UObject command = new UObject(ImmutableMap.of("command", commandStr,
					"user", "fake",
					"params", ImmutableMap.of(me.key, me.value)));
			
			try {
				mocks.admindel.runCommand(new AuthToken("tok", "usah"), command, null);
				fail("expected exception for command " + commandStr);
			} catch (IllegalArgumentException got) {
				final String err = "incorrect message for exception:\n" +
						ExceptionUtils.getStackTrace(got);
				assertThat(err, got.getMessage(), containsString(
						"Unable to deserialize " + me.clazz + " out of params field: Cannot"));
			}
		}
	}

	private static class WorkspaceUObjectCommandMatcher implements
			ArgumentMatcher<WorkspaceCommand<UObject>>{
		
		private final UObject command;
		
		public WorkspaceUObjectCommandMatcher(final UObject command) {
			this.command = command;
		}
		
		@Override
		public boolean matches(final WorkspaceCommand<UObject> cmd) {
			// this is utterly insane
			final WorkspaceClient wc = mock(WorkspaceClient.class);
			final ArgumentMatcher<UObject> uom = new ArgumentMatcher<UObject>() {

				@Override
				public boolean matches(final UObject got) {
					@SuppressWarnings("unchecked")
					final Map<String, Object> expected = command.asClassInstance(Map.class);
					final Object gotobj = got.asClassInstance(Object.class);
					if (!expected.equals(gotobj)) {
						System.out.format("%s: expected %s, got %s\n",
								WorkspaceUObjectCommandMatcher.class.getSimpleName(),
								expected, gotobj);
					}
					return expected.equals(gotobj);
				}
			};
			// test that the command sent to the delegator is ok
			try {
				when(wc.administer(argThat(uom))).thenReturn(new UObject("foo$"));
			} catch (IOException | JsonClientException e) {
				throw new RuntimeException("should be impossible", e);
			}
			final Object res;
			try {
				res = cmd.execute(wc).asClassInstance(Object.class);
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
			if (!res.equals("foo$")) {
				System.out.println(WorkspaceUObjectCommandMatcher.class.getSimpleName()
						+ ": wanted foo$, got " + res);
			}
			return res.equals("foo$");
		}
	}
	
	private Map<String, Object> map(
			final String k1, final Object v1,
			final String k2, final Object v2,
			final String k3, final Object v3) {
		return _map(Arrays.asList(k1, v1, k2, v2, k3, v3));
	}
	
	private Map<String, Object> map(
			final String k1, final Object v1,
			final String k2, final Object v2,
			final String k3, final Object v3,
			final String k4, final Object v4) {
		return _map(Arrays.asList(k1, v1, k2, v2, k3, v3, k4, v4));
	}

	public Map<String, Object> _map(final List<Object> o) {
		final MapBuilder<String, Object> b = MapBuilder.<String, Object>newHashMap();
		final Iterator<Object> i = o.iterator();
		while (i.hasNext()) {
			b.with((String) i.next(), i.next());
		}
		return b.build();
	}
	
	@Test
	public void typeDelegationListModRequests() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "listModRequests"));
		final OwnerInfo oi = new OwnerInfo();
		oi.setModuleName("mod");

		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.del.getTargetWorkspace())
				.thenReturn(new URL("https://holycrapthistestisnuts.com"));
		when(mocks.del.delegate(
				eq(new AuthToken("tok", "fake")),
				argThat(new WorkspaceUObjectCommandMatcher(
						new UObject(map(
								"command", "listModRequests",
								"params", null,
								"module", null,
								"user", null)
						)))))
				.thenReturn(new UObject(Arrays.asList(oi)));
		
		final Object o =  mocks.admindel.runCommand(new AuthToken("tok", "fake"), command, null);
		assertThat("incorrect return", o, is(Arrays.asList(map(
				"moduleName", "mod", "ownerUserId", null, "withChangeOwnersPrivilege", false))));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"listModRequests delegated to https://holycrapthistestisnuts.com",
				AdministrationCommandSetInstaller.class));
	}
	
	@Test
	public void typeDelegationApproveModRequest() throws Exception {
		// testing with both null and UObject(null) to be safe
		for (final UObject ret: list(new UObject(null), null)) {
			logEvents.clear();
			final TestMocks mocks = initTestMocks();
			
			final UObject command = new UObject(ImmutableMap.of(
					"command", "approveModRequest", "module", "somemod"));
			
			when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
			when(mocks.del.getTargetWorkspace())
					.thenReturn(new URL("https://devpocsuredoessuckuptime.com"));
			final WorkspaceUObjectCommandMatcher cmdmtch = new WorkspaceUObjectCommandMatcher(
					new UObject(map(
							"command", "approveModRequest",
							"params", null,
							"module", "somemod",
							"user", null)
					));
			when(mocks.del.delegate(eq(new AuthToken("tok", "fake")), argThat(cmdmtch)))
					.thenReturn(ret);
			
			final Object o = mocks.admindel.runCommand(
					new AuthToken("tok", "fake"), command, null);
			assertThat("incorrect return", o, nullValue());
			
			assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
					"approveModRequest somemod delegated to https://devpocsuredoessuckuptime.com",
					AdministrationCommandSetInstaller.class));
		}
	}
	
	@Test
	public void typeDelegationDenyModRequest() throws Exception {
		// testing with both null and UObject(null) to be safe
		for (final UObject ret: list(new UObject(null), null)) {
			logEvents.clear();
			final TestMocks mocks = initTestMocks();
			
			final UObject command = new UObject(ImmutableMap.of(
					"command", "denyModRequest", "module", "somemod2"));
			
			when(mocks.ah.getAdminRole(new AuthToken("t", "u"))).thenReturn(AdminRole.ADMIN);
			when(mocks.del.getTargetWorkspace())
					.thenReturn(new URL("https://thesetestsarepainful.com"));
			final WorkspaceUObjectCommandMatcher cmdmtch = new WorkspaceUObjectCommandMatcher(
					new UObject(map(
							"command", "denyModRequest",
							"params", null,
							"module", "somemod2",
							"user", null)
					));
			when(mocks.del.delegate(eq(new AuthToken("t", "u")), argThat(cmdmtch)))
					.thenReturn(ret);
			
			final Object o = mocks.admindel.runCommand(new AuthToken("t", "u"), command, null);
			assertThat("incorrect return", o, nullValue());
			
			assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
					"denyModRequest somemod2 delegated to https://thesetestsarepainful.com",
					AdministrationCommandSetInstaller.class));
		}
	}
	
	@Test
	public void typeDelegationGrantModuleOwnership() throws Exception {
		// testing with both null and UObject(null) to be safe
		for (final UObject ret: list(new UObject(null), null)) {
			logEvents.clear();
			final TestMocks mocks = initTestMocks();
			
			final UObject command = new UObject(ImmutableMap.of(
					"command", "grantModuleOwnership",
					"params", map(
							"mod", "ModName",
							"new_owner", "owner",
							"with_grant_option", 0)));
			
			when(mocks.ah.getAdminRole(new AuthToken("t2", "u"))).thenReturn(AdminRole.ADMIN);
			when(mocks.del.getTargetWorkspace()).thenReturn(new URL("https://owowow.com"));
			final WorkspaceUObjectCommandMatcher cmdmtch = new WorkspaceUObjectCommandMatcher(
					new UObject(map(
							"command", "grantModuleOwnership",
							"params", map(
									"mod", "ModName",
									"new_owner", "owner",
									"with_grant_option", 0),
							"module", null,
							"user", null)
					));
			when(mocks.del.delegate(eq(new AuthToken("t2", "u")), argThat(cmdmtch)))
					.thenReturn(ret);
	
			final Object o = mocks.admindel.runCommand(new AuthToken("t2", "u"), command, null);
			assertThat("incorrect return", o, nullValue());
			
			assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
					"grantModuleOwnership ModName owner delegated to https://owowow.com",
					AdministrationCommandSetInstaller.class));
		}
	}
	
	@Test
	public void typeDelegationRemoveModuleOwnership() throws Exception {
		// testing with both null and UObject(null) to be safe
		for (final UObject ret: list(new UObject(null), null)) {
			logEvents.clear();
			final TestMocks mocks = initTestMocks();
			
			final UObject command = new UObject(ImmutableMap.of("command", "removeModuleOwnership",
					"params", ImmutableMap.of(
							"mod", "ModName",
							"old_owner", "owner")));
			
			when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
			when(mocks.del.getTargetWorkspace()).thenReturn(new URL("http://iamwebsite.com"));
			final WorkspaceUObjectCommandMatcher cmdmtch = new WorkspaceUObjectCommandMatcher(
					new UObject(map(
							"command", "removeModuleOwnership",
							"params", ImmutableMap.of(
									"mod", "ModName",
									"old_owner", "owner"),
							"module", null,
							"user", null)
					));
			when(mocks.del.delegate(eq(new AuthToken("tok", "fake")), argThat(cmdmtch)))
					.thenReturn(ret);
	
			final Object o = mocks.admindel.runCommand(
					new AuthToken("tok", "fake"), command, null);
			assertThat("incorrect return", o, nullValue());
			
			assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
					"removeModuleOwnership ModName owner delegated to http://iamwebsite.com",
					AdministrationCommandSetInstaller.class));
		}
	}
}
