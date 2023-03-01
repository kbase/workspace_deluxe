
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
 * <p>Original spec-file type: Contributor</p>
 * <pre>
 * Contributor
 *                 Represents a contributor to the resource.
 *                 Contributors must have a 'contributor_type', either 'person' or 'organization', and
 *                 a 'name'.
 *                 The 'credit_name' field is used to store the name of a person as it would appear in
 *                 a citation. If there is no 'credit_name' supplied, the 'name' field would be used
 *                 in citations.
 *                 For example:
 *                         name:                Hubert George Wells
 *                         credit_name:        Wells, HG
 *                         name:                Alexandria Ocasio-Cortez
 *                         credit_name:        Ocasio-Cortez, A
 *                         name:                Helena Bonham Carter
 *                         credit_name:        Bonham Carter, H
 *                 The 'contributor_role' field takes values from the DataCite and CRediT contributor
 *                 roles vocabularies. For more information on these resources and choosing the
 *                 appropriate roles, please see the following links:
 *                 DataCite contributor roles: https://support.datacite.org/docs/datacite-metadata-schema-v44-recommended-and-optional-properties#7a-contributortype
 *                 CRediT contributor role taxonomy: https://credit.niso.org
 *                 contributor_type - must be either 'person' or 'organization'.
 *                         Valid 'contributor_type' values:
 *                                 - person
 *                                 - organization
 *                 contributor_id (optional) - persistent unique identifier for the contributor;
 *                         this might be an ORCID for an individual, or a ROR ID for an organization.
 *                         Examples:
 *                                 - ORCID:0000-0001-9557-7715
 *                                 - ROR:01znn6x10
 *                 name - contributor name. For organizations, this should be the full (unabbreviated)
 *                         name; for a person, the full name should be entered.
 *                         Examples:
 *                                 - Helena Bonham Carter
 *                                 - Hubert Wells
 *                                 - Madonna
 *                                 - Marionetta Cecille de la Carte-Postale
 *                                 - National Institute of Mental Health
 *                                 - Ransome the Clown
 *                 credit_name (optional) - for a person, how the name should appear in a citation.
 *                         Examples:
 *                                 - Carte-Postale, MC
 *                                 - Bonham Carter, H
 *                                 - Wells, HG
 *                 affiliations (optional) - list of organizations with which the
 *                         contributor is affiliated. For contributors that represent an organization,
 *                         this may be a parent organization (e.g. KBase, US DOE; Arkin lab, LBNL).
 *                 contributor_roles (optional) - list of roles played by the contributor when working
 *                         on the resource.
 *                         Valid 'contributor_role' values:
 *                                 - DataCite:ContactPerson
 *                                 - DataCite:DataCollector
 *                                 - DataCite:DataCurator
 *                                 - DataCite:DataManager
 *                                 - DataCite:Distributor
 *                                 - DataCite:Editor
 *                                 - DataCite:HostingInstitution
 *                                 - DataCite:Producer
 *                                 - DataCite:ProjectLeader
 *                                 - DataCite:ProjectManager
 *                                 - DataCite:ProjectMember
 *                                 - DataCite:RegistrationAgency
 *                                 - DataCite:RegistrationAuthority
 *                                 - DataCite:RelatedPerson
 *                                 - DataCite:Researcher
 *                                 - DataCite:ResearchGroup
 *                                 - DataCite:RightsHolder
 *                                 - DataCite:Sponsor
 *                                 - DataCite:Supervisor
 *                                 - DataCite:WorkPackageLeader
 *                                 - DataCite:Other
 *                                 - CRediT:conceptualization
 *                                 - CRediT:data-curation
 *                                 - CRediT:formal-analysis
 *                                 - CRediT:funding-acquisition
 *                                 - CRediT:investigation
 *                                 - CRediT:methodology
 *                                 - CRediT:project-administration
 *                                 - CRediT:resources
 *                                 - CRediT:software
 *                                 - CRediT:supervision
 *                                 - CRediT:validation
 *                                 - CRediT:visualization
 *                                 - CRediT:writing-original-draft
 *                                 - CRediT:writing-review-editing
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "contributor_type",
    "name",
    "credit_name",
    "contributor_id",
    "affiliations",
    "contributor_roles"
})
public class Contributor {

    @JsonProperty("contributor_type")
    private java.lang.String contributorType;
    @JsonProperty("name")
    private java.lang.String name;
    @JsonProperty("credit_name")
    private java.lang.String creditName;
    @JsonProperty("contributor_id")
    private java.lang.String contributorId;
    @JsonProperty("affiliations")
    private List<Organization> affiliations;
    @JsonProperty("contributor_roles")
    private List<String> contributorRoles;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("contributor_type")
    public java.lang.String getContributorType() {
        return contributorType;
    }

    @JsonProperty("contributor_type")
    public void setContributorType(java.lang.String contributorType) {
        this.contributorType = contributorType;
    }

    public Contributor withContributorType(java.lang.String contributorType) {
        this.contributorType = contributorType;
        return this;
    }

    @JsonProperty("name")
    public java.lang.String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(java.lang.String name) {
        this.name = name;
    }

    public Contributor withName(java.lang.String name) {
        this.name = name;
        return this;
    }

    @JsonProperty("credit_name")
    public java.lang.String getCreditName() {
        return creditName;
    }

    @JsonProperty("credit_name")
    public void setCreditName(java.lang.String creditName) {
        this.creditName = creditName;
    }

    public Contributor withCreditName(java.lang.String creditName) {
        this.creditName = creditName;
        return this;
    }

    @JsonProperty("contributor_id")
    public java.lang.String getContributorId() {
        return contributorId;
    }

    @JsonProperty("contributor_id")
    public void setContributorId(java.lang.String contributorId) {
        this.contributorId = contributorId;
    }

    public Contributor withContributorId(java.lang.String contributorId) {
        this.contributorId = contributorId;
        return this;
    }

    @JsonProperty("affiliations")
    public List<Organization> getAffiliations() {
        return affiliations;
    }

    @JsonProperty("affiliations")
    public void setAffiliations(List<Organization> affiliations) {
        this.affiliations = affiliations;
    }

    public Contributor withAffiliations(List<Organization> affiliations) {
        this.affiliations = affiliations;
        return this;
    }

    @JsonProperty("contributor_roles")
    public List<String> getContributorRoles() {
        return contributorRoles;
    }

    @JsonProperty("contributor_roles")
    public void setContributorRoles(List<String> contributorRoles) {
        this.contributorRoles = contributorRoles;
    }

    public Contributor withContributorRoles(List<String> contributorRoles) {
        this.contributorRoles = contributorRoles;
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
        return ((((((((((((((("Contributor"+" [contributorType=")+ contributorType)+", name=")+ name)+", creditName=")+ creditName)+", contributorId=")+ contributorId)+", affiliations=")+ affiliations)+", contributorRoles=")+ contributorRoles)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
