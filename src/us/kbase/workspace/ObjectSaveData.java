
package us.kbase.workspace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Generated;
import org.codehaus.jackson.annotate.JsonAnyGetter;
import org.codehaus.jackson.annotate.JsonAnySetter;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonPropertyOrder;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import us.kbase.common.service.UObject;


/**
 * <p>Original spec-file type: ObjectSaveData</p>
 * <pre>
 * An object and associated data required for saving.
 *         Required parameters:
 *         type_id type - the type of the object.
 *         UnspecifiedObject data - the object data.
 *         
 *         Optional parameters:
 *         One of an object name or id. If no name or id is provided the name
 *                 will be set to the object id as a string, possibly with -\d+
 *                 appended if that object id already exists as a name.
 *         obj_name name - the name of the object.
 *         obj_id objid - the id of the object to save over.
 *         usermeta metadata - arbitrary user-supplied metadata for the object,
 *                 not to exceed 16kb.
 *         list<ProvenanceAction> provenance - provenance data for the object.
 *         type_ver tver - the version of the type. If the version or minor
 *                 version is not provided the latest version will be assumed.
 *         boolean hidden - true if this object should not be listed when listing
 *                 workspace objects.
 * </pre>
 * 
 */
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "type",
    "data",
    "name",
    "objid",
    "metadata",
    "provenance",
    "tver",
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
    private Integer objid;
    @JsonProperty("metadata")
    private Map<String, String> metadata;
    @JsonProperty("provenance")
    private List<ProvenanceAction> provenance = new ArrayList<ProvenanceAction>();
    @JsonProperty("tver")
    private java.lang.String tver;
    @JsonProperty("hidden")
    private Integer hidden;
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
    public Integer getObjid() {
        return objid;
    }

    @JsonProperty("objid")
    public void setObjid(Integer objid) {
        this.objid = objid;
    }

    public ObjectSaveData withObjid(Integer objid) {
        this.objid = objid;
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

    public ObjectSaveData withMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
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

    @JsonProperty("tver")
    public java.lang.String getTver() {
        return tver;
    }

    @JsonProperty("tver")
    public void setTver(java.lang.String tver) {
        this.tver = tver;
    }

    public ObjectSaveData withTver(java.lang.String tver) {
        this.tver = tver;
        return this;
    }

    @JsonProperty("hidden")
    public Integer getHidden() {
        return hidden;
    }

    @JsonProperty("hidden")
    public void setHidden(Integer hidden) {
        this.hidden = hidden;
    }

    public ObjectSaveData withHidden(Integer hidden) {
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

}
