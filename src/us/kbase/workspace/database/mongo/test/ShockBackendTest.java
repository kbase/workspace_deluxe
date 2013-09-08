package us.kbase.workspace.database.mongo.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.auth.AuthService;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.workspace.database.exceptions.WorkspaceBackendException;
import us.kbase.workspace.database.mongo.ShockBackend;
import us.kbase.workspace.database.mongo.TypeData;
import us.kbase.workspace.workspaces.AbsoluteTypeId;
import us.kbase.workspace.workspaces.WorkspaceType;

public class ShockBackendTest {
	
	private static ShockBackend sb;
	private static BasicShockClient bsc;
	
	private static final Pattern UUID =
			Pattern.compile("[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}");
	private static final Pattern MD5 = Pattern.compile("[\\da-f]{32}");

	@BeforeClass
	public static void setUpClass() throws Exception {
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
		URL url = new URL(System.getProperty("test.shock.url"));
		System.out.println("Testing workspace shock backend pointed at: " + url);
		String u1 = System.getProperty("test.user1");
		String p1 = System.getProperty("test.pwd1");
		bsc = new BasicShockClient(url, AuthService.login(u1, p1).getToken());
		sb = new ShockBackend(url, u1, p1);
	}
	
	@Test
	public void saveAndGetBlob() throws Exception {
		String mod = "moduleA";
		String type = "typeA";
		int majorver = 0;
		int minorver = 1;
		AbsoluteTypeId wt = new AbsoluteTypeId(new WorkspaceType(mod, type), majorver, minorver);
		List<String> workspaces = new ArrayList<>();
		workspaces.add("workspace1");
		workspaces.add("workspace2");
		Map<String, Object> subdata = new HashMap<>(); //subdata not used here
		String data = "this is some data";
		TypeData td = new TypeData(data, wt, workspaces, subdata);
		sb.saveBlob(td);
		ShockNodeId id = td.getShockNodeId();
		assertTrue("Got a valid shock id",
				UUID.matcher(id.getId()).matches());
		assertTrue("Got a valid shock version",
				MD5.matcher(td.getShockVersion().getVersion()).matches());
		assertThat("Ext id is the shock node", id.getId(),
				is(sb.getExternalIdentifier(td)));
		ShockNode sn = bsc.getNode(id);
		@SuppressWarnings("unchecked")
		Map<String, Object> attribs = (Map<String, Object>)
				sn.getAttributes().get("workspace");
		assertThat("Type module saved correctly", mod,
				is(attribs.get("module")));
		assertThat("Type type saved correctly", type,
				is(attribs.get("type")));
		assertThat("Type major version saved correctly", majorver,
				is(attribs.get("major-version")));
		assertThat("Type minor version saved correctly", minorver,
				is(attribs.get("minor-version")));
		TypeData faketd = new TypeData("foo", wt, workspaces, subdata);
		faketd.addShockInformation(sn);
		assertThat("Shock data returned correctly", data, is(sb.getBlob(faketd)));
		sb.removeBlob(faketd);
		try {
			sb.removeBlob(faketd);
			fail("Able to remove non-existent blob");
		} catch (WorkspaceBackendException wbe) {}
		//TODO WAIT DEP better error handling when shock allows it
	}
}
