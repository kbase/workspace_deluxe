package us.kbase.workspace.performance.workspace;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;

import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.mongo.BlobStore;
import us.kbase.workspace.database.mongo.CollectionNames;
import us.kbase.workspace.database.mongo.Fields;
import us.kbase.workspace.kbase.InitConstants;

public class Common {


	public static List<String> getMD5s(
			final DB db,
			final String workspace) {
		final DBObject ws = db.getCollection(CollectionNames.COL_WORKSPACES).findOne(
				new BasicDBObject(Fields.WS_NAME, workspace));
		final long id = (long) ws.get(Fields.WS_ID);
		final DBObject sort = new BasicDBObject(Fields.VER_WS_ID, 1);
		sort.put(Fields.VER_ID, 1);
		final List<String> md5s = new ArrayList<>();
		final long startvers = System.nanoTime();
		for (final DBObject dbo: db.getCollection(CollectionNames.COL_WORKSPACE_VERS)
				.find(new BasicDBObject(Fields.VER_WS_ID, id)).sort(sort)) {
			md5s.add((String) dbo.get(Fields.VER_CHKSUM));
		}
		System.out.println("time to get md5s: " + (System.nanoTime() - startvers) / 1000000000.0);
		return md5s;
	}
	
	public static void printStats(final List<Long> shocktimes) {
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
	
	public static List<Long> getObjects(
			final BlobStore blob,
			final List<String> md5s,
			final int batch)
			throws Exception {
		final ByteArrayFileCacheManager man = new ByteArrayFileCacheManager(2000000000, 2000000000,
				new TempFilesManager(new File("temp_BlobBackendTiming")));
		
		final List<Long> shocktimes = new LinkedList<>();
		final long startShock = System.nanoTime();
		int count = 1;
		for (int i = 0; i < md5s.size(); i += batch) {
			final List<ByteArrayFileCache> l = new LinkedList<>();
			final long startBatch = System.nanoTime();
			for (int j = i + 1; j <= i + batch; j++) {
				final long startNode = System.nanoTime();
				l.add(blob.getBlob(new MD5(md5s.get(j - 1)), man)); // reads into memory
				shocktimes.add(System.nanoTime() - startNode);
				if (count % 10000 == 0) {
					System.out.println(count);
				}
				count++;
			}
			for (final ByteArrayFileCache b: l) {
				b.destroy();
			}
			System.out.println("time to get batch: " +
					(System.nanoTime() - startBatch) / 1000000000.0);
		}
		System.out.println("time to get objects: " +
				(System.nanoTime() - startShock) / 1000000000.0);
		return shocktimes;
	}

	public static List<String> getShockNodes(final DB db, final List<String> md5s) {
		final List<String> nodes = new LinkedList<>();
		final long startNodes = System.nanoTime();
		for (final String md5: md5s) {
			final DBObject node = db.getCollection(InitConstants.COL_SHOCK_NODES)
					.findOne(new BasicDBObject(Fields.SHOCK_CHKSUM, md5));
			nodes.add((String) node.get(Fields.SHOCK_NODE));
		}
		System.out.println("Time to get nodes: " +
				(System.nanoTime() - startNodes) / 1000000000.0);
		return nodes;
	}
}
