package us.kbase.workspace.test;

import java.net.URL;

import us.kbase.common.test.TestException;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;

public class WorkspaceTestCommon {
	
	public static final String SHOCKEXE = "test.shock.exe";
	public static final String MONGOEXE = "test.mongo.exe";
	public static final String MONGO_USE_WIRED_TIGER = "test.mongo.useWiredTiger";
	public static final String MYSQLEXE = "test.mysql.exe";
	public static final String MYSQL_INSTALL_EXE = "test.mysql.install.exe";
	public static final String PLACKUPEXE = "test.plackup.exe";
	public static final String HANDLE_SRV_PSGI = "test.handle.service.psgi";
	public static final String HANDLE_MGR_PSGI = "test.handle.manager.psgi";
	public static final String HANDLE_PERL5LIB = "test.handle.PERL5LIB";
	
	public static final String TEST_TEMP_DIR = "test.temp.dir";
	public static final String KEEP_TEMP_DIR = "test.temp.dir.keep";
	public static final String GRIDFS = "gridFS";
	public static final String SHOCK = "shock";
			
	public static void stfuLoggers() {
		((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
				.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
			.setLevel(ch.qos.logback.classic.Level.OFF);
		java.util.logging.Logger.getLogger("com.mongodb")
			.setLevel(java.util.logging.Level.OFF);
	}
	
	public static void printJava() {
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
	}
	
	private static String getProp(String prop) {
		if (System.getProperty(prop) == null || prop.isEmpty()) {
			throw new TestException("Property " + prop + " cannot be null or the empty string.");
		}
		return System.getProperty(prop);
	}
	
	public static String getTempDir() {
		return getProp(TEST_TEMP_DIR);
	}
	
	public static String getMongoExe() {
		return getProp(MONGOEXE);
	}
	
	public static String getKidlSource() {
		return "both";
	}
	
	public static String getShockExe() {
		return getProp(SHOCKEXE);
	}

	public static String getMySQLExe() {
		return getProp(MYSQLEXE);
	}
	
	public static String getMySQLInstallExe() {
		return getProp(MYSQL_INSTALL_EXE);
	}
	
	public static String getPlackupExe() {
		return getProp(PLACKUPEXE);
	}
	
	public static String getHandleServicePSGI() {
		return getProp(HANDLE_SRV_PSGI);
	}
	
	public static String getHandleManagerPSGI() {
		return getProp(HANDLE_MGR_PSGI);
	}
	
	public static String getHandlePERL5LIB() {
		return getProp(HANDLE_PERL5LIB);
	}
	
	public static boolean deleteTempFiles() {
		return !"true".equals(System.getProperty(KEEP_TEMP_DIR));
	}
	
	public static boolean useWiredTigerEngine() {
		return "true".equals(System.getProperty(MONGO_USE_WIRED_TIGER));
	}
	
	//useful for tests starting a server with GFS as the backend
	public static void initializeGridFSWorkspaceDB(DB mdb, String typedb) {
		destroyWSandTypeDBs(mdb, typedb);
		DBObject dbo = new BasicDBObject();
		dbo.put("type_db", typedb);
		dbo.put("backend", GRIDFS);
		mdb.getCollection("settings").insert(dbo);
		System.out.println(String.format("Configured new %s backend.", GRIDFS));
	}

	public static void destroyWSandTypeDBs(DB mdb, String typedb) {
		destroyDB(mdb);
		destroyDB(mdb.getSisterDB(typedb));
	}
	
	public static void destroyDB(DB db) {
		for (String name: db.getCollectionNames()) {
			if (!name.startsWith("system.")) {
				// dropping collection also drops indexes
				db.getCollection(name).remove(new BasicDBObject());
			}
		}
	}
	
	//useful for tests starting a server with shock as the backend
	public static void initializeShockWorkspaceDB(DB mdb, String shockuser,
			URL shockURL, String typedb) {
		destroyWSandTypeDBs(mdb, typedb);
		DBObject dbo = new BasicDBObject();
		dbo.put("type_db", typedb);
		dbo.put("backend", SHOCK);
		if (shockuser == null) {
			throw new TestException("Shock user cannot be null");
		}
		dbo.put("shock_user", shockuser);
		if (shockURL == null) {
			throw new TestException("The shock url may not be null");
		}
		dbo.put("shock_location", shockURL.toExternalForm());
		System.out.println(String.format(
				"Setting up shock with user %s and url %s", shockuser,
				shockURL.toExternalForm()));
	mdb.getCollection("settings").insert(dbo);
		System.out.println(String.format("Configured new %s backend.", SHOCK));
	}

}
