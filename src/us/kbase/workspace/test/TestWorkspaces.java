package us.kbase.workspace.test;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.mongo.MongoDatabase;
import us.kbase.workspace.workspaces.Workspaces;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

//TODO test vs. auth'd mongo
@RunWith(Parameterized.class)
public class TestWorkspaces {

	public static final String M_USER = "test.mongo.user";
	public static final String M_PWD = "test.mongo.pwd";
	public static Workspaces[] TEST_WORKSPACES = new Workspaces[2];

	@Parameters
	public static Collection<Object[]> generateData() throws Exception {
		setUpWorkspaces();
		return Arrays.asList(new Object[][] {
				{TEST_WORKSPACES[0]},
				{TEST_WORKSPACES[1]}
		});
	}
	
	public final Workspaces ws;
	
	public static void setUpWorkspaces() throws Exception {
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
		Database db = null;
		if (mUser != null) {
			db = new MongoDatabase(host, db1, shockpwd, mUser, mPwd);
		} else {
			db = new MongoDatabase(host, db1, shockpwd);
		}
		TEST_WORKSPACES[0] = new Workspaces(db);
		assertTrue("GridFS backend setup failed", TEST_WORKSPACES[0].getBackendType().equals("GridFS"));
		
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
			db = new MongoDatabase(host, db2, shockpwd, mUser, mPwd);
		} else {
			db = new MongoDatabase(host, db2, shockpwd);
		}
		TEST_WORKSPACES[1] = new Workspaces(db);
		assertTrue("Shock backend setup failed", TEST_WORKSPACES[1].getBackendType().equals("Shock"));
	}
	
	public TestWorkspaces(Workspaces ws) {
		this.ws = ws;
	}
	
	
	@Test
	public void fatDumbAndHappy() {
		System.out.println(ws.getBackendType());
	}
}
