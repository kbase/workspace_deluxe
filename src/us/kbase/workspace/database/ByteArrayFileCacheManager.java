package us.kbase.workspace.database;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.ObjectPaths;
import us.kbase.typedobj.core.SubdataExtractor;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;
import us.kbase.workspace.database.exceptions.FileCacheIOException;
import us.kbase.workspace.database.exceptions.FileCacheLimitExceededException;

public class ByteArrayFileCacheManager {
	
	//TODO unit tests
	
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
	
	public ByteArrayFileCache createBAFC(InputStream input)
			throws FileCacheIOException, FileCacheLimitExceededException {
		return createBAFC(input);
	}
	
	public ByteArrayFileCache createBAFC(InputStream input, boolean trustedJson)
			throws FileCacheIOException, FileCacheLimitExceededException {
		byte[] buf = new byte[100000];
		ByteArrayOutputStream bufOs = new ByteArrayOutputStream();
		int maxInMemorySize = maxSizeInMem - sizeInMem;
		long size = 0;
		while (size < maxInMemorySize) {
			int count;
			try {
				count = input.read(buf, 0, Math.min(
						buf.length, maxInMemorySize - (int)size));
			} catch (IOException ioe) {
				throw new FileCacheIOException(ioe.getLocalizedMessage(), ioe);
			}
			if (count < 0)
				break;
			bufOs.write(buf, 0, count);
			size += count;
		}
		try {
			bufOs.close();
		} catch (IOException ioe) {
			throw new FileCacheIOException(ioe.getLocalizedMessage(), ioe);
		}
		if (size >= maxInMemorySize) {
			File tempFile = null;
			OutputStream os = null;
			try {
				tempFile = tfm.generateTempFile("resp", "json");
				os = new BufferedOutputStream(
						new FileOutputStream(tempFile));
				try {
					os.write(bufOs.toByteArray());
					bufOs = null;
					while (true) {
						if (sizeOnDisk + size > maxSizeOnDisk) {
							cleanUp(tempFile, os);
							throw new FileCacheLimitExceededException(
									"Disk limit exceeded for file cache: " +
											maxSizeOnDisk);
						}
						int count = input.read(buf, 0, buf.length);
						if (count < 0)
							break;
						os.write(buf, 0, count);
						size += count;
					}
				} finally {
					try { os.close(); } catch (Exception ignore) {}
				}
				sizeOnDisk += size;
				return new ByteArrayFileCache(null, tempFile,
						new JsonTokenStream(tempFile)
							.setTrustedWholeJson(trustedJson));
			} catch (IOException ioe) {
				cleanUp(tempFile, os);
				throw new FileCacheIOException(ioe.getLocalizedMessage(), ioe);
			} catch (RuntimeException re) {
				cleanUp(tempFile, os);
				throw re;
			}
		} else {
			sizeInMem += (int)size;
			try {
				return new ByteArrayFileCache(null, null,
						new JsonTokenStream(bufOs.toByteArray())
							.setTrustedWholeJson(trustedJson));
			} catch (IOException ioe) {
				throw new FileCacheIOException(
						ioe.getLocalizedMessage(), ioe);
			}
		}
	}

	private void cleanUp(File tempFile, OutputStream os) {
		if (os != null)
			try {
				os.close();
			} catch (Exception ignore) {}
		if (tempFile != null)
			tempFile.delete();
	}

	public ByteArrayFileCache getSubdataExtraction(
			final ByteArrayFileCache parent, final ObjectPaths paths)
			throws TypedObjectExtractionException,
			FileCacheLimitExceededException, FileCacheIOException {
		final OutputStream[] origin = {new ByteArrayOutputStream()};
		final File[] tempFile = {null};
		final long[] size = {0L};
		OutputStream os = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				throw new RuntimeException("Single byte writing is not supported");
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
					if (sizeOnDisk + size[0] > maxSizeOnDisk) {
						final String err = "Disk limit exceeded for file cache: " +
								maxSizeOnDisk;
						throw new IOException(err,
								new FileCacheLimitExceededException(err));
					}
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
				return new ByteArrayFileCache(parent, tempFile[0], new JsonTokenStream(tempFile[0])
						.setTrustedWholeJson(parent.containsTrustedJson()));
			} else {
				sizeInMem += (int)size[0];
				byte[] arr = ((ByteArrayOutputStream)origin[0]).toByteArray();
				return new ByteArrayFileCache(parent, null, new JsonTokenStream(arr)
						.setTrustedWholeJson(parent.containsTrustedJson()));
			}
		} catch (Throwable e) {
			try {
				os.close();
			} catch (Exception ignore) {}
			if (tempFile[0] != null) {
				tempFile[0].delete();
			}
			if (e instanceof TypedObjectExtractionException) {
				throw (TypedObjectExtractionException)e;
			}
			if (e instanceof RuntimeException) {
				throw (RuntimeException)e;
			}
			if (e instanceof IOException) {
				final IOException ioe = (IOException) e;
				if (ioe.getCause() instanceof FileCacheLimitExceededException) {
					throw (FileCacheLimitExceededException) ioe.getCause();
				}
				throw new FileCacheIOException(ioe.getLocalizedMessage(), ioe);
			}
			throw new RuntimeException(e.getMessage(), e);
		}
	}
	
	public static ByteArrayFileCacheManager forTests() {
		return new ByteArrayFileCacheManager(16000000, 2000000000L, TempFilesManager.forTests());
	}
	
	public class ByteArrayFileCache {
		private File tempFile = null;
		private JsonTokenStream jts;
		private ByteArrayFileCache parent = null;
		private boolean destroyed = false;
		
		private ByteArrayFileCache(ByteArrayFileCache parent, File tempFile, JsonTokenStream jts) {
			this.parent = parent;
			this.tempFile = tempFile;
			this.jts = jts;
		}
		
		public UObject getUObject() {
			checkIfDestroyed();
			return new UObject(jts);
		}
		
		public JsonNode getAsJsonNode() {
			checkIfDestroyed();
			return UObject.transformObjectToJackson(getUObject());
		}
		
		public Reader getJSON() throws IOException {
			checkIfDestroyed();
			return jts.createDataReader();
		}
		
		/** True if this BAFC was marked as containing known good JSON.
		 * @return true if the this BAFC was marked as contains known good
		 * JSON, false otherwise.
		 */
		public boolean containsTrustedJson() {
			checkIfDestroyed();
			return jts.hasTrustedWholeJson();
		}

		private void checkIfDestroyed() {
			if (destroyed) {
				throw new RuntimeException(
						"This ByteArrayFileCache is destroyed");
			}
		}
		
		private void getSubdataExtractionAsStream(final ObjectPaths paths, 
				final OutputStream os)
				throws TypedObjectExtractionException {
			checkIfDestroyed();
			try {
				JsonGenerator jgen = UObject.getMapper().getFactory().createGenerator(os);
				try {
					SubdataExtractor.extract(paths, jts.setRoot(null), jgen);
				} finally {
					jts.close();
					jgen.close();
				}
			} catch (IOException ex) {
				throw new TypedObjectExtractionException(ex.getMessage(), ex);
			}
		}
		
		public void destroy() {
			if (destroyed) {
				return;
			}
			try {
				jts.close();
			} catch (IOException ioe) {
				//nothing can be done
			}
			if (tempFile != null && tempFile.exists()) {
				tempFile.delete();
			}
			if (parent != null) {
				parent.destroy();
			}
			parent = null;
			jts = null;
			tempFile = null;
			destroyed = true;
		}
	}
}
