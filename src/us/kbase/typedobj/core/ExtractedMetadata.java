package us.kbase.typedobj.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A thin container to return extracted Metadata (indicated by
 * the \@metadata ws annotation) during a pass over an object
 * stream via the MetadataExtractor.
 * @author msneddon
 * @author gaprice
 */
public class ExtractedMetadata {

	private final Map<String, String> metadata;

	public ExtractedMetadata(final Map<String, String> metadata) {
		if (metadata == null) {
			this.metadata = Collections.unmodifiableMap(
					new HashMap<String, String>());
		} else {
			this.metadata = Collections.unmodifiableMap(
					new HashMap<String, String>(metadata));
		}
	}
	
	public Map<String, String> getMetadata() {
		return metadata;
	}
}
