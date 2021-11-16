package us.kbase.typedobj.test;

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

import us.kbase.common.test.TestCommon;
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
 * simple test of the basic typed object validation framework that creates a simple
 * file storage typed object database using a couple spec files, looks at the directory
 * containing instances to validate, and ensures that the instances validate or don't
 * as indicated.
 * 
 * The test files are in us.kbase.typedobj.tests.files.t1
 * 
 * You can add as many instances to validate as you like by naming text files as:
 *   ModuleName.TypeName.[valid|invalid].instance.N
 * where you need to indicate if the instance is valid or not, and N is any string
 * identifier for the test, usually integer numbers.
 * 
 * If the spec files are updated or new ones added, you need to modify the db
 * setup method to add the new typed obj defs.
 * 
 * 
 * @author msneddon
 *
 */
@RunWith(value = Parameterized.class)
public class BasicValidationTest {
	
	private static final int VALID_TEST_COUNT = 16;
	private static final int INVALID_TEST_COUNT = 29;

	private final static Path TEST_DB_LOCATION =
			Paths.get(TestCommon.getTempDir())
			.resolve("BasicValidationTest");
	
	private final static String TEST_RESOURCE_LOCATION = "files/BasicValidation/";
	
	private static TypeDefinitionDB db;
	
	private static TypedObjectValidator validator;
	
	private final static boolean VERBOSE = true;
	
	/*
	 * structures to store info on each instance we wish to validate 
	 */
	
	private static List<TestInstanceInfo> validInstanceResources = new ArrayList <TestInstanceInfo> ();
	private static List<TestInstanceInfo> invalidInstanceResources = new ArrayList <TestInstanceInfo> ();
	
	private static class TestInstanceInfo {
		public TestInstanceInfo(String resourceName, String moduleName, String typeName, boolean isValid) {
			this.resourceName = resourceName;
			this.moduleName = moduleName;
			this.typeName = typeName;
			this.isValid = isValid;
		}
		public String resourceName;
		public String moduleName;
		public String typeName;
		@SuppressWarnings("unused")
		public boolean isValid;
	}
	
	/**
	 * As each test instance object is created, this sets which instance to actually test
	 */
	private int instanceNumber;
	private boolean isValidInstance;
	
	public BasicValidationTest(Integer instanceNumber, Boolean isValidInstance) {
		this.instanceNumber = instanceNumber.intValue();
		this.isValidInstance = isValidInstance.booleanValue();
	}
	
	
	/**
	 * This is invoked before anything else, so here we invoke the creation of the db
	 * @return
	 * @throws Exception 
	 */
	@Parameters
	public static Collection<Object[]> assembleTestInstanceList() throws Exception {
		prepareDb();
		Object [][] instanceInfo = new Object[validInstanceResources.size()+invalidInstanceResources.size()][2];
		for(int k=0; k<validInstanceResources.size(); k++) {
			instanceInfo[k][0] = k;
			instanceInfo[k][1] = true;
		}
		for(int k=0; k<invalidInstanceResources.size(); k++) {
			instanceInfo[k+validInstanceResources.size()][0] = k;
			instanceInfo[k+validInstanceResources.size()][1] = false;
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
		List<String> kb_types =  Arrays.asList("Feature","Genome","FeatureGroup","genome_id","feature_id","RandomObject","NumberObj","TupleObject","BigNumberObj");
		db.requestModuleRegistration("KB", username);
		db.approveModuleRegistrationRequest(username, "KB", true);
		db.registerModule(kbSpec ,kb_types, username);
		db.releaseModule("KB", username, false);
		
		String fbaSpec = loadResourceFile(TEST_RESOURCE_LOCATION+"FBA.spec");
		List<String> fba_types =  Arrays.asList("FBAModel","FBAResult","fba_model_id");
		db.requestModuleRegistration("FBA", username);
		db.approveModuleRegistrationRequest(username, "FBA", true);
		db.registerModule(fbaSpec ,fba_types, username);
		db.releaseModule("FBA", username, false);
		
		if(VERBOSE) System.out.print("finding test instances: ");
		String [] resources = getResourceListing(TEST_RESOURCE_LOCATION);
		Arrays.sort(resources);
		for(int k=0; k<resources.length; k++) {
			String [] tokens = resources[k].split("\\.");
			if(tokens.length!=5) { continue; }
			if(tokens[3].equals("instance")) {
				if(tokens[2].equals("valid")) {
					validInstanceResources.add(new TestInstanceInfo(resources[k],tokens[0],tokens[1],true));
				} else if(tokens[2].equals("invalid")) {
					invalidInstanceResources.add(new TestInstanceInfo(resources[k],tokens[0],tokens[1],false));
				}
			}
		}
		if (VALID_TEST_COUNT != validInstanceResources.size() ||
				INVALID_TEST_COUNT != invalidInstanceResources.size()) {
			String err = String.format(
					"Incorrect test count, got valid: %s, invalid: %s. Expected valid: %s, invalid: %s",
					validInstanceResources.size(), invalidInstanceResources.size(),
					VALID_TEST_COUNT, INVALID_TEST_COUNT);
			System.out.println(err);
			throw new TestException(err);
		}
		if(VERBOSE) System.out.println(validInstanceResources.size()+" valid, "+invalidInstanceResources.size()+" invalid");
	}
	
	//@After
	//public void clear 
	
	@AfterClass
	public static void removeDb() throws IOException {
		File dir = TEST_DB_LOCATION.toFile();
		FileUtils.deleteDirectory(dir);
		if(VERBOSE) System.out.println("deleting typed obj database");
	}
	
	@Test
	public void testInstance() throws Exception {
		
		final IdReferenceHandlerSetFactory fac = IdReferenceHandlerSetFactoryBuilder
				.getBuilder(6).build().getFactory(null);
		IdReferenceHandlerSet<String> handler = fac.createHandlers(String.class);
		handler.associateObject("foo");
		
		if(this.isValidInstance) {
			// load the instance information
			TestInstanceInfo instance = validInstanceResources.get(this.instanceNumber);
			if(VERBOSE) System.out.println("  -VALID TEST ("+instance.resourceName+")");
			String instanceJson = loadResourceFile(TEST_RESOURCE_LOCATION+instance.resourceName);
			
			try {
				ValidatedTypedObject report = 
					validator.validate(
						instanceJson,
						new TypeDefId(new TypeDefName(instance.moduleName,instance.typeName)),
						handler
						);
				
				// print errors, if any before the assert to aid in testing
				List <String> mssgs = report.getErrorMessages();
				for(int i=0; i<mssgs.size(); i++) {
					if(VERBOSE) System.out.println("    ["+i+"]:"+mssgs.get(i));
				}
				
				assertTrue("  -("+instance.resourceName+") does not validate, but should",report.isInstanceValid());
				//System.out.println("  *("+instance.resourceName+")");
				//System.out.println(report.toString());
			} catch (Exception e) {
				//if an exception is thrown, the object did not validate, so we failed
				System.out.println(e);
				fail("("+instance.resourceName+") did not validate - an exception was thrown during validation");
			}
		} else {

			// load the instance information
			TestInstanceInfo instance = invalidInstanceResources.get(this.instanceNumber);
			if(VERBOSE) System.out.println("  -INVALID TEST ("+instance.resourceName+")");
			String instanceJson = loadResourceFile(TEST_RESOURCE_LOCATION+instance.resourceName);
			
			try {
				ValidatedTypedObject report = 
					validator.validate(
						instanceJson,
						new TypeDefId(new TypeDefName(instance.moduleName,instance.typeName)),
						handler);
				assertFalse("  -("+instance.resourceName+") validates, but should not",report.isInstanceValid());
				//System.out.println("  -("+instance.resourceName+")");
				//System.out.println(report.toString());

			} catch (Exception e) {
				//if an exception is thrown, it must be an InstanceValidationException
				//we are not testing if an incorrect module name or type name is given here
				if(! e.getClass().getSimpleName().equals("TypedObjectValidationException")) {
					fail("  -("+instance.resourceName+") did not validate successfully, but exception thrown was '"+e.getClass().getSimpleName()+"' and not 'TypedObjectValidationException'");
				}
			}
		}
		if(VERBOSE) System.out.println("      PASS.");
	}
	
	
	/**
	 * helper method to load test files, mostly copied from TypeRegistering test
	 */
	private static String loadResourceFile(String resourceName) throws Exception {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		InputStream is = BasicValidationTest.class.getResourceAsStream(resourceName);
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
		URL dirURL = BasicValidationTest.class.getResource(path);
		if (dirURL != null && dirURL.getProtocol().equals("file")) {
			/* A file path: easy enough */
			return new File(dirURL.toURI()).list();
		}

		if (dirURL == null) {
			// In case of a jar file, we can't actually find a directory.
			// Have to assume the same jar as the class.
			String me = BasicValidationTest.class.getName().replace(".", "/")+".class";
			dirURL = BasicValidationTest.class.getResource(me);
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
				String fullPath = BasicValidationTest.class.getPackage().getName().replace(".","/") + "/" + path;
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
