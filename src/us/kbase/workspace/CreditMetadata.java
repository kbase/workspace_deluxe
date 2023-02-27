
package us.kbase.workspace;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Generated;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


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
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "identifier",
    "license",
    "resource_type",
    "version",
    "comments",
    "contributors",
    "dates",
    "funding",
    "related_identifiers",
    "titles"
})
public class CreditMetadata {

    @JsonProperty("identifier")
    private java.lang.String identifier;
    @JsonProperty("license")
    private java.lang.String license;
    @JsonProperty("resource_type")
    private java.lang.String resourceType;
    @JsonProperty("version")
    private java.lang.String version;
    @JsonProperty("comments")
    private List<String> comments;
    @JsonProperty("contributors")
    private List<Contributor> contributors;
    @JsonProperty("dates")
    private List<EventDate> dates;
    @JsonProperty("funding")
    private List<FundingReference> funding;
    @JsonProperty("related_identifiers")
    private List<PermanentID> relatedIdentifiers;
    @JsonProperty("titles")
    private List<Title> titles;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("identifier")
    public java.lang.String getIdentifier() {
        return identifier;
    }

    @JsonProperty("identifier")
    public void setIdentifier(java.lang.String identifier) {
        this.identifier = identifier;
    }

    public CreditMetadata withIdentifier(java.lang.String identifier) {
        this.identifier = identifier;
        return this;
    }

    @JsonProperty("license")
    public java.lang.String getLicense() {
        return license;
    }

    @JsonProperty("license")
    public void setLicense(java.lang.String license) {
        this.license = license;
    }

    public CreditMetadata withLicense(java.lang.String license) {
        this.license = license;
        return this;
    }

    @JsonProperty("resource_type")
    public java.lang.String getResourceType() {
        return resourceType;
    }

    @JsonProperty("resource_type")
    public void setResourceType(java.lang.String resourceType) {
        this.resourceType = resourceType;
    }

    public CreditMetadata withResourceType(java.lang.String resourceType) {
        this.resourceType = resourceType;
        return this;
    }

    @JsonProperty("version")
    public java.lang.String getVersion() {
        return version;
    }

    @JsonProperty("version")
    public void setVersion(java.lang.String version) {
        this.version = version;
    }

    public CreditMetadata withVersion(java.lang.String version) {
        this.version = version;
        return this;
    }

    @JsonProperty("comments")
    public List<String> getComments() {
        return comments;
    }

    @JsonProperty("comments")
    public void setComments(List<String> comments) {
        this.comments = comments;
    }

    public CreditMetadata withComments(List<String> comments) {
        this.comments = comments;
        return this;
    }

    @JsonProperty("contributors")
    public List<Contributor> getContributors() {
        return contributors;
    }

    @JsonProperty("contributors")
    public void setContributors(List<Contributor> contributors) {
        this.contributors = contributors;
    }

    public CreditMetadata withContributors(List<Contributor> contributors) {
        this.contributors = contributors;
        return this;
    }

    @JsonProperty("dates")
    public List<EventDate> getDates() {
        return dates;
    }

    @JsonProperty("dates")
    public void setDates(List<EventDate> dates) {
        this.dates = dates;
    }

    public CreditMetadata withDates(List<EventDate> dates) {
        this.dates = dates;
        return this;
    }

    @JsonProperty("funding")
    public List<FundingReference> getFunding() {
        return funding;
    }

    @JsonProperty("funding")
    public void setFunding(List<FundingReference> funding) {
        this.funding = funding;
    }

    public CreditMetadata withFunding(List<FundingReference> funding) {
        this.funding = funding;
        return this;
    }

    @JsonProperty("related_identifiers")
    public List<PermanentID> getRelatedIdentifiers() {
        return relatedIdentifiers;
    }

    @JsonProperty("related_identifiers")
    public void setRelatedIdentifiers(List<PermanentID> relatedIdentifiers) {
        this.relatedIdentifiers = relatedIdentifiers;
    }

    public CreditMetadata withRelatedIdentifiers(List<PermanentID> relatedIdentifiers) {
        this.relatedIdentifiers = relatedIdentifiers;
        return this;
    }

    @JsonProperty("titles")
    public List<Title> getTitles() {
        return titles;
    }

    @JsonProperty("titles")
    public void setTitles(List<Title> titles) {
        this.titles = titles;
    }

    public CreditMetadata withTitles(List<Title> titles) {
        this.titles = titles;
        return this;
    }

    @JsonAnyGetter
    public Map<java.lang.String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperties(java.lang.String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    @Override
    public java.lang.String toString() {
        return ((((((((((((((((((((((("CreditMetadata"+" [identifier=")+ identifier)+", license=")+ license)+", resourceType=")+ resourceType)+", version=")+ version)+", comments=")+ comments)+", contributors=")+ contributors)+", dates=")+ dates)+", funding=")+ funding)+", relatedIdentifiers=")+ relatedIdentifiers)+", titles=")+ titles)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
