
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
 * <p>Original spec-file type: CreditMetadataEntry</p>
 * <pre>
 * CreditMetadataEntry
 *                 Container for the credit metadata for a workspace object.
 *                 Workspace objects have credit metadata (CM), or citation information for the
 *                 object. This information allows data imported into KBase to retain the appropriate
 *                 details for users wishing to cite the data source, and for those who contributed to
 *                 its creation to be credited for their work.
 *                 Credit metadata is added after object creation, and requires the user to be a
 *                 workspace admin. Username, timestamp, and credit metadata schema version are stored
 *                 with the credit information.
 *                 WS admins can use the workspace `administer` function to add CM to a workspace
 *                 object.
 *                 All fields will be populated.
 *                 credit_metadata - the credit metadata itself
 *                 credit_metadata_schema_version - version of the credit metadata schema used
 *                         Examples:
 *                                 - 1.1.0
 *                 saved_by - KBase workspace ID of the user who added this entry
 *                 timestamp - unix timestamp for the addition of this set of credit metadata
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "credit_metadata",
    "saved_by",
    "credit_metadata_schema_version",
    "timestamp"
})
public class CreditMetadataEntry {

    /**
     * <p>Original spec-file type: CreditMetadata</p>
     * <pre>
     * CreditMetadata
     *                 Represents the credit metadata associated with a workspace object.
     *                 In the following documentation, 'Resource' is used to refer to the workspace object
     *                 that the CM pertains to.
     *                 The 'resource_type' field should be filled using values from the DataCite
     *                 resourceTypeGeneral field:
     *                 https://support.datacite.org/docs/datacite-metadata-schema-v44-mandatory-properties#10a-resourcetypegeneral
     *                 Currently the KBase workspace only supports credit metadata for objects of type
     *                 'dataset'; anything else will return an error.
     *                 The license may be supplied either as an URL pointing to licensing information for
     *                 the resource, or using a license name. See https://choosealicense.com/appendix/ for
     *                 a list of common open source licenses.
     *                 Required fields are:
     *                 - identifier
     *                 - versioning information: if the resource does not have an explicit version number,
     *                 one or more dates should be supplied: ideally the date of resource publication and
     *                 the last update (if applicable).
     *                 - contributors (one or more required)
     *                 - titles (one or more required)
     *                 The resource_type field is required, but as there is currently only a single valid
     *                 value, 'dataset', it is automatically populated if no value is supplied.
     *                 comments - list of strings of freeform text providing extra information about this
     *                         credit metadata.
     *                         Examples:
     *                                 - Credit metadata generated automatically from DOI:10.13039/100000015
     *                 identifier - persistent unique identifier for the resource (i.e. the source data
     *                         for this workspace object).
     *                         Should be in the format <database name>:<identifier within database>
     *                         Examples:
     *                                 - RefSeq:GCF_004214875.1
     *                                 - GenBank:CP035949.1
     *                                 - img.taxon:648028003
     *                 license (optional) - usage license for the resource. May be a text string or an
     *                         URL. Abbreviations should be spelled out where possible (e.g. 'Creative
     *                         Commons 4.0' instead of 'CC-BY-4.0'). The license is interpreted as an URL
     *                         and checked for well-formedness if it starts with a series of letters, a
     *                         colon, and slashes, e.g. "http://"; "https://"; "ftp://".
     *                         Examples:
     *                                 - Creative Commons 4.0
     *                                 - MIT
     *                                 - https://jgi.doe.gov/user-programs/pmo-overview/policies/
     *                 resource_type - the broad type of the source data for this workspace object.
     *                         Defaults to 'dataset' (the only valid value currently) if no value is
     *                         provided.
     *                         Valid 'resource_type' values:
     *                                 - dataset
     *                 version (optional if dates are provided) - the version of the resource. This must
     *                         be an absolute version, not a relative version like 'latest'.
     *                         Examples:
     *                                 - 5
     *                                 - 1.2.1
     *                                 - 20220405
     *                 contributors - a list of people and/or organizations who contributed to the
     *                         resource.
     *                 dates (optional if version is provided) - a list of relevant lifecycle events for
     *                         the resource.
     *                 funding (optional) - funding sources for the resource.
     *                 related_identifiers (optional) - other resolvable persistent unique IDs related to
     *                         the resource.
     *                 titles - one or more titles for the resource.
     * </pre>
     * 
     */
    @JsonProperty("credit_metadata")
    private CreditMetadata creditMetadata;
    @JsonProperty("saved_by")
    private String savedBy;
    @JsonProperty("credit_metadata_schema_version")
    private String creditMetadataSchemaVersion;
    @JsonProperty("timestamp")
    private Long timestamp;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * <p>Original spec-file type: CreditMetadata</p>
     * <pre>
     * CreditMetadata
     *                 Represents the credit metadata associated with a workspace object.
     *                 In the following documentation, 'Resource' is used to refer to the workspace object
     *                 that the CM pertains to.
     *                 The 'resource_type' field should be filled using values from the DataCite
     *                 resourceTypeGeneral field:
     *                 https://support.datacite.org/docs/datacite-metadata-schema-v44-mandatory-properties#10a-resourcetypegeneral
     *                 Currently the KBase workspace only supports credit metadata for objects of type
     *                 'dataset'; anything else will return an error.
     *                 The license may be supplied either as an URL pointing to licensing information for
     *                 the resource, or using a license name. See https://choosealicense.com/appendix/ for
     *                 a list of common open source licenses.
     *                 Required fields are:
     *                 - identifier
     *                 - versioning information: if the resource does not have an explicit version number,
     *                 one or more dates should be supplied: ideally the date of resource publication and
     *                 the last update (if applicable).
     *                 - contributors (one or more required)
     *                 - titles (one or more required)
     *                 The resource_type field is required, but as there is currently only a single valid
     *                 value, 'dataset', it is automatically populated if no value is supplied.
     *                 comments - list of strings of freeform text providing extra information about this
     *                         credit metadata.
     *                         Examples:
     *                                 - Credit metadata generated automatically from DOI:10.13039/100000015
     *                 identifier - persistent unique identifier for the resource (i.e. the source data
     *                         for this workspace object).
     *                         Should be in the format <database name>:<identifier within database>
     *                         Examples:
     *                                 - RefSeq:GCF_004214875.1
     *                                 - GenBank:CP035949.1
     *                                 - img.taxon:648028003
     *                 license (optional) - usage license for the resource. May be a text string or an
     *                         URL. Abbreviations should be spelled out where possible (e.g. 'Creative
     *                         Commons 4.0' instead of 'CC-BY-4.0'). The license is interpreted as an URL
     *                         and checked for well-formedness if it starts with a series of letters, a
     *                         colon, and slashes, e.g. "http://"; "https://"; "ftp://".
     *                         Examples:
     *                                 - Creative Commons 4.0
     *                                 - MIT
     *                                 - https://jgi.doe.gov/user-programs/pmo-overview/policies/
     *                 resource_type - the broad type of the source data for this workspace object.
     *                         Defaults to 'dataset' (the only valid value currently) if no value is
     *                         provided.
     *                         Valid 'resource_type' values:
     *                                 - dataset
     *                 version (optional if dates are provided) - the version of the resource. This must
     *                         be an absolute version, not a relative version like 'latest'.
     *                         Examples:
     *                                 - 5
     *                                 - 1.2.1
     *                                 - 20220405
     *                 contributors - a list of people and/or organizations who contributed to the
     *                         resource.
     *                 dates (optional if version is provided) - a list of relevant lifecycle events for
     *                         the resource.
     *                 funding (optional) - funding sources for the resource.
     *                 related_identifiers (optional) - other resolvable persistent unique IDs related to
     *                         the resource.
     *                 titles - one or more titles for the resource.
     * </pre>
     * 
     */
    @JsonProperty("credit_metadata")
    public CreditMetadata getCreditMetadata() {
        return creditMetadata;
    }

    /**
     * <p>Original spec-file type: CreditMetadata</p>
     * <pre>
     * CreditMetadata
     *                 Represents the credit metadata associated with a workspace object.
     *                 In the following documentation, 'Resource' is used to refer to the workspace object
     *                 that the CM pertains to.
     *                 The 'resource_type' field should be filled using values from the DataCite
     *                 resourceTypeGeneral field:
     *                 https://support.datacite.org/docs/datacite-metadata-schema-v44-mandatory-properties#10a-resourcetypegeneral
     *                 Currently the KBase workspace only supports credit metadata for objects of type
     *                 'dataset'; anything else will return an error.
     *                 The license may be supplied either as an URL pointing to licensing information for
     *                 the resource, or using a license name. See https://choosealicense.com/appendix/ for
     *                 a list of common open source licenses.
     *                 Required fields are:
     *                 - identifier
     *                 - versioning information: if the resource does not have an explicit version number,
     *                 one or more dates should be supplied: ideally the date of resource publication and
     *                 the last update (if applicable).
     *                 - contributors (one or more required)
     *                 - titles (one or more required)
     *                 The resource_type field is required, but as there is currently only a single valid
     *                 value, 'dataset', it is automatically populated if no value is supplied.
     *                 comments - list of strings of freeform text providing extra information about this
     *                         credit metadata.
     *                         Examples:
     *                                 - Credit metadata generated automatically from DOI:10.13039/100000015
     *                 identifier - persistent unique identifier for the resource (i.e. the source data
     *                         for this workspace object).
     *                         Should be in the format <database name>:<identifier within database>
     *                         Examples:
     *                                 - RefSeq:GCF_004214875.1
     *                                 - GenBank:CP035949.1
     *                                 - img.taxon:648028003
     *                 license (optional) - usage license for the resource. May be a text string or an
     *                         URL. Abbreviations should be spelled out where possible (e.g. 'Creative
     *                         Commons 4.0' instead of 'CC-BY-4.0'). The license is interpreted as an URL
     *                         and checked for well-formedness if it starts with a series of letters, a
     *                         colon, and slashes, e.g. "http://"; "https://"; "ftp://".
     *                         Examples:
     *                                 - Creative Commons 4.0
     *                                 - MIT
     *                                 - https://jgi.doe.gov/user-programs/pmo-overview/policies/
     *                 resource_type - the broad type of the source data for this workspace object.
     *                         Defaults to 'dataset' (the only valid value currently) if no value is
     *                         provided.
     *                         Valid 'resource_type' values:
     *                                 - dataset
     *                 version (optional if dates are provided) - the version of the resource. This must
     *                         be an absolute version, not a relative version like 'latest'.
     *                         Examples:
     *                                 - 5
     *                                 - 1.2.1
     *                                 - 20220405
     *                 contributors - a list of people and/or organizations who contributed to the
     *                         resource.
     *                 dates (optional if version is provided) - a list of relevant lifecycle events for
     *                         the resource.
     *                 funding (optional) - funding sources for the resource.
     *                 related_identifiers (optional) - other resolvable persistent unique IDs related to
     *                         the resource.
     *                 titles - one or more titles for the resource.
     * </pre>
     * 
     */
    @JsonProperty("credit_metadata")
    public void setCreditMetadata(CreditMetadata creditMetadata) {
        this.creditMetadata = creditMetadata;
    }

    public CreditMetadataEntry withCreditMetadata(CreditMetadata creditMetadata) {
        this.creditMetadata = creditMetadata;
        return this;
    }

    @JsonProperty("saved_by")
    public String getSavedBy() {
        return savedBy;
    }

    @JsonProperty("saved_by")
    public void setSavedBy(String savedBy) {
        this.savedBy = savedBy;
    }

    public CreditMetadataEntry withSavedBy(String savedBy) {
        this.savedBy = savedBy;
        return this;
    }

    @JsonProperty("credit_metadata_schema_version")
    public String getCreditMetadataSchemaVersion() {
        return creditMetadataSchemaVersion;
    }

    @JsonProperty("credit_metadata_schema_version")
    public void setCreditMetadataSchemaVersion(String creditMetadataSchemaVersion) {
        this.creditMetadataSchemaVersion = creditMetadataSchemaVersion;
    }

    public CreditMetadataEntry withCreditMetadataSchemaVersion(String creditMetadataSchemaVersion) {
        this.creditMetadataSchemaVersion = creditMetadataSchemaVersion;
        return this;
    }

    @JsonProperty("timestamp")
    public Long getTimestamp() {
        return timestamp;
    }

    @JsonProperty("timestamp")
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public CreditMetadataEntry withTimestamp(Long timestamp) {
        this.timestamp = timestamp;
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
        return ((((((((((("CreditMetadataEntry"+" [creditMetadata=")+ creditMetadata)+", savedBy=")+ savedBy)+", creditMetadataSchemaVersion=")+ creditMetadataSchemaVersion)+", timestamp=")+ timestamp)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
