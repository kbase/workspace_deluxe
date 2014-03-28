package us.kbase.typedobj.core;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;

import us.kbase.common.service.JsonTokenStream;

/**
 * This class lets you to check sorting order of keys in maps.
 * @author rsutormin
 */
public class SortCheckingTokenSequenceProvider implements TokenSequenceProvider {
	// jts provides tokens of real json data we would like to check of sorted keys,
	private JsonTokenStream jts;
	// previous key name is compared to current key in order to find are they sorted
	private String prevFieldName = null;
	// sorted flag is switched into false after first occurrence of unsroted keys
	private boolean sorted = true;
	
	public SortCheckingTokenSequenceProvider(JsonTokenStream jts) {
		this.jts = jts;
	}
	
	public boolean isSorted() {
		return sorted;
	}
	
	@Override
	public JsonToken nextToken() throws IOException, JsonParseException {
		JsonToken t = jts.nextToken();
		if (t == JsonToken.START_OBJECT) {
			prevFieldName = null;
		} else if (t == JsonToken.FIELD_NAME) {
			// get real name of key after relabeling
			String curFieldName = getText();
			// check for sorting order
			if (prevFieldName != null && prevFieldName.compareTo(curFieldName) >= 0) {
				sorted = false;
			}
			prevFieldName = curFieldName;
		}
		return t;
	}

	@Override
	public String getText() throws IOException, JsonParseException {
		return jts.getText();
	}

	@Override
	public Number getNumberValue() throws IOException, JsonParseException {
		return jts.getNumberValue();
	}
	
	@Override
	public void close() throws IOException {
		jts.close();
	}
}
