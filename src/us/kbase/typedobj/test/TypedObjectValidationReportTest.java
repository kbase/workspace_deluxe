package us.kbase.typedobj.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.UObject;
import us.kbase.common.utils.sortjson.KeyDuplicationException;
import us.kbase.common.utils.sortjson.TooManyKeysException;
import us.kbase.common.utils.sortjson.UTF8JsonSorterFactory;
import us.kbase.typedobj.core.JsonTokenValidationSchema;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.core.Writable;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.workspace.kbase.Util;
import us.kbase.workspace.test.WorkspaceTestCommon;

public class TypedObjectValidationReportTest {
	
	// needs more tests, just adding tests for new functionality
	
	private static final UTF8JsonSorterFactory SORT_FAC =
			new UTF8JsonSorterFactory(10000);
	
	private static TypeDefinitionDB db;
	private static JsonTokenValidationSchema idMapSchema;
	
	private static final String USER = "someUser";
	
	@BeforeClass
	public static void setupTypeDB() throws Exception {
		//ensure test location is available
		Path dir = Files.createTempDirectory("TypedObjectValReportTest");
		dir.toFile().deleteOnExit();
		System.out.println("setting up temporary typed obj database");

		// point the type definition db to point there
		Path tempdir = Files.createTempDirectory("TypedObjectValReportTest");
		tempdir.toFile().deleteOnExit();
		db = new TypeDefinitionDB(
				new FileTypeStorage(dir.toFile().getAbsolutePath()),
				tempdir.toFile(), new Util().getKIDLpath(),
				WorkspaceTestCommon.getKidlSource());
		addSpecs();
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
		idMapSchema =
				db.getJsonSchema(new TypeDefName(module, name));
		
	}


	@Test
	public void errors() throws Exception {
		String json = "{\"z\": \"a\", \"b\": \"d\"}";
		
		List<String> errors = Arrays.asList("foo", "bar", "baz");
		
		TypedObjectValidationReport tovr = new TypedObjectValidationReport(
				new UObject(new JsonTokenStream(json)), null, errors, null,
				null, null, null);
		assertThat("correct errors", tovr.getErrorMessages(), is(errors));
		assertThat("errors not empty", tovr.isInstanceValid(), is(false));
		
		tovr = new TypedObjectValidationReport(
				new UObject(new JsonTokenStream(json)), null, null, null,
				null, null, null);
		assertThat("correct errors", tovr.getErrorMessages(),
				is((List<String>) new LinkedList<String>()));
		assertThat("errors empty", tovr.isInstanceValid(), is(true));
		
		errors = Collections.emptyList();

		tovr = new TypedObjectValidationReport(
				new UObject(new JsonTokenStream(json)), null, errors, null,
				null, null, null);
		assertThat("correct errors", tovr.getErrorMessages(), is(errors));
		assertThat("errors not empty", tovr.isInstanceValid(), is(true));
		
	}
	
	@Test
	public void noSortFac() throws Exception {
		String json = "{\"z\": \"a\", \"b\": \"d\"}";

		TypedObjectValidationReport tovr = new TypedObjectValidationReport(
				new UObject(new JsonTokenStream(json)), null, null, null,
				null, null, null);
		try {
			tovr.sort(null);
			fail("sorted with no factory");
		} catch (NullPointerException npe) {
			assertThat("correct expt msg", npe.getLocalizedMessage(),
					is("Sorter factory cannot be null"));
		}
		TempFilesManager tfm = TempFilesManager.forTests();
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
	public void writeWithoutSort() throws Exception {
		String json = "{\"m\": {\"c\": 1, \"z\": \"d\"}}";
		String expectedJson = "{\"m\":{\"c\":1,\"y\":\"whoop\"}}";
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("z", "y");
		refmap.put("d", "whoop");
		refmap.put("c", "c");

		TypedObjectValidationReport tovr = new TypedObjectValidationReport(
				new UObject(new JsonTokenStream(json)), null, null, null, null,
				idMapSchema, null);
		tovr.setAbsoluteIdRefMapping(refmap);
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		tovr.createJsonWritable().write(o);
		assertThat("Relabel correctly without sort", o.toString("UTF-8"), is(expectedJson));
		
		
		//sort unnecessarily
		tovr = new TypedObjectValidationReport(
				new UObject(new JsonTokenStream(json)), null, null, null,
				null, idMapSchema, null);
		tovr.setAbsoluteIdRefMapping(refmap);
		tovr.sort(SORT_FAC);
		o = new ByteArrayOutputStream();
		tovr.createJsonWritable().write(o);
		assertThat("Relabel correctly with unecessary sort", o.toString("UTF-8"), is(expectedJson));
	}
	
	@Test
	public void sortWithNoMapping() throws Exception {
		String json = "{\"z\": \"a\", \"b\": \"d\"}";
		String expectedJson = "{\"b\":\"d\",\"z\":\"a\"}";

		TypedObjectValidationReport tovr = new TypedObjectValidationReport(
				new UObject(new JsonTokenStream(json)), null, null, null,
				null, null, null);
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
		
		//sort via sort() method in memory
		TypedObjectValidationReport tovr = new TypedObjectValidationReport(
				new UObject(new JsonTokenStream(json)), null, null, null, null,
				idMapSchema, null);
		tovr.setAbsoluteIdRefMapping(refmap);
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
		
		TypedObjectValidationReport tovr = new TypedObjectValidationReport(
				new UObject(new JsonTokenStream(json)), null, null, null, null,
				idMapSchema, null);
		
		tovr.setAbsoluteIdRefMapping(refmap);
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
		
		//sort via sort() method in memory
		TypedObjectValidationReport tovr = new TypedObjectValidationReport(
				new UObject(new JsonTokenStream(json)), null, null, null, null,
				idMapSchema, null);
		assertThat("correct object size", tovr.getRelabeledSize(), is(23L));
		
		tovr.setAbsoluteIdRefMapping(refmap);
		assertThat("correct object size", tovr.getRelabeledSize(), is(27L));
		tovr.sort(SORT_FAC);
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		tovr.createJsonWritable().write(o);
		assertThat("Relabel and sort in memory correctly", o.toString("UTF-8"), is(expectedJson));
		
		//sort via sort(TFM) method with null TFM, again in memory
		tovr = new TypedObjectValidationReport(
				new UObject(new JsonTokenStream(json)), null, null, null, null,
				idMapSchema, null);
		
		tovr.setAbsoluteIdRefMapping(refmap);
		tovr.sort(SORT_FAC, null);
		o = new ByteArrayOutputStream();
		tovr.createJsonWritable().write(o);
		assertThat("Relabel and sort in memory correctly", o.toString("UTF-8"), is(expectedJson));
		
		//sort via sort(TFM) method with data stored in file
		tovr = new TypedObjectValidationReport(
				new UObject(new JsonTokenStream(json)), null, null, null, null,
				idMapSchema, null);

		tovr.setAbsoluteIdRefMapping(refmap);
		TempFilesManager tfm = TempFilesManager.forTests();
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
		int maxmem = 8 + 64 + 8 + 64;
		TempFilesManager tfm = TempFilesManager.forTests();
		UTF8JsonSorterFactory fac = new UTF8JsonSorterFactory(maxmem);
		TypedObjectValidationReport tovr = new TypedObjectValidationReport(
				new UObject(new JsonTokenStream(json)),null, null, null, 
				null, null, null);
		
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
