
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
 * <p>Original spec-file type: ObjectInfo</p>
 * <pre>
 * Information about an object as a struct rather than a tuple.
 * This allows adding fields in a backward compatible way in the future.
 * Includes more fields than object_info.
 * obj_id objid - the numerical id of the object.
 * obj_name name - the name of the object.
 * type_string type - the type of the object.
 * timestamp save_date - the save date of the object.
 * obj_ver ver - the version of the object.
 * username saved_by - the user that saved or copied the object.
 * ws_id wsid - the workspace containing the object.
 * ws_name workspace - the workspace containing the object.
 * string chsum - the md5 checksum of the object.
 * int size - the size of the object in bytes.
 * usermeta meta - arbitrary user-supplied metadata about the object.
 * usermeta adminmeta - service administrator metadata set on an object. Unlike most
 *         other object fields, admin metadata is mutable.
 * list<obj_ref> path - the path to the object.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "objid",
    "name",
    "type",
    "save_date",
    "version",
    "saved_by",
    "wsid",
    "workspace",
    "chsum",
    "size",
    "meta",
    "adminmeta",
    "path"
})
public class ObjectInfo {

    @JsonProperty("objid")
    private Long objid;
    @JsonProperty("name")
    private java.lang.String name;
    @JsonProperty("type")
    private java.lang.String type;
    @JsonProperty("save_date")
    private java.lang.String saveDate;
    @JsonProperty("version")
    private Long version;
    @JsonProperty("saved_by")
    private java.lang.String savedBy;
    @JsonProperty("wsid")
    private Long wsid;
    @JsonProperty("workspace")
    private java.lang.String workspace;
    @JsonProperty("chsum")
    private java.lang.String chsum;
    @JsonProperty("size")
    private Long size;
    @JsonProperty("meta")
    private Map<String, String> meta;
    @JsonProperty("adminmeta")
    private Map<String, String> adminmeta;
    @JsonProperty("path")
    private List<String> path;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("objid")
    public Long getObjid() {
        return objid;
    }

    @JsonProperty("objid")
    public void setObjid(Long objid) {
        this.objid = objid;
    }

    public ObjectInfo withObjid(Long objid) {
        this.objid = objid;
        return this;
    }

    @JsonProperty("name")
    public java.lang.String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(java.lang.String name) {
        this.name = name;
    }

    public ObjectInfo withName(java.lang.String name) {
        this.name = name;
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

    public ObjectInfo withType(java.lang.String type) {
        this.type = type;
        return this;
    }

    @JsonProperty("save_date")
    public java.lang.String getSaveDate() {
        return saveDate;
    }

    @JsonProperty("save_date")
    public void setSaveDate(java.lang.String saveDate) {
        this.saveDate = saveDate;
    }

    public ObjectInfo withSaveDate(java.lang.String saveDate) {
        this.saveDate = saveDate;
        return this;
    }

    @JsonProperty("version")
    public Long getVersion() {
        return version;
    }

    @JsonProperty("version")
    public void setVersion(Long version) {
        this.version = version;
    }

    public ObjectInfo withVersion(Long version) {
        this.version = version;
        return this;
    }

    @JsonProperty("saved_by")
    public java.lang.String getSavedBy() {
        return savedBy;
    }

    @JsonProperty("saved_by")
    public void setSavedBy(java.lang.String savedBy) {
        this.savedBy = savedBy;
    }

    public ObjectInfo withSavedBy(java.lang.String savedBy) {
        this.savedBy = savedBy;
        return this;
    }

    @JsonProperty("wsid")
    public Long getWsid() {
        return wsid;
    }

    @JsonProperty("wsid")
    public void setWsid(Long wsid) {
        this.wsid = wsid;
    }

    public ObjectInfo withWsid(Long wsid) {
        this.wsid = wsid;
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

    public ObjectInfo withWorkspace(java.lang.String workspace) {
        this.workspace = workspace;
        return this;
    }

    @JsonProperty("chsum")
    public java.lang.String getChsum() {
        return chsum;
    }

    @JsonProperty("chsum")
    public void setChsum(java.lang.String chsum) {
        this.chsum = chsum;
    }

    public ObjectInfo withChsum(java.lang.String chsum) {
        this.chsum = chsum;
        return this;
    }

    @JsonProperty("size")
    public Long getSize() {
        return size;
    }

    @JsonProperty("size")
    public void setSize(Long size) {
        this.size = size;
    }

    public ObjectInfo withSize(Long size) {
        this.size = size;
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

    public ObjectInfo withMeta(Map<String, String> meta) {
        this.meta = meta;
        return this;
    }

    @JsonProperty("adminmeta")
    public Map<String, String> getAdminmeta() {
        return adminmeta;
    }

    @JsonProperty("adminmeta")
    public void setAdminmeta(Map<String, String> adminmeta) {
        this.adminmeta = adminmeta;
    }

    public ObjectInfo withAdminmeta(Map<String, String> adminmeta) {
        this.adminmeta = adminmeta;
        return this;
    }

    @JsonProperty("path")
    public List<String> getPath() {
        return path;
    }

    @JsonProperty("path")
    public void setPath(List<String> path) {
        this.path = path;
    }

    public ObjectInfo withPath(List<String> path) {
        this.path = path;
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
        return ((((((((((((((((((((((((((((("ObjectInfo"+" [objid=")+ objid)+", name=")+ name)+", type=")+ type)+", saveDate=")+ saveDate)+", version=")+ version)+", savedBy=")+ savedBy)+", wsid=")+ wsid)+", workspace=")+ workspace)+", chsum=")+ chsum)+", size=")+ size)+", meta=")+ meta)+", adminmeta=")+ adminmeta)+", path=")+ path)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
