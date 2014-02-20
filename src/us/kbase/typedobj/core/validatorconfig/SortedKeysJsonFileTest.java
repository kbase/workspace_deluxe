package us.kbase.typedobj.core.validatorconfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

import us.kbase.typedobj.core.validatornew.JsonTokenValidatorTest;

public class SortedKeysJsonFileTest {
	public static void main(String[] args) throws Exception {
		testForLargeNetwork();
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
}
