
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
 * <p>Original spec-file type: GetObjects2Params</p>
 * <pre>
 * Input parameters for the get_objects2 function.
 *                 Required parameters:
 *                 list<ObjectSpecification> objects - the list of object specifications
 *                         for the objects to return (via reference chain and as a subset if
 *                         specified).
 *                 Optional parameters:
 *                 boolean ignoreErrors - Don't throw an exception if an object cannot
 *                         be accessed; return null for that object's information instead.
 *                         Default false.
 *                 boolean no_data - return the provenance, references, and
 *                         object_info for this object without the object data. Default false.
 *                 boolean skip_external_system_updates - if the objects contain any external IDs, don't
 *                         contact external systems to perform any updates for those IDs (often ACL updates,
 *                         e.g. for handle / blobstore / sample IDs). In some cases this can speed up fetching the
 *                         data. Default false.
 *                 boolean batch_external_system_updates - if the objects contain any external IDs,
 *                         send all external system updates in a batch to each external system when possible
 *                         rather than object by object. This can potentially speed up the updates, but the
 *                         drawback is that if the external update fails for any object, all the objects that
 *                         required updates for that system will be marked as having a failed update.
 *                         Has no effect if skip_external_system_updates is true. Default false.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "objects",
    "ignoreErrors",
    "no_data",
    "skip_external_system_updates",
    "batch_external_system_updates"
})
public class GetObjects2Params {

    @JsonProperty("objects")
    private List<ObjectSpecification> objects;
    @JsonProperty("ignoreErrors")
    private Long ignoreErrors;
    @JsonProperty("no_data")
    private Long noData;
    @JsonProperty("skip_external_system_updates")
    private Long skipExternalSystemUpdates;
    @JsonProperty("batch_external_system_updates")
    private Long batchExternalSystemUpdates;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("objects")
    public List<ObjectSpecification> getObjects() {
        return objects;
    }

    @JsonProperty("objects")
    public void setObjects(List<ObjectSpecification> objects) {
        this.objects = objects;
    }

    public GetObjects2Params withObjects(List<ObjectSpecification> objects) {
        this.objects = objects;
        return this;
    }

    @JsonProperty("ignoreErrors")
    public Long getIgnoreErrors() {
        return ignoreErrors;
    }

    @JsonProperty("ignoreErrors")
    public void setIgnoreErrors(Long ignoreErrors) {
        this.ignoreErrors = ignoreErrors;
    }

    public GetObjects2Params withIgnoreErrors(Long ignoreErrors) {
        this.ignoreErrors = ignoreErrors;
        return this;
    }

    @JsonProperty("no_data")
    public Long getNoData() {
        return noData;
    }

    @JsonProperty("no_data")
    public void setNoData(Long noData) {
        this.noData = noData;
    }

    public GetObjects2Params withNoData(Long noData) {
        this.noData = noData;
        return this;
    }

    @JsonProperty("skip_external_system_updates")
    public Long getSkipExternalSystemUpdates() {
        return skipExternalSystemUpdates;
    }

    @JsonProperty("skip_external_system_updates")
    public void setSkipExternalSystemUpdates(Long skipExternalSystemUpdates) {
        this.skipExternalSystemUpdates = skipExternalSystemUpdates;
    }

    public GetObjects2Params withSkipExternalSystemUpdates(Long skipExternalSystemUpdates) {
        this.skipExternalSystemUpdates = skipExternalSystemUpdates;
        return this;
    }

    @JsonProperty("batch_external_system_updates")
    public Long getBatchExternalSystemUpdates() {
        return batchExternalSystemUpdates;
    }

    @JsonProperty("batch_external_system_updates")
    public void setBatchExternalSystemUpdates(Long batchExternalSystemUpdates) {
        this.batchExternalSystemUpdates = batchExternalSystemUpdates;
    }

    public GetObjects2Params withBatchExternalSystemUpdates(Long batchExternalSystemUpdates) {
        this.batchExternalSystemUpdates = batchExternalSystemUpdates;
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
        return ((((((((((((("GetObjects2Params"+" [objects=")+ objects)+", ignoreErrors=")+ ignoreErrors)+", noData=")+ noData)+", skipExternalSystemUpdates=")+ skipExternalSystemUpdates)+", batchExternalSystemUpdates=")+ batchExternalSystemUpdates)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
