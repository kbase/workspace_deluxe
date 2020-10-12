package us.kbase.common.test.service;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.common.service.ServiceChecker;
import us.kbase.common.service.ServiceChecker.ServiceException;

public class ServiceCheckerTest {
	
	private final static String KBASE_ENV = "https://ci.kbase.us"; // "https://kbase.us/"

	public static class TestSpec {
		public final String err;
		public final URL url;
		private TestSpec(String err, String url) throws MalformedURLException {
			super();
			this.err = err;
			this.url = new URL(url);
		}
	}
	
	public static Map<String, TestSpec> TESTS = new HashMap<>();
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		TESTS.put("Perl", new TestSpec(
				null, KBASE_ENV + "/services/handle_service"));
		TESTS.put("Python", new TestSpec(
				null, KBASE_ENV + "/services/catalog"));
		TESTS.put("Java", new TestSpec(
				null, KBASE_ENV + "/services/ws"));
		TESTS.put("HTTP", new TestSpec(
				null, "http://dev03.berkeley.kbase.us:7058"));
		TESTS.put("500", new TestSpec(
				"Could not contact the service at URL http://the-internet." +
				"herokuapp.com/status_codes/500: Unexpected character ('<' " +
				"(code 60)):",
				"http://the-internet.herokuapp.com/status_codes/500"));
		TESTS.put("200", new TestSpec(
				"URL http://google.com does not point to a KBase SDK " +
				"generated service. Code: 200, message: OK, content: " +
				"<!doctype html><html itemscope=",
				"http://google.com"));
		TESTS.put("short200", new TestSpec(
				"URL " + KBASE_ENV + "/services/shock-api does not point " +
				"to a KBase SDK generated service. Code: 200, message: OK, " +
				"content: {",
				KBASE_ENV + "/services/shock-api"));
		
	}
	
	@Test
	public void testPerl() throws Exception {
		test();
	}
	
	@Test
	public void testPython() throws Exception {
		test();
	}
	
	@Test
	public void testJava() throws Exception {
		test();
	}
	
	// no longer any http SDK services available
//	@Test
//	public void testHTTP() throws Exception {
//		test();
//	}
	
	@Test
	public void test500() throws Exception {
		test();
	}
	
	@Test
	public void test200() throws Exception {
		test();
	}
	
	@Test
	public void short200() throws Exception {
		test();
	}
	
	public static void test() throws Exception {
		final Exception e = new Exception();
		e.fillInStackTrace();
		final String testMethod = e.getStackTrace()[1].getMethodName()
				.replace("test", "");
		final TestSpec ts = TESTS.get(testMethod);
		if (ts.err == null) {
			//should work
			ServiceChecker.checkService(ts.url);
		} else {
			try {
				ServiceChecker.checkService(ts.url);
				fail("expected failure");
			} catch (ServiceException se) {
				assertThat("incorrect exception: " + se.getMessage(),
						se.getMessage().startsWith(ts.err), is(true));
			}
		}
	}
}
