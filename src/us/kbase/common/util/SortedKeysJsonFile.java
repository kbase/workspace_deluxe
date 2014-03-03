package us.kbase.common.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Class sorts map keys in JSON data stored in either in File or in byte array. 
 * Result of sorting is written into external output stream without modification 
 * of original data source. Code is optimized in the way of using as less memory 
 * as possible. The only case of large memory requirement is map with large 
 * count of keys is present in data. In order to sort keys of some map we need 
 * to store all keys of this map in memory. For default settings keys are stored 
 * in memory as byte arrays. So if the data contains few millions of keys in the
 * same map we need to keep in memory all these key values bytes plus about 24
 * bytes per key for mapping key to place of key-value data in data source.
 * @author Roman Sutormin (rsutormin)
 */
public class SortedKeysJsonFile {
	private final RandomAccessSource raf;
	private PosBufInputStream mainIs;
	private int maxBufferSize = 10 * 1024;
	private boolean skipKeyDuplication = false;
	private boolean useStringsForKeyStoring = false;
	private long maxMemoryForKeyStoring = -1;
	
	private static final ObjectMapper mapper = new ObjectMapper();
	private static final Charset utf8 = Charset.forName("UTF-8");
	
	/**
	 * Defines file as data source.
	 * @param f file data source
	 * @throws IOException
	 */
	public SortedKeysJsonFile(File f) throws IOException {
		raf = new RandomAccessSource(f);
	}

	/**
	 * Defines byte array as data source
	 * @param byteSource byte array data source
	 * @throws IOException
	 */
	public SortedKeysJsonFile(byte[] byteSource) throws IOException {
		raf = new RandomAccessSource(byteSource);
	}

	/**
	 * @return true if key duplication is skipped (ignored). false is default value.
	 */
	public boolean isSkipKeyDuplication() {
		return skipKeyDuplication;
	}

	/**
	 * Defines if key duplication should be skipped (ignored) or not. false means
	 * error is generated in case of duplication (default).
	 * @param skipKeyDuplication value to set
	 * @return this object for chaining
	 */
	public SortedKeysJsonFile setSkipKeyDuplication(boolean skipKeyDuplication) {
		this.skipKeyDuplication = skipKeyDuplication;
		return this;
	}

	/**
	 * @return true if string type is used for keeping key values in memory. false 
	 * is default value.
	 */
	public boolean isUseStringsForKeyStoring() {
		return useStringsForKeyStoring;
	}
	
	/**
	 * Defines if string type should be used for keeping key values in memory. 
	 * false means keys are kept as byte arrays (default).
	 * @param useStringsForKeyStoring
	 * @return this object for chaining
	 */
	public SortedKeysJsonFile setUseStringsForKeyStoring(boolean useStringsForKeyStoring) {
		this.useStringsForKeyStoring = useStringsForKeyStoring;
		return this;
	}
	
	/**
	 * @return size of memory buffer which is used for caching data fragments from 
	 * data source. Default value is 10k. It seems to be optimal because less value
	 * causes slower processing of unsortable data like lists, but greater value
	 * causes slower processing of maps as longer time is spent for loading larger 
	 * buffer from disk each time we jump between places of unsorted keys.
	 */
	public int getMaxBufferSize() {
		return maxBufferSize;
	}
	
	/**
	 * Defines size of memory buffer which is used for caching data fragments from 
	 * data source. Default value is 10k. It seems to be optimal because less value
	 * causes slower processing of unsortable data like lists, but greater value
	 * causes slower processing of maps as longer time is spent for loading larger 
	 * buffer from disk each time we jump between places of unsorted keys.
	 * @param maxBufferSize value to set
	 * @return this object for chaining
	 */
	public SortedKeysJsonFile setMaxBufferSize(int maxBufferSize) {
		this.maxBufferSize = maxBufferSize;
		return this;
	}
	/**
	 * @return limit of memory used for keeping keys for sorting. Default value is 
	 * -1 which means switching this limitation off.
	 */
	public long getMaxMemoryForKeyStoring() {
		return maxMemoryForKeyStoring;
	}
	
	/**
	 * Defines the limit of memory used for keeping keys for sorting. Use -1 or 0 
	 * for switching this limitation off.
	 * @param maxMemoryForKeyStoring value to set
	 * @return this object for chaining
	 */
	public SortedKeysJsonFile setMaxMemoryForKeyStoring(long maxMemoryForKeyStoring) {
		this.maxMemoryForKeyStoring = maxMemoryForKeyStoring;
		return this;
	}
	
	/**
	 * Method saves sorted data into output stream. It doesn't close internal input stream.
	 * So please call close() after calling this method. 
	 * @param os output stream for saving sorted result
	 * @return this object for chaining
	 * @throws IOException in case of problems with i/o or with JSON parsing
	 */
	public SortedKeysJsonFile writeIntoStream(OutputStream os) throws IOException {
		UnthreadedBufferedOutputStream ubos = new UnthreadedBufferedOutputStream(os, 100000);
		write(0, -1, maxMemoryForKeyStoring > 0 ? new long[] {0L} : null, ubos);
		ubos.flush();
		return this;
	}
	
	private void write(long globalStart, long globalStop, long[] keysByteSize, UnthreadedBufferedOutputStream os) throws IOException {
		PosBufInputStream is = setPosition(globalStart);
		while (true) {
			if (globalStop >= 0 && is.getFilePointer() >= globalStop)
				break;
			int b = is.read();
			if (b == -1)
				break;
			if (b == '{') {
				long[] keysByteSizeTemp = keysByteSize == null ? null : new long[] {keysByteSize[0]};
				List<KeyValueLocation> fieldPosList = searchForMapCloseBracket(is, true, keysByteSizeTemp);
				Collections.sort(fieldPosList);
				long stop = is.getFilePointer();  // After close bracket
				os.write(b);
				boolean wasEntry = false;
				KeyValueLocation prevLoc = null;
				for (KeyValueLocation loc : fieldPosList) {
					if (prevLoc != null && prevLoc.areKeysEqual(loc)) {
						if (skipKeyDuplication) {
							continue;
						} else {
							throw new IOException("Duplicated key: " + loc.getKey());
						}
					}
					if (wasEntry)
						os.write(',');
					write(loc.keyStart, loc.stop, keysByteSizeTemp, os);
					wasEntry = true;
					prevLoc = loc;
				}
				os.write('}');
				is.setGlobalPos(stop);
			} else if (b == '"') {
				os.write(b);
				while (true) {
					b = is.read();
					if (b == -1)
						throw new IOException("String close quot wasn't found");
					os.write(b);
					if (b == '"')
						break;
					if (b == '\\') {
						b = is.read();
						if (b == -1)
							throw new IOException("String close quot wasn't found");
						os.write(b);
					}
				}
			} else {
				os.write(b);
			}
		}
	}
	
	private PosBufInputStream setPosition(long pos) throws IOException {
		if (mainIs == null)
			mainIs = new PosBufInputStream(raf, maxBufferSize);
		return mainIs.setGlobalPos(pos);
	}
	
	private List<KeyValueLocation> searchForMapCloseBracket(PosBufInputStream raf, boolean createMap, long[] keysByteSize) throws IOException {
		List<KeyValueLocation> ret = createMap ? new ArrayList<KeyValueLocation>() : null;
		boolean isBeforeField = true;
		String currentKey = null;
		long currentKeyStart = -1;
		long currentValueStart = -1;
		while (true) {
			int b = raf.read();
			if (b == -1)
				throw new IOException("Mapping close bracket wasn't found");
			if (b == '}') {
				if (currentKey != null && createMap) {
					if (currentValueStart < 0 || currentKeyStart < 0)
						throw new IOException("Value without key in mapping");
					ret.add(new KeyValueLocation(currentKey,currentKeyStart, currentValueStart, raf.getFilePointer() - 1, useStringsForKeyStoring));
					if (keysByteSize != null)
						countKeysMemory(keysByteSize, currentKey);
					currentKey = null;
					currentKeyStart = -1;
					currentValueStart = -1;
				}
				break;
			} else if (b == '"') {
				if (isBeforeField) {
					currentKeyStart = raf.getFilePointer() - 1;
					currentKey = searchForEndQuot(raf, createMap);
				} else {
					searchForEndQuot(raf, false);
				}
			} else if (b == ':') {
				if (!isBeforeField)
					throw new IOException("Unexpected colon sign in the middle of value text");
				if (createMap) {
					if (currentKey == null)
						throw new IOException("Unexpected colon sign before key text");
					currentValueStart = raf.getFilePointer();
				}
				isBeforeField = false;
			} else if (b == '{') {
				if (isBeforeField)
					throw new IOException("Mapping opened before key text");
				searchForMapCloseBracket(raf, false, null);
			} else if (b == ',') {
				if (createMap) {
					if (currentKey == null)
						throw new IOException("Comma in mapping without key-value pair before");
					if (currentValueStart < 0 || currentKeyStart < 0)
						throw new IOException("Value without key in mapping");
					ret.add(new KeyValueLocation(currentKey, currentKeyStart, currentValueStart, raf.getFilePointer() - 1, useStringsForKeyStoring));
					if (keysByteSize != null)
						countKeysMemory(keysByteSize, currentKey);
					currentKey = null;
					currentKeyStart = -1;
					currentValueStart = -1;
				}
				isBeforeField = true;
			} else if (b == '[') {
				if (isBeforeField)
					throw new IOException("Array opened before key text");
				searchForArrayCloseBracket(raf);
			}
		}
		return ret;
	}

	private void countKeysMemory(long[] keysByteSize, String currentKey) throws IOException {
		keysByteSize[0] += useStringsForKeyStoring ? (2 * currentKey.length() + 8 + 4 + 3 * 8) : (currentKey.length() + 3 * 8);
		if (maxMemoryForKeyStoring > 0 && keysByteSize[0] > maxMemoryForKeyStoring)
			throw new IOException("Memory for keys were exceeded");
	}

	private void searchForArrayCloseBracket(PosBufInputStream raf) throws IOException {
		while (true) {
			int b = raf.read();
			if (b == -1)
				throw new IOException("Array close bracket wasn't found");
			if (b == ']') {
				break;
			} else if (b == '"') {
				searchForEndQuot(raf, false);
			} else if (b == '{') {
				searchForMapCloseBracket(raf, false, null);
			} else if (b == '[') {
				searchForArrayCloseBracket(raf);
			}
		}
	}

	private String searchForEndQuot(PosBufInputStream raf, boolean createString) throws IOException {
		ByteArrayOutputStream ret = null;
		if (createString) {
			ret = new ByteArrayOutputStream();
			ret.write('"');
		}
		while (true) {
			int b = raf.read();
			if (b == -1)
				throw new IOException("String close quot wasn't found");
			if (createString)
				ret.write(b);
			if (b == '"')
				break;
			if (b == '\\') {
				b = raf.read();
				if (b == -1)
					throw new IOException("String close quot wasn't found");
				if (createString)
					ret.write(b);
			}
		}
		if (createString)
			return mapper.readValue(ret.toByteArray(), String.class);
		return null;
	}
	
	/**
	 * Closing inner input streams after writing.
	 * @throws IOException
	 */
	public void close() throws IOException {
		raf.close();
	}
	
	private static class RandomAccessSource {
		private RandomAccessFile raf = null;
		private byte[] byteSrc = null;
		private int byteSrcPos = 0;
		
		public RandomAccessSource(File f) throws IOException {
			raf = new RandomAccessFile(f, "r");
		}

		public RandomAccessSource(byte[] array) throws IOException {
			byteSrc = array;
		}
		
	    public void seek(long pos) throws IOException {
	    	if (raf != null) {
	    		raf.seek(pos);
	    	} else {
	    		byteSrcPos = (int)pos;
	    	}
	    }

	    public int read(byte b[], int off, int len) throws IOException {
	    	if (raf != null) {
	    		return raf.read(b, off, len);
	    	} else {
	    		if (off + len > b.length)
	    			throw new IOException();
	    		if (byteSrcPos + len > byteSrc.length)
	    			len = byteSrc.length - byteSrcPos;
	    		if (len <= 0)
	    			return -1;
	    		System.arraycopy(byteSrc, byteSrcPos, b, off, len);
	    		byteSrcPos += len;
	    		return len;
	    	}
	    }
	    
		public void close() throws IOException {
			if (raf != null)
				raf.close();
		}
	}
	
	private static class PosBufInputStream {
		RandomAccessSource raf;
		private byte[] buffer;
		private long globalBufPos;
		private int posInBuf;
		private int bufSize;
		
		public PosBufInputStream(RandomAccessSource raf, int maxBufSize) {
			this.raf = raf;
			this.buffer = new byte[maxBufSize];
			this.globalBufPos = 0;
			this.posInBuf = 0;
			this.bufSize = 0;
		}
		
		public PosBufInputStream setGlobalPos(long pos) {
			if (pos >= globalBufPos && pos < globalBufPos + bufSize) {
				posInBuf = (int)(pos - globalBufPos);
			} else {
				globalBufPos = pos;
				posInBuf = 0;
				bufSize = 0;
			}
			return this;
		}
		
		public int read() throws IOException {
			if (posInBuf >= bufSize) {
				if (!nextBufferLoad())
					return -1;
			}
			int ret = buffer[posInBuf] & 0xff;
			posInBuf++;
			return ret;
		}
		
		public boolean nextBufferLoad() throws IOException {
			posInBuf = 0;
			globalBufPos += bufSize;
			raf.seek(globalBufPos);
			bufSize = 0;
			while (true) {
				int len = raf.read(buffer, bufSize, buffer.length - bufSize);
				if (len < 0)
					break;
				bufSize += len;
				if (bufSize == buffer.length)
					break;
			}
			return bufSize > 0;
		}
		
		public byte[] read(long start, byte[] array) throws IOException {
			setGlobalPos(start);
			for (int arrPos = 0; arrPos < array.length; arrPos++) {
				if (posInBuf >= bufSize) {
					if (!nextBufferLoad())
						throw new IOException("Unexpected end of file");
				}
				array[arrPos] = buffer[posInBuf];
				posInBuf++;
			}
			return array;
		}

		
		public long getFilePointer() {
			return globalBufPos + posInBuf;
		}
	}
	
	private static class KeyValueLocation implements Comparable<KeyValueLocation> {
		Object key;
		long keyStart;
		long stop;
		
		public KeyValueLocation(String key, long keyStart, long valueStart, long stop, boolean useString) {
			this.key = useString ? key : key.getBytes(utf8);
			this.keyStart = keyStart;
			this.stop = stop;
		}
		
		public String getKey() {
			if (key instanceof String)
				return (String)key;
			return new String((byte[])key, utf8);
		}
		
		@Override
		public String toString() {
			return key + "(" + keyStart + "-" + stop + ")";
		}
		
		@Override
		public int compareTo(KeyValueLocation o) {
			return getKey().compareTo(o.getKey());
		}
		
		public boolean areKeysEqual(KeyValueLocation loc) {
			if (key instanceof String || loc.key instanceof String) {
				return getKey().equals(loc.getKey());
			}
			byte[] key1 = (byte[])key;
			byte[] key2 = (byte[])loc.key;
			if (key1.length != key2.length)
				return false;
			for (int i = 0; i < key1.length; i++)
				if (key1[i] != key2[i])
					return false;
			return true;
		}
	}
	
	private static class UnthreadedBufferedOutputStream extends OutputStream {
	    OutputStream out;
	    byte buffer[];
	    int bufSize;

	    public UnthreadedBufferedOutputStream(OutputStream out, int size) throws IOException {
	        this.out = out;
	        if (size <= 0) {
	            throw new IOException("Buffer size should be a positive number");
	        }
	        buffer = new byte[size];
	    }

	    void flushBuffer() throws IOException {
	        if (bufSize > 0) {
	            out.write(buffer, 0, bufSize);
	            bufSize = 0;
	        }
	    }

	    public void write(int b) throws IOException {
	        if (bufSize >= buffer.length) {
	            flushBuffer();
	        }
	        buffer[bufSize++] = (byte)b;
	    }

	    public void write(byte b[]) throws IOException {
	        write(b, 0, b.length);
	    }

	    public void write(byte b[], int off, int len) throws IOException {
	        if (len >= buffer.length) {
	            flushBuffer();
	            out.write(b, off, len);
	            return;
	        }
	        if (len > buffer.length - bufSize) {
	            flushBuffer();
	        }
	        System.arraycopy(b, off, buffer, bufSize, len);
	        bufSize += len;
	    }

	    public void flush() throws IOException {
	        flushBuffer();
	        out.flush();
	    }
	    
	    public void close() throws IOException {
	    	flush();
	    }
	}
}
