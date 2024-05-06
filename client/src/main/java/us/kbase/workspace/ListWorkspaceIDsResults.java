
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
 * <p>Original spec-file type: ListWorkspaceIDsResults</p>
 * <pre>
 * Results of the "list_workspace_ids" function.
 * list<int> workspaces - the workspaces to which the user has explicit
 *         access.
 * list<int> pub - the workspaces to which the user has access because
 *         they're globally readable.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspaces",
    "pub"
})
public class ListWorkspaceIDsResults {

    @JsonProperty("workspaces")
    private List<Long> workspaces;
    @JsonProperty("pub")
    private List<Long> pub;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("workspaces")
    public List<Long> getWorkspaces() {
        return workspaces;
    }

    @JsonProperty("workspaces")
    public void setWorkspaces(List<Long> workspaces) {
        this.workspaces = workspaces;
    }

    public ListWorkspaceIDsResults withWorkspaces(List<Long> workspaces) {
        this.workspaces = workspaces;
        return this;
    }

    @JsonProperty("pub")
    public List<Long> getPub() {
        return pub;
    }

    @JsonProperty("pub")
    public void setPub(List<Long> pub) {
        this.pub = pub;
    }

    public ListWorkspaceIDsResults withPub(List<Long> pub) {
        this.pub = pub;
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
        return ((((((("ListWorkspaceIDsResults"+" [workspaces=")+ workspaces)+", pub=")+ pub)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
