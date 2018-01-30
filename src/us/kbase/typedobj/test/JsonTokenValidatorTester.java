package us.kbase.typedobj.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Random;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.LocalTypeProvider;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.ValidatedTypedObject;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.db.TypeStorage;
import us.kbase.typedobj.db.test.TypeRegisteringTest;
import us.kbase.typedobj.idref.IdReferenceHandlerSet;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;

public class JsonTokenValidatorTester {
	private static final long seed = 1234567890L;
	private static final Random rnd = new Random(seed);

	public static void main(String[] args) throws Exception {
		// point the type definition db to point there
		TypeStorage storage = new MongoTypeStorage(TypeRegisteringTest.createMongoDbConnection());
		storage.removeAllData();
		TypeDefinitionDB db = new TypeDefinitionDB(storage);
		// create a validator that uses the type def db
		String username = "wstester1";
		String moduleName = "KBaseNetworks";
		String typeName = "Network";
		String kbSpec = "module " + moduleName + " {typedef structure {string val;} " + typeName + ";};";
		db.requestModuleRegistration(moduleName, username);
		db.approveModuleRegistrationRequest(username, moduleName, true);
		db.registerModule(kbSpec, Arrays.asList(typeName), username);
		db.releaseModule(moduleName, username, false);
		File f = new File("temp_files/temp_validation.json");
		for (int n = 0; n < 1000; n++) {
			JsonGenerator jgen = new ObjectMapper().getFactory().createGenerator(f, JsonEncoding.UTF8);
			int buffer = 100 + rnd.nextInt(1000000);
			jgen.writeStartObject();
			jgen.writeFieldName("val_");
			jgen.writeString(generateLargeString(rnd, buffer - 1));
			jgen.writeFieldName("val");
			jgen.writeString(generateLargeString(rnd, buffer));
			jgen.writeFieldName("val-");
			jgen.writeString(generateLargeString(rnd, buffer + 1));
			for (int i = 0; i < 1000; i++) {
				int len = buffer + rnd.nextInt(buffer);
				jgen.writeFieldName("\"val" + i);
				jgen.writeString("test\"" + i + ((i % 10) == 9 ? generateLargeString(rnd, len) : ""));
			}
			jgen.close();
			long time = System.currentTimeMillis();
			JsonTokenStream jp = new JsonTokenStream(f);  //, buffer);
			IdReferenceHandlerSetFactory fac =
					new IdReferenceHandlerSetFactory(6);
			IdReferenceHandlerSet<String> han = fac.createHandlers(String.class);
			han.associateObject("foo");
			ValidatedTypedObject report = new TypedObjectValidator(
					new LocalTypeProvider(db)).validate(new UObject(jp, null), 
					new TypeDefId(new TypeDefName(moduleName, typeName)), han);
			assertThat(report.isInstanceValid(), is(true));
			System.out.println(buffer + "\t" + f.length() + "\t" + (System.currentTimeMillis() - time) + " ms");
			jp.setRoot(null);
			File f2 = new File("temp_files/temp_validation2.json");
			jp.writeJson(f2);
			compareFiles(f, f2, false);
		}
	}

	public static void compareFiles(File f1, File f2, boolean debug) throws IOException {
		Reader r1 = new FileReader(f1);
		Reader r2 = new FileReader(f2);
		compareReaders(r1, r2, debug);
		r1.close();
		r2.close();
	}
	
	private static void compareReaders(Reader r1, Reader r2, boolean debug) throws IOException {
		int bufSize = 1000;
		char[] buf1 = new char[bufSize];
		char[] buf2 = new char[bufSize];
		long size = 0;
		while (true) {
			int c1 = readAsMuchAsPossible(r1, buf1);
			int c2 = readAsMuchAsPossible(r2, buf2);
			if (c1 != c2) {
				if (debug) {
					System.out.println(new String(buf1, 0, c1));
					System.out.println(new String(buf2, 0, c2));
				}
				throw new IllegalStateException("Sources have different sizes: " + 
						(c1 == bufSize ? ">" : "") + (size + c1) + ", " + (c2 == bufSize ? ">" : "") + (size + c2));
			}
			for (int i = 0; i < c1; i++)
				if (buf1[i] != buf2[i]) {
					if (debug) {
						System.out.println(new String(buf1, 0, c1));
						System.out.println(new String(buf2, 0, c2));
					}
					throw new IllegalStateException("Sources differ at position " + (size + i));
				}
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
		String largeValue = new String(data);
		return largeValue;
	}
}
