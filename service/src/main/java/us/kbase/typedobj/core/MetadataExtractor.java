package us.kbase.typedobj.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.typedobj.exceptions.ExceededMaxMetadataSizeException;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Extraction of selected metadata based on a json token stream.
 * 
 * @author msneddon
 * @author rsutormin
 * @author gaprice
 */
public class MetadataExtractor {
	
	/**
	 * Metadata extraction happens by creating a metadataExtractionHandler
	 * which has registered the metadata selections.  As metadata items are
	 * found during traversal for subset extraction, they are added to
	 * the metadata extraction handler.  Then, after calling extract fields,
	 * you can use the metadata extraction handler to get the metadata found.
	 * @throws ExceededMaxMetadataSizeException 
	 * @throws IOException 
	 * 
	 */
	public static ExtractedMetadata extractFields(
			final TokenSequenceProvider jts, 
			final MetadataExtractionHandler metadataExtractionHandler) 
			throws ExceededMaxMetadataSizeException, IOException {

		//System.out.println(keysOfSelection);
		//System.out.println(fieldsSelection);
		//System.out.println(metadataExtractionHandler);
		if (metadataExtractionHandler == null) {
			throw new NullPointerException("metadata handler cannot be null");
		}
		final MetadataNode root = new MetadataNode();
		prepareMetadataSelectionTree(metadataExtractionHandler, root);

		//		root.printTree("  ");
		if(!root.hasChildren()) {
			//Tree is empty, so we just return an empty extraction
			return new ExtractedMetadata(null);
		} else {
			final JsonToken t = jts.nextToken();
			try {
				extractFieldsWithOpenToken(jts, t, root,
						metadataExtractionHandler, new ArrayList<String>());
			} catch (TypedObjectExtractionException e) {
				throw new RuntimeException(
						"This is bad. There is an unexpected internal error when extracting object metadata",
						e);
			}
			return new ExtractedMetadata(
					metadataExtractionHandler.getSavedMetadata());
		}

	}

	/**
	 * Add the metadata selection to the parsing tree.  The metadata selection 
	 * can ONLY be used to extract the field value (if a scalar) as a string,
	 * or extract the length of a map (i.e. object) or
	 * array or string. 
	 * 
	 */
	private static void prepareMetadataSelectionTree(
			final MetadataExtractionHandler metadataExtractionHandler,
			final MetadataNode parent) {
		// currently, we can only extract fields from the top level
		final JsonNode selection =
				metadataExtractionHandler.getMetadataSelection();
		final Iterator<Map.Entry<String, JsonNode>> it = selection.fields();
		while (it.hasNext()) {
			final Map.Entry<String, JsonNode> entry = it.next();
			final String metadataName = entry.getKey();
			String expression = entry.getValue().asText().trim();

			// evaluate the metadata selection expression (right now we only
			// support descending into structures, and the length(f) function)
			boolean getLength = false;
			if(expression.startsWith("length(") && expression.endsWith(")")) {
				expression = expression.substring(7);
				expression = expression.substring(0, expression.length()-1);
				getLength = true;
			}
			
			final String [] expTokens = expression.split("\\.");
			MetadataNode currentNode = parent;
			for(int k=0; k<expTokens.length; k++) {
				MetadataNode childNode =
						currentNode.getChild(expTokens[k]);
				if(childNode==null) {
					childNode = new MetadataNode();
					currentNode.addChild(expTokens[k], childNode);
				}
				currentNode = childNode;
			}
			if(getLength) {
				currentNode.addNeedLengthForMetadata(metadataName);
			} else {
				currentNode.addNeedValueForMetadata(metadataName);
			}
		}
	}

	/**
	 * when metadata extraction of the length of an object/array is required,
	 * but we do not need to write the data to the subdata stream, then we can
	 * call this to traverse the data and compute the length.
	 */
	private static long countElementsInCurrent(
			final TokenSequenceProvider jts,
			final JsonToken current)
			throws IOException, TypedObjectExtractionException {
		JsonToken t = current;
		long n_elements = 0;
		if (t == JsonToken.START_OBJECT) {
			while (true) {
				t = jts.nextToken();
				if (t == JsonToken.END_OBJECT)
					break;
				//should never happen since the data has been type checked
				if (t != JsonToken.FIELD_NAME)
					throw new TypedObjectExtractionException(
							"Error parsing json format: " + t.asString());
				t = jts.nextToken();
				n_elements++;
				countElementsInCurrent(jts, t);
			}
		} else if (t == JsonToken.START_ARRAY) {
			while (true) {
				t = jts.nextToken();
				if (t == JsonToken.END_ARRAY) {
					break;
				}
				n_elements++;
				countElementsInCurrent(jts, t);
			}
		}
		return n_elements;
	}



	/**
	 * If some part of the json data is not mentioned in traversal tree,
	 * we can skip all tokens of it
	 */
	private static void skipChildren(
			final TokenSequenceProvider jts,
			final JsonToken current) 
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
		//this code should never actually run since we don't allow array metadata
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
	 * helper method to add the length of an array/object to the metadata for
	 * every metadata named in metadataHandler
	 */
	private static void addLengthMetadata(
			final long length, MetadataNode selection,
			final MetadataExtractionHandler metadataHandler) 
			throws ExceededMaxMetadataSizeException {
		final List<String> metadataNames = selection.getNeedLengthForMetadata();
		for(final String name:metadataNames) {
			metadataHandler.saveMetadata(name,Long.toString(length));
		}
	}
	private static void addNullLengthMetadata(
			final MetadataNode selection,
			final MetadataExtractionHandler metadataHandler) 
			throws ExceededMaxMetadataSizeException {
		final List<String> metadataNames = selection.getNeedLengthForMetadata();
		for(final String name:metadataNames) {
			metadataHandler.saveMetadata(name,"NaN");
		}
	}

	/**
	 * helper method to add the value of an array/object to the metadata for
	 * every metadata named in metadataHandler
	 */
	private static void addValueMetadata(
			final String value,
			final MetadataNode selection,
			final MetadataExtractionHandler metadataHandler) 
			throws ExceededMaxMetadataSizeException {
		final List<String> metadataNames = selection.getNeedValueForMetadata();
		for(final String name:metadataNames) {
			metadataHandler.saveMetadata(name,value);
		}
	}

	/*
	 * This is main recursive method for tracking current token place in metadata schema tree
	 * and making decisions whether or not we need to process this token or block of tokens or just skip it.
	 */
	private static void extractFieldsWithOpenToken(
			final TokenSequenceProvider jts,
			final JsonToken current, 
			final MetadataNode selection,
			final MetadataExtractionHandler metadataHandler,
			final List<String> path) 
			throws IOException, TypedObjectExtractionException,
				ExceededMaxMetadataSizeException {

		JsonToken t = current;
		// We observe the opening of a mapping/object in the JSON data
		if (t == JsonToken.START_OBJECT) {
			if (selection.hasChildren()) {
				// we will remove visited keys from selectedFields, and we could check that we visited every node at the end
				Set<String> selectedFields = new LinkedHashSet<String>(
						selection.getChildren().keySet());
				long n_elements = 0;
				while (true) {
					t = jts.nextToken();
					n_elements++;
					if (t == JsonToken.END_OBJECT) {
						break;
					}
					//should never happen since the data has been type checked
					if (t != JsonToken.FIELD_NAME)
						throw new TypedObjectExtractionException(
								"Error parsing json format: " + t.asString());
					final String fieldName = jts.getText();
					t = jts.nextToken();
					if (selectedFields.contains(fieldName)) {
						final MetadataNode child =
								selection.getChild(fieldName);
						path.add(fieldName);
						extractFieldsWithOpenToken(
								jts, t, child, metadataHandler, path);
						path.remove(path.size() - 1);
						selectedFields.remove(fieldName);
					} else {
						// otherwise we skip value following after field
						skipChildren(jts, t);
					}
				}
				addLengthMetadata(n_elements, selection, metadataHandler);
				
				// note: fields can be optional, so if we did not visit a selected field, it is just left out of the metadata
				// we do not need to check here to see if there are paths we did not visit
			} 

			// otherwise we need (at most) just the length metadata field
			else {
				// there are no children so the only thing possible is getting metadata
				final long n_elements = countElementsInCurrent(jts, t);
				addLengthMetadata(n_elements, selection, metadataHandler);
			}
		}

		// We observe an array/list starting in the real JSON data
		else if (t == JsonToken.START_ARRAY) {
			if (selection.hasChildren()) { // no metadata from arrays allowed
				//This code should never run in a normal case, checked by type comp
				Set<String> selectedFields = selection.getChildren().keySet();
				throw new TypedObjectExtractionException(
						"Cannot extract metadata from an array. Requested "
								+ "fields are (" + selectedFields + ") at " +
								SubdataExtractor.getPathText(path));
			} else { //count the array length
				final long n_elements = countElementsInCurrent(jts, t);
				addLengthMetadata(n_elements, selection, metadataHandler);
			}
		} else { // we observe scalar value (text, integer, double, boolean, null) in real json data
			if (selection.hasChildren()) {
				//This code should never run in a normal case, checked by type comp
				throw new TypedObjectExtractionException(
						"WS metadata path contains non-empty level for " +
						"scalar value at " +
						SubdataExtractor.getPathText(path));
			}
			// first handle the length of metadata extraction
			if (t == JsonToken.VALUE_STRING) { // if a string, add the length to metadata if it was selected
				addLengthMetadata(jts.getText().length(), selection,
						metadataHandler);
			} else if (t == JsonToken.VALUE_NULL) {
				// value is null, but we should still add it so that the metadata is not just completely missing
				addNullLengthMetadata(selection, metadataHandler);
			} else if (!selection.getNeedLengthForMetadata().isEmpty()) {
				// if we got here, then the value is not a string and is not null, so this is not valid (although
				// this should be caught as an error during type registration if using this lib with the workspace)
					throw new TypedObjectExtractionException(
							"Metadata path contains length() method called " +
							"on a scalar value at " +
							SubdataExtractor.getPathText(path));
			}
			
			final String metadataValue = jts.getText();
			addValueMetadata(metadataValue, selection, metadataHandler);
		}
	}
}
