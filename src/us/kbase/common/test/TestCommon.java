package us.kbase.common.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.Document;
import org.ini4j.Ini;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.mongodb.client.MongoDatabase;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import us.kbase.common.test.TestException;
import us.kbase.typedobj.core.TempFilesManager;

public class TestCommon {
	
	public static final String MINIOEXE = "test.minio.exe";
	public static final String BLOBSTOREEXE = "test.blobstore.exe";
	public static final String MONGOEXE = "test.mongo.exe";
	public static final String MONGO_USE_WIRED_TIGER = "test.mongo.useWiredTiger";
	public static final String HANDLE_SERVICE_DIR = "test.handleservice.dir";
	public static final String SAMPLE_SERVICE_DIR = "test.sampleservice.dir";
	public static final String ARANGOEXE = "test.arango.exe";
	public static final String ARANGOJS = "test.arango.js";
	
	public static final String JARS_PATH = "test.jars.dir";
	
	public static final String TEST_TEMP_DIR = "test.temp.dir";
	public static final String KEEP_TEMP_DIR = "test.temp.dir.keep";
	
	public static final String TEST_CONFIG_FILE_PROP_NAME = "test.cfg";
	public static final String TEST_CONFIG_FILE_SECTION = "Workspacetest";
	
	public static final String LONG101;
	public static final String LONG1001;
	static {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 100; i++) {
			sb.append("a");
		}
		final String s100 = sb.toString();
		final StringBuilder sb2 = new StringBuilder();
		for (int i = 0; i < 10; i++) {
			sb2.append(s100);
		}
		LONG101 = s100 + "a";
		LONG1001 = sb2.toString() + "a";
	}
	
	private static Map<String, String> testConfig = null;
			
	public static void stfuLoggers() {
		((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
				.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
			.setLevel(ch.qos.logback.classic.Level.OFF);
		java.util.logging.Logger.getLogger("com.mongodb")
			.setLevel(java.util.logging.Level.OFF);
	}
	
	public static void printJava() {
		System.out.println("Java: " +
				System.getProperty("java.runtime.version"));
	}
	
	public static String getTestProperty(final String propertyKey, final boolean allowNull) {
		getTestConfig();
		final String prop = testConfig.get(propertyKey);
		if (!allowNull && (prop == null || prop.trim().isEmpty())) {
			throw new TestException(String.format(
					"Property %s in section %s of test file %s is missing",
					propertyKey, TEST_CONFIG_FILE_SECTION, getConfigFilePath()));
		}
		return prop;
	}
	
	public static String getTestProperty(final String propertyKey) {
		return getTestProperty(propertyKey, false);
	}

	private static void getTestConfig() {
		if (testConfig != null) {
			return;
		}
		final Path testCfgFilePath = getConfigFilePath();
		final Ini ini;
		try {
			ini = new Ini(testCfgFilePath.toFile());
		} catch (IOException ioe) {
			throw new TestException(String.format(
					"IO Error reading the test configuration file %s: %s",
					testCfgFilePath, ioe.getMessage()), ioe);
		}
		testConfig = ini.get(TEST_CONFIG_FILE_SECTION);
		if (testConfig == null) {
			throw new TestException(String.format("No section %s found in test config file %s",
					TEST_CONFIG_FILE_SECTION, testCfgFilePath));
		}
	}

	private static Path getConfigFilePath() {
		final String testCfgFilePathStr = System.getProperty(TEST_CONFIG_FILE_PROP_NAME);
		if (testCfgFilePathStr == null || testCfgFilePathStr.trim().isEmpty()) {
			throw new TestException(String.format("Cannot get the test config file path." +
					" Ensure the java system property %s is set to the test config file location.",
					TEST_CONFIG_FILE_PROP_NAME));
		}
		return Paths.get(testCfgFilePathStr).toAbsolutePath().normalize();
	}
	
	// TODO CODE return Paths for all of these
	public static String getTempDir() {
		return getTestProperty(TEST_TEMP_DIR);
	}
	
	public static String getMongoExe() {
		return getTestProperty(MONGOEXE);
	}
	
	public static String getBlobstoreExe() {
		return getTestProperty(BLOBSTOREEXE);
	}
	
	public static Path getArangoExe() {
		return Paths.get(getTestProperty(ARANGOEXE));
	}
	
	public static Path getArangoJS() {
		return Paths.get(getTestProperty(ARANGOJS));
	}

	public static String getMinioExe() {
		return getTestProperty(MINIOEXE);
	}
	
	public static String getHandleServiceDir() {
		return getTestProperty(HANDLE_SERVICE_DIR);
	}
	
	public static Path getSampleServiceDir() {
		return Paths.get(getTestProperty(SAMPLE_SERVICE_DIR));
	}
	
	public static Path getJarsDir() {
		return Paths.get(getTestProperty(JARS_PATH));
	}
	
	public static boolean getDeleteTempFiles() {
		return !"true".equals(getTestProperty(KEEP_TEMP_DIR));
	}
	
	public static boolean useWiredTigerEngine() {
		return "true".equals(getTestProperty(MONGO_USE_WIRED_TIGER));
	}
	
	public static void destroyDB(final MongoDatabase db) {
		for (String name: db.listCollectionNames()) {
			if (!name.startsWith("system.")) {
				// dropping collection also drops indexes
				db.getCollection(name).deleteMany(new Document());
			}
		}
	}
	
	
	/** Get the name of a method in the stack.
	 * @param index determines which method to get. 1 = the method calling this method,
	 * 2 = the method calling that method, etc.
	 * @return the method name.
	 */
	public static String getMethodName(final int index) {
		return new Throwable().getStackTrace()[index].getMethodName();
	}
	
	public static void assertExceptionCorrect(
			final Throwable got,
			final Throwable expected) {
		assertThat("incorrect exception. trace:\n" +
				ExceptionUtils.getStackTrace(got),
				got.getLocalizedMessage(),
				is(expected.getLocalizedMessage()));
		assertThat("incorrect exception type", got, instanceOf(expected.getClass()));
	}
	
	public static void assertCloseToNow(final long epochMillis) {
		assertCloseToNow(epochMillis, 1000);
	}
	
	public static void assertCloseToNow(final long epochMillis, final int rangeMillis) {
		final long now = Instant.now().toEpochMilli();
		assertThat(String.format("time (%s) not within %sms of now: %s",
				epochMillis, rangeMillis, now),
				Math.abs(epochMillis - now) < rangeMillis, is(true));
	}
	
	public static void assertCloseToNow(final Instant creationDate) {
		assertCloseToNow(creationDate.toEpochMilli());
	}
	
	public static void assertCloseToNow(final Instant creationDate, final int rangeMillis) {
		assertCloseToNow(creationDate.toEpochMilli(), rangeMillis);
	}

	public static void assertNoTempFilesExist(TempFilesManager tfm)
			throws Exception {
		assertNoTempFilesExist(Arrays.asList(tfm));
	}
	
	
	public static void assertNoTempFilesExist(List<TempFilesManager> tfms)
			throws Exception {
		int i = 0;
		try {
			for (TempFilesManager tfm: tfms) {
				if (!tfm.isEmpty()) {
					// Allow <=10 seconds to finish all activities
					for (; i < 100; i++) {
						Thread.sleep(100);
						if (tfm.isEmpty())
							break;
					}
				}
				// this is weird. Should it just be a fail()?
				assertThat("There are tempfiles: " + tfm.getTempFileList(),
						tfm.isEmpty(), is(true));
			}
		} finally {
			for (TempFilesManager tfm: tfms)
				tfm.cleanup();
		}
	}
	
	//http://quirkygba.blogspot.com/2009/11/setting-environment-variables-in-java.html
	@SuppressWarnings("unchecked")
	public static Map<String, String> getenv()
			throws NoSuchFieldException, SecurityException,
			IllegalArgumentException, IllegalAccessException {
		Map<String, String> unmodifiable = System.getenv();
		Class<?> cu = unmodifiable.getClass();
		Field m = cu.getDeclaredField("m");
		m.setAccessible(true);
		return (Map<String, String>) m.get(unmodifiable);
	}
	
	@SafeVarargs
	public static <T> Set<T> set(final T... objects) {
		return new HashSet<T>(Arrays.asList(objects));
	}
	
	@SafeVarargs
	public static <T> List<T> list(final T... objects) {
		return Arrays.asList(objects);
	}
	
	public static Instant inst(final long epoch) {
		return Instant.ofEpochMilli(epoch);
	}
	
	public static Instant now() {
		return Instant.now();
	}
	
	public static final Optional<String> ES = Optional.empty();
	public static final Optional<Long> EL = Optional.empty();
	public static final Optional<Integer> EI = Optional.empty();
	
	public static <T> Optional<T> opt(final T obj) {
		return Optional.of(obj);
	}
	
	public static <T> Optional<T> optn(final T obj) {
		return Optional.ofNullable(obj);
	}
	
	public static class LogEvent {
		
		public final Level level;
		public final String message;
		public final String className;
		public final Throwable ex;
		
		public LogEvent(final Level level, final String message, final Class<?> clazz) {
			this.level = level;
			this.message = message;
			this.className = clazz.getName();
			ex = null;
		}

		public LogEvent(final Level level, final String message, final String className) {
			this.level = level;
			this.message = message;
			this.className = className;
			ex = null;
		}
		
		public LogEvent(
				final Level level,
				final String message,
				final Class<?> clazz,
				final Throwable ex) {
			this.level = level;
			this.message = message;
			this.className = clazz.getName();
			this.ex = ex;
		}
		
		public LogEvent(
				final Level level,
				final String message,
				final String className,
				final Throwable ex) {
			this.level = level;
			this.message = message;
			this.className = className;
			this.ex = ex;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("LogEvent [level=");
			builder.append(level);
			builder.append(", message=");
			builder.append(message);
			builder.append(", className=");
			builder.append(className);
			builder.append(", ex=");
			builder.append(ex);
			builder.append("]");
			return builder.toString();
		}
	}
	
	public static List<ILoggingEvent> setUpSLF4JTestLoggerAppender(final String package_) {
		final Logger authRootLogger = (Logger) LoggerFactory.getLogger(package_);
		authRootLogger.setAdditive(false);
		authRootLogger.setLevel(Level.ALL);
		final List<ILoggingEvent> logEvents = new LinkedList<>();
		final AppenderBase<ILoggingEvent> appender =
				new AppenderBase<ILoggingEvent>() {
			@Override
			protected void append(final ILoggingEvent event) {
				logEvents.add(event);
			}
		};
		appender.start();
		authRootLogger.addAppender(appender);
		return logEvents;
	}
	
	public static void assertLogEventsCorrect(
			final List<ILoggingEvent> logEvents,
			final LogEvent... expectedlogEvents) {
		
		assertThat("incorrect log event count for list: " + logEvents, logEvents.size(),
				is(expectedlogEvents.length));
		final Iterator<ILoggingEvent> iter = logEvents.iterator();
		for (final LogEvent le: expectedlogEvents) {
			final ILoggingEvent e = iter.next();
			assertThat("incorrect log level", e.getLevel(), is(le.level));
			assertThat("incorrect originating class", e.getLoggerName(), is(le.className));
			assertThat("incorrect message", e.getFormattedMessage(), is(le.message));
			final IThrowableProxy err = e.getThrowableProxy();
			if (err != null) {
				if (le.ex == null) {
					fail(String.format("Logged exception where none was expected: %s %s %s",
							err.getClassName(), err.getMessage(), le));
				} else {
					assertThat("incorrect error class for event " + le, err.getClassName(),
							is(le.ex.getClass().getName()));
					assertThat("incorrect error message for event " + le, err.getMessage(),
							is(le.ex.getMessage()));
				}
			} else if (le.ex != null) { 
				fail("Expected exception but none was logged: " + le);
			}
		}
	}
	
	public static void createAuthUser(
			final URL authURL,
			final String userName,
			final String displayName)
			throws Exception {
		final URL target = new URL(authURL.toString() + "/api/V2/testmodeonly/user");
		final HttpURLConnection conn = getPOSTConnection(target);

		final DataOutputStream writer = new DataOutputStream(conn.getOutputStream());
		writer.writeBytes(new ObjectMapper().writeValueAsString(ImmutableMap.of(
				"user", userName,
				"display", displayName)));
		writer.flush();
		writer.close();

		checkForError(conn);
	}

	private static HttpURLConnection getPOSTConnection(final URL target) throws Exception {
		return getConnection("POST", target);
	}
	
	private static HttpURLConnection getPUTConnection(final URL target) throws Exception {
		return getConnection("PUT", target);
	}
	
	private static HttpURLConnection getConnection(final String verb, final URL target)
			throws Exception {
		final HttpURLConnection conn = (HttpURLConnection) target.openConnection();
		conn.setRequestMethod(verb);
		conn.setRequestProperty("content-type", "application/json");
		conn.setRequestProperty("accept", "application/json");
		conn.setDoOutput(true);
		return conn;
	}

	private static void checkForError(final HttpURLConnection conn) throws IOException {
		final int rescode = conn.getResponseCode();
		if (rescode < 200 || rescode >= 300) {
			System.out.println("Response code: " + rescode);
			String err = IOUtils.toString(conn.getErrorStream()); 
			System.out.println(err);
			if (err.length() > 200) {
				err = err.substring(0, 200);
			}
			throw new TestException(err);
		}
	}

	public static String createLoginToken(final URL authURL, String user) throws Exception {
		final URL target = new URL(authURL.toString() + "/api/V2/testmodeonly/token");
		final HttpURLConnection conn = getPOSTConnection(target);

		final DataOutputStream writer = new DataOutputStream(conn.getOutputStream());
		writer.writeBytes(new ObjectMapper().writeValueAsString(ImmutableMap.of(
				"user", user,
				"type", "Login")));
		writer.flush();
		writer.close();

		checkForError(conn);
		final String out = IOUtils.toString(conn.getInputStream());
		@SuppressWarnings("unchecked")
		final Map<String, Object> resp = new ObjectMapper().readValue(out, Map.class);
		return (String) resp.get("token");
	}
	
	public static void createCustomRole(
			final URL authURL,
			final String role,
			final String description)
			throws Exception {
		final URL target = new URL(authURL.toString() + "/api/V2/testmodeonly/customroles");
		final HttpURLConnection conn = getPOSTConnection(target);

		final DataOutputStream writer = new DataOutputStream(conn.getOutputStream());
		writer.writeBytes(new ObjectMapper().writeValueAsString(ImmutableMap.of(
				"id", role,
				"desc", description)));
		writer.flush();
		writer.close();

		checkForError(conn);
	}
	
	// will zero out standard roles, which don't do much in test mode
	public static void setUserRoles(
			final URL authURL,
			final String user,
			final List<String> customRoles)
			throws Exception {
		final URL target = new URL(authURL.toString() + "/api/V2/testmodeonly/userroles");
		final HttpURLConnection conn = getPUTConnection(target);
		
		final DataOutputStream writer = new DataOutputStream(conn.getOutputStream());
		writer.writeBytes(new ObjectMapper().writeValueAsString(ImmutableMap.of(
				"user", user,
				"customroles", customRoles)));
		writer.flush();
		writer.close();

		checkForError(conn);
	}
}
