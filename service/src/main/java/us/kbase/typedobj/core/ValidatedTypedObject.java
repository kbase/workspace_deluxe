package us.kbase.typedobj.core;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.UObject;
import us.kbase.common.utils.CountingOutputStream;
import us.kbase.common.utils.sortjson.KeyDuplicationException;
import us.kbase.common.utils.sortjson.TooManyKeysException;
import us.kbase.common.utils.sortjson.UTF8JsonSorterFactory;
import us.kbase.typedobj.exceptions.ExceededMaxMetadataSizeException;
import us.kbase.typedobj.idref.IdReference;
import us.kbase.typedobj.idref.IdReferenceHandlerSet;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A typed object that has been validated  If the type
 * definition indicates that fields are ID references, those ID references can
 * be extracted from this object.
 * 
 * Note that before the VTO can be treated as a {@link Restreamable}, the
 * {@link ValidatedTypedObject#sort(UTF8JsonSorterFactory)} or
 * {@link ValidatedTypedObject#sort(UTF8JsonSorterFactory, TempFilesManager)} method must be
 * called.
 *
 * @author msneddon
 * @author rsutormin
 * @author gaprice@lbl.gov
 */
public class ValidatedTypedObject implements Restreamable {

	/**
	 * The list of errors found during validation.  If the object is not valid, this must be non-empty, (although
	 * note that not all validations errors found may be added, for instance, if there are many errors only the
	 * first 10 may be reported).
	 */
	protected List<String> errors;
	
	/**
	 * The typedef author selection indicating in the JSON Schema what data should be extracted as metadata
	 */
	private JsonNode wsMetadataSelection;
	
	/**
	 * This is the ID of the type definition used in validation - it is an AbsoluteTypeDefId so you always have full version info
	 */
	private final AbsoluteTypeDefId validationTypeDefId;
	
	/**
	 * Used to keep track of the IDs that were parsed from the object
	 */
	private IdReferenceHandlerSet<?> idHandler;

	/**
	 * We keep a reference to the original instance that was validated so we can later easily rename labels or extract
	 * the ws searchable subset or metadata
	 */
	private final UObject tokenStreamProvider;
	
	// the size of the object after relabeling. -1 if not yet calculated.
	private long size = -1;
	// the MD5 of the object after relabeling and sorting.
	private MD5 md5 = null;
	// whether the object is naturally sorted after relabeling.
	// Only set to true after relabeling.
	private boolean naturallySorted = false;
	
	private byte[] byteCache = null;
	
	private File fileCache = null;
	
	private final JsonTokenValidationSchema schema;
	
	/**
	 * Create a validated object. The object contains
	 * information on validation errors (if any), the IDs found in the object,
	 * and information about the metadata extraction selection.
	 */
	protected ValidatedTypedObject(
			final UObject tokenStreamProvider,
			final AbsoluteTypeDefId validationTypeDefId, 
			final List<String> errors,
			final JsonNode wsMetadataSelection,
			final JsonTokenValidationSchema schema,
			final IdReferenceHandlerSet<?> idHandler) {
		if (errors == null) {
			throw new NullPointerException("errors");
		}
		if (validationTypeDefId == null) {
			throw new NullPointerException("validationTypeDefId");
		}
		if (idHandler == null) {
			throw new NullPointerException("idHandler");
		}
		if (tokenStreamProvider == null) {
			throw new NullPointerException("tokenStreamProvider");
		}
		if (schema == null) {
			throw new NullPointerException("schema");
		}
		this.errors = Collections.unmodifiableList(new LinkedList<>(errors));
		//null is ok, metadata handler handles correctly
		this.wsMetadataSelection = wsMetadataSelection;
		this.validationTypeDefId=validationTypeDefId;
		this.idHandler = idHandler;
		this.tokenStreamProvider = tokenStreamProvider;
		this.schema = schema;
	}
	
	/**
	 * Get the absolute ID of the typedef that was used to validate the object.
	 * @return the type ID.
	 */
	public AbsoluteTypeDefId getValidationTypeDefId() {
		return validationTypeDefId;
	}
	
	/** Get whether this object is valid according to the validator.
	 * @return boolean true if the object is valid, false otherwise
	 */
	public boolean isInstanceValid() {
		return errors.isEmpty();
	}
	
	/**
	 * Return any validation errors for this object.
	 * @return errors the validation errors.
	 */
	public List<String> getErrorMessages() {
		return errors;
	}
	
	/** Get an input stream containing the relabeled, sorted object. sort()
	 * must be called before calling this method. The stream is buffered
	 * when appropriate.
	 * 
	 * The caller of this method is responsible for closing the stream.
	 * @return an object input stream.
	 */
	@Override
	public InputStream getInputStream() {
		if (byteCache == null && fileCache == null) {
			throw new IllegalStateException(
					"You must call sort() prior to accessing the object data.");
		}
		if (byteCache != null) {
			return new ByteArrayInputStream(byteCache);
		} else {
			try {
				return new BufferedInputStream(new FileInputStream(fileCache));
			} catch (FileNotFoundException e) {
				throw new RuntimeException("A programming error occured and " +
						"the file cache could not be found.", e);
			}
		}
	}
	
	/** Calculate the size of the object, in bytes, when ids have been
	 * remapped.
	 * @return the size of the object after id remapping.
	 * @throws IOException if an IO error occurs.
	 */
	public long calculateRelabeledSize() throws IOException {
		if (!idHandler.wereIdsProcessed()) {
			throw new IllegalStateException(
					"Must process IDs in handler prior to relabling");
		}
		if (size > -1) {
			return size;
		}
		final CountingOutputStream cos = new CountingOutputStream();
		final JsonGenerator jgen = new JsonFactory().createGenerator(cos);
		naturallySorted = relabelWsIdReferencesIntoGeneratorAndCheckOrder(jgen);
		jgen.close();
		this.size = cos.getSize();
		return this.size;
	}
	
	
	/** Get the size of the object, in bytes, when ids have been remapped.
	 * calculateRelabledSize() must have been called previously, either
	 * directly or indirectly via sort().
	 * @return the size of the object after id remapping.
	 */
	public long getRelabeledSize() {
		if (size < 0) {
			throw new IllegalStateException(
					"Must call calculateRelabeledSize() " +
					"before getting said size");
		}
		return size;
	}
	
	// to implement Restreamable
	@Override
	public long getSize() {
		return getRelabeledSize();
	}

	/** Get the MD5 of the sorted, relabeled object.
	 * sort() must have been called previously.
	 * @return the object's MD5
	 */
	public MD5 getMD5() {
		if (md5 == null) {
			throw new IllegalStateException(
					"Must call sort() before getting the MD5");
		}
		return md5;
	}
	
	private MD5 getMD5fromDigest(final MessageDigest digest) {
		final byte[] d = digest.digest();
		final StringBuilder sb = new StringBuilder();
		for (final byte b : d) {
			sb.append(String.format("%02x", b));
		}
		return new MD5(sb);
	}
	
	private MessageDigest getMD5Digest() {
		try {
			return MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException nsae) {
			throw new RuntimeException(
					"There definitely should be an MD5 digest", nsae);
		}
	}

	/** Relabel ids, sort the object if necessary and keep a copy.
	 * You must call this method prior to calling getInputStream().
	 * Equivalent of sort(null). All data is kept in memory.
	 * @param fac the sorter factory to use when generating a sorter.
	 * @throws IOException if an IO exception occurs.
	 * @throws TooManyKeysException if the memory required to sort the map is
	 * too high.
	 * @throws KeyDuplicationException if there are duplicate keys present
	 * in a map after relabeling.
	 */
	public void sort(final UTF8JsonSorterFactory fac)
			throws IOException, KeyDuplicationException, TooManyKeysException {
		sort(fac, null);
	}
	
	/** Relabel ids, sort the object if necessary and keep a copy.
	 * You must call this method prior to calling getInputStream().
	 * @param fac the sorter factory to use when generating a sorter.
	 * @param tfm the temporary file manager to use for managing temporary
	 * files. All data is kept in memory if tfm is null.
	 * @throws IOException if an IO exception occurs.
	 * @throws TooManyKeysException if the memory required to sort the map is
	 * too high.
	 * @throws KeyDuplicationException if there are duplicate keys present
	 * in a map after relabeling.
	 */
	public void sort(final UTF8JsonSorterFactory fac, final TempFilesManager tfm)
			throws IOException, KeyDuplicationException, TooManyKeysException {
		if (fac == null) {
			throw new NullPointerException("Sorter factory cannot be null");
		}
		if (size < 0) {
			calculateRelabeledSize();
		}
		destroyCachedResources();
		final MessageDigest digest = getMD5Digest();
		if (tfm == null) {
			if (naturallySorted) {
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				relabelWsIdReferencesIntoWriter(new DigestOutputStream(
						baos, digest));
				byteCache = baos.toByteArray();
			} else {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				relabelWsIdReferencesIntoWriter(baos);
				byteCache = baos.toByteArray();
				baos = new ByteArrayOutputStream();
				fac.getSorter(byteCache).writeIntoStream(
						new DigestOutputStream(baos, digest));
				byteCache = baos.toByteArray();
			}
		} else {
			/* note that Jackson, JsonTokenStream (the data source) and the
			 * sorters do their own buffering, so wrapping streams in a buffer
			 * isn't necessary
			 */
			if (naturallySorted) {
				fileCache = tfm.generateTempFile("natsortout", "json");
				try (final OutputStream os = new FileOutputStream(fileCache)) {
					relabelWsIdReferencesIntoWriter(new DigestOutputStream(
							os, digest));
				} catch (IOException | RuntimeException | Error e) {
					destroyCachedResources();
					throw e;
				}
			} else {
				final File f1 = tfm.generateTempFile("sortinp", "json");
				try {
					try (final OutputStream os = new FileOutputStream(f1)) {
						relabelWsIdReferencesIntoWriter(os);
					}
					fileCache = tfm.generateTempFile("sortout", "json");
					try (final OutputStream os =
							new FileOutputStream(fileCache)) {
						fac.getSorter(f1).writeIntoStream(
								new DigestOutputStream(os, digest));
					} catch (IOException | KeyDuplicationException |
							TooManyKeysException | RuntimeException |
							Error e) {
						destroyCachedResources();
						throw e;
					}
				} finally {
					f1.delete();
				}
			}
		}
		md5 = getMD5fromDigest(digest);
	}
	
	/** Destroy any cached resources created by this class and allow garbage
	 * collection of in-memory caches. This method must be called before
	 * program exit or temporary files may be left on disk. The caches will be
	 * recreated as necessary. 
	 */
	public void destroyCachedResources() {
		this.byteCache = null;
		if (this.fileCache != null) {
			this.fileCache.delete();
			this.fileCache = null;
		}
	}
	
	private void relabelWsIdReferencesIntoWriter(final OutputStream os)
			throws IOException {
		relabelWsIdReferencesIntoGenerator(
				new JsonFactory().createGenerator(os));
	}

	private void relabelWsIdReferencesIntoGenerator(final JsonGenerator jgen)
			throws IOException {
		final TokenSequenceProvider tsp = createIdRefTokenSequenceProvider();
		try {
			new JsonTokenStreamWriter().writeTokens(tsp, jgen);
			jgen.flush();
		} finally {
			tsp.close();
		}
	}
	
	private TokenSequenceProvider createIdRefTokenSequenceProvider() throws IOException {
		JsonTokenStream jts = tokenStreamProvider.getPlacedStream();
		if (idHandler.isEmpty())
			return makeTSPfromJTS(jts);
		return new IdRefTokenSequenceProvider(jts, schema, idHandler);
	}
	
	private boolean relabelWsIdReferencesIntoGeneratorAndCheckOrder(final JsonGenerator jgen)
			throws IOException {
		//TODO PERFORMANCE make the metadata extractor a TSP wrapper and extract here
		TokenSequenceProvider tsp = null;
		try {
			final JsonTokenStream jts = tokenStreamProvider.getPlacedStream();
			final IdRefTokenSequenceProvider idSubst =
					new IdRefTokenSequenceProvider(jts, schema, idHandler);
			tsp = idSubst;
			new JsonTokenStreamWriter().writeTokens(idSubst, jgen);
			idSubst.close();
			return idSubst.isSorted();
		} finally {
			if (tsp != null)
				tsp.close();
		}
	}
	
	/** Get the path to an ID in the object.
	 * @param ref the ID to search for.
	 * @return the location of the ID in the object.
	 * @throws IOException if an IO error occurs.
	 */
	public JsonDocumentLocation getIdReferenceLocation (
			final IdReference<?> ref)
					throws IOException {
		JsonTokenStream jts = tokenStreamProvider.getPlacedStream();
		IdRefTokenSequenceProvider idSubst = new IdRefTokenSequenceProvider(jts, schema, null);
		idSubst.setFindMode(ref); // TODO CODE this should be a constructor arg
		try {
			new JsonTokenStreamWriter().writeTokens(
					idSubst, new NullJsonGenerator());
		} finally {
			idSubst.close();
		}
		return idSubst.getReferencePath();
	}
	
	private TokenSequenceProvider createTokenSequenceForMetaDataExtraction()
			throws IOException {
		if (byteCache != null || fileCache != null) {
			final JsonTokenStream afterSort = new JsonTokenStream(
					byteCache != null ? byteCache : fileCache);
			return makeTSPfromJTS(afterSort);
		} else {
			return createIdRefTokenSequenceProvider();
		}
	}

	private TokenSequenceProvider makeTSPfromJTS(final JsonTokenStream jts) {
		return new TokenSequenceProvider() {
			@Override
			public JsonToken nextToken() throws IOException, JsonParseException {
				return jts.nextToken();
			}
			@Override
			public String getText() throws IOException, JsonParseException {
				return jts.getText();
			}
			@Override
			public Number getNumberValue() throws IOException, JsonParseException {
				return jts.getNumberValue();
			}
			@Override
			public void close() throws IOException {
				jts.close();
			}
			@Override
			public boolean isComplete() {
				return false;
			}
		};
	}
	
	
	/**
	 * If metadata ws was defined in the Json Schema, then you can use this
	 * method to extract out the contents.
	 * @param maxMetadataSize the maximum allowable size for the metadata.
	 * @throws ExceededMaxMetadataSizeException if the metadata exceeds the
	 * maximum allowed size.
	 */
	public ExtractedMetadata extractMetadata(
			final long maxMetadataSize) 
			throws ExceededMaxMetadataSizeException {
		
		// return nothing if instance does not validate
		if (!isInstanceValid()) {
			return new ExtractedMetadata(null);
		}
		final MetadataExtractionHandler handler =
				new MetadataExtractionHandler(wsMetadataSelection,
						maxMetadataSize);
		// Identify what we need to extract
		TokenSequenceProvider tsp = null;
		try {
			tsp = createTokenSequenceForMetaDataExtraction();
			final ExtractedMetadata esam = MetadataExtractor
					.extractFields(tsp, handler);
			tsp.close();
			return esam;
		} catch (IOException e) {
			// error that can happen if we cannot write to create the output subset json object! should never happen!
			throw new RuntimeException(
					"Something went very wrong when extracting subset- instance data or memory may have been corrupted.",
					e);
		}  finally {
			if (tsp != null)
				try { tsp.close(); } catch (Exception ignore) {}
		}
		
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("TypedObjectValidationReport [errors=");
		builder.append(errors);
		builder.append(", validationTypeDefId=");
		builder.append(validationTypeDefId);
		builder.append(", size=");
		builder.append(size);
		builder.append(", sorted=");
		builder.append(naturallySorted);
		builder.append("]");
		return builder.toString();
	}
}
