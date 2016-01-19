package us.kbase.workspace.database;

import static us.kbase.typedobj.util.SizeUtils.checkJSONSizeInBytes;
import static us.kbase.typedobj.util.SizeUtils.checkSizeInBytes;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class WorkspaceUserMetadata {
	
	//TODO BF test, unit tests 
	//TODO BF docs
	
	public static final int MAX_METADATA_SIZE = 16000;
	private static final int MAX_KEY_VALUE_SIZE = 1000;
	
	private Map<String, String> metadata;
	
	public WorkspaceUserMetadata() {
		metadata = new HashMap<String, String>();
	}
	
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
			if (checkSizeInBytes(e.getKey()) > MAX_KEY_VALUE_SIZE) {
				throw new MetadataKeySizeException(String.format(
						"Metadata key exceeds maximum of %sB: %s",
						MAX_KEY_VALUE_SIZE, e.getKey()));
			}
			if (checkSizeInBytes(e.getValue()) > MAX_KEY_VALUE_SIZE) {
				throw new MetadataValueSizeException(String.format(
						"Value for metadata key %s exceeds maximum of %sB: %s",
						e.getKey(), MAX_KEY_VALUE_SIZE, e.getValue()));
			}
		}
	}
	
	public void addMetadata(final Map<String, String> meta)
			throws MetadataException {
		checkKeyValueSizes(meta);
		Map<String, String> m = new HashMap<String, String>();
		m.putAll(metadata);
		m.putAll(meta);
		checkMetadataSize(m);
		metadata = m;
	}
	
	public static void checkMetadataSize(final Map<String, String> meta) 
			throws MetadataException {
		if (checkJSONSizeInBytes(meta) > MAX_METADATA_SIZE) {
			throw new MetadataSizeException(String.format(
					"Metadata exceeds maximum of %sB", MAX_METADATA_SIZE)); 
		}
	}
	
	public void addMetadata(final WorkspaceUserMetadata meta)
			throws MetadataException {
		addMetadata(meta.getMetadata());
	}
	
	public Map<String, String> getMetadata() {
		return Collections.unmodifiableMap(metadata);
	}

	public boolean isEmpty() {
		return metadata.isEmpty();
	}
	
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
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((metadata == null) ? 0 : metadata.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		WorkspaceUserMetadata other = (WorkspaceUserMetadata) obj;
		if (metadata == null) {
			if (other.metadata != null)
				return false;
		} else if (!metadata.equals(other.metadata))
			return false;
		return true;
	}
	
	public static class MetadataException extends Exception {
		
		private static final long serialVersionUID = 1L;

		public MetadataException(final String msg) {super(msg);}
		
	}
	
	public static class MetadataSizeException extends MetadataException {
		
		private static final long serialVersionUID = 1L;

		public MetadataSizeException(final String msg) {super(msg);}
	}
	
	public static class MetadataKeySizeException extends MetadataException {
		
		private static final long serialVersionUID = 1L;

		public MetadataKeySizeException(final String msg) {super(msg);}
	}
	
	public static class MetadataValueSizeException extends MetadataException {
		
		private static final long serialVersionUID = 1L;

		public MetadataValueSizeException(final String msg) {super(msg);}
	}

}
