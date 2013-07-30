package us.kbase;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

public class UObject {
	private static ObjectMapper mapper = new ObjectMapper().withModule(new JacksonTupleModule());
	
	public static boolean isList(Object obj) {
		return obj instanceof List;
	}
	
	public static <T> List<T> asList(Object obj) {
		return (List<T>)obj;
	}
	
	public static boolean isMap(Object obj) {
		return obj instanceof Map;
	}
	
	public static <T> Map<String, T> asMap(Object obj) {
		return (Map<String, T>)obj;
	}
	
	public static boolean isInteger(Object obj) {
		return obj instanceof Integer;
	}
	
	public static boolean isString(Object obj) {
		return obj instanceof String;
	}

	public static boolean isDouble(Object obj) {
		return obj instanceof Double;
	}

	public static boolean isBoolean(Object obj) {
		return obj instanceof Boolean;
	}

	public static <T> T asScalar(Object obj) {
		return (T)obj;
	}

	public static <T> T transform(Object obj, Class<T> retType) throws JsonProcessingException {
		try {
			StringWriter sw = new StringWriter();
			mapper.writeValue(sw, obj);
			sw.close();
			T ret = mapper.readValue(sw.toString(), retType);
			return ret;
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static <T> T transform(Object obj, TypeReference<T> retType) throws JsonProcessingException {
		try {
			StringWriter sw = new StringWriter();
			mapper.writeValue(sw, obj);
			sw.close();
			T ret = mapper.readValue(sw.toString(), retType);
			return ret;
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
	
	public static JsonNode asJsonTree(Object obj) {
		try {
			StringWriter sw = new StringWriter();
			mapper.writeValue(sw, obj);
			sw.close();
			return mapper.readTree(sw.toString());
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
