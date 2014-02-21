package us.kbase.typedobj.core.validatorconfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SortedKeysJsonFileTest {
	public static void main(String[] args) throws Exception {
		testLargeList();
		System.out.println("---------------------------------");
		testLargeMapBuffer();
		System.out.println("---------------------------------");
		testLargeMap();
		System.out.println("---------------------------------");
		testLargeListBuffer();
	}
	
	private static void testLargeMap() throws Exception {
		System.out.println("Large map test (buffer=10k):");
		System.out.println("map_size, file_size, time_ms");
		int baseSize = 1000000;
		Random rnd = new Random(1234567890L);
		int[] sizes = {baseSize, 2 * baseSize, 5 * baseSize, 10 * baseSize};
		File dir = new File("temp_files");
		boolean deleteDir = false;
		if (!dir.exists()) {
			dir.mkdir();
			deleteDir = true;
		}
		for (int size : sizes) {
			File f = new File(dir, "temp_large_map.json");
			writeRandomMapIntoFile(rnd, size, f);
			File f2 = new File(dir, "temp_large_map2.json");
			long time = System.currentTimeMillis();
			OutputStream os = new FileOutputStream(f2);
			new SortedKeysJsonFile(f).setMaxBufferSize(10 * 1024).setSkipKeyDuplication(true).writeIntoStream(os);
			os.close();
			System.out.println(size + ", " + f.length() + ", " + (System.currentTimeMillis() - time));
			f.delete();
			f2.delete();
		}
		if (deleteDir)
			dir.delete();
	}

	private static void testLargeMapBuffer() throws Exception {
		System.out.println("Large map buffer test (map=1M):");
		System.out.println("buffer_size, time_ms");
		int size = 1000000;
		Random rnd = new Random(1234567890L);
		File dir = new File("temp_files");
		boolean deleteDir = false;
		if (!dir.exists()) {
			dir.mkdir();
			deleteDir = true;
		}
		File f = new File(dir, "temp_large_map.json");
		writeRandomMapIntoFile(rnd, size, f);
		int bufSize = 1024;
		for (int n = 0; n <= 10; n++) {
			File f2 = new File(dir, "temp_large_map2.json");
			long time = System.currentTimeMillis();
			OutputStream os = new FileOutputStream(f2);
			new SortedKeysJsonFile(f).setMaxBufferSize(bufSize).setSkipKeyDuplication(true).writeIntoStream(os);
			os.close();
			System.out.println(bufSize + ", " + (System.currentTimeMillis() - time));
			f2.delete();
			bufSize *= 2;
		}
		f.delete();
		if (deleteDir)
			dir.delete();
	}

	private static void testLargeList() throws Exception {
		System.out.println("Large list test (buffer=10k)");
		System.out.println("list_size, file_size, time_ms");
		int baseSize = 1000000;
		Random rnd = new Random(1234567890L);
		int[] sizes = {baseSize, 2 * baseSize, 5 * baseSize, 10 * baseSize};
		File dir = new File("temp_files");
		boolean deleteDir = false;
		if (!dir.exists()) {
			dir.mkdir();
			deleteDir = true;
		}
		for (int size : sizes) {
			File f = new File(dir, "temp_large_list.json");
			writeRandomListIntoFile(rnd, size, f);
			File f2 = new File(dir, "temp_large_list2.json");
			long time = System.currentTimeMillis();
			OutputStream os = new FileOutputStream(f2);
			new SortedKeysJsonFile(f).setMaxBufferSize(10 * 1024).setSkipKeyDuplication(true).writeIntoStream(os);
			os.close();
			System.out.println(size + ", " + f.length() + ", " + (System.currentTimeMillis() - time));
			f.delete();
			f2.delete();
		}
		if (deleteDir)
			dir.delete();
	}

	private static void testLargeListBuffer() throws Exception {
		System.out.println("Large list buffer test (map=1M):");
		System.out.println("buffer_size, time_ms");
		int size = 1000000;
		Random rnd = new Random(1234567890L);
		File dir = new File("temp_files");
		boolean deleteDir = false;
		if (!dir.exists()) {
			dir.mkdir();
			deleteDir = true;
		}
		File f = new File(dir, "temp_large_list.json");
		writeRandomListIntoFile(rnd, size, f);
		int bufSize = 1024;
		for (int n = 0; n <= 10; n++) {
			File f2 = new File(dir, "temp_large_list2.json");
			long time = System.currentTimeMillis();
			OutputStream os = new FileOutputStream(f2);
			new SortedKeysJsonFile(f).setMaxBufferSize(bufSize).setSkipKeyDuplication(true).writeIntoStream(os);
			os.close();
			System.out.println(bufSize + ", " + (System.currentTimeMillis() - time));
			f2.delete();
			bufSize *= 2;
		}
		f.delete();
		if (deleteDir)
			dir.delete();
	}

	private static void writeRandomMapIntoFile(Random rnd, int size, File f)
			throws IOException, JsonGenerationException {
		JsonGenerator jgen = new ObjectMapper().getFactory().createGenerator(f, JsonEncoding.UTF8);
		jgen.writeStartObject();
		for (int i = 0; i < size; i++) {
			int num = rnd.nextInt(size);
			jgen.writeFieldName("key" + num);
			String value = "";
			for (int j = 0; j < 8; j++)
				value += "value" + num;
			jgen.writeString(value);
		}
		jgen.writeEndObject();
		jgen.close();
	}

	private static void writeRandomListIntoFile(Random rnd, int size, File f)
			throws IOException, JsonGenerationException {
		JsonGenerator jgen = new ObjectMapper().getFactory().createGenerator(f, JsonEncoding.UTF8);
		jgen.writeStartArray();
		for (int i = 0; i < size; i++) {
			jgen.writeStartObject();
			int num = rnd.nextInt(size);
			jgen.writeFieldName("key" + num);
			String value = "";
			for (int j = 0; j < 10; j++)
				value += "value" + num;
			jgen.writeString(value);
			jgen.writeFieldName("a");
			jgen.writeString("b");
			jgen.writeEndObject();
		}
		jgen.writeEndArray();
		jgen.close();
	}
}
