
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
 * <p>Original spec-file type: ListWorkspaceInfoParams</p>
 * <pre>
 * Input parameters for the "list_workspace_info" function.
 * Optional parameters:
 * boolean excludeGlobal - if excludeGlobal is true exclude world
 *         readable workspaces
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "excludeGlobal"
})
public class ListWorkspaceInfoParams {

    @JsonProperty("excludeGlobal")
    private Long excludeGlobal;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("excludeGlobal")
    public Long getExcludeGlobal() {
        return excludeGlobal;
    }

    @JsonProperty("excludeGlobal")
    public void setExcludeGlobal(Long excludeGlobal) {
        this.excludeGlobal = excludeGlobal;
    }

    public ListWorkspaceInfoParams withExcludeGlobal(Long excludeGlobal) {
        this.excludeGlobal = excludeGlobal;
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
        return ((((("ListWorkspaceInfoParams"+" [excludeGlobal=")+ excludeGlobal)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
