package us.kbase.typedobj.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.typedobj.core.JsonDocumentLocation.JsonArrayLocation;
import us.kbase.typedobj.core.JsonDocumentLocation.JsonLocation;

/**
 * This class lets you to substitute id references into text tokens (string 
 * keys and values) on the fly during json token stream reading. It checks
 * sorting order also.
 * @author rsutormin
 */
public class IdRefTokenSequenceProvider implements TokenSequenceProvider {
	// jts provides tokens of real json data we would like to relabel id-refs in,
	// relabeling is just substituting tokens with old id-ref values by new ones
	private JsonTokenStream jts;
	// info about mapping old id-refs into new ones taken from workspace db based
	// on list of old id-refs created on validation stage
	private Map<String, String> absoluteIdRefMapping;
	// path is branch in real json data pointing to position of currently observed
	// token in jts
	private JsonDocumentLocation path = new JsonDocumentLocation();
	// the path into the schema for the object into which we're relabeling IDs.
	private List<JsonTokenValidationSchema> schemaLoc;
	// internal flags helping to chose between relabeling rules for keys and values
	private boolean wasField = false;
	private boolean wasValue = false;
	// previous key name is compared to current key in order to find are they sorted
	private String prevFieldName = null;
	// sorted flag is switched into false after first occurrence of unsorted keys
	private boolean sorted = true;
	
	public IdRefTokenSequenceProvider(final JsonTokenStream jts,
			final JsonTokenValidationSchema schema, 
			final Map<String, String> absoluteIdRefMapping) {
		this.jts = jts;
		this.absoluteIdRefMapping = absoluteIdRefMapping;
		this.schemaLoc = new ArrayList<JsonTokenValidationSchema>(
				Arrays.asList(schema));
		// we put root of id-reference schema tree as first element of path, path
		// should contain it until the whole json data token sequence is processed
//		refPath = new ArrayList<IdRefNode>();
	}
	
	public boolean isSorted() {
		return sorted;
	}
	
	@Override
	public JsonToken nextToken() throws IOException, JsonParseException {
		// This is central method processing tokens one by one, substituting id-refs
		// and tracking current json path and refPath branch in id-ref tree.
		wasField = false;
		if (wasValue) {
			removeLastSchemaLocation();
		}
		wasValue = false;
		JsonToken t = jts.nextToken();
		if (t == JsonToken.START_OBJECT) {
			incrementArrayPosAndUpdateSchemaIfInArray();
			path.addMapStart();
			prevFieldName = null;
		} else if (t == JsonToken.START_ARRAY) {
			incrementArrayPosAndUpdateSchemaIfInArray();
			path.addArrayStart();
		} else if (t == JsonToken.END_OBJECT || t == JsonToken.END_ARRAY) {
			path.removeLast();
			removeLastSchemaLocation();
		} else if (t == JsonToken.FIELD_NAME) {
			// this token that can not be first of some scalar or object and it means 
			// we don't need to call incrementArrayPosIfInArray().
			// we change last path element into new field
			path.replaceLast(jts.getText());
			updateSchemaLocation();
			wasField = true;
			// get real name of key after relabeling
			String curFieldName = getText();
			// check for sorting order
			if (prevFieldName != null && prevFieldName.compareTo(curFieldName) >= 0) {
				sorted = false;
			}
			prevFieldName = curFieldName;
		} else {
			wasValue = true;
			incrementArrayPosAndUpdateSchemaIfInArray();
		}
		return t;
	}
	
	private JsonTokenValidationSchema getCurrentSchema() {
		return schemaLoc.get(schemaLoc.size() - 1);
	}
	
	private JsonTokenValidationSchema getPreviousSchema() {
		return schemaLoc.get(schemaLoc.size() - 2);
	}

	private void removeLastSchemaLocation() {
		schemaLoc.remove(schemaLoc.size() - 1);
	}

	private void updateSchemaLocation() {
		final JsonLocation jl = path.getLast();
		final JsonTokenValidationSchema current = getCurrentSchema();
		if (current == null) { //no type checking in this part of the object
			schemaLoc.add(null);
			return;
		}
		if (jl.isMapLocation()) {
			final String field = jl.getLocationAsString();
			schemaLoc.add(current.getChild(field));
		} else if (jl.isArrayLocation()) {
			final int index = ((JsonArrayLocation) jl).getLocationAsInt();
			schemaLoc.add(current.getArraySchema(index));
		}
	}

	@Override
	public String getText() throws IOException, JsonParseException {
		final String ret = jts.getText();
		final JsonTokenValidationSchema s;
		if (wasField) {
			s = getPreviousSchema();
		} else {
			s = getCurrentSchema();
		}
		if (s != null && s.hasIdReference()) {
			final String subst = absoluteIdRefMapping.get(ret);
			if (subst == null) {
				throw new IllegalStateException(String.format(
						"Tried to remap id %s but no remapping found at %s",
						ret, path.getFullLocationAsString()));
			}
			return subst;
		}
		return ret;
	}

	@Override
	public Number getNumberValue() throws IOException, JsonParseException {
		return jts.getNumberValue();
	}
	
	private void incrementArrayPosAndUpdateSchemaIfInArray() {
		if (path.getDepth() > 0 && path.getLast().isArrayLocation()) {
			path.incrementArrayLocation();
			updateSchemaLocation();
		}
	}
	
	@Override
	public void close() throws IOException {
		jts.close();
	}
}
