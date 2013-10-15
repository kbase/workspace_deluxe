
package us.kbase.workspace;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Generated;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import us.kbase.common.service.Tuple10;
import us.kbase.common.service.UObject;


/**
 * <p>Original spec-file type: ObjectData</p>
 * <pre>
 * The data and metadata for an object.
 *         UnspecifiedObject data - the object's data.
 *         object_metadata_full meta - metadata about the object.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "data",
    "meta"
})
public class ObjectData {

    @JsonProperty("data")
    private UObject data;
    @JsonProperty("meta")
    private Tuple10 <Long, String, String, String, Long, String, Long, String, Long, Map<String, String>> meta;
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

    @JsonProperty("meta")
    public Tuple10 <Long, String, String, String, Long, String, Long, String, Long, Map<String, String>> getMeta() {
        return meta;
    }

    @JsonProperty("meta")
    public void setMeta(Tuple10 <Long, String, String, String, Long, String, Long, String, Long, Map<String, String>> meta) {
        this.meta = meta;
    }

    public ObjectData withMeta(Tuple10 <Long, String, String, String, Long, String, Long, String, Long, Map<String, String>> meta) {
        this.meta = meta;
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
        return ((((((("ObjectData"+" [data=")+ data)+", meta=")+ meta)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
