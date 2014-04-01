package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import junit.framework.Assert;

import org.junit.Test;

import us.kbase.common.service.UObject;
import us.kbase.kidl.KbList;
import us.kbase.kidl.KbMapping;
import us.kbase.kidl.KbScalar;
import us.kbase.kidl.KbTuple;
import us.kbase.kidl.KbTypedef;
import us.kbase.kidl.KbUnspecifiedObject;
import us.kbase.kidl.KbStruct;
import us.kbase.kidl.KbStructItem;
import us.kbase.kidl.KbType;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.RegisterTypespecParams;
import us.kbase.workspace.SaveObjectsParams;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/*
 * These tests are specifically for testing the JSON-RPC communications between
 * the client, up to the invocation of the {@link us.kbase.workspace.workspaces.Workspaces}
 * methods. As such they do not test the full functionality of the Workspaces methods;
 * {@link us.kbase.workspace.test.workspaces.TestWorkspaces} handles that. This means
 * that only one backend (the simplest gridFS backend) is tested here, while TestWorkspaces
 * tests all backends and {@link us.kbase.workspace.database.WorkspaceDatabase} implementations.
 */
public class JSONRPCLayerLongTests extends JSONRPCLayerTester {
	
	private static boolean printMemUsage = false;
	
	@Test
	public void saveBigData() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("bigdata"));
		
		final boolean[] threadStopWrapper1 = {false};
		Thread t1 = null;
		if (printMemUsage) {
//			waitForGC("[JSONRPCLayerTest.saveBigData] Used memory before preparation", 1000000000L);
			System.out.println("----------------------------------------------------------------------");
			t1 = watchForMem("[JSONRPCLayerTest.saveBigData] Used memory during preparation", threadStopWrapper1);
		}
		
		final boolean[] threadStopWrapper2 = {false};
		Thread t2 = null;
		if (printMemUsage) {
			threadStopWrapper1[0] = true;
			t1.join();
			System.out.println("----------------------------------------------------------------------");
			t2 = watchForMem("[JSONRPCLayerTest.saveBigData] Used memory during saveObject", threadStopWrapper2);
		}
		File tempFile = SERVER1.getTempFilesManager().generateTempFile("clreq", "json");
		try {
			JsonGenerator jgen = new ObjectMapper().getFactory().createGenerator(tempFile, JsonEncoding.UTF8);
			jgen.writeStartObject();
			jgen.writeFieldName("subset");
			jgen.writeStartArray();
			for (int i = 0; i < 997008; i++) {
				jgen.writeString(TEXT1000);
			}
			jgen.writeEndArray();
			jgen.writeFieldName("not/sorted");
			jgen.writeString("not \"{ sorted");
			jgen.writeEndObject();
			jgen.close();
			//printMem("*** created object ***");
			UObject data = new UObject(tempFile);
			CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("bigdata")
					.withObjects(Arrays.asList(new ObjectSaveData().withType(SAFE_TYPE)
							.withData(data))));
		} finally {
			tempFile.delete();
		}
		if (printMemUsage) {
			threadStopWrapper2[0] = true;
			t2.join();
		}
				
		final boolean[] threadStopWrapper3 = {false};
		Thread t3 = null;
		if (printMemUsage) {
			System.out.println("----------------------------------------------------------------------");
//			waitForGC("[JSONRPCLayerTest.saveBigData] Used memory before getObject", 1000000000L);
			System.out.println("----------------------------------------------------------------------");
			t3 = watchForMem("[JSONRPCLayerTest.saveBigData] Used memory during getObject", threadStopWrapper3);
		}
		// need 3g to get to this point
		File tempFile2 = SERVER1.getTempFilesManager().generateTempFile("clresp", "json");
		CLIENT1._setFileForNextRpcResponse(tempFile2);
		UObject data = CLIENT1.getObjects(Arrays.asList(new ObjectIdentity().withObjid(1L)
				.withWorkspace("bigdata"))).get(0).getData();
		if (printMemUsage) {
			threadStopWrapper3[0] = true;
			t3.join();
			System.out.println("----------------------------------------------------------------------");
//			waitForGC("[JSONRPCLayerTest.saveBigData] Used memory after getObject", 3000000000L);
		}
		try {
			UObject array = new UObject(data, "subset");
			JsonParser jp = array.getPlacedStream();
			Assert.assertEquals(JsonToken.START_ARRAY, jp.nextToken());
			for (int i = 0; i < 997008; i++) {
				Assert.assertEquals(JsonToken.VALUE_STRING, jp.nextToken());
				assertThat("correct string in subdata", jp.getText(), is(TEXT1000));
			}
			Assert.assertEquals(JsonToken.END_ARRAY, jp.nextToken());
			jp.close();
		} finally {
			tempFile2.delete();
		}
		//need 6g to get past readValueAsTree() in UObjectDeserializer
		/*assertThat("correct obj keys", data.keySet(),
				is((Set<String>) new HashSet<String>(Arrays.asList("subset"))));
		@SuppressWarnings("unchecked")
		List<String> newsd = (List<String>) data.get("subset");
		assertThat("correct subdata size", newsd.size(), is(997008));
		for (String s: newsd) {
			assertThat("correct string in subdata", s, is(TEXT1000));
		}*/
		assertNoTempFilesExist();
	}

	@Test
	public void unicode() throws Exception {
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace("unicode"));
		
		Map<String, Object> data = new HashMap<String, Object>();
		List<String> subdata = new LinkedList<String>();
		StringBuilder sb = new StringBuilder();
		//19 ttl bytes in UTF-8
		sb.appendCodePoint(0x10310);
		sb.appendCodePoint(0x4A);
		sb.appendCodePoint(0x103B0);
		sb.appendCodePoint(0x120);
		sb.appendCodePoint(0x1D120);
		sb.appendCodePoint(0x0A90);
		sb.appendCodePoint(0x6A);
		String test = sb.toString();
		
		int count = 4347900;
		
		data.put("subset", subdata);
		for (int i = 0; i < count; i++) {
			subdata.add(test);
		}
		
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("unicode")
				.withObjects(Arrays.asList(new ObjectSaveData().withType(SAFE_TYPE)
						.withData(new UObject(data)))));
		data = CLIENT1.getObjects(Arrays.asList(new ObjectIdentity().withObjid(1L)
				.withWorkspace("unicode"))).get(0).getData().asInstance();
		
		assertThat("correct obj keys", data.keySet(),
				is((Set<String>) new HashSet<String>(Arrays.asList("subset"))));
		@SuppressWarnings("unchecked")
		List<String> newsd = (List<String>) data.get("subset");
		assertThat("correct subdata size", newsd.size(), is(count));
		int i = 0;
		for (String s: newsd) {
			assertThat(String.format("correct string %s in subdata", i), s, is(test));
			i++;
		}
		
		data.clear();
		data.put(test, "foo");
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace("unicode")
				.withObjects(Arrays.asList(new ObjectSaveData().withType(SAFE_TYPE)
						.withData(new UObject(data)))));
		data = CLIENT1.getObjects(Arrays.asList(new ObjectIdentity().withObjid(2L)
				.withWorkspace("unicode"))).get(0).getData().asInstance();
		
		assertThat("unicode key correct", data.keySet(),
				is((Set<String>) new HashSet<String>(Arrays.asList(test))));
		assertThat("value correct", (String) data.get(test), is("foo"));
		assertNoTempFilesExist();
	}
	
	@Test
	public void randomSpec() throws Exception {
		String moduleName = "TestRandomSpec";
		CLIENT1.requestModuleOwnership(moduleName);
		administerCommand(CLIENT2, "approveModRequest", "module", moduleName);
		String wsName = "randomspec";
		CLIENT1.createWorkspace(new CreateWorkspaceParams().withWorkspace(wsName));
		Random r = new Random(1234567890L);
		Set<String> registeredTypeNames = new TreeSet<String>();
		int maxSize = 0;
		for (int i = 0; i < 500; i++) 
			try {
				int size = registerRandomSpecAndSaveData(r, wsName, moduleName, 3, 
						registeredTypeNames);
				if (maxSize < size)
					maxSize = size;
			} catch (Exception e) {
				System.err.println("Error processing " + i + "th round");
				throw e;
			}
		System.out.println("Max size: " + maxSize);
	}
	
	private static int registerRandomSpecAndSaveData(Random r, String wsName, 
			String moduleName, int typeCount, Set<String> registeredTypeNames) throws Exception {
		String specDoc = "module " + moduleName + " {\n";
		List<ObjectSaveData> saveDataList = new ArrayList<ObjectSaveData>();
		List<byte[]> jsonList = new ArrayList<byte[]>();
		List<ObjectIdentity> objIds = new ArrayList<ObjectIdentity>();
		List<KbTypedef> registeredTypes = new ArrayList<KbTypedef>();
		List<String> newTypeNames = new ArrayList<String>();
		List<Integer> sizes = new ArrayList<Integer>();
		int maxSize = 0;
		for (int i = 0; i < typeCount; i++) {
			String typeName = "type" + i;
			KbType type = generateRandomType(r, registeredTypes, false, 0);
			Object data = generateRandomData(r, type, new int[] {0});
			specDoc += "typedef " + getTypeSpecText(type) + " " + typeName + ";\n";
			String objName = "object" + i;
			objIds.add(new ObjectIdentity().withRef(wsName + "/" + objName));
			saveDataList.add(new ObjectSaveData().withType(moduleName + "." + typeName)
					.withData(new UObject(data)).withName(objName));
			byte[] json = sortJson(new ObjectMapper().writeValueAsBytes(data));
			sizes.add(json.length);
			jsonList.add(json);
			registeredTypes.add(new KbTypedef(moduleName, typeName, type, ""));
			if (!registeredTypeNames.contains(typeName)) {
				newTypeNames.add(typeName);
				registeredTypeNames.add(typeName);
			}
			if (json.length > maxSize)
				maxSize = json.length;
		}
		//System.out.println("Object sizes: " + sizes);
		specDoc += "};";
		//System.out.println("Spec: " + specDoc);
		CLIENT1.registerTypespec(new RegisterTypespecParams().withDryrun(0L).withSpec(specDoc)
				.withNewTypes(newTypeNames));
		CLIENT1.releaseModule(moduleName);
		CLIENT1.saveObjects(new SaveObjectsParams().withWorkspace(wsName).withObjects(saveDataList));
		saveDataList = null;
		List<ObjectData> retList = CLIENT1.getObjects(objIds);
		for (int i = 0; i < retList.size(); i++) {
			ObjectData ret = retList.get(i);
			byte[] retJson = UObject.getMapper().writeValueAsBytes(ret.getData());
			Assert.assertTrue(Arrays.equals(jsonList.get(i), retJson));
		}
		return maxSize;
	}
	
	@SuppressWarnings("unchecked")
	private static byte[] sortJson(byte[] json) throws Exception {
		ObjectMapper SORT_MAPPER = new ObjectMapper();
		SORT_MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
		Map<String, Object> d = SORT_MAPPER.readValue(json, Map.class);
		return SORT_MAPPER.writeValueAsBytes(d);
	}
	
	private static KbType generateRandomType(Random r, List<KbTypedef> registeredTypes, boolean inner, int depth) {
		int typeKind;
		if (inner) {
			while (true) {
				typeKind = depth >=9 ? r.nextInt(8) : r.nextInt(3);	// On 10th level we choose only primitives
				if (typeKind == 5)	// Can not use structure as inner type
					continue;
				if (typeKind == 7) {
					if (registeredTypes.isEmpty())
						continue;
					if (r.nextInt(5) > 0)  // Suppose typedef referencing is rare event
						continue;
				}
				break;
			}
		} else {	// Only structure can be as outer type
			typeKind = 5;
		}
		switch (typeKind) {
		case 0: return new KbScalar("int");
		case 1: return new KbScalar("float");
		case 2: return new KbScalar("string");
		case 3: return new KbList(generateRandomType(r, registeredTypes, true, depth + 1));
		case 4: {
			if (r.nextInt(10) == 0)	// In 10% cases it'll be unspecified object instead of mapping
				return new KbUnspecifiedObject();
			return new KbMapping(new KbScalar("string"), 
					generateRandomType(r, registeredTypes, true, depth + 1));
		}
		case 5: {
			int size = 1 + r.nextInt(5);	// size of structure can be from 1 to 5
			List<KbStructItem> items = new ArrayList<KbStructItem>();
			for (int i = 0; i < size; i++)
				items.add(new KbStructItem(generateRandomType(r, registeredTypes, true, depth + 1), 
						"item" + i));
			return new KbStruct(items);
		}
		case 6: {
			int size = 2 + r.nextInt(4);	// size of tuple can be from 2 to 5
			List<KbType> items = new ArrayList<KbType>();
			for (int i = 0; i < size; i++)
				items.add(generateRandomType(r, registeredTypes, true, depth + 1));
			return new KbTuple(items);
		}
		case 7: {
			return registeredTypes.get(r.nextInt(registeredTypes.size()));
		}
		default: throw new IllegalStateException("Unsupported type code: " + typeKind);
		}
	}
	
	private static Object generateRandomData(Random r, KbType type, int[] largeStringCount) {
		if (type instanceof KbScalar) {
			switch (((KbScalar)type).getScalarType()) {
			case intType:
				return r.nextInt(10) == 0 ? null : r.nextLong();
			case floatType:
				return r.nextInt(10) == 0 ? null : r.nextDouble();
			case stringType: {
				int textLen;
				if (r.nextInt(10) == 0 && largeStringCount[0] < 5) {
					textLen = r.nextInt(20000000);	// length of string is from 0 to 2M in 10% of the cases
					largeStringCount[0]++;
				} else {
					textLen = r.nextInt(1000);	// length of string is from 0 to 999 in 90% cases
				}
				return generateRandomString(r, textLen);
			}
			default:
				throw new IllegalStateException("Unsupported scalar type: " + ((KbScalar)type).getScalarType());
			}
		} else if (type instanceof KbList) {
			int size = r.nextInt(6);	// size of list can be from 0 to 5
			KbType itemType = ((KbList)type).getElementType();
			List<Object> ret = new ArrayList<Object>();
			for (int i = 0; i < size; i++)
				ret.add(generateRandomData(r, itemType, largeStringCount));
			return ret;
		} else if (type instanceof KbMapping) {
			int size = r.nextInt(6);	// size of list can be from 0 to 5
			KbType itemType = ((KbMapping)type).getValueType();
			Map<String, Object> ret = new LinkedHashMap<String, Object>();
			for (int i = 0; i < size; i++)
				ret.put(generateRandomString(r, 100), generateRandomData(r, itemType, largeStringCount));
			return ret;			
		} else if (type instanceof KbStruct) {
			Map<String, Object> ret = new LinkedHashMap<String, Object>();
			for (KbStructItem item : ((KbStruct)type).getItems())
				ret.put(item.getName(), generateRandomData(r, item.getItemType(), largeStringCount));
			return ret;
		} else if (type instanceof KbTuple) {
			List<Object> ret = new ArrayList<Object>();
			for (KbType itemType : ((KbTuple)type).getElementTypes())
				ret.add(generateRandomData(r, itemType, largeStringCount));
			return ret;
		} else if (type instanceof KbUnspecifiedObject) {
			int size = r.nextInt(2);	// size of UObject map can be from 0 to 1
			Map<String, Object> ret = new LinkedHashMap<String, Object>();
			for (int i = 0; i < size; i++)
				ret.put(generateRandomString(r, 100), generateRandomString(r, 1000));
			return ret;			
		} else if (type instanceof KbTypedef) {
			return generateRandomData(r, ((KbTypedef)type).getAliasType(), largeStringCount);
		}
		throw new IllegalStateException("Unsupported type: " + type.getClass().getName());
	}
	
	private static String generateRandomString(Random r, int len) {
		char[] arr = new char[len];
		for (int i = 0; i < len; i++)
			arr[i] = (char)(32 + r.nextInt(127 - 32));
		return new String(arr);
	}

	private static String getTypeSpecText(KbType type) {
		if (type instanceof KbScalar) {
			KbScalar sc = (KbScalar)type;
			return sc.getSpecName();
		} else if (type instanceof KbList) {
			KbList ls = (KbList)type;
			return "list<" + getTypeSpecText(ls.getElementType()) + ">";
		} else if (type instanceof KbMapping) {
			KbMapping mp = (KbMapping)type;
			return "mapping<" + getTypeSpecText(mp.getKeyType()) + ", " + 
					getTypeSpecText(mp.getValueType()) + ">";
		} else if (type instanceof KbTuple) {
			KbTuple tp = (KbTuple)type;
			StringBuilder ret = new StringBuilder();
			for (KbType iType : tp.getElementTypes()) {
				if (ret.length() > 0)
					ret.append(", ");
				ret.append(getTypeSpecText(iType));
			}
			return "tuple<" + ret + ">";
		} else if (type instanceof KbStruct) {
			KbStruct st = (KbStruct)type;
			StringBuilder ret = new StringBuilder("structure {\n");
			for (KbStructItem item : st.getItems()) {
				ret.append("  ").append(getTypeSpecText(item.getItemType()));
				ret.append(" ").append(item.getName()).append(";\n");
			}
			ret.append("}");
			return ret.toString();
		} else if (type instanceof KbUnspecifiedObject) {
			return "UnspecifiedObject";
		} else if (type instanceof KbTypedef) {
			return ((KbTypedef)type).getName();
		}
		return "Unknown type: " + type.getClass().getSimpleName();
	}

}
