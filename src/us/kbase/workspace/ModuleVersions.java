
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
 * <p>Original spec-file type: ModuleVersions</p>
 * <pre>
 * A set of versions from a module.
 *         modulename mod - the name of the module.
 *         list<spec_version> - a set or subset of versions associated with the
 *                 module.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "mod",
    "vers"
})
public class ModuleVersions {

    @JsonProperty("mod")
    private String mod;
    @JsonProperty("vers")
    private List<Long> vers;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("mod")
    public String getMod() {
        return mod;
    }

    @JsonProperty("mod")
    public void setMod(String mod) {
        this.mod = mod;
    }

    public ModuleVersions withMod(String mod) {
        this.mod = mod;
        return this;
    }

    @JsonProperty("vers")
    public List<Long> getVers() {
        return vers;
    }

    @JsonProperty("vers")
    public void setVers(List<Long> vers) {
        this.vers = vers;
    }

    public ModuleVersions withVers(List<Long> vers) {
        this.vers = vers;
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
        return ((((((("ModuleVersions"+" [mod=")+ mod)+", vers=")+ vers)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
