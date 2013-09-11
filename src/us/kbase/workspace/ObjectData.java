
package us.kbase.workspace;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Generated;
import org.codehaus.jackson.annotate.JsonAnyGetter;
import org.codehaus.jackson.annotate.JsonAnySetter;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonPropertyOrder;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import us.kbase.Tuple10;


/**
 * <p>Original spec-file type: ObjectData</p>
 * <pre>
 * The data and metadata for an object.
 *         mapping<String, UnspecifiedObject> data - the object's data.
 *         object_metadata meta - metadata about the object.
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
    private Map<String, us.kbase.UObject> data;
    @JsonProperty("meta")
    private Tuple10 <Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String, us.kbase.UObject>> meta;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("data")
    public Map<String, us.kbase.UObject> getData() {
        return data;
    }

    @JsonProperty("data")
    public void setData(Map<String, us.kbase.UObject> data) {
        this.data = data;
    }

    public ObjectData withData(Map<String, us.kbase.UObject> data) {
        this.data = data;
        return this;
    }

    @JsonProperty("meta")
    public Tuple10 <Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String, us.kbase.UObject>> getMeta() {
        return meta;
    }

    @JsonProperty("meta")
    public void setMeta(Tuple10 <Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String, us.kbase.UObject>> meta) {
        this.meta = meta;
    }

    public ObjectData withMeta(Tuple10 <Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String, us.kbase.UObject>> meta) {
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
