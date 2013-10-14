
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
 * <p>Original spec-file type: ModuleInfo</p>
 * <pre>
 * Information about a module.
 *         username owner - the owner of the module.
 *         spec_version ver - the version of the module.
 *         typespec spec - the typespec.
 *         string description - the description of the module from the typespec.
 *         mapping<type_string, jsonschema> types - the types associated with this
 *                 module and their JSON schema.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "owner",
    "ver",
    "spec",
    "description",
    "types"
})
public class ModuleInfo {

    @JsonProperty("owner")
    private java.lang.String owner;
    @JsonProperty("ver")
    private Integer ver;
    @JsonProperty("spec")
    private java.lang.String spec;
    @JsonProperty("description")
    private java.lang.String description;
    @JsonProperty("types")
    private Map<String, String> types;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("owner")
    public java.lang.String getOwner() {
        return owner;
    }

    @JsonProperty("owner")
    public void setOwner(java.lang.String owner) {
        this.owner = owner;
    }

    public ModuleInfo withOwner(java.lang.String owner) {
        this.owner = owner;
        return this;
    }

    @JsonProperty("ver")
    public Integer getVer() {
        return ver;
    }

    @JsonProperty("ver")
    public void setVer(Integer ver) {
        this.ver = ver;
    }

    public ModuleInfo withVer(Integer ver) {
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
        return ((((((((((((("ModuleInfo"+" [owner=")+ owner)+", ver=")+ ver)+", spec=")+ spec)+", description=")+ description)+", types=")+ types)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
