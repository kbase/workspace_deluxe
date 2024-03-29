package us.kbase.test.typedobj;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.test.common.TestCommon;
import us.kbase.common.utils.sortjson.KeyDuplicationException;
import us.kbase.common.utils.sortjson.TooManyKeysException;
import us.kbase.common.utils.sortjson.UTF8JsonSorterFactory;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.JsonDocumentLocation;
import us.kbase.typedobj.core.LocalTypeProvider;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.ValidatedTypedObject;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.idref.IdReference;
import us.kbase.typedobj.idref.IdReferenceHandlerSet;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactoryBuilder;
import us.kbase.typedobj.idref.IdReferenceType;

public class TypedObjectValidationReportTest {
	
	// needs more tests, just adding tests for new functionality
	
	private static final UTF8JsonSorterFactory SORT_FAC =
			new UTF8JsonSorterFactory(10000);
	
	private static TypeDefinitionDB db;
	private static TypedObjectValidator validator;
	
	private static final String USER = "someUser";
	
	private static Path tempdir;
	
	@BeforeClass
	public static void setupTypeDB() throws Exception {
		//ensure test location is available
		final Path temppath = Paths.get(TestCommon.getTempDir());
		Files.createDirectories(temppath);
		tempdir = Files.createTempDirectory(temppath,
				"TypedObjectValReportTest");
		System.out.println("setting up temporary typed obj database");

		// point the type definition db to point there
		final Path storagedir = tempdir.resolve("typestorage");
		Files.createDirectories(storagedir);
		db = new TypeDefinitionDB(
				new FileTypeStorage(storagedir.toFile().getAbsolutePath()));
		addSpecs();
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		FileUtils.deleteDirectory(tempdir.toFile());
	}
	
	private static void addSpecs() throws Exception {
		String module = "TestIDMap";
		String name = "IDMap";
		String spec =
				"module " + module + " {" +
					"/* @id ws\n */" +
					"typedef string id;" +
					"/* @id foo1 Attrib\n */" +
					"typedef string attrib1;" +
					"/* @id foo1 Attrib Attrib1\n */" +
					"typedef string attrib1_1;" +
					"/* @id foo2 Attrib\n */" +
					"typedef string attrib2;" +
					"/* @optional att att2\n */" +
					"typedef structure {" +
						"mapping<id, id> m;" +
						"tuple<attrib1, int foo, attrib1_1, attrib1, attrib2> att;" +
						"tuple<attrib1, int foo, attrib1_1, attrib1, attrib2> att2;" +
					"} " + name + ";" +
				"};";
		db.requestModuleRegistration(module, USER);
		db.approveModuleRegistrationRequest(USER, module, true);
		db.registerModule(spec, Arrays.asList(name), USER);
		db.releaseModule(module, USER, false);
		validator = new TypedObjectValidator(new LocalTypeProvider(db));
		
	}

	private IdReferenceHandlerSetFactory getFac(final int maxIds) {
		return IdReferenceHandlerSetFactoryBuilder
				.getBuilder(maxIds).build().getFactory(null);
	}
	
	@Test
	public void errors() throws Exception {
		String json = "{\"m\": {\"z\": 1, \"b\": []}}";
		
		IdReferenceHandlerSetFactory fac = getFac(100);
		ValidatedTypedObject tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"),
				fac.createHandlers(String.class).associateObject("foo"));
		List<String> errors = Arrays.asList(
			"instance type (integer) not allowed for ID reference (allowed: [\"string\"]), at /m/z",
			"instance type (array) not allowed for ID reference (allowed: [\"string\"]), at /m/b");
		assertThat("correct errors", tovr.getErrorMessages(), is(errors));
		assertThat("errors not empty", tovr.isInstanceValid(), is(false));
		
		json = "{\"m\": {\"z\": 1, \"b\": \"d\"}}";
		tovr = validator.validate(json, new TypeDefId("TestIDMap.IDMap"),
				fac.createHandlers(String.class));
		errors = Arrays.asList(
				"instance type (integer) not allowed for ID reference (allowed: [\"string\"]), at /m/z");
		assertThat("correct errors", tovr.getErrorMessages(), is(errors));
		assertThat("errors not empty", tovr.isInstanceValid(), is(false));
		
		json = "{\"m\": {\"z\": \"a\", \"b\": \"d\"}}";
		errors = Collections.emptyList();
		tovr = validator.validate(json, new TypeDefId("TestIDMap.IDMap"),
				fac.createHandlers(String.class));
		assertThat("correct errors", tovr.getErrorMessages(), is(errors));
		assertThat("errors not empty", tovr.isInstanceValid(), is(true));
	}

	@Test
	public void noSortFac() throws Exception {
		String json = "{\"m\": {\"z\": \"a\", \"b\": \"d\"}}";
		
		IdReferenceHandlerSetFactory fac = getFac(100);
		fac.addFactory(new DummyIdHandlerFactory(new IdReferenceType("ws"),
						new HashMap<String, String>()));
		IdReferenceHandlerSet<String> handlers =
				fac.createHandlers(String.class).associateObject("foo");
		ValidatedTypedObject tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), handlers);
		handlers.processIDs();
		
		try {
			tovr.sort(null);
			fail("sorted with no factory");
		} catch (NullPointerException npe) {
			assertThat("correct expt msg", npe.getLocalizedMessage(),
					is("Sorter factory cannot be null"));
		}
		TempFilesManager tfm = new TempFilesManager(
				new File(TestCommon.getTempDir()));
		try {
			tovr.sort(null, tfm);
			fail("sorted with no factory");
		} catch (NullPointerException npe) {
			assertThat("correct expt msg", npe.getLocalizedMessage(),
					is("Sorter factory cannot be null"));
		}
		tfm.cleanup();
	}
	
	@Test
	public void findIds() throws Exception {
		String json = "{\"m\": {\"c\": \"a\", \"z\": \"d\"}," +
						"\"att\":[\"a1\", 3, \"a1\", \"a2\", \"a1\"]," +
						"\"att2\":[\"b1\", 3, \"b2\", \"b3\", \"b4\"]}";
		ValidatedTypedObject tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), 
				getFac(1).createHandlers(String.class));
		
		IdReferenceType wsType = new IdReferenceType("ws");
		IdReferenceType foo1Type = new IdReferenceType("foo1");
		IdReferenceType foo2Type = new IdReferenceType("foo2");
		List<String> mtAttribs = new LinkedList<String>();
		List<String> attrib = Arrays.asList("Attrib");
		List<String> attrib1 = Arrays.asList("Attrib", "Attrib1");
		
		checkIDLocation(tovr, wsType, "c", mtAttribs, "/m/c");
		checkIDLocation(tovr, wsType, "a", mtAttribs, "/m/c");
		checkIDLocation(tovr, wsType, "z", mtAttribs, "/m/z");
		checkIDLocation(tovr, wsType, "d", mtAttribs, "/m/z");
		
		checkIDLocation(tovr, foo1Type, "a1", attrib, "/att/0");
		checkIDLocation(tovr, foo1Type, "a1", attrib1, "/att/2");
		checkIDLocation(tovr, foo1Type, "a2", attrib, "/att/3");
		checkIDLocation(tovr, foo2Type, "a1", attrib, "/att/4");
		
		//"failing" tests
		checkIDLocation(tovr, foo1Type, "b1", attrib, "/att2/0");
		checkIDLocation(tovr, foo1Type, "b1", attrib1, null);
		checkIDLocation(tovr, foo2Type, "b1", attrib, null);
		
		checkIDLocation(tovr, foo1Type, "b2", attrib1, "/att2/2");
		checkIDLocation(tovr, foo1Type, "b2", attrib, null);
		checkIDLocation(tovr, foo2Type, "b2", attrib1, null);
		
		checkIDLocation(tovr, foo1Type, "b3", attrib, "/att2/3");
		checkIDLocation(tovr, foo1Type, "b3", attrib1, null);
		checkIDLocation(tovr, foo2Type, "b3", attrib, null);
		
		checkIDLocation(tovr, foo2Type, "b4", attrib, "/att2/4");
		checkIDLocation(tovr, foo1Type, "b4", attrib, null);
		checkIDLocation(tovr, foo2Type, "b4", attrib1, null);
	}

	private void checkIDLocation(ValidatedTypedObject tovr,
			IdReferenceType wsType, final String id, List<String> mtAttribs,
			String expectedLoc) throws IOException {
		JsonDocumentLocation loc = tovr.getIdReferenceLocation(
				new IdReference<String>(wsType, id, mtAttribs));
		if (expectedLoc == null) {
			if (loc != null) {
				fail("expected null location, got " +
						loc.getFullLocationAsString());
			}
		} else {
			if (loc == null) {
				fail("expected " + expectedLoc + " as location, got null");
			}
			assertThat("correct id location", loc.getFullLocationAsString(),
					is(expectedLoc));
		}
	}
	
	@Test
	public void writeWithoutSort() throws Exception {
		// test objects that do not require a sort
		String json = "{\"m\": {\"c\": \"a\", \"z\": \"d\"}}";
		String expectedJson = "{\"m\":{\"c\":\"a\",\"y\":\"whoop\"}}";
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("z", "y");
		refmap.put("d", "whoop");
		refmap.put("c", "c");
		refmap.put("a", "a");
		
		IdReferenceHandlerSetFactory fac = getFac(100);
		fac.addFactory(new DummyIdHandlerFactory(
				new IdReferenceType("ws"), refmap));
		IdReferenceHandlerSet<String> handlers =
				fac.createHandlers(String.class).associateObject("foo");

		TempFilesManager tfm = new TempFilesManager(
				new File(TestCommon.getTempDir()));
		tfm.cleanup();
		// in memory
		ValidatedTypedObject tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), handlers);
		handlers.processIDs();
		failGetRelabeledSize(tovr);
		failGetMD5(tovr);
		assertThat("incorrect size", tovr.calculateRelabeledSize(), is(27L));
		assertThat("incorrect size", tovr.getRelabeledSize(), is(27L));
		assertThat("incorrect size", tovr.getSize(), is(27L));
		failGetMD5(tovr);
		// uses the naturally sorted path
		tovr.sort(SORT_FAC);
		assertThat("incorrect md5", tovr.getMD5(),
				is(new MD5("b5a128ad62a50790c65d66831eec6e66")));
		String gotjson = IOUtils.toString(tovr.getInputStream(), "UTF-8");
		assertThat("Relabel correctly without sort", gotjson,
				is(expectedJson));
		assertThat("Temp files manager is empty", tfm.isEmpty(), is(true));
		
		// in file
		handlers = fac.createHandlers(String.class).associateObject("foo");
		tfm.cleanup();
		tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), handlers);
		handlers.processIDs();
		// uses the naturally sorted path
		tovr.sort(SORT_FAC, tfm);
		assertThat("incorrect size", tovr.getRelabeledSize(), is(27L));
		assertThat("incorrect size", tovr.getSize(), is(27L));
		assertThat("incorrect md5", tovr.getMD5(),
				is(new MD5("b5a128ad62a50790c65d66831eec6e66")));
		gotjson = IOUtils.toString(tovr.getInputStream(), "UTF-8");
		assertThat("Relabel correctly without sort", gotjson,
				is(expectedJson));
		tovr.destroyCachedResources();
		assertThat("Temp files manager is empty", tfm.isEmpty(), is(true));
	}

	private void failGetMD5(ValidatedTypedObject tovr) {
		try {
			tovr.getMD5();
			fail("got md5 for unsorted data");
		} catch (IllegalStateException e) {
			assertThat("incorrect exception", e.getMessage(),
					is("Must call sort() before getting the MD5"));
		}
	}

	@Test
	public void sortWithNoMapping() throws Exception {
		String json = "{\"z\": \"a\", \"b\": \"d\"}";
		String expectedJson = "{\"b\":\"d\",\"z\":\"a\"}";

		IdReferenceHandlerSetFactory fac = getFac(100);
		IdReferenceHandlerSet<String> handlers =
				fac.createHandlers(String.class).associateObject("foo");

		ValidatedTypedObject tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), handlers);
		handlers.processIDs();
		failGetMD5(tovr);
		tovr.sort(SORT_FAC);
		assertThat("incorrect size", tovr.getRelabeledSize(), is(17L));
		assertThat("incorrect size", tovr.getSize(), is(17L));
		assertThat("incorrect md5", tovr.getMD5(),
				is(new MD5("16903d0745c0f47a90d92d1abd535b12")));
		
		String gotjson = IOUtils.toString(tovr.getInputStream(), "UTF-8");
		assertThat("Relabel correctly without sort", gotjson,
				is(expectedJson));
	}

	@Test
	public void failWriteWithoutSort() throws Exception {
		String json = "{\"m\": {\"b\": \"a\", \"w\": \"d\"}}";
		@SuppressWarnings("unused") //below is just for reference
		String expectedJson = "{\"m\":{\"w\":\"whoop\",\"y\":\"a\"}}";
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("b", "y");
		refmap.put("d", "whoop");
		refmap.put("a", "a");
		refmap.put("w", "w");
		
		IdReferenceHandlerSetFactory fac = getFac(100);
		fac.addFactory(new DummyIdHandlerFactory(new IdReferenceType("ws"), refmap));
		IdReferenceHandlerSet<String> handlers =
				fac.createHandlers(String.class).associateObject("foo");
		
		ValidatedTypedObject tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), handlers);
		//sort via sort() method in memory
		handlers.processIDs();
		try {
			tovr.getInputStream();
			fail("created an inputstream on unsorted data");
		} catch (IllegalStateException ise) {
			assertThat("correct exception message on failing to write",
					ise.getLocalizedMessage(),
					is("You must call sort() prior to accessing the object data."));
		}
	}
	
	@Test
	public void duplicateKeys() throws Exception {
		String json = "{\"m\": {\"z\": \"a\", \"b\": \"d\"}}";
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("z", "b");
		refmap.put("d", "whoop");
		refmap.put("b", "b");
		refmap.put("a", "a");
		
		IdReferenceHandlerSetFactory fac = getFac(100);
		fac.addFactory(new DummyIdHandlerFactory(new IdReferenceType("ws"), refmap));
		IdReferenceHandlerSet<String> handlers =
				fac.createHandlers(String.class).associateObject("foo");
		
		ValidatedTypedObject tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), handlers);
		handlers.processIDs();
		TempFilesManager tfm = new TempFilesManager(
				new File(TestCommon.getTempDir()));
		UTF8JsonSorterFactory sfac = new UTF8JsonSorterFactory(225);
		try {
			tovr.sort(sfac, tfm);
			fail("sorting didn't detect duplicate keys");
		} catch (KeyDuplicationException kde){
			assertThat("correct exception message", kde.getLocalizedMessage(),
					is("Duplicated key 'b' was found at /m"));
		}
		assertThat("Temp files manager is empty", tfm.isEmpty(), is(true));
	}
	
	@Test
	public void relabelAndSortInMemAndFile() throws Exception {
		String json = "{\"m\": {\"z\": \"a\", \"b\": \"d\"}}";
		String expectedJson = "{\"m\":{\"b\":\"whoop\",\"y\":\"a\"}}";
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("z", "y");
		refmap.put("d", "whoop");
		refmap.put("a", "a");
		refmap.put("b", "b");
		
		IdReferenceHandlerSetFactory fac = getFac(100);
		fac.addFactory(new DummyIdHandlerFactory(new IdReferenceType("ws"),
				refmap));
		IdReferenceHandlerSet<String> handlers =
				fac.createHandlers(String.class).associateObject("foo");
		
		ValidatedTypedObject tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), handlers);
		assertThat("incorrect typedef", tovr.getValidationTypeDefId(),
				is(AbsoluteTypeDefId.fromAbsoluteTypeString(
						"TestIDMap.IDMap-1.0")));
		try {
			tovr.calculateRelabeledSize();
			fail("calculated size w/o processed IDs");
		} catch (IllegalStateException e) {
			assertThat("incorrect exception", e.getMessage(),
					is("Must process IDs in handler prior to relabling"));
		}
		handlers.processIDs();
		
		failGetRelabeledSize(tovr);
		
		//sort via sort() method in memory
		assertThat("correct object size", tovr.calculateRelabeledSize(), is(27L));
		assertThat("correct object size", tovr.getRelabeledSize(), is(27L));
		assertThat("correct object size", tovr.getSize(), is(27L));
		// check twice, result is memoized
		assertThat("correct object size", tovr.calculateRelabeledSize(), is(27L));
		failGetMD5(tovr);
		tovr.sort(SORT_FAC);
		assertThat("incorrect md5", tovr.getMD5(),
				is(new MD5("920d54af26c56df84e4c4df358952138")));
		String gotjson = IOUtils.toString(tovr.getInputStream(), "UTF-8");
		assertThat("Relabel and sort in memory correctly", gotjson,
				is(expectedJson));
		
		//sort via sort(TFM) method with null TFM, again in memory
		handlers = fac.createHandlers(String.class).associateObject("foo");
		tovr = validator.validate(json, new TypeDefId("TestIDMap.IDMap"),
				handlers);
		handlers.processIDs();
		
		tovr.sort(SORT_FAC, null);
		assertThat("incorrect md5", tovr.getMD5(),
				is(new MD5("920d54af26c56df84e4c4df358952138")));
		gotjson = IOUtils.toString(tovr.getInputStream(), "UTF-8");
		assertThat("Relabel and sort in memory correctly", gotjson,
				is(expectedJson));
		
		//sort via sort(TFM) method with data stored in file
		handlers = fac.createHandlers(String.class).associateObject("foo");
		tovr = validator.validate(json, new TypeDefId("TestIDMap.IDMap"),
				handlers);
		handlers.processIDs();

		TempFilesManager tfm = new TempFilesManager(
				new File(TestCommon.getTempDir()));
		tfm.cleanup();
		assertThat("Temp files manager is empty", tfm.isEmpty(), is(true));
		tovr.sort(SORT_FAC, tfm);
		assertThat("TFM is no longer empty", tfm.isEmpty(), is(false));
		assertThat("TFM has one file", tfm.getTempFileList().size(), is(1));
		assertThat("incorrect md5", tovr.getMD5(),
				is(new MD5("920d54af26c56df84e4c4df358952138")));
		gotjson = IOUtils.toString(tovr.getInputStream(), "UTF-8");
		assertThat("Relabel and in file correctly", gotjson,
				is(expectedJson));
		tovr.destroyCachedResources();
		assertThat("Temp files manager is empty", tfm.isEmpty(), is(true));
	}

	private void failGetRelabeledSize(ValidatedTypedObject tovr) {
		try {
			tovr.getRelabeledSize();
			fail("got relabled size before calculation");
		} catch (IllegalStateException e) {
			assertThat("incorrect exception", e.getMessage(),
					is("Must call calculateRelabeledSize() before getting said size"));
		}
		
		try {
			tovr.getSize();
			fail("got relabled size before calculation");
		} catch (IllegalStateException e) {
			assertThat("incorrect exception", e.getMessage(),
					is("Must call calculateRelabeledSize() before getting said size"));
		}
	}

	@Test
	public void keySize() throws Exception {
		String json = "{\"z\":\"a\",\"b\":\"d\"}";
		
		IdReferenceHandlerSetFactory hfac = getFac(100);
		IdReferenceHandlerSet<String> handlers =
				hfac.createHandlers(String.class).associateObject("foo");
		ValidatedTypedObject tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), handlers);
		handlers.processIDs();
		
		int maxmem = 8 + 64 + 8 + 64;
		TempFilesManager tfm = new TempFilesManager(
				new File(TestCommon.getTempDir()));
		UTF8JsonSorterFactory fac = new UTF8JsonSorterFactory(maxmem);
		
		//test with json stored in file
		tovr.sort(fac, tfm); //should work
		tovr.destroyCachedResources();
		assertThat("Temp files manager is empty", tfm.isEmpty(), is(true));
		maxmem--;
		fac = new UTF8JsonSorterFactory(maxmem);
		try {
			tovr.sort(fac, tfm);
			fail("sorted with too little memory");
		} catch (TooManyKeysException tmke) {
			assertThat("correct exception message", tmke.getLocalizedMessage(),
					is("Memory necessary for sorting map keys exceeds the limit " +
					maxmem + " bytes at /"));
		}
		assertThat("Temp files manager is empty", tfm.isEmpty(), is(true));
		
		//test with json stored in memory
		int filelength = json.getBytes("UTF-8").length;
		maxmem += filelength + 1;
		fac = new UTF8JsonSorterFactory(maxmem);
		tovr.sort(fac); //should work
		maxmem--;
		fac = new UTF8JsonSorterFactory(maxmem);
		try {
			tovr.sort(fac);
			fail("sorted with too little memory");
		} catch (TooManyKeysException tmke) {
			assertThat("correct exception message", tmke.getLocalizedMessage(),
					is("Memory necessary for sorting map keys exceeds the limit " +
					(maxmem - filelength) + " bytes at /"));
		}
	}
}
