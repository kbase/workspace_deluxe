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
import org.nocrala.tools.texttablefmt.Table;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UObject;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;

/* DO NOT run these tests on the production workspace.
 * 
 * Note you must make the SupahFakeKBGA.Genome type available in the workspace
 * before running these tests. 
 */
public class TimeReadWrite {
	
	//TODO doesn't seem to be threaded anymore? Check
	
	//TODO bypass JSONRPC, use ws directly
	
	public static void main(String[] args) throws Exception {
		int writes = Integer.valueOf(args[0]);
		String user = args[1];
		String pwd = args[2];
		timeReadWrite(writes, user, pwd, "http://localhost:7044",
				"http://localhost:7058", Arrays.asList("Shock", "WorkspaceSingle"),
				Arrays.asList(16)); //1, 2, 3, 4, 5, 7, 10, 16, 20));
	}

	private static final Map<String, Class<? extends ReadWriteAbstractThread>> configMap =
			new HashMap<String, Class<? extends ReadWriteAbstractThread>>();
	static {
		configMap.put("Shock", ShockThread.class);
		configMap.put("WorkspaceSingle", WorkspaceJsonRPCThread.class);
	}
	
	private static final String TYPE = "SupahFakeKBGA.Genome";
	private static final ObjectMapper MAP = new ObjectMapper(); 
	
	private static byte[] data;
	private static BasicShockClient bsc;
	private static WorkspaceClient wsc;
	
	public static void timeReadWrite(int writes, String user, String pwd, String shockURL,
			String workspaceURL,  List<String> configs, List<Integer> threadCounts)
					throws Exception {
		System.out.println(
				"Timing read/write against shock and the workspace service");
		System.out.println("Shock url: " + shockURL);
		System.out.println("Workspace url: " + workspaceURL);
		System.out.println("logging in " + user);
		AuthToken t = AuthService.login(user, pwd).getToken();
		bsc = new BasicShockClient(new URL(shockURL), t);
		wsc = new WorkspaceClient(new URL(workspaceURL), t);
		wsc.setAuthAllowedForHttp(true);
		data = IOUtils.toByteArray(TimeReadWrite.class.getResourceAsStream("83333.2.txt"));
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
		
		Table tbl = new Table(6);
		tbl.addCell("Configuration");
		tbl.addCell("Threads");
		tbl.addCell("write (s)");
		tbl.addCell("write (MBps)");
		tbl.addCell("read (s)");
		tbl.addCell("read (MBps)");
		for (String config: results.keySet()) {
			List<Integer> sorted = new ArrayList<Integer>(results.get(config).keySet());
			Collections.sort(sorted);
			for (Integer threads: sorted) {
				Perf p = results.get(config).get(threads);
				tbl.addCell(config);
				tbl.addCell("" + threads);
				tbl.addCell(String.format("%,.4f", p.writeSec));
				tbl.addCell(String.format("%,.3f", p.writeBPS));
				tbl.addCell(String.format("%,.4f", p.readSec));
				tbl.addCell(String.format("%,.3f", p.readBPS));
			}
		}
		System.out.println(tbl.render());
		
	}
	
	private static Perf measurePerformance(int writes, int threads,
			Class<? extends ReadWriteAbstractThread> clazz)
			throws Exception {
		ReadWriteAbstractThread[] rwthreads = new ReadWriteAbstractThread[threads]; 
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
			System.out.println("Starting thread " + (i + 1));
			rwthreads[i].doWrites();
			System.out.println("Started thread " + (i + 1));
		}
		for (int i = 0; i < threads; i++) {
			System.out.println("Joining thread " + (i + 1));
			rwthreads[i].join();
			System.out.println("Joined thread " + (i + 1));
		}
		List<Double> shockWriteRes = summarize(writes, data.length, start, System.nanoTime());
		
		start = System.nanoTime();
		for (int i = 0; i < threads; i++) {
			rwthreads[i].doReads();
		}
		for (int i = 0; i < threads; i++) {
			rwthreads[i].join();
		}
		List<Double> shockReadRes = summarize(writes, data.length, start, System.nanoTime());
		
		for (int i = 0; i < threads; i++) {
			rwthreads[i].cleanUp();
		}

		return new Perf(shockWriteRes.get(0), shockWriteRes.get(1),
				shockReadRes.get(0), shockReadRes.get(1));
	}
	
	//TODO just record the time, summarize when building table
	private static List<Double> summarize(int writes, int bytes, long start, long stop) {
		double elapsedsec = (stop - start) / 1000000000.0;
		double mbps = (double) writes * (double) bytes / elapsedsec / 1000000.0;
		return Arrays.asList(elapsedsec, mbps);
	}
	
	public static class WorkspaceJsonRPCThread extends ReadWriteAbstractThread {

		private int writes;
		private int id;
		final List<String> wsids = new LinkedList<String>();
		private List<Map<String,Object>> objs = new LinkedList<Map<String,Object>>();
		private String workspace;
		
		public WorkspaceJsonRPCThread() throws Exception {
			workspace = "SupahFake" + new String("" + Math.random()).substring(2);
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
			System.out.println("Thread " + id + " starting writes");
			for (Map<String, Object> o: objs) {
				wsids.add(wsc.saveObjects(new SaveObjectsParams()
					.withWorkspace(workspace)
					.withObjects(Arrays.asList(new ObjectSaveData()
						.withData(new UObject(o))
						.withType(TYPE)))).get(0).getE2());
			}
			System.out.println("Thread " + id + " completed writes");
		}

		@Override
		public void cleanUp() throws Exception {
			wsc.deleteWorkspace(new WorkspaceIdentity().withWorkspace(workspace));
		}
	}
	
	// use builder in future, not really worth the time here
	public static class ShockThread extends ReadWriteAbstractThread {
		
		private int writes;
		private int id;
		public final List<ShockNode> nodes = new LinkedList<ShockNode>();
		
		public void initialize(int writes, int id) {
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
			System.out.println("Thread " + id + " starting writes");
			for (int i = 0; i < this.writes; i++) {
				nodes.add(bsc.addNode(new ByteArrayInputStream(data), "foo", "UTF-8"));
			}
			System.out.println("Thread " + id + " completed writes");
		}

		@Override
		public void cleanUp() throws Exception {
			for (ShockNode sn: nodes) {
				sn.delete();
			}
		}
	}
	
	public static abstract class ReadWriteAbstractThread {
		
//		private boolean read;
		private Thread thread;
		
		public ReadWriteAbstractThread() {}
		
		public void doReads() {
//			read = true;
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
//			read = false;
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
		
//		@Override
//		public void run() {
//			try {
//				if (read) {
//					performReads();
//				} else {
//					performWrites();
//				}
//			} catch (Exception e) {
//				e.printStackTrace();
//				if (e instanceof ServerException) {
//					System.out.println(((ServerException) e).getData());
//				}
//			}
//		}
	}
	
	private static class Perf {
		
		public final double writeSec;
		public final double writeBPS;
		public final double readSec;
		public final double readBPS;
		
		public Perf(double shockWriteSec, double shockWriteBPS, double shockReadSec,
				double shockReadBPS) {
			super();
			this.writeSec = shockWriteSec;
			this.writeBPS = shockWriteBPS;
			this.readSec = shockReadSec;
			this.readBPS = shockReadBPS;
		}
		
	}

}
