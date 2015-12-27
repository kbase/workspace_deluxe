package us.kbase.workspace.test.docserver;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.workspace.docserver.DocServer;
import us.kbase.workspace.test.WorkspaceTestCommon;

public class DocServerTest {
	
	//TODO DS test logging
	
	private static DocServer server;
	private static URL docURL;

	@BeforeClass
	public static void setUpClass() throws Exception {
		WorkspaceTestCommon.stfuLoggers();
		Files.createDirectories(Paths.get(WorkspaceTestCommon.getTempDir())
				.toAbsolutePath());
		File iniFile = File.createTempFile("test", ".cfg",
				new File(WorkspaceTestCommon.getTempDir()));
		Ini ini = new Ini();
		Section ws = ini.add("Workspace");
		ws.add("doc-server-name", "TestDocServer");
		ws.add("doc-server-docs-location",
				"/us/kbase/workspace/test/docserver");
		ini.store(iniFile);
		iniFile.deleteOnExit();
		System.out.println("Created temporary config file: " +
				iniFile.getAbsolutePath());
		
		//set up env
		Map<String, String> env = getenv();
		env.put("KB_DEPLOYMENT_CONFIG", iniFile.getAbsolutePath());
		env.put("KB_SERVICE_NAME", "Workspace");
		
		server = new DocServer();
		new ServerThread(server).start();
		while (server.getServerPort() == null) {
			Thread.sleep(1000);
		}
		docURL = new URL("http://localhost:" + server.getServerPort() +
				"/docs");
		System.out.println("Started doc server at " + docURL);
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (server != null) {
			server.stopServer();
		}
	}
	
	//TODO put this in test utils in common, used all over
	//http://quirkygba.blogspot.com/2009/11/setting-environment-variables-in-java.html
	@SuppressWarnings("unchecked")
	protected static Map<String, String> getenv() throws NoSuchFieldException,
	SecurityException, IllegalArgumentException, IllegalAccessException {
		Map<String, String> unmodifiable = System.getenv();
		Class<?> cu = unmodifiable.getClass();
		Field m = cu.getDeclaredField("m");
		m.setAccessible(true);
		return (Map<String, String>) m.get(unmodifiable);
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
	
	@Test
	public void getIndex() throws Exception {
		CloseableHttpClient client = HttpClients.createDefault();
		CloseableHttpResponse res = client.execute(new HttpGet(docURL + "/"));
		System.out.println(res.getStatusLine());
		System.out.println(EntityUtils.toString(res.getEntity()));
		//TODO DS actually test results and stuff
	}
	
}
