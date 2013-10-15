
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "workspace",
    "wsid",
    "name",
    "objid",
    "ver",
    "ref"
})
public class ObjectIdentity {

    @JsonProperty("workspace")
    private String workspace;
    @JsonProperty("wsid")
    private Long wsid;
    @JsonProperty("name")
    private String name;
    @JsonProperty("objid")
    private Long objid;
    @JsonProperty("ver")
    private Long ver;
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
    public Long getWsid() {
        return wsid;
    }

    @JsonProperty("wsid")
    public void setWsid(Long wsid) {
        this.wsid = wsid;
    }

    public ObjectIdentity withWsid(Long wsid) {
        this.wsid = wsid;
        return this;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public ObjectIdentity withName(String name) {
        this.name = name;
        return this;
    }

    @JsonProperty("objid")
    public Long getObjid() {
        return objid;
    }

    @JsonProperty("objid")
    public void setObjid(Long objid) {
        this.objid = objid;
    }

    public ObjectIdentity withObjid(Long objid) {
        this.objid = objid;
        return this;
    }

    @JsonProperty("ver")
    public Long getVer() {
        return ver;
    }

    @JsonProperty("ver")
    public void setVer(Long ver) {
        this.ver = ver;
    }

    public ObjectIdentity withVer(Long ver) {
        this.ver = ver;
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

    @Override
    public String toString() {
        return ((((((((((((((("ObjectIdentity"+" [workspace=")+ workspace)+", wsid=")+ wsid)+", name=")+ name)+", objid=")+ objid)+", ver=")+ ver)+", ref=")+ ref)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
