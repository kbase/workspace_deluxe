package performance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.nocrala.tools.texttablefmt.CellStyle;
import org.nocrala.tools.texttablefmt.Table;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UObject;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.database.DefaultReferenceParser;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspace.workspaces.WorkspaceSaveObject;
import us.kbase.workspace.workspaces.Workspaces;

/* DO NOT run these tests on the production workspace.
 * 
 * Note you must make the SupahFakeKBGA.Genome type available in the workspace
 * before running these tests. 
 */
public class TimeReadWrite {
	
	//TODO time 0.0.5 ws
	//TODO time with gridfs
	//TODO time w/o subsetting
	
	public static void main(String[] args) throws Exception {
		int writes = Integer.valueOf(args[0]);
		String user = args[1];
		String pwd = args[2];
		timeReadWrite(writes, user, pwd, "http://localhost:7044",
				"http://localhost:7058", Arrays.asList("Shock", "WorkspaceLibJsonNode",
						"WorkspaceJSON1ObjPer"),
				Arrays.asList(1, 2, 3, 4));//, 5, 7, 10, 16, 20));
	}

	private static final Map<String, Class<? extends AbstractReadWriteTest>> configMap =
			new HashMap<String, Class<? extends AbstractReadWriteTest>>();
	static {
		configMap.put("Shock", ShockThread.class);
		configMap.put("WorkspaceJSON1ObjPer", WorkspaceJsonRPCThread.class);
		configMap.put("WorkspaceLibJsonNode", WorkspaceLibJsonNode.class);
	}
	
	private static final String FILE = "83333.2.txt";
	private static final String SPEC_FILE = "SupahFakeKBGA.spec";
	private static final String MONGO_HOST = "localhost";
	private static final String MONGO_DB = "delete_this_ws";
	private static final String TYPE_DB = "delete_this_type";
	
	
	private static final String MODULE = "SupahFakeKBGA";
	private static final String M_TYPE = "Genome";
	private static final String TYPE = MODULE + "." + M_TYPE;
	private static final TypeDefId TYPEDEF = new TypeDefId(TYPE); 
	private static final ObjectMapper MAP = new ObjectMapper(); 
	
	private static byte[] data;
	private static AuthToken token;
	private static String password;
	private static URL shockURL;
	private static URL workspace0_1_0URL;
	
	
	public static void timeReadWrite(int writes, String user, String pwd, String shockurl,
			String workspaceURL,  List<String> configs, List<Integer> threadCounts)
					throws Exception {
		System.out.println(
				"Timing read/write against shock and the workspace service");
		System.out.println("Shock url: " + shockURL);
		System.out.println("Workspace url: " + workspaceURL);
		System.out.println("logging in " + user);
		
		password = pwd;
		token = AuthService.login(user, pwd).getToken();
		shockURL = new URL(shockurl);
		workspace0_1_0URL = new URL(workspaceURL);
		data = IOUtils.toByteArray(TimeReadWrite.class.getResourceAsStream(FILE));
		String spec = IOUtils.toString(TimeReadWrite.class.getResourceAsStream(SPEC_FILE));
		
		System.setProperty("test.mongo.db1", MONGO_DB);
		System.setProperty("test.mongo.db.types1", TYPE_DB);
		System.setProperty("test.mongo.host", MONGO_HOST);
		System.setProperty("test.shock.url", shockurl);
		WorkspaceTestCommon.destroyAndSetupDB(1, WorkspaceTestCommon.SHOCK, user);
		Workspaces ws = new Workspaces(new MongoWorkspaceDB(MONGO_HOST, MONGO_DB, password, null, null),
				new DefaultReferenceParser());
		ws.requestModuleRegistration(new WorkspaceUser("foo"), MODULE);
		ws.resolveModuleRegistration(MODULE, true);
		ws.compileNewTypeSpec(new WorkspaceUser("foo"), spec, Arrays.asList(M_TYPE), null, null, false, null);
		ws.releaseTypes(new WorkspaceUser("foo"), MODULE);
		
		
		System.out.println(String.format(
				"Writing a file %s times, then reading it back %s times",
				writes, writes));
		System.out.println(String.format("file size: %,dB", data.length));
		
		Map<String, Map<Integer, Perf>> results =
				new HashMap<String, Map<Integer, TimeReadWrite.Perf>>();
		
		for (String config: configs) {
			if (!configMap.containsKey(config)) {
				throw new IllegalArgumentException("No test config " + config);
			}
			results.put(config, new HashMap<Integer, TimeReadWrite.Perf>());
			for (Integer threadCount: threadCounts) {
				System.out.println("Measuring config " + config + " performance with "
						+ threadCount + " threads");
				results.get(config).put(threadCount, measurePerformance(
						writes, threadCount, configMap.get(config)));
			}
		}
		
		Table tbl = new Table(5);
		tbl.addCell("Threads");
		tbl.addCell("write (s)");
		tbl.addCell("write (MBps)");
		tbl.addCell("read (s)");
		tbl.addCell("read (MBps)");
		for (String config: configs) {
			tbl.addCell(config, new CellStyle(CellStyle.HorizontalAlign.center), 5);
			List<Integer> sorted = new ArrayList<Integer>(results.get(config).keySet());
			Collections.sort(sorted);
			for (Integer threads: sorted) {
				Perf p = results.get(config).get(threads);
				tbl.addCell("" + threads);
				tbl.addCell(String.format("%,.4f", p.writeSec));
				tbl.addCell(String.format("%,.3f", calcMBps(writes, p.writeSec)));
				tbl.addCell(String.format("%,.4f", p.readSec));
				tbl.addCell(String.format("%,.3f", calcMBps(writes, p.readSec)));
			}
		}
		System.out.println(tbl.render());
	}
	
	private static double calcMBps(int writes, double elapsedSec) {
		return (double) writes * (double) data.length / elapsedSec / 1000000.0;
	}
	
	
	private static Perf measurePerformance(int writes, int threads,
			Class<? extends AbstractReadWriteTest> clazz)
			throws Exception {
		
		AbstractReadWriteTest[] rwthreads = new AbstractReadWriteTest[threads]; 
		boolean hasMod = writes % threads != 0;
		int minWrites = writes / threads;
		int pos = 0;
		List<Integer> threadDist = new LinkedList<Integer>();
		
		for (int i = 0; i < threads; i++) {
			if (i + 1 == threads) {
				int threadSize = writes - pos;
				rwthreads[i] = clazz.newInstance();
				rwthreads[i].initialize(threadSize, i + 1);
				threadDist.add(threadSize);
			} else if (hasMod && i % 2 == 1) {
				rwthreads[i] = clazz.newInstance();
				rwthreads[i].initialize(minWrites + 1, i + 1);
				pos += minWrites + 1;
				threadDist.add(minWrites + 1);
			} else {
				rwthreads[i] = clazz.newInstance();
				rwthreads[i].initialize(minWrites, i + 1);
				pos += minWrites;
				threadDist.add(minWrites);
			}
		}
		System.out.println("Thread distribution: " + threadDist);

		long start = System.nanoTime();
		for (int i = 0; i < threads; i++) {
			rwthreads[i].doWrites();
		}
		for (int i = 0; i < threads; i++) {
			rwthreads[i].join();
		}
		long writeNanoSec = System.nanoTime() - start;
		
		start = System.nanoTime();
		for (int i = 0; i < threads; i++) {
			rwthreads[i].doReads();
		}
		for (int i = 0; i < threads; i++) {
			rwthreads[i].join();
		}
		long readNanoSec = System.nanoTime() - start;
		
		for (int i = 0; i < threads; i++) {
			rwthreads[i].cleanUp();
		}

		return new Perf(writeNanoSec, readNanoSec);
	}
	
	public static class WorkspaceJsonRPCThread extends AbstractReadWriteTest {

		private WorkspaceClient wsc;
		private int writes;
		@SuppressWarnings("unused")
		private int id;
		final List<String> wsids = new LinkedList<String>();
		private List<Map<String,Object>> objs = new LinkedList<Map<String,Object>>();
		private String workspace;
		
		public WorkspaceJsonRPCThread() throws Exception {
			wsc = new WorkspaceClient(workspace0_1_0URL, token);
			wsc.setAuthAllowedForHttp(true);
			workspace = "SupahFake" + new String("" + Math.random()).substring(2)
					.replace("-", ""); //in case it's E-X
			try {
				wsc.createWorkspace(new CreateWorkspaceParams().withWorkspace(workspace));
			} catch (ServerException se) {
				//probably just created already
			}
		};
		
		@SuppressWarnings("unchecked")
		public void initialize(int writes, int id) throws Exception {
			this.writes = writes;
			this.id = id;
			for (int i = 0; i < this.writes; i++) {
				objs.add((Map<String, Object>) MAP.readValue(data, Map.class));
				objs.get(i).put("fakekey", Math.random());
			}
		}

		@Override
		public void performReads() throws Exception {
			for (String id: wsids) {
				wsc.getObjects(Arrays.asList(new ObjectIdentity()
					.withWorkspace(workspace).withName(id)));
			}
		}

		@Override
		public void performWrites() throws Exception {
			for (Map<String, Object> o: objs) {
				wsids.add(wsc.saveObjects(new SaveObjectsParams()
					.withWorkspace(workspace)
					.withObjects(Arrays.asList(new ObjectSaveData()
						.withData(new UObject(o))
						.withType(TYPE)))).get(0).getE2());
			}
		}

		@Override
		public void cleanUp() throws Exception {
			wsc.deleteWorkspace(new WorkspaceIdentity().withWorkspace(workspace));
		}
	}
	
	public static class WorkspaceLibJsonNode extends AbstractReadWriteTest {

		private static final WorkspaceUser foo = new WorkspaceUser("foo");
		
		private Workspaces ws;
		private int writes;
		@SuppressWarnings("unused")
		private int id;
		final List<String> wsids = new LinkedList<String>();
		private List<JsonNode> objs = new LinkedList<JsonNode>();
		private String workspace;
		
		public WorkspaceLibJsonNode() throws Exception {
			
			ws = new Workspaces(new MongoWorkspaceDB(MONGO_HOST, MONGO_DB, password, null, null),
					new DefaultReferenceParser());
			workspace = "SupahFake" + new String("" + Math.random()).substring(2)
					.replace("-", ""); //in case it's E-X
			ws.createWorkspace(foo, workspace, false, null);
		};
		
		@SuppressWarnings("unchecked")
		public void initialize(int writes, int id) throws Exception {
			this.writes = writes;
			this.id = id;
			for (int i = 0; i < this.writes; i++) {
				Map<String, Object> foo = (Map<String, Object>) MAP.readValue(data, Map.class);
				foo.put("fakekey", Math.random());
				objs.add(MAP.valueToTree(foo));
			}
		}

		@Override
		public void performReads() throws Exception {
			for (String id: wsids) {
				ws.getObjects(foo, Arrays.asList(
						new ObjectIdentifier(new WorkspaceIdentifier(workspace), id)));
			}
		}

		@Override
		public void performWrites() throws Exception {
			for (JsonNode o: objs) {
				wsids.add(ws.saveObjects(foo, new WorkspaceIdentifier(workspace),
						Arrays.asList(new WorkspaceSaveObject(
								o, TYPEDEF, null, new Provenance(foo), false)))
						.get(0).getObjectName());
			}
		}

		@Override
		public void cleanUp() throws Exception {
			ws.setWorkspaceDeleted(foo, new WorkspaceIdentifier(workspace), true);
		}
	}
	
	// use builder in future, not really worth the time here
	public static class ShockThread extends AbstractReadWriteTest {
		
		private BasicShockClient bsc;
		private int writes;
		@SuppressWarnings("unused")
		private int id;
		public final List<ShockNode> nodes = new LinkedList<ShockNode>();
		
		public void initialize(int writes, int id) throws Exception {
			this.bsc = new BasicShockClient(shockURL, token);
			this.writes = writes;
			this.id = id;
		}
		
		@Override
		public void performReads() throws Exception {
			for (ShockNode sn: nodes) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				sn.getFile(baos);
				baos.toByteArray();
			}
		}

		@Override
		public void performWrites() throws Exception {
			for (int i = 0; i < this.writes; i++) {
				nodes.add(bsc.addNode(new ByteArrayInputStream(data), "foo", "UTF-8"));
			}
		}

		@Override
		public void cleanUp() throws Exception {
			for (ShockNode sn: nodes) {
				sn.delete();
			}
		}
	}
	
	public static abstract class AbstractReadWriteTest {
		
		private Thread thread;
		
		public AbstractReadWriteTest() {}
		
		public void doReads() {
			thread = new Thread() {
				
				@Override
				public void run() {
					try {
						performReads();
					} catch (Exception e) {
						e.printStackTrace();
						if (e instanceof ServerException) {
							System.out.println(((ServerException) e).getData());
						}
					}
				}
			};
			thread.start();
		}
		
		public void doWrites() {
			thread = new Thread() {
				
				@Override
				public void run() {
					try {
						performWrites();
					} catch (Exception e) {
						e.printStackTrace();
						if (e instanceof ServerException) {
							System.out.println(((ServerException) e).getData());
						}
					}
				}
			};
			thread.start();
		}
		
		public void join() throws Exception {
			thread.join();
		}
		
		public abstract void initialize(int writes, int id) throws Exception;
		public abstract void performReads() throws Exception;
		public abstract void performWrites() throws Exception;
		public abstract void cleanUp() throws Exception;
	}
	
	private static class Perf {
		
		public final double writeSec;
		public final double readSec;
		
		public Perf(double writeNanoSec, double readNanoSec) {
			writeSec = ((double) writeNanoSec) / 1000000000.0;
			readSec = ((double) readNanoSec) / 1000000000.0;
		}
	}
}
