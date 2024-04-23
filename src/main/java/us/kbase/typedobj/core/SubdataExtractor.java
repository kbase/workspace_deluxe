package us.kbase.typedobj.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import us.kbase.typedobj.exceptions.TypedObjectExtractionException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Extraction of ws-searchable subset based on json token stream.
 * @author rsutormin
 */
public class SubdataExtractor {
	
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
	public static void extract(SubsetSelection objpaths, JsonParser jp, JsonGenerator output) 
			throws IOException, TypedObjectExtractionException {
		extractFields(objpaths, createTokenSequenceProvider(jp), output);
	}
	
	private static void extractFields(SubsetSelection objpaths, TokenSequenceProvider jts, JsonGenerator output) 
	        throws IOException, TypedObjectExtractionException {
		//if the selection is empty, we return without adding anything
		SubdataExtractionNode root = new SubdataExtractionNode();
		for (int i = 0; i < objpaths.size(); i++) {
		    try {
		        String[] path = trimPath(objpaths.getPath(i));
		        root.addPath(path);
		    } catch (JsonPointerParseException ex) {
		        throw new TypedObjectExtractionException(ex.getMessage(), ex);
		    }
		}
		JsonToken t = jts.nextToken();
		extractFieldsWithOpenToken(jts, t, root, output, new ArrayList<String>(), 
		        objpaths.isStrictMaps(), objpaths.isStrictArrays());
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
	private static String[] trimPath(String[] pathToken) {
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
	private static void writeTokensFromCurrent(
			final TokenSequenceProvider jts,
			final JsonToken current, 
			final JsonGenerator jgen)
			throws IOException, TypedObjectExtractionException {
		JsonToken t = current;
		writeCurrentToken(jts, t, jgen);
		if (t == JsonToken.START_OBJECT) {
			while (true) {
				t = jts.nextToken();
				writeCurrentToken(jts, t, jgen);
				if (t == JsonToken.END_OBJECT) {
					break;
				}
				if (t != JsonToken.FIELD_NAME) {
					throw new TypedObjectExtractionException(
							"Error parsing json format: " + t.asString());
				}
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
			SubdataExtractionNode selection, JsonGenerator jgen, List<String> path, 
			boolean strictMaps, boolean strictArrays) throws IOException, TypedObjectExtractionException {
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
							selection.getChildren().get(fieldName), jgen, path, strictMaps, strictArrays);
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
				if (strictMaps && !selectedFields.isEmpty()) {
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
						extractFieldsWithOpenToken(jts, t, child, jgen, path, strictMaps, strictArrays);
						// remove field from tail of path branch
						path.remove(path.size() - 1);
					}
				}
				// let's check have we visited all selected items in this array
				if (strictArrays && !selectedFields.isEmpty()) {
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
				throw new TypedObjectExtractionException("Invalid selection: the path given specifies fields or elements that do not exist because data " +
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
			ret.append("/").append(item.replace("~", "~0").replace("/", "~1"));
		return ret.toString();
	}
}
