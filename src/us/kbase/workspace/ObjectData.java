
package us.kbase.workspace;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Generated;
import org.codehaus.jackson.annotate.JsonAnyGetter;
import org.codehaus.jackson.annotate.JsonAnySetter;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonPropertyOrder;
import org.codehaus.jackson.map.annotate.JsonSerialize;
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
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "data",
    "meta"
})
public class ObjectData {

    @JsonProperty("data")
    private UObject data;
    @JsonProperty("meta")
    private Tuple10 <Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String, String>> meta;
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
    public Tuple10 <Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String, String>> getMeta() {
        return meta;
    }

    @JsonProperty("meta")
    public void setMeta(Tuple10 <Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String, String>> meta) {
        this.meta = meta;
    }

    public ObjectData withMeta(Tuple10 <Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String, String>> meta) {
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

}
