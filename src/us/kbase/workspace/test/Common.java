package us.kbase.workspace.test;


import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import us.kbase.workspace.database.exceptions.InvalidHostException;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;

public class Common {
	
	public static final String DB1 = "test.mongo.db1";
	public static final String DB2 = "test.mongo.db2";
	public static final String HOST = "test.mongo.host";
	public static final String M_USER = "test.mongo.user";
	public static final String M_PWD = "test.mongo.pwd";
	public static final String SHOCKURL = "test.shock.url";
	public static final String GRIDFS = "gridFS";
	public static final String SHOCK = "shock";
	public static final List<String> COLLECTIONS = Arrays.asList(
			"settings", "workspaces", "workspaceACLs", "workspaceCounter");
			
	private static MongoClient mongoClient = null;
	
	public static void printJava() {
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
	}
	
	private static String getProp(String prop) {
		if (System.getProperty(prop) == null) {
			throw new TestException("Property " + prop + " cannot be null.");
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
	
	public static String getShockUrl() {
		return getProp(SHOCKURL);
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
		if (mUser.equals("")) {
			mUser = null;
		}
		if (mPwd.equals("")) {
			mPwd = null;
		}
		if (mUser == null ^ mPwd == null) {
			System.err.println(String.format("Must provide both %s and %s ",
					M_USER, M_PWD) + "params for testing if authentication " + 
					"is to be used");
			System.exit(1);
		}
		System.out.print("Mongo auth params are user: " + mUser + " pwd: ");
		if (mPwd != null && mPwd.length() > 0) {
			System.out.println("[redacted]");
		} else {
			System.out.println(mPwd);
		}
		if (mongoClient == null) {
			final MongoClientOptions opts = MongoClientOptions.builder()
					.autoConnectRetry(true).build();
			try {
				mongoClient = new MongoClient(host, opts);
			} catch (NumberFormatException nfe) {
				throw new InvalidHostException(host
						+ " is not a valid mongodb host");
			}
		}
	}
	
//		String shockurl = System.getProperty("test.shock.url");
//		String shockuser = System.getProperty("test.user.noemail");
//		String shockpwd = System.getProperty("test.pwd.noemail");
	
	//run this method first, lots of error checking
	public static DB destroyAndSetupDB(int num, String type, String shockuser)
			throws InvalidHostException, UnknownHostException, TestException {
		buildMongo();
		String dbname = num == 1 ? DB1 : DB2;
		String db = System.getProperty(dbname);
		if (db == null) {
			throw new TestException("The property " + dbname + " is not set.");
		}
		String mUser = getMongoUser();
		String mPwd = getMongoPwd();
		DB mdb = mongoClient.getDB(db);
		if (mUser != null) {
			mdb.authenticate(mUser, mPwd.toCharArray());
		}
		DBObject dbo = new BasicDBObject();
		for (String c: COLLECTIONS) {
			mdb.getCollection(c).remove(dbo);
		}
		if (type == GRIDFS) {
			dbo.put("backend", GRIDFS);
		}
		if (type == SHOCK) {
			dbo.put("backend", SHOCK);
			if (shockuser == null) {
				throw new TestException("Shock user cannot be null");
			}
			dbo.put("shock_user", shockuser);
			dbo.put("shock_location", getShockUrl());
		}
		mdb.getCollection("settings").insert(dbo);
		return mdb;
	}
}
