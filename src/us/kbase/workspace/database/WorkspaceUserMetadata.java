package us.kbase.workspace.database;

import static us.kbase.common.utils.SizeUtils.checkJSONSizeInBytes;
import static us.kbase.common.utils.SizeUtils.checkSizeInBytes;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/** Container for user-provided workspace metadata. Ensures that metadata does
 * not exceed size limits on keys, values or the total size.
 * @author gaprice@lbl.gov
 *
 */
public class WorkspaceUserMetadata {
	
	/**
	 * The maximum size for user provided metdata, in bytes, when serialized
	 * to JSON.
	 */
	public static final int MAX_METADATA_SIZE = 16000;
	private static final int MAX_KEY_VALUE_SIZE = 900;
	
	private Map<String, String> metadata;
	
	/** 
	 * Creates an empty metadata container.
	 */
	public WorkspaceUserMetadata() {
		metadata = new HashMap<String, String>();
	}
	
	/** Creates a metdata container.
	 * @param meta the metadata to contain. A null value results in an empty
	 * metadata container.
	 * @throws MetadataException if the metadata is too large or any
	 * individual key or value in the metadata is too large.
	 */
	public WorkspaceUserMetadata(final Map<String, String> meta)
			throws MetadataException {
		final Map<String, String> m = new HashMap<String, String>();
		if (meta != null && !meta.isEmpty()) {
			checkKeyValueSizes(meta);
			checkMetadataSize(meta);
			m.putAll(meta);
		}
		metadata = m;
	}

	private void checkKeyValueSizes(final Map<String, String> meta)
			throws MetadataException {
		for (final Entry<String, String> e: meta.entrySet()) {
			if (e.getKey() == null) {
				throw new MetadataException("Null values are not allowed for metadata keys");
			}
			if (e.getValue() == null) {
				throw new MetadataException("Null value for metadata key " + e.getKey());
			}
			if (checkSizeInBytes(e.getKey()) + checkSizeInBytes(e.getValue()) >
					MAX_KEY_VALUE_SIZE) {
				throw new MetadataKeyValueSizeException(String.format(
						"Total size of metadata key + value exceeds maximum of %sB for key %s",
						MAX_KEY_VALUE_SIZE, e.getKey()));
			}
		}
	}
	
	// TODO CODE IMMUTABILITY see if these methods can return a new class vs. mutating current
	/** Adds metadata to this container, overwriting any existing metadata with
	 * the same key.
	 * @param meta the metadata to add. A null value results in a no-op.
	 * @throws MetadataException if the combined metadata is too large or any
	 * individual key or value in the metadata is too large.
	 */
	public void addMetadata(final Map<String, String> meta)
			throws MetadataException {
		if (meta == null) {return;}
		checkKeyValueSizes(meta);
		Map<String, String> m = new HashMap<String, String>();
		m.putAll(metadata);
		m.putAll(meta);
		checkMetadataSize(m);
		metadata = m;
	}
	
	/** Adds metadata to this container, overwriting any existing metadata with
	 * the same key.
	 * @param meta the metadata to add. A null value results in a no-op.
	 * @throws MetadataException if the combined metadata is too large or any
	 * individual key or value in the metadata is too large.
	 */
	public void addMetadata(final WorkspaceUserMetadata meta)
			throws MetadataException {
		if (meta == null) {return;}
		addMetadata(meta.getMetadata());
	}

	/** Checks that the size of metadata in Map form does not exceed the limit.
	 * Does not check key / value sizes.
	 * @param meta the metadata to check
	 * @throws MetadataException if the metadata is too large.
	 */
	public static void checkMetadataSize(final Map<String, String> meta) 
			throws MetadataException {
		if (checkJSONSizeInBytes(meta) > MAX_METADATA_SIZE) {
			throw new MetadataSizeException(String.format(
					"Metadata exceeds maximum of %sB", MAX_METADATA_SIZE)); 
		}
	}
	
	/** Get the metadata in this container.
	 * @return the metadata.
	 */
	public Map<String, String> getMetadata() {
		return Collections.unmodifiableMap(metadata);
	}

	/** Check if this container is empty.
	 * @return true if there is no data in this container, false otherwise.
	 */
	public boolean isEmpty() {
		return metadata.isEmpty();
	}
	
	/** Get the number of metadata entries in this container.
	 * @return the number of metadata entries in this container.
	 */
	public int size() {
		return metadata.size();
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("WorkspaceUserMetadata [metadata=");
		builder.append(metadata);
		builder.append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		return 31 + metadata.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final WorkspaceUserMetadata other = (WorkspaceUserMetadata) obj;
		return metadata.equals(other.metadata);
	}
	
	public static class MetadataException extends Exception {
		
		private static final long serialVersionUID = 1L;

		public MetadataException(final String msg) {super(msg);}
		
	}
	
	public static class MetadataSizeException extends MetadataException {
		
		private static final long serialVersionUID = 1L;

		public MetadataSizeException(final String msg) {super(msg);}
	}
	
	public static class MetadataKeyValueSizeException extends MetadataException {
		
		private static final long serialVersionUID = 1L;

		public MetadataKeyValueSizeException(final String msg) {super(msg);}
	}
	
}
