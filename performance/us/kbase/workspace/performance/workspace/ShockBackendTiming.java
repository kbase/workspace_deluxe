package us.kbase.workspace.performance.workspace;

import static us.kbase.workspace.performance.workspace.Common.getMD5s;
import static us.kbase.workspace.performance.workspace.Common.getObjects;
import static us.kbase.workspace.performance.workspace.Common.printStats;

import java.net.URL;
import java.util.List;

import com.mongodb.DB;
import com.mongodb.MongoClient;

import us.kbase.auth.AuthService;
import us.kbase.workspace.database.mongo.BlobStore;
import us.kbase.workspace.database.mongo.ShockBlobStore;
import us.kbase.workspace.kbase.InitWorkspaceServer;

public class ShockBackendTiming {

	private static final String WORKSPACE = "TestObjs";
	private static final String WS_DB = "ws_test";
	
	private static final String SHOCK_URL = "http://localhost:7044";
	private static final int BATCH_SIZE = 10000;
	
	public static void main(final String[] args) throws Exception {
		final String token = args[0];
		if (token == null || token.trim().isEmpty()) {
			throw new IllegalArgumentException("no token in args");
		}
		
		final MongoClient mc = new MongoClient();
		final DB db = mc.getDB(WS_DB);
		final List<String> md5s = getMD5s(db, WORKSPACE);
		
		final BlobStore blob = new ShockBlobStore(
				db.getCollection(InitWorkspaceServer.COL_SHOCK_NODES),
				new URL(SHOCK_URL), AuthService.validateToken(token));
		final List<Long> shocktimes = getObjects(blob, md5s, BATCH_SIZE);
		printStats(shocktimes);
	}
}
