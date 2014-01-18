package us.kbase.typedobj.core.validatornew;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.db.TypeStorage;
import us.kbase.typedobj.db.test.TypeRegisteringTest;
import us.kbase.workspace.kbase.Util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonTokenValidatorTest {
	private static final String WORK_DIR = "/Users/rsutormin/Work/2014-01-15_hugeobject";

	public static void main(String[] args) throws Exception {
		File dir = new File(".");
		// point the type definition db to point there
		TypeStorage storage = new MongoTypeStorage(TypeRegisteringTest.createMongoDbConnection());
		storage.removeAllData();
		TypeDefinitionDB db = new TypeDefinitionDB(storage, dir, new Util().getKIDLpath(), null);
		// create a validator that uses the type def db
		String username = "wstester1";
		String moduleName = "KBaseNetworks";
		String kbSpec = loadSpecFile(new File(WORK_DIR, moduleName + ".spec"));
		String typeName = "Network";
		db.requestModuleRegistration(moduleName, username);
		db.approveModuleRegistrationRequest(username, moduleName, true);
		db.registerModule(kbSpec, Arrays.asList(typeName), username);
		db.releaseModule(moduleName, username, false);
		//String schemaText = db.getJsonSchemaDocument();
		//NodeSchema schema = NodeSchema.parseJsonSchema(schemaText);
		//JsonFactory jf = new JsonFactory();
		//JsonParser jp = jf.createParser(new File(WORK_DIR, "network.json"));
		JsonNode tree = new ObjectMapper().readTree(new File(WORK_DIR, "network.json"));
		System.out.println("Data was loaded");
		long time = System.currentTimeMillis();
		TypedObjectValidationReport report = new JsonTokenValidator(db).validate(tree, 
				new TypeDefId(new TypeDefName(moduleName, typeName)));
		System.out.println(report.isInstanceValid());
		System.out.println(report.getErrorMessagesAsList());
		System.out.println("Time: " + (System.currentTimeMillis() - time) + " ms");
	}
	
	private static String loadSpecFile(File specPath) throws Exception {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		InputStream is = new FileInputStream(specPath);
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
