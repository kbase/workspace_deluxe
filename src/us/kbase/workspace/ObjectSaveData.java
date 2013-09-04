
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


/**
 * <p>Original spec-file type: ObjectSaveData</p>
 * <pre>
 * An object and associated data required for saving.
 *         Required parameters:
 *         type_id type - the type of the object.
 *         mapping<string, UnspecifiedObject> data - the object data.
 *         Optional parameters:
 *         obj_name name - the name of the object. If no name is provided the name
 *                 will be set to the object id as a string.
 *         mapping<string, UnspecifiedObject>  metadata - arbitrary user-supplied
 *                 metadata for the object, not to exceed 16kb.
 *         list<ProvenanceAction> provenance - provenance data for the object.
 *         type_ver tver - the version of the type. If the version is not
 *                 provided the latest version will be assumed.
 * </pre>
 * 
 */
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "name",
    "metadata",
    "provenance",
    "type",
    "tver",
    "data"
})
public class ObjectSaveData {

    @JsonProperty("name")
    private java.lang.String name;
    @JsonProperty("metadata")
    private Map<String, us.kbase.UObject> metadata;
    @JsonProperty("provenance")
    private List<ProvenanceAction> provenance = new ArrayList<ProvenanceAction>();
    @JsonProperty("type")
    private java.lang.String type;
    @JsonProperty("tver")
    private java.lang.String tver;
    @JsonProperty("data")
    private Map<String, us.kbase.UObject> data;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

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

    @JsonProperty("metadata")
    public Map<String, us.kbase.UObject> getMetadata() {
        return metadata;
    }

    @JsonProperty("metadata")
    public void setMetadata(Map<String, us.kbase.UObject> metadata) {
        this.metadata = metadata;
    }

    public ObjectSaveData withMetadata(Map<String, us.kbase.UObject> metadata) {
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

    @JsonProperty("data")
    public Map<String, us.kbase.UObject> getData() {
        return data;
    }

    @JsonProperty("data")
    public void setData(Map<String, us.kbase.UObject> data) {
        this.data = data;
    }

    public ObjectSaveData withData(Map<String, us.kbase.UObject> data) {
        this.data = data;
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
