package performance;

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
		byte[] data = new String("{this: [\"is\", \"a JSON object\"]}").getBytes(utf8);
		JsonTokenStream jts = new JsonTokenStream(data);
		jts.setTrustedWholeJson(true);
		ByteArrayOutputStream target = new ByteArrayOutputStream();
		JsonGenerator jgen = new ObjectMapper().getFactory().createGenerator(target);
		jgen.writeStartObject();
		jgen.writeFieldName("data");
		jts.writeTokens(jgen);
		jgen.writeEndObject();
		jgen.flush();
		String res = new String(target.toByteArray(), utf8);
		System.out.println(res);
		assertThat("Correctly streamed in object", res, is("{\"data\":{this: [\"is\", \"a JSON object\"]}}"));
	}

}
