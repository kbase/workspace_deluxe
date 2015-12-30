
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
 * <p>Original spec-file type: GetNamesByPrefixResults</p>
 * <pre>
 * Results object for the get_names_by_prefix function.
 *         list<list<obj_name>> names - the names matching the provided prefix,
 *                 listed in order of the input workspaces.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "names"
})
public class GetNamesByPrefixResults {

    @JsonProperty("names")
    private List<List<String>> names;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("names")
    public List<List<String>> getNames() {
        return names;
    }

    @JsonProperty("names")
    public void setNames(List<List<String>> names) {
        this.names = names;
    }

    public GetNamesByPrefixResults withNames(List<List<String>> names) {
        this.names = names;
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
        return ((((("GetNamesByPrefixResults"+" [names=")+ names)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
