package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.auth.AuthToken;
import us.kbase.workspace.kbase.RefreshingToken;

public class RefreshingTokenTest {
	
	private static String USER;
	private static String PWD;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		USER = System.getProperty("test.user1");
		PWD = System.getProperty("test.pwd1");
	}
	
	@Test
	public void refreshToken() throws Exception {
		RefreshingToken rt = new RefreshingToken(USER, PWD, 5);
		AuthToken t1 = rt.getToken();
		AuthToken t2 = rt.getToken();
		assertThat("got same token immediately", t2.toString(), is(t1.toString()));
		Thread.sleep(2000); //wait 2s
		AuthToken t3 = rt.getToken();
		assertThat("got same token after 2s", t3.toString(), is(t1.toString()));
		Thread.sleep(4000); //wait 4s
		AuthToken t4 = rt.getToken();
		assertTrue("token different after 6s", !t4.toString().equals(t1.toString()));
	}
	
	//TODO bad args tests

}
