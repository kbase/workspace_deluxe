
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
 * <p>Original spec-file type: GrantModuleOwnershipParams</p>
 * <pre>
 * Parameters for the grant_module_ownership function.
 * Required arguments:
 * modulename mod - the module to modify.
 * username new_owner - the user to add to the module's list of
 *         owners.
 * Optional arguments:
 * boolean with_grant_option - true to allow the user to add owners
 *         to the module.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "mod",
    "new_owner",
    "with_grant_option"
})
public class GrantModuleOwnershipParams {

    @JsonProperty("mod")
    private String mod;
    @JsonProperty("new_owner")
    private String newOwner;
    @JsonProperty("with_grant_option")
    private Long withGrantOption;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("mod")
    public String getMod() {
        return mod;
    }

    @JsonProperty("mod")
    public void setMod(String mod) {
        this.mod = mod;
    }

    public GrantModuleOwnershipParams withMod(String mod) {
        this.mod = mod;
        return this;
    }

    @JsonProperty("new_owner")
    public String getNewOwner() {
        return newOwner;
    }

    @JsonProperty("new_owner")
    public void setNewOwner(String newOwner) {
        this.newOwner = newOwner;
    }

    public GrantModuleOwnershipParams withNewOwner(String newOwner) {
        this.newOwner = newOwner;
        return this;
    }

    @JsonProperty("with_grant_option")
    public Long getWithGrantOption() {
        return withGrantOption;
    }

    @JsonProperty("with_grant_option")
    public void setWithGrantOption(Long withGrantOption) {
        this.withGrantOption = withGrantOption;
    }

    public GrantModuleOwnershipParams withWithGrantOption(Long withGrantOption) {
        this.withGrantOption = withGrantOption;
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
        return ((((((((("GrantModuleOwnershipParams"+" [mod=")+ mod)+", newOwner=")+ newOwner)+", withGrantOption=")+ withGrantOption)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
