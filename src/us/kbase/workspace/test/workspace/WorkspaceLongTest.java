package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import org.junit.Ignore;
import org.junit.Test;

import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.ObjIDWithRefPathAndSubset;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class WorkspaceLongTest extends WorkspaceTester {
	
	public WorkspaceLongTest(String config, String backend,
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
			JsonGenerator jgen = MAPPER.getFactory().createGenerator(tempFile, JsonEncoding.UTF8);
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
					new WorkspaceSaveObject(getRandomName(), data, SAFE_TYPE1, null,
							new Provenance(userfoo), false)), getIdFactory());
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
				Arrays.asList(new ObjectIdentifier(bigdataws, 1))).get(0).getSerializedData();
//		printMem("*** retrieved object ***");
//		System.gc();
//		printMem("*** ran gc after retrieve ***");
		try {
			UObject array = new UObject(newdata.getUObject(), "subset");
			JsonParser jp = array.getPlacedStream();
			assertThat(jp.nextToken(), is(JsonToken.START_ARRAY));
			for (int i = 0; i < 997008; i++) {
				assertThat(jp.nextToken(), is(JsonToken.VALUE_STRING));
				assertThat("correct string in subdata", jp.getText(), is(TEXT1000));
			}
			assertThat(jp.nextToken(), is(JsonToken.END_ARRAY));
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
	
	@Test(timeout=120000)
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
		types.requestModuleRegistration(userfoo, mod);
		types.resolveModuleRegistration(mod, true);
		types.compileNewTypeSpec(userfoo, specRef,
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
			wsos.add(new WorkspaceSaveObject(new ObjectIDNoWSNoVer("auto" + i), torefdata,
					toRef, null, emptyprov, false));
			refs.put("tenKrefs/auto" + i, "expected " + i);
			expectedRefs.add(wsid + "/" + i + "/" + 1);
		}
		ws.saveObjects(userfoo, wspace, wsos, getIdFactory());
		ws.saveObjects(userfoo, wspace, Arrays.asList(
				new WorkspaceSaveObject(new ObjectIDNoWSNoVer("last"), refdata, fromRef, null,
						emptyprov, false)),
				getIdFactory());
		
		WorkspaceObjectData wod = ws.getObjects(userfoo,
				Arrays.asList(new ObjectIdentifier(wspace, "last")))
				.get(0);
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> ret = (Map<String, Object>) getData(wod);
			@SuppressWarnings("unchecked")
			Map<String, String> retrefs = (Map<String, String>) ret.get("map");
			for (Entry<String, String> es: retrefs.entrySet()) {
				long expected = Long.parseLong(es.getValue().split(" ")[1]);
				ObjectIdentifier oi = ObjectIdentifier.parseObjectReference(es.getKey());
				assertThat("reference ws is correct", oi.getWorkspaceIdentifier().getId(), is(wsid));
				assertThat("reference id is correct", oi.getId(), is(expected));
				assertThat("reference ver is correct", oi.getVersion(), is(1));
			}
		} finally {
			destroyGetObjectsResources(Arrays.asList(wod));
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
				new WorkspaceSaveObject(getRandomName(), data, SAFE_TYPE1, null,
						new Provenance(userfoo), false)), getIdFactory());
		final List<WorkspaceObjectData> objects = ws.getObjects(
				userfoo, Arrays.asList(new ObjectIdentifier(unicode, 1)));
		final Map<String, Object> newdata;
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> newdatatmp = (Map<String, Object>) getData(objects.get(0));
			newdata = newdatatmp;
		} finally {
			destroyGetObjectsResources(objects);
		}
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
				new WorkspaceSaveObject(getRandomName(), data, SAFE_TYPE1, null,
						new Provenance(userfoo), false)), getIdFactory());
		final List<WorkspaceObjectData> objects2 = ws.getObjects(
				userfoo, Arrays.asList(new ObjectIdentifier(unicode, 2)));
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> newdata2 = (Map<String, Object>) getData(objects2.get(0));
			assertThat("unicode key correct", newdata2.keySet(),
					is((Set<String>) new HashSet<String>(Arrays.asList(test))));
			assertThat("value correct", (String) newdata2.get(test), is("foo"));
		} finally {
			destroyGetObjectsResources(objects2);
		}
	}
	
	@Test
	public void listObjectsLimit() throws Exception {
		WorkspaceUser user = new WorkspaceUser("pagUser");
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("pagination");
		ws.createWorkspace(user, wsi.getName(), false, null, null).getId();
		
		List<WorkspaceSaveObject> objs = new LinkedList<WorkspaceSaveObject>();
		for (int i = 0; i < 20000; i++) {
			objs.add(new WorkspaceSaveObject(getRandomName(), new HashMap<String, String>(),
					SAFE_TYPE1, null, new Provenance(user), false));
		}
		ws.saveObjects(user, wsi, objs, getIdFactory());
		
		//this depends on the natural sort order of mongo
		checkObjectLimit(user, wsi, 0, 1, 10000);
		checkObjectLimit(user, wsi, 1, 1, 1);
		checkObjectLimit(user, wsi, 10000, 1, 10000);
		checkObjectLimit(user, wsi, 10001, 1, 10000);
		checkObjectLimit(user, wsi, 1000000, 1, 10000);
		checkObjectLimit(user, wsi, 5000, 1, 5000);
	}
	
	//this test takes FOREVER and doesn't actually test anything, it's a performance measurement
	@Ignore
	@SuppressWarnings("unchecked")
	@Test
	public void getObjectSubset() throws Exception {
		String mod = "TestGetObjectSubset";
		String typeName = "DomainAnnotation";
		String type2 = "Empty";
		final String specRef =
				"module " + mod + " {\n" +
					"typedef tuple<int start_in_feature,int stop_in_feature,float evalue,\n" +
					"	float bitscore, float domain_coverage> domain_place;\n" +
					"typedef tuple<string feature_id,int feature_start,int feature_stop,int feature_dir,\n" +
					"	mapping<string domain_model_ref,list<domain_place>>> annotation_element;\n" +
					"/* @optional alignments_ref */\n" +
					"typedef structure {\n" +
						"string genome_ref;\n" +
						"mapping<string contig_id, list<annotation_element>> data;\n" +
						"mapping<string contig_id, tuple<int size,int features>> contig_to_size_and_feature_count;\n" +
						"mapping<string feature_id, tuple<string contig_id,int feature_index>> feature_to_contig_and_index;\n" +
						"string alignments_ref\n;" +
					"} " + typeName + ";\n" +
					"/* @optional val */\n" +
					"typedef structure {\n" +
						"string val;" +
					"} " + type2 + ";\n" +
				"};\n";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		types.requestModuleRegistration(userfoo, mod);
		types.resolveModuleRegistration(mod, true);
		types.compileNewTypeSpec(userfoo, specRef,
				Arrays.asList(typeName, type2), null, null, false, null);
		TypeDefId daType = new TypeDefId(new TypeDefName(mod, typeName), 0, 1);
		TypeDefId emptyType = new TypeDefId(new TypeDefName(mod, type2), 0, 1);
		WorkspaceIdentifier wspace = new WorkspaceIdentifier("testGetObjectSubset");
		ws.createWorkspace(userfoo, wspace.getName(), false, null, null);
		Provenance emptyprov = new Provenance(userfoo);
		InputStream is = new GZIPInputStream(this.getClass().getResourceAsStream("long_test_get_object_subset.json.gz.properties"));
		Map<String, Object> data = UObject.getMapper().readValue(is, Map.class);
		List<WorkspaceSaveObject> wsos = new LinkedList<WorkspaceSaveObject>();		
		wsos.add(new WorkspaceSaveObject(getRandomName(), data, daType, null, emptyprov, false));
		ObjectInformation oi = ws.saveObjects(userfoo, wspace, wsos, getIdFactory()).get(0);
		/////////////////////////////////////////// get_objects /////////////////////////////////////////////
		String contigId = null;
		int featureCount = -1;
		double avgTime1 = 0;
		int len1 = -1;
		int iterCount1 = 100;
		for (int iter = 0; iter < iterCount1; iter++) {
			long time1 = System.currentTimeMillis();
			WorkspaceObjectData wod1 = ws.getObjects(userfoo,
					Arrays.asList(new ObjectIdentifier(wspace, oi.getObjectId()))).get(0);
			Map<String, Object> ret1 = (Map<String, Object>) getData(wod1);
			String data1 = UObject.getMapper().writeValueAsString(ret1);
			Map<String, Object> contigIdsToFeatures = (Map<String, Object>)ret1.get("data");
			contigId = contigIdsToFeatures.keySet().iterator().next();
			featureCount = ((List<Object>)contigIdsToFeatures.get(contigId)).size();
			time1 = System.currentTimeMillis() - time1;
			avgTime1 += time1;
			len1 = data1.length();
		}
		avgTime1 /= iterCount1;
		System.out.println("[WorkspaceLongTest] get_objects: time=" + avgTime1 + " ms, ret-object-length=" + len1 + " bytes");
		//////////////////////////////////////// get_object_subset //////////////////////////////////////////
		Random rnd = new Random(1234567890123456789L);
		int[] includedPathsNumbers = {1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, featureCount};
		for (int numberOfIncludedPaths : includedPathsNumbers) {
			estimateGetSubsetTime(userfoo, wspace, oi, contigId, featureCount,
					rnd, numberOfIncludedPaths, "get_object_subset");
		}
		//////////////////////////////////////// many_objects //////////////////////////////////////////
		int numberOfIncludedPaths = 20;
		int savedObejcts = 0;
		for (int saveObjIter = 0; saveObjIter < 10; saveObjIter++) {
			int iterCount2 = 1;
			for (int iter = 0; iter < iterCount2; iter++) {
				List<WorkspaceSaveObject> wsos2 = new LinkedList<WorkspaceSaveObject>();
				for (int i = 0; i < 20000; i++) {
					String val = "val" + (iter * 100 + i);
					Map<String, String> emptyData = new TreeMap<String, String>();
					emptyData.put("val", val);
					wsos2.add(new WorkspaceSaveObject(getRandomName(), emptyData, emptyType, null,
							emptyprov, false));
					savedObejcts++;
				}
				ws.saveObjects(userfoo, wspace, wsos2, getIdFactory());
			}
			System.out.println("[WorkspaceLongTest] objects saved: " + savedObejcts);
			estimateGetSubsetTime(userfoo, wspace, oi, contigId, featureCount,
					rnd, numberOfIncludedPaths, "get_object_subset with many objects");
		}		
	}

	private void estimateGetSubsetTime(WorkspaceUser userfoo,
			WorkspaceIdentifier wspace, ObjectInformation oi, String contigId,
			int featureCount, Random rnd, int numberOfIncludedPaths, String caller)
			throws Exception {
		double avgTime2 = 0;
		double avgLen = 0;
		int iterCount2 = 100;
		for (int iter = 0; iter < iterCount2; iter++) {
			List<String> included = new ArrayList<String>();
			for (int i = 0; i < numberOfIncludedPaths; i++)
				included.add("data/" + contigId + "/" + rnd.nextInt(featureCount));
			long time2 = System.currentTimeMillis();
			List<ObjectIdentifier> a = new LinkedList<ObjectIdentifier>();
			a.add(new ObjIDWithRefPathAndSubset(
					new ObjectIdentifier(wspace, oi.getObjectId()), null,
						new SubsetSelection(included)));
			WorkspaceObjectData wod2 = ws.getObjects(userfoo, a).get(0);
			String data2 = UObject.getMapper().writeValueAsString(getData(wod2));
			time2 = System.currentTimeMillis() - time2;
			avgTime2 += time2;
			avgLen += data2.length();
		}
		avgTime2 /= iterCount2;
		avgLen /= iterCount2;
		System.out.println("[WorkspaceLongTest] " + caller + ": time=" + avgTime2 + " ms, " +
				"input-path-size=" + numberOfIncludedPaths + ", ret-object-length=" + avgLen + " bytes");
	}
}
