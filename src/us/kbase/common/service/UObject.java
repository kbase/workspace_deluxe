package us.kbase.common.service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Class provides ways to deal with unconstrained (untyped) data. In order to
 * be able to work with very large amounts of data there is a way to store this
 * data in file, wrap it by UObject (through JsonTokenStream) and process it as
 * a stream of json tokens (like open/close of maps/arrays, map keys, long/
 * double/text/boolean/null values).
 * @author rsutormin
 */
public class UObject {
	private Object userObj;
	private List<String> tokenStreamRootPath = null;
		
	private static ObjectMapper mapper = new ObjectMapper().registerModule(new JacksonTupleModule());
	
	/**
	 * @return instance of UObject created from Jackson tree parsed from JSON text
	 */
	public static UObject fromJsonString(String json) {
		return new UObject(transformStringToJackson(json));
	}
	
	/**
	 * Creates instance of UObject from File, JsonTokenStream, JsonNode, POJO, Map, List or scalar.
	 */
	public UObject(Object obj) {
		if (obj instanceof File) {
			try {
				userObj = new JsonTokenStream(obj);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		} else {
			userObj = obj;
		}
	}

	/**
	 * Creates instance of UObject from JsonTokenStream with specified root path modifier.
	 */
	public UObject(JsonTokenStream jts, List<String> rootPath) {
		this.userObj = jts;
		this.tokenStreamRootPath = rootPath;
	}

	/**
	 * Creates instance of UObject from another UObject with specified root path modifier.
	 */
	public UObject(UObject parent, String... relativePath) {
		if (!parent.isTokenStream())
			throw new IllegalStateException("Relative path is supported only for token streams");
		this.userObj = parent.userObj;
		List<String> fullPath = parent.tokenStreamRootPath;
		if (relativePath.length > 0) {
			fullPath = new ArrayList<String>();
			if (parent.tokenStreamRootPath != null)
				fullPath.addAll(parent.tokenStreamRootPath);
			fullPath.addAll(Arrays.asList(relativePath));
		}
		this.tokenStreamRootPath = fullPath;
	}

	public static ObjectMapper getMapper() {
		return mapper;
	}

	/**
	 * Setting up internal JsonTokenStream before usage.
	 */
	public JsonTokenStream getPlacedStream() throws IOException {
		if (isTokenStream())
			return ((JsonTokenStream)userObj).setRoot(tokenStreamRootPath);
		return new JsonTokenStream(asJsonNode());
	}
	
	/**
	 * Root path specified in order to shift starting point of working subtree
	 * inside actual data described by internal JsonTokenStream.
	 * @return null in case of UObject created for not JsonTokenStream
	 */
	public List<String> getRootPath() {
		return tokenStreamRootPath;
	}
	
	/**
	 * @return true in case UObject was created from Jackson tree rather 
	 * than from plain maps, lists, scalars, POJOs or JsonTokenStream 
	 */
	public boolean isJsonNode() {
		return userObj instanceof JsonNode;
	}

	/**
	 * @return true in case UObject was created from JsonTokenStream 
	 */
	public boolean isTokenStream() {
		return userObj instanceof JsonTokenStream;
	}
	
	/**
	 * @return Jackson tree representation of this object
	 */
	public JsonNode asJsonNode() {
		if (isJsonNode())
			return (JsonNode)userObj;
		if (isTokenStream()) {
			try {
				return mapper.readTree(getPlacedStream());
			} catch (IOException ex) {
				throw new IllegalStateException(ex);
			} finally {
				close();
			}
		}
		return transformObjectToJackson(userObj);
	}
	
	private void close() {
		try {
			((JsonTokenStream)userObj).close();
		} catch (IOException ignore) {}
	}
	
	Object getUserObject() {
		return userObj;
	}
	
	/**
	 * @return true in case this object is list of something,
	 * for JsonTokenStream it will always be false
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
	 * @return true in case this object is mapping of something,
	 * for JsonTokenStream it will always be false
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
	 * @return true in case this object is integer,
	 * for JsonTokenStream it will always be false
	 */
	public boolean isInteger() {
		if (isJsonNode())
			return asJsonNode().isInt();
		return userObj instanceof Integer;
	}

	/**
	 * @return true in case this object is long,
	 * for JsonTokenStream it will always be false
	 */
	public boolean isLong() {
		if (isJsonNode())
			return asJsonNode().isLong();
		return userObj instanceof Long;
	}

	/**
	 * @return true in case this object is text,
	 * for JsonTokenStream it will always be false
	 */
	public boolean isString() {
		if (isJsonNode())
			return asJsonNode().isTextual();
		return userObj instanceof String;
	}

	/**
	 * @return true in case this object is floating,
	 * for JsonTokenStream it will always be false
	 */
	public boolean isDouble() {
		if (isJsonNode())
			return asJsonNode().isDouble();
		return userObj instanceof Double;
	}

	/**
	 * @return true in case this object is boolean,
	 * for JsonTokenStream it will always be false
	 */
	public boolean isBoolean() {
		if (isJsonNode())
			return asJsonNode().isBoolean();
		return userObj instanceof Boolean;
	}

	/**
	 * @return true in case this object is null,
	 * for JsonTokenStream it will always be false
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
		if (isTokenStream()) {
			try {
				return mapper.readValue(getPlacedStream(), retType);
			} catch (IOException ex) {
				throw new IllegalStateException(ex);
			} finally {
				close();
			}
		}
		return transformObjectToObject(userObj, retType);
	}

	/**
	 * @return POJO representation of this object of type retType
	 */
	public <T> T asClassInstance(TypeReference<T> retType) {
		if (isJsonNode())
			return transformJacksonToObject(asJsonNode(), retType);
		if (isTokenStream()) {
			try {
				return mapper.readValue(getPlacedStream(), retType);
			} catch (IOException ex) {
				throw new IllegalStateException(ex);
			} finally {
				close();
			}
		}
		return transformObjectToObject(userObj, retType);
	}

	/**
	 * Writing inner data into output token stream (generator). Method closes 
	 * inner JsonTokenStream if it's used. So you don't need to worry about it.
	 */
	public void write(JsonGenerator jgen) throws IOException {
		if (isTokenStream()) {
			getPlacedStream().writeTokens(jgen);
		} else {
			mapper.writeValue(jgen, userObj);
		}
	}
	
	/**
	 * Writing inner data into output stream. Method closes inner 
	 * JsonTokenStream if it's used. So you don't need to worry about it.
	 */
	public void write(OutputStream os) throws IOException {
		if (isTokenStream()) {
			getPlacedStream().writeJson(os);
		} else {
			mapper.writeValue(os, userObj);
		}
	}

	/**
	 * Writing inner data into writer. Method closes inner JsonTokenStream
	 * if it's used. So you don't need to worry about it.
	 */
	public void write(Writer w) throws IOException {
		if (isTokenStream()) {
			getPlacedStream().writeJson(w);
		} else {
			mapper.writeValue(w, userObj);
		}
	}

	/**
	 * @return JSON text representation of this object
	 */
	public String toJsonString() {
		if (isJsonNode())
			return transformJacksonToString(asJsonNode());
		if (isTokenStream()) {
			StringWriter sw = new StringWriter();
			try {
				write(sw);
				sw.close();
			} catch (IOException ex) {
				throw new IllegalStateException(ex);
			}
			return sw.toString();
		}
		return transformObjectToString(getUserObject());
	}

	@Override
	public String toString() {
		return "UObject [userObj=" + (isJsonNode() || isTokenStream() ? toJsonString() : ("" + userObj)) + "]";
	}
	
	/**
	 * @return Real size of UObject as if it was stored in byte array.
	 * @throws IOException
	 */
	public long getSizeInBytes() throws IOException {
		final long[] size = {0L};
		OutputStream sizeOs = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				size[0]++;
			}
			@Override
			public void write(byte[] b, int off, int len)
					throws IOException {
				size[0] += len;
			}
		};
		write(sizeOs);
		sizeOs.close();
		return size[0];
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
