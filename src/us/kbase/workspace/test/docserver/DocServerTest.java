package us.kbase.workspace.test.docserver;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.productivity.java.syslog4j.SyslogIF;

import us.kbase.common.service.JsonServerSyslog;
import us.kbase.common.service.JsonServerSyslog.SyslogOutput;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.docserver.DocServer;

public class DocServerTest {
	
	private static DocServer server;
	private static URL docURL;
	private static SysLogOutputMock logout;
	private static File iniFile;
	
	/* For some reason running tests with a static client works fine in
	 * Eclipse but causes infinite hang in ant, seemingly randomly. 
	 */
//	private static CloseableHttpClient client = HttpClients.createDefault();
	public static CloseableHttpClient getClient() {
		return HttpClients.createDefault();
	}
	
	private static String FILE1_CONTENTS = "<html>\n<body>\nfoo\n</body>\n</html>";
	private static String FILE2_CONTENTS = "<html>\n<body>\nfoo2\n</body>\n</html>";
	private static String IDX1_CONTENTS = "<html>\n<body>\nidx\n</body>\n</html>";
	private static String IDX2_CONTENTS = "<html>\n<body>\nidx2\n</body>\n</html>";
	
	private static String CT_HTML = "text/html";
	private static String CT_HTML_ERR = "text/html;charset=ISO-8859-1";
	
	private static int INFO = JsonServerSyslog.LOG_LEVEL_INFO;
	private static int ERR = JsonServerSyslog.LOG_LEVEL_ERR;

	@BeforeClass
	public static void setUpClass() throws Exception {
		TestCommon.stfuLoggers();
		logout = new SysLogOutputMock();
		DocServer.setLoggerOutput(logout);
		Files.createDirectories(Paths.get(TestCommon.getTempDir())
				.toAbsolutePath());
		
		server = createServer("TestDocServer",
				"/us/kbase/workspace/test/docserver");
		iniFile = new File(TestCommon.getenv().get("KB_DEPLOYMENT_CONFIG"));
		docURL = getServerURL(server);
		System.out.println("Started doc server at " + docURL);
	}

	private static URL getServerURL(DocServer server)
			throws MalformedURLException {
		return new URL("http://localhost:" + server.getServerPort() +
				"/docs");
	}

	private static DocServer createServer(String serverName, String serverDocs)
			throws IOException, NoSuchFieldException, IllegalAccessException,
			InterruptedException {
		File iniFile = File.createTempFile("test", ".cfg",
				new File(TestCommon.getTempDir()));
		Ini ini = new Ini();
		Section ws = ini.add("Workspace");
		if (serverName != null) {
			ws.add("doc-server-name", serverName);
		}
		if (serverDocs != null) {
			ws.add("doc-server-docs-location", serverDocs);
		}
		ini.store(iniFile);
		iniFile.deleteOnExit();
		System.out.println("Created temporary config file: " +
				iniFile.getAbsolutePath());
		
		//set up env
		Map<String, String> env = TestCommon.getenv();
		env.put("KB_DEPLOYMENT_CONFIG", iniFile.getAbsolutePath());
		env.put("KB_SERVICE_NAME", "Workspace");
		
		DocServer serv = new DocServer();
		new ServerThread(serv).start();
		while (serv.getServerPort() == null) {
			Thread.sleep(1000);
		}
		return serv;
	}
	
	private void restoreEnv() throws Exception {
		Map<String, String> env = TestCommon.getenv();
		env.put("KB_DEPLOYMENT_CONFIG", iniFile.getAbsolutePath());
		env.put("KB_SERVICE_NAME", "Workspace");
		DocServer.setDefaultDocsLocation(DocServer.DEFAULT_DOCS_LOC);
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (server != null) {
			server.stopServer();
		}
	}
	
	protected static class ServerThread extends Thread {
		private DocServer server;
		
		protected ServerThread(DocServer server) {
			this.server = server;
		}
		
		public void run() {
			try {
				server.startupServer();
			} catch (Exception e) {
				System.err.println("Can't start server:");
				e.printStackTrace();
			}
		}
	}
	
	private static class LogEvent {
		public int level;
		public String message;
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("LogEvent [level=");
			builder.append(level);
			builder.append(", message=");
			builder.append(message);
			builder.append("]");
			return builder.toString();
		}
	}
	
	private static class SysLogOutputMock extends SyslogOutput {
		
		public List<LogEvent> events = new LinkedList<LogEvent>(); 
		
		@Override
		public void logToSystem(SyslogIF log, int level, String message) {
			LogEvent e = new LogEvent();
			e.level = level;
			e.message = message;
			events.add(e);
		}
		
		@Override
		public PrintWriter logToFile(File f, PrintWriter pw, int level,
				String message) throws Exception {
			throw new UnsupportedOperationException();
		}
		
		public void reset() {
			events.clear();
		}
	}
	
	private static class ExpectedLog {
		
		public int level;
		public String ip;
		public String method;
		public String url;
		public String fullMessage;
		public String serviceName = "TestDocServer";

		public ExpectedLog(int level, String ip, String method) {
			this.level = level;
			this.ip = ip;
			this.method = method;
		}
		
		public ExpectedLog withURL(String url) {
			this.url = url;
			return this;
		}
		
		public ExpectedLog withFullMsg(String msg) {
			this.fullMessage = msg;
			return this;
		}
		
		public ExpectedLog withServiceName(String name) {
			this.serviceName = name;
			return this;
		}
	}
	
	@Before
	public void beforeTest() throws Exception {
		logout.reset();
		restoreEnv();
	}
	
	@Test
	public void serverNameNull() throws Exception {
		checkStartup(null, "DocServ", "/us/kbase/workspace/test/docserver", CT_HTML,
				IDX1_CONTENTS);
	}
	
	@Test
	public void serverNameEmpty() throws Exception {
		checkStartup("", "DocServ", "/us/kbase/workspace/test/docserver", CT_HTML, IDX1_CONTENTS);
	}
	
	@Test
	public void docsLocNull() throws Exception {
		DocServer.setDefaultDocsLocation("/us/kbase/workspace/test/docserver/files");
		checkStartup("WhooptyWoo", "WhooptyWoo", null, CT_HTML, IDX2_CONTENTS);
	}
	
	@Test
	public void docsLocEmpty() throws Exception {
		DocServer.setDefaultDocsLocation("/us/kbase/workspace/test/docserver/files");
		checkStartup("WhooptyWoo", "WhooptyWoo", "", CT_HTML, IDX2_CONTENTS);
	}
	
	@Test
	public void docsLocNoSlash() throws Exception {
		checkStartup("WhooptyWoo", "WhooptyWoo",
				"us/kbase/workspace/test/docserver/files", CT_HTML, IDX2_CONTENTS);
	}

	private void checkStartup(
			final String serverName,
			final String serverNameExp,
			final String serverDocs,
			final String contentType,
			final String body)
			throws Exception {
		DocServer serv = createServer(serverName, serverDocs);
		URL url = getServerURL(serv);
		CloseableHttpResponse res = getClient().execute(new HttpGet(url + "/"));
		checkResponse(res, 200, contentType, body);
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "127.0.0.1", "GET").withURL("/docs/")
						.withServiceName(serverNameExp))); //default server name
		
		serv.stopServer();
	}
	
	@Test
	public void badRead() throws Exception {
		DocServer s = new DocServer();
		Method doGet = s.getClass()
				.getDeclaredMethod("doGet", HttpServletRequest.class,
						HttpServletResponse.class);
		doGet.setAccessible(true);
		HttpServletRequestMock req = new HttpServletRequestMock();
		HttpServletResponseMock res = new HttpServletResponseMock();
		req.setIpAddress("123.123.123.123");
		req.setPathInfo("/");
		req.setRequestURI("/docs/");
		req.setHeader("User-Agent", "Apache-HttpClient/4.3.1 (java 1.5)");
		
		doGet.invoke(s, (HttpServletRequest) req, (HttpServletResponse) res);
		assertThat("correct status code", res.getStatusCode(), is(500));
		checkLogging(Arrays.asList(
				new ExpectedLog(ERR, "123.123.123.123", "GET")
						.withFullMsg("/docs/ 500 Apache-HttpClient/4.3.1 (java 1.5)"),
				new ExpectedLog(ERR, "123.123.123.123", "GET")
						.withFullMsg("java.io.IOException: ow"),
				new ExpectedLog(ERR, "123.123.123.123", "GET")
						.withFullMsg("Traceback (most recent call last):")),
				10);
	}
	
	@Test
	public void options() throws Exception {
		CloseableHttpResponse res = getClient().execute(
				new HttpOptions(docURL + ""));
		assertThat("correct access origin header",
				res.getFirstHeader("Access-Control-Allow-Origin").getValue(),
				is("*"));
		assertThat("correct access headers header",
				res.getFirstHeader("Access-Control-Allow-Headers").getValue(),
				is("authorization"));
		assertThat("correct content type",
				res.getFirstHeader("Content-Type").getValue(),
				is("application/json"));
		assertThat("correct content length",
				res.getFirstHeader("Content-Length").getValue(), is("0"));
		checkResponse(res, 200, "application/json", "");
	}
	
	@Test
	public void forwardHeader() throws Exception {
		HttpGet h = new HttpGet(docURL + "/");
		h.addHeader("X-Forwarded-For", "123.456.789.123,foo,bar");
		CloseableHttpResponse res = getClient().execute(h);
		checkResponse(res, 200, CT_HTML, IDX1_CONTENTS);
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "123.456.789.123", "GET")
						.withFullMsg("X-Forwarded-For: 123.456.789.123,foo,bar"),
				new ExpectedLog(INFO, "123.456.789.123", "GET").withURL("/docs/")));
	}
	
	@Test
	public void forwardHeaderEmpty() throws Exception {
		HttpGet h = new HttpGet(docURL + "/");
		h.addHeader("X-Forwarded-For", "");
		CloseableHttpResponse res = getClient().execute(h);
		checkResponse(res, 200, CT_HTML, IDX1_CONTENTS);
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "127.0.0.1", "GET").withURL("/docs/")));
	}
	
	@Test
	public void getIndexIndirect() throws Exception {
		CloseableHttpResponse res = getClient().execute(new HttpGet(docURL + "/"));
		checkResponse(res, 200, CT_HTML, IDX1_CONTENTS);
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "127.0.0.1", "GET").withURL("/docs/")));
	}
	
	@Test
	public void getIndexDirect() throws Exception {
		CloseableHttpResponse res = getClient().execute(new HttpGet(docURL + "/index.html"));
		checkResponse(res, 200, CT_HTML, IDX1_CONTENTS);
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "127.0.0.1", "GET")
				.withURL("/docs/index.html")));
	}
	
	@Test
	public void getSubIndexIndirect() throws Exception {
		CloseableHttpResponse res = getClient().execute(
				new HttpGet(docURL + "/files/"));
		checkResponse(res, 200, CT_HTML, IDX2_CONTENTS);
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "127.0.0.1", "GET")
				.withURL("/docs/files/")));
	}
	
	@Test
	public void getSubIndexDirect() throws Exception {
		CloseableHttpResponse res = getClient().execute(
				new HttpGet(docURL + "/files/index.html"));
		checkResponse(res, 200, CT_HTML, IDX2_CONTENTS);
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "127.0.0.1", "GET")
				.withURL("/docs/files/index.html")));
	}
	
	@Test
	public void getFile() throws Exception {
		CloseableHttpResponse res = getClient().execute(
				new HttpGet(docURL + "/docserverTestFile.html"));
		checkResponse(res, 200, CT_HTML, FILE1_CONTENTS);
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "127.0.0.1", "GET")
				.withURL("/docs/docserverTestFile.html")));
	}
	
	@Test
	public void getSubFile() throws Exception {
		CloseableHttpResponse res = getClient().execute(
				new HttpGet(docURL + "/files/docserverTestFile2.html"));
		checkResponse(res, 200, CT_HTML, FILE2_CONTENTS);
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "127.0.0.1", "GET")
				.withURL("/docs/files/docserverTestFile2.html")));
	}
	
	@Test
	public void getCSS() throws Exception {
		CloseableHttpResponse res = getClient().execute(new HttpGet(docURL + "/fake.css"));
		checkResponse(res, 200, "text/css", "Not really css");
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "127.0.0.1", "GET").withURL("/docs/fake.css")));
	}
	
	@Test
	public void getGIF() throws Exception {
		CloseableHttpResponse res = getClient().execute(new HttpGet(docURL + "/fake.gif"));
		checkResponse(res, 200, "image/gif", "Not really a gif");
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "127.0.0.1", "GET").withURL("/docs/fake.gif")));
	}
	
	@Test
	public void getJS() throws Exception {
		CloseableHttpResponse res = getClient().execute(new HttpGet(docURL + "/fake.js"));
		checkResponse(res, 200, "text/javascript", "Not really javascript");
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "127.0.0.1", "GET").withURL("/docs/fake.js")));
	}
	
	@Test
	public void getPNG() throws Exception {
		CloseableHttpResponse res = getClient().execute(new HttpGet(docURL + "/fake.png"));
		checkResponse(res, 200, "image/png", "Not really a png");
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "127.0.0.1", "GET").withURL("/docs/fake.png")));
	}
	
	@Test
	public void getSpec() throws Exception {
		CloseableHttpResponse res = getClient().execute(new HttpGet(docURL + "/fake.spec"));
		checkResponse(res, 200, "application/octet-stream", "Not really a spec");
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "127.0.0.1", "GET").withURL("/docs/fake.spec")));
	}
	
	@Test
	public void getTXT() throws Exception {
		CloseableHttpResponse res = getClient().execute(new HttpGet(docURL + "/fake.txt"));
		checkResponse(res, 200, "text/plain", "This really is a text file");
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "127.0.0.1", "GET").withURL("/docs/fake.txt")));
	}
	
	@Test
	public void getWeird() throws Exception {
		CloseableHttpResponse res = getClient().execute(new HttpGet(docURL + "/fake.weirdsuffix"));
		checkResponse(res, 200, "application/octet-stream", "Weird crap in here");
		
		checkLogging(Arrays.asList(
				new ExpectedLog(INFO, "127.0.0.1", "GET").withURL("/docs/fake.weirdsuffix")));
	}
	
	@Test
	public void nullRoot() throws Exception {
		CloseableHttpResponse res = getClient().execute(
				new HttpGet(docURL + ""));
		checkResponse(res, 404, CT_HTML_ERR, null);
		
		checkLogging(Arrays.asList(
				new ExpectedLog(ERR, "127.0.0.1", "GET").withURL("/docs")));
	}
	
	@Test
	public void getNonExtantFile() throws Exception {
		CloseableHttpResponse res = getClient().execute(
				new HttpGet(docURL + "/foo.html"));
		checkResponse(res, 404, CT_HTML_ERR, null);
		
		checkLogging(Arrays.asList(new ExpectedLog(ERR, "127.0.0.1", "GET")
				.withURL("/docs/foo.html")));
	}
	
	private void checkLogging(List<ExpectedLog> expected) throws Exception {
		checkLogging(expected, expected.size());
	}
	
	private void checkLogging(List<ExpectedLog> expected, int eventCount)
			throws Exception {
		assertThat("correct # of logging events", logout.events.size(),
				is(eventCount));
		Iterator<ExpectedLog> i = expected.iterator();
		Iterator<LogEvent> e = logout.events.iterator();
		String callID = null;
		while (i.hasNext()) {
			ExpectedLog exp = i.next();
			LogEvent got = e.next();
			assertThat("correct level", got.level, is(exp.level));
			String call = checkMessage(got.message, exp);
			if (callID == null) {
				callID = call;
			} else {
				assertThat("same call IDs for all calls", call, is(callID));
			}
		}
		
	}
	
	@SuppressWarnings("unused")
	private void printArray(String[] array) {
		for (int i = 0; i < array.length; i++) {
			System.out.println(array[i]);
		}
	}
	
	private void checkResponse(
			final CloseableHttpResponse res,
			final int code,
			final String contentType,
			final String body)
			throws Exception {
		assertThat("correct response code", res.getStatusLine().getStatusCode(), is(code));
		assertThat("incorrect content-type", res.getHeaders("Content-Type")[0].getValue(),
				is(contentType));
		if (code != 404) {
			assertThat("correct response body",
					EntityUtils.toString(res.getEntity()), is(body));
		} else {
			assertThat("body contains 404 message", 
					EntityUtils.toString(res.getEntity())
					.contains("Error 404 Not Found"), is (true));
		}
	}

	private String checkMessage(String message, ExpectedLog exp) {
		String[] parts = message.split(":", 2);
		String[] headerParts = parts[0].split("]\\s*\\[");
		assertThat("server name correct", headerParts[0].substring(1),
				is(exp.serviceName));
		assertThat("record type correct", headerParts[1],
				is(exp.level == INFO ? "INFO" : "ERR"));
		//2 is timestamp
		//3 is user running the service
		assertThat("caller correct", headerParts[4],
				is("us.kbase.workspace.docserver.DocServer"));
		//5 is pid
		assertThat("ip correct", headerParts[6], is(exp.ip));
		assertThat("remote user correct", headerParts[7], is("-"));
		assertThat("module correct", headerParts[8], is("-"));
		assertThat("method correct", headerParts[9], is(exp.method));
		String callID = headerParts[10].substring(
				0, headerParts[10].length() - 1);
		
		
		if (exp.fullMessage != null) {
			assertThat("full message correct", parts[1].trim(),
					is(exp.fullMessage));
		} else {
			String[] msgparts = parts[1].trim().split("\\s+", 3);
//			printArray(msgparts);
			assertThat("URL correct", msgparts[0], is(exp.url));
			assertThat("status code correct", msgparts[1],
					is(exp.level == INFO ? "200" : "404"));
			assertThat("User agent correct", msgparts[2],
					is("Apache-HttpClient/4.3.1 (java 1.5)"));
		}
		return callID;
	}
	
}
