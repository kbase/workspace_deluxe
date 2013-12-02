
package us.kbase.workspace;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Generated;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * <p>Original spec-file type: CopyObjectParams</p>
 * <pre>
 * Input parameters for the 'copy_object' function. 
 *         If the 'from' ObjectIdentity includes no version and the object is
 *         copied to a new name, the entire version history of the object is
 *         copied. In all other cases only the version specified, or the latest
 *         version if no version is specified, is copied.
 *         
 *         The version from the 'to' ObjectIdentity is always ignored.
 *         
 *         Required arguments:
 *         ObjectIdentity from - the object to copy.
 *         ObjectIdentity to - where to copy the object.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "from",
    "to"
})
public class CopyObjectParams {

    /**
     * <p>Original spec-file type: ObjectIdentity</p>
     * <pre>
     * An object identifier.
     * Select an object by either:
     *         One, and only one, of the numerical id or name of the workspace,
     *         where the name can also be a KBase ID including the numerical id,
     *         e.g. kb|ws.35.
     *                 ws_id wsid - the numerical ID of the workspace.
     *                 ws_name workspace - name of the workspace or the workspace ID
     *                         in KBase format, e.g. kb|ws.78.
     *         AND 
     *         One, and only one, of the numerical id or name of the object.
     *                 obj_id objid- the numerical ID of the object.
     *                 obj_name name - name of the object.
     *         OPTIONALLY
     *                 obj_ver ver - the version of the object.
     * OR an object reference string:
     *         obj_ref ref - an object reference string.
     * </pre>
     * 
     */
    @JsonProperty("from")
    private ObjectIdentity from;
    /**
     * <p>Original spec-file type: ObjectIdentity</p>
     * <pre>
     * An object identifier.
     * Select an object by either:
     *         One, and only one, of the numerical id or name of the workspace,
     *         where the name can also be a KBase ID including the numerical id,
     *         e.g. kb|ws.35.
     *                 ws_id wsid - the numerical ID of the workspace.
     *                 ws_name workspace - name of the workspace or the workspace ID
     *                         in KBase format, e.g. kb|ws.78.
     *         AND 
     *         One, and only one, of the numerical id or name of the object.
     *                 obj_id objid- the numerical ID of the object.
     *                 obj_name name - name of the object.
     *         OPTIONALLY
     *                 obj_ver ver - the version of the object.
     * OR an object reference string:
     *         obj_ref ref - an object reference string.
     * </pre>
     * 
     */
    @JsonProperty("to")
    private ObjectIdentity to;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * <p>Original spec-file type: ObjectIdentity</p>
     * <pre>
     * An object identifier.
     * Select an object by either:
     *         One, and only one, of the numerical id or name of the workspace,
     *         where the name can also be a KBase ID including the numerical id,
     *         e.g. kb|ws.35.
     *                 ws_id wsid - the numerical ID of the workspace.
     *                 ws_name workspace - name of the workspace or the workspace ID
     *                         in KBase format, e.g. kb|ws.78.
     *         AND 
     *         One, and only one, of the numerical id or name of the object.
     *                 obj_id objid- the numerical ID of the object.
     *                 obj_name name - name of the object.
     *         OPTIONALLY
     *                 obj_ver ver - the version of the object.
     * OR an object reference string:
     *         obj_ref ref - an object reference string.
     * </pre>
     * 
     */
    @JsonProperty("from")
    public ObjectIdentity getFrom() {
        return from;
    }

    /**
     * <p>Original spec-file type: ObjectIdentity</p>
     * <pre>
     * An object identifier.
     * Select an object by either:
     *         One, and only one, of the numerical id or name of the workspace,
     *         where the name can also be a KBase ID including the numerical id,
     *         e.g. kb|ws.35.
     *                 ws_id wsid - the numerical ID of the workspace.
     *                 ws_name workspace - name of the workspace or the workspace ID
     *                         in KBase format, e.g. kb|ws.78.
     *         AND 
     *         One, and only one, of the numerical id or name of the object.
     *                 obj_id objid- the numerical ID of the object.
     *                 obj_name name - name of the object.
     *         OPTIONALLY
     *                 obj_ver ver - the version of the object.
     * OR an object reference string:
     *         obj_ref ref - an object reference string.
     * </pre>
     * 
     */
    @JsonProperty("from")
    public void setFrom(ObjectIdentity from) {
        this.from = from;
    }

    public CopyObjectParams withFrom(ObjectIdentity from) {
        this.from = from;
        return this;
    }

    /**
     * <p>Original spec-file type: ObjectIdentity</p>
     * <pre>
     * An object identifier.
     * Select an object by either:
     *         One, and only one, of the numerical id or name of the workspace,
     *         where the name can also be a KBase ID including the numerical id,
     *         e.g. kb|ws.35.
     *                 ws_id wsid - the numerical ID of the workspace.
     *                 ws_name workspace - name of the workspace or the workspace ID
     *                         in KBase format, e.g. kb|ws.78.
     *         AND 
     *         One, and only one, of the numerical id or name of the object.
     *                 obj_id objid- the numerical ID of the object.
     *                 obj_name name - name of the object.
     *         OPTIONALLY
     *                 obj_ver ver - the version of the object.
     * OR an object reference string:
     *         obj_ref ref - an object reference string.
     * </pre>
     * 
     */
    @JsonProperty("to")
    public ObjectIdentity getTo() {
        return to;
    }

    /**
     * <p>Original spec-file type: ObjectIdentity</p>
     * <pre>
     * An object identifier.
     * Select an object by either:
     *         One, and only one, of the numerical id or name of the workspace,
     *         where the name can also be a KBase ID including the numerical id,
     *         e.g. kb|ws.35.
     *                 ws_id wsid - the numerical ID of the workspace.
     *                 ws_name workspace - name of the workspace or the workspace ID
     *                         in KBase format, e.g. kb|ws.78.
     *         AND 
     *         One, and only one, of the numerical id or name of the object.
     *                 obj_id objid- the numerical ID of the object.
     *                 obj_name name - name of the object.
     *         OPTIONALLY
     *                 obj_ver ver - the version of the object.
     * OR an object reference string:
     *         obj_ref ref - an object reference string.
     * </pre>
     * 
     */
    @JsonProperty("to")
    public void setTo(ObjectIdentity to) {
        this.to = to;
    }

    public CopyObjectParams withTo(ObjectIdentity to) {
        this.to = to;
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
        return ((((((("CopyObjectParams"+" [from=")+ from)+", to=")+ to)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
