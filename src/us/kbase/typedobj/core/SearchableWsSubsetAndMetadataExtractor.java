package us.kbase.typedobj.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.common.utils.JsonTreeGenerator;
import us.kbase.kidl.KidlParseException;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Extraction of ws-searchable subset and selected metadata based on a json token stream.
 * 
 * @author msneddon
 * @author rsutormin
 */
public class SearchableWsSubsetAndMetadataExtractor {
	private static ObjectMapper mapper = new ObjectMapper();

	/**
	 * extract the fields listed in selection from the element and add them to the subset
	 * 
	 * Subset selection must either be an object containing structure field names to extract, '*' in the case of
	 * extracting a mapping, or '[*]' for extracting a list.  if the selection is empty, nothing is added.
	 * If extractKeysOf is set, and the element is an Object (ie a kidl mapping), then an array of the keys
	 * is added instead of the entire mapping.
	 * 
	 * we assume here that selection has already been validated against the structure of the document, so that
	 * if we get true on extractKeysOf, it really is a mapping, and if we get a '*' or '[*]', it really is
	 * a mapping or array.
	 * 
	 * Metadata extraction happens by creating a metadataExtractionHandler which has registered the metadata
	 * selections.  As metadata items are found during traversal for subset extraction, they are added to
	 * the metadata extraction handler.  Then, after calling extract fields, you can use the metadata extraction
	 * handler to get the metadata found.
	 * 
	 */
	public static JsonNode extractFields(
			TokenSequenceProvider jts, 
			ObjectNode keysOfSelection,
			ObjectNode fieldsSelection,
			long maxSubdataSize,
			MetadataExtractionHandler metadataExtractionHandler) 
					throws IOException, TypedObjectExtractionException {

		System.out.println(keysOfSelection);
		System.out.println(fieldsSelection);
		System.out.println(metadataExtractionHandler);

		SearchableWsSubsetAndMetadataNode root = new SearchableWsSubsetAndMetadataNode();
		//if the selection is empty, we return without adding anything
		if (keysOfSelection != null && keysOfSelection.size() > 0) 
			prepareWsSubsetTree(keysOfSelection, true, root);
		if (fieldsSelection != null && fieldsSelection.size() > 0)
			prepareWsSubsetTree(fieldsSelection, false, root);
		if (metadataExtractionHandler != null)
			prepareMetadataSelectionTree(metadataExtractionHandler, root);

		root.printTree("TREE  ");
		JsonNode subset = null;

		// if there is nothing to extract as subdata, then we create an empty node because the
		// extractFieldsWithOpenToken method will not add anything to the stream unless something
		// needs to be extracted as subdata
		if ( !root.isNeedSubsetInChildren() ) {
			subset = mapper.createObjectNode();
			if(root.getNeedValueForMetadata().isEmpty() && root.getNeedLengthForMetadata().isEmpty() && !(root.hasChildren())) {
				// no subset, no metadata, no children.  Tree is empty, so we just return
				return subset;
			} else {
				// no subset, but we need metadata so run the extraction, but without the json tree generator
				JsonToken t = jts.nextToken();
				extractFieldsWithOpenToken(jts, t, root, metadataExtractionHandler, null, new ArrayList<String>());
				return subset;
			}
		}
//		

//		START_OBJECT
//		FIELD_NAME
//		START_OBJECT
//		FIELD_NAME
//		VALUE_NUMBER_INT
//		FIELD_NAME
//		VALUE_NUMBER_INT
//		FIELD_NAME
//		VALUE_NUMBER_INT
//		END_OBJECT
//		FIELD_NAME
//		START_OBJECT
//		FIELD_NAME
//		VALUE_NUMBER_FLOAT
//		FIELD_NAME
//		VALUE_NUMBER_FLOAT
//		FIELD_NAME
//		VALUE_NUMBER_FLOAT
//		END_OBJECT
//		FIELD_NAME
//		VALUE_STRING
//		END_OBJECT

		// we need subdata, so run the full method
		JsonTreeGenerator jgen = new JsonTreeGenerator(mapper);
		jgen.setMaxDataSize(maxSubdataSize);
		JsonToken t = jts.nextToken();
		extractFieldsWithOpenToken(jts, t, root, metadataExtractionHandler, jgen, new ArrayList<String>());
		jgen.close();
		return jgen.getTree();
	}

	/**
	 * Method prepares parsing tree for set of key or field selections. The idea is to join two trees
	 * for keys and for fields into common tree cause we have no chance to process json tokens of
	 * real data twice.
	 */
	private static void prepareWsSubsetTree(JsonNode selection, boolean keysOf, 
			SearchableWsSubsetAndMetadataNode parent) {
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
				SearchableWsSubsetAndMetadataNode child = null;
				if (parent.getChildren() == null || 
						!parent.getChildren().containsKey(entry.getKey())) {
					child = new SearchableWsSubsetAndMetadataNode();
					parent.addChild(entry.getKey(), child);
					parent.setNeedSubsetInChildren(true);
				} else {
					child = parent.getChildren().get(entry.getKey());
				}
				prepareWsSubsetTree(entry.getValue(), keysOf, child);
			}
		}
	}

	/**
	 * Add the metadata selection to the parsing tree.  The metadata selection can ONLY be at the top level fields
	 * and can ONLY be used to extract the field value (if a scalar) as a string, or extract the length of a map (i.e. object) or
	 * array or string.  If you extend this to add lower level selections, you must revise the extraction logic!  Namely,
	 * behavior will not be correct if there is a metadata selection at a lower level, and a subdata extraction at a higher level.
	 * 
	 */
	private static void prepareMetadataSelectionTree(MetadataExtractionHandler metadataExtractionHandler, SearchableWsSubsetAndMetadataNode parent) {
		// currently, we can only extract fields from the top level
		JsonNode selection = metadataExtractionHandler.getMetadataSelection();
		Iterator<Map.Entry<String, JsonNode>> it = selection.fields();
		while (it.hasNext()) {
			Map.Entry<String, JsonNode> entry = it.next();
			String metadataName = entry.getKey();
			String expression = entry.getValue().asText().trim();

			// evaluate the metadata selection expression (right now we only support top-level field names, and the length(f) function)
			if(expression.startsWith("length(")) {
				if(expression.endsWith(")")) {
					expression = expression.substring(7);
					expression = expression.substring(0, expression.length()-1);
					SearchableWsSubsetAndMetadataNode selectionNode = parent.getChild(expression);
					if(selectionNode==null) {
						selectionNode = new SearchableWsSubsetAndMetadataNode();
						parent.addChild(expression, selectionNode);
					}
					selectionNode.addNeedLengthForMetadata(metadataName);
				}
			} else {
				SearchableWsSubsetAndMetadataNode selectionNode = parent.getChild(expression);
				if(selectionNode==null) {
					selectionNode = new SearchableWsSubsetAndMetadataNode();
					parent.addChild(expression, selectionNode);
				}
				selectionNode.addNeedValueForMetadata(metadataName);
			}
		}
	}

	/**
	 * This method is recursively processing block of json data (map, array of scalar) when
	 * first token of this block was already taken and stored in current variable. This is
	 * typical for processing array elements because we need to read first token in order to
	 * know is it the end of array of not. For maps/objects there is such problem because
	 * we read field token before processing value block.
	 * 
	 * This method returns the number of (top-level) elements in the object or list, which
	 * may be needed in computing metadata.
	 */
	private static long writeTokensFromCurrent(TokenSequenceProvider jts, JsonToken current, 
			JsonGenerator jgen) throws IOException, TypedObjectExtractionException {
		JsonToken t = current;
		System.out.println("in dump, writing token "+t);
		writeCurrentToken(jts, t, jgen);
		long n_elements = 0;
		if (t == JsonToken.START_OBJECT) {
			while (true) {
				t = jts.nextToken();
				System.out.println("in dump, writing token "+t);
				writeCurrentToken(jts, t, jgen);
				if (t == JsonToken.END_OBJECT)
					break;
				if (t != JsonToken.FIELD_NAME)
					throw new TypedObjectExtractionException("Error parsing json format: " + t.asString());
				t = jts.nextToken();
				n_elements++;
				writeTokensFromCurrent(jts, t, jgen);
			}
		} else if (t == JsonToken.START_ARRAY) {
			while (true) {
				t = jts.nextToken();
				if (t == JsonToken.END_ARRAY) {
					writeCurrentToken(jts, t, jgen);
					break;
				}
				n_elements++;
				writeTokensFromCurrent(jts, t, jgen);
			}
		}
		return n_elements;
	}

	/**
	 * when metadata extraction of the length of an object/array is required, but we do not
	 * need to write the data to the subdata stream, then we can call this to traverse the
	 * data and compute the length.
	 */
	private static long countElementsInCurrent(TokenSequenceProvider jts, JsonToken current, 
			JsonGenerator jgen) throws IOException, TypedObjectExtractionException {
		JsonToken t = current;
		long n_elements = 0;
		if (t == JsonToken.START_OBJECT) {
			while (true) {
				t = jts.nextToken();
				if (t == JsonToken.END_OBJECT)
					break;
				if (t != JsonToken.FIELD_NAME)
					throw new TypedObjectExtractionException("Error parsing json format: " + t.asString());
				t = jts.nextToken();
				n_elements++;
				countElementsInCurrent(jts, t, jgen);
			}
		} else if (t == JsonToken.START_ARRAY) {
			while (true) {
				t = jts.nextToken();
				if (t == JsonToken.END_ARRAY) {
					break;
				}
				n_elements++;
				countElementsInCurrent(jts, t, jgen);
			}
		}
		return n_elements;
	}


	/**
	 * Method processes (writes into output token stream - jgen) only one token.
	 */
	private static JsonToken writeCurrentToken(TokenSequenceProvider jts, JsonToken current, 
			JsonGenerator jgen) throws IOException, TypedObjectExtractionException {
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
			// VALUE_NUMBER_INT type corresponds to set of integer types
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
			// VALUE_NUMBER_FLOAT type corresponds to set of floating point types
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
			throw new TypedObjectExtractionException("Unexpected token type: " + t);
		}
		return t;
	}

	/**
	 * If some part of the json data is not mentioned in traversal tree, we can skip all tokens of it
	 */
	private static void skipChildren(TokenSequenceProvider jts, JsonToken current) 
			throws IOException, JsonParseException {
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


	/**
	 * helper method to add the length of an array/object to the metadata for every metadata named in metadataHandler
	 */
	private static void addLengthMetadata(long length, SearchableWsSubsetAndMetadataNode selection, MetadataExtractionHandler metadataHandler) {
		List<String> metadataNames = selection.getNeedLengthForMetadata();
		for(String name:metadataNames) {
			metadataHandler.saveMetadata(name,Long.toString(length));
		}
	}

	/**
	 * helper method to add the value of an array/object to the metadata for every metadata named in metadataHandler
	 */
	private static void addValueMetadata(String value, SearchableWsSubsetAndMetadataNode selection, MetadataExtractionHandler metadataHandler) {
		List<String> metadataNames = selection.getNeedValueForMetadata();
		for(String name:metadataNames) {
			metadataHandler.saveMetadata(name,value);
		}
	}

	/*
	 * This is main recursive method for tracking current token place in searchable schema tree
	 * and making decisions whether or not we need to process this token or block of tokens or
	 * just skip it.
	 */
	private static void extractFieldsWithOpenToken(
			TokenSequenceProvider jts,
			JsonToken current, 
			SearchableWsSubsetAndMetadataNode selection,
			MetadataExtractionHandler metadataHandler,
			JsonGenerator jgen,
			List<String> path) 
					throws IOException, TypedObjectExtractionException {

		JsonToken t = current;

		// We observe the opening of a mapping/object in the JSON data
		if (t == JsonToken.START_OBJECT) {
			System.out.println("starting to read an object");
			// we need everything at this node and below
			if (selection.isNeedAll()) {
				System.out.println("we need everything");
				if(selection.hasChildren()) {  // if it has children, then we must need some metadata below
					// TODO: right now this is not possible because metadata is always at the top level
				} else { // if it does not have children, then we just extract everything

					System.out.println("now at token "+t);
					long n_elements = writeTokensFromCurrent(jts, t, jgen);
					addLengthMetadata(n_elements, selection, metadataHandler);
				}
				System.out.println("we got everything");
			}

			// we need only keys of this node
			else if (selection.isNeedKeys()) {
				System.out.println("we need keys only");
				Set<String> selectedFields = null;
				if(selection.hasChildren()) { // if it has children, then we must need some metadata value below
					selectedFields = new LinkedHashSet<String>(selection.getChildren().keySet());
				} else {
					selectedFields = new LinkedHashSet<String> ();
				}
				jgen.writeStartArray();  // write in output start of array instead of start of object
				long n_elements = 0;
				while (true) {
					t = jts.nextToken();
					if (t == JsonToken.END_OBJECT) {
						jgen.writeEndArray();  // write in output end of array instead of end of object
						break;
					}
					if (t != JsonToken.FIELD_NAME)
						throw new TypedObjectExtractionException("Error parsing json format: " + t.asString());
					String fieldName = jts.getText();
					jgen.writeString(fieldName);  // write in output field name
					t = jts.nextToken();
					n_elements++;

					if (selectedFields.contains(fieldName)) {
						// then we have to traverse down the tree
						SearchableWsSubsetAndMetadataNode child = selection.getChild(fieldName);
						// add field to the end of path branch
						path.add(fieldName);
						// process value of this field recursively
						extractFieldsWithOpenToken(jts, t, child, metadataHandler, jgen, path);
						// remove field from end of path branch
						path.remove(path.size() - 1);
					} else {
						// we can safely skip the children
						skipChildren(jts, t);
					}
				}
				addLengthMetadata(n_elements, selection, metadataHandler);

			}

			// we have children, and these children have restrictions in subdata, so we go down the tree
			else if (selection.hasChildren()) {
				System.out.println("we need nothing here, but there are children");
				
				// we will remove visited keys from selectedFields and check emptiness at object end
				Set<String> selectedFields = new LinkedHashSet<String>(selection.getChildren().keySet());
				boolean all = false;
				SearchableWsSubsetAndMetadataNode allChild = null;

				if (selectedFields.contains("*")) {
					all = true;
					selectedFields.remove("*"); // detach the subtree below the *
					allChild = selection.getChildren().get("*");
					if (selectedFields.size() > 0)
						throw new TypedObjectExtractionException("WS subset path with * contains other " +
								"fields (" + selectedFields + ") at " + SubdataExtractor.getPathText(path));
				}
				// process first token standing for start of object, only write if we need subset data below
				if(selection.isNeedSubsetInChildren())
					writeCurrentToken(jts, t, jgen);
				long n_elements = 0;
				while (true) {
					t = jts.nextToken();
					System.out.println("t: "+t);
					n_elements++;
					if (t == JsonToken.END_OBJECT) {
						if(selection.isNeedSubsetInChildren()) {
							writeCurrentToken(jts, t, jgen);
						}
						break;
					}
					if(t==null)  System.out.println("t is null"); //break; } if(true) {continue; }
					if (t != JsonToken.FIELD_NAME)
						throw new TypedObjectExtractionException("Error parsing json format: " + t.asString());
					String fieldName = jts.getText();
					if (all || selectedFields.contains(fieldName)) {
						SearchableWsSubsetAndMetadataNode child = selection.getChild(fieldName);
						System.out.println("Looking at field "+fieldName);
						// if we need all fields or the field is in list of necessary fields we process it and the value following after that
						// We have to check if we need to write out this node!!!  only works because metadata can only
						// be defined on fields at the top level!  
						if(selection.isNeedSubsetInChildren()) {
							writeCurrentToken(jts, t, jgen);
						}
						// read first token of value block in order to prepare state for recursive 
						// extractFieldsWithOpenToken call
						t = jts.nextToken();
						System.out.println("at token "+t);
						// add field to the end of path branch
						path.add(fieldName);
						// we cannot have 'all' and select metadata below, because we cannot enter a mapping and
						// all (ie the * notation) can only be used in the case of mappings to select all keys
						System.out.println("starting sub extraction");
						extractFieldsWithOpenToken(jts, t, all ? allChild : child, metadataHandler, jgen, path);
						System.out.println("ended sub extraction");
						// remove field from end of path branch
						path.remove(path.size() - 1);
						if (!all)
							selectedFields.remove(fieldName);
					} else {
						// otherwise we skip value following after field
						t = jts.nextToken();
						System.out.println("skipping" + t);
						skipChildren(jts, t);
					}
				}
				addLengthMetadata(n_elements, selection, metadataHandler); // add length of object to metadata if needed
				/* 
							// TODO: we temporary comment this check because fields can be optional
							if (!selectedFields.isEmpty()) {
								String notFound = selectedFields.iterator().next();
								throw new TypedObjectExtractionException("Malformed selection string, cannot get " +
										"'" + notFound + "', at: " + SubdataExtractor.getPathText(path));
							}*/
				
			} 


			// need (at most) just the length metadata field
			else {
				// there are no children, and we didn't need subdata here, so the only thing possible is getting metadata
				long n_elements = countElementsInCurrent(jts,t,jgen);
				addLengthMetadata(n_elements, selection, metadataHandler);
			}
		}



		// We observe an array/list starting in the real JSON data
		else if (t == JsonToken.START_ARRAY) {
			if (selection.hasChildren()) {  // we have some restrictions for array item positions in selection
				Set<String> selectedFields = new LinkedHashSet<String>(
						selection.getChildren().keySet());
				SearchableWsSubsetAndMetadataNode allChild = null;
				// now we support only '[*]' keyword which means all elements
				if (!selectedFields.contains("[*]"))
					throw new TypedObjectExtractionException("WS subset path doesn't contain [*] on array " +
							"level (" + selectedFields + ") at " + SubdataExtractor.getPathText(path));
				selectedFields.remove("[*]");
				allChild = selection.getChildren().get("[*]");
				if (selectedFields.size() > 0)
					throw new TypedObjectExtractionException("WS subset path with [*] contains other " +
							"fields (" + selectedFields + ") at " + SubdataExtractor.getPathText(path));
				writeCurrentToken(jts, t, jgen);  // write start of array into output
				long n_elements = 0;
				for (int pos = 0; ; pos++) {
					t = jts.nextToken();
					n_elements++;
					if (t == JsonToken.END_ARRAY) {
						writeCurrentToken(jts, t, jgen);
						break;
					}
					// add element position to the end of path branch
					path.add("" + pos);
					// process value of this element recursively
					extractFieldsWithOpenToken(jts, t, allChild, metadataHandler, jgen, path);
					// remove field from end of path branch
					path.remove(path.size() - 1);
				}
				addLengthMetadata(n_elements, selection, metadataHandler); // add length of array to metadata if needed
			} else {  // we need the whole array
				if (selection.isNeedKeys())
					throw new TypedObjectExtractionException("WS subset path contains keys-of level for array " +
							"value at " + SubdataExtractor.getPathText(path));
				// need all elements
				if(selection.isNeedAll()) { // need all elements, possibly also the length
					long n_elements = writeTokensFromCurrent(jts, t, jgen);
					addLengthMetadata(n_elements, selection, metadataHandler);
				} else { // only need the length of the object
					long n_elements = countElementsInCurrent(jts,t,jgen);
					addLengthMetadata(n_elements, selection, metadataHandler);
				}
			}
		} else {	// we observe scalar value (text, integer, double, boolean, null) in real json data
			if (selection.hasChildren())
				throw new TypedObjectExtractionException("WS subset path contains non-empty level for scalar " +
						"value at " + SubdataExtractor.getPathText(path));
			if (selection.isNeedKeys())
				throw new TypedObjectExtractionException("WS subset path contains keys-of level for scalar " +
						"value at " + SubdataExtractor.getPathText(path));

			if (selection.isNeedAll()) // if this is set, then save the data to the output stream
				writeCurrentToken(jts, t, jgen);

			if(t==JsonToken.VALUE_STRING) { // if a string, add the length to metadata if it was selected
				addLengthMetadata(jts.getText().length(), selection, metadataHandler);
			} else if(t!=JsonToken.VALUE_NULL) {
				if (!selection.getNeedLengthForMetadata().isEmpty())
					throw new TypedObjectExtractionException("WS metadata path contains length() method called on a scalar " +
							"value at " + SubdataExtractor.getPathText(path));
			}
			addValueMetadata(jts.getText(), selection, metadataHandler); // add the value to metadata if needed
		}
	}
}
