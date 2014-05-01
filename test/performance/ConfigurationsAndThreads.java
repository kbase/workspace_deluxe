package performance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.nocrala.tools.texttablefmt.CellStyle;
import org.nocrala.tools.texttablefmt.Table;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UObject;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.Writable;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.DefaultReferenceParser;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.mongo.GridFSBackend;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.ShockBackend;
import us.kbase.workspace.lib.WorkspaceSaveObject;
import us.kbase.workspace.lib.Workspace;
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspaceservice.DeleteWorkspaceParams;
import us.kbase.workspaceservice.GetObjectParams;
import us.kbase.workspaceservice.SaveObjectParams;
import us.kbase.workspaceservice.WorkspaceServiceClient;

/* DO NOT run these tests on production workspaces.
 * 
 * Note you must make the SupahFakeKBGA.Genome type available in the workspace
 * before running these tests. 
 */
public class ConfigurationsAndThreads {
	
	public static void main(String[] args) throws Exception {
		int writes = Integer.valueOf(args[0]);
		String user = args[1];
		String pwd = args[2];
		timeReadWrite(writes, user, pwd, "http://localhost:7044", "http://localhost:7058", "http://localhost:7057",
				Arrays.asList(
						"Workspace005",
						"Shock",
						"ShockBackend",
						"GridFSBackend",
						"WorkspaceLibShockEmptyType",
						"WorkspaceLibShock",
						"WS_JSONRPCShock1ObjPerCall"
						),
				Arrays.asList(1, 2, 3, 4, 5, 6, 7));
	}

	private static final Map<String, Class<? extends AbstractReadWriteTest>> configMap =
			new HashMap<String, Class<? extends AbstractReadWriteTest>>();
	static {
		configMap.put("Shock", ShockClient.class);
		configMap.put("WS_JSONRPCShock1ObjPerCall", WorkspaceJsonRPCShock.class);
		configMap.put("WorkspaceLibShock", WorkspaceLibShock.class);
		configMap.put("GridFSBackend", GridFSBackendOnly.class);
		configMap.put("ShockBackend", ShockBackendOnly.class);
		configMap.put("Workspace005", Workspace005JsonRPCShock.class);
		configMap.put("WorkspaceLibShockEmptyType", WorkspaceLibShockEmptySpec.class);
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
	
	private static final String SIMPLE_MODULE = "SomeModule";
	private static final String SIMPLE_M_TYPE = "AType";
	private static final String SIMPLE_TYPE = SIMPLE_MODULE + "." + SIMPLE_M_TYPE;
	private static final TypeDefId SIMPLE_TYPEDEF = new TypeDefId(SIMPLE_TYPE);
	private static final String SIMPLE_SPEC =
			"module SomeModule {/* @optional thing */ typedef structure {string thing;} AType;};";
	
	private static final ObjectMapper MAP = new ObjectMapper(); 
	
	private static byte[] data;
	private static JsonNode jsonData;
	private static Map<String, Object> mapData;
	private static AuthToken token;
	private static String password;
	
	private static URL shockURL;
	private static URL workspace0_1_0URL;
	private static URL workspace0_0_5URL;
	
	@SuppressWarnings("unchecked")
	public static void timeReadWrite(int writes, String user, String pwd, String shockurl,
			String workspaceURL, String workspace005URL, List<String> configs, List<Integer> threadCounts)
					throws Exception {
		System.out.println(
				"Timing read/write against shock and the workspace service");
		System.out.println("Shock url: " + shockurl);
		System.out.println("Workspace url: " + workspaceURL);
		System.out.println("v0.0.5 Workspace url: " + workspace005URL);
		System.out.println("logging in " + user);
		
		password = pwd;
		token = AuthService.login(user, pwd).getToken();
		shockURL = new URL(shockurl);
		workspace0_1_0URL = new URL(workspaceURL);
		workspace0_0_5URL = new URL(workspace005URL);
		data = IOUtils.toByteArray(ConfigurationsAndThreads.class.getResourceAsStream(FILE));
		jsonData = MAP.readTree(data);
		mapData = MAP.treeToValue(jsonData, Map.class);
		String spec = IOUtils.toString(ConfigurationsAndThreads.class.getResourceAsStream(SPEC_FILE));
		
		System.out.println(String.format(
				"Writing a file %s times, then reading it back %s times",
				writes, writes));
		System.out.println(String.format("file size: %,dB", data.length));
		Date start = new Date();
		System.out.println("started at " + start);
		
		System.setProperty("test.mongo.db1", MONGO_DB);
		System.setProperty("test.mongo.db.types1", TYPE_DB);
		System.setProperty("test.mongo.host", MONGO_HOST);
		System.setProperty("test.shock.url", shockurl);
		WorkspaceTestCommon.destroyAndSetupDB(1, WorkspaceTestCommon.SHOCK, user);
		Workspace ws = new Workspace(new MongoWorkspaceDB(MONGO_HOST, MONGO_DB, password, TempFilesManager.forTests()),
				new DefaultReferenceParser());
		WorkspaceUser foo = new WorkspaceUser("foo");
		ws.requestModuleRegistration(foo, MODULE);
		ws.resolveModuleRegistration(MODULE, true);
		ws.compileNewTypeSpec(foo, spec, Arrays.asList(M_TYPE), null, null, false, null);
		ws.releaseTypes(foo, MODULE);
		
		ws.requestModuleRegistration(foo, SIMPLE_MODULE);
		ws.resolveModuleRegistration(SIMPLE_MODULE, true);
		ws.compileNewTypeSpec(foo, SIMPLE_SPEC,
				Arrays.asList(SIMPLE_M_TYPE), null, null, false, null);
		ws.releaseTypes(foo, SIMPLE_MODULE);
		
		Map<String, Map<Integer, Perf>> results =
				new HashMap<String, Map<Integer, ConfigurationsAndThreads.Perf>>();
		
		for (String config: configs) {
			if (!configMap.containsKey(config)) {
				throw new IllegalArgumentException("No test config " + config);
			}
			results.put(config, new HashMap<Integer, ConfigurationsAndThreads.Perf>());
			for (Integer threadCount: threadCounts) {
				System.out.println("Measuring config " + config + " performance with "
						+ threadCount + " threads");
				results.get(config).put(threadCount, measurePerformance(
						writes, threadCount, configMap.get(config)));
			}
		}
		
		final int width = 6;
		Table tbl = new Table(width);
		tbl.addCell("Threads");
		tbl.addCell("write (s)");
		tbl.addCell("write (MBps)");
		tbl.addCell("read (s)");
		tbl.addCell("read (MBps)");
		tbl.addCell("errors");
		for (String config: configs) {
			tbl.addCell(config, new CellStyle(CellStyle.HorizontalAlign.center), width);
			List<Integer> sorted = new ArrayList<Integer>(results.get(config).keySet());
			Collections.sort(sorted);
			for (Integer threads: sorted) {
				Perf p = results.get(config).get(threads);
				tbl.addCell("" + threads);
				tbl.addCell(String.format("%,.4f", p.writeSec));
				tbl.addCell(String.format("%,.3f", calcMBps(writes, p.writeSec)));
				tbl.addCell(String.format("%,.4f", p.readSec));
				tbl.addCell(String.format("%,.3f", calcMBps(writes, p.readSec)));
				tbl.addCell("" + p.errors);
			}
		}
		System.out.println(tbl.render());
		Date complete = new Date();
		System.out.println("Completed: " + complete);
		System.out.println("Elapsed: " + calculateElapsed(start, complete));
	}
	
	public static String calculateElapsed(Date start, Date complete) {
		double secdiff = ((double) (complete.getTime() - start.getTime())) / 1000.0;
		long hours = (long) secdiff / 3600;
		long mins = (long) secdiff / 60;
		double secs = secdiff % 60;
		return hours + "h " + mins + "m " + String.format("%.3fs", secs);
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

		int errors = 0;
		for (int i = 0; i < threads; i++) {
			errors += rwthreads[i].getErrorCount();
			rwthreads[i].cleanUp();
		}

		return new Perf(writeNanoSec, readNanoSec, errors);
	}
	
	private static Writable treeToWritable(final JsonNode value) {
		return new Writable() {
			@Override
			public void write(OutputStream w) throws IOException {
				MAP.writeValue(w, value);
			}
			@Override
			public void releaseResources() throws IOException {
			}
		};
	}

	public static class Workspace005JsonRPCShock extends AbstractReadWriteTest {

		private WorkspaceServiceClient wsc;
		private int id;
		final List<String> wsids = new LinkedList<String>();
		private List<Map<String,Object>> objs = new LinkedList<Map<String,Object>>();
		private String workspace;
		
		public Workspace005JsonRPCShock() throws Exception {
			super();
			wsc = new WorkspaceServiceClient(workspace0_0_5URL, token);
			wsc.setAuthAllowedForHttp(true);
			workspace = "SupahFake" + new String("" + Math.random()).substring(2)
					.replace("-", ""); //in case it's E-X
			try {
				wsc.createWorkspace(new us.kbase.workspaceservice.CreateWorkspaceParams()
					.withWorkspace(workspace));
			} catch (ServerException se) {
				//probably just created already
			}
		};
		
		public void initialize(int writes, int id) throws Exception {
			this.id = id;
			for (int i = 0; i < writes; i++) {
				objs.add(new HashMap<String, Object>(mapData));
				String name = "id" + ("" + Math.random()).substring(2).replace("-", "");
				objs.get(i).put("fakekey", name);
				wsids.add(name);
			}
		}

		@Override
		public int performReads() throws Exception {
			int errcount = 0;
			for (String id: wsids) {
				boolean error = true;
				while (error) {
					try {
						wsc.getObject(new GetObjectParams().withWorkspace(workspace)
								.withId(id).withType(M_TYPE));
						error = false;
					} catch (Exception e) {
						errcount++;
						System.out.println(String.format(
								"error # %s in read thread w/ id %s on object %s",
								errcount, this.id, id));
						e.printStackTrace();
					}
				}
			}
			return errcount;
		}

		@Override
		public int performWrites() throws Exception {
			int errcount = 0;
			for (Map<String, Object> o: objs) {
				boolean error = true;
				while (error) {
					String id = (String) o.get("fakekey");
					try {
						wsc.saveObject(new SaveObjectParams().withWorkspace(workspace)
								.withId(id).withType(M_TYPE).withData(new UObject(mapData)));
						error = false;
					} catch (Exception e) {
						errcount++;
						System.out.println(String.format(
								"error # %s in write thread w/ id %s on object %s",
								errcount, this.id, id));
						e.printStackTrace();
					}
				}
			}
			return errcount;
		}

		@Override
		public void cleanUp() throws Exception {
			wsc.deleteWorkspace(new DeleteWorkspaceParams().withWorkspace(workspace));
		}
	}
	
	public static class WorkspaceJsonRPCShock extends AbstractReadWriteTest {

		private WorkspaceClient wsc;
		@SuppressWarnings("unused")
		private int id;
		final List<String> wsids = new LinkedList<String>();
		private List<Map<String,Object>> objs = new LinkedList<Map<String,Object>>();
		private String workspace;
		
		public WorkspaceJsonRPCShock() throws Exception {
			super();
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
		
		public void initialize(int writes, int id) throws Exception {
			this.id = id;
			for (int i = 0; i < writes; i++) {
				objs.add(new HashMap<String, Object>(mapData));
				objs.get(i).put("fakekey", Math.random());
			}
		}

		@Override
		public int performReads() throws Exception {
			for (String id: wsids) {
				wsc.getObjects(Arrays.asList(new ObjectIdentity()
					.withWorkspace(workspace).withName(id)));
			}
			return 0;
		}

		@Override
		public int performWrites() throws Exception {
			for (Map<String, Object> o: objs) {
				wsids.add(wsc.saveObjects(new SaveObjectsParams()
					.withWorkspace(workspace)
					.withObjects(Arrays.asList(new ObjectSaveData()
						.withData(new UObject(o))
						.withType(TYPE)))).get(0).getE2());
			}
			return 0;
		}

		@Override
		public void cleanUp() throws Exception {
			wsc.deleteWorkspace(new WorkspaceIdentity().withWorkspace(workspace));
		}
	}
	
	public static class WorkspaceLibShockEmptySpec extends WorkspaceLibShock {
		
		public WorkspaceLibShockEmptySpec() throws Exception {
			super();
			type = SIMPLE_TYPEDEF;
		}
	}
	
	public static class WorkspaceLibShock extends AbstractReadWriteTest {

		private static final WorkspaceUser foo = new WorkspaceUser("foo");
		protected TypeDefId type;
		
		private Workspace ws;
		private int writes;
		@SuppressWarnings("unused")
		private int id;
		final List<String> wsids = new LinkedList<String>();
		private List<JsonNode> objs = new LinkedList<JsonNode>();
		private String workspace;
		
		public WorkspaceLibShock() throws Exception {
			super();
			ws = new Workspace(new MongoWorkspaceDB(MONGO_HOST, MONGO_DB, password, TempFilesManager.forTests()),
					new DefaultReferenceParser());
			workspace = "SupahFake" + new String("" + Math.random()).substring(2)
					.replace("-", ""); //in case it's E-X
			ws.createWorkspace(foo, workspace, false, null, null);
			type = TYPEDEF;
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
		public int performReads() throws Exception {
			for (String id: wsids) {
				ws.getObjects(foo, Arrays.asList(
						new ObjectIdentifier(new WorkspaceIdentifier(workspace), id)));
			}
			return 0;
		}

		@Override
		public int performWrites() throws Exception {
			for (JsonNode o: objs) {
				wsids.add(ws.saveObjects(foo, new WorkspaceIdentifier(workspace),
						Arrays.asList(new WorkspaceSaveObject(
								o, type, null, new Provenance(foo), false)))
						.get(0).getObjectName());
			}
			return 0;
		}

		@Override
		public void cleanUp() throws Exception {
			ws.setWorkspaceDeleted(foo, new WorkspaceIdentifier(workspace), true);
		}
	}
	
	public static class ShockBackendOnly extends AbstractReadWriteTest {
		
		private ShockBackend sb;
		@SuppressWarnings("unused")
		private int id;
		public final List<MD5> md5s = new LinkedList<MD5>();
		
		public void initialize(int writes, int id) throws Exception {
			Random rand = new Random();
			this.sb = new ShockBackend(GetMongoDB.getDB(MONGO_HOST, MONGO_DB),
					"temp_shock_node_map", shockURL, token.getUserName(), password);
			for (int i = 0; i < writes; i++) {
				byte[] r = new byte[16]; //128 bit
				rand.nextBytes(r);
				md5s.add(new MD5(Hex.encodeHexString(r)));
			}
			this.id = id;
		}
		
		@Override
		public int performReads() throws Exception {
			for (MD5 md5: md5s) {
				sb.getBlob(md5, ByteArrayFileCacheManager.forTests());
			}
			return 0;
		}

		@Override
		public int performWrites() throws Exception {
			for (MD5 md5: md5s) {
				sb.saveBlob(md5, treeToWritable(jsonData), true);
			}
			return 0;
		}

		@Override
		public void cleanUp() throws Exception {
			sb.removeAllBlobs();
		}
	}
	
	public static class GridFSBackendOnly extends AbstractReadWriteTest {
		
		private GridFSBackend gfsb;
		@SuppressWarnings("unused")
		private int id;
		public final List<MD5> md5s = new LinkedList<MD5>();
		
		public void initialize(int writes, int id) throws Exception {
			Random rand = new Random();
			this.gfsb = new GridFSBackend(GetMongoDB.getDB(MONGO_HOST, MONGO_DB));
			for (int i = 0; i < writes; i++) {
				byte[] r = new byte[16]; //128 bit
				rand.nextBytes(r);
				md5s.add(new MD5(Hex.encodeHexString(r)));
			}
			this.id = id;
		}
		
		@Override
		public int performReads() throws Exception {
			for (MD5 md5: md5s) {
				gfsb.getBlob(md5, ByteArrayFileCacheManager.forTests());
			}
			return 0;
		}

		@Override
		public int performWrites() throws Exception {
			for (MD5 md5: md5s) {
				gfsb.saveBlob(md5, treeToWritable(jsonData), true);
			}
			return 0;
		}

		@Override
		public void cleanUp() throws Exception {
			//Database will be deleted next time anyway
		}
	}
	
	// use builder in future, not really worth the time here
	public static class ShockClient extends AbstractReadWriteTest {
		
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
		public int performReads() throws Exception {
			for (ShockNode sn: nodes) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				sn.getFile(baos);
				baos.toByteArray();
			}
			return 0;
		}

		@Override
		public int performWrites() throws Exception {
			for (int i = 0; i < this.writes; i++) {
				nodes.add(bsc.addNode(new ByteArrayInputStream(data), "foo", "UTF-8"));
			}
			return 0;
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
		private int errors = 0;
		
		public AbstractReadWriteTest() {}
		
		public void doReads() {
			thread = new Thread() {
				
				@Override
				public void run() {
					try {
						errors += performReads();
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
						errors += performWrites();
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
		
		public int getErrorCount() {
			return errors;
		}
		
		public abstract void initialize(int writes, int id) throws Exception;
		public abstract int performReads() throws Exception;
		public abstract int performWrites() throws Exception;
		public abstract void cleanUp() throws Exception;
	}
	
	private static class Perf {
		
		public final double writeSec;
		public final double readSec;
		public final int errors;
		
		public Perf(double writeNanoSec, double readNanoSec, int errors) {
			writeSec = ((double) writeNanoSec) / 1000000000.0;
			readSec = ((double) readNanoSec) / 1000000000.0;
			this.errors = errors;
		}
	}
}
