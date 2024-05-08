
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
 * <p>Original spec-file type: list_workspaces_params</p>
 * <pre>
 * Input parameters for the "list_workspaces" function. Provided for
 * backwards compatibility.
 * Optional parameters:
 * string auth - the authentication token of the KBase account accessing
 *         the list of workspaces. Overrides the client provided authorization
 *         credentials if they exist.
 * boolean excludeGlobal - if excludeGlobal is true exclude world
 *         readable workspaces. Defaults to false.
 * @deprecated Workspace.ListWorkspaceInfoParams
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "auth",
    "excludeGlobal"
})
public class ListWorkspacesParams {

    @JsonProperty("auth")
    private String auth;
    @JsonProperty("excludeGlobal")
    private Long excludeGlobal;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("auth")
    public String getAuth() {
        return auth;
    }

    @JsonProperty("auth")
    public void setAuth(String auth) {
        this.auth = auth;
    }

    public ListWorkspacesParams withAuth(String auth) {
        this.auth = auth;
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

    public ListWorkspacesParams withExcludeGlobal(Long excludeGlobal) {
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
        return ((((((("ListWorkspacesParams"+" [auth=")+ auth)+", excludeGlobal=")+ excludeGlobal)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
