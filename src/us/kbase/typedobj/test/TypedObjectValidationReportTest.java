package us.kbase.typedobj.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.IdRefNode;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.core.Writable;
import us.kbase.typedobj.exceptions.RelabelIdReferenceException;

public class TypedObjectValidationReportTest {
	
	// needs more tests, just adding tests for new functionality
	
	@Test
	public void errors() throws Exception {
		String json = "{\"z\": \"a\", \"b\": \"d\"}";
		
		List<String> errors = Arrays.asList("foo", "bar", "baz");

		TypedObjectValidationReport tovr = new TypedObjectValidationReport(
				errors, null, null, new UObject(new JsonTokenStream(json)),
				null, null);
		assertThat("correct errors", tovr.getErrorMessages(), is(errors));
		assertThat("errors not empty", tovr.isInstanceValid(), is(false));
		
		tovr = new TypedObjectValidationReport(
				null, null, null, new UObject(new JsonTokenStream(json)),
				null, null);
		assertThat("correct errors", tovr.getErrorMessages(),
				is((List<String>) new LinkedList<String>()));
		assertThat("errors empty", tovr.isInstanceValid(), is(true));
		
		errors = Collections.emptyList();

		tovr = new TypedObjectValidationReport(
				errors, null, null, new UObject(new JsonTokenStream(json)),
				null, null);
		assertThat("correct errors", tovr.getErrorMessages(), is(errors));
		assertThat("errors not empty", tovr.isInstanceValid(), is(true));
		
	}
	
	@Test
	public void writeWithoutSort() throws Exception {
		String json = "{\"c\": 1, \"z\": \"d\"}";
		String expectedJson = "{\"c\":1,\"y\":\"whoop\"}";
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("z", "y");
		refmap.put("d", "whoop");
		IdRefNode root = new IdRefNode(null);
		IdRefNode z = new IdRefNode("z");
		z.setLocationIsID();
		z.setIDAtValue("d");
		root.addChild(z);

		TypedObjectValidationReport tovr = new TypedObjectValidationReport(
				null, null, null, new UObject(new JsonTokenStream(json)),
				root, null);
		tovr.setAbsoluteIdRefMapping(refmap);
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		tovr.createJsonWritable().write(o);
		assertThat("Relabel correctly without sort", o.toString("UTF-8"), is(expectedJson));
		
		
		//sort unnecessarily
		tovr = new TypedObjectValidationReport(
				null, null, null, new UObject(new JsonTokenStream(json)),
				root, null);
		tovr.setAbsoluteIdRefMapping(refmap);
		tovr.sort();
		o = new ByteArrayOutputStream();
		tovr.createJsonWritable().write(o);
		assertThat("Relabel correctly with unecessary sort", o.toString("UTF-8"), is(expectedJson));
	}
	
	@Test
	public void sortWithNoMapping() throws Exception {
		String json = "{\"z\": \"a\", \"b\": \"d\"}";
		String expectedJson = "{\"b\":\"d\",\"z\":\"a\"}";

		TypedObjectValidationReport tovr = new TypedObjectValidationReport(
				null, null, null, new UObject(new JsonTokenStream(json)),
				null, null);
		tovr.sort();
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		tovr.createJsonWritable().write(o);
		assertThat("Relabel correctly without sort", o.toString("UTF-8"), is(expectedJson));
	}
	
	@Test
	public void failWriteWithoutSort() throws Exception {
		String json = "{\"b\": \"a\", \"w\": \"d\"}";
		@SuppressWarnings("unused") //below is just for reference
		String expectedJson = "{\"w\":\"whoop\",\"y\":\"a\"}";
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("b", "y");
		refmap.put("d", "whoop");
		IdRefNode root = new IdRefNode(null);
		IdRefNode b = new IdRefNode("b");
		b.setLocationIsID();
		root.addChild(b);
		IdRefNode w = new IdRefNode("w");
		w.setIDAtValue("d");
		root.addChild(w);
		
		//sort via sort() method in memory
		TypedObjectValidationReport tovr = new TypedObjectValidationReport(
				null, null, null, new UObject(new JsonTokenStream(json)),
				root, null);
		tovr.setAbsoluteIdRefMapping(refmap);
		try {
			tovr.createJsonWritable();
			fail("created a writable on non-naturally sorted data");
		} catch (IllegalStateException ise) {
			assertThat("correct exception message on failing to write",
					ise.getLocalizedMessage(),
					is("You must call sort() prior to creating a Writeable."));
		}
	}
	
	@Test
	public void duplicateKeys() throws Exception {
		String json = "{\"z\": \"a\", \"b\": \"d\"}";
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("z", "b");
		refmap.put("d", "whoop");
		IdRefNode root = new IdRefNode(null);
		IdRefNode z = new IdRefNode("z");
		z.setLocationIsID();
		root.addChild(z);
		IdRefNode b = new IdRefNode("b");
		b.setIDAtValue("d");
		root.addChild(b);
		
		TypedObjectValidationReport tovr = new TypedObjectValidationReport(
				null, null, null, new UObject(new JsonTokenStream(json)),
				root, null);
		
		tovr.setAbsoluteIdRefMapping(refmap);
		try {
			tovr.sort();
			fail("sorting didn't detect duplicate keys");
		} catch (RelabelIdReferenceException r){
			assertThat("correct exception message", r.getLocalizedMessage(),
					is("Duplicated key 'b' was found at /"));
		}
	}
	
	@Test
	public void relabelAndSortInMemAndFile() throws Exception {
		String json = "{\"z\": \"a\", \"b\": \"d\"}";
		String expectedJson = "{\"b\":\"whoop\",\"y\":\"a\"}";
		Map<String, String> refmap = new HashMap<String, String>();
		refmap.put("z", "y");
		refmap.put("d", "whoop");
		IdRefNode root = new IdRefNode(null);
		IdRefNode z = new IdRefNode("z");
		z.setLocationIsID();
		root.addChild(z);
		IdRefNode b = new IdRefNode("b");
		b.setIDAtValue("d");
		root.addChild(b);
		
		//sort via sort() method in memory
		TypedObjectValidationReport tovr = new TypedObjectValidationReport(
				null, null, null, new UObject(new JsonTokenStream(json)),
				root, null);
		assertThat("correct object size", tovr.getRelabeledSize(), is(17L));
		
		tovr.setAbsoluteIdRefMapping(refmap);
		assertThat("correct object size", tovr.getRelabeledSize(), is(21L));
		tovr.sort();
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		tovr.createJsonWritable().write(o);
		assertThat("Relabel and sort in memory correctly", o.toString("UTF-8"), is(expectedJson));
		
		//sort via sort(TFM) method with null TFM, again in memory
		tovr = new TypedObjectValidationReport(
				null, null, null, new UObject(new JsonTokenStream(json)),
				root, null);
		
		tovr.setAbsoluteIdRefMapping(refmap);
		tovr.sort(null);
		o = new ByteArrayOutputStream();
		tovr.createJsonWritable().write(o);
		assertThat("Relabel and sort in memory correctly", o.toString("UTF-8"), is(expectedJson));
		
		//sort via sort(TFM) method with data stored in file
		tovr = new TypedObjectValidationReport(
				null, null, null, new UObject(new JsonTokenStream(json)),
				root, null);

		tovr.setAbsoluteIdRefMapping(refmap);
		TempFilesManager tfm = TempFilesManager.forTests();
		tfm.cleanup();
		assertThat("Temp files manager is empty", tfm.isEmpty(), is(true));
		tovr.sort(tfm);
		assertThat("TFM is no longer empty", tfm.isEmpty(), is(false));
		assertThat("TFM has one file", tfm.getTempFileList().size(), is(1));
		o = new ByteArrayOutputStream();
		Writable w = tovr.createJsonWritable();
		w.write(o);
		assertThat("Relabel and in file correctly", o.toString("UTF-8"), is(expectedJson));
		w.releaseResources();
		assertThat("Temp files manager is empty", tfm.isEmpty(), is(true));
	}
}
