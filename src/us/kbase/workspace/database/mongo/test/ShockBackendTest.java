package us.kbase.workspace.database.mongo.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.DB;

import us.kbase.auth.AuthService;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.workspace.database.mongo.ShockBackend;
import us.kbase.workspace.database.mongo.TypeData;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;
import us.kbase.workspace.test.Common;
import us.kbase.workspace.workspaces.AbsoluteTypeId;
import us.kbase.workspace.workspaces.WorkspaceType;

public class ShockBackendTest {
	
	private static ShockBackend sb;
//	private static BasicShockClient bsc;
	
	private static final Pattern UUID =
			Pattern.compile("[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}");
//	private static final Pattern MD5 = Pattern.compile("[\\da-f]{32}");

	@BeforeClass
	public static void setUpClass() throws Exception {
		String u1 = System.getProperty("test.user1");
		String p1 = System.getProperty("test.pwd1");
		final DB mongo = Common.destroyAndSetupDB(1, "shock", u1);
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
		URL url = new URL(System.getProperty("test.shock.url"));
		System.out.println("Testing workspace shock backend pointed at: " + url);
//		bsc = new BasicShockClient(url, AuthService.login(u1, p1).getToken());
		sb = new ShockBackend(mongo.getCollection("shockData"), url, u1, p1);
	}
	
	@Test
	public void saveAndGetBlob() throws Exception {
		String mod = "moduleA";
		String type = "typeA";
		int majorver = 0;
		int minorver = 1;
		AbsoluteTypeId wt = new AbsoluteTypeId(new WorkspaceType(mod, type), majorver, minorver);
		Map<String, Object> subdata = new HashMap<>(); //subdata not used here
		String data = "this is some data";
		TypeData td = new TypeData(data, wt, 3, subdata);
		sb.saveBlob(td);
		ShockNodeId id = new ShockNodeId(sb.getExternalIdentifier(td));
		assertTrue("Got a valid shock id",
				UUID.matcher(id.getId()).matches());
//		assertTrue("Got a valid shock version",
//				MD5.matcher(td.getShockVersion().getVersion()).matches());
		assertThat("Ext id is the shock node", id.getId(),
				is(sb.getExternalIdentifier(td)));
//		ShockNode sn = bsc.getNode(id);
//		@SuppressWarnings("unchecked")
//		Map<String, Object> attribs = (Map<String, Object>)
//				sn.getAttributes().get("workspace");
//		assertThat("Type module saved correctly", mod,
//				is(attribs.get("module")));
//		assertThat("Type type saved correctly", type,
//				is(attribs.get("type")));
//		assertThat("Type major version saved correctly", majorver,
//				is(attribs.get("major-version")));
//		assertThat("Type minor version saved correctly", minorver,
//				is(attribs.get("minor-version")));
		TypeData faketd = new TypeData(data, wt, 3, subdata); //use same data to get same chksum
//		faketd.addShockInformation(sn);
		assertThat("Shock data returned correctly", data, is(sb.getBlob(faketd)));
		sb.removeBlob(faketd);
		try {
			sb.getBlob(faketd);
			fail("Got non-existant blob");
		} catch (NoSuchBlobException nb) {
			assertThat("correct exception msg", nb.getLocalizedMessage(),
					is("No blob saved with chksum 1463f25d10e363181d686d2484a9eab6"));
		}
		TypeData badtd = new TypeData("nosuchdata", wt, 3, subdata);
		try {
			sb.getBlob(badtd);
			fail("Got non-existant blob");
		} catch (NoSuchBlobException nb) {
			assertThat("correct exception msg", nb.getLocalizedMessage(),
					is("No blob saved with chksum 34626e65760b5b0bb9be303ac6520642"));
		}
//		try {
//			sb.removeBlob(faketd);
//			fail("Able to remove non-existent blob");
//		} catch (BlobStoreException wbe) {}
//		//TODO WAIT DEP better error handling when shock allows it
	}
}
