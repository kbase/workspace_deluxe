package legacy.performance.us.kbase.workspace.performance.workspace;

import static legacy.performance.us.kbase.workspace.performance.utils.Utils.printElapse;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;

import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.mongo.BlobStore;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;

public class GetObjectsMongoWSDB {
	// Note this originally timed getting data from the mongo backend, but now that
	// method is split into two methods. If this code is needed, update to fetch the data as well.
	// See the git history for more info

//	public static final String WORKSPACE = "ReferenceTaxons";
	public static final String WORKSPACE = "TestObjs";
	
	public static final int ITERS = 20; //10
	public static final long BATCH_SIZE = 10000L;
	
	private static final String WS_DB = "ws_test";
	
	public static void main(final String[] args) throws Exception {
		final String token = args[0];
		if (token == null || token.trim().isEmpty()) {
			throw new IllegalArgumentException("no token in args");
		}
		
		@SuppressWarnings("resource")
		final MongoClient mc = new MongoClient();
		final MongoDatabase db = mc.getDatabase(WS_DB);
		
		final BlobStore blob = new GridFSBlobStore(db);
		final MongoWorkspaceDB mws = new MongoWorkspaceDB(db, blob);
		
		final ResolvedWorkspaceID rwsi = mws.resolveWorkspace(new WorkspaceIdentifier(WORKSPACE));
		
		for (int i = 0; i < ITERS; i++) {
			final Set<ObjectIDResolvedWS> objs = new HashSet<>();
			final long start = (i * BATCH_SIZE) + 1;
			final long end = (i + 1) * BATCH_SIZE;
			for (long j = start; j <= end; j++) {
				objs.add(new ObjectIDResolvedWS(rwsi, j));
			}
			
			final long preiter = System.nanoTime();
			final Map<ObjectIDResolvedWS, WorkspaceObjectData.Builder> res =
					mws.getObjects(objs, true, false, true); // no longer returns data
			for (final WorkspaceObjectData.Builder wos: res.values()) {
				wos.build().destroy();
			}
			printElapse("get", preiter);
		}
	}
	
}
