
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
 * <p>Original spec-file type: AlterAdminObjectMetadataParams</p>
 * <pre>
 * Input parameters for the alter_admin_object_metadata method.
 *         updates - the metadata updates to apply to the objects. If the same object is specified
 *                 twice in the list, the update order is unspecified. At most 1000 updates are allowed
 *                 in one call.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "updates"
})
public class AlterAdminObjectMetadataParams {

    @JsonProperty("updates")
    private List<ObjectMetadataUpdate> updates;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("updates")
    public List<ObjectMetadataUpdate> getUpdates() {
        return updates;
    }

    @JsonProperty("updates")
    public void setUpdates(List<ObjectMetadataUpdate> updates) {
        this.updates = updates;
    }

    public AlterAdminObjectMetadataParams withUpdates(List<ObjectMetadataUpdate> updates) {
        this.updates = updates;
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
        return ((((("AlterAdminObjectMetadataParams"+" [updates=")+ updates)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
