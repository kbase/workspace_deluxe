package us.kbase.workspace.test.kbase.admin;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.assertLogEventsCorrect;
import static us.kbase.common.test.TestCommon.set;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableMap;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.UObject;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.TestCommon.LogEvent;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.kbase.admin.AdminCommand;
import us.kbase.workspace.kbase.admin.AdminRole;
import us.kbase.workspace.kbase.admin.AdministratorHandler;
import us.kbase.workspace.kbase.admin.AdministratorHandlerException;
import us.kbase.workspace.kbase.admin.WorkspaceAdministration;
import us.kbase.workspace.kbase.admin.WorkspaceAdministration.AdminCommandHandler;
import us.kbase.workspace.kbase.admin.WorkspaceAdministration.AdminCommandSpecification;
import us.kbase.workspace.kbase.admin.WorkspaceAdministration.UserValidator;

public class WorkspaceAdministrationTest {
	
	private static final AdminCommand COM = new ObjectMapper().convertValue(
			ImmutableMap.of("command", "foo"), AdminCommand.class);
	
	private static List<ILoggingEvent> logEvents;
	
	@BeforeClass
	public static void beforeClass() {
		logEvents = TestCommon.setUpSLF4JTestLoggerAppender("us.kbase.workspace");
	}
	
	@Before
	public void before() {
		logEvents.clear();
	}
	
	private class FakeTicker extends Ticker {

		private final AtomicLong nanos = new AtomicLong();

		public FakeTicker advance(long nanoseconds) {
			nanos.addAndGet(nanoseconds);
			return this;
		}

		@Override
		public long read() {
			return nanos.get();
		}
	}
	
	private static class TestMocks {
		private final AdministratorHandler ah;
		private final UserValidator uval;
		private final FakeTicker ticker;
		private final WorkspaceAdministration admin;
		
		private TestMocks(
				final AdministratorHandler ah,
				final UserValidator uval,
				final FakeTicker ticker,
				final WorkspaceAdministration admin) {
			this.ah = ah;
			this.uval = uval;
			this.ticker = ticker;
			this.admin = admin;
		}
	}
	
	private TestMocks initTestMocks() {
		return initTestMocks(0, 0, null);
	}
	
	private TestMocks initTestMocks(final int cacheSize, final int cacheTimeMS) {
		return initTestMocks(cacheSize, cacheTimeMS, new FakeTicker());
	}
		
	private TestMocks initTestMocks(
			final int cacheSize,
			final int cacheTimeMS,
			final FakeTicker ticker) {
		final UserValidator userVal = mock(UserValidator.class);
		final AdministratorHandler ah = mock(AdministratorHandler.class);
		final WorkspaceAdministration admin = WorkspaceAdministration.getBuilder(ah, userVal)
				.withCacheMaxSize(cacheSize)
				.withCacheTimeMS(cacheTimeMS)
				.withCacheTicker(ticker)
				.build();
		return new TestMocks(ah, userVal, ticker, admin);
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
	public void constantsAndImmutabilityThereof() throws Exception {
		assertThat("incorrect cache MS", WorkspaceAdministration.DEFAULT_CACHE_EXP_TIME_MS,
				is(300000));
		assertThat("incorrect cache size", WorkspaceAdministration.DEFAULT_CACHE_MAX_SIZE,
				is(100));
		assertThat("incorrect reserved commands", WorkspaceAdministration.RESERVED_COMMANDS,
				is(set("removeAdmin", "addAdmin", "listAdmins")));
		
		try {
			WorkspaceAdministration.RESERVED_COMMANDS.remove("listAdmins");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passes
		}
	}
	
	@Test
	public void adminCommandSpec() throws Exception {
		final AdminCommandSpecification acs = new AdminCommandSpecification(
				"mycommand",
				(cmd, token, toDelete) -> Arrays.asList(cmd, token, toDelete)
				);
		final ThreadLocal<List<WorkspaceObjectData>> tl = new ThreadLocal<>();
		
		assertThat("incorrect command", acs.getName(), is("mycommand"));
		assertThat("incorrect write req", acs.isRequireWrite(), is(false));
		assertThat("incorrect run", acs.runCommand(COM, new AuthToken("t", "foo"), tl),
				is(Arrays.asList(COM, new AuthToken("t", "foo"), tl)));
		
		for (final boolean write: Arrays.asList(true, false)) {
			final AdminCommandSpecification acs2 = new AdminCommandSpecification(
					"mycommand",
					(cmd, token, toDelete) -> Arrays.asList(toDelete, cmd, token),
					write);
			
			assertThat("incorrect command", acs2.getName(), is("mycommand"));
			assertThat("incorrect write req", acs2.isRequireWrite(), is(write));
			assertThat("incorrect run", acs2.runCommand(COM, new AuthToken("t", "foo"), tl),
					is(Arrays.asList(tl, COM, new AuthToken("t", "foo"))));
		}
	}
	
	@Test
	public void adminCommandSpecConstructFail() throws Exception {
		failAdminCommandSpecConstruct(null, (c, t, d) -> {return null;},
				new NullPointerException("commandName")); 
		failAdminCommandSpecConstruct("c", null, new NullPointerException("commandHandler"));
	}
	
	private void failAdminCommandSpecConstruct(
			final String command,
			final AdminCommandHandler handler,
			final Exception expected) {
		try {
			new AdminCommandSpecification(command, handler);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
		try {
			new AdminCommandSpecification(command, handler, true);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	
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
					"start field name"));
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
					"ignorable (4 known properties: " +
					"\"command\", \"module\", \"params\", \"user\"])"));
		}
	}
	
	@Test
	public void failIOException() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		final JsonTokenStream jts = mock(JsonTokenStream.class);
		
		final UObject command = new UObject(jts);
		
		when(jts.setRoot(null)).thenReturn(jts);
		when(jts.getCurrentToken()).thenReturn(JsonToken.START_OBJECT);
		when(jts.isExpectedStartObjectToken()).thenReturn(true);
		when(jts.nextToken()).thenThrow(new IOException("whoopsie"));
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "usah"))).thenReturn(AdminRole.READ_ONLY);
		
		runCommandFail(
				mocks.admin, new AuthToken("tok", "usah"), command, new IOException("whoopsie"));
	}
	
	@Test
	public void failNotFullAdmin() throws Exception {
		final TestMocks mocks = initTestMocks();
		
		when(mocks.ah.getAdminRole(new AuthToken("t", "user1"))).thenReturn(AdminRole.READ_ONLY);
		
		for (final String command: Arrays.asList("addAdmin", "removeAdmin")) {
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
		when(mocks.uval.validateUser(null, new AuthToken("t", "user1")))
				.thenThrow(new NullPointerException("User may not be null"));
		
		for (final String command: Arrays.asList("addAdmin", "removeAdmin")) {
			runCommandFail(
					mocks.admin,
					new AuthToken("t", "user1"),
					new UObject(ImmutableMap.of("command", command)),
					new NullPointerException("User may not be null"));
		}
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
		when(mocks.uval.validateUser("someuser", new AuthToken("tok", "fake")))
				.thenReturn(new WorkspaceUser("someuser"));
		
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
		when(mocks.uval.validateUser("someuser", new AuthToken("tok", "fake")))
				.thenReturn(new WorkspaceUser("someuser"));
		
		mocks.admin.runCommand(new AuthToken("tok", "fake"), command, null);
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.INFO,
				"removeAdmin someuser", WorkspaceAdministration.class));
		
		verify(mocks.ah).removeAdmin(new WorkspaceUser("someuser"));
	}
	
	/* *****************************************
	 * Cache related tests
	 * *****************************************
	 */
	
	@Test
	public void cacheFailHandlerException() throws Exception {
		final TestMocks mocks = initTestMocks(1, 10000000);
		
		when(mocks.ah.getAdminRole(new AuthToken("tok", "fake")))
				.thenThrow(new AdministratorHandlerException("oopsie"));
		
		try {
			mocks.admin.runCommand(new AuthToken("tok", "fake"), new UObject(ImmutableMap.of(
					"command", "whatever")), null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new AdministratorHandlerException("oopsie"));
		}
	}
	
	@Test
	public void cacheEvictOnTime() throws Exception {
		final TestMocks mocks = initTestMocks(1, 5);
		
		final AuthToken token = new AuthToken("tok", "fake");
		when(mocks.ah.getAdminRole(token))
				.thenReturn(AdminRole.NONE, AdminRole.ADMIN, AdminRole.READ_ONLY, AdminRole.NONE);
		
		runCommandFail(mocks.admin, token, new UObject(ImmutableMap.of("command", "addAdmin")),
				new IllegalArgumentException("User fake is not an admin"));
		
		mocks.ticker.advance(4999999);
		runCommandFail(mocks.admin, token, new UObject(ImmutableMap.of("command", "addAdmin")),
				new IllegalArgumentException("User fake is not an admin"));
		
		when(mocks.uval.validateUser("foo", token)).thenReturn(new WorkspaceUser("foo"));
		
		mocks.ticker.advance(1);
		mocks.admin.runCommand(token, new UObject(ImmutableMap.of("command", "removeAdmin",
				"user", "foo")), null);
		
		when(mocks.uval.validateUser("bar", token)).thenReturn(new WorkspaceUser("bar"));
		
		mocks.ticker.advance(4999999);
		mocks.admin.runCommand(token, new UObject(ImmutableMap.of("command", "removeAdmin",
				"user", "bar")), null);
		
		mocks.ticker.advance(1);
		runCommandFail(mocks.admin, token, new UObject(ImmutableMap.of("command", "addAdmin")),
				new IllegalArgumentException(
						"Full administration rights required for this command"));
		
		mocks.admin.runCommand(token, new UObject(ImmutableMap.of("command", "listAdmins")), null);
		
		mocks.ticker.advance(4999999);
		mocks.admin.runCommand(token, new UObject(ImmutableMap.of("command", "listAdmins")), null);
		
		mocks.ticker.advance(1);
		runCommandFail(mocks.admin, token,
				new UObject(ImmutableMap.of("command", "listAdmins")),
				new IllegalArgumentException("User fake is not an admin"));
		
		
		verify(mocks.ah, never()).addAdmin(any());
		verify(mocks.ah).removeAdmin(new WorkspaceUser("foo"));
		verify(mocks.ah).removeAdmin(new WorkspaceUser("bar"));
		verify(mocks.ah, times(2)).getAdmins();
	}
	
	@Test
	public void cacheEvictOnSize() throws Exception {
		final TestMocks mocks = initTestMocks(2, 1000000000);
		
		final AuthToken token1 = new AuthToken("tok1", "user1");
		final AuthToken token2 = new AuthToken("tok2", "user2");
		final AuthToken token3 = new AuthToken("tok3", "user3");
		
		when(mocks.ah.getAdminRole(token1)).thenReturn(AdminRole.ADMIN, AdminRole.READ_ONLY);
		when(mocks.ah.getAdminRole(token2)).thenReturn(AdminRole.NONE);
		when(mocks.ah.getAdminRole(token3)).thenReturn(AdminRole.ADMIN);
		
		when(mocks.uval.validateUser("foo", token1)).thenReturn(new WorkspaceUser("foo"));
		
		mocks.admin.runCommand(token1, new UObject(ImmutableMap.of("command", "removeAdmin",
				"user", "foo")), null);
		
		runCommandFail(mocks.admin, token2, new UObject(ImmutableMap.of("command", "addAdmin")),
				new IllegalArgumentException("User user2 is not an admin"));
		
		when(mocks.uval.validateUser("bar", token3)).thenReturn(new WorkspaceUser("bar"));
		
		mocks.admin.runCommand(token3, new UObject(ImmutableMap.of("command", "removeAdmin",
				"user", "bar")), null);
		
		// user1 should now be evicted from the cache
		runCommandFail(mocks.admin, token1, new UObject(ImmutableMap.of("command", "addAdmin")),
				new IllegalArgumentException(
						"Full administration rights required for this command"));
		
		verify(mocks.ah, never()).addAdmin(any());
		verify(mocks.ah).removeAdmin(new WorkspaceUser("foo"));
		verify(mocks.ah).removeAdmin(new WorkspaceUser("bar"));
	}
	
	@Test
	public void cacheEvictOnAdminChange() throws Exception {
		// will never evict during the test
		final TestMocks mocks = initTestMocks(100000, 1000000000);
		
		final AuthToken token1 = new AuthToken("tok1", "user1");
		final AuthToken token2 = new AuthToken("tok2", "user2");
		
		when(mocks.ah.getAdminRole(token1)).thenReturn(AdminRole.ADMIN);
		when(mocks.ah.getAdminRole(token2))
				.thenReturn(AdminRole.NONE, AdminRole.ADMIN, AdminRole.NONE);
		
		when(mocks.uval.validateUser("user2", token1)).thenReturn(new WorkspaceUser("user2"));
		
		runCommandFail(mocks.admin, token2, new UObject(ImmutableMap.of("command", "listAdmins")),
				new IllegalArgumentException("User user2 is not an admin"));
		
		// should evict user2 from cache
		mocks.admin.runCommand(token1, new UObject(ImmutableMap.of("command", "addAdmin",
				"user", "user2")), null);
		
		when(mocks.ah.getAdmins()).thenReturn(set(new WorkspaceUser("foo")));
		
		@SuppressWarnings("unchecked")
		final List<String> admins = (List<String>) mocks.admin.runCommand(
				token2, new UObject(ImmutableMap.of("command", "listAdmins")), null);
		assertThat("incorrect admins", admins, is(Arrays.asList("foo")));
		
		// should evict user2 from cache
		mocks.admin.runCommand(token1, new UObject(ImmutableMap.of("command", "removeAdmin",
				"user", "user2")), null);
		
		runCommandFail(mocks.admin, token2, new UObject(ImmutableMap.of("command", "listAdmins")),
				new IllegalArgumentException("User user2 is not an admin"));
		
		verify(mocks.ah, times(1)).getAdmins();
		verify(mocks.ah).addAdmin(new WorkspaceUser("user2"));
		verify(mocks.ah).removeAdmin(new WorkspaceUser("user2"));
	}
	
	/* ###### Builder specific tests #####
	 * Most builder methods are tested in the initTestMocks and cache test functions. These tests
	 * cover parts that aren't.
	 */
	
	@Test
	public void getBuilderFail() throws Exception {
		failGetBuilder(null, (u, t) -> null, new NullPointerException("admin"));
		failGetBuilder(initTestMocks().ah, null, new NullPointerException("userValidator"));
	}
	
	private void failGetBuilder(
			final AdministratorHandler handler,
			final UserValidator uval,
			final Exception expected) {
		try {
			WorkspaceAdministration.getBuilder(handler, uval);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void withCacheSizeFail() throws Exception {
		final TestMocks mocks = initTestMocks();
		for (final int i: Arrays.asList(-1, -100, -1000)) {
			try {
				WorkspaceAdministration.getBuilder(mocks.ah, mocks.uval).withCacheMaxSize(i);
				fail("expected exception");
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(
						got, new IllegalArgumentException("maxCacheSize must be >= 0"));
			}
		}
	}
	
	@Test
	public void withCacheTimeFail() throws Exception {
		final TestMocks mocks = initTestMocks();
		for (final int i: Arrays.asList(-1, -100, -1000)) {
			try {
				WorkspaceAdministration.getBuilder(mocks.ah, mocks.uval).withCacheTimeMS(i);
				fail("expected exception");
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(
						got, new IllegalArgumentException("cacheTimeMS must be >= 0"));
			}
		}
	}
	
	@Test
	public void withCommandFail() throws Exception {
		final AdminCommandHandler h = (c, t, d) -> null;
		
		failWithCommand(null, new NullPointerException("spec"));
		
		for (final String c: Arrays.asList("listAdmins", "addAdmin", "removeAdmin")) {
			failWithCommand(new AdminCommandSpecification(c, h), new IllegalArgumentException(
					"Reserved command: " + c));
		}
	}
	
	private void failWithCommand(final AdminCommandSpecification spec, final Exception expected) {
		final TestMocks m = initTestMocks();
		try {
			WorkspaceAdministration.getBuilder(m.ah, m.uval).withCommand(spec);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	private WorkspaceAdministration runCommandSetup() throws Exception {
		final TestMocks mocks = initTestMocks();
		final WorkspaceAdministration wa = WorkspaceAdministration.getBuilder(mocks.ah, mocks.uval)
				.withCommand(new AdminCommandSpecification(
						"c1", (c, t, d) -> Arrays.asList(d, t, c)))
				.withCommand(new AdminCommandSpecification(
						"c2", (c, t, d) -> Arrays.asList(t, c, d), true))
				.build();
		
		when(mocks.ah.getAdminRole(new AuthToken("t1", "u1"))).thenReturn(AdminRole.READ_ONLY);
		when(mocks.ah.getAdminRole(new AuthToken("t2", "u2"))).thenReturn(AdminRole.ADMIN);
		return wa;
	}
	
	@Test
	public void runCommandFailFullAdmin() throws Exception {
		runCommandFail(
				runCommandSetup(),
				new AuthToken("t1", "u1"),
				new UObject(ImmutableMap.of("command", "c2")),
				new IllegalArgumentException(
						"Full administration rights required for this command"));
	}
		
	@Test
	public void runCommandC1() throws Exception {
		final ThreadLocal<List<WorkspaceObjectData>> tl = new ThreadLocal<>();
		@SuppressWarnings("unchecked")
		final List<Object> r1 = (List<Object>) runCommandSetup().runCommand(
				new AuthToken("t2", "u2"), new UObject(ImmutableMap.of("command", "c2")), tl);
		
		assertThat("incorrect arg count", r1.size(), is(3));
		assertThat("incorrect token", r1.get(0), is(new AuthToken("t2", "u2")));
		assertThat("incorrect command class", r1.get(1), instanceOf(AdminCommand.class));
		final AdminCommand ac = (AdminCommand) r1.get(1);
		assertThat("incorrect command", ac.getCommand(), is("c2"));
		assertThat("incorrect module", ac.getModule(), nullValue());
		assertThat("incorrect params", ac.getParams(), nullValue());
		assertThat("incorrect user", ac.getUser(), nullValue());
		assertThat("incorrect threadlocal", r1.get(2), is(tl));
	}
		
	@Test
	public void runCommandC2() throws Exception {
		final ThreadLocal<List<WorkspaceObjectData>> tl = new ThreadLocal<>();
		@SuppressWarnings("unchecked")
		final List<Object> r2 = (List<Object>) runCommandSetup().runCommand(
				new AuthToken("t1", "u1"),
				new UObject(ImmutableMap.of(
						"command", "c1",
						"params", Arrays.asList("foo", "bar"),
						"module", "m",
						"user", "u3")),
				tl);
		
		assertThat("incorrect arg count", r2.size(), is(3));
		assertThat("incorrect threadlocal", r2.get(0), is(tl));
		assertThat("incorrect token", r2.get(1), is(new AuthToken("t1", "u1")));
		assertThat("incorrect command class", r2.get(2), instanceOf(AdminCommand.class));
		final AdminCommand ac2 = (AdminCommand) r2.get(2);
		assertThat("incorrect command", ac2.getCommand(), is("c1"));
		assertThat("incorrect module", ac2.getModule(), is("m"));
		assertThat("incorrect params", ac2.getParams().asClassInstance(List.class),
				is(Arrays.asList("foo", "bar")));
		assertThat("incorrect user", ac2.getUser(), is("u3"));
	}
}
