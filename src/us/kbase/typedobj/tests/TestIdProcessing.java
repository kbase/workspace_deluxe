package us.kbase.typedobj.tests;

import static org.junit.Assert.*;

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

import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.db.UserInfoProviderForTests;


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
public class TestIdProcessing {

	/**
	 * location to stash the temporary database for testing
	 * WARNING: THIS DIRECTORY WILL BE WIPED OUT AFTER TESTS!!!!
	 */
	private final static String TEST_DB_LOCATION = "test/typedobj_temp_test_files/t2";
	
	/**
	 * relative location to find the input files
	 */
	private final static String TEST_RESOURCE_LOCATION = "files/t2/";
	

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
	
	public TestIdProcessing(TestInstanceInfo tii) {
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
		db = new TypeDefinitionDB(new FileTypeStorage(TEST_DB_LOCATION), new UserInfoProviderForTests());
		
		// create a validator that uses the type def db
		validator = new TypedObjectValidator(db);
	
		
		System.out.println("loading db with types");
		String username = "wstester1";
		
		String kbSpec = loadResourceFile(TEST_RESOURCE_LOCATION+"KB.spec");
		List<String> kb_types =  Arrays.asList("Feature","Genome","FeatureGroup","genome_id","feature_id");
		db.requestModuleRegistration("KB", username);
		db.approveModuleRegistrationRequest(username, "KB");
		db.registerModule(kbSpec ,kb_types, username);
		for(String typename : kb_types) {
			db.releaseType(new TypeDefName("KB." + typename), username);
		}
		
		String fbaSpec = loadResourceFile(TEST_RESOURCE_LOCATION+"FBA.spec");
		List<String> fba_types =  Arrays.asList("FBAModel","FBAResult","fba_model_id");
		db.requestModuleRegistration("FBA", username);
		db.approveModuleRegistrationRequest(username, "FBA");
		db.registerModule(fbaSpec ,fba_types, username);
		for(String typename : fba_types) {
			db.releaseType(new TypeDefName("FBA." + typename), username);
		}
		
		System.out.println("finding test instances\n");
		String [] resources = getResourceListing(TEST_RESOURCE_LOCATION);
		for(int k=0; k<resources.length; k++) {
			String [] tokens = resources[k].split("\\.");
			if(tokens.length!=4) { continue; }
			if(tokens[2].equals("instance")) {
				instanceResources.add(new TestInstanceInfo(resources[k],tokens[0],tokens[1]));
			}
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
		//read the 
		System.out.println("  -("+instance.resourceName+")");
		String instanceJson = loadResourceFile(TEST_RESOURCE_LOCATION+instance.resourceName);

		// read the ids file, which provides the list of ids we expect to extract from the instance
		String idsJson = loadResourceFile(TEST_RESOURCE_LOCATION+instance.resourceName+".ids");
		ObjectMapper mapper = new ObjectMapper();
		JsonNode instanceRootNode = mapper.readTree(idsJson);
		JsonNode expectedIds = instanceRootNode.get("ids-expected");
		Iterator <JsonNode> it = expectedIds.iterator();
		Map <String,Integer> expectedIdList = new HashMap<String,Integer>();
		while(it.hasNext()) {
			String id = it.next().asText();
			if(expectedIdList.containsKey(id)) {
				int count = expectedIdList.get(id).intValue() + 1;
				expectedIdList.put(id, new Integer(count));
			} else { expectedIdList.put(id,new Integer(1)); }
		}
		
		// perform the initial validation, which must validate!
		TypedObjectValidationReport report = 
			validator.validate(
				instanceJson,
				new TypeDefId(new TypeDefName(instance.moduleName,instance.typeName))
				);
		String [] mssgs = report.getErrorMessages();
		for(int i=0; i<mssgs.length; i++) {
			System.out.println("    ["+i+"]:"+mssgs[i]);
		}
		assertTrue("  -("+instance.resourceName+") does not validate, but should",report.isInstanceValid());
		
		// check that all expected Ids are in fact found
		String [] foundIdRefs = report.getListOfIdReferences();
		for(int k=0; k<foundIdRefs.length; k++) {
			assertTrue("  -("+instance.resourceName+") extracted id "+foundIdRefs[k]+" that should not have been extracted",
					expectedIdList.containsKey(foundIdRefs[k]));
			int n_refs = expectedIdList.get(foundIdRefs[k]).intValue();
			assertFalse("  -("+instance.resourceName+") extracted id "+foundIdRefs[k]+" too many times",
					n_refs==0);
			expectedIdList.put(foundIdRefs[k], new Integer(n_refs-1));
		}
		Iterator<Map.Entry<String,Integer>> mapIter = expectedIdList.entrySet().iterator();
		while(mapIter.hasNext()) {
			Map.Entry<String, Integer> pair = mapIter.next();
			int n_refs_remaining = pair.getValue().intValue();
			assertTrue("  -("+instance.resourceName+") needed to extract id '"+pair.getKey()+"' "+n_refs_remaining+" more times",
					n_refs_remaining == 0);
		}
	}

	
	/**
	 * helper method to load test files, mostly copied from TypeRegistering test
	 */
	private static String loadResourceFile(String resourceName) throws Exception {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		InputStream is = TestIdProcessing.class.getResourceAsStream(resourceName);
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
		URL dirURL = TestIdProcessing.class.getResource(path);
		if (dirURL != null && dirURL.getProtocol().equals("file")) {
			/* A file path: easy enough */
			return new File(dirURL.toURI()).list();
		}

		if (dirURL == null) {
			// In case of a jar file, we can't actually find a directory.
			// Have to assume the same jar as the class.
			String me = TestIdProcessing.class.getName().replace(".", "/")+".class";
			dirURL = TestIdProcessing.class.getResource(me);
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
				String fullPath = TestIdProcessing.class.getPackage().getName().replace(".","/") + "/" + path;
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
			return result.toArray(new String[result.size()]);
		}
		throw new UnsupportedOperationException("Cannot list files for URL "+dirURL);
	}
	
}
