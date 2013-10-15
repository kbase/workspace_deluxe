
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
 * <p>Original spec-file type: SetPermissionsParams</p>
 * <pre>
 * Input parameters for the "set_permissions" function.
 *         One, and only one, of the following is required:
 *         ws_id id - the numerical ID of the workspace.
 *         ws_name workspace - name of the workspace or the workspace ID in KBase
 *                 format, e.g. kb|ws.78.
 *         
 *         Required arguments:
 *         permission new_permission - the permission to assign to the users.
 *         list<username> users - the users whose permissions will be altered.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspace",
    "id",
    "new_permission",
    "users"
})
public class SetPermissionsParams {

    @JsonProperty("workspace")
    private java.lang.String workspace;
    @JsonProperty("id")
    private Long id;
    @JsonProperty("new_permission")
    private java.lang.String newPermission;
    @JsonProperty("users")
    private List<String> users;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("workspace")
    public java.lang.String getWorkspace() {
        return workspace;
    }

    @JsonProperty("workspace")
    public void setWorkspace(java.lang.String workspace) {
        this.workspace = workspace;
    }

    public SetPermissionsParams withWorkspace(java.lang.String workspace) {
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

    public SetPermissionsParams withId(Long id) {
        this.id = id;
        return this;
    }

    @JsonProperty("new_permission")
    public java.lang.String getNewPermission() {
        return newPermission;
    }

    @JsonProperty("new_permission")
    public void setNewPermission(java.lang.String newPermission) {
        this.newPermission = newPermission;
    }

    public SetPermissionsParams withNewPermission(java.lang.String newPermission) {
        this.newPermission = newPermission;
        return this;
    }

    @JsonProperty("users")
    public List<String> getUsers() {
        return users;
    }

    @JsonProperty("users")
    public void setUsers(List<String> users) {
        this.users = users;
    }

    public SetPermissionsParams withUsers(List<String> users) {
        this.users = users;
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
        return ((((((((((("SetPermissionsParams"+" [workspace=")+ workspace)+", id=")+ id)+", newPermission=")+ newPermission)+", users=")+ users)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
