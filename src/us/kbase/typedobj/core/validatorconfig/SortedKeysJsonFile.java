package us.kbase.typedobj.core.validatorconfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.TreeMap;

import us.kbase.typedobj.core.validatornew.KBaseJsonTreeGenerator;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SortedKeysJsonFile {
	private final File f;
	private final RandomAccessFile raf;
	private final PosBufInputStream mainIs;
	private final PosBufInputStream smallIs;
	private int maxBufSize = 100000;
	
	private static final ObjectMapper mapper = new ObjectMapper();
	
	public SortedKeysJsonFile(File f) throws IOException {
		this.f = f;
		raf = new RandomAccessFile(f, "r");
		mainIs = new PosBufInputStream(raf, maxBufSize);
		smallIs = new PosBufInputStream(raf, maxBufSize);
	}
	
	public void writeIntoStream(OutputStream os) throws IOException {
		write(new UnthreadedBufferedOutputStream(os, maxBufSize));
	}
	
	private void write(OutputStream os) throws IOException {
		write(0, -1, os);
	}
	
	private void write(long globalStart, long globalStop, OutputStream os) throws IOException {
		PosBufInputStream is = setPosition(globalStart);
		while (true) {
			if (globalStop >= 0 && is.getFilePointer() >= globalStop)
				break;
			int b = is.read();
			if (b == -1)
				break;
			if (b == '{') {
				long start = is.getFilePointer() - 1;  // Open bracket
				Map<String, Location> fieldPosMap = searchForMapCloseBracket(is, true);
				long stop = is.getFilePointer();  // After close bracket
				if (false) { //stop - start < 1000) {
					int len = (int)(stop - start);
					byte[] arr = smallIs.read(start, new byte[(int)len]);
					Object obj = getSortedTree(arr);
					mapper.writeValue(os, obj);
				} else {
					os.write(b);
					boolean wasEntry = false;
					for (Map.Entry<String, Location> entry : fieldPosMap.entrySet()) {
						if (wasEntry)
							os.write(',');
						mapper.writeValue(os, entry.getKey());
						os.write(':');
						Location loc = entry.getValue();
						write(loc.start, loc.stop, os);
						wasEntry = true;
					}
					os.write('}');
					is.setGlobalPos(stop);
				}
			} else {
				os.write(b);
			}
		}
	}
	
	private Object getSortedTree(byte[] arr) throws IOException {
		KBaseJsonTreeGenerator jgen = new KBaseJsonTreeGenerator(mapper, true);
		TreeNode tree = mapper.readTree(arr);
		mapper.writeValue(jgen, tree);
		return jgen.getTree();
	}
	
	private PosBufInputStream setPosition(long pos) throws IOException {
		return mainIs.setGlobalPos(pos);
	}
	
	private Map<String, Location> searchForMapCloseBracket(PosBufInputStream raf, boolean createMap) throws IOException {
		Map<String, Location> ret = createMap ? new TreeMap<String, Location>() : null;
		boolean isBeforeField = true;
		String currentKey = null;
		long currentValueStart = -1;
		while (true) {
			int b = raf.read();
			if (b == -1)
				throw new IllegalStateException("Mapping close bracket wasn't found");
			if (b == '}') {
				if (currentKey != null && createMap) {
					if (currentValueStart < 0)
						throw new IllegalStateException("Value without key in mapping");
					ret.put(currentKey, new Location(currentValueStart, raf.getFilePointer() - 1));
					currentKey = null;
					currentValueStart = -1;
				}
				break;
			} else if (b == '"') {
				if (isBeforeField) {
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
					if (currentValueStart < 0)
						throw new IllegalStateException("Value without key in mapping");
					ret.put(currentKey, new Location(currentValueStart, raf.getFilePointer() - 1));
					currentKey = null;
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
				throw new IllegalStateException("Array close bracket wasn't found");
			if (createString)
				ret.write(b);
			if (b == '"')
				break;
			if (b == '\\') {
				b = raf.read();
				if (b == -1)
					throw new IllegalStateException("Array close bracket wasn't found");
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
	
	public static void main(String[] args) throws IOException {
		File dir = new File("/Users/rsutormin/Work/2014-01-15_hugeobject");
		File f = new File(dir, "network.json");
		File outFile = new File(dir, "network2.json");
		long time = System.currentTimeMillis();
		OutputStream os = new FileOutputStream(outFile);
		new SortedKeysJsonFile(f).writeIntoStream(os);
		os.close();
		System.out.println("Time: " + (System.currentTimeMillis() - time));
	}
	
	private static class PosBufInputStream {
		RandomAccessFile raf;
		private byte[] buffer;
		private long globalBufPos;
		private int posInBuf;
		private int bufSize;
		public PosBufInputStream(RandomAccessFile raf, int maxBufSize) {
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
	
	private static class Location {
		long start;
		long stop;
		
		public Location(long start, long stop) {
			this.start = start;
			this.stop = stop;
		}
		
		@Override
		public String toString() {
			return "(" + start + "-" + stop + ")";
		}
	}
	
	private static class UnthreadedBufferedOutputStream extends OutputStream {
	    protected OutputStream out;
	    protected byte buf[];
	    protected int count;

	    public UnthreadedBufferedOutputStream(OutputStream out, int size) {
	        this.out = out;
	        if (size <= 0) {
	            throw new IllegalArgumentException("Buffer size <= 0");
	        }
	        buf = new byte[size];
	    }

	    private void flushBuffer() throws IOException {
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
