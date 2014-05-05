package performance;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.nocrala.tools.texttablefmt.CellStyle;
import org.nocrala.tools.texttablefmt.Table;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.utils.CountingOutputStream;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GetModuleInfoParams;
import us.kbase.workspace.ListModulesParams;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.TypeInfo;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;

/* Simple test that saves a specified object multiple times to the workspace,
 * reads it back, and prints a summary.
 * 
 * DO NOT run these tests on production workspaces.
 * 
 * Eventually you'll need to drop the shock and ws databases, as they'll
 * fill up.
 * 
 * The user for these tests must have admin privs on the workspace and must
 * own the modules tested if the types haven't already been registered and
 * released. Probably still buggy - better to run on a clean type database.
 * 
 */
public class SaveGetObjServerSpeedTest {
	
	public static void main(String[] args) throws Exception {
		String user = args[0];
		String pwd = args[1];
		List<TestSetup> tests = new LinkedList<SaveGetObjServerSpeedTest.TestSetup>();
//		tests.add(new SpecAndObjectFromFile("Genome", 5, new File("test/performance/83333.2.txt"), 
//				new File("test/performance/SupahFakeKBGA.spec"), "SupahFakeKBGA", "Genome"));
//		tests.add(new NoTypeChecking("Genome no TC", 100, new File("test/performance/83333.2.txt")).setSkipWrites(true));
//		tests.add(new SpecAndObjectFromFile("Genome", 100, new File("test/performance/83333.2.txt"), 
//				new File("test/performance/SupahFakeKBGA.spec"), "SupahFakeKBGA", "Genome").setSkipWrites(true));
//		tests.add(new SpecAndObjectFromFile("Genome", 500, new File("test/performance/83333.2.txt"), 
//				new File("test/performance/SupahFakeKBGA.spec"), "SupahFakeKBGA", "Genome"));
//		tests.add(new NoTypeChecking("Genome no TC", 500, new File("test/performance/83333.2.txt")));
//		tests.add(new SpecAndObjectFromFile("Narrative", 500, new File("../../wsTimingData/800_1_47.json"),
//				new File("../../wsTimingData/KBaseNarrative.spec"), "KBaseNarrative", "Narrative"));
		tests.add(new SpecAndObjectFromFile("Contig", 100, new File("../../wsTimingData/188_8_1.json"),
				new File("../../wsTimingData/KBaseGenomes.spec"), "KBaseGenomes", "ContigSet"));
		tests.add(new SpecAndObjectFromFile("Genome", 10, new File("../../wsTimingData/970_2_1.json"),
				new File("../../wsTimingData/KBaseGenomes.spec"), "KBaseGenomes", "Genome"));
		try {
			timeReadWrite(user, pwd, Arrays.asList(new URL("http://localhost:7058"),
					new URL("http://localhost:7059")), tests);
		} catch (ServerException e) {
			e.printStackTrace();
			Thread.sleep(100);
			System.out.println("--");
			System.out.println(e);
			System.out.println(e.getCode());
			System.out.println(e.getData());
		}
	}
	
	public interface TestSetup {
		
		public String getTestName();
		public int getWrites();
		public String getType();
		public Map<String, Object> getObject() throws Exception;
		public String getModule();
		public String getFullTypeName();
		public String getTypeSpec() throws IOException;
		public long getObjectSize() throws Exception;
		public boolean getSkipReads();
		public boolean getSkipWrites();
	}
	
	public static class SpecAndObjectFromFile implements TestSetup {
		
		private static final ObjectMapper MAP = new ObjectMapper(); 
		
		private final String testName;
		private final File file;
		private final String type;
		private final File typespec;
		private final String module;
		private final int writes;
		private long size = -1;
		private boolean skipReads = false;
		private boolean skipWrites = false;
		
		public SpecAndObjectFromFile(String testName, int writes, File object,
				File typespec, String module, String type) {
			this.testName = testName;
			this.writes = writes;
			this.file = object;
			this.type = type;
			this.typespec = typespec;
			this.module = module;
		}
		
		public String getTestName() {
			return testName;
		}
		
		public String getType() {
			return type;
		}
		
		public String getModule() {
			return module;
		}
		
		public String getFullTypeName() {
			return module + "." + type;
		}
		
		public String getTypeSpec() throws IOException {
			return FileUtils.readFileToString(typespec);
		}
		
		public Map<String, Object> getObject() throws Exception {
			@SuppressWarnings("unchecked")
			Map<String, Object> o = MAP.readValue(file, Map.class);
			if (size < 0) {
				size = calcSize(o);
			}
			return o;
		}

		@Override
		public int getWrites() {
			return writes;
		}

		@Override
		public long getObjectSize() throws Exception {
			if (size > -1 ) {
				return size;
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> o = MAP.readValue(file, Map.class);
			size = calcSize(o);
			return size;
		}

		private long calcSize(Map<String, Object> o) throws IOException,
				JsonGenerationException, JsonMappingException {
			CountingOutputStream cos = new CountingOutputStream();
			MAP.writeValue(cos, o);
			return cos.getSize();
		}
		
		public SpecAndObjectFromFile setSkipReads(boolean skip) {
			skipReads = skip;
			return this;
		}
		
		public boolean getSkipReads() {
			return skipReads;
		}
		
		public SpecAndObjectFromFile setSkipWrites(boolean skip) {
			skipWrites = skip;
			return this;
		}
		
		public boolean getSkipWrites() {
			return skipWrites;
		}

		@Override
		public String toString() {
			return "SpecAndObjectFromFile [testName=" + testName + ", file="
					+ file + ", type=" + type + ", typespec=" + typespec
					+ ", module=" + module + ", writes=" + writes + ", size="
					+ size + ", skipReads=" + skipReads + ", skipWrites="
					+ skipWrites + "]";
		}
	}
	
	public static class NoTypeChecking implements TestSetup {
		
		private static final ObjectMapper MAP = new ObjectMapper(); 
		
		private final String testName;
		private final File file;
		private final int writes;
		private long size = -1;
		private boolean skipReads = false;
		private boolean skipWrites = false;
		
		public NoTypeChecking(String testName, int writes, File object) {
			this.testName = testName;
			this.writes = writes;
			this.file = object;
		}
		
		public String getTestName() {
			return testName;
		}
		
		public String getType() {
			return "TestNoTC";
		}
		
		public String getModule() {
			return "TestNoTypeChecking";
		}
		
		public String getFullTypeName() {
			return getModule() + "." + getType();
		}
		
		public String getTypeSpec() throws IOException {
			return 
				"module TestNoTypeChecking {" +
					"/* @optional optionalfield */" +
					"typedef structure {" +
						"string optionalfield;" +
					"} TestNoTC;" +
				"};";
		}
		
		public Map<String, Object> getObject() throws Exception {
			@SuppressWarnings("unchecked")
			Map<String, Object> o = MAP.readValue(file, Map.class);
			if (size < 0) {
				size = calcSize(o);
			}
			return o;
		}

		@Override
		public int getWrites() {
			return writes;
		}

		@Override
		public long getObjectSize() throws Exception {
			if (size > -1 ) {
				return size;
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> o = MAP.readValue(file, Map.class);
			size = calcSize(o);
			return size;
		}
		
		public NoTypeChecking setSkipReads(boolean skip) {
			this.skipReads = skip;
			return this;
		}
		
		public boolean getSkipReads() {
			return skipReads;
		}
		
		public NoTypeChecking setSkipWrites(boolean skip) {
			skipWrites = skip;
			return this;
		}
		
		public boolean getSkipWrites() {
			return skipWrites;
		}

		private long calcSize(Map<String, Object> o) throws IOException,
				JsonGenerationException, JsonMappingException {
			CountingOutputStream cos = new CountingOutputStream();
			MAP.writeValue(cos, o);
			return cos.getSize();
		}

		@Override
		public String toString() {
			return "NoTypeChecking [testName=" + testName + ", file=" + file
					+ ", writes=" + writes + ", size=" + size + ", skipReads="
					+ skipReads + ", skipWrites=" + skipWrites + "]";
		}
	}
	
	private static AuthToken token;
	
	private static final String WORKSPACE_NAME = "testws123457891234567894";
	
	public static void timeReadWrite(String user, String pwd, List<URL> wsURLs,
			List<TestSetup> tests)
			throws Exception {
		
		System.out.println("logging in " + user);
		token = AuthService.login(user, pwd).getToken();
		
		LinkedHashMap<URL, WorkspaceClient> clients = setUpServers(user, pwd,
				wsURLs, tests);

		Date start = new Date();
		System.out.println("started at " + start);
		
		List<ReadWritePerformance> results =
				new LinkedList<ReadWritePerformance>();
		for (URL u: wsURLs) {
			WorkspaceClient ws = clients.get(u);
			System.out.println(String.format(
					"Timing read/write against the workspace service at %s, ver %s",
					u, ws.ver()));
			for (TestSetup ts: tests) {
				results.add(measurePerformance(u, ws, ts));
			}
		}
		printResults(wsURLs, tests, clients, start, results);
	}

	private static void printResults(List<URL> wsURLs, List<TestSetup> tests,
			LinkedHashMap<URL, WorkspaceClient> clients, Date start,
			List<ReadWritePerformance> results) throws IOException,
			JsonClientException, Exception {
		final int width = 8;
		Table tbl = new Table(width);
		tbl.addCell("Test");
		tbl.addCell("Type");
		tbl.addCell("Size (B)");
		tbl.addCell("N");
		tbl.addCell("write (s)");
		tbl.addCell("write (MBps)");
		tbl.addCell("read (s)");
		tbl.addCell("read (MBps)");
		int i = 0;
		for (URL u: wsURLs) {
			tbl.addCell(u + ", ver " + clients.get(u).ver(),
					new CellStyle(CellStyle.HorizontalAlign.center), width);
			for (TestSetup ts: tests) {
				ReadWritePerformance pm  = results.get(i);
				tbl.addCell(ts.getTestName());
				tbl.addCell(ts.getFullTypeName());
				tbl.addCell("" + ts.getObjectSize());
				tbl.addCell("" + ts.getWrites());
				
				double wmean = pm.getAverageWritesInSec();
				double rmean = pm.getAverageReadsInSec();
				double wmbps = ts.getObjectSize() / wmean / 1000000;
				double rmbps = ts.getObjectSize() / rmean / 1000000;
				
				tbl.addCell(String.format("%,.4f +/- %,.4f", wmean, pm.getStdDevWritesInSec()));
				tbl.addCell(String.format("%,.3f", wmbps));
				tbl.addCell(String.format("%,.4f +/- %,.4f", rmean, pm.getStdDevReadsInSec()));
				tbl.addCell(String.format("%,.3f", rmbps));
				i++;
			}
		}
		System.out.println(tbl.render());
		Date complete = new Date();
		System.out.println("Completed: " + complete);
		System.out.println("Elapsed: " + calculateElapsed(start, complete));
	}

	private static LinkedHashMap<URL, WorkspaceClient> setUpServers(
			String user, String pwd, List<URL> wsURLs, List<TestSetup> tests)
			throws UnauthorizedException, IOException, JsonClientException {
		LinkedHashMap<URL, WorkspaceClient> clients =
				new LinkedHashMap<URL, WorkspaceClient>(); 
		
		for (URL u: wsURLs) {
			System.out.println("Setting up server at " + u);
			WorkspaceClient ws = new WorkspaceClient(u, user, pwd);
			clients.put(u, ws);
			ws.setAuthAllowedForHttp(true);
	
			for (TestSetup test: tests) {
				if (!ws.listModules(new ListModulesParams()).contains(test.getModule())) {
					System.out.println("Registering spec " + test.getModule());
					ws.requestModuleOwnership(test.getModule());
					Map<String, String> cmd = new HashMap<String, String>();
					cmd.put("command", "approveModRequest");
					cmd.put("module", test.getModule());
					ws.administer(new UObject(cmd));
					ws.registerTypespec(new RegisterTypespecParams()
					.withDryrun(0L)
					.withSpec(test.getTypeSpec())
					.withNewTypes(Arrays.asList(test.getType())));
					ws.releaseModule(test.getModule());
				} else {
					Set<String> types = ws.getModuleInfo(new GetModuleInfoParams()
							.withMod(test.getModule())).getTypes().keySet();
					boolean hasType = false;
					for (String type: types) {
						TypeDefId td = TypeDefId.fromTypeString(type);
						if (td.getType().getName().equals(test.getType())) {
							hasType = true;
							break;
						}
					}
					if (!hasType) {
						ws.registerTypespec(new RegisterTypespecParams()
								.withDryrun(0L)
								.withMod(test.getModule())
								.withNewTypes(Arrays.asList(test.getType())));
						ws.releaseModule(test.getModule());
					} else {
						System.out.println(test.getModule() + "." + test.getType() +
								" already registered");
					}
				}
			}

			try {
				ws.getWorkspaceInfo(new WorkspaceIdentity()
						.withWorkspace(WORKSPACE_NAME));
			} catch (ServerException se) {
				try {
					ws.createWorkspace(new CreateWorkspaceParams()
							.withWorkspace(WORKSPACE_NAME));
				} catch (ServerException se2) {
					System.out.println("Couldn't access or create workspace " + WORKSPACE_NAME);
					System.out.println("Access exception:");
					System.out.println(se);
					System.out.println("\nCreation exception:");
					System.out.println(se2);
				}
			}
		}
		return clients;
	}
	
	private static ReadWritePerformance measurePerformance(
			URL url, WorkspaceClient ws, TestSetup ts) throws Exception {
		Map<String, Object> obj = ts.getObject();
		String type = ts.getFullTypeName();
		//0.1.6 has a bug where saving objects doesn't work without the full type
		TypeInfo tinfo = ws.getTypeInfo(type);
		type = tinfo.getTypeDef();
		
		ObjectSaveData osd = new ObjectSaveData().withData(new UObject(obj))
				.withType(type).withName("skipwrites");
		
		System.out.println(String.format("Reading and writing %s objects of type %s, size %s",
				ts.getWrites(), ts.getFullTypeName(), ts.getObjectSize()));
		if (ts.getSkipWrites()) {
			ws.saveObjects(new SaveObjectsParams().withWorkspace(WORKSPACE_NAME)
					.withObjects(Arrays.asList(osd)));
		}
		byte[] b = new byte[10000000];
		List<Long> writes = new LinkedList<Long>();
		List<Long> reads = new LinkedList<Long>();
		for (int i = 0; i < ts.getWrites(); i++) {
			String name;
			if (!ts.getSkipWrites()) {
				name = "test-obj" + i;
				obj.put("fakekeyaddededforperftesting", Math.random()); //ensures save to backend since MD5 will be different
				osd.withName(name);
				long start = System.nanoTime();
				ws.saveObjects(new SaveObjectsParams().withWorkspace(WORKSPACE_NAME)
						.withObjects(Arrays.asList(osd)));
				writes.add(System.nanoTime() - start);
			} else {
				name = "skipwrites";
			}
			
			Map<String, Object> req = new HashMap<String, Object>();
			req.put("params", Arrays.asList(Arrays.asList(new ObjectIdentity()
					.withWorkspace(WORKSPACE_NAME).withName(name))));
			req.put("method", "Workspace.get_objects");
			req.put("version", "1.1");
			req.put("id", ("" + Math.random()).substring(2));
			
			if (!ts.getSkipReads()) {
				long start = System.nanoTime();
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setConnectTimeout(10000);
				conn.setDoOutput(true);
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Authorization", token.toString());
				new ObjectMapper().writeValue(conn.getOutputStream(), req);
				conn.getResponseCode();
				InputStream is = conn.getInputStream();
				int read = 1;
				while (read > -1) {
					read = is.read(b);
				}
				is.close();
				reads.add(System.nanoTime() - start);
			}
			
//			start = System.nanoTime();
//			ws.getObjects(Arrays.asList(
//					new ObjectIdentity().withWorkspace(WORKSPACE_NAME)
//					.withName(name)));
//			reads.add(System.nanoTime() - start);
		}
//		System.out.println("writes:");
//		for (Long l: writes) {
//			System.out.println(l);
//		}
//		System.out.println("reads:");
//		for (Long l: reads) {
//			System.out.println(l);
//		}
		return new ReadWritePerformance(writes, reads);
	}



	public static String calculateElapsed(Date start, Date complete) {
		double secdiff = ((double) (complete.getTime() - start.getTime())) / 1000.0;
		long hours = (long) secdiff / 3600;
		long mins = (long) secdiff / 60;
		double secs = secdiff % 60;
		return hours + "h " + mins + "m " + String.format("%.3fs", secs);
	}
	
}
