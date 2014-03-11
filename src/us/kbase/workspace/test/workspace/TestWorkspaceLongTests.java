package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import junit.framework.Assert;

import org.junit.Test;

import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.lib.WorkspaceSaveObject;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class TestWorkspaceLongTests extends WorkspaceTester {
	
	public TestWorkspaceLongTests(String config, String backend,
			Integer maxMemoryUsePerCall) throws Exception {
		super(config, backend, maxMemoryUsePerCall);
	}

	@Test
	public void saveWithBigData() throws Exception {
//		System.gc();
//		printMem("*** starting saveWithBigData, ran gc ***");
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		
		WorkspaceIdentifier bigdataws = new WorkspaceIdentifier("bigdata");
		ws.createWorkspace(userfoo, bigdataws.getName(), false, null, null);
		File tempFile = ws.getTempFilesManager().generateTempFile("clreq", "json");
		try {
			JsonGenerator jgen = mapper.getFactory().createGenerator(tempFile, JsonEncoding.UTF8);
			jgen.writeStartObject();
			jgen.writeFieldName("subset");
			jgen.writeStartArray();
			for (int i = 0; i < 997008; i++) {
				jgen.writeString(TEXT1000);
			}
			jgen.writeEndArray();
			jgen.writeEndObject();
			jgen.close();
			//printMem("*** created object ***");
			UObject data = new UObject(tempFile);
			ws.saveObjects(userfoo, bigdataws, Arrays.asList( //should work
					new WorkspaceSaveObject(data, SAFE_TYPE1, null, new Provenance(userfoo), false)));
		} finally {
			tempFile.delete();
		}
		//printMem("*** saved object ***");
		/*subdata.add("" + TEXT1000);
		try {
			ws.saveObjects(userfoo, bigdataws, Arrays.asList(
					new WorkspaceSaveObject(data, SAFE_TYPE1, null, new Provenance(userfoo), false)));
			fail("saved too big data");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Object #1 data size 1000000039 exceeds limit of 1000000000"));
		}
		data = null;
		subdata = null;*/
		//System.gc();
		
		//printMem("*** released refs ***");
		
		ByteArrayFileCache newdata = ws.getObjects(userfoo, 
				Arrays.asList(new ObjectIdentifier(bigdataws, 1))).get(0).getDataAsTokens();
//		printMem("*** retrieved object ***");
//		System.gc();
//		printMem("*** ran gc after retrieve ***");
		try {
			UObject array = new UObject(newdata.getUObject(), "subset");
			JsonParser jp = array.getPlacedStream();
			Assert.assertEquals(JsonToken.START_ARRAY, jp.nextToken());
			for (int i = 0; i < 997008; i++) {
				Assert.assertEquals(JsonToken.VALUE_STRING, jp.nextToken());
				assertThat("correct string in subdata", jp.getText(), is(TEXT1000));
			}
			Assert.assertEquals(JsonToken.END_ARRAY, jp.nextToken());
			jp.close();
		} finally {
			newdata.destroy();
		}
//		newdata = null;
//		newsd = null;
//		printMem("*** released refs ***");
//		System.gc();
//		printMem("*** ran gc, exiting saveWithBigMeta ***");
	}
	
	@Test(timeout=60000)
	public void tenKrefs() throws Exception {
		final String specRef =
				"module Test10KRefs {\n" +
					"typedef structure {\n" +
						"float foo;\n" +
						"list<int> bar;\n" +
						"string baz;\n" +
						"mapping <string, mapping<string, int>> map;\n" +
					"} ToRefType;\n" +
					"/* @id ws Test10KRefs.ToRefType */\n" +
					"typedef string reference;\n" +
					"typedef structure {\n" +
						"mapping<reference, string> map;\n" +
					"} FromRefType;\n" + 
				"};\n";
		String mod = "Test10KRefs";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		ws.requestModuleRegistration(userfoo, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(userfoo, specRef,
				Arrays.asList("ToRefType", "FromRefType"), null, null, false, null);
		TypeDefId toRef = new TypeDefId(new TypeDefName(mod, "ToRefType"), 0, 1);
		TypeDefId fromRef = new TypeDefId(new TypeDefName(mod, "FromRefType"), 0, 1);
		
		WorkspaceIdentifier wspace = new WorkspaceIdentifier("tenKrefs");
		WorkspaceInformation wi = ws.createWorkspace(userfoo, wspace.getName(), false, null, null);
		long wsid = wi.getId();
		Provenance emptyprov = new Provenance(userfoo);
		Map<String, Object> torefdata = new HashMap<String, Object>();
		torefdata.put("foo", 3.2);
		torefdata.put("baz", "astring");
		torefdata.put("bar", Arrays.asList(-3, 1, 234567890));
		Map<String, Integer> inner2 = new HashMap<String, Integer>();
		inner2.put("Foo", 3);
		inner2.put("bar", 6);
		inner2.put("baz", 42);
		Map<String, Map<String, Integer>> inner1 = new HashMap<String, Map<String,Integer>>();
		inner1.put("string1", inner2);
		inner1.put("string2", inner2);
		torefdata.put("map", inner1);
		
		List<WorkspaceSaveObject> wsos = new LinkedList<WorkspaceSaveObject>();
		Map<String, Object> refdata = new HashMap<String, Object>();
		Map<String, String> refs = new HashMap<String, String>();
		refdata.put("map", refs);
		
		Set<String> expectedRefs = new HashSet<String>();
		for (int i = 1; i < 10001; i++) {
			wsos.add(new WorkspaceSaveObject(torefdata, toRef, null, emptyprov, false));
			refs.put("tenKrefs/auto" + i, "expected " + i);
			expectedRefs.add(wsid + "/" + i + "/" + 1);
		}
		ws.saveObjects(userfoo, wspace, wsos);
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(refdata, fromRef, null, emptyprov, false)));
		
		WorkspaceObjectData wod = ws.getObjects(userfoo,
				Arrays.asList(new ObjectIdentifier(wspace, "auto10001")))
				.get(0);
		
		@SuppressWarnings("unchecked")
		Map<String, Object> ret = (Map<String, Object>) wod.getData();
		@SuppressWarnings("unchecked")
		Map<String, String> retrefs = (Map<String, String>) ret.get("map");
		for (Entry<String, String> es: retrefs.entrySet()) {
			long expected = Long.parseLong(es.getValue().split(" ")[1]);
			ObjectIdentifier oi = ObjectIdentifier.parseObjectReference(es.getKey());
			assertThat("reference ws is correct", oi.getWorkspaceIdentifier().getId(), is(wsid));
			assertThat("reference id is correct", oi.getId(), is(expected));
			assertThat("reference ver is correct", oi.getVersion(), is(1));
		}
		assertThat("returned refs correct", new HashSet<String>(wod.getReferences()),
				is(expectedRefs));
	}
	
	@Test(timeout=60000)
	public void unicode() throws Exception {
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		
		WorkspaceIdentifier unicode = new WorkspaceIdentifier("unicode");
		ws.createWorkspace(userfoo, unicode.getName(), false, null, null);
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
		ws.saveObjects(userfoo, unicode, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, new Provenance(userfoo), false)));
		@SuppressWarnings("unchecked")
		Map<String, Object> newdata = (Map<String, Object>) ws.getObjects(
				userfoo, Arrays.asList(new ObjectIdentifier(unicode, 1))).get(0).getData();
		assertThat("correct obj keys", newdata.keySet(),
				is((Set<String>) new HashSet<String>(Arrays.asList("subset"))));
		@SuppressWarnings("unchecked")
		List<String> newsd = (List<String>) newdata.get("subset");
		assertThat("correct subdata size", newsd.size(), is(count));
		for (String s: newsd) {
			assertThat("correct string in subdata", s, is(test));
		}
		
		data.clear();
		data.put(test, "foo");
		ws.saveObjects(userfoo, unicode, Arrays.asList(
				new WorkspaceSaveObject(data, SAFE_TYPE1, null, new Provenance(userfoo), false)));
		@SuppressWarnings("unchecked")
		Map<String, Object> newdata2 = (Map<String, Object>) ws.getObjects(
				userfoo, Arrays.asList(new ObjectIdentifier(unicode, 2))).get(0).getData();
		assertThat("unicode key correct", newdata2.keySet(),
				is((Set<String>) new HashSet<String>(Arrays.asList(test))));
		assertThat("value correct", (String) newdata2.get(test), is("foo"));
	}
	
	@Test
	public void listObjectsPagination() throws Exception {
		WorkspaceUser user = new WorkspaceUser("pagUser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("pagination");
		ws.createWorkspace(user, wsi.getName(), false, null, null).getId();
		
		List<WorkspaceSaveObject> objs = new LinkedList<WorkspaceSaveObject>();
		for (int i = 0; i < 20000; i++) {
			objs.add(new WorkspaceSaveObject(new HashMap<String, String>(), SAFE_TYPE1,
				null, new Provenance(user), false));
		}
		ws.saveObjects(user, wsi, objs);
		
		//this depends on the natural sort order of mongo
		checkObjectPagination(user, wsi, -1, 0, 1, 10000);
		checkObjectPagination(user, wsi, -1, 10001, 1, 10000);
		checkObjectPagination(user, wsi, -1, 1000000, 1, 10000);
		checkObjectPagination(user, wsi, -1, 5000, 1, 5000);
		checkObjectPagination(user, wsi, 10000, 5000, 10001, 15000);
		checkObjectPagination(user, wsi, 10000, 10000, 10001, 20000);
		checkObjectPagination(user, wsi, 15000, 10000, 15001, 20000);
		checkObjectPagination(user, wsi, 15000, 1, 15001, 15001);
		checkObjectPagination(user, wsi, 20000, -1, 2, 1); //hack
	}
}
