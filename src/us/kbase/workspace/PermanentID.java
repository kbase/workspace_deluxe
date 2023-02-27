
package us.kbase.workspace;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Generated;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * <p>Original spec-file type: PermanentID</p>
 * <pre>
 * PermanentID
 *                 Represents a persistent unique identifier for an entity, with an optional
 *                 relationship to some other entity.
 *                 The 'id' field is required; all other fields are optional.
 *                 The values in the 'relationship_type' field come from controlled vocabularies
 *                 maintained by DataCite and Crossref. See the documentation links below for more
 *                 details.
 *                 DataCite relation types: https://support.datacite.org/docs/datacite-metadata-schema-v44-recommended-and-optional-properties#12b-relationtype
 *                 Crossref relation types: https://www.crossref.org/documentation/schema-library/markup-guide-metadata-segments/relationships/
 *                 id - persistent unique ID for an entity. Should be in the format
 *                         <database name>:<identifier within database>.
 *                         Examples:
 *                                 - DOI:10.46936/10.25585/60000745
 *                                 - GO:0005456
 *                                 - HGNC:7470
 *                 description (optional) - description of that entity.
 *                         Examples:
 *                                 - Amaranthus hypochondriacus genome
 *                 relationship_type (optional) - relationship between the ID and some other entity.
 *                         For example, when a PermanentID class is used to represent objects in the
 *                         CreditMetadata field 'related_identifiers', the 'relationship_type' field
 *                         captures the relationship between the CreditMetadata and this ID.
 *                         Valid 'relationship_type' values:
 *                                 - DataCite:Cites
 *                                 - DataCite:Compiles
 *                                 - DataCite:Continues
 *                                 - DataCite:Describes
 *                                 - DataCite:Documents
 *                                 - DataCite:HasMetadata
 *                                 - DataCite:HasPart
 *                                 - DataCite:HasVersion
 *                                 - DataCite:IsCitedBy
 *                                 - DataCite:isCompiledBy
 *                                 - DataCite:IsContinuedBy
 *                                 - DataCite:IsDerivedFrom
 *                                 - DataCite:IsDescribedBy
 *                                 - DataCite:IsDocumentedBy
 *                                 - DataCite:IsIdenticalTo
 *                                 - DataCite:IsMetadataFor
 *                                 - DataCite:IsNewVersionOf
 *                                 - DataCite:IsOriginalFormOf
 *                                 - DataCite:IsPartOf
 *                                 - DataCite:IsPreviousVersionOf
 *                                 - DataCite:IsPublishedIn
 *                                 - DataCite:IsReferencedBy
 *                                 - DataCite:IsRequiredBy
 *                                 - DataCite:IsReviewedBy
 *                                 - DataCite:IsSourceOf
 *                                 - DataCite:IsSupplementTo
 *                                 - DataCite:IsSupplementedBy
 *                                 - DataCite:IsVariantFormOf
 *                                 - DataCite:IsVersionOf
 *                                 - DataCite:Obsoletes
 *                                 - DataCite:References
 *                                 - DataCite:Requires
 *                                 - DataCite:Reviews
 *                                 - Crossref:BasedOnData
 *                                 - Crossref:Finances
 *                                 - Crossref:HasComment
 *                                 - Crossref:HasDerivation
 *                                 - Crossref:HasExpression
 *                                 - Crossref:HasFormat
 *                                 - Crossref:HasManifestation
 *                                 - Crossref:HasManuscript
 *                                 - Crossref:HasPreprint
 *                                 - Crossref:HasRelatedMaterial
 *                                 - Crossref:HasReply
 *                                 - Crossref:HasReview
 *                                 - Crossref:HasTranslation
 *                                 - Crossref:IsBasedOn
 *                                 - Crossref:IsBasisFor
 *                                 - Crossref:IsCommentOn
 *                                 - Crossref:IsDataBasisFor
 *                                 - Crossref:IsExpressionOf
 *                                 - Crossref:IsFinancedBy
 *                                 - Crossref:IsFormatOf
 *                                 - Crossref:IsManifestationOf
 *                                 - Crossref:IsManuscriptOf
 *                                 - Crossref:IsPreprintOf
 *                                 - Crossref:IsRelatedMaterial
 *                                 - Crossref:IsReplacedBy
 *                                 - Crossref:IsReplyTo
 *                                 - Crossref:IsReviewOf
 *                                 - Crossref:IsSameAs
 *                                 - Crossref:IsTranslationOf
 *                                 - Crossref:Replaces
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "id",
    "description",
    "relationship_type"
})
public class PermanentID {

    @JsonProperty("id")
    private String id;
    @JsonProperty("description")
    private String description;
    @JsonProperty("relationship_type")
    private String relationshipType;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    public PermanentID withId(String id) {
        this.id = id;
        return this;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    public PermanentID withDescription(String description) {
        this.description = description;
        return this;
    }

    @JsonProperty("relationship_type")
    public String getRelationshipType() {
        return relationshipType;
    }

    @JsonProperty("relationship_type")
    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public PermanentID withRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
        return this;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperties(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    @Override
    public String toString() {
        return ((((((((("PermanentID"+" [id=")+ id)+", description=")+ description)+", relationshipType=")+ relationshipType)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
