
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
 * <p>Original spec-file type: SaveObjectsParams</p>
 * <pre>
 * Input parameters for the "save_objects" function.
 *         One, and only one, of the following is required:
 *         ws_id id - the numerical ID of the workspace.
 *         ws_name workspace - name of the workspace or the workspace ID in KBase
 *                 format, e.g. kb|ws.78.
 *         
 *         Required arguments:
 *         list<ObjectSaveData> objects - the objects to save.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspace",
    "id",
    "objects"
})
public class SaveObjectsParams {

    @JsonProperty("workspace")
    private String workspace;
    @JsonProperty("id")
    private Long id;
    @JsonProperty("objects")
    private List<ObjectSaveData> objects;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("workspace")
    public String getWorkspace() {
        return workspace;
    }

    @JsonProperty("workspace")
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public SaveObjectsParams withWorkspace(String workspace) {
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

    public SaveObjectsParams withId(Long id) {
        this.id = id;
        return this;
    }

    @JsonProperty("objects")
    public List<ObjectSaveData> getObjects() {
        return objects;
    }

    @JsonProperty("objects")
    public void setObjects(List<ObjectSaveData> objects) {
        this.objects = objects;
    }

    public SaveObjectsParams withObjects(List<ObjectSaveData> objects) {
        this.objects = objects;
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
        return ((((((((("SaveObjectsParams"+" [workspace=")+ workspace)+", id=")+ id)+", objects=")+ objects)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
