package us.kbase.workspace.database.mongo;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import us.kbase.typedobj.core.MD5;

public class MD5DigestOutputStream extends OutputStream {

	private final MessageDigest digest;
	private long size = 0;
	
	public MD5DigestOutputStream() {
		try {
			digest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException nsae) {
			throw new RuntimeException("There definitely should be an MD5 digest");
		}
	}
	
	@Override
	public void write(byte[] b) throws IOException {
		digest.update(b);
		size += b.length;
	}
	
	public long getSize() {
		return size;
	}
	
	/**
	 * Get the MD5 of the output stream. This call resets the MD5 and the size
	 * to zero.
	 * @return the MD5 of the output stream.
	 */
	public MD5 getMD5() {
		final byte[] d = digest.digest();
		size = 0;
		final StringBuilder sb = new StringBuilder();
		for (final byte b : d) {
			sb.append(String.format("%02x", b));
		}
		return new MD5(sb);
	}
	
	@Override
	public void write(byte[] b, int offset, int len) throws IOException {
		digest.update(b, offset, len);
		size += len;
	}

	@Override
	public void write(final int b) throws IOException {
		throw new RuntimeException("Unimplemented");
	}
}
