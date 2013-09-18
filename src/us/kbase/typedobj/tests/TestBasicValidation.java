package us.kbase.typedobj.tests;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;

import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.SimpleTypeDefinitionDB;
import us.kbase.typedobj.db.TypeDefinitionDB;


public class TestBasicValidation {

	/**
	 * test file location, relative to the ant build.xml file from which tests are invoked
	 */
	private final static String test_file_location = "test_files/t1/spec_out/jsonschema";
	
	private static TypedObjectValidator validator;
	
	
	@BeforeClass
	public static void setupDb() throws FileNotFoundException {

		// Create a simple db
		TypeDefinitionDB db            = new SimpleTypeDefinitionDB(new FileTypeStorage(test_file_location));
		
		// Create a simple validator that finds objects using the db
		validator = new TypedObjectValidator(db);
		
	}
	
	@Test
	public void testValidInstances() {
		
		
		
		
		
		
		
		fail("Not yet implemented");
	}

}
