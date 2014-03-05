package us.kbase.workspace.database;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.typedobj.core.ObjectPaths;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;

public class ByteArrayFileCacheManager {
	private int sizeInMem = 0;
	private final int maxSizeInMem;
	private long sizeOnDisk = 0;
	private final long maxSizeOnDisk;
	private final TempFilesManager tfm;
	
	public ByteArrayFileCacheManager(int maxSizeInMem, long maxSizeOnDisk, TempFilesManager tfm) {
		this.maxSizeInMem = maxSizeInMem;
		this.maxSizeOnDisk = maxSizeOnDisk;
		this.tfm = tfm;
	}
	
	public ByteArrayFileCache createBAFC(InputStream input) throws IOException {
		byte[] buf = new byte[100000];
		ByteArrayOutputStream bufOs = new ByteArrayOutputStream();
		int maxInMemorySize = maxSizeInMem - sizeInMem;
		long size = 0;
		while (size < maxInMemorySize) {
			int count = input.read(buf, 0, Math.min(buf.length, maxInMemorySize - (int)size));
			if (count < 0)
				break;
			bufOs.write(buf, 0, count);
			size += count;
		}
		bufOs.close();
		if (size >= maxInMemorySize) {
			File tempFile = null;
			OutputStream os = null;
			try {
				tempFile = tfm.generateTempFile("resp", "json");
				os = new BufferedOutputStream(
						new FileOutputStream(tempFile));
				os.write(bufOs.toByteArray());
				bufOs = null;
				while (true) {
					if (sizeOnDisk + size > maxSizeOnDisk)
						throw new IOException("Out of disk space limit");
					int count = input.read(buf, 0, buf.length);
					if (count < 0)
						break;
					os.write(buf, 0, count);
					size += count;
				}
				os.close();
				sizeOnDisk += size;
				return new ByteArrayFileCache(null, tempFile, new JsonTokenStream(tempFile));
			} catch (Throwable e) {
				if (os != null)
					try {
						os.close();
					} catch (Exception ignore) {}
				if (tempFile != null)
					tempFile.delete();
				if (e instanceof IOException)
					throw (IOException)e;
				if (e instanceof RuntimeException)
					throw (RuntimeException)e;
				throw new IllegalStateException(e.getMessage(), e);
			}
		} else {
			sizeInMem += (int)size;
			return new ByteArrayFileCache(null, null, new JsonTokenStream(bufOs.toByteArray()));
		}
	}

	public ByteArrayFileCache getSubdataExtraction(ByteArrayFileCache parent, ObjectPaths paths) throws TypedObjectExtractionException {
		final OutputStream[] origin = {new ByteArrayOutputStream()};
		final File[] tempFile = {null};
		final long[] size = {0L};
		OutputStream os = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				throw new IllegalStateException("Single byte writing is not supported");
			}
			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				origin[0].write(b, off, len);
				size[0] += len;
				if (tempFile[0] == null) {
					if (sizeInMem + size[0] > maxSizeInMem) {
						origin[0].close();
						byte[] arr = ((ByteArrayOutputStream)origin[0]).toByteArray();
						tempFile[0] = tfm.generateTempFile("resp", "json");
						origin[0] = new BufferedOutputStream(new FileOutputStream(tempFile[0]));
						origin[0].write(arr);
					}
				} else {
					if (sizeOnDisk + size[0] > maxSizeOnDisk)
						throw new IOException("Out of disk space limit");
				}
			}
			@Override
			public void close() throws IOException {
				origin[0].close();
			}
		};
		try {
			parent.getSubdataExtractionAsStream(paths, os);
			if (tempFile[0] != null) {
				sizeOnDisk += size[0];
				return new ByteArrayFileCache(parent, tempFile[0], new JsonTokenStream(tempFile[0]));
			} else {
				sizeInMem += (int)size[0];
				byte[] arr = ((ByteArrayOutputStream)origin[0]).toByteArray();
				return new ByteArrayFileCache(parent, null, new JsonTokenStream(arr));
			}
		} catch (Throwable e) {
			try {
				os.close();
			} catch (Exception ignore) {}
			if (tempFile[0] != null)
				tempFile[0].delete();
			if (e instanceof TypedObjectExtractionException)
				throw (TypedObjectExtractionException)e;
			if (e instanceof RuntimeException)
				throw (RuntimeException)e;
			throw new IllegalStateException(e.getMessage(), e);
		}
	}
	
	public static ByteArrayFileCacheManager forTests() {
		return new ByteArrayFileCacheManager(16000000, 2000000000L, TempFilesManager.forTests());
	}
}
