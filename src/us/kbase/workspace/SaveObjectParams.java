
package us.kbase.workspace;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Generated;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import us.kbase.common.service.UObject;


/**
 * <p>Original spec-file type: save_object_params</p>
 * <pre>
 * Input parameters for the "save_object" function. Provided for backwards
 * compatibility.
 *         
 * Required arguments:
 * type_string type - type of the object to be saved
 * ws_name workspace - name of the workspace where the object is to be
 *         saved
 * obj_name id - name behind which the object will be saved in the
 *         workspace
 * UnspecifiedObject data - data to be saved in the workspace
 * Optional arguments:
 * usermeta metadata - a hash of metadata to be associated with the object
 * string auth - the authentication token of the KBase account accessing
 *         the workspace. Overrides the client provided authorization
 *         credentials if they exist.
 * @deprecated
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "id",
    "type",
    "data",
    "workspace",
    "metadata",
    "auth"
})
public class SaveObjectParams {

    @JsonProperty("id")
    private java.lang.String id;
    @JsonProperty("type")
    private java.lang.String type;
    @JsonProperty("data")
    private UObject data;
    @JsonProperty("workspace")
    private java.lang.String workspace;
    @JsonProperty("metadata")
    private Map<String, String> metadata;
    @JsonProperty("auth")
    private java.lang.String auth;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("id")
    public java.lang.String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(java.lang.String id) {
        this.id = id;
    }

    public SaveObjectParams withId(java.lang.String id) {
        this.id = id;
        return this;
    }

    @JsonProperty("type")
    public java.lang.String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(java.lang.String type) {
        this.type = type;
    }

    public SaveObjectParams withType(java.lang.String type) {
        this.type = type;
        return this;
    }

    @JsonProperty("data")
    public UObject getData() {
        return data;
    }

    @JsonProperty("data")
    public void setData(UObject data) {
        this.data = data;
    }

    public SaveObjectParams withData(UObject data) {
        this.data = data;
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

    public SaveObjectParams withWorkspace(java.lang.String workspace) {
        this.workspace = workspace;
        return this;
    }

    @JsonProperty("metadata")
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @JsonProperty("metadata")
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public SaveObjectParams withMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
        return this;
    }

    @JsonProperty("auth")
    public java.lang.String getAuth() {
        return auth;
    }

    @JsonProperty("auth")
    public void setAuth(java.lang.String auth) {
        this.auth = auth;
    }

    public SaveObjectParams withAuth(java.lang.String auth) {
        this.auth = auth;
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
        return ((((((((((((((("SaveObjectParams"+" [id=")+ id)+", type=")+ type)+", data=")+ data)+", workspace=")+ workspace)+", metadata=")+ metadata)+", auth=")+ auth)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
