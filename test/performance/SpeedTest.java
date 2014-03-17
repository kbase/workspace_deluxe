package performance;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.nocrala.tools.texttablefmt.Table;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.common.service.ServerException;
import us.kbase.common.service.UObject;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.ListModulesParams;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.database.CountingOutputStream;

/* Simple test that saves a specified object multiple times to the workspace,
 * reads it back, and prints a summary.
 * 
 * DO NOT run these tests on production workspaces.
 * 
 * Eventually you'll need to drop the shock and ws databases, as they'll
 * fill up.
 * 
 * The user for these tests must have admin privs on the workspace.
 * 
 */
public class SpeedTest {
	
	public interface TestSetup {
		
		public String getTestName();
		public int getWrites();
		public String getType();
		public Map<String, Object> getObject() throws Exception;
		public String getModule();
		public String getFullTypeName();
		public String getTypeSpec() throws IOException;
		public long getObjectSize() throws Exception;
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

		@Override
		public String toString() {
			return "SpecAndObjectFromFile [type=" + type + ", module=" + module
					+ ", writes=" + writes + ", size=" + size + "]";
		}
	}
	
	public static class NoTypeChecking implements TestSetup {
		
		private static final ObjectMapper MAP = new ObjectMapper(); 
		
		private final String testName;
		private final File file;
		private final int writes;
		private long size = -1;
		
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

		private long calcSize(Map<String, Object> o) throws IOException,
				JsonGenerationException, JsonMappingException {
			CountingOutputStream cos = new CountingOutputStream();
			MAP.writeValue(cos, o);
			return cos.getSize();
		}

		@Override
		public String toString() {
			return "NoTypeChecking [testName=" + testName + ", writes="
					+ writes + ", size=" + size + "]";
		}
	}
	
	public static void main(String[] args) throws Exception {
		String user = args[0];
		String pwd = args[1];
		timeReadWrite(user, pwd, new URL("http://localhost:7058"),
				Arrays.asList(
						new SpecAndObjectFromFile("Genome", 100, new File("test/performance/83333.2.txt"), 
								new File("test/performance/SupahFakeKBGA.spec"), "SupahFakeKBGA", "Genome"),
						new NoTypeChecking("Genome no TC", 100, new File("test/performance/83333.2.txt"))));
	}

	private static final String WORKSPACE_NAME = "testws123457891234567894";
	
	public static void timeReadWrite(String user, String pwd, URL wsURL,
			List<TestSetup> tests)
			throws Exception {
		
		System.out.println("logging in " + user);
		
		WorkspaceClient ws = new WorkspaceClient(wsURL, user, pwd);
		ws.setAuthAllowedForHttp(true);

		Date start = new Date();
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
				System.out.println(test.getModule() + " already registered");
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

		System.out.println(String.format(
				"Timing read/write against the workspace service at %s, ver %s",
				wsURL, ws.ver()));
		System.out.println("started at " + start);
		
		List<PerformanceMeasurement> results =
				new LinkedList<PerformanceMeasurement>();
		
		for (TestSetup ts: tests) {
			results.add(measurePerformance(ws, ts));
		}
		
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
		for (int i = 0; i < tests.size(); i++) {
			TestSetup ts = tests.get(i);
			PerformanceMeasurement pm  = results.get(i);
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
		}
		System.out.println(tbl.render());
		Date complete = new Date();
		System.out.println("Completed: " + complete);
		System.out.println("Elapsed: " + calculateElapsed(start, complete));
	}
	
	private static PerformanceMeasurement measurePerformance(
			WorkspaceClient ws, TestSetup ts) throws Exception {
		Map<String, Object> obj = ts.getObject();
		String type = ts.getFullTypeName();
		ObjectSaveData osd = new ObjectSaveData().withData(new UObject(obj))
				.withType(type);
		
		System.out.println(String.format("Reading and writing %s objects of type %s, size %s",
				ts.getWrites(), ts.getFullTypeName(), ts.getObjectSize()));
		
		List<Long> writes = new LinkedList<Long>();
		List<Long> reads = new LinkedList<Long>();
		for (int i = 0; i < ts.getWrites(); i++) {
			String name = "test-obj" + i;
			obj.put("fakekeyaddededforperftesting", i); //ensures save to backend since MD5 will be different
			osd.withName(name);
			long start = System.nanoTime();
			ws.saveObjects(new SaveObjectsParams().withWorkspace(WORKSPACE_NAME)
					.withObjects(Arrays.asList(osd)));
			writes.add(System.nanoTime() - start);
			
			start = System.nanoTime();
			ws.getObjects(Arrays.asList(new ObjectIdentity().withWorkspace(WORKSPACE_NAME)
					.withName(name)));
			reads.add(System.nanoTime() - start);
		}
//		System.out.println("writes:");
//		for (Long l: writes) {
//			System.out.println(l);
//		}
//		System.out.println("reads:");
//		for (Long l: reads) {
//			System.out.println(l);
//		}
		return new PerformanceMeasurement(writes, reads);
	}



	public static String calculateElapsed(Date start, Date complete) {
		double secdiff = ((double) (complete.getTime() - start.getTime())) / 1000.0;
		long hours = (long) secdiff / 3600;
		long mins = (long) secdiff / 60;
		double secs = secdiff % 60;
		return hours + "h " + mins + "m " + String.format("%.3fs", secs);
	}
	
}
