package us.kbase.typedobj.db.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.FuncInfo;
import us.kbase.typedobj.db.ModuleInfo;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.OwnerInfo;
import us.kbase.typedobj.db.RefInfo;
import us.kbase.typedobj.db.SemanticVersion;
import us.kbase.typedobj.db.TypeChange;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.db.TypeStorage;
import us.kbase.typedobj.db.UserInfoProviderForTests;
import us.kbase.typedobj.exceptions.NoSuchPrivilegeException;
import us.kbase.typedobj.exceptions.SpecParseException;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.workspace.test.WorkspaceTestCommon;

@RunWith(Parameterized.class)
public class TypeRegisteringTest {
	private TestTypeStorage storage = null;
	private TypeDefinitionDB db = null;
	private final boolean useMongo;
	private static String adminUser = "admin";

	public static void main(String[] args) throws Exception {
		boolean[] storageParams = {false, true};
		for (boolean useMongoParam : storageParams) {
			TypeRegisteringTest test = new TypeRegisteringTest(useMongoParam);
			test.cleanupBefore();
			try {
				//test.testSimple();
				//test.testDescr();
				//test.testBackward();
				//test.testRollback();
				//test.testRestrict();
				//test.testIndeces();
				//test.testMD5();
				test.testRegistration();
			} finally {
				test.cleanupAfter();
			}
		}
	}

	public TypeRegisteringTest(boolean useMongoParam) throws Exception {
		useMongo = useMongoParam;
		File dir = new File("temp_files");
		if (!dir.exists())
			dir.mkdir();
		TypeStorage innerStorage;
		if (useMongo) {
			innerStorage = new MongoTypeStorage(createMongoDbConnection());
		} else {
			innerStorage = new FileTypeStorage(dir.getAbsolutePath());
		}
		storage = TestTypeStorageFactory.createTypeStorageWrapper(innerStorage);
		db = new TypeDefinitionDB(storage, dir, new UserInfoProviderForTests(adminUser));
	}
	
	public static DB createMongoDbConnection() throws UnknownHostException {
		String host = System.getProperty(WorkspaceTestCommon.HOST);
		if (host == null)
			host = "localhost";
		String db = System.getProperty(WorkspaceTestCommon.DB1);
		if (db == null)
			db = "test";
		DB mdb = new MongoClient(host, MongoClientOptions.builder().autoConnectRetry(true).build()).getDB(db);
		String mUser = System.getProperty(WorkspaceTestCommon.M_USER);
		String mPwd = System.getProperty(WorkspaceTestCommon.M_PWD);
		if (mUser != null)
			mdb.authenticate(mUser, mPwd.toCharArray());
		return mdb;
	}
	
	@Parameters
	public static Collection<Object[]> generateData() {
		return Arrays.asList(new Object[][] {
				{false}, {true}
		});
	}
		
	@Before
	public void cleanupBefore() throws Exception {
		storage.removeAllTypeStorageListeners();
		storage.removeAllData();
	}
	
	@After
	public void cleanupAfter() throws Exception {
		cleanupBefore();
	}
	
	@Test
	public void testSimple() throws Exception {
		String user = "Owner";
		String taxonomySpec = loadSpec("simple", "Taxonomy");
		initModule("Taxonomy", user);
		readOnlyMode();
		try {
			db.registerModule(taxonomySpec, Arrays.asList("taxon"), Collections.<String>emptyList(), "Nobody", true);
			Assert.fail();
		} catch (NoSuchPrivilegeException ex) {
			Assert.assertTrue(ex.getMessage().equals("User Nobody is not in list of owners of module Taxonomy"));
		}
		db.registerModule(taxonomySpec, Arrays.asList("taxon"), Collections.<String>emptyList(), user, true);
		storage.removeAllTypeStorageListeners();
		db.registerModule(taxonomySpec, Arrays.asList("taxon"), user);
		releaseModule("Taxonomy", user);
		String sequenceSpec = loadSpec("simple", "Sequence");
		initModule("Sequence", user);
		db.registerModule(sequenceSpec, Arrays.asList("sequence_id", "sequence_pos"), user);
		releaseModule("Sequence", user);
		String annotationSpec = loadSpec("simple", "Annotation");
		initModule("Annotation", user);
		db.registerModule(annotationSpec, Arrays.asList("genome", "gene"), user);
		releaseModule("Annotation", user);
		checkTypeDep("Annotation", "gene", "Sequence", "sequence_pos", null, true);
		String regulationSpec = loadSpec("simple", "Regulation");
		initModule("Regulation", user);
		db.registerModule(regulationSpec, Arrays.asList("regulator", "binding_site"), user);
		checkTypeDep("Regulation", "binding_site", "Regulation", "regulator", "0.1", true);
		releaseModule("Regulation", user);
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
		releaseModule("Regulation", user);
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
		db.releaseModule("Regulation", adminUser);
		checkTypeVer("Regulation", "binding_site", "1.0");
		String reg2spec = loadSpec("backward", "Regulation", "2");
		Map<TypeDefName, TypeChange> changes = db.registerModule(reg2spec, Arrays.<String>asList(), 
				Collections.<String>emptyList(), adminUser);
		Assert.assertEquals(2, changes.size());
		Assert.assertEquals("1.1", changes.get(new TypeDefName("Regulation.gene")).getTypeVersion().getVerString());
		Assert.assertEquals("2.0", changes.get(new TypeDefName("Regulation.binding_site")).getTypeVersion().getVerString());
		db.releaseModule("Regulation", adminUser);
		checkFuncVer("Regulation", "get_gene_descr", "2.0");
		checkFuncVer("Regulation", "get_nearest_binding_sites", "2.0");
		checkFuncVer("Regulation", "get_regulated_genes", "1.1");
		String reg3spec = loadSpec("backward", "Regulation", "3");
		Map<TypeDefName, TypeChange> changes3 = db.registerModule(reg3spec, Arrays.<String>asList(), 
				Collections.<String>emptyList(), adminUser);
		Assert.assertEquals(2, changes3.size());
		Assert.assertEquals("1.2", changes3.get(new TypeDefName("Regulation.gene")).getTypeVersion().getVerString());
		Assert.assertEquals("3.0", changes3.get(new TypeDefName("Regulation.binding_site")).getTypeVersion().getVerString());
		db.releaseModule("Regulation", adminUser);
		checkFuncVer("Regulation", "get_gene_descr", "2.0");
		checkFuncVer("Regulation", "get_nearest_binding_sites", "3.0");
		checkFuncVer("Regulation", "get_regulated_genes", "1.2");
		String reg4spec = loadSpec("backward", "Regulation", "4");
		Map<TypeDefName, TypeChange> changes4 = db.registerModule(reg4spec, Arrays.<String>asList(), 
				Collections.<String>emptyList(), adminUser);
		Assert.assertEquals(2, changes4.size());
		Assert.assertEquals("2.0", changes4.get(new TypeDefName("Regulation.gene")).getTypeVersion().getVerString());
		Assert.assertEquals("4.0", changes4.get(new TypeDefName("Regulation.binding_site")).getTypeVersion().getVerString());
		db.releaseModule("Regulation", adminUser);
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
			storage.removeAllTypeStorageListeners();
			withErrorAfterMethod(errMethod);
			Assert.assertEquals(1, storage.getTypeStorageListeners().size());
			try {
				db.registerModule(spec1, Arrays.asList("seq_id", "seq_pos"), adminUser);
				Assert.fail("Error should occur before this line");
			} catch (Exception ex) {
				Assert.assertEquals("Method has test error at the end of body.", ex.getMessage());
				Assert.assertEquals(verAfterInit, db.getLastModuleVersion("First"));
				Assert.assertEquals(typesAfterInit, db.getAllRegisteredTypes("First").size());
				Assert.assertEquals(funcsAfterInit, db.getAllRegisteredFuncs("First").size());
				Assert.assertEquals(objAfterInit, getStorageObjects());
			}
		}
	}
	
	@Test
	public void testRestrict() throws Exception {
		initModule("Common", adminUser);
		db.registerModule(loadSpec("restrict", "Common"), Arrays.asList("common_struct"), adminUser);
		long commonVer1 = db.getLastModuleVersion("Common");
		initModule("Middle", adminUser);
		db.registerModule(loadSpec("restrict", "Middle"), Arrays.asList("middle_struct"), adminUser);
		long middleVer1 = db.getLastModuleVersion("Middle");
		db.registerModule(loadSpec("restrict", "Common", "2"), Collections.<String>emptyList(), adminUser);
		long commonVer2 = db.getLastModuleVersion("Common");
		initModule("Upper", adminUser);
		try {
			db.registerModule(loadSpec("restrict", "Upper"), Arrays.asList("upper_struct"), adminUser);
			Assert.fail();
		} catch (SpecParseException ex) {
			Assert.assertTrue(ex.getMessage().contains("Incompatible module dependecies: Common"));
		}
		db.registerModule(loadSpec("restrict", "Upper"), Arrays.asList("upper_struct"), 
				Collections.<String>emptyList(), adminUser, true, restrict("Common", commonVer1));
		db.refreshModule("Middle", adminUser);
		try {
			db.registerModule(loadSpec("restrict", "Upper"), Arrays.asList("upper_struct"),
					Collections.<String>emptyList(), adminUser, false, restrict("Common", commonVer1));
			Assert.fail();
		} catch (SpecParseException ex) {
			Assert.assertTrue(ex.getMessage().contains("Version of dependent module Common"));
		}
		try {
			db.registerModule(loadSpec("restrict", "Upper"), Arrays.asList("upper_struct"),
					Collections.<String>emptyList(), adminUser, false, 
					restrict("Common", commonVer2, "Middle", middleVer1));
			Assert.fail();
		} catch (SpecParseException ex) {
			Assert.assertTrue(ex.getMessage().contains("Version of dependent module Common"));
		}
		db.registerModule(loadSpec("restrict", "Upper"), Arrays.asList("upper_struct"),
				Collections.<String>emptyList(), adminUser, false, 
				restrict("Common", commonVer1, "Middle", middleVer1));
	}
	
	public void testIndeces() throws Exception {
		long time = System.currentTimeMillis();
		String regulationSpec = loadSpec("backward", "Regulation");
		initModule("Regulation", adminUser);
		long initVer = db.getLastModuleVersion("Regulation");
		db.registerModule(regulationSpec, Arrays.asList("sequence_pos1", "gene", "sequence_pos2", 
				"binding_site"), adminUser);
		long regVer = db.getLastModuleVersion("Regulation");
		MongoTypeStorage mts = (MongoTypeStorage)storage.getInnerStorage();
		for (int i = 0; i < 1000; i++) {
			mts.copyModuleVersion("Regulation", regVer, regVer + 1 + i);
		}
		System.out.println("Preparation time: " + (System.currentTimeMillis() - time));
		time = System.currentTimeMillis();
		List<Long> versions = db.getAllModuleVersions("Regulation");
		for (long ver : versions) {
			if (ver == initVer)
				continue;
			db.getModuleSpecDocument("Regulation", ver);
			ModuleInfo info = db.getModuleInfo("Regulation", ver);
			int refCount = 0;
			for (String type : info.getTypes().keySet()) {
				TypeDefId typeDef = new TypeDefId(info.getModuleName() + "." + type, 
						info.getTypes().get(type).getTypeVersion());
				db.getJsonSchemaDocument(typeDef);
				db.getTypeParsingDocument(typeDef);
				refCount += db.getTypeRefsByDep(typeDef).size();
			}
			Assert.assertEquals(3, refCount);
			refCount = 0;
			for (String func : info.getFuncs().keySet()) {
				String funcVer = info.getFuncs().get(func).getFuncVersion();
				db.getFuncParsingDocument(info.getModuleName(), func, funcVer);
				refCount += db.getFuncRefsByDep(info.getModuleName(), func, funcVer).size();
			}
			Assert.assertEquals(2, refCount);
		}
		System.out.println("Search time: " + (System.currentTimeMillis() - time));
	}
	
	@Test
	public void testMD5() throws Exception {
		initModule("Common", adminUser);
		db.registerModule(loadSpec("md5", "Common"), Arrays.asList("common_struct"), adminUser);
		initModule("Upper", adminUser);
		db.registerModule(loadSpec("md5", "Upper"), Arrays.asList("upper_struct"), adminUser);
		String common1hash = db.getModuleMD5("Common");
		String upper1hash = db.getModuleMD5("Upper");
		db.registerModule(loadSpec("md5", "Common", "2"), adminUser);
		db.refreshModule("Upper", adminUser);
		String common2hash = db.getModuleMD5("Common");
		Assert.assertFalse(common1hash.equals(common2hash));
		String upper2hash = db.getModuleMD5("Upper");
		Assert.assertFalse(upper1hash.equals(upper2hash));
		Assert.assertEquals(db.getLastModuleVersion("Upper"), 
				(long)db.findModuleVersionByMD5("Upper", upper2hash));
		db.registerModule(loadSpec("md5", "Common", "3"), Arrays.asList("unused_struct"), adminUser);
		db.refreshModule("Upper", adminUser);
		String common3hash = db.getModuleMD5("Common");
		Assert.assertFalse(common2hash.equals(common3hash));
		String upper3hash = db.getModuleMD5("Upper");
		Assert.assertTrue(upper2hash.equals(upper3hash));
		Assert.assertEquals(db.getLastModuleVersion("Common"), 
				(long)db.findModuleVersionByMD5("Common", common3hash));
	}
	
	@Test
	public void testRegistration() throws Exception {
		for (int item = 0; item < 2; item++) {
			db.requestModuleRegistration("NewModule", adminUser);
			List<OwnerInfo> list = db.getNewModuleRegistrationRequests(adminUser);
			Assert.assertEquals(1, list.size());
			Assert.assertEquals("NewModule", list.get(0).getModuleName());
			if (item == 0) {
				db.refuseModuleRegistrationRequest(adminUser, "NewModule");
			} else {
				db.approveModuleRegistrationRequest(adminUser, "NewModule");
			}
			Assert.assertEquals(0, db.getNewModuleRegistrationRequests(adminUser).size());
		}		
	}
	
	private Map<String, Long> restrict(Object... params) {
		Map<String, Long> restrictions = new HashMap<String, Long>();
		for (int i = 0; i < params.length / 2; i++) {
			restrictions.put((String)params[i * 2], (Long)params[i * 2 + 1]);
		}
		return restrictions;
	}
	
	private String getStorageObjects() throws Exception {
		Map<String, Long> ret = storage.listObjects();
		for (String key : new ArrayList<String>(ret.keySet())) 
			if (ret.get(key) == 0)
				ret.remove(key);
		return "" + ret;
	}

	private void checkTypeVer(String module, String typeName, String version) throws Exception {
		Assert.assertEquals(version, db.getLatestTypeVersion(new TypeDefName(module, typeName)));
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
		db.approveModuleRegistrationRequest(adminUser, moduleName);
	}
	
	private void releaseModule(String module, String user) throws Exception {
		db.releaseModule(module, user);
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
