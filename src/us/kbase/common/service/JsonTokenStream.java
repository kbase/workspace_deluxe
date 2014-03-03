package us.kbase.common.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.FormatSchema;
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
import com.fasterxml.jackson.databind.JsonNode;

/**
 * This class gives a way to keep json data in different types of source (file, string or byte array) 
 * and to process this data through sequence of json tokens. The difference of this class comparing to
 * standard UTF8StreamJsonParser or ReaderBasedJsonParser is that here we can deal with large text
 * values since they are processed as whole tokens in standard jackson approach. Here we substitute
 * these large text values by special keywords storing mapping from these keywords to position of real
 * text in character stream. This mapping is stored in largeStringPos. For that we wrap input reader
 * before parsing it into tokens by wrapper searching these large texts (see getWrapperForLargeStrings
 * method for Reader for details). And during writing operation we wrap output writer by wrapper 
 * substituting these keywords back into large strings (see getWrapperForLargeStrings for Writer).
 * For searching large string by stored position LargeStringSearchingReader is used by calling
 * getLargeStringReader method.
 * Another useful feature which is not present in standard parsers is tracking current json path. Even
 * more, you can define json path as a root point for your token stream and after that this stream
 * will provide tokens only from subtree of your json data deeper than root path. This feature is
 * important for UObject creation based on fragment in bigger json data covered by JsonTokenStream.
 * @author rsutormin
 */
public class JsonTokenStream extends JsonParser {
	// string data source, only one of sdata/bdata/fdata could be not null
	private String sdata = null;
	// byte array data source, only one of sdata/bdata/fdata could be not null
	private byte[] bdata = null;
	// file data source, only one of sdata/bdata/fdata could be not null
	private File fdata = null;
	// standard jackson parser created for chosen data source
	private JsonParser inner;
	// current path following to processed token
	private List<Object> path = new ArrayList<Object>();
	// minimum number of levels, more than 0 in case root point is deeper than real root
	private int fixedLevels = 0;
	// currentTokenIsNull could be true only at the beginning of process after root point definition
	private boolean currentTokenIsNull = false;
	// main mapping for large strings found in data source, value is two-element array {start, length}
	private Map<String, long[]> largeStringPos = new LinkedHashMap<String, long[]>(); 
	// in case large string has length > this size it's processed with substitution by keyword (def=1000000)
	private final int stringBufferSize;
	// reader for large string extraction, it should be positioned by calling getLargeStringReader
	private final LargeStringSearchingReader largeStringReader = new LargeStringSearchingReader();
	
	private static final boolean debug = false;  //true;
	private static final Charset utf8 = Charset.forName("UTF-8");
	private static final String largeStringSubstPrefix = "^*->#";
	private static DebugOpenCloseListener debugOpenCloseListener = null;
	
	/**
	 * Create token stream for data source of one of the following types: File, String, byte[], JsonNode.
	 * @param data
	 * @throws JsonParseException
	 * @throws IOException
	 */
	public JsonTokenStream(Object data) throws JsonParseException, IOException {
		this(data, true);
	}

	/**
	 * Create token stream for data source of one of the following types: File, String, byte[], JsonNode.
	 * @param data data source
	 * @param optimizeLargeStrings flag for usage of large string substitutions
	 * @throws JsonParseException
	 * @throws IOException
	 */
	public JsonTokenStream(Object data, boolean optimizeLargeStrings) throws JsonParseException, IOException {
		this(data, optimizeLargeStrings ? 1000000 : 0);
	}
	
	/**
	 * Create token stream for data source of one of the following types: File, String, byte[], JsonNode.
	 * @param data data source
	 * @param largeStringbufferSize size of buffer used for large string substitutions, 0 - for switching it off
	 * @throws JsonParseException
	 * @throws IOException
	 */
	public JsonTokenStream(Object data, int largeStringbufferSize) throws JsonParseException, IOException {
		if (data instanceof String) {
			sdata = (String)data;
		} else if (data instanceof File) {
			fdata = (File)data;
		} else if (data instanceof JsonNode) {
			JsonNode jdata = (JsonNode)data;
			sdata = UObject.transformJacksonToString(jdata);
		} else {
			bdata = (byte[])data;
		}
		stringBufferSize = largeStringbufferSize;
		init(null);
	}

	/**
	 * Define root point in data source from which token stream should start.
	 * @param root list of fields or array numbers defining json path
	 * @return
	 * @throws JsonParseException
	 * @throws IOException
	 */
	public JsonTokenStream setRoot(List<String> root) throws JsonParseException, IOException {
		if (root == null || root.isEmpty()) {
			init(null);
		} else {
			init(root);
		}
		return this;
	}
	
	/**
	 * Json path before current token.
	 * @return list of fields or array numbers defining json path
	 * @throws IOException
	 */
	public List<String> getCurrentPath() throws IOException {
		JsonToken lastToken = getCurrentToken();
		if (lastToken == null)
			lastToken = nextToken();
		List<String> ret = new ArrayList<String>();
		int size = path.size() - 1;
		for (int i = 0; i < size; i++) {
			Object item = path.get(i);
			ret.add(String.valueOf(item));
		}
		if (lastToken != JsonToken.START_OBJECT && lastToken != JsonToken.START_ARRAY) {
			Object item = path.get(size);
			if (item instanceof Integer)
				item = ((Integer)item) - 1;
			ret.add(String.valueOf(item));
		}
		return ret;
	}
	
	private void init(List<String> root) throws JsonParseException, IOException {
		path = new ArrayList<Object>();
		fixedLevels = 0;
		currentTokenIsNull = false;
		if (inner != null) {
			if (!inner.isClosed())
				throw new IOException("Inner parser wasn't closed previously");
			inner = null;
		}
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
		if (debug)
			System.out.println("end of init");
	}
	
	/**
	 * Standard json parser used inside this parser.
	 * @return
	 */
	protected JsonParser getInner() {
		if (inner == null) {
			try {
				Reader r = createDataReader();
				if (debugOpenCloseListener != null)
					debugOpenCloseListener.onStreamOpen(this);
				if (stringBufferSize > 0)
					r = getWrapperForLargeStrings(r);
				inner = new JsonFactory().createParser(r);
				if (inner.getCodec() == null)
					inner.setCodec(UObject.getMapper());
			} catch (IOException ex) {
				throw new IllegalStateException(ex);
			}
		}
		return inner;
	}

	/**
	 * Create reader for data source.
	 * @return Reader
	 * @throws IOException
	 */
	public Reader createDataReader() throws IOException {
		Reader r;
		if (sdata != null) {
			r = new StringReader(sdata);
		} else if (bdata != null) {
			r = new InputStreamReader(new ByteArrayInputStream(bdata));
		} else if (fdata != null) {
			r = new InputStreamReader(new BufferedInputStream(new FileInputStream(fdata)), utf8);
		} else {
			throw new IOException("Data source was not set");
		}
		return r;
	}
	
	private Reader getWrapperForLargeStrings(final Reader r) {
		return new Reader() {
			long bufSizeGlobalPos = 0;	// reflects global position of bufSize place in input data source before substitution (array/String/file)
			boolean inQ = false;
			boolean wasBS = false;
			char[] buffer = new char[stringBufferSize];
			int bufPos = 0;			// position in buffer pointing to next character after last processed one
			int bufSize = 0;		// part of buffer filled by characters
			int maxLargeStringPosKey = 40;
			@Override
			public int read(char[] retbuf, int off, int len) throws IOException {
				int ret = 0;
				while (ret < len) {
					if (bufPos == bufSize) {
						if (bufSize == buffer.length) {
							bufPos = 0;
							bufSize = 0;
						}
						fillBuffer();
						if (bufPos == bufSize)
							break;
					}
					char ch = buffer[bufPos];
					retbuf[off + ret] = ch;
					ret++;
					//pos++;
					bufPos++;
					if (inQ) {
						if (wasBS) {
							wasBS = false;
						} else if (ch == '\\') {
							wasBS = true;
						} else if (ch == '\"') {  // Close string value
							inQ = false;
						}
					} else if (ch == '\"') {  // Open string value
						inQ = true;
						wasBS = false;
						lookup();
					}
				}
				if (ret == 0)
					return -1;
				return ret;
			}
			private void lookup() throws IOException {  // Open string value
				int internalPos = bufPos;
				boolean wasBS = false;
				long largeStringStart = -1;
				while (true) {
					if (internalPos == bufSize) {
						if (largeStringStart < 0) {
							if (internalPos == buffer.length) {
								if (bufPos > 0) {
									internalPos = repos(internalPos);
								} else {  // Here we are, all the string prefix is in our buffer and this string is going to be longer
									largeStringStart = bufSizeGlobalPos - bufSize;
									//pos += bufSize;
									bufSize = maxLargeStringPosKey;
									bufPos = bufSize;
									internalPos = bufSize;
								}
							}
							fillBuffer();
						} else {
							if (bufSize == buffer.length) {
								if (bufPos != maxLargeStringPosKey)
									throw new IllegalStateException();
								internalPos = maxLargeStringPosKey;
								//pos += (bufSize - maxLargeStringPosKey);
								bufSize = maxLargeStringPosKey;
							}
							fillBuffer();
							if (internalPos == bufSize)
								break;
						}
					}
					char ch = buffer[internalPos];
					if (wasBS) {
						wasBS = false;
					} else if (ch == '\\') {
						wasBS = true;
					} else if (ch == '\"') {  // Close string value
						break;
					}
					internalPos++;
				}
				if (largeStringStart >= 0) {
					long pos = bufSizeGlobalPos - (bufSize - internalPos);
					long largeStringLen = pos - largeStringStart;
					String key = largeStringSubstPrefix + largeStringStart + "," + largeStringLen;
					int keyLen = key.length();
					if (keyLen > maxLargeStringPosKey)
						throw new IllegalStateException("Key is too large: " + keyLen);
					largeStringPos.put(key, new long[] {largeStringStart, largeStringLen});
					System.arraycopy(key.toCharArray(), 0, buffer, 0, keyLen);
					for (int i = 0; i < bufSize - internalPos; i++)
						buffer[keyLen + i] = buffer[internalPos + i];
					bufSize = keyLen + bufSize - internalPos;
					bufPos = 0;
				}
			}
			private int repos(int internalPos) throws IOException {
				if (bufPos > 0) {
					int count = bufSize - bufPos;
					for (int i = 0; i < count; i++)
						buffer[i] = buffer[bufPos + i];
					internalPos -= bufPos;
					bufSize -= bufPos;
					bufPos = 0;
					fillBuffer();
				}
				return internalPos;
			}
			private void fillBuffer() throws IOException {
				if (bufSize < buffer.length) {
					while (true) {
						int count = r.read(buffer, bufSize, buffer.length - bufSize);
						if (count < 0)
							break;
						bufSize += count;
						bufSizeGlobalPos += count;
						if (bufSize == buffer.length)
							break;
					}
				}
			}
			@Override
			public void close() throws IOException {
				r.close();
			}
		};
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
		getInner().clearCurrentToken();
	}
	
	@Override
	public void close() throws IOException {
		if (debug) debug();
		if (inner != null && !inner.isClosed()) {
			inner.close();
			if (debugOpenCloseListener != null)
					debugOpenCloseListener.onStreamClosed(this);
		}
		largeStringReader.close();
	}
	
	@Override
	public BigInteger getBigIntegerValue() throws IOException,
			JsonParseException {
		if (debug) debug();
		return getInner().getBigIntegerValue();
	}
	
	@Override
	public byte[] getBinaryValue(Base64Variant arg0) throws IOException,
			JsonParseException {
		if (debug) debug();
		return getInner().getBinaryValue(arg0);
	}
	
	@Override
	public ObjectCodec getCodec() {
		if (debug) debug();
		return getInner().getCodec();
	}
	
	@Override
	public JsonLocation getCurrentLocation() {
		if (debug) debug();
		return getInner().getCurrentLocation();
	}
	
	@Override
	public String getCurrentName() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getCurrentName();
	}
	
	@Override
	public JsonToken getCurrentToken() {
		if (debug) debug();
		if (currentTokenIsNull) {
			return null;
		}
		return getInner().getCurrentToken();
	}
	
	@Override
	public BigDecimal getDecimalValue() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getDecimalValue();
	}
	
	@Override
	public double getDoubleValue() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getDoubleValue();
	}
	
	@Override
	public Object getEmbeddedObject() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getEmbeddedObject();
	}
	
	@Override
	public float getFloatValue() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getFloatValue();
	}
	
	@Override
	public int getIntValue() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getIntValue();
	}
	
	@Override
	public JsonToken getLastClearedToken() {
		if (debug) debug();
		return getInner().getLastClearedToken();
	}
	
	@Override
	public long getLongValue() throws IOException, JsonParseException {
		if (debug) debug();
		Number ret = getInner().getNumberValue();
		if (ret instanceof Double || ret instanceof BigDecimal)
			throw new JsonParseException("Floating point value ("+getText()+") is not compatible " +
					"with long type", getInner().getCurrentLocation());
		if (ret instanceof BigInteger) {
			BigInteger numberBigInt = (BigInteger)ret;
			BigInteger BI_MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
			BigInteger BI_MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
			if (BI_MIN_LONG.compareTo(numberBigInt) > 0 || BI_MAX_LONG.compareTo(numberBigInt) < 0)
				throw new JsonParseException("Numeric value ("+getText()+") out of range of long (" +
					Long.MIN_VALUE + " - " + Long.MAX_VALUE + ")", getInner().getCurrentLocation());
		}
		return getInner().getLongValue();
	}
	
	@Override
	public NumberType getNumberType() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getNumberType();
	}
	
	@Override
	public Number getNumberValue() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getNumberValue();
	}
	
	@Override
	public JsonStreamContext getParsingContext() {
		if (debug) debug();
		return getInner().getParsingContext();
	}
	
	@Override
	public String getText() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getText();
	}
	
	@Override
	public char[] getTextCharacters() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getTextCharacters();
	}
	
	@Override
	public int getTextLength() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getTextLength();
	}
	
	@Override
	public int getTextOffset() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getTextOffset();
	}
	
	@Override
	public JsonLocation getTokenLocation() {
		if (debug) debug();
		return getInner().getTokenLocation();
	}
	
	@Override
	public String getValueAsString(String arg0) throws IOException,
			JsonParseException {
		if (debug) debug();
		return getInner().getValueAsString(arg0);
	}
	
	@Override
	public boolean hasCurrentToken() {
		if (debug) debug();
		return getInner().hasCurrentToken();
	}
	
	@Override
	public boolean hasTextCharacters() {
		if (debug) debug();
		return getInner().hasTextCharacters();
	}
	
	@Override
	public boolean isClosed() {
		if (debug) debug();
		return inner == null || inner.isClosed();
	}
	
	@Override
	public JsonToken nextToken() throws IOException, JsonParseException {
		if (debug) debug();
		currentTokenIsNull = false;
		JsonToken ret = getInner().nextToken();
		int lastPos = path.size() - 1;
		if (ret == JsonToken.START_ARRAY) {
			path.add(0);
		} else if (ret == JsonToken.END_ARRAY) {
			path.remove(lastPos);
			lastPos--;
			if (fixedLevels > 0 && path.size() == fixedLevels) {
				close();
			} else if (lastPos >= 0) {
				Object obj = path.get(lastPos);
				if (obj instanceof Integer) 
					path.set(lastPos, (Integer)obj + 1);
			}
		} else if (ret == JsonToken.START_OBJECT) {
			path.add("{");
		} else if (ret == JsonToken.END_OBJECT) {
			path.remove(lastPos);
			lastPos--;
			if (fixedLevels > 0 && path.size() == fixedLevels) {
				close();
			} else if (lastPos >= 0) {
				Object obj = path.get(lastPos);
				if (obj instanceof Integer) 
					path.set(lastPos, (Integer)obj + 1);
			}
		} else if (ret == JsonToken.FIELD_NAME) {
			path.set(lastPos, inner.getText());
		} else {
			if (lastPos >= 0) {
				Object obj = path.get(lastPos);
				if (obj instanceof Integer) 
					path.set(lastPos, (Integer)obj + 1);
			}
		}
		if (fixedLevels > 0 && path.size() < fixedLevels) {
			close();
			ret = null;
		}
		if (debug)
			System.out.println("Token: " + ret + ", path: " + path);
		return ret;
	}
	
	@Override
	public JsonToken nextValue() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().nextValue();
	}
	
	@Override
	public void overrideCurrentName(String arg0) {
		if (debug) debug();
		getInner().overrideCurrentName(arg0);
	}
	
	@Override
	public void setCodec(ObjectCodec arg0) {
		if (debug) debug();
		getInner().setCodec(arg0);
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
		return getInner().version();
	}

	@Override
	public boolean canUseSchema(FormatSchema arg0) {
		if (debug) debug();
		return getInner().canUseSchema(arg0);
	}
	
	@Override
	public JsonParser configure(Feature arg0, boolean arg1) {
		if (debug) debug();
		return getInner().configure(arg0, arg1);
	}
	
	@Override
	public JsonParser disable(Feature arg0) {
		if (debug) debug();
		return getInner().disable(arg0);
	}
	
	@Override
	public JsonParser enable(Feature arg0) {
		if (debug) debug();
		return getInner().enable(arg0);
	}
	
	@Override
	public byte[] getBinaryValue() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getBinaryValue();
	}
	
	@Override
	public boolean getBooleanValue() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getBooleanValue();
	}
	
	@Override
	public byte getByteValue() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getByteValue();
	}
	
	@Override
	public Object getInputSource() {
		if (debug) debug();
		return getInner().getInputSource();
	}
	
	@Override
	public FormatSchema getSchema() {
		if (debug) debug();
		return getInner().getSchema();
	}
	
	@Override
	public short getShortValue() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getShortValue();
	}
	
	@Override
	public boolean getValueAsBoolean() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getValueAsBoolean();
	}
	
	@Override
	public boolean getValueAsBoolean(boolean arg0) throws IOException,
			JsonParseException {
		if (debug) debug();
		return getInner().getValueAsBoolean(arg0);
	}
	
	@Override
	public double getValueAsDouble() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getValueAsDouble();
	}
	
	@Override
	public double getValueAsDouble(double arg0) throws IOException,
			JsonParseException {
		if (debug) debug();
		return getInner().getValueAsDouble(arg0);
	}
	
	@Override
	public int getValueAsInt() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getValueAsInt();
	}
	
	@Override
	public int getValueAsInt(int arg0) throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getValueAsInt(arg0);
	}
	
	@Override
	public long getValueAsLong() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getValueAsLong();
	}
	
	@Override
	public long getValueAsLong(long arg0) throws IOException,
			JsonParseException {
		if (debug) debug();
		return getInner().getValueAsLong(arg0);
	}
	
	@Override
	public String getValueAsString() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().getValueAsString();
	}
	
	@Override
	public boolean isEnabled(Feature arg0) {
		if (debug) debug();
		return getInner().isEnabled(arg0);
	}
	
	@Override
	public boolean isExpectedStartArrayToken() {
		if (debug) debug();
		return getInner().isExpectedStartArrayToken();
	}
	
	@Override
	public Boolean nextBooleanValue() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().nextBooleanValue();
	}
	
	@Override
	public boolean nextFieldName(SerializableString arg0) throws IOException,
			JsonParseException {
		if (debug) debug();
		return getInner().nextFieldName(arg0);
	}
	
	@Override
	public int nextIntValue(int arg0) throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().nextIntValue(arg0);
	}
	
	@Override
	public long nextLongValue(long arg0) throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().nextLongValue(arg0);
	}
	
	@Override
	public String nextTextValue() throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().nextTextValue();
	}
	
	@Override
	public int readBinaryValue(Base64Variant arg0, OutputStream arg1)
			throws IOException, JsonParseException {
		if (debug) debug();
		return getInner().readBinaryValue(arg0, arg1);
	}
	
	@Override
	public int readBinaryValue(OutputStream arg0) throws IOException,
			JsonParseException {
		if (debug) debug();
		return getInner().readBinaryValue(arg0);
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
		return getInner().releaseBuffered(arg0);
	}
	
	@Override
	public int releaseBuffered(Writer arg0) throws IOException {
		if (debug) debug();
		return getInner().releaseBuffered(arg0);
	}
	
	@Override
	public boolean requiresCustomCodec() {
		if (debug) debug();
		return getInner().requiresCustomCodec();
	}
	
	@Override
	public void setSchema(FormatSchema arg0) {
		if (debug) debug();
		getInner().setSchema(arg0);
	}
	
	/**
	 * Write all selected (probably through setRoot) tokens into output generator.
	 * @param jgen
	 * @throws IOException
	 */
	public void writeTokens(JsonGenerator jgen) throws IOException {
		try {
			writeNextToken(jgen);
			writeTokensWithoutFirst(jgen);
		} finally {
			close();
		}
	}

	/**
	 * Write all selected (probably through setRoot) tokens into output file.
	 * @param f
	 * @throws IOException
	 */
	public void writeJson(File f) throws IOException {
		OutputStream os = new BufferedOutputStream(new FileOutputStream(f));
		writeJson(os);
		os.close();
	}

	/**
	 * Write all selected (probably through setRoot) tokens into output stream.
	 * @param os
	 * @throws IOException
	 */
	public void writeJson(OutputStream os) throws IOException {
		writeJson(new OutputStreamWriter(os, utf8));
	}
	
	/**
	 * Write all selected (probably through setRoot) tokens into output writer.
	 * @param w
	 * @throws IOException
	 */
	public void writeJson(Writer w) throws IOException {
		if (stringBufferSize > 0)
			w = getWrapperForLargeStrings(w);
		JsonFactory jf = new JsonFactory();
		JsonGenerator jgen = jf.createGenerator(w);
		writeTokens(jgen);
		jgen.flush();
	}
	
	/**
	 * Check if this text is keyword mapping to position of real large string.
	 * @param text
	 * @return
	 */
	public boolean isLargeString(String text) {
		return largeStringPos.containsKey(text);
	}
	
	/**
	 * Search large string position and allow to read this large string as reader.
	 * @param text
	 * @return
	 * @throws IOException
	 */
	public Reader getLargeStringReader(String text) throws IOException {
		long[] borders = largeStringPos.get(text);
		if (borders == null)
			throw new IllegalArgumentException("It's not large string");
		return getLargeStringReader(borders[0], borders[1]);
	}
	
	private Reader getLargeStringReader(long pos, final long commonLength) throws IOException {
		return largeStringReader.place(pos, commonLength);
	}
	
	@SuppressWarnings("unused")
	private Writer getWrapperForLargeStrings(final Writer w) {
		return new Writer() {
			long pos = 0;
			boolean inQ = false;
			boolean wasBS = false;
			char[] buffer = new char[stringBufferSize];
			int afterOpenQuotPos = -1;
			int bufSize = 0;
			int maxLargeStringPosKey = 40;
			@Override
			public void write(char[] cbuf, int off, int len) throws IOException {
				for (int i = 0; i < len; i++) {
					char ch = cbuf[off + i];
					if (bufSize == buffer.length) {
						if (inQ && afterOpenQuotPos == 0)
							throw new IllegalStateException("String value is longer than buffer but it should be substituted on reading stage");
						forwardBuffer();
					}
					pos++;
					buffer[bufSize] = ch;
					bufSize++;
					if (inQ) {
						if (wasBS) {
							wasBS = false;
						} else if (ch == '\\') {
							wasBS = true;
						} else if (ch == '\"') {  // Close string value
							inQ = false;
							if (afterOpenQuotPos >= 0 && bufSize - 1 - afterOpenQuotPos <= maxLargeStringPosKey) {
								boolean prefixIsGood = true;
								for (int p = 0; p < largeStringSubstPrefix.length(); p++)
									if (buffer[afterOpenQuotPos + p] != largeStringSubstPrefix.charAt(p)) {
										prefixIsGood = false;  // It's not large string, treat it normally.
										break;
									}
								long[] borders = null;
								if (prefixIsGood) {
									String key = new String(buffer, afterOpenQuotPos, bufSize - 1 - afterOpenQuotPos);
									borders = largeStringPos.get(key);
								}
								if (borders != null) {  // It's our large string 
									pos -= (bufSize - afterOpenQuotPos);
									//System.out.println("Large string: pos+len=" + borders[0] + "+" + borders[1] + ", afterquotpos=" + pos);
									bufSize = afterOpenQuotPos;
									afterOpenQuotPos = -1;
									Reader lsr = getLargeStringReader(borders[0], borders[1]);
									while (true) {
										if (bufSize == buffer.length)
											forwardBuffer();
										int partLen = lsr.read(buffer, bufSize, buffer.length - bufSize);
										if (partLen < 0)
											break;
										bufSize += partLen;
										pos += partLen;
									}
									if (bufSize == buffer.length)
										forwardBuffer();
									buffer[bufSize] = '\"';
									bufSize++;
									pos++;
								} else {  // It's not large string, treat it normally.
									afterOpenQuotPos = -1;
								}
							}
						}
					} else if (ch == '\"') {  // Open string value
						inQ = true;
						wasBS = false;
						afterOpenQuotPos = bufSize;
					}
				}
			}
			private void forwardBuffer() throws IOException {
				int count = inQ && (afterOpenQuotPos >= 0) ? afterOpenQuotPos : bufSize;
				if (count > 0) {
					w.write(buffer, 0, count);
					for (int i = count; i < bufSize; i++)
						buffer[i - count] = buffer[i];
					bufSize -= count;
					if (inQ && afterOpenQuotPos >= 0)
						afterOpenQuotPos = Math.max(-1, afterOpenQuotPos - count);
				}
			}
			@Override
			public void flush() throws IOException {
				forwardBuffer();
				w.flush();
			}
			@Override
			public void close() throws IOException {
				w.close();
			}
		};
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
			String text = getText();
			if (largeStringPos.containsKey(text)) {
				//System.out.println("Large string: " + text);
				jgen.writeString(text);
			} else {
				jgen.writeString(text);
			}
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
	
	/**
	 * Useful for listening data stream open/close events for debug purposes.
	 * @param debugOpenCloseListener
	 */
	public static void setDebugOpenCloseListener(
			DebugOpenCloseListener debugOpenCloseListener) {
		JsonTokenStream.debugOpenCloseListener = debugOpenCloseListener;
	}
	
	private class LargeStringSearchingReader extends Reader {
		private Reader r = null;
		private long pos = 0;
		private long processed = 0;
		private long commonLength = 0;
		private boolean isClosed = false;
		
		public LargeStringSearchingReader place(long start, long len) throws IOException {
			if (r == null || isClosed) {
				r = createDataReader();
				pos = 0;
			} else if (pos > start) {
				r.reset();
				pos = 0;
			}
			if (pos < start) {
				r.skip(start - pos);
				pos = start;
			}
			processed = 0;
			commonLength = len;
			return this;
		}
		
		@Override
		public void close() throws IOException {
			isClosed = true;
			if (r != null) {
				r.close();
				r = null;
			}
		}
		
		@Override
		public int read(char[] cbuf, int off, int len) throws IOException {
			if (processed >= commonLength)
				return -1;
			if (processed + len > commonLength)
				len = (int)(commonLength - processed);
			int ret = r.read(cbuf, off, len);
			if (ret < 0)
				throw new IllegalStateException("Unexpected end of file");
			processed += ret;
			pos += ret;
			return ret;
		}
	}
	
	/**
	 * Useful for listening data stream open/close events for debug purposes.
	 * @author rsutormin
	 */
	public interface DebugOpenCloseListener {
		public void onStreamOpen(JsonTokenStream instance);
		public void onStreamClosed(JsonTokenStream instance);
	}
}
