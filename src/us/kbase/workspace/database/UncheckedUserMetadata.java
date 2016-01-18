package us.kbase.workspace.database;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class UncheckedUserMetadata {
	
	//TODO BF test, unit tests 
	//TODO BF docs

	private Map<String, String> metadata;
	
	public UncheckedUserMetadata(final Map<String, String> meta) {
		final Map<String, String> m = new HashMap<String, String>();
		if (meta != null) {
			m.putAll(meta);
		}
		metadata = m;
	}
	
	public UncheckedUserMetadata(final WorkspaceUserMetadata meta) {
		final Map<String, String> m = new HashMap<String, String>();
		if (meta != null) {
			m.putAll(meta.getMetadata());
		}
		metadata = m;
	}

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
		UncheckedUserMetadata other = (UncheckedUserMetadata) obj;
		return metadata.equals(other.metadata);
	}
}
