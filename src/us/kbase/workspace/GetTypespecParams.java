
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
 * <p>Original spec-file type: GetTypespecParams</p>
 * <pre>
 * Parameters for the get_typespec function.
 *         Required parameters:
 *         modulename mod - the name of the module to retrieve.
 *         
 *         Optional parameters:
 *         spec_version ver - the version of the module to retrieve. Defaults to
 *                 the latest version.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "mod",
    "ver"
})
public class GetTypespecParams {

    @JsonProperty("mod")
    private String mod;
    @JsonProperty("ver")
    private String ver;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("mod")
    public String getMod() {
        return mod;
    }

    @JsonProperty("mod")
    public void setMod(String mod) {
        this.mod = mod;
    }

    public GetTypespecParams withMod(String mod) {
        this.mod = mod;
        return this;
    }

    @JsonProperty("ver")
    public String getVer() {
        return ver;
    }

    @JsonProperty("ver")
    public void setVer(String ver) {
        this.ver = ver;
    }

    public GetTypespecParams withVer(String ver) {
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
        return ((((((("GetTypespecParams"+" [mod=")+ mod)+", ver=")+ ver)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
