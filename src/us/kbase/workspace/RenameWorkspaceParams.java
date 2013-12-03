
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
 * <p>Original spec-file type: RenameWorkspaceParams</p>
 * <pre>
 * Input parameters for the 'rename_workspace' function.
 * Required arguments:
 * WorkspaceIdentity wsi - the workspace to rename.
 * ws_name new_name - the new name for the workspace.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "wsi",
    "new_name"
})
public class RenameWorkspaceParams {

    /**
     * <p>Original spec-file type: WorkspaceIdentity</p>
     * <pre>
     * A workspace identifier.
     *                 Select a workspace by one, and only one, of the numerical id or name,
     *                         where the name can also be a KBase ID including the numerical id,
     *                         e.g. kb|ws.35.
     *                 ws_id id - the numerical ID of the workspace.
     *                 ws_name workspace - name of the workspace or the workspace ID in KBase
     *                         format, e.g. kb|ws.78.
     * </pre>
     * 
     */
    @JsonProperty("wsi")
    private WorkspaceIdentity wsi;
    @JsonProperty("new_name")
    private String newName;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * <p>Original spec-file type: WorkspaceIdentity</p>
     * <pre>
     * A workspace identifier.
     *                 Select a workspace by one, and only one, of the numerical id or name,
     *                         where the name can also be a KBase ID including the numerical id,
     *                         e.g. kb|ws.35.
     *                 ws_id id - the numerical ID of the workspace.
     *                 ws_name workspace - name of the workspace or the workspace ID in KBase
     *                         format, e.g. kb|ws.78.
     * </pre>
     * 
     */
    @JsonProperty("wsi")
    public WorkspaceIdentity getWsi() {
        return wsi;
    }

    /**
     * <p>Original spec-file type: WorkspaceIdentity</p>
     * <pre>
     * A workspace identifier.
     *                 Select a workspace by one, and only one, of the numerical id or name,
     *                         where the name can also be a KBase ID including the numerical id,
     *                         e.g. kb|ws.35.
     *                 ws_id id - the numerical ID of the workspace.
     *                 ws_name workspace - name of the workspace or the workspace ID in KBase
     *                         format, e.g. kb|ws.78.
     * </pre>
     * 
     */
    @JsonProperty("wsi")
    public void setWsi(WorkspaceIdentity wsi) {
        this.wsi = wsi;
    }

    public RenameWorkspaceParams withWsi(WorkspaceIdentity wsi) {
        this.wsi = wsi;
        return this;
    }

    @JsonProperty("new_name")
    public String getNewName() {
        return newName;
    }

    @JsonProperty("new_name")
    public void setNewName(String newName) {
        this.newName = newName;
    }

    public RenameWorkspaceParams withNewName(String newName) {
        this.newName = newName;
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
        return ((((((("RenameWorkspaceParams"+" [wsi=")+ wsi)+", newName=")+ newName)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
