
package us.kbase.workspace;

import java.util.ArrayList;
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
 * <p>Original spec-file type: ReleaseTypesParams</p>
 * <pre>
 * Parameters for the release_types function. 
 *         Releases the most recent version of a type or types. Releasing a
 *         type does two things:
 *         1) If the type's major version is 0, it is changed to 1. A major
 *                 version of 0 implies that the type is in development and may have
 *                 backwards compatible changes from minor version to minor version.
 *                 Once a type is released, backwards incompatible changes always
 *                 cause a major version increment.
 *         2) This version of the type becomes the default version, and if a 
 *                 specific version is not supplied in a function call, this version
 *                 will be used. This means that newer, unreleased versions of the
 *                 type may be skipped.
 *         
 *         Required parameters:
 *         One of:
 *         modulename mod - releases all the types for this module
 *         type_id type - releases this type.
 *         
 *         Optional parameters:
 *         list<string> types - if a module is specified, specify here the types
 *                 to release. Default is all types if no or an empty list is passed.
 *                 If type is specified this argument is ignored.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "mod",
    "type",
    "types"
})
public class ReleaseTypesParams {

    @JsonProperty("mod")
    private String mod;
    @JsonProperty("type")
    private String type;
    @JsonProperty("types")
    private List<String> types = new ArrayList<String>();
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("mod")
    public String getMod() {
        return mod;
    }

    @JsonProperty("mod")
    public void setMod(String mod) {
        this.mod = mod;
    }

    public ReleaseTypesParams withMod(String mod) {
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

    public ReleaseTypesParams withType(String type) {
        this.type = type;
        return this;
    }

    @JsonProperty("types")
    public List<String> getTypes() {
        return types;
    }

    @JsonProperty("types")
    public void setTypes(List<String> types) {
        this.types = types;
    }

    public ReleaseTypesParams withTypes(List<String> types) {
        this.types = types;
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
        return ((((((((("ReleaseTypesParams"+" [mod=")+ mod)+", type=")+ type)+", types=")+ types)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
