package us.kbase.typedobj.core.validatornew;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import us.kbase.common.service.UObject;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NodeSchema {
	enum Type {
		object, array, string, integer, number
	}
	
	private String id;									// For all: id
	//private String description;						// For all: description
	private Type type;									// For all: type
	//private String originalType;						// For all: original-type
	private JsonNode idReference;						// For scalars and mappings: id-reference
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
	
	@SuppressWarnings("unchecked")
	public static NodeSchema parseJsonSchema(Map<String, Object> data) {
		NodeSchema ret = new NodeSchema();
		ret.id = (String)data.get("id");
		//ret.description = (String)data.get("description");
		ret.type = Type.valueOf("" + data.get("type"));
		//ret.originalType = (String)data.get("original-type");
		if (data.containsKey("id-reference"))
			ret.idReference = UObject.transformObjectToJackson(data.get("id-reference"));
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

	public void checkJsonData(JsonParser jp, ProcessStat stat, JsonTokenValidationListener lst) 
			throws JsonParseException, IOException, JsonTokenValidationException {
		checkJsonData(jp, stat, lst, new ArrayList<String>());
	}
	
	private void checkJsonData(JsonParser jp, ProcessStat stat, JsonTokenValidationListener lst, List<String> path) 
			throws JsonParseException, IOException, JsonTokenValidationException {
		jp.nextToken();
		checkJsonDataWithoutFirst(jp, stat, lst, path);
	}
	
	private void checkJsonDataWithoutFirst(JsonParser jp, ProcessStat stat, JsonTokenValidationListener lst, List<String> path) 
			throws JsonParseException, IOException, JsonTokenValidationException {
		if (type == Type.object) {
			if (searchableWsSubset != null)
				lst.addSearchableWsSubsetMessage(searchableWsSubset);
			path.add("{");
			try {
			if (stat != null)
				stat.objectCount++;
			JsonToken t = jp.getCurrentToken();
			if (t == null || t != JsonToken.START_OBJECT) {
				throw new JsonTokenValidationException("Object start is expected but found " + t);
			}
			boolean[] reqPropUsage = new boolean[objectRequired.size()];
			int reqPropUsageCount = 0;
			while (true) {
				t = jp.nextToken();
				if (t == JsonToken.END_OBJECT) {
					break;
				} else if (t != JsonToken.FIELD_NAME) {
					throw new JsonTokenValidationException("Object field name is expected but found " + t);
				}
				String fieldName = jp.getCurrentName();
				path.set(path.size() - 1, fieldName);
				if (objectRequired.containsKey(fieldName)) {
					reqPropUsageCount++;
					reqPropUsage[objectRequired.get(fieldName)] = true;
				}
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
					skipValue(jp);
				} else {
					childType.checkJsonData(jp, stat, lst, path);
				}
				if (idReference != null) {
					lst.addIdRefMessage(fieldName, idReference, path.subList(0, path.size() - 1), true);
					//System.out.println("NodeSchema: path=" + path + ", id=" + jp.getText());
				}
			}
			if (reqPropUsageCount != reqPropUsage.length) {
				List<String> absentProperties = new ArrayList<String>();
				for (Map.Entry<String, Integer> entry : objectRequired.entrySet())
					if (!reqPropUsage[entry.getValue()])
						absentProperties.add(entry.getKey());
				lst.addError("Object doesn't have required fields : " + absentProperties);
			}
			} finally {
				path.remove(path.size() - 1);
			}
		} else if (type == Type.array) {
			path.add("-1");
			try {
			if (stat != null)
				stat.arrayCount++;
			JsonToken t = jp.getCurrentToken();
			if (t == null || t != JsonToken.START_ARRAY) {
				throw new JsonTokenValidationException("Array start is expected but found " + t);
			}
			int itemPos = 0;
			boolean skipAll = false;
			while (true) {
				if (arrayMaxItems != null && itemPos > arrayMaxItems) {
					lst.addError("Array contains more than " + arrayMaxItems + " items");
					skipAll = true;
				}
				t = jp.nextToken();
				if (t == JsonToken.END_ARRAY)
					break;
				path.set(path.size() - 1, "" + itemPos);
				NodeSchema childType = arrayItems;
				if ((!skipAll) && childType == null && arrayItemList != null && itemPos < arrayItemList.size())
					childType = arrayItemList.get(itemPos);
				if (skipAll || childType == null) {
					skipValueWithoutFirst(jp);
				} else {
					childType.checkJsonDataWithoutFirst(jp, stat, lst, path);
				}
				itemPos++;
			}
			if (arrayMinItems != null && itemPos < arrayMinItems)
				lst.addError("Array contains less than " + arrayMinItems + " items");
			} finally {
				path.remove(path.size() - 1);
			}
		} else if (type == Type.string) {
			if (stat != null)
				stat.stringCount++;
			JsonToken t = jp.getCurrentToken();
			if (t != JsonToken.VALUE_STRING)
				lst.addError("String is expected but found " + t);
			if (idReference != null) {
				lst.addIdRefMessage(jp.getText(), idReference, path, false);
				//System.out.println("NodeSchema: path=" + path + ", id=" + jp.getText());
			}
		} else if (type == Type.integer) {
			if (stat != null)
				stat.integerCount++;
			JsonToken t = jp.getCurrentToken();
			if (t != JsonToken.VALUE_NUMBER_INT)
				lst.addError("Integer is expected but found " + t);
		} else if (type == Type.number) {
			if (stat != null)
				stat.floatCount++;
			JsonToken t = jp.getCurrentToken();
			if (t != JsonToken.VALUE_NUMBER_FLOAT)
				lst.addError("Float is expected but found " + t);
		} else {
			lst.addError("Unsupported node type: " + type);
		}
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
	
	public JsonNode getIdReference() {
		return idReference;
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
	
	public static class ProcessStat {
		public int objectCount = 0;
		public int arrayCount = 0;
		public int stringCount = 0;
		public int integerCount = 0;
		public int floatCount = 0;
		@Override
		public String toString() {
			return "ProcessStat [objectCount=" + objectCount + ", arrayCount="
					+ arrayCount + ", stringCount=" + stringCount
					+ ", integerCount=" + integerCount + ", floatCount="
					+ floatCount + "]";
		}
	}
}
