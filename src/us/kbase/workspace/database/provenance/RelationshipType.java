package us.kbase.workspace.database.provenance;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import us.kbase.workspace.database.Util;
import static us.kbase.workspace.database.provenance.Common.DATACITE;
import static us.kbase.workspace.database.provenance.Common.CROSSREF;

/**
 * An enum representing relationship types.
 */
public enum RelationshipType {

	// from DataCite
	CITES(DATACITE, "Cites"),
	COMPILES(DATACITE, "Compiles"),
	CONTINUES(DATACITE, "Continues"),
	DESCRIBES(DATACITE, "Describes"),
	DOCUMENTS(DATACITE, "Documents"),
	HAS_METADATA(DATACITE, "HasMetadata"),
	HAS_PART(DATACITE, "HasPart"),
	HAS_VERSION(DATACITE, "HasVersion"),
	IS_CITED_BY(DATACITE, "IsCitedBy"),
	IS_COMPILED_BY(DATACITE, "isCompiledBy"),
	IS_CONTINUED_BY(DATACITE, "IsContinuedBy"),
	IS_DERIVED_FROM(DATACITE, "IsDerivedFrom"),
	IS_DESCRIBED_BY(DATACITE, "IsDescribedBy"),
	IS_DOCUMENTED_BY(DATACITE, "IsDocumentedBy"),
	IS_IDENTICAL_TO(DATACITE, "IsIdenticalTo"),
	IS_METADATA_FOR(DATACITE, "IsMetadataFor"),
	IS_NEW_VERSION_OF(DATACITE, "IsNewVersionOf"),
	IS_ORIGINAL_FORM_OF(DATACITE, "IsOriginalFormOf"),
	IS_PART_OF(DATACITE, "IsPartOf"),
	IS_PREVIOUS_VERSION_OF(DATACITE, "IsPreviousVersionOf"),
	IS_PUBLISHED_IN(DATACITE, "IsPublishedIn"),
	IS_REFERENCED_BY(DATACITE, "IsReferencedBy"),
	IS_REQUIRED_BY(DATACITE, "IsRequiredBy"),
	IS_REVIEWED_BY(DATACITE, "IsReviewedBy"),
	IS_SOURCE_OF(DATACITE, "IsSourceOf"),
	IS_SUPPLEMENT_TO(DATACITE, "IsSupplementTo"),
	IS_SUPPLEMENTED_BY(DATACITE, "IsSupplementedBy"),
	IS_VARIANT_FORM_OF(DATACITE, "IsVariantFormOf"),
	IS_VERSION_OF(DATACITE, "IsVersionOf"),
	OBSOLETES(DATACITE, "Obsoletes"),
	REFERENCES(DATACITE, "References"),
	REQUIRES(DATACITE, "Requires"),
	REVIEWS(DATACITE, "Reviews"),

	// from Crossref
	BASED_ON_DATA(CROSSREF, "BasedOnData"),
	FINANCES(CROSSREF, "Finances"),
	HAS_COMMENT(CROSSREF, "HasComment"),
	HAS_DERIVATION(CROSSREF, "HasDerivation"),
	HAS_EXPRESSION(CROSSREF, "HasExpression"),
	HAS_FORMAT(CROSSREF, "HasFormat"),
	HAS_MANIFESTATION(CROSSREF, "HasManifestation"),
	HAS_MANUSCRIPT(CROSSREF, "HasManuscript"),
	HAS_PREPRINT(CROSSREF, "HasPreprint"),
	HAS_RELATED_MATERIAL(CROSSREF, "HasRelatedMaterial"),
	HAS_REPLY(CROSSREF, "HasReply"),
	HAS_REVIEW(CROSSREF, "HasReview"),
	HAS_TRANSLATION(CROSSREF, "HasTranslation"),
	IS_BASED_ON(CROSSREF, "IsBasedOn"),
	IS_BASIS_FOR(CROSSREF, "IsBasisFor"),
	IS_COMMENT_ON(CROSSREF, "IsCommentOn"),
	IS_DATA_BASIS_FOR(CROSSREF, "IsDataBasisFor"),
	IS_EXPRESSION_OF(CROSSREF, "IsExpressionOf"),
	IS_FINANCED_BY(CROSSREF, "IsFinancedBy"),
	IS_FORMAT_OF(CROSSREF, "IsFormatOf"),
	IS_MANIFESTATION_OF(CROSSREF, "IsManifestationOf"),
	IS_MANUSCRIPT_OF(CROSSREF, "IsManuscriptOf"),
	IS_PREPRINT_OF(CROSSREF, "IsPreprintOf"),
	IS_RELATED_MATERIAL(CROSSREF, "IsRelatedMaterial"),
	IS_REPLACED_BY(CROSSREF, "IsReplacedBy"),
	IS_REPLY_TO(CROSSREF, "IsReplyTo"),
	IS_REVIEW_OF(CROSSREF, "IsReviewOf"),
	IS_SAME_AS(CROSSREF, "IsSameAs"),
	IS_TRANSLATION_OF(CROSSREF, "IsTranslationOf"),
	REPLACES(CROSSREF, "Replaces");

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
			STRING_TO_REL_TYPE_MAP.put(rt.identifier.toLowerCase(), rt);
			// datacite:is_original_form_of
			STRING_TO_REL_TYPE_MAP.put((rt.source + ":" + rt.name()).toLowerCase(), rt);
			// datacite:isoriginalformof
			STRING_TO_REL_TYPE_MAP.put(rt.getPid().toLowerCase(), rt);
			// add in the crossref equivalent (if applicable)
			if (CROSSREF_DUPLICATES.contains(rt.identifier)) {
				// e.g. crossref:is_variant_form_of
				STRING_TO_REL_TYPE_MAP.put((CROSSREF + ":" + rt.name()).toLowerCase(), rt);
				// crossref:isvariantformof
				STRING_TO_REL_TYPE_MAP.put((CROSSREF + ":" + rt.identifier).toLowerCase(), rt);
			}
		}
	}

	private RelationshipType(final String source, final String identifier) {
		this.source = source;
		this.identifier = identifier;
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
		final String lowercaseInput = Util.checkString(input, "relationshipType").toLowerCase();
		if (!STRING_TO_REL_TYPE_MAP.containsKey(lowercaseInput)) {
			throw new IllegalArgumentException("Invalid relationshipType: " + input);
		}
		return STRING_TO_REL_TYPE_MAP.get(lowercaseInput);
	}
}
