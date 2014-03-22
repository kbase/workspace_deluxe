package us.kbase.common.test.service;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.common.service.JsonTokenStream;

public class TestJsonTokenStream {
	
	private static final Charset utf8 = Charset.forName("UTF-8");
	
	@Test
	public void streamingMiddleData() throws Exception {
		//TODO test with file, string input & large input (set buffer size?)
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
	
	@SuppressWarnings("unused")
	private void printBytes(byte[] b) {
		for (int i = 0; i < b.length; i++) {
			System.out.print(b[i] + " ");
		}
		System.out.println();
	}

}
