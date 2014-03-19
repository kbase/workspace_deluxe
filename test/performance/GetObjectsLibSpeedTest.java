package performance;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.DefaultReferenceParser;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.lib.Workspace;
import us.kbase.workspace.lib.WorkspaceSaveObject;
import us.kbase.workspace.test.WorkspaceTestCommon;

public class GetObjectsLibSpeedTest {
	
	
	private static final ObjectMapper MAP = new ObjectMapper();
	
	public static void main(String[] args) throws Exception {
		String shockuser = args[0];
		String shockpwd = args[1];
		int reps = 500;
		String mongohost = "localhost";
		String shockurl = "http://localhost:7044";
		String wsDB = "getObjectsSpeedTest"; // this will get wiped out
		String typeDB = "getObjectsSpeedTestTypes"; // this too
		String module = "SupahFakeKBGA";
		String type = "Genome";
		String specfile = "test/performance/SupahFakeKBGA.spec";
		String objfile = "test/performance/83333.2.txt";
		
		
		System.setProperty("test.mongo.db1", wsDB);
		System.setProperty("test.mongo.db.types1", typeDB);
		System.setProperty("test.mongo.host", mongohost);
		System.setProperty("test.shock.url", shockurl);
		WorkspaceTestCommon.destroyAndSetupDB(1, WorkspaceTestCommon.SHOCK, shockuser);
		Workspace ws = new Workspace(new MongoWorkspaceDB(mongohost, wsDB, shockpwd, TempFilesManager.forTests()),
				new DefaultReferenceParser());
		
		WorkspaceUser user = new WorkspaceUser("foo");
		ws.requestModuleRegistration(user, module);
		ws.resolveModuleRegistration(module, true);
		ws.compileNewTypeSpec(user, FileUtils.readFileToString(new File(specfile)),
				Arrays.asList(type), null, null, false, null);
		ws.releaseTypes(user, module);
		@SuppressWarnings("unchecked")
		Map<String, Object> o = MAP.readValue(new File(objfile), Map.class);
		
		ws.createWorkspace(user, "fake", false, null, null);
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("fake");
		TypeDefId td = new TypeDefId(new TypeDefName(module, type));
		ws.saveObjects(user, wsi, Arrays.asList(
				new WorkspaceSaveObject(o, td, null, new Provenance(user), false)));
		o = null;
		
		ObjectIdentifier oi = new ObjectIdentifier(wsi, "auto1");
		char[] c = new char[300000];
		ByteArrayOutputStream baos = new ByteArrayOutputStream(30000000);
		
		long start = System.nanoTime();
		ByteArrayFileCache bafc = ws.getObjects(user, Arrays.asList(oi)).get(0).getDataAsTokens();
		long gotbytes = System.nanoTime();
		Reader r = bafc.getBytes();
		int read = 1;
		while (read > -1) {
			read = r.read(c);
		}
		r.close();
		long readchars = System.nanoTime();
		bafc.getUObject().write(baos);
		baos.close();
		long readJTS = System.nanoTime();
		System.out.println(gotbytes - start);
		System.out.println(readchars - gotbytes);
		System.out.println(readJTS - readchars);
		//TODO to JsonGenerator
	}

}
