package us.kbase.typedobj.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.common.utils.sortjson.KeyDuplicationException;
import us.kbase.common.utils.sortjson.TooManyKeysException;
import us.kbase.common.utils.sortjson.UTF8JsonSorterFactory;
import us.kbase.typedobj.core.JsonDocumentLocation;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.core.Writable;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.idref.IdReference;
import us.kbase.typedobj.idref.IdReferenceHandlerSet;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.workspace.kbase.Util;
import us.kbase.workspace.test.WorkspaceTestCommon;

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
		final Path temppath = Paths.get(WorkspaceTestCommon.getTempDir());
		Files.createDirectories(temppath);
		tempdir = Files.createTempDirectory(temppath, "TypedObjectValReportTest");
		System.out.println("setting up temporary typed obj database");

		// point the type definition db to point there
		final Path storagedir = tempdir.resolve("typestorage");
		Files.createDirectories(storagedir);
		db = new TypeDefinitionDB(
				new FileTypeStorage(storagedir.toFile().getAbsolutePath()),
				tempdir.toFile(), new Util().getKIDLpath(),
				WorkspaceTestCommon.getKidlSource());
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
					"typedef structure {" +
						"mapping<id, id> m;" +
					"} " + name + ";" +
				"};";
		db.requestModuleRegistration(module, USER);
		db.approveModuleRegistrationRequest(USER, module, true);
		db.registerModule(spec, Arrays.asList(name), USER);
		db.releaseModule(module, USER, false);
		validator = new TypedObjectValidator(db);
		
	}

	@Test
	public void errors() throws Exception {
		String json = "{\"m\": {\"z\": 1, \"b\": []}}";
		
		IdReferenceHandlerSetFactory fac = new IdReferenceHandlerSetFactory(100);
		TypedObjectValidationReport tovr = validator.validate(json,
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
		
		IdReferenceHandlerSetFactory fac = new IdReferenceHandlerSetFactory(100);
		fac.addFactory(new DummyIdHandlerFactory(new IdReferenceType("ws"),
						new HashMap<String, String>()));
		IdReferenceHandlerSet<String> handlers =
				fac.createHandlers(String.class).associateObject("foo");
		TypedObjectValidationReport tovr = validator.validate(json,
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
				new File(WorkspaceTestCommon.getTempDir()));
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
		//TODO 1 find with various Id types and attribs
		String json = "{\"m\": {\"c\": \"a\", \"z\": \"d\"}}";
		TypedObjectValidationReport tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), 
				new IdReferenceHandlerSetFactory(1).createHandlers(String.class));
		IdReferenceType wsType = new IdReferenceType("ws");
		List<String> mtAttribs = new LinkedList<String>();
		
		checkVariableLocation(tovr, wsType, "c", mtAttribs, "/m/c");
		checkVariableLocation(tovr, wsType, "a", mtAttribs, "/m/c");
		checkVariableLocation(tovr, wsType, "z", mtAttribs, "/m/z");
		checkVariableLocation(tovr, wsType, "d", mtAttribs, "/m/z");
		
	}

	private void checkVariableLocation(TypedObjectValidationReport tovr,
			IdReferenceType wsType, final String id, List<String> mtAttribs,
			String expectedLoc) throws IOException {
		JsonDocumentLocation loc = tovr.getIdReferenceLocation(
				new IdReference<String>(wsType, id, mtAttribs));
		assertThat("correct id location", loc.getFullLocationAsString(),
				is(expectedLoc));
	}
	
	@Test
	public void writeWithoutSort() throws Exception {
		String json = "{\"m\": {\"c\": \"a\", \"z\": \"d\"}}";
		String expectedJson = "{\"m\":{\"c\":\"a\",\"y\":\"whoop\"}}";
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("z", "y");
		refmap.put("d", "whoop");
		refmap.put("c", "c");
		refmap.put("a", "a");
		
		IdReferenceHandlerSetFactory fac = new IdReferenceHandlerSetFactory(100);
		fac.addFactory(new DummyIdHandlerFactory(new IdReferenceType("ws"), refmap));
		IdReferenceHandlerSet<String> handlers =
				fac.createHandlers(String.class).associateObject("foo");

		TypedObjectValidationReport tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), handlers);
		handlers.processIDs();
		tovr.getRelabeledSize();
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		tovr.createJsonWritable().write(o);
		assertThat("Relabel correctly without sort", o.toString("UTF-8"), is(expectedJson));
		
		
		//sort unnecessarily
		handlers = fac.createHandlers(String.class).associateObject("foo");
		tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), handlers);
		handlers.processIDs();
		tovr.sort(SORT_FAC);
		o = new ByteArrayOutputStream();
		tovr.createJsonWritable().write(o);
		assertThat("Relabel correctly with unecessary sort", o.toString("UTF-8"), is(expectedJson));
	}

	@Test
	public void sortWithNoMapping() throws Exception {
		String json = "{\"z\": \"a\", \"b\": \"d\"}";
		String expectedJson = "{\"b\":\"d\",\"z\":\"a\"}";

		IdReferenceHandlerSetFactory fac = new IdReferenceHandlerSetFactory(100);
		IdReferenceHandlerSet<String> handlers =
				fac.createHandlers(String.class).associateObject("foo");

		TypedObjectValidationReport tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), handlers);
		handlers.processIDs();
		tovr.sort(SORT_FAC);
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		tovr.createJsonWritable().write(o);
		assertThat("Relabel correctly without sort", o.toString("UTF-8"), is(expectedJson));
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
		
		IdReferenceHandlerSetFactory fac = new IdReferenceHandlerSetFactory(100);
		fac.addFactory(new DummyIdHandlerFactory(new IdReferenceType("ws"), refmap));
		IdReferenceHandlerSet<String> handlers =
				fac.createHandlers(String.class).associateObject("foo");
		
		TypedObjectValidationReport tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), handlers);
		//sort via sort() method in memory
		handlers.processIDs();
		try {
			tovr.createJsonWritable();
			fail("created a writable on non-naturally sorted data");
		} catch (IllegalStateException ise) {
			assertThat("correct exception message on failing to write",
					ise.getLocalizedMessage(),
					is("You must call sort() prior to creating a Writeable."));
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
		
		IdReferenceHandlerSetFactory fac = new IdReferenceHandlerSetFactory(100);
		fac.addFactory(new DummyIdHandlerFactory(new IdReferenceType("ws"), refmap));
		IdReferenceHandlerSet<String> handlers =
				fac.createHandlers(String.class).associateObject("foo");
		
		TypedObjectValidationReport tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), handlers);
		handlers.processIDs();
		try {
			tovr.sort(SORT_FAC);
			fail("sorting didn't detect duplicate keys");
		} catch (KeyDuplicationException kde){
			assertThat("correct exception message", kde.getLocalizedMessage(),
					is("Duplicated key 'b' was found at /m"));
		}
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
		
		IdReferenceHandlerSetFactory fac = new IdReferenceHandlerSetFactory(100);
		fac.addFactory(new DummyIdHandlerFactory(new IdReferenceType("ws"), refmap));
		IdReferenceHandlerSet<String> handlers =
				fac.createHandlers(String.class).associateObject("foo");
		
		TypedObjectValidationReport tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), handlers);
		handlers.processIDs();
		
		//sort via sort() method in memory
		assertThat("correct object size", tovr.getRelabeledSize(), is(27L));
		tovr.sort(SORT_FAC);
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		tovr.createJsonWritable().write(o);
		assertThat("Relabel and sort in memory correctly", o.toString("UTF-8"), is(expectedJson));
		
		//sort via sort(TFM) method with null TFM, again in memory
		handlers = fac.createHandlers(String.class).associateObject("foo");
		tovr = validator.validate(json, new TypeDefId("TestIDMap.IDMap"),
				handlers);
		handlers.processIDs();
		
		tovr.sort(SORT_FAC, null);
		o = new ByteArrayOutputStream();
		tovr.createJsonWritable().write(o);
		assertThat("Relabel and sort in memory correctly", o.toString("UTF-8"), is(expectedJson));
		
		//sort via sort(TFM) method with data stored in file
		handlers = fac.createHandlers(String.class).associateObject("foo");
		tovr = validator.validate(json, new TypeDefId("TestIDMap.IDMap"),
				handlers);
		handlers.processIDs();

		TempFilesManager tfm = new TempFilesManager(
				new File(WorkspaceTestCommon.getTempDir()));
		tfm.cleanup();
		assertThat("Temp files manager is empty", tfm.isEmpty(), is(true));
		tovr.sort(SORT_FAC, tfm);
		assertThat("TFM is no longer empty", tfm.isEmpty(), is(false));
		assertThat("TFM has one file", tfm.getTempFileList().size(), is(1));
		o = new ByteArrayOutputStream();
		Writable w = tovr.createJsonWritable();
		w.write(o);
		assertThat("Relabel and in file correctly", o.toString("UTF-8"), is(expectedJson));
		w.releaseResources();
		assertThat("Temp files manager is empty", tfm.isEmpty(), is(true));
	}

	@Test
	public void keySize() throws Exception {
		String json = "{\"z\":\"a\",\"b\":\"d\"}";
		
		IdReferenceHandlerSetFactory hfac = new IdReferenceHandlerSetFactory(100);
		IdReferenceHandlerSet<String> handlers =
				hfac.createHandlers(String.class).associateObject("foo");
		TypedObjectValidationReport tovr = validator.validate(json,
				new TypeDefId("TestIDMap.IDMap"), handlers);
		handlers.processIDs();
		
		int maxmem = 8 + 64 + 8 + 64;
		TempFilesManager tfm = new TempFilesManager(
				new File(WorkspaceTestCommon.getTempDir()));
		UTF8JsonSorterFactory fac = new UTF8JsonSorterFactory(maxmem);
		
		//test with json stored in file
		tovr.sort(fac, tfm); //should work
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
