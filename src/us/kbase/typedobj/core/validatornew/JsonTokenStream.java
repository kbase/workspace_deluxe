package us.kbase.typedobj.core.validatornew;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;

public class JsonTokenStream extends JsonParser {
	private String sdata = null;
	private byte[] bdata = null;
	private File fdata = null;
	private JsonParser super2;
	private List<Object> path = new ArrayList<Object>();
	private int fixedLevels = 0;
	private boolean currentTokenIsNull = false;
	
	private static final boolean debug = true;
	
	public JsonTokenStream(String data) throws JsonParseException, IOException {
		sdata = data;
		init(null);
	}

	public JsonTokenStream(byte[] data) throws JsonParseException, IOException {
		bdata = data;
		init(null);
	}

	public JsonTokenStream(File data) throws JsonParseException, IOException {
		fdata = data;
		init(null);
	}

	public JsonTokenStream setRoot(String root) throws JsonParseException, IOException {
		if (root.isEmpty()) {
			init(null);
		} else {
			init(Arrays.asList(root.split("/")));
		}
		return this;
	}
	
	public String getCurrentPath() throws IOException {
		if (getCurrentToken() == null)
			nextToken();
		StringBuilder ret = new StringBuilder();
		int size = path.size() - 1;
		for (int i = 0; i < size; i++) {
			Object item = path.get(i);
			if (ret.length() > 0)
				ret.append('/');
			ret.append(String.valueOf(item));
		}
		return ret.toString();
	}
	
	private void init(List<String> root) throws JsonParseException, IOException {
		path = new ArrayList<Object>();
		fixedLevels = 0;
		currentTokenIsNull = false;
		JsonFactory jf = new JsonFactory();
		super2 = sdata != null ? jf.createParser(sdata) :
				(bdata == null ? jf.createParser(fdata) : jf.createParser(bdata));
		if (root != null && root.size() > 0) {
			int pos = -1;
			while (true) {
				if (nextToken() == null)
					throw new IllegalStateException("End of token stream for root path: " + root);
				if (!eq(root, pos))
					throw new IllegalStateException("Root path not found: " + root);
				if (eq(root, pos + 1)) {
					pos++;
					if (pos + 1 == root.size())
						break;
				}
			}
			if (path.size() != root.size())
				throw new IllegalStateException("Unexpected path length: " + path);
			fixedLevels = root.size();
			currentTokenIsNull = true;
		}
		System.out.println("end of init");
	}
	
	private boolean eq(List<String> root, int pos) {
		if (pos < 0)
			return true;
		if (pos >= path.size())
			return false;
		return String.valueOf(path.get(pos)).equals(root.get(pos));
	}
	
	private void debug() {
		StackTraceElement el = Thread.currentThread().getStackTrace()[2];
		try {
			System.out.println("Calling JsonTokenStream." + el.getMethodName());
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
	
	@Override
	public void clearCurrentToken() {
		if (debug) debug();
		super2.clearCurrentToken();
	}
	
	@Override
	public void close() throws IOException {
		if (debug) debug();
		super2.close();
	}
	
	@Override
	public BigInteger getBigIntegerValue() throws IOException,
			JsonParseException {
		if (debug) debug();
		return super2.getBigIntegerValue();
	}
	
	@Override
	public byte[] getBinaryValue(Base64Variant arg0) throws IOException,
			JsonParseException {
		if (debug) debug();
		return super2.getBinaryValue(arg0);
	}
	
	@Override
	public ObjectCodec getCodec() {
		if (debug) debug();
		return super2.getCodec();
	}
	
	@Override
	public JsonLocation getCurrentLocation() {
		if (debug) debug();
		return super2.getCurrentLocation();
	}
	
	@Override
	public String getCurrentName() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getCurrentName();
	}
	
	@Override
	public JsonToken getCurrentToken() {
		if (debug) debug();
		if (currentTokenIsNull) {
			return null;
		}
		return super2.getCurrentToken();
	}
	
	@Override
	public BigDecimal getDecimalValue() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getDecimalValue();
	}
	
	@Override
	public double getDoubleValue() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getDoubleValue();
	}
	
	@Override
	public Object getEmbeddedObject() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getEmbeddedObject();
	}
	
	@Override
	public float getFloatValue() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getFloatValue();
	}
	
	@Override
	public int getIntValue() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getIntValue();
	}
	
	@Override
	public JsonToken getLastClearedToken() {
		if (debug) debug();
		return super2.getLastClearedToken();
	}
	
	@Override
	public long getLongValue() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getLongValue();
	}
	
	@Override
	public NumberType getNumberType() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getNumberType();
	}
	
	@Override
	public Number getNumberValue() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getNumberValue();
	}
	
	@Override
	public JsonStreamContext getParsingContext() {
		if (debug) debug();
		return super2.getParsingContext();
	}
	
	@Override
	public String getText() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getText();
	}
	
	@Override
	public char[] getTextCharacters() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getTextCharacters();
	}
	
	@Override
	public int getTextLength() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getTextLength();
	}
	
	@Override
	public int getTextOffset() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getTextOffset();
	}
	
	@Override
	public JsonLocation getTokenLocation() {
		if (debug) debug();
		return super2.getTokenLocation();
	}
	
	@Override
	public String getValueAsString(String arg0) throws IOException,
			JsonParseException {
		if (debug) debug();
		return super2.getValueAsString(arg0);
	}
	
	@Override
	public boolean hasCurrentToken() {
		if (debug) debug();
		return super2.hasCurrentToken();
	}
	
	@Override
	public boolean hasTextCharacters() {
		if (debug) debug();
		return super2.hasTextCharacters();
	}
	
	@Override
	public boolean isClosed() {
		if (debug) debug();
		return super2.isClosed();
	}
	
	@Override
	public JsonToken nextToken() throws IOException, JsonParseException {
		if (debug) debug();
		currentTokenIsNull = false;
		JsonToken ret = super2.nextToken();
		int lastPos = path.size() - 1;
		if (ret == JsonToken.START_ARRAY) {
			path.add(0);
		} else if (ret == JsonToken.END_ARRAY) {
			path.remove(lastPos);
			lastPos--;
		} else if (ret == JsonToken.START_OBJECT) {
			path.add("{");
		} else if (ret == JsonToken.END_OBJECT) {
			path.remove(lastPos);
			lastPos--;
			if (fixedLevels > 0 && path.size() == fixedLevels) {
				super2.close();
			} else if (lastPos >= 0) {
				Object obj = path.get(lastPos);
				if (obj instanceof Integer) 
					path.set(lastPos, (Integer)obj + 1);
			}
		} else if (ret == JsonToken.FIELD_NAME) {
			path.set(lastPos, super2.getText());
		} else {
			if (lastPos >= 0) {
				Object obj = path.get(lastPos);
				if (obj instanceof Integer) 
					path.set(lastPos, (Integer)obj + 1);
			}
		}
		if (fixedLevels > 0 && path.size() < fixedLevels) {
			super2.close();
			ret = null;
		}
		System.out.println("Token: " + ret + ", path: " + path);
		return ret;
	}
	
	@Override
	public JsonToken nextValue() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.nextValue();
	}
	
	@Override
	public void overrideCurrentName(String arg0) {
		if (debug) debug();
		super2.overrideCurrentName(arg0);
	}
	
	@Override
	public void setCodec(ObjectCodec arg0) {
		if (debug) debug();
		super2.setCodec(arg0);
	}
	
	@Override
	public JsonParser skipChildren() throws IOException, JsonParseException {
		if (debug) debug();
		JsonToken t = getCurrentToken();
		if (t == JsonToken.START_OBJECT) {
			while (true) {
				t = nextToken();
				if (t == JsonToken.END_OBJECT) {
					break;
				}
				nextToken();
				skipChildren();
			}
		} else if (t == JsonToken.START_ARRAY) {
			while (true) {
				t = nextToken();
				if (t == JsonToken.END_ARRAY)
					break;
				skipChildren();
			}
		}
		return this;
	}
	
	@Override
	public Version version() {
		if (debug) debug();
		return super2.version();
	}

	@Override
	public boolean canUseSchema(FormatSchema arg0) {
		if (debug) debug();
		return super2.canUseSchema(arg0);
	}
	
	@Override
	public JsonParser configure(Feature arg0, boolean arg1) {
		if (debug) debug();
		return super2.configure(arg0, arg1);
	}
	
	@Override
	public JsonParser disable(Feature arg0) {
		if (debug) debug();
		return super2.disable(arg0);
	}
	
	@Override
	public JsonParser enable(Feature arg0) {
		if (debug) debug();
		return super2.enable(arg0);
	}
	
	@Override
	public byte[] getBinaryValue() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getBinaryValue();
	}
	
	@Override
	public boolean getBooleanValue() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getBooleanValue();
	}
	
	@Override
	public byte getByteValue() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getByteValue();
	}
	
	@Override
	public Object getInputSource() {
		if (debug) debug();
		return super2.getInputSource();
	}
	
	@Override
	public FormatSchema getSchema() {
		if (debug) debug();
		return super2.getSchema();
	}
	
	@Override
	public short getShortValue() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getShortValue();
	}
	
	@Override
	public boolean getValueAsBoolean() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getValueAsBoolean();
	}
	
	@Override
	public boolean getValueAsBoolean(boolean arg0) throws IOException,
			JsonParseException {
		if (debug) debug();
		return super2.getValueAsBoolean(arg0);
	}
	
	@Override
	public double getValueAsDouble() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getValueAsDouble();
	}
	
	@Override
	public double getValueAsDouble(double arg0) throws IOException,
			JsonParseException {
		if (debug) debug();
		return super2.getValueAsDouble(arg0);
	}
	
	@Override
	public int getValueAsInt() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getValueAsInt();
	}
	
	@Override
	public int getValueAsInt(int arg0) throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getValueAsInt(arg0);
	}
	
	@Override
	public long getValueAsLong() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getValueAsLong();
	}
	
	@Override
	public long getValueAsLong(long arg0) throws IOException,
			JsonParseException {
		if (debug) debug();
		return super2.getValueAsLong(arg0);
	}
	
	@Override
	public String getValueAsString() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.getValueAsString();
	}
	
	@Override
	public boolean isEnabled(Feature arg0) {
		if (debug) debug();
		return super2.isEnabled(arg0);
	}
	
	@Override
	public boolean isExpectedStartArrayToken() {
		if (debug) debug();
		return super2.isExpectedStartArrayToken();
	}
	
	@Override
	public Boolean nextBooleanValue() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.nextBooleanValue();
	}
	
	@Override
	public boolean nextFieldName(SerializableString arg0) throws IOException,
			JsonParseException {
		if (debug) debug();
		return super2.nextFieldName(arg0);
	}
	
	@Override
	public int nextIntValue(int arg0) throws IOException, JsonParseException {
		if (debug) debug();
		return super2.nextIntValue(arg0);
	}
	
	@Override
	public long nextLongValue(long arg0) throws IOException, JsonParseException {
		if (debug) debug();
		return super2.nextLongValue(arg0);
	}
	
	@Override
	public String nextTextValue() throws IOException, JsonParseException {
		if (debug) debug();
		return super2.nextTextValue();
	}
	
	@Override
	public int readBinaryValue(Base64Variant arg0, OutputStream arg1)
			throws IOException, JsonParseException {
		if (debug) debug();
		return super2.readBinaryValue(arg0, arg1);
	}
	
	@Override
	public int readBinaryValue(OutputStream arg0) throws IOException,
			JsonParseException {
		if (debug) debug();
		return super2.readBinaryValue(arg0);
	}
	
	@Override
	public <T> T readValueAs(Class<T> arg0) throws IOException,
			JsonProcessingException {
		if (debug) debug();
        ObjectCodec codec = getCodec();
        if (codec == null) {
            throw new IllegalStateException("No ObjectCodec defined for the parser, can not deserialize JSON into Java objects");
        }
        return codec.readValue(this, arg0);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <T> T readValueAs(TypeReference<?> arg0) throws IOException,
			JsonProcessingException {
		if (debug) debug();
        ObjectCodec codec = getCodec();
        if (codec == null) {
            throw new IllegalStateException("No ObjectCodec defined for the parser, can not deserialize JSON into Java objects");
        }
        return (T) codec.readValue(this, arg0);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <T extends TreeNode> T readValueAsTree() throws IOException,
			JsonProcessingException {
		if (debug) debug();
        ObjectCodec codec = getCodec();
        if (codec == null) {
            throw new IllegalStateException("No ObjectCodec defined for the parser, can not deserialize JSON into JsonNode tree");
        }
        return (T) codec.readTree(this);
	}
	
	@Override
	public <T> Iterator<T> readValuesAs(Class<T> arg0) throws IOException,
			JsonProcessingException {
		if (debug) debug();
        ObjectCodec codec = getCodec();
        if (codec == null) {
            throw new IllegalStateException("No ObjectCodec defined for the parser, can not deserialize JSON into Java objects");
        }
        return codec.readValues(this, arg0);
	}
	
	@Override
	public <T> Iterator<T> readValuesAs(TypeReference<?> arg0)
			throws IOException, JsonProcessingException {
		if (debug) debug();
        ObjectCodec codec = getCodec();
        if (codec == null) {
            throw new IllegalStateException("No ObjectCodec defined for the parser, can not deserialize JSON into Java objects");
        }
        return codec.readValues(this, arg0);
	}
	
	@Override
	public int releaseBuffered(OutputStream arg0) throws IOException {
		if (debug) debug();
		return super2.releaseBuffered(arg0);
	}
	
	@Override
	public int releaseBuffered(Writer arg0) throws IOException {
		if (debug) debug();
		return super2.releaseBuffered(arg0);
	}
	
	@Override
	public boolean requiresCustomCodec() {
		if (debug) debug();
		return super2.requiresCustomCodec();
	}
	
	@Override
	public void setSchema(FormatSchema arg0) {
		if (debug) debug();
		super2.setSchema(arg0);
	}
	
	public void writeTokens(JsonGenerator jgen) throws IOException {
		writeNextToken(jgen);
		writeTokensWithoutFirst(jgen);
	}
	
	public void writeJson(OutputStream os) throws IOException {
		JsonFactory jf = new JsonFactory();
		JsonGenerator jgen = jf.createGenerator(os, JsonEncoding.UTF8);
		writeTokens(jgen);
		jgen.flush();
	}
	
	private void writeTokensWithoutFirst(JsonGenerator jgen) throws IOException {
		JsonToken t = getCurrentToken();
		if (t == JsonToken.START_OBJECT) {
			while (true) {
				t = writeNextToken(jgen);
				if (t == JsonToken.END_OBJECT) {
					break;
				}
				writeNextToken(jgen);
				writeTokensWithoutFirst(jgen);
			}
		} else if (t == JsonToken.START_ARRAY) {
			while (true) {
				t = writeNextToken(jgen);
				if (t == JsonToken.END_ARRAY)
					break;
				writeTokensWithoutFirst(jgen);
			}
		}
	}
	
	private JsonToken writeNextToken(JsonGenerator jgen) throws IOException {
		JsonToken t = nextToken();
		if (t == JsonToken.START_ARRAY) {
			jgen.writeStartArray();
		} else if (t == JsonToken.START_OBJECT) {
			jgen.writeStartObject();
		} else if (t == JsonToken.END_ARRAY) {
			jgen.writeEndArray();
		} else if (t == JsonToken.END_OBJECT) {
			jgen.writeEndObject();
		} else if (t == JsonToken.FIELD_NAME) {
			jgen.writeFieldName(getText());
		} else if (t == JsonToken.VALUE_NUMBER_INT) {
			jgen.writeNumber(getIntValue());
		} else if (t == JsonToken.VALUE_NUMBER_FLOAT) {
			jgen.writeNumber(getDoubleValue());
		} else if (t == JsonToken.VALUE_STRING) {
			jgen.writeString(getText());
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
