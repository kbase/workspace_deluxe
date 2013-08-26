
package us.kbase.workspace;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Generated;
import org.codehaus.jackson.annotate.JsonAnyGetter;
import org.codehaus.jackson.annotate.JsonAnySetter;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonPropertyOrder;
import org.codehaus.jackson.map.annotate.JsonSerialize;


/**
 * <p>Original spec-file type: GetPermissionsParams</p>
 * <pre>
 * Input parameters for the "set_permissions" function.
 * One, and only one, of the following is required:
 * ws_id id - the numerical ID of the workspace.
 * ws_name workspace - name of the workspace or the workspace ID in KBase format, e.g. kb|ws.78.
 * </pre>
 * 
 */
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspace",
    "id"
})
public class GetPermissionsParams {

    @JsonProperty("workspace")
    private String workspace;
    @JsonProperty("id")
    private Integer id;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("workspace")
    public String getWorkspace() {
        return workspace;
    }

    @JsonProperty("workspace")
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public GetPermissionsParams withWorkspace(String workspace) {
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

    public GetPermissionsParams withId(Integer id) {
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

}
