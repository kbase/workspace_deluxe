package us.kbase.typedobj.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import us.kbase.common.util.KBaseJsonTreeGenerator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Extraction of ws-searchable subset based on json token stream.
 * @author rsutormin
 */
public class SearchableWsSubsetExtractor {
	private static ObjectMapper mapper = new ObjectMapper();
	
	/**
	 * extract the fields listed in selection from the element and add them to the subset
	 * 
	 * selection must either be an object containing structure field names to extract, '*' in the case of
	 * extracting a mapping, or '[*]' for extracting a list.  if the selection is empty, nothing is added.
	 * If extractKeysOf is set, and the element is an Object (ie a kidl mapping), then an array of the keys
	 * is added instead of the entire mapping.
	 * 
	 * we assume here that selection has already been validated against the structure of the document, so that
	 * if we get true on extractKeysOf, it really is a mapping, and if we get a '*' or '[*]', it really is
	 * a mapping or array.
	 */
	public static JsonNode extractFields(TokenSequenceProvider jts, 
			ObjectNode keysOfSelection, ObjectNode fieldsSelection) throws IOException {
		WsSubsetNode root = new WsSubsetNode();
		//if the selection is empty, we return without adding anything
		if (keysOfSelection != null && keysOfSelection.size() > 0) 
			prepareWsSubsetTree(keysOfSelection, true, root);
		if (fieldsSelection != null && fieldsSelection.size() > 0)
			prepareWsSubsetTree(fieldsSelection, false, root);
		if ((!root.isNeedAll()) && (!root.isNeedKeys()) && (!root.hasChildren()))
			return mapper.createObjectNode();
		JsonToken t = jts.nextToken();
		KBaseJsonTreeGenerator jgen = new KBaseJsonTreeGenerator(mapper);
		extractFieldsWithOpenToken(jts, t, root, jgen);
		jgen.close();
		return jgen.getTree();
	}
	
	private static void prepareWsSubsetTree(JsonNode selection, boolean keysOf, WsSubsetNode parent) {
		if (selection.size() == 0) {
			if (keysOf) {
				parent.setNeedKeys(true);
			} else {
				parent.setNeedAll(true);
			}
		} else {
			Iterator<Map.Entry<String, JsonNode>> it = selection.fields();
			while (it.hasNext()) {
				Map.Entry<String, JsonNode> entry = it.next();
				WsSubsetNode child = null;
				if (parent.getChildren() == null || !parent.getChildren().containsKey(entry.getKey())) {
					child = new WsSubsetNode();
					parent.addChild(entry.getKey(), child);
				} else {
					child = parent.getChildren().get(entry.getKey());
				}
				prepareWsSubsetTree(entry.getValue(), keysOf, child);
			}
		}
	}

	private static void writeTokensFromCurrent(TokenSequenceProvider jts, JsonToken current, 
			JsonGenerator jgen) throws IOException {
		JsonToken t = current;
		writeCurrentToken(jts, t, jgen);
		if (t == JsonToken.START_OBJECT) {
			while (true) {
				t = jts.nextToken();
				writeCurrentToken(jts, t, jgen);
				if (t == JsonToken.END_OBJECT)
					break;
				if (t != JsonToken.FIELD_NAME)
					throw new IllegalStateException("Error parsing json format: " + t.asString());
				t = jts.nextToken();
				writeTokensFromCurrent(jts, t, jgen);
			}
		} else if (t == JsonToken.START_ARRAY) {
			while (true) {
				t = jts.nextToken();
				if (t == JsonToken.END_ARRAY) {
					writeCurrentToken(jts, t, jgen);
					break;
				}
				writeTokensFromCurrent(jts, t, jgen);
			}
		}
	}

	private static JsonToken writeCurrentToken(TokenSequenceProvider jts, JsonToken current, 
			JsonGenerator jgen) throws IOException {
		JsonToken t = current;
		if (t == JsonToken.START_ARRAY) {
			jgen.writeStartArray();
		} else if (t == JsonToken.START_OBJECT) {
			jgen.writeStartObject();
		} else if (t == JsonToken.END_ARRAY) {
			jgen.writeEndArray();
		} else if (t == JsonToken.END_OBJECT) {
			jgen.writeEndObject();
		} else if (t == JsonToken.FIELD_NAME) {
			jgen.writeFieldName(jts.getText());
		} else if (t == JsonToken.VALUE_NUMBER_INT) {
			Number value = jts.getNumberValue();
			if (value instanceof Short) {
				jgen.writeNumber((Short)value);
			} else if (value instanceof Integer) {
				jgen.writeNumber((Integer)value);
			} else if (value instanceof Long) {
				jgen.writeNumber((Long)value);
			} else if (value instanceof BigInteger) {
				jgen.writeNumber((BigInteger)value);
			} else {
				jgen.writeNumber(value.longValue());
			}
		} else if (t == JsonToken.VALUE_NUMBER_FLOAT) {
			Number value = jts.getNumberValue();
			if (value instanceof Float) {
				jgen.writeNumber((Float)value);
			} else if (value instanceof Double) {
				jgen.writeNumber((Double)value);
			} else if (value instanceof BigDecimal) {
				jgen.writeNumber((BigDecimal)value);
			} else {
				jgen.writeNumber(value.doubleValue());
			}
		} else if (t == JsonToken.VALUE_STRING) {
			jgen.writeString(jts.getText());
		} else if (t == JsonToken.VALUE_NULL) {
			jgen.writeNull();
		} else if (t == JsonToken.VALUE_FALSE) {
			jgen.writeBoolean(false);
		} else if (t == JsonToken.VALUE_TRUE) {
			jgen.writeBoolean(true);
		} else {
			throw new IOException("Unexpected token type: " + t);
		}
		return t;
	}

	private static void skipChildren(TokenSequenceProvider jts, JsonToken current) throws IOException, JsonParseException {
		JsonToken t = current;
		if (t == JsonToken.START_OBJECT) {
			while (true) {
				t = jts.nextToken();
				if (t == JsonToken.END_OBJECT)
					break;
				t = jts.nextToken();
				skipChildren(jts, t);
			}
		} else if (t == JsonToken.START_ARRAY) {
			while (true) {
				t = jts.nextToken();
				if (t == JsonToken.END_ARRAY)
					break;
				skipChildren(jts, t);
			}
		}
	}

	private static void extractFieldsWithOpenToken(TokenSequenceProvider jts, JsonToken current, WsSubsetNode selection, 
			JsonGenerator jgen) throws IOException {
		JsonToken t = current;
		if (t == JsonToken.START_OBJECT) {
			if (selection.hasChildren()) {
				Set<String> selectedFields = new LinkedHashSet<String>(selection.getChildren().keySet());
				boolean all = false;
				WsSubsetNode allChild = null;
				if (selectedFields.contains("*")) {
					all = true;
					selectedFields.remove("*");
					allChild = selection.getChildren().get("*");
					if (selectedFields.size() > 0)
						throw new IllegalStateException("WS subset path with * contains other fields: " + selectedFields);
				}
				writeCurrentToken(jts, t, jgen);
				while (true) {
					t = jts.nextToken();
					if (t == JsonToken.END_OBJECT) {
						writeCurrentToken(jts, t, jgen);
						break;
					}
					if (t != JsonToken.FIELD_NAME)
						throw new IllegalStateException("Error parsing json format: " + t.asString());
					String fieldName = jts.getText();
					if (all || selectedFields.contains(fieldName)) {
						writeCurrentToken(jts, t, jgen);
						t = jts.nextToken();
						extractFieldsWithOpenToken(jts, t, all ? allChild : selection.getChildren().get(fieldName), jgen);
					} else {
						t = jts.nextToken();
						skipChildren(jts, t);
					}
				}
			} else if (selection.isNeedKeys()) {
				jgen.writeStartArray();
				while (true) {
					t = jts.nextToken();
					if (t == JsonToken.END_OBJECT) {
						jgen.writeEndArray();
						break;
					}
					if (t != JsonToken.FIELD_NAME)
						throw new IllegalStateException("Error parsing json format: " + t.asString());
					jgen.writeString(jts.getText());
					t = jts.nextToken();
					skipChildren(jts, t);
				}
			} else {  // need all fields and values
				writeTokensFromCurrent(jts, t, jgen);
			}
		} else if (t == JsonToken.START_ARRAY) {
			if (selection.hasChildren()) {
				Set<String> selectedFields = new LinkedHashSet<String>(selection.getChildren().keySet());
				WsSubsetNode allChild = null;
				if (!selectedFields.contains("[*]"))
					throw new IllegalStateException("WS subset path doesn't contain [*] on array level: " + selectedFields);
				selectedFields.remove("[*]");
				allChild = selection.getChildren().get("[*]");
				if (selectedFields.size() > 0)
					throw new IllegalStateException("WS subset path with [*] contains other fields: " + selectedFields);
				writeCurrentToken(jts, t, jgen);
				while (true) {
					t = jts.nextToken();
					if (t == JsonToken.END_ARRAY) {
						writeCurrentToken(jts, t, jgen);
						break;
					}
					extractFieldsWithOpenToken(jts, t, allChild, jgen);
				}
			} else {
				if (selection.isNeedKeys())
					throw new IllegalStateException("WS subset path contains keys-of level for array value");
				// need all elements
				writeTokensFromCurrent(jts, t, jgen);
			}
		} else {
			if (selection.hasChildren())
				throw new IllegalStateException("WS subset path contains non-empty level for scalar value");
			if (selection.isNeedKeys())
				throw new IllegalStateException("WS subset path contains keys-of level for scalar value");
			writeCurrentToken(jts, t, jgen);
		}
	}
}
