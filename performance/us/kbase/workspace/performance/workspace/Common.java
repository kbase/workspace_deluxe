package us.kbase.workspace.performance.workspace;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.bson.Document;

import com.mongodb.client.MongoDatabase;

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
			final MongoDatabase db,
			final String workspace) {
		final Document ws = db.getCollection(CollectionNames.COL_WORKSPACES).find(
				new Document(Fields.WS_NAME, workspace)).first();
		final long id = ws.getLong(Fields.WS_ID);
		final Document sort = new Document(Fields.VER_WS_ID, 1);
		sort.put(Fields.VER_ID, 1);
		final List<String> md5s = new ArrayList<>();
		final long startvers = System.nanoTime();
		for (final Document dbo: db.getCollection(CollectionNames.COL_WORKSPACE_VERS)
				.find(new Document(Fields.VER_WS_ID, id)).sort(sort)) {
			md5s.add(dbo.getString(Fields.VER_CHKSUM));
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
		final ByteArrayFileCacheManager man = new ByteArrayFileCacheManager(
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

	public static List<String> getShockNodes(final MongoDatabase db, final List<String> md5s) {
		final List<String> nodes = new LinkedList<>();
		final long startNodes = System.nanoTime();
		for (final String md5: md5s) {
			final Document node = db.getCollection(InitConstants.COL_SHOCK_NODES)
					.find(new Document(Fields.SHOCK_CHKSUM, md5)).first();
			nodes.add(node.getString(Fields.SHOCK_NODE));
		}
		System.out.println("Time to get nodes: " +
				(System.nanoTime() - startNodes) / 1000000000.0);
		return nodes;
	}
}
