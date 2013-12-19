
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
 * <p>Original spec-file type: RegisterTypespecParams</p>
 * <pre>
 * Parameters for the register_typespec function.
 *         Required arguments:
 *         One of:
 *         typespec spec - the new typespec to register.
 *         modulename mod - the module to recompile with updated options (see below).
 *         
 *         Optional arguments:
 *         boolean dryrun - Return, but do not save, the results of compiling the 
 *                 spec. Default true. Set to false for making permanent changes.
 *         list<typename> new_types - types in the spec to make available in the
 *                 workspace service. When compiling a spec for the first time, if
 *                 this argument is empty no types will be made available. Previously
 *                 available types remain so upon recompilation of a spec or
 *                 compilation of a new spec.
 *         list<typename> remove_types - no longer make these types available in
 *                 the workspace service for the new version of the spec. This does
 *                 not remove versions of types previously compiled.
 *         mapping<modulename, spec_version> dependencies - By default, the
 *                 latest released versions of spec dependencies will be included when
 *                 compiling a spec. Specific versions can be specified here.
 *         spec_version prev_ver - the id of the previous version of the typespec.
 *                 An error will be thrown if this is set and prev_ver is not the
 *                 most recent version of the typespec. This prevents overwriting of
 *                 changes made since retrieving a spec and compiling an edited spec.
 *                 This argument is ignored if a modulename is passed.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "spec",
    "mod",
    "new_types",
    "remove_types",
    "dependencies",
    "dryrun",
    "prev_ver"
})
public class RegisterTypespecParams {

    @JsonProperty("spec")
    private java.lang.String spec;
    @JsonProperty("mod")
    private java.lang.String mod;
    @JsonProperty("new_types")
    private List<String> newTypes;
    @JsonProperty("remove_types")
    private List<String> removeTypes;
    @JsonProperty("dependencies")
    private Map<String, Long> dependencies;
    @JsonProperty("dryrun")
    private java.lang.Long dryrun;
    @JsonProperty("prev_ver")
    private java.lang.Long prevVer;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("spec")
    public java.lang.String getSpec() {
        return spec;
    }

    @JsonProperty("spec")
    public void setSpec(java.lang.String spec) {
        this.spec = spec;
    }

    public RegisterTypespecParams withSpec(java.lang.String spec) {
        this.spec = spec;
        return this;
    }

    @JsonProperty("mod")
    public java.lang.String getMod() {
        return mod;
    }

    @JsonProperty("mod")
    public void setMod(java.lang.String mod) {
        this.mod = mod;
    }

    public RegisterTypespecParams withMod(java.lang.String mod) {
        this.mod = mod;
        return this;
    }

    @JsonProperty("new_types")
    public List<String> getNewTypes() {
        return newTypes;
    }

    @JsonProperty("new_types")
    public void setNewTypes(List<String> newTypes) {
        this.newTypes = newTypes;
    }

    public RegisterTypespecParams withNewTypes(List<String> newTypes) {
        this.newTypes = newTypes;
        return this;
    }

    @JsonProperty("remove_types")
    public List<String> getRemoveTypes() {
        return removeTypes;
    }

    @JsonProperty("remove_types")
    public void setRemoveTypes(List<String> removeTypes) {
        this.removeTypes = removeTypes;
    }

    public RegisterTypespecParams withRemoveTypes(List<String> removeTypes) {
        this.removeTypes = removeTypes;
        return this;
    }

    @JsonProperty("dependencies")
    public Map<String, Long> getDependencies() {
        return dependencies;
    }

    @JsonProperty("dependencies")
    public void setDependencies(Map<String, Long> dependencies) {
        this.dependencies = dependencies;
    }

    public RegisterTypespecParams withDependencies(Map<String, Long> dependencies) {
        this.dependencies = dependencies;
        return this;
    }

    @JsonProperty("dryrun")
    public java.lang.Long getDryrun() {
        return dryrun;
    }

    @JsonProperty("dryrun")
    public void setDryrun(java.lang.Long dryrun) {
        this.dryrun = dryrun;
    }

    public RegisterTypespecParams withDryrun(java.lang.Long dryrun) {
        this.dryrun = dryrun;
        return this;
    }

    @JsonProperty("prev_ver")
    public java.lang.Long getPrevVer() {
        return prevVer;
    }

    @JsonProperty("prev_ver")
    public void setPrevVer(java.lang.Long prevVer) {
        this.prevVer = prevVer;
    }

    public RegisterTypespecParams withPrevVer(java.lang.Long prevVer) {
        this.prevVer = prevVer;
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
        return ((((((((((((((((("RegisterTypespecParams"+" [spec=")+ spec)+", mod=")+ mod)+", newTypes=")+ newTypes)+", removeTypes=")+ removeTypes)+", dependencies=")+ dependencies)+", dryrun=")+ dryrun)+", prevVer=")+ prevVer)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
