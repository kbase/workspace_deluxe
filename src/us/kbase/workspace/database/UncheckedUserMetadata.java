package us.kbase.workspace.database;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** User provided metadata container. This class performs no checks on the
 * metadata and is intended for returning metadata to the user from the
 * database, as it is presumed that metadata stored in the database has
 * been checked already.
 * 
 * Use WorkspaceUserMetadata for incoming metadata that needs checking.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class UncheckedUserMetadata {
	
	private Map<String, String> metadata;
	
	/** Create a new unchecked metadata container
	 * @param meta a map containing metadata. If null, an empty metadata map is
	 * created.
	 */
	public UncheckedUserMetadata(final Map<String, String> meta) {
		final Map<String, String> m = new HashMap<String, String>();
		if (meta != null) {
			m.putAll(meta);
		}
		metadata = m;
	}
	
	/** Create a new unchecked metadata container
	 * @param meta a WorkspaceUserMetadata object. If null, an empty metadata
	 * map is created.
	 */
	public UncheckedUserMetadata(final WorkspaceUserMetadata meta) {
		final Map<String, String> m = new HashMap<String, String>();
		if (meta != null) {
			m.putAll(meta.getMetadata());
		}
		metadata = m;
	}

	/** Get the metadata
	 * @return the metadata is an unmodifiable map.
	 */
	public Map<String, String> getMetadata() {
		return Collections.unmodifiableMap(metadata);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("UncheckedUserMetadata [metadata=");
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
		final UncheckedUserMetadata other = (UncheckedUserMetadata) obj;
		return metadata.equals(other.metadata);
	}
}
