
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
 * <p>Original spec-file type: SaveObjectsParams</p>
 * <pre>
 * Input parameters for the "save_objects" function.
 *         One, and only one, of the following is required:
 *         ws_id id - the numerical ID of the workspace.
 *         ws_name workspace - name of the workspace or the workspace ID in KBase
 *                 format, e.g. kb|ws.78.
 *         Required arguments:
 *         list<ObjectSaveData> objects - the objects to save.
 * </pre>
 * 
 */
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
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
    private Integer id;
    @JsonProperty("objects")
    private List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
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
    public Integer getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(Integer id) {
        this.id = id;
    }

    public SaveObjectsParams withId(Integer id) {
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

}
