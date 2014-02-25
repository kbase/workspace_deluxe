package us.kbase.typedobj.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.typedobj.idref.IdReference;

/**
 * This class lets you to substitute id references into text tokens (string 
 * keys and values) on the fly during json token stream reading.
 * @author rsutormin
 */
public class IdRefTokenSequenceProvider implements TokenSequenceProvider {
	private JsonTokenStream jts;
	private Map<String, String> absoluteIdRefMapping;
	private List<Object> path = new ArrayList<Object>();
	private List<IdRefNode> refPath;
	private boolean wasField = false;
	private String prevFieldName = null;
	private boolean sorted = true;
	
	public IdRefTokenSequenceProvider(JsonTokenStream jts, IdRefNode idRefTree, 
			Map<String, String> absoluteIdRefMapping) {
		this.jts = jts;
		this.absoluteIdRefMapping = absoluteIdRefMapping;
		refPath = new ArrayList<IdRefNode>(Arrays.asList(idRefTree));
	}
	
	public boolean isSorted() {
		return sorted;
	}
	
	@Override
	public JsonToken nextToken() throws IOException, JsonParseException {
		wasField = false;
		JsonToken t = jts.nextToken();
		if (t == JsonToken.START_OBJECT) {
			incrementArrayPos();
			path.add("{");			// next level
			prevFieldName = null;
		} else if (t == JsonToken.START_ARRAY) {
			incrementArrayPos();
			path.add(-1);			// next level
		} else if (t == JsonToken.END_OBJECT || t == JsonToken.END_ARRAY) {
			while (refPath.size() > path.size())
				refPath.remove(refPath.size() - 1);
			path.remove(path.size() - 1);
		} else if (t == JsonToken.FIELD_NAME) {
			setCurrentLevel(jts.getText());
			wasField = true;
			String curFieldName = getText();
			if (prevFieldName != null && prevFieldName.compareTo(curFieldName) >= 0) {
				sorted = false;
			}
			prevFieldName = curFieldName;
		} else {
			incrementArrayPos();
		}
		return t;
	}

	@Override
	public String getText() throws IOException, JsonParseException {
		String ret = jts.getText();
		if (refPath.size() == path.size() + 1) {
			IdRefNode node = refPath.get(path.size());
			IdReference ref = wasField ? node.getParentKeyRef() : node.getScalarValueRef();
			if (ref != null) {
				String subst = absoluteIdRefMapping.get(ret);
				if (ref.getId().equals(ret)) {
					if (subst == null)
						throw new IllegalStateException("Id was not found: " + ret);
					return subst;
				} else {
					throw new IllegalStateException("Id ref subst internal error: ref.id=" + ref.getId() + ", actual id=" + ret);
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

	private void incrementArrayPos() {
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
