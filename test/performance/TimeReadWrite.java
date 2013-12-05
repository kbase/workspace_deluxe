package performance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;

public class TimeReadWrite {
	
	private static final int writes = 100;
	
	public static void main(String[] args) throws Exception {
		byte[] f = Files.readAllBytes(Paths.get(args[0]));
		System.out.println("bytes: " + f.length);
		System.out.println("logging in " + args[1]);
		AuthToken t = AuthService.login(args[1], args[2]).getToken();
		BasicShockClient bsc = new BasicShockClient(new URL(args[3]), t);
		List<ShockNode> nodes = new LinkedList<ShockNode>();
		System.out.println("Doing " + writes + " writes");
		long start = System.nanoTime();
		for (int i = 0; i < writes; i++) {
			nodes.add(bsc.addNode(new ByteArrayInputStream(f), "foo", "UTF-8"));
		}
		long stop = System.nanoTime();
		double elapsedsec = (stop - start) / 1000000000.0;
		double bps = (double) writes * (double) f.length / elapsedsec;
		System.out.println(String.format("Saved %d bytestreams in %,.2f sec at %,.2f bps: ",
				writes, elapsedsec, bps));

		System.out.println("Doing " + writes + " reads");
		start = System.nanoTime();
		for (ShockNode sn: nodes) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			sn.getFile(baos);
			baos.toByteArray();
		}
		stop = System.nanoTime();		
		elapsedsec = (stop - start) / 1000000000.0;
		bps = (double) writes * (double) f.length / elapsedsec;
		System.out.println(String.format("Read %d bytestreams in %,.2f sec at %,.2f bps: ",
				writes, elapsedsec, bps));

		System.out.println("Deleting nodes");
		for (ShockNode sn: nodes) {
			sn.delete();
		}
	}

}
