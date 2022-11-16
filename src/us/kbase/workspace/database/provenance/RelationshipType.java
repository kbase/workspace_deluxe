package us.kbase.workspace.database.provenance;

import java.util.HashMap;
import java.util.Map;
import us.kbase.workspace.database.Util;

/**
 * An enum representing relationship types.
 */
public enum RelationshipType {

	IS_CITED_BY("IsCitedBy"),
	CITES("Cites"),
	IS_SUPPLEMENT_TO("IsSupplementTo"),
	IS_SUPPLEMENTED_BY("IsSupplementedBy"),
	IS_CONTINUED_BY("IsContinuedBy"),
	CONTINUES("Continues"),
	IS_DESCRIBED_BY("IsDescribedBy"),
	DESCRIBES("Describes"),
	HAS_METADATA("HasMetadata"),
	IS_METADATA_FOR("IsMetadataFor"),
	HAS_VERSION("HasVersion"),
	IS_VERSION_OF("IsVersionOf"),
	IS_NEW_VERSION_OF("IsNewVersionOf"),
	IS_PREVIOUS_VERSION_OF("IsPreviousVersionOf"),
	IS_PART_OF("IsPartOf"),
	HAS_PART("HasPart"),
	IS_PUBLISHED_IN("IsPublishedIn"),
	IS_REFERENCED_BY("IsReferencedBy"),
	REFERENCES("References"),
	IS_DOCUMENTED_BY("IsDocumentedBy"),
	DOCUMENTS("Documents"),
	IS_COMPILED_BY("isCompiledBy"),
	COMPILES("Compiles"),
	IS_VARIANT_FORM_OF("IsVariantFormOf"),
	IS_ORIGINAL_FORM_OF("IsOriginalFormOf"),
	IS_IDENTICAL_TO("IsIdenticalTo"),
	IS_REVIEWED_BY("IsReviewedBy"),
	REVIEWS("Reviews"),
	IS_DERIVED_FROM("IsDerivedFrom"),
	IS_SOURCE_OF("IsSourceOf"),
	IS_REQUIRED_BY("IsRequiredBy"),
	REQUIRES("Requires"),
	OBSOLETES("Obsoletes");

	private final static String SOURCE = "DataCite";
	private final String identifier;

	// mapping of PIDs and related strings to RelationshipType
	private static final Map<String, RelationshipType> STRING_TO_REL_TYPE_MAP = new HashMap<>();
	static {
		for (final RelationshipType rt : RelationshipType.values()) {
			STRING_TO_REL_TYPE_MAP.put(rt.name().toLowerCase(), rt);
			STRING_TO_REL_TYPE_MAP.put(rt.name().replace("_", "").toLowerCase(), rt);
			STRING_TO_REL_TYPE_MAP.put(SOURCE.toLowerCase() + ":" + rt.name().toLowerCase(), rt);
			STRING_TO_REL_TYPE_MAP.put(rt.getPid().toLowerCase(), rt);
		}
	}

	private RelationshipType(final String identifier) {
		this.identifier = identifier;
	}

	/**
	 * Gets the fully-qualified ID of this relationship type.
	 *
	 * @return the PID.
	 */
	public String getPid() {
		return SOURCE + ":" + identifier;
	}

	/**
	 * Gets a relationship type based on a supplied string.
	 *
	 * @param input a string representing a relationship type.
	 * @return a relationship type.
	 * @throws IllegalArgumentException if there is no relationship type PID or
	 *                                  lowercased relationship type name related
	 *                                  to the input string.
	 */
	public static RelationshipType getRelationshipType(final String input) {
		final String lowercaseInput = Util.checkString(input, "input").toLowerCase();
		if (STRING_TO_REL_TYPE_MAP.containsKey(lowercaseInput)) {
			return STRING_TO_REL_TYPE_MAP.get(lowercaseInput);
		}
		throw new IllegalArgumentException("Invalid relationship type: " + input);
	}
}
