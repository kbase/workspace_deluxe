package us.kbase.typedobj.core.validatorconfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.typedobj.core.validatornew.JsonTokenValidatorTest;

public class SortedKeysJsonFileTest {
	public static void main(String[] args) throws Exception {
		//testForLargeNetwork();
		testLargeMap();
	}
	
	private static void testForLargeNetwork() throws Exception {
		File dir = new File("/Users/rsutormin/Work/2014-01-15_hugeobject");
		File f = new File(dir, "network.json");
		File outFile = new File(dir, "network1.json");
		long time = System.currentTimeMillis();
		OutputStream os = new FileOutputStream(outFile);
		new SortedKeysJsonFile(f).writeIntoStream(os);
		os.close();
		System.out.println("Time1: " + (System.currentTimeMillis() - time));
		byte[] buffer = new byte[(int)f.length()];
		FileInputStream fis = new FileInputStream(f);
		int pos = 0;
		for (;pos < buffer.length;) {
			int len = fis.read(buffer, pos, buffer.length - pos);
			if (len < 0)
				break;
			pos += len;
		}
		fis.close();
		File outFile2 = new File(dir, "network2.json");
		time = System.currentTimeMillis();
		os = new FileOutputStream(outFile2);
		new SortedKeysJsonFile(buffer).writeIntoStream(os);
		os.close();
		System.out.println("Time2: " + (System.currentTimeMillis() - time));
		JsonTokenValidatorTest.compareFiles(outFile, outFile2, true);
	}
	
	private static void testLargeMap() throws Exception {
		int size = 1000000;
		Random rnd = new Random(1234567890L);
		for (int n = 0; n < 5; n++) {
			File dir = new File("temp_files");
			if (!dir.exists())
				dir.mkdir();
			Map<String, String> data = new LinkedHashMap<String, String>();
			for (int i = 0; i < size; i++) {
				int val = rnd.nextInt(size);
				data.put("key" + val, "value" + val);
			}
			File f = new File(dir, "temp_large_map.json");
			new ObjectMapper().writeValue(f, data);
			File outFile2 = new File(dir, "temp_large_map2.json");
			long time = System.currentTimeMillis();
			OutputStream os = new FileOutputStream(outFile2);
			new SortedKeysJsonFile(f).writeIntoStream(os);
			os.close();
			System.out.println(size + "\t" + f.length() + "\t" + (System.currentTimeMillis() - time));
			size *= 2;
		}
	}
}
