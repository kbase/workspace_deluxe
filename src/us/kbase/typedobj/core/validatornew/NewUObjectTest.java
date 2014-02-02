package us.kbase.typedobj.core.validatornew;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class NewUObjectTest {
	
	public static void main(String[] args) throws Exception {
		String text = "[{\"key1\": [1, 2.0, [{\"3\": \"4\"}]]},{\"key2\": {\"key3\": [\"1\", 2, 3.0]}}]";
		JsonTokenStream jts = new JsonTokenStream(text);
		ObjectMapper mapper = createObjectMapperForNewUObject();
		TypeReference<List<Map<String, NewUObject>>> type = new TypeReference<List<Map<String,NewUObject>>>() {};
		List<Map<String, NewUObject>> obj = mapper.readValue(jts, type);
		jts.close();
		System.out.println(obj);
	}

	public static ObjectMapper createObjectMapperForNewUObject() {
		return new ObjectMapper().registerModule(new SimpleModule()
			.addSerializer(NewUObject.class, new JsonSerializer<NewUObject>() {		
				public void serialize(NewUObject value, JsonGenerator jgen, 
						SerializerProvider provider) throws IOException, JsonProcessingException {
					try {
						value.write(jgen);
					} catch (IOException ex) {
						throw ex;
					} catch (Exception ex) {
						throw new IOException(ex);
					}
				}
			})
			.addDeserializer(NewUObject.class, new JsonDeserializer<NewUObject>() {
				public NewUObject deserialize(JsonParser p, DeserializationContext ctx) throws IOException, JsonProcessingException {
					try {
						new Exception("Before NewUObject deserializing").printStackTrace(System.out);
						JsonTokenStream jts = (JsonTokenStream)p;
						String path = jts.getCurrentPath();
						jts.skipChildren();  //Value();
						System.out.println("Before NewUObject deserializing");
						return new NewUObject(jts, path);
					} catch (Exception ex) {
						throw new IOException(ex);
					}
				}
			}));
	}
	
	public static class NewUObject {
		private JsonTokenStream jts;
		private String rootPath;
		private static ObjectCodec mapper = createObjectMapperForNewUObject();
		
		public NewUObject(JsonTokenStream jts, String rootPath) {
			this.jts = jts;
			this.rootPath = rootPath;
		}
		
		public JsonTokenStream getPlacedStream() throws IOException {
			return jts.setRoot(rootPath);
		}
		
		public <T> T getInstance(Class<T> type) throws IOException {
			return mapper.readValue(getPlacedStream(), type);
		}

		public <T> T getInstance(TypeReference<T> type) throws IOException {
			return mapper.readValue(getPlacedStream(), type);
		}
		
		public void write(JsonGenerator jgen) throws IOException {
			getPlacedStream().writeTokens(jgen);
		}
		
		public void write(OutputStream os) throws IOException {
			getPlacedStream().writeJson(os);
		}
		
		public String getRootPath() {
			return rootPath;
		}
		
		@Override
		public String toString() {
			try {
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				write(os);
				return new String(os.toByteArray(), Charset.forName("UTF-8"));
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
	}
}
