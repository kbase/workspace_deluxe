
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
 * <p>Original spec-file type: get_workspacemeta_params</p>
 * <pre>
 * Input parameters for the "get_workspacemeta" function. Provided for
 * backwards compatibility.
 *         
 * One, and only one of:
 * ws_name workspace - name of the workspace or the workspace ID in KBase
 *         format, e.g. kb|ws.78.
 * ws_id id - the numerical ID of the workspace.
 *         
 * Optional arguments:
 * string auth - the authentication token of the KBase account accessing
 *         the workspace. Overrides the client provided authorization
 *         credentials if they exist.
 * @deprecated Workspace.WorkspaceIdentity
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspace",
    "id",
    "auth"
})
public class GetWorkspacemetaParams {

    @JsonProperty("workspace")
    private String workspace;
    @JsonProperty("id")
    private Long id;
    @JsonProperty("auth")
    private String auth;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("workspace")
    public String getWorkspace() {
        return workspace;
    }

    @JsonProperty("workspace")
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public GetWorkspacemetaParams withWorkspace(String workspace) {
        this.workspace = workspace;
        return this;
    }

    @JsonProperty("id")
    public Long getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(Long id) {
        this.id = id;
    }

    public GetWorkspacemetaParams withId(Long id) {
        this.id = id;
        return this;
    }

    @JsonProperty("auth")
    public String getAuth() {
        return auth;
    }

    @JsonProperty("auth")
    public void setAuth(String auth) {
        this.auth = auth;
    }

    public GetWorkspacemetaParams withAuth(String auth) {
        this.auth = auth;
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
        return ((((((((("GetWorkspacemetaParams"+" [workspace=")+ workspace)+", id=")+ id)+", auth=")+ auth)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
