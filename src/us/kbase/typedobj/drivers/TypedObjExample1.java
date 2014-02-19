package us.kbase.typedobj.drivers;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.InstanceValidationException;
import us.kbase.typedobj.idref.IdReference;
import us.kbase.workspace.kbase.Util;
import us.kbase.workspace.test.WorkspaceTestCommon;

public class TypedObjExample1 {

	public static void main(String[] args) throws Exception {
		
		String dblocation = "test/typedobj_temp_test_files/testdb";
		try {
			System.out.println("kbase typed object example 1 - creating a database");
			
			
			// Create a simple db
			if (!new File(dblocation).exists())
				new File(dblocation).mkdir();
			// point the type definition db to point there
			File tempdir = new File("temp_files");
			if (!tempdir.exists())
				tempdir.mkdir();
			TypeDefinitionDB db = new TypeDefinitionDB(new FileTypeStorage(dblocation), tempdir, 
					new Util().getKIDLpath(),  WorkspaceTestCommon.getKidlSource());
			System.out.println("connecting to: "+dblocation);
			
			// list all the modules that have been loaded
			List<String> allModules = db.getAllRegisteredModules();
			System.out.println("all registered modules:");
			for(String name : allModules) {
				System.out.println("\t"+name);
			}
			
			// load the database if it does not exist
			String username = "wstester1";
			if(allModules.isEmpty()) {
				db.requestModuleRegistration("KB", username);
				db.approveModuleRegistrationRequest(username, "KB", true);
				db.requestModuleRegistration("FBA", username);
				db.approveModuleRegistrationRequest(username, "FBA", true);
				String kbSpec = loadResourceFile("../tests/files/t4/KB.spec");
				db.registerModule(kbSpec, Arrays.asList("Feature","Genome","genome_id"), username);
				db.releaseModule("KB", username, false);
				String fbaSpec = loadResourceFile("../tests/files/t4/FBA.spec");
				db.registerModule(fbaSpec, Arrays.asList("FBAModel","FBAResult","fba_model_id"), username);
				db.releaseModule("FBA", username, false);
			}
				
			 
			
			// Create a simple validator that finds objects using the db
			TypedObjectValidator validator = new TypedObjectValidator(db);
			
			String instance1 = 
					("{`source`:`g.0`,`name`:`ecoli`,`sequence`:`agct`,`bestFeature`:`kb|f/1`, `secondBest`:{`name`:`num2`,`sequence`:`gat`}, `feats`:[{`name`:`feat1`,`sequence`:`yadayada`},{`name`:`feat2`,`sequence`:`superseq`}],  "
							+ "`featmap`:{`fm1`:{`name`:`feat1`,`sequence`:`ttt`},`fm2`:{`name`:`feat2`,`sequence`:`superseq`}},"
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
			System.out.println("Validation errors:" );
			for(String err : report.getErrorMessagesAsList()) {
				System.out.println("  Error: "+err);
			}
			
			
			List<IdReference> idRefLists = report.getAllIdReferences();
			System.out.println("found ids:");
			for(IdReference idref: idRefLists) {
				System.out.println("\t"+idref);
			}
			
			System.out.println(report.getRawProcessingReport());
			
			
			
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
			
			// relabel, take a look at the results
			JsonNode relabeled = report.relabelWsIdReferences(absoluteIdRefMapping);
			
			//JsonNode relabeled = report.getJsonInstance();
			//validator.relableToAbsoluteIds(instance1RootNode, report);
			System.out.println("renamed:");
			writer.writeValue(s, instance1RootNode);
			System.out.println(s.toString());
			s.flush();
			s.reset();
			
			// extract just the subset
			JsonNode indexableSubset = report.extractSearchableWsSubset(relabeled); //.extractWsSearchableSubset(instance1RootNode, report);
			System.out.println("subset:");
			writer.writeValue(s, indexableSubset);
			System.out.println(s.toString());
			s.close();
			
			
		} catch(Exception e) {
			throw e;
		} finally {
			// delete the db
			File dir = new File(dblocation);
			FileUtils.deleteDirectory(dir);
		}
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
