package us.kbase.common.service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class UObject {
	private Object userObj;
		
	private static ObjectMapper mapper = new ObjectMapper().registerModule(new JacksonTupleModule());
	
	public UObject(Object obj) {
		userObj = obj;
	}

	public boolean isJsonNode() {
		return userObj instanceof JsonNode;
	}
	
	public JsonNode asJsonNode() {
		if (isJsonNode())
			return (JsonNode)userObj;
		return transformObjectToJackson(userObj);
	}
	
	public Object getUserObject() {
		return userObj;
	}
	
	public boolean isList() {
		if (isJsonNode())
			return asJsonNode().isArray();
		return userObj instanceof List;
	}
		
	public List<UObject> asList() throws JsonProcessingException {
		List<UObject> ret = new ArrayList<UObject>();
		if (isJsonNode()) {
			JsonNode root = asJsonNode();
			for (int i = 0; i < root.size(); i++)
				ret.add(new UObject(root.get(i)));
		} else {
			@SuppressWarnings("unchecked")
			List<Object> list = (List<Object>)userObj;
			for (Object val : list)
				ret.add(new UObject(val));
		}
		return ret;
	}
	
	public boolean isMap() {
		if (isJsonNode()) {
			return asJsonNode().isObject();
		}
		return userObj instanceof Map;
	}
	
	public Map<String, UObject> asMap() throws JsonProcessingException {
		Map<String, UObject> ret = new LinkedHashMap<String, UObject>();
		if (isJsonNode()) {
			JsonNode root = asJsonNode();
			for (Iterator<String> propIt = root.fieldNames(); propIt.hasNext(); ) {
				String prop = propIt.next();
				ret.put(prop, new UObject(root.get(prop)));
			}
		} else {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>)userObj;
			for (Map.Entry<String, Object> entry : map.entrySet())
				ret.put(entry.getKey(), new UObject(entry.getValue()));
		}
		return ret;
	}
	
	public boolean isInteger() {
		if (isJsonNode())
			return asJsonNode().isInt();
		return userObj instanceof Integer;
	}
	
	public boolean isString() {
		if (isJsonNode())
			return asJsonNode().isTextual();
		return userObj instanceof String;
	}

	public boolean isDouble() {
		if (isJsonNode())
			return asJsonNode().isDouble();
		return userObj instanceof Double;
	}

	public boolean isBoolean() {
		if (isJsonNode())
			return asJsonNode().isBoolean();
		return userObj instanceof Boolean;
	}

	public boolean isNull() {
		if (isJsonNode())
			return asJsonNode().isNull();
		return userObj == null;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T asScalar() throws JsonProcessingException {
		if (isJsonNode()) {
			JsonNode root = asJsonNode();
			Object ret = null;
			if (isBoolean()) {
				ret = root.asBoolean();
			} else if (isDouble()) {
				ret = root.asDouble();
			} else if (isInteger()) {
				ret = root.asInt();
			} else if (isString()) {
				ret = root.asText();
			} else {
				throw new IllegalStateException("Unexpected JsonNode: " + root);
			}
			return (T)ret;
		}
		return (T)userObj;
	}

	public <T> T asInstance() throws JsonProcessingException {
		return asClassInstance(new TypeReference<T>() {});
	}

	public <T> T asClassInstance(Class<T> retType) throws JsonProcessingException {
		if (isJsonNode())
			return transformJacksonToObject(asJsonNode(), retType);
		return transformObjectToObject(userObj, retType);
	}

	public <T> T asClassInstance(TypeReference<T> retType) throws JsonProcessingException {
		if (isJsonNode())
			return transformJacksonToObject(asJsonNode(), retType);
		return transformObjectToObject(userObj, retType);
	}

	public static <T> T transformObjectToObject(Object obj, Class<T> retType) throws JsonProcessingException {
		//return transformJacksonToObject(transformObjectToJackson(obj), retType);
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

	public static <T> T transformObjectToObject(Object obj, TypeReference<T> retType) throws JsonProcessingException {
		//return transformJacksonToObject(transformObjectToJackson(obj), retType);
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

	public static <T> T transformJacksonToObject(JsonNode node, Class<T> retType) throws JsonProcessingException {
		try {
			T ret = mapper.readValue(mapper.treeAsTokens(node), retType);
			return ret;
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static <T> T transformJacksonToObject(JsonNode node, TypeReference<T> retType) throws JsonProcessingException {
		try {
			T ret = mapper.readValue(mapper.treeAsTokens(node), retType);
			return ret;
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
	
	/*public static String transformJacksonToString(JsonNode node) throws JsonProcessingException {
		JsonGenerator gen = mapper.
		mapper.writeTree(jgen, node);
	}*/
	
	public static JsonNode transformObjectToJackson(Object obj) {
		//return mapper.valueToTree(obj);
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
