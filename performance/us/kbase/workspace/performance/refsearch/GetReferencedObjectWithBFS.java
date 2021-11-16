package us.kbase.workspace.performance.refsearch;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

import org.slf4j.LoggerFactory;

import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.LocalTypeProvider;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactoryBuilder;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIDWithRefPath;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;

/** 
 * Code for testing the performance cost of performing a BFS up the reference
 * graph in order to find a path from a user-accessible object to the user
 * requested object.
 * @author gaprice@lbl.gov
 *
 */
public class GetReferencedObjectWithBFS {
	
	private static final int TEST_REPS = 3;
	private static final boolean DO_LINEAR = false;
	private static final boolean DO_BRANCHED = true;
	private static final int MAX_TREE_BREADTH = 7;
	
	private static final String MONGO_EXE = "/kb/runtime/bin/mongod";
	private static final String TEMP_DIR = "GetRefedObjectWithBFS_temp";
	private static final boolean USE_WIRED_TIGER = false;
	
	private static final String MOD_NAME_STR = "TestModule";
	private static final String LEAF_TYPE_STR = "LeafType";
	private static final String REF_TYPE_STR = "RefType";
	
	private static final String DB_WS = "GetReferencedObjectBFSTest";
	private static final String DB_TYPES = DB_WS + "_types";
	
	private static final AbsoluteTypeDefId LEAF_TYPE = new AbsoluteTypeDefId(
			new TypeDefName(MOD_NAME_STR, LEAF_TYPE_STR), 1, 0);
	private static final AbsoluteTypeDefId REF_TYPE = new AbsoluteTypeDefId(
			new TypeDefName(MOD_NAME_STR, REF_TYPE_STR), 1, 0);
	
	private static Workspace WS;
	private static MongoDatabase WSDB;

	public static void main(String[] args) throws Exception {
		final Logger rootLogger = ((Logger) LoggerFactory.getLogger(
				org.slf4j.Logger.ROOT_LOGGER_NAME));
		rootLogger.setLevel(Level.OFF);
		
		final MongoController mongo = new MongoController(
				MONGO_EXE,
				Paths.get(TEMP_DIR),
				USE_WIRED_TIGER);
		System.out.println("Using Mongo temp dir " + mongo.getTempDir());
		System.out.println("Mongo port: " + mongo.getServerPort());
		@SuppressWarnings("resource")
		final MongoClient mc = new MongoClient("localhost:" + mongo.getServerPort());
		WSDB = mc.getDatabase(DB_WS);
		final MongoDatabase tdb = mc.getDatabase(DB_TYPES);
		TestCommon.destroyDB(WSDB);
		TestCommon.destroyDB(tdb);
		
		final TempFilesManager tfm = new TempFilesManager(new File(TEMP_DIR));
		tfm.cleanup();
		
		final TypeDefinitionDB typeDB = new TypeDefinitionDB(new MongoTypeStorage(tdb));
		final TypedObjectValidator val = new TypedObjectValidator(new LocalTypeProvider(typeDB));
		final MongoWorkspaceDB mwdb = new MongoWorkspaceDB(WSDB, new GridFSBlobStore(WSDB));
		
		WS = new Workspace(mwdb, new ResourceUsageConfigurationBuilder().build(), val, tfm);
		WS.setMaximumObjectSearchCount(10000000);
		installTypes(new Types(typeDB));
		if (DO_LINEAR) {
			runLinearReferencesTest();
		}
		if (DO_BRANCHED) {
			runBranchedReferencesTest();
		}
		System.out.println("Press a key to clean up test resources");
		final Scanner s = new Scanner(System.in);
		s.nextLine();
		s.close();
		tfm.cleanup();
		mongo.destroy(true);
	}

	private static void installTypes(final Types types) throws Exception {
		WorkspaceUser foo = new WorkspaceUser("foo");
		//simple spec
		types.requestModuleRegistration(foo, MOD_NAME_STR);
		types.resolveModuleRegistration(MOD_NAME_STR, true);
		types.compileNewTypeSpec(foo, 
				"module " + MOD_NAME_STR + " {" +
					"/* @optional thing */" +
					"typedef structure {" +
						"string thing;" +
					"} " + LEAF_TYPE_STR + ";" +
					"/* @id ws */" +
					"typedef string reference;" +
					"typedef structure {" +
						"list<reference> refs;" +
					"} " + REF_TYPE_STR + ";" +
				"};",
				Arrays.asList(LEAF_TYPE_STR, REF_TYPE_STR), null, null, false, null);
		types.releaseTypes(foo, MOD_NAME_STR);
	}

	//added obj name when autonaming removed
	private static ObjectIDNoWSNoVer getRandomName() {
		return new ObjectIDNoWSNoVer(UUID.randomUUID().toString().replace("-", ""));
	}

	private static void runBranchedReferencesTest() throws Exception {
		WorkspaceUser u1 = new WorkspaceUser("brcu1");
		WorkspaceUser u2 = new WorkspaceUser("brcu2");
		final IdReferenceHandlerSetFactory fac = IdReferenceHandlerSetFactoryBuilder
				.getBuilder(10).build().getFactory(null);
		Provenance p = new Provenance(u1);
		WorkspaceIdentifier read = new WorkspaceIdentifier("brcread");
		WorkspaceIdentifier priv = new WorkspaceIdentifier("brcpriv");
		for (int breadth = 1; breadth <= MAX_TREE_BREADTH; breadth++) {
			TestCommon.destroyDB(WSDB);
			WS.createWorkspace(u1, read.getName(), true, null, null);
			WS.createWorkspace(u1, priv.getName(), false, null, null);
			ObjectInformation o = WS.saveObjects(u1, priv, Arrays.asList(
					new WorkspaceSaveObject(getRandomName(), new HashMap<String, String>(),
							LEAF_TYPE, null, p, false)), fac).get(0);
			List<ObjectInformation> increfs = new LinkedList<ObjectInformation>();
			increfs.add(o);
			for (int depth = 1; depth <= 6; depth++) {
				increfs = generateReferences(u1, priv, increfs, breadth);
//				System.out.println(breadth + " " + depth + " " + increfs.size());
			}
			saveRefData(u1, read, increfs.get(0));
			System.out.print(breadth + " ");
			for (int j = 0; j < TEST_REPS; j++) {
				long start = System.nanoTime();
				WS.getObjects(u2, Arrays.asList((ObjectIdentifier) new ObjectIDWithRefPath(
						new ObjectIdentifier(priv, o.getObjectId()))));
				System.out.print((System.nanoTime() - start) + " ");
			}
			System.out.println();
		}
	}

	
	private static List<ObjectInformation> generateReferences(
			WorkspaceUser user,
			WorkspaceIdentifier wsi,
			List<ObjectInformation> increfs,
			int breadth) 
			throws Exception {
		final IdReferenceHandlerSetFactory fac = IdReferenceHandlerSetFactoryBuilder
				.getBuilder(100000).build().getFactory(null);
		Provenance p = new Provenance(user);
		List<WorkspaceSaveObject> objs = new LinkedList<WorkspaceSaveObject>();
		for (ObjectInformation oi: increfs) {
			String ref = oi.getWorkspaceId() + "/" + oi.getObjectId() + "/" + oi.getVersion();
			Map<String, List<String>> refdata = new HashMap<String, List<String>>();
			refdata.put("refs", Arrays.asList(ref));
			for (int i = 0; i < breadth; i++) {
				objs.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer(
						UUID.randomUUID().toString()), refdata, REF_TYPE, null, p, false));
			}
		}
		final List<ObjectInformation> ret = new LinkedList<ObjectInformation>();
		final int batch = 100000;
		int i;
		for(i = batch; i < objs.size(); i += batch) {
			ret.addAll(WS.saveObjects(user, wsi, objs.subList(i - batch, i), fac));
		}
		if ((i - batch) < objs.size()) {
			ret.addAll(WS.saveObjects(user, wsi, objs.subList(i-batch, objs.size()), fac));
		}
		return ret;
	}

	private static void runLinearReferencesTest() throws Exception {
		WorkspaceUser u1 = new WorkspaceUser("linu1");
		WorkspaceUser u2 = new WorkspaceUser("linu2");
		WorkspaceIdentifier priv = new WorkspaceIdentifier("linpriv");
		WS.createWorkspace(u1, priv.getName(), false, null, null);
		WorkspaceIdentifier read = new WorkspaceIdentifier("linread");
		WS.createWorkspace(u1, read.getName(), true, null, null);
		
		final IdReferenceHandlerSetFactory fac = IdReferenceHandlerSetFactoryBuilder
				.getBuilder(10000).build().getFactory(null);
		Provenance p = new Provenance(u1);
		ObjectInformation o = WS.saveObjects(u1, priv, Arrays.asList(
				new WorkspaceSaveObject(getRandomName(), new HashMap<String, String>(), LEAF_TYPE,
						null, p, false)), fac).get(0);
		
		for (int i = 2; i <= 50; i++) {
			o = saveRefData(u1, priv, o);
		}
		saveRefData(u1, read, o);
		
		for (int i = 50; i > 0; i--) {
			System.out.print(i + " ");
			for (int j = 0; j < TEST_REPS; j++) {
				long start = System.nanoTime();
				WS.getObjects(u2, Arrays.asList((ObjectIdentifier) new ObjectIDWithRefPath(
						new ObjectIdentifier(priv, i))));
				System.out.print((System.nanoTime() - start) + " ");
			}
			System.out.println();
		}
	}

	// just use generateReferences above
	private static ObjectInformation saveRefData(
			WorkspaceUser u1,
			WorkspaceIdentifier priv,
			ObjectInformation o)
			throws Exception {
		String ref = o.getWorkspaceId() + "/" + o.getObjectId() + "/" + o.getVersion();
		Map<String, List<String>> refdata = new HashMap<String, List<String>>();
		refdata.put("refs", Arrays.asList(ref));
		final IdReferenceHandlerSetFactory fac = IdReferenceHandlerSetFactoryBuilder
				.getBuilder(10000).build().getFactory(null);
		Provenance p = new Provenance(u1);
		return WS.saveObjects(u1, priv, Arrays.asList(
				new WorkspaceSaveObject(getRandomName(), refdata, REF_TYPE, null, p, false)), fac)
				.get(0);
	}

}
