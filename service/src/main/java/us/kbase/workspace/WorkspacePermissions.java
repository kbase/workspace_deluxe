
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
 * <p>Original spec-file type: WorkspacePermissions</p>
 * <pre>
 * A set of workspace permissions.
 * perms - the list of permissions for each requested workspace
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "perms"
})
public class WorkspacePermissions {

    @JsonProperty("perms")
    private List<Map<String, String>> perms;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("perms")
    public List<Map<String, String>> getPerms() {
        return perms;
    }

    @JsonProperty("perms")
    public void setPerms(List<Map<String, String>> perms) {
        this.perms = perms;
    }

    public WorkspacePermissions withPerms(List<Map<String, String>> perms) {
        this.perms = perms;
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
        return ((((("WorkspacePermissions"+" [perms=")+ perms)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
