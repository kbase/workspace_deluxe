package us.kbase.typedobj.db.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.RefInfo;
import us.kbase.typedobj.db.SemanticVersion;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.db.TypeStorage;
import us.kbase.typedobj.db.UserInfoProviderForTests;

public class TypeRegisteringTest {
	private static TypeStorage storage = null;
	private static TypeDefinitionDB db = null;
	private static boolean useMongo = true;
	private static String adminUser = "admin";

	@BeforeClass
	public static void prepareBeforeClass() throws Exception {
		File dir = new File("temp_files");
		if (!dir.exists())
			dir.mkdir();
		if (useMongo) {
			storage = new MongoTypeStorage(new MongoClient("localhost", 
					MongoClientOptions.builder().autoConnectRetry(true).build()).getDB(getTestDbName()));
		} else {
			storage = new FileTypeStorage(dir.getAbsolutePath());
		}
		db = new TypeDefinitionDB(storage, dir, new UserInfoProviderForTests());
	}
	
	private static String getTestDbName() {
		String ret = System.getProperty("test.mongo.db1");
		if (ret == null)
			ret = "test";
		return ret;
	}
	
	@Before
	public void cleanupBefore() throws Exception {
		storage.removeAllData();
	}
	
	@After
	public void cleanupAfter() throws Exception {
		//cleanupBefore();
	}
	
	@Test
	public void testSimple() throws Exception {
		String user = adminUser;
		String taxonomySpec = loadSpec("simple", "Taxonomy");
		db.registerModule(taxonomySpec, Arrays.asList("taxon"), user);
		releaseType("Taxonomy", "taxon", user);
		String sequenceSpec = loadSpec("simple", "Sequence");
		db.registerModule(sequenceSpec, Arrays.asList("sequence_id", "sequence_pos"), user);
		releaseType("Sequence", "sequence_id", user);
		releaseType("Sequence", "sequence_pos", user);
		String annotationSpec = loadSpec("simple", "Annotation");
		db.registerModule(annotationSpec, Arrays.asList("genome", "gene"), user);
		releaseType("Annotation", "genome", user);
		releaseType("Annotation", "gene", user);
		checkTypeDep("Annotation", "gene", "Sequence", "sequence_pos", null, true);
		String regulationSpec = loadSpec("simple", "Regulation");
		db.registerModule(regulationSpec, Arrays.asList("regulator", "binding_site"), user);
		checkTypeDep("Regulation", "binding_site", "Regulation", "regulator", "0.1", true);
		releaseType("Regulation", "regulator", user);
		releaseType("Regulation", "binding_site", user);
		checkTypeDep("Regulation", "binding_site", "Regulation", "regulator", "1.0", true);
		String reg2spec = loadSpec("simple", "Regulation", "2");
		db.updateModule(reg2spec, Arrays.asList("new_regulator"), Collections.<String>emptyList(), user);
		checkTypeDep("Regulation", "binding_site", "Regulation", "regulator", null, false);
		checkTypeDep("Regulation", "binding_site", "Regulation", "new_regulator", "0.1", true);
		Assert.assertEquals(5, db.getAllModuleVersions("Regulation").size());
		Assert.assertEquals("2.0", db.getLatestTypeVersion(new TypeDefName("Regulation.binding_site")));
	}
	
	private void releaseType(String module, String type, String user) throws Exception {
		db.releaseType(new TypeDefName(module + "." + type), user);
	}
	
	private String loadSpec(String testName, String specName) throws Exception {
		return loadSpec(testName, specName, null);
	}
	
	private String loadSpec(String testName, String specName, String version) throws Exception {
		String resName = testName + "." + specName + (version == null ? "" : ("." +version)) + ".spec.properties";
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		InputStream is = getClass().getResourceAsStream(resName);
		if (is == null)
			throw new IllegalStateException("Resource not found: " + resName);
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		while (true) {
			String line = br.readLine();
			if (line == null)
				break;
			pw.println(line);
		}
		br.close();
		pw.close();
		return sw.toString();
	}
	
	private void checkTypeDep(String depModule, String depType, 
			String refModule, String refType, String refVer, boolean res) throws Exception {
		SemanticVersion depVer = new SemanticVersion(db.getLatestTypeVersion(new TypeDefName(depModule + "." + depType)));
		Set<RefInfo> refs = db.getTypeRefsByDep(new AbsoluteTypeDefId(new TypeDefName(depModule + "." + depType), 
				depVer.getMajor(), depVer.getMinor()));
		RefInfo ret = null;
		for (RefInfo ri : refs) {
			if (ri.getRefModule().equals(refModule) && ri.getRefName().equals(refType)) {
				ret = ri;
				break;
			}
		}
		Assert.assertEquals(res, ret != null);
		if (ret != null && refVer != null) {
			Assert.assertEquals(refVer, ret.getRefVersion());
		}
	}
}
