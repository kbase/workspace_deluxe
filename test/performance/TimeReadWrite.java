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
		new TimeReadWrite(writes, user, pwd, "http://localhost:7044",
				"http://localhost:7058",
				Arrays.asList(1, 2, 3, 4, 5, 7, 10, 16, 20));
	}

	private static final String TYPE = "SupahFakeKBGA.Genome";
	private final String workspace;
	private static final ObjectMapper MAP = new ObjectMapper(); 
	
	private final int writes;
	private final byte[] data;
	private List<Map<String, Object>> objdata;
	private final BasicShockClient bsc;
	private final WorkspaceClient wsc;
	private final List<Integer> threads;
	
	public TimeReadWrite(int writes, String user, String pwd,
			String shockURL, String workspaceURL, List<Integer> threadCounts)
					throws Exception {
		System.out.println(
				"Timing read/write against shock and the workspace service");
		System.out.println("logging in " + user);
		AuthToken t = AuthService.login(user, pwd).getToken();
		bsc = new BasicShockClient(new URL(shockURL), t);
		wsc = new WorkspaceClient(new URL(workspaceURL), t);
		wsc.setAuthAllowedForHttp(true);
		workspace = "SupahFake" + new String("" + Math.random()).substring(2);
		try {
			wsc.createWorkspace(new CreateWorkspaceParams().withWorkspace(workspace));
		} catch (ServerException se) {
			//probably just created already
		}
		threads = threadCounts;
		this.writes = writes;
		data = IOUtils.toByteArray(TimeReadWrite.class.getResourceAsStream("83333.2.txt"));
		System.out.println(String.format(
				"Writing a file %s times, then reading it back %s times",
				writes, writes));
		System.out.println(String.format("file size: %,dB", data.length));
		Map<Integer, Perf> results = new HashMap<Integer, TimeReadWrite.Perf>();
		for (Integer threadCount: threads) {
			System.out.println("Measuring performance with " + threadCount + " threads");
			results.put(threadCount, measurePerformance(threadCount));
		}
		System.out.println("Shock");
		System.out.println("Threads\twrite (s)\twrite (MBps)\tread (s)\tread (MBps)");
		List<Integer> sorted = new ArrayList<Integer>(results.keySet());
		Collections.sort(sorted);
		for (Integer i: sorted) {
			Perf p = results.get(i);
			System.out.println(String.format("%d\t%,.4f\t\t%,.3f\t\t%,.4f\t\t%,.3f",
					i, p.shockWriteSec, p.shockWriteBPS, p.shockReadSec, p.shockReadBPS));
		}
		System.out.println("Workspace");
		System.out.println("Threads\twrite (s)\twrite (MBps)\tread (s)\tread (MBps)");
		for (Integer i: sorted) {
			Perf p = results.get(i);
			System.out.println(String.format("%d\t%,.4f\t\t%,.3f\t\t%,.4f\t\t%,.3f",
					i, p.wsWriteSec, p.wsWriteBPS, p.wsReadSec, p.wsReadBPS));
		}
	}
	
	@SuppressWarnings("unchecked")
	private Perf measurePerformance(int threads) throws Exception {
		objdata = new ArrayList<Map<String, Object>>(writes);
		for (int i = 0; i < writes; i++) {
			objdata.add((Map<String, Object>) MAP.readValue(data, Map.class));
			objdata.get(i).put("fakekey", Math.random());
		}
		WriteThread[] shockWrites = new WriteThread[threads];
		WriteThread[] wsWrites = new WriteThread[threads];
		boolean hasMod = writes % threads != 0;
		int minWrites = writes / threads;
		int pos = 0;
		List<Integer> threadDist = new LinkedList<Integer>();
		for (int i = 0; i < threads; i++) {
			if (i + 1 == threads) {
				int threadSize = objdata.size() - pos;
				shockWrites[i] = new WriteThread(threadSize, i);
				wsWrites[i] = new WriteThread(objdata.subList(pos, objdata.size()), i);
				threadDist.add(threadSize);
			} else if (hasMod && i % 2 == 1) {
				shockWrites[i] = new WriteThread(minWrites + 1, i);
				wsWrites[i] = new WriteThread(objdata.subList(pos, pos + minWrites + 1), i);
				pos += minWrites + 1;
				threadDist.add(minWrites + 1);
			} else {
				shockWrites[i] = new WriteThread(minWrites, i);
				wsWrites[i] = new WriteThread(objdata.subList(pos, pos + minWrites), i);
				pos += minWrites;
				threadDist.add(minWrites);
			}
		}
		System.out.println("Thread distribution: " + threadDist);
		//Shock
		long start = System.nanoTime();
		for (int i = 0; i < threads; i++) {
			shockWrites[i].start();
		}
		for (int i = 0; i < threads; i++) {
			shockWrites[i].join();
		}
		List<Double> shockWriteRes = summarize(writes, data.length, start, System.nanoTime());
		
		ReadThread[] shockReads = new ReadThread[threads];
		for (int i = 0; i < threads; i++) {
			shockReads[i] = new ReadThread(shockWrites[i].nodes, null);
		}
		start = System.nanoTime();
		for (int i = 0; i < threads; i++) {
			shockReads[i].start();
		}
		for (int i = 0; i < threads; i++) {
			shockReads[i].join();
		}
		List<Double> shockReadRes = summarize(writes, data.length, start, System.nanoTime());
		
		for (int i = 0; i < threads; i++) {
			for (ShockNode sn: shockWrites[i].nodes) {
				sn.delete();
			}
		}
		
		//Workspace
		start = System.nanoTime();
		for (int i = 0; i < threads; i++) {
			wsWrites[i].start();
		}
		for (int i = 0; i < threads; i++) {
			wsWrites[i].join();
		}
		List<Double> wsWriteRes = summarize(writes, data.length, start, System.nanoTime());

		ReadThread[] wsReads = new ReadThread[threads];
		for (int i = 0; i < threads; i++) {
			wsReads[i] = new ReadThread(null, wsWrites[i].wsids);
		}
		start = System.nanoTime();
		for (int i = 0; i < threads; i++) {
			wsReads[i].start();
		}
		for (int i = 0; i < threads; i++) {
			wsReads[i].join();
		}
		List<Double> wsReadRes = summarize(writes, data.length, start, System.nanoTime());
		
		return new Perf(shockWriteRes.get(0), shockWriteRes.get(1),
				shockReadRes.get(0), shockReadRes.get(1), wsWriteRes.get(0),
				wsWriteRes.get(1), wsReadRes.get(0), wsReadRes.get(1));
	}
	
	private static List<Double> summarize(int writes, int bytes, long start, long stop) {
		double elapsedsec = (stop - start) / 1000000000.0;
		double mbps = (double) writes * (double) bytes / elapsedsec / 1000000.0;
		return Arrays.asList(elapsedsec, mbps);
	}
	
	private class WriteThread extends Thread {
		
		public final int writes;
		public final List<ShockNode> nodes = new LinkedList<ShockNode>();
		final List<String> wsids = new LinkedList<String>();
		private List<Map<String,Object>> objs;
		
		public WriteThread(int writes, int id) {
			this.writes = writes;
			this.objs = null;
//			printID(id);
		}
		
		public WriteThread(List<Map<String, Object>> writes, int id) {
			this.writes = writes.size();
			this.objs = writes;
//			printID(id);
		}
		
		@SuppressWarnings("unused")
		private void printID(int id) {
			System.out.println(String.format("Creating thread id %s with %s and %s writes",
					id, objs == null ? "no objects, shock thread," : (objs.size() + " objects"), writes));
		}
		
		@Override
		public void run() {
			try {
				if (objs != null) {
					for (Map<String, Object> o: objs) {
						wsids.add(wsc.saveObjects(new SaveObjectsParams()
							.withWorkspace(workspace)
							.withObjects(Arrays.asList(new ObjectSaveData()
								.withData(new UObject(o))
								.withType(TYPE)))).get(0).getE2());
					}
				} else {
					for (int i = 0; i < writes; i++) {
						nodes.add(bsc.addNode(new ByteArrayInputStream(data), "foo", "UTF-8"));
					}
				}
			} catch (Exception e) {
				if (e instanceof ServerException) {
					System.out.println(((ServerException) e).getData());
				}
			}
		}
	}
	
	private class ReadThread extends Thread {
		
		public final List<ShockNode> nodes;
		private List<String> wsids;
		
		public ReadThread(List<ShockNode> nodes, List<String> wsids) {
			this.nodes = nodes;
			this.wsids = wsids;
		}
		
		@Override
		public void run() {
			try {
				if (wsids != null) {
					for (String id: wsids) {
						wsc.getObjects(Arrays.asList(new ObjectIdentity()
							.withWorkspace(workspace).withName(id)));
					}
				} else {
					for (ShockNode sn: nodes) {
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						sn.getFile(baos);
						baos.toByteArray();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				if (e instanceof ServerException) {
					System.out.println(((ServerException) e).getData());
				}
			}
		}
	}
	
	private class Perf {
		
		public final double shockWriteSec;
		public final double shockWriteBPS;
		public final double shockReadSec;
		public final double shockReadBPS;
		public final double wsWriteSec;
		public final double wsWriteBPS;
		public final double wsReadSec;
		public final double wsReadBPS;
		
		public Perf(double shockWriteSec, double shockWriteBPS, double shockReadSec,
				double shockReadBPS, double wsWriteSec, double wsWriteBPS,
				double wsReadSec, double wsReadBPS) {
			super();
			this.shockWriteSec = shockWriteSec;
			this.shockWriteBPS = shockWriteBPS;
			this.shockReadSec = shockReadSec;
			this.shockReadBPS = shockReadBPS;
			this.wsWriteSec = wsWriteSec;
			this.wsWriteBPS = wsWriteBPS;
			this.wsReadSec = wsReadSec;
			this.wsReadBPS = wsReadBPS;
		}
		
	}

}
