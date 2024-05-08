
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
import us.kbase.common.service.UObject;


/**
 * <p>Original spec-file type: ObjectSaveData</p>
 * <pre>
 * An object and associated data required for saving.
 *                 Required arguments:
 *                 type_string type - the type of the object. Omit the version information
 *                         to use the latest version.
 *                 UnspecifiedObject data - the object data.
 *                 One, and only one, of:
 *                         obj_name name - the name of the object.
 *                         obj_id objid - the id of the object to save over.
 *                 Optional arguments:
 *                 usermeta meta - arbitrary user-supplied metadata for the object,
 *                         not to exceed 16kb; if the object type specifies automatic
 *                         metadata extraction with the 'meta ws' annotation, and your
 *                         metadata name conflicts, then your metadata will be silently
 *                         overwritten.
 *                 list<ProvenanceAction> provenance - provenance data for the object.
 *                 boolean hidden - true if this object should not be listed when listing
 *                         workspace objects.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "type",
    "data",
    "name",
    "objid",
    "meta",
    "provenance",
    "hidden"
})
public class ObjectSaveData {

    @JsonProperty("type")
    private java.lang.String type;
    @JsonProperty("data")
    private UObject data;
    @JsonProperty("name")
    private java.lang.String name;
    @JsonProperty("objid")
    private Long objid;
    @JsonProperty("meta")
    private Map<String, String> meta;
    @JsonProperty("provenance")
    private List<ProvenanceAction> provenance;
    @JsonProperty("hidden")
    private Long hidden;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("type")
    public java.lang.String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(java.lang.String type) {
        this.type = type;
    }

    public ObjectSaveData withType(java.lang.String type) {
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

    public ObjectSaveData withData(UObject data) {
        this.data = data;
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

    public ObjectSaveData withName(java.lang.String name) {
        this.name = name;
        return this;
    }

    @JsonProperty("objid")
    public Long getObjid() {
        return objid;
    }

    @JsonProperty("objid")
    public void setObjid(Long objid) {
        this.objid = objid;
    }

    public ObjectSaveData withObjid(Long objid) {
        this.objid = objid;
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

    public ObjectSaveData withMeta(Map<String, String> meta) {
        this.meta = meta;
        return this;
    }

    @JsonProperty("provenance")
    public List<ProvenanceAction> getProvenance() {
        return provenance;
    }

    @JsonProperty("provenance")
    public void setProvenance(List<ProvenanceAction> provenance) {
        this.provenance = provenance;
    }

    public ObjectSaveData withProvenance(List<ProvenanceAction> provenance) {
        this.provenance = provenance;
        return this;
    }

    @JsonProperty("hidden")
    public Long getHidden() {
        return hidden;
    }

    @JsonProperty("hidden")
    public void setHidden(Long hidden) {
        this.hidden = hidden;
    }

    public ObjectSaveData withHidden(Long hidden) {
        this.hidden = hidden;
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
        return ((((((((((((((((("ObjectSaveData"+" [type=")+ type)+", data=")+ data)+", name=")+ name)+", objid=")+ objid)+", meta=")+ meta)+", provenance=")+ provenance)+", hidden=")+ hidden)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
