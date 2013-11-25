
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
 *         readable workspaces. Defaults to false.
 * boolean showDeleted - show deleted workspaces that are owned by the
 *         user.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "excludeGlobal",
    "showDeleted"
})
public class ListWorkspaceInfoParams {

    @JsonProperty("excludeGlobal")
    private Long excludeGlobal;
    @JsonProperty("showDeleted")
    private Long showDeleted;
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

    @JsonProperty("showDeleted")
    public Long getShowDeleted() {
        return showDeleted;
    }

    @JsonProperty("showDeleted")
    public void setShowDeleted(Long showDeleted) {
        this.showDeleted = showDeleted;
    }

    public ListWorkspaceInfoParams withShowDeleted(Long showDeleted) {
        this.showDeleted = showDeleted;
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
        return ((((((("ListWorkspaceInfoParams"+" [excludeGlobal=")+ excludeGlobal)+", showDeleted=")+ showDeleted)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
