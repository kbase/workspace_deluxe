package us.kbase.common.test;

import us.kbase.common.test.TestException;
import us.kbase.typedobj.core.TempFilesManager;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;

public class TestCommon {
	
	public static final String SHOCKEXE = "test.shock.exe";
	public static final String SHOCKVER = "test.shock.version";
	public static final String MONGOEXE = "test.mongo.exe";
	public static final String MONGO_USE_WIRED_TIGER = "test.mongo.useWiredTiger";
	public static final String MYSQLEXE = "test.mysql.exe";
	public static final String MYSQL_INSTALL_EXE = "test.mysql.install.exe";
	
	public static final String TEST_TEMP_DIR = "test.temp.dir";
	public static final String KEEP_TEMP_DIR = "test.temp.dir.keep";
			
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
	
	private static String getProp(String prop) {
		if (System.getProperty(prop) == null || prop.isEmpty()) {
			throw new TestException("Property " + prop +
					" cannot be null or the empty string.");
		}
		return System.getProperty(prop);
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
	
	public static boolean deleteTempFiles() {
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
	protected static Map<String, String> getenv()
			throws NoSuchFieldException, SecurityException,
			IllegalArgumentException, IllegalAccessException {
		Map<String, String> unmodifiable = System.getenv();
		Class<?> cu = unmodifiable.getClass();
		Field m = cu.getDeclaredField("m");
		m.setAccessible(true);
		return (Map<String, String>) m.get(unmodifiable);
	}
}
