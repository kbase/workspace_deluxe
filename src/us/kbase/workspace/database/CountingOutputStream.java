package us.kbase.workspace.database;

import java.io.IOException;
import java.io.OutputStream;

public class CountingOutputStream extends OutputStream {

	private long size = 0;
	
	
	@Override
	public void write(byte[] b) throws IOException {
		size += b.length;
	}
	
	public long getSize() {
		return size;
	}
	
	@Override
	public void write(byte[] b, int offset, int len) throws IOException {
		size += len;
	}

	@Override
	public void write(final int b) throws IOException {
		throw new RuntimeException("Unimplemented");
	}
}
