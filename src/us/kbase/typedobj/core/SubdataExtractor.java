package us.kbase.typedobj.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import us.kbase.common.utils.JsonTreeGenerator;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;

/**
 * Extraction of ws-searchable subset based on json token stream.
 * @author rsutormin
 */
public class SubdataExtractor {
	private static ObjectMapper mapper = new ObjectMapper();
	
	/** sets default behavior for extraction.  If strict is true, then errors are thrown if a field
	 * or array element is requested but does not exist in the data object. If strict is false, then
	 * if the field or array element is missing, nothing is returned.  This is useful for optional
	 * fields, but may be prone to error if a user had a typo in the path...
	 */
	public static boolean STRICT_DEFAULT = false;
	/**
	 * This method should be used only in tests because it processes json data 
	 * stored in memory as a tree rather than as token stream that could be processed
	 * directly from a file.
	 */
	public static JsonNode extract(ObjectPaths objpaths, JsonNode input) 
			throws IOException, TypedObjectExtractionException {
		return extract(objpaths, input, STRICT_DEFAULT);
	}
	public static JsonNode extract(ObjectPaths objpaths, JsonNode input, boolean strict) 
			throws IOException, TypedObjectExtractionException {
		TokenSequenceProvider tsp = createTokenSequenceProvider(new TreeTraversingParser(input));
		JsonTreeGenerator jgen = new JsonTreeGenerator(mapper);
		extractFields(objpaths, tsp, jgen, strict);
		tsp.close();
		jgen.close();
		return jgen.getTree();
	}
	
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
	 * @throws TypedObjectExtractionException 
	 */
	public static void extract(ObjectPaths objpaths, JsonParser jp, JsonGenerator output) 
			throws IOException, TypedObjectExtractionException {
		extract(objpaths, jp, output, STRICT_DEFAULT);
	}
	public static void extract(ObjectPaths objpaths, JsonParser jp, JsonGenerator output, boolean strict) 
			throws IOException, TypedObjectExtractionException {
		extractFields(objpaths, createTokenSequenceProvider(jp), output, strict);
	}
	
	private static void extractFields(ObjectPaths objpaths, TokenSequenceProvider jts, JsonGenerator output, boolean strict) 
			throws IOException, TypedObjectExtractionException {
		//if the selection is empty, we return without adding anything
		SubdataExtractionNode root = new SubdataExtractionNode();
		for (String p : objpaths.getPaths()) {
			String[] path = parsePath(p);
			root.addPath(path);
		}
		JsonToken t = jts.nextToken();
		extractFieldsWithOpenToken(jts, t, root, output, new ArrayList<String>(), strict);
	}
	
	/*
	 * TokenSequenceProvider wrapper around JsonParser.
	 */
	private static TokenSequenceProvider createTokenSequenceProvider(final JsonParser jp) {
		return new TokenSequenceProvider() {
			@Override
			public JsonToken nextToken() throws IOException, JsonParseException {
				return jp.nextToken();
			}
			@Override
			public String getText() throws IOException, JsonParseException {
				return jp.getText();
			}
			@Override
			public Number getNumberValue() throws IOException, JsonParseException {
				return jp.getNumberValue();
			}
			@Override
			public void close() throws IOException {
				jp.close();
			}
			@Override
			public boolean isComplete() {
				return false;
			}
		};
	}
	
	// remove trailing '*' and '[*]', because these select everything
	private static String[] parsePath(String path) {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		String [] pathToken = path.split("/");
		int end = pathToken.length;
		for(int k=pathToken.length-1; k>0; k--) {
			if(pathToken[k].equals("*") || pathToken[k].equals("[*]")) {
				// but we do not remove the first one, because this means we select everything...
				if(k==0) {break;} 
				end--;
			} else {break;}
		}
		String[] parsedPath = new String[end];
		for(int k=0; k<end; k++) {
			parsedPath[k] = pathToken[k];
		}
		return parsedPath;
	}

	
	/*
	 * This method is recursively processing block of json data (map, array of scalar) when
	 * first token of this block was already taken and stored in current variable. This is
	 * typical for processing array elements because we need to read first token in order to
	 * know is it the end of array of not. For maps/objects there is such problem because
	 * we read field token before processing value block.
	 */
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

	/*
	 * Method processes (writes into output token stream - jgen) only one token.
	 */
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

	/*
	 * If some part of real json data is not mention in subset schema tree we need to skip tokens of it
	 */
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

	/*
	 * This is main recursive method for tracking current token place in subset schema tree
	 * and making decisions whether or not we need to process this token or block of tokens or
	 * just skip it.
	 */
	private static void extractFieldsWithOpenToken(TokenSequenceProvider jts, JsonToken current, 
			SubdataExtractionNode selection, JsonGenerator jgen, List<String> path, boolean strict) 
					throws IOException, TypedObjectExtractionException {
		JsonToken t = current;
		if (t == JsonToken.START_OBJECT) {	// we observe open of mapping/object in real json data
			if (selection.hasChildren()) {	// we have some restrictions for this object in selection
				// we will remove visited keys from selectedFields and check emptiness at object end
				Set<String> selectedFields = new LinkedHashSet<String>(selection.getChildren().keySet());
				boolean all = false;
				SubdataExtractionNode allChild = null;
				if (selectedFields.contains("*")) {
					all = true;
					selectedFields.remove("*");
					allChild = selection.getChildren().get("*");
					if (selectedFields.size() > 0)
						throw new TypedObjectExtractionException("Invalid selection: the selection path contains both '*'" +
								"to select all fields and selction of specific fields (" + selectedFields + "), at: " + getPathText(path));
				}
				// process first token standing for start of object
				writeCurrentToken(jts, t, jgen);
				while (true) {
					t = jts.nextToken();
					if (t == JsonToken.END_OBJECT) {
						writeCurrentToken(jts, t, jgen);
						break;
					}
					if (t != JsonToken.FIELD_NAME)
						throw new TypedObjectExtractionException("Error parsing json format " + 
								t.asString() + ", at: " + getPathText(path));
					String fieldName = jts.getText();
					if (all || selectedFields.contains(fieldName)) {
						// if we need all fields or the field is present in list of necessary fields 
						// we process it and value following after that
						if (!all)
							selectedFields.remove(fieldName);
						writeCurrentToken(jts, t, jgen);
						// read first token of value block in order to prepare state for recursive 
						// extractFieldsWithOpenToken call
						t = jts.nextToken();
						// add field to the tail of path branch
						path.add(fieldName);
						// process value corresponding to this field recursively
						extractFieldsWithOpenToken(jts, t, all ? allChild : 
							selection.getChildren().get(fieldName), jgen, path, strict);
						// remove field from tail of path branch
						path.remove(path.size() - 1);
					} else {
						// otherwise we skip value following after field
						t = jts.nextToken();
						skipChildren(jts, t);
					}
				}
				// let's check have we visited all selected fields in this map
				// we will not visit them in real data and hence will not delete them from selection
				if (strict && !selectedFields.isEmpty()) {
					String notFound = selectedFields.iterator().next();
					throw new TypedObjectExtractionException("Invalid selection: data does not contain a field or key named " +
							"'" + notFound + "', at: " + getPathText(path, notFound));
				}
			} else {  // need all fields and values
				writeTokensFromCurrent(jts, t, jgen);
			}
		} else if (t == JsonToken.START_ARRAY) {	// we observe open of array/list in real json data
			if (selection.hasChildren()) {  // we have some restrictions for array item positions in selection
				Set<String> selectedFields = new LinkedHashSet<String>(selection.getChildren().keySet());
				SubdataExtractionNode allChild = null;
				// now we support only '[*]' which means all elements and set of numbers in case of 
				// certain item positions are selected in array
				if (!selectedFields.contains("[*]")) {
					for (String item : selectedFields) {
						try {
							Integer.parseInt(item);
						} catch (NumberFormatException ex) {
							throw new TypedObjectExtractionException("Invalid selection: data at '"+getPathText(path)+"' is an array, so " +
									"element selection must be an integer.  You requested element '" + item + "', at: " + getPathText(path));
						}
					}
				}
				if (selectedFields.contains("[*]")) {
					selectedFields.remove("[*]");
					allChild = selection.getChildren().get("[*]");
					// if there is [*] keyword selected there shouldn't be anything else in selection
					if (selectedFields.size() > 0)
						throw new TypedObjectExtractionException("Invalid selection: the selection path contains both '[*]'" +
								"to select all elements and selction of specific elements (" + selectedFields + "), at: " + getPathText(path));
				}
				writeCurrentToken(jts, t, jgen);  // write start of array into output
				for (int pos = 0; ; pos++) {
					t = jts.nextToken();
					if (t == JsonToken.END_ARRAY) {
						writeCurrentToken(jts, t, jgen);
						break;
					}
					SubdataExtractionNode child = null;
					if (allChild != null) {
						child = allChild; 
					} else {
						String key = "" + pos;
						if (selection.getChildren().containsKey(key)) {
							child = selection.getChildren().get(key);
							selectedFields.remove(key);
						}
					}
					if (child == null) {
						// this element of array is not selected, skip it
						skipChildren(jts, t);
					} else {
						// add element position to the tail of path branch
						path.add("" + pos);
						// process value of this element recursively
						extractFieldsWithOpenToken(jts, t, child, jgen, path, strict);
						// remove field from tail of path branch
						path.remove(path.size() - 1);
					}
				}
				// let's check have we visited all selected items in this array
				if (!selectedFields.isEmpty()) {
					String notFound = selectedFields.iterator().next();
					throw new TypedObjectExtractionException("Invalid selection: no array element exists at position " +
							"'" + notFound + "', at: " + getPathText(path, notFound));
				}
			} else {
				// need all elements
				writeTokensFromCurrent(jts, t, jgen);
			}
		} else {	// we observe scalar value (text, integer, double, boolean, null) in real json data
			if (selection.hasChildren())
				throw new TypedObjectExtractionException("Invalid selection: the path given specifies fields or elements that do not exists becase data " +
						"at this location is a scalar value (i.e. string, integer, float), at: " + getPathText(path));
			writeCurrentToken(jts, t, jgen);
		}
	}

	public static String getPathText(List<String> path, String add) {
		path.add(add);
		String ret = getPathText(path);
		path.remove(path.size() - 1);
		return ret;
	}
	
	public static String getPathText(List<String> path) {
		StringBuilder ret = new StringBuilder();
		for (String item : path)
			ret.append("/").append(item);
		return ret.toString();
	}
}
