
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
 * <p>Original spec-file type: CloneWorkspaceParams</p>
 * <pre>
 * Input parameters for the "clone_workspace" function.
 *         Note that deleted objects are not cloned, although hidden objects are
 *         and remain hidden in the new workspace.
 *         Required arguments:
 *         WorkspaceIdentity wsi - the workspace to be cloned.
 *         ws_name workspace - name of the workspace to be cloned into. This must
 *                 be a non-existant workspace name.
 *         
 *         Optional arguments:
 *         permission globalread - 'r' to set the new workspace globally readable,
 *                 default 'n'.
 *         string description - A free-text description of the new workspace, 1000
 *                 characters max. Longer strings will be mercilessly and brutally
 *                 truncated.
 *         usermeta meta - arbitrary user-supplied metadata for the workspace.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "wsi",
    "workspace",
    "globalread",
    "description",
    "meta"
})
public class CloneWorkspaceParams {

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
    @JsonProperty("workspace")
    private java.lang.String workspace;
    @JsonProperty("globalread")
    private java.lang.String globalread;
    @JsonProperty("description")
    private java.lang.String description;
    @JsonProperty("meta")
    private Map<String, String> meta;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

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

    public CloneWorkspaceParams withWsi(WorkspaceIdentity wsi) {
        this.wsi = wsi;
        return this;
    }

    @JsonProperty("workspace")
    public java.lang.String getWorkspace() {
        return workspace;
    }

    @JsonProperty("workspace")
    public void setWorkspace(java.lang.String workspace) {
        this.workspace = workspace;
    }

    public CloneWorkspaceParams withWorkspace(java.lang.String workspace) {
        this.workspace = workspace;
        return this;
    }

    @JsonProperty("globalread")
    public java.lang.String getGlobalread() {
        return globalread;
    }

    @JsonProperty("globalread")
    public void setGlobalread(java.lang.String globalread) {
        this.globalread = globalread;
    }

    public CloneWorkspaceParams withGlobalread(java.lang.String globalread) {
        this.globalread = globalread;
        return this;
    }

    @JsonProperty("description")
    public java.lang.String getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(java.lang.String description) {
        this.description = description;
    }

    public CloneWorkspaceParams withDescription(java.lang.String description) {
        this.description = description;
        return this;
    }

    @JsonProperty("meta")
    public Map<String, String> getMeta() {
        return meta;
    }

    @JsonProperty("meta")
    public void setMeta(Map<String, String> meta) {
        this.meta = meta;
    }

    public CloneWorkspaceParams withMeta(Map<String, String> meta) {
        this.meta = meta;
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
        return ((((((((((((("CloneWorkspaceParams"+" [wsi=")+ wsi)+", workspace=")+ workspace)+", globalread=")+ globalread)+", description=")+ description)+", meta=")+ meta)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
