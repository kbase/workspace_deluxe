package us.kbase.common.test.service;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.common.service.JsonTokenStream;

public class TestJsonTokenStream {
	
	private static final Charset utf8 = Charset.forName("UTF-8");
	
	@Test
	public void getSetTrustedWholeJSON() throws Exception {
		String data = "{\"foo\": \"bar\"}";
		JsonTokenStream jts = new JsonTokenStream(data);
		assertThat("default good whole json is false", jts.hasTrustedWholeJson(),
				is(false));
		assertThat("setting trusted json returns this", jts.setTrustedWholeJson(true),
				is(jts));
		assertThat("trusted json set correctly", jts.hasTrustedWholeJson(),
				is(true));
		
		jts.setRoot(null);
		jts.setRoot(new LinkedList<String>());
		assertThat("trusted json still set after setting null/emtpy root",
				jts.hasTrustedWholeJson(), is(true));

		jts.setRoot(Arrays.asList("foo"));
		assertThat("trusted json set to false after setting root inside json",
				jts.hasTrustedWholeJson(), is(false));
		
		try {
			jts.setTrustedWholeJson(true);
			fail("should fail to set trusted json");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Root is inside contained object, cannot set trustedWholeJson to true"));
		}
	}
	
	@Test
	public void getSetCopyBufferSize() throws Exception {
		String data = "{\"foo\": \"bar\"}";
		JsonTokenStream jts = new JsonTokenStream(data);
		assertThat("default copy buffer size", jts.getCopyBufferSize(),
				is(100000));
		assertThat("setting copy buffer size returns this",
				jts.setCopyBufferSize(10), is(jts));
		assertThat("copy buffer size set correctly", jts.getCopyBufferSize(),
				is(10));
		
		try {
			jts.setCopyBufferSize(9);
			fail("should fail to set copy buffer size");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is("Buffer size must be at least 10"));
		}
	}
	
	@Test
	public void emtpyData() throws Exception {
		failInitJTS("", "Data must be at least 1 byte / char");
		failInitJTS(new byte[0], "Data must be at least 1 byte / char");
		File f = File.createTempFile("TestJsonTokenStream-", ".tmp");
		f.deleteOnExit();
		failInitJTS(f, "Data must be at least 1 byte / char");
		//this doesn't work - mapped to ""
//		JsonNode n = new ObjectMapper().valueToTree("");
//		failInitJTS(n, "Data must be at least 1 byte / char");
		failInitJTS(new HashMap<String, String>(),
				"Only String, File, JsonNode, and byte[]s are allowed as input");
	}
	
	private void failInitJTS(Object data, String exception) throws Exception {
		try {
			new JsonTokenStream(data);
			fail("Inited JTS with bad data");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception message", e.getLocalizedMessage(),
					is(exception));
		}
	}
	
	@Test
	public void streamingMiddleData() throws Exception {
		//TODO test with file, jsonnode, string input & large input (set buffer size?)
		//TODO test with arrays, strings, numbers, true, false, null
		//TODO test with jgen wrapping Writer, and the writeJSON methods
		byte[] data = new String("{this: [\"is\", \"a JSON object\"]}")
				.getBytes(utf8);
		JsonTokenStream jts = new JsonTokenStream(data)
				.setTrustedWholeJson(true);
		ByteArrayOutputStream target = new ByteArrayOutputStream();
		JsonGenerator jgen = new ObjectMapper().getFactory()
				.createGenerator(target);
		jgen.writeStartObject();
		jgen.writeFieldName("data");
		jts.writeTokens(jgen);
		jgen.writeEndObject();
		jgen.flush();
		String res = new String(target.toByteArray(), utf8);
		assertThat("Correctly streamed in object", res,
				is("{\"data\":{this: [\"is\", \"a JSON object\"]}}"));
	}
	
	@Test
	public void streamingDataWithUTF8LongChars() throws Exception {
		StringBuilder sb = new StringBuilder();
		//TODO test with file & String &Json Node
		sb.append("[\"");
		//23 ttl bytes in UTF-8
		sb.appendCodePoint(0x10310); //4 byte
		sb.appendCodePoint(0x4A);    //1 byte
		sb.appendCodePoint(0x103B0); //4 byte
		sb.appendCodePoint(0x120);   //2 byte
		sb.appendCodePoint(0x1D120); //4 byte
		sb.appendCodePoint(0x0A90);  //3 byte
		sb.appendCodePoint(0x6A);    //1 byte
		sb.appendCodePoint(0x1D120); //4 byte
		sb.append("\"]");
		byte[] b = sb.toString().getBytes();
		JsonTokenStream jts = new JsonTokenStream(b).setTrustedWholeJson(true)
				.setCopyBufferSize(13);
		ByteArrayOutputStream target = new ByteArrayOutputStream();
		JsonGenerator jgen = new ObjectMapper().getFactory()
				.createGenerator(target);
		jts.writeTokens(jgen);
		jgen.flush();
		String res = new String(target.toByteArray(), utf8);
		assertThat("UTF8 long chars past buffer end processed correctly",
				res, is(sb.toString()));
		
	}
}
