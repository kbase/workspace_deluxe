package us.kbase.typedobj.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import us.kbase.common.service.UObject;
import us.kbase.common.util.KBaseJsonTreeGenerator;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
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
	
	public static JsonNode extractFields(ObjectPaths objpaths, JsonNode input) 
			throws IOException, TypedObjectExtractionException {
		TokenSequenceProvider tsp = createTokenSequenceProvider(input);
		JsonNode ret = extractFields(objpaths, tsp);
		tsp.close();
		return ret;
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
	public static JsonNode extractFields(ObjectPaths objpaths, TokenSequenceProvider jts) 
			throws IOException, TypedObjectExtractionException {
		//if the selection is empty, we return without adding anything
		SubdataExtractionNode root = new SubdataExtractionNode();
		for (String p : objpaths.getPaths()) {
			String[] path = parsePath(p);
			root.addPath(path);
		}
		JsonToken t = jts.nextToken();
		KBaseJsonTreeGenerator jgen = new KBaseJsonTreeGenerator(mapper);
		extractFieldsWithOpenToken(jts, t, root, jgen, new ArrayList<String>());
		jgen.close();
		return jgen.getTree();
	}
	
	private static TokenSequenceProvider createTokenSequenceProvider(JsonNode tree) {
		final TreeTraversingParser ttp = new TreeTraversingParser(tree);
		return new TokenSequenceProvider() {
			@Override
			public JsonToken nextToken() throws IOException, JsonParseException {
				return ttp.nextToken();
			}
			@Override
			public String getText() throws IOException, JsonParseException {
				return ttp.getText();
			}
			@Override
			public long getLongValue() throws IOException, JsonParseException {
				return ttp.getLongValue();
			}
			@Override
			public double getDoubleValue() throws IOException, JsonParseException {
				return ttp.getDoubleValue();
			}
			@Override
			public void close() throws IOException {
				ttp.close();
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
			jgen.writeNumber(jts.getLongValue());
		} else if (t == JsonToken.VALUE_NUMBER_FLOAT) {
			jgen.writeNumber(jts.getDoubleValue());
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

	private static void extractFieldsWithOpenToken(TokenSequenceProvider jts, JsonToken current, 
			SubdataExtractionNode selection, JsonGenerator jgen, List<String> path) 
					throws IOException, TypedObjectExtractionException {
		JsonToken t = current;
		if (t == JsonToken.START_OBJECT) {
			if (selection.hasChildren()) {
				Set<String> selectedFields = new LinkedHashSet<String>(selection.getChildren().keySet());
				boolean all = false;
				SubdataExtractionNode allChild = null;
				if (selectedFields.contains("*")) {
					all = true;
					selectedFields.remove("*");
					allChild = selection.getChildren().get("*");
					if (selectedFields.size() > 0)
						throw new TypedObjectExtractionException("Subdata extraction path with * " +
								"contains other fields " + selectedFields + ", at: " + getPathText(path));
				}
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
						if (!all)
							selectedFields.remove(fieldName);
						writeCurrentToken(jts, t, jgen);
						t = jts.nextToken();
						path.add(fieldName);
						extractFieldsWithOpenToken(jts, t, all ? allChild : selection.getChildren().get(fieldName), jgen, path);
						path.remove(path.size() - 1);
					} else {
						t = jts.nextToken();
						skipChildren(jts, t);
					}
				}
				if (!selectedFields.isEmpty()) {
					String notFound = selectedFields.iterator().next();
					throw new TypedObjectExtractionException("Malformed selection string, cannot get " +
							"'" + notFound + "', at: " + getPathText(path, notFound));
				}
			} else {  // need all fields and values
				writeTokensFromCurrent(jts, t, jgen);
			}
		} else if (t == JsonToken.START_ARRAY) {
			if (selection.hasChildren()) {
				Set<String> selectedFields = new LinkedHashSet<String>(selection.getChildren().keySet());
				SubdataExtractionNode allChild = null;
				if (!selectedFields.contains("[*]")) {
					for (String item : selectedFields) {
						try {
							Integer.parseInt(item);
						} catch (NumberFormatException ex) {
							throw new TypedObjectExtractionException("Subdata extraction path contains " +
									"non-numneric item on array level " + item + ", at: " + getPathText(path));
						}
					}
				}
				if (selectedFields.contains("[*]")) {
					selectedFields.remove("[*]");
					allChild = selection.getChildren().get("[*]");
					if (selectedFields.size() > 0)
						throw new TypedObjectExtractionException("Subdata extraction path with [*] " +
								"contains other fields " + selectedFields + ", at: " + getPathText(path));
				}
				writeCurrentToken(jts, t, jgen);
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
						child = selection.getChildren().get("" + pos);
						selectedFields.remove("" + pos);
					}
					if (child == null) {
						skipChildren(jts, t);
					} else {
						path.add("" + pos);
						extractFieldsWithOpenToken(jts, t, child, jgen, path);
						path.remove(path.size() - 1);
					}
				}
				if (!selectedFields.isEmpty()) {
					String notFound = selectedFields.iterator().next();
					throw new TypedObjectExtractionException("No element at position " +
							"'" + notFound + "', at: " + getPathText(path, notFound));
				}
			} else {
				// need all elements
				writeTokensFromCurrent(jts, t, jgen);
			}
		} else {
			if (selection.hasChildren())
				throw new TypedObjectExtractionException("Subdata extraction path contains " +
						"non-empty level for scalar value, at: " + getPathText(path));
			writeCurrentToken(jts, t, jgen);
		}
	}

	private static String getPathText(List<String> path, String add) {
		path.add(add);
		String ret = getPathText(path);
		path.remove(path.size() - 1);
		return ret;
	}
	
	private static String getPathText(List<String> path) {
		StringBuilder ret = new StringBuilder();
		for (String item : path)
			ret.append("/").append(item);
		return ret.toString();
	}
}
