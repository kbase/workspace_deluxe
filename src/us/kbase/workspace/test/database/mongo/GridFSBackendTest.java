package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.Writable;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.mongo.GridFSBackend;
import us.kbase.workspace.database.mongo.TypeData;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreException;
import us.kbase.workspace.test.WorkspaceTestCommon;

public class GridFSBackendTest {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	private static GridFSBackend gfsb;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		gfsb = new GridFSBackend(WorkspaceTestCommon.destroyAndSetupDB(1, "gridFS", null));
	}
	
	@Test
	public void saveAndGetBlob() throws Exception {
		AbsoluteTypeDefId wt = new AbsoluteTypeDefId(new TypeDefName("foo", "foo"), 1, 0);
		Map<String, Object> subdata = new HashMap<String, Object>(); //subdata not used here
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("key", "value");
		TypeData td = new TypeData(valueToTree(data), wt, subdata);
		MD5 tdmd = new MD5(td.getChksum());
		gfsb.saveBlob(tdmd, td.getData());
		//have to use the same data to get same md5
		wt = new AbsoluteTypeDefId(new TypeDefName("foo1", "foo1"), 2, 1);
		TypeData tdr = new TypeData(valueToTree(data), wt, subdata);
		MD5 tdmdr = new MD5(tdr.getChksum());
		@SuppressWarnings("unchecked")
		Map<String, Object> returned = MAPPER.treeToValue(gfsb.getBlob(tdmdr, 
				ByteArrayFileCacheManager.forTests()).getAsJsonNode(), Map.class);
		assertThat("Didn't get same data back from store", returned, is(data));
		assertTrue("GridFS has no external ID", gfsb.getExternalIdentifier(tdmdr) == null);
		gfsb.saveBlob(tdmd, td.getData()); //should be able to save the same thing twice with no error
		gfsb.removeBlob(tdmdr);
	}
	
	@Test
	public void getNonExistantBlob() throws Exception {
		AbsoluteTypeDefId wt = new AbsoluteTypeDefId(new TypeDefName("foo", "foo"), 1, 0);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("no such", "data");
		TypeData td = new TypeData(valueToTree(data), wt, new HashMap<String, Object>());
		try {
			gfsb.getBlob(new MD5(td.getChksum()), ByteArrayFileCacheManager.forTests());
			fail("getblob should throw exception");
		} catch (BlobStoreException wbe) {
			assertThat("wrong exception message from failed getblob",
					wbe.getLocalizedMessage(), is("Attempt to retrieve non-existant blob with chksum 0c961a58424b67d6f1814ee334886e83"));
		}
	}
	
	@Test
	public void removeNonExistantBlob() throws Exception {
		AbsoluteTypeDefId wt = new AbsoluteTypeDefId(new TypeDefName("foo", "foo"), 1, 0);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("no such", "data");
		TypeData td = new TypeData(valueToTree(data), wt, new HashMap<String, Object>());
		gfsb.removeBlob(new MD5(td.getChksum())); //should silently not remove anything
	}
	
	private static Writable valueToTree(final Object value) {
		return new Writable() {
			@Override
			public void write(OutputStream w) throws IOException {
				MAPPER.writeValue(w, value);
			}
			@Override
			public void releaseResources() throws IOException {
			}
		};
	}
}
