
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
 * <p>Original spec-file type: SetGlobalPermissionsParams</p>
 * <pre>
 * Input parameters for the "set_global_permission" function.
 *         One, and only one, of the following is required:
 *         ws_id id - the numerical ID of the workspace.
 *         ws_name workspace - name of the workspace or the workspace ID in KBase
 *                 format, e.g. kb|ws.78.
 *         
 *         Required arguments:
 *         permission new_permission - the permission to assign to all users,
 *                 either 'n' or 'r'. 'r' means that all users will be able to read
 *                 the workspace; otherwise users must have specific permission to
 *                 access the workspace.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspace",
    "id",
    "new_permission"
})
public class SetGlobalPermissionsParams {

    @JsonProperty("workspace")
    private String workspace;
    @JsonProperty("id")
    private Long id;
    @JsonProperty("new_permission")
    private String newPermission;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("workspace")
    public String getWorkspace() {
        return workspace;
    }

    @JsonProperty("workspace")
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public SetGlobalPermissionsParams withWorkspace(String workspace) {
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

    public SetGlobalPermissionsParams withId(Long id) {
        this.id = id;
        return this;
    }

    @JsonProperty("new_permission")
    public String getNewPermission() {
        return newPermission;
    }

    @JsonProperty("new_permission")
    public void setNewPermission(String newPermission) {
        this.newPermission = newPermission;
    }

    public SetGlobalPermissionsParams withNewPermission(String newPermission) {
        this.newPermission = newPermission;
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
        return ((((((((("SetGlobalPermissionsParams"+" [workspace=")+ workspace)+", id=")+ id)+", newPermission=")+ newPermission)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
