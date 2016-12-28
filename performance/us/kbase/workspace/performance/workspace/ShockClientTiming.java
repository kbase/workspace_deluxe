package us.kbase.workspace.performance.workspace;

import static us.kbase.workspace.performance.workspace.Common.getMD5s;
import static us.kbase.workspace.performance.workspace.Common.printStats;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

import us.kbase.auth.AuthService;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.workspace.database.mongo.Fields;
import us.kbase.workspace.kbase.InitWorkspaceServer;

public class ShockClientTiming {
	
	private static final String WORKSPACE = "TestObjs";
	private static final String WS_DB = "ws_test";
	
	private static final String SHOCK_URL = "http://localhost:7044";
	
	public static void main(String args[]) throws Exception {
		final String token = args[0];
		if (token == null || token.trim().isEmpty()) {
			throw new IllegalArgumentException("no token in args");
		}
		
		final MongoClient mc = new MongoClient();
		final DB db = mc.getDB(WS_DB);
		final List<String> md5s = getMD5s(db, WORKSPACE);
		
		final List<String> nodes = new LinkedList<>();
		final long startNodes = System.nanoTime();
		for (final String md5: md5s) {
			final DBObject node = db.getCollection(InitWorkspaceServer.COL_SHOCK_NODES)
					.findOne(new BasicDBObject(Fields.SHOCK_CHKSUM, md5));
			nodes.add((String) node.get(Fields.SHOCK_NODE));
		}
		System.out.println("Time to get nodes: " +
				(System.nanoTime() - startNodes) / 1000000000.0);
		
		final BasicShockClient cli = new BasicShockClient(new URL(SHOCK_URL),
				AuthService.validateToken(token));
		final List<Long> shockTimes = new LinkedList<>();
		final long startShock = System.nanoTime();
		int count = 1;
		for (final String node: nodes) {
			final long startNode = System.nanoTime();
			cli.getFile(new ShockNodeId(node), new ByteArrayOutputStream());
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
