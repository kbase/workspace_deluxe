package us.kbase.test.typedobj;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.test.common.TestCommon;
import us.kbase.common.test.TestException;
import us.kbase.typedobj.core.LocalTypeProvider;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.ValidatedTypedObject;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.idref.IdReferenceHandlerSet;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactoryBuilder;


/**
 * Detailed validation tests.  Tests specify the instance data, the expected result (pass/error),
 * the error type (exception/invalid), and the list of error messages or exception name.
 * 
 * @author msneddon
 *
 */
@RunWith(value = Parameterized.class)
public class DetailedValidationTest {

	private static final int EXPECTED_TESTS = 61;
	
	/**
	 * location to stash the temporary database for testing
	 * WARNING: THIS DIRECTORY WILL BE WIPED OUT AFTER TESTS!!!
	 */
	private final static Path TEST_DB_LOCATION =
			Paths.get(TestCommon.getTempDir())
			.resolve("DetailedValidationTest");
	
	private final static String TEST_RESOURCE_LOCATION = "files/DetailedValidation/";
	
	private static TypeDefinitionDB db;
	
	private static TypedObjectValidator validator;
	
	private final static boolean VERBOSE = true;
	
	private final static List<String> KB_TYPES =
			Arrays.asList("StringField", "IntField", "FloatField", "ListField", "MappingField", "TupleField", "StructureField");
	
	
	private static List<TestInstanceInfo> resources = new ArrayList <TestInstanceInfo> ();
	
	/**
	 * structures to store info on each instance we wish to validate 
	 */
	private static class TestInstanceInfo {
		public TestInstanceInfo(String resourceName) {
			this.resourceName = resourceName;
		}
		public String resourceName;
	}
	
	/**
	 * As each test instance object is created, this sets which instance to actually test
	 */
	private int instanceNumber;
	public DetailedValidationTest(Integer instanceNumber) {
		this.instanceNumber = instanceNumber.intValue();
	}
	
	
	/**
	 * This is invoked before anything else, so here we invoke the creation of the db
	 * @return
	 * @throws Exception 
	 */
	@Parameters
	public static Collection<Object[]> assembleTestInstanceList() throws Exception {
		prepareDb();
		Object [][] instanceInfo = new Object[resources.size()][1];
		for(int k=0; k<resources.size(); k++) {
			instanceInfo[k][0] = k;
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
		File dir = TEST_DB_LOCATION.toFile();
		if (dir.exists()) {
			//fail("database at location: "+TEST_DB_LOCATION+" already exists, remove/rename this directory first");
			removeDb();
		}
		Files.createDirectories(TEST_DB_LOCATION);

		
		if(VERBOSE) System.out.println("setting up the typed obj database");
		
		// point the type definition db to point there
		db = new TypeDefinitionDB(
				new FileTypeStorage(TEST_DB_LOCATION.toString()));
		
		
		// create a validator that uses the type def db
		validator = new TypedObjectValidator(new LocalTypeProvider(db));
	
		if(VERBOSE) System.out.println("loading db with types");
		String username = "wstester1";
		
		String kbSpec = loadResourceFile(TEST_RESOURCE_LOCATION+"KB.spec");
		db.requestModuleRegistration("KB", username);
		db.approveModuleRegistrationRequest(username, "KB", true);
		db.registerModule(kbSpec ,KB_TYPES, username);
		db.releaseModule("KB", username, false);
		
		if(VERBOSE) System.out.print("finding test instances: ");
		String [] resourcesArray = getResourceListing(TEST_RESOURCE_LOCATION);
		Arrays.sort(resourcesArray);
		for(int k=0; k<resourcesArray.length; k++) {
			String [] tokens = resourcesArray[k].split("\\.");
			if(tokens[0].equals("instance")) {
				resources.add(new TestInstanceInfo(resourcesArray[k]));
			}
		}
		if (resources.size() != EXPECTED_TESTS) {
			System.out.println("Missing test resources - found " +
					resources.size() + ", expected " + EXPECTED_TESTS);
			throw new TestException("Missing test resources - found " +
					resources.size() + ", expected " + EXPECTED_TESTS);
			
		}
		if(VERBOSE) System.out.println("found "+resources.size()+" instances.");
	}
	
	@AfterClass
	public static void removeDb() throws IOException {
		File dir = TEST_DB_LOCATION.toFile();
		FileUtils.deleteDirectory(dir);
		if(VERBOSE) System.out.println("deleting typed obj database");
	}
	
	@Test
	public void testInstance() {
		
		// load the instance information
		TestInstanceInfo resource = resources.get(this.instanceNumber);
		String testConditionsString;
		try {
			testConditionsString = loadResourceFile(TEST_RESOURCE_LOCATION+resource.resourceName);
		} catch (Exception e) {
			if (VERBOSE) System.out.println("    malformed test case, cannot load '"+resource.resourceName+"'. error was: "+e.getMessage());
			throw new RuntimeException("malformed test case, cannot load '"+resource.resourceName+"'. error was: "+e.getMessage());
		}
		
		ObjectMapper mapper = new ObjectMapper().configure(Feature.ALLOW_COMMENTS, true);
		JsonNode testConditions;
		try {
			testConditions = mapper.readTree(testConditionsString);
		} catch (Exception e) {
			if (VERBOSE) System.out.println("malformed test case, cannot parse '"+resource.resourceName+"' as JSON. error was: "+e.getMessage());
			throw new RuntimeException("malformed test case, cannot parse '"+resource.resourceName+"' as JSON. error was: "+e.getMessage());
		} 
		
		// perform some checking on the test condition JSON
		
		String expectedResult = testConditions.get("result").asText();
		if(!expectedResult.equals("pass") && !expectedResult.equals("error")) {
			if(VERBOSE) System.out.println("     malformed test case, result must be pass/error.");
			throw new RuntimeException("malformed test case, result must be pass/error in "+resource.resourceName);
		}
		
		String typeName = testConditions.get("type").asText();
		String [] typeTokens = typeName.split("\\.");
		if(typeTokens.length<2 || typeTokens.length>3) {
			if(VERBOSE) System.out.println("     malformed test case, type name could not be parsed from type.");
			throw new RuntimeException("malformed test case,  type name could not be parsed from type in "+resource.resourceName);
		}
		
		JsonNode instance = testConditions.get("instance");
		if(instance==null) {
			if(VERBOSE) System.out.println("     malformed test case, no instance defined.");
			throw new RuntimeException("malformed test case,  no instance defined in "+resource.resourceName);
		}
		

		if(VERBOSE) System.out.println("  -TEST ("+resource.resourceName+") - instance of '"+typeName+"' expected result: "+expectedResult+".");
		// actually perform the test and verify we get what is expected
		
		final IdReferenceHandlerSetFactory fac = IdReferenceHandlerSetFactoryBuilder
				.getBuilder(6).build().getFactory(null);
		IdReferenceHandlerSet<String> handler = fac.createHandlers(String.class);
		handler.associateObject("foo");
		
		try {
			ValidatedTypedObject report = 
				validator.validate(
					instance,
					new TypeDefId(new TypeDefName(typeTokens[0],typeTokens[1])),
					handler
					);
			
			// if we expected to pass, we must pass without any error messages
			if(expectedResult.equals("pass")) {
				StringBuilder errMssgs = new StringBuilder();
				List <String> mssgs = report.getErrorMessages();
				for(int i=0; i<mssgs.size(); i++) {
					errMssgs.append("    ["+i+"]:"+mssgs.get(i));
				}
				if(VERBOSE) if(!report.isInstanceValid()) System.out.println("    FAILED! Instance should validate, but doesn't. errors were: \n"+errMssgs.toString());
				assertTrue("  -("+resource.resourceName+") does not validate, but should.  Errors are: \n"+errMssgs.toString(),
						report.isInstanceValid());
			}
			
			else { //if(expectedResult.equals("error")) {

				// if we expected to fail, then we must confirm that we did fail
				if(VERBOSE) if(report.isInstanceValid()) System.out.println("    FAILED! Instance passes validation, but should not");
				assertFalse("  -("+resource.resourceName+") passes validation, but should not.",
						report.isInstanceValid());
				
				// and the error messages must match IN THE CORRECT ORDER!
				JsonNode expectedErrorMssgs = testConditions.get("error-mssgs");
				if(expectedErrorMssgs == null) {	
					if(VERBOSE) System.out.println("     FAILED! Instance does not validate as expected, but test case does not specify the expected error messages.");
					fail("Instance does not validate as expected, but test case does not specify the expected error messages in "+resource.resourceName);
				}
				if(!expectedErrorMssgs.isArray()) {	
					if(VERBOSE) System.out.println("     FAILED! Instance does not validate as expected, but test case does not specify the expected error messages as a list.");
					fail("Instance does not validate as expected, but test case does not specify the expected error messages as a list in "+resource.resourceName);
				}
				
				List <String> mssgs = report.getErrorMessages();
				if(VERBOSE) {
					if(mssgs.size()!=expectedErrorMssgs.size()) {
						System.out.println("     FAILED! The number of expected error messages does not match! Expected "
							+ expectedErrorMssgs.size() + " errors, but got:");
						for(int k=0; k<mssgs.size(); k++) {
							System.out.println("      ["+k+"]:"+mssgs.get(k));
						}
					}
				}
				assertTrue("  -("+resource.resourceName+") The number of expected error messages does not match. Expected "
						+  expectedErrorMssgs.size() + " errors, but got "+mssgs.size(),
						mssgs.size()==expectedErrorMssgs.size());
				
				
				for(int i=0; i<mssgs.size(); i++) {
					if(VERBOSE) {
						if(!mssgs.get(i).equals(expectedErrorMssgs.get(i).asText())) {
							System.out.println("     FAILED! Error["+i+"] does not match expected error message.");
							System.out.println("     Expected:'"+expectedErrorMssgs.get(i).asText()+"'\n     Errors were:");
							for(int k=0; k<mssgs.size(); k++) {
								System.out.println("      ["+k+"]:"+mssgs.get(k));
							}
						}
					}
					assertTrue("  -("+resource.resourceName+") validation provides wrong error message for error "+i+"\n"
							+ "    Expected: " + expectedErrorMssgs.get(i).asText()
							+ "    Got:      " + mssgs.get(i),
							mssgs.get(i).equals(expectedErrorMssgs.get(i).asText()));
				}
			}
			
		} catch (Exception e) {
			// if an exception was thrown, and test should pass, then we fail
			if(expectedResult.equals("pass")) {
				if(VERBOSE) System.out.println("    FAILED! Instance should validate, but doesn't.   Exception was : "+e.getClass().getName() +"\nmssg: "+e.getMessage());
				fail("  -("+resource.resourceName+") does not validate, but should.  Exception was : "+e.getClass().getName() +"\nmssg: "+e.getMessage());
			} else  { // if(expectedResult.equals("error"))
				// if exception was thrown, we need to confirm that the correct exception class was thrown
				if(testConditions.get("exception-class")==null) {
					if(VERBOSE) System.out.println("     FAILED! Instance validation throws exception, but test case does not specify that there is an expected exception.\n"
							+"     Exception was : "+e.getClass().getName() +"\n     mssg: "+e.getMessage());
					fail("Instance validation throws exception, but test case does not specify that there is an expected exception in "+resource.resourceName);
				}
				String expectedException = testConditions.get("exception-class").asText();
				if(!expectedException.equals(e.getClass().getName())) {
					if(VERBOSE) System.out.println("    FAILED! Validation throws wrong exception.\n    Expected: '"
									+expectedException+"', but was : "+e.getClass().getName() +"\n    mssg: "+e.getMessage());
				}
				assertTrue("  -("+resource.resourceName+") validation throws wrong exception.    Expected: '"
									+expectedException+"', but was : "+e.getClass().getName() +"\n    mssg: "+e.getMessage(),
									expectedException.equals(e.getClass().getName()));
			}
		}
		
		
		/*if(expectedResult.equals)
		String expectedErrorType = testConditions.get("error-type");
		"error-type":"invalid",
		"error-mssgs":[],*/
		
		
		if(VERBOSE) System.out.println("      PASS.");
	}
	
	
	/**
	 * helper method to load test files, mostly copied from TypeRegistering test
	 */
	private static String loadResourceFile(String resourceName) throws Exception {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		InputStream is = DetailedValidationTest.class.getResourceAsStream(resourceName);
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
		URL dirURL = DetailedValidationTest.class.getResource(path);
		if (dirURL != null && dirURL.getProtocol().equals("file")) {
			/* A file path: easy enough */
			return new File(dirURL.toURI()).list();
		}

		if (dirURL == null) {
			// In case of a jar file, we can't actually find a directory.
			// Have to assume the same jar as the class.
			String me = DetailedValidationTest.class.getName().replace(".", "/")+".class";
			dirURL = DetailedValidationTest.class.getResource(me);
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
				String fullPath = DetailedValidationTest.class.getPackage().getName().replace(".","/") + "/" + path;
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
