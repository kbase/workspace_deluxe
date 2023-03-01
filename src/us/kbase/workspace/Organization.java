
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
 * <p>Original spec-file type: Organization</p>
 * <pre>
 * Organization
 *                 Represents an organization.
 *                 Recommended resources for organization identifiers and canonical organization names
 *                 include:
 *                         - Research Organization Registry, http://ror.org
 *                         - International Standard Name Identifier, https://isni.org
 *                         - Crossref Funder Registry, https://www.crossref.org/services/funder-registry/
 *                 For example, the US DOE would be entered as:
 *                         organization_name: United States Department of Energy
 *                         organization_id:   ROR:01bj3aw27
 *                 'organization_name' is required; 'organization_id' is optional.
 *                 organization_name - common name of the organization; use the name recommended by
 *                         ROR if possible.
 *                         Examples:
 *                                 - KBase
 *                                 - Lawrence Berkeley National Laboratory
 *                                 - The Ohio State University
 *                 organization_id (optional) - persistent unique identifier for the organization
 *                         in the format <database name>:<identifier within database>.
 *                         Examples:
 *                                 - ROR:01bj3aw27
 *                                 - ISNI:0000000123423717
 *                                 - CrossrefFunder:100000015
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "organization_name",
    "organization_id"
})
public class Organization {

    @JsonProperty("organization_name")
    private String organizationName;
    @JsonProperty("organization_id")
    private String organizationId;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("organization_name")
    public String getOrganizationName() {
        return organizationName;
    }

    @JsonProperty("organization_name")
    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public Organization withOrganizationName(String organizationName) {
        this.organizationName = organizationName;
        return this;
    }

    @JsonProperty("organization_id")
    public String getOrganizationId() {
        return organizationId;
    }

    @JsonProperty("organization_id")
    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public Organization withOrganizationId(String organizationId) {
        this.organizationId = organizationId;
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
        return ((((((("Organization"+" [organizationName=")+ organizationName)+", organizationId=")+ organizationId)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
