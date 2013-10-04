package us.kbase.typedobj.drivers;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.output.ByteArrayOutputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.fge.jackson.jsonpointer.JsonNodeResolver;
import com.github.fge.jackson.jsonpointer.ReferenceToken;
import com.github.fge.jsonschema.report.ProcessingReport;

import us.kbase.typedobj.core.IdReference;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.db.UserInfoProviderForTests;
import us.kbase.typedobj.exceptions.InstanceValidationException;

public class TypedObjExample1 {

	public static void main(String[] args) throws Exception {
		
		System.out.println("kbase typed object example 1 - creating a database");
		
		
		// Create a simple db
		String dblocation = "test/typedobj_temp_test_files/testdb";
		TypeDefinitionDB db            = new TypeDefinitionDB(new FileTypeStorage(dblocation), new UserInfoProviderForTests());
		System.out.println("connecting to: "+dblocation);
		
		// list all the modules that have been loaded
		List<String> allModules = db.getAllRegisteredModules();
		System.out.println("all registered modules:");
		for(String name : allModules) {
			System.out.println("\t"+name);
		}
		
		
		// SET KB_TOP in environment before running this; delete the files in the db dir if you want to recreate the db
		String username = "wstester1";
		if(allModules.isEmpty()) {
			db.approveModuleRegistrationRequest(username, "KB", username);
			db.approveModuleRegistrationRequest(username, "FBA", username);
			String kbSpec = loadResourceFile("../tests/files/t3/KB.spec");
			db.registerModule(kbSpec, Arrays.asList("Feature","Genome"), username);
			//String fbaSpec = loadResourceFile("../tests/files/t1/FBA.spec");
			//db.registerModule(fbaSpec, Arrays.asList("FBAModel","FBAResult","fba_model_id"), username);
		} 
		
		// Create a simple validator that finds objects using the db
		TypedObjectValidator validator = new TypedObjectValidator(db);
		
		String instance1 = 
				("{`name`:`ecoli`,`sequence`:`agct`,`bestFeature`:`kb|f/1`,"
				+ "`feature_ids`:[`f1`,`f2`],`length_of_features`:{`f1`:11,`f2`:22},"
				+ "`regulators`:{`f1`:[`f2`]} }").replace('`', '"');
		
		ObjectMapper mapper = new ObjectMapper();
		final JsonNode instance1RootNode;
		try {
			instance1RootNode = mapper.readTree(instance1);
		} catch (Exception e) {
			throw new InstanceValidationException("instance was not a valid or readable JSON document",e);
		}
		
		TypedObjectValidationReport report = validator.validate(instance1RootNode, new TypeDefId(new TypeDefName("KB", "Genome")));
		
		List<List<IdReference>> idRefLists = report.getListOfIdReferenceObjects();
		for(List<IdReference> idRefList : idRefLists) {
			for(IdReference idRef: idRefList) {
				System.out.println(idRef);
			}
		}
		
		System.out.println(report);
		
		
		//////////////////////////////////////
		ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
		ByteArrayOutputStream s = new ByteArrayOutputStream();
		System.out.println("\nraw object:");
		writer.writeValue(s, instance1RootNode);
		System.out.println(s.toString());
		s.reset();
		
		// set the replacement ids
		Map <String,String> absoluteIdRefMapping = new HashMap<String,String>();
		absoluteIdRefMapping.put("f1", "f1.abs");
		absoluteIdRefMapping.put("f2", "f2.abs");
		absoluteIdRefMapping.put("kb|f/1", "bad_id");
		report.setAbsoluteIdReferences(absoluteIdRefMapping);
		
		// relabel, take a look at the results
		validator.relableToAbsoluteIds(instance1RootNode, report);
		System.out.println("renamed:");
		writer.writeValue(s, instance1RootNode);
		System.out.println(s.toString());
		s.flush();
		s.reset();
		
		// extract just the subset
		JsonNode indexableSubset = validator.extractWsSearchableSubset(instance1RootNode, report);
		System.out.println("subset:");
		writer.writeValue(s, indexableSubset);
		System.out.println(s.toString());
		s.close();
	}
	
	
	/**
	 * helper method to load test files, mostly copied from TypeRegistering test
	 */
	private static String loadResourceFile(String resourceName) throws Exception {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		InputStream is = TypedObjExample1.class.getResourceAsStream(resourceName);
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
	

}
