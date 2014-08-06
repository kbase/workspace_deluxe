
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


/**
 * <p>Original spec-file type: ObjectProvenanceInfo</p>
 * <pre>
 * The provenance and supplemental info for an object.
 *         object_info info - information about the object.
 *         list<ProvenanceAction> provenance - the object's provenance.
 *         username creator - the user that first saved the object to the
 *                 workspace.
 *         timestamp created - the date the object was first saved to the
 *                 workspace.
 *         list<obj_ref> - the references contained within the object.
 *         mapping<id_type, list<extracted_id>> extracted_ids - any ids extracted
 *                 from the object.
 *         string handle_error - if an error occurs while setting ACLs on
 *                 embedded handle IDs, it will be reported here.
 *         string handle_stacktrace - the stacktrace for handle_error.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "info",
    "provenance",
    "creator",
    "created",
    "refs",
    "extracted_ids",
    "handle_error",
    "handle_stacktrace"
})
public class ObjectProvenanceInfo {

    @JsonProperty("info")
    private Tuple11 <Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> info;
    @JsonProperty("provenance")
    private List<ProvenanceAction> provenance;
    @JsonProperty("creator")
    private java.lang.String creator;
    @JsonProperty("created")
    private java.lang.String created;
    @JsonProperty("refs")
    private List<String> refs;
    @JsonProperty("extracted_ids")
    private Map<String, List<String>> extractedIds;
    @JsonProperty("handle_error")
    private java.lang.String handleError;
    @JsonProperty("handle_stacktrace")
    private java.lang.String handleStacktrace;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("info")
    public Tuple11 <Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> getInfo() {
        return info;
    }

    @JsonProperty("info")
    public void setInfo(Tuple11 <Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> info) {
        this.info = info;
    }

    public ObjectProvenanceInfo withInfo(Tuple11 <Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>> info) {
        this.info = info;
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

    public ObjectProvenanceInfo withProvenance(List<ProvenanceAction> provenance) {
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

    public ObjectProvenanceInfo withCreator(java.lang.String creator) {
        this.creator = creator;
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

    public ObjectProvenanceInfo withCreated(java.lang.String created) {
        this.created = created;
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

    public ObjectProvenanceInfo withRefs(List<String> refs) {
        this.refs = refs;
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

    public ObjectProvenanceInfo withExtractedIds(Map<String, List<String>> extractedIds) {
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

    public ObjectProvenanceInfo withHandleError(java.lang.String handleError) {
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

    public ObjectProvenanceInfo withHandleStacktrace(java.lang.String handleStacktrace) {
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
        return ((((((((((((((((((("ObjectProvenanceInfo"+" [info=")+ info)+", provenance=")+ provenance)+", creator=")+ creator)+", created=")+ created)+", refs=")+ refs)+", extractedIds=")+ extractedIds)+", handleError=")+ handleError)+", handleStacktrace=")+ handleStacktrace)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
