package us.kbase.workspace.performance.workspace;

import static us.kbase.workspace.performance.utils.Utils.printElapse;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mongodb.DB;
import com.mongodb.MongoClient;

import us.kbase.auth.AuthService;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.mongo.BlobStore;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.mongo.ShockBlobStore;
import us.kbase.workspace.kbase.InitWorkspaceServer;

public class GetObjectsMongoWSDB {

//	public static final String WORKSPACE = "ReferenceTaxons";
	public static final String WORKSPACE = "TestObjs";
	
	public static final int ITERS = 20; //10
	public static final long BATCH_SIZE = 10000L;
	
	private static final String WS_DB = "ws_test";
	
	private static final String SHOCK_URL = "http://localhost:7044";
	
	public static void main(final String[] args) throws Exception {
		final String token = args[0];
		if (token == null || token.trim().isEmpty()) {
			throw new IllegalArgumentException("no token in args");
		}
		
		final MongoClient mc = new MongoClient();
		final DB db = mc.getDB(WS_DB);
		
		final BlobStore blob = new ShockBlobStore(
				db.getCollection(InitWorkspaceServer.COL_SHOCK_NODES),
				new URL(SHOCK_URL), AuthService.validateToken(token));
		final TempFilesManager tfm = new TempFilesManager(new File("temp_getobjmongoWS"));
		final MongoWorkspaceDB mws = new MongoWorkspaceDB(db, blob, tfm);
		
		final ResolvedWorkspaceID rwsi = mws.resolveWorkspace(new WorkspaceIdentifier(WORKSPACE));
		final ByteArrayFileCacheManager man = new ByteArrayFileCacheManager(
				200000000, 1000000000000L, tfm);
		
		final Set<SubsetSelection> empty = new HashSet<>(Arrays.asList(SubsetSelection.EMPTY));
		
		for (int i = 0; i < ITERS; i++) {
			final Map<ObjectIDResolvedWS, Set<SubsetSelection>> objs = new HashMap<>();
			final long start = (i * BATCH_SIZE) + 1;
			final long end = (i + 1) * BATCH_SIZE;
			for (long j = start; j <= end; j++) {
				objs.put(new ObjectIDResolvedWS(rwsi, j), empty);
			}
			
			final long preiter = System.nanoTime();
			final Map<ObjectIDResolvedWS, Map<SubsetSelection, WorkspaceObjectData>> res =
					mws.getObjects(objs, man, 0, true, false, true);
			for (final Map<SubsetSelection, WorkspaceObjectData> ss2wos: res.values()) {
				for (final WorkspaceObjectData wos: ss2wos.values()) {
					wos.destroy();
				}
			}
			printElapse("get", preiter);
		}
	}
	
}
