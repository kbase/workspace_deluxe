
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspace",
    "id"
})
public class WorkspaceIdentity {

    @JsonProperty("workspace")
    private String workspace;
    @JsonProperty("id")
    private Long id;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("workspace")
    public String getWorkspace() {
        return workspace;
    }

    @JsonProperty("workspace")
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public WorkspaceIdentity withWorkspace(String workspace) {
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

    public WorkspaceIdentity withId(Long id) {
        this.id = id;
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
        return ((((((("WorkspaceIdentity"+" [workspace=")+ workspace)+", id=")+ id)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
