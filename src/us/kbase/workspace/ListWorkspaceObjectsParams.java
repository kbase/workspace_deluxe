
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
 * <p>Original spec-file type: list_workspace_objects_params</p>
 * <pre>
 * Input parameters for the "list_workspace_objects" function. Provided
 * for backwards compatibility.
 * Required arguments:
 * ws_name workspace - Name of the workspace for which objects should be
 *         listed
 * Optional arguments:
 * type_string type - type of the objects to be listed. Here, omitting
 *         version information will find any objects that match the provided
 *         type - e.g. Foo.Bar-0 will match Foo.Bar-0.X where X is any
 *         existing version.
 * boolean showDeletedObject - show objects that have been deleted
 * string auth - the authentication token of the KBase account requesting
 *         access. Overrides the client provided authorization credentials if
 *         they exist.
 *         
 * @deprecated Workspaces.ListWorkspaceInfoParams
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspace",
    "type",
    "showDeletedObject",
    "auth"
})
public class ListWorkspaceObjectsParams {

    @JsonProperty("workspace")
    private String workspace;
    @JsonProperty("type")
    private String type;
    @JsonProperty("showDeletedObject")
    private Long showDeletedObject;
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

    public ListWorkspaceObjectsParams withWorkspace(String workspace) {
        this.workspace = workspace;
        return this;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    public ListWorkspaceObjectsParams withType(String type) {
        this.type = type;
        return this;
    }

    @JsonProperty("showDeletedObject")
    public Long getShowDeletedObject() {
        return showDeletedObject;
    }

    @JsonProperty("showDeletedObject")
    public void setShowDeletedObject(Long showDeletedObject) {
        this.showDeletedObject = showDeletedObject;
    }

    public ListWorkspaceObjectsParams withShowDeletedObject(Long showDeletedObject) {
        this.showDeletedObject = showDeletedObject;
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

    public ListWorkspaceObjectsParams withAuth(String auth) {
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
        return ((((((((((("ListWorkspaceObjectsParams"+" [workspace=")+ workspace)+", type=")+ type)+", showDeletedObject=")+ showDeletedObject)+", auth=")+ auth)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
