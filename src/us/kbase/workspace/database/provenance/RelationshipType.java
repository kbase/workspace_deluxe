package us.kbase.workspace.database.provenance;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import us.kbase.workspace.database.Util;

/**
 * An enum representing relationship types.
 */
public enum RelationshipType {

	// from DataCite
	IS_CITED_BY("DataCite", "IsCitedBy"),
	CITES("DataCite", "Cites"),
	IS_SUPPLEMENT_TO("DataCite", "IsSupplementTo"),
	IS_SUPPLEMENTED_BY("DataCite", "IsSupplementedBy"),
	IS_CONTINUED_BY("DataCite", "IsContinuedBy"),
	CONTINUES("DataCite", "Continues"),
	IS_DESCRIBED_BY("DataCite", "IsDescribedBy"),
	DESCRIBES("DataCite", "Describes"),
	HAS_METADATA("DataCite", "HasMetadata"),
	IS_METADATA_FOR("DataCite", "IsMetadataFor"),
	HAS_VERSION("DataCite", "HasVersion"),
	IS_VERSION_OF("DataCite", "IsVersionOf"),
	IS_NEW_VERSION_OF("DataCite", "IsNewVersionOf"),
	IS_PREVIOUS_VERSION_OF("DataCite", "IsPreviousVersionOf"),
	IS_PART_OF("DataCite", "IsPartOf"),
	HAS_PART("DataCite", "HasPart"),
	IS_PUBLISHED_IN("DataCite", "IsPublishedIn"),
	IS_REFERENCED_BY("DataCite", "IsReferencedBy"),
	REFERENCES("DataCite", "References"),
	IS_DOCUMENTED_BY("DataCite", "IsDocumentedBy"),
	DOCUMENTS("DataCite", "Documents"),
	IS_COMPILED_BY("DataCite", "isCompiledBy"),
	COMPILES("DataCite", "Compiles"),
	IS_VARIANT_FORM_OF("DataCite", "IsVariantFormOf"),
	IS_ORIGINAL_FORM_OF("DataCite", "IsOriginalFormOf"),
	IS_IDENTICAL_TO("DataCite", "IsIdenticalTo"),
	IS_REVIEWED_BY("DataCite", "IsReviewedBy"),
	REVIEWS("DataCite", "Reviews"),
	IS_DERIVED_FROM("DataCite", "IsDerivedFrom"),
	IS_SOURCE_OF("DataCite", "IsSourceOf"),
	IS_REQUIRED_BY("DataCite", "IsRequiredBy"),
	REQUIRES("DataCite", "Requires"),
	OBSOLETES("DataCite", "Obsoletes"),

	// from Crossref
	BASED_ON_DATA("Crossref", "BasedOnData"),
	FINANCES("Crossref", "Finances"),
	HAS_COMMENT("Crossref", "HasComment"),
	HAS_DERIVATION("Crossref", "HasDerivation"),
	HAS_EXPRESSION("Crossref", "HasExpression"),
	HAS_FORMAT("Crossref", "HasFormat"),
	HAS_MANIFESTATION("Crossref", "HasManifestation"),
	HAS_MANUSCRIPT("Crossref", "HasManuscript"),
	HAS_PREPRINT("Crossref", "HasPreprint"),
	HAS_RELATED_MATERIAL("Crossref", "HasRelatedMaterial"),
	HAS_REPLY("Crossref", "HasReply"),
	HAS_REVIEW("Crossref", "HasReview"),
	HAS_TRANSLATION("Crossref", "HasTranslation"),
	IS_BASED_ON("Crossref", "IsBasedOn"),
	IS_BASIS_FOR("Crossref", "IsBasisFor"),
	IS_COMMENT_ON("Crossref", "IsCommentOn"),
	IS_DATA_BASIS_FOR("Crossref", "IsDataBasisFor"),
	IS_EXPRESSION_OF("Crossref", "IsExpressionOf"),
	IS_FINANCED_BY("Crossref", "IsFinancedBy"),
	IS_FORMAT_OF("Crossref", "IsFormatOf"),
	IS_MANIFESTATION_OF("Crossref", "IsManifestationOf"),
	IS_MANUSCRIPT_OF("Crossref", "IsManuscriptOf"),
	IS_PREPRINT_OF("Crossref", "IsPreprintOf"),
	IS_RELATED_MATERIAL("Crossref", "IsRelatedMaterial"),
	IS_REPLACED_BY("Crossref", "IsReplacedBy"),
	IS_REPLY_TO("Crossref", "IsReplyTo"),
	IS_REVIEW_OF("Crossref", "IsReviewOf"),
	IS_SAME_AS("Crossref", "IsSameAs"),
	IS_TRANSLATION_OF("Crossref", "IsTranslationOf"),
	REPLACES("Crossref", "Replaces");


	private final String identifier;
	private final String source;

	// From the DataCite / Crossref "related identifier" crosswalk
	// The following are Crossref relationship type terms that duplicate
	// DataCite terms. If any of the following terms is entered,
	// it should be replaced by the DataCite term.
	private static final List<String> CROSSREF_DUPLICATES = Arrays.asList(
		"Compiles",
		"Continues",
		"Documents",
		"HasPart",
		"HasVersion",
		"IsCompiledBy",
		"IsContinuedBy",
		"IsDerivedFrom",
		"IsDocumentedBy",
		"IsIdenticalTo",
		"IsOriginalFormOf",
		"IsPartOf",
		"IsReferencedBy",
		"IsRequiredBy",
		"IsSupplementedBy",
		"IsSupplementTo",
		"IsVariantFormOf",
		"IsVersionOf",
		"References",
		"Requires"
	);

	// mapping of PIDs and related strings to RelationshipType
	private static final Map<String, RelationshipType> STRING_TO_REL_TYPE_MAP = new HashMap<>();
	static {
		for (final RelationshipType rt : RelationshipType.values()) {
			// is_original_form_of
			STRING_TO_REL_TYPE_MAP.put(rt.name().toLowerCase(), rt);
			// isoriginalformof
			STRING_TO_REL_TYPE_MAP.put(rt.getIdentifier().toLowerCase(), rt);
			// datacite:is_original_form_of
			STRING_TO_REL_TYPE_MAP.put(rt.getSource().toLowerCase() + ":" + rt.name().toLowerCase(), rt);
			// datacite:isoriginalformof
			STRING_TO_REL_TYPE_MAP.put(rt.getPid().toLowerCase(), rt);
			// add in the crossref equivalent (if applicable)
			if (CROSSREF_DUPLICATES.contains(rt.getIdentifier())) {
				// e.g. crossref:is_variant_form_of
				STRING_TO_REL_TYPE_MAP.put("crossref:" + rt.name().toLowerCase(), rt);
				// crossref:isvariantformof
				STRING_TO_REL_TYPE_MAP.put("crossref:" + rt.getIdentifier().toLowerCase(), rt);
			}
		}
	}

	private RelationshipType(final String source, final String identifier) {
		this.source = source;
		this.identifier = identifier;
	}

	/**
	 * Get the source (e.g. DataCite or Crossref) of this relationship type.
	 *
	 * @return the source.
	 */
	private String getSource() {
		return source;
	}

	/**
	 * Get the identifier for this relationship type.
	 *
	 * @return the identifier.
	 */
	private String getIdentifier() {
		return identifier;
	}


	/**
	 * Gets the fully-qualified ID of this relationship type.
	 *
	 * @return the PID.
	 */
	public String getPid() {
		return source + ":" + identifier;
	}

	/**
	 * Gets a relationship type based on a supplied string.
	 *
	 * @param input a string representing a relationship type.
	 * @return a relationship type.
	 * @throws IllegalArgumentException if there is no relationship type PID or
	 *                                  relationship type name related to the input string.
	 */
	public static RelationshipType getRelationshipType(final String input) {
		final String lowercaseInput = Util.checkString(input, "input").toLowerCase();
		if (STRING_TO_REL_TYPE_MAP.containsKey(lowercaseInput)) {
			return STRING_TO_REL_TYPE_MAP.get(lowercaseInput);
		}
		throw new IllegalArgumentException("Invalid relationship type: " + input);
	}
}
