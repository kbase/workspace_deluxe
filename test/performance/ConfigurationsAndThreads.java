package performance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
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
import java.util.UUID;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.nocrala.tools.texttablefmt.CellStyle;
import org.nocrala.tools.texttablefmt.Table;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DB;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UObject;
import us.kbase.common.test.TestCommon;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;
import us.kbase.typedobj.core.LocalTypeProvider;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.ShockBlobStore;

/* DO NOT run these tests on production workspaces.
 * WARNING: extensive changes have been made to the code. Read through the code
 * before using, probably doesn't work 
 * correctly any more. See TODOs
 * 
 * Removed all references to the 0.0.5 Perl workspace version 2016/01/23.
 * 
 * Note you must make the SupahFakeKBGA.Genome type available in the workspace
 * before running these tests. 
 */
public class ConfigurationsAndThreads {
	
	public static void main(String[] args) throws Exception {
		int writes = Integer.valueOf(args[0]);
		String user = args[1];
		String pwd = args[2];
		timeReadWrite(writes, user, pwd, "http://localhost:7044", "http://localhost:7058",
				Arrays.asList(
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
	private static TempFilesManager tfm;
	
	private static URL shockURL;
	private static URL workspace0_1_0URL;
	
	@SuppressWarnings({ "unchecked"})
	public static void timeReadWrite(int writes, String user, String pwd, String shockurl,
			String workspaceURL, List<String> configs, List<Integer> threadCounts)
					throws Exception {
		
		
		System.out.println(
				"Timing read/write against shock and the workspace service");
		System.out.println("Shock url: " + shockurl);
		System.out.println("Workspace url: " + workspaceURL);
		System.out.println("logging in " + user);
		
		token = AuthService.login(user, pwd).getToken();
		shockURL = new URL(shockurl);
		workspace0_1_0URL = new URL(workspaceURL);
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
		tfm = new TempFilesManager(
				new File(TestCommon.getTempDir()));
		//need to redo set up if this is used again
//		us.kbase.workspace.test.WorkspaceTestCommonDeprecated.destroyAndSetupDB(
//				1, WorkspaceTestCommon.SHOCK, user, null);
		//NOTE this setup is just to make it compile, not tested yet
		final TypeDefinitionDB typeDefDB = new TypeDefinitionDB(
				new MongoTypeStorage(GetMongoDB.getDB(MONGO_HOST, TYPE_DB)));
		
		Types types = new Types(typeDefDB);
		WorkspaceUser foo = new WorkspaceUser("foo");
		types.requestModuleRegistration(foo, MODULE);
		types.resolveModuleRegistration(MODULE, true);
		types.compileNewTypeSpec(foo, spec, Arrays.asList(M_TYPE), null, null, false, null);
		types.releaseTypes(foo, MODULE);
		
		types.requestModuleRegistration(foo, SIMPLE_MODULE);
		types.resolveModuleRegistration(SIMPLE_MODULE, true);
		types.compileNewTypeSpec(foo, SIMPLE_SPEC,
				Arrays.asList(SIMPLE_M_TYPE), null, null, false, null);
		types.releaseTypes(foo, SIMPLE_MODULE);
		
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
	
	private static InputStream treeToWritable(final JsonNode value) {
		//NOTE no idea if this was a good change or not, just made it compile
		return IOUtils.toInputStream(value.toString());
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
			wsc.setIsInsecureHttpConnectionAllowed(true);
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
				@SuppressWarnings({ "deprecation", "unused" })
				List<ObjectData> objects = wsc.getObjects(Arrays.asList(new ObjectIdentity()
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
			//NOTE check this still works NOTE2 it doesn't
			DB db = GetMongoDB.getDB(MONGO_HOST, MONGO_DB);
			final TypeDefinitionDB typeDefDB = new TypeDefinitionDB(
					new MongoTypeStorage(GetMongoDB.getDB(MONGO_HOST, TYPE_DB)));
			TypedObjectValidator val = new TypedObjectValidator(
					new LocalTypeProvider(typeDefDB));
			MongoWorkspaceDB mwdb = new MongoWorkspaceDB(db,
					new ShockBlobStore(db.getCollection("shock_map"), shockURL,
							AuthService.validateToken("foo")), tfm);
			ws = new Workspace(mwdb, new ResourceUsageConfigurationBuilder().build(), val);
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
				final IdReferenceHandlerSetFactory fac = new IdReferenceHandlerSetFactory(1);
//				fac.addFactory(ws.getHandlerFactory(foo));
				wsids.add(ws.saveObjects(foo, new WorkspaceIdentifier(workspace),
						Arrays.asList(new WorkspaceSaveObject(
								//added obj name when autonaming removed
								new ObjectIDNoWSNoVer(UUID.randomUUID().toString()
										.replace("-", "")),
								o, type, null, new Provenance(foo), false)), fac)
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
		
		private ShockBlobStore sb;
		@SuppressWarnings("unused")
		private int id;
		public final List<MD5> md5s = new LinkedList<MD5>();
		
		public void initialize(int writes, int id) throws Exception {
			Random rand = new Random();
			this.sb = new ShockBlobStore(GetMongoDB.getDB(MONGO_HOST, MONGO_DB, 0, 0).getCollection(
					"temp_shock_node_map"), shockURL, token);
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
				sb.getBlob(md5,
						new ByteArrayFileCacheManager(16000000, 2000000000L, tfm));
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
		
		private GridFSBlobStore gfsb;
		@SuppressWarnings("unused")
		private int id;
		public final List<MD5> md5s = new LinkedList<MD5>();
		
		public void initialize(int writes, int id) throws Exception {
			Random rand = new Random();
			this.gfsb = new GridFSBlobStore(GetMongoDB.getDB(MONGO_HOST, MONGO_DB));
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
				gfsb.getBlob(md5,
						new ByteArrayFileCacheManager(16000000, 2000000000L, tfm));
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
