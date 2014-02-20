package us.kbase.typedobj.core.validatorconfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SortedKeysJsonFile {
	private final RandomAccessSource raf;
	private final PosBufInputStream mainIs;
	private int maxBufSize;
	
	private static final ObjectMapper mapper = new ObjectMapper();
	public static final int DEFAULT_BUFFER_SIZE = 100000; 
	
	public SortedKeysJsonFile(File f) throws IOException {
		this(f, DEFAULT_BUFFER_SIZE);
	}
	
	public SortedKeysJsonFile(File f, int maxBufferSize) throws IOException {
		maxBufSize = maxBufferSize;
		raf = new RandomAccessSource(f);
		mainIs = new PosBufInputStream(raf, maxBufSize);
	}

	public SortedKeysJsonFile(byte[] byteSource) throws IOException {
		maxBufSize = DEFAULT_BUFFER_SIZE;
		raf = new RandomAccessSource(byteSource);
		mainIs = new PosBufInputStream(raf, maxBufSize);
	}

	public SortedKeysJsonFile writeIntoStream(OutputStream os) throws IOException {
		UnthreadedBufferedOutputStream ubos = new UnthreadedBufferedOutputStream(os, maxBufSize);
		write(ubos);
		ubos.flush();
		return this;
	}
	
	private void write(UnthreadedBufferedOutputStream os) throws IOException {
		write(0, -1, os);
	}
	
	private void write(long globalStart, long globalStop, UnthreadedBufferedOutputStream os) throws IOException {
		PosBufInputStream is = setPosition(globalStart);
		while (true) {
			if (globalStop >= 0 && is.getFilePointer() >= globalStop)
				break;
			int b = is.read();
			if (b == -1)
				break;
			if (b == '{') {
				//long start = is.getFilePointer() - 1;  // Open bracket
				List<KeyLocation> fieldPosList = searchForMapCloseBracket(is, true);
				Collections.sort(fieldPosList);
				checkForKeyDuplications(fieldPosList);
				long stop = is.getFilePointer();  // After close bracket
				os.write(b);
				boolean wasEntry = false;
				for (KeyLocation loc : fieldPosList) {
					if (wasEntry)
						os.write(',');
					write(loc.keyStart, loc.stop, os);
					wasEntry = true;
				}
				os.write('}');
				is.setGlobalPos(stop);
			} else if (b == '"') {
				os.write(b);
				while (true) {
					b = is.read();
					if (b == -1)
						throw new IllegalStateException("String close quot wasn't found");
					os.write(b);
					if (b == '"')
						break;
					if (b == '\\') {
						b = is.read();
						if (b == -1)
							throw new IllegalStateException("String close quot wasn't found");
						os.write(b);
					}
				}
			} else {
				os.write(b);
			}
		}
	}
	
	private void checkForKeyDuplications(List<KeyLocation> keyList) throws IOException {
		String prevKey = null;
		for (KeyLocation loc : keyList) {
			if (prevKey != null && prevKey.equals(loc.key))
				throw new IOException("Duplicated key: " + prevKey);
			prevKey = loc.key;
		}
	}
	
	private PosBufInputStream setPosition(long pos) throws IOException {
		return mainIs.setGlobalPos(pos);
	}
	
	private List<KeyLocation> searchForMapCloseBracket(PosBufInputStream raf, boolean createMap) throws IOException {
		List<KeyLocation> ret = createMap ? new ArrayList<KeyLocation>() : null;
		boolean isBeforeField = true;
		String currentKey = null;
		long currentKeyStart = -1;
		long currentValueStart = -1;
		while (true) {
			int b = raf.read();
			if (b == -1)
				throw new IllegalStateException("Mapping close bracket wasn't found");
			if (b == '}') {
				if (currentKey != null && createMap) {
					if (currentValueStart < 0 || currentKeyStart < 0)
						throw new IllegalStateException("Value without key in mapping");
					ret.add(new KeyLocation(currentKey,currentKeyStart, currentValueStart, raf.getFilePointer() - 1));
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
					throw new IllegalStateException("Unexpected colon sign in the middle of value text");
				if (createMap) {
					if (currentKey == null)
						throw new IllegalStateException("Unexpected colon sign before key text");
					currentValueStart = raf.getFilePointer();
				}
				isBeforeField = false;
			} else if (b == '{') {
				if (isBeforeField)
					throw new IllegalStateException("Mapping opened before key text");
				searchForMapCloseBracket(raf, false);
			} else if (b == ',') {
				if (createMap) {
					if (currentKey == null)
						throw new IllegalStateException("Comma in mapping without key-value pair before");
					if (currentValueStart < 0 || currentKeyStart < 0)
						throw new IllegalStateException("Value without key in mapping");
					ret.add(new KeyLocation(currentKey, currentKeyStart, currentValueStart, raf.getFilePointer() - 1));
					currentKey = null;
					currentKeyStart = -1;
					currentValueStart = -1;
				}
				isBeforeField = true;
			} else if (b == '[') {
				if (isBeforeField)
					throw new IllegalStateException("Array opened before key text");
				searchForArrayCloseBracket(raf);
			}
		}
		return ret;
	}

	private void searchForArrayCloseBracket(PosBufInputStream raf) throws IOException {
		while (true) {
			int b = raf.read();
			if (b == -1)
				throw new IllegalStateException("Array close bracket wasn't found");
			if (b == ']') {
				break;
			} else if (b == '"') {
				searchForEndQuot(raf, false);
			} else if (b == '{') {
				searchForMapCloseBracket(raf, false);
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
				throw new IllegalStateException("String close quot wasn't found");
			if (createString)
				ret.write(b);
			if (b == '"')
				break;
			if (b == '\\') {
				b = raf.read();
				if (b == -1)
					throw new IllegalStateException("String close quot wasn't found");
				if (createString)
					ret.write(b);
			}
		}
		if (createString)
			return mapper.readValue(ret.toByteArray(), String.class);
		return null;
	}
	
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
	
	public static class PosBufInputStream {
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
						throw new IllegalStateException("Unexpected end of file");
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
	
	private static class KeyLocation implements Comparable<KeyLocation> {
		String key;
		long keyStart;
		long valueStart;
		long stop;
		
		public KeyLocation(String key, long fieldStart, long start, long stop) {
			this.key = key;
			this.keyStart = fieldStart;
			this.valueStart = start;
			this.stop = stop;
		}
		
		@Override
		public String toString() {
			return key + "(" + keyStart + ":" + valueStart + "-" + stop + ")";
		}
		
		@Override
		public int compareTo(KeyLocation o) {
			return key.compareTo(o.key);
		}
	}
	
	private static class UnthreadedBufferedOutputStream extends OutputStream {
	    OutputStream out;
	    byte buf[];
	    int count;

	    public UnthreadedBufferedOutputStream(OutputStream out, int size) {
	        this.out = out;
	        if (size <= 0) {
	            throw new IllegalArgumentException("Buffer size <= 0");
	        }
	        buf = new byte[size];
	    }

	    void flushBuffer() throws IOException {
	        if (count > 0) {
	            out.write(buf, 0, count);
	            count = 0;
	        }
	    }

	    public void write(int b) throws IOException {
	        if (count >= buf.length) {
	            flushBuffer();
	        }
	        buf[count++] = (byte)b;
	    }

	    public void write(byte b[]) throws IOException {
	        write(b, 0, b.length);
	    }

	    public void write(byte b[], int off, int len) throws IOException {
	        if (len >= buf.length) {
	            flushBuffer();
	            out.write(b, off, len);
	            return;
	        }
	        if (len > buf.length - count) {
	            flushBuffer();
	        }
	        System.arraycopy(b, off, buf, count, len);
	        count += len;
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
