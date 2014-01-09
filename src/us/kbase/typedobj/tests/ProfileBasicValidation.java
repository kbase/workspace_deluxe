package us.kbase.typedobj.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;

import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.db.TypeStorage;
import us.kbase.typedobj.db.test.TypeRegisteringTest;
import us.kbase.workspace.kbase.Util;
import us.kbase.workspace.test.WorkspaceTestCommon;

public class ProfileBasicValidation {
	private final static String TEST_DB_LOCATION = "test/typedobj_test_files/t1";
	private final static String TEST_RESOURCE_LOCATION = "files/t1/";
	private static TypeDefinitionDB db;
	private static TypedObjectValidator validator;

	public static void main(String[] args) throws Exception {
		//ensure test location is available
		File dir = new File(TEST_DB_LOCATION);
		if (dir.exists()) {
			//fail("database at location: "+TEST_DB_LOCATION+" already exists, remove/rename this directory first");
			removeDb();
		}
		if(!dir.mkdirs()) {
			fail("unable to create needed test directory: "+TEST_DB_LOCATION);
		}
		
		// point the type definition db to point there
		File tempdir = new File("temp_files");
		if (!tempdir.exists())
			tempdir.mkdir();
		TypeStorage storage = new MongoTypeStorage(TypeRegisteringTest.createMongoDbConnection());
		storage.removeAllData();
		db = new TypeDefinitionDB(storage, tempdir, 
				new Util().getKIDLpath(), WorkspaceTestCommon.getKidlSource());
		
		
		// create a validator that uses the type def db
		validator = new TypedObjectValidator(db);
	
		
		String username = "wstester1";
		
		String kbSpec = loadResourceFile(TEST_RESOURCE_LOCATION+"KB.spec");
		List<String> kb_types =  Arrays.asList("Feature","Genome","FeatureGroup","genome_id","feature_id");
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
		
		String [] resources = getResourceListing(TEST_RESOURCE_LOCATION);
		List<TestInstanceInfo> instanceList = new ArrayList<TestInstanceInfo>();
		for(int k=0; k<resources.length; k++) {
			String [] tokens = resources[k].split("\\.");
			if(tokens.length!=5) { continue; }
			if(tokens[3].equals("instance")) {
				String instanceJson = loadResourceFile(TEST_RESOURCE_LOCATION+resources[k]);
				if(tokens[2].equals("valid")) {
					instanceList.add(new TestInstanceInfo(resources[k],tokens[0],tokens[1],instanceJson,true));
				} else if(tokens[2].equals("invalid")) {
					instanceList.add(new TestInstanceInfo(resources[k],tokens[0],tokens[1],instanceJson,false));
				}
			}
		}
		while (true) {
			for (TestInstanceInfo instance : instanceList)
				test(instance);
		}
	}
		
	public static void removeDb() throws IOException {
		File dir = new File(TEST_DB_LOCATION);
		FileUtils.deleteDirectory(dir);
	}
	
	/**
	 * helper method to load test files, mostly copied from TypeRegistering test
	 */
	private static String loadResourceFile(String resourceName) throws Exception {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		InputStream is = TestBasicValidation.class.getResourceAsStream(resourceName);
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
		URL dirURL = TestBasicValidation.class.getResource(path);
		if (dirURL != null && dirURL.getProtocol().equals("file")) {
			/* A file path: easy enough */
			return new File(dirURL.toURI()).list();
		}

		if (dirURL == null) {
			// In case of a jar file, we can't actually find a directory.
			// Have to assume the same jar as the class.
			String me = TestBasicValidation.class.getName().replace(".", "/")+".class";
			dirURL = TestBasicValidation.class.getResource(me);
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
				String fullPath = TestBasicValidation.class.getPackage().getName().replace(".","/") + "/" + path;
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

	private static void test(TestInstanceInfo instance) {
		if(instance.isValid) {

			try {
				TypedObjectValidationReport report = 
					validator.validate(
						instance.instanceJson,
						new TypeDefId(new TypeDefName(instance.moduleName,instance.typeName))
						);
				
				// print errors, if any before the assert to aid in testing
				report.getErrorMessagesAsList();
				assertTrue("  -("+instance.resourceName+") does not validate, but should",report.isInstanceValid());
				//System.out.println("  *("+instance.resourceName+")");
				//System.out.println(report.toString());
			} catch (Exception e) {
				//if an exception is thrown, the object did not validate, so we failed
				fail("("+instance.resourceName+") does not validate, but should");
			}
		} else {

			try {
				TypedObjectValidationReport report = 
					validator.validate(
						instance.instanceJson,
						new TypeDefId(new TypeDefName(instance.moduleName,instance.typeName))
						);
				assertFalse("  -("+instance.resourceName+") validates, but should not",report.isInstanceValid());
				//System.out.println("  -("+instance.resourceName+")");
				//System.out.println(report.toString());

			} catch (Exception e) {
				//if an exception is thrown, it must be an InstanceValidationException
				//we are not testing if an incorrect module name or type name is given here
				if(! e.getClass().getSimpleName().equals("InstanceValidationException")) {
					fail("  -("+instance.resourceName+") did not validate successfully, but exception thrown was '"+e.getClass().getSimpleName()+"' and not 'InstanceValidationException'");
				}
			}
		}
	}
	
	private static class TestInstanceInfo {
		public String resourceName;
		public String moduleName;
		public String typeName;
		public String instanceJson;
		public boolean isValid;

		public TestInstanceInfo(String resourceName, String moduleName, String typeName, 
				String instanceJson, boolean isValid) {
			this.resourceName = resourceName;
			this.moduleName = moduleName;
			this.typeName = typeName;
			this.instanceJson = instanceJson;
			this.isValid = isValid;
		}
	}
}
