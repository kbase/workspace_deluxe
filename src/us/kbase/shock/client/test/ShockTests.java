package us.kbase.shock.client.test;

import java.net.URL;

import junit.framework.Assert;

import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;

public class ShockTests {
	
	private static BasicShockClient bsc1;
	private static BasicShockClient bsc2;
	private static BasicShockClient bscNoAuth;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		URL url = new URL(System.getProperty("test.shock.url"));
		System.out.println("Testing shock clients pointed at: " + url);
		String u1 = System.getProperty("test.user1");
		String u2 = System.getProperty("test.user2");
		String p1 = System.getProperty("test.pwd1");
		String p2 = System.getProperty("test.pwd2");
		AuthToken t1 = AuthService.login(u1, p1).getToken();
		AuthToken t2 = AuthService.login(u2, p2).getToken();
		bsc1 = new BasicShockClient(url, t1);
		bsc2 = new BasicShockClient(url, t2);
		bscNoAuth = new BasicShockClient(url);
	}

	@Test
	public void getNodeBasic() throws Exception {
		ShockNode sn = bsc1.addNode();
		ShockNode snget = bsc1.getNode(sn.getId());
		Assert.assertEquals("get node != add Node output", sn.toString(), snget.toString());
	}
}
