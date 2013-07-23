package us.kbase.shock.client.test;

import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.shock.client.BasicShockClient;

public class ShockTests {
	
	private static BasicShockClient bsc1;
	private static BasicShockClient bsc2;
	private static BasicShockClient bscNoAuth;
	
	@BeforeClass
	public static void setUpClass() {
		String shockurl = System.getProperty("test.shock.url");
		System.out.println(shockurl);
		
	}

	@Test
	public void passing() {}
}
