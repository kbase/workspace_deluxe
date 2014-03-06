package us.kbase.typedobj.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import us.kbase.common.service.UObject;
import us.kbase.typedobj.idref.WsIdReference;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This is main validation algorithm.
 * @author rsutormin
 */
public class NodeSchema {
	enum Type {
		object, array, string, integer, number
	}
	
	private String id;									// For all: id
	//private String description;						// For all: description
	private Type type;									// For all: type
	//private String originalType;						// For all: original-type
	private IdRefDescr idReference;						// For scalars and mappings: id-reference
	private JsonNode searchableWsSubset;				// For structures: searchable-ws-subset
	private Map<String, NodeSchema> objectProperties;	// For structures: properties
	private NodeSchema objectAdditionalPropertiesType;	// For mapping value type: additionalProperties
	private boolean objectAdditionalPropertiesBoolean;	// For structures: additionalProperties
	private Map<String, Integer> objectRequired;		// For structures: required
	private NodeSchema arrayItems;						// For list: items (one type for all items)
	private List<NodeSchema> arrayItemList;				// For tuple: items (list of types)
	private Integer arrayMinItems;						// For tuple: minItems
	private Integer arrayMaxItems;						// For tuple: maxItems

	@SuppressWarnings("unchecked")
	public static NodeSchema parseJsonSchema(String document) 
			throws JsonParseException, JsonMappingException, IOException {
		Map<String, Object> data = new ObjectMapper().readValue(document, Map.class);
		return parseJsonSchema(data);
	}
	
	/**
	 * Method parses json schema of some typed object and constructs structure
	 * containing everything necessary for validation of selected typed object.
	 */
	@SuppressWarnings("unchecked")
	public static NodeSchema parseJsonSchema(Map<String, Object> data) {
		NodeSchema ret = new NodeSchema();
		ret.id = (String)data.get("id");
		//ret.description = (String)data.get("description");
		ret.type = Type.valueOf("" + data.get("type"));
		//ret.originalType = (String)data.get("original-type");
		if (data.containsKey("id-reference")) {
			Map<String, Object> idInfo = (Map<String, Object>)data.get("id-reference");
			String idType = (String)idInfo.get("id-type");           // the id-type must be defined
			String[] validTypeDefNames = null;
			if (idType.equals(WsIdReference.typestring)) {
				List<String> validNames = (List<String>)idInfo.get("valid-typedef-names");
				if (validNames == null) 
					throw new RuntimeException("cannot create WsIdReference; invalid IdReference info; 'valid-typedef-names' field is required");
				validTypeDefNames = validNames.toArray(new String[validNames.size()]);
			}
			ret.idReference = new IdRefDescr(idType, validTypeDefNames);
		}
		if (ret.type == Type.object) {
			if (data.containsKey("searchable-ws-subset"))
				ret.searchableWsSubset = UObject.transformObjectToJackson(data.get("searchable-ws-subset"));
			ret.objectProperties = new LinkedHashMap<String, NodeSchema>();
			Map<String, Object> props = (Map<String, Object>)data.get("properties");
			if (props != null) {
				for (Map.Entry<String, Object> entry : props.entrySet()) {
					String prop = entry.getKey();
					Map<String, Object> propType = (Map<String, Object>)entry.getValue();
					ret.objectProperties.put(prop, parseJsonSchema(propType));
				}
			}
			Object addProps = data.get("additionalProperties");
			if (addProps != null) {
				if (addProps instanceof Map) {
					ret.objectAdditionalPropertiesType = parseJsonSchema((Map<String, Object>)addProps);
					ret.objectAdditionalPropertiesBoolean = true;
				} else {
					ret.objectAdditionalPropertiesBoolean = (Boolean)addProps;
				}
			}
			ret.objectRequired = new LinkedHashMap<String, Integer>();
			List<String> reqList = (List<String>)data.get("required");
			if (reqList != null) {
				for (String reqItem : reqList)
					ret.objectRequired.put(reqItem, ret.objectRequired.size());
			}
		} else if (ret.type == Type.array) {
			Object items = data.get("items");
			if (items instanceof Map) {
				ret.arrayItems = parseJsonSchema((Map<String, Object>)items);
			} else {
				List<Map<String, Object>> itemList = (List<Map<String, Object>>)items;
				ret.arrayItemList = new ArrayList<NodeSchema>();
				for (Map<String, Object> item : itemList)
					ret.arrayItemList.add(parseJsonSchema(item));
			}
			if (data.get("minItems") != null)
				ret.arrayMinItems = Integer.parseInt("" + data.get("minItems"));
			if (data.get("maxItems") != null)
				ret.arrayMaxItems = Integer.parseInt("" + data.get("maxItems"));
		}
		return ret;
	}

	/**
	 * Method validates object described by tokens provided by json parser against 
	 * selected type this schema object was created for.
	 * @param jp json parser providing tokens of validated object
	 * @param stat statistics of token types observed for testing
	 * @param lst callback used for resulting features registration
	 * @param refRoot another result collecting tree schema for id-reference relabeling
	 * @throws JsonParseException
	 * @throws IOException
	 * @throws JsonTokenValidationException
	 */
	public void checkJsonData(JsonParser jp, JsonTokenValidationListener lst, 
			IdRefNode refRoot) 
			throws JsonParseException, IOException, JsonTokenValidationException {
		checkJsonData(jp, lst, new ArrayList<String>(), new ArrayList<IdRefNode>(Arrays.asList(refRoot)));
		jp.close();
	}
	
	private void checkJsonData(JsonParser jp, JsonTokenValidationListener lst, 
			List<String> path, List<IdRefNode> refPath) 
			throws JsonParseException, IOException, JsonTokenValidationException {
		jp.nextToken();
		checkJsonDataWithoutFirst(jp, lst, path, refPath);
	}
	
	private void checkJsonDataWithoutFirst(JsonParser jp, JsonTokenValidationListener lst, 
			List<String> path, List<IdRefNode> refPath) 
			throws JsonParseException, IOException, JsonTokenValidationException {
		// This is main recursive validation procedure. The idea is we enter here every time we observe
		// token starting nested block (which could be only mapping or array) or token for basic scalar 
		// values (integer, floating, string, boolean and null). According to structure of nested blocks
		// in observed tokens we trevel by nested nodes in json schema and add nested nodes into output
		// id-reference relabeling tree (only if id referencing annotation is present for branch we 
		// visit now in json schema).
		if (type == Type.object) { 
			// mapping (object) is expected in json schema
			if (searchableWsSubset != null) {
				// seachable ws-subset description is defined for this object/mapping
				lst.addSearchableWsSubsetMessage(searchableWsSubset);
			}
			// add new level to json path we are visiting now, every field of this object will
			// change this '{' value into name of this field, so exact character ('{') is not 
			// important, we just shift depth of path into deeper level
			path.add("{");
			try {
				JsonToken t = jp.getCurrentToken();
				if (t == null || t != JsonToken.START_OBJECT) {
					// we expect mapping (object) but in real data we observe token of different type 
					throw new JsonTokenValidationException("Object start is expected but found " + t);
				}
				// flags for usage (true) or not usage (false) of fields having positions in this 
				// array coded in objectRequired map
				boolean[] reqPropUsage = new boolean[objectRequired.size()];
				// count of true values in reqPropUsage
				int reqPropUsageCount = 0;
				// in following loop we process all fields of opened object
				while (true) {
					t = jp.nextToken();
					if (t == JsonToken.END_OBJECT) {
						// end of object, go out of loop
						break;
					} else if (t != JsonToken.FIELD_NAME) {
						// every time we here we expect next field since rest of this loop is for 
						// processing of value for this field
						throw new JsonTokenValidationException("Object field name is expected but found " + t);
					}
					// name of object field (key of mapping)
					String fieldName = jp.getCurrentName();
					// set current path pointing to this field
					path.set(path.size() - 1, fieldName);
					// if this field is required we mark it as visited
					if (objectRequired.containsKey(fieldName)) {
						reqPropUsageCount++;
						reqPropUsage[objectRequired.get(fieldName)] = true;
					}
					// we need to find json-schema node describing value of this field
					NodeSchema childType = objectProperties.get(fieldName);
					if (childType == null) {
						if (!objectAdditionalPropertiesBoolean) {
							if (objectProperties.size() > 0)
								lst.addError("Object field name [" + fieldName + "] is not in allowed " +
										"object properties: " + objectProperties.keySet());
						}
						childType = objectAdditionalPropertiesType;
					}
					if (childType == null) {
						// if we don't have such schema it means we don't need to validate it, just skip it
						skipValue(jp);
					} else {
						// otherwise we execute validation recursively for child json-schema node
						childType.checkJsonData(jp, lst, path, refPath);
					}
					// and finally we can add this key (field) as requiring id-reference relabeling in 
					// case there was defined idReference property in json-schema node describing this 
					// object (mapping)
					if (idReference != null) {
						WsIdReference ref = createRef(fieldName, idReference, path.subList(0, path.size() - 1), true);
						if (ref != null) {
							// this line adds id-reference into flat list which will be used to extract resolved 
							// values from workspace db
							lst.addIdRefMessage(ref);
							// this line adds id-reference into tree structure that will be used for actual 
							// relabeling in object tokens based on list of resolved values constructed by workspace
							getIdRefNode(path, refPath).setParentKeyRef(fieldName);
						}
					}
				}
				// check whether all required fields were occured
				if (reqPropUsageCount != reqPropUsage.length) {
					List<String> absentProperties = new ArrayList<String>();
					for (Map.Entry<String, Integer> entry : objectRequired.entrySet())
						if (!reqPropUsage[entry.getValue()])
							absentProperties.add(entry.getKey());
					lst.addError("Object doesn't have required fields : " + absentProperties);
				}
			} finally {
				// shift (if necessary) depth of id-reference related result tree
				while (refPath.size() > path.size()) 
					refPath.remove(refPath.size() - 1);
				// shift depth of path by 1 level up (closer to root)
				path.remove(path.size() - 1);
			}
		} else if (type == Type.array) {
			// array (list) is expected in json data based on json schema of selected type
			JsonToken t = jp.getCurrentToken();
			if (t == null || t != JsonToken.START_ARRAY) {
				// but token of some other type is observed
				throw new JsonTokenValidationException(generateError(type, t, path));
			}
			// add next level in path corresponding to this array, this value should be 
			// incremented every time we jump to next array item
			path.add("-1");
			try {
				int itemPos = 0;
				// following flag means that we have more items in real data than we have 
				// described in scheme, we use this flag in order to skip not described 
				// items and jump to next data, because we collect error messages rather 
				// than just throw one of them
				boolean skipAll = false;
				while (true) {
					if (arrayMaxItems != null && itemPos > arrayMaxItems) {
						// too many items in real data comparing to limitation in json schema
						lst.addError("Array contains more than " + arrayMaxItems + " items");
						skipAll = true;
					}
					t = jp.nextToken();
					if (t == JsonToken.END_ARRAY)
						break;
					// if we are here then we see in real data next item of this array (list)
					// let's increment last path element according to position of this item in array
					path.set(path.size() - 1, "" + itemPos);
					NodeSchema childType = arrayItems;
					if ((!skipAll) && childType == null && arrayItemList != null && itemPos < arrayItemList.size())
						childType = arrayItemList.get(itemPos);
					if (skipAll || childType == null) {
						// if we have more items than we expect or we didn't specify types for 
						// some of them then we skip real data of these items
						skipValueWithoutFirst(jp);
					} else {
						// otherwise we execute recursive validation for current item
						childType.checkJsonDataWithoutFirst(jp, lst, path, refPath);
					}
					itemPos++;
				}
				// check if we have too less items than we define in schema limitations (if any)
				if (arrayMinItems != null && itemPos < arrayMinItems)
					lst.addError("Array contains less than " + arrayMinItems + " items");
			} finally {
				// shift (if necessary) depth of id-reference related result tree
				while (refPath.size() > path.size()) 
					refPath.remove(refPath.size() - 1);
				// shift depth of path by 1 level up (closer to root)
				path.remove(path.size() - 1);
			}
		} else if (type == Type.string) {
			// string value is expecting
			JsonToken t = jp.getCurrentToken();
			if (t != JsonToken.VALUE_STRING)	// but found something else
				lst.addError(generateError(type, t, path));
			if (idReference != null) {
				// we can add this string value as requiring id-reference relabeling in case 
				// there was defined idReference property in json-schema node describing this 
				// string value
				WsIdReference ref = createRef(jp.getText(), idReference, path, false);
				if (ref != null) {
					// this line adds id-reference into flat list which will be used to extract resolved 
					// values from workspace db
					lst.addIdRefMessage(ref);
					getIdRefNode(path, refPath).setScalarValueRef(jp.getText());
				}
			}
		} else if (type == Type.integer) {
			// integer value is expected
			JsonToken t = jp.getCurrentToken();
			if (t != JsonToken.VALUE_NUMBER_INT)	// but found something else
				lst.addError(generateError(type, t, path));
		} else if (type == Type.number) {
			// floating point value is expected
			JsonToken t = jp.getCurrentToken();
			if (t != JsonToken.VALUE_NUMBER_FLOAT)	// but found something else
				lst.addError(generateError(type, t, path));
		} else {
			lst.addError("Unsupported node type: " + type);
		}
	}
	
	private static String generateError(Type expectedType, JsonToken actualToken, List<String> path) {
		String expected = expectedType == Type.number ? "float" : expectedType.toString();
		String actual = tokenToType(actualToken);
		return "instance type ("+actual+") does not match any allowed primitive type " +
				"(allowed: [\""+expected+"\"]), at " + getPathText(path);
	}
	
	private static String tokenToType(JsonToken t) {
		switch (t) {
		case START_OBJECT:
			return "object";
		case END_OBJECT:
			return "object end";
		case START_ARRAY:
			return "array";
		case END_ARRAY:
			return "array end";
		case FIELD_NAME:
			return "object field";
		case VALUE_NUMBER_FLOAT:
			return "float";
		case VALUE_NUMBER_INT:
			return "integer";
		case VALUE_STRING:
			return "string";
		case VALUE_NULL:
			return "null";
		case VALUE_TRUE:
			return "boolean";
		case VALUE_FALSE:
			return "boolean";
		default:
			return t.asString();
		}
	}
	
	private static String getPathText(List<String> path) {
		StringBuilder ret = new StringBuilder();
		for (String part : path)
			ret.append('/').append(part);
		return ret.toString();
	}
	
	private static IdRefNode getIdRefNode(List<String> path, List<IdRefNode> refPath) {
		if (refPath.size() == 0 || refPath.size() > path.size() + 1)
			throw new IllegalStateException("Reference branch path has wrong length: " + refPath.size());
		while (refPath.size() > 1 && !refPath.get(refPath.size() - 1).getLastPathLocation().equals(path.get(refPath.size() - 2))) {
			refPath.remove(refPath.size() - 1);
		}
		while (refPath.size() <= path.size()) {
			int pos = refPath.size() - 1;
			IdRefNode parent = refPath.get(pos);
			String key = path.get(pos);
			IdRefNode child = new IdRefNode(key);
			parent.addChild(key, child);
			refPath.add(child);
		}
		return refPath.get(path.size());
	}
	
	private static WsIdReference createRef(String id, IdRefDescr idInfo, List<String> path, boolean isFieldName) { 
		// construct the IdReference object
		if (idInfo.idType.equals(WsIdReference.typestring)) {
			return new WsIdReference(id, idInfo.validTypeDefNames, isFieldName);
		}
		return null;
	}
	
	private static void skipValue(JsonParser jp) throws JsonParseException, IOException, JsonTokenValidationException {
		jp.nextToken();
		skipValueWithoutFirst(jp);
	}
	
	private static void skipValueWithoutFirst(JsonParser jp) throws JsonParseException, IOException, JsonTokenValidationException {
		JsonToken t = jp.getCurrentToken();
		if (t == JsonToken.START_OBJECT) {
			while (true) {
				t = jp.nextToken();
				if (t == JsonToken.END_OBJECT) {
					break;
				}
				skipValue(jp);
			}
		} else if (t == JsonToken.START_ARRAY) {
			while (true) {
				t = jp.nextToken();
				if (t == JsonToken.END_ARRAY)
					break;
				skipValueWithoutFirst(jp);
			}
		}
	}
	
	public String getId() {
		return id;
	}
	
	public Type getType() {
		return type;
	}
	
	public String getIdReferenceType() {
		return idReference == null ? null : idReference.idType;
	}

	public String[] getIdReferenceValidTypeDefNames() {
		return idReference == null ? null : idReference.validTypeDefNames;
	}

	public JsonNode getSearchableWsSubset() {
		return searchableWsSubset;
	}
	
	public Map<String, NodeSchema> getObjectProperties() {
		return objectProperties;
	}
	
	public NodeSchema getObjectAdditionalPropertiesType() {
		return objectAdditionalPropertiesType;
	}
	
	public boolean isObjectAdditionalPropertiesBoolean() {
		return objectAdditionalPropertiesBoolean;
	}
	
	public Map<String, Integer> getObjectRequired() {
		return objectRequired;
	}
	
	public NodeSchema getArrayItems() {
		return arrayItems;
	}
	
	public List<NodeSchema> getArrayItemList() {
		return arrayItemList;
	}
	
	public Integer getArrayMinItems() {
		return arrayMinItems;
	}
	
	public Integer getArrayMaxItems() {
		return arrayMaxItems;
	}
	
	private static class IdRefDescr {
		String idType;
		String[] validTypeDefNames;
		public IdRefDescr(String idType, String[] validTypeDefNames) {
			this.idType = idType;
			this.validTypeDefNames = validTypeDefNames;
		}
	}
}
