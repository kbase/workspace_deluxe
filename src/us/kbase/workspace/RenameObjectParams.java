
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
 * <p>Original spec-file type: RenameObjectParams</p>
 * <pre>
 * Input parameters for the 'rename_object' function.
 * Required arguments:
 * ObjectIdentity obj - the object to rename.
 * obj_name new_name - the new name for the object.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "obj",
    "new_name"
})
public class RenameObjectParams {

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
    @JsonProperty("obj")
    private ObjectIdentity obj;
    @JsonProperty("new_name")
    private String newName;
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
    @JsonProperty("obj")
    public ObjectIdentity getObj() {
        return obj;
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
    @JsonProperty("obj")
    public void setObj(ObjectIdentity obj) {
        this.obj = obj;
    }

    public RenameObjectParams withObj(ObjectIdentity obj) {
        this.obj = obj;
        return this;
    }

    @JsonProperty("new_name")
    public String getNewName() {
        return newName;
    }

    @JsonProperty("new_name")
    public void setNewName(String newName) {
        this.newName = newName;
    }

    public RenameObjectParams withNewName(String newName) {
        this.newName = newName;
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
        return ((((((("RenameObjectParams"+" [obj=")+ obj)+", newName=")+ newName)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
