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

/** A manager for containers for arbitrary JSON data, stored in memory or on disk.
 * 
 * When the container is no longer of use, be sure to call {@link ByteArrayFileCache#destroy()}
 * to clean up any temporary files.
 * 
 * Implementation / known bug note:
 * Any {@link UObject}s returned from {@link ByteArrayFileCache#getUObject()} share the same
 * {@link JsonTokenStream}. In practice, this means the {@link UObject}s must be processed
 * one at a time, and trying to process more than one at a time (whether in parallel or not)
 * can cause errors, or possibly even data corruption.
 * {@link #getSubdataExtraction(ByteArrayFileCache, SubsetSelection)} also uses the same
 * {@link JsonTokenStream} and so cannot be called while a {@link UObject} from the parent
 * cache is being processed.
 *
 */
public class ByteArrayFileCacheManager {
	
	private final TempFilesManager tfm;

	/** Create a data cache manager that stores all data in memory. */
	public ByteArrayFileCacheManager() {
		this.tfm = null;
	}
	
	/** Create a data cache manager that stores all data on disk if a {@link TempFilesManager}
	 * is provided or in memory otherwise.
	 * @param tfm the temporary file manager, or null to store data in memory.
	 */
	public ByteArrayFileCacheManager(final TempFilesManager tfm) {
		this.tfm = tfm;
	}
	
	/** Check if this manager stores data in memory or on disk.
	 * @return true if the data is stored on disk.
	 */
	public boolean isStoringOnDisk() {
		return tfm != null;
	}
	
	/** Create a data cache.
	 * @param input the data to be stored in the cache.
	 * @param trustedJson true if the cache stores known good JSON. If this is the case the JSON
	 * will not be parsed when serializing a {@link UObject} from
	 * {@link ByteArrayFileCache#getUObject()}, which can save significant time.
	 * @param sorted true if the JSON is sorted.
	 * @return the new data cache.
	 * @throws FileCacheIOException if an IO exception occurs when attempting to read the file.
	 */
	public ByteArrayFileCache createBAFC(
			final InputStream input,
			final boolean trustedJson,
			final boolean sorted)
			throws FileCacheIOException { // TODO NOW CODE why not just use IOException?
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

	/** Get a subset of the data in a file cache.
	 * @param parent the cache containing data to be subsetted.
	 * @param paths the subset to extract from the data.
	 * @return a new cache containing the data subset.
	 * @throws TypedObjectExtractionException if an error occurs while subsetting the data.
	 * @throws FileCacheIOException if an IO exception occurs during the operation.
	 */
	public ByteArrayFileCache getSubdataExtraction(
			final ByteArrayFileCache parent,
			final SubsetSelection paths)
			// TODO NOW CODE again, why not IOError?
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
	
	/** A container for arbitrary JSON data. */
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
		
		/** Check if the JSON data is sorted.
		 * @return true if sorted.
		 */
		public boolean isSorted() {
			return sorted;
		}
		
		/** Get the size of the data in this cache.
		 * @return the size of the data.
		 */
		public long getSize() {
			return size;
		}
		
		/** Get the data in this cache as a {@link UObject}.
		 * @return the object.
		 * @throws JsonParseException if the JSON data in this file could not be parsed.
		 * @throws IOException if an IO error occurs.
		 */
		public UObject getUObject() throws JsonParseException, IOException {
			checkIfDestroyed();
			jts.setRoot(null);
			return new UObject(jts);
		}
		
		/** Get the data in this cache as a reader.
		 * @return the reader.
		 * @throws IOException if an IO error occurs.
		 */
		public Reader getJSON() throws IOException {
			checkIfDestroyed();
			return jts.createDataReader();
		}
		
		/** True if this cache was marked as containing known good JSON.
		 * @return true if the this cache was marked as containing known good
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
		
		/** Check if this cache has been destroyed and is no longer useful.
		 * @return true if the cache is destroyed.
		 */
		public boolean isDestroyed() {
			return destroyed;
		}
		
		/** Destroys any data associated with this cache and calls {@link #destroy()}
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
