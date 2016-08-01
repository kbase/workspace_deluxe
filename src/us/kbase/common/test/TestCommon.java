package us.kbase.common.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthToken;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.common.test.TestException;
import us.kbase.typedobj.core.TempFilesManager;

public class TestCommon {
	
	public static final String AUTHSERV = "test.auth.url";
	public static final String GLOBUS = "test.globus.url";
	
	public static final String SHOCKEXE = "test.shock.exe";
	public static final String SHOCKVER = "test.shock.version";
	public static final String MONGOEXE = "test.mongo.exe";
	public static final String MONGO_USE_WIRED_TIGER = "test.mongo.useWiredTiger";
	public static final String MYSQLEXE = "test.mysql.exe";
	public static final String MYSQL_INSTALL_EXE = "test.mysql.install.exe";
	
	public static final String TEST_TEMP_DIR = "test.temp.dir";
	public static final String KEEP_TEMP_DIR = "test.temp.dir.keep";
	
	public static final String TEST_USER_PREFIX = "test.user";
	public static final String TEST_PWD_PREFIX = "test.pwd";
	public static final String TEST_TOKEN_PREFIX = "test.token";
			
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
	
	public static String getProp(String prop) {
		if (System.getProperty(prop) == null ||
				System.getProperty(prop).isEmpty()) {
			throw new TestException("Property " + prop +
					" cannot be null or the empty string.");
		}
		return System.getProperty(prop);
	}
	
	public static boolean hasToken(final int user) {
		final String t = System.getProperty(TEST_TOKEN_PREFIX + user);
		if (t == null || t.isEmpty()) {
			final String u = System.getProperty(TEST_USER_PREFIX + user);
			final String p = System.getProperty(TEST_PWD_PREFIX + user);
			if (u == null || u.isEmpty()) {
				throw new TestException(String.format(
						"Neither %s or %s are set in the test configuration",
						TEST_TOKEN_PREFIX + user, TEST_USER_PREFIX + user));
			}
			if (p == null || p.isEmpty()) {
				throw new TestException(String.format(
						"%s is not set in the test configuration",
						TEST_PWD_PREFIX + user));
			}
			return false;
		} else {
			return true;
		}
	}
	
	public static AuthToken getToken(
			final int user,
			final ConfigurableAuthService auth) {
		try {
			if (hasToken(user)) {
				return auth.validateToken(getToken(user));
			} else {
				return auth.login(getUserName(user), getPwd(user)).getToken();
			}
		} catch (AuthException | IOException e) {
			throw new TestException(String.format(
					"Couldn't log in user #%s with %s : %s", user,
					hasToken(user) ? "token" : "username " + getUserName(user),
					e.getMessage()), e);
		}
	}
	
	public static String getToken(final int user) {
		return getProp(TEST_TOKEN_PREFIX + user);
	}
	
	public static String getUserName(final int user) {
		return getProp(TEST_USER_PREFIX + user);
	}
	
	public static String getPwd(final int user) {
		return getProp(TEST_PWD_PREFIX + user);
	}
	
	public static String getPwdNullIfToken(final int user) {
		if (hasToken(user)) {
			return null;
		}
		return getPwd(user);
	}
	
	public static URL getAuthUrl() {
		return getURL(AUTHSERV);
	}
	
	private static URL getURL(String prop) {
		try {
			return new URL(getProp(prop));
		} catch (MalformedURLException e) {
			throw new TestException("Property " + prop + " is not a valid url",
					e);
		}
	}
	
	public static URL getGlobusUrl() {
		return getURL(GLOBUS);
	}
	
	public static String getTempDir() {
		return getProp(TEST_TEMP_DIR);
	}
	
	public static String getMongoExe() {
		return getProp(MONGOEXE);
	}
	
	public static String getShockExe() {
		return getProp(SHOCKEXE);
	}
	
	public static String getShockVersion() {
		return getProp(SHOCKVER);
	}

	public static String getMySQLExe() {
		return getProp(MYSQLEXE);
	}
	
	public static String getMySQLInstallExe() {
		return getProp(MYSQL_INSTALL_EXE);
	}
	
	public static boolean getDeleteTempFiles() {
		return !"true".equals(System.getProperty(KEEP_TEMP_DIR));
	}
	
	public static boolean useWiredTigerEngine() {
		return "true".equals(System.getProperty(MONGO_USE_WIRED_TIGER));
	}
	
	public static void destroyDB(DB db) {
		for (String name: db.getCollectionNames()) {
			if (!name.startsWith("system.")) {
				// dropping collection also drops indexes
				db.getCollection(name).remove(new BasicDBObject());
			}
		}
	}
	
	public static void assertExceptionCorrect(
			final Exception got,
			final Exception expected) {
		assertThat("incorrect exception. trace:\n" +
				ExceptionUtils.getStackTrace(got),
				got.getLocalizedMessage(),
				is(expected.getLocalizedMessage()));
		assertThat("incorrect exception type", got, is(expected.getClass()));
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
}
