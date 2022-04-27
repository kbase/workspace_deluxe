package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.controllers.ControllerCommon.makeTempDirs;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;

import us.kbase.common.test.MapBuilder;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.mongo.SchemaUpdater;
import us.kbase.workspace.database.mongo.SchemaUpdater.SchemaUpdateException;
import us.kbase.workspace.kbase.KBaseWorkspaceConfig;
import us.kbase.workspace.kbase.SchemaUpdaterCLI;
import us.kbase.workspace.kbase.SchemaUpdaterCLI.MongoProvider;
import us.kbase.workspace.kbase.InitWorkspaceServer.WorkspaceInitException;

public class SchemaUpdaterCLITest {
	
	private static final String VERSION = "0.13.2-dev1";
	
	private static final List<String> HELP = list(
			"Usage: update_workspace_database_schema [-chosV] <CONFIG_FILE>",
			"Update the workspace database to the current version.",
			"Please read the upgrade documentation carefully before running this script.",
			"      <CONFIG_FILE>        The configuration file (usually called deploy.cfg)",
			"                             for the workspace service to modify.",
			"  -c, --complete           Complete the upgrade. WARNING: DO NOT complete the",
			"                             upgrade unless no other processes are writing to",
			"                             the workspace database.",
			"  -h, --help               Show this help message and exit.",
			"  -o, --override-version-check",
			"                           Allow the upgrade to continue even if the database",
			"                             schema version indicates it is complete.",
			"  -s, --print-stacktrace   On an error, print a stacktrace if available.",
			"  -V, --version            Print version information and exit."
			);

	private static final Map<String, String> VALID_CONFIG = MapBuilder.<String, String>newHashMap()
			.with("auth2-service-url", "https://ci.kbase.us/services/auth/")
			.with("auth-service-url",
					"https://ci.kbase.us/services/auth/api/legacy/KBase/Sessions/Login")
			.with("mongodb-host", "localhost:1")
			.with("mongodb-database", "thingy")
			.with("mongodb-type-database", "typethingy")
			.with("backend-type", "GridFS")
			.with("temp-dir", "somedir")
			.with("ignore-handle-service", "true")
			.build();

	private class Mocks {
		public final SchemaUpdater updater;
		public final MongoProvider provider;
		public final MongoClient client;
		public final MongoDatabase db;

		public Mocks() {
			this.updater = mock(SchemaUpdater.class);
			this.provider = mock(MongoProvider.class);
			this.client = mock(MongoClient.class);
			this.db = mock(MongoDatabase.class);
			when(this.client.getDatabase("thingy")).thenReturn(this.db);
		}
	}
	
	private static Path TEMP_DIR;
	private static Path VALID_CONFIG_FILE;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		TEMP_DIR = makeTempDirs(
				Paths.get(TestCommon.getTempDir()),
				SchemaUpdaterCLITest.class.getSimpleName(),
				Collections.emptyList());
		VALID_CONFIG_FILE = KBaseWorkspaceConfigTest.writeConfig(
				VALID_CONFIG, TEMP_DIR, "validconfig");
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (TEMP_DIR != null && TestCommon.getDeleteTempFiles()) {
			FileUtils.deleteDirectory(TEMP_DIR.toFile());
		}
	}
	
	@SafeVarargs
	public static <T> T[] array(T... items) {
		return items;
	}
	
	@SafeVarargs
	public static <T> List<T> list(T... items) {
		return Arrays.asList(items);
	}
	
	private static class UpdateAnswer implements Answer<Long> {

		private final List<String> logsToSend;
		
		public UpdateAnswer(final List<String> logsToSend) {
			this.logsToSend = logsToSend;
		}

		@Override
		public Long answer(final InvocationOnMock inv) throws Throwable {
			final Consumer<String> logger = inv.getArgument(1);
			logsToSend.forEach(l -> logger.accept(l));
			return 0L; // value is ignored by the CLI code
		}
	}
	
	private void runUpdater(final Mocks mocks, final String[] args, final String expectedOut) {
		runUpdater(mocks, args, expectedOut, "", 0);
	}
	
	private void runUpdater(
			final Mocks mocks,
			final String[] args,
			final String expectedOut,
			final String expectedErr,
			final int expectedExitCode) {
		
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final ByteArrayOutputStream err = new ByteArrayOutputStream();
		
		final int exitCode = SchemaUpdaterCLI.run(mocks.updater, mocks.provider, out, err, args);
		
		assertThat("incorrect sys err", err.toString(), is(expectedErr));
		assertThat("incorrect sys out", out.toString(), is(expectedOut));
		assertThat("incorrect exit code", exitCode, is(expectedExitCode));
	}
	
	private void runUpdater(
			final Mocks mocks,
			final String[] args,
			final String expectedOut,
			final List<String> expectedInErr,
			final int expectedExitCode) {
		// trying to merge this with the above turned into a ridiculous mess since you want to
		// check the error conditions first
		
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final ByteArrayOutputStream err = new ByteArrayOutputStream();
		
		final int exitCode = SchemaUpdaterCLI.run(mocks.updater, mocks.provider, out, err, args);
		
		for (final String errstr: expectedInErr) {
			assertThat("incorrect sys err", err.toString(), containsString(errstr));
		}
		assertThat("incorrect sys out", out.toString(), is(expectedOut));
		assertThat("incorrect exit code", exitCode, is(expectedExitCode));
	}
	
	public String generateHelpString(final List<String> preface) {
		final LinkedList<String> helpCopy = new LinkedList<String>(HELP);
		Collections.reverse(preface);
		preface.forEach(i -> helpCopy.addFirst(i));
		return String.join("\n", helpCopy) + "\n";
	}
	
	@Test
	public void runNoArgs() throws Exception {
		run(array(VALID_CONFIG_FILE.toString()), false, false);
	}
	
	@Test
	public void runSingleCharArgs() throws Exception {
		run(array("-c", VALID_CONFIG_FILE.toString(), "-o"), true, true);
	}
	
	@Test
	public void runFullLengthArgs() throws Exception {
		run(array("--override-version-check", VALID_CONFIG_FILE.toString(), "--complete"),
				true, true);
	}
	
	private void run(final String[] args, final boolean complete, final boolean override)
			throws Exception {
		final Mocks m = new Mocks();
		
		when(m.provider.provide(new KBaseWorkspaceConfig(VALID_CONFIG))).thenReturn(m.client);
		when(m.updater.update(eq(m.db), any(), eq(complete), eq(override))).then(new UpdateAnswer(
				list("I'm, like, supposed to find some types or something?", "whatever")));
		
		runUpdater(m, args, "I'm, like, supposed to find some types or something?\nwhatever\n");
	}
	
	@Test
	public void version() throws Exception {
		version(array("-V"));
		version(array("--version"));
	}
	
	private void version(final String[] args) {
		runUpdater(new Mocks(), args, VERSION + "\n");
	}

	@Test
	public void help() throws Exception {
		help(array("-h"));
		help(array("--help"));
	}
	
	private void help(final String[] args) {
		runUpdater(new Mocks(), args, String.join("\n", HELP) + "\n");
	}

	@Test
	public void badOptionFail() throws Exception {
		final String err =
				"Unknown option: '--o'\nPossible solutions: -o, --override-version-check\n";
		runUpdater(new Mocks(), array("--o", VALID_CONFIG_FILE.toString()), "", err, 2);
	}
	
	@Test
	public void noArgsFail() throws Exception {
		final String err = generateHelpString(list("Missing required parameter: '<CONFIG_FILE>'"));
		runUpdater(new Mocks(), array(), "", err, 2);
	}
	
	@Test
	public void tooManyPositionalsFail() throws Exception {
		final String err = generateHelpString(list("Unmatched argument at index 1: 'file2'"));
		runUpdater(new Mocks(), array("file1", "file2"), "", err, 2);
	}
	
	@Test
	public void noConfigFileFail() throws Exception {
		final Path file = Paths.get("super_fake_file");
		final String err = generateHelpString(list(String.format(
				"Could not read from configuration file super_fake_file: %s " +
						"(No such file or directory)",
				file.toAbsolutePath())));
		runUpdater(new Mocks(), array(file.toString()), "", err, 2);
		
		final List<String> expectedErrs = Arrays.asList(
				err,
				"us.kbase.workspace.kbase.KBaseWorkspaceConfig$KBaseWorkspaceConfigException: " +
						"Could not read from configuration file super_fake_file:",
				"Caused by: java.io.FileNotFoundException: /",
				".workspace.kbase.KBaseWorkspaceConfig.getConfigMap(KBaseWorkspaceConfig.java"
				);
		runUpdater(new Mocks(), array("-s", file.toString()), "", expectedErrs, 2);
		runUpdater(new Mocks(), array("--print-stacktrace", file.toString()), "", expectedErrs, 2);
	}
	
	@Test
	public void invalidConfigFileFail() throws Exception {
		final Map<String, String> configCopy = new HashMap<>(VALID_CONFIG);
		configCopy.remove("auth2-service-url");
		configCopy.remove("mongodb-database");
		final Path file = KBaseWorkspaceConfigTest.writeConfig(
				configCopy, TEMP_DIR, TestCommon.getMethodName(1));
		final String err = generateHelpString(list(
				"Must provide param mongodb-database in config file",
				"Must provide param auth2-service-url in config file",
				"Invalid specification file " + file.toAbsolutePath()));
		runUpdater(new Mocks(), array(file.toString()), "", err, 2);
	}
	
	@Test
	public void workspaceInitFail() throws Exception {
		final Mocks m = new Mocks();
		when(m.provider.provide(new KBaseWorkspaceConfig(VALID_CONFIG)))
				.thenThrow(new WorkspaceInitException("oh dang"));
		final String err = generateHelpString(list("oh dang"));
		final String file = VALID_CONFIG_FILE.toString();
		runUpdater(m, array(file), "", err, 2);
		
		final List<String> expectedErrs = Arrays.asList(
				err,
				"us.kbase.workspace.kbase.InitWorkspaceServer$WorkspaceInitException: oh dang",
				"at us.kbase.workspace.kbase.SchemaUpdaterCLI.call(SchemaUpdaterCLI.",
				"at us.kbase.workspace.kbase.SchemaUpdaterCLI.run(SchemaUpdaterCLI.java"
				);
		runUpdater(m, array("-s", file.toString()), "", expectedErrs, 2);
		runUpdater(m, array("--print-stacktrace", file.toString()), "", expectedErrs, 2);
	}
	
	@Test
	public void schemaUpdateFail() throws Exception {
		final Mocks m = new Mocks();
		
		when(m.provider.provide(new KBaseWorkspaceConfig(VALID_CONFIG))).thenReturn(m.client);
		when(m.updater.update(eq(m.db), any(), eq(false), eq(false)))
				.thenThrow(new SchemaUpdateException("well kiss my grits"));
		final String err = generateHelpString(list("well kiss my grits"));
		final String file = VALID_CONFIG_FILE.toString();
		runUpdater(m, array(file), "", err, 2);
		
		final List<String> expectedErrs = Arrays.asList(
				err,
				"workspace.database.mongo.SchemaUpdater$SchemaUpdateException: well kiss my grits",
				"at us.kbase.workspace.kbase.SchemaUpdaterCLI.call(SchemaUpdaterCLI",
				"at us.kbase.workspace.kbase.SchemaUpdaterCLI.run(SchemaUpdaterCLI.java"
				);
		runUpdater(m, array("-s", file.toString()), "", expectedErrs, 2);
		runUpdater(m, array("--print-stacktrace", file.toString()), "", expectedErrs, 2);
	}
}
