
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
 * <p>Original spec-file type: GetObjectInfoNewParams</p>
 * <pre>
 * Input parameters for the "get_object_info_new" function.
 *         Required arguments:
 *         list<ObjectIdentity> objects - the objects for which the information
 *                 should be fetched
 *         
 *         Optional arguments:
 *         boolean includeMetadata - include the object metadata in the returned
 *                 information. Default false.
 *         boolean ignoreErrors - Don't throw an exception if an object cannot
 *                 be accessed; return null for that object's information instead.
 *                 Default false.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "objects",
    "includeMetadata",
    "ignoreErrors"
})
public class GetObjectInfoNewParams {

    @JsonProperty("objects")
    private List<ObjectIdentity> objects;
    @JsonProperty("includeMetadata")
    private Long includeMetadata;
    @JsonProperty("ignoreErrors")
    private Long ignoreErrors;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("objects")
    public List<ObjectIdentity> getObjects() {
        return objects;
    }

    @JsonProperty("objects")
    public void setObjects(List<ObjectIdentity> objects) {
        this.objects = objects;
    }

    public GetObjectInfoNewParams withObjects(List<ObjectIdentity> objects) {
        this.objects = objects;
        return this;
    }

    @JsonProperty("includeMetadata")
    public Long getIncludeMetadata() {
        return includeMetadata;
    }

    @JsonProperty("includeMetadata")
    public void setIncludeMetadata(Long includeMetadata) {
        this.includeMetadata = includeMetadata;
    }

    public GetObjectInfoNewParams withIncludeMetadata(Long includeMetadata) {
        this.includeMetadata = includeMetadata;
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

    public GetObjectInfoNewParams withIgnoreErrors(Long ignoreErrors) {
        this.ignoreErrors = ignoreErrors;
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
        return ((((((((("GetObjectInfoNewParams"+" [objects=")+ objects)+", includeMetadata=")+ includeMetadata)+", ignoreErrors=")+ ignoreErrors)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
