
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
 * <p>Original spec-file type: ListWorkspaceIDsParams</p>
 * <pre>
 * Input parameters for the "list_workspace_ids" function.
 * Optional parameters:
 * permission perm - filter workspaces by minimum permission level. 'None'
 *         and 'readable' are ignored.
 * boolean onlyGlobal - if onlyGlobal is true only include world readable
 *         workspaces. Defaults to false. If true, excludeGlobal is ignored.
 * boolean excludeGlobal - if excludeGlobal is true exclude world
 *         readable workspaces. Defaults to true.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "perm",
    "excludeGlobal",
    "onlyGlobal"
})
public class ListWorkspaceIDsParams {

    @JsonProperty("perm")
    private String perm;
    @JsonProperty("excludeGlobal")
    private Long excludeGlobal;
    @JsonProperty("onlyGlobal")
    private Long onlyGlobal;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("perm")
    public String getPerm() {
        return perm;
    }

    @JsonProperty("perm")
    public void setPerm(String perm) {
        this.perm = perm;
    }

    public ListWorkspaceIDsParams withPerm(String perm) {
        this.perm = perm;
        return this;
    }

    @JsonProperty("excludeGlobal")
    public Long getExcludeGlobal() {
        return excludeGlobal;
    }

    @JsonProperty("excludeGlobal")
    public void setExcludeGlobal(Long excludeGlobal) {
        this.excludeGlobal = excludeGlobal;
    }

    public ListWorkspaceIDsParams withExcludeGlobal(Long excludeGlobal) {
        this.excludeGlobal = excludeGlobal;
        return this;
    }

    @JsonProperty("onlyGlobal")
    public Long getOnlyGlobal() {
        return onlyGlobal;
    }

    @JsonProperty("onlyGlobal")
    public void setOnlyGlobal(Long onlyGlobal) {
        this.onlyGlobal = onlyGlobal;
    }

    public ListWorkspaceIDsParams withOnlyGlobal(Long onlyGlobal) {
        this.onlyGlobal = onlyGlobal;
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
        return ((((((((("ListWorkspaceIDsParams"+" [perm=")+ perm)+", excludeGlobal=")+ excludeGlobal)+", onlyGlobal=")+ onlyGlobal)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
