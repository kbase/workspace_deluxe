package performance;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.nocrala.tools.texttablefmt.Table;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
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
		
		List<Long> pull = new LinkedList<Long>();
		List<Long> xlate = new LinkedList<Long>();
		List<Long> jts = new LinkedList<Long>();
		List<Long> jgenjts = new LinkedList<Long>();
		List<Long> jgenwriter = new LinkedList<Long>();
		for (int i = 0; i < reps; i++) {
			char[] c = new char[300000];
			ByteArrayOutputStream baos = new ByteArrayOutputStream(30000000);
			ByteArrayOutputStream baos2 = new ByteArrayOutputStream(30000000);
			ByteArrayOutputStream baos3 = new ByteArrayOutputStream(30000000);

			long start = System.nanoTime();
			ByteArrayFileCache bafc = ws.getObjects(user, Arrays.asList(oi)).get(0).getDataAsTokens();
			long gotbytes = System.nanoTime();
			
			Reader r = bafc.getJSON();
			int read = 1;
			while (read > -1) {
				read = r.read(c);
			}
			r.close();
			long readchars = System.nanoTime();
			
			bafc.getUObject().write(baos);
			baos.close();
			long readJTS = System.nanoTime();
			
			JsonGenerator jgen = MAP.getFactory().createGenerator(baos2);
			bafc.getUObject().write(jgen);
			jgen.close();
			long readJgenJTS = System.nanoTime();
			
			Writer w = new OutputStreamWriter(baos3);
			JsonGenerator jgen2 = MAP.getFactory().createGenerator(w);
			bafc.getUObject().getPlacedStream().writeTokens(jgen2);
			jgen2.close();
			long readJgenWriter = System.nanoTime();
			
			pull.add(gotbytes - start);
			xlate.add(readchars - gotbytes);
			jts.add(readJTS - readchars);
			jgenjts.add(readJgenJTS - readJTS);
			jgenwriter.add(readJgenWriter - readJgenJTS);
		}
		List<PerformanceMeasurement> pms = new LinkedList<PerformanceMeasurement>();
		pms.add(new PerformanceMeasurement(pull, "Pull data from WS as BAFC"));
		pms.add(new PerformanceMeasurement(xlate, "Translate data to JSON via BAFC.getJSON()"));
		pms.add(new PerformanceMeasurement(jts, "Translate data to JSON via BAFC.getUObject.write(OutputStream)"));
		pms.add(new PerformanceMeasurement(jgenjts, "Translate data to JSON via BAFC.getUObject.write(JsonGenerator)"));
		pms.add(new PerformanceMeasurement(jgenwriter, "Translate data to JSON via BAFC.getUObject.getPlacedStream().writeTokens(JsonGenerator)"));
		
		renderResults(pms);
	}

	private static void renderResults(List<PerformanceMeasurement> pms) {
		final int width = 4;
		Table tbl = new Table(width);
		tbl.addCell("Operation");
		tbl.addCell("N");
		tbl.addCell("Mean time (s)");
		tbl.addCell("Std dev (s)");
		for (PerformanceMeasurement pm: pms) {
			tbl.addCell(pm.getName());
			tbl.addCell("" + pm.getN());
			tbl.addCell(String.format("%,.4f", pm.getAverageInSec()));
			tbl.addCell(String.format("%,.4f", pm.getStdDevInSec()));
		}
		System.out.println(tbl.render());
	}

}
