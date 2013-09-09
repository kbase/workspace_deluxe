package us.kbase.workspace.database.mongo.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.workspace.database.mongo.GridFSBackend;
import us.kbase.workspace.database.mongo.MD5;
import us.kbase.workspace.database.mongo.TypeData;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreException;
import us.kbase.workspace.test.Common;
import us.kbase.workspace.workspaces.AbsoluteTypeId;
import us.kbase.workspace.workspaces.WorkspaceType;

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
		AbsoluteTypeId wt = new AbsoluteTypeId(new WorkspaceType("foo", "foo"), 1, 0);
		Map<String, Object> subdata = new HashMap<String, Object>(); //subdata not used here
		String data = "this is some data";
		TypeData td = new TypeData(data, wt, 3, subdata);
		MD5 tdmd = new MD5(td.getChksum());
		gfsb.saveBlob(tdmd, td.getData());
		//have to use the same data to get same md5
		wt = new AbsoluteTypeId(new WorkspaceType("foo1", "foo1"), 2, 1);
		TypeData tdr = new TypeData(data, wt, 3, subdata);
		MD5 tdmdr = new MD5(tdr.getChksum());
		String returned = gfsb.getBlob(tdmdr);
		assertEquals("Didn't get same data back from store", returned, data);
		assertTrue("GridFS has no external ID", gfsb.getExternalIdentifier(tdmdr) == null);
//		try {
		gfsb.saveBlob(tdmd, td.getData()); //should be able to save the same thing twice with no error
//			fail("Able to save same document twice");
//		} catch (BlobStoreException wbe) {}
		gfsb.removeBlob(tdmdr);
	}
	
	@Test
	public void getNonExistantBlob() throws Exception {
		AbsoluteTypeId wt = new AbsoluteTypeId(new WorkspaceType("foo", "foo"), 1, 0);
		String data = "this is non-existant data";
		TypeData td = new TypeData(data, wt, 3, new HashMap<String, Object>());
		try {
			gfsb.getBlob(new MD5(td.getChksum()));
			fail("getblob should throw exception");
		} catch (BlobStoreException wbe) {
			assertThat("wrong exception message from failed getblob",
					wbe.getLocalizedMessage(), is("Attempt to retrieve non-existant blob with chksum 99e25aec48da90bd349b114451314286"));
		}
	}
	
	@Test
	public void removeNonExistantBlob() throws Exception {
		AbsoluteTypeId wt = new AbsoluteTypeId(new WorkspaceType("foo", "foo"), 1, 0);
		String data = "this is also non-existant data";
		TypeData td = new TypeData(data, wt, 3, new HashMap<String, Object>());
		gfsb.removeBlob(new MD5(td.getChksum())); //should silently not remove anything
	}
}
