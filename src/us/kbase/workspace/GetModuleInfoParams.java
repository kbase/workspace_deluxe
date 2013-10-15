
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
 * <p>Original spec-file type: GetModuleInfoParams</p>
 * <pre>
 * Parameters for the get_module_info function.
 *         Required parameters:
 *         One of:
 *         modulename mod - the name of the module to retrieve.
 *         type_string type - the module information will be retrieved for the
 *                 module with the associated type.
 *         
 *         Optional parameters:
 *         spec_version ver - the version of the module to retrieve. Defaults to
 *                 the latest version. If a type is provided this argument is ignored.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "mod",
    "type",
    "ver"
})
public class GetModuleInfoParams {

    @JsonProperty("mod")
    private String mod;
    @JsonProperty("type")
    private String type;
    @JsonProperty("ver")
    private Long ver;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("mod")
    public String getMod() {
        return mod;
    }

    @JsonProperty("mod")
    public void setMod(String mod) {
        this.mod = mod;
    }

    public GetModuleInfoParams withMod(String mod) {
        this.mod = mod;
        return this;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    public GetModuleInfoParams withType(String type) {
        this.type = type;
        return this;
    }

    @JsonProperty("ver")
    public Long getVer() {
        return ver;
    }

    @JsonProperty("ver")
    public void setVer(Long ver) {
        this.ver = ver;
    }

    public GetModuleInfoParams withVer(Long ver) {
        this.ver = ver;
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
        return ((((((((("GetModuleInfoParams"+" [mod=")+ mod)+", type=")+ type)+", ver=")+ ver)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
