package us.kbase.typedobj.core.validatornew;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import junit.framework.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.db.TypeStorage;
import us.kbase.typedobj.db.test.TypeRegisteringTest;
import us.kbase.workspace.kbase.Util;

public class JsonTokenValidatorTest {
	private static final String WORK_DIR = "/Users/rsutormin/Work/2014-01-15_hugeobject";
	private static final long seed = 1234567890L;
	private static final Random rnd = new Random(seed);

	public static void main(String[] args) throws Exception {
		File dir = new File(".");
		// point the type definition db to point there
		TypeStorage storage = new MongoTypeStorage(TypeRegisteringTest.createMongoDbConnection());
		storage.removeAllData();
		TypeDefinitionDB db = new TypeDefinitionDB(storage, dir, new Util().getKIDLpath(), null);
		// create a validator that uses the type def db
		String username = "wstester1";
		String moduleName = "KBaseNetworks";
		//String kbSpec = loadSpecFile(new File(WORK_DIR, moduleName + ".spec"));
		String typeName = "Network";
		String kbSpec = "module " + moduleName + " {typedef structure {string val;} " + typeName + ";};";
		db.requestModuleRegistration(moduleName, username);
		db.approveModuleRegistrationRequest(username, moduleName, true);
		db.registerModule(kbSpec, Arrays.asList(typeName), username);
		db.releaseModule(moduleName, username, false);
		//String schemaText = db.getJsonSchemaDocument();
		//NodeSchema schema = NodeSchema.parseJsonSchema(schemaText);
		//JsonFactory jf = new JsonFactory();
		//JsonParser jp = jf.createParser(new File(WORK_DIR, "network.json"));
		//JsonNode tree = new ObjectMapper().readTree(new File(WORK_DIR, "network.json"));
		File f = new File("test/temp.json");
		/*Writer fw = new BufferedWriter(new FileWriter(f));
		fw.write("{\"val\":\"");
		for (int i = 0; i < 80000000; i++)
			fw.write("test" + i);
		fw.write("\"}");
		fw.close();*/
		//String largeValue = "123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_1234567890";
		for (int n = 0; n < 1000; n++) {
			int buffer = 100 + rnd.nextInt(1000000);
			Map<String, String> map = new LinkedHashMap<String, String>();
			map.put("val_", generateLargeString(rnd, buffer - 1));
			map.put("val", generateLargeString(rnd, buffer));
			map.put("val-", generateLargeString(rnd, buffer + 1));
			for (int i = 0; i < 100; i++) {
				int len = buffer + rnd.nextInt(buffer);
				map.put("\"val" + i, "test\"" + i + ((i % 10) == 9 ? generateLargeString(rnd, len) : ""));
			}
			new ObjectMapper().writeValue(f, map);
			//System.out.println("Data was loaded (" + Runtime.getRuntime().maxMemory() + ")");
			long time = System.currentTimeMillis();
			JsonTokenStream jp = new JsonTokenStream(f, buffer);
			TypedObjectValidationReport report = new JsonTokenValidator(db).validate(jp, 
					new TypeDefId(new TypeDefName(moduleName, typeName)));
			Assert.assertTrue(report.isInstanceValid());
			//System.out.println(report.getErrorMessagesAsList());
			System.out.println(buffer + "\t" + (System.currentTimeMillis() - time) + " ms");
			jp.setRoot(null);
			File f2 = new File("test/temp2.json");
			jp.writeJson(f2);
			compareFiles(f, f2);
		}
	}

	private static void compareFiles(File f1, File f2) throws IOException {
		Reader r1 = new FileReader(f1);
		Reader r2 = new FileReader(f2);
		compareReaders(r1, r2);
		r1.close();
		r2.close();
	}
	
	private static void compareReaders(Reader r1, Reader r2) throws IOException {
		int bufSize = 1000000;
		char[] buf1 = new char[bufSize];
		char[] buf2 = new char[bufSize];
		long size = 0;
		while (true) {
			int c1 = readAsMuchAsPossible(r1, buf1);
			int c2 = readAsMuchAsPossible(r2, buf2);
			if (c1 != c2)
				throw new IllegalStateException("Sources have different sizes: " + 
						(c1 == bufSize ? ">" : "") + (size + c1) + ", " + (c2 == bufSize ? ">" : "") + (size + c2));
			for (int i = 0; i < c1; i++)
				if (buf1[i] != buf2[i])
					throw new IllegalStateException("Sources differ at position " + (size + i));
			size += c1;
			if (c1 < bufSize)
				break;
		}
	}
	
	private static int readAsMuchAsPossible(Reader r, char[] buf) throws IOException {
		int pos = 0;
		while (true) {
			int count = r.read(buf, pos, buf.length - pos);
			if (count < 0)
				break;
			pos += count;
			if (pos == buf.length)
				break;
		}
		return pos;
	}
	
	private static String generateLargeString(Random rnd, int len) {
		char[] data = new char[len];
		for (int i = 0; i < len; i++)
			data[i] = (char)(32 + rnd.nextInt(95));
		return new String(data);
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
