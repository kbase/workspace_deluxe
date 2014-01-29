
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
 * <p>Original spec-file type: AlterWorkspaceMetadataParams</p>
 * <pre>
 * Input parameters for the "alter_workspace_metadata" function.
 * Required arguments:
 * WorkspaceIdentity wsi - the workspace to be altered
 * One or both of the following arguments are required:
 * usermeta new - metadata to assign to the workspace. Duplicate keys will
 *         be overwritten.
 * list<string> remove - these keys will be removed from the workspace
 *         metadata key/value pairs.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "wsi",
    "new",
    "remove"
})
public class AlterWorkspaceMetadataParams {

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
    @JsonProperty("new")
    private Map<String, String> _new;
    @JsonProperty("remove")
    private List<String> remove;
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

    public AlterWorkspaceMetadataParams withWsi(WorkspaceIdentity wsi) {
        this.wsi = wsi;
        return this;
    }

    @JsonProperty("new")
    public Map<String, String> getNew() {
        return _new;
    }

    @JsonProperty("new")
    public void setNew(Map<String, String> _new) {
        this._new = _new;
    }

    public AlterWorkspaceMetadataParams withNew(Map<String, String> _new) {
        this._new = _new;
        return this;
    }

    @JsonProperty("remove")
    public List<String> getRemove() {
        return remove;
    }

    @JsonProperty("remove")
    public void setRemove(List<String> remove) {
        this.remove = remove;
    }

    public AlterWorkspaceMetadataParams withRemove(List<String> remove) {
        this.remove = remove;
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
        return ((((((((("AlterWorkspaceMetadataParams"+" [wsi=")+ wsi)+", _new=")+ _new)+", remove=")+ remove)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
