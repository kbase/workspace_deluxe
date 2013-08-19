package us.kbase.workspace.database.test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.DB;
import com.mongodb.MongoClient;

import us.kbase.workspace.database.GridFSBackend;
import us.kbase.workspace.database.TypeData;
import us.kbase.workspace.database.WorkspaceType;
import us.kbase.workspace.database.exceptions.WorkspaceBackendException;

public class GridFSBackendTest {
	
	public static final String USER = "test.mongo.user";
	public static final String PWD = "test.mongo.pwd";
	private static GridFSBackend gfsb;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
		String host = System.getProperty("test.mongo.host");
		String db = System.getProperty("test.mongo.db");
		String user = System.getProperty(USER);
		String pwd = System.getProperty(PWD);
		if (user.equals("")) {
			user = null;
		}
		if (pwd.equals("")) {
			pwd = null;
		}
		if (user == null ^ pwd == null) {
			System.err.println(String.format("Must provide both %s and %s ",
					USER, PWD) + "params for testing if authentication " + 
					"is to be used");
			System.exit(1);
		}
		
		System.out.println("Testing workspace GridFS backend at: " + host +
				", DB: " + db);
		System.out.print("Auth params are user: " + user + " pwd: ");
		if (pwd != null && pwd.length() > 0) {
			System.out.println("[redacted]");
		} else {
			System.out.println(pwd);
		}
		DB mdb = new MongoClient(host).getDB(db);
		if (user != null) {
			mdb.authenticate(user, pwd.toCharArray());
		}
		gfsb = new GridFSBackend(new MongoClient(host).getDB(db));
	}
	
	@Test
	public void saveAndGetBlob() throws Exception {
		WorkspaceType wt = new WorkspaceType("foo", "foo", "foo", 0);
		List<String> workspaces = new ArrayList<String>();
		workspaces.add("workspace1");
		workspaces.add("workspace2");
		Map<String, Object> subdata = new HashMap<String, Object>(); //subdata not used here
		String data = "this is some data";
		TypeData td = new TypeData(data, wt, workspaces, subdata);
		gfsb.saveBlob(td);
		//have to use the same data to get same md5
		wt = new WorkspaceType("foo1", "foo1", "foo1", 1);
		TypeData tdr = new TypeData(data, wt, new ArrayList<String>(), subdata);
		String returned = gfsb.getBlob(tdr);
		assertEquals("Didn't get same data back from store", returned, data);
		assertTrue("GridFS has no external ID", gfsb.getExternalIdentifier(tdr) == null);
		try {
			gfsb.saveBlob(td);
			fail("Able to save same document twice");
		} catch (WorkspaceBackendException wbe) {}
		gfsb.removeBlob(tdr);
	}
}
