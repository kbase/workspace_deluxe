package us.kbase.typedobj.drivers;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import com.github.fge.jsonschema.report.ProcessingReport;

import us.kbase.typedobj.core.IdReference;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.db.UserInfoProviderForTests;

public class TypedObjExample1 {

	public static void main(String[] args) throws Exception {
		
		System.out.println("kbase typed object example 1 - creating a database");
		
		
		// Create a simple db
		String dblocation = "test/typedobj_test_files/t1/db";
		TypeDefinitionDB db            = new TypeDefinitionDB(new FileTypeStorage(dblocation), new UserInfoProviderForTests());
		System.out.println("connecting to: "+dblocation);
		
		// list all the modules that have been loaded
		List<String> allModules = db.getAllRegisteredModules();
		System.out.println("all registered modules:");
		for(String name : allModules) {
			System.out.println("\t"+name);
		}
		
		
		// SET KB_TOP in environment before running this; delete the files in the db dir if you want to recreate the db
		/*String username = "wstester1";
		String kbSpec = readTestFile("test/typedobj_test_files/t1/spec/KB.spec");
		db.registerModule(kbSpec, Arrays.asList("Feature","Genome","FeatureGroup","genome_id","feature_id"), username);
		String fbaSpec = readTestFile("test/typedobj_test_files/t1/spec/FBA.spec");
		db.registerModule(fbaSpec, Arrays.asList("FBAModel","FBAResult","fba_model_id"), username);
		*/
		
		// Create a simple validator that finds objects using the db
		TypedObjectValidator validator = new TypedObjectValidator(db);
		
		String instance1 = "{\"id\":\"g.1\",\"name\":\"myGenome\",\"sequence\":\"gataca\",\"feature_ids\":[\"cds.8\",\"cds.99\"]}";
		
		TypedObjectValidationReport report = validator.validate(instance1, new TypeDefId(new TypeDefName("KB", "Genome")));
		
		List<IdReference> idRefList = report.getListOfIdReferences();
		
		for(IdReference idRef: idRefList) {
			System.out.println(idRef);
		}
		
		
		

	}
	
	private static String readTestFile(String filename) throws Exception {
		BufferedReader br = new BufferedReader(new FileReader(filename));
		StringBuilder sb = new StringBuilder();
		String line = br.readLine();
		while (line != null) {
			sb.append(line);
			sb.append('\n');
			line = br.readLine();
		}
		br.close();
		return sb.toString();
	}
	

}
