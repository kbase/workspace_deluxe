package us.kbase.workspace.test;

import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.mongo.MongoDatabase;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

//TODO test vs. auth'd mongo
public class TestWorkspaces {

	public static final String M_USER = "test.mongo.user";
	public static final String M_PWD = "test.mongo.pwd";
	public static Database[] TEST_DATABASES = new Database[2];
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
		String host = System.getProperty("test.mongo.host");
		String db1 = System.getProperty("test.mongo.db");
		String db2 = System.getProperty("test.mongo.db2");
		String mUser = System.getProperty(M_USER);
		String mPwd = System.getProperty(M_PWD);
		String shockurl = System.getProperty("test.shock.url");
		String shockuser = System.getProperty("test.user.noemail");
		String shockpwd = System.getProperty("test.pwd.noemail");
		
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
		//Set up mongo backend database
		DB mdb = new MongoClient(host).getDB(db1);
		if (mUser != null) {
			mdb.authenticate(mUser, mPwd.toCharArray());
		}
		DBObject dbo = new BasicDBObject();
		mdb.getCollection("settings").remove(dbo);
		dbo.put("backend", "gridFS");
		mdb.getCollection("settings").insert(dbo);
		Database db = new MongoDatabase(host, db1, shockpwd, mUser, mPwd);
		if (mUser != null) {
			TEST_DATABASES[0] = new MongoDatabase(host, db1, shockpwd, mUser, mPwd);
		} else {
			TEST_DATABASES[0] = new MongoDatabase(host, db1, shockpwd);
		}
		assertTrue("GridFS backend setup failed", TEST_DATABASES[0].getBackendType().equals("GridFS"));
		
		//Set up shock backend database
		mdb = new MongoClient(host).getDB(db2);
		if (mUser != null) {
			mdb.authenticate(mUser, mPwd.toCharArray());
		}
		dbo = new BasicDBObject();
		mdb.getCollection("settings").remove(dbo);
		dbo.put("backend", "shock");
		dbo.put("shock_user", shockuser);
		dbo.put("shock_location", shockurl);
		mdb.getCollection("settings").insert(dbo);
		if (mUser != null) {
			TEST_DATABASES[1] = new MongoDatabase(host, db2, shockpwd, mUser, mPwd);
		} else {
			TEST_DATABASES[1] = new MongoDatabase(host, db2, shockpwd);
		}
		assertTrue("Shock backend setup failed", TEST_DATABASES[1].getBackendType().equals("Shock"));
	}
	
	@Test
	public void fatDumbAndHappy() {}
}
