package us.kbase.workspace.database;

import static java.util.Objects.requireNonNull;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.typedobj.core.SubdataExtractor;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;
import us.kbase.workspace.database.exceptions.FileCacheIOException;

public class ByteArrayFileCacheManager {
	
	//TODO JAVADOC
	
	private final TempFilesManager tfm;

	public ByteArrayFileCacheManager() {
		this.tfm = null;
	}
	
	public ByteArrayFileCacheManager(final TempFilesManager tfm) {
		this.tfm = tfm;
	}
	
	public boolean isStoringOnDisk() {
		return tfm != null;
	}
	
	public ByteArrayFileCache createBAFC(
			final InputStream input,
			final boolean trustedJson,
			final boolean sorted)
			throws FileCacheIOException {
		requireNonNull(input, "input");
		File tempFile = null;
		try {
			if (tfm == null) {
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				final int size = IOUtils.copy(input, baos);
				@SuppressWarnings("resource")
				final JsonTokenStream jts = new JsonTokenStream(baos.toByteArray());
				return new ByteArrayFileCache(
						null, null, jts.setTrustedWholeJson(trustedJson), sorted, size);
			} else {
				tempFile = tfm.generateTempFile("resp", "json");
				final int size;
				try (final OutputStream os = new BufferedOutputStream(
						new FileOutputStream(tempFile))) {
					size = IOUtils.copy(input, os);
				}
				@SuppressWarnings("resource")
				final JsonTokenStream jts = new JsonTokenStream(tempFile);
				return new ByteArrayFileCache(
						null, tempFile, jts.setTrustedWholeJson(trustedJson), sorted, size);
			}
		} catch (IOException e) {
			cleanUp(tempFile);
			throw new FileCacheIOException(e.getLocalizedMessage(), e);
		} catch (RuntimeException e) {
			cleanUp(tempFile);
			throw e;
		}
	}
	
	private void cleanUp(final File tempFile) {
		if (tempFile != null) {
			tempFile.delete();
		}
	}

	// in docs note that this method or parent.getUObject() must complete before calling either
	// one again or exceptions will be thrown. Also note not thread safe.
	// fixing this bug would be a lot of work and not necessary at the moment
	public ByteArrayFileCache getSubdataExtraction(
			final ByteArrayFileCache parent,
			final SubsetSelection paths)
			throws TypedObjectExtractionException, FileCacheIOException {
		requireNonNull(parent, "parent").checkIfDestroyed();
		if (requireNonNull(paths, "paths").isEmpty()) {
			throw new IllegalArgumentException("paths cannot be empty");
		}
		File tempFile = null;
		try {
			if (tfm == null) {
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				parent.getSubdataExtractionAsStream(paths, baos);
				@SuppressWarnings("resource")
				final JsonTokenStream jts = new JsonTokenStream(baos.toByteArray());
				return new ByteArrayFileCache(
						parent,
						null,
						jts.setTrustedWholeJson(parent.containsTrustedJson()),
						parent.isSorted(),
						baos.size());
			} else {
				tempFile = tfm.generateTempFile("resp", "json");
				try (final OutputStream os = new BufferedOutputStream(
						new FileOutputStream(tempFile))) {
					parent.getSubdataExtractionAsStream(paths, os);
				}
				@SuppressWarnings("resource")
				final JsonTokenStream jts = new JsonTokenStream(tempFile);
				return new ByteArrayFileCache(
						parent,
						tempFile,
						jts.setTrustedWholeJson(parent.containsTrustedJson()),
						parent.isSorted(),
						Files.size(tempFile.toPath())); 
			}
		} catch (IOException e) {
			// given the method inputs there doesn't seem to be a way to test this
			cleanUp(tempFile);
			throw new FileCacheIOException(e.getLocalizedMessage(), e);
		} catch (TypedObjectExtractionException e) {
			cleanUp(tempFile);
			throw e;
		} catch (RuntimeException e) {
			cleanUp(tempFile);
			throw e;
		}
	}
	
	public class ByteArrayFileCache {
		private File tempFile = null;
		private JsonTokenStream jts;
		private ByteArrayFileCache parent = null;
		private boolean destroyed = false;
		private final boolean sorted;
		private final long size;
		
		// sorted is ignored if a parent is present
		private ByteArrayFileCache(
				final ByteArrayFileCache parent,
				final File tempFile,
				final JsonTokenStream jts,
				final boolean sorted,
				final long size) {
			this.parent = parent;
			this.tempFile = tempFile;
			this.jts = jts;
			this.sorted = sorted;
			this.size = size;
		}
		
		public boolean isSorted() {
			return sorted;
		}
		
		public long getSize() {
			return size;
		}
		
		// in docs note that this method or parent.getUObject() must complete before calling either
		// one again or exceptions will be thrown. Also note not thread safe.
		// fixing this bug would be a lot of work and not necessary at the moment
		public UObject getUObject() throws JsonParseException, IOException {
			checkIfDestroyed();
			jts.setRoot(null);
			return new UObject(jts);
		}
		
		public Reader getJSON() throws IOException {
			checkIfDestroyed();
			return jts.createDataReader();
		}
		
		/** True if this BAFC was marked as containing known good JSON.
		 * @return true if the this BAFC was marked as containing known good
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
		
		// in docs note that this method or parent.getUObject() must complete before calling either
		// one again or exceptions will be thrown. Also note not thread safe.
		// fixing this bug would be a lot of work and not necessary at the moment
		private void getSubdataExtractionAsStream(
				final SubsetSelection paths, 
				final OutputStream os)
				throws TypedObjectExtractionException {
			checkIfDestroyed(); // not necessary now but we might want to make this method public
			try {
				JsonGenerator jgen = UObject.getMapper().getFactory().createGenerator(os);
				try {
					SubdataExtractor.extract(paths, jts.setRoot(null), jgen);
				} finally {
					jts.close();
					jgen.close();
				}
				// jts.setRoot throws IllegalStateException in a bunch of places, ugh
			} catch (IOException | IllegalStateException ex) {
				// there doesn't seem to be a good way of testing this
				throw new TypedObjectExtractionException(ex.getMessage(), ex);
			}
		}
		
		public boolean isDestroyed() {
			return destroyed;
		}
		
		/** Destroys any data associated with this cache and calls destroy()
		 * on this cache's parent. Only subdata objects have a parent, but
		 * multiple subdata objects can share the same parent.
		 */
		public void destroy() {
			if (destroyed) {
				return;
			}
			try {
				jts.close();
			} catch (IOException ioe) {
				//nothing can be done
			}
			if (tempFile != null) {
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
