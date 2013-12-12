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
	
	//TODO bypass JSONRPC, use ws directly
	
	public static void main(String[] args) throws Exception {
		int writes = Integer.valueOf(args[0]);
		String user = args[1];
		String pwd = args[2];
		timeReadWrite(writes, user, pwd, "http://localhost:7044",
				"http://localhost:7058", Arrays.asList("Shock", "WorkspaceSingle"),
				Arrays.asList(1, 2, 3, 4, 5, 7, 10, 16, 20));
	}

	private static final Map<String, Class<? extends ReadWriteAbstractThread>> configMap =
			new HashMap<String, Class<? extends ReadWriteAbstractThread>>();
	static {
		configMap.put("Shock", ShockThread.class);
		configMap.put("WorkspaceSingle", WorkspaceJsonRPCThread.class);
	}
	
	private static final String TYPE = "SupahFakeKBGA.Genome";
	private static final ObjectMapper MAP = new ObjectMapper(); 
	
	private static int writes; //TODO local
	private static byte[] data;
	private static BasicShockClient bsc;
	private static WorkspaceClient wsc;
	private static List<Integer> threads; //TODO local
	
	public static void timeReadWrite(int methwrites, String user, String pwd, String shockURL,
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
//		workspace = "SupahFake" + new String("" + Math.random()).substring(2);
//		try {
//			wsc.createWorkspace(new CreateWorkspaceParams().withWorkspace(workspace));
//		} catch (ServerException se) {
//			//probably just created already
//		}
		threads = threadCounts;
		writes = methwrites;
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
			for (Integer threadCount: threads) {
				System.out.println("Measuring config " + config + " performance with "
						+ threadCount + " threads");
				results.get(config).put(threadCount, measurePerformance(
						threadCount, configMap.get(config)));
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
				tbl.addCell(String.format("%,.4f", p.shockWriteSec));
				tbl.addCell(String.format("%,.3f", p.shockWriteBPS));
				tbl.addCell(String.format("%,.4f", p.shockReadSec));
				tbl.addCell(String.format("%,.3f", p.shockReadBPS));
			}
		}
		System.out.println(tbl.render());
		
//		System.out.println("Shock");
//		System.out.println("Threads\twrite (s)\twrite (MBps)\tread (s)\tread (MBps)");
//		List<Integer> sorted = new ArrayList<Integer>(results.keySet());
//		Collections.sort(sorted);
//		for (Integer i: sorted) {
//			Perf p = results.get(i);
//			System.out.println(String.format("%d\t%,.4f\t\t%,.3f\t\t%,.4f\t\t%,.3f",
//					i, p.shockWriteSec, p.shockWriteBPS, p.shockReadSec, p.shockReadBPS));
//		}
//		System.out.println("Workspace");
//		System.out.println("Threads\twrite (s)\twrite (MBps)\tread (s)\tread (MBps)");
//		for (Integer i: sorted) {
//			Perf p = results.get(i);
//			System.out.println(String.format("%d\t%,.4f\t\t%,.3f\t\t%,.4f\t\t%,.3f",
//					i, p.wsWriteSec, p.wsWriteBPS, p.wsReadSec, p.wsReadBPS));
//		}
	}
	
	private static Perf measurePerformance(int threads, Class<? extends ReadWriteAbstractThread> clazz)
			throws Exception {
		ReadWriteAbstractThread[] rwthreads = new ReadWriteAbstractThread[threads]; 
//		WriteThread[] shockWrites = new WriteThread[threads];
//		WriteThread[] wsWrites = new WriteThread[threads];
		boolean hasMod = writes % threads != 0;
		int minWrites = writes / threads;
		int pos = 0;
		List<Integer> threadDist = new LinkedList<Integer>();
		for (int i = 0; i < threads; i++) {
			if (i + 1 == threads) {
				int threadSize = writes - pos;
				rwthreads[i] = clazz.newInstance();
				rwthreads[i].setWrites(threadSize);
//				shockWrites[i] = new WriteThread(threadSize, i);
//				wsWrites[i] = new WriteThread(objdata.subList(pos, objdata.size()), i);
				threadDist.add(threadSize);
			} else if (hasMod && i % 2 == 1) {
				rwthreads[i] = clazz.newInstance();
				rwthreads[i].setWrites(minWrites + 1);
//				shockWrites[i] = new WriteThread(minWrites + 1, i);
//				wsWrites[i] = new WriteThread(objdata.subList(pos, pos + minWrites + 1), i);
				pos += minWrites + 1;
				threadDist.add(minWrites + 1);
			} else {
				rwthreads[i] = clazz.newInstance();
				rwthreads[i].setWrites(minWrites);
//				shockWrites[i] = new WriteThread(minWrites, i);
//				wsWrites[i] = new WriteThread(objdata.subList(pos, pos + minWrites), i);
				pos += minWrites;
				threadDist.add(minWrites);
			}
		}
		System.out.println("Thread distribution: " + threadDist);
		//Shock
		long start = System.nanoTime();
		for (int i = 0; i < threads; i++) {
			rwthreads[i].doWrites();
		}
		for (int i = 0; i < threads; i++) {
			rwthreads[i].join();
		}
		List<Double> shockWriteRes = summarize(writes, data.length, start, System.nanoTime());
		
//		ReadThread[] shockReads = new ReadThread[threads];
//		for (int i = 0; i < threads; i++) {
//			shockReads[i] = new ReadThread(shockWrites[i].nodes, null);
//		}
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
//			for (ShockNode sn: shockWrites[i].nodes) {
//				sn.delete();
//			}
		}
		
		//Workspace
//		start = System.nanoTime();
//		for (int i = 0; i < threads; i++) {
//			wsWrites[i].start();
//		}
//		for (int i = 0; i < threads; i++) {
//			wsWrites[i].join();
//		}
//		List<Double> wsWriteRes = summarize(writes, data.length, start, System.nanoTime());
//
//		ReadThread[] wsReads = new ReadThread[threads];
//		for (int i = 0; i < threads; i++) {
//			wsReads[i] = new ReadThread(null, wsWrites[i].wsids);
//		}
//		start = System.nanoTime();
//		for (int i = 0; i < threads; i++) {
//			wsReads[i].start();
//		}
//		for (int i = 0; i < threads; i++) {
//			wsReads[i].join();
//		}
//		List<Double> wsReadRes = summarize(writes, data.length, start, System.nanoTime());
		
		return new Perf(shockWriteRes.get(0), shockWriteRes.get(1),
				shockReadRes.get(0), shockReadRes.get(1));//, wsWriteRes.get(0),
//				wsWriteRes.get(1), wsReadRes.get(0), wsReadRes.get(1));
	}
	
	//TODO just record the time, summarize when building table
	private static List<Double> summarize(int writes, int bytes, long start, long stop) {
		double elapsedsec = (stop - start) / 1000000000.0;
		double mbps = (double) writes * (double) bytes / elapsedsec / 1000000.0;
		return Arrays.asList(elapsedsec, mbps);
	}
	
//	private class WriteThread extends Thread {
//		
//		public final int writes;
//		public final List<ShockNode> nodes = new LinkedList<ShockNode>();
//		final List<String> wsids = new LinkedList<String>();
//		private List<Map<String,Object>> objs;
//		
//		public WriteThread(int writes, int id) {
//			this.writes = writes;
//			this.objs = null;
////			printID(id);
//		}
//		
//		public WriteThread(List<Map<String, Object>> writes, int id) {
//			this.writes = writes.size();
//			this.objs = writes;
////			printID(id);
//		}
//		
//		@SuppressWarnings("unused")
//		private void printID(int id) {
//			System.out.println(String.format("Creating thread id %s with %s and %s writes",
//					id, objs == null ? "no objects, shock thread," : (objs.size() + " objects"), writes));
//		}
//		
//		@Override
//		public void run() {
//			try {
//				if (objs != null) {
//					for (Map<String, Object> o: objs) {
//						wsids.add(wsc.saveObjects(new SaveObjectsParams()
//							.withWorkspace(workspace)
//							.withObjects(Arrays.asList(new ObjectSaveData()
//								.withData(new UObject(o))
//								.withType(TYPE)))).get(0).getE2());
//					}
//				} else {
//					for (int i = 0; i < writes; i++) {
//						nodes.add(bsc.addNode(new ByteArrayInputStream(data), "foo", "UTF-8"));
//					}
//				}
//			} catch (Exception e) {
//				if (e instanceof ServerException) {
//					System.out.println(((ServerException) e).getData());
//				}
//			}
//		}
//	}
//	
//	private class ReadThread extends Thread {
//		
//		public final List<ShockNode> nodes;
//		private List<String> wsids;
//		
//		public ReadThread(List<ShockNode> nodes, List<String> wsids) {
//			this.nodes = nodes;
//			this.wsids = wsids;
//		}
//		
//		@Override
//		public void run() {
//			try {
//				if (wsids != null) {
//					for (String id: wsids) {
//						wsc.getObjects(Arrays.asList(new ObjectIdentity()
//							.withWorkspace(workspace).withName(id)));
//					}
//				} else {
//					for (ShockNode sn: nodes) {
//						ByteArrayOutputStream baos = new ByteArrayOutputStream();
//						sn.getFile(baos);
//						baos.toByteArray();
//					}
//				}
//			} catch (Exception e) {
//				e.printStackTrace();
//				if (e instanceof ServerException) {
//					System.out.println(((ServerException) e).getData());
//				}
//			}
//		}
//	}
	
	public static class WorkspaceJsonRPCThread extends ReadWriteAbstractThread {

		private int writes;
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
		public void setWrites(int writes) throws Exception {
			this.writes = writes;
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
	
	public static class ShockThread extends ReadWriteAbstractThread {
		
		private int writes;
		public final List<ShockNode> nodes = new LinkedList<ShockNode>();
		
		public void setWrites(int writes) {
			this.writes = writes;
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
	
	public static abstract class ReadWriteAbstractThread extends Thread {
		
		private boolean read;
		
		public ReadWriteAbstractThread() {}
		
		public void doReads() {
			read = true;
			run();
		}
		
		public void doWrites() {
			read = false;
			run();
		}
		
		public abstract void setWrites(int writes) throws Exception;
		public abstract void performReads() throws Exception;
		public abstract void performWrites() throws Exception;
		public abstract void cleanUp() throws Exception;
		
		@Override
		public void run() {
			try {
				if (read) {
					performReads();
				} else {
					performWrites();
				}
			} catch (Exception e) {
				e.printStackTrace();
				if (e instanceof ServerException) {
					System.out.println(((ServerException) e).getData());
				}
			}
		}
	}
	
	private static class Perf {
		
		public final double shockWriteSec;
		public final double shockWriteBPS;
		public final double shockReadSec;
		public final double shockReadBPS;
//		public final double wsWriteSec;
//		public final double wsWriteBPS;
//		public final double wsReadSec;
//		public final double wsReadBPS;
		
		public Perf(double shockWriteSec, double shockWriteBPS, double shockReadSec,
				double shockReadBPS) {//, double wsWriteSec, double wsWriteBPS,
//				double wsReadSec, double wsReadBPS) {
			super();
			this.shockWriteSec = shockWriteSec;
			this.shockWriteBPS = shockWriteBPS;
			this.shockReadSec = shockReadSec;
			this.shockReadBPS = shockReadBPS;
//			this.wsWriteSec = wsWriteSec;
//			this.wsWriteBPS = wsWriteBPS;
//			this.wsReadSec = wsReadSec;
//			this.wsReadBPS = wsReadBPS;
		}
		
	}

}
