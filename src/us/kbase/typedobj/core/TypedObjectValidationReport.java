package us.kbase.typedobj.core;

import java.io.BufferedOutputStream;
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
import us.kbase.common.utils.JsonTreeGenerator;
import us.kbase.common.utils.sortjson.KeyDuplicationException;
import us.kbase.common.utils.sortjson.TooManyKeysException;
import us.kbase.common.utils.sortjson.UTF8JsonSorterFactory;
import us.kbase.typedobj.exceptions.ExceededMaxMetadataSizeException;
import us.kbase.typedobj.idref.IdReference;
import us.kbase.typedobj.idref.IdReferenceHandlerSet;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The report generated when a typed object instance is validated.  If the type
 * definition indicates that fields are ID references, those ID references can
 * be extracted from this report.
 *
 * @author msneddon
 * @author rsutormin
 * @author gaprice@lbl.gov
 */
public class TypedObjectValidationReport {

	//TODO JAVADOC
	
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
	 * After validation, assemble the validation result into a report for later use. The report contains
	 * information on validation errors (if any), the IDs found in the object, and information about the
	 * metadata extraction selection.
	 * 
	 */
	protected TypedObjectValidationReport(
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
	 * Get the absolute ID of the typedef that was used to validate the instance
	 * @return
	 */
	public AbsoluteTypeDefId getValidationTypeDefId() {
		return validationTypeDefId;
	}
	
	/**
	 * @return boolean true if the instance is valid, false otherwise
	 */
	public boolean isInstanceValid() {
		return errors.isEmpty();
	}
	
	/**
	 * Iterate over all items in the report and return the error messages.
	 * @return errors
	 */
	public List<String> getErrorMessages() {
		return errors;
	}
	
	/** Get an input stream containing the relabeled, sorted object. sort()
	 * must be called before calling this method.
	 * 
	 * The caller of this method is responsible for closing the stream.
	 * @return an object input stream.
	 */
	public InputStream getInputStream() {
		if (byteCache == null && fileCache == null) {
			throw new IllegalStateException(
					"You must call sort() prior to accessing the object data.");
		}
		if (byteCache != null) {
			return new ByteArrayInputStream(byteCache);
		} else {
			try {
				return new FileInputStream(fileCache);
			} catch (FileNotFoundException e) {
				throw new RuntimeException("A programming error occured and " +
						"the file cache could not be found.", e);
			}
		}
	}
	
	/**
	 * Relabel the WS IDs in the original Json document based on the specified set of
	 * ID Mappings, where keys are the original ids and values are the replacement ids.
	 * 
	 * Caution: this relabeling happens in-place, so if you have modified the structure
	 * of the JSON node between validation and invocation of this method, you will likely
	 * get many runtime errors.  You should make a deep copy first if you indent to do this.
	 * 
	 * Memory of the original ids is not changed by this operation.  Thus, if you need
	 * to rename the ids a second time, you must still refer to the id as its original name,
	 * which will not necessarily be the name in the current version of the object.
	 */
	public JsonNode getInstanceAfterIdRefRelabelingForTests() throws IOException {
		JsonTreeGenerator jgen = new JsonTreeGenerator(UObject.getMapper());
		relabelWsIdReferencesIntoGenerator(jgen);
		JsonNode originalInstance = jgen.getTree();
		return originalInstance;
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
		naturallySorted =
				relabelWsIdReferencesIntoGeneratorAndCheckOrder(jgen);
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
	 * You must call this method prior to calling createJsonWritable().
	 * Equivalent of sort(null). All data is kept in memory.
	 * @param fac the sorter factory to use when generating a sorter.
	 * @throws RelabelIdReferenceException if there are duplicate keys after
	 * relabeling the ids or if sorting the map keys takes too much memory.
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
	 * You must call this method prior to calling createJsonWritable().
	 * @param fac the sorter factory to use when generating a sorter.
	 * @param tfm the temporary file manager to use for managing temporary
	 * files. All data is kept in memory if tfm is null.
	 * @throws IOException if an IO exception occurs.
	 * @throws TooManyKeysException if the memory required to sort the map is
	 * too high.
	 * @throws KeyDuplicationException if there are duplicate keys present
	 * in a map after relabeling.
	 */
	public void sort(final UTF8JsonSorterFactory fac,
			final TempFilesManager tfm)
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
				try (final OutputStream os = new BufferedOutputStream(baos)) {
					relabelWsIdReferencesIntoWriter(new DigestOutputStream(
							os, digest));
				}
				byteCache = baos.toByteArray();
			} else {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try (final OutputStream os = new BufferedOutputStream(baos)) {
					relabelWsIdReferencesIntoWriter(os);
				}
				byteCache = baos.toByteArray();
				baos = new ByteArrayOutputStream();
				try (final OutputStream os = new BufferedOutputStream(baos)) {
					fac.getSorter(byteCache).writeIntoStream(
							new DigestOutputStream(os, digest));
				}
				byteCache = baos.toByteArray();
			}
		} else {
			if (naturallySorted) {
				fileCache = tfm.generateTempFile("natsortout", "json");
				try (final OutputStream os = new BufferedOutputStream(
						new FileOutputStream(fileCache))) {
					relabelWsIdReferencesIntoWriter(new DigestOutputStream(
							os, digest));
				} catch (IOException | RuntimeException | Error e) {
					destroyCachedResources();
					throw e;
				}
			} else {
				final File f1 = tfm.generateTempFile("sortinp", "json");
				try {
					try (final OutputStream os = new BufferedOutputStream(
							new FileOutputStream(f1))) {
						relabelWsIdReferencesIntoWriter(os);
					}
					fileCache = tfm.generateTempFile("sortout", "json");
					try (final OutputStream os = new BufferedOutputStream(
							new FileOutputStream(fileCache))) {
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
	
	private boolean relabelWsIdReferencesIntoGeneratorAndCheckOrder(
			JsonGenerator jgen) throws IOException {
		//TODO PERFORMANCE make the metadata extractor a TSP wrapper and extract here
		TokenSequenceProvider tsp = null;
		try {
			if (idHandler.isEmpty()) {
				JsonTokenStream jts = tokenStreamProvider.getPlacedStream();
				SortCheckingTokenSequenceProvider sortCheck =
						new SortCheckingTokenSequenceProvider(jts);
				tsp = sortCheck;
				new JsonTokenStreamWriter().writeTokens(sortCheck, jgen);
				return sortCheck.isSorted();
			} else {
				JsonTokenStream jts = tokenStreamProvider.getPlacedStream();
				IdRefTokenSequenceProvider idSubst =
						new IdRefTokenSequenceProvider(jts, schema, idHandler);
				tsp = idSubst;
				new JsonTokenStreamWriter().writeTokens(idSubst, jgen);
				idSubst.close();
				return idSubst.isSorted();
			}
		} finally {
			if (tsp != null)
				tsp.close();
		}
	}
	
	public JsonDocumentLocation getIdReferenceLocation (
			final IdReference<?> ref)
					throws IOException {
		JsonTokenStream jts = tokenStreamProvider.getPlacedStream();
		IdRefTokenSequenceProvider idSubst =
				new IdRefTokenSequenceProvider(jts, schema,
						new IdReferenceHandlerSetFactory(0)
							.createHandlers(String.class));
		idSubst.setFindMode(ref);
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
	 * If metadata ws was defined in the Json Schema, then you can use this method
	 * to extract out the contents.  Note that this method does not perform a deep copy of the data,
	 * so if you extract metadata, then modify the original instance that was validated, it can
	 * (in some but not all cases) modify this metadata as well.  So you should always perform a
	 * deep copy of the original instance if you intend to modify it and  metadata has already
	 * been extracted.
	 * @throws ExceededMaxMetadataSizeException 
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
