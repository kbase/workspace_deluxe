package us.kbase.typedobj.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;

import us.kbase.common.service.JsonTokenStream;

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
	private List<Object> path = new ArrayList<Object>();
	// refPath reflects path as long as this path exists inside id-reference schema 
	// tree with root stored in refPath[0]
	private List<IdRefNode> refPath;
	// internal flag helping to chose between relabeling rules for keys and values
	private boolean wasField = false;
	// previous key name is compared to current key in order to find are they sorted
	private String prevFieldName = null;
	// sorted flag is switched into false after first occurrence of unsroted keys
	private boolean sorted = true;
	
	public IdRefTokenSequenceProvider(JsonTokenStream jts, IdRefNode idRefTree, 
			Map<String, String> absoluteIdRefMapping) {
		this.jts = jts;
		this.absoluteIdRefMapping = absoluteIdRefMapping;
		// we put root of id-reference schema tree as first element of path, path
		// should contain it until the whole json data token sequence is processed
		refPath = new ArrayList<IdRefNode>(Arrays.asList(idRefTree));
	}
	
	public boolean isSorted() {
		return sorted;
	}
	
	@Override
	public JsonToken nextToken() throws IOException, JsonParseException {
		// This is central method processing tokens one by one, substituting id-refs
		// and tracking current json path and refPath branch in id-ref tree.
		wasField = false;
		JsonToken t = jts.nextToken();
		if (t == JsonToken.START_OBJECT) {
			incrementArrayPosIfInArray();
			path.add("{");			// next level
			prevFieldName = null;
		} else if (t == JsonToken.START_ARRAY) {
			incrementArrayPosIfInArray();
			path.add(-1);			// next level
		} else if (t == JsonToken.END_OBJECT || t == JsonToken.END_ARRAY) {
			// these tokens that can not be first of some scalar or object and it means 
			// we don't need to call incrementArrayPosIfInArray().
			while (refPath.size() > path.size())
				refPath.remove(refPath.size() - 1);
			path.remove(path.size() - 1);	// prev. level
		} else if (t == JsonToken.FIELD_NAME) {
			// this token that can not be first of some scalar or object and it means 
			// we don't need to call incrementArrayPosIfInArray().
			// we change last path element into new field
			setCurrentLevel(jts.getText());
			wasField = true;
			// get real name of key after relabeling
			String curFieldName = getText();
			// check for sorting order
			if (prevFieldName != null && prevFieldName.compareTo(curFieldName) >= 0) {
				sorted = false;
			}
			prevFieldName = curFieldName;
		} else {
			incrementArrayPosIfInArray();
		}
		return t;
	}

	@Override
	public String getText() throws IOException, JsonParseException {
		// This method is called for text keys and text values. We can differentiate 
		// these cases based on wasField flag.
		String ret = jts.getText();
		if (refPath.size() == path.size() + 1) {
			IdRefNode node = refPath.get(path.size());
			String ref = wasField ? node.getParentKeyRef() : node.getScalarValueRef();
			if (ref != null) {
				String subst = absoluteIdRefMapping.get(ret);
				if (ref.equals(ret)) {
					if (subst == null)
						throw new IllegalStateException("Id was not found: " + ret);
					return subst;
				} else {
					throw new IllegalStateException("Id ref subst internal error: ref.id=" + ref + ", actual id=" + ret);
				}
			}
		}
		return ret;
	}

	@Override
	public long getLongValue() throws IOException, JsonParseException {
		return jts.getLongValue();
	}

	@Override
	public double getDoubleValue() throws IOException, JsonParseException {
		return jts.getDoubleValue();
	}

	@Override
	public Number getNumberValue() throws IOException, JsonParseException {
		return jts.getNumberValue();
	}
	
	/*
	 * This method change current place pointing by path. We need to synchronize
	 * refPath pointing into id-ref relabeling tree according to path. If there
	 * is no such branch in this tree then we don't need to do anything.
	 */
	private void setCurrentLevel(Object value) {
		path.set(path.size() - 1, value);
		while (refPath.size() > path.size())
			refPath.remove(refPath.size() - 1);
		if (refPath.size() == path.size()) {
			IdRefNode refNode = refPath.get(refPath.size() - 1);
			if (refNode.getChildren() != null) {
				String text = "" + value;
				IdRefNode child = refNode.getChildren().get(text);
				if (child != null)
					refPath.add(child);
			}
		}
	}

	/*
	 * This method detects are we in array or not by type of last item in path.
	 * If it's number then we are in array and should increment it otherwise 
	 * it's text of key in object and we don't do anything. If we change 
	 * position in array we need to synchronize refPath as well.
	 */
	private void incrementArrayPosIfInArray() {
		if (path.size() > 0) {
			Object obj = path.get(path.size() - 1);
			if (obj instanceof Integer) {
				int pos = (Integer)obj;
				setCurrentLevel(pos + 1);
			}
		}
	}
	
	@Override
	public void close() throws IOException {
		jts.close();
	}
}
