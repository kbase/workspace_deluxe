package us.kbase.typedobj.db.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.mongodb.DB;
import com.mongodb.MongoClient;

import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.FuncDetailedInfo;
import us.kbase.typedobj.db.ModuleDefId;
import us.kbase.typedobj.db.ModuleInfo;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.OwnerInfo;
import us.kbase.typedobj.db.RefInfo;
import us.kbase.typedobj.db.SemanticVersion;
import us.kbase.typedobj.db.TypeChange;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.db.TypeDetailedInfo;
import us.kbase.typedobj.db.TypeStorage;
import us.kbase.typedobj.exceptions.NoSuchFuncException;
import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchPrivilegeException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.typedobj.exceptions.SpecParseException;
import us.kbase.typedobj.exceptions.TypeStorageException;

@RunWith(Parameterized.class)
public class TypeRegisteringTest {
	private TestTypeStorage storage = null;
	private TypeDefinitionDB db = null;
	private final boolean useMongo;
	private static String adminUser = "admin";
	
	private static MongoController mongo = null;
	
	private static String longstr = "";
	static {
		for (int i = 0; i < 256; i++) {
			longstr += "a";
		}
	}

	public static void main(String[] args) throws Exception {
		boolean[] storageParams = {false, true};
		for (boolean useMongoParam : storageParams) {
			TypeRegisteringTest test = new TypeRegisteringTest(useMongoParam);
			String[] methods = {
					"testSimple",
					"testDescr",
					"testBackward",
					"testRollback",
					"testRestrict",
					"testMD5",
					"testRegistration",
					"testError",
					"testStop",
					"testDeps",
					"testOwnership",
					"testEmpty",
			};
			for (String method : methods) {
				System.out.println("o-------------------------------------------------------");
				System.out.println("| Test method: " + method + ", useMongo=" + useMongoParam);
				System.out.println("o-------------------------------------------------------");
				test.cleanupBefore();
				try {
					TypeRegisteringTest.class.getMethod(method).invoke(test);
				} catch (InvocationTargetException ex) {
					if (ex.getCause() != null) {
						if (ex.getCause() instanceof Exception) {
							throw (Exception)ex.getCause();
						} else if (ex.getCause() instanceof RuntimeException) {
							throw (RuntimeException)ex.getCause();							
						} else if (ex.getCause() instanceof Error) {
							throw (Error)ex.getCause();							
						}
					}
					throw ex;
				} finally {
					//test.cleanupAfter();
				}
			}
		}
	}

	public TypeRegisteringTest(boolean useMongoParam) throws Exception {
		useMongo = useMongoParam;
		Path d = Paths.get(TestCommon.getTempDir())
				.resolve("TypeRegisteringTest");
		Files.createDirectories(d);
		TypeStorage innerStorage;
		if (useMongo) {
			innerStorage = new MongoTypeStorage(createMongoDbConnection());
		} else {
			innerStorage = new FileTypeStorage(d.toFile().getAbsolutePath());
		}
		storage = TestTypeStorageFactory.createTypeStorageWrapper(innerStorage);
		db = new TypeDefinitionDB(storage);
	}
	
	public static DB createMongoDbConnection() throws Exception {
		if (mongo == null) {
			mongo = new MongoController(TestCommon.getMongoExe(),
					Paths.get(TestCommon.getTempDir()),
					TestCommon.useWiredTigerEngine());
			System.out.println("Using mongo temp dir " + 
					mongo.getTempDir());
		}
		DB mdb = new MongoClient("localhost:" + mongo.getServerPort())
			.getDB("TypeRegisteringTest");
		TestCommon.destroyDB(mdb);
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
		db.cleanupCaches();
	}
	
	@After
	public void cleanupAfter() throws Exception {
		cleanupBefore();
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (mongo != null) {
			mongo.destroy(TestCommon.getDeleteTempFiles());
		}
	}
	
	@Test
	public void testSimple() throws Exception {
		String user = "Owner";
		String taxonomySpec = loadSpec("simple", "Taxonomy");
		initModule("Taxonomy", user);
		readOnlyMode();
		try {
			db.registerModule(taxonomySpec, Arrays.asList("taxon"), Collections.<String>emptyList(), "Nobody", true);
			fail();
		} catch (NoSuchPrivilegeException ex) {
			assertThat(ex.getMessage().equals(
					"User Nobody is not in list of owners of module Taxonomy"), is(true));
		}
		db.registerModule(taxonomySpec, Arrays.asList("taxon"), Collections.<String>emptyList(), user, true);
		storage.removeAllTypeStorageListeners();
		db.registerModule(taxonomySpec, Arrays.asList("taxon"), user);
		releaseModule("Taxonomy", user);
		assertThat(db.getModulesByOwner(user).get(0), is("Taxonomy"));
		assertThat(db.isValidType(new TypeDefName("Taxonomy", "taxon")), is(true));
		assertThat(db.isValidType(new TypeDefName("Taxonomy", "taxon2")), is(false));
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
		releaseModule("Regulation", user);
		checkTypeDep("Regulation", "binding_site", "Regulation", "regulator", "1.0", true);
		String reg2spec = loadSpec("simple", "Regulation", "2");
		Map<AbsoluteTypeDefId, String> typeToJsonSchema1 = db.getJsonSchemasForAllTypes(new ModuleDefId("Regulation"));
		readOnlyMode();
		Map<TypeDefName, TypeChange> changes = db.registerModule(reg2spec, Arrays.asList("new_regulator"), 
				Collections.<String>emptyList(), user, true);
		assertThat(changes.size(), is(3));
		assertThat(changes.get(new TypeDefName("Regulation.new_regulator")).isUnregistered(),
				is(false));
		assertThat(changes.get(new TypeDefName("Regulation.new_regulator"))
				.getTypeVersion().getVerString(), is("0.1"));
		assertThat(changes.get(new TypeDefName("Regulation.binding_site")).isUnregistered(),
				is(false));
		assertThat(changes.get(new TypeDefName("Regulation.binding_site"))
				.getTypeVersion().getVerString(), is("2.0"));
		assertThat(changes.get(new TypeDefName("Regulation.regulator")).isUnregistered(),
				is(true));
		storage.removeAllTypeStorageListeners();
		db.registerModule(reg2spec, Arrays.asList("new_regulator"), Collections.<String>emptyList(), user);
		releaseModule("Regulation", user);
		checkTypeDep("Regulation", "binding_site", "Regulation", "regulator", null, false);
		checkTypeDep("Regulation", "binding_site", "Regulation", "new_regulator", "1.0", true);
		assertThat(db.getAllModuleVersions("Regulation").size(), is(2));
		assertThat(db.getLatestTypeVersion(new TypeDefName("Regulation.binding_site")), is("2.0"));
		Map<AbsoluteTypeDefId, String> typeToJsonSchema2 = db.getJsonSchemasForAllTypes(new ModuleDefId("Regulation"));
		String json1 = typeToJsonSchema1.get(new AbsoluteTypeDefId(new TypeDefName("Regulation.binding_site"), 1, 0));
		String json2 = typeToJsonSchema2.get(new AbsoluteTypeDefId(new TypeDefName("Regulation.binding_site"), 2, 0));
		assertThat(json2, is(notNullValue()));
		assertThat(json1.equals(json2), is(false));
		Set<RefInfo> depFuncs = db.getFuncRefsByRef(new TypeDefId("Regulation.new_regulator"));
		assertThat(depFuncs.size(), is(1));
		TypeDetailedInfo tdi = db.getTypeDetailedInfo(new AbsoluteTypeDefId(new TypeDefName("Regulation.binding_site"), 2, 0), true, user);
		assertThat(tdi.getSpecDef().contains("{"), is(true));
		FuncDetailedInfo fdi = db.getFuncDetailedInfo("Regulation", "get_regulator_binding_sites_and_genes", null, true, user);
		assertThat(fdi.getSpecDef().contains("("), is(true));
	}
			
	@Test
	public void testDescr() throws Exception {
		String sequenceSpec = loadSpec("descr", "Descr");
		initModule("Descr", adminUser);
		db.registerModule(sequenceSpec, Arrays.asList("sequence_id", "sequence_pos"), adminUser);
		releaseModule("Descr", adminUser);
		assertThat(db.getModuleDescription("Descr"), is("Descr module.\n\nEnd of comment."));
		assertThat(db.getTypeDescription(new TypeDefId("Descr.sequence_id")), is(""));
		assertThat(db.getFuncDescription("Descr", "invis_func", null), is(""));
		assertThat(db.getTypeDescription(new TypeDefId("Descr.sequence_pos")),
				is("position of fragment on a sequence"));
		assertThat(db.getFuncDescription("Descr", "super_func", null), is("The super function."));
	}
	
	@Test
	public void testBackward() throws Exception {
		String regulationSpec = loadSpec("backward", "Regulation");
		initModule("Regulation", adminUser);
		db.registerModule(regulationSpec, Arrays.asList("gene", "binding_site"), adminUser);
		db.releaseModule("Regulation", adminUser, false);
		checkTypeVer("Regulation", "binding_site", "1.0");
		String reg2spec = loadSpec("backward", "Regulation", "2");
		Map<TypeDefName, TypeChange> changes = db.registerModule(reg2spec, Arrays.<String>asList(), 
				Collections.<String>emptyList(), adminUser);
		assertThat(changes.size(), is(2));
		assertThat(changes.get(new TypeDefName("Regulation.gene")).getTypeVersion().getVerString(),
				is("1.1"));
		assertThat(changes.get(new TypeDefName("Regulation.binding_site"))
				.getTypeVersion().getVerString(), is("2.0"));
		db.releaseModule("Regulation", adminUser, false);
		checkFuncVer("Regulation", "get_gene_descr", "2.0");
		checkFuncVer("Regulation", "get_nearest_binding_sites", "2.0");
		checkFuncVer("Regulation", "get_regulated_genes", "1.1");
		String reg3spec = loadSpec("backward", "Regulation", "3");
		Map<TypeDefName, TypeChange> changes3 = db.registerModule(reg3spec, Arrays.<String>asList(), 
				Collections.<String>emptyList(), adminUser);
		assertThat(changes3.size(), is(2));
		assertThat(changes3.get(new TypeDefName("Regulation.gene"))
				.getTypeVersion().getVerString(), is("1.2"));
		assertThat(changes3.get(new TypeDefName("Regulation.binding_site"))
				.getTypeVersion().getVerString(), is("3.0"));
		db.releaseModule("Regulation", adminUser, false);
		checkFuncVer("Regulation", "get_gene_descr", "2.0");
		checkFuncVer("Regulation", "get_nearest_binding_sites", "3.0");
		checkFuncVer("Regulation", "get_regulated_genes", "1.2");
		String reg4spec = loadSpec("backward", "Regulation", "4");
		Map<TypeDefName, TypeChange> changes4 = db.registerModule(reg4spec, Arrays.<String>asList(), 
				Collections.<String>emptyList(), adminUser);
		assertThat(changes4.size(), is(2));
		assertThat(changes4.get(new TypeDefName("Regulation.gene"))
				.getTypeVersion().getVerString(), is("2.0"));
		assertThat(changes4.get(new TypeDefName("Regulation.binding_site"))
				.getTypeVersion().getVerString(), is("4.0"));
		checkTypeVer("Regulation", "binding_site", "3.0");
		assertThat(db.findModuleVersionsByTypeVersion(
				new TypeDefId("Regulation.binding_site", "3.0"), null).size(), is(1));
		assertThat(db.getAllModuleVersions("Regulation").size(), is(3));
		List<AbsoluteTypeDefId> releaseVers = db.releaseModule("Regulation", adminUser, false);
		String bindingSiteTypeVer = null;
		for (AbsoluteTypeDefId typeDef : releaseVers) {
			if (typeDef.getType().getTypeString().equals("Regulation.binding_site"))
				bindingSiteTypeVer = typeDef.getVerString();
		}
		assertThat(bindingSiteTypeVer, is("4.0"));
		List<ModuleDefId> verList1 = db.findModuleVersionsByTypeVersion(TypeDefId.fromTypeString("Regulation.binding_site"), null);
		assertThat(verList1.size(), is(1));
		long lastRegVer = db.getLatestModuleVersion("Regulation");
		assertThat((long)verList1.get(0).getVersion(), is(lastRegVer));
		List<ModuleDefId> verList2 = db.findModuleVersionsByTypeVersion(new TypeDefId("Regulation.binding_site", "4.0"), null);
		assertThat(verList2.size(), is(1));
		assertThat((long)verList2.get(0).getVersion(), is(lastRegVer));
		checkFuncVer("Regulation", "get_gene_descr", "3.0");
		checkFuncVer("Regulation", "get_nearest_binding_sites", "4.0");
		checkFuncVer("Regulation", "get_regulated_genes", "2.0");
		assertThat(db.getAllModuleVersions("Regulation").size(), is(4));
		// Annotations
		String annMod = "Annotations";
		initModule(annMod, adminUser);
		db.registerModule(loadSpec("backward", annMod), Arrays.asList("type1", "type2", "type3"), adminUser);
		db.releaseModule(annMod, adminUser, false);
		db.registerModule(loadSpec("backward", annMod, "2"), Collections.<String>emptyList(), adminUser);
		Map<String, String> map2 = asMap(db.releaseModule(annMod, adminUser, false));
		assertThat(map2.get("Annotations.type2"), is("2.0"));
		assertThat(map2.get("Annotations.type3"), is("2.0"));
		db.registerModule(loadSpec("backward", annMod, "3"), Collections.<String>emptyList(), adminUser);
		Map<String, String> map3 = asMap(db.releaseModule(annMod, adminUser, false));
		assertThat(map3.get("Annotations.type2"), is("3.0"));
		assertThat(map3.get("Annotations.type3"), is("2.0"));
		db.registerModule(loadSpec("backward", annMod, "4"), Collections.<String>emptyList(), adminUser);
		Map<String, String> map4 = asMap(db.releaseModule(annMod, adminUser, false));
		assertThat(map4.get("Annotations.type2"), is("4.0"));
		assertThat(map4.get("Annotations.type3"), is("3.0"));
		// Id refs
		String exprName = "Expression";
		String exprSpec = loadSpec("backward", exprName);
		initModule(exprName, adminUser);
		db.registerModule(exprSpec, Arrays.asList("ExpressionSeries"), adminUser);
		db.releaseModule(exprName, adminUser, false);
		checkTypeVer(exprName, "ExpressionSeries", "1.0");
		exprSpec = loadSpec("backward", exprName, "2");
		db.registerModule(exprSpec, Collections.<String>emptyList(), adminUser);
		db.releaseModule(exprName, adminUser, false);
		checkTypeVer(exprName, "ExpressionSeries", "2.0");
	}
	
	private static Map<String, String> asMap(List<AbsoluteTypeDefId> list) {
		Map<String, String> ret = new TreeMap<String, String>();
		for (AbsoluteTypeDefId item : list)
			ret.put(item.getType().getTypeString(), item.getVerString());
		return ret;
	}
	
	@Test
	public void testRollback() throws Exception {
		String spec1 = loadSpec("rollback", "First");
		initModule("First", adminUser);
		long verAfterInit = db.getLatestModuleVersion("First");
		int typesAfterInit = db.getAllRegisteredTypes("First").size();
		int funcsAfterInit = db.getAllRegisteredFuncs("First").size();
		String objAfterInit = getStorageObjects();
		for (String errMethod : Arrays.asList(
				"writeTypeSchemaRecord", "writeTypeParseRecord", "writeFuncParseRecord", "writeModuleRecords", "addRefs")) {
			storage.removeAllTypeStorageListeners();
			withErrorAfterMethod(errMethod);
			assertThat(storage.getTypeStorageListeners().size(), is(1));
			try {
				db.registerModule(spec1, Arrays.asList("seq_id", "seq_pos"), adminUser);
				fail("Error should occur before this line");
			} catch (Exception ex) {
				assertThat(ex.getMessage(), is("Method has test error at the end of body."));
				assertThat(db.getLatestModuleVersion("First"), is(verAfterInit));
				assertThat(db.getAllRegisteredTypes("First").size(), is(typesAfterInit));
				assertThat(db.getAllRegisteredFuncs("First").size(), is(funcsAfterInit));
				assertThat(getStorageObjects(), is(objAfterInit));
			}
		}
	}
	
	@Test
	public void testRestrict() throws Exception {
		initModule("Common", adminUser);
		db.registerModule(loadSpec("restrict", "Common"), Arrays.asList("common_struct"), adminUser);
		db.releaseModule("Common", adminUser, false);
		long commonVer1 = db.getLatestModuleVersion("Common");
		initModule("Middle", adminUser);
		db.registerModule(loadSpec("restrict", "Middle"), Arrays.asList("middle_struct"), adminUser);
		db.releaseModule("Middle", adminUser, false);		
		long middleVer1 = db.getLatestModuleVersion("Middle");
		db.registerModule(loadSpec("restrict", "Common", "2"), Collections.<String>emptyList(), adminUser);
		db.releaseModule("Common", adminUser, false);
		long commonVer2 = db.getLatestModuleVersion("Common");
		initModule("Upper", adminUser);
		try {
			db.registerModule(loadSpec("restrict", "Upper"), Arrays.asList("upper_struct"), adminUser);
			fail();
		} catch (SpecParseException ex) {
			assertThat(ex.getMessage().contains("Incompatible module dependecies: Common"),
					is(true));
		}
		db.registerModule(loadSpec("restrict", "Upper"), Arrays.asList("upper_struct"), 
				Collections.<String>emptyList(), adminUser, true, restrict("Common", commonVer1));
		db.refreshModule("Middle", adminUser);
		db.releaseModule("Middle", adminUser, false);		
		try {
			db.registerModule(loadSpec("restrict", "Upper"), Arrays.asList("upper_struct"),
					Collections.<String>emptyList(), adminUser, false, restrict("Common", commonVer1));
			fail();
		} catch (SpecParseException ex) {
			assertThat(ex.getMessage().contains("Version of dependent module Common"), is(true));
		}
		try {
			db.registerModule(loadSpec("restrict", "Upper"), Arrays.asList("upper_struct"),
					Collections.<String>emptyList(), adminUser, false, 
					restrict("Common", commonVer2, "Middle", middleVer1));
			fail();
		} catch (SpecParseException ex) {
			assertThat(ex.getMessage().contains("Version of dependent module Common"), is(true));
		}
		Map<TypeDefName, TypeChange> ret = db.registerModule(loadSpec("restrict", "Upper"), 
				Arrays.asList("upper_struct"), Collections.<String>emptyList(), adminUser, false, 
				restrict("Common", commonVer1, "Middle", middleVer1));
		assertThat(ret.size(), is(1));
		assertThat(ret.get(new TypeDefName("Upper.upper_struct")).getTypeVersion().getVerString(),
				is("0.1"));
		db.releaseModule("Upper", adminUser, false);
		assertThat( db.findModuleVersionsByTypeVersion(
				new TypeDefId("Upper.upper_struct", "1"), null).size(), is(1));
	}
	
	/**
	 * It's not Unit test. It measures execution time (with indexes and without).
	 * @throws Exception
	 */
	public void testIndexes() throws Exception {
		long time = System.currentTimeMillis();
		String regulationSpec = loadSpec("backward", "Regulation");
		initModule("Regulation", adminUser);
		long initVer = db.getLatestModuleVersion("Regulation");
		db.registerModule(regulationSpec, Arrays.asList("sequence_pos1", "gene", "sequence_pos2", 
				"binding_site"), adminUser);
		long regVer = db.getLatestModuleVersion("Regulation");
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
			assertThat(refCount, is(3));
			refCount = 0;
			for (String func : info.getFuncs().keySet()) {
				String funcVer = info.getFuncs().get(func).getFuncVersion();
				db.getFuncParsingDocument(info.getModuleName(), func, funcVer);
				refCount += db.getFuncRefsByDep(info.getModuleName(), func, funcVer).size();
			}
			assertThat(refCount, is(2));
		}
		System.out.println("Search time: " + (System.currentTimeMillis() - time));
	}
	
	@Test
	public void testMD5() throws Exception {
		initModule("Common", adminUser);
		db.registerModule(loadSpec("md5", "Common"), Arrays.asList("common_struct"), adminUser);
		db.releaseModule("Common", adminUser, false);
		initModule("Upper", adminUser);
		db.registerModule(loadSpec("md5", "Upper"), Arrays.asList("upper_struct"), adminUser);
		db.releaseModule("Upper", adminUser, false);
		String common1hash = db.getModuleMD5("Common");
		String upper1hash = db.getModuleMD5("Upper");
		db.registerModule(loadSpec("md5", "Common", "2"), adminUser);
		db.releaseModule("Common", adminUser, false);
		db.refreshModule("Upper", adminUser);
		db.releaseModule("Upper", adminUser, false);
		String common2hash = db.getModuleMD5("Common");
		assertThat(common1hash.equals(common2hash), is(false));
		String upper2hash = db.getModuleMD5("Upper");
		assertThat(upper1hash.equals(upper2hash), is(false));
		assertThat(db.findModuleVersionsByMD5("Upper", upper2hash).contains(
				new ModuleDefId("Upper", db.getLatestModuleVersion("Upper"))), is(true));
		db.registerModule(loadSpec("md5", "Common", "3"), Arrays.asList("unused_struct"), adminUser);
		db.releaseModule("Common", adminUser, false);
		db.refreshModule("Upper", adminUser);
		db.releaseModule("Upper", adminUser, false);
		String common3hash = db.getModuleMD5("Common");
		assertThat(common2hash.equals(common3hash), is(false));
		String upper3hash = db.getModuleMD5("Upper");
		assertThat(upper2hash.equals(upper3hash), is(true));
		assertThat(db.findModuleVersionsByMD5("Common", common3hash).contains(
				new ModuleDefId("Common", db.getLatestModuleVersion("Common"))), is(true));
		assertThat(db.getModuleInfo(new ModuleDefId("Common")).getMd5hash(), is(common3hash));
	}
	
	@Test
	public void testRegistration() throws Exception {
		for (int item = 0; item < 2; item++) {
			db.requestModuleRegistration("NewModule", adminUser);
			List<OwnerInfo> list = db.getNewModuleRegistrationRequests(adminUser, true);
			assertThat(list.size(), is(1));
			assertThat(list.get(0).getModuleName(), is("NewModule"));
			if (item == 0) {
				db.refuseModuleRegistrationRequest(adminUser, "NewModule", true);
			} else {
				db.approveModuleRegistrationRequest(adminUser, "NewModule", true);
			}
			assertThat(db.getNewModuleRegistrationRequests(adminUser, true).size(), is(0));
		}
		
		failReg(null, adminUser, "Module name cannot be null or the empty string");
		failReg("", adminUser, "Module name cannot be null or the empty string");
		failReg("a-b", adminUser, "Illegal character in Module name a-b: -");
		failReg(longstr, adminUser, "Module name size is > 255 bytes");
	}
	
	private void failReg(String module, String user, String exp) {
		try {
			db.requestModuleRegistration(module, user);
			fail("expected exception");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(), is(exp));
		} catch (TypeStorageException e) {
			fail("Wrong exception type");
		}
		
	}

	@Test
	public void testError() throws Exception {
		// bad module names will be caught at module registration
		
		List<String> mttypes = new ArrayList<String>();
		failRegister("LongTypeName", mttypes, adminUser, "error",
				"Type name size is > 255 bytes");
		failRegister("LongFuncName", mttypes, adminUser, "error",
				"Function name size is > 255 bytes");
		
		initModule("Test", adminUser);
		try {
			db.registerModule(loadSpec("error", "Test"), Arrays.asList("bebebe"), adminUser);
			fail();
		} catch (SpecParseException ex) {
			assertThat(ex.getMessage().contains("bebebe"), is(true));
		}
		initModule("DoubleModule", adminUser);
		try {
			db.registerModule(loadSpec("error", "DoubleModule"), adminUser);
			fail();
		} catch (SpecParseException ex) {
			assertThat(ex.getMessage().contains("only one"), is(true));
		}
		initModule("Empty", adminUser);
		try {
			db.registerModule("module Empty {};", Collections.<String>emptyList(), Collections.<String>emptyList(),
					adminUser, false, Collections.<String,Long>emptyMap(), -1L);
			fail();
		} catch (SpecParseException ex) {
			assertThat(ex.getMessage().contains(
					"Concurrent modification: previous module version is "), is(true));
		}
		initModule("Common", adminUser);
		db.registerModule(loadSpec("error", "Common"), adminUser);
		try {
			db.registerModule(loadSpec("error", "Common"), adminUser);
			fail();
		} catch (SpecParseException ex) {
			assertThat(ex.getMessage().contains("There is no difference"), is(true));
		}
		try {
			db.getJsonSchema(TypeDefId.fromTypeString("UnknownModule.UnknownType"));
			fail();
		} catch (NoSuchModuleException ex) {
			assertThat(ex.getMessage().contains("Module doesn't exist: UnknownModule"), is(true));
		}
		try {
			db.getJsonSchema(TypeDefId.fromTypeString("Common.UnknownType"));
			fail();
		} catch (NoSuchTypeException ex) {
			assertThat(ex.getMessage().contains("Unable to locate type: Common.UnknownType"),
					is(true));
		}
		try {
			db.getJsonSchema(TypeDefId.fromTypeString("Common.UnknownType-1.0"));
			fail();
		} catch (NoSuchTypeException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains(
					"Unable to locate type: Common.UnknownType-1.0"), is(true));
		}
		initModule("StructDuplication", adminUser);
		try {
			db.registerModule(loadSpec("error", "StructDuplication"), Arrays.asList("my_struct"), adminUser);
			fail();
		} catch (SpecParseException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains(
					"Name duplication for field [val1]"), is(true));
		}
	}

	private void failRegister(String mod, List<String> types, String user,
			String test, String exp) throws Exception {
		initModule(mod, user);
		try {
			db.registerModule(loadSpec(test, mod), types, user);
			fail("registered bad type");
		} catch (SpecParseException ex) {
			assertThat("correct exception", ex.getLocalizedMessage(), is (exp));
		}
	}
	
	@Test
	public void testStop() throws Exception {
		String moduleName = "Regulation";
		initModule(moduleName, adminUser);
		db.registerModule(loadSpec("stop", moduleName), Arrays.asList("regulator", "binding_site"), adminUser);
		releaseModule(moduleName, adminUser);
		long lastModVer = db.getLatestModuleVersion(moduleName);
		String lastTypeVer = db.getLatestTypeVersion(new TypeDefName(moduleName, "regulator"));
		String lastFuncVer = db.getLatestFuncVersion(moduleName, "get_genome");
		db.stopModuleSupport(moduleName, adminUser, true);
		try {
			releaseModule(moduleName, adminUser);
			fail();
		} catch (NoSuchModuleException ex) {}
		assertThat(new HashSet<String>(db.getAllRegisteredModules()).contains(moduleName),
				is(false));
		try {
			db.getLatestModuleVersion(moduleName);
			fail();
		} catch (NoSuchModuleException ex) {}
		try {
			db.getAllModuleVersions(moduleName);
			fail();
		} catch (NoSuchModuleException ex) {}
		try {
			db.getModuleInfo(moduleName);
			fail();
		} catch (NoSuchModuleException ex) {}
		db.getModuleInfo(moduleName, lastModVer);
		try {
			db.getModuleInfo(new ModuleDefId(moduleName));
			fail();
		} catch (NoSuchModuleException ex) {}
		db.getModuleInfo(new ModuleDefId(moduleName, lastModVer));
		try {
			db.getModuleSpecDocument(moduleName);
			fail();
		} catch (NoSuchModuleException ex) {}
		db.getModuleSpecDocument(moduleName, lastModVer);
		try {
			db.getModuleSpecDocument(new ModuleDefId(moduleName));
			fail();
		} catch (NoSuchModuleException ex) {}
		db.getModuleSpecDocument(new ModuleDefId(moduleName, lastModVer));
		try {
			db.getModuleMD5(moduleName);
			fail();
		} catch (NoSuchModuleException ex) {}
		String md5 = db.getModuleMD5(moduleName, lastModVer);
		db.findModuleVersionsByMD5(moduleName, md5);
		try {
			db.getModuleDescription(moduleName);
			fail();
		} catch (NoSuchModuleException ex) {}
		db.getModuleDescription(moduleName, lastModVer);
		db.getModuleOwners(moduleName);
		assertThat(new HashSet<String>(db.getModulesByOwner(adminUser)).contains(moduleName),
				is(false));
		//// Types
		try {
			db.getAllRegisteredTypes(moduleName);
			fail();
		} catch (NoSuchModuleException ex) {}
		try {
			db.getAllRegisteredTypes(new ModuleDefId(moduleName));
			fail();
		} catch (NoSuchModuleException ex) {}
		db.getAllRegisteredTypes(new ModuleDefId(moduleName, lastModVer));
		try {
			db.getLatestTypeVersion(new TypeDefName(moduleName, "regulator"));
			fail();
		} catch (NoSuchModuleException ex) {}
		try {
			db.getTypeMd5Version(new TypeDefId(new TypeDefName(moduleName, "regulator")), null).getMd5();
			fail();
		} catch (NoSuchTypeException ex) {}
		String typeMd5 = db.getTypeMd5Version(new TypeDefId(moduleName + ".regulator", lastTypeVer), null).getMd5().getMD5();
		assertThat(typeMd5, is(notNullValue()));
		try {
			db.getJsonSchemaDocument(new TypeDefName(moduleName, "regulator"));
			fail();
		} catch (NoSuchTypeException ex) {}
		try {
			db.getJsonSchemaDocument(new TypeDefId(new TypeDefName(moduleName, "regulator")));
			fail();
		} catch (NoSuchTypeException ex) {}
		String jsonSchema = db.getJsonSchemaDocument(new TypeDefId(moduleName + ".regulator", lastTypeVer));
		assertThat(db.getJsonSchemaDocument(new TypeDefId(moduleName + ".regulator", typeMd5)),
				is(jsonSchema));
		assertThat(db.getJsonSchemaDocument(new AbsoluteTypeDefId(
				new TypeDefName(moduleName, "regulator"), new MD5(typeMd5))), is(jsonSchema));
		try {
			db.getTypeParsingDocument(new TypeDefName(moduleName, "regulator"));
			fail();
		} catch (NoSuchTypeException ex) {}
		try {
			db.getTypeParsingDocument(new TypeDefId(new TypeDefName(moduleName, "regulator")));
			fail();
		} catch (NoSuchTypeException ex) {}
		assertThat(db.getTypeParsingDocument(new TypeDefId(moduleName + ".regulator", lastTypeVer))
					.getData(),
				is(db.getTypeParsingDocument(new TypeDefId(moduleName + ".regulator", typeMd5))
						.getData()));
		try {
			db.getTypeRefsByDep(new TypeDefId(new TypeDefName(moduleName, "regulator")));
			fail();
		} catch (NoSuchTypeException ex) {}
		db.getTypeRefsByDep(new TypeDefId(moduleName + ".regulator", lastTypeVer));
		try {
			db.getTypeRefsByRef(new TypeDefId(new TypeDefName(moduleName, "regulator")));
			fail();
		} catch (NoSuchTypeException ex) {}
		db.getTypeRefsByRef(new TypeDefId(moduleName + ".regulator", lastTypeVer));
		try {
			db.getFuncRefsByRef(new TypeDefId(new TypeDefName(moduleName, "regulator")));
			fail();
		} catch (NoSuchTypeException ex) {}
		db.getFuncRefsByRef(new TypeDefId(moduleName + ".regulator", lastTypeVer));
		try {
			db.stopTypeSupport(new TypeDefName(moduleName, "regulator"), adminUser, "", false);
			fail();
		} catch (NoSuchModuleException ex) {}
		//// Functions
		try {
			db.getAllRegisteredFuncs(moduleName);
			fail();
		} catch (NoSuchModuleException ex) {}
		try {
			db.getLatestFuncVersion(moduleName, "get_genome");
			fail();
		} catch (NoSuchModuleException ex) {}
		try {
			db.getFuncParsingDocument(moduleName, "get_genome");
			fail();
		} catch (NoSuchFuncException ex) {}
		db.getFuncParsingDocument(moduleName, "get_genome", lastFuncVer);
		try {
			db.getFuncRefsByDep(moduleName, "get_genome");
			fail();
		} catch (NoSuchModuleException ex) {}
		db.getFuncRefsByDep(moduleName, "get_genome", lastFuncVer);
		//// Other
		try {
			db.registerModule(loadSpec("stop", moduleName), Arrays.asList("regulator", "binding_site"), adminUser);
			fail();
		} catch (NoSuchModuleException ex) {}
		try {
			db.registerModule(loadSpec("stop", "Dependant"), Arrays.asList("new_type"), adminUser);
			fail();
		} catch (NoSuchModuleException ex) {
			assertThat(ex.getMessage().contains("Module Regulation is no longer supported"),
					is(true));
		}
		initModule("Dependant", adminUser);
		db.registerModule(loadSpec("stop", "Dependant"), Arrays.asList("new_type"), Collections.<String>emptyList(), 
				adminUser, false, restrict(moduleName, lastModVer));
		db.resumeModuleSupport(moduleName, adminUser, true);
		db.registerModule(loadSpec("stop", moduleName, "2"), adminUser);
		db.releaseModule(moduleName, adminUser, false);
		try {
			db.refreshModule("Dependant", adminUser);
			fail();
		} catch (SpecParseException ex) {}
		db.registerModule(loadSpec("stop", "Dependant"), Collections.<String>emptyList(), Collections.<String>emptyList(), 
				adminUser, false, restrict(moduleName, lastModVer), null, "Test message", false);
		db.releaseModule("Dependant", adminUser, false);
		assertThat(db.getModuleInfo("Dependant").getUploadComment(), is("Test message"));
	}

	@Test
	public void testDeps() throws Exception {
		initModule("SomeModule", adminUser);
		db.registerModule(loadSpec("deps", "SomeModule"), Arrays.asList("AType"), adminUser);
		releaseModule("SomeModule", adminUser);
		initModule("DepModule", adminUser);
		db.registerModule(loadSpec("deps", "DepModule"), Arrays.asList("BType"), adminUser);
		TypeDetailedInfo tdi = db.getTypeDetailedInfo(new TypeDefId("DepModule.BType", "0.1"), false, adminUser);
		assertThat(tdi.getUsedTypeDefIds().size(), is(1));
		assertThat(tdi.getUsingFuncDefIds().size(), is(1));
		assertThat(db.getFuncDetailedInfo("DepModule", "new_call", "0.1", false, adminUser)
				.getUsedTypeDefIds().size(), is(1));
		assertThat(db.getTypeDetailedInfo(
				new TypeDefId("SomeModule.AType", "1.0"), false, adminUser)
						.getUsingTypeDefIds().size(), is(1));
		releaseModule("DepModule", adminUser);
		Set<RefInfo> funcs = db.getFuncRefsByRef(new TypeDefId("DepModule.BType", "1.0"));
		assertThat(funcs.size(), is(1));
		
	}
	
	@Test
	public void testOwnership() throws Exception {
		String module = "SomeModule";
		try {
			db.registerModule(loadSpec("deps", module), Arrays.asList("AType"), "author");
			fail();
		} catch (NoSuchModuleException ex) {
			assertThat(ex.getMessage(), is("Module SomeModule was not initialized. For that " +
					"you must request ownership of the module, and your request must be " +
					"approved."));
		}
		initModule(module, "author");
		db.registerModule(loadSpec("deps", module), Arrays.asList("AType"), "author");
		try {
			db.getModuleSpecDocument(new ModuleDefId(module), "stranger", false);		// bad
			fail();
		} catch (NoSuchModuleException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains(
					"Module wasn't uploaded: SomeModule"), is(true));
		}
		db.addOwnerToModule(adminUser, module, "friend", false, true);
		db.getModuleSpecDocument(new ModuleDefId(module), "friend", false);
		db.getJsonSchemaDocument(new TypeDefId(module + ".AType"), "friend");
		db.getTypeDetailedInfo(new TypeDefId(module + ".AType"), false, "friend");
		db.getFuncDetailedInfo(module, "aFunc", null, false, "friend");
		try {
			db.getTypeDetailedInfo(new TypeDefId(module + ".AType", "0.2"), false, "friend");
			fail();
		} catch (NoSuchTypeException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains(
					"Unable to locate type: SomeModule.AType-0.2"), is(true));
		}
		db.removeOwnerFromModule(adminUser, module, "friend", true);
		try {
			db.getModuleSpecDocument(new ModuleDefId(module), "friend", false);		// bad
			fail();
		} catch (NoSuchModuleException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains(
					"Module wasn't uploaded: SomeModule"), is(true));
		}
		try {
			db.getJsonSchemaDocument(new TypeDefId(module + ".AType"), "friend");
			fail();
		} catch (NoSuchTypeException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains(
					"This type wasn't released yet and you should be an owner to access "+
					"unreleased version information"), is(true));
		}
		try {
			db.getTypeDetailedInfo(new TypeDefId(module + ".AType"), false, "friend");
			fail();
		} catch (NoSuchTypeException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains(
					"This type wasn't released yet and you should be an owner to access " +
					"unreleased version information"), is(true));
		}
		try {
			db.getFuncDetailedInfo(module, "aFunc", null, false, "friend");
			fail();
		} catch (NoSuchFuncException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains(
					"This function wasn't released yet and you should be an owner to access " +
					"unreleased version information"), is(true));
		}
		try {
			db.addOwnerToModule("stranger", module, "stranger2", false, false);	// bad
			fail();
		} catch (NoSuchPrivilegeException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains(
					"User stranger is not in list of owners of module SomeModule"), is(true));
		}
		db.addOwnerToModule("author", module, "stranger", false, false);
		db.getModuleSpecDocument(new ModuleDefId(module), "stranger", false);
		try {
			db.addOwnerToModule("stranger", module, "stranger2", false, false);	// bad
			fail();
		} catch (NoSuchPrivilegeException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains(
					"User stranger can not change privileges for module SomeModule"), is(true));
		}
		db.addOwnerToModule("author", module, "stranger", true, false);
		db.getModuleSpecDocument(new ModuleDefId(module), "stranger", false);
		db.addOwnerToModule("stranger", module, "stranger2", false, false);
		db.removeOwnerFromModule("stranger", module, "stranger2", false);
		try {
			db.getModuleSpecDocument(new ModuleDefId(module), "stranger2", false);		// bad
			fail();
		} catch (NoSuchModuleException ex) {
			assertThat(ex.getMessage(), ex.getMessage().contains(
					"Module wasn't uploaded: SomeModule"), is(true));
		}
	}
	
	@Test
	public void testEmpty() throws Exception {
		String module = "EmptyModule";
		initModule(module, "author");
		db.registerModule("module EmptyModule {};", Collections.<String>emptyList(), "author");
		db.registerModule("module EmptyModule {funcdef foo() returns ();};", Collections.<String>emptyList(), "author");
		assertThat(db.getFuncDetailedInfo("EmptyModule", "foo", null, false, "author")
				.getSpecDef(), is("funcdef foo() returns () authentication none;"));
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
		assertThat(db.getLatestTypeVersion(new TypeDefName(module, typeName)), is(version));
	}

	private void checkFuncVer(String module, String funcName, String version) throws Exception {
		assertThat(db.getLatestFuncVersion(module, funcName), is(version));
	}
	
	private void readOnlyMode() {
		storage.addTypeStorageListener(new TypeStorageListener() {
			@Override
			public void onMethodStart(String method, Object[] params)
					throws TypeStorageException {
				if (method.startsWith("add") || method.startsWith("write") || 
						method.startsWith("init") || method.startsWith("remove") || 
						method.startsWith("set") || method.startsWith("change"))
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
		db.approveModuleRegistrationRequest(adminUser, moduleName, true);
	}
	
	private void releaseModule(String module, String user) throws Exception {
		db.releaseModule(module, user, false);
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
		assertThat(ret != null, is(res));
		if (ret != null && refVer != null) {
			assertThat(ret.getRefVersion(), is(refVer));
		}
	}
}
