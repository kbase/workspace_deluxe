package us.kbase.typedobj.drivers;


import us.kbase.typedobj.core.*;
import us.kbase.typedobj.db.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.report.ProcessingReport;




public class SimpleTest1 {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		
		// Create a simple db
		TypeDefinitionDB db= new SimpleTypeDefinitionDB(
				new FileTypeStorage("kb-typed-obj-lib/test_schemas/test_db_1"), new UserInfoProviderForTests());
		
		// Create a simple validator that finds objects using the db
		TypedObjectValidator validator = new TypedObjectValidator(db);
		TypeId t = new TypeId(new ModuleType("ID", "Feature"));
		
		// Validate something directly from JSON
		String instance1 = "{\"id\":\"f1\",\"name\":\"cheA\",\"start\":131,\"end\":582,\"other_ids\":[\"f8\",\"f99\"]," +
				"\"sizes\":{\"f33\":\"f33.2\",\"f44\":\"f44.1\"}}";
		ProcessingReport report = validator.validate(instance1, t);
		System.out.println("VALIDATION MESSAGE\n"+report.toString());
		
		
		// Validate something that is already parsed by Jackson into a JsonTree
		ObjectMapper mapper = new ObjectMapper();
		JsonNode instance1RootNode = mapper.readTree(instance1);
		ProcessingReport report2 = validator.validate(instance1RootNode, t);
		System.out.println("VALIDATION MESSAGE 2\n"+report2.toString());
		
		// print the 
		System.out.println(instance1RootNode);
		
		// Pull out the list of IDs from the report
		System.out.println(ReportUtil.getIdList(report2));
		
		// Pull out the searchable subset from the report
		System.out.println(ReportUtil.getSearchableSubset(report2));
		
	}
	
	

}
