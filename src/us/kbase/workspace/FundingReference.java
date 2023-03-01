
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
 * <p>Original spec-file type: FundingReference</p>
 * <pre>
 * FundingReference
 *                 Represents a funding source for a resource, including the funding body and the
 *                 grant awarded.
 *                 The 'funder_name' field is required; all others are optional.
 *                 Recommended resources for organization identifiers include:
 *                         - Research Organization Registry, http://ror.org
 *                         - International Standard Name Identifier, https://isni.org
 *                         - Crossref Funder Registry, https://www.crossref.org/services/funder-registry/
 *                 Some organizations may have a digital object identifier (DOI).
 *                 funder_id (optional) - persistent unique identifier for the funder in the format
 *                         <database name>:<identifier within database>.
 *                         Examples:
 *                                 - DOI:10.13039/100000015
 *                                 - ROR:04xm1d337
 *                                 - ISNI:0000000405337147
 *                 funder_name - common name for the funder.
 *                         Examples:
 *                                 - Joint Genome Institute
 *                                 - National Science Foundation
 *                                 - US DOE Office of Science (SC), Biological and Environmental
 *                                 Research (BER)
 *                 award_id (optional) - code for the award, assigned by the funder.
 *                         Examples:
 *                                 - 1296
 *                                 - CBET-0756451
 *                                 - DOI:10.46936/10.25585/60000745
 *                 award_title (optional) - title for the award.
 *                         Examples:
 *                                 - Metagenomic analysis of the rhizosphere of three biofuel crops at
 *                                 the KBS intensive site
 *                 award_url (optional) - URL for the award.
 *                         Examples:
 *                                 - https://genome.jgi.doe.gov/portal/Metanaintenssite/Metanaintenssite.info.html
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "funder_id",
    "funder_name",
    "award_id",
    "award_title",
    "award_url"
})
public class FundingReference {

    @JsonProperty("funder_id")
    private String funderId;
    @JsonProperty("funder_name")
    private String funderName;
    @JsonProperty("award_id")
    private String awardId;
    @JsonProperty("award_title")
    private String awardTitle;
    @JsonProperty("award_url")
    private String awardUrl;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("funder_id")
    public String getFunderId() {
        return funderId;
    }

    @JsonProperty("funder_id")
    public void setFunderId(String funderId) {
        this.funderId = funderId;
    }

    public FundingReference withFunderId(String funderId) {
        this.funderId = funderId;
        return this;
    }

    @JsonProperty("funder_name")
    public String getFunderName() {
        return funderName;
    }

    @JsonProperty("funder_name")
    public void setFunderName(String funderName) {
        this.funderName = funderName;
    }

    public FundingReference withFunderName(String funderName) {
        this.funderName = funderName;
        return this;
    }

    @JsonProperty("award_id")
    public String getAwardId() {
        return awardId;
    }

    @JsonProperty("award_id")
    public void setAwardId(String awardId) {
        this.awardId = awardId;
    }

    public FundingReference withAwardId(String awardId) {
        this.awardId = awardId;
        return this;
    }

    @JsonProperty("award_title")
    public String getAwardTitle() {
        return awardTitle;
    }

    @JsonProperty("award_title")
    public void setAwardTitle(String awardTitle) {
        this.awardTitle = awardTitle;
    }

    public FundingReference withAwardTitle(String awardTitle) {
        this.awardTitle = awardTitle;
        return this;
    }

    @JsonProperty("award_url")
    public String getAwardUrl() {
        return awardUrl;
    }

    @JsonProperty("award_url")
    public void setAwardUrl(String awardUrl) {
        this.awardUrl = awardUrl;
    }

    public FundingReference withAwardUrl(String awardUrl) {
        this.awardUrl = awardUrl;
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
        return ((((((((((((("FundingReference"+" [funderId=")+ funderId)+", funderName=")+ funderName)+", awardId=")+ awardId)+", awardTitle=")+ awardTitle)+", awardUrl=")+ awardUrl)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
