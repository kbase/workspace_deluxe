package us.kbase.workspace.performance.workspace;

import static us.kbase.workspace.performance.workspace.Common.getMD5s;
import static us.kbase.workspace.performance.workspace.Common.getObjects;
import static us.kbase.workspace.performance.workspace.Common.printStats;

import java.util.List;

import com.mongodb.DB;
import com.mongodb.MongoClient;

import us.kbase.workspace.database.mongo.BlobStore;
import us.kbase.workspace.database.mongo.GridFSBlobStore;

public class GridFSBackendTiming {

	private static final String WORKSPACE = "TestObjs";
	private static final String WS_DB = "ws_test_gfs";
	
	public static void main(final String[] args) throws Exception {
		final MongoClient mc = new MongoClient();
		final DB db = mc.getDB(WS_DB);
		final List<String> md5s = getMD5s(db, WORKSPACE);
		
		final BlobStore blob = new GridFSBlobStore(db);
		final List<Long> shocktimes = getObjects(blob, md5s, md5s.size());
		printStats(shocktimes);
	}
	
}
