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

import us.kbase.shock.client.ShockNodeId;
import us.kbase.workspace.database.mongo.MD5;
import us.kbase.workspace.database.mongo.ShockBackend;
import us.kbase.workspace.database.mongo.TypeData;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;
import us.kbase.workspace.test.Common;
import us.kbase.workspace.workspaces.AbsoluteTypeId;
import us.kbase.workspace.workspaces.WorkspaceType;

public class ShockBackendTest {
	
	private static ShockBackend sb;
	
	private static final Pattern UUID =
			Pattern.compile("[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}");

	@BeforeClass
	public static void setUpClass() throws Exception {
		String u1 = System.getProperty("test.user1");
		String p1 = System.getProperty("test.pwd1");
		final DB mongo = Common.destroyAndSetupDB(1, "shock", u1);
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
		URL url = new URL(System.getProperty("test.shock.url"));
		System.out.println("Testing workspace shock backend pointed at: " + url);
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
		MD5 tdmd = new MD5(td.getChksum());
		sb.saveBlob(tdmd, td.getData());
		ShockNodeId id = new ShockNodeId(sb.getExternalIdentifier(tdmd));
		assertTrue("Got a valid shock id",
				UUID.matcher(id.getId()).matches());
		assertThat("Ext id is the shock node", id.getId(),
				is(sb.getExternalIdentifier(tdmd)));
		TypeData faketd = new TypeData(data, wt, 3, subdata); //use same data to get same chksum
		MD5 tdfakemd = new MD5(faketd.getChksum());
		assertThat("Shock data returned correctly", data, is(sb.getBlob(tdfakemd)));
		sb.removeBlob(tdfakemd);
		try {
			sb.getBlob(tdfakemd);
			fail("Got non-existant blob");
		} catch (NoSuchBlobException nb) {
			assertThat("correct exception msg", nb.getLocalizedMessage(),
					is("No blob saved with chksum 1463f25d10e363181d686d2484a9eab6"));
		}
		TypeData badtd = new TypeData("nosuchdata", wt, 3, subdata);
		try {
			sb.getBlob(new MD5(badtd.getChksum()));
			fail("Got non-existant blob");
		} catch (NoSuchBlobException nb) {
			assertThat("correct exception msg", nb.getLocalizedMessage(),
					is("No blob saved with chksum 34626e65760b5b0bb9be303ac6520642"));
		}
	}
}
