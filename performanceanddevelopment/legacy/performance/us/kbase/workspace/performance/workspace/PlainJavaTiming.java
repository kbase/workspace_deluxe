package legacy.performance.us.kbase.workspace.performance.workspace;

import static legacy.performance.us.kbase.workspace.performance.workspace.Common.getMD5s;
import static legacy.performance.us.kbase.workspace.performance.workspace.Common.getShockNodes;
import static legacy.performance.us.kbase.workspace.performance.workspace.Common.printStats;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;

public class PlainJavaTiming {
	private static final String WORKSPACE = "TestObjs";
	private static final String WS_DB = "ws_test";
	
	private static final String SHOCK_URL = "http://localhost:7044";
	
	public static void main(String args[]) throws Exception {
		final String token = args[0];
		if (token == null || token.trim().isEmpty()) {
			throw new IllegalArgumentException("no token in args");
		}
		
		@SuppressWarnings("resource")
		final MongoClient mc = new MongoClient();
		final MongoDatabase db = mc.getDatabase(WS_DB);
		final List<String> md5s = getMD5s(db, WORKSPACE);
		
		final List<String> nodes = getShockNodes(db, md5s);
		
		final URL shockURL = new URL(SHOCK_URL);
		final List<Long> shockTimes = new LinkedList<>();
		final long startShock = System.nanoTime();
		int count = 1;
		for (final String node: nodes) {
			final URL nodeURL = new URL(shockURL.toString() + "/node/" + node + "/?download");
			final long startNode = System.nanoTime();
			final URLConnection con = nodeURL.openConnection();
			con.setRequestProperty("Authorization", "OAuth " + token);
			final InputStream io = con.getInputStream();
			@SuppressWarnings("unused")
			final String s = IOUtils.toString(io);
			shockTimes.add(System.nanoTime() - startNode);
			if (count % 10000 == 0) {
				System.out.println(count);
			}
			count++;
		}
		System.out.println("Time to get objects: " +
				(System.nanoTime() - startShock) / 1000000000.0);
		printStats(shockTimes);
	}
}
