package us.kbase.common.service;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.core.io.CharTypes;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.io.NumberInput;
import com.fasterxml.jackson.core.json.JsonReadContext;
import com.fasterxml.jackson.core.json.PackageVersion;
import com.fasterxml.jackson.core.sym.*;
import com.fasterxml.jackson.core.util.*;

/**
 * This class was forked from com.fasterxml.jackson.core.json.UTF8StreamJsonParser
 * in order to be able to limit maximum size of text values in JSON data.
 */
public class KBaseJsonParser extends ParserBase {
	public static final int MAX_STRING_SIZE = 1000000000;
	
    final static byte BYTE_LF = (byte) '\n';

    private final static int[] sInputCodesUtf8 = CharTypes.getInputCodeUtf8();

    /**
     * Latin1 encoding is not supported, but we do use 8-bit subset for
     * pre-processing task, to simplify first pass, keep it fast.
     */
    private final static int[] sInputCodesLatin1 = CharTypes.getInputCodeLatin1();
    
    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    /**
     * Codec used for data binding when (if) requested; typically full
     * <code>ObjectMapper</code>, but that abstract is not part of core
     * package.
     */
    protected ObjectCodec _objectCodec;

    /**
     * Symbol table that contains field names encountered so far
     */
    final protected BytesToNameCanonicalizer _symbols;
    
    /*
    /**********************************************************
    /* Parsing state
    /**********************************************************
     */
    
    /**
     * Temporary buffer used for name parsing.
     */
    protected int[] _quadBuffer = new int[16];

    /**
     * Flag that indicates that the current token has not yet
     * been fully processed, and needs to be finished for
     * some access (or skipped to obtain the next token)
     */
    protected boolean _tokenIncomplete = false;

    /**
     * Temporary storage for partially parsed name bytes.
     */
    private int _quad1;
    
    /*
    /**********************************************************
    /* Input buffering (from former 'StreamBasedParserBase')
    /**********************************************************
     */
    
    protected InputStream _inputStream;

    /*
    /**********************************************************
    /* Current input data
    /**********************************************************
     */

    /**
     * Current buffer from which data is read; generally data is read into
     * buffer from input source, but in some cases pre-loaded buffer
     * is handed to the parser.
     */
    protected byte[] _inputBuffer;

    /**
     * Flag that indicates whether the input buffer is recycable (and
     * needs to be returned to recycler once we are done) or not.
     *<p>
     * If it is not, it also means that parser can NOT modify underlying
     * buffer.
     */
    protected boolean _bufferRecyclable;
    
    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public KBaseJsonParser(JsonFactory factory, InputStream in)
    {
        super(JsonParser.Feature.collectDefaults(), factory._getBufferRecycler(), in, false);
    	BytesToNameCanonicalizer rootByteSymbols = BytesToNameCanonicalizer.createRoot();
    	ObjectCodec codec = factory.getCodec();
    	boolean canonicalize = factory.isEnabled(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES);
    	boolean intern = factory.isEnabled(JsonFactory.Feature.INTERN_FIELD_NAMES);
    	byte[] inputBuffer = _ioContext.allocReadIOBuffer();
        boolean bufferRecyclable = true;
        BytesToNameCanonicalizer sym = rootByteSymbols.makeChild(canonicalize, intern);
        _inputStream = in;
        _objectCodec = codec;
        _symbols = sym;
        _inputBuffer = inputBuffer;
        _inputPtr = 0;
        _inputEnd = 0;
        _bufferRecyclable = bufferRecyclable;
    }

    @Override
    public ObjectCodec getCodec() {
        return _objectCodec;
    }

    @Override
    public void setCodec(ObjectCodec c) {
        _objectCodec = c;
    }
    
    /*
    /**********************************************************
    /* Overrides
    /**********************************************************
     */

    @Override
    public int releaseBuffered(OutputStream out) throws IOException
    {
        int count = _inputEnd - _inputPtr;
        if (count < 1) {
            return 0;
        }
        // let's just advance ptr to end
        int origPtr = _inputPtr;
        out.write(_inputBuffer, origPtr, count);
        return count;
    }

    @Override
    public Object getInputSource() {
        return _inputStream;
    }
    
    /*
    /**********************************************************
    /* Low-level reading, other
    /**********************************************************
     */

    @Override
    protected boolean loadMore()
        throws IOException
    {
        _currInputProcessed += _inputEnd;
        _currInputRowStart -= _inputEnd;
        
        if (_inputStream != null) {
            int count = _inputStream.read(_inputBuffer, 0, _inputBuffer.length);
            if (count > 0) {
                _inputPtr = 0;
                _inputEnd = count;
                return true;
            }
            // End of input
            _closeInput();
            // Should never return 0, so let's fail
            if (count == 0) {
                throw new IOException("InputStream.read() returned 0 characters when trying to read "+_inputBuffer.length+" bytes");
            }
        }
        return false;
    }

    /**
     * Helper method that will try to load at least specified number bytes in
     * input buffer, possible moving existing data around if necessary
     */
    protected boolean _loadToHaveAtLeast(int minAvailable)
        throws IOException
    {
        // No input stream, no leading (either we are closed, or have non-stream input source)
        if (_inputStream == null) {
            return false;
        }
        // Need to move remaining data in front?
        int amount = _inputEnd - _inputPtr;
        if (amount > 0 && _inputPtr > 0) {
            _currInputProcessed += _inputPtr;
            _currInputRowStart -= _inputPtr;
            System.arraycopy(_inputBuffer, _inputPtr, _inputBuffer, 0, amount);
            _inputEnd = amount;
        } else {
            _inputEnd = 0;
        }
        _inputPtr = 0;
        while (_inputEnd < minAvailable) {
            int count = _inputStream.read(_inputBuffer, _inputEnd, _inputBuffer.length - _inputEnd);
            if (count < 1) {
                // End of input
                _closeInput();
                // Should never return 0, so let's fail
                if (count == 0) {
                    throw new IOException("InputStream.read() returned 0 characters when trying to read "+amount+" bytes");
                }
                return false;
            }
            _inputEnd += count;
        }
        return true;
    }
    
    @Override
    protected void _closeInput() throws IOException
    {
        /* 25-Nov-2008, tatus: As per [JACKSON-16] we are not to call close()
         *   on the underlying InputStream, unless we "own" it, or auto-closing
         *   feature is enabled.
         */
        if (_inputStream != null) {
            if (_ioContext.isResourceManaged() || isEnabled(Feature.AUTO_CLOSE_SOURCE)) {
                _inputStream.close();
            }
            _inputStream = null;
        }
    }

    /**
     * Method called to release internal buffers owned by the base
     * reader. This may be called along with {@link #_closeInput} (for
     * example, when explicitly closing this reader instance), or
     * separately (if need be).
     */
    @Override
    protected void _releaseBuffers() throws IOException
    {
        super._releaseBuffers();
        if (_bufferRecyclable) {
            byte[] buf = _inputBuffer;
            if (buf != null) {
                _inputBuffer = null;
                _ioContext.releaseReadIOBuffer(buf);
            }
        }
    }

    /*
    /**********************************************************
    /* Public API, data access
    /**********************************************************
     */

    private void checkStringLength() throws JsonParseException {
    	if (_textBuffer.size() > MAX_STRING_SIZE)
    		_reportError("Too large string literal was found (length>" + MAX_STRING_SIZE + ")");
    }
    
    @Override
    public String getText()
        throws IOException, JsonParseException
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            if (_tokenIncomplete) {
                _tokenIncomplete = false;
                _finishString(); // only strings can be incomplete
            }
            checkStringLength();
            return _textBuffer.contentsAsString();
        }
        return _getText2(_currToken);
    }

    // // // Let's override default impls for improved performance
    
    // @since 2.1
    @Override
    public String getValueAsString() throws IOException, JsonParseException
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            if (_tokenIncomplete) {
                _tokenIncomplete = false;
                _finishString(); // only strings can be incomplete
            }
            checkStringLength();
            return _textBuffer.contentsAsString();
        }
        return super.getValueAsString(null);
    }
    
    // @since 2.1
    @Override
    public String getValueAsString(String defValue) throws IOException, JsonParseException
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            if (_tokenIncomplete) {
                _tokenIncomplete = false;
                _finishString(); // only strings can be incomplete
            }
            checkStringLength();
            return _textBuffer.contentsAsString();
        }
        return super.getValueAsString(defValue);
    }
    
    protected String _getText2(JsonToken t)
    {
        if (t == null) {
            return null;
        }
        switch (t) {
        case FIELD_NAME:
            return _parsingContext.getCurrentName();

        case VALUE_STRING:
            // fall through
        case VALUE_NUMBER_INT:
        case VALUE_NUMBER_FLOAT:
            return _textBuffer.contentsAsString();
        default:
        	return t.asString();
        }
    }

    @Override
    public char[] getTextCharacters()
        throws IOException, JsonParseException
    {
        if (_currToken != null) { // null only before/after document
            switch (_currToken) {
                
            case FIELD_NAME:
                if (!_nameCopied) {
                    String name = _parsingContext.getCurrentName();
                    int nameLen = name.length();
                    if (_nameCopyBuffer == null) {
                        _nameCopyBuffer = _ioContext.allocNameCopyBuffer(nameLen);
                    } else if (_nameCopyBuffer.length < nameLen) {
                        _nameCopyBuffer = new char[nameLen];
                    }
                    name.getChars(0, nameLen, _nameCopyBuffer, 0);
                    _nameCopied = true;
                }
                return _nameCopyBuffer;
    
            case VALUE_STRING:
                if (_tokenIncomplete) {
                    _tokenIncomplete = false;
                    _finishString(); // only strings can be incomplete
                }
                // fall through
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return _textBuffer.getTextBuffer();
                
            default:
                return _currToken.asCharArray();
            }
        }
        return null;
    }

    @Override
    public int getTextLength()
        throws IOException, JsonParseException
    {
        if (_currToken != null) { // null only before/after document
            switch (_currToken) {
                
            case FIELD_NAME:
                return _parsingContext.getCurrentName().length();
            case VALUE_STRING:
                if (_tokenIncomplete) {
                    _tokenIncomplete = false;
                    _finishString(); // only strings can be incomplete
                }
                // fall through
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return _textBuffer.size();
                
            default:
                return _currToken.asCharArray().length;
            }
        }
        return 0;
    }

    @Override
    public int getTextOffset() throws IOException, JsonParseException
    {
        // Most have offset of 0, only some may have other values:
        if (_currToken != null) {
            switch (_currToken) {
            case FIELD_NAME:
                return 0;
            case VALUE_STRING:
                if (_tokenIncomplete) {
                    _tokenIncomplete = false;
                    _finishString(); // only strings can be incomplete
                }
                // fall through
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return _textBuffer.getTextOffset();
            default:
            }
        }
        return 0;
    }
    
    @Override
    public byte[] getBinaryValue(Base64Variant b64variant)
        throws IOException, JsonParseException
    {
        if (_currToken != JsonToken.VALUE_STRING &&
                (_currToken != JsonToken.VALUE_EMBEDDED_OBJECT || _binaryValue == null)) {
            _reportError("Current token ("+_currToken+") not VALUE_STRING or VALUE_EMBEDDED_OBJECT, can not access as binary");
        }
        /* To ensure that we won't see inconsistent data, better clear up
         * state...
         */
        if (_tokenIncomplete) {
            try {
                _binaryValue = _decodeBase64(b64variant);
            } catch (IllegalArgumentException iae) {
                throw _constructError("Failed to decode VALUE_STRING as base64 ("+b64variant+"): "+iae.getMessage());
            }
            /* let's clear incomplete only now; allows for accessing other
             * textual content in error cases
             */
            _tokenIncomplete = false;
        } else { // may actually require conversion...
            if (_binaryValue == null) {
                ByteArrayBuilder builder = _getByteArrayBuilder();
                _decodeBase64(getText(), builder, b64variant);
                _binaryValue = builder.toByteArray();
            }
        }
        return _binaryValue;
    }

    @Override
    public int readBinaryValue(Base64Variant b64variant, OutputStream out)
        throws IOException, JsonParseException
    {
        // if we have already read the token, just use whatever we may have
        if (!_tokenIncomplete || _currToken != JsonToken.VALUE_STRING) {
            byte[] b = getBinaryValue(b64variant);
            out.write(b);
            return b.length;
        }
        // otherwise do "real" incremental parsing...
        byte[] buf = _ioContext.allocBase64Buffer();
        try {
            return _readBinary(b64variant, out, buf);
        } finally {
            _ioContext.releaseBase64Buffer(buf);
        }
    }

    protected int _readBinary(Base64Variant b64variant, OutputStream out,
                              byte[] buffer)
        throws IOException, JsonParseException
    {
        int outputPtr = 0;
        final int outputEnd = buffer.length - 3;
        int outputCount = 0;

        while (true) {
            // first, we'll skip preceding white space, if any
            int ch;
            do {
                if (_inputPtr >= _inputEnd) {
                    loadMoreGuaranteed();
                }
                ch = (int) _inputBuffer[_inputPtr++] & 0xFF;
            } while (ch <= INT_SPACE);
            int bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) { // reached the end, fair and square?
                if (ch == INT_QUOTE) {
                    break;
                }
                bits = _decodeBase64Escape(b64variant, ch, 0);
                if (bits < 0) { // white space to skip
                    continue;
                }
            }

            // enough room? If not, flush
            if (outputPtr > outputEnd) {
                outputCount += outputPtr;
                out.write(buffer, 0, outputPtr);
                outputPtr = 0;
            }

            int decodedData = bits;

            // then second base64 char; can't get padding yet, nor ws

            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) {
                bits = _decodeBase64Escape(b64variant, ch, 1);
            }
            decodedData = (decodedData << 6) | bits;

            // third base64 char; can be padding, but not ws
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            bits = b64variant.decodeBase64Char(ch);

            // First branch: can get padding (-> 1 byte)
            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    // as per [JACKSON-631], could also just be 'missing'  padding
                    if (ch == '"' && !b64variant.usesPadding()) {
                        decodedData >>= 4;
                        buffer[outputPtr++] = (byte) decodedData;
                        break;
                    }
                    bits = _decodeBase64Escape(b64variant, ch, 2);
                }
                if (bits == Base64Variant.BASE64_VALUE_PADDING) {
                    // Ok, must get padding
                    if (_inputPtr >= _inputEnd) {
                        loadMoreGuaranteed();
                    }
                    ch = _inputBuffer[_inputPtr++] & 0xFF;
                    if (!b64variant.usesPaddingChar(ch)) {
                        throw reportInvalidBase64Char(b64variant, ch, 3, "expected padding character '"+b64variant.getPaddingChar()+"'");
                    }
                    // Got 12 bits, only need 8, need to shift
                    decodedData >>= 4;
                    buffer[outputPtr++] = (byte) decodedData;
                    continue;
                }
            }
            // Nope, 2 or 3 bytes
            decodedData = (decodedData << 6) | bits;
            // fourth and last base64 char; can be padding, but not ws
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    // as per [JACKSON-631], could also just be 'missing'  padding
                    if (ch == '"' && !b64variant.usesPadding()) {
                        decodedData >>= 2;
                        buffer[outputPtr++] = (byte) (decodedData >> 8);
                        buffer[outputPtr++] = (byte) decodedData;
                        break;
                    }
                    bits = _decodeBase64Escape(b64variant, ch, 3);
                }
                if (bits == Base64Variant.BASE64_VALUE_PADDING) {
                    /* With padding we only get 2 bytes; but we have
                     * to shift it a bit so it is identical to triplet
                     * case with partial output.
                     * 3 chars gives 3x6 == 18 bits, of which 2 are
                     * dummies, need to discard:
                     */
                    decodedData >>= 2;
                    buffer[outputPtr++] = (byte) (decodedData >> 8);
                    buffer[outputPtr++] = (byte) decodedData;
                    continue;
                }
            }
            // otherwise, our triplet is now complete
            decodedData = (decodedData << 6) | bits;
            buffer[outputPtr++] = (byte) (decodedData >> 16);
            buffer[outputPtr++] = (byte) (decodedData >> 8);
            buffer[outputPtr++] = (byte) decodedData;
        }
        _tokenIncomplete = false;
        if (outputPtr > 0) {
            outputCount += outputPtr;
            out.write(buffer, 0, outputPtr);
        }
        return outputCount;
    }

    /*
    /**********************************************************
    /* Public API, traversal, basic
    /**********************************************************
     */

    /**
     * @return Next token from the stream, if any found, or null
     *   to indicate end-of-input
     */
    @Override
    public JsonToken nextToken()
        throws IOException, JsonParseException
    {
        _numTypesValid = NR_UNKNOWN;
        /* First: field names are special -- we will always tokenize
         * (part of) value along with field name to simplify
         * state handling. If so, can and need to use secondary token:
         */
        if (_currToken == JsonToken.FIELD_NAME) {
            return _nextAfterName();
        }
        if (_tokenIncomplete) {
            _skipString(); // only strings can be partial
        }

        int i = _skipWSOrEnd();
        if (i < 0) { // end-of-input
            /* 19-Feb-2009, tatu: Should actually close/release things
             *    like input source, symbol table and recyclable buffers now.
             */
            close();
            return (_currToken = null);
        }

        /* First, need to ensure we know the starting location of token
         * after skipping leading white space
         */
        _tokenInputTotal = _currInputProcessed + _inputPtr - 1;
        _tokenInputRow = _currInputRow;
        _tokenInputCol = _inputPtr - _currInputRowStart - 1;

        // finally: clear any data retained so far
        _binaryValue = null;

        // Closing scope?
        if (i == INT_RBRACKET) {
            if (!_parsingContext.inArray()) {
                _reportMismatchedEndMarker(i, '}');
            }
            _parsingContext = _parsingContext.getParent();
            return (_currToken = JsonToken.END_ARRAY);
        }
        if (i == INT_RCURLY) {
            if (!_parsingContext.inObject()) {
                _reportMismatchedEndMarker(i, ']');
            }
            _parsingContext = _parsingContext.getParent();
            return (_currToken = JsonToken.END_OBJECT);
        }

        // Nope: do we then expect a comma?
        if (_parsingContext.expectComma()) {
            if (i != INT_COMMA) {
                _reportUnexpectedChar(i, "was expecting comma to separate "+_parsingContext.getTypeDesc()+" entries");
            }
            i = _skipWS();
        }

        /* And should we now have a name? Always true for
         * Object contexts, since the intermediate 'expect-value'
         * state is never retained.
         */
        if (!_parsingContext.inObject()) {
            return _nextTokenNotInObject(i);
        }
        // So first parse the field name itself:
        Name n = _parseFieldName(i);
        _parsingContext.setCurrentName(n.getName());
        _currToken = JsonToken.FIELD_NAME;
        i = _skipWS();
        if (i != INT_COLON) {
            _reportUnexpectedChar(i, "was expecting a colon to separate field name and value");
        }
        i = _skipWS();

        // Ok: we must have a value... what is it? Strings are very common, check first:
        if (i == INT_QUOTE) {
            _tokenIncomplete = true;
            _nextToken = JsonToken.VALUE_STRING;
            return _currToken;
        }        
        JsonToken t;

        switch (i) {
        case INT_LBRACKET:
            t = JsonToken.START_ARRAY;
            break;
        case INT_LCURLY:
            t = JsonToken.START_OBJECT;
            break;
        case INT_RBRACKET:
        case INT_RCURLY:
            // Error: neither is valid at this point; valid closers have
            // been handled earlier
            _reportUnexpectedChar(i, "expected a value");
        case INT_t:
            _matchToken("true", 1);
            t = JsonToken.VALUE_TRUE;
            break;
        case INT_f:
            _matchToken("false", 1);
             t = JsonToken.VALUE_FALSE;
            break;
        case INT_n:
            _matchToken("null", 1);
            t = JsonToken.VALUE_NULL;
            break;

        case INT_MINUS:
            /* Should we have separate handling for plus? Although
             * it is not allowed per se, it may be erroneously used,
             * and could be indicate by a more specific error message.
             */
        case INT_0:
        case INT_1:
        case INT_2:
        case INT_3:
        case INT_4:
        case INT_5:
        case INT_6:
        case INT_7:
        case INT_8:
        case INT_9:
            t = parseNumberText(i);
            break;
        default:
            t = _handleUnexpectedValue(i);
        }
        _nextToken = t;
        return _currToken;
    }

    private JsonToken _nextTokenNotInObject(int i)
        throws IOException, JsonParseException
    {
        if (i == INT_QUOTE) {
            _tokenIncomplete = true;
            return (_currToken = JsonToken.VALUE_STRING);
        }
        switch (i) {
        case INT_LBRACKET:
            _parsingContext = _parsingContext.createChildArrayContext(_tokenInputRow, _tokenInputCol);
            return (_currToken = JsonToken.START_ARRAY);
        case INT_LCURLY:
            _parsingContext = _parsingContext.createChildObjectContext(_tokenInputRow, _tokenInputCol);
            return (_currToken = JsonToken.START_OBJECT);
        case INT_RBRACKET:
        case INT_RCURLY:
            // Error: neither is valid at this point; valid closers have
            // been handled earlier
            _reportUnexpectedChar(i, "expected a value");
        case INT_t:
            _matchToken("true", 1);
            return (_currToken = JsonToken.VALUE_TRUE);
        case INT_f:
            _matchToken("false", 1);
            return (_currToken = JsonToken.VALUE_FALSE);
        case INT_n:
            _matchToken("null", 1);
            return (_currToken = JsonToken.VALUE_NULL);
        case INT_MINUS:
            /* Should we have separate handling for plus? Although
             * it is not allowed per se, it may be erroneously used,
             * and could be indicate by a more specific error message.
             */
        case INT_0:
        case INT_1:
        case INT_2:
        case INT_3:
        case INT_4:
        case INT_5:
        case INT_6:
        case INT_7:
        case INT_8:
        case INT_9:
            return (_currToken = parseNumberText(i));
        }
        return (_currToken = _handleUnexpectedValue(i));
    }
    
    private JsonToken _nextAfterName()
    {
        _nameCopied = false; // need to invalidate if it was copied
        JsonToken t = _nextToken;
        _nextToken = null;
        // Also: may need to start new context?
        if (t == JsonToken.START_ARRAY) {
            _parsingContext = _parsingContext.createChildArrayContext(_tokenInputRow, _tokenInputCol);
        } else if (t == JsonToken.START_OBJECT) {
            _parsingContext = _parsingContext.createChildObjectContext(_tokenInputRow, _tokenInputCol);
        }
        return (_currToken = t);
    }

    @Override
    public void close() throws IOException
    {
        super.close();
        // Merge found symbols, if any:
        _symbols.release();
    }
    
    /*
    /**********************************************************
    /* Public API, traversal, nextXxxValue/nextFieldName
    /**********************************************************
     */
    
    @Override
    public boolean nextFieldName(SerializableString str)
        throws IOException, JsonParseException
    {
        // // // Note: most of code below is copied from nextToken()
        
        _numTypesValid = NR_UNKNOWN;
        if (_currToken == JsonToken.FIELD_NAME) { // can't have name right after name
            _nextAfterName();
            return false;
        }
        if (_tokenIncomplete) {
            _skipString();
        }
        int i = _skipWSOrEnd();
        if (i < 0) { // end-of-input
            close();
            _currToken = null;
            return false;
        }
        _tokenInputTotal = _currInputProcessed + _inputPtr - 1;
        _tokenInputRow = _currInputRow;
        _tokenInputCol = _inputPtr - _currInputRowStart - 1;

        // finally: clear any data retained so far
        _binaryValue = null;

        // Closing scope?
        if (i == INT_RBRACKET) {
            if (!_parsingContext.inArray()) {
                _reportMismatchedEndMarker(i, '}');
            }
            _parsingContext = _parsingContext.getParent();
            _currToken = JsonToken.END_ARRAY;
            return false;
        }
        if (i == INT_RCURLY) {
            if (!_parsingContext.inObject()) {
                _reportMismatchedEndMarker(i, ']');
            }
            _parsingContext = _parsingContext.getParent();
            _currToken = JsonToken.END_OBJECT;
            return false;
        }

        // Nope: do we then expect a comma?
        if (_parsingContext.expectComma()) {
            if (i != INT_COMMA) {
                _reportUnexpectedChar(i, "was expecting comma to separate "+_parsingContext.getTypeDesc()+" entries");
            }
            i = _skipWS();
        }

        if (!_parsingContext.inObject()) {
            _nextTokenNotInObject(i);
            return false;
        }
        
        // // // This part differs, name parsing
        if (i == INT_QUOTE) {
            // when doing literal match, must consider escaping:
            byte[] nameBytes = str.asQuotedUTF8();
            final int len = nameBytes.length;
            if ((_inputPtr + len) < _inputEnd) { // maybe...
                // first check length match by
                final int end = _inputPtr+len;
                if (_inputBuffer[end] == INT_QUOTE) {
                    int offset = 0;
                    final int ptr = _inputPtr;
                    while (true) {
                        if (offset == len) { // yes, match!
                            _inputPtr = end+1; // skip current value first
                            // First part is simple; setting of name
                            _parsingContext.setCurrentName(str.getValue());
                            _currToken = JsonToken.FIELD_NAME;
                            // But then we also must handle following value etc
                            _isNextTokenNameYes();
                            return true;
                        }
                        if (nameBytes[offset] != _inputBuffer[ptr+offset]) {
                            break;
                        }
                        ++offset;
                    }
                }
            }
        }
        return _isNextTokenNameMaybe(i, str);
    }

    private void _isNextTokenNameYes()
        throws IOException, JsonParseException
    {
        // very first thing: common case, colon, value, no white space
        int i;
        if (_inputPtr < (_inputEnd-1) && _inputBuffer[_inputPtr] == INT_COLON) { // fast case first
            i = _inputBuffer[++_inputPtr];
            ++_inputPtr;
            if (i == INT_QUOTE) {
                _tokenIncomplete = true;
                _nextToken = JsonToken.VALUE_STRING;
                return;
            }
            if (i == INT_LCURLY) {
                _nextToken = JsonToken.START_OBJECT;
                return;
            }
            if (i == INT_LBRACKET) {
                _nextToken = JsonToken.START_ARRAY;
                return;
            }
            i &= 0xFF;
            if (i <= INT_SPACE || i == INT_SLASH) {
            	--_inputPtr;
                i = _skipWS();
            }
        } else {
            i = _skipColon();
        }
        switch (i) {
        case INT_QUOTE:
            _tokenIncomplete = true;
            _nextToken = JsonToken.VALUE_STRING;
            return;
        case INT_LBRACKET:
            _nextToken = JsonToken.START_ARRAY;
            return;
        case INT_LCURLY:
            _nextToken = JsonToken.START_OBJECT;
            return;
        case INT_RBRACKET:
        case INT_RCURLY:
            _reportUnexpectedChar(i, "expected a value");
        case INT_t:
            _matchToken("true", 1);
            _nextToken = JsonToken.VALUE_TRUE;
            return;
        case INT_f:
            _matchToken("false", 1);
            _nextToken = JsonToken.VALUE_FALSE;
            return;
        case INT_n:
            _matchToken("null", 1);
            _nextToken = JsonToken.VALUE_NULL;
            return;
        case INT_MINUS:
        case INT_0:
        case INT_1:
        case INT_2:
        case INT_3:
        case INT_4:
        case INT_5:
        case INT_6:
        case INT_7:
        case INT_8:
        case INT_9:
            _nextToken = parseNumberText(i);
            return;
        }
        _nextToken = _handleUnexpectedValue(i);
    }
    
    private boolean _isNextTokenNameMaybe(int i, SerializableString str)
        throws IOException, JsonParseException
    {
        // // // and this is back to standard nextToken()
            
        Name n = _parseFieldName(i);
        final boolean match;
        {
            String nameStr = n.getName();
            _parsingContext.setCurrentName(nameStr);
            match = nameStr.equals(str.getValue());
        }
        _currToken = JsonToken.FIELD_NAME;
        i = _skipWS();
        if (i != INT_COLON) {
            _reportUnexpectedChar(i, "was expecting a colon to separate field name and value");
        }
        i = _skipWS();

        // Ok: we must have a value... what is it? Strings are very common, check first:
        if (i == INT_QUOTE) {
            _tokenIncomplete = true;
            _nextToken = JsonToken.VALUE_STRING;
            return match;
        }
        JsonToken t;

        switch (i) {
        case INT_LBRACKET:
            t = JsonToken.START_ARRAY;
            break;
        case INT_LCURLY:
            t = JsonToken.START_OBJECT;
            break;
        case INT_RBRACKET:
        case INT_RCURLY:
            _reportUnexpectedChar(i, "expected a value");
        case INT_t:
            _matchToken("true", 1);
            t = JsonToken.VALUE_TRUE;
            break;
        case INT_f:
            _matchToken("false", 1);
             t = JsonToken.VALUE_FALSE;
            break;
        case INT_n:
            _matchToken("null", 1);
            t = JsonToken.VALUE_NULL;
            break;

        case INT_MINUS:
        case INT_0:
        case INT_1:
        case INT_2:
        case INT_3:
        case INT_4:
        case INT_5:
        case INT_6:
        case INT_7:
        case INT_8:
        case INT_9:
            t = parseNumberText(i);
            break;
        default:
            t = _handleUnexpectedValue(i);
        }
        _nextToken = t;
        return match;
    }

    @Override
    public String nextTextValue()
        throws IOException, JsonParseException
    {
        // two distinct cases; either got name and we know next type, or 'other'
        if (_currToken == JsonToken.FIELD_NAME) { // mostly copied from '_nextAfterName'
            _nameCopied = false;
            JsonToken t = _nextToken;
            _nextToken = null;
            _currToken = t;
            if (t == JsonToken.VALUE_STRING) {
                if (_tokenIncomplete) {
                    _tokenIncomplete = false;
                    _finishString();
                }
                return _textBuffer.contentsAsString();
            }
            if (t == JsonToken.START_ARRAY) {
                _parsingContext = _parsingContext.createChildArrayContext(_tokenInputRow, _tokenInputCol);
            } else if (t == JsonToken.START_OBJECT) {
                _parsingContext = _parsingContext.createChildObjectContext(_tokenInputRow, _tokenInputCol);
            }
            return null;
        }
        return (nextToken() == JsonToken.VALUE_STRING) ? getText() : null;
    }

    @Override
    public int nextIntValue(int defaultValue)
        throws IOException, JsonParseException
    {
        // two distinct cases; either got name and we know next type, or 'other'
        if (_currToken == JsonToken.FIELD_NAME) { // mostly copied from '_nextAfterName'
            _nameCopied = false;
            JsonToken t = _nextToken;
            _nextToken = null;
            _currToken = t;
            if (t == JsonToken.VALUE_NUMBER_INT) {
                return getIntValue();
            }
            if (t == JsonToken.START_ARRAY) {
                _parsingContext = _parsingContext.createChildArrayContext(_tokenInputRow, _tokenInputCol);
            } else if (t == JsonToken.START_OBJECT) {
                _parsingContext = _parsingContext.createChildObjectContext(_tokenInputRow, _tokenInputCol);
            }
            return defaultValue;
        }
        return (nextToken() == JsonToken.VALUE_NUMBER_INT) ? getIntValue() : defaultValue;
    }

    @Override
    public long nextLongValue(long defaultValue)
        throws IOException, JsonParseException
    {
        // two distinct cases; either got name and we know next type, or 'other'
        if (_currToken == JsonToken.FIELD_NAME) { // mostly copied from '_nextAfterName'
            _nameCopied = false;
            JsonToken t = _nextToken;
            _nextToken = null;
            _currToken = t;
            if (t == JsonToken.VALUE_NUMBER_INT) {
                return getLongValue();
            }
            if (t == JsonToken.START_ARRAY) {
                _parsingContext = _parsingContext.createChildArrayContext(_tokenInputRow, _tokenInputCol);
            } else if (t == JsonToken.START_OBJECT) {
                _parsingContext = _parsingContext.createChildObjectContext(_tokenInputRow, _tokenInputCol);
            }
            return defaultValue;
        }
        return (nextToken() == JsonToken.VALUE_NUMBER_INT) ? getLongValue() : defaultValue;
    }

    @Override
    public Boolean nextBooleanValue()
        throws IOException, JsonParseException
    {
        // two distinct cases; either got name and we know next type, or 'other'
        if (_currToken == JsonToken.FIELD_NAME) { // mostly copied from '_nextAfterName'
            _nameCopied = false;
            JsonToken t = _nextToken;
            _nextToken = null;
            _currToken = t;
            if (t == JsonToken.VALUE_TRUE) {
                return Boolean.TRUE;
            }
            if (t == JsonToken.VALUE_FALSE) {
                return Boolean.FALSE;
            }
            if (t == JsonToken.START_ARRAY) {
                _parsingContext = _parsingContext.createChildArrayContext(_tokenInputRow, _tokenInputCol);
            } else if (t == JsonToken.START_OBJECT) {
                _parsingContext = _parsingContext.createChildObjectContext(_tokenInputRow, _tokenInputCol);
            }
            return null;
        }
        switch (nextToken()) {
        case VALUE_TRUE:
            return Boolean.TRUE;
        case VALUE_FALSE:
            return Boolean.FALSE;
        default:
        	return null;
        }
    }
    
    /*
    /**********************************************************
    /* Internal methods, number parsing
    /* (note: in 1.6 and prior, part of "Utf8NumericParser"
    /**********************************************************
     */

    /**
     * Initial parsing method for number values. It needs to be able
     * to parse enough input to be able to determine whether the
     * value is to be considered a simple integer value, or a more
     * generic decimal value: latter of which needs to be expressed
     * as a floating point number. The basic rule is that if the number
     * has no fractional or exponential part, it is an integer; otherwise
     * a floating point number.
     *<p>
     * Because much of input has to be processed in any case, no partial
     * parsing is done: all input text will be stored for further
     * processing. However, actual numeric value conversion will be
     * deferred, since it is usually the most complicated and costliest
     * part of processing.
     */
    protected JsonToken parseNumberText(int c)
        throws IOException, JsonParseException
    {
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int outPtr = 0;
        boolean negative = (c == INT_MINUS);

        // Need to prepend sign?
        if (negative) {
            outBuf[outPtr++] = '-';
            // Must have something after sign too
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            c = (int) _inputBuffer[_inputPtr++] & 0xFF;
            // Note: must be followed by a digit
            if (c < INT_0 || c > INT_9) {
                return _handleInvalidNumberStart(c, true);
            }
        }

        // One special case: if first char is 0, must not be followed by a digit
        if (c == INT_0) {
            c = _verifyNoLeadingZeroes();
        }
        
        // Ok: we can first just add digit we saw first:
        outBuf[outPtr++] = (char) c;
        int intLen = 1;

        // And then figure out how far we can read without further checks:
        int end = _inputPtr + outBuf.length;
        if (end > _inputEnd) {
            end = _inputEnd;
        }

        // With this, we have a nice and tight loop:
        while (true) {
            if (_inputPtr >= end) {
                // Long enough to be split across boundary, so:
                return _parserNumber2(outBuf, outPtr, negative, intLen);
            }
            c = (int) _inputBuffer[_inputPtr++] & 0xFF;
            if (c < INT_0 || c > INT_9) {
                break;
            }
            ++intLen;
            if (outPtr >= outBuf.length) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
            }
            outBuf[outPtr++] = (char) c;
        }
        if (c == '.' || c == 'e' || c == 'E') {
            return _parseFloatText(outBuf, outPtr, c, negative, intLen);
        }

        --_inputPtr; // to push back trailing char (comma etc)
        _textBuffer.setCurrentLength(outPtr);

        // And there we have it!
        return resetInt(negative, intLen);
    }
    
    /**
     * Method called to handle parsing when input is split across buffer boundary
     * (or output is longer than segment used to store it)
     */
    private JsonToken _parserNumber2(char[] outBuf, int outPtr, boolean negative,
            int intPartLength)
        throws IOException, JsonParseException
    {
        // Ok, parse the rest
        while (true) {
            if (_inputPtr >= _inputEnd && !loadMore()) {
                _textBuffer.setCurrentLength(outPtr);
                return resetInt(negative, intPartLength);
            }
            int c = (int) _inputBuffer[_inputPtr++] & 0xFF;
            if (c > INT_9 || c < INT_0) {
                if (c == '.' || c == 'e' || c == 'E') {
                    return _parseFloatText(outBuf, outPtr, c, negative, intPartLength);
                }
                break;
            }
            if (outPtr >= outBuf.length) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
            }
            outBuf[outPtr++] = (char) c;
            ++intPartLength;
        }
        --_inputPtr; // to push back trailing char (comma etc)
        _textBuffer.setCurrentLength(outPtr);

        // And there we have it!
        return resetInt(negative, intPartLength);
        
    }
    
    /**
     * Method called when we have seen one zero, and want to ensure
     * it is not followed by another
     */
    private int _verifyNoLeadingZeroes()
        throws IOException, JsonParseException
    {
        // Ok to have plain "0"
        if (_inputPtr >= _inputEnd && !loadMore()) {
            return INT_0;
        }
        int ch = _inputBuffer[_inputPtr] & 0xFF;
        // if not followed by a number (probably '.'); return zero as is, to be included
        if (ch < INT_0 || ch > INT_9) {
            return INT_0;
        }
        // [JACKSON-358]: we may want to allow them, after all...
        if (!isEnabled(Feature.ALLOW_NUMERIC_LEADING_ZEROS)) {
            reportInvalidNumber("Leading zeroes not allowed");
        }
        // if so, just need to skip either all zeroes (if followed by number); or all but one (if non-number)
        ++_inputPtr; // Leading zero to be skipped
        if (ch == INT_0) {
            while (_inputPtr < _inputEnd || loadMore()) {
                ch = _inputBuffer[_inputPtr] & 0xFF;
                if (ch < INT_0 || ch > INT_9) { // followed by non-number; retain one zero
                    return INT_0;
                }
                ++_inputPtr; // skip previous zeroes
                if (ch != INT_0) { // followed by other number; return 
                    break;
                }
            }
        }
        return ch;
    }
    
    private JsonToken _parseFloatText(char[] outBuf, int outPtr, int c,
            boolean negative, int integerPartLength)
        throws IOException, JsonParseException
    {
        int fractLen = 0;
        boolean eof = false;

        // And then see if we get other parts
        if (c == '.') { // yes, fraction
            outBuf[outPtr++] = (char) c;

            fract_loop:
            while (true) {
                if (_inputPtr >= _inputEnd && !loadMore()) {
                    eof = true;
                    break fract_loop;
                }
                c = (int) _inputBuffer[_inputPtr++] & 0xFF;
                if (c < INT_0 || c > INT_9) {
                    break fract_loop;
                }
                ++fractLen;
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                }
                outBuf[outPtr++] = (char) c;
            }
            // must be followed by sequence of ints, one minimum
            if (fractLen == 0) {
                reportUnexpectedNumberChar(c, "Decimal point not followed by a digit");
            }
        }

        int expLen = 0;
        if (c == 'e' || c == 'E') { // exponent?
            if (outPtr >= outBuf.length) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
            }
            outBuf[outPtr++] = (char) c;
            // Not optional, can require that we get one more char
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            c = (int) _inputBuffer[_inputPtr++] & 0xFF;
            // Sign indicator?
            if (c == '-' || c == '+') {
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                }
                outBuf[outPtr++] = (char) c;
                // Likewise, non optional:
                if (_inputPtr >= _inputEnd) {
                    loadMoreGuaranteed();
                }
                c = (int) _inputBuffer[_inputPtr++] & 0xFF;
            }

            exp_loop:
            while (c <= INT_9 && c >= INT_0) {
                ++expLen;
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                }
                outBuf[outPtr++] = (char) c;
                if (_inputPtr >= _inputEnd && !loadMore()) {
                    eof = true;
                    break exp_loop;
                }
                c = (int) _inputBuffer[_inputPtr++] & 0xFF;
            }
            // must be followed by sequence of ints, one minimum
            if (expLen == 0) {
                reportUnexpectedNumberChar(c, "Exponent indicator not followed by a digit");
            }
        }

        // Ok; unless we hit end-of-input, need to push last char read back
        if (!eof) {
            --_inputPtr;
        }
        _textBuffer.setCurrentLength(outPtr);

        // And there we have it!
        return resetFloat(negative, integerPartLength, fractLen, expLen);
    }
    
    /*
    /**********************************************************
    /* Internal methods, secondary parsing
    /**********************************************************
     */
    
    protected Name _parseFieldName(int i)
        throws IOException, JsonParseException
    {
        if (i != INT_QUOTE) {
            return _handleUnusualFieldName(i);
        }
        // First: can we optimize out bounds checks?
        if ((_inputPtr + 9) > _inputEnd) { // Need 8 chars, plus one trailing (quote)
            return slowParseFieldName();
        }

        // If so, can also unroll loops nicely
        /* 25-Nov-2008, tatu: This may seem weird, but here we do
         *   NOT want to worry about UTF-8 decoding. Rather, we'll
         *   assume that part is ok (if not it will get caught
         *   later on), and just handle quotes and backslashes here.
         */
        final byte[] input = _inputBuffer;
        final int[] codes = sInputCodesLatin1;

        int q = input[_inputPtr++] & 0xFF;

        if (codes[q] == 0) {
            i = input[_inputPtr++] & 0xFF;
            if (codes[i] == 0) {
                q = (q << 8) | i;
                i = input[_inputPtr++] & 0xFF;
                if (codes[i] == 0) {
                    q = (q << 8) | i;
                    i = input[_inputPtr++] & 0xFF;
                    if (codes[i] == 0) {
                        q = (q << 8) | i;
                        i = input[_inputPtr++] & 0xFF;
                        if (codes[i] == 0) {
                            _quad1 = q;
                            return parseMediumFieldName(i, codes);
                        }
                        if (i == INT_QUOTE) { // one byte/char case or broken
                            return findName(q, 4);
                        }
                        return parseFieldName(q, i, 4);
                    }
                    if (i == INT_QUOTE) { // one byte/char case or broken
                        return findName(q, 3);
                    }
                    return parseFieldName(q, i, 3);
                }                
                if (i == INT_QUOTE) { // one byte/char case or broken
                    return findName(q, 2);
                }
                return parseFieldName(q, i, 2);
            }
            if (i == INT_QUOTE) { // one byte/char case or broken
                return findName(q, 1);
            }
            return parseFieldName(q, i, 1);
        }     
        if (q == INT_QUOTE) { // special case, ""
            return BytesToNameCanonicalizer.getEmptyName();
        }
        return parseFieldName(0, q, 0); // quoting or invalid char
    }

    protected Name parseMediumFieldName(int q2, final int[] codes)
        throws IOException, JsonParseException
    {
        // Ok, got 5 name bytes so far
        int i = _inputBuffer[_inputPtr++] & 0xFF;
        if (codes[i] != 0) {
            if (i == INT_QUOTE) { // 5 bytes
                return findName(_quad1, q2, 1);
            }
            return parseFieldName(_quad1, q2, i, 1); // quoting or invalid char
        }
        q2 = (q2 << 8) | i;
        i = _inputBuffer[_inputPtr++] & 0xFF;
        if (codes[i] != 0) {
            if (i == INT_QUOTE) { // 6 bytes
                return findName(_quad1, q2, 2);
            }
            return parseFieldName(_quad1, q2, i, 2);
        }
        q2 = (q2 << 8) | i;
        i = _inputBuffer[_inputPtr++] & 0xFF;
        if (codes[i] != 0) {
            if (i == INT_QUOTE) { // 7 bytes
                return findName(_quad1, q2, 3);
            }
            return parseFieldName(_quad1, q2, i, 3);
        }
        q2 = (q2 << 8) | i;
        i = _inputBuffer[_inputPtr++] & 0xFF;
        if (codes[i] != 0) {
            if (i == INT_QUOTE) { // 8 bytes
                return findName(_quad1, q2, 4);
            }
            return parseFieldName(_quad1, q2, i, 4);
        }
        _quadBuffer[0] = _quad1;
        _quadBuffer[1] = q2;
        return parseLongFieldName(i);
    }

    protected Name parseLongFieldName(int q)
        throws IOException, JsonParseException
    {
        // As explained above, will ignore UTF-8 encoding at this point
        final int[] codes = sInputCodesLatin1;
        int qlen = 2;

        while (true) {
            /* Let's offline if we hit buffer boundary (otherwise would
             * need to [try to] align input, which is bit complicated
             * and may not always be possible)
             */
            if ((_inputEnd - _inputPtr) < 4) {
                return parseEscapedFieldName(_quadBuffer, qlen, 0, q, 0);
            }
            // Otherwise can skip boundary checks for 4 bytes in loop

            int i = _inputBuffer[_inputPtr++] & 0xFF;
            if (codes[i] != 0) {
                if (i == INT_QUOTE) {
                    return findName(_quadBuffer, qlen, q, 1);
                }
                return parseEscapedFieldName(_quadBuffer, qlen, q, i, 1);
            }

            q = (q << 8) | i;
            i = _inputBuffer[_inputPtr++] & 0xFF;
            if (codes[i] != 0) {
                if (i == INT_QUOTE) {
                    return findName(_quadBuffer, qlen, q, 2);
                }
                return parseEscapedFieldName(_quadBuffer, qlen, q, i, 2);
            }

            q = (q << 8) | i;
            i = _inputBuffer[_inputPtr++] & 0xFF;
            if (codes[i] != 0) {
                if (i == INT_QUOTE) {
                    return findName(_quadBuffer, qlen, q, 3);
                }
                return parseEscapedFieldName(_quadBuffer, qlen, q, i, 3);
            }

            q = (q << 8) | i;
            i = _inputBuffer[_inputPtr++] & 0xFF;
            if (codes[i] != 0) {
                if (i == INT_QUOTE) {
                    return findName(_quadBuffer, qlen, q, 4);
                }
                return parseEscapedFieldName(_quadBuffer, qlen, q, i, 4);
            }

            // Nope, no end in sight. Need to grow quad array etc
            if (qlen >= _quadBuffer.length) {
                _quadBuffer = growArrayBy(_quadBuffer, qlen);
            }
            _quadBuffer[qlen++] = q;
            q = i;
        }
    }

    /**
     * Method called when not even first 8 bytes are guaranteed
     * to come consequtively. Happens rarely, so this is offlined;
     * plus we'll also do full checks for escaping etc.
     */
    protected Name slowParseFieldName()
        throws IOException, JsonParseException
    {
        if (_inputPtr >= _inputEnd) {
            if (!loadMore()) {
                _reportInvalidEOF(": was expecting closing '\"' for name");
            }
        }
        int i = _inputBuffer[_inputPtr++] & 0xFF;
        if (i == INT_QUOTE) { // special case, ""
            return BytesToNameCanonicalizer.getEmptyName();
        }
        return parseEscapedFieldName(_quadBuffer, 0, 0, i, 0);
    }

    private Name parseFieldName(int q1, int ch, int lastQuadBytes)
        throws IOException, JsonParseException
    {
        return parseEscapedFieldName(_quadBuffer, 0, q1, ch, lastQuadBytes);
    }

    private Name parseFieldName(int q1, int q2, int ch, int lastQuadBytes)
        throws IOException, JsonParseException
    {
        _quadBuffer[0] = q1;
        return parseEscapedFieldName(_quadBuffer, 1, q2, ch, lastQuadBytes);
    }

    /**
     * Slower parsing method which is generally branched to when
     * an escape sequence is detected (or alternatively for long
     * names, or ones crossing input buffer boundary). In any case,
     * needs to be able to handle more exceptional cases, gets
     * slower, and hance is offlined to a separate method.
     */
    protected Name parseEscapedFieldName(int[] quads, int qlen, int currQuad, int ch,
                                         int currQuadBytes)
        throws IOException, JsonParseException
    {
        /* 25-Nov-2008, tatu: This may seem weird, but here we do
         *   NOT want to worry about UTF-8 decoding. Rather, we'll
         *   assume that part is ok (if not it will get caught
         *   later on), and just handle quotes and backslashes here.
         */
        final int[] codes = sInputCodesLatin1;

        while (true) {
            if (codes[ch] != 0) {
                if (ch == INT_QUOTE) { // we are done
                    break;
                }
                // Unquoted white space?
                if (ch != INT_BACKSLASH) {
                    // As per [JACKSON-208], call can now return:
                    _throwUnquotedSpace(ch, "name");
                } else {
                    // Nope, escape sequence
                    ch = _decodeEscaped();
                }
                /* Oh crap. May need to UTF-8 (re-)encode it, if it's
                 * beyond 7-bit ascii. Gets pretty messy.
                 * If this happens often, may want to use different name
                 * canonicalization to avoid these hits.
                 */
                if (ch > 127) {
                    // Ok, we'll need room for first byte right away
                    if (currQuadBytes >= 4) {
                        if (qlen >= quads.length) {
                            _quadBuffer = quads = growArrayBy(quads, quads.length);
                        }
                        quads[qlen++] = currQuad;
                        currQuad = 0;
                        currQuadBytes = 0;
                    }
                    if (ch < 0x800) { // 2-byte
                        currQuad = (currQuad << 8) | (0xc0 | (ch >> 6));
                        ++currQuadBytes;
                        // Second byte gets output below:
                    } else { // 3 bytes; no need to worry about surrogates here
                        currQuad = (currQuad << 8) | (0xe0 | (ch >> 12));
                        ++currQuadBytes;
                        // need room for middle byte?
                        if (currQuadBytes >= 4) {
                            if (qlen >= quads.length) {
                                _quadBuffer = quads = growArrayBy(quads, quads.length);
                            }
                            quads[qlen++] = currQuad;
                            currQuad = 0;
                            currQuadBytes = 0;
                        }
                        currQuad = (currQuad << 8) | (0x80 | ((ch >> 6) & 0x3f));
                        ++currQuadBytes;
                    }
                    // And same last byte in both cases, gets output below:
                    ch = 0x80 | (ch & 0x3f);
                }
            }
            // Ok, we have one more byte to add at any rate:
            if (currQuadBytes < 4) {
                ++currQuadBytes;
                currQuad = (currQuad << 8) | ch;
            } else {
                if (qlen >= quads.length) {
                    _quadBuffer = quads = growArrayBy(quads, quads.length);
                }
                quads[qlen++] = currQuad;
                currQuad = ch;
                currQuadBytes = 1;
            }
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    _reportInvalidEOF(" in field name");
                }
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
        }

        if (currQuadBytes > 0) {
            if (qlen >= quads.length) {
                _quadBuffer = quads = growArrayBy(quads, quads.length);
            }
            quads[qlen++] = currQuad;
        }
        Name name = _symbols.findName(quads, qlen);
        if (name == null) {
            name = addName(quads, qlen, currQuadBytes);
        }
        return name;
    }

    /**
     * Method called when we see non-white space character other
     * than double quote, when expecting a field name.
     * In standard mode will just throw an expection; but
     * in non-standard modes may be able to parse name.
     */
    protected Name _handleUnusualFieldName(int ch)
        throws IOException, JsonParseException
    {
        // [JACKSON-173]: allow single quotes
        if (ch == INT_APOSTROPHE && isEnabled(Feature.ALLOW_SINGLE_QUOTES)) {
            return _parseApostropheFieldName();
        }
        // [JACKSON-69]: allow unquoted names if feature enabled:
        if (!isEnabled(Feature.ALLOW_UNQUOTED_FIELD_NAMES)) {
            _reportUnexpectedChar(ch, "was expecting double-quote to start field name");
        }
        /* Also: note that although we use a different table here,
         * it does NOT handle UTF-8 decoding. It'll just pass those
         * high-bit codes as acceptable for later decoding.
         */
        final int[] codes = CharTypes.getInputCodeUtf8JsNames();
        // Also: must start with a valid character...
        if (codes[ch] != 0) {
            _reportUnexpectedChar(ch, "was expecting either valid name character (for unquoted name) or double-quote (for quoted) to start field name");
        }

        /* Ok, now; instead of ultra-optimizing parsing here (as with
         * regular JSON names), let's just use the generic "slow"
         * variant. Can measure its impact later on if need be
         */
        int[] quads = _quadBuffer;
        int qlen = 0;
        int currQuad = 0;
        int currQuadBytes = 0;

        while (true) {
            // Ok, we have one more byte to add at any rate:
            if (currQuadBytes < 4) {
                ++currQuadBytes;
                currQuad = (currQuad << 8) | ch;
            } else {
                if (qlen >= quads.length) {
                    _quadBuffer = quads = growArrayBy(quads, quads.length);
                }
                quads[qlen++] = currQuad;
                currQuad = ch;
                currQuadBytes = 1;
            }
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    _reportInvalidEOF(" in field name");
                }
            }
            ch = _inputBuffer[_inputPtr] & 0xFF;
            if (codes[ch] != 0) {
                break;
            }
            ++_inputPtr;
        }

        if (currQuadBytes > 0) {
            if (qlen >= quads.length) {
                _quadBuffer = quads = growArrayBy(quads, quads.length);
            }
            quads[qlen++] = currQuad;
        }
        Name name = _symbols.findName(quads, qlen);
        if (name == null) {
            name = addName(quads, qlen, currQuadBytes);
        }
        return name;
    }

    /* Parsing to support [JACKSON-173]. Plenty of duplicated code;
     * main reason being to try to avoid slowing down fast path
     * for valid JSON -- more alternatives, more code, generally
     * bit slower execution.
     */
    protected Name _parseApostropheFieldName()
        throws IOException, JsonParseException
    {
        if (_inputPtr >= _inputEnd) {
            if (!loadMore()) {
                _reportInvalidEOF(": was expecting closing '\'' for name");
            }
        }
        int ch = _inputBuffer[_inputPtr++] & 0xFF;
        if (ch == INT_APOSTROPHE) { // special case, ''
            return BytesToNameCanonicalizer.getEmptyName();
        }
        int[] quads = _quadBuffer;
        int qlen = 0;
        int currQuad = 0;
        int currQuadBytes = 0;

        // Copied from parseEscapedFieldName, with minor mods:

        final int[] codes = sInputCodesLatin1;

        while (true) {
            if (ch == INT_APOSTROPHE) {
                break;
            }
            // additional check to skip handling of double-quotes
            if (ch != INT_QUOTE && codes[ch] != 0) {
                if (ch != INT_BACKSLASH) {
                    // Unquoted white space?
                    // As per [JACKSON-208], call can now return:
                    _throwUnquotedSpace(ch, "name");
                } else {
                    // Nope, escape sequence
                    ch = _decodeEscaped();
                }
                /* Oh crap. May need to UTF-8 (re-)encode it, if it's
                 * beyond 7-bit ascii. Gets pretty messy.
                 * If this happens often, may want to use different name
                 * canonicalization to avoid these hits.
                 */
                if (ch > 127) {
                    // Ok, we'll need room for first byte right away
                    if (currQuadBytes >= 4) {
                        if (qlen >= quads.length) {
                            _quadBuffer = quads = growArrayBy(quads, quads.length);
                        }
                        quads[qlen++] = currQuad;
                        currQuad = 0;
                        currQuadBytes = 0;
                    }
                    if (ch < 0x800) { // 2-byte
                        currQuad = (currQuad << 8) | (0xc0 | (ch >> 6));
                        ++currQuadBytes;
                        // Second byte gets output below:
                    } else { // 3 bytes; no need to worry about surrogates here
                        currQuad = (currQuad << 8) | (0xe0 | (ch >> 12));
                        ++currQuadBytes;
                        // need room for middle byte?
                        if (currQuadBytes >= 4) {
                            if (qlen >= quads.length) {
                                _quadBuffer = quads = growArrayBy(quads, quads.length);
                            }
                            quads[qlen++] = currQuad;
                            currQuad = 0;
                            currQuadBytes = 0;
                        }
                        currQuad = (currQuad << 8) | (0x80 | ((ch >> 6) & 0x3f));
                        ++currQuadBytes;
                    }
                    // And same last byte in both cases, gets output below:
                    ch = 0x80 | (ch & 0x3f);
                }
            }
            // Ok, we have one more byte to add at any rate:
            if (currQuadBytes < 4) {
                ++currQuadBytes;
                currQuad = (currQuad << 8) | ch;
            } else {
                if (qlen >= quads.length) {
                    _quadBuffer = quads = growArrayBy(quads, quads.length);
                }
                quads[qlen++] = currQuad;
                currQuad = ch;
                currQuadBytes = 1;
            }
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    _reportInvalidEOF(" in field name");
                }
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
        }

        if (currQuadBytes > 0) {
            if (qlen >= quads.length) {
                _quadBuffer = quads = growArrayBy(quads, quads.length);
            }
            quads[qlen++] = currQuad;
        }
        Name name = _symbols.findName(quads, qlen);
        if (name == null) {
            name = addName(quads, qlen, currQuadBytes);
        }
        return name;
    }

    /*
    /**********************************************************
    /* Internal methods, symbol (name) handling
    /**********************************************************
     */

    private Name findName(int q1, int lastQuadBytes)
        throws JsonParseException
    {
        // Usually we'll find it from the canonical symbol table already
        Name name = _symbols.findName(q1);
        if (name != null) {
            return name;
        }
        // If not, more work. We'll need add stuff to buffer
        _quadBuffer[0] = q1;
        return addName(_quadBuffer, 1, lastQuadBytes);
    }

    private Name findName(int q1, int q2, int lastQuadBytes)
        throws JsonParseException
    {
        // Usually we'll find it from the canonical symbol table already
        Name name = _symbols.findName(q1, q2);
        if (name != null) {
            return name;
        }
        // If not, more work. We'll need add stuff to buffer
        _quadBuffer[0] = q1;
        _quadBuffer[1] = q2;
        return addName(_quadBuffer, 2, lastQuadBytes);
    }

    private Name findName(int[] quads, int qlen, int lastQuad, int lastQuadBytes)
        throws JsonParseException
    {
        if (qlen >= quads.length) {
            _quadBuffer = quads = growArrayBy(quads, quads.length);
        }
        quads[qlen++] = lastQuad;
        Name name = _symbols.findName(quads, qlen);
        if (name == null) {
            return addName(quads, qlen, lastQuadBytes);
        }
        return name;
    }

    /**
     * This is the main workhorse method used when we take a symbol
     * table miss. It needs to demultiplex individual bytes, decode
     * multi-byte chars (if any), and then construct Name instance
     * and add it to the symbol table.
     */
    private Name addName(int[] quads, int qlen, int lastQuadBytes)
        throws JsonParseException
    {
        /* Ok: must decode UTF-8 chars. No other validation is
         * needed, since unescaping has been done earlier as necessary
         * (as well as error reporting for unescaped control chars)
         */
        // 4 bytes per quad, except last one maybe less
        int byteLen = (qlen << 2) - 4 + lastQuadBytes;

        /* And last one is not correctly aligned (leading zero bytes instead
         * need to shift a bit, instead of trailing). Only need to shift it
         * for UTF-8 decoding; need revert for storage (since key will not
         * be aligned, to optimize lookup speed)
         */
        int lastQuad;

        if (lastQuadBytes < 4) {
            lastQuad = quads[qlen-1];
            // 8/16/24 bit left shift
            quads[qlen-1] = (lastQuad << ((4 - lastQuadBytes) << 3));
        } else {
            lastQuad = 0;
        }

        // Need some working space, TextBuffer works well:
        char[] cbuf = _textBuffer.emptyAndGetCurrentSegment();
        int cix = 0;

        for (int ix = 0; ix < byteLen; ) {
            int ch = quads[ix >> 2]; // current quad, need to shift+mask
            int byteIx = (ix & 3);
            ch = (ch >> ((3 - byteIx) << 3)) & 0xFF;
            ++ix;

            if (ch > 127) { // multi-byte
                int needed;
                if ((ch & 0xE0) == 0xC0) { // 2 bytes (0x0080 - 0x07FF)
                    ch &= 0x1F;
                    needed = 1;
                } else if ((ch & 0xF0) == 0xE0) { // 3 bytes (0x0800 - 0xFFFF)
                    ch &= 0x0F;
                    needed = 2;
                } else if ((ch & 0xF8) == 0xF0) { // 4 bytes; double-char with surrogates and all...
                    ch &= 0x07;
                    needed = 3;
                } else { // 5- and 6-byte chars not valid xml chars
                    _reportInvalidInitial(ch);
                    needed = ch = 1; // never really gets this far
                }
                if ((ix + needed) > byteLen) {
                    _reportInvalidEOF(" in field name");
                }
                
                // Ok, always need at least one more:
                int ch2 = quads[ix >> 2]; // current quad, need to shift+mask
                byteIx = (ix & 3);
                ch2 = (ch2 >> ((3 - byteIx) << 3));
                ++ix;
                
                if ((ch2 & 0xC0) != 0x080) {
                    _reportInvalidOther(ch2);
                }
                ch = (ch << 6) | (ch2 & 0x3F);
                if (needed > 1) {
                    ch2 = quads[ix >> 2];
                    byteIx = (ix & 3);
                    ch2 = (ch2 >> ((3 - byteIx) << 3));
                    ++ix;
                    
                    if ((ch2 & 0xC0) != 0x080) {
                        _reportInvalidOther(ch2);
                    }
                    ch = (ch << 6) | (ch2 & 0x3F);
                    if (needed > 2) { // 4 bytes? (need surrogates on output)
                        ch2 = quads[ix >> 2];
                        byteIx = (ix & 3);
                        ch2 = (ch2 >> ((3 - byteIx) << 3));
                        ++ix;
                        if ((ch2 & 0xC0) != 0x080) {
                            _reportInvalidOther(ch2 & 0xFF);
                        }
                        ch = (ch << 6) | (ch2 & 0x3F);
                    }
                }
                if (needed > 2) { // surrogate pair? once again, let's output one here, one later on
                    ch -= 0x10000; // to normalize it starting with 0x0
                    if (cix >= cbuf.length) {
                        cbuf = _textBuffer.expandCurrentSegment();
                    }
                    cbuf[cix++] = (char) (0xD800 + (ch >> 10));
                    ch = 0xDC00 | (ch & 0x03FF);
                }
            }
            if (cix >= cbuf.length) {
                cbuf = _textBuffer.expandCurrentSegment();
            }
            cbuf[cix++] = (char) ch;
        }

        // Ok. Now we have the character array, and can construct the String
        String baseName = new String(cbuf, 0, cix);
        // And finally, un-align if necessary
        if (lastQuadBytes < 4) {
            quads[qlen-1] = lastQuad;
        }
        return _symbols.addName(baseName, quads, qlen);
    }

    /*
    /**********************************************************
    /* Internal methods, String value parsing
    /**********************************************************
     */

    @Override
    protected void _finishString()
        throws IOException, JsonParseException
    {
        // First, single tight loop for ASCII content, not split across input buffer boundary:        
        int ptr = _inputPtr;
        if (ptr >= _inputEnd) {
            loadMoreGuaranteed();
            ptr = _inputPtr;
        }
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        final int[] codes = sInputCodesUtf8;

        final int max = Math.min(_inputEnd, (ptr + outBuf.length));
        final byte[] inputBuffer = _inputBuffer;
        while (ptr < max) {
            int c = (int) inputBuffer[ptr] & 0xFF;
            if (codes[c] != 0) {
                if (c == INT_QUOTE) {
                    _inputPtr = ptr+1;
                    _textBuffer.setCurrentLength(outPtr);
                    return;
                }
                break;
            }
            ++ptr;
            outBuf[outPtr++] = (char) c;
        }
        _inputPtr = ptr;
        _finishString2(outBuf, outPtr);
    }

    private void _finishString2(char[] outBuf, int outPtr)
        throws IOException, JsonParseException
    {
        int c;

        // Here we do want to do full decoding, hence:
        final int[] codes = sInputCodesUtf8;
        final byte[] inputBuffer = _inputBuffer;

        main_loop:
        while (true) {
            // Then the tight ASCII non-funny-char loop:
            ascii_loop:
            while (true) {
                int ptr = _inputPtr;
                if (ptr >= _inputEnd) {
                    loadMoreGuaranteed();
                    ptr = _inputPtr;
                }
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                }
                final int max = Math.min(_inputEnd, (ptr + (outBuf.length - outPtr)));
                while (ptr < max) {
                    c = (int) inputBuffer[ptr++] & 0xFF;
                    if (codes[c] != 0) {
                        _inputPtr = ptr;
                        break ascii_loop;
                    }
                    outBuf[outPtr++] = (char) c;
                }
                _inputPtr = ptr;
            }
            // Ok: end marker, escape or multi-byte?
            if (c == INT_QUOTE) {
                break main_loop;
            }

            switch (codes[c]) {
            case 1: // backslash
                c = _decodeEscaped();
                break;
            case 2: // 2-byte UTF
                c = _decodeUtf8_2(c);
                break;
            case 3: // 3-byte UTF
                if ((_inputEnd - _inputPtr) >= 2) {
                    c = _decodeUtf8_3fast(c);
                } else {
                    c = _decodeUtf8_3(c);
                }
                break;
            case 4: // 4-byte UTF
                c = _decodeUtf8_4(c);
                // Let's add first part right away:
                outBuf[outPtr++] = (char) (0xD800 | (c >> 10));
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                }
                c = 0xDC00 | (c & 0x3FF);
                // And let the other char output down below
                break;
            default:
                if (c < INT_SPACE) {
                    // As per [JACKSON-208], call can now return:
                    _throwUnquotedSpace(c, "string value");
                } else {
                    // Is this good enough error message?
                    _reportInvalidChar(c);
                }
            }
            // Need more room?
            if (outPtr >= outBuf.length) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
            }
            // Ok, let's add char to output:
            outBuf[outPtr++] = (char) c;
        }
        _textBuffer.setCurrentLength(outPtr);
    }

    /**
     * Method called to skim through rest of unparsed String value,
     * if it is not needed. This can be done bit faster if contents
     * need not be stored for future access.
     */
    protected void _skipString()
        throws IOException, JsonParseException
    {
        _tokenIncomplete = false;

        // Need to be fully UTF-8 aware here:
        final int[] codes = sInputCodesUtf8;
        final byte[] inputBuffer = _inputBuffer;

        main_loop:
        while (true) {
            int c;

            ascii_loop:
            while (true) {
                int ptr = _inputPtr;
                int max = _inputEnd;
                if (ptr >= max) {
                    loadMoreGuaranteed();
                    ptr = _inputPtr;
                    max = _inputEnd;
                }
                while (ptr < max) {
                    c = (int) inputBuffer[ptr++] & 0xFF;
                    if (codes[c] != 0) {
                        _inputPtr = ptr;
                        break ascii_loop;
                    }
                }
                _inputPtr = ptr;
            }
            // Ok: end marker, escape or multi-byte?
            if (c == INT_QUOTE) {
                break main_loop;
            }
            
            switch (codes[c]) {
            case 1: // backslash
                _decodeEscaped();
                break;
            case 2: // 2-byte UTF
                _skipUtf8_2(c);
                break;
            case 3: // 3-byte UTF
                _skipUtf8_3(c);
                break;
            case 4: // 4-byte UTF
                _skipUtf8_4(c);
                break;
            default:
                if (c < INT_SPACE) {
                    // As per [JACKSON-208], call can now return:
                    _throwUnquotedSpace(c, "string value");
                } else {
                    // Is this good enough error message?
                    _reportInvalidChar(c);
                }
            }
        }
    }

    /**
     * Method for handling cases where first non-space character
     * of an expected value token is not legal for standard JSON content.
     */
    protected JsonToken _handleUnexpectedValue(int c)
        throws IOException, JsonParseException
    {
        // Most likely an error, unless we are to allow single-quote-strings
        switch (c) {
        case '\'':
            if (isEnabled(Feature.ALLOW_SINGLE_QUOTES)) {
                return _handleApostropheValue();
            }
            break;
        case 'N':
            _matchToken("NaN", 1);
            if (isEnabled(Feature.ALLOW_NON_NUMERIC_NUMBERS)) {
                return resetAsNaN("NaN", Double.NaN);
            }
            _reportError("Non-standard token 'NaN': enable JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS to allow");
            break;
        case 'I':
            _matchToken("Infinity", 1);
            if (isEnabled(Feature.ALLOW_NON_NUMERIC_NUMBERS)) {
                return resetAsNaN("Infinity", Double.POSITIVE_INFINITY);
            }
            _reportError("Non-standard token 'Infinity': enable JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS to allow");
            break;
        case '+': // note: '-' is taken as number
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    _reportInvalidEOFInValue();
                }
            }
            return _handleInvalidNumberStart(_inputBuffer[_inputPtr++] & 0xFF, false);
        }

        _reportUnexpectedChar(c, "expected a valid value (number, String, array, object, 'true', 'false' or 'null')");
        return null;
    }
    
    protected JsonToken _handleApostropheValue()
        throws IOException, JsonParseException
    {
        int c = 0;
        // Otherwise almost verbatim copy of _finishString()
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();

        // Here we do want to do full decoding, hence:
        final int[] codes = sInputCodesUtf8;
        final byte[] inputBuffer = _inputBuffer;

        main_loop:
        while (true) {
            // Then the tight ascii non-funny-char loop:
            ascii_loop:
            while (true) {
                if (_inputPtr >= _inputEnd) {
                    loadMoreGuaranteed();
                }
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                }
                int max = _inputEnd;
                {
                    int max2 = _inputPtr + (outBuf.length - outPtr);
                    if (max2 < max) {
                        max = max2;
                    }
                }
                while (_inputPtr < max) {
                    c = (int) inputBuffer[_inputPtr++] & 0xFF;
                    if (c == INT_APOSTROPHE || codes[c] != 0) {
                        break ascii_loop;
                    }
                    outBuf[outPtr++] = (char) c;
                }
            }

            // Ok: end marker, escape or multi-byte?
            if (c == INT_APOSTROPHE) {
                break main_loop;
            }

            switch (codes[c]) {
            case 1: // backslash
                if (c != INT_QUOTE) { // marked as special, isn't here
                    c = _decodeEscaped();
                }
                break;
            case 2: // 2-byte UTF
                c = _decodeUtf8_2(c);
                break;
            case 3: // 3-byte UTF
                if ((_inputEnd - _inputPtr) >= 2) {
                    c = _decodeUtf8_3fast(c);
                } else {
                    c = _decodeUtf8_3(c);
                }
                break;
            case 4: // 4-byte UTF
                c = _decodeUtf8_4(c);
                // Let's add first part right away:
                outBuf[outPtr++] = (char) (0xD800 | (c >> 10));
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                }
                c = 0xDC00 | (c & 0x3FF);
                // And let the other char output down below
                break;
            default:
                if (c < INT_SPACE) {
                    _throwUnquotedSpace(c, "string value");
                }
                // Is this good enough error message?
                _reportInvalidChar(c);
            }
            // Need more room?
            if (outPtr >= outBuf.length) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
            }
            // Ok, let's add char to output:
            outBuf[outPtr++] = (char) c;
        }
        _textBuffer.setCurrentLength(outPtr);

        return JsonToken.VALUE_STRING;
    }

    /**
     * Method called if expected numeric value (due to leading sign) does not
     * look like a number
     */
    protected JsonToken _handleInvalidNumberStart(int ch, boolean neg)
        throws IOException, JsonParseException
    {
        while (ch == 'I') {
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    _reportInvalidEOFInValue();
                }
            }
            ch = _inputBuffer[_inputPtr++];
            String match;
            if (ch == 'N') {
                match = neg ? "-INF" :"+INF";
            } else if (ch == 'n') {
                match = neg ? "-Infinity" :"+Infinity";
            } else {
                break;
            }
            _matchToken(match, 3);
            if (isEnabled(Feature.ALLOW_NON_NUMERIC_NUMBERS)) {
                return resetAsNaN(match, neg ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
            }
            _reportError("Non-standard token '"+match+"': enable JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS to allow");
        }
        reportUnexpectedNumberChar(ch, "expected digit (0-9) to follow minus sign, for valid numeric value");
        return null;
    }
    
    protected void _matchToken(String matchStr, int i)
        throws IOException, JsonParseException
    {
        final int len = matchStr.length();
    
        do {
            if (((_inputPtr >= _inputEnd) && !loadMore())
                ||  (_inputBuffer[_inputPtr] != matchStr.charAt(i))) {
                _reportInvalidToken(matchStr.substring(0, i));
            }
            ++_inputPtr;
        } while (++i < len);
    
        // but let's also ensure we either get EOF, or non-alphanum char...
        if (_inputPtr >= _inputEnd && !loadMore()) {
            return;
        }
        int ch = _inputBuffer[_inputPtr] & 0xFF;
        if (ch < '0' || ch == ']' || ch == '}') { // expected/allowed chars
            return;
        }
        // but actually only alphanums are problematic
        char c = (char) _decodeCharForError(ch);
        if (Character.isJavaIdentifierPart(c)) {
            _reportInvalidToken(matchStr.substring(0, i));
        }
    }

    protected void _reportInvalidToken(String matchedPart)
       throws IOException, JsonParseException
    {
        _reportInvalidToken(matchedPart, "'null', 'true', 'false' or NaN");
    }
    
    protected void _reportInvalidToken(String matchedPart, String msg)
        throws IOException, JsonParseException
    {
        StringBuilder sb = new StringBuilder(matchedPart);

        /* Let's just try to find what appears to be the token, using
         * regular Java identifier character rules. It's just a heuristic,
         * nothing fancy here (nor fast).
         */
        while (true) {
            if (_inputPtr >= _inputEnd && !loadMore()) {
                break;
            }
            int i = (int) _inputBuffer[_inputPtr++];
            char c = (char) _decodeCharForError(i);
            if (!Character.isJavaIdentifierPart(c)) {
                break;
            }
            sb.append(c);
        }
        _reportError("Unrecognized token '"+sb.toString()+"': was expecting "+msg);
    }

    /*
    /**********************************************************
    /* Internal methods, ws skipping, escape/unescape
    /**********************************************************
     */

    private int _skipWS()
        throws IOException, JsonParseException
    {
        while (_inputPtr < _inputEnd || loadMore()) {
            int i = _inputBuffer[_inputPtr++] & 0xFF;
            if (i > INT_SPACE) {
                if (i != INT_SLASH) {
                    return i;
                }
                _skipComment();
            } else if (i != INT_SPACE) {
                if (i == INT_LF) {
                    _skipLF();
                } else if (i == INT_CR) {
                    _skipCR();
                } else if (i != INT_TAB) {
                    _throwInvalidSpace(i);
                }
            }
        }
        throw _constructError("Unexpected end-of-input within/between "+_parsingContext.getTypeDesc()+" entries");
    }

    private int _skipWSOrEnd()
        throws IOException, JsonParseException
    {
        while ((_inputPtr < _inputEnd) || loadMore()) {
            int i = _inputBuffer[_inputPtr++] & 0xFF;
            if (i > INT_SPACE) {
                if (i != INT_SLASH) {
                    return i;
                }
                _skipComment();
            } else if (i != INT_SPACE) {
                if (i == INT_LF) {
                    _skipLF();
                } else if (i == INT_CR) {
                    _skipCR();
                } else if (i != INT_TAB) {
                    _throwInvalidSpace(i);
                }
            }
        }
        // We ran out of input...
        _handleEOF();
        return -1;
    }

    /**
     * Helper method for matching and skipping a colon character,
     * optionally surrounded by white space
     */
    private int _skipColon()
        throws IOException, JsonParseException
    {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        // first fast case: we just got a colon without white space:
        int i = _inputBuffer[_inputPtr++];
        if (i == INT_COLON) {
            if (_inputPtr < _inputEnd) {
                i = _inputBuffer[_inputPtr] & 0xFF;
                if (i > INT_SPACE && i != INT_SLASH) {
                    ++_inputPtr;
                    return i;
                }
            }
        } else {
            // need to skip potential leading space
            i &= 0xFF;
            
            space_loop:
            while (true) {
                switch (i) {
                case INT_SPACE:
                case INT_TAB:
                    break;
                case INT_CR:
                    _skipCR();
                    break;
                case INT_LF:
                    _skipLF();
                    break;
                case INT_SLASH:
                    _skipComment();
                    break;
                default:
                    if (i < INT_SPACE) {
                        _throwInvalidSpace(i);
                    }
                    break space_loop;
                }
                if (_inputPtr >= _inputEnd) {
                    loadMoreGuaranteed();
                }
                i = _inputBuffer[_inputPtr++] & 0xFF;
            }
            if (i != INT_COLON) {
                _reportUnexpectedChar(i, "was expecting a colon to separate field name and value");
            }
        }

            // either way, found colon, skip through trailing WS
        while (_inputPtr < _inputEnd || loadMore()) {
            i = _inputBuffer[_inputPtr++] & 0xFF;
            if (i > INT_SPACE) {
                if (i != INT_SLASH) {
                    return i;
                }
                _skipComment();
            } else if (i != INT_SPACE) {
                if (i == INT_LF) {
                    _skipLF();
                } else if (i == INT_CR) {
                    _skipCR();
                } else if (i != INT_TAB) {
                    _throwInvalidSpace(i);
                }
            }
        }
        throw _constructError("Unexpected end-of-input within/between "+_parsingContext.getTypeDesc()+" entries");
    }
    
    private void _skipComment()
        throws IOException, JsonParseException
    {
        if (!isEnabled(Feature.ALLOW_COMMENTS)) {
            _reportUnexpectedChar('/', "maybe a (non-standard) comment? (not recognized as one since Feature 'ALLOW_COMMENTS' not enabled for parser)");
        }
        // First: check which comment (if either) it is:
        if (_inputPtr >= _inputEnd && !loadMore()) {
            _reportInvalidEOF(" in a comment");
        }
        int c = _inputBuffer[_inputPtr++] & 0xFF;
        if (c == INT_SLASH) {
            _skipCppComment();
        } else if (c == INT_ASTERISK) {
            _skipCComment();
        } else {
            _reportUnexpectedChar(c, "was expecting either '*' or '/' for a comment");
        }
    }

    private void _skipCComment()
        throws IOException, JsonParseException
    {
        // Need to be UTF-8 aware here to decode content (for skipping)
        final int[] codes = CharTypes.getInputCodeComment();

        // Ok: need the matching '*/'
        main_loop:
        while ((_inputPtr < _inputEnd) || loadMore()) {
            int i = (int) _inputBuffer[_inputPtr++] & 0xFF;
            int code = codes[i];
            if (code != 0) {
                switch (code) {
                case INT_ASTERISK:
                    if (_inputPtr >= _inputEnd && !loadMore()) {
                        break main_loop;
                    }
                    if (_inputBuffer[_inputPtr] == INT_SLASH) {
                        ++_inputPtr;
                        return;
                    }
                    break;
                case INT_LF:
                    _skipLF();
                    break;
                case INT_CR:
                    _skipCR();
                    break;
                case 2: // 2-byte UTF
                    _skipUtf8_2(i);
                    break;
                case 3: // 3-byte UTF
                    _skipUtf8_3(i);
                    break;
                case 4: // 4-byte UTF
                    _skipUtf8_4(i);
                    break;
                default: // e.g. -1
                    // Is this good enough error message?
                    _reportInvalidChar(i);
                }
            }
        }
        _reportInvalidEOF(" in a comment");
    }

    private void _skipCppComment()
        throws IOException, JsonParseException
    {
        // Ok: need to find EOF or linefeed
        final int[] codes = CharTypes.getInputCodeComment();
        while ((_inputPtr < _inputEnd) || loadMore()) {
            int i = (int) _inputBuffer[_inputPtr++] & 0xFF;
            int code = codes[i];
            if (code != 0) {
                switch (code) {
                case INT_LF:
                    _skipLF();
                    return;
                case INT_CR:
                    _skipCR();
                    return;
                case INT_ASTERISK: // nop for these comments
                    break;
                case 2: // 2-byte UTF
                    _skipUtf8_2(i);
                    break;
                case 3: // 3-byte UTF
                    _skipUtf8_3(i);
                    break;
                case 4: // 4-byte UTF
                    _skipUtf8_4(i);
                    break;
                default: // e.g. -1
                    // Is this good enough error message?
                    _reportInvalidChar(i);
                }
            }
        }
    }

    @Override
    protected char _decodeEscaped()
        throws IOException, JsonParseException
    {
        if (_inputPtr >= _inputEnd) {
            if (!loadMore()) {
                _reportInvalidEOF(" in character escape sequence");
            }
        }
        int c = (int) _inputBuffer[_inputPtr++];

        switch ((int) c) {
            // First, ones that are mapped
        case INT_b:
            return '\b';
        case INT_t:
            return '\t';
        case INT_n:
            return '\n';
        case INT_f:
            return '\f';
        case INT_r:
            return '\r';

            // And these are to be returned as they are
        case INT_QUOTE:
        case INT_SLASH:
        case INT_BACKSLASH:
            return (char) c;

        case INT_u: // and finally hex-escaped
            break;

        default:
            return _handleUnrecognizedCharacterEscape((char) _decodeCharForError(c));
        }

        // Ok, a hex escape. Need 4 characters
        int value = 0;
        for (int i = 0; i < 4; ++i) {
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    _reportInvalidEOF(" in character escape sequence");
                }
            }
            int ch = (int) _inputBuffer[_inputPtr++];
            int digit = CharTypes.charToHex(ch);
            if (digit < 0) {
                _reportUnexpectedChar(ch, "expected a hex-digit for character escape sequence");
            }
            value = (value << 4) | digit;
        }
        return (char) value;
    }

    protected int _decodeCharForError(int firstByte)
        throws IOException, JsonParseException
    {
        int c = (int) firstByte;
        if (c < 0) { // if >= 0, is ascii and fine as is
            int needed;
            
            // Ok; if we end here, we got multi-byte combination
            if ((c & 0xE0) == 0xC0) { // 2 bytes (0x0080 - 0x07FF)
                c &= 0x1F;
                needed = 1;
            } else if ((c & 0xF0) == 0xE0) { // 3 bytes (0x0800 - 0xFFFF)
                c &= 0x0F;
                needed = 2;
            } else if ((c & 0xF8) == 0xF0) {
                // 4 bytes; double-char with surrogates and all...
                c &= 0x07;
                needed = 3;
            } else {
                _reportInvalidInitial(c & 0xFF);
                needed = 1; // never gets here
            }

            int d = nextByte();
            if ((d & 0xC0) != 0x080) {
                _reportInvalidOther(d & 0xFF);
            }
            c = (c << 6) | (d & 0x3F);
            
            if (needed > 1) { // needed == 1 means 2 bytes total
                d = nextByte(); // 3rd byte
                if ((d & 0xC0) != 0x080) {
                    _reportInvalidOther(d & 0xFF);
                }
                c = (c << 6) | (d & 0x3F);
                if (needed > 2) { // 4 bytes? (need surrogates)
                    d = nextByte();
                    if ((d & 0xC0) != 0x080) {
                        _reportInvalidOther(d & 0xFF);
                    }
                    c = (c << 6) | (d & 0x3F);
                }
            }
        }
        return c;
    }

    /*
    /**********************************************************
    /* Internal methods,UTF8 decoding
    /**********************************************************
     */

    private int _decodeUtf8_2(int c)
        throws IOException, JsonParseException
    {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c & 0x1F) << 6) | (d & 0x3F);
    }

    private int _decodeUtf8_3(int c1)
        throws IOException, JsonParseException
    {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        c1 &= 0x0F;
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        int c = (c1 << 6) | (d & 0x3F);
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        return c;
    }

    private int _decodeUtf8_3fast(int c1)
        throws IOException, JsonParseException
    {
        c1 &= 0x0F;
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        int c = (c1 << 6) | (d & 0x3F);
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        return c;
    }

    /**
     * @return Character value <b>minus 0x10000</c>; this so that caller
     *    can readily expand it to actual surrogates
     */
    private int _decodeUtf8_4(int c)
        throws IOException, JsonParseException
    {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = ((c & 0x07) << 6) | (d & 0x3F);

        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }

        /* note: won't change it to negative here, since caller
         * already knows it'll need a surrogate
         */
        return ((c << 6) | (d & 0x3F)) - 0x10000;
    }

    private void _skipUtf8_2(int c)
        throws IOException, JsonParseException
    {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        c = (int) _inputBuffer[_inputPtr++];
        if ((c & 0xC0) != 0x080) {
            _reportInvalidOther(c & 0xFF, _inputPtr);
        }
    }

    /* Alas, can't heavily optimize skipping, since we still have to
     * do validity checks...
     */
    private void _skipUtf8_3(int c)
        throws IOException, JsonParseException
    {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        //c &= 0x0F;
        c = (int) _inputBuffer[_inputPtr++];
        if ((c & 0xC0) != 0x080) {
            _reportInvalidOther(c & 0xFF, _inputPtr);
        }
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        c = (int) _inputBuffer[_inputPtr++];
        if ((c & 0xC0) != 0x080) {
            _reportInvalidOther(c & 0xFF, _inputPtr);
        }
    }

    private void _skipUtf8_4(int c)
        throws IOException, JsonParseException
    {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
    }

    /*
    /**********************************************************
    /* Internal methods, input loading
    /**********************************************************
     */

    /**
     * We actually need to check the character value here
     * (to see if we have \n following \r).
     */
    protected void _skipCR() throws IOException
    {
        if (_inputPtr < _inputEnd || loadMore()) {
            if (_inputBuffer[_inputPtr] == BYTE_LF) {
                ++_inputPtr;
            }
        }
        ++_currInputRow;
        _currInputRowStart = _inputPtr;
    }

    protected void _skipLF() throws IOException
    {
        ++_currInputRow;
        _currInputRowStart = _inputPtr;
    }

    private int nextByte()
        throws IOException, JsonParseException
    {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        return _inputBuffer[_inputPtr++] & 0xFF;
    }

    /*
    /**********************************************************
    /* Internal methods, error reporting
    /**********************************************************
     */

    protected void _reportInvalidChar(int c)
        throws JsonParseException
    {
        // Either invalid WS or illegal UTF-8 start char
        if (c < INT_SPACE) {
            _throwInvalidSpace(c);
        }
        _reportInvalidInitial(c);
    }

    protected void _reportInvalidInitial(int mask)
        throws JsonParseException
    {
        _reportError("Invalid UTF-8 start byte 0x"+Integer.toHexString(mask));
    }

    protected void _reportInvalidOther(int mask)
        throws JsonParseException
    {
        _reportError("Invalid UTF-8 middle byte 0x"+Integer.toHexString(mask));
    }

    protected void _reportInvalidOther(int mask, int ptr)
        throws JsonParseException
    {
        _inputPtr = ptr;
        _reportInvalidOther(mask);
    }

    public static int[] growArrayBy(int[] arr, int more)
    {
        if (arr == null) {
            return new int[more];
        }
        int[] old = arr;
        int len = arr.length;
        arr = new int[len + more];
        System.arraycopy(old, 0, arr, 0, len);
        return arr;
    }

    /*
    /**********************************************************
    /* Binary access
    /**********************************************************
     */

    /**
     * Efficient handling for incremental parsing of base64-encoded
     * textual content.
     */
    protected byte[] _decodeBase64(Base64Variant b64variant)
        throws IOException, JsonParseException
    {
        ByteArrayBuilder builder = _getByteArrayBuilder();

        //main_loop:
        while (true) {
            // first, we'll skip preceding white space, if any
            int ch;
            do {
                if (_inputPtr >= _inputEnd) {
                    loadMoreGuaranteed();
                }
                ch = (int) _inputBuffer[_inputPtr++] & 0xFF;
            } while (ch <= INT_SPACE);
            int bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) { // reached the end, fair and square?
                if (ch == INT_QUOTE) {
                    return builder.toByteArray();
                }
                bits = _decodeBase64Escape(b64variant, ch, 0);
                if (bits < 0) { // white space to skip
                    continue;
                }
            }
            int decodedData = bits;
            
            // then second base64 char; can't get padding yet, nor ws
            
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) {
                bits = _decodeBase64Escape(b64variant, ch, 1);
            }
            decodedData = (decodedData << 6) | bits;
            
            // third base64 char; can be padding, but not ws
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            bits = b64variant.decodeBase64Char(ch);

            // First branch: can get padding (-> 1 byte)
            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    // as per [JACKSON-631], could also just be 'missing'  padding
                    if (ch == '"' && !b64variant.usesPadding()) {
                        decodedData >>= 4;
                        builder.append(decodedData);
                        return builder.toByteArray();
                    }
                    bits = _decodeBase64Escape(b64variant, ch, 2);
                }
                if (bits == Base64Variant.BASE64_VALUE_PADDING) {
                    // Ok, must get padding
                    if (_inputPtr >= _inputEnd) {
                        loadMoreGuaranteed();
                    }
                    ch = _inputBuffer[_inputPtr++] & 0xFF;
                    if (!b64variant.usesPaddingChar(ch)) {
                        throw reportInvalidBase64Char(b64variant, ch, 3, "expected padding character '"+b64variant.getPaddingChar()+"'");
                    }
                    // Got 12 bits, only need 8, need to shift
                    decodedData >>= 4;
                    builder.append(decodedData);
                    continue;
                }
            }
            // Nope, 2 or 3 bytes
            decodedData = (decodedData << 6) | bits;
            // fourth and last base64 char; can be padding, but not ws
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    // as per [JACKSON-631], could also just be 'missing'  padding
                    if (ch == '"' && !b64variant.usesPadding()) {
                        decodedData >>= 2;
                        builder.appendTwoBytes(decodedData);
                        return builder.toByteArray();
                    }
                    bits = _decodeBase64Escape(b64variant, ch, 3);
                }
                if (bits == Base64Variant.BASE64_VALUE_PADDING) {
                    /* With padding we only get 2 bytes; but we have
                     * to shift it a bit so it is identical to triplet
                     * case with partial output.
                     * 3 chars gives 3x6 == 18 bits, of which 2 are
                     * dummies, need to discard:
                     */
                    decodedData >>= 2;
                    builder.appendTwoBytes(decodedData);
                    continue;
                }
            }
            // otherwise, our triplet is now complete
            decodedData = (decodedData << 6) | bits;
            builder.appendThreeBytes(decodedData);
        }
    }
}

/**
 * This class was forked from com.fasterxml.jackson.core.base.ParserBase
 * in order to be able to limit lengths of text values in JSON text.
 */
abstract class ParserBase extends ParserMinimalBase {
    /*
    /**********************************************************
    /* Generic I/O state
    /**********************************************************
     */

    /**
     * I/O context for this reader. It handles buffer allocation
     * for the reader.
     */
    final protected IOContext _ioContext;

    /**
     * Flag that indicates whether parser is closed or not. Gets
     * set when parser is either closed by explicit call
     * ({@link #close}) or when end-of-input is reached.
     */
    protected boolean _closed;

    /*
    /**********************************************************
    /* Current input data
    /**********************************************************
     */

    // Note: type of actual buffer depends on sub-class, can't include

    /**
     * Pointer to next available character in buffer
     */
    protected int _inputPtr = 0;

    /**
     * Index of character after last available one in the buffer.
     */
    protected int _inputEnd = 0;
    
    /*
    /**********************************************************
    /* Current input location information
    /**********************************************************
     */

    /**
     * Number of characters/bytes that were contained in previous blocks
     * (blocks that were already processed prior to the current buffer).
     */
    protected long _currInputProcessed = 0L;

    /**
     * Current row location of current point in input buffer, starting
     * from 1, if available.
     */
    protected int _currInputRow = 1;

    /**
     * Current index of the first character of the current row in input
     * buffer. Needed to calculate column position, if necessary; benefit
     * of not having column itself is that this only has to be updated
     * once per line.
     */
    protected int _currInputRowStart = 0;

    /*
    /**********************************************************
    /* Information about starting location of event
    /* Reader is pointing to; updated on-demand
    /**********************************************************
     */

    // // // Location info at point when current token was started

    /**
     * Total number of bytes/characters read before start of current token.
     * For big (gigabyte-sized) sizes are possible, needs to be long,
     * unlike pointers and sizes related to in-memory buffers.
     */
    protected long _tokenInputTotal = 0; 

    /**
     * Input row on which current token starts, 1-based
     */
    protected int _tokenInputRow = 1;

    /**
     * Column on input row that current token starts; 0-based (although
     * in the end it'll be converted to 1-based)
     */
    protected int _tokenInputCol = 0;

    /*
    /**********************************************************
    /* Parsing state
    /**********************************************************
     */

    /**
     * Information about parser context, context in which
     * the next token is to be parsed (root, array, object).
     */
    protected JsonReadContext _parsingContext;
    
    /**
     * Secondary token related to the next token after current one;
     * used if its type is known. This may be value token that
     * follows FIELD_NAME, for example.
     */
    protected JsonToken _nextToken;

    /*
    /**********************************************************
    /* Buffer(s) for local name(s) and text content
    /**********************************************************
     */

    /**
     * Buffer that contains contents of String values, including
     * field names if necessary (name split across boundary,
     * contains escape sequence, or access needed to char array)
     */
    protected final TextBuffer _textBuffer;

    /**
     * Temporary buffer that is needed if field name is accessed
     * using {@link #getTextCharacters} method (instead of String
     * returning alternatives)
     */
    protected char[] _nameCopyBuffer = null;

    /**
     * Flag set to indicate whether the field name is available
     * from the name copy buffer or not (in addition to its String
     * representation  being available via read context)
     */
    protected boolean _nameCopied = false;

    /**
     * ByteArrayBuilder is needed if 'getBinaryValue' is called. If so,
     * we better reuse it for remainder of content.
     */
    protected ByteArrayBuilder _byteArrayBuilder = null;

    /**
     * We will hold on to decoded binary data, for duration of
     * current event, so that multiple calls to
     * {@link #getBinaryValue} will not need to decode data more
     * than once.
     */
    protected byte[] _binaryValue;

    /*
    /**********************************************************
    /* Constants and fields of former 'JsonNumericParserBase'
    /**********************************************************
     */

    final protected static int NR_UNKNOWN = 0;

    // First, integer types

    final protected static int NR_INT = 0x0001;
    final protected static int NR_LONG = 0x0002;
    final protected static int NR_BIGINT = 0x0004;

    // And then floating point types

    final protected static int NR_DOUBLE = 0x008;
    final protected static int NR_BIGDECIMAL = 0x0010;

    // Also, we need some numeric constants

    final static BigInteger BI_MIN_INT = BigInteger.valueOf(Integer.MIN_VALUE);
    final static BigInteger BI_MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);

    final static BigInteger BI_MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    final static BigInteger BI_MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    
    final static BigDecimal BD_MIN_LONG = new BigDecimal(BI_MIN_LONG);
    final static BigDecimal BD_MAX_LONG = new BigDecimal(BI_MAX_LONG);

    final static BigDecimal BD_MIN_INT = new BigDecimal(BI_MIN_INT);
    final static BigDecimal BD_MAX_INT = new BigDecimal(BI_MAX_INT);

    final static long MIN_INT_L = (long) Integer.MIN_VALUE;
    final static long MAX_INT_L = (long) Integer.MAX_VALUE;

    // These are not very accurate, but have to do... (for bounds checks)

    final static double MIN_LONG_D = (double) Long.MIN_VALUE;
    final static double MAX_LONG_D = (double) Long.MAX_VALUE;

    final static double MIN_INT_D = (double) Integer.MIN_VALUE;
    final static double MAX_INT_D = (double) Integer.MAX_VALUE;
    
    
    // Digits, numeric
    final protected static int INT_0 = '0';
    final protected static int INT_1 = '1';
    final protected static int INT_2 = '2';
    final protected static int INT_3 = '3';
    final protected static int INT_4 = '4';
    final protected static int INT_5 = '5';
    final protected static int INT_6 = '6';
    final protected static int INT_7 = '7';
    final protected static int INT_8 = '8';
    final protected static int INT_9 = '9';

    final protected static int INT_MINUS = '-';
    final protected static int INT_PLUS = '+';
    final protected static int INT_DECIMAL_POINT = '.';

    final protected static int INT_e = 'e';
    final protected static int INT_E = 'E';

    final protected static char CHAR_NULL = '\0';
    
    // Numeric value holders: multiple fields used for
    // for efficiency

    /**
     * Bitfield that indicates which numeric representations
     * have been calculated for the current type
     */
    protected int _numTypesValid = NR_UNKNOWN;

    // First primitives

    protected int _numberInt;

    protected long _numberLong;

    protected double _numberDouble;

    // And then object types

    protected BigInteger _numberBigInt;

    protected BigDecimal _numberBigDecimal;

    // And then other information about value itself

    /**
     * Flag that indicates whether numeric value has a negative
     * value. That is, whether its textual representation starts
     * with minus character.
     */
    protected boolean _numberNegative;

    /**
     * Length of integer part of the number, in characters
     */
    protected int _intLength;

    /**
     * Length of the fractional part (not including decimal
     * point or exponent), in characters.
     * Not used for  pure integer values.
     */
    protected int _fractLength;

    /**
     * Length of the exponent part of the number, if any, not
     * including 'e' marker or sign, just digits. 
     * Not used for  pure integer values.
     */
    protected int _expLength;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    protected ParserBase(int features, BufferRecycler _bufferRecycler, Object sourceRef, boolean managedResource)
    {
        super();
        _features = features;
        _ioContext = new IOContext(_bufferRecycler, sourceRef, managedResource);
        _textBuffer = new TextBuffer(_bufferRecycler);  //ctxt.constructTextBuffer();
        _parsingContext = JsonReadContext.createRootContext();
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************
    /* JsonParser impl
    /**********************************************************
     */
    
    /**
     * Method that can be called to get the name associated with
     * the current event.
     */
    @Override
    public String getCurrentName()
        throws IOException, JsonParseException
    {
        // [JACKSON-395]: start markers require information from parent
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            JsonReadContext parent = _parsingContext.getParent();
            return parent.getCurrentName();
        }
        return _parsingContext.getCurrentName();
    }

    @Override
    public void overrideCurrentName(String name)
    {
        // Simple, but need to look for START_OBJECT/ARRAY's "off-by-one" thing:
        JsonReadContext ctxt = _parsingContext;
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            ctxt = ctxt.getParent();
        }
        ctxt.setCurrentName(name);
    }
    
    @Override
    public void close() throws IOException
    {
        if (!_closed) {
            _closed = true;
            try {
                _closeInput();
            } finally {
                // as per [JACKSON-324], do in finally block
                // Also, internal buffer(s) can now be released as well
                _releaseBuffers();
            }
        }
    }

    @Override
    public boolean isClosed() { return _closed; }

    @Override
    public JsonReadContext getParsingContext()
    {
        return _parsingContext;
    }

    /**
     * Method that return the <b>starting</b> location of the current
     * token; that is, position of the first character from input
     * that starts the current token.
     */
    @Override
    public JsonLocation getTokenLocation()
    {
        return new JsonLocation(_ioContext.getSourceReference(),
                                getTokenCharacterOffset(),
                                getTokenLineNr(),
                                getTokenColumnNr());
    }

    /**
     * Method that returns location of the last processed character;
     * usually for error reporting purposes
     */
    @Override
    public JsonLocation getCurrentLocation()
    {
        int col = _inputPtr - _currInputRowStart + 1; // 1-based
        return new JsonLocation(_ioContext.getSourceReference(),
                                _currInputProcessed + _inputPtr - 1,
                                _currInputRow, col);
    }

    /*
    /**********************************************************
    /* Public API, access to token information, text and similar
    /**********************************************************
     */

    @Override
    public boolean hasTextCharacters()
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            return true; // usually true
        }        
        if (_currToken == JsonToken.FIELD_NAME) {
            return _nameCopied;
        }
        return false;
    }

    // No embedded objects with base impl...
    @Override
    public Object getEmbeddedObject() throws IOException, JsonParseException {
        return null;
    }
    
    /*
    /**********************************************************
    /* Public low-level accessors
    /**********************************************************
     */

    public long getTokenCharacterOffset() { return _tokenInputTotal; }
    public int getTokenLineNr() { return _tokenInputRow; }
    public int getTokenColumnNr() {
        // note: value of -1 means "not available"; otherwise convert from 0-based to 1-based
        int col = _tokenInputCol;
        return (col < 0) ? col : (col + 1);
    }

    /*
    /**********************************************************
    /* Low-level reading, other
    /**********************************************************
     */

    protected final void loadMoreGuaranteed()
        throws IOException
    {
        if (!loadMore()) {
            _reportInvalidEOF();
        }
    }
    
    /*
    /**********************************************************
    /* Abstract methods needed from sub-classes
    /**********************************************************
     */

    protected abstract boolean loadMore() throws IOException;
    
    protected abstract void _finishString() throws IOException, JsonParseException;

    protected abstract void _closeInput() throws IOException;
    
    /*
    /**********************************************************
    /* Low-level reading, other
    /**********************************************************
     */

    /**
     * Method called to release internal buffers owned by the base
     * reader. This may be called along with {@link #_closeInput} (for
     * example, when explicitly closing this reader instance), or
     * separately (if need be).
     */
    protected void _releaseBuffers() throws IOException
    {
        _textBuffer.releaseBuffers();
        char[] buf = _nameCopyBuffer;
        if (buf != null) {
            _nameCopyBuffer = null;
            _ioContext.releaseNameCopyBuffer(buf);
        }
    }
    
    /**
     * Method called when an EOF is encountered between tokens.
     * If so, it may be a legitimate EOF, but only iff there
     * is no open non-root context.
     */
    @Override
    protected void _handleEOF() throws JsonParseException
    {
        if (!_parsingContext.inRoot()) {
            _reportInvalidEOF(": expected close marker for "+_parsingContext.getTypeDesc()+" (from "+_parsingContext.getStartLocation(_ioContext.getSourceReference())+")");
        }
    }

    /*
    /**********************************************************
    /* Internal/package methods: Error reporting
    /**********************************************************
     */
    
    protected void _reportMismatchedEndMarker(int actCh, char expCh)
        throws JsonParseException
    {
        String startDesc = ""+_parsingContext.getStartLocation(_ioContext.getSourceReference());
        _reportError("Unexpected close marker '"+((char) actCh)+"': expected '"+expCh+"' (for "+_parsingContext.getTypeDesc()+" starting at "+startDesc+")");
    }

    /*
    /**********************************************************
    /* Internal/package methods: shared/reusable builders
    /**********************************************************
     */
    
    public ByteArrayBuilder _getByteArrayBuilder()
    {
        if (_byteArrayBuilder == null) {
            _byteArrayBuilder = new ByteArrayBuilder();
        } else {
            _byteArrayBuilder.reset();
        }
        return _byteArrayBuilder;
    }

    /*
    /**********************************************************
    /* Methods from former JsonNumericParserBase
    /**********************************************************
     */

    // // // Life-cycle of number-parsing
    
    protected final JsonToken reset(boolean negative, int intLen, int fractLen, int expLen)
    {
        if (fractLen < 1 && expLen < 1) { // integer
            return resetInt(negative, intLen);
        }
        return resetFloat(negative, intLen, fractLen, expLen);
    }
        
    protected final JsonToken resetInt(boolean negative, int intLen)
    {
        _numberNegative = negative;
        _intLength = intLen;
        _fractLength = 0;
        _expLength = 0;
        _numTypesValid = NR_UNKNOWN; // to force parsing
        return JsonToken.VALUE_NUMBER_INT;
    }
    
    protected final JsonToken resetFloat(boolean negative, int intLen, int fractLen, int expLen)
    {
        _numberNegative = negative;
        _intLength = intLen;
        _fractLength = fractLen;
        _expLength = expLen;
        _numTypesValid = NR_UNKNOWN; // to force parsing
        return JsonToken.VALUE_NUMBER_FLOAT;
    }
    
    protected final JsonToken resetAsNaN(String valueStr, double value)
    {
        _textBuffer.resetWithString(valueStr);
        _numberDouble = value;
        _numTypesValid = NR_DOUBLE;
        return JsonToken.VALUE_NUMBER_FLOAT;
    }
    
    /*
    /**********************************************************
    /* Numeric accessors of public API
    /**********************************************************
     */
    
    @Override
    public Number getNumberValue() throws IOException, JsonParseException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            _parseNumericValue(NR_UNKNOWN); // will also check event type
        }
        // Separate types for int types
        if (_currToken == JsonToken.VALUE_NUMBER_INT) {
            if ((_numTypesValid & NR_INT) != 0) {
                return _numberInt;
            }
            if ((_numTypesValid & NR_LONG) != 0) {
                return _numberLong;
            }
            if ((_numTypesValid & NR_BIGINT) != 0) {
                return _numberBigInt;
            }
            // Shouldn't get this far but if we do
            return _numberBigDecimal;
        }
    
        /* And then floating point types. But here optimal type
         * needs to be big decimal, to avoid losing any data?
         */
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            return _numberBigDecimal;
        }
        if ((_numTypesValid & NR_DOUBLE) == 0) { // sanity check
            _throwInternal();
        }
        return _numberDouble;
    }
    
    @Override
    public NumberType getNumberType() throws IOException, JsonParseException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            _parseNumericValue(NR_UNKNOWN); // will also check event type
        }
        if (_currToken == JsonToken.VALUE_NUMBER_INT) {
            if ((_numTypesValid & NR_INT) != 0) {
                return NumberType.INT;
            }
            if ((_numTypesValid & NR_LONG) != 0) {
                return NumberType.LONG;
            }
            return NumberType.BIG_INTEGER;
        }
    
        /* And then floating point types. Here optimal type
         * needs to be big decimal, to avoid losing any data?
         * However... using BD is slow, so let's allow returning
         * double as type if no explicit call has been made to access
         * data as BD?
         */
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            return NumberType.BIG_DECIMAL;
        }
        return NumberType.DOUBLE;
    }
    
    @Override
    public int getIntValue() throws IOException, JsonParseException
    {
        if ((_numTypesValid & NR_INT) == 0) {
            if (_numTypesValid == NR_UNKNOWN) { // not parsed at all
                _parseNumericValue(NR_INT); // will also check event type
            }
            if ((_numTypesValid & NR_INT) == 0) { // wasn't an int natively?
                convertNumberToInt(); // let's make it so, if possible
            }
        }
        return _numberInt;
    }
    
    @Override
    public long getLongValue() throws IOException, JsonParseException
    {
    	// TODO: check for bounds
        if ((_numTypesValid & NR_LONG) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _parseNumericValue(NR_LONG);
            }
            if ((_numTypesValid & NR_LONG) == 0) {
                convertNumberToLong();
            }
        }
        return _numberLong;
    }
    
    @Override
    public BigInteger getBigIntegerValue() throws IOException, JsonParseException
    {
        if ((_numTypesValid & NR_BIGINT) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _parseNumericValue(NR_BIGINT);
            }
            if ((_numTypesValid & NR_BIGINT) == 0) {
                convertNumberToBigInteger();
            }
        }
        return _numberBigInt;
    }
    
    @Override
    public float getFloatValue() throws IOException, JsonParseException
    {
        double value = getDoubleValue();
        /* 22-Jan-2009, tatu: Bounds/range checks would be tricky
         *   here, so let's not bother even trying...
         */
        /*
        if (value < -Float.MAX_VALUE || value > MAX_FLOAT_D) {
            _reportError("Numeric value ("+getText()+") out of range of Java float");
        }
        */
        return (float) value;
    }
    
    @Override
    public double getDoubleValue() throws IOException, JsonParseException
    {
    	// TODO: check for NaNs and Infs
        if ((_numTypesValid & NR_DOUBLE) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _parseNumericValue(NR_DOUBLE);
            }
            if ((_numTypesValid & NR_DOUBLE) == 0) {
                convertNumberToDouble();
            }
        }
        return _numberDouble;
    }
    
    @Override
    public BigDecimal getDecimalValue() throws IOException, JsonParseException
    {
        if ((_numTypesValid & NR_BIGDECIMAL) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _parseNumericValue(NR_BIGDECIMAL);
            }
            if ((_numTypesValid & NR_BIGDECIMAL) == 0) {
                convertNumberToBigDecimal();
            }
        }
        return _numberBigDecimal;
    }

    /*
    /**********************************************************
    /* Conversion from textual to numeric representation
    /**********************************************************
     */
    
    /**
     * Method that will parse actual numeric value out of a syntactically
     * valid number value. Type it will parse into depends on whether
     * it is a floating point number, as well as its magnitude: smallest
     * legal type (of ones available) is used for efficiency.
     *
     * @param expType Numeric type that we will immediately need, if any;
     *   mostly necessary to optimize handling of floating point numbers
     */
    protected void _parseNumericValue(int expType)
        throws IOException, JsonParseException
    {
        // Int or float?
        if (_currToken == JsonToken.VALUE_NUMBER_INT) {
            char[] buf = _textBuffer.getTextBuffer();
            int offset = _textBuffer.getTextOffset();
            int len = _intLength;
            if (_numberNegative) {
                ++offset;
            }
            if (len <= 9) { // definitely fits in int
                int i = NumberInput.parseInt(buf, offset, len);
                _numberInt = _numberNegative ? -i : i;
                _numTypesValid = NR_INT;
                return;
            }
            if (len <= 18) { // definitely fits AND is easy to parse using 2 int parse calls
                long l = NumberInput.parseLong(buf, offset, len);
                if (_numberNegative) {
                    l = -l;
                }
                // [JACKSON-230] Could still fit in int, need to check
                if (len == 10) {
                    if (_numberNegative) {
                        if (l >= MIN_INT_L) {
                            _numberInt = (int) l;
                            _numTypesValid = NR_INT;
                            return;
                        }
                    } else {
                        if (l <= MAX_INT_L) {
                            _numberInt = (int) l;
                            _numTypesValid = NR_INT;
                            return;
                        }
                    }
                }
                _numberLong = l;
                _numTypesValid = NR_LONG;
                return;
            }
            _parseSlowIntValue(expType, buf, offset, len);
            return;
        }
        if (_currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            _parseSlowFloatValue(expType);
            return;
        }
        _reportError("Current token ("+_currToken+") not numeric, can not use numeric value accessors");
    }
    
    private void _parseSlowFloatValue(int expType)
        throws IOException, JsonParseException
    {
        /* Nope: floating point. Here we need to be careful to get
         * optimal parsing strategy: choice is between accurate but
         * slow (BigDecimal) and lossy but fast (Double). For now
         * let's only use BD when explicitly requested -- it can
         * still be constructed correctly at any point since we do
         * retain textual representation
         */
        try {
            if (expType == NR_BIGDECIMAL) {
                _numberBigDecimal = _textBuffer.contentsAsDecimal();
                _numTypesValid = NR_BIGDECIMAL;
            } else {
                // Otherwise double has to do
                _numberDouble = _textBuffer.contentsAsDouble();
                _numTypesValid = NR_DOUBLE;
            }
        } catch (NumberFormatException nex) {
            // Can this ever occur? Due to overflow, maybe?
            _wrapError("Malformed numeric value '"+_textBuffer.contentsAsString()+"'", nex);
        }
    }
    
    private void _parseSlowIntValue(int expType, char[] buf, int offset, int len)
        throws IOException, JsonParseException
    {
        String numStr = _textBuffer.contentsAsString();
        try {
            // [JACKSON-230] Some long cases still...
            if (NumberInput.inLongRange(buf, offset, len, _numberNegative)) {
                // Probably faster to construct a String, call parse, than to use BigInteger
                _numberLong = Long.parseLong(numStr);
                _numTypesValid = NR_LONG;
            } else {
                // nope, need the heavy guns... (rare case)
                _numberBigInt = new BigInteger(numStr);
                _numTypesValid = NR_BIGINT;
            }
        } catch (NumberFormatException nex) {
            // Can this ever occur? Due to overflow, maybe?
            _wrapError("Malformed numeric value '"+numStr+"'", nex);
        }
    }
    
    /*
    /**********************************************************
    /* Numeric conversions
    /**********************************************************
     */    
    
    protected void convertNumberToInt()
        throws IOException, JsonParseException
    {
        // First, converting from long ought to be easy
        if ((_numTypesValid & NR_LONG) != 0) {
            // Let's verify it's lossless conversion by simple roundtrip
            int result = (int) _numberLong;
            if (((long) result) != _numberLong) {
                _reportError("Numeric value ("+getText()+") out of range of int");
            }
            _numberInt = result;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_INT.compareTo(_numberBigInt) > 0 
                    || BI_MAX_INT.compareTo(_numberBigInt) < 0) {
                reportOverflowInt();
            }
            _numberInt = _numberBigInt.intValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            // Need to check boundaries
            if (_numberDouble < MIN_INT_D || _numberDouble > MAX_INT_D) {
                reportOverflowInt();
            }
            _numberInt = (int) _numberDouble;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_INT.compareTo(_numberBigDecimal) > 0 
                || BD_MAX_INT.compareTo(_numberBigDecimal) < 0) {
                reportOverflowInt();
            }
            _numberInt = _numberBigDecimal.intValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_INT;
    }
    
    protected void convertNumberToLong()
        throws IOException, JsonParseException
    {
        if ((_numTypesValid & NR_INT) != 0) {
            _numberLong = (long) _numberInt;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_LONG.compareTo(_numberBigInt) > 0 
                    || BI_MAX_LONG.compareTo(_numberBigInt) < 0) {
                reportOverflowLong();
            }
            _numberLong = _numberBigInt.longValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            // Need to check boundaries
            if (_numberDouble < MIN_LONG_D || _numberDouble > MAX_LONG_D) {
                reportOverflowLong();
            }
            _numberLong = (long) _numberDouble;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_LONG.compareTo(_numberBigDecimal) > 0 
                || BD_MAX_LONG.compareTo(_numberBigDecimal) < 0) {
                reportOverflowLong();
            }
            _numberLong = _numberBigDecimal.longValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_LONG;
    }
    
    protected void convertNumberToBigInteger()
        throws IOException, JsonParseException
    {
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            // here it'll just get truncated, no exceptions thrown
            _numberBigInt = _numberBigDecimal.toBigInteger();
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberBigInt = BigInteger.valueOf(_numberLong);
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberBigInt = BigInteger.valueOf(_numberInt);
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            _numberBigInt = BigDecimal.valueOf(_numberDouble).toBigInteger();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_BIGINT;
    }
    
    protected void convertNumberToDouble()
        throws IOException, JsonParseException
    {
        /* 05-Aug-2008, tatus: Important note: this MUST start with
         *   more accurate representations, since we don't know which
         *   value is the original one (others get generated when
         *   requested)
         */
    
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            _numberDouble = _numberBigDecimal.doubleValue();
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            _numberDouble = _numberBigInt.doubleValue();
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberDouble = (double) _numberLong;
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberDouble = (double) _numberInt;
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_DOUBLE;
    }
    
    protected void convertNumberToBigDecimal()
        throws IOException, JsonParseException
    {
        /* 05-Aug-2008, tatus: Important note: this MUST start with
         *   more accurate representations, since we don't know which
         *   value is the original one (others get generated when
         *   requested)
         */
    
        if ((_numTypesValid & NR_DOUBLE) != 0) {
            /* Let's actually parse from String representation,
             * to avoid rounding errors that non-decimal floating operations
             * would incur
             */
            _numberBigDecimal = new BigDecimal(getText());
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            _numberBigDecimal = new BigDecimal(_numberBigInt);
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberBigDecimal = BigDecimal.valueOf(_numberLong);
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberBigDecimal = BigDecimal.valueOf((long) _numberInt);
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_BIGDECIMAL;
    }
    
    /*
    /**********************************************************
    /* Number handling exceptions
    /**********************************************************
     */    
    
    protected void reportUnexpectedNumberChar(int ch, String comment)
        throws JsonParseException
    {
        String msg = "Unexpected character ("+_getCharDesc(ch)+") in numeric value";
        if (comment != null) {
            msg += ": "+comment;
        }
        _reportError(msg);
    }
    
    protected void reportInvalidNumber(String msg)
        throws JsonParseException
    {
        _reportError("Invalid numeric value: "+msg);
    }
    
    protected void reportOverflowInt()
        throws IOException, JsonParseException
    {
        _reportError("Numeric value ("+getText()+") out of range of int ("+Integer.MIN_VALUE+" - "+Integer.MAX_VALUE+")");
    }
    
    protected void reportOverflowLong()
        throws IOException, JsonParseException
    {
        _reportError("Numeric value ("+getText()+") out of range of long ("+Long.MIN_VALUE+" - "+Long.MAX_VALUE+")");
    }    

    /*
    /**********************************************************
    /* Base64 handling support
    /**********************************************************
     */

    /**
     * Method that sub-classes must implement to support escaped sequences
     * in base64-encoded sections.
     * Sub-classes that do not need base64 support can leave this as is
     */
    protected char _decodeEscaped()
        throws IOException, JsonParseException {
        throw new UnsupportedOperationException();
    }
    
    protected final int _decodeBase64Escape(Base64Variant b64variant, int ch, int index)
        throws IOException, JsonParseException
    {
        // 17-May-2011, tatu: As per [JACKSON-xxx], need to handle escaped chars
        if (ch != '\\') {
            throw reportInvalidBase64Char(b64variant, ch, index);
        }
        int unescaped = _decodeEscaped();
        // if white space, skip if first triplet; otherwise errors
        if (unescaped <= INT_SPACE) {
            if (index == 0) { // whitespace only allowed to be skipped between triplets
                return -1;
            }
        }
        // otherwise try to find actual triplet value
        int bits = b64variant.decodeBase64Char(unescaped);
        if (bits < 0) {
            throw reportInvalidBase64Char(b64variant, unescaped, index);
        }
        return bits;
    }
    
    protected final int _decodeBase64Escape(Base64Variant b64variant, char ch, int index)
        throws IOException, JsonParseException
    {
        // 17-May-2011, tatu: As per [JACKSON-xxx], need to handle escaped chars
        if (ch != '\\') {
            throw reportInvalidBase64Char(b64variant, ch, index);
        }
        char unescaped = _decodeEscaped();
        // if white space, skip if first triplet; otherwise errors
        if (unescaped <= INT_SPACE) {
            if (index == 0) { // whitespace only allowed to be skipped between triplets
                return -1;
            }
        }
        // otherwise try to find actual triplet value
        int bits = b64variant.decodeBase64Char(unescaped);
        if (bits < 0) {
            throw reportInvalidBase64Char(b64variant, unescaped, index);
        }
        return bits;
    }
    
    protected IllegalArgumentException reportInvalidBase64Char(Base64Variant b64variant, int ch, int bindex)
        throws IllegalArgumentException
    {
        return reportInvalidBase64Char(b64variant, ch, bindex, null);
    }

    /**
     * @param bindex Relative index within base64 character unit; between 0
     *   and 3 (as unit has exactly 4 characters)
     */
    protected IllegalArgumentException reportInvalidBase64Char(Base64Variant b64variant, int ch, int bindex, String msg)
        throws IllegalArgumentException
    {
        String base;
        if (ch <= INT_SPACE) {
            base = "Illegal white space character (code 0x"+Integer.toHexString(ch)+") as character #"+(bindex+1)+" of 4-char base64 unit: can only used between units";
        } else if (b64variant.usesPaddingChar(ch)) {
            base = "Unexpected padding character ('"+b64variant.getPaddingChar()+"') as character #"+(bindex+1)+" of 4-char base64 unit: padding only legal as 3rd or 4th character";
        } else if (!Character.isDefined(ch) || Character.isISOControl(ch)) {
            // Not sure if we can really get here... ? (most illegal xml chars are caught at lower level)
            base = "Illegal character (code 0x"+Integer.toHexString(ch)+") in base64 content";
        } else {
            base = "Illegal character '"+((char)ch)+"' (code 0x"+Integer.toHexString(ch)+") in base64 content";
        }
        if (msg != null) {
            base = base + ": " + msg;
        }
        return new IllegalArgumentException(base);
    }
}

/**
 * This class was forked from com.fasterxml.jackson.core.util.TextBuffer in order
 * to be able to work with char buffers of very large lengths.
 */
class TextBuffer
{
    final static char[] NO_CHARS = new char[0];

    /**
     * Let's start with sizable but not huge buffer, will grow as necessary
     */
    final static int MIN_SEGMENT_LEN = 1000;
    
    /**
     * Let's limit maximum segment length to something sensible
     * like 256k
     */
    final static int MAX_SEGMENT_LEN = 0x40000;
    
    /*
    /**********************************************************
    /* Configuration:
    /**********************************************************
     */

    private final BufferRecycler _allocator;

    /*
    /**********************************************************
    /* Shared input buffers
    /**********************************************************
     */

    /**
     * Shared input buffer; stored here in case some input can be returned
     * as is, without being copied to collector's own buffers. Note that
     * this is read-only for this Object.
     */
    private char[] _inputBuffer;

    /**
     * Character offset of first char in input buffer; -1 to indicate
     * that input buffer currently does not contain any useful char data
     */
    private int _inputStart;

    private int _inputLen;

    /*
    /**********************************************************
    /* Aggregation segments (when not using input buf)
    /**********************************************************
     */

    /**
     * List of segments prior to currently active segment.
     */
    private ArrayList<char[]> _segments;

    /**
     * Flag that indicates whether _seqments is non-empty
     */
    private boolean _hasSegments = false;

    // // // Currently used segment; not (yet) contained in _seqments

    /**
     * Amount of characters in segments in {@link _segments}
     */
    private int _segmentSize;

    private char[] _currentSegment;

    /**
     * Number of characters in currently active (last) segment
     */
    private int _currentSize;

    /*
    /**********************************************************
    /* Caching of results
    /**********************************************************
     */

    /**
     * String that will be constructed when the whole contents are
     * needed; will be temporarily stored in case asked for again.
     */
    private String _resultString;

    private char[] _resultArray;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public TextBuffer(BufferRecycler allocator)
    {
        _allocator = allocator;
    }

    /**
     * Method called to indicate that the underlying buffers should now
     * be recycled if they haven't yet been recycled. Although caller
     * can still use this text buffer, it is not advisable to call this
     * method if that is likely, since next time a buffer is needed,
     * buffers need to reallocated.
     * Note: calling this method automatically also clears contents
     * of the buffer.
     */
    public void releaseBuffers()
    {
        if (_allocator == null) {
            resetWithEmpty();
        } else {
            if (_currentSegment != null) {
                // First, let's get rid of all but the largest char array
                resetWithEmpty();
                // And then return that array
                char[] buf = _currentSegment;
                _currentSegment = null;
                _allocator.releaseCharBuffer(BufferRecycler.CharBufferType.TEXT_BUFFER, buf);
            }
        }
    }

    /**
     * Method called to clear out any content text buffer may have, and
     * initializes buffer to use non-shared data.
     */
    public void resetWithEmpty()
    {
        _inputStart = -1; // indicates shared buffer not used
        _currentSize = 0;
        _inputLen = 0;

        _inputBuffer = null;
        _resultString = null;
        _resultArray = null;

        // And then reset internal input buffers, if necessary:
        if (_hasSegments) {
            clearSegments();
        }
    }

    /**
     * Method called to initialize the buffer with a shared copy of data;
     * this means that buffer will just have pointers to actual data. It
     * also means that if anything is to be appended to the buffer, it
     * will first have to unshare it (make a local copy).
     */
    public void resetWithShared(char[] buf, int start, int len)
    {
        // First, let's clear intermediate values, if any:
        _resultString = null;
        _resultArray = null;

        // Then let's mark things we need about input buffer
        _inputBuffer = buf;
        _inputStart = start;
        _inputLen = len;

        // And then reset internal input buffers, if necessary:
        if (_hasSegments) {
            clearSegments();
        }
    }

    public void resetWithCopy(char[] buf, int start, int len)
    {
        _inputBuffer = null;
        _inputStart = -1; // indicates shared buffer not used
        _inputLen = 0;

        _resultString = null;
        _resultArray = null;

        // And then reset internal input buffers, if necessary:
        if (_hasSegments) {
            clearSegments();
        } else if (_currentSegment == null) {
            _currentSegment = findBuffer(len);
        }
        _currentSize = _segmentSize = 0;
        append(buf, start, len);
    }

    public void resetWithString(String value)
    {
        _inputBuffer = null;
        _inputStart = -1;
        _inputLen = 0;

        _resultString = value;
        _resultArray = null;

        if (_hasSegments) {
            clearSegments();
        }
        _currentSize = 0;
        
    }
    
    /**
     * Helper method used to find a buffer to use, ideally one
     * recycled earlier.
     */
    private char[] findBuffer(int needed)
    {
        if (_allocator != null) {
            return _allocator.allocCharBuffer(BufferRecycler.CharBufferType.TEXT_BUFFER, needed);
        }
        return new char[Math.max(needed, MIN_SEGMENT_LEN)];
    }

    private void clearSegments()
    {
        _hasSegments = false;
        /* Let's start using _last_ segment from list; for one, it's
         * the biggest one, and it's also most likely to be cached
         */
        /* 28-Aug-2009, tatu: Actually, the current segment should
         *   be the biggest one, already
         */
        //_currentSegment = _segments.get(_segments.size() - 1);
        _segments.clear();
        _currentSize = _segmentSize = 0;
    }

    /*
    /**********************************************************
    /* Accessors for implementing public interface
    /**********************************************************
     */

    /**
     * @return Number of characters currently stored by this collector
     */
    public int size() {
        if (_inputStart >= 0) { // shared copy from input buf
            return _inputLen;
        }
        if (_resultArray != null) {
            return _resultArray.length;
        }
        if (_resultString != null) {
            return _resultString.length();
        }
        // local segmented buffers
        return _segmentSize + _currentSize;
    }

    public int getTextOffset()
    {
        /* Only shared input buffer can have non-zero offset; buffer
         * segments start at 0, and if we have to create a combo buffer,
         * that too will start from beginning of the buffer
         */
        return (_inputStart >= 0) ? _inputStart : 0;
    }

    /**
     * Method that can be used to check whether textual contents can
     * be efficiently accessed using {@link #getTextBuffer}.
     */
    public boolean hasTextAsCharacters()
    {
        // if we have array in some form, sure
        if (_inputStart >= 0 || _resultArray != null) {
            return true;
        }
        // not if we have String as value
        if (_resultString != null) {
            return false;
        }
        return true;
    }
    
    public char[] getTextBuffer()
    {
        // Are we just using shared input buffer?
        if (_inputStart >= 0) {
            return _inputBuffer;
        }
        if (_resultArray != null) {
            return _resultArray;
        }
        if (_resultString != null) {
            return (_resultArray = _resultString.toCharArray());
        }
        // Nope; but does it fit in just one segment?
        if (!_hasSegments) {
            return _currentSegment;
        }
        // Nope, need to have/create a non-segmented array and return it
        return contentsAsArray();
    }

    /*
    /**********************************************************
    /* Other accessors:
    /**********************************************************
     */

    public String contentsAsString()
    {
        if (_resultString == null) {
            // Has array been requested? Can make a shortcut, if so:
            if (_resultArray != null) {
                _resultString = new String(_resultArray);
            } else {
                // Do we use shared array?
                if (_inputStart >= 0) {
                    if (_inputLen < 1) {
                        return (_resultString = "");
                    }
                    _resultString = new String(_inputBuffer, _inputStart, _inputLen);
                } else { // nope... need to copy
                    // But first, let's see if we have just one buffer
                    int segLen = _segmentSize;
                    int currLen = _currentSize;
                    
                    if (segLen == 0) { // yup
                        _resultString = (currLen == 0) ? "" : new String(_currentSegment, 0, currLen);
                    } else { // no, need to combine
                        StringBuilder sb = new StringBuilder(segLen + currLen);
                        // First stored segments
                        if (_segments != null) {
                            for (int i = 0, len = _segments.size(); i < len; ++i) {
                                char[] curr = _segments.get(i);
                                sb.append(curr, 0, curr.length);
                            }
                        }
                        // And finally, current segment:
                        sb.append(_currentSegment, 0, _currentSize);
                        _resultString = sb.toString();
                    }
                }
            }
        }
        return _resultString;
    }
 
    public char[] contentsAsArray()
    {
        char[] result = _resultArray;
        if (result == null) {
            _resultArray = result = buildResultArray();
        }
        return result;
    }

    /**
     * Convenience method for converting contents of the buffer
     * into a {@link BigDecimal}.
     */
    public BigDecimal contentsAsDecimal()
        throws NumberFormatException
    {
        // Already got a pre-cut array?
        if (_resultArray != null) {
            return new BigDecimal(_resultArray);
        }
        // Or a shared buffer?
        if (_inputStart >= 0) {
            return new BigDecimal(_inputBuffer, _inputStart, _inputLen);
        }
        // Or if not, just a single buffer (the usual case)
        if (_segmentSize == 0) {
            return new BigDecimal(_currentSegment, 0, _currentSize);
        }
        // If not, let's just get it aggregated...
        return new BigDecimal(contentsAsArray());
    }

    /**
     * Convenience method for converting contents of the buffer
     * into a Double value.
     */
    public double contentsAsDouble()
        throws NumberFormatException
    {
        return NumberInput.parseDouble(contentsAsString());
    }

    /*
    /**********************************************************
    /* Public mutators:
    /**********************************************************
     */

    /**
     * Method called to make sure that buffer is not using shared input
     * buffer; if it is, it will copy such contents to private buffer.
     */
    public void ensureNotShared() {
        if (_inputStart >= 0) {
            unshare(16);
        }
    }

    public void append(char c) {
        // Using shared buffer so far?
        if (_inputStart >= 0) {
            unshare(16);
        }
        _resultString = null;
        _resultArray = null;
        // Room in current segment?
        char[] curr = _currentSegment;
        if (_currentSize >= curr.length) {
            expand(1);
            curr = _currentSegment;
        }
        curr[_currentSize++] = c;
    }

    public void append(char[] c, int start, int len)
    {
        // Can't append to shared buf (sanity check)
        if (_inputStart >= 0) {
            unshare(len);
        }
        _resultString = null;
        _resultArray = null;

        // Room in current segment?
        char[] curr = _currentSegment;
        int max = curr.length - _currentSize;
            
        if (max >= len) {
            System.arraycopy(c, start, curr, _currentSize, len);
            _currentSize += len;
            return;
        }
        // No room for all, need to copy part(s):
        if (max > 0) {
            System.arraycopy(c, start, curr, _currentSize, max);
            start += max;
            len -= max;
        }
        /* And then allocate new segment; we are guaranteed to now
         * have enough room in segment.
         */
        // Except, as per [Issue-24], not for HUGE appends... so:
        do {
            expand(len);
            int amount = Math.min(_currentSegment.length, len);
            System.arraycopy(c, start, _currentSegment, 0, amount);
            _currentSize += amount;
            start += amount;
            len -= amount;
        } while (len > 0);
    }

    public void append(String str, int offset, int len)
    {
        // Can't append to shared buf (sanity check)
        if (_inputStart >= 0) {
            unshare(len);
        }
        _resultString = null;
        _resultArray = null;

        // Room in current segment?
        char[] curr = _currentSegment;
        int max = curr.length - _currentSize;
        if (max >= len) {
            str.getChars(offset, offset+len, curr, _currentSize);
            _currentSize += len;
            return;
        }
        // No room for all, need to copy part(s):
        if (max > 0) {
            str.getChars(offset, offset+max, curr, _currentSize);
            len -= max;
            offset += max;
        }
        /* And then allocate new segment; we are guaranteed to now
         * have enough room in segment.
         */
        // Except, as per [Issue-24], not for HUGE appends... so:
        do {
            expand(len);
            int amount = Math.min(_currentSegment.length, len);
            str.getChars(offset, offset+amount, _currentSegment, 0);
            _currentSize += amount;
            offset += amount;
            len -= amount;
        } while (len > 0);
    }

    /*
    /**********************************************************
    /* Raw access, for high-performance use:
    /**********************************************************
     */

    public char[] getCurrentSegment()
    {
        /* Since the intention of the caller is to directly add stuff into
         * buffers, we should NOT have anything in shared buffer... ie. may
         * need to unshare contents.
         */
        if (_inputStart >= 0) {
            unshare(1);
        } else {
            char[] curr = _currentSegment;
            if (curr == null) {
                _currentSegment = findBuffer(0);
            } else if (_currentSize >= curr.length) {
                // Plus, we better have room for at least one more char
                expand(1);
            }
        }
        return _currentSegment;
    }

    public char[] emptyAndGetCurrentSegment()
    {
        // inlined 'resetWithEmpty()'
        _inputStart = -1; // indicates shared buffer not used
        _currentSize = 0;
        _inputLen = 0;

        _inputBuffer = null;
        _resultString = null;
        _resultArray = null;

        // And then reset internal input buffers, if necessary:
        if (_hasSegments) {
            clearSegments();
        }
        char[] curr = _currentSegment;
        if (curr == null) {
            _currentSegment = curr = findBuffer(0);
        }
        return curr;
    }

    public int getCurrentSegmentSize() {
        return _currentSize;
    }

    public void setCurrentLength(int len) {
        _currentSize = len;
    }

    public char[] finishCurrentSegment()
    {
        if (_segments == null) {
            _segments = new ArrayList<char[]>();
        }
        _hasSegments = true;
        _segments.add(_currentSegment);
        int oldLen = _currentSegment.length;
        _segmentSize += oldLen;
        // Let's grow segments by 50%
        int newLen = Math.min(oldLen + (oldLen >> 1), MAX_SEGMENT_LEN);
        char[] curr = _charArray(newLen);
        _currentSize = 0;
        _currentSegment = curr;
        return curr;
    }

    /**
     * Method called to expand size of the current segment, to
     * accomodate for more contiguous content. Usually only
     * used when parsing tokens like names.
     */
    public char[] expandCurrentSegment()
    {
        char[] curr = _currentSegment;
        // Let's grow by 50%
        int len = curr.length;
        // Must grow by at least 1 char, no matter what
        int newLen = (len == MAX_SEGMENT_LEN) ?
            (MAX_SEGMENT_LEN + 1) : Math.min(MAX_SEGMENT_LEN, len + (len >> 1));
        _currentSegment = _charArray(newLen);
        System.arraycopy(curr, 0, _currentSegment, 0, len);
        return _currentSegment;
    }

    /*
    /**********************************************************
    /* Standard methods:
    /**********************************************************
     */

    /**
     * Note: calling this method may not be as efficient as calling
     * {@link #contentsAsString}, since it's not guaranteed that resulting
     * String is cached.
     */
    @Override
    public String toString() {
         return contentsAsString();
    }

    /*
    /**********************************************************
    /* Internal methods:
    /**********************************************************
     */

    /**
     * Method called if/when we need to append content when we have been
     * initialized to use shared buffer.
     */
    private void unshare(int needExtra)
    {
        int sharedLen = _inputLen;
        _inputLen = 0;
        char[] inputBuf = _inputBuffer;
        _inputBuffer = null;
        int start = _inputStart;
        _inputStart = -1;

        // Is buffer big enough, or do we need to reallocate?
        int needed = sharedLen+needExtra;
        if (_currentSegment == null || needed > _currentSegment.length) {
            _currentSegment = findBuffer(needed);
        }
        if (sharedLen > 0) {
            System.arraycopy(inputBuf, start, _currentSegment, 0, sharedLen);
        }
        _segmentSize = 0;
        _currentSize = sharedLen;
    }

    /**
     * Method called when current segment is full, to allocate new
     * segment.
     */
    private void expand(int minNewSegmentSize)
    {
        // First, let's move current segment to segment list:
        if (_segments == null) {
            _segments = new ArrayList<char[]>();
        }
        char[] curr = _currentSegment;
        _hasSegments = true;
        _segments.add(curr);
        _segmentSize += curr.length;
        int oldLen = curr.length;
        // Let's grow segments by 50% minimum
        int sizeAddition = oldLen >> 1;
        if (sizeAddition < minNewSegmentSize) {
            sizeAddition = minNewSegmentSize;
        }
        curr = _charArray(Math.min(MAX_SEGMENT_LEN, oldLen + sizeAddition));
        _currentSize = 0;
        _currentSegment = curr;
    }

    private char[] buildResultArray()
    {
        if (_resultString != null) { // Can take a shortcut...
            return _resultString.toCharArray();
        }
        char[] result;
        
        // Do we use shared array?
        if (_inputStart >= 0) {
            if (_inputLen < 1) {
                return NO_CHARS;
            }
            result = _charArray(_inputLen);
            System.arraycopy(_inputBuffer, _inputStart, result, 0,
                             _inputLen);
        } else { // nope 
            int size = size();
            if (size < 1) {
                return NO_CHARS;
            }
            int offset = 0;
            result = _charArray(size);
            if (_segments != null) {
                for (int i = 0, len = _segments.size(); i < len; ++i) {
                    char[] curr = (char[]) _segments.get(i);
                    int currLen = curr.length;
                    System.arraycopy(curr, 0, result, offset, currLen);
                    offset += currLen;
                }
            }
            System.arraycopy(_currentSegment, 0, result, offset, _currentSize);
        }
        return result;
    }

    private char[] _charArray(int len) {
        return new char[len];
    }
}
