package us.kbase.typedobj.db.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.RefInfo;
import us.kbase.typedobj.db.SemanticVersion;
import us.kbase.typedobj.db.TypeChange;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.db.TypeStorage;
import us.kbase.typedobj.db.UserInfoProviderForTests;
import us.kbase.typedobj.exceptions.TypeStorageException;

@RunWith(Parameterized.class)
public class TypeRegisteringTest {
	private TestTypeStorage storage = null;
	private TypeDefinitionDB db = null;
	private boolean useMongo = false; //true;
	private static String adminUser = "admin";

	public TypeRegisteringTest(boolean useMongoParam) throws Exception {
		File dir = new File("temp_files");
		if (!dir.exists())
			dir.mkdir();
		TypeStorage innerStorage;
		if (useMongo) {
			innerStorage = new MongoTypeStorage(new MongoClient("localhost", 
					MongoClientOptions.builder().autoConnectRetry(true).build()).getDB(getTestDbName()));
		} else {
			innerStorage = new FileTypeStorage(dir.getAbsolutePath());
		}
		storage = TestTypeStorageFactory.createTypeStorageWrapper(innerStorage);
		db = new TypeDefinitionDB(storage, dir, new UserInfoProviderForTests());
	}
	
	@Parameters
	public static Collection<Object[]> generateData() {
		return Arrays.asList(new Object[][] {
				{false}, {true}
		});
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
		storage.removeAllTypeStorageListeners();
	}
	
	@After
	public void cleanupAfter() throws Exception {
		//cleanupBefore();
	}
	
	@Test
	public void testSimple() throws Exception {
		String user = adminUser;
		String taxonomySpec = loadSpec("simple", "Taxonomy");
		initModule("Taxonomy", user);
		readOnlyMode();
		db.registerModule(taxonomySpec, Arrays.asList("taxon"), Collections.<String>emptyList(), user, true);
		storage.removeAllTypeStorageListeners();
		db.registerModule(taxonomySpec, Arrays.asList("taxon"), user);
		releaseType("Taxonomy", "taxon", user);
		String sequenceSpec = loadSpec("simple", "Sequence");
		initModule("Sequence", user);
		db.registerModule(sequenceSpec, Arrays.asList("sequence_id", "sequence_pos"), user);
		releaseType("Sequence", "sequence_id", user);
		releaseType("Sequence", "sequence_pos", user);
		String annotationSpec = loadSpec("simple", "Annotation");
		initModule("Annotation", user);
		db.registerModule(annotationSpec, Arrays.asList("genome", "gene"), user);
		releaseType("Annotation", "genome", user);
		releaseType("Annotation", "gene", user);
		checkTypeDep("Annotation", "gene", "Sequence", "sequence_pos", null, true);
		String regulationSpec = loadSpec("simple", "Regulation");
		initModule("Regulation", user);
		db.registerModule(regulationSpec, Arrays.asList("regulator", "binding_site"), user);
		checkTypeDep("Regulation", "binding_site", "Regulation", "regulator", "0.1", true);
		releaseType("Regulation", "regulator", user);
		releaseType("Regulation", "binding_site", user);
		checkTypeDep("Regulation", "binding_site", "Regulation", "regulator", "1.0", true);
		String reg2spec = loadSpec("simple", "Regulation", "2");
		readOnlyMode();
		Map<TypeDefName, TypeChange> changes = db.registerModule(reg2spec, Arrays.asList("new_regulator"), 
				Collections.<String>emptyList(), user, true);
		Assert.assertEquals(3, changes.size());
		Assert.assertFalse(changes.get(new TypeDefName("Regulation.new_regulator")).isUnregistered());
		Assert.assertEquals("0.1", changes.get(new TypeDefName("Regulation.new_regulator")).getTypeVersion().getVerString());
		Assert.assertFalse(changes.get(new TypeDefName("Regulation.binding_site")).isUnregistered());
		Assert.assertEquals("2.0", changes.get(new TypeDefName("Regulation.binding_site")).getTypeVersion().getVerString());
		Assert.assertTrue(changes.get(new TypeDefName("Regulation.regulator")).isUnregistered());
		storage.removeAllTypeStorageListeners();
		db.registerModule(reg2spec, Arrays.asList("new_regulator"), Collections.<String>emptyList(), user);
		checkTypeDep("Regulation", "binding_site", "Regulation", "regulator", null, false);
		checkTypeDep("Regulation", "binding_site", "Regulation", "new_regulator", "0.1", true);
		Assert.assertEquals(5, db.getAllModuleVersions("Regulation").size());
		Assert.assertEquals("2.0", db.getLatestTypeVersion(new TypeDefName("Regulation.binding_site")));
	}
	
	@Test
	public void testDescr() throws Exception {
		String sequenceSpec = loadSpec("descr", "Descr");
		initModule("Descr", adminUser);
		db.registerModule(sequenceSpec, Arrays.asList("sequence_id", "sequence_pos"), adminUser);
		Assert.assertEquals("Descr module.\n\nEnd of comment.", db.getModuleDescription("Descr"));
		Assert.assertEquals("", db.getTypeDescription(new TypeDefId("Descr.sequence_id")));
		Assert.assertEquals("", db.getFuncDescription("Descr", "invis_func", null));
		Assert.assertEquals("position of fragment on a sequence", db.getTypeDescription(new TypeDefId("Descr.sequence_pos")));
		Assert.assertEquals("The super function.", db.getFuncDescription("Descr", "super_func", null));
	}
	
	@Test
	public void testBackward() throws Exception {
		String regulationSpec = loadSpec("backward", "Regulation");
		initModule("Regulation", adminUser);
		db.registerModule(regulationSpec, Arrays.asList("gene", "binding_site"), adminUser);
		releaseType("Regulation", "gene", adminUser);
		releaseType("Regulation", "binding_site", adminUser);
		db.releaseFunc("Regulation", "get_gene_descr", adminUser);
		db.releaseFunc("Regulation", "get_nearest_binding_sites", adminUser);
		db.releaseFunc("Regulation", "get_regulated_genes", adminUser);
		String reg2spec = loadSpec("backward", "Regulation", "2");
		Map<TypeDefName, TypeChange> changes = db.registerModule(reg2spec, Arrays.<String>asList(), 
				Collections.<String>emptyList(), adminUser);
		Assert.assertEquals(2, changes.size());
		Assert.assertEquals("1.1", changes.get(new TypeDefName("Regulation.gene")).getTypeVersion().getVerString());
		Assert.assertEquals("2.0", changes.get(new TypeDefName("Regulation.binding_site")).getTypeVersion().getVerString());
		checkFuncVer("Regulation", "get_gene_descr", "2.0");
		checkFuncVer("Regulation", "get_nearest_binding_sites", "2.0");
		checkFuncVer("Regulation", "get_regulated_genes", "1.1");
		String reg3spec = loadSpec("backward", "Regulation", "3");
		Map<TypeDefName, TypeChange> changes3 = db.registerModule(reg3spec, Arrays.<String>asList(), 
				Collections.<String>emptyList(), adminUser);
		Assert.assertEquals(2, changes3.size());
		Assert.assertEquals("1.2", changes3.get(new TypeDefName("Regulation.gene")).getTypeVersion().getVerString());
		Assert.assertEquals("3.0", changes3.get(new TypeDefName("Regulation.binding_site")).getTypeVersion().getVerString());
		checkFuncVer("Regulation", "get_gene_descr", "2.0");
		checkFuncVer("Regulation", "get_nearest_binding_sites", "3.0");
		checkFuncVer("Regulation", "get_regulated_genes", "1.2");
		String reg4spec = loadSpec("backward", "Regulation", "4");
		Map<TypeDefName, TypeChange> changes4 = db.registerModule(reg4spec, Arrays.<String>asList(), 
				Collections.<String>emptyList(), adminUser);
		Assert.assertEquals(2, changes4.size());
		Assert.assertEquals("2.0", changes4.get(new TypeDefName("Regulation.gene")).getTypeVersion().getVerString());
		Assert.assertEquals("4.0", changes4.get(new TypeDefName("Regulation.binding_site")).getTypeVersion().getVerString());
		checkFuncVer("Regulation", "get_gene_descr", "3.0");
		checkFuncVer("Regulation", "get_nearest_binding_sites", "4.0");
		checkFuncVer("Regulation", "get_regulated_genes", "2.0");
	}
	
	@Test
	public void testRollback() throws Exception {
		String spec1 = loadSpec("rollback", "First");
		initModule("First", adminUser);
		long verAfterInit = db.getLastModuleVersion("First");
		int typesAfterInit = db.getAllRegisteredTypes("First").size();
		int funcsAfterInit = db.getAllRegisteredFuncs("First").size();
		String objAfterInit = getStorageObjects();
		for (String errMethod : Arrays.asList(
				"writeTypeSchemaRecord", "writeTypeParseRecord", "writeFuncParseRecord", "writeModuleRecords", "addRefs")) {
			System.out.println("Error method: " + errMethod);
			storage.removeAllTypeStorageListeners();
			withErrorAfterMethod(errMethod);
			Assert.assertEquals(1, storage.getTypeStorageListeners().size());
			try {
				db.registerModule(spec1, Arrays.asList("seq_id", "seq_pos"), adminUser);
				Assert.fail("Error should occur before this line");
			} catch (Exception ex) {
				Assert.assertEquals("Method has test error at the end of body.", ex.getMessage());
				System.out.println(objAfterInit);
				Assert.assertEquals(verAfterInit, db.getLastModuleVersion("First"));
				Assert.assertEquals(typesAfterInit, db.getAllRegisteredTypes("First").size());
				Assert.assertEquals(funcsAfterInit, db.getAllRegisteredFuncs("First").size());
				Assert.assertEquals(objAfterInit, getStorageObjects());
			}
		}
	}
	
	private String getStorageObjects() throws Exception {
		Map<String, Long> ret = storage.listObjects();
		for (String key : new ArrayList<String>(ret.keySet())) 
			if (ret.get(key) == 0)
				ret.remove(key);
		return "" + ret;
	}
	
	private void checkFuncVer(String module, String funcName, String version) throws Exception {
		Assert.assertEquals(version, db.getLatestFuncVersion(module, funcName));
	}
	
	private void readOnlyMode() {
		storage.addTypeStorageListener(new TypeStorageListener() {
			@Override
			public void onMethodStart(String method, Object[] params)
					throws TypeStorageException {
				if (method.startsWith("add") || method.startsWith("write") || 
						method.startsWith("init") || method.startsWith("remove"))
					throw new TypeStorageException("Type storage is in read only mode.");
			}
			@Override
			public void onMethodEnd(String method, Object[] params, Object ret)
					throws TypeStorageException {
			}
		});
	}

	private void withErrorAfterMethod(final String errMethodName) {
		storage.addTypeStorageListener(new TypeStorageListener() {
			@Override
			public void onMethodStart(String method, Object[] params)
					throws TypeStorageException {
				//System.out.println("TypeStorage method starts: " + method);
			}
			@Override
			public void onMethodEnd(String method, Object[] params, Object ret)
					throws TypeStorageException {
				if (method.equals(errMethodName))
					throw new TypeStorageException("Method has test error at the end of body.");
			}
		});
	}

	private void initModule(String moduleName, String user) throws Exception {
		db.requestModuleRegistration(moduleName, user);
		db.approveModuleRegistrationRequest(adminUser, moduleName, user);
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
