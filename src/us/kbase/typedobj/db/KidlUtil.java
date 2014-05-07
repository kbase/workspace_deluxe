package us.kbase.typedobj.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class KidlUtil {

	public static boolean compareJson(Map<?, ?> parse1, Map<?, ?> parse2, String header)
			throws JsonGenerationException, JsonMappingException, IOException,
			Exception {
		boolean ok = true;
		String parse1text = rewriteJson(writeJson(parse1));
		String parse2text = rewriteJson(writeJson(parse2));
		if (!parse1text.equals(parse2text)) {
			ok = false;
        	System.out.println(header + " (original/internal):");
        	System.out.println("--------------------------------------------------------");
        	showDiff(parse1text, parse2text);
        	System.out.println();
		}
		return ok;
	}

	public static boolean compareJsonSchemas(Map<String, Map<String, String>> schemas1,
			Map<String, Map<String, String>> schemas2, String header) throws IOException,
			JsonParseException, JsonMappingException, JsonGenerationException,
			Exception {
		boolean ok = true;
		Assert.assertEquals(schemas1.keySet(), schemas2.keySet());
		for (String moduleName : schemas1.keySet()) {
			Assert.assertEquals(schemas1.get(moduleName).keySet(), schemas2.get(moduleName).keySet());
			for (Map.Entry<String, String> entry : schemas1.get(moduleName).entrySet()) {
				String schema1 = rewriteJson(entry.getValue());
				String schema2 = rewriteJson(schemas2.get(moduleName).get(entry.getKey()));
				if (!schema1.equals(schema2)) {
					ok = false;
					System.out.println(header + " (" + moduleName + "." + entry.getKey() + "):");
					System.out.println("--------------------------------------------------------");
					showDiff(schema1, schema2);
					System.out.println();
					System.out.println("*");
				}
			}
		}
		return ok;
	}

	private static void showDiff(String origText, String newText) throws Exception {
		List<String> origLn = getLines(origText);
		List<String> newLn = getLines(newText);
		int origWidth = 0;
		for (String l : origLn)
			if (origWidth < l.length())
				origWidth = l.length();
		if (origWidth > 100)
			origWidth = 100;
		int maxSize = Math.max(origLn.size(), newLn.size());
		for (int pos = 0; pos < maxSize; pos++) {
			String origL = pos < origLn.size() ? origLn.get(pos) : "";
			String newL = pos < newLn.size() ? newLn.get(pos) : "";
			boolean eq = origL.equals(newL);
			if (origL.length() > origWidth) {
				System.out.println("/" + (eq ? " " : "*") +origL);
				System.out.println("\\" + (eq ? " " : "*") + newL);
			} else {
				String sep = eq ? "   " : " * ";
				char[] gap = new char[origWidth - origL.length()];
				Arrays.fill(gap, ' ');
				System.out.println(origL + new String(gap) + sep + newL);
			}
		}
	}

	/*
	 * Method sorts keys in maps inside JSON.
	 */
	private static String rewriteJson(String schema1) 
			throws IOException, JsonParseException, JsonMappingException, JsonGenerationException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
		TreeNode schemaTree = mapper.readTree(schema1);
		Object schemaMap = mapper.treeToValue(schemaTree, Object.class);
		mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
		return mapper.writeValueAsString(schemaMap);
	}
	
	private static String writeJson(Object obj) 
			throws JsonGenerationException, JsonMappingException, IOException {
		StringWriter sw = new StringWriter();
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
		mapper.writeValue(sw, obj);
		sw.close();
		return sw.toString();
	}
	
	private static List<String> getLines(String text) throws Exception {
		BufferedReader br = new BufferedReader(new StringReader(text));
		List<String> ret = new ArrayList<String>();
		while (true) {
			String l = br.readLine();
			if (l == null)
				break;
			ret.add(l);
		}
		br.close();
		return ret;
	}
}
