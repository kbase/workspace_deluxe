package us.kbase.workspace.test.kbase.admin;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;
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
import static us.kbase.common.test.TestCommon.set;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple9;
import us.kbase.common.service.UObject;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.TestCommon.LogEvent;
import us.kbase.typedobj.db.OwnerInfo;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GetObjectInfo3Params;
import us.kbase.workspace.GetObjectInfo3Results;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ObjectSpecification;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.WorkspacePermissions;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.kbase.WorkspaceServerMethods;
import us.kbase.workspace.kbase.admin.AdminRole;
import us.kbase.workspace.kbase.admin.AdministratorHandler;
import us.kbase.workspace.kbase.admin.WorkspaceAdministration;

public class WorkspaceAdministrationTest {
	
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
		private final AdministratorHandler ah;
		private final WorkspaceAdministration admin;
		
		private TestMocks(
				final Workspace ws,
				final WorkspaceServerMethods wsmeth,
				final Types types,
				final AdministratorHandler ah,
				final WorkspaceAdministration admin) {
			this.ws = ws;
			this.wsmeth = wsmeth;
			this.types = types;
			this.ah = ah;
			this.admin = admin;
		}
	}
	
	private TestMocks initTestMocks() {
		final Workspace ws = mock(Workspace.class);
		final WorkspaceServerMethods wsmeth = mock(WorkspaceServerMethods.class);
		final Types types = mock(Types.class);
		final AdministratorHandler ah = mock(AdministratorHandler.class);
		final WorkspaceAdministration admin = new WorkspaceAdministration(ws, wsmeth, types, ah);
		return new TestMocks(ws, wsmeth, types, ah, admin);
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
	
	/* ***********************************************
	 * Non command specific error tests
	 * ***********************************************
	 */
	
	@Test
	public void failNotAdmin() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "listModRequests"));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "usah"))).thenReturn(AdminRole.NONE);
		
		runCommandFail(mocks.admin, new AuthToken("tok", "usah"), command,
				new IllegalArgumentException("User usah is not an admin"));
	}
	
	@Test
	public void failUnknownCommand() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "hugeIfBlocksAreBad"));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "usah"))).thenReturn(AdminRole.READ_ONLY);
		
		runCommandFail(mocks.admin, new AuthToken("tok", "usah"), command,
				new IllegalArgumentException(
						"I don't know how to process the command: hugeIfBlocksAreBad"));
	}
	
	@Test
	public void failParseException() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject("{,}".toCharArray());
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "usah"))).thenReturn(AdminRole.READ_ONLY);
		
		try {
			mocks.admin.runCommand(new AuthToken("tok", "usah"), command, null);
			fail("expected exception");
		} catch (IllegalArgumentException got) {
			assertThat("incorrect message", got.getMessage(), containsString(
					"Unable to deserialize a workspace admin command from the input: " +
					"Unexpected character (',' (code 44)): was expecting double-quote to " +
					"start field name\n at [Source: java.io.StringReader"));
		}
	}
	
	@Test
	public void failMappingException() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("unexpectedfield", 1));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "usah"))).thenReturn(AdminRole.READ_ONLY);
		
		try {
			mocks.admin.runCommand(new AuthToken("tok", "usah"), command, null);
			fail("expected exception");
		} catch (IllegalArgumentException got) {
			assertThat("incorrect message", got.getMessage(), containsString(
					"Unable to deserialize a workspace admin command from the input: " +
					"Unrecognized field \"unexpectedfield\" (" +
					"class us.kbase.workspace.kbase.admin.AdminCommand), not marked as " +
					"ignorable (4 known properties: , " +
					"\"command\", \"user\", \"module\", \"params\"])"));
		}
	}
	
	@Test
	public void failIOException() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final JsonTokenStream jts = mock(JsonTokenStream.class);
		
		final UObject command = new UObject(jts);
		
		when(jts.setRoot(null)).thenReturn(jts);
		when(jts.getCurrentToken()).thenReturn(JsonToken.START_OBJECT);
		when(jts.nextToken()).thenThrow(new IOException("whoopsie"));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "usah"))).thenReturn(AdminRole.READ_ONLY);
		
		try {
			mocks.admin.runCommand(new AuthToken("tok", "usah"), command, null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IOException("whoopsie"));
		}
		
	}
	
	/* ***********************************************
	 * Command error tests for error types that
	 * affect all commands
	 * ***********************************************
	 */
	
	@Test
	public void failNotFullAdmin() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		when(mocks.ah.getAdminRole(new AuthToken("t", "user1"))).thenReturn(AdminRole.READ_ONLY);
		
		final List<String> commands = Arrays.asList("approveModRequest", "denyModRequest",
				"addAdmin", "removeAdmin", "setWorkspaceOwner", "createWorkspace",
				"setPermissions", "setWorkspaceDescription", "setGlobalPermission",
				"saveObjects", "deleteWorkspace", "undeleteWorkspace", "grantModuleOwnership",
				"removeModuleOwnership");
		
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
	public void failNullUser() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		when(mocks.ah.getAdminRole(new AuthToken("t", "user1"))).thenReturn(AdminRole.ADMIN);
		
		final List<String> commands = Arrays.asList("addAdmin", "removeAdmin", "createWorkspace",
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
		
		for (final String commandStr: commandToClass.keySet()) {
			final UObject command = new UObject(ImmutableMap.of("command", commandStr,
					"user", "u1"));
			
			when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
			when(mocks.wsmeth.validateUsers(Arrays.asList("u1"), new AuthToken("tok", "fake")))
					.thenReturn(Arrays.asList(new WorkspaceUser("u1")));
			
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
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "usah")))
				.thenReturn(AdminRole.ADMIN);
		when(mocks.wsmeth.validateUsers(Arrays.asList("fake"), new AuthToken("tok", "usah")))
				.thenReturn(Arrays.asList(new WorkspaceUser("fake")));
		
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
						"Unable to deserialize " + me.clazz + " out of params field: Can not"));
			}
		}
	}
	
	/* ***********************************************
	 * Command specific tests
	 * ***********************************************
	 */
	
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
				"listModRequests", WorkspaceAdministration.class));
	}
	
	@Test
	public void approveModRequest() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "approveModRequest",
				"module", "somemod"));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		
		mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"approveModRequest somemod", WorkspaceAdministration.class));
		
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
				"denyModRequest somemod", WorkspaceAdministration.class));
		
		verify(mocks.types).resolveModuleRegistration("somemod", false);
	}
	
	@Test
	public void listAdmins() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "listAdmins"));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.ah.getAdmins()).thenReturn(
				set(new WorkspaceUser("a1"), new WorkspaceUser("a2")));
		
		@SuppressWarnings("unchecked")
		final List<String> admins = (List<String>) mocks.admin.runCommand(
				new AuthToken("tok", "fake"), command, null);
		
		assertThat("incorrect admins", new HashSet<>(admins), is(set("a1", "a2")));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"listAdmins", WorkspaceAdministration.class));
	}
	
	@Test
	public void addAdmin() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "addAdmin",
				"user", "someuser"));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		when(mocks.wsmeth.validateUsers(Arrays.asList("someuser"), new AuthToken("tok", "fake")))
				.thenReturn(Arrays.asList(new WorkspaceUser("someuser")));
		
		mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"addAdmin someuser", WorkspaceAdministration.class));
		
		verify(mocks.ah).addAdmin(new WorkspaceUser("someuser"));
	}
	
	@Test
	public void removeAdmin() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "removeAdmin",
				"user", "someuser"));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		when(mocks.wsmeth.validateUsers(Arrays.asList("someuser"), new AuthToken("tok", "fake")))
				.thenReturn(Arrays.asList(new WorkspaceUser("someuser")));
		
		mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"removeAdmin someuser", WorkspaceAdministration.class));
		
		verify(mocks.ah).removeAdmin(new WorkspaceUser("someuser"));
	}
	
	@Test
	public void setWorkspaceOwnerWithNullArgs() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "setWorkspaceOwner",
				"params", ImmutableMap.of("wsi", ImmutableMap.of("id", 3))));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		when(mocks.ws.setWorkspaceOwner(
				// really ws will throw an exception if a null user is passed
				null, new WorkspaceIdentifier(3), null, Optional.absent(), true))
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
				"setWorkspaceOwner 3 owner", WorkspaceAdministration.class));
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
		when(mocks.wsmeth.validateUsers(Arrays.asList("usern"), new AuthToken("tok", "fake")))
				.thenReturn(Arrays.asList(new WorkspaceUser("usern")));
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
				"setWorkspaceOwner 10 usern", WorkspaceAdministration.class));
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
		when(mocks.wsmeth.validateUsers(Arrays.asList("user1"), new AuthToken("tok", "fake")))
				.thenReturn(Arrays.asList(new WorkspaceUser("user1")));
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
				"createWorkspace 7 user1", WorkspaceAdministration.class));
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
				"setPermissions 24 a u1 u2", WorkspaceAdministration.class));
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
				"setWorkspaceDescription 8", WorkspaceAdministration.class));
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
				"getWorkspaceDescription null ws1", WorkspaceAdministration.class));
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
				"getPermissions 3 null", WorkspaceAdministration.class));
	}
	
	@Test
	public void getPermissionsWithUser() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "getPermissions",
				"user", "auser",
				"params", ImmutableMap.of("workspace", "foo")));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.wsmeth.validateUsers(Arrays.asList("auser"), new AuthToken("tok", "fake")))
				.thenReturn(Arrays.asList(new WorkspaceUser("auser")));
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
				"getPermissions null foo auser", WorkspaceAdministration.class));
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
				"getPermissionsMass 2 workspaces in input", WorkspaceAdministration.class));
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
				"getWorkspaceInfo 10", WorkspaceAdministration.class));
	}

	@Test
	public void setGlobalPermissions() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final UObject command = new UObject(ImmutableMap.of("command", "setGlobalPermission",
				"user", "auser",
				"params", ImmutableMap.of("workspace", "foo", "new_permission", "r")));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake"))).thenReturn(AdminRole.ADMIN);
		when(mocks.wsmeth.validateUsers(Arrays.asList("auser"), new AuthToken("tok", "fake")))
				.thenReturn(Arrays.asList(new WorkspaceUser("auser")));
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
				"setGlobalPermission 65 r auser", WorkspaceAdministration.class));
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
		when(mocks.wsmeth.validateUsers(Arrays.asList("auser"), new AuthToken("tok", "fake")))
				.thenReturn(Arrays.asList(new WorkspaceUser("auser")));
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
				"saveObjects auser", WorkspaceAdministration.class));
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
				"getObjectInfo", WorkspaceAdministration.class));
	}
	
}
