package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.Assert;

import org.junit.Test;

import us.kbase.common.service.UObject;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.SaveObjectsParams;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

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
		for (String s: newsd) {
			assertThat("correct string in subdata", s, is(test));
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
}
