
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
 * <p>Original spec-file type: RegisterTypespecCopyParams</p>
 * <pre>
 * Parameters for the register_typespec_copy function.
 *         Required arguments:
 *         string external_workspace_url - the URL of the  workspace server from
 *                 which to copy a typespec.
 *         modulename mod - the name of the module in the workspace server
 *         
 *         Optional arguments:
 *         spec_version version - the version of the module in the workspace
 *                 server
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "external_workspace_url",
    "mod",
    "version"
})
public class RegisterTypespecCopyParams {

    @JsonProperty("external_workspace_url")
    private String externalWorkspaceUrl;
    @JsonProperty("mod")
    private String mod;
    @JsonProperty("version")
    private Long version;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("external_workspace_url")
    public String getExternalWorkspaceUrl() {
        return externalWorkspaceUrl;
    }

    @JsonProperty("external_workspace_url")
    public void setExternalWorkspaceUrl(String externalWorkspaceUrl) {
        this.externalWorkspaceUrl = externalWorkspaceUrl;
    }

    public RegisterTypespecCopyParams withExternalWorkspaceUrl(String externalWorkspaceUrl) {
        this.externalWorkspaceUrl = externalWorkspaceUrl;
        return this;
    }

    @JsonProperty("mod")
    public String getMod() {
        return mod;
    }

    @JsonProperty("mod")
    public void setMod(String mod) {
        this.mod = mod;
    }

    public RegisterTypespecCopyParams withMod(String mod) {
        this.mod = mod;
        return this;
    }

    @JsonProperty("version")
    public Long getVersion() {
        return version;
    }

    @JsonProperty("version")
    public void setVersion(Long version) {
        this.version = version;
    }

    public RegisterTypespecCopyParams withVersion(Long version) {
        this.version = version;
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
        return ((((((((("RegisterTypespecCopyParams"+" [externalWorkspaceUrl=")+ externalWorkspaceUrl)+", mod=")+ mod)+", version=")+ version)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
