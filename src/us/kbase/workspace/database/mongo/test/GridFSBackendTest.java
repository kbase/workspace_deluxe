package us.kbase.workspace.database.mongo.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.workspace.database.exceptions.WorkspaceBackendException;
import us.kbase.workspace.database.mongo.GridFSBackend;
import us.kbase.workspace.database.mongo.TypeData;
import us.kbase.workspace.database.mongo.WorkspaceType;
import us.kbase.workspace.test.Common;

public class GridFSBackendTest {
	
	public static final String USER = "test.mongo.user";
	public static final String PWD = "test.mongo.pwd";
	private static GridFSBackend gfsb;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		gfsb = new GridFSBackend(Common.destroyAndSetupDB(1, "gridFS", null));
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
	
	@Test
	public void getNonExistantBlob() throws Exception {
		WorkspaceType wt = new WorkspaceType("foo", "foo", "foo", 0);
		String data = "this is non-existant data";
		TypeData td = new TypeData(data, wt, new ArrayList<String>(), new HashMap<String, Object>());
		try {
			gfsb.getBlob(td);
			fail("getblob should throw exception");
		} catch (WorkspaceBackendException wbe) {
			assertThat("wrong exception message from failed getblob",
					wbe.getLocalizedMessage(), is("Attempt to retrieve non-existant blob with MD5 99e25aec48da90bd349b114451314286"));
		}
	}
	
	@Test
	public void removeNonExistantBlob() throws Exception {
		WorkspaceType wt = new WorkspaceType("foo", "foo", "foo", 0);
		String data = "this is also non-existant data";
		TypeData td = new TypeData(data, wt, new ArrayList<String>(), new HashMap<String, Object>());
		gfsb.removeBlob(td); //should silently not remove anything
	}
}
