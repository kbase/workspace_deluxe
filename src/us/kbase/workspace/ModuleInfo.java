
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
 * <p>Original spec-file type: ModuleInfo</p>
 * <pre>
 * Information about a module.
 *         list<username> owners - the owners of the module.
 *         spec_version ver - the version of the module.
 *         typespec spec - the typespec.
 *         string description - the description of the module from the typespec.
 *         mapping<type_string, jsonschema> types - the types associated with this
 *                 module and their JSON schema.
 *         mapping<modulename, spec_version> included_spec_version - names of 
 *                 included modules associated with their versions.
 *         string chsum - the md5 checksum of the object.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "owners",
    "ver",
    "spec",
    "description",
    "types",
    "included_spec_version",
    "chsum",
    "functions"
})
public class ModuleInfo {

    @JsonProperty("owners")
    private List<String> owners;
    @JsonProperty("ver")
    private java.lang.Long ver;
    @JsonProperty("spec")
    private java.lang.String spec;
    @JsonProperty("description")
    private java.lang.String description;
    @JsonProperty("types")
    private Map<String, String> types;
    @JsonProperty("included_spec_version")
    private Map<String, Long> includedSpecVersion;
    @JsonProperty("chsum")
    private java.lang.String chsum;
    @JsonProperty("functions")
    private List<String> functions;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("owners")
    public List<String> getOwners() {
        return owners;
    }

    @JsonProperty("owners")
    public void setOwners(List<String> owners) {
        this.owners = owners;
    }

    public ModuleInfo withOwners(List<String> owners) {
        this.owners = owners;
        return this;
    }

    @JsonProperty("ver")
    public java.lang.Long getVer() {
        return ver;
    }

    @JsonProperty("ver")
    public void setVer(java.lang.Long ver) {
        this.ver = ver;
    }

    public ModuleInfo withVer(java.lang.Long ver) {
        this.ver = ver;
        return this;
    }

    @JsonProperty("spec")
    public java.lang.String getSpec() {
        return spec;
    }

    @JsonProperty("spec")
    public void setSpec(java.lang.String spec) {
        this.spec = spec;
    }

    public ModuleInfo withSpec(java.lang.String spec) {
        this.spec = spec;
        return this;
    }

    @JsonProperty("description")
    public java.lang.String getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(java.lang.String description) {
        this.description = description;
    }

    public ModuleInfo withDescription(java.lang.String description) {
        this.description = description;
        return this;
    }

    @JsonProperty("types")
    public Map<String, String> getTypes() {
        return types;
    }

    @JsonProperty("types")
    public void setTypes(Map<String, String> types) {
        this.types = types;
    }

    public ModuleInfo withTypes(Map<String, String> types) {
        this.types = types;
        return this;
    }

    @JsonProperty("included_spec_version")
    public Map<String, Long> getIncludedSpecVersion() {
        return includedSpecVersion;
    }

    @JsonProperty("included_spec_version")
    public void setIncludedSpecVersion(Map<String, Long> includedSpecVersion) {
        this.includedSpecVersion = includedSpecVersion;
    }

    public ModuleInfo withIncludedSpecVersion(Map<String, Long> includedSpecVersion) {
        this.includedSpecVersion = includedSpecVersion;
        return this;
    }

    @JsonProperty("chsum")
    public java.lang.String getChsum() {
        return chsum;
    }

    @JsonProperty("chsum")
    public void setChsum(java.lang.String chsum) {
        this.chsum = chsum;
    }

    public ModuleInfo withChsum(java.lang.String chsum) {
        this.chsum = chsum;
        return this;
    }

    @JsonProperty("functions")
    public List<String> getFunctions() {
        return functions;
    }

    @JsonProperty("functions")
    public void setFunctions(List<String> functions) {
        this.functions = functions;
    }

    public ModuleInfo withFunctions(List<String> functions) {
        this.functions = functions;
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
        return ((((((((((((((((((("ModuleInfo"+" [owners=")+ owners)+", ver=")+ ver)+", spec=")+ spec)+", description=")+ description)+", types=")+ types)+", includedSpecVersion=")+ includedSpecVersion)+", chsum=")+ chsum)+", functions=")+ functions)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
