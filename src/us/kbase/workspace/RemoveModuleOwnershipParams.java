
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
 * <p>Original spec-file type: RemoveModuleOwnershipParams</p>
 * <pre>
 * Parameters for the remove_module_ownership function.
 * Required arguments:
 * modulename mod - the module to modify.
 * username old_owner - the user to remove from the module's list of
 *         owners.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "mod",
    "old_owner"
})
public class RemoveModuleOwnershipParams {

    @JsonProperty("mod")
    private String mod;
    @JsonProperty("old_owner")
    private String oldOwner;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("mod")
    public String getMod() {
        return mod;
    }

    @JsonProperty("mod")
    public void setMod(String mod) {
        this.mod = mod;
    }

    public RemoveModuleOwnershipParams withMod(String mod) {
        this.mod = mod;
        return this;
    }

    @JsonProperty("old_owner")
    public String getOldOwner() {
        return oldOwner;
    }

    @JsonProperty("old_owner")
    public void setOldOwner(String oldOwner) {
        this.oldOwner = oldOwner;
    }

    public RemoveModuleOwnershipParams withOldOwner(String oldOwner) {
        this.oldOwner = oldOwner;
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
        return ((((((("RemoveModuleOwnershipParams"+" [mod=")+ mod)+", oldOwner=")+ oldOwner)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
