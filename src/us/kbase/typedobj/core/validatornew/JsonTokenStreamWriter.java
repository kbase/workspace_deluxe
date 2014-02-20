package us.kbase.typedobj.core.validatornew;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;

public class JsonTokenStreamWriter {
	
	public void writeTokens(TokenSequenceProvider src, JsonGenerator jgen) throws IOException {
		JsonToken t = writeNextToken(src, jgen);
		writeTokensWithoutFirst(src, t, jgen);
	}

	private void writeTokensWithoutFirst(TokenSequenceProvider src, JsonToken currentToken, JsonGenerator jgen) throws IOException {
		JsonToken t = currentToken;
		if (t == JsonToken.START_OBJECT) {
			while (true) {
				t = writeNextToken(src, jgen);
				if (t == JsonToken.END_OBJECT) {
					break;
				}
				t = writeNextToken(src, jgen);
				writeTokensWithoutFirst(src, t, jgen);
			}
		} else if (t == JsonToken.START_ARRAY) {
			while (true) {
				t = writeNextToken(src, jgen);
				if (t == JsonToken.END_ARRAY)
					break;
				writeTokensWithoutFirst(src, t, jgen);
			}
		}
	}
	
	private JsonToken writeNextToken(TokenSequenceProvider src, JsonGenerator jgen) throws IOException {
		JsonToken t = src.nextToken();
		if (jgen == null)
			return t;
		if (t == JsonToken.START_ARRAY) {
			jgen.writeStartArray();
		} else if (t == JsonToken.START_OBJECT) {
			jgen.writeStartObject();
		} else if (t == JsonToken.END_ARRAY) {
			jgen.writeEndArray();
		} else if (t == JsonToken.END_OBJECT) {
			jgen.writeEndObject();
		} else if (t == JsonToken.FIELD_NAME) {
			jgen.writeFieldName(src.getText());
		} else if (t == JsonToken.VALUE_NUMBER_INT) {
			jgen.writeNumber(src.getLongValue());
		} else if (t == JsonToken.VALUE_NUMBER_FLOAT) {
			jgen.writeNumber(src.getDoubleValue());
		} else if (t == JsonToken.VALUE_STRING) {
			String text = src.getText();
			jgen.writeString(text);
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
}
