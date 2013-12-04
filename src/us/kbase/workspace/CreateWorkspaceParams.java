
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
 * <p>Original spec-file type: CreateWorkspaceParams</p>
 * <pre>
 * Input parameters for the "create_workspace" function.
 *         Required arguments:
 *         ws_name workspace - name of the workspace to be created.
 *         
 *         Optional arguments:
 *         permission globalread - 'r' to set the new workspace globally readable,
 *                 default 'n'.
 *         string description - A free-text description of the new workspace, 1000
 *                 characters max. Longer strings will be mercilessly and brutally
 *                 truncated.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspace",
    "globalread",
    "description"
})
public class CreateWorkspaceParams {

    @JsonProperty("workspace")
    private String workspace;
    @JsonProperty("globalread")
    private String globalread;
    @JsonProperty("description")
    private String description;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("workspace")
    public String getWorkspace() {
        return workspace;
    }

    @JsonProperty("workspace")
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public CreateWorkspaceParams withWorkspace(String workspace) {
        this.workspace = workspace;
        return this;
    }

    @JsonProperty("globalread")
    public String getGlobalread() {
        return globalread;
    }

    @JsonProperty("globalread")
    public void setGlobalread(String globalread) {
        this.globalread = globalread;
    }

    public CreateWorkspaceParams withGlobalread(String globalread) {
        this.globalread = globalread;
        return this;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    public CreateWorkspaceParams withDescription(String description) {
        this.description = description;
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
        return ((((((((("CreateWorkspaceParams"+" [workspace=")+ workspace)+", globalread=")+ globalread)+", description=")+ description)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
