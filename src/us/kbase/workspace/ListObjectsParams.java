
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
 *                 permission perm - filter objects by permission level. 'None' and
 *                         'readable' are ignored.
 *                 list<username> savedby - filter objects by the user that saved or
 *                         copied the object.
 *                 usermeta meta - filter objects by the user supplied metadata. NOTE:
 *                         only one key/value pair is supported at this time. A full map
 *                         is provided as input for the possibility for expansion in the
 *                         future.
 *                 timestamp after - only return objects that were created after this
 *                         date.
 *                 timestamp before - only return objects that were created before this
 *                         date.
 *                 boolean showDeleted - show deleted objects in workspaces to which the
 *                         user has write access.
 *                 boolean showOnlyDeleted - only show deleted objects in workspaces to
 *                         which the user has write access.
 *                 boolean showHidden - show hidden objects.
 *                 boolean showAllVersions - show all versions of each object that match
 *                         the filters rather than only the most recent version.
 *                 boolean includeMetadata - include the user provided metadata in the
 *                         returned object_info. If false (0 or null), the default, the
 *                         metadata will be null.
 *                 boolean excludeGlobal - exclude objects in global workspaces. This
 *                         parameter only has an effect when filtering by types alone.
 *                 int skip - skip the first X objects. Maximum value is 2^31, skip values
 *                         < 0 are treated as 0, the default.
 *                 int limit - limit the output to X objects. Default and maximum value
 *                         is 10000. Limit values < 1 are treated as 10000, the default.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspaces",
    "ids",
    "type",
    "perm",
    "savedby",
    "meta",
    "after",
    "before",
    "showDeleted",
    "showOnlyDeleted",
    "showHidden",
    "showAllVersions",
    "includeMetadata",
    "excludeGlobal",
    "skip",
    "limit"
})
public class ListObjectsParams {

    @JsonProperty("workspaces")
    private List<String> workspaces;
    @JsonProperty("ids")
    private List<Long> ids;
    @JsonProperty("type")
    private java.lang.String type;
    @JsonProperty("perm")
    private java.lang.String perm;
    @JsonProperty("savedby")
    private List<String> savedby;
    @JsonProperty("meta")
    private Map<String, String> meta;
    @JsonProperty("after")
    private java.lang.String after;
    @JsonProperty("before")
    private java.lang.String before;
    @JsonProperty("showDeleted")
    private java.lang.Long showDeleted;
    @JsonProperty("showOnlyDeleted")
    private java.lang.Long showOnlyDeleted;
    @JsonProperty("showHidden")
    private java.lang.Long showHidden;
    @JsonProperty("showAllVersions")
    private java.lang.Long showAllVersions;
    @JsonProperty("includeMetadata")
    private java.lang.Long includeMetadata;
    @JsonProperty("excludeGlobal")
    private java.lang.Long excludeGlobal;
    @JsonProperty("skip")
    private java.lang.Long skip;
    @JsonProperty("limit")
    private java.lang.Long limit;
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

    @JsonProperty("perm")
    public java.lang.String getPerm() {
        return perm;
    }

    @JsonProperty("perm")
    public void setPerm(java.lang.String perm) {
        this.perm = perm;
    }

    public ListObjectsParams withPerm(java.lang.String perm) {
        this.perm = perm;
        return this;
    }

    @JsonProperty("savedby")
    public List<String> getSavedby() {
        return savedby;
    }

    @JsonProperty("savedby")
    public void setSavedby(List<String> savedby) {
        this.savedby = savedby;
    }

    public ListObjectsParams withSavedby(List<String> savedby) {
        this.savedby = savedby;
        return this;
    }

    @JsonProperty("meta")
    public Map<String, String> getMeta() {
        return meta;
    }

    @JsonProperty("meta")
    public void setMeta(Map<String, String> meta) {
        this.meta = meta;
    }

    public ListObjectsParams withMeta(Map<String, String> meta) {
        this.meta = meta;
        return this;
    }

    @JsonProperty("after")
    public java.lang.String getAfter() {
        return after;
    }

    @JsonProperty("after")
    public void setAfter(java.lang.String after) {
        this.after = after;
    }

    public ListObjectsParams withAfter(java.lang.String after) {
        this.after = after;
        return this;
    }

    @JsonProperty("before")
    public java.lang.String getBefore() {
        return before;
    }

    @JsonProperty("before")
    public void setBefore(java.lang.String before) {
        this.before = before;
    }

    public ListObjectsParams withBefore(java.lang.String before) {
        this.before = before;
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

    @JsonProperty("showOnlyDeleted")
    public java.lang.Long getShowOnlyDeleted() {
        return showOnlyDeleted;
    }

    @JsonProperty("showOnlyDeleted")
    public void setShowOnlyDeleted(java.lang.Long showOnlyDeleted) {
        this.showOnlyDeleted = showOnlyDeleted;
    }

    public ListObjectsParams withShowOnlyDeleted(java.lang.Long showOnlyDeleted) {
        this.showOnlyDeleted = showOnlyDeleted;
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

    @JsonProperty("excludeGlobal")
    public java.lang.Long getExcludeGlobal() {
        return excludeGlobal;
    }

    @JsonProperty("excludeGlobal")
    public void setExcludeGlobal(java.lang.Long excludeGlobal) {
        this.excludeGlobal = excludeGlobal;
    }

    public ListObjectsParams withExcludeGlobal(java.lang.Long excludeGlobal) {
        this.excludeGlobal = excludeGlobal;
        return this;
    }

    @JsonProperty("skip")
    public java.lang.Long getSkip() {
        return skip;
    }

    @JsonProperty("skip")
    public void setSkip(java.lang.Long skip) {
        this.skip = skip;
    }

    public ListObjectsParams withSkip(java.lang.Long skip) {
        this.skip = skip;
        return this;
    }

    @JsonProperty("limit")
    public java.lang.Long getLimit() {
        return limit;
    }

    @JsonProperty("limit")
    public void setLimit(java.lang.Long limit) {
        this.limit = limit;
    }

    public ListObjectsParams withLimit(java.lang.Long limit) {
        this.limit = limit;
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
        return ((((((((((((((((((((((((((((((((((("ListObjectsParams"+" [workspaces=")+ workspaces)+", ids=")+ ids)+", type=")+ type)+", perm=")+ perm)+", savedby=")+ savedby)+", meta=")+ meta)+", after=")+ after)+", before=")+ before)+", showDeleted=")+ showDeleted)+", showOnlyDeleted=")+ showOnlyDeleted)+", showHidden=")+ showHidden)+", showAllVersions=")+ showAllVersions)+", includeMetadata=")+ includeMetadata)+", excludeGlobal=")+ excludeGlobal)+", skip=")+ skip)+", limit=")+ limit)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
