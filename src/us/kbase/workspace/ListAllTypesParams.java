
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
 * <p>Original spec-file type: ListAllTypesParams</p>
 * <pre>
 * Parameters for list_all_types function.
 * Optional arguments:
 * boolean with_empty_modules - include empty module names, optional flag,
 *         default value is false.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "with_empty_modules"
})
public class ListAllTypesParams {

    @JsonProperty("with_empty_modules")
    private Long withEmptyModules;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("with_empty_modules")
    public Long getWithEmptyModules() {
        return withEmptyModules;
    }

    @JsonProperty("with_empty_modules")
    public void setWithEmptyModules(Long withEmptyModules) {
        this.withEmptyModules = withEmptyModules;
    }

    public ListAllTypesParams withWithEmptyModules(Long withEmptyModules) {
        this.withEmptyModules = withEmptyModules;
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
        return ((((("ListAllTypesParams"+" [withEmptyModules=")+ withEmptyModules)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
