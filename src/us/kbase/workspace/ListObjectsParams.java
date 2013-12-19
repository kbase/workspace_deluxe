
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
 * <p>Original spec-file type: ListObjectsParams</p>
 * <pre>
 * Parameters for the 'list_objects' function.
 *                 At least one of the following filters must be provided. It is strongly
 *                 recommended that the list is restricted to the workspaces of interest,
 *                 or the results may be very large:
 *                 list<ws_id> ids - the numerical IDs of the workspaces of interest.
 *                 list<ws_name> workspaces - names of the workspaces of interest or the
 *                         workspace IDs in KBase format, e.g. kb|ws.78.
 *                 type_string type - type of the objects to be listed.  Here, omitting
 *                         version information will find any objects that match the provided
 *                         type - e.g. Foo.Bar-0 will match Foo.Bar-0.X where X is any
 *                         existing version.
 *                 
 *                 Optional arguments:
 *                 boolean showDeleted - show deleted objects in workspaces to which the
 *                         user has write access.
 *                 boolean showHidden - show hidden objects.
 *                 boolean showAllVersions - show all versions of each object that match
 *                         the filters rather than only the most recent version.
 *                 boolean includeMetadata - include the user provided metadata in the
 *                         returned object_info. If false (0 or null), the default, the
 *                         metadata will be null.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspaces",
    "ids",
    "type",
    "showDeleted",
    "showHidden",
    "showAllVersions",
    "includeMetadata"
})
public class ListObjectsParams {

    @JsonProperty("workspaces")
    private List<String> workspaces;
    @JsonProperty("ids")
    private List<Long> ids;
    @JsonProperty("type")
    private java.lang.String type;
    @JsonProperty("showDeleted")
    private java.lang.Long showDeleted;
    @JsonProperty("showHidden")
    private java.lang.Long showHidden;
    @JsonProperty("showAllVersions")
    private java.lang.Long showAllVersions;
    @JsonProperty("includeMetadata")
    private java.lang.Long includeMetadata;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("workspaces")
    public List<String> getWorkspaces() {
        return workspaces;
    }

    @JsonProperty("workspaces")
    public void setWorkspaces(List<String> workspaces) {
        this.workspaces = workspaces;
    }

    public ListObjectsParams withWorkspaces(List<String> workspaces) {
        this.workspaces = workspaces;
        return this;
    }

    @JsonProperty("ids")
    public List<Long> getIds() {
        return ids;
    }

    @JsonProperty("ids")
    public void setIds(List<Long> ids) {
        this.ids = ids;
    }

    public ListObjectsParams withIds(List<Long> ids) {
        this.ids = ids;
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

    public ListObjectsParams withType(java.lang.String type) {
        this.type = type;
        return this;
    }

    @JsonProperty("showDeleted")
    public java.lang.Long getShowDeleted() {
        return showDeleted;
    }

    @JsonProperty("showDeleted")
    public void setShowDeleted(java.lang.Long showDeleted) {
        this.showDeleted = showDeleted;
    }

    public ListObjectsParams withShowDeleted(java.lang.Long showDeleted) {
        this.showDeleted = showDeleted;
        return this;
    }

    @JsonProperty("showHidden")
    public java.lang.Long getShowHidden() {
        return showHidden;
    }

    @JsonProperty("showHidden")
    public void setShowHidden(java.lang.Long showHidden) {
        this.showHidden = showHidden;
    }

    public ListObjectsParams withShowHidden(java.lang.Long showHidden) {
        this.showHidden = showHidden;
        return this;
    }

    @JsonProperty("showAllVersions")
    public java.lang.Long getShowAllVersions() {
        return showAllVersions;
    }

    @JsonProperty("showAllVersions")
    public void setShowAllVersions(java.lang.Long showAllVersions) {
        this.showAllVersions = showAllVersions;
    }

    public ListObjectsParams withShowAllVersions(java.lang.Long showAllVersions) {
        this.showAllVersions = showAllVersions;
        return this;
    }

    @JsonProperty("includeMetadata")
    public java.lang.Long getIncludeMetadata() {
        return includeMetadata;
    }

    @JsonProperty("includeMetadata")
    public void setIncludeMetadata(java.lang.Long includeMetadata) {
        this.includeMetadata = includeMetadata;
    }

    public ListObjectsParams withIncludeMetadata(java.lang.Long includeMetadata) {
        this.includeMetadata = includeMetadata;
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
        return ((((((((((((((((("ListObjectsParams"+" [workspaces=")+ workspaces)+", ids=")+ ids)+", type=")+ type)+", showDeleted=")+ showDeleted)+", showHidden=")+ showHidden)+", showAllVersions=")+ showAllVersions)+", includeMetadata=")+ includeMetadata)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
