package us.kbase.workspace.performance.workspace;

import java.io.File;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

import us.kbase.auth.AuthService;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.mongo.Fields;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.ShockBlobStore;
import us.kbase.workspace.kbase.InitWorkspaceServer;
import us.kbase.workspace.kbase.TokenProvider;

public class ShockBackendTiming {

	private static final String WORKSPACE = "TestObjs";
	private static final String WS_DB = "ws_test";
	
	private static final String SHOCK_URL = "http://localhost:7044";
	
	public static void main(final String[] args) throws Exception {
		final String token = args[0];
		if (token == null || token.trim().isEmpty()) {
			throw new IllegalArgumentException("no token in args");
		}
		
		final MongoClient mc = new MongoClient();
		final DB db = mc.getDB(WS_DB);
		final DBObject ws = db.getCollection(MongoWorkspaceDB.COL_WORKSPACES).findOne(
				new BasicDBObject(Fields.WS_NAME, WORKSPACE));
		final long id = (long) ws.get(Fields.WS_ID);
		final DBObject sort = new BasicDBObject(Fields.VER_WS_ID, 1);
		sort.put(Fields.VER_ID, 1);
		final List<String> md5s = new LinkedList<>();
		final long startvers = System.nanoTime();
		for (final DBObject dbo: db.getCollection(MongoWorkspaceDB.COL_WORKSPACE_VERS)
				.find(new BasicDBObject(Fields.VER_WS_ID, id)).sort(sort)) {
			md5s.add((String) dbo.get(Fields.VER_CHKSUM));
		}
		System.out.println("time to get md5s: " + (System.nanoTime() - startvers) / 1000000000.0);
		final ShockBlobStore shock = new ShockBlobStore(
				db.getCollection(InitWorkspaceServer.COL_SHOCK_NODES),
				new URL(SHOCK_URL), new TokenProvider(AuthService.validateToken(token)));
		final ByteArrayFileCacheManager man = new ByteArrayFileCacheManager(2000000000, 2000000000,
				new TempFilesManager(new File("temp_ShockBackendTiming")));
		
		final List<Long> shocktimes = new LinkedList<>();
		final long startShock = System.nanoTime();
		int count = 1;
		for (final String md5: md5s) {
			final long startNode = System.nanoTime();
			shock.getBlob(new MD5(md5), man); // reads into memory
			shocktimes.add(System.nanoTime() - startNode);
			if (count % 10000 == 0) {
				System.out.println(count);
			}
			count++;
		}
		System.out.println("time to get shock nodes: " +
				(System.nanoTime() - startShock) / 1000000000.0);
		System.out.println("N: " + shocktimes.size());
		long sum = 0;
		for (final Long l: shocktimes) {
			sum += l;
		}
		final double mean = (sum / (double) shocktimes.size()) / 1000000000.0;
		System.out.println("Mean: " + mean);
		double ss = 0;
		for (final Long l: shocktimes) {
			ss += Math.pow((l - mean), 2);
		}
		final double stddev = Math.pow(ss / (shocktimes.size() - 1), 0.5) / 1000000000.0;
		System.out.println("Stddev (sample): " + stddev);
	}
	
}
