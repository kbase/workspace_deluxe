package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DB;

import us.kbase.common.test.TestException;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.Writable;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.mongo.ShockBackend;
import us.kbase.workspace.database.mongo.TypeData;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;
import us.kbase.workspace.test.WorkspaceTestCommon;

public class ShockBackendTest {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	private static ShockBackend sb;
	
	private static final Pattern UUID =
			Pattern.compile("[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}");

	@BeforeClass
	public static void setUpClass() throws Exception {
		String u1 = System.getProperty("test.user1");
		String p1 = System.getProperty("test.pwd1");
		final DB mongo = WorkspaceTestCommon.destroyAndSetupDB(1, "shock", u1);
		URL url = new URL(System.getProperty("test.shock.url"));
		System.out.println("Testing workspace shock backend pointed at: " + url);
		try {
			sb = new ShockBackend(mongo, "shock_", url, u1, p1);
		} catch (BlobStoreAuthorizationException bsae) {
			throw new TestException("Unable to login with test.user1: " + u1 +
					"\nPlease check the credentials in the test configuration.", bsae);
		}
	}
	
	@Test
	public void saveAndGetBlob() throws Exception {
		String mod = "moduleA";
		String type = "typeA";
		int majorver = 0;
		int minorver = 1;
		AbsoluteTypeDefId wt = new AbsoluteTypeDefId(new TypeDefName(mod, type), majorver, minorver);
		Map<String, Object> subdata = new HashMap<String,Object>(); //subdata not used here
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("key", "value");
		TypeData td = new TypeData(valueToTree(data), wt, subdata);
		MD5 tdmd = new MD5(td.getChksum());
		sb.saveBlob(tdmd, td.getData());
		ShockNodeId id = new ShockNodeId(sb.getExternalIdentifier(tdmd));
		assertTrue("Got a valid shock id",
				UUID.matcher(id.getId()).matches());
		assertThat("Ext id is the shock node", id.getId(),
				is(sb.getExternalIdentifier(tdmd)));
		TypeData faketd = new TypeData(valueToTree(data), wt, subdata); //use same data to get same chksum
		MD5 tdfakemd = new MD5(faketd.getChksum());
		@SuppressWarnings("unchecked")
		Map<String, Object> ret = MAPPER.treeToValue(sb.getBlob(tdfakemd, 
				ByteArrayFileCacheManager.forTests()).getAsJsonNode(), Map.class);
		assertThat("Shock data returned correctly", ret, is(data));
		sb.removeBlob(tdfakemd);
		try {
			sb.getBlob(tdfakemd, ByteArrayFileCacheManager.forTests());
			fail("Got non-existant blob");
		} catch (NoSuchBlobException nb) {
			assertThat("correct exception msg", nb.getLocalizedMessage(),
					is("No blob saved with chksum a7353f7cddce808de0032747a0b7be50"));
		}
		Map<String, Object> baddata = new HashMap<String, Object>();
		data.put("keyfoo", "value");
		TypeData badtd = new TypeData(valueToTree(baddata), wt, subdata);
		try {
			sb.getBlob(new MD5(badtd.getChksum()), ByteArrayFileCacheManager.forTests());
			fail("Got non-existant blob");
		} catch (NoSuchBlobException nb) {
			assertThat("correct exception msg", nb.getLocalizedMessage(),
					is("No blob saved with chksum 99914b932bd37a50b983c5e7c90ae93b"));
		}
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
