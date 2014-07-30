package us.kbase.typedobj.test;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.is;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import us.kbase.common.test.TestException;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.idref.IdReferenceHandlerSet;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.workspace.kbase.Util;
import us.kbase.workspace.test.WorkspaceTestCommon;

/**
 * Tests that ensure IDs are properly extracted from typed object instances, and that IDs
 * can be relabeled properly.  Test files which specify test cases are specified
 * in us.kbase.typedobj.tests.files.t2.  Temporary test files are stored in 
 * test/typedobj_temp_test_files.  Running this test will blow away any files you have
 * saved to 
 * 
 * To add new tests of the ID processing machinery, add files named in the format:
 *   [ModuleName].[TypeName].instance.[label] 
 *        - json encoding of a valid type instance
 *   [ModuleName].[TypeName].instance.[label].ids
 *        - json formatted file listing IDs that must be found in the instance, and
 *          new names for each of the IDs as needed.  Tests will check the the specified
 *          ids are all found in the instance, and that the instance contains no extra
 *          ids.  If the same ID is used multiple times in an instance, then it must
 *          by listed multiple times in this structure!  See existing files for an
 *          example of the json structure you need to use.
 *
 * @author msneddon
 */
@RunWith(value = Parameterized.class)
public class IdProcessingTest {

	private static final int TEST_COUNT = 10;
	
	/**
	 * location to stash the temporary database for testing
	 * WARNING: THIS DIRECTORY WILL BE WIPED OUT AFTER TESTS!!!!
	 */
	private final static String TEST_DB_LOCATION = "test/typedobj_temp_test_files/IdProcessing";
	
	/**
	 * relative location to find the input files
	 */
	private final static String TEST_RESOURCE_LOCATION = "files/IdProcessing/";
	
	/**
	 * KB types to register
	 */
	private final static List<String> KB_TYPES =
			Arrays.asList("Feature","Genome","FeatureGroup","genome_id",
					"feature_id","FeatureMap","DeepFeatureMap",
					"NestedFeaturesValue", "NestedFeaturesKey",
					"NestedFeaturesList", "AltIDs", "WeirdTuple");
	
	
	private static TypeDefinitionDB db;
	private static TypedObjectValidator validator;
	
	/**
	 * List to stash the handle to the test case files
	 */
	private static List<TestInstanceInfo> instanceResources = new ArrayList <TestInstanceInfo> ();
	
	private static class TestInstanceInfo {
		public TestInstanceInfo(String resourceName, String moduleName, String typeName) {
			this.resourceName = resourceName;
			this.moduleName = moduleName;
			this.typeName = typeName;
		}
		public String resourceName;
		public String moduleName;
		public String typeName;
	}
	
	/**
	 * As each test instance object is created, this sets which instance to actually test
	 */
	private TestInstanceInfo instance;
	
	public IdProcessingTest(TestInstanceInfo tii) {
		this.instance = tii;
	}

	/**
	 * This is invoked before anything else, so here we invoke the creation of the db
	 * @return
	 * @throws Exception 
	 */
	@Parameters
	public static Collection<Object[]> assembleTestInstanceList() throws Exception {
		prepareDb();
		Object [][] instanceInfo = new Object[instanceResources.size()][1];
		for(int k=0; k<instanceResources.size(); k++) {
			instanceInfo[k][0] = instanceResources.get(k);
		}
		
		return Arrays.asList(instanceInfo);
	}

	/**
	 * Setup the typedef database, load and release the types in the simple specs, and
	 * identify all the files containing instances to validate.
	 * @throws Exception
	 */
	public static void prepareDb() throws Exception
	{
		
		//ensure test location is available
		File dir = new File(TEST_DB_LOCATION);
		if (dir.exists()) {
			//fail("database at location: "+TEST_DB_LOCATION+" already exists, remove/rename this directory first");
			removeDb();
		}
		if(!dir.mkdirs()) {
			fail("unable to create needed test directory: "+TEST_DB_LOCATION);
		}
		
		System.out.println("setting up the typed obj database");
		// point the type definition db to point there
		File tempdir = new File("temp_files");
		if (!dir.exists())
			dir.mkdir();
		db = new TypeDefinitionDB(new FileTypeStorage(TEST_DB_LOCATION), tempdir,
				new Util().getKIDLpath(), WorkspaceTestCommon.getKidlSource());
		
		// create a validator that uses the type def db
		validator = new TypedObjectValidator(db);
	
		
		System.out.println("loading db with types");
		String username = "wstester1";
		
		String kbSpec = loadResourceFile(TEST_RESOURCE_LOCATION+"KB.spec");
		db.requestModuleRegistration("KB", username);
		db.approveModuleRegistrationRequest(username, "KB", true);
		db.registerModule(kbSpec ,KB_TYPES, username);
		db.releaseModule("KB", username, false);
		
		String fbaSpec = loadResourceFile(TEST_RESOURCE_LOCATION+"FBA.spec");
		List<String> fba_types =  Arrays.asList("FBAModel","FBAResult","fba_model_id");
		db.requestModuleRegistration("FBA", username);
		db.approveModuleRegistrationRequest(username, "FBA", true);
		db.registerModule(fbaSpec ,fba_types, username);
		db.releaseModule("FBA", username, false);
		
		System.out.println("finding test instances");
		String [] resources = getResourceListing(TEST_RESOURCE_LOCATION);
		Arrays.sort(resources);
		for(int k=0; k<resources.length; k++) {
			String [] tokens = resources[k].split("\\.");
			if(tokens.length!=4) { continue; }
			if(tokens[2].equals("instance")) {
				instanceResources.add(new TestInstanceInfo(resources[k],tokens[0],tokens[1]));
			}
		}
		if (TEST_COUNT != instanceResources.size()) {
			String err = String.format(
					"Incorrect test count, got: %s, expected %s",
					instanceResources.size(), TEST_COUNT);
			System.out.println(err);
			throw new TestException(err);
		}
	}
	
	
	@AfterClass
	public static void removeDb() throws IOException {
		File dir = new File(TEST_DB_LOCATION);
		FileUtils.deleteDirectory(dir);
		System.out.println("deleting typed obj database");
	}
	
	@Test
	public void testValidInstances() throws Exception
	{
		ObjectMapper mapper = new ObjectMapper();
		
		//read the instance data
		System.out.println("  -("+instance.resourceName+")");
		String instanceJson = loadResourceFile(TEST_RESOURCE_LOCATION+instance.resourceName);
		JsonNode instanceRootNode = mapper.readTree(instanceJson);
		
		// read the ids file, which provides the list of ids we expect to extract from the instance
		String idsJson = loadResourceFile(TEST_RESOURCE_LOCATION+instance.resourceName+".ids");
		JsonNode idsRootNode = mapper.readTree(idsJson);
		
		//set up id relabeling mapping
		Map <String,String> absoluteIdMapping = new HashMap<String,String>();
		JsonNode newIds = idsRootNode.get("ids-relabel");
		Iterator<String> fieldNames = newIds.fieldNames();
		while(fieldNames.hasNext()) {
			String originalId = fieldNames.next();
			String absoluteId = newIds.get(originalId).asText();
			absoluteIdMapping.put(originalId, absoluteId);
		}
		
		@SuppressWarnings("unchecked")
		Map<String, Integer> expectedIds = mapper.treeToValue(
				idsRootNode.get("ids-found"), Map.class); 
		
		Map<String, Integer> foundIDs = new HashMap<String, Integer>();
		IdReferenceHandlerSetFactory fac = new IdReferenceHandlerSetFactory(100);
		fac.addFactory(new DummyIdHandlerFactory(new IdReferenceType("ws"),
				absoluteIdMapping, foundIDs));
		IdReferenceHandlerSet<String> idhandlers = fac.createHandlers(String.class);
		idhandlers.associateObject("foo");
		
		// perform the initial validation, which must validate!
		TypedObjectValidationReport report = 
			validator.validate(
					instanceRootNode,
					new TypeDefId(new TypeDefName(instance.moduleName,instance.typeName)),
					idhandlers);
		idhandlers.processIDs();
		List <String> mssgs = report.getErrorMessages();
		for(int i=0; i<mssgs.size(); i++) {
			System.out.println("    ["+i+"]:"+mssgs.get(i));
		}
		assertTrue("  -("+instance.resourceName+") does not validate, but should",report.isInstanceValid());
		
		assertThat("found the correct id counts", foundIDs, is(expectedIds));
		
		// now we relabel the ids
		JsonNode relabeledInstance = report.getInstanceAfterIdRefRelabelingForTests();
		
		// now we revalidate the instance, and ensure that the labels have been renamed
		IdReferenceHandlerSetFactory dummyfac = new IdReferenceHandlerSetFactory(0);
		TypedObjectValidationReport report2 = validator.validate(relabeledInstance, new TypeDefId(new TypeDefName(instance.moduleName,instance.typeName)),
				dummyfac.createHandlers(String.class).associateObject("foo"));
		List <String> mssgs2 = report2.getErrorMessages();
		for(int i=0; i<mssgs2.size(); i++) {
			System.out.println("    ["+i+"]:"+mssgs2.get(i));
		}
		assertTrue("  -("+instance.resourceName+") validation of relabeled object must still pass", report2.isInstanceValid());
		
		// make sure that the relabeled object matches what we expect
		JsonNode expectedRelabeled = idsRootNode.get("renamed-expected");
		compare(expectedRelabeled, relabeledInstance, instance.resourceName);
		System.out.println("      PASS.");
	}
	
	private static void compare(JsonNode expectedRelabeled, JsonNode relabeledInstance, String resourceName) throws IOException {
		assertEquals("  -(" + resourceName + ") extracted object does not match expected extract",
				sort(expectedRelabeled), sort(relabeledInstance));
	}
	
	private static String sort(JsonNode tree) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
		//TreeNode schemaTree = mapper.readTree(tree);
		Object obj = mapper.treeToValue(tree, Object.class);
		mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
		return mapper.writeValueAsString(obj);
	}
	
	/**
	 * helper method to load test files, mostly copied from TypeRegistering test
	 */
	private static String loadResourceFile(String resourceName) throws Exception {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		InputStream is = IdProcessingTest.class.getResourceAsStream(resourceName);
		if (is == null)
			throw new IllegalStateException("Resource not found: " + resourceName);
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
	
	
	
	/**
	 * List directory contents for a resource folder. Not recursive.
	 * This is basically a brute-force implementation.
	 * Works for regular files and also JARs.
	 * adapted from: http://www.uofr.net/~greg/java/get-resource-listing.html
	 * 
	 * @author Greg Briggs
	 * @author msneddon
	 * @param path Should end with "/", but not start with one.
	 * @return Just the name of each member item, not the full paths.
	 * @throws URISyntaxException 
	 * @throws IOException 
	 */
	private static String[] getResourceListing(String path) throws URISyntaxException, IOException {
		URL dirURL = IdProcessingTest.class.getResource(path);
		if (dirURL != null && dirURL.getProtocol().equals("file")) {
			/* A file path: easy enough */
			return new File(dirURL.toURI()).list();
		}

		if (dirURL == null) {
			// In case of a jar file, we can't actually find a directory.
			// Have to assume the same jar as the class.
			String me = IdProcessingTest.class.getName().replace(".", "/")+".class";
			dirURL = IdProcessingTest.class.getResource(me);
		}

		if (dirURL.getProtocol().equals("jar")) {
			/* A JAR path */
			String jarPath = dirURL.getPath().substring(5, dirURL.getPath().indexOf("!")); //strip out only the JAR file
			JarFile jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"));
			Enumeration<JarEntry> entries = jar.entries(); //gives ALL entries in jar
			Set<String> result = new HashSet<String>(); //avoid duplicates in case it is a subdirectory
			while(entries.hasMoreElements()) {
				String name = entries.nextElement().getName();
				// construct internal jar path relative to the class
				String fullPath = IdProcessingTest.class.getPackage().getName().replace(".","/") + "/" + path;
				if (name.startsWith(fullPath)) { //filter according to the path
					String entry = name.substring(fullPath.length());
					int checkSubdir = entry.indexOf("/");
					if (checkSubdir >= 0) {
						// if it is a subdirectory, we just return the directory name
						entry = entry.substring(0, checkSubdir);
					}
					result.add(entry);
				}
			}
			jar.close();
			return result.toArray(new String[result.size()]);
		}
		throw new UnsupportedOperationException("Cannot list files for URL "+dirURL);
	}
}
