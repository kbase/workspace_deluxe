package performance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;

public class TimeReadWrite {
	
	public static void main(String[] args) throws Exception {
		int writes = Integer.valueOf(args[0]);
		String file = args[1];
		String user = args[2];
		String pwd = args[3];
		String shockUrl = args[4];
		String workspaceUrl = args[5];
		new TimeReadWrite(writes, file, user, pwd, shockUrl, workspaceUrl,
				Arrays.asList(1, 2, 3, 4, 5, 7, 10, 16, 20));
	}

	private final int writes;
	private final byte[] data;
	private final BasicShockClient bsc;
	private final List<Integer> threads;
	
	public TimeReadWrite(int writes, String file, String user, String pwd,
			String shockURL, String workspaceURL, List<Integer> threadCounts)
					throws Exception {
		System.out.println("logging in " + user);
		AuthToken t = AuthService.login(user, pwd).getToken();
		bsc = new BasicShockClient(new URL(shockURL), t);
		threads = threadCounts;
		this.writes = writes;
		data = Files.readAllBytes(Paths.get(file));
		System.out.println(String.format(
				"Writing a file %s times, then reading it back %s times",
				writes, writes));
		System.out.println(String.format("file size: %,dB", data.length));
		Map<Integer, Perf> results = new HashMap<Integer, TimeReadWrite.Perf>();
		for (Integer threadCount: threads) {
			System.out.println("Measuring performance with " + threadCount + " threads");
			results.put(threadCount, measurePerformance(threadCount));
		}
		System.out.println("Threads\twrite (s)\twrite (MBps)\tread (s)\tread (MBps)");
		List<Integer> sorted = new ArrayList<Integer>(results.keySet());
		Collections.sort(sorted);
		for (Integer i: sorted) {
			Perf p = results.get(i);
			System.out.println(String.format("%d\t%,.4f\t\t%,.3f\t\t%,.4f\t\t%,.3f",
					i, p.writeSec, p.writeBPS, p.readSec, p.readBPS));
		}
	}
	
	private Perf measurePerformance(int threads) throws Exception {
		WriteThread[] writethreads = new WriteThread[threads];
		boolean hasMod = writes % threads != 0;
		int minWrites = writes / threads;
		int remainder = writes; //lazy bastard
		List<Integer> threadDist = new LinkedList<Integer>();
		for (int i = 0; i < threads; i++) {
			if (i + 1 == threads) {
				writethreads[i] = new WriteThread(remainder);
				threadDist.add(remainder);
			} else if (hasMod && i % 2 == 1) {
				writethreads[i] = new WriteThread(minWrites + 1);
				remainder -= minWrites + 1;
				threadDist.add(minWrites + 1);
			} else {
				writethreads[i] = new WriteThread(minWrites);
				remainder -= minWrites;
				threadDist.add(minWrites);
			}
		}
		System.out.println("Thread distribution: " + threadDist);
		long start = System.nanoTime();
		for (int i = 0; i < threads; i++) {
			writethreads[i].start();
		}
		for (int i = 0; i < threads; i++) {
			writethreads[i].join();
		}
		List<Double> wres = summarize(writes, data.length, start, System.nanoTime());
		
		ReadThread[] readthreads = new ReadThread[threads];
		for (int i = 0; i < threads; i++) {
			readthreads[i] = new ReadThread(writethreads[i].nodes);
		}
		start = System.nanoTime();
		for (int i = 0; i < threads; i++) {
			readthreads[i].start();
		}
		for (int i = 0; i < threads; i++) {
			readthreads[i].join();
		}
		List<Double> rres = summarize(writes, data.length, start, System.nanoTime());
		
		for (int i = 0; i < threads; i++) {
			for (ShockNode sn: writethreads[i].nodes) {
				sn.delete();
			}
		}
		return new Perf(wres.get(0), wres.get(1), rres.get(0), rres.get(1));
	}
	
	private static List<Double> summarize(int writes, int bytes, long start, long stop) {
		double elapsedsec = (stop - start) / 1000000000.0;
		double mbps = (double) writes * (double) bytes / elapsedsec / 1000000.0;
		return Arrays.asList(elapsedsec, mbps);
	}
	
	private class WriteThread extends Thread {
		
		public final int writes;
		public final List<ShockNode> nodes = new LinkedList<ShockNode>();
		
		public WriteThread(int writes) {
			this.writes = writes;
		}
		
		@Override
		public void run() {
			try {
				for (int i = 0; i < writes; i++) {
					nodes.add(bsc.addNode(new ByteArrayInputStream(data), "foo", "UTF-8"));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private class ReadThread extends Thread {
		
		public final List<ShockNode> nodes;
		
		public ReadThread(List<ShockNode> nodes) {
			this.nodes = nodes;
		}
		
		@Override
		public void run() {
			try {
				for (ShockNode sn: nodes) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					sn.getFile(baos);
					baos.toByteArray();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private class Perf {
		
		public final double writeSec;
		public final double writeBPS;
		public final double readSec;
		public final double readBPS;
		
		public Perf(double writeSec, double writeBPS, double readSec,
				double readBPS) {
			super();
			this.writeSec = writeSec;
			this.writeBPS = writeBPS;
			this.readSec = readSec;
			this.readBPS = readBPS;
		}
		
	}

}
