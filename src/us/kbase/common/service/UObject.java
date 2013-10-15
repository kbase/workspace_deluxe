package us.kbase.common.service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class UObject {
	private Object userObj;
		
	private static ObjectMapper mapper = new ObjectMapper().registerModule(new JacksonTupleModule());
	
	/**
	 * @return instance of UObject created from Jackson tree parsed from JSON text
	 */
	public static UObject fromJsonString(String json) {
		return new UObject(transformStringToJackson(json));
	}
	
	/**
	 * Creates instance of UObject from Jackson tree, POJO, Map, List or scalar.
	 */
	public UObject(Object obj) {
		userObj = obj;
	}

	/**
	 * @return true in case UObject was created from Jackson tree rather 
	 * than from plain maps, lists, scalars and POJOs 
	 */
	public boolean isJsonNode() {
		return userObj instanceof JsonNode;
	}

	/**
	 * @return Jackson tree representation of this object
	 */
	public JsonNode asJsonNode() {
		if (isJsonNode())
			return (JsonNode)userObj;
		return transformObjectToJackson(userObj);
	}
	
	Object getUserObject() {
		return userObj;
	}
	
	/**
	 * @return true in case this object is list of something
	 */
	public boolean isList() {
		if (isJsonNode())
			return asJsonNode().isArray();
		return userObj instanceof List;
	}

	/**
	 * @return list representation of this object
	 */
	public List<UObject> asList() {
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
	
	/**
	 * @return true in case this object is mapping of something
	 */
	public boolean isMap() {
		if (isJsonNode()) {
			return asJsonNode().isObject();
		}
		return userObj instanceof Map;
	}
	
	/**
	 * @return map representation of this object
	 */
	public Map<String, UObject> asMap() {
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
	
	/**
	 * @return true in case this object is integer
	 */
	public boolean isInteger() {
		if (isJsonNode())
			return asJsonNode().isInt();
		return userObj instanceof Integer;
	}

	/**
	 * @return true in case this object is long
	 */
	public boolean isLong() {
		if (isJsonNode())
			return asJsonNode().isLong();
		return userObj instanceof Long;
	}

	/**
	 * @return true in case this object is text
	 */
	public boolean isString() {
		if (isJsonNode())
			return asJsonNode().isTextual();
		return userObj instanceof String;
	}

	/**
	 * @return true in case this object is floating
	 */
	public boolean isDouble() {
		if (isJsonNode())
			return asJsonNode().isDouble();
		return userObj instanceof Double;
	}

	/**
	 * @return true in case this object is boolean
	 */
	public boolean isBoolean() {
		if (isJsonNode())
			return asJsonNode().isBoolean();
		return userObj instanceof Boolean;
	}

	/**
	 * @return true in case this object is null
	 */
	public boolean isNull() {
		if (isJsonNode())
			return asJsonNode().isNull();
		return userObj == null;
	}
	
	/**
	 * @return scalar representation of this object
	 */
	@SuppressWarnings("unchecked")
	public <T> T asScalar() {
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

	/**
	 * @return POJO representation of this object
	 */
	public <T> T asInstance() {
		return asClassInstance(new TypeReference<T>() {});
	}

	/**
	 * @return POJO representation of this object of type retType
	 */
	public <T> T asClassInstance(Class<T> retType) {
		if (isJsonNode())
			return transformJacksonToObject(asJsonNode(), retType);
		return transformObjectToObject(userObj, retType);
	}

	/**
	 * @return POJO representation of this object of type retType
	 */
	public <T> T asClassInstance(TypeReference<T> retType) {
		if (isJsonNode())
			return transformJacksonToObject(asJsonNode(), retType);
		return transformObjectToObject(userObj, retType);
	}

	/**
	 * @return JSON text representation of this object
	 */
	public String toJsonString() {
		if (isJsonNode())
			return transformJacksonToString(asJsonNode());
		return transformObjectToString(getUserObject());
	}

	@Override
	public String toString() {
		return "UObject [userObj=" + (isJsonNode() ? toJsonString() : ("" + userObj)) + "]";
	}
	
	/**
	 * Helper method for transformation POJO into POJO of another type.
	 */
	public static <T> T transformObjectToObject(Object obj, Class<T> retType) {
		//return transformStringToObject(transformObjectToString(obj), retType);
		return transformJacksonToObject(transformObjectToJackson(obj), retType);
	}

	/**
	 * Helper method for transformation JSON text into POJO.
	 */
	public static <T> T transformStringToObject(String json, Class<T> retType) {
		try {
			return mapper.readValue(json, retType);
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * Helper method for transformation POJO into POJO of another type.
	 */
	public static <T> T transformObjectToObject(Object obj, TypeReference<T> retType) {
		//return transformStringToObject(transformObjectToString(obj), retType);
		return transformJacksonToObject(transformObjectToJackson(obj), retType);
	}

	/**
	 * Helper method for transformation JSON text into POJO.
	 */
	public static <T> T transformStringToObject(String json, TypeReference<T> retType) {
		try {
			return mapper.readValue(json, retType);
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * Helper method for transformation Jackson tree into POJO.
	 */
	public static <T> T transformJacksonToObject(JsonNode node, Class<T> retType) {
		try {
			T ret = mapper.readValue(new JsonTreeTraversingParser(node, mapper), retType);
			return ret;
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * Helper method for transformation Jackson tree into POJO.
	 */
	public static <T> T transformJacksonToObject(JsonNode node, TypeReference<T> retType) {
		try {
			T ret = mapper.readValue(new JsonTreeTraversingParser(node, mapper), retType);
			return ret;
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * Helper method for transformation POJO into JSON text.
	 */
	public static String transformObjectToString(Object obj) {
		try {
			StringWriter sw = new StringWriter();
			mapper.writeValue(sw, obj);
			sw.close();
			return sw.toString();
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * Helper method for transformation Jackson tree into JSON text.
	 */
	public static String transformJacksonToString(JsonNode node) {
		try {
			StringWriter sw = new StringWriter();
			JsonGenerator gen = mapper.getFactory().createGenerator(sw);
			mapper.writeTree(gen, node);
			sw.close();
			return sw.toString();
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
	
	/**
	 * Helper method for transformation POJO into Jackson tree.
	 */
	public static JsonNode transformObjectToJackson(Object obj) {
		return mapper.valueToTree(obj);
		//return transformStringToJackson(transformObjectToString(obj));
	}

	/**
	 * Helper method for transformation JSON text into Jackson tree.
	 */
	public static JsonNode transformStringToJackson(String json) {
		try {
			return mapper.readTree(json);
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
