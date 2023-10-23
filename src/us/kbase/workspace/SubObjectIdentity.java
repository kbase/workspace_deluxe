
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
 * <p>Original spec-file type: SubObjectIdentity</p>
 * <pre>
 * DEPRECATED
 *                 An object subset identifier.
 *                 Select a subset of an object by:
 *                 EITHER
 *                         One, and only one, of the numerical id or name of the workspace.
 *                                 ws_id wsid - the numerical ID of the workspace.
 *                                 ws_name workspace - name of the workspace.
 *                         AND
 *                         One, and only one, of the numerical id or name of the object.
 *                                 obj_id objid- the numerical ID of the object.
 *                                 obj_name name - name of the object.
 *                         OPTIONALLY
 *                                 obj_ver ver - the version of the object.
 *                 OR an object reference string:
 *                         obj_ref ref - an object reference string.
 *                 AND a subset specification:
 *                         list<object_path> included - the portions of the object to include
 *                                 in the object subset.
 *                 boolean strict_maps - if true, throw an exception if the subset
 *                         specification traverses a non-existant map key (default false)
 *                 boolean strict_arrays - if true, throw an exception if the subset
 *                         specification exceeds the size of an array (default true)
 *                 @deprecated Workspace.ObjectSpecification
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
    "ref",
    "included",
    "strict_maps",
    "strict_arrays"
})
public class SubObjectIdentity {

    @JsonProperty("workspace")
    private java.lang.String workspace;
    @JsonProperty("wsid")
    private Long wsid;
    @JsonProperty("name")
    private java.lang.String name;
    @JsonProperty("objid")
    private Long objid;
    @JsonProperty("ver")
    private Long ver;
    @JsonProperty("ref")
    private java.lang.String ref;
    @JsonProperty("included")
    private List<String> included;
    @JsonProperty("strict_maps")
    private Long strictMaps;
    @JsonProperty("strict_arrays")
    private Long strictArrays;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("workspace")
    public java.lang.String getWorkspace() {
        return workspace;
    }

    @JsonProperty("workspace")
    public void setWorkspace(java.lang.String workspace) {
        this.workspace = workspace;
    }

    public SubObjectIdentity withWorkspace(java.lang.String workspace) {
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

    public SubObjectIdentity withWsid(Long wsid) {
        this.wsid = wsid;
        return this;
    }

    @JsonProperty("name")
    public java.lang.String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(java.lang.String name) {
        this.name = name;
    }

    public SubObjectIdentity withName(java.lang.String name) {
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

    public SubObjectIdentity withObjid(Long objid) {
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

    public SubObjectIdentity withVer(Long ver) {
        this.ver = ver;
        return this;
    }

    @JsonProperty("ref")
    public java.lang.String getRef() {
        return ref;
    }

    @JsonProperty("ref")
    public void setRef(java.lang.String ref) {
        this.ref = ref;
    }

    public SubObjectIdentity withRef(java.lang.String ref) {
        this.ref = ref;
        return this;
    }

    @JsonProperty("included")
    public List<String> getIncluded() {
        return included;
    }

    @JsonProperty("included")
    public void setIncluded(List<String> included) {
        this.included = included;
    }

    public SubObjectIdentity withIncluded(List<String> included) {
        this.included = included;
        return this;
    }

    @JsonProperty("strict_maps")
    public Long getStrictMaps() {
        return strictMaps;
    }

    @JsonProperty("strict_maps")
    public void setStrictMaps(Long strictMaps) {
        this.strictMaps = strictMaps;
    }

    public SubObjectIdentity withStrictMaps(Long strictMaps) {
        this.strictMaps = strictMaps;
        return this;
    }

    @JsonProperty("strict_arrays")
    public Long getStrictArrays() {
        return strictArrays;
    }

    @JsonProperty("strict_arrays")
    public void setStrictArrays(Long strictArrays) {
        this.strictArrays = strictArrays;
    }

    public SubObjectIdentity withStrictArrays(Long strictArrays) {
        this.strictArrays = strictArrays;
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
        return ((((((((((((((((((((("SubObjectIdentity"+" [workspace=")+ workspace)+", wsid=")+ wsid)+", name=")+ name)+", objid=")+ objid)+", ver=")+ ver)+", ref=")+ ref)+", included=")+ included)+", strictMaps=")+ strictMaps)+", strictArrays=")+ strictArrays)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
