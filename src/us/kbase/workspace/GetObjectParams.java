
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
 * <p>Original spec-file type: get_object_params</p>
 * <pre>
 * Input parameters for the "get_object" function. Provided for backwards
 * compatibility.
 *         
 * Required arguments:
 * ws_name workspace - Name of the workspace containing the object to be
 *         retrieved
 * obj_name id - Name of the object to be retrieved
 * Optional arguments:
 * int instance - Version of the object to be retrieved, enabling
 *         retrieval of any previous version of an object
 * string auth - the authentication token of the KBase account accessing
 *         the object. Overrides the client provided authorization
 *         credentials if they exist.
 * @deprecated Workspace.ObjectIdentity
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "id",
    "workspace",
    "instance",
    "auth"
})
public class GetObjectParams {

    @JsonProperty("id")
    private String id;
    @JsonProperty("workspace")
    private String workspace;
    @JsonProperty("instance")
    private Long instance;
    @JsonProperty("auth")
    private String auth;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    public GetObjectParams withId(String id) {
        this.id = id;
        return this;
    }

    @JsonProperty("workspace")
    public String getWorkspace() {
        return workspace;
    }

    @JsonProperty("workspace")
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public GetObjectParams withWorkspace(String workspace) {
        this.workspace = workspace;
        return this;
    }

    @JsonProperty("instance")
    public Long getInstance() {
        return instance;
    }

    @JsonProperty("instance")
    public void setInstance(Long instance) {
        this.instance = instance;
    }

    public GetObjectParams withInstance(Long instance) {
        this.instance = instance;
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

    public GetObjectParams withAuth(String auth) {
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
        return ((((((((((("GetObjectParams"+" [id=")+ id)+", workspace=")+ workspace)+", instance=")+ instance)+", auth=")+ auth)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
