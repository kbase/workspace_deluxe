
package us.kbase.workspace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Generated;
import org.codehaus.jackson.annotate.JsonAnyGetter;
import org.codehaus.jackson.annotate.JsonAnySetter;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonPropertyOrder;
import org.codehaus.jackson.map.annotate.JsonSerialize;


/**
 * <p>Original spec-file type: SetPermissionsParams</p>
 * <pre>
 * Input parameters for the "set_permissions" function.
 *         One, and only one, of the following is required:
 *         ws_id id - the numerical ID of the workspace.
 *         ws_name workspace - name of the workspace or the workspace ID in KBase
 *                 format, e.g. kb|ws.78.
 *         Required arguments:
 *         permission new_permission - the permission to assign to the users.
 *         list<username> users - the users whose permissions will be altered.
 * </pre>
 * 
 */
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspace",
    "id",
    "new_permission",
    "users"
})
public class SetPermissionsParams {

    @JsonProperty("workspace")
    private String workspace;
    @JsonProperty("id")
    private Integer id;
    @JsonProperty("new_permission")
    private String newPermission;
    @JsonProperty("users")
    private List<String> users = new ArrayList<String>();
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("workspace")
    public String getWorkspace() {
        return workspace;
    }

    @JsonProperty("workspace")
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public SetPermissionsParams withWorkspace(String workspace) {
        this.workspace = workspace;
        return this;
    }

    @JsonProperty("id")
    public Integer getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(Integer id) {
        this.id = id;
    }

    public SetPermissionsParams withId(Integer id) {
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

    public SetPermissionsParams withNewPermission(String newPermission) {
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
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperties(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
