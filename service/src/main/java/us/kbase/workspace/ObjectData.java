
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
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.UObject;


/**
 * <p>Original spec-file type: ObjectData</p>
 * <pre>
 * The data and supplemental info for an object.
 *                 UnspecifiedObject data - the object's data or subset data.
 *                 object_info info - information about the object.
 *                 ObjectInfo infostruct - information about the object as a structure rather than a tuple.
 *                 list<obj_ref> path - the path to the object through the object reference graph. All the
 *                         references in the path are absolute.
 *                 list<ProvenanceAction> provenance - the object's provenance.
 *                 username creator - the user that first saved the object to the workspace.
 *                 ws_id orig_wsid - the id of the workspace in which this object was
 *                                 originally saved. Missing for objects saved prior to version
 *                                 0.4.1.
 *                 timestamp created - the date the object was first saved to the
 *                         workspace.
 *                 epoch epoch - the date the object was first saved to the
 *                         workspace.
 *                 list<obj_ref> refs - the references contained within the object.
 *                 obj_ref copied - the reference of the source object if this object is
 *                         a copy and the copy source exists and is accessible.
 *                         null otherwise.
 *                 boolean copy_source_inaccessible - true if the object was copied from
 *                         another object, but that object is no longer accessible to the
 *                         user. False otherwise.
 *                 mapping<id_type, list<extracted_id>> extracted_ids - any ids extracted
 *                         from the object.
 *                 string handle_error - if an error occurs while setting ACLs on
 *                         embedded external IDs, it will be reported here. If not for historical reasons the
 *                         parameter would be called "external_id_error".
 *                 string handle_stacktrace - the stacktrace for handle_error. As above, the parameter
 *                         should be called "external_id_stacktrace".
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "data",
    "info",
    "infostruct",
    "path",
    "provenance",
    "creator",
    "orig_wsid",
    "created",
    "epoch",
    "refs",
    "copied",
    "copy_source_inaccessible",
    "extracted_ids",
    "handle_error",
    "handle_stacktrace"
})
public class ObjectData {

    @JsonProperty("data")
    private UObject data;
    @JsonProperty("info")
    private Tuple11 <Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> info;
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
    @JsonProperty("infostruct")
    private ObjectInfo infostruct;
    @JsonProperty("path")
    private List<String> path;
    @JsonProperty("provenance")
    private List<ProvenanceAction> provenance;
    @JsonProperty("creator")
    private java.lang.String creator;
    @JsonProperty("orig_wsid")
    private java.lang.Long origWsid;
    @JsonProperty("created")
    private java.lang.String created;
    @JsonProperty("epoch")
    private java.lang.Long epoch;
    @JsonProperty("refs")
    private List<String> refs;
    @JsonProperty("copied")
    private java.lang.String copied;
    @JsonProperty("copy_source_inaccessible")
    private java.lang.Long copySourceInaccessible;
    @JsonProperty("extracted_ids")
    private Map<String, List<String>> extractedIds;
    @JsonProperty("handle_error")
    private java.lang.String handleError;
    @JsonProperty("handle_stacktrace")
    private java.lang.String handleStacktrace;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("data")
    public UObject getData() {
        return data;
    }

    @JsonProperty("data")
    public void setData(UObject data) {
        this.data = data;
    }

    public ObjectData withData(UObject data) {
        this.data = data;
        return this;
    }

    @JsonProperty("info")
    public Tuple11 <Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> getInfo() {
        return info;
    }

    @JsonProperty("info")
    public void setInfo(Tuple11 <Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> info) {
        this.info = info;
    }

    public ObjectData withInfo(Tuple11 <Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> info) {
        this.info = info;
        return this;
    }

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
    @JsonProperty("infostruct")
    public ObjectInfo getInfostruct() {
        return infostruct;
    }

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
    @JsonProperty("infostruct")
    public void setInfostruct(ObjectInfo infostruct) {
        this.infostruct = infostruct;
    }

    public ObjectData withInfostruct(ObjectInfo infostruct) {
        this.infostruct = infostruct;
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

    public ObjectData withPath(List<String> path) {
        this.path = path;
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

    public ObjectData withProvenance(List<ProvenanceAction> provenance) {
        this.provenance = provenance;
        return this;
    }

    @JsonProperty("creator")
    public java.lang.String getCreator() {
        return creator;
    }

    @JsonProperty("creator")
    public void setCreator(java.lang.String creator) {
        this.creator = creator;
    }

    public ObjectData withCreator(java.lang.String creator) {
        this.creator = creator;
        return this;
    }

    @JsonProperty("orig_wsid")
    public java.lang.Long getOrigWsid() {
        return origWsid;
    }

    @JsonProperty("orig_wsid")
    public void setOrigWsid(java.lang.Long origWsid) {
        this.origWsid = origWsid;
    }

    public ObjectData withOrigWsid(java.lang.Long origWsid) {
        this.origWsid = origWsid;
        return this;
    }

    @JsonProperty("created")
    public java.lang.String getCreated() {
        return created;
    }

    @JsonProperty("created")
    public void setCreated(java.lang.String created) {
        this.created = created;
    }

    public ObjectData withCreated(java.lang.String created) {
        this.created = created;
        return this;
    }

    @JsonProperty("epoch")
    public java.lang.Long getEpoch() {
        return epoch;
    }

    @JsonProperty("epoch")
    public void setEpoch(java.lang.Long epoch) {
        this.epoch = epoch;
    }

    public ObjectData withEpoch(java.lang.Long epoch) {
        this.epoch = epoch;
        return this;
    }

    @JsonProperty("refs")
    public List<String> getRefs() {
        return refs;
    }

    @JsonProperty("refs")
    public void setRefs(List<String> refs) {
        this.refs = refs;
    }

    public ObjectData withRefs(List<String> refs) {
        this.refs = refs;
        return this;
    }

    @JsonProperty("copied")
    public java.lang.String getCopied() {
        return copied;
    }

    @JsonProperty("copied")
    public void setCopied(java.lang.String copied) {
        this.copied = copied;
    }

    public ObjectData withCopied(java.lang.String copied) {
        this.copied = copied;
        return this;
    }

    @JsonProperty("copy_source_inaccessible")
    public java.lang.Long getCopySourceInaccessible() {
        return copySourceInaccessible;
    }

    @JsonProperty("copy_source_inaccessible")
    public void setCopySourceInaccessible(java.lang.Long copySourceInaccessible) {
        this.copySourceInaccessible = copySourceInaccessible;
    }

    public ObjectData withCopySourceInaccessible(java.lang.Long copySourceInaccessible) {
        this.copySourceInaccessible = copySourceInaccessible;
        return this;
    }

    @JsonProperty("extracted_ids")
    public Map<String, List<String>> getExtractedIds() {
        return extractedIds;
    }

    @JsonProperty("extracted_ids")
    public void setExtractedIds(Map<String, List<String>> extractedIds) {
        this.extractedIds = extractedIds;
    }

    public ObjectData withExtractedIds(Map<String, List<String>> extractedIds) {
        this.extractedIds = extractedIds;
        return this;
    }

    @JsonProperty("handle_error")
    public java.lang.String getHandleError() {
        return handleError;
    }

    @JsonProperty("handle_error")
    public void setHandleError(java.lang.String handleError) {
        this.handleError = handleError;
    }

    public ObjectData withHandleError(java.lang.String handleError) {
        this.handleError = handleError;
        return this;
    }

    @JsonProperty("handle_stacktrace")
    public java.lang.String getHandleStacktrace() {
        return handleStacktrace;
    }

    @JsonProperty("handle_stacktrace")
    public void setHandleStacktrace(java.lang.String handleStacktrace) {
        this.handleStacktrace = handleStacktrace;
    }

    public ObjectData withHandleStacktrace(java.lang.String handleStacktrace) {
        this.handleStacktrace = handleStacktrace;
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
        return ((((((((((((((((((((((((((((((((("ObjectData"+" [data=")+ data)+", info=")+ info)+", infostruct=")+ infostruct)+", path=")+ path)+", provenance=")+ provenance)+", creator=")+ creator)+", origWsid=")+ origWsid)+", created=")+ created)+", epoch=")+ epoch)+", refs=")+ refs)+", copied=")+ copied)+", copySourceInaccessible=")+ copySourceInaccessible)+", extractedIds=")+ extractedIds)+", handleError=")+ handleError)+", handleStacktrace=")+ handleStacktrace)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
