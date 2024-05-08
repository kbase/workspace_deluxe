
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
 * <p>Original spec-file type: GetPermissionsMassParams</p>
 * <pre>
 * Input parameters for the "get_permissions_mass" function.
 * workspaces - the workspaces for which to return the permissions,
 *         maximum 1000.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspaces"
})
public class GetPermissionsMassParams {

    @JsonProperty("workspaces")
    private List<WorkspaceIdentity> workspaces;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("workspaces")
    public List<WorkspaceIdentity> getWorkspaces() {
        return workspaces;
    }

    @JsonProperty("workspaces")
    public void setWorkspaces(List<WorkspaceIdentity> workspaces) {
        this.workspaces = workspaces;
    }

    public GetPermissionsMassParams withWorkspaces(List<WorkspaceIdentity> workspaces) {
        this.workspaces = workspaces;
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
        return ((((("GetPermissionsMassParams"+" [workspaces=")+ workspaces)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
