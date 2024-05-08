
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
 * <p>Original spec-file type: ObjectMetadataUpdate</p>
 * <pre>
 * An object metadata update specification.
 *                 Required arguments:
 *                 ObjectIdentity oi - the object to be altered
 *                 One or both of the following arguments are required:
 *                 usermeta new - metadata to assign to the workspace. Duplicate keys will
 *                         be overwritten.
 *                 list<string> remove - these keys will be removed from the workspace
 *                         metadata key/value pairs.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "oi",
    "new",
    "remove"
})
public class ObjectMetadataUpdate {

    /**
     * <p>Original spec-file type: ObjectIdentity</p>
     * <pre>
     * An object identifier.
     *                 Select an object by either:
     *                         One, and only one, of the numerical id or name of the workspace.
     *                                 ws_id wsid - the numerical ID of the workspace.
     *                                 ws_name workspace - the name of the workspace.
     *                         AND
     *                         One, and only one, of the numerical id or name of the object.
     *                                 obj_id objid- the numerical ID of the object.
     *                                 obj_name name - name of the object.
     *                         OPTIONALLY
     *                                 obj_ver ver - the version of the object.
     *                 OR an object reference string:
     *                         obj_ref ref - an object reference string.
     * </pre>
     * 
     */
    @JsonProperty("oi")
    private ObjectIdentity oi;
    @JsonProperty("new")
    private Map<String, String> _new;
    @JsonProperty("remove")
    private List<String> remove;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    /**
     * <p>Original spec-file type: ObjectIdentity</p>
     * <pre>
     * An object identifier.
     *                 Select an object by either:
     *                         One, and only one, of the numerical id or name of the workspace.
     *                                 ws_id wsid - the numerical ID of the workspace.
     *                                 ws_name workspace - the name of the workspace.
     *                         AND
     *                         One, and only one, of the numerical id or name of the object.
     *                                 obj_id objid- the numerical ID of the object.
     *                                 obj_name name - name of the object.
     *                         OPTIONALLY
     *                                 obj_ver ver - the version of the object.
     *                 OR an object reference string:
     *                         obj_ref ref - an object reference string.
     * </pre>
     * 
     */
    @JsonProperty("oi")
    public ObjectIdentity getOi() {
        return oi;
    }

    /**
     * <p>Original spec-file type: ObjectIdentity</p>
     * <pre>
     * An object identifier.
     *                 Select an object by either:
     *                         One, and only one, of the numerical id or name of the workspace.
     *                                 ws_id wsid - the numerical ID of the workspace.
     *                                 ws_name workspace - the name of the workspace.
     *                         AND
     *                         One, and only one, of the numerical id or name of the object.
     *                                 obj_id objid- the numerical ID of the object.
     *                                 obj_name name - name of the object.
     *                         OPTIONALLY
     *                                 obj_ver ver - the version of the object.
     *                 OR an object reference string:
     *                         obj_ref ref - an object reference string.
     * </pre>
     * 
     */
    @JsonProperty("oi")
    public void setOi(ObjectIdentity oi) {
        this.oi = oi;
    }

    public ObjectMetadataUpdate withOi(ObjectIdentity oi) {
        this.oi = oi;
        return this;
    }

    @JsonProperty("new")
    public Map<String, String> getNew() {
        return _new;
    }

    @JsonProperty("new")
    public void setNew(Map<String, String> _new) {
        this._new = _new;
    }

    public ObjectMetadataUpdate withNew(Map<String, String> _new) {
        this._new = _new;
        return this;
    }

    @JsonProperty("remove")
    public List<String> getRemove() {
        return remove;
    }

    @JsonProperty("remove")
    public void setRemove(List<String> remove) {
        this.remove = remove;
    }

    public ObjectMetadataUpdate withRemove(List<String> remove) {
        this.remove = remove;
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
        return ((((((((("ObjectMetadataUpdate"+" [oi=")+ oi)+", _new=")+ _new)+", remove=")+ remove)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
