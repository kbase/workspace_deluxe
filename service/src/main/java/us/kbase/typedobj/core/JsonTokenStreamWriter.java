package us.kbase.typedobj.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;

/**
 * This class is a pipe between TokenSequenceProvider as a source of tokens and
 * JsonGenerator as a target.
 * @author rsutormin
 */
public class JsonTokenStreamWriter {
	
	public void writeTokens(TokenSequenceProvider src, 
			JsonGenerator jgen) throws IOException {
		JsonToken t = writeNextToken(src, jgen);
		writeTokensWithoutFirst(src, t, jgen);
	}

	private void writeTokensWithoutFirst(TokenSequenceProvider src, 
			JsonToken currentToken, JsonGenerator jgen) throws IOException {
		JsonToken t = currentToken;
		if (t == JsonToken.START_OBJECT) {
			while (true) {
				if (src.isComplete()) {
					return;
				}
				t = writeNextToken(src, jgen);
				if (t == JsonToken.END_OBJECT) {
					break;
				}
				t = writeNextToken(src, jgen);
				writeTokensWithoutFirst(src, t, jgen);
			}
		} else if (t == JsonToken.START_ARRAY) {
			while (true) {
				if (src.isComplete()) {
					return;
				}
				t = writeNextToken(src, jgen);
				if (t == JsonToken.END_ARRAY) {
					break;
				}
				writeTokensWithoutFirst(src, t, jgen);
			}
		}
	}
	
	private JsonToken writeNextToken(TokenSequenceProvider src, 
			JsonGenerator jgen) throws IOException {
		JsonToken t = src.nextToken();
		if (src.isComplete() || jgen == null)
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
			Number value = src.getNumberValue();
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
			Number value = src.getNumberValue();
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
