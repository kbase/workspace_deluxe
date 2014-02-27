
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
 * <p>Original spec-file type: ListWorkspaceInfoParams</p>
 * <pre>
 * Input parameters for the "list_workspace_info" function.
 * Optional parameters:
 * permission perm - filter workspaces by permission level. 'None' and
 *         'readable' are ignored.
 * list<username> owners - filter workspaces by owner.
 * usermeta meta - filter workspaces by the user supplied metadata. NOTE:
 *         only one key/value pair is supported at this time. A full map
 *         is provided as input for the possibility for expansion in the
 *         future.
 * timestamp after - only return workspaces that were modified after this
 *         date.
 * timestamp before - only return workspaces that were modified before
 *         this date.
 * boolean excludeGlobal - if excludeGlobal is true exclude world
 *         readable workspaces. Defaults to false.
 * boolean showDeleted - show deleted workspaces that are owned by the
 *         user.
 * boolean showOnlyDeleted - only show deleted workspaces that are owned
 *         by the user.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "perm",
    "owners",
    "meta",
    "after",
    "before",
    "excludeGlobal",
    "showDeleted",
    "showOnlyDeleted"
})
public class ListWorkspaceInfoParams {

    @JsonProperty("perm")
    private java.lang.String perm;
    @JsonProperty("owners")
    private List<String> owners;
    @JsonProperty("meta")
    private Map<String, String> meta;
    @JsonProperty("after")
    private java.lang.String after;
    @JsonProperty("before")
    private java.lang.String before;
    @JsonProperty("excludeGlobal")
    private Long excludeGlobal;
    @JsonProperty("showDeleted")
    private Long showDeleted;
    @JsonProperty("showOnlyDeleted")
    private Long showOnlyDeleted;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("perm")
    public java.lang.String getPerm() {
        return perm;
    }

    @JsonProperty("perm")
    public void setPerm(java.lang.String perm) {
        this.perm = perm;
    }

    public ListWorkspaceInfoParams withPerm(java.lang.String perm) {
        this.perm = perm;
        return this;
    }

    @JsonProperty("owners")
    public List<String> getOwners() {
        return owners;
    }

    @JsonProperty("owners")
    public void setOwners(List<String> owners) {
        this.owners = owners;
    }

    public ListWorkspaceInfoParams withOwners(List<String> owners) {
        this.owners = owners;
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

    public ListWorkspaceInfoParams withMeta(Map<String, String> meta) {
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

    public ListWorkspaceInfoParams withAfter(java.lang.String after) {
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

    public ListWorkspaceInfoParams withBefore(java.lang.String before) {
        this.before = before;
        return this;
    }

    @JsonProperty("excludeGlobal")
    public Long getExcludeGlobal() {
        return excludeGlobal;
    }

    @JsonProperty("excludeGlobal")
    public void setExcludeGlobal(Long excludeGlobal) {
        this.excludeGlobal = excludeGlobal;
    }

    public ListWorkspaceInfoParams withExcludeGlobal(Long excludeGlobal) {
        this.excludeGlobal = excludeGlobal;
        return this;
    }

    @JsonProperty("showDeleted")
    public Long getShowDeleted() {
        return showDeleted;
    }

    @JsonProperty("showDeleted")
    public void setShowDeleted(Long showDeleted) {
        this.showDeleted = showDeleted;
    }

    public ListWorkspaceInfoParams withShowDeleted(Long showDeleted) {
        this.showDeleted = showDeleted;
        return this;
    }

    @JsonProperty("showOnlyDeleted")
    public Long getShowOnlyDeleted() {
        return showOnlyDeleted;
    }

    @JsonProperty("showOnlyDeleted")
    public void setShowOnlyDeleted(Long showOnlyDeleted) {
        this.showOnlyDeleted = showOnlyDeleted;
    }

    public ListWorkspaceInfoParams withShowOnlyDeleted(Long showOnlyDeleted) {
        this.showOnlyDeleted = showOnlyDeleted;
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
        return ((((((((((((((((((("ListWorkspaceInfoParams"+" [perm=")+ perm)+", owners=")+ owners)+", meta=")+ meta)+", after=")+ after)+", before=")+ before)+", excludeGlobal=")+ excludeGlobal)+", showDeleted=")+ showDeleted)+", showOnlyDeleted=")+ showOnlyDeleted)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
