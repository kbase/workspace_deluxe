package us.kbase.workspace.database.test;

import static org.junit.Assert.assertTrue;

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
import us.kbase.shock.client.ShockNodeId;
import us.kbase.workspace.database.ShockBackend;
import us.kbase.workspace.database.TypeData;
import us.kbase.workspace.database.WorkspaceType;

public class ShockBackendTest {
	
	private static ShockBackend sb;
	private static BasicShockClient bsc;
	
	private static final Pattern UUID =
			Pattern.compile("[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}");

	@BeforeClass
	public static void setUpClass() throws Exception {
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
		URL url = new URL(System.getProperty("test.shock.url"));
		System.out.println("Testing shock clients pointed at: " + url);
		String u1 = System.getProperty("test.user1");
		String p1 = System.getProperty("test.pwd1");
		bsc = new BasicShockClient(url, AuthService.login(u1, p1).getToken());
		sb = new ShockBackend(url, u1, p1);
	}
	
	@Test
	public void saveBlob() throws Exception {
		WorkspaceType wt = new WorkspaceType("foo", "moduleA", "typeA", 0);
		List<String> workspaces = new ArrayList<>();
		workspaces.add("workspace1");
		workspaces.add("workspace2");
		Map<String, Object> subdata = new HashMap<>(); //subdata not used here
		TypeData td = new TypeData("this is some data", wt, workspaces, subdata);
		sb.saveBlob(td);
		System.out.println(td);
		assertTrue("Got a valid shock id",
				UUID.matcher(td.getShockNodeId()).matches());
		assertTrue("Got a valid shock version",
				UUID.matcher(td.getShockNodeId()).matches());
		//TODO verify type saved correctly
		bsc.deleteNode(new ShockNodeId(td.getShockNodeId()));
	}
}
