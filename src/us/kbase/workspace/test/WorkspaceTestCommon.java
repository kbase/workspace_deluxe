package us.kbase.workspace.test;


import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.test.TestException;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoException;

public class WorkspaceTestCommon {
	
	public static final String DB1 = "test.mongo.db1";
	public static final String DB2 = "test.mongo.db2";
	public static final String TYPEDB1 = "test.mongo.db.types1";
	public static final String TYPEDB2 = "test.mongo.db.types2";
	public static final String HOST = "test.mongo.host";
	public static final String M_USER = "test.mongo.user";
	public static final String M_PWD = "test.mongo.pwd";
	public static final String SHOCKURL = "test.shock.url";
	public static final String SHOCKEXE = "test.shock.exe";
	public static final String SHOCKDB = "test.shock.db";
	public static final String GRIDFS = "gridFS";
	public static final String SHOCK = "shock";
			
	private static MongoClient mongoClient = null;
	
	public static void printJava() {
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
	}
	
	private static String getProp(String prop) {
		if (System.getProperty(prop) == null || prop.isEmpty()) {
			throw new TestException("Property " + prop + " cannot be null or the empty string.");
		}
		return System.getProperty(prop);
	}
	
	public static String getHost() {
		return getProp(HOST);
	}
	
	public static String getMongoUser() {
		return System.getProperty(M_USER);
	}
	
	public static String getMongoPwd() {
		return System.getProperty(M_PWD);
	}
	
	public static String getDB1() {
		return getProp(DB1);
	}
	
	public static String getDB2() {
		return getProp(DB2);
	}
	
	public static String getTypeDB1() {
		return getProp(TYPEDB1);
	}
	public static String getTypeDB2() {
		return getProp(TYPEDB2);
	}
	
	public static String getShockUrl() {
		return getProp(SHOCKURL);
	}
	
	public static String getKidlSource() {
		return "both";
	}
	
	public static String getShockExe() {
		return getProp(SHOCKEXE);
	}

	public static String getShockDB() {
		return getProp(SHOCKDB);
	}
	
	private static void buildMongo() throws UnknownHostException,
			InvalidHostException, TestException {
		if (mongoClient != null) {
			return;
		}
		printJava();
		String host = getHost();
		String mUser = getMongoUser();
		String mPwd = getMongoPwd();
		if (mUser == null || mUser.equals("")) {
			mUser = null;
		}
		if (mPwd == null || mPwd.equals("")) {
			mPwd = null;
		}
		if (mUser == null ^ mPwd == null) {
			throw new TestException(String.format("Must provide both %s and %s ",
					M_USER, M_PWD) + "params for testing if authentication " + 
					"is to be used");
		}
		System.out.print("Mongo auth params are user: " + mUser + " pwd: ");
		if (mPwd != null && mPwd.length() > 0) {
			System.out.println("[redacted]");
		} else {
			System.out.println(mPwd);
		}
		if (mongoClient == null) {
			Logger.getLogger("com.mongodb").setLevel(Level.OFF);
			final MongoClientOptions opts = MongoClientOptions.builder()
					.autoConnectRetry(true).build();
			try {
				mongoClient = new MongoClient(host, opts);
			} catch (NumberFormatException nfe) {
				throw new InvalidHostException(host
						+ " is not a valid mongodb host");
			}
		}
		System.out.println("Created static mongo client pointed at: " + host);
	}
	
	//run this method first, lots of error checking
	public static DB destroyAndSetupDB(int num, String type, String shockuser,
			URL shockURL)
			throws InvalidHostException, UnknownHostException, TestException {
		String db = num == 1 ? getDB1() : getDB2();
		String typedb = num == 1 ? getTypeDB1() : getTypeDB2();
		if (db == null) {
			throw new TestException("The property " + (num == 1 ? DB1 : DB2) +
					" is not set.");
		}
		if (typedb == null) {
			throw new TestException("The property " + (num == 1 ? TYPEDB1 :
					TYPEDB2) + " is not set.");
		}
		destroyAndSetupDB(typedb);
		DB mdb = destroyAndSetupDB(db);
		System.out.println(" buhbye.");
		
		DBObject dbo = new BasicDBObject();
		dbo.put("type_db", typedb);
		if (type == GRIDFS) {
			dbo.put("backend", GRIDFS);
		}
		if (type == SHOCK) {
			dbo.put("backend", SHOCK);
			if (shockuser == null) {
				throw new TestException("Shock user cannot be null");
			}
			dbo.put("shock_user", shockuser);
			URL sh;
			
			if (shockURL != null) {
				sh = shockURL;
			} else {
				try {
					sh = new URL(getShockUrl());
				} catch (MalformedURLException mue) {
					throw new TestException("Bad shock url:" +
							getShockUrl());
				}
			}
			dbo.put("shock_location", sh.toExternalForm());
			System.out.println(String.format(
					"Setting up shock with user %s and url %s", shockuser,
					sh.toExternalForm()));
		}
		mdb.getCollection("settings").insert(dbo);
		System.out.println(String.format("Configured new %s backend.", type));
		return mdb;
	}

	public static DB destroyAndSetupShockDB()
			throws InvalidHostException, UnknownHostException, TestException {
		String db = getShockDB();
		if (db == null) {
			throw new TestException("The property " + SHOCKDB + " is not set.");
		}
		return destroyAndSetupDB(db);
	}

	private static DB destroyAndSetupDB(String db) throws UnknownHostException,
			InvalidHostException {
		buildMongo();
		String mUser = getMongoUser();
		String mPwd = getMongoPwd();
		System.out.print(String.format("Destroying mongo database %s at %s...",
				db, getHost()));
		DB mdb;
		try {
			mdb = mongoClient.getDB(db);
			if (mUser != null) {
				mdb.authenticate(mUser, mPwd.toCharArray());
			}
		} catch (MongoException.Network men) {
			throw new TestException("Error connecting to mongodb test instance: "
					+ men.getCause().getLocalizedMessage());
		}
		try {
			for (String name: mdb.getCollectionNames()) {
				if (!name.startsWith("system.")) {
					mdb.getCollection(name).drop();
				}
			}
		} catch (MongoException me) {
			throw new TestException("\nCould not delete the database. Please grant " + 
					"read/write access to the database or correct the credentials:\n" +
					me.getLocalizedMessage());
		}
		System.out.println(" buhbye.");
		return mdb;
	}
}
