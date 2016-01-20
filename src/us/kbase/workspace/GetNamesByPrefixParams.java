
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
 * <p>Original spec-file type: GetNamesByPrefixParams</p>
 * <pre>
 * Input parameters for the get_names_by_prefix function.
 *         Required arguments:
 *         list<WorkspaceIdentity> workspaces - the workspaces to search.
 *         string prefix - the prefix of the object names to return.
 *         
 *         Optional arguments:
 *         boolean includeHidden - include names of hidden objects in the results.
 *                 Default false.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspaces",
    "prefix",
    "includeHidden"
})
public class GetNamesByPrefixParams {

    @JsonProperty("workspaces")
    private List<WorkspaceIdentity> workspaces;
    @JsonProperty("prefix")
    private String prefix;
    @JsonProperty("includeHidden")
    private Long includeHidden;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("workspaces")
    public List<WorkspaceIdentity> getWorkspaces() {
        return workspaces;
    }

    @JsonProperty("workspaces")
    public void setWorkspaces(List<WorkspaceIdentity> workspaces) {
        this.workspaces = workspaces;
    }

    public GetNamesByPrefixParams withWorkspaces(List<WorkspaceIdentity> workspaces) {
        this.workspaces = workspaces;
        return this;
    }

    @JsonProperty("prefix")
    public String getPrefix() {
        return prefix;
    }

    @JsonProperty("prefix")
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public GetNamesByPrefixParams withPrefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    @JsonProperty("includeHidden")
    public Long getIncludeHidden() {
        return includeHidden;
    }

    @JsonProperty("includeHidden")
    public void setIncludeHidden(Long includeHidden) {
        this.includeHidden = includeHidden;
    }

    public GetNamesByPrefixParams withIncludeHidden(Long includeHidden) {
        this.includeHidden = includeHidden;
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
        return ((((((((("GetNamesByPrefixParams"+" [workspaces=")+ workspaces)+", prefix=")+ prefix)+", includeHidden=")+ includeHidden)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
