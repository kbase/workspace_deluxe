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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import us.kbase.common.test.TestException;
import us.kbase.typedobj.core.ObjectPaths;
import us.kbase.typedobj.core.SubdataExtractor;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;

/**
 * @author msneddon
 *
 */
@RunWith(value = Parameterized.class)
public class ObjectExtractionByPathTest {

	private final static int TEST_COUNT = 9;
	
	private final static String TEST_RESOURCE_LOCATION = "files/t5/";
	
	private final static boolean VERBOSE = true;
	
	/*
	 * structures to store info on each instance we wish to validate 
	 */
	
	private static List<TestInstanceInfo> instanceResources = new ArrayList <TestInstanceInfo> ();
	
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
	
	public ObjectExtractionByPathTest(Integer instanceNumber) {
		this.instanceNumber = instanceNumber.intValue();
	}
	
	
	/**
	 * Generate the parameters to the tests
	 */
	@Parameters
	public static Collection<Object[]> assembleTestInstanceList() throws Exception {
		
		if(VERBOSE) System.out.print("finding test instances...");
		String [] resources = getResourceListing(TEST_RESOURCE_LOCATION);
		for(int k=0; k<resources.length; k++) {
			if(resources[k].isEmpty()) continue;
			instanceResources.add(new TestInstanceInfo(resources[k]));
		}
		if (TEST_COUNT != instanceResources.size()) {
			String err = String.format(
					"Incorrect test count, got: %s, expected %s",
					instanceResources.size(), TEST_COUNT);
			System.out.println(err);
			throw new TestException(err);
		}
		
		if(VERBOSE) System.out.println(" " + instanceResources.size() + " found");
		
		Object [][] instanceInfo = new Object[instanceResources.size()][1];
		for(int k=0; k<instanceResources.size(); k++) {
			instanceInfo[k][0] = new Integer(k);
		}
		return Arrays.asList(instanceInfo);
	}
	
	
	
	@Test
	public void testInstance() throws Exception {
		
		TestInstanceInfo instance = instanceResources.get(this.instanceNumber);
		String instanceJson = loadResourceFile(TEST_RESOURCE_LOCATION+instance.resourceName);
		if(VERBOSE) System.out.println("  -EXTRACTION TEST ("+instance.resourceName+")");
		
		ObjectMapper mapper = new ObjectMapper();
		JsonNode instanceData = mapper.readTree(instanceJson);
		JsonNode paths = instanceData.get("paths");
		JsonNode data = instanceData.get("data");
		JsonNode expectedExtract = instanceData.get("extract");
		boolean expectError = false;
		String expectedErrorMessage = "";
		if(expectedExtract.isTextual()) {
			expectError = true;
			expectedErrorMessage = expectedExtract.asText();
		}
		
		List<String>pathStrings = new ArrayList <String> (paths.size());
		for(int k=0; k<paths.size(); k++) {
			pathStrings.add(paths.get(k).asText());
		}
		ObjectPaths op = new ObjectPaths(pathStrings);
		try {
			JsonNode extract = SubdataExtractor.extract(op, data);
			
			assertFalse("  -("+instance.resourceName+") extracted something when error was expected; extract="+extract,expectError);
			
			compare(extract,expectedExtract, instance.resourceName);
			
		} catch(TypedObjectExtractionException e) {
			assertTrue("  -("+instance.resourceName+") error message should be '"+expectedErrorMessage+"', but was: '"+e.getMessage()+"'",
					expectedErrorMessage.equals(e.getMessage()));
		}
		if(VERBOSE) System.out.println("      PASS.");
	}
	
	public void compare(JsonNode expectedSubset, JsonNode actualSubset, String resourceName) throws IOException {
		assertEquals("  -(" + resourceName + ") extracted object does not match expected extract",
				sortJson(expectedSubset), sortJson(actualSubset));
	}

	private static String sortJson(JsonNode tree) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
		Object map = mapper.treeToValue(tree, Object.class);
		return mapper.writeValueAsString(map);
	}
	
	/**
	 * helper method to load test files, mostly copied from TypeRegistering test
	 */
	private static String loadResourceFile(String resourceName) throws Exception {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		InputStream is = ObjectExtractionByPathTest.class.getResourceAsStream(resourceName);
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
		URL dirURL = ObjectExtractionByPathTest.class.getResource(path);
		if (dirURL != null && dirURL.getProtocol().equals("file")) {
			/* A file path: easy enough */
			return new File(dirURL.toURI()).list();
		}

		if (dirURL == null) {
			// In case of a jar file, we can't actually find a directory.
			// Have to assume the same jar as the class.
			String me = ObjectExtractionByPathTest.class.getName().replace(".", "/")+".class";
			dirURL = ObjectExtractionByPathTest.class.getResource(me);
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
				String fullPath = ObjectExtractionByPathTest.class.getPackage().getName().replace(".","/") + "/" + path;
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
