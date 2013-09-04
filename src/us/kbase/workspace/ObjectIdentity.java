
package us.kbase.workspace;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Generated;
import org.codehaus.jackson.annotate.JsonAnyGetter;
import org.codehaus.jackson.annotate.JsonAnySetter;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonPropertyOrder;
import org.codehaus.jackson.map.annotate.JsonSerialize;


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
 *                 obj_name object - name of the object.
 * OR an object reference string:
 *         obj_ref ref - an object reference string.
 * </pre>
 * 
 */
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspace",
    "wsid",
    "object",
    "objid",
    "ref"
})
public class ObjectIdentity {

    @JsonProperty("workspace")
    private String workspace;
    @JsonProperty("wsid")
    private Integer wsid;
    @JsonProperty("object")
    private String object;
    @JsonProperty("objid")
    private Integer objid;
    @JsonProperty("ref")
    private String ref;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("workspace")
    public String getWorkspace() {
        return workspace;
    }

    @JsonProperty("workspace")
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public ObjectIdentity withWorkspace(String workspace) {
        this.workspace = workspace;
        return this;
    }

    @JsonProperty("wsid")
    public Integer getWsid() {
        return wsid;
    }

    @JsonProperty("wsid")
    public void setWsid(Integer wsid) {
        this.wsid = wsid;
    }

    public ObjectIdentity withWsid(Integer wsid) {
        this.wsid = wsid;
        return this;
    }

    @JsonProperty("object")
    public String getObject() {
        return object;
    }

    @JsonProperty("object")
    public void setObject(String object) {
        this.object = object;
    }

    public ObjectIdentity withObject(String object) {
        this.object = object;
        return this;
    }

    @JsonProperty("objid")
    public Integer getObjid() {
        return objid;
    }

    @JsonProperty("objid")
    public void setObjid(Integer objid) {
        this.objid = objid;
    }

    public ObjectIdentity withObjid(Integer objid) {
        this.objid = objid;
        return this;
    }

    @JsonProperty("ref")
    public String getRef() {
        return ref;
    }

    @JsonProperty("ref")
    public void setRef(String ref) {
        this.ref = ref;
    }

    public ObjectIdentity withRef(String ref) {
        this.ref = ref;
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

}
