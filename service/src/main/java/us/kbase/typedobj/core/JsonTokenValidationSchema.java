package us.kbase.typedobj.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import us.kbase.common.service.UObject;
import us.kbase.typedobj.exceptions.TypedObjectSchemaException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandlerException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.TooManyIdsException;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.IdReference;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This is main validation algorithm.
 * @author rsutormin
 */
public class JsonTokenValidationSchema {
	enum Type {
		object, array, string, integer, number
	}
	
	private String id;									// For all: id
	//private String description;						// For all: description
	private Type type;									// For all: type
	private String originalType;						// For all: original-type
	private IdRefDescr idReference;						// For scalars and mappings: id-reference
	private JsonNode metadataWs;						// For structures: metadata-ws
	private Map<String, JsonTokenValidationSchema> objectProperties;	// For structures: properties
	private JsonTokenValidationSchema objectAdditionalPropertiesType;	// For mapping value type: additionalProperties
	private boolean objectAdditionalPropertiesBoolean;	// For structures: additionalProperties
	private Map<String, Integer> objectRequired;		// For structures: required
	private JsonTokenValidationSchema arrayItems;						// For list: items (one type for all items)
	private List<JsonTokenValidationSchema> arrayItemList;				// For tuple: items (list of types)
	private Integer arrayMinItems;						// For tuple: minItems
	private Integer arrayMaxItems;						// For tuple: maxItems
	
	private NumberRange numberRange;					// For number: minimum, maximum, exclusiveMinimum, exclusiveMaximum
	private IntRange intRange;							// For integer: minimum, maximum, exclusiveMinimum, exclusiveMaximum

	private static final String VALID_TYPEDEF_NAMES = "valid-typedef-names";
	private static final String ATTRIBUTES = "attributes";
	
	private JsonTokenValidationSchema() {}
	
	@SuppressWarnings("unchecked")
	public static JsonTokenValidationSchema parseJsonSchema(
			final String document) 
			throws TypedObjectSchemaException {
		final Map<String, Object> data;
		try {
			data = new ObjectMapper().readValue(document, Map.class);
		} catch (Exception e) {
			throw new TypedObjectSchemaException(
					"Could not parse type schema document: "
					+ e.getMessage(), e);
		}
		return parseJsonSchema(data);
	}
	
	/**
	 * Method parses json schema of some typed object and constructs structure
	 * containing everything necessary for validation of selected typed object.
	 * @throws BadJsonSchemaDocumentException 
	 */
	@SuppressWarnings("unchecked")
	public static JsonTokenValidationSchema parseJsonSchema(
			final Map<String, Object> data)
			throws TypedObjectSchemaException {
		final JsonTokenValidationSchema ret = new JsonTokenValidationSchema();
		ret.id = (String)data.get("id");
		//ret.description = (String)data.get("description");
		ret.type = Type.valueOf("" + data.get("type"));
		ret.originalType = (String)data.get("original-type");
		if (data.containsKey("id-reference")) {
			final Map<String, Object> idInfo =
					(Map<String, Object>)data.get("id-reference");
			final String idType = (String)idInfo.get("id-type");
			if (idType == null) {
				//could add location at some point, but not important
				//we expect the type compiler not to compile bad schemas
				throw new TypedObjectSchemaException(
						"ID reference in type schema is missing type");
			}
			final List<String> attributes = new LinkedList<String>();
			//for backwards compatibility
			final List<String> typedefs =
					(List<String>)idInfo.get(VALID_TYPEDEF_NAMES);
			if (typedefs != null) {
				attributes.addAll(typedefs);
			}
			final List<String> attribs = (List<String>)idInfo.get(ATTRIBUTES);
			if (attribs != null) {
				attributes.addAll(attribs);
			}
			if (attribs != null && typedefs != null) {
				//could add location at some point, but not important
				//we expect the type compiler not to compile bad schemas
				throw new TypedObjectSchemaException(String.format(
						"ID reference in type schema with %s and %s both set is illegal",
						VALID_TYPEDEF_NAMES, ATTRIBUTES));
			}
			
			ret.idReference = new IdRefDescr(new IdReferenceType(idType),
					attributes);
		}
		if (ret.type == Type.object) {
			if (data.containsKey("metadata-ws"))
				ret.metadataWs = UObject.transformObjectToJackson(data.get("metadata-ws"));
			
			ret.objectProperties = new LinkedHashMap<String, JsonTokenValidationSchema>();
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
				ret.arrayItemList = new ArrayList<JsonTokenValidationSchema>();
				for (Map<String, Object> item : itemList)
					ret.arrayItemList.add(parseJsonSchema(item));
			}
			if (data.get("minItems") != null)
				ret.arrayMinItems = Integer.parseInt("" + data.get("minItems"));
			if (data.get("maxItems") != null)
				ret.arrayMaxItems = Integer.parseInt("" + data.get("maxItems"));
		} else if (ret.type == Type.number) {
			ret.numberRange = new NumberRange(data);
		} else if (ret.type == Type.integer) {
			ret.intRange = new IntRange(data);
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
	 * @throws IdReferenceHandlerException 
	 * @throws TooManyIdsException 
	 */
	public void checkJsonData(final JsonParser jp,
			final JsonTokenValidationListener lst) 
			throws JsonParseException, IOException,
			JsonTokenValidationException, TooManyIdsException {
		checkJsonData(jp, lst, new JsonDocumentLocation());
		jp.close();
	}
	
	private void checkJsonData(final JsonParser jp,
			final JsonTokenValidationListener lst, 
			final JsonDocumentLocation path) 
			throws JsonParseException, IOException,
			JsonTokenValidationException, TooManyIdsException {
		jp.nextToken();
		checkJsonDataWithoutFirst(jp, lst, path);
	}
	
	private void checkJsonDataWithoutFirst(final JsonParser jp,
			final JsonTokenValidationListener lst, 
			final JsonDocumentLocation path) 
			throws JsonParseException, IOException,
			JsonTokenValidationException, TooManyIdsException {
		// This is main recursive validation procedure. The idea is we enter here every time we observe
		// token starting nested block (which could be only mapping or array) or token for basic scalar 
		// values (integer, floating, string, boolean and null). According to structure of nested blocks
		// in observed tokens we trevel by nested nodes in json schema and add nested nodes into output
		// id-reference relabeling tree (only if id referencing annotation is present for branch we 
		// visit now in json schema).
		if (type == Type.object) { 
			// mapping (object) is expected in json schema
			if (metadataWs != null) {
				// metadata ws description is defined for this object/mapping
				lst.addMetadataWsMessage(metadataWs);
			}
			
			try {
				path.addMapStart();
				JsonToken t = jp.getCurrentToken();
				if (t == null || t != JsonToken.START_OBJECT) {
					// we expect mapping (object) but in real data we observe token of different type 
					throw new JsonTokenValidationException(
							generateError(type, t, path, false));
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
						throw new JsonTokenValidationException(
								"Object field name is expected but found, "
								+ t + " at " + path.getFullLocationAsString());
					}
					// name of object field (key of mapping)
					String fieldName = jp.getCurrentName();
					// set current path pointing to this field
					path.replaceLast(fieldName);
					// if this field is required we mark it as visited
					if (objectRequired.containsKey(fieldName)) {
						reqPropUsageCount++;
						reqPropUsage[objectRequired.get(fieldName)] = true;
					}
					// we need to find json-schema node describing value of this field
					JsonTokenValidationSchema childType = objectProperties.get(fieldName);
					if (childType == null) {
						if (!objectAdditionalPropertiesBoolean) {
							if (objectProperties.size() > 0)
								lst.addError("Object field name [" +
										fieldName + "] is not in allowed " +
										"object properties: " +
										objectProperties.keySet() + ", at " +
										path.getFullLocationAsString());
						}
						childType = objectAdditionalPropertiesType;
					}
					if (childType == null) {
						// if we don't have such schema it means we don't need to validate it, just skip it
						skipValue(jp);
					} else {
						// otherwise we execute validation recursively for child json-schema node
						childType.checkJsonData(jp, lst, path);
					}
					// and finally we can add this key (field) as requiring id-reference relabeling in 
					// case there was defined idReference property in json-schema node describing this 
					// object (mapping)
					if (idReference != null) {
						final IdReference<String> ref = new IdReference<String>(
								idReference.idType, fieldName,
								idReference.attributes);
						lst.addStringIdRefMessage(ref, path);
					}
				}
				// check whether all required fields were occured
				if (reqPropUsageCount != reqPropUsage.length) {
					List<String> absentProperties = new ArrayList<String>();
					for (Map.Entry<String, Integer> entry : objectRequired.entrySet())
						if (!reqPropUsage[entry.getValue()])
							absentProperties.add(entry.getKey());
					lst.addError("Object doesn't have required fields : " +
							absentProperties + ", at " +
							path.getLocationOfContainerAsString());
				}
			} finally {
				// shift depth of path by 1 level up (closer to root)
				path.removeLast();
			}
		} else if (type == Type.array) {
			// array (list) is expected in json data based on json schema of selected type
			JsonToken t = jp.getCurrentToken();
			if (t == null || t != JsonToken.START_ARRAY) {
				// but token of some other type is observed
				throw new JsonTokenValidationException(
						generateError(type, t, path, false));
			}
			try {
				path.addArrayStart();
				int itemPos = 0;
				// following flag means that we have more items in real data than we have 
				// described in scheme, we use this flag in order to skip not described 
				// items and jump to next data, because we collect error messages rather 
				// than just throw one of them
				boolean skipAll = false;
				while (true) {
					if (arrayMaxItems != null && itemPos > arrayMaxItems) {
						// too many items in real data comparing to limitation in json schema
						lst.addError("Array contains more than " +
								arrayMaxItems + " items, at " +
								path.getLocationOfContainerAsString());
						skipAll = true;
					}
					t = jp.nextToken();
					if (t == JsonToken.END_ARRAY)
						break;
					// if we are here then we see in real data next item of this array (list)
					// let's increment last path element according to position of this item in array
					path.replaceLast(itemPos);
					JsonTokenValidationSchema childType = arrayItems;
					if ((!skipAll) && childType == null && arrayItemList != null
							&& itemPos < arrayItemList.size()) {
						childType = arrayItemList.get(itemPos);
					}
					if (skipAll || childType == null) {
						// if we have more items than we expect or we didn't specify types for 
						// some of them then we skip real data of these items
						skipValueWithoutFirst(jp);
					} else {
						// otherwise we execute recursive validation for current item
						childType.checkJsonDataWithoutFirst(jp, lst, path);
					}
					itemPos++;
				}
				// check if we have too less items than we define in schema limitations (if any)
				if (arrayMinItems != null && itemPos < arrayMinItems)
					lst.addError("Array contains less than " + arrayMinItems +
							" items, at " + path.getLocationOfContainerAsString());
			} finally {
				// shift depth of path by 1 level up (closer to root)
				path.removeLast();
			}
		} else if (type == Type.string) {
			// string value is expecting
			JsonToken t = jp.getCurrentToken();
			if (t != JsonToken.VALUE_STRING) {	// but found something else
				final boolean isID = idReference != null;
				if (t != JsonToken.VALUE_NULL || isID) { // we allow nulls but not for references
					lst.addError(generateError(type, t, path, isID));
				}
				if (t == JsonToken.START_ARRAY
						|| t == JsonToken.START_OBJECT) {
					skipValueWithoutFirst(jp);
				}
			} else {
				if (idReference != null) {
					// we can add this string value as requiring id-reference relabeling in case 
					// there was defined idReference property in json-schema node describing this 
					// string value
					final IdReference<String> ref = new IdReference<String>(
							idReference.idType, jp.getText(),
							idReference.attributes);
					lst.addStringIdRefMessage(ref, path);
				}
			}
		} else if (type == Type.integer) {
			// integer value is expected
			JsonToken t = jp.getCurrentToken();
			if (t != JsonToken.VALUE_NUMBER_INT) { // but found something else
				
				//if restoring int ids, make sure remapping works in the type validator
//				final boolean isID = idReference != null;
				if (t != JsonToken.VALUE_NULL) { // || isID) { // we allow nulls but not for references
					lst.addError(generateError(type, t, path, false)); // isID));
				}
				if (t == JsonToken.START_ARRAY
						|| t == JsonToken.START_OBJECT) {
					skipValueWithoutFirst(jp);
				}
			} else {
				//range check
				if (intRange != null) {
					intRange.checkValue(jp, lst, path);
				}
//				if (idReference != null) {
//					// we can add this int value as requiring id-reference relabeling in case 
//					// there was defined idReference property in json-schema node describing this 
//					// string value
//					final IdReference<Long> ref = new IdReference<Long>(
//							idReference.idType, jp.getLongValue(),
//							idReference.attributes);
//					lst.addLongIdRefMessage(ref);
//				}
			}
		} else if (type == Type.number) {
			// floating point value is expected, but we accept numbers that appear as integers as well
			JsonToken t = jp.getCurrentToken();
			if ((t != JsonToken.VALUE_NUMBER_FLOAT) &&
				(t != JsonToken.VALUE_NUMBER_INT) &&
				(t != JsonToken.VALUE_NULL)) {   // but found something else
				lst.addError(generateError(type, t, path, false));
				if (t == JsonToken.START_ARRAY
						|| t == JsonToken.START_OBJECT) {
					skipValueWithoutFirst(jp);
				}	
			} else {
				//range check
				if (numberRange != null) {
					numberRange.checkValue(jp, lst, path);
				}
			}
		} else {
			lst.addError("Unsupported node type: " + type + " at " +
					path.getFullLocationAsString());
		}
	}
	
	private static String generateError(
			final Type expectedType,
			final JsonToken actualToken,
			final JsonDocumentLocation path,
			final boolean isID) {
		String expected = expectedType == 
				Type.number ? "float" : expectedType.toString();
		String actual = tokenToType(actualToken);
		if (isID) {
			return "instance type (" + actual + ") not allowed for ID reference " +
					"(allowed: [\"" + expected + "\"]), at " +
					path.getFullLocationAsString();
		}
		return "instance type (" + actual + ") does not match any allowed primitive type " +
				"(allowed: [\"" + expected + "\"]), at " +
				path.getFullLocationAsString();
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
	
	public String getOriginalType() {
		return originalType;
	}
	
	public boolean hasIdReference() {
		return idReference != null;
	}
	
	public IdReferenceType getIdReferenceType() {
		return idReference == null ? null : idReference.idType;
	}

	public List<String> getIdReferenceAttributes() {
		return idReference == null ? null : idReference.attributes;
	}

	public Map<String, JsonTokenValidationSchema> getObjectProperties() {
		return objectProperties;
	}
	
	public JsonTokenValidationSchema getObjectAdditionalPropertiesType() {
		return objectAdditionalPropertiesType;
	}
	
	public JsonTokenValidationSchema getChild(final String field) {
		if (field == null) {
			throw new NullPointerException("field cannot be null");
		}
		if (objectAdditionalPropertiesType != null) {
			return objectAdditionalPropertiesType;
		}
		if (objectProperties != null) {
			return objectProperties.get(field);
		}
		throw new IllegalStateException(
				"This schema does not represent a structure or mapping");
	}
	
	public boolean isObjectAdditionalPropertiesBoolean() {
		return objectAdditionalPropertiesBoolean;
	}
	
	public Map<String, Integer> getObjectRequired() {
		return objectRequired;
	}
	
	public JsonTokenValidationSchema getArrayItems() {
		return arrayItems;
	}
	
	public List<JsonTokenValidationSchema> getArrayItemList() {
		return arrayItemList;
	}
	
	public JsonTokenValidationSchema getArraySchema(final int pos) {
		if (arrayItems != null) {
			return arrayItems;
		}
		if (arrayItemList != null) {
			 //just let the List implementation do bounds checking
			return arrayItemList.get(pos);
		}
		throw new IllegalStateException(
				"This schema does not represent an array or tuple");
	}
	
	public Integer getArrayMinItems() {
		return arrayMinItems;
	}
	
	public Integer getArrayMaxItems() {
		return arrayMaxItems;
	}
	


	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("JsonTokenValidationSchema [id=");
		builder.append(id);
		builder.append(", type=");
		builder.append(type);
		builder.append(", originalType=");
		builder.append(originalType);
		builder.append(", idReference=");
		builder.append(idReference);
		if (objectAdditionalPropertiesType != null) { //mapping
			builder.append(", objectAdditionalPropertiesType=");
			builder.append(objectAdditionalPropertiesType);
		} else if (objectProperties != null) { //it's a structure
			builder.append(", objectAdditionalPropertiesBoolean=");
			builder.append(objectAdditionalPropertiesBoolean);
			builder.append(", objectRequired=");
			builder.append(objectRequired);
			builder.append(", objectProperties=");
			builder.append(objectProperties);
		} else if (arrayItems != null) { //list
			builder.append(", arrayItems=");
			builder.append(arrayItems);
		} else if (arrayItemList != null) { //tuple
			builder.append(", arrayMinItems=");
			builder.append(arrayMinItems);
			builder.append(", arrayMaxItems=");
			builder.append(arrayMaxItems);
			builder.append(", arrayItemList=");
			builder.append(arrayItemList);
		}
		builder.append("]");
		return builder.toString();
	}



	private static class IdRefDescr {
		IdReferenceType idType;
		List<String> attributes;
		public IdRefDescr(IdReferenceType idType, List<String> attributes) {
			this.idType = idType;
			this.attributes = attributes;
		}
		@Override
		public String toString() {
			return "IdRefDescr [idType=" + idType + ", attributes="
					+ attributes + "]";
		}
	}
	
	
	private static abstract class Range {
		protected boolean minValueDefined;
		protected boolean maxValueDefined;
		protected boolean exclusiveMin;
		protected boolean exclusiveMax;
		protected Range() {
			minValueDefined=false;
			maxValueDefined=false;
			exclusiveMin = false;
			exclusiveMax = false;
		}
		abstract void checkValue(JsonParser jp, JsonTokenValidationListener lst, JsonDocumentLocation path) throws JsonTokenValidationException;
	}
	
	private static class NumberRange extends Range {
		double minValue;
		double maxValue;
		
		public NumberRange(Map<String, Object> data) {
			minValue=0; maxValue=0; 
			if(data.get("minimum") != null) {
				minValueDefined = true;
				minValue = Double.parseDouble("" + data.get("minimum"));
				if(data.get("exclusiveMinimum") != null)
					exclusiveMin = true;
				else
					exclusiveMin = false;
			}
			if(data.get("maximum") != null) {
				maxValueDefined = true;
				maxValue = Double.parseDouble("" + data.get("maximum"));
				if(data.get("exclusiveMaximum") != null)
					exclusiveMax = true;
				else
					exclusiveMax = false;
			}
		}

		@Override
		void checkValue(JsonParser jp, JsonTokenValidationListener lst, JsonDocumentLocation path) throws JsonTokenValidationException {
			// do not validate range for null values or non-numeric values (we assume typechecking will catch
			// cases where something was given where a numeric value was expected)
			if(jp.getCurrentToken() == JsonToken.VALUE_NULL || !jp.getCurrentToken().isNumeric()) return;
			// do not validate range if no range was defined
			if(!minValueDefined && !maxValueDefined) return;
			try {
				// first attempt to check range assuming it is a double value
				double value = jp.getDoubleValue();
				if(minValueDefined) {
					if(exclusiveMin) {
						if( !(value>minValue) ) {
							lst.addError("Number value given ("+value+") was less than minimum value accepted ("+minValue+", exclusive) at "+path.getFullLocationAsString());
						}
					} else {
						if( !(value>=minValue) ) {
							lst.addError("Number value given ("+value+") was less than minimum value accepted ("+minValue+", inclusive) at "+path.getFullLocationAsString());
						}
					}
				}
				if(maxValueDefined) {
					if(exclusiveMax) {
						if( !(value<maxValue)) {
							lst.addError("Number value given ("+value+") was more than maximum value accepted ("+maxValue+", exclusive) at "+path.getFullLocationAsString());
						}
					} else {
						if( !(value<=maxValue) ) {
							lst.addError("Number value given ("+value+") was more than maximum value accepted ("+maxValue+", inclusive) at "+path.getFullLocationAsString());
						}
					}
				}
			} catch (IOException e) {
				System.out.println("ERROR !! buffer overflow: " + e.getMessage());
				// if we encountered an exception, then there was probably a buffer overflow, so attempt to use a DecimalValue ??
				//jp.getDecimalValue();
			}
		}
		
		@Override
		public String toString() {
			String s = "";
			if(minValueDefined) {
				if(exclusiveMin) s+="("; else s+="[";
				s+=minValue+",";
			} else s+= "inf,";
			if(maxValueDefined) {
				s+=maxValue;
				if(exclusiveMax) s+=")"; else s+="]";
			} else s+= "inf";
			return s;
		}
	}
	
	private static class IntRange extends Range {
		long minValue;
		long maxValue;
		BigInteger bigMin = null;
		BigInteger bigMax = null;

		public IntRange(Map<String, Object> data) {
			minValue=0; maxValue=0; 
			if(data.get("minimum") != null) {
				minValueDefined = true;
				try {
					minValue = Long.parseLong("" + data.get("minimum"));
				} catch (NumberFormatException e) {
					try {
						bigMin = new BigInteger(data.get("minimum").toString());
					} catch (NumberFormatException e2) {
						BigDecimal d = new BigDecimal(data.get("minimum").toString());
						BigDecimal d2 = d.setScale(0, RoundingMode.CEILING);
						if(d2.compareTo(d)!=0) {
							// we cannot be exclusive if we had to round...
							data.put("exclusiveMinimum",null);
						}
						bigMin = d2.toBigInteger();
					}
				}
				if(data.get("exclusiveMinimum") != null)
					exclusiveMin = true;
				else
					exclusiveMin = false;
			}
			if(data.get("maximum") != null) {
				maxValueDefined = true;
				try {
					maxValue = Long.parseLong("" + data.get("maximum"));
				} catch (NumberFormatException e) {
					try {
						bigMax = new BigInteger(data.get("maximum").toString());
					} catch (NumberFormatException e2) {
						BigDecimal d = new BigDecimal(data.get("maximum").toString());
						BigDecimal d2 = d.setScale(0, RoundingMode.FLOOR);
						if(d2.compareTo(d)!=0) {
							// we cannot be exclusive if we had to round...
							data.put("exclusiveMaximum",null);
						}
						bigMax = d2.toBigInteger();
					}
					
				}
				if(data.get("exclusiveMaximum") != null)
					exclusiveMax = true;
				else
					exclusiveMax = false;
			}
		}

		@Override
		void checkValue(JsonParser jp, JsonTokenValidationListener lst, JsonDocumentLocation path) throws JsonTokenValidationException {
			// do not validate range for null values or non-numeric values (we assume typechecking will catch
			// cases where something was given where a numeric value was expected)
			if(jp.getCurrentToken() == JsonToken.VALUE_NULL || !jp.getCurrentToken().isNumeric()) return;
			// do not validate range if no range was defined
			if(!minValueDefined && !maxValueDefined) return;
			double value=0; String textValue = null; boolean tryAsBigValue = false;
			try {
				// first attempt to check range assuming it is a double value
				value = jp.getLongValue();
				textValue = jp.getText();
			} catch (IOException e) { tryAsBigValue = true; }
			
			if(tryAsBigValue) {
				BigInteger bigValue=null;
				try {
					bigValue = jp.getBigIntegerValue();
				} catch (Exception e) {
					lst.addError("Number value given cannot be parsed as an integer at "+path.getFullLocationAsString());
					return;
				}
				if(minValueDefined) {
					if(exclusiveMin) {
						if(bigMin!=null) {
							if( bigValue.compareTo(bigMin) <= 0 )
								lst.addError("Number value given ("+bigValue+") was less than minimum value accepted ("+bigMin+", exclusive) at "+path.getFullLocationAsString());
						} else {
							if( bigValue.compareTo(BigInteger.valueOf(minValue)) <= 0 )
								lst.addError("Number value given ("+bigValue+") was less than minimum value accepted ("+minValue+", exclusive) at "+path.getFullLocationAsString());
						}
					} else {
						if(bigMin!=null) {
							if( bigValue.compareTo(bigMin) < 0 )
								lst.addError("Number value given ("+bigValue+") was less than minimum value accepted ("+bigMin+", inclusive) at "+path.getFullLocationAsString());
						} else {
							if( bigValue.compareTo(BigInteger.valueOf(minValue)) < 0 )
								lst.addError("Number value given ("+bigValue+") was less than minimum value accepted ("+minValue+", inclusive) at "+path.getFullLocationAsString());
						}
					}
				}
				if(maxValueDefined) {
					if(exclusiveMax) {
						if(bigMax!=null) {
							if( bigValue.compareTo(bigMax) >= 0 )
								lst.addError("Number value given ("+bigValue+") was more than maximum value accepted ("+bigMax+", exclusive) at "+path.getFullLocationAsString());
						} else {
							if( bigValue.compareTo(BigInteger.valueOf(maxValue)) >= 0 )
								lst.addError("Number value given ("+bigValue+") was more than maximum value accepted ("+maxValue+", exclusive) at "+path.getFullLocationAsString());
						}
					} else {
						if(bigMax!=null) {
							if( bigValue.compareTo(bigMax) > 0 )
								lst.addError("Number value given ("+bigValue+") was more than maximum value accepted ("+bigMax+", inclusive) at "+path.getFullLocationAsString());
						} else {
							if( bigValue.compareTo(BigInteger.valueOf(maxValue)) > 0 )
								lst.addError("Number value given ("+bigValue+") was more than maximum value accepted ("+maxValue+", inclusive) at "+path.getFullLocationAsString());
						}
					}
				}
			} else {
				if(minValueDefined) {
					if(exclusiveMin) {
						if(bigMin!=null) {
							// should never fail here because a valid long value should never be less than a min value we had to treat as a BigInt
							if( new BigInteger(textValue).compareTo(bigMin) < 0 )
								lst.addError("Number value given ("+value+") was less than minimum value accepted ("+bigMin+", exclusive) at "+path.getFullLocationAsString());
						} else {
							if( value<=minValue )
								lst.addError("Number value given ("+value+") was less than minimum value accepted ("+minValue+", exclusive) at "+path.getFullLocationAsString());
						}
					} else {
						if(bigMin!=null) {
							// should never fail here because a valid long value should never be less than a min value we had to treat as a BigInt
							if( new BigInteger(textValue).compareTo(bigMin) < 0 )
								lst.addError("Number value given ("+value+") was less than minimum value accepted ("+bigMin+", inclusive) at "+path.getFullLocationAsString());
						} else {
							if( value<minValue )
								lst.addError("Number value given ("+value+") was less than minimum value accepted ("+minValue+", inclusive) at "+path.getFullLocationAsString());
						}
					}
				}
				if(maxValueDefined) {
					if(exclusiveMax) {
						if(bigMax!=null) {
							// should never fail here because a valid long value should never be more than a max value we had to treat as a BigInt
							if( new BigInteger(textValue).compareTo(bigMax) >= 0 )
								lst.addError("Number value given ("+value+") was more than maximum value accepted ("+bigMax+", exclusive) at "+path.getFullLocationAsString());
						} else {
							if( value>=maxValue )
								lst.addError("Number value given ("+value+") was more than maximum value accepted ("+maxValue+", exclusive) at "+path.getFullLocationAsString());
						}
					} else {
						if(bigMax!=null) {
							// should never fail here because a valid long value should never be more than a max value we had to treat as a BigInt
							if( new BigInteger(textValue).compareTo(bigMax) > 0 )
								lst.addError("Number value given ("+value+") was more than maximum value accepted ("+bigMax+", inclusive) at "+path.getFullLocationAsString());
						} else {
							if( value>maxValue )
								lst.addError("Number value given ("+value+") was more than maximum value accepted ("+maxValue+", inclusive) at "+path.getFullLocationAsString());
						}
					}
				}
			}
		}
		
		@Override
		public String toString() {
			String s = "";
			if(minValueDefined) {
				if(exclusiveMin) s+="("; else s+="[";
				s+=minValue+",";
			} else s+= "inf,";
			if(maxValueDefined) {
				s+=maxValue;
				if(exclusiveMax) s+=")"; else s+="]";
			} else s+= "inf";
			return s;
		}
	}
}
